# 20260320 - Permission group management screen entry error and migration applicability check

## 1. User requirement

### Requirement description

Users report an **error when entering the permission group management screen** (권한 그룹 관리). The product needs to **determine whether a recent database migration was not applied** on the target environment, among other possible causes. The investigation must **not assume** that missing migrations are the root cause until **evidence** (logs, HTTP responses, schema inspection) confirms it.

### User scenario

1. An administrator (or user with `permission-group-management` / `user-permission-hierarchy` access) opens the **permission group management** UI from the menu.
2. On load, the screen fetches permission group data and related lists.
3. **Problem**: An error appears (exact message may be generic UI text, 4xx/5xx, or browser console/network failure), blocking normal use of the screen.

### Expected outcome

- The **actual root cause** is identified using the **mandatory diagnostic phase** (logs + reproduction + analysis) before any logic-only fix is applied.
- **Migration applicability** is **verified objectively**: required `permission_group` / `permission_group_screen` (and related sys-schema) shape is compared against what the running backend expects (SQL and JDBC mapping), using schema queries and/or `check-db` / documented migration inventory vs. `setup.sh` order.
- If the cause is **missing columns or constraints** on `permission_group_screen` (or related tables), operators can **apply the correct idempotent migration scripts** (or re-run the documented setup path) and confirm the screen loads.
- If the cause is **not** DB-related (e.g. auth/session, CORS, screen access interceptor, frontend runtime API base URL, 403 vs 500), the same diagnostic evidence **rules out** migration gap and points implementers to the correct scope (Backend / Frontend / config).
- **Diagnostic logging** used during investigation is **not left verbose in production** (DEBUG, feature flag, or removed after verification).

**Note**: This requirement does **not** change search/filter UI standards; pattern §2.4 (search/filter consistency) does **not** apply.

## 2. Design

### 2.1 Security review (optional; when PII / decryption / access control)

- [ ] Security review performed (check if applicable — **recommended only if** investigation shows authz/session anomalies or sensitive data in logs)

Screen entry touches **admin-only** APIs and **screen access** rules. Formal Security Step 2 is **optional** unless diagnostics reveal access-control or PII-in-log risks.

### Technical design

#### Codebase summary

**Frontend**

- `PermissionGroupManagement.js` gates rendering by `getAllowedScreenIds` / system admin; on allowed access it renders `PermissionGroupPanel`.
- `PermissionGroupPanel.js` on mount calls `loadGroups()` → `listPermissionGroups()` (`GET /api/permission-groups`) and `loadUsers()` → `getUsers()`. Failures set UI error state or empty lists; `logger.error` logs client-side failures.

**Backend**

- `PermissionGroupController` exposes `/api/permission-groups` (list, CRUD, user assignment). `ScreenAccessInterceptor` maps `^/api/permission-groups.*` to screens `USER_MANAGEMENT` and `USER_PERMISSION_HIERARCHY` (see `ScreenAccessInterceptor` path rules).
- `PermissionGroupService.listAll()` selects groups, then for **each** group calls `loadAllowedScreens`, which runs:
  - `SELECT screen_id, scope, read, write, approve, decrypt FROM permission_group_screen WHERE permission_group_id = ?`
- `saveAllowedScreens` inserts with `(permission_group_id, screen_id, scope, read, write, approve, decrypt)`. The code therefore **requires** those columns to exist on `permission_group_screen` in the **sys** datasource / `SCHEMA_SYS` (or `public` when not split).

**DB**

- `schema_sys.sql` defines `permission_group_screen` with `scope`, `read`, `write`, `approve`, `decrypt` (and `chk_permission_group_screen_scope` including `team` when current DDL is applied in full).
- **Idempotent migrations** under `backend/src/main/resources/db/` that affect permission-group screens include:
  - `migrate-permission-group-screen-scope.sql` — adds `scope`
  - `migrate-permission-group-screen-functions.sql` — adds `read`, `write`, `approve`
  - `migrate-permission-group-screen-decrypt.sql` — adds `decrypt`
  - `migrate-permission-group-screen-scope-team.sql` — extends scope check for `team`
  - `migrate-main-to-pb-feplog-java-fw-imagelog.sql` — copies rows from legacy `main` to `pb-feplog` / `java-fw-imagelog`
  - `migrate-permission-group-screen-imagelog-canonical.sql` — normalizes `java-fw_imagelog` → `java-fw-imagelog`
- `setup.sh` (current tree) runs **after** `schema_sys.sql` and init data: **`migrate-main-to-pb-feplog-java-fw-imagelog.sql`** and **`migrate-permission-group-screen-imagelog-canonical.sql`** for permission-group screen rows. It does **not** explicitly invoke the older **`migrate-permission-group-screen-scope.sql`**, **`migrate-permission-group-screen-functions.sql`**, **`migrate-permission-group-screen-decrypt.sql`**, or **`migrate-permission-group-screen-scope-team.sql`** as separate steps. **Fresh installs** that apply current `schema_sys.sql` get the full column set via DDL; **legacy databases** created before those columns existed may still need the standalone migrate scripts if `CREATE TABLE IF NOT EXISTS` never added new columns.
- Multi-DB deployments must apply sys-schema migrations on **DB A** with correct `search_path` (`SCHEMA_SYS`, `SCHEMA_PB`, `public` per `DB_SETUP_GUIDE.md` / contract).

#### Problem analysis

1. **Observed symptom**: Error on entering permission group management — could be HTTP error from `GET /api/permission-groups`, `GET /api/users`, **403** from screen access, **500** from SQL (e.g. missing column), network/CORS, or frontend runtime config (`runtimeApi` / base URL).
2. **Migration hypothesis**: If `permission_group_screen` lacks `read`, `write`, `approve`, `decrypt`, or `scope`, PostgreSQL will raise errors when `PermissionGroupService` runs `loadAllowedScreens`, likely surfacing as **500** on list. This is **one** plausible cause, not proven until logs/SQL state confirm it.
3. **Setup vs. legacy gap**: Operators who upgraded code but did not run all historical migrate files (or used a partial `setup.sh` path) may have **row** migrations applied (5a / 5a-1) while **column** migrations from older releases were never run on an old table shape — verification must compare **actual catalog** to **expected columns**.

#### Diagnostic phase (mandatory for error/bug fix only)

- **Phase 0 (diagnostic):** (1) Add diagnostic (**DEBUG** or dev-flag) logs in **suspected areas**: `PermissionGroupService.listAll` / `loadAllowedScreens` (SQL state, group id, SQLException message **without** PII), `PermissionGroupController` entry, `ScreenAccessInterceptor` denial path for `/api/permission-groups`, and frontend `PermissionGroupPanel` `loadGroups` / `loadUsers` (status code, `code` from JSON body if present). (2) Reproduce once and capture **backend** logs + **browser** Network tab (status, response body). (3) **Analyze** to confirm root cause. (4) Only after confirmation, apply the fix (migrations, code, or config).
- **Production safety:** Diagnostic logs must be **DEBUG** (off in prod), **feature-flagged**, or **removed/downgraded** after verification.

#### Solution approach

Structure by scope; **do not** prescribe a code fix until diagnostics confirm the cause.

**Frontend:**

- **Verify** whether failure is pre-request (routing, `getApiBaseUrl()`, credentials) or post-response (parse error, unexpected payload). Add **temporary** diagnostic logging per Phase 0 if needed.
- **Confirm** user object includes expected `allowedScreens` / `screenFunctions` for the permission-group screen so UI does not mis-attribute a **403** as a generic load failure.

**Backend:**

- **Verify** `GET /api/permission-groups` response path: interceptor allow/deny vs. service SQLException.
- If diagnostics show **missing column / relation** errors, **coordinate with DB scope** to apply idempotent migrations on the correct database/schema; **do not** weaken SQL to hide schema drift without product approval.

**DB:**

- **Inventory**: Treat the files listed in **Codebase summary → DB** as the **canonical list** of permission-group-screen–related migrations; compare to what was run in the environment (deployment log, DBA runbook, or `setup.sh` history).
- **Verify** with catalog queries (example pattern — adjust schema name for non-`public`):

```sql
-- Expected columns on permission_group_screen (names only; run on DB A / SCHEMA_SYS as applicable)
SELECT column_name, data_type
FROM information_schema.columns
WHERE table_schema = current_schema()  -- or literal e.g. 'logmng_sys'
  AND table_name = 'permission_group_screen'
ORDER BY ordinal_position;
```

- **Confirm** `chk_permission_group_screen_scope` allows `team` if rows use `team` (migration `migrate-permission-group-screen-scope-team.sql`).

### Affected scopes and change targets (verification)

**Change target verification** (per `docs/workflow/REQUIREMENTS-CHANGE-TARGET-CHECKLIST.md`) completed before finalizing §2.

| Scope | Affected? | §2 subsection and change file list complete? |
|-------|------------|-----------------------------------------------|
| Backend | Yes (if service/controller/logging or error handling after confirmed cause) | Yes |
| Frontend (config UI + view screen) | Yes (permission group management / hierarchy consumer) | Yes |
| DB | Yes (migration applicability verification; possible migrate script or setup.sh documentation fix after confirmed cause) | Yes |
| Contract / Spec | Only if API behavior or documented error codes change after fix | TBD after diagnosis |
| Cursor tools (skills, specs) | Only if permission model or screen IDs change | TBD after diagnosis |

**Domain pattern §3.2 (permission or screen-access change):** Backend access checks, frontend menu/permission UI, contract permission mapping, auth skills — **included** in analysis; concrete doc updates only if behavior changes.

**§2.4 (search/filter UI):** N/A — not applicable.

### Planned change file list (expected change targets)

**(Planned at authoring. Implementing agent (Step 4) confirms or amends after diagnosis and fix.)**

#### Frontend

- `frontend/src/components/PermissionGroupManagement/PermissionGroupPanel.js` (diagnostic logging or error surfacing **if** Phase 0 implicates client)
- `frontend/src/config/runtimeApi.js` / `frontend/public/runtime-config.js` (**if** base URL misconfiguration confirmed)

#### Backend

- `backend/src/main/java/com/logmng/service/PermissionGroupService.java` (DEBUG diagnostic around list/loadAllowedScreens **if** Phase 0 implicates server)
- `backend/src/main/java/com/logmng/controller/PermissionGroupController.java` (diagnostic boundary **if** needed)
- `backend/src/main/java/com/logmng/config/ScreenAccessInterceptor.java` (**if** access denial logic is implicated after evidence)
- Tests under `backend/src/test/java/...` for any behavior change after root cause is fixed

#### DB

- `backend/src/main/resources/db/setup.sh` — **step 4h** (after 4g, before step 5 init-data): runs idempotent column migrations in order — `migrate-permission-group-screen-scope.sql`, `migrate-permission-group-screen-functions.sql`, `migrate-permission-group-screen-decrypt.sql`, `migrate-permission-group-screen-scope-team.sql` on DB A with `search_path` `${SCHEMA_SYS}, ${SCHEMA_PB}, public` (same as `SP_APP`).
- `backend/src/main/resources/db/check-db.sh` — **section 6b**: if `${SCHEMA_SYS}.permission_group_screen` exists, verifies columns `scope`, `read`, `write`, `approve`, `decrypt`; prints remediation (rerun `setup.sh` or apply the four migrate files manually in order).
- `backend/DB_SETUP_GUIDE.md` — **마이그레이션 적용 순서**: documents `setup.sh` 4h and manual apply note for operators who cannot re-run full `setup.sh`.
- (Reference, unchanged SQL sources) `migrate-permission-group-screen-*.sql` — idempotent; listed above for 4h.

#### Contract / Spec

- `docs/contract.md`, `docs/api-definition.md`, `specs/permission-group-hierarchy.spec.yaml` — **only if** response or error contract changes post-fix

### Cursor tool update targets

- **If** screen IDs or permission rules change: `.cursor/skills/auth-permission-domain/SKILL.md`, `.cursor/skills/api-permission-map/SKILL.md`, `specs/permission-group-hierarchy.spec.yaml`
- **If** DB layout or setup order changes: `.cursor/skills/db-domain/SKILL.md`

## 3. Test approach

### Test case list (required)

**Domain note:** Permission/access APIs — align verification with `docs/contract.md` and `specs/permission-group-hierarchy.spec.yaml`; trace `/api/permission-groups` through `ScreenAccessInterceptor` and `PermissionGroupController`.

| ID | Scope | Type | Scenario (input / condition) | Expected result | Verification |
|----|-------|------|------------------------------|-----------------|----------------|
| TC-01 | DB | Normal | On DB A (sys schema), query `information_schema.columns` for `permission_group_screen` | Columns include `scope`, `read`, `write`, `approve`, `decrypt` | Manual / psql (or documented integration) |
| TC-02 | DB | Edge | Legacy DB missing one of the columns (repro fixture or staging) | Before fix: `GET /api/permission-groups` fails with SQL error in logs; after applying idempotent migrate files: columns present and list succeeds | Integration |
| TC-03 | Backend | Normal | Authenticated admin, allowed screen, `GET /api/permission-groups` | 200, JSON body matches contract §14 list shape | Integration (curl with session cookie) or unit tests for service with test DB |
| TC-04 | Backend | Exception | User without required screen access | 403 (or contract-defined denial), no stack trace from missing column | Integration |
| TC-05 | Frontend | Normal | Open permission group management as allowed user | Groups table loads or clear error; no unhandled exception | Manual / browser or `npm test` if UI assertion added |
| TC-06 | Integration | Diagnostic | Reproduce reported failure with DEBUG logs enabled | Logs show branch: interceptor vs SQLException vs network | Manual — capture log excerpt for §6 |
| TC-07 | DB | Normal | Compare migration inventory: `migrate-permission-group-screen-*.sql` + `migrate-main-to-pb-feplog-java-fw-imagelog.sql` vs. scripts recorded as run on server | Gaps documented; optional `setup.sh` doc update tracked | Manual checklist |

### Test scenarios

#### Scenario 1: Schema verification (migration applicability)

1. Connect to application **sys** database with same `search_path` as runtime.
2. Run TC-01 column query; optionally check constraint `chk_permission_group_screen_scope` includes `team` if needed.
3. If any column missing, run the corresponding idempotent `migrate-permission-group-screen-*.sql` files in dependency order (scope → functions → decrypt → scope-team as historically required), then re-run TC-01 and TC-03.

#### Scenario 2: End-to-end screen entry

1. Log in as user with permission-group management access.
2. Open permission group management (or hierarchy screen that hosts the panel).
3. Confirm network: `GET /api/permission-groups` status and body; confirm UI matches success criteria.

### Test data

- Use existing admin / `ADMIN_EXT` or system admin from `init-data.sql` per environment docs.
- No new PII in requirement doc; test users are documented in DB setup guides.

### Test environment

- Frontend: per `docs/contract.md` (e.g. `http://localhost:3001`)
- Backend: per `docs/contract.md` (e.g. `http://localhost:9200`)
- Database: PostgreSQL, DB A / `SCHEMA_SYS` per multi-datasource configuration

### 3.5 Browser automation verification (optional)

- **Applicable TCs**: TC-05, Scenario 2.
- **Procedure**: Navigate → login → open permission group management → snapshot; capture Network for `/api/permission-groups`.
- **Reference**: `docs/workflow/BROWSER-AUTOMATION-VERIFICATION-POLICY.md`

## 4. Checklist

### Frontend verification

- [ ] API parameters validated
- [ ] UI behavior confirmed
- [ ] Error handling verified

### Backend verification

- [ ] API test cases written and run
- [ ] Logs checked
- [ ] Performance checked (if applicable)

### Integration

- [ ] End-to-end flow tested
- [ ] Edge cases tested

### Documentation

- [x] Requirement doc completed (§5/§6 filled 2026-03-20)
- [x] Code comments added (if applicable) — diagnostic class Javadoc / DB script headers as implemented

## 5. Test results

### Test run date

- **2026-03-20** (QA Step 5 verification)

### Test results

| Check | Command / scope | Result | Notes |
|-------|-----------------|--------|--------|
| Backend unit/integration tests | `cd backend && mvn test` | **Pass** (exit 0) | Re-validated at QA; no regressions observed. |
| DB check — §6b `permission_group_screen` | `bash backend/src/main/resources/db/check-db.sh` (repo root, default env: `DB_HOST=localhost`, `DB_PORT=5432`, `SCHEMA_SYS=public`, `DB_A_NAME=logmng`) | **Pass** (exit 0) | Section **6b** printed: `✅ permission_group_screen: scope, read, write, approve, decrypt 존재`. |
| Optional health (backend unchanged in latest DB-only batch) | `curl -s -o /dev/null -w "%{http_code}" http://localhost:9200/api/health` | **200** | Optional per `verify.md` for shell/DB-only scope; backend responded OK at verification time. |

**TC mapping (§3):**

- **TC-01 / Scenario 1**: Satisfied by `check-db.sh` **6b** on the verification host (catalog-level column presence for `permission_group_screen`).
- **TC-02–TC-07**: Not fully automated in this run; original **incident root cause** (if any) still requires on-site logs / Network tab per §2 diagnostic phase. **Operator mitigation path** (4h migrations + 6b validation) is verified by script behavior and code review.

**Commands (recorded):**

```bash
cd backend && mvn test
# exit 0

cd /path/to/repo && bash backend/src/main/resources/db/check-db.sh
# exit 0; confirm block "6b. permission_group_screen 필수 컬럼" shows ✅

curl -s http://localhost:9200/api/health
# 200 + JSON (optional)
```

## 6. Error remedy result (cause and action) — for error/bug fix requirements only

- **Requirement ID**: `20260320-permission-group-screen-entry-error-migration-check`
- **Root cause (product / schema)**: `PermissionGroupService.loadAllowedScreens` expects columns `scope`, `read`, `write`, `approve`, `decrypt` on `permission_group_screen` (sys datasource). **Legacy databases** that never ran the historical column migrations can hit SQL errors at list time — a **documented** failure mode addressed by idempotent migrations and setup order.
- **Root cause (single reported incident)**: **Not confirmed** in this QA run (no production/staging log bundle). Use DEBUG flag `app.diagnostic.permission-group-screen` and §2 Phase 0 to distinguish **403 (interceptor)** vs **500 (SQL)** vs **client/config** if the issue recurs.
- **Actions taken**: (1) Backend DEBUG diagnostics behind `app.diagnostic.permission-group-screen` (`PermissionGroupScreenDiagnosticLog`, `PermissionGroupService`, `PermissionGroupController`, `ScreenAccessInterceptor`, `application.yml`). (2) **`setup.sh` step 4h** — four `migrate-permission-group-screen-*.sql` files before init-data/5a. (3) **`check-db.sh` section 6b** — required-column check with remediation text. (4) **`DB_SETUP_GUIDE.md`** — migration order for operators.
- **Result**: `mvn test` pass; `check-db.sh` pass with **6b** ✅ on verification DB; optional `/api/health` 200.
- **Completed**: **2026-03-20** (QA verification + §5/§6)

---

**Author**: Requirements (subagent)  
**Date**: 2026-03-20  
**Status**: Verification recorded (Step 5 — QA); incident-specific diagnosis remains environment-dependent if symptoms persist
