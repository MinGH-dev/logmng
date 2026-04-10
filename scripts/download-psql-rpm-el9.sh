#!/usr/bin/env bash
#
# Download PostgreSQL 16 client RPMs for RHEL / Rocky / Alma **9.6** x86_64 (PGDG yum repo).
# For offline bundle: outputs third_party/psql-rpm-el9/*.rpm (gitignored).
# Then run scripts/build-offline-bundle.sh.
#
# These RPMs are NOT usable on Debian/Ubuntu (.deb path is separate: download-psql-for-bundle.sh).
# Minor RHEL 9.x releases: 9.5/9.7 repos may work; 9.6 is the build target for compatibility with "Linux 9.6".
#
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DEST="$ROOT/third_party/psql-rpm-el9"
mkdir -p "$DEST"
rm -f "$DEST"/*.rpm 2>/dev/null || true

# PGDG: https://download.postgresql.org/pub/repos/yum/16/redhat/rhel-9.6-x86_64/
PG_YUM_BASE="https://download.postgresql.org/pub/repos/yum/16/redhat/rhel-9.6-x86_64"
# Bump when PGDG publishes newer 16.x — check directory listing.
PG16_RPM_VER="${PG16_RPM_VER:-16.13-1PGDG.rhel9.6}"

URLS=(
  "$PG_YUM_BASE/postgresql16-libs-${PG16_RPM_VER}.x86_64.rpm"
  "$PG_YUM_BASE/postgresql16-${PG16_RPM_VER}.x86_64.rpm"
)

for u in "${URLS[@]}"; do
  base="$(basename "$u")"
  out="$DEST/$base"
  echo "[download-psql-rpm-el9] $base"
  curl -fsSL -o "$out" "$u"
done

cat >"$DEST/README.txt" <<EOF
Bundled PostgreSQL 16 client for LogMng offline installer (RHEL-compatible EL9.6 x86_64).

- Target OS: RHEL / Rocky / Alma **9.6** (and typically other 9.x) — **RPM**, not .deb.
- Packages: postgresql16-libs + postgresql16 (psql and client tools; excludes postgresql16-server).
- Install on air-gapped host: ./install-offline.sh install-psql (uses dnf/yum localinstall) or db step.
- Requires: glibc/openssl from RHEL 9 base; minimal systems may need additional RPMs from your mirror.

Generated: $(date -u +%Y-%m-%dT%H:%M:%SZ)
Script: scripts/download-psql-rpm-el9.sh
EOF

echo "[download-psql-rpm-el9] Done: $DEST ($(ls -1 "$DEST"/*.rpm 2>/dev/null | wc -l | tr -d ' ') .rpm files)"
