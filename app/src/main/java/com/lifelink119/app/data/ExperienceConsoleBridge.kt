package com.lifelink119.app.data

import android.os.Handler
import android.os.Looper
import com.lifelink119.app.BuildConfig
import com.lifelink119.app.core.EmergencySession
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import org.json.JSONArray
import org.json.JSONObject

/** Optional metadata-only bridge to the existing loopback server. No profile/medical text. */
class ExperienceConsoleBridge {
    private val main = Handler(Looper.getMainLooper())
    private val worker =
        Executors.newSingleThreadExecutor { r ->
            Thread(r, "lifelink-expansion-console").apply { isDaemon = true }
        }
    @Volatile private var closed = false

    fun publish(s: EmergencySession, done: (String?) -> Unit) {
        val body =
            JSONObject()
                .put("schemaVersion", 1)
                .put("syntheticOnly", true)
                .put("id", s.id)
                .put("target", s.target.name)
                .put("scenarioId", s.scenarioId)
                .put("status", s.status.name)
                .put("revision", s.revision)
                .put("sharedFields", JSONArray(s.shared.keys.map { it.name }))
                .put("video", s.video.name)
                .put("videoSource", s.videoSource.name)
                .put("police", s.police.name)
                .put("navigation", s.navigation.name)
                .put("sign", s.sign.name)
        send("/snapshots", body, done)
    }

    fun revoke(id: String, done: (String?) -> Unit) {
        require(id.matches(Regex("DEMO-[0-9]{4,20}")))
        send("/revoke", JSONObject().put("syntheticOnly", true).put("id", id), done)
    }

    private fun send(path: String, body: JSONObject, done: (String?) -> Unit) {
        if (closed) return
        if (!BuildConfig.DEMO_FEATURES) {
            main.post { if (!closed) done("PC 전송은 debug 빌드의 localhost 시연에서만 가능합니다.") }
            return
        }
        worker.execute {
            val error =
                runCatching {
                        val c =
                            URL("http://127.0.0.1:8781/api/demo/expansion$path").openConnection()
                                as HttpURLConnection
                        try {
                            c.requestMethod = "POST"
                            c.connectTimeout = 2500
                            c.readTimeout = 3500
                            c.instanceFollowRedirects = false
                            c.useCaches = false
                            c.doOutput = true
                            c.setRequestProperty("Content-Type", "application/json")
                            val bytes = body.toString().toByteArray(Charsets.UTF_8)
                            c.setFixedLengthStreamingMode(bytes.size)
                            c.outputStream.use { it.write(bytes) }
                            check(c.responseCode in 200..299) {
                                "로컬 상황실이 이 기능을 지원하지 않거나 요청을 거절했습니다. 서버 버전을 확인하세요."
                            }
                            c.inputStream.use {
                                it.readBytes().also { b -> check(b.size <= 16384) }
                            }
                        } finally {
                            c.disconnect()
                        }
                    }
                    .exceptionOrNull()
                    ?.let { it.message ?: "PC 상태 전송 실패 · 앱 내 상황실은 계속 이용할 수 있습니다." }
            main.post { if (!closed) done(error) }
        }
    }

    fun release() {
        closed = true
        worker.shutdownNow()
    }
}
