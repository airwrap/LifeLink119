"use strict";
// Localhost role simulation only. No vehicle API, physical light, or authenticated institution.
window.DemoSignal = (() => {
  const labels = {OFF:"꺼짐",REQUESTED:"승인 요청 대기",CONFIRMED:"비상상황 확인 (모의)",APPROVED:"표시 승인 (모의)",PREVIEW_ACTIVE:"화면 점멸 표시 중 (모의)",REVOKED:"중단·승인 철회",REJECTED:"요청 거절",EXPIRED:"승인 만료"};
  const contexts = new WeakMap();
  const authorityChoices = new Map();
  function updateIndicators() {
    document.querySelectorAll(".signal-preview-dot").forEach(dot => {
      const c = contexts.get(dot);
      const active = c && c.valid && c.state === "PREVIEW_ACTIVE" && !document.hidden && c.allowed() && Date.now()-c.freshAt() < 8000 && Date.now()<c.deadline;
      dot.classList.toggle("active",!!active);
      dot.setAttribute("aria-label",active?"모의 표시등 켜짐":"모의 표시등 꺼짐");
      const state = dot.parentElement.querySelector(".signal-effective");
      if (state) state.textContent = active?"화면 표시 ON · 실제 등화 미연결":"화면 표시 OFF · 유효한 승인과 연결 확인 필요";
    });
  }
  function render(container, session, role, send, options) {
    container.replaceChildren();
    const s = session.signalPreview || {state:"OFF",remainingSeconds:0};
    const h=document.createElement("h3"); h.textContent="기관 승인형 비상 표시 (모의)";
    const note=document.createElement("p"); note.className="muted"; note.textContent="localhost 역할 연습입니다. 실제 119·112 승인, 긴급자동차 지정, 차량 등화 제어가 아닙니다. 색상·점멸 규격은 미확정입니다.";
    const status=document.createElement("p");status.textContent=(labels[s.state]||"꺼짐")+(s.authority?` · ${s.authority} 역할`:"")+(s.remainingSeconds?` · 잔여 ${s.remainingSeconds}초`:"");
    const visual=document.createElement("div");visual.className="signal-visual";
    const dot=document.createElement("span");dot.className="signal-preview-dot";dot.setAttribute("role","img");
    const effective=document.createElement("span");effective.className="signal-effective";
    visual.append(dot,effective);
    const valid=s.simulationOnly===true && s.hardwareConnected===false && ["119","112"].includes(s.authority) && s.approvalId?.startsWith("SIM-LIGHT-"+session.id+"-") && s.remainingSeconds>0 && s.remainingSeconds<=120 && s.previewEnabled===true;
    contexts.set(dot,{state:s.state,valid,deadline:options.freshAt()+s.remainingSeconds*1000,freshAt:options.freshAt,allowed:options.allowed});
    const actions=document.createElement("div");actions.className="actions";
    const canUse=()=>session.status!=="DEMO_CLOSED" && !options.busy && options.allowed();
    function add(label,action,valid,authority) {
      const b=document.createElement("button");b.textContent=label;b.dataset.focusKey="signal-"+action;
      b.disabled=!canUse()||!valid;
      b.onclick=()=>send(role,{syntheticOnly:true,action,...(role==="operator"?{authority:authority()}: {})});
      actions.append(b);
    }
    if(role==="passenger") {
      add("표시 승인 요청 (모의)","REQUEST",["OFF","REVOKED","REJECTED","EXPIRED"].includes(s.state));
      add("승인된 화면 점멸 시작", "ACTIVATE",s.state==="APPROVED" && s.remainingSeconds>0);
      add("표시 중단·승인 철회", "STOP",["REQUESTED","CONFIRMED","APPROVED","PREVIEW_ACTIVE"].includes(s.state));
    } else {
      const label=document.createElement("label"); label.textContent="모의 기관 역할 ";
      const select=document.createElement("select");select.setAttribute("aria-label","모의 표시 승인 기관");
      select.dataset.focusKey="signal-authority";
      for(const value of ["119","112"]){const opt=document.createElement("option");opt.value=value;opt.textContent=value+" 역할 (모의)";select.append(opt);}
      select.value=s.authority||authorityChoices.get(session.id)||"119";
      select.disabled=!["OFF","REQUESTED","REJECTED","REVOKED","EXPIRED"].includes(s.state)||options.busy;
      select.onchange=()=>authorityChoices.set(session.id,select.value);label.append(select);container.append(label);
      const authority=()=>select.value;
      add("비상상황 확인 (모의)","CONFIRM",s.state==="REQUESTED"&&["DEMO_REVIEWING","DEMO_GUIDANCE"].includes(session.status),authority);
      add("120초 표시 승인 (모의)","APPROVE",s.state==="CONFIRMED",authority);
      add("표시 요청 거절 (모의)","REJECT",["REQUESTED","CONFIRMED"].includes(s.state),authority);
      add("표시 승인 철회 (모의)","REVOKE",["REQUESTED","CONFIRMED","APPROVED","PREVIEW_ACTIVE"].includes(s.state),authority);
    }
    container.prepend(h,note,status,visual);
    if(s.approvalId){const id=document.createElement("p");id.className="muted";id.textContent="가짜 승인 ID: "+s.approvalId;container.append(id);}
    container.append(actions);
    updateIndicators();
  }
  setInterval(updateIndicators,500);
  document.addEventListener("visibilitychange",updateIndicators);
  return {render,updateIndicators};
})();
