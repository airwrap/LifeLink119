"""Build separate debug-signed native View QA helper; public frames refuse FLAG_SECURE.
Reuses the verified LifeLink video capture harness without app source injection.
"""
from pathlib import Path
import os,subprocess,zipfile,argparse
parser=argparse.ArgumentParser();parser.add_argument("--serial",required=True);args=parser.parse_args()
ROOT=Path(__file__).resolve().parents[1];sdk=Path(os.environ.get('ANDROID_HOME',Path(os.environ['LOCALAPPDATA'])/'Android/Sdk'));tools=sdk/'build-tools/35.0.0';jar=sdk/'platforms/android-34/android.jar';out=ROOT/'verification/native-ui-helper-v07';out.mkdir(parents=True,exist_ok=True);classes=out/'classes';classes.mkdir(exist_ok=True)
def run(*a,**kw):subprocess.run([str(x) for x in a],check=True,**kw)
manifest='<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.lifelink119.nativeverify"><uses-sdk android:minSdkVersion="34" android:targetSdkVersion="34"/><application android:label="Native window capture"/><instrumentation android:name=".WindowInstrumentation" android:targetPackage="com.lifelink119.app"/></manifest>'
(out/'AndroidManifest.xml').write_text(manifest,encoding='utf-8')
run(Path(os.environ['JAVA_HOME'])/'bin/javac.exe','--release','8','-encoding','UTF-8','-classpath',jar,'-d',classes,ROOT/'scripts/android-native-window/WindowInstrumentation.java')
run(tools/'d8.bat','--lib',jar,'--min-api','34','--output',out,*classes.rglob('*.class'))
unsigned=out/'unsigned.apk'
if unsigned.exists():unsigned.unlink()
run(tools/'aapt.exe','package','-f','-M',out/'AndroidManifest.xml','-I',jar,'-F',unsigned)
with zipfile.ZipFile(unsigned,'a') as z:z.write(out/'classes.dex','classes.dex')
run(tools/'zipalign.exe','-f','4',unsigned,out/'aligned.apk')
run(tools/'apksigner.bat','sign','--ks',Path.home()/'.android/debug.keystore','--ks-pass','pass:android','--key-pass','pass:android','--out',out/'window-helper.apk',out/'aligned.apk')
assert args.serial.startswith('emulator-'), 'Dedicated test emulator only'
adb=sdk/'platform-tools/adb.exe'
user=subprocess.check_output([str(adb),'-s',args.serial,'shell','am','get-current-user'],timeout=15).decode().strip();assert user.isdigit()
run(adb,'-s',args.serial,'push',out/'window-helper.apk','/data/local/tmp/lifelink-native-verify-helper.apk')
subprocess.run([str(adb),'-s',args.serial,'shell','pm','install','-r','--user',user,'/data/local/tmp/lifelink-native-verify-helper.apk'],check=True,timeout=45)
