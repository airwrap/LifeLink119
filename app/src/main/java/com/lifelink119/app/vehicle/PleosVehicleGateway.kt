package com.lifelink119.app.vehicle

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.DoorArea
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.constant.area.DoorValue
import ai.pleos.playground.vehicle.constant.area.SeatValue
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.lifelink119.app.core.BatteryReadPolicy
import com.lifelink119.app.core.DiagnosticStatus
import com.lifelink119.app.core.DoorReadPolicy
import com.lifelink119.app.core.VehicleDiagnostic
import com.lifelink119.app.core.VehicleMetric
import com.lifelink119.app.core.VehicleWorkerState
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Official read-only Vehicle SDK 2.3.2 observations for diagnostics and local context. A vehicle
 * signal never determines medical severity, dispatch or safe stopping. 카메라/GPS/전화/의료정보에 접근하지 않음.
 */
class PleosVehicleGateway(context: Context) {
    private enum class Query {
        DOOR,
        BATTERY,
        AIRBAG,
    }

    private val applicationContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val executor =
        Executors.newSingleThreadExecutor { task ->
            Thread(task, "lifelink-door-diagnostic").apply { isDaemon = true }
        }
    private var vehicle: Vehicle? = null
    private var initialized = false
    @Volatile private var unsupportedAirbag: VehicleDiagnostic? = null
    @Volatile private var closed = false
    @Volatile private var reconnect = false
    private val pending = AtomicBoolean(false)
    private val workerState = VehicleWorkerState()
    private val generation = AtomicLong(0)

    fun readDriverDoor(onResult: (VehicleDiagnostic) -> Unit) = read(Query.DOOR, onResult)

    fun readBatteryCharge(onResult: (VehicleDiagnostic) -> Unit) = read(Query.BATTERY, onResult)

    fun readObservation(metric: VehicleMetric, onResult: (VehicleDiagnostic) -> Unit) =
        read(
            when (metric) {
                VehicleMetric.DRIVER_DOOR -> Query.DOOR
                VehicleMetric.BATTERY_SOC -> Query.BATTERY
                VehicleMetric.DRIVER_AIRBAG -> Query.AIRBAG
            },
            onResult,
        )

    private fun read(query: Query, onResult: (VehicleDiagnostic) -> Unit) {
        if (closed) {
            main.post {
                onResult(
                    VehicleDiagnostic(DiagnosticStatus.RELEASED, "SDK 진단이 종료됐습니다. 화면을 다시 여세요.")
                )
            }
            return
        }
        if (
            !applicationContext.packageManager.hasSystemFeature(
                android.content.pm.PackageManager.FEATURE_AUTOMOTIVE
            )
        ) {
            main.post {
                onResult(
                    VehicleDiagnostic(
                        DiagnosticStatus.UNAVAILABLE,
                        "Pleos 차량 서비스가 없는 환경입니다. SDK 성공값으로 대체하지 않습니다.",
                    )
                )
            }
            return
        }
        unsupportedAirbag
            ?.takeIf { query == Query.AIRBAG && !reconnect }
            ?.let { known ->
                main.post { if (!closed) onResult(known) }
                return
            }
        if (!pending.compareAndSet(false, true)) {
            main.post { onResult(VehicleDiagnostic(DiagnosticStatus.BUSY, "진행 중인 차량 조회가 있습니다.")) }
            return
        }
        if (!workerState.begin()) {
            pending.set(false)
            val result =
                if (workerState.state == VehicleWorkerState.State.STALLED)
                    VehicleDiagnostic(
                        DiagnosticStatus.SERVICE_UNAVAILABLE,
                        "차량 SDK 초기화가 응답하지 않습니다. 차량 서비스를 확인하고 앱을 다시 실행하세요. 이전 작업이 끝나면 재조회할 수 있습니다.",
                    )
                else
                    VehicleDiagnostic(
                        DiagnosticStatus.BUSY,
                        "차량 서비스가 이전 요청을 처리 중입니다. 잠시 후 다시 시도하세요.",
                    )
            main.post { onResult(result) }
            return
        }
        val token = generation.incrementAndGet()
        val completed = AtomicBoolean(false)
        lateinit var timeout: Runnable
        fun active() = !closed && token == generation.get() && !completed.get()
        fun finish(result: VehicleDiagnostic) {
            if (closed || token != generation.get() || !completed.compareAndSet(false, true)) return
            if (query == Query.AIRBAG && result.status == DiagnosticStatus.UNSUPPORTED)
                unsupportedAirbag = result
            main.removeCallbacks(timeout)
            pending.set(false)
            main.post { if (!closed && token == generation.get()) onResult(result) }
        }
        fun failed(stage: String, error: Exception) {
            if (!active()) return
            val result = DoorReadPolicy.failure(stage, error)
            if (
                result.status in
                    setOf(
                        DiagnosticStatus.ERROR,
                        DiagnosticStatus.SERVICE_UNAVAILABLE,
                        DiagnosticStatus.TIMEOUT,
                    )
            )
                reconnect = true
            finish(result)
        }
        timeout = Runnable {
            if (!active()) return@Runnable
            reconnect = true
            workerState.timeout()
            finish(
                VehicleDiagnostic(
                    DiagnosticStatus.TIMEOUT,
                    "15초 동안 응답이 없습니다.\nPleos 차량 서비스 상태를 확인하고 다시 시도하세요.",
                )
            )
        }
        main.postDelayed(timeout, 15_000)
        executor.execute {
            var stage = "초기화"
            try {
                if (!active()) return@execute
                if (reconnect) {
                    unsupportedAirbag = null
                    runCatching { vehicle?.release() }
                    vehicle = null
                    initialized = false
                    reconnect = false
                }
                val client = vehicle ?: Vehicle(applicationContext).also { vehicle = it }
                if (!initialized) {
                    client.initialize()
                    initialized = true
                }
                if (!active()) return@execute
                stage = "지원 기능 확인"
                if (query == Query.AIRBAG) {
                    val safety = client.getSafety()
                    val seat = SeatArea(SeatValue.ROW_1_LEFT)
                    safety.checkAirbagDeployedCapabilities(
                        onComplete = capability@{ config, areas ->
                                if (!active()) return@capability
                                if (
                                    config.accessType.name !in setOf("READ", "READ_WRITE") ||
                                        seat !in areas
                                )
                                    finish(
                                        VehicleDiagnostic(
                                            DiagnosticStatus.UNSUPPORTED,
                                            "운전석 에어백 전개 조회를 지원하지 않습니다.",
                                        )
                                    )
                                else
                                    try {
                                        safety.hasSeatAirbagBeenDeployed(
                                            seat,
                                            onComplete = { value ->
                                                finish(
                                                    if (value == null)
                                                        VehicleDiagnostic(
                                                            DiagnosticStatus.UNAVAILABLE,
                                                            "에어백 신호값 없음",
                                                        )
                                                    else
                                                        VehicleDiagnostic(
                                                            DiagnosticStatus.READ,
                                                            "에어백 전개 신호 조회 · 사고·부상 판단 아님",
                                                            if (value) 1.0 else 0.0,
                                                        )
                                                )
                                            },
                                            onFailed = { error -> failed("에어백 신호 조회", error) },
                                        )
                                    } catch (error: Exception) {
                                        failed("에어백 신호 조회", error)
                                    }
                            },
                        onFailed = { error -> failed("에어백 지원 확인", error) },
                    )
                    return@execute
                }
                if (query == Query.BATTERY) {
                    val battery = client.getEvBattery()
                    battery.checkEvBatteryStateOfChargeCapabilities(
                        onComplete = capability@{ config ->
                                if (!active()) return@capability
                                val problem = BatteryReadPolicy.capability(config.accessType.name)
                                if (problem != null) finish(problem)
                                else
                                    try {
                                        battery.getEvBatteryStateOfCharge(
                                            onComplete = { value ->
                                                finish(BatteryReadPolicy.position(value))
                                            },
                                            onFailed = { error -> failed("배터리 충전 상태 조회", error) },
                                        )
                                    } catch (error: Exception) {
                                        failed("배터리 충전 상태 조회", error)
                                    }
                            },
                        onFailed = { error -> failed("배터리 지원 기능 확인", error) },
                    )
                    return@execute
                }
                val area = DoorArea(DoorValue.ROW_1_LEFT)
                val door = client.getDoor()
                door.checkDoorPositionCapabilities(
                    onComplete = capability@{ config, areas ->
                            if (!active()) return@capability
                            val bounds = areas[area]
                            val problem =
                                DoorReadPolicy.capabilityProblem(
                                    config.accessType.name,
                                    bounds != null,
                                    bounds?.minValue,
                                    bounds?.maxValue,
                                )
                            if (problem != null) {
                                finish(problem)
                            } else {
                                try {
                                    door.getCurrentDoorPosition(
                                        area = area,
                                        onComplete = { position ->
                                            finish(
                                                DoorReadPolicy.position(
                                                    position,
                                                    bounds?.minValue,
                                                    bounds?.maxValue,
                                                )
                                            )
                                        },
                                        onFailed = { error -> failed("도어 위치 조회", error) },
                                    )
                                } catch (error: Exception) {
                                    failed("도어 위치 조회", error)
                                }
                            }
                        },
                    onFailed = { error -> failed("지원 기능 확인", error) },
                )
            } catch (error: Exception) {
                failed(stage, error)
            } catch (error: LinkageError) {
                finish(
                    VehicleDiagnostic(
                        DiagnosticStatus.UNAVAILABLE,
                        "SDK 런타임 호환 오류 · ${error.javaClass.simpleName}\nSDK와 차량 서비스 버전을 확인하세요.",
                    )
                )
            } finally {
                workerState.complete()
            }
        }
    }

    fun release() {
        if (closed) return
        closed = true
        unsupportedAirbag = null
        generation.incrementAndGet()
        pending.set(false)
        main.removeCallbacksAndMessages(null)
        executor.execute {
            runCatching { vehicle?.release() }
            vehicle = null
            initialized = false
        }
        executor.shutdown()
    }
}
