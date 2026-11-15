# 안심콜 공식 입력 항목 대조 및 보완

2026-11-13. 사용자 제공 이미지 5장과 154,451바이트의 등록 화면 HTML을 기준으로 대조했다. HTML은 로그인 요청 코드가 아닌 렌더된 등록 양식이며 스크립트와 form action은 포함하지 않는다. 사용자 계정, 인증 문자, 쿠키 또는 실제 의료정보를 테스트에 사용하지 않았다.

## 확인 결과

수정 전 앱은 이름·기존 병력 메모·알레르기·투약·등록 연락처·본인/대리인 관리만 보관했다. 제공된 공식 양식 전체가 구현된 상태는 아니었다. 이번에 아래 항목을 모두 직접 입력·암호화 보관·인증 후 재조회할 수 있도록 추가했다. 화면은 제공 이미지와 같은 순서의 5개 구역을 하나의 스크롤 양식으로 보여준다.

| 이미지 순서 | 공식 양식의 입력 항목 | 현재 구현 |
|---|---|---|
| 1 | 신청유형·상세, 거동여부, 혈액형 2칸, 승강기, 통화 가능 여부, 통화 불가 사유 | `SafeCallDetails`에 각각 저장. 혈액형은 ABO/Rh 구분. 승강기·통화 여부는 미입력/예/아니요를 구분 |
| 2 | 과거질환·수술력, 현재질환 목록, 복용약물, 법정감염병, 진료기관명·진료과목·주간 연락처 | 과거질환 별도 항목, 현재질환/감염병 복수 항목 추가·삭제, 기존 투약 항목 유지, 기관 정보 별도 저장 |
| 3 | 야간 연락처, 장애유형 목록, 불편사항, 특이체질, 구급대원 요구사항, 자주 가는 곳 | 각 항목 및 복수 장애유형 보관. 자주 가는 곳 15자 제한 |
| 4 | 보호자 목록, 성명, 관계 2단계, 휴대폰 | 복수 보호자 추가·삭제 및 관계 구분/상세 저장 |
| 5 | 보호자 전화, 이메일, 동거/별거, SMS 수신, 초기화/추가 | 보호자별 저장. SMS 미입력/예/아니요 구분. 전체 초기화는 확인 후 초안에만 반영하고 실제 저장은 저장 버튼에서 수행 |

제공 HTML의 안정적인 항목 ID 접미사와 대응되는 속성은 아래와 같다. WebSquare가 동적으로 붙인 UUID는 앱의 식별자로 사용하지 않는다.

| 공식 DOM ID 접미사 | 앱 데이터 |
|---|---|
| `sbx_propTp`, `sbx_propTpCode` | `applicationType`, `applicationDetail` |
| `sbx_bhaveSeCode`, `sbx_bldtyCd`, `sbx_bldtySeCd` | `mobility`, `bloodGroup`, `rhesus` |
| 승강기/통화 라디오, `ibx_tlphonImposblRsn` | `elevator`, `canCall`, `callUnavailableReason` |
| `ibx_hstcsInfoCn`, 현재질환 목록, `ibx_takngDrfstfCn` | `pastHistory`, `currentConditions`, 기존 `medicationHistory` |
| `ibx_contDissNm` | `infectiousDiseases` |
| `ibx_mdlcnstNm`, `sbx_mdlcnstMdexmSbjctCd`, `ibx_mediInstTelNum*`, `ibx_mediInstTelNumNight*` | `facilityName`, `specialty`, `dayPhone`, `nightPhone` |
| 장애 목록, `ibx_incnvnncMttr`, `ibx_excptnPhysgCn` | `disabilities`, `discomfort`, `constitution` |
| `ibx_relifMemberDemandMttr`, `ibx_rcntVstRgnNm` | `responderRequests`, `frequentPlace` |
| `ibx_flnm`, `sbx_protecLv1`, `sbx_protecLv2` | 보호자 `name`, `relationCategory`, `relation` |
| `ibx_moblphon*`, `ibx_tlphon*`, `ibx_eml*` | 보호자 `mobile`, `phone`, `email` |
| `rad_habSeCd`, `rad_smsRcptnCd` | 보호자 `living`, `smsPreference` |

## 공식 화면과 동일하다고 주장하지 않는 범위

- HTML에는 닫힌 선택 상자의 전체 옵션·기관 코드표·현재질환/장애 선택 팝업의 전체 내용이 없다. 신청유형·거동·과목·관계·질환·장애는 공식 화면의 명칭을 직접 입력해 보관한다. 공식 코드나 선택 목록을 임의로 만들어 넣지 않았다.
- 3칸 전화번호와 2칸 이메일은 차량 화면에서 한 입력칸으로 합쳤다. 저장 시 형식을 검사한다.
- 공식 법정감염병 선택/정보 조회 대신 확인된 명칭을 직접 보관한다. 법정감염병 해당 여부나 환자 상태를 앱이 판정하지 않는다.
- HTML에 숨김/비활성 상태로 들어 있는 대리인 자격증 첨부, 수혜자 평가, 관리자·등록자·일련번호 등은 사용자 제공 5개 화면의 입력 항목으로 복제하지 않았다. 기존 본인/대리인 구분과 관리 권한 확인은 유지했다.
- 목록당 16개, 항목명 120자, 보호자 8명은 이 차량 앱의 보관 한도이며 공식 서비스의 제한을 의미하지 않는다. 전체 암호화 평문 400,000바이트 상한도 그대로 적용한다.
- 통화 불가를 선택하면 연락 가능한 보호자를 입력하고 확인해야 저장한다. 이 검증은 건강정보 저장에만 적용하며 긴급 연결 시연을 막지 않는다.
- SMS 선호 저장으로 실제 문자 수신 신청이나 사고 자동 통지를 하지 않는다.

## ID·휴대폰 인증과 가져오기

사용자가 설명한 ID·휴대폰 문자 인증 후 본인 등록정보 조회는 공식 사이트에서 수행하는 흐름이다. 브라우저로 [공식 로그인 화면](https://u119.nfa.go.kr/main/loginForm?menuCd=login)을 열어 ID 입력란과 로그인 메뉴를 확인했다. 실제 ID 입력·문자 발송·인증·회원 정보 조회는 하지 않았다.

추가로 [공식 알림마당](https://u119.nfa.go.kr/main/loginForm?menuCd=SCSBM010000)의 **2026-09-01 「개인정보의 본인전송요구에 관한 사항 알림」**을 열어 확인했다. 공지는 기본 등록정보와 병력·복용약·알레르기·보호자 연락처의 본인 수령 절차를 안내한다. 홈페이지 문의하기 또는 국민신문고에서 요구 항목과 전송방법을 신청하는 방식이다. 자동화 도구를 쓰는 대리 요청은 소방청과 미리 협의한 방식에 따라야 한다고 안내한다.

따라서 현재 구현을 **공식 로그인 → 차량으로 자동 가져오기 완료**로 표시하지 않는다. 현재 앱은 외부 공식 사이트 안내 + 수동 입력한 상세 항목의 암호화 보관·조회까지 제공한다. 자동 가져오기는 본인전송요구/승인된 연계 방식, 제공 형식, 가족별 위임 확인, 수신 데이터 검증 및 저장 전 미리보기를 확보하는 후속 작업이다. HTML에 보이는 것만으로 비공개 로그인 요청이나 문자 인증 API를 추측해 구현하지 않았다. 자세한 구분은 [기관 연계 검토](SAFE_CALL_WEB_INTEGRATION_REVIEW.md)를 참고한다.

## 호환성과 검증

- 저장 형식 v3에 `safeCall`을 추가했다. v1/v2의 기존 내용은 그대로 읽고, 새 항목을 정상·없음 등으로 추정하지 않는다. 명시적으로 저장할 때만 v3로 쓴다.
- 새 항목도 같은 Android Keystore/AES-GCM 저장 경로를 이용한다. 로그인 정보·임시 초안·인증 문자를 디스크나 로그에 쓰지 않는다. 실제 119/PC 서버에 보내지 않는다.
- Kotlin 단위 검사 226개 통과. 모든 새 항목과 복수 보호자의 직렬화·복원, v2 이전, 미입력 구분, 연락처·목록 한도, 초안 유지와 민감 모델 로그 가림을 검사했다.
- 공식 Pleos 2560×1440 QA에서 새 양식 검사 8개 통과: 실제 입력, 검증 실패 후 유지, 반복 행 추가·삭제, 보호자 삭제 시 다른 정보 유지, 잠금/구성 변경, 암호화 보관, 앱 재실행 후 조회, 초기화 취소와 QA 데이터 정리. 로컬 증빙: `verification/safe-call-form/native-form.json`.
- 동일 APK에서 기존 UI 회귀 검사 17개도 다시 통과했다. 60초 인증 만료 후 입력 초안 복원과 기존 매뉴얼 복귀·가족별 삭제를 포함해 총 25개 UI 검사를 확인했다. 두 실행의 임시 PIN과 건강정보는 정리했다.
- debug/release/demo 빌드, debug/demo lint, Spotless 검사 통과. 공식 계정 로그인/자동 가져오기/실제 신고·SMS 전송은 검증 대상이 아니다.

새 APK는 별도의 QA AVD에 설치해 검증했다. 일상 시연 AVD의 APK 업데이트는 자동 승인 검사에서 `blocked by policy`로 거절되어 실행하지 못했다. 구체적인 차단 이유는 반환되지 않았다. 일상 AVD의 앱 데이터는 삭제하지 않았다. 아래 오디오 설정과 재기동은 일상 AVD에도 실제 적용했다.

## 에뮬레이터 마이크 오류

`virtio-snd-mic0`와 `DirectSoundCapture`는 Android Emulator가 Windows 녹음 장치를 초기화하는 호스트 오디오 경로의 오류다. 앱은 현재 실제 녹음·음성 출력을 하지 않으므로 다음 실행기에 `-no-audio`를 적용했다.

- `Start-Pleos-Emulator.cmd`
- `scripts/run-full-demo.ps1`, `scripts/start-qa-emulator.ps1`, `scripts/start-sdk-verification.ps1`
- 패키지 옆 `.tools/Start-LifeLink-Demo.cmd`

현재 공식 AVD의 `hw.audioInput`도 `no`로 바꾸고 이전 설정을 `.tools/logs/pleos-config-before-audio.ini`에 보관했다. 기존 사용자 데이터를 지우지 않고 에뮬레이터를 재기동했다. 새 로그 `.tools/logs/pleos-audio-fixed-out.log`, `pleos-audio-fixed-error.log`에서 해당 세 오류 문구가 0건임을 확인했다. 로그 필터로 숨긴 것이 아니다.

`-no-audio`는 마이크와 소리 출력을 모두 비활성화한다. 향후 실제 오디오 기능을 검증하려면 사용 가능한 Windows 녹음 장치를 준비하고 이 옵션을 제외해야 한다. 사용한 Emulator 37.2.13의 `-help-no-audio`와 [Android 공식 실행 안내](https://developer.android.com/studio/run/emulator-commandline)를 확인했다. 현재 버전에서 지원하지 않는 `-audio-in none`은 사용하지 않았다.
