"""Bounded APK install on the named Pleos emulator; preserve app storage.

Use push + PackageManager rather than an unbounded streaming installer. No
physical devices, root, permission overrides, package removal, or data clearing.
"""
from pathlib import Path
import argparse,os,subprocess,uuid

ROOT=Path(__file__).resolve().parents[1]

def main():
    p=argparse.ArgumentParser();p.add_argument('--serial',required=True);p.add_argument('--apk',required=True);a=p.parse_args()
    assert a.serial.startswith('emulator-')
    apk=(ROOT/a.apk).resolve();allowed={(ROOT/'app/build/outputs/apk/debug/app-debug.apk').resolve(),(ROOT/'verification/pleos-sdk-v16-runtime/probe/sdk-probe.apk').resolve()}
    assert apk in allowed and apk.is_file(),'Only this project build and SDK probe are allowed'
    sdk=Path(os.environ.get('ANDROID_HOME',Path(os.environ['LOCALAPPDATA'])/'Android/Sdk'))
    adb=[str(sdk/'platform-tools/adb.exe'),'-s',a.serial]
    def run(*args,timeout=30):return subprocess.check_output(adb+list(args),stderr=subprocess.STDOUT,timeout=timeout).decode(errors='replace').strip()
    assert run('emu','avd','name').splitlines()[0]=='LifeLink119_SDK_Compat_20261107'
    assert run('shell','getprop','ro.hardware')=='ranchu' and run('shell','getprop','ro.product.name')=='crp_avd'
    user=run('shell','am','get-current-user');assert user.isdigit()
    remote='/data/local/tmp/lifelink-sdk-test-'+uuid.uuid4().hex+'.apk'
    pushed=False
    try:
        run('push',str(apk),remote,timeout=60);pushed=True
        result=run('shell','pm','install','-r','--user',user,remote,timeout=120)
        assert result.splitlines()[-1]=='Success',result
        print('Installed '+apk.name+' on '+a.serial+' for Android user '+user,flush=True)
    finally:
        if pushed:run('shell','rm','-f',remote,timeout=15)

if __name__=='__main__':main()
