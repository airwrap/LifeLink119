"""Synthetic, loopback-only cooperative vehicle experiment. No real dispatch or lamp I/O."""
import copy
from datetime import datetime, timezone
import hashlib
import json
import math
from pathlib import Path
import re
import secrets
import threading
import time

CATALOG_FILE=Path(__file__).resolve().parents[1]/'app/src/main/assets/cooperation-catalog.json'
CATALOG=json.loads(CATALOG_FILE.read_text(encoding='utf-8'))
SCENARIOS={s['id']:s for s in CATALOG['scenarios']}
VEHICLES={v['id']:v for v in CATALOG['vehicles']}
TERMINAL={'HANDED_OVER','CANCELLED','REVOKED','WITHDRAWN','EXPIRED'}
RUN_ID=re.compile(r'POC-[0-9]{16}\Z')
MISSION_SECONDS=300
BEACON_SECONDS=120

class CoopError(Exception):
    def __init__(self,status,code,message):
        super().__init__(message)
        self.status,self.code,self.message=status,code,message

def fail(code,message,status=409):raise CoopError(status,code,message)
def stamp():return datetime.now(timezone.utc).isoformat(timespec='seconds').replace('+00:00','Z')

class CooperationStore:
    def __init__(self,clock=time.monotonic,limit=50,ttl=3600,namespace=None):
        self.clock,self.limit,self.ttl=clock,limit,ttl
        self.namespace=secrets.randbelow(10**12) if namespace is None else namespace
        self.lock=threading.RLock()
        self.items,self.keys={},{}
        self.sequence=0

    def _event(self,r,label):
        r['timeline'].append({'label':label,'at':stamp(),'elapsedSeconds':round(self.clock()-r['_born'],2)})

    def _off(self,r,state='OFF'):
        r['beacon'].update(state=state,previewEnabled=False,remainingSeconds=0)
        r['_beaconDeadline']=None

    def _finish(self,r,state,label):
        r['state']=state;r['missionRemainingSeconds']=0;r['_missionDeadline']=None
        self._off(r,'REVOKED');self._event(r,label)

    def _prune(self):
        expired={k for k,r in self.items.items() if self.clock()-r['_born']>=self.ttl}
        for key in expired:del self.items[key]
        self.keys={key:value for key,value in self.keys.items() if value['id'] not in expired}
        for r in self.items.values():
            if r['_missionDeadline'] is not None:
                r['missionRemainingSeconds']=max(0,math.ceil(r['_missionDeadline']-self.clock()))
                if r['missionRemainingSeconds']==0:self._finish(r,'EXPIRED','응급협력 임무 유효기간 만료 (모의)')
            if r['_beaconDeadline'] is not None:
                r['beacon']['remainingSeconds']=max(0,math.ceil(r['_beaconDeadline']-self.clock()))
                if r['beacon']['remainingSeconds']==0:
                    self._off(r,'EXPIRED');self._event(r,'비상 표시 승인 만료 (모의)')

    def create(self,body,key):
        if not isinstance(body,dict) or set(body)!={'syntheticOnly','scenarioId','consent'} or body.get('syntheticOnly') is not True or body.get('consent') is not True or not isinstance(body.get('scenarioId'),str) or body['scenarioId'] not in SCENARIOS:
            fail('POC_PAYLOAD','고정 가상 상황과 명시적 모의 위치 공유 동의가 필요합니다.',422)
        if not re.fullmatch(r'[A-Za-z0-9_-]{8,80}',key or ''):fail('POC_KEY','가상 요청 식별자가 필요합니다.',422)
        fingerprint=hashlib.sha256(json.dumps(body,sort_keys=True).encode()).hexdigest()
        with self.lock:
            self._prune()
            old=self.keys.get(key)
            if old:
                if old['fingerprint']!=fingerprint:fail('POC_KEY_CONFLICT','같은 요청 ID로 다른 실험을 만들 수 없습니다.')
                return self.get(old['id']),False
            if len(self.items)>=self.limit:fail('POC_CAPACITY','실험 기록이 가득 찼습니다. 기록 보관기간이 지나면 다시 시도하세요.',429)
            self.sequence+=1
            ident=f'POC-{self.namespace:012d}{self.sequence:04d}'
            r={'id':ident,'syntheticOnly':True,'state':'REQUESTED','scenario':copy.deepcopy(SCENARIOS[body['scenarioId']]),'vehicleId':None,
               'missionId':None,'missionRemainingSeconds':0,'generation':0,'declinedVehicleIds':[],'timeline':[],
               'beacon':{'state':'OFF','authority':None,'approvalId':None,'sequence':0,'remainingSeconds':0,'previewEnabled':False,'hardwareConnected':False,'simulationOnly':True},
               'emergencyServiceContinues':True,'clinicalOutcomeMeasured':False,'transportAuthorized':False,
               '_born':self.clock(),'_missionDeadline':None,'_beaconDeadline':None}
            self._event(r,'주변 차량 응급협력 요청 (모의)')
            self.items[ident]=r;self.keys[key]={'id':ident,'fingerprint':fingerprint}
            return self.get(ident),True

    def _candidates(self,r):
        result=[]
        for v in VEHICLES.values():
            reason='참여 가능'
            if not r['scenario']['available'] or not v['available']:reason='현재 참여 불가'
            elif not v['optedIn']:reason='자발적 참여 동의 없음'
            elif r['scenario']['requiredCapability'] not in v['capabilities']:reason='요청된 교육·장비 조건 불일치'
            elif v['id'] in r['declinedVehicleIds']:reason='이번 요청 참여 거절'
            result.append({**copy.deepcopy(v),'eligible':reason=='참여 가능','reason':reason})
        return result

    def get(self,ident):
        with self.lock:
            self._prune()
            if ident not in self.items:fail('POC_NOT_FOUND','실험이 없거나 만료됐습니다.',404)
            r=copy.deepcopy(self.items[ident]);r['candidates']=self._candidates(r)
            choices=[v for v in r['candidates'] if v['eligible']]
            selected=next((v for v in r['candidates'] if v['id']==r['vehicleId']),None)
            candidate=selected if selected and selected['eligible'] else min(choices,key=lambda v:v['travelSeconds'],default=None)
            support=candidate['travelSeconds']+r['scenario']['activationSeconds'] if candidate else None
            delta=r['scenario']['baselineSeconds']-support if support is not None else None
            r['projection']={'baselineSeconds':r['scenario']['baselineSeconds'],'supportSeconds':support,
                'vehicleId':candidate['id'] if candidate else None,
                'potentialHeadStartSeconds':max(0,delta) if delta is not None else 0,'signedDeltaSeconds':delta,
                'basis':'FIXED_SCENARIO_ASSUMPTIONS','clinicalOutcomeMeasured':False,'actualResponseTimeMeasured':False}
            r['interactionElapsedSeconds']=round(self.clock()-r['_born'],2)
            return {k:v for k,v in r.items() if not k.startswith('_')}

    def listing(self):
        with self.lock:
            self._prune()
            return [self.get(k) for k in reversed(list(self.items))]

    def action(self,ident,role,body):
        allowed={'requester':{'CANCEL'},'operator':{'CONFIRM','INVITE','AUTHORIZE','APPROVE_BEACON','REVOKE','HANDOVER'},
                 'volunteer':{'ACCEPT','DECLINE','WITHDRAW','ARRIVE','ACTIVATE_BEACON','STOP_BEACON'}}
        if not isinstance(body,dict) or body.get('syntheticOnly') is not True or not isinstance(body.get('action'),str) or body['action'] not in allowed.get(role,set()):
            fail('POC_ROLE_ACTION','이 역할의 허용된 모의 동작이 아닙니다.',422)
        action=body['action'];fields={'syntheticOnly','action'}
        if role=='operator':fields.add('authority')
        if role=='volunteer' or action=='INVITE':fields.add('vehicleId')
        # Accept the old clients' optional boolean, but it no longer gates PoC actions.
        if action in {'ARRIVE','ACTIVATE_BEACON'} and 'parked' in body:fields.add('parked')
        if set(body)!=fields:fail('POC_FIELDS','가상 동작에 허용되지 않은 필드가 있습니다.',422)
        authority=body.get('authority')
        if role=='operator' and (not isinstance(authority,str) or authority not in {'119','112'}):fail('POC_AUTHORITY','기관 역할은 119·112만 사용합니다.',422)
        if 'vehicleId' in fields and not isinstance(body['vehicleId'],str):fail('POC_VEHICLE','고정 가상 차량 ID가 필요합니다.',422)
        if role=='operator' and action not in {'APPROVE_BEACON','REVOKE'} and authority!='119':fail('POC_MEDICAL_ROLE','응급협력 임무 조율은 119 역할에서 시연합니다.',422)
        if 'parked' in fields and type(body['parked']) is not bool:fail('POC_FIELDS','이전 버전의 parked 필드는 불리언이어야 합니다.',422)
        with self.lock:
            self.get(ident);r=self.items[ident];state=r['state']
            if role=='volunteer' and body['vehicleId']!=r['vehicleId']:fail('POC_VEHICLE','초대된 가상 차량과 일치해야 합니다.')
            if state in TERMINAL:
                if (action=='CANCEL' and state=='CANCELLED') or (action=='REVOKE' and state=='REVOKED') or (action=='HANDOVER' and state=='HANDED_OVER'):return self.get(ident)
                fail('POC_TERMINAL','종료·철회·만료된 실험입니다. 새 요청을 만드세요.')
            label=action
            if action in {'CANCEL','REVOKE','WITHDRAW'}:
                self._finish(r,{'CANCEL':'CANCELLED','REVOKE':'REVOKED','WITHDRAW':'WITHDRAWN'}[action],'응급협력 중단 · '+action)
                return self.get(ident)
            if action=='CONFIRM':
                if state=='CONFIRMED':return self.get(ident)
                if state!='REQUESTED':fail('POC_ORDER','요청 후 상황 확인이 필요합니다.')
                r['state']='CONFIRMED';label='119 역할 · 상황 확인'
            elif action=='INVITE':
                if state not in {'CONFIRMED','DECLINED'}:fail('POC_ORDER','상황 확인 후 참여 차량을 초대하세요.')
                vehicle=next((v for v in self._candidates(r) if v['id']==body['vehicleId'] and v['eligible']),None)
                if vehicle is None:fail('POC_INELIGIBLE','참여 동의·교육 조건·가용성을 충족하는 차량이 필요합니다.')
                r.update(state='INVITED',vehicleId=vehicle['id'],generation=r['generation']+1)
                self._off(r);label=vehicle['name']+' 참여 초대'
            elif action in {'ACCEPT','DECLINE'}:
                if state!='INVITED':fail('POC_ORDER','선행 참여 초대가 필요합니다.')
                if action=='DECLINE':r['declinedVehicleIds'].append(r['vehicleId'])
                r['state']='ACCEPTED' if action=='ACCEPT' else 'DECLINED';label='협력 차량 · '+('자발적 참여 수락' if action=='ACCEPT' else '참여 거절')
            elif action=='AUTHORIZE':
                if state in {'AUTHORIZED','ON_SCENE'}:return self.get(ident)
                if state!='ACCEPTED':fail('POC_ACCEPT_REQUIRED','차량의 자발적 수락 후에만 임무를 부여합니다.')
                r.update(state='AUTHORIZED',missionId=f"SIM-MISSION-{ident}-{r['generation']}",missionRemainingSeconds=MISSION_SECONDS)
                r['_missionDeadline']=self.clock()+MISSION_SECONDS;label='119 역할 · 300초 응급협력 임무 부여'
            elif action=='ARRIVE':
                if state=='ON_SCENE':return self.get(ident)
                if state!='AUTHORIZED':fail('POC_MISSION_REQUIRED','유효한 기관 조율 임무가 필요합니다.')
                r['state']='ON_SCENE';label='현장 도착·초기대응 지원 (모의)'
            elif action=='HANDOVER':
                if state!='ON_SCENE':fail('POC_ORDER','현장 지원 시연 후 구급대에 인계하세요.')
                self._finish(r,'HANDED_OVER','구급대 인계 완료 (모의)');return self.get(ident)
            elif action=='APPROVE_BEACON':
                if state not in {'AUTHORIZED','ON_SCENE'}:fail('POC_MISSION_REQUIRED','임무 부여 후 표시 승인을 시연합니다.')
                if r['beacon']['state'] in {'APPROVED','ACTIVE'}:
                    if r['beacon']['authority']!=authority:fail('POC_BEACON_AUTHORITY','기존 승인 기관 역할을 유지하세요.')
                    return self.get(ident)
                r['_beaconDeadline']=min(r['_missionDeadline'],self.clock()+BEACON_SECONDS)
                sequence=r['beacon']['sequence']+1
                r['beacon'].update(state='APPROVED',sequence=sequence,authority=authority,approvalId=f"SIM-COOP-LIGHT-{ident}-{r['generation']}-{r['vehicleId']}-{sequence}",remainingSeconds=min(BEACON_SECONDS,r['missionRemainingSeconds']),previewEnabled=False)
                label=authority+' 역할 · 비상 표시 한시 승인'
            elif action=='ACTIVATE_BEACON':
                if state not in {'AUTHORIZED','ON_SCENE'} or r['beacon']['state'] not in {'APPROVED','ACTIVE'} or r['beacon']['remainingSeconds']<=0:fail('POC_BEACON_REQUIRED','해당 차량의 유효한 표시 승인이 필요합니다.')
                if r['beacon']['state']=='ACTIVE':return self.get(ident)
                r['beacon'].update(state='ACTIVE',previewEnabled=True);label='협력 차량 외부 표시 모형 ON'
            elif action=='STOP_BEACON':
                self._off(r,'REVOKED');label='협력 차량 표시 OFF'
            self._event(r,label)
            return self.get(ident)
