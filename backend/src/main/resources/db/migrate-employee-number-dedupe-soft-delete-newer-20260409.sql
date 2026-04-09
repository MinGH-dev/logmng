-- =============================================================================
-- Requirement: 20260409-employee-number-dedupe-soft-delete-newer
-- Purpose: One-time data cleanup — remove duplicate *active* app_user rows that
--   share the same normalized (trimmed) employee_number by soft-deleting all but
--   the oldest account (min created_at; tie-break min id).
-- Policy: Keeper = ROW_NUMBER() 1 over PARTITION BY BTRIM(employee_number)
--   ORDER BY created_at ASC NULLS LAST, id ASC. Losers (rn > 1) get deleted_at.
--   Only groups with COUNT(*) > 1 among qualifying active rows; empty trim excluded.
--
-- BACKUP: Take a DB backup or snapshot before running the migration UPDATE on
--   production/staging. Review dry-run output; do not paste PII to public channels.
--
-- Execution (adjust user/host/port/schema per environment; contract: localhost
--   :5432, database logmng — see docs/contract.md and setup.sh):
--
--   psql -U logmng -h localhost -p 5432 -d logmng -v ON_ERROR_STOP=1 \
--     -c "SET search_path TO public;" \
--     -f migrate-employee-number-dedupe-soft-delete-newer-20260409.sql
--
-- If app_user lives in a non-public schema, replace the first component of
-- search_path with SCHEMA_SYS (e.g. logmng_sys) per DB_SETUP_GUIDE / setup.sh.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- DRY-RUN (read-only): duplicate groups — keeper vs each loser. Run and review
-- before the migration UPDATE below. No writes.
-- -----------------------------------------------------------------------------
WITH dup AS (
    SELECT
        BTRIM(employee_number) AS norm_en,
        id,
        username,
        created_at,
        COUNT(*) OVER (PARTITION BY BTRIM(employee_number)) AS grp_cnt,
        ROW_NUMBER() OVER (
            PARTITION BY BTRIM(employee_number)
            ORDER BY created_at ASC NULLS LAST, id ASC
        ) AS rn
    FROM app_user
    WHERE deleted_at IS NULL
      AND employee_number IS NOT NULL
      AND BTRIM(employee_number) <> ''
),
filtered AS (
    SELECT * FROM dup WHERE grp_cnt > 1
),
keepers AS (
    SELECT
        norm_en,
        id AS keeper_id,
        username AS keeper_username,
        created_at AS keeper_created_at
    FROM filtered
    WHERE rn = 1
)
SELECT
    f.norm_en,
    k.keeper_id,
    k.keeper_username,
    k.keeper_created_at,
    f.id AS loser_id,
    f.username AS loser_username,
    f.created_at AS loser_created_at
FROM filtered f
JOIN keepers k USING (norm_en)
WHERE f.rn > 1
ORDER BY f.norm_en, f.id;

-- -----------------------------------------------------------------------------
-- MIGRATION: soft-delete loser rows (single transaction). Idempotent: re-run
-- updates 0 rows once losers already have deleted_at set.
-- -----------------------------------------------------------------------------
BEGIN;

WITH batch AS (SELECT clock_timestamp() AS ts),
dup AS (
    SELECT
        id,
        COUNT(*) OVER (PARTITION BY BTRIM(employee_number)) AS grp_cnt,
        ROW_NUMBER() OVER (
            PARTITION BY BTRIM(employee_number)
            ORDER BY created_at ASC NULLS LAST, id ASC
        ) AS rn
    FROM app_user
    WHERE deleted_at IS NULL
      AND employee_number IS NOT NULL
      AND BTRIM(employee_number) <> ''
),
loser_ids AS (
    SELECT id FROM dup WHERE grp_cnt > 1 AND rn > 1
)
UPDATE app_user u
SET deleted_at = b.ts
FROM batch b
WHERE u.deleted_at IS NULL
  AND u.id IN (SELECT id FROM loser_ids);

COMMIT;

-- -----------------------------------------------------------------------------
-- POST-VERIFY: expect duplicate_groups_remaining = 0 (no two active rows share
-- the same non-empty trimmed employee_number).
-- -----------------------------------------------------------------------------
-- SELECT COUNT(*) AS duplicate_groups_remaining
-- FROM (
--     SELECT BTRIM(employee_number) AS norm_en
--     FROM app_user
--     WHERE deleted_at IS NULL
--       AND employee_number IS NOT NULL
--       AND BTRIM(employee_number) <> ''
--     GROUP BY BTRIM(employee_number)
--     HAVING COUNT(*) > 1
-- ) v;
