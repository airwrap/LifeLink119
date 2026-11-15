"""PDF shares the native slide geometry and source crops; fonts are embedded."""
import json
from pathlib import Path
import fitz
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.pdfgen import canvas
from reportlab.lib.colors import HexColor
from reportlab.lib.utils import ImageReader
from PIL import Image, ImageOps, ImageDraw, ImageFont

B=Path(__file__).resolve().parent
BASE=B.parent
M=json.loads((B/'slide-models.json').read_text(encoding='utf-8'))
W,H=M['width'],M['height']; S=.75
pdfmetrics.registerFont(TTFont('Malgun','C:/Windows/Fonts/malgun.ttf'))
pdfmetrics.registerFont(TTFont('MalgunBold','C:/Windows/Fonts/malgunbd.ttf'))
target=B/'candidate.pdf'
p=canvas.Canvas(str(target),pagesize=(W*S,H*S),pageCompression=1)
p.setTitle('LifeLink 119 상황별 사용·탑승자 안전')
p.setAuthor('LifeLink 119')
p.setSubject('긴급신고, 상담, 신고 후 대기, 폭염·고립·공기질 안전과 센서 확장안')
issues=[]
def draw_shape(el):
    p.saveState()
    if el['fill']: p.setFillColor(HexColor('#'+el['fill']))
    if el['stroke']: p.setStrokeColor(HexColor('#'+el['stroke']))
    p.setLineWidth(el['sw']); p.setLineCap(1); p.setLineJoin(1)
    x,y,w,h=el['x'],H-el['y']-el['h'],el['w'],el['h']
    f,st=int(bool(el['fill'])),int(bool(el['stroke']))
    if el['kind']=='ellipse': p.ellipse(x,y,x+w,y+h,fill=f,stroke=st)
    elif el['kind']=='roundRect': p.roundRect(x,y,w,h,min(w,h)/6,fill=f,stroke=st)
    elif el['kind']=='rect': p.rect(x,y,w,h,fill=f,stroke=st)
    elif el['kind']=='path':
        path=p.beginPath()
        def pt(a,b): return x+a*w/24,H-el['y']-b*h/24
        for c in el['commands']:
            if c[0]=='M': path.moveTo(*pt(c[1],c[2]))
            elif c[0]=='L': path.lineTo(*pt(c[1],c[2]))
            elif c[0]=='C': path.curveTo(*pt(c[1],c[2]),*pt(c[3],c[4]),*pt(c[5],c[6]))
            elif c[0]=='Z': path.close()
        p.drawPath(path,fill=f,stroke=st)
    p.restoreState()
for slide in M['slides']:
    p.saveState();p.scale(S,S)
    p.setFillColor(HexColor('#'+slide['background']));p.rect(0,0,W,H,fill=1,stroke=0)
    for el in slide.get('shapes',[]):
        if el['layer']!='over': draw_shape(el)
    for im in slide['images']:
        l,t,r,b=im['crop'];k=im['w']/(r-l)
        y=H-im['y']-im['h']
        p.saveState();clip=p.beginPath();clip.rect(im['x'],y,im['w'],im['h']);p.clipPath(clip,stroke=0,fill=0)
        p.drawImage(ImageReader(str(BASE/'assets'/im['file'])), im['x']-l*k,
                    y-(im['sourceHeight']-b)*k, im['sourceWidth']*k,im['sourceHeight']*k,mask='auto')
        p.restoreState()
    for el in slide.get('shapes',[]):
        if el['layer']=='over': draw_shape(el)
    for el in slide['texts']:
        font='MalgunBold' if el['bold'] else 'Malgun'
        p.setFont(font,el['size']);p.setFillColor(HexColor('#'+el['color']))
        ascent,descent=pdfmetrics.getAscentDescent(font,el['size'])
        baseline=el['y']+(el['lh']-(ascent-descent))/2+ascent
        for i,line in enumerate(el['lines']):
            ww=pdfmetrics.stringWidth(line,font,el['size'])
            if ww>el['w']+.1:issues.append(dict(slide=slide['number'],line=line,width=ww,frame=el['w']))
            yy=H-baseline-i*el['lh']
            if el['align']=='right':p.drawRightString(el['x']+el['w'],yy,line)
            else:p.drawString(el['x'],yy,line)
    for tb in slide.get('tables',[]):
        for ri,row in enumerate(tb['rows']):
            xx=tb['x'];yy=H-tb['y']-(ri+1)*tb['rowHeight']
            fill=tb['headerFill'] if ri==0 else (tb['bodyFill'] if ri%2 else 'F5F6F1')
            p.setFillColor(HexColor('#'+fill));p.rect(xx,yy,tb['w'],tb['rowHeight'],fill=1,stroke=0)
            font='MalgunBold' if ri==0 else 'Malgun'
            p.setFillColor(HexColor('#'+(tb['headerColor'] if ri==0 else tb['bodyColor'])))
            p.setFont(font,tb['fontSize'])
            asc,desc=pdfmetrics.getAscentDescent(font,tb['fontSize'])
            base=yy+(tb['rowHeight']-asc-desc)/2
            for ci,text in enumerate(row):
                ww=pdfmetrics.stringWidth(text,font,tb['fontSize'])
                if ww>tb['columns'][ci]-32:issues.append(dict(slide=slide['number'],table=True,row=ri,column=ci))
                p.drawString(xx+20,base,text);xx+=tb['columns'][ci]
    p.restoreState()
    key='slide-'+str(slide['number']);p.bookmarkPage(key);p.addOutlineEntry(slide['title'].replace('\n',' '),key,level=0)
    p.showPage()
p.save()
(B/'pdf-text-metrics.json').write_text(json.dumps(issues,ensure_ascii=False,indent=2),encoding='utf-8')
if issues:raise RuntimeError('Text overflow: '+str(issues))
doc=fitz.open(target)
render=B/'rendered';render.mkdir(exist_ok=True)
for i,page in enumerate(doc):page.get_pixmap(matrix=fitz.Matrix(1/S,1/S),alpha=False).save(render/f'slide-{i+1:02}.png')
assert len(doc)==len(M['slides'])==24
assert all(page.get_text().strip() for page in doc)
sheet=Image.new('RGB',(1600,236*((len(doc)+3)//4)),'#d9e0dc')
for i in range(len(doc)):
    img=Image.open(render/f'slide-{i+1:02}.png').resize((384,216))
    sheet.paste(img,(8+(i%4)*400,8+(i//4)*236))
sheet.save(B/'contact-sheet.jpg',quality=90)
(B/'pdf-check.json').write_text(json.dumps(dict(pages=len(doc),embedded_font=True,text_metric_issues=0,bytes=target.stat().st_size),indent=2),encoding='utf-8')
print(json.dumps(dict(pages=len(doc),bytes=target.stat().st_size,text_metric_issues=0)))
