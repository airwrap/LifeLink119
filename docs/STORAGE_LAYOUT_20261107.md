# C:/D: 중복 파일 조사 및 AVD 이전 결과

확인일: 2026-11-07. 이 문서는 현재 개발 PC의 로컬 저장 경로를 기록한다. 다른 PC에서는 각자의 SDK/AVD 경로를 사용한다.

## 처리 결과

등록된 `Pleos_Connect_API_34` 에뮬레이터의 전체 데이터 63개 파일, 13,687,303,259바이트(12.75GiB)를 C:에서 D:로 이전했다. 전체 파일의 SHA-256 일치를 확인한 후 C:의 실제 원본을 제거했다.

| 항목 | 처리 전 | 처리 후 |
| --- | ---: | ---: |
| C: 여유 공간 | 18.48GiB | 31.19GiB |
| D: 여유 공간 | 29.78GiB | 17.04GiB |

C:의 측정 여유 공간 증가량은 13,646,602,240바이트(약 13.65GB, 12.71GiB)다. 여유 공간은 다른 프로세스의 사용량에 따라 변할 수 있다. 이번 작업은 C: 공간을 확보하기 위한 이전이며, C:/D: 합산 사용량을 줄이는 작업은 아니다. D:의 기존 복구 백업은 유지했다.

## 현재 저장 경로

- **실제 저장소:** `D:\CODEX\ORCA\.tools\android-avd\Pleos_Connect_API_34.avd`
- **AVD 등록 파일:** `C:\Users\hotmh\.android\avd\Pleos_Connect_API_34.ini`의 `path=`가 위 D: 경로를 참조한다. 이전 `path.rel` 항목은 제거했다.
- **기존 경로 호환:** `C:\Users\hotmh\.android\avd\Pleos_Connect_API_34.avd`는 위 D: 폴더를 가리키는 디렉터리 정션이다. 과거 설정에 남은 절대 경로를 유지하기 위한 연결이다.

중복 탐색 프로그램이 정션을 따라가면 C: 경로에서도 같은 파일을 표시할 수 있다. 이는 D:의 동일한 파일을 다른 경로로 보여주는 것이며, C:에 대용량 실데이터가 다시 복제된 것은 아니다. C: 정션을 대상으로 재귀 삭제를 실행하지 않는다. D: 실제 저장소도 에뮬레이터 데이터이므로 임시 산출물로 정리하지 않는다.

기존 `Start-Pleos-Emulator.cmd`는 AVD 이름으로 실행하므로 수정하지 않았다. Android Studio에서도 같은 AVD 이름을 유지한다.

## 중복처럼 보인 이유와 판단

| 파일/환경 | 확인 결과 | 조치 |
| --- | --- | --- |
| C: Pleos `userdata-qemu.img.qcow2`와 D: `verification/v07/avd-before-standard` | 8,136,622,080바이트이며 전체 SHA-256이 동일. 검증 전 상태의 복구 백업 | C: 등록 원본을 새 D: 저장소로 이전. 기존 D: 복구 백업 유지 |
| C: Pleos `snapshots/default_boot/ram.img`와 D: `verification/v07/avd-clean` | 4,295,032,832바이트이며 전체 SHA-256이 동일. 같은 시점의 RAM 스냅샷 복사본 | C: 원본을 위 데이터와 함께 이전. 검증용 AVD는 유지 |
| 여러 AVD의 `userdata`와 `ram.img` | 같은 이름은 Android 에뮬레이터의 표준 파일명. AVD마다 별도 사용자 디스크/스냅샷을 가짐. 위 두 쌍 외에 전체 동일성을 주장하지 않음 | 파일명이나 크기만으로 삭제하지 않음 |
| C: Chatterbox 환경과 D: JedoBridge 환경의 `torch/lib/dnnl.lib` | 두 파일 모두 2,325,803,578바이트, Torch 버전 표시는 모두 `2.8.0+cpu`이나 전체 SHA-256은 다름. 서로 다른 Python 실행환경 | 모두 유지. 다른 프로젝트의 라이브러리로 대체하거나 개별 삭제하지 않음 |
| C: Chatterbox 모델과 D: Qwen TTS 모델 | 서로 다른 음성 모델. `model.safetensors` 같은 일반적인 파일명은 모델 동일성의 근거가 아님 | 유지 |
| BAAI/bge-m3, JedoBridge, RAGs, Whisper 관련 파일 | 별도 프로젝트의 실행환경/모델/데이터 | 작업 대상에서 제외 |
| `C:\Windows\LiveKernelReports`의 덤프 | Windows 진단 파일, 이 프로젝트 복사본이 아님 | 작업 대상에서 제외 |

Chatterbox 음성 재생성 스크립트 `LifeLink119-video/narration-announcer-v1/rebuild_calm_sample.ps1`는 C:의 `lifelink-chatterbox-v3-venv` Python을 직접 참조하고 있다. 이 실행환경을 삭제하면 음성 재생성에 문제가 생길 수 있어 유지했다.

## 보존한 환경

- `verification/v07/avd-clean`: `LifeLink119_Verify_20261101` 등록 AVD.
- `verification/v07/avd-before-standard`: 복구 백업.
- `verification/unslop-v12/avd-fresh`, `avd-android34`: 별도의 등록된 UI 검증용 AVD.
- `C:\Users\hotmh\AppData\Local\LifeLink119SdkVerify\avd-compat-36.2.12`: 별도 SDK 검증용 AVD. 이번 이전 대상과 다르다.
- C: Android SDK와 TTS 모델/실행환경, 다른 프로젝트 데이터.
- 실행 중인 LifeLink119 콘솔 서버. 에뮬레이터는 작업 전후 모두 종료 상태를 유지했다.

## 검증 범위

1. 이전 전 에뮬레이터 프로세스가 없는 것을 확인했다.
2. 대상 폴더가 실제 디렉터리이고 내부 정션/심볼릭 링크가 없는 것을 확인했다.
3. Robocopy로 63개 파일을 복사했다. 실패/불일치 0개, 종료 코드 1(정상 복사).
4. 원본/대상의 전체 파일 목록, 디렉터리 목록, 크기, 모든 파일의 SHA-256을 대조했다.
5. 대상 루트 접근 권한은 원본의 사용자/시스템/관리자 권한을 유지하고 새 상위 폴더로부터 추가 권한이 상속되지 않게 했다.
6. AVD 등록을 D:로 변경하고 기존 C: 경로에 정션을 만들었다.
7. 5개 AVD 이름이 이전 전후 동일한 것을 확인했다.
8. `qemu-img info --backing-chain`으로 사용자 디스크, 캐시, 암호화 키 이미지의 참조를 D: 실제 경로와 C: 호환 경로에서 각각 확인했다(6개 검사).
9. 위 검증 후, 정확한 C: 임시 보관 경로가 정션이 아닌 실제 디렉터리임을 다시 확인하고 해당 C: 복사본만 삭제했다.
10. C: 정션의 목적지, 등록 파일, 원본 제거 여부와 여유 공간 증가를 확인했다.

에뮬레이터 부팅, Android 화면 동작, SDK 재실행은 이번 저장소 이전 작업에서 검증하지 않았다. 서비스 소스코드는 변경하지 않았다.

## 상세 증거

작업 증거는 Git에서 제외되는 [verification/storage-audit-20261107](../verification/storage-audit-20261107)에 보관했다.

- [원본 중복 검사](../verification/storage-audit-20261107/inventory.json)
- [63개 전체 파일 SHA-256 대조](../verification/storage-audit-20261107/copy-verification.json)
- [RAM 스냅샷 전체 SHA-256 대조](../verification/storage-audit-20261107/ram-comparison.json)
- [등록 목록·정션·이미지 참조 검사](../verification/storage-audit-20261107/cutover-verification.json)
- [이전 결과와 실제 공간 변화](../verification/storage-audit-20261107/migration-result.json)
- 원래 AVD 등록 파일의 작은 백업과 수행 스크립트, Robocopy 로그.
