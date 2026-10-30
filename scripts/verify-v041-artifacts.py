"""Collect the completed v0.4.1 checks without reusing older APK results."""
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import re
import urllib.request
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'verification'
apk = ROOT / 'artifacts/LifeLink119-v0.4.1-demo-debug.apk'
sha = hashlib.sha256(apk.read_bytes()).hexdigest()
assert sha == hashlib.sha256((ROOT / 'app/build/outputs/apk/debug/app-debug.apk').read_bytes()).hexdigest()
device = json.loads((OUT / 'v041-device.json').read_text(encoding='utf-8'))
assert device['installedApkSha256'] == device['artifactSha256'] == sha
assert device['api'] == '34' and device['avd'] == 'Pleos_Connect_API_34'
evidence = {}
limitations = []
for name in ('v041-web-e2e.json', 'v041-regression-android-e2e.json', 'v041-cooperation-e2e.json'):
    value = json.loads((OUT / name).read_text(encoding='utf-8'))
    assert all(c['status'] == 'PASS' for c in value['checks'])
    if name == 'v041-cooperation-e2e.json' and value['status'] == 'PARTIAL':
        assert value['requestedMotionBehavior'] == 'PASS'
        assert value['limitationCategory'] == 'emulator_accessibility_connection'
        assert {'movingAndParkedContextsKeepApprovedCooperationIndicator', '112BoundApprovalAndRenderedPulse'}.issubset(c['check'] for c in value['checks'])
        limitations.append(value['limitation'])
    else:
        assert value['status'] == 'PASS'
    if 'apkSha256' in value:
        assert value['apkSha256'] == sha
    evidence[name] = {'status': value['status'], 'count': len(value['checks']), 'checks': [c['check'] for c in value['checks']],
                      'baselineEvidence': value.get('baselineEvidence')}
suites = [ET.parse(p).getroot() for p in (ROOT / 'app/build/test-results/testDebugUnitTest').glob('TEST-*.xml')]
assert suites and all(int(s.get('failures')) == int(s.get('errors')) == 0 for s in suites)
unit_count = sum(int(s.get('tests')) for s in suites)
assert len(ET.parse(ROOT / 'app/build/reports/lint-results-debug.xml').getroot()) == 0
assert 'BUILD SUCCESSFUL' in (OUT / 'v041-build.log').read_text(encoding='utf-8')
server_log = (OUT / 'v041-server-tests.log').read_text(encoding='utf-8')
assert re.search(r'Ran 51 tests', server_log) and server_log.rstrip().endswith('OK')
assert 'Verified using v2 scheme (APK Signature Scheme v2): true' in (OUT / 'v041-apk-signature.txt').read_text(encoding='utf-8')
with urllib.request.urlopen('http://127.0.0.1:8781/api/demo/health', timeout=5) as response:
    health = json.load(response)
assert health['version'] == '0.4.1' and health['syntheticOnly'] is True
report = {'status': 'VERIFIED_WITH_LIMITATIONS' if limitations else 'PASS', 'limitations': limitations,
          'at': datetime.now(timezone.utc).isoformat(), 'apk': str(apk.relative_to(ROOT)),
          'sha256': sha, 'installedHashMatches': True, 'androidUnitTests': unit_count,
          'runtime': {'avd': device['avd'], 'api': device['api'], 'renderer': device.get('renderer'),
                      'finalScreenVerified': device.get('finalScreenVerified'), 'helperInstalled': device.get('uiSnapshotHelperInstalled')},
          'serverTests': 51, 'lintIssues': 0, 'evidence': evidence,
          'motionContextIsSimulated': True, 'realAgencyConnected': False, 'physicalLampConnected': False}
(OUT / 'v041-artifact-report.json').write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
print(json.dumps(report, ensure_ascii=False, indent=2))
