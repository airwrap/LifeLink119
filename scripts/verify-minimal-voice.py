"""Dedicated QA: persisted choice, one-action start and fully synthetic spoken demonstration."""
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

ROOT = Path(__file__).resolve().parents[1]


def main():
    p = argparse.ArgumentParser(); p.add_argument('--serial', required=True); args = p.parse_args()
    sdk = android_sdk(); adb = [str(sdk/'platform-tools/adb.exe'), '-s', args.serial]
    guard = QaAvdGuard(adb).acquire_clean()
    digest = verify_installed_apk(adb, ROOT/'app/build/outputs/apk/debug/app-debug.apk', sdk)
    def run(*a): return subprocess.check_output(adb+list(a), timeout=30).decode().strip()
    restore_microphone = guard.microphone_for_test()
    fixture = QaConsole(adb, external=True)
    client = WindowClient(adb)
    out = ROOT/'verification/minimal-voice'; out.mkdir(parents=True, exist_ok=True)
    checks = []; result = dict(status='FAIL', apkSha256=digest, checks=checks, actualCalls=0,
        humanVoiceRecorded=False, scriptedAnswers=True)
    def node(key): return ET.fromstring(client.node(key)).find('.//node')
    def text(key): return ' '.join(n.get('text','') for n in ET.fromstring(client.node(key)).iter('node'))
    def tap(key):
        if client.click(key): return
        for direction in ['up','down']:
            for _ in range(20):
                if not client.scroll(direction): break
                if client.click(key): return
        raise AssertionError('Missing '+key)
    def wait(key, contains='', seconds=20):
        until = time.monotonic()+seconds
        while time.monotonic()<until:
            if node(key) is not None and contains in text(key): return text(key)
            time.sleep(.2)
        raise AssertionError('Expected '+key+': '+contains+'; '+text('x_es_message'))
    def mark(value): checks.append(value); print('PASS '+value,flush=True)
    def shot(name):
        if client.request('PING').get('secureRefused'):
            result['protectedCaptureSkipped'] = True
            return
        (out/(name+'.jpg')).write_bytes(client.frame())
    def restart():
        client.close()
        run('shell','am','force-stop','com.lifelink119.app')
        run('shell','am','start','-W','-n','com.lifelink119.app/.MainActivity')
        client.start(choose_setup=False)
    def settings():
        if node('x_emergency') is None: tap('x_home')
        tap('x_more'); tap('x_ai_preferences')
    try:
        fixture.__enter__()
        client.start(choose_setup=False)
        first = node('x_ai_setup_allow') is not None
        result['firstRunScreenObserved'] = first
        if not first: settings()
        shot('01-onboarding'); tap('x_ai_setup_allow'); wait('x_emergency')
        restart(); wait('x_emergency'); assert node('x_ai_setup_allow') is None
        mark('One visible external-AI choice survives application restart; no repeated onboarding')
        tap('x_emergency'); tap('x_ai_quick')
        wait('x_es_voice_status','질문을 읽고')
        assert node('x_es_llm_consent') is None and node('x_es_start_ai') is None
        mark('One action from the 119 screen starts spoken questions without consent/start interstitials')
        # Check while the response is active, before silence legitimately clears it.
        # Frame capture may outlast a short microphone turn on slow emulators.
        provider_seen = False
        for _ in range(12):
            all_text = ' '.join(n.get('text','') for n in ET.fromstring(client.dump()).iter('node'))
            if '검증된 쉬운 문장 사용' in all_text:
                provider_seen = True; break
            if not client.scroll('down'): time.sleep(.3)
        assert provider_seen, 'Approved external response was not observed in the rendered UI'
        assert any(r['phraseId'] and r['fallback'] is False for r in fixture.responses)
        tap('x_es_mute')
        mark('Saved permission reaches the real LLM; voice can still be stopped immediately')
        tap('x_home'); tap('x_more'); tap('x_ai_auto_demo')
        wait('x_ai_demo_badge','합성 응답'); shot('03-auto-demo')
        wait('x_es_voice_start',seconds=100)
        wait('x_es_demo_handover','가슴 통증')
        shot('04-demo-completed')
        tap('x_back'); tap('x_es_packet'); tap('x_es_handover')
        summary = wait('x_es_message','가슴 통증')
        assert '시뮬레이션' in summary
        assert '정상 호흡이라고 응답' in summary
        assert '이전보다 상태가 나빠졌다고 응답' not in summary
        mark('Six synthetic voice answers advance automatically and produce a clearly simulated handover')
        settings(); tap('x_ai_setup_local'); restart(); wait('x_emergency')
        assert node('x_ai_setup_allow') is None
        tap('x_more'); tap('x_ai_preferences'); wait('x_ai_preference_status','기본 음성 안내')
        mark('Revocation is persistent; declining external AI does not repeat onboarding or disable local voice')
        tap('x_ai_setup_allow'); wait('x_emergency')
        tap('x_more'); tap('x_ai_auto_demo'); wait('x_ai_demo_badge'); tap('x_es_mute')
        wait('x_es_message','마이크와 음성을 멈췄습니다')
        tap('x_back'); tap('x_es_progress'); tap('x_es_call_choices'); tap('x_es_call_yes'); tap('x_es_ai')
        wait('x_es_message','통화 중에는')
        mark('Automatic demo stops on request; confirmed operator call still blocks voice')
        result['status'] = 'PASS'
    finally:
        try:
            client.close(); guard.verify_target()
            run('shell','am','force-stop','com.lifelink119.app')
            guard.acquire_clean()
        finally:
            fixture.close(); restore_microphone()
            result['proxyResponses'] = fixture.responses
            (out/'results.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')


if __name__ == '__main__': main()
