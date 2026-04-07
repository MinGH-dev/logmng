#!/usr/bin/env bash
# Re-seed log DB payloads with current ProObject-compatible crypto:
# - pb_send / pb_recv: UPDATE all rows (request_data, response_data) via RegeneratePbFepEncryptedSeedMain
# - imagelog: DELETE all rows; backend restart triggers GenerateSampleDataScript when table is empty
#
# To keep existing imagelog rows and only add encrypted sample row(s) (idempotent), use instead:
#   ./scripts/append-imagelog-encrypted-samples.sh
#
# Env (optional): ENCRYPTION_KEY, JDBC_URL, JDBC_USER, JDBC_PASSWORD (defaults match dev application.yml)
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT/backend"
mvn -q test-compile
CP="target/test-classes:target/classes:$(mvn -q dependency:build-classpath -Dmdep.outputFile=/dev/stdout -q)"
java -cp "$CP" com.logmng.util.RegeneratePbFepEncryptedSeedMain

export PGPASSWORD="${PGPASSWORD:-logmng123}"
psql -h localhost -U logmng -d logmng -v ON_ERROR_STOP=1 -c "DELETE FROM imagelog;"

cd "$ROOT"
./scripts/dev-services.sh backend restart
echo "Done. Wait ~10s then: curl -s http://localhost:9200/api/health"
echo "imagelog count: psql -U logmng -d logmng -tAc \"SELECT COUNT(*) FROM imagelog;\""
