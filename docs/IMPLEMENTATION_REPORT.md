# LifeLink 119 v0.6.0-demo · 공모전 시연 확장 및 의료정보 보호

작업일: 2026-10-28. 기준은 [통합 UX 지침](LIFELINK_EXPANSION_PROMPT.md), [작업계획](CONTEST_COMPLETION_PLAN.md), 후속 의료정보 보호 요청이다. 이전 구현·검증 기록은 [v0.5 보고서](IMPLEMENTATION_REPORT_V05.md)에 보존했다. 확장 전 소스는 `verification/backups/before-contest-completion-20261028.zip`에 보관했다.

## 결과

18개 시나리오와 A~M 기능의 공모전 시연 경로에 주변 차량 협력·별도 승인형 표시를 통합했다. 인터넷·PC 서버 없이 앱 내 가상 상황실에서 수행한다. 기존 매뉴얼·5개 흐름·SDK 도어/배터리 조회·선택적 localhost PC 콘솔도 유지했다. 실제 전화·기관 출동·카메라·차량 제어는 연결하지 않았다.

의료정보는 기본 잠금이다. 차량 Android에 보안 화면 잠금이 있으면 시스템 PIN·패턴·비밀번호 인증 결과를 사용하고, 없으면 사용자 설정 앱 PIN을 요구한다. 이름·가족 목록·의료 카드·요약·공유 조작을 인증 뒤로 옮겼다. 긴급 119/112 연결 시연은 인증 없이 시작한다. 저장 프로필 암호화, 60초·홈·앱 전환 잠금, 민감 화면 캡처 제한, 재시작에도 유지되는 실패 횟수 제한을 적용했다. 세부 사항은 [의료정보 보호](MEDICAL_PRIVACY.md)를 따른다.

## 구현 범위

| 구분 | 구현 내용 |
|---|---|
| 홈·기관 | 두 대표 CTA, 119 구조·구급/의료상담·112·109 목적 구분, 등록·동의 없는 긴급 진입 |
| 가족·공유 | 고정 가상 예시 CRUD·선호, 환자/운전자 독립, 항목별 동의·철회·종료 정리 |
| 접근성·미디어 | 문자·의사표현·수어 3자 모형·무음 음성 안내, 합성 영상 소스/동의/중단, 큰 글씨·쉬운 안내 |
| 전문 지원 | 검토·수정·동의하는 규칙 요약, 안심콜 등록/미등록, 경찰 검토/승인·거절, 전문가 안내와 사용자 확인을 거치는 모의 만남 장소 |
| 협력 차량 | 별도 동의·대조 조건 3개·참여 자격·초대·자발적 수락·거절·300초 임무·현장 도착·구급대 인계 |
| 승인형 표시 | 별도 119/112 역할 요청·상황 확인·승인/거절·명시적 시작, 최대120초 및 임무 잔여시간 제한. 철회·만료·인계·동의 철회·문맥 변경 시 종료. 화면 비활성/페이지 이탈 시 자동 재개 방지 |
| EV | 실제 수식과 가정 입력, 비전기차/미지원/0W/보존SOC 초과 fallback. SDK 원본 조회와 SOC 가정 입력 분리 |
| 주행·화면 | 실제 CarUX 리스너와 수동 시연 상태 분리, 공식 Virtual Console 주행 값 전환, 내부520dp 네이티브 레이아웃 미리보기 |
| 보호 | 시스템 자격 확인/앱 PIN, Keystore AES-GCM 저장, salt+PBKDF2+Keystore HMAC 검증값, 캡처 제한·접근 유효시간·다회 실패 지연·개인 saved-state 제거 |
| PC | 별도 동의한 가상 상태·항목 이름만 localhost에 수동 게시/철회. 의료값·PIN·영상은 미전송 |

기존 Kotlin Views/단일 MainActivity 구조, Vehicle SDK 2.3.2, minSdk28/compile·target34, Kotlin2.0.21/AGP8.8.2/Gradle8.10.2/JDK17을 유지했다. 임의 distractionOptimized 선언이나 차량 제어 권한은 추가하지 않았다.

## 주요 변경 파일

| 경로 | 역할 |
|---|---|
| `core/OfflineCooperation.kt`, `OfflineCooperationTest.kt` | 오프라인 협력·임무·승인·시간/문맥 정책과 경계 검증 |
| `core/MedicalPrivacy.kt`, `MedicalPrivacyTest.kt` | 인증 경로·접근 유효시간·PIN 형식/검증·실패 제한·안전한 표시 |
| `data/MedicalPrivacyManager.kt`, `MedicalCrypto.kt` | 실제 시스템 자격 확인, 비동기 앱 PIN, Keystore·암호화 |
| `core/Experience.kt`, `data/ExperienceData.kt` | 협력 통합·잠금·암호화 프로필·민감 saved-state 제거 |
| `ui/ExpansionUi.kt`, `ui/ExperienceViewIds.kt`, `res/values/*ids.xml` | 의료정보 인증 게이트·공개 긴급 경로·협력 UI·좁은 화면·정적 ID |
| `MainActivity.kt`, `AndroidManifest.xml` | 인증 결과·주기 처리, singleTask로 중복 세션 Activity 방지 |
| `vehicle/DrivingRestrictionsGateway.kt` | 실제 제한 변경의 debug 진단 로그, 의료정보 미포함 |
| `scripts/virtual_console.py`, `Start-Virtual-Console.cmd` | 전용 AVD·포트 검증, 공식 콘솔·heartbeat·P/0 복귀 |
| `scripts/smoke-medical-privacy.py`, `smoke-contest*.py`, `smoke-driving.py` | 실제 네이티브 개인정보/18개 사례/복구/PC·SDK/주행 검사 |
| `scripts/capture_emulator.py`, `ui_snapshot_client.py`, `android-ui-snapshot/` | 보호 캡처 거부 기록·암호 입력 마스킹·별도 테스트 helper |
| `scripts/run-contest-verification.py`, `verify-contest-artifacts.py` | 임시 QA PIN 수명·전체 검사·동일 APK 해시·서명·권한·패키징 |
| `console/server.py`, `scripts/local-demo.ps1`, `run-full-demo.ps1` | 현재 버전 실행·복구, 기존 서버 보존 |
| `README.md`, `AGENTS.md`, 기능 매트릭스·SDK 경계·시연 대본 | 기능·보호·제약·재현 경로 갱신 |

Kotlin 경로는 `app/src/main/java/com/lifelink119/app/`, 테스트는 `app/src/test/java/com/lifelink119/app/` 기준이다. 소스별 변경 목록은 `verification/v06/source-changes.json`을 참고한다.

## 검증

최종 상태는 **VERIFIED_WITH_LIMITATIONS**다. 아래 공모전 시연 검사는 통과했으며, 실제 기관·차량·OEM 인증 범위와 구분한다. `verification/v06/gate.json` 및 `artifact-report.json`에 기계 판독 결과를 보관했다. 재개 실행에서 재사용한 단계도 PASS와 동일 APK SHA-256을 모두 확인했다.

| 검사 | 결과 | 근거 |
|---|---|---|
| debug/release 컴파일·단위·lint | BUILD SUCCESSFUL, exit0 | `verification/v06/build.log` |
| Android 단위 | **129개**, 실패/오류/skip0 | `app/build/test-results/testDebugUnitTest/TEST-*.xml` |
| Python 서버·콘솔 경계 | **68개 통과** | `verification/v06/server-tests.log` |
| lint | **오류0·경고0** | `app/build/reports/lint-results-debug.xml` |
| 개인정보 네이티브 | **11항목 통과**: PIN 설정/실패 제한/재시작/60초/앱 전환/캡처 보호/암호화/확인 중 취소/잠금 중 긴급 진입 | `verification/v06/privacy.json` |
| 공모전 네이티브 | **16항목 + 18개 사례 통과**. 18개 각각 실행·기본 행동3개·종료 확인, 대표 모듈의 상세 상호작용 검사 | `verification/v06/contest-native.json`, `native/*.xml/png` |
| 대표 흐름 회귀 | **13항목 통과**: S01 영상·동의, S08 문자·수어, S14 경찰·목적지, EV·제한·기존 도구 | `verification/v06/regression/native-smoke.json` |
| 오프라인 복구 | **5항목 통과**, PC 서버가 없는 상태에서 연결·위치·대조 조건·잘못된 수치·V2L 실제 슬라이더 검사 | `verification/v06/recovery.json` |
| 실제 CarUX 전환 | 공식 콘솔 P/0 → D/가상25km/h → P/0, 앱 PID의 실제 콜백 **0→80→0** 및 복귀 확인 | `verification/v06/driving.json`, `driving/*-callback.txt` |
| 선택적 PC 콘솔 | LOCATION 항목 이름과 상태만 게시·조회·철회, 의료값 미전송 | `verification/v06/pc-sdk.json` |
| Vehicle SDK 원본 | 도어 **READ0**, 배터리 **READ100**. 배터리 원본을 SOC%로 변환하지 않음 | `verification/v06/pc-sdk.json` |
| APK | 빌드·네이티브 검사·설치본·배포본 SHA-256 일치, debug v2 서명 검증 | `verification/v06/artifact-report.json`, `signing.txt` |
| 권한·정리 | 통화/SMS/카메라/마이크/GPS 권한 없음, release INTERNET 없음. 임시 PIN·저장 가상 프로필 삭제 확인 | `verification/v06/permissions.json`, `gate.json`의 `qaPinReset=true` |

배포본은 `artifacts/LifeLink119-v0.6.0-demo-debug.apk`, **10,146,252 bytes**다.

```text
SHA-256 e5089d83c55d6863ee3efbb3229c2d69bfbe87d95231c176e79d4df9f302f4a2
```

캡처 제한 화면은 보호를 우회하지 않고 `.capture.json`에 캡처 거부·보안 창 상태를 기록했다. 검사 도구는 AAOS 창을 활성화한 뒤 조작하고, 필요하면 검증 서비스만 재연결한다. 이 재연결은 앱 세션 초기화·인증 우회 없이 수행하며 이동 후 실제 화면 상태를 확인한다. localhost 요청은 PC 프록시를 거치지 않도록 고정 loopback 전송을 사용한다.

현재 소프트웨어 Pleos AVD에서 PIN 설정은26.41초, 앱 재시작 후 올바른 PIN 확인은27.02초였다. UI는 비동기로 동작하며 확인 중 취소·긴급 연결 복귀를 네이티브로 검증했다. 실기기 속도로 일반화하지 않는다. 기본 시스템 인증이 있는 기기는 그 인증을 우선 사용하며 해당 OEM 인증 화면은 별도 검증 대상이다.

## 범위와 남은 일

- 이 결과는 공모전 **시연용 구현**이다. 실제 119/112/109·안심콜·경찰·수어·카메라·GPS·내비·디지털키·차량 등화·V2L 제어는 MOCK/미연계다. 협력 역할 승인은 실제 기관 인증이 아니다.
- 실제 제한 전환은 Pleos 에뮬레이터와 공식 Virtual Console 범위다. 실차 주행·차종별 제한·OEM 앱 심사·DrivingView 인증은 하지 않았다.
- 현재 AVD에는 시스템 화면 잠금이 등록돼 있지 않아 앱 PIN 경로만 네이티브 검증한다. 시스템 인증 경로는 실제 API·capability 검사·성공 결과 게이트와 단위 검증을 구현했으며 해당 OEM 장치에서 추가 확인이 필요하다.
- 내부520dp 네이티브 레이아웃과 실제 물리 세로 화면·OEM 다중 창 인증을 구분한다. 전체 TalkBack 감사도 남아 있다.
- 기존 localhost 협력 기능은 보존하고 서버 회귀를 수행한다. 새 오프라인 협력 흐름을 이번 네이티브 공모전 검증 기준으로 한다.
- release는 unsigned 컴파일 결과이며 배포용 서명본이 아니다. 제공하는 APK는 설치 가능한 debug 시연본이다. 과거 HTML/PPTX·전송 ZIP은 당시 버전 자료로 보존했다.

[실행 안내](../README.md) · [클릭 대본](DEMO_SCRIPT.md) · [기능별 경로](CONTEST_DEMO_CHECKLIST.md) · [SDK 확인](SDK_GAPS.md)
