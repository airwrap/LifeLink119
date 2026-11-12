"""Capture only the running APK's public guide. Never capture protected data."""
from pathlib import Path
import argparse,hashlib,json,os,subprocess,time,xml.etree.ElementTree as ET
from native_window_client import WindowClient
from ui_snapshot_client import verify_installed_apk

ROOT=Path(__file__).resolve().parents[1]
def main():
    p=argparse.ArgumentParser();p.add_argument('--serial',default='emulator-5580');a=p.parse_args()
    sdk=Path(os.environ['LOCALAPPDATA'])/'Android/Sdk';adb=[str(sdk/'platform-tools/adb.exe'),'-s',a.serial]
    assert subprocess.check_output(adb+['emu','avd','name'],timeout=20).decode().splitlines()[0]=='LifeLink119_SafeCall_20261108'
    out=ROOT/'docs/reviews/safe-call-v18';out.mkdir(parents=True,exist_ok=True)
    c=WindowClient(adb);result={'status':'FAIL'};shots=[]
    def node(key):return ET.fromstring(c.node(key)).find('.//node')
    def tap(key):
        if c.click(key):return
        for d in ['down','up']:
            for _ in range(24):
                if not c.scroll(d):break
                if c.click(key):return
        raise AssertionError('Cannot activate public control: '+key)
    def shot(name):
        time.sleep(.3);assert not c.request('PING').get('secureRefused')
        path=out/(name+'.jpg');path.write_bytes(c.frame());shots.append(path.name)
    try:
        digest=verify_installed_apk(adb,ROOT/'app/build/outputs/apk/debug/app-debug.apk',sdk,'0.18.0-demo')
        c.start();subprocess.run(adb+['shell','am','start','-W','-f','0x10000000','-n','com.lifelink119.app/.MainActivity'],check=True,capture_output=True,timeout=40)
        if node('x_home') is not None:tap('x_home')
        shot('01-home');tap('x_more');tap('x_safe_official');shot('02-official-self')
        tap('x_agency_proxy');shot('03-official-proxy')
        tap('x_agency_self')
        while c.scroll('down'):pass
        shot('04-official-links')
        for key in ['x_agency_open','x_agency_guide','x_agency_healthway']:
            # Scroll to each control without activating an external site.
            for direction in ('up','down'):
                for _ in range(18):
                    if node(key) is not None:break
                    if not c.scroll(direction):break
                if node(key) is not None:break
            n=node(key);assert n is not None;assert n.get('gravity')=='17';assert n.get('text-clipped')=='false'
        tap('x_home');tap('x_family');time.sleep(.3)
        assert c.request('PING').get('secureRefused')
        try:c.frame();raise AssertionError('Protected frame must be refused')
        except RuntimeError as error:assert 'Protected window capture refused' in str(error)
        cancelled=False
        for _ in range(12):
            if c.request('CANCEL_AUTH').get('cancelled') is True:cancelled=True;break
            time.sleep(.3)
        assert cancelled,'Own authentication Cancel button was unavailable'
        result={'status':'PASS','apkSha256':digest,'captures':shots,'protectedCaptureRefused':True,'captureBackend':c.request('PING').get('backend'),'privateFrames':0}
        print('PASS public guide captures, centered unclipped controls and protected frame refusal',flush=True)
    finally:
        c.close();(out/'capture-results.json').write_text(json.dumps(result,indent=2),encoding='utf-8')
    if result['status']!='PASS':raise SystemExit(1)
if __name__=='__main__':main()
