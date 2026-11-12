# v0.18 화면과 검증 증거

- [구현·검증 범위](../../SAFE_CALL_IMPLEMENTATION_V18.md)
- [최종 검사 요약](verification-summary.json)
- [공식 SDK 값 비교 요약](sdk-summary.json)
- [공개 안내 캡처 결과](capture-results.json)

| 실제 APK 화면 | 파일 |
| --- | --- |
| 홈 | [01-home.jpg](01-home.jpg) |
| 수혜자 본인 안내 | [02-official-self.jpg](02-official-self.jpg) |
| 가족·대리자 안내 | [03-official-proxy.jpg](03-official-proxy.jpg) |
| 공식 사이트 연결 | [04-official-links.jpg](04-official-links.jpg) |

공개 프레임은 실행 중인 Android Activity의 View를 직접 그린 `native_view_draw` 방식이다. HTML로 재현한 화면이 아니다. 민감 화면의 FLAG_SECURE를 해제하지 않았으며 가족정보·연락처·PIN 화면의 프레임은 반환하지 않는다.

재검증은 전용 시험 AVD에서 진행한다. 기존 PIN·의료정보·사용자 편집 프로필이 있으면 새 기능 검사 스크립트가 중단한다. 개인 데이터가 있는 AVD에 맞추어 이 사전 검사를 제거하지 않는다. 테스트 PIN은 매번 무작위 생성하며 입력·저장·삭제 확인 과정에 로그로 남기지 않는다.

```powershell
# 기존 새 PC 실행 가이드의 JDK 17/SDK/Gradle 설정과 전용 AVD가 필요하다.
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
python scripts/prepare-ui-snapshot.py --serial emulator-5580
python scripts/verify-safe-call-v18.py --serial emulator-5580
python scripts/prepare-native-window.py --serial emulator-5580
python scripts/capture-safe-call-public-v18.py --serial emulator-5580
python scripts/verify-unslop-ui.py --serial emulator-5580 --revision v18 --avd LifeLink119_SafeCall_20261108
python scripts/verify-bright-ui-display.py --revision v18
```

위 명령 전에 현재 빌드 APK가 시험 AVD에 설치되어 있어야 한다. 스크립트는 빌드·설치 APK의 SHA-256을 대조한다. 공식 SDK 비교는 별도의 `LifeLink119_SDK_Compat_20261107` AVD에서 `scripts/verify-pleos-sdk-values.py --out verification/v18/sdk-final`을 사용하며, AVD·실제 SDK·공식 Console의 데이터 공급 연결을 확인한다. 자세한 준비는 [SDK 실행 안내](../../SDK_RUNTIME_VERIFICATION_V16.md)를 참고한다.

1280×720·1024×600은 실제 디스플레이 크기 변경과 ADB 터치 검사다. 520dp 검사는 앱 내부 너비 미리보기이며, 실제 차량 시험이나 모든 화면 크기의 검증으로 확대 해석하지 않는다.
