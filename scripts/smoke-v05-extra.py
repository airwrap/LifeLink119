"""Additional fake profile, loopback snapshot and narrow-window checks on the owned AVD."""
import json
import os
from pathlib import Path
import re
import subprocess
import time
import urllib.request
import xml.etree.ElementTree as ET
from ui_snapshot_client import SnapshotClient

root=Path(__file__).resolve().parents[1];out=root/'verification/v05'
adb=[str(Path(os.environ['LOCALAPPDATA'])/'Android/Sdk/platform-tools/adb.exe'),'-s','emulator-5580']
c=SnapshotClient(adb);checks=[];resize=None
def shell(*args):return subprocess.check_output(adb+['shell',*args],timeout=30).decode(errors='replace')
def node(k):return ET.fromstring(c.node(k)).find('.//node')
def tap(k):
    if c.click(k):return
    for d in ('down','up'):
        for _ in range(18):
            if not c.scroll(d):break
            if c.click(k):return
    raise AssertionError('Unavailable '+k)
def wait(k,text):
    end=time.monotonic()+12
    while time.monotonic()<end:
        n=node(k)
        if n is not None and text in n.get('text',''):return
        time.sleep(.25)
    raise AssertionError(k+' missing '+text)
def snap(name):
    (out/(name+'.xml')).write_text(c.dump(),encoding='utf-8')
    with (out/(name+'.png')).open('wb') as f:subprocess.run(adb+['exec-out','screencap','-p'],stdout=f,check=True,timeout=30)
def mark(t):checks.append(t);print('PASS',t,flush=True)
def confirm():
    r=c.request('CLICK android:id/button1');assert r.get('clicked')=='true',r;time.sleep(.5)
def snapshots():
    with urllib.request.urlopen('http://127.0.0.1:8781/api/demo/expansion/snapshots',timeout=5) as r:return json.load(r)['items']

try:
    c.start()
    if node('x_home') is not None:tap('x_home')
    tap('x_family');tap('x_profile_add');confirm();wait('x_profile_alias','')
    snap('11-profile-editor');tap('x_profile_save');tap('x_profile_reset');confirm();mark('Synthetic profile add/save and confirmed default restore')
    tap('x_home');tap('x_emergency');tap('x_119');wait('x_session_status','가상 상담원 응답');tap('x_primary_2');tap('x_share_location');tap('x_back');tap('x_support');tap('x_center');tap('x_pc_consent');tap('x_pc_publish');wait('x_pc_status','게시됨')
    data=snapshots();assert len(data)==1 and data[0]['sharedFields']==['LOCATION'],data
    assert set(data[0])=={'schemaVersion','syntheticOnly','id','revision','target','scenarioId','status','sharedFields','video','videoSource','police','navigation','sign'}
    snap('12-pc-metadata');tap('x_pc_revoke');wait('x_pc_status','철회됨');assert snapshots()==[];mark('App optional PC snapshot sends only metadata, and revoke removes it')
    tap('x_return_session');tap('x_end');tap('x_finish_home')
    # Keep the current display override, including a pre-existing one, and restore it in finally.
    original=shell('wm','size');match=re.search(r'Override size: (\d+x\d+)',original)
    resize=match.group(1) if match else 'reset';print('RESIZE_ORIGINAL',original.strip(),flush=True)
    shell('wm','size','900x1600','-d','0');time.sleep(2)
    shell('am','start','-W','--display','0','-f','0x10008000','-n','com.lifelink119.app/.MainActivity')
    wait('x_emergency','지금 긴급해요');a=node('x_emergency');b=node('x_consult')
    assert a is not None and b is not None
    def bounds(n):return list(map(int,re.findall(r'\d+',n.get('bounds'))))
    x,y=bounds(a),bounds(b);assert x[3]<=y[1],(x,y)
    snap('13-narrow-portrait-home');tap('x_emergency');tap('x_119');wait('x_session_status','가상 상담원 응답');snap('14-narrow-session');mark('900x1600 display: stacked CTAs, two-tap urgent session and recreation')
    result={'status':'PASS','checks':checks,'displayOriginal':original}
except Exception as e:
    result={'status':'PARTIAL','checks':checks,'error':str(e)};print('PARTIAL',str(e),flush=True)
    try:snap('extra-failure')
    except Exception:pass
finally:
    if resize is not None:
        print('RESTORE_DISPLAY',shell('wm','size',resize,'-d','0').strip(),flush=True)
        time.sleep(1)
        try:shell('am','start','-W','--display','0','-f','0x10008000','-n','com.lifelink119.app/.MainActivity')
        except Exception:pass
    try:
        if node('x_home') is not None:tap('x_home')
    except Exception:pass
    (out/'native-extra.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8');c.close()
