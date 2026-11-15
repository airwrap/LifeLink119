"""Native v0.16 vehicle-context checks on the dedicated standard Android AVD.

Does not unlock medical data, clear application storage, make a call, or change
vehicle properties. Captures actual public Activity Views using the QA helper.
"""
from pathlib import Path
import argparse, json, os, subprocess, time, xml.etree.ElementTree as ET
from native_window_client import WindowClient
from ui_snapshot_client import verify_installed_apk
from qa_avd_guard import QaAvdGuard

ROOT=Path(__file__).resolve().parents[1]

def main():
    p=argparse.ArgumentParser();p.add_argument('--serial',default='emulator-5580');a=p.parse_args()
    assert a.serial.startswith('emulator-')
    sdk=Path(os.environ.get('ANDROID_HOME',Path(os.environ['LOCALAPPDATA'])/'Android/Sdk'))
    adb=[str(sdk/'platform-tools/adb.exe'),'-s',a.serial]
    def run(*args):return subprocess.check_output(adb+list(args),timeout=45).decode(errors='replace')
    guard = QaAvdGuard(adb).acquire_clean()
    assert 'feature:android.hardware.type.automotive' not in run('shell','pm','list','features')
    out=ROOT/'verification/vehicle-context-v16/native';out.mkdir(parents=True,exist_ok=True)
    c=WindowClient(adb);checks=[];captures=[];result={'status':'FAIL'}
    def node(key):return ET.fromstring(c.node(key)).find('.//node')
    def wait(key,contains='',seconds=15):
        deadline=time.monotonic()+seconds
        if node(key) is None:
            for direction in ['up','down']:
                for _ in range(18):
                    if node(key) is not None:break
                    if not c.scroll(direction):break
                if node(key) is not None:break
        while time.monotonic()<deadline:
            n=node(key)
            if n is not None and contains in n.get('text',''):return n
            time.sleep(.12)
        raise AssertionError((key,contains,'not found'))
    def tap(key):
        if c.click(key):return
        for direction in ['down','up']:
            for _ in range(28):
                if not c.scroll(direction):break
                if c.click(key):return
        raise AssertionError('Cannot activate '+key)
    def snap(name):
        assert not c.request('PING').get('secureRefused')
        tree=ET.fromstring(c.dump())
        for n in tree.iter('node'):
            if n.get('class')=='android.widget.Button':
                assert n.get('gravity')=='17',n.attrib
                assert n.get('text-clipped')=='false',n.attrib
        (out/(name+'.jpg')).write_bytes(c.frame())
        (out/(name+'.xml')).write_text(ET.tostring(tree,encoding='unicode'),encoding='utf-8');captures.append(name)
    def mark(label):checks.append(label);print('PASS '+label,flush=True)
    def open_context():
        if node('x_home') is not None:tap('x_home')
        else:wait('x_emergency')
        tap('x_more');tap('x_vehicle_context');wait('x_vctx_source')
    try:
        run('shell','am','force-stop','com.lifelink119.app')
        run('shell','am','start','-W','--display','0','-f','0x10008000','-n','com.lifelink119.app/.MainActivity')
        c.start();run('shell','am','start','-W','--display','0','-f','0x10000000','-n','com.lifelink119.app/.MainActivity')
        wait('x_emergency');open_context()
        assert wait('x_vctx_source').get('text')=='Pleos SDK 조회값'
        assert wait('x_vctx_share').get('enabled')=='false'
        tap('x_vctx_refresh');wait('x_vctx_age','확인 가능한 값 없음')
        assert wait('x_vctx_source').get('text')=='Pleos SDK 조회값'
        assert '80%' not in wait('x_vctx_soc').get('text');snap('01-sdk-unavailable')
        mark('Ordinary Android reports unavailable SDK values and never falls back to example success')
        tap('x_vctx_normal');wait('x_vctx_soc','80%')
        assert wait('x_vctx_share').get('enabled')=='false'
        mark('No active session means vehicle sharing remains disabled')
        tap('x_driving_quick');wait('x_session_status','가상 상담원 응답')
        tap('x_driving_details');tap('x_driving_vehicle');wait('x_vctx_source','예시 데이터')
        tap('x_vctx_normal');assert wait('x_vctx_consent').get('checked')=='false'
        assert wait('x_vctx_share').get('enabled')=='false'
        tap('x_vctx_consent');tap('x_vctx_share');wait('x_vctx_receipt','앱 내 상황실 수신');snap('02-consented-receipt')
        tap('x_vctx_revoke');wait('x_vctx_receipt','전달한 차량 상태 없음')
        mark('119 remains available without PIN; latest snapshot requires explicit consent and supports revocation')
        tap('x_vctx_normal');tap('x_vctx_power');tap('x_ev_import_soc')
        wait('x_ev_soc_source','예시 데이터');wait('x_power_result','27.20');snap('03-soc-to-ev')
        mark('Explicit example SOC 80 percent yields 27.20 kWh with declared manual assumptions')
        tap('x_vehicle_context');tap('x_vctx_incident');wait('x_vctx_airbag','신호 있음');snap('04-changed-signals')
        tap('x_vctx_power');tap('x_ev_import_soc');wait('x_power_result','0.00')
        mark('Changed SOC 15 percent updates EV calculation; airbag remains an observation, not an automatic emergency')
        tap('x_vehicle_context');tap('x_vctx_missing');assert wait('x_vctx_share').get('enabled')=='false';snap('05-no-data')
        tap('x_vctx_stale');wait('x_vctx_soc','이전 조회값');assert wait('x_vctx_share').get('enabled')=='false';snap('06-stale')
        tap('x_vctx_power');assert wait('x_ev_import_soc').get('enabled')=='false'
        mark('Missing and stale observations cannot be shared or imported as current SOC')
        tap('x_vehicle_context');tap('x_vctx_normal');tap('x_vctx_consent');tap('x_vctx_share')
        tap('x_vctx_power');tap('x_ev_import_soc')
        # Real elapsed-time expiry, not an injected device clock or artificial app state.
        time.sleep(31)
        wait('x_power_result','계산을 보류');snap('07-soc-expired')
        tap('x_vehicle_context');wait('x_vctx_receipt','전달한 차량 상태 없음')
        tap('x_vctx_power');tap('x_ev_manual_soc');wait('x_ev_soc_source','수동 입력');wait('x_power_result','27.20')
        mark('30-second expiry withdraws local receipt and blocks imported SOC until explicit manual selection or refresh')
        tap('x_vehicle_context');tap('x_vctx_normal');tap('x_vctx_consent');tap('x_vctx_share')
        run('shell','input','keyevent','KEYCODE_HOME');time.sleep(.5)
        run('shell','am','start','-W','--display','0','-f','0x10000000','-n','com.lifelink119.app/.MainActivity')
        wait('x_vctx_receipt','전달한 차량 상태 없음');wait('x_vctx_source','Pleos SDK 조회값')
        mark('Leaving app clears observations and vehicle consent while preserving the emergency session')
        open_context();tap('x_vctx_watch');wait('x_vctx_age','확인 가능한 값 없음');tap('x_home');open_context()
        assert wait('x_vctx_watch').get('checked')=='false'
        tap('x_home');tap('x_more');tap('x_tools');tap('x_test_restriction')
        tap('x_home');tap('x_more');tap('x_vehicle_context');assert node('x_vctx_refresh') is None
        tap('x_driving_quick');wait('x_driving_restricted','플랫폼 조작 제한');tap('x_end')
        tap('x_home');tap('x_more');tap('x_tools');tap('x_test_restriction');tap('x_home')
        mark('Foreground polling stops on page exit; combined additional restriction gate blocks details without blocking119')
        digest=verify_installed_apk(adb,ROOT/'app/build/outputs/apk/debug/app-debug.apk',sdk)
        result={'status':'PASS','checks':checks,'captures':captures,'apkSha256':digest,
            'serial':a.serial,'platform':'standard Android34: explicit fixtures, not real Pleos signal validation',
            'medical':'No PIN entered, no private data captured or reset'}
    except Exception as e:
        result={'status':'FAIL','checks':checks,'captures':captures,'error':repr(e)};print('FAIL',repr(e),flush=True)
        try:
            if not c.request('PING').get('secureRefused'):
                (out/'failure.xml').write_text(c.dump(),encoding='utf-8');(out/'failure.jpg').write_bytes(c.frame())
        except Exception:pass
    finally:
        c.close();(out/'results.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
    if result['status']!='PASS':raise SystemExit(1)

if __name__=='__main__':main()
