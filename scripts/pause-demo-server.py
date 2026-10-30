"""Briefly pause only this project's recorded test server, preserving fake data.

Resumes on a newline, parent pipe closure, or a 30-second deadline. Used to test
real read timeouts without touching host networking or unrelated processes.
"""
import json
from pathlib import Path
import sys
import threading
import psutil

root=Path(__file__).resolve().parents[1]
record=json.loads((root/'verification/local-console-process.json').read_text(encoding='utf-8-sig'))
process=psutil.Process(int(record['processId']))
server=(root/'console/server.py').resolve()
assert any(Path(arg).resolve()==server for arg in process.cmdline() if arg.endswith('server.py')), 'Recorded PID belongs to another program'
assert process.status()!=psutil.STATUS_STOPPED, 'Server was already paused'
created=process.create_time()
resume=threading.Event()

def parent_finished():
    try:sys.stdin.buffer.readline()
    finally:resume.set()

paused=False
try:
    process.suspend();paused=True
    print(json.dumps({'paused':True,'pid':process.pid,'maxSeconds':30}),flush=True)
    threading.Thread(target=parent_finished,daemon=True).start()
    resume.wait(30)
finally:
    if paused and process.is_running() and process.create_time()==created:
        process.resume()
        print(json.dumps({'resumed':True,'pid':process.pid}),flush=True)
