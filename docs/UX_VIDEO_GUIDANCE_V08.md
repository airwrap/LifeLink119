# LifeLink 119 v0.8 UI 및 영상 안내 시연

**시연 모드: 실제 119/112/109 신고·상담, 영상통화, 출동은 일어나지 않습니다.**

## 사용 흐름

1. 홈의 **119 구조·구급 바로 요청**은 등록·인증·정차 확인 없이 차 안의 위급 상황 시연을 한 번에 시작합니다. 같은 요청을 다시 눌러도 새 티켓을 만들지 않습니다.
2. **상담받고 싶어요**에서 119 응급의료상담 또는 109 심리위기 상담을 고릅니다. **112 긴급신고 시연**은 홈에서 별도로 시작합니다. 119는 실제로 구조·구급 신고와 응급의료상담을 모두 담당하지만 이 앱은 이를 실제로 연결하지 않습니다.
3. 119 연결 시연의 **상황실 영상 안내 보기** → 요청 → 합성 영상 사용 동의 → 시작을 선택하면 가상 담당자의 고정 문구와 합성 도형을 볼 수 있습니다. 카메라·마이크·실제 상황실·전송은 사용하지 않습니다. 다음 안내와 중단·동의 철회를 지원합니다.
4. 플랫폼 영상 제한 중에는 영상 안내를 시작할 수 없습니다. 앱이 백그라운드로 가거나 영상 제한이 켜지면 미리보기는 숨겨지고 자동으로 다시 켜지지 않습니다. 실제 플랫폼 제한은 시연 설정으로 해제하지 않습니다.
5. 가족·의료정보는 별도 인증을 유지합니다. 영상 안내의 고정 합성 장면에는 프로필·의료정보가 포함되지 않으며, 긴급 요청의 선행 조건으로 의료정보 인증을 요구하지 않습니다.

## 화면 기반 점검

변경 전 설치된 APK의 홈·긴급 선택·상담 선택·상담 연결·주행 중 연결 화면을 네이티브 View 캡처로 확인했습니다. 중복된 긴급 버튼, 119 신고와 상담의 관계가 드러나지 않는 문구, 긴 화면에 흩어진 행동과 선택사항의 불명확성을 발견했습니다. 변경 후 홈에서 중복을 없애고 신고 목적별 경로를 분리했으며, 119 상담 설명, 영상 안내 진입, 정보 선택의 선택사항 표시와 더 짧은 안내를 추가했습니다. 넓은 홈 내용은 가운데로 모으고 520dp에서는 주요 행동을 세로로 배치합니다.

로컬 증거: `verification/v08/ux/before/` 및 `verification/v08/ux/after/`; 화면은 테스트 에뮬레이터에서 설치된 앱의 네이티브 View를 그린 것입니다. 보호 화면의 캡처 제한을 우회하지 않습니다. 이 기록과 APK는 Git에서 제외됩니다.

## 검증 및 한계

- `:app:assembleDebug :app:testDebugUnitTest :app:lintDebug` 성공: Android 단위 테스트 143개, lint 지적 없음. 상세 결과는 로컬 `verification/v08/build-final.log`.
- `python -m unittest discover -s console -p 'test_*.py' -v`: 68개 통과. 결과는 로컬 `verification/v08/console-tests.log`.
- `python scripts/smoke-ux-v08.py --serial emulator-5580`: 7개 묶음 통과. 한 번 호출과 중복 방지, 동의·시작·철회, 백그라운드/수동 재개, 112·119 상담·109 경로, 실패 재시도, 제한 시 영상 차단, 520dp 배치와 동의 취소를 검사했습니다. 홈 주요 네 버튼의 텍스트·클릭 가능 여부와 48dp 이상 크기도 확인했습니다. 결과는 로컬 `verification/v08/ux/smoke-results.json`입니다.
- `python scripts/smoke-driving-emergency.py --serial emulator-5580`: 주행 시나리오 11개 묶음 통과. 가상 상황 전달·공유 철회·안전 확보 및 인계·위치 없음·실패 재시도 등을 확인했습니다. 결과는 `verification/v08/driving-native.json`입니다.
- 자동화가 읽는 View 텍스트와 활성·좌표를 확인했으나 TalkBack을 이용한 사용자 평가, 외부 사용성 평가, 색각·모든 글꼴 크기 검증, 실제 차량 주행과 제조사 인증은 수행하지 않았습니다. 520dp는 앱 내 너비 미리보기이며 별도 실제 기기 크기 검증이 아닙니다.
- 영상 프레임과 역할 안내는 합성된 정적 예시입니다. 실제 상황실 직원의 얼굴이나 의료 지시·영상통화 기능을 구현한 것이 아닙니다.

## 참고

- [소방청 119 응급의료 안내·상담](https://www.nfa.go.kr/nfa/news/pressrelease/explanation/?cntId=44&mode=view)
- [MBC: 119 영상 안내 사례](https://imnews.imbc.com/replay/2026/nwdesk/article/6861437_37004.html)
- [Google Android 공식 GitHub skills](https://github.com/android/skills), [차량 UX 제한 API](https://developer.android.com/reference/android/car/drivingstate/CarUxRestrictions)
- GitHub 모바일 디자인 스킬 후보도 살펴보았으나 일부 외부 서비스/API 키를 요구했습니다. 이번에는 기존 product-design audit 지침과 실제 Android 화면을 사용했습니다.
