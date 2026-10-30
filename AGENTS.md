# Codex / coding agent rules — LifeLink119

당신은 Kotlin + Android (minSdk 28, compileSdk 34) 기반 Pleos Connect 차량용 앱 개발자다.

1. 서비스 이름: LifeLink 119. 핵심 콘셉트: 가족을 위한 차량형 응급지원.
2. 현재 모든 119/112/109, 영상 전송, 구급차/경찰 연계는 **모의 시연**이다. 실제 긴급 번호로 전화·문자·HTTP 요청하는 코드는 작성하지 않는다.
3. Pleos Vehicle SDK 공식 의존성 `ai.pleos.playground:Vehicle:2.3.2`를 유지한다. 문서에서 확인된 API만 사용하고 확실하지 않은 API는 인터페이스/미구현으로 남긴다.
4. `CAR_DOORS` 이외의 권한은 기능/문서가 확인되지 않으면 추가하지 않는다. 개인정보 수집, 증상 기록, 위치 전송 기본값은 꺼 둔다.
5. 의료 진단·응급도 분류·병원 결정·교통법규 위반 권고·경찰 호송 자동승인은 만들지 않는다. 전문기관 판단의 보조 기능만 설계한다.
6. 차량 내 주행안전/주의분산 최소화를 고려한다. 수동 이동 상태는 시연 상황 선택이며 별도의 정차 확인을 긴급 연결의 전제조건으로 두지 않는다. 실제 CarUxRestrictionsManager/ActiveRestrictions는 별도로 준수한다. 상태 미확인 AAOS에서는 상세·영상·긴 목록을 보수적으로 잠그고, 시연 토글로 실제 제한을 해제하지 않는다. 임의 distractionOptimized 선언을 추가하거나 실차 안전·상용화 완료로 주장하지 않는다.
7. 각 기능 구현 뒤 `:app:assembleDebug`, `:app:testDebugUnitTest` 결과를 기록한다. 네트워크/SDK가 없어 확인할 수 없으면 실패 이유를 명시한다.
8. 아키텍처는 `core`(상황 정책), `data`(mock/adapter), `vehicle`(SDK), `MainActivity`(UI) 구분을 유지한다.
9. UI에 `시연 모드 | 실제 신고·상담 연결 아님` 안내를 항상 노출한다.
10. 가족·의료정보의 인증 전 노출을 막는다. 사용 가능한 시스템 자격 확인을 연결하고 없으면 사용자가 설정하는 앱 PIN을 요구한다. 기본 PIN·평문 PIN·평문 프로필·개인정보 saved-instance state·로그를 남기지 않는다. 의료정보 잠금과 실제 플랫폼 제한을 긴급 연결의 선행 등록 조건과 혼동하지 않는다. `docs/MEDICAL_PRIVACY.md`를 따른다.

참고: `README.md`, `docs/PRD.md`, `docs/SDK_INTEGRATION.md`, `docs/SAFETY_TESTS.md`.
