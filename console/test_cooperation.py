import copy
import http.client
import json
import threading
import unittest
from cooperation import CooperationStore, CoopError
from server import make_server

class CooperationTests(unittest.TestCase):
    def setUp(self):
        self.tick=100.0
        self.store=CooperationStore(clock=lambda:self.tick,namespace=2468)
        self.body={'syntheticOnly':True,'scenarioId':'EARLY_HELP','consent':True}
        self.rid=self.store.create(self.body,'poc-test-0001')[0]['id']

    def do(self,action,role='operator',vehicle='DEMO-HELPER-A',authority='119',parked=None):
        body={'syntheticOnly':True,'action':action}
        if role=='operator':body['authority']=authority
        if role=='volunteer' or action=='INVITE':body['vehicleId']=vehicle
        if action in {'ARRIVE','ACTIVATE_BEACON'} and parked is not None:body['parked']=parked
        return self.store.action(self.rid,role,body)

    def authorized(self):
        self.do('CONFIRM');self.do('INVITE');self.do('ACCEPT','volunteer');return self.do('AUTHORIZE')

    def active(self):
        self.authorized();self.do('APPROVE_BEACON');return self.do('ACTIVATE_BEACON','volunteer')

    def test_consent_is_explicit_and_data_is_fixed(self):
        for patch in ({'consent':False},{'consent':1},{'syntheticOnly':False},{'scenarioId':'real-patient'},{'gps':[1,2]},{'patient':'name'}):
            with self.assertRaises(CoopError):self.store.create({**self.body,**patch},'other-poc-001')

    def test_idempotency_and_conflict(self):
        same,created=self.store.create(self.body,'poc-test-0001')
        self.assertFalse(created);self.assertEqual(self.rid,same['id'])
        with self.assertRaises(CoopError):self.store.create({**self.body,'scenarioId':'EMS_FIRST'},'poc-test-0001')

    def test_nearest_is_not_automatically_eligible(self):
        item=self.store.get(self.rid)
        ids={v['id'] for v in item['candidates'] if v['eligible']}
        self.assertEqual(ids,{'DEMO-HELPER-A','DEMO-HELPER-D'})
        self.do('CONFIRM')
        for vehicle in ('DEMO-HELPER-B','DEMO-HELPER-C','unknown'):
            with self.assertRaises(CoopError):self.do('INVITE',vehicle=vehicle)

    def test_positive_projection_is_an_assumption(self):
        p=self.store.get(self.rid)['projection']
        self.assertEqual((p['baselineSeconds'],p['supportSeconds'],p['potentialHeadStartSeconds']),(480,195,285))
        self.assertFalse(p['clinicalOutcomeMeasured']);self.assertFalse(p['actualResponseTimeMeasured'])

    def test_no_gain_control_is_not_a_positive_claim(self):
        self.rid=self.store.create({**self.body,'scenarioId':'EMS_FIRST'},'poc-control-01')[0]['id']
        p=self.store.get(self.rid)['projection']
        self.assertEqual(p['potentialHeadStartSeconds'],0);self.assertEqual(p['signedDeltaSeconds'],-75)

    def test_no_vehicle_control_keeps_emergency_response(self):
        self.rid=self.store.create({**self.body,'scenarioId':'NO_RESPONDER'},'poc-control-02')[0]['id']
        r=self.store.get(self.rid)
        self.assertFalse(any(v['eligible'] for v in r['candidates']))
        self.assertTrue(r['emergencyServiceContinues']);self.assertIsNone(r['projection']['supportSeconds'])
        self.do('CONFIRM')
        with self.assertRaises(CoopError):self.do('INVITE')

    def test_acceptance_and_confirmation_are_required(self):
        for action in ('INVITE','AUTHORIZE','APPROVE_BEACON','HANDOVER'):
            with self.assertRaises(CoopError):self.do(action)
        self.do('CONFIRM');self.do('INVITE')
        with self.assertRaises(CoopError):self.do('AUTHORIZE')

    def test_roles_cannot_self_authorize(self):
        for role,body in [('requester',{'syntheticOnly':True,'action':'AUTHORIZE'}),('volunteer',{'syntheticOnly':True,'action':'AUTHORIZE','vehicleId':'DEMO-HELPER-A'}),('operator',{'syntheticOnly':True,'action':'CONFIRM','authority':'109'}),('operator',{'syntheticOnly':True,'action':'CONFIRM','authority':'112'}),('operator',{'syntheticOnly':True,'action':'CONFIRM','authority':[]})]:
            with self.assertRaises(CoopError):self.store.action(self.rid,role,body)

    def test_wrong_vehicle_cannot_accept_or_light(self):
        self.do('CONFIRM');self.do('INVITE')
        with self.assertRaises(CoopError):self.do('ACCEPT','volunteer',vehicle='DEMO-HELPER-D')
        self.do('ACCEPT','volunteer');self.do('AUTHORIZE');self.do('APPROVE_BEACON')
        with self.assertRaises(CoopError):self.do('ACTIVATE_BEACON','volunteer',vehicle='DEMO-HELPER-D')

    def test_decline_requires_another_willing_vehicle(self):
        self.do('CONFIRM');self.do('INVITE');self.do('DECLINE','volunteer')
        projection=self.store.get(self.rid)['projection']
        self.assertEqual(projection['supportSeconds'],315)
        self.assertEqual(projection['potentialHeadStartSeconds'],165)
        self.assertEqual(projection['vehicleId'],'DEMO-HELPER-D')
        with self.assertRaises(CoopError):self.do('INVITE')
        r=self.do('INVITE',vehicle='DEMO-HELPER-D')
        self.assertEqual(r['generation'],2)
        with self.assertRaises(CoopError):self.do('ACCEPT','volunteer')
        self.do('ACCEPT','volunteer',vehicle='DEMO-HELPER-D')

    def test_112_can_approve_display_but_not_medical_mission(self):
        self.authorized()
        r=self.do('APPROVE_BEACON',authority='112')
        self.assertEqual(r['beacon']['authority'],'112')
        with self.assertRaises(CoopError):self.do('APPROVE_BEACON',authority='119')

    def test_unapproved_activation_denied_but_moving_approved_activation_allowed(self):
        self.authorized()
        with self.assertRaises(CoopError):self.do('ACTIVATE_BEACON','volunteer')
        self.do('APPROVE_BEACON')
        r=self.do('ACTIVATE_BEACON','volunteer',parked=False)
        self.assertEqual(r['beacon']['state'],'ACTIVE')
        for parked in (1,'true'):
            with self.assertRaises(CoopError):self.do('ACTIVATE_BEACON','volunteer',parked=parked)

    def test_approved_activation_needs_no_motion_claim(self):
        self.authorized();self.do('APPROVE_BEACON')
        r=self.do('ACTIVATE_BEACON','volunteer')
        self.assertEqual(r['beacon']['state'],'ACTIVE')
        self.assertEqual(self.do('ARRIVE','volunteer',parked=False)['state'],'ON_SCENE')

    def test_handover_turns_light_and_mission_off(self):
        self.active();self.do('ARRIVE','volunteer');r=self.do('HANDOVER')
        self.assertEqual(r['state'],'HANDED_OVER');self.assertFalse(r['beacon']['previewEnabled'])
        self.assertEqual(r['missionRemainingSeconds'],0);self.assertFalse(r['transportAuthorized'])
        self.assertEqual(self.do('HANDOVER'),r)

    def test_approval_replays_never_extend_either_lease(self):
        self.active();self.tick+=30
        r=self.do('AUTHORIZE');self.assertEqual(r['missionRemainingSeconds'],270)
        r=self.do('APPROVE_BEACON');self.assertEqual(r['beacon']['remainingSeconds'],90)
        r=self.do('ACTIVATE_BEACON','volunteer');self.assertEqual(r['beacon']['remainingSeconds'],90)

    def test_beacon_expires_and_can_only_be_renewed_by_operator(self):
        old=self.active()['beacon']['approvalId'];self.tick+=121;r=self.store.get(self.rid)
        self.assertEqual(r['state'],'AUTHORIZED');self.assertEqual(r['beacon']['state'],'EXPIRED')
        with self.assertRaises(CoopError):self.do('ACTIVATE_BEACON','volunteer')
        new=self.do('APPROVE_BEACON');self.assertNotEqual(new['beacon']['approvalId'],old)
        self.assertEqual(new['beacon']['sequence'],2)
        self.assertEqual(self.do('ACTIVATE_BEACON','volunteer')['beacon']['state'],'ACTIVE')

    def test_mission_expiry_prevents_revival(self):
        self.active();self.tick+=301;r=self.store.get(self.rid)
        self.assertEqual(r['state'],'EXPIRED');self.assertFalse(r['beacon']['previewEnabled'])
        for action in ('AUTHORIZE','APPROVE_BEACON'):
            with self.assertRaises(CoopError):self.do(action)

    def test_cancellation_and_revocation_stop_light(self):
        self.active();r=self.do('CANCEL','requester')
        self.assertEqual(r['state'],'CANCELLED');self.assertFalse(r['beacon']['previewEnabled'])
        with self.assertRaises(CoopError):self.do('ARRIVE','volunteer')
        self.rid=self.store.create(self.body,'poc-next-002')[0]['id'];self.active()
        self.assertEqual(self.do('REVOKE',authority='112')['beacon']['state'],'REVOKED')

    def test_ui_elapsed_time_is_not_travel_time(self):
        self.tick+=6;self.do('CONFIRM');r=self.store.get(self.rid)
        self.assertEqual(r['interactionElapsedSeconds'],6)
        self.assertEqual(r['timeline'][-1]['elapsedSeconds'],6)
        self.assertEqual(r['projection']['supportSeconds'],195)

    def test_expiry_capacity_and_restart_ids(self):
        store=CooperationStore(limit=1,ttl=10,clock=lambda:self.tick,namespace=9)
        old=store.create(self.body,'limit-test-001')[0]
        with self.assertRaises(CoopError):store.create(self.body,'limit-test-002')
        self.tick+=11
        with self.assertRaises(CoopError):store.get(old['id'])
        new=store.create(self.body,'limit-test-002')[0]
        self.assertNotEqual(new['id'],old['id'])
        other=CooperationStore(namespace=10).create(self.body,'limit-test-001')[0]
        self.assertNotEqual(new['id'],other['id'])

    def test_unknown_input_is_not_stored(self):
        with self.assertRaises(CoopError):self.store.action(self.rid,'operator',{'syntheticOnly':True,'authority':'119','action':'CONFIRM','patient':'real'})
        r=self.store.get(self.rid)
        self.assertFalse(r['beacon']['hardwareConnected']);self.assertTrue(r['syntheticOnly'])
        self.assertFalse(any(k.startswith('_') for k in r))

class CooperationHttpTests(unittest.TestCase):
    def test_loopback_api_role_checks_and_new_ui(self):
        server=make_server(0);thread=threading.Thread(target=server.serve_forever,daemon=True);thread.start()
        port=server.server_port
        def request(method,path,body=None,host=None,key=None):
            c=http.client.HTTPConnection('127.0.0.1',port,timeout=4)
            headers={'Content-Type':'application/json','Host':host or '127.0.0.1:'+str(port)}
            if key:headers['Idempotency-Key']=key
            c.request(method,path,json.dumps(body) if body is not None else None,headers)
            r=c.getresponse();result=(r.status,r.read());c.close();return result
        try:
            self.assertEqual(request('GET','/api/demo/cooperation/catalog')[0],200)
            self.assertEqual(request('GET','/api/demo/cooperation/runs',host='evil.example')[0],403)
            status,data=request('POST','/api/demo/cooperation/runs',{'syntheticOnly':True,'scenarioId':'EARLY_HELP','consent':True},key='http-poc-001')
            self.assertEqual(status,201);rid=json.loads(data)['id']
            self.assertEqual(request('POST',f'/api/demo/cooperation/runs/{rid}/requester',{'syntheticOnly':True,'action':'AUTHORIZE'})[0],422)
            self.assertEqual(request('GET',f'/api/demo/cooperation/runs/{rid}')[0],200)
            self.assertEqual(len(json.loads(request('GET','/api/demo/cooperation/runs')[1])['items']),1)
        finally:server.shutdown();server.server_close();thread.join()

if __name__=='__main__':unittest.main()
