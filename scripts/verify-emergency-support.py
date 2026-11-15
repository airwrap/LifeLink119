"""Dedicated QA only. Synthetic scenarios, no real calls, profiles, microphone or medical PIN.

Uses the installed native UI; preserves the original ADB reverse and existing console.
"""
import argparse
import json
from pathlib import Path
import subprocess
import time
import urllib.request
import xml.etree.ElementTree as ET
from native_window_client import WindowClient
from qa_avd_guard import QaAvdGuard, android_sdk
from ui_snapshot_client import verify_installed_apk
from qa_console import QaConsole

ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser(); parser.add_argument('--serial', required=True)
    args = parser.parse_args()
    sdk = android_sdk(); adb = [str(sdk/'platform-tools/adb.exe'), '-s', args.serial]
    guard = QaAvdGuard(adb).acquire_clean()
    restore_microphone = guard.microphone_for_test()
    digest = verify_installed_apk(adb, ROOT/'app/build/outputs/apk/debug/app-debug.apk', sdk)
    out = ROOT/'verification/emergency-support'; out.mkdir(parents=True, exist_ok=True)
    checks = []; result = {'status': 'FAIL', 'apkSha256': digest, 'checks': checks, 'actualCalls': 0, 'privateDataCreated': False}
    # Fresh production server behind a bounded native-only QA relay. Existing :8781
    # and production Host/Origin validation are preserved; no external model is used.
    fixture = QaConsole(adb).__enter__()
    server = fixture.server
    responses = []
    original_send = server.RequestHandlerClass.send_data
    def traced_send(handler, status, content, *args):
        responses.append((status, content.get('code', '') if isinstance(content, dict) else 'static'))
        return original_send(handler, status, content, *args)
    server.RequestHandlerClass.send_data = traced_send
    def run(*cmd): return subprocess.run(adb+list(cmd), check=True, capture_output=True, timeout=20).stdout.decode().strip()
    client = WindowClient(adb)
    def node(key): return ET.fromstring(client.node(key)).find('.//node')
    def tap(key):
        if client.click(key): time.sleep(.15); return
        for direction in ('down', 'up'):
            for _ in range(18):
                if not client.scroll(direction): break
                if client.click(key): time.sleep(.15); return
        raise AssertionError('Missing action '+key)
    def wait(key, text='', seconds=10):
        until = time.monotonic()+seconds
        while time.monotonic()<until:
            n=node(key)
            if n is None:
                for direction in ('up', 'down'):
                    for _ in range(18):
                        if not client.scroll(direction): break
                        n=node(key)
                        if n is not None: break
                    if n is not None: break
            if n is not None and text in n.get('text',''): return n
            time.sleep(.15)
        detail = n.get('text', '') if n is not None and key == 'x_es_message' else '[not visible]'
        raise AssertionError('Missing expected state '+key+'; status='+detail+'; http='+repr(responses))
    def mark(text): checks.append(text); print('PASS '+text, flush=True)
    def shot(name):
        if client.request('PING').get('secureRefused'): raise AssertionError('Protected capture refused')
        (out/(name+'.jpg')).write_bytes(client.frame())
        xml=client.dump(); (out/(name+'.xml')).write_text(xml,encoding='utf-8')
        if any(n.get('text-clipped')=='true' for n in ET.fromstring(xml).iter('node')): raise AssertionError('Clipped text '+name)
    def overview():
        # Public outer back action returns to the group's overview before leaving the group.
        tap('x_back')
    def scenario(key):
        tap('x_es_demo'); tap(key); time.sleep(.4)
    def packet():
        tap('x_es_packet'); tap('x_es_details'); return wait('x_es_summary').get('text','')
    try:
        client.start(); wait('x_emergency'); tap('x_emergency'); wait('x_es_entry'); tap('x_es_entry')
        # SDK must not be queried before opt-in; no endpoint can convert missing data to success.
        tap('x_es_options'); check=wait('x_es_collect'); assert check.get('checked')=='false'
        overview(); mark('Vehicle collection remains opt-in; emergency retry remains accessible')
        scenario('x_es_demo_airbag'); text=packet()
        assert '전개' in text and '시뮬레이션' in text and '실제 기관 전송 없음' in text
        shot('01-airbag-summary'); overview(); mark('Scenario 1: synthetic airbag and location automatically populate packet')
        tap('x_es_demo'); tap('x_es_pc_consent'); tap('x_es_publish'); wait('x_es_message','게시 완료')
        data=json.load(urllib.request.urlopen(f'http://127.0.0.1:{server.server_port}/api/demo/emergency/snapshots'))
        assert data['items'][0]['scenario']=='AIRBAG'; assert data['items'][0]['syntheticOnly']
        tap('x_es_revoke'); wait('x_es_message','철회'); overview(); mark('Synthetic PC roundtrip and revocation; real data cannot enter PC payload')
        scenario('x_es_demo_collision'); text=packet()
        assert '미전개' in text and '안전 여부 판단 불가' in text and '모의 충돌 이벤트' in text
        overview(); mark('Scenario 2: no airbag does not imply no accident or no injury')
        scenario('x_es_demo_highway'); assert wait('x_es_packet').get('enabled')=='false'; wait('x_es_retry')
        shot('02-highway-minimal'); tap('x_es_stopped'); text=packet(); assert '동쪽' in text and '가상 고속도로' in text
        overview(); mark('Scenario 3: minimal moving UI, details only after synthetic stop plus platform permission')
        scenario('x_es_demo_illness'); tap('x_es_progress'); tap('x_es_call_choices'); tap('x_es_call_no')
        tap('x_es_call_yes'); wait('x_es_message','콜백'); shot('03-callback')
        tap('x_es_retry'); wait('x_es_message','실제 발신 없음'); overview(); text=packet(); assert '실제 전화 상태 미연동' in text
        overview(); mark('Scenario 4: delay/callback confirmation and retry retain the incident summary')
        tap('x_es_progress'); tap('x_es_call_choices'); tap('x_es_call_no'); tap('x_es_call_no'); tap('x_es_call_yes'); tap('x_es_ai'); wait('x_es_choices'); tap('x_es_choices')
        wait('x_es_question','의식'); tap('x_es_yes'); wait('x_es_question','숨'); tap('x_es_yes'); tap('x_es_no'); tap('x_es_no'); tap('x_es_yes'); tap('x_es_yes')
        wait('x_es_guidance'); shot('04-worsening-guidance'); overview(); text=packet()
        assert '이전보다 상태가 나빠졌다고 응답' in text and '시뮬레이션' in text
        overview(); mark('Scenario 5: dispatch-wait report, one-question flow, worsening escalation and handover summary')
        scenario('x_es_demo_illness'); tap('x_es_ai'); wait('x_es_choices'); tap('x_es_choices'); overview(); tap('x_es_options')

        if wait('x_es_llm_consent').get('checked') != 'true': tap('x_es_llm_consent')
        tap('x_es_llm'); wait('x_es_llm_state','검증된 쉬운 문장')
        overview(); tap('x_es_ai'); wait('x_es_question','환자를 불렀을 때')
        overview(); tap('x_es_options'); mark('Loopback LLM proxy accepts consented step codes and returns only an approved phrase')
        tap('x_es_llm_fail')
        wait('x_es_llm_state','오프라인'); overview(); tap('x_es_ai'); wait('x_es_question','의식'); wait('x_es_retry')
        shot('05-offline-companion'); mark('Scenario 6: failed LLM keeps offline guidance and emergency retry available')
        overview(); tap('x_es_progress'); tap('x_es_call_choices'); tap('x_es_call_yes'); tap('x_es_ai')
        wait('x_es_message','통화 중에는')
        overview(); mark('Operator-confirmed call blocks automatic TTS/microphone without changing call status')
        scenario('x_es_demo_failure'); text=packet(); assert '확인 불가' in text; assert '37.5665' not in text
        overview(); mark('SDK/location unavailable scenario contains no invented values')
        # Clipboard requires deliberate consent, and no real SMS/phone intent is ever triggered.
        tap('x_es_packet'); assert wait('x_es_copy').get('enabled')=='false'
        overview(); mark('Summary export is gated by separate explicit consent')
        result['status']='PASS'
    finally:
        try:
            if node('x_es_return') is not None: tap('x_es_return')
            if node('x_end') is not None: tap('x_end')
            if node('x_finish_home') is not None: tap('x_finish_home')
        except Exception: pass
        client.close(); restore_microphone(); guard.verify_target()
        fixture.close()
        (out/'results.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
    print('UI verification PASS',flush=True)


if __name__=='__main__': main()
