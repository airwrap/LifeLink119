"""Explicit QA patient selection and auth lifecycle. Never captures a protected screen."""
import argparse, json, os, secrets, subprocess, time, xml.etree.ElementTree as ET
from pathlib import Path
from qa_avd_guard import QaAvdGuard, android_sdk
from ui_snapshot_client import SnapshotClient, verify_installed_apk

ROOT=Path(__file__).resolve().parents[1]

def main():
    p=argparse.ArgumentParser(); p.add_argument('--serial',required=True); args=p.parse_args()
    sdk=android_sdk(); adb=[str(sdk/'platform-tools/adb.exe'),'-s',args.serial]
    guard=QaAvdGuard(adb).acquire_clean()
    restore_microphone=guard.microphone_for_test()
    digest=verify_installed_apk(adb,ROOT/'app/build/outputs/apk/debug/app-debug.apk',sdk,'0.18.0-demo')
    os.environ['LIFELINK_TEST_PIN']=''.join(str(secrets.randbelow(10)) for _ in range(8))
    client=SnapshotClient(adb,auto_auth=True)
    result=dict(status='FAIL',apkSha256=digest,checks=[],syntheticDataOnly=True,protectedScreenshots=0)
    out=ROOT/'verification/refined-ux';out.mkdir(parents=True,exist_ok=True)
    def run(*a): return subprocess.check_output(adb+list(a),timeout=30).decode().strip()
    def node(k):return ET.fromstring(client.node(k)).find('.//node')
    def find(k):
        n=node(k)
        if n is not None:return n
        for d in ['up','down']:
            for _ in range(30):
                if not client.scroll(d):break
                n=node(k)
                if n is not None:return n
        return None
    def tap(k):
        if find(k) is None or not client.click(k):raise AssertionError('Missing '+k)
    def wait(k,contains='',seconds=20):
        until=time.monotonic()+seconds
        while time.monotonic()<until:
            n=find(k)
            if n is not None and contains in n.get('text',''):return n.get('text','')
            time.sleep(.2)
        raise AssertionError('Expected control '+k+'; private values redacted')
    def mark(s):result['checks'].append(s); print('PASS '+s,flush=True)
    def include():
        tap('x_es_medical');client.authenticate_if_needed();wait('x_health_use_emergency');tap('x_health_use_emergency')
    try:
        run('shell','am','force-stop','com.lifelink119.app');run('shell','am','start','-W','-n','com.lifelink119.app/.MainActivity')
        client.start(choose_setup=False);wait('x_family');tap('x_family');client.authenticate_if_needed()
        tap('x_my_health_edit')
        values={'name':'QAPATIENT','conditions':'QAHISTORY','allergies':'QAALLERGY','medications':'QAMEDICINE'}
        for key,value in values.items():
            control='x_my_health_'+key;find(control)
            if client.request('SET_TEXT com.lifelink119.app:id/'+control+' '+value).get('changed')!='true':raise AssertionError('Synthetic form input failed')
        tap('x_my_health_save');wait('x_my_health_edit','수정')
        tap('x_home');tap('x_emergency');tap('x_es_entry');tap('x_es_packet')
        assert not any(v in wait('x_es_readout') for v in values.values())
        mark('An existing saved health record is never attached to an incident automatically')
        include();readout=wait('x_es_readout','QAPATIENT')
        assert all(v in readout for v in values.values())
        assert '공식 조회 아님' in readout and '외부 AI로 보내지 않습니다' in readout
        mark('Authenticated patient selection reuses saved history, allergies and medication in the human-readable packet')
        tap('x_es_medical');assert 'QAPATIENT' not in wait('x_es_readout')
        mark('Removing health information immediately removes it from the packet')
        include();wait('x_es_readout','QAPATIENT')
        tap('x_back');tap('x_es_ai');wait('x_es_choices');tap('x_es_choices');tap('x_es_yes')
        wait('x_es_question','숨')
        client.auto_auth=False
        print('CHECK Auth timeout hides family data while preserving current foreground question',flush=True)
        deadline=time.monotonic()+65
        while time.monotonic()<deadline:
            time.sleep(1)
        assert node('x_es_unlock') is None
        wait('x_es_question','숨')
        tap('x_back');tap('x_es_packet');assert 'QAPATIENT' not in wait('x_es_readout')
        mark('Family PIN expiry removes saved health data while keeping the ongoing foreground emergency dialogue available')
        client.auto_auth=True
        include();wait('x_es_readout','QAPATIENT')
        run('shell','input','keyevent','3');time.sleep(.3);run('shell','am','start','-W','-n','com.lifelink119.app/.MainActivity')
        assert node('x_es_readout') is None or 'QAPATIENT' not in node('x_es_readout').get('text','')
        mark('Backgrounding revokes incident health selection; private information does not reappear on return')
        result['status']='PASS'
    finally:
        try:
            guard.before_cleanup()
            if node('x_emergency') is None:tap('x_home')
            tap('x_more');tap('x_tools');tap('privacy_settings');tap('privacy_reset')
            assert client.request('CLICK android:id/button1').get('clicked')=='true'
            wait('x_emergency');QaAvdGuard(adb).acquire_clean();result['cleanup']='PASS'
        except Exception:
            result['status']='FAIL';result['cleanup']='FAIL'
            print('QA data from this run needs guarded cleanup; no unrelated target was changed.',flush=True)
        client.close();restore_microphone();os.environ.pop('LIFELINK_TEST_PIN',None)
        (out/'health-results.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
    if result['status']!='PASS':raise RuntimeError('Health packet verification incomplete')

if __name__=='__main__':main()
