package com.lifelink119.app.data

import android.os.Handler
import android.os.Looper
import com.lifelink119.app.BuildConfig
import com.lifelink119.app.core.CooperationSnapshot
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

data class CooperationResult(val snapshot: CooperationSnapshot? = null, val error: String? = null, val missing: Boolean = false)

/** Same loopback demo boundary as the existing console; no physical vehicle writes. */
class CooperationGateway {
    private val main=Handler(Looper.getMainLooper())
    private val worker=Executors.newSingleThreadExecutor { r -> Thread(r,"lifelink-cooperation").apply { isDaemon=true } }
    private val pending=AtomicBoolean(false)
    @Volatile private var closed=false
    private val id=Regex("POC-[0-9]{16}")

    fun create(scenario: String, key: String, callback: (CooperationResult)->Unit) {
        require(scenario in setOf("EARLY_HELP","EMS_FIRST","NO_RESPONDER"))
        request("/runs",JSONObject().put("syntheticOnly",true).put("scenarioId",scenario).put("consent",true),key,callback)
    }
    fun refresh(run: String, callback: (CooperationResult)->Unit) {
        require(id.matches(run));request("/runs/$run",null,null,callback)
    }
    fun action(run: String, action: String, vehicle: String?, callback: (CooperationResult)->Unit) {
        require(id.matches(run))
        require(action in setOf("CANCEL","ACCEPT","DECLINE","ARRIVE","ACTIVATE_BEACON","STOP_BEACON","WITHDRAW"))
        val body=JSONObject().put("syntheticOnly",true).put("action",action)
        val role=if(action=="CANCEL") "requester" else "volunteer"
        if(role=="volunteer") { require(vehicle in setOf("DEMO-HELPER-A","DEMO-HELPER-D"));body.put("vehicleId",vehicle) }
        request("/runs/$run/$role",body,null,callback)
    }
    private fun request(path: String, body: JSONObject?, key: String?, callback: (CooperationResult)->Unit) {
        if(closed)return
        if(!BuildConfig.DEBUG) { main.post { if(!closed)callback(CooperationResult(error="로컬 응급협력 실험은 debug 빌드에서 실행합니다.")) };return }
        if(!pending.compareAndSet(false,true)) { main.post { if(!closed)callback(CooperationResult(error="이전 실험 요청을 처리하고 있습니다.")) };return }
        worker.execute {
            val result=try {
                val c=URL("http://127.0.0.1:8781/api/demo/cooperation$path").openConnection() as HttpURLConnection
                try {
                    c.requestMethod=if(body==null) "GET" else "POST";c.connectTimeout=2500;c.readTimeout=3500
                    c.instanceFollowRedirects=false;c.useCaches=false
                    if(body!=null){c.doOutput=true;c.setRequestProperty("Content-Type","application/json");if(key!=null)c.setRequestProperty("Idempotency-Key",key)
                        val bytes=body.toString().toByteArray(Charsets.UTF_8);c.setFixedLengthStreamingMode(bytes.size);c.outputStream.use { it.write(bytes) }}
                    val code=c.responseCode
                    val raw=(if(code in 200..299)c.inputStream else c.errorStream)?.bufferedReader(Charsets.UTF_8)?.use { reader ->
                        val out=StringBuilder();val buffer=CharArray(2048)
                        while(true){val n=reader.read(buffer);if(n<0)break;out.append(buffer,0,n);check(out.length<=65_536)};out.toString()
                    } ?: "{}"
                    val json=JSONObject(raw)
                    if(code !in 200..299) CooperationResult(error="실험 서버 $code · ${json.optString("message")}",missing=code==404) else CooperationResult(parse(json))
                } finally { c.disconnect() }
            } catch(_:Exception){CooperationResult(error="로컬 응급협력 실험에 연결하지 못했습니다. 데모 서버와 ADB 연결을 확인하세요.")}
            pending.set(false);main.post { if(!closed)callback(result) }
        }
    }
    private fun parse(j: JSONObject): CooperationSnapshot {
        val run=j.getString("id");require(id.matches(run));require(j.get("syntheticOnly")==true)
        val candidates=j.getJSONArray("candidates");val vehicle=j.optString("vehicleId").takeIf { it.startsWith("DEMO-HELPER-") }
        var eligible=false;val profiles=mutableListOf<String>()
        for(i in 0 until candidates.length()) { val v=candidates.getJSONObject(i);if(v.getString("id")==vehicle)eligible=v.getBoolean("eligible")
            profiles.add("${v.getString("name")} · 도착 ${v.getInt("travelSeconds")}초 가정 · ${v.getString("reason")}") }
        val b=j.getJSONObject("beacon");val p=j.getJSONObject("projection");val timeline=j.getJSONArray("timeline")
        val events=(maxOf(0,timeline.length()-6) until timeline.length()).joinToString("\n") { i -> val e=timeline.getJSONObject(i);"${e.getDouble("elapsedSeconds")}초 · ${e.getString("label")}" }
        return CooperationSnapshot(run,j.getString("state"),j.getJSONObject("scenario").getString("title"),vehicle,eligible,
            j.getInt("generation"),j.optString("missionId").takeIf { it.startsWith("SIM-MISSION-") },j.getInt("missionRemainingSeconds"),
            b.getString("state"),b.optString("authority").takeIf { it in setOf("119","112") },b.optString("approvalId").takeIf { it.startsWith("SIM-COOP-LIGHT-") },b.getInt("sequence"),
            b.getInt("remainingSeconds"),b.opt("previewEnabled")==true,j.get("syntheticOnly")==true,b.opt("simulationOnly")==true,b.opt("hardwareConnected")!=false,
            p.getInt("baselineSeconds"),if(p.isNull("supportSeconds"))null else p.getInt("supportSeconds"),p.getInt("potentialHeadStartSeconds"),profiles.joinToString("\n\n"),events)
    }
    fun release(){closed=true;main.removeCallbacksAndMessages(null);worker.shutdownNow()}
}
