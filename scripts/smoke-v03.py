"""Exercise the installed APK on a dedicated Pleos AVD and a synthetic loopback server.

Only creates and closes its own fake sessions. Keeps other servers/devices intact.
Both device and host must use port 8781: the server validates the HTTP Host port.
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
import urllib.error
import urllib.request
import xml.etree.ElementTree as ET
from PIL import Image, ImageChops, ImageStat
from ui_snapshot_client import SnapshotClient, verify_installed_apk

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'verification'
PKG='com.lifelink119.app'


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--serial',required=True)
    p.add_argument('--port',type=int,default=8781)
    p.add_argument('--expected-version',default='0.3.0')
    p.add_argument('--expected-apk-version',default='0.3.0-demo')
    p.add_argument('--output-prefix',default='v03')
    p.add_argument('--persistent-ui',action='store_true',help='Reuse one bounded ADB-only accessibility helper session')
    p.add_argument('--moving-demo',action='store_true',help='Verify assistance remains available in all motion contexts (v0.4.1+)')
    p.add_argument('--expiry-check',action='store_true')
    p.add_argument('--race-check',action='store_true',help='Requires the test server to use --read-delay 1')
    p.add_argument('--baseline',type=Path,help='Reuse completed basic checks from a matching-APK partial report')
    p.add_argument('--resume-safety-complete',action='store_true',help='Reuse the 19 completed checks; run actual expiry and SDK checks')
    args=p.parse_args()
    assert re.fullmatch(r'[a-z0-9-]{3,40}',args.output_prefix), 'Safe artifact prefix required'
    assert args.serial.startswith('emulator-')
    assert args.port==8781, 'The APK uses localhost:8781; keep device and host ports equal for Host validation'
    sdk=Path(os.environ.get('ANDROID_HOME',Path(os.environ['LOCALAPPDATA'])/'Android/Sdk'))
    cmd=[str(sdk/'platform-tools/adb.exe'),'-s',args.serial]
    apk=ROOT/'app/build/outputs/apk/debug/app-debug.apk'
    results=[]
    baseline=None
    if args.baseline:
        baseline_path=args.baseline.resolve()
        assert baseline_path.parent==OUT.resolve()
        baseline=json.loads(baseline_path.read_text(encoding='utf-8'))
        assert baseline['status']=='PARTIAL' and baseline['apkSha256']==hashlib.sha256(apk.read_bytes()).hexdigest()
        results=list(baseline['checks'])
        completed={item['check'] for item in results}
        expected_basics={'appLaunchWithoutParkedGate' if args.moving_demo else 'appLaunchAndParkedGate'}
        if not args.moving_demo or 'returnDuringAutoRefreshKeepsControlsEnabled' in completed:
            expected_basics.add('returnDuringAutoRefreshKeepsControlsEnabled')
        if args.moving_demo and '119ApprovalAndRenderedPulse' in completed:
            expected_basics.add('119ApprovalAndRenderedPulse')
        expected_basics.update('offlineManual:'+key for key in ('first','adult-cpr','aed','bleeding','burn','seizure'))
        expected_basics.update('scenario:'+key for key in ('MEDICAL','VIDEO','TRANSPORT','POLICE','WELLBEING'))
        if args.resume_safety_complete:
            assert args.expiry_check or args.moving_demo
            expected_basics.update({'119ApprovalAndRenderedPulse','movingContextKeepsApprovedSignal' if args.moving_demo else 'drivingGateHidesSignal','connectionFailureStopsSignal','operatorRevokeStopsSignal','112ApprovalAndBackgroundRefresh' if args.moving_demo else '112ApprovalAndBackgroundResumeGate','closeCancelsSignal'})
        assert {item['check'] for item in results}==expected_basics and all(item['status']=='PASS' for item in results)
    assert not args.resume_safety_complete or baseline is not None
    phase='startup';snapshot_client=None

    def adb(*parts,raw=False,timeout=60):
        result=subprocess.check_output(cmd+list(parts),timeout=timeout)
        return result if raw else result.decode('utf-8','replace').strip()

    def api(path,body=None,expected=200):
        data=json.dumps(body).encode('utf-8') if body is not None else None
        req=urllib.request.Request(f'http://127.0.0.1:{args.port}/api/demo'+path,data=data,headers={'Content-Type':'application/json'})
        try:
            with urllib.request.urlopen(req,timeout=6) as r:
                assert r.status==expected,(path,r.status)
                return json.load(r)
        except urllib.error.HTTPError as e:
            if e.code!=expected:raise
            return json.load(e)

    def dump(banner=True):
        last=''
        for _ in range(5):
            # The standard uiautomator CLI waits for idle and stalls on the pulse.
            # This separate helper reads the public accessibility API immediately.
            # Its instrumentation targets itself, preserving the running app state.
            output=('INSTRUMENTATION_RESULT: hierarchy='+snapshot_client.dump()) if snapshot_client else adb('shell','am','instrument','-w','com.lifelink119.verify/.SnapshotInstrumentation')
            marker='INSTRUMENTATION_RESULT: hierarchy='
            if marker not in output:
                last=output;time.sleep(.2);continue
            last=output.split(marker,1)[1].split('\nINSTRUMENTATION_CODE:',1)[0].strip()
            try:
                tree=ET.fromstring(last)
                if banner:assert node(tree,'safety_banner') is not None and node(tree,'content_scroll') is not None,'LifeLink page is between layouts'
                return tree
            except (ET.ParseError,AssertionError):time.sleep(.3)
        raise AssertionError('Invalid UI hierarchy: '+last[:100])

    def node(tree,key):
        return next((n for n in tree.iter('node') if n.get('resource-id')==PKG+':id/'+key),None)

    def bounds(n):return list(map(int,re.findall(r'\d+',n.get('bounds'))))

    def text(tree):return '\n'.join(n.get('text','') for n in tree.iter('node'))

    def scroll(tree,direction):
        if snapshot_client:
            snapshot_client.scroll(direction);return
        view=next((n for n in tree.iter('node') if n.get('class')=='android.widget.ScrollView' and n.get('package')==PKG),None)
        assert view is not None,'No LifeLink ScrollView'
        x1,y1,x2,y2=bounds(view)
        top,bottom=y1+100,y2-100
        start,end=(bottom,top) if direction=='down' else (top,bottom)
        adb('shell','input','swipe',str((x1+x2)//2),str(start),str((x1+x2)//2),str(end),'280')

    def find_once(key):
        tree=dump()
        directions=('up','down') if key in {'simulate_driving','confirm_parked','motion_status','back_button'} else ('down','up')
        for direction in directions:
            previous=None
            for _ in range(9):
                target=node(ET.fromstring(snapshot_client.node(key)),key) if snapshot_client else node(tree,key)
                if target is not None:
                    x1,y1,x2,y2=bounds(target)
                    minimum=65 if target.get('class') in {'android.widget.Button','android.widget.CheckBox'} and key!='back_button' else 10
                    if x2>x1 and y2-y1>=minimum:return tree,target
                # The countdown changes text, so compare scroll bounds/control ids rather than all text.
                fingerprint=[(n.get('resource-id'),n.get('bounds')) for n in tree.iter('node') if n.get('package')==PKG]
                if fingerprint==previous:break
                previous=fingerprint
                scroll(tree,direction);tree=dump()
        raise AssertionError('Missing control '+key+' / '+text(tree)[-700:])

    def find(key):
        for attempt in range(4):
            try:return find_once(key)
            except AssertionError:
                if attempt==3:raise
                time.sleep(.25)

    def tap(key):
        end=time.monotonic()+20
        while time.monotonic()<end:
            tree,n=find(key)
            if n.get('enabled')!='false':
                if key=='connect_demo':
                    x1,y1,x2,y2=bounds(n)
                    adb('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2))
                    time.sleep(.35);return tree
                if snapshot_client:
                    if snapshot_client.click(key):return tree
                    time.sleep(.2);continue
                output=adb('shell','am','instrument','-w','-e','click',PKG+':id/'+key,'com.lifelink119.verify/.SnapshotInstrumentation')
                if 'INSTRUMENTATION_RESULT: clicked=true' in output:return tree
            time.sleep(.2)
        raise AssertionError('Control could not be activated: '+key)

    def wait_text(key,expected,timeout=30):
        end=time.monotonic()+timeout
        while time.monotonic()<end:
            tree,n=find(key)
            if expected in n.get('text',''):return tree,n
            time.sleep(.25)
        raise AssertionError('Expected '+expected+' in '+key+' / '+n.get('text',''))

    def wait_signal(ticket,state,timeout=18):
        end=time.monotonic()+timeout
        while time.monotonic()<end:
            item=api('/sessions/'+ticket)
            if item['signalPreview']['state']==state:return item
            time.sleep(.2)
        raise AssertionError('Expected server signal '+state)

    def capture(name):
        if name.startswith('v03-'):name=args.output_prefix+name[3:]
        tree=dump()
        (OUT/(name+'.xml')).write_bytes(ET.tostring(tree,encoding='utf-8',xml_declaration=True))
        (OUT/(name+'.png')).write_bytes(adb('exec-out','screencap','-p',raw=True))

    def record(name,**data):
        results.append({'check':name,'status':'PASS',**data})
        print('PASS '+name+' '+json.dumps(data,ensure_ascii=False),flush=True)

    def create_session(scenario='MEDICAL',share_all=False):
        tap('scenario_'+scenario.lower());tap('next_step')
        for key in ('share_vehicle','share_location'):
            _,n=find(key);assert n.get('checked')=='false','Consent default not OFF'
        if scenario=='VIDEO':
            _,n=find('share_video');assert n.get('checked')=='false'
        if share_all:
            for key in ('share_vehicle','share_location','share_video'):tap(key)
        before={s['id'] for s in api('/sessions')['items']}
        tap('connect_demo')
        find('session_ticket')
        assert {s['id'] for s in api('/sessions')['items']}==before,'Implicit server share'
        tap('share_console');wait_text('console_result','DEMO-')
        new=[s for s in api('/sessions')['items'] if s['id'] not in before]
        assert len(new)==1,new
        return new[0]['id']

    def review(ticket):
        current=api('/sessions/'+ticket)
        if current['status']=='DEMO_CONNECTED':api('/sessions/'+ticket+'/actions',{'syntheticOnly':True,'action':'ACKNOWLEDGE'})
        if api('/sessions/'+ticket)['status']=='DEMO_RECEIVED':api('/sessions/'+ticket+'/actions',{'syntheticOnly':True,'action':'REVIEW'})

    def approve(ticket,authority):
        tap('signal_request');wait_signal(ticket,'REQUESTED')
        wait_text('signal_status','승인 요청 대기')
        assert node(dump(),'signal_activate') is None,'Activation offered before approval'
        api('/sessions/'+ticket+'/signal/passenger',{'syntheticOnly':True,'action':'APPROVE'},expected=422)
        api('/sessions/'+ticket+'/signal/operator',{'syntheticOnly':True,'action':'CONFIRM','authority':'109'},expected=422)
        review(ticket)
        for action in ('CONFIRM','APPROVE'):
            api('/sessions/'+ticket+'/signal/operator',{'syntheticOnly':True,'action':action,'authority':authority})
        wait_text('signal_status','표시 승인 (모의)')
        tap('signal_activate');wait_text('signal_indicator','ON')
        item=wait_signal(ticket,'PREVIEW_ACTIVE')
        assert item['signalPreview']['authority']==authority
        assert item['signalPreview']['hardwareConnected'] is False
        return item

    try:
        health=api('/health');assert health['mode']=='DEMO' and health['version']==args.expected_version
        assert 'Pleos_Connect_API_34' in adb('emu','avd','name')
        verify_installed_apk(cmd,apk,sdk,args.expected_apk_version)
        assert 'package:com.lifelink119.verify' in adb('shell','pm','list','packages','com.lifelink119.verify'), 'Run scripts/prepare-ui-snapshot.py --serial '+args.serial
        adb('reverse','tcp:8781','tcp:'+str(args.port))
        adb('shell','am','force-stop',PKG)
        if args.persistent_ui:snapshot_client=SnapshotClient(cmd).start()
        adb('shell','am','start','-W','-n',PKG+'/.MainActivity')
        if args.moving_demo:
            find('scenario_medical')
            if not baseline:capture('v03-android-initial')
            tap('simulate_driving');wait_text('motion_status','이동 중 응급상황')
            if not baseline:capture('v03-android-home')
        else:
            tree=dump();assert node(tree,'confirm_parked') is not None
            capture('v03-android-locked');tap('confirm_parked');capture('v03-android-home')
        if not baseline:record('appLaunchWithoutParkedGate' if args.moving_demo else 'appLaunchAndParkedGate',serial=args.serial)

        if not baseline:
            phase='offline-manual'
            adb('reverse','--remove','tcp:8781')
            assert 'tcp:8781' not in adb('reverse','--list')
            before=len(api('/sessions')['items'])
            tap('open_manual')
            assert '의료진 검토 전' in text(dump())
            manual=json.loads((ROOT/'app/src/main/assets/emergency-manual.json').read_text(encoding='utf-8'))
            ids=['manual_first','manual_cpr','manual_aed','manual_bleeding','manual_burn','manual_seizure']
            for key,guide in zip(ids,manual['guides']):
                tap(key);wait_text('manual_title',guide['title'])
                # All clinical wording comes from the packaged local asset.
                tree=dump();assert guide['audience'] in text(tree)
                if key=='manual_cpr':capture('v03-android-manual-cpr')
                tap('back_button')
                record('offlineManual:'+guide['id'],title=guide['title'])
            assert len(api('/sessions')['items'])==before
            tap('back_button');adb('reverse','tcp:8781','tcp:'+str(args.port))

            phase='five-scenario-regression'
            for scenario in ('MEDICAL','VIDEO','TRANSPORT','POLICE','WELLBEING'):
                ticket=create_session(scenario,share_all=scenario=='VIDEO')
                item=api('/sessions/'+ticket);assert item['scenario']==scenario
                if scenario=='VIDEO':
                    assert all(item['consent'].values())
                    for action in ('ACKNOWLEDGE','REVIEW','GUIDANCE'):api('/sessions/'+ticket+'/actions',{'syntheticOnly':True,'action':action})
                    wait_text('console_result','모의 안내');tap('view_route');find('route_preview');capture('v03-android-route')
                    api('/sessions/'+ticket+'/revoke',{'syntheticOnly':True})
                    wait_text('return_session','모의 상담으로 돌아가기')
                    end=time.monotonic()+20
                    while node(dump(),'route_preview') is not None:
                        assert time.monotonic()<end,'Route survived consent revocation'
                    if args.race_check:
                        # Reproduce the disabled-controls bug: navigate during an
                        # unchanged automatic GET, then let that GET finish.
                        _,button=find('return_session');x1,y1,x2,y2=bounds(button)
                        process_id=adb('shell','pidof',PKG)
                        def latest_poll():
                            lines=adb('logcat','-d','--pid='+process_id,'-t','100','-v','epoch','-s','LifeLinkConsole:D').splitlines()
                            return next((line for line in reversed(lines) if 'poll_start' in line),None)
                        previous=latest_poll();until=time.monotonic()+12
                        while latest_poll()==previous:
                            assert time.monotonic()<until,'Automatic refresh did not run'
                            time.sleep(.1)
                        adb('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2))
                        time.sleep(1.3)
                        for key in ('refresh_console','close_session'):
                            _,control=find(key);assert control.get('enabled')=='true',key+' stayed disabled after automatic refresh'
                        record('returnDuringAutoRefreshKeepsControlsEnabled',serverReadDelaySeconds=1)
                    else:tap('return_session')
                else:assert not any(item['consent'].values())
                tap('close_session');wait_text('console_result','모의 종료')
                assert api('/sessions/'+ticket)['status']=='DEMO_CLOSED'
                tap('home_button');record('scenario:'+scenario,ticket=ticket)

        if not args.resume_safety_complete:
            phase='119-signal'
            ticket=create_session();approve(ticket,'119')
            # Sample the indicator's actual rendered alpha, not only the state label.
            if '119ApprovalAndRenderedPulse' not in {item['check'] for item in results}:
                capture('v03-android-signal-119')
                _,indicator=find('signal_indicator');box=tuple(bounds(indicator))
                samples=[]
                for _ in range(3):
                    samples.append(Image.open(io.BytesIO(adb('exec-out','screencap','-p',raw=True))).convert('RGB').crop(box))
                    time.sleep(.35)
                difference=max(sum(ImageStat.Stat(ImageChops.difference(samples[0],im)).mean) for im in samples[1:])
                assert difference>.05,'Indicator did not visibly animate'
                record('119ApprovalAndRenderedPulse',pixelDifference=difference)
                if api('/sessions/'+ticket)['signalPreview']['state']=='EXPIRED':approve(ticket,'119')
            if args.moving_demo:
                wait_text('motion_status','이동 중 응급상황');wait_text('signal_indicator','ON')
                tap('confirm_parked');wait_text('signal_indicator','ON')
                tap('simulate_driving');wait_text('signal_indicator','ON');capture('v03-android-moving-signal')
                record('movingContextKeepsApprovedSignal')
            else:
                tap('simulate_driving');assert node(dump(),'signal_indicator') is None
                tap('confirm_parked');wait_text('signal_indicator','ON');record('drivingGateHidesSignal')
            # Removing ADB reverse leaves established HTTP keep-alive sockets alive.
            # Pause the owned synthetic server instead: the app must really time out.
            pause=subprocess.Popen([sys.executable,str(ROOT/'scripts/pause-demo-server.py')],
                stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,
                text=True,encoding='utf-8',creationflags=subprocess.CREATE_NO_WINDOW if os.name=='nt' else 0)
            try:
                ready=pause.stdout.readline();assert json.loads(ready)['paused'] is True,ready
                wait_text('signal_indicator','OFF',timeout=25);wait_text('console_result','연결하지 못했습니다')
                capture('v03-android-signal-offline');record('connectionFailureStopsSignal',fault='Owned demo server paused; actual HTTP read timeout')
            finally:
                try:pause.stdin.write('\n');pause.stdin.flush();pause.stdin.close()
                except (BrokenPipeError,OSError):pass
                pause.wait(timeout=35)
                assert pause.returncode==0,'Server pause helper failed to resume'
            tap('refresh_console');wait_text('signal_indicator','ON')
            api('/sessions/'+ticket+'/signal/operator',{'syntheticOnly':True,'action':'REVOKE','authority':'119'})
            wait_text('signal_status','승인 철회');wait_text('signal_indicator','OFF');capture('v03-android-signal-revoked')
            record('operatorRevokeStopsSignal')

            phase='112-signal-and-background'
            approve(ticket,'112');capture('v03-android-signal-112')
            # Pleos consumes HOME without switching this app's task area. Launch a
            # different actual activity and verify that it becomes globally resumed.
            adb('shell','am','start','-W','-a','android.settings.SETTINGS')
            time.sleep(.5)
            activities=adb('shell','dumpsys','activity','activities')
            focused=next(line for line in activities.splitlines() if line.strip().startswith('ResumedActivity:'))
            assert 'com.android.car.settings' in focused,focused
            adb('shell','am','start','-W','-n',PKG+'/.MainActivity')
            if args.moving_demo:
                wait_text('motion_status','이동 중 응급상황');wait_text('signal_indicator','ON');capture('v03-android-resume-moving')
                record('112ApprovalAndBackgroundRefresh')
            else:
                tree=dump();assert node(tree,'confirm_parked') is not None and node(tree,'signal_indicator') is None
                capture('v03-android-resume-locked');tap('confirm_parked');wait_text('signal_indicator','ON')
                record('112ApprovalAndBackgroundResumeGate')
            tap('close_session');wait_text('console_result','모의 종료');wait_text('signal_indicator','OFF')
            closed=api('/sessions/'+ticket)
            assert closed['status']=='DEMO_CLOSED'
            assert closed['signalPreview']['state'] in {'REVOKED','EXPIRED','OFF'}
            assert closed['signalPreview']['previewEnabled'] is False and closed['signalPreview']['remainingSeconds']==0
            record('closeCancelsSignal');tap('home_button')

        if args.expiry_check:
            phase='actual-120-second-expiry'
            ticket=create_session();approve(ticket,'119')
            end=time.monotonic()+135;last_bucket=None
            while time.monotonic()<end:
                item=api('/sessions/'+ticket);signal=item['signalPreview']
                if signal['state']=='EXPIRED':break
                bucket=signal['remainingSeconds']//20
                if bucket!=last_bucket:
                    print('WAIT approval expiry '+str(signal['remainingSeconds'])+'s',flush=True);last_bucket=bucket
                time.sleep(3)
            assert signal['state']=='EXPIRED','Real-time approval did not expire'
            wait_text('signal_status','승인 만료');wait_text('signal_indicator','OFF')
            assert node(dump(),'signal_activate') is None
            capture('v03-android-signal-expired');record('actual120SecondExpiry',remainingSeconds=signal['remainingSeconds'])
            tap('close_session');wait_text('console_result','모의 종료');tap('home_button')

        phase='sdk-read'
        tap('sdk_diagnostic');tap('read_driver_door');_,door=wait_text('diagnostic_result','조회 결과:')
        tap('read_battery');_,battery=wait_text('diagnostic_result','배터리 조회 결과:')
        capture('v03-android-sdk');record('sdkDoorAndBatteryRead',door=door.get('text'),battery=battery.get('text'))
        tap('back_button');capture('v03-android-home-final')
        report={'status':'PASS','at':datetime.now(timezone.utc).isoformat(),'serial':args.serial,'avd':'Pleos_Connect_API_34','hostPort':args.port,
                'uiSnapshot':'Separate self-targeted instrumentation using the public accessibility API; animations remain enabled',
                'delayedRefreshRaceCheck':args.race_check,
                'baselineEvidence':str(args.baseline) if args.baseline else None,
                'apkSha256':hashlib.sha256(apk.read_bytes()).hexdigest(),'checks':results,'realAgencyConnected':False,'physicalLampConnected':False}
        (OUT/(args.output_prefix+'-android-e2e.json')).write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
        print(json.dumps(report,ensure_ascii=False),flush=True)
    except Exception as error:
        try:capture('v03-android-failure')
        except Exception:pass
        failure={'status':'FAIL','phase':phase,'error':str(error),'apkSha256':hashlib.sha256(apk.read_bytes()).hexdigest(),'checks':results}
        (OUT/(args.output_prefix+'-android-e2e-failure.json')).write_text(json.dumps(failure,ensure_ascii=False,indent=2),encoding='utf-8')
        print(json.dumps(failure,ensure_ascii=False),flush=True)
        raise
    finally:
        if snapshot_client:snapshot_client.close()
        adb('reverse','tcp:8781','tcp:'+str(args.port))


if __name__=='__main__':main()
