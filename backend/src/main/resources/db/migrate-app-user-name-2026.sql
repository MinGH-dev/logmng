-- Add display name column to app_user (idempotent; safe to run multiple times)
-- Requirement: docs/requirements/20260316-login-id-user-name-display.md
-- Backend uses app_user.name for display name; fallback to username when null.

ALTER TABLE app_user ADD COLUMN IF NOT EXISTS name VARCHAR(200) NULL;
