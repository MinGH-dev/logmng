#!/bin/bash

# PostgreSQL 16 데이터베이스 설정 스크립트
#
# 환경에 따라 postgres 역할이 없을 수 있음 (예: Homebrew PostgreSQL은 OS 사용자 사용).
# 그 경우: DB_SUPERUSER=$USER ./setup.sh
#
# --- Multi-database / multi-schema (요구: 20260320-multi-datasource-schema-configuration) ---
# DB A (Primary / logmng sys): SCHEMA_SYS. PB FEP: SCHEMA_PB on DB A or separate PB DB. DB B: ImageLog (SCHEMA_IMAGELOG).
# 새 변수를 설정하지 않으면 기존과 동일: 단일 DB(logmng)·스키마 public.
#
# --- Contract: docs/contract.md (DB 설치·부트스트랩) ---
# 슈퍼유저 OS 역할: DB_SUPERUSER (기본 postgres). 클라이언트 비밀번호는 PGPASSWORD 또는
# PGPASSWORD_SUPER(설정 시 내부에서 PGPASSWORD로 전달) — 값은 절대 stdout/stderr에 출력하지 않음.
# Primary cluster (A): DB_HOST, DB_PORT, DB_NAME(레거시), DB_A_NAME, DB_USER, DB_PASSWORD,
# DB_ETL_USER, DB_ETL_PASSWORD, SCHEMA_*.
# ImageLog cluster (B): DB_B_NAME, SCHEMA_IMAGELOG; optional split endpoint DB_B_HOST, DB_B_PORT, DB_B_SUPERUSER
# (defaults: DB_HOST, DB_PORT, DB_SUPERUSER — unset = same-cluster A+B as before).
# Split-PB 클러스터: DB_PB_NAME, DB_PB_HOST, DB_PB_PORT, DB_PB_SUPERUSER(기본 DB_SUPERUSER와 동일).
#
# --- PB FEP 별도 PostgreSQL database (split-PB) ---
# DB_PB_NAME이 비어 있거나 DB_A_NAME과 같으면 레거시: PB DDL·마이그레이션이 A에 적용됨.
# DB_PB_NAME이 설정되고 DB_A_NAME과 다르면 split-PB: PB DDL(schema_pb_fep, PB 전용 마이그레이션)은
# DB_PB_NAME DB에만 적용(psql_pb_admin = DB_PB_HOST/DB_PB_PORT/DB_PB_SUPERUSER, 기본은 primary와 동일).
# Spring 런타임 URL은 contract의 APP_DATASOURCE_PB_* / SPRING_DATASOURCE_* 참고(본 스크립트는 JDBC 전체 URL을 로그에 찍지 않음).
#
# SETUP_MODE (default full); allowed: full | sys_only | pb_only | primary_only | imagelog_only
#   full(default)   Primary A + ImageLog B + (split PB DB when configured). B DDL uses psql_b_admin (defaults = Primary).
#   sys_only        Same as before: lighter A path; split PB DDL skipped on this run — use pb_only for PB cluster.
#   pb_only         PB database only (unchanged). No Primary A / ImageLog B steps.
#   primary_only    Primary A path only: system DDL/migrations/grants on A per full-like contract for A.
#                   No ImageLog B DDL/seeds/migrations. When SPLIT_PB=1 (PB DB distinct from A), no PB DDL —
#                   use SETUP_MODE=pb_only on the PB host. When SPLIT_PB=0, PB DDL on A runs like full (PB on A).
#   imagelog_only   ImageLog B cluster only: DB_B_NAME, SCHEMA_IMAGELOG, imagelog DDL/migrations/seeds per flags.
#                   No Primary A DDL, no PB steps.
#
# --- Non-interactive (.env-driven install, req 20260410) ---
# install 래퍼는 set -a && source .env && set +a 후 본 스크립트를 호출할 수 있다.
# INSTALL_NONINTERACTIVE=1 또는 SETUP_NONINTERACTIVE=1 이면(대소문자 true/yes/y 허용) 스크립트 기본값으로
# 비밀번호·호스트를 채우지 않도록 **기본값 적용 전에** 필수 변수가 설정·비어 있지 않은지 검사한다.
#   full / sys_only / primary_only: DB_HOST, DB_PORT, DB_USER, DB_PASSWORD, DB_ETL_USER, DB_ETL_PASSWORD
#   pb_only: DB_PB_NAME, DB_USER, DB_PASSWORD 및 (DB_PB_HOST·DB_PB_PORT 가 모두 설정되어 있거나
#            대체로 DB_HOST·DB_PORT 가 모두 설정)
#   imagelog_only: DB_B_NAME, DB_USER, DB_PASSWORD 및 (DB_B_HOST·DB_B_PORT 가 모두 설정되어 있거나
#                  대체로 DB_HOST·DB_PORT 가 모두 설정)
# 누락/빈 값 오류 메시지는 변수 **이름만** 나열한다. trust 인증이면 PGPASSWORD/PGPASSWORD_SUPER 생략 가능.
#
# --- Security (stdout/stderr) ---
# 비밀번호, PGPASSWORD, PGPASSWORD_SUPER, 자격 증명이 포함된 JDBC URL, .env 원문을 출력하지 않는다.
# 기본 경로에서 set -x를 켜지 않는다. 디버그 시 SETUP_BASH_XTRACE=1 이면 set -x 활성(로그에 비밀 유출 가능 — 운영 금지).
#
#   SYS_ONLY_LOAD_INIT_DATA  1이면 sys_only에서도 INIT_DATA_FILE 실행
#   SKIP_INIT_DATA       1이면 INIT_DATA_FILE 실행 생략(full·sys_only 공통; DDL·마이그레이션은 유지)
#   INIT_DATA_FILE     기본: init-data.sql
#   CLOSED_NETWORK_MINIMAL  1이면 PB pagination/bmsg 샘플·imagelog 샘플 등 생략(DDL 마이그레이션은 유지)
#   LOAD_LOCAL_DECRYPT_TEST_DATA  1이면 로컬 복호화 연습용 소량 시드(init-data-local-decrypt-test-*.sql, dev/local only; DDL·마이그레이션 이후 실행, CLOSED_NETWORK_MINIMAL과 무관)
#
# 예: A에 logmng_sys + PB는 별도 DB logmng_pb (동일 클러스터)
#   DB_A_NAME=logmng DB_PB_NAME=logmng_pb SCHEMA_SYS=logmng_sys SCHEMA_PB=public ./setup.sh
#
# setup.sh 4h (permission_group_screen): 신규 설치는 schema_sys.sql에 컬럼 포함; 레거시는 4h 필요.
#
set -e

if [ "${SETUP_BASH_XTRACE:-0}" = "1" ]; then
  set -x
fi

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

# --- Non-interactive: validate before applying script defaults (TC-01 / TC-05) ---
noninteractive_enabled() {
  case "${INSTALL_NONINTERACTIVE:-}" in 1|true|TRUE|yes|YES|y|Y) return 0 ;; esac
  case "${SETUP_NONINTERACTIVE:-}" in 1|true|TRUE|yes|YES|y|Y) return 0 ;; esac
  return 1
}

_validate_noninteractive_env() {
  local mode="${SETUP_MODE:-full}"
  local bad=""
  local n

  case "$mode" in
    pb_only)
      n="DB_PB_NAME"
      if ! eval "[ -n \"\${${n}+x}\" ]" || ! eval "[ -n \"\$$n\" ]"; then bad="${bad}${bad:+ }DB_PB_NAME"; fi
      n="DB_USER"
      if ! eval "[ -n \"\${${n}+x}\" ]" || ! eval "[ -n \"\$$n\" ]"; then bad="${bad}${bad:+ }DB_USER"; fi
      n="DB_PASSWORD"
      if ! eval "[ -n \"\${${n}+x}\" ]" || ! eval "[ -n \"\$$n\" ]"; then bad="${bad}${bad:+ }DB_PASSWORD"; fi
      if eval "[ -n \"\${DB_PB_HOST+x}\" ]" && eval "[ -n \"\${DB_PB_PORT+x}\" ]"; then
        :
      else
        n="DB_HOST"
        if ! eval "[ -n \"\${${n}+x}\" ]"; then bad="${bad}${bad:+ }DB_HOST"; fi
        n="DB_PORT"
        if ! eval "[ -n \"\${${n}+x}\" ]"; then bad="${bad}${bad:+ }DB_PORT"; fi
      fi
      ;;
    imagelog_only)
      n="DB_B_NAME"
      if ! eval "[ -n \"\${${n}+x}\" ]" || ! eval "[ -n \"\$$n\" ]"; then bad="${bad}${bad:+ }DB_B_NAME"; fi
      n="DB_USER"
      if ! eval "[ -n \"\${${n}+x}\" ]" || ! eval "[ -n \"\$$n\" ]"; then bad="${bad}${bad:+ }DB_USER"; fi
      n="DB_PASSWORD"
      if ! eval "[ -n \"\${${n}+x}\" ]" || ! eval "[ -n \"\$$n\" ]"; then bad="${bad}${bad:+ }DB_PASSWORD"; fi
      if eval "[ -n \"\${DB_B_HOST+x}\" ]" && eval "[ -n \"\${DB_B_PORT+x}\" ]"; then
        :
      else
        n="DB_HOST"
        if ! eval "[ -n \"\${${n}+x}\" ]"; then bad="${bad}${bad:+ }DB_HOST"; fi
        n="DB_PORT"
        if ! eval "[ -n \"\${${n}+x}\" ]"; then bad="${bad}${bad:+ }DB_PORT"; fi
      fi
      ;;
    full|sys_only|primary_only)
      n="DB_HOST"
      if ! eval "[ -n \"\${${n}+x}\" ]"; then bad="${bad}${bad:+ }DB_HOST"; fi
      n="DB_PORT"
      if ! eval "[ -n \"\${${n}+x}\" ]"; then bad="${bad}${bad:+ }DB_PORT"; fi
      n="DB_USER"
      if ! eval "[ -n \"\${${n}+x}\" ]" || ! eval "[ -n \"\$$n\" ]"; then bad="${bad}${bad:+ }DB_USER"; fi
      n="DB_PASSWORD"
      if ! eval "[ -n \"\${${n}+x}\" ]" || ! eval "[ -n \"\$$n\" ]"; then bad="${bad}${bad:+ }DB_PASSWORD"; fi
      n="DB_ETL_USER"
      if ! eval "[ -n \"\${${n}+x}\" ]" || ! eval "[ -n \"\$$n\" ]"; then bad="${bad}${bad:+ }DB_ETL_USER"; fi
      n="DB_ETL_PASSWORD"
      if ! eval "[ -n \"\${${n}+x}\" ]" || ! eval "[ -n \"\$$n\" ]"; then bad="${bad}${bad:+ }DB_ETL_PASSWORD"; fi
      ;;
    *)
      echo "Error: invalid SETUP_MODE (expected full, sys_only, pb_only, primary_only, or imagelog_only)." >&2
      exit 2
      ;;
  esac

  if [ -n "$bad" ]; then
    echo "Error: non-interactive install: missing or empty required variables: $bad" >&2
    exit 2
  fi
}

if noninteractive_enabled; then
  _validate_noninteractive_env
fi

DB_SUPERUSER="${DB_SUPERUSER:-postgres}"
DB_NAME="${DB_NAME:-logmng}"
DB_A_NAME="${DB_A_NAME:-$DB_NAME}"
DB_B_NAME="${DB_B_NAME:-$DB_A_NAME}"

SCHEMA_SYS="${SCHEMA_SYS:-public}"
SCHEMA_PB="${SCHEMA_PB:-public}"
SCHEMA_IMAGELOG="${SCHEMA_IMAGELOG:-public}"

DB_USER="${DB_USER:-logmng}"
DB_PASSWORD="${DB_PASSWORD:-logmng123}"
DB_ETL_USER="${DB_ETL_USER:-logmng_etl}"
DB_ETL_PASSWORD="${DB_ETL_PASSWORD:-logmng_etl123}"
DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"

DB_PB_NAME="${DB_PB_NAME:-}"
DB_PB_HOST="${DB_PB_HOST:-$DB_HOST}"
DB_PB_PORT="${DB_PB_PORT:-$DB_PORT}"
DB_PB_SUPERUSER="${DB_PB_SUPERUSER:-$DB_SUPERUSER}"

# ImageLog cluster (B): defaults fold to Primary — TC-03 backward compatibility when unset
DB_B_HOST="${DB_B_HOST:-$DB_HOST}"
DB_B_PORT="${DB_B_PORT:-$DB_PORT}"
DB_B_SUPERUSER="${DB_B_SUPERUSER:-$DB_SUPERUSER}"

SETUP_MODE="${SETUP_MODE:-full}"
INIT_DATA_FILE="${INIT_DATA_FILE:-init-data.sql}"
CLOSED_NETWORK_MINIMAL="${CLOSED_NETWORK_MINIMAL:-0}"
SKIP_INIT_DATA="${SKIP_INIT_DATA:-0}"
LOAD_LOCAL_DECRYPT_TEST_DATA="${LOAD_LOCAL_DECRYPT_TEST_DATA:-0}"

case "$SETUP_MODE" in full|sys_only|pb_only|primary_only|imagelog_only) ;; *)
  echo "Error: invalid SETUP_MODE (expected full, sys_only, pb_only, primary_only, or imagelog_only)." >&2
  exit 2
  ;;
esac

SPLIT_PB=0
if [ -n "$DB_PB_NAME" ] && [ "$DB_PB_NAME" != "$DB_A_NAME" ]; then
  SPLIT_PB=1
fi

PB_CLUSTER_DIFFERS=0
if [ "$DB_PB_HOST" != "$DB_HOST" ] || [ "$DB_PB_PORT" != "$DB_PORT" ]; then
  PB_CLUSTER_DIFFERS=1
fi

B_CLUSTER_DIFFERS=0
if [ "$DB_B_HOST" != "$DB_HOST" ] || [ "$DB_B_PORT" != "$DB_PORT" ]; then
  B_CLUSTER_DIFFERS=1
fi

# Primary-only: no ImageLog B steps; when split-PB, no PB DDL (use pb_only on PB host)
PRIMARY_ONLY=0
if [ "$SETUP_MODE" = "primary_only" ]; then
  PRIMARY_ONLY=1
fi

# Run ImageLog-on-B DDL/seeds in main path (not primary_only; imagelog_only handled separately)
RUN_B_IMAGELOG=1
if [ "$PRIMARY_ONLY" = "1" ]; then
  RUN_B_IMAGELOG=0
fi

if [ "$SPLIT_PB" = "1" ]; then
  SP_A_DDL="${SCHEMA_SYS}, public"
  SP_APP="${SCHEMA_SYS}, public"
else
  SP_A_DDL="${SCHEMA_SYS}, ${SCHEMA_PB}, public"
  SP_APP="${SCHEMA_SYS}, ${SCHEMA_PB}, public"
fi

# 슈퍼유저 클라이언트 비밀번호(로컬 trust면 불필요). PGPASSWORD_SUPER 또는 PGPASSWORD로만 전달.
export PGPASSWORD="${PGPASSWORD_SUPER:-${PGPASSWORD:-}}"

psql_admin() {
  psql -U "$DB_SUPERUSER" -h "$DB_HOST" -p "$DB_PORT" "$@"
}

psql_pb_admin() {
  psql -U "$DB_PB_SUPERUSER" -h "$DB_PB_HOST" -p "$DB_PB_PORT" "$@"
}

psql_b_admin() {
  psql -U "$DB_B_SUPERUSER" -h "$DB_B_HOST" -p "$DB_B_PORT" "$@"
}

ensure_schema() {
  local db="$1"
  local sch="$2"
  if [ "$sch" = "public" ]; then
    return 0
  fi
  psql_admin -d "$db" -v ON_ERROR_STOP=1 -c "CREATE SCHEMA IF NOT EXISTS ${sch};"
}

ensure_schema_pb() {
  local db="$1"
  local sch="$2"
  if [ "$sch" = "public" ]; then
    return 0
  fi
  psql_pb_admin -d "$db" -v ON_ERROR_STOP=1 -c "CREATE SCHEMA IF NOT EXISTS ${sch};"
}

ensure_schema_b() {
  local db="$1"
  local sch="$2"
  if [ "$sch" = "public" ]; then
    return 0
  fi
  psql_b_admin -d "$db" -v ON_ERROR_STOP=1 -c "CREATE SCHEMA IF NOT EXISTS ${sch};"
}

grant_schema_objects() {
  local db="$1"
  local sch="$2"
  local user="$3"
  psql_admin -d "$db" -v ON_ERROR_STOP=1 -c "GRANT USAGE ON SCHEMA ${sch} TO ${user};"
  psql_admin -d "$db" -v ON_ERROR_STOP=1 -c "GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA ${sch} TO ${user};"
  psql_admin -d "$db" -v ON_ERROR_STOP=1 -c "GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA ${sch} TO ${user};"
  psql_admin -d "$db" -v ON_ERROR_STOP=1 -c "ALTER DEFAULT PRIVILEGES IN SCHEMA ${sch} GRANT ALL ON TABLES TO ${user};"
  psql_admin -d "$db" -v ON_ERROR_STOP=1 -c "ALTER DEFAULT PRIVILEGES IN SCHEMA ${sch} GRANT ALL ON SEQUENCES TO ${user};"
}

grant_schema_objects_pb() {
  local db="$1"
  local sch="$2"
  local user="$3"
  psql_pb_admin -d "$db" -v ON_ERROR_STOP=1 -c "GRANT USAGE ON SCHEMA ${sch} TO ${user};"
  psql_pb_admin -d "$db" -v ON_ERROR_STOP=1 -c "GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA ${sch} TO ${user};"
  psql_pb_admin -d "$db" -v ON_ERROR_STOP=1 -c "GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA ${sch} TO ${user};"
  psql_pb_admin -d "$db" -v ON_ERROR_STOP=1 -c "ALTER DEFAULT PRIVILEGES IN SCHEMA ${sch} GRANT ALL ON TABLES TO ${user};"
  psql_pb_admin -d "$db" -v ON_ERROR_STOP=1 -c "ALTER DEFAULT PRIVILEGES IN SCHEMA ${sch} GRANT ALL ON SEQUENCES TO ${user};"
}

grant_schema_objects_b() {
  local db="$1"
  local sch="$2"
  local user="$3"
  psql_b_admin -d "$db" -v ON_ERROR_STOP=1 -c "GRANT USAGE ON SCHEMA ${sch} TO ${user};"
  psql_b_admin -d "$db" -v ON_ERROR_STOP=1 -c "GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA ${sch} TO ${user};"
  psql_b_admin -d "$db" -v ON_ERROR_STOP=1 -c "GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA ${sch} TO ${user};"
  psql_b_admin -d "$db" -v ON_ERROR_STOP=1 -c "ALTER DEFAULT PRIVILEGES IN SCHEMA ${sch} GRANT ALL ON TABLES TO ${user};"
  psql_b_admin -d "$db" -v ON_ERROR_STOP=1 -c "ALTER DEFAULT PRIVILEGES IN SCHEMA ${sch} GRANT ALL ON SEQUENCES TO ${user};"
}

run_sql_file_sp() {
  local db="$1"
  local search_path_csv="$2"
  local file="$3"
  psql_admin -d "$db" -v ON_ERROR_STOP=1 \
    -c "SET search_path TO ${search_path_csv};" \
    -f "$file"
}

run_sql_file_sp_pb() {
  local db="$1"
  local search_path_csv="$2"
  local file="$3"
  psql_pb_admin -d "$db" -v ON_ERROR_STOP=1 \
    -c "SET search_path TO ${search_path_csv};" \
    -f "$file"
}

run_sql_file_sp_b() {
  local db="$1"
  local search_path_csv="$2"
  local file="$3"
  psql_b_admin -d "$db" -v ON_ERROR_STOP=1 \
    -c "SET search_path TO ${search_path_csv};" \
    -f "$file"
}

grant_connect_pb_db() {
  psql_pb_admin -d postgres -v ON_ERROR_STOP=1 -c "GRANT CONNECT ON DATABASE ${DB_PB_NAME} TO ${DB_USER};"
}

apply_split_pb_migrations_and_grants() {
  local sp_pb="${SCHEMA_PB}, public"
  if [ "${CLOSED_NETWORK_MINIMAL}" = "1" ]; then
    echo "5-pb-fep. PB FEP pagination/bmsg 샘플 ⏭️  생략 (CLOSED_NETWORK_MINIMAL=1, DB=${DB_PB_NAME})"
  else
    echo "5-pb-fep. PB FEP pagination / bmsg 샘플 (split, DB=${DB_PB_NAME})..."
    run_sql_file_sp_pb "$DB_PB_NAME" "$sp_pb" "$SCRIPT_DIR/migrate-pb-fep-pagination-bmsg-sample-20260330.sql"
    echo "   ✅ PB FEP pagination/bmsg 샘플(PB DB)"
  fi
  # Order: (1) ordinary -> partitioned + daily window (no DEFAULT) — migrate-pb-send-recv-partitioning-20260408.sql
  #        (2) legacy monthly *_YYYYMM -> daily *_YYYYMMDD — migrate-pb-send-recv-monthly-to-daily-20260414.sql
  #        (3) rebuild to RANGE(log_time) and drop log_timestamp — migrate-pb-send-recv-remove-log-timestamp-20260414.sql
  #        (4) log_time VARCHAR(20) + lexical backfill — migrate-pb-fep-log-time-varchar20-20260415.sql
  echo "5-pb-fep-partition. PB FEP(pb_send/pb_recv) 파티셔닝(split, DB=${DB_PB_NAME})..."
  run_sql_file_sp_pb "$DB_PB_NAME" "$sp_pb" "$SCRIPT_DIR/migrate-pb-send-recv-partitioning-20260408.sql"
  echo "5-pb-fep-partition-daily-upgrade. PB FEP 월파티션→일파티션(20260414, 멱등)..."
  run_sql_file_sp_pb "$DB_PB_NAME" "$sp_pb" "$SCRIPT_DIR/migrate-pb-send-recv-monthly-to-daily-20260414.sql"
  echo "5-pb-fep-drop-log-timestamp. PB FEP log_timestamp 물리 제거(20260414, 멱등)..."
  run_sql_file_sp_pb "$DB_PB_NAME" "$sp_pb" "$SCRIPT_DIR/migrate-pb-send-recv-remove-log-timestamp-20260414.sql"
  echo "5-pb-fep-log-time-varchar20. PB FEP log_time VARCHAR(20) + 백필(20260415, 멱등)..."
  run_sql_file_sp_pb "$DB_PB_NAME" "$sp_pb" "$SCRIPT_DIR/migrate-pb-fep-log-time-varchar20-20260415.sql"
  echo "   ✅ PB FEP 파티셔닝 + 일단위 정렬 마이그레이션(PB DB)"
  echo "5-pb-split-grant. PB 스키마 GRANT (${SCHEMA_PB} → ${DB_USER})..."
  grant_schema_objects_pb "$DB_PB_NAME" "$SCHEMA_PB" "$DB_USER"
  psql_pb_admin -d "$DB_PB_NAME" -c "GRANT ALL PRIVILEGES ON SCHEMA public TO $DB_USER;" 2>/dev/null || true
}

# --- PB-only provisioning (no A/B imagelog) ---
if [ "$SETUP_MODE" = "pb_only" ]; then
  if [ -z "$DB_PB_NAME" ]; then
    echo "Error: SETUP_MODE=pb_only requires DB_PB_NAME." >&2
    exit 1
  fi
  echo "=== PostgreSQL PB-only (DB_PB_NAME=${DB_PB_NAME}, host=${DB_PB_HOST}:${DB_PB_PORT}, SCHEMA_PB=${SCHEMA_PB}) ==="
  echo ""
  if ! pg_isready -h "$DB_PB_HOST" -p "$DB_PB_PORT" >/dev/null 2>&1; then
    echo "경고: pg_isready 실패 — PB 클러스터 ${DB_PB_HOST}:${DB_PB_PORT} 가 응답하지 않습니다. 계속 시도합니다."
  fi
  echo "1-pb. PB database 생성..."
  psql_pb_admin -tc "SELECT 1 FROM pg_database WHERE datname = '${DB_PB_NAME}'" | grep -q 1 || \
    psql_pb_admin -c "CREATE DATABASE ${DB_PB_NAME};"
  echo "   ✅ database '${DB_PB_NAME}' 확인됨"

  echo "2-pb. PB 클러스터에 앱 사용자 확인(없으면 CREATE)..."
  psql_pb_admin -d postgres -tc "SELECT 1 FROM pg_roles WHERE rolname = '${DB_USER}'" | grep -q 1 || \
    psql_pb_admin -d postgres -c "CREATE USER ${DB_USER} WITH PASSWORD '${DB_PASSWORD}';"
  echo "   ✅ 사용자 '${DB_USER}' 확인됨(PB 클러스터)"
  if [ "$PB_CLUSTER_DIFFERS" != "1" ]; then
    echo "      (PB 클러스터 = primary — 이미 역할이 있으면 위 CREATE는 건너뜀)"
  fi

  echo "3-pb. CONNECT 및 SCHEMA_PB 준비..."
  grant_connect_pb_db
  ensure_schema_pb "$DB_PB_NAME" "$SCHEMA_PB"

  echo "4-pb. schema_pb_fep.sql (PB DB)..."
  run_sql_file_sp_pb "$DB_PB_NAME" "${SCHEMA_PB}, public" "$SCRIPT_DIR/schema_pb_fep.sql"
  echo "   ✅ schema_pb_fep 적용"

  apply_split_pb_migrations_and_grants

  echo ""
  echo "=== PB-only 설정 완료 ==="
  echo "데이터베이스(PB): $DB_PB_NAME (SCHEMA_PB=$SCHEMA_PB)"
  echo "앱 역할(이름만): $DB_USER"
  echo "PB 엔드포인트: ${DB_PB_HOST}:${DB_PB_PORT} (런타임 URL은 contract APP_DATASOURCE_PB_* 참고)"
  exit 0
fi

# --- ImageLog-only provisioning (B cluster; no Primary A / no PB) ---
if [ "$SETUP_MODE" = "imagelog_only" ]; then
  echo "=== PostgreSQL imagelog_only (DB_B_NAME=${DB_B_NAME}, host=${DB_B_HOST}:${DB_B_PORT}, SCHEMA_IMAGELOG=${SCHEMA_IMAGELOG}) ==="
  echo ""
  if ! pg_isready -h "$DB_B_HOST" -p "$DB_B_PORT" >/dev/null 2>&1; then
    echo "경고: pg_isready 실패 — ImageLog 클러스터 ${DB_B_HOST}:${DB_B_PORT} 가 응답하지 않습니다. 계속 시도합니다."
  fi

  echo "1-b. ImageLog database 생성..."
  psql_b_admin -tc "SELECT 1 FROM pg_database WHERE datname = '${DB_B_NAME}'" | grep -q 1 || \
    psql_b_admin -c "CREATE DATABASE ${DB_B_NAME};"
  echo "   ✅ database '${DB_B_NAME}' 확인됨"

  echo "2-b. ImageLog 클러스터에 앱 사용자 확인(없으면 CREATE)..."
  psql_b_admin -d postgres -tc "SELECT 1 FROM pg_roles WHERE rolname = '${DB_USER}'" | grep -q 1 || \
    psql_b_admin -d postgres -c "CREATE USER ${DB_USER} WITH PASSWORD '${DB_PASSWORD}';"
  echo "   ✅ 사용자 '${DB_USER}' 확인됨(ImageLog 클러스터)"
  if [ "$B_CLUSTER_DIFFERS" != "1" ]; then
    echo "      (ImageLog 클러스터 = primary — 이미 역할이 있으면 위 CREATE는 건너뜀)"
  fi

  echo "3-b. CONNECT 및 SCHEMA_IMAGELOG 준비..."
  psql_b_admin -d postgres -v ON_ERROR_STOP=1 -c "GRANT CONNECT ON DATABASE ${DB_B_NAME} TO ${DB_USER};"
  psql_b_admin -d "$DB_B_NAME" -v ON_ERROR_STOP=1 -c "GRANT ALL PRIVILEGES ON DATABASE ${DB_B_NAME} TO ${DB_USER};"
  ensure_schema_b "$DB_B_NAME" "$SCHEMA_IMAGELOG"
  psql_b_admin -d "$DB_B_NAME" -c "GRANT ALL PRIVILEGES ON SCHEMA public TO $DB_USER;" 2>/dev/null || true

  echo "4-b. ImageLog DDL (schema_imagelog.sql)..."
  run_sql_file_sp_b "$DB_B_NAME" "${SCHEMA_IMAGELOG}, public" "$SCRIPT_DIR/schema_imagelog.sql"
  echo "4-b-1. imagelog (guid, status) unique index..."
  run_sql_file_sp_b "$DB_B_NAME" "${SCHEMA_IMAGELOG}, public" "$SCRIPT_DIR/migrate-imagelog-guid-status-unique-20260320.sql"
  echo "   ✅ imagelog DDL 완료"

  echo "4-b-grant. ImageLog 스키마 GRANT (${SCHEMA_IMAGELOG} → ${DB_USER})..."
  grant_schema_objects_b "$DB_B_NAME" "$SCHEMA_IMAGELOG" "$DB_USER"

  if [ "${SKIP_INIT_DATA:-0}" = "1" ]; then
    echo "5-b. imagelog 샘플 ⏭️  생략 (SKIP_INIT_DATA=1)"
  elif [ "${CLOSED_NETWORK_MINIMAL}" = "1" ]; then
    echo "5-b / 5-b-1 / 5-b-2. imagelog 샘플·데모 마이그레이션 ⏭️  생략 (CLOSED_NETWORK_MINIMAL=1)"
  else
    echo "5-b. imagelog 샘플 데이터 삽입 중 (비어 있을 때만)..."
    run_sql_file_sp_b "$DB_B_NAME" "${SCHEMA_IMAGELOG}, public" "$SCRIPT_DIR/init-data-imagelog.sql"
    echo "   ✅ imagelog 샘플 데이터 완료"
    echo "5-b-1. imagelog 동일 GUID·상이 status 샘플 행 마이그레이션..."
    run_sql_file_sp_b "$DB_B_NAME" "${SCHEMA_IMAGELOG}, public" "$SCRIPT_DIR/migrate-imagelog-dup-guid-sample-20260330.sql"
    echo "5-b-2. imagelog companion-status 마이그레이션..."
    run_sql_file_sp_b "$DB_B_NAME" "${SCHEMA_IMAGELOG}, public" "$SCRIPT_DIR/migrate-imagelog-companion-status-20260330.sql"
  fi

  if [ "${LOAD_LOCAL_DECRYPT_TEST_DATA:-0}" = "1" ]; then
    echo "6-b-local-decrypt-test. 로컬 복호화 연습용 시드 (ImageLog, LOAD_LOCAL_DECRYPT_TEST_DATA=1)..."
    run_sql_file_sp_b "$DB_B_NAME" "${SCHEMA_IMAGELOG}, public" "$SCRIPT_DIR/init-data-local-decrypt-test-imagelog.sql"
    echo "   ✅ init-data-local-decrypt-test-imagelog.sql"
  fi

  echo ""
  echo "=== imagelog_only 설정 완료 ==="
  echo "데이터베이스(B/ImageLog): $DB_B_NAME (SCHEMA_IMAGELOG=$SCHEMA_IMAGELOG)"
  echo "앱 역할(이름만): $DB_USER"
  echo "ImageLog 엔드포인트: ${DB_B_HOST}:${DB_B_PORT} (런타임: APP_DATASOURCE_IMAGELOG_* 참고)"
  exit 0
fi

echo "=== PostgreSQL 데이터베이스 설정 (MODE=${SETUP_MODE}, SPLIT_PB=${SPLIT_PB}, INIT_DATA_FILE=${INIT_DATA_FILE}, SKIP_INIT_DATA=${SKIP_INIT_DATA}, CLOSED_NETWORK_MINIMAL=${CLOSED_NETWORK_MINIMAL}, LOAD_LOCAL_DECRYPT_TEST_DATA=${LOAD_LOCAL_DECRYPT_TEST_DATA}, A=${DB_A_NAME}, B=${DB_B_NAME}@${DB_B_HOST}:${DB_B_PORT}, PB=${DB_PB_NAME:-'(on A)'}, SCHEMA_SYS=${SCHEMA_SYS}, SCHEMA_PB=${SCHEMA_PB}, SCHEMA_IMAGELOG=${SCHEMA_IMAGELOG}) ==="
echo ""

if command -v brew >/dev/null 2>&1 && ! pg_isready -h "$DB_HOST" -p "$DB_PORT" >/dev/null 2>&1; then
  echo "PostgreSQL 서비스를 시작합니다..."
  brew services start postgresql@16 2>/dev/null || true
  sleep 3
fi

if ! pg_isready -h "$DB_HOST" -p "$DB_PORT" >/dev/null 2>&1; then
  echo "경고: pg_isready 실패 — PostgreSQL이 ${DB_HOST}:${DB_PORT} 에서 응답하지 않습니다. 계속 시도합니다."
fi

if [ "$SPLIT_PB" = "1" ]; then
  if ! pg_isready -h "$DB_PB_HOST" -p "$DB_PB_PORT" >/dev/null 2>&1; then
    echo "경고: pg_isready 실패 — PB 클러스터 ${DB_PB_HOST}:${DB_PB_PORT} 가 응답하지 않습니다. 계속 시도합니다."
  fi
fi

if [ "$RUN_B_IMAGELOG" = "1" ]; then
  if ! pg_isready -h "$DB_B_HOST" -p "$DB_B_PORT" >/dev/null 2>&1; then
    echo "경고: pg_isready 실패 — ImageLog 클러스터 ${DB_B_HOST}:${DB_B_PORT} 가 응답하지 않습니다. 계속 시도합니다."
  fi
fi

if [ "$SETUP_MODE" = "sys_only" ] && [ "$SPLIT_PB" = "1" ]; then
  echo "참고: split-PB + sys_only — PB DDL/마이그레이션은 이 실행에서 생략합니다. PB DB는 SETUP_MODE=pb_only 로 별도 실행하세요."
fi

if [ "$PRIMARY_ONLY" = "1" ] && [ "$SPLIT_PB" = "1" ]; then
  echo "참고: split-PB + primary_only — PB DDL/마이그레이션은 이 실행에서 생략합니다(PB는 pb_only). ImageLog(B) 단계 없음."
fi

echo "1. 데이터베이스 생성 중..."
psql_admin -tc "SELECT 1 FROM pg_database WHERE datname = '${DB_A_NAME}'" | grep -q 1 || \
  psql_admin -c "CREATE DATABASE ${DB_A_NAME};"
echo "   ✅ 데이터베이스 '${DB_A_NAME}' 확인됨(primary)"

if [ "$RUN_B_IMAGELOG" = "1" ]; then
  if [ "$DB_B_NAME" = "$DB_A_NAME" ] && [ "$DB_B_HOST" = "$DB_HOST" ] && [ "$DB_B_PORT" = "$DB_PORT" ]; then
    echo "   ℹ️  B=A 단일 DB — ImageLog 동일 인스턴스"
  else
    psql_b_admin -tc "SELECT 1 FROM pg_database WHERE datname = '${DB_B_NAME}'" | grep -q 1 || \
      psql_b_admin -c "CREATE DATABASE ${DB_B_NAME};"
    echo "   ✅ 데이터베이스 '${DB_B_NAME}' 확인됨(ImageLog 클러스터)"
  fi
fi

if [ "$SPLIT_PB" = "1" ] && [ "$PRIMARY_ONLY" != "1" ]; then
  psql_pb_admin -tc "SELECT 1 FROM pg_database WHERE datname = '${DB_PB_NAME}'" | grep -q 1 || \
    psql_pb_admin -c "CREATE DATABASE ${DB_PB_NAME};"
  echo "   ✅ 데이터베이스(PB) '${DB_PB_NAME}' 확인됨"
fi

echo "2. 사용자 생성 중..."
psql_admin -d "$DB_A_NAME" -tc "SELECT 1 FROM pg_user WHERE usename = '$DB_USER'" | grep -q 1 || \
  psql_admin -d "$DB_A_NAME" -c "CREATE USER $DB_USER WITH PASSWORD '$DB_PASSWORD';"
echo "   ✅ 사용자 '$DB_USER' 확인됨(primary)"

if [ "$RUN_B_IMAGELOG" = "1" ] && [ "$B_CLUSTER_DIFFERS" = "1" ]; then
  psql_b_admin -d postgres -tc "SELECT 1 FROM pg_roles WHERE rolname = '${DB_USER}'" | grep -q 1 || \
    psql_b_admin -d postgres -c "CREATE USER ${DB_USER} WITH PASSWORD '${DB_PASSWORD}';"
  echo "   ✅ 사용자 '$DB_USER' 확인됨(ImageLog 클러스터)"
fi

if [ "$SPLIT_PB" = "1" ] && [ "$PB_CLUSTER_DIFFERS" = "1" ] && [ "$PRIMARY_ONLY" != "1" ]; then
  psql_pb_admin -d postgres -tc "SELECT 1 FROM pg_roles WHERE rolname = '${DB_USER}'" | grep -q 1 || \
    psql_pb_admin -d postgres -c "CREATE USER ${DB_USER} WITH PASSWORD '${DB_PASSWORD}';"
  echo "   ✅ 사용자 '$DB_USER' 확인됨(PB 클러스터)"
fi

echo "3. DB 연결 권한 및 스키마 준비..."
psql_admin -d "$DB_A_NAME" -c "GRANT ALL PRIVILEGES ON DATABASE $DB_A_NAME TO $DB_USER;"
if [ "$RUN_B_IMAGELOG" = "1" ] && [ "$DB_B_NAME" != "$DB_A_NAME" ]; then
  psql_b_admin -d postgres -v ON_ERROR_STOP=1 -c "GRANT CONNECT ON DATABASE ${DB_B_NAME} TO ${DB_USER};"
  psql_b_admin -d "$DB_B_NAME" -c "GRANT ALL PRIVILEGES ON DATABASE $DB_B_NAME TO $DB_USER;"
fi
if [ "$SPLIT_PB" = "1" ] && [ "$PRIMARY_ONLY" != "1" ]; then
  grant_connect_pb_db
fi

ensure_schema "$DB_A_NAME" "$SCHEMA_SYS"
if [ "$SPLIT_PB" != "1" ]; then
  ensure_schema "$DB_A_NAME" "$SCHEMA_PB"
fi
if [ "$RUN_B_IMAGELOG" = "1" ]; then
  ensure_schema_b "$DB_B_NAME" "$SCHEMA_IMAGELOG"
  if [ "$DB_B_NAME" != "$DB_A_NAME" ]; then
    psql_b_admin -d "$DB_B_NAME" -c "GRANT ALL PRIVILEGES ON SCHEMA public TO $DB_USER;" 2>/dev/null || true
  fi
fi

psql_admin -d "$DB_A_NAME" -c "GRANT ALL PRIVILEGES ON SCHEMA public TO $DB_USER;" 2>/dev/null || true

if [ "$SPLIT_PB" = "1" ] && [ "$SETUP_MODE" = "full" ]; then
  echo "3-split-pb. PB database에 schema_pb_fep 선적용 (SYS DDL 전, DB=${DB_PB_NAME})..."
  ensure_schema_pb "$DB_PB_NAME" "$SCHEMA_PB"
  run_sql_file_sp_pb "$DB_PB_NAME" "${SCHEMA_PB}, public" "$SCRIPT_DIR/schema_pb_fep.sql"
  echo "   ✅ schema_pb_fep(PB DB) 적용"
fi

echo "4. DDL 적용 (PB → SYS → user_activity → ImageLog on B)..."
if [ "$SETUP_MODE" = "sys_only" ]; then
  echo "   ⏭️  SETUP_MODE=sys_only: schema_pb_fep.sql 생략 (기존 PB는 SCHEMA_PB=${SCHEMA_PB}에 있다고 가정)"
else
  if [ "$SPLIT_PB" = "1" ]; then
    echo "   ⏭️  split-PB: schema_pb_fep 는 A가 아닌 DB_PB_NAME(${DB_PB_NAME})에만 적용됨"
  else
    run_sql_file_sp "$DB_A_NAME" "${SCHEMA_PB}, public" "$SCRIPT_DIR/schema_pb_fep.sql"
  fi
fi
run_sql_file_sp "$DB_A_NAME" "$SP_A_DDL" "$SCRIPT_DIR/schema_sys.sql"
echo "4-ext. 외부 복제 ext_* / app_user_external_identity (레거시 DB 정렬, req 20260407)..."
run_sql_file_sp "$DB_A_NAME" "$SP_A_DDL" "$SCRIPT_DIR/migrate-external-identity-tables-20260407.sql"
echo "   ✅ migrate-external-identity-tables-20260407.sql 적용(또는 신규 스키마와 동일·no-op)"
echo "4-ext-1. department_org_link (복제 부서키 → department.code, 20260407)..."
run_sql_file_sp "$DB_A_NAME" "$SP_A_DDL" "$SCRIPT_DIR/migrate-department-org-link-20260407.sql"
echo "   ✅ migrate-department-org-link-20260407.sql 적용(또는 신규 스키마와 동일·no-op)"
echo "4-ext-1b. app_user.employee_number (인사정보 사번, 프로비저닝/ext_employee 동기화, 20260407)..."
run_sql_file_sp "$DB_A_NAME" "$SP_A_DDL" "$SCRIPT_DIR/migrate-app-user-employee-number-20260407.sql"
echo "   ✅ migrate-app-user-employee-number-20260407.sql 적용(또는 신규 스키마와 동일·no-op)"
echo "4-ext-1c. app_user.deleted_at (소프트 삭제, DBA·req 20260407)..."
run_sql_file_sp "$DB_A_NAME" "$SP_A_DDL" "$SCRIPT_DIR/migrate-app-user-soft-delete-20260407.sql"
echo "   ✅ migrate-app-user-soft-delete-20260407.sql 적용(또는 신규 스키마와 동일·no-op)"
echo "4-ext-2. HR_SAMPLE ext_employee.employee_number → 8자리 사용자 ID 형식 (20260407)..."
run_sql_file_sp "$DB_A_NAME" "$SP_A_DDL" "$SCRIPT_DIR/migrate-hr-sample-employee-number-userid-format-20260407.sql"
echo "   ✅ migrate-hr-sample-employee-number-userid-format-20260407.sql 적용(구 시드만 갱신, 재실행 no-op)"
echo "4-ext-3. HR Sync PoC ext_employee.snapshot_id + index + HR_SAMPLE snapshot 백필 (req 20260408)..."
echo "   순서: ext_employee 존재 후 · init-data(5단계) 전에 실행 — 신규 컬럼·시드 정합."
run_sql_file_sp "$DB_A_NAME" "$SP_A_DDL" "$SCRIPT_DIR/migrate-hr-sync-poc-ext-employee-snapshot-id-20260408.sql"
echo "   ✅ migrate-hr-sync-poc-ext-employee-snapshot-id-20260408.sql 적용(재실행 idempotent)"
run_sql_file_sp "$DB_A_NAME" "$SP_A_DDL" "$SCRIPT_DIR/schema_user_activity_log.sql"

echo "4a-user-activity-access-audit. user_activity_access_audit (append-only access audit, req 20260330 audit evidence)..."
run_sql_file_sp "$DB_A_NAME" "$SP_A_DDL" "$SCRIPT_DIR/migrate-user-activity-access-audit-20260406.sql"
echo "   ✅ user_activity_access_audit 적용(또는 이미 존재)"

if [ "$RUN_B_IMAGELOG" = "1" ]; then
  run_sql_file_sp_b "$DB_B_NAME" "${SCHEMA_IMAGELOG}, public" "$SCRIPT_DIR/schema_imagelog.sql"

  echo "4a-imagelog. (guid, status) 유니크 인덱스 — 레거시 DB 정렬 (req 20260320)..."
  run_sql_file_sp_b "$DB_B_NAME" "${SCHEMA_IMAGELOG}, public" "$SCRIPT_DIR/migrate-imagelog-guid-status-unique-20260320.sql"
  echo "   ✅ imagelog uq_imagelog_guid_row_status 적용(또는 이미 존재)"
else
  echo "   ⏭️  ImageLog(B) DDL 생략 (primary_only)"
fi

echo "   ✅ 스키마 파일 적용 완료"

echo "4b. 스키마별 GRANT (앱 사용자)..."
grant_schema_objects "$DB_A_NAME" "$SCHEMA_SYS" "$DB_USER"
if [ "$SPLIT_PB" != "1" ]; then
  grant_schema_objects "$DB_A_NAME" "$SCHEMA_PB" "$DB_USER"
fi
if [ "$RUN_B_IMAGELOG" = "1" ]; then
  grant_schema_objects_b "$DB_B_NAME" "$SCHEMA_IMAGELOG" "$DB_USER"
fi

echo "4b-ext. ext_department / ext_employee: 앱 역할(${DB_USER}) SELECT-only, ETL 역할(${DB_ETL_USER}) 쓰기 (req 20260407)..."
EXT_DEP_EXISTS=$(psql_admin -d "$DB_A_NAME" -tAc "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${SCHEMA_SYS}' AND table_name='ext_department';" 2>/dev/null || echo "0")
if [ "${EXT_DEP_EXISTS:-0}" = "1" ]; then
  psql_admin -d "$DB_A_NAME" -tc "SELECT 1 FROM pg_roles WHERE rolname = '${DB_ETL_USER}'" | grep -q 1 || \
    psql_admin -d "$DB_A_NAME" -c "CREATE USER ${DB_ETL_USER} WITH PASSWORD '${DB_ETL_PASSWORD}';"
  psql_admin -d "$DB_A_NAME" -v ON_ERROR_STOP=1 -c "GRANT CONNECT ON DATABASE ${DB_A_NAME} TO ${DB_ETL_USER};"
  psql_admin -d "$DB_A_NAME" -v ON_ERROR_STOP=1 -c "GRANT USAGE ON SCHEMA ${SCHEMA_SYS} TO ${DB_ETL_USER};"
  psql_admin -d "$DB_A_NAME" -v ON_ERROR_STOP=1 -c "
    REVOKE ALL ON TABLE ${SCHEMA_SYS}.ext_department FROM ${DB_USER};
    REVOKE ALL ON TABLE ${SCHEMA_SYS}.ext_employee FROM ${DB_USER};
    GRANT SELECT ON TABLE ${SCHEMA_SYS}.ext_department TO ${DB_USER};
    GRANT SELECT ON TABLE ${SCHEMA_SYS}.ext_employee TO ${DB_USER};
    GRANT INSERT, UPDATE, DELETE ON TABLE ${SCHEMA_SYS}.ext_department TO ${DB_ETL_USER};
    GRANT INSERT, UPDATE, DELETE ON TABLE ${SCHEMA_SYS}.ext_employee TO ${DB_ETL_USER};
    GRANT USAGE, SELECT ON SEQUENCE ${SCHEMA_SYS}.ext_department_id_seq TO ${DB_ETL_USER};
    GRANT USAGE, SELECT ON SEQUENCE ${SCHEMA_SYS}.ext_employee_id_seq TO ${DB_ETL_USER};
  "
  echo "   ✅ ext_*: ${DB_USER}=SELECT only; ${DB_ETL_USER}=DML + 시퀀스(ETL)"
else
  echo "   ⚠️  ${SCHEMA_SYS}.ext_department 없음 — migrate-external-identity-tables-20260407.sql 확인"
fi

echo "4c. app_user name 컬럼 마이그레이션 적용 중..."
run_sql_file_sp "$DB_A_NAME" "$SP_APP" "$SCRIPT_DIR/migrate-app-user-name-2026.sql"
echo "   ✅ app_user name 마이그레이션 완료"

echo "4d. search_history user_id 마이그레이션 (BIGINT, FK) 적용 중..."
run_sql_file_sp "$DB_A_NAME" "$SP_APP" "$SCRIPT_DIR/migrate-search-history-user-id-to-bigint.sql"
echo "   ✅ search_history user_id 마이그레이션 완료"

echo "4e. search_history request_reason 컬럼 마이그레이션 적용 중..."
run_sql_file_sp "$DB_A_NAME" "$SP_APP" "$SCRIPT_DIR/migrate-search-history-request-reason.sql"
echo "   ✅ search_history request_reason 마이그레이션 완료"

echo "4f. search_history 결과/복호화 대상 건수 컬럼 마이그레이션 적용 중..."
run_sql_file_sp "$DB_A_NAME" "$SP_APP" "$SCRIPT_DIR/migrate-search-history-result-counts.sql"
echo "   ✅ search_history 결과 건수 마이그레이션 완료"

echo "4g. 승인 스냅샷·decryption-allowed 복합 PK (row_status, req 20260320)..."
run_sql_file_sp "$DB_A_NAME" "$SP_APP" "$SCRIPT_DIR/migrate-sys-decryption-composite-pk-20260320.sql"
echo "   ✅ search_history_approved_row / user_decryption_allowed 복합 PK 적용(또는 이미 신규 스키마)"

echo "4h. permission_group_screen 컬럼 마이그레이션 (scope → functions → decrypt → scope-team, req 20260320)..."
run_sql_file_sp "$DB_A_NAME" "$SP_APP" "$SCRIPT_DIR/migrate-permission-group-screen-scope.sql"
run_sql_file_sp "$DB_A_NAME" "$SP_APP" "$SCRIPT_DIR/migrate-permission-group-screen-functions.sql"
run_sql_file_sp "$DB_A_NAME" "$SP_APP" "$SCRIPT_DIR/migrate-permission-group-screen-decrypt.sql"
run_sql_file_sp "$DB_A_NAME" "$SP_APP" "$SCRIPT_DIR/migrate-permission-group-screen-scope-team.sql"
echo "   ✅ permission_group_screen 컬럼·제약 정렬 완료(또는 이미 신규 스키마)"

if [ "${SKIP_INIT_DATA:-0}" = "1" ]; then
  echo "5. 초기 샘플 데이터 ⏭️  생략 (SKIP_INIT_DATA=1)"
elif [ "$SETUP_MODE" = "sys_only" ] && [ "${SYS_ONLY_LOAD_INIT_DATA:-0}" != "1" ]; then
  echo "5. 초기 샘플 데이터 ⏭️  생략 (sys_only; SYS_ONLY_LOAD_INIT_DATA=1 로 재실행 가능)"
else
  echo "5. 초기 샘플 데이터 삽입 중... (${INIT_DATA_FILE})"
  run_sql_file_sp "$DB_A_NAME" "$SP_APP" "$SCRIPT_DIR/${INIT_DATA_FILE}"
  echo "   ✅ 초기 데이터 삽입 완료"
fi

echo "5-emp-display. app_user.employee_number 백필 (사용자 관리 트리 사번 표시, 20260409)..."
run_sql_file_sp "$DB_A_NAME" "$SP_APP" "$SCRIPT_DIR/migrate-app-user-employee-number-display-backfill-20260409.sql"
echo "   ✅ migrate-app-user-employee-number-display-backfill-20260409.sql (idempotent)"

echo "5-poc-um-v2-screen. ADMIN_EXT → hr-sync-poc / user-management-v2-poc (init-data 미재실행 DB, 20260408)..."
run_sql_file_sp "$DB_A_NAME" "$SP_APP" "$SCRIPT_DIR/migrate-poc-user-mgmt-v2-screen-grant-20260408.sql"
echo "   ✅ migrate-poc-user-mgmt-v2-screen-grant-20260408.sql (idempotent)"

if [ "$SPLIT_PB" = "1" ]; then
  if [ "$SETUP_MODE" = "full" ]; then
    apply_split_pb_migrations_and_grants
  else
    echo "5-pb-fep / 5-pb-fep-partition ⏭️  split-PB — PB DB 마이그레이션 생략(SETUP_MODE=sys_only|primary_only 등 → pb_only 로 실행)"
  fi
else
  if [ "${CLOSED_NETWORK_MINIMAL}" = "1" ]; then
    echo "5-pb-fep. PB FEP pagination/bmsg 샘플 ⏭️  생략 (CLOSED_NETWORK_MINIMAL=1)"
  else
    echo "5-pb-fep. PB FEP pagination / bmsg 샘플 (migrate-pb-fep-pagination-bmsg-sample-20260330)..."
    run_sql_file_sp "$DB_A_NAME" "$SP_APP" "$SCRIPT_DIR/migrate-pb-fep-pagination-bmsg-sample-20260330.sql"
    echo "   ✅ PB FEP pagination/bmsg 샘플 적용(재실행 시 seed 행만 삭제 후 재삽입)"
  fi

  # Order: (1) ordinary -> partitioned + daily window (no DEFAULT) — migrate-pb-send-recv-partitioning-20260408.sql
  #        (2) legacy monthly *_YYYYMM -> daily — migrate-pb-send-recv-monthly-to-daily-20260414.sql
  #        (3) rebuild to RANGE(log_time) and drop log_timestamp — migrate-pb-send-recv-remove-log-timestamp-20260414.sql
  #        (4) log_time VARCHAR(20) + lexical backfill — migrate-pb-fep-log-time-varchar20-20260415.sql
  echo "5-pb-fep-partition. PB FEP(pb_send/pb_recv) 파티셔닝 전환(데이터 보존형, 일 단위 사전창, 20260408)..."
  run_sql_file_sp "$DB_A_NAME" "$SP_APP" "$SCRIPT_DIR/migrate-pb-send-recv-partitioning-20260408.sql"
  echo "5-pb-fep-partition-daily-upgrade. PB FEP 월→일 파티션(20260414, 멱등)..."
  run_sql_file_sp "$DB_A_NAME" "$SP_APP" "$SCRIPT_DIR/migrate-pb-send-recv-monthly-to-daily-20260414.sql"
  echo "5-pb-fep-drop-log-timestamp. PB FEP log_timestamp 물리 제거(20260414, 멱등)..."
  run_sql_file_sp "$DB_A_NAME" "$SP_APP" "$SCRIPT_DIR/migrate-pb-send-recv-remove-log-timestamp-20260414.sql"
  echo "5-pb-fep-log-time-varchar20. PB FEP log_time VARCHAR(20) + 백필(20260415, 멱등)..."
  run_sql_file_sp "$DB_A_NAME" "$SP_APP" "$SCRIPT_DIR/migrate-pb-fep-log-time-varchar20-20260415.sql"
  echo "   ✅ PB FEP 파티셔닝 마이그레이션 적용(이미 일 단위·멱등 경로면 no-op)"
fi

echo "5a. permission_group_screen main → pb-feplog/java-fw-imagelog 마이그레이션 적용 중..."
run_sql_file_sp "$DB_A_NAME" "$SP_APP" "$SCRIPT_DIR/migrate-main-to-pb-feplog-java-fw-imagelog.sql"
echo "   ✅ main → pb-feplog/java-fw-imagelog 마이그레이션 완료"

echo "5a-1. permission_group_screen java-fw_imagelog → java-fw-imagelog 정규화 적용 중..."
run_sql_file_sp "$DB_A_NAME" "$SP_APP" "$SCRIPT_DIR/migrate-permission-group-screen-imagelog-canonical.sql"
echo "   ✅ java-fw_imagelog 정규화 완료"

if [ "$RUN_B_IMAGELOG" != "1" ]; then
  echo "5b / 5b-1 / 5b-2. imagelog 샘플 ⏭️  생략 (primary_only)"
elif [ "${CLOSED_NETWORK_MINIMAL}" = "1" ]; then
  echo "5b / 5b-1 / 5b-2. imagelog 샘플·데모 마이그레이션 ⏭️  생략 (CLOSED_NETWORK_MINIMAL=1)"
else
  echo "5b. imagelog 샘플 데이터 삽입 중 (비어 있을 때만)..."
  run_sql_file_sp_b "$DB_B_NAME" "${SCHEMA_IMAGELOG}, public" "$SCRIPT_DIR/init-data-imagelog.sql"
  echo "   ✅ imagelog 샘플 데이터 완료"

  echo "5b-1. imagelog 동일 GUID·상이 status 샘플 행 마이그레이션 (req 20260330)..."
  run_sql_file_sp_b "$DB_B_NAME" "${SCHEMA_IMAGELOG}, public" "$SCRIPT_DIR/migrate-imagelog-dup-guid-sample-20260330.sql"
  echo "   ✅ imagelog duplicate-GUID sample 적용(또는 이미 존재)"

  echo "5b-2. imagelog 단일-status guid 동반 행 (input↔output/error→input) 마이그레이션 (req 20260330)..."
  run_sql_file_sp_b "$DB_B_NAME" "${SCHEMA_IMAGELOG}, public" "$SCRIPT_DIR/migrate-imagelog-companion-status-20260330.sql"
  echo "   ✅ imagelog companion-status 적용(또는 이미 존재)"
fi

echo "6. app_user id 마이그레이션 (admin=20269999, 기타=20260001~) 적용 중..."
run_sql_file_sp "$DB_A_NAME" "$SP_APP" "$SCRIPT_DIR/migrate-app-user-id-2026.sql"
echo "   ✅ app_user id 마이그레이션 완료"

echo "6a. app_user_permission_group.user_id 정규화 (레거시 id::text → username, FK·감사 조회 정합, req 20260316·20260407)..."
run_sql_file_sp "$DB_A_NAME" "$SP_APP" "$SCRIPT_DIR/migrate-app-user-permission-group-user-id-to-username-20260407.sql"
echo "   ✅ app_user_permission_group user_id 정규화 완료(또는 이미 username)"

if [ "${LOAD_LOCAL_DECRYPT_TEST_DATA:-0}" = "1" ]; then
  echo "6b-local-decrypt-test. 로컬 복호화 연습용 시드 (LOAD_LOCAL_DECRYPT_TEST_DATA=1, dev/local only)..."
  if [ "$RUN_B_IMAGELOG" = "1" ]; then
    run_sql_file_sp_b "$DB_B_NAME" "${SCHEMA_IMAGELOG}, public" "$SCRIPT_DIR/init-data-local-decrypt-test-imagelog.sql"
    echo "   ✅ init-data-local-decrypt-test-imagelog.sql"
  else
    echo "   ⏭️  ImageLog local-decrypt 시드 생략 (primary_only)"
  fi
  if [ "$PRIMARY_ONLY" = "1" ]; then
    echo "   ⏭️  PB local-decrypt 시드 생략 (primary_only; PB는 별도 실행)"
  elif [ "$SPLIT_PB" = "1" ]; then
    run_sql_file_sp_pb "$DB_PB_NAME" "${SCHEMA_PB}, public" "$SCRIPT_DIR/init-data-local-decrypt-test-pbfep.sql"
    echo "   ✅ init-data-local-decrypt-test-pbfep.sql (PB DB=${DB_PB_NAME})"
    run_sql_file_sp_pb "$DB_PB_NAME" "${SCHEMA_PB}, public" "$SCRIPT_DIR/seed-pb-fep-keyword-decrypt-20260415.sql"
    echo "   ✅ seed-pb-fep-keyword-decrypt-20260415.sql (PB DB=${DB_PB_NAME})"
  else
    run_sql_file_sp "$DB_A_NAME" "${SCHEMA_PB}, public" "$SCRIPT_DIR/init-data-local-decrypt-test-pbfep.sql"
    echo "   ✅ init-data-local-decrypt-test-pbfep.sql (DB A, SCHEMA_PB)"
    run_sql_file_sp "$DB_A_NAME" "${SCHEMA_PB}, public" "$SCRIPT_DIR/seed-pb-fep-keyword-decrypt-20260415.sql"
    echo "   ✅ seed-pb-fep-keyword-decrypt-20260415.sql (DB A, SCHEMA_PB)"
  fi
fi

echo ""
echo "=== 설정 완료 ==="
echo "데이터베이스 A: $DB_A_NAME  (SYS=$SCHEMA_SYS, PB=$SCHEMA_PB)"
if [ "$SPLIT_PB" = "1" ]; then
  echo "데이터베이스 PB: $DB_PB_NAME @ ${DB_PB_HOST}:${DB_PB_PORT} (런타임: APP_DATASOURCE_PB_*)"
fi
if [ "$RUN_B_IMAGELOG" = "1" ]; then
  echo "데이터베이스 B: $DB_B_NAME  (ImageLog schema=$SCHEMA_IMAGELOG @ ${DB_B_HOST}:${DB_B_PORT})"
else
  echo "데이터베이스 B: (skipped — primary_only)"
fi
echo "앱 역할(이름만): $DB_USER"
echo "Primary 엔드포인트: ${DB_HOST}:${DB_PORT}"
echo "자격 증명은 DB_USER / DB_PASSWORD 등 환경 변수로만 설정(값은 로그에 출력하지 않음)"
echo ""
echo "런타임 JDBC·스키마 매핑: docs/contract.md (SPRING_DATASOURCE_*, APP_DATASOURCE_PB_*, APP_DATASOURCE_IMAGELOG_*)"
echo "search_path(앱): SCHEMA_SYS, SCHEMA_PB 및 백엔드 설정 — DB_SETUP_GUIDE.md 참고"
