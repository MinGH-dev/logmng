#!/bin/bash

# PostgreSQL 16 데이터베이스 설정 스크립트
#
# 환경에 따라 postgres 역할이 없을 수 있음 (예: Homebrew PostgreSQL은 OS 사용자 사용).
# 그 경우: DB_SUPERUSER=$USER ./setup.sh
#
# --- Multi-database / multi-schema (요구: 20260320-multi-datasource-schema-configuration) ---
# DB A: 시스템(SCHEMA_SYS) + PB FEP(SCHEMA_PB). DB B: ImageLog(SCHEMA_IMAGELOG).
# 새 변수를 설정하지 않으면 기존과 동일: 단일 DB(logmng)·스키마 public.
#
# 환경 변수 (선택, 기본값은 단일 DB public 흐름):
#   DB_NAME          레거시 DB 이름 (기본: logmng). DB_A_NAME 미설정 시 사용.
#   DB_A_NAME        데이터베이스 A (시스템+PB). 기본: DB_NAME 또는 logmng
#   DB_B_NAME        데이터베이스 B (ImageLog). 기본: DB_A_NAME (단일 DB)
#   SCHEMA_SYS       A 위 시스템 DDL 대상 스키마 (기본: public)
#   SCHEMA_PB        A 위 PB FEP DDL 대상 스키마 (기본: public)
#   SCHEMA_IMAGELOG  B 위 imagelog DDL 대상 스키마 (기본: public)
#   DB_SUPERUSER     슈퍼유저 (기본: postgres)
#   DB_USER / DB_PASSWORD / DB_HOST / DB_PORT  애플리케이션 DB 역할 (기본: logmng / logmng123 / localhost / 5432)
#
# 예: A에 logmng_sys + logmng, B는 별도 DB imagelog_store, ImageLog는 public
#   DB_A_NAME=logmng DB_B_NAME=imagelog_store SCHEMA_SYS=logmng_sys SCHEMA_PB=logmng SCHEMA_IMAGELOG=public ./setup.sh
#
# 수동 적용 예 (sys → logmng_sys, PB → logmng, imagelog → B):
#   psql -U postgres -d logmng -c "CREATE SCHEMA IF NOT EXISTS logmng_sys; CREATE SCHEMA IF NOT EXISTS logmng;"
#   psql -U postgres -d logmng -v ON_ERROR_STOP=1 -c "SET search_path TO logmng, public;" -f schema_pb_fep.sql
#   psql -U postgres -d logmng -v ON_ERROR_STOP=1 -c "SET search_path TO logmng_sys, logmng, public;" -f schema_sys.sql
#   psql -U postgres -d imagelog_store -v ON_ERROR_STOP=1 -c "SET search_path TO public;" -f schema_imagelog.sql
#
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

DB_SUPERUSER="${DB_SUPERUSER:-postgres}"
DB_NAME="${DB_NAME:-logmng}"
DB_A_NAME="${DB_A_NAME:-$DB_NAME}"
DB_B_NAME="${DB_B_NAME:-$DB_A_NAME}"

SCHEMA_SYS="${SCHEMA_SYS:-public}"
SCHEMA_PB="${SCHEMA_PB:-public}"
SCHEMA_IMAGELOG="${SCHEMA_IMAGELOG:-public}"

DB_USER="${DB_USER:-logmng}"
DB_PASSWORD="${DB_PASSWORD:-logmng123}"
DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"

# 슈퍼유저 비밀번호(로컬 trust면 불필요). 예: PGPASSWORD_SUPER=secret
export PGPASSWORD="${PGPASSWORD_SUPER:-${PGPASSWORD:-}}"

psql_admin() {
  psql -U "$DB_SUPERUSER" -h "$DB_HOST" -p "$DB_PORT" "$@"
}

ensure_schema() {
  local db="$1"
  local sch="$2"
  if [ "$sch" = "public" ]; then
    return 0
  fi
  psql_admin -d "$db" -v ON_ERROR_STOP=1 -c "CREATE SCHEMA IF NOT EXISTS ${sch};"
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

run_sql_file_sp() {
  local db="$1"
  local search_path_csv="$2"
  local file="$3"
  psql_admin -d "$db" -v ON_ERROR_STOP=1 \
    -c "SET search_path TO ${search_path_csv};" \
    -f "$file"
}

echo "=== PostgreSQL 데이터베이스 설정 (A=${DB_A_NAME}, B=${DB_B_NAME}, SCHEMA_SYS=${SCHEMA_SYS}, SCHEMA_PB=${SCHEMA_PB}, SCHEMA_IMAGELOG=${SCHEMA_IMAGELOG}) ==="
echo ""

# PostgreSQL 서비스 시작 확인 (macOS Homebrew)
if command -v brew >/dev/null 2>&1 && ! pg_isready -h "$DB_HOST" -p "$DB_PORT" >/dev/null 2>&1; then
  echo "PostgreSQL 서비스를 시작합니다..."
  brew services start postgresql@16 2>/dev/null || true
  sleep 3
fi

if ! pg_isready -h "$DB_HOST" -p "$DB_PORT" >/dev/null 2>&1; then
  echo "경고: pg_isready 실패 — PostgreSQL이 ${DB_HOST}:${DB_PORT} 에서 응답하지 않습니다. 계속 시도합니다."
fi

echo "1. 데이터베이스 생성 중..."
for dbname in "$DB_A_NAME" "$DB_B_NAME"; do
  psql_admin -tc "SELECT 1 FROM pg_database WHERE datname = '$dbname'" | grep -q 1 || \
    psql_admin -c "CREATE DATABASE $dbname;"
  echo "   ✅ 데이터베이스 '$dbname' 확인됨"
done

echo "2. 사용자 생성 중..."
psql_admin -d "$DB_A_NAME" -tc "SELECT 1 FROM pg_user WHERE usename = '$DB_USER'" | grep -q 1 || \
  psql_admin -d "$DB_A_NAME" -c "CREATE USER $DB_USER WITH PASSWORD '$DB_PASSWORD';"
echo "   ✅ 사용자 '$DB_USER' 확인됨"

echo "3. DB 연결 권한 및 스키마 준비..."
psql_admin -d "$DB_A_NAME" -c "GRANT ALL PRIVILEGES ON DATABASE $DB_A_NAME TO $DB_USER;"
if [ "$DB_B_NAME" != "$DB_A_NAME" ]; then
  psql_admin -d "$DB_B_NAME" -c "GRANT ALL PRIVILEGES ON DATABASE $DB_B_NAME TO $DB_USER;"
fi

ensure_schema "$DB_A_NAME" "$SCHEMA_SYS"
ensure_schema "$DB_A_NAME" "$SCHEMA_PB"
ensure_schema "$DB_B_NAME" "$SCHEMA_IMAGELOG"

# 레거시: public 단일 스키마 시 기존과 동일
psql_admin -d "$DB_A_NAME" -c "GRANT ALL PRIVILEGES ON SCHEMA public TO $DB_USER;" 2>/dev/null || true

echo "4. DDL 적용 (PB → SYS → user_activity → ImageLog on B)..."
run_sql_file_sp "$DB_A_NAME" "${SCHEMA_PB}, public" "$SCRIPT_DIR/schema_pb_fep.sql"
run_sql_file_sp "$DB_A_NAME" "${SCHEMA_SYS}, ${SCHEMA_PB}, public" "$SCRIPT_DIR/schema_sys.sql"
run_sql_file_sp "$DB_A_NAME" "${SCHEMA_SYS}, ${SCHEMA_PB}, public" "$SCRIPT_DIR/schema_user_activity_log.sql"
run_sql_file_sp "$DB_B_NAME" "${SCHEMA_IMAGELOG}, public" "$SCRIPT_DIR/schema_imagelog.sql"

echo "   ✅ 스키마 파일 적용 완료"

echo "4b. 스키마별 GRANT (앱 사용자)..."
grant_schema_objects "$DB_A_NAME" "$SCHEMA_SYS" "$DB_USER"
grant_schema_objects "$DB_A_NAME" "$SCHEMA_PB" "$DB_USER"
grant_schema_objects "$DB_B_NAME" "$SCHEMA_IMAGELOG" "$DB_USER"

SP_APP="${SCHEMA_SYS}, ${SCHEMA_PB}, public"

# app_user.name 컬럼 추가 (요건 20260316-login-id-user-name-display). 기존 DB에만 적용; idempotent.
echo "4c. app_user name 컬럼 마이그레이션 적용 중..."
run_sql_file_sp "$DB_A_NAME" "$SP_APP" "$SCRIPT_DIR/migrate-app-user-name-2026.sql"
echo "   ✅ app_user name 마이그레이션 완료"

# search_history.user_id VARCHAR -> BIGINT NOT NULL FK to app_user(id).
echo "4d. search_history user_id 마이그레이션 (BIGINT, FK) 적용 중..."
run_sql_file_sp "$DB_A_NAME" "$SP_APP" "$SCRIPT_DIR/migrate-search-history-user-id-to-bigint.sql"
echo "   ✅ search_history user_id 마이그레이션 완료"

echo "4e. search_history request_reason 컬럼 마이그레이션 적용 중..."
run_sql_file_sp "$DB_A_NAME" "$SP_APP" "$SCRIPT_DIR/migrate-search-history-request-reason.sql"
echo "   ✅ search_history request_reason 마이그레이션 완료"

echo "4f. search_history 결과/복호화 대상 건수 컬럼 마이그레이션 적용 중..."
run_sql_file_sp "$DB_A_NAME" "$SP_APP" "$SCRIPT_DIR/migrate-search-history-result-counts.sql"
echo "   ✅ search_history 결과 건수 마이그레이션 완료"

echo "5. 초기 샘플 데이터 삽입 중..."
run_sql_file_sp "$DB_A_NAME" "$SP_APP" "$SCRIPT_DIR/init-data.sql"
echo "   ✅ 초기 데이터 삽입 완료"

echo "5a. permission_group_screen main → pb-feplog/java-fw-imagelog 마이그레이션 적용 중..."
run_sql_file_sp "$DB_A_NAME" "$SP_APP" "$SCRIPT_DIR/migrate-main-to-pb-feplog-java-fw-imagelog.sql"
echo "   ✅ main → pb-feplog/java-fw-imagelog 마이그레이션 완료"

echo "5a-1. permission_group_screen java-fw_imagelog → java-fw-imagelog 정규화 적용 중..."
run_sql_file_sp "$DB_A_NAME" "$SP_APP" "$SCRIPT_DIR/migrate-permission-group-screen-imagelog-canonical.sql"
echo "   ✅ java-fw_imagelog 정규화 완료"

echo "5b. imagelog 샘플 데이터 삽입 중 (비어 있을 때만)..."
run_sql_file_sp "$DB_B_NAME" "${SCHEMA_IMAGELOG}, public" "$SCRIPT_DIR/init-data-imagelog.sql"
echo "   ✅ imagelog 샘플 데이터 완료"

echo "6. app_user id 마이그레이션 (admin=20269999, 기타=20260001~) 적용 중..."
run_sql_file_sp "$DB_A_NAME" "$SP_APP" "$SCRIPT_DIR/migrate-app-user-id-2026.sql"
echo "   ✅ app_user id 마이그레이션 완료"

echo ""
echo "=== 설정 완료 ==="
echo "데이터베이스 A: $DB_A_NAME  (SYS=$SCHEMA_SYS, PB=$SCHEMA_PB)"
echo "데이터베이스 B: $DB_B_NAME  (ImageLog schema=$SCHEMA_IMAGELOG)"
echo "사용자: $DB_USER"
echo "비밀번호: (설정값; 프로덕션에서는 환경 변수로 덮어쓰기)"
echo "호스트: $DB_HOST"
echo "포트: $DB_PORT"
echo ""
echo "Primary JDBC 예: jdbc:postgresql://$DB_HOST:$DB_PORT/$DB_A_NAME"
echo "ImageLog JDBC 예: jdbc:postgresql://$DB_HOST:$DB_PORT/$DB_B_NAME"
echo "search_path(앱): 운영 시 SCHEMA_SYS, SCHEMA_PB 또는 백엔드 설정에 따름 — DB_SETUP_GUIDE.md 참고"
