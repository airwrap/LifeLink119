# LifeLink 119 최종 설명자료 · 24장

**2026-11-15 최종본 · 앱 v0.18.1**. 공모전 제출과 발표에는 아래 파일을 사용한다.

| 형식 | 최종 파일 | 구성 |
|---|---|---|
| PDF | [LifeLink119_Final_Guide.pdf](output/LifeLink119_Final_Guide.pdf) | 24페이지 · 약 7.0 MB |
| PowerPoint | [LifeLink119_Final_Guide.pptx](output/LifeLink119_Final_Guide.pptx) | 24슬라이드 · 약 5.1 MB · 발표자 노트 포함 |
| 시연영상 | [LifeLink119_demo_final.mp4](https://github.com/airwrap/LifeLink119-video/blob/main/contest-demo-20261115/output/LifeLink119_demo_final.mp4) | 최신 SUV·가족·반려견 이미지 적용 |

표지 문구는 **“나와 내 가족을 위한 안전 동행”**, **“위급상황에서는 1분 1초가 생명을 살릴 수 있습니다”**다. 현대 SUV 옆을 네 가족과 사용자가 제공한 골든 리트리버가 함께 걷는 이미지를 앱·영상과 동일하게 사용한다.

## 발표 흐름

| 페이지 | 설명 내용 |
|---|---|
| 1–3 | 서비스 소개와 홈에서 필요한 도움 찾기 |
| 4–6 | 가족 나들이 중 어머니의 가슴 통증 → 119 긴급신고 → 음성 질문과 응급 요약 |
| 7–8 | 119 연결·접수 지연 → 음성 동행 → 상태 악화·재연결·인계 요약 |
| 9–11 | 응급상담, 마음상담, 혼자 탑승한 사람의 위기 대응 |
| 12 | 안심콜 본인 조회·차량 연계 설계, 안전한 보관과 환자별 응급 요약 활용 |
| 13–22 | 폭염·잔류 탑승자·고립 위험, CO₂/CO 전용 센서, 검증 절차와 AI 안내 원칙 |
| 23–24 | 현재 구현과 확장 제안의 구분, 발표·앱 시연 순서 |

기본 시연은 제출 APK를 실행한 뒤 **더보기 → 음성 동행 자동 시연**으로 시작한다. 실제 긴급 전화는 발신하지 않으며 합성 응답으로 질문·상태 변화·인계 요약을 보여준다. 실제 마이크 응답 방법과 필요한 설정은 [저장소 실행 안내](../../../README.md#실행-방법)를 따른다.

## 구현 범위

현재 앱은 직접 입력한 본인·가족 건강정보를 암호화해 저장하고, 환자 확인과 공유 선택을 거쳐 응급 요약에 활용한다. 본인 인증 후 소방청 안심콜 정보를 조회해 현대차 Pleos 차량으로 자동 연계하는 흐름은 **공식 연계 방식·인증·권한 검증과 기관 협력이 필요한 확장 설계**다. 승인된 조회 API나 실제 기관 전송이 제공된다는 뜻은 아니다. [안심콜 연계 검토](../../SAFE_CALL_WEB_INTEGRATION_REVIEW.md).

온도·가스·잔류 탑승자 감지와 심리적 위기 전용 AI 절차는 제안 화면으로 구분했다. 센서·제조사 연동, 의료전문가 검토와 실차 검증이 필요하다. [센서·UX 확장 기획과 근거](../../CABIN_SAFETY_EXTENSION_20261115.md).

## 화면·파일 검증

홈·긴급신고·응급상담·마음상담·음성 질문은 제출 release 검증 화면으로 갱신했다. 응급 요약·연결 대기·상태 악화는 최신 시연영상의 실제 앱 녹화에서 가져왔다. 24개 PDF 페이지를 시각 검수하고 PPTX의 구조·레이아웃·폰트·표를 검사했다. 네이티브 PowerPoint 앱에서의 실행 검증은 수행하지 않았다.

본문·아이콘·도식은 편집 가능한 PowerPoint 요소이고 실제 앱 화면은 이미지다. 19·23페이지는 네이티브 표다. 전체 슬라이드의 발표자 노트에 설명과 근거·이미지 출처가 있다. 이 자료 변경으로 앱 소스와 제출 APK를 변경하지 않았다.

## 제작 소스

`.build/prepare.py`, `build_deck.mjs`, `render_pdf.py`, `finalize.mjs`, `delivery_check.py`와 `assets/`를 보관한다. Windows 맑은 고딕, Python의 Pillow·ReportLab·PyMuPDF, Node.js를 사용한다. 초안 생성은 이 폴더에서 다음 순서다.

```powershell
python .build/prepare.py
node .build/build_deck.mjs
python .build/render_pdf.py
```

최종 검증에는 `LIFELINK_PRESENTATIONS_SKILL`을 설치된 Presentations 스킬 경로로, `LIFELINK_PYTHON`을 Python 실행 파일의 절대 경로로 지정하고 `node .build/finalize.mjs`, `python .build/delivery_check.py`를 실행한다. 재편집 시 기존 최종본을 보존하고 새 출력·검증 기록 경로를 지정한다. PDF는 동일한 슬라이드 모델에서 생성하며 PowerPoint에서 내보낸 파일은 아니다.

기존 `LifeLink119_상황별안내와_탑승자안전.pptx`와 `.pdf`는 이전 이력으로 보존한다. 현재 제출용은 위 표의 **`LifeLink119_Final_Guide`** 두 파일이다. 내부 렌더·초안·검사 로그는 최종 설명자료가 아니다.
