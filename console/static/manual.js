"use strict";
const m=id=>document.getElementById(id);
let manual, currentGuide="first";
function showGuide(id,focus=false){
  const guide=manual.guides.find(g=>g.id===id);if(!guide)return;
  currentGuide=id;
  document.querySelectorAll("#manual-topics button").forEach(b=>b.setAttribute("aria-pressed",String(b.dataset.guide===id)));
  m("manual-title").textContent=guide.title;m("manual-audience").textContent=guide.audience;
  m("manual-steps").replaceChildren(...guide.steps.map(text=>{const li=document.createElement("li");li.textContent=text;return li;}));
  m("manual-caution").textContent=guide.caution;
  m("manual-version").textContent="자료 확인일: "+manual.version+" · 의료진 검토 전";
  m("manual-sources").replaceChildren(...guide.sources.map(key=>{const li=document.createElement("li"),a=document.createElement("a");a.href=manual.sources[key].url;a.textContent=manual.sources[key].title;a.target="_blank";a.rel="noopener noreferrer";li.append(a);return li;}));
  if(focus)m("manual-title").focus();
}
async function loadManual(){
  try{const r=await fetch("/api/demo/manual",{signal:AbortSignal.timeout(5000)});if(!r.ok)throw Error();manual=await r.json();m("manual-notice").textContent=manual.notice;m("manual-scope").textContent=manual.scope;
    m("manual-topics").replaceChildren(...manual.guides.map(g=>{const b=document.createElement("button");b.textContent=g.title;b.dataset.guide=g.id;b.onclick=()=>showGuide(g.id,true);return b;}));
    m("manual-error").hidden=true;m("manual-retry").hidden=true;showGuide(currentGuide);m("manual-content").hidden=false;
  }catch{m("manual-error").textContent="자료를 읽지 못했습니다. 로컬 서버를 확인하고 다시 시도하세요.";m("manual-error").hidden=false;m("manual-retry").hidden=false;}
}
m("manual-retry").onclick=loadManual;loadManual();
