# 음성 동행 사용 안내 · 현대차와 가족

현재 앱의 음성 중심 사용법과 자동 시연을 설명하는 16장 자료다. 현대차 사진을 디자인 참조로 삼아 가족 장면을 합성한 이미지를 표지와 앱 홈에 적용했다. 7·8페이지는 선택지를 줄인 현재 질문·연결 대기 화면, 10페이지는 안심콜 건강정보 활용 절차다.

- [PowerPoint](output/LifeLink119_음성중심_사용안내_현대차가족.pptx)
- [PDF](output/LifeLink119_음성중심_사용안내_현대차가족.pdf)
- [현대차와 가족 이미지](assets/hyundai-family-hero.png)
- [이미지 생성 프롬프트](assets/hyundai-family-hero.prompt.txt)
- [기능 개선·검증 기록](../../UX_REFINEMENT_20261115.md)

PPTX 본문·질문 표는 편집 가능한 요소이며 앱 화면은 캡처 이미지다. 보안 화면은 수록하지 않았다. 앱의 실제 기관 발신·접수·출동 연동과 공식 안심콜 자동 조회는 미구현이다. 의료전문가 검토와 실차 음성 검증도 남아 있다.

## 앱 시연

프로젝트 루트에서 JDK 17, Android SDK 34, 공식 Pleos QA AVD를 준비한 뒤 실행한다. 기존 AVD가 없으면 `scripts/start-qa-emulator.ps1`로 전용 AVD를 먼저 만든다.

```powershell
# Windows 한국어 TTS와 오프라인 한국어 인식 모델 준비
python scripts/prepare-offline-speech.py
.\Build-And-Test.cmd
.\Start-Voice-Demo.cmd
```

`ANDROID_HOME` 또는 `ANDROID_SDK_ROOT`를 사용하며 이 PC의 패키지 옆 `.tools/android-sdk`는 폴백이다. 인식 모델·합성 음성·SDK·JDK는 Git에 넣지 않는다. 준비 스크립트는 고정 SHA-256으로 모델을 검사한다. 모델 준비 없이도 빌드할 수 있지만 시스템 TTS/기기 내 한국어 인식이 없는 환경에서는 음성 기능이 제한된다. 외부 AI 설정은 프로젝트 루트 `.env.example`을 참고해 로컬 `.env`에 입력한다.

슬라이드 15페이지의 `.tools/Start-LifeLink-Voice-Demo.cmd`는 이전 PC 패키지용 이름이다. GitHub에서 받은 소스에서는 같은 시연을 실행하는 프로젝트 루트 `Start-Voice-Demo.cmd`를 사용한다.

앱에서 최초 외부 AI 선택·Android 마이크 권한을 설정하고 **더보기 → 음성 동행 자동 시연**을 누른다. 합성 응답이 자동 진행되며 실제 마이크 인식과 구분 표시된다. 실제 입력 체험은 **119 → AI 음성 동행 시작**을 사용한다. 마이크를 새로 연결했다면 `Start-Voice-Demo.cmd -Restart`로 호스트 오디오 구성을 다시 적용한다.

## 자료 제작 소스

`.build/prepare.py`, `build_deck.mjs`, `render_pdf.py`와 `assets/` 입력을 보관한다. Python 3.10 이상, Pillow·ReportLab·PyMuPDF, Node.js, Windows의 맑은 고딕 글꼴이 필요하다. 작업 폴더를 이 문서가 있는 폴더로 바꾼 뒤:

```powershell
python .build/prepare.py
node .build/build_deck.mjs
python .build/render_pdf.py
```

초안은 `.build/candidate.pptx`·`.build/candidate.pdf`다. 최종 `output/` 파일은 이미 검수한 전달본이다. `finalize.mjs`를 사용하려면 `LIFELINK_PRESENTATIONS_SKILL`에 설치된 Presentations 스킬 경로를, 필요하면 `LIFELINK_PYTHON`에 Python 실행 파일을 지정한다. `delivery_check.py`는 최종 PPTX가 있을 때 PDF를 복사하고 두 파일의 페이지·텍스트를 점검한다. 중간 렌더·로그는 Git에서 제외한다. 새로운 내용은 초안을 검수한 후 별도 결과 파일로 저장한다.

최신 이미지 적용 후 Debug/Release/Demo 빌드·Kotlin 288개와 홈 화면을 확인했다. 기능 검증은 [UX 검사 당시 APK](../../UX_REFINEMENT_VERIFICATION.json), 이미지 교체 이후 APK는 [별도 기록](../../FAMILY_HYUNDAI_VERIFICATION.json)에 구분한다. PDF는 PPTX와 공통 배치를 사용해 생성했으며 네이티브 PowerPoint 앱 검수는 수행하지 않았다.
