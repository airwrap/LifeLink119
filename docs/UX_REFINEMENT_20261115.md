# 음성 중심 UX 개선 기록 — 2026-11-15

사용자가 지적한 설명자료 7·8페이지의 실제 앱 화면을 우선 수정했다. 기존 홈·119 진입 구조와 시연/실제 연결의 구분은 유지한다.

## 사용자 흐름

1. 최초 앱 실행에서 외부 AI 이용 여부를 한 번 선택한다. 마이크 권한은 Android의 별도 최초 권한이다. 더보기 → 음성 AI 설정에서 선택을 바꿀 수 있다.
2. 홈 → 119 긴급신고 → **AI 음성 동행 시작**. 시작 후 질문을 TTS로 읽고 답변을 듣는다. 질문마다 시작/듣기 버튼을 누르지 않는다.
3. 현재 질문에 **예 / 아니요 / 잘 모르겠어요**로 답한다. 명확한 위험 발화는 현재 질문보다 먼저 처리한다.
4. 화면으로 답하기를 누르면 **현재 질문과 세 가지 답만** 표시한다. 음성에서 화면으로 바꿀 때 질문 위치와 모델 해석 확인 대기를 보존한다. 다시 음성으로 이어도 같은 질문을 유지한다. 답변 버튼은 모두 같은 색이며 특정 답을 권하지 않는다.
5. 연결 지연 화면은 음성 동행 시작을 중심에 둔다. 7가지 상태 선택 버튼을 없애고, 보조 화면 확인은 한 질문씩 진행한다. 아니요·모르겠어요를 접수/출동 확인으로 해석하지 않는다.
6. **통화 중이에요**, **다시 전화 준대요**, **출동 기다리래요** 등 명확한 발화는 사용자 진술로만 반영한다. 전화 왔어요/그만은 음성을 멈춘다. 기관 상태를 자동 조회한 결과가 아니다.
7. 호흡 이상, 화재·갇힘, 심한 출혈, 뇌졸중 의심 진술, 급격한 악화는 고정 안전 안내와 119 재연결을 우선한다. 긴 증상 질문을 계속하지 않는다. 실제 전화 연결/접수/출동은 구현하지 않은 시연 앱이다.

## 안심콜·가족정보 활용

정차 후 **응급상황 요약 → 가족 안심콜 정보 포함 → PIN 인증 → 이번 환자 확인 → 이 환자 정보로 응급 요약 만들기**로 연결한다. 저장된 정보가 있다고 해서 환자를 본인이나 특정 가족으로 자동 지정하지 않는다.

기존 암호화 저장 자료에서 병력, 과거 질환/수술력, 알레르기, 복약, 거동, 통화 가능 여부와 어려움, 장애·의사소통, 구급대원 요청, 보호자 연락처, 수정일을 가져온다. 복약·알레르기 원문을 임의로 자르거나 약/치료 안내로 바꾸지 않는다. 혈액형 등으로 처치를 결정하지 않는다. 오래된 정보인지 상담원에게 확인할 문구를 함께 표시한다.

선택은 **이번 incident의 메모리 안에만** 유지된다. 인증 만료, 잠금, 홈, 앱 백그라운드 전환, 다른 사건에서는 해제된다. 복사/가족 앱 공유에는 기존 별도 내보내기 동의를 유지한다. 클립보드는 민감정보 표시, 일정 시간 경과/화면 이탈/인증 만료 시 제거된다. 상대 앱에 이미 붙여넣은 정보는 삭제할 수 없다.

이는 **차량에 사용자가 직접 저장한 정보**이다. 공식 안심콜 웹 로그인/SMS 인증을 자동화하거나 DB를 조회하는 API는 연결하지 않았다. 실제 119/보호자 자동 전송도 하지 않는다. 외부 LLM·PC 합성 상황실 payload에는 이 건강정보를 넣지 않는다.

가족정보의 PIN 인증이 만료되어도 **지금 같은 화면에서 진행 중인 기본 응급 대화는 계속**된다. 가족정보 표시와 내보내기는 즉시 해제한다. 앱을 벗어난 뒤 돌아왔거나 이전 암호화 기록을 복구한 경우에는 기존 관찰 기록의 인증 잠금을 유지한다. 과거 자료를 보호하는 인증과 현재의 응급 대화를 별도 정책으로 관리한다.

## AI 안내의 경계

- 외부 LLM은 허용된 질문 문장 ID 또는 제한된 의미 코드만 반환할 수 있다. 자유 생성된 의료 문장은 앱에 표시하거나 TTS로 읽지 않는다.
- 서버와 앱 양쪽에서 입력 개념과 결과가 맞는지 검사한다. 예를 들어 호흡 곤란/부정/모름이 있는데 정상 호흡이라는 답을 반환하면 버린다. 유효한 모델 해석도 사용자 확인 전에는 상태로 기록하지 않는다.
- 위험 안내는 코드가 선택한 고정 콘텐츠다. 앞선 중요한 경고를 뒤의 다른 증상으로 지우지 않는다. 이는 화면 표시 우선순위이며 임상 분류 점수나 진단이 아니다.
- 인계 요약에는 최초/최근 의식·호흡, 서로 다른 증상, 시각, 사용자/합성 출처를 유지한다. 증상들을 하나의 마지막 값으로 덮어쓰지 않는다.
- 오프라인 인식은 짧은 응답과 긴 명령을 별도 제한 문법으로 처리한다. 완전한 허용 문장, 최소 단어 신뢰도, 미확인 토큰을 검사한다. 인식 결과가 충돌하면 채택하지 않는다. 원음은 인식 중 메모리에서만 처리하고 저장·업로드하지 않는다.
- 모델 실패·무응답·인식 실패를 정상 상태로 처리하지 않는다. 재질문 후 화면 답변을 제공한다. 통화 사용자 확인, 오디오 포커스 상실, 앱 전환은 TTS/STT를 멈춘다.

**의료적으로 오류가 절대 없다고 보장할 수는 없다.** 음성 인식과 사용자의 진술은 틀릴 수 있고, 안내 콘텐츠는 공식 자료를 바탕으로 작성했지만 의료전문가 검토 전이다. 실차 주행 소음, 억양, 동시 발화, 운영체제별 통화 오디오 검증과 전문가 검토가 상용 제공 전에 필요하다. 앱은 119 상담원/의료진의 지시를 대체하지 않는다.

공식 콘텐츠 근거: [질병관리청 심폐소생술 안내](https://health.kdca.go.kr/healthinfo/biz/health/gnrlzHealthInfo/gnrlzHealthInfo/gnrlzHealthInfoView.do?cntnts_sn=6226), [AHA/미국적십자 2024 First Aid Guidelines](https://cpr.heart.org/en/resuscitation-science/2024-first-aid-guidelines). 음성 엔진 확인: [Vosk Recognizer](https://github.com/alphacep/vosk-api/blob/master/java/lib/src/main/java/org/vosk/Recognizer.java), [SpeechService](https://github.com/alphacep/vosk-api/blob/master/android/lib/src/main/java/org/vosk/android/SpeechService.java).

## 차량 이미지

이 UX 개선 단계에서는 홈의 일반 생성 차량 이미지를 제거하고 사용자가 제공한 현대자동차 사진을 그대로 사용했다. 이후 사용자 요청으로 사진 속 현대차를 참조한 가족 합성 이미지로 홈과 설명자료를 다시 갱신했다. 현재 리소스는 `drawable-nodpi/hyundai_family_hero.png`이며 [가족 이미지 후속 적용 기록](FAMILY_HYUNDAI_VISUAL_20261115.md)에 최신 자료와 출처를 정리했다. 차종/연식이나 현대자동차의 공식 인증을 추정해 표기하지 않는다.

## 주요 변경 파일

| 파일 | 역할 |
|---|---|
| `ui/EmergencySupportUi.kt` | 질문 하나 중심 UI, 순차 연결 확인, 건강정보 포함/제외 |
| `ui/ExpansionUi.kt` | 기존 건강정보·PIN·화면/인증 수명주기 연결 |
| `ui/AssistanceUi.kt` | 현대차 사진을 사용하는 홈 |
| `core/emergency/EmergencyVoiceConversation.kt` | 질문 보존, 위험 시 질문 중단, 발화 기반 연결 확인, 의미 코드 검증 |
| `core/emergency/EmergencyCallCheck.kt` | 연결 상태 순차 질문/명시적 발화 매핑 |
| `core/emergency/EmergencyAiCoordinator.kt` | 중요 경고·복수 증상·최초/최근 관찰 유지 |
| `core/emergency/EmergencyHealthSelection.kt` | 사건·인증·주행 제한에 묶인 환자 선택 |
| `core/emergency/EmergencyObservationPrivacy.kt`, `data/EmergencySupportController.kt` | 가족정보 인증 만료와 현재 대화 분리, 과거 관찰 기록 잠금 |
| `data/EmergencyCheckpointStore.kt` | 재시작 후에도 중요 위험 안내를 유지하는 복구 |
| `data/EmergencyHealthSummary.kt` | 저장된 건강정보의 결정적 전달 문구 |
| `data/EmergencyGrammarRecognizer.kt`, `data/OfflineKoreanSpeech.kt` | 제한 문법 인식과 충돌/미확인 결과 거부 |
| `console/emergency.py` | 앱과 동일한 경고 유지 및 모델 결과 검증 |

## 재현 및 자료

- 전용 QA: `emulator-5584`, `LifeLink119_QA_API_34`. 기존 개인 데이터가 있으면 검사 스크립트가 중단한다.
- `Build-And-Test.cmd`: 포맷, Debug/Release/Demo, 단위 검사, lint.
- `python -m unittest discover -s console -p "test_*.py"`: 서버 검사.
- `python scripts/verify-refined-ux.py --serial emulator-5584`: 선택지 제한, 질문 진행, 위험 경고, 콜백, 통화 중 음성 차단.
- `python scripts/verify-emergency-health.py --serial emulator-5584`: 가상 환자 생성 후 명시적 포함/제외/백그라운드 해제, 생성 자료는 UI로 정리. 보안 화면은 캡처하지 않는다.
- 기존 `verify-emergency-support.py`, `verify-emergency-recovery.py`, `verify-minimal-voice.py`, `verify-voice-conversation.py`도 새 조작 흐름에 맞췄다.
- `:app:connectedDebugAndroidTest`: 실제 Android 네이티브 음성 디코더와 저장 동의 검사. 합성 음성 자료만 사용한다.
- 시연: `.tools/Start-LifeLink-Voice-Demo.cmd` → 더보기 → 음성 동행 자동 시연. 여섯 합성 응답이 버튼 조작 없이 진행되며 실제 마이크 인식과 구분 표시된다.

이 단계의 설명자료는 패키지의 `deliverables/LifeLink119_VoiceGuide_Refined_20261115/output/`에 보존했다. 이후 현대차 가족 이미지까지 반영한 [최신 PPTX/PDF·제작 소스](presentation/voice-family-20261115/README.md)를 저장소에 포함했다. 7·8페이지는 수정된 앱의 새 캡처, 10페이지는 가족정보 활용 절차를 설명한다. [이 단계의 기능 검사 결과](UX_REFINEMENT_VERIFICATION.json)와 [이후 이미지 교체 검사 결과](FAMILY_HYUNDAI_VERIFICATION.json)를 구분한다.

## 최종 확인 결과

| 범위 | 확인 결과 |
|---|---|
| 빌드·포맷 | Debug / Release / Demo, Spotless 통과 |
| Kotlin 단위 검사 | 288개 통과, 실패·건너뜀 없음 |
| Python 서버 검사 | 98개 통과 |
| Android 네이티브 검사 | 3개 통과. 13개 합성 음성 × 일괄 byte 입력 / 연속 short 입력 = 26회 인식 확인 포함 |
| 화면·통합 검사 | 34개 통과: UI 6, 건강정보 5, SDK·복구 5, 모의 상황실·오프라인 등 12, 음성·동의 6 |
| Lint | Debug/Demo 오류 0. 각각 테스트 라이브러리 업데이트 알림 2개 |
| 외부 LLM | 기존 `.env` 설정을 사용한 프록시 응답과 동의 철회 확인. 키·주소는 보고서에 노출하지 않음 |
| 개인정보 | 실제 환자 자료 사용 없음, 가상 건강정보·PIN·복구 기록은 검사 후 UI로 정리 완료 |
| 설명자료 | PPTX/PDF 각 16페이지, 원본 현대차 사진 동일성 확인, 전체 PDF 시각 검수 및 PPTX 패키지·배치 검사 통과 |

Android 검사는 전용 QA 에뮬레이터에 설치된 같은 APK에서 실행했다. 기존 일상용 AVD의 정보는 초기화하지 않았다. PDF는 PPTX와 동일한 편집 가능한 슬라이드 배치를 사용해 생성했다. 이 PC에서 PowerPoint 앱을 직접 실행해 검수한 것은 아니다.

실제 사람이 차량 소음 속에서 말하는 시험, 실차 승인 신호, 공식 안심콜 자동 조회 및 실제 기관 신고·출동 연계, 의료전문가 콘텐츠 검토는 완료하지 않았다. 합성 음성 통과율을 실사용 정확도로 설명하지 않는다.
