"""Drive only the verified Pleos AVD via its official console, then restore Park."""
import json
import os
from pathlib import Path
import subprocess
import time
from virtual_console import VirtualConsole
from contest_ui import ContestUI
from ui_snapshot_client import verify_installed_apk

root=Path(__file__).resolve().parents[1];out=root/'verification/v06/driving';out.mkdir(parents=True,exist_ok=True)
sdk=Path(os.environ['LOCALAPPDATA'])/'Android/Sdk'
adb=[str(sdk/'platform-tools/adb.exe'),'-s','emulator-5580'];stages=[];result={'status':'FAIL'};pid=None
def save_system(name):
    for args,file in [(['shell','dumpsys','car_service'],name+'-car.txt'),(['logcat','-d','--pid='+str(pid),'-v','time','-s','LifeLinkCarUx:I','*:S'],name+'-callback.txt')]:
        p=subprocess.run(adb+args,capture_output=True,timeout=20);(out/file).write_bytes(p.stdout+p.stderr)
    with (out/(name+'.png')).open('wb') as f:subprocess.run(adb+['exec-out','screencap','-p'],stdout=f,check=True,timeout=30)
try:
 with VirtualConsole() as v,ContestUI(adb,out) as ui:
    apk_hash=verify_installed_apk(adb,root/'app/build/outputs/apk/debug/app-debug.apk',sdk,'0.6.0-demo')
    pid=subprocess.check_output(adb+['shell','pidof','com.lifelink119.app'],timeout=10).decode().strip();assert pid.isdigit()
    v.park();ui.home();ui.tap('x_more');ui.tap('x_tools')
    for _ in range(12):
        if ui.node('x_restrictions') is not None:break
        if not ui.client.scroll('down'):break
    parked=ui.expect('x_restrictions','REAL');save_system('01-park');stages.append({'stage':'Park','app':parked,'vehicle':v.state()})
    v.gear('Drive');v.controls(speed=25);time.sleep(2)
    driving=v.state();assert driving['gear']['actual']['value']=='Drive';assert driving['controls']['speed_kph']==25
    save_system('02-drive25')
    try:app=ui.expect('x_restrictions','REAL',seconds=3)
    except Exception as e:app='App root unavailable or platform blocked: '+str(e)
    stages.append({'stage':'Drive25','app':app,'vehicle':driving})
    v.controls();time.sleep(.5);v.gear('Park');time.sleep(1)
    subprocess.run(adb+['shell','am','start','-W','--display','0','-f','0x10008000','-n','com.lifelink119.app/.MainActivity'],capture_output=True,check=True,timeout=35)
    ui.home();ui.tap('x_more');ui.tap('x_tools')
    for _ in range(12):
        if ui.node('x_restrictions') is not None:break
        if not ui.client.scroll('down'):break
    restored=ui.expect('x_restrictions','ActiveRestrictions=0');save_system('03-restored');stages.append({'stage':'ParkRestored','app':restored,'vehicle':v.state()});ui.home()
    logs=(out/'03-restored-callback.txt').read_text(encoding='utf-8',errors='replace')
    # Do not count only a vehicle-control API success as actual UX restriction verification.
    import re
    values=[int(x) for x in re.findall(r'ActiveRestrictions=(\d+)',logs)]
    driving_flags=[int(x) for x in re.findall(r'ActiveRestrictions=(\d+)',stages[1]['app'])]
    passed=any(x>0 for x in values) and values[-1:]==[0] and any(x>0 for x in driving_flags)
    result={'status':'PASS' if passed else 'PARTIAL','apkSha256':apk_hash,'pid':pid,'stages':stages,'callbackFlags':values,
            'scope':'Official console vehicle values and real CarUxRestrictions; emulator only, no physical hardware.',
            'remaining':[] if passed else ['The complete nonzero/recovery CarUX sequence was not proven for this APK and process. Overlay coverage is separate.']}
except Exception as e:result={'status':'FAIL','stages':stages,'error':str(e)};print('FAIL',str(e),flush=True)
finally:
 (out.parent/'driving.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps({'status':result['status'],'flags':result.get('callbackFlags'),'error':result.get('error')},ensure_ascii=False),flush=True)
if result['status']=='FAIL':raise SystemExit(1)
