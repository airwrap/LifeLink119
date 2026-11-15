"""Isolated QA console + bounded native-app relay; never replaces a user's server.

The app uses localhost:8781. ADB forwards that to an ephemeral relay, which
validates the native request and forwards to a fresh production server with
its exact local Host. Production Host/Origin checks remain unchanged.
No URLs, headers, request bodies, credentials or raw responses are logged.
Only status codes and allowlisted response identifiers are retained for assertions.
"""
from contextlib import AbstractContextManager
import http.client
from http.server import BaseHTTPRequestHandler
import os
from pathlib import Path
import subprocess
import sys
import threading
from urllib.parse import urlsplit

from qa_avd_guard import QaAvdGuard

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'console'))
from server import LoopbackHttpServer, make_server
from llm_config import KEYS, load_settings


class QaConsole(AbstractContextManager):
    def __init__(self, adb, external=False):
        self.adb = list(adb)
        self.guard = QaAvdGuard(adb)
        self.guard.verify_target()
        self.external = external
        self.server = self.relay = None
        self.threads = []
        self.previous = None
        self.mapped = False
        self.previous_env = None
        self.responses = []

    def run(self, *args):
        return subprocess.check_output(self.adb + list(args), timeout=20).decode().strip()

    def __enter__(self):
        self.previous_env = {k: os.environ.get(k) for k in KEYS}
        try:
            if self.external:
                os.environ.update(load_settings(os.environ, [ROOT.parent/'.env', ROOT/'.env']))
            else:
                os.environ['LIFELINK_LLM_MODE'] = 'mock'
            self.server = make_server(0)
        except Exception:
            self.restore_env()
            raise
        target = self.server.server_port
        original_send = self.server.RequestHandlerClass.send_data
        def measured_send(handler, status, content, *args):
            if isinstance(content, dict):
                self.responses.append({'status': status, 'code': content.get('code'),
                                       'phraseId': content.get('phraseId'),
                                       'fallback': content.get('fallback')})
            return original_send(handler, status, content, *args)
        self.server.RequestHandlerClass.send_data = measured_send

        class Relay(BaseHTTPRequestHandler):
            def log_message(self, *_):
                pass

            def reject(self, code):
                self.send_response(code)
                self.send_header('Content-Length', '0')
                self.end_headers()
                self.close_connection = True

            def forward(self):
                # This is a native-client fixture, never an open/browser proxy.
                if (self.client_address[0] != '127.0.0.1'
                        or self.headers.get('Host') not in {'127.0.0.1:8781', 'localhost:8781'}
                        or self.headers.get('Origin') is not None
                        or self.headers.get('Sec-Fetch-Site') is not None
                        or self.headers.get('Transfer-Encoding') is not None):
                    return self.reject(403)
                path = urlsplit(self.path)
                if path.scheme or path.netloc or not path.path.startswith('/api/demo/'):
                    return self.reject(403)
                try:
                    size = int(self.headers.get('Content-Length', '0'))
                except ValueError:
                    return self.reject(400)
                if not 0 <= size <= 10_240:
                    return self.reject(413)
                self.connection.settimeout(8)
                data = self.rfile.read(size) if size else None
                headers = {k: v for k, v in self.headers.items()
                           if k.lower() in {'content-type', 'idempotency-key'}}
                connection = http.client.HTTPConnection('127.0.0.1', target, timeout=8)
                try:
                    connection.request(self.command, self.path, data, headers)
                    response = connection.getresponse()
                    body = response.read(1_000_001)
                    if len(body) > 1_000_000:
                        return self.reject(502)
                    self.send_response(response.status)
                    for key, value in response.getheaders():
                        if key.lower() not in {'content-length', 'connection', 'transfer-encoding'}:
                            self.send_header(key, value)
                    self.send_header('Content-Length', str(len(body)))
                    self.end_headers()
                    self.wfile.write(body)
                except (OSError, http.client.HTTPException):
                    self.close_connection = True
                finally:
                    connection.close()

            do_GET = do_POST = do_PUT = do_DELETE = forward

        try:
            self.relay = LoopbackHttpServer(('127.0.0.1', 0), Relay)
            self.relay.daemon_threads = True
            for server in (self.server, self.relay):
                thread = threading.Thread(target=server.serve_forever, daemon=True)
                thread.start()
                self.threads.append(thread)
            raw = self.run('reverse', '--list')
            self.previous = next((line.split()[-1] for line in raw.splitlines()
                                  if 'tcp:8781' in line.split()[:-1]), None)
            self.run('reverse', 'tcp:8781', 'tcp:' + str(self.relay.server_port))
            self.mapped = True
            return self
        except Exception:
            self.close()
            raise

    @property
    def base_url(self):
        return 'http://127.0.0.1:' + str(self.server.server_port)

    def restore_env(self):
        if self.previous_env is not None:
            for key, value in self.previous_env.items():
                if value is None: os.environ.pop(key, None)
                else: os.environ[key] = value
            self.previous_env = None

    def close(self):
        try:
            if self.mapped:
                self.guard.verify_target()
                self.run('reverse', '--remove', 'tcp:8781')
                if self.previous:
                    self.run('reverse', 'tcp:8781', self.previous)
                self.mapped = False
        finally:
            try:
                for index, server in enumerate((self.server, self.relay)):
                    if server is not None:
                        if index < len(self.threads):
                            server.shutdown()
                            self.threads[index].join(timeout=5)
                        server.server_close()
                self.threads.clear()
            finally:
                self.restore_env()

    def __exit__(self, *_):
        self.close()
