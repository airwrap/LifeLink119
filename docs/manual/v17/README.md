# LifeLink 119 전체 이용·시연 매뉴얼 · v0.17

완성본은 [오프라인 HTML](output/LifeLink119_Manual_v0.17.html)과 [PDF](output/pdf/LifeLink119_Manual_v0.17.pdf)입니다. HTML은 이미지를 포함한 단일 파일이며 목차 검색과 화면 확대를 지원합니다.

총 **52쪽**, 실제 공개 화면 **40개**를 포함합니다. 전체 흐름과 인쇄용 목차·PDF 책갈피, 개인정보 보호 절차, 기능 범위와 발표 순서를 함께 제공합니다.

## 수록 범위

홈·119 긴급 요청·주행 상황 전달·동의형 차량 상태 공유·인계·종료, 119 의료상담·109 상담·112 신고, 영상 안내, 문자·수어·음성 문장·쉬운 안내, 가상 위치·공동대응·목적지 설정 전제조건, 주변 차량 협력·기관 역할의 표시 승인, 응급 매뉴얼, 접근성·의료정보 잠금, 안심콜·건강정보 등록·투약 내역·건강정보 고속도로 예시, SDK 검증·발표 순서·새 PC 실행을 설명합니다.

`images/`의 40개 공개 화면은 실제 APK의 Android Activity View를 촬영한 결과입니다. `capture-manifest.json`은 APK·이미지 SHA-256과 출처를 기록합니다. 표준 Android 34 / 1280×800 구성으로 촬영했으며, 장치 화면 크기는 촬영 후 복원했습니다. 이미지 내부의 앱 UI를 HTML로 재구성하지 않았습니다.

의료정보·PIN·보호 화면은 캡처하지 않습니다. 해당 부분은 **보호 화면 안내도**로 구분해 소스에서 확인한 절차와 입력 항목을 설명합니다. 의료정보 인증·FLAG_SECURE의 캡처 거부는 동일 APK의 별도 네이티브 회귀 결과로 확인했습니다. 기존 PIN이나 의료기록을 입력·생성·변경·삭제하지 않았습니다.

차량 상태 화면의 예시는 명시적으로 선택한 예시 데이터입니다. `sdk-verification.json`은 동일 APK에서 공식 Console 입력과 실제 Vehicle SDK 반환값을 비교한 요약입니다. 둘을 혼동하지 않도록 본문에 구분했습니다. 실제 기관·건강정보 고속도로·영상통화·차량 등화/전원 제어와 연동됐다는 주장을 하지 않습니다.

## 재생성

원본 이미지와 메타데이터가 있으면 Windows에서 PDF/HTML을 다시 만들 수 있습니다. Python 3와 `reportlab`, Windows 맑은 고딕 글꼴이 필요합니다.

```powershell
python scripts/build-service-manual.py
```

출력 검사는 `python scripts/verify-service-manual.py`로 수행합니다. PDF 검사에는 PyMuPDF·Pillow, HTML 검사에는 Playwright와 Chromium이 필요합니다. Playwright가 다른 Python 환경에 있으면 `LIFELINK_BROWSER_PYTHON`으로 해당 Python 실행 파일을 지정할 수 있습니다.

현재 개발 PC의 전용 표준 Android AVD에서 다시 촬영하려면 먼저 동일 debug APK와 별도 QA helper를 설치합니다. 스크립트는 지정 AVD 이름·버전·해시를 확인하며 실제 기기를 대상으로 실행하지 않습니다. 장치 이름은 `LifeLink119_Android34_UI_20261107`, serial은 `emulator-5580`입니다. 다른 PC에서 이 이름과 경로를 그대로 사용할 수 있다고 가정하지 마세요.

```powershell
python scripts/prepare-native-window.py --serial emulator-5580
python scripts/verify-unslop-ui.py --serial emulator-5580 --avd LifeLink119_Android34_UI_20261107 --revision v17
python scripts/capture-service-manual.py --serial emulator-5580
python scripts/build-service-manual.py
```

실제 SDK 검사는 `Verify-Pleos-SDK.cmd` 또는 `scripts/verify-pleos-sdk-values.py`를 사용합니다. 요약 JSON과 촬영본의 APK 해시가 일치해야 매뉴얼 생성이 진행됩니다. 내부 오류 로그·설치된 SDK 이미지·개인 키는 매뉴얼에 포함하지 않습니다.

UI 개선 참고: [unslop](https://github.com/heydamir/unslop), Damir Sokolovsky (@heydamir), CC BY 4.0.
