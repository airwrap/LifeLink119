"""Read-only public UI regression on an already-installed signed development release.

Requires a dedicated QA AVD and its separately release-signed WindowInstrumentation.
Does not install, reset, authenticate, send a real call or change stored AI consent.
"""
import argparse
import json
from pathlib import Path
import subprocess
import time
import xml.etree.ElementTree as ET

from native_window_client import WindowClient
from qa_avd_guard import QaAvdGuard, android_sdk
from ui_snapshot_client import verify_installed_apk

ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--serial', required=True)
    parser.add_argument('--apk', type=Path, required=True)
    args = parser.parse_args()
    sdk = android_sdk()
    adb = [str(sdk/'platform-tools/adb.exe'), '-s', args.serial]
    QaAvdGuard(adb).verify_target()
    digest = verify_installed_apk(adb, args.apk, sdk)
    metadata = subprocess.check_output([str(sdk/'build-tools/35.0.0/aapt.exe'), 'dump',
                                        'badging', str(args.apk)], timeout=20).decode()
    if 'application-debuggable' in metadata:
        raise RuntimeError('This smoke test requires a non-debuggable release')
    subprocess.run([str(sdk/'build-tools/35.0.0/apksigner.bat'), 'verify', str(args.apk)],
                   check=True, capture_output=True, timeout=30)
    client = WindowClient(adb)
    out = ROOT/'verification/current-release'
    out.mkdir(parents=True, exist_ok=True)
    result = dict(status='FAIL', apkSha256=digest, debuggable=False, checks=[], actualCalls=0)

    def text(key):
        return ' '.join(n.get('text', '') for n in ET.fromstring(client.node(key)).iter('node'))

    def tap(key):
        if client.click(key): return
        for direction in ('up', 'down'):
            for _ in range(20):
                if not client.scroll(direction): break
                if client.click(key): return
        raise AssertionError('Missing '+key)

    def wait(key, value='', seconds=12):
        deadline = time.monotonic()+seconds
        while time.monotonic() < deadline:
            xml = ET.fromstring(client.node(key))
            if xml.find('.//node') is not None and value in text(key): return
            time.sleep(.2)
        raise AssertionError('Expected '+key+': '+value)

    def mark(message):
        result['checks'].append(message); print('PASS '+message, flush=True)

    try:
        client.start(choose_setup=False)
        # Only the first-run prompt on this clean QA installation is answered.
        client.click('x_ai_setup_local')
        wait('x_emergency')
        mark('Signed, non-debuggable release opens the current home')
        for key in ('x_emergency', 'x_medical', 'x_109', 'x_home_112'):
            tap(key); wait('x_session_status', '가상 상담원 응답')
            tap('x_end'); wait('x_finished'); tap('x_finish_home')
            mark(key+': one-action mock connection and end')
        tap('x_more'); tap('x_ai_auto_demo')
        wait('x_ai_demo_badge', '합성 응답')
        wait('x_es_voice_start', seconds=100)
        wait('x_es_demo_handover', '가슴 통증')
        mark('Offline automatic voice demonstration completes with synthetic handover')
        tap('x_home')
        result['status'] = 'PASS'
    finally:
        client.close()
        (out/'results.json').write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding='utf-8')


if __name__ == '__main__': main()
