"""QA-only local records on a clean dedicated AVD; never use real medical data."""
import json,os,secrets,subprocess,time,xml.etree.ElementTree as ET
from pathlib import Path
from ui_snapshot_client import SnapshotClient,verify_installed_apk

root=Path(__file__).resolve().parents[1];out=root/'verification/v10';out.mkdir(parents=True,exist_ok=True)
sdk=Path(os.environ['LOCALAPPDATA'])/'Android/Sdk';adb=[str(sdk/'platform-tools/adb.exe'),'-s','emulator-5580']
assert subprocess.check_output(adb+['emu','avd','name'],timeout=20).decode().splitlines()[0]=='LifeLink119_Verify_20261101'
user=subprocess.check_output(adb+['shell','am','get-current-user'],timeout=20).decode().strip()
assert user.isdigit()
for pref,keys in [('lifelink_medical_lock_v1',{'pin_salt','pin_verifier'}),('lifelink_personal_health_v1',{'record_cipher'}),('lifelink_synthetic_profiles_v05',{'profiles','profiles_cipher'})]:
 existing=subprocess.run(adb+['shell','run-as','com.lifelink119.app','--user',user,'cat','shared_prefs/'+pref+'.xml'],capture_output=True,timeout=20)
 if existing.returncode==0:
  assert not any(n.get('name') in keys for n in ET.fromstring(existing.stdout)), 'Use a clean dedicated AVD; existing data was left untouched'
pin=''.join(str(secrets.randbelow(10)) for _ in range(8));c=SnapshotClient(adb);checks=[];result={'status':'FAIL'}
def node(k):return ET.fromstring(c.node(k)).find('.//node')
def wait(k,word='',seconds=40):
 end=time.monotonic()+seconds
 while time.monotonic()<end:
  n=node(k)
  if n is not None and word in n.get('text',''):return n
  time.sleep(.2)
 raise AssertionError('Unavailable: '+k)
def tap(k):
 if c.click(k):return
 for d in ('down','up'):
  for _ in range(25):
   if not c.scroll(d):break
   if c.click(k):return
 raise AssertionError('Cannot click '+k)
def enter_pin(setup=False):
 assert c.request('SET_TEXT com.lifelink119.app:id/privacy_pin '+pin).get('changed')=='true'
 if setup:assert c.request('SET_TEXT com.lifelink119.app:id/privacy_confirm '+pin).get('changed')=='true'
 assert c.request('CLICK android:id/button1').get('clicked')=='true'
def set_synthetic(k,value):
 tap(k);subprocess.run(adb+['shell','input','text',value],check=True,timeout=20)
 n=wait(k);assert n.get('text')==value,'Synthetic value did not appear in '+k
def mark(s):checks.append(s);print('PASS',s,flush=True)
try:
 subprocess.run(adb+['shell','am','force-stop','com.lifelink119.app'],check=True,timeout=20)
 subprocess.run(adb+['shell','am','start','-W','--display','0','-n','com.lifelink119.app/.MainActivity'],check=True,capture_output=True,timeout=50)
 c.start();wait('x_family');tap('x_family');wait('privacy_pin');wait('privacy_confirm');assert node('x_my_health_edit') is None
 enter_pin(True);wait('x_my_health_edit');mark('No personal screen before explicit authentication')
 tap('x_my_health_edit');wait('x_my_health_name')
 set_synthetic('x_my_health_name','QANAMEONLY');set_synthetic('x_my_health_conditions','QACONDITIONONLY')
 set_synthetic('x_my_health_allergies','QAALLERGYONLY');set_synthetic('x_my_health_medications','QAMEDICATIONONLY')
 tap('x_my_health_save');wait('x_my_health_edit','수정')
 tree=c.dump();assert all(x in tree for x in ('QANAMEONLY','QACONDITIONONLY','QAALLERGYONLY','QAMEDICATIONONLY'))
 data=subprocess.check_output(adb+['shell','run-as','com.lifelink119.app','--user',user,'cat','shared_prefs/lifelink_personal_health_v1.xml'],timeout=20).decode()
 assert 'record_cipher' in data and all(x not in data for x in ('QANAMEONLY','QACONDITIONONLY','QAALLERGYONLY','QAMEDICATIONONLY'))
 mark('Directly entered example medical and medication fields save encrypted')
 tap('x_home');tap('x_family');wait('privacy_pin');assert node('x_my_health_edit') is None
 enter_pin();wait('x_my_health_edit','수정');assert 'QAMEDICATIONONLY' in c.dump()
 mark('Home locks the view; record remains available only after reauthentication')
 tap('x_my_health_delete');assert c.request('CLICK android:id/button1').get('clicked')=='true';wait('x_my_health_edit','등록')
 data=subprocess.check_output(adb+['shell','run-as','com.lifelink119.app','--user',user,'cat','shared_prefs/lifelink_personal_health_v1.xml'],timeout=20).decode()
 assert 'record_cipher' not in data;mark('Explicit delete removes stored record')
 tap('x_home');tap('x_emergency');wait('x_session_status','가상 상담원 응답');assert node('privacy_pin') is None
 mark('119 request stays accessible without medical authentication')
 # This dedicated AVD had no medical preferences at the start. Clear only the QA PIN.
 tap('x_home');tap('x_more');tap('x_tools');tap('privacy_settings');tap('privacy_reset')
 assert c.request('CLICK android:id/button1').get('clicked')=='true';wait('x_family')
 result={'status':'PASS','checks':checks,'apkSha256':verify_installed_apk(adb,root/'app/build/outputs/apk/debug/app-debug.apk',sdk,'0.10.0-demo'),
         'limitations':['Synthetic input only; not a real 119 or health-data connection.','System credential path and real vehicle not exercised.']}
except Exception as e:
 result={'status':'FAIL','checks':checks,'error':str(e)};print('FAIL',str(e),flush=True)
finally:
 (out/'personal-health.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8');c.close()
if result['status']!='PASS':raise SystemExit(1)
