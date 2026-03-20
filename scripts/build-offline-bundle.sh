#!/usr/bin/env bash
#
# Build a self-contained offline deployment tree + gzip tarball for air-gapped servers.
# Includes: backend fat JAR, static UI, JDK static-server JAR, all db/*.sql + *.sh, installer, docs.
# Run ONLY on a machine with Internet (npm, Maven) — the resulting .tar.gz needs no network.
#
# Usage (repo root):
#   ./scripts/build-offline-bundle.sh
#   REACT_APP_API_BASE_URL=http://백엔드:9200/api ./scripts/build-offline-bundle.sh
#   VERSION=1.0.0 NO_TAR=1 ./scripts/build-offline-bundle.sh   # directory only, skip tar (dev)
#
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

VERSION="${VERSION:-1.0.0}"
OUT_NAME="logmng-offline-${VERSION}"
OUT="$ROOT/dist/${OUT_NAME}"
TAR_NAME="${OUT_NAME}.tar.gz"
TAR_PATH="$ROOT/dist/${TAR_NAME}"

echo "[build-offline-bundle] Packaging bin (npm + mvn)..."
"$ROOT/scripts/package-airgap-bin.sh"

echo "[build-offline-bundle] Assembling $OUT ..."
rm -rf "$OUT"
mkdir -p "$OUT/bin/backend" "$OUT/bin/frontend/www" "$OUT/db" "$OUT/docs"

# --- Application binaries (libraries embedded in JARs) ---
cp "$ROOT/bin/backend/logmng-backend-1.0.0.jar" "$OUT/bin/backend/"
cp "$ROOT/bin/backend/run.sh" "$OUT/bin/backend/"
cp "$ROOT/bin/frontend/logmng-static-server-1.0.0.jar" "$OUT/bin/frontend/"
cp "$ROOT/bin/frontend/run.sh" "$OUT/bin/frontend/"
cp -R "$ROOT/bin/frontend/www/." "$OUT/bin/frontend/www/"

# --- Database: all DDL, migrations, seeds, shell helpers ---
cp -R "$ROOT/backend/src/main/resources/db/." "$OUT/db/"

# --- Single offline installer + operator docs ---
cp "$ROOT/scripts/offline-bundle/install-offline.sh" "$OUT/"
cp "$ROOT/scripts/offline-bundle/README-OFFLINE.md" "$OUT/"
cp "$ROOT/backend/DB_SETUP_GUIDE.md" "$OUT/docs/"
cp "$ROOT/docs/contract.md" "$OUT/docs/"
cp "$ROOT/bin/README.md" "$OUT/docs/BIN-DEPLOY-README.md"

chmod +x "$OUT/install-offline.sh" "$OUT/bin/backend/run.sh" "$OUT/bin/frontend/run.sh"
find "$OUT/db" -name "*.sh" -exec chmod +x {} \; 2>/dev/null || true

# --- Bundle metadata ---
{
  echo "bundle_name=${OUT_NAME}"
  echo "version=${VERSION}"
  echo "built_utc=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo "react_app_api_base_url=${REACT_APP_API_BASE_URL:-http://127.0.0.1:9200/api}"
  printf 'git_commit='
  (cd "$ROOT" && git rev-parse --short HEAD 2>/dev/null) || echo "n/a"
} >"$OUT/BUNDLE-VERSION.txt"

( cd "$OUT" && find . -type f | LC_ALL=C sort >MANIFEST.txt )

MANIFEST_LINES="$(wc -l <"$OUT/MANIFEST.txt" | awk '{print $1}')"
echo "[build-offline-bundle] Wrote MANIFEST.txt (${MANIFEST_LINES} files)"

if [[ "${NO_TAR:-0}" != "1" ]]; then
  mkdir -p "$ROOT/dist"
  rm -f "$TAR_PATH"
  (cd "$ROOT/dist" && tar -czvf "$TAR_NAME" "$OUT_NAME")
  echo "[build-offline-bundle] Tarball: dist/${TAR_NAME} ($(du -h "$TAR_PATH" | cut -f1))"
fi

echo "[build-offline-bundle] Directory: $OUT"
echo "  Offline server: tar xzf ${TAR_NAME} && cd ${OUT_NAME} && ./install-offline.sh all"
