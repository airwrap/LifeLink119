# 로컬 모의 상담센터: 설계 및 v0.2 구현

원래 CODEX 프롬프트 3의 설계를 바탕으로, 사용자의 후속 구현 요청에 따라 `console/server.py`와 두 브라우저 화면을 구현했습니다. 실제 공공 신고 시스템과 연결하지 않습니다.

## 실행과 데이터 경계

- 기본 주소 `http://127.0.0.1:8781`. IPv4 loopback에만 바인딩합니다.
- 상담원 `/`, Android 없이 체험하는 탑승자 `/passenger`.
- 정적 HTML/CSS/JavaScript, Python 표준 HTTP 서버, 검증용 jsonschema 4.23.0 사용.
- 가짜 세션 최대 50개, 생성 후 60분 메모리 보관. 프로세스 재시작 시 삭제. DB·사용자 파일 저장 없음.
- Host는 지정 loopback 주소만 허용. API는 다른 Origin/cross-site 요청을 거부하며 CORS 허용 헤더를 내보내지 않습니다. 외부 링크에서 UI 첫 화면으로 이동하는 GET은 허용합니다.
- JSON 본문 최대 10KB, 중복 JSON 키·비정상 값·알 수 없는 필드 거부. 요청 본문은 로그로 기록하지 않습니다.
- 서버에 외부 HTTP 클라이언트나 긴급기관 어댑터를 넣지 않았습니다.

## API

| 요청 | 동작 |
|---|---|
| GET `/api/demo/health` | DEMO 모드, v0.2.0, loopback 상태 |
| GET `/api/demo/catalog` | 5개 시나리오와 고정 가상 상황 10종 |
| POST `/api/demo/sessions` | 가짜 세션 생성. `Idempotency-Key` 필수, 신규 201/동일 재시도 200 |
| GET `/api/demo/sessions` | 최신순 가상 세션 목록 |
| GET `/api/demo/sessions/{id}` | 상세. 만료/없음은 404 |
| POST `/api/demo/sessions/{id}/actions` | `ACKNOWLEDGE`, `REVIEW`, `GUIDANCE`, `CLOSE` |
| POST `/api/demo/sessions/{id}/close` | 모의 종료. 반복 호출도 같은 종료 상태 |
| POST `/api/demo/sessions/{id}/revoke` | 차량·위치·영상·경로를 null로 제거, 동의 전부 OFF |
| POST `/api/demo/reset` | 가짜 기록 초기화. 프로세스 내 세션 번호는 재사용하지 않음 |

생성 외 상태 변경 본문은 `{"syntheticOnly":true}`이고 actions에는 `action` 필드만 추가합니다. 알 수 없는 action은 422, 잘못된 순서는 409입니다.

## 진행 상태

`DEMO_CONNECTED` → `DEMO_RECEIVED` → `DEMO_REVIEWING` → `DEMO_GUIDANCE` → `DEMO_CLOSED`

어느 열린 단계에서도 종료할 수 있습니다. 같은 현재 단계의 동작은 중복 기록을 만들지 않습니다. 뒤로 돌리거나 검토를 건너뛰는 동작은 거부합니다. 접수·검토·안내는 **모의 진행 상태**이며, 의료 판단·출동·배차·이송·경찰 호송 승인을 뜻하지 않습니다.

서버 ID는 실행마다 다른 12자리 숫자 영역과 증가 번호를 결합합니다. 서버를 재시작해도 기존 클라이언트의 ID가 새 세션을 가리키지 않도록 합니다. 앱 내부의 짧은 `DEMO-0001`과 로컬 서버의 긴 ID는 서로 다른 식별자입니다.

같은 Idempotency-Key로 다른 본문을 보내면 409입니다. 원문 대신 본문 해시를 보관합니다. 공유 철회 후 원래 요청을 재전송해도 철회된 세션을 반환하며 정보를 되살리지 않습니다. 재시작 후에는 이전 세션이 없으므로 새 시연을 시작해야 합니다.

## 공유 규칙과 계약

- 요청/응답: `contracts/demo-session-request.schema.json`, `contracts/demo-session.schema.json`
- 카탈로그/예시: `fixtures/scenario-catalog.json`, `fixtures/demo-sessions.json`
- `caseCode`는 해당 시나리오의 고정 enum만 허용합니다. 자유로운 증상·상황 입력은 받지 않습니다.
- 차량은 `DEMO-CAR-01`/가상 차량 A/가짜 배터리 72%로 고정합니다.
- 위치는 `DEMO_GRID`/가상 훈련장 A구역/(120,80)로 고정합니다. 실제 지도 좌표가 아닙니다.
- 합성 영상은 VIDEO 시나리오의 명시적 동의에만 허용합니다. `demo://video/...`는 표시용 식별자이며 URL 다운로드·카메라·영상 재생을 수행하지 않습니다. 화면에는 합성 도형 애니메이션만 표시합니다.
- 동의 false면 해당 값은 null이어야 하며, true여도 허용된 고정 값이 있어야 합니다.
- 위치 동의와 모의 안내 단계가 있으면 가상 만남 지점 B까지 고정 격자 경로를 표시합니다. 실제 병원 선택이나 내비게이션을 실행하지 않습니다.

## Android 연결

Debug 앱의 목적지는 `http://127.0.0.1:8781`로 고정하며 다른 주소로 바꿀 입력란이 없습니다. ADB reverse로 장치의 localhost를 호스트의 localhost에 연결합니다. `Connect-Local-Console.cmd emulator-5580` 또는 `Run-Full-Demo.cmd`를 사용합니다. 실제 카메라·GPS·SDK 진단 값을 전송하지 않습니다.

연결 실패는 공유 성공으로 처리하지 않습니다. 앱은 같은 요청 식별자로 재시도하고, 알려진 세션을 2.5초 간격으로 읽습니다. 화면/앱 종료 시 폴링을 중단합니다. 서버에서 공유를 철회하면 앱의 가상 경로도 숨깁니다. release에는 INTERNET 권한과 로컬 평문 예외가 없습니다.

## 브라우저 화면

고정 시연 고지, 목록/시나리오 필터, 상세 동의별 표시, 합성 영상, 가상 경로, 접수·검토·안내·종료·철회, 타임라인, 빈 상태, 서버 실패·만료·용량 오류를 제공합니다. 보이지 않는 탭에서는 자동 읽기를 멈추고, 변경 없는 데이터는 다시 그리지 않습니다. 상태 전이 중 키보드 초점을 다음 조작으로 유지하며, 390px과 데스크톱에서 가로 넘침을 확인했습니다.

검증은 `console/test_server.py`, `scripts/validate-demo-contract.py`, `scripts/smoke-v02.py`, `verification/v02-web-e2e.json` 및 구현 보고서를 참조하세요.
