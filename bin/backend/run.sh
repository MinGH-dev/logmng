#!/usr/bin/env bash
# Deploy-only: backend fat JAR under bin/backend (not dev-services / backend/target workflow).
# Optional: JAVA_OPTS, SPRING_* / APP_* env — see ../README.md
set -euo pipefail
DIR="$(cd "$(dirname "$0")" && pwd)"
JAR="$DIR/logmng-backend-1.0.0.jar"
if [[ ! -f "$JAR" ]]; then
  echo "Missing: $JAR" >&2
  echo "On a build machine run: ./scripts/package-airgap-bin.sh" >&2
  exit 1
fi
exec java ${JAVA_OPTS:-} -jar "$JAR" "$@"
