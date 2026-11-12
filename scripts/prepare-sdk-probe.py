"""Build a separate debug-signed read-only SDK probe for the dedicated Pleos AVD.

The target APK must already be installed. No app data clearing or system changes.
The probe calls the target APK's existing gateway, not a replacement Vehicle SDK.
"""
from pathlib import Path
import argparse, os, subprocess, zipfile

ROOT=Path(__file__).resolve().parents[1]

def main():
    p=argparse.ArgumentParser();p.add_argument('--serial',default='emulator-5582')
    p.add_argument('--build-only',action='store_true')
    p.add_argument('--avd',choices=['LifeLink119_UI_20261107','LifeLink119_SDK_Compat_20261107','Pleos_Connect_API_34'],default='LifeLink119_SDK_Compat_20261107');a=p.parse_args()
    assert a.serial.startswith('emulator-')
    sdk=Path(os.environ.get('ANDROID_HOME',Path(os.environ['LOCALAPPDATA'])/'Android/Sdk'))
    adb=[str(sdk/'platform-tools/adb.exe'),'-s',a.serial]
    def output(*args):return subprocess.check_output(adb+list(args),timeout=20).decode().strip()
    if not a.build_only:
        assert output('emu','avd','name').splitlines()[0]==a.avd
        assert output('shell','getprop','sys.boot_completed')=='1'
        assert output('shell','getprop','ro.product.product.name')=='crp_avd' or output('shell','getprop','ro.product.name')=='crp_avd'
        assert output('shell','getprop','ro.hardware')=='ranchu'
    out=ROOT/'verification/pleos-sdk-v16-runtime/probe';out.mkdir(parents=True,exist_ok=True)
    classes=out/'classes';classes.mkdir(exist_ok=True)
    assert classes.resolve().is_relative_to((ROOT/'verification').resolve())
    for old_class in classes.rglob('*.class'):
        assert old_class.resolve().is_relative_to(classes.resolve())
        old_class.unlink()
    tools=sdk/'build-tools/35.0.0';android=sdk/'platforms/android-34/android.jar'
    java=Path(os.environ['JAVA_HOME'])/'bin/javac.exe'
    app_classes=ROOT/'app/build/intermediates/runtime_app_classes_jar/debug/bundleDebugClassesToRuntimeJar/classes.jar'
    assert app_classes.is_file(),'Build :app:assembleDebug first'
    cache=Path(os.environ.get('GRADLE_USER_HOME',Path.home()/'.gradle'))/'caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib'
    stdlib=next(cache.glob('2.0.21/*/kotlin-stdlib-2.0.21.jar'))
    def run(*args):subprocess.run([str(x) for x in args],check=True,timeout=180)
    run(java,'--release','8','-encoding','UTF-8','-classpath',os.pathsep.join(map(str,[android,app_classes,stdlib])),
        '-d',classes,*(ROOT/'scripts/android-sdk-probe').glob('*.java'))
    run(tools/'d8.bat','--lib',android,'--classpath',app_classes,'--classpath',stdlib,'--min-api','34','--output',out,*classes.rglob('*.class'))
    manifest='<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.lifelink119.sdkprobe"><uses-sdk android:minSdkVersion="34" android:targetSdkVersion="34"/><application android:label="Read only Pleos SDK probe" android:debuggable="true"/><instrumentation android:name=".SdkProbeInstrumentation" android:targetPackage="com.lifelink119.app"/></manifest>'
    (out/'AndroidManifest.xml').write_text(manifest,encoding='utf-8');unsigned=out/'unsigned.apk'
    run(tools/'aapt.exe','package','-f','-M',out/'AndroidManifest.xml','-I',android,'-F',unsigned)
    with zipfile.ZipFile(unsigned,'a') as z:z.write(out/'classes.dex','classes.dex')
    run(tools/'zipalign.exe','-f','4',unsigned,out/'aligned.apk')
    run(tools/'apksigner.bat','sign','--ks',Path.home()/'.android/debug.keystore','--ks-pass','pass:android','--key-pass','pass:android','--out',out/'sdk-probe.apk',out/'aligned.apk')
    if a.build_only:
        print('SDK probe built: '+str(out/'sdk-probe.apk'),flush=True);return
    user=output('shell','am','get-current-user');assert user.isdigit()
    run(*adb,'install','-r','--user',user,out/'sdk-probe.apk')
    print('Read-only SDK probe installed for user '+user,flush=True)

if __name__=='__main__':main()
