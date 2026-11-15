package com.lifelink119.app.data

import com.lifelink119.app.core.emergency.VoiceAnswers
import org.vosk.Model
import org.vosk.Recognizer

/**
 * A small yes/no grammar must not lose recognition when longer commands are enabled. Both decoders
 * see the same in-memory PCM. Only approved complete phrases survive; disagreeing recognized
 * phrases are rejected, never arbitrarily combined or corrected.
 */
class EmergencyGrammarRecognizer(model: Model) :
    Recognizer(model, 16000f, OfflineKoreanSpeech.SHORT_GRAMMAR) {
    private val commands =
        Recognizer(model, 16000f, OfflineKoreanSpeech.BINARY_GRAMMAR).apply { setWords(true) }
    private var released = false

    init {
        super.setWords(true)
    }

    override fun acceptWaveForm(data: ByteArray, len: Int): Boolean {
        val commandEnd = commands.acceptWaveForm(data, len)
        return super.acceptWaveForm(data, len) || commandEnd
    }

    override fun acceptWaveForm(data: ShortArray, len: Int): Boolean {
        val commandEnd = commands.acceptWaveForm(data, len)
        return super.acceptWaveForm(data, len) || commandEnd
    }

    override fun acceptWaveForm(data: FloatArray, len: Int): Boolean {
        val commandEnd = commands.acceptWaveForm(data, len)
        return super.acceptWaveForm(data, len) || commandEnd
    }

    override fun getResult(): String = select(super.getFinalResult(), commands.finalResult)

    override fun getFinalResult(): String = select(super.getFinalResult(), commands.finalResult)

    override fun getPartialResult(): String = "{}"

    override fun close() {
        if (!released) {
            released = true
            try {
                commands.close()
            } finally {
                super.close()
            }
        }
    }

    companion object {
        fun select(short: String, command: String): String {
            val a = OfflineKoreanSpeech.decodedText(short)
            val b = OfflineKoreanSpeech.decodedText(command)
            return when {
                a != null && VoiceAnswers.stop(a) -> short
                b != null && VoiceAnswers.stop(b) -> command
                a != null &&
                    b != null &&
                    VoiceAnswers.yesNo(a) != null &&
                    VoiceAnswers.yesNo(a) == VoiceAnswers.yesNo(b) -> short
                a != null && b != null && a.replace(" ", "") != b.replace(" ", "") -> "{}"
                a != null -> short
                b != null -> command
                else -> "{}"
            }
        }
    }
}
