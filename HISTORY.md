# LifeLink 119 · 가족을 위한 차량형 응급지원 데모 v0.18.0

**시연 모드 | 실제 신고·상담 연결 아님**

가족·반려견 이미지 업데이트: [현대 SUV와 가족, 강아지가 함께 걷는 홈 이미지](docs/FAMILY_DOG_VISUAL_20261115.md)를 원본 앱에 적용했습니다. 홈 문구도 **나와 내 가족을 위한 안전 동행**으로 맞췄습니다. 앱 빌드와 단위 테스트 288개를 통과했습니다.

2026-11-15 최신 시연영상: [가족 나들이 중 응급질환부터 AI 음성 동행·인계요약까지](https://github.com/airwrap/LifeLink119-video/tree/main/contest-demo-20261115). 최종 MP4, 녹화 원본, 편집·재현 코드와 QA를 영상 저장소에 보존합니다. 가족·반려견 업데이트의 홈과 상담 진입 장면은 현재 원본 앱에서 재촬영했습니다.

2026-11-15 최신 설명자료: [상황별 사용·탑승자 안전 24장 PPTX/PDF](docs/presentation/scenario-safety-20261115/README.md). 긴급신고·상담·신고 후 대기, 안심콜 정보 활용과 폭염·차량 내 고립·심리적 위기·차박 공기질 대응을 설명합니다. 신규 온도·CO₂·CO·잔류 감지는 [센서 연동 확장안](docs/CABIN_SAFETY_EXTENSION_20261115.md)이며 현재 앱에 연결된 기능과 구분합니다.

음성 중심 앱 개선: **AI가 한 질문씩 읽고 사용자가 음성으로 답하는 흐름**, 환자 확인 후 안심콜 건강정보를 응급 요약에 포함하는 기능, 고정 위험 안내·오프라인 인식 보강과 현대차 가족 합성 이미지를 반영했습니다. [기존 16장 안내와 제작 소스](docs/presentation/voice-family-20261115/README.md) · [UX 개선·검증](docs/UX_REFINEMENT_20261115.md). 빌드 후 `Start-Voice-Demo.cmd`로 음성 시연을 실행합니다. 외부 LLM은 `.env.example`을 참고해 로컬 `.env`에 설정합니다.

2026-11-15 음성 동행: `.env`의 실제 LLM 연결을 적용했습니다. 최초 1회 동의 후 한 번 시작하면 **AI 질문(TTS) → 음성 답변 → 다음 질문**으로 이어집니다. 더보기의 **음성 동행 자동 시연**은 예시 응답부터 인계 요약까지 자동 진행합니다. [사용법·음성 실행·검증](docs/VOICE_AI_IMPLEMENTATION.md).

2026-11-14 고도화: 기존 119 화면에 차량·위치 응급 요약, 연결 지연·콜백 대기, **AI 응급 대기 동행**을 추가했습니다. [구현 결과·SDK/Mock 구분·검증·시연 순서](docs/EMERGENCY_SUPPORT_IMPLEMENTATION.md) · [외부 LLM 설정](docs/LLM_PROXY_SETUP.md).

현재 실행·빌드·검증 기준은 [현재 상태](docs/CURRENT_STATUS.md), 이번 결함 수정은 [2026-11-13 수정 기록](docs/REVIEW_FIXES_20261113.md)을 참고하세요. **소방청 로그인 → 차량 정보 가져오기 → 신고 전달**의 가능 범위와 선행 조건은 [안심콜 연계 검토](docs/SAFE_CALL_WEB_INTEGRATION_REVIEW.md)에 정리했습니다.

제공된 안심콜 입력 화면 5개를 반영한 상세 항목·복수 보호자 저장과 마이크 오류 해결은 [공식 양식 대조·보완](docs/SAFE_CALL_FORM_AUDIT_20261113.md)에 정리했습니다. 공식 사이트의 본인전송요구 절차도 확인했으며, 현재 앱의 자동 가져오기·실제 신고 전달은 미연결입니다.

비공개 개발 저장소: <https://github.com/airwrap/LifeLink119> · [새 PC 실행 가이드](docs/OTHER_PC_GUIDE.md) · [협업 규칙](CONTRIBUTING.md) · [20개 개선 검토 항목](docs/reviews/20261030/README.md)

최신 가족정보·안심콜 기능은 [v0.18 구현 범위](docs/SAFE_CALL_IMPLEMENTATION_V18.md), 전체 화면과 이용 흐름은 [v0.17 기준 전체 매뉴얼](docs/manual/v17/output/LifeLink119_Manual_v0.17.html)을 참고하세요.

## 가족정보 관리와 공식 가입 안내 · v0.18

본인·가족 건강정보 최대 12개를 인증 후 암호화 저장·조회·수정·개별 삭제합니다. 수혜자/대리자 관리 방식, 등록 연락처, 등록 여부에 대한 사용자 확인과 수정 시각을 기록합니다. 실제 119안심콜·소방청·건강정보 고속도로의 공식 사이트는 개인정보를 붙이지 않고 외부 브라우저로 연결합니다. 사이트 방문이나 사용자 체크를 공식 등록 확인 응답으로 취급하지 않습니다.

긴급 연결 안에서는 **가상** 대상·신고 회선을 대조해 일치·불일치·확인 불가·공용 번호와 조회 오류를 구분합니다. 대상 미선택을 미등록으로 처리하지 않으며, 번호 불일치·조회 실패로 긴급 연결을 막지 않습니다. 실제 발신번호 확인·기관 등록/조회 API·의료정보 전송은 미연결입니다. [구현·검증 범위](docs/SAFE_CALL_IMPLEMENTATION_V18.md) · [실제 기관 연계 준비](docs/AGENCY_LINKAGE_HANDOFF.md)

- [v0.18 APK (로컬)](artifacts/LifeLink119-v0.18.0-family-safecall.apk)
- 검증: 단위 테스트 **208개**, lint **오류 0·경고 0**, 새 기능 조작 **13개 묶음**, 기존 공개 화면 **9개 묶음**, 실제 디스플레이 **1280×720·1024×600** 통과. 같은 APK에서 공식 SDK 도어·배터리 값 변경과 복구를 확인했습니다. [공개 화면·검증 증거](docs/reviews/safe-call-v18/README.md) · [APK SHA-256](artifacts/LifeLink119-v0.18.0-family-safecall.apk.sha256)

## 어두운 배경과 밝은 버튼 · 전체 매뉴얼 · v0.17

차콜 배경에서 누를 곳이 잘 보이도록 홈 네 메뉴·긴급 요청·상담·영상 안내·차량 상태·설정·기존 매뉴얼의 버튼을 밝은 바탕과 짙은 글자로 통일했습니다. 주요 동작은 따뜻한 흰색, 보조 동작은 밝은 청회색, 119·112 동작은 각각 옅은 빨강·파랑을 사용합니다. 네 홈 메뉴의 비중은 같고, 기관 번호는 짙은 빨강 119·파랑 112로 구분합니다. 누름·포커스·비활성 상태도 구분하며 기존 96dp 터치 영역과 가운데 정렬을 유지합니다.

- [전체 이용·시연 매뉴얼 HTML](docs/manual/v17/output/LifeLink119_Manual_v0.17.html): 검색 가능한 목차·실제 앱 화면 확대·오프라인 열람.
- [인쇄·제출용 PDF](docs/manual/v17/output/pdf/LifeLink119_Manual_v0.17.pdf): 전체 흐름과 화면별 조작법, 보호 화면의 절차 안내, 구현·연계 범위.
- [UI 개선과 검증 기록](docs/UI_BRIGHT_CONTROLS_V17.md) · [촬영 원본·재생성 안내](docs/manual/v17/README.md).
- [v0.17 APK (로컬)](artifacts/LifeLink119-v0.17.0-bright-controls.apk).

매뉴얼의 공개 화면은 실제 APK를 표준 Android 34에서 촬영했습니다. 차량 상태 예시는 출처를 표시하고, 공식 SDK 값 비교는 별도 증거로 구분합니다. 의료정보 보호를 해제하거나 PIN·건강정보를 촬영하지 않았습니다.

## 공식 에뮬레이터와 실제 SDK 값 비교 · v0.16.1

실차 없이 **공식 Virtual Console → Pleos 차량 서비스 → Vehicle SDK 2.3.2 → 설치 앱의 Gateway**를 검증했습니다. 운전석 도어 `0→1→0`, 배터리 SoC `100→80→15→100%`가 Console 입력과 일치했고 원래 상태로 복구했습니다. 입력은 공식 에뮬레이터의 가상 신호이며 앱의 예시 분기는 사용하지 않았습니다. 설치본·빌드·전달 APK의 SHA-256도 일치합니다.

에어백은 현재 이미지가 `ERROR_NOT_SUPPORTED_PROPERTY`를 반환해 **미지원**입니다. 정확한 SDK 오류 구분과 미지원 capability 재조회 처리를 보완했습니다. debug 빌드·단위 테스트 **166개**·lint **오류 0 / 경고 0**을 통과했습니다.

[검증 범위·결과·공모전 진행 순서](docs/SDK_RUNTIME_VERIFICATION_V16.md) · [SDK 검사 실행](Verify-Pleos-SDK.cmd) · [v0.16.1 APK (로컬)](artifacts/LifeLink119-v0.16.1-pleos-sdk-poc.apk). 실행 전 JDK 17·API 34 SDK·정식 Pleos 이미지·Python 3가 필요합니다. 실제 차량이나 상담 서버는 SDK 값 비교에 필요하지 않습니다.

## 차량 데이터가 긴급 지원과 계산으로 이어지는 흐름 · v0.16

제공된 공모전 설명회를 소스와 대조해 [20개 개선 항목](docs/BRIEFING_GAP_ANALYSIS_V16.md)을 정리하고, SDK 진단에 머물던 값을 실제 서비스 흐름에 연결했습니다. 기존 큰 버튼·다크 UI·의료정보 잠금·CarUX 제한은 유지합니다.

- **더보기 → 차량 상태**: 운전석 도어 위치·배터리 SoC·에어백 전개 신호를 공식 Vehicle SDK 2.3.2로 읽습니다. 초기화·capability·권한·null·오류를 확인합니다. 차량 제어는 없습니다.
- **119·112 요청 → 차량 상태 → 동의 → 전달**: 출처와 수신 시각을 표시하고 수신 후 30초 이내 조회값만 앱 내 상황실에 전달합니다. 철회·만료·앱 이탈·인물/세션 변경·종료 시 삭제합니다. 실제 기관·PC 전송과 저장 JSON에는 포함하지 않습니다. 긴급 요청은 조회 성공이나 PIN을 기다리지 않습니다.
- **EV 계산 → 최근 차량 SoC 불러오기**: 직접 선택해 조회값을 반영합니다. 용량·V2L 지원·효율·부하는 수동 가정입니다. 불러온 SoC가 만료되면 계산을 보류하고 다시 조회하거나 수동 입력을 선택합니다.
- **실차 없이 확인**: 일반 상태/신호 변화/값 없음/오래된 값 예시를 명시적으로 선택할 수 있습니다. SDK 실패가 예시 성공으로 자동 바뀌지 않습니다. 에어백·도어 신호로 사고·환자 상태를 판단하지 않습니다.

[심층 분석·적용 내용·후속 계획](docs/BRIEFING_GAP_ANALYSIS_V16.md) · [실제 새 화면 7개](docs/reviews/vehicle-context-v16/index.html) · [빌드·네이티브 검사 결과](docs/VEHICLE_CONTEXT_VALIDATION_V16.md) · [v0.16 APK (로컬)](artifacts/LifeLink119-v0.16.0-vehicle-context.apk)

v0.16.0 당시 Pleos AVD의 vendor HAL 반복 종료로 보류했던 SDK 값 검사는 **v0.16.1의 별도 AVD에서 도어·SoC 변화 검증을 완료**했습니다. 당시 표준 Android에서의 명시적 예시·실패·동의·만료 검증과 이번 SDK 결과를 구분합니다. 새 PC에서는 [SDK 연동 가이드](docs/SDK_INTEGRATION.md)를 참고하세요. 위치·음성·내비 SDK는 공개되어 있지만 앱의 해당 기능은 아직 모의입니다. 기존 영상·발표자료를 v16 화면으로 재촬영한 것은 아닙니다.

## 배경과 조작 영역을 구분하는 색상 · v0.15

Pleos 공식 샘플·색상 체계와 Android Automotive 가이드를 검토해, 차콜 바탕·슬레이트 카드·밝은 글씨로 조정했습니다. 네 홈 카드의 비중과 큰 터치 영역은 유지하고, 작은 119 빨강·112 파랑만 의미 색으로 사용합니다. 본문·번호 색과 채움 버튼 색을 분리하고 누름·포커스·비활성 상태 및 기본 대화상자도 맞췄습니다. 현재는 고정 다크 팔레트이며 자동 주야간 전환·실차 시인성 검증을 뜻하지 않습니다.

[실제 앱 화면 비교](docs/reviews/unslop-v15/index.html) · [사례·색상·검증 기록](docs/SDK_COLOR_REVIEW_V15.md) · [v0.15 APK (로컬)](artifacts/LifeLink119-v0.15.0-car-dark-theme.apk)

## 네 가지 도움을 같은 비중으로 · v0.14

구조·구급, 의료·심리상담, 경찰 신고, 가족 안심콜을 같은 흰색 카드와 같은 제목 크기로 표시합니다. 119 빨강·112 파랑은 보조 문구의 작은 기관 번호에만 적용해 두 긴급 버튼에만 시선이 몰리지 않도록 했습니다. 큰 터치 영역과 각 기능의 기존 연결 흐름은 유지합니다.

[실제 홈 화면 비교](docs/reviews/unslop-v14/index.html) · [변경·검증 기록](docs/UNSLOP_HOME_V14.md) · [v0.14 APK (로컬)](artifacts/LifeLink119-v0.14.0-balanced-home.apk)

## 크게 누르는 차량 UI · v0.13

홈을 119·상담·112·안심콜의 큰 터치 영역 네 개로 바꿨습니다. 일반 버튼은 최소 96×96dp, 28sp로 확대하고 소개·반복 안내는 줄였습니다. 연결 화면에서는 핵심 동작 네 개를 먼저 보여주며, 상세 전달·공유·역할 조작은 다음 화면에서 선택합니다. 낮은 가로 화면은 네 버튼을 한 줄로 배치합니다. 의료정보 잠금·동의·실제 차량 제한은 유지합니다.

[실제 앱 개선 전후](docs/reviews/unslop-v13/index.html) · [설계·검증 기록](docs/UNSLOP_CAR_TOUCH_V13.md) · [v0.13 APK (로컬)](artifacts/LifeLink119-v0.13.0-car-touch-ui.apk)

표준 Android 34의 UI 검증이며, Pleos의 실제 CarUX·차량 SDK·실차 검증 완료를 뜻하지 않습니다. 적용 지침: [unslop](https://github.com/heydamir/unslop) by Damir Sokolovsky (@heydamir), CC BY 4.0.

## 차분한 차량 UI · v0.12

기존 밝은 화면과 119 빨강·112 파랑을 유지하면서 글씨·간격·버튼과 탐색 영역을 정돈했습니다. 홈·상담 선택의 세로 균형을 맞추고, 매뉴얼 목록은 흰색 항목으로 구분했습니다. 기본 버튼은 64dp 이상의 터치 영역과 중앙 정렬을 유지하며, 큰 글씨·좁은 화면과 포커스·비활성 상태를 고려합니다. 의료정보 인증·화면 보호와 실제 차량 제한은 유지합니다.

[실제 앱 화면 개선 전후](docs/reviews/unslop-v12/index.html) · [설계·검증 기록](docs/UNSLOP_UI_V12.md) · [v0.12 APK (로컬)](artifacts/LifeLink119-v0.12.0-unslop-ui.apk)

새 UI는 표준 Android 34 에뮬레이터에서 검증했습니다. Pleos 에뮬레이터는 차량 오디오 서비스 오류로 추가 검증이 남아 있으며, 실제 CarUX·차량 SDK 검증 완료를 뜻하지 않습니다.

적용 지침: [unslop](https://github.com/heydamir/unslop) by Damir Sokolovsky (@heydamir), CC BY 4.0. Kotlin/Android Views와 한국어 차량 UI에 맞춰 적용했습니다.

## 전체 기능 발표자료 · v0.11

[PowerPoint · 41장](docs/presentation/complete-functions-v11/output/LifeLink119_전체기능_UI_소개_v11.pptx) · [PDF](docs/presentation/complete-functions-v11/output/LifeLink119_전체기능_UI_소개_v11.pdf) · [브라우저 보기](docs/presentation/complete-functions-v11/output/LifeLink119_전체기능_UI_소개_v11.html) · [발표 메모](docs/presentation/complete-functions-v11/output/LifeLink119_전체기능_발표메모_v11.md) · [실제 UI 원본 30개](docs/presentation/complete-functions-v11/output/LifeLink119_실제_UI_화면모음_v11.zip)

긴급 요청·상담부터 안심콜·건강정보, 접근성, 주변 차량 협력과 차량 기능까지 화면별로 설명합니다. 설치된 v0.11 앱의 실제 화면 30개를 포함합니다. 개인정보 보호로 캡처가 제한된 화면 7개와 캡처하지 못한 주변 차량 협력 후반 2단계는 설명용 도식으로 구분했습니다. HTML은 내려받아 브라우저로 열고, PPTX·PDF는 발표 또는 열람에 사용합니다. [자료 구성과 재생성 조건](docs/presentation/complete-functions-v11/README.md)

Git에는 소스·테스트·문서·Gradle wrapper를 관리합니다. APK·SDK·AVD·JDK·빌드 캐시·가상환경·`local.properties`·비밀키·로컬 검증 기록은 제외합니다. 아래 과거 기록 중 `verification/`와 `artifacts/` 링크는 원래 개발 PC의 로컬 파일이며 저장소에서 제공하지 않습니다. 새 PC에서는 소스로 APK를 빌드합니다.

내 차가 나와 가족을 위한 응급지원 공간이 되는 아이디어를 체험하는 Pleos Connect Android 앱입니다. 홈에서 **119 구조·구급 바로 요청**, **상담받고 싶어요**, **112 긴급신고 시연**을 구분합니다. 119 구조·구급은 등록이나 인증 없이 한 번에 시연하며, 상담은 119 의료상담과 109 심리상담 중 선택합니다.

## 건강정보 고속도로 가입 동의 예시 · v0.11

`우리 가족 안심콜 → 건강정보 고속도로 가입·연결`에서 가입 동의를 선택하면 가상 투약 내역을 확인하고 동의를 철회할 수 있습니다. 실제 가입·조회·정보 전송은 하지 않습니다. 예시 동의는 앱을 벗어나면 초기화되며 직접 입력한 건강정보와 구분합니다. [기능과 검증 범위](docs/HEALTH_LINK_EXAMPLE_V11.md)

[v0.11 APK (로컬)](artifacts/LifeLink119-v0.11.0-health-link-demo.apk) · [SHA-256 (로컬)](artifacts/LifeLink119-v0.11.0-health-link-demo.apk.sha256)

## 우리 가족 안심콜 · v0.10

홈의 **우리 가족 안심콜**에서 인증 후 본인이 직접 입력한 이름·병력·알레르기·투약 내역을 기기에 암호화해 저장·조회·수정·삭제할 수 있습니다. 기존 가족 예시는 별도이며, 입력한 정보는 119나 가상 상황실에 자동 제공되지 않습니다. 공식 119 안심콜 등록·조회와 건강정보 고속도로 자동 조회는 연결되지 않았습니다. 공모전용 개발 빌드이며 실사용 의료정보 저장 전 별도 보안·운영 검증이 필요합니다. [구현 및 연계 범위](docs/PERSONAL_HEALTH_V10.md)

[안심콜 등록·신고자·수혜자 연결 개선 검토](docs/SAFE_CALL_IDENTITY_REVIEW.md)에는 수혜자/대리자 구분, 등록 전화와 신고 회선의 불일치, 조회 실패와 개인정보 보호를 고려한 개선안을 정리했습니다. 이후 일부 항목을 [v0.18](docs/SAFE_CALL_IMPLEMENTATION_V18.md)에 구현했으며 실제 기관 등록·조회 API는 미연결입니다.

[v0.10 APK (로컬)](artifacts/LifeLink119-v0.10.0-family-health-demo.apk) · [SHA-256 (로컬)](artifacts/LifeLink119-v0.10.0-family-health-demo.apk.sha256)

## 밝은 화면 디자인 · v0.9

밝은 배경, 흰색 카드와 차분한 파란색을 사용하는 화면으로 정리했습니다. 홈에서 119 구조·구급 요청은 붉은색으로 구분하고 상담·112·가족정보는 별도 행동으로 표시합니다. 웹 서비스처럼 보이지만 화면은 Android 앱에서 직접 구현해 기존 인증과 차량 제한을 유지합니다. [화면과 검증 기록](docs/WEB_STYLE_UI_V09.md)

[v0.9 APK (로컬)](artifacts/LifeLink119-v0.9.0-light-ui-demo.apk) · [SHA-256 (로컬)](artifacts/LifeLink119-v0.9.0-light-ui-demo.apk.sha256)

## UI 개선과 영상 안내 · v0.8

119 연결 시연 후 **상황실 영상 안내 보기**에서 합성 영상 사용 동의 → 시작 → 안내 문구 확인 → 중단·동의 철회를 시연할 수 있습니다. 실제 상황실 연결이나 카메라·마이크 사용·영상 전송은 없습니다. 플랫폼이 영상을 제한하면 사용할 수 없고 앱을 벗어나면 미리보기를 중단합니다. [UI 점검과 검증](docs/UX_VIDEO_GUIDANCE_V08.md)

[v0.8 APK (로컬)](artifacts/LifeLink119-v0.8.0-ux-demo.apk) · [SHA-256 (로컬)](artifacts/LifeLink119-v0.8.0-ux-demo.apk.sha256)

## 주행 중 긴급 호출 · v0.7

홈의 **119 구조·구급 바로 요청** 또는 다른 화면 상단의 **119 바로 요청** 한 번으로 정차·등록·PIN·위치 확인 없이 호출을 시연합니다. 위험 종류를 미리 고르지 않고 **차 안의 위급 상황**으로 가상 상황실 전달 → 확인 → 안전 확보 알림 → 인계 → 공유정보 정리까지 진행합니다. 실제 발신·녹음·음성 인식은 없습니다. 음성 요청은 별도의 예시 버튼으로 구분합니다.

[이전 v0.7 APK](artifacts/LifeLink119-v0.7.0-driving-demo.apk) · [SHA-256](artifacts/LifeLink119-v0.7.0-driving-demo.apk.sha256) · [v0.7 검증 기록](verification/v07/validation.json)

[발표 순서·실패 시연·구현 범위](docs/DRIVING_EMERGENCY_DEMO.md)를 참고하세요. 실제 차량 플랫폼 제한은 유지하며 의료정보와 위치·차량 공유는 별도 동의를 따릅니다.

[가족 안전 서비스 작동 흐름 · 10장 슬라이드](docs/presentation/family-safety-20261101/LifeLink119_Family_Safety_Flow.html) · [PowerPoint](docs/presentation/family-safety-20261101/LifeLink119_Family_Safety_Flow.pptx) · [PDF](docs/presentation/family-safety-20261101/LifeLink119_Family_Safety_Flow.pdf)

## 공모전에서 시연할 수 있는 것

- `119 구조·구급 바로 요청`, `112 긴급신고`, `상담받고 싶어요 → 119 의료상담 / 109 심리위기 상담`을 목적별로 구분했습니다.
- 직접 입력한 본인 건강정보는 인증 뒤 기기에 암호화해 보관하고 삭제할 수 있습니다. 기존 가상 가족 예시의 항목별 공유·철회는 앱 내 가상 상황실에서 별도 시연합니다.
- 어린이·고령자·임산부·장애 접근성·심리위기·교통사고 등 **S01~S14 + E01~E04 + D01 총 19개**를 JSON 갤러리에서 실행합니다. 상황별 기본 행동은 최대 세 개입니다.
- 문자/수어/전·후방·스마트폰 합성 영상, 가상 위치·차량, 전문가 안내와 사용자 확인을 거치는 모의 목적지, 경찰 요청·검토·가상 승인/거절, 안심콜·디지털키 모형을 연결했습니다.
- 검토·수정 가능한 전달 요약, 가상 혈당 참고 입력, EV/V2L 가정 계산과 비지원 fallback을 제공합니다.
- 기존 Vehicle SDK 2.3.2 도어·배터리 진단, 기본 매뉴얼, 주변 차량 협력·승인형 화면 표시를 더보기에서 유지합니다.
- 실제 CarUxRestrictions 상태에 따라 영상·상세 입력·긴 목록을 제한합니다. 수동 이동/정차 선택은 실제 제한을 해제하지 않습니다.
- 주변 차량 협력도 앱 안에서 오프라인으로 시연합니다. 별도 동의 → 초대·자발적 수락 → 300초 임무 → 119/112 역할의 별도 승인 → 최대120초 화면 표시 → 철회·구급대 인계를 연결했습니다.
- **의료정보 잠금이 기본**입니다. 제공되는 차량 Android 화면 잠금 인증을 사용하고, 없으면 처음 접근할 때 앱 PIN을 직접 설정합니다. 인증 전 이름·의료정보를 숨기고, 60초·홈 복귀·앱 전환 시 잠급니다. 저장 프로필은 암호화하며 민감 화면 캡처를 제한합니다.

대표 경로:

1. 홈 → 119 구조·구급 바로 요청 또는 112 긴급신고 시연. 프로필 없이 시작합니다.
2. 연결 후 사람 선택 → 필요한 정보에 동의 → 가상 상황실에서 확인 → 종료·공유정보 정리.
3. 더보기 → 설정·시연 도구 → 시나리오 갤러리 → S01/S08/S14 등 선택.
4. 더보기 → 차량 안전 준비 → EV/V2L 계산. 기존 기능은 설정·시연 도구에 있습니다.

[현재 구현·검증 보고서](docs/IMPLEMENTATION_REPORT_V07.md) · [기능별 시연 체크리스트](docs/CONTEST_DEMO_CHECKLIST.md) · [의료정보 보호](docs/MEDICAL_PRIVACY.md)

의료정보를 처음 열 때 PIN을 직접 설정하세요. PIN을 채팅으로 보낼 필요는 없습니다. 자동 검사에서 사용한 임시 PIN은 검사 종료 후 제거합니다. 잠금 중에도 긴급 연결 시연을 시작할 수 있습니다.

## 실행·개발

Kotlin Views / 단일 Activity / minSdk 28 / compile·target 34 / JDK 17을 사용합니다. 처음에는 [새 PC 실행 가이드](docs/OTHER_PC_GUIDE.md)에 따라 Python 가상환경, JAVA_HOME, ANDROID_HOME과 공식 Pleos AVD를 준비합니다. 빌드에는 Build Tools 35.0.0도 사용합니다. 프로젝트 폴더에서 다음 명령을 실행합니다.

```powershell
git clone https://github.com/airwrap/LifeLink119.git
cd LifeLink119
# 새 PC 가이드의 환경 설정 후
.\Build-And-Test.cmd
.\Run-Full-Demo.cmd
python -m unittest discover -s console -p 'test_*.py' -v
```

GitHub Actions는 Python 테스트·계약 검사와 Android debug 빌드·JVM 단위 테스트를 실행합니다. Pleos 화면·시스템 인증·실차 검증은 별도입니다. 현재 미해결 개선 항목은 위 검토 목록을 확인합니다.

새 18개 시나리오는 **인터넷·PC 서버 없이** 앱 안에서 끝까지 시연합니다. PC 보조 화면은 `Start-Local-Demo.cmd`와 `Connect-Local-Console.cmd` 실행 후 <http://127.0.0.1:8781/experience-console>. 앱 내 상황실에서 별도 동의해 수동 게시한 **상태와 항목 이름만** 표시합니다. 의료내용·연락처·실제 위치·영상은 전송하지 않습니다. 기존 웹 상황실/협력 PoC도 그대로 제공합니다.

실제 자동차 없이도 `Start-Virtual-Console.cmd`로 Pleos 에뮬레이터의 P/D·가상 속도를 바꿔 실제 CarUX 제한 전환을 검사할 수 있습니다. 검사 뒤 P/0으로 복귀합니다. [시연 대본](docs/DEMO_SCRIPT.md)에 의료정보 인증과 가상 주행 순서를 정리했습니다.

종료: 앱 홈/에뮬레이터 홈, 서버는 `Stop-Local-Demo.cmd`, 에뮬레이터는 창의 X. 종료한 모의 세션의 공유값은 정리하며 저장된 가상 프로필은 가족정보에서 따로 삭제합니다.

## 범위와 검증

[전체 기능 매트릭스](docs/FEATURE_MATRIX.md), [UX 사양](docs/UX_SPEC.md), [SDK 확인·미연계](docs/SDK_GAPS.md), [확장 전 감사](docs/FEATURE_AUDIT.md)에 근거와 제한을 구분했습니다. 새 빌드·테스트·네이티브 화면 검사 결과는 [현재 구현 보고서](docs/IMPLEMENTATION_REPORT.md)를 기준으로 합니다.

v0.7 검증: Android 단위 **143개**, Python **68개**, lint **오류0·경고0**, 주행 중 긴급 호출 네이티브 화면 **11항목 통과**. 실제 발신·기관 연결은 모의 시연이다. [v0.7 보고서](docs/IMPLEMENTATION_REPORT_V07.md)

v0.6 당시 검증: Android 단위 **129개**, Python **68개**, lint **오류0·경고0**. 의료정보 보호 **11항목**, 공모전 **16항목·18개 사례**, 대표 회귀 **13항목**, 복구 **5항목**을 통과했습니다. 공식 콘솔의 가상 주행과 실제 CarUX 콜백 **0→80→0**, PC 게시·철회, 도어·배터리 SDK 원본 조회도 확인했습니다. 검사·설치·배포 APK 해시는 일치하며 임시 검사 PIN은 제거했습니다. [최종 근거 JSON](verification/v06/artifact-report.json)

v0.6 검증 당시 소프트웨어 에뮬레이터에서 앱 PIN 설정·확인은 약26~27초가 걸립니다. 확인 중 취소·긴급 연결 복귀는 가능합니다. 기본 시스템 인증이 등록되지 않은 AVD여서 앱 PIN 경로를 네이티브로 검증했으며, OEM 시스템 인증 화면과 실기기 속도는 별도 확인 대상입니다.

실제 119/112/109 통화·문자, GPS/카메라/마이크 수집, 안심콜·수어·경찰·디지털키·실제 내비·차량 등화/V2L 제어는 연결하지 않았습니다. 외부 기관 연계, 실차 주행, TalkBack 전체 품질, OEM 앱 심사는 별도 검증과 협력이 필요합니다. 이전 HTML/PPTX는 과거 버전 소개 자료이며 현재 기능은 위 문서를 따릅니다.

## 이전 버전 기록

아래 실행 경로·검증 수치는 각 당시 버전의 기록입니다. 현재 새 홈에서 기존 5개 흐름·매뉴얼·협력 기능은 `더보기 → 설정·시연 도구`로 진입합니다.

## v0.4.1 · 이동 중 응급상황 시연

정차 확인을 시작 조건에서 제거했습니다. 이동 상태 미선택·이동 중·정차 상황 모두 응급상담, 매뉴얼, 주변 차량 도움 요청과 승인된 비상표시를 사용할 수 있습니다. 이동 상태는 수동 시나리오 선택이며, 상세 조작은 동승자·관제 역할로 시연합니다.

119·112 역할의 표시 승인, 120초 만료, 임무·차량 일치, 철회·인계·연결 실패·백그라운드 중단 조건은 유지합니다. 실제 속도 감지·기관 연결·차량 등화 제어는 미연동입니다.

[새 v0.4.1 APK](artifacts/LifeLink119-v0.4.1-demo-debug.apk) · [변경·검증 기록](docs/IMPLEMENTATION_REPORT_V041.md)

검증: Android 단위 36개, 서버 51개, 웹 8개, 기본 앱 화면 19개 및 협력 차량의 변경 동작 5개를 확인했습니다. 협력 차량의 추가 전체 자동 회귀는 앱 복귀 후 에뮬레이터 접근성 연결 오류로 일부 미완료입니다. [최종 검증 요약](verification/v041-artifact-report.json)에 통과 범위와 제한을 구분했습니다.

재현: `python scripts/smoke-moving-web.py`, `python scripts/smoke-v04-cooperation.py --serial emulator-5580 --expected-apk-version 0.4.1-demo --output-prefix v041 --moving-demo`, `python scripts/smoke-v03.py --serial emulator-5580 --expected-version 0.4.1 --expected-apk-version 0.4.1-demo --output-prefix v041-regression --moving-demo --persistent-ui`.

## v0.4 · 옆 차가 첫 도움으로

가까운 차량이 자발적으로 참여하고 기관 조율을 거쳐 잠시 응급협력 역할을 맡는 **주변 차량 협력 PoC**를 추가했습니다. 구급대가 도착하기 전 첫 도움을 연결한다는 아이디어를 Android와 웹에서 직접 실험합니다.

- **연결되는 흐름**: 동의 후 요청 → 119 역할의 상황 확인 → 조건에 맞는 가상 차량 초대 → 자발적 수락·거절 → 300초 임무 부여 → 별도 119·112 역할의 최대 120초 표시 승인 → 현장 지원 → 구급대 인계.
- **비상차량 표시 모형**: 선택한 차량·임무·승인 회차에 결합한 화면 점멸입니다. 인계·철회·만료 시 역할과 표시가 종료됩니다. 연결 장애·화면 비활성에서는 표시를 끕니다. 이동 상태 선택으로는 표시를 끄지 않습니다.
- **대조 조건 3개**: 협력 차량이 먼저 돕는 경우, 구급대가 더 빠른 경우, 참여 차량이 없는 경우. 가장 가까워도 동의·교육 조건이 맞지 않으면 초대할 수 없습니다.
- **결과 구분**: 고정 도착 가정과 실제 화면 조작 경과를 따로 기록하고 JSON으로 내보냅니다. 예시의 4분 45초는 가정 비교이며 실제 대응시간·생존 효과의 측정값이 아닙니다.

웹은 <http://127.0.0.1:8781/cooperation>, Android는 홈의 **옆 차가 첫 도움으로 · 응급협력 차량 PoC**에서 시작합니다. Android의 POC 실험 ID를 PC 웹의 목록에서 선택하면 기관 역할을 함께 시연할 수 있습니다. `새로고침`으로 목록을 갱신합니다. 기관 역할은 인증 없는 localhost 연습이며 실제 기관 승인·차량 등화·환자 이송을 연결하지 않습니다.

[PoC 목적·실험 조건·다음 단계 계획](docs/POC_RESPONSE_NETWORK.md)에 실내 모형·마네킹, 폐쇄 시험장, 공식 실증 검토 순서와 측정 기준을 정리했습니다. 기존 HTML·PPTX 소개 자료는 v0.2 기준을 유지합니다.

[새 v0.4 debug APK](artifacts/LifeLink119-v0.4.0-demo-debug.apk) · [SHA-256](artifacts/LifeLink119-v0.4.0-demo-debug.apk.sha256) · [구현·검증 기록](docs/IMPLEMENTATION_REPORT_V04.md)

**v0.4 당시 검증** (정차 제한 제거 전의 기록): debug·release 빌드, Android 단위 테스트 **38개**, 서버 테스트 **50개**, lint **오류 0 / 경고 0**을 통과했습니다. Pleos API 34 AVD에서는 신규 협력 흐름 **15개**와 기존 기능 **21개**를 확인했습니다. 새 표시와 기존 표시의 실제 120초 만료, 통신 장애·복구, 앱 전환·주행 시연 제한, 인계·철회, SDK 도어·배터리 조회를 포함합니다. 동일 APK에서 완료한 일부 검사 기록을 재사용했으며 [최종 검증 요약](verification/v04-artifact-report.json)과 연결된 원본 기록에 근거를 남겼습니다.

이전 서버가 실행 중이면 `Stop-Local-Demo.cmd` → `Start-Local-Demo.cmd`로 갱신합니다. 종료하면 메모리의 모의 기록은 사라집니다.

## v0.3 추가 기능

- **기본 응급상황 매뉴얼**: 먼저 할 일, 성인 CPR·AED, 출혈, 열화상, 경련의 6개 항목을 Android APK에 넣었습니다. 인터넷 없이 읽으며 적용 대상·주의사항·공식 출처·확인일을 표시합니다. 의료진 검토 전 학습용 요약이며 실제 119 지시가 우선합니다.
- **119·112 승인형 비상 표시 시연**: 탑승자 요청 후 상담원 웹의 119 또는 112 역할이 비상상황 확인과 120초 승인을 순서대로 진행해야 화면 점멸을 시작할 수 있습니다. 만료·철회·종료 시 중단합니다.
- **실제 차량 등화는 미연결**: 인증 없는 localhost 역할 연습으로 실제 기관 승인이 아닙니다. 기존 빨간 제동등을 변경하지 않으며 색상·점멸 규격은 미확정입니다. 승인 조건 외에도 법령·기관·제조사 검토가 필요합니다.

자세한 절차·출처·규정 검토는 [응급 매뉴얼과 승인형 표시 안내](docs/EMERGENCY_MANUAL_AND_SIGNAL.md)를 참고하세요. 기존 HTML·PPTX는 v0.2 기준 소개 자료입니다.

소스를 갱신한 뒤 v0.2 로컬 서버가 이미 실행 중이면 `Stop-Local-Demo.cmd`로 종료하고 `Start-Local-Demo.cmd`로 다시 시작해야 새 API와 매뉴얼을 사용할 수 있습니다. 브라우저 검증은 별도 포트 **8783**에서 수행했습니다. Android 연결은 HTTP Host 검사에 맞게 기기와 PC 모두 **8781**을 사용해야 합니다(`adb reverse tcp:8781 tcp:8781`).

## 프로젝트를 처음 본다면

팀명과 앱명은 **LifeLink 119**입니다. 아이·고령자 등 가족과 이동하는 탑승자가 이동 중에도 필요한 도움을 고르고, 선택한 정보로 전문인력과 소통하는 경험을 목표로 합니다. 현재 버전은 그 절차를 가상 데이터로 연습하는 프로토타입입니다.

- [HTML 프로젝트 소개](docs/presentation/LifeLink119_Introduction.html): 기능별 설명, 단계별 실제 화면, 구현 범위와 체험 방법. 파일을 내려받아 브라우저로 열면 인터넷·서버 없이 읽을 수 있습니다.
- [발표용 PPTX](docs/presentation/LifeLink119_Introduction.pptx): 같은 내용을 발표하기 쉽게 정리한 11장 자료입니다. 제목·본문·기능 표를 편집할 수 있습니다.
- [다른 PC에서 시작하기](docs/OTHER_PC_GUIDE.md): 웹만 체험하는 경우와 Pleos Android 개발 환경을 준비하는 경우를 구분합니다.

HTML의 그림은 파일 안에 포함되어 있습니다. HTML 안의 PPTX 다운로드 링크도 사용하려면 두 파일을 같은 폴더에 함께 옮기세요. 소개 자료는 **v0.2.0-demo / 2026-10-25** 구현·검증 기록을 기준으로 작성했습니다.

## 구성과 사용 흐름

```text
탑승자: Android 앱 또는 웹
          ↕ 같은 PC 안의 가상 정보·진행 상태
로컬 서버: Python / 127.0.0.1:8781
          ↕ 모의 접수·검토·안내
상담원: 브라우저
```

1. 앱·웹에서 바로 5개 흐름 중 상황과 고정 가상 사례를 고릅니다. 이동 상태를 선택하지 않아도 진행할 수 있습니다.
2. 가상 차량·위치·합성 영상 예시를 선택합니다. 모든 공유 항목은 기본 OFF입니다.
3. 앱 내부 가상 세션을 생성한 뒤, 별도 버튼으로 로컬 모의 상담센터에 공유합니다.
4. 상담원이 접수·검토·가상 안내를 진행하면 탑승자 화면에도 상태가 반영됩니다.
5. 공유를 철회하거나 모의 세션을 종료합니다. 철회하면 공유 정보와 가상 경로를 제거합니다.

Android debug 앱은 ADB reverse로 PC의 로컬 서버에 연결합니다. 실제 SDK 조회 결과와 모의 상담 데이터는 분리합니다.

## 바로 실행

### Android 없이 웹으로 체험

Python 3.10 이상이 있는 PC에서 실행합니다.

```powershell
python -m pip install -r console/requirements.txt
.\Start-Local-Demo.cmd
```

- 상담원: http://127.0.0.1:8781/
- 탑승자: http://127.0.0.1:8781/passenger
- 기본 매뉴얼: http://127.0.0.1:8781/manual
- 주변 차량 응급협력 PoC: http://127.0.0.1:8781/cooperation
- 종료: `Stop-Local-Demo.cmd`. 메모리의 가짜 세션은 삭제됩니다.
- Windows 이외 환경: `python console/server.py`, 종료는 Ctrl+C.

원격 개발 PC에서도 Android 환경 없이 위 두 화면을 통해 시나리오를 개발·검증할 수 있습니다. 서버는 해당 PC의 localhost에만 바인딩하며 외부 접속 서버로 공개하지 않습니다.

### Pleos 앱과 웹을 함께 실행

이 PC는 Android Studio, JDK 17, API 34 SDK, 공식 Pleos 이미지와 `Pleos_Connect_API_34` AVD를 준비했습니다.

1. `Run-Full-Demo.cmd`: 웹 서버 실행 → Pleos AVD 부팅 확인 → ADB localhost 터널 → debug APK 설치 → 앱 실행.
2. 앱을 열면 바로 응급지원 기능을 사용할 수 있습니다. **이동 중 상황 시연**·**정차 상황 시연**은 상황 선택이며 잠금 조건이 아닙니다. 실제 차량 상태 감지는 미연동입니다.
3. 상황 선택 → 가상 상황 선택 → 정보 확인 → 앱 내부 가상 세션을 생성합니다.
4. **로컬 모의 상담센터에 공유**를 명시적으로 누릅니다.
5. 상담원 웹에서 **모의 접수 → 검토 시작 → 가상 안내 표시**를 누릅니다. 앱에도 상태가 반영됩니다.
6. 공유 철회와 모의 종료를 시험합니다.

수동 실행은 `Start-Pleos-Emulator.cmd`, `Connect-Local-Console.cmd emulator-5580`, Android Studio Run 순서로 진행할 수 있습니다. 에뮬레이터를 닫으면 ADB 터널은 다시 연결해야 합니다.

앱만 닫으려면 홈에서 뒤로가기, 에뮬레이터는 창의 X, 웹 서버는 `Stop-Local-Demo.cmd`로 종료합니다. 서버 종료 스크립트는 자신이 시작한 프로세스만 확인해 종료합니다.

## 구현 범위

| 기능 | v0.4 상태 |
|---|---|
| 119 응급의료상담 | 가상 상황 2종, 3단계 흐름, 로컬 모의 상담원 상태 연동 |
| 현장 영상 공유 | 동의 후 합성 도형 애니메이션 예시 표시. 실제 카메라 미연결 |
| 이송·구조 지원 | 검토·안내 절차 시연, 가상 만남 지점과 격자 경로 |
| 112 경찰 협조 | 모의 공동대응 검토 요청. 출동·호송 승인을 하지 않음 |
| 109 심리위기 상담 | 개인정보 입력 없는 전문상담 요청 흐름. 위험도 판정 없음 |
| 공유 제어 | 차량·위치·합성 영상 기본 OFF, 명시적 로컬 공유, 공유 철회 시 경로까지 제거 |
| 연결 복구 | 타임아웃·재시도·중복 생성 방지, 서버 재시작 시 이전 ID 오인 방지 |
| 이동 중 응급지원 | 미확인·이동 중·정차 상황 모두 상담·매뉴얼·승인 표시 사용 가능. 이동 상태는 시연 문맥이며 실제 속도 감지 미연동 |
| 차량 SDK | 실제 도어·배터리 원본 값 조회. 모의 상담 payload와 분리 |
| 상담센터 | localhost 서버·상담원 화면·탑승자 화면 구현, 최대 50개/60분 메모리 보관 |
| 기본 매뉴얼 | 6개 학습용 요약, 앱 오프라인 열람과 같은 원본의 웹 화면. 의료진 검토 전 |
| 승인형 비상 표시 | 119·112 역할의 확인·한시 승인 후 화면 시연. 실제 기관 인증·차량 등화 미연결 |
| 주변 차량 응급협력 | 적합한 차량 초대·자발적 수락·기관 역할 조율·현장 지원·인계, 대조 조건과 실험 기록 내보내기 |

## 개발 환경과 검증

- minSdk 28 / compileSdk·targetSdk 34 / JDK 17 / Gradle 8.10.2 / Kotlin 2.0.21
- 공식 Vehicle SDK `ai.pleos.playground:Vehicle:2.3.2`와 Pleos Maven 저장소 유지
- JDK·SDK·Gradle 캐시 경로는 각 개발자 환경을 사용. 경로 설정은 [새 PC 가이드](docs/OTHER_PC_GUIDE.md) 참고
- `Build-And-Test.cmd` 또는 JDK 17 환경의 `gradlew.bat :app:assembleDebug :app:testDebugUnitTest`
- 서버: `python -m unittest discover -s console -p 'test_*.py' -v`
- 계약: `python scripts/validate-demo-contract.py`
- APK·AVD 검증: `python scripts/smoke-v03.py --serial emulator-5580 --expected-version 0.4.1 --expected-apk-version 0.4.1-demo --output-prefix v041-regression --moving-demo --persistent-ui` (전용 Pleos AVD, 프로젝트 런처의 8781 포트, Python Pillow·psutil 필요)

v0.4에서 기존 기능 회귀 검사는 `python scripts/smoke-v03.py --serial emulator-5580 --expected-version 0.4.0 --expected-apk-version 0.4.0-demo --output-prefix v04-regression --expiry-check --race-check --persistent-ui`를 사용합니다. 경합 검사용 서버는 `scripts/local-demo.ps1 -Action start -ReadDelay 1`로 시작합니다. 새 협력 흐름은 `python scripts/smoke-v04-cooperation.py --serial emulator-5580`으로 검사합니다. 두 AVD 검사는 같은 기기를 조작하므로 순서대로 실행합니다. 검사 후 서버는 응답 지연 없는 기본 설정으로 복원합니다.

AVD 자동 검증 전에는 JDK 17 환경에서 `python scripts/prepare-ui-snapshot.py --serial emulator-5580`을 실행합니다. 이 검증용 APK는 자기 패키지의 instrumentation으로 화면 구조를 읽으므로 대상 앱을 재시작하거나 점멸 애니메이션을 끄지 않습니다. Android 앱 자체에는 검증 코드나 권한을 추가하지 않습니다.

Android 단위 테스트·서버 테스트·실제 AVD/브라우저 결과, 변경 파일, 개선 이력은 [V0.2 구현 보고서](docs/IMPLEMENTATION_REPORT_V02.md)에 기록합니다. [작업계획](docs/WORK_PLAN_V02.md)과 [SDK 연동 가이드](docs/SDK_INTEGRATION.md)도 참고하세요.

**v0.2** 검증 기록은 Android 단위 테스트 **25개**, 서버 테스트 **19개**, lint **오류 0 / 경고 0**입니다. Pleos AVD와 브라우저에서 5개 흐름, 공유 철회, 종료와 연결 재시도를 확인했습니다. 근거는 [검증 요약 JSON](verification/v02-artifact-report.json)과 구현 보고서에 있습니다. 실차 안전 인증·전체 접근성·공식 기관 연동·사용자 효과 측정은 별도 과제입니다.

**v0.3 / 2026-10-25 최종 검증**: debug/release 빌드, Android 단위 테스트 **32개**, 서버 테스트 **29개**, lint **오류 0 / 경고 0**을 통과했습니다. Pleos API 34 AVD에 최종 debug APK를 설치해 **화면 검사 21개**를 통과했습니다. 오프라인 매뉴얼 6개, 상담 시나리오 5개, 기본 동의 OFF·명시적 공유·철회·종료, 119·112 역할 승인과 실제 화면 점멸, 주행 제한, 서버 응답 중단·복구, 다중 창 앱 전환, 실제 120초 승인 만료를 확인했습니다. SDK 조회 결과는 운전석 도어 **READ 0 / 범위 0~1**, 배터리 **READ 100 / SDK 원본 값**입니다. APK의 서명·매뉴얼 포함·권한도 검사했고 설치본과 전달본의 SHA-256이 일치합니다.

검증 중 발견한 **자동 갱신 도중 화면 복귀 시 버튼이 잠기는 문제**와 **다중 창에서 활성 앱을 벗어나도 표시가 유지되는 문제**를 수정했습니다. 서버 응답을 1초 늦춘 복귀 시험과 실제 다른 앱으로 전환하는 시험으로 재확인했습니다. 점멸 화면은 애니메이션을 유지한 채 접근성 API로 검사했으며 검증용 보조 APK는 제거했습니다.

- [최종 debug APK](artifacts/LifeLink119-v0.3.0-demo-debug.apk) · [SHA-256](artifacts/LifeLink119-v0.3.0-demo-debug.apk.sha256)
- [최종 검증 요약](verification/v03-artifact-report.json) · [AVD 화면 검사 21개 상세](verification/v03-android-e2e.json)
- [홈 화면](verification/v03-android-home-final.png) · [오프라인 매뉴얼](verification/v03-android-manual-cpr.png) · [승인 만료 화면](verification/v03-android-signal-expired.png)

자동 갱신 경합 재현은 전용 데모 서버를 `scripts/local-demo.ps1 -Action start -ReadDelay 1`로 시작한 뒤 `smoke-v03.py`에 `--race-check`를 추가합니다. 이미 실행 중인 서버는 종료 후 다시 시작해야 지연 설정이 적용됩니다. 연결 실패 시험은 프로젝트 런처가 기록한 서버만 잠시 정지시키고 최대 30초 또는 검증 프로세스 종료 시 자동 복구합니다. 최종 검증 후 서버는 응답 지연 없는 기본 설정으로 복원했습니다. 실제 기관 연결·차량 등화 제어·의료진 검토는 완료된 기능에 포함되지 않습니다.

## 파일 안내

| 경로 | 역할 |
|---|---|
| `app/src/main/java/com/lifelink119/app/` | Kotlin 앱. `core`는 정책, `data`는 mock/adapter, `vehicle`은 공식 SDK, `MainActivity`는 화면 |
| `console/` | Python 로컬 서버, 탑승자·상담원 웹 화면과 서버 테스트 |
| `docs/presentation/` | HTML 소개와 편집 가능한 발표용 PPTX |
| `docs/` | 구현 보고서, SDK 안내, 계약 스키마·가상 예시, 다른 PC 안내 |
| `scripts/` | 실행·빌드·연결·시연 검증 스크립트 |
| `verification/` | 로컬 화면·테스트 기록. Git 제외; 현재 협업용 개선 목록은 `docs/reviews/20261030/` |
| `AGENTS.md`, `docs/CODEX_PROMPTS.md` | 다음 개발자가 먼저 읽을 작업 지침 |

## 다음 개발 과제

- 가족 탑승자·상담원 관점의 사용성 평가: 흐름 완료, 동의 이해와 오류 복구를 측정합니다. 아직 실사용 지표는 없습니다.
- 기관·제조사와 공식 연계 가능성을 확인하고 지원 API·운영 절차·데이터 처리 책임을 정합니다.
- 승인된 범위에서 실제 영상·위치·주행 상태·NaviHelper 연동과 실차·접근성 검증을 진행합니다.

이는 향후 과제이며, 확정된 기관 연계나 완료된 서비스 운영을 뜻하지 않습니다. 상세 수락 기준은 [개발 단계](docs/ROADMAP.md)를 참고하세요.

## 중요한 범위 구분

- localhost 연결용 INTERNET 권한과 평문 loopback 예외는 **debug에만** 있습니다. release는 로컬 공유 버튼을 비활성화하고 인터넷 권한을 포함하지 않습니다.
- 서버에는 외부 HTTP 클라이언트·기관 어댑터가 없으며, 실제 전화번호·증상·VIN·GPS·외부 영상 URL 입력은 거부합니다.
- 실제 차량 속도/기어 감지에 따른 자동 제한, 실차 안전 인증, 실제 카메라·기관·NaviHelper 연동은 별도 과제입니다. 현재 제한과 경로는 명시적인 시뮬레이션입니다.
- 계정·실제 가족 정보·환자 정보·실제 위치를 입력하지 마세요. 모든 예시는 고정된 가짜 데이터입니다.
- 다른 PC로 옮길 때는 최신 Transfer ZIP의 `00_README_OTHER_PC_KO.md`부터 확인하세요. IDE/JDK/SDK/AVD 설치물은 프로젝트 ZIP에 포함되지 않습니다.
- 기존 Transfer ZIP은 생성 당시의 스냅샷입니다. 이번에 추가한 HTML·PPTX와 갱신된 README가 필요하면 현재 프로젝트의 해당 파일을 별도로 함께 옮기세요.
