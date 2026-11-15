# 에뮬레이터·내장 브라우저 통합 검증 — 2026-11-16

개발본 **v0.18.2-browser**를 실제 Android 에뮬레이터에서 조작했다. 최종 실행 결과는 **UI 검사 19개 묶음 / 157개 확인 항목 통과**, Kotlin 단위 테스트 292개, Python 서버 98개, QA 도구 10개 통과다. Android 기기 테스트는 debug와 서명된 release에서 각각 5개씩 통과했다. 서로 다른 환경에서 반복한 항목도 포함한 실행 건수이며, 157개의 독립적인 제품 기능을 뜻하지 않는다.

[기계 판독 가능한 결과와 검사 목록](EMULATOR_TEST_RESULTS_20261116.json)에 APK 해시와 확인 항목을 보관했다. 원본 기기 로그·공개 화면 캡처는 로컬 `verification/`, 빌드 로그는 패키지의 `.tools/logs/`에 있다. 보호 화면은 캡처하지 않았고 실제 계정·환자정보·인증 문자를 사용하지 않았다.

## Playwright에 해당하는 검사 방식

- **Android 앱:** UI Automator 접근성 API와 별도 Instrumentation 도구로 실제 버튼·입력·화면 전환을 확인했다. 기존 `SnapshotClient`와 `WindowClient`를 재사용했다. APK 내부 상태를 임의로 성공 처리하는 방식이 아니다.
- **내장 WebView:** 실제 UI Automator 트리에서 공식 사이트의 로그인 버튼을 찾아 터치했다. 웹 디버깅을 켜거나 로그인 정보를 추출하지 않았다.
- **PC 모의 상황실:** Playwright Chromium으로 실제 페이지를 열어 사용자의 클릭부터 상황실 반영·철회·종료까지 검사했다.

공식 문서: [UI Automator](https://developer.android.com/training/testing/other-components/ui-automator), [Espresso-Web](https://developer.android.com/training/testing/espresso/web), [Playwright Android 실험 지원](https://playwright.dev/docs/api/class-android).

## 환경과 보존 범위

| 환경 | 검사 범위 |
|---|---|
| Pleos Connect v2.1.0 / API 34 / 2560×1440 / 160 dpi | 현재 debug 전체 기능 및 SDK 조회, 이후 서명된 release 설치·실행 |
| 표준 Android API 34 / 1280×720 / 160 dpi | SDK 미지원 처리, EV 계산·공유, 외부/오프라인 음성, 홈·연결·제한 동작 |
| 같은 표준 AVD를 1024×600으로 임시 변경 | 홈의 버튼 접근성·텍스트·터치 영역; 상세 기능 전체 재검사는 아님 |
| Windows / Python 3.12 / Chromium 1920×1080 | PC 상황실 5종 흐름과 협력 모의 절차 |

QA 전용 `emulator-5584`, `emulator-5588`을 사용했다. 사용자가 보던 `emulator-5586`의 앱·데이터와 기존 `127.0.0.1:8781` 서버는 보존했다. 기존 서버도 고정 질문 코드 요청에 HTTP 200 / `fallback=false`로 응답했다. QA의 임시 PIN·건강정보는 검사 후 UI 초기화로 제거했고, 네트워크·화면 크기·ADB 터널은 원래 값으로 복구했다.

검증한 앱 코드 기준은 `a4fcfba102a336c7533532e07dcfbe1535951dc7`이다. 이번 작업은 테스트 코드와 문서를 보완했으며 제품 소스는 변경하지 않았다. 제출 확정본 `apk/LifeLink119_LifeLink119_release.apk`와 심사 기준 커밋은 변경하지 않았다.

## 실제 확인 결과

| 영역 | 조작 → 결과 |
|---|---|
| 홈·신고·상담 | 119 긴급신고·응급상담, 109, 112 각각 한 번 선택 → 모의 응답 → 종료·공유 정리. 긴급 연결에 PIN 요구 없음 |
| 주행 제한 | 추가 제한 적용 → 상세·영상·문자 조작 제한. 긴급 연결 경로는 유지 |
| 내장 브라우저 | 안심콜 홈페이지 → 로그인 입력 화면. 닫기 → 앱 복귀 → 새 세션. 실제 네트워크 차단 → 오류 → 복구·재시도 성공. 소방청 안내·건강정보고속도로 페이지도 열림 |
| 브라우저 보호 | debug/release에서 WebView 보안 설정·별도 프로세스 검사. 실제 공식 페이지에서 `FLAG_SECURE` 유지 확인 |
| 안심콜·가족정보 | 5개 입력 구역·보호자 항목 저장, 잘못된 입력의 초안 유지, PIN 만료·재인증, 주야간 변경·재시작, 가족 추가·개별 삭제, 암호화 저장 |
| 건강정보 활용 | 저장되어 있어도 자동 첨부되지 않음. 인증 후 선택한 환자의 병력·알레르기·복용약을 응급요약에 반영. 제외·인증 만료·백그라운드 이동 시 다시 숨김 |
| 응급요약 | 에어백 전개·미전개·응급질환·고속도로·데이터 실패 시나리오. 미전개를 안전하다고 판정하지 않음. 출처·확인 불가·모의 표시 유지 |
| 신고 지연·대기 | 사용자가 확인한 연결/대기 상태 유지 → 재연결 → 한 질문씩 상태 확인 → 악화 시 질문 중단과 재신고 안내 → 인계요약 반영 |
| 기록 복구 | 선택한 대기 기록만 암호화 저장·인증 후 복구. 재시작 시 통화 상태는 확인 불가, 음성은 자동 재개하지 않음 |
| 음성·외부 AI | 한 번 저장한 동의 유지, TTS 후 음성인식 시작, 허용된 질문 선택, 무응답 시 중단, 명시적 음성 중단, 통화 확인 시 차단 |
| 자동 음성 시연 | 마이크 대신 6개 합성 답변으로 의식·호흡·증상 흐름 진행 → 모의 인계요약. 중간 중단 가능 |
| 실제 AI 연결 실패 | QA의 프록시 연결을 실제로 끊음 → 약 3.3초 내 고정 질문으로 대체 → 119 재연결 버튼 사용 가능. 기존 서버는 중지하지 않음 |
| 차량·EV | 공식 SDK 조회 완료, 미지원 SDK에서 앱 유지, 합성 SoC 계산, 동의 후 공유·철회, 오래된 값 제한·30초 만료·페이지 이탈 후 조회 정지 |
| 보조 기능 | 19개 상황별 시연 진입·종료, 6개 오프라인 매뉴얼, 큰 글씨·쉬운 안내 유지, 가상 운전자 선택·해제 |
| 문자·수어·영상 | 정형 문구·가상 답장, 수어 동의·중단, 영상 안내 단계 전환, 전방·후방·스마트폰 합성 영상 동의·시작·철회 |
| 공동대응·내비 | 가상 112 검토·거절·승인. 가상 상담원 안내 + 위치 공유 + 사용자 확인 후에만 모의 목적지 설정 |
| 주변 차량 협력 | 동의 → 초대 → 수락 → 임무 승인 → 표시 승인 → 화면 모형 → 철회 → 도착·인계 |
| PC 상황실 | 실제 Playwright로 MEDICAL/VIDEO/TRANSPORT/POLICE/WELLBEING의 생성·검토·안내·철회·종료. Android의 응급요약 게시·철회도 별도 확인 |
| 서명된 release | 비디버그·서명 검증 → 홈 → 4종 연결·종료 → 오프라인 자동 음성 시연·인계요약. 기기 테스트 5개도 통과 |

두 QA AVD의 crash 버퍼에 LifeLink 앱 강제종료 항목은 0건이었다. debug·demo 빌드, `spotlessCheck`, lint가 통과했다. lint는 오류 0개이며, Android 테스트 라이브러리의 새 버전 알림 2개가 남아 있다.

## 검증 도구에서 보완한 문제

1. 과거 버전에 고정된 검사 때문에 현재 APK를 거부했다. 소스의 현재 버전과 **설치된 APK의 정확한 SHA-256**을 함께 확인하도록 바꿨다.
2. 8781 포트를 이미 사용 중이면 검사를 진행하지 못했다. 기존 서버를 유지하고 별도 서버·제한된 QA 전용 중계와 ADB 터널을 사용한다. 제품 서버의 Host/Origin 검사는 완화하지 않았다.
3. 작은 화면에서 화면 밖의 버튼을 없는 것으로 판단하거나 PIN 보호 화면에서 인증을 생략하는 검사가 있었다. 화면 탐색과 QA 전용 임시 인증을 사용하도록 수정했다.
4. 화면 캡처 중 짧은 마이크 단계가 끝나 외부 AI 성공 표시를 놓쳤다. 음성 단계 검사는 캡처와 분리했고, 실제 프록시 응답과 허용된 질문의 표시를 대조했다. 로컬·외부 AI 결과 파일도 분리했다.

이번 재검사에서 제품 코드를 고쳐야 하는 앱 강제종료·정보 손실은 재현되지 않았다. 아래 미확인 범위까지 정상 동작을 보증하는 결과는 아니다.

## 남은 확인 범위

- **소방청 본인 인증과 실제 건강정보 자동 연계:** 로그인 화면까지 확인했다. 실계정 로그인·SMS·외부 인증 앱 전환·공식 API 자동조회는 이번에 수행하지 않았다.
- **실제 음성 품질:** 실제 Vosk 디코더에 한국어 합성 WAV를 입력하는 기기 테스트는 통과했다. 사람의 발화·자동차 소음·실물 마이크 정확도는 별도 확인이 필요하다.
- **실차·기관:** 에뮬레이터 SDK 값은 시뮬레이션으로 표시한다. 에어백 프로퍼티는 미지원이다. 실제 충돌 센서·실제 신고·접수·출동 연동을 시험한 것은 아니다. 119·112·109 실제 발신은 0회다.
- **작은 화면 UX:** 1280×720 홈은 주요 버튼이 한 화면에 보인다. 1024×600에서는 긴급신고 아래의 보조 메뉴를 보려면 스크롤이 필요하다. 해당 해상도는 홈 검사만 수행했다.
- **의료 안내:** 고정 지침·위험 시 중단·자유 생성 차단의 동작 검사이며, 의료전문가의 임상적 검토를 대신하지 않는다.

## 재실행

debug QA용이며 일상 시연 AVD에는 실행하지 않는다. 이미 release가 설치된 AVD와 debug 서명이 다르면 새 QA AVD를 사용한다. 기존 사용자 데이터 삭제로 해결하지 않는다.

```powershell
. ..\.tools\lifelink-env.ps1
& .\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug :app:assembleDemo :app:lintDemo spotlessCheck
& .\scripts\start-qa-emulator.ps1 -Port 5584
adb -s emulator-5584 install --no-streaming -r app\build\outputs\apk\debug\app-debug.apk
python scripts/prepare-native-window.py --serial emulator-5584
python scripts/prepare-ui-snapshot.py --serial emulator-5584

python scripts/verify-assistance-ui.py --serial emulator-5584 --label official
python scripts/verify-official-browser.py --serial emulator-5584
python scripts/verify-current-tools.py --serial emulator-5584
python scripts/verify-emergency-support.py --serial emulator-5584
python scripts/verify-minimal-voice.py --serial emulator-5584
python scripts/verify-voice-conversation.py --serial emulator-5584 --external
python scripts/verify-llm-offline.py --serial emulator-5584
python scripts/verify-console-playwright.py
python -m unittest discover -s scripts -p "test_*.py"
```

외부 AI 검사에는 로컬 `.env`와 해당 서버 접근이 필요하다. API 키는 앱·결과 문서·Git에 기록하지 않는다. PC 검사는 Python Playwright와 Chromium 설치가 필요하다. 각 검사 결과는 로컬 `verification/`에 생성된다. 동시에 같은 AVD에 두 UI 검사를 실행하지 않는다.

서명된 release 공개 화면 검사는 [`verify-release-smoke.py`](../scripts/verify-release-smoke.py)를 사용한다. 동일한 release 인증서로 서명한 별도 테스트 도구가 필요하며, 일반 debug 도구를 release에 억지로 연결하지 않는다.
