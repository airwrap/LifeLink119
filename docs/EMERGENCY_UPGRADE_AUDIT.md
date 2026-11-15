# 응급 대기 동행 개발 전 점검

2026-11-14 · 구현 전 소스/테스트/서버/계약/런처를 점검한 기준선.

| 기능 | 현재 구현 | 추가 개발 |
|---|---|---|
| 119 긴급/응급상담, 112, 109 | SessionEngine + MockDispatchGateway, 전부 모의 연결 | 기존 흐름 유지, 별도 신고 확인 상태 |
| 가족 안심콜 | 공식 사이트 외부 열기, 직접 입력 12명, AES-GCM/PIN, 공식 DB 동기화 없음 | 새 요약에 자동 병합하지 않음; 기존 인증/동의 유지 |
| 차량 | Vehicle 2.3.2 capability 확인 후 도어/SOC/운전석 에어백 읽기; 15초 실패 처리 | Provider 어댑터, 출처/시각, 응급 세션에서 동의 후 자동 준비 |
| 위치 | 고정 가상 훈련장; 실제 GPS 미구현 | FusedLocation 1.1.3 지원 확인, 최신성·정확도 처리, 짧은 메모리 표본 |
| 매뉴얼/접근성 | 공식 출처를 표기한 학습용 카드, 문자 선택, 큰 글씨, CarUxRestrictions 적용 | 규칙 기반 한 질문씩 안내, 별도 음성 어댑터 |
| 영상/EV/협력 | 합성 영상 동의/철회, V2L 계산, 오프라인 협력 상태 | 재사용, 실제 전송/차량 제어로 확대하지 않음 |
| PC 상황실 | localhost 서버; 합성 메타데이터 허용 목록/Host-Origin 검사 | 별도 응급 대기 합성 스냅샷, 기존 계약 유지 |
| 콜백/출동 대기 | 미구현; 가상 상담원 응답이 실제 접수 확인은 아님 | 출처가 있는 사용자 확인 상태 및 재시도 |
| AI/외부 LLM | 오프라인 상담 요약 템플릿, 외부 모델 없음 | 고정 안내 우선, 엄격한 코드 응답만 받는 선택적 프록시 |

## SDK 조사와 경계

- [Vehicle 공식 문서](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/intro): 현재 의존성과 같은 2.3.2. 기존 에어백 capability/get을 재사용한다. 미지원/false를 사고 없음으로 바꾸지 않는다.
- [권한 문서](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle-permission): 조회 권한만 사용한다. 미확인 충돌 프로퍼티를 추정하거나 제조사 비공개 API를 호출하지 않는다.
- [FusedLocation](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/FusedLocation/intro): 1.1.3, FUSED_LOCATION 권한, isSupported 및 null/오류 분기 필수. lastKnown은 현재 위치를 보장하지 않는다.
- [NaviHelper](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/intro): 3.0.5. 지원·권한과 도로/방향 데이터 의미가 검증된 경우만 조회 어댑터로 연결한다. 목적지 변경·병원 자동 안내는 범위 밖이다.
- [차종별 가용성](https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/api-compatibility-availability): 미지원 특수 프로퍼티는 Pleos 기술 지원 검토가 필요하다. 실제 차량과 에뮬레이터 SDK 값을 구분한다.

## 구현 결정

기존 홈/신고 흐름을 유지하고 119 세션에 ‘응급 요약·대기 지원’을 추가한다. Android 비의존 core에 출처·시각을 포함한 스냅샷, 신고 상태, 규칙 기반 동행을 둔다. SDK/위치/내비/LLM은 교체 가능한 Provider로 분리한다. 개인정보 수집 및 외부 전송은 기본 꺼짐, 실제 위치/환자 관찰은 로컬에만 두고 PC에는 명시적으로 선택한 합성 시나리오만 게시한다. 대기 기록의 선택적 복구는 기존 Keystore 암호화와 인증을 재사용하며 저장 기한을 제한한다.

고정 응급 안내는 기존 emergency-manual.json을 재사용하며 신규 카드는 공식 자료에 출처를 연결한다. ‘전문가 검토 완료’라고 표시하지 않는다. 앱은 실제 전화 상태/접수/출동/콜백 예약을 확인할 수 없으며 이를 자동 확정하지 않는다.
