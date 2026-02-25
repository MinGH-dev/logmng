#!/bin/bash

# PostgreSQL 16 데이터베이스 설정 스크립트

set -e

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
psql -U postgres -h $DB_HOST -p $DB_PORT -tc "SELECT 1 FROM pg_database WHERE datname = '$DB_NAME'" | grep -q 1 || \
    psql -U postgres -h $DB_HOST -p $DB_PORT -c "CREATE DATABASE $DB_NAME;"
echo "   ✅ 데이터베이스 '$DB_NAME' 생성 완료"

# 사용자 생성
echo "2. 사용자 생성 중..."
psql -U postgres -h $DB_HOST -p $DB_PORT -d $DB_NAME -tc "SELECT 1 FROM pg_user WHERE usename = '$DB_USER'" | grep -q 1 || \
    psql -U postgres -h $DB_HOST -p $DB_PORT -d $DB_NAME -c "CREATE USER $DB_USER WITH PASSWORD '$DB_PASSWORD';"
echo "   ✅ 사용자 '$DB_USER' 생성 완료"

# 권한 부여
echo "3. 권한 부여 중..."
psql -U postgres -h $DB_HOST -p $DB_PORT -d $DB_NAME -c "GRANT ALL PRIVILEGES ON DATABASE $DB_NAME TO $DB_USER;"
psql -U postgres -h $DB_HOST -p $DB_PORT -d $DB_NAME -c "GRANT ALL PRIVILEGES ON SCHEMA public TO $DB_USER;"
echo "   ✅ 권한 부여 완료"

# 스키마 생성
# For existing DBs created before search_history_approved_row was added, run once:
#   psql -U postgres -h $DB_HOST -p $DB_PORT -d $DB_NAME -f "$(dirname "$0")/migrate-search-history-approved-row.sql"
echo "4. 테이블 및 인덱스 생성 중..."
psql -U postgres -h $DB_HOST -p $DB_PORT -d $DB_NAME -f "$(dirname "$0")/schema.sql"
psql -U postgres -h $DB_HOST -p $DB_PORT -d $DB_NAME -f "$(dirname "$0")/schema_user_activity_log.sql"
echo "   ✅ 스키마 생성 완료"

# 초기 데이터 삽입
echo "5. 초기 샘플 데이터 삽입 중..."
psql -U postgres -h $DB_HOST -p $DB_PORT -d $DB_NAME -f "$(dirname "$0")/init-data.sql"
echo "   ✅ 초기 데이터 삽입 완료"

echo ""
echo "=== 설정 완료 ==="
echo "데이터베이스: $DB_NAME"
echo "사용자: $DB_USER"
echo "비밀번호: $DB_PASSWORD"
echo "호스트: $DB_HOST"
echo "포트: $DB_PORT"
echo ""
echo "연결 문자열: postgresql://$DB_USER:$DB_PASSWORD@$DB_HOST:$DB_PORT/$DB_NAME"





