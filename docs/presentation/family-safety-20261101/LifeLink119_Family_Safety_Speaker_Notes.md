# LifeLink 119 · 발표 메모

v0.7.0-demo · 2026-11-01 · 10장

## 1

LifeLink 119는 나와 가족이 함께 타는 자동차에서 긴급 도움과 상담을 요청하는 과정을 보여줍니다. 위급한 순간뿐 아니라 가족의 상태가 걱정될 때도 도움을 구할 수 있는 흐름을 담았습니다. 현재는 공모전용 모의 연결입니다. 표지 이미지는 서비스 콘셉트를 표현한 AI 생성 이미지이며 실제 차량 연동 사진이 아닙니다.

근거: README.md; docs/IMPLEMENTATION_REPORT_V07.md

## 2

홈에서는 지금 긴급한지, 상담이 필요한지를 먼저 선택합니다. 가족정보 등록은 시작 조건이 아닙니다. 위급하면 상단 119 빠른 호출로 바로 요청할 수 있고, 일반 긴급 메뉴에서는 119와 112를 구분해 선택합니다.

근거: app/src/main/java/com/lifelink119/app/ui/ExpansionUi.kt: homePage, entry; 공개 화면 home.jpg

## 3

긴급 상황과 상담은 진입 목적이 다릅니다. 두 경로 모두 연결 후 필요한 지원과 공유 정보를 선택하고, 종료할 때 세션의 공유 내용을 정리합니다. 의료정보 인증은 정보를 열 때 필요하며 긴급 연결의 선행 조건이 아닙니다.

근거: docs/DRIVING_EMERGENCY_DEMO.md; docs/MEDICAL_PRIVACY.md; ExpansionUi.kt

## 4

위급한 순간에는 위험 종류를 고르는 질문보다 먼저 도움을 요청하는 흐름을 보여줍니다. 상단 119 빠른 호출은 정차나 PIN 입력 없이 요청합니다. 주행 중 표시는 시연 상황이며 실제 차량의 안전·조작 제한을 해제하는 기능은 아닙니다.

근거: docs/DRIVING_EMERGENCY_DEMO.md; core/DrivingEmergency.kt; 공개 화면 emergency.jpg

## 5

연결 후 상황 전달과 상황실 확인을 시연합니다. 위치와 차량 정보는 선택적으로 공유하며 철회할 수 있습니다. 정차와 안전 확보는 시연자가 알려주는 상태이고 자동 차량 판단이 아닙니다. 가상 인계 후 종료하면 세션 공유정보를 정리합니다. 실제 출동이나 대응팀 인계는 발생하지 않습니다.

근거: core/DrivingEmergency.kt; Experience.kt; 공개 화면 handover.jpg

## 6

상담은 응급의료상담과 심리위기상담으로 구분합니다. 가족의 상태가 걱정될 때 119 의료상담을, 마음의 어려움을 이야기하려는 경우 109 상담 경로를 선택하는 사용 흐름입니다. 현재 제품은 가상 연결을 시연하며 실제 상담 대화를 수행하지 않습니다. 앱이 의료 진단이나 응급도를 판정하는 방식도 아닙니다.

근거: Experience.kt: ConnectionTarget; ExpansionUi.kt: entry; 공개 화면 consult-choice.jpg

## 7

상담 연결 시연 자체에는 가족 등록이나 개인정보 제공이 필요하지 않습니다. 연결 뒤 도움이 필요한 가족을 선택할 수 있으며, 의료정보는 인증한 뒤 필요한 항목만 공유합니다. 문자와 전달 요약 등 추가 지원도 목적과 차량의 실제 제한에 맞춰 선택합니다. 화면의 기관 연결과 영상은 모의 기능입니다.

근거: docs/MEDICAL_PRIVACY.md; ExpansionUi.kt: sessionPage, supportPage; 공개 화면 medical-connected.jpg

## 8

차에 함께 탄 사람의 의료정보가 먼저 노출되지 않도록 보호합니다. 사용할 수 있는 시스템 인증이 있으면 연결하고, 없으면 사용자가 정한 앱 PIN을 사용합니다. 인증 뒤에도 공유할 항목을 선택하며 연결 중 철회할 수 있습니다. 종료하면 세션 공유정보를 정리하지만 저장된 가족 프로필 관리는 별도입니다.

근거: docs/MEDICAL_PRIVACY.md; ExperienceData.kt: encodePrivacySafe; Experience.kt: finish

## 9

지원 기능은 나와 가족에게 필요한 것만 선택하도록 구성했습니다. 설명을 돕는 문자와 쉬운 안내, 기본 매뉴얼, 전달 요약, 가상 정보와 합성 영상이 있습니다. 주변 차량 협력과 승인형 화면 표시도 별도의 동의와 가상 승인 절차를 거치는 추가 시연입니다. 모든 기능이 모든 기관에 같은 방식으로 제공되는 것은 아니며 실제 기관 승인이나 차량 등화 작동은 없습니다.

근거: docs/FEATURE_MATRIX.md; docs/CONTEST_DEMO_CHECKLIST.md; ExpansionUi.kt: action, cooperationPage

## 10

LifeLink 119가 제안하는 자동차는 나와 가족이 도움이 필요할 때 요청을 시작할 수 있는 공간입니다. 위급하면 먼저 긴급 도움을 요청하고, 가족의 상태나 마음이 걱정되면 상담 경로를 선택합니다. 실제 기관 연결과 차량 제어를 포함한 운영 서비스는 추가 협력과 검증이 필요한 단계입니다.

근거: README.md; docs/IMPLEMENTATION_REPORT_V07.md
