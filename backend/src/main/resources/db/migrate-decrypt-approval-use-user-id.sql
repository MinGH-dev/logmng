-- Migration: Use numeric app_user.id in approval flow (decrypt_approver.app_user_id, search_history.approved_by_user_id).
-- Req: docs/requirements/20260316-decrypt-approval-use-user-id-everywhere.md
-- Idempotent: ADD COLUMN IF NOT EXISTS, FK if not exists, then backfill. Safe to run multiple times.
-- Run after schema or on existing DB: psql -U logmng -h localhost -p 5432 -d logmng -f migrate-decrypt-approval-use-user-id.sql

-- 1) decrypt_approver: add app_user_id, FK, backfill from user_id (username)
ALTER TABLE decrypt_approver ADD COLUMN IF NOT EXISTS app_user_id BIGINT NULL;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint c
    JOIN pg_attribute a ON a.attnum = ANY(c.conkey) AND a.attrelid = c.conrelid
    WHERE c.conrelid = 'decrypt_approver'::regclass AND c.contype = 'f' AND a.attname = 'app_user_id'
  ) THEN
    ALTER TABLE decrypt_approver ADD CONSTRAINT fk_decrypt_approver_app_user FOREIGN KEY (app_user_id) REFERENCES app_user(id);
  END IF;
END $$;

UPDATE decrypt_approver da
SET app_user_id = u.id
FROM app_user u
WHERE u.username = da.user_id AND da.app_user_id IS NULL;

CREATE INDEX IF NOT EXISTS idx_decrypt_approver_app_user ON decrypt_approver(app_user_id);

-- 2) search_history: add approved_by_user_id, FK (only if column has no FK yet; schema may have added REFERENCES inline), backfill from approved_by (username)
ALTER TABLE search_history ADD COLUMN IF NOT EXISTS approved_by_user_id BIGINT NULL;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint c
    JOIN pg_attribute a ON a.attnum = ANY(c.conkey) AND a.attrelid = c.conrelid
    WHERE c.conrelid = 'search_history'::regclass AND c.contype = 'f' AND a.attname = 'approved_by_user_id'
  ) THEN
    ALTER TABLE search_history ADD CONSTRAINT fk_search_history_approved_by_app_user FOREIGN KEY (approved_by_user_id) REFERENCES app_user(id);
  END IF;
END $$;

UPDATE search_history sh
SET approved_by_user_id = u.id
FROM app_user u
WHERE u.username = sh.approved_by AND sh.approved_by IS NOT NULL AND sh.approved_by_user_id IS NULL;
