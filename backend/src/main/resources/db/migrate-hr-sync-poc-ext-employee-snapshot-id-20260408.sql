-- HR Sync PoC: ext_employee.snapshot_id (nullable) + index + HR_SAMPLE snapshot seed alignment
-- Requirement: docs/requirements/20260408-hr-sync-poc-snapshot-list-and-sample-data.md (DB §2, Option A)
--
-- Order (setup.sh):
--   Run after ext_employee exists (schema_sys.sql / migrate-external-identity-tables-20260407.sql).
--   Run before step 5 init-data.sql so fresh INSERTs can populate snapshot_id; re-run safe (idempotent).
--   For legacy DBs: ADD COLUMN + index + UPDATE backfill for existing HR_SAMPLE seed rows.
--
-- Apply: SET search_path TO SCHEMA_SYS, SCHEMA_PB, public;  (see setup.sh run_sql_file_sp)

ALTER TABLE ext_employee ADD COLUMN IF NOT EXISTS snapshot_id VARCHAR(128) NULL;

CREATE INDEX IF NOT EXISTS idx_ext_employee_source_snapshot ON ext_employee (source_system, snapshot_id);

-- Idempotent: assign two PoC snapshots (≥2 employees each) using existing HR_SAMPLE keys; ext_department unchanged.
UPDATE ext_employee SET snapshot_id = 'poc-snap-20260408-A'
WHERE source_system = 'HR_SAMPLE' AND external_employee_id IN ('E-10001', 'E-10002');

UPDATE ext_employee SET snapshot_id = 'poc-snap-20260408-B'
WHERE source_system = 'HR_SAMPLE' AND external_employee_id IN ('E-10003', 'E-UNPROV-1');
