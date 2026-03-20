#!/usr/bin/env bash
#
# Download PostgreSQL client (.deb) for the offline bundle — run on a machine WITH Internet.
# Outputs: third_party/psql-deb/*.deb (gitignored). Then run scripts/build-offline-bundle.sh.
#
# Target: Debian bookworm amd64 client (PostgreSQL 15). Compatible with many Debian/Ubuntu
# servers; on RHEL use your own RPM mirror — this script does not fetch RPMs.
#
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DEST="$ROOT/third_party/psql-deb"
mkdir -p "$DEST"

# Bump versions when Debian security updates change filenames (check packages.debian.org).
POSTGRES_DEB_VER="${POSTGRES_DEB_VER:-15.16-0+deb12u1}"
COMMON_VER="${COMMON_VER:-248+deb12u1}"

URLS=(
  "https://security.debian.org/debian-security/pool/updates/main/p/postgresql-15/postgresql-client-15_${POSTGRES_DEB_VER}_amd64.deb"
  "https://security.debian.org/debian-security/pool/updates/main/p/postgresql-15/libpq5_${POSTGRES_DEB_VER}_amd64.deb"
  "https://ftp.debian.org/debian/pool/main/p/postgresql-common/postgresql-client-common_${COMMON_VER}_all.deb"
)

for u in "${URLS[@]}"; do
  base="$(basename "$u")"
  out="$DEST/$base"
  echo "[download-psql] $base"
  curl -fsSL -o "$out" "$u"
done

cat >"$DEST/README.txt" <<EOF
Bundled PostgreSQL client packages for LogMng offline installer.

- OS: Debian bookworm–style amd64 .deb (PostgreSQL 15 client + libpq5 + client-common).
- Install host: Debian/Ubuntu (or derivative) with dpkg. May require extra libs from the
  same OS release on minimal images (e.g. libssl, libreadline) — use your local mirror if dpkg fails.
- Not for RHEL/Rocky: install postgresql from your RPM mirror instead.

Generated: $(date -u +%Y-%m-%dT%H:%M:%SZ)
Versions: postgresql-client-15 / libpq5 ${POSTGRES_DEB_VER}, postgresql-client-common ${COMMON_VER}
Script: scripts/download-psql-for-bundle.sh
EOF

echo "[download-psql] Done: $DEST ($(ls -1 "$DEST"/*.deb 2>/dev/null | wc -l | tr -d ' ') .deb files)"
