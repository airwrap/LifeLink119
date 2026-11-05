"""Capture public views of the installed APK on a dedicated emulator."""
import argparse
import json
import os
from pathlib import Path
import subprocess
import time
import xml.etree.ElementTree as ET
from native_window_client import WindowClient

p=argparse.ArgumentParser();p.add_argument('--serial',required=True);p.add_argument('--phase',choices=['before','after'],required=True);a=p.parse_args()
assert a.serial.startswith('emulator-')
root=Path(__file__).resolve().parents[1];out=root/'verification/v10/ux'/a.phase;out.mkdir(parents=True,exist_ok=True)
adb=[str(Path(os.environ['LOCALAPPDATA'])/'Android/Sdk/platform-tools/adb.exe'),'-s',a.serial]
name=subprocess.check_output(adb+['emu','avd','name'],timeout=15).decode().splitlines()[0]
assert name=='LifeLink119_Verify_20261101'
c=WindowClient(adb)
def node(k):return ET.fromstring(c.node(k)).find('.//node')
def tap(k):
    if c.click(k):return
    for direction in ('down','up'):
        for _ in range(20):
            if not c.scroll(direction):break
            if c.click(k):return
    raise RuntimeError('Control unavailable: '+k)
def wait(k,word):
    for _ in range(100):
        n=node(k)
        if n is not None and word in n.get('text',''):return
        time.sleep(.15)
    raise RuntimeError('State unavailable: '+k)
def capture(name):
    time.sleep(.8) # Let the native window finish its layout and draw.
    assert not c.request('PING').get('secureRefused')
    (out/(name+'.jpg')).write_bytes(c.frame())
    (out/(name+'.xml')).write_text(c.dump(),encoding='utf-8')
    print('CAPTURE',a.phase,name,flush=True)
try:
    subprocess.run(adb+['shell','am','force-stop','com.lifelink119.app'],check=True,timeout=20)
    c.start();wait('x_emergency','119');capture('01-home')
    tap('x_consult' if a.phase=='after' else 'x_emergency');capture('02-choice')
    tap('x_home');tap('x_consult');capture('03-consult-choice')
    tap('x_medical');wait('x_session_status','가상 상담원 응답');capture('04-consult-connected')
    tap('x_home');tap('x_emergency' if a.phase=='after' else 'x_driving_quick');wait('x_session_status','가상 상담원 응답');capture('05-driving')
    if a.phase=='after':
        tap('x_guided_video');tap('x_guide_request');capture('06-video-consent')
        tap('x_guide_consent');tap('x_guide_start');capture('07-video-guide')
    print(json.dumps({'avd':name,'phase':a.phase,'screens':7 if a.phase=='after' else 5},ensure_ascii=False))
finally:c.close()
