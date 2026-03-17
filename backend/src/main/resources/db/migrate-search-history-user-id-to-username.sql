-- *** LEGACY — DO NOT RUN FOR NEW DEPLOYMENTS ***
-- This script converted search_history.user_id from app_user.id::text to app_user.username.
-- The canonical schema now stores numeric app_user.id in search_history.user_id (BIGINT, FK to app_user(id)).
-- Use migrate-search-history-user-id-to-bigint.sql to align existing DBs to user_id (numeric). Req: 20260316-search-history-user-id-query-and-naming.
-- *** LEGACY — DO NOT RUN FOR NEW DEPLOYMENTS ***
--
-- Remediate search_history.user_id: ensure it stores username (app_user.username), not numeric app_user.id.
-- List JOIN is app_user.username = search_history.user_id; if user_id held numeric id, JOIN would fail.
--
-- This script updates rows where user_id equals app_user.id::text to the corresponding app_user.username.
-- Idempotent: safe to run multiple times; no DROP/TRUNCATE.
-- Run only after user approval (e.g. confirm existing data or backup).
--
-- One-line run (from repo root or db dir):
--   psql -U postgres -h localhost -p 5432 -d logmng -f backend/src/main/resources/db/migrate-search-history-user-id-to-username.sql
-- Or from this directory:
--   psql -U postgres -h localhost -p 5432 -d logmng -f migrate-search-history-user-id-to-username.sql

UPDATE search_history sh
SET user_id = u.username
FROM app_user u
WHERE sh.user_id = u.id::text;
