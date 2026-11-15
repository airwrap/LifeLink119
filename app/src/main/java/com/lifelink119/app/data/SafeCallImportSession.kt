package com.lifelink119.app.data

import java.util.UUID
import org.json.JSONObject

/** Memory-only, one-use encrypted Activity result. No plaintext extras, files or saved state. */
class SafeCallImportSession(private val elapsed: () -> Long) {
    companion object {
        const val REQUEST = 6241
        const val EXTRA_NONCE = "safeCallImportNonce"
        const val EXTRA_CIPHER = "safeCallImportCipher"
        const val MAX_CIPHER_CHARS = 140_000
        const val TTL_MS = 10 * 60 * 1000L

        fun wrap(raw: String, nonce: String, retrievedAt: Long): String {
            require(nonce.matches(Regex("[a-f0-9-]{36}")))
            require(raw.toByteArray(Charsets.UTF_8).size <= SafeCallWebImport.MAX_JSON_BYTES)
            return JSONObject()
                .put("kind", "SAFE_CALL_WEB_IMPORT_V1")
                .put("nonce", nonce)
                .put("retrievedAt", retrievedAt)
                .put("payload", JSONObject(raw))
                .toString()
        }
    }

    private var nonce: String? = null
    private var started = 0L
    private var cipher: String? = null
    private var received = false

    fun begin(): String {
        clear()
        started = elapsed()
        return UUID.randomUUID().toString().also { nonce = it }
    }

    fun accept(value: String?): Boolean {
        if (!active() || received || value == null || value.length !in 40..MAX_CIPHER_CHARS) {
            clear()
            return false
        }
        received = true
        cipher = value
        return true
    }

    private fun active() = nonce != null && elapsed() - started in 0..TTL_MS

    fun preview(
        existing: PersonalHealthRecord?,
        decrypt: (String) -> String,
    ): SafeCallImportPreview {
        check(active() && received)
        val plain = decrypt(checkNotNull(cipher))
        require(plain.toByteArray(Charsets.UTF_8).size <= SafeCallWebImport.MAX_JSON_BYTES + 1024)
        val envelope = JSONObject(plain)
        require(
            envelope.getString("kind") == "SAFE_CALL_WEB_IMPORT_V1" &&
                envelope.getString("nonce") == nonce
        )
        return SafeCallWebImport.prepare(
            envelope.getJSONObject("payload").toString(),
            envelope.getLong("retrievedAt"),
            existing,
        )
    }

    fun clear() {
        nonce = null
        cipher = null
        received = false
        started = 0
    }
}
