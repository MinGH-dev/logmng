-- Add is_system_admin column to app_user (idempotent; safe to run multiple times)
-- Requirement: docs/requirements/20250303-permission-group-delete-system-admin-protection.md

ALTER TABLE app_user ADD COLUMN IF NOT EXISTS is_system_admin BOOLEAN NOT NULL DEFAULT false;

-- Set admin user as system administrator (or first ADMIN if admin does not exist)
UPDATE app_user
SET is_system_admin = true
WHERE username = 'admin'
   OR (id = (SELECT id FROM app_user WHERE role = 'ADMIN' ORDER BY id LIMIT 1)
       AND NOT EXISTS (SELECT 1 FROM app_user WHERE username = 'admin'));
