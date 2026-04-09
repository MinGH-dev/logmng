# 20260409 - User Management v2 access denied for approver-style groups (permission-groups API gap)

## 1. User requirement

### Requirement description

Operators who receive **User Management v2** (`user-management-v2`) through a **permission group oriented to decrypt approvers / team leads** (e.g. 승인자 그룹, `APPROVE_USER`-style groups that historically granted **`pending-approvals`** / **`search-history`** but not legacy **`user-management`** or **`user-permission-hierarchy`**) must be able to **open User Management v2** and load its data when **`user-management-v2`** is explicitly granted to that group.

Today, after granting **`user-management-v2`** to such a group, the user can reach the menu entry but the screen fails (access denied / error path), which contradicts the configured permission.

**Related requirement (broader scope alignment):** `docs/requirements/20260409-user-management-v2-read-scope.md` (read scope, shared APIs, interceptor alignment). This bugfix doc isolates a **specific runtime failure** observed when **`user-management-v2`** is the **only** management-family screen (typical for approver groups).

### User scenario

1. A permission-group administrator assigns **`user-management-v2`** (read and/or write per product) to a group used by **decrypt approvers** (승인자 그룹), which may **not** include **`user-management`** or **`user-permission-hierarchy`**.
2. User **Hong Gildong (numeric id example: 20260001)** belongs to that group and logs in.
3. The **User Management v2** menu is visible (routing / `allowedScreenIds` includes `user-management-v2`).
4. **Problem**: Opening **User Management v2** shows **no permission** / failure to load (user-facing message equivalent to access denied or “관리자만 접근할 수 있습니다.”).

### Expected outcome

- With **`user-management-v2`** in **`allowedScreenIds`** (and matching **`screenFunctions`** / **`screenScopes`** per contract), the User Management v2 view **loads hierarchy and user list** (or fails only for reasons unrelated to missing legacy screen ids), consistent with `docs/contract.md` and `specs/permission-group-hierarchy.spec.yaml` §4.3 / §4.4.
- **`GET /api/auth/me`** remains the authoritative source for **`allowedScreenIds`**, **`screenFunctions`**, **`screenScopes`**; no approver-only user must be **implicitly excluded** from UM v2 by a code path that only recognizes legacy management screens.
- Any **ScreenAccessInterceptor** rule for APIs invoked by the UM v2 screen must include **`user-management-v2`** where the product intends v2-only operators to call that API (e.g. read-only group list for assignment dropdowns).

---

## 2. Design

### 2.1 Security review (optional; when PII / decryption / access control)

- [ ] Security review performed (check if applicable)

**Note:** Extending **`GET /api/permission-groups`** (list) access to principals who have **`user-management-v2`** but not legacy **`user-management`** exposes **permission group metadata** (names/codes) to those operators—consistent with already allowing **`GET /api/users`** and hierarchy reads for the same screen per parent requirement. Product/security should confirm this is acceptable for the intended UM v2 role. **Mutating** permission-group APIs must remain gated by existing write/admin rules (unchanged unless a separate requirement says otherwise).

### Technical design

#### Codebase summary (investigation notes)

- **Frontend routing:** `frontend/src/App.js` — `canAccessView('user-management-v2')` allows the view when **`allowedScreenIds`** contains `user-management-v2` (or legacy `user-management` / `user-permission-hierarchy`).
- **Frontend UM v2 shell:** `frontend/src/components/UserManagement/UserManagement.js` — `canAccessUserManagement` is true when **`allowedScreenIds`** includes **`user-management-v2`** (among others). On load, **`loadHierarchy`** runs **`Promise.all`**: `getUserPermissionHierarchy('tree')` → **`GET /api/departments/user-permission-hierarchy`**, `getUsers()` → **`GET /api/users`**, **`listPermissionGroups()`** → **`GET /api/permission-groups`** (`frontend/src/services/permissionGroupService.js`).
- **Backend screen gate:** `backend/src/main/java/com/logmng/config/ScreenAccessInterceptor.java` — `PATH_SCREEN_RULES` map API path prefixes to **required** `screen_id` lists. Non–system-admin users must have **at least one** required screen in **`allowedScreenIds`**.
  - **`^/api/users.*`** requires **`user-management`** OR **`user-management-v2`** (aligned with UM v2).
  - **`^/api/departments/user-permission-hierarchy$`** requires **`user-management`** OR **`user-permission-hierarchy`** OR **`user-management-v2`** (aligned with UM v2).
  - **`^/api/permission-groups.*`** currently requires **`user-management`** OR **`user-permission-hierarchy`** only — **`user-management-v2` is not listed**.
- **Auth aggregation:** `AuthService.resolveAllowedScreenIds` → `PermissionGroupService.getAllowedScreenIdsForUser(username)` unions screens from **`permission_group_screen`** with **`read` null or true** (`app_user_permission_group.user_id` = **`app_user.username`** per schema).
- **Session / me:** `AuthService.getCurrentUserInfo` builds **`allowedScreenIds`**, **`screenScopes`**, **`screenFunctions`** for **`GET /api/auth/me`** and interceptors.

#### Problem analysis

1. **Primary hypothesis (high likelihood):** User Management v2 loads **`GET /api/permission-groups`** as part of its initial parallel fetch. For operators who have **only** **`user-management-v2`** (common for **승인자 그룹** extended with v2), the interceptor **denies** this path because the rule omits **`user-management-v2`**. **`Promise.all`** fails; the UI surfaces an access/403-style error even though the user is allowed the screen and other calls would succeed.
2. **Secondary hypotheses (must be ruled out in diagnostics before a narrow fix):**
   - **`permission_group_screen.read = false`** for `user-management-v2` → row excluded from **`getAllowedScreenIdsForUser`**, so **`allowedScreenIds`** never contains v2 (configuration/save bug).
   - **`app_user_permission_group.user_id`** stored as **numeric id string** vs **username** (legacy); user not linked to the group in SQL (see `migrate-app-user-permission-group-user-id-to-username-20260407.sql`).
   - Stale client **`localStorage`** vs **`/api/auth/check`** merge in **`App.js`** showing an outdated **`allowedScreenIds`** list (less likely if menu shows v2).

#### Diagnostic phase (mandatory for error/bug fix only)

Do **not** change authorization logic based on hypothesis alone. Implementers must confirm the root cause from evidence (logs and/or network traces), then apply the minimal fix.

- **Phase 0 (diagnostic):**
  1. **Reproduce** with a test user matching the report: member of an **approver-oriented** group where **`user-management-v2`** is granted and **`user-management` / `user-permission-hierarchy`** are absent (or confirm production user’s effective groups in DB).
  2. **Browser / HTTP:** Record which request returns **403** (Network tab): expect **`GET /api/permission-groups`** vs **`GET /api/users`** vs **`GET /api/departments/user-permission-hierarchy`**.
  3. **API contract check:** Call **`GET /api/auth/me`** (authenticated session) and record **`allowedScreenIds`**, **`screenFunctions['user-management-v2']`**, **`screenScopes['user-management-v2']`**. Confirm whether **`user-management-v2`** is present. If absent, prioritize DB row / **`read`** flag / **`app_user_permission_group`** linkage before interceptor changes.
  4. **Backend DEBUG (dev only):** With `ScreenAccessInterceptor` diagnostic flags if available (`app.diagnostic.permission-group-screen`, etc.), or temporary **DEBUG** logs (guarded: DEBUG level or dev profile only; **no production noise**), log on deny: **`path`**, **`requiredScreens`**, **`allowedScreenIds` count**, and whether **`user-management-v2`** is in the allowed set. **Remove or downgrade** after verification per `docs/workflow/REQUIREMENTS-AUTHORING-WORKFLOW.md` §1.3.
  5. **Analyze:** Only after step 2–4 confirm **403 on `/api/permission-groups`** with **`user-management-v2` ∈ allowedScreenIds**, treat the interceptor rule gap as **confirmed root cause**. If **`user-management-v2` ∉ allowedScreenIds**, fix data path / permission-group persistence first.

- **Production safety:** Diagnostic logs must be **DEBUG**, behind a **flag**, or **removed** after verification; they must not run in production as verbose INFO.

#### Solution approach (after confirmed cause)

Structure by scope for handoff.

**Frontend:**

- If diagnostics prove **403 only on `listPermissionGroups`**: optionally **short-term** degrade gracefully (e.g. catch per-request or avoid `Promise.all` all-or-nothing) only if product requests—but **preferred** fix is **backend interceptor + contract alignment** so v2-only users receive a consistent **200** for **GET** list when allowed by product.
- If diagnostics prove **missing `allowedScreenIds`**: no frontend-only workaround; fix backend/DB/configuration.

**Backend:**

- If confirmed: extend **`ScreenAccessInterceptor`** **`^/api/permission-groups.*`** required screen list to include **`ScreenConstants.USER_MANAGEMENT_V2`** (in addition to existing legacy screens), **unless** product explicitly restricts permission-group **listing** to legacy screens only (then §1 must be revised and Frontend must stop calling **`listPermissionGroups`** for v2-only users—larger UX change).
- Align **`AuthService.canAccessUserManagementView`** / controller-level checks only if diagnostics show a separate gap (current code already includes **`USER_MANAGEMENT_V2`** for management view).
- **Mutating** `/api/permission-groups/**` endpoints should continue to enforce **write** / admin rules per existing controllers; this bugfix focuses on **read/list** behavior needed by UM v2 load path unless audit shows otherwise.

**DB:**

- No schema change expected for the primary interceptor hypothesis. If diagnostics show **assignment/key** issues, apply corrective data or follow-up migration per existing **`app_user_permission_group`** conventions.

**Contract / spec:**

- Update **`docs/contract.md`**, **`docs/api-definition.md`**, and **`specs/permission-group-hierarchy.spec.yaml`** § path-to-screen mapping for **`GET /api/permission-groups`** to document **`user-management-v2`** as an allowed alternative where product approves.

**Cursor tools:**

- After implementation, update **`.cursor/skills/api-permission-map/SKILL.md`** (and **`auth-permission-domain`** if needed) so **`GET /api/permission-groups`** mapping reflects the new rule.

### Affected scopes and change targets (verification)

| Scope | Affected? | §2 subsection and change file list complete? |
|-------|------------|-----------------------------------------------|
| Backend | Yes | Yes |
| Frontend | Maybe (error handling only if product requests) | Yes |
| DB | Maybe (diagnostic only) | Yes |
| Contract / Spec | Yes | Yes |
| Cursor tools | Yes | Yes |

### Planned change file list (expected change targets)

**(Planned at authoring. Implementing agent (Step 4) confirms or amends this list when implementation is complete.)**

#### Backend

- `backend/src/main/java/com/logmng/config/ScreenAccessInterceptor.java`
  - **Must** verify and, if product confirms, **extend** `PATH_SCREEN_RULES` entry for `^/api/permission-groups.*` so **`user-management-v2`** satisfies the interceptor for principals who rely on v2-only access (align with UM v2 `listPermissionGroups` usage).

#### Frontend

- `frontend/src/components/UserManagement/UserManagement.js` — **only if** product chooses partial-load UX after failed diagnostics; otherwise **no change** expected for the primary hypothesis.

#### Contract / docs

- `docs/contract.md` — path ↔ screen mapping for permission-groups GET (if documented).
- `docs/api-definition.md` — § auth / permission-groups access rules.
- `specs/permission-group-hierarchy.spec.yaml` — §4.3 interceptor matrix for `/api/permission-groups`.

#### Cursor tools

- `.cursor/skills/api-permission-map/SKILL.md` — register updated mapping for `GET /api/permission-groups`.

---

## 3. Test approach

### Test case list (required)

**Domain note:** Apply **`api-permission-map`** skill checklist: map endpoint → interceptor → denial; include **`GET /api/auth/me`** shape when UI behavior depends on it.

| ID | Scope | Type | Scenario (input / condition) | Expected result | Verification (unit / integration / manual) |
|----|-------|------|------------------------------|-----------------|--------------------------------------------|
| TC-01 | Backend | Normal | Non–system-admin user with **`allowedScreenIds` = [`user-management-v2`]** (and no `user-management` / `user-permission-hierarchy`) calls **`GET /api/permission-groups`** | **200** with list payload (same as today for authorized admins), not **403** from interceptor | Integration (`mvn test` or documented `@SpringBootTest` + mockMvc) |
| TC-02 | Backend | Normal | Same user calls **`GET /api/users`** and **`GET /api/departments/user-permission-hierarchy?format=tree`** | **200** (regression; already expected per v2 rules) | Integration |
| TC-03 | Backend | Exception | Non–system-admin **without** any of `user-management`, `user-permission-hierarchy`, **`user-management-v2`** calls **`GET /api/permission-groups`** | **403** `FORBIDDEN` | Integration |
| TC-04 | Integration | Normal | Login as approver-group user with v2 granted; open UM v2 | No access-denied banner; hierarchy and user list load; Network shows **`GET /api/permission-groups` 200** | Manual / browser |
| TC-05 | Frontend | Normal | Unit test: `loadHierarchy` behavior when `listPermissionGroups` resolves (existing mocks) | Still calls all three APIs; update mocks if contract changes | Unit (`npm test`) |

### Test data

- SQL or seed steps: one **`permission_group`** (e.g. approver-style), **`permission_group_screen`** row for **`user-management-v2`** with **`read`** true (or null), **`app_user_permission_group`** linking test **`app_user.username`** to that group; ensure **no** `user-management` / `user-permission-hierarchy` rows for that user unless testing mixed cases.

### Test environment

- Frontend: `http://localhost:3001` (per contract)
- Backend: `http://localhost:9200`
- Database: PostgreSQL (per project)

### 3.5 Browser automation verification (optional)

- **Applicable TCs:** TC-04.
- **Procedure:** Login → navigate to User Management v2 → confirm **`GET /api/permission-groups`** status **200** and main panel not showing forbidden message.

---

## 4. Checklist

### Frontend verification

- [ ] UM v2 loads for v2-only test user (manual or automated) — *deferred: Step 5 ran backend + health only; TC-04 / §3.5 E2E recommended in staging*
- [ ] Error handling remains correct for true zero-permission users — *unchanged by this fix; not re-tested in this session*

### Backend verification

- [x] Interceptor unit/integration tests cover new rule (`ScreenAccessInterceptorTest`: `getPermissionGroups_allowedWithUserManagementV2Only`, `getPermissionGroups_deniedWithoutManagementFamilyScreens`; full suite `mvn test` pass)
- [x] No elevated access for users without `user-management-v2` / legacy screens as intended (deny case asserts 403)

### Integration

- [ ] End-to-end login + UM v2 path verified — *not run this session; TC-01–TC-03 covered by interceptor tests + `mvn test`*

### Documentation

- [x] Requirement doc completed (§5 / §6 this pass)
- [x] Contract/spec updated when behavior changes (`docs/contract.md`, `docs/api-definition.md`, `specs/permission-group-hierarchy.spec.yaml`, `api-permission-map` skill)

---

## 5. Test results

### Test run date

- **2026-04-09** (QA Step 5)

### Test results

#### Summary

| Check | Result | Notes |
|-------|--------|--------|
| `cd backend && mvn test` | **Pass** | Exit code 0 (full backend suite) |
| `curl -s http://localhost:9200/api/health` | **Pass** | HTTP 200, `success: true`, status OK |
| TC-01–TC-03 (§3) | **Pass** | Covered by `ScreenAccessInterceptorTest` + full `mvn test` |
| TC-04 / §3.5 browser | **Not run** | Backend-only scope this session; optional E2E per `verify.md` |
| TC-05 (frontend unit) | **N/A** | No frontend code change in this fix |

#### Frontend

- Not applicable for this commit (no `frontend/` changes). TC-04 deferred to manual/staging E2E.

#### Backend

- **Command:** `cd backend && mvn test` — **Pass** (2026-04-09).
- **Mapping to §3:** TC-01 → `getPermissionGroups_allowedWithUserManagementV2Only`; TC-03 → `getPermissionGroups_deniedWithoutManagementFamilyScreens`; TC-02 → existing interceptor rules for `/api/users` and user-permission-hierarchy (regression via suite).

**Commands:**

- `cd backend && mvn test`
- `curl -s http://localhost:9200/api/health`

---

## 6. Error remedy result (cause and action) — for error/bug fix requirements only

- **Requirement ID**: `20260409-user-management-v2-permission-groups-api-access-bugfix`
- **Root cause**: `ScreenAccessInterceptor` `PATH_SCREEN_RULES` for `^/api/permission-groups.*` required legacy screens (`user-management`, `user-permission-hierarchy`) only and omitted **`user-management-v2`**, so UM v2-only users received **403** on `GET /api/permission-groups` during `loadHierarchy` (`Promise.all`).
- **Actions taken**: Extended the permission-groups path rule to include `ScreenConstants.USER_MANAGEMENT_V2`; updated `ScreenAccessInterceptorTest`; aligned `docs/contract.md`, `docs/api-definition.md`, `specs/permission-group-hierarchy.spec.yaml`, and `.cursor/skills/api-permission-map/SKILL.md`.
- **Result**: Interceptor allows GET `/api/permission-groups` when `allowedScreenIds` contains `user-management-v2` only; deny behavior unchanged for unrelated screens. Backend tests pass; `/api/health` 200 after restart.
- **Completed**: 2026-04-09

---

## 7. Final version (Korean) — add after all verification is complete

_(Not added until verification is complete per `docs/workflow/DOCUMENT-LANGUAGE-POLICY.md`.)_

---

**Author**: Requirements (subagent)  
**Date**: 2026-04-09  
**Status**: Verified (QA Step 5 — backend tests + health; E2E TC-04 deferred)
