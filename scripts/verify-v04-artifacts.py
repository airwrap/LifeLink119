"""Tie v0.4 deliverables to the installed APK and completed test evidence."""
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import xml.etree.ElementTree as ET
from zipfile import ZipFile

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'verification'
sdk=Path(os.environ.get('ANDROID_HOME',Path(os.environ['LOCALAPPDATA'])/'Android/Sdk'))
tools=sdk/'build-tools/35.0.0'
apk=ROOT/'app/build/outputs/apk/debug/app-debug.apk'
release=ROOT/'app/build/outputs/apk/release/app-release-unsigned.apk'
distribution=ROOT/'artifacts/LifeLink119-v0.4.0-demo-debug.apk'
def digest(file):return hashlib.sha256(file.read_bytes()).hexdigest()
def read(file):return json.loads((OUT/file).read_text(encoding='utf-8'))
def dump(kind,file):return subprocess.check_output([str(tools/'aapt.exe'),'dump',kind,str(file)],encoding='utf-8',errors='replace')
def log(file):
    data=(OUT/file).read_bytes()
    return data.decode('utf-16' if data.startswith((b'\xff\xfe',b'\xfe\xff')) else 'utf-8-sig')

assert 'BUILD SUCCESSFUL' in log('v04-build-unit-lint.log')
badging=dump('badging',apk)
assert "versionName='0.4.0-demo'" in badging and "versionCode='4'" in badging
assert "sdkVersion:'28'" in badging and "targetSdkVersion:'34'" in badging
expected={'android.permission.INTERNET','pleos.car.permission.CAR_DOORS','pleos.car.permission.CAR_ENERGY','ai.pleos.playground.service.vehicle.VEHICLE_BINDING'}
uses=re.findall(r"uses-permission: name='([^']+)'",dump('permissions',apk))
release_uses=re.findall(r"uses-permission: name='([^']+)'",dump('permissions',release))
assert set(uses)==expected and set(release_uses)==expected-{'android.permission.INTERNET'}
assets={}
with ZipFile(apk) as archive:
    for name in ('emergency-manual.json','cooperation-catalog.json'):
        file=ROOT/'app/src/main/assets'/name
        assert archive.read('assets/'+name)==file.read_bytes()
        assets[name]={'sha256':digest(file),'apkContainsExactOfflineAsset':True}
suites=[]
for file in (ROOT/'app/build/test-results/testDebugUnitTest').glob('TEST-*.xml'):
    s=ET.parse(file).getroot()
    suites.append({'name':s.get('name'),'tests':int(s.get('tests')),'failures':int(s.get('failures')),'errors':int(s.get('errors'))})
assert sum(s['tests'] for s in suites)==38 and not any(s['failures'] or s['errors'] for s in suites)
assert not ET.parse(ROOT/'app/build/reports/lint-results-debug.xml').getroot().findall('issue')
server=log('v04-server-tests.log');assert re.search(r'Ran 50 tests',server) and re.search(r'\bOK\b',server) and 'FAILED' not in server
regression=read('v04-regression-android-e2e.json')
cooperation=read('v04-cooperation-e2e.json')
for report in (regression,cooperation):
    assert report['status']=='PASS' and report['apkSha256']==digest(apk)
    assert report['avd']=='Pleos_Connect_API_34' and all(c['status']=='PASS' for c in report['checks'])
    assert report['realAgencyConnected'] is False and report['physicalLampConnected'] is False
legacy={c['check'] for c in regression['checks']}
assert len(legacy)==21 and regression['delayedRefreshRaceCheck'] is True
assert {'returnDuringAutoRefreshKeepsControlsEnabled','actual120SecondExpiry','112ApprovalAndBackgroundResumeGate','connectionFailureStopsSignal','sdkDoorAndBatteryRead'}.issubset(legacy)
new={c['check'] for c in cooperation['checks']}
assert len(new)==15
assert {'actual120SecondCooperationExpiry','newApprovalIdentityAfterExpiry','112BoundApprovalAndRenderedPulse','emsHandoverEndsTemporaryRoleAndIndicator','readTimeoutStopsIndicator','declineAlternativeVehicleAndVoluntaryWithdrawal'}.issubset(new)
device=read('v04-device.json');assert device['api']=='34' and device['installedApkSha256']==digest(apk)
assert digest(distribution)==digest(apk)
assert distribution.with_suffix('.apk.sha256').read_text(encoding='utf-8').startswith(digest(apk)+'  ')
sdk_read=next(c for c in regression['checks'] if c['check']=='sdkDoorAndBatteryRead')
assert re.search(r'조회 결과: READ(?:\s|$)',sdk_read['door']) and re.search(r'조회 결과: READ(?:\s|$)',sdk_read['battery'])
state=read('v04-runtime-state.json')
assert state['fatalExceptions']==0 and state['verificationHelperRemoved'] is True
assert state['serverReadDelaySeconds']==0 and state['localServer']['version']=='0.4.0'
export=read('v04-web-experiment-export.json')
assert export['run']['state']=='HANDED_OVER' and export['run']['beacon']['previewEnabled'] is False
assert export['run']['projection']['actualResponseTimeMeasured'] is False and export['run']['clinicalOutcomeMeasured'] is False
web=read('v04-web-checks.json');assert web['status']=='PASS' and all(c['status']=='PASS' for c in web['checks'])
signature=subprocess.check_output([str(tools/'apksigner.bat'),'verify','--verbose','--print-certs',str(apk)],encoding='utf-8',errors='replace')
assert 'CN=Android Debug' in signature
(OUT/'v04-apk-signature.log').write_text(signature,encoding='utf-8')
result={
    'version':'0.4.0-demo','date':'2026-10-25','minSdk':28,'targetSdk':34,
    'androidUnitTests':38,'serverTests':50,'failures':0,'lint':{'errors':0,'warnings':0},
    'debugApk':{'deliverable':str(distribution.relative_to(ROOT)),'bytes':apk.stat().st_size,'sha256':digest(apk),'installedApkSha256':device['installedApkSha256'],'permissions':uses,'signatureVerified':True,'signer':'Android Debug','certificateSha256':re.search(r'certificate SHA-256 digest: ([0-9a-f]+)',signature).group(1)},
    'releaseApk':{'productionSigned':False,'sha256':digest(release),'permissions':release_uses},
    'assets':assets,
    'cooperation':{'simulationOnly':True,'realAgencyConnected':False,'physicalLampConnected':False,'missionSeconds':300,'beaconSeconds':120,'stalePreviewOffSeconds':8,'actualResponseTimeMeasured':False,'clinicalOutcomeMeasured':False,'fixedScenarioCount':3},
    'avd':{'name':regression['avd'],'serial':regression['serial'],'regressionChecks':len(legacy),'cooperationChecks':len(new),'regressionEvidence':'verification/v04-regression-android-e2e.json','cooperationEvidence':'verification/v04-cooperation-e2e.json','deviceTunnel':'tcp:8781 -> host tcp:8781'},
    'web':web,'finalRuntimeState':state,'suites':suites,
}
(OUT/'v04-artifact-report.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps(result,ensure_ascii=False,indent=2))
