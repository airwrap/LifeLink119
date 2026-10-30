"""Verify current v0.5 build evidence and package the tested debug APK."""
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import xml.etree.ElementTree as ET

root=Path(__file__).resolve().parents[1]
sdk=Path(os.environ.get('ANDROID_HOME',Path(os.environ['LOCALAPPDATA'])/'Android/Sdk'))
env=os.environ.copy();env['JAVA_HOME']=str(root.parent/'.tools/java/jdk-17.0.20.1+1')
reports=list((root/'app/build/test-results/testDebugUnitTest').glob('TEST-*.xml'))
assert reports
counts={k:sum(int(ET.parse(p).getroot().get(k,'0')) for p in reports) for k in ['tests','failures','errors','skipped']}
assert counts['tests']>=98 and counts['failures']==counts['errors']==0
assert 'BUILD SUCCESSFUL' in (root/'verification/v05-final-build.log').read_text(encoding='utf-8')
raw=(root/'verification/v05-python-tests.log').read_bytes()
assert 'Ran 60 tests' in raw.decode('utf-16' if raw.startswith(b'\xff\xfe') else 'utf-8-sig')
native=json.loads((root/'verification/v05/native-smoke.json').read_text(encoding='utf-8'))
assert native['status']=='PASS',native
apk=root/'app/build/outputs/apk/debug/app-debug.apk'
digest=hashlib.sha256(apk.read_bytes()).hexdigest();assert native['apkSha256']==digest
def run(*a):return subprocess.check_output([str(x) for x in a],env=env,stderr=subprocess.STDOUT,timeout=30).decode(errors='replace')
sign=run(sdk/'build-tools/35.0.0/apksigner.bat','verify','--verbose','--print-certs',apk)
assert 'Verified using v2 scheme (APK Signature Scheme v2): true' in sign
metadata=run(sdk/'build-tools/35.0.0/aapt.exe','dump','badging',apk)
assert "versionCode='6'" in metadata and "versionName='0.5.0-demo'" in metadata
release=root/'app/build/outputs/apk/release/app-release-unsigned.apk';assert release.is_file()
permissions={kind:run(sdk/'build-tools/35.0.0/aapt.exe','dump','permissions',p) for kind,p in [('debug',apk),('release',release)]}
for kind,perms in permissions.items():
    for permission in ['CALL_PHONE','SEND_SMS','RECORD_AUDIO','android.permission.CAMERA','ACCESS_FINE_LOCATION']:assert permission not in perms
assert 'android.permission.INTERNET' not in permissions['release']
assert 'android.permission.INTERNET' in permissions['debug']
target=root/'artifacts/LifeLink119-v0.5.0-demo-debug.apk';target.parent.mkdir(exist_ok=True);shutil.copy2(apk,target)
target.with_suffix('.apk.sha256').write_text(digest+'  '+target.name+'\n',encoding='ascii')
(root/'verification/v05/signing.txt').write_text(sign,encoding='utf-8')
(root/'verification/v05/permissions.json').write_text(json.dumps(permissions,ensure_ascii=False,indent=2),encoding='utf-8')
lint=ET.parse(root/'app/build/reports/lint-results-debug.xml').getroot()
issues=list(lint.findall('issue'));errors=sum(x.get('severity') in ['Error','Fatal'] for x in issues)
assert errors==0
result={'status':'VERIFIED_WITH_LIMITATIONS','version':'0.5.0-demo','versionCode':6,'unit':counts,'pythonTests':60,
        'native':native,'lint':{'errors':errors,'warnings':sum(x.get('severity')=='Warning' for x in issues)},
        'apk':str(target.relative_to(root)),'apkBytes':target.stat().st_size,'apkSha256':digest,
        'release':'compiled unsigned; no INTERNET permission; not a distributable signed release',
        'limitations':['External emergency/media/location/transport integrations are MOCK or unavailable.',
                       'Actual driving transitions, OEM approval, physical vehicle and TalkBack are not fully verified.',
                       'Legacy cooperation backend regression passed; full legacy native end-to-end flow not repeated.']}
extra=root/'verification/v05/native-extra.json'
if extra.exists():
    result['nativeExtra']=json.loads(extra.read_text(encoding='utf-8'))
    result['limitations'].append('Portrait/narrow Pleos window verification incomplete: app bounds remained fixed; display restored.')
sdk_runtime=root/'verification/v05/sdk-runtime.json'
if sdk_runtime.exists():
    result['sdkRuntime']=json.loads(sdk_runtime.read_text(encoding='utf-8'))
    assert 'READ' in result['sdkRuntime']['door'] and 'READ' in result['sdkRuntime']['battery']
(root/'verification/v05-artifact-report.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps({k:v for k,v in result.items() if k!='native'},ensure_ascii=False,indent=2))
