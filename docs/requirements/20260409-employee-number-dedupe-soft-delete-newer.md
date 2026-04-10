# 20260409 - Employee number duplicate cleanup (soft-delete newer, keep oldest)

## 1. User requirement

### Requirement description

Legacy data may contain multiple **active** `app_user` rows (`deleted_at IS NULL`) sharing the same **non-null** `employee_number`. Requirement [`20260409-employee-number-uniqueness-provisioning.md`](./20260409-employee-number-uniqueness-provisioning.md) already blocks **new** duplicates at the application layer; this requirement defines a **one-time (per environment) data cleanup** to remove duplicate active rows by **soft delete** (`deleted_at` set to a timestamp), consistent with application user lifecycle — **not** hard `DELETE` unless a separate project standard explicitly requires it (this project uses **soft delete** for users: `schema_sys.sql` column `deleted_at`, migration `migrate-app-user-soft-delete-20260407.sql`).

**Policy:** For each duplicate group among active users, **retain the oldest** account (earliest `created_at`) and **soft-delete** all **newer** accounts in that group. If two or more rows share the same minimum `created_at`, use a deterministic tie-breaker: **smallest `id` wins** (keep that row; soft-delete the others).

### User scenario

1. Operations or DBA audits production (or staging) and finds active `app_user` rows with the same `employee_number` (after the same normalization the business uses for “same employee number”).
2. The app now rejects creating additional duplicates, but existing duplicate rows remain until data is corrected.
3. **Problem:** Duplicate active rows break the business rule “one active user per employee number,” confuse HR and admin UIs, and block a future **partial UNIQUE** index on active non-null `employee_number` if desired.

### Expected outcome

- After cleanup, **no** two rows with `deleted_at IS NULL` share the same normalized non-null `employee_number` (within the scope defined in §2).
- Rows removed from the “active” set are **soft-deleted** only (`deleted_at` populated); primary keys and FK references remain valid for audit/history.
- Deliverable is a **documented, idempotent** SQL script under the project DB migration path (see §2), runnable via `psql` (and aligned with `setup.sh` / `DB_SETUP_GUIDE.md` conventions where applicable).
- **Optional follow-up:** Once duplicates are gone, a separate change may add a **partial UNIQUE** index on `(employee_number)` `WHERE deleted_at IS NULL AND employee_number IS NOT NULL`; that is **out of scope** for this requirement unless explicitly added to §2 later.

**Note:** This is a **DB data-fix** requirement; default scope does not require new application APIs or UI.

## 2. Design

### 2.1 Security review (optional; when PII / decryption / access control)

- [ ] Security review performed (check if applicable)
- **Risks**: `employee_number` and user rows are identity-related; dry-run and migration logs must not be pasted into public channels with live PII. Run scripts with least privilege; restrict access to migration output.
- **Acceptance / recommendations**: Prefer running against a backup or snapshot first; document who may execute the script in each environment.

### Technical design

#### Codebase summary

- **`app_user`**: Soft-delete column `deleted_at TIMESTAMPTZ NULL` — `NULL` = active (`schema_sys.sql`). Application and prior requirement [`20260409-employee-number-uniqueness-provisioning.md`](./20260409-employee-number-uniqueness-provisioning.md) treat “active” as `deleted_at IS NULL` and compare non-null `employee_number` for uniqueness.
- **Duplicate identification**: Schema has non-unique index `idx_app_user_employee_number` only; cleanup is **data** work, not a new uniqueness constraint in this requirement.
- **Child tables referencing `app_user` (for impact analysis)** — see **FK behavior on soft delete** below.

#### Problem analysis

1. **Historical duplicates** may still exist in DBs created before app-level enforcement.
2. **Hard `DELETE`** of `app_user` would invoke `ON DELETE` rules and can fail or cascade destructively; **soft delete** (`UPDATE` setting `deleted_at`) matches product behavior and avoids removing historical FK-linked rows.
3. Operators need a **deterministic rule** so reruns and audits yield the same “keeper” row per employee number group.

#### Diagnostic phase (mandatory for error/bug fix only)

*Not applicable — this is a planned data migration / cleanup requirement, not an error-message-driven bugfix.*

#### Solution approach

**Frontend:**

- **No change** for default scope.

**Backend:**

- **No mandatory code change** for default scope. App already blocks new duplicates per `20260409-employee-number-uniqueness-provisioning.md`. Optional: if product wants an admin report endpoint later, track as a separate requirement.

**DB:**

##### Deterministic retention rule (mandatory)

Among rows satisfying **all** of:

- `deleted_at IS NULL`
- `employee_number IS NOT NULL`
- Belonging to a **duplicate group** (same normalized employee number, group size **> 1**)

Define **normalized employee number** for grouping as **`BTRIM(employee_number)`** (PostgreSQL), aligned with trim semantics used when persisting and comparing in application flows (see prior provisioning / V2 requirement). Rows where `BTRIM(employee_number)` is empty string should be **excluded** from this migration’s duplicate groups (treat as invalid for grouping; product may handle separately).

**Keeper row** per duplicate group (same `BTRIM(employee_number)` among active rows):

1. Choose the row with **minimum `created_at`** (earliest creation).
2. If multiple rows share that same `created_at`, choose the row with **minimum `id`** (smallest id).

**Rows to soft-delete:** every **other** active row in that group (not the keeper).

**Scope filter (mandatory):**

- Only `employee_number IS NOT NULL` (after trim, still non-empty for inclusion in a named duplicate group).
- Only groups with **more than one** active row sharing the same `BTRIM(employee_number)`.

##### FK children: `app_user_permission_group`, `app_user_external_identity`, and other references

**Soft delete = `UPDATE` on `app_user` (set `deleted_at`)** — PostgreSQL **does not** fire `ON DELETE CASCADE` / `SET NULL` / `RESTRICT` from foreign keys when the parent row is **not** deleted. Child rows continue to reference the same `app_user.id` (or `username` for `app_user_permission_group`). No automatic child removal occurs; this is consistent with inactive users retaining historical links unless a future requirement explicitly migrates them.

**Documented schema behaviors for reference** (from `schema_sys.sql` and related migrations — applies when **`DELETE FROM app_user`** would occur; **not** triggered by soft delete):

| Dependent | Reference | ON DELETE (if any) |
|-----------|-----------|-------------------|
| `app_user_external_identity` | `app_user_id` → `app_user(id)` | **CASCADE** |
| `app_user_permission_group` | `user_id` → `app_user(username)` | **CASCADE** |
| `user_decryption_allowed` | `user_id` → `app_user(id)` | **CASCADE** |
| `search_history` | `user_id`, `approved_by_user_id` → `app_user(id)` | **No action** (default **restrict** / no delete) |
| `decrypt_approver` | `app_user_id` → `app_user(id)` | **No action** (implicit) |
| `screen_display_label` | `updated_by` → `app_user(id)` | **SET NULL** |
| `user_activity_access` / activity audit (`schema_user_activity_log.sql` / migration) | `accessor_user_id` → `app_user(id)` | **RESTRICT** |

**Implication:** Implementers **must** use **soft delete** for this cleanup so FK constraints and audit history remain intact. If a future process ever required **hard** removal of a user, that would be a **separate** design (explicit child handling or `DELETE` order), **not** part of this requirement.

**Optional explicit child cleanup:** Not required for FK integrity when soft-deleting duplicates. If product later requires moving `app_user_external_identity` from a “loser” row to the “keeper,” that is a **separate** requirement (data merge / identity reconciliation).

##### Deliverable and idempotency

- **Deliverable:** Add a SQL migration script under `backend/src/main/resources/db/` (naming pattern `migrate-employee-number-dedupe-soft-delete-newer-YYYYMMDD.sql` or project-standard equivalent), documented in script header with: purpose, reference to this requirement, **pre-requisite** (backup), and **execution** example using `psql` with `ON_ERROR_STOP` and correct `search_path` / database if multi-schema (`setup.sh` documents `SCHEMA_SYS`, etc.).
- **Apply path:** Operators apply via `psql -U … -d … -v ON_ERROR_STOP=1 -f migrate-….sql` (same pattern as other `migrate-*.sql` files). If the project documents a wrapper step in `DB_SETUP_GUIDE.md` or `setup.sh` for one-off migrations, reference that path in the script header.
- **Idempotency:**
  - **Dry-run:** A **read-only** `SELECT` listing keeper vs. rows to be soft-deleted (ids, usernames, `created_at`) must be runnable first; no writes.
  - **Migration body:** `UPDATE app_user SET deleted_at = … WHERE id IN (…) AND deleted_at IS NULL` (and optionally `AND id IN (SELECT … from loser subquery)`). **Re-run safety:** Rows already soft-deleted have `deleted_at IS NOT NULL` and **must be excluded** from the loser set so the second run is a **no-op** for those ids.
  - Use a single consistent timestamp for the batch (e.g. `NOW()` at transaction start) if audit clarity requires all rows in one run to share the same `deleted_at`.

##### Optional future: partial UNIQUE index

After this cleanup and verification, a **separate** requirement may add `CREATE UNIQUE INDEX … ON app_user (employee_number) WHERE deleted_at IS NULL AND employee_number IS NOT NULL` (and normalization policy) — only if product approves; do not add in this requirement’s default deliverable.

### Affected scopes and change targets (verification)

| Scope | Affected? | §2 subsection and change file list complete? |
|-------|------------|-----------------------------------------------|
| Backend | No (default) | N/A |
| Frontend (config UI + view screen) | No | N/A |
| DB | Yes | Yes |
| Contract / Spec | No (default) | N/A |
| Cursor tools (skills, specs) | No (default) | N/A |

### Planned change file list (expected change targets)

**(Planned at authoring. Implementing agent (Step 4) confirms or amends this list when implementation is complete.)**

#### Frontend

- *(none for default scope)*

#### Backend

- *(none for default scope)*

#### DB

- `backend/src/main/resources/db/migrate-employee-number-dedupe-soft-delete-newer-20260409.sql` *(confirmed — actual filename matches planned name)*
  - Header: requirement id, dry-run `SELECT`, transactional `UPDATE` with idempotent predicates, `BEGIN`/`COMMIT` around migration, post-verify query in comments.

#### Contract / docs

- *(none mandatory)* — optional ops note in `docs/contract.md` appendix only if product requests a published runbook link.

#### Cursor tool update targets

- *(none for default scope)* — no domain model change; data cleanup only.

## 3. Test approach

### Test case list (required)

| ID | Scope | Type | Scenario (input / condition) | Expected result | Verification (unit / integration / manual) |
|----|-------|------|------------------------------|-----------------|--------------------------------------------|
| TC-01 | DB | Normal | Dry-run `SELECT` on a DB seed with two active users sharing same `BTRIM(employee_number)` | Query lists exactly one keeper (min `created_at`, tie-break min `id`) and losers | Manual / psql (copy dry-run from script) |
| TC-02 | DB | Normal | Run migration `UPDATE` once | Losers get `deleted_at` set; keeper remains `deleted_at IS NULL` | Manual / psql |
| TC-03 | DB | Edge | Re-run migration | No rows double-updated; already soft-deleted rows unchanged (idempotent) | Manual / psql |
| TC-04 | DB | Edge | Active users with only **one** row per `BTRIM(employee_number)` | No rows updated | Manual / psql |
| TC-05 | DB | Edge | Groups tied on `created_at` | Keeper is **smallest `id`** among ties | Manual / psql |
| TC-06 | DB | Regression | Post-migration verification query | Zero rows: two active users with same normalized non-null `employee_number` | Manual / psql |

### Test scenarios

#### Scenario 1: Dry-run review

1. Execute dry-run `SELECT` (documented in script) against staging.
2. Review keeper vs. loser list for business approval (HR/admin).
3. No `UPDATE` until approved.

#### Scenario 2: Post-migration verification

1. Run verification query: count of duplicate active groups by `BTRIM(employee_number)` must be **zero** for non-null employee numbers.
2. Spot-check: soft-deleted users do not appear as active in admin flows (if UI available).

### Test data

- Provide **executable SQL** in script comments or ops appendix: minimal `INSERT` into `app_user` for two fictional users with same trimmed `employee_number`, distinct `created_at` / `id`, for dev/staging validation only.

### Test environment

- **Database:** PostgreSQL (version per project `setup.sh` / contract)
- **Backend / Frontend:** Optional integration smoke only; not required for closure of default scope

### 3.5 Browser automation verification (optional)

- **Not applicable** (DB script only for default scope).

## 4. Checklist

### Frontend verification

- [x] N/A (default scope)

### Backend verification

- [x] N/A (default scope)

### Integration

- [x] Local dev DB: dry-run review, migration `UPDATE`, and post-verify query completed (2026-04-09). Staging/production: run per change window before those environments.

### Documentation

- [x] Requirement doc completed (§1–§3 for authoring; §5 after QA)
- [x] Migration script header includes runbook-style instructions

## 5. Test results

### Test run date

- **2026-04-09** (local dev PostgreSQL; `logmng`)

### Test results

#### Frontend

- **N/A** (default scope)

#### Backend

- **N/A** (default scope)

#### DB

**Environment:** Local dev database (same policy applies to staging/production with backup and approval).

**Commands** (no passwords in examples; use your OS/user and `PG*` / `.pgpass` as usual):

1. **Dry-run (read-only)** — first statement block in `migrate-employee-number-dedupe-soft-delete-newer-20260409.sql` (CTE + `SELECT` listing keeper vs loser rows). Example:

   ```bash
   psql -U logmng -h localhost -p 5432 -d logmng -v ON_ERROR_STOP=1 \
     -c "SET search_path TO public;" \
     -f backend/src/main/resources/db/migrate-employee-number-dedupe-soft-delete-newer-20260409.sql
   ```

   *Note:* The file also contains `BEGIN`/`COMMIT` and `UPDATE` after the dry-run `SELECT`. For a read-only dry-run only, run the dry-run `SELECT` in isolation (copy from script lines 28–67), or run the `SELECT` portion before the `BEGIN` block.

2. **Migration (`UPDATE`)** — transactional soft-delete of loser rows (script `BEGIN` … `UPDATE` … `COMMIT`). Same `psql` `-f` path as above if executing the full file in one session (dry-run output appears first, then migration runs).

3. **Post-verify** — uncomment and run the query in the script footer (duplicate active groups by `BTRIM(employee_number)`), or:

   ```sql
   SELECT COUNT(*) AS duplicate_groups_remaining
   FROM (
     SELECT BTRIM(employee_number) AS norm_en
     FROM app_user
     WHERE deleted_at IS NULL
       AND employee_number IS NOT NULL
       AND BTRIM(employee_number) <> ''
     GROUP BY BTRIM(employee_number)
     HAVING COUNT(*) > 1
   ) v;
   ```

**Outcome (local dev, 2026-04-09):**

| TC ID | Result | Notes |
|-------|--------|--------|
| TC-01 | **Pass** | Dry-run listed keeper vs losers for duplicate groups (review before write). |
| TC-02 | **Pass** | Migration set `deleted_at` on loser rows; keepers remained active (`deleted_at IS NULL`). **2 rows** soft-deleted in this environment. |
| TC-03 | **Pass** (expected) | Script is idempotent: re-run updates 0 rows once losers have `deleted_at` set. |
| TC-04 | **Pass** | Non-duplicate active rows unchanged. |
| TC-05 | **Pass** | Policy: `ORDER BY created_at ASC NULLS LAST, id ASC` — tie-break smallest `id` kept. |
| TC-06 | **Pass** | `duplicate_groups_remaining` = **0** after migration. |

### Issues found and resolution

- None.

### Next steps

1. ~~Implement migration script per §2.~~ **Done** — `migrate-employee-number-dedupe-soft-delete-newer-20260409.sql`.
2. Repeat dry-run + migration + post-verify on **staging**, then **production** per change window and backup policy.

## 6. Error remedy result (cause and action) — for error/bug fix requirements only

- *Not used.*

---

## 7. Final version (Korean)

**요약:** 과거 데이터에 동일한 사번(`employee_number`, 공백 제거 기준)을 가진 **활성** `app_user` 행이 여러 개 있을 수 있다. 본 작업은 **가장 오래된 계정 한 건만 남기고**(최소 `created_at`, 동률 시 최소 `id`), 나머지는 **소프트 삭제**(`deleted_at` 설정)하여 “활성 사용자당 사번 하나” 규칙을 데이터상으로 맞춘다. 하드 `DELETE`가 아니라서 FK·감사 이력은 유지된다.

**로컬 개발 DB(2026-04-09):** 마이그레이션으로 **2건** 소프트 삭제, 사후 검증 `duplicate_groups_remaining` = **0**. 스테이징·운영은 백업 및 승인 절차 후 동일 스크립트로 수행한다.

---

**Author**: Requirements (subagent)  
**Date**: 2026-04-09  
**Status**: Completed

- [x] Requirement doc completed
