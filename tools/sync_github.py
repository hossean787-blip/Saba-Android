#!/usr/bin/env python3
"""Synchronize the original 80 Saba v21 source files with GitHub, verifying SHA256."""
import base64
import hashlib
import json
import os
from pathlib import Path
import sys
import time
from urllib import request, error, parse

ROOT = Path(__file__).resolve().parents[1]
REPO = os.environ.get('SABA_GITHUB_REPO', 'hossean787-blip/Saba-Android')
TOKEN = os.environ.get('GITHUB_TOKEN', '')
MODE = '--dry-run' if '--dry-run' in sys.argv else ('--verify-only' if '--verify-only' in sys.argv else 'sync')
MANIFEST = json.loads((ROOT / 'SOURCE_SHA256_MANIFEST.json').read_text(encoding='utf-8'))
API = 'https://api.github.com/repos/' + REPO + '/contents/'

def sha(b):
    return hashlib.sha256(b).hexdigest()

def call(method, path, payload=None):
    url = API + '/'.join(parse.quote(part, safe='') for part in path.split('/'))
    body = json.dumps(payload).encode('utf-8') if payload is not None else None
    headers = {'Accept':'application/vnd.github+json', 'X-GitHub-Api-Version':'2022-11-28',
               'Authorization':'Bearer '+TOKEN, 'User-Agent':'saba-v21-sync'}
    if body is not None: headers['Content-Type'] = 'application/json'
    req = request.Request(url, data=body, headers=headers, method=method)
    for attempt in range(4):
        try:
            with request.urlopen(req, timeout=40) as r:
                return json.load(r)
        except error.HTTPError as e:
            if e.code == 404 and method == 'GET': return None
            if e.code in (429,500,502,503,504) and attempt < 3:
                time.sleep(2**attempt); continue
            raise RuntimeError(f'GitHub HTTP {e.code} for {path}: {e.read(500).decode("utf-8", "replace")}') from e
        except (error.URLError, TimeoutError):
            if attempt == 3: raise
            time.sleep(2**attempt)

def main():
    if not TOKEN and MODE != '--dry-run':
        raise SystemExit('Set GITHUB_TOKEN with Contents read/write permission. Never paste it into chat.')
    stats={'same':0,'created':0,'updated':0,'different':0,'failed':0}
    for path, digest in sorted(MANIFEST.items()):
        content = (ROOT / path).read_bytes()
        if sha(content) != digest:
            print('LOCAL HASH MISMATCH:',path,file=sys.stderr)
            stats['failed']+=1; continue
        if MODE == '--dry-run':
            print('LOCAL OK:',path);stats['same']+=1;continue
        try:
            existing=call('GET',path)
            remote=None
            if existing:
                if existing.get('type')!='file': raise ValueError('Remote path is not a file')
                if existing.get('content'):
                    remote=base64.b64decode(existing['content'])
                else:
                    raise ValueError('GitHub content omitted; cannot safely compare')
            if remote == content:
                stats['same']+=1;print('SAME:',path);continue
            if MODE == '--verify-only':
                stats['different']+=1;print('MISSING:' if existing is None else 'DIFFERENT:',path);continue
            payload={'message':'Sync original Saba v21: '+path,
                     'content':base64.b64encode(content).decode('ascii')}
            if existing: payload['sha']=existing['sha']
            call('PUT',path,payload)
            checked=call('GET',path)
            if not checked or sha(base64.b64decode(checked.get('content') or ''))!=digest:
                raise ValueError('Post-upload SHA256 verification failed')
            stats['updated' if existing else 'created']+=1
            print('UPDATED:' if existing else 'CREATED:',path)
        except Exception as exc:
            stats['failed']+=1
            print('ERROR:',path,str(exc),file=sys.stderr)
    print('RESULT:',json.dumps(stats,ensure_ascii=False))
    if stats['failed'] or stats['different']: raise SystemExit(1)

if __name__=='__main__': main()
