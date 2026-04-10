-- Default read scope for User Management v2 (req 20260409-user-management-v2-read-scope).
-- Idempotent: only updates rows where scope IS NULL.
UPDATE permission_group_screen
SET scope = 'team'
WHERE screen_id = 'user-management-v2'
  AND (scope IS NULL OR trim(scope) = '');
