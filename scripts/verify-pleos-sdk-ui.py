"""Actual Pleos Activity UI: Console -> SDK values -> consent -> local receipt -> EV.
No EXAMPLE controls, medical authentication, real calls or CarUX overrides.
"""
from pathlib import Path
import argparse,json,os,re,subprocess,time,xml.etree.ElementTree as ET
from native_window_client import WindowClient
from virtual_console import VirtualConsole

ROOT=Path(__file__).resolve().parents[1]

def main():
    p=argparse.ArgumentParser();p.add_argument('--serial',default='emulator-5582');a=p.parse_args()
    assert a.serial.startswith('emulator-')
    sdk=Path(os.environ.get('ANDROID_HOME',Path(os.environ['LOCALAPPDATA'])/'Android/Sdk'))
    adb=[str(sdk/'platform-tools/adb.exe'),'-s',a.serial]
    def run(*args):return subprocess.check_output(adb+list(args),timeout=45).decode(errors='replace')
    assert run('emu','avd','name').splitlines()[0]=='LifeLink119_SDK_Compat_20261107'
    assert 'feature:android.hardware.type.automotive' in run('shell','pm','list','features')
    prior=json.loads((ROOT/'verification/pleos-sdk-current/results.json').read_text(encoding='utf-8'))
    assert prior['status']=='SDK_VALUE_CHANGES_VERIFIED','Verify genuine SDK changes before UI evidence'
    out=ROOT/'verification/pleos-sdk-current/ui';out.mkdir(parents=True,exist_ok=True)
    c=WindowClient(adb);v=VirtualConsole(a.serial,'LifeLink119_SDK_Compat_20261107',18088)
    checks=[];result={'status':'FAIL','checks':checks,'sdkApkSha256':prior['installedApkSha256']};started=False
    last_focus=[0.0]
    def node(key):
        n=ET.fromstring(c.node(key)).find('.//node')
        # Pleos's navigation app may take the shared panel during startup.
        # Reopen our ordinary launch Intent only when the entire public window
        # is hidden. This neither clears state nor overrides real CarUX gates.
        if n is None and time.monotonic()-last_focus[0]>3 and ET.fromstring(c.dump()).find('.//node') is None:
            last_focus[0]=time.monotonic()
            print('Refocus ordinary app Intent: shared AAOS panel was hidden',flush=True)
            run('shell','am','start','-W','--display','0','-f','0x10000000','-n','com.lifelink119.app/.MainActivity')
        return n
    def wait(key,text='',seconds=60):
        deadline=time.monotonic()+seconds
        if node(key) is None:
            for direction in ['up','down']:
                for _ in range(18):
                    if node(key) is not None or not c.scroll(direction):break
                if node(key) is not None:break
        while time.monotonic()<deadline:
            n=node(key)
            if n is not None and text in n.get('text',''):return n
            time.sleep(.2)
        # Capture system focus while instrumentation is still running. A hidden
        # AAOS application panel is not evidence that a widget/SDK value failed.
        for service in ['activity','window']:
            try:(out/('failure-'+service+'.txt')).write_text(run('shell','dumpsys',service),encoding='utf-8')
            except Exception:pass
        raise AssertionError((key,text,'not observed; actual CarUX was not overridden'))
    def tap(key):
        node(key)
        if c.click(key):return
        for direction in ['down','up']:
            for _ in range(22):
                if not c.scroll(direction):break
                if c.click(key):return
        raise AssertionError('Cannot activate '+key)
    def snap(name):
        assert not c.request('PING').get('secureRefused'),'Private window refused'
        (out/(name+'.jpg')).write_bytes(c.frame());(out/(name+'.xml')).write_text(c.dump(),encoding='utf-8')
    def mark(s):checks.append(s);print('PASS '+s,flush=True)
    def refresh(soc,door):
        print('Refresh SDK, expected SOC '+str(soc),flush=True)
        tap('x_vctx_refresh');wait('x_vctx_soc',str(soc)+'%')
        wait('x_vctx_door','위치 신호 '+str(door))
        assert wait('x_vctx_source').get('text')=='Pleos SDK 조회값'
        # Wait for all callbacks before subsequent page navigation.
        wait('x_vctx_refresh','차량에서 새로 조회')
    before=v.state();oldDoor=before['body']['doors']['front_left'];oldSoc=int(before['ev_battery']['state_of_charge'])
    try:
        with v:
            v.park();v.soc(80);v.driver_door(True)
            run('shell','am','force-stop','com.lifelink119.app')
            run('shell','am','start','-W','--display','0','-f','0x10008000','-n','com.lifelink119.app/.MainActivity')
            c.start();started=True
            run('shell','am','start','-W','--display','0','-f','0x10000000','-n','com.lifelink119.app/.MainActivity')
            wait('x_emergency');tap('x_more');tap('x_vehicle_context');refresh(80,1);snap('01-real-sdk-80-open')
            mark('Actual automotive platform allows SDK page under real CarUX; Console door open and SOC80 match native app')
            tap('x_driving_quick');wait('x_session_status','가상 상담원 응답');tap('x_driving_details');tap('x_driving_vehicle');refresh(80,1)
            assert wait('x_vctx_consent').get('checked')=='false'
            tap('x_vctx_consent');tap('x_vctx_share');wait('x_vctx_receipt','앱 내 상황실 수신')
            assert '80%' in wait('x_vctx_receipt').get('text') and 'Pleos SDK 조회값' in wait('x_vctx_receipt').get('text');snap('02-real-sdk-receipt')
            mark('Actual SDK snapshot requires consent and reaches the local in-app receipt without PIN or real dispatch')
            tap('x_vctx_power');tap('x_ev_import_soc');wait('x_ev_soc_source','Pleos SDK 조회값');wait('x_power_result','27.20');snap('03-real-sdk-ev80')
            mark('SDK SOC80 explicitly imports into EV calculation: 27.20kWh under documented manual assumptions')
            v.soc(15);v.driver_door(False);tap('x_vehicle_context');refresh(15,0);snap('04-real-sdk-15-closed')
            tap('x_vctx_power');tap('x_ev_import_soc');wait('x_power_result','0.00');snap('05-real-sdk-ev15')
            mark('Changed Console values reach native SDK UI and SOC15 yields zero available energy above reserve')
            tap('x_vehicle_context');tap('x_vctx_revoke');wait('x_vctx_receipt','전달한 차량 상태 없음')
            mark('Vehicle receipt can be revoked; no automatic medical or PC transmission')
            result['status']='PASS'
    except Exception as e:
        result['error']=repr(e);print('FAIL',repr(e),flush=True)
        if started:
            try:snap('failure-public-only')
            except Exception:pass
    finally:
        try:
            with v:v.soc(oldSoc);v.driver_door(oldDoor);v.park()
            state=v.state();result['fixtureRestore']=state['body']['doors']['front_left']==oldDoor and state['ev_battery']['state_of_charge']==oldSoc
            if not result['fixtureRestore']:result['status']='FAIL'
        except Exception as e:result['fixtureRestore']=repr(e);result['status']='FAIL'
        if started:c.close()
        (out/'results.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
    if result['status']!='PASS':raise SystemExit(1)

if __name__=='__main__':main()
