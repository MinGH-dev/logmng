-- =============================================================================
-- Air-gap / 폐쇄망 전용: 시스템 DB(SCHEMA_SYS) 복합 PK + row_status 마이그레이션만 수행
-- =============================================================================
-- 기존 DB에 테이블이 옛 DDL로만 있을 때 사용합니다. 신규 설치는 schema_sys.sql이
-- 이미 올바른 정의를 포함하므로 이 파일은 생략 가능합니다.
--
-- 실행 전 (스키마가 public이 아니면 search_path를 맞춤):
--   psql -v ON_ERROR_STOP=1 -c "SET search_path TO public, public;" -f airgap-only-20260320-sys-decryption-composite-pk.sql
--
-- 상세: migrate-sys-decryption-composite-pk-20260320.sql 과 동일 본문 (단일 파일 배포용).
-- Req: docs/requirements/20260320-imagelog-guid-status-composite-key.md
-- =============================================================================

ALTER TABLE search_history_approved_row
    ADD COLUMN IF NOT EXISTS row_status VARCHAR(256) NOT NULL DEFAULT '';

ALTER TABLE search_history_approved_row
    DROP CONSTRAINT IF EXISTS search_history_approved_row_pkey;

ALTER TABLE search_history_approved_row
    ADD PRIMARY KEY (search_history_id, log_type, row_id, row_status);

ALTER TABLE user_decryption_allowed
    ADD COLUMN IF NOT EXISTS row_status VARCHAR(256) NOT NULL DEFAULT '';

ALTER TABLE user_decryption_allowed
    DROP CONSTRAINT IF EXISTS user_decryption_allowed_pkey;

ALTER TABLE user_decryption_allowed
    ADD PRIMARY KEY (user_id, screen, guid, row_status);
