"""Scenario-led editable presentation: implemented demo flows and explicit sensor proposals."""
import json
import math
from pathlib import Path
from PIL import Image
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont

B = Path(__file__).resolve().parent
BASE = B.parent
ASSETS = BASE/'assets'
pdfmetrics.registerFont(TTFont('Malgun', 'C:/Windows/Fonts/malgun.ttf'))
pdfmetrics.registerFont(TTFont('MalgunBold', 'C:/Windows/Fonts/malgunbd.ttf'))
META = {p.name: dict(zip(['width', 'height'], Image.open(p).size)) for p in ASSETS.iterdir() if p.suffix in ['.png', '.jpg']}
W, H = 1600, 900
C = dict(bg='F5F6F1', ink='193139', muted='52676D', teal='286C61', dark='102830',
         mint='C5E2D8', pale='E5EEE7', coral='F0B6A5', amber='F3D9AA', lilac='DEDCEF', white='FFFFFF', line='CDD9D2')
S = []
REFS = {
    'app': 'LifeLink119 release v0.18.1: ui/AssistanceUi.kt, ui/EmergencySupportUi.kt, core/emergency, data/EmergencyHealthSummary.kt. APK: apk/LifeLink119_LifeLink119_release.apk. Release UI verification: docs/SUBMISSION_RELEASE_20261115.md.',
    'safecall': '소방청 119안심콜 공식 사이트: https://u119.nfa.go.kr/ . 본인 인증·정보 관리 경로와 Pleos 차량 자동 연계 구상을 구분한다. 공식 조회 API·제조사/기관 연동이 승인·구현됐다는 의미가 아니다. 검토: docs/SAFE_CALL_WEB_INTEGRATION_REVIEW.md.',
    'kdca': '질병관리청 국가건강정보포털 · 온열질환 예방과 응급조치: https://health.kdca.go.kr/healthinfo/biz/health/ntcnInfo/healthSourc/thtimtCntnts/thtimtCntntsView.do?thtimt_cntnts_sn=122',
    'nhtsa': 'NHTSA · Child Safety / Heatstroke: https://www.nhtsa.gov/road-safety/child-safety',
    'co': 'CDC · Carbon Monoxide Poisoning Basics: https://www.cdc.gov/carbon-monoxide/about/index.html',
    'co2': 'UK HSE · Using CO2 monitors: https://www.hse.gov.uk/ventilation/using-co2-monitors.htm . Building ventilation guidance is not a validated vehicle alarm threshold.',
    'ndir': 'Sensirion SCD30 NDIR CO2 sensor datasheet: https://sensirion.com/en/media/documents/4EAF6AF8/61652C3C/Sensirion_CO2_Sensors_SCD30_Datasheet.pdf . Technology example, not a certified automotive component selection.',
    'radar': 'TI · Child-presence and occupant-detection reference design: https://www.ti.com/tool/TIDEP-01023 . Capabilities require vehicle-specific validation; no accuracy figures are transferred to this app.',
    'hyundai': 'Hyundai SmartSense · Advanced Rear Occupant Alert, selected vehicles: https://www.hyundaiusa.com/us/en/safety . OEM function, not an integration delivered by LifeLink119.',
    'pleos': 'Pleos Vehicle SDK introduction: https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/Vehicle/intro ; API compatibility: https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/api-compatibility-availability . HVAC methods exist in the documentation; actual cabin measurement meaning, area, permission and target vehicle support are not yet verified in this app.',
    'hotline': 'Helpline tool verified 2026-11-15, South Korea: 자살예방상담전화 [109](tel:+82109?oai_link_source=model_response_hotline), [https://www.129.go.kr/109](https://www.129.go.kr/109?oai_link_source=model_response_hotline). This deck discusses product support, not the personal mental state of its reader.',
}

def wrap(text, width, size, bold=False):
    lines = []
    font = 'MalgunBold' if bold else 'Malgun'
    for para in text.split('\n'):
        if not para:
            lines.append(''); continue
        line = ''
        for char in para:
            if line and pdfmetrics.stringWidth(line+char, font, size) > width:
                space = line.rfind(' ')
                if space > len(line)*.4:
                    lines.append(line[:space]); line = line[space+1:]+char
                else:
                    lines.append(line); line = char
            else:
                line += char
        if line: lines.append(line)
    return lines

def t(s, text, x, y, w, size=30, color=None, bold=False, name='body', lh=None, align='left'):
    lh = lh or round(size*1.4)
    lines = wrap(text, w, size, bold)
    s['texts'].append(dict(text=text, lines=lines, x=x, y=y, w=w, h=len(lines)*lh+5,
                           size=size, lh=lh, color=color or C['ink'], bold=bold, name=name, align=align))

def shape(s, x, y, w, h, fill=None, stroke=None, kind='rect', sw=2, layer='under', commands=None):
    s['shapes'].append(dict(x=x, y=y, w=w, h=h, fill=fill, stroke=stroke, kind=kind, sw=sw,
                            layer=layer, commands=commands or []))

def im(s, file, x, y, w, h, crop=None):
    a = META[file]; crop = crop or [0, 0, a['width'], a['height']]
    cw, ch = crop[2]-crop[0], crop[3]-crop[1]; k = min(w/cw, h/ch)
    s['images'].append(dict(file=file, x=x+(w-cw*k)/2, y=y+(h-ch*k)/2, w=cw*k, h=ch*k,
                            crop=crop, sourceWidth=a['width'], sourceHeight=a['height']))

def path(s, commands, x, y, size, color):
    shape(s, x, y, size, size, stroke=color, kind='path', sw=size*.072, commands=commands)

def icon(s, name, x, y, size=85, color=None):
    c = color or C['teal']
    def p(cmd): path(s,cmd,x,y,size,c)
    def oval(a,b,w,h): shape(s,x+a*size/24,y+b*size/24,w*size/24,h*size/24,stroke=c,kind='ellipse',sw=size*.068)
    if name == 'phone':
        p([['M',5,3],['L',9,3],['L',11,8],['L',8.5,10.5],['C',9.7,12.8,11.2,14.3,13.5,15.5],['L',16,13],['L',21,15],['L',21,19],['C',21,20.1,20.1,21,19,21],['C',10.2,21,3,13.8,3,5],['C',3,3.9,3.9,3,5,3],['Z']])
    elif name == 'medical':
        p([['M',4,3],['L',4,7],['C',4,14,14,14,14,7],['L',14,3],['M',2,3],['L',6,3],['M',12,3],['L',16,3],['M',9,12],['L',9,16],['C',9,23,19,23,19,16],['L',19,14]])
        oval(16,8,6,6)
    elif name == 'headset':
        p([['M',4,13],['L',4,11],['C',4,1,20,1,20,11],['L',20,13],['M',4,11],['L',6,11],['L',6,18],['L',4,18],['C',1,18,1,11,4,11],['M',20,11],['L',18,11],['L',18,18],['L',20,18],['C',23,18,23,11,20,11],['M',19,18],['C',19,22,15,22,11,22]])
    elif name == 'family':
        oval(2,4,6,6); oval(16,4,6,6); oval(9,9,6,6)
        p([['M',1,21],['L',1,17],['C',1,11,8,11,8,16],['M',23,21],['L',23,17],['C',23,11,16,11,16,16],['M',8,23],['L',8,20],['C',8,15,16,15,16,20],['L',16,23]])
    elif name == 'mic':
        p([['M',9,5],['C',9,1,15,1,15,5],['L',15,11],['C',15,15,9,15,9,11],['Z'],['M',5,10],['C',5,21,19,21,19,10],['M',12,18],['L',12,22],['M',8,22],['L',16,22]])
    elif name == 'clock':
        oval(2,2,20,20); p([['M',12,6],['L',12,12],['L',17,15]])
    elif name == 'thermo':
        p([['M',9,14],['L',9,5],['C',9,0,15,0,15,5],['L',15,14],['C',22,21,13,26,8,21],['C',6,19,6,16,9,14],['M',12,7],['L',12,18],['M',17,6],['L',21,6],['M',17,10],['L',20,10]])
    elif name == 'sun':
        oval(7,7,10,10)
        for n in range(8):
            a=n*math.pi/4; p([['M',12+8*math.cos(a),12+8*math.sin(a)],['L',12+11*math.cos(a),12+11*math.sin(a)]])
    elif name == 'shield':
        p([['M',12,2],['L',21,6],['L',20,14],['C',19,19,14,22,12,23],['C',10,22,5,19,4,14],['L',3,6],['Z'],['M',7,12],['L',11,16],['L',17,9]])
    elif name == 'pin':
        p([['M',12,23],['C',6,17,2,12,4,7],['C',7,-1,20,0,21,9],['C',21,14,16,19,12,23],['Z']]); oval(9,6,6,6)
    elif name == 'radar':
        oval(2,2,20,20); oval(6,6,12,12); oval(10,10,4,4)
        p([['M',12,12],['L',21,3]])
    elif name == 'air':
        p([['M',2,6],['L',15,6],['C',22,6,21,0,17,2],['M',2,12],['L',19,12],['C',24,12,24,18,20,18],['M',2,18],['L',11,18],['C',16,18,15,24,11,22]])
    elif name == 'heart':
        p([['M',12,21],['C',-5,10,1,-1,10,5],['L',12,7],['L',14,5],['C',23,-1,29,10,12,21],['Z']])
    elif name == 'alert':
        p([['M',12,2],['L',23,22],['L',1,22],['Z'],['M',12,8],['L',12,14],['M',12,18],['L',12,18.3]])
    elif name == 'battery':
        p([['M',2,5],['L',19,5],['L',19,20],['L',2,20],['Z'],['M',19,10],['L',22,10],['L',22,15],['L',19,15],['M',6,9],['L',6,16],['M',11,9],['L',11,16]])

def new(title, sub='', dark=False, tag=''):
    n=len(S)+1; s=dict(number=n,title=title,background=C['dark'] if dark else C['bg'],texts=[],images=[],tables=[],shapes=[],notes='')
    S.append(s)
    if n>1:
        t(s,title,65,40,1460,49,C['white'] if dark else C['ink'],True,'title',65)
        if sub: t(s,sub,68,120,1450,27,C['mint'] if dark else C['muted'],lh=38)
        t(s,f'{n:02}',1470,850,65,24,C['mint'] if dark else C['teal'],True,'folio',33,'right')
        if tag: t(s,tag,70,845,1370,20,C['mint'] if dark else C['muted'],name='category',lh=28)
    return s

def notes(s, text, refs=('app',)):
    s['notes']=s['title']+'\n\n발표자 설명\n'+text+'\n\n근거·출처\n'+'\n'.join(REFS[k] for k in refs)
    for pic in s['images']:
        s['notes']+='\n이미지 입력: assets/'+pic['file']
    if any(p['file']=='hyundai-family-hero.png' for p in s['images']):
        s['notes']+='\n사용자가 제공한 현대 SUV와 동일한 골든 리트리버의 두 사진을 참고한 가족·반려견 AI 합성 이미지. 실제 가족 또는 현대자동차 공식 광고가 아님. 원 프롬프트: assets/hyundai-family-hero.prompt.txt.'

def three(s, items, y=270, icons=None, dark=False):
    for i,(head,body) in enumerate(items):
        x=75+515*i
        if icons: icon(s,icons[i],x,y-78,62,C['mint'] if dark else C['teal'])
        t(s,head,x,y+15,430,34,C['mint'] if dark else C['teal'],True,lh=48)
        t(s,body,x,y+92,430,30,C['white'] if dark else C['ink'],lh=49)

def screen(s, file, crop, steps):
    im(s,file,65,215,980,560,crop)
    for i,(head,body) in enumerate(steps):
        y=230+i*180
        t(s,head,1100,y,430,31,C['teal'],True,lh=44)
        t(s,body,1100,y+58,430,28,lh=43)

def strip(s, text, y=752, fill=None, ink=None):
    shape(s,70,y,1460,78,fill or C['pale'],kind='roundRect')
    t(s,text,92,y+17,1410,27,ink or C['teal'],True,lh=38)

def band(s, number, title, detail, y, color=None):
    shape(s,75,y,70,70,color or C['mint'],kind='ellipse')
    t(s,number,87,y+12,48,28,C['ink'],True,lh=39,align='right')
    t(s,title,180,y-1,1260,31,C['teal'],True,lh=45)
    t(s,detail,180,y+53,1250,28,lh=42)

# 01
s=new('LifeLink 119',dark=True)
im(s,'hyundai-family-hero.png',760,175,800,570)
t(s,'LifeLink 119',70,98,670,65,C['white'],True,'title',86)
t(s,'나와 내 가족을 위한\n안전 동행',70,228,675,58,C['white'],True,'title',85)
t(s,'위급상황에서는 1분 1초가\n생명을 살릴 수 있습니다',73,450,650,31,C['mint'],lh=52)
t(s,'긴급신고 · 상담 · 신고 후 대기 안내',73,704,670,27,C['mint'],lh=40)
t(s,'최종 설명자료 · 앱 v0.18.1 · 2026.11.15',73,780,650,23,C['mint'],lh=33)
notes(s,'가족을 위한 차량형 응급지원이라는 서비스 방향을 소개한다. 기존의 실제 앱 화면을 이용한 사용 안내를 상황 중심으로 개편했다. 후반 온도·공기질·잔류 감지는 신규 제품 제안이며 현재 센서 연동 구현을 뜻하지 않는다.')

# 02
s=new('도움이 필요한 순간은 세 가지입니다','갑자기 다쳤을 때, 상담이 필요할 때, 도움을 기다릴 때')
three(s,[('긴급신고','다친 사람·화재·구조 요청은 119\n위협·범죄 상황은 112\n위치와 상황을 짧게 전달'),('상담 연결','응급처치 문의 · 119\n심리적 위기 · 109\n상황에 맞는 전문 도움'),('신고 후 안내','119 연결이 늦어질 때 상태 확인\n악화하면 다시 알릴 내용 준비\n구조대에게 전할 요약 유지')],y=315,icons=['phone','headset','clock'])
strip(s,'화면은 필요한 진입점만, 질문은 하나씩, 답변은 짧은 음성으로')
notes(s,'이 자료에서 연결은 현재 앱이 제공하는 기관 연결 화면·모의 흐름을 설명한다. 실제 기관 발신·접수·출동 수신은 현재 구현되지 않았다. 전문상담은 AI가 대신하지 않는다.',('app','hotline'))

# 03
s=new('홈에서 지금 필요한 도움을 고릅니다','가족이 같은 화면에서 긴급신고·응급상담·마음상담으로 들어갑니다')
screen(s,'home.jpg',[40,310,1645,1050],[('119 긴급신고','사고·부상·화재 등 긴급한 도움을 요청할 때'),('119 응급상담 · 109','몸의 응급처치 상담과 심리적 위기 상담을 구분'),('가족 안심콜','평소 저장한 건강정보를 이번 환자에게 연결')])
notes(s,'현대차와 가족 이미지를 적용한 현재 앱 홈이다. 기관별 진입점과 가족정보 메뉴가 있으며 세부 조작을 반복하지 않도록 음성 대기 동행과 연결한다.')

# 04
s=new('사례 1 · 가족 나들이 중 어머니가 가슴 통증을 호소합니다','충돌 신호가 없어도 필요한 도움을 한 번에 열고, 현재 상황을 정리합니다',tag='현재 앱 모의 시연')
screen(s,'call.jpg',[40,365,1645,820],[('01 · 동승 가족이 신고','홈의 119 긴급신고를 한 번 누릅니다.'),('02 · 응급 요약','확인한 위치·차량 상태와 환자가 말한 증상을 모읍니다.'),('03 · AI 음성 동행','연결이 늦어지면 한 질문씩 듣고 짧게 답합니다.')])
notes(s,'첫 사례는 충돌이 아닌 가족 나들이 중 갑작스러운 응급질환이다. 제조사의 기존 충돌 자동 통보와 다른 지원 상황을 설명한다. 환자가 가슴 통증을 말했다는 사용자 진술을 기록하며 원인이나 진단을 확정하지 않는다. 실제 통화 중에는 상담원 지시가 우선이고 앱 음성을 중단한다. 현재 연결은 모의 시연이다.')

# 05
s=new('질문을 듣고 말하면 다음 단계로 이어집니다','화면을 계속 누르지 않고 현재 질문에만 답합니다',tag='현재 앱 흐름')
im(s,'question.jpg',70,215,920,430,[40,225,1640,805])
icon(s,'mic',1100,213,92)
t(s,'AI',1100,333,360,27,C['teal'],True)
t(s,'“환자는 의식이 있나요?”',1100,387,430,33,bold=True,lh=48)
t(s,'사용자',1100,497,360,27,C['teal'],True)
t(s,'“예”\n“아니요”\n“잘 모르겠어요”',1100,546,430,30,lh=49)
strip(s,'잘 듣지 못하면 다시 확인합니다. 무응답을 정상 상태로 기록하지 않습니다.')
notes(s,'현재 음성 동행은 TTS가 끝난 다음 STT로 듣는다. 외부 LLM이 선택한 의미 코드는 확인 질문을 거치며 자유 의료 문장을 출력하지 않는다. 화면 답변을 원하면 같은 질문에 세 응답만 표시한다.')

# 06
s=new('상담원에게 위치와 환자 상태를 짧게 전달합니다','차량에서 확인한 정보와 사람이 말한 내용을 나누어 정리합니다',tag='현재 앱 흐름')
im(s,'packet.jpg',70,228,1460,340,[40,474,1640,790])
three(s,[('위치·차량','측정 시각과 정확도\n차량 신호의 출처\n확인 불가 항목도 표시'),('환자 상태','의식·정상 호흡에 대한 응답\n흉통·출혈 등 사용자 진술\n증상이 달라진 시각'),('가족정보','선택한 환자의 병력·복약\n알레르기·의사소통 참고\n보호자 연락처')],y=590)
notes(s,'현재 패킷 예시의 좌표와 에어백 값은 합성 시나리오다. 위치 등 실제 값은 출처·측정 시각과 함께 표시하며 오래된 자료를 현재 값으로 대체하지 않는다. 공식 안심콜 DB를 자동 가져오는 기능은 없고 차량 저장본을 명시적으로 선택한다.')

# 07
s=new('사례 2 · 119 연결이나 접수가 늦어집니다','확인한 연결 상태를 말하면 상황을 유지하며 음성 동행으로 이어집니다',tag='현재 앱 모의 시연')
im(s,'callback.jpg',70,210,1460,535,[55,190,1625,765])
t(s,'“연결이 안 돼요” · “다시 전화 준대요”처럼 확인한 상태를 말하면 AI가 다음 질문을 이어갑니다.',73,779,1450,28,C['teal'],True,lh=40)
notes(s,'콜백 대기는 명시적인 사용자 진술로 기록한다. 앱이 콜백 예약을 확인하거나 통화 연결을 자동 판단하는 것이 아니다. 접수·출동 상태를 알 수 없는 경우 확인되지 않음으로 유지한다. 긴급 재연결은 언제나 접근할 수 있다.')

# 08
s=new('사례 3 · 구급차를 기다리다 증상이 달라졌습니다','상태 악화는 추가 질문보다 다시 알리는 행동을 우선합니다',tag='현재 앱 흐름')
screen(s,'worsening.jpg',[40,190,1640,690],[('사용자 답변','상태가 나빠졌는지 묻는 질문에 “예”라고 답합니다.'),('AI 안내','준비된 안내를 읽고 119에 변화를 다시 알리도록 돕습니다.'),('인계 요약','처음 상태와 현재 상태, 달라진 시각을 함께 남깁니다.')])
notes(s,'출동 대기·상태 악화는 앱 시뮬레이션과 사용자 응답에 근거한다. AI는 구급차 도착 시간·위치 또는 출동 확정을 만들어내지 않는다. 호흡 이상 등 명확한 위험은 긴 증상 설문을 중지한다. 임상 콘텐츠는 의료전문가 검토 전이다.')

# 09
s=new('사례 4 · 응급처치가 궁금해 상담이 필요합니다','119 응급상담에서 상태와 궁금한 점을 전문 상담원에게 설명합니다',tag='현재 앱 흐름')
screen(s,'medical119.jpg',[40,395,1645,960],[('응급상담 진입','홈에서 119 응급상담을 선택합니다.'),('설명할 정보 준비','증상·발생 시각·현재 위치와 선택한 건강정보를 확인합니다.'),('긴급해지면 전환','의식·호흡 이상 등 급박한 상황은 긴급신고를 우선합니다.')])
notes(s,'현재 앱은 응급상담 연결 화면과 응급 요약 경로를 제공한다. 전문 상담원과 실제 통화하는 기능은 기관·전화 플랫폼 연결 단계가 필요하다. AI가 진단이나 약물 복용 여부를 대신 결정하지 않는다.')

# 10
s=new('사례 5 · 차 안에서 자살 위기에 놓였습니다','109 마음상담을 가까이 두고, 즉각적인 신체 위험에는 긴급 도움을 우선합니다')
screen(s,'counsel109.jpg',[40,365,1640,1015],[('현재 · 마음상담 화면','홈의 109 마음상담으로 연결 경로에 들어갑니다.'),('확장안 · 짧은 안전 확인','“지금 안전한 곳에 계신가요?” 같은 질문 한 가지로 시작합니다.'),('즉각적인 위험','신체 응급상황은 119, 신변 위협은 112 도움을 우선합니다.')])
notes(s,'사용자가 요청한 차량 내 자살 예방이라는 제품 시나리오다. 현재 109 연결 화면은 있으나 정신건강 위험 평가·전용 AI 대화는 미구현이다. 센서·독차·위치·무응답만으로 자살 의도나 우울증을 추정하지 않는다. 전용 대화는 전문가 검토와 위기 개입 정책이 필요한 확장안이다. 자해 수단이나 방법에 대한 설명은 포함하지 않는다.',('app','hotline'))

# 11
s=new('혼자 탑승한 사람의 무응답도 살필 수 있어야 합니다','차량 내 고립·고독사 위험에 대비해 신체 응급상황과 심리적 위기를 구분합니다',tag='무응답 감지·보호자 알림은 센서·제조사 연동 확장안')
three(s,[('위험 환경을 먼저 확인','실내 온도·공기질\n차량 정차·전원 상태\n탑승자 존재 신호'),('답할 수 있는지 질문','한 번의 짧은 음성 확인\n응답 없음은 확인 불가\n잠·통신 실패도 구분 필요'),('도움으로 이어지는 설계','확인 가능한 신호만 전달\n사전 동의한 보호자 알림\n승인된 긴급서비스와 연계')],y=315,icons=['thermo','mic','phone'])
strip(s,'원인을 단정하는 대신, 확인 가능한 신호와 실제 도움 요청에 따라 지원합니다.')
notes(s,'차량고독사라는 표현은 사망 원인을 의미하지 않는다. 신체 질환, 열·가스 노출, 고립, 심리 위기 등 지원 경로를 나누어 설명한다. 무응답 자동 감지·원격 알림·사망 판단 기능은 현재 구현되어 있지 않다. 사망이나 의도를 센서로 진단하지 않는다.',('app','hotline','nhtsa','co'))

# 12
s=new('안심콜 본인 정보를 차량의 응급 요약으로 연결합니다','본인 조회와 차량 자동 연계를 통해 병력·복약을 다시 설명하는 부담을 줄이는 구상입니다',tag='현재: 차량 저장본 활용 / 확장: 공식 본인 조회·자동 연계')
three(s,[('본인 조회 · 연계 설계','본인 인증과 공유 동의\n소방청 안심콜 정보 조회\n현대차 Pleos 차량으로 연계'),('차량 · 안전하게 보관','병력·복용약·알레르기\n거동·통화 어려움·보호자\n암호화 저장과 최신 정보 확인'),('응급상황 · 전달 준비','이번 환자 한 명을 확인\n현재 증상과 대기 중 변화 결합\n상담원에게 전할 내용 요약')],y=315,icons=['family','shield','medical'])
strip(s,'본인 조회·자동 연계에는 공식 연계 방식과 인증·권한 검증이 필요합니다.')
notes(s,'공식 안심콜 사이트에서 본인이 인증하여 정보를 조회·관리하는 경로를 현대차 Pleos 앱의 정보 활용 흐름에 연결하는 설계다. 앱의 현재 기능은 직접 입력한 로컬 암호화 건강정보를 선택한 환자의 응급 요약에 포함하는 것과 공식 사이트 안내다. 공식 로그인 세션·문자 인증·DB 조회 API 자동 연계가 구현 또는 승인된 것으로 표시하지 않는다. 본인 자료는 본인 인증·동의, 가족은 당사자 동의나 적법한 대리 권한, 최신성·출처 확인, 철회·삭제가 필요하다. 건강정보는 외부 LLM에 보내지 않는다. 보안 화면은 캡처하지 않고 절차로 설명한다.',('app','safecall'))

# 13
s=new('신고 이전의 위험을 알려주는 기능으로 확장합니다','사고가 없더라도 탑승자가 도움이 필요한 상황이 생길 수 있습니다',tag='신규 탑승자 안전 기능 제안')
three(s,[('폭염·고온 노출','차 안의 열 축적\n아이·고령자 등 잔류\n온도 변화와 반응 확인'),('캠핑·차박·장시간 대기','환기 상태의 변화\nCO₂와 CO를 별도 측정\n전원 상태·센서 이상 확인'),('혼자 탑승·도움 요청','사용자의 상태 확인\n무응답 시 확인 경로 유지\n전문상담·긴급 도움 연결')],y=315,icons=['sun','air','heart'])
strip(s,'차량의 위험 신호를 알아차리고, 짧게 묻고, 적절한 도움으로 이어가는 경험')
notes(s,'예방 확장 아이디어를 소개한다. 현재 앱이 환경 센서를 측정하거나 차량 시동 종료 후 감시를 수행하지는 않는다. 연동 검증 전에 사망 예방 효과나 임상 정확도·안전 보장을 주장하지 않는다.',('nhtsa','co','co2','app'))

# 14
s=new('사례 6 · 폭염으로 차 안의 열이 쌓입니다','온도 상승을 알리고, 탑승자가 안전하게 벗어날 수 있는지 확인합니다',tag='센서 연동 확장안')
band(s,'1','위험을 알아차림','실내 온도·상승 추세·습도와 탑승 신호를 함께 확인합니다.',235,C['amber'])
band(s,'2','짧은 음성 질문','정차 후: “차 안이 많이 덥습니다. 안전하게 내릴 수 있나요?”',395,C['mint'])
band(s,'3','상태에 맞는 도움','의식 저하·심한 증상·스스로 벗어나기 어려운 경우 119 도움을 우선합니다.',555,C['coral'])
strip(s,'실내 온도는 체온과 다릅니다. 냉방 설정값을 실제 실내 온도로 표시하지 않습니다.')
notes(s,'고온의 차 안에 남은 탑승자에게 먼저 경고하는 제품 시나리오다. KDCA는 온열질환의 심한 증상·의식 소실 시 119 요청과 냉각을 안내한다. 안내는 안전한 위치·정차를 전제로 하며 의식 없는 사람에게 음료를 주지 않는다. 한 숫자로 사람의 안전 또는 열사병을 판단하지 않는다. 경고 임계값·지속시간·복합 조건은 차종·센서 위치와 임상 검토를 거쳐야 하며 이 자료는 의학적 임계값을 정하지 않는다.',('kdca','nhtsa','pleos'))

# 15
s=new('사례 7 · 주차 후 아이가 차 안에 남았습니다','존재 신호와 주변 환경을 함께 살피는 잔류 탑승자 지원을 제안합니다',tag='제조사 잔류 감지·저전력 감시·원격 알림 협력 필요')
icon(s,'family',95,235,135)
t(s,'한 가지 신호로\n빈 차라고 단정하지 않습니다',300,235,1120,42,C['teal'],True,lh=65)
three(s,[('존재 확인','실내 레이더·좌석 신호\n도어 이력·전원 상태\n서로 다른 신호를 교차 확인'),('환경 확인','온도와 상승 추세\n센서 시각·통신 상태\n움직임 없음도 확인 불가'),('경고와 도움','차량 내 경고\n사전 동의한 보호자 알림\n공식 긴급서비스와 연계')],y=465)
notes(s,'NHTSA는 어린이를 차 안에 혼자 두지 말라고 안내한다. 기능은 이를 대체하지 않는다. 실제로 일부 현대차에는 OEM Rear Occupant Alert가 있으므로 새로운 잔류 감지 자체를 독창적 기능으로 주장하지 않는다. 앱의 차별화 제안은 경고 뒤에 반응 확인과 신고 요약·대기 지원을 잇는 것. 현재 LifeLink119에서 잔류 감지·원격 알림을 수행하지 않는다.',('nhtsa','hyundai','radar'))

# 16
s=new('사례 8 · 차박·캠핑 중 공기질을 확인합니다','CO₂와 CO는 다른 가스이며, 환기 확인과 유해가스 경고에는 서로 다른 센서가 필요합니다',tag='차량용 검증을 거친 전용 센서가 필요')
for x,label,name,desc,items,fill in [
    (75,'CO₂','이산화탄소','호흡·환기 상태를 살피는 지표','NDIR 등 전용 CO₂ 센서\n농도 변화·환기 맥락 확인\n낮은 값이 다른 가스의 안전을 뜻하지 않음',C['mint']),
    (835,'CO','일산화탄소','별도로 감지해야 하는 유독가스','전기화학식 등 전용 CO 센서\n경고·증상이 있으면 안전 확보와 도움 요청\nCO₂ 센서나 냄새로 대신 판단하지 않음',C['coral'])]:
    shape(s,x,215,690,535,fill,kind='roundRect')
    t(s,label,x+32,242,610,89,C['ink'],True,lh=120)
    t(s,name,x+34,385,600,38,C['ink'],True,lh=54)
    t(s,desc,x+34,461,610,29,lh=43)
    t(s,items,x+34,543,610,27,lh=48)
notes(s,'CO₂는 전용 센서가 있으면 측정 가능하다. 온도·스마트폰 마이크만으로 농도를 산출하지 않는다. HSE의 건물 환기 숫자를 차량의 안전·위험 임계값으로 가져오지 않는다. CO는 무색·무취로 급성 중독과 사망을 일으킬 수 있어 별도 측정이 필요하다. CO₂가 낮아도 CO 노출을 배제하지 못한다. 구체적인 센서·인증·경보 조건은 향후 안전 평가 대상이다.',('co','co2','ndir'))

# 17
s=new('온도 경고는 큰 문장 하나와 음성 질문으로','여러 센서를 사용해도 사용자가 매번 여러 항목을 고르지 않게 합니다',tag='확장 화면안 · 실제 앱에 없는 가상 센서 예시')
shape(s,70,207,1460,555,C['dark'],kind='roundRect')
t(s,'차 안 온도가 높아졌습니다',115,243,1300,47,C['white'],True,lh=68)
icon(s,'thermo',124,353,92,C['coral'])
t(s,'42°C',256,332,450,91,C['coral'],True,lh=120)
t(s,'가상 실내 측정값 · 5초 전\n의학적 위험 판정값이 아닌 화면 예시',260,465,1080,24,C['mint'],lh=37)
shape(s,111,569,1378,136,C['mint'],kind='roundRect')
icon(s,'mic',132,595,63,C['teal'])
t(s,'“지금 정차 중입니다. 안전하게 내릴 수 있나요?”',220,594,1220,34,C['ink'],True,lh=49)
t(s,'CO₂: 전용 센서 필요     CO: 전용 센서 필요     미수신 값은 빈칸·확인 불가로 표시',112,785,1370,25,C['muted'],lh=36)
notes(s,'현재 앱 캡처가 아닌 편집 가능한 제안 화면이다. 42°C와 5초는 가상 값이며 경고 임계값·노출 한계가 아니다. 경보 시 먼저 필요한 안내와 질문 하나만 제시하는 UX를 보여준다. 이 제안은 주행 상태 확인 뒤 정차 상황에서 사용하며 자동 공조·창문 제어 또는 실제 센서 수신 구현을 의미하지 않는다.',('kdca','pleos'))

# 18
s=new('센서는 실내 환경과 탑승자의 존재를 나누어 봅니다','온도·가스 센서와 실내 레이더·좌석 신호를 함께 검토합니다',tag='현대차 사진은 개념 설명용 · 실제 센서 위치와 차종 적용은 별도 설계')
im(s,'hyundai-suv-reference.png',395,220,790,505)
for x,y,name,head,body in [(75,230,'thermo','온도·습도','일사·송풍구 영향을 검토'),(1225,230,'radar','실내 레이더','좌석별 존재·움직임 참고'),(75,570,'air','CO₂ · CO','가스별 전용 센서 사용'),(1225,570,'family','좌석·도어 신호','다른 센서와 교차 확인')]:
    icon(s,name,x,y,67)
    t(s,head,x,y+88,300,29,C['teal'],True,lh=43)
    t(s,body,x,y+146,300,25,lh=38)
notes(s,'사용자가 제공한 현대차 사진을 그대로 배치했다. 도식은 특정 차종의 실제 장착 위치나 탑재 기능을 확인한 그림이 아니다. 레이더는 촬영 영상 없이 탑승자 존재를 살필 수 있는 후보 기술이며 수면·반응·의식을 단독 확정하지 않는다. 좌석 하중은 짐과 구분하는 검증이 필요하다.',('pleos','radar','ndir'))

# 19
s=new('탑승자 안전을 위해 우선 검토할 센서','기능의 목적과 센서의 한계를 함께 정의합니다',tag='센서 후보 · 상용 부품 선정과 차량 인증 전')
s['tables'].append(dict(x=70,y=213,w=1460,h=546,columns=[275,400,450,335],rowHeight=78,
    rows=[['센서·신호','알 수 있는 정보','도움이 되는 장면','검증할 점'],
          ['온도·습도','실내 열 환경·변화','폭염·정차 중 열 축적 경고','위치·일사·단위'],
          ['CO₂ 전용 센서','이산화탄소 농도','장시간 대기 중 환기 확인','보정·가스 구분'],
          ['CO 전용 센서','일산화탄소 농도','차박·캠핑 중 유해가스 경고','수명·교차 감도'],
          ['실내 mmWave','존재·미세 움직임','주차 후 잔류 탑승자 확인','차종별 검출 검증'],
          ['좌석·벨트·도어','착석·승하차 맥락','존재 신호의 교차 확인','짐·오탐·이력 한계'],
          ['전원·통신 상태','감시 지속 가능 여부','시동 종료 후 경고 유지','저전력·단절 알림']],
    fontSize=25,headerFill=C['teal'],headerColor=C['white'],bodyColor=C['ink'],bodyFill='E7EEE7',lineColor=C['line']))
t(s,'일반 VOC/미세먼지 센서 하나로 CO₂·CO를 함께 측정했다고 표시하지 않습니다.',75,791,1420,27,C['teal'],True,lh=39)
notes(s,'NDIR CO₂, 전기화학식 CO, 실내 mmWave는 원리를 설명하는 후보이다. 특정 산업용 부품의 사양을 그대로 차량 인증·개인 안전 성능으로 주장하지 않는다. 센서 위치, 보정·온습도 영향, 수명, 진단, 저전력 전원·연결성, 제조사 API 권한을 검토한다. 안면 영상·생체 추정은 기본 수집 범위에 넣지 않는다.',('ndir','radar','co','pleos'))

# 20
s=new('센서가 연결돼도 네 단계를 확인해야 합니다','신호가 없거나 오래되면 위험이 없다고 판단하지 않습니다',tag='환경 감시의 처리 흐름 제안')
for i,(head,body,ic) in enumerate([
    ('01 · 실제 측정','센서 종류·단위\n측정 위치·보정 상태','thermo'),
    ('02 · 유효성 확인','측정 시각·통신 상태\n값 범위·고장 신호','shield'),
    ('03 · 짧은 안내','주행·정차 맥락 확인\n필요한 질문 하나','mic'),
    ('04 · 도움 연결','정해진 경고 절차\n사용자·공식기관 확인','phone')]):
    x=75+i*385
    icon(s,ic,x,253,79)
    t(s,head,x,382,330,32,C['teal'],True,lh=46)
    t(s,body,x,463,325,28,lh=49)
strip(s,'주차 후 감시에는 제조사 백그라운드 서비스·전원·통신 지원이 함께 필요합니다.')
notes(s,'현재 일반 AAOS 앱의 생명주기만으로 시동 종료 뒤 감시를 보장할 수 없다. 휴대폰 연결 끊김을 차량 비움으로 간주하지 않는다. 유효하지 않은 수치는 자동 제어나 기관 신고의 근거가 되지 않는다. 지속 시간·복수 신호·오탐 정책과 안전한 대체 경로는 제조사·전문가 검증을 거친다.',('pleos','hyundai','co2'))

# 21
s=new('AI는 질문과 정리를 돕고, 지침은 통제합니다','위험 대응을 자유 생성된 의료 문장에 맡기지 않습니다')
band(s,'1','고정된 위험 안내','호흡 이상·화재·심한 출혈·급격한 악화는 준비된 절차를 먼저 안내합니다.',227)
band(s,'2','불확실한 응답은 다시 확인','AI 해석은 사용자 확인을 거치고, 무응답·모름을 정상으로 바꾸지 않습니다.',390)
band(s,'3','통화와 전문 도움을 우선','상담원과 통화 중에는 앱 음성을 멈추고 전달할 상태 요약을 유지합니다.',553)
strip(s,'추가하는 폭염·가스·심리 위기 절차도 전문가 검토와 오인식 시험을 거쳐야 합니다.')
notes(s,'기존 LLM은 허용된 문장 ID·의미 코드만 반환한다. 앱과 서버 양쪽 검사를 거치며 자유 진단·치료 문장은 읽지 않는다. 외부 모델이 실패해도 기존 고정 안내는 유지된다. 검증된 절차라는 개발상 표현을 임상적으로 무오류임을 뜻하는 것으로 사용하지 않는다. 신규 열·가스·심리 위기 콘텐츠는 제품 제안이며 추가 임상·안전 검증이 필요하다.',('app','kdca','hotline'))

# 22
s=new('기존 차량 경고 뒤에 지원 경험을 이어 붙입니다','현대차의 후석 승객 알림 같은 안전 기능과 협력하는 방향입니다')
shape(s,75,237,670,360,C['pale'],kind='roundRect')
shape(s,855,237,670,360,C['mint'],kind='roundRect')
icon(s,'radar',110,270,75)
icon(s,'mic',890,270,75)
t(s,'제조사 안전 기능',110,385,600,36,C['teal'],True,lh=51)
t(s,'탑승자 잔류 감지·차량 경고\n일부 차종의 후석 승객 알림',110,463,590,29,lh=48)
t(s,'LifeLink가 연결할 경험',890,385,600,36,C['teal'],True,lh=51)
t(s,'반응 확인 → 도움 요청 준비\n상담·신고 요약 → 대기 중 안내',890,463,590,29,lh=48)
t(s,'→',771,380,65,54,C['teal'],True,lh=76)
strip(s,'차별화 제안: 경고를 받은 뒤 사용자가 무엇을 해야 하는지까지 짧게 이어주는 것')
notes(s,'Hyundai Advanced ROA는 일부 차종에서 후석 움직임 감지·차량 경고·Bluelink 알림 등을 제공한다. 지역·연식·옵션·서비스 요건이 다르므로 모든 현대차에 있다고 설명하지 않는다. LifeLink119가 이미 그 상태를 받거나 알림·차량 제어를 연동한 것은 아니다. 기존 OEM 보호 기능을 대체하지 않는 협력 제안이다.',('hyundai','app'))

# 23
s=new('현재 체험할 기능과 다음 개발 범위를 구분합니다','설명자료의 확장 화면은 제품 방향을 구체화한 제안입니다')
s['tables'].append(dict(x=70,y=216,w=1460,h=504,columns=[320,740,400],rowHeight=84,
    rows=[['영역','기능','개발 단계'],
          ['기존 사용 흐름','기관별 연결 화면·응급 요약·음성 동행','현재 앱에서 모의 체험'],
          ['가족 건강정보','환자 선택 후 차량 저장본 활용','현재 앱에서 체험'],
          ['온도·CO₂·CO','실내 경고·짧은 음성 확인','화면·센서 연동 제안'],
          ['잔류·무응답','존재 감지·저전력 감시·보호자 알림','제조사·센서 협력 필요'],
          ['안심콜·기관 연계','본인 조회·차량 자동 연계·공식 상태 수신','기관 협력·승인 필요']],
    fontSize=27,headerFill=C['teal'],headerColor=C['white'],bodyColor=C['ink'],bodyFill='E7EEE7',lineColor=C['line']))
t(s,'신규 센서 기능은 아직 앱에 연결하지 않았으며, 의료·실차 안전성 검증을 거쳐야 합니다.',75,768,1440,27,C['muted'],lh=42)
notes(s,'반복되는 일괄 하단 문구 대신 기능별 구현 범위를 이 페이지와 각 제안 화면에 명확히 표시한다. 현재 앱은 실제 번호 발신, 기관 접수·출동 상태 수신, 공식 안심콜 DB 자동 조회를 수행하지 않는다. 환경 경고 및 심리 위기 전용 AI는 이번 자료에서 제안한 범위다.',('app','pleos'))

# 24
s=new('발표에서는 네 가지 상황을 차례로 보여줍니다','기존 앱의 동작과 확장 제안 화면을 구분해 설명합니다',dark=True)
for i,(head,body) in enumerate([
    ('1 · 긴급상황','홈 → 119 긴급신고 → 응급 요약'),
    ('2 · 상담','119 응급상담 · 109 마음상담의 진입점 소개'),
    ('3 · 신고 후 대기','AI 질문·답변 → 상태 변화 → 인계 요약'),
    ('4 · 예방 확장','폭염 경고 화면안 → CO₂/CO → 필요한 센서')]):
    y=227+i*132
    t(s,head,75,y,355,34,C['mint'],True,lh=50)
    t(s,body,475,y+1,1040,31,C['white'],lh=47)
t(s,'제출 APK 실행 후: 더보기에서 “음성 동행 자동 시연”을 선택합니다',75,788,1440,25,C['mint'],lh=38)
notes(s,'현재 앱 자동 시연은 합성 응답으로 진행된다. 최초 설정은 발표 전 준비한다. 신규 센서 기능을 시연 앱에서 실행한 것처럼 설명하지 않고 이 자료의 화면안과 센서 도식으로 설명한다. 원본 현대차 사진 기반 가족 이미지와 기존 UI의 색상 체계를 유지한다.')

assert len(S)==24
for s in S:
    for el in s['texts']+s['images']+s['tables']+s['shapes']:
        if min(el['x'],el['y'])<0 or el['x']+el['w']>W+1 or el['y']+el['h']>H+1:
            raise ValueError(f"Out of bounds slide {s['number']}: {el.get('text',el.get('file',el.get('kind','table')))}")
    for el in s['texts']:
        if el['name'] not in ['folio','category'] and s['number']>1 and el['y']+el['h']>838:
            raise ValueError(f"Content hits bottom on slide {s['number']}: {el['text']}")

(B/'slide-models.json').write_text(json.dumps(dict(width=W,height=H,font='Malgun Gothic',title='LifeLink 119 최종 설명자료 · v0.18.1',slides=S),ensure_ascii=False,indent=2),encoding='utf-8')
(B/'source-manifest.json').write_text(json.dumps(REFS,ensure_ascii=False,indent=2),encoding='utf-8')
(B/'asset-metadata.json').write_text(json.dumps(META,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps(dict(slides=len(S),nativeTables=[s['number'] for s in S if s['tables']],nativeShapes=sum(len(s['shapes']) for s in S))))
