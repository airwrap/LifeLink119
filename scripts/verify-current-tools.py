"""Synthetic-only end-to-end checks on a guarded current debug QA AVD.

Protected routes use a disposable test PIN; no private view text is persisted.
Only data created after acquiring the clean-device guard is reset on exit.
"""
import argparse
import json
import os
import secrets
from pathlib import Path
import subprocess
import time
import xml.etree.ElementTree as ET

from qa_avd_guard import QaAvdGuard, android_sdk
from ui_snapshot_client import SnapshotClient, verify_installed_apk

ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--serial', required=True)
    args = parser.parse_args()
    sdk = android_sdk()
    adb = [str(sdk/'platform-tools/adb.exe'), '-s', args.serial]
    guard = QaAvdGuard(adb).acquire_clean()
    digest = verify_installed_apk(adb, ROOT/'app/build/outputs/apk/debug/app-debug.apk', sdk)
    previous_pin = os.environ.get('LIFELINK_TEST_PIN')
    os.environ['LIFELINK_TEST_PIN'] = ''.join(str(secrets.randbelow(10)) for _ in range(8))
    client = SnapshotClient(adb, auto_auth=True)
    out = ROOT/'verification/current-tools'
    out.mkdir(parents=True, exist_ok=True)
    result = dict(status='FAIL', apkSha256=digest, checks=[], failures=[],
                  syntheticDataOnly=True, actualCalls=0)

    def node(key):
        return ET.fromstring(client.node(key)).find('.//node')

    def text(key):
        return ' '.join(n.get('text', '') for n in ET.fromstring(client.node(key)).iter('node'))

    def seek(key):
        if node(key) is not None:
            return
        for direction in ('up', 'down'):
            for _ in range(24):
                if not client.scroll(direction):
                    break
                if node(key) is not None:
                    return
        raise AssertionError('Missing '+key)

    def tap(key):
        seek(key)
        if not client.click(key):
            raise AssertionError('Disabled/unavailable '+key)

    def wait(key, value='', seconds=12):
        seek(key)
        until = time.monotonic()+seconds
        while time.monotonic() < until:
            if node(key) is not None and value in text(key):
                return text(key)
            time.sleep(.2)
        raise AssertionError('Expected '+key+': '+value)

    def mark(message):
        result['checks'].append(message)
        print('PASS '+message, flush=True)

    def restart():
        client.close()
        subprocess.run(adb+['shell', 'am', 'force-stop', 'com.lifelink119.app'], check=True,
                       capture_output=True, timeout=20)
        subprocess.run(adb+['shell', 'am', 'start', '-W', '-n', 'com.lifelink119.app/.MainActivity'],
                       check=True, capture_output=True, timeout=20)
        client.start()

    def home():
        if node('x_emergency') is None:
            tap('x_home')

    def tools():
        home(); tap('x_more'); tap('x_tools')

    def case(case_id):
        tools(); tap('x_gallery'); tap('x_case_'+case_id.lower())
        wait('x_end')
        time.sleep(1.2)

    def support(action):
        tap('x_support'); tap('x_action_'+action)

    def session():
        home(); tap('x_more'); tap('x_resume')

    def check_group(name, fn):
        try:
            restart(); fn()
        except Exception as error:
            result['failures'].append(dict(group=name, error=str(error)))
            print('FAIL '+name+': '+str(error), flush=True)
            # Protected UI is inspected only by the dedicated synthetic-data helper.
            # Do not persist private view text or screenshots.

    def catalog():
        data = json.loads((ROOT/'app/src/main/assets/experience-catalog.json').read_text(encoding='utf-8'))
        for spec in data['scenarios']:
            case(spec['id'])
            assert '시연' in ' '.join(n.get('text', '') for n in ET.fromstring(client.dump()).iter('node'))
            tap('x_end'); wait('x_finished'); tap('x_finish_home')
            mark('Gallery '+spec['id']+': enter, simulated connection, end and clear')

    def manuals():
        home(); tap('x_more'); tap('x_manual')
        for topic in ('first', 'cpr', 'aed', 'bleeding', 'burn', 'seizure'):
            tap('manual_'+topic)
            assert wait('manual_title').strip()
            tap('back_button')
            mark('Offline manual '+topic+': open content and return')
        tap('x_home'); wait('x_emergency')

    def access_and_driver():
        tools(); tap('x_access')
        originals = {k: node(k).get('checked') for k in ('x_large_text', 'x_easy_text')}
        try:
            for key, original in originals.items():
                tap(key)
                assert node(key).get('checked') != original
            home(); wait('x_emergency')
            restart(); tools(); tap('x_access')
            for key, original in originals.items():
                assert node(key).get('checked') != original
            mark('Large text and easy wording persist across restart')
        finally:
            for key, original in originals.items():
                if node(key) is not None and node(key).get('checked') != original:
                    tap(key)
        tools(); tap('x_driver')
        tap('x_driver_1'); first = text('x_driver_status')
        tap('x_driver_2'); assert first != text('x_driver_status')
        tap('x_driver_clear'); wait('x_driver_status', '선택 없음')
        mark('Synthetic digital-key driver selection changes and clears')

    def text_and_sign():
        case('S08'); support('text')
        before = text('x_messages')
        tap('x_send_phrase'); seek('x_messages'); assert text('x_messages') != before
        sent = text('x_messages')
        tap('x_reply'); seek('x_messages'); assert text('x_messages') != sent
        mark('Phrase selection and simulated operator reply appear in conversation')
        session(); support('sign')
        tap('x_sign_connect'); wait('x_sign_status', '연결 시연')
        tap('x_sign_connect'); wait('x_sign_status', '요청 전')
        mark('Sign-interpreter mock requires consent and can be stopped')

    def videos():
        home(); tap('x_medical'); time.sleep(1.3); tap('x_guided_video')
        tap('x_guide_request'); tap('x_guide_consent'); tap('x_guide_start')
        wait('x_guide_preview'); before = text('x_guide_message')
        tap('x_guide_next'); assert text('x_guide_message') != before
        tap('x_guide_stop'); wait('x_guide_request')
        assert node('x_guide_preview') is None
        mark('Guided synthetic video: request, consent, play, next, revoke')
        case('S01'); support('video')
        for source in ('front', 'rear', 'phone'):
            tap('x_video_'+source); tap('x_video_approve'); tap('x_video_start')
            wait('x_video_status', '공유 중'); seek('x_video_preview')
            tap('x_next_frame'); tap('x_video_stop'); wait('x_video_status', '공유 종료')
            assert node('x_video_preview') is None
            mark('Synthetic '+source+' video: consent, preview, stop')

    def police_navigation():
        case('S14'); support('police')
        tap('x_police_request'); tap('x_police_review'); tap('x_police_reject')
        wait('x_police_status', '거절')
        tap('x_police_request'); tap('x_police_review'); tap('x_police_approve')
        wait('x_police_status', '실제 권한 없음')
        mark('112 mock cooperation includes independent review, rejection and approval')
        session(); support('navigation'); wait('x_nav_status', '제안 전')
        assert node('x_nav_set') is None
        tap('x_center'); tap('x_operator_guidance'); tap('x_return_session')
        support('location'); tap('x_share_location')
        session(); support('navigation')
        tap('x_nav_propose'); wait('x_nav_status', '가상 경로 제안')
        tap('x_nav_confirm'); wait('x_nav_status', '사용자 확인 완료')
        tap('x_nav_set'); wait('x_nav_status', '시연 목적지 설정 완료')
        mark('Mock navigation requires operator guidance, shared location and user confirmation')

    def cooperation():
        case('S14'); support('location'); tap('x_share_location')
        tools(); tap('x_cooperation')
        tap('x_coop_consent'); tap('x_coop_case_early'); tap('x_coop_review')
        tap('x_coop_invite_a'); tap('x_coop_accept'); tap('x_coop_authorize')
        tap('x_coop_request_119'); tap('x_coop_beacon_review'); tap('x_coop_beacon_approve')
        tap('x_coop_beacon_start'); seek('x_coop_preview')
        assert 'DEMO' in text('x_coop_preview')
        tap('x_coop_beacon_revoke'); assert node('x_coop_preview') is None
        tap('x_coop_arrive'); tap('x_coop_handover'); seek('x_coop_restart')
        mark('Nearby-vehicle mock: consent, invite, accept, authorize, display, revoke, handover')

    try:
        for name, fn in [('gallery', catalog), ('manuals', manuals),
                         ('access-driver', access_and_driver), ('text-sign', text_and_sign),
                         ('videos', videos), ('police-navigation', police_navigation),
                         ('cooperation', cooperation)]:
            check_group(name, fn)
        result['status'] = 'PASS' if not result['failures'] else 'FAIL'
    finally:
        try:
            guard.before_cleanup()
            restart(); tools(); tap('privacy_settings'); tap('privacy_reset')
            assert client.request('CLICK android:id/button1').get('clicked') == 'true'
            wait('x_emergency'); guard.acquire_clean()
            result['cleanup'] = 'PASS'
        finally:
            client.close()
            subprocess.run(adb+['shell', 'am', 'force-stop', 'com.lifelink119.app'],
                           capture_output=True, timeout=20)
            if previous_pin is None: os.environ.pop('LIFELINK_TEST_PIN', None)
            else: os.environ['LIFELINK_TEST_PIN'] = previous_pin
            (out/'results.json').write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding='utf-8')
    if result['failures']:
        raise SystemExit(1)


if __name__ == '__main__':
    main()
