package com.lifelink119.app.core

import com.lifelink119.app.core.emergency.*
import org.junit.Assert.*
import org.junit.Test

class VoiceAiSetupTest {
    @Test
    fun noChoiceOrChangedNoticeNeverAllowsExternalData() {
        assertFalse(VoiceAiConsent().externalAllowed)
        val old = VoiceAiConsent(VoiceAiChoice.EXTERNAL, VoiceAiConsent.NOTICE_VERSION - 1)
        assertTrue(old.needsChoice)
        assertFalse(old.externalAllowed)
        val declined = VoiceAiConsent.decide(false)
        assertFalse(declined.needsChoice)
        assertFalse(declined.externalAllowed)
    }

    private class Output : EmergencySpeechPort {
        var spoken = emptyList<String>()
        var done: ((Boolean) -> Unit)? = null
        var microphoneCalls = 0

        override fun speak(parts: List<String>, done: (Boolean) -> Unit) {
            spoken = parts
            this.done = done
        }

        override fun listen(binary: Boolean, done: (String?) -> Unit) {
            microphoneCalls++
        }

        override fun stop() {}
    }

    @Test
    fun authoredDemoRunsSameDialogueWithoutOpeningMicrophone() {
        val output = Output()
        val speech = DemoEmergencySpeech(output) {}
        val ai = EmergencyAiCoordinator { 1000L }.apply { attach("DEMO-12345") }
        val voice =
            EmergencyVoiceConversation(
                ai,
                speech,
                MockLlmProvider(),
                { true },
                { ai.answer(it, EvidenceStatus.SIMULATION) },
                {},
            )
        speech.begin()
        voice.start(false)
        repeat(DemoEmergencySpeech.ANSWERS.size) {
            output.done!!(true) // question finished
            assertEquals(DemoEmergencySpeech.ANSWERS[it], speech.example)
            output.done!!(true) // synthetic answer finished
        }
        output.done!!(true) // final authored rest message
        assertFalse(voice.active)
        assertTrue(speech.finished)
        assertEquals(0, output.microphoneCalls)
        assertEquals(
            listOf(
                ObservationCode.CONSCIOUS_YES,
                ObservationCode.BREATHING_YES,
                ObservationCode.CHEST_PAIN,
            ),
            ai.session!!.patientObservations.map { it.code },
        )
        assertTrue(ai.session!!.patientObservations.all { it.source == EvidenceStatus.SIMULATION })
        assertTrue(ai.session!!.escalationRequired)
    }

    @Test
    fun stoppingDemoDiscardsSyntheticAnswerAndNextLiveStartUsesMicrophone() {
        val output = Output()
        val speech = DemoEmergencySpeech(output) {}
        speech.begin()
        var received = false
        speech.listen(true) { received = true }
        val late = output.done!!
        speech.end()
        late(true)
        assertFalse(received)
        assertFalse(speech.finished)
        speech.listen(true) {}
        assertEquals(1, output.microphoneCalls)
    }
}
