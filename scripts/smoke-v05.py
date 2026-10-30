"""Bounded native smoke checks on a dedicated Pleos emulator; never contacts agencies."""
import argparse
import json
import os
from pathlib import Path
import subprocess
import time
import xml.etree.ElementTree as ET
from ui_snapshot_client import SnapshotClient, verify_installed_apk
from capture_emulator import capture_emulator

ROOT=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser(description=__doc__);p.add_argument('--serial',default='emulator-5580');p.add_argument('--expected-apk-version',default='0.5.0-demo');p.add_argument('--output-dir',default='verification/v05');args=p.parse_args()
assert args.serial.startswith('emulator-')
sdk=Path(os.environ.get('ANDROID_HOME',Path(os.environ['LOCALAPPDATA'])/'Android/Sdk'))
adb=[str(sdk/'platform-tools/adb.exe'),'-s',args.serial]
out=ROOT/args.output_dir;out.mkdir(parents=True,exist_ok=True)
checks=[];client=SnapshotClient(adb,auto_auth=True)

def find(key):
    xml=client.node(key)
    return ET.fromstring(xml).find('.//node')

def tap(key):
    if client.click(key):return
    for direction in ('down','up'):
        for _ in range(18):
            if not client.scroll(direction):break
            if client.click(key):return
    raise AssertionError('Control unavailable: '+key)

def expect(key,value):
    deadline=time.monotonic()+12;last=''
    while time.monotonic()<deadline:
        n=find(key)
        if n is not None:
            last=n.get('text','')
            if value in last:return last
        time.sleep(.25)
    raise AssertionError(f'{key}: expected {value!r}, got {last!r}')

def snap(name):
    tree=client.dump();(out/(name+'.xml')).write_text(tree,encoding='utf-8')
    capture_emulator(adb,out/(name+'.png'))

def record(name):checks.append(name);print('PASS',name,flush=True)
def gallery(case):
    tap('x_home');tap('x_more');tap('x_tools');tap('x_gallery');tap('x_case_'+case.lower());expect('x_session_status','가상 상담원 응답')
def end():tap('x_back');tap('x_end');expect('x_finished','공유정보를 정리했습니다')

def open_medical(control,ready):
    # Verify navigation after the system/dialog transition, not ACTION_CLICK alone.
    for _ in range(3):
        tap(control);client.authenticate_if_needed()
        deadline=time.monotonic()+2
        while time.monotonic()<deadline:
            if find(ready) is not None:return
            time.sleep(.2)
        if find(control) is None:break
    raise AssertionError('Authenticated destination unavailable: '+ready)

try:
    client.start()
    # Start at the explicit Activity, then always navigate by app-owned controls.
    if find('x_home') is not None:tap('x_home')
    expect('x_emergency','지금 긴급해요');snap('01-home')
    tap('x_emergency');tap('x_119');expect('x_session_status','가상 상담원 응답');expect('x_person','미등록 승객');record('119 urgent in two taps without profile/consent')
    tap('x_end');expect('x_finished','공유정보를 정리했습니다');tap('x_finish_home')
    tap('x_emergency');tap('x_112');expect('x_session_status','가상 상담원 응답');expect('x_person','미등록 승객');record('112 direct in two taps without 119 prerequisite')
    tap('x_end');tap('x_finish_home');tap('x_consult');tap('x_109');expect('x_session_status','가상 상담원 응답');expect('x_primary_1','공유');record('109 privacy-first route')
    # S01: granular consent / mock center / request-consent-start-stop video.
    gallery('S01');expect('x_primary_1','응급정보 공유');open_medical('x_primary_1','x_consent_conditions')
    tap('x_consent_conditions');tap('x_consent_allergies');tap('x_share_card');tap('x_center');expect('x_shared_count','2개');snap('02-s01-consent-center');record('S01 selected two fields reflected in app center')
    tap('x_return_session');tap('x_primary_2');tap('x_video_phone');tap('x_video_approve');tap('x_video_start');expect('x_video_status','공유 중');tap('x_next_frame');snap('03-s01-synthetic-video');tap('x_video_stop');expect('x_video_status','공유 종료');record('S01 synthetic video request/consent/start/frame/stop')
    end();record('S01 session end and cleanup')
    # S08: hearing profile reorders text/sign/location.
    gallery('S08');expect('x_primary_1','문자로 설명');expect('x_primary_2','수어통역');tap('x_primary_1');tap('x_send_phrase');tap('x_reply');expect('x_messages','가상 상담원');snap('04-s08-text');tap('x_back');tap('x_primary_2');tap('x_sign_connect');expect('x_sign_status','가상 통역사 연결 시연');snap('05-s08-sign');tap('x_sign_connect');end();record('S08 preset text/reply and explicit three-party sign mock')
    # S14: vehicle/location, police review and decision, guided navigation.
    gallery('S14');tap('x_primary_1');tap('x_share_location');tap('x_share_vehicle');tap('x_back');tap('x_primary_3')
    tap('x_police_request');expect('x_police_status','요청됨');tap('x_police_review');expect('x_police_status','검토 중');tap('x_police_reject');expect('x_police_status','거절');tap('x_police_request');tap('x_police_review');tap('x_police_approve');expect('x_police_status','실제 권한 없음');snap('06-s14-police');record('S14 police request/review/reject/re-request/mock approval')
    tap('x_back');tap('x_support');tap('x_center');tap('x_operator_guidance');tap('x_return_session');tap('x_support');tap('x_action_navigation');tap('x_nav_propose');tap('x_nav_confirm');tap('x_nav_set');expect('x_nav_status','설정 완료');snap('07-s14-navigation');end();record('S14 guidance/location/proposal/user confirmation/mock navigation/cleanup')
    # EV calculation and unsupported-car fallback.
    tap('x_finish_home');tap('x_more');tap('x_power');tap('x_ev_support');expect('x_power_result','미지원');snap('08-ev-fallback');tap('x_ev_support');expect('x_power_result','114.24');record('EV/V2L formula output and unsupported fallback')
    tap('x_home');tap('x_more');tap('x_tools');tap('x_legacy');snap('09-legacy');record('Legacy tools remain reachable');
    # Back to new home; read actual platform restriction provenance.
    tap('x_home');tap('x_more');tap('x_tools');
    for _ in range(10):
        if find('x_restrictions') is not None:break
        if not client.scroll('down'):break
    text=expect('x_restrictions','REAL');record('Actual CarUxRestrictions callback: '+text.replace('\n',' / '))
    tap('x_test_restriction');tap('x_home');tap('x_emergency');tap('x_119');expect('x_session_status','가상 상담원 응답');tap('x_person');assert find('x_person_1') is None,'Restricted profile list remained visible';snap('10-restricted-detail');record('Extra restrictions preserve urgent entry and block detailed profile UI')
    tap('x_home');tap('x_more');tap('x_tools');tap('x_test_restriction');tap('x_home')
    result={'status':'PASS','serial':args.serial,'checks':checks,'apkSha256':verify_installed_apk(adb,ROOT/'app/build/outputs/apk/debug/app-debug.apk',sdk,args.expected_apk_version),'limitations':['Synthetic services only; physical vehicle/real agencies/actual driving/TalkBack not end-to-end verified.']}
except Exception as e:
    result={'status':'FAIL','serial':args.serial,'checks':checks,'error':str(e)}
    try:snap('failure')
    except Exception:pass
    raise
finally:
    (out/'native-smoke.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
    client.close()
