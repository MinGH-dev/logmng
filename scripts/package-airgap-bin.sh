#!/usr/bin/env bash
# Build backend fat JAR, frontend static assets, and JDK-only static server; copy into bin/ for air-gapped deployment.
# Run on a machine with Maven, JDK 17+, and Node/npm (online build). Target server only needs JRE 17+ and PostgreSQL.
#
# Usage (from repo root):
#   ./scripts/package-airgap-bin.sh
# Optional:
#   REACT_APP_API_BASE_URL=http://백엔드호스트:9200/api ./scripts/package-airgap-bin.sh
#   AIRGAP_BIN_ROOT=/path/to/bundle/bin ./scripts/package-airgap-bin.sh
#     — write deploy layout here instead of repo ./bin (used by build-offline-bundle.sh)

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

AIRGAP_BIN_ROOT="${AIRGAP_BIN_ROOT:-$ROOT/bin}"

BACKEND_JAR_NAME="logmng-backend-1.0.1.jar"
STATIC_SERVER_JAR_NAME="logmng-static-server-1.0.0.jar"

export REACT_APP_API_BASE_URL="${REACT_APP_API_BASE_URL:-http://127.0.0.1:9200/api}"
echo "[package-airgap-bin] REACT_APP_API_BASE_URL=$REACT_APP_API_BASE_URL"
echo "[package-airgap-bin] AIRGAP_BIN_ROOT=$AIRGAP_BIN_ROOT"

echo "[package-airgap-bin] frontend npm run build..."
(cd "$ROOT/frontend" && npm run build)

echo "[package-airgap-bin] backend mvn package..."
(cd "$ROOT/backend" && mvn -q package -DskipTests)

echo "[package-airgap-bin] static server mvn package..."
(cd "$ROOT/tools/airgap-static-server" && mvn -q package -DskipTests)

mkdir -p "$AIRGAP_BIN_ROOT/backend" "$AIRGAP_BIN_ROOT/frontend/www"
# Avoid cp error when source and dest are the same file (default AIRGAP_BIN_ROOT=$ROOT/bin).
if [ ! "$ROOT/bin/backend/run.sh" -ef "$AIRGAP_BIN_ROOT/backend/run.sh" ]; then
  cp "$ROOT/bin/backend/run.sh" "$AIRGAP_BIN_ROOT/backend/"
fi
if [ ! "$ROOT/bin/frontend/run.sh" -ef "$AIRGAP_BIN_ROOT/frontend/run.sh" ]; then
  cp "$ROOT/bin/frontend/run.sh" "$AIRGAP_BIN_ROOT/frontend/"
fi

cp "$ROOT/backend/target/$BACKEND_JAR_NAME" "$AIRGAP_BIN_ROOT/backend/"
cp "$ROOT/tools/airgap-static-server/target/$STATIC_SERVER_JAR_NAME" "$AIRGAP_BIN_ROOT/frontend/"

# Clear previous static files but keep www/.gitkeep (deploy layout tracked in Git)
mkdir -p "$AIRGAP_BIN_ROOT/frontend/www"
find "$AIRGAP_BIN_ROOT/frontend/www" -mindepth 1 -maxdepth 1 ! -name '.gitkeep' -exec rm -rf {} + 2>/dev/null || true
cp -R "$ROOT/frontend/build/." "$AIRGAP_BIN_ROOT/frontend/www/"

chmod +x "$AIRGAP_BIN_ROOT/backend/run.sh" "$AIRGAP_BIN_ROOT/frontend/run.sh" 2>/dev/null || true

echo ""
echo "[package-airgap-bin] Done."
echo "  Backend:  $AIRGAP_BIN_ROOT/backend/$BACKEND_JAR_NAME  + run.sh"
echo "  Frontend: $AIRGAP_BIN_ROOT/frontend/$STATIC_SERVER_JAR_NAME + www/ + run.sh"
echo "  See bin/README.md for 폐쇄망 실행 및 CORS·환경 변수."
