-- Add decrypt column to permission_group_screen (main screen only; req 20260306)
-- Idempotent; safe to run multiple times.
ALTER TABLE permission_group_screen ADD COLUMN IF NOT EXISTS decrypt BOOLEAN;
