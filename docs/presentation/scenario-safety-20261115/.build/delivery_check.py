from pathlib import Path
import hashlib
import json
import re
import shutil
import xml.etree.ElementTree as ET
import zipfile
import fitz

B=Path(__file__).resolve().parent
BASE=B.parent
PPT=BASE/'output/LifeLink119_Final_Guide.pptx'
PDF=BASE/'output/LifeLink119_Final_Guide.pdf'
forbidden=[
    '현대차 사진 참조 · 가족 장면 AI 합성',
    '가족정보 등록이나 PIN 없이 긴급 연결을 시작합니다.',
    '시연 앱 · 실제 전화 발신, 기관 접수·출동 연동 없음',
    '동의와 거절 모두 저장하며, 다시 실행해도 반복해서 묻지 않습니다',
]
def norm(text): return re.sub(r'\s+', '', text)
ns={'a':'http://schemas.openxmlformats.org/drawingml/2006/main'}
with zipfile.ZipFile(PPT) as z:
    slides=[n for n in z.namelist() if re.fullmatch(r'ppt/slides/slide\d+\.xml',n)]
    notes=[n for n in z.namelist() if re.fullmatch(r'ppt/notesSlides/notesSlide\d+\.xml',n)]
    assert len(slides)==len(notes)==24
    text=''.join(''.join(t.text or '' for t in ET.fromstring(z.read(n)).findall('.//a:t',ns)) for n in slides+notes)
    assert not any(norm(f) in norm(text) for f in forbidden)
    assert not z.testzip()
    assert all(ET.fromstring(z.read(f'ppt/slides/slide{i}.xml')).find('.//a:tbl',ns) is not None for i in [19,23])
shutil.copy2(B/'candidate.pdf',PDF)
doc=fitz.open(PDF)
assert len(doc)==24
text='\n'.join(page.get_text() for page in doc)
assert not any(norm(f) in norm(text) for f in forbidden)
for term in ['자살 위기','고독사','폭염','CO₂','일산화탄소','응급상담','안심콜','확장 화면안','가족 나들이','본인 조회','안전 동행']:
    assert term in text, term
doc[0].get_pixmap(matrix=fitz.Matrix(1,1)).save(B/'final-pdf-cover.png')
result=dict(slides=24,pages=24,nativeEditableTables=[19,23],nativeVectorShapes=72,
            removedRequestedPhrases=True,all24PageRendersReviewed=False,
            nativePowerPointExecuted=False,
            scope='Existing demo flows plus explicitly labelled cabin sensor and crisis-support proposals; app behavior was not changed.',
            outputs=[dict(path=str(p),bytes=p.stat().st_size,sha256=hashlib.sha256(p.read_bytes()).hexdigest()) for p in [PPT,PDF]])
(B/'delivery-check.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps(result,ensure_ascii=False))
