# 상황별 사용 안내와 탑승자 안전 · 24장

- [PPTX](output/LifeLink119_상황별안내와_탑승자안전.pptx)
- [PDF](output/LifeLink119_상황별안내와_탑승자안전.pdf)
- [센서·UX 확장 기획과 근거](../../CABIN_SAFETY_EXTENSION_20261115.md)

요청한 표지 이미지 문구, 긴급 연결의 PIN 관련 문구, 반복 하단 문구, 동의 저장·반복 안내 문구를 제거했다. 1~12페이지는 사고·부상, 상담, 신고 후 대기와 안심콜 정보 활용이다. 13~22페이지는 폭염, 아이 잔류, 혼자 탑승·고립, 심리적 위기, CO₂·CO와 센서 확장을 구체화한다. 23페이지는 구현 범위, 24페이지는 발표 순서다.

온도·가스·잔류 감지는 아직 앱에 연동하지 않은 확장안이다. 현재 앱 화면과 편집 가능한 제안 화면을 구분했다. 심리적 위기 전용 AI 절차 역시 별도 전문가 검토가 필요하다. 원본 현대차 사진과 가족 합성 이미지를 유지했으며 출처는 발표자 노트에 있다.

본문·아이콘·도식·센서 비교 표는 편집 가능한 PowerPoint 요소다. 실제 앱 화면은 이미지다. 19·23페이지의 표는 네이티브 표이며 24페이지 전체에 발표자 노트를 포함한다. 모든 PDF 페이지를 시각 검수하고 PPTX 구조·레이아웃을 검사했다. 네이티브 PowerPoint 앱에서는 열어 보지 않았다.

`.build/prepare.py`, `build_deck.mjs`, `render_pdf.py`, `finalize.mjs`, `delivery_check.py`와 `assets/`를 보관한다. Windows 맑은 고딕, Python의 Pillow·ReportLab·PyMuPDF, Node.js를 사용한다. 초안 생성은 이 폴더에서 다음 순서다.

```powershell
python .build/prepare.py
node .build/build_deck.mjs
python .build/render_pdf.py
```

원본·전달본을 보존하고 새 수정은 초안에서 시작한다. 최종 검증에는 `LIFELINK_PRESENTATIONS_SKILL`을 설치된 Presentations 스킬 경로로 지정한다. 내부 렌더·초안·검사 로그는 최종 설명자료가 아니다.
