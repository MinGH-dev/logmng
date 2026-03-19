#!/bin/bash

# PostgreSQL 16 데이터베이스 설정 스크립트
#
# 환경에 따라 postgres 역할이 없을 수 있음 (예: Homebrew PostgreSQL은 OS 사용자 사용).
# 그 경우: DB_SUPERUSER=$USER ./setup.sh
#
# DB/사용자는 이미 있고 스키마·init-data만 적용할 때:
#   psql -U "$DB_SUPERUSER" -h localhost -p 5432 -d logmng -f "$(dirname "$0")/schema.sql"
#   psql -U "$DB_SUPERUSER" -h localhost -p 5432 -d logmng -f "$(dirname "$0")/schema_user_activity_log.sql"
#   psql -U "$DB_SUPERUSER" -h localhost -p 5432 -d logmng -f "$(dirname "$0")/init-data.sql"

set -e

DB_SUPERUSER="${DB_SUPERUSER:-postgres}"
DB_NAME="logmng"
DB_USER="logmng"
DB_PASSWORD="logmng123"
DB_HOST="localhost"
DB_PORT="5432"

echo "=== PostgreSQL 16 데이터베이스 설정 ==="
echo ""

# PostgreSQL 서비스 시작 확인
if ! pg_isready -h $DB_HOST -p $DB_PORT > /dev/null 2>&1; then
    echo "PostgreSQL 서비스를 시작합니다..."
    brew services start postgresql@16
    sleep 3
fi

# 데이터베이스 생성
echo "1. 데이터베이스 생성 중..."
psql -U "$DB_SUPERUSER" -h $DB_HOST -p $DB_PORT -tc "SELECT 1 FROM pg_database WHERE datname = '$DB_NAME'" | grep -q 1 || \
    psql -U "$DB_SUPERUSER" -h $DB_HOST -p $DB_PORT -c "CREATE DATABASE $DB_NAME;"
echo "   ✅ 데이터베이스 '$DB_NAME' 생성 완료"

# 사용자 생성
echo "2. 사용자 생성 중..."
psql -U "$DB_SUPERUSER" -h $DB_HOST -p $DB_PORT -d $DB_NAME -tc "SELECT 1 FROM pg_user WHERE usename = '$DB_USER'" | grep -q 1 || \
    psql -U "$DB_SUPERUSER" -h $DB_HOST -p $DB_PORT -d $DB_NAME -c "CREATE USER $DB_USER WITH PASSWORD '$DB_PASSWORD';"
echo "   ✅ 사용자 '$DB_USER' 생성 완료"

# 권한 부여
echo "3. 권한 부여 중..."
psql -U "$DB_SUPERUSER" -h $DB_HOST -p $DB_PORT -d $DB_NAME -c "GRANT ALL PRIVILEGES ON DATABASE $DB_NAME TO $DB_USER;"
psql -U "$DB_SUPERUSER" -h $DB_HOST -p $DB_PORT -d $DB_NAME -c "GRANT ALL PRIVILEGES ON SCHEMA public TO $DB_USER;"
echo "   ✅ 권한 부여 완료"

# 스키마 생성
# For existing DBs, run migrations as needed (once each):
#   psql -U "$DB_SUPERUSER" -h $DB_HOST -p $DB_PORT -d $DB_NAME -f "$(dirname "$0")/migrate-search-history-approved-row.sql"
#   psql -U "$DB_SUPERUSER" -h $DB_HOST -p $DB_PORT -d $DB_NAME -f "$(dirname "$0")/migrate-app-user-position.sql"
#   psql -U "$DB_SUPERUSER" -h $DB_HOST -p $DB_PORT -d $DB_NAME -f "$(dirname "$0")/migrate-search-history-request-reason.sql"
echo "4. 테이블 및 인덱스 생성 중..."
psql -U "$DB_SUPERUSER" -h $DB_HOST -p $DB_PORT -d $DB_NAME -f "$(dirname "$0")/schema.sql"
psql -U "$DB_SUPERUSER" -h $DB_HOST -p $DB_PORT -d $DB_NAME -f "$(dirname "$0")/schema_user_activity_log.sql"
psql -U "$DB_SUPERUSER" -h $DB_HOST -p $DB_PORT -d $DB_NAME -f "$(dirname "$0")/schema_imagelog.sql"
echo "   ✅ 스키마 생성 완료"

# app_user.name 컬럼 추가 (요건 20260316-login-id-user-name-display). 기존 DB에만 적용; idempotent.
echo "4b. app_user name 컬럼 마이그레이션 적용 중..."
psql -U "$DB_SUPERUSER" -h $DB_HOST -p $DB_PORT -d $DB_NAME -f "$(dirname "$0")/migrate-app-user-name-2026.sql"
echo "   ✅ app_user name 마이그레이션 완료"

# search_history.user_id VARCHAR -> BIGINT NOT NULL FK to app_user(id). Req 20260316-search-history-user-id-query-and-naming. Idempotent.
# Must be applied before relying on decrypt execution path (POST /api/logs/decrypt); req 20260317-decrypt-execution-user-id-fix.
echo "4c. search_history user_id 마이그레이션 (BIGINT, FK) 적용 중..."
psql -U "$DB_SUPERUSER" -h $DB_HOST -p $DB_PORT -d $DB_NAME -f "$(dirname "$0")/migrate-search-history-user-id-to-bigint.sql"
echo "   ✅ search_history user_id 마이그레이션 완료"

# search_history.request_reason (TEXT NULL). Req 20260317-request-reason-and-search-history-search-fields. Idempotent.
echo "4d. search_history request_reason 컬럼 마이그레이션 적용 중..."
psql -U "$DB_SUPERUSER" -h $DB_HOST -p $DB_PORT -d $DB_NAME -f "$(dirname "$0")/migrate-search-history-request-reason.sql"
echo "   ✅ search_history request_reason 마이그레이션 완료"

# search_history search_result_total_count, decryption_target_count (INTEGER NULL). Idempotent.
echo "4e. search_history 결과/복호화 대상 건수 컬럼 마이그레이션 적용 중..."
psql -U "$DB_SUPERUSER" -h $DB_HOST -p $DB_PORT -d $DB_NAME -f "$(dirname "$0")/migrate-search-history-result-counts.sql"
echo "   ✅ search_history 결과 건수 마이그레이션 완료"

# 초기 데이터 삽입
echo "5. 초기 샘플 데이터 삽입 중..."
psql -U "$DB_SUPERUSER" -h $DB_HOST -p $DB_PORT -d $DB_NAME -f "$(dirname "$0")/init-data.sql"
echo "   ✅ 초기 데이터 삽입 완료"

# permission_group_screen: main → pb-feplog, java-fw-imagelog (req 20260318). 기존 DB에 main 행이 있으면 복사; 신규 설치(init-data에 main 없음)면 무해. Idempotent.
echo "5a. permission_group_screen main → pb-feplog/java-fw-imagelog 마이그레이션 적용 중..."
psql -U "$DB_SUPERUSER" -h $DB_HOST -p $DB_PORT -d $DB_NAME -f "$(dirname "$0")/migrate-main-to-pb-feplog-java-fw-imagelog.sql"
echo "   ✅ main → pb-feplog/java-fw-imagelog 마이그레이션 완료"

# permission_group_screen: legacy typo java-fw_imagelog → java-fw-imagelog (req 20260318-permission-group-menu-invalid-screen-id-imagelog). Idempotent.
echo "5a-1. permission_group_screen java-fw_imagelog → java-fw-imagelog 정규화 적용 중..."
psql -U "$DB_SUPERUSER" -h $DB_HOST -p $DB_PORT -d $DB_NAME -f "$(dirname "$0")/migrate-permission-group-screen-imagelog-canonical.sql"
echo "   ✅ java-fw_imagelog 정규화 완료"

# imagelog 샘플 데이터 (테이블이 비어 있을 때만 삽입; 기존 데이터 유지. Req 20260318-image-log-sample-data-preserve)
echo "5b. imagelog 샘플 데이터 삽입 중 (비어 있을 때만)..."
psql -U "$DB_SUPERUSER" -h $DB_HOST -p $DB_PORT -d $DB_NAME -f "$(dirname "$0")/init-data-imagelog.sql"
echo "   ✅ imagelog 샘플 데이터 완료"

# app_user.id 부여: admin=20269999, 나머지=20260001부터 (기존 DB 재설정 시 또는 마이그레이션)
echo "6. app_user id 마이그레이션 (admin=20269999, 기타=20260001~) 적용 중..."
psql -U "$DB_SUPERUSER" -h $DB_HOST -p $DB_PORT -d $DB_NAME -f "$(dirname "$0")/migrate-app-user-id-2026.sql"
echo "   ✅ app_user id 마이그레이션 완료"

echo ""
echo "=== 설정 완료 ==="
echo "데이터베이스: $DB_NAME"
echo "사용자: $DB_USER"
echo "비밀번호: $DB_PASSWORD"
echo "호스트: $DB_HOST"
echo "포트: $DB_PORT"
echo ""
echo "연결 문자열: postgresql://$DB_USER:$DB_PASSWORD@$DB_HOST:$DB_PORT/$DB_NAME"





