"""Regression checks for current APK identity and isolated QA network boundaries."""
import hashlib
import http.client
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from qa_console import QaConsole
from ui_snapshot_client import current_app_version, verify_installed_apk


class ApkIdentityTests(unittest.TestCase):
    def verify(self, remote_hash, version):
        with tempfile.TemporaryDirectory() as folder:
            apk = Path(folder) / 'app.apk'
            apk.write_bytes(b'qa-apk')
            responses = [b'10', b'package:/data/app/base.apk',
                         (remote_hash + ' /data/app/base.apk').encode(),
                         ("package: name='com.lifelink119.app' versionName='" + version + "'").encode()]
            with patch('ui_snapshot_client.subprocess.check_output', side_effect=responses):
                return verify_installed_apk(['adb', '-s', 'emulator-5584'], apk, Path(folder))

    def test_current_source_version_and_identical_bytes_are_required(self):
        digest = hashlib.sha256(b'qa-apk').hexdigest()
        self.assertEqual(digest, self.verify(digest, current_app_version()))
        with self.assertRaisesRegex(RuntimeError, 'version'):
            self.verify(digest, 'old-unrelated-build')
        with self.assertRaisesRegex(RuntimeError, 'bytes differ'):
            self.verify('0' * 64, current_app_version())


class IsolatedConsoleTests(unittest.TestCase):
    def setUp(self):
        self.calls = []
        self.guard = patch('qa_console.QaAvdGuard').start()
        def run(fixture, *args):
            self.calls.append(args)
            return 'UsbFfs tcp:8781 tcp:19999' if args == ('reverse', '--list') else ''
        self.run = patch.object(QaConsole, 'run', run).start()
        self.before = os.environ.get('LIFELINK_LLM_MODE')
        self.fixture = QaConsole(['adb', '-s', 'emulator-5584']).__enter__()
        self.addCleanup(patch.stopall)
        self.addCleanup(self.fixture.close)

    def request(self, port, headers=None, path='/api/demo/health'):
        connection = http.client.HTTPConnection('127.0.0.1', port, timeout=5)
        try:
            connection.request('GET', path, headers=headers or {})
            response = connection.getresponse()
            response.read()
            return response.status
        finally:
            connection.close()

    def test_only_bounded_native_requests_reach_fresh_production_server(self):
        relay = self.fixture.relay.server_port
        self.assertEqual(200, self.request(relay, {'Host': '127.0.0.1:8781'}))
        self.assertEqual(403, self.request(relay, {'Host': 'evil.example'}))
        self.assertEqual(403, self.request(relay, {'Host': '127.0.0.1:8781', 'Origin': 'https://evil.example'}))
        self.assertEqual(403, self.request(relay, {'Host': '127.0.0.1:8781', 'Sec-Fetch-Site': 'cross-site'}))
        self.assertEqual(403, self.request(relay, {'Host': '127.0.0.1:8781'}, 'https://evil.example/api/demo/health'))
        self.assertEqual(403, self.request(relay, {'Host': '127.0.0.1:8781'}, '/private'))

    def test_production_host_origin_policy_is_unchanged(self):
        port = self.fixture.server.server_port
        self.assertEqual(200, self.request(port))
        self.assertEqual(403, self.request(port, {'Host': '127.0.0.1:8781'}))
        self.assertEqual(403, self.request(port, {'Origin': 'https://evil.example'}))

    def test_qa_cleanup_restores_original_tunnel_and_environment(self):
        self.assertEqual('mock', os.environ.get('LIFELINK_LLM_MODE'))
        self.fixture.close()
        self.assertEqual(self.before, os.environ.get('LIFELINK_LLM_MODE'))
        self.assertIn(('reverse', '--remove', 'tcp:8781'), self.calls)
        self.assertEqual(('reverse', 'tcp:8781', 'tcp:19999'), self.calls[-1])


if __name__ == '__main__':
    unittest.main()
