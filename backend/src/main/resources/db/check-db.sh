#!/bin/bash

# PostgreSQL 데이터베이스 상태 점검 스크립트

DB_NAME="logmng"
DB_USER="logmng"
DB_PASSWORD="logmng123"
DB_HOST="localhost"
DB_PORT="5432"

echo "=========================================="
echo "PostgreSQL 데이터베이스 상태 점검"
echo "=========================================="
echo ""

# 1. PostgreSQL 서비스 상태 확인
echo "1. PostgreSQL 서비스 상태 확인"
if brew services list | grep -q "postgresql@16.*started"; then
    echo "   ✅ PostgreSQL 16 서비스 실행 중"
else
    echo "   ❌ PostgreSQL 16 서비스 미실행"
    echo "   💡 실행 명령: brew services start postgresql@16"
fi
echo ""

# 2. PostgreSQL 연결 가능 여부 확인
echo "2. PostgreSQL 연결 가능 여부"
if pg_isready -h $DB_HOST -p $DB_PORT > /dev/null 2>&1; then
    echo "   ✅ PostgreSQL 서버 연결 가능 (포트: $DB_PORT)"
else
    echo "   ❌ PostgreSQL 서버 연결 불가"
    echo "   💡 서비스 시작 후 잠시 대기 필요"
fi
echo ""

# 3. 데이터베이스 존재 확인
echo "3. 데이터베이스 존재 확인"
DB_EXISTS=$(PGPASSWORD=postgres psql -U postgres -h $DB_HOST -p $DB_PORT -tAc "SELECT 1 FROM pg_database WHERE datname='$DB_NAME'" 2>/dev/null)
if [ "$DB_EXISTS" = "1" ]; then
    echo "   ✅ 데이터베이스 '$DB_NAME' 존재"
else
    echo "   ❌ 데이터베이스 '$DB_NAME' 없음"
    echo "   💡 생성 명령: CREATE DATABASE $DB_NAME;"
fi
echo ""

# 4. 사용자 존재 확인
echo "4. 사용자 존재 확인"
USER_EXISTS=$(PGPASSWORD=postgres psql -U postgres -h $DB_HOST -p $DB_PORT -d $DB_NAME -tAc "SELECT 1 FROM pg_user WHERE usename='$DB_USER'" 2>/dev/null)
if [ "$USER_EXISTS" = "1" ]; then
    echo "   ✅ 사용자 '$DB_USER' 존재"
else
    echo "   ❌ 사용자 '$DB_USER' 없음"
    echo "   💡 생성 명령: CREATE USER $DB_USER WITH PASSWORD '$DB_PASSWORD';"
fi
echo ""

# 5. 사용자 연결 테스트
echo "5. 사용자 연결 테스트"
if PGPASSWORD=$DB_PASSWORD psql -U $DB_USER -h $DB_HOST -p $DB_PORT -d $DB_NAME -c "SELECT 1;" > /dev/null 2>&1; then
    echo "   ✅ 사용자 '$DB_USER'로 연결 성공"
else
    echo "   ❌ 사용자 '$DB_USER'로 연결 실패"
fi
echo ""

# 6. 테이블 존재 확인
echo "6. 테이블 존재 확인"
if PGPASSWORD=$DB_PASSWORD psql -U $DB_USER -h $DB_HOST -p $DB_PORT -d $DB_NAME -c "\dt" > /dev/null 2>&1; then
    SEND_TABLE=$(PGPASSWORD=$DB_PASSWORD psql -U $DB_USER -h $DB_HOST -p $DB_PORT -d $DB_NAME -tAc "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='public' AND table_name='pb_send';" 2>/dev/null)
    RECV_TABLE=$(PGPASSWORD=$DB_PASSWORD psql -U $DB_USER -h $DB_HOST -p $DB_PORT -d $DB_NAME -tAc "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='public' AND table_name='pb_recv';" 2>/dev/null)
    
    if [ "$SEND_TABLE" = "1" ]; then
        echo "   ✅ 테이블 'pb_send' 존재"
    else
        echo "   ❌ 테이블 'pb_send' 없음"
    fi
    
    if [ "$RECV_TABLE" = "1" ]; then
        echo "   ✅ 테이블 'pb_recv' 존재"
    else
        echo "   ❌ 테이블 'pb_recv' 없음"
    fi
else
    echo "   ❌ 테이블 조회 실패"
fi
echo ""

# 7. 테이블 구조 확인
echo "7. 테이블 구조 확인"
if [ "$SEND_TABLE" = "1" ]; then
    COLUMN_COUNT=$(PGPASSWORD=$DB_PASSWORD psql -U $DB_USER -h $DB_HOST -p $DB_PORT -d $DB_NAME -tAc "SELECT COUNT(*) FROM information_schema.columns WHERE table_name='pb_send';" 2>/dev/null)
    if [ "$COLUMN_COUNT" -ge "15" ]; then
        echo "   ✅ pb_send 테이블 컬럼 수: $COLUMN_COUNT (정상)"
    else
        echo "   ⚠️  pb_send 테이블 컬럼 수: $COLUMN_COUNT (예상: 15개 이상)"
    fi
fi
echo ""

# 8. 인덱스 확인
echo "8. 인덱스 확인"
if [ "$SEND_TABLE" = "1" ]; then
    INDEX_COUNT=$(PGPASSWORD=$DB_PASSWORD psql -U $DB_USER -h $DB_HOST -p $DB_PORT -d $DB_NAME -tAc "SELECT COUNT(*) FROM pg_indexes WHERE tablename='pb_send';" 2>/dev/null)
    if [ "$INDEX_COUNT" -ge "6" ]; then
        echo "   ✅ pb_send 인덱스 수: $INDEX_COUNT (정상)"
    else
        echo "   ⚠️  pb_send 인덱스 수: $INDEX_COUNT (예상: 6개 이상)"
    fi
    
    INDEX_COUNT_RECV=$(PGPASSWORD=$DB_PASSWORD psql -U $DB_USER -h $DB_HOST -p $DB_PORT -d $DB_NAME -tAc "SELECT COUNT(*) FROM pg_indexes WHERE tablename='pb_recv';" 2>/dev/null)
    if [ "$INDEX_COUNT_RECV" -ge "6" ]; then
        echo "   ✅ pb_recv 인덱스 수: $INDEX_COUNT_RECV (정상)"
    else
        echo "   ⚠️  pb_recv 인덱스 수: $INDEX_COUNT_RECV (예상: 6개 이상)"
    fi
fi
echo ""

# 9. 데이터 확인
echo "9. 데이터 확인"
if [ "$SEND_TABLE" = "1" ]; then
    SEND_COUNT=$(PGPASSWORD=$DB_PASSWORD psql -U $DB_USER -h $DB_HOST -p $DB_PORT -d $DB_NAME -tAc "SELECT COUNT(*) FROM pb_send;" 2>/dev/null)
    echo "   📊 pb_send 레코드 수: $SEND_COUNT"
    
    RECV_COUNT=$(PGPASSWORD=$DB_PASSWORD psql -U $DB_USER -h $DB_HOST -p $DB_PORT -d $DB_NAME -tAc "SELECT COUNT(*) FROM pb_recv;" 2>/dev/null)
    echo "   📊 pb_recv 레코드 수: $RECV_COUNT"
fi
echo ""

# 10. 권한 확인
echo "10. 권한 확인"
if [ "$DB_EXISTS" = "1" ] && [ "$USER_EXISTS" = "1" ]; then
    HAS_PRIVILEGES=$(PGPASSWORD=postgres psql -U postgres -h $DB_HOST -p $DB_PORT -d $DB_NAME -tAc "SELECT COUNT(*) FROM information_schema.table_privileges WHERE grantee='$DB_USER' AND table_name IN ('pb_send', 'pb_recv');" 2>/dev/null)
    if [ "$HAS_PRIVILEGES" -ge "2" ]; then
        echo "   ✅ 테이블 권한 정상"
    else
        echo "   ⚠️  테이블 권한 확인 필요"
    fi
fi
echo ""

# 11. 상세 테이블 정보
echo "11. 상세 테이블 정보"
if [ "$SEND_TABLE" = "1" ]; then
    echo "   📋 pb_send 테이블 컬럼:"
    PGPASSWORD=$DB_PASSWORD psql -U $DB_USER -h $DB_HOST -p $DB_PORT -d $DB_NAME -c "\d pb_send" 2>/dev/null | head -20
    echo ""
fi

# 12. 연결 문자열 확인
echo "12. 연결 정보"
echo "   데이터베이스: $DB_NAME"
echo "   사용자: $DB_USER"
echo "   호스트: $DB_HOST"
echo "   포트: $DB_PORT"
echo "   연결 문자열: postgresql://$DB_USER:$DB_PASSWORD@$DB_HOST:$DB_PORT/$DB_NAME"
echo ""

echo "=========================================="
echo "점검 완료"
echo "=========================================="





