-- Migration: user_activity_access_audit — append-only access audit (privileged reveal / sensitive activity log detail).
-- Requirement: docs/requirements/20260330-audit-evidence-activity-log-conservative.md
-- Contract: docs/contract.md appendix AAE-01 (dedicated table); specs/activity-log-audit-evidence.spec.yaml
--
-- FK choices (documented):
--   target_activity_log_id → user_activity_log(id) ON DELETE SET NULL
--     Retain audit rows when a target activity log row is removed (e.g. retention/purge); cleared reference documents
--     that the target row no longer exists. Alternative: NOT NULL + ON DELETE RESTRICT blocks deleting referenced log rows.
--   accessor_user_id → app_user(id) ON DELETE RESTRICT
--     Keeps a stable "who" for each row; deleting an app_user that is referenced by audit rows is rejected unless
--     those rows are removed first. Alternative: NULLable + ON DELETE SET NULL if user lifecycle deletion must proceed
--     without touching audit rows first.
--
-- Idempotent: CREATE TABLE IF NOT EXISTS; CREATE INDEX IF NOT EXISTS.
-- Apply (example; match setup.sh search_path — DB A, SCHEMA_SYS first in SP_APP):
--   psql -U logmng -h localhost -p 5432 -d logmng -v ON_ERROR_STOP=1 \
--     -c "SET search_path TO logmng_sys, logmng_pb, public;" \
--     -f backend/src/main/resources/db/migrate-user-activity-access-audit-20260406.sql

CREATE TABLE IF NOT EXISTS user_activity_access_audit (
    id BIGSERIAL PRIMARY KEY,
    accessor_user_id BIGINT NOT NULL REFERENCES app_user(id) ON DELETE RESTRICT,
    target_activity_log_id BIGINT NULL REFERENCES user_activity_log(id) ON DELETE SET NULL,
    access_type VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ip_address VARCHAR(45) NULL,
    user_agent TEXT NULL
);

CREATE INDEX IF NOT EXISTS idx_user_activity_access_audit_created_at ON user_activity_access_audit(created_at);
CREATE INDEX IF NOT EXISTS idx_user_activity_access_audit_accessor_created ON user_activity_access_audit(accessor_user_id, created_at);
CREATE INDEX IF NOT EXISTS idx_user_activity_access_audit_target_log ON user_activity_access_audit(target_activity_log_id);

GRANT ALL PRIVILEGES ON TABLE user_activity_access_audit TO logmng;
GRANT ALL PRIVILEGES ON SEQUENCE user_activity_access_audit_id_seq TO logmng;
