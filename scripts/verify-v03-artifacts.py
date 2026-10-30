"""Verify the v0.3 offline asset, built APK boundaries and recorded test results."""
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import xml.etree.ElementTree as ET
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'verification'
sdk = Path(os.environ.get('ANDROID_HOME', Path(os.environ['LOCALAPPDATA']) / 'Android/Sdk'))
tools = sdk / 'build-tools/35.0.0'
apk = ROOT / 'app/build/outputs/apk/debug/app-debug.apk'
release = ROOT / 'app/build/outputs/apk/release/app-release-unsigned.apk'
manual = ROOT / 'app/src/main/assets/emergency-manual.json'

def digest(file):
    return hashlib.sha256(file.read_bytes()).hexdigest()

def dump(kind, file):
    return subprocess.check_output([str(tools/'aapt.exe'),'dump',kind,str(file)],encoding='utf-8',errors='replace')

permissions = dump('permissions',apk)
uses = re.findall(r"uses-permission: name='([^']+)'",permissions)
expected = {'android.permission.INTERNET','pleos.car.permission.CAR_DOORS','pleos.car.permission.CAR_ENERGY','ai.pleos.playground.service.vehicle.VEHICLE_BINDING'}
assert set(uses) == expected, uses
release_uses = re.findall(r"uses-permission: name='([^']+)'",dump('permissions',release))
assert set(release_uses) == expected-{'android.permission.INTERNET'}, release_uses
badging = dump('badging',apk)
assert "versionName='0.3.0-demo'" in badging and "versionCode='3'" in badging
assert "sdkVersion:'28'" in badging and "targetSdkVersion:'34'" in badging
with ZipFile(apk) as archive:
    assert archive.read('assets/emergency-manual.json') == manual.read_bytes()

tests=[]
for file in (ROOT/'app/build/test-results/testDebugUnitTest').glob('TEST-*.xml'):
    suite=ET.parse(file).getroot()
    tests.append({'name':suite.get('name'),'tests':int(suite.get('tests')),'failures':int(suite.get('failures')),'errors':int(suite.get('errors'))})
assert sum(s['tests'] for s in tests)==32
assert not any(s['failures'] or s['errors'] for s in tests)
issues=ET.parse(ROOT/'app/build/reports/lint-results-debug.xml').getroot().findall('issue')
assert not issues
server_bytes=(OUT/'v03-server-tests.log').read_bytes()
server_log=server_bytes.decode('utf-16' if server_bytes.startswith((b'\xff\xfe',b'\xfe\xff')) else 'utf-8')
assert re.search(r'Ran 29 tests',server_log) and re.search(r'\bOK\b',server_log) and 'FAILED' not in server_log
runtime=json.loads((OUT/'v03-android-e2e.json').read_text(encoding='utf-8'))
assert runtime['status']=='PASS' and runtime['apkSha256']==digest(apk), 'Runtime evidence must match this APK'
assert runtime['avd']=='Pleos_Connect_API_34'
assert all(item['status']=='PASS' for item in runtime['checks'])
runtime_checks={item['check'] for item in runtime['checks'] if item['status']=='PASS'}
assert len([name for name in runtime_checks if name.startswith('offlineManual:')])==6
assert len([name for name in runtime_checks if name.startswith('scenario:')])==5
assert {'119ApprovalAndRenderedPulse','drivingGateHidesSignal','connectionFailureStopsSignal',
        'operatorRevokeStopsSignal','112ApprovalAndBackgroundResumeGate','closeCancelsSignal',
        'actual120SecondExpiry','sdkDoorAndBatteryRead','returnDuringAutoRefreshKeepsControlsEnabled'}.issubset(runtime_checks)
assert runtime['delayedRefreshRaceCheck'] is True
assert runtime['realAgencyConnected'] is False and runtime['physicalLampConnected'] is False
device=json.loads((OUT/'v03-device.json').read_text(encoding='utf-8'))
assert device['api']=='34' and device['installedApkSha256']==digest(apk), 'Installed APK differs from the tested build'
distribution=ROOT/'artifacts/LifeLink119-v0.3.0-demo-debug.apk'
assert digest(distribution)==digest(apk), 'The deliverable must match the installed and tested APK'
assert distribution.with_suffix('.apk.sha256').read_text(encoding='utf-8').startswith(digest(apk)+'  ')
sdk_read=next(item for item in runtime['checks'] if item['check']=='sdkDoorAndBatteryRead')
assert '조회 결과: READ' in sdk_read['door'] and '조회 결과: READ' in sdk_read['battery'], 'SDK reads did not succeed'
final_state=json.loads((OUT/'v03-runtime-state.json').read_text(encoding='utf-8'))
assert final_state['fatalExceptions']==0 and final_state['verificationHelperRemoved'] is True
assert final_state['serverReadDelaySeconds']==0 and final_state['localServer']['version']=='0.3.0'
signature=subprocess.check_output([str(tools/'apksigner.bat'),'verify','--verbose','--print-certs',str(apk)],encoding='utf-8',errors='replace')
assert 'CN=Android Debug' in signature
(OUT/'v03-apk-signature.log').write_text(signature,encoding='utf-8')
certificate=re.search(r'certificate SHA-256 digest: ([0-9a-f]+)',signature).group(1)
result={
    'version':'0.3.0-demo','date':'2026-10-25','minSdk':28,'targetSdk':34,
    'androidUnitTests':32,'serverTests':29,'failures':0,'lint':{'errors':0,'warnings':0},
    'debugApk':{'path':str(apk.relative_to(ROOT)),'bytes':apk.stat().st_size,'sha256':digest(apk),'permissions':uses,
        'deliverable':str(distribution.relative_to(ROOT)),
        'signatureVerified':True,'signer':'Android Debug','certificateSha256':certificate},
    'releaseApk':{'productionSigned':False,'sha256':digest(release),'permissions':release_uses},
    'manual':{'guides':6,'sha256':digest(manual),'apkContainsExactOfflineAsset':True,'clinicalReviewComplete':False},
    'signal':{'simulationOnly':True,'hardwareConnected':False,'authenticatedAgencyApproval':False,'approvalSeconds':120,'stalePreviewOffSeconds':8},
    'androidRuntimeV03':{'verified':True,'evidence':'verification/v03-android-e2e.json',
        'avd':runtime['avd'],'serial':runtime['serial'],'at':runtime['at'],
        'passedChecks':len(runtime_checks),'apkSha256':runtime['apkSha256'],
        'installedApkSha256':device['installedApkSha256'],
        'delayedAutomaticRefreshNavigationRegression':True,
        'multiWindowTopResumedGate':True,
        'baselineEvidence':runtime.get('baselineEvidence'),
        'deviceTunnel':'tcp:8781 -> host tcp:'+str(runtime['hostPort'])},
    'browserPreview':'http://127.0.0.1:8783/manual',
    'localServer':{'baseUrl':'http://127.0.0.1:8781','version':'0.3.0','oldVersionReplacedAfterZeroSessionCheck':True},
    'finalRuntimeState':final_state,
    'suites':tests,
}
(OUT/'v03-artifact-report.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps(result,ensure_ascii=False,indent=2))
