-- Migration: screen_display_label — admin-configurable menu/screen display labels (label_user, optional label_admin, audit).
-- Requirement: docs/requirements/20260406-menu-display-names-admin.md (id: 20260406-menu-display-names-admin).
-- Spec: specs/menu-display-labels.spec.yaml
--
-- Prerequisites: app_user, update_updated_at_column() (schema_sys.sql / prior migrations).
-- Idempotent: CREATE TABLE IF NOT EXISTS; CREATE OR REPLACE TRIGGER.
--
-- Apply (example; use superuser or app user with DDL — match setup.sh / check-db.sh defaults):
--   PGPASSWORD='<redacted>' psql -U logmng -h localhost -p 5432 -d logmng -v ON_ERROR_STOP=1 -f backend/src/main/resources/db/migrate-screen-display-labels-20260406.sql
-- If `postgres` role does not exist (some local installs), use -U logmng as above.

CREATE TABLE IF NOT EXISTS screen_display_label (
    screen_id   VARCHAR(128) PRIMARY KEY,
    label_user  VARCHAR(256) NOT NULL,
    label_admin VARCHAR(256) NULL,
    updated_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by  BIGINT NULL REFERENCES app_user(id) ON DELETE SET NULL
);

CREATE OR REPLACE TRIGGER update_screen_display_label_updated_at
    BEFORE UPDATE ON screen_display_label
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();
