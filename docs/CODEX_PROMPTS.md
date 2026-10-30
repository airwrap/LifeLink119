# VS Code / Codex용 단계별 프롬프트

2026-10-28: 현재 통합 UX 작업 지침은 [LIFELINK_EXPANSION_PROMPT.md](LIFELINK_EXPANSION_PROMPT.md)다. 아래 프롬프트는 기존 5개 흐름의 이력·회귀용이다. 현재 홈은 CTA 2개이며, 실제 CarUxRestrictions는 수동 이동 시연 설정과 별도로 준수한다.

## 프롬프트 1 — 실행환경 검증

v0.6 후속 작업은 [공모전 체크리스트](CONTEST_DEMO_CHECKLIST.md), [의료정보 보호](MEDICAL_PRIVACY.md), [현재 구현·검증 보고서](IMPLEMENTATION_REPORT.md)를 함께 따른다. 의료정보 인증을 단순 확인창으로 바꾸거나, 기본 PIN을 넣거나, 실제 CarUX 제한을 시연 설정으로 해제하지 않는다.

```text
현재 리포지토리는 LifeLink119 Pleos Connect 차량용 앱입니다. AGENTS.md 및 README.md를 먼저 읽으세요.
Android Studio/Gradle 환경에서 :app:assembleDebug 와 :app:testDebugUnitTest 를 실행하여 빌드 실패를 확인하고 수정하세요.
공식 Vehicle SDK 2.3.2 및 Pleos Maven 저장소를 유지하고, 실제 119·112·109 신고/통화 기능을 구현하지 마세요.
빌드 가능 여부, 미해결 오류 및 수정을 변경 파일별로 보고해 주세요.
```

## 프롬프트 2 — 화면 고도화

```text
LifeLink119의 홈 화면과 5개 시나리오를 12~14인치 가로형 차량 디스플레이에서 보기 좋게 고도화하세요.
상단 모의 시연 고지는 항상 노출하고, 119 응급상담→정보확인→모의 상담센터 연결의 3단계 흐름을 구현하세요.
실제 전화/영상 송신을 추가하지 말고 가상의 세션ID 및 상태로 동작하게 만드세요.
이동 중에도 응급지원·매뉴얼·승인된 표시 시연을 허용하고 정차 확인을 선행 조건으로 두지 마세요. 이동 상태는 수동 시나리오이며 실제 속도 감지 미연동임을 명시하고 동승자·관제 역할의 UX 테스트 항목을 작성하세요.
```

## 프롬프트 3 — 상담센터 웹 데모 설계

```text
실제 공공 신고 시스템에 연결하지 않는, 로컬 모의 상황실 서버와 브라우저용 상황실 화면을 설계해 주세요.
Android 앱이 제공하는 가상의 차량정보/위치/영상 링크를 보여줄 수 있도록 JSON 스키마, REST API, 테스트 데이터를 설계하세요.
데이터는 완전한 가짜 데이터로만 제한하고 서버는 localhost에 기본 바인딩 하세요.
외부 119/112/109 시스템에 요청하지 마세요.
```

## 프롬프트 4 — 차량 데이터/SDK 고도화

```text
공식 Pleos Vehicle SDK 문서를 참고해서 현재 `PleosVehicleGateway`의 capability/error handling을 개선하고,
차량·배터리·안전 관련 조회 API 중 정식으로 공개된 것만 후보로 분석해 주세요.
앱이 불필요한 권한을 요청하지 않도록 설계하고, 빌트인 캠은 공개 API가 확인되기 전까지 미연동으로 유지하세요.
공식 문서 URL과 테스트 시나리오를 docs/SDK_INTEGRATION.md에 기록하세요.
```
