package com.lifelink119.app.data

/** Local, deterministic readout for a human responder. Never prompt text or medical guidance. */
object EmergencyHealthSummary {
    fun build(record: PersonalHealthRecord): String = buildString {
        appendLine(
            if (record.webSource == null) "가족 안심콜 정보 · 차량에 직접 입력 · 공식 조회 아님"
            else "가족 안심콜 정보 · 웹화면 복사와 기존 입력 · 사용자 확인 · 기관 API 검증 아님"
        )
        record.webSource?.let {
            val fetched =
                java.text
                    .SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.KOREA)
                    .format(java.util.Date(it.retrievedAt))
            appendLine("웹 정보 가져온 시각: $fetched${if (it.editedAfterImport) " · 이후 직접 수정" else ""}")
        }
        fun field(name: String, value: String) {
            if (value.isNotBlank())
                appendLine("$name: ${value.replace(Regex("[\\r\\n\\t\\p{Cc}]+"), " ")}")
        }
        field("선택한 환자", record.name)
        field(
            "병력",
            listOf(record.conditions, record.safeCall.currentConditions.joinToString(", "))
                .filter(String::isNotBlank)
                .distinct()
                .joinToString(" / "),
        )
        field("알레르기", record.allergies)
        field("과거 질환·수술력", record.safeCall.pastHistory)
        field(
            if (record.webSource == null) "복용약 · 직접 입력" else "복용약 · 사용자 확인 필요",
            record.medicationHistory,
        )
        field("거동", record.safeCall.mobility)
        if (record.safeCall.canCall != OptionalAnswer.UNSPECIFIED)
            field("전화통화 가능", record.safeCall.canCall.label)
        field("통화 어려움", record.safeCall.callUnavailableReason)
        field("장애·의사소통 참고", record.safeCall.disabilities.joinToString(", "))
        field("구급대원 참고사항", record.safeCall.responderRequests)
        field(
            "불편사항·특이체질",
            listOf(record.safeCall.discomfort, record.safeCall.constitution)
                .filter(String::isNotBlank)
                .joinToString(" / "),
        )
        record.safeCall.guardians.firstOrNull()?.let { g ->
            field(
                "보호자 · 자동 연락 없음",
                listOf(g.name, g.relation, g.mobile.ifBlank { g.phone })
                    .filter(String::isNotBlank)
                    .joinToString(" / "),
            )
        }
        val updated =
            if (record.updatedAt > 0L)
                java.text
                    .SimpleDateFormat("yyyy-MM-dd", java.util.Locale.KOREA)
                    .format(java.util.Date(record.updatedAt))
            else "확인 안 됨"
        appendLine("마지막 수정: $updated · 현재 정보인지 상담원에게 확인해 주세요.")
        append("진단·투약 지침에 사용하지 않으며 외부 AI로 보내지 않습니다.")
    }
}
