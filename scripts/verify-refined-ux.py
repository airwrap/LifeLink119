"""Public QA-only screens: bounded choices, retained question, critical stop and Hyundai home."""
import argparse, json, subprocess, time, xml.etree.ElementTree as ET
from pathlib import Path
from native_window_client import WindowClient
from qa_avd_guard import QaAvdGuard, android_sdk
from ui_snapshot_client import verify_installed_apk

ROOT = Path(__file__).resolve().parents[1]

def main():
    p = argparse.ArgumentParser(); p.add_argument('--serial', required=True); args = p.parse_args()
    sdk = android_sdk(); adb = [str(sdk/'platform-tools/adb.exe'), '-s', args.serial]
    guard = QaAvdGuard(adb).acquire_clean()
    restore_microphone = guard.microphone_for_test()
    digest = verify_installed_apk(adb, ROOT/'app/build/outputs/apk/debug/app-debug.apk', sdk)
    out = ROOT/'verification/refined-ux'; out.mkdir(parents=True, exist_ok=True)
    client = WindowClient(adb); checks=[]
    result = dict(status='FAIL', apkSha256=digest, checks=checks, actualCalls=0, realPatientData=False)
    def node(k): return ET.fromstring(client.node(k)).find('.//node')
    def text(k): return ' '.join(n.get('text','') for n in ET.fromstring(client.node(k)).iter('node'))
    def tap(k):
        until = time.monotonic()+3
        while time.monotonic()<until:
            if client.click(k): return
            time.sleep(.2)
        for d in ['up','down']:
            for _ in range(18):
                if not client.scroll(d): break
                if client.click(k): return
        raise AssertionError('Missing '+k)
    def wait(k, contains='', seconds=15):
        until=time.monotonic()+seconds
        while time.monotonic()<until:
            if node(k) is not None and contains in text(k): return
            time.sleep(.2)
        raise AssertionError('Expected '+k+': '+contains)
    def mark(s): checks.append(s); print('PASS '+s, flush=True)
    def shot(name):
        if client.request('PING').get('secureRefused'): raise AssertionError('Protected capture refused')
        (out/(name+'.jpg')).write_bytes(client.frame())
        xml=client.dump(); (out/(name+'.xml')).write_text(xml, encoding='utf-8')
        assert not any(n.get('text-clipped')=='true' for n in ET.fromstring(xml).iter('node')), 'Clipped '+name
    def scenario():
        tap('x_es_demo'); tap('x_es_demo_illness')
    def manual():
        tap('x_es_ai'); wait('x_es_choices'); tap('x_es_choices')
    try:
        client.start(choose_setup=False)
        if node('x_ai_setup_allow') is not None: tap('x_ai_setup_allow')
        if node('x_emergency') is None: tap('x_home')
        shot('home')
        tap('x_emergency'); shot('call'); tap('x_es_entry'); scenario(); manual()
        wait('x_es_question','환자')
        assert all(node(k) is None for k in ['x_es_chest','x_es_bleed','x_es_fire','x_es_worse','x_es_skip'])
        assert all(node(k) is not None for k in ['x_es_yes','x_es_no','x_es_unknown'])
        shot('manual-consciousness')
        mark('Manual fallback shows one question and exactly three answer choices')
        tap('x_es_yes'); wait('x_es_question','숨'); tap('x_es_yes')
        wait('x_es_question','불이나'); tap('x_es_no'); wait('x_es_question','피가')
        tap('x_es_no'); wait('x_es_question','가슴'); shot('manual-symptom')
        tap('x_es_yes'); wait('x_es_question','나빠졌'); tap('x_es_yes')
        wait('x_es_guidance'); assert node('x_es_question') is None
        assert node('x_es_yes') is None; wait('x_es_retry'); shot('worsening')
        mark('Sequential answers record chest pain; worsening replaces questions with safety guidance')
        tap('x_back'); tap('x_es_packet'); tap('x_es_handover')
        wait('x_es_message','가슴 통증'); assert '이전보다 상태가 나빠졌' in text('x_es_message')
        shot('handover'); tap('x_back')
        mark('Handover retains multiple symptom statements with source labels')
        tap('x_es_progress'); shot('callback')
        assert all(node(k) is None for k in ['x_es_operator','x_es_callback','x_es_dispatch','x_es_reported'])
        tap('x_es_call_choices'); wait('x_es_call_question','통화 중')
        tap('x_es_call_no'); wait('x_es_call_question','다시 전화'); shot('callback-question')
        tap('x_es_call_yes'); wait('x_es_message','콜백'); assert node('x_es_call_question') is None
        tap('x_es_retry'); wait('x_es_message','실제 발신 없음')
        mark('Call-state screen has one voice entry; optional confirmation is sequential and retry remains available')
        time.sleep(1.3); tap('x_back'); scenario(); manual(); tap('x_es_yes'); tap('x_es_no')
        wait('x_es_guidance'); assert node('x_es_question') is None
        shot('breathing'); tap('x_back')
        mark('Absent normal breathing stops the symptom questionnaire immediately')
        tap('x_es_progress'); tap('x_es_call_choices'); tap('x_es_call_yes'); tap('x_es_ai')
        wait('x_es_message','통화 중에는')
        mark('User-confirmed operator connection prevents AI audio')
        result['status']='PASS'
    except Exception:
        if not client.request('PING').get('secureRefused'):
            (out/'failure-public.xml').write_text(client.dump(),encoding='utf-8')
            (out/'failure-public.jpg').write_bytes(client.frame())
        raise
    finally:
        client.close(); restore_microphone(); guard.verify_target()
        (out/'results.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
        subprocess.run(adb+['shell','am','force-stop','com.lifelink119.app'], capture_output=True)
        subprocess.run(adb+['shell','am','start','-n','com.lifelink119.app/.MainActivity'], capture_output=True)

if __name__ == '__main__': main()
