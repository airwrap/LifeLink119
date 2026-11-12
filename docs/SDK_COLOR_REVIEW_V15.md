# SDK 사례를 참고한 차량 다크 색상 · v0.15

## 확인한 사례와 판단

확인일: 2026-11-07.

| 1차 자료 | 관찰 | 반영 |
|---|---|---|
| [Pleos Vehicle SDK 공식 샘플](https://github.com/Pleos-Playground/pleos-sample-vehicle-app), [테마 소스](https://github.com/Pleos-Playground/pleos-sample-vehicle-app/blob/959981d960ec71144accb27af5ae5d341e72a644/app/src/main/java/ai/pleos/playground/sample/vehicle/ui/theme/Theme.kt) | 샘플은 `lightColorScheme`과 밝은 바탕·흰 패널을 사용함. 기능 목록·상세 화면의 실제 AVD 이미지도 확인 | 샘플의 밝은 색이 SDK의 고정 테마라고 보지 않음. 같은 역할에 같은 색을 쓰는 구조를 참고 |
| [Pleos 색상 체계](https://document.pleos.ai/docs/connect/guide/docs-design/foundations/colors) | Light/Dark의 배경·버튼·표면·텍스트 역할을 구분하고, 버튼의 기본·누름·비활성 상태를 별도로 정의 | 배경·헤더·정보 패널·조작 버튼을 서로 다른 명도로 구분 |
| [Pleos 디자인 가이드](https://document.pleos.ai/docs/connect/guide/docs-design/Introduction/intro) | 충분한 대비, 불필요한 색 최소화, 주야간 환경 대응을 안내 | 읽는 글자와 밝은 채움색 위 글자의 색을 분리. 4.5:1 이상을 확인 |
| [Android Automotive 색상 가이드](https://developers.google.com/cars/design/automotive-os/design-system/color) | 어두운 기반·회색 단계·절제된 의미 색상과 가독성 대비를 안내 | 채도가 낮은 차콜·슬레이트를 기본으로 선택 |

이번 화면은 큰 카드 네 개가 대부분의 면적을 차지한다. 기존 밝은 바탕에서는 카드와 주변의 구분이 약했다. **짙은 차콜 바탕, 한 단계 밝은 슬레이트 버튼, 밝은 글씨**가 더 적합하다고 판단했다. 이는 이 앱에 대한 디자인 판단이며 SDK의 의무 테마나 실차 안전 검증 결과가 아니다. 샘플 소스를 제품에 복사하거나 SDK 버전·권한을 바꾸지 않았다.

현재는 **고정 다크 팔레트**다. 조도 센서·차량 주야간 신호에 따른 자동 전환은 구현하지 않았다. Pleos 가이드의 주야간 대응 전체를 완료했다고 주장하지 않는다.

## 디자인 방향과 적용

- 제품: 나와 가족을 위한 차량 응급지원
- 인상: 차분함, 선명함, 균형
- 환경: 어두운 자동차 대시보드와 큰 터치 조작판
- 핵심 장면: 네 가지 도움을 동등하게 고르는 홈과 읽기 쉬운 연결 상태
- 일관된 표시: 작은 119 빨강·112 파랑, 같은 조작 영역과 글씨 비중

[unslop](https://github.com/heydamir/unslop)의 순서에 따라 기존 간격·큰 터치 영역·짧은 문구·글씨·배치를 유지하고 색과 상태 표현을 수정했다. 새 장식·아이콘·이미지·진입 애니메이션은 추가하지 않았다. 기존 100~120ms 상태 전환을 유지한다.

| 역할 | 색상 |
|---|---|
| 전체 배경 | `#191C22` |
| 헤더 | `#20242B` |
| 정보 패널 | `#292F38` |
| 누르는 카드·중립 버튼 | `#3E4754` |
| 누름·비활성 바탕 | `#303A48` |
| 조작 테두리 | `#8996A8` |
| 제목·본문 | `#F3F5F8` |
| 보조 글씨 | `#C4CDD8` |
| 작은 파랑·상태·포커스 | `#A7C7F4` |
| 작은 119 빨강 | `#F2A9A2` |
| 파랑 채움 버튼 | `#315EA8` + 흰 글씨 |
| 119 채움 버튼 | `#A8322D` + 흰 글씨 |

네 홈 카드의 바탕·제목 크기·굵기는 같다. 상세 화면의 주요 동작에만 채움색을 사용한다. 숫자·본문에 쓰는 밝은 의미 색과 흰 글씨가 올라가는 어두운 채움색을 분리해 대비가 뒤집히지 않도록 했다. 기본 Android 대화상자·선택·입력과 시스템 표시줄도 어두운 테마로 맞췄다.

## 확인

- 빌드·단위 테스트 148개 통과, Android lint 오류 0·경고 0
- 계산한 제목/카드 대비 **8.61:1**, 보조 글씨 **5.85:1**, 119 작은 빨강 **4.91:1**, 112 작은 파랑 **5.42:1**
- 조작 테두리/카드 대비 **3.13:1**. 주요 채움 버튼의 흰 글씨도 4.5:1 이상
- 기본·누름·비활성·본문·정보 패널의 토큰 16쌍 확인: `verification/unslop-v15/palette-contrast.json`
- 실제 앱의 렌더링·큰 글씨·좁은 화면·연결 흐름·의료정보 보호 기록: `verification/unslop-v15/after/`
- 실제 디스플레이 크기 변경과 터치 입력 기록: `verification/unslop-v15/car-layout/`
- 주요 동작 **9항목 통과**, 실제 View 화면 **18개** 확인. 렌더링 버튼 글자색/바탕색 **111쌍**의 최소 대비는 **6.369:1**. 홈의 부분 색상 숫자는 별도 토큰 검사에서 최저 **4.91:1**
- 1280×720·1024×600에서 홈·연결 핵심 버튼을 스크롤 없이 표시, 실제 터치로 모의 119 요청 시작 확인. 원래 화면 크기와 큰 글씨 설정 복원
- 실제 터치를 누르고 있는 동안 카드 바탕이 `#3E4754` → `#303A48`로 변하는 것을 확인. 키보드 포커스는 밝은 파랑 3dp 선으로 표시

실행·배포 APK SHA-256: `9ff634e7e59f1417c1a47edd31a22d2c3aebe9bd92d9d92305802876ab35cd9b`

[화면 비교](reviews/unslop-v15/index.html) · [APK (로컬)](../artifacts/LifeLink119-v0.15.0-car-dark-theme.apk)

```powershell
.\Build-And-Test.cmd
.\gradlew.bat :app:lintDebug --console=plain
python scripts/verify-unslop-ui.py --serial emulator-5580 --avd LifeLink119_Android34_UI_20261107 --revision v15
python scripts/verify-car-touch-layout.py --serial emulator-5580 --revision v15
```

표준 Android 34 검증용 AVD에서 동일 APK를 확인한다. 앞서 발생한 Pleos 차량 오디오 서비스 문제는 이번 작업에서 수정하지 않았으며 실제 CarUX 콜백·차량 SDK·실차·야간 조명 환경·사람의 시인성 평가를 검증 완료로 주장하지 않는다. 의료정보 입력·PIN 설정·보호 화면 캡처 없이 확인하며 기존 데이터·동의·암호화·실제 플랫폼 제한을 유지한다. 모든 기관 연결은 기존 모의 기능이다.

스킬: **[unslop](https://github.com/heydamir/unslop)** by **Damir Sokolovsky (@heydamir)**, **[CC BY 4.0](https://creativecommons.org/licenses/by/4.0/)**. Kotlin/Android 차량 UI에 맞춰 적용했다. 공식 샘플의 참조 소스와 이미지는 분석 근거로만 보관하고 앱·비교 페이지에 재배포하지 않았다.
