# LifeLink 119 v0.4 · 응급협력 차량 PoC 구현 기록

2026-10-25

## 결과와 목적

주변 차량이 자발적으로 참여하고 기관 역할의 조율 아래 임시 응급협력 임무를 맡는 흐름을 Android와 웹에 구현했다. 비상표시는 임무와 별도의 한시 승인을 받으며, 현장 지원에서 구급대 인계까지를 하나의 실험으로 기록한다.

구급대 도착 전 첫 도움을 연결할 수 있는지 검증하는 PoC다. 차량이 일찍 도착하는 가정뿐 아니라 구급대가 더 빠른 조건과 참여 가능한 차량이 없는 조건을 함께 제공한다. 실제 조작 경과와 고정 도착 가정을 구분한다. 실내 모형·마네킹·폐쇄 시험장으로 이어지는 계획은 [PoC 실험 계획](POC_RESPONSE_NETWORK.md)에 있다.

## 주요 변경

| 경로 | 변경과 이유 |
|---|---|
| `app/src/main/assets/cooperation-catalog.json` | 시나리오 3개·가상 차량 4개를 하나의 원본으로 고정. 실제 위치·교육·장비 정보 입력 없이 비교 |
| `core/CooperationPolicy.kt` | 요청·차량·임무·승인 회차 결합, 최신 상태·정차 시연·활성 화면·협력 차량 역할 조건에서만 표시 |
| `data/CooperationGateway.kt` | debug 전용 loopback API, 중복 생성 방지 키, 응답 크기·시간 제한과 오류 복구 |
| `MainActivity.kt` | 홈의 응급협력 PoC 진입, 동의·요청, 요청자/차량 역할, 수락·거절·철회·현장 지원·표시 제어 |
| `console/cooperation.py` | 기관/요청자/차량 역할별 상태 전이, 적합성·시간 제한·인계·실험 기록. 기존 상담 저장소와 분리 |
| `console/server.py` | `/api/demo/cooperation/` API와 `/cooperation` 웹 경로, 기존 loopback·Host·Origin·입력 제한 유지 |
| `console/static/cooperation.*` | 전체 역할 웹 시연, Android 실험 ID 선택, 별도 표시 모형 보기, 가정 비교와 JSON 다운로드 |
| 실행·검증 스크립트 | v0.4 버전 확인, 재현 가능한 AVD 검사, 실제 설치본·서명·오프라인 자산·권한 검증 |

기존 공식 Pleos Vehicle SDK `ai.pleos.playground:Vehicle:2.3.2`와 minSdk 28 / target·compileSdk 34를 유지한다. 등화·사이렌 제어 API나 권한은 추가하지 않았다. release는 기존과 같이 인터넷 권한 없이 오프라인 설명을 제공한다.

## 검증 중 개선한 점

- **동의 후 요청 버튼 비활성**: 체크박스 내부의 `findViewById`가 형제 버튼을 찾지 못했다. Activity에서 버튼을 찾도록 수정하고 실제 APK에서 동의 OFF → ON → 요청 등록을 다시 검증했다.
- **웹 자동 갱신과 조작 충돌**: 주기 갱신이 모든 버튼을 다시 만들던 부분을 상태 변경과 카운터 갱신으로 나눴다. 의미 있는 상태가 같으면 잔여 시간만 갱신한다.
- **거절 후 시간 가정**: 이미 거절한 차량을 비교 기준에서 제외하고 남은 적합 차량으로 다시 계산한다. 비교 기준 차량 ID도 응답·웹에 명시한다.
- **색상 가독성**: 기존 밝은 웹 테마에 맞춰 시간 카드·진행 상태·인계 설명의 글자와 배경 대비를 수정했다.
- **에뮬레이터 그래픽 오류**: Intel GPU 환경에서 Vulkan의 YCbCr 변환 오류가 반복됐다. 같은 Pleos API 34 이미지에서 Vulkan을 끄고 검증했으며 실행 스크립트에도 적용했다.
- **검사 도구 지연·전환 순간의 불완전한 화면 구조**: 검증 보조 APK를 매번 시작하는 대신, 30분 한도의 ADB 전용 abstract Unix socket으로 같은 UiAutomation을 재사용했다. 실제 화면 전환 중 일시적으로 없는 루트·레이아웃은 다시 읽는다. 대상 앱에 검증 코드·권한을 넣거나 애니메이션을 끄지 않는다.

초기 실패·진단 로그는 삭제하지 않았다. 최종 완료 여부는 아래의 최종 요약과 PASS 기록으로 판단한다. 기존 기능은 동일 APK에서 통과한 기본 검사 13개와 표시·안전 검사 6개의 기록을 보존하고, 검사 도구의 대기·스크롤·화면 구조 읽기를 보완한 뒤 남은 실제 만료·SDK 조회를 실행한다. 최종 결과는 같은 APK의 완료 기록을 합친 것이며, 재사용 근거는 회귀 JSON의 `baselineEvidence`에 연결한다.

## 검증 근거

- [빌드·단위 검사·lint 로그](../verification/v04-build-unit-lint.log)
- [서버 테스트 로그](../verification/v04-server-tests.log)
- [새 협력 흐름 AVD 검사](../verification/v04-cooperation-e2e.json): 실제 120초 만료, 새 승인 회차, 통신 중단·복구, 다른 앱 전환, 거절·철회·인계 포함
- [기존 기능 회귀 검사](../verification/v04-regression-android-e2e.json): 오프라인 매뉴얼 6개, 상담 5개, 자동 갱신 중 복귀, 표시·안전 조건, SDK 조회
- [웹 UI 검사](../verification/v04-web-checks.json) · [브라우저에서 내려받은 실험 JSON](../verification/v04-web-experiment-export.json)
- [최종 APK·서명·권한·설치본·테스트 수 요약](../verification/v04-artifact-report.json)
- [APK](../artifacts/LifeLink119-v0.4.0-demo-debug.apk) · [SHA-256](../artifacts/LifeLink119-v0.4.0-demo-debug.apk.sha256)

최종 요약은 검사 결과·전달 APK·기기 설치본의 SHA-256이 일치해야 생성된다. 보조 APK 제거, 기본 서버 응답 지연 0 복원, 현재 앱 PID의 AndroidRuntime 오류 여부도 확인한다.

## 해석 범위

도착 가정 차이 285초는 실측 단축 효과가 아니다. 현장 도착·지원은 역할 버튼으로 시연하고, 실제 환자·도로·기관·차량 등화를 이용하지 않는다. 기관 역할은 인증 없는 localhost 연습이며 실제 기관 승인이 아니다. 실제 주행 상태 감지와 정식 기관·장치 연동, 의료진 검토와 임상 효과 평가는 후속 과제다.

브라우저와 Pleos AVD에서 실제 흐름을 확인했지만 전체 접근성 감사, 모바일 브라우저 전용 viewport, 실차 시험을 완료한 것은 아니다. HTML·PPTX 프로젝트 소개는 기존 v0.2 자료를 보존하고 최신 PoC 내용은 README와 실험 계획으로 안내한다.
