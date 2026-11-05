from pathlib import Path
import base64,html,json
import fitz

root=Path('docs/presentation/family-safety-v09');tmp=root/'.build';out=root/'output';out.mkdir(exist_ok=True)
slides=sorted(tmp.glob('slide-*.png'))
assert len(slides)==10
pdf=fitz.open()
for image in slides:
    page=pdf.new_page(width=1280,height=720)
    page.insert_image(page.rect,filename=str(image))
pdf.set_metadata({'title':'LifeLink 119 가족 안전 발표자료 v0.9','author':'LifeLink 119'})
pdf.save(out/'LifeLink119_가족안전_발표자료_v09.pdf',deflate=True)
pdf.close()
items=[]
for i,p in enumerate(slides,1):
    data=base64.b64encode(p.read_bytes()).decode('ascii')
    items.append(f'<section class="slide" {"" if i==1 else "hidden"}><img alt="발표자료 {i}번 슬라이드" src="data:image/png;base64,{data}"></section>')
document='''<!doctype html><html lang="ko"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>LifeLink 119 가족 안전 발표자료</title><style>
*{box-sizing:border-box}body{margin:0;background:#e9eef5;font:16px "Malgun Gothic",sans-serif;color:#192639}.bar{display:flex;align-items:center;gap:12px;padding:10px 16px;background:#fff}.brand{font-weight:bold;color:#2452aa;margin-right:auto}button{font:inherit;color:#192639;background:white;border:1px solid #b8c5d5;border-radius:8px;min-height:44px;padding:8px 16px}button:focus-visible{outline:3px solid #2452aa}main{display:flex;align-items:center;justify-content:center;min-height:calc(100vh - 70px);padding:12px}.slide{width:min(100%,1280px);box-shadow:0 10px 35px #1b2b4430}.slide img{display:block;width:100%;height:auto}.slide[hidden]{display:none}#count{min-width:5em;text-align:center}
</style></head><body><nav class="bar" aria-label="슬라이드 조작"><span class="brand">LifeLink 119</span><button id="prev">이전</button><span id="count" aria-live="polite"></span><button id="next">다음</button><button id="full">전체 화면</button></nav><main>'''+''.join(items)+'''</main><script>
const slides=Array.from(document.querySelectorAll('.slide'));let current=0;const count=document.querySelector('#count');function show(n){current=Math.max(0,Math.min(slides.length-1,n));slides.forEach((s,i)=>s.hidden=i!==current);count.textContent=(current+1)+' / '+slides.length;document.querySelector('#prev').disabled=current===0;document.querySelector('#next').disabled=current===slides.length-1}document.querySelector('#prev').onclick=()=>show(current-1);document.querySelector('#next').onclick=()=>show(current+1);document.querySelector('#full').onclick=()=>document.fullscreenElement?document.exitFullscreen():document.documentElement.requestFullscreen();document.addEventListener('keydown',e=>{if(e.key==='ArrowRight'||e.key==='PageDown'){show(current+1);e.preventDefault()}if(e.key==='ArrowLeft'||e.key==='PageUp'){show(current-1);e.preventDefault()}if(e.key.toLowerCase()==='f')document.querySelector('#full').click()});show(0);
</script></body></html>'''
(out/'LifeLink119_가족안전_발표자료_v09.html').write_text(document,encoding='utf-8')
notes=json.loads((tmp/'notes.json').read_text(encoding='utf-8'))
text='# LifeLink 119 발표 메모 · v0.9\n\n시연 앱 화면은 실제 에뮬레이터 캡처이며, 표지는 기존 자료의 AI 생성 콘셉트 이미지입니다.\n\n'
for i,note in enumerate(notes,1):text+=f'## {i}장\n\n{note}\n\n'
(out/'LifeLink119_발표메모_v09.md').write_text(text,encoding='utf-8')
print('PDF, HTML, notes generated:',len(slides),'slides')
