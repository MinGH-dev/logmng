#!/usr/bin/env bash
#
# Download PostgreSQL 16 client (.deb) for the offline bundle — run on a machine WITH Internet.
# Outputs: third_party/psql-deb/*.deb (gitignored). Then run scripts/build-offline-bundle.sh.
#
# Source: PostgreSQL Apt (PGDG) bookworm — amd64. libpq5 is from PGDG (newer than bookworm's 15.x)
# so it satisfies postgresql-client-16 (needs libpq5 >= 16.x). psql binary is from postgresql-client-16.
#
# RHEL/Rocky: this script does not fetch RPMs — use your mirror and install postgresql16 or psql there.
#
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DEST="$ROOT/third_party/psql-deb"
mkdir -p "$DEST"
# Avoid mixing old PG15 .deb with PG16 set (duplicate libpq5 / client would break dpkg -i).
rm -f "$DEST"/*.deb 2>/dev/null || true

PGDG_BASE="https://download.postgresql.org/pub/repos/apt"

# Bump when PGDG publishes newer 16.x / libpq — check:
#   curl -fsSL "$PGDG_BASE/dists/bookworm-pgdg/main/binary-amd64/Packages.gz" | gzip -dc | grep -E '^(Package|Version|Filename):.*(postgresql-client-16|libpq5|postgresql-client-common)'
POSTGRES_CLIENT_16_VER="${POSTGRES_CLIENT_16_VER:-16.13-1.pgdg12+1}"
LIBPQ5_VER="${LIBPQ5_VER:-18.3-1.pgdg12+1}"
CLIENT_COMMON_VER="${CLIENT_COMMON_VER:-290.pgdg12+1}"

# libpq5 18.x satisfies postgresql-client-16 Depends: libpq5 (>= 16.13)
URLS=(
  "$PGDG_BASE/pool/main/p/postgresql-18/libpq5_${LIBPQ5_VER}_amd64.deb"
  "$PGDG_BASE/pool/main/p/postgresql-common/postgresql-client-common_${CLIENT_COMMON_VER}_all.deb"
  "$PGDG_BASE/pool/main/p/postgresql-16/postgresql-client-16_${POSTGRES_CLIENT_16_VER}_amd64.deb"
)

for u in "${URLS[@]}"; do
  base="$(basename "$u")"
  out="$DEST/$base"
  echo "[download-psql] $base"
  curl -fsSL -o "$out" "$u"
done

cat >"$DEST/README.txt" <<EOF
Bundled PostgreSQL client for LogMng offline installer (PostgreSQL 16 psql + PGDG libpq5).

- psql: postgresql-client-16 (PostgreSQL project PGDG, Debian bookworm amd64).
- libpq5: ${LIBPQ5_VER} (satisfies client dependency libpq5 >= 16.x; protocol-compatible with PG 16 servers).
- Install host: Debian/Ubuntu with dpkg. Minimal images may need libc6, libssl3, libreadline8, etc. from the same OS.
- Not for RHEL/Rocky: use your RPM mirror.

Generated: $(date -u +%Y-%m-%dT%H:%M:%SZ)
Script: scripts/download-psql-for-bundle.sh
EOF

echo "[download-psql] Done: $DEST ($(ls -1 "$DEST"/*.deb 2>/dev/null | wc -l | tr -d ' ') .deb files)"
