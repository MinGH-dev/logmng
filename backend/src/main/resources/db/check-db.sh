#!/bin/bash

# PostgreSQL 데이터베이스 상태 점검 스크립트
#
# 환경 변수 (선택, setup.sh와 동일한 기본값):
#   DB_NAME / DB_A_NAME / DB_B_NAME / SCHEMA_SYS / SCHEMA_PB / SCHEMA_IMAGELOG
#   DB_USER / DB_PASSWORD / DB_HOST / DB_PORT
#   DB_SUPERUSER (기본: postgres)
#   PGPASSWORD_SUPER — 슈퍼유저 비밀번호 (기본: postgres; 로컬 trust면 무시될 수 있음)

set -e

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

DB_SUPERUSER="${DB_SUPERUSER:-postgres}"
export PGPASSWORD_SUPER="${PGPASSWORD_SUPER:-postgres}"

psql_su() {
  PGPASSWORD="$PGPASSWORD_SUPER" psql -U "$DB_SUPERUSER" -h "$DB_HOST" -p "$DB_PORT" "$@"
}

psql_app() {
  PGPASSWORD="$DB_PASSWORD" psql -U "$DB_USER" -h "$DB_HOST" -p "$DB_PORT" "$@"
}

echo "=========================================="
echo "PostgreSQL 데이터베이스 상태 점검"
echo "  A=${DB_A_NAME} (SCHEMA_SYS=${SCHEMA_SYS}, SCHEMA_PB=${SCHEMA_PB})"
echo "  B=${DB_B_NAME} (SCHEMA_IMAGELOG=${SCHEMA_IMAGELOG})"
echo "=========================================="
echo ""

# 1. PostgreSQL 서비스 상태 확인
echo "1. PostgreSQL 서비스 상태 확인"
if command -v brew >/dev/null 2>&1 && brew services list 2>/dev/null | grep -q "postgresql@16.*started"; then
  echo "   ✅ PostgreSQL 16 서비스 실행 중 (brew)"
else
  echo "   ⚠️  brew postgresql@16 상태를 확인하지 못함 (Linux 등에서는 pg_isready로 판단)"
fi
echo ""

# 2. PostgreSQL 연결 가능 여부 확인
echo "2. PostgreSQL 연결 가능 여부"
if pg_isready -h "$DB_HOST" -p "$DB_PORT" >/dev/null 2>&1; then
  echo "   ✅ PostgreSQL 서버 연결 가능 (포트: $DB_PORT)"
else
  echo "   ❌ PostgreSQL 서버 연결 불가"
  echo "   💡 서비스 시작 후 잠시 대기 필요"
fi
echo ""

# 3. 데이터베이스 존재 확인
echo "3. 데이터베이스 존재 확인"
DB_A_EXISTS=$(psql_su -tAc "SELECT 1 FROM pg_database WHERE datname='${DB_A_NAME}'" 2>/dev/null || echo "")
if [ "$DB_A_EXISTS" = "1" ]; then
  echo "   ✅ 데이터베이스 A '$DB_A_NAME' 존재"
else
  echo "   ❌ 데이터베이스 A '$DB_A_NAME' 없음"
fi

if [ "$DB_B_NAME" != "$DB_A_NAME" ]; then
  DB_B_EXISTS=$(psql_su -tAc "SELECT 1 FROM pg_database WHERE datname='${DB_B_NAME}'" 2>/dev/null || echo "")
  if [ "$DB_B_EXISTS" = "1" ]; then
    echo "   ✅ 데이터베이스 B '$DB_B_NAME' 존재"
  else
    echo "   ❌ 데이터베이스 B '$DB_B_NAME' 없음"
  fi
else
  echo "   ℹ️  B=A (단일 DB 모드)"
fi
echo ""

# 4. 사용자 존재 확인
echo "4. 사용자 존재 확인"
USER_EXISTS=$(psql_su -d "$DB_A_NAME" -tAc "SELECT 1 FROM pg_user WHERE usename='$DB_USER'" 2>/dev/null || echo "")
if [ "$USER_EXISTS" = "1" ]; then
  echo "   ✅ 사용자 '$DB_USER' 존재"
else
  echo "   ❌ 사용자 '$DB_USER' 없음"
fi
echo ""

# 5. 사용자 연결 테스트
echo "5. 사용자 연결 테스트"
if psql_app -d "$DB_A_NAME" -c "SELECT 1;" >/dev/null 2>&1; then
  echo "   ✅ 사용자 '$DB_USER'로 DB A 연결 성공"
else
  echo "   ❌ 사용자 '$DB_USER'로 DB A 연결 실패"
fi

if [ "$DB_B_NAME" != "$DB_A_NAME" ]; then
  if psql_app -d "$DB_B_NAME" -c "SELECT 1;" >/dev/null 2>&1; then
    echo "   ✅ 사용자 '$DB_USER'로 DB B 연결 성공"
  else
    echo "   ❌ 사용자 '$DB_USER'로 DB B 연결 실패"
  fi
fi
echo ""

# 6. 테이블 존재 확인 (PB on A, ImageLog on B)
echo "6. 테이블 존재 확인"
SEND_TABLE=$(psql_app -d "$DB_A_NAME" -tAc "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${SCHEMA_PB}' AND table_name='pb_send';" 2>/dev/null || echo "0")
RECV_TABLE=$(psql_app -d "$DB_A_NAME" -tAc "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${SCHEMA_PB}' AND table_name='pb_recv';" 2>/dev/null || echo "0")

if [ "$SEND_TABLE" = "1" ]; then
  echo "   ✅ 스키마 ${SCHEMA_PB}.pb_send 존재"
else
  echo "   ❌ 스키마 ${SCHEMA_PB}.pb_send 없음"
fi

if [ "$RECV_TABLE" = "1" ]; then
  echo "   ✅ 스키마 ${SCHEMA_PB}.pb_recv 존재"
else
  echo "   ❌ 스키마 ${SCHEMA_PB}.pb_recv 없음"
fi

IMG_TABLE=$(psql_app -d "$DB_B_NAME" -tAc "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${SCHEMA_IMAGELOG}' AND table_name='imagelog';" 2>/dev/null || echo "0")
if [ "$IMG_TABLE" = "1" ]; then
  echo "   ✅ 스키마 ${SCHEMA_IMAGELOG}.imagelog 존재 (DB B)"
else
  echo "   ❌ 스키마 ${SCHEMA_IMAGELOG}.imagelog 없음 (DB B)"
fi

SYS_SAMPLE=$(psql_app -d "$DB_A_NAME" -tAc "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${SCHEMA_SYS}' AND table_name='app_user';" 2>/dev/null || echo "0")
if [ "$SYS_SAMPLE" = "1" ]; then
  echo "   ✅ 스키마 ${SCHEMA_SYS}.app_user 존재 (시스템)"
else
  echo "   ⚠️  스키마 ${SCHEMA_SYS}.app_user 없음 (시스템)"
fi
echo ""

# 7. 테이블 구조 확인
echo "7. 테이블 구조 확인"
if [ "$SEND_TABLE" = "1" ]; then
  COLUMN_COUNT=$(psql_app -d "$DB_A_NAME" -tAc "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='${SCHEMA_PB}' AND table_name='pb_send';" 2>/dev/null || echo "0")
  if [ "${COLUMN_COUNT:-0}" -ge "15" ]; then
    echo "   ✅ pb_send 테이블 컬럼 수: $COLUMN_COUNT (정상)"
  else
    echo "   ⚠️  pb_send 테이블 컬럼 수: $COLUMN_COUNT (예상: 15개 이상)"
  fi
fi
echo ""

# 8. 인덱스 확인
echo "8. 인덱스 확인"
if [ "$SEND_TABLE" = "1" ]; then
  INDEX_COUNT=$(psql_app -d "$DB_A_NAME" -tAc "SELECT COUNT(*) FROM pg_indexes WHERE schemaname='${SCHEMA_PB}' AND tablename='pb_send';" 2>/dev/null || echo "0")
  if [ "${INDEX_COUNT:-0}" -ge "6" ]; then
    echo "   ✅ pb_send 인덱스 수: $INDEX_COUNT (정상)"
  else
    echo "   ⚠️  pb_send 인덱스 수: $INDEX_COUNT (예상: 6개 이상)"
  fi

  INDEX_COUNT_RECV=$(psql_app -d "$DB_A_NAME" -tAc "SELECT COUNT(*) FROM pg_indexes WHERE schemaname='${SCHEMA_PB}' AND tablename='pb_recv';" 2>/dev/null || echo "0")
  if [ "${INDEX_COUNT_RECV:-0}" -ge "6" ]; then
    echo "   ✅ pb_recv 인덱스 수: $INDEX_COUNT_RECV (정상)"
  else
    echo "   ⚠️  pb_recv 인덱스 수: $INDEX_COUNT_RECV (예상: 6개 이상)"
  fi
fi
echo ""

# 9. 데이터 확인
echo "9. 데이터 확인"
if [ "$SEND_TABLE" = "1" ]; then
  SEND_COUNT=$(psql_app -d "$DB_A_NAME" -tAc "SET search_path TO ${SCHEMA_PB}, public; SELECT COUNT(*) FROM pb_send;" 2>/dev/null | tail -1 || echo "0")
  echo "   📊 pb_send 레코드 수: $SEND_COUNT"
  RECV_COUNT=$(psql_app -d "$DB_A_NAME" -tAc "SET search_path TO ${SCHEMA_PB}, public; SELECT COUNT(*) FROM pb_recv;" 2>/dev/null | tail -1 || echo "0")
  echo "   📊 pb_recv 레코드 수: $RECV_COUNT"
fi
echo ""

# 10. 권한 확인
echo "10. 권한 확인"
if [ "$SEND_TABLE" = "1" ]; then
  HAS_PRIVILEGES=$(psql_su -d "$DB_A_NAME" -tAc "SELECT COUNT(*) FROM information_schema.table_privileges WHERE grantee='$DB_USER' AND table_schema='${SCHEMA_PB}' AND table_name IN ('pb_send', 'pb_recv');" 2>/dev/null || echo "0")
  if [ "${HAS_PRIVILEGES:-0}" -ge "2" ]; then
    echo "   ✅ PB 테이블 권한 정상"
  else
    echo "   ⚠️  PB 테이블 권한 확인 필요"
  fi
fi
echo ""

# 11. 상세 테이블 정보
echo "11. 상세 테이블 정보"
if [ "$SEND_TABLE" = "1" ]; then
  echo "   📋 pb_send (스키마 ${SCHEMA_PB}):"
  psql_app -d "$DB_A_NAME" -c "SET search_path TO ${SCHEMA_PB}, public;" -c '\d pb_send' 2>/dev/null | head -20 || true
  echo ""
fi

# 12. 연결 정보
echo "12. 연결 정보"
echo "   DB A: $DB_A_NAME"
echo "   DB B: $DB_B_NAME"
echo "   사용자: $DB_USER"
echo "   호스트: $DB_HOST"
echo "   포트: $DB_PORT"
echo "   search_path 힌트: SYS=${SCHEMA_SYS}, PB=${SCHEMA_PB}, ImageLog(B)=${SCHEMA_IMAGELOG}"
echo ""

echo "=========================================="
echo "점검 완료"
echo "=========================================="
