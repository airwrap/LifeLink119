from pathlib import Path
import base64,html,json,zipfile,hashlib,re
import fitz

root=Path('docs/presentation/complete-functions-v11');tmp=root/'.build';out=root/'output';out.mkdir(exist_ok=True)
slides=sorted(tmp.glob('slide-*.png'));notes=json.loads((tmp/'notes.json').read_text(encoding='utf-8'))
assert slides and len(slides)==len(notes)
pdf=fitz.open()
for image in slides:
 page=pdf.new_page(width=1280,height=720);page.insert_image(page.rect,filename=str(image))
pdf.set_metadata({'title':'LifeLink 119 전체 기능과 화면','author':'LifeLink 119'})
pdf.save(out/'LifeLink119_전체기능_UI_소개_v11.pdf',deflate=True);pdf.close()
items=[]
for i,p in enumerate(slides,1):
 data=base64.b64encode(p.read_bytes()).decode('ascii')
 items.append(f'<section class="slide" {"" if i==1 else "hidden"}><img alt="발표자료 {i}번 슬라이드" src="data:image/png;base64,{data}"></section>')
page='''<!doctype html><html lang="ko"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>LifeLink 119 전체 기능 소개</title><style>
*{box-sizing:border-box}body{margin:0;background:#e9eef5;font:16px "Malgun Gothic",sans-serif;color:#192639}.bar{display:flex;align-items:center;gap:12px;padding:10px 16px;background:#fff}.brand{font-weight:bold;color:#2452aa;margin-right:auto}button{font:inherit;color:#192639;background:white;border:1px solid #b8c5d5;border-radius:8px;min-height:44px;padding:8px 16px}button:focus-visible{outline:3px solid #2452aa}main{display:flex;align-items:center;justify-content:center;min-height:calc(100vh - 70px);padding:12px}.slide{width:min(100%,1280px);box-shadow:0 10px 35px #1b2b4430}.slide img{display:block;width:100%;height:auto}.slide[hidden]{display:none}#count{min-width:5em;text-align:center}
</style></head><body><nav class="bar" aria-label="슬라이드 조작"><span class="brand">LifeLink 119</span><button id="prev">이전</button><span id="count" aria-live="polite"></span><button id="next">다음</button><button id="full">전체 화면</button></nav><main>'''+''.join(items)+'''</main><script>
const slides=Array.from(document.querySelectorAll('.slide'));let current=0;const count=document.querySelector('#count');function show(n){current=Math.max(0,Math.min(slides.length-1,n));slides.forEach((s,i)=>s.hidden=i!==current);count.textContent=(current+1)+' / '+slides.length;document.querySelector('#prev').disabled=current===0;document.querySelector('#next').disabled=current===slides.length-1}document.querySelector('#prev').onclick=()=>show(current-1);document.querySelector('#next').onclick=()=>show(current+1);document.querySelector('#full').onclick=()=>document.fullscreenElement?document.exitFullscreen():document.documentElement.requestFullscreen();document.addEventListener('keydown',e=>{if(e.key==='ArrowRight'||e.key==='PageDown'){show(current+1);e.preventDefault()}if(e.key==='ArrowLeft'||e.key==='PageUp'){show(current-1);e.preventDefault()}if(e.key.toLowerCase()==='f')document.querySelector('#full').click()});show(0);
</script></body></html>'''
(out/'LifeLink119_전체기능_UI_소개_v11.html').write_text(page,encoding='utf-8')
note='# LifeLink 119 전체 기능 발표 메모 · v0.11\n\n표지는 AI 생성 콘셉트 이미지입니다. 실제 앱 화면은 전용 에뮬레이터의 설치된 v0.11 APK에서 캡처했습니다. 의료정보 보호 화면은 소스 기준 구조도이며 실제 캡처가 아닙니다.\n\n'
for i,n in enumerate(notes,1):note+=f'## {i}장\n\n{n["body"]}\n\n근거: {n["source"]}\n\n'
(out/'LifeLink119_전체기능_발표메모_v11.md').write_text(note,encoding='utf-8')
source=(tmp/'build.mjs').read_text(encoding='utf-8')
titles={m.group(2):m.group(1) for m in re.finditer(r"await screen\('([^']+)'\s*,\s*'[^']*'\s*,\s*'[^']*'\s*,\s*'([^']+)'",source)}
captures=sorted((tmp/'screens').glob('*.jpg'));manifest=[]
archive=out/'LifeLink119_실제_UI_화면모음_v11.zip'
with zipfile.ZipFile(archive,'w',compression=zipfile.ZIP_DEFLATED,compresslevel=6) as z:
 for p in captures:
  xml=p.with_suffix('.xml')
  if not xml.is_file() or '<node ' not in xml.read_text(encoding='utf-8'):raise ValueError('Missing visible evidence: '+p.name)
  manifest.append({'file':p.name,'title':titles.get(p.stem,p.stem),'sha256':hashlib.sha256(p.read_bytes()).hexdigest()})
  z.write(p,'screens/'+p.name)
 z.writestr('화면_안내.txt','LifeLink 119 v0.11 실제 앱 화면. 보호 화면은 캡처하지 않았습니다. 각 기능의 기관 연결·전송은 모의 기능입니다.\n\n'+'\n'.join(f'{m["file"]}  {m["title"]}' for m in manifest))
 z.writestr('manifest.json',json.dumps({'apkSha256':'51d5bec8061983321ddd80d132e072da78eca849aa7d7ca1331b6bc63bf04545','screens':manifest},ensure_ascii=False,indent=2))
with zipfile.ZipFile(archive) as z:assert z.testzip() is None
print('Slides:',len(slides),'Public UI screenshots:',len(manifest),'ZIP CRC: OK')
