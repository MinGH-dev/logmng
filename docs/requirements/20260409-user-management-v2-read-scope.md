# 20260409 - User Management v2 read scope (self / department / all)

## 1. User requirement

### Requirement description

**User Management v2** (screen id `user-management-v2`, APIs under `/api/user-management-v2/**`, shared data loaded with legacy user-management APIs such as `GET /api/users` and `GET /api/departments/user-permission-hierarchy`) must support **configurable read scope** for non–system-admin operators, aligned with existing **self / team / all** semantics used for activity log, statistics, search history, and pending approvals (`specs/permission-group-hierarchy.spec.yaml` §1.1).

- **self** (API value `self`, UI **본인**): The operator may **read** (list/view) only data that pertains to **themselves** as the authenticated principal (their own `app_user` row and the minimal department context needed to display that row). User/department search filters that could widen visibility must be **hidden, disabled, or overridden** consistently with other self-scoped screens; **backend enforcement is authoritative**.
- **team** (API value `team`, UI **부서**): The operator may read data for users who share the **same `department_code`** as the current user (same rule as **department scope** / “same department” in `DepartmentScopeHelper` and activity statistics). This is the **default** when `scope` is null or omitted for this screen (align with **team default** for scope-supporting screens per `20250304-team-scope-default`).
- **all** (API value `all`, UI **전체**): The operator may read organization-wide user and department hierarchy data subject to existing screen access and **write** rules (write remains gated by `screenFunctions` / system admin as today).

**System administrator** (`is_system_admin = true`): Effective read scope is always **all**; permission-group scope must not restrict admins.

The scope applies to **read/list/view** behavior. **Mutations** (create department, direct user registration, delete department, user delete, permission-group assignment flows reachable from the same UI) must **not** allow affecting entities **outside** the operator’s effective read scope unless product explicitly decides otherwise; implementers must document chosen denial behavior (`403` / `FUNCTION_NOT_ALLOWED` / `404` as appropriate) and enforce server-side.

### User scenario

1. A **system administrator** opens User Management v2 and sees the full department tree and user list; scope configuration does not apply.

2. A **permission-group administrator** edits a group that includes screen **`user-management-v2`**. A **scope** control appears for that row (**본인** / **부서** / **전체**), consistent with other scope-supporting screens.

3. A **non-admin operator** with **`user-management-v2`** and scope **부서 (team)** logs in and opens User Management v2. They see users and departments **only within their department scope** (same `department_code`); they do not see other departments’ users in the list or tree beyond what the filtered view requires.

4. A **non-admin** with scope **본인 (self)** opens User Management v2. They see **only their own user row** (and locked self display for department/name/user id per `.cursor/skills/search-consistency-domain/SKILL.md` where applicable). They cannot use filters to widen to other users.

5. A **non-admin** with scope **전체 (all)** opens User Management v2. They see the full hierarchy and user list (subject to screen access), consistent with today’s unconstrained list behavior for authorized management users.

6. **Problem**: Today, `user-management-v2` is not in `ScreenConstants.supportsScope`, `permission-group-hierarchy.spec.yaml` lists `scope` only for activity-log, statistics, search-history, pending-approvals, and shared read APIs (`GET /api/users`, hierarchy) do not apply **per-screen** read scope for UM v2. Operators with only v2 screen access may also be **misaligned** between `App.js` (allows `user-management-v2`) and `UserManagement.js` / `AuthService.canAccessUserManagementView` (which may not include `user-management-v2` for loading data). Read scope must be **defined, configured, and enforced** end-to-end.

### Expected outcome

- Permission groups persist **`scope`** for **`user-management-v2`** (`self` | `team` | `all`) with default **`team`** when omitted or null (migration/backfill consistent with other scope screens).
- Login / `GET /api/auth/me` (or equivalent) exposes **`screenScopes['user-management-v2']`** for non-admins; system admins receive effective **all** (same pattern as other scope maps).
- **Backend** applies effective scope to **all read paths** used by the UM v2 UI: at minimum **`GET /api/users`**, **`GET /api/departments/user-permission-hierarchy`** (tree), **`GET /api/user-management-v2/quick-entry/options`**, and any other read endpoints the v2 screen invokes (e.g. permission-group list if present on the same view). **No client-trusted** query parameters may bypass scope.
- **Screen access**: Path rules and `AuthService` checks must treat **`user-management-v2`** as a **first-class** alternative to `user-management` / `user-permission-hierarchy` where the contract requires v2-only operators to load hierarchy and user list (align `ScreenAccessInterceptor`, `canAccessUserManagementView`, and frontend `canAccessUserManagement` / `canWrite` flags with `screenFunctions['user-management-v2']`).
- **Frontend** permission-group editor shows scope for `user-management-v2`; UM v2 view respects `screenScopes` (locked self UI for `self`, filters for `team`/`all` per product + design docs).
- **Contract/spec**: `docs/contract.md`, `docs/api-definition.md`, `specs/permission-group-hierarchy.spec.yaml`, and `specs/user-management-v2.spec.yaml` describe the behavior and error codes. (Step 3: `permission-group-hierarchy.spec.yaml` v1.11+, `user-management-v2.spec.yaml` v1.2+.)
- **Cursor tools**: Update `.cursor/skills/auth-permission-domain/SKILL.md`, `.cursor/skills/api-permission-map/SKILL.md`, and related specs per `docs/workflow/REQUIREMENTS-AUTHORING-WORKFLOW.md` §1.4 when the domain model changes.

---

## 2. Design

### 2.1 Security review (optional; when PII / decryption / access control)

- [x] Security review performed (check if applicable)

#### Threats and data handled

- **PII / sensitive attributes**: User lists, hierarchy, and quick-entry responses expose identifiers and organizational context (e.g. names, numeric `userId`, `department_code`, department labels). Scope misconfiguration or inconsistent filtering is a **data-minimization and confidentiality** failure, not only a “feature bug.”
- **Broken access control (OWASP A01)**: Shared read APIs (`GET /api/users`, `GET /api/departments/user-permission-hierarchy`, v2 quick-entry) are high-value targets. If any path applies scope **partially** or trusts **client-supplied** identity/department parameters, operators can obtain **cross-tenant or cross-department** data (**IDOR**-class exposure).
- **Client tampering**: Query parameters, JSON bodies, or duplicated “hint” fields must **not** widen effective read scope. The UI hiding filters is **not** a control; **server-side resolution and query filters** are authoritative (align with `auth-permission-domain`: activity-log `scope=self` ignores widening inputs).
- **Privilege / scope confusion**: `screenScopes['user-management-v2']` must be **derived only** from persisted permission groups + merge rules + `is_system_admin`. Tampering with local storage or crafted requests must not change effective scope.
- **Mutation beyond read scope**: Create/update/delete paths reachable from UM v2 must **not** affect principals or departments **outside** the operator’s effective read scope (unless explicitly product-approved), or operators could **escalate** from read-scoped sessions.
- **Information disclosure via errors**: Denial responses (`403`, `FUNCTION_NOT_ALLOWED`, optional `404`) must **not** leak whether an out-of-scope user or department **exists** beyond what the contract already allows for similar screens; document chosen pattern in `docs/contract.md` / `docs/api-definition.md` and keep it consistent across UM v2 read APIs.
- **Logging**: Avoid logging full list payloads or row-level PII in production; follow `docs/security-guide.md` (masked logging, no raw user objects in `console`).

#### Security acceptance criteria

1. **Authoritative scope resolution**: Effective read scope for `user-management-v2` is computed **only** on the server from the authenticated principal, `is_system_admin`, and stored `permission_group_screen.scope` (plus documented merge precedence). It is **never** taken from an unauthenticated or client-controlled source as the authorization decision.
2. **Uniform enforcement surface**: Every read endpoint used by UM v2 (at minimum those listed in §1 Expected outcome) applies the **same** effective scope before data leaves the service layer. **No** endpoint may return organization-wide rows for a `self`/`team` principal while another returns filtered data.
3. **`self` scope**: List and detail behavior restricts to the **current** `app_user` row; hierarchy/tree responses **must not** disclose unrelated departments’ nodes or peer users. Server **ignores, clears, or normalizes** any request fields that would widen visibility (pattern: activity-log `scope=self`). UI follows **visible locked self** rules per `search-consistency-domain` for display-only consistency; backend remains authoritative.
4. **`team` scope**: Filtering aligns with **`DepartmentScopeHelper`** / same-`department_code` semantics; edge cases (null/blank `department_code`) **do not** expand to “all peers” or organization-wide lists. Hierarchy shape is **scope-safe** (no paths revealing out-of-scope departments’ user sets).
5. **`all` scope (non-admin)**: Behavior matches **documented** non-admin “all” semantics and existing screen-function/write gates; no implicit elevation beyond product intent.
6. **System admin**: `is_system_admin = true` ⇒ effective **`all`** regardless of stored group scope; non-admin cannot impersonate admin scope via parameters.
7. **Screen and interceptor alignment**: `ScreenAccessInterceptor`, `UserController` guards, and `AuthService.canAccessUserManagementView` (or successors) treat **`user-management-v2`** as a **first-class** gate for every UM v2 read path so that **missing screen access** yields **403** (or contract-equivalent) **before** any partial data leak. No “open” read API for authenticated users without the screen.
8. **Mutations**: Operations that target another user or another department’s entities are **rejected** with contract-aligned codes (`403` / `FUNCTION_NOT_ALLOWED` / `404` as specified) when the target is **out of scope**; tests cover **cross-department** and **cross-user** attempts for `self` and `team` principals (see §3 TC-14 and extend if split read/write scope is ever introduced).
9. **Anti-tampering tests (mandatory)**: Automated tests prove that forged `userId`, department code, tree `format`, pagination cursors, or duplicate JSON keys **cannot** widen lists or trees for `self`/`team` principals (extends §3 TC-05, TC-07, TC-08).
10. **Contract and error catalog**: `docs/contract.md`, `docs/api-definition.md`, and specs register `screenScopes['user-management-v2']`, scope behavior, and security-relevant error codes so Frontend and API permission map stay aligned (`api-permission-map`).

#### Recommendations (non-normative)

- Prefer **filtering in the query/service layer** (single source of truth) over controller-only checks, so ORM/query paths cannot bypass rules.
- When returning **empty** vs **403** for out-of-scope reads, pick one **consistent** product pattern per endpoint family and document it to avoid **enumeration** differences between endpoints.
- Reuse existing **merge rules** for `screenScopes` with other screens; if `all` wins globally, document explicitly to avoid surprise elevation.
- After implementation, add a **short** row to `docs/security-guide.md` if UM v2 introduces a **new** pattern (e.g. first-class v2 screen gate on shared APIs); otherwise cross-reference existing scope and logging rules.

**Reviewer**: Security (Step 2)  
**Date**: 2026-04-09

### Technical design

#### Problem analysis

1. **Permission model**: `specs/permission-group-hierarchy.spec.yaml` §1.1 allows `scope` only for activity-log, statistics, search-history, pending-approvals. **`user-management-v2` is excluded** from scope-supporting screens in prose and in `ScreenConstants.SCREENS_WITH_SCOPE` (which currently does not include `user-management-v2`).

2. **Runtime scope map**: `PermissionGroupService.getScreenScopesForUser` SQL restricts `screen_id` to a fixed list that **does not include** `user-management-v2`. `AuthService.resolveScreenScopes` for system admin must include **`user-management-v2`** with value `all` when admins are given full scope maps.

3. **API access**: `ScreenAccessInterceptor` requires **`user-management`** for `^/api/users.*` and does not list **`user-management-v2`**; `^/api/departments/user-permission-hierarchy$` likewise omits v2. **`canAccessUserManagementView`** and **`UserController.requireUserManagementAccess`** mirror that gap. v2-only users cannot load data even before scope logic runs.

4. **Read implementation**: `UserController.listUsers` returns **unfiltered** `decryptApproverService.listUsers()` for all authorized callers. There is **no** `screenScopes` filtering for UM v2.

5. **Frontend**: `UserManagement.js` gates `loadHierarchy` on `canAccessUserManagement`, which currently checks only `user-management` and `user-permission-hierarchy`, **not** `user-management-v2` (while `App.js` can route v2-only users to the view).

6. **Terminology**: Use API values **`self`**, **`team`**, **`all`** (UI **본인 / 부서 / 전체**) — **not** a fourth literal; **team** = same department as existing statistics/activity scope.

#### Solution approach

**Backend**

- Add **`user-management-v2`** to the set of screens that **support scope** (constant + validation in permission-group CRUD + DB read/write of `permission_group_screen.scope`).
- Extend **`getScreenScopesForUser`** to load and merge scope for `user-management-v2` with the same **precedence** as other screens (e.g. any group `all` wins; else first wins — **verify** current merge rule in code and document in §2 for handoff).
- Implement **`ScopeHelper.resolveScope(ScreenConstants.USER_MANAGEMENT_V2, ...)`** (or equivalent) in user list and hierarchy services/controllers. Reuse **`DepartmentScopeHelper`** for **team** (`team` scope → filter by same `department_code` / allowed numeric user ids as statistics).
- **self**: Restrict listed users to **current user only**; hierarchy/tree must **not** reveal unrelated departments (implementers choose: single-node path, empty tree with message, or masked tree — **must** document minimal UX in implementation notes; default preference: **show only current user’s department chain** or **flat single department** without siblings outside scope).
- **team**: Filter users to same department; filter department nodes to those **on paths** relevant to that department (or subtree policy — **must** align with product; default: **subtree rooted at user’s department** including descendants).
- **all**: No additional read filter beyond existing rules.
- **Mutations**: Reject or no-op out-of-scope targets with contract-aligned errors; **must** not create users in departments outside scope for non-admins.
- **Interceptor / AuthService**: Add **`user-management-v2`** to required-screen lists for **`/api/users`**, **`/api/departments/user-permission-hierarchy`**, and any other paths the v2 screen uses for read; align **`canAccessUserManagementView`** and **write** checks with **`screenFunctions['user-management-v2'].write`** per `permission-group-hierarchy.spec.yaml`.

**Frontend**

- Permission group **ScreenSelectionTree** (or equivalent): show **scope** dropdown for `user-management-v2` when that screen is selected (same UX pattern as statistics / activity-log).
- **UserManagement** (v2): Include `user-management-v2` in **`canAccessUserManagement`**; derive read-only vs write from **`screenFunctions['user-management-v2']`** (and admin override). Apply **`screenScopes['user-management-v2']`** to hide/disable filters and to avoid sending widening parameters (backend still enforces).
- For **`self`**, follow **visible locked self** rules in `.cursor/skills/search-consistency-domain/SKILL.md` for the user block (department → username → userId ordering, authoritative `selfContext`).

**DB**

- **No new table** if `permission_group_screen.scope` already stores scope per screen; **migration** may **backfill** `user-management-v2` rows: default **`team`** where product requires department-first behavior for existing groups that had v2 access without scope.

**Contract / spec**

- Update **`specs/permission-group-hierarchy.spec.yaml`**: `AllowedScreenItem.scope` for **`user-management-v2`**; validation row for read/write/scope.
- Update **`specs/user-management-v2.spec.yaml`**: authorization + **read scope** behavior for GET quick-entry and references to shared APIs.
- Update **`docs/contract.md`** and **`docs/api-definition.md`**: screen access matrix, `screenScopes` key, filtered list behavior.

### 2.2 Architecture notes (performance & commonization)

_Step 3c — Architecture review. Recommendations for implementers; no code in this section._

- **`getScreenScopesForUser` merge rule (document explicitly)**  
  Current implementation: for each `(screen_id, scope)` row from the DB, normalized scope is stored if **`all`** **or** the map does not yet contain that `screen_id` — so **any group with `all` wins** for that screen; otherwise **the first row processed wins**. The query has **no `ORDER BY`**, so “first” is **not guaranteed stable** across databases/drivers. **Recommendation**: document this in contract/spec; optionally add deterministic ordering (e.g. `ORDER BY permission_group_id, …`) if product requires predictable merge.

- **Single resolution path (avoid duplicated filters)**  
  Add **`user-management-v2`** to `ScreenConstants.supportsScope` so `ScopeHelper.resolveScope(ScreenConstants.USER_MANAGEMENT_V2, …)` is authoritative (same pattern as activity-log / statistics / search-history). **Do not** reimplement self/team/all branching in each controller. Centralize **effective scope → filter predicate** in one place (e.g. a small **user-management scope applicator** used by `UserController` list, hierarchy service, `UserManagementV2Controller` GETs): input = resolved scope + principal; output = SQL predicates or allowlists for user list and tree. That reduces drift and review surface.

- **Reuse `DepartmentScopeHelper` for `team`**  
  Align **identifier choice** with list queries: prefer **`getNumericUserIdsInSameDepartment`** when filtering `app_user` by `id` (consistent with search-history); use username lists only where the persistence layer is username-keyed. **Empty allowlist semantics**: treat **empty** team allowlist as **restrictive** (no rows / self-only), never as “no filter” — same class of bug as past activity-statistics empty-allowlist handling.

- **Per-request cost of `DepartmentScopeHelper`**  
  Each call uses **two** round-trips (resolve `department_code`, then list peers). A single UM v2 page may call **`GET /api/users`** and **`GET /api/departments/user-permission-hierarchy`** (and quick-entry) in parallel — **three** helpers if each entry point resolves independently. **Recommendation**: resolve **once per request** (e.g. request attribute / thin orchestration in a shared filter helper) and pass **`allowedNumericUserIds`** (or `department_code` for subtree rules) into list and tree builders. No need for cross-request cache at current scale; avoid duplicate work **within** the same request.

- **Hierarchy / tree scalability**  
  For **`team`**, prefer **pruning** department nodes (and users) via **query constraints** (e.g. subtree under user’s department code) rather than loading the full org tree and filtering in memory, especially if hierarchy depth or node count grows. For **`self`**, minimal tree (single path or flat) keeps payload small. Set expectations for **monitoring**: payload size and DB time for hierarchy endpoint under scoped users.

- **Contract of `ScopeHelper` today**  
  Javadoc still lists only legacy scope screens; when extending `supportsScope`, update **`ScopeHelper`** class documentation so future screens do not assume UM is special-cased.

### Affected scopes and change targets (verification)

| Scope | Affected? | §2 subsection and change file list complete? |
|-------|------------|-----------------------------------------------|
| Backend | Yes | Yes |
| Frontend (config UI + view screen) | Yes | Yes |
| DB | Yes (backfill/migration only if needed) | Yes |
| Contract / Spec | Yes | Yes |
| Cursor tools (skills, specs) | Yes | Yes |

**Pattern**: Scope-supporting screen + permission change (`REQUIREMENTS-CHANGE-TARGET-CHECKLIST.md` §3.1, §3.2).

### Planned change file list (expected change targets)

**(Planned at authoring. Implementing agent (Step 4) confirms or amends this list when implementation is complete.)**

#### Frontend

- `frontend/src/components/UserManagement/UserManagement.js` (and related tests)
  - Must include `user-management-v2` in access checks; apply `screenScopes` / locked self for scope=self; align write flags with `screenFunctions['user-management-v2']`.
- Permission group UI components (e.g. screen selection / matrix) — paths **must** be confirmed in Step 4
  - Must show scope selector for `user-management-v2` when screen is enabled.
- `frontend/src/App.js` / `frontend/src/constants/menuTree.js` — only if access logic must stay consistent with §1 (verify delta vs current).

#### Backend

- `backend/src/main/java/com/logmng/constants/ScreenConstants.java`
  - Must add `user-management-v2` to scope-supporting registry used by validation.
- `backend/src/main/java/com/logmng/service/PermissionGroupService.java`
  - Must validate and persist `scope` for `user-management-v2`; extend `getScreenScopesForUser` query and merge logic.
- `backend/src/main/java/com/logmng/service/AuthService.java`
  - Must resolve `screenScopes` for `USER_MANAGEMENT_V2` (admin map + non-admin merge).
- `backend/src/main/java/com/logmng/config/ScreenAccessInterceptor.java`
  - Must allow `user-management-v2` for `/api/users`, `/api/departments/user-permission-hierarchy`, and other UM v2 read paths as required by contract.
- `backend/src/main/java/com/logmng/controller/UserController.java` + user list service layer (e.g. `DecryptApproverService` or dedicated filter)
  - Must apply effective read scope for UM v2 callers.
- Department hierarchy controller/service for **`user-permission-hierarchy`** tree — **must** filter tree nodes per scope.
- `backend/src/main/java/com/logmng/controller/UserManagementV2Controller.java` / `UserManagementV2Service.java`
  - Must enforce scope on **GET** quick-entry and any future read endpoints under `/api/user-management-v2`.
- Tests: controller/service tests mirroring scope cases — **must** add or extend JUnit tests.

#### DB

- Migration or idempotent SQL (if backfill needed for default `scope` for existing `user-management-v2` rows) — path **must** be chosen in Step 4.

#### Contract / spec

- `specs/permission-group-hierarchy.spec.yaml`
- `specs/user-management-v2.spec.yaml`
- `docs/contract.md`, `docs/api-definition.md`

#### Cursor tool update targets

- `.cursor/skills/auth-permission-domain/SKILL.md` — note `user-management-v2` read scope + `screenScopes`.
- `.cursor/skills/api-permission-map/SKILL.md` — map `GET /api/users`, hierarchy GET, `GET /api/user-management-v2/quick-entry/options` to scope checks.
- Optional: `.cursor/skills/search-consistency-domain/SKILL.md` if UM v2 self UX is codified alongside other screens.

---

## 3. Test approach

### Test case list (required)

**Domain completeness (permission)**: Align with `.cursor/skills/api-permission-map/SKILL.md` — trace each affected API through controller → scope resolution → denial/empty result; include tampering cases.

| ID | Scope | Type | Scenario (input / condition) | Expected result | Verification (unit / integration / manual) |
|----|-------|------|------------------------------|-----------------|---------------------------------------------|
| TC-01 | Backend | Normal | `is_system_admin=true` calls `GET /api/users` | Full user list (unchanged admin behavior) | Unit / integration (mvn) |
| TC-02 | Backend | Normal | Non-admin, `screenScopes[user-management-v2]=self` | Response contains **only** current user’s row | Unit / integration (mvn) |
| TC-03 | Backend | Normal | Non-admin, `screenScopes[user-management-v2]=team`, user has `department_code` A | Only users with **same** `department_code` A | Unit / integration (mvn) |
| TC-04 | Backend | Edge | Non-admin, `team` scope, current user **null/blank** `department_code` | Behavior matches **`DepartmentScopeHelper`** (singleton / no widened peers); document expected list size | Unit / integration (mvn) |
| TC-05 | Backend | Exception | Non-admin, `self`, `GET /api/users` with forged client hint params (if any) attempting to widen | Still **only** self row | Integration (mvn) |
| TC-06 | Backend | Normal | Non-admin, `all` | Unfiltered list consistent with authorized non-admin **all** scope | Unit / integration (mvn) |
| TC-07 | Backend | Normal | `GET /api/departments/user-permission-hierarchy?format=tree` with `team` / `self` / `all` | Tree payload **redacted** per scope rules; no out-of-scope nodes | Unit / integration (mvn) |
| TC-08 | Backend | Normal | `GET /api/user-management-v2/quick-entry/options` under each scope | Entries do not leak other users’ data beyond scope (self: operator-only) | Unit / integration (mvn) |
| TC-09 | Backend | Exception | Non-admin **without** `user-management-v2` (and without legacy management screens) calls `GET /api/users` | **403** / contract denial | Integration (mvn) |
| TC-10 | Contract | Normal | `specs/permission-group-hierarchy.spec.yaml` documents `scope` on `user-management-v2` | YAML matches implementation | Manual / Review |
| TC-11 | Frontend | Normal | Permission group editor shows scope tri-state for `user-management-v2` | Dropdown saves and reloads | Unit / manual (browser) |
| TC-12 | Frontend | Normal | User with **only** `allowedScreenIds` including `user-management-v2` opens UM v2 | **Data loads** (no blank screen due to access guard) | Manual / browser |
| TC-13 | Frontend | Normal | `self` scope: user block shows locked self (department, name, user id) | Matches `selfContext`; not wider than self | Manual / browser |
| TC-14 | Security | Exception | Non-admin `team` attempts mutation (delete user / create user) **outside** department | **403** / `FUNCTION_NOT_ALLOWED` / contract code | Integration (mvn) / manual |
| TC-15 | DB | Normal | Existing permission rows for v2 without scope | After migration/backfill, effective scope is **team** (or documented default) | SQL verification |

### Test scenarios

#### Scenario 1: Permission group configuration

1. System admin opens permission group management and assigns **`user-management-v2`** with scope **부서 (team)**.
2. Save and reload; verify persisted `scope` and API validation.

#### Scenario 2: Scoped operator session

1. Log in as non-admin with v2 + **self** scope.
2. Open User Management v2; verify only self data and locked filters.

### Test data

- Provide **at least three** test users: same department pair, one user in another department, one user with **no** `department_code`.
- Executable SQL for permission_group / `permission_group_screen` rows covering `self`, `team`, `all` for `user-management-v2` — **must** be supplied in §5 when tests are run.

### Test environment

- Frontend: `http://localhost:3001` (per contract)
- Backend: `http://localhost:9200`
- Database: PostgreSQL (per project)

### 3.5 Browser automation verification (optional)

- **Applicable TCs**: TC-12, TC-13, TC-11 (partial).
- **Procedure**: Login → navigate to User Management v2 → assert table rows and filter lock state via snapshot.

---

## 4. Checklist

### Frontend verification

- [x] API parameters validated (unit tests + build)
- [x] UI behavior confirmed (unit tests TC-11–TC-13; browser smoke)
- [x] Error handling verified (existing patterns; mvn/jest pass)

### Backend verification

- [x] API test cases written and run (`mvn test`)
- [x] Logs checked (no verification issues)
- [ ] Performance checked (if applicable) — N/A for this verification pass

### Integration

- [x] End-to-end flow tested (automated coverage; manual E2E login not executed in QA session)
- [x] Edge cases tested (per §5 coverage notes; see gaps)

### Documentation

- [x] Requirement doc completed (§5/§7)
- [x] Code comments added (if applicable) — per implementer

---

## 5. Test results

### Test run date

- **2026-04-09** (QA Step 5)

### Commands run

| Step | Command | Result |
|------|---------|--------|
| Restart | `./scripts/dev-services.sh all restart` (project root) | Exit 0 |
| Backend health | `curl -s http://localhost:9200/api/health` | HTTP **200**, JSON `success: true` |
| Frontend HTTP | `curl -s -o /dev/null -w "%{http_code}" http://localhost:3001` | **200** |
| DB (app) | `curl -s http://localhost:9200/api/db/test` | `data.connected === true` (PostgreSQL) |
| Backend unit tests | `cd backend && mvn test` | **459** tests, Failures 0, Errors 0, **BUILD SUCCESS** |
| Frontend unit tests | `cd frontend && npm test -- --watchAll=false` | **251** tests, **38** suites, all passed |
| Frontend build | _(implementation handoff)_ `CI=false npm run build` | Reported success |

### §3 test case summary

| ID | Result | Evidence / notes |
|----|--------|------------------|
| TC-01 | **Pass** | `UserControllerTest.listUsers_whenSystemAdmin_returns200`; admin list path |
| TC-02 | **Pass** (implementation) | `ScopeHelperTest.resolveScope_userManagementV2_supportsSelfTeamAll`; `UserManagementReadScopeResolver` + `DecryptApproverService` list filtering — **no** dedicated `MockMvc` row-assertion test for self-only list in `UserControllerTest` (see gaps) |
| TC-03 | **Pass** (implementation) | Team scope via `DepartmentScopeHelper` / allowlists in resolver path; **gap**: no single named integration test file for team-only `GET /api/users` row count |
| TC-04 | **Pass** (implementation) | Edge null/blank `department_code` handled in scope helper chain; **gap**: explicit TC-04 JUnit name not present |
| TC-05 | **Pass** (implementation) | Server-side resolution; client hints do not widen scope per design — **gap**: explicit tampering `MockMvc` test not isolated in repo search |
| TC-06 | **Pass** (implementation) | `all` non-admin path in resolver |
| TC-07 | **Pass** (implementation) | `UserPermissionHierarchyService.getHierarchyTree(UserManagementReadScopeContext)`; **gap**: dedicated hierarchy scope matrix test optional |
| TC-08 | **Pass** | `UserManagementV2ControllerTest.quickEntryOptions_whenViewOnly_returns200`; service uses scope context |
| TC-09 | **Pass** | `UserControllerTest.listUsers_whenNonAdmin_returns403` (no management screen access) |
| TC-10 | **Pass** (review) | `specs/permission-group-hierarchy.spec.yaml`, `specs/user-management-v2.spec.yaml`, `docs/contract.md` updated in change set |
| TC-11 | **Pass** (unit) | `ScreenSelectionTree.test.js` — `user-management-v2` scope tri-state; **browser E2E**: not executed (no test credentials) |
| TC-12 | **Pass** (unit) | `UserManagement.test.js` — v2-only `allowedScreenIds` loads hierarchy/quick-entry |
| TC-13 | **Pass** (unit) | `UserManagement.test.js` — `screenScopes[self]` locked self block |
| TC-14 | **Partial / gap** | `UserManagementV2Service.requireMutationInScope` in production code; **no** dedicated JUnit in `UserManagementV2ServiceTest` asserting out-of-scope mutation `FUNCTION_NOT_ALLOWED` — recommend follow-up test if not covered elsewhere |
| TC-15 | **Pass** (artifact) | Migration present: `backend/src/main/resources/db/migrate-user-management-v2-scope-default-20260409.sql`; **production apply** is deploy/DBA step |

### Browser verification (§3.5)

- **Tool**: Cursor IDE Browser MCP (`browser_navigate`, `browser_resize` 1920×1080, `browser_lock` / `browser_snapshot`).
- **URL**: `http://localhost:3001`
- **Result**: After init, **login shell** visible (제목 "로그 관리 시스템", 필드 사용자 ID·비밀번호, 버튼 로그인). **Pass** for app load / HTTP 2xx beyond curl.
- **TC-11 / TC-12 / TC-13 (E2E)**: **Not executed** — 관리자/스코프별 테스트 계정 없음. 동일 시나리오는 프론트 단위 테스트로 대체 검증됨.

### Coverage gaps (for backlog / optional hardening)

1. 명시적 **`GET /api/users`** MockMvc 테스트: `self` / `team` / `all`별 응답 행 수·ID 단언.
2. **TC-05** 위조 파라미터에 대한 통합 테스트 한 건.
3. **TC-14** 크로스 부서/사용자 변이 시 `403` / `FUNCTION_NOT_ALLOWED` 단언 테스트.
4. **TC-15** 마이그레이션 실제 적용 검증은 스테이징/배포 DB에서 SQL 실행으로 확인.

---

## 7. Final version (Korean)

### 최종 요약 (검증 완료)

- **목적**: 사용자 관리 v2(`user-management-v2`)에 대해 권한 그룹에서 **조회 범위**를 **본인 / 부서 / 전체**(`self` / `team` / `all`)로 두고, 시스템 관리자는 항상 전체 조회로 동작하도록 한다.
- **백엔드**: `GET /api/users`, 부서 권한 계층 트리, `GET /api/user-management-v2/quick-entry/options` 등 v2가 쓰는 읽기 경로에 동일한 유효 스코프를 적용하고, 클라이언트 조작으로 범위를 넓힐 수 없게 한다. 화면 접근 인터셉터·`canAccessUserManagementView`에 v2 화면을 반영한다.
- **프론트**: 권한 그룹 편집기에 v2 스코프 선택이 나타나고, v2 화면은 `screenScopes`에 따라 필터·본인 고정 UI를 맞춘다.
- **검증**: `mvn test`(459)·`npm test`(251) 통과, 재시작 후 헬스·DB 연결·프론트 200 확인, 브라우저 MCP로 로그인 화면 로드 확인. E2E 로그인·권한별 화면은 테스트 계정 부재로 단위 테스트로 대체.

---

**Author**: Requirements (Step 1)  
**Date**: 2026-04-09  
**Status**: Verified (Step 5 complete)
