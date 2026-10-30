package com.lifelink119.app.data

import android.os.Handler
import android.os.Looper
import com.lifelink119.app.BuildConfig
import com.lifelink119.app.core.DemoShareSelection
import com.lifelink119.app.core.DemoSignalSnapshot
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

data class ConsoleSnapshot(
    val id: String, val status: String, val lastMessage: String,
    val selection: DemoShareSelection, val routeAvailable: Boolean,
    val signal: DemoSignalSnapshot = DemoSignalSnapshot(),
)
data class ConsoleResult(val snapshot: ConsoleSnapshot? = null, val error: String? = null)

/** Debug-only, fixed loopback destination, synthetic payloads, no redirects. */
class LocalConsoleGateway {
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor { task -> Thread(task, "lifelink-local-console").apply { isDaemon = true } }
    private val pending = AtomicBoolean(false)
    @Volatile private var closed = false
    private val validId = Regex("DEMO-[0-9]{4,}")

    fun share(session: MockDispatchGateway.Session, requestKey: String, callback: (ConsoleResult) -> Unit) {
        val body = JSONObject().apply {
            put("schemaVersion", "1.0"); put("syntheticOnly", true)
            put("scenario", session.scenario.name); put("caseCode", session.caseCode)
            put("consent", JSONObject().put("vehicle", session.selection.vehicle).put("location", session.selection.location).put("video", session.selection.video))
            put("vehicle", session.vehicle?.let { JSONObject().put("id", it.id).put("name", it.name).put("batteryPercent", it.batteryPercent) } ?: JSONObject.NULL)
            put("location", session.location?.let { JSONObject().put("coordinateSystem", "DEMO_GRID").put("label", it.label).put("x", it.x).put("y", it.y) } ?: JSONObject.NULL)
            put("videoLink", session.videoLink ?: JSONObject.NULL)
        }
        request("POST", "/sessions", body, requestKey, callback)
    }

    fun refresh(id: String, callback: (ConsoleResult) -> Unit) = operation(id, null, callback)
    fun close(id: String, callback: (ConsoleResult) -> Unit) = operation(id, "close", callback)
    fun revoke(id: String, callback: (ConsoleResult) -> Unit) = operation(id, "revoke", callback)
    fun signal(id: String, action: String, callback: (ConsoleResult) -> Unit) {
        if (!validId.matches(id) || action !in setOf("REQUEST","ACTIVATE","STOP")) {
            main.post { if (!closed) callback(ConsoleResult(error="허용된 모의 표시 요청이 아닙니다.")) }; return
        }
        request("POST", "/sessions/$id/signal/passenger", JSONObject().put("syntheticOnly",true).put("action",action), null, callback)
    }

    private fun operation(id: String, action: String?, callback: (ConsoleResult) -> Unit) {
        if (!validId.matches(id)) {
            main.post { if (!closed) callback(ConsoleResult(error = "잘못된 로컬 세션 ID입니다.")) }; return
        }
        request(if (action == null) "GET" else "POST", "/sessions/$id" + (action?.let { "/$it" } ?: ""),
            if (action == null) null else JSONObject().put("syntheticOnly", true), null, callback)
    }

    private fun request(method: String, path: String, body: JSONObject?, key: String?, callback: (ConsoleResult) -> Unit) {
        if (closed) return
        if (!BuildConfig.DEBUG) {
            main.post { if (!closed) callback(ConsoleResult(error = "로컬 상담센터 연결은 debug 시연 빌드에서만 가능합니다.")) }; return
        }
        if (!pending.compareAndSet(false, true)) {
            main.post { if (!closed) callback(ConsoleResult(error = "이전 로컬 요청 처리 중입니다. 잠시 후 다시 시도하세요.")) }; return
        }
        worker.execute {
            val result = try {
                val connection = URL("http://127.0.0.1:8781/api/demo$path").openConnection() as HttpURLConnection
                try {
                    connection.requestMethod = method
                    connection.connectTimeout = 2500; connection.readTimeout = 3500
                    connection.instanceFollowRedirects = false
                    connection.useCaches = false
                    if (body != null) {
                        connection.doOutput = true
                        connection.setRequestProperty("Content-Type", "application/json")
                        if (key != null) connection.setRequestProperty("Idempotency-Key", key)
                        val bytes = body.toString().toByteArray(Charsets.UTF_8)
                        connection.setFixedLengthStreamingMode(bytes.size)
                        connection.outputStream.use { it.write(bytes) }
                    }
                    val code = connection.responseCode
                    val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                    val text = stream?.bufferedReader(Charsets.UTF_8)?.use { reader ->
                        val buffer = CharArray(2048); val result = StringBuilder()
                        while (true) { val count = reader.read(buffer); if (count < 0) break; result.append(buffer, 0, count); check(result.length <= 65_536) { "응답 크기 초과" } }
                        result.toString()
                    } ?: "{}"
                    val response = JSONObject(text)
                    if (code !in 200..299) ConsoleResult(error = "로컬 서버 $code · ${response.optString("message", "요청을 확인하세요.")}")
                    else {
                        val id = response.getString("id"); require(validId.matches(id))
                        val status = response.getString("status")
                        require(status in setOf("DEMO_CONNECTED", "DEMO_RECEIVED", "DEMO_REVIEWING", "DEMO_GUIDANCE", "DEMO_CLOSED"))
                        val consent = response.getJSONObject("consent")
                        val events = response.getJSONArray("timeline")
                        val last = if (events.length() > 0) events.getJSONObject(events.length()-1).getString("message") else "모의 상담 대기"
                        val rawSignal = response.optJSONObject("signalPreview")
                        val signal = rawSignal?.let { DemoSignalSnapshot(it.optString("state","OFF"),
                            it.optString("authority").takeIf { value -> value in setOf("119","112") },
                            it.optString("approvalId").takeIf { value -> value.startsWith("SIM-LIGHT-") },
                            it.optInt("remainingSeconds",0),it.optBoolean("previewEnabled",false),
                            it.optBoolean("simulationOnly",false),it.optBoolean("hardwareConnected",true)) } ?: DemoSignalSnapshot()
                        ConsoleResult(ConsoleSnapshot(id, status, last, DemoShareSelection(consent.getBoolean("vehicle"), consent.getBoolean("location"), consent.getBoolean("video")), !response.isNull("route"), signal))
                    }
                } finally { connection.disconnect() }
            } catch (_: Exception) {
                ConsoleResult(error = "로컬 상담센터에 연결하지 못했습니다. Start-Local-Demo.cmd와 Connect-Local-Console.cmd를 확인하고 재시도하세요.")
            }
            pending.set(false)
            main.post { if (!closed) callback(result) }
        }
    }

    fun release() {
        closed = true; main.removeCallbacksAndMessages(null); worker.shutdownNow()
    }
}
