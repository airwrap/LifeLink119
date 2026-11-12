# Pleos SDK 값 검증 · v0.16.1 / 2026-11-07

## 결론

실차 없이 **공식 Virtual Console의 가상 입력 → Pleos 차량 서비스 → Vehicle SDK 2.3.2 → 설치된 LifeLink 119 앱의 기존 Gateway**를 통과하는 값 변화 검증에 성공했습니다. 앱의 `예시 데이터` 분기는 사용하지 않았습니다. 입력은 에뮬레이터가 만든 값이며 실제 자동차에서 측정한 값은 아닙니다.

| 검사 단계 | 공식 Console 입력 | 앱 SDK 도어 반환값 | 앱 SDK SoC 반환값 | 결과 |
|---|---|---:|---:|---|
| 시작 | 운전석 도어 닫힘, 100% | READ 0 | READ 100 | 일치 |
| 변경 1 | 운전석 도어 열림, 80% | READ 1 | READ 80 | 일치 |
| 변경 2 | 운전석 도어 닫힘, 15% | READ 0 | READ 15 | 일치 |
| 원복 | 원래의 닫힘, 100% | READ 0 | READ 100 | 일치 |

결과 상태는 `SDK_VALUE_CHANGES_VERIFIED`, 원복은 `PASS`입니다. 도어 capability의 지원 범위 0~1을 먼저 확인했고, SoC는 공식 배포 소스의 정수 백분율 계약에 따라 비교했습니다. 초기 준비 중 TIMEOUT/BUSY가 발생한 시도도 원본 기록에 남습니다. 준비 대기 후 실제 READ가 반환되어야 통과하며, 실패를 예시 값으로 치환하지 않습니다.

## 버전과 증거

- 앱: **0.16.1-demo / versionCode 18**, minSdk 28 / compile·target 34.
- APK: [LifeLink119-v0.16.1-pleos-sdk-poc.apk](../artifacts/LifeLink119-v0.16.1-pleos-sdk-poc.apk).
- SHA-256: `b81f3d3bc9e1e8067ddbe8ac061570129796eee5eb45e683028e0e5bb0decfda`.
- [최종 SDK 값 비교 결과 JSON](../verification/pleos-sdk-v16-runtime/v161-clean-values/results.json): 최종 읽기 전용 검사 APK로 전체 네 단계를 다시 통과했습니다. 단계별 원본 SDK 상태·숫자·수신 시각·Console 상태를 함께 기록했습니다. 현재 Android 사용자 10의 실제 설치 APK를 `pm path --user`로 찾아 내려받아 빌드·전달본과 해시가 일치하는지 확인했습니다. [최초 v0.16.1 값 비교](../verification/pleos-sdk-v16-runtime/v161-values/results.json)도 보존했습니다.
- [빌드 결과](../verification/pleos-sdk-v16-runtime/build-v161-proof.json): assembleDebug 성공, 단위 테스트 **166개 / 실패 0**, lint **No issues found**.
- [최종 읽기 전용 검사 APK의 추가 실행](../verification/pleos-sdk-v16-runtime/clean-probe/results.json): 도어 READ 0, SoC READ 100, 에어백 UNSUPPORTED를 실제 런타임에서 확인했습니다. 검사 APK manifest에 별도 네트워크 권한이나 네트워크 설정용 instrumentation은 없습니다.

`verification/`, `artifacts/`는 개발 PC의 로컬 결과이며 Git에서 제외됩니다. 새 PC에서는 아래 명령으로 재생성합니다. v0.16.0의 표준 Android 예시 UI 회귀와 이번 공식 Pleos SDK 검증은 별도 결과입니다.

## 화면 검사와 SDK 검사 구분

위 SDK 값 검사는 통과했습니다. 추가로 시도한 **Pleos 네이티브 전체 화면 자동 회귀는 통과하지 못했습니다**. 공유 앱 패널을 기본 내비 앱이 차지해 LifeLink 창이 숨겨지는 상황을 확인했고, 검사 도구에서 일반 실행 Intent로 다시 여는 처리를 추가했습니다. 그 후에도 네이티브 View 캡처/조회가 비는 구간이 남아 SoC 표시 대기에서 중단했습니다. 최종 [UI 검사 결과](../verification/pleos-sdk-v16-runtime/ui/results.json)는 FAIL이며 입력 원복은 성공했습니다. 이를 ‘SDK가 잘못된 값을 반환했다’거나 ‘전체 UI가 검증됐다’는 결과로 바꾸지 않습니다.

현재 자동화의 보장 범위는 **설치된 앱의 실제 Gateway/SDK 반환값과 Console 입력의 일치**입니다. 동의·앱 내 전달·EV 계산 전체 화면의 실제 Pleos 자동 회귀와 주행 상태 회귀는 후속 확인 항목입니다. v0.16.0의 표준 Android 예시 데이터 회귀는 별도 기록으로 유지합니다. 런처의 선택적 `-Ui`는 이 조사용 검사이며 기본 SDK 합격 판정에 포함되지 않습니다.

## 에어백과 기능 한계

현재 Pleos Connect 이미지에서 에어백 capability 조회는 **`ERROR_NOT_SUPPORTED_PROPERTY`**를 반환했습니다. 이 신호의 변화·사고 상황은 SDK로 검증하지 못했습니다. 공모전에서는 에어백을 ‘현재 환경 미지원’으로 설명하고, 별도 예시를 보여줄 때만 예시 출처를 유지해야 합니다.

이번 실제 실행 결과를 반영해 앱은 공식 오류 메시지의 정확한 코드가 일치할 때 미지원·권한 거절·값 없음·서비스 연결 실패를 구분합니다. 비공개 예외 클래스나 리플렉션은 사용하지 않습니다. 같은 연결에서 확인한 에어백 미지원 결과는 재사용해 반복 capability 조회와 불필요한 재연결을 줄이고, SDK 해제·재연결 시에는 다시 확인합니다. TIMEOUT/null을 미지원으로 캐시하지 않습니다. 관련 단위 회귀 2개를 추가했습니다.

도어·SoC로 사고, 환자 상태, 전원 공급 가능 여부를 자동 판정하지 않습니다. 위치·음성·내비·실제 기관 연결·카메라·차량 등화·V2L 전원 제어는 이번 검증 범위가 아닙니다. EV 계산의 용량·효율·부하·예비 잔량은 수동 가정입니다. 실차 안전성·차종별 동작·성능 인증도 별도입니다.

## 이 PC에서 정상 동작한 구성

| 항목 | 값 |
|---|---|
| 공식 이미지 | Pleos Connect 2.1.0, Android API 34, x86_64, crp_avd |
| Android Emulator | 37.2.12 |
| 전용 AVD / serial | LifeLink119_SDK_Compat_20261107 / emulator-5582 |
| 화면 / 메모리 | 1280×720, density 160, 4GB RAM, 4 cores |
| 그래픽 | swiftshader, Vulkan 비활성, 30Hz 구성값 |
| 시작 | GUI 엔진 유지, cold boot, snapshot 사용 안 함, 오디오 장치 유지 |
| Console | guest 8088 → host loopback 18088, browser session heartbeat 유지 |

30Hz는 설정값이며 측정 FPS가 아닙니다. 이 PC는 Intel Iris Xe 내장 GPU를 사용합니다. 소프트웨어 그래픽으로 PoC는 확인했지만 공식 권장 그래픽 하드웨어 충족이나 실차 성능을 주장하지 않습니다. 이전 AVD의 HAL 반복 종료 문제는 이 별도 구성을 사용해 우회했습니다. 기존 AVD·앱 데이터·다른 에뮬레이터·로컬 서비스는 삭제하지 않았습니다.

이 환경에서 `-no-window`, `-no-audio`, 고해상도 설정은 안정적인 차량 서비스 검증에 적합하지 않았습니다. Console 상태 조회만으로 충분하지 않으며 `emulator_active`와 `bridge_ready`가 모두 살아 있는 상태를 유지해야 SDK에 변경이 전달됩니다. 자동화는 공식 browser session heartbeat를 조회 전체 동안 유지합니다. 초기 SDK 준비에 시간이 걸릴 수 있으며 준비 실패는 실패/차단으로 기록합니다.

공식 ZIP의 중요 파일 7개는 설치 이미지와 CRC를 비교했습니다. 전체 18GB system.img를 내려받아 해시 검증한 것은 아닙니다. 별도 조사한 Emulator 36.2.12는 최종 실행 엔진이 아닙니다.

## 재현 방법

Windows, JDK 17, Python 3, API 34 SDK·Build Tools 35.0.0·Platform Tools·Android Emulator 및 정식으로 받은 Pleos 이미지가 필요합니다. 별도 Playwright, 실제 차량, 긴급기관 계정이나 로컬 상담 서버는 이 SDK 값 검사에 필요하지 않습니다. 앱과 검사 APK는 같은 PC의 Android debug 키로 서명해야 합니다.

프로젝트 루트에서 실행합니다.

```powershell
Set-Location D:\CODEX\ORCA\LifeLink119
$env:JAVA_HOME='D:\CODEX\ORCA\.tools\java\jdk-17.0.20.1+1'
$env:ANDROID_HOME="$env:LOCALAPPDATA\Android\Sdk"
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\start-sdk-verification.ps1 -CheckOnly
.\Verify-Pleos-SDK.cmd
```

경로는 해당 PC에 맞게 지정합니다. 런처는 빌드·단위 테스트·lint, 읽기 전용 검사 APK 생성, 별도 허용 AVD 시작, 현재 Android 사용자 설치, Console 입력/SDK 비교/원복을 수행합니다. 기존 다른 AVD가 선택 포트를 사용 중이면 중단합니다. 최초 AVD 생성은 수 GB의 여유 공간과 수 분의 cold boot가 필요할 수 있습니다.

이 PC에서는 각 단계의 실제 실행과 최종 SDK 비교, 런처의 PowerShell 구문·사전 점검을 확인했습니다. 완전히 새 PC에서 AVD 최초 생성부터 런처 전체를 실행하는 검사는 아직 수행하지 않았습니다.

현재 준비된 환경에서 SDK 값 검사만 다시 실행하려면:

```powershell
python scripts\verify-pleos-sdk-values.py --serial emulator-5582 --avd LifeLink119_SDK_Compat_20261107 --port 18088
```

별도 [읽기 전용 검사 APK 소스](../scripts/android-sdk-probe/SdkProbeInstrumentation.java)는 설치된 앱의 `PleosVehicleGateway`를 직접 호출하며 SDK 값을 재구현하지 않습니다. emulator/ranchu/crp_avd 검사로 실물 장치 실행을 거부합니다. [Console 어댑터](../scripts/virtual_console.py)는 허용된 AVD와 ADB 포워딩을 매 요청 확인하고, localhost로만 접속합니다. 앱에는 차량 제어 API를 추가하지 않았고 테스트 도구만 공식 Console의 가상 도어·SoC 입력을 변경합니다.

## 공모전에서 보여줄 순서

1. Pleos 에뮬레이터와 공식 Virtual Console을 함께 열고, **실차 없이 차량 신호를 재현하는 환경**임을 설명합니다.
2. 도어 닫힘·SoC 100%를 앱의 차량 상태에서 새로 조회합니다.
3. Console에서 도어 열림·SoC 80%로 바꾸고 새로 조회해 앱 값이 바뀌는 것을 보여줍니다.
4. 긴급 연결은 SDK 성공·의료정보 등록·PIN과 별개로 시작합니다. 유효한 최신 조회값을 동의 후 앱 내 상황실에 전달하고 철회합니다.
5. EV 계산에서 조회 SoC를 직접 불러옵니다. SoC 15%에서는 예비 잔량을 제외한 가용 에너지가 0이 되는 가정을 설명합니다.
6. 현재 이미지가 지원하지 않는 에어백은 미지원으로 보여주고, 종료 후 Console 입력을 원복합니다.

앱의 실제 CarUX 제한을 끄거나 `distractionOptimized`를 임의 선언하지 않았습니다. 주행/제한 상태에서 상세 화면이 차단되면 이를 유지합니다. 의료정보 인증을 풀거나 FLAG_SECURE 화면을 캡처하지 않습니다. 실제 발신·신고·외부 의료정보 전송은 없습니다.

## 공식 참고

- [Virtual Console 사용 안내](https://document.pleos.ai/docs/pleos-only/app-test-guide/virtual-console-guide)
- [차량 에뮬레이터 사용](https://document.pleos.ai/en/docs/connect/guide/getting-started/application-development/use-vehicle-emulator)
- [에뮬레이터 설정·하드웨어 안내](https://document.pleos.ai/en/docs/connect/guide/getting-started/application-development/configure-pleos-connect-emulator)
- [Vehicle SDK 오류 코드](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle-error-code)
