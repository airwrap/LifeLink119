# LifeLink 119 전체 기능 발표 메모 · v0.11

표지는 AI 생성 콘셉트 이미지입니다. 실제 앱 화면은 전용 에뮬레이터의 설치된 v0.11 APK에서 캡처했습니다. 의료정보 보호 화면은 소스 기준 구조도이며 실제 캡처가 아닙니다.

## 1장

LifeLink 119의 전체 기능을 실제 앱 화면과 함께 설명합니다. 표지는 서비스 콘셉트를 표현한 AI 생성 이미지입니다.

근거: 기존 발표자료 표지; README.md

## 2장

나와 가족을 위한 시작 화면
119 구조·구급, 상담, 112, 우리 가족 안심콜을 목적에 따라 선택합니다.

근거: ExpansionUi.homePage; 앱 화면 01-home; APK 51d5bec8061983321ddd80d132e072da78eca849aa7d7ca1331b6bc63bf04545

## 3장

등록 없이 한 번에 시작
차 안에서 위급한 상황이 발생하면 정차 확인이나 의료정보 인증을 기다리지 않고 모의 요청을 시작합니다.

근거: ExpansionUi.drivingPage; DrivingEmergency.kt; 앱 화면 02-driving; APK 51d5bec8061983321ddd80d132e072da78eca849aa7d7ca1331b6bc63bf04545

## 4장

현재 상황을 함께 확인
상황 전달과 확인 절차를 보여줍니다. 위치와 차량 정보는 별도 선택이며 실제 GPS는 사용하지 않습니다.

근거: DrivingEmergency.kt; ExpansionUi.drivingPage; 앱 화면 03-driving-sent; APK 51d5bec8061983321ddd80d132e072da78eca849aa7d7ca1331b6bc63bf04545

## 5장

요청 후 동의를 선택
가상의 영상 안내를 시작하기 전에 합성 장면 사용 범위를 확인합니다.

근거: ExpansionUi.guidedVideoPage; 앱 화면 04-video-consent; APK 51d5bec8061983321ddd80d132e072da78eca849aa7d7ca1331b6bc63bf04545

## 6장

가상 담당자의 안내
합성 영상과 고정 안내 문구를 보여줍니다. 실제 카메라, 음성 통화, 영상 전송은 없습니다.

근거: ExpansionUi.guidedVideoPage; 앱 화면 05-video-guide; APK 51d5bec8061983321ddd80d132e072da78eca849aa7d7ca1331b6bc63bf04545

## 7장

목적에 따라 선택
119 응급의료상담과 109 심리위기 상담을 구분합니다. 현재 연결과 상담원 응답은 앱 안의 모의 기능입니다.

근거: ExpansionUi.entry; 앱 화면 07-consult-choice; APK 51d5bec8061983321ddd80d132e072da78eca849aa7d7ca1331b6bc63bf04545

## 8장

필요한 지원을 선택
가상 상담원 응답 후 도움받는 사람과 추가 지원을 고를 수 있습니다. 가족 등록은 시작 조건이 아닙니다.

근거: ExpansionUi.sessionPage; 앱 화면 08-consult; APK 51d5bec8061983321ddd80d132e072da78eca849aa7d7ca1331b6bc63bf04545

## 9장

공유 여부를 직접 선택
가상 위치와 차량 정보를 선택적으로 공유하고 철회합니다. 실제 GPS나 차량 식별정보를 보내지 않습니다.

근거: ExpansionUi.locationPage; 앱 화면 09-location; APK 51d5bec8061983321ddd80d132e072da78eca849aa7d7ca1331b6bc63bf04545

## 10장

선택한 항목만 전달
버튼을 눌러 가상의 위치를 선택해 공유합니다. 실제 위치 조회나 기관 전송은 이루어지지 않습니다.

근거: ExpansionUi.locationPage; 앱 화면 10-location-shared; APK 51d5bec8061983321ddd80d132e072da78eca849aa7d7ca1331b6bc63bf04545

## 11장

상황에 맞게 더하기
상황 변경, 문자·수어, 위치, 협력 요청 등 현재 연결에 맞는 추가 기능으로 이동합니다.

근거: ExpansionUi.supportPage; 앱 화면 11-support; APK 51d5bec8061983321ddd80d132e072da78eca849aa7d7ca1331b6bc63bf04545

## 12장

119와 구분된 진입 경로
홈에서 112를 별도로 선택할 수 있습니다. 경찰 발신이나 실제 출동은 연결되지 않습니다.

근거: ConnectionTarget.EMERGENCY_112; ExpansionUi.sessionPage; 앱 화면 12-112; APK 51d5bec8061983321ddd80d132e072da78eca849aa7d7ca1331b6bc63bf04545

## 13장

말하기 어려울 때
정해진 문구를 선택해 앱 내 가상 상황실에 표시합니다. 실제 SMS 발송은 없습니다.

근거: ExpansionUi.textPage; 앱 화면 13-text; APK 51d5bec8061983321ddd80d132e072da78eca849aa7d7ca1331b6bc63bf04545

## 14장

화면에서 내용 확인
사용자가 고른 예시 문구와 가상 답장을 앱 안에서 확인합니다.

근거: ExpansionUi.textPage; 앱 화면 14-text-sent; APK 51d5bec8061983321ddd80d132e072da78eca849aa7d7ca1331b6bc63bf04545

## 15장

별도의 가상 검토 절차
가상 경찰 역할의 요청·검토·승인 또는 거절을 보여줍니다. 실제 출동, 호송이나 우선통행 권한은 없습니다.

근거: ExpansionUi.policePage; 앱 화면 15-police; APK 51d5bec8061983321ddd80d132e072da78eca849aa7d7ca1331b6bc63bf04545

## 16장

상담 경로를 따로 선택
109 상담은 프라이버시를 우선하는 모의 연결입니다. 실제 전문 상담원과 통화하지 않습니다.

근거: ConnectionTarget.COUNSEL_109; 앱 화면 16-109; APK 51d5bec8061983321ddd80d132e072da78eca849aa7d7ca1331b6bc63bf04545

## 17장

상황에 맞는 지원
문자, 수어 연결 모형, 위치 등 해당 예시에 맞는 기능을 우선 표시합니다.

근거: experience-catalog.json S08; 앱 화면 17-hearing; APK 51d5bec8061983321ddd80d132e072da78eca849aa7d7ca1331b6bc63bf04545

## 18장

3자 연결 상태 표현
사용자·가상 상담원·가상 통역사의 연결 상태만 표현합니다. 실제 영상·수어 통역은 아닙니다.

근거: ExpansionUi.signPage; 앱 화면 18-sign; APK 51d5bec8061983321ddd80d132e072da78eca849aa7d7ca1331b6bc63bf04545

## 19장

정보를 짧은 단계로
인지 접근성이 필요한 상황에 맞춰 간단한 안내를 제공합니다.

근거: ExpansionUi.easyPage; experience-catalog.json S12; 앱 화면 19-easy; APK 51d5bec8061983321ddd80d132e072da78eca849aa7d7ca1331b6bc63bf04545

## 20장

다양한 가족과 상황
어린이·고령자·임산부·장애 접근성·교통사고·주행 중 위급 상황 등의 예시를 선택합니다.

근거: experience-catalog.json; ExpansionUi.gallery; 앱 화면 20-gallery; APK 51d5bec8061983321ddd80d132e072da78eca849aa7d7ca1331b6bc63bf04545

## 21장

큰 글씨와 쉬운 안내
큰 글씨와 짧은 안내를 선택합니다. 화면 읽기는 기기의 시스템 설정을 사용합니다.

근거: ExpansionUi.accessPage; 앱 화면 21-access; APK 51d5bec8061983321ddd80d132e072da78eca849aa7d7ca1331b6bc63bf04545

## 22장

입력값에 따른 가정 계산
배터리 용량, 잔량과 부하 등을 입력해 이용 가능 시간을 계산합니다. 실제 V2L 제어는 아닙니다.

근거: ExpansionUi.powerPage; 앱 화면 22-power; APK 51d5bec8061983321ddd80d132e072da78eca849aa7d7ca1331b6bc63bf04545

## 23장

도어와 배터리 조회
지원 환경에서 Pleos Vehicle SDK의 읽기 진단을 확인합니다. 실제 차량 제어나 응급정보 전송은 하지 않습니다.

근거: MainActivity vehicle diagnostic; README.md; 앱 화면 23-sdk; APK 51d5bec8061983321ddd80d132e072da78eca849aa7d7ca1331b6bc63bf04545

## 24장

오프라인 학습용 안내
기본 대응 항목을 앱에서 확인합니다. 의료진 검토 전 학습용 자료이며 실제 상황에서는 119 안내가 우선입니다.

근거: MainActivity manual page; 앱 화면 24-manual; APK 51d5bec8061983321ddd80d132e072da78eca849aa7d7ca1331b6bc63bf04545

## 25장

가상 위치 공유 후 시작
동의를 선택한 뒤 주변 차량이 도움에 참여하는 오프라인 가상 절차를 시작합니다.

근거: ExpansionUi.cooperationPage; 앱 화면 25-coop-consent; APK 51d5bec8061983321ddd80d132e072da78eca849aa7d7ca1331b6bc63bf04545

## 26장

자발적 참여가 먼저
가상 차량의 참여 가능 조건을 확인하고 초대·수락하는 흐름을 보여줍니다.

근거: ExpansionUi.cooperationPage; CooperationPolicy; 앱 화면 26-coop-review; APK 51d5bec8061983321ddd80d132e072da78eca849aa7d7ca1331b6bc63bf04545

## 27장

기관 역할의 별도 검토
가상 119 또는 112 역할의 확인과 한시 승인을 거쳐 화면 표시를 시작하도록 구성했습니다. 이 단계는 최신 화면을 확보하지 못해 구조도로 설명합니다.
해당 단계 캡처 미확보; 소스 기준 구조도로 설명합니다.

근거: ExpansionUi.cooperationPage

## 28장

앱 화면의 표현
승인 후 빨간 화면 표시 모형을 보여줍니다. 실제 차량 등화 제어나 긴급차 권한은 없습니다. 이 단계는 구조도로 설명합니다.
해당 단계 캡처 미확보; 소스 기준 구조도로 설명합니다.

근거: ExpansionUi.cooperationPage

## 29장

문장 강조 예시
음성 도움말은 화면에서 문장을 강조하는 방식입니다. 실제 마이크, 음성 인식이나 음성 재생은 연결되지 않았습니다.

근거: ExpansionUi.voicePage; 앱 화면 29-voice; APK 51d5bec8061983321ddd80d132e072da78eca849aa7d7ca1331b6bc63bf04545

## 30장

가상 안내와 사용자 확인
가상 전문가의 안내와 위치 공유 뒤 목적지를 제안·확인합니다. 실제 내비게이션이나 병원 수용 여부 조회는 없습니다.

근거: ExpansionUi.navigationPage; 앱 화면 30-navigation; APK 51d5bec8061983321ddd80d132e072da78eca849aa7d7ca1331b6bc63bf04545

## 31장

정해진 예시 버튼
가상의 음성 요청 문장을 눌러 119 요청 흐름을 시작합니다. 실제 음성 인식은 사용하지 않습니다.

근거: ExpansionUi.drivingSetup; 앱 화면 31-driving-voice; APK 51d5bec8061983321ddd80d132e072da78eca849aa7d7ca1331b6bc63bf04545

## 32장

인증과 잠금 관리
시스템 인증 또는 직접 설정한 앱 PIN을 사용합니다. 정보 삭제·초기화도 이 화면에서 선택합니다.

근거: ExpansionUi.securityPage; MedicalPrivacyManager; 앱 화면 32-security; APK 51d5bec8061983321ddd80d132e072da78eca849aa7d7ca1331b6bc63bf04545

## 33장

인증 후 내 정보 관리
본인이 직접 입력한 병력, 알레르기와 투약 내역을 기기에 암호화해 보관합니다. 실제 119 안심콜에는 등록되지 않습니다.
개인정보 보호로 캡처 제한; 소스 기준 구조도로 설명합니다.

근거: ExpansionUi.personalHealthPage; docs/PERSONAL_HEALTH_V10.md

## 34장

동의 후 예시 화면 확인
가입과 데이터 제공 동의를 선택하는 형태를 보여줍니다. 실제 가입·본인 확인·의료정보 전송은 없습니다.
개인정보 보호로 캡처 제한; 소스 기준 구조도로 설명합니다.

근거: ExpansionUi.healthLinkPage; docs/HEALTH_LINK_EXAMPLE_V11.md

## 35장

가상의 기록으로 설명
예시 약품과 날짜만 보여주며 실제 의료기관 조회 결과가 아닙니다. 동의 철회 또는 앱 전환 시 예시 동의는 초기화됩니다.
개인정보 보호로 캡처 제한; 소스 기준 구조도로 설명합니다.

근거: ExpansionUi.healthExamplePage; docs/HEALTH_LINK_EXAMPLE_V11.md

## 36장

필요한 항목만 동의
가족 예시의 병력·복용약·알레르기 등은 인증 후 항목별로 선택하고 철회합니다. 직접 입력한 내 정보는 자동 공유되지 않습니다.
개인정보 보호로 캡처 제한; 소스 기준 구조도로 설명합니다.

근거: ExpansionUi.cardPage; PersonalHealthRecord.kt

## 37장

검토 후 선택적으로 공유
관찰 내용과 발견 시각 등을 사용자가 확인하고 수정합니다. 규칙 기반 요약이며 진단이나 위험도 판정은 하지 않습니다.
개인정보 보호로 캡처 제한; 소스 기준 구조도로 설명합니다.

근거: ExpansionUi.summaryPage; Experience.kt

## 38장

추가 지원은 별도로 선택
앱 내 상황실과 안심콜 모형은 가상 데이터 기반입니다. 공식 119 안심콜 조회·등록과 실제 기관 연계는 없습니다.
개인정보 보호로 캡처 제한; 소스 기준 구조도로 설명합니다.

근거: ExpansionUi.centerPage; ExpansionUi.safePage

## 39장

가상 프로필은 독립적으로 관리
가상 운전자와 도움이 필요한 탑승자를 별도로 선택합니다. 실제 디지털키 인식이나 OEM 연계는 없습니다.
개인정보 보호로 캡처 제한; 소스 기준 구조도로 설명합니다.

근거: ExpansionUi.driverPage; Experience.kt

## 40장

현재 세션을 마무리
종료 시 세션의 공유정보와 동의를 정리합니다. 저장한 개인 건강정보는 별도로 관리합니다.

근거: ExpansionUi.finishPage; SessionEngine.finish; 앱 화면 06-finish; APK 51d5bec8061983321ddd80d132e072da78eca849aa7d7ca1331b6bc63bf04545

## 41장

앱의 UI, 동의 상태와 잠금, 로컬 저장 및 계산은 직접 동작합니다. 기관 통신과 건강정보 연계, 카메라·위치 전송, 경찰 승인과 차량 외부 등화는 가상 기능입니다. SDK의 도어와 배터리 읽기는 지원 환경에 한정됩니다.

근거: README.md; docs/HEALTH_LINK_EXAMPLE_V11.md; docs/FEATURE_MATRIX.md
