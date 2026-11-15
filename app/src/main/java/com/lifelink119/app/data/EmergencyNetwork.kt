package com.lifelink119.app.data

import android.os.Handler
import android.os.Looper
import com.lifelink119.app.BuildConfig
import com.lifelink119.app.core.emergency.*
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import org.json.JSONArray
import org.json.JSONObject

/** The app knows only a loopback proxy, never an external model credential or provider URL. */
class EmergencyNetwork(private val externalAllowed: () -> Boolean) : LlmProvider {
    var lastFailureCode = "NONE"
        private set

    private val main = Handler(Looper.getMainLooper())
    private val worker =
        Executors.newSingleThreadExecutor {
            Thread(it, "lifelink-emergency-proxy").apply { isDaemon = true }
        }
    private var generation = 0L
    @Volatile private var closed = false
    @Volatile private var connection: HttpURLConnection? = null

    override fun suggest(request: LlmRequest, done: (String?) -> Unit) {
        if (!externalAllowed()) {
            done(null)
            return
        }
        val token = ++generation
        post(
            "/api/demo/emergency/phrase",
            JSONObject()
                .put("version", 1)
                .put("consent", true)
                .put("step", request.step.name)
                .put("easy", request.easy),
        ) { result ->
            if (token == generation && externalAllowed()) done(result?.optString("phraseId"))
        }
    }

    override fun interpret(request: VoiceMeaningRequest, done: (ObservationCode?) -> Unit) {
        if (!externalAllowed()) {
            done(null)
            return
        }
        val token = ++generation
        post(
            "/api/demo/emergency/interpret",
            JSONObject()
                .put("version", 1)
                .put("consent", true)
                .put("step", request.step.name)
                .put("concepts", JSONArray(request.concepts.map { it.name })),
        ) { result ->
            if (token == generation && externalAllowed())
                done(
                    runCatching {
                            ObservationCode.valueOf(result?.optString("observationCode").orEmpty())
                        }
                        .getOrNull()
                        ?.takeIf { it in VoiceAnswers.allowed(request.step) }
                )
        }
    }

    fun publish(controller: EmergencySupportController, consent: Boolean, done: (Boolean) -> Unit) {
        check(consent && BuildConfig.DEMO_FEATURES)
        val scenario = requireNotNull(controller.scenario) { "PC에는 선택한 합성 시나리오만 게시할 수 있습니다." }
        val call = requireNotNull(controller.call)
        val support = requireNotNull(controller.ai.session)
        require(support.patientObservations.all { it.source == EvidenceStatus.SIMULATION })
        val body =
            JSONObject()
                .put("syntheticOnly", true)
                .put("id", call.incidentId)
                .put("scenario", scenario.name)
                .put("revision", controller.revision)
                .put("callStatus", call.connectionStatus.name)
                .put("guidanceId", support.verifiedGuidanceId)
                .put("observations", JSONArray(support.patientObservations.map { it.code.name }))
        post("/api/demo/emergency/snapshots", body) { done(it?.optBoolean("accepted") == true) }
    }

    fun revoke(id: String, done: (Boolean) -> Unit) {
        require(id.matches(Regex("DEMO-[0-9]{4,20}")))
        post("/api/demo/emergency/revoke", JSONObject().put("syntheticOnly", true).put("id", id)) {
            done(it?.optBoolean("revoked") == true)
        }
    }

    private fun post(path: String, body: JSONObject, done: (JSONObject?) -> Unit) {
        if (closed) return
        if (!BuildConfig.DEMO_FEATURES) {
            main.post { if (!closed) done(null) }
            return
        }
        worker.execute {
            if (
                path in setOf("/api/demo/emergency/phrase", "/api/demo/emergency/interpret") &&
                    !externalAllowed()
            ) {
                main.post { if (!closed) done(null) }
                return@execute
            }
            lastFailureCode = "CONNECT"
            val result =
                runCatching {
                        val c =
                            URL("http://127.0.0.1:8781$path")
                                .openConnection(java.net.Proxy.NO_PROXY) as HttpURLConnection
                        connection = c
                        try {
                            c.requestMethod = "POST"
                            c.connectTimeout = 1500
                            c.readTimeout = 5000
                            c.instanceFollowRedirects = false
                            c.useCaches = false
                            c.doOutput = true
                            c.setRequestProperty("Content-Type", "application/json")
                            val bytes = body.toString().toByteArray(Charsets.UTF_8)
                            c.setFixedLengthStreamingMode(bytes.size)
                            c.outputStream.use { it.write(bytes) }
                            lastFailureCode = "HTTP_${c.responseCode}"
                            check(c.responseCode == 200)
                            val raw =
                                c.inputStream.use { stream ->
                                    val bytesIn = java.io.ByteArrayOutputStream()
                                    val buffer = ByteArray(1024)
                                    while (true) {
                                        val count = stream.read(buffer)
                                        if (count < 0) break
                                        check(bytesIn.size() + count <= 8192)
                                        bytesIn.write(buffer, 0, count)
                                    }
                                    bytesIn.toString("UTF-8")
                                }
                            JSONObject(raw)
                        } finally {
                            c.disconnect()
                            connection = null
                        }
                    }
                    .getOrElse { error ->
                        if (!lastFailureCode.startsWith("HTTP_")) {
                            lastFailureCode = error.javaClass.simpleName
                        }
                        null
                    }
            main.post { if (!closed) done(result) }
        }
    }

    override fun cancel() {
        generation++
        connection?.disconnect()
    }

    fun close() {
        closed = true
        cancel()
        worker.shutdownNow()
        main.removeCallbacksAndMessages(null)
    }
}
