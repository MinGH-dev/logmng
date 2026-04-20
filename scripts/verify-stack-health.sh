#!/usr/bin/env bash
#
# Quick health: backend API, optional Docker UI, optional host CRA.
# From repo root: ./scripts/verify-stack-health.sh
#
# Exit 0 if all checked services pass; non-zero if a required check fails.
# Backend (9200) is always checked. Docker frontend container is detected by name; host curls may fail
# in some CI/sandbox environments — container-internal checks are used when Docker is available.
#
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

FAIL=0

echo "[verify-stack-health] Backend http://localhost:9200/api/health"
if curl -sf --max-time 8 http://localhost:9200/api/health | grep -q '"success":true'; then
  echo "  OK"
else
  echo "  FAIL"
  FAIL=1
fi

if command -v docker >/dev/null 2>&1 && docker ps --format '{{.Names}}' 2>/dev/null | grep -q '^logmng-local-frontend-1$'; then
  echo "[verify-stack-health] Docker UI (logmng-local-frontend-1 → in-container :3001)"
  if docker exec logmng-local-frontend-1 sh -c 'wget -q -O- --timeout=5 http://127.0.0.1:3001/ 2>/dev/null' | grep -q 'root'; then
    echo "  OK (HTML served)"
  else
    echo "  FAIL (in-container wget)"
    FAIL=1
  fi
  echo "[verify-stack-health] Host curl :3001 (Docker publish — may be unavailable in some sandboxes)"
  code=$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 http://127.0.0.1:3001/ 2>/dev/null | tr -d '\r\n' || true)
  [[ -z "$code" ]] && code="000"
  if [[ "$code" =~ ^2[0-9][0-9]$ ]]; then
    echo "  OK HTTP $code"
  else
    echo "  -- HTTP ${code:-000} (host may not forward to Docker in this environment; in-container check above is authoritative)"
  fi
else
  echo "[verify-stack-health] Docker frontend container not running — skip Docker UI checks"
fi

echo "[verify-stack-health] Host CRA :3002 (if dev-services / npm start)"
code2=$(curl -s -o /dev/null -w "%{http_code}" --max-time 3 http://127.0.0.1:3002/ 2>/dev/null | tr -d '\r\n' || true)
[[ -z "$code2" ]] && code2="000"
if [[ "$code2" =~ ^2 ]]; then
  echo "  OK HTTP $code2"
else
  echo "  -- not listening (expected when only Docker is used)"
fi

if [[ "$FAIL" -eq 0 ]]; then
  echo "[verify-stack-health] Done: required checks passed."
  exit 0
fi
echo "[verify-stack-health] Done: one or more required checks failed."
exit 1
