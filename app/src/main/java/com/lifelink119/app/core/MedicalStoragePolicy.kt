package com.lifelink119.app.core

/** One byte budget for both directions, including AES-GCM's IV and authentication tag. */
object MedicalStoragePolicy {
    const val MAX_PLAINTEXT_BYTES = 400_000
    const val IV_BYTES = 12
    const val TAG_BYTES = 16
    const val MAX_CIPHERTEXT_BYTES = MAX_PLAINTEXT_BYTES + IV_BYTES + TAG_BYTES
    const val MAX_BASE64_CHARS = ((MAX_CIPHERTEXT_BYTES + 2) / 3) * 4

    fun requirePlaintextSize(bytes: Int) {
        require(bytes in 1..MAX_PLAINTEXT_BYTES) {
            "저장 가능한 건강정보 용량을 초과했습니다. 입력을 줄여 주세요. 기존 정보는 유지됩니다."
        }
    }
}
