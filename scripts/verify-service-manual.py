"""Render every PDF page, check source binding, and exercise the offline HTML."""
from pathlib import Path
import argparse,hashlib,importlib.util,json,os,re,subprocess,sys

ROOT=Path(__file__).resolve().parents[1];BASE=ROOT/'docs/manual/v17';OUT=BASE/'output'
QA=ROOT/'verification/bright-ui-v17/manual-output'

def browser_check():
    from playwright.sync_api import sync_playwright
    results=[];errors=[];expected_pages=json.loads((OUT/'manifest.json').read_text(encoding='utf-8'))['pages']
    with sync_playwright() as p:
        browser=p.chromium.launch(headless=True)
        for width,height in [(1366,900),(390,844)]:
            page=browser.new_page(viewport={'width':width,'height':height},device_scale_factor=1)
            page.on('pageerror',lambda e:errors.append(str(e)))
            page.goto((OUT/'LifeLink119_Manual_v0.17.html').as_uri(),wait_until='load');page.evaluate('document.fonts.ready')
            assert page.locator('h1').count()==1
            assert page.locator('main figure img').count()==40
            assert page.locator('main figure img').evaluate_all('(xs)=>xs.every(x=>x.complete&&x.naturalWidth>0)')
            assert page.evaluate('document.documentElement.scrollWidth<=innerWidth'), 'Page overflows viewport'
            assert page.locator('nav a').count()==expected_pages
            page.locator('#search').fill('영상')
            count=page.locator('nav a:not([hidden])').count();assert 1<=count<expected_pages
            assert page.locator('nav a[hidden]').first.evaluate('(e)=>getComputedStyle(e).display')=='none'
            page.locator('#search').fill('')
            page.locator('#cover').scroll_into_view_if_needed()
            page.screenshot(path=str(QA/f'html-{width}.png'))
            page.locator('#cover .image').click();assert page.locator('#zoom').is_visible()
            assert page.locator('#zoom img').evaluate('(e)=>e.clientWidth>=e.naturalWidth')
            if width<700:assert page.locator('#zoom').evaluate('(e)=>e.scrollWidth>e.clientWidth')
            page.keyboard.press('Escape');assert not page.locator('#zoom').is_visible()
            page.locator('nav a[href="#health"]').click()
            page.wait_for_function("location.hash==='#health' && document.querySelector('#health').getBoundingClientRect().top<innerHeight")
            assert not errors,errors
            results.append({'viewport':[width,height],'status':'PASS','images':40,'tocEntries':expected_pages,'search':True,'nativeSizeZoom':True,'horizontalPageOverflow':False})
            page.close()
        browser.close()
    (QA/'browser.json').write_text(json.dumps({'status':'PASS','checks':results,'pageErrors':errors},ensure_ascii=False,indent=2),encoding='utf-8')
    print('Offline HTML browser checks: PASS',flush=True)

def main():
    p=argparse.ArgumentParser();p.add_argument('--browser',action='store_true');a=p.parse_args()
    QA.mkdir(parents=True,exist_ok=True)
    if a.browser:browser_check();return
    import fitz
    from PIL import Image,ImageDraw
    data=json.loads((BASE/'manual-content.json').read_text(encoding='utf-8'))
    manifest=json.loads((OUT/'manifest.json').read_text(encoding='utf-8'))
    assert manifest['pages']==len(data)==52 and manifest['actualNativeScreens']==40
    assert {'health','health-input','health-highway','sharing-room','pc-room','driver-safe'}.issubset({p['id'] for p in data})
    proof=json.loads((BASE/'sdk-verification.json').read_text(encoding='utf-8'));assert proof['apkSha256']==manifest['apkSha256']
    pdf=fitz.open(OUT/'pdf/LifeLink119_Manual_v0.17.pdf');assert len(pdf)==len(data)
    assert len(pdf.get_toc())==len(data)
    render=QA/'pdf';render.mkdir(exist_ok=True);checks=[];thumbs=[]
    for i,page in enumerate(pdf):
        text=page.get_text();expected=re.sub(r'\s+','',data[i]['title'])
        assert expected in re.sub(r'\s+','',text),(i+1,'title missing or Korean font failed')
        assert '\ufffd' not in text and len(text)>200
        for b in page.get_text('dict')['blocks']:
            if b['type']!=0:continue
            for line in b['lines']:
                for s in line['spans']:
                    x0,y0,x1,y1=s['bbox'];assert x0>=0 and y0>=0 and x1<=page.rect.width+.5 and y1<=page.rect.height+.5,(i+1,s['text'],'page overflow')
        path=render/f'page-{i+1:02d}.png';page.get_pixmap(matrix=fitz.Matrix(1.3,1.3)).save(path)
        im=Image.open(path).convert('RGB');im.thumbnail((700,495));thumbs.append(im.copy())
        checks.append({'page':i+1,'title':data[i]['title'],'textAndBounds':'PASS'})
    for start in range(0,len(thumbs),8):
        sheet=Image.new('RGB',(1440,2100),'#DCE3EA');d=ImageDraw.Draw(sheet)
        for j,im in enumerate(thumbs[start:start+8]):
            x=(j%2)*720+10;y=(j//2)*525+25;sheet.paste(im,(x,y));d.text((x,y-18),f'Page {start+j+1:02d}',fill='#162536')
        sheet.save(QA/f'contact-{start//8+1}.jpg',quality=86)
    browser_python=os.environ.get('LIFELINK_BROWSER_PYTHON')
    if not browser_python:
        browser_python=sys.executable if importlib.util.find_spec('playwright') else str(ROOT.parent/'LifeLink119-video/.tts-venv/Scripts/python.exe')
    subprocess.run([browser_python,str(Path(__file__).resolve()),'--browser'],check=True,timeout=150)
    (QA/'results.json').write_text(json.dumps({'status':'PASS','pages':checks,'fonts':'Embedded Malgun Gothic; every title and text bounds checked','browser':json.loads((QA/'browser.json').read_text(encoding='utf-8')),'apkSha256':manifest['apkSha256']},ensure_ascii=False,indent=2),encoding='utf-8')
    print(f'PDF {len(data)} pages rendered; Korean text, bounds, bookmarks and HTML browser checks: PASS',flush=True)

if __name__=='__main__':main()
