#!/usr/bin/env bash
# Idempotently append encrypted imagelog row(s) for java-fw-imagelog UI decrypt tests.
# Does NOT delete existing rows. Re-run safe: skips rows that already exist (guid + status).
#
# Env (optional): ENCRYPTION_KEY, APP_SECURITY_ENCRYPTION_KEY;
# ImageLog JDBC: APP_DATASOURCE_IMAGELOG_URL, APP_DATASOURCE_IMAGELOG_USERNAME, APP_DATASOURCE_IMAGELOG_PASSWORD
# (fallback: JDBC_URL, JDBC_USER, JDBC_PASSWORD — defaults match dev application.yml)
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT/backend"
mvn -q test-compile
CP="target/test-classes:target/classes:$(mvn -q dependency:build-classpath -Dmdep.outputFile=/dev/stdout -q)"
java -cp "$CP" com.logmng.util.AppendEncryptedImagelogSamplesMain
