# 새 PC에서 개발 시작하기 · LifeLink 119 v0.18

**시연 모드 | 실제 신고·상담 연결 아님**

비공개 저장소: <https://github.com/airwrap/LifeLink119>. 초대받은 GitHub 계정으로 접근합니다. 소스·테스트·문서·Gradle wrapper를 공유하며 APK·JDK·SDK·AVD·가상환경·빌드 캐시·검증 로그·`local.properties`는 포함하지 않습니다.

소개 영상 소스는 별도 비공개 저장소 <https://github.com/airwrap/LifeLink119-video>에 있습니다. 두 프로젝트를 같은 부모 폴더 아래 나란히 배치하고 영상 저장소의 `MIGRATION.md`를 따릅니다. 본체 빌드에 영상 도구는 필요하지 않습니다.

## 코드 받기

Git 설치 후 원하는 작업 폴더에서 실행합니다. 고정된 드라이브 경로는 필요하지 않습니다.

```powershell
git clone https://github.com/airwrap/LifeLink119.git
cd LifeLink119
```

비공개 저장소는 Git Credential Manager의 브라우저 로그인 또는 `gh auth login`으로 인증합니다. 토큰을 URL·소스·문서에 넣지 않습니다. Android Studio의 Get from Version Control로도 같은 주소를 열 수 있습니다. `AGENTS.md`, `README.md`, `docs/CODEX_PROMPTS.md`부터 읽습니다. Empty Activity를 새로 만들거나 기존 `install-starter.ps1`로 덮어쓸 필요가 없습니다.

## 담당 영역별 환경

| 작업 | 준비할 환경 | 차량 필요 여부 |
|---|---|---|
| Python 서버·웹·문서 | Git, Python 3.10 이상, 브라우저 | 필요 없음 |
| Android 빌드·단위 테스트 | Android Studio, JDK 17, SDK Platform 34, Build Tools 35.0.0 | 필요 없음; 빌드에는 AVD도 불필요 |
| Pleos 화면·Vehicle SDK·CarUX 시연 | 위 Android 환경 + Platform-Tools, Emulator, 공식 Pleos API 34 이미지·AVD | 에뮬레이터로 가능; 실차 검증은 별도 |

프로젝트 고정값은 minSdk 28, compile/targetSdk 34, AGP 8.8.2, Gradle 8.10.2, Kotlin 2.0.21, Vehicle SDK 2.3.2입니다. Gradle은 wrapper가 내려받으므로 별도 설치하지 않습니다. 첫 빌드에는 Gradle 배포 서버·Google·Maven Central·Pleos Maven에 접속할 수 있어야 합니다.

## 웹·서버 먼저 실행

Windows PowerShell에서 프로젝트 루트 기준으로 실행합니다. Python 3.13은 기존 개발 PC에서 확인한 버전이며 최소 요구 버전은 3.10입니다.

```powershell
python -m venv .venv
.\.venv\Scripts\Activate.ps1
python -m pip install -r console/requirements.txt
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/check-env.ps1 -WebOnly
.\Start-Local-Demo.cmd
```

가상환경 활성화가 정책으로 차단되면 시스템 정책을 바꾸는 대신 현재 터미널 PATH에 가상환경을 추가합니다. 이후 같은 터미널에서 실행합니다.

```powershell
$env:PATH = "$(Get-Location)\.venv\Scripts;$env:PATH"
python -m pip install -r console/requirements.txt
.\Start-Local-Demo.cmd
```

- 상담원: <http://127.0.0.1:8781/>
- 탑승자: <http://127.0.0.1:8781/passenger>
- 주변 차량 협력: <http://127.0.0.1:8781/cooperation>
- Android 보조 상태 화면: <http://127.0.0.1:8781/experience-console>
- 종료: `Stop-Local-Demo.cmd` (이 런처가 시작한 서버만 종료)

모든 주소는 **서버를 실행한 PC 안에서** 접속합니다. 원격 PC라면 원격 데스크톱의 브라우저로 엽니다. 저장소 공유만으로 두 PC의 세션이 연결되지는 않습니다. 서버를 인터넷에 공개하거나 실제 정보를 입력하지 않습니다. 종료·재시작하면 메모리의 모의 세션이 사라집니다.

macOS/Linux는 `python3 -m venv .venv`, `source .venv/bin/activate`, `python -m pip install -r console/requirements.txt`, `python console/server.py` 순서이며 Ctrl+C로 종료합니다. CMD/PowerShell 런처는 Windows용입니다.

## Android 빌드 환경

1. [Android Studio](https://developer.android.com/studio)를 설치합니다. SDK Manager에서 **Android SDK Platform 34**, **Android SDK Build-Tools 35.0.0**, **Platform-Tools**, **Android Emulator**를 설치합니다. SDK 경로는 다른 드라이브여도 됩니다.
2. **JDK 17**을 준비합니다. Android Studio 자체는 번들 런타임으로 실행하고, Settings → Build, Execution, Deployment → Build Tools → Gradle의 **Gradle JDK**를 17로 선택합니다. IDE와 터미널의 JDK는 다를 수 있습니다. [공식 JDK 안내](https://developer.android.com/build/jdks)
3. 복제한 프로젝트 폴더를 Open하고 Gradle Sync를 진행합니다. SDK 위치와 `local.properties`는 각 PC에서 생성하며 Git에 올리지 않습니다.
4. CLI에서는 아래 두 경로를 **자신의 실제 설치 경로**로 바꿉니다. JAVA_HOME은 bin의 상위 JDK 폴더입니다. 새 터미널에서는 다시 설정하거나 사용자 환경 변수로 등록합니다.

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
.\Build-And-Test.cmd
```

완료 조건은 `BUILD SUCCESSFUL`, 종료 코드 0, `app/build/outputs/apk/debug/app-debug.apk` 생성입니다. Android 테스트 보고서는 `app/build/reports/tests/testDebugUnitTest/index.html`에서 확인합니다. 새 PC에서는 APK를 먼저 빌드해야 합니다. Git에는 APK를 포함하지 않습니다.

빌드 스크립트는 명시한 JAVA_HOME·ANDROID_HOME·GRADLE_USER_HOME을 유지합니다. JAVA_HOME이 없을 때만 기존 PC의 프로젝트 옆 `.tools` JDK를 선택하고, GRADLE_USER_HOME이 없을 때만 그 위치의 기존 캐시를 재사용합니다. 새 PC에서는 이 폴더들이 필요하지 않습니다. 이전 PC의 `.gradle`·`.idea`·`local.properties`를 복사하지 않습니다.

## Pleos 에뮬레이터 구성·실행

[공식 Pleos 시작 가이드](https://document.pleos.ai/docs/pleos-only/getting-started)에 따라 SDK Update Sites에 **본인이 제공받은 공식 system image URL**을 등록하고 API 34 이미지를 설치합니다. 다운로드 권한과 제공 URL은 각 개발자가 공식 경로로 확보합니다. 일반 휴대폰 AVD로 Pleos SDK 동작을 검증할 수는 없습니다.

Device Manager에서 Automotive 디바이스와 공식 Pleos API 34 이미지를 선택합니다. 자동 실행용 이름은 **Pleos_Connect_API_34**, RAM 4GB, Cold boot를 사용합니다. 가상화와 에뮬레이터 가속을 준비합니다. 원격·가상 PC는 중첩 가상화·그래픽 가속 지원에 따라 실행이 느리거나 불가능할 수 있으므로 먼저 Device Manager에서 정상 부팅을 확인합니다.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/check-env.ps1
.\Run-Full-Demo.cmd
```

전체 실행은 Python 가상환경을 선택한 터미널에서 진행합니다. 로컬 서버 → 전용 AVD 부팅 → ADB reverse → debug APK 설치 → 앱 실행 순서입니다. 기본 serial은 `emulator-5580`이며 `Run-Full-Demo.cmd emulator-5582`처럼 비어 있는 짝수 포트를 선택할 수 있습니다. 다른 AVD 이름을 쓰면 아래처럼 수동 실행합니다.

```powershell
.\Start-Pleos-Emulator.cmd My_Pleos_AVD
# Android Studio에서 해당 AVD를 선택하고 Run
.\Connect-Local-Console.cmd emulator-5554
```

실제 serial은 `& "$env:ANDROID_HOME\platform-tools\adb.exe" devices`로 확인합니다. 여러 기기가 있으면 serial을 지정합니다. AVD 이름만으로 공식 이미지 여부가 보장되지는 않으므로 Device Manager에서도 확인합니다.

19개 Android 시나리오(S01~S14, E01~E04, D01)는 PC 서버 없이 앱 안에서도 실행됩니다. PC 보조 화면 게시만 별도 동의·ADB 터널이 필요하며 **상태·항목 이름만** 전송합니다. 의료정보는 시스템 인증 또는 직접 설정한 앱 PIN으로 보호합니다. 기본 PIN은 없습니다. `docs/DEMO_SCRIPT.md`, `docs/MEDICAL_PRIVACY.md`를 따릅니다.

v0.11은 밝은 화면 UI, 주행 중 긴급 요청, 합성 영상 안내, 직접 입력한 건강정보의 기기 내 암호화 저장, 건강정보 고속도로 가입 동의·투약 예시를 포함합니다. 실제 의료기관 가입·조회·전송은 연결되지 않았습니다. 전체 기능 발표자료는 `docs/presentation/complete-functions-v11/output/`에서 내려받아 볼 수 있습니다.

종료는 앱/에뮬레이터 홈, 에뮬레이터 창 X, `Stop-Local-Demo.cmd`로 합니다. AVD 재시작 뒤에는 ADB reverse도 다시 연결합니다.

## 검사와 협업

```powershell
python -m unittest discover -s console -p 'test_*.py' -v
python scripts/validate-demo-contract.py
.\Build-And-Test.cmd
```

GitHub Actions는 Windows에서 Python 테스트·계약 검사, Linux에서 Android debug 빌드·JVM 단위 테스트를 수행합니다. Pleos AVD·OEM 인증·실차 검증은 CI에 포함하지 않습니다. 로컬과 원격 Actions 결과를 따로 확인합니다.

일부 AVD 자동 검사(`smoke-medical-privacy.py`, 공모전 검사 등)는 앱 데이터와 가상 프로필·PIN을 초기화합니다. **가상 데이터만 있는 전용 테스트 AVD**에서 스크립트 내용을 확인하고 실행합니다. 일상적으로 쓰는 AVD나 기기에 그대로 실행하지 않습니다.

[협업 규칙](../CONTRIBUTING.md), [20개 개선 검토 항목](reviews/20261030/README.md)을 기준으로 나눠 작업합니다. 미해결 검토 항목이 남아 있습니다. 기존 구현 보고서의 검증 수치는 해당 버전·환경의 과거 기록이며 새 PC에서 자동 재검증된 결과가 아닙니다. 기존 `verification/`·`artifacts/` 링크는 원래 개발 PC의 로컬 근거이며 Git에는 포함하지 않습니다.

## 문제 해결

| 증상 | 확인 |
|---|---|
| Repository not found | 비공개 저장소 초대 수락·올바른 GitHub 계정 로그인 |
| JAVA_HOME·Gradle JDK 오류 | 실제 JDK 17 경로, bin/javac.exe, IDE Gradle JDK |
| SDK location not found | ANDROID_HOME 또는 IDE SDK Location; local.properties는 PC별 생성 |
| 의존성 다운로드 실패 | Gradle·Google·Maven Central·Pleos Maven 접근 및 프록시·인증; 키는 소스에 기록하지 않음 |
| Python import 오류 | 가상환경 선택 후 requirements 설치 및 check-env.ps1 -WebOnly |
| AVD 부팅 실패 | 공식 이미지·AVD 이름·가상화·가속·디스크 여유·포트 점유 |
| 8781 사용 중 | 기존 서비스 확인; 런처는 다른 서비스를 종료하지 않음 |
| Android와 PC 연결 실패 | debug 빌드·서버 health·대상 serial의 ADB reverse; release는 보조 HTTP 연결 비활성 |

macOS/Linux Android 빌드는 JDK 17과 SDK 준비 뒤 `./gradlew :app:assembleDebug :app:testDebugUnitTest`를 사용합니다. Pleos 이미지의 호스트 OS·CPU 지원은 공식 배포 안내를 확인해야 하며 해당 OS의 런처·AVD 동작은 이번에 검증하지 않았습니다.


## v0.17 UI와 전체 매뉴얼

현재 앱은 차콜 배경과 밝은 버튼을 사용합니다. [전체 매뉴얼 HTML](manual/v17/output/LifeLink119_Manual_v0.17.html)은 설치 없이 로컬에서 열 수 있고, 실제 공개 화면과 보호 화면의 절차를 구분해 안내합니다. [PDF](manual/v17/output/pdf/LifeLink119_Manual_v0.17.pdf)는 인쇄·제출용입니다. 빌드·UI·SDK 검증은 [v0.17 기록](UI_BRIGHT_CONTROLS_V17.md)을 참고하세요.

SDK 검사 결과의 기본 위치는 `verification/pleos-sdk-current/results.json`입니다. cold boot 뒤 Console 게시자가 늦게 준비될 수 있어 최대 120초를 기다립니다. 별도 APK 설치기는 현재 Android 사용자를 확인하고 앱 데이터를 유지한 채 시간 제한이 있는 push+PackageManager 설치를 사용합니다. 실제 차량·추가 차량 제어 권한이나 실제 기관 연결은 필요하지 않습니다.

## 차량 데이터 기능 확인

기존 `Vehicle:2.3.2`와 JDK/SDK 설정을 그대로 사용합니다. 앱에 문서화된 읽기 권한 CAR_DOORS·CAR_ENERGY·CAR_SAFETY가 선언되어 있습니다. 임의 차량 제어 권한이나 실제 신고 API 설정은 필요하지 않습니다.

- **일반 Android 에뮬레이터**: UI 개발과 명시적 예시 회귀가 가능합니다. 차량 상태에서 SDK 조회 시 확인 불가가 정상이며 이를 SDK 성공으로 간주하지 않습니다.
- **정상 Pleos Connect 에뮬레이터**: 더보기 → 차량 상태 → 새로 조회. Virtual Console에서 도어 상태를 변경하고 SDK 반환값이 앱에 반영되는지 확인합니다. 표준 Android의 예시 화면 검사와 별도로 기록합니다.
- **119·112 연결 후**: 최신 차량 조회값에 동의 → 앱 내 전달 → 철회/30초 만료를 확인합니다. EV 계산에서는 최근 SoC를 직접 불러옵니다. 실제 통화·기관/PC 전송·차량 전원 제어는 하지 않습니다.

이 개발 PC는 별도 AVD의 1280×720·4GB·swiftshader·cold boot·GUI 엔진으로 도어와 SoC의 실제 SDK 값 변화를 검증했습니다. 에어백은 현재 이미지에서 미지원입니다. [검증 환경·결과](SDK_RUNTIME_VERIFICATION_V16.md)를 참고하세요. `sys.boot_completed`, 차량 서비스, Console emulator_active/bridge_ready를 먼저 확인하고 heartbeat를 SDK 조회 동안 유지해야 합니다. 부팅 장애를 해결하려고 기존 AVD 데이터를 초기화하거나 실제 CarUX 제한을 끄지 마세요.

JDK 17·API 34 SDK·공식 Pleos 이미지·Python 3을 설치한 Windows PC에서는 프로젝트 루트의 `Verify-Pleos-SDK.cmd`를 실행합니다. 빌드·단위 테스트·lint와 별도 읽기 전용 검사 APK 준비 후 허용된 테스트 AVD에서 공식 Console 값 변경 → 설치 앱 SDK 값 비교 → 원복을 수행합니다. 처음 실행 시 cold boot에 수 분이 걸릴 수 있습니다. SDK 값 검사에는 실제 차량·전화·상담 서버·Playwright가 필요하지 않습니다. 기존 포트가 다른 AVD에 배정되어 있으면 중단하며 기존 데이터를 지우지 않습니다. 검사 앱과 서비스 앱은 같은 PC의 debug 키로 서명해야 합니다.

자동 검사에서 Console bridge가 준비되지 않았거나 SDK가 값을 읽지 못하면 실패/차단으로 남깁니다. 표준 Android의 예시 버튼 성공으로 대체하지 않습니다. 이전 APK 이름·화면·검사 수치는 각 버전의 기록이며 최신 SDK 결과는 README의 v0.16.1 항목입니다.
