"""Loopback-only synthetic LifeLink119 console. No external client or real emergency adapter."""
from __future__ import annotations

import argparse
import copy
import hashlib
from datetime import datetime, timezone
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
import math
import os
import logging
import traceback
from pathlib import Path
import re
import secrets
import socket
import sys
import threading
import time
from urllib.parse import urlsplit

from jsonschema import Draft202012Validator
from experience import ExperienceStore, ExperienceError
from emergency import EmergencyStore, PhraseProxy
from llm_config import load_settings
from cooperation import CooperationStore, CoopError, RUN_ID as COOP_ID, CATALOG as COOP_CATALOG

ROOT = Path(__file__).resolve().parents[1]
STATIC = Path(__file__).resolve().parent / "static"
CATALOG = json.loads((ROOT / "docs/fixtures/scenario-catalog.json").read_text(encoding="utf-8"))
SCHEMA = json.loads((ROOT / "docs/contracts/demo-session-request.schema.json").read_text(encoding="utf-8"))
VALIDATOR = Draft202012Validator(SCHEMA)
MANUAL_FILE = ROOT / "app/src/main/assets/emergency-manual.json"
SIGNAL_TTL = 120
MAX_TIMELINE = 10
VERSION = re.search(r'versionName\s*=\s*"([^"]+)"', (ROOT / 'app/build.gradle.kts').read_text(encoding='utf-8')).group(1)
LOGGER = logging.getLogger('lifelink.console')


class LoopbackHttpServer(ThreadingHTTPServer):
    # Windows SO_REUSEADDR can bind a second live listener to an occupied port.
    # Reserve it exclusively so an old console cannot silently receive new API calls.
    allow_reuse_address = sys.platform != 'win32'

    def server_bind(self):
        if sys.platform == 'win32':
            self.socket.setsockopt(socket.SOL_SOCKET, socket.SO_EXCLUSIVEADDRUSE, 1)
        super().server_bind()


def new_signal():
    return {"state":"OFF", "authority":None, "approvalId":None, "remainingSeconds":0,
            "previewEnabled":False, "hardwareConnected":False, "simulationOnly":True}
AGENCIES = {"MEDICAL": "119 (모의)", "VIDEO": "119 (모의)", "TRANSPORT": "119 (모의)", "POLICE": "112 (모의)", "WELLBEING": "109 (모의)"}
ACTIONS = {
    "ACKNOWLEDGE": ("DEMO_RECEIVED", "모의 접수", "가상 상담원이 요청을 확인했습니다."),
    "REVIEW": ("DEMO_REVIEWING", "모의 검토", "가상 상황을 검토하는 절차입니다. 출동·이송·호송 승인이 아닙니다."),
    "GUIDANCE": ("DEMO_GUIDANCE", "모의 안내", "가상 만남 지점과 다음 절차를 확인하세요. 실제 상황은 전문기관의 판단을 따릅니다."),
    "CLOSE": ("DEMO_CLOSED", "모의 종료", "모의 세션을 종료했습니다."),
}
ORDER = ["DEMO_CONNECTED", "DEMO_RECEIVED", "DEMO_REVIEWING", "DEMO_GUIDANCE", "DEMO_CLOSED"]
ID = re.compile(r"DEMO-[0-9]{4,}\Z")
KEY = re.compile(r"[A-Za-z0-9_-]{8,80}\Z")


def now():
    return datetime.now(timezone.utc).isoformat(timespec="seconds").replace("+00:00", "Z")


class ApiError(Exception):
    def __init__(self, status, code, message):
        super().__init__(message)
        self.status, self.code, self.message = status, code, message


class SessionStore:
    @staticmethod
    def _event(session, event):
        session['timeline'].append(event)
        del session['timeline'][:-MAX_TIMELINE]

    def __init__(self, limit=50, ttl=3600, clock=time.monotonic, namespace=None):
        self.limit, self.ttl, self.clock = limit, ttl, clock
        self.lock = threading.RLock()
        self.items, self.keys = {}, {}
        self.sequence = 0
        self.namespace = secrets.randbelow(10**12) if namespace is None else namespace

    def _prune(self):
        expired = {key for key, value in self.items.items() if self.clock() - value["born"] >= self.ttl}
        for key in expired:
            del self.items[key]
        self.keys = {k: v for k, v in self.keys.items() if v["id"] not in expired}
        for entry in self.items.values():
            signal = entry["session"]["signalPreview"]
            deadline = entry.get("signalDeadline")
            if signal["state"] in {"APPROVED", "PREVIEW_ACTIVE"} and deadline is not None:
                signal["remainingSeconds"] = max(0, math.ceil(deadline-self.clock()))
                if signal["remainingSeconds"] == 0:
                    self._stop_signal(entry["session"], "EXPIRED", "모의 표시 승인 만료")

    def _stop_signal(self, session, state="REVOKED", label="모의 표시 승인 철회"):
        signal = session["signalPreview"]
        if signal["state"] not in {"OFF", "REVOKED", "EXPIRED", "REJECTED"}:
            signal.update(state=state, previewEnabled=False, remainingSeconds=0)
            session["updatedAt"] = now()
            self._event(session, {"status":session["status"], "label":label,
                "message":"화면의 표시등 시연을 중단했습니다. 실제 차량 등화와 연결하지 않습니다.", "at":now()})

    def create(self, payload, key):
        if not KEY.fullmatch(key or ""):
            raise ApiError(422, "INVALID_REQUEST_KEY", "8~80자의 가상 요청 식별자가 필요합니다.")
        if not isinstance(payload, dict) or not VALIDATOR.is_valid(payload):
            raise ApiError(422, "INVALID_SYNTHETIC_PAYLOAD", "허용된 가상 데이터 형식을 확인하세요.")
        scenario = payload["scenario"]
        case = payload.get("caseCode", CATALOG[scenario][0]["code"])
        if case not in {x["code"] for x in CATALOG[scenario]}:
            raise ApiError(422, "INVALID_CASE", "시나리오에 해당하는 가상 상황을 선택하세요.")
        if payload["consent"]["video"] and scenario != "VIDEO":
            raise ApiError(422, "VIDEO_SCENARIO_ONLY", "합성 영상 예시는 영상 시나리오에서만 사용할 수 있습니다.")
        for consent, field in (("vehicle", "vehicle"), ("location", "location"), ("video", "videoLink")):
            if payload["consent"][consent] != (payload[field] is not None):
                raise ApiError(422, "CONSENT_DATA_MISMATCH", "동의 항목과 가상 데이터가 일치하지 않습니다.")
        canonical = hashlib.sha256(json.dumps(payload, ensure_ascii=False, sort_keys=True).encode("utf-8")).hexdigest()
        with self.lock:
            self._prune()
            previous = self.keys.get(key)
            if previous:
                if previous["payload"] != canonical:
                    raise ApiError(409, "REQUEST_KEY_CONFLICT", "같은 요청 식별자로 다른 정보를 보낼 수 없습니다.")
                return self.get(previous["id"]), False
            if len(self.items) >= self.limit:
                raise ApiError(429, "DEMO_CAPACITY", "가상 세션이 가득 찼습니다. 데모 기록을 초기화하거나 만료 후 다시 시도하세요.")
            self.sequence += 1
            ticket = f"DEMO-{self.namespace:012d}{self.sequence:04d}"
            stamp = now()
            session = copy.deepcopy(payload)
            session.update(id=ticket, agency=AGENCIES[scenario], caseCode=case,
                           status="DEMO_CONNECTED", createdAt=stamp, updatedAt=stamp,
                           timeline=[{"status": "DEMO_CONNECTED", "label": "모의 연결", "message": "고정 가상 정보만 로컬 상담센터에 전달됐습니다.", "at": stamp}],
                           route=None, signalPreview=new_signal())
            if session["videoLink"]:
                session["videoLink"] = "demo://video/" + ticket
            self.items[ticket] = {"born": self.clock(), "session": session, "signalSequence": 0}
            self.keys[key] = {"id": ticket, "payload": canonical}
            return copy.deepcopy(session), True

    def get(self, ticket):
        with self.lock:
            self._prune()
            if ticket not in self.items:
                raise ApiError(404, "DEMO_NOT_FOUND", "세션이 없거나 만료됐습니다. 목록으로 돌아가 새 시연을 시작하세요.")
            return copy.deepcopy(self.items[ticket]["session"])

    def listing(self):
        with self.lock:
            self._prune()
            return [copy.deepcopy(x["session"]) for x in reversed(list(self.items.values()))]

    def action(self, ticket, action):
        if not isinstance(action, str) or action not in ACTIONS:
            raise ApiError(422, "INVALID_ACTION", "허용된 모의 상담 동작을 선택하세요.")
        with self.lock:
            self.get(ticket)
            session = self.items[ticket]["session"]
            status, label, message = ACTIONS[action]
            old = ORDER.index(session["status"])
            new = ORDER.index(status)
            if new == old:
                return copy.deepcopy(session)
            if old == len(ORDER)-1 or (action != "CLOSE" and new != old+1):
                raise ApiError(409, "INVALID_TRANSITION", "현재 단계에서 수행할 수 없습니다. 새로고침 후 순서대로 진행하세요.")
            stamp = now()
            session["status"], session["updatedAt"] = status, stamp
            self._event(session, {"status": status, "label": label, "message": message, "at": stamp})
            if action == "GUIDANCE" and session["consent"]["location"]:
                session["route"] = {"coordinateSystem": "DEMO_GRID", "destination": "가상 만남 지점 B", "points": [[120,80],[180,80],[180,160],[240,160]], "simulationOnly": True}
            if action == "CLOSE":
                self._stop_signal(session)
            return copy.deepcopy(session)

    def revoke(self, ticket):
        with self.lock:
            self.get(ticket)
            session = self.items[ticket]["session"]
            session.update(consent={"vehicle":False,"location":False,"video":False}, vehicle=None, location=None, videoLink=None, route=None, updatedAt=now())
            self._stop_signal(session)
            if not any(x["label"] == "모의 공유 철회" for x in session["timeline"]):
                self._event(session, {"status":session["status"],"label":"모의 공유 철회","message":"차량·위치·합성 영상 예시를 메모리에서 제거했습니다.","at":now()})
            return copy.deepcopy(session)

    def signal_action(self, ticket, role, body):
        """Unauthenticated localhost ROLE SIMULATION. Never an agency approval or lamp command."""
        fields = {"syntheticOnly","action"} | ({"authority"} if role == "operator" else set())
        if not isinstance(body, dict) or set(body) != fields or body.get("syntheticOnly") is not True:
            raise ApiError(422,"INVALID_SIGNAL_PAYLOAD","모의 표시 요청 형식을 확인하세요.")
        action = body["action"]
        allowed = {"passenger":{"REQUEST","ACTIVATE","STOP"}, "operator":{"CONFIRM","APPROVE","REJECT","REVOKE"}}
        if not isinstance(action, str) or action not in allowed.get(role,set()):
            raise ApiError(422,"SIGNAL_ROLE_ACTION","이 역할에서는 해당 표시 시연 동작을 수행할 수 없습니다.")
        authority = body.get("authority")
        if role == "operator" and authority not in ("119","112"):
            raise ApiError(422,"SIGNAL_AUTHORITY","모의 확인·승인 역할은 119 또는 112만 선택합니다.")
        with self.lock:
            self.get(ticket)
            entry = self.items[ticket]
            session = entry["session"]
            signal = session["signalPreview"]
            state = signal["state"]
            if session["status"] == "DEMO_CLOSED":
                raise ApiError(409,"SIGNAL_CLOSED","종료한 세션에는 표시 승인을 요청할 수 없습니다.")
            if action == "STOP":
                self._stop_signal(session)
                return self.get(ticket)
            if action == "REVOKE":
                self._stop_signal(session)
                return self.get(ticket)
            if action == "REQUEST":
                if state == "REQUESTED": return self.get(ticket)
                if state not in {"OFF","REVOKED","EXPIRED","REJECTED"}:
                    raise ApiError(409,"SIGNAL_PENDING","진행 중인 모의 승인을 먼저 중단하세요.")
                session["signalPreview"] = new_signal()
                signal = session["signalPreview"]
                signal["state"] = "REQUESTED"
                entry["signalDeadline"] = None
                label = "표시 승인 요청 (모의)"
            elif action == "CONFIRM":
                if session["status"] not in {"DEMO_REVIEWING","DEMO_GUIDANCE"}:
                    raise ApiError(409,"SIGNAL_REVIEW_REQUIRED","모의 상담 검토 단계부터 비상상황 확인을 시연하세요.")
                if state == "CONFIRMED" and signal["authority"] == authority: return self.get(ticket)
                if state != "REQUESTED":
                    raise ApiError(409,"SIGNAL_REQUEST_REQUIRED","먼저 탑승자의 표시 승인 요청이 필요합니다.")
                signal.update(state="CONFIRMED",authority=authority)
                label = "비상상황 확인 (모의)"
            elif action == "APPROVE":
                if signal["authority"] != authority:
                    raise ApiError(409,"SIGNAL_AUTHORITY_MISMATCH","비상상황을 확인한 모의 기관 역할과 일치해야 합니다.")
                if state in {"APPROVED","PREVIEW_ACTIVE"}: return self.get(ticket) # replay never extends expiry
                if state != "CONFIRMED":
                    raise ApiError(409,"SIGNAL_CONFIRM_REQUIRED","비상상황 확인 후에만 모의 표시를 승인할 수 있습니다.")
                entry["signalDeadline"] = self.clock()+SIGNAL_TTL
                self.items[ticket]['signalSequence'] += 1
                signal.update(state="APPROVED",approvalId=f"SIM-LIGHT-{ticket}-{self.items[ticket]['signalSequence']}",remainingSeconds=SIGNAL_TTL)
                label = "120초 표시 승인 (모의)"
            elif action == "ACTIVATE":
                if state == "PREVIEW_ACTIVE": return self.get(ticket)
                if state != "APPROVED" or signal["remainingSeconds"] <= 0:
                    raise ApiError(409,"SIGNAL_APPROVAL_REQUIRED","유효한 119·112 역할의 모의 승인 이후에만 화면 표시를 시작할 수 있습니다.")
                signal.update(state="PREVIEW_ACTIVE",previewEnabled=True)
                label = "화면 점멸 표시 시작 (모의)"
            else: # REJECT
                if state not in {"REQUESTED","CONFIRMED"}:
                    raise ApiError(409,"SIGNAL_REQUEST_REQUIRED","검토할 모의 표시 요청이 없습니다.")
                signal.update(state="REJECTED",authority=authority,previewEnabled=False,remainingSeconds=0)
                label = "표시 요청 거절 (모의)"
            session["updatedAt"] = now()
            self._event(session, {"status":session["status"], "label":label,
                "message":"localhost 역할 연습입니다. 실제 기관 승인·긴급자동차 지정·차량 등화 제어가 아닙니다.", "at":now()})
            return self.get(ticket)

    def reset(self):
        with self.lock:
            self.items.clear()
            self.keys.clear()
            # Keep monotonic IDs so an old client cannot accidentally address a new session.


def make_server(port=8781, store=None, read_delay=0):
    store = store or SessionStore()
    cooperation = CooperationStore()
    experience = ExperienceStore()
    emergency = EmergencyStore()
    phrases = PhraseProxy()

    class Handler(BaseHTTPRequestHandler):
        protocol_version = "HTTP/1.1"

        def log_message(self, *_):
            pass

        def send_data(self, status, content, mime="application/json; charset=utf-8"):
            data = json.dumps(content, ensure_ascii=False).encode("utf-8") if mime.startswith("application/json") else content
            self.send_response(status)
            self.send_header("Content-Type", mime)
            self.send_header("Content-Length", str(len(data)))
            self.send_header("Cache-Control", "no-store")
            self.send_header("X-Content-Type-Options", "nosniff")
            self.send_header("Referrer-Policy", "no-referrer")
            self.send_header("Content-Security-Policy", "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; connect-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'")
            self.end_headers()
            self.wfile.write(data)

        def boundary(self):
            expected = {f"127.0.0.1:{self.server.server_port}", f"localhost:{self.server.server_port}"}
            if self.headers.get("Host") not in expected:
                raise ApiError(403, "LOOPBACK_HOST_ONLY", "localhost 데모 주소만 허용합니다.")
            origin = self.headers.get("Origin")
            if origin and origin not in {"http://"+x for x in expected}:
                raise ApiError(403, "SAME_ORIGIN_ONLY", "같은 로컬 화면에서만 사용할 수 있습니다.")
            # A link/bookmark may navigate to the public demo UI from another site.
            # API reads and all state changes still require same-origin/absent Origin.
            if self.headers.get("Sec-Fetch-Site") == "cross-site" and (self.command != "GET" or urlsplit(self.path).path.startswith("/api/")):
                raise ApiError(403, "SAME_ORIGIN_ONLY", "외부 웹페이지 요청은 허용하지 않습니다.")

        def body(self):
            if self.headers.get("Content-Type", "").split(";")[0].strip() != "application/json":
                raise ApiError(415, "JSON_ONLY", "JSON 요청만 허용합니다.")
            if self.headers.get("Transfer-Encoding"):
                raise ApiError(400, "FIXED_BODY_REQUIRED", "고정 길이 JSON 요청만 허용합니다.")
            try:
                size = int(self.headers.get("Content-Length", "0"))
            except ValueError:
                raise ApiError(400, "INVALID_LENGTH", "요청 길이를 확인하세요.")
            if not 1 <= size <= 10_240:
                raise ApiError(413, "BODY_LIMIT", "요청 본문은 10KB 이하여야 합니다.")
            try:
                def unique(pairs):
                    value = {}
                    for key, item in pairs:
                        if key in value:
                            raise ValueError("duplicate key")
                        value[key] = item
                    return value
                raw = self.rfile.read(size)
                self.body_consumed = True
                result = json.loads(raw.decode("utf-8"), object_pairs_hook=unique,
                                    parse_constant=lambda _: (_ for _ in ()).throw(ValueError("invalid number")))
                if not isinstance(result, dict):
                    raise ValueError("object required")
                return result
            except (ValueError, UnicodeError, RecursionError):
                raise ApiError(400, "INVALID_JSON", "유효한 JSON 객체를 보내세요.")

        def dispatch(self):
            self.connection.settimeout(5)
            self.body_consumed = False
            self.boundary()
            path = urlsplit(self.path).path
            if self.command == "GET":
                if path == "/api/demo/emergency/snapshots":
                    return self.send_data(200, {"syntheticOnly": True, "items": emergency.listing()})
                if path == "/api/demo/expansion/snapshots":
                    return self.send_data(200, {"syntheticOnly":True,"items":experience.listing()})
                if path == "/api/demo/health":
                    return self.send_data(200, {"mode":"DEMO","syntheticOnly":True,"version":VERSION,"bind":"loopback","sessions":len(store.listing())})
                if path == "/api/demo/cooperation/catalog":
                    return self.send_data(200, COOP_CATALOG)
                if path == "/api/demo/cooperation/runs":
                    return self.send_data(200, {"syntheticOnly":True,"items":cooperation.listing()})
                if path.startswith("/api/demo/cooperation/runs/") and COOP_ID.fullmatch(path.rsplit("/",1)[1]):
                    return self.send_data(200, cooperation.get(path.rsplit("/",1)[1]))
                if path == "/api/demo/manual":
                    return self.send_data(200, json.loads(MANUAL_FILE.read_text(encoding="utf-8")))
                if path == "/api/demo/catalog":
                    return self.send_data(200, {"mode":"DEMO","catalog":CATALOG})
                if path == "/api/demo/sessions":
                    return self.send_data(200, {"mode":"DEMO","items":store.listing()})
                if path.startswith("/api/demo/sessions/") and ID.fullmatch(path.rsplit("/",1)[1]):
                    if read_delay:
                        time.sleep(read_delay)
                    return self.send_data(200, store.get(path.rsplit("/",1)[1]))
                resources = {"/":("index.html","text/html; charset=utf-8"), "/passenger":("passenger.html","text/html; charset=utf-8"), "/app.js":("app.js","text/javascript; charset=utf-8"), "/passenger.js":("passenger.js","text/javascript; charset=utf-8"), "/style.css":("style.css","text/css; charset=utf-8")}
                resources.update({"/manual":("manual.html","text/html; charset=utf-8"), "/manual.js":("manual.js","text/javascript; charset=utf-8"), "/signal.js":("signal.js","text/javascript; charset=utf-8")})
                resources.update({"/cooperation":("cooperation.html","text/html; charset=utf-8"),"/cooperation.js":("cooperation.js","text/javascript; charset=utf-8"),"/cooperation.css":("cooperation.css","text/css; charset=utf-8")})
                resources.update({"/experience-console":("experience.html","text/html; charset=utf-8"),"/experience.js":("experience.js","text/javascript; charset=utf-8")})
                if path in resources:
                    name, mime = resources[path]
                    return self.send_data(200, (STATIC/name).read_bytes(), mime)
            elif self.command == "POST":
                body = self.body()
                if path == "/api/demo/emergency/snapshots":
                    return self.send_data(200, emergency.publish(body))
                if path == "/api/demo/emergency/revoke":
                    return self.send_data(200, emergency.revoke(body))
                if path == "/api/demo/emergency/phrase":
                    return self.send_data(200, phrases.select(body))
                if path == "/api/demo/emergency/interpret":
                    return self.send_data(200, phrases.interpret(body))
                if path == "/api/demo/expansion/snapshots":
                    return self.send_data(200,experience.publish(body))
                if path == "/api/demo/expansion/revoke":
                    return self.send_data(200,experience.revoke(body))
                if path == "/api/demo/cooperation/runs":
                    run,created=cooperation.create(body,self.headers.get("Idempotency-Key"))
                    return self.send_data(201 if created else 200,run)
                coop_match=re.fullmatch(r"/api/demo/cooperation/runs/(POC-[0-9]{16})/(requester|operator|volunteer)",path)
                if coop_match:
                    return self.send_data(200,cooperation.action(*coop_match.groups(),body))
                if path == "/api/demo/sessions":
                    session, created = store.create(body, self.headers.get("Idempotency-Key"))
                    return self.send_data(201 if created else 200, session)
                if path == "/api/demo/reset" and body == {"syntheticOnly": True}:
                    store.reset()
                    cooperation.reset()
                    experience.reset()
                    emergency.reset()
                    return self.send_data(200, {"mode":"DEMO","cleared":True})
                signal_match = re.fullmatch(r"/api/demo/sessions/(DEMO-[0-9]{4,})/signal/(passenger|operator)",path)
                if signal_match:
                    ticket, role = signal_match.groups()
                    return self.send_data(200, store.signal_action(ticket,role,body))
                match = re.fullmatch(r"/api/demo/sessions/(DEMO-[0-9]{4,})/(actions|close|revoke)", path)
                if match:
                    ticket, action = match.groups()
                    if action in {"close", "revoke"} and body == {"syntheticOnly":True}:
                        return self.send_data(200, store.action(ticket,"CLOSE") if action == "close" else store.revoke(ticket))
                    if action == "actions" and set(body) == {"syntheticOnly","action"} and body["syntheticOnly"] is True:
                        return self.send_data(200, store.action(ticket, body["action"]))
                    raise ApiError(422,"INVALID_ACTION_PAYLOAD","모의 상담 동작 형식을 확인하세요.")
            raise ApiError(404,"NOT_FOUND","요청한 데모 경로를 찾을 수 없습니다.")

        def do_GET(self):
            try:
                self.dispatch()
            except (ApiError,CoopError,ExperienceError) as error:
                self.close_connection = True
                # Closing a Windows socket with an unread POST body can reset it
                # before the client receives the rejection. Drain only a small,
                # fixed-length body, with a short deadline; never parse or store it.
                if self.command == "POST" and not getattr(self, "body_consumed", False) and not self.headers.get("Transfer-Encoding"):
                    try:
                        remaining = int(self.headers.get("Content-Length", "0"))
                        if 0 < remaining <= 65_536:
                            self.connection.settimeout(0.25)
                            self.rfile.read(remaining)
                    except (ValueError, OSError):
                        pass
                self.send_data(error.status,{"mode":"DEMO","code":error.code,"message":error.message})
            except (BrokenPipeError, ConnectionResetError, TimeoutError):
                self.close_connection = True
            except Exception as error:
                self.close_connection = True
                # Log location/type, never exception text, request body, headers or identifiers.
                frames = [(frame.filename, frame.lineno, frame.name) for frame in traceback.extract_tb(error.__traceback__)]
                LOGGER.error('Unhandled console failure: %s; frames=%s', type(error).__name__, frames)
                self.send_data(500,{"mode":"DEMO","code":"DEMO_ERROR","message":"로컬 데모 오류입니다. 다시 시도하세요."})

        do_POST = do_GET

        def do_OPTIONS(self):
            self.close_connection = True
            self.send_data(405,{"mode":"DEMO","code":"METHOD_NOT_ALLOWED","message":"외부 CORS 요청은 지원하지 않습니다."})

    server = LoopbackHttpServer(("127.0.0.1", port), Handler)
    server.daemon_threads = True
    return server


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--port", type=int, default=8781)
    parser.add_argument("--read-delay", type=float, default=0, help="0..2 seconds, local connection recovery testing only")
    parser.add_argument("--env-file", type=Path, help="Optional server-only LLM dotenv file; values are never logged")
    args = parser.parse_args()
    if not 0 <= args.read_delay <= 2:
        parser.error("read-delay must be between 0 and 2 seconds")
    root = Path(__file__).resolve().parents[1]
    settings = load_settings(os.environ, [args.env_file] if args.env_file else [root.parent/'.env', root/'.env'])
    os.environ.update(settings)
    try:
        server = make_server(args.port, read_delay=args.read_delay)
    except OSError as error:
        raise SystemExit("Local demo port unavailable; preserve the existing process and choose another port. " + str(error))
    print(f"LifeLink119 DEMO {VERSION} http://127.0.0.1:{server.server_port} (synthetic only)", flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()


if __name__ == "__main__":
    main()
