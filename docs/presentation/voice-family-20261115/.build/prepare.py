"""Editable slide layout and factual copy from the current app's usage guide."""
import json, shutil
from pathlib import Path
from PIL import Image
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont

BUILD = Path(__file__).resolve().parent
BASE = BUILD.parent
ROOT = BASE.parents[2]
ASSETS = BASE / 'assets'
ASSETS.mkdir(exist_ok=True)
(BASE / 'output').mkdir(exist_ok=True)
pdfmetrics.registerFont(TTFont('Malgun', 'C:/Windows/Fonts/malgun.ttf'))
pdfmetrics.registerFont(TTFont('MalgunBold', 'C:/Windows/Fonts/malgunbd.ttf'))

sources = {
    name: 'docs/presentation/voice-family-20261115/assets/' + name
    for name in [
        'home.jpg', 'manual.jpg', 'hyundai-family-hero.png', 'first-run.jpg',
        'question.jpg', 'listening.jpg', 'callback.jpg', 'packet.jpg',
        'worsening.jpg', 'automatic.jpg', 'handover.jpg', 'call.jpg', 'more.jpg',
    ]
}
for name, source in sources.items():
    if (ROOT/source).resolve() != (ASSETS/name).resolve(): shutil.copy2(ROOT/source, ASSETS/name)
meta = {p.name: dict(zip(['width', 'height'], Image.open(p).size)) for p in ASSETS.iterdir() if p.suffix in ['.png', '.jpg']}

W, H = 1600, 900
C = dict(bg='F5F6F1', ink='1A3037', muted='52656D', accent='276D61', dark='102830', mint='B8DDD2', light='F6F7F2', warn='835427')
slides = []

def wrap(s, width, size, bold=False):
    font = 'MalgunBold' if bold else 'Malgun'
    lines = []
    for para in s.split('\n'):
        if not para: lines.append(''); continue
        line = ''
        for char in para:
            if line and pdfmetrics.stringWidth(line + char, font, size) > width:
                space = line.rfind(' ')
                if space > len(line) * .4:
                    lines.append(line[:space]); line = line[space+1:] + char
                else:
                    lines.append(line); line = char
            else: line += char
        if line: lines.append(line)
    return lines

def t(s, text, x, y, w, size=29, color=None, bold=False, name='body', lh=None, align='left'):
    lh = lh or round(size * 1.4)
    lines = wrap(text, w, size, bold)
    obj = dict(text=text, lines=lines, x=x, y=y, w=w, h=len(lines)*lh+5, size=size, lh=lh,
               color=color or C['ink'], bold=bold, name=name, align=align)
    s['texts'].append(obj)
    return obj

def im(s, file, x, y, w, h, crop=None):
    a = meta[file]
    crop = crop or [0, 0, a['width'], a['height']]
    cw, ch = crop[2]-crop[0], crop[3]-crop[1]
    k = min(w/cw, h/ch)
    obj = dict(file=file, x=x+(w-cw*k)/2, y=y+(h-ch*k)/2, w=cw*k, h=ch*k,
               crop=crop, sourceWidth=a['width'], sourceHeight=a['height'])
    s['images'].append(obj)

def new(title, subtitle='', dark=False):
    n=len(slides)+1
    s=dict(number=n,title=title,background=C['dark'] if dark else C['bg'],texts=[],images=[],tables=[],notes='')
    slides.append(s)
    if n != 1:
        t(s,title,65,40,1460,49,C['light'] if dark else C['ink'],True,'title',65)
        if subtitle: t(s,subtitle,68,119,1450,27,C['mint'] if dark else C['muted'],False,'subtitle',38)
        t(s,'시연 앱 · 실제 전화 발신, 기관 접수·출동 연동 없음',68,853,1400,19,C['mint'] if dark else C['muted'],False,'footer',27)
        t(s,f'{n:02}',1467,849,65,25,C['mint'] if dark else C['accent'],True,'folio',33,'right')
    return s

def notes(s, narration, extra=''):
    s['notes'] = s['title'] + '\n\n발표자 설명\n' + narration + '\n\n내용 근거\nLifeLink119/docs/UX_REFINEMENT_20261115.md\nLifeLink119/docs/VOICE_AI_IMPLEMENTATION.md (2026-11-15)\nLifeLink119/docs/EMERGENCY_SUPPORT_IMPLEMENTATION.md\n' + extra
    for pic in s['images']:
        source = sources.get(pic['file'], 'docs/presentation/voice-family-20261115/assets/' + pic['file'])
        s['notes'] += '\n화면/이미지: ' + source
    s['notes'] += '\n공개 화면 및 합성 시나리오만 사용. 보안 화면 또는 실제 개인정보는 수록하지 않음.'

def screen(title, sub, file, crop, steps, note='', narration=''):
    s=new(title,sub)
    im(s,file,65,202,1000,600,crop)
    t(s,'앱 실제 화면 · 주요 영역 확대',68,808,1000,21,C['muted'],False,'caption',29)
    y=211
    for head,body in steps:
        a=t(s,head,1110,y,425,30,C['accent'],True,lh=42)
        b=t(s,body,1110,y+a['h']+9,425,28,lh=40)
        y+=a['h']+b['h']+36
    if note: t(s,note,1110,max(718,y),425,23,C['warn'],lh=32,name='note')
    notes(s,narration or '\n'.join(h+'\n'+b for h,b in steps))
    return s

s=new('LifeLink 119\n음성 동행 사용 안내',dark=True)
im(s,'hyundai-family-hero.png',760,180,800,560)
t(s,'현대차 사진 참조 · 가족 장면 AI 합성',790,747,720,21,C['mint'],lh=30)
t(s,'LifeLink 119',70,96,670,66,C['light'],True,'title',88)
t(s,'음성 동행\n사용 안내',70,206,680,68,C['light'],True,'title',92)
t(s,'질문을 듣고, 짧게 답하는\n차량 응급지원',73,448,650,34,C['mint'],lh=50)
t(s,'나와 내 가족을 위한 구급차',73,647,650,28,C['light'],lh=39)
t(s,'실제 앱 화면으로 보는 사용법과 자동 시연\n2026.11.15 · v0.18.0-demo',73,712,650,23,C['mint'],lh=35)
t(s,'시연 앱 · 실제 신고·상담 연결 아님',73,843,690,21,'E8C598',lh=30)
notes(s,'기존 사용 안내를 최신 음성 중심 흐름으로 정리한 16장 개정 설명 자료입니다. 사용자가 제공한 현대자동차 사진을 차량 디자인 참조로 삼아 가족과 함께 있는 장면을 AI로 합성했습니다. 이전 가족 이미지의 따뜻한 분위기를 유지하며 일반 SUV를 현대차로 교체했습니다. 실제 가족의 사진이나 현대자동차 공식 광고가 아닙니다. 실제 구조 서비스 또는 실차 인증을 의미하지 않습니다. 생성 프롬프트는 assets/hyundai-family-hero.prompt.txt에 보관했습니다.')

screen('홈에서 필요한 도움을 선택합니다','긴급 연결, 상담, 가족정보의 진입점을 한 화면에 모았습니다',
       'home.jpg',[40,300,1645,1060], [
       ('긴급신고','119 긴급신고와 112 경찰 신고를 바로 선택합니다.'),
       ('상담과 가족정보','119 응급상담, 109 마음상담, 가족 안심콜을 제공합니다.'),
       ('이번 안내의 중심','119 연결 화면에서 시작하는 AI 음성 동행입니다.')],
       '가족정보 등록이나 PIN 없이 긴급 연결을 시작합니다.',
       '기존 홈 구조와 아이콘을 유지하고 사용자가 제공한 현대차 사진을 기반으로 합성한 가족 이미지를 적용했습니다. 차량은 현대차 디자인 참조를 사용했고 가족은 합성 인물입니다. 109와 112는 별도 기관 연결 시연 경로이며 AI 음성 동행과 동일한 기능이 아닙니다. 모든 발신·상담 연결은 시연입니다.')

screen('외부 AI 선택은 처음 한 번만','동의와 거절 모두 저장하며, 다시 실행해도 반복해서 묻지 않습니다',
       'first-run.jpg',[45,185,1640,710],[
       ('외부 AI 사용','동의하고 시작을 누르면 필요한 증상 의미 코드만 전송합니다.'),
       ('기본 안내 선택','외부 AI를 사용하지 않아도 기본 음성 안내를 이용합니다.'),
       ('마이크 권한','Android가 처음 요청하면 마이크 사용을 허용합니다.')],
       '선택은 더보기의 음성 AI 설정에서 바꿀 수 있습니다.',
       '외부 AI 동의는 앱의 선택이며 Android 마이크 권한과 별개입니다. 최초 1회는 현재 설치된 차량 앱 기준입니다. 앱 데이터 삭제·재설치 후까지 유지한다고 보장하지 않습니다. 질문 단계와 한정 의미 코드도 건강 맥락이므로 사용자가 선택합니다.')

screen('119 화면에서 한 번 눌러 시작','홈의 긴급신고를 누른 뒤 AI 음성 동행 시작을 선택합니다',
       'call.jpg',[40,330,1640,1025],[
       ('AI 음성 동행 시작','추가 시작 화면이나 동의 체크 없이 질문이 이어집니다.'),
       ('응급 요약·대기 지원','상황 요약과 연결 지연 상태를 확인할 수 있습니다.')],
       '실제 통화 중에는 음성 동행을 시작하지 않습니다.',
       '한 번 시작한다는 의미는 119 연결 화면에서 AI 음성 동행 시작 버튼을 한 번 누르는 것입니다. 홈에서의 전체 조작이 한 번이라는 뜻은 아닙니다. 새 사건마다 외부 AI 동의를 다시 받지 않습니다. 이 화면의 가상 상담원 응답은 실제 통화 상태가 아닙니다.')

screen('AI가 질문을 한 가지씩 읽습니다','질문이 끝난 뒤 말하면 다음 질문과 필요한 안내가 이어집니다',
       'question.jpg',[40,165,1640,790],[
       ('첫 질문','“환자는 의식이 있나요?”'),
       ('질문을 듣는 동안','질문을 읽고 있습니다라는 상태를 보여줍니다.'),
       ('답할 차례','질문이 끝나면 자동으로 음성 입력을 기다립니다.')],
       '긴 설명이나 여러 선택지를 한꺼번에 요구하지 않습니다.',
       '음성 출력(TTS)이 끝난 후에만 기기 내 음성인식(STT)을 시작합니다. 마이크를 계속 켜두지 않습니다. 외부 모델이 만드는 임의의 의료 조언을 읽는 구조가 아닙니다.')

screen('답변은 짧게 말하면 됩니다','“예”, “아니요”, “잘 모르겠어요”로 현재 질문에 답합니다',
       'listening.jpg',[40,165,1640,790],[
       ('듣고 있습니다','이 문구를 확인한 뒤 짧게 답합니다.'),
       ('뜻이 불확실할 때','AI가 짧게 다시 확인하면 예 또는 아니요로 답합니다.'),
       ('응답이 없을 때','한 번 재질문한 뒤 듣기를 쉬며, 임의로 답을 채우지 않습니다.')],
       '긴 자유 대화보다 짧고 분명한 답변을 사용합니다.',
       '음성인식이 실패하거나 무응답이어도 환자가 정상이라고 기록하지 않습니다. 외부 모델 해석이 불명확하면 고정 확인 질문을 거칩니다. 실제 차량 소음과 사람의 억양에 대한 실차 검증은 남아 있습니다.')

s=new('화면 답변도 현재 질문 하나만','기본은 음성 대화 · 화면으로 전환해도 방금 들은 질문을 유지합니다')
im(s,'manual.jpg',70,193,1460,535,[60,225,1625,780])
t(s,'답변은 예 · 아니요 · 잘 모르겠어요. 증상 목록을 고르지 않고 다음 질문으로 이어집니다.',73,758,1450,29,C['accent'],True,lh=42)
t(s,'앱 실제 화면 확대 · 상세 화면 조작은 정차 중에 이용합니다.',73,807,1450,21,C['muted'],lh=29)
notes(s,'음성 중심 흐름에서 화면으로 답하기를 선택한 모습입니다. 질문 위치와 모델 해석 확인 대기를 유지합니다. 예와 아니요는 같은 색으로 표시하며 특정 답을 권하지 않습니다. 응답 세 가지 외의 음성 재개와 119 재연결은 보조 동작입니다. 개인정보 없는 합성 시나리오입니다.')

s=new('연결 대기도 음성으로 이어갑니다','상태 선택 목록을 줄이고 AI 음성 동행을 중심에 두었습니다')
im(s,'callback.jpg',70,193,1460,535,[55,190,1625,765])
t(s,'“다시 전화 준대요”처럼 확인한 내용을 말합니다. 화면 확인도 한 질문씩 진행합니다.',73,758,1450,29,C['accent'],True,lh=42)
t(s,'앱 실제 화면 확대 · 콜백 예약·출동은 보장하지 않으며 재연결을 막지 않습니다.',73,807,1450,21,C['muted'],lh=29)
notes(s,'119 연결 화면에서 시작하는 음성 동행에 필요한 연결 상태도 명시적 발화로 알릴 수 있습니다. 기관에서 상태를 자동 수신한 것이 아니며 사용자가 확인한 진술로 표시합니다. 긴 발화가 불명확하면 다시 묻거나 화면의 순차 확인을 이용합니다. 통화 중이에요 또는 전화 왔어요라는 말, 오디오 포커스 변경, 앱 전환은 음성을 중지합니다. 실제 전화 발신은 시연입니다.')

screen('상담원에게 전할 내용을 모아 보여줍니다','응급 요약·대기 지원에서 상황, 위치, 차량, 환자 응답을 확인합니다',
       'packet.jpg',[40,474,1640,790],[
       ('확인한 정보 중심','위치의 시각·정확도와 차량 상태의 출처를 함께 봅니다.'),
       ('환자 상태 구분','사용자가 답한 상태를 차량 신호와 구분해 정리합니다.'),
       ('읽어주기·복사','상담원에게 전할 내용을 화면에서 확인합니다.')],
       '사진의 좌표·에어백은 합성 시나리오 값입니다.',
       '이 화면은 에어백 전개 시뮬레이션의 짧은 읽기 요약입니다. 실제 에어백 신호를 확인한 증거가 아닙니다. 미지원·오래된 위치·확인 불가를 현재 사실처럼 표시하지 않습니다. 복사 등 상세 동작은 주행 제한에 따릅니다. 실제 기관 자동 전송은 없습니다.')

s=new('저장한 안심콜 정보를 응급 요약에 포함','한 번 저장한 가족정보를 다시 입력하지 않고 이번 환자에게 연결합니다')
for x,num,head,body in [
    (75,'01','정차 후 인증','응급 요약에서\n가족 안심콜 정보 포함을\n누르고 PIN으로 확인합니다.'),
    (590,'02','이번 환자 확인','저장된 가족 중 환자를 고르고\n이 환자 정보로 응급 요약\n만들기를 누릅니다.'),
    (1105,'03','전달 내용 확인','병력·알레르기·복용약과\n의사소통·보호자 정보를\n상담원에게 알려줍니다.')]:
    t(s,num,x,224,400,52,C['accent'],True,lh=70)
    t(s,head,x,321,420,33,C['ink'],True,lh=46)
    t(s,body,x,394,420,29,C['muted'],lh=48)
t(s,'건강정보는 외부 AI로 보내지 않으며, 치료법을 결정하는 데 사용하지 않습니다.',75,629,1450,29,C['accent'],True,lh=42)
t(s,'차량에 직접 저장한 정보입니다. 공식 안심콜 사이트의 로그인·자동 조회는 연동하지 않았습니다.\n환자 선택은 이번 상황에만 적용되고, 잠금·앱 전환 시 표시가 해제됩니다.',75,714,1450,25,C['muted'],lh=37)
notes(s,'실제 구현 경로는 응급 요약, 가족 안심콜 정보 포함, 인증, 환자 한 명 확인, 응급 요약 만들기입니다. 기존 AES-GCM 암호화 저장을 재사용합니다. 환자를 임의로 가족 중 한 명으로 가정하지 않습니다. 저장된 정보는 선택 후 표시되며 잠금이 만료되거나 앱이 백그라운드로 가면 해제됩니다. 보호자에게 자동 연락하지 않습니다. 제공된 건강정보 양식의 거동, 통화 가능 여부, 요청사항, 과거 수술력도 요약에 활용합니다. 보안 화면의 실제 개인정보는 슬라이드에 수록하지 않습니다.')

screen('자동 시연은 한 번 누르면 진행됩니다','더보기에서 음성 동행 자동 시연을 선택합니다',
       'more.jpg',[40,160,1640,480],[
       ('약 1분 자동 진행','AI 질문과 예시 답변이 번갈아 재생됩니다.'),
       ('추가 조작 최소화','답변 버튼을 누르지 않아도 마지막 요약까지 이어집니다.'),
       ('명확한 시연 표시','화면에 합성 응답 시연임을 표시합니다.')],
       '자동 시연의 답변은 실제 마이크 인식 결과가 아닙니다.',
       '자동 시연은 마이크가 있는 실제 사용 흐름을 고정된 합성 응답으로 재현합니다. 사용자에게 말하도록 요구하지 않습니다. 실제 입력 체험은 앞서 설명한 119 화면의 AI 음성 동행 시작을 이용합니다. 저장된 외부 AI 동의가 이 시연에도 적용됩니다.')

s=new('자동 시연에서 이어지는 여섯 질문','질문과 합성 응답을 재생하며 같은 상태 관리·요약 흐름을 사용합니다')
s['tables'].append(dict(x=70,y=200,w=1460,h=540,columns=[100,1070,290],rowHeight=77,
    rows=[['순서','AI 질문','예시 답변'],['1','환자는 의식이 있나요?','네'],['2','정상적으로 숨을 쉬나요?','네'],
          ['3','불·연기·차량 갇힘이 있나요?','아니요'],['4','피가 많이 나나요?','아니요'],
          ['5','가슴에 통증이 있나요?','네'],['6','아까보다 상태가 나빠졌나요?','아니요']],
    fontSize=29,headerFill=C['accent'],headerColor='FFFFFF',bodyColor=C['ink'],bodyFill='EDF1EB',lineColor='CEDAD3'))
t(s,'흉통 답변 후에는 준비된 고정 안내를 읽고, 마지막에 인계 요약을 표시합니다.',73,770,1450,27,C['accent'],True,lh=38)
notes(s,'문장들은 사용 안내서의 자동 시연 시나리오입니다. 시연 속도는 음성 재생과 네트워크 환경에 따라 달라집니다. 아니요라는 답변이 이전 흉통을 해소하거나 환자가 정상이라는 판정을 의미하지 않습니다. 이 슬라이드의 답변은 모두 합성 응답입니다.')

screen('상태가 나빠지면 다시 알리도록 안내합니다','증상 변화와 확인 시각을 요약에 남기고 재연결을 우선 안내합니다',
       'worsening.jpg',[40,190,1640,690],[
       ('추가 질문 중단','호흡 이상·화재·급격한 악화에는 안전 안내와 재연결을 우선합니다.'),
       ('고정 안내 사용','외부 AI가 임의의 치료법을 추가하지 않습니다.'),
       ('통신이 끊겨도','준비된 기본 응급 안내는 계속 사용할 수 있습니다.')],
       '실제 상황에서는 119 상담원·의료진의 지시를 우선합니다.',
       '화면은 현재 질문에 답해 상태 악화를 확인한 별도 시나리오입니다. 앞 슬라이드의 자동 시연 마지막 답변인 아니요를 악화라고 해석한 결과가 아닙니다. 안내 콘텐츠는 공식 자료를 바탕으로 구현했으며 의료전문가 검토 전 시연 콘텐츠입니다.')

screen('시연이 끝나면 인계 요약을 확인합니다','응답한 상태와 확인 시각을 짧게 모아 보여줍니다',
       'handover.jpg',[40,180,1640,760],[
       ('응답과 시각','의식·호흡·가슴 통증에 대한 예시 답변을 확인합니다.'),
       ('다음 전달 준비','상태 변화를 다시 알릴 때 참고할 내용을 확인합니다.'),
       ('반복 시연','음성 시연 다시 보기로 처음부터 재생합니다.')],
       '요약 표시는 기관 전송이나 신고 접수 완료를 뜻하지 않습니다.',
       '자동 시연을 완료하면 인계 요약이 자동 표시됩니다. 표시된 시각은 캡처한 시연 시각입니다. 환자 진단이 아닌 응답 기록입니다. 실제 기관으로 전송되지 않는다는 문구가 화면에 남아 있습니다.')

s=new('발표자가 따라 하는 약 3분 시연','준비 후에는 홈 소개, 자동 대화, 인계 요약 순서로 보여줍니다')
rows=[('준비','음성 시연 런처 실행','최초 동의와 스피커 음량을 확인합니다.'),
      ('0:00','홈과 사용 흐름 소개','119 연결 화면의 AI 시작 버튼을 설명합니다.'),
      ('0:40','자동 시연 시작','더보기에서 음성 동행 자동 시연을 누릅니다.'),
      ('약 1분','질문과 합성 응답 재생','답변 조작 없이 여섯 질문이 이어집니다.'),
      ('마무리','요약과 다시 보기','응답 시각, 흉통 기록, 시뮬레이션 표시를 봅니다.')]
for i,(a,b,c) in enumerate(rows):
    y=206+i*108
    t(s,a,75,y,170,32,C['accent'],True,lh=43)
    t(s,b,280,y,520,31,C['ink'],True,lh=43)
    t(s,c,850,y+1,675,28,C['muted'],lh=40)
t(s,'실행 파일: .tools/Start-LifeLink-Voice-Demo.cmd',75,784,1450,26,C['accent'],True,lh=36)
notes(s,'발표 전 패키지 폴더의 .tools/Start-LifeLink-Voice-Demo.cmd를 더블클릭합니다. 이 런처는 QA 에뮬레이터와 서버·ADB 연결·앱 실행을 준비합니다. 일반 무음 런처 대신 음성 런처를 사용합니다. 시스템 부팅과 최초 설정 시간은 약 3분 발표에 포함하지 않습니다. 자동 시연 자체는 약 1분이며 환경에 따라 달라집니다. 키나 서버 주소를 화면에 띄우지 않습니다.')

s=new('지금 시연할 수 있는 범위', '음성 대기 지원을 확인하되, 실제 기관 연동과 구분합니다', dark=True)
t(s,'현재 앱에서 체험',76,223,640,36,C['mint'],True,lh=49)
t(s,'최초 동의와 설정 변경\n한 번 시작하는 음성 질문·답변\n고정 응급 안내와 상태 변화 기록\n응급 요약·인계 요약\n합성 응답으로 진행하는 자동 시연',76,310,660,31,C['light'],lh=66)
t(s,'협력·검증이 필요한 범위',860,223,650,36,C['mint'],True,lh=49)
t(s,'실제 119·112·109 발신과 접수\n콜백 예약·출동·도착 상태 수신\n공식 안심콜 정보 자동 가져오기\n제조사 승인 충돌·에어백 신호\n의료전문가 검토와 실차 음성 검증',860,310,650,31,C['light'],lh=66)
t(s,'현재 앱은 신고를 돕고 대기 중 정보를 정리하는 시연용 응급지원 앱입니다.',76,747,1440,29,C['mint'],True,lh=41)
notes(s,'현재 모든 기관 연결과 출동 상태 표시는 시뮬레이션입니다. 공식 안심콜 DB 자동 동기화는 연결하지 않았습니다. 실제 조회 가능한 차량 정보와 미지원 항목을 구분합니다. 이 기능을 공인 의료 서비스, 구조대 파견 또는 상용화 완료 기능으로 설명하지 않습니다. 사용자는 실제 위급 상황에서 공식 긴급 연락 수단을 이용해야 합니다.')

for s in slides:
    for el in s['texts']+s['images']+s['tables']:
        if el['x']<0 or el['y']<0 or el['x']+el['w']>W+1 or el['y']+el['h']>H+1:
            raise RuntimeError(f"Out of bounds on slide {s['number']}: {el.get('text',el.get('file','table'))}")
    # Body objects must not overlap the running footer; image captions are separate.
    for el in s['texts']:
        if el['name'] not in ['footer','folio'] and s['number'] != 1 and el['y']+el['h']>845:
            raise RuntimeError(f"Content touches footer on slide {s['number']}: {el['text']}")

(BUILD/'slide-models.json').write_text(json.dumps(dict(width=W,height=H,font='Malgun Gothic',slides=slides),ensure_ascii=False,indent=2),encoding='utf-8')
(BUILD/'asset-metadata.json').write_text(json.dumps(meta,ensure_ascii=False,indent=2),encoding='utf-8')
(BUILD/'source-manifest.json').write_text(json.dumps(sources,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps(dict(slides=len(slides),images=sum(len(s['images']) for s in slides),native_tables=1)))
