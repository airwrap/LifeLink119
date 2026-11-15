package com.lifelink119.app.data

import android.content.Context
import com.lifelink119.app.core.emergency.*
import org.json.JSONArray
import org.json.JSONObject

data class EmergencyCheckpoint(
    val startedAt: Long,
    val call: EmergencyCallSession,
    val support: EmergencySupportSession,
)

object EmergencyCheckpointCodec {
    const val TTL = 60 * 60 * 1000L

    fun encode(value: EmergencyCheckpoint, now: Long): String =
        JSONObject()
            .put("v", 1)
            .put("saved", now)
            .put("started", value.startedAt)
            .put("id", value.call.incidentId)
            .put("attempt", value.call.callAttemptTime)
            .put(
                "observations",
                JSONArray(
                    value.support.patientObservations.map {
                        JSONObject()
                            .put("code", it.code.name)
                            .put("at", it.at)
                            .put("source", it.source.name)
                    }
                ),
            )
            .toString()

    fun decode(raw: String, now: Long): EmergencyCheckpoint {
        require(raw.length <= 12_000)
        val o = JSONObject(raw)
        require(o.getInt("v") == 1)
        val saved = o.getLong("saved")
        require(now >= saved && now - saved < TTL)
        val id = o.getString("id")
        require(id.matches(Regex("DEMO-[0-9]{4,20}")))
        val started = o.getLong("started")
        require(started in 1..saved)
        val list = o.getJSONArray("observations")
        require(list.length() <= 24)
        val ai = EmergencyAiCoordinator { now }
        ai.attach(id)
        ai.start(true)
        val observations =
            (0 until list.length()).map { index ->
                val item = list.getJSONObject(index)
                val code = ObservationCode.valueOf(item.getString("code"))
                val source = EvidenceStatus.valueOf(item.getString("source"))
                require(source == EvidenceStatus.USER || source == EvidenceStatus.SIMULATION)
                val at = item.getLong("at")
                require(at in started..saved)
                PatientObservation(code, at, source)
            }
        val guide =
            observations
                .map { VerifiedGuidance.forObservation(it.code) }
                .fold("WAIT", VerifiedGuidance::retain)
        return EmergencyCheckpoint(
            started,
            EmergencyCallSession(
                id,
                callAttemptTime =
                    if (o.isNull("attempt")) null
                    else o.getLong("attempt").also { require(it in started..saved) },
                connectionStatus = EmergencyCallStatus.UNKNOWN,
                lastUpdatedAt = now,
            ),
            EmergencySupportSession(
                id,
                SupportStep.STOPPED,
                guide,
                observations,
                guide in VerifiedGuidance.urgent,
                now,
            ),
        )
    }
}

/** Opt-in, one checkpoint, one-hour expiry; no coordinates, family profiles, audio or free text. */
class EmergencyCheckpointStore(context: Context) {
    private val app = context.applicationContext
    private val prefs =
        context.getSharedPreferences("lifelink_emergency_checkpoint", Context.MODE_PRIVATE)

    private fun boot() =
        android.provider.Settings.Global.getInt(
            app.contentResolver,
            android.provider.Settings.Global.BOOT_COUNT,
            -1,
        )

    val available: Boolean
        get() {
            if (!prefs.contains("cipher")) return false
            val now = android.os.SystemClock.elapsedRealtime()
            if (
                boot() < 0 ||
                    prefs.getInt("boot", -2) != boot() ||
                    now < prefs.getLong("saved_elapsed", Long.MAX_VALUE) ||
                    now >= prefs.getLong("expires_elapsed", 0)
            ) {
                clear()
                return false
            }
            return true
        }

    fun save(value: EmergencyCheckpoint, authenticated: Boolean, consent: Boolean) {
        check(authenticated && consent)
        val elapsed = android.os.SystemClock.elapsedRealtime()
        check(boot() >= 0)
        check(
            prefs
                .edit()
                .putString(
                    "cipher",
                    MedicalCrypto.encrypt(
                        EmergencyCheckpointCodec.encode(value, System.currentTimeMillis())
                    ),
                )
                .putInt("boot", boot())
                .putLong("saved_elapsed", elapsed)
                .putLong("expires_elapsed", elapsed + EmergencyCheckpointCodec.TTL)
                .commit()
        )
    }

    fun load(authenticated: Boolean): EmergencyCheckpoint? {
        check(authenticated)
        if (!available) return null
        val raw = prefs.getString("cipher", null) ?: return null
        return runCatching {
                EmergencyCheckpointCodec.decode(
                    MedicalCrypto.decrypt(raw),
                    System.currentTimeMillis(),
                )
            }
            .getOrElse {
                clear()
                null
            }
    }

    fun clear() {
        check(prefs.edit().clear().commit())
    }
}
