# 공모전 기능별 시연 경로 · v0.6

> v0.7 추가: [주행 중 긴급 호출 D01 시연](DRIVING_EMERGENCY_DEMO.md). 기존 18개 예시에 D01을 더해 총 19개이며, 아래 v0.6 기록은 당시 범위를 나타냅니다.

시작 전 `Run-Full-Demo.cmd`. 새 A~M 흐름과 협력 시연은 PC 서버 없이도 완결된다. 항상 **시연 모드 | 실제 신고·상담 연결 아님**을 설명한다. 아래는 공모전 동작 경로이며 실제 기관 연계·임상/차량 인증 완료를 뜻하지 않는다.

| 기능 | 재현 경로 | 화면·자동검사 근거 |
|---|---|---|
| 등록 없는 긴급 요청 | 홈 → 지금 긴급해요 → 119 또는 112 | contest-native.json / 기본 회귀 native-smoke.json |
| 119 상담 / 109 | 홈 → 상담받고 싶어요 → 기관 선택 | medical-consultation / 기본 회귀 109 |
| A 가족 CRUD·마스킹 | 홈 → 가족 → 추가/가상정보 열기/저장/확인·편집/삭제 | contest-native.json Family CRUD; 기본 회귀 카드 |
| 의료정보 보호 | 가족정보 접근 → 시스템 인증 또는 첫 앱 PIN 설정 → 수동 잠금/60초/앱 전환 후 재인증 | privacy.json; 보호 화면은 PNG 대신 capture.json 기록 가능 |
| B 동의·철회 | 연결 → 카드 → 항목 선택 → 모의 공유 → 상황실 → 철회 | summary-consent 및 기본 회귀 S01 |
| C·D 연결·상황실 | 연결 상태 확인 → 추가 지원 → 가상 상황실 | 모든 18개 사례 및 기본 회귀 |
| E 영상·수어 | S01 영상 요청/동의/시작/중단, S08 문자·통역 | 기본 회귀 S01/S08 |
| F 위치·차량 SDK | S14 위치/차량 공유, 더보기 EV → 실제 SDK 진단 | 기본 회귀 S14, SDK 별도 원본 응답 |
| G 목적지 | 상황실 안내 → 위치 동의 → 추가 지원 만남 장소 → 제안/확인/설정 | 기본 회귀 S14 |
| H 경찰 | S14 세 번째 기본 행동 → 요청/검토/거절 또는 가상 승인 | 기본 회귀 S14 |
| I 안심콜 | S01/E04 → 추가 지원 → 안심콜 → 동의하고 가상 조회 | safe-call-registered / contest-native.json |
| J 접근성 | S08~S12, 시연 도구 접근성에서 큰 글씨 | case-S08~12, vision-voice-mock, cognitive-easy, large-text-home |
| K 요약 | S05 요약 → 가상 수치/관찰 수정 → 검토 동의 → 상황실 | summary-consent |
| L EV/V2L | 더보기 → 차량 안전 준비 → 조건 입력/지원 해제 | 기본 회귀 EV 및 수식/경계값 단위 검사 |
| M 디지털키 모형 | 시연 도구 → 운전자 선택 → 새 연결 → 환자는 미선택 확인 | contest-native.json Digital-key |
| 주변 차량 협력 | S14 위치 동의 → 추가 지원 협력 → 동의/요청/확인/초대/수락/임무 | offline-approved-beacon / offline-handover |
| 별도 표시 승인 | 협력 임무 후 119/112 역할 요청/확인/승인·거절 → 시작/철회/인계 | offline-approved-beacon, 단위 120초/300초 만료 |
| 협력 대조 조건 | 협력 실험 재시작 → 구급대 우선/차량 없음 | offline-no-responder, 단위 대조 조건 |
| 매뉴얼 | 시연 도구 → 기본 응급 매뉴얼 | manual |
| 실패·제한·복구 | 시연 도구 연결 실패/위치 없음/추가 제한, 실제 창 비활성화 | 기본 회귀·단위 상태 전이·driving.json |
| 좁은 화면 | 시연 도구 → 520dp 미리보기 → 홈/긴급 연결 → 기본 너비 복귀 | narrow-520dp-home / session |
| 가상 주행 | 공식 Virtual Console → P/D/가상 속도 → P 복귀 | driving.json 및 driving/*-callback.txt |

화면은 `verification/v06/native/`, 기본 회귀는 `verification/v06/regression/`, 가상 주행은 `verification/v06/driving/`에 저장한다. 실제 실행 결과의 통과·부분 완료 여부는 [현재 구현 보고서](IMPLEMENTATION_REPORT.md) 및 `verification/v06/artifact-report.json`을 우선한다.

## 18개 갤러리

`더보기 → 설정·시연 도구 → 시나리오 갤러리`에서 S01~S14/E01~E04를 선택한다. 각 사례의 기관·인물·우선 작업은 [전체 기능 매트릭스](FEATURE_MATRIX.md)에 있다. `scripts/smoke-contest.py`는 18개를 각각 실행하고 3개 작업 및 종료를 확인해 `contest-native.json`과 `case-<ID>.png/xml`을 남긴다.

## 반복 검증 명령

```powershell
$env:JAVA_HOME='D:\CODEX\ORCA\.tools\java\jdk-17.0.20.1+1'
$env:GRADLE_USER_HOME='D:\CODEX\ORCA\.tools\gradle-cache'
$env:PYTHONIOENCODING='utf-8'
.\gradlew.bat :app:assembleDebug :app:assembleRelease :app:testDebugUnitTest :app:lintDebug --console=plain --no-parallel --max-workers=1 *> verification/v06/build.log
python -m unittest discover -s console -p 'test_*.py' -v *> verification/v06/server-tests.log
python scripts\prepare-ui-snapshot.py --serial emulator-5580
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" -s emulator-5580 install --no-streaming -r app/build/outputs/apk/debug/app-debug.apk
.\Stop-Local-Demo.cmd
python scripts\run-contest-verification.py
python scripts\verify-contest-artifacts.py
```

전체 검사는 가상 데이터 전용 AVD에서 실행한다. PC 데모 서버를 먼저 `Stop-Local-Demo.cmd`로 종료해 오프라인 동작을 검사한다. runner는 임시 QA PIN을 메모리로만 전달하고 마지막에 저장 가상정보와 함께 제거한다. 실제 개인 PIN을 환경변수·로그·채팅에 넣지 않는다. helper는 별도 검증용 APK이며 검사 후 `adb -s emulator-5580 uninstall com.lifelink119.verify`로 제거한다. Virtual Console은 전용 AVD만 허용하고 다른 장치/포트는 보존한다. 실제 TTS·수어·기관 응답·영상·GPS·디지털키·차량 등화는 이 데모에서 연결하지 않는다.

검사 도구 오류를 수정한 뒤 같은 APK의 통과 근거를 유지하려면 `python scripts/run-contest-verification.py --resume`을 사용한다. PASS 및 APK SHA-256이 모두 일치하는 단계만 재사용하며, 가상 프로필·임시 PIN은 새로 초기화한다. 소스를 바꿨으면 먼저 빌드·설치해야 하고 APK가 달라지면 모든 단계가 다시 실행된다.
