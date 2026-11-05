"""Exercise public v0.9 UI on the explicitly selected dedicated emulator."""
import argparse, json, os, re, subprocess, time
from pathlib import Path
from contest_ui import ContestUI
from native_window_client import WindowClient
from ui_snapshot_client import verify_installed_apk

root=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser();p.add_argument('--serial',required=True);a=p.parse_args()
assert a.serial.startswith('emulator-')
sdk=Path(os.environ.get('ANDROID_HOME',Path(os.environ['LOCALAPPDATA'])/'Android/Sdk'))
adb=[str(sdk/'platform-tools/adb.exe'),'-s',a.serial]
avd=subprocess.check_output(adb+['emu','avd','name'],timeout=15).decode().splitlines()[0]
assert avd=='LifeLink119_Verify_20261101'
density_text=subprocess.check_output(adb+['shell','wm','density'],timeout=15).decode()
density=int(re.findall(r'(?:Physical|Override) density:\s*(\d+)',density_text)[-1])/160
out=root/'verification/v09/ux';out.mkdir(parents=True,exist_ok=True)
checks=[];result={'status':'FAIL'}
class UI(ContestUI):
    def __init__(self):
        super().__init__(adb,out);self.client=WindowClient(adb)
    def tap(self,key):
        try:return super().tap(key)
        except AssertionError:
            self.focus();time.sleep(.4);return super().tap(key)
def mark(label):checks.append(label);print('PASS',label,flush=True)
def save(ui,name):
    (out/(name+'.jpg')).write_bytes(ui.client.frame())
    (out/(name+'.xml')).write_text(ui.client.dump(),encoding='utf-8')
def tools(ui):ui.home();ui.tap('x_more');ui.tap('x_tools')
try:
    subprocess.run(adb+['shell','am','force-stop','com.lifelink119.app'],check=True,timeout=20)
    with UI() as ui:
        ui.home();ui.expect('x_emergency','119 구조');ui.expect('x_consult','상담');ui.expect('x_home_112','112')
        assert ui.node('x_driving_quick') is None
        for key in ('x_emergency','x_consult','x_home_112','x_family'):
            n=ui.node(key);assert n is not None and n.get('text') and n.get('clickable')=='true',key
            b=ui.bounds(n);assert b[2]-b[0]>=48*density and b[3]-b[1]>=48*density,(key,b,density)
        save(ui,'home-wide');ui.tap('x_emergency');ui.expect('x_session_status','가상 상담원 응답')
        ui.expect('x_driving_safety','주행 중');ticket=ui.expect('x_ticket','DEMO-')
        ui.tap('x_driving_quick');assert ticket==ui.expect('x_ticket','DEMO-')
        mark('Home directly starts one 119 demo without PIN; repeat does not duplicate')
        ui.tap('x_guided_video');assert ui.node('x_guide_preview') is None
        ui.tap('x_guide_request');assert ui.node('x_guide_preview') is None
        ui.expect('x_guide_consent','동의');save(ui,'video-consent')
        ui.tap('x_guide_consent');assert ui.node('x_guide_preview') is None
        ui.tap('x_guide_start');assert ui.node('x_guide_preview') is not None
        ui.expect('x_guide_message','상황');save(ui,'video-playing')
        ui.tap('x_guide_next');ui.expect('x_guide_message','확인')
        ui.tap('x_guide_stop');assert ui.node('x_guide_preview') is None;ui.expect('x_guide_request','요청')
        mark('Synthetic video requires request, consent, start; next and revoke work')
        ui.tap('x_guide_request');ui.tap('x_guide_consent');ui.tap('x_guide_start')
        subprocess.run(adb+['shell','input','keyevent','KEYCODE_HOME'],check=True,timeout=20);time.sleep(1);ui.focus()
        ui.expect('x_guide_resume','다시');assert ui.node('x_guide_preview') is None
        ui.tap('x_guide_resume');assert ui.node('x_guide_preview') is not None;ui.tap('x_guide_stop')
        ui.tap('x_guide_back');ui.tap('x_end')
        mark('Background hides preview and returning requires explicit resume')
        for first,second in [('x_home_112',None),('x_consult','x_medical'),('x_consult','x_109')]:
            ui.home();ui.tap(first)
            if second:ui.tap(second)
            ui.expect('x_session_status','가상 상담원 응답');ui.tap('x_end')
        mark('112, 119 medical consultation, 109 routes remain reachable')
        tools(ui);ui.tap('x_fault_connection');ui.tap('x_driving_quick');ui.expect('x_session_status','실패')
        ticket=ui.expect('x_ticket','DEMO-');ui.tap('x_retry');ui.expect('x_session_status','가상 상담원 응답')
        assert ticket==ui.expect('x_ticket','DEMO-');ui.tap('x_end')
        mark('Failed request can retry without duplicate ticket')
        tools(ui);ui.tap('x_test_restriction');ui.tap('x_driving_quick');ui.expect('x_session_status','가상 상담원 응답')
        ui.expect('x_driving_restricted','플랫폼 조작 제한')
        assert ui.node('x_guided_video').get('enabled')=='false';ui.tap('x_end');tools(ui);ui.tap('x_test_restriction')
        mark('Restriction preserves 119 request and disables video entry')
        ui.tap('x_narrow_preview');ui.home()
        assert ui.bounds(ui.node('x_emergency'))[3]<=ui.bounds(ui.node('x_consult'))[1]
        save(ui,'home-narrow');ui.tap('x_emergency');ui.expect('x_session_status','가상 상담원 응답')
        ui.tap('x_guided_video');ui.tap('x_guide_request');ui.tap('x_guide_stop');ui.tap('x_guide_back');ui.tap('x_end')
        tools(ui);ui.tap('x_narrow_preview');ui.home()
        mark('520dp layout stacks main actions; video consent may be cancelled')
        digest=verify_installed_apk(adb,root/'app/build/outputs/apk/debug/app-debug.apk',sdk,'0.9.0-demo')
        result={'status':'PASS','checks':checks,'apkSha256':digest,'serial':a.serial,'avd':avd,
                'limitations':['Native view snapshots, not a real vehicle or TalkBack user test.','All dispatch and video are simulated.']}
except Exception as e:
    result={'status':'FAIL','checks':checks,'error':repr(e)};print('FAIL',repr(e),flush=True)
finally:(out/'smoke-results.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
if result['status']!='PASS':raise SystemExit(1)
