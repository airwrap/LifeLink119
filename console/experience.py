"""Metadata-only, loopback snapshots of the offline Android experience.

No names, contacts, clinical text, messages, coordinates, or media are accepted.
These are manually published snapshots, never a live emergency integration.
"""
import copy
import re
import threading
import time


class ExperienceError(Exception):
    def __init__(self, status, code, message):
        super().__init__(message)
        self.status, self.code, self.message = status, code, message


SCENARIOS = {f'S{i:02d}' for i in range(1, 15)} | {f'E{i:02d}' for i in range(1, 5)}
TARGETS = {'EMERGENCY_119', 'CONSULT_119', 'EMERGENCY_112', 'COUNSEL_109'}
STATUSES = {'REQUESTING', 'DEMO_CONNECTED', 'SUPPORT_IN_PROGRESS', 'FAILED', 'COMPLETED', 'CANCELLED'}
FIELDS = {'ALIAS', 'CONDITIONS', 'MEDICATIONS', 'ALLERGIES', 'PREGNANCY', 'ACCESS', 'GUARDIAN',
          'LOCATION', 'VEHICLE', 'SUMMARY', 'TEXT', 'VIDEO'}
ENUMS = {'target': TARGETS, 'scenarioId': SCENARIOS, 'status': STATUSES,
         'video': {'OFF', 'REQUESTED', 'APPROVED', 'SHARING', 'STOPPED'},
         'videoSource': {'FRONT', 'REAR', 'PHONE'},
         'police': {'NONE', 'REQUESTED', 'REVIEWING', 'APPROVED', 'REJECTED'},
         'navigation': {'NONE', 'PROPOSED', 'CONFIRMED', 'SET'},
         'sign': {'OFF', 'REQUESTED', 'CONNECTED'}}
KEYS = set(ENUMS) | {'schemaVersion', 'syntheticOnly', 'id', 'revision', 'sharedFields'}
ID = re.compile(r'DEMO-[0-9]{4,20}\Z')


class ExperienceStore:
    def __init__(self, limit=50, ttl=3600, clock=time.monotonic):
        self.limit, self.ttl, self.clock = limit, ttl, clock
        self.items, self.revoked = {}, {}
        self.lock = threading.RLock()

    def _prune(self):
        current = self.clock()
        self.items = {k: v for k, v in self.items.items() if current-v[0] < self.ttl}
        self.revoked = {k: v for k, v in self.revoked.items() if current-v < self.ttl}

    def publish(self, payload):
        valid = isinstance(payload, dict) and set(payload) == KEYS
        if valid:
            valid = (type(payload['schemaVersion']) is int and payload['schemaVersion'] == 1
                     and payload['syntheticOnly'] is True
                     and isinstance(payload['id'], str) and ID.fullmatch(payload['id'])
                     and type(payload['revision']) is int and 1 <= payload['revision'] <= 1_000_000
                     and all(isinstance(payload[k], str) and payload[k] in v for k, v in ENUMS.items())
                     and isinstance(payload['sharedFields'], list) and len(payload['sharedFields']) <= len(FIELDS)
                     and all(isinstance(x, str) and x in FIELDS for x in payload['sharedFields'])
                     and len(set(payload['sharedFields'])) == len(payload['sharedFields']))
        if not valid:
            raise ExperienceError(422, 'METADATA_ONLY', '허용된 가상 상태·항목 이름만 전송할 수 있습니다.')
        if ((payload['video'] == 'SHARING') != ('VIDEO' in payload['sharedFields'])
                or (payload['navigation'] != 'NONE' and 'LOCATION' not in payload['sharedFields'])
                or payload['status'] in {'COMPLETED', 'CANCELLED'} and payload['sharedFields']):
            raise ExperienceError(422, 'INCONSISTENT_SNAPSHOT', '동의 항목과 모의 상태가 일치하지 않습니다.')
        with self.lock:
            self._prune()
            key = payload['id']
            if key in self.revoked:
                raise ExperienceError(409, 'SNAPSHOT_REVOKED', '철회한 세션은 다시 게시하지 않습니다. 새 세션을 시작하세요.')
            prior = self.items.get(key)
            if prior and (payload['revision'] < prior[1]['revision'] or
                          payload['revision'] == prior[1]['revision'] and payload != prior[1]):
                raise ExperienceError(409, 'STALE_SNAPSHOT', '새로운 세션 상태를 다시 전송하세요.')
            if not prior and len(self.items) >= self.limit:
                raise ExperienceError(429, 'SNAPSHOT_CAPACITY', '로컬 모의 상태 저장 한도입니다.')
            self.items[key] = (self.clock(), copy.deepcopy(payload))
            return {'syntheticOnly': True, 'id': key, 'accepted': True, 'revision': payload['revision']}

    def revoke(self, payload):
        if (not isinstance(payload, dict) or set(payload) != {'syntheticOnly', 'id'}
                or payload['syntheticOnly'] is not True or not isinstance(payload['id'], str)
                or not ID.fullmatch(payload['id'])):
            raise ExperienceError(422, 'INVALID_REVOKE', '가상 세션 식별자만 허용합니다.')
        with self.lock:
            self._prune()
            key = payload['id']
            self.items.pop(key, None)
            if key not in self.revoked and len(self.revoked) >= 200:
                raise ExperienceError(429, 'REVOCATION_CAPACITY', '로컬 철회 기록 한도입니다.')
            self.revoked[key] = self.clock()
            return {'syntheticOnly': True, 'id': key, 'revoked': True}

    def listing(self):
        with self.lock:
            self._prune()
            return [copy.deepcopy(v[1]) for v in reversed(list(self.items.values()))]
