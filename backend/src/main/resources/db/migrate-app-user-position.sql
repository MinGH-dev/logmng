-- Add position column to app_user (idempotent; safe to run multiple times)
-- Requirement: docs/requirements/20250227-department-approver-position.md

ALTER TABLE app_user ADD COLUMN IF NOT EXISTS position VARCHAR(50) NULL;
