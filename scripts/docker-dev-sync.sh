#!/usr/bin/env bash
#
# After frontend/backend source changes: rebuild offline dist from current sources,
# then refresh Docker backend + frontend images without re-running db-init (Postgres stays up).
#
# This is the **only** supported path to get new Java/JS into **running** backend/frontend containers:
# images copy from dist/logmng-offline-<VERSION>/ — not from live source mounts.
# Equivalent: ./scripts/docker-local-manual-test.sh sync
#
# Usage (repo root):
#   ./scripts/docker-dev-sync.sh
#   VERSION=1.0.2 NO_TAR=0 ./scripts/docker-dev-sync.sh   # include gzip tarball (slower)
#
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

export VERSION="${VERSION:-1.0.2}"
export NO_TAR="${NO_TAR:-1}"

echo "[docker-dev-sync] Rebuilding offline bundle (VERSION=${VERSION}, NO_TAR=${NO_TAR})..."
VERSION="$VERSION" NO_TAR="$NO_TAR" ./scripts/build-offline-bundle.sh

# macOS / exFAT: AppleDouble `._*` files under dist/ break Docker build context (xattr "operation not permitted").
if [[ -d dist ]]; then
  n=$(find dist -name '._*' 2>/dev/null | wc -l | tr -d ' ')
  if [[ "${n:-0}" != "0" ]]; then
    find dist -name '._*' -delete 2>/dev/null || true
    echo "[docker-dev-sync] Removed ${n} AppleDouble (._*) file(s) under dist/ before Docker build."
  fi
fi

echo "[docker-dev-sync] Recreating backend + frontend from dist (SKIP_BUNDLE_BUILD=1, SKIP_DB_INIT=1)..."
SKIP_BUNDLE_BUILD=1 SKIP_DB_INIT=1 ./scripts/docker-local-manual-test.sh up

echo ""
echo "[docker-dev-sync] Success. Local Docker stack matches current sources."
echo "  - UI:    http://localhost:3001/"
echo "  - API:   http://localhost:9200/api/health"
echo "  - DB체크: http://localhost:9200/api/db/test"
echo ""
