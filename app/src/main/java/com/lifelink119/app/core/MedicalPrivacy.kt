package com.lifelink119.app.core

import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

enum class MedicalAuthRoute {
    DEVICE_CREDENTIAL,
    APP_PIN_SETUP,
    APP_PIN,
}

object MedicalPrivacyPolicy {
    const val UNLOCK_MS = 60_000L

    fun route(deviceSecure: Boolean, nativePromptAvailable: Boolean, pinConfigured: Boolean) =
        if (pinConfigured) MedicalAuthRoute.APP_PIN
        else if (deviceSecure && nativePromptAvailable) MedicalAuthRoute.DEVICE_CREDENTIAL
        else MedicalAuthRoute.APP_PIN_SETUP

    fun validPin(pin: CharArray) = pin.size in 6..12 && pin.all { it in '0'..'9' }

    fun safeAlias(authenticated: Boolean, alias: String?, selected: Boolean) =
        if (authenticated && alias != null) alias else if (selected) "선택된 탑승자 · 잠김" else "미등록 승객"
}

class MedicalAccessWindow(private val clock: () -> Long) {
    private var deadline = 0L
    val authorized
        get() = deadline > 0 && clock() < deadline

    fun unlock() {
        deadline = clock() + MedicalPrivacyPolicy.UNLOCK_MS
    }

    fun lock() {
        deadline = 0
    }
}

/** Serializes invalidation/reset with the final write of a slow PIN calculation. */
class MedicalAuthEpoch {
    private var generation = 0
    val token: Int
        @Synchronized get() = generation

    @Synchronized
    fun invalidate(action: () -> Unit = {}) {
        generation++
        action()
    }

    @Synchronized fun isCurrent(token: Int) = generation == token

    @Synchronized
    fun runIfCurrent(token: Int, action: () -> Unit): Boolean {
        if (generation != token) return false
        action()
        return true
    }
}

data class PinAttemptState(val failures: Int = 0, val strikes: Int = 0, val lockedUntil: Long = 0)

object PinAttemptPolicy {
    private fun delay(strikes: Int) =
        minOf(300_000L, 30_000L * (1L shl (strikes.coerceIn(1, 5) - 1)))

    /** Deadlines are boot-local. A reboot restarts an outstanding penalty, never clears it. */
    fun forBoot(s: PinAttemptState, storedBoot: Int, currentBoot: Int, now: Long) =
        if (s.lockedUntil > 0 && storedBoot != currentBoot)
            s.copy(lockedUntil = now + delay(s.strikes))
        else s

    fun remainingSeconds(s: PinAttemptState, now: Long) =
        ((s.lockedUntil - now + 999) / 1000).coerceIn(0, 300).toInt()

    fun failed(s: PinAttemptState, now: Long): PinAttemptState {
        val failures = s.failures + 1
        if (failures < 5) return s.copy(failures = failures)
        val strikes = (s.strikes + 1).coerceAtMost(5)
        return PinAttemptState(0, strikes, now + delay(strikes))
    }
}

object MedicalPinDigest {
    const val ITERATIONS = 210_000

    fun derive(pin: CharArray, salt: ByteArray, pepper: (ByteArray) -> ByteArray): ByteArray {
        require(MedicalPrivacyPolicy.validPin(pin))
        require(salt.size == 16)
        val spec = PBEKeySpec(pin, salt, ITERATIONS, 256)
        val derived =
            try {
                SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            } finally {
                spec.clearPassword()
            }
        return try {
            pepper(derived)
        } finally {
            derived.fill(0)
        }
    }
}
