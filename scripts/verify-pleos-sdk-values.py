"""Verify genuine installed-app SDK reads against emulator-only Console fixtures.

Never selects the app's EXAMPLE branch. No physical devices, medical access,
actual dispatch or application-source changes. Fixture changes require the
official Console's live publisher; an unavailable bridge is a blocked test.
"""
from pathlib import Path
import argparse, hashlib, json, os, re, subprocess, time
from virtual_console import VirtualConsole

ROOT=Path(__file__).resolve().parents[1]

def main():
    p=argparse.ArgumentParser();p.add_argument('--serial',default='emulator-5582')
    p.add_argument('--avd',choices=['LifeLink119_SDK_Compat_20261107','LifeLink119_UI_20261107','Pleos_Connect_API_34'],default='LifeLink119_SDK_Compat_20261107')
    p.add_argument('--port',type=int,default=18088);p.add_argument('--baseline-only',action='store_true')
    p.add_argument('--out',default='verification/pleos-sdk-current');a=p.parse_args()
    assert a.serial.startswith('emulator-'),'Physical devices refused'
    sdk=Path(os.environ.get('ANDROID_HOME',Path(os.environ['LOCALAPPDATA'])/'Android/Sdk'))
    adb=[str(sdk/'platform-tools/adb.exe'),'-s',a.serial]
    def shell(*args,timeout=20):return subprocess.check_output(adb+list(args),timeout=timeout).decode(errors='replace').replace('\r','')
    assert shell('emu','avd','name').splitlines()[0]==a.avd
    assert shell('shell','getprop','ro.hardware').strip()=='ranchu'
    assert shell('shell','getprop','ro.product.name').strip()=='crp_avd'
    assert shell('shell','getprop','sys.boot_completed').strip()=='1'
    user=shell('shell','am','get-current-user').strip();assert user.isdigit()
    out=(ROOT/a.out).resolve();assert out.is_relative_to((ROOT/'verification').resolve());out.mkdir(parents=True,exist_ok=True)
    result={'status':'BLOCKED','serial':a.serial,'avd':a.avd,'source':'Actual app gateway/Vehicle SDK 2.3.2; Console inputs are simulated','runs':[],'fixtureRestore':'not needed'}
    v=None;console_started=False
    def probe(label,warmup=90):
        proc=subprocess.run(adb+['shell','am','instrument','-w','--user',user,'-e','label',label,'-e','warmupSeconds',str(warmup),'com.lifelink119.sdkprobe/.SdkProbeInstrumentation'],capture_output=True,timeout=190)
        raw=proc.stdout.decode(errors='replace')+'\n'+proc.stderr.decode(errors='replace');(out/(label+'.txt')).write_text(raw,encoding='utf-8')
        m=re.search(r'INSTRUMENTATION_RESULT: sdkProbe=(\{[^\n]+\})',raw)
        if not m:raise RuntimeError('SDK probe exit '+str(proc.returncode)+': '+raw[-1000:])
        report=json.loads(m.group(1));assert report['appVersion']==result['expectedVersion']
        (out/(label+'.json')).write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
        result['runs'].append(report);print(label,[(r['metric'],r['status'],r['rawValue']) for r in report['values']],flush=True)
        return report
    def readings(report):return {r['metric']:r for r in report['values']}
    def compare(report,door,soc):
        data=readings(report)
        for key,value in [('DRIVER_DOOR',1 if door else 0),('BATTERY_SOC',soc)]:
            assert data[key]['status']=='READ' and data[key]['rawValue']==value,(key,value,data[key])
    try:
        apk=ROOT/'app/build/outputs/apk/debug/app-debug.apk'
        badging=subprocess.check_output([str(sdk/'build-tools/35.0.0/aapt.exe'),'dump','badging',str(apk)],timeout=20).decode(errors='replace')
        result['expectedVersion']=re.search(r"versionName='([^']+)'",badging).group(1)
        result['expectedApkSha256']=hashlib.sha256(apk.read_bytes()).hexdigest()
        # Verify bytes read from PackageManager's actual installed base APK.
        pm=shell('shell','pm','path','--user',user,'com.lifelink119.app').strip().splitlines();base=next(x[8:] for x in pm if x.startswith('package:') and x.endswith('/base.apk'))
        installed=out/'installed-base.apk';subprocess.run(adb+['pull',base,str(installed)],check=True,capture_output=True,timeout=35)
        result['installedApkSha256']=hashlib.sha256(installed.read_bytes()).hexdigest()
        assert result['installedApkSha256']==result['expectedApkSha256'],'Installed APK differs from tested source build'
        v=VirtualConsole(a.serial,a.avd,a.port);v.__enter__();console_started=True
        # A cold-booted image can expose getters before all Console DDS
        # publishers are ready. Wait for each required producer, not just the
        # browser lease; never replace missing SDK data with a fixture result.
        deadline=time.monotonic()+120
        before=v.state()
        def ready(state):return state['controls']['emulator_active'] and state['gear']['bridge_ready'] and state['ev_battery']['bridge_ready']
        if not ready(before):print('Waiting for official Console producers (up to 120 seconds)',flush=True)
        while not ready(before) and time.monotonic()<deadline:
            time.sleep(.5);before=v.state()
        if before['gear']['bridge_ready'] and before['controls']['emulator_active'] and not a.baseline_only:v.park()
        baseline=probe('baseline')
        if not baseline['sdkReadable']:raise RuntimeError('Actual SDK values are unavailable; no example fallback')
        result['baselineRead']='PASS'
        if a.baseline_only:
            result['status']='BASELINE_READ_VERIFIED';return
        before=v.state();(out/'console-before.json').write_text(json.dumps(before,indent=2),encoding='utf-8')
        if not before['controls']['emulator_active'] or not before['gear']['bridge_ready'] or not before['ev_battery']['bridge_ready']:
            result['status']='BASELINE_READ_VERIFIED_CONSOLE_BLOCKED';result['reason']='Official Console emulator_active/publisher bridge is false; no fixture values written';raise SystemExit(2)
        old_door=before['body']['doors']['front_left'];old_soc=before['ev_battery']['state_of_charge']
        assert type(old_door) is bool and float(old_soc).is_integer() and 0<=old_soc<=100
        assert readings(baseline)['DRIVER_DOOR']['minimum']==0 and readings(baseline)['DRIVER_DOOR']['maximum']==1
        try:
            for label,door,soc in [('door_open_soc80',True,80),('door_closed_soc15',False,15)]:
                v.driver_door(door);v.soc(soc);time.sleep(2)
                state=v.state();(out/(label+'-console.json')).write_text(json.dumps(state,indent=2),encoding='utf-8')
                assert state['body']['doors']['front_left']==door and state['ev_battery']['state_of_charge']==soc
                compare(probe(label),door,soc)
        finally:
            # Only the dedicated virtual fixture is touched. Preserve actual prior values.
            try:
                v.driver_door(old_door);v.soc(int(old_soc));v.park()
                compare(probe('restored'),old_door,int(old_soc));result['fixtureRestore']='PASS'
            except Exception as e:result['fixtureRestore']='FAILED: '+repr(e);raise
        result['status']='SDK_VALUE_CHANGES_VERIFIED'
    except Exception as e:
        result['status']='FAILED' if result['runs'] else 'BLOCKED';result['reason']=repr(e);print(result['status'],repr(e),flush=True)
    finally:
        if console_started:
            try:v.close()
            except Exception as e:result['consoleClose']=repr(e)
        (out/'results.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
        print('Result',result['status'],flush=True)
    if result['status'] not in {'SDK_VALUE_CHANGES_VERIFIED','BASELINE_READ_VERIFIED'}:raise SystemExit(2)

if __name__=='__main__':main()
