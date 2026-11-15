import copy
import http.client
import json
from pathlib import Path
import threading
import unittest

from experience import ExperienceError, ExperienceStore, SCENARIOS
from server import make_server


def snapshot(**changes):
    value = dict(schemaVersion=1, syntheticOnly=True, id='DEMO-12345678', revision=1,
                 target='EMERGENCY_119', scenarioId='S01', status='DEMO_CONNECTED',
                 sharedFields=[], video='OFF', videoSource='PHONE', police='NONE', navigation='NONE', sign='OFF')
    value.update(changes)
    return value


class ExperienceTests(unittest.TestCase):
    def test_all_nineteen_scenarios_have_synthetic_fixture_and_three_actions(self):
        root=Path(__file__).resolve().parents[1]
        catalog=json.loads((root/'app/src/main/assets/experience-catalog.json').read_text(encoding='utf-8'))
        self.assertTrue(catalog['syntheticOnly'])
        self.assertEqual({s['id'] for s in catalog['scenarios']},SCENARIOS)
        self.assertEqual(len(catalog['scenarios']),19)
        for case in catalog['scenarios']:
            self.assertEqual(len(set(case['priorities'])),3)
            store=ExperienceStore();store.publish(snapshot(scenarioId=case['id'],target=case['target']))
            self.assertEqual(len(store.listing()),1)

    def test_only_whitelisted_metadata_accepted(self):
        for changes in [dict(phone='REAL'),dict(name='PERSON'),dict(sharedFields=['Real Name']),dict(syntheticOnly=False),
                        dict(schemaVersion=True),dict(revision=True),dict(revision=-1),dict(id='tel:119'),dict(scenarioId='S15'),
                        dict(target=[]),dict(sharedFields=[{}]),dict(video='UNKNOWN'),dict(sharedFields=['LOCATION','LOCATION'])]:
            with self.subTest(changes=changes), self.assertRaises(ExperienceError):ExperienceStore().publish(snapshot(**changes))

    def test_revision_and_idempotency(self):
        s=ExperienceStore();s.publish(snapshot());s.publish(snapshot())
        s.publish(snapshot(revision=2,sharedFields=['LOCATION']))
        for p in [snapshot(),snapshot(revision=2)]:
            with self.assertRaises(ExperienceError):s.publish(p)
        self.assertEqual(s.listing()[0]['sharedFields'],['LOCATION'])

    def test_listing_and_input_are_not_shared_mutable_references(self):
        s=ExperienceStore();p=snapshot();s.publish(p);p['sharedFields'].append('LOCATION')
        view=s.listing();view[0]['sharedFields'].append('VIDEO');self.assertEqual(s.listing()[0]['sharedFields'],[])

    def test_revocation_is_idempotent_and_blocks_delayed_publish(self):
        s=ExperienceStore();s.publish(snapshot());p={'syntheticOnly':True,'id':'DEMO-12345678'}
        s.revoke(p);s.revoke(p);self.assertEqual(s.listing(),[])
        with self.assertRaises(ExperienceError):s.publish(snapshot(revision=10))
        with self.assertRaises(ExperienceError):s.revoke(dict(p,phone='REAL'))

    def test_expiry_capacity_and_consistency(self):
        clock=[0];s=ExperienceStore(limit=1,ttl=10,clock=lambda:clock[0]);s.publish(snapshot())
        with self.assertRaises(ExperienceError):s.publish(snapshot(id='DEMO-9999'))
        for changes in [dict(video='SHARING'),dict(sharedFields=['VIDEO']),dict(navigation='SET'),dict(status='COMPLETED',sharedFields=['LOCATION'])]:
            with self.assertRaises(ExperienceError):ExperienceStore().publish(snapshot(**changes))
        clock[0]=10;self.assertEqual(s.listing(),[]);s.publish(snapshot(id='DEMO-9999'))

    def test_manifest_has_no_emergency_contact_camera_or_do_bypass(self):
        root=Path(__file__).resolve().parents[1]
        for path in root.glob('app/src/*/AndroidManifest.xml'):
            text=path.read_text(encoding='utf-8')
            for forbidden in ['CALL_PHONE','SEND_SMS','android.permission.CAMERA','ACCESS_FINE_LOCATION','distractionOptimized']:
                self.assertNotIn(forbidden,text)


class ExperienceHttpTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.server=make_server(0);cls.thread=threading.Thread(target=cls.server.serve_forever,daemon=True);cls.thread.start()
    @classmethod
    def tearDownClass(cls):cls.server.shutdown();cls.server.server_close();cls.thread.join()
    def request(self,method,path,body=None,headers=None):
        c=http.client.HTTPConnection('127.0.0.1',self.server.server_port,timeout=5)
        h={'Content-Type':'application/json'};h.update(headers or {})
        c.request(method,path,json.dumps(body) if body is not None else None,h);r=c.getresponse();data=r.read();status=r.status;c.close();return status,data
    def test_publish_read_revoke_round_trip(self):
        self.assertEqual(self.request('POST','/api/demo/expansion/snapshots',snapshot())[0],200)
        status,data=self.request('GET','/api/demo/expansion/snapshots');self.assertEqual(status,200);self.assertEqual(json.loads(data)['items'][0]['scenarioId'],'S01')
        self.assertEqual(self.request('POST','/api/demo/expansion/revoke',{'syntheticOnly':True,'id':'DEMO-12345678'})[0],200)
        self.assertEqual(json.loads(self.request('GET','/api/demo/expansion/snapshots')[1])['items'],[])
    def test_bad_payload_and_cross_origin_and_static(self):
        self.assertEqual(self.request('POST','/api/demo/expansion/snapshots',snapshot(name='REAL'))[0],422)
        self.assertEqual(self.request('GET','/api/demo/expansion/snapshots',headers={'Origin':'https://example.invalid'})[0],403)
        status,data=self.request('GET','/experience-console');self.assertEqual(status,200);self.assertIn(b'/experience.js',data)
        self.assertEqual(self.request('GET','/experience.js')[0],200)


if __name__=='__main__':unittest.main()
