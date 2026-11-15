package com.lifelink119.app

import android.content.Context
import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lifelink119.app.data.VoiceAiPreferences
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VoiceAiPreferencesTest {
    @Test
    fun choiceSurvivesNewStoreAndRevocationWithoutPatientData() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val file = "voice-ai-test-" + java.util.UUID.randomUUID()
        val context =
            object : ContextWrapper(base) {
                override fun getSharedPreferences(name: String, mode: Int) =
                    base.getSharedPreferences(file, mode)
            }
        try {
            assertTrue(VoiceAiPreferences(context).consent.needsChoice)
            VoiceAiPreferences(context).choose(true)
            assertTrue(VoiceAiPreferences(context).externalAllowed)
            assertFalse(VoiceAiPreferences(context).consent.needsChoice)
            VoiceAiPreferences(context).choose(false)
            assertFalse(VoiceAiPreferences(context).externalAllowed)
            assertFalse(VoiceAiPreferences(context).consent.needsChoice)
            assertEquals(
                setOf("choice", "notice_version"),
                base.getSharedPreferences(file, Context.MODE_PRIVATE).all.keys,
            )
        } finally {
            base.deleteSharedPreferences(file)
        }
    }
}
