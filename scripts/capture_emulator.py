"""Capture public demo surfaces; record secure-window refusal without bypassing it."""
import json
import re
import subprocess
from pathlib import Path

def capture_emulator(adb,path):
    path=Path(path);r=subprocess.run(adb+['exec-out','screencap','-p'],capture_output=True,timeout=30)
    if r.returncode==0 and r.stdout.startswith(b'\x89PNG\r\n\x1a\n'):
        path.write_bytes(r.stdout);return {'png':True,'path':str(path)}
    dump=subprocess.check_output(adb+['shell','dumpsys','window','windows'],timeout=20).decode(errors='replace')
    blocks=re.split(r'(?m)^  Window #',dump)
    secure=any('com.lifelink119.app' in block.split('\n',1)[0] and re.search(r'\bSECURE\b',block[:2500]) for block in blocks)
    record={'png':False,'secureWindow':bool(secure),'captureBytes':len(r.stdout),'captureExit':r.returncode,
            'meaning':'Protected medical surface was not captured; protection was not bypassed.' if secure else 'Capture failed without a proven app secure flag.'}
    path.with_suffix('.capture.json').write_text(json.dumps(record,indent=2),encoding='utf-8')
    if path.exists() and path.stat().st_size==0:path.unlink()
    if not secure:raise RuntimeError('Emulator capture failed without a proven protected window')
    return record
