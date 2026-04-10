#!/usr/bin/env bash
#
# Build the standard offline bundle, then tag it as a closed-network layout
# (marker file + README). Same binaries/db copy as build-offline-bundle.sh.
#
# Usage (repo root):
#   ./scripts/build-offline-closed-network-bundle.sh
#   VERSION=my-tag ./scripts/build-offline-closed-network-bundle.sh
#   REACT_APP_API_BASE_URL=http://백엔드:9200/api ./scripts/build-offline-closed-network-bundle.sh
#   NO_TAR=1 ./scripts/build-offline-closed-network-bundle.sh
#
# Pass-through: VERSION, REACT_APP_API_BASE_URL, NO_TAR (other env inherited by child).
#
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

VERSION="${VERSION:-$(date +%Y%m%d)-closed-network}"
export VERSION

echo "[build-offline-closed-network-bundle] VERSION=${VERSION}"
"$ROOT/scripts/build-offline-bundle.sh"

OUT="$ROOT/dist/logmng-offline-${VERSION}"

if [[ ! -d "$OUT" ]]; then
  echo "Expected bundle directory missing: $OUT" >&2
  exit 1
fi

# One-line marker so install-offline.sh can apply closed-network DB defaults.
printf '%s\n' "LogMng closed-network offline bundle (minimal seed + migration skips)." >"$OUT/CLOSED-NETWORK-BUNDLE"

cp "$ROOT/scripts/offline-bundle/README-CLOSED-NETWORK.md" "$OUT/README-CLOSED-NETWORK.md"

# Refresh MANIFEST after added files
( cd "$OUT" && find . -type f | LC_ALL=C sort >MANIFEST.txt )
MANIFEST_LINES="$(wc -l <"$OUT/MANIFEST.txt" | awk '{print $1}')"
echo "[build-offline-closed-network-bundle] Appended CLOSED-NETWORK-BUNDLE + README; MANIFEST.txt (${MANIFEST_LINES} files)"
echo "[build-offline-closed-network-bundle] Directory: $OUT"

if [[ "${NO_TAR:-0}" != "1" ]]; then
  TAR_NAME="logmng-offline-${VERSION}.tar.gz"
  TAR_PATH="$ROOT/dist/${TAR_NAME}"
  if [[ -f "$TAR_PATH" ]]; then
    echo "[build-offline-closed-network-bundle] Re-packing tarball including closed-network files..."
    rm -f "$TAR_PATH"
    (cd "$ROOT/dist" && tar -czvf "$TAR_NAME" "logmng-offline-${VERSION}")
    echo "[build-offline-closed-network-bundle] Tarball: dist/${TAR_NAME}"
  fi
fi
