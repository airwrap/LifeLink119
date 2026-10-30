package com.lifelink119.app.data

/** 향후 협약 및 제조사 API 공개 후 구현할 추상화. */
interface CameraGateway {
    fun capability(): String
    fun sharePreview(): String
}

/** 빌트인 캠 영상 접근 API를 확인하지 못했으므로 반드시 비활성화. */
class UnavailableCameraGateway : CameraGateway {
    override fun capability() = "빌트인 캠 API 미연결 · 모의 시연만 가능"
    override fun sharePreview() = "영상 공유를 시뮬레이션했습니다. 실제 촬영·전송은 수행하지 않았습니다."
}

/** Explicit offline adapter used by the expanded UI; no camera API or files. */
class SyntheticCameraGateway : CameraGateway {
    override fun capability() = "MOCK · 전방/후방/스마트폰 합성 도형 · 실제 카메라 미연결"
    override fun sharePreview() = "본인·환자 동의를 가정한 합성 예시만 이용합니다. 실제 영상 전송은 없습니다."
}
