# v0.6 소스 검토: 협업용 개선 목록

2026-10-30의 20개 검토 항목입니다. **높음 5개·보통 15개이며 모든 항목은 미해결 상태**입니다. 저장소 공유 준비는 문제들의 수정 완료를 뜻하지 않습니다. 가상 데이터 PoC의 코드·격리 검사 결과이며 실제 개인정보 유출을 주장하지 않습니다.

상세 근거는 [findings.json](findings.json)입니다. 원본 스크립트·로그·화면은 로컬 `verification/review-20261030/`에 있고 Git에는 포함하지 않습니다. 정적 분석·JVM/Python/최소 DOM 재현과 실제 기기 검증을 구분합니다.

인증·잠금·철회 관련 1~5번부터 처리합니다. 새 PC 가이드는 20번의 기존 복사 설치 대신 Git clone을 사용합니다.

## 1. 시스템 인증에서 신규 앱 PIN으로의 전환에 기존 데이터 보호 방식 확인이 없음

- 우선순위: 높음 / 상태: 미해결
- 영향: 앱 PIN이 없는 설치에서 시스템 인증을 사용할 수 없으면 신규 PIN 설정으로 기존 암호화 프로필을 열 수 있는 경로가 있다.
- 근거와 한계: Static conditional path plus pure-policy JVM observation; native credential removal/failure was not exercised on a device.
- 개선 방향: 기존 인증 방식과 데이터 소유 상태를 저장하고, 인증 방식 변경은 기존 인증 성공 또는 저장정보 삭제 후에만 허용한다. 시스템 인증 모드는 인증에 연결된 키 정책도 검토한다.
- 소스 위치(검토 당시): [app/src/main/java/com/lifelink119/app/data/MedicalPrivacyManager.kt:64](../../../app/src/main/java/com/lifelink119/app/data/MedicalPrivacyManager.kt#L64), [app/src/main/java/com/lifelink119/app/data/MedicalCrypto.kt:21](../../../app/src/main/java/com/lifelink119/app/data/MedicalCrypto.kt#L21)

## 2. 잠금 대상에서 문자 대화와 세션의 의료 문맥이 빠짐

- 우선순위: 높음 / 상태: 미해결
- 영향: 이름은 가려져도 의료 시나리오 제목과 기존 의사표현 대화가 인증 없이 표시되는 경로가 남는다.
- 근거와 한계: Static UI paths; JVM probe confirms messages survive locking/revocation-related state transitions. No new native UI test was run.
- 개선 방향: 인증 없이 쓰는 새 긴급 의사표현과 기존 대화 열람을 분리하고, 잠금 시 과거 대화 및 개인 의료 문맥을 가린다.
- 소스 위치(검토 당시): [app/src/main/java/com/lifelink119/app/ui/ExpansionUi.kt:33](../../../app/src/main/java/com/lifelink119/app/ui/ExpansionUi.kt#L33), [app/src/main/java/com/lifelink119/app/ui/ExpansionUi.kt:240](../../../app/src/main/java/com/lifelink119/app/ui/ExpansionUi.kt#L240), [app/src/main/java/com/lifelink119/app/ui/ExpansionUi.kt:417](../../../app/src/main/java/com/lifelink119/app/ui/ExpansionUi.kt#L417)

## 3. 전체 공유 철회가 수어 연결 및 관련 대화·조회 상태를 정리하지 않음

- 우선순위: 높음 / 상태: 미해결
- 영향: 화면은 전체 철회를 알리지만 관련 연결·정보 상태가 계속 남아 사용자 동의 의도와 달라질 수 있다.
- 근거와 한계: JVM reproduced: sharedFields empty, messages=1, sign=CONNECTED, safeCall=MOCK_REGISTERED.
- 개선 방향: 공유 범위별 철회 정책을 정의하고 전체 철회에서는 관련 연결 중단, 대화 보존 여부, 조회 결과 삭제를 일관되게 처리한다.
- 소스 위치(검토 당시): [app/src/main/java/com/lifelink119/app/core/Experience.kt:177](../../../app/src/main/java/com/lifelink119/app/core/Experience.kt#L177)

## 4. 오래된 웹 조회 응답이 철회된 스냅샷을 화면에 다시 표시함

- 우선순위: 높음 / 상태: 미해결
- 영향: 서버에서 삭제가 완료돼도 응답 역전으로 이전 게시 내용이 다시 화면에 나타난다.
- 근거와 한계: Node VM minimal-DOM reproduced: newer empty response renders zero cards, older response then restores one card.
- 개선 방향: 조회 세대 번호, 이전 요청 취소, 단일 요청 실행 및 응답 revision 검사를 적용하고 연결 실패 시 오래된 표시임을 명확히 한다.
- 소스 위치(검토 당시): [console/static/experience.js:4](../../../console/static/experience.js#L4)

## 5. 철회 기록 한도 초과에서 부분 삭제 후 재게시가 허용됨

- 우선순위: 높음 / 상태: 미해결
- 영향: 철회 오류가 반환되기 전에 자료가 삭제되지만 재게시 차단 기록은 남지 않아 작업의 원자성이 깨진다.
- 근거와 한계: Python reproduced at 200 tombstones: REVOCATION_CAPACITY, snapshot deleted, same payload accepted again.
- 개선 방향: 용량 검사를 변경 전에 수행하거나 삭제와 철회 표식을 한 트랜잭션으로 처리한다.
- 소스 위치(검토 당시): [console/experience.py:83](../../../console/experience.py#L83)

## 6. 권한 검사가 UI에 집중돼 도메인 호출과 지연된 확인창 콜백을 보호하지 못함

- 우선순위: 보통 / 상태: 미해결
- 영향: 도메인 계층의 직접 호출은 인증을 확인하지 않는다. 기본 예시 복원 확인창의 긍정 콜백도 잠금 만료 후 재인증 없이 실행된다.
- 근거와 한계: JVM accepts medical sharing with privateUnlocked=false and complexBlocked=true; restore-dialog expiry path is static-only.
- 개선 방향: 인증 수명·작업 범위를 포함한 권한 검사를 작업 실행 경계로 옮기고 확인창의 실행 시점에도 다시 확인한다. 긴급 진입은 예외로 유지한다.
- 소스 위치(검토 당시): [app/src/main/java/com/lifelink119/app/core/Experience.kt:172](../../../app/src/main/java/com/lifelink119/app/core/Experience.kt#L172), [app/src/main/java/com/lifelink119/app/ui/ExpansionUi.kt:350](../../../app/src/main/java/com/lifelink119/app/ui/ExpansionUi.kt#L350)

## 7. 저장 실패 시 메모리 프로필과 영구 저장값이 달라짐

- 우선순위: 보통 / 상태: 미해결
- 영향: 추가·수정·삭제가 메모리에서 먼저 반영되고 암호화 또는 commit이 실패하면 재시작 전후의 내용이 달라진다.
- 근거와 한계: Static mutation ordering; no real-device storage failure was induced.
- 개선 방향: 후보 상태를 검증·저장한 뒤 공개하거나 실패 시 이전 상태로 되돌린다. 저장 장애 주입 테스트를 추가한다.
- 소스 위치(검토 당시): [app/src/main/java/com/lifelink119/app/ui/ExpansionUi.kt:384](../../../app/src/main/java/com/lifelink119/app/ui/ExpansionUi.kt#L384), [app/src/main/java/com/lifelink119/app/data/ExperienceData.kt:57](../../../app/src/main/java/com/lifelink119/app/data/ExperienceData.kt#L57)

## 8. 초기화와 저장 경로에서 키스토어·암복호화·디스크 작업을 메인 스레드에서 수행함

- 우선순위: 보통 / 상태: 미해결
- 영향: 저장장치 또는 키스토어 응답이 느리면 의료정보 화면뿐 아니라 초기 긴급 홈 표시도 지연될 수 있다.
- 근거와 한계: Static call chain; ANR was not induced in this review.
- 개선 방향: 인증 후 비동기 프로필 로드와 저장을 적용하고 공개 긴급 홈은 민감정보 저장소의 준비와 독립적으로 표시한다.
- 소스 위치(검토 당시): [app/src/main/java/com/lifelink119/app/ui/ExpansionUi.kt:31](../../../app/src/main/java/com/lifelink119/app/ui/ExpansionUi.kt#L31), [app/src/main/java/com/lifelink119/app/data/ExperienceData.kt:49](../../../app/src/main/java/com/lifelink119/app/data/ExperienceData.kt#L49)

## 9. 취소된 PIN 작업이 계산을 계속해 새 요청을 지연시킬 수 있음

- 우선순위: 보통 / 상태: 미해결
- 영향: 저장 직전의 epoch 검사는 오래된 결과를 막지만, 취소된 요청도 PBKDF2 계산부터 실행한다. 단일 executor에 새 요청이 쌓일 수 있다.
- 근거와 한계: Static executor/epoch placement. Historical 2026-10-28 AVD JSON records enrollment=26.41s and unlock=27.02s; not newly benchmarked.
- 개선 방향: 계산 전에도 epoch를 확인하고 최신 요청 하나만 유지한다. 취소·큐 길이·장치별 시간을 측정하되 암호 강도를 근거 없이 낮추지 않는다.
- 소스 위치(검토 당시): [app/src/main/java/com/lifelink119/app/data/MedicalPrivacyManager.kt:114](../../../app/src/main/java/com/lifelink119/app/data/MedicalPrivacyManager.kt#L114)

## 10. PIN 입력 제한이 변경 가능한 시스템 시각에 의존함

- 우선순위: 보통 / 상태: 미해결
- 영향: 시각을 앞으로 바꾸면 제한이 조기 해제되고, 뒤로 바꾸면 표시상 최대5분과 달리 훨씬 오래 잠길 수 있다.
- 근거와 한계: JVM wall-clock model: 30 seconds remaining becomes zero after a forward time jump.
- 개선 방향: 동일 부팅에서는 단조 시계를 사용하고 재부팅·시각 변경을 구분하는 영속 제한 정책을 둔다.
- 소스 위치(검토 당시): [app/src/main/java/com/lifelink119/app/data/MedicalPrivacyManager.kt:107](../../../app/src/main/java/com/lifelink119/app/data/MedicalPrivacyManager.kt#L107), [app/src/main/java/com/lifelink119/app/core/MedicalPrivacy.kt:36](../../../app/src/main/java/com/lifelink119/app/core/MedicalPrivacy.kt#L36)

## 11. 전체 화면 재생성 시 편집 중인 초안과 포커스 보존이 부족함

- 우선순위: 보통 / 상태: 미해결
- 영향: 개인정보 편집 상태가 렌더 함수의 지역 변수에 있어 잠금·재렌더링 후 미저장 선택이 사라질 수 있다. 새 UI는 스크롤만 복원한다.
- 근거와 한계: Static View reconstruction; native TalkBack/rotation/form-expiry behavior was not exercised in this review.
- 개선 방향: 민감 초안을 메모리의 별도 편집 상태로 관리하고 재인증 후 복원한다. 터치·키보드·접근성 포커스도 일관되게 보존한다.
- 소스 위치(검토 당시): [app/src/main/java/com/lifelink119/app/ui/ExpansionUi.kt:159](../../../app/src/main/java/com/lifelink119/app/ui/ExpansionUi.kt#L159), [app/src/main/java/com/lifelink119/app/ui/ExpansionUi.kt:362](../../../app/src/main/java/com/lifelink119/app/ui/ExpansionUi.kt#L362)

## 12. 기존 세션의 종료 정책이 새 흐름의 공유정보 정리 정책과 다름

- 우선순위: 보통 / 상태: 미해결
- 영향: 종료가 삭제를 의미하는지 단순 상태 종료인지 경로에 따라 다르며 기존 웹 값은 TTL까지 남는다.
- 근거와 한계: Python reproduced: DEMO_CLOSED retains vehicle/location/video/route and true consent values. Existing legacy tests deliberately retain consent choices.
- 개선 방향: 종료와 보존 정책을 명시적으로 분리하고 공통 계약으로 통일한다. 종료 후 필요한 기록은 공유 원본과 분리한다.
- 소스 위치(검토 당시): [console/server.py:154](../../../console/server.py#L154), [app/src/main/java/com/lifelink119/app/data/MockDispatchGateway.kt:49](../../../app/src/main/java/com/lifelink119/app/data/MockDispatchGateway.kt#L49)

## 13. 웹 협력 요청 재시도에서 멱등 키를 새로 생성함

- 우선순위: 보통 / 상태: 미해결
- 영향: 서버가 요청을 만들고 응답만 유실된 경우 재시도 시 같은 실험 대신 중복 실험이 생성될 수 있다.
- 근거와 한계: Node VM simulated response loss after commit: two retry attempts used two different keys.
- 개선 방향: 같은 입력의 재시도에는 요청 키를 유지하고 사용자에게 새 실험을 시작할 때만 키를 갱신한다.
- 소스 위치(검토 당시): [console/static/cooperation.js:108](../../../console/static/cooperation.js#L108)

## 14. Android·서버 상태 계약과 종료 처리의 불일치

- 우선순위: 보통 / 상태: 미해결
- 영향: 동일한 실험의 종료 시간·재전송 처리·종료 후 하위 상태가 화면마다 다를 수 있다.
- 근거와 한계: Python terminal elapsed grows 10→70 seconds; JVM rejects repeated handover while server is idempotent; Python accepts COMPLETED with APPROVED police and CONNECTED sign.
- 개선 방향: 상태 전이·불변조건·시간 정지 정책을 공통 명세로 만들고 동일 이벤트 열을 Android와 Python에 적용하는 계약 테스트를 추가한다.
- 소스 위치(검토 당시): [console/cooperation.py:110](../../../console/cooperation.py#L110), [app/src/main/java/com/lifelink119/app/core/OfflineCooperation.kt:61](../../../app/src/main/java/com/lifelink119/app/core/OfflineCooperation.kt#L61), [console/experience.py:57](../../../console/experience.py#L57)

## 15. PC 브리지 응답 크기 제한을 전체 읽기 후에 확인함

- 우선순위: 보통 / 상태: 미해결
- 영향: 16KB 제한이 메모리 할당을 막지 못한다. 다른 로컬 프로세스가 포트를 차지하거나 서버가 잘못 응답하면 큰 응답을 먼저 메모리에 읽는다.
- 근거와 한계: Static readBytes-before-limit ordering.
- 개선 방향: 상한+1바이트까지만 읽는 스트림 처리와 전체 응답 기한을 적용한다.
- 소스 위치(검토 당시): [app/src/main/java/com/lifelink119/app/data/ExperienceConsoleBridge.kt:38](../../../app/src/main/java/com/lifelink119/app/data/ExperienceConsoleBridge.kt#L38)

## 16. SDK 초기화 자체가 멈춘 경우 시간 초과 후 재시도가 영구 BUSY가 될 수 있음

- 우선순위: 보통 / 상태: 미해결
- 영향: 15초 UI 타임아웃 후에도 초기화 worker가 반환하지 않으면 workerBusy가 풀리지 않고 release 작업도 같은 executor 뒤에서 대기한다.
- 근거와 한계: Static synchronous initialize plus workerBusy lifetime; no SDK hang was injected into a car/emulator.
- 개선 방향: SDK의 취소·비동기 연결 수단을 확인하고 초기화 복구와 사용자 요청 수명을 분리한다. 무기한 정지 fake SDK 테스트를 추가한다.
- 소스 위치(검토 당시): [app/src/main/java/com/lifelink119/app/vehicle/PleosVehicleGateway.kt:67](../../../app/src/main/java/com/lifelink119/app/vehicle/PleosVehicleGateway.kt#L67), [app/src/main/java/com/lifelink119/app/vehicle/PleosVehicleGateway.kt:85](../../../app/src/main/java/com/lifelink119/app/vehicle/PleosVehicleGateway.kt#L85)

## 17. 세션 건수 외의 서버 자원 상한이 부족함

- 우선순위: 보통 / 상태: 미해결
- 영향: 타임라인은 요청에 따라 계속 커지고, 불완전한 헤더 연결은 dispatch 이전에 스레드를 점유할 수 있다. localhost 서비스의 안정성 문제다.
- 근거와 한계: Python: one session reaches 243 events; an isolated HTTP handler has no socket timeout before header parsing/dispatch.
- 개선 방향: 이벤트 수·동시 연결 수를 제한하고 setup 단계부터 연결 타임아웃을 적용한다.
- 소스 위치(검토 당시): [console/server.py:83](../../../console/server.py#L83), [console/server.py:308](../../../console/server.py#L308)

## 18. 검증 재개 캐시가 APK 이외 소스 변경을 추적하지 않음

- 우선순위: 보통 / 상태: 미해결
- 영향: Python 서버·웹 JS·검사 스크립트를 바꿔도 APK가 같으면 과거 검사 결과가 재사용될 수 있다.
- 근거와 한계: Extracted execute function reuses PASS using APK hash alone; no destructive runner import or execution.
- 개선 방향: 단계별 소스·fixture·서버·검사기·도구 버전 지문을 저장하고 바뀐 단계는 무효화한다. 프로필 수정 값 변화, 시스템 인증 전환, 저장 실패도 통합 검증에 포함한다.
- 소스 위치(검토 당시): [scripts/run-contest-verification.py:47](../../../scripts/run-contest-verification.py#L47)

## 19. 검사 대상 보호와 배포 판정의 필수 조건이 assert에 의존함

- 우선순위: 보통 / 상태: 미해결
- 영향: python -O 또는 PYTHONOPTIMIZE 환경에서는 대상 AVD 확인과 PASS 판정용 assert가 제거된다.
- 근거와 한계: Compile-only probe: 3 QA runner assertion instructions become 0 under optimize=1; destructive code was not executed.
- 개선 방향: 삭제 대상·버전·해시·검증 결과 같은 필수 보호는 명시적 if/raise로 구현한다.
- 소스 위치(검토 당시): [scripts/run-contest-verification.py:22](../../../scripts/run-contest-verification.py#L22), [scripts/verify-contest-artifacts.py:26](../../../scripts/verify-contest-artifacts.py#L26)

## 20. 스타터 설치 스크립트의 복사 범위와 경로 겹침 검사가 부족함

- 우선순위: 보통 / 상태: 미해결
- 영향: APK·검증 자료·local.properties가 복사 대상에 포함되고 향후 비밀 파일도 자동 제외되지 않는다. 소스 내부 대상 경로도 차단하지 않는다.
- 근거와 한계: Static file enumeration: only .git/.gradle/build are excluded; no installation/copy was performed.
- 개선 방향: 설치 대상 파일을 허용 목록으로 정의하고 환경 파일·산출물을 제외한다. 소스와 대상의 상하위 경로 겹침도 거부한다.
- 소스 위치(검토 당시): [scripts/install-starter.ps1:16](../../../scripts/install-starter.ps1#L16)
