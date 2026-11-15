# LifeLink 119

**나와 내 가족을 위한 안전 동행** — 차량·위치·가족 건강정보로 긴급상황 전달을 돕고, 119 연결이 늦어지거나 구조대를 기다리는 동안 음성으로 상태를 확인하는 Pleos Connect 앱입니다.

> 공모전 제출본의 119·112·109 연결과 PC 상황실은 **모의 시연**입니다. 실제 전화·문자를 발신하거나 기관 접수·출동을 요청하지 않습니다.

## 최종 설명자료와 시연영상

**2026-11-15 최종본 · 앱 v0.18.1 · 24장**입니다. 최신 현대 SUV·가족·반려견 이미지와 실제 앱 화면으로 사용 순서를 설명합니다.

| 자료 | 바로 보기 / 다운로드 |
|---|---|
| 설명자료 PDF | [LifeLink119_Final_Guide.pdf](docs/presentation/scenario-safety-20261115/output/LifeLink119_Final_Guide.pdf) |
| 편집 가능한 PowerPoint | [LifeLink119_Final_Guide.pptx](docs/presentation/scenario-safety-20261115/output/LifeLink119_Final_Guide.pptx) |
| 최종 시연영상 MP4 | [LifeLink119_demo_final.mp4](https://github.com/airwrap/LifeLink119-video/blob/main/contest-demo-20261115/output/LifeLink119_demo_final.mp4) |

가족 나들이 중 응급질환 → 119 긴급신고 → 응급 요약 → 연결 지연 시 AI 음성 동행 → 상태 변화·재연결·인계 요약을 중심으로, 응급상담·마음상담과 안심콜 활용을 설명합니다. 안심콜 본인 정보 자동 연계와 폭염·CO₂·CO·잔류 탑승자 감지는 공식 협력이나 센서 연동이 필요한 확장안으로 구분했습니다. [페이지 구성·발표자 노트·제작 소스 안내](docs/presentation/scenario-safety-20261115/README.md).

## 제출 APK

- **제출 저장소:** <https://github.com/airwrap/LifeLink119>
- **제출 APK 경로:** [`apk/LifeLink119_LifeLink119_release.apk`](apk/LifeLink119_LifeLink119_release.apk)
- 팀명 / 앱명: **LifeLink 119 / LifeLink 119**. 파일명은 공백 없이 `팀명_앱이름_release.apk` 형식을 사용합니다.
- 파일: `LifeLink119_LifeLink119_release.apk` — 약 152 MB, 독립 실행 가능한 서명된 **release APK**
- 패키지: `com.lifelink119.app` · versionCode `21` · versionName `0.18.1`
- `BuildConfig.DEBUG=false`, Android 디버깅 비활성화, 전용 release 인증서 서명입니다. 심사용 모의 기능은 별도의 빌드 설정으로 활성화했습니다.
- 한국어 오프라인 음성인식 모델과 고정 음성 안내를 포함합니다. 기본 시연에 PC 서버·LLM API 키가 필요하지 않습니다.
- APK는 GitHub 일반 Git 파일 크기 한도를 초과하므로 **Git LFS**로 `apk/` 폴더에 업로드했습니다. 파일 페이지의 **Download raw file**로 내려받거나 Git LFS가 설치된 환경에서 `git lfs pull`을 실행하세요. 소스 ZIP에는 APK 대신 LFS 포인터가 들어갈 수 있습니다. [다운로드·검증 방법](apk/README.md).
- [원본 APK Release](https://github.com/airwrap/LifeLink119/releases/tag/v0.18.1-contest-release)도 보조 다운로드로 유지합니다. 이번 제출 경로의 APK는 원본과 **바이트·서명·SHA-256이 동일**하며 파일명만 규정에 맞췄습니다. 제출 페이지에는 위 저장소 주소와 `apk/` 경로를 입력하고, 커밋 번호가 필요하면 최신 제출 커밋을 사용합니다.

## 실행 방법

1. 공식 Pleos Connect 에뮬레이터를 실행합니다. 아래 검증 환경을 권장합니다.
2. [`apk/LifeLink119_LifeLink119_release.apk`](apk/LifeLink119_LifeLink119_release.apk)의 파일 페이지에서 **Download raw file**을 눌러 내려받고 설치합니다.

```powershell
adb devices
# 아래 serial은 adb devices에 표시된 본인의 에뮬레이터 값으로 바꿉니다.
adb -s emulator-5586 install --no-streaming -r .\LifeLink119_LifeLink119_release.apk
adb -s emulator-5586 shell am start -n com.lifelink119.app/.MainActivity
```

기존 debug/demo APK와 제출용 APK의 서명이 다릅니다. `INSTALL_FAILED_UPDATE_INCOMPATIBLE`이 나오면 **새 AVD에 설치**하세요. 기존 AVD의 가족정보를 삭제할 필요가 없습니다. 같은 제출 인증서로 서명한 이후 버전은 `-r`로 업데이트할 수 있습니다.

3. 첫 실행에서 **기본 음성 안내로 시작**을 선택하면 외부 AI 없이 바로 사용할 수 있습니다. 실제 음성 답변에는 에뮬레이터 마이크 연결과 앱 마이크 권한이 필요합니다. 소리 출력이 가능한 상태로 에뮬레이터를 실행하세요.
4. 가장 빠른 시연은 **더보기 → 음성 동행 자동 시연**입니다. 한 번 누르면 합성 답변과 고정 질문이 진행되고 인계 요약이 표시됩니다. 마이크가 없어도 이 자동 시연은 작동합니다.

### 주요 시연 흐름

| 상황 | 조작과 결과 |
|---|---|
| 가족 나들이 중 응급질환 | 홈 **119 긴급신고** → 모의 연결 화면 → 응급상황 요약 확인 |
| 119 연결이 늦어지는 상황 | 신고 진행 상태를 사용자 확인으로 표시 → **AI 음성 동행** → 한 번에 한 질문에 짧게 답변 |
| 상태 변화·재연결 | 의식·호흡·증상 변화를 확인 → 위험 신호에는 119에 변화를 다시 알리도록 안내 → 인계 요약 확인 |
| 응급처치·심리적 위기 | 홈 **119 응급상담**, **109 마음상담**에서 각각 모의 연결 |
| 가족 안심콜 | 인증 후 직접 입력한 건강정보를 암호화 저장·조회. 환자를 확인하고 공유를 선택한 정보만 응급 요약에 포함 |

개인정보를 넣지 말고 합성 자료로 시연하세요. 실제 주행 제한이 확인되거나 상태가 불명확하면 복잡한 상세 입력을 제한합니다.

## 테스트 환경과 필요한 설정

| 항목 | 개발·검증 환경 |
|---|---|
| 공식 에뮬레이터 이미지 | **Pleos Connect v2.1.0**, Android API 34, x86_64 |
| 화면 규격 | **2560 × 1440 px, 160 dpi**, 가로 화면; Pleos의 앱 창은 시스템 UI/분할 화면에 따라 달라짐 |
| AVD 메모리 / Emulator | RAM 4 GB / Android Emulator **37.2.13** |
| 제출본 검증 AVD | `LifeLink119_Release_QA_API_34`, `emulator-5586` — 기존 사용자 데이터가 없는 별도 환경 |
| Android 빌드 | minSdk 28, compileSdk/targetSdk 34, Build-Tools 35.0.0 |
| JDK / Gradle | Temurin **17.0.21** / Gradle Wrapper **8.10.2** |
| Pleos SDK | Vehicle **2.3.2**, FusedLocation **1.1.3**, NaviHelper **3.0.5** |
| PC 콘솔 | Python 3.12, `console/requirements.txt`의 의존성; 기본 앱 시연에는 선택 사항 |
| 음성 | 고정 한국어 WAV + Vosk 한국어 모델 포함. 사람의 음성 답변에는 마이크 입력과 권한 필요 |

이 환경에서 release 설치·실행과 주요 화면, 오프라인 자동 음성 동행을 검사합니다. 다른 실차·화면·차량 소음 환경의 검증을 완료했다는 의미는 아닙니다. [제출본 빌드·실행 검증 기록](docs/SUBMISSION_RELEASE_20261115.md)을 참고하세요.

### PC 모의 상황실과 외부 LLM — 선택 설정

```powershell
python -m pip install -r console/requirements.txt
.\Start-Local-Demo.cmd
adb -s emulator-5586 reverse tcp:8781 tcp:8781
```

PC에서 `http://127.0.0.1:8781/`을 엽니다. 상황실 수신은 모두 모의 자료입니다. 외부 LLM을 사용할 때만 `.env.example`을 참고해 로컬 `.env`를 설정하고 앱의 **더보기 → 음성 AI 설정**에서 동의합니다. API 키는 PC 프록시에서만 사용하며 APK/Git에 넣지 않습니다. 실제 주소·인증 설정은 [LLM 프록시 설정](docs/LLM_PROXY_SETUP.md)을 따릅니다. 네트워크가 끊겨도 고정 응급 안내와 모의 연결을 사용할 수 있습니다.

## 소스에서 빌드

Windows에서 JDK 17, Android SDK Platform 34와 Build-Tools 35.0.0을 준비하고 `JAVA_HOME`, `ANDROID_HOME`을 설정합니다. 최초 Gradle 실행은 의존성 다운로드를 위한 인터넷이 필요합니다. 이 개발 패키지의 `.tools/lifelink-env.ps1`이 있으면 런처가 현재 프로세스에만 적용합니다.

```powershell
# 처음 한 번: 약 82 MB의 공식 한국어 인식 모델을 확인·다운로드하고 고정 질문 WAV를 생성합니다.
# WAV 생성에는 Windows 한국어 TTS 음성이 필요합니다.
python scripts/prepare-offline-speech.py

# 처음 한 번: 저장소 밖에 전용 서명키를 생성하고 release 빌드·검사를 실행합니다.
.\Build-Release.cmd -InitializeSigning

# 이후에는 같은 키로 다시 빌드합니다.
.\Build-Release.cmd
```

빌드 결과는 `artifacts/submission/LifeLink119-v0.18.1-contest-release.apk`이며, 같은 파일을 제출 경로 `apk/LifeLink119_LifeLink119_release.apk`에도 복사하고 체크섬을 생성합니다. **`apk/`의 제출 APK만 Git LFS로 추적**하며, 생성 중간 APK·원본 모델 캐시·서명키는 Git에 포함하지 않습니다. 음성 모델과 라이브러리 고지는 APK의 `assets/licenses/`에 포함합니다.

`Build-Release.cmd`는 포매터 검사, debug/release 빌드·단위 테스트, release lint, 인증서와 `debuggable=false` 검사를 수행합니다. 서명키는 기본적으로 저장소 옆 `.tools/release-signing/`에 두고 비밀번호는 현재 Windows 계정의 DPAPI로 보호합니다. **이 폴더를 비공개로 백업**해야 동일 인증서 업데이트가 가능합니다. 다른 개발자가 생성한 새 키는 배포 APK와 서명이 다릅니다.

고급 CI 빌드는 `-PlifelinkSubmission=true`와 `LIFELINK_RELEASE_KEYSTORE`, `LIFELINK_RELEASE_STORE_PASSWORD`, `LIFELINK_RELEASE_KEY_ALIAS`, `LIFELINK_RELEASE_KEY_PASSWORD` 환경변수를 사용합니다. 자격증명이 빠지면 제출 빌드를 중단합니다. 일반 `release`는 모의 네트워크를 끄고, **제출 release**만 모의 기능과 ADB 루프백 프록시를 명시적으로 켭니다. 두 경우 모두 디버깅은 비활성화합니다.

## 구현 범위와 자료

- 차량 SDK가 제공하는 값·에뮬레이터 값·합성 자료·미지원·확인 불가를 구분합니다. 에어백 신호 부재를 안전하다는 뜻으로 해석하지 않습니다.
- 현재 안심콜 기능은 본인·가족이 입력한 암호화 로컬 정보 활용과 공식 사이트 안내입니다. 본인 인증을 통한 소방청 자동 조회·기관 전송은 **공식 협력·연동이 필요한 확장 범위**입니다.
- AI는 의사나 119 상담원을 대체하지 않습니다. 위험 신호에는 고정 안내를 우선하며, 실제 접수·콜백 예약·출동을 임의로 확정하지 않습니다. 의료전문가 검토와 실차 검증은 후속 과제입니다.
- 실내 폭염, CO₂/CO 전용 센서, 잔류 탑승자 감지는 센서·제조사 연동이 필요한 제안 기능입니다.

[최신 시연영상·제작 소스](https://github.com/airwrap/LifeLink119-video/tree/main/contest-demo-20261115) · [최종 설명자료 안내](docs/presentation/scenario-safety-20261115/README.md) · [현재 기능 상태](docs/CURRENT_STATUS.md) · [음성 사용법](docs/VOICE_AI_IMPLEMENTATION.md) · [안심콜 연계 검토](docs/SAFE_CALL_WEB_INTEGRATION_REVIEW.md) · [개발 이력](HISTORY.md)
