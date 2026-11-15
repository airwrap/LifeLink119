package com.lifelink119.app.vehicle

import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.model.LocationData
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.lifelink119.app.core.*
import com.lifelink119.app.core.emergency.*
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

internal fun sdkOrigin(): EvidenceStatus =
    if (
        Build.FINGERPRINT.contains("generic") ||
            Build.MODEL.contains("sdk", true) ||
            Build.HARDWARE.contains("ranchu") ||
            Build.HARDWARE.contains("goldfish")
    )
        EvidenceStatus.SIMULATION
    else EvidenceStatus.VEHICLE

/** Reuses capability/error/timeout handling in the existing official Vehicle adapter. */
class PleosEmergencyVehicleProvider(private val gateway: PleosVehicleGateway) :
    VehicleDataProvider {
    override fun read(done: (VehicleState) -> Unit) {
        val unsupported =
            Evidence<String>(status = EvidenceStatus.UNSUPPORTED, source = "승인된 읽기 API 미연결")
        val values = VehicleField.entries.associateWith { unsupported }.toMutableMap()
        var airbag = Evidence<String>()
        fun next(index: Int) {
            if (index == VehicleMetric.entries.size) {
                done(VehicleState(values, airbag, unsupported))
                return
            }
            val metric = VehicleMetric.entries[index]
            gateway.readObservation(metric) { result ->
                val status =
                    when (result.status) {
                        DiagnosticStatus.READ -> sdkOrigin()
                        DiagnosticStatus.UNSUPPORTED -> EvidenceStatus.UNSUPPORTED
                        else -> EvidenceStatus.UNKNOWN
                    }
                val value =
                    if (status in setOf(EvidenceStatus.VEHICLE, EvidenceStatus.SIMULATION))
                        when (metric) {
                            VehicleMetric.DRIVER_AIRBAG ->
                                if (result.rawValue == 1.0) "전개"
                                else if (result.rawValue == 0.0) "미전개" else null
                            VehicleMetric.BATTERY_SOC ->
                                result.rawValue
                                    ?.takeIf { it.isFinite() && it in 0.0..100.0 }
                                    ?.let { "${it.toInt()}%" }
                            VehicleMetric.DRIVER_DOOR ->
                                result.rawValue?.let { "위치값 ${it.toInt()}" }
                        }
                    else null
                val sample =
                    Evidence(
                        value,
                        if (value == null && status != EvidenceStatus.UNSUPPORTED)
                            EvidenceStatus.UNKNOWN
                        else status,
                        "Pleos Vehicle 2.3.2 · ${result.status.name}" +
                            if (sdkOrigin() == EvidenceStatus.SIMULATION) " · 에뮬레이터" else "",
                        if (value == null) null else System.currentTimeMillis(),
                        SystemClock.elapsedRealtime(),
                    )
                when (metric) {
                    VehicleMetric.DRIVER_AIRBAG -> airbag = sample
                    VehicleMetric.BATTERY_SOC -> values[VehicleField.BATTERY] = sample
                    VehicleMetric.DRIVER_DOOR -> values[VehicleField.DOOR] = sample
                }
                next(index + 1)
            }
        }
        next(0)
    }
}

/** Documented SDK 1.1.3. confidence has no verified metre unit; never used as accuracyMeters. */
class PleosEmergencyLocationProvider(context: Context) : LocationProvider {
    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val worker =
        Executors.newSingleThreadExecutor {
            Thread(it, "lifelink-location").apply { isDaemon = true }
        }
    private var sdk: FusedLocation? = null
    private var generation = 0L
    private var closed = false
    private val workerBusy = AtomicBoolean(false)

    override fun read(done: (Evidence<LocationFix>) -> Unit) {
        if (closed) return
        val token = ++generation
        var complete = false
        lateinit var timeout: Runnable
        fun finish(value: Evidence<LocationFix>) {
            main.post {
                if (closed || token != generation || complete) return@post
                complete = true
                main.removeCallbacks(timeout)
                done(value)
            }
        }
        timeout = Runnable { finish(Evidence(source = "FusedLocation · 응답 시간 초과")) }
        main.postDelayed(timeout, 5_000)
        if (app.checkSelfPermission(PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            finish(Evidence(source = "FusedLocation · 위치 권한 없음"))
            return
        }
        if (!workerBusy.compareAndSet(false, true)) {
            finish(Evidence(source = "FusedLocation · 이전 SDK 작업 지연"))
            return
        }
        worker.execute {
            try {
                val client = sdk ?: FusedLocation(app).also { sdk = it }
                if (!client.isSupported()) {
                    finish(
                        Evidence(
                            status = EvidenceStatus.UNSUPPORTED,
                            source = "FusedLocation 서비스 미지원",
                        )
                    )
                    return@execute
                }
                client.initialize()
                client.getLastKnownLocation(
                    object : FusedLocationListener {
                        override fun onChanged(location: LocationData?) {
                            val fix =
                                location?.let {
                                    LocationFix(
                                        it.latitude,
                                        it.longitude,
                                        // Public 1.1.3 source does not define heading units or
                                        // reference direction. Do not silently interpret it as
                                        // degrees.
                                        headingDegrees = null,
                                    )
                                }
                            finish(
                                if (fix?.valid() == true)
                                    Evidence(
                                        fix,
                                        sdkOrigin(),
                                        "Pleos FusedLocation 1.1.3 · 마지막 측정" +
                                            if (sdkOrigin() == EvidenceStatus.SIMULATION) " · 에뮬레이터"
                                            else "",
                                        location.timestampMillis,
                                        SystemClock.elapsedRealtime(),
                                    )
                                else Evidence(source = "FusedLocation · 유효한 위치 없음")
                            )
                        }

                        override fun onError(exception: Exception) {
                            finish(Evidence(source = "FusedLocation · 조회 실패"))
                        }
                    }
                )
            } catch (_: Exception) {
                finish(Evidence(source = "FusedLocation · 서비스/권한 오류"))
            } catch (_: LinkageError) {
                finish(
                    Evidence(
                        status = EvidenceStatus.UNSUPPORTED,
                        source = "FusedLocation · 런타임 미지원",
                    )
                )
            } finally {
                workerBusy.set(false)
            }
        }
    }

    override fun close() {
        closed = true
        generation++
        main.removeCallbacksAndMessages(null)
        worker.execute {
            runCatching { sdk?.release() }
            sdk = null
        }
        worker.shutdown()
    }

    companion object {
        const val PERMISSION = "pleos.car.permission.FUSED_LOCATION"
    }
}
