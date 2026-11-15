'use strict';
const labels={EMERGENCY_119:'119 구조·구급',CONSULT_119:'119 응급의료상담',EMERGENCY_112:'112 긴급신고',COUNSEL_109:'109 심리위기 상담',REQUESTING:'연결 시연 요청',DEMO_CONNECTED:'가상 상담원 응답',SUPPORT_IN_PROGRESS:'모의 지원 중',FAILED:'연결 시연 실패',COMPLETED:'시연 완료',CANCELLED:'시연 취소'};
function node(tag,text){const e=document.createElement(tag);e.textContent=text;return e;}
async function refresh(){
  try{
    const r=await fetch('/api/demo/expansion/snapshots',{cache:'no-store'});if(!r.ok)throw Error('로컬 서버 응답 오류');
    const data=await r.json();const root=document.getElementById('snapshots');root.replaceChildren();
    for(const s of data.items){const card=node('section','');card.append(node('h2',`${s.scenarioId} · ${labels[s.target]}`),node('p',`${labels[s.status]} · 시연 ${s.id} · 상태 버전 ${s.revision}`),node('p',`동의한 항목 이름: ${s.sharedFields.join(', ')||'없음'} (값은 보내지 않음)`),node('p',`영상 ${s.video} / 공동대응 ${s.police} / 목적지 ${s.navigation} / 수어 ${s.sign} — 모두 MOCK`));root.append(card);}
    document.getElementById('status').textContent=data.items.length?`기존 연결 상태: ${data.items.length}개의 수동 스냅샷 · 앱 내 상황실에서 최신 상태 확인`:'기존 연결 상태: 게시된 스냅샷 없음';
  }catch(e){document.getElementById('status').textContent='로컬 서버에 연결하지 못했습니다. 다음 주기에 다시 확인합니다.';}
}
refresh();setInterval(refresh,3000);

async function refreshEmergency(){
  let root=document.getElementById('emergency-snapshots');
  if(!root){root=node('div','');root.id='emergency-snapshots';document.getElementById('snapshots').after(root);}
  try{
    const r=await fetch('/api/demo/emergency/snapshots',{cache:'no-store'});if(!r.ok)throw Error();
    const data=await r.json();root.replaceChildren(node('h2','응급 대기 동행 · 시뮬레이션'));
    for(const s of data.items){
      const card=node('section','');card.className='emergency-card';
      card.append(node('h2',s.scenarioLabel));
      const badge=node('p',s.callLabel+' · 시뮬레이션');badge.className='emergency-badge';card.append(badge);
      const grid=node('div','');grid.className='emergency-grid';
      const vehicle=node('div','');vehicle.append(node('h3','🚗 차량 · 합성 신호'),node('p',s.vehicle),node('p',`에어백: ${s.airbag}`),node('p',`충돌: ${s.collision}`));
      const location=node('div','');location.append(node('h3','📍 위치 · 합성 좌표'),node('p',s.location),node('p',s.road));
      grid.append(vehicle,location);card.append(grid);
      const patient=node('div','');patient.className='emergency-patient';patient.append(node('h3','환자 상태 변화 · 합성 응답, 진단 없음'));
      const observations=node('ol','');for(const line of s.handover)observations.append(node('li',line));
      patient.append(s.handover.length?observations:node('p','입력 없음'));
      if(s.escalationRequired){const alert=node('p','상태 변화를 119에 다시 알리도록 안내합니다.');alert.className='emergency-escalation';patient.append(alert);}
      card.append(patient,node('p',s.warning));
      const meta=node('small',`${s.id} · 스냅샷 버전 ${s.revision} · 고정 안내 ${s.guidanceId}`);card.append(meta);root.append(card);
    }
    if(!data.items.length)root.append(node('p','게시된 합성 시나리오 없음'));
  }catch(e){root.replaceChildren(node('p','응급 대기 상태를 가져오지 못했습니다. 다시 확인합니다.'));}
}
refreshEmergency();setInterval(refreshEmergency,3000);
