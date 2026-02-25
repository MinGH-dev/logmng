# Migration note: decrypt_approver (20260225-department-approver-hierarchy)

## Risk of schema.sql (full apply)

- `schema.sql` uses `DROP TABLE IF EXISTS decrypt_approver` then `CREATE TABLE decrypt_approver`.
- **Re-running the full schema (e.g. for a fresh DB) wipes existing `decrypt_approver` data.** Acceptable for new installs; for existing deployments with data, use a safe migration instead.

## Safer migration for existing deployments (optional)

If you already have `decrypt_approver` with the **old** structure (e.g. `(user_id)` as PK, no `id`, no `department_code`), apply a migration that preserves data instead of DROP/CREATE:

1. Backup: `pg_dump ... -t decrypt_approver > decrypt_approver_backup.sql`
2. Add column: `ALTER TABLE decrypt_approver ADD COLUMN IF NOT EXISTS id BIGSERIAL`, then add `department_code VARCHAR(50) NULL`.
3. Backfill: existing rows get `department_code = NULL` (global approver).
4. Add FK: `ALTER TABLE decrypt_approver ADD CONSTRAINT fk_decrypt_approver_department FOREIGN KEY (department_code) REFERENCES department(code) ON DELETE SET NULL;`
5. Drop old PK/unique if any; add new PK on `id` and partial unique indexes as in `schema.sql`.
6. Optionally set `id` as primary key and drop the old primary key constraint.

This file is a note only; no executable script is provided. Adjust steps to your actual prior schema (column names and constraints).
