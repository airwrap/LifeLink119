# 외부 LLM 프록시 설정

2026-11-15 · 음성 중심 AI 응급 대기 동행에 적용. 외부 연결 없이도 고정 질문·응급 안내가 작동한다.

## 현재 연결

패키지 루트의 `.env`에 있는 `LIFELINK_LLM_*` 값을 서버 시작 시 읽는다. 갱신된 주소와 `gpt-oss-120b` 모델로 실제 승인 질문 선택 및 시험용 의미 코드 해석을 확인했다. 실제 API 키와 서버 주소는 이 문서·APK·검증 결과에 기록하지 않는다.

로드 순서는 **패키지 루트 .env → LifeLink119/.env → 프로세스 환경변수**이다. 뒤의 값이 우선하며 같은 파일의 중복 키도 마지막 값이 적용된다. `--env-file`을 지정하면 그 파일만 읽고 프로세스 환경변수가 최우선이다. 셸 실행·변수 확장 없이 허용된 다섯 설정만 읽는다. `.env` 수정 후 서버를 재시작한다.

| 변수 | 설정 |
|---|---|
| `LIFELINK_LLM_MODE` | `disabled`(설정 없을 때 기본값), `mock`, `external` |
| `LIFELINK_LLM_PROTOCOL` | `chat-completions` 또는 기존 `approved-phrase` |
| `LIFELINK_LLM_URL` | 공급자 HTTPS 주소. Chat Completions에서는 `/v1` 같은 기본 경로에 `/chat/completions`를 붙이며 완전한 경로는 그대로 사용 |
| `LIFELINK_LLM_API_KEY` | 서버에서만 읽는 Bearer 키 |
| `LIFELINK_LLM_MODEL` | 공급자의 모델 ID |

```powershell
# 프로젝트 LifeLink119 폴더의 개발 셸
.\Stop-Local-Demo.cmd
.\Start-Local-Demo.cmd
adb -s emulator-5584 reverse tcp:8781 tcp:8781
```

별도 `.env` 파일로 실행하려면 `python console/server.py --env-file <파일경로>`를 사용한다. `LIFELINK_LLM_MODE=disabled`가 셸에 남아 있으면 .env보다 우선하므로 기존 의도적인 설정인지 확인한다. 설정 파일은 Git 제외 대상이며 키를 명령행 인자로 넘기지 않는다.

## 앱 사용과 동의

**최초 앱 실행**에서 외부 AI 사용 여부를 한 번 선택한다. 저장된 동의·거절은 앱 재실행과 새 사건에서도 유지하며 **더보기 → 음성 AI 설정**에서 바꾼다. **119 연결 화면 → AI 음성 동행 시작**을 한 번 누르면 질문·듣기가 이어진다. 마이크 권한은 OS가 최초 1회 요청하며, 허용 후 시작 버튼을 다시 누를 필요가 없다. 자세한 동작은 [음성 동행 구현·사용법](VOICE_AI_IMPLEMENTATION.md)을 참고한다.

앱에는 외부 키나 주소를 넣지 않는다. debug/demo 앱은 ADB 터널의 `http://127.0.0.1:8781/api/demo/emergency/phrase`와 `/interpret`만 호출한다. release에는 INTERNET 권한이 없으며 실제 배포용 원격 프록시·인증·기관 연동은 별도 개발 사항이다.

## 보내는 정보

질문 선택은 기존 단계 코드만 사용한다.

```json
{"version":1,"consent":true,"step":"CONSCIOUSNESS","easy":true}
```

기기 내 음성인식 결과가 명확한 지원 발화가 아닐 때만, 정해진 어휘로 추출한 의미 코드를 보낼 수 있다.

```json
{"version":1,"consent":true,"step":"BREATHING","concepts":["BREATH","DIFFICULT"]}
```

이름·전화번호·GPS·사건 ID·가족 건강정보·발화 원문·음성 녹음은 보내지 않는다. 의미 코드도 건강 맥락이므로 외부 전송 동의가 필요하다. 단어 추출은 문맥을 잃을 수 있어, LLM이 고른 관찰 코드는 **사용자가 고정된 확인 질문에 예라고 답한 뒤에만** 기록한다. 진단이나 정상 상태를 자동 확정하지 않는다.

## 응답 경계

- 질문 응답은 허용된 `phraseId` 하나. 앱 안의 승인 문장으로 바꿔 읽는다.
- 의미 응답은 해당 단계에 허용된 `observationCode` 하나 또는 null. 임의 문장·진단·약물·의료 절차는 표시하거나 읽지 않는다.
- Chat Completions 요청은 `temperature=0`, `max_tokens=384`, `stream=false`, JSON 응답 형식을 사용한다. 고정 system 지시와 한정된 코드 payload만 포함한다.
- 모델이 다른 단계의 코드, 추가 필드, 자유 의료 조언, 잘린 응답을 반환하면 버린다. [Chat Completions 호환 형식의 공식 예](https://docs.ollama.com/api/openai-compatibility).
- 명확한 위험 진술은 규칙 기반 안내가 먼저 작동하며 모델 요청 때문에 안내를 늦추지 않는다.

## 제한과 장애

공개 HTTPS 443만 허용하며 URL 사용자 정보·query·fragment, 사설/루프백 DNS 주소, 리다이렉트를 거부한다. 주소는 관리자 설정으로만 지정한다. 두 API가 합쳐 분당 12회, 동시 호출 2개를 공유한다. 소켓 3초, 요청 대기 3.5초, 전체 외부 응답 8KB, 최종 JSON content 256자 상한이다. 키·요청 본문·모델 응답·민감한 예외 내용은 로그에 쓰지 않는다.

연결 실패, 타임아웃, 무효 응답에는 고정 질문으로 복귀한다. 화면 이탈·통화·동의 철회·대화 중단 후 도착한 결과는 폐기한다. 앱 백그라운드/재시작 시 진행 중 음성과 요청은 중단하되, 명시적으로 저장한 외부 AI 사용 선택은 유지한다. 새로운 음성 세션은 사용자가 시작한다. 전송 범위가 바뀌어 동의 문서 버전이 달라진 경우 기존 동의로 자동 전송하지 않는다. DNS를 찾을 수 없으면 최신 서버 주소인지 확인한다. 이 경우에도 오프라인 응급 안내와 기존 신고 시연은 유지된다.

검증에는 실제 외부 모델과 앱↔로컬 서버 경로, 모의 통신 실패·프롬프트 주입·동의 거부·잘못된 응답을 포함한다. 실제 환자 정보나 사람의 음성은 외부 호출 시험에 사용하지 않았다.
