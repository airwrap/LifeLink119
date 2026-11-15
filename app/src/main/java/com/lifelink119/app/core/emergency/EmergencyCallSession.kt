package com.lifelink119.app.core.emergency

enum class EmergencyCallStatus(val label: String) {
    PREPARING("신고 준비"),
    ATTEMPTING("119 연결 시도"),
    UNKNOWN("연결 여부 확인되지 않음"),
    OPERATOR("상담원 연결 확인"),
    CALLBOT("119 AI 콜봇 응대 중"),
    DELAYED("연결 지연 또는 실패"),
    CALLBACK("콜백 대기"),
    REPORTED("신고 접수 완료 안내를 받음"),
    DISPATCH_WAIT("구급차 출동 대기 안내를 받음"),
    ARRIVED("구조대 도착"),
}

data class EmergencyCallSession(
    val incidentId: String,
    val callAttemptTime: Long? = null,
    val connectionStatus: EmergencyCallStatus = EmergencyCallStatus.PREPARING,
    val callbackStatus: Evidence<Boolean> = Evidence(),
    val reportStatus: Evidence<Boolean> = Evidence(),
    val dispatchStatus: Evidence<Boolean> = Evidence(),
    val statusSource: EvidenceStatus = EvidenceStatus.UNKNOWN,
    val lastUpdatedAt: Long,
) {
    /**
     * A retry never implies reception or cancels access because another service might be involved.
     */
    fun attempt(now: Long) =
        copy(
            callAttemptTime = now,
            connectionStatus = EmergencyCallStatus.ATTEMPTING,
            statusSource = EvidenceStatus.SIMULATION,
            lastUpdatedAt = now,
        )

    fun unconfirmed(now: Long) =
        if (connectionStatus == EmergencyCallStatus.ATTEMPTING)
            copy(
                connectionStatus = EmergencyCallStatus.UNKNOWN,
                statusSource = EvidenceStatus.UNKNOWN,
                lastUpdatedAt = now,
            )
        else this

    fun confirm(
        next: EmergencyCallStatus,
        source: EvidenceStatus,
        now: Long,
        elapsed: Long,
    ): EmergencyCallSession {
        require(source == EvidenceStatus.USER || source == EvidenceStatus.SIMULATION)
        require(
            next !in
                setOf(
                    EmergencyCallStatus.PREPARING,
                    EmergencyCallStatus.ATTEMPTING,
                    EmergencyCallStatus.UNKNOWN,
                )
        )
        check(connectionStatus != EmergencyCallStatus.ARRIVED)
        val evidence = Evidence(true, source, "사용자가 직접 확인", now, elapsed)
        return copy(
            connectionStatus = next,
            statusSource = source,
            lastUpdatedAt = now,
            callbackStatus = if (next == EmergencyCallStatus.CALLBACK) evidence else callbackStatus,
            reportStatus = if (next == EmergencyCallStatus.REPORTED) evidence else reportStatus,
            dispatchStatus =
                if (next == EmergencyCallStatus.DISPATCH_WAIT) evidence else dispatchStatus,
        )
    }

    val voiceMustPause
        get() =
            connectionStatus in
                setOf(
                    EmergencyCallStatus.ATTEMPTING,
                    EmergencyCallStatus.UNKNOWN,
                    EmergencyCallStatus.OPERATOR,
                    EmergencyCallStatus.CALLBOT,
                    EmergencyCallStatus.ARRIVED,
                )
}
