# 안심콜 웹 정보 가져오기 · v0.18.3-web-import

본인이 공식 웹에서 로그인·휴대전화 인증을 마친 뒤, **현재 열어 둔 병력정보 화면**을 사용자의 버튼 조작으로 읽는다. 확인한 정보는 기존 의료정보 저장소에 암호화 보관한다. 공식 API 연계, 서버 저장 여부 검증, 주기적 자동 동기화, 119 자동 전달과는 구분한다.

개발 브랜치 `feature/in-app-official-browser`의 기능이다. 제출 확정 커밋 `3cae1de57e266815f93f13440c89d87bc3da54f3` 및 `apk/` 제출 파일은 유지한다.

## 사용 순서

1. 정차 상태에서 **더보기 → 119안심콜 공식 로그인·정보관리**를 열고 공식 사이트 버튼을 누른다. **우리 가족 안심콜 → 공식 로그인·등록 안내**에서도 진입한다.
2. 공식 웹에서 직접 로그인하고 휴대전화 문자 인증을 완료한다. 내 병력정보·보호자 목록 화면이 로드될 때까지 기다린다.
3. 상단의 **내 정보 가져오기**를 누른다. 이름·휴대폰 번호가 가려져 있거나 인증 팝업이 열려 있으면 가져오지 않는다. 아이디·비밀번호·인증번호는 읽지 않는다.
4. 앱으로 돌아오면 의료정보 PIN/시스템 인증으로 잠금을 푼다. 가져온 항목·누락 항목을 확인하고 **본인 정보이며 보관에 동의**를 선택한다.
5. **확인한 정보 암호화 저장**으로 ‘내 정보’에 보관한다. **내용 보완 후 저장**을 누르면 기존 편집 화면에서 수정할 수 있다. 통화 불가능으로 표시된 사람은 통화 가능한 보호자 연락처를 확인해야 저장할 수 있다.
6. 이후 오프라인에서도 인증 후 **우리 가족 안심콜**에서 확인한다. 응급 요약에는 기존 흐름대로 이번 환자를 선택하고 사용에 동의해야 포함된다. 실제 건강정보는 외부 LLM·모의 상황실로 보내지 않는다.

웹에서 아직 서버에 저장하지 않은 수정 내용도 포함될 수 있으므로 사용자가 확인해야 한다. 기존 ‘내 정보’와 이름 또는 전화번호가 다르면 저장을 막는다. 다른 가족·대리인 기록을 자동 선택해 본인 정보와 합치지 않는다.

## 첨부 HTML에서 확인한 태그

2026-11-16 제공된 MHT의 WebSquare DOM을 검사했다. 아래 ID 앞에는 `mf_wfm_container_wfm_userInfo_`가 붙는다. 원본 MHT·인증 화면·개인정보는 Git과 테스트 자료에 복사하지 않았다.

| 항목 | 확인한 DOM 구조 |
|---|---|
| 본인 이름·휴대폰 | `ibx_name`, `ibx_phone1/2/3`의 현재 `.value` |
| 신청유형·거동 | `sbx_propTp`, `sbx_propTpCode`, `sbx_bhaveSeCode` 내부 `_label` |
| 혈액형·Rh | `sbx_bldtyCd_label`, `sbx_bldtySeCd_label`; 명시적으로 인식한 표시값만 매핑 |
| 승강기·통화 가능 | 제목이 일치하는 `.form-group`의 선택된 라디오와 연결된 `label`. 가변 `wq_uuid` 숫자에 의존하지 않음 |
| 통화불가 사유·과거질환·복용약 | `ibx_tlphonImposblRsn`, `ibx_hstcsInfoCn`, `ibx_takngDrfstfCn` |
| 현재질환·장애 목록 | `gen_setDiss`/`gen_firstDiss`, `gen_setFirstDsblty`/`gen_firstDsblty` 내부에서 구조를 확인할 수 있는 선택 항목 |
| 감염병·의료기관·진료과 | `ibx_contDissNm`, `ibx_mdlcnstNm`, `sbx_mdlcnstMdexmSbjctCd_label` |
| 주간·야간 연락처 | `ibx_mediInstTelNum1/2/3`, `ibx_mediInstTelNumNight1/2/3` |
| 기타 참고사항 | `ibx_incnvnncMttr`, `ibx_excptnPhysgCn`, `ibx_relifMemberDemandMttr`, `ibx_rcntVstRgnNm` |
| 보호자 목록 | `grd_list_body_tbody`의 실제 행. `col_id`가 `flnm`, `relNm`, `mobAll`, `tlpAll`, `eml`, `habSeNm`, `smsRcptnNm`인 셀만 읽음 |

MHT에는 입력란의 현재 값이 HTML `value` 속성으로 남아 있지 않았다. 따라서 MHT 자체를 의료정보 파일로 가져오지 않고 **실행 중인 WebView의 `.value`와 선택 상태**를 읽는다. 보호자 추가 입력 중인 폼, 숨겨진 템플릿 행, 생년월일·주소·자격증 번호·로그인 필드는 제외한다.

## 누락·오류·기존 정보

- 비어 있거나 읽지 못한 항목으로 기존 저장값을 지우지 않는다. 기존 병력 메모·알레르기는 유지한다.
- 질환·장애·보호자 목록은 기존 목록과 합친다. 보호자는 이번에 가져온 연락처를 먼저 표시하며, 저장될 기존 연락처도 확인 화면에 함께 표시한다. 화면에 로드된 행만 읽으므로 가상 스크롤·페이지 분할의 전체 조회를 보장하지 않는다. 전체 목록과 중복/이전 항목은 사용자가 확인하고 편집 화면에서 정리한다.
- 첨부 MHT에는 값이 채워진 질환/장애 동적 행이 없었다. 해당 컨테이너의 선택 라벨 구조가 다르면 누락으로 표시한다. 임의 주변 텍스트를 질환으로 해석하지 않는다.
- 알 수 없는 선택값, 가려진 연락처, 한도 초과, DOM 변경, 중복 ID, 인증 팝업은 실패 또는 누락으로 처리한다. 저장 버튼을 누르기 전에는 기존 암호문이 바뀌지 않는다.
- 취소·홈 이동·프로세스 재시작 시 가져오기 결과를 버린다. 공식 웹 진입부터 10분이 지나면 다시 가져와야 한다. 인증 만료 시 내용을 가리고 재인증을 요구한다.
- 가져온 시각·항목과 이후 직접 수정 여부를 기록한다. ‘공식 등록 완료’는 웹 화면을 읽었다는 이유로 자동 설정하지 않는다.

## 구현과 보호

| 파일 | 역할 |
|---|---|
| `assets/safe-call-extract.js` | 정확한 HTTPS 출처 검사, 허용한 태그만 읽는 DOM 추출기; 네트워크 호출·웹 폼 제출 없음 |
| `data/SafeCallWebImport.kt` | JSON 타입·필드·길이·선택값 검사, 기존 정보 보존, 본인 식별 충돌 검사 |
| `data/SafeCallImportSession.kt` | 일회성 nonce·10분 만료·메모리 전용 암호화 Activity 결과 |
| `ui/OfficialHealthBrowserActivity.kt` | 사용자 버튼으로 추출, 이동/백그라운드/제한 변경 때 진행 중 응답 무효화 |
| `ui/SafeCallImportReviewUi.kt`, `ui/ExpansionUi.kt` | 기존 의료정보 인증·정차 제한, 저장 전 확인·수정·취소 |
| `data/PersonalHealthRecord.kt` | 출처를 포함한 저장 형식 v4; v1~v3 읽기 유지, 기존 AES-GCM/Keystore 저장 재사용 |
| `data/EmergencyHealthSummary.kt` | 사용자가 선택한 정보의 출처·가져온 시각을 인계 요약에 표시 |

`evaluateJavascript`의 반환만 사용하고 `addJavascriptInterface`는 추가하지 않는다. 브라우저와 앱 사이에는 AES-GCM 암호문만 전달한다. 평문 Intent/파일/저장 상태/로그/클립보드를 사용하지 않는다. 외부 AI에 데이터를 보내거나 비공개 로그인 요청을 재현하지 않는다. 기존 `FLAG_SECURE`, HTTPS 제한, 주행 제한, 쿠키·캐시 정리를 유지한다.

## 검증 범위와 재실행

합성 DOM을 실제 Android WebView에 로드하고 네트워크를 차단한 상태에서 추출·검증·암호화·확인 화면·저장을 검사했다. 실제 계정이나 문자 인증번호는 입력하지 않았다. **실제 로그인 완료 후 개인 병력 조회 → 가져오기까지의 전체 검증은 아직 하지 않았다.** 사이트의 양식 변경이나 다른 인증 제공자 화면은 추가 대응이 필요할 수 있다.

2026-11-16 실행 결과:

| 검사 | 결과 |
|---|---|
| Debug / 서명된 release 빌드 | 성공, versionCode 23 / `0.18.3-web-import` |
| Kotlin 단위 테스트 | Debug·Release 각각 **302개 통과** |
| 실제 WebView·암호화 저장·확인 UI·의료정보 잠금 | Debug·Release 각각 **9개 통과** |
| 공식 사이트 실제 조작·미인증 가져오기 차단·네트워크 장애 복구 | **7개 통과**, 실제 ID·문자 입력 없음 |
| 기존 5단계 건강정보 입력·보호자·암호화·재인증·재시작·취소 | **8개 통과**, 전용 QA 데이터·임시 PIN 정리 확인 |
| Kotlin 포매터 | `spotlessCheck` 통과 |
| Debug·Release lint | 오류 0, 기존 의존성 업데이트 경고 2 |

Pleos Connect v2.1.0 / API 34 / 2560×1440에서 검사했다. 전용 QA `emulator-5584`는 합성 데이터만 사용하고, 일상 확인용 `emulator-5586`에는 기존 데이터 삭제 없이 같은 인증서의 개발 release를 업데이트했다. 저장 왕복 Android 테스트는 별도 임시 preference를 사용한다.

개발 APK는 로컬 `artifacts/development/web-import-20261116/LifeLink119-v0.18.3-web-import-release.apk`에 있다. `DEBUG=false`, 기존 release 인증서 서명을 확인했다. SHA-256: `d3c1464d8b5063c4ac7d505452598eb34ade5dff2056f5d68b0a664739f946d9`. 제출 `apk/` 파일의 SHA-256은 `d013570c1224616a987c5f174cd29874181e282c765470ee707cf22fd6bcc095`로 유지한다.

```powershell
.\gradlew.bat spotlessCheck :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
# 전용 QA AVD에 설치한다. 일상 AVD의 기존 정보를 초기화하지 않는다.
adb -s emulator-5584 install --no-streaming -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5584 install --no-streaming -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s emulator-5584 shell am instrument --user 10 -w -e class com.lifelink119.app.SafeCallWebImportTest,com.lifelink119.app.OfficialBrowserSettingsTest com.lifelink119.app.test/androidx.test.runner.AndroidJUnitRunner
python scripts/verify-official-browser.py --serial emulator-5584
python scripts/verify-safe-call-form.py --serial emulator-5584
```

`--user`는 QA 기기의 `am get-current-user` 값에 맞춘다. Android 검사는 개인 저장소를 삭제하지 않으며 저장 왕복 검사는 별도 임시 preference에서 수행한다. 운영 소스에는 테스트용 가져오기·인증 우회 경로가 없다.

본인 웹화면 복사는 기관이 승인한 API 연계가 아니다. 상용 배포 시 사이트 이용조건·자동화 허용 범위와 기관 제공 절차는 별도로 확인해야 한다. [공식 사이트](https://u119.nfa.go.kr/), [Android WebView 문서](https://developer.android.com/reference/android/webkit/WebView).
