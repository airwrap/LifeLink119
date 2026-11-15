package com.lifelink119.app.data

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.lifelink119.app.core.emergency.EmergencySpeechPort
import java.security.MessageDigest

/**
 * Half-duplex, bounded listening. No cloud STT, continuous recording, audio files or transcript
 * logs.
 */
class EmergencyVoice(
    private val host: Activity,
    private val permitted: () -> Boolean = { true },
    private val interrupted: () -> Unit = {},
) : EmergencySpeechPort {
    private val main = Handler(host.mainLooper)
    private val offline = OfflineKoreanSpeech(host.applicationContext)
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var player: MediaPlayer? = null
    private var ttsReady = false
    private val audio = host.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var generation = 0L
    private val attributes =
        AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
    private val focus =
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener { change ->
                if (change <= 0)
                    main.post {
                        stop()
                        interrupted()
                    }
            }
            .build()

    private fun allowed() = permitted() && audio.mode == AudioManager.MODE_NORMAL

    fun hasPermission() =
        host.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    fun requestPermission() =
        host.requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 8192)

    fun initialize() {
        offline.prepare()
        if (tts != null) return
        tts =
            TextToSpeech(host) { status ->
                val local =
                    tts?.voices?.firstOrNull {
                        it.locale.language == "ko" && !it.isNetworkConnectionRequired
                    }
                if (status == TextToSpeech.SUCCESS && local != null) {
                    tts?.voice = local
                    ttsReady = true
                }
            }
    }

    private fun watch(token: Long) {
        main.postDelayed(
            {
                if (token == generation) {
                    if (!allowed()) {
                        stop()
                        interrupted()
                    } else watch(token)
                }
            },
            250,
        )
    }

    override fun speak(parts: List<String>, done: (Boolean) -> Unit) {
        stop()
        if (
            !allowed() || audio.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        ) {
            done(false)
            return
        }
        initialize()
        val token = generation
        watch(token)
        fun finish(success: Boolean) {
            if (token == generation) {
                stop()
                done(success)
            }
        }
        fun part(index: Int) {
            if (token != generation) return
            if (!allowed()) {
                finish(false)
                return
            }
            if (index >= parts.size) {
                finish(true)
                return
            }
            val text = parts[index]
            fun bundled() {
                if (token != generation) return
                try {
                    val hash =
                        MessageDigest.getInstance("SHA-256")
                            .digest(text.toByteArray(Charsets.UTF_8))
                            .joinToString("") { "%02x".format(it) }
                            .take(20)
                    val next = MediaPlayer()
                    player = next
                    next.setAudioAttributes(attributes)
                    host.assets.openFd("voice/$hash.wav").use {
                        next.setDataSource(it.fileDescriptor, it.startOffset, it.length)
                    }
                    next.setOnCompletionListener {
                        next.release()
                        if (player === next) player = null
                        part(index + 1)
                    }
                    next.setOnErrorListener { _, _, _ ->
                        finish(false)
                        true
                    }
                    next.prepare()
                    next.start()
                } catch (_: Exception) {
                    finish(false)
                }
            }
            if (!ttsReady) {
                bundled()
                return
            }
            val utterance = "emergency-$token-$index"
            tts?.setOnUtteranceProgressListener(
                object : UtteranceProgressListener() {
                    override fun onStart(id: String?) {}

                    override fun onDone(id: String?) {
                        if (id == utterance) main.post { if (token == generation) part(index + 1) }
                    }

                    @Deprecated("Legacy Android callback")
                    override fun onError(id: String?) {
                        if (id == utterance) main.post { bundled() }
                    }
                }
            )
            if (
                tts?.speak(text.take(500), TextToSpeech.QUEUE_FLUSH, Bundle(), utterance) !=
                    TextToSpeech.SUCCESS
            )
                bundled()
        }
        main.postDelayed({ finish(false) }, 45_000)
        part(0)
    }

    override fun listen(binary: Boolean, done: (String?) -> Unit) {
        stop()
        if (
            !allowed() ||
                !hasPermission() ||
                audio.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        ) {
            done(null)
            return
        }
        val token = generation
        watch(token)
        fun finish(text: String?) {
            if (token == generation) {
                stop()
                done(text?.takeIf { it.length <= 160 })
            }
        }
        main.postDelayed({ finish(null) }, 15_000)
        if (Build.VERSION.SDK_INT < 31 || !SpeechRecognizer.isOnDeviceRecognitionAvailable(host)) {
            offline.listen(binary) { finish(it) }
            return
        }
        try {
            recognizer =
                SpeechRecognizer.createOnDeviceSpeechRecognizer(host).apply {
                    setRecognitionListener(
                        object : RecognitionListener {
                            override fun onReadyForSpeech(params: Bundle?) {}

                            override fun onBeginningOfSpeech() {}

                            override fun onRmsChanged(rmsdB: Float) {}

                            override fun onBufferReceived(buffer: ByteArray?) {}

                            override fun onEndOfSpeech() {}

                            override fun onError(error: Int) {
                                finish(null)
                            }

                            override fun onResults(results: Bundle?) {
                                val score =
                                    results
                                        ?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)
                                        ?.firstOrNull()
                                finish(
                                    if (score != null && score >= 0 && score < 0.6f) null
                                    else
                                        results
                                            ?.getStringArrayList(
                                                SpeechRecognizer.RESULTS_RECOGNITION
                                            )
                                            ?.firstOrNull()
                                )
                            }

                            override fun onPartialResults(partialResults: Bundle?) {}

                            override fun onEvent(eventType: Int, params: Bundle?) {}
                        }
                    )
                    startListening(
                        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
                            .putExtra(
                                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                            )
                            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                            .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                    )
                }
        } catch (_: Exception) {
            finish(null)
        }
    }

    override fun stop() {
        generation++
        main.removeCallbacksAndMessages(null)
        recognizer?.cancel()
        recognizer?.destroy()
        recognizer = null
        offline.stop()
        player?.release()
        player = null
        tts?.stop()
        audio.abandonAudioFocusRequest(focus)
    }

    fun close() {
        stop()
        offline.close()
        tts?.shutdown()
        tts = null
        ttsReady = false
    }
}
