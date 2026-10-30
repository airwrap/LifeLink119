"""Targeted offline recovery, EMS-first and V2L boundary checks; no backend required."""
import json
import os
from pathlib import Path
import socket
import subprocess
import time
from contest_ui import ContestUI
from ui_snapshot_client import verify_installed_apk

root=Path(__file__).resolve().parents[1];out=root/'verification/v06/recovery';checks=[]
sdk=Path(os.environ['LOCALAPPDATA'])/'Android/Sdk';adb=[str(sdk/'platform-tools/adb.exe'),'-s','emulator-5580']
result={'status':'FAIL'}
try:
    try:
        with socket.create_connection(('127.0.0.1',8781),timeout=1):raise AssertionError('Stop the owned local demo server to prove offline execution')
    except (ConnectionRefusedError,TimeoutError):pass
    with ContestUI(adb,out) as ui:
        ui.home();ui.tap('x_more');ui.tap('x_tools');ui.tap('x_fault_connection');ui.home();ui.tap('x_emergency');ui.tap('x_119');ui.expect('x_session_status','연결 시연 실패');ui.snap('connection-failed');ui.tap('x_retry');ui.expect('x_session_status','가상 상담원 응답');checks.append('Offline connection failure and explicit retry')
        ui.home();ui.tap('x_more');ui.tap('x_tools');ui.tap('x_fault_location');ui.home();ui.tap('x_more');ui.tap('x_resume');ui.tap('x_primary_2');ui.expect('x_location_status','위치 없음');assert ui.node('x_share_location').get('enabled')=='false';ui.snap('location-missing');ui.tap('x_location_retry');ui.tap('x_share_location');ui.tap('x_back');checks.append('Missing location blocks sharing and recovers without ending connection')
        ui.support('cooperation');ui.tap('x_coop_consent');ui.tap('x_coop_case_ems');ui.tap('x_coop_review');ui.tap('x_coop_invite_a');ui.tap('x_coop_accept');ui.tap('x_coop_authorize');assert '기존 대응 유지' in '\n'.join(ui.labels());ui.snap('ems-first-control');ui.tap('x_coop_cancel');before=ui.expect('x_coop_time','경과');time.sleep(1.2);assert ui.expect('x_coop_time','경과')==before;checks.append('EMS-first fixed control condition and frozen end-of-demo elapsed time')
        ui.gallery('S05');ui.open_medical('x_primary_1','x_summary');ui.set_value('x_glucose',0);ui.tap('x_summary_update');ui.expect('x_message','확인');ui.set_value('x_glucose',80);ui.tap('x_summary_update');ui.expect('x_summary','80 mg/dL');checks.append('Invalid synthetic numeric input recovers to a valid editable summary')
        ui.home();ui.tap('x_more');ui.tap('x_power');ui.set_value('x_ev_load',0,'SET_PROGRESS');ui.expect('x_power_result','부하 0W');ui.snap('v2l-zero-load');ui.set_value('x_ev_load',20,'SET_PROGRESS');ui.set_value('x_ev_reserve',100,'SET_PROGRESS');ui.expect('x_power_result','0.00');ui.snap('v2l-reserve-above-soc');ui.set_value('x_ev_reserve',30,'SET_PROGRESS');ui.expect('x_power_result','114.24');checks.append('Live V2L slider boundaries: 0W, reserve above SOC, restored formula')
        ui.home();result={'status':'PASS','localConsoleReachable':False,'checks':checks,'apkSha256':verify_installed_apk(adb,root/'app/build/outputs/apk/debug/app-debug.apk',sdk,'0.6.0-demo')}
except Exception as e:
    result={'status':'FAIL','checks':checks,'error':str(e)};print('FAIL',str(e),flush=True)
    try:
        with ContestUI(adb,out) as ui:ui.snap('failure')
    except Exception:pass
finally:(out.parent/'recovery.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps(result,ensure_ascii=False,indent=2),flush=True)
if result['status']!='PASS':raise SystemExit(1)
