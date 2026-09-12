#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
mkdir -p "$ROOT/app/libs"
URL=$(python3 - <<'PY'
import json, urllib.request
req=urllib.request.Request('https://api.github.com/repos/AkashPriyadarshii/tdlib-android/releases/latest',headers={'User-Agent':'TelegramMediaLibrary'})
with urllib.request.urlopen(req) as r: data=json.load(r)
for a in data['assets']:
    if a['name']=='core-release.aar':
        print(a['browser_download_url']); break
else: raise SystemExit('core-release.aar não encontrado')
PY
)
curl -L "$URL" -o "$ROOT/app/libs/core-release.aar"
echo "TDLib AAR instalado em app/libs/core-release.aar"
