package com.lifelink119.app.core

import java.util.Locale

enum class VehicleMetric(val label: String) {
    DRIVER_DOOR("운전석 도어"),
    BATTERY_SOC("배터리 SoC"),
    DRIVER_AIRBAG("운전석 에어백 전개 신호"),
}

enum class VehicleDataOrigin(val label: String) {
    SDK("Pleos SDK 조회값"),
    EXAMPLE("예시 데이터"),
}

/** Device-local observations; no VIN, GPS, occupant identity or medical inference. */
data class VehicleSample(
    val metric: VehicleMetric,
    val origin: VehicleDataOrigin,
    val status: DiagnosticStatus,
    val value: Double?,
    val receivedAt: Long,
    val minimum: Double? = null,
    val maximum: Double? = null,
) {
    fun fresh(now: Long) =
        status == DiagnosticStatus.READ &&
            value?.isFinite() == true &&
            receivedAt >= 0 &&
            now >= receivedAt &&
            now - receivedAt <= VehicleContextPolicy.MAX_AGE_MS &&
            VehicleContextPolicy.valid(metric, value, minimum, maximum)

    fun display(now: Long): String {
        if (status != DiagnosticStatus.READ)
            return when (status) {
                DiagnosticStatus.UNSUPPORTED -> "이 차량에서 미지원"
                DiagnosticStatus.PERMISSION_DENIED -> "조회 권한 없음"
                DiagnosticStatus.TIMEOUT -> "응답 시간 초과"
                DiagnosticStatus.BUSY -> "다른 조회 처리 중"
                DiagnosticStatus.RELEASED -> "조회 종료"
                DiagnosticStatus.ERROR -> "값·응답 확인 필요"
                else -> "값을 확인하지 못함"
            }
        if (!fresh(now)) return "이전 조회값 · 다시 확인 필요"
        return when (metric) {
            VehicleMetric.BATTERY_SOC -> String.format(Locale.ROOT, "%.0f%%", value)
            VehicleMetric.DRIVER_AIRBAG -> if (value == 1.0) "전개 신호 있음" else "전개 신호 없음"
            VehicleMetric.DRIVER_DOOR ->
                "위치 신호 ${value?.toInt()} · 범위 ${minimum ?: "?"}~${maximum ?: "?"}"
        }
    }
}

data class VehicleContextSnapshot(
    val origin: VehicleDataOrigin = VehicleDataOrigin.SDK,
    val samples: Map<VehicleMetric, VehicleSample> = emptyMap(),
) {
    fun usable(now: Long) =
        samples
            .filter { (key, sample) -> key == sample.metric }
            .values
            .filter { it.origin == origin && it.fresh(now) }

    fun soc(now: Long) =
        samples[VehicleMetric.BATTERY_SOC]?.takeIf {
            it.metric == VehicleMetric.BATTERY_SOC && it.origin == origin && it.fresh(now)
        }
}

data class VehicleContextShare(
    val sessionId: String,
    val origin: VehicleDataOrigin,
    val samples: List<VehicleSample>,
    val consentAt: Long,
) {
    fun fresh(now: Long) =
        samples.isNotEmpty() &&
            now >= consentAt &&
            samples.all { it.origin == origin && it.fresh(now) }

    fun summary(now: Long) =
        origin.label +
            "\n" +
            samples.joinToString("\n") { it.metric.label + ": " + it.display(now) }
}

object VehicleContextPolicy {
    const val MAX_AGE_MS = 30_000L

    fun valid(metric: VehicleMetric, value: Double?, minimum: Double?, maximum: Double?): Boolean {
        if (
            value == null ||
                !value.isFinite() ||
                minimum?.isFinite() == false ||
                maximum?.isFinite() == false
        )
            return false
        if (minimum != null && maximum != null && minimum > maximum) return false
        if (minimum != null && value < minimum || maximum != null && value > maximum) return false
        return when (metric) {
            VehicleMetric.BATTERY_SOC -> value in 0.0..100.0 && value % 1.0 == 0.0
            VehicleMetric.DRIVER_AIRBAG -> value == 0.0 || value == 1.0
            VehicleMetric.DRIVER_DOOR -> value >= 0 && value % 1.0 == 0.0
        }
    }

    fun observation(metric: VehicleMetric, result: VehicleDiagnostic, now: Long): VehicleSample {
        val okay =
            result.status != DiagnosticStatus.READ ||
                valid(metric, result.rawValue, result.minimum, result.maximum)
        return VehicleSample(
            metric,
            VehicleDataOrigin.SDK,
            if (okay) result.status else DiagnosticStatus.ERROR,
            result.rawValue.takeIf { okay && result.status == DiagnosticStatus.READ },
            now,
            result.minimum,
            result.maximum,
        )
    }

    fun consent(
        snapshot: VehicleContextSnapshot,
        sessionId: String,
        accepted: Boolean,
        now: Long,
    ): VehicleContextShare {
        require(accepted) { "차량 관측정보 제공 동의가 필요합니다." }
        val values = snapshot.usable(now).map { it.copy() }
        require(values.isNotEmpty()) { "공유 가능한 최신 조회값이 없습니다." }
        return VehicleContextShare(sessionId, snapshot.origin, values, now)
    }

    fun example(kind: String, now: Long): VehicleContextSnapshot {
        require(kind in setOf("NORMAL", "INCIDENT", "NO_DATA", "STALE"))
        val at = if (kind == "STALE") now - MAX_AGE_MS - 1 else now
        fun sample(metric: VehicleMetric, value: Double, min: Double? = null, max: Double? = null) =
            VehicleSample(
                metric,
                VehicleDataOrigin.EXAMPLE,
                if (kind == "NO_DATA") DiagnosticStatus.UNAVAILABLE else DiagnosticStatus.READ,
                if (kind == "NO_DATA") null else value,
                at,
                min,
                max,
            )
        return VehicleContextSnapshot(
            VehicleDataOrigin.EXAMPLE,
            listOf(
                    sample(
                        VehicleMetric.DRIVER_DOOR,
                        if (kind == "INCIDENT") 1.0 else 0.0,
                        0.0,
                        1.0,
                    ),
                    sample(VehicleMetric.BATTERY_SOC, if (kind == "INCIDENT") 15.0 else 80.0),
                    sample(VehicleMetric.DRIVER_AIRBAG, if (kind == "INCIDENT") 1.0 else 0.0),
                )
                .associateBy { it.metric },
        )
    }
}
