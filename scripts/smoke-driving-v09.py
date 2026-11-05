"""Public native driving UI gate. No credentials, real calls or private pages."""
import argparse
import json
import os
from pathlib import Path
import subprocess
import time
from contest_ui import ContestUI
from capture_emulator import capture_emulator
from ui_snapshot_client import verify_installed_apk
from native_window_client import WindowClient

root = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--serial', required=True)
args = parser.parse_args()
assert args.serial.startswith('emulator-'), 'Use a dedicated emulator'
sdk = Path(os.environ.get('ANDROID_HOME', Path(os.environ['LOCALAPPDATA'])/'Android/Sdk'))
adb = [str(sdk/'platform-tools/adb.exe'), '-s', args.serial]
out = root/'verification/v09/driving-generic'
out.mkdir(parents=True, exist_ok=True)
checks = []
result = {'status': 'FAIL'}

class DrivingUI(ContestUI):
    """AAOS can briefly move the accessibility root after a width transition."""
    retries = 0
    def __init__(self, adb, out):
        super().__init__(adb, out)
        self.client = WindowClient(adb)
    def expect(self, key, value, seconds=15):
        # The native backend deliberately reports only visible Views.
        if self.node(key) is None:
            for direction in ('up', 'down'):
                for _ in range(18):
                    if not self.client.scroll(direction): break
                    if self.node(key) is not None: return super().expect(key,value,seconds)
        return super().expect(key,value,seconds)
    def tap(self, key):
        try:
            return super().tap(key)
        except AssertionError as error:
            if not str(error).startswith('Control unavailable:'):
                raise
            self.retries += 1
            print('RETRY after automotive window transition:', key, flush=True)
            self.focus()
            time.sleep(.35)
            return super().tap(key)

def mark(name):
    checks.append(name)
    print('PASS', name, flush=True)

def tools(ui):
    ui.home(); ui.tap('x_more'); ui.tap('x_tools')

def record(ui, name):
    # Accessibility evidence is distinct from visual screenshot evidence.
    (out/(name+'.xml')).write_text(ui.client.dump(), encoding='utf-8')
    (out/(name+'.jpg')).write_bytes(ui.client.frame())
    capture_emulator(adb, out/(name+'.png'))

try:
    # Reset only the app process on the explicitly selected test emulator.
    # Profiles, app credentials and all unrelated processes are preserved.
    subprocess.run(adb+['shell', 'am', 'force-stop', 'com.lifelink119.app'], check=True, timeout=20)
    ui = DrivingUI(adb, out)
    ui.client.auto_auth = False
    with ui:
        ui.home()
        ui.expect('x_emergency', '119 구조'); ui.expect('x_consult', '상담')
        ui.tap('x_emergency')
        ui.expect('x_session_status', '가상 상담원 응답')
        ui.expect('x_driving_safety', '주행 중')
        try:
            ui.expect('x_driving_shared', '없음')
        except AssertionError:
            if ui.node('x_driving_restricted') is not None:
                raise RuntimeError('Platform restricts detailed actions. The quick call works; run the full gate only on an allowed platform state. No restriction bypass is performed.')
            raise
        ticket = ui.expect('x_ticket', 'DEMO-')
        ui.tap('x_driving_quick'); assert ticket == ui.expect('x_ticket', 'DEMO-')
        mark('One-touch request while moving, no profile/PIN; repeated shortcut keeps ticket')
        record(ui, '01-connected-moving')

        ui.expect('x_driving_context', '차 안의 위급 상황')
        assert ui.node('x_driving_snake') is None and ui.node('x_driving_smoke') is None
        assert ui.node('x_driving_handover') is None
        ui.tap('x_driving_send'); ui.expect('x_driving_report', '전달됨')
        ui.tap('x_driving_ack'); ui.expect('x_driving_report', '확인 완료')
        ui.expect('x_driving_safety', '주행 중')
        mark('Report and acknowledgement occur before stop, without location')
        record(ui, '02-situation-acknowledged')
        ui.tap('x_driving_location'); ui.expect('x_driving_shared', '위치')
        ui.tap('x_driving_vehicle'); ui.expect('x_driving_shared', '차량')
        ui.tap('x_driving_revoke'); ui.expect('x_driving_shared', '없음')
        mark('Explicit synthetic location/vehicle sharing and complete revocation')
        ui.tap('x_driving_stopped'); ui.expect('x_driving_safety', '정차했다고')
        assert ui.node('x_driving_handover') is None
        ui.tap('x_driving_safe'); ui.expect('x_driving_safety', '안전')
        ui.tap('x_driving_handover'); ui.expect('x_driving_report', '인계 완료')
        record(ui, '03-handover')
        ui.tap('x_driving_finish'); ui.expect('x_finished', '공유정보')
        mark('Ordered stop/safe-place reports and operator acknowledgement gate mock handover')

        tools(ui); ui.tap('x_driving_setup'); ui.tap('x_driving_voice')
        ui.expect('x_session_status', '가상 상담원 응답')
        ui.expect('x_driving_trigger', '실제 인식 아님')
        record(ui, '04-voice-example')
        ui.tap('x_end'); ui.expect('x_finished', '공유정보')
        mark('Voice example is explicitly a preset, and can be cancelled')

        tools(ui); ui.tap('x_fault_connection'); ui.tap('x_driving_quick')
        ui.expect('x_session_status', '실패')
        failed_ticket = ui.expect('x_ticket', 'DEMO-')
        ui.tap('x_driving_quick'); assert failed_ticket == ui.expect('x_ticket', 'DEMO-')
        ui.tap('x_retry'); ui.expect('x_session_status', '가상 상담원 응답')
        assert failed_ticket == ui.expect('x_ticket', 'DEMO-')
        ui.tap('x_end'); ui.expect('x_finished', '공유정보')
        mark('Failure/retry keeps the same request; no duplicate dispatch')

        tools(ui); ui.tap('x_fault_location'); ui.tap('x_driving_quick')
        ui.expect('x_session_status', '가상 상담원 응답')
        assert ui.node('x_driving_location').get('enabled') == 'false'
        ui.tap('x_driving_send'); ui.tap('x_driving_ack')
        ui.expect('x_driving_report', '확인 완료'); ui.tap('x_end')
        tools(ui); ui.tap('x_fault_location')
        mark('Unavailable location blocks only location sharing, not calling/reporting')

        ui.tap('x_test_restriction'); ui.tap('x_driving_quick')
        ui.expect('x_session_status', '가상 상담원 응답')
        ui.expect('x_driving_restricted', '플랫폼 조작 제한')
        assert ui.node('x_driving_send') is None and ui.node('x_driving_snake') is None
        record(ui, '05-restricted')
        ui.tap('x_end'); tools(ui); ui.tap('x_test_restriction')
        mark('Restrictive overlay preserves quick request and hides detailed role actions')

        ui.tap('x_narrow_preview'); ui.home()
        assert ui.bounds(ui.node('x_emergency'))[3] <= ui.bounds(ui.node('x_consult'))[1]
        ui.tap('x_emergency'); ui.expect('x_session_status', '가상 상담원 응답')
        ui.expect('x_driving_context', '차 안의 위급 상황')
        record(ui, '06-narrow-520dp')
        ui.tap('x_end'); tools(ui); ui.tap('x_narrow_preview')
        mark('520dp layout retains quick request and generic emergency context')

        for entry, target in [('x_home_112', None), ('x_consult', 'x_medical'), ('x_consult', 'x_109')]:
            ui.home(); ui.tap(entry)
            if target: ui.tap(target)
            ui.expect('x_session_status', '가상 상담원 응답')
            ui.tap('x_end'); ui.expect('x_finished', '공유정보')
        mark('Existing 112, 119 consultation and 109 public entry routes still connect')
        ui.home(); ui.tap('x_more'); ui.tap('x_tools'); ui.tap('x_gallery'); ui.tap('x_case_d01')
        ui.expect('x_session_status', '가상 상담원 응답'); ui.expect('x_driving_safety', '주행 중')
        ui.tap('x_end'); ui.home()
        mark('D01 gallery entry starts the dedicated driving flow')

        digest = verify_installed_apk(adb, root/'app/build/outputs/apk/debug/app-debug.apk', sdk, '0.9.0-demo')
        result = {'status': 'PASS', 'checks': checks, 'apkSha256': digest, 'serial': args.serial,
                  'automotiveWindowRetries': ui.retries, 'evidenceDirectory': 'driving-generic', 'automationBackend': 'native-view-instrumentation',
                  'limitations': ['Accessibility assertions are not visual screenshot evidence.',
                                  'Motion, voice input, agency response and handover are synthetic.',
                                  'Test overlay does not certify an OEM driving policy.']}
except Exception as error:
    result = {'status': 'FAIL', 'checks': checks, 'error': str(error)}
    print('FAIL', error, flush=True)
finally:
    (out.parent/'driving-native.json').write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding='utf-8')
if result['status'] != 'PASS':
    raise SystemExit(1)
