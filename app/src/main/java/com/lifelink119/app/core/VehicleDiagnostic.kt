package com.lifelink119.app.core

enum class DiagnosticStatus {
    READ, UNSUPPORTED, UNAVAILABLE, PERMISSION_DENIED, SERVICE_UNAVAILABLE,
    TIMEOUT, BUSY, RELEASED, ERROR,
}

data class VehicleDiagnostic(val status: DiagnosticStatus, val message: String,
    val rawValue: Double? = null, val minimum: Double? = null, val maximum: Double? = null)

object BatteryReadPolicy {
    fun capability(access: String): VehicleDiagnostic? = if (access == "READ" || access == "READ_WRITE") null
        else VehicleDiagnostic(DiagnosticStatus.UNSUPPORTED, "배터리 충전 상태 조회를 지원하지 않습니다. 접근 수준: $access")

    fun position(value: Int?): VehicleDiagnostic = if (value == null)
        VehicleDiagnostic(DiagnosticStatus.UNAVAILABLE, "현재 배터리 충전 상태 값이 없습니다.")
    else if(value !in 0..100)VehicleDiagnostic(DiagnosticStatus.ERROR,"배터리 SoC 값이 0~100% 범위 밖입니다.")
    else VehicleDiagnostic(DiagnosticStatus.READ, "배터리 충전 상태 SDK 조회값: $value%\n공식 2.3.2 배포 소스의 백분율 계약을 적용합니다.",value.toDouble())
}

/** Read-only checks; a door result never participates in emergency routing. */
object DoorReadPolicy {
    fun capabilityProblem(access: String, hasDriverArea: Boolean, min: Int?, max: Int?): VehicleDiagnostic? = when {
        access != "READ" && access != "READ_WRITE" -> VehicleDiagnostic(
            DiagnosticStatus.UNSUPPORTED, "도어 위치 조회를 지원하지 않습니다. 접근 수준: $access",
        )
        !hasDriverArea -> VehicleDiagnostic(DiagnosticStatus.UNSUPPORTED, "운전석 도어 영역이 지원 목록에 없습니다.")
        min != null && max != null && min > max -> VehicleDiagnostic(DiagnosticStatus.ERROR, "SDK가 잘못된 조회 범위를 반환했습니다.")
        else -> null
    }

    fun position(value: Int?, min: Int?, max: Int?): VehicleDiagnostic = when {
        value == null -> VehicleDiagnostic(DiagnosticStatus.UNAVAILABLE, "차량 서비스에 연결했지만 현재 도어 위치 값이 없습니다.")
        (min != null && value < min) || (max != null && value > max) -> VehicleDiagnostic(
            DiagnosticStatus.ERROR, "도어 위치 값 $value 이 지원 범위 ${min ?: "?"}~${max ?: "?"} 밖입니다.",
        )
        else -> VehicleDiagnostic(DiagnosticStatus.READ, "도어 상태 조회 성공 · 운전석 위치 값: $value\n지원 범위: ${min ?: "?"}~${max ?: "?"} · 의료 판단에 사용하지 않음",value.toDouble(),min?.toDouble(),max?.toDouble())
    }

    fun failure(stage: String, error: Exception): VehicleDiagnostic {
        // Exact public service error codes, observed through the official SDK.
        // Do not depend on its obfuscated/private exception implementation.
        val sdkStatus=when(error.message){
            "ERROR_NOT_SUPPORTED_PROPERTY","ERROR_NOT_SUPPORTED_AREA"->DiagnosticStatus.UNSUPPORTED
            "ERROR_SECURITY_PERMISSION"->DiagnosticStatus.PERMISSION_DENIED
            "ERROR_EMPTY_RESULT"->DiagnosticStatus.UNAVAILABLE
            "ERROR_SERVICE_CONNECTION_FAILURE"->DiagnosticStatus.SERVICE_UNAVAILABLE
            else->null
        }
        val status = sdkStatus ?: when (error) {
            is SecurityException -> DiagnosticStatus.PERMISSION_DENIED
            is UnsupportedOperationException -> DiagnosticStatus.UNSUPPORTED
            else -> if (stage == "초기화") DiagnosticStatus.SERVICE_UNAVAILABLE else DiagnosticStatus.ERROR
        }
        val recovery = when (status) {
            DiagnosticStatus.PERMISSION_DENIED -> "요청 기능의 CAR_DOORS/CAR_ENERGY/CAR_SAFETY 권한과 SDK 바인딩 권한을 확인하세요."
            DiagnosticStatus.UNSUPPORTED -> "이 환경에서 지원되지 않는 조회입니다. 제어 API로 우회하지 않습니다."
            else -> "Pleos 전용 AVD의 부팅과 차량 서비스를 확인한 뒤 다시 시도하세요."
        }
        return VehicleDiagnostic(status, "$stage 실패 · ${error.javaClass.simpleName}\n${error.message ?: "상세 메시지 없음"}\n$recovery")
    }
}
