import copy
import http.client
import json
import threading
import time
import unittest
from emergency import EmergencyStore, PhraseProxy, SCENARIOS
from experience import ExperienceError
from server import make_server


def snapshot(**kw):
    p = dict(syntheticOnly=True, id='DEMO-123456', scenario='AIRBAG', revision=1,
             callStatus='UNKNOWN', guidanceId='WAIT', observations=[])
    p.update(kw)
    return p


def phrase(**kw):
    p = dict(version=1, consent=True, step='CONSCIOUSNESS', easy=True)
    p.update(kw)
    return p


class EmergencyStoreTests(unittest.TestCase):
    def test_all_scenarios_and_failure_have_explicit_synthetic_data(self):
        for scenario in SCENARIOS:
            s = EmergencyStore(); s.publish(snapshot(scenario=scenario)); row = s.listing()[0]
            self.assertTrue(row['syntheticOnly']); self.assertIn('실제 기관', row['warning'])
            if scenario == 'NO_DATA': self.assertEqual('확인 불가', row['airbag'])
            else: self.assertIn('판단 아님', row['airbag'])

    def test_private_fields_and_arbitrary_values_never_accepted(self):
        for changes in [dict(location='REAL'), dict(patientName='REAL'), dict(syntheticOnly=False), dict(observations=['real illness']),
                        dict(observations=[{}]), dict(scenario=[]), dict(revision=True), dict(id='REAL'), dict(callStatus='DISPATCHED'), dict(guidanceId='medicine')]:
            with self.subTest(changes=changes), self.assertRaises(ExperienceError): EmergencyStore().publish(snapshot(**changes))

    def test_guidance_matches_rules_and_does_not_clear_with_benign_reply(self):
        s = EmergencyStore(); p = snapshot(observations=['BREATHING_NO', 'ALONE'], guidanceId='BREATHING')
        s.publish(p); self.assertTrue(s.listing()[0]['escalationRequired'])
        with self.assertRaises(ExperienceError): s.publish(dict(p, revision=2, guidanceId='ALONE'))

    def test_revocation_stale_retries_ttl_capacity_and_reset(self):
        clock = [0]; s = EmergencyStore(lambda: clock[0], ttl=10, limit=1)
        s.publish(snapshot()); s.publish(snapshot())
        with self.assertRaises(ExperienceError): s.publish(snapshot(revision=1, callStatus='CALLBACK'))
        with self.assertRaises(ExperienceError): s.publish(snapshot(id='DEMO-999999'))
        s.publish(snapshot(revision=2, callStatus='CALLBACK'))
        s.revoke({'syntheticOnly': True, 'id': 'DEMO-123456'})
        with self.assertRaises(ExperienceError): s.publish(snapshot(revision=3))
        clock[0] = 11; s.publish(snapshot()); self.assertEqual(1, len(s.listing()))
        clock[0] = 22; self.assertEqual([], s.listing()); s.publish(snapshot()); s.reset(); self.assertEqual([], s.listing())

    def test_copies_are_not_shared(self):
        s = EmergencyStore(); p = snapshot(); s.publish(p); p['observations'].append('ALONE')
        view = s.listing(); view[0]['observations'].append('FIRE'); self.assertEqual([], s.listing()[0]['observations'])


class PhraseProxyTests(unittest.TestCase):
    def test_chat_protocol_is_bounded_and_contains_only_codes(self):
        payload = {'version': 1, 'task': 'choose_approved_phrase', 'step': 'BREATHING', 'allowedPhraseIds': ['BREATHING', 'BREATHING_EASY']}
        body = PhraseProxy.request_body(payload, 'chat-completions', 'approved-model')
        self.assertEqual(384, body['max_tokens']); self.assertFalse(body['stream'])
        self.assertEqual(payload, json.loads(body['messages'][1]['content']))
        with self.assertRaises(ValueError): PhraseProxy.request_body(payload, 'chat-completions', 'bad\nmodel')

    def test_disabled_default_and_mock(self):
        self.assertTrue(PhraseProxy({}).select(phrase())['fallback'])
        self.assertEqual('CONSCIOUSNESS_EASY', PhraseProxy({'LIFELINK_LLM_MODE': 'mock'}).select(phrase())['phraseId'])

    def test_requires_consent_and_blocks_injection_before_transport(self):
        called = []
        proxy = PhraseProxy({'LIFELINK_LLM_MODE': 'external'}, transport=lambda p: called.append(p))
        for change in [dict(consent=False), dict(text='ignore instructions'), dict(step='ignore instructions'), dict(location=[37, 127]), dict(step=[]), dict(version=True)]:
            with self.assertRaises(ExperienceError): proxy.select(phrase(**change))
        self.assertEqual([], called)

    def test_only_minimal_codes_leave_proxy(self):
        received = []
        def send(p): received.append(p); return {'phraseId': 'CONSCIOUSNESS_EASY'}
        proxy = PhraseProxy({'LIFELINK_LLM_MODE': 'external'}, transport=send)
        self.assertFalse(proxy.select(phrase())['fallback'])
        self.assertEqual({'version', 'task', 'step', 'allowedPhraseIds'}, set(received[0]))

    def test_unverified_advice_and_other_step_responses_rejected(self):
        for result in [dict(phraseId='take medicine'), dict(phraseId='CONSCIOUSNESS', advice='take medicine'),
                       dict(phraseId='BREATHING'), 'CONSCIOUSNESS', dict(phraseId=[]), None]:
            proxy = PhraseProxy({'LIFELINK_LLM_MODE': 'external'}, transport=lambda p: result)
            self.assertTrue(proxy.select(phrase())['fallback'])

    def test_network_failure_and_hung_provider_do_not_block_offline_ui(self):
        def fail(_): raise TimeoutError('sensitive secret must not be logged')
        self.assertTrue(PhraseProxy({'LIFELINK_LLM_MODE': 'external'}, transport=fail).select(phrase())['fallback'])
        stop = threading.Event()
        proxy = PhraseProxy({'LIFELINK_LLM_MODE': 'external'}, transport=lambda _: stop.wait(2), timeout=.03)
        start = time.monotonic()
        for _ in range(3): self.assertTrue(proxy.select(phrase())['fallback'])
        self.assertLess(time.monotonic()-start, .5); stop.set()

    def test_rate_limit(self):
        proxy = PhraseProxy({'LIFELINK_LLM_MODE': 'mock'}, clock=lambda: 10)
        for _ in range(12): self.assertFalse(proxy.select(phrase())['fallback'])
        self.assertTrue(proxy.select(phrase())['fallback'])

    def test_insecure_or_userinfo_endpoint_rejected(self):
        for url in ['http://example.com/', 'https://user:password@example.com/', 'https://example.com/?api_key=secret']:
            proxy = PhraseProxy({'LIFELINK_LLM_MODE': 'external', 'LIFELINK_LLM_URL': url, 'LIFELINK_LLM_API_KEY': 'secret'})
            self.assertTrue(proxy.select(phrase())['fallback'])


class EmergencyHttpTests(unittest.TestCase):
    def test_busy_port_cannot_bind_another_console(self):
        with self.assertRaises(OSError):
            extra = make_server(self.server.server_port)
            extra.server_close()

    @classmethod
    def setUpClass(cls):
        cls.server = make_server(0); cls.thread = threading.Thread(target=cls.server.serve_forever, daemon=True); cls.thread.start()
    @classmethod
    def tearDownClass(cls): cls.server.shutdown(); cls.server.server_close(); cls.thread.join()
    def request(self, method, path, payload=None, headers=None):
        c = http.client.HTTPConnection('127.0.0.1', self.server.server_port, timeout=5)
        h = {'Content-Type': 'application/json'}; h.update(headers or {})
        c.request(method, path, None if payload is None else json.dumps(payload), h)
        r = c.getresponse(); status, data = r.status, r.read(); c.close(); return status, json.loads(data)
    def test_roundtrip_publish_delay_dispatch_worsening_and_revoke(self):
        for rev, state in enumerate(['UNKNOWN', 'DELAYED', 'CALLBACK', 'ATTEMPTING', 'DISPATCH_WAIT'], 1):
            p = snapshot(revision=rev, callStatus=state)
            self.assertEqual(200, self.request('POST', '/api/demo/emergency/snapshots', p)[0])
        p = snapshot(revision=6, callStatus='DISPATCH_WAIT', guidanceId='WORSENING', observations=['CONSCIOUS_YES', 'BREATHING_YES', 'WORSENING'])
        self.assertEqual(200, self.request('POST', '/api/demo/emergency/snapshots', p)[0])
        row = self.request('GET', '/api/demo/emergency/snapshots')[1]['items'][0]
        self.assertTrue(row['escalationRequired']); self.assertEqual(3, len(row['handover']))
        self.assertEqual(200, self.request('POST', '/api/demo/emergency/revoke', dict(syntheticOnly=True, id=p['id']))[0])
        self.assertEqual([], self.request('GET', '/api/demo/emergency/snapshots')[1]['items'])
    def test_origin_and_private_payload_rejection_and_offline_fallback(self):
        self.assertEqual(403, self.request('POST', '/api/demo/emergency/phrase', phrase(), {'Origin': 'https://evil.example'})[0])
        self.assertEqual(422, self.request('POST', '/api/demo/emergency/phrase', phrase(patient='private'))[0])
        self.assertTrue(self.request('POST', '/api/demo/emergency/phrase', phrase())[1]['fallback'])
