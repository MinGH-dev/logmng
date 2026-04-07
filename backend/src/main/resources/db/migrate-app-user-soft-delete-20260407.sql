-- app_user: soft-delete (DBA recommendation; req 20260407-user-management-consistency-delete-reason-activity-audit)
-- Semantics: NULL = active; non-NULL = soft-deleted at that instant (TIMESTAMPTZ).
-- Idempotent; safe for legacy DBs.

ALTER TABLE app_user ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ NULL;

COMMENT ON COLUMN app_user.deleted_at IS 'NULL = active; non-NULL = soft-deleted at this time (UTC).';
