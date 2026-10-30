"""Run the isolated emulator gate with an ephemeral QA PIN, then remove that PIN.

Use only the dedicated synthetic-data AVD. The reset step erases saved demo
profiles/session data; it never changes Android's own screen-lock credential.
"""
import json
import argparse
import os
from pathlib import Path
import secrets
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from contest_ui import ContestUI
from ui_snapshot_client import verify_installed_apk

parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--resume',action='store_true',help='Reuse only PASS evidence for the identical APK bytes; reset QA profiles/PIN regardless');args=parser.parse_args()
root=Path(__file__).resolve().parents[1];os.chdir(root);out=root/'verification/v06';env=os.environ.copy();env['LIFELINK_TEST_PIN']=str(secrets.randbelow(90_000_000)+10_000_000);os.environ['LIFELINK_TEST_PIN']=env['LIFELINK_TEST_PIN']
apk=root/'app/build/outputs/apk/debug/app-debug.apk'
source_inputs=[p for p in (root/'app/src/main').rglob('*') if p.is_file()]+[root/'app/build.gradle.kts']
assert apk.stat().st_mtime>=max(p.stat().st_mtime for p in source_inputs),'Build the current sources before running the native gate'
sdk=Path(os.environ['LOCALAPPDATA'])/'Android/Sdk';adb=[str(sdk/'platform-tools/adb.exe'),'-s','emulator-5580'];stages=[];result={'status':'FAIL'}
avd=subprocess.check_output(adb+['emu','avd','name'],timeout=15).decode().splitlines()
assert 'Pleos_Connect_API_34' in avd,'Use the isolated synthetic-data Pleos AVD only'
apk_hash=verify_installed_apk(adb,apk,sdk,'0.6.0-demo')
def launch():
    subprocess.run(adb+['shell','am','force-stop','com.lifelink119.verify'],capture_output=True,check=True,timeout=15)
    subprocess.run(adb+['shell','am','start','-W','--display','0','-f','0x10008000','-n','com.lifelink119.app/.MainActivity'],capture_output=True,check=True,timeout=40)
def clear_qa_lock():
    launch()
    with ContestUI(adb,out/'cleanup') as ui:
        ui.client.request('CLICK android:id/button2')
        ui.home();ui.tap('x_more');ui.tap('x_tools');ui.tap('privacy_settings');ui.tap('privacy_reset')
        end=time.monotonic()+8
        while time.monotonic()<end:
            if ui.client.request('CLICK android:id/button1').get('clicked')=='true':break
            time.sleep(.2)
        else:raise RuntimeError('QA medical-data reset confirmation did not appear')
        ui.expect('x_emergency','');ui.snap('final-home')
    user=subprocess.check_output(adb+['shell','am','get-current-user'],timeout=10).decode().strip()
    for file,forbidden in [('lifelink_medical_lock_v1',{'pin_salt','pin_verifier'}),('lifelink_synthetic_profiles_v05',{'profiles','profiles_cipher'})]:
        raw=subprocess.check_output(adb+['shell','run-as','com.lifelink119.app','--user',user,'cat','shared_prefs/'+file+'.xml'],timeout=15)
        assert not ({n.get('name') for n in ET.fromstring(raw)} & forbidden),'QA credentials or medical profiles were not erased'
def execute(name,script,script_args=()):
    proof={'privacy':'privacy.json','native':'contest-native.json','regression':'regression/native-smoke.json','recovery':'recovery.json','driving':'driving.json','pc-sdk':'pc-sdk.json'}[name]
    if args.resume:
        try:previous=json.loads((out/proof).read_text(encoding='utf-8'))
        except (OSError,ValueError):previous={}
        if previous.get('status')=='PASS' and previous.get('apkSha256')==apk_hash:
            stages.append({'name':name,'attempt':0,'exit':0,'reusedEvidence':proof});print(name,'reuse PASS for identical APK',flush=True);return
    for attempt in range(2):
        path=out/(name+('.log' if attempt==0 else '.retry.log'))
        with path.open('w',encoding='utf-8') as f:r=subprocess.run([sys.executable,script,*script_args],env=env,stdout=f,stderr=subprocess.STDOUT)
        stages.append({'name':name,'attempt':attempt+1,'exit':r.returncode});print(name,'attempt',attempt+1,'exit',r.returncode,flush=True)
        if r.returncode==0:return
        detail=path.read_text(encoding='utf-8')[-1000:];print(detail,flush=True)
        if attempt==0 and ('timed out' in detail or 'No active accessibility root' in detail):
            launch()
            if name=='privacy':clear_qa_lock();launch()
            elif name=='native':
                with ContestUI(adb,out/'retry-prep') as ui:ui.home();ui.tap('x_family');ui.tap('x_profile_reset');ui.confirm();ui.home()
            continue
        raise RuntimeError(name+' failed; see '+str(path.relative_to(root)))
try:
    clear_qa_lock()
    execute('privacy','scripts/smoke-medical-privacy.py')
    execute('native','scripts/smoke-contest.py')
    execute('regression','scripts/smoke-v05.py',['--expected-apk-version','0.6.0-demo','--output-dir','verification/v06/regression'])
    execute('recovery','scripts/smoke-contest-recovery.py')
    execute('driving','scripts/smoke-driving.py')
    # A Windows background child can inherit a captured pipe and hold EOF open
    # after PowerShell exits. File-backed output keeps launcher completion bounded.
    with (out/'server-launch.log').open('w',encoding='utf-8') as launch_log:
        subprocess.run(['powershell','-NoProfile','-ExecutionPolicy','Bypass','-File','scripts/local-demo.ps1','-Action','start'],stdout=launch_log,stderr=subprocess.STDOUT,check=True,timeout=35)
    subprocess.run(adb+['reverse','tcp:8781','tcp:8781'],capture_output=True,check=True,timeout=15)
    execute('pc-sdk','scripts/smoke-contest-pc-sdk.py')
    result={'status':'PASS','stages':stages}
except Exception as e:result={'status':'FAIL','stages':stages,'error':str(e)};print('GATE_FAIL',str(e),flush=True)
finally:
    try:clear_qa_lock();result['qaPinReset']=True
    except Exception as e:result['qaPinReset']=False;result['cleanupError']=str(e)
    env.pop('LIFELINK_TEST_PIN',None);os.environ.pop('LIFELINK_TEST_PIN',None)
    (out/'gate.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps(result,ensure_ascii=False,indent=2),flush=True)
if result['status']!='PASS' or not result.get('qaPinReset'):raise SystemExit(1)
