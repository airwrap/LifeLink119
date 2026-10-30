# LifeLink119 v0.2 구현·자가 점검 보고서

## 구현 결과

사용자가 요청한 2시간 한도 안에서, 기존 5개 화면 중심의 시연을 **Android 앱 ↔ localhost 서버 ↔ 상담원/탑승자 브라우저**가 연결되는 데모로 확장했습니다. 원래 목적은 가족을 위한 차량형 응급지원이며, 실제 기관이나 의료 판단을 대신하지 않습니다.

| 원래 목적 | 구현한 동작 | 남은 외부 범위 |
|---|---|---|
| 응급의료상담 | 고정 가상 상황 선택, 동의, 명시적 로컬 공유, 상담원 접수·검토·안내·종료 | 실제 119 통화/상담/신고 |
| 현장 영상 | 동의 후 합성 도형 애니메이션 예시, 철회 시 제거 | 실제 카메라·스마트폰·빌트인 캠 송신 |
| 이송 협력 | 검토·가상 만남 지점·고정 격자 경로, 앱과 웹 상태 반영 | 기관 판단, 실제 이송·배차·내비게이션 |
| 경찰 협조 | 모의 공동대응 검토 절차 | 실제 112 공동대응·출동·호송 승인 |
| 심리위기 상담 | 개인정보 입력 없는 가상 요청과 상담원 진행 상태 | 실제 109 연결, 전문상담 |
| 차량 특화 | 실제 Pleos SDK 도어·배터리 읽기, 정차/주행 제한 UX 시연 | 실차 상태 감지·안전 인증 |
| 원격 개발 | Python과 브라우저만으로 양쪽 역할을 체험 | Android APK 실행에는 별도 SDK/AVD 필요 |

실제 전화·SMS·카메라·마이크·GPS 권한을 추가하지 않았습니다. 실제 차량 진단 값은 상담 payload에 넣지 않습니다. 가상 차량·위치·영상 동의는 모두 기본 OFF이며, 전송도 별도 버튼을 눌러야 합니다.

## 주요 변경 파일

| 파일/영역 | 변경 |
|---|---|
| `console/server.py` | loopback 서버, 스키마 검증, 세션/단계/동의 철회/만료/한도/중복 요청 처리, 실행별 ID 영역 |
| `console/static/index.html`, `app.js` | 상담원 목록·필터·상세·모의 접수/검토/안내/종료·철회·타임라인 |
| `console/static/passenger.html`, `passenger.js` | Android 없이 5개 흐름, 정차 확인, 가상 상황/동의, 연결·종료·철회 |
| `console/static/style.css` | 반응형 화면, 큰 조작 영역, 초점, 합성 도형, 가짜 지도, 움직임 줄이기 대응 |
| `console/test_server.py`, `requirements.txt` | HTTP·계약·병행 재시도·상태/동의/한도/재시작 테스트 및 의존성 |
| `core/DemoCase.kt`, `DemoShareSelection.kt` | 가상 상황 10종, 영상 선택, 시연 조작 제한 정책 |
| `data/MockDispatchGateway.kt` | 가상 상황과 합성 URI, 선택별 고정 가짜 데이터, 복원·종료 |
| `data/LocalConsoleGateway.kt` | 고정 loopback만 접근, debug 제한, timeout·redirect 차단·응답 제한·SDK 데이터 분리 |
| `MainActivity.kt` | 로컬 공유·상태 읽기·철회·종료·가상 경로·제한 시연, 상태 저장, 스크롤/초점 유지, 갱신 중 수동 동작 대기 |
| `vehicle/PleosVehicleGateway.kt`, `core/VehicleDiagnostic.kt` | 배터리 읽기, capability 확인, 오래된 실패/timeout 무시, 단계별 오류 |
| `app/src/debug/AndroidManifest.xml`, `res/xml/local_demo_network.xml` | debug에만 INTERNET 및 127.0.0.1 평문 예외 |
| `app/src/main/AndroidManifest.xml`, 백업 XML 2종 | 문서로 확인한 CAR_ENERGY, 백업/기기 이전 제외, 불필요한 방향 속성 제거 |
| `app/build.gradle.kts`, `ids.xml`, `DemoWorkflowTest.kt` | v0.2 버전, BuildConfig, View ID와 정책/가상 데이터 테스트 |
| `Start-Local-Demo.cmd`, `Stop-Local-Demo.cmd`, `scripts/local-demo.ps1` | 서버 시작/상태 확인/소유한 프로세스만 종료 |
| `Connect-Local-Console.cmd` | ADB reverse로 localhost 연결 |
| `Run-Full-Demo.cmd`, `scripts/run-full-demo.ps1`, `wait-emulator.py` | 웹·AVD·터널·APK 설치·앱 실행. 새 PC APK artifact 경로도 지원 |
| `scripts/smoke-v02.py` | 실제 AVD 5개 흐름·명시 공유·철회·경로·재시도·SDK·release·느린 폴링 중 수동 종료 검증 |
| `docs/contracts/*`, `docs/fixtures/*` | caseCode, 동의, 상태/타임라인/경로 규격과 고정 가상 예시 |
| README, PRD 안내, ROADMAP, SDK_INTEGRATION, SAFETY_TESTS, AGENTS | 현재 구현/제약 및 실행·검증 방법 갱신 |

`core/data/vehicle` 경로는 `app/src/main/java/com/lifelink119/app/` 아래입니다. 공식 SDK 2.3.2와 Maven 주소, minSdk 28, compileSdk/targetSdk 34를 유지했습니다.

## 테스트와 실행 근거

- Android 필수 `assembleDebug`, `testDebugUnitTest` 및 `assembleRelease`, `lintDebug` 성공. 단위 테스트 **25개**. 최종 lint **오류 0 / 경고 0**.
- 서버 HTTP/저장소 테스트 **19개**. 외부 Origin/Host/API cross-site, JSON 크기·중복 키·금지 필드, 5개 라우팅, 동시 재시도, 상태 전이, 철회, 만료, 용량, 초기화·재시작 ID 검증.
- 계약 검증: 요청/응답 스키마 2개, 응답 예시 3개·대응 요청 3개 통과, 금지 입력 11종 거부.
- Pleos AVD: 5개 시나리오의 선택 기본 OFF, 명시적 공유 이전 서버 기록 미생성, 선택별 가짜 정보 전달, 종료 확인. 영상 시나리오에서 상담원 안내→앱 반영→가상 경로→철회 시 경로 숨김 확인.
- 실제 연결 실패를 ADB 터널 제거로 만들고, 복구 후 같은 요청으로 재시도해 중복 생성이 없음을 확인.
- 실제 SDK: 도어 READ 0, 지원 범위 0~1; 배터리 READ, 원본 값 100.
- release APK는 시험용 debug 키로 별도 서명해 기기에 설치했으며, 공유 버튼 비활성·서버 기록 미생성을 확인했습니다. 이 시험 APK/키는 일반 인계 ZIP에 배포하지 않습니다.
- 브라우저: 두 역할의 5개 시나리오·종료, 상담원 단계 반영·철회·제한 시연. 390px/데스크톱의 가로 넘침 없음, 버튼 약 49px, 다음 단계 키보드 초점 유지 확인.

주요 파일: `verification/v02-final-build.log`, `v02-server-tests.log`, `v02-android-e2e.json`, `v02-release-runtime.json`, `v02-web-e2e.json`, `contract-validation.json`, `v02-*-*.png/jpg/xml`, `app/build/reports/lint-results-debug.xml`.

## 발견하고 수정한 미비점

1. 최초 브라우저 이동이 cross-site로 분류돼 UI까지 거부됨 → UI 첫 GET은 허용하고 API/상태 변경의 경계는 유지. 회귀 테스트 추가.
2. 자동 읽기마다 전체 DOM을 다시 만들어 키보드 초점이 사라짐 → 변경된 데이터만 다시 그리고, 단계 전환 시 다음 조작에 초점 유지.
3. 주기적 연결 시각/같은 오류가 반복 알림을 유발 → 상태가 달라질 때만 갱신하고 진행 상태에 별도 live 영역 적용.
4. 가상 상황 이름과 실제 선택 가능 항목이 모순 → 영상 예시 B를 정보 선택 연습으로 정리.
5. 공유 처리 중 양식 변경 가능 → 진행 중 조작 잠금과 처리 중 상태 표시.
6. 서버 재시작 후 짧은 번호를 재사용해 이전 앱이 새 기록을 오인할 수 있음 → 실행별 숫자 영역, 초기화 시 증가 번호 유지, 이전 ID 404 회귀 테스트.
7. 앱 상태 갱신 시 스크롤이 위로 이동 → 같은 페이지의 위치와 키보드 초점을 레이아웃 이후 복원.
8. 상태 읽기 중 수동 종료/철회를 누르면 요청이 누락될 수 있음 → 수동 동작 1개를 대기시켜 현재 읽기 뒤 처리. 느린 로컬 연결에서 별도 회귀 검증.
9. 이전 요청이 남은 상태에서 새 세션을 만들 때 busy 표시가 남을 수 있음 → 새 세션의 요청·대기 상태 초기화, 오래된 요청 결과는 식별자로 무시.
10. 일부만 노출된 버튼을 자동 테스트가 누름 → 테스트도 충분한 버튼 높이가 보이도록 스크롤한 뒤 입력.
11. 가상 지도 텍스트/표식이 화면 비율에 따라 늘어짐 → 좌표만 변환하고 글자·원·선 굵기는 별도 크기 유지.
12. 정적 검사 경고 2개 → 방향 속성 제거, 구형 백업과 Android 12 이상 데이터 추출 제외 규칙 추가. 최종 경고 0.
13. release에서 비활성 버튼의 이유가 불명확 → 앱 내부 시연 범위 안내 및 실제 release 실행 확인.

14. Windows에서 거부한 POST의 본문을 남긴 채 연결을 닫으면 응답 전에 연결이 초기화될 수 있었음 → 작은 고정 길이 본문만 짧은 제한 시간 안에 비우고 오류 응답. 서버 19개 테스트를 5회 연속 통과.
15. SVG의 `hidden` 속성이 HTML 요소처럼 해제되지 않아 웹 경로가 보이지 않았음 → 속성을 직접 토글하고 실제 Chrome에서 455×220 경로와 철회 시 숨김을 확인.
16. 웹의 이전 자동 읽기 응답이 이후 철회·새 세션의 상태를 덮어쓸 수 있었음 → 탑승자 세션 revision과 상담원 읽기 generation을 비교해 오래된 응답을 무시.
17. 도어 조회 중 배터리 버튼이 이전 상태로 남을 수 있었음 → 조회 시작 시 전체 진단 버튼의 진행 상태를 함께 반영.

## 최종 산출물과 원본 보존

- 현재 APK: `0.2.0-demo` / versionCode 2 / minSdk 28 / targetSdk 34.
- debug APK SHA-256: `8e56d0401170a23da5c9f1f81a68eea9e2e66c403a79b70fafddebcbdfb71509`.
- `verification/v02-artifact-report.json`에 최신 APK·권한·25개 단위 테스트·19개 서버 테스트·실제 화면 검증을 모았습니다. 기존 `artifact-report.json`은 v0.1 과거 기록입니다.
- 느린 상태 읽기 중 종료 검증은 `v02-android-slow-poll-e2e.json`, 최신 APK 전체 흐름은 `v02-android-e2e.json`에 각각 기록했습니다. `history-intermediate-*`는 수정 이전 실패 근거를 보존한 것입니다.
- 원본 `LifeLink119_Starter.zip`의 SHA-256과 최초 추출 파일 26개의 모든 바이트를 재확인했습니다. 새 인계 파일명을 사용해 이전 결과를 덮어쓰지 않습니다.
- 새 PC용 산출물은 `LifeLink119_v02_Transfer_*.zip`입니다. 소스·문서·웹·스크립트·테스트·검증 화면·debug APK·원본 ZIP을 포함합니다. 캐시·빌드 중간 파일·컴퓨터별 SDK 경로·서버 PID·비밀키는 제외합니다.
- 압축별 `.sha256`, `.verification.json`에서 CRC·복원 해시 및 복원 프로젝트 빌드 결과를 확인합니다. 실행 안내는 `OTHER_PC_GUIDE.md`와 압축본 최상위 `00_README_OTHER_PC_KO.md`입니다.

## 남은 한계

- 실제 신고·상담·기관 공동대응, 실제 영상/GPS, 차량 상태 기반 자동 제한, NaviHelper·실차·안전 인증은 완료로 주장하지 않습니다.
- 서버는 가짜 데이터용 localhost 개발 서버입니다. 운영 상담센터·보안 인증·감사/개인정보 시스템이 아닙니다.
- 배터리 원본 정수의 단위를 임의 변환하지 않았습니다. SDK 문서/배포 예외 타입 차이 때문에 미확인 클래스·오류 코드를 추정하지 않습니다.
- TalkBack 전체, 모든 D-pad, 다양한 OEM, 실제 권한 거부/SDK 서비스 소멸·모든 timeout 주입, 전역 글자 크기 변경 시 Pleos DrivingView 포커스 이동은 별도 검증 항목입니다.
- debug 인터넷 권한은 OS 수준에서 localhost만 허용하는 방화벽을 뜻하지 않습니다. 앱 코드의 고정 목적지와 redirect 차단, 서버의 loopback 바인딩으로 범위를 제한합니다.
- 초기 Gradle 실행에는 SDK XML 버전 안내가 출력됐지만 빌드/테스트/lint는 성공했습니다. 이를 컴파일 오류로 처리하지 않았습니다.
