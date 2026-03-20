-- Req: docs/requirements/20260320-imagelog-guid-status-composite-key.md
--
-- Split for multi-database setups (DB A = sys, DB B = ImageLog):
--   1) On DB B, SCHEMA_IMAGELOG:  migrate-imagelog-guid-status-unique-20260320.sql
--   2) On DB A, SCHEMA_SYS:      migrate-sys-decryption-composite-pk-20260320.sql
--
-- Single-schema dev (all tables in public, one database): psql from this directory can run:
--   \ir migrate-imagelog-guid-status-unique-20260320.sql
--   \ir migrate-sys-decryption-composite-pk-20260320.sql
--
-- setup.sh runs the two files separately with correct DB and search_path.

\echo '20260320: imagelog unique (guid, status)...'
\ir migrate-imagelog-guid-status-unique-20260320.sql
\echo '20260320: sys composite PKs...'
\ir migrate-sys-decryption-composite-pk-20260320.sql
