#!/bin/sh
set -eu
cd "$(dirname "$0")"
python3 tools/sync_github.py
python3 tools/sync_github.py --verify-only
