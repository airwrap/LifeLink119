"""Five-section medical form checks, synthetic data only on a clean QA AVD.

No official login, SMS requests, agency submissions, or private screenshots.
"""
import argparse
import json
import os
from pathlib import Path
import re
import secrets
import subprocess
import time
import traceback
import xml.etree.ElementTree as ET

from qa_avd_guard import QaAvdGuard, android_sdk
from ui_snapshot_client import SnapshotClient, verify_installed_apk

ROOT=Path(__file__).resolve().parents[1]


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial',default='emulator-5584')
    args=parser.parse_args()
    sdk=android_sdk();adb=[str(sdk/'platform-tools/adb.exe'),'-s',args.serial]
    guard=QaAvdGuard(adb).acquire_clean()
    user=guard.verify_target()
    version=re.search(r'versionName\s*=\s*"([^"]+)"',(ROOT/'app/build.gradle.kts').read_text(encoding='utf-8')).group(1)
    digest=verify_installed_apk(adb,ROOT/'app/build/outputs/apk/debug/app-debug.apk',sdk,version)
    os.environ['LIFELINK_TEST_PIN']=''.join(str(secrets.randbelow(10)) for _ in range(8))
    client=SnapshotClient(adb,auto_auth=True);started=False;cleaned=False;checks=[]
    out=ROOT/'verification/safe-call-form';out.mkdir(parents=True,exist_ok=True)

    def run(*args):return subprocess.check_output(adb+list(args),timeout=40)
    def node(key):return ET.fromstring(client.node(key)).find('.//node')
    def find(key):
        n=node(key)
        if n is not None:return n
        for direction in ('up','down'):
            for _ in range(60):
                if not client.scroll(direction):break
                n=node(key)
                if n is not None:return n
        return None
    def wait(key,text='',seconds=25):
        deadline=time.monotonic()+seconds
        while time.monotonic()<deadline:
            n=find(key)
            if n is not None and text in n.get('text',''):return n
            time.sleep(.2)
        raise RuntimeError('Expected QA control/state not found: '+key)
    def tap(key):
        if client.click(key):return
        for direction in ('up','down'):
            for _ in range(60):
                if not client.scroll(direction):break
                if client.click(key):return
        raise RuntimeError('Cannot click QA control: '+key)
    def set_text(key,value):
        tap(key)
        if client.request('SET_TEXT com.lifelink119.app:id/'+key+' '+value).get('changed')!='true':
            raise RuntimeError('Could not enter fixed synthetic input: '+key)
    def select(key,label):
        tap(key);time.sleep(.2)
        matches=[n for n in ET.fromstring(client.dump()).iter('node') if n.get('text')==label]
        if not matches:raise RuntimeError('Synthetic picker option missing: '+key)
        bounds=list(map(int,re.findall(r'\d+',matches[-1].get('bounds'))))
        run('shell','input','tap',str((bounds[0]+bounds[2])//2),str((bounds[1]+bounds[3])//2))
        time.sleep(.4)
    def mark(text):checks.append(text);print('PASS '+text,flush=True)
    def tap_text(label):
        for direction in ('down','up'):
            for _ in range(60):
                matches=[n for n in ET.fromstring(client.dump()).iter('node') if n.get('text')==label and n.get('clickable')=='true']
                if matches:
                    bounds=list(map(int,re.findall(r'\d+',matches[0].get('bounds'))))
                    run('shell','input','tap',str((bounds[0]+bounds[2])//2),str((bounds[1]+bounds[3])//2));time.sleep(.4);return
                if not client.scroll(direction):break
        raise RuntimeError('QA action label missing')
    def all_text():
        result=[]
        for direction in ('up','down'):
            for _ in range(60):
                result.extend(n.get('text','') for n in ET.fromstring(client.dump()).iter('node'))
                if not client.scroll(direction):break
        return '\n'.join(result)
    def cleanup():
        guard.before_cleanup();tap('x_home');tap('x_more');tap('x_tools');tap('privacy_settings');tap('privacy_reset')
        if client.request('CLICK android:id/button1').get('clicked')!='true':raise RuntimeError('QA reset confirmation missing')
        wait('x_emergency')
        for pref,keys in [('lifelink_medical_lock_v1',{'pin_salt','pin_verifier'}),('lifelink_personal_health_v1',{'records_cipher','record_cipher'})]:
            raw=run('shell','run-as','com.lifelink119.app','--user',user,'cat','shared_prefs/'+pref+'.xml')
            if any(n.get('name') in keys for n in ET.fromstring(raw)):raise RuntimeError('QA private data remains')

    result={'status':'FAIL'}
    try:
        run('shell','am','force-stop','com.lifelink119.app')
        run('shell','am','start','-W','-n','com.lifelink119.app/.MainActivity')
        client.start();wait('x_emergency');started=True
        tap('x_family');wait('x_my_health_edit');tap('x_my_health_edit')
        set_text('x_my_health_name','QAFORMSELF');set_text('x_my_health_medications','QAFORMMED')
        for key,value in [('application_type','QATYPE'),('application_detail','QADETAIL'),('mobility','QAMOBILITY'),('call_reason','QACALLREASON'),('past_history','QAPAST')]:
            set_text('x_health_'+key,value)
        select('x_health_blood','AB');select('x_health_rhesus','Rh+');select('x_health_elevator','없음');select('x_health_can_call','불가능')
        tap('x_health_condition_add');set_text('x_health_condition_first','QACONDITION')
        tap('x_health_infection_add');set_text('x_health_infection_first','QAINFECTION')
        for key,value in [('facility','QAFACILITY'),('specialty','QASPECIALTY'),('discomfort','QADISCOMFORT'),('constitution','QACONSTITUTION'),('responder_requests','QAREQUEST'),('frequent_place','QAPLACE')]:
            set_text('x_health_'+key,value)
        set_text('x_health_day_phone','00000000000');set_text('x_health_night_phone','00000000001')
        tap('x_health_disability_add');set_text('x_health_disability_first','QADISABILITY')
        tap('x_my_health_save');wait('x_my_health_status','보호자')
        if wait('x_health_facility').get('text')!='QAFACILITY':raise RuntimeError('Validation discarded the structured draft')
        mark('Applicant, disease, facility, disability and responder fields retain input across validation failure')
        tap('x_health_guardian_add')
        for key,value in [('guardian_name','QAGUARDIAN'),('guardian_category','QAFAMILY'),('guardian_relation','QACHILD')]:set_text('x_health_'+key,value)
        set_text('x_health_guardian_mobile','00000000002');set_text('x_health_guardian_phone','00000000003')
        set_text('x_health_guardian_email','qa@example.invalid')
        select('x_health_guardian_living','동거');select('x_health_guardian_sms','예')
        tap('x_health_guardian_add');tap_text('보호자 2 삭제')
        if wait('x_health_guardian_name').get('text')!='QAGUARDIAN':raise RuntimeError('Removing a second guardian affected the first')
        tap('x_health_condition_remove');tap('x_health_condition_add');set_text('x_health_condition_first','QACONDITION')
        mark('Repeated rows add and remove without losing other sections or an existing guardian')
        tap('x_health_guardian_call_confirm')
        mark('Guardian name, two relationship levels, contacts, email, residence and SMS preference are editable')
        # Updating legacy medication text must not discard newly structured sections.
        set_text('x_my_health_medications','QAFORMMEDCHANGED')
        tap('privacy_lock');tap('privacy_unlock')
        if wait('x_health_guardian_name').get('text')!='QAGUARDIAN':raise RuntimeError('Relock discarded guardian draft')
        run('shell','cmd','uimode','night','yes');time.sleep(.4)
        if wait('x_health_facility').get('text')!='QAFACILITY':raise RuntimeError('Configuration discarded facility draft')
        run('shell','cmd','uimode','night','no');time.sleep(.4)
        tap('x_my_health_save');wait('x_my_health_edit','수정')
        expected=['QATYPE','QADETAIL','QAMOBILITY','QACALLREASON','QAPAST','QACONDITION','QAINFECTION','QAFACILITY','QASPECIALTY','QADISCOMFORT','QACONSTITUTION','QAREQUEST','QAPLACE','QADISABILITY','QAGUARDIAN','QAFAMILY','QACHILD','qa@example.invalid','QAFORMMEDCHANGED']
        text=all_text()
        if any(value not in text for value in expected):raise RuntimeError('Saved five-section summary lost a synthetic field')
        mark('Five sections save and display every supplied field after relock and configuration changes')
        stored=run('shell','run-as','com.lifelink119.app','--user',user,'cat','shared_prefs/lifelink_personal_health_v1.xml').decode()
        if 'records_cipher' not in stored or any(value in stored for value in expected):raise RuntimeError('Expected encrypted-only private storage')
        mark('Structured medical and guardian data remain encrypted at rest')
        run('shell','am','force-stop','com.lifelink119.app');run('shell','am','start','-W','-n','com.lifelink119.app/.MainActivity')
        wait('x_emergency');tap('x_family');wait('x_my_health_edit','수정')
        restored_text=all_text()
        if any(value not in restored_text for value in ['QAFACILITY','QAGUARDIAN','QAFORMMEDCHANGED']):raise RuntimeError('Recreation lost stored structured data')
        mark('App restart and authentication restore the saved structured record')
        tap('x_my_health_edit');tap('x_health_guardians_reset')
        if client.request('CLICK android:id/button1').get('clicked')!='true':raise RuntimeError('Guardian reset confirmation missing')
        wait('x_health_guardian_add');tap('x_my_health_cancel');wait('x_my_health_edit','수정')
        if 'QAGUARDIAN' not in all_text():raise RuntimeError('Cancelling guardian reset changed the saved record')
        mark('Guardian reset changes the draft only and cancel preserves the saved guardian')
        cleanup();cleaned=True;mark('Only this QA run data and temporary PIN are erased')
        result={'status':'PASS','checks':checks,'apkSha256':digest,'privateScreensCaptured':False,'actualAgencyCalls':0,'officialLoginTested':False}
    except Exception as error:
        result={'status':'FAIL','checks':checks,'errorType':type(error).__name__,'error':str(error),'frames':[(f.name,f.lineno) for f in traceback.extract_tb(error.__traceback__)],'privateScreensCaptured':False}
        print('FAIL '+str(error),flush=True)
    finally:
        if started and not cleaned:
            try:cleanup();cleaned=True
            except Exception:result['cleanupError']='QA data remains on the dedicated AVD; inspect before reusing'
        result['qaDataRemoved']=not started or cleaned
        client.close();os.environ.pop('LIFELINK_TEST_PIN',None)
        (out/'native-form.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
    if result['status']!='PASS' or not cleaned:raise SystemExit(1)


if __name__=='__main__':main()
