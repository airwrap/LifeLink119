package com.lifelink119.app.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.lifelink119.app.BuildConfig
import com.lifelink119.app.core.emergency.*
import com.lifelink119.app.vehicle.*

/** Foreground collection only, independent of the existing mock connection's acknowledgement. */
class EmergencySupportController(
    context: Context,
    gateway: PleosVehicleGateway,
    private val changed: () -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    private val vehicle: VehicleDataProvider = PleosEmergencyVehicleProvider(gateway)
    private val location: LocationProvider = PleosEmergencyLocationProvider(context)
    private val navigation: NavigationProvider = PleosEmergencyNavigationProvider(context)
    private val route = RecentLocations()
    val ai = EmergencyAiCoordinator(System::currentTimeMillis)
    var snapshot: EmergencyContextSnapshot? = null
        private set

    var call: EmergencyCallSession? = null
        private set

    var scenario: EmergencyDemoScenario? = null
        private set

    var collectionConsent = false
        private set

    private val observationPrivacy = EmergencyObservationPrivacy()
    val backgroundLocked
        get() = observationPrivacy.locked

    var busy = false
        private set

    var revision = 0
        private set

    private var visible = false
    private var generation = 0L
    private val poll = Runnable { refresh() }

    fun attach(id: String) {
        if (snapshot?.incidentId == id) return
        clear()
        val now = System.currentTimeMillis()
        snapshot =
            EmergencyContextSnapshot(
                id,
                Evidence(
                    now,
                    EvidenceStatus.USER,
                    "앱 요청 시작 시각",
                    now,
                    SystemClock.elapsedRealtime(),
                ),
                collectedAt = now,
            )
        call = EmergencyCallSession(id, lastUpdatedAt = now)
        ai.attach(id)
        attempt()
    }

    fun attempt() {
        call = call?.attempt(System.currentTimeMillis())
        ai.cancelExternal()
        update()
        val id = call?.incidentId
        val time = call?.callAttemptTime
        main.postDelayed(
            {
                if (call?.incidentId == id && call?.callAttemptTime == time) {
                    call = call?.unconfirmed(System.currentTimeMillis())
                    update()
                }
            },
            1_200,
        )
    }

    fun confirm(status: EmergencyCallStatus) {
        call =
            call?.confirm(
                status,
                if (scenario == null) EvidenceStatus.USER else EvidenceStatus.SIMULATION,
                System.currentTimeMillis(),
                SystemClock.elapsedRealtime(),
            )
        ai.cancelExternal()
        if (status == EmergencyCallStatus.ARRIVED) {
            ai.stop()
            collection(false)
        }
        update()
    }

    fun foreground(value: Boolean) {
        visible = value
        if (!value) {
            generation++
            busy = false
            main.removeCallbacks(poll)
            route.clear()
            snapshot =
                snapshot?.copy(
                    recentRouteSummary = emptyList(),
                    currentLocation = Evidence(),
                    road = Evidence(),
                )
            lockPrivateObservations()
            ai.cancelExternal()
        } else if (collectionConsent) refresh()
    }

    fun unlock(authenticated: Boolean) {
        observationPrivacy.unlock(authenticated)
        update()
    }

    fun expireMedicalAuthentication() {
        observationPrivacy.expireAuthentication()
        if (backgroundLocked) ai.cancelExternal()
        update()
    }

    /** Leaving the private context never prevents retrying the emergency connection. */
    fun lockPrivateObservations() {
        observationPrivacy.leave(
            scenario == null && ai.session?.patientObservations?.isNotEmpty() == true
        )
        if (backgroundLocked) ai.cancelExternal()
    }

    fun collection(consent: Boolean) {
        collectionConsent = consent
        if (consent) refresh()
        else {
            generation++
            busy = false
            main.removeCallbacks(poll)
            route.clear()
            snapshot =
                snapshot?.copy(
                    vehicleState = VehicleState(),
                    currentLocation = Evidence(),
                    road = Evidence(),
                    recentRouteSummary = emptyList(),
                )
            update()
        }
    }

    fun selectScenario(value: EmergencyDemoScenario?) {
        require(value == null || BuildConfig.DEMO_FEATURES)
        collection(false)
        scenario = value
        call =
            EmergencyCallSession(
                requireNotNull(snapshot).incidentId,
                lastUpdatedAt = System.currentTimeMillis(),
            )
        attempt()
        ai.attach(requireNotNull(snapshot).incidentId)
        observationPrivacy.clear()
        snapshot =
            snapshot?.copy(
                incidentType =
                    Evidence(
                        value?.label,
                        if (value == null) EvidenceStatus.UNKNOWN else EvidenceStatus.SIMULATION,
                        if (value == null) "미입력" else "합성 시나리오",
                        System.currentTimeMillis(),
                        SystemClock.elapsedRealtime(),
                    ),
                passengerInformation = Evidence(),
            )
        if (value != null) collection(true) else update()
    }

    fun passengerCount(value: Int) {
        require(value in 1..12)
        snapshot =
            snapshot?.copy(
                passengerInformation =
                    Evidence(
                        value,
                        if (scenario == null) EvidenceStatus.USER else EvidenceStatus.SIMULATION,
                        "화면에서 인원 확인",
                        System.currentTimeMillis(),
                        SystemClock.elapsedRealtime(),
                    )
            )
        update()
    }

    fun incidentType(value: String) {
        require(value in setOf("사용자가 알린 차량 충돌", "사용자가 알린 응급질환", "상황 미확인"))
        snapshot =
            snapshot?.copy(
                incidentType =
                    Evidence(
                        value,
                        if (scenario == null) EvidenceStatus.USER else EvidenceStatus.SIMULATION,
                        "사용자가 직접 선택",
                        System.currentTimeMillis(),
                        SystemClock.elapsedRealtime(),
                    )
            )
        update()
    }

    fun answer(code: ObservationCode) {
        check(!backgroundLocked)
        ai.answer(code, if (scenario == null) EvidenceStatus.USER else EvidenceStatus.SIMULATION)
        update()
    }

    fun refresh() {
        val before = snapshot ?: return
        if (!visible || !collectionConsent || busy) return
        main.removeCallbacks(poll)
        busy = true
        val token = ++generation
        val mock =
            scenario?.let {
                MockEmergencyProviders(it, System::currentTimeMillis, SystemClock::elapsedRealtime)
            }
        var remaining = 3
        fun accept(change: (EmergencyContextSnapshot) -> EmergencyContextSnapshot) {
            main.post {
                if (
                    token != generation ||
                        !visible ||
                        !collectionConsent ||
                        snapshot?.incidentId != before.incidentId
                )
                    return@post
                snapshot =
                    change(requireNotNull(snapshot)).copy(collectedAt = System.currentTimeMillis())
                remaining--
                if (remaining == 0) {
                    busy = false
                    main.postDelayed(poll, 5_000)
                }
                update()
            }
        }
        (mock?.vehicle ?: vehicle).read { value -> accept { it.copy(vehicleState = value) } }
        (mock?.location ?: location).read { value ->
            accept {
                val now = System.currentTimeMillis()
                val elapsed = SystemClock.elapsedRealtime()
                route.add(value, now, elapsed)
                // Retain the last measured location as explicitly old if the next request fails.
                it.copy(
                    currentLocation =
                        if (value.value != null || it.currentLocation.value == null) value
                        else it.currentLocation,
                    recentRouteSummary = route.values(now, elapsed),
                )
            }
        }
        (mock?.navigation ?: navigation).read { value -> accept { it.copy(road = value) } }
        update()
    }

    fun summary(includeObservations: Boolean) =
        snapshot?.let { snap ->
            EmergencyPacket.build(
                snap,
                requireNotNull(call),
                requireNotNull(ai.session),
                System.currentTimeMillis(),
                SystemClock.elapsedRealtime(),
                includeObservations && !backgroundLocked,
            )
        } ?: "진행 중인 요청 없음"

    fun readout(includeObservations: Boolean) =
        snapshot?.let {
            EmergencyPacket.readAloud(
                it,
                requireNotNull(ai.session),
                System.currentTimeMillis(),
                SystemClock.elapsedRealtime(),
                includeObservations && !backgroundLocked,
            )
        } ?: "진행 중인 요청 없음"

    fun restore(value: EmergencyCheckpoint) {
        attach(value.call.incidentId)
        call =
            value.call.copy(
                connectionStatus = EmergencyCallStatus.UNKNOWN,
                statusSource = EvidenceStatus.UNKNOWN,
                lastUpdatedAt = System.currentTimeMillis(),
            )
        ai.restore(value.support)
        snapshot =
            snapshot?.copy(
                incidentTime =
                    Evidence(
                        value.startedAt,
                        EvidenceStatus.USER,
                        "암호화 복구 · 이전 요청 시각",
                        value.startedAt,
                        SystemClock.elapsedRealtime(),
                    )
            )
        observationPrivacy.restore()
        update()
    }

    private fun update() {
        revision++
        changed()
    }

    fun clear() {
        generation++
        main.removeCallbacksAndMessages(null)
        busy = false
        collectionConsent = false
        route.clear()
        ai.clear()
        snapshot = null
        call = null
        scenario = null
        observationPrivacy.clear()
    }

    fun close() {
        clear()
        location.close()
        navigation.close()
    }
}
