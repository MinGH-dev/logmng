-- Add scope column to permission_group_screen (idempotent; safe to run multiple times)
-- Requirement: docs/requirements/20250303-activity-statistics-self-only-scope.md
-- DBA design: § DBA 검토. NULL = 'self'; values 'self'|'all'.

ALTER TABLE permission_group_screen ADD COLUMN IF NOT EXISTS scope VARCHAR(10) DEFAULT NULL;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_permission_group_screen_scope') THEN
    ALTER TABLE permission_group_screen ADD CONSTRAINT chk_permission_group_screen_scope
      CHECK (scope IS NULL OR scope IN ('self', 'all'));
  END IF;
END $$;
