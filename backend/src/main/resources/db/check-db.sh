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

# 6a. row_status on approval / decryption-allowed (req 20260320; avoids PG 42703 at runtime)
echo "6a. 승인 스냅샷·복호화 허용 row_status 컬럼 (SCHEMA_SYS=${SCHEMA_SYS})"
AR_TABLE=$(psql_app -d "$DB_A_NAME" -tAc "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${SCHEMA_SYS}' AND table_name='search_history_approved_row';" 2>/dev/null || echo "0")
AR_ROW_STATUS=$(psql_app -d "$DB_A_NAME" -tAc "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='${SCHEMA_SYS}' AND table_name='search_history_approved_row' AND column_name='row_status';" 2>/dev/null || echo "0")
UDA_ROW_STATUS=$(psql_app -d "$DB_A_NAME" -tAc "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='${SCHEMA_SYS}' AND table_name='user_decryption_allowed' AND column_name='row_status';" 2>/dev/null || echo "0")
if [ "$AR_TABLE" = "1" ] && [ "$AR_ROW_STATUS" = "1" ] && [ "$UDA_ROW_STATUS" = "1" ]; then
  echo "   ✅ search_history_approved_row.row_status 및 user_decryption_allowed.row_status 존재"
elif [ "$AR_TABLE" = "1" ]; then
  echo "   ❌ row_status 누락 가능 — DB A에 migrate-sys-decryption-composite-pk-20260320.sql 적용 필요 (setup.sh 4g 또는 DB_SETUP_GUIDE.md § PostgreSQL 42703)"
else
  echo "   ℹ️  search_history_approved_row 없음 — 시스템 스키마 미적용 또는 다른 스키마 대상일 수 있음"
fi
echo ""

# 6b. permission_group_screen columns required by backend (req 20260320-permission-group-screen-entry-error-migration-check)
echo "6b. permission_group_screen 필수 컬럼 (SCHEMA_SYS=${SCHEMA_SYS})"
PGS_TABLE=$(psql_app -d "$DB_A_NAME" -tAc "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${SCHEMA_SYS}' AND table_name='permission_group_screen';" 2>/dev/null || echo "0")
if [ "$PGS_TABLE" = "1" ]; then
  C_SCOPE=$(psql_app -d "$DB_A_NAME" -tAc "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='${SCHEMA_SYS}' AND table_name='permission_group_screen' AND column_name='scope';" 2>/dev/null || echo "0")
  C_READ=$(psql_app -d "$DB_A_NAME" -tAc "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='${SCHEMA_SYS}' AND table_name='permission_group_screen' AND column_name='read';" 2>/dev/null || echo "0")
  C_WRITE=$(psql_app -d "$DB_A_NAME" -tAc "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='${SCHEMA_SYS}' AND table_name='permission_group_screen' AND column_name='write';" 2>/dev/null || echo "0")
  C_APPROVE=$(psql_app -d "$DB_A_NAME" -tAc "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='${SCHEMA_SYS}' AND table_name='permission_group_screen' AND column_name='approve';" 2>/dev/null || echo "0")
  C_DECRYPT=$(psql_app -d "$DB_A_NAME" -tAc "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='${SCHEMA_SYS}' AND table_name='permission_group_screen' AND column_name='decrypt';" 2>/dev/null || echo "0")
  if [ "$C_SCOPE" = "1" ] && [ "$C_READ" = "1" ] && [ "$C_WRITE" = "1" ] && [ "$C_APPROVE" = "1" ] && [ "$C_DECRYPT" = "1" ]; then
    echo "   ✅ permission_group_screen: scope, read, write, approve, decrypt 존재"
  else
    echo "   ❌ permission_group_screen 필수 컬럼 누락 — setup.sh 4h 재실행 또는 DB A에서 아래 순서로 수동 적용(search_path: SCHEMA_SYS, SCHEMA_PB, public 또는 환경에 맞게):"
    echo "      migrate-permission-group-screen-scope.sql → migrate-permission-group-screen-functions.sql → migrate-permission-group-screen-decrypt.sql → migrate-permission-group-screen-scope-team.sql"
    echo "      (요구사항: docs/requirements/20260320-permission-group-screen-entry-error-migration-check.md)"
  fi
else
  echo "   ℹ️  ${SCHEMA_SYS}.permission_group_screen 없음 — 시스템 스키마 미적용 또는 다른 스키마 대상일 수 있음"
fi
echo ""

# 6c. screen_display_label (req 20260406-menu-display-names-admin; PUT /api/screen-display-labels; 20260407 parent/order)
echo "6c. screen_display_label (메뉴 표시 라벨, SCHEMA_SYS=${SCHEMA_SYS})"
SDL_TABLE=$(psql_app -d "$DB_A_NAME" -tAc "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${SCHEMA_SYS}' AND table_name='screen_display_label';" 2>/dev/null || echo "0")
if [ "$SDL_TABLE" = "1" ]; then
  SDL_PG=$(psql_app -d "$DB_A_NAME" -tAc "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='${SCHEMA_SYS}' AND table_name='screen_display_label' AND column_name='parent_group_id';" 2>/dev/null || echo "0")
  SDL_SO=$(psql_app -d "$DB_A_NAME" -tAc "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='${SCHEMA_SYS}' AND table_name='screen_display_label' AND column_name='sort_order';" 2>/dev/null || echo "0")
  if [ "$SDL_PG" = "1" ] && [ "$SDL_SO" = "1" ]; then
    echo "   ✅ ${SCHEMA_SYS}.screen_display_label 존재 (parent_group_id, sort_order 포함)"
  else
    echo "   ⚠️  ${SCHEMA_SYS}.screen_display_label 존재하나 parent_group_id/sort_order 누락 가능 — DB A에 migrate-screen-display-label-parent-order-20260407.sql 적용 (요구사항: docs/requirements/20260407-screen-menu-parent-order.md)"
  fi
else
  echo "   ❌ ${SCHEMA_SYS}.screen_display_label 없음 — DB A에 migrate-screen-display-labels-20260406.sql 적용 필요 (요구사항: docs/requirements/20260406-menu-display-names-admin.md)"
fi
echo ""

# 6d. app_user_permission_group.user_id must reference app_user.username (FK); not legacy id::text (req 20260316 / 20260407)
echo "6d. app_user_permission_group.user_id ⊆ app_user.username (SCHEMA_SYS=${SCHEMA_SYS})"
AUPG_TABLE=$(psql_app -d "$DB_A_NAME" -tAc "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${SCHEMA_SYS}' AND table_name='app_user_permission_group';" 2>/dev/null || echo "0")
if [ "$AUPG_TABLE" = "1" ]; then
  # Optional SQL: SELECT * FROM app_user_permission_group aupg WHERE NOT EXISTS (SELECT 1 FROM app_user u WHERE u.username = aupg.user_id);
  AUPG_ORPHAN=$(psql_app -d "$DB_A_NAME" -tAc "SET search_path TO ${SCHEMA_SYS}, ${SCHEMA_PB}, public; SELECT COUNT(*) FROM app_user_permission_group aupg WHERE NOT EXISTS (SELECT 1 FROM app_user u WHERE u.username = aupg.user_id);" 2>/dev/null | tail -1 || echo "")
  if [ "${AUPG_ORPHAN:-0}" = "0" ]; then
    echo "   ✅ app_user_permission_group.user_id 값이 모두 app_user.username에 존재 (FK 정합)"
  else
    echo "   ❌ app_user_permission_group에 username에 없는 user_id ${AUPG_ORPHAN}건 — migrate-app-user-permission-group-user-id-to-username-20260407.sql 적용 검토 (setup.sh 6a)"
  fi
else
  echo "   ℹ️  ${SCHEMA_SYS}.app_user_permission_group 없음"
fi
echo ""

# 6e. External replica ext_* + app role SELECT-only (req 20260407-external-dept-employee-ad-login; TC-D01 / TC-D02)
echo "6e. 외부 복제 ext_department / ext_employee 및 앱 역할 SELECT-only (${SCHEMA_SYS})"
EXT_T=$(psql_app -d "$DB_A_NAME" -tAc "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${SCHEMA_SYS}' AND table_name IN ('ext_department','ext_employee','app_user_external_identity');" 2>/dev/null || echo "0")
if [ "${EXT_T:-0}" = "3" ]; then
  echo "   ✅ ext_department, ext_employee, app_user_external_identity 존재"
  EXT_ROWS=$(psql_app -d "$DB_A_NAME" -tAc "SET search_path TO ${SCHEMA_SYS}, public; SELECT COUNT(*) FROM ext_employee;" 2>/dev/null | tail -1 || echo "0")
  if [ "${EXT_ROWS:-0}" -ge "1" ]; then
    echo "   ✅ ext_employee 샘플 행 조회 가능 (TC-D01 SELECT)"
  else
    echo "   ⚠️  ext_employee 행 0건 — init-data.sql 시드 확인"
  fi
  HR_EMP_OK=$(psql_app -d "$DB_A_NAME" -tAc "SET search_path TO ${SCHEMA_SYS}, public; SELECT COUNT(*) FROM ext_employee e WHERE e.source_system='HR_SAMPLE' AND ((e.external_employee_id = 'E-10001' AND e.employee_number = '20261001') OR (e.external_employee_id = 'E-10002' AND e.employee_number = '20261002') OR (e.external_employee_id = 'E-10003' AND e.employee_number = '20261003') OR (e.external_employee_id = 'E-UNPROV-1' AND e.employee_number = '20261999'));" 2>/dev/null | tail -1 || echo "0")
  if [ "${HR_EMP_OK:-0}" = "4" ]; then
    echo "   ✅ HR_SAMPLE ext_employee employee_number 시드 형식(8자리 2026xxxx) 정합"
  else
    echo "   ⚠️  HR_SAMPLE ext_employee employee_number 기대 4건(20261001/02/03, 20261999), 실제 매칭 ${HR_EMP_OK:-0} — init-data.sql 또는 migrate-hr-sample-employee-number-userid-format-20260407.sql 검토"
  fi
  if psql_app -d "$DB_A_NAME" -v ON_ERROR_STOP=1 -c "SET search_path TO ${SCHEMA_SYS}, public; INSERT INTO ext_employee (source_system, external_employee_id, imported_at) VALUES ('__chk__','__deny-ins__', CURRENT_TIMESTAMP);" >/dev/null 2>&1; then
    echo "   ❌ ext_employee 에 앱 역할(${DB_USER}) INSERT 허용됨 — SELECT-only 위반 (setup.sh 4b-ext)"
    psql_app -d "$DB_A_NAME" -c "SET search_path TO ${SCHEMA_SYS}, public; DELETE FROM ext_employee WHERE source_system='__chk__' AND external_employee_id='__deny-ins__';" >/dev/null 2>&1 || true
  else
    echo "   ✅ ext_employee 앱 역할 INSERT 거부 (TC-D02)"
  fi
else
  echo "   ℹ️  외부 복제 테이블 미생성 — migrate-external-identity-tables-20260407.sql / schema_sys.sql 적용 필요"
fi
echo ""

# 6f. department_org_link: replica org dept key → department.code (provisioning / hierarchy; 20260407)
echo "6f. department_org_link (복제 부서키 → department.code, SCHEMA_SYS=${SCHEMA_SYS})"
DOL_TABLE=$(psql_app -d "$DB_A_NAME" -tAc "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${SCHEMA_SYS}' AND table_name='department_org_link';" 2>/dev/null || echo "0")
if [ "${DOL_TABLE:-0}" = "1" ]; then
  echo "   ✅ ${SCHEMA_SYS}.department_org_link 존재"
  DOL_IDX=$(psql_app -d "$DB_A_NAME" -tAc "SELECT COUNT(*) FROM pg_indexes WHERE schemaname='${SCHEMA_SYS}' AND tablename='department_org_link' AND indexname='idx_department_org_link_department';" 2>/dev/null || echo "0")
  if [ "${DOL_IDX:-0}" = "1" ]; then
    echo "   ✅ idx_department_org_link_department 존재 (역방향 조회)"
  else
    echo "   ⚠️  idx_department_org_link_department 없음 — migrate-department-org-link-20260407.sql 확인"
  fi
  DOL_SEL=$(psql_app -d "$DB_A_NAME" -tAc "SET search_path TO ${SCHEMA_SYS}, public; SELECT COUNT(*) FROM department_org_link WHERE source_system='HR_SAMPLE' AND external_department_id IN ('D-SALES-001','D-RD-001');" 2>/dev/null | tail -1 || echo "0")
  if [ "${DOL_SEL:-0}" = "2" ]; then
    echo "   ✅ department_org_link HR_SAMPLE 시드 2건 처리 (D-SALES-001 / D-RD-001)"
  else
    echo "   ⚠️  department_org_link 시드 기대 2건, 실제 ${DOL_SEL:-0} — init-data.sql 또는 마이그레이션 재실행 검토"
  fi
else
  echo "   ❌ ${SCHEMA_SYS}.department_org_link 없음 — migrate-department-org-link-20260407.sql 적용 필요"
fi
echo ""

# 6g. app_user.employee_number (HR 사번; 프로비저닝 ext_employee 동기화, 20260407)
echo "6g. app_user.employee_number 및 조회 인덱스 (SCHEMA_SYS=${SCHEMA_SYS})"
AU_TABLE=$(psql_app -d "$DB_A_NAME" -tAc "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${SCHEMA_SYS}' AND table_name='app_user';" 2>/dev/null || echo "0")
if [ "${AU_TABLE:-0}" = "1" ]; then
  AU_EMPNUM=$(psql_app -d "$DB_A_NAME" -tAc "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='${SCHEMA_SYS}' AND table_name='app_user' AND column_name='employee_number';" 2>/dev/null || echo "0")
  AU_IDX=$(psql_app -d "$DB_A_NAME" -tAc "SELECT COUNT(*) FROM pg_indexes WHERE schemaname='${SCHEMA_SYS}' AND tablename='app_user' AND indexname='idx_app_user_employee_number';" 2>/dev/null || echo "0")
  if [ "${AU_EMPNUM:-0}" = "1" ] && [ "${AU_IDX:-0}" = "1" ]; then
    echo "   ✅ app_user.employee_number 컬럼 및 idx_app_user_employee_number 존재"
  elif [ "${AU_EMPNUM:-0}" = "1" ]; then
    echo "   ⚠️  app_user.employee_number 있으나 idx_app_user_employee_number 없음 — migrate-app-user-employee-number-20260407.sql 재실행 검토"
  else
    echo "   ❌ app_user.employee_number 없음 — migrate-app-user-employee-number-20260407.sql 적용 필요 (setup.sh 4-ext-1b)"
  fi
else
  echo "   ℹ️  ${SCHEMA_SYS}.app_user 없음"
fi

# 6h. app_user.deleted_at (soft delete; DBA·req 20260407)
if [ "${AU_TABLE:-0}" = "1" ]; then
  echo "6h. app_user.deleted_at (소프트 삭제, SCHEMA_SYS=${SCHEMA_SYS})"
  AU_DEL=$(psql_app -d "$DB_A_NAME" -tAc "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='${SCHEMA_SYS}' AND table_name='app_user' AND column_name='deleted_at';" 2>/dev/null || echo "0")
  if [ "${AU_DEL:-0}" = "1" ]; then
    echo "   ✅ app_user.deleted_at 컬럼 존재"
  else
    echo "   ❌ app_user.deleted_at 없음 — migrate-app-user-soft-delete-20260407.sql 적용 필요 (setup.sh 4-ext-1c)"
  fi
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
