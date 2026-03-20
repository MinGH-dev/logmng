-- Req: docs/requirements/20260320-imagelog-guid-status-composite-key.md
-- ImageLog DB / schema only (table imagelog). Apply with search_path set to SCHEMA_IMAGELOG (e.g. public).
--
-- Pre-flight (manual if imagelog has production data):
--   SELECT guid, COALESCE(NULLIF(TRIM(status), ''), '') AS norm_status, COUNT(*) AS cnt
--   FROM imagelog GROUP BY 1, 2 HAVING COUNT(*) > 1;
--
-- Pair with migrate-sys-decryption-composite-pk-20260320.sql on the system database.

CREATE UNIQUE INDEX IF NOT EXISTS uq_imagelog_guid_row_status
    ON imagelog (guid, COALESCE(NULLIF(TRIM(status), ''), ''));
