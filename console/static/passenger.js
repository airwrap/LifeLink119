"use strict";
const $ = (id) => document.getElementById(id);
let catalog = {},
  step = 1,
  session = null,
  busy = false,
  key = "",
  revision = 0,
  refreshing = false;
let lastServerAt = 0, signalNetworkOk = false;
const states = {
  DEMO_CONNECTED: "모의 연결",
  DEMO_RECEIVED: "모의 접수",
  DEMO_REVIEWING: "모의 검토",
  DEMO_GUIDANCE: "모의 안내",
  DEMO_CLOSED: "모의 종료",
};
function newKey() {
  key = "web-" + crypto.randomUUID();
}
async function api(path, body) {
  const r = await fetch("/api/demo" + path, {
    method: body ? "POST" : "GET",
    headers: body
      ? { "Content-Type": "application/json", "Idempotency-Key": key }
      : {},
    body: body ? JSON.stringify(body) : undefined,
    signal: AbortSignal.timeout(5000),
  });
  const data = await r.json();
  if (!r.ok) throw new Error(data.message || "로컬 요청 실패");
  return data;
}
function error(message) {
  $("error").hidden = !message;
  if ($("error").textContent !== message) $("error").textContent = message;
}
function stage(value) {
  step = value;
  for (let i = 1; i <= 3; i++)
    $("step-" + i).className = i === step ? "active" : "";
  $("intro").hidden = step !== 1;
  $("review").hidden = step !== 2;
  $("connected").hidden = step !== 3;
}
function safety() {
  const motion = $("motion-state").value;
  $("controls").disabled = busy;
  $("controls").setAttribute("aria-busy", String(busy));
  $("driving-status").textContent = motion === "DRIVING"
    ? "이동 중 응급상황 · 동승자 조작 시연 · 실제 차량 상태 감지 아님"
    : motion === "PARKED" ? "정차 상황 · 응급지원 시연 · 실제 차량 상태 감지 아님"
    : "이동 상태 선택 전 · 응급지원 바로 사용 가능";
  window.DemoSignal?.updateIndicators();
}
function preview() {
  return [
    $("vehicle").checked
      ? "차량: 가상 차량 A / DEMO-CAR-01 · 배터리 72% (가상)"
      : "차량: 공유 안 함",
    $("location").checked
      ? "위치: 가상 훈련장 A구역 / DEMO_GRID (120, 80)"
      : "위치: 공유 안 함",
    $("video").checked
      ? "영상: 합성 도형 예시 · 실제 카메라 아님"
      : "영상: 공유 안 함",
  ].join("\n\n");
}
function options() {
  const choices = catalog[$("scenario").value] || [];
  $("case").replaceChildren();
  for (const x of choices) {
    const option = document.createElement("option");
    option.value = x.code;
    option.textContent = x.label;
    $("case").append(option);
  }
  for (const key of ["vehicle", "location", "video"]) $(key).checked = false;
  $("video-option").hidden = $("scenario").value !== "VIDEO";
  caseSummary();
  newKey();
}
function caseSummary() {
  $("case-summary").textContent =
    (catalog[$("scenario").value] || []).find((x) => x.code === $("case").value)
      ?.summary || "";
}
function renderSession() {
  if (!session) return;
  if(window.DemoSignal) DemoSignal.render($("passenger-signal"),session,"passenger",(role,body)=>action("signal/"+role,body),{busy,freshAt:()=>lastServerAt,allowed:()=>signalNetworkOk});
  else $("passenger-signal").textContent="새 표시 기능에는 v0.3 서버 재시작이 필요합니다. 기존 상담 흐름은 계속 사용할 수 있습니다.";
  $("ticket").textContent = session.id + " · " + session.agency;
  $("session-state").textContent = states[session.status];
  $("shared").textContent = [
    session.vehicle ? "가상 차량 공유됨" : "차량: 공유 안 함",
    session.location ? "가짜 격자 위치 공유됨" : "위치: 공유 안 함",
    session.videoLink ? "합성 영상 예시 공유됨" : "영상: 공유 안 함",
    session.route
      ? "가상 경로: 만남 지점 B (실제 이동 지시 아님)"
      : "가상 경로: 안내 대기",
  ].join("\n");
  $("timeline").replaceChildren();
  for (const entry of session.timeline) {
    const li = document.createElement("li");
    li.textContent = entry.label + " · " + entry.message;
    $("timeline").append(li);
  }
  $("close").disabled = session.status === "DEMO_CLOSED" || busy;
  $("revoke").disabled = !Object.values(session.consent).some(Boolean) || busy;
}
async function refresh() {
  if (!session || busy || refreshing) return;
  const expectedId = session.id;
  const expectedRevision = revision;
  const current = () => session?.id === expectedId && revision === expectedRevision;
  refreshing = true;
  try {
    const latest = await api("/sessions/" + expectedId);
    if (!current()) return;
    session = latest;
    lastServerAt = Date.now(); signalNetworkOk = true;
    error("");
    renderSession();
  } catch (e) {
    if (current()) { signalNetworkOk = false; window.DemoSignal?.updateIndicators(); error(e.message + " 새로고침하거나 새로운 상황을 시작하세요."); }
  } finally {
    refreshing = false;
  }
}
async function action(name, body={syntheticOnly:true}) {
  if (!session || busy) return;
  revision++;
  busy = true;
  safety();
  renderSession();
  try {
    session = await api("/sessions/" + session.id + "/" + name, body);
    lastServerAt = Date.now(); signalNetworkOk = true;
    error("");
  } catch (e) {
    signalNetworkOk = false; window.DemoSignal?.updateIndicators();
    error(e.message);
  } finally {
    busy = false;
    safety();
    renderSession();
  }
}
$("motion-state").onchange = safety;
$("scenario").onchange = options;
$("case").onchange = () => {
  caseSummary();
  newKey();
};
for (const field of ["vehicle", "location", "video"])
  $(field).onchange = () => {
    $("preview").textContent = preview();
    newKey();
  };
$("next").onclick = () => {
  stage(2);
  $("preview").textContent = preview();
};
$("back").onclick = () => stage(1);
$("connect").onclick = async () => {
  if (busy) return;
  busy = true;
  safety();
  $("connect").disabled = true;
  $("connect").textContent = "로컬 연결 처리 중…";
  try {
    const payload = {
      schemaVersion: "1.0",
      syntheticOnly: true,
      scenario: $("scenario").value,
      caseCode: $("case").value,
      consent: {
        vehicle: $("vehicle").checked,
        location: $("location").checked,
        video: $("video").checked,
      },
      vehicle: $("vehicle").checked
        ? { id: "DEMO-CAR-01", name: "가상 차량 A", batteryPercent: 72 }
        : null,
      location: $("location").checked
        ? {
            coordinateSystem: "DEMO_GRID",
            label: "가상 훈련장 A구역",
            x: 120,
            y: 80,
          }
        : null,
      videoLink: $("video").checked ? "demo://video/DEMO-0000" : null,
    };
    session = await api("/sessions", payload);
    lastServerAt = Date.now(); signalNetworkOk = true;
    error("");
    stage(3);
    renderSession();
  } catch (e) {
    error(
      "연결되지 않았습니다. 같은 요청으로 재시도할 수 있습니다. " + e.message,
    );
  } finally {
    busy = false;
    safety();
    $("connect").disabled = false;
    $("connect").textContent = "로컬 모의 상담센터 연결";
    renderSession();
  }
};
$("refresh").onclick = refresh;
$("close").onclick = () => action("close");
$("revoke").onclick = () => action("revoke");
$("new").onclick = () => {
  revision++;
  session = null;
  options();
  stage(1);
  error("");
};
api("/catalog")
  .then((r) => {
    catalog = r.catalog;
    options();
  })
  .catch((e) =>
    error("서버의 가상 상황 목록을 가져오지 못했습니다. " + e.message),
  );
newKey();
safety();
setInterval(() => {
  if (!document.hidden) refresh();
}, 2500);
