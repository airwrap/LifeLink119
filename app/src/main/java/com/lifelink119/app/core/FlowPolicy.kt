package com.lifelink119.app.core

/** 의학적 위험도 판단 엔진이 아님. 단순 안내문/시연 라우팅만 담당. */
object FlowPolicy {
    fun agency(scenario: EmergencyScenario): String = when (scenario) {
        EmergencyScenario.MEDICAL, EmergencyScenario.VIDEO, EmergencyScenario.TRANSPORT -> "119 (모의)"
        EmergencyScenario.POLICE -> "112 (모의)"
        EmergencyScenario.WELLBEING -> "109 (모의)"
    }

    fun requiresProfessionalDecision(scenario: EmergencyScenario): Boolean =
        scenario == EmergencyScenario.TRANSPORT || scenario == EmergencyScenario.POLICE
}
