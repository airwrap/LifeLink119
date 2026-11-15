package com.lifelink119.app.ui

import com.lifelink119.app.data.SafeCallDetails

internal object SafeCallSummary {
    fun sections(d: SafeCallDetails): List<Pair<String, List<Pair<String, String>>>> =
        listOf(
            "1. 신청자 인적사항" to
                listOf(
                    "신청유형" to d.applicationType,
                    "신청유형 상세" to d.applicationDetail,
                    "거동여부" to d.mobility,
                    "혈액형" to
                        listOf(d.bloodGroup, d.rhesus).filter { it.isNotBlank() }.joinToString(" "),
                    "승강기 설치여부" to d.elevator.label,
                    "전화통화 가능여부" to d.canCall.label,
                    "전화통화 불가사유" to d.callUnavailableReason,
                ),
            "2. 질병사항·진료의료기관" to
                listOf(
                    "과거질환 (수술력)" to d.pastHistory,
                    "현재질환" to d.currentConditions.joinToString(" · "),
                    "법정감염병" to d.infectiousDiseases.joinToString(" · "),
                    "진료기관명" to d.facilityName,
                    "진료과목" to d.specialty,
                    "진료기관 연락처 (주간)" to d.dayPhone,
                ),
            "3. 장애·기타 구급관련 정보" to
                listOf(
                    "진료기관 연락처 (야간)" to d.nightPhone,
                    "장애유형" to d.disabilities.joinToString(" · "),
                    "불편사항" to d.discomfort,
                    "특이체질" to d.constitution,
                    "위급 시 구급대원에게 요구사항" to d.responderRequests,
                    "자주 가는 곳 (기타)" to d.frequentPlace,
                ),
            "4. 보호자정보" to
                d.guardians.flatMapIndexed { index, g ->
                    listOf(
                        "보호자 ${index+1} 성함" to g.name,
                        "관계" to
                            listOf(g.relationCategory, g.relation)
                                .filter { it.isNotBlank() }
                                .joinToString(" · "),
                        "휴대폰 번호" to g.mobile,
                    )
                },
            "5. 보호자 추가 연락정보" to
                d.guardians.flatMapIndexed { index, g ->
                    listOf(
                        "보호자 ${index+1} 전화번호" to g.phone,
                        "이메일" to g.email,
                        "거주구분" to g.living.label,
                        "SMS 수신 선호 · 발송 기능 아님" to g.smsPreference.label,
                    )
                },
        )
}
