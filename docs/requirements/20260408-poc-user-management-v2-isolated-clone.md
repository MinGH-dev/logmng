# 20260408 - Isolated PoC User Management (UM v2 clone) for HR integration migration testing

## 1. User requirement

### Requirement description

Deliver a **separate Proof-of-Concept (PoC) User Management screen** created by **cloning** the existing User Management v2 UI (`frontend/src/components/UserManagement/UserManagement.js` and closely related assets: styles, tests, services wired from that screen), with a **new sidebar menu entry** and **distinct view / screen identifier**. The PoC screen exists **only** to support **HR / personnel integration PoC migration testing** (validating replica data, snapshot-scoped views, and future mapping UX) **without** coupling those experiments to production user-administration flows.

**Zero production impact (normative definition for this requirement)**

- PoC code paths **must not** perform **writes** to production identity or authorization stores unless a **future** requirement explicitly scopes such writes, obtains product + Security approval, and documents separate TCs. For this requirement, **writes to `app_user`, application permission / screen / tree tables, and any production user-management mutation APIs are out of scope and forbidden** from the PoC UM implementation.
- PoC UM **must** prefer **read-only** backends that source from **PoC-scoped replica data** (e.g. `ext_department`, `ext_employee` under existing HR Sync PoC rules) or **explicit PoC-only HTTP namespaces**; it **must not** call **`/api/user-management-v2/**` mutating methods** (POST/PATCH/DELETE for departments, users, etc.) for “real” effects.
- The **non-PoC** User Management v2 view (`user-management-v2`), **legacy** user management (`UserManagementLegacy` / `user-management`), routes, permissions, and menus **remain unchanged** in behavior and URL patterns except for **additive** registration of the new PoC entry (no removal or redirection of existing entries).

### User scenario

1. A PoC operator (see §2.1) has the HR Sync PoC feature enabled (`HR_SYNC_POC_ENABLED` / `REACT_APP_HR_SYNC_POC_UI` per existing project conventions) and has been granted access to the **new** PoC UM screen permission.
2. The operator opens the app and selects the **PoC User Management** (or similarly labeled) menu item; the app navigates to view id **`user-management-v2-poc`** (exact label text may follow admin display-name rules).
3. The UI presents a layout and interactions **cloned from** UM v2 where applicable (tree, filters, tables), but data and actions are served **only** via PoC-isolated APIs (§2).
4. The operator exercises migration-testing flows (e.g. compare replica department tree vs snapshot employees, exercise read-only drill-down); **no** production users, departments, or permission assignments change.
5. Users **without** the PoC UM screen permission or with PoC globally disabled **cannot** access the screen or its APIs (consistent denial codes and UX).

**Problem**: Today, UM v2 is wired to **production** user-admin APIs. Reusing the same screen for HR PoC migration testing risks accidental calls to production mutations or blurs permission boundaries. A **dedicated** PoC clone with **strictly separated** backend surface is required.

### Expected outcome

- **Frontend**: New PoC UM component(s) and view routing for **`user-management-v2-poc`**; new menu leaf under the same **admin / user-management** grouping policy as existing PoC items, with **separate** `allowedScreenIds` entry (**not** reusing `user-management-v2` alone unless product explicitly chooses alias — default is a **new** screen id).
- **Backend**: PoC UM reads through **dedicated** read-only (and optionally no-op stub) endpoints under a PoC namespace — **recommended: Option (A)** in §2 — so **no** shared mutation service layer with production UM v2 for PoC-driven actions.
- **Contract / specs**: New screen id, API paths, and permission behavior documented under **`docs/contract.md`**, **`docs/api-definition.md`**, and the **authoritative YAML** (extend `specs/hr-sync-poc.spec.yaml` and/or add `specs/poc-user-management-v2.spec.yaml` per Contract owner) with **DOC-CODE-SYNC** (`docs/workflow/DOC-CODE-SYNC.md`).
- **Security / audit**: Access limited to agreed principals; PII minimization for replica fields; sensitive actions either absent or **stubbed** with no backend side effects (§2.1).

## 2. Design

### 2.1 Security review (optional; when PII / decryption / access control)

- [ ] Security review performed (check when Security subagent has reviewed)

**Risks**

- **PII**: `ext_employee` / joined replica data may include identifiers, names, emails, job attributes. The PoC UM clone increases **exposure surface** versus a minimal aggregate preview if it shows rich grids and trees.
- **Permission confusion**: If the new screen id is omitted from permission matrices or incorrectly aliased to `user-management-v2`, operators may gain PoC UI without review, or production UM permission may unintentionally unlock PoC UM.
- **Session fixation / deep link**: Direct navigation to PoC routes must still enforce **session + screen access** server-side, not UI-only hiding.

**Acceptance / recommendations**

- **Who may access PoC UM**: Align with **existing HR Sync PoC** audience unless product specifies narrower scope — default: **system administrator** and/or principals that already pass **`hr-sync-poc`**-class checks; **register** new API prefixes in **`ScreenAccessInterceptor`** (or successor) with screen id **`user-management-v2-poc`** (or explicit mapping table entry). Deny with **403** and contract-aligned `code` values.
- **Authentication**: Valid session required for all PoC UM APIs; unauthenticated → **401**.
- **Data minimization**: PoC UM DTOs expose **only** fields needed for migration testing; mask or omit email / full employee number if policy requires (mirror §2.1 patterns in `docs/requirements/20260408-hr-sync-poc-snapshot-list-and-sample-data.md`).
- **No secrets**: Do not return upstream credentials or raw ETL payloads in APIs.
- **Audit**: **Read-only** PoC list/detail access may log at **INFO** metadata level (user id, screen id, snapshot id) without logging full row payloads; **stub** actions must **not** emit production-audit events that imply a real mutation. If product requires explicit `user_activity_log` rows for PoC reads, that must be a **separate** approved requirement (out of scope here).

### Technical design

#### Problem analysis

1. **UI parity vs safety**: Full UM v2 parity implies create/update/delete paths; those **conflict** with zero production impact unless every action is stubbed or mapped to replica-only sandboxes.
2. **Shared code risk**: Importing production UM services on the frontend for PoC accidentally wires **production base URLs**. The PoC fork **must** use **distinct** API modules (e.g. `pocUserMgmtService` vs `userManagementV2Service`).
3. **Permission model**: `ORDERED_SCREEN_IDS`, `menuTree.js`, `AppSidebar`, and backend screen registry **must** include a **new** stable id **`user-management-v2-poc`** so permission gating mirrors **`user-management-v2`** **policies** but remains **independent** in assignment.
4. **Feature flag**: PoC UI should remain gated by existing **`REACT_APP_HR_SYNC_POC_UI`** / backend **`HR_SYNC_POC_ENABLED`** in addition to screen permission, consistent with `hr-sync-poc` view behavior.

#### Diagnostic phase (mandatory for error/bug fix only)

*(Not applicable — new feature requirement.)*

#### Solution approach — backend strategy options (pick one)

| Option | Summary | Pros | Cons |
|--------|---------|------|------|
| **(A) Recommended** | New read-only (+ stub) APIs under **`/api/hr-sync/poc/user-mgmt/**`** (or sibling namespace under existing PoC base path) returning DTOs shaped **like** UM v2 where useful, backed **only** by `ext_*` reads and existing snapshot semantics. **Mutating buttons** either hidden or call **no-op / 501 / explicit stub** endpoints that **never** touch `app_user` or permission tables. | Clearest boundary; reuses PoC flag + interceptor patterns; minimal risk of accidental production calls. | Requires new contract work; partial UX parity if stubs replace real creates. |
| **(B)** | Duplicate Spring controllers (feature-flagged) that **only** read `ext_*`, registered alongside production controllers, path prefix distinct from `/api/user-management-v2`. | Familiar layering | Higher risk of copy-paste drift; must prove **no** delegate to production write services. |
| **(C)** | **UI-only** clone wired **exclusively** to existing PoC snapshot/preview/list employees APIs (no new user-mgmt-shaped API). | Smallest backend addition | **Limited parity** with UM v2 (tree vs grid semantics may diverge); may not satisfy migration testers expecting v2 layout. |

**Selected approach for implementers: Option (A)** — implement PoC UM against **new** PoC-scoped endpoints documented in contract/spec; **do not** call production **`/api/user-management-v2/**`** from the PoC bundle except if a future requirement explicitly adds a **read-only passthrough** sub-resource that is proven side-effect free (not in scope for initial delivery).

**Frontend:**

- Add a **cloned** entry component (e.g. `UserManagementPoc.js` + scoped CSS) derived from `UserManagement.js`, replacing imports to use **PoC-only** services and **disabling or stubbing** mutating controls per §2 final API set.
- **`frontend/src/App.js`**: Register **`currentView === 'user-management-v2-poc'`** rendering the PoC component; **do not** alter conditions for `user-management-v2` or legacy `user-management`.
- **`frontend/src/constants/menuTree.js`**: Add menu leaf with screen id **`user-management-v2-poc`**; mirror placement near **`hr-sync-poc`** / admin user-management group per product preference.
- **`ORDERED_SCREEN_IDS`** (same file or imported constant): Insert new id in appropriate order; **must not** reuse `user-management-v2` slot.
- **`AppSidebar.js`** (if special cases exist for admin sections): Apply same visibility rules as other PoC items — **separate** id string for checks.
- **Deep link** (optional): If product wants bookmarkable URL, follow the pattern used for `/user-management/hr-sync-poc` — e.g. `/user-management/poc-v2` — **without** changing existing paths.
- **Tests**: Clone/adapt `UserManagement.test.js` (or subset) for PoC component; mock PoC APIs only.

**Backend:**

- Add controller handler(s) under **PoC** namespace for **tree snapshot**, **user list**, and **preview-compatible** aggregates as needed by the cloned UI; **read-only** queries on `ext_department` / `ext_employee` (and existing snapshot id validation).
- **Stub** endpoints for actions that would mutate in real UM v2: return **400/501** with explicit `code` (e.g. `POC_ACTION_NOT_PERSISTED`) **or** success payload that states **no-op** — product + Contract must pick one pattern and document it.
- **`ScreenAccessInterceptor`**: Map new API prefix to screen id **`user-management-v2-poc`** (or dual-check with `hr-sync-poc` if product chooses shared PoC gate — document chosen rule in §2 and contract).
- **`WebConfig`** / CORS / path registration unchanged except additive patterns.

**DB:**

- **No** new production tables required for **read-only** PoC UM if existing `ext_*` + snapshot columns suffice; otherwise follow **`docs/requirements/20260408-external-hr-user-sync-security-db-design.md`** and HR PoC migrations — **only** `ext_*` / PoC tables, never `app_user` for PoC seeding.

**Contract / spec:**

- Add **`user-management-v2-poc`** to screen id enumerations and permission docs; extend **`specs/hr-sync-poc.spec.yaml`** (preferred single PoC authority) **or** add **`specs/poc-user-management-v2.spec.yaml`** if Contract owner splits concerns; update **`docs/contract.md`** and **`docs/api-definition.md`** in the **same change** as code.

### Affected scopes and change targets (verification)

| Scope | Affected? | §2 subsection and change file list complete? |
|-------|------------|-----------------------------------------------|
| Backend | Yes | Yes |
| Frontend | Yes | Yes |
| DB | Optional | Only if new PoC columns/tables needed for tree parity |
| Contract / Spec | Yes | Yes |
| Cursor tools (skills, specs) | Optional | Update `api-permission-map` notes if needed |

### Planned change file list (expected change targets)

#### Frontend

- `frontend/src/components/UserManagement/UserManagementPoc.js` (new; working name — implementer may adjust if Consistency requires)
  - PoC clone of UM v2; PoC service imports; mutation UX gated/stubbed.
- `frontend/src/components/UserManagement/UserManagementPoc.css` (optional if split from shared `UserManagement.css`)
- `frontend/src/services/pocUserManagementService.js` (new; calls `/api/hr-sync/poc/user-mgmt/...` only)
- `frontend/src/App.js` — view branch for `user-management-v2-poc`; optional history helper for deep link
- `frontend/src/constants/menuTree.js` — new screen id + label key
- `frontend/src/components/AppSidebar.js` — if explicit screen id checks exist for PoC group
- Tests: `UserManagementPoc.test.js` (new)

#### Backend

- `com.logmng.controller.HrSyncPocController` or **`UserManagementPocController`** under `.../hr-sync/poc/user-mgmt` package (implementer picks; document in spec)
- Service layer: read-only queries + stub handlers
- DTOs for tree nodes and user rows (PoC-specific or shared read-only shapes)
- `ScreenAccessInterceptor.java`, `WebConfig.java` — path → **`user-management-v2-poc`**
- Init / permission SQL: **`init-data.sql`** / migration for **screen** row **`user-management-v2-poc`** if `screen` table drives valid ids (verify against existing `user-management-v2` seed pattern)
- Tests: controller + service tests for new endpoints; negative tests proving **no** `app_user` writes on PoC paths (assert mocks / transaction rollback patterns as appropriate)

#### DB

- Migrations only if §2 tree parity requires additional `ext_*` fields or indexes; otherwise none.

#### Contract / documentation

- `docs/contract.md` — screen id + API overview
- `docs/api-definition.md` — request/response summaries
- `specs/hr-sync-poc.spec.yaml` (extend) **or** `specs/poc-user-management-v2.spec.yaml` (new)
- `docs/workflow/DOC-CODE-SYNC.md` compliance when editing the above

## 3. Test approach

### Test case list (required)

**Scope tags** for handoff: each TC lists a **Scope** column value from: **Requirements**, **Security**, **Contract**, **Backend**, **Frontend**, **DB**, **QA** (use **multiple** Scope entries in the scenario text if several parties must attest, e.g. `Backend; QA`).

| ID | Scope | Type | Scenario (input / condition) | Expected result | Verification |
|----|-------|------|------------------------------|-----------------|---------------|
| TC-01 | Requirements | Normal | Product confirms **Option (A)** and zero-impact definition in §1 | Implementers follow PoC-only APIs; no production UM mutations from PoC bundle | Review checklist / sign-off in §4 |
| TC-02 | Contract | Normal | Spec lists **`user-management-v2-poc`** and all **`/api/hr-sync/poc/user-mgmt/**`** paths | YAML + `contract.md` + `api-definition.md` align with implementation; **DOC-CODE-SYNC** satisfied | Manual doc diff + Review |
| TC-03 | Security | Normal | Authenticated user **without** `user-management-v2-poc` (and without applicable admin override) calls PoC UM API | **403** with contract `code` | Integration / unit (mvn) |
| TC-04 | Security | Normal | Unauthenticated request to PoC UM API | **401** | mvn test |
| TC-05 | Security | Normal | PoC globally disabled (`HR_SYNC_POC_ENABLED` false) | **403** `POC_DISABLED` or route not registered per existing PoC policy | mvn test |
| TC-06 | Backend | Normal | `GET` tree/read endpoint for valid `snapshotId` | **200** + DTO from `ext_*` only; response schema matches spec | Unit / integration |
| TC-07 | Backend | Normal | Stub mutation endpoint (if exposed) invoked | **No** `INSERT`/`UPDATE`/`DELETE` on `app_user` or app permission tables (verify via test doubles / DB assert helpers) | Integration |
| TC-08 | Backend | Edge | Invalid or unknown `snapshotId` | **404** or empty payload per spec (documented choice) | mvn test |
| TC-09 | Frontend | Normal | Render PoC UM with mocks | Tree/table render without calling `user-management-v2` base URL | npm test |
| TC-10 | Frontend | Normal | Menu shows PoC UM item only when **PoC UI flag true** and user allowed screen contains **`user-management-v2-poc`** | Item hidden otherwise | npm test |
| TC-11 | Frontend | Regression | `user-management-v2` view still renders **`UserManagement`** (non-PoC); legacy route unchanged | No regression in existing tests | npm test (existing + new) |
| TC-12 | DB | Normal | If migration added | Applies on clean DB; **only** `ext_*` / PoC tables touched | QA / migrate script run |
| TC-13 | QA | Integration | End-to-end: login as permitted PoC operator → open PoC UM → load snapshot data | Visible read-only data; production user count unchanged (spot-check) | Manual or browser (§3.5) |
| TC-14 | QA | Regression | Production UM v2: create/department flows (if env allows) | Unchanged behavior vs pre-change baseline | Manual regression |

### Test scenarios

#### Scenario 1: Permission isolation

1. Grant user A **`user-management-v2`** only; deny **`user-management-v2-poc`**.
2. User A opens production UM v2 — **success**.
3. User A navigates to PoC UM deep link or API — **denied**.

#### Scenario 2: PoC disabled

1. Set `HR_SYNC_POC_ENABLED` false.
2. Attempt PoC UM UI and API — disabled message and **403** on API per TC-05.

### Test data

- Reuse HR Sync PoC multi-snapshot sample data from `docs/requirements/20260408-hr-sync-poc-snapshot-list-and-sample-data.md` where applicable.
- Ensure at least one **`user-management-v2-poc`** permission row exists for a test permission group (SQL per implementer, documented in §5 commands when available).

### Test environment

- Frontend: `http://localhost:3001` (or per contract)
- Backend: `http://localhost:9200`
- Database: PostgreSQL (per project)

### 3.5 Browser automation verification

- **Applicable TCs**: TC-10, TC-11, TC-13, TC-14 (UI visibility, non-regression, E2E happy path, regression).
- **Procedure (example)**:
  1. `browser_navigate` → login as PoC-eligible operator.
  2. Open sidebar → select PoC User Management → `browser_snapshot` confirms view marker / heading distinct from production UM v2.
  3. Select snapshot (if UI provides) → confirm table population.
  4. Log in as user **without** PoC screen → confirm menu item absent.
- **Reference**: `docs/workflow/BROWSER-AUTOMATION-VERIFICATION-POLICY.md`.

### DOC-CODE-SYNC

- When adding **`user-management-v2-poc`** and PoC UM APIs, update **`docs/contract.md`**, **`docs/api-definition.md`**, and authoritative **`specs/*.spec.yaml`** in the **same PR/task** as code (`docs/workflow/DOC-CODE-SYNC.md`). Implementer **must** list screen id in permission / screen-id tables used by `ScreenAccessInterceptor` and permission-group UIs.

## 4. Checklist

### Frontend verification

- [ ] PoC component uses **only** PoC API module(s)
- [ ] `user-management-v2` and legacy routes **unchanged**
- [ ] Error handling for `POC_DISABLED` / 403 aligned with HrSync PoC

### Backend verification

- [ ] New paths registered in `ScreenAccessInterceptor`
- [ ] Tests prove read-only + stub behavior (TC-07)
- [ ] No accidental wiring to `UserManagementV2Service` mutation methods

### Integration

- [ ] Permission matrix includes **`user-management-v2-poc`**
- [ ] E2E PoC flow (TC-13) recorded in §5

### Documentation

- [ ] Requirement doc completed (this file)
- [ ] TOPIC-INDEX: after verification complete, add index line — `20260408-poc-user-management-v2-isolated-clone | Isolated UM v2 PoC clone + menu for HR migration testing; read-only PoC APIs; no production user/permission writes` — under **permission | user-management | PoC** (or **misc** if no section fits); run `./scripts/generate-requirements-index.sh` as needed

## 5. Test results

*(To be filled by QA after implementation.)*

### Test run date

- TBD

### Test results

#### Frontend

- TBD

#### Backend

- TBD

**Outcome:**

- TBD

---

**Author**: Requirements (subagent)  
**Date**: 2026-04-08  
**Status**: In progress
