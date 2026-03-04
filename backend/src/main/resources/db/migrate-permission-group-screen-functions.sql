-- Add read, write, approve columns to permission_group_screen (idempotent; safe to run multiple times)
-- Requirement: docs/requirements/20250303-screen-function-checkbox-selection.md
-- NULL = use existing derivation. No DEFAULT to preserve backward compatibility.

ALTER TABLE permission_group_screen ADD COLUMN IF NOT EXISTS read BOOLEAN;
ALTER TABLE permission_group_screen ADD COLUMN IF NOT EXISTS write BOOLEAN;
ALTER TABLE permission_group_screen ADD COLUMN IF NOT EXISTS approve BOOLEAN;
