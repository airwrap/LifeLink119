# LifeLink 119 v0.4.1 · 이동 중 응급상황 시연

변경 시작: 2026-10-25 · 최종 검증: 2026-10-26. 위급상황은 이동 중에도 발생한다는 요구에 따라 정차 확인을 선행 조건에서 제거했습니다.

## 변경한 동작

- 앱을 열면 이동 상태를 선택하지 않아도 5개 상담 흐름과 응급 매뉴얼, 주변 협력 차량 요청을 사용할 수 있습니다.
- **이동 중 상황 시연 / 정차 상황 시연**은 화면의 상황 설명만 바꿉니다. 이동 상태를 바꿔도 상담과 유효한 승인 표시는 유지됩니다.
- Android 앱과 웹의 매뉴얼·상담·협력 차량 화면을 함께 수정했습니다. 웹의 정차 체크박스를 제거했습니다.
- 비상표시 정책에서 정차 조건을 제거했습니다. 기관 역할 승인, 대상 세션·차량·임무 일치, 유효시간, 통신 최신성, 활성 화면 조건은 유지합니다.
- Android 클라이언트가 실제 상태 확인 없이 `parked: true`를 보내던 코드를 제거했습니다. 서버는 구버전의 선택적 `parked` 불리언을 호환용으로 수용하며 접근 제한에는 사용하지 않습니다.
- 화면 전환 후 복귀하면 최신 승인 상태를 다시 확인합니다. 이동 상태는 유지하며 정차 확인을 다시 요구하지 않습니다.
- Pleos 초기 부팅이 기존 4분 대기를 초과하는 사례를 재현해 런처의 최대 부팅 대기를 10분으로 늘렸습니다.
- AVD 중간 종료와 host GPU의 ColorBuffer 오류를 관찰해, 통합 런처의 렌더러를 이 PC에서 지원하는 SwiftShader로 변경했습니다.

이동 중 상세 조작은 동승자·관제 역할의 시연입니다. 실제 속도 감지·119/112 기관 연결·차량 등화 제어는 미연동입니다. 기본 정보공유 동의는 계속 OFF입니다.

## 검증 근거

- debug APK 빌드 및 Android 단위 테스트 36개 통과. 이전 정차 잠금 동작을 요구하던 2개 테스트는 제거하고 실제 화면 검증으로 대체했습니다.
- Android lint 오류·경고 0개.
- 서버 테스트 51개 통과. 승인 없는 활성화는 거부하며 정차 필드가 없거나 `false`인 승인된 모의 동작은 허용합니다.
- 실제 Chromium 웹 화면 검사 8개 통과: 초기 접근, 이동 중 상담 연결, 모든 이동 상태의 승인 표시 유지, 승인 철회, 매뉴얼 6개, 협력 차량 수락·승인·인계, 모바일 가로 넘침, JavaScript 예외.
- APK 서명은 Android Debug 인증서의 v2 서명으로 확인했습니다.

실행 로그와 화면:

- [최종 빌드 로그](../verification/v041-build.log)
- [서버 테스트 로그](../verification/v041-server-tests.log)
- [웹 검사](../verification/v041-web-e2e.json)
- [이동 중 탑승자 웹 화면](../verification/v041-web-moving-passenger.png)
- [협력 차량 승인 표시 웹 화면](../verification/v041-web-cooperation-active.png)
- [APK 서명](../verification/v041-apk-signature.txt)

Pleos API 34 AVD에서는 기본 앱 화면 검사 **19개**와 협력 차량의 변경 동작 **5개**를 확인했습니다. 기본 흐름에는 오프라인 매뉴얼 6개, 상담 시나리오 5개, 이동 상태 전환 후 표시 유지, 통신 장애·승인 철회·상담 종료 시 표시 OFF, 화면 복귀와 SDK 도어·배터리 조회가 포함됩니다. 협력 차량에서는 정차 선행 조건 없이 시작, 명시적 동의와 적합 차량 선택, 자발적 수락과 별도 임무 부여, 112 역할 승인 후 실제 점멸, 이동·정차 전환 후 승인 표시 유지를 확인했습니다.

**추가 회귀 제한:** 협력 차량의 전체 자동 검사는 설정 앱에서 복귀한 뒤 `No active accessibility root` 오류로 중단됐습니다. 해당 흐름의 후속 통신 장애·대조 조건·참여 철회·실제 120초 만료·새 승인·구급대 인계에 대한 새 APK의 추가 기기 회귀는 완료로 기록하지 않습니다. 웹에서는 협력 차량 수락·승인·인계를 확인했고, 서버 단위 테스트에서는 만료·철회·인계 조건을 검증했습니다. 전체 요약 상태는 `VERIFIED_WITH_LIMITATIONS`입니다.

설치본과 전달본 SHA-256은 `2e018a19eef854d8ff636cbcd01ccbd87e08da4ec329095f6c27360494e59272`로 일치합니다. 버전은 `0.4.1-demo` / versionCode 5, minSdk 28 / targetSdk 34입니다.

- [최종 검증 요약](../verification/v041-artifact-report.json)
- [기본 앱 19개 검사](../verification/v041-regression-android-e2e.json)
- [협력 차량 변경 동작 5개와 추가 검사 제한](../verification/v041-cooperation-e2e.json)
- [설치본·전달본 대조](../verification/v041-device.json)
- [이동 중 승인 표시 화면](../verification/v041-regression-android-moving-signal.png)
- [협력 차량 점멸 픽셀 대조](../verification/v041-coop-pulse-metadata.json)
- [최종 실행 화면 · 이동 중 시연 모드](../verification/v041-android-ready-final.png)

최종 재실행에서는 Pleos 앱 표시 영역에 흰 화면이 남는 현상도 관찰했습니다. 앱 로그의 SurfaceSyncGroup 지연을 보존했고, 테스트 에뮬레이터만 재부팅한 뒤 display 0에 새 태스크로 실행해 화면을 복구했습니다. 이후 실제 버튼 터치로 이동 중 시연 모드 전환을 확인했습니다. 최종 화면을 캡처했으며 검증 도구 패키지는 제거했습니다. 이 PC의 에뮬레이터 표시·접근성 연결은 완전히 안정화됐다고 기록하지 않습니다.

최종 화면 복구에 사용한 새 시연 시작 명령: `adb -s emulator-5580 shell am start -W --display 0 -f 0x10008000 -n com.lifelink119.app/.MainActivity`. 앱 내부의 모의 화면 상태를 새로 시작하는 명령입니다.

검증 도구의 첫 실행에서 기본 동작과 점멸 13개 항목은 통과했으나 후속 화면 탐색에서 실패했습니다. 해당 결과와 화면을 `verification/history-*`로 보존했습니다. 좌표 스와이프를 공개 접근성 스크롤 동작으로 바꾸고, 부하 시 정보가 누락되는 `dumpsys package` 대신 설치된 APK의 해시와 APK 메타데이터로 버전을 확인하도록 보정했습니다. 앱 APK를 변경하지 않고 완료한 [13개 부분 검사](../verification/v041-regression-baseline.json), 이후 [18개 부분 검사](../verification/v041-regression-safety-baseline.json)를 재사용해 기본 앱 19개 검사를 완료했습니다. 상담 종료 전에 승인이 이미 만료된 경우는 EXPIRED·OFF도 정상 종료 상태임을 서버 응답과 실제 화면으로 확인했습니다. 관련 근거는 [종료 시 표시 OFF 확인](../verification/v041-closed-signal-proof.json)에 있습니다.

Pleos의 앱 화면이 별도 표시 영역으로 이동하는 경우를 처리하도록 검증 도구에 전체 디스플레이 조회와 개별 View ID 조회를 추가했습니다. 도구의 화면 좌표 및 가시성 판정은 실제 PNG 화면과 대조했습니다. 이 보정과 관련된 중간 실패 기록도 보존했으며 TalkBack 전체나 실차 접근성 검증을 완료했다고 주장하지 않습니다.

## 재현 명령

`Build-And-Test.cmd`로 빌드·단위 테스트를 실행합니다. Python Playwright와 Chromium이 설치된 환경에서 `python scripts/smoke-moving-web.py`로 웹을 검사합니다.

Pleos AVD와 로컬 서버는 `Run-Full-Demo.cmd`로 시작합니다. JDK 17 환경에서 `python scripts/prepare-ui-snapshot.py --serial emulator-5580`로 별도 검증 도구를 설치한 뒤 아래 검사를 순서대로 실행합니다.

```powershell
python scripts/smoke-v03.py --serial emulator-5580 --expected-version 0.4.1 --expected-apk-version 0.4.1-demo --output-prefix v041-regression --moving-demo --persistent-ui
python scripts/smoke-v04-cooperation.py --serial emulator-5580 --expected-apk-version 0.4.1-demo --output-prefix v041 --moving-demo
```

이전 정차 제한이 있던 버전의 APK·검증 기록과 변경 전 소스 ZIP은 보존했습니다. 이전 실행 서버의 가상 기록도 재시작 전에 `verification/before-v041-server-restart-*.json`으로 따로 저장했습니다.
