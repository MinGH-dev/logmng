# DB-scope validation: 20260318-menu-and-permission-restructure

**Validated by**: DB subagent  
**Date**: 2026-03-18  
**Scope**: DB only (schema, migrations, init-data consistency). No implementation.

---

## 1. Requirement summary (DB part)

- **permission_group_screen**: New rows use `screen_id` in (`pb-feplog`, `java-fw-imagelog`). Optional migration: for each row with `screen_id = 'main'`, insert two rows for `pb-feplog` and `java-fw-imagelog` with same read/decrypt; optionally remove or deprecate `main`. No schema column change; only allowed values for `screen_id` expand.
- **Optional migration file**: `migrate-main-to-pb-feplog-java-fw-imagelog.sql` (or equivalent).

---

## 2. Schema and existing usage check

### 2.1 permission_group_screen (schema.sql)

| Column               | Type         | Constraints |
|----------------------|--------------|-------------|
| permission_group_id  | BIGINT       | NOT NULL, FK → permission_group(id) ON DELETE CASCADE |
| screen_id            | VARCHAR(50)  | NOT NULL |
| scope                | VARCHAR(10)  | NULL, CHECK IN ('self','all','team') |
| read                 | BOOLEAN      | NULL |
| write                | BOOLEAN      | NULL |
| approve              | BOOLEAN      | NULL |
| decrypt              | BOOLEAN      | NULL |
| **PRIMARY KEY**      | (permission_group_id, screen_id) | |

- **screen_id**: No CHECK constraint on allowed values; expanding to `pb-feplog`, `java-fw-imagelog` does **not** require any schema change. Consistent with requirement (“No schema change to columns; only allowed values for screen_id expand”).

### 2.2 Current data (init-data.sql)

- **GENERAL_USER**: `permission_group_screen` gets `unnest(ARRAY['main','search-history','activity-log','statistics','pending-approvals'])`.
- So `main` is currently used. The optional migration is about existing rows with `screen_id = 'main'`.

### 2.3 Other DB objects

- **user_decryption_allowed**: Has `screen` VARCHAR(50) (no FK to `permission_group_screen`). Requirement §2 says decrypt-allowed store must use `pb-feplog` / `java-fw-imagelog`; the doc does **not** require a DB migration for this table. Application/backend will use the new screen IDs for new data; existing rows with `screen = 'main'` are out of scope for this requirement’s DB part.

---

## 3. Optional migration feasibility

The described migration is **feasible** with the current schema.

- **Step 1 – Insert from main**: For each row in `permission_group_screen` with `screen_id = 'main'`, insert two rows with the same `permission_group_id`, and `screen_id` = `'pb-feplog'` and `'java-fw-imagelog'`, copying the same column values. All referenced columns exist; PK is (permission_group_id, screen_id), so the new rows do not conflict with existing ones.
- **Step 2 – Optional delete**: `DELETE FROM permission_group_screen WHERE screen_id = 'main'` is valid; no other table has a FK to `permission_group_screen.screen_id`.
- **Idempotency**: Using `ON CONFLICT (permission_group_id, screen_id) DO NOTHING` on the INSERTs makes the script safe to run multiple times (e.g. if run after some groups already have the new screen_ids).

**Suggested migration shape (for implementer):**

- Copy **all** columns: `permission_group_id`, `screen_id`, `scope`, `read`, `write`, `approve`, `decrypt` (not only read/decrypt), so that the new rows fully reflect the original main row’s permissions. The requirement text mentions “same read/decrypt”; for consistency and to avoid subtle bugs (e.g. scope), copying all columns is recommended.

---

## 4. Report

### (a) Inconsistencies between requirement and DB

- **None.** The requirement’s description of `permission_group_screen` (screen_id expansion, optional migration from main to both, no schema column change) matches the current schema and is implementable as described.

### (b) Missing or incorrect table/column or migration step

1. **Migration script wording**: The requirement says “insert rows … with same read/decrypt”. The table also has `scope`, `write`, `approve`. Recommendation: state in the requirement or runbook that the migration should copy **all** columns (`scope`, `read`, `write`, `approve`, `decrypt`) from the `main` row to the new `pb-feplog` and `java-fw-imagelog` rows, so behavior is consistent and no column is left NULL by default.
2. **init-data.sql (new installs)**: The requirement’s “Planned change file list” does not mention `init-data.sql`. Currently, init-data inserts `'main'` for GENERAL_USER. For **new installations** after this restructure, it may be desirable to insert `pb-feplog` and `java-fw-imagelog` instead of (or in addition to) `main` so that fresh installs match the new permission model. This is a product/design choice; if the intent is that new installs also use the new screen IDs, the requirement or change list should explicitly add an update to `backend/src/main/resources/db/init-data.sql` (or document that setup/backend will do it elsewhere).
3. **user_decryption_allowed**: No migration is required by the requirement for this table. If the product decision is to phase out `screen = 'main'` in this table as well, that would be a separate migration and should be documented outside this requirement’s DB scope.

### (c) Pass/fail summary (DB-scope consistency)

**Result: PASS**

- The requirement is **consistent** with the current DB schema and data model.
- The optional migration **is feasible** with the existing `permission_group_screen` definition; no schema change is required for the described migration.
- Recommendations above are clarifications and optional follow-ups (init-data for new installs, “copy all columns” in migration, and possible future handling of `user_decryption_allowed`), not blockers for DB-scope consistency or implementability.

---

## 5. After schema/migration changes (reminder)

If the optional migration script is added or init-data is changed:

- Backend (and optionally API spec) must align with the new screen_id set (`pb-feplog`, `java-fw-imagelog`) and any deprecation of `main`, per the requirement and `docs/contract.md`.
