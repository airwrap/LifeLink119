"""Non-Pleos dedicated QA AVD: unsupported SDKs never fabricate a successful vehicle fix."""
import argparse
import json
from pathlib import Path
import subprocess
import time
import xml.etree.ElementTree as ET
from qa_avd_guard import QaAvdGuard, android_sdk
from ui_snapshot_client import SnapshotClient, verify_installed_apk

ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser(); parser.add_argument('--serial', required=True)
    args = parser.parse_args(); sdk = android_sdk()
    adb = [str(sdk/'platform-tools/adb.exe'), '-s', args.serial]
    guard = QaAvdGuard(adb).acquire_clean()
    restore_microphone = guard.microphone_for_test()
    features = subprocess.check_output(adb+['shell','pm','list','features'],timeout=20).decode()
    if 'android.hardware.type.automotive' in features: raise RuntimeError('This check requires a non-automotive QA AVD')
    digest = verify_installed_apk(adb, ROOT/'app/build/outputs/apk/debug/app-debug.apk', sdk)
    subprocess.run(adb+['shell','am','force-stop','com.lifelink119.app'],check=True,capture_output=True)
    subprocess.run(adb+['shell','am','start','-W','-n','com.lifelink119.app/.MainActivity'],check=True,capture_output=True)
    c = SnapshotClient(adb); result = {'status':'FAIL','apkSha256':digest,'actualCalls':0,'privateDataCreated':False}
    def node(key): return ET.fromstring(c.node(key)).find('.//node')
    def find(key):
        n=node(key)
        if n is not None: return n
        for direction in ('up','down'):
            for _ in range(20):
                if not c.scroll(direction): break
                n=node(key)
                if n is not None: return n
        return None
    def tap(key):
        if find(key) is None or not c.click(key): raise AssertionError('Missing SDK failure UI '+key)
    try:
        c.start(); tap('x_emergency'); tap('x_es_entry'); tap('x_es_options'); tap('x_es_collect')
        time.sleep(7)
        # Unknown OEM permissions may show a platform denial; never infer a successful read.
        for key in ('android:id/button1','com.android.permissioncontroller:id/permission_deny_button'):
            c.request('CLICK '+key)
        tap('x_back'); tap('x_es_packet'); tap('x_es_details'); n=find('x_es_summary')
        if n is None: raise AssertionError('No summary after unsupported SDK read')
        text=n.get('text','')
        assert '시뮬레이션' not in text and '차량 SDK 확인' not in text
        for field in ('운전석 도어','EV 배터리','에어백','위치','도로·지점'):
            line=next(v for v in text.splitlines() if v.startswith(field+':'))
            assert '확인 불가' in line or '지원되지 않음' in line
        tap('x_back'); tap('x_es_ai'); time.sleep(1.5); tap('x_es_choices'); assert '의식' in find('x_es_question').get('text','')
        tap('x_es_retry'); assert '실제 발신 없음' in find('x_es_message').get('text','')
        result.update(status='PASS',checks=['Non-automotive install/start succeeds','All five SDK fields unavailable without synthetic fallback','Offline companion and retry remain available'])
        print('PASS standard Android: unavailable SDKs, offline guidance and emergency retry',flush=True)
    finally:
        c.close();restore_microphone();guard.verify_target();QaAvdGuard(adb).acquire_clean()
        subprocess.run(adb+['shell','am','force-stop','com.lifelink119.app'],check=True,capture_output=True)
        out=ROOT/'verification/emergency-support';out.mkdir(parents=True,exist_ok=True)
        (out/'unsupported-sdk-results.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')


if __name__ == '__main__': main()
