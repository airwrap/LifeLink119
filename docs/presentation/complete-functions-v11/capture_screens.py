"""Record only non-secure public Views from the installed v0.11 APK on the dedicated AVD."""
import sys,os,time,json,subprocess,xml.etree.ElementTree as ET
from pathlib import Path
from PIL import Image,ImageStat
root=Path(__file__).resolve().parents[3];sys.path.insert(0,str(root/'scripts'))
from contest_ui import ContestUI
from native_window_client import WindowClient
from ui_snapshot_client import verify_installed_apk
sdk=Path(os.environ['LOCALAPPDATA'])/'Android/Sdk';adb=[str(sdk/'platform-tools/adb.exe'),'-s','emulator-5580']
assert subprocess.check_output(adb+['emu','avd','name'],timeout=20).decode().splitlines()[0]=='LifeLink119_Verify_20261101'
out=root/'docs/presentation/complete-functions-v11/.build/screens';out.mkdir(parents=True,exist_ok=True)
checks=[];failures=[]
class UI(ContestUI):
 def __init__(self):super().__init__(adb,out);self.client=WindowClient(adb)
 def __enter__(self):self.client.start();return self
 def tap(self,key):
  try:return super().tap(key)
  except AssertionError:
   self.focus();time.sleep(.4);return super().tap(key)
 def expect(self,key,value='',seconds=18):
  if self.node(key) is None:
   for direction in ('up','down'):
    for _ in range(20):
     if not self.client.scroll(direction):break
     if self.node(key) is not None:return super().expect(key,value,seconds)
  try:return super().expect(key,value,seconds)
  except AssertionError:
   self.focus();time.sleep(.4);return super().expect(key,value,seconds)
def save(ui,name,required=None,text=''):
 if required:ui.expect(required,text)
 time.sleep(.65)
 if ui.client.request('PING').get('secureRefused'):raise RuntimeError('Protected screen: capture intentionally refused')
 tree=ui.client.dump()
 if '<node ' not in tree:
  ui.focus();time.sleep(.5);tree=ui.client.dump()
 assert '<node ' in tree,'No visible app window'
 data=ui.client.frame(); p=out/(name+'.jpg');p.write_bytes(data)
 im=Image.open(p).convert('L');assert min(im.size)>500 and ImageStat.Stat(im).stddev[0]>5,'blank frame'
 (out/(name+'.xml')).write_text(tree,encoding='utf-8')
 checks.append(name);print('CAPTURE',name,flush=True)
def scene(ui,code):
 ui.home();ui.tap('x_more');ui.tap('x_tools');ui.tap('x_gallery');ui.tap('x_case_'+code.lower());ui.expect('x_session_status','가상 상담원 응답')
def tools(ui):ui.home();ui.tap('x_more');ui.tap('x_tools')
def run(name,fn):
 try:ui.focus();time.sleep(.3);fn()
 except Exception as e:failures.append({'stage':name,'error':str(e)});print('FAILED',name,repr(e),flush=True)
try:
 digest=verify_installed_apk(adb,root/'artifacts/LifeLink119-v0.11.0-health-link-demo.apk',sdk,'0.11.0-demo')
 subprocess.run(adb+['shell','am','force-stop','com.lifelink119.app'],check=True,timeout=20)
 with UI() as ui:
  def home():ui.home();save(ui,'01-home','x_emergency','119')
  def urgent():
   ui.home();ui.tap('x_emergency');ui.expect('x_session_status','가상 상담원 응답');save(ui,'02-driving','x_session_status')
   ui.tap('x_driving_send');save(ui,'03-driving-sent','x_driving_report','전달')
   ui.tap('x_guided_video');ui.tap('x_guide_request');save(ui,'04-video-consent','x_guide_consent')
   ui.tap('x_guide_consent');ui.tap('x_guide_start');save(ui,'05-video-guide','x_guide_message')
   ui.tap('x_guide_stop');ui.tap('x_guide_back');ui.tap('x_end');save(ui,'06-finish','x_finished')
  def consult():
   ui.home();ui.tap('x_consult');save(ui,'07-consult-choice','x_medical')
   ui.tap('x_medical');ui.expect('x_session_status','가상 상담원 응답');save(ui,'08-consult','x_session_status')
   ui.tap('x_primary_2');save(ui,'09-location','x_location_status');ui.tap('x_share_location');save(ui,'10-location-shared','x_share_location')
   ui.tap('x_back');ui.tap('x_support');save(ui,'11-support','x_change_case')
  def police():
   ui.home();ui.tap('x_home_112');ui.expect('x_session_status','가상 상담원 응답');save(ui,'12-112','x_session_status')
   ui.tap('x_primary_1');save(ui,'13-text','x_send_phrase');ui.tap('x_send_phrase');save(ui,'14-text-sent','x_messages')
   ui.tap('x_back');ui.tap('x_primary_3');save(ui,'15-police','x_police_status')
  def mental():
   ui.home();ui.tap('x_consult');ui.tap('x_109');ui.expect('x_session_status','가상 상담원 응답');save(ui,'16-109','x_session_status')
  def sign():
   scene(ui,'S08');save(ui,'17-hearing','x_case_title');ui.tap('x_primary_2');save(ui,'18-sign','x_sign_status')
  def easy():
   scene(ui,'S12');ui.tap('x_primary_1');save(ui,'19-easy','x_scroll')
  def voice():
   scene(ui,'S10');ui.tap('x_primary_1');save(ui,'29-voice','x_voice')
  def navigation():
   scene(ui,'E02');ui.tap('x_primary_2');save(ui,'30-navigation','x_nav_status')
  def driving_setup():
   tools(ui);ui.tap('x_driving_setup');save(ui,'31-driving-voice','x_driving_voice')
  def security():
   tools(ui);ui.tap('privacy_settings');save(ui,'32-security','privacy_reset')
  def gallery():tools(ui);ui.tap('x_gallery');save(ui,'20-gallery','x_case_s01')
  def access():tools(ui);ui.tap('x_access');save(ui,'21-access','x_large_text')
  def power():
   ui.home();ui.tap('x_more');ui.tap('x_power');save(ui,'22-power','x_power_result')
   ui.tap('x_sdk');save(ui,'23-sdk','read_battery')
  def manual():tools(ui);ui.tap('x_manual');save(ui,'24-manual','content_scroll')
  def cooperation():
   scene(ui,'S14');ui.tap('x_primary_1');ui.tap('x_share_location');ui.tap('x_back');ui.tap('x_support');ui.tap('x_action_cooperation')
   save(ui,'25-coop-consent','x_coop_consent');ui.tap('x_coop_consent');ui.tap('x_coop_case_early');ui.tap('x_coop_review');save(ui,'26-coop-review','x_coop_invite_a')
   ui.tap('x_coop_invite_a');ui.tap('x_coop_accept');ui.tap('x_coop_authorize');ui.tap('x_coop_request_119');ui.tap('x_coop_beacon_review');ui.tap('x_coop_beacon_approve');save(ui,'27-coop-approved','x_coop_beacon_start')
   ui.tap('x_coop_beacon_start');save(ui,'28-coop-beacon','x_coop_preview')
  stages=[('home',home),('urgent',urgent),('consult',consult),('police',police),('mental',mental),('sign',sign),('easy',easy),('gallery',gallery),('access',access),('power',power),('manual',manual),('cooperation',cooperation),('voice',voice),('navigation',navigation),('driving_setup',driving_setup),('security',security)]
  chosen=set(sys.argv[1:])
  for name,fn in stages:
   if not chosen or name in chosen:run(name,fn)
 result={'apkSha256':digest,'captured':checks,'failures':failures,'limitations':['Protected views intentionally not captured.','App screen capture uses native View rendering.']}
except Exception as e:result={'captured':checks,'failures':failures+[{'stage':'setup','error':str(e)}]}
(out/'capture-report.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps(result,ensure_ascii=False),flush=True)
