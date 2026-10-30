'use strict';
const labels={EMERGENCY_119:'119 구조·구급',CONSULT_119:'119 응급의료상담',EMERGENCY_112:'112 긴급신고',COUNSEL_109:'109 심리위기 상담',REQUESTING:'연결 시연 요청',DEMO_CONNECTED:'가상 상담원 응답',SUPPORT_IN_PROGRESS:'모의 지원 중',FAILED:'연결 시연 실패',COMPLETED:'시연 완료',CANCELLED:'시연 취소'};
function node(tag,text){const e=document.createElement(tag);e.textContent=text;return e;}
async function refresh(){
  try{
    const r=await fetch('/api/demo/expansion/snapshots',{cache:'no-store'});if(!r.ok)throw Error('로컬 서버 응답 오류');
    const data=await r.json();const root=document.getElementById('snapshots');root.replaceChildren();
    for(const s of data.items){const card=node('section','');card.append(node('h2',`${s.scenarioId} · ${labels[s.target]}`),node('p',`${labels[s.status]} · 시연 ${s.id} · 상태 버전 ${s.revision}`),node('p',`동의한 항목 이름: ${s.sharedFields.join(', ')||'없음'} (값은 보내지 않음)`),node('p',`영상 ${s.video} / 공동대응 ${s.police} / 목적지 ${s.navigation} / 수어 ${s.sign} — 모두 MOCK`));root.append(card);}
    document.getElementById('status').textContent=data.items.length?`${data.items.length}개의 수동 스냅샷 · 앱 내 상황실에서 최신 상태 확인`:'게시된 스냅샷 없음 · 차량 앱의 가상 상황실에서 선택적으로 전송';
  }catch(e){document.getElementById('status').textContent='로컬 서버에 연결하지 못했습니다. 다음 주기에 다시 확인합니다.';}
}
refresh();setInterval(refresh,3000);
