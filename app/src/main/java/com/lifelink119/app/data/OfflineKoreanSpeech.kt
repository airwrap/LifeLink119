package com.lifelink119.app.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.io.File
import java.util.concurrent.Executors
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService

/** Optional bundled model. PCM and recognized text stay in memory and are never logged or saved. */
class OfflineKoreanSpeech(private val context: Context) {
    companion object {
        // Korean 0.22 uses <UNK> and morpheme-separated words, unlike the English [unk] model.
        // Keep a real unknown token: unrelated speech must not be forced into yes or no.
        private val BINARY_PHRASES =
            org.json.JSONArray(
                listOf(
                    "네",
                    "예",
                    "아니요",
                    "아니 요",
                    "잘 모르겠 어요",
                    "모르겠 어요",
                    "몰라요",
                    "그만",
                    "멈춰",
                    "의식이 없어요",
                    "숨을 안 쉬 어요",
                    "불이 났어요",
                    "가슴 이 아파 요",
                    "상태가 나빠졌 어요",
                    "숨쉬기 힘들어 요",
                    "통화 중 이에요",
                    "전화 왔어요",
                    "연결 이 안돼 요",
                    "콜백 기다리 래요",
                    "다시 전화 준 대요",
                    "출동 기다리 래요",
                    "구조대 왔어요",
                    "<UNK>",
                    "가슴 이 아프지 않아 요",
                    "불이 난 건 아니 에 요",
                    "통화 중 이 아니 에 요",
                    "연결 이 됐는지 모르겠어 요",
                )
            )

        val BINARY_GRAMMAR = BINARY_PHRASES.toString()
        val SHORT_GRAMMAR =
            org.json
                .JSONArray(
                    listOf(
                        "네",
                        "예",
                        "아니요",
                        "아니 요",
                        "잘 모르겠 어요",
                        "모르겠 어요",
                        "몰라요",
                        "그만",
                        "멈춰",
                        "의식이 없어요",
                        "숨을 안 쉬 어요",
                        "불이 났어요",
                        "<UNK>",
                    )
                )
                .toString()
        private val supported =
            (0 until BINARY_PHRASES.length())
                .map { BINARY_PHRASES.getString(it).replace(" ", "") }
                .toSet()

        fun createRecognizer(model: Model, binary: Boolean) =
            (if (binary) EmergencyGrammarRecognizer(model) else Recognizer(model, 16000f)).apply {
                setWords(true)
            }

        fun decodedText(raw: String?, binary: Boolean = true): String? =
            runCatching {
                    val value = JSONObject(raw.orEmpty())
                    val words = value.optJSONArray("result")
                    val confidence =
                        if (words == null || words.length() == 0) 0.0
                        else
                            (0 until words.length()).minOf {
                                words.getJSONObject(it).optDouble("conf", 0.0)
                            }
                    value.optString("text").takeIf {
                        it.isNotBlank() &&
                            it.length <= 160 &&
                            confidence >= 0.6 &&
                            (!binary || it.replace(" ", "") in supported) &&
                            !it.contains("<UNK>")
                    }
                }
                .getOrNull()
    }

    private val main = Handler(Looper.getMainLooper())
    private val worker =
        Executors.newSingleThreadExecutor {
            Thread(it, "lifelink-local-speech").apply { isDaemon = true }
        }
    @Volatile private var model: Model? = null
    @Volatile private var closed = false
    private var loading = false
    private var recognizer: Recognizer? = null
    private var service: SpeechService? = null
    private var generation = 0L
    private val pending = mutableListOf<(Boolean) -> Unit>()

    fun prepare(done: (Boolean) -> Unit = {}) {
        if (closed) {
            done(false)
            return
        }
        if (model != null) {
            done(true)
            return
        }
        pending.add(done)
        if (loading) return
        loading = true
        worker.execute {
            val loaded =
                try {
                    val name = "vosk-model-small-ko-0.22"
                    val target = File(context.noBackupFilesDir, "speech/$name")
                    val marker = File(target, ".complete-0.22")
                    if (!marker.exists()) {
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
                        copy("speech/$name", target)
                        marker.writeText("model-0.22")
                    }
                    org.vosk.LibVosk.setLogLevel(org.vosk.LogLevel.WARNINGS)
                    Model(target.absolutePath)
                } catch (_: Exception) {
                    null
                } catch (_: LinkageError) {
                    null
                }
            main.post {
                loading = false
                if (closed) loaded?.close() else model = loaded
                val waiting = pending.toList()
                pending.clear()
                waiting.forEach { it(!closed && loaded != null) }
            }
        }
    }

    fun listen(binary: Boolean, done: (String?) -> Unit) {
        stop()
        val token = generation
        prepare { ready ->
            if (token != generation || closed) return@prepare
            if (!ready) {
                done(null)
                return@prepare
            }
            fun result(raw: String?) {
                if (token != generation) return
                val text = decodedText(raw, binary)
                stop()
                done(text)
            }
            try {
                recognizer = createRecognizer(requireNotNull(model), binary)
                service =
                    SpeechService(requireNotNull(recognizer), 16000f).apply {
                        startListening(
                            object : RecognitionListener {
                                override fun onPartialResult(hypothesis: String?) {}

                                override fun onResult(hypothesis: String?) {
                                    result(hypothesis)
                                }

                                override fun onFinalResult(hypothesis: String?) {
                                    result(hypothesis)
                                }

                                override fun onError(exception: Exception?) {
                                    result(null)
                                }

                                override fun onTimeout() {
                                    result(null)
                                }
                            },
                            10_000,
                        )
                    }
            } catch (_: Exception) {
                result(null)
            } catch (_: LinkageError) {
                result(null)
            }
        }
    }

    fun stop() {
        generation++
        service?.cancel()
        service?.shutdown()
        service = null
        recognizer?.close()
        recognizer = null
    }

    fun close() {
        closed = true
        stop()
        pending.clear()
        worker.execute {
            model?.close()
            model = null
        }
        worker.shutdown()
    }
}
