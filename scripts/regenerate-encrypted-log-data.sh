#!/usr/bin/env bash
# Previously ran a Java main to UPDATE pb_send/pb_recv and reloaded imagelog via psql.
# The backend no longer ships Java utilities that mutate pb_send, pb_recv, or imagelog.
#
# Operators who need to refresh ciphertext or samples should use SQL and psql (or DBA tooling)
# against the log database, for example:
#   export PGPASSWORD="${PGPASSWORD:-logmng123}"
#   # Optional: reload imagelog samples (PostgreSQL; see file header for idempotency rules)
#   psql -h localhost -U logmng -d logmng -v ON_ERROR_STOP=1 -f backend/src/main/resources/db/init-data-imagelog.sql
#
# Do not reintroduce application or test Java code that performs INSERT/UPDATE/DELETE/TRUNCATE
# on pb_send, pb_recv, or imagelog.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
echo "Java regeneration helpers were removed. See comments in this script for SQL-only options." >&2
echo "Project root: $ROOT" >&2
exit 0
