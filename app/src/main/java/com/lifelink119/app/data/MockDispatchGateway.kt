package com.lifelink119.app.data

import com.lifelink119.app.core.EmergencyScenario
import com.lifelink119.app.core.FlowPolicy
import com.lifelink119.app.core.DemoShareSelection
import com.lifelink119.app.core.DemoCases
import java.util.Locale

/** DEMO 전용: 네트워크, 전화, 실시간 신고, 개인·위치정보 수집 없음. */
class MockDispatchGateway(initialSequence: Int = 0) {
    enum class Status { DEMO_CONNECTED, DEMO_CLOSED }
    data class FakeVehicle(val id: String = "DEMO-CAR-01", val name: String = "가상 차량 A", val batteryPercent: Int = 72)
    data class FakeLocation(val label: String = "가상 훈련장 A구역", val x: Int = 120, val y: Int = 80)
    data class Session(
        val ticket: String,
        val scenario: EmergencyScenario,
        val agency: String,
        val status: Status,
        val selection: DemoShareSelection,
        val vehicle: FakeVehicle?,
        val location: FakeLocation?,
        val caseCode: String = DemoCases.defaultCode(scenario),
        val videoLink: String? = null,
        val syntheticOnly: Boolean = true,
    ) {
        val message: String get() = "앱 안에서 만든 가상 세션입니다. 실제 신고·상담기관과 통신하지 않습니다."
    }

    var sequence: Int = initialSequence.coerceAtLeast(0)
        private set

    fun connect(scenario: EmergencyScenario, selection: DemoShareSelection = DemoShareSelection(), caseCode: String = DemoCases.defaultCode(scenario)): Session {
        require(DemoCases.accepts(scenario, caseCode)) { "시나리오와 가상 상황이 일치하지 않습니다." }
        require(!selection.video || scenario == EmergencyScenario.VIDEO) { "합성 영상은 영상 시나리오에서만 선택하세요." }
        check(sequence < Int.MAX_VALUE) { "데모 세션 번호 범위를 초과했습니다." }
        sequence += 1
        return describe("DEMO-%04d".format(Locale.ROOT, sequence), scenario, selection, Status.DEMO_CONNECTED, caseCode)
    }

    fun restore(ticket: String, scenario: EmergencyScenario, selection: DemoShareSelection, status: Status, caseCode: String = DemoCases.defaultCode(scenario)): Session {
        require(DemoCases.accepts(scenario, caseCode)) { "잘못된 가상 상황" }
        require(!selection.video || scenario == EmergencyScenario.VIDEO) { "잘못된 합성 영상 선택" }
        require(ticket.matches(Regex("DEMO-[0-9]{4,}"))) { "잘못된 데모 세션 번호" }
        val restoredSequence = ticket.removePrefix("DEMO-").toIntOrNull() ?: error("잘못된 데모 세션 번호")
        sequence = maxOf(sequence, restoredSequence)
        return describe(ticket, scenario, selection, status, caseCode)
    }

    fun close(session: Session): Session = session.copy(status = Status.DEMO_CLOSED)

    private fun describe(ticket: String, scenario: EmergencyScenario, selection: DemoShareSelection, status: Status, caseCode: String) = Session(
            ticket = ticket,
            scenario = scenario,
            agency = FlowPolicy.agency(scenario),
            status = status,
            selection = selection,
            vehicle = if (selection.vehicle) FakeVehicle() else null,
            location = if (selection.location) FakeLocation() else null,
            caseCode = caseCode,
            videoLink = if (selection.video) "demo://video/$ticket" else null,
        )
}
