#!/usr/bin/env bash
# Previously ran a Java main to append encrypted imagelog rows. The application and backend tests
# no longer ship Java utilities that INSERT into imagelog / PB FEP tables.
#
# To load or refresh ImageLog sample data, use PostgreSQL tooling only, for example:
#   export PGPASSWORD="${PGPASSWORD:-logmng123}"
#   psql -h localhost -U logmng -d logmng -v ON_ERROR_STOP=1 -f backend/src/main/resources/db/init-data-imagelog.sql
#
# That script is idempotent when the table is already populated (see comments inside the SQL file).
# For encrypted one-off rows, maintain a standalone .sql file and apply it with psql; do not add
# Java mains that mutate pb_send, pb_recv, or imagelog.
set -euo pipefail
echo "This script no longer invokes Java. See comments inside for psql / SQL-only workflows." >&2
exit 0
