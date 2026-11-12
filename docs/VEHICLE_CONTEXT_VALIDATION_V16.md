# 차량 데이터 기능 검증 · v0.16

> 이 문서는 v0.16.0 당시 기록입니다. 이후 v0.16.1에서 도어·SoC의 공식 Pleos SDK 값 변화 검증을 완료했습니다. 최신 환경·미지원 항목·증거는 [SDK 런타임 검증](SDK_RUNTIME_VERIFICATION_V16.md)을 참고하세요.

검증일: 2026-11-07. APK `0.16.0-demo` / versionCode 17, minSdk 28 / compile·target 34. [설명회 심층 분석과 20개 개선 항목](BRIEFING_GAP_ANALYSIS_V16.md).

## 빌드와 단위 테스트

`assembleDebug`, `testDebugUnitTest`, `lintDebug` 동시 요청의 최종 결과는 BUILD SUCCESSFUL. 단위 테스트 **164개, 실패·오류 0개**, lint **오류·경고 0개**다. 신규 차량 정책 테스트 16개를 포함한다. SoC의 공식 백분율 계약을 확인하면서 기존 ‘임의 정수 유지’ 테스트도 0~100 검증으로 수정했다.

의미 있는 검사 범위는 null과 0/false 구분, 잘못된 범위·NaN, 30초 경계·미래 시각, 출처/metric 불일치, 일부 값만 유효한 경우, 동의·공유값 동결, 세션 저장 JSON에 실제 관측값 제외, 백그라운드·인물/세션 변경·종료·철회·만료, 109/플랫폼 제한, SoC 계산 반영이다.

초기 검사에서 기존 배터리 계약 테스트 불일치, 수정 전 클래스와 새 테스트의 빌드 시점 차이, lint 들여쓰기·문자열 경고가 발견되어 수정한 후 전체 필수 검사를 통과했다. lint 비활성화나 baseline으로 숨기지 않았다.

## 네이티브 화면 검증

최종 결과를 아래에 기록한다. 검증 대상은 전용 **일반 Android34 AVD**이며 실제 Pleos SDK 값이나 실차 안전성을 검증하는 것이 아니다. 실제 설치된 Activity의 View·입력을 검사하고 공용 화면을 캡처한다. HTML로 앱 화면을 재구성하지 않는다. 의료 PIN 입력·정보 초기화·보호 화면 캡처는 하지 않는다.

- 신규 차량 흐름 **9개 검사 그룹 통과**, 실제 공용 화면 7개 캡처.
- 기존 UI **9개 검사 그룹 통과**, 실제 공용 화면 18개 캡처. 긴급 요청·상담·영상 동의·의료정보 잠금/인증 취소·매뉴얼·큰 글씨·좁은 화면 포함. 인증 취소는 고정 지연 대신 종료 상태를 제한 시간 내 확인하며 FLAG_SECURE를 해제하도록 우회하지 않았다.
- **1280×720 / 1024×600** 두 실제 display 크기에서 홈·연결 동작과 Android input 119 터치 통과, 원래 display 복원 확인.
- 새 화면 버튼 **49개 측정**, 최소 터치 높이 **96dp**, 최소 글자 대비 **6.369:1**, 중앙 정렬·글자 잘림 없음. 보이는 View의 metadata 측정이며 실물 터치/빛반사 평가가 아니다.
- APK 설치본·전달본 SHA-256 일치. 기존 백엔드 health 정상, 자동 PC 게시 없음. 실제 신고는 하지 않았다.
- [새 기능의 실제 앱 화면 7개](reviews/vehicle-context-v16/index.html). HTML 자료는 이미지 7개 로딩·페이지 오류 없음·가로 넘침 없음 확인. Android 앱의 실제 동작 검사와 HTML 자료 로딩 검사를 구분한다.

주요 신규 시나리오: SDK 미지원·출처 유지, 세션 없이 공유 차단, 119 즉시 요청, 동의/철회, 80%→27.20kWh, 15%→0.00kWh, null·오래된 값 차단, 실제 30초 경과 후 전달 만료/계산 보류, 수동 입력 선택, 앱 이탈 삭제·세션 유지, 화면 이탈 polling 중단, 추가 주행 제한과 119 접근.

전체 결과: `verification/vehicle-context-v16/final-results.json`. 기존 UI: `verification/unslop-v16/after/results.json`, 화면 크기: `verification/unslop-v16/car-layout/results.json`.

## 실제 Pleos 검증은 환경 차단

별도 전용 Pleos AVD에서 `sys.boot_completed`가 비어 있고 boot animation이 계속됐다. vendor `adas`/`vehicle_ext` 서비스의 SIGABRT 반복, Virtual Console `bridge_ready=false`를 확인했다. 앱 설치/차량 신호 주입 전 상태이며 원인은 확정하지 않았다. 새 코드의 SDK→앱 실데이터 경로는 **미검증**이다. 정상 환경에서 capability·도어 변경·SoC·에어백·실제 CarUX를 확인해야 한다.

이번 작업이 시작한 숨김 Pleos 프로세스와 임시 ADB forward만 종료하고 등록 AVD 파일·기존 일반 Android 에뮬레이터·백엔드·사용자 데이터를 보존했다. 실제 차량·실제 기관 연락·OEM 시스템 자격 화면·차종별 권한 거부·실물 로터리·TalkBack·실주행 시인성 검증도 수행하지 않았다.

## 전달물과 증거

- [v0.16 APK](../artifacts/LifeLink119-v0.16.0-vehicle-context.apk) · [SHA-256](../artifacts/LifeLink119-v0.16.0-vehicle-context.apk.sha256)
- APK SHA-256: `9440a78f28a06b1ec5b3b5b52bf6ee0839402739ffc13fc03c25e37571a59975`
- 로컬 빌드/소스 해시: `verification/vehicle-context-v16/build-proof.json`
- 최종 빌드: `verification/vehicle-context-v16/build-verified.log`
- 단위 결과: `app/build/test-results/testDebugUnitTest/`
- lint: `app/build/reports/lint-results-debug.txt`
- 기능 회귀: `verification/vehicle-context-v16/native/results.json`
- Pleos 차단 증거: `verification/vehicle-context-v16/pleos-validation.json`, `pleos-crash.txt`, `references/initial-console-state.json`

APK는 기존 개발용 debug 서명이다. APK·verification은 Git 제외 로컬 산출물이다. 원격 push·GitHub Release 게시·심사 계정 초대는 이번 작업에서 하지 않았다. 기존 발표자료·영상은 v16 새 기능을 반영해 재촬영한 자료가 아니다.
