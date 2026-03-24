#!/usr/bin/env bash
# Chromium(Chrome)은 SVG를 먼저 XML로 읽습니다. UTF-8 + 올바른 XML이면 브라우저에서도 깨지지 않습니다.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
exec python3 "$ROOT/scripts/validate_svg.py" "$@"
