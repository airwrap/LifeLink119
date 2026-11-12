# LifeLink 119 · unslop UI 개선 v0.12

## 방향과 범위

- 제품: 나와 가족을 위한 차량 응급지원
- 인상: 차분함, 명확함, 따뜻함
- 사용 환경: 짧게 보고 바로 누르는 자동차 조작 패널
- 강조할 곳: 긴급 요청과 현재 연결 상태
- 일관된 표식: 119 빨강, 112 파랑

사용자가 요청한 UI 개선과 기존의 자율 점검·개선 지시에 따라 방향을 공개하고 구현한다. `unslop`의 **polish mode**를 적용한다. 기존 밝은 색상, 기능과 화면 순서, 버튼 중앙 정렬을 유지한다. 차량 화면이므로 웹용 32~48px 컨트롤 수치는 적용하지 않고 64dp 이상의 터치 영역과 큰 한국어 글씨를 유지한다. 개인·의료정보 인증과 `FLAG_SECURE`, 실제 CarUX 제한, 공유 동의, 실제 기관 연결이 아닌 모의 기능 구분은 디자인 변경 대상이 아니다.

## 변경 전 점검

실행 중인 v0.11 앱의 실제 Android View를 별도 네이티브 검증 패키지로 캡처했다. 정적 HTML 재현 화면이 아니다. 보호 화면은 캡처하지 않는다. 원본은 `verification/unslop-v12/before/`에 보관한다.

| 관찰 | 위치 | 조치 |
|---|---|---|
| 홈과 상담 선택 내용이 위에 몰려 아래 빈 공간이 큼 | `ExpansionUi.shell`, 홈·상담 | 짧은 선택 화면의 내용 블록을 사용 가능한 높이의 가운데에 배치 |
| 14·18·22·26dp 등 간격이 혼재 | 두 화면 구현의 공통 `add`·패널 | 공통 4dp 간격 체계, 패널 24dp |
| 굵은 제목이 많아 시각적 무게가 큼 | `text`, `label`, 홈 카드 | 시스템 한국어 폰트 유지, 보통 400·강조 500 |
| 버튼과 카드의 모서리·높이가 불일치 | 공통 버튼·패널 | 버튼 12dp, 패널 16dp, 기본 터치 높이 64dp |
| 상단 버튼이 붙고 중간 너비에서 로고가 눌릴 가능성 | 확장 화면 상단 | 버튼 간 12dp, 큰 글씨·좁은 화면에서 탐색 행 분리 |
| 매뉴얼에서 거의 모든 항목이 파란 채움 버튼 | 기존 도구 공통 버튼 | 목록·뒤로·홈은 흰 표면, 주요 진행 동작만 색상 강조 |
| 정상 응답 상태도 경고 갈색 | 연결 상태 카드 | 정상 진행 파랑, 실패 갈색, 종료 중립색 |
| 색이 있는 버튼의 파란 포커스 테두리 대비가 낮음 | 공통 StateListDrawable | 채움 버튼은 흰 포커스선, 흰 버튼은 파란 포커스선 |
| 홈 소개와 안내가 중복·장문 | 홈·상담 선택 | 기존 사실을 유지하면서 짧게 정리 |
| 두 구현에서 스타일 값이 중복됨 | `ExpansionUi`, `MainActivity` | 네이티브 `LifeLinkUiStyle` 토큰 공유 |

보라 그라데이션·발광·이모지·광고성 수치 등은 관찰되지 않았다. 없던 문제를 만든 뒤 제거했다고 주장하지 않는다. 생성 이미지나 장식용 도형, 진입 애니메이션은 추가하지 않는다.

## 아홉 단계 적용

1. 간격: 공통 4dp 간격, 일관된 패딩과 행 간격
2. 색상: 기존 밝은 바탕과 의미 있는 119·112 색을 유지하고 도구 목록의 과도한 채움 감소
3. 아이콘: 장식 이모지·아이콘을 새로 추가하지 않음, 기존 문구의 불필요한 화살표만 정리
4. 문구: 홈 안내를 짧게 정돈하고 필수 모의 연결 안내는 유지
5. 글씨: 시스템 한글 폰트, 400·500 두 굵기, 숫자 폭 정렬
6. 구성: 홈·상담 선택의 균형, 본문 폭과 상단 탐색의 일관성
7. 세부: 모서리, 터치 영역, 큰 글씨, 누름·포커스 상태
8. 시각 자료: 실제 앱 내용이 중심이며 새 이미지 생성 없음
9. 움직임: 불필요한 움직임 없음, 버튼 표면 전환만 100~120ms

## 검증

| 검사 | 결과 | 근거 |
|---|---|---|
| debug APK 빌드 | 통과, `0.12.0-demo` / versionCode 13 | `:app:assembleDebug` |
| Android 단위 테스트 | **148개 통과**, 실패·오류·제외 0 | `:app:testDebugUnitTest` |
| Android lint | **오류 0·경고 0** | `:app:lintDebug` |
| 전달 계약 검사 | 통과 | `scripts/validate-demo-contract.py` |
| 주요 네이티브 동작 | **8항목 통과** | `scripts/verify-unslop-ui.py` |
| 실제 앱 화면 | **17개 검사** | `verification/unslop-v12/after/` |
| 실제 화면 터치 | 홈 119 버튼 → 모의 접수·가상 상담원 응답 확인, 홈 복귀 | `verification/unslop-v12/physical-tap.json` |
| 비교 자료 브라우저 검사 | 1440·390px, 5개 화면 쌍·이미지 10개 로드, 가로 넘침·페이지 오류 0 | `verification/unslop-v12/browser-review.json` |
| 실행 APK 일치 | 설치본·빌드본 SHA-256 일치 | `after/results.json`, `build-summary.json` |

APK SHA-256: `a46b2b0dd302e3ceabb44846c9cffa7bc871c02bcea63b732ed533f7bc25cd74`

네이티브 검사는 일반 화면, 앱의 큰 글씨 설정, 520dp 너비 미리보기에서 보이는 텍스트의 내부 잘림·생략과 버튼의 중앙 정렬·64dp 이상 터치 높이·글꼴 굵기를 확인했다. 119 빨강·112 파랑과 포커스 상태도 실제 View에서 확인했다. 119 빠른 요청·중복 방지, 112·109, 119 상담, 합성 영상의 요청·동의·시작·철회, 의료 인증 전 화면 보호와 실제 취소 버튼, 모의 추가 제한에서 119 유지·영상 비활성, 오프라인 매뉴얼 진입을 검사했다. 큰 글씨 설정은 검사 전 값으로 되돌렸다. PIN을 입력·설정하거나 의료정보를 생성·삭제하지 않았다. 보호 화면은 캡처하지 않았으며, 캡처 요청이 거부되는 것을 확인했다.

설치 APK와 해당 빌드의 소스 SHA를 확인한 뒤 [개선 전후 비교 페이지](reviews/unslop-v12/index.html)를 만들었다. 캡처는 실제 실행 중인 Android View를 그린 것으로, HTML로 앱 화면을 재현한 이미지가 아니다. 세션 식별값은 실행마다 달라지는 가상 값이다.

### 플랫폼 검증의 한계

- 개선 전 캡처: 기존 `LifeLink119_Verify_20261101`, 공식 Pleos API 34 이미지.
- 개선 후 UI 검사: 별도 `LifeLink119_Android34_UI_20261107`, 표준 Android 34 Google APIs 이미지. 동일 APK의 기존 **비차량 모의 모드**를 사용했다. 차량 제한을 해제하는 코드를 추가하지 않았다.
- Pleos AVD에서 차량 서비스의 `registerAudioPolicy failed -1`, 오디오 믹서 연결 `error: -6`, 시스템 재시작을 관찰했다. 별도 공식 Pleos 프로필의 신규 AVD에서도 **앱 설치 전** 문제가 재현됐다. 오디오를 비활성화해도 해결되지 않았다. 이 실행에서 Pleos의 새 UI·실제 CarUX 콜백·차량 SDK 동작 검증은 **완료하지 못했다**. 호스트 GPU나 앱을 원인으로 단정하지 않는다.
- 기존 AVD는 삭제·초기화하지 않았다. 기존 의료정보·PIN을 새 검증 AVD로 옮기지 않았다. 추가한 AVD들은 `verification/unslop-v12/` 아래에 있으며 등록 이름으로 구분한다.
- 표준 Android의 에뮬레이터 오디오는 꺼 두었다. 실제 발신·상황실·차량·오디오 출력·시스템 자격 인증 성공·사람이 수행하는 TalkBack/로터리 조작 검증은 포함하지 않는다. 17개 화면 검사를 모든 19개 시나리오나 의료정보 화면의 시각 검사로 확대 해석하지 않는다.

### 재현

동일 PC의 Android SDK와 JDK 17을 사용해 빌드한다. 검증 스크립트는 지정한 검증용 AVD와 에뮬레이터 시리얼을 확인하며 실제 기기에서 실행하지 않는다.

```powershell
.\Build-And-Test.cmd
.\gradlew.bat :app:lintDebug --console=plain
python scripts/prepare-native-window.py --serial emulator-5580
python scripts/verify-unslop-ui.py --serial emulator-5580 --avd LifeLink119_Android34_UI_20261107
python scripts/build-unslop-review.py
```

화면 검증 패키지는 개발용 APK와 별도로 설치되며 앱 배포본에 포함되지 않는다. 자체 인증창의 표준 ‘취소’ 버튼만 취소할 수 있고, PIN 값이나 의료정보를 읽거나 인증을 통과시키지 않는다. 보호 화면의 FRAME 요청 거부를 유지한다. UI 도구가 시작한 작업을 전면으로 가져온 뒤 조작하며, `CLEAR_TASK`로 검증 중인 Activity를 교체하지 않는다.

## 출처

적용한 스킬: [unslop](https://github.com/heydamir/unslop), [SKILL.md](https://github.com/heydamir/unslop/blob/main/skills/unslop/SKILL.md). 제작자 **Damir Sokolovsky (@heydamir)**, **[CC BY 4.0](https://creativecommons.org/licenses/by/4.0/)**. 스킬의 점검 순서와 디자인 지침을 이 프로젝트의 Kotlin/Android Views 및 한국어 차량 UI에 맞춰 적용했다. 스킬 원본은 앱에 포함하지 않는다. 설치한 `SKILL.md`의 SHA-256은 `9e49b2ff7643fa09729e8665890a18f6bfa58a82e6fcbba330da38136ccdc393`이다.
