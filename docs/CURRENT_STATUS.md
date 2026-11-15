# 현재 개발 상태

기준일: 2026-11-15. 앱 버전: **0.18.1**. 과거 버전별 기록은 [개발 이력](../HISTORY.md)에 보존한다.

[공모전 제출 release](SUBMISSION_RELEASE_20261115.md)는 전용 인증서로 서명하고 디버깅을 비활성화했다. 한국어 오프라인 모델·고정 음성을 포함하며 모의 기능은 `-PlifelinkSubmission=true`로 명시적으로 활성화한다. 제출 안내에 맞춰 **[`apk/LifeLink119_LifeLink119_release.apk`](../apk/LifeLink119_LifeLink119_release.apk)**에 Git LFS로 배치한다. 원본 Release와 동일한 APK다.

[가족·반려견 홈 이미지](FAMILY_DOG_VISUAL_20261115.md)를 원본 앱에 적용했다. 현대 SUV 옆을 네 가족과 골든 리트리버가 함께 걷는 장면이며, 홈 문구는 ‘나와 내 가족을 위한 안전 동행’이다. 기존 기능은 유지하고 빌드·단위 테스트 288개 및 네이티브 화면 진입 검증을 통과했다.

최종 설명자료는 [24장 PPTX/PDF](presentation/scenario-safety-20261115/README.md)다. 앱 v0.18.1의 제출 release 화면과 최신 현대 SUV·가족·반려견 이미지를 반영했다. 가족 나들이 중 응급질환, 119 연결 지연과 음성 동행, 상태 변화·인계 요약, 안심콜 정보 활용을 설명하고 [폭염·가스·잔류 탑승자 센서 확장안](CABIN_SAFETY_EXTENSION_20261115.md)을 구분한다. 본인 정보 자동 연계는 공식 인증·권한 검증과 기관 협력이 필요한 설계다. 이 자료 개편으로 앱이나 제출 APK를 변경하지는 않았다.

이전 [현대차 가족 이미지 적용 16장 PPTX/PDF](presentation/voice-family-20261115/README.md)는 이력으로 보존한다. [음성 중심 UX 개선](UX_REFINEMENT_20261115.md)과 [당시 이미지 적용 기록](FAMILY_HYUNDAI_VISUAL_20261115.md)도 참고할 수 있다. 증상·연결 상태 목록을 한 질문 중심으로 줄였고, 인증 만료 시 가족정보는 숨기되 현재 전경 응급 대화는 이어진다. 안심콜 정보는 환자를 확인한 후 로컬 요약에 활용하며 외부 LLM에는 보내지 않는다.

최신 검사: Kotlin 288개, Python 콘솔 98개, 네이티브 Android 3개 및 화면·통합 34개 통과. [기능 검사 당시 APK 기록](UX_REFINEMENT_VERIFICATION.json)과 [그 이후 가족 이미지 변경 APK 기록](FAMILY_HYUNDAI_VERIFICATION.json)을 구분한다. 일반 무음 런처 대신 프로젝트 루트 `Start-Voice-Demo.cmd`를 사용한다. 마이크가 없는 PC에서도 더보기의 합성 응답 자동 시연은 실행할 수 있다. [음성 자산 준비와 재현 절차](presentation/voice-family-20261115/README.md).

차량·위치 기반 응급 요약, 사용자 확인 방식의 연결/콜백/출동 대기 상태, 규칙 기반 **AI 응급 대기 동행**을 기존 119 연결 화면에 추가했다. [고도화 구현·지원 범위·검증·6개 시연 순서](EMERGENCY_SUPPORT_IMPLEMENTATION.md), [외부 LLM 프록시 설정](LLM_PROXY_SETUP.md)을 최신 기준으로 사용한다. 실제 기관 접수·출동 연동과 실차 검증은 미완료다.

[기존 사용법·시연 안내 슬라이드](../../deliverables/LifeLink119_DemoGuide_20261114/output/LifeLink119_사용법과_시연안내_20slides.pdf)는 이번 응급 대기 기능 추가 전 화면과 두 가지 시연 순서를 20장으로 설명한다. 새 화면은 위 구현 문서의 시연 순서를 참고한다.

2026-11-15: 최초 1회 외부 AI 선택 저장, 119 화면에서 한 번 시작, 예시 음성으로 진행되는 자동 시연과 인계 요약을 추가했다. `.env` 기반 실제 LLM 연결과 **자동 질문(TTS) → 기기 내 음성 답변 → 다음 질문** 흐름을 추가했다. [음성 동행 사용법·마이크 환경·검증](VOICE_AI_IMPLEMENTATION.md).

## 지금 가능한 것

- 가족·차량 일러스트와 기관별 아이콘을 적용한 홈에서 119 긴급신고, 119 응급상담, 109 마음상담, 112 경찰 신고를 각각 한 번 눌러 모의 연결한다. 상담 선택 중간 단계를 줄이고 연결 종료 위치를 통일했다. [화면 개편·검증 기록](FAMILY_ASSISTANCE_UI_20261114.md).
- Pleos Vehicle SDK 2.3.2의 지원 기기에서 도어·배터리·에어백 상태를 읽는다. 에어백 상태는 사고/부상 진단이나 의료정보 인증의 대체 수단이 아니다.
- 직접 입력한 본인·가족 건강정보 최대 12개를 차량 기기에 암호화 보관하고 인증 후 조회·수정·삭제한다. 공식 안심콜 DB와 동기화하지 않는다.
- 공식 입력 화면 5개에 대응하는 신청자·질병·진료기관·장애/구급 요청·복수 보호자 정보를 입력할 수 있다. 저장 형식 v3는 기존 v1/v2 자료를 유지해 읽는다. [공식 양식 대조 결과](SAFE_CALL_FORM_AUDIT_20261113.md).
- 공식 안심콜 사이트를 외부 브라우저로 열어 가입·로그인·수정을 안내한다. 자동 가져오기·신고 전송의 검토 결과는 [연계 검토](SAFE_CALL_WEB_INTEGRATION_REVIEW.md)에 정리했다.
- 긴급 연결·문자·영상·기관 공동대응은 모의 시연이다. 새 응급 요약은 동의 후 FusedLocation/NaviHelper SDK를 읽되, 에뮬레이터 출처·마지막 측정 시각·미확인 정확도를 구분한다. 기존 위치 공유 시연은 합성 자료이며 PC에는 실제 위치를 보내지 않는다.
- 실제 플랫폼 주행 제한이 불명확하면 상세 기능을 잠근다. 긴급 연결 시연 자체에는 의료정보 등록이나 인증이 필요하지 않다.

## 빌드와 검증

`Build-And-Test.cmd`는 JDK 17 패치 버전을 탐색하고 패키지 옆 `.tools/android-sdk`를 사용할 수 있다. 환경변수는 현재 프로세스에만 적용한다. Spotless/ktfmt, debug 빌드·단위 검사·lint, release 빌드, demo 빌드·lint를 실행한다. CI에도 같은 검사와 Python 계약·회귀 검사를 추가했다.

| 빌드 | PC 콘솔 | Android 디버깅 | 네트워크 |
|---|---|---|---|
| debug | 가능 | 가능 | 127.0.0.1 시연 |
| demo | 가능 | 불가 | 127.0.0.1 시연 |
| 일반 release | 불가 | 불가 | INTERNET 권한 없음 |
| 제출 release (`lifelinkSubmission=true`) | 가능 | 불가 | 127.0.0.1 시연 |

제출 APK는 `Build-Release.cmd`로 빌드하며 전용 비공개 release 키를 사용한다. demo APK는 로컬 시연을 위해 debug 인증서로 서명하며 실서비스 배포 서명으로 사용하지 않는다. `BuildConfig.DEMO_FEATURES`가 시연 기능을 제어한다.

## 데이터 삭제 검사

일상 시연용 `Pleos_Connect_API_34`와 `LifeLink119_Std_API_34`에서는 의료정보 삭제 검증을 거부한다. `scripts/start-qa-emulator.ps1`은 별도의 `LifeLink119_QA_API_34`를 생성·기동한다. 공식 Pleos 프로필의 2560×1440을 사용하고 일상 AVD의 userdata나 snapshot을 복사하지 않는다.

개발 셸에서:

```powershell
.\scripts\start-qa-emulator.ps1
adb -s emulator-5584 install --no-streaming -r app/build/outputs/apk/debug/app-debug.apk
python scripts/prepare-ui-snapshot.py --serial emulator-5584
python scripts/verify-safe-call-v18.py --serial emulator-5584
python scripts/verify-safe-call-form.py --serial emulator-5584
```

검사 시작 전 기존 PIN/의료정보/프로필이 있으면 보존하고 중단한다. 초기 검사에서 승인된 QA 실행만 이번 실행의 데이터를 정리할 수 있으며, 정리 직전 AVD·현재 Android 사용자·부팅 ID를 재확인한다. 과거 v0.6용 `run-contest-verification.py`는 과거 APK 전용이다. 버전 고정 과거 스크립트를 현재 앱 회귀 검증으로 사용하지 않는다.

## 이번 수정과 남은 일

[수정·검증 기록](REVIEW_FIXES_20261113.md)에 분석 항목별 처리 범위와 결과를 정리했다. 클래스 전체 분리, 과거 문서의 증빙 링크 정비, 발표자료/영상 바이너리의 Release 또는 LFS 이전, 영상 프로젝트 공통화·현재 앱 재촬영은 후속 작업이다. 기존 자료와 영상은 삭제하지 않았다.

초기 응급 대기 확장 빌드는 Kotlin 252개, Python 콘솔 89개, UI·기기 44개 검사를 통과했다. [초기 APK 검증 기록](EMERGENCY_SUPPORT_VERIFICATION.json)은 당시 결과이며 이후 결과는 위 최신 기록을 따른다. 실제 사람이 차량 소음 속에서 말하는 시험, 의료전문가 콘텐츠 검토, 공식 안심콜 조회·기관 신고 연계는 완료하지 않았다.
