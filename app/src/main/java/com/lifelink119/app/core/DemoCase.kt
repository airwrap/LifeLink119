package com.lifelink119.app.core

data class DemoCase(val code: String, val label: String)

object DemoCases {
    fun options(scenario: EmergencyScenario): List<DemoCase> =
        when (scenario) {
            EmergencyScenario.MEDICAL ->
                listOf(
                    DemoCase("MEDICAL_FAMILY_A", "가상 상황 A · 동승자 도움 요청"),
                    DemoCase("MEDICAL_FAMILY_B", "가상 상황 B · 가족과 상황 설명 연습"),
                )
            EmergencyScenario.VIDEO ->
                listOf(
                    DemoCase("VIDEO_SCENE_A", "가상 상황 A · 현장 공유 연습"),
                    DemoCase("VIDEO_SCENE_B", "가상 상황 B · 정보 선택 확인 연습"),
                )
            EmergencyScenario.TRANSPORT ->
                listOf(
                    DemoCase("TRANSPORT_MEET_A", "가상 상황 A · 만남 지점 확인"),
                    DemoCase("TRANSPORT_REVIEW_B", "가상 상황 B · 공동대응 검토"),
                )
            EmergencyScenario.POLICE ->
                listOf(
                    DemoCase("POLICE_REVIEW_A", "가상 상황 A · 공동대응 검토 요청"),
                    DemoCase("POLICE_MEET_B", "가상 상황 B · 만남 지점 안내 연습"),
                )
            EmergencyScenario.WELLBEING ->
                listOf(
                    DemoCase("WELLBEING_TALK_A", "가상 상황 A · 전문상담 요청 연습"),
                    DemoCase("WELLBEING_FAMILY_B", "가상 상황 B · 가족과 도움 요청 연습"),
                )
        }

    fun defaultCode(scenario: EmergencyScenario) = options(scenario).first().code

    fun accepts(scenario: EmergencyScenario, code: String) =
        options(scenario).any { it.code == code }
}

/** Scenario context only; never a lock on emergency assistance or a speed sensor. */
enum class DemoDriveState {
    UNKNOWN,
    PARKED,
    DRIVING,
}
