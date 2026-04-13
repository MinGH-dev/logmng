#!/usr/bin/env bash
# Invoked by docker compose db-init service. Mount offline bundle db/ at /db (read-only).
# Requires POSTGRES_PASSWORD (or PGPASSWORD_SUPER) and setup.sh non-interactive vars in env / env_file.
set -euo pipefail
: "${POSTGRES_PASSWORD:?Set POSTGRES_PASSWORD in .env.docker (superuser password for postgres service)}"
export PGPASSWORD="${PGPASSWORD_SUPER:-${PGPASSWORD:-$POSTGRES_PASSWORD}}"
export PGPASSWORD_SUPER="${PGPASSWORD_SUPER:-$POSTGRES_PASSWORD}"
export INSTALL_NONINTERACTIVE="${INSTALL_NONINTERACTIVE:-1}"
export SETUP_NONINTERACTIVE="${SETUP_NONINTERACTIVE:-1}"
export SETUP_MODE="${SETUP_MODE:-full}"
export DB_HOST="${DB_HOST:-postgres}"
export DB_PORT="${DB_PORT:-5432}"
export DB_SUPERUSER="${DB_SUPERUSER:-postgres}"
export DB_A_NAME="${DB_A_NAME:-logmng}"
export DB_PB_NAME="${DB_PB_NAME:-pbfep}"
export DB_B_NAME="${DB_B_NAME:-imagelog}"
# Local bundle/db-init: default on so CLOSED_NETWORK_MINIMAL imagelog samples + PB still get decrypt-practice rows (setup.sh 6b).
export LOAD_LOCAL_DECRYPT_TEST_DATA="${LOAD_LOCAL_DECRYPT_TEST_DATA:-1}"
exec bash /db/setup.sh
