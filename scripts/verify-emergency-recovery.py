"""QA-only SDK failures and encrypted restart recovery; no private screenshots or real calls."""
import argparse
import json
import os
from pathlib import Path
import secrets
import subprocess
import time
import xml.etree.ElementTree as ET
from qa_avd_guard import QaAvdGuard, android_sdk
from ui_snapshot_client import SnapshotClient, verify_installed_apk

ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser(); parser.add_argument('--serial', required=True)
    args = parser.parse_args()
    sdk = android_sdk(); adb = [str(sdk/'platform-tools/adb.exe'), '-s', args.serial]
    guard = QaAvdGuard(adb).acquire_clean()
    restore_microphone = guard.microphone_for_test(); user = guard.verify_target()
    digest = verify_installed_apk(adb, ROOT/'app/build/outputs/apk/debug/app-debug.apk', sdk)
    os.environ['LIFELINK_TEST_PIN'] = ''.join(str(secrets.randbelow(10)) for _ in range(8))
    client = SnapshotClient(adb, auto_auth=True)
    result = {'status': 'FAIL', 'apkSha256': digest, 'checks': [], 'actualCalls': 0, 'sdk': {}}
    out = ROOT/'verification/emergency-support'; out.mkdir(parents=True, exist_ok=True)
    def run(*cmd): return subprocess.check_output(adb+list(cmd), timeout=30).decode().strip()
    def node(key): return ET.fromstring(client.node(key)).find('.//node')
    def find(key):
        n = node(key)
        if n is not None: return n
        for direction in ('up', 'down'):
            for _ in range(18):
                if not client.scroll(direction): break
                n = node(key)
                if n is not None: return n
        return None
    def tap(key):
        if find(key) is None or not client.click(key): raise AssertionError('Missing action '+key)
    def wait(key, expected='', seconds=20):
        end = time.monotonic()+seconds
        while time.monotonic()<end:
            n = find(key)
            if n is not None and expected in n.get('text', ''): return n
            time.sleep(.2)
        raise AssertionError('Missing expected private control '+key+'; values redacted')
    def mark(name): result['checks'].append(name); print('PASS '+name, flush=True)
    def overview(): tap('x_back')
    def open_support(): tap('x_emergency'); wait('x_es_entry'); tap('x_es_entry')
    def read_prefs(name):
        p = subprocess.run(adb+['shell','run-as','com.lifelink119.app','--user',user,'cat','shared_prefs/'+name+'.xml'], capture_output=True, timeout=20)
        if p.returncode and b'No such file' not in p.stderr: raise AssertionError('QA preference read failed')
        return p.stdout.decode() if p.returncode == 0 else '<map/>'
    def launch():
        run('shell','am','force-stop','com.lifelink119.app')
        run('shell','am','start','-W','-n','com.lifelink119.app/.MainActivity')
    try:
        launch(); client.start(); wait('x_emergency'); open_support()
        # Exercise documented runtime permission only on this dedicated, initially clean QA device.
        # No coordinates or SDK values are emitted in the test report.
        permission = subprocess.run(adb+['shell','pm','grant','--user',user,'com.lifelink119.app','pleos.car.permission.FUSED_LOCATION'], capture_output=True, timeout=20)
        result['sdk']['locationPermissionGranted'] = permission.returncode == 0
        tap('x_es_options'); tap('x_es_collect'); time.sleep(1)
        # Missing permission on unsupported OS must remain a safe unavailable result.
        for key in ('com.android.permissioncontroller:id/permission_allow_button', 'com.android.permissioncontroller:id/permission_allow_foreground_only_button'):
            client.request('CLICK '+key)
        time.sleep(7); overview(); tap('x_es_packet'); tap('x_es_details'); text = wait('x_es_summary', 'Pleos Vehicle', seconds=25).get('text','')
        for field in ('운전석 도어', 'EV 배터리', '에어백', '위치', '도로·지점'):
            line = next((v for v in text.splitlines() if v.startswith(field+':')), '')
            result['sdk'][field] = next((v for v in ('지원되지 않음','확인 불가','시뮬레이션','실제 차량에서 확인') if v in line), 'label not found')
        assert '실제 기관 전송 없음' in text and '신호 없음/미전개로 안전 여부 판단 불가' in text
        mark('Documented SDK collection completes without fabricating unsupported signals; output stores statuses only')
        overview(); tap('x_es_options'); tap('x_es_collect'); overview()
        tap('x_es_ai'); wait('x_es_choices'); tap('x_es_choices'); tap('x_es_yes'); tap('x_es_yes'); tap('x_es_no'); tap('x_es_no'); tap('x_es_yes')
        overview(); tap('x_es_options'); tap('x_es_save'); client.authenticate_if_needed()
        wait('x_es_message', '암호화 보관')
        raw = read_prefs('lifelink_emergency_checkpoint')
        assert any(n.get('name') == 'cipher' for n in ET.fromstring(raw))
        assert all(value not in raw for value in ('CONSCIOUS_YES','CHEST_PAIN','latitude','longitude','DEMO-'))
        mark('Explicit authentication saves encrypted answers/timestamps without plaintext or location')
        launch(); wait('x_emergency'); open_support(); tap('x_es_options')
        assert wait('x_es_restore').get('enabled') == 'true'
        tap('x_es_restore'); client.authenticate_if_needed(); time.sleep(.5)
        overview(); tap('x_es_packet'); tap('x_es_details'); text = wait('x_es_summary','가슴 통증').get('text','')
        assert '연결 여부 확인되지 않음' in text and '실제 전화 상태 미연동' in text
        overview(); wait('x_es_ai')
        mark('Restart restores authenticated observations; connection remains unknown and AI/voice do not auto-resume')
        tap('x_home'); tap('x_more'); tap('x_resume'); tap('x_es_entry'); wait('x_es_unlock'); wait('x_es_retry')
        assert node('x_es_summary') is None
        tap('x_es_unlock'); client.authenticate_if_needed(); wait('x_es_packet')
        mark('Home locks restored observations while emergency retry stays available; authentication restores access')
        # Explicit background transition must also lock; no screenshot of protected state is taken.
        run('shell','input','keyevent','3'); time.sleep(.3)
        run('shell','am','start','-W','-n','com.lifelink119.app/.MainActivity')
        wait('x_es_unlock'); wait('x_es_retry')
        mark('Backgrounding masks patient observations and leaves emergency access available')
        result['status'] = 'PASS'
    finally:
        try:
            guard.before_cleanup()
            if node('x_emergency') is None: tap('x_home')
            tap('x_more'); tap('x_tools'); tap('privacy_settings'); tap('privacy_reset')
            if client.request('CLICK android:id/button1').get('clicked') != 'true': raise AssertionError('QA cleanup confirmation missing')
            wait('x_emergency')
            QaAvdGuard(adb).acquire_clean()
            result['cleanup'] = 'PASS'
        except Exception:
            result['cleanup'] = 'FAIL'; result['status'] = 'FAIL'
            print('QA cleanup did not complete; this run owns the synthetic data, retain the guard before retrying cleanup.', flush=True)
        client.close(); restore_microphone(); os.environ.pop('LIFELINK_TEST_PIN', None)
        (out/'recovery-results.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
    if result['status'] != 'PASS': raise RuntimeError('Recovery verification incomplete')


if __name__ == '__main__': main()
