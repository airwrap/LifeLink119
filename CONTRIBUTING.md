# LifeLink 119 함께 개발하기

비공개 저장소: <https://github.com/airwrap/LifeLink119>

먼저 [새 PC 실행 가이드](docs/OTHER_PC_GUIDE.md), `AGENTS.md`, `docs/CODEX_PROMPTS.md`를 읽습니다. 현재는 가상 데이터 PoC이며 실제 신고·기관 연결·실차 안전 검증을 완료한 서비스가 아닙니다.

## 작업 순서

1. 이슈에 목적·변경 범위·완료 조건을 적고 담당자를 정합니다. 기존 번호는 [개선 항목](docs/reviews/20261030/README.md)을 참조합니다.
2. 최신 main에서 작업 브랜치를 만듭니다.

```sh
git switch main
git pull --ff-only
git switch -c feature/short-task-name
```

3. 변경한 영역을 검사하고 작은 단위로 커밋합니다. 커밋 전 `git diff --cached`로 공유할 내용을 확인합니다.

```sh
git add <변경한-파일>
git commit -m "Describe the change"
git push -u origin feature/short-task-name
```

4. GitHub에서 main 대상 Pull Request를 만들고 변경 목적·테스트 결과·미검증 범위를 적습니다. 팀원이 검토하고 CI를 확인한 뒤 병합합니다. main 직접 push·공유 브랜치 강제 push는 피합니다. 문서의 검토 절차가 서버의 강제 보호 규칙 설정을 의미하지는 않습니다.

`MainActivity.kt`와 `ui/ExpansionUi.kt`는 동시 수정 시 미리 조율합니다. `core` 정책, `data` 어댑터/개인정보 보호, `vehicle` SDK, Android 화면, Python/웹, 문서·검증으로 역할을 나눌 수 있습니다.

## 필요한 검사

- Android: `Build-And-Test.cmd` 또는 JDK 17에서 `./gradlew :app:assembleDebug :app:testDebugUnitTest`.
- Python/웹·계약: `python -m unittest discover -s console -p 'test_*.py' -v`, `python scripts/validate-demo-contract.py`.
- 인증·차량 제한·화면 변경은 전용 Pleos AVD에서 별도 확인합니다. JVM 테스트가 시스템 인증·실차 검증을 대신하지 않습니다.
- CI는 빌드·단위 테스트이며 화면·실차 검증 성공으로 표현하지 않습니다.

## 공유 범위

소스·테스트·문서·Gradle wrapper를 관리합니다. local.properties, .env, 키·토큰·서명키, SDK/JDK/AVD, IDE·빌드 캐시, 가상환경, APK, 로컬 검증 기록은 공유하지 않습니다. APK는 필요할 때 별도 검토한 배포 파일로 전달합니다. Git 제외 규칙만으로 모든 비밀이 탐지되지는 않으므로 커밋 내용을 확인합니다.

실제 환자·가족·연락처를 fixture·화면·로그·이슈에 넣지 않습니다. 비밀 설정이 필요하면 값 없는 `.env.example` 등 예제만 공유합니다. 기본 PIN을 앱에 추가하지 않습니다.

모든 119/112/109·영상·차량 등화·기관 승인 흐름은 모의 시연 범위를 유지합니다. 실제 CarUX 제한을 토글로 해제하지 않습니다. 새 기능은 `AGENTS.md`, `docs/MEDICAL_PRIVACY.md`를 따릅니다.
