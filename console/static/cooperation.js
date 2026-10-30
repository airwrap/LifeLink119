"use strict";
(() => {
  const $=id=>document.getElementById(id), root="/api/demo/cooperation";
  const terminal=new Set(["HANDED_OVER","CANCELLED","REVOKED","WITHDRAWN","EXPIRED"]);
  const names={REQUESTED:"주변 도움 요청",CONFIRMED:"119 역할 상황 확인",INVITED:"협력 차량 초대",DECLINED:"참여 거절 · 다른 차량 검토",ACCEPTED:"자발적 참여 수락",AUTHORIZED:"응급협력 역할 부여",ON_SCENE:"안전한 현장 지원 시연",HANDED_OVER:"구급대 인계 완료",CANCELLED:"요청 취소",REVOKED:"기관 역할 철회",WITHDRAWN:"참여 철회",EXPIRED:"임무 유효기간 만료"};
  let run=null,catalog=null,busy=false,polling=false,fresh=0,beaconDeadline=0,epoch=0,vehicleChoice="",renderedSignature="";
  const query=new URLSearchParams(location.search);
  if(query.get("view")==="beacon")document.body.classList.add("poc-display-only");
  const fmt=n=>n==null?"참여 차량 없음":`${Math.floor(n/60)}분 ${Math.round(n%60)}초`;
  const signature=value=>JSON.stringify(value,(key,v)=>["interactionElapsedSeconds","missionRemainingSeconds","remainingSeconds"].includes(key)?undefined:v);
  function error(message){$("poc-error").hidden=!message;$("poc-error").textContent=message||"";$("poc-recover").hidden=true;}
  async function loadRuns(){
    try{
      const data=await api("/runs"),select=$("poc-existing"),previous=select.value||run?.id||query.get("run");
      if(data.syntheticOnly!==true||!Array.isArray(data.items))throw new Error("가상 실험 목록이 아닙니다.");
      const empty=document.createElement("option");empty.value="";empty.textContent="앱의 POC 실험 ID를 선택하세요";
      select.replaceChildren(empty,...data.items.filter(r=>/^POC-\d{16}$/.test(r.id)).map(r=>{const o=document.createElement("option");o.value=r.id;o.textContent=`${r.id} · ${r.scenario.title} · ${names[r.state]||r.state}`;return o;}));
      select.value=data.items.some(r=>r.id===previous)?previous:"";$("poc-load").disabled=!select.value;
    }catch(e){error(e.message);}
  }
  async function api(path,body,key){
    const controller=new AbortController(),timer=setTimeout(()=>controller.abort(),4500);
    try {
      const response=await fetch(root+path,{method:body?"POST":"GET",headers:{"Content-Type":"application/json",...(key?{"Idempotency-Key":key}:{})},body:body?JSON.stringify(body):undefined,signal:controller.signal,redirect:"error",cache:"no-store"});
      const value=await response.json();if(!response.ok){const e=new Error(value.message||"로컬 실험 요청을 처리하지 못했습니다.");e.status=response.status;throw e;}return value;
    } finally {clearTimeout(timer);}
  }
  function accept(value){
    if(value.syntheticOnly!==true||!/^POC-\d{16}$/.test(value.id))throw new Error("고정 가상 실험 응답이 아닙니다.");
    const same=run?.id===value.id && run?.beacon?.approvalId && run.beacon.approvalId===value.beacon?.approvalId;
    const next=performance.now()+(value.beacon?.remainingSeconds||0)*1000;
    beaconDeadline=same?Math.min(beaconDeadline,next):next;run=value;fresh=performance.now();
  }
  function indicator(){
    const b=run?.beacon;
    const vehicle=run?.candidates?.find(v=>v.id===run.vehicleId);
    const valid=run && ["AUTHORIZED","ON_SCENE"].includes(run.state) && ["DEMO-HELPER-A","DEMO-HELPER-D"].includes(run.vehicleId) && vehicle?.eligible===true && run.generation>0 && run.missionId===`SIM-MISSION-${run.id}-${run.generation}` && b?.state==="ACTIVE" && b.simulationOnly===true && b.hardwareConnected===false && b.previewEnabled===true && ["119","112"].includes(b.authority) && b.sequence>0 && b.approvalId===`SIM-COOP-LIGHT-${run.id}-${run.generation}-${run.vehicleId}-${b.sequence}` && b.remainingSeconds>0 && b.remainingSeconds<=120 && run.missionRemainingSeconds>0 && run.missionRemainingSeconds<=300 && fresh>0 && performance.now()-fresh<8000 && performance.now()<beaconDeadline && !document.hidden;
    $("poc-beacon").classList.toggle("active",!!valid);$("poc-beacon").setAttribute("aria-label",valid?"응급협력 비상표시 모형 켜짐":"비상표시 꺼짐");$("poc-beacon-state").textContent=valid?"표시 ON · 실등 미연결":"표시 OFF";
  }
  function readings(){
    if(!run)return;
    $("poc-mission").textContent=run.missionId?`${run.missionId} · 잔여 ${run.missionRemainingSeconds}초 · ${run.vehicleId}`:"차량의 수락과 119 역할의 임무 부여를 기다립니다.";
    const b=run.beacon;$("poc-beacon-note").textContent=b.approvalId?`${b.authority} 역할 · ${b.approvalId} · 잔여 ${b.remainingSeconds}초`:"역할 부여와 별도로 119·112 역할의 한시 표시 승인이 필요합니다.";
    $("poc-interaction").textContent=`현재까지 화면 조작 경과 ${run.interactionElapsedSeconds}초 · 고정 가정과 구분해 기록`;
    indicator();
  }
  function button(parent,id,label,enabled,role,action,extra={}){
    const b=document.createElement("button");b.id=id;b.textContent=label;b.disabled=busy||!enabled;
    b.onclick=()=>send(role,action,typeof extra==="function"?extra():extra);$(parent).append(b);
  }
  async function send(role,action,extra){
    if(!run||busy)return;busy=true;error("");render();
    const id=run.id,requestEpoch=++epoch;
    try{const value=await api(`/runs/${id}/${role}`,{syntheticOnly:true,action,...extra});if(requestEpoch===epoch)accept(value);}
    catch(e){fresh=0;error(e.message||"연결 실패 · 데모 서버 상태를 확인하세요.");$("poc-recover").hidden=e.status!==404;}
    finally{busy=false;render();}
  }
  function render(){
    $("poc-start").disabled=busy||!catalog||!$("poc-consent").checked||!!(run&&!terminal.has(run.state));
    $("poc-refresh").disabled=busy;$("poc-experiment").hidden=!run;
    $("poc-download").disabled=!run;$("poc-display").disabled=!run;
    if(!run){indicator();return;}
    const focus=document.activeElement?.id,state=run.state,live=!terminal.has(state),a=$("poc-authority").value;
    $("poc-title").textContent=run.scenario.title;$("poc-state").textContent=names[state]||state;$("poc-run-id").textContent=run.id+" · 가상 실험 ID";
    const steps=["도움 요청","상황 확인","초대·수락","역할 부여","현장 지원","구급대 인계"];
    const position={REQUESTED:0,CONFIRMED:1,INVITED:2,DECLINED:2,ACCEPTED:2,AUTHORIZED:3,ON_SCENE:4,HANDED_OVER:5}[state]??-1;
    $("poc-progress").replaceChildren(...steps.map((s,i)=>{const li=document.createElement("li");li.textContent=`${i+1}. ${s}`;li.className=i<=position?"done":"";return li;}));
    const p=run.projection;$("poc-baseline").textContent=fmt(p.baselineSeconds);$("poc-support").textContent=fmt(p.supportSeconds);$("poc-gain").textContent=fmt(p.potentialHeadStartSeconds);
    $("poc-assumptions").textContent=catalog.assumptions+" 비교 기준: "+(p.vehicleId||"참여 가능한 차량 없음")+" · 조건을 충족하는 차량의 가정값 · 자동 초대하지 않음";
    $("poc-comparison").textContent=p.supportSeconds==null?"참여 가능한 차량이 없습니다. 기존 119 대응을 유지합니다.":p.signedDeltaSeconds<=0?`이 조건에서는 구급대가 ${fmt(-p.signedDeltaSeconds)} 먼저 도착합니다. 시간 이득 0인 대조 조건입니다.`:`가정상 ${fmt(p.potentialHeadStartSeconds)} 먼저 도울 기회가 있습니다. 실제 시간 단축·생존 효과를 의미하지 않습니다.`;
    $("poc-car-role").textContent=run.vehicleId&&["AUTHORIZED","ON_SCENE"].includes(state)?"기관 조율 응급협력 차량 · PoC":terminal.has(state)?"응급협력 역할 종료":"협력 차량 역할 대기";
    $("poc-mission").textContent=run.missionId?`${run.missionId} · 잔여 ${run.missionRemainingSeconds}초 · ${run.vehicleId}`:"차량의 수락과 119 역할의 임무 부여를 기다립니다.";
    const beacon=run.beacon;$("poc-beacon-note").textContent=beacon.approvalId?`${beacon.authority} 역할 · ${beacon.approvalId} · 잔여 ${beacon.remainingSeconds}초`:"역할 부여와 별도로 119·112 역할의 한시 표시 승인이 필요합니다.";
    const select=$("poc-vehicle"),previous=vehicleChoice||select.value;
    select.replaceChildren(...run.candidates.map(v=>{const o=document.createElement("option");o.value=v.id;o.textContent=v.name+" · "+v.reason;o.disabled=!v.eligible;return o;}));
    select.value=run.candidates.some(v=>v.id===previous&&v.eligible)?previous:(run.candidates.find(v=>v.eligible)?.id||"");vehicleChoice=select.value;
    select.disabled=busy||!["CONFIRMED","DECLINED"].includes(state);
    $("poc-candidates").replaceChildren(...run.candidates.map(v=>{const d=document.createElement("article");d.className="poc-candidate"+(v.eligible?" eligible":"");const h=document.createElement("strong");h.textContent=v.name;const info=document.createElement("p");info.textContent=`도착 ${fmt(v.travelSeconds)} 가정 · ${v.capabilities.includes("CPR_TRAINED")?"CPR 교육 이력 가정":"교육 이력 없음"}${v.capabilities.includes("AED_CARRIER")?" · AED 보유 가정":""}`;const reason=document.createElement("p");reason.textContent=v.reason;d.append(h,info,reason);return d;}));
    for(const id of ["poc-operator-actions","poc-volunteer-actions","poc-requester-actions"])$(id).replaceChildren();
    const op=action=>({authority:$("poc-authority").value,...(action==="INVITE"?{vehicleId:$("poc-vehicle").value}:{})});
    button("poc-operator-actions","poc-confirm","상황 확인 (모의)",state==="REQUESTED"&&a==="119","operator","CONFIRM",()=>op("CONFIRM"));
    button("poc-operator-actions","poc-invite","선택 차량 초대",["CONFIRMED","DECLINED"].includes(state)&&a==="119"&&!!select.value,"operator","INVITE",()=>op("INVITE"));
    button("poc-operator-actions","poc-authorize","응급협력 역할 부여 · 300초",state==="ACCEPTED"&&a==="119","operator","AUTHORIZE",()=>op("AUTHORIZE"));
    button("poc-operator-actions","poc-approve-beacon","비상 표시 승인 · 최대 120초",["AUTHORIZED","ON_SCENE"].includes(state)&&!["APPROVED","ACTIVE"].includes(beacon.state),"operator","APPROVE_BEACON",()=>op("APPROVE_BEACON"));
    button("poc-operator-actions","poc-handover","구급대 인계 완료",state==="ON_SCENE"&&a==="119","operator","HANDOVER",()=>op("HANDOVER"));
    button("poc-operator-actions","poc-revoke","임무·표시 철회",live,"operator","REVOKE",()=>op("REVOKE"));
    const vol={vehicleId:run.vehicleId};
    $("poc-volunteer-info").textContent=run.vehicleId?`${run.vehicleId} 역할 시연 · 실제 차량·교육 자격 확인 아님`:"119 역할에서 가상 차량을 초대하면 참여를 수락하거나 거절할 수 있습니다.";
    button("poc-volunteer-actions","poc-accept","참여 수락",state==="INVITED","volunteer","ACCEPT",vol);
    button("poc-volunteer-actions","poc-decline","참여 거절",state==="INVITED","volunteer","DECLINE",vol);
    button("poc-volunteer-actions","poc-arrive","현장 도착·초기대응 지원 시연",state==="AUTHORIZED","volunteer","ARRIVE",vol);
    button("poc-volunteer-actions","poc-activate","승인된 비상 표시 켜기",["AUTHORIZED","ON_SCENE"].includes(state)&&beacon.state==="APPROVED","volunteer","ACTIVATE_BEACON",vol);
    button("poc-volunteer-actions","poc-stop","비상 표시 끄기",["APPROVED","ACTIVE"].includes(beacon.state),"volunteer","STOP_BEACON",vol);
    button("poc-volunteer-actions","poc-withdraw","참여 철회",["ACCEPTED","AUTHORIZED","ON_SCENE"].includes(state),"volunteer","WITHDRAW",vol);
    button("poc-requester-actions","poc-cancel","도움 요청 취소",live,"requester","CANCEL");
    $("poc-timeline").replaceChildren(...run.timeline.map(e=>{const li=document.createElement("li");li.textContent=`${e.elapsedSeconds}초 · ${e.label}`;return li;}));
    $("poc-interaction").textContent=`현재까지 화면 조작 경과 ${run.interactionElapsedSeconds}초 · 고정 가정과 구분해 기록`;
    renderedSignature=signature(run);indicator();if(focus)document.getElementById(focus)?.focus({preventScroll:true});
  }
  async function refresh(){
    if(busy||polling)return;polling=true;const current=run?.id||query.get("run");const requestEpoch=epoch;
    try {
      if(!catalog){catalog=await api("/catalog");$("poc-case").replaceChildren(...catalog.scenarios.map(s=>{const o=document.createElement("option");o.value=s.id;o.textContent=s.title;return o;}));await loadRuns();}
      if(current&&/^POC-\d{16}$/.test(current)){const value=await api("/runs/"+current);if(requestEpoch===epoch)accept(value);}
      $("poc-connection").textContent="localhost 실험 연결됨 · 실제 기관 미연결";error("");if(run&&signature(run)===renderedSignature)readings();else render();
    }catch(e){fresh=0;$("poc-connection").textContent="로컬 실험 연결 실패";error(e.message);$("poc-recover").hidden=e.status!==404;indicator();}finally{polling=false;}
  }
  $("poc-start").onclick=async()=>{if(busy)return;busy=true;++epoch;render();try{const value=await api("/runs",{syntheticOnly:true,scenarioId:$("poc-case").value,consent:$("poc-consent").checked},"web-poc-"+crypto.randomUUID());accept(value);$("poc-consent").checked=false;history.replaceState(null,"","/cooperation?run="+encodeURIComponent(run.id));error("");}catch(e){fresh=0;error(e.message);}finally{busy=false;render();}};
  $("poc-case").onchange=()=>{$("poc-consent").checked=false;render();};$("poc-consent").onchange=render;$("poc-authority").onchange=render;$("poc-vehicle").onchange=()=>{vehicleChoice=$("poc-vehicle").value;};$("poc-refresh").onclick=async()=>{await refresh();await loadRuns();};
  $("poc-existing").onchange=()=>{$("poc-load").disabled=!$("poc-existing").value;};
  $("poc-load").onclick=()=>{const id=$("poc-existing").value;if(/^POC-\d{16}$/.test(id))location.href="/cooperation?run="+encodeURIComponent(id);};
  $("poc-download").onclick=()=>{if(!run)return;const blob=new Blob([JSON.stringify({experiment:"LifeLink119 cooperation PoC",exportedAt:new Date().toISOString(),assumptions:catalog.assumptions,run},null,2)],{type:"application/json"});const url=URL.createObjectURL(blob),a=document.createElement("a");a.href=url;a.download=run.id+"-experiment.json";a.click();setTimeout(()=>URL.revokeObjectURL(url),1000);};
  $("poc-display").onclick=()=>{if(run)window.open("/cooperation?run="+encodeURIComponent(run.id)+"&view=beacon","_blank","noopener");};
  document.addEventListener("visibilitychange",()=>{indicator();if(!document.hidden)refresh();});
  setInterval(indicator,500);setInterval(()=>{if(!document.hidden)refresh();},2500);refresh();
})();
