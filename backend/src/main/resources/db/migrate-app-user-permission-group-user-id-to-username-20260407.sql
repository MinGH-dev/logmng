-- Normalize app_user_permission_group.user_id: legacy rows may store app_user.id::text
-- (e.g. '20260004') instead of app_user.username. Schema FK fk_app_user_permission_group_user
-- references app_user(username); username-only app queries (e.g. current permission group lookup,
-- permission-group assign/unassign audit) require consistent username keys.
--
-- Related requirements:
--   docs/requirements/20260316-user-id-numeric-userid-naming.md
--   docs/requirements/20260316-search-history-user-id-query-and-naming.md (user_id naming)
--   docs/requirements/20260407-permission-group-assign-unassign-audit-before-after.md (audit / user_id)
--
-- Run after migrate-app-user-id-2026.sql (and schema_sys / init-data) so id::text matches current app_user.id.
-- Idempotent: safe to re-run; uses only UPDATE/DELETE, no DDL weakening.
--
-- Duplicate-key rule (UNIQUE(user_id) on app_user_permission_group):
--   If both a numeric-id row (user_id = u.id::text) and a canonical row (user_id = u.username) exist
--   for the same app_user, the numeric-key row is removed. The username-key row is authoritative;
--   dropping the duplicate avoids UNIQUE violation and matches "one row per user" intent (req 20250304).
--
-- Manual prod: apply with same search_path as setup.sh (SCHEMA_SYS, SCHEMA_PB, public), backup first.

-- 1) Drop legacy numeric-key rows when a canonical username row already exists for that user.
DELETE FROM app_user_permission_group aupg
USING app_user u
WHERE aupg.user_id = u.id::text
  AND aupg.user_id IS DISTINCT FROM u.username
  AND EXISTS (
    SELECT 1
    FROM app_user_permission_group aupg2
    WHERE aupg2.user_id = u.username
  );

-- 2) Remap remaining rows: user_id = id::text → username (must satisfy FK to app_user(username)).
UPDATE app_user_permission_group aupg
SET user_id = u.username
FROM app_user u
WHERE aupg.user_id = u.id::text
  AND aupg.user_id IS DISTINCT FROM u.username;
