# Pleos SDK 연동 가이드 · v0.2

## 유지한 환경

공식 Maven 저장소 `https://nexus-playground.pleos.ai/repository/maven-releases/`와 의존성 `ai.pleos.playground:Vehicle:2.3.2`를 유지합니다. minSdk 28, compileSdk/targetSdk 34, JDK 17, Gradle 8.10.2입니다.

## 구현된 읽기 API

| 조회 | 확인 후 사용하는 API | 권한 | Pleos AVD 결과 |
|---|---|---|---|
| 운전석 도어 | `checkDoorPositionCapabilities` → `getCurrentDoorPosition(DoorArea(ROW_1_LEFT))` | CAR_DOORS | READ, 값 0, 범위 0~1 |
| 배터리 충전 상태 | `checkEvBatteryStateOfChargeCapabilities` → `getEvBatteryStateOfCharge` | CAR_ENERGY | READ, SDK 원본 값 100 |

메서드는 공식 API Reference와 실제 배포 AAR 2.3.2의 public 시그니처/RequiresPermission을 대조했습니다. 배터리는 단위 변환 없이 SDK 원본 정수를 표시하며, 문서에서 충분히 확인하지 않은 단위를 임의로 추정하지 않습니다. 모의 상담의 가짜 72%와 별개입니다.

차량/도어를 제어하는 set API와 CONTROL_CAR_* 권한은 사용하지 않습니다. SDK의 바인딩 권한 `ai.pleos.playground.service.vehicle.VEHICLE_BINDING`이 AAR에서 병합됩니다.

## 동작과 오류

- UI 스레드 대신 단일 작업 스레드에서 초기화/조회. Main Handler로 결과 전달.
- READ/READ_WRITE 확인. 도어는 운전석 지원 영역과 반환 범위를 확인한 뒤 조회.
- null과 정상 값 0 구분. 잘못된 범위와 범위 밖 값은 성공 처리하지 않음.
- 15초 제한, 중복 요청 방지, 오래된 세대의 콜백·실패 무시, 화면 종료 시 SDK release.
- SDK 내부의 멈춘 호출 자체를 강제 취소한다고 보장하지 않습니다. 이전 작업이 실제로 남아 있으면 BUSY로 알립니다.
- 공식 문서의 VehicleException/ErrorCode와 실제 2.3.2 공개 클래스 이름에 차이가 있습니다. 축약/비공개 클래스나 리플렉션으로 오류 코드를 추정하지 않습니다.
- 표준 SecurityException/UnsupportedOperationException은 구분하고, 그 밖의 SDK 예외는 단계와 원문 메시지로 표시합니다.

## 권한 분리

- 공통: CAR_DOORS, CAR_ENERGY, SDK 바인딩 권한.
- debug만: INTERNET, network security config의 127.0.0.1 평문 예외. 앱의 로컬 연결 주소는 고정이고 HTTP redirect는 따르지 않습니다.
- release: INTERNET 없음, 로컬 공유 비활성화. 실제 기기 실행에서도 비활성 상태를 확인합니다.
- 전화·SMS·카메라·마이크·GPS·CAR_DRIVE·CAR_SAFETY·VIN 권한을 추가하지 않았습니다.
- 진단 결과는 MockDispatchGateway 및 로컬 서버로 보내는 payload에 포함하지 않습니다.

## 구현을 보류한 항목

- 빌트인 캠: 공개 접근 계약을 확인하지 못해 UnavailableCameraGateway 유지. 합성 영상 예시는 실제 카메라 연동과 별개입니다.
- 실제 주행 상태 감지: 이번에 확인한 공식 문서와 SDK 공개 계약만으로 충분히 검증하지 못했습니다. v0.4.1의 UNKNOWN/PARKED/DRIVING은 수동 시나리오 선택이며 어떤 상태에서도 응급지원 접근을 잠그지 않습니다.
- NaviHelper: 실제 목적지 설정을 호출하지 않습니다. 앱과 웹의 경로는 DEMO_GRID 고정 그림입니다.
- VIN, Safety 에어백/경고등은 후보 분석만 유지합니다. 의료 판단·자동 신고·추가 권한을 넣지 않았습니다.

## 공식 근거

- 시작: https://document.pleos.ai/docs/pleos-only/getting-started
- SDK 설정: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/project-setup
- 권한: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle-permission
- Capability: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/check-capability
- 오류: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle-error-code
- 배터리 capability: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/ev-battery/check-ev-battery-state-of-charge-capabilities
- 배터리 get: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/ev-battery/get-ev-battery-state-of-charge
- Android ADB reverse: https://developer.android.com/develop/ui/views/layout/webapps/access-local-server
- Android network security config: https://developer.android.com/privacy-and-security/security-config
- Android 백업 제외 규칙: https://developer.android.com/identity/data/autobackup

## 재현

Pleos Connect 2.1.0/API 34 AVD에서 앱 실행 → 정차 시연 확인 → Pleos SDK 진단 → 도어/배터리 버튼. `verification/v02-android-e2e.json`과 대응 PNG/XML을 확인하세요. 실제 차량 및 미지원·권한 거부·SDK 서비스 소멸을 주입한 실차 시험은 별도입니다.
