"""QA-only real Android TTS/playback -> bounded microphone lifecycle; no human speech or agency calls."""
import argparse
import json
from pathlib import Path
import subprocess
import time
import xml.etree.ElementTree as ET
from native_window_client import WindowClient
from qa_avd_guard import QaAvdGuard, android_sdk
from ui_snapshot_client import verify_installed_apk
from qa_console import QaConsole

ROOT=Path(__file__).resolve().parents[1]


def main():
    parser=argparse.ArgumentParser(); parser.add_argument('--serial',required=True); parser.add_argument('--external',action='store_true'); args=parser.parse_args()
    sdk=android_sdk(); adb=[str(sdk/'platform-tools/adb.exe'),'-s',args.serial]
    def run(*args): return subprocess.check_output(adb+list(args),timeout=30).decode().strip()
    guard=QaAvdGuard(adb).acquire_clean()
    digest=verify_installed_apk(adb,ROOT/'app/build/outputs/apk/debug/app-debug.apk',sdk)
    restore_microphone=guard.microphone_for_test()
    fixture=QaConsole(adb,external=True) if args.external else None
    client=WindowClient(adb)
    out=ROOT/'verification/voice-companion'/('external' if args.external else 'local'); out.mkdir(parents=True,exist_ok=True)
    checks=[]; result=dict(status='FAIL',apkSha256=digest,checks=checks,actualCalls=0,humanVoiceRecorded=False)
    def tap(key):
        if client.click(key): return
        for direction in ['down','up']:
            for _ in range(20):
                if not client.scroll(direction): break
                if client.click(key): return
        raise RuntimeError('Missing '+key)
    def texts(key): return ' '.join(n.get('text','') for n in ET.fromstring(client.node(key)).iter('node'))
    def wait(key,text,seconds=15):
        until=time.monotonic()+seconds
        while time.monotonic()<until:
            value=texts(key)
            if text in value: return value
            time.sleep(.15)
        raise RuntimeError('Expected '+key+': '+text+'; actual='+value+'; message='+texts('x_es_message'))
    def mark(text): checks.append(text); print('PASS '+text,flush=True)
    def shot(name):
        if client.request('PING').get('secureRefused'): raise RuntimeError('Protected capture refused')
        (out/(name+'.jpg')).write_bytes(client.frame())
    try:
        if fixture: fixture.__enter__()
        client.start(choose_setup=False)
        if not client.click('x_ai_setup_allow' if args.external else 'x_ai_setup_local'):
            tap('x_more'); tap('x_ai_preferences')
            tap('x_ai_setup_allow' if args.external else 'x_ai_setup_local')
        tap('x_emergency'); tap('x_es_entry')
        tap('x_es_demo'); tap('x_es_demo_illness'); time.sleep(1.5); shot('01-start')

        tap('x_es_ai'); wait('x_es_voice_status','질문을 읽고')
        mark('One start begins spoken question; answer controls are collapsed by default')
        if args.external:
            assert any(r['phraseId'] and r['fallback'] is False for r in fixture.responses)
            question = texts('x_es_question')
            assert question in {'제가 함께 상황을 확인하겠습니다. 환자는 의식이 있나요?', '환자를 불렀을 때 반응하나요?'}
            mark('Real configured LLM chooses approved question through app and loopback proxy')
        wait('x_es_voice_status','듣고 있습니다',30)
        mark('TTS completion automatically starts on-device microphone recognition')
        # No host microphone is connected on this QA machine. Silence must not become a yes/no.
        wait('x_es_voice_status','마이크를 쉬고',55)
        mark('Two unanswered turns pause; no invented patient observation')
        tap('x_back'); tap('x_es_packet'); tap('x_es_handover')
        wait('x_es_message','환자 상태 입력 없음')
        tap('x_back'); tap('x_es_ai')
        wait('x_es_voice_status','질문을 읽고'); tap('x_es_mute')
        wait('x_es_message','마이크와 음성을 멈췄습니다')
        mark('Visible stop cancels playback and recognition immediately')
        tap('x_back'); tap('x_es_progress'); tap('x_es_call_choices'); tap('x_es_call_yes'); tap('x_es_ai')
        wait('x_es_message','통화 중에는')
        mark('Confirmed operator call blocks voice start; emergency retry remains available')
        result['status']='PASS'
    finally:
        try:
            client.close()
            run('shell','am','force-stop','com.lifelink119.app')
            guard.before_cleanup(); guard.acquire_clean()
        finally:
            if fixture:
                fixture.close()
                result['proxyResponses']=fixture.responses
            restore_microphone()
            (out/'ui-results.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')


if __name__=='__main__': main()
