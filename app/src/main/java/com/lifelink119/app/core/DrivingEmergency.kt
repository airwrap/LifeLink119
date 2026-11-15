package com.lifelink119.app.core

enum class DrivingTrigger(val label: String) {
    TOUCH("빠른 호출 버튼"),
    VOICE_EXAMPLE("음성 요청 예시 · 실제 인식 아님"),
}

enum class DrivingSafety(val label: String) {
    MOVING("주행 중 · 시연"),
    STOPPED_REPORTED("안전한 곳에 정차했다고 알림 · 시연"),
    SAFE_PLACE_REPORTED("주변을 확인하고 안전을 확보했다고 알림 · 시연"),
}

enum class DrivingReport(val label: String) {
    DRAFT("상황 전달 전"),
    SENT("가상 상황실에 전달됨"),
    ACKNOWLEDGED("가상 상황실 확인 완료"),
    HANDED_OVER("가상 대응팀 인계 완료"),
}

/** Fixed synthetic choices only. No microphone, real location, telephony or vehicle actuation. */
data class DrivingEmergencyState(
    val trigger: DrivingTrigger = DrivingTrigger.TOUCH,
    val safety: DrivingSafety = DrivingSafety.MOVING,
    val report: DrivingReport = DrivingReport.DRAFT,
    val reconfirm: Boolean = false,
) {
    fun send(): DrivingEmergencyState {
        check(report == DrivingReport.DRAFT)
        return copy(report = DrivingReport.SENT, reconfirm = false)
    }

    fun acknowledge(): DrivingEmergencyState {
        check(report == DrivingReport.SENT)
        return copy(report = DrivingReport.ACKNOWLEDGED)
    }

    fun stopped(): DrivingEmergencyState {
        check(safety == DrivingSafety.MOVING)
        return copy(safety = DrivingSafety.STOPPED_REPORTED)
    }

    fun safe(): DrivingEmergencyState {
        check(safety == DrivingSafety.STOPPED_REPORTED)
        return copy(safety = DrivingSafety.SAFE_PLACE_REPORTED)
    }

    fun handover(): DrivingEmergencyState {
        check(report == DrivingReport.ACKNOWLEDGED && safety == DrivingSafety.SAFE_PLACE_REPORTED)
        return copy(report = DrivingReport.HANDED_OVER)
    }

    fun privacySafe() = DrivingEmergencyState(trigger = trigger, reconfirm = true)

    fun summary() =
        "가상 119 상황실\n도움 요청: 차 안의 위급 상황\n구체적인 내용은 연결 후 상황실에 설명합니다.\n안전 상태: ${safety.label}\n인원·부상 여부: 미확인 · 임의 판단 없음\n위치·차량: 별도 동의한 가상 항목만 표시\n실제 접수·출동 없음"

    val guidance = "주변 교통을 살피고 안전한 곳에 정차하세요. 정차 전에도 119 상황실에 도움을 요청할 수 있습니다."
}
