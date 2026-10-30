# LifeLink 119 v0.5.0-demo · 구현·검증 보고서

작업일: 2026-10-28. 지시: [통합 확장 프롬프트](LIFELINK_EXPANSION_PROMPT.md). 기존 프로젝트에 적용했으며 Git 커밋/푸시·계정 연결·실제 신고·외부 개인정보 업로드는 하지 않았다.

## 결과

홈의 두 의도에서 시작해 기관별 가상 연결, 선택적 가족·상황, 최대 세 개 맥락형 작업, 추가 지원, 종료·공유 정리까지 연결했다. S01~S14 + E01~E04가 한 JSON 카탈로그와 단일 세션 엔진을 사용한다. A~M의 모든 범주에 상호작용 경로를 제공하되 실제 외부 기관·카메라·위치·차량 제어는 모의/미연계로 구분한다.

기존 Kotlin Views·MainActivity·Vehicle SDK 2.3.2·min 28/target 34를 유지했다. 기존 5개 흐름, 기본 응급 매뉴얼, 주변 차량 협력·승인형 표시와 실제 SDK 진단은 더보기에서 접근한다. 확장 전 소스는 `verification/backups/before-ux-expansion-20261028-013300.zip`으로 보존했다.

## 변경 파일과 이유

| 파일 | 역할 |
|---|---|
| `MainActivity.kt` | 새 화면 호스팅, 실제 CarUX 수명주기, 기존 도구 복귀 경로 |
| `core/Experience.kt` | 기관/프로필/동의/지원 상태, 3-action 규칙, V2L 계산 |
| `core/ConsultationSummary.kt` | 외부 모델 없는 오프라인 템플릿과 향후 provider 인터페이스 |
| `data/ExperienceData.kt` | JSON 검증, 가상 프로필 저장, 기존 MockDispatch 재사용, 복원값 검증 |
| `data/CameraGateway.kt` | 기존 인터페이스 유지, 합성 영상 전용 Mock 어댑터 |
| `data/ExperienceConsoleBridge.kt` | 선택적 debug localhost 메타데이터 게시/철회 |
| `vehicle/DrivingRestrictionsGateway.kt` | 실제 CarUxRestrictions 리스너와 해제·실패 처리 |
| `ui/ExpansionUi.kt` | 기존 Views 방식의 새 화면·접근성·반응형 구조·합성 프레임 |
| `assets/experience-catalog.json`, `res/values/experience_ids.xml` | 18개 사례·고정 가상 어휘·가상 인물·검사 가능한 뷰 ID |
| `app/build.gradle.kts`, `AndroidManifest.xml` | v0.5/code6, optional android.car, JVM JSON 테스트 의존성 |
| `core/ExperienceTest.kt` | 18개 기본/미등록 경로, 상태·동의·복원·전력 경계값 |
| `console/experience.py`, `server.py` | 이름/의료내용을 받지 않는 별도 스냅샷 API, 기존 API 유지 |
| `console/static/experience.html/js`, `test_experience.py` | PC 보조 표시, 엄격한 계약·철회·오리진·용량/만료 검사 |
| `scripts/smoke-v05.py`, `smoke-v05-extra.py`, `verify-v05-artifacts.py` | 새 네이티브 흐름·부가검사·설치 APK/서명/권한 검증 |
| `scripts/android-ui-snapshot/SnapshotInstrumentation.java` | 새 ScrollView와 가상정보 확인 dialog의 제한된 검사 지원 |
| `scripts/local-demo.ps1`, `run-full-demo.ps1` | 서버/APK 버전·실행 경로 갱신, emulator display 0 명시 |
| `README.md`, `AGENTS.md`, `docs/CODEX_PROMPTS.md` 및 새 설계 문서 | 현재 진입·실제 플랫폼 제한·검증 범위와 과거 기록 구분 |

전체 경로는 `app/src/main/java/com/lifelink119/app/` 기준이다. 기존 문서·HTML/PPTX·APK는 이력으로 보존했다.

## 검증 근거

| 검사 | 현재 결과 | 근거 |
|---|---|---|
| 확장 전 기준선 | debug build + 기존 36개 단위 통과 | `verification/v05-baseline-build.log` |
| 최종 debug/release 컴파일·단위·lint | BUILD SUCCESSFUL, exit 0 | `verification/v05-final-build.log` |
| Android 단위 | **98개, 실패/오류/skip 0** | `app/build/test-results/testDebugUnitTest/TEST-*.xml` |
| Python 서버 회귀 | **60개 통과**, 기존 API/협력/표시 + 신규 9개 | `verification/v05-python-tests.log` |
| lint | **오류 0 / 경고 7** | `app/build/reports/lint-results-debug.html`; 동적 ID 조회·한국어 문자열 리소스화 권고 |
| Pleos API 34 네이티브 | **13개 검사 통과**, 대표 S01/S08/S14 포함 | `verification/v05/native-smoke.json`, `v05-native-smoke.log` |
| 부가 네이티브 | 가상 가족 추가/저장/기본 복원, 앱에서 PC 메타데이터 게시/철회 **2개 통과** | `verification/v05/native-extra.json`, `v05-native-extra.log` |
| 실제 CarUX | REAL 콜백, 당시 `ActiveRestrictions=0` | 네이티브 도구 화면; 실제 속도/기어 변화 검증과 구분 |
| 기존 Vehicle SDK | 새 APK에서 도어 READ/운전석 값 0, 배터리 READ/SDK 원본 100 | `verification/v05/sdk-runtime.json`, `15-sdk.xml/png`; 원본 100을 SOC %로 변환하지 않음 |
| 추가 제한 시연 | 상세 화면 차단 + 프로필 없는 긴급 진입 유지 | `verification/v05/10-restricted-detail.png` |
| APK 설치본 | 빌드·배포·설치본 SHA-256 일치, 버전 code6/name0.5.0-demo | `verification/v05-artifact-report.json` |
| APK 서명·권한 | debug 서명 v2 확인, 전화/SMS/카메라/마이크/GPS 권한 없음, release INTERNET 없음 | `verification/v05/signing.txt`, `permissions.json` |

`release`는 서명 없는 컴파일 산출물이며 배포용 출시본이 아니다. 배포한 것은 debug 시연 APK다. 최종 APK는 `artifacts/LifeLink119-v0.5.0-demo-debug.apk` (10,193,036 bytes).

```text
SHA-256 c772742c9bb850daa8b13208c98b9bed52b4539bf3aa59960160ce7654471ab0
```

네이티브 대표 검증:

- 119 긴급·112 직접 신고는 프로필·동의 없이 각각 두 탭으로 시연 요청. 109는 프라이버시 행동 우선.
- S01: 병력/알레르기 2항목 동의 → 상황실 2개 반영 → 스마트폰 합성 영상 요청/동의/시작/장면 변경/중단 → 종료.
- S08: 문자 프리셋/가상 답장 → 가상 수어통역 3자 상태 → 종료.
- S14: 위치/차량 → 경찰 요청/검토/거절/재요청/가상 승인 → 전문가 안내/위치 동의/제안/사용자 확인/가상 목적지 설정 → 종료.
- EV: 가정 기본값 22.848kWh/114.24시간과 V2L 미지원 fallback. 기존 도구 진입, 실제 CarUX 수신, 추가 제한/해제.

검사 중 합성 View가 부모 높이를 채워 버튼을 밀어내는 문제를 발견했다. 높이를 180dp로 명시해 수정했고, 수정 후 위 전체 네이티브 검사를 다시 통과했다. 과거 버전의 화면 성공이나 설치 명령 성공만으로 새 버전 통과를 대체하지 않았다.

좁은 세로 화면 검사는 **부분 완료**다. 전용 에뮬레이터에서 900×1600 크기 변경을 요청했지만 앱 접근성 bounds는 기존 Pleos 다중 창의 1682×1210을 유지해 세로 단일 열 배치를 실증하지 못했다. 원래 물리 크기 2560×1440으로 복구했고 부가 결과를 PARTIAL로 남겼다. 반응형 분기는 구현했으나 화면 크기/방향·다중 창 전체 검증 완료로 표시하지 않는다.

## 수용 기준 점검

- [x] 홈 대표 CTA 2개, 가족 보조, 차량/시연 도구 별도.
- [x] 미등록 승객으로 119/112 시연, 119 긴급/상담/112/109 목적 구분.
- [x] 18개 갤러리 실행과 각각의 기본·미등록 단위 흐름.
- [x] 최대 세 기본 행동, 접근성·기관·실제 제한별 우선순위.
- [x] 가상 프로필 CRUD/마스킹, 항목별 동의·철회·대상 변경·종료 정리.
- [x] 문자/수어/영상/위치/상황실/경찰/목적지/안심콜/키 **모의 상태 동기화**.
- [x] EV 수식/0%/보존 초과/0W/미지원/비정상 숫자 단위 검사.
- [x] 실제 기관 호출·개인정보 외부 전송·카메라/GPS 수집 없음.
- [x] 기존 실제 SDK 코드·매뉴얼·협력 모듈 유지, 서버 회귀/기존 도구 진입.
- [x] 버튼 64dp·체크56dp·슬라이더48dp, 고대비·큰 글씨·접근성 라벨 및 순서 구현.
- [x] 실제 CarUX 구독·제한 중 상세/영상/긴 목록 차단. DO 우회 메타데이터 없음.
- [x] 실패/재시도/취소/위치 없음/미등록/권한·SDK 미지원 fallback 및 복원·백그라운드 잠금 코드/단위 검사.
- [x] 단위·서버·3개 대표 네이티브 검증, 새 APK·문서·대본·소스 구분 제공.
- [ ] 실제 TalkBack 낭독 전 과정, 실차 주행 flag 전환, OEM DrivingView/심사 및 실제 외부 연계는 미검증.
- [ ] 좁은 창/세로 화면 전체 검증은 Pleos 창 크기 고정 문제로 미완료. 기본 가로 화면은 네이티브 캡처로 확인했다.
- [ ] 기존 주변 차량 협력의 전체 네이티브 E2E를 이번 버전에서 다시 반복하지는 않았다. 기존 서버 회귀는 통과했다.

## 미연계와 다음 단계

이 결과는 **VERIFIED_WITH_LIMITATIONS**인 공모전 데모다. [전체 기능 매트릭스](FEATURE_MATRIX.md)와 [SDK 공백](SDK_GAPS.md)에 REAL/MOCK/NOT_AVAILABLE/NEEDS_PARTNERSHIP을 구분했다. 신규 일반 흐름은 앱 내 센터로 오프라인 완결되며 선택적 PC 전송만 localhost를 사용한다.

차기 검증은 실제 주행 제한 전환/회전·다중 창·TalkBack, 차종별 SDK 권한·원본 응답, 기관/OEM 협약 및 의료진 문구 검토 순서로 진행한다. 실제 카메라·GPS·내비·통신·차량 제어를 붙이는 작업은 별도 어댑터와 동의/인증/실차 검증이 필요하다.

실행 명령은 [README](../README.md), 발표 클릭 순서는 [DEMO_SCRIPT](DEMO_SCRIPT.md), 화면 근거는 `verification/v05/*.png` 및 XML을 사용한다.
