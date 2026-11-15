# 공식 사이트 내장 웹 · v0.18.2-browser

공식 Pleos Connect v2.1.0 에뮬레이터에는 `ai.pleos.android.webview` 131.0.6752.0이 설치되어 있지만 HTTPS 링크를 처리하는 일반 브라우저 앱이 없다. 기존 `ACTION_VIEW` 실행 실패로 “이 기기에 연결 가능한 브라우저가 없습니다”가 표시되었다. 개발 버전은 공식 사이트를 앱 내부의 WebView 화면으로 연다.

## 사용 방법

1. 정차 상태에서 **더보기 → 119안심콜 공식 로그인·정보관리 → 119안심콜 공식 로그인·정보관리**를 연다. 가족 안심콜의 **119안심콜 공식 로그인·등록 안내**에서도 같은 안내 화면에 들어갈 수 있다.
2. 상단의 공식 도메인을 확인하고 사이트의 로그인 메뉴에서 직접 진행한다. 계정 입력·휴대전화 문자 인증은 공식 사이트가 처리한다.
3. 방문 이력이 있으면 **뒤로**로 이전 웹 화면을 보거나 **앱으로 돌아가기**로 닫는다. 안심콜처럼 주소를 바꾸지 않고 내용을 전환하는 화면에서는 사이트 안의 홈·메뉴를 사용한다. **공식 주소 복사**는 휴대전화·PC에서 사용할 공개 첫 주소만 복사한다.
4. 인터넷 오류가 나면 **사이트 처음부터 다시 열기**를 누른다. 로그인 세션이 새로 시작되며 이전 POST 요청을 자동으로 재전송하지 않는다.

현재 공개 로그인 문서의 ID 입력 → 등록 전화 확인 → 동일 사이트의 문자 인증 팝업 흐름을 확인했다. 실제 계정·인증번호로 로그인하거나 문자를 발송하는 검증은 하지 않는다. 외부 인증 앱 실행, 다른 도메인의 본인 인증, 파일 첨부·다운로드는 휴대전화·PC 이용 안내로 대체한다. 외부 인증 제공자의 모든 방식이 동작한다고 보장하지 않는다.

## 개인정보와 주행 제한

- 내장 웹 Activity는 외부 앱에서 실행할 수 없으며 별도 프로세스와 WebView 데이터 디렉터리를 사용한다. 앱에서 전달하는 값은 공식 사이트 종류뿐이다.
- 공식 HTTPS 호스트만 연다. 주소의 스킴·호스트·포트·사용자정보를 함께 검사하며, `tel:`, `sms:`, `intent:`, 파일·콘텐츠 접근과 혼합 HTTP 리소스를 차단한다. 웹 링크로 실제 긴급 발신을 시작하지 않는다.
- WebSquare 표시를 위해 JavaScript·DOM storage를 사용하지만 앱과 연결하는 JavaScript bridge, 폼 추출, 자격정보 자동 입력, 임의 요청 재현은 구현하지 않는다. 사이트의 상태를 앱의 가입·조회·연동 성공으로 처리하지 않는다.
- 웹 화면은 `FLAG_SECURE`로 보호한다. 화면을 닫거나 백그라운드로 전환하거나 주행 제한이 걸리면 WebView를 파괴하고 쿠키·캐시·웹 저장소를 정리한다. 비정상 종료 후에도 다음 진입 전에 남은 웹 세션을 정리한다. 이는 WebView의 임시 웹 세션이며 의료정보 암호화 저장소로의 가져오기가 아니다.
- SSL 오류는 연결을 취소한다. 웹 권한·위치·파일 요청은 허용하지 않는다. 웹 콘솔 메시지를 앱 로그로 복사하지 않으며, 표시·복사하는 주소에 방문 URL의 인증 쿼리를 넣지 않는다.
- 기존 `DrivingRestrictionsGateway`를 재사용한다. 차량 제한을 확인할 수 없거나 키보드·상세 조작·영상·콘텐츠 제한이 있으면 웹을 열지 않으며, 열린 상태에서 제한이 바뀌어도 세션을 종료한다. 시연용 정차 선택으로 실제 플랫폼 제한을 해제하지 않는다.

## 변경 파일

| 파일 | 역할 |
|---|---|
| `OfficialHealthNavigation.kt` | 공개 사이트 enum으로 내부 Activity 실행 |
| `OfficialHealthBrowserActivity.kt` | 웹 화면, 공식 도메인 표시, 팝업·오류·복귀, 세션 정리와 주행 제한 |
| `OfficialWebSettings.kt` | WebView의 공통 접근·저장·권한 설정 |
| `OfficialBrowserPolicy.kt` | Android와 분리한 주소·플랫폼 제한 정책 |
| `OfficialBrowserPolicyTest.kt`, `OfficialBrowserSettingsTest.kt` | 우회 주소·긴급 링크·제한 정책 및 실제 WebView 설정 검사 |

공식 Android 문서에 따라 일반 INTERNET 권한을 추가했다. 통화·문자·저장소·추가 차량 제어 권한은 추가하지 않았다. 제출 확정본은 `main`의 `3cae1de57e266815f93f13440c89d87bc3da54f3` 및 기존 `apk/` 파일로 유지한다. 이 개발 브랜치에서 기존 `Build-Release.cmd`를 실행하면 제출 경로로 복사하므로, 개발 검증 시에는 Gradle의 release 산출물을 별도 `artifacts/development/browser-20261116/`에 보관한다.

## 실행 검증 · 2026-11-16

- Debug 및 서명된 release 빌드 성공. 각 변형의 단위 테스트 **292개 통과**. `spotlessCheck` 통과, release lint 오류 0건이며 기존 테스트 의존성 버전 경고 2건이 남는다.
- Pleos Connect v2.1.0 / API 34 / 2560×1440 / `LifeLink119_Release_QA_API_34`에서 별도 개발 APK를 기존 데이터 삭제 없이 업데이트했다.
- 실제 WebView 설정·권한·별도 프로세스 Android 테스트 **2개 통과**. `dumpsys window`로 웹 화면의 `SECURE` 플래그를 확인했다.
- 실제 메뉴 조작으로 공식 첫 화면 → 로그인 → 빈 ID 입력란 표시를 확인했다. 앱 복귀와 재진입 시 새 공개 첫 화면을 여는 동작도 통과했다. 계정 입력·문자 발송·로그인 후 건강정보 조회는 검증하지 않았다.
- 제출 APK의 SHA-256은 `d013570c1224616a987c5f174cd29874181e282c765470ee707cf22fd6bcc095`로 변경되지 않았다. 개발 APK의 SHA-256은 `a87aad859c4a5ccb592707c1863fe4357b57bfabab1f24b8ddcc2b1df98a620c`이며 같은 release 인증서로 서명했다.

## 근거

- [Android WebView와 INTERNET·JavaScript 설정](https://developer.android.com/develop/ui/views/layout/webapps/webview)
- [안전한 URI 검사](https://developer.android.com/privacy-and-security/risks/unsafe-uri-loading)
- [SSL 오류 처리와 WebView 수명주기 콜백](https://developer.android.com/reference/android/webkit/WebViewClient)
- [소방청 119안심콜 공식 사이트](https://u119.nfa.go.kr/)

본인 정보의 차량 자동 가져오기, 공식 기관 데이터 전송과 인증 제공자의 사용 승인은 별도 연계 과제다.
