"""Real proxy connection failure on QA: offline question and retry must survive."""
import argparse
import json
from pathlib import Path
import socket
import subprocess
import time
import xml.etree.ElementTree as ET
from native_window_client import WindowClient
from qa_avd_guard import QaAvdGuard, android_sdk
from ui_snapshot_client import verify_installed_apk

ROOT = Path(__file__).resolve().parents[1]


def main():
    p = argparse.ArgumentParser(); p.add_argument('--serial', required=True); args = p.parse_args()
    sdk = android_sdk(); adb = [str(sdk/'platform-tools/adb.exe'), '-s', args.serial]
    guard = QaAvdGuard(adb).acquire_clean()
    digest = verify_installed_apk(adb, ROOT/'app/build/outputs/apk/debug/app-debug.apk', sdk)
    restore_microphone = guard.microphone_for_test()
    def run(*args): return subprocess.check_output(adb+list(args), timeout=20).decode().strip()
    previous = next((r.split()[-1] for r in run('reverse', '--list').splitlines()
                     if 'tcp:8781' in r.split()[:-1]), None)
    # Reserve an unused local port without listening. No user's server is touched.
    reserved = socket.socket(); reserved.bind(('127.0.0.1', 0))
    c = WindowClient(adb)
    result = dict(status='FAIL', apkSha256=digest, checks=[], actualCalls=0)
    mapped = False
    def tap(key):
        if c.click(key): return
        for direction in ('up', 'down'):
            for _ in range(15):
                if not c.scroll(direction): break
                if c.click(key): return
        raise AssertionError('Missing '+key)
    def text(key): return ' '.join(n.get('text', '') for n in ET.fromstring(c.node(key)).iter('node'))
    def wait(key, expected, seconds=20):
        deadline = time.monotonic()+seconds
        while time.monotonic() < deadline:
            if expected in text(key): return
            time.sleep(.1)
        raise AssertionError('Expected '+key+': '+expected)
    def mark(value): result['checks'].append(value); print('PASS '+value, flush=True)
    try:
        run('reverse', 'tcp:8781', 'tcp:'+str(reserved.getsockname()[1])); mapped = True
        c.start(choose_setup=False)
        if not c.click('x_ai_setup_allow'):
            tap('x_more'); tap('x_ai_preferences'); tap('x_ai_setup_allow')
        tap('x_emergency'); started = time.monotonic(); tap('x_ai_quick')
        wait('x_es_voice_status', '질문을 읽고')
        result['fallbackSeconds'] = round(time.monotonic()-started, 2)
        assert '의식' in text('x_es_question')
        observed = False
        for _ in range(12):
            values = ' '.join(n.get('text', '') for n in ET.fromstring(c.dump()).iter('node'))
            if '외부 응답 없음/검증 실패' in values: observed = True; break
            c.scroll('down')
        assert observed, 'Offline fallback was not displayed'
        mark('Unavailable real proxy falls back to fixed question without stopping the app')
        tap('x_es_retry'); wait('x_es_message', '실제 발신 없음')
        mark('119 retry remains available after actual network connection failure')
        result['status'] = 'PASS'
    finally:
        try:
            c.close(); guard.verify_target()
            run('shell', 'am', 'force-stop', 'com.lifelink119.app')
            if mapped:
                run('reverse', '--remove', 'tcp:8781')
                if previous: run('reverse', 'tcp:8781', previous)
            result['tunnelRestored'] = True
        finally:
            reserved.close(); restore_microphone()
            out = ROOT/'verification/voice-companion'; out.mkdir(parents=True, exist_ok=True)
            (out/'network-failure.json').write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding='utf-8')


if __name__ == '__main__': main()
