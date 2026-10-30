"""Package only the debug APK whose bytes passed the complete v0.6 AVD gate."""
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
out = root / 'verification/v06'
sdk = Path(os.environ.get('ANDROID_HOME', Path(os.environ['LOCALAPPDATA']) / 'Android/Sdk'))
env = os.environ.copy()
env['JAVA_HOME'] = str(root.parent / '.tools/java/jdk-17.0.20.1+1')

def read_log(path):
    raw = path.read_bytes()
    return raw.decode('utf-16' if raw.startswith(b'\xff\xfe') else 'utf-8-sig')

def run(*args):
    return subprocess.check_output([str(a) for a in args], env=env, stderr=subprocess.STDOUT, timeout=40).decode(errors='replace')

def evidence(name):
    result = json.loads((out / name).read_text(encoding='utf-8'))
    assert result['status'] == 'PASS', (name, result.get('error', result['status']))
    assert result['apkSha256'] == digest, name + ': stale or different APK'
    return result

apk = root / 'app/build/outputs/apk/debug/app-debug.apk'
digest = hashlib.sha256(apk.read_bytes()).hexdigest()
assert 'BUILD SUCCESSFUL' in read_log(out / 'build.log')
reports = list((root / 'app/build/test-results/testDebugUnitTest').glob('TEST-*.xml'))
assert reports
counts = {k: sum(int(ET.parse(p).getroot().get(k, '0')) for p in reports) for k in ('tests', 'failures', 'errors', 'skipped')}
assert counts['tests'] >= 129 and counts['failures'] == counts['errors'] == counts['skipped'] == 0
python_log = read_log(out / 'server-tests.log')
python_tests = int(re.search(r'Ran (\d+) tests', python_log).group(1))
assert python_tests >= 68 and re.search(r'^OK\s*$', python_log, re.M)
issues = ET.parse(root / 'app/build/reports/lint-results-debug.xml').getroot().findall('issue')
assert not issues, 'Unresolved Android lint findings'
gate = json.loads((out / 'gate.json').read_text(encoding='utf-8'))
assert gate['status'] == 'PASS' and gate['qaPinReset'] is True
expected_stages = {'privacy', 'native', 'regression', 'recovery', 'driving', 'pc-sdk'}
assert {s['name'] for s in gate['stages'] if s['exit'] == 0} == expected_stages

privacy = evidence('privacy.json')
assert privacy['captureProtected'] and privacy['profilesEncrypted'] and not privacy['pinPlaintextStored']
assert len(privacy['checks']) >= 11
native = evidence('contest-native.json')
assert len(native['checks']) >= 16
assert {s['id'] for s in native['scenarios']} == {f'S{i:02}' for i in range(1, 15)} | {f'E{i:02}' for i in range(1, 5)}
assert all(s['status'] == 'PASS' and len(s['actions']) == 3 for s in native['scenarios'])
regression = evidence('regression/native-smoke.json')
recovery = evidence('recovery.json')
assert recovery['localConsoleReachable'] is False
driving = evidence('driving.json')
assert any(x > 0 for x in driving['callbackFlags']) and driving['callbackFlags'][-1] == 0
assert len(driving['stages']) == 3 and 'ActiveRestrictions=0' in driving['stages'][-1]['app']
pc_sdk = evidence('pc-sdk.json')
assert pc_sdk['metadataOnly'] and pc_sdk['revoked'] and 'READ' in pc_sdk['door'] and 'READ' in pc_sdk['battery']

sign = run(sdk / 'build-tools/35.0.0/apksigner.bat', 'verify', '--verbose', '--print-certs', apk)
assert 'Verified using v2 scheme (APK Signature Scheme v2): true' in sign
metadata = run(sdk / 'build-tools/35.0.0/aapt.exe', 'dump', 'badging', apk)
assert "versionCode='7'" in metadata and "versionName='0.6.0-demo'" in metadata
assert "sdkVersion:'28'" in metadata and "targetSdkVersion:'34'" in metadata
release = root / 'app/build/outputs/apk/release/app-release-unsigned.apk'
assert release.is_file()
permissions = {kind: run(sdk / 'build-tools/35.0.0/aapt.exe', 'dump', 'permissions', path) for kind, path in [('debug', apk), ('release', release)]}
for perms in permissions.values():
    for forbidden in ('CALL_PHONE', 'SEND_SMS', 'READ_SMS', 'RECORD_AUDIO', 'android.permission.CAMERA', 'ACCESS_FINE_LOCATION', 'ACCESS_COARSE_LOCATION'):
        assert forbidden not in perms, forbidden
assert 'android.permission.INTERNET' not in permissions['release']
assert 'android.permission.INTERNET' in permissions['debug']
adb = [str(sdk / 'platform-tools/adb.exe'), '-s', 'emulator-5580']
remote = run(*adb, 'shell', 'pm', 'path', 'com.lifelink119.app').strip().split('package:', 1)[1].splitlines()[0]
assert run(*adb, 'shell', 'sha256sum', remote).split()[0] == digest

target = root / 'artifacts/LifeLink119-v0.6.0-demo-debug.apk'
target.parent.mkdir(exist_ok=True)
shutil.copy2(apk, target)
assert hashlib.sha256(target.read_bytes()).hexdigest() == digest
target.with_suffix('.apk.sha256').write_text(digest + '  ' + target.name + '\n', encoding='ascii')
(out / 'signing.txt').write_text(sign, encoding='utf-8')
(out / 'permissions.json').write_text(json.dumps(permissions, indent=2), encoding='utf-8')
result = {
    'status': 'VERIFIED_WITH_LIMITATIONS', 'version': '0.6.0-demo', 'versionCode': 7,
    'unit': counts, 'pythonTests': python_tests, 'lint': {'errors': 0, 'warnings': 0},
    'apk': str(target.relative_to(root)), 'apkBytes': target.stat().st_size, 'apkSha256': digest,
    'qaPinReset': True,
    'nativeChecks': len(native['checks']), 'scenarioCount': len(native['scenarios']),
    'privacyChecks': len(privacy['checks']), 'regressionChecks': len(regression['checks']),
    'emulatorAuthSeconds': privacy['emulatorAuthSeconds'],
    'recoveryChecks': len(recovery['checks']), 'callbackFlags': driving['callbackFlags'],
    'pcAndSdk': {'metadataOnly': True, 'revoked': True, 'door': pc_sdk['door'], 'battery': pc_sdk['battery']},
    'release': 'Compiled unsigned, no INTERNET permission; not a signed distribution release.',
    'evidence': ['gate.json', 'privacy.json', 'contest-native.json', 'regression/native-smoke.json', 'recovery.json', 'driving.json', 'pc-sdk.json'],
    'limitations': [
        'Real agencies, camera/GPS/audio, navigation, digital key, physical lights and V2L actuation remain MOCK/unavailable.',
        'System credential routing is implemented; this AVD has no enrolled device lock, so only the fallback app PIN was exercised natively.',
        'Fallback PIN calculation takes about 26-27 seconds on this software AVD; cancellation and urgent entry remain available. Physical-device latency is unmeasured.',
        'Driving and CarUX transitions are verified on the Pleos AVD only; no physical car, OEM approval or road test.',
        'Internal 520dp native layout is verified; physical portrait/OEM multi-window coverage and complete TalkBack audit remain incomplete.',
        'Legacy cooperation is preserved with backend tests; the new offline cooperation flow is the v0.6 native demonstration gate.'
    ]
}
(out / 'artifact-report.json').write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding='utf-8')
print(json.dumps(result, ensure_ascii=False, indent=2))
