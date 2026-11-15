package com.lifelink119.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lifelink119.app.core.emergency.*
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.vosk.Model

/**
 * Synthetic Windows-TTS WAVs only. Exercises the real Android native decoder without a microphone.
 */
@RunWith(AndroidJUnit4::class)
class EmergencyOfflineSpeechTest {
    @Test
    fun contradictoryOrRecombinedDecoderOutputsAreRejected() {
        fun raw(text: String, confidence: Double = 1.0) =
            JSONObject()
                .put("text", text)
                .put("result", JSONArray().put(JSONObject().put("conf", confidence)))
                .toString()
        val select = com.lifelink119.app.data.EmergencyGrammarRecognizer.Companion::select
        assertEquals("{}", select(raw("네"), raw("아니 요")))
        assertEquals("{}", select(raw("불이 난 이 준 래요"), raw("안 이에요")))
        assertEquals("{}", select(raw("네", 0.59), "{}"))
        assertEquals(raw("그만"), select(raw("그만"), raw("네")))
        assertEquals(raw("아니 요"), select(raw("아니 요"), raw("안 이에요")))
    }

    @Test
    fun testKoreanNativeDecoderOnSyntheticAudio() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val fixture = instrumentation.context.assets
        val folder = File(context.cacheDir, "synthetic-speech-model-test")
        fun copy(asset: String, dest: File) {
            val children = context.assets.list(asset).orEmpty()
            if (children.isEmpty())
                context.assets.open(asset).use { input ->
                    dest.parentFile?.mkdirs()
                    dest.outputStream().use { input.copyTo(it) }
                }
            else {
                dest.mkdirs()
                children.forEach { copy("$asset/$it", File(dest, it)) }
            }
        }
        copy("speech/vosk-model-small-ko-0.22", folder)
        org.vosk.LibVosk.setLogLevel(org.vosk.LogLevel.WARNINGS)
        try {
            Model(folder.absolutePath).use { model ->
                val cases = JSONArray(fixture.open("cases.json").bufferedReader().readText())
                assertTrue("Synthetic speech fixtures must be generated", cases.length() >= 5)
                val failures = mutableListOf<String>()
                val outcomes = JSONArray()
                for (i in 0 until cases.length()) {
                    val case = cases.getJSONObject(i)
                    val wav = fixture.open(case.getString("file")).use { it.readBytes() }
                    val bytes = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN)
                    var offset = 12
                    var data = ByteArray(0)
                    while (offset + 8 <= wav.size) {
                        val chunk = String(wav, offset, 4, Charsets.US_ASCII)
                        val size = bytes.getInt(offset + 4)
                        if (chunk == "fmt ") {
                            assertEquals(1, bytes.getShort(offset + 8).toInt())
                            assertEquals(1, bytes.getShort(offset + 10).toInt())
                            assertEquals(16000, bytes.getInt(offset + 12))
                        }
                        if (chunk == "data") {
                            data = wav.copyOfRange(offset + 8, offset + 8 + size)
                            break
                        }
                        offset += 8 + size + (size % 2)
                    }
                    assertTrue(data.isNotEmpty())
                    for (streaming in listOf(false, true)) com.lifelink119.app.data
                        .OfflineKoreanSpeech
                        .createRecognizer(model, true)
                        .use { recognizer ->
                            var endpoint = false
                            if (streaming) {
                                val pcm = ShortArray(data.size / 2)
                                ByteBuffer.wrap(data)
                                    .order(ByteOrder.LITTLE_ENDIAN)
                                    .asShortBuffer()
                                    .get(pcm)
                                for (start in pcm.indices step 3200) {
                                    val chunk =
                                        pcm.copyOfRange(start, minOf(start + 3200, pcm.size))
                                    if (recognizer.acceptWaveForm(chunk, chunk.size)) {
                                        endpoint = true
                                        break
                                    }
                                }
                                repeat(5) {
                                    if (!endpoint)
                                        endpoint = recognizer.acceptWaveForm(ShortArray(3200), 3200)
                                }
                            } else recognizer.acceptWaveForm(data, data.size)
                            val decoded =
                                JSONObject(
                                    if (endpoint) recognizer.result else recognizer.finalResult
                                )
                            val words = decoded.optJSONArray("result")
                            val confidence =
                                if (words == null || words.length() == 0) 0.0
                                else
                                    (0 until words.length()).minOf {
                                        words.getJSONObject(it).optDouble("conf", 0.0)
                                    }
                            val text =
                                com.lifelink119.app.data.OfflineKoreanSpeech.decodedText(
                                        decoded.toString()
                                    )
                                    .orEmpty()
                            val code =
                                VoiceAnswers.local(SupportStep.CONSCIOUSNESS, text)?.name
                                    ?: EmergencyCallSpeech.status(text)?.let { "CALL_${it.name}" }
                                    ?: if (VoiceAnswers.stop(text)) "STOP" else "UNRECOGNIZED"
                            outcomes.put(
                                JSONObject()
                                    .put("expected", case.getString("code"))
                                    .put("actual", code)
                                    .put("confidence", confidence)
                                    .put("streaming", streaming)
                            )
                            // The UX uses yes/no questions. Unclear longer speech must be rejected,
                            // never guessed.
                            val required = true
                            if (
                                code != case.getString("code") &&
                                    (required || code != "UNRECOGNIZED")
                            )
                                failures.add(
                                    "$i expected=${case.getString("code")} heard=$text code=$code"
                                )
                        }
                }
                instrumentation.addResults(
                    android.os.Bundle().apply { putString("syntheticSpeech", outcomes.toString()) }
                )
                assertTrue(
                    "Synthetic native decoder: $outcomes; " + failures.joinToString("; "),
                    failures.isEmpty(),
                )
            }
        } finally {
            check(folder.canonicalFile.parentFile == context.cacheDir.canonicalFile)
            folder.deleteRecursively()
        }
    }
}
