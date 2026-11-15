package com.lifelink119.app.data

import android.content.Context
import com.lifelink119.app.core.emergency.*

/** Only a consent choice/version. No patient content, transcript, credential or incident ID. */
class VoiceAiPreferences(context: Context) {
    private val prefs =
        context.getSharedPreferences("lifelink_voice_ai_choice", Context.MODE_PRIVATE)
    val consent
        get() =
            runCatching {
                    VoiceAiConsent(
                        VoiceAiChoice.valueOf(prefs.getString("choice", "UNDECIDED")!!),
                        prefs.getInt("notice_version", 0),
                    )
                }
                .getOrDefault(VoiceAiConsent())

    val externalAllowed
        get() = consent.externalAllowed

    fun choose(external: Boolean) {
        val value = VoiceAiConsent.decide(external)
        check(
            prefs
                .edit()
                .putString("choice", value.choice.name)
                .putInt("notice_version", value.version)
                .commit()
        )
    }
}
