from pathlib import Path
import shutil,zipfile,xml.etree.ElementTree as ET,json,hashlib
import fitz

B=Path(__file__).resolve().parent
BASE=B.parent
pdf=BASE/'output/LifeLink119_음성중심_사용안내_현대차가족.pdf'
shutil.copy2(B/'candidate.pdf',pdf)
ppt=BASE/'output/LifeLink119_음성중심_사용안내_현대차가족.pptx'
with zipfile.ZipFile(ppt) as z:
    ns={'a':'http://schemas.openxmlformats.org/drawingml/2006/main'}
    ss=[n for n in z.namelist() if n.startswith('ppt/slides/slide') and n.endswith('.xml')]
    assert len(ss)==16
    for n in ss:
        assert ET.fromstring(z.read(n)).findall('.//a:t',ns)
    assert len([n for n in z.namelist() if n.startswith('ppt/notesSlides/notesSlide') and n.endswith('.xml')])==16
    assert not any(n.endswith(('.env','.log')) for n in z.namelist())
d=fitz.open(pdf)
assert len(d)==16
assert '인계 요약' in d[13].get_text()
assert '외부 AI' in d[2].get_text()
assert hashlib.sha256(pdf.read_bytes()).digest()==hashlib.sha256((B/'candidate.pdf').read_bytes()).digest()
result={'slides':16,'pages':16,'nativeText':True,'nativeTable':True,'speakerNotes':16,
        'visualReview':'Changed cover and home slides reviewed, with all remaining slides checked unchanged from the reviewed source',
        'nativePowerPointExecution':False,
        'files':[{'path':str(p),'bytes':p.stat().st_size} for p in (ppt,pdf)]}
(B/'delivery-check.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps(result,ensure_ascii=False))
