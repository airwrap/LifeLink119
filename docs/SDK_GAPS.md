# SDK 확인 및 미연계 범위 · 2026-10-28

기존 `ai.pleos.playground:Vehicle:2.3.2`, Kotlin 2.0.21, AGP 8.8.2, Gradle 8.10.2, JDK 17을 유지했다. minSdk 28 / compile·target 34. 공식 문서를 실제 열어 확인한 범위이며 추정 API를 호출하지 않았다.

| 항목 | 확인 근거와 현재 구현 | 남은 일 |
|---|---|---|
| Vehicle State | [공식 소개](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/intro) 및 기존 로컬 AAR. 문서 참고 버전 2.3.2. 기존 초기화/해제·도어·배터리 READ 어댑터 보존 | 차종별 capability·권한 거절·실차 회귀 |
| Car UX | [공식 제한 개발 가이드](https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/driving-restriction-development-guide). 로컬 API 34 `optional/android.car.jar`를 javap로 대조. Car.createCar 비동기 수명주기, registerListener/currentCarUxRestrictions/unregisterListener/disconnect 실제 API 사용. v0.6 검사는 공식 Virtual Console의 P→D/25km/h→P와 앱 PID의 실제 제한 콜백을 함께 기록 | 실차·차종별 시스템 차단/OEM DrivingView 테스트 |
| 가상 주행 | [공식 Virtual Console](https://document.pleos.ai/docs/pleos-only/app-test-guide/virtual-console-guide), localhost 8088 및 전용 Pleos AVD. 대상 AVD·ADB forward를 요청마다 확인하고 종료 시 속도 0/P로 복구 | 실제 자동차·외부 조작 장치 불필요. 물리 차량의 동작·도로 시험을 검증한 것은 아님 |
| 의료정보 인증 | [Android KeyguardManager](https://developer.android.com/reference/android/app/KeyguardManager) 보안 화면 잠금 확인·시스템 자격 확인 Intent 연결. 없으면 자체 앱 PIN. API 28 호환을 위해 deprecated 자격 확인 Intent를 사용 | 현재 AVD에는 시스템 잠금이 없어 앱 PIN 경로만 네이티브 검증. OEM 시스템 자격 화면은 해당 장치에서 별도 검사 |
| 의존성 | `useLibrary("android.car", false)`와 optional uses-library. 차량 기능 없는 Android에서는 로컬 MOCK 경로. 권한·distractionOptimized 메타데이터 신규 추가 없음 | OEM 심사·카테고리 승인 |
| NaviHelper | [공식 소개](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/intro), 참고 3.0.5, initialize/addListener/removeListener/release 확인. 프로젝트에는 추가하지 않음 | 가상 좌표는 실제 목적지로 보내지 않음. 실제 좌표/SDK 호환성/사용자 확인·전문가 안내 검증 후 별도 어댑터 |
| 위치·STT·TTS·Control | [프로젝트 생성 안내](https://document.pleos.ai/docs/pleos-only/create-my-project)에서 Vehicle State/NaviHelper 가용, 나머지 준비 중 표기를 확인 | 현재 GPS/마이크/차량 제어 미연결. 로컬 위치 fixture·무음 음성 모형만 사용 |
| 카메라 | 기존 CameraGateway 재사용, SyntheticCameraGateway로 명시적 MOCK. 실제 빌트인캠 API·스마트폰 영상 연결 미확인 | OEM 공개 API·사용범위·동의·승인·보안 검토 |
| 디지털 키 | 실제 운전자 식별 API 미확인 | 선택한 가상 운전자만 별도 저장, 환자는 자동 선택하지 않음 |
| 기관 | 전화 Intent·SMS·실제 119/112/109·안심콜·수어·경찰 서비스 호출 없음 | 기관 협약·인증·운영 절차·실제 동의/연락 검증 |
| AI | ConsultationSummaryGateway 인터페이스와 OfflineTemplateSummaryGateway로 결정론적 요약만 수행. 네트워크 LLM·진단·위험도 평가 없음 | 임상 검토/추후 외부 모델 어댑터는 별도 범위 |
| EV/V2L | 가정 입력 수식만 실제 계산, SDK 값 자동 적용·실제 전원 켜기 없음 | 차종·효율·출력·실제 부하/안전 검증 |

주행 UX 참고: [주의분산 가이드](https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/driver-distraction-guideline), [화면 구조](https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/app-structure-screen-model), [앱 심사](https://document.pleos.ai/docs/pleos-only/distribute/app-review-guide). 수동 이동 상태의 정차 게이트 제거는 실제 CarUX 우회 허용이 아니다. 실제 제한이 불명확한 AAOS에서는 상세 기능을 보수적으로 잠근다.

테스트에만 `org.json:json:20240303`를 추가해 JVM에서 실제 JSON parser/복원을 검사했다. [공식 release](https://github.com/stleary/JSON-java/releases/tag/20240303). 앱 런타임 의존성이나 새 UI 프레임워크는 추가하지 않았다.
