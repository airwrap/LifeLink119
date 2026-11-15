package com.lifelink119.app.core.emergency

enum class EmergencyDemoScenario(val label: String) {
    AIRBAG("에어백 전개 사고"),
    COLLISION("에어백 미전개 충돌"),
    ILLNESS("운전자 응급질환"),
    HIGHWAY("고속도로 긴급상황"),
    NO_DATA("위치·차량 수신 실패"),
}

/** Explicit selection only. Never used as a fallback for a real SDK failure. */
class MockEmergencyProviders(
    private val scenario: EmergencyDemoScenario,
    private val wall: () -> Long,
    private val elapsed: () -> Long,
) {
    private fun <T> sample(value: T) =
        Evidence(value, EvidenceStatus.SIMULATION, "선택한 합성 시나리오", wall(), elapsed())

    val vehicle =
        object : VehicleDataProvider {
            override fun read(done: (VehicleState) -> Unit) {
                if (scenario == EmergencyDemoScenario.NO_DATA) {
                    done(VehicleState())
                    return
                }
                val moving = scenario == EmergencyDemoScenario.HIGHWAY
                done(
                    VehicleState(
                        VehicleField.entries.associateWith { field ->
                            sample(
                                when (field) {
                                    VehicleField.DOOR -> "닫힘"
                                    VehicleField.BATTERY -> "72%"
                                    VehicleField.MOTION -> if (moving) "주행 중" else "정차"
                                    VehicleField.SPEED -> if (moving) "80 km/h" else "0 km/h"
                                    VehicleField.PARKING_BRAKE -> "미확인 · 합성"
                                    VehicleField.BASIC_INFO -> "가상 차량 DEMO-CAR"
                                    VehicleField.WARNING -> "판단하지 않음"
                                }
                            )
                        },
                        sample(if (scenario == EmergencyDemoScenario.AIRBAG) "전개" else "미전개"),
                        if (
                            scenario in
                                setOf(EmergencyDemoScenario.AIRBAG, EmergencyDemoScenario.COLLISION)
                        )
                            sample("모의 충돌 이벤트")
                        else Evidence(),
                    )
                )
            }
        }
    val location =
        object : LocationProvider {
            override fun read(done: (Evidence<LocationFix>) -> Unit) {
                done(
                    if (scenario == EmergencyDemoScenario.NO_DATA) Evidence(source = "수신 실패 시뮬레이션")
                    else sample(LocationFix(37.5665, 126.9780, 20.0, 90.0))
                )
            }
        }
    val navigation =
        object : NavigationProvider {
            override fun read(done: (Evidence<String>) -> Unit) {
                done(
                    if (scenario == EmergencyDemoScenario.NO_DATA) Evidence(source = "수신 실패 시뮬레이션")
                    else
                        sample(
                            if (scenario == EmergencyDemoScenario.HIGHWAY)
                                "가상 고속도로 A · 동쪽 · 실제 지명 아님"
                            else "가상 훈련장 · 실제 도로명 아님"
                        )
                )
            }
        }
}

class UnavailableNavigationProvider : NavigationProvider {
    override fun read(done: (Evidence<String>) -> Unit) =
        done(Evidence(status = EvidenceStatus.UNSUPPORTED, source = "NaviHelper 도로명·측정 시각 어댑터 미검증"))
}
