package com.lifelink119.app.core.emergency

enum class VoiceAiChoice {
    UNDECIDED,
    EXTERNAL,
    LOCAL_ONLY,
}

/** A saved choice covers only the disclosed finite-code payload, never recordings or location. */
data class VoiceAiConsent(
    val choice: VoiceAiChoice = VoiceAiChoice.UNDECIDED,
    val version: Int = 0,
) {
    val needsChoice
        get() = version != NOTICE_VERSION || choice == VoiceAiChoice.UNDECIDED

    val externalAllowed
        get() = !needsChoice && choice == VoiceAiChoice.EXTERNAL

    companion object {
        const val NOTICE_VERSION = 1

        fun decide(external: Boolean) =
            VoiceAiConsent(
                if (external) VoiceAiChoice.EXTERNAL else VoiceAiChoice.LOCAL_ONLY,
                NOTICE_VERSION,
            )
    }
}
