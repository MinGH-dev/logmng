-- Extend permission_group_screen.scope to allow 'team' (idempotent; safe to run multiple times)
-- Requirement: docs/requirements/20250304-team-scope-default-and-approval.md

ALTER TABLE permission_group_screen DROP CONSTRAINT IF EXISTS chk_permission_group_screen_scope;
ALTER TABLE permission_group_screen ADD CONSTRAINT chk_permission_group_screen_scope
  CHECK (scope IS NULL OR scope IN ('self', 'all', 'team'));
