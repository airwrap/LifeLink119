"""Load only server LLM settings. Never evaluate shell syntax or print secret values."""
from pathlib import Path
import re

KEYS = frozenset({'LIFELINK_LLM_MODE', 'LIFELINK_LLM_PROTOCOL', 'LIFELINK_LLM_URL',
                  'LIFELINK_LLM_API_KEY', 'LIFELINK_LLM_MODEL'})


def load_settings(environ, paths):
    values = {}
    for path in paths:
        path = Path(path)
        if not path.is_file():
            continue
        if path.stat().st_size > 65536:
            raise ValueError('LLM settings file is too large')
        for number, raw in enumerate(path.read_text(encoding='utf-8-sig').splitlines(), 1):
            line = raw.strip()
            if not line or line.startswith('#'):
                continue
            line = line.removeprefix('export ')
            key, separator, value = line.partition('=')
            key = key.strip()
            if key not in KEYS:
                continue
            if not separator:
                raise ValueError(f'Invalid LLM setting at line {number}')
            value = value.strip()
            if value.startswith(('"', "'")):
                quote = value[0]
                end = value.find(quote, 1)
                if end < 0 or value[end+1:].strip() and not value[end+1:].strip().startswith('#'):
                    raise ValueError(f'Invalid quoted LLM setting at line {number}')
                value = value[1:end]
            else:
                value = re.split(r'\s+#', value, maxsplit=1)[0].rstrip()
            if any(ord(c) < 32 for c in value):
                raise ValueError(f'Invalid LLM setting at line {number}')
            values[key] = value  # dotenv convention: last occurrence wins.
    values.update({k: v for k, v in environ.items() if k in KEYS})
    return values


def endpoint_path(path, protocol):
    path = path.rstrip('/')
    if protocol == 'chat-completions' and not path.endswith('/chat/completions'):
        path += '/chat/completions'
    return path or '/'
