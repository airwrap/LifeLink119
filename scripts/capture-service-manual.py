"""Capture public native screens for the service manual. No medical unlock.

The existing encrypted records/PIN remain untouched. Refuses FLAG_SECURE and
restores Android display size. All actions use existing enabled UI controls.
"""
from pathlib import Path
import argparse, hashlib, json, os, re, subprocess, time
import xml.etree.ElementTree as ET
from native_window_client import WindowClient

ROOT=Path(__file__).resolve().parents[1]

def main():
    p=argparse.ArgumentParser();p.add_argument('--serial',default='emulator-5580');a=p.parse_args()
    sdk=Path(os.environ.get('ANDROID_HOME',Path(os.environ['LOCALAPPDATA'])/'Android/Sdk'))
    adb=[str(sdk/'platform-tools/adb.exe'),'-s',a.serial]
    def run(*args):return subprocess.check_output(adb+list(args),timeout=45).decode(errors='replace')
    assert a.serial.startswith('emulator-') and run('emu','avd','name').splitlines()[0]=='LifeLink119_Android34_UI_20261107'
    assert 'feature:android.hardware.type.automotive' not in run('shell','pm','list','features')
    version=run('shell','dumpsys','package','com.lifelink119.app')
    assert 'versionName=0.17.0-demo' in version
    original=run('shell','wm','size');match=re.search(r'Override size:\s*(\d+x\d+)',original)
    restore=match.group(1) if match else 'reset'
    out=ROOT/'docs/manual/v17';images=out/'images';images.mkdir(parents=True,exist_ok=True)
    trace=ROOT/'verification/bright-ui-v17/manual';trace.mkdir(parents=True,exist_ok=True)
    c=WindowClient(adb);frames=[];protected=[];status={'status':'FAIL'};started=False
    def node(key):return ET.fromstring(c.node(key)).find('.//node')
    def locate(key):
        n=node(key)
        if n is not None:return n
        for direction in ['up','down']:
            for _ in range(24):
                if not c.scroll(direction):break
                n=node(key)
                if n is not None:return n
        return None
    def wait(key,text='',seconds=18):
        n=locate(key);deadline=time.monotonic()+seconds
        while time.monotonic()<deadline:
            n=node(key)
            if n is not None and text in n.get('text',''):return n
            time.sleep(.12)
        raise AssertionError((key,text,'not visible'))
    def tap(key):
        locate(key)
        if not c.click(key):raise AssertionError('Not enabled/visible: '+key)
    def top():
        for _ in range(24):
            if not c.scroll('up'):break
    def snap(key,title,path,focus=None,kind='앱 내 모의 동작'):
        if focus:wait(focus)
        else:top()
        time.sleep(.3)
        assert not c.request('PING').get('secureRefused'),'Private capture refused'
        raw=c.frame();(images/(key+'.jpg')).write_bytes(raw)
        xml=c.dump();(trace/(key+'.xml')).write_text(xml,encoding='utf-8')
        frames.append({'id':key,'title':title,'path':path,'kind':kind,'image':'images/'+key+'.jpg','sha256':hashlib.sha256(raw).hexdigest()})
        print('CAPTURE '+key+' '+title,flush=True)
    def home():
        if locate('x_home') is not None:tap('x_home')
        elif node('x_finish_home') is not None:tap('x_finish_home')
        wait('x_emergency')
    def tools():home();tap('x_more');tap('x_tools')
    def case(sid):
        tools();tap('x_gallery');tap('x_case_'+sid.lower());wait('x_session_status','가상 상담원 응답')
    def support():
        if locate('x_support') is not None:tap('x_support')
        elif locate('x_driving_extra') is not None:tap('x_driving_extra')
        else:raise AssertionError('Support route unavailable')
    def settings_tail():
        tools();tap('x_access');snap('accessibility','큰 글씨와 쉬운 안내 설정','더보기 → 설정·도구 → 접근성 설정')
        tools();tap('privacy_settings');snap('privacy','의료정보 잠금과 관리','설정·도구 → 의료정보 잠금·PIN')
        # The separate native UI regression already verifies authentication and
        # FLAG_SECURE with this exact APK. The book deliberately never captures
        # or unlocks a protected page; avoid repeating that interaction here.
        qa=json.loads((ROOT/'verification/unslop-v17/after/results.json').read_text(encoding='utf-8'))
        assert qa['status']=='PASS' and qa['apkSha256']==hashlib.sha256((ROOT/'app/build/outputs/apk/debug/app-debug.apk').read_bytes()).hexdigest()
        assert any('Medical authentication stays protected' in s for s in qa['checks'])
        protected.append({'feature':'안심콜·건강정보·가족 관리','capture':'NOT_CAPTURED','privacyValidation':'Native UI regression: FLAG_SECURE refused; same APK hash','pinEntered':False,'recordsModified':False})
        home();tools();tap('x_gallery');snap('gallery','19개 상황별 체험','설정·도구 → 상황별 체험')
        home()
    try:
        run('shell','wm','size','1280x800')
        run('shell','am','force-stop','com.lifelink119.app')
        run('shell','am','start','-W','--display','0','-f','0x10008000','-n','com.lifelink119.app/.MainActivity')
        c.start();started=True
        run('shell','am','start','-W','--display','0','-f','0x10000000','-n','com.lifelink119.app/.MainActivity')
        wait('x_emergency');snap('home','네 가지 시작 메뉴','홈')
        tap('x_more');snap('more','차량 상태와 추가 기능','홈 → 더보기')
        tap('x_urgent_shortcut');snap('agency','119와 112 선택','더보기 → 119·112 선택')
        tools();snap('tools','상황별 체험과 설정','더보기 → 설정·도구')
        tap('x_driving_setup');snap('driving-setup','주행 중 도움 요청','설정·도구 → 주행 중 도움 요청')
        tap('x_driving_touch');wait('x_session_status','가상 상담원 응답');snap('driving','119 긴급 요청 연결 화면','홈 → 구조·구급')
        tap('x_driving_details');snap('driving-details','상황과 선택 정보 전달','119 연결 → 상황·정보 전달')
        tap('x_driving_send');tap('x_driving_ack');wait('x_driving_report','확인 완료')
        snap('driving-ack','상황실 역할의 내용 확인','상황 전달 → 상황실 역할 · 내용 확인',focus='x_driving_report')
        tap('x_driving_location');snap('driving-location','가상 위치의 선택적 공유','상황·정보 전달 → 가상 위치만 공유',focus='x_driving_shared')
        tap('x_driving_vehicle');tap('x_vctx_normal')
        snap('vehicle-example','출처를 구분하는 차량 상태','차량 상태 → 일반 상태 예시',focus='x_vctx_source',kind='명시적으로 선택한 예시 데이터')
        snap('vehicle-values','도어·SoC·에어백 예시 값','차량 상태 → 일반 상태 예시',focus='x_vctx_soc',kind='명시적으로 선택한 예시 데이터')
        tap('x_vctx_consent');tap('x_vctx_share');wait('x_vctx_receipt','앱 내 상황실 수신')
        snap('vehicle-receipt','동의한 차량 상태의 앱 내 수신','차량 상태 → 동의 → 선택한 차량 상태 전달',focus='x_vctx_receipt',kind='예시 데이터의 앱 내 전달')
        tap('x_vctx_revoke');tap('x_vctx_normal');tap('x_vctx_power');tap('x_ev_import_soc');wait('x_power_result','27.20')
        snap('ev-result','SoC 80%를 반영한 EV 계산','차량 상태 → 배터리 잔량으로 EV 계산 → 최근 SoC 불러오기',focus='x_power_result',kind='예시 SoC와 수동 가정의 계산')
        tap('x_vehicle_context');tap('x_vctx_stale');snap('vehicle-stale','오래된 값의 전달 차단','차량 상태 → 오래된 값 예시',focus='x_vctx_soc',kind='만료 상태 예시')
        tap('x_return_session');tap('x_driving_details');tap('x_driving_stopped');tap('x_driving_safe');tap('x_driving_handover')
        snap('handover','안전 확보와 대응팀 인계','상황·정보 전달 → 안전 확보 알림 → 인계 확인',focus='x_driving_report')
        tap('x_driving_finish');snap('finish','종료 후 공유정보 정리','인계 확인 → 완료·공유정보 정리')
        home();tap('x_consult');snap('consult','의료상담과 심리상담 선택','홈 → 의료·심리상담')
        tap('x_medical');wait('x_session_status','가상 상담원 응답');snap('medical-session','119 의료상담 연결','상담 → 119 응급의료상담')
        tap('x_guided_video');snap('video-request','영상 안내 요청','119 연결 → 영상 안내')
        tap('x_guide_request');snap('video-consent','별도 영상 사용 동의','영상 안내 → 요청')
        tap('x_guide_consent');tap('x_guide_start');wait('x_guide_preview');snap('video-active','합성 영상과 상황실 안내','동의 → 영상 안내 시작',focus='x_guide_preview',kind='합성 장면 · 실제 촬영·통화 없음')
        tap('x_guide_stop');home();tap('x_consult');tap('x_109');wait('x_session_status','가상 상담원 응답');snap('counsel109','109 심리상담','홈 → 의료·심리상담 → 109')
        home();tap('x_home_112');wait('x_session_status','가상 상담원 응답');snap('emergency112','112 긴급신고','홈 → 경찰 신고')
        case('S08');support();snap('support','상황에 맞는 추가 지원','연결 화면 → 추가 지원')
        tap('x_action_text');tap('x_send_phrase');tap('x_reply');snap('phrases','큰 의사표현 카드와 답장','추가 지원 → 문자·의사표현',focus='x_messages')
        tap('x_back');support();tap('x_action_sign');tap('x_sign_connect');snap('sign','수어통역 연결 상태','추가 지원 → 수어통역',focus='x_sign_status')
        case('S10');support();tap('x_action_voice');tap('x_voice');snap('voice','음성 안내 문장 강조','시각장애 상황 예시 → 추가 지원 → 음성 도움말')
        case('S12');support();tap('x_action_easy');snap('easy','하나씩 안내하는 쉬운 화면','인지장애 상황 예시 → 추가 지원 → 쉬운 안내')
        case('E02');support();tap('x_action_police');tap('x_police_request');tap('x_police_review');snap('police-review','기관 역할의 검토 단계','이송·112 협조 예시 → 추가 지원 → 112 공동대응')
        tap('x_police_approve');snap('police-approved','가상 승인 결과','112 공동대응 → 가상 112 역할 승인')
        tap('x_back');support();tap('x_action_navigation');snap('navigation-gate','전문가 안내가 필요한 목적지 설정','추가 지원 → 만남 장소 확인')
        case('S14');support();tap('x_action_location');tap('x_share_location');snap('location','선택적으로 공유하는 가상 위치','추가 지원 → 위치·차량 정보')
        tools();tap('x_cooperation');tap('x_coop_consent');tap('x_coop_case_early');tap('x_coop_review')
        snap('cooperation-review','참여 가능한 협력 차량 확인','설정·도구 → 주변 차량 협력 → 상황 확인')
        tap('x_coop_invite_a');tap('x_coop_accept');tap('x_coop_authorize')
        snap('cooperation-mission','수락 후 가상 임무 부여','협력 차량 초대 → 수락 → 119 역할 임무 부여')
        tap('x_coop_request_119');tap('x_coop_beacon_review');tap('x_coop_beacon_approve');tap('x_coop_beacon_start')
        snap('cooperation-display','별도 승인 후 화면 표시','표시 승인 요청 → 검토 → 한시 승인 → 시작',focus='x_coop_preview',kind='앱 화면 표시 모형 · 실제 차량 등화 아님')
        tap('x_coop_beacon_revoke');tap('x_coop_arrive');tap('x_coop_handover')
        home();tap('x_more');tap('x_manual');snap('manual-topics','오프라인 응급 매뉴얼 목록','더보기 → 응급 매뉴얼')
        tap('manual_first');snap('manual-detail','응급 매뉴얼 읽기','응급 매뉴얼 → 첫 번째 항목')
        settings_tail();status['status']='PASS'
    except Exception as e:
        status['error']=repr(e);print('FAIL '+repr(e),flush=True)
        if started and not c.request('PING').get('secureRefused'):
            (trace/'failure.xml').write_text(c.dump(),encoding='utf-8')
    finally:
        if started:c.close()
        run('shell','wm','size',restore)
        status.update({'version':'0.17.0-demo','platform':'Standard Android34 / actual native Activity Views','resolution':'1280x800','frames':frames,'protected':protected,'displayRestored':True,'medical':'No PIN submitted; no records created, changed or reset','apkSha256':hashlib.sha256((ROOT/'app/build/outputs/apk/debug/app-debug.apk').read_bytes()).hexdigest()})
        (out/'capture-manifest.json').write_text(json.dumps(status,ensure_ascii=False,indent=2),encoding='utf-8')
        (trace/'result.json').write_text(json.dumps(status,ensure_ascii=False,indent=2),encoding='utf-8')
    if status['status']!='PASS':raise SystemExit(1)

if __name__=='__main__':main()
