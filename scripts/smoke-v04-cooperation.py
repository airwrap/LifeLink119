"""Drive the real Pleos APK through synthetic cooperative-vehicle experiments.

Uses the separate public-accessibility snapshot helper. Operator actions are
explicit localhost role simulations; no agency, GPS or physical lamp is used.
"""
import argparse
from datetime import datetime, timezone
import hashlib
import io
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import time
import urllib.request
import xml.etree.ElementTree as ET
from PIL import Image, ImageChops, ImageStat
from ui_snapshot_client import SnapshotClient, verify_installed_apk

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'verification'
PKG='com.lifelink119.app'

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial',required=True)
    parser.add_argument('--expected-apk-version',default='0.4.0-demo')
    parser.add_argument('--output-prefix',default='v04')
    parser.add_argument('--moving-demo',action='store_true')
    args=parser.parse_args()
    assert args.serial.startswith('emulator-')
    assert re.fullmatch(r'[a-z0-9-]{3,40}',args.output_prefix)
    sdk=Path(os.environ.get('ANDROID_HOME',Path(os.environ['LOCALAPPDATA'])/'Android/Sdk'))
    cmd=[str(sdk/'platform-tools/adb.exe'),'-s',args.serial]
    results=[];phase='startup';run_id=None;snapshot_client=None
    apk=ROOT/'app/build/outputs/apk/debug/app-debug.apk'
    def adb(*parts,raw=False,timeout=60):
        output=subprocess.check_output(cmd+list(parts),timeout=timeout)
        return output if raw else output.decode('utf-8','replace').strip()
    def api(path,body=None,key=None):
        data=json.dumps(body).encode() if body is not None else None
        headers={'Content-Type':'application/json'}
        if key:headers['Idempotency-Key']=key
        req=urllib.request.Request('http://127.0.0.1:8781/api/demo/cooperation'+path,data=data,headers=headers)
        with urllib.request.urlopen(req,timeout=8) as response:return json.load(response)
    def current():return api('/runs/'+run_id)
    def op(action,authority='119',**extra):
        return api('/runs/'+run_id+'/operator',{'syntheticOnly':True,'action':action,'authority':authority,**extra})
    def node(tree,key):return next((n for n in tree.iter('node') if n.get('resource-id')==PKG+':id/'+key),None)
    def dump():
        for _ in range(5):
            tree=ET.fromstring(snapshot_client.dump())
            if node(tree,'safety_banner') is not None:return tree
            # setContentView briefly exposes an empty root during a real render.
            time.sleep(.2)
        raise AssertionError('LifeLink app not visible')
    def bounds(n):return list(map(int,re.findall(r'\d+',n.get('bounds'))))
    def scroll(tree,direction):
        if snapshot_client:
            snapshot_client.scroll(direction);return
        n=node(tree,'content_scroll');assert n is not None
        x1,y1,x2,y2=bounds(n);top,bottom=y1+100,y2-100
        start,end=(bottom,top) if direction=='down' else (top,bottom)
        adb('shell','input','swipe',str((x1+x2)//2),str(start),str((x1+x2)//2),str(end),'250')
    def find_once(key):
        tree=dump()
        directions=('up','down') if key in {'simulate_driving','confirm_parked','motion_status','coop_state','coop_metrics','coop_role_switch'} else ('down','up')
        for direction in directions:
            previous=None
            for _ in range(12):
                n=node(ET.fromstring(snapshot_client.node(key)),key) if snapshot_client else node(tree,key)
                if n is not None:
                    x1,y1,x2,y2=bounds(n)
                    minimum={'android.widget.TextView':25,'android.widget.CheckBox':50,'android.widget.Button':60}.get(n.get('class'),10)
                    if x2>x1 and y2-y1>=minimum:return tree,n
                fingerprint=[(n.get('resource-id'),n.get('bounds')) for n in tree.iter('node') if n.get('package')==PKG]
                if fingerprint==previous:break
                previous=fingerprint;scroll(tree,direction);tree=dump()
        raise AssertionError('Missing control '+key)
    def find(key):
        for attempt in range(4):
            try:return find_once(key)
            except AssertionError:
                if attempt==3:raise
                time.sleep(.25)
    def tap(key):
        end=time.monotonic()+20
        while time.monotonic()<end:
            _,n=find(key)
            if n.get('enabled')!='false':
                if snapshot_client.click(key):return
            time.sleep(.2)
        raise AssertionError('Disabled or detached '+key)
    def wait(key,value,timeout=25):
        end=time.monotonic()+timeout
        while time.monotonic()<end:
            tree,n=find(key)
            if value in n.get('text',''):return tree,n
            time.sleep(.25)
        raise AssertionError('Expected '+value+' in '+key+' / '+n.get('text',''))
    def state(value):return wait('coop_state',value)
    def capture(name):
        tree=dump()
        (OUT/(args.output_prefix+'-coop-'+name+'.xml')).write_bytes(ET.tostring(tree,encoding='utf-8',xml_declaration=True))
        (OUT/(args.output_prefix+'-coop-'+name+'.png')).write_bytes(adb('exec-out','screencap','-p',raw=True))
    def record(name,**extra):
        results.append({'check':name,'status':'PASS',**extra})
        print('PASS '+name+' '+json.dumps(extra,ensure_ascii=False),flush=True)
    def create(case='early'):
        nonlocal run_id
        tap('coop_case_'+case)
        _,consent=find('coop_consent');assert consent.get('checked')=='false'
        _,button=find('coop_create');assert button.get('enabled')=='false'
        before={r['id'] for r in api('/runs')['items']}
        tap('coop_consent');tap('coop_create');wait('coop_status','POC-')
        created=[r for r in api('/runs')['items'] if r['id'] not in before]
        assert len(created)==1,created
        run_id=created[0]['id'];return created[0]
    def invite(vehicle='DEMO-HELPER-A'):
        op('CONFIRM');op('INVITE',vehicleId=vehicle)
        state('협력 차량 초대');tap('coop_role_switch');tap('coop_accept')
        state('자발적 참여 수락');op('AUTHORIZE');state('응급협력 역할 부여')
    def approve(authority='119'):
        op('APPROVE_BEACON',authority);find('coop_activate');tap('coop_activate');wait('coop_indicator','ON')
        assert current()['beacon']['state']=='ACTIVE'
    try:
        verify_installed_apk(cmd,apk,sdk,args.expected_apk_version)
        assert 'Pleos_Connect_API_34' in adb('emu','avd','name')
        adb('reverse','tcp:8781','tcp:8781');adb('shell','am','force-stop',PKG)
        snapshot_client=SnapshotClient(cmd).start()
        adb('shell','am','start','-W','-n',PKG+'/.MainActivity')
        if args.moving_demo:
            find('open_cooperation');tap('simulate_driving')
            wait('motion_status','이동 중 응급상황');record('emergencyAvailableWithoutParkedConfirmation')
        else:tap('confirm_parked')
        tap('open_cooperation');capture('start')
        phase='nearby-volunteer'
        r=create();assert r['projection']['potentialHeadStartSeconds']==285
        eligible=[v['id'] for v in r['candidates'] if v['eligible']]
        assert eligible==['DEMO-HELPER-A','DEMO-HELPER-D']
        record('explicitConsentAndEligibleVehicleSelection',runId=run_id,eligible=eligible)
        invite();assert node(dump(),'coop_activate') is None
        wait('coop_indicator','OFF');record('voluntaryAcceptanceAndSeparateMissionApproval')
        phase='112-beacon'
        approve('112');capture('active-112')
        _,indicator=find('coop_indicator');box=tuple(bounds(indicator));samples=[]
        for _ in range(3):
            samples.append(Image.open(io.BytesIO(adb('exec-out','screencap','-p',raw=True))).convert('RGB').crop(box));time.sleep(.35)
        diff=max(sum(ImageStat.Stat(ImageChops.difference(samples[0],im)).mean) for im in samples[1:])
        (OUT/(args.output_prefix+'-coop-pulse-metadata.json')).write_text(json.dumps({'bounds':box,'pixelDifference':diff,'sampleSizes':[im.size for im in samples]}),encoding='utf-8')
        for index,sample in enumerate(samples):sample.save(OUT/(args.output_prefix+'-coop-pulse-sample-'+str(index)+'.png'))
        assert diff>.05,'Visible indicator did not animate'
        record('112BoundApprovalAndRenderedPulse',pixelDifference=diff)
        if args.moving_demo and current()['beacon']['state']=='EXPIRED':approve('112')
        if args.moving_demo:
            wait('motion_status','이동 중 응급상황');wait('coop_indicator','ON')
            tap('confirm_parked');wait('coop_indicator','ON')
            tap('simulate_driving');wait('coop_indicator','ON');capture('moving-active')
            record('movingAndParkedContextsKeepApprovedCooperationIndicator')
        else:
            tap('simulate_driving');assert node(dump(),'coop_indicator') is None
            tap('confirm_parked');wait('coop_indicator','ON');record('drivingGateHidesCooperationIndicator')
        adb('shell','am','start','-W','-a','android.settings.SETTINGS')
        activities=adb('shell','dumpsys','activity','activities')
        focused=next(line for line in activities.splitlines() if line.strip().startswith('ResumedActivity:'))
        assert 'com.android.car.settings' in focused,focused
        adb('shell','am','start','-W','-n',PKG+'/.MainActivity')
        if args.moving_demo:
            wait('motion_status','이동 중 응급상황');wait('coop_indicator','ON');capture('resume-moving')
            record('foregroundResumeRefreshesApprovalWithoutParkedConfirmation')
        else:
            assert node(dump(),'confirm_parked') is not None
            capture('resume-locked');tap('confirm_parked');wait('coop_indicator','ON')
            record('multiWindowForegroundLossRequiresParkedConfirmation')
        phase='connection-failure'
        # A real read timeout, preserving this project's synthetic records.
        pause=subprocess.Popen([sys.executable,str(ROOT/'scripts/pause-demo-server.py')],stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,encoding='utf-8',creationflags=subprocess.CREATE_NO_WINDOW if os.name=='nt' else 0)
        try:
            ready=pause.stdout.readline();assert json.loads(ready)['paused'] is True,ready
            wait('coop_indicator','OFF',timeout=25);wait('coop_status','연결')
            capture('connection-off');record('readTimeoutStopsIndicator')
        finally:
            try:pause.stdin.write('\n');pause.stdin.flush();pause.stdin.close()
            except (BrokenPipeError,OSError):pass
            pause.wait(timeout=35);assert pause.returncode==0
        tap('coop_refresh');state('응급협력 역할 부여')
        if current()['beacon']['state']=='EXPIRED':approve('112')
        wait('coop_indicator','ON');record('connectionRecoveryRetainsBoundApproval')
        op('REVOKE','112');state('기관 역할 철회');wait('coop_indicator','OFF')
        record('operatorRevokeStopsMissionAndIndicator');tap('coop_new')
        phase='controls'
        r=create('ems');assert r['projection']['potentialHeadStartSeconds']==0 and r['projection']['signedDeltaSeconds']==-75
        wait('coop_metrics','기회 가정 0분 0초');capture('ems-first')
        tap('coop_cancel');state('요청 취소');record('emsFirstControlHasZeroAssumedGain');tap('coop_new')
        r=create('none');assert not any(v['eligible'] for v in r['candidates']) and r['projection']['supportSeconds'] is None
        wait('coop_metrics','참여 차량 없음');capture('no-responder')
        tap('coop_cancel');state('요청 취소');record('noResponderControlRetainsExistingEmsScenario');tap('coop_new')
        phase='decline-withdraw'
        create();op('CONFIRM');op('INVITE',vehicleId='DEMO-HELPER-A');state('협력 차량 초대')
        tap('coop_role_switch');tap('coop_decline');state('참여 거절')
        assert not next(v for v in current()['candidates'] if v['id']=='DEMO-HELPER-A')['eligible']
        op('INVITE',vehicleId='DEMO-HELPER-D');state('협력 차량 초대');tap('coop_accept');state('자발적 참여 수락')
        op('AUTHORIZE');state('응급협력 역할 부여');approve()
        tap('coop_withdraw');state('참여 철회');wait('coop_indicator','OFF')
        record('declineAlternativeVehicleAndVoluntaryWithdrawal');tap('coop_new')
        phase='real-time-expiry-handover'
        create();invite();approve();capture('active-119')
        original=current()['beacon']['approvalId'];remaining=current()['beacon']['remainingSeconds']
        replay=op('APPROVE_BEACON');assert replay['beacon']['approvalId']==original and replay['beacon']['remainingSeconds']<=remaining
        record('approvalReplayDoesNotExtendLease')
        end=time.monotonic()+135;bucket=None
        while time.monotonic()<end:
            r=current();b=r['beacon']
            if b['state']=='EXPIRED':break
            if b['remainingSeconds']//20!=bucket:
                bucket=b['remainingSeconds']//20;print('WAIT cooperation expiry '+str(b['remainingSeconds'])+'s',flush=True)
            time.sleep(3)
        assert b['state']=='EXPIRED'
        wait('coop_indicator','OFF');assert node(dump(),'coop_activate') is None
        capture('expired');record('actual120SecondCooperationExpiry',remainingSeconds=b['remainingSeconds'])
        approve();assert current()['beacon']['approvalId']!=original
        record('newApprovalIdentityAfterExpiry')
        tap('coop_arrive');state('안전한 현장 지원 시연');op('HANDOVER');state('구급대 인계 완료');wait('coop_indicator','OFF')
        capture('handed-over');record('emsHandoverEndsTemporaryRoleAndIndicator')
        assert current()['transportAuthorized'] is False and current()['clinicalOutcomeMeasured'] is False
        tap('coop_new');capture('ready-final')
        report={'status':'PASS','at':datetime.now(timezone.utc).isoformat(),'serial':args.serial,'avd':'Pleos_Connect_API_34','apkSha256':hashlib.sha256(apk.read_bytes()).hexdigest(),'checks':results,'actualResponseTimeMeasured':False,'clinicalOutcomeMeasured':False,'physicalLampConnected':False,'realAgencyConnected':False}
        (OUT/(args.output_prefix+'-cooperation-e2e.json')).write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
        print(json.dumps(report,ensure_ascii=False),flush=True)
    except Exception as error:
        try:capture('failure')
        except Exception:pass
        failure={'status':'FAIL','phase':phase,'runId':run_id,'error':str(error),'apkSha256':hashlib.sha256(apk.read_bytes()).hexdigest(),'checks':results}
        (OUT/(args.output_prefix+'-cooperation-e2e-failure.json')).write_text(json.dumps(failure,ensure_ascii=False,indent=2),encoding='utf-8')
        print(json.dumps(failure,ensure_ascii=False),flush=True);raise
    finally:
        if snapshot_client:snapshot_client.close()
        adb('reverse','tcp:8781','tcp:8781')

if __name__=='__main__':main()
