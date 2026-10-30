"""Build a local-only instrumentation helper for animated-screen verification.

Requires the project's JDK 17, Android API 34 SDK, build-tools 35.0.0, and an
existing Android debug keystore. The private key is never copied or exported.
"""
import argparse
import os
from pathlib import Path
import subprocess

ROOT=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser(description=__doc__)
p.add_argument('--serial',required=True)
args=p.parse_args()
assert args.serial.startswith('emulator-'), 'Use a dedicated test emulator'
sdk=Path(os.environ.get('ANDROID_HOME',Path(os.environ['LOCALAPPDATA'])/'Android/Sdk'))
jdk=Path(os.environ['JAVA_HOME'])
tools=sdk/'build-tools/35.0.0'
source=ROOT/'scripts/android-ui-snapshot'
output=ROOT/'verification/ui-snapshot-helper'
classes=output/'classes'
classes.mkdir(parents=True,exist_ok=True)
platform=sdk/'platforms/android-34/android.jar'
key=Path.home()/'.android/debug.keystore'
assert key.is_file(), 'Build the app debug APK once to create the standard debug keystore'

def run(*args,cwd=None):subprocess.run([str(arg) for arg in args],cwd=cwd,check=True)

run(jdk/'bin/javac.exe','-encoding','UTF-8','--release','8','-classpath',platform,
    '-d',classes,source/'SnapshotInstrumentation.java')
compiled=classes/'com/lifelink119/verify/SnapshotInstrumentation.class'
run(tools/'d8.bat','--lib',platform,'--min-api','28','--output',output,compiled)
unsigned=output/'unsigned.apk'
if unsigned.exists():unsigned.unlink()
run(tools/'aapt.exe','package','-M',source/'AndroidManifest.xml','-I',platform,'-F',unsigned)
run(tools/'aapt.exe','add',unsigned,'classes.dex',cwd=output)
run(tools/'zipalign.exe','-f','4',unsigned,output/'aligned.apk')
apk=output/'lifelink-ui-snapshot.apk'
run(tools/'apksigner.bat','sign','--ks',key,'--ks-pass','pass:android','--key-pass','pass:android','--out',apk,output/'aligned.apk')
run(tools/'apksigner.bat','verify',apk)
run(sdk/'platform-tools/adb.exe','-s',args.serial,'install','--no-streaming','-r',apk)
print('Snapshot helper ready. It has no activity and does not restart LifeLink 119.')
