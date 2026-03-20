#!/usr/bin/env bash
# Deploy-only: static UI from bin/frontend/www + JDK static-server JAR (not npm start).
# Env: PORT (default 3001), JAVA_OPTS, LOGMNG_API_BASE_URL (browser API base; static JAR serves /runtime-config.js)
set -euo pipefail
DIR="$(cd "$(dirname "$0")" && pwd)"
JAR="$DIR/logmng-static-server-1.0.0.jar"
WWW="$DIR/www"
PORT="${PORT:-3001}"
if [[ ! -f "$JAR" ]]; then
  echo "Missing: $JAR" >&2
  echo "On a build machine run: ./scripts/package-airgap-bin.sh" >&2
  exit 1
fi
if [[ ! -f "$WWW/index.html" ]]; then
  echo "Missing: $WWW/index.html (static build output)" >&2
  exit 1
fi
exec java ${JAVA_OPTS:-} -jar "$JAR" "$WWW" "$PORT"
