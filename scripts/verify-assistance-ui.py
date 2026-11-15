"""Public-screen QA for the family assistance dashboard. No private fields or real calls.

Install app-debug.apk and prepare-native-window.py on a dedicated QA AVD first.
"""
import argparse
import json
from pathlib import Path
import re
import time
import xml.etree.ElementTree as ET

from native_window_client import WindowClient
from qa_avd_guard import QaAvdGuard, android_sdk
from ui_snapshot_client import verify_installed_apk

ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--serial', required=True)
    parser.add_argument('--label', default='official')
    parser.add_argument('--home-only', action='store_true')
    parser.add_argument('--allow-scroll', action='store_true')
    args = parser.parse_args()
    if not re.fullmatch(r'[a-z0-9-]+', args.label):
        raise RuntimeError('Use a simple label')
    sdk = android_sdk()
    adb = [str(sdk / 'platform-tools/adb.exe'), '-s', args.serial]
    QaAvdGuard(adb).acquire_clean()
    digest = verify_installed_apk(adb, ROOT / 'app/build/outputs/apk/debug/app-debug.apk', sdk)
    out = ROOT / 'verification/assistance-ui' / args.label
    out.mkdir(parents=True, exist_ok=True)
    client = WindowClient(adb)
    checks = []
    result = {'status': 'FAIL', 'apkSha256': digest, 'checks': checks, 'actualCalls': 0}

    def node(key):
        return ET.fromstring(client.node(key)).find('.//node')

    def reachable_node(key):
        value = node(key)
        if value is not None:
            return value
        if args.allow_scroll:
            for direction in ('up', 'down'):
                for _ in range(15):
                    if not client.scroll(direction):
                        break
                    value = node(key)
                    if value is not None:
                        return value
        raise AssertionError('Missing visible control: ' + key)

    def wait(key, expected='', seconds=10):
        until = time.monotonic() + seconds
        while time.monotonic() < until:
            value = node(key)
            if value is not None and expected in value.get('text', ''):
                return value
            time.sleep(.1)
        raise AssertionError('Expected public state: ' + key)

    def tap(key):
        if client.click(key):
            return
        for direction in ('down', 'up'):
            for _ in range(15):
                if not client.scroll(direction):
                    break
                if client.click(key):
                    return
        raise AssertionError('Could not activate ' + key)

    def rect(value):
        return list(map(int, re.findall(r'-?\d+', value.get('bounds', ''))))

    def shot(name):
        if client.request('PING').get('secureRefused'):
            raise AssertionError('Protected screen; refused capture')
        (out / (name + '.jpg')).write_bytes(client.frame())
        xml = client.dump()
        (out / (name + '.xml')).write_text(xml, encoding='utf-8')
        tree = ET.fromstring(xml)
        clipped = [n.get('text') for n in tree.iter('node') if n.get('text-clipped') == 'true']
        if clipped:
            raise AssertionError('Clipped visible text: ' + repr(clipped))
        if node('safety_banner') is None:
            raise AssertionError('Demo banner is missing')
        return tree

    def mark(message):
        checks.append(message)
        print('PASS ' + message, flush=True)

    try:
        client.start()
        wait('x_emergency')
        tree = shot('home')
        viewport = rect(tree.find('.//node'))
        for key in ('x_emergency', 'x_medical', 'x_109', 'x_home_112', 'x_family'):
            value = node(key)
            if args.allow_scroll:
                for _ in range(15):
                    if value is not None:
                        break
                    client.scroll('down')
                    value = node(key)
            if value is None or value.get('clickable') != 'true':
                raise AssertionError('Missing touch target ' + key)
            x1, y1, x2, y2 = rect(value)
            if y2 - y1 < 96 or x2 - x1 < 96:
                raise AssertionError('Touch target is too small ' + key)
            if not args.allow_scroll and not (0 <= x1 < x2 <= viewport[2] and 0 <= y1 < y2 <= viewport[3]):
                raise AssertionError('Primary home target requires scrolling: ' + key)
        mark('Five home actions are reachable with automotive touch targets')
        if not args.home_only:
            routes = [('x_emergency', '119 긴급신고', 'emergency119'),
                      ('x_medical', '119 응급상담', 'medical119'),
                      ('x_109', '109 마음상담', 'counsel109'),
                      ('x_home_112', '112 경찰 신고', 'police112')]
            for key, title, name in routes:
                tap(key)
                wait('x_session_status', '가상 상담원 응답')
                tree = shot(name)
                texts = [n.get('text') for n in tree.iter('node')]
                if title not in texts or client.request('PING').get('secureRefused'):
                    raise AssertionError('Wrong route or unnecessary authentication: ' + key)
                if '위치·가족정보 기본 비공유' not in texts:
                    raise AssertionError('Unexpected sharing default')
                wait('x_end')
                tap('x_end')
                wait('x_home')
                tap('x_home')
                wait('x_emergency')
                mark(title + ' starts with one tap, no PIN, default private, and can end')
            tap('x_more')
            tap('x_tools')
            for _ in range(15):
                if node('x_test_restriction') is not None:
                    break
                client.scroll('down')
            tap('x_test_restriction')
            tap('x_home')
            tap('x_emergency')
            wait('x_session_status', '가상 상담원 응답')
            if reachable_node('x_guided_video').get('enabled') != 'false' or reachable_node('x_driving_details').get('enabled') != 'false':
                raise AssertionError('Driving restriction bypassed')
            tap('x_home')
            tap('x_109')
            wait('x_session_status', '가상 상담원 응답')
            if '문자로 설명' in client.node('x_primary_1'):
                raise AssertionError('Text messaging promoted while restricted')
            tap('x_end')
            tap('x_home')
            tap('x_more')
            tap('x_tools')
            tap('x_test_restriction')
            tap('x_home')
            mark('Driving restriction preserves urgent entry and blocks video, details and text promotion')
            tap('x_family')
            time.sleep(.3)
            if not client.request('PING').get('secureRefused'):
                raise AssertionError('Family information lost its secure window')
            client.request('CANCEL_AUTH')
            mark('Family information still requires authentication; no protected frame captured')
        result['status'] = 'PASS'
    finally:
        client.close()
        (out / 'result.json').write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding='utf-8')


if __name__ == '__main__':
    main()
