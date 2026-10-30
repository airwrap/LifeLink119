import concurrent.futures
import copy
import http.client
import json
import threading
import unittest

from jsonschema import Draft202012Validator

from server import ApiError, CATALOG, ROOT, SessionStore, make_server


def payload(scenario="MEDICAL", vehicle=False, location=False, video=False):
    return {"schemaVersion":"1.0","syntheticOnly":True,"scenario":scenario,"caseCode":CATALOG[scenario][0]["code"],
            "consent":{"vehicle":vehicle,"location":location,"video":video},
            "vehicle":{"id":"DEMO-CAR-01","name":"가상 차량 A","batteryPercent":72} if vehicle else None,
            "location":{"coordinateSystem":"DEMO_GRID","label":"가상 훈련장 A구역","x":120,"y":80} if location else None,
            "videoLink":"demo://video/DEMO-0000" if video else None}


class StoreTests(unittest.TestCase):
    def setUp(self):
        self.store = SessionStore()

    def test_five_scenarios_match_response_contract(self):
        schema=json.loads((ROOT/'docs/contracts/demo-session.schema.json').read_text(encoding='utf-8'))
        validator=Draft202012Validator(schema)
        for i, scenario in enumerate(CATALOG):
            item, created=self.store.create(payload(scenario),f'request-{i:04d}')
            self.assertTrue(created)
            validator.validate(item)
            self.assertIsNone(item['vehicle'])
            self.assertIsNone(item['location'])
            self.assertIsNone(item['videoLink'])

    def test_all_choices_and_operator_stages(self):
        validator=Draft202012Validator(json.loads((ROOT/'docs/contracts/demo-session.schema.json').read_text(encoding='utf-8')))
        item,_=self.store.create(payload('VIDEO',True,True,True),'video-000001')
        for action,status in [('ACKNOWLEDGE','DEMO_RECEIVED'),('REVIEW','DEMO_REVIEWING'),('GUIDANCE','DEMO_GUIDANCE'),('CLOSE','DEMO_CLOSED')]:
            item=self.store.action(item['id'],action)
            self.assertEqual(item['status'],status)
            validator.validate(item)
        self.assertEqual(item['route']['points'][0],[120,80])
        self.assertTrue(item['route']['simulationOnly'])
        self.assertEqual(len(item['timeline']),5)

    def test_invalid_transition_and_repeated_action(self):
        item,_=self.store.create(payload(),'request-0001')
        with self.assertRaises(ApiError) as error:self.store.action(item['id'],'GUIDANCE')
        self.assertEqual(error.exception.status,409)
        first=self.store.action(item['id'],'ACKNOWLEDGE')
        self.assertEqual(first,self.store.action(item['id'],'ACKNOWLEDGE'))
        closed=self.store.action(item['id'],'CLOSE')
        self.assertEqual(closed,self.store.action(item['id'],'CLOSE'))
        with self.assertRaises(ApiError):self.store.action(item['id'],'REVIEW')

    def test_concurrent_retries_do_not_duplicate(self):
        with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
            result=list(pool.map(lambda _:self.store.create(payload(),'same-request-0001'),range(16)))
        self.assertEqual(len(self.store.listing()),1)
        self.assertEqual(sum(created for _,created in result),1)

    def test_same_key_with_changed_payload_rejected(self):
        self.store.create(payload(),'request-0001')
        with self.assertRaises(ApiError) as error:self.store.create(payload(vehicle=True),'request-0001')
        self.assertEqual(error.exception.status,409)

    def test_revoke_clears_all_shared_fields_and_replay_does_not_restore(self):
        item,_=self.store.create(payload('VIDEO',True,True,True),'video-000001')
        for action in ['ACKNOWLEDGE','REVIEW','GUIDANCE']:self.store.action(item['id'],action)
        revoked=self.store.revoke(item['id'])
        for field in ['vehicle','location','videoLink','route']:self.assertIsNone(revoked[field])
        self.assertFalse(any(revoked['consent'].values()))
        replay,created=self.store.create(payload('VIDEO',True,True,True),'video-000001')
        self.assertFalse(created)
        self.assertIsNone(replay['videoLink'])
        self.assertEqual(len(self.store.revoke(item['id'])['timeline']),len(revoked['timeline']))

    def test_no_route_without_location_consent(self):
        item,_=self.store.create(payload(),'request-0001')
        for action in ['ACKNOWLEDGE','REVIEW','GUIDANCE']:item=self.store.action(item['id'],action)
        self.assertIsNone(item['route'])

    def test_expiry_and_capacity(self):
        clock=[0]
        store=SessionStore(limit=1,ttl=10,clock=lambda:clock[0])
        old,_=store.create(payload(),'request-0001')
        with self.assertRaises(ApiError) as error:store.create(payload(),'request-0002')
        self.assertEqual(error.exception.status,429)
        clock[0]=10
        self.assertEqual(store.listing(),[])
        new,_=store.create(payload(),'request-0002')
        self.assertNotEqual(old['id'],new['id'])

    def test_reset_never_reuses_ids(self):
        old,_=self.store.create(payload(),'request-0001')
        self.store.reset()
        new,_=self.store.create(payload(),'request-0001')
        self.assertNotEqual(old['id'],new['id'])
        with self.assertRaises(ApiError):self.store.get(old['id'])

    def test_restart_namespace_prevents_stale_client_id_reuse(self):
        previous=SessionStore(namespace=1)
        restarted=SessionStore(namespace=2)
        old,_=previous.create(payload(),'request-0001')
        new,_=restarted.create(payload(),'request-0001')
        self.assertNotEqual(old['id'],new['id'])
        with self.assertRaises(ApiError):restarted.get(old['id'])

    def test_forbidden_payloads(self):
        changes=[lambda p:p.update(syntheticOnly=False),lambda p:p.update(phone='FAKE'),
                 lambda p:p.update(videoLink='https://example.invalid/a'),lambda p:p.update(caseCode='UNKNOWN'),
                 lambda p:p.update(caseCode=CATALOG['POLICE'][0]['code']),lambda p:p.update(location={'latitude':0,'longitude':0}),
                 lambda p:p['consent'].update(vehicle=True),lambda p:p.update(vehicle={'id':'REAL'})]
        for change in changes:
            value=payload();change(value)
            with self.assertRaises(ApiError):self.store.create(value,'request-0001')
        with self.assertRaises(ApiError):self.store.create(payload('POLICE',video=True),'request-0001')
        with self.assertRaises(ApiError):self.store.create(payload(),'short')
        with self.assertRaises(ApiError):self.store.action('DEMO-0001',[])

    def test_returned_object_cannot_mutate_store(self):
        item,_=self.store.create(payload(vehicle=True),'request-0001')
        item['vehicle']['id']='CHANGED'
        self.assertEqual(self.store.get(item['id'])['vehicle']['id'],'DEMO-CAR-01')

    def test_catalog_matches_android_case_ids(self):
        kotlin=(ROOT/'app/src/main/java/com/lifelink119/app/core/DemoCase.kt').read_text(encoding='utf-8')
        for cases in CATALOG.values():
            for case in cases:self.assertIn('"'+case['code']+'"',kotlin)


class HttpTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.server=make_server(0)
        cls.thread=threading.Thread(target=cls.server.serve_forever,daemon=True)
        cls.thread.start()
        cls.port=cls.server.server_port

    @classmethod
    def tearDownClass(cls):
        cls.server.shutdown();cls.server.server_close();cls.thread.join(timeout=3)

    def request(self,method,path,body=None,headers=None,raw=None):
        client=http.client.HTTPConnection('127.0.0.1',self.port,timeout=5)
        h={'Content-Type':'application/json','Idempotency-Key':'http-request-0001'}
        h.update(headers or {})
        content=raw if raw is not None else json.dumps(body).encode() if body is not None else None
        client.request(method,path,content,h)
        response=client.getresponse();data=response.read();status=response.status;rh=dict(response.getheaders());client.close()
        return status,data,rh

    def test_loopback_bind_and_health(self):
        self.assertEqual(self.server.server_address[0],'127.0.0.1')
        status,data,headers=self.request('GET','/api/demo/health')
        self.assertEqual(status,200)
        self.assertEqual(json.loads(data)['mode'],'DEMO')
        self.assertNotIn('Access-Control-Allow-Origin',headers)

    def test_foreign_origin_host_and_fetch_rejected(self):
        for headers in [{'Origin':'https://example.invalid'},{'Host':'example.invalid'},{'Sec-Fetch-Site':'cross-site'}]:
            status,_,_=self.request('POST','/api/demo/sessions',payload(),headers)
            self.assertEqual(status,403)

    def test_json_content_size_and_duplicate_keys(self):
        self.assertEqual(self.request('POST','/api/demo/sessions',payload(),{'Content-Type':'text/plain'})[0],415)
        self.assertEqual(self.request('POST','/api/demo/sessions',raw=b'x'*10241)[0],413)
        self.assertEqual(self.request('POST','/api/demo/sessions',raw=b'{"syntheticOnly":true,"syntheticOnly":false}')[0],400)
        self.assertEqual(self.request('POST','/api/demo/sessions',raw=b'{broken')[0],400)
        self.assertEqual(self.request('POST','/api/demo/sessions',raw=b'[]')[0],400)

    def test_create_get_revoke_close_and_replay(self):
        self.request('POST','/api/demo/reset',{'syntheticOnly':True})
        status,data,_=self.request('POST','/api/demo/sessions',payload('VIDEO',True,True,True))
        self.assertEqual(status,201);ticket=json.loads(data)['id']
        self.assertEqual(self.request('POST','/api/demo/sessions',payload('VIDEO',True,True,True))[0],200)
        self.assertEqual(self.request('GET','/api/demo/sessions/'+ticket)[0],200)
        self.assertEqual(self.request('POST','/api/demo/sessions/'+ticket+'/revoke',{'syntheticOnly':True})[0],200)
        self.assertEqual(self.request('POST','/api/demo/sessions/'+ticket+'/close',{'syntheticOnly':True})[0],200)
        self.assertEqual(self.request('POST','/api/demo/sessions/'+ticket+'/actions',{'syntheticOnly':True,'action':'GUIDANCE'})[0],409)

    def test_static_pages_and_unknown_paths(self):
        for path in ['/','/passenger','/style.css','/app.js','/passenger.js']:
            status,data,headers=self.request('GET',path)
            self.assertEqual(status,200);self.assertTrue(data)
            self.assertIn("frame-ancestors 'none'",headers['Content-Security-Policy'])
        self.assertEqual(self.request('GET','/../server.py')[0],404)
        self.assertEqual(self.request('GET','/api/demo/sessions/DEMO-9999')[0],404)
        self.assertEqual(self.request('OPTIONS','/api/demo/sessions')[0],405)

    def test_external_navigation_to_ui_allowed_but_api_read_denied(self):
        self.assertEqual(self.request('GET','/',headers={'Sec-Fetch-Site':'cross-site'})[0],200)
        self.assertEqual(self.request('GET','/api/demo/sessions',headers={'Sec-Fetch-Site':'cross-site'})[0],403)


if __name__ == '__main__':
    unittest.main()
