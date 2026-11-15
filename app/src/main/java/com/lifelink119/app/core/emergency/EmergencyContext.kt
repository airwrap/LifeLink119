package com.lifelink119.app.core.emergency

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class EvidenceStatus(val label: String) {
    VEHICLE("차량 SDK 확인"),
    USER("사용자 입력"),
    SIMULATION("시뮬레이션"),
    UNSUPPORTED("지원되지 않음"),
    UNKNOWN("확인 불가"),
}

/** Measurement time is never replaced with receipt time. No identifying values in diagnostics. */
data class Evidence<T>(
    val value: T? = null,
    val status: EvidenceStatus = EvidenceStatus.UNKNOWN,
    val source: String = "수집 전",
    val measuredAt: Long? = null,
    val receivedElapsed: Long? = null,
) {
    fun fresh(wall: Long, elapsed: Long, ttl: Long = 30_000): Boolean =
        value != null &&
            status in
                setOf(EvidenceStatus.VEHICLE, EvidenceStatus.USER, EvidenceStatus.SIMULATION) &&
            measuredAt != null &&
            measuredAt > 0 &&
            wall >= measuredAt &&
            wall - measuredAt <= ttl &&
            receivedElapsed != null &&
            elapsed >= receivedElapsed &&
            elapsed - receivedElapsed <= ttl

    override fun toString() = "Evidence(${status.name}, value=REDACTED)"
}

data class LocationFix(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Double? = null,
    val headingDegrees: Double? = null,
) {
    fun valid() =
        latitude.isFinite() &&
            longitude.isFinite() &&
            latitude in -90.0..90.0 &&
            longitude in -180.0..180.0 &&
            (accuracyMeters == null || accuracyMeters.isFinite() && accuracyMeters >= 0) &&
            (headingDegrees == null || headingDegrees.isFinite() && headingDegrees in 0.0..<360.0)

    override fun toString() = "LocationFix(REDACTED)"
}

enum class VehicleField(val label: String) {
    DOOR("운전석 도어"),
    BATTERY("EV 배터리"),
    MOTION("주행·정차"),
    SPEED("차량 속도"),
    PARKING_BRAKE("주차 브레이크"),
    BASIC_INFO("차량 기본 정보"),
    WARNING("안전 경고"),
}

data class VehicleState(
    val values: Map<VehicleField, Evidence<String>> =
        VehicleField.entries.associateWith { Evidence() },
    val airbag: Evidence<String> = Evidence(),
    val collision: Evidence<String> = Evidence(),
)

data class EmergencyContextSnapshot(
    val incidentId: String,
    val incidentTime: Evidence<Long>,
    val incidentType: Evidence<String> = Evidence(),
    val vehicleState: VehicleState = VehicleState(),
    val currentLocation: Evidence<LocationFix> = Evidence(),
    val road: Evidence<String> = Evidence(),
    val recentRouteSummary: List<Evidence<LocationFix>> = emptyList(),
    val passengerInformation: Evidence<Int> = Evidence(),
    val collectedAt: Long,
) {
    val airbagState
        get() = vehicleState.airbag

    val collisionState
        get() = vehicleState.collision

    val locationAccuracy
        get() = currentLocation.value?.accuracyMeters

    val locationTimestamp
        get() = currentLocation.measuredAt

    val heading
        get() = currentLocation.value?.headingDegrees

    val dataSources
        get() =
            vehicleState.values.values.map { it.source } +
                listOf(currentLocation.source, road.source)

    override fun toString() = "EmergencyContextSnapshot(REDACTED)"
}

interface VehicleDataProvider {
    fun read(done: (VehicleState) -> Unit)
}

interface LocationProvider {
    fun read(done: (Evidence<LocationFix>) -> Unit)

    fun close() {}
}

interface NavigationProvider {
    fun read(done: (Evidence<String>) -> Unit)

    fun close() {}
}

/**
 * At most six distinct fixes over two minutes, memory only. Cleared on consent
 * withdrawal/background.
 */
class RecentLocations {
    private var samples = emptyList<Evidence<LocationFix>>()

    fun add(value: Evidence<LocationFix>, wall: Long, elapsed: Long) {
        samples = values(wall, elapsed)
        if (
            value.fresh(wall, elapsed) &&
                value.value?.valid() == true &&
                samples.none { it.measuredAt == value.measuredAt }
        )
            samples = (samples + value).takeLast(6)
    }

    fun values(wall: Long, elapsed: Long): List<Evidence<LocationFix>> {
        samples = samples.filter { it.fresh(wall, elapsed, 120_000) }
        return samples.toList()
    }

    fun clear() {
        samples = emptyList()
    }
}

object EmergencyPacket {
    fun time(value: Long?) =
        value?.let { SimpleDateFormat("HH:mm:ss", Locale.KOREA).format(Date(it)) } ?: "시각 미확인"

    fun <T> field(
        e: Evidence<T>,
        wall: Long,
        elapsed: Long,
        format: (T) -> String = { it.toString() },
    ): String {
        val label = "${e.status.label} · ${e.source} · ${time(e.measuredAt)}"
        if (e.value == null) return "${e.status.label} [$label]"
        return if (e.fresh(wall, elapsed)) "${format(e.value)} [$label]"
        else "이전/시각 미확인 자료 · 현재 상태 아님 [$label]"
    }

    fun location(e: Evidence<LocationFix>, wall: Long, elapsed: Long): String {
        if (e.value?.valid() == false) return "위치값 확인 불가"
        return field(e, wall, elapsed) { fix ->
            String.format(Locale.ROOT, "%.5f, %.5f", fix.latitude, fix.longitude) +
                (fix.accuracyMeters?.let { " · 정확도 ±${it.toInt()}m" } ?: " · 정확도 미확인") +
                (fix.headingDegrees?.let { " · 방향 ${it.toInt()}°" } ?: " · 방향 미확인")
        }
    }

    /** Short read-aloud view; stale and unverified evidence can never become a current fact. */
    fun readAloud(
        s: EmergencyContextSnapshot,
        support: EmergencySupportSession,
        wall: Long,
        elapsed: Long,
        includeObservations: Boolean,
    ): String = buildString {
        fun current(e: Evidence<String>) =
            if (e.fresh(wall, elapsed)) "${e.value} · ${e.status.label}" else "현재 상태 미확인"
        appendLine("상황: ${s.incidentType.value ?: "아직 설명하지 않음"} · ${s.incidentType.status.label}")
        appendLine("위치: ${location(s.currentLocation, wall, elapsed)}")
        if (s.road.fresh(wall, elapsed)) appendLine("도로·지점: ${current(s.road)}")
        appendLine("차량: ${current(s.vehicleState.values[VehicleField.MOTION] ?: Evidence())}")
        appendLine("에어백: ${current(s.airbagState)} · 미전개도 안전을 뜻하지 않음")
        appendLine(
            "탑승자: ${s.passengerInformation.value?.let { "$it 명 · ${s.passengerInformation.status.label}" } ?: "미확인"}"
        )
        appendLine("환자 상태: ${if (includeObservations) support.handover() else "비공유 · 별도 확인 필요"}")
        appendLine("앱 요청 시작: ${time(s.incidentTime.value)} · 사고 시각과 다를 수 있음")
        append("실제 기관 자동 전송 없음 · 확인한 내용과 위치를 상담원에게 알려주세요.")
    }

    fun build(
        s: EmergencyContextSnapshot,
        call: EmergencyCallSession,
        support: EmergencySupportSession,
        wall: Long,
        elapsed: Long,
        includeObservations: Boolean,
    ): String = buildString {
        appendLine("[LifeLink 119 응급상황 요약 · 실제 기관 전송 없음]")
        appendLine(
            "상황: ${s.incidentType.value ?: "미확인"} · ${s.incidentType.status.label} · ${time(s.incidentType.measuredAt)}"
        )
        appendLine("요청 시작: ${time(s.incidentTime.value)} · 사고 발생 시각과 다를 수 있음")
        appendLine("위치: ${location(s.currentLocation, wall, elapsed)}")
        if (!s.currentLocation.fresh(wall, elapsed) && s.currentLocation.value?.valid() == true)
            appendLine(
                "마지막 확인 좌표: ${s.currentLocation.value.latitude}, ${s.currentLocation.value.longitude} · ${time(s.locationTimestamp)} · 현재 위치 아님"
            )
        appendLine("도로·지점: ${field(s.road, wall, elapsed)}")
        if (s.road.value != null && s.road.measuredAt == null)
            appendLine("내비 보고 주소: ${s.road.value} · 측정 시각 미제공, 현재 위치와 대조 필요")
        appendLine("에어백: ${field(s.airbagState, wall, elapsed)} · 신호 없음/미전개로 안전 여부 판단 불가")
        appendLine("충돌 이벤트: ${field(s.collisionState, wall, elapsed)}")
        s.vehicleState.values.forEach { (key, value) ->
            appendLine("${key.label}: ${field(value, wall, elapsed)}")
        }
        val route = s.recentRouteSummary.filter { it.fresh(wall, elapsed, 120_000) }
        if (route.isNotEmpty())
            appendLine(
                "최근 위치: 2분 이내 ${route.size}개 표본 · 마지막 변화 ${time(route.last().measuredAt)} · 장기 이력 없음"
            )
        appendLine(
            "탑승자: ${s.passengerInformation.value?.let { "$it 명 · ${s.passengerInformation.status.label} · ${time(s.passengerInformation.measuredAt)}" } ?: "미확인"}"
        )
        appendLine(
            "신고 진행: ${call.connectionStatus.label} · ${call.statusSource.label} · ${time(call.lastUpdatedAt)}"
        )
        appendLine("최근 연결 시도: ${time(call.callAttemptTime)} · 실제 전화 상태 미연동")
        appendLine("환자 상태 (차량 신호로 추정하지 않음):")
        appendLine(if (includeObservations) support.handover() else "비공유 · 별도 확인 필요")
        append("저장 가족 건강정보: 자동 포함하지 않음 · 별도 인증/동의 필요\n접수·출동·콜백 예약 및 도착 예상 시간은 확인할 수 없습니다.")
    }
}
