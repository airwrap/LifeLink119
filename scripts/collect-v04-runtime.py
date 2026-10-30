"""Read final emulator/server state and copy the exact tested debug APK."""
import argparse
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import urllib.request
import psutil

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'verification'
p=argparse.ArgumentParser(description=__doc__);p.add_argument('--serial',required=True);args=p.parse_args()
assert args.serial.startswith('emulator-')
sdk=Path(os.environ.get('ANDROID_HOME',Path(os.environ['LOCALAPPDATA'])/'Android/Sdk'))
cmd=[str(sdk/'platform-tools/adb.exe'),'-s',args.serial]
def adb(*parts):return subprocess.check_output(cmd+list(parts),timeout=60).decode('utf-8','replace').strip()
def save(name,value):(OUT/name).write_text(json.dumps(value,ensure_ascii=False,indent=2),encoding='utf-8')
apk=ROOT/'app/build/outputs/apk/debug/app-debug.apk'
sha=hashlib.sha256(apk.read_bytes()).hexdigest()
avd=adb('emu','avd','name').splitlines()[0];assert avd=='Pleos_Connect_API_34'
user=adb('shell','am','get-current-user')
package=adb('shell','pm','path','--user',user,'com.lifelink119.app')
path=next(line.removeprefix('package:') for line in package.splitlines() if line.endswith('/base.apk'))
assert path.startswith('/data/app/') and re.fullmatch(r'[A-Za-z0-9_+~=./-]+',path)
installed=adb('shell','sha256sum',path).split()[0];assert installed==sha
config=(Path.home()/'.android/avd/Pleos_Connect_API_34.avd/config.ini').read_text(encoding='utf-8')
setting=dict(line.split('=',1) for line in config.splitlines() if '=' in line)
device={'serial':args.serial,'avd':avd,'api':adb('shell','getprop','ro.build.version.sdk'),'android':adb('shell','getprop','ro.build.version.release'),'abi':adb('shell','getprop','ro.product.cpu.abi'),'bootCompleted':adb('shell','getprop','sys.boot_completed'),'user':user,'installedApkSha256':installed,'matchesBuiltApk':True,'systemImage':setting['image.sysdir.1'],'vehicleDevice':setting['hw.device.name']}
assert device['api']=='34' and 'pleos_car_emulator' in device['systemImage']
save('v04-device.json',device)
pid=adb('shell','pidof','com.lifelink119.app')
assert pid.isdigit()
crashes=adb('logcat','-d','--pid='+pid,'-v','threadtime','-s','AndroidRuntime:E')
(OUT/'v04-android-crash.log').write_text(crashes,encoding='utf-8')
activities=adb('shell','dumpsys','activity','activities')
resumed=next(line.strip() for line in activities.splitlines() if line.strip().startswith('ResumedActivity:'))
assert 'com.lifelink119.app/.MainActivity' in resumed
helper_removed='com.lifelink119.verify' not in adb('shell','pm','list','packages','com.lifelink119.verify')
assert helper_removed,'Uninstall only the temporary verification helper after tests'
with urllib.request.urlopen('http://127.0.0.1:8781/api/demo/health',timeout=5) as r:health=json.load(r)
assert health['version']=='0.4.0'
record=json.loads((OUT/'local-console-process.json').read_text(encoding='utf-8-sig'))
server_args=psutil.Process(record['processId']).cmdline()
assert any(Path(arg).resolve()==(ROOT/'console/server.py').resolve() for arg in server_args if arg.endswith('server.py'))
delay=float(server_args[server_args.index('--read-delay')+1]) if '--read-delay' in server_args else 0
assert delay==0
emulator_args=None
for process in psutil.process_iter(['name','cmdline']):
    if process.info['name']=='qemu-system-x86_64.exe':
        argv=process.info['cmdline'] or []
        if '-avd' in argv and argv[argv.index('-avd')+1]==avd and '-port' in argv and argv[argv.index('-port')+1]==args.serial.split('-')[1]:emulator_args=argv
assert emulator_args is not None
state={'at':datetime.now(timezone.utc).isoformat(),'appPid':pid,'fatalExceptions':crashes.count('FATAL EXCEPTION'),'crashLogScope':'Current app PID after the completed suites','resumedActivity':resumed,'tunnel':adb('reverse','--list'),'verificationHelperRemoved':helper_removed,'localServer':health,'serverReadDelaySeconds':delay,'emulatorVulkanDisabledForHostGraphicsCompatibility':'-Vulkan' in emulator_args}
assert state['fatalExceptions']==0
save('v04-runtime-state.json',state)
distribution=ROOT/'artifacts/LifeLink119-v0.4.0-demo-debug.apk'
shutil.copyfile(apk,distribution)
distribution.with_suffix('.apk.sha256').write_text(sha+'  '+distribution.name+'\n',encoding='utf-8')
print(json.dumps({'device':device,'state':state,'artifact':str(distribution),'sha256':sha},ensure_ascii=False,indent=2))
