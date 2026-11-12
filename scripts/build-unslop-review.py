"""Package actual before/after Android View captures as a local review page."""
from pathlib import Path
import argparse, html, json, shutil, hashlib

ROOT=Path(__file__).resolve().parents[1]

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--revision',choices=['v12','v13'],default='v12');args=parser.parse_args()
    evidence=ROOT/('verification/unslop-'+args.revision)
    result=json.loads((evidence/'after/results.json').read_text(encoding='utf-8'))
    assert result['status']=='PASS','Only package a verified candidate'
    build=json.loads((evidence/'build-summary.json').read_text(encoding='utf-8'))
    assert build['status']=='PASS' and build['lint']=={'errors':0,'warnings':0}
    assert result['apkSha256']==build['sha256']['app/build/outputs/apk/debug/app-debug.apk']
    assert all(hashlib.sha256((ROOT/p).read_bytes()).hexdigest()==digest for p,digest in build['sha256'].items()),'Sources or APK changed since validation'
    if args.revision=='v13':
        car=json.loads((evidence/'car-layout/results.json').read_text(encoding='utf-8'))
        assert car['status']=='PASS' and car['originalDisplayRestored']
        assert car['apkSha256']==result['apkSha256']
    dest=ROOT/('docs/reviews/unslop-'+args.revision);images=dest/'images';images.mkdir(parents=True,exist_ok=True)
    rows=[('home','홈','01-home','01-home'),('consult','상담 선택','02-consult','02-consult'),('session','상담 연결','03-session','03-session'),('more','더보기','04-more','07-more'),('manual','응급 매뉴얼','05-manual','09-manual')]
    panels=[]
    for key,label,before,after in rows:
        for stage,filename in [('before',before),('after',after)]:
            shutil.copy2(evidence/stage/(filename+'.jpg'),images/(stage+'-'+key+'.jpg'))
        after_label='개선 후 · v0.12' if result['automotivePlatform'] else '개선 후 · v0.12 · Android 34 UI 검증'
        cards=''.join(f'<article><h3>{heading}</h3><a href="images/{stage}-{key}.jpg"><img src="images/{stage}-{key}.jpg" alt="{label} {heading} 실제 앱 화면" loading="lazy"></a><a class="zoom" href="images/{stage}-{key}.jpg">원본 확대</a></article>' for stage,heading in [('before','개선 전 · v0.11 · Pleos'),('after',after_label)])
        panels.append(f'<section data-screen="{key}" {"" if key=="home" else "hidden"}><h2>{label}</h2><div class="pair">{cards}</div></section>')
    options=''.join(f'<option value="{key}">{label}</option>' for key,label,_,_ in rows)
    checks=''.join('<li>'+html.escape(x)+'</li>' for x in result['checks'])
    page='''<!doctype html><html lang="ko"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>LifeLink 119 UI 개선 전후 · v0.12</title><style>
    *{box-sizing:border-box}body{margin:0;background:#f6f8fc;color:#192639;font:17px/1.65 system-ui,"Malgun Gothic",sans-serif}main{max-width:1500px;margin:auto;padding:32px 24px}h1{font-size:32px;font-weight:500;margin:0 0 8px}h2{font-size:24px;font-weight:500}h3{font-size:18px;font-weight:500;margin:0;padding:16px 20px;border-bottom:1px solid #d8dee7}p{max-width:80ch;color:#4d5e73}label{display:flex;gap:16px;align-items:center;margin:28px 0}select{font:inherit;min-height:48px;padding:8px 16px;border:1px solid #6d7d92;border-radius:8px;background:white;color:#192639}.pair{display:grid;grid-template-columns:1fr 1fr;gap:24px}article{min-width:0;background:white;border:1px solid #d8dee7;border-radius:16px;overflow:hidden}img{display:block;width:100%;height:auto}.zoom{display:inline-block;margin:12px 20px 20px}a{color:#2452aa}a:focus-visible,select:focus-visible{outline:3px solid #2452aa;outline-offset:4px}.note{margin-top:32px;border-top:1px solid #d8dee7;padding-top:24px}small{color:#4d5e73}details{margin-top:24px}li{margin:8px 0}@media(max-width:700px){main{padding:24px 16px}.pair{grid-template-columns:1fr;gap:16px}h1{font-size:26px}label{align-items:flex-start;flex-direction:column;gap:8px}}
    </style><main><h1>LifeLink 119 · UI 개선 전후</h1><p>차분한 글씨와 일정한 간격, 분명한 긴급 요청. 기존 기능을 유지하면서 Android 앱 화면을 정돈했습니다.</p><label>비교할 화면<select id="screen">OPTIONS</select></label>PANELS<div class="note"><p>동일한 전용 Pleos API 34 에뮬레이터에서 실행한 실제 앱 View 캡처입니다. 의료정보와 PIN 화면은 포함하지 않았습니다. 기관 연결·영상 안내는 앱의 모의 기능입니다.</p><p>검증: Android 단위 테스트 148개, lint 오류 0·경고 0, 네이티브 주요 동작 CHECKCOUNT항목. 앱 내부의 520dp 미리보기·큰 글씨도 확인했습니다. 실차·실제 상황실·사람이 수행하는 스크린리더 검증은 포함하지 않습니다.</p><details><summary>검사 항목 보기</summary><ul>CHECKS</ul></details><p><a href="../../UNSLOP_UI_V12.md">설계 방향과 검증 기록</a></p><small>적용한 지침: <a href="https://github.com/heydamir/unslop">unslop</a> by Damir Sokolovsky (@heydamir), CC BY 4.0 · Kotlin/Android Views와 한국어 차량 UI에 맞춰 적용</small></div></main><script>document.querySelector('#screen').addEventListener('change',e=>{for(const section of document.querySelectorAll('[data-screen]'))section.hidden=section.dataset.screen!==e.target.value;});</script></html>'''
    page=page.replace('OPTIONS',options).replace('PANELS',''.join(panels)).replace('CHECKCOUNT',str(len(result['checks']))).replace('CHECKS',checks)
    page=page.replace('단위 테스트 148개','단위 테스트 '+str(build['unit']['tests'])+'개')
    if not result['automotivePlatform']:
        page=page.replace('동일한 전용 Pleos API 34 에뮬레이터에서 실행한 실제 앱 View 캡처입니다.','개선 전은 Pleos API 34, 개선 후는 표준 Android 34 검증용 AVD에서 실행한 실제 앱 View 캡처입니다. Pleos 시스템의 부팅·차량 오디오 서비스 오류로 개선 후의 차량 플랫폼 검증은 완료하지 못했습니다. 같은 APK의 기존 비차량 모의 모드를 사용했으며 차량 제한을 해제하지 않았습니다. 플랫폼 차이는 UI 레이아웃 비교와 구분해 봐 주세요.')
        page=page.replace('실차·실제 상황실·사람이 수행하는 스크린리더 검증은 포함하지 않습니다.','실제 CarUX 콜백·차량 SDK·실차·상황실·오디오 출력·사람이 수행하는 스크린리더 검증은 포함하지 않습니다.')
    if args.revision=='v13':
        page=page.replace('v0.12','v0.13').replace('개선 전 · v0.11 · Pleos','개선 전 · v0.12 · Android 34')
        page=page.replace('UNSLOP_UI_V12.md','UNSLOP_CAR_TOUCH_V13.md')
        page=page.replace('차분한 글씨와 일정한 간격, 분명한 긴급 요청. 기존 기능을 유지하면서 Android 앱 화면을 정돈했습니다.','손을 뻗어 바로 누르는 차량 조작판. 일반 버튼을 64dp에서 96dp로 키우고, 홈을 네 개의 큰 터치 영역으로 바꿨습니다. 긴 설명과 상세 조작은 필요한 화면에서 열도록 정리했습니다.')
        page=page.replace('개선 전은 Pleos API 34, 개선 후는 표준 Android 34 검증용 AVD에서 실행한 실제 앱 View 캡처입니다. Pleos 시스템의 부팅·차량 오디오 서비스 오류로 개선 후의 차량 플랫폼 검증은 완료하지 못했습니다. 같은 APK의 기존 비차량 모의 모드를 사용했으며 차량 제한을 해제하지 않았습니다. 플랫폼 차이는 UI 레이아웃 비교와 구분해 봐 주세요.','개선 전후 모두 동일한 표준 Android 34 검증용 AVD의 실제 앱 View 캡처입니다. 1280×720과 1024×600의 실제 디스플레이 크기 변경에서도 홈·연결의 핵심 버튼이 스크롤 없이 보이고, 실제 터치 입력으로 모의 119 요청이 시작되는지 확인했습니다. Pleos 차량 오디오 서비스 문제로 실제 차량 플랫폼 재검증은 남아 있습니다. 기존 비차량 모의 모드를 사용했으며 실제 CarUX 제한을 해제하지 않았습니다.')
        extra=[]
        for size in ['1280x720','1024x600']:
            filename=size+'-home.jpg';shutil.copy2(evidence/'car-layout'/filename,images/filename)
            extra.append(f'<article><h3>{size} · 핵심 버튼 네 개</h3><a href="images/{filename}"><img src="images/{filename}" alt="{size} Android 실제 홈 화면" loading="lazy"></a></article>')
        page=page.replace('<div class="note">','<h2>가로형 차량 화면 검사</h2><div class="pair">'+''.join(extra)+'</div><div class="note">')
    (dest/'index.html').write_text(page,encoding='utf-8')
    print('Review page with five real before/after screens:',dest/'index.html')

if __name__=='__main__':main()
