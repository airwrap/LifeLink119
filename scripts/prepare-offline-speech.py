"""Prepare optional offline Korean speech assets outside source control. No credentials required."""
from pathlib import Path
import argparse
import hashlib
import json
import re
import subprocess
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[1]
CACHE = ROOT.parent/'.tools/speech'
MODEL = 'vosk-model-small-ko-0.22'
SHA256 = 'eea36124087fed26c59996a4761519458e3bd185e8ea9d9865ad8760c4a1d989'


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--test-fixtures', action='store_true', help='Also synthesize public native-decoder test inputs')
    args = parser.parse_args()
    CACHE.mkdir(parents=True, exist_ok=True)
    archive = CACHE/(MODEL+'.zip')
    if not archive.exists():
        urllib.request.urlretrieve('https://alphacephei.com/vosk/models/'+MODEL+'.zip', archive)
    if hashlib.sha256(archive.read_bytes()).hexdigest() != SHA256:
        raise ValueError('Offline model checksum mismatch; file preserved for inspection')
    target = (CACHE/'assets/speech').resolve()
    target.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(archive) as source:
        for item in source.infolist():
            if not (target/item.filename).resolve().is_relative_to(target):
                raise ValueError('Unsafe archive member')
        if not (target/MODEL/'am/final.mdl').exists():
            source.extractall(target)
    core = ROOT/'app/src/main/java/com/lifelink119/app/core/emergency'
    ai = (core/'EmergencyAiCoordinator.kt').read_text(encoding='utf-8')
    voice = (core/'EmergencyVoiceConversation.kt').read_text(encoding='utf-8')
    demo = (core/'DemoEmergencySpeech.kt').read_text(encoding='utf-8')
    texts = set(re.findall(r'"[A-Z_]+"\s+to\s+"([^"\n]+)"', ai))
    texts.update(re.findall(r'GuidanceCard\(\s*"[A-Z_]+",\s*"[^"\n]+",\s*"([^"\n]+)"', ai))
    texts.update(re.findall(r'ObservationCode\.[A-Z_]+\s+to\s+"([^"\n]+)"', voice))
    section = voice[voice.index('fun confirmation'):voice.index('interface EmergencySpeechPort')]
    texts.update(re.findall(r'->\s*"([^"\n]+)"', section))
    texts.update(re.findall(r'const val (?:REPEAT|CONFIRM|REST|WORSENING_QUESTION)\s*=\s*"([^"\n]+)"', voice))
    texts.update(re.findall(r'"([^"\n]+)"', demo))
    rows = [dict(file=hashlib.sha256(text.encode()).hexdigest()[:20]+'.wav', text=text) for text in sorted(texts)]
    manifest = CACHE/'voice-prompts.json'
    manifest.write_text(json.dumps(rows, ensure_ascii=False, indent=2), encoding='utf-8')
    subprocess.run(['powershell', '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File',
                    str(ROOT/'scripts/render-voice-prompts.ps1'), '-Manifest', str(manifest),
                    '-OutputDirectory', str(CACHE/'assets/voice')], check=True)
    print(f'Offline Korean model verified; {len(rows)} fixed prompts prepared.')
    if args.test_fixtures:
        folder = CACHE/'test-assets'; folder.mkdir(parents=True, exist_ok=True)
        examples = [('네', 'CONSCIOUS_YES'), ('아니요', 'CONSCIOUS_NO'), ('의식이 없어요', 'CONSCIOUS_NO'),
                    ('숨을 안 쉬어요', 'BREATHING_NO'), ('가슴이 아파요', 'CHEST_PAIN'), ('그만', 'STOP'), ('오늘 날씨 좋네요', 'UNRECOGNIZED'),
                    ('잘 모르겠어요', 'CONSCIOUS_UNKNOWN'), ('통화 중이에요', 'CALL_OPERATOR'),
                    ('다시 전화 준대요', 'CALL_CALLBACK'), ('출동 기다리래요', 'CALL_DISPATCH_WAIT'),
                    ('가슴이 아프지 않아요', 'UNRECOGNIZED'), ('불이 난 건 아니에요', 'UNRECOGNIZED')]
        cases = [dict(text=t, code=c, file=hashlib.sha256(t.encode()).hexdigest()[:20]+'.wav') for t, c in examples]
        case_file = folder/'cases.json'
        case_file.write_text(json.dumps(cases, ensure_ascii=False), encoding='utf-8')
        subprocess.run(['powershell', '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File',
                        str(ROOT/'scripts/render-voice-prompts.ps1'), '-Manifest', str(case_file),
                        '-OutputDirectory', str(folder), '-SampleRate', '16000'], check=True)


if __name__ == '__main__':
    main()
