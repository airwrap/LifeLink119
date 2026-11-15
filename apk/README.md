# 제출 APK

- 저장소 주소: <https://github.com/airwrap/LifeLink119>
- 제출 페이지의 APK 경로: **`apk/LifeLink119_LifeLink119_release.apk`**
- 팀명 / 앱명: **LifeLink 119 / LifeLink 119**
- APK: [LifeLink119_LifeLink119_release.apk](LifeLink119_LifeLink119_release.apk)
- 체크섬: [LifeLink119_LifeLink119_release.apk.sha256](LifeLink119_LifeLink119_release.apk.sha256)
- 버전 `0.18.1` / versionCode `21` / 패키지 `com.lifelink119.app`
- 전용 인증서로 서명한 release, `debuggable=false`, 크기 151,935,970 bytes

파일 페이지에서 **Download raw file**을 눌러 실제 APK를 내려받습니다. 약 152 MB이므로 [Git LFS](https://docs.github.com/en/repositories/working-with-files/managing-large-files/about-git-large-file-storage)로 저장합니다. 소스 ZIP의 APK가 수백 바이트에 불과하고 `version https://git-lfs.github.com/spec/v1`로 시작한다면 실제 APK가 아닌 포인터입니다. 위 파일 페이지에서 다운로드하거나 다음 명령을 사용하세요.

```powershell
git lfs install
git clone https://github.com/airwrap/LifeLink119.git
cd LifeLink119
git lfs pull --include="apk/*.apk"
Get-FileHash .\apk\LifeLink119_LifeLink119_release.apk -Algorithm SHA256
```

SHA-256: `d013570c1224616a987c5f174cd29874181e282c765470ee707cf22fd6bcc095`

```powershell
# adb devices에서 확인한 serial로 바꿉니다.
adb -s emulator-5586 install --no-streaming -r .\apk\LifeLink119_LifeLink119_release.apk
adb -s emulator-5586 shell am start -n com.lifelink119.app/.MainActivity
```

원본은 [v0.18.1 Release](https://github.com/airwrap/LifeLink119/releases/tag/v0.18.1-contest-release)의 `LifeLink119-v0.18.1-contest-release.apk`와 동일합니다. 파일명과 제출 위치만 달라졌으며, 서명과 실행 검증 결과는 [제출본 검증 기록](../docs/SUBMISSION_RELEASE_20261115.md)에 있습니다. 기존 debug/demo 설치와 서명이 충돌하면 새 AVD를 사용하세요.

[전체 실행 방법·테스트 환경·Pleos 화면 규격](../README.md)
