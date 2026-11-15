# 가족 응급지원 화면 개편 — 2026-11-14

“나와 내 가족을 위한 구급차”를 홈의 중심에 두었다. 일상 기록·상담일지는 이번 범위에 포함하지 않는다. 차량에서 긴급 연결을 시작할 때 메뉴 탐색과 사전 입력을 줄이는 데 초점을 맞췄다.

## 구현

| 화면 | 변경한 동작과 표현 |
|---|---|
| 홈 | 생성한 가족·차량 이미지, 산호색 119 긴급신고, 민트색 119 구급상담, 라벤더색 109 마음상담. 기관 번호·목적·아이콘을 함께 표시한다. |
| 상담 진입 | 기존 `홈 → 의료·심리 상담 → 기관 선택`을 `홈 → 해당 상담`으로 단축했다. 기존 상담 선택 화면은 더보기에서 접근한다. |
| 보조 연결 | 112 경찰 신고와 가족 안심콜을 홈 하단의 큰 버튼으로 제공한다. 112도 한 번 눌러 모의 연결한다. |
| 연결 중 | 큰 기관 아이콘·상태 패널과 정보 전달/문자 설명·종료를 먼저 표시한다. 영상·공유 설정·추가 지원은 보조 위치에 둔다. 종료 버튼의 위치를 화면 간에 통일했다. |
| 109 | 문자가 허용된 상태에서는 문자 설명을 먼저 제공한다. 주행 제한으로 문자가 금지된 경우 기존 정책의 허용된 동작을 선택한다. |
| 작은 화면 | 낮은 가로 화면에서 연결 화면의 네 버튼을 한 행에 배치한다. 1280×720 기본 글씨에서는 홈 및 연결의 주요 버튼에 스크롤이 필요 없다. |
| 접근성 | 주요 버튼 96dp 이상, 아이콘·글자·번호를 병용한다. 장식 이미지는 접근성 탐색에서 제외하고 카드 전체에 버튼 역할·설명을 부여했다. 큰 글씨는 세로로 재배치되며 일부 스크롤이 필요하다. |

홈과 연결 화면의 표현은 `app/src/main/java/com/lifelink119/app/ui/AssistanceUi.kt`로 분리했다. `ExpansionUi.kt`가 기존 core·privacy·차량 제한 정책에 연결하며, 공용 색상은 `LifeLinkUiStyle.kt`에서 관리한다.

이미지는 `app/src/main/res/drawable-nodpi/family_car_hero.png`에 포함했다. [내장 image_gen 생성 방식과 최종 프롬프트](FAMILY_ASSISTANCE_IMAGE_PROMPT.md)를 함께 보존한다. 아이콘 8개는 Android VectorDrawable로 작성했다. 이미지 안에 글자나 조작 요소를 굽지 않았다.

## 유지한 동작과 한계

- 연결 시작에 의료정보 등록, PIN, 위치 공유 또는 정차 확인을 요구하지 않는다.
- 가족·의료정보에는 기존 인증, 암호화, FLAG_SECURE를 적용한다. 기본 비공유 상태이며 새 홈 이미지에 개인정보를 사용하지 않는다.
- 실제 플랫폼 제한과 시연 추가 제한이 영상·상세 입력·문자에 적용된다.
- 현재 119·112·109 연결은 모의 시연이다. 실제 전화·문자·기관 전송과 마이크/음성인식은 추가하지 않았다. 센서 충격만으로 신고기관을 결정하거나 자동 신고하는 기능도 추가하지 않았다.
- 공식 안심콜 로그인/DB 자동 가져오기는 기존 [연계 검토](SAFE_CALL_WEB_INTEGRATION_REVIEW.md)의 상태를 유지한다.
- `-no-audio` 실행 정책을 유지했다. 실제 음성 연결을 검증한 결과가 아니다.

## 검증 결과

| 검사 | 결과 |
|---|---|
| Spotless/ktfmt | 통과 |
| debug 빌드·Kotlin 단위 검사 | 통과, 226개 / 실패 0 |
| debug·demo lint | 각각 0건 |
| release·demo 빌드 | 통과 |
| 공식 Pleos 2560×1440 | UI 검사 7개 통과. 실제 앱 패널 1682×1210. |
| 표준 Android 34, 1280×720 | UI 검사 7개 통과. 주요 버튼 크기·화면 내 위치·글자 잘림 확인. |
| 표준 화면 글씨 1.3배 | 재배치 및 5개 홈 경로 접근 확인. 세로 스크롤 허용. |
| 기존 안심콜 회귀 UI | 17개 통과. 레거시 도구 복귀, 주야간 전환, 60초 인증 만료 후 초안 복원, 암호화·개별 삭제 포함. 테스트 자료/PIN 정리 완료. |
| Python QA 가드 검사·콘솔 계약 | 가드 6개 통과, 계약 PASS |

모든 UI 검사는 별도 QA AVD에서 수행했다. 공개 화면만 `WindowInstrumentation`의 실제 Android View 렌더링으로 저장했으며 보호된 의료 화면은 캡처하지 않았다. 네 연결 경로의 실행과 종료, 기본 비공유, 인증 없는 긴급 진입, 주행 제한 적용을 확인했다. 실제 기관 발신 건수는 0이다.

공식 Pleos의 Android 현재 사용자가 0번이 아닌 환경에서 APK 검증이 실패하는 문제도 발견해 `scripts/ui_snapshot_client.py`가 현재 사용자에 설치된 패키지를 검사하도록 수정했다. APK/버전 불일치는 최적화 실행에서도 예외로 거부한다.

검증 재현:

```powershell
.\Build-And-Test.cmd
python scripts/prepare-native-window.py --serial emulator-5584
python scripts/verify-assistance-ui.py --serial emulator-5584 --label official
python scripts/prepare-ui-snapshot.py --serial emulator-5584
python scripts/verify-safe-call-v18.py --serial emulator-5584
```

전용 QA AVD에 새 debug APK를 먼저 설치해야 한다. 기존 PIN·의료정보가 있는 AVD에서는 검사를 거부한다. `verify-assistance-ui.py` 자체는 개인 자료를 만들거나 지우지 않는다.

## 결과물

- [실제 앱 화면별 미리보기](../../deliverables/LifeLink119_FamilyAssistance_20261114/preview.html): 홈·119 긴급신고·109 마음상담. 외부 통신 없는 단일 HTML, 425 KB. 데스크톱 및 390px 브라우저에서 화면 전환·이미지 로딩·가로 넘침을 확인했다.
- debug APK: `app/build/outputs/apk/debug/app-debug.apk`
- 비디버그 demo APK: `app/build/outputs/apk/demo/app-demo.apk`
- debug SHA-256: `0de53843ce4f537f34d4b5e68c5d3c212257e7372d832c54d565ac16c680e3ed`
- demo SHA-256: `4d1d48c58f8a12c7c320e55fd5f868f078fbe12b2a236ea7f1c6621b32277adf`

버전 문자열은 기존 0.18.0-demo를 유지하므로 구분 시 APK 해시와 이 개편일을 함께 사용한다. 11월 13일 작성한 38장 사용안내의 홈·상담 화면은 이번 개편 전 모습이다.
