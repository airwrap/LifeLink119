"""Public official pages through actual Android accessibility/touch, with real offline recovery.

Dedicated clean QA AVD only. Never types an ID, requests SMS, logs in, captures
protected pixels or enables WebView debugging. Restores QA network settings.
"""
import argparse
import json
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET
from qa_avd_guard import QaAvdGuard, android_sdk
from ui_snapshot_client import verify_installed_apk

ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--serial', required=True)
    args = parser.parse_args()
    adb = [str(android_sdk()/'platform-tools/adb.exe'), '-s', args.serial]
    guard = QaAvdGuard(adb).acquire_clean()
    digest = verify_installed_apk(adb, ROOT/'app/build/outputs/apk/debug/app-debug.apk', android_sdk())
    result = dict(status='FAIL', apkSha256=digest, checks=[], officialLoginCompleted=False,
                  credentialsEntered=False, smsRequested=False, protectedScreenshots=0)
    out = ROOT/'verification/current-browser'
    out.mkdir(parents=True, exist_ok=True)
    network_changed = False
    def run(*a): return subprocess.check_output(adb+list(a), timeout=25).decode('utf-8').strip()
    def tree():
        run('shell', 'uiautomator', 'dump', '--compressed', '/data/local/tmp/browser-current-qa.xml')
        return ET.fromstring(run('shell', 'cat', '/data/local/tmp/browser-current-qa.xml'))
    def full_id(key):
        return 'com.lifelink119.app:id/'+key if key.startswith(('x_', 'official_')) else key
    def find(root, key):
        return next((n for n in root.iter('node') if n.get('resource-id') == full_id(key)), None)
    def wait(key, contains='', timeout=35):
        end = time.monotonic()+timeout
        while time.monotonic()<end:
            node = find(tree(), key)
            if node is not None and contains in node.get('text', ''): return node
            time.sleep(.3)
        raise AssertionError('Public control/state not observed: '+key)
    def tap(key):
        node = wait(key)
        if node.get('enabled') != 'true': raise AssertionError('Disabled '+key)
        x1,y1,x2,y2 = map(int, re.findall(r'\d+', node.get('bounds')))
        if x2 <= x1 or y2 <= y1: raise AssertionError('Offscreen '+key)
        run('shell', 'input', 'tap', str((x1+x2)//2), str((y1+y2)//2))
    def mark(value):
        result['checks'].append(value); print('PASS '+value, flush=True)
    def open_agency():
        tap('x_more'); tap('x_safe_official')
    wifi = run('shell','settings','get','global','wifi_on')
    mobile = run('shell','settings','get','global','mobile_data')
    try:
        run('shell','am','force-stop','com.lifelink119.app')
        run('shell','am','start','-n','com.lifelink119.app/.MainActivity')
        root = tree()
        if find(root,'x_ai_setup_local') is not None: tap('x_ai_setup_local')
        open_agency();tap('x_agency_open')
        wait('official_web_origin','u119.nfa.go.kr')
        wait('mf_wfm_header_btn_login')
        tap('mf_wfm_header_btn_login');wait('mf_wfm_container_ibx_id')
        windows=run('shell','dumpsys','window','windows')
        match=re.search(r'Window #\d+ Window\{[^\n]*OfficialHealthBrowserActivity[^\n]*\}:(.*?)(?=Window #|\Z)', windows, re.S)
        if not match or 'SECURE' not in match.group(1): raise AssertionError('Secure browser window missing')
        mark('Official SafeCall homepage -> login form via real touch; secure window retained')
        tap('official_web_close');wait('x_agency_open');tap('x_agency_open')
        wait('mf_wfm_header_btn_login')
        if find(tree(),'mf_wfm_container_ibx_id') is not None: raise AssertionError('Old page restored unexpectedly')
        mark('Closing returns to app; reopening begins a fresh public website session')
        if wifi not in {'0','1','2'} or mobile not in {'0','1'}:
            raise RuntimeError('Unknown QA network state; offline test refused')
        guard.verify_target();network_changed=True
        run('shell','svc','wifi','disable');run('shell','svc','data','disable')
        time.sleep(2)
        tap('official_web_retry');wait('official_web_status','연결하지 못했습니다')
        mark('Real QA network loss displays recoverable connection error')
        run('shell','svc','data','enable' if mobile=='1' else 'disable')
        run('shell','svc','wifi','enable' if wifi!='0' else 'disable')
        network_changed=False
        time.sleep(5)
        tap('official_web_retry');wait('mf_wfm_header_btn_login',timeout=50)
        mark('Restoring network and retrying reloads the official site')
        tap('official_web_close')
        for key, host in [('x_agency_guide','nfa.go.kr'), ('x_agency_healthway','myhealthway.go.kr')]:
            tap(key);wait('official_web_origin',host)
            end=time.monotonic()+35
            visible=False
            while time.monotonic()<end:
                root=tree();error=find(root,'official_web_status')
                if error is not None and '확인하고' not in error.get('text','') and '열고 있' not in error.get('text',''):
                    raise AssertionError(key+': '+error.get('text',''))
                web=next((n for n in root.iter('node') if n.get('class')=='android.webkit.WebView'),None)
                if web is not None and any(n.get('text','').strip() for n in web.iter('node')):
                    visible=True;break
            if not visible: raise AssertionError('Public website content missing: '+key)
            mark('Embedded official information page loads: '+host)
            tap('official_web_close');wait('x_agency_open')
        tap('x_home');wait('x_emergency')
        result['status']='PASS'
    finally:
        if network_changed:
            guard.verify_target()
            run('shell','svc','data','enable' if mobile=='1' else 'disable')
            run('shell','svc','wifi','enable' if wifi!='0' else 'disable')
        result['networkSettingsRestored'] = (run('shell','settings','get','global','wifi_on') == wifi
                                             and run('shell','settings','get','global','mobile_data') == mobile)
        (out/'results.json').write_text(json.dumps(result, ensure_ascii=False, indent=2),encoding='utf-8')


if __name__ == '__main__': main()
