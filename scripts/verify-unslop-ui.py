"""Native UI regression checks. No medical unlock, reset or external calls."""
from pathlib import Path
import argparse, json, os, re, subprocess, time, xml.etree.ElementTree as ET
from native_window_client import WindowClient
from ui_snapshot_client import verify_installed_apk

ROOT=Path(__file__).resolve().parents[1]

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--serial',required=True)
    parser.add_argument('--revision',choices=['v12','v13','v14','v15','v16','v17','v18'],default='v12')
    parser.add_argument('--avd',choices=['LifeLink119_Verify_20261101','LifeLink119_UI_20261107','LifeLink119_Android34_UI_20261107','LifeLink119_SafeCall_20261108'],default='LifeLink119_Verify_20261101');args=parser.parse_args()
    assert args.serial.startswith('emulator-')
    sdk=Path(os.environ.get('ANDROID_HOME',Path(os.environ['LOCALAPPDATA'])/'Android/Sdk'))
    adb=[str(sdk/'platform-tools/adb.exe'),'-s',args.serial]
    assert subprocess.check_output(adb+['emu','avd','name'],timeout=20).decode().splitlines()[0]==args.avd
    features=subprocess.check_output(adb+['shell','pm','list','features'],timeout=20).decode()
    automotive='feature:android.hardware.type.automotive' in features
    if args.avd in ('LifeLink119_Android34_UI_20261107','LifeLink119_SafeCall_20261108'):assert not automotive
    density=int(re.findall(r'(?:Physical|Override) density:\s*(\d+)',subprocess.check_output(adb+['shell','wm','density'],timeout=20).decode())[-1])/160
    out=ROOT/('verification/unslop-'+args.revision+'/after');out.mkdir(parents=True,exist_ok=True)
    min_touch=64 if args.revision=='v12' else 96
    c=WindowClient(adb);checks=[];captures=[];metrics=[];button_contrasts=[];result={'status':'FAIL'};large_changed=False
    def contrast(first,second):
        def luminance(color):
            rgb=[int(color[i:i+2],16)/255 for i in (2,4,6)]
            linear=[v/12.92 if v<=.04045 else ((v+.055)/1.055)**2.4 for v in rgb]
            return sum(x*y for x,y in zip(linear,[.2126,.7152,.0722]))
        low,high=sorted([luminance(first),luminance(second)])
        return (high+.05)/(low+.05)
    def node(key):return ET.fromstring(c.node(key)).find('.//node')
    def bounds(n):return list(map(int,re.findall(r'-?\d+',n.get('bounds',''))))
    def wait(key,text='',seconds=20):
        end=time.monotonic()+seconds;searched=False
        while time.monotonic()<end:
            n=node(key)
            if n is not None and text in n.get('text',''):return n
            if n is None and not searched and key not in ('x_emergency','x_consult','x_home_112','x_family','x_session_status'):
                searched=True
                for direction in ('up','down'):
                    for _ in range(18):
                        if not c.scroll(direction):break
                        n=node(key)
                        if n is not None:break
                    if n is not None:break
                if n is not None and text in n.get('text',''):return n
            time.sleep(.15)
        raise AssertionError('Control/state unavailable: '+key)
    def tap(key):
        if c.click(key):return
        for direction in ('down','up'):
            for _ in range(24):
                if not c.scroll(direction):break
                if c.click(key):return
        raise AssertionError('Cannot activate: '+key)
    def mark(label):checks.append(label);print('PASS '+label,flush=True)
    def home():
        if node('x_home') is not None:tap('x_home')
        elif node('x_finish_home') is not None:tap('x_finish_home')
        wait('x_emergency')
    def tools():home();tap('x_more');tap('x_tools')
    def snapshot(name):
        time.sleep(.7)
        assert not c.request('PING').get('secureRefused'), 'Protected page must not be captured'
        tree=ET.fromstring(c.dump())
        for n in tree.iter('node'):
            if n.get('text') and 'text-clipped' in n.attrib:
                assert n.get('text-clipped')=='false',(name,n.get('resource-id',''),'text clipped')
                assert n.get('ellipsis-count')=='0',(name,n.get('resource-id',''),'ellipsis')
            if n.get('class')!='android.widget.Button':continue
            key=n.get('resource-id','');b=bounds(n)
            assert n.get('gravity')=='17',(name,key,'not centered')
            assert n.get('text-clipped')=='false',(name,key,'text clipped')
            assert n.get('ellipsis-count')=='0',(name,key,'ellipsis')
            assert b[3]-b[1]>=min_touch*density-1,(name,key,b,'short touch target')
            assert b[2]-b[0]>=min_touch*density-1,(name,key,b,'narrow touch target')
            assert int(n.get('typeface-weight','0'))<=600,(name,key,'heavy weight')
            if args.revision in ('v15','v16','v17','v18') and n.get('background-color'):
                ratio=contrast(n.get('text-color'),n.get('background-color'))
                assert ratio>=4.5,(name,key,'low text contrast',ratio)
                if args.revision in ('v17','v18') and n.get('enabled')=='true':
                    assert contrast(n.get('background-color'),'ff191c22')>=3,(name,key,'button blends into the dark page')
                button_contrasts.append({'screen':name,'id':key,'ratio':round(ratio,3),'enabled':n.get('enabled')=='true'})
        (out/(name+'.jpg')).write_bytes(c.frame())
        (out/(name+'.xml')).write_text(ET.tostring(tree,encoding='unicode'),encoding='utf-8')
        captures.append(name)
    def home_metrics(reveal=False):
        expected_colors=[('x_emergency','ffac2d26'),('x_home_112','ff2452aa')] if args.revision in ('v12','v13') else [(k,'fff2f0e8' if args.revision in ('v17','v18') else 'ff3e4754' if args.revision in ('v15','v16') else 'ffffffff') for k in ['x_emergency','x_consult','x_home_112','x_family']]
        current_metrics={}
        for key,expected in expected_colors:
            if reveal and node(key) is None:
                for direction in ('up','down'):
                    for _ in range(18):
                        if not c.scroll(direction):break
                        if node(key) is not None:break
                    if node(key) is not None:break
            n=wait(key);assert n.get('background-color')==expected,(key,n.attrib)
            assert n.get('gravity')=='17' and n.get('text-clipped')=='false',(key,'clipped or uncentered')
            current_metrics[key]=dict(n.attrib)
        metrics.append(current_metrics)
        if reveal:
            for _ in range(18):
                if not c.scroll('up'):break
    try:
        subprocess.run(adb+['shell','am','force-stop','com.lifelink119.app'],check=True,timeout=20)
        subprocess.run(adb+['shell','am','start','-W','--display','0','-f','0x10008000','-n','com.lifelink119.app/.MainActivity'],check=True,capture_output=True,timeout=50)
        c.start()
        # Match ContestUI.focus(): instrumentation can start a non-focused task.
        # NEW_TASK alone preserves the Activity owned by the native capture helper.
        subprocess.run(adb+['shell','am','start','-W','--display','0','-f','0x10000000','-n','com.lifelink119.app/.MainActivity'],check=True,capture_output=True,timeout=40)
        time.sleep(.3)
        home_metrics();snapshot('01-home')
        assert c.request('FOCUS x_emergency').get('focused') is True
        assert wait('x_emergency').get('focused')=='true'
        snapshot('01-home-focused')
        tree=ET.fromstring(c.dump());root_bounds=bounds(tree.find('node'))
        a=bounds(wait('x_emergency'));b=bounds(wait('x_home_112'))
        assert b[3]<root_bounds[3]
        if args.revision=='v12':assert a[1]>root_bounds[3]*.20
        else:
            cards=[bounds(wait(k)) for k in ['x_emergency','x_consult','x_home_112','x_family']]
            assert all(r[0]>=root_bounds[0] and r[1]>=root_bounds[1] and r[2]<=root_bounds[2] and r[3]<=root_bounds[3] for r in cards),'Home cards must all fit the initial viewport'
            assert max(r[3]-r[1] for r in cards)-min(r[3]-r[1] for r in cards)<=1,'Equal home touch areas'
            assert abs(cards[0][1]-cards[1][1])<=1 and abs(cards[2][1]-cards[3][1])<=1,'Home rows must align despite different label sizes'
            assert cards[1][0]-cards[0][2]>=24*density-1 and cards[2][1]-cards[0][3]>=24*density-1
            assert len([n for n in tree.iter('node') if n.get('text')])<=8,'Home copy became crowded'
        mark(f'Wide home has visible centered {min_touch}dp controls, separated targets, and red119/blue112')
        tap('x_consult');snapshot('02-consult');tap('x_medical');wait('x_session_status','가상 상담원 응답');snapshot('03-session')
        tap('x_guided_video');tap('x_guide_request');snapshot('04-video-consent')
        assert node('x_guide_preview') is None
        tap('x_guide_consent');assert node('x_guide_preview') is None
        tap('x_guide_start');wait('x_guide_preview');snapshot('05-video-guide')
        tap('x_guide_stop');assert node('x_guide_preview') is None
        mark('119 consultation and synthetic video retain separate request, consent, start and revoke')
        home();tap('x_home_112');wait('x_session_status','가상 상담원 응답');tap('x_end')
        home();tap('x_consult');tap('x_109');wait('x_session_status','가상 상담원 응답');tap('x_end')
        mark('112 emergency and 109 counseling remain reachable')
        home();tap('x_emergency');wait('x_session_status','가상 상담원 응답')
        if args.revision!='v12':tap('x_driving_details')
        ticket=wait('x_ticket').get('text')
        if args.revision!='v12':tap('x_return_session')
        assert node('privacy_pin') is None
        tap('x_driving_quick')
        if args.revision!='v12':tap('x_driving_details')
        assert wait('x_ticket').get('text')==ticket
        if args.revision!='v12':tap('x_return_session')
        snapshot('06-driving');tap('x_end');wait('x_finished')
        mark('119 request does not require registration or PIN and repeating quick request does not duplicate')
        if args.revision!='v12':
            home();tap('x_emergency');wait('x_session_status','가상 상담원 응답');tap('x_driving_details');wait('x_driving_report','상황 전달 전');snapshot('06-driving-details')
            tap('x_driving_send');wait('x_driving_report','전달됨');tap('x_driving_ack');wait('x_driving_report','확인 완료')
            tap('x_driving_location');wait('x_driving_shared','가상 위치');tap('x_driving_vehicle')
            if args.revision in ('v16','v17','v18'):
                wait('x_vctx_source');tap('x_vctx_normal');tap('x_vctx_consent');tap('x_vctx_share');wait('x_vctx_receipt','앱 내 상황실 수신');tap('x_vctx_revoke');tap('x_return_session');tap('x_driving_details')
            else:wait('x_driving_shared','가상 차량 정보')
            tap('x_driving_revoke');wait('x_driving_shared','없음')
            tap('x_driving_stopped');tap('x_driving_safe');tap('x_driving_handover');wait('x_driving_report','인계 완료');tap('x_driving_finish');wait('x_finished')
            mark('Progressive driving details preserve mock report, explicit sharing/revoke, safety and handover transitions')
        home();tap('x_family');time.sleep(.3)
        assert c.request('PING').get('secureRefused'), 'Medical authentication must secure the window'
        try:c.frame();raise AssertionError('Protected frame was returned')
        except RuntimeError as e:assert 'Protected window capture refused' in str(e)
        # Cancel the real dialog by its standard negative control, not an IME Back event.
        cancelled=False
        for _ in range(8):
            if c.request('CANCEL_AUTH').get('cancelled') is True:cancelled=True;break
            time.sleep(.3)
        assert cancelled,'Own authentication Cancel button was unavailable'
        deadline=time.monotonic()+8
        while c.request('PING').get('secureRefused') and time.monotonic()<deadline:time.sleep(.2)
        assert not c.request('PING').get('secureRefused'),'Authentication cancellation did not complete'
        home();mark('Medical authentication stays protected; no PIN entered, no private frame captured')
        # Match the active anonymous consultation used in the before/more capture.
        tap('x_consult');tap('x_medical');wait('x_session_status','가상 상담원 응답');home()
        tap('x_more');snapshot('07-more');tap('x_urgent_shortcut')
        assert wait('x_112').get('background-color')==('ffd4e7ff' if args.revision in ('v17','v18') else 'ff315ea8' if args.revision in ('v15','v16') else 'ff2452aa')
        snapshot('07-agency-choice');home();tap('x_more');tap('x_tools');snapshot('08-tools')
        tap('x_test_restriction');tap('x_driving_quick');wait('x_driving_restricted','플랫폼 조작 제한')
        assert wait('x_guided_video').get('enabled')=='false';tap('x_end');tools();tap('x_test_restriction')
        mark('Additional driving restriction leaves quick119 available and video disabled')
        tap('x_manual');snapshot('09-manual');tap('manual_first');snapshot('10-manual-detail')
        mark('Legacy offline manual is reachable and uses quiet secondary list controls')
        # Return from legacy UI with the shared home action.
        home();tap('x_more');tap('x_access')
        original_large=wait('x_large_text').get('checked')=='true'
        if not original_large:tap('x_large_text');large_changed=True
        home();snapshot('11-home-large')
        tap('x_consult');snapshot('12-consult-large')
        tools();tap('x_narrow_preview');home();home_metrics(reveal=True);snapshot('13-home-narrow-large')
        assert bounds(wait('x_emergency'))[3]<=bounds(wait('x_consult'))[1]
        tap('x_consult');snapshot('14-consult-narrow-large')
        tap('x_medical');wait('x_session_status','가상 상담원 응답');snapshot('15-session-narrow-large');tap('x_end')
        tools();tap('x_narrow_preview')
        tap('x_access')
        if large_changed:tap('x_large_text');large_changed=False
        home();mark('Large text and 520dp preview retain unclipped centered buttons; preferences restored')
        digest=verify_installed_apk(adb,ROOT/'app/build/outputs/apk/debug/app-debug.apk',sdk,'0.'+args.revision[1:]+'.0-demo')
        result={'status':'PASS','checks':checks,'captures':captures,'metrics':metrics,'buttonContrasts':button_contrasts,'apkSha256':digest,'serial':args.serial,'avd':args.avd,'automotivePlatform':automotive,'density':density,'nativeCapture':'actual Activity View drawing; not recreated HTML','limitations':['Not a real vehicle or live agency connection.','On standard Android the existing non-automotive demo mode is used, not real CarUX callbacks.','No medical unlock, record creation/reset or native credential submission.','No human TalkBack or real rotary-controller validation.']}
    except Exception as e:
        result={'status':'FAIL','checks':checks,'captures':captures,'error':repr(e)}
        print('FAIL '+repr(e),flush=True)
    finally:
        if large_changed:
            try:
                tools();tap('x_access');tap('x_large_text');home()
            except Exception:result['preferenceRestore']='Failed; inspect large-text setting'
        c.close()
        (out/'results.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
    if result['status']!='PASS':raise SystemExit(1)

if __name__=='__main__':main()
