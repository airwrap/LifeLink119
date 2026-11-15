"""Synthetic-only Android end-to-end checks. Refuses existing private data/PIN.

No protected screenshots, real phone numbers, external submissions or agency calls.
"""
from pathlib import Path
import argparse, json, os, re, secrets, subprocess, time, xml.etree.ElementTree as ET
from ui_snapshot_client import SnapshotClient, verify_installed_apk
from qa_avd_guard import QaAvdGuard, android_sdk

ROOT=Path(__file__).resolve().parents[1]

def main():
    p=argparse.ArgumentParser();p.add_argument('--serial',default='emulator-5580');args=p.parse_args()
    sdk=android_sdk();adb=[str(sdk/'platform-tools/adb.exe'),'-s',args.serial]
    def run(*a):return subprocess.check_output(adb+list(a),timeout=40)
    qa_guard=QaAvdGuard(adb).acquire_clean()
    user=qa_guard.verify_target()
    def prefs(name):
        value=subprocess.run(adb+['shell','run-as','com.lifelink119.app','--user',user,'cat','shared_prefs/'+name+'.xml'],capture_output=True,timeout=20)
        return value.stdout.decode() if value.returncode==0 else '<map/>'
    out=ROOT/'verification/v18';out.mkdir(parents=True,exist_ok=True)
    pin=''.join(str(secrets.randbelow(10)) for _ in range(8));os.environ['LIFELINK_TEST_PIN']=pin
    c=SnapshotClient(adb,auto_auth=True);checks=[];result={'status':'FAIL'};qa_started=False;cleaned=False
    def node(k):return ET.fromstring(c.node(k)).find('.//node')
    def find(k):
        n=node(k)
        if n is not None:return n
        for direction in ('up','down'):
            for _ in range(18):
                if not c.scroll(direction):break
                n=node(k)
                if n is not None:return n
        return None
    def wait(k,text='',seconds=20):
        end=time.monotonic()+seconds
        while time.monotonic()<end:
            n=find(k)
            if n is not None and text in n.get('text',''):return n
            time.sleep(.15)
        n=node(k)
        actual=n.get('text','') if n is not None and k=='x_safe_status' else '[redacted]'
        raise AssertionError('Missing expected control/state: '+k+' expected='+text+' actual='+actual)
    def tap(k):
        if c.click(k):return
        for d in ('down','up'):
            for _ in range(24):
                if not c.scroll(d):break
                if c.click(k):return
        raise AssertionError('Cannot activate: '+k)
    def home():tap('x_home');wait('x_emergency')
    def mark(name):checks.append(name);print('PASS '+name,flush=True)
    def all_text():
        values=[]
        for direction in ('up','down'):
            for _ in range(18):
                values.extend(n.get('text','') for n in ET.fromstring(c.dump()).iter('node'))
                if not c.scroll(direction):break
        return '\n'.join(values)
    def set_qa(k,value):
        tap(k);assert c.request('SET_TEXT com.lifelink119.app:id/'+k+' '+value).get('changed')=='true', 'Could not set synthetic QA field: '+k
    def select(k,label):
        tap(k);time.sleep(.2)
        tree=ET.fromstring(c.dump());matches=[n for n in tree.iter('node') if n.get('text')==label]
        assert matches,'Expected fixed picker option missing'
        bounds=list(map(int,re.findall(r'\d+',matches[-1].get('bounds'))))
        run('shell','input','tap',str((bounds[0]+bounds[2])//2),str((bounds[1]+bounds[3])//2));time.sleep(.4)
        deadline=time.monotonic()+5;captions=[]
        while time.monotonic()<deadline:
            current=find(k)
            if current is not None:
                captions=[n.get('text','') for n in current.iter('node')]
                if label in captions:return
            time.sleep(.15)
        raise AssertionError('Picker did not retain fixed option: '+k+' expected='+label+' actual='+repr(captions))
    def safe_page():
        if node('x_return_session') is not None:tap('x_return_session')
        wait('x_session_status')
        tap('x_driving_extra' if node('x_driving_extra') is not None else 'x_support')
        tap('x_action_safe_call');wait('x_safe_status')
    def lookup(expected):
        consent=find('x_safe_consent');assert consent is not None
        if consent.get('checked')!='true':tap('x_safe_consent')
        tap('x_safe_lookup');wait('x_safe_status',expected)
    def cleanup():
        qa_guard.before_cleanup()
        home();tap('x_more');tap('x_tools');tap('privacy_settings');tap('privacy_reset')
        assert c.request('CLICK android:id/button1').get('clicked')=='true';wait('x_emergency')
        for name,keys in [('lifelink_medical_lock_v1',{'pin_salt','pin_verifier'}),('lifelink_personal_health_v1',{'record_cipher','records_cipher'})]:
            assert not any(n.get('name') in keys for n in ET.fromstring(prefs(name)))
    try:
        digest=verify_installed_apk(adb,ROOT/'app/build/outputs/apk/debug/app-debug.apk',sdk)
        run('shell','am','force-stop','com.lifelink119.app');run('shell','am','start','-W','-n','com.lifelink119.app/.MainActivity')
        c.start();wait('x_emergency')
        tap('x_more');tap('x_safe_official');wait('x_agency_status');tap('x_agency_proxy')
        assert '수혜자' in c.dump();assert node('privacy_pin') is None
        wait('x_agency_open');mark('Public official registration guide separates self/proxy and requires no medical PIN')
        tap('x_agency_open');time.sleep(1)
        activities=run('shell','dumpsys','activity','activities').decode(errors='replace')
        if '.ui.OfficialHealthBrowserActivity' in activities:
            wait('official_web_origin','HTTPS · u119.nfa.go.kr')
            mark('Internal official browser displays the public HTTPS origin without attached personal data')
            tap('official_web_close');wait('x_agency_status')
        elif 'https://u119.nfa.go.kr/' in activities:
            mark('Official signup button opens its fixed public URL without attached personal data')
            run('shell','input','keyevent','4');time.sleep(.3)
            run('shell','am','start','-W','-f','0x14000000','-n','com.lifelink119.app/.MainActivity');wait('x_agency_status')
        else:
            assert 'https://u119.nfa.go.kr/' in c.dump(), 'Neither official navigation nor no-browser guidance was observed'
            assert c.request('CLICK android:id/button1').get('clicked')=='true'
            mark('No-browser device provides the exact official URL as a safe fallback')
        home();tap('x_more');tap('x_manual');wait('manual_first');home()
        qa_started=True
        tap('x_family');wait('x_my_health_edit')
        mark('Legacy manual -> home -> medical PIN authentication opens protected information')
        home();tap('x_emergency');wait('x_session_status','가상 상담원 응답');assert node('privacy_pin') is None
        mark('Emergency request starts while unregistered and medically locked')
        qa_started=True;safe_page();lookup('도움 대상자');mark('Unselected beneficiary is distinct from unregistered')
        tap('x_safe_person_choose');tap('x_person_1');safe_page()
        for label,expected in [('등록한 전화','일치'),('다른 가족 전화','다른 회선'),('차량 자체 회선','다른 회선'),('회선 확인 불가','확인할 수 없습니다'),('가족 공용 전화','대상 확인')]:
            select('x_safe_line',label);lookup(expected)
        mark('Real Android picker/touch covers matching, different, vehicle, unknown and shared synthetic lines')
        for label,expected in [('시간 초과','초과'),('조회 오류','확인하지 못했습니다'),('기관 연계 미지원','연결되지 않았습니다')]:
            select('x_safe_fault',label);lookup(expected)
        mark('Timeout, lookup error and unsupported are not misreported as unregistered')
        select('x_safe_fault','정상 응답')
        if node('privacy_lock') is not None:tap('privacy_lock')
        wait('x_safe_status')
        tap('x_safe_role_proxy');lookup('권한을 확인');mark('Proxy lookup requires an explicit authority attestation')
        tap('x_safe_authority');select('x_safe_line','등록한 전화');lookup('일치')
        tap('x_safe_revoke');wait('x_safe_status','확인 전');mark('Proxy attestation and per-request consent/revoke work')
        home();tap('x_family');wait('x_my_health_edit');tap('x_my_health_edit')
        set_qa('x_my_health_name','QAV18SELF');set_qa('x_my_health_medications','QAV18SELFMED')
        set_qa('x_health_phone','000');tap('x_my_health_save');wait('x_my_health_status','전화번호')
        assert wait('x_my_health_name').get('text')=='QAV18SELF'
        assert wait('x_my_health_medications').get('text')=='QAV18SELFMED'
        mark('Validation failure keeps the unsaved editor fields')
        run('shell','cmd','uimode','night','yes');time.sleep(.5)
        assert wait('x_my_health_name').get('text')=='QAV18SELF'
        run('shell','cmd','uimode','night','no');time.sleep(.5)
        assert wait('x_my_health_medications').get('text')=='QAV18SELFMED'
        mark('Day/night configuration changes preserve the editor draft')
        deadline=time.monotonic()+62
        while time.monotonic()<deadline:
            print('Waiting for the medical authentication expiry regression check',flush=True)
            time.sleep(min(20,max(0,deadline-time.monotonic())))
        assert node('privacy_unlock') is not None, 'Medical view did not relock after expiry'
        tap('privacy_unlock')
        assert wait('x_my_health_name').get('text')=='QAV18SELF'
        assert wait('x_my_health_medications').get('text')=='QAV18SELFMED'
        mark('60-second relock and reauthentication preserve the memory-only draft')
        set_qa('x_health_phone','00000000000');tap('x_health_registered');tap('x_my_health_save');wait('x_my_health_edit','수정')
        assert 'QAV18SELFMED' in all_text();mark('Local self record stores registration reference and direct medical history')
        tap('x_my_health_add');set_qa('x_my_health_name','QAV18FAMILY');set_qa('x_my_health_medications','QAV18FAMILYMED')
        tap('x_health_proxy_consent');tap('x_my_health_save');wait('x_my_health_edit','수정')
        tree=all_text();assert 'QAV18FAMILYMED' in tree and 'QAV18SELFMED' not in tree
        data=prefs('lifelink_personal_health_v1');assert 'records_cipher' in data
        assert all(v not in data for v in ['QAV18SELF','QAV18FAMILY','00000000000'])
        tap('x_local_self');assert 'QAV18SELFMED' in all_text();mark('Family records remain distinct and sensitive fields are encrypted at rest')
        tap('x_local_record_1');tap('x_my_health_delete');assert c.request('CLICK android:id/button1').get('clicked')=='true'
        wait('x_my_health_edit','수정');remaining=all_text();assert 'QAV18SELFMED' in remaining and 'QAV18FAMILY' not in remaining
        mark('Deleting one family record preserves the self record')
        home();tap('x_family');wait('x_my_health_edit','수정');assert 'QAV18SELFMED' in all_text()
        mark('Home locks medical information and reauthentication preserves saved data')
        cleanup();cleaned=True;mark('Only QA-created medical data and temporary PIN are removed')
        result={'status':'PASS','checks':checks,'apkSha256':digest,'privateScreensCaptured':False,'actualAgencyCalls':0,'officialApiConnected':False}
    except Exception as error:
        result={'status':'FAIL','checks':checks,'error':str(error),'privateScreensCaptured':False}
        try:result['visibleControlIds']=[n.get('resource-id') for n in ET.fromstring(c.dump()).iter('node') if n.get('resource-id')]
        except Exception:pass
        print('FAIL '+str(error),flush=True)
    finally:
        if qa_started and not cleaned:
            try:cleanup();cleaned=True
            except Exception:result['cleanupError']='QA data remains on the dedicated AVD; inspect before reusing.'
        result['qaDataRemoved']=not qa_started or cleaned
        c.close();os.environ.pop('LIFELINK_TEST_PIN',None)
        (out/'native-safe-call.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
    if result['status']!='PASS' or not cleaned:raise SystemExit(1)

if __name__=='__main__':main()
