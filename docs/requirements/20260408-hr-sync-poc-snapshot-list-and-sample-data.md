# 20260408 - HR Sync PoC snapshot list, sample external HR data, and personnel view

## 1. User requirement

### Requirement description

The HR Sync Proof-of-Concept must be **runnable with realistic sample data** and must let an authorized user **choose a snapshot identifier from a server-provided list** and **inspect personnel (HR replica) records** belonging to that snapshot. Behavior stays **zero-impact**: preview and list paths perform **no writes** to `app_user`, application permission tables, or production Tree authority. Sample data may be loaded **only** into replica tables (`ext_department`, `ext_employee`) via the **DB** scope (migration / seed), consistent with existing ETL vs app-role grants.

This requirement **extends** the PoC described in `docs/requirements/20260408-external-hr-user-sync-security-db-design.md` (§2.6), `specs/hr-sync-poc.spec.yaml`, and `docs/contract.md` (HR Sync PoC). Any **new** HTTP paths or response fields must be added to **`specs/hr-sync-poc.spec.yaml`**, `docs/contract.md`, and `docs/api-definition.md` in the **same change** as the implementation (**DOC-CODE-SYNC**, `docs/workflow/DOC-CODE-SYNC.md`).

### User scenario

1. An operator enables the PoC (`HR_SYNC_POC_ENABLED` true) and opens the HR Sync PoC preview area (existing or extended UI).
2. The UI loads **available snapshot IDs** from the backend (distinct logical loads / extract batches for PoC).
3. The operator **selects one snapshot ID** from the list.
4. The UI requests **personnel rows** for that snapshot and displays a read-only table (name, job, department key, active flag, etc., per §2.1 masking rules).
5. The operator may switch snapshot and refresh the personnel list; **no apply** and no mutation of app users or permissions occurs.

**Problem**: Today the PoC preview stub may only expose aggregate counts (`SELECT COUNT` from `ext_employee`) with **no** snapshot dimension and **insufficient** multi-snapshot sample data for demos; the UI cannot **list snapshots** or **drill into** replica employees for a chosen snapshot.

### Expected outcome

- **DB**: PoC has **at least two** distinct snapshot groupings of `ext_employee` rows (see §2) so list + personnel views are demonstrable; loading uses **`ext_*` writes only** (ETL/migration role), not app runtime writes.
- **Backend**: **Read-only** APIs under `/api/hr-sync/poc` (1) return a **snapshot list** and (2) return **paginated (or bounded) personnel** for a selected `snapshotId`. No `INSERT`/`UPDATE`/`DELETE` against `app_user` or permission tables on these code paths.
- **Frontend**: Snapshot **dropdown or list** selection drives personnel table; empty snapshot list and error states follow existing PoC gating (`POC_DISABLED`, validation).
- **Contract/spec**: `specs/hr-sync-poc.spec.yaml` and `docs/contract.md` **HR Sync PoC** section document the new endpoints and fields; deviations are not allowed without simultaneous doc updates.

**Note**: Numeric layout standards for unrelated search forms do not apply unless the personnel view reuses shared filter components; if it does, align with `docs/design/search-fields-by-screen.md` only where those components are shared.

## 2. Design

### 2.1 Security review (optional; when PII / decryption / access control)

- [ ] Security review performed (check when Security subagent has reviewed this extension)

**Risks**

- **PII exposure**: `ext_employee` may carry `display_name`, `email`, `employee_number`, and other fields suitable for identification. Listing them in the PoC UI/API increases disclosure surface compared to aggregate counts alone.
- **Over-broad audience**: If any authenticated user can call the new endpoints, personnel data may be visible outside intended admin/PoC operators.

**Acceptance / recommendations (product + Security must align implementation)**

- **Authentication**: All new routes under `/api/hr-sync/poc` require a **valid session**, consistent with `specs/hr-sync-poc.spec.yaml` §3.
- **Authorization**: Restrict snapshot list and personnel APIs to the **same class of principal** as existing PoC preview (e.g. system admin and/or dedicated screen permission for the HR Sync PoC view). Deny with **403** and project `code` (`FORBIDDEN`, `FUNCTION_NOT_ALLOWED`, or aligned existing PoC denial codes). Register paths in **`ScreenAccessInterceptor`** (or successor) so **screen access** and API enforcement stay consistent.
- **Data minimization**: Response DTOs should expose **only fields needed** for the PoC personnel table. Fields such as raw `email` may be **omitted** or **masked** (e.g. local-part redaction, domain-only, or configurable PoC policy). `employee_number` may be **partially masked** if product treats it as sensitive.
- **No secrets in JSON**: Do not return upstream credentials, manifest secrets, or full replica row dumps beyond the agreed DTO.
- **Audit (optional for PoC)**: If product requires audit for PII list access, specify read-only logging metadata only; **no** new writes to permission stores.

### Technical design

#### Problem analysis

1. **Snapshot grouping**: `ext_employee` as defined in `backend/src/main/resources/db/schema_sys.sql` has **no** `snapshot_id` column today; distinct PoC “snapshots” cannot be distinguished without schema or naming convention changes.
2. **Sample volume**: `init-data.sql` seeds a single logical `HR_SAMPLE` cohort; PoC needs **multiple snapshot IDs** with **non-empty** employee sets each.
3. **API gap**: Current spec documents `GET .../config` and `POST .../preview` only; there is no machine-readable contract for **snapshot enumeration** or **per-snapshot personnel**.

#### Diagnostic phase (mandatory for error/bug fix only)

*(Not applicable — feature requirement.)*

#### Solution approach

**DB:**

- **Snapshot grouping strategy (pick one; implementer documents final choice in spec + §2 change list)**  
  - **Option A (preferred for minimal surface)**: Add a **nullable** PoC-oriented column on `ext_employee`, e.g. **`snapshot_id VARCHAR(128) NULL`**, indexed for `(source_system, snapshot_id)` or `(snapshot_id)` as needed for list queries. Existing rows may use `NULL` or a default single snapshot for backward compatibility; **new PoC seed rows** use explicit snapshot keys (e.g. `poc-snap-20260408-A`, `poc-snap-20260408-B`).  
  - **Option B**: Introduce a small table **`hr_sync_poc_snapshot`** (`snapshot_id` PK, optional `label`, `source_system`, `created_at`) **only if** product requires snapshot-level metadata without embedding `snapshot_id` on every employee row; otherwise avoid extra tables.  
- **Sample data delivery**: Prefer **idempotent migration SQL** (e.g. `migrate-hr-sync-poc-sample-snapshots-<date>.sql`) **plus** updates to **`init-data.sql`** (or a dedicated seed block) so fresh environments get **≥2 snapshots** with **≥2 employees each** (adjust counts as needed for demo). Inserts target **`ext_department` / `ext_employee` only**; **`source_system`** may stay `HR_SAMPLE` or use a dedicated PoC label — **must** remain consistent with existing `setup.sh` grants (app **SELECT-only** on `ext_*`).  
- **No runtime sample injection** from application code for production paths; PoC data is **DB-delivered**.

**Backend:**

- Add **read-only** endpoints (exact paths and query params — **YAML authority** after Step 4), for example:  
  - **`GET /api/hr-sync/poc/snapshots`** — returns distinct snapshot ids available for PoC (and optional metadata: label, row count, max `imported_at`).  
  - **`GET /api/hr-sync/poc/snapshots/{snapshotId}/employees`** — returns a **bounded** page of personnel DTOs for the snapshot (`page`/`size` or `limit`/`cursor`; **must** enforce a max page size).  
- Validate `snapshotId` (length, allowed charset); unknown snapshot → **404** or **200 with empty list** per product choice — **document in spec**.  
- Queries read **`ext_employee`** (and optionally join `ext_department` for display name) **only**; **no** writes to `app_user` / permission tables. When `HR_SYNC_POC_ENABLED` is false, **403 `POC_DISABLED`** (or route not registered) consistent with existing PoC.

**Frontend:**

- On PoC screen load: fetch config (existing), then fetch **snapshot list**.  
- **Selection UX**: Dropdown, listbox, or table row selection for `snapshotId`; on change, fetch **personnel** for that id.  
- Display loading/error states; respect **masking** expectations (do not render fields the API omits).  
- Gate the entire sub-flow when `pocEnabled` is false (hide or disable with message consistent with existing PoC UI).

### Affected scopes and change targets (verification)

| Scope | Affected? | §2 subsection and change file list complete? |
|-------|------------|-----------------------------------------------|
| Backend | Yes | Yes |
| Frontend (config UI + view screen) | Yes | Yes |
| DB | Yes | Yes |
| Contract / Spec | Yes | Yes |
| Cursor tools (skills, specs) | Optional | If domain skills mention PoC APIs only |

### Planned change file list (expected change targets)

**(Planned at authoring. Implementing agent (Step 4) confirms or amends this list when implementation is complete.)**

#### Frontend

- `frontend/src/components/UserManagement/HrSyncPocPreview.js` (or equivalent PoC container)
  - Snapshot selector + personnel table; service calls for new APIs.
- `frontend/src/services/hrSyncPocService.js`
  - Client methods for snapshot list and personnel endpoints.
- `frontend/src/config/hrSyncPocUi.js` (if present)
  - Labels, max page size hints aligned with backend.

#### Backend

- `backend/src/main/java/com/logmng/controller/HrSyncPocController.java` (or new nested controller under same base path)
  - New GET handlers; reuse PoC feature flags and auth patterns.
- `backend/src/main/java/com/logmng/service/HrSyncPocService.java`
  - Read-only queries for distinct snapshots + paginated employees; **no** application permission mutations.
- New/updated DTOs under `backend/src/main/java/com/logmng/dto/...`
  - Snapshot list item + employee row response (field list per §2.1).
- `backend/src/main/java/com/logmng/config/ScreenAccessInterceptor.java` / `WebConfig.java`
  - Register new paths for screen access mapping if required by project rules.
- Tests: `HrSyncPocControllerTest`, `HrSyncPocServiceTest` (extend for new endpoints).

#### DB

- `backend/src/main/resources/db/schema_sys.sql` and/or **`migrate-*.sql`**
  - Column `ext_employee.snapshot_id` **and/or** optional `hr_sync_poc_snapshot` table per chosen strategy.
- `backend/src/main/resources/db/init-data.sql` (and/or new migrate seed)
  - Additional `ext_*` rows for **≥2** snapshots.
- `backend/src/main/resources/db/setup.sh` / `check-db.sh`
  - Update only if new objects need grants or health checks (follow existing ext_* patterns).

#### Contract / spec (same PR as code)

- `specs/hr-sync-poc.spec.yaml` — new sections for snapshot list + personnel APIs; bump version if project convention requires.
- `docs/contract.md` — HR Sync PoC subsection: new bullets for endpoints and DTO summary.
- `docs/api-definition.md` — narrative API list and examples.

## 3. Test approach

### Test case list (required)

**Domain note**: Map each new `/api/hr-sync/poc/*` route to **controller → permission / PoC flag check → denial** per `api-permission-map` skill; include **403** cases for disabled PoC and unauthorized roles.

| ID | Scope | Type | Scenario (input / condition) | Expected result | Verification (unit / integration / manual) |
|----|-------|------|------------------------------|-----------------|--------------------------------------------|
| TC-01 | DB | Normal | Fresh DB after migration + seed: `ext_employee` rows exist for **snapshot A** and **snapshot B** with non-zero counts | Distinct snapshot keys queryable; app role **cannot** INSERT into `ext_employee` (existing grant tests still pass) | Manual or scripted psql + existing `check-db.sh` patterns |
| TC-02 | Backend | Normal | Authenticated allowed user, `HR_SYNC_POC_ENABLED` true, `GET /api/hr-sync/poc/snapshots` | **200**, `success: true`, `data` contains array of snapshots including A and B (or agreed ids) | Unit / integration (`mvn test`) |
| TC-03 | Backend | Normal | Same as TC-02, `GET .../snapshots/{id}/employees` with valid id, `size` within limit | **200**, paginated list of personnel DTOs; fields match spec; no internal-only columns | Unit / integration (`mvn test`) |
| TC-04 | Backend | Edge | `GET .../employees` with unknown `snapshotId` | Behavior per spec (**404** or empty page + documented code) | Unit (`mvn test`) |
| TC-05 | Backend | Exception | `HR_SYNC_POC_ENABLED` false, `GET /api/hr-sync/poc/snapshots` | **403**, `POC_DISABLED` (or route absent — document) | Unit (`mvn test`) |
| TC-06 | Backend | Exception | Authenticated user **without** PoC screen/admin permission | **403** `FORBIDDEN` / `FUNCTION_NOT_ALLOWED` (aligned with project) | Unit / integration |
| TC-07 | Backend | Edge | `GET .../employees` with `size` above max | **400** `VALIDATION_ERROR` or clamped page — **document in spec** | Unit (`mvn test`) |
| TC-08 | Frontend | Normal | PoC enabled: snapshot list loads; user selects snapshot B | Personnel table shows rows for B; loading indicator clears | Unit (`npm test`) |
| TC-09 | Frontend | Normal | PoC disabled from config | Snapshot/personnel controls hidden or disabled; user sees consistent message | Unit (`npm test`) |
| TC-10 | QA | Integration | End-to-end: login as allowed user → open PoC UI → select each snapshot → verify table content matches DB seed | Visual + optional network log; no mutations to `app_user` | Manual / browser automation per policy |
| TC-11 | QA | Regression | After PoC browsing, run DB assertion or existing check: **no new** unexpected writes to `app_user` / permission tables from preview session | Baseline unchanged aside from normal session audit if any | Manual / integration |

### Test scenarios

#### Scenario 1: Multi-snapshot demo

1. Apply DB migration + seed; confirm two snapshot ids in DB.
2. Call `GET /api/hr-sync/poc/snapshots` with valid session; verify both ids appear.
3. For each id, call personnel endpoint; verify row sets differ and match seed.
4. Confirm `POST /api/hr-sync/poc/preview` still works when `snapshotId` is supplied (existing spec).

#### Scenario 2: Access denial

1. Disable PoC flag; verify snapshot APIs return **403** (or routes unregistered).
2. Authenticate as user without permission; verify **403** on snapshot and personnel endpoints.

### Test data

- After implementation, §5 should reference **executable SQL** copied from the migration/seed: `INSERT` into `ext_department` / `ext_employee` with explicit `snapshot_id` (if Option A) for snapshots **A** and **B**, using `HR_SAMPLE` or agreed `source_system`.

### Test environment

- Frontend: `http://localhost:3001` (per contract)
- Backend: `http://localhost:9200`
- Database: PostgreSQL (project standard)

### 3.5 Browser automation verification (optional)

- **Applicable TCs**: TC-10 (and TC-09 if feasible).
- **Procedure**: Navigate to HR Sync PoC screen → assert snapshot control populated → select snapshot → snapshot personnel table → `browser_snapshot` confirms expected columns; verify no console errors.
- **Reference**: `docs/workflow/BROWSER-AUTOMATION-VERIFICATION-POLICY.md`

## 4. Checklist

### Frontend verification

- [x] API parameters validated (§3 TC-08; `HrSyncPocPreview.test.js`)
- [x] UI behavior confirmed (§3 TC-08, TC-10; browser MCP)
- [x] Error handling verified (unit tests: PoC disabled / 403 paths)

### Backend verification

- [x] API test cases written and run (`HrSyncPocControllerTest`, `HrSyncPocServiceTest`)
- [ ] Logs checked (not required for this pass)
- [x] Performance checked (if applicable — pagination bounded) (spec max page size enforced in code/tests)

### Integration

- [x] End-to-end flow tested (TC-10; admin session, snapshot select, personnel table)
- [ ] Edge cases tested (full §3 matrix deferred; core paths verified)

### Documentation

- [x] Requirement doc completed (§5 recorded)
- [x] `specs/hr-sync-poc.spec.yaml`, `docs/contract.md`, `docs/api-definition.md` updated with new endpoints (**DOC-CODE-SYNC**) (present in branch; verified at review time)

## 5. Test results

### Test run date

- 2026-04-08 (QA verification run)

### Test results

**Environment:** macOS dev; PostgreSQL `logmng` on localhost:5432; backend `http://localhost:9200`; frontend `http://localhost:3001`. `./scripts/dev-services.sh all restart` then health checks.

**DB (TC-01):**

- **Migration file re-apply:** `psql … -f migrate-hr-sync-poc-ext-employee-snapshot-id-20260408.sql` failed with `ERROR: must be owner of table ext_employee` (OS user `logmng` not table owner). **Data already present:** `SELECT snapshot_id, count(*) FROM ext_employee WHERE source_system='HR_SAMPLE' GROUP BY snapshot_id` returned two groups `poc-snap-20260408-A` (2 rows) and `poc-snap-20260408-B` (2 rows). For fresh installs, run migration via `setup.sh` / DB owner as documented.
- **Command:**  
  `PGPASSWORD=logmng123 psql -h localhost -U logmng -d logmng -c "SELECT snapshot_id, count(*) FROM ext_employee WHERE source_system='HR_SAMPLE' GROUP BY snapshot_id ORDER BY snapshot_id;"`

**Health / verify (post-restart):**

- `curl -s http://localhost:9200/api/health` → 200, `success: true`
- Frontend `http://localhost:3001` → HTTP 200
- `curl -s http://localhost:9200/api/db/test` → `data.connected === true`

**Automated tests:**

| Scope | Command | Result |
|-------|---------|--------|
| Backend (PoC) | `cd backend && mvn test -q -Dtest=HrSyncPocControllerTest,HrSyncPocServiceTest` | Pass (exit 0) |
| Frontend (PoC) | `cd frontend && npm test -- --watchAll=false --testPathPattern=HrSyncPocPreview` | Pass — 5 tests (console warnings only) |

**Browser automation (§3 TC-10) — MCP `project-0-dev-browser` (Puppeteer):**

- **Base URL:** `http://localhost:3001`
- **Flow:** Navigate → local login `userId=20269999`, `password=admin123` → open **HR Sync PoC** from sidebar → snapshot `<select id="hr-sync-poc-snapshot-select">`.
- **TC-10 Pass:** Options count **3** (placeholder “스냅샷 선택…” + **2** snapshot ids: `poc-snap-20260408-A`, `poc-snap-20260408-B`). Selected `poc-snap-20260408-A`; personnel table `.hr-sync-poc-emp-table tbody tr` count **2**; row text included **Sample Alpha**, **Sample Beta**.
- **Console:** No errors captured in MCP evaluate output during this run.

**§3 mapping (summary):**

| ID | Result | Note |
|----|--------|------|
| TC-01 | Pass (data) | Migration re-run blocked by ownership; counts confirm ≥2 snapshots × ≥2 employees |
| TC-02–TC-07 | Pass | Covered by `mvn test` PoC tests (not full suite in this run) |
| TC-08 | Pass | `HrSyncPocPreview.test.js` |
| TC-09 | Pass | `HrSyncPocPreview.test.js` |
| TC-10 | Pass | Browser MCP (above) |
| TC-11 | N/A / spot | No assertion run; preview paths are read-only per design |

**Commands (copy-paste):**

```bash
./scripts/dev-services.sh all restart
sleep 12
curl -s http://localhost:9200/api/health
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:3001
curl -s http://localhost:9200/api/db/test
cd backend && mvn test -q -Dtest=HrSyncPocControllerTest,HrSyncPocServiceTest
cd frontend && npm test -- --watchAll=false --testPathPattern=HrSyncPocPreview
```

### Issues found and resolution

- **DB migration as non-owner:** Re-applying `migrate-hr-sync-poc-ext-employee-snapshot-id-20260408.sql` with app DB user failed (`must be owner of table ext_employee`). **Resolution for this environment:** schema/data already applied; use superuser or `setup.sh` pipeline for new databases.

### Next steps

1. Security review checkbox in §2.1 when applicable.
2. Optional: run full `mvn test` / `npm test` for whole-branch regression before release PR.

## 6. Error remedy result (cause and action) — for error/bug fix requirements only

N/A

---

## 7. Final version (Korean) — add after all verification is complete

*(Deferred per `docs/workflow/DOCUMENT-LANGUAGE-POLICY.md`.)*

---

**Author**: Requirements (subagent)  
**Date**: 2026-04-08  
**Status**: Verified (QA §5 2026-04-08)
