-- Normalize legacy screen_id typo: java-fw_imagelog → java-fw-imagelog (req 20260318-permission-group-menu-invalid-screen-id-imagelog).
-- Idempotent: safe to run multiple times.
-- Step 1: Remove legacy rows where canonical row already exists (avoid unique violation).
DELETE FROM permission_group_screen a
WHERE a.screen_id = 'java-fw_imagelog'
  AND EXISTS (
    SELECT 1 FROM permission_group_screen b
    WHERE b.permission_group_id = a.permission_group_id AND b.screen_id = 'java-fw-imagelog'
  );
-- Step 2: Update remaining legacy rows to canonical.
UPDATE permission_group_screen
SET screen_id = 'java-fw-imagelog'
WHERE screen_id = 'java-fw_imagelog';
