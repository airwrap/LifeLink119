# Pleos SDK 연동 가이드 · v0.17

v0.17 최종 UI APK에서도 Console 입력과 도어·SoC SDK 값 변화, 원복 및 에어백 미지원 응답을 재확인했습니다. [동일 APK 검증 요약](manual/v17/sdk-verification.json). 기본 자동 검사 출력은 `verification/pleos-sdk-current/results.json`이며, v0.16.1 아래 기록은 당시 증거로 보존합니다.

## 유지한 환경

공식 Maven 저장소 `https://nexus-playground.pleos.ai/repository/maven-releases/`와 의존성 `ai.pleos.playground:Vehicle:2.3.2`를 유지합니다. minSdk 28, compileSdk/targetSdk 34, JDK 17, Gradle 8.10.2입니다.

## 구현된 읽기 API

| 조회 | 확인 후 사용하는 API | 권한 | Pleos AVD 결과 |
|---|---|---|---|
| 운전석 도어 | `checkDoorPositionCapabilities` → `getCurrentDoorPosition(DoorArea(ROW_1_LEFT))` | CAR_DOORS | v0.16.1 공식 Console 변경과 SDK READ 0→1→0 일치, 범위 0~1 |
| 배터리 충전 상태 | `checkEvBatteryStateOfChargeCapabilities` → `getEvBatteryStateOfCharge` | CAR_ENERGY | v0.16.1 공식 Console 변경과 SDK READ 100→80→15→100% 일치 |
| 운전석 에어백 신호 | `checkAirbagDeployedCapabilities` → `hasSeatAirbagBeenDeployed(SeatArea(ROW_1_LEFT))` | CAR_SAFETY | 현재 Pleos 이미지가 ERROR_NOT_SUPPORTED_PROPERTY 반환. 값 변화 검증 불가, 미지원 표시 |

메서드는 공식 API Reference와 실제 배포 AAR 2.3.2의 public 시그니처/RequiresPermission을 대조했습니다. 공식 Vehicle-2.3.2-sources.jar의 EvBattery KDoc에서 SoC가 정수 백분율임을 추가 확인했습니다. 0~100%를 검증해 typed 값으로 사용합니다. 구형 가상 세션의 고정 72% 호환 필드는 실제 SDK 값과 별개이며 새 UI에서는 차량 상태 화면으로 연결합니다. [계약·소스 해시·분석](BRIEFING_GAP_ANALYSIS_V16.md).

차량/도어를 제어하는 set API와 CONTROL_CAR_* 권한은 사용하지 않습니다. SDK의 바인딩 권한 `ai.pleos.playground.service.vehicle.VEHICLE_BINDING`이 AAR에서 병합됩니다.

## 동작과 오류

- UI 스레드 대신 단일 작업 스레드에서 초기화/조회. Main Handler로 결과 전달.
- READ/READ_WRITE 확인. 도어는 운전석 지원 영역과 반환 범위를 확인한 뒤 조회.
- null과 정상 값 0 구분. 잘못된 범위와 범위 밖 값은 성공 처리하지 않음.
- 15초 제한, 중복 요청 방지, 오래된 세대의 콜백·실패 무시, 화면 종료 시 SDK release.
- SDK 내부의 멈춘 호출 자체를 강제 취소한다고 보장하지 않습니다. 이전 작업이 실제로 남아 있으면 BUSY로 알립니다.
- 공식 문서의 VehicleException/ErrorCode와 실제 2.3.2 공개 클래스 이름에 차이가 있습니다. 축약/비공개 클래스나 리플렉션으로 오류 코드를 추정하지 않습니다.
- 표준 SecurityException/UnsupportedOperationException 및 정확히 일치하는 공식 SDK 오류 메시지 코드를 구분합니다. 미지원·권한 거절·값 없음·서비스 연결 실패를 분리하고, 다른 예외는 단계와 원문 메시지로 표시합니다. 같은 연결에서 확인한 에어백 미지원 결과는 캐시하고 SDK 해제/재연결 시 초기화합니다. TIMEOUT/null은 미지원으로 캐시하지 않습니다.

## 권한 분리

- 공통: CAR_DOORS, CAR_ENERGY, CAR_SAFETY(문서화된 읽기만), SDK 바인딩 권한.
- debug만: INTERNET, network security config의 127.0.0.1 평문 예외. 앱의 로컬 연결 주소는 고정이고 HTTP redirect는 따르지 않습니다.
- release: INTERNET 없음, 로컬 공유 비활성화. 실제 기기 실행에서도 비활성 상태를 확인합니다.
- 전화·SMS·카메라·마이크·GPS·CAR_DRIVE·VIN·차량 CONTROL 권한을 추가하지 않았습니다.
- 새 차량 관측값은 메모리에서만 보관하고 별도 명시적 동의로 앱 내 상황실에 표시합니다. EmergencySession/saved state/PC payload에는 포함하지 않습니다. 백그라운드·세션/인물 변경·종료·철회·30초 만료 시 전달을 지웁니다.

## 구현을 보류한 항목

- 빌트인 캠: 공개 접근 계약을 확인하지 못해 UnavailableCameraGateway 유지. 합성 영상 예시는 실제 카메라 연동과 별개입니다.
- 실제 주행 상태 감지: 이번에 확인한 공식 문서와 SDK 공개 계약만으로 충분히 검증하지 못했습니다. v0.4.1의 UNKNOWN/PARKED/DRIVING은 수동 시나리오 선택이며 어떤 상태에서도 응급지원 접근을 잠그지 않습니다.
- NaviHelper: 실제 목적지 설정을 호출하지 않습니다. 앱과 웹의 경로는 DEMO_GRID 고정 그림입니다.
- Safety 에어백 읽기는 v0.16에서 추가했습니다. 사고·부상·자동 신고 판단은 하지 않습니다. VIN·경고등·탑승자 판정·차량 제어는 추가하지 않았습니다.

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

정상 Pleos Connect/API 34 AVD에서 앱 실행 → 실제 CarUX가 상세 조작을 허용하는지 확인 → 더보기 → 차량 상태 → 차량에서 새로 조회. 값의 출처·수신 시각·실패 상태를 확인합니다. Virtual Console 도어 변경 → 새로 조회/5초 확인 → 앱 반응을 기록합니다. 긴급 요청 후 동의 → 앱 내 전달, EV 계산에서 SoC 불러오기를 각각 검사합니다.

표준 Android에서는 SDK 확인 불가가 정상입니다. 예시 버튼을 직접 선택한 회귀는 `scripts/verify-vehicle-context.py --serial emulator-5580`으로 수행합니다. helper 설치와 JDK/SDK 설정은 기존 네이티브 검사 스크립트와 같습니다. 장치 이름·serial은 새 PC의 실제 구성으로 확인하세요.

v0.16.0 당시 HAL 문제로 보류했던 도어·SoC 실제 SDK 값 검사는 v0.16.1에서 완료했습니다. [환경·원본 증거·실행 절차](SDK_RUNTIME_VERIFICATION_V16.md)를 확인하세요. `Verify-Pleos-SDK.cmd`로 별도 AVD에서 공식 Console 입력과 설치 앱의 SDK 값을 자동 비교하고 원복합니다. 표준 Android 예시 검사 결과는 [당시 기록](VEHICLE_CONTEXT_VALIDATION_V16.md)이며 실제 차량·차종별 권한 거절·서비스 소멸 주입은 별도 검사입니다.
