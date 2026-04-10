-- Migration: screen_display_label — parent sidebar group + leaf sort order (presentation tree).
-- Requirement: docs/requirements/20260407-screen-menu-parent-order.md (id: 20260407-screen-menu-parent-order).
-- Related: docs/requirements/20260406-menu-display-names-admin.md, specs/menu-display-labels.spec.yaml
--
-- parent_group_id: MENU_TREE top-level group id (e.g. log-search, history, statistics, admin); NULL = use MENU_TREE default.
-- sort_order: non-negative when set (enforced in app); NULL = use MENU_TREE default sibling order.
--
-- Idempotent: ADD COLUMN IF NOT EXISTS (PostgreSQL 11+).
-- Apply with the same search_path as setup.sh for schema_sys.sql (SCHEMA_SYS first), e.g.:
--   PGPASSWORD='<redacted>' psql -U logmng -h localhost -p 5432 -d logmng -v ON_ERROR_STOP=1 \
--     -c "SET search_path TO logmng_sys, logmng, public;" \
--     -f backend/src/main/resources/db/migrate-screen-display-label-parent-order-20260407.sql

ALTER TABLE screen_display_label
    ADD COLUMN IF NOT EXISTS parent_group_id VARCHAR(64) NULL,
    ADD COLUMN IF NOT EXISTS sort_order INT NULL;
