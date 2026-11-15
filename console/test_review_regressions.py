import http.client
import json
import threading
import unittest
from unittest.mock import patch

from jsonschema import Draft202012Validator
from cooperation import CooperationStore, CoopError
from experience import ExperienceStore
from server import ROOT, SessionStore, make_server, VERSION
from test_server import payload
from test_experience import snapshot


class ReviewRegressionTests(unittest.TestCase):
    def test_repeated_signal_cycles_stay_in_contract_and_never_reuse_approval_ids(self):
        store = SessionStore()
        ticket = store.create(payload(), 'review-signal-01')[0]['id']
        store.action(ticket, 'ACKNOWLEDGE')
        store.action(ticket, 'REVIEW')
        schema = json.loads((ROOT / 'docs/contracts/demo-session.schema.json').read_text(encoding='utf-8'))
        validator = Draft202012Validator(schema)
        approvals = set()
        for _ in range(20):
            for action, role in [('REQUEST', 'passenger'), ('CONFIRM', 'operator'),
                                 ('APPROVE', 'operator'), ('ACTIVATE', 'passenger'), ('STOP', 'passenger')]:
                body = {'syntheticOnly': True, 'action': action}
                if role == 'operator':
                    body['authority'] = '119'
                result = store.signal_action(ticket, role, body)
                validator.validate(result)
                if action == 'APPROVE':
                    ident = result['signalPreview']['approvalId']
                    self.assertNotIn(ident, approvals)
                    approvals.add(ident)
        self.assertEqual(len(approvals), 20)
        self.assertEqual(len(store.get(ticket)['timeline']), 10)

    def test_volunteer_cannot_withdraw_or_stop_before_valid_mission(self):
        store = CooperationStore()
        ticket = store.create({'syntheticOnly': True, 'scenarioId': 'EARLY_HELP', 'consent': True}, 'review-coop-01')[0]['id']
        for action in ('WITHDRAW', 'STOP_BEACON'):
            with self.assertRaises(CoopError):
                store.action(ticket, 'volunteer', {'syntheticOnly': True, 'action': action, 'vehicleId': 'DEMO-HELPER-A'})
        store.action(ticket, 'operator', {'syntheticOnly': True, 'action': 'CONFIRM', 'authority': '119'})
        store.action(ticket, 'operator', {'syntheticOnly': True, 'action': 'INVITE', 'authority': '119', 'vehicleId': 'DEMO-HELPER-A'})
        for action in ('WITHDRAW', 'STOP_BEACON'):
            with self.assertRaises(CoopError):
                store.action(ticket, 'volunteer', {'syntheticOnly': True, 'action': action, 'vehicleId': 'DEMO-HELPER-A'})
        self.assertEqual(store.get(ticket)['state'], 'INVITED')

    def test_http_reset_clears_every_store_and_capacity_can_be_reused(self):
        coop = CooperationStore(limit=1)
        experience = ExperienceStore()
        coop.create({'syntheticOnly': True, 'scenarioId': 'EARLY_HELP', 'consent': True}, 'review-coop-02')
        experience.publish(snapshot())
        with patch('server.CooperationStore', return_value=coop), patch('server.ExperienceStore', return_value=experience):
            server = make_server(0)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        try:
            client = http.client.HTTPConnection('127.0.0.1', server.server_port, timeout=5)
            client.request('POST', '/api/demo/reset', json.dumps({'syntheticOnly': True}), {'Content-Type': 'application/json'})
            response = client.getresponse()
            response.read()
            self.assertEqual(response.status, 200)
            client.close()
            self.assertEqual(coop.listing(), [])
            self.assertEqual(experience.listing(), [])
            self.assertEqual(experience.revoked, {})
            self.assertTrue(coop.create({'syntheticOnly': True, 'scenarioId': 'EARLY_HELP', 'consent': True}, 'review-coop-03')[1])
        finally:
            server.shutdown()
            server.server_close()
            thread.join(3)

    def test_internal_errors_are_logged_without_exception_message_or_payload(self):
        store = SessionStore()
        server = make_server(0, store=store)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        try:
            with patch.object(store, 'listing', side_effect=RuntimeError('SECRET_MEDICAL_TEXT')):
                with self.assertLogs('lifelink.console', level='ERROR') as logs:
                    client = http.client.HTTPConnection('127.0.0.1', server.server_port, timeout=5)
                    client.request('GET', '/api/demo/health')
                    response = client.getresponse()
                    response.read()
                    self.assertEqual(response.status, 500)
                    client.close()
            self.assertIn('RuntimeError', str(logs.output))
            self.assertIn('frames=', str(logs.output))
            self.assertNotIn('SECRET_MEDICAL_TEXT', str(logs.output))
        finally:
            server.shutdown()
            server.server_close()
            thread.join(3)

    def test_server_version_follows_android_version(self):
        self.assertIn('versionName = "' + VERSION + '"', (ROOT / 'app/build.gradle.kts').read_text(encoding='utf-8'))
