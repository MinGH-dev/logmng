#!/usr/bin/env bash
#
# Build a self-contained offline deployment tree + gzip tarball for air-gapped servers.
# Includes: backend fat JAR, static UI, JDK static-server JAR, all db/*.sql + *.sh, installer, docs,
#           optional tools/psql-deb/*.deb and tools/psql-rpm-el9/*.rpm (see download-psql-* scripts).
# Run ONLY on a machine with Internet (npm, Maven) — the resulting .tar.gz needs no network.
#
# Usage (repo root):
#   ./scripts/build-offline-bundle.sh
#   REACT_APP_API_BASE_URL=http://백엔드:9200/api ./scripts/build-offline-bundle.sh
#   VERSION=1.0.1 NO_TAR=1 ./scripts/build-offline-bundle.sh   # directory only, skip tar (dev)
#
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

VERSION="${VERSION:-1.0.1}"
OUT_NAME="logmng-offline-${VERSION}"
OUT="$ROOT/dist/${OUT_NAME}"
TAR_NAME="${OUT_NAME}.tar.gz"
TAR_PATH="$ROOT/dist/${TAR_NAME}"

echo "[build-offline-bundle] Assembling $OUT ..."
rm -rf "$OUT"
mkdir -p "$OUT/db" "$OUT/docs" "$OUT/tools/psql-deb" "$OUT/tools/psql-rpm-el9"

echo "[build-offline-bundle] Packaging bin into $OUT/bin (npm + mvn)..."
AIRGAP_BIN_ROOT="$OUT/bin" "$ROOT/scripts/package-airgap-bin.sh"

# --- Database: all DDL, migrations, seeds, shell helpers ---
cp -R "$ROOT/backend/src/main/resources/db/." "$OUT/db/"

# --- Single offline installer + operator docs ---
cp "$ROOT/scripts/offline-bundle/install-offline.sh" "$OUT/"
cp "$ROOT/scripts/offline-bundle/README-OFFLINE.md" "$OUT/"
cp "$ROOT/backend/DB_SETUP_GUIDE.md" "$OUT/docs/"
cp "$ROOT/docs/contract.md" "$OUT/docs/"
cp "$ROOT/bin/README.md" "$OUT/docs/BIN-DEPLOY-README.md"

# Optional: bundled psql (.deb) for Debian/Ubuntu air-gap hosts (see download-psql-for-bundle.sh)
PSQL_SRC="$ROOT/third_party/psql-deb"
shopt -s nullglob
PSQL_DEBS=( "$PSQL_SRC"/*.deb )
shopt -u nullglob
if [[ ${#PSQL_DEBS[@]} -gt 0 ]]; then
  cp "${PSQL_DEBS[@]}" "$OUT/tools/psql-deb/"
  [[ -f "$PSQL_SRC/README.txt" ]] && cp "$PSQL_SRC/README.txt" "$OUT/tools/psql-deb/README.txt"
  echo "[build-offline-bundle] Included ${#PSQL_DEBS[@]} file(s) in tools/psql-deb/"
else
  cp "$ROOT/scripts/offline-bundle/bundle-psql-deb-README.no-debs.txt" "$OUT/tools/psql-deb/README.txt"
  echo "[build-offline-bundle] WARN: tools/psql-deb/ has no .deb — run scripts/download-psql-for-bundle.sh on a build PC with Internet to bundle psql."
fi

# Optional: bundled psql (RPM) for RHEL / Rocky / Alma 9.6 x86_64 (see download-psql-rpm-el9.sh)
PSQL_RPM_SRC="$ROOT/third_party/psql-rpm-el9"
shopt -s nullglob
PSQL_RPMS=( "$PSQL_RPM_SRC"/*.rpm )
shopt -u nullglob
if [[ ${#PSQL_RPMS[@]} -gt 0 ]]; then
  cp "${PSQL_RPMS[@]}" "$OUT/tools/psql-rpm-el9/"
  [[ -f "$PSQL_RPM_SRC/README.txt" ]] && cp "$PSQL_RPM_SRC/README.txt" "$OUT/tools/psql-rpm-el9/README.txt"
  echo "[build-offline-bundle] Included ${#PSQL_RPMS[@]} file(s) in tools/psql-rpm-el9/"
else
  cp "$ROOT/scripts/offline-bundle/bundle-psql-rpm-README.no-rpms.txt" "$OUT/tools/psql-rpm-el9/README.txt"
  echo "[build-offline-bundle] WARN: tools/psql-rpm-el9/ has no .rpm — for RHEL 9.6 app servers run scripts/download-psql-rpm-el9.sh then rebuild."
fi

chmod +x "$OUT/install-offline.sh" "$OUT/bin/backend/run.sh" "$OUT/bin/frontend/run.sh"
find "$OUT/db" -name "*.sh" -exec chmod +x {} \; 2>/dev/null || true

# --- Bundle metadata ---
{
  echo "bundle_name=${OUT_NAME}"
  echo "version=${VERSION}"
  echo "built_utc=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo "react_app_api_base_url=${REACT_APP_API_BASE_URL:-http://127.0.0.1:9200/api}"
  echo "runtime_api_base_hint=Set LOGMNG_API_BASE_URL on static-server process for /runtime-config.js (no rebuild)"
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
