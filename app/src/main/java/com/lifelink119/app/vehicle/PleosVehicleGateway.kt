package com.lifelink119.app.vehicle

import android.content.Context
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.DoorArea
import ai.pleos.playground.vehicle.constant.area.DoorValue
import android.os.Handler
import android.os.Looper
import com.lifelink119.app.core.DiagnosticStatus
import com.lifelink119.app.core.DoorReadPolicy
import com.lifelink119.app.core.VehicleDiagnostic
import com.lifelink119.app.core.BatteryReadPolicy
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * 공식 Vehicle SDK 2.3.2 검증 전용 코드.
 * 도어 조회는 SDK 연결 확인을 위한 테스트이고 응급 상황 판단에는 사용하지 않음.
 * 카메라/GPS/전화/의료정보에 접근하지 않음.
 */
class PleosVehicleGateway(context: Context) {
    private enum class Query { DOOR, BATTERY }
    private val applicationContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor { task -> Thread(task, "lifelink-door-diagnostic").apply { isDaemon = true } }
    private var vehicle: Vehicle? = null
    private var initialized = false
    @Volatile private var closed = false
    @Volatile private var reconnect = false
    private val pending = AtomicBoolean(false)
    private val workerBusy = AtomicBoolean(false)
    private val generation = AtomicLong(0)

    fun readDriverDoor(onResult: (VehicleDiagnostic) -> Unit) = read(Query.DOOR, onResult)
    fun readBatteryCharge(onResult: (VehicleDiagnostic) -> Unit) = read(Query.BATTERY, onResult)

    private fun read(query: Query, onResult: (VehicleDiagnostic) -> Unit) {
        if (closed) {
            main.post { onResult(VehicleDiagnostic(DiagnosticStatus.RELEASED, "SDK 진단이 종료됐습니다. 화면을 다시 여세요.")) }
            return
        }
        if (!pending.compareAndSet(false, true)) {
            main.post { onResult(VehicleDiagnostic(DiagnosticStatus.BUSY, "진행 중인 차량 조회가 있습니다.")) }
            return
        }
        if (!workerBusy.compareAndSet(false, true)) {
            pending.set(false)
            main.post { onResult(VehicleDiagnostic(DiagnosticStatus.BUSY, "차량 서비스가 이전 요청을 처리 중입니다. 잠시 후 다시 시도하세요.")) }
            return
        }
        val token = generation.incrementAndGet()
        val completed = AtomicBoolean(false)
        lateinit var timeout: Runnable
        fun active() = !closed && token == generation.get() && !completed.get()
        fun finish(result: VehicleDiagnostic) {
            if (closed || token != generation.get() || !completed.compareAndSet(false, true)) return
            main.removeCallbacks(timeout)
            pending.set(false)
            main.post { if (!closed && token == generation.get()) onResult(result) }
        }
        fun failed(stage: String, error: Exception) {
            if (!active()) return
            reconnect = true
            finish(DoorReadPolicy.failure(stage, error))
        }
        timeout = Runnable {
            if (!active()) return@Runnable
            reconnect = true
            finish(VehicleDiagnostic(DiagnosticStatus.TIMEOUT, "15초 동안 응답이 없습니다.\nPleos 차량 서비스 상태를 확인하고 다시 시도하세요."))
        }
        main.postDelayed(timeout, 15_000)
        executor.execute {
            var stage = "초기화"
            try {
                if (!active()) return@execute
                if (reconnect) {
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
                if (query == Query.BATTERY) {
                    val battery = client.getEvBattery()
                    battery.checkEvBatteryStateOfChargeCapabilities(
                        onComplete = capability@{ config ->
                            if (!active()) return@capability
                            val problem = BatteryReadPolicy.capability(config.accessType.name)
                            if (problem != null) finish(problem) else try {
                                battery.getEvBatteryStateOfCharge(
                                    onComplete = { value -> finish(BatteryReadPolicy.position(value)) },
                                    onFailed = { error -> failed("배터리 충전 상태 조회", error) },
                                )
                            } catch (error: Exception) { failed("배터리 충전 상태 조회", error) }
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
                        val problem = DoorReadPolicy.capabilityProblem(config.accessType.name, bounds != null, bounds?.minValue, bounds?.maxValue)
                        if (problem != null) {
                            finish(problem)
                        } else {
                            try {
                                door.getCurrentDoorPosition(
                                    area = area,
                                    onComplete = { position -> finish(DoorReadPolicy.position(position, bounds?.minValue, bounds?.maxValue)) },
                                    onFailed = { error -> failed("도어 위치 조회", error) },
                                )
                            } catch (error: Exception) { failed("도어 위치 조회", error) }
                        }
                    },
                    onFailed = { error -> failed("지원 기능 확인", error) },
                )
            } catch (error: Exception) {
                failed(stage, error)
            } catch (error: LinkageError) {
                finish(VehicleDiagnostic(DiagnosticStatus.UNAVAILABLE, "SDK 런타임 호환 오류 · ${error.javaClass.simpleName}\nSDK와 차량 서비스 버전을 확인하세요."))
            } finally {
                workerBusy.set(false)
            }
        }
    }

    fun release() {
        if (closed) return
        closed = true
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
