"""Synthetic emergency snapshots and a data-minimized, optional phrase-selection proxy.

No real patient text, coordinates, account information or audio are accepted on either endpoint.
The external protocol is vendor-neutral JSON: operators can adapt their model behind this proxy.
"""
import copy
import http.client
import ipaddress
import json
import os
import re
import socket
import threading
import time
from urllib.parse import urlsplit
from experience import ExperienceError
from llm_config import endpoint_path

STEPS = {'CONSCIOUSNESS', 'BREATHING', 'SYMPTOMS', 'WAITING'}
CONCEPTS = set('YES NO UNSURE RESPONSE CONSCIOUS EYES OPEN CLOSED BREATH NORMAL DIFFICULT CHEST PAIN BLOOD MUCH FACE ARM SPEECH SUDDEN ACCIDENT INJURY FIRE SMOKE TRAPPED ALONE FAMILY WORSE NONE'.split())
CALLS = {'PREPARING', 'ATTEMPTING', 'UNKNOWN', 'OPERATOR', 'CALLBOT', 'DELAYED', 'CALLBACK', 'REPORTED', 'DISPATCH_WAIT', 'ARRIVED'}
LABELS = dict(PREPARING='신고 준비', ATTEMPTING='119 연결 시도', UNKNOWN='연결 여부 미확인', OPERATOR='상담원 연결 사용자 확인',
              CALLBOT='AI 콜봇 응대 사용자 확인', DELAYED='연결 지연 시뮬레이션', CALLBACK='콜백 대기 사용자 확인',
              REPORTED='접수 안내 사용자 확인', DISPATCH_WAIT='출동 대기 안내 사용자 확인', ARRIVED='구조대 도착 사용자 확인')
OBS = dict(CONSCIOUS_YES='의식 있음', CONSCIOUS_NO='의식 없음', CONSCIOUS_UNKNOWN='의식 미확인',
           BREATHING_YES='정상 호흡 응답', BREATHING_NO='정상 호흡 없음', BREATHING_UNKNOWN='정상 호흡 미확인',
           BLEEDING='심한 출혈', CHEST_PAIN='가슴 통증', STROKE_SIGNS='얼굴·팔·말의 갑작스런 이상', INJURY='사고 후 부상',
           FIRE='화재·연기·갇힘', WORSENING='상태 악화', ALONE='혼자 대기', COMPANION='가족·동승자가 환자', UNKNOWN='설명 어려움')
GUIDES = dict(CONSCIOUS_NO='RESPONSE', CONSCIOUS_UNKNOWN='RESPONSE', BREATHING_NO='BREATHING', BREATHING_UNKNOWN='BREATHING',
              BLEEDING='BLEEDING', CHEST_PAIN='CHEST', STROKE_SIGNS='STROKE', INJURY='INJURY', FIRE='FIRE',
              WORSENING='WORSENING', ALONE='ALONE', COMPANION='COMPANION')
URGENT = {'RESPONSE', 'BREATHING', 'BLEEDING', 'CHEST', 'STROKE', 'FIRE', 'WORSENING'}
SCENARIOS = {'AIRBAG': '에어백 전개 사고', 'COLLISION': '에어백 미전개 충돌', 'ILLNESS': '운전자 응급질환',
             'HIGHWAY': '고속도로 긴급상황', 'NO_DATA': '위치·차량 수신 실패'}
ID = re.compile(r'DEMO-[0-9]{4,20}\Z')


class EmergencyStore:
    def __init__(self, clock=time.monotonic, ttl=3600, limit=50):
        self.clock, self.ttl, self.limit = clock, ttl, limit
        self.items, self.revoked = {}, {}
        self.lock = threading.RLock()

    def prune(self):
        now = self.clock()
        self.items = {k: v for k, v in self.items.items() if now-v[0] < self.ttl}
        self.revoked = {k: v for k, v in self.revoked.items() if now-v < self.ttl}

    def publish(self, p):
        keys = {'syntheticOnly', 'id', 'scenario', 'revision', 'callStatus', 'guidanceId', 'observations'}
        valid = isinstance(p, dict) and set(p) == keys
        if valid:
            valid = (p['syntheticOnly'] is True and isinstance(p['id'], str) and ID.fullmatch(p['id'])
                     and type(p['revision']) is int and 1 <= p['revision'] <= 1_000_000
                     and isinstance(p['scenario'], str) and p['scenario'] in SCENARIOS
                     and isinstance(p['callStatus'], str) and p['callStatus'] in CALLS
                     and isinstance(p['guidanceId'], str) and p['guidanceId'] in set(GUIDES.values()) | {'WAIT'}
                     and isinstance(p['observations'], list) and len(p['observations']) <= 24
                     and all(isinstance(code, str) and code in OBS for code in p['observations']))
        if not valid:
            raise ExperienceError(422, 'SYNTHETIC_CODES_ONLY', '선택한 합성 시나리오와 허용된 상태 코드만 받습니다.')
        guide = 'WAIT'
        for code in p['observations']:
            next_guide = GUIDES.get(code, 'WAIT')
            priority = {'FIRE': 100, 'BREATHING': 90, 'RESPONSE': 80, 'BLEEDING': 70,
                        'WORSENING': 60, 'STROKE': 50, 'CHEST': 50}
            if priority.get(next_guide, 0) >= priority.get(guide, 0):
                guide = next_guide
        if p['guidanceId'] != guide:
            raise ExperienceError(422, 'INCONSISTENT_GUIDANCE', '관찰 코드와 규칙 기반 안내가 일치하지 않습니다.')
        with self.lock:
            self.prune()
            if p['id'] in self.revoked:
                raise ExperienceError(409, 'REVOKED', '철회한 요청은 새 시연 요청에서 다시 시작하세요.')
            prior = self.items.get(p['id'])
            if prior and (p['revision'] < prior[1]['revision'] or p['revision'] == prior[1]['revision'] and p != prior[1]
                          or prior[1]['callStatus'] == 'ARRIVED' and p['callStatus'] not in {'ARRIVED', 'ATTEMPTING', 'UNKNOWN'}):
                raise ExperienceError(409, 'STALE', '최신 시연 상태를 다시 게시하세요.')
            if not prior and len(self.items) >= self.limit:
                raise ExperienceError(429, 'CAPACITY', '시연 저장 한도입니다.')
            self.items[p['id']] = (self.clock(), copy.deepcopy(p))
            return {'accepted': True, 'syntheticOnly': True, 'revision': p['revision']}

    def listing(self):
        with self.lock:
            self.prune()
            result = []
            for _, p in self.items.values():
                item = copy.deepcopy(p)
                available = p['scenario'] != 'NO_DATA'
                item.update(scenarioLabel=SCENARIOS[p['scenario']], callLabel=LABELS[p['callStatus']],
                            vehicle='가상 SOC 72% · '+('80 km/h 주행' if p['scenario'] == 'HIGHWAY' else '정차') if available else '확인 불가',
                            airbag=('전개' if p['scenario'] == 'AIRBAG' else '미전개')+' · 합성 신호, 사고/부상 판단 아님' if available else '확인 불가',
                            collision='모의 충돌 이벤트' if p['scenario'] in {'AIRBAG', 'COLLISION'} else '확인 불가',
                            location='합성 좌표 37.56650, 126.97800 · 동쪽 90° · ±20m (가상)' if available else '위치 수신 실패',
                            road='가상 고속도로 A · 실제 지명 아님' if p['scenario'] == 'HIGHWAY' else '가상 훈련장 · 실제 도로명 아님',
                            handover=[OBS[x]+' · 시연 사용자 응답' for x in p['observations']],
                            escalationRequired=guide_urgent(p),
                            warning='시뮬레이션 · 실제 기관 접수·출동·콜백 예약·예상 도착 시간 없음')
                result.append(item)
            return result

    def revoke(self, p):
        if not isinstance(p, dict) or set(p) != {'syntheticOnly', 'id'} or p['syntheticOnly'] is not True or not isinstance(p['id'], str) or not ID.fullmatch(p['id']):
            raise ExperienceError(422, 'INVALID_REVOKE', '가상 요청 식별자를 확인하세요.')
        with self.lock:
            self.prune()
            if p['id'] not in self.revoked and len(self.revoked) >= 200:
                raise ExperienceError(429, 'CAPACITY', '철회 기록 한도입니다.')
            self.items.pop(p['id'], None); self.revoked[p['id']] = self.clock()
            return {'revoked': True, 'syntheticOnly': True}

    def reset(self):
        with self.lock:
            self.items.clear(); self.revoked.clear()


def guide_urgent(p):
    return p['guidanceId'] in URGENT


def meaning_supported(step, concepts, code):
    """Lossy concepts cannot authorize a normal/positive finding without matching evidence."""
    if code is None:
        return False
    c = set(concepts)
    negative = bool(c & {'NO', 'NONE'})
    unsure = 'UNSURE' in c
    positive = not negative and not unsure
    if code in {'CONSCIOUS_UNKNOWN', 'BREATHING_UNKNOWN', 'UNKNOWN'}:
        return True
    if code == 'CONSCIOUS_YES':
        return positive and bool(c & {'RESPONSE', 'CONSCIOUS', 'OPEN'})
    if code == 'CONSCIOUS_NO':
        return not unsure and bool(c & {'RESPONSE', 'CONSCIOUS', 'EYES'}) and (negative or 'CLOSED' in c)
    if code == 'BREATHING_YES':
        return positive and {'BREATH', 'NORMAL'} <= c and 'DIFFICULT' not in c
    if code == 'BREATHING_NO':
        return not unsure and 'BREATH' in c and (negative or 'DIFFICULT' in c)
    if code == 'BLEEDING':
        return positive and {'BLOOD', 'MUCH'} <= c
    if code == 'CHEST_PAIN':
        return positive and {'CHEST', 'PAIN'} <= c
    if code == 'STROKE_SIGNS':
        return positive and 'SUDDEN' in c and bool(c & {'FACE', 'ARM', 'SPEECH'})
    if code == 'INJURY':
        return positive and {'ACCIDENT', 'INJURY'} <= c
    if code == 'WORSENING':
        return positive and ('WORSE' in c or {'BREATH', 'DIFFICULT'} <= c)
    evidence = {'FIRE': {'FIRE', 'SMOKE', 'TRAPPED'}, 'ALONE': {'ALONE'}, 'COMPANION': {'FAMILY'}}
    return positive and bool(c & evidence.get(code, set()))


class PhraseProxy:
    """Only step/style codes leave the PC. Free text cannot enter a model prompt or the UI.

    External adapter contract: POST {version, task, step, allowedPhraseIds};
    response exactly {phraseId: <one of allowedPhraseIds>}. No medical generation.
    """
    def __init__(self, env=None, clock=time.monotonic, transport=None, timeout=3.5):
        self.env = os.environ if env is None else env
        self.clock, self.transport = clock, transport or self._external
        self.calls = []
        self.lock = threading.Lock()
        self.capacity = threading.BoundedSemaphore(2)
        self.timeout = timeout

    def select(self, p):
        if (not isinstance(p, dict) or set(p) != {'version', 'consent', 'step', 'easy'}
                or type(p['version']) is not int or p['version'] != 1 or p['consent'] is not True
                or not isinstance(p['step'], str) or p['step'] not in STEPS or type(p['easy']) is not bool):
            raise ExperienceError(422, 'MINIMAL_CONSENT_REQUIRED', '동의한 질문 단계 코드만 전송할 수 있습니다.')
        allowed = [p['step'], p['step']+'_EASY']
        return self._run({'version': 1, 'task': 'choose_approved_phrase', 'step': p['step'], 'allowedPhraseIds': allowed},
                         'phraseId', allowed, allowed[1] if p['easy'] else allowed[0])

    def interpret(self, p):
        if (not isinstance(p, dict) or set(p) != {'version', 'consent', 'step', 'concepts'}
                or type(p['version']) is not int or p['version'] != 1 or p['consent'] is not True
                or not isinstance(p['step'], str) or p['step'] not in STEPS
                or not isinstance(p['concepts'], list) or not 1 <= len(p['concepts']) <= 12
                or any(not isinstance(c, str) or c not in CONCEPTS for c in p['concepts'])
                or len(set(p['concepts'])) != len(p['concepts'])):
            raise ExperienceError(422, 'MINIMAL_CONSENT_REQUIRED', '동의한 한정 의미 코드만 전송할 수 있습니다.')
        allowed = ([c for c in OBS if c.startswith('CONSCIOUS_')] if p['step'] == 'CONSCIOUSNESS' else
                   [c for c in OBS if c.startswith('BREATHING_')] if p['step'] == 'BREATHING' else
                   [c for c in OBS if not c.startswith(('CONSCIOUS_', 'BREATHING_'))])
        result = self._run({'version': 1, 'task': 'interpret_concepts', 'step': p['step'],
                          'concepts': p['concepts'], 'allowedObservationCodes': allowed}, 'observationCode', allowed, None)
        if not meaning_supported(p['step'], p['concepts'], result.get('observationCode')):
            return {'observationCode': None, 'fallback': True}
        return result

    def _run(self, payload, field, allowed, mock):
        fallback = {field: None, 'fallback': True}
        mode = self.env.get('LIFELINK_LLM_MODE', 'disabled')
        if mode == 'disabled':
            return fallback
        with self.lock:
            now = self.clock(); self.calls = [x for x in self.calls if now-x < 60]
            if len(self.calls) >= 12:
                return fallback
            self.calls.append(now)
        if mode == 'mock':
            return {field: mock, 'fallback': mock is None, 'mock': True}
        if mode != 'external' or not self.capacity.acquire(blocking=False):
            return fallback
        result_box = []
        finished = threading.Event()
        def run():
            try:
                result_box.append(self.transport(payload))
            except Exception:
                pass
            finally:
                self.capacity.release()
                finished.set()
        # At most two in-flight requests even if DNS or an upstream ignores its timeout.
        threading.Thread(target=run, daemon=True).start()
        try:
            if not finished.wait(self.timeout) or not result_box:
                return fallback
            result = result_box[0]
            if not isinstance(result, dict) or set(result) != {field} or result[field] not in allowed:
                return fallback
            return {field: result[field], 'fallback': False}
        except Exception:
            # Intentionally do not log exception, response, token, endpoint or body.
            return fallback

    def _external(self, payload):
        url = urlsplit(self.env.get('LIFELINK_LLM_URL', ''))
        key = self.env.get('LIFELINK_LLM_API_KEY', '')
        if url.scheme != 'https' or not url.hostname or url.username or url.password or url.fragment or url.query or url.port not in (None, 443) or not key or '\n' in key or '\r' in key:
            raise ValueError('invalid configuration')
        # Reject local/private endpoints. Configuration is administrator-owned, never client-supplied.
        addresses = socket.getaddrinfo(url.hostname, 443, type=socket.SOCK_STREAM)
        if not addresses or any(not ipaddress.ip_address(item[4][0]).is_global for item in addresses):
            raise ValueError('public HTTPS only')
        connection = http.client.HTTPSConnection(url.hostname, 443, timeout=3)
        try:
            protocol = self.env.get('LIFELINK_LLM_PROTOCOL', 'approved-phrase')
            request_body = self.request_body(payload, protocol, self.env.get('LIFELINK_LLM_MODEL', ''))
            connection.request('POST', endpoint_path(url.path, protocol), json.dumps(request_body), {'Content-Type': 'application/json', 'Authorization': 'Bearer '+key})
            response = connection.getresponse()
            if response.status != 200:
                raise ValueError('upstream failure')
            raw = response.read(8193)
            if len(raw) > 8192:
                raise ValueError('response limit')
            def unique(pairs):
                value = {}
                for k, v in pairs:
                    if k in value: raise ValueError('duplicate')
                    value[k] = v
                return value
            response_body = json.loads(raw, object_pairs_hook=unique)
            if protocol == 'chat-completions':
                choices = response_body['choices']
                if len(choices) != 1 or choices[0].get('finish_reason') != 'stop':
                    raise ValueError('incomplete model response')
                content = choices[0]['message']['content']
                if not isinstance(content, str) or len(content) > 256:
                    raise ValueError('response limit')
                return json.loads(content, object_pairs_hook=unique)
            return response_body
        finally:
            connection.close()

    @staticmethod
    def request_body(payload, protocol, model):
        if protocol == 'approved-phrase':
            return payload
        if protocol != 'chat-completions' or not re.fullmatch(r'[A-Za-z0-9_./:-]{1,100}', model):
            raise ValueError('invalid model protocol')
        instruction = ('Select exactly one approved phrase ID from the supplied list. Return only a JSON object with the single key phraseId.'
                       if payload['task'] == 'choose_approved_phrase' else
                       'Interpret the unordered, lossy symptom concepts for the current question. Return only a JSON object with key observationCode, selecting an allowed code, or null if uncertain. Negation and uncertainty must never be interpreted as normal. The app requires user confirmation; do not diagnose.')
        return {'model': model, 'temperature': 0, 'max_tokens': 384, 'stream': False,
                'response_format': {'type': 'json_object'}, 'messages': [
                    {'role': 'system', 'content': instruction+' Do not generate medical instructions, explanations or other text.'},
                    {'role': 'user', 'content': json.dumps(payload, separators=(',', ':'))}]}
