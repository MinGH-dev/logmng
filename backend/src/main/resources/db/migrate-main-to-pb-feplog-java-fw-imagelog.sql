-- Migrate permission_group_screen: main → pb-feplog and java-fw-imagelog (req 20260318-menu-and-permission-restructure)
-- Idempotent: safe to run multiple times (ON CONFLICT DO NOTHING).
-- For each row with screen_id = 'main', inserts two rows with screen_id = 'pb-feplog' and 'java-fw-imagelog'
-- copying all columns (scope, read, write, approve, decrypt). Does NOT delete main rows by default.
-- To remove main after migration, run (optional, uncomment and execute separately if desired):
--   DELETE FROM permission_group_screen WHERE screen_id = 'main';

INSERT INTO permission_group_screen (permission_group_id, screen_id, scope, read, write, approve, decrypt)
SELECT permission_group_id, 'pb-feplog', scope, read, write, approve, decrypt
FROM permission_group_screen
WHERE screen_id = 'main'
ON CONFLICT (permission_group_id, screen_id) DO NOTHING;

INSERT INTO permission_group_screen (permission_group_id, screen_id, scope, read, write, approve, decrypt)
SELECT permission_group_id, 'java-fw-imagelog', scope, read, write, approve, decrypt
FROM permission_group_screen
WHERE screen_id = 'main'
ON CONFLICT (permission_group_id, screen_id) DO NOTHING;
