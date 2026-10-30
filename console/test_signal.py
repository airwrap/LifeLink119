import json
import unittest
from jsonschema import Draft202012Validator
from server import ApiError, MANUAL_FILE, ROOT, SessionStore
import test_server as helpers
from test_server import payload


class SignalTests(unittest.TestCase):
    def setUp(self):
        self.time = [10.0]
        self.store = SessionStore(clock=lambda:self.time[0])
        self.id = self.store.create(payload(),'signal-request-001')[0]['id']

    def signal(self,action,role='passenger',authority='119'):
        body={'syntheticOnly':True,'action':action}
        if role=='operator':body['authority']=authority
        return self.store.signal_action(self.id,role,body)['signalPreview']

    def reviewed(self):
        self.store.action(self.id,'ACKNOWLEDGE');self.store.action(self.id,'REVIEW')

    def approved(self,authority='119'):
        self.signal('REQUEST');self.reviewed();self.signal('CONFIRM','operator',authority)
        return self.signal('APPROVE','operator',authority)

    def test_no_preview_before_confirm_and_approval(self):
        for stage in ['OFF','REQUESTED','CONFIRMED']:
            if stage=='REQUESTED':self.signal('REQUEST');self.reviewed()
            if stage=='CONFIRMED':self.signal('CONFIRM','operator')
            with self.assertRaises(ApiError):self.signal('ACTIVATE')
        self.signal('APPROVE','operator')
        active=self.signal('ACTIVATE');self.assertTrue(active['previewEnabled']);self.assertFalse(active['hardwareConnected'])

    def test_passenger_cannot_confirm_or_approve_and_109_is_rejected(self):
        for action in ['CONFIRM','APPROVE','REVOKE']:
            with self.assertRaises(ApiError):self.signal(action)
        self.signal('REQUEST');self.reviewed()
        with self.assertRaises(ApiError):self.signal('CONFIRM','operator','109')
        with self.assertRaises(ApiError):self.signal('CONFIRM','operator',True)

    def test_review_and_same_authority_required(self):
        self.signal('REQUEST')
        with self.assertRaises(ApiError):self.signal('CONFIRM','operator')
        self.reviewed();self.signal('CONFIRM','operator','112')
        with self.assertRaises(ApiError):self.signal('APPROVE','operator','119')
        self.assertEqual(self.signal('APPROVE','operator','112')['authority'],'112')

    def test_expiry_is_monotonic_and_replay_never_renews(self):
        initial=self.approved();self.assertEqual(initial['remainingSeconds'],120)
        self.time[0]+=30
        replay=self.signal('APPROVE','operator');self.assertEqual(replay['remainingSeconds'],90)
        self.signal('ACTIVATE');self.time[0]+=90
        expired=self.store.get(self.id)['signalPreview'];self.assertEqual(expired['state'],'EXPIRED');self.assertFalse(expired['previewEnabled'])
        with self.assertRaises(ApiError):self.signal('ACTIVATE')
        self.assertEqual(self.signal('REQUEST')['state'],'REQUESTED')

    def test_close_and_share_revoke_cancel_approval(self):
        for operation in ['close','revoke']:
            with self.subTest(operation=operation):
                self.setUp();self.approved();self.signal('ACTIVATE')
                result=self.store.action(self.id,'CLOSE') if operation=='close' else self.store.revoke(self.id)
                self.assertEqual(result['signalPreview']['state'],'REVOKED');self.assertFalse(result['signalPreview']['previewEnabled'])
                with self.assertRaises(ApiError):self.signal('ACTIVATE')

    def test_reject_and_operator_or_passenger_stop(self):
        self.signal('REQUEST');self.assertEqual(self.signal('REJECT','operator')['state'],'REJECTED')
        self.approved();self.signal('ACTIVATE');self.assertEqual(self.signal('STOP')['state'],'REVOKED')
        with self.assertRaises(ApiError):self.signal('ACTIVATE')

    def test_restart_and_other_session_cannot_reuse_approval(self):
        approved=self.approved();self.signal('ACTIVATE')
        other=self.store.create(payload(),'signal-request-002')[0]
        self.assertEqual(other['signalPreview']['state'],'OFF')
        with self.assertRaises(ApiError):SessionStore(namespace=1).signal_action(self.id,'passenger',{'syntheticOnly':True,'action':'ACTIVATE'})
        self.assertIn(self.id,approved['approvalId'])

    def test_response_contract_for_all_preview_states(self):
        validator=Draft202012Validator(json.loads((ROOT/'docs/contracts/demo-session.schema.json').read_text(encoding='utf-8')))
        self.approved();self.signal('ACTIVATE');validator.validate(self.store.get(self.id))
        self.time[0]+=121;validator.validate(self.store.get(self.id))

    def test_manual_sources_and_offline_asset(self):
        manual=json.loads(MANUAL_FILE.read_text(encoding='utf-8'))
        self.assertEqual(len(manual['guides']),6)
        self.assertEqual(len({g['id'] for g in manual['guides']}),6)
        for guide in manual['guides']:
            self.assertTrue(guide['steps']);self.assertTrue(guide['caution'])
            for source in guide['sources']:self.assertTrue(manual['sources'][source]['url'].startswith('https://'))
        self.assertIn('의료진 검토 전',manual['notice'])


class SignalHttpTests(unittest.TestCase):
    setUpClass = classmethod(helpers.HttpTests.setUpClass.__func__)
    tearDownClass = classmethod(helpers.HttpTests.tearDownClass.__func__)
    request = helpers.HttpTests.request
    def test_manual_and_signal_http_boundaries(self):
        status,data,_=self.request('GET','/api/demo/manual');self.assertEqual(status,200);self.assertEqual(len(json.loads(data)['guides']),6)
        status,data,_=self.request('POST','/api/demo/sessions',payload(),headers={'Idempotency-Key':'signal-http-0001'})
        ticket=json.loads(data)['id'];base='/api/demo/sessions/'+ticket+'/signal/'
        for body in [{'syntheticOnly':False,'action':'REQUEST'},{'syntheticOnly':True,'action':'APPROVE'},{'syntheticOnly':True,'action':'REQUEST','approved':True}]:
            self.assertEqual(self.request('POST',base+'passenger',body)[0],422)
        self.assertEqual(self.request('POST',base+'passenger',{'syntheticOnly':True,'action':'REQUEST'})[0],200)
        self.assertEqual(self.request('POST',base+'operator',{'syntheticOnly':True,'action':'CONFIRM','authority':'109'})[0],422)
