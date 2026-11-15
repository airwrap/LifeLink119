package com.lifelink119.app.data

import android.content.Context
import com.lifelink119.app.core.LocalRegistration
import com.lifelink119.app.core.MedicalStoragePolicy
import com.lifelink119.app.core.RegistrationRole
import org.json.JSONArray
import org.json.JSONObject

/** User-entered local data only. It is never used by the mock dispatch gateway. */
data class PersonalHealthRecord(
    val name: String = "",
    val conditions: String = "",
    val allergies: String = "",
    val medicationHistory: String = "",
    val id: String = "SELF",
    val role: RegistrationRole = RegistrationRole.SELF,
    val registeredPhone: String = "",
    val registration: LocalRegistration = LocalRegistration.UNKNOWN,
    val updatedAt: Long = 0L,
    val proxyConsent: Boolean = false,
    val safeCall: SafeCallDetails = SafeCallDetails(),
) {
    override fun toString() = "PersonalHealthRecord([REDACTED])"
}

object PersonalHealthCodec {
    fun validate(r: PersonalHealthRecord): Boolean =
        r.name.length <= 80 &&
            r.conditions.length <= 1500 &&
            r.allergies.length <= 1000 &&
            r.medicationHistory.length <= 3000 &&
            r.id.matches(Regex("SELF|LOCAL-[a-f0-9-]{36}")) &&
            r.updatedAt >= 0 &&
            (r.registeredPhone.isBlank() ||
                r.registeredPhone.matches(Regex("[+0-9 ()-]{7,24}")) &&
                    r.registeredPhone.count(Char::isDigit) in 7..15) &&
            (r.role != RegistrationRole.PROXY || r.proxyConsent) &&
            SafeCallDetailsCodec.error(r.safeCall) == null

    fun encode(r: PersonalHealthRecord): String {
        require(validate(r))
        return JSONObject()
            .put("version", 3)
            .put("name", r.name)
            .put("conditions", r.conditions)
            .put("allergies", r.allergies)
            .put("medicationHistory", r.medicationHistory)
            .put("id", r.id)
            .put("role", r.role.name)
            .put("registeredPhone", r.registeredPhone)
            .put("registration", r.registration.name)
            .put("updatedAt", r.updatedAt)
            .put("proxyConsent", r.proxyConsent)
            .put("safeCall", SafeCallDetailsCodec.encode(r.safeCall))
            .toString()
    }

    fun decode(raw: String): PersonalHealthRecord {
        MedicalStoragePolicy.requirePlaintextSize(raw.toByteArray(Charsets.UTF_8).size)
        val o = JSONObject(raw)
        val version = o.getInt("version")
        require(version in 1..3)
        return PersonalHealthRecord(
                o.getString("name"),
                o.getString("conditions"),
                o.getString("allergies"),
                o.getString("medicationHistory"),
                if (version == 1) "SELF" else o.getString("id"),
                if (version == 1) RegistrationRole.SELF
                else RegistrationRole.valueOf(o.getString("role")),
                if (version == 1) "" else o.getString("registeredPhone"),
                if (version == 1) LocalRegistration.UNKNOWN
                else LocalRegistration.valueOf(o.getString("registration")),
                if (version == 1) 0 else o.getLong("updatedAt"),
                if (version == 1) false else o.getBoolean("proxyConsent"),
                if (version < 3) SafeCallDetails()
                else SafeCallDetailsCodec.decode(o.getJSONObject("safeCall")),
            )
            .also { require(validate(it)) }
    }

    fun encodeList(records: List<PersonalHealthRecord>): String {
        require(records.size <= 12 && records.map { it.id }.distinct().size == records.size)
        return JSONArray(records.map { JSONObject(encode(it)) }).toString().also {
            MedicalStoragePolicy.requirePlaintextSize(it.toByteArray(Charsets.UTF_8).size)
        }
    }

    fun decodeList(raw: String): List<PersonalHealthRecord> {
        MedicalStoragePolicy.requirePlaintextSize(raw.toByteArray(Charsets.UTF_8).size)
        val a = JSONArray(raw)
        require(a.length() <= 12)
        return (0 until a.length())
            .map { decode(a.getJSONObject(it).toString()) }
            .also { require(it.map { r -> r.id }.distinct().size == it.size) }
    }
}

/** AES-GCM key belongs to Android Keystore; no plaintext preference or network copy. */
class PersonalHealthStore(context: Context) {
    private val prefs =
        context.getSharedPreferences("lifelink_personal_health_v1", Context.MODE_PRIVATE)

    fun records(): List<PersonalHealthRecord> {
        prefs.getString("records_cipher", null)?.let {
            return PersonalHealthCodec.decodeList(MedicalCrypto.decrypt(it))
        }
        // Read the legacy record without deleting or rewriting it until a successful explicit save.
        return prefs.getString("record_cipher", null)?.let {
            listOf(PersonalHealthCodec.decode(MedicalCrypto.decrypt(it)))
        } ?: emptyList()
    }

    fun load(): PersonalHealthRecord? = records().firstOrNull { it.id == "SELF" }

    fun save(record: PersonalHealthRecord) {
        val existing = records()
        val next =
            if (existing.any { it.id == record.id })
                existing.map { if (it.id == record.id) record else it }
            else existing + record
        write(next)
    }

    private fun write(records: List<PersonalHealthRecord>) {
        val cipher = MedicalCrypto.encrypt(PersonalHealthCodec.encodeList(records))
        check(prefs.edit().putString("records_cipher", cipher).remove("record_cipher").commit())
    }

    fun remove(id: String) {
        val remaining = records().filterNot { it.id == id }
        if (remaining.isEmpty()) erase() else write(remaining)
    }

    fun erase() {
        check(prefs.edit().remove("records_cipher").remove("record_cipher").commit())
    }
}
