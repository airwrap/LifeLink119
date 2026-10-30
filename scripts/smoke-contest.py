"""Native contest feature gate: 18 gallery cases, all A-M modules and offline collaboration."""
import argparse
import json
import os
from pathlib import Path
import subprocess
from contest_ui import ContestUI
from ui_snapshot_client import verify_installed_apk

root=Path(__file__).resolve().parents[1];p=argparse.ArgumentParser(description=__doc__);p.add_argument('--serial',default='emulator-5580');a=p.parse_args();assert a.serial.startswith('emulator-')
sdk=Path(os.environ.get('ANDROID_HOME',Path(os.environ['LOCALAPPDATA'])/'Android/Sdk'));adb=[str(sdk/'platform-tools/adb.exe'),'-s',a.serial]
out=root/'verification/v06/native';checks=[];cases=[]
def mark(name):checks.append(name);print('PASS',name,flush=True)
result={'status':'FAIL'}
try:
 with ContestUI(adb,out) as ui:
    catalog=json.loads((root/'app/src/main/assets/experience-catalog.json').read_text(encoding='utf-8'))
    for case in catalog['scenarios']:
        ui.gallery(case['id']);ui.expect('x_case_title',case['title'])
        labels=[ui.expect(f'x_primary_{i}','') for i in range(1,4)];assert all(labels) and len(set(labels))==3
        for i in range(1,4):
            n=ui.node(f'x_primary_{i}');assert n.get('content-desc') and n.get('enabled')=='true'
        ui.snap('case-'+case['id']);cases.append({'id':case['id'],'actions':labels,'status':'PASS'})
        ui.tap('x_end');ui.expect('x_finished','공유정보')
    mark('All 18 scenarios execute, expose three labelled actions, and finish')
    # Distinct routes and no prerequisite profile.
    ui.home();ui.tap('x_emergency');ui.tap('x_112');ui.expect('x_session_status','가상 상담원 응답');ui.expect('x_person','미등록 승객');mark('Direct 112 without profile or 119 prerequisite')
    ui.home();ui.tap('x_consult');ui.tap('x_medical');ui.expect('x_session_status','가상 상담원 응답');ui.snap('medical-consultation');mark('119 medical consultation route')
    # Fake family CRUD and default restore; independent driver and patient.
    ui.home();ui.tap('x_family');ui.tap('x_profile_add');ui.confirm();ui.tap('x_profile_save');ui.tap('x_profile_edit_11');ui.confirm();ui.tap('x_profile_delete');ui.confirm();ui.tap('x_profile_edit_1');ui.confirm();ui.tap('x_profile_save');mark('Family add/edit/delete with authenticated medical access')
    ui.home();ui.tap('x_more');ui.tap('x_tools');ui.tap('x_driver');ui.tap('x_driver_2');ui.expect('x_driver_status','민수');ui.home();ui.tap('x_emergency');ui.tap('x_119');ui.expect('x_session_status','가상 상담원 응답');ui.expect('x_person','미등록 승객');mark('Digital-key mock driver never auto-selects patient')
    ui.gallery('S01');ui.support('safe_call');ui.tap('x_safe_lookup');ui.expect('x_safe_status','확인했습니다');ui.snap('safe-call-registered');ui.gallery('E04');ui.support('safe_call');ui.tap('x_safe_lookup');ui.expect('x_safe_status','없습니다');mark('SafeCall explicit consent: registered and unregistered fixtures')
    # Observation editing and field consent/revocation.
    ui.gallery('S05');ui.tap('x_primary_1');ui.set_value('x_glucose',80);ui.tap('x_summary_update');ui.expect('x_summary','80 mg/dL');ui.tap('x_share_summary');ui.tap('x_back');ui.tap('x_support');ui.tap('x_center');ui.expect('x_shared_count','1개');ui.snap('summary-consent');ui.tap('x_card_manage');ui.tap('x_revoke');ui.tap('x_center');ui.expect('x_shared_count','0개');mark('Editable summary and field-level consent/revocation reach same center')
    ui.gallery('S10');ui.tap('x_primary_1');ui.tap('x_voice');ui.snap('vision-voice-mock');mark('Vision voice-help mock is interactive and labelled')
    ui.gallery('S12');ui.tap('x_primary_1');ui.snap('cognitive-easy');mark('Cognitive simple steps')
    ui.gallery('S11');ui.tap('x_primary_1');ui.tap('x_consent_access');ui.tap('x_share_card');ui.tap('x_center');ui.expect('x_shared_count','1개');mark('Mobility assistance shared only with field consent')
    ui.home();ui.tap('x_more');ui.tap('x_tools');ui.tap('x_access');ui.tap('x_large_text');ui.home();ui.snap('large-text-home');ui.tap('x_more');ui.tap('x_tools');ui.tap('x_access');ui.tap('x_large_text');mark('Large text setting and recovery')
    # Narrow layout preview uses the exact same width breakpoints as a narrow window.
    ui.home();ui.tap('x_more');ui.tap('x_tools');ui.tap('x_narrow_preview');ui.home();b1=ui.bounds(ui.node('x_emergency'));b2=ui.bounds(ui.node('x_consult'));assert b1[3]<=b2[1],(b1,b2);assert b1[2]-b1[0]<=520
    ui.snap('narrow-520dp-home');ui.tap('x_emergency');ui.tap('x_119');ui.expect('x_session_status','가상 상담원 응답');ui.snap('narrow-520dp-session');ui.home();ui.tap('x_more');ui.tap('x_tools');ui.tap('x_narrow_preview');mark('520dp constrained native layout: stacked CTAs and urgent session')
    # Full offline cooperative mission and separate authority approval, without the PC server.
    ui.gallery('S14');ui.tap('x_primary_1');ui.tap('x_share_location');ui.tap('x_back');ui.support('cooperation');ui.tap('x_coop_consent');ui.tap('x_coop_case_early');ui.tap('x_coop_review');ui.tap('x_coop_invite_a');ui.tap('x_coop_decline');ui.tap('x_coop_invite_d');ui.tap('x_coop_accept');ui.tap('x_coop_authorize');ui.expect('x_coop_status','임무 부여')
    assert ui.node('x_coop_preview') is None
    ui.tap('x_coop_request_112');ui.tap('x_coop_beacon_review');ui.tap('x_coop_beacon_reject');ui.expect('x_coop_beacon_status','거절');ui.tap('x_coop_request_119');ui.tap('x_coop_beacon_review');ui.tap('x_coop_beacon_approve');assert ui.node('x_coop_preview') is None
    ui.tap('x_coop_beacon_start');ui.expect('x_coop_preview','DEMO');ui.snap('offline-approved-beacon')
    subprocess.run(adb+['shell','am','start','-W','--display','0','-a','android.settings.SETTINGS'],capture_output=True,check=True,timeout=30)
    subprocess.run(adb+['shell','am','start','-W','--display','0','-f','0x10000000','-n','com.lifelink119.app/.MainActivity'],capture_output=True,check=True,timeout=30)
    assert ui.node('x_coop_preview') is None,'Background preview must not resume automatically'
    ui.expect('x_coop_beacon_status','시작 전');ui.tap('x_coop_beacon_start');ui.expect('x_coop_preview','DEMO');mark('Actual app background/foreground stops beacon until explicit restart')
    ui.tap('x_coop_beacon_revoke');assert ui.node('x_coop_preview') is None
    ui.tap('x_coop_arrive');ui.tap('x_coop_handover');ui.expect('x_coop_status','인계');ui.snap('offline-handover');mark('Offline cooperation: decline/accept/mission/separate 112 rejection/119 approval/preview/revoke/handover')
    ui.tap('x_coop_restart');ui.tap('x_coop_consent');ui.tap('x_coop_case_none');ui.tap('x_coop_review');assert ui.node('x_coop_invite_a') is None and ui.node('x_coop_invite_d') is None;ui.snap('offline-no-responder');ui.tap('x_coop_cancel');mark('No-responder fallback keeps original 119 session')
    # Existing manual and diagnostic entry remain reachable.
    ui.home();ui.tap('x_more');ui.tap('x_tools');ui.tap('x_manual');ui.snap('manual');ui.tap('x_home');mark('Existing offline emergency manual')
    ui.home()
    result={'status':'PASS','checks':checks,'scenarios':cases,'apkSha256':verify_installed_apk(adb,root/'app/build/outputs/apk/debug/app-debug.apk',sdk,'0.6.0-demo'),'limitations':['External agency, media, location and vehicle actuation are synthetic. Narrow preview is not an OEM window certification.']}
except Exception as e:
 result={'status':'FAIL','checks':checks,'scenarios':cases,'error':str(e)};print('FAIL',str(e),flush=True)
 try:
  with ContestUI(adb,out) as ui:ui.snap('failure')
 except Exception:pass
finally:
 (out.parent/'contest-native.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
if result['status']!='PASS':raise SystemExit(1)
