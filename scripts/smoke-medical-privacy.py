"""Use an ephemeral QA-only PIN to verify privacy; never print or persist its plaintext."""
import json
import os
from pathlib import Path
import subprocess
import time
import xml.etree.ElementTree as E
from PIL import Image
from ui_snapshot_client import SnapshotClient,verify_installed_apk
from capture_emulator import capture_emulator

root=Path(__file__).resolve().parents[1];out=root/'verification/v06/privacy';out.mkdir(parents=True,exist_ok=True)
sdk=Path(os.environ['LOCALAPPDATA'])/'Android/Sdk';adb=[str(sdk/'platform-tools/adb.exe'),'-s','emulator-5580']
pin=os.environ.get('LIFELINK_TEST_PIN','');assert pin.isascii() and pin.isdigit() and 6<=len(pin)<=12,'QA runner must supply an ephemeral PIN'
c=SnapshotClient(adb);checks=[];result={'status':'FAIL'};timings={}
def node(key):
    return E.fromstring(c.node(key)).find('.//node')
def tap(k):
    present=node(k) is not None
    for _ in range(8):
        if c.click(k):return
        time.sleep(.25)
        # Automotive window actions can finish after UiAutomation returns false.
        # Subsequent state assertions still verify the destination explicitly.
        if present and node(k) is None:return
    for direction in ['down','up']:
        for _ in range(18):
            if not c.scroll(direction):break
            if c.click(k):return
    raise AssertionError('Privacy control unavailable: '+k)
def wait(key,text='',seconds=90):
    end=time.monotonic()+seconds
    while time.monotonic()<end:
        n=node(key)
        if n is not None and text in n.get('text',''):return n
        time.sleep(.15)
    raise AssertionError('Privacy state did not reach '+key)
def positive():
    for _ in range(8):
        if c.request('CLICK android:id/button1').get('clicked')=='true':return
        time.sleep(.25)
    raise AssertionError('PIN action was not available')
def enter(value,setup=False):
    assert c.request('SET_TEXT com.lifelink119.app:id/privacy_pin '+value).get('changed')=='true'
    if setup:assert c.request('SET_TEXT com.lifelink119.app:id/privacy_confirm '+value).get('changed')=='true'
    assert node('privacy_pin').get('text')=='[PROTECTED]'
    time.sleep(.2)
    positive()
def mark(t):checks.append(t);print('PASS',t,flush=True)
def screenshot(name):
    path=out/(name+'.png')
    record=capture_emulator(adb,path)
    if not record['png']:return None,record
    return path,record
def prefs(name):
    user=subprocess.check_output(adb+['shell','am','get-current-user'],timeout=10).decode().strip()
    p=subprocess.run(adb+['shell','run-as','com.lifelink119.app','--user',user,'cat','shared_prefs/'+name+'.xml'],capture_output=True,timeout=15)
    if p.returncode:raise AssertionError('Cannot inspect QA-only preferences metadata')
    return {n.get('name'):n.text if n.tag=='string' else n.get('value') for n in E.fromstring(p.stdout)}
try:
 c.start()
 if node('x_home') is not None:tap('x_home')
 tap('x_emergency');tap('x_119');wait('x_session_status','가상 상담원 응답');assert node('privacy_pin') is None;mark('Urgent request remains two taps without medical authentication')
 tap('x_home');tap('x_family');wait('privacy_pin');assert node('x_profile_edit_1') is None
 if node('privacy_confirm') is None:raise AssertionError('QA expected an unconfigured app lock; use an isolated test profile or explicit data-reset UI')
 secure,capture=screenshot('01-pin-setup-secure');black=None
 if secure is not None:
    im=Image.open(secure).convert('RGB');roi=im.crop((853,90,2535,1300));black=sum(max(rgb)<8 for rgb in roi.getdata())/(roi.width*roi.height);assert black>.98,'Medical authentication window was not protected against capture'
 else:assert capture['secureWindow']
 mark('No family data before PIN; secure authentication surface is black in screen capture')
 started=time.monotonic();enter(pin,True);wait('x_profile_edit_1');timings['enrollment']=round(time.monotonic()-started,2);mark('User-set PIN enrollment and successful authenticated access')
 authenticated,auth_capture=screenshot('02-authenticated-medical-secure')
 if authenticated is not None:
    im=Image.open(authenticated).convert('RGB');roi=im.crop((853,90,2535,1300));assert sum(max(rgb)<8 for rgb in roi.getdata())/(roi.width*roi.height)>.98
 else:assert auth_capture['secureWindow']
 mark('Authenticated medical screen retains capture protection')
 tap('x_home');tap('x_family');wait('privacy_pin');wrong=('9' if pin[0]!='9' else '8')+pin[1:]
 for i in range(5):
    enter(wrong);wait('privacy_status','제한' if i==4 else '맞지')
    assert node('x_profile_edit_1') is None
 mark('Five wrong PINs expose no data and cause temporary lockout')
 before=prefs('lifelink_medical_lock_v1');assert int(before.get('locked_until',0))>0
 assert all(value!=pin for name,value in before.items() if isinstance(value,str))
 assert c.request('CLICK android:id/button2').get('clicked')=='true'
 subprocess.run(adb+['shell','am','force-stop','com.lifelink119.app'],check=True,timeout=15)
 subprocess.run(adb+['shell','am','start','-W','--display','0','-f','0x10008000','-n','com.lifelink119.app/.MainActivity'],check=True,capture_output=True,timeout=35)
 wait('x_family');after=prefs('lifelink_medical_lock_v1');assert after['locked_until']==before['locked_until'];mark('PIN hash and failed-attempt lockout survive process restart')
 tap('x_family');wait('privacy_pin')
 # Observe the persisted cooldown against the device clock, then submit once.
 # Repeated submissions race the async success callback as the dialog closes.
 end=time.monotonic()+40
 while time.monotonic()<end:
    device_seconds=int(subprocess.check_output(adb+['shell','date','+%s'],timeout=10).decode().strip())
    if device_seconds*1000>int(after['locked_until']):break
    time.sleep(.5)
 else:raise AssertionError('Persisted PIN lockout did not expire')
 started=time.monotonic();enter(pin)
 wait('x_profile_edit_1');timings['unlockAfterRestart']=round(time.monotonic()-started,2);tap('x_profile_edit_1');tap('x_profile_save');wait('x_profile_edit_1')
 stored=prefs('lifelink_synthetic_profiles_v05');assert 'profiles_cipher' in stored and 'profiles' not in stored
 assert all('김하늘' not in str(v) and 'DIABETES' not in str(v) for v in stored.values())
 mark('Saved profiles are encrypted and legacy plaintext profile storage is removed')
 # Real 60-second expiry, not a test-only bypass.
 end=time.monotonic()+75
 while time.monotonic()<end:
    state=node('privacy_state')
    if state is not None and '인증' in state.get('text',''):break
    time.sleep(.5)
 wait('privacy_state','인증');assert node('x_profile_edit_1') is None;mark('Actual 60-second authorization window expires and hides the medical page')
 tap('privacy_unlock');wait('privacy_pin');enter(pin);wait('x_profile_edit_1')
 subprocess.run(adb+['shell','am','start','-W','--display','0','-a','android.settings.SETTINGS'],capture_output=True,check=True,timeout=30)
 subprocess.run(adb+['shell','am','start','-W','--display','0','-f','0x10000000','-n','com.lifelink119.app/.MainActivity'],capture_output=True,check=True,timeout=30)
 wait('privacy_state','인증');assert node('x_profile_edit_1') is None;mark('Real app background and foreground require fresh authentication')
 tap('x_urgent_shortcut');tap('x_112');wait('x_session_status','가상 상담원 응답');assert node('privacy_pin') is None;tap('x_home');mark('Locked medical information never blocks the direct 112 path')
 tap('x_family');wait('privacy_pin');enter(pin)
 assert c.request('CLICK android:id/button2').get('clicked')=='true','PIN checking must remain cancellable'
 wait('x_emergency');tap('x_emergency');tap('x_119');wait('x_session_status','가상 상담원 응답');assert node('x_profile_edit_1') is None;tap('x_home');mark('A pending PIN calculation is cancellable and never blocks urgent entry')
 result={'status':'PASS','checks':checks,'nativeCredential':'Fallback PIN tested; native device-credential path is capability-routed but this emulator has no enrolled system lock.',
         'captureBlackFraction':black,'captureProtected':True,'pinPlaintextStored':False,'profilesEncrypted':True,'emulatorAuthSeconds':timings,'helperReconnects':c.reconnections,'apkSha256':verify_installed_apk(adb,root/'app/build/outputs/apk/debug/app-debug.apk',sdk,'0.6.0-demo')}
except Exception as e:
 import traceback;traceback.print_exc()
 result={'status':'FAIL','checks':checks,'error':str(e) or type(e).__name__};print('FAIL',str(e) or type(e).__name__,flush=True)
 try:(out/'failure.xml').write_text(c.dump(),encoding='utf-8')
 except Exception:pass
 try:screenshot('failure-secure')
 except Exception:pass
finally:
 (out.parent/'privacy.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8');c.close()
if result['status']!='PASS':raise SystemExit(1)
