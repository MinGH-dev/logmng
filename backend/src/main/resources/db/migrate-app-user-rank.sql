-- Add rank column to app_user (idempotent; safe to run multiple times)
-- Requirement: docs/requirements/20250227-remove-department-approver-screen-user-mgmt-improvements.md

ALTER TABLE app_user ADD COLUMN IF NOT EXISTS rank VARCHAR(50) NULL;
