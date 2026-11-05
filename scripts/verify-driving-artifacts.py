"""Package v0.7 only after build, unit/server tests and matching native UI evidence."""
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
out = root/'verification/v07'
out.mkdir(parents=True, exist_ok=True)
sdk = Path(os.environ.get('ANDROID_HOME', Path(os.environ['LOCALAPPDATA'])/'Android/Sdk'))
env = os.environ.copy()
env.setdefault('JAVA_HOME', str(root.parent/'.tools/java/jdk-17.0.20.1+1'))

def log(path):
    raw = path.read_bytes()
    return raw.decode('utf-16' if raw.startswith(b'\xff\xfe') else 'utf-8-sig')

build = root/'build-driving-v07-final.log'
server = root/'console-driving-v07.log'
assert 'BUILD SUCCESSFUL' in log(build) and 'BUILD FAILED' not in log(build)
server_text = log(server)
assert re.search(r'^OK\s*$', server_text, re.M)
server_count = int(re.search(r'Ran (\d+) tests', server_text).group(1))
assert server_count >= 68
reports = list((root/'app/build/test-results/testDebugUnitTest').glob('TEST-*.xml'))
counts = {k: sum(int(ET.parse(p).getroot().get(k, '0')) for p in reports)
          for k in ('tests', 'failures', 'errors', 'skipped')}
assert counts['tests'] >= 143 and counts['failures'] == counts['errors'] == counts['skipped'] == 0
assert not ET.parse(root/'app/build/reports/lint-results-debug.xml').getroot().findall('issue')

apk = root/'app/build/outputs/apk/debug/app-debug.apk'
digest = hashlib.sha256(apk.read_bytes()).hexdigest()
native = json.loads((out/'driving-native.json').read_text(encoding='utf-8'))
assert native['status'] == 'PASS' and native['apkSha256'] == digest
assert native.get('evidenceDirectory') == 'driving-generic'
assert len(native['checks']) >= 10
metadata = subprocess.check_output([str(sdk/'build-tools/35.0.0/aapt.exe'), 'dump', 'badging', str(apk)], timeout=25).decode()
assert "versionCode='8'" in metadata and "versionName='0.7.0-demo'" in metadata
assert "sdkVersion:'28'" in metadata and "targetSdkVersion:'34'" in metadata
for forbidden in ('CALL_PHONE', 'SEND_SMS', 'RECORD_AUDIO', 'ACCESS_FINE_LOCATION', 'ACCESS_COARSE_LOCATION', 'CAMERA'):
    assert 'android.permission.'+forbidden not in metadata
signature = subprocess.check_output([str(sdk/'build-tools/35.0.0/apksigner.bat'), 'verify', '--verbose', '--print-certs', str(apk)], env=env, timeout=30).decode()
assert 'Verified using v2 scheme (APK Signature Scheme v2): true' in signature
artifacts = root/'artifacts'
artifacts.mkdir(exist_ok=True)
target = artifacts/'LifeLink119-v0.7.0-driving-demo.apk'
shutil.copy2(apk, target)
(artifacts/'LifeLink119-v0.7.0-driving-demo.apk.sha256').write_text(digest+'  '+target.name+'\n', encoding='ascii')
shutil.copy2(build, out/'build.log')
shutil.copy2(server, out/'server-tests.log')
(out/'apk-signature.txt').write_text(signature, encoding='utf-8')
result = {'status': 'PASSED_WITH_DEMO_LIMITATIONS', 'version': '0.7.0-demo', 'versionCode': 8,
          'apk': str(target), 'apkSha256': digest, 'unitTests': counts,
          'serverTests': server_count, 'lintIssues': 0, 'nativeChecks': native['checks'],
          'nativeAutomationBackend': native.get('automationBackend'), 'nativeEvidenceDirectory': native['evidenceDirectory'],
          'limitations': native['limitations']}
(out/'validation.json').write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding='utf-8')
print(json.dumps(result, ensure_ascii=False, indent=2))
