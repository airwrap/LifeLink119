"use strict";
const $ = (id) => document.getElementById(id);
const titles = {
  MEDICAL: "119 응급상담",
  VIDEO: "합성 영상 공유",
  TRANSPORT: "이송 협력",
  POLICE: "112 협조",
  WELLBEING: "109 상담",
};
const statuses = {
  DEMO_CONNECTED: "모의 연결",
  DEMO_RECEIVED: "모의 접수",
  DEMO_REVIEWING: "모의 검토",
  DEMO_GUIDANCE: "모의 안내",
  DEMO_CLOSED: "모의 종료",
};
let sessions = [],
  selected = null,
  busy = false,
  catalog = {},
  networkFailed = false,
  refreshGeneration = 0;
let lastServerAt = 0;
async function api(path, body) {
  const r = await fetch("/api/demo" + path, {
    method: body ? "POST" : "GET",
    headers: body ? { "Content-Type": "application/json" } : {},
    body: body ? JSON.stringify(body) : undefined,
    signal: AbortSignal.timeout(5000),
  });
  const data = await r.json();
  if (!r.ok) throw new Error(data.message || "로컬 요청에 실패했습니다.");
  return data;
}
function error(message) {
  if ($("error").textContent !== message) $("error").textContent = message;
  $("error").hidden = !message;
}
function button(label, action, disabled = false) {
  const b = document.createElement("button");
  b.textContent = label;
  b.dataset.focusKey = label;
  b.disabled = disabled || busy;
  b.onclick = action;
  return b;
}
function map(session) {
  const svg = $("map");
  svg.replaceChildren();
  svg.toggleAttribute("hidden", !session.location);
  if (!session.location) return;
  const ns = "http://www.w3.org/2000/svg";
  const add = (tag, attrs) => {
    const e = document.createElementNS(ns, tag);
    for (const [k, v] of Object.entries(attrs)) e.setAttribute(k, v);
    svg.append(e);
    return e;
  };
  for (let x = 0; x <= 320; x += 40)
    add("path", { d: `M${x} 0V220`, stroke: "#d0dee8" });
  for (let y = 0; y <= 220; y += 40)
    add("path", { d: `M0 ${y}H320`, stroke: "#d0dee8" });
  add("circle", { cx: 120, cy: 80, r: 8, fill: "#007d73" });
  if (session.route) {
    add("polyline", {
      points: session.route.points.map((p) => p.join(",")).join(" "),
      stroke: "#007d73",
      "stroke-width": 5,
      fill: "none",
    });
    add("circle", { cx: 240, cy: 160, r: 8, fill: "#ad6823" });
  }
  add("text", { x: 10, y: 207, fill: "#526779", "font-size": 12 }).textContent =
    "DEMO_GRID · 실제 지도/내비게이션 아님";
}
function renderContent() {
  const visible = sessions.filter(
    (s) => !$("filter").value || s.scenario === $("filter").value,
  );
  $("count").textContent = String(visible.length);
  $("sessions").replaceChildren();
  for (const s of visible) {
    const b = button(s.id, () => {
      selected = s.id;
      render();
    });
    b.className = selected === s.id ? "selected" : "";
    const sub = document.createElement("small");
    sub.textContent = titles[s.scenario] + " · " + statuses[s.status];
    b.append(sub);
    $("sessions").append(b);
  }
  if (!visible.length) {
    const p = document.createElement("p");
    p.className = "muted";
    p.textContent = sessions.length
      ? "필터에 맞는 세션이 없습니다."
      : "탑승자 데모에서 새 시연을 시작하세요.";
    $("sessions").append(p);
  }
  const s = sessions.find((s) => s.id === selected);
  $("empty").hidden = !!s;
  $("detail").hidden = !s;
  if (!s) return;
  if(window.DemoSignal) DemoSignal.render($("operator-signal"),s,"operator",(role,body)=>change("/signal/"+role,body),{busy,freshAt:()=>lastServerAt,allowed:()=>!networkFailed});
  else $("operator-signal").textContent="새 표시 기능에는 v0.3 서버 재시작이 필요합니다. 기존 상담 흐름은 계속 사용할 수 있습니다.";
  $("ticket").textContent = s.id;
  $("state").textContent = statuses[s.status];
  const c = (catalog[s.scenario] || []).find((c) => c.code === s.caseCode);
  $("summary").textContent =
    s.agency + " · " + titles[s.scenario] + " · " + (c ? c.label : s.caseCode);
  $("vehicle").textContent = s.vehicle
    ? `${s.vehicle.name} / ${s.vehicle.id} · 배터리 ${s.vehicle.batteryPercent}% (가상 값)`
    : "공유 안 함";
  $("location").textContent = s.location
    ? `${s.location.label} / DEMO_GRID (${s.location.x}, ${s.location.y}) · 실제 GPS 아님`
    : "공유 안 함";
  $("video-status").textContent = s.videoLink
    ? "표시 동의됨 · " + s.videoLink
    : "공유 안 함 · 실제 촬영 기능은 미연결";
  $("video").hidden = !s.videoLink;
  $("route-status").textContent = !s.location
    ? "위치 동의 후에만 가상 지점을 표시합니다."
    : s.route
      ? "가상 만남 지점 B · 모의 안내용, 실제 이동 지시 아님"
      : "모의 안내 단계에서 가상 경로가 표시됩니다.";
  map(s);
  $("timeline").replaceChildren();
  for (const e of s.timeline) {
    const li = document.createElement("li");
    const strong = document.createElement("strong");
    strong.textContent = e.label + " · ";
    li.append(strong, document.createTextNode(e.message));
    const t = document.createElement("time");
    t.dateTime = e.at;
    t.textContent = new Date(e.at).toLocaleTimeString("ko-KR");
    li.append(t);
    $("timeline").append(li);
  }
  $("actions").replaceChildren();
  const next = {
    DEMO_CONNECTED: ["모의 접수", "ACKNOWLEDGE"],
    DEMO_RECEIVED: ["검토 시작 (모의)", "REVIEW"],
    DEMO_REVIEWING: ["가상 안내 표시", "GUIDANCE"],
  }[s.status];
  if (next) {
    const b = button(next[0], () =>
      change("/actions", { syntheticOnly: true, action: next[1] }),
    );
    b.className = "primary";
    b.dataset.focusKey = "next-stage";
    $("actions").append(b);
  }
  if (s.status !== "DEMO_CLOSED")
    $("actions").append(
      button("모의 세션 종료", () => change("/close", { syntheticOnly: true })),
    );
  $("actions").append(
    button(
      "공유 정보 철회",
      () => change("/revoke", { syntheticOnly: true }),
      !Object.values(s.consent).some(Boolean),
    ),
  );
}
async function change(suffix, body) {
  if (busy || !selected) return;
  refreshGeneration++;
  busy = true;
  render();
  try {
    await api("/sessions/" + selected + suffix, body);
    error("");
    await refresh();
  } catch (e) {
    error(e.message);
  } finally {
    busy = false;
    render();
  }
}
async function refresh(manual = false) {
  const generation = ++refreshGeneration;
  try {
    const r = await api("/sessions");
    if (generation !== refreshGeneration) return;
    const changed = JSON.stringify(sessions) !== JSON.stringify(r.items);
    const oldSelection = selected;
    sessions = r.items;
    lastServerAt = Date.now();
    if (!selected && sessions.length) selected = sessions[0].id;
    if (selected && !sessions.some((s) => s.id === selected))
      selected = sessions[0]?.id || null;
    if ($("connection").textContent !== "로컬 연결됨 · 자동 갱신 중")
      $("connection").textContent = "로컬 연결됨 · 자동 갱신 중";
    if (manual || networkFailed) error("");
    networkFailed = false;
    if (changed || oldSelection !== selected) render();
  } catch (e) {
    if (generation !== refreshGeneration) return;
    networkFailed = true;
    window.DemoSignal?.updateIndicators();
    $("connection").textContent = "로컬 연결 끊김";
    error("상담센터 서버를 확인한 뒤 새로고침하세요. " + e.message);
  }
}
$("refresh").onclick = () => refresh(true);
$("filter").onchange = render;
$("reset").onclick = async () => {
  if (busy) return;
  if (!window.confirm("가짜 시연 기록을 모두 지울까요?")) return;
  try {
    await api("/reset", { syntheticOnly: true });
    selected = null;
    await refresh(true);
  } catch (e) {
    error(e.message);
  }
};
api("/catalog")
  .then((r) => {
    catalog = r.catalog;
    render();
  })
  .catch((e) => error(e.message));
refresh();
setInterval(() => {
  if (!document.hidden && !busy) refresh();
}, 2500);

let deferredFocus = null;
function render() {
  const key = document.activeElement?.dataset?.focusKey || deferredFocus;
  renderContent();
  if (key) {
    const target = Array.from(
      document.querySelectorAll("[data-focus-key]"),
    ).find((b) => b.dataset.focusKey === key);
    if (target && !target.disabled) {
      target.focus({ preventScroll: true });
      deferredFocus = null;
    } else {
      deferredFocus = busy ? key : null;
      if (!busy && !$("detail").hidden) {
        const fallback =
          document.querySelector("#actions button:not(:disabled)") ||
          $("ticket");
        fallback.tabIndex = 0;
        fallback.focus({ preventScroll: true });
      }
    }
  }
}
