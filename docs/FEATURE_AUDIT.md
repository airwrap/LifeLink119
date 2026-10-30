# 확장 전 기능 감사 · 2026-10-28

원본 지시: [LIFELINK_EXPANSION_PROMPT.md](LIFELINK_EXPANSION_PROMPT.md). 기존 소스를 조사한 뒤 작성했으며 확장 전 소스는 `verification/backups/before-ux-expansion-*.zip`에 보존했다.

## 확인한 기술 구성

- 앱 ID/package: `com.lifelink119.app`, 단일 `MainActivity`, Kotlin + Android Views. Compose 없음.
- AGP 8.8.2 / Kotlin 2.0.21 / Gradle 8.10.2 / JDK 17.
- minSdk 28 / compileSdk·targetSdk 34 / Vehicle SDK 2.3.2 / JUnit 4.13.2.
- `core` 정책, `data` Mock·localhost adapter, `vehicle/PleosVehicleGateway` 실제 READ, Python 상황실 서버.
- debug만 localhost 인터넷 권한. 전화·문자·카메라·마이크·GPS 권한 없음.

## 기존 동작과 확장 필요

| 영역 | 조사한 기존 상태 | 확장 방향 |
|---|---|---|
| 홈·연결 | 5개 상황 + 도구가 홈에 동시 노출, 정보 확인 후 연결 | 큰 CTA 2개, 기관 선택 후 즉시 연결 시연 |
| 119·112·109 | 고정 가상 사례 10개, 앱 Mock 및 localhost 상황실 | 119 긴급/의료상담 구분, 연결 후 선택적 개인화 |
| 가족·동의 | 차량·위치·합성 영상 동의만 제공 | 가상 가족 CRUD, 마스킹, 의료 항목별 동의·철회 |
| 시나리오 | 5개 흐름, 각 고정 사례 2개 | 선언형 S01~S14 + E01~E04 갤러리 |
| 접근성 | 큰 버튼과 일부 contentDescription | 문자·정형 문구·수어 연결 모형·쉬운 안내·큰 글씨 |
| 영상·위치 | 합성 예시, 고정 격자, 실제 수집 없음 | 전·후방/스마트폰 Mock 상태 전이 및 미리보기 제한 |
| 차량 SDK | 실제 도어·배터리 원본 READ, capability/error handling | 기존 진단을 더보기에서 유지 |
| 협력 차량·표시 | 별도 localhost 역할·임무·119/112 표시 승인 | 기존 모듈과 매뉴얼을 더보기 시연 도구에서 유지 |
| 내비·112 지원 | 고정 격자 경로·검토 모형 | 사용자 확인·가상 기관 안내·승인/거절 상태 명확화 |
| 안심콜·디지털키·요약·EV | 통합된 UI 경로 없음 | 외부 연결 없는 Mock, 규칙 요약, V2L 계산 |
| 주행 UX | 수동 이동 상태, 실제 플랫폼 제한 미연동 | 수동 상태와 별도로 CarUxRestrictions 수신·보수적 fallback |

## 검증 기준선

확장 전 소스로 `assembleDebug`와 `testDebugUnitTest`를 실행해 BUILD SUCCESSFUL/exit 0을 확인했다(`verification/v05-baseline-build.log`, 기존 36개). 기존 v0.4.1 결과는 과거 기록이며 이번 버전 통과로 재사용하지 않는다. 과거 협력 차량 자동검사 및 Pleos 다중 화면 연결의 미완료 이력은 유지한다.

## 유지할 경계

모든 긴급연결·의료정보·영상·위치 공유는 명시적인 시연이다. 실제 기관 호출, 미확인 차량 제어, 임의 의료 판단은 추가하지 않는다. 홈의 긴급 경로는 프로필·동의·증상 문답의 선행 조건을 두지 않는다. 플랫폼 제한은 별도로 준수하며 DO 우회 선언을 추가하지 않는다.
