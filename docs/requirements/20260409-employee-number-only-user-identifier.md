# 20260409 - Employee number as end-user–visible user identifier (사번 only)

## 1. User requirement

### Requirement description

Across the product, end users and administrators must see and use **employee number (`app_user.employee_number`, 사번)** as the **human-facing “user identifier”** wherever the UI or copy refers to “사용자 ID” for login, self-context, or admin screens. The system must **stop presenting the internal numeric primary key (`app_user.id`) as if it were the user’s business identifier**, which currently causes confusion when `id`, `username`, and `employee_number` differ (e.g. logging in with one numeric value while the UI shows another for the same person).

**Canonical rule:** When a human interprets “사용자 ID” in login or admin contexts, the value must be **사번** (`employee_number`), **not** the internal numeric PK—**except** where no 사번 exists; those edge cases must be explicitly defined (see §2).

### User scenario

1. An operator opens the login screen and reads the field labeled for user identification; they enter **their 사번** (string, possibly with leading zeros or alphanumeric per org rules—not assumed to be purely numeric).
2. After login, locked self-context blocks (activity log, statistics, search history, pending approvals, user-management v2, etc.) show **사번** in the slot that today is often labeled “User ID” and bound to numeric `app_user.id`.
3. An administrator adds a user to a permission group or looks up a user in User Management v2; they search and confirm identity by **사번**, consistent with login.
4. **Problem:** Today, local login uses numeric `LoginRequest.userId` → `app_user.id`; API contract and skills document **`selfContext.userId` as numeric `app_user.id`**; User Management v2 already **prefers `employeeNumber` in the grid when present** but other surfaces still expose PK-shaped values as “user ID”. Integrations and REST paths use `{userId}` as numeric id. This splits mental model and training materials.

### Expected outcome

- **Login (local):** End users authenticate using **사번 semantics** (see §2 for string vs numeric and normalization), not by typing the opaque internal PK unless in a documented legacy/edge path.
- **Login (AD / external):** Directory **`principal`** remains the technical credential; after successful bind and app-user resolution, **all human-visible “user ID” labels show 사번** from `app_user`, not the directory id alone.
- **API / JSON:** The contract distinguishes **(a) stable internal key** used for joins, path parameters, and existing integrations from **(b) human-facing employee number** exposed in auth payloads and list/detail DTOs as agreed in §2 (naming, nullability, deprecation).
- **Backward compatibility:** Existing API clients that send or display numeric `app_user.id` continue to work for an agreed transition period, or versioned/documented breaking rules apply—decided in §2.
- **Null 사번:** Product policy is fixed in §2 (forbid new users without 사번, admin-only fallback, or legacy read-only account).
- **Consistency:** Screens that show a locked “User ID” in the user/requester block use **사번** as the value; internal PK is not shown as “사용자 ID” except per §2 edge rules.

**Note:** Numeric layout (widths, spacing) for user blocks on aligned screens follow existing design references where applicable; this requirement does not introduce new pixel values—it changes **semantic content** of the identifier field. If a future UI pass aligns field sizes, use `docs/design/search-fields-by-screen.md` and related design docs.

## 2. Design

### 2.1 Security review (optional; when PII / decryption / access control)

- [ ] Security review performed (check if applicable)
- **Risks:** Employee numbers are organization identifiers; displaying them consistently reduces confusion but must stay aligned with audit/logging policy (do not log secrets; avoid mixing 사번 and PK in security-sensitive messages without clarity). Directory principals and internal ids remain separate; AD password never stored (existing rule).
- **Acceptance / recommendations:** Security should confirm that exposing 사번 on self-context and admin UIs matches data-minimization expectations; confirm no new PII in logs beyond existing patterns.

### Technical design

#### Codebase summary (verified)

- **Schema / data model:** `app_user` has distinct columns including **`id`** (PK), **`username`** (system username, often generated e.g. `umv2_*` / `emp_*`), and **`employee_number`** (nullable string; uniqueness enforced for active users when present—see `AppUserEmployeeNumberUniqueness`, provisioning and UM v2 tests). Init data and migrations reference employee number backfill for display (`migrate-app-user-employee-number-display-backfill-20260409.sql`).
- **Local login:** `LoginRequest.userId` (`Long`) is **`app_user.id`**. `AuthService.loginLocal` loads `SELECT ... FROM app_user WHERE id = ?` and validates `password_hash`. `LoginRequest.java` documents: *app_user.id — used when auth.login.mode=local*.
- **AD login:** `LoginRequest.principal` + password; LDAP bind then `ExternalIdentityService.findAppUserIdForDirectoryPrincipal`; subsequent lookup by **`app_user.id`**. AD mode rejects `userId` in body (`INVALID_INPUT` pattern).
- **Frontend local login:** `LoginForm.js` uses label tied to “사용자 ID”, **`name="userId"`**, validates **integer numeric**, submits `{ userId: number, password }`.
- **Auth responses:** `LoginResponse` includes top-level **`userId`** (Long) and **`selfContext`** (`SelfContext`). `AuthService.resolveSelfContext` queries `u.id`, department, `u.name`; **does not select `employee_number` today**. Contract (`docs/contract.md`, `docs/api-definition.md`) states **`selfContext.userId` is numeric `app_user.id`** and is the authoritative locked display for the “User ID” column in scope=self user blocks.
- **User Management v2 UI:** `UserManagement.js` display column uses `employeeNumber ?? employee_number ?? userId ?? username`—already prefers 사번 when present.
- **Other API consumers:** Path parameters such as **`DELETE /api/users/{userId}`**, permission-group user assignment, search history, and `search_history.user_id` store **numeric `app_user.id`** per contract.

#### Problem analysis

1. **Semantic overload:** The product uses the label “사용자 ID” / `userId` for **internal PK** in APIs and UI, while HR and operators think of **사번** as “user id”. When `employee_number` ≠ `id` (or differs from what users remember), trust and support tickets increase.
2. **Contract vs product intent:** Published contract explicitly defines canonical JSON **`userId` = numeric `app_user.id`**. Aligning with “사번 only” for humans **requires** a deliberate contract evolution: either rename fields, add parallel fields, or restrict “사용자 ID” wording to 사번-only while retaining internal keys elsewhere.
3. **Login identifier mismatch:** Local login requires numeric PK; users expect 사번-shaped input. Without server-side resolution of 사번 → user row, users must discover internal ids (undesirable).
4. **AD:** Users see directory accounts; app display should still show **app 사번** for consistency after mapping.
5. **Null `employee_number`:** Codebase and tests allow **null** 사번 (e.g. provisioning scenarios). A strict “always 사번” rule needs a migration and product policy for legacy rows.

#### Solution approach

Structure by scope. **Product and Contract must confirm** items marked **(confirm)**.

**Frontend:**

- Replace or supplement labels so “사용자 ID” / “User ID” in **login**, **self-context user blocks**, **permission-group user entry**, **UM v2**, and other admin surfaces mean **사번**, not internal PK—unless §2 edge case applies.
- Local login input: accept **사번** per §2.1 login semantics **(confirm: string pattern, trim, leading zeros)**; remove or relax “integer only” validation once backend accepts 사번 resolution.
- For API responses: bind display fields to **`employeeNumber`** (or agreed field) from auth/me and list DTOs; **do not** show numeric `app_user.id` in slots labeled as 사번/사용자 ID.
- Permission-group “add user by id” flows: **(confirm)** whether input is 사번, numeric id, or both during transition.
- Update Jest tests (`LoginForm.test.js`, `UserManagement.test.js`, etc.) to expect 사번 semantics where applicable.

**Backend:**

- **Local login (confirm one approach):**
  - **Option A (recommended for UX):** Accept **`employeeNumber`** (string) **or** legacy **`userId`** (number) in `POST /api/auth/login` for `auth.login.mode=local`: if `employeeNumber` present, resolve **unique active** user by trimmed `app_user.employee_number`; else fall back to existing `userId` → `id` lookup for backward compatibility until deprecation date.
  - **Option B:** Keep `userId` field but document it as 사번-only **only if** product guarantees `app_user.id` always equals 사번 for all users (**generally false** given separate columns).
- **Reject** ambiguous matches (duplicate 사번 active rows) with a defined error code; align with uniqueness rules.
- **`resolveSelfContext` / `GET /api/auth/me`:** Populate **`employeeNumber`** on `SelfContext` (nullable string). **(confirm)** whether **`selfContext.userId` remains numeric id** for backward compatibility while UI uses `employeeNumber` for display, or whether fields are renamed (breaking).
- **Logging:** Log lines that print “사용자 ID” for operators should prefer 사번 where available; avoid ambiguous “userId” without schema in structured logs **(confirm)**.
- **Unchanged by default unless product requests:** Internal FKs, `search_history.user_id`, REST path `{userId}` as numeric id—remain unless a separate migration requirement authorizes breaking changes.

**DB:**

- **(confirm)** `NOT NULL` on `employee_number` for new inserts vs phased backfill.
- Data repair scripts if 사번 must be reconciled with former login habits (one-time migration).

**Contract / spec:**

- Update **`docs/contract.md`** and **`docs/api-definition.md`**: login body for local mode; **`selfContext`** shape (add `employeeNumber`; document meaning of `userId` vs 사번); deprecation notes for integrations relying on displaying `userId` as business id.
- Update **`specs/external-identity-auth.spec.yaml`**, **`specs/permission-group-hierarchy.spec.yaml`** (self-context and scope=self behavior), and any UM v2 spec if response fields change.

**Cursor tool update targets**

- **`.cursor/skills/auth-permission-domain/SKILL.md`** — Update bullets that state **`userId` is numeric `app_user.id`** as the **human-facing** identifier; split **technical id** vs **display 사번** after implementation.
- **`.cursor/skills/api-permission-map/SKILL.md`** — Refresh auth/current-user and self-context verification bullets.
- **`.cursor/skills/search-consistency-domain/SKILL.md`** — If requester/self user-block field meanings change, align wording.
- Other skills referencing “userId = app_user.id” for **display** (review grep during implementation).

### Affected scopes and change targets (verification)

Per `docs/workflow/REQUIREMENTS-CHANGE-TARGET-CHECKLIST.md` §1:

| Scope | Affected? | §2 subsection and change file list complete? |
|-------|------------|-----------------------------------------------|
| Backend | Yes | Yes |
| Frontend (config UI + view screen) | Yes | Yes |
| DB | Maybe (policy / constraints) | Yes (pending product confirm) |
| Contract / Spec | Yes | Yes |
| Cursor tools (skills, specs) | Yes | Yes |

**Pattern §3.4 (search/filter UI consistency):** Not the primary pattern—identifier **semantics** change. If implementation touches shared user-requester blocks, cross-check `docs/design/search-fields-by-screen.md` for labeling only.

### Planned change file list (expected change targets)

**(Planned at authoring. Implementing agent (Step 4) confirms or amends this list when implementation is complete.)**

#### Frontend

- `frontend/src/components/LoginForm.js` — Local-mode field: label, validation, and request body shape per agreed login semantics; tests.
- `frontend/src/components/LoginForm.test.js` — Assertions for 사번 vs legacy behavior.
- Components that render **locked self-context** / “User ID” from auth (e.g. activity log, statistics, search history, pending approvals, shared user blocks) — **verify full list at implementation**; bind display to **`employeeNumber`** when contract adds it.
- `frontend/src/components/UserManagement/UserManagement.js` — Align any remaining fallbacks that surface PK as primary label; tests `UserManagement.test.js`.
- `frontend/src/components/PermissionGroupManagement/PermissionGroupPanel.js` — Add-user input semantics **(confirm)**; numeric-only paths may need 사번 resolution or dual mode.
- Other grep hits for user-facing **`userId`** display — confirm in Step 4.

**Step 4 implementation confirmation (actual changed files):**

- `frontend/src/components/LoginForm.js`
- `frontend/src/components/LoginForm.test.js`
- `frontend/src/components/common/UserContextFilterBlock.js`
- `frontend/src/components/common/UserContextFilterBlock.test.js`
- `frontend/src/utils/security.js`
- `frontend/src/components/UserActivityLog/UserActivityLogSearchForm.js`
- `frontend/src/components/ActivityStatistics.js`
- `frontend/src/components/SearchHistory/SearchHistoryList.js`
- `frontend/src/components/SearchHistory/SearchHistoryList.test.js`
- `frontend/src/components/PendingApprovals/PendingApprovals.js`
- `frontend/src/components/UserManagement/UserManagement.js`
- `frontend/src/components/UserManagement/UserManagement.test.js`
- `frontend/src/components/PermissionGroupManagement/PermissionGroupPanel.js`

#### Backend

- `backend/src/main/java/com/logmng/dto/request/LoginRequest.java` — Document and implement optional `employeeNumber` or agreed dual-mode login request.
- `backend/src/main/java/com/logmng/service/AuthService.java` — `loginLocal`, `resolveSelfContext`, `getCurrentUserInfoInternal`; load `employee_number`; uniqueness errors.
- `backend/src/main/java/com/logmng/dto/response/LoginResponse.java` — `SelfContext` nested type: add `employeeNumber` field **(confirm)**.
- `backend/src/main/java/com/logmng/controller/AuthController.java` — If validation annotations change.
- Tests under `backend/src/test/java/com/logmng/...` for auth login and `/api/auth/me`.

**Step 4 actual changed files (backend, confirmed):**

- `backend/src/main/java/com/logmng/dto/request/LoginRequest.java`
- `backend/src/main/java/com/logmng/dto/response/LoginResponse.java`
- `backend/src/main/java/com/logmng/service/AuthService.java`
- `backend/src/test/java/com/logmng/service/AuthServiceTest.java`
- `backend/src/test/java/com/logmng/controller/AuthControllerTest.java`
- `backend/src/test/java/com/logmng/controller/SearchHistoryControllerTest.java` (SelfContext constructor alignment)
- `backend/src/test/java/com/logmng/webtest/DecryptControllerTest.java` (SelfContext constructor alignment)
- `backend/src/test/java/com/logmng/aspect/ActivityLogAspectTest.java` (SelfContext constructor alignment)

#### DB

- Optional: migration for `NOT NULL` / backfill — **only if** §2 policy requires; else document “no schema change” in §5.

#### Contract / spec

- `docs/contract.md` — Auth and self-context sections.
- `docs/api-definition.md` — §2.1 login, §2.4 `/api/auth/me`.
- `specs/external-identity-auth.spec.yaml`, `specs/permission-group-hierarchy.spec.yaml`, `specs/user-management-v2.spec.yaml` as applicable.

#### Cursor skills

- `.cursor/skills/auth-permission-domain/SKILL.md`
- `.cursor/skills/api-permission-map/SKILL.md`
- `.cursor/skills/search-consistency-domain/SKILL.md` (if touched)

## 3. Test approach

### Test case list (required)

| ID | Scope | Type | Scenario (input / condition) | Expected result | Verification (unit / integration / manual) |
|----|-------|------|------------------------------|-----------------|--------------------------------------------|
| TC-01 | Backend | Normal | `auth.login.mode=local`, login body uses **사번 string** matching unique active `app_user.employee_number` | 200, session established, same user as lookup by id | Unit + integration (mvn) |
| TC-02 | Backend | Normal | Local login uses **legacy numeric `userId`** (= `app_user.id`) only, no 사번 in body | Still 200 until deprecation window ends (if Option A) | Unit (mvn) |
| TC-03 | Backend | Exception | Local login with 사번 matching **zero** active users | 401 `INVALID_CREDENTIALS` or agreed code | Unit (mvn) |
| TC-04 | Backend | Exception | Local login with 사번 matching **more than one** active user (data anomaly) | 4xx with defined error code; no session | Unit (mvn) |
| TC-05 | Backend | Edge | Local login with **both** `userId` and `employeeNumber` present **(if product forbids)** | 400 `INVALID_INPUT` | Unit (mvn) |
| TC-06 | Backend | Normal | `GET /api/auth/me` after login includes **`selfContext.employeeNumber`** when DB has 사번 | JSON matches DB `employee_number` | Unit + integration (mvn) |
| TC-07 | Backend | Edge | User with **`employee_number` IS NULL** | `selfContext.employeeNumber` null or omitted per contract; display fallback rule documented | Unit (mvn) |
| TC-08 | Integration | Normal | `auth.login.mode=ad`, successful bind | Post-login `selfContext` shows 사번 from `app_user`, not raw principal as “user id” | Integration / manual |
| TC-09 | Frontend | Normal | Login form submits agreed local-login shape (사번) | Request matches contract | Unit (npm) |
| TC-10 | Frontend | Normal | Self-context user block shows **사번** in User ID slot | Visible text = `employeeNumber`, not internal PK | Unit + manual/browser |
| TC-11 | Frontend | Normal | UM v2 grid “identifier” column | Still prefers 사번; no regression vs `UserManagement.test.js` expectations | Unit (npm) |
| TC-12 | Frontend | Edge | Permission group add-user dialog **(confirm input type)** | Matches §2 (사번 vs id) | Unit + manual |
| TC-13 | Integration | Regression | `search_history` and APIs using path `{userId}` | Still function with numeric id; no accidental string 사번 in path without new routes | Integration |
| TC-14 | Integration | Normal | Contract doc **docs/api-definition.md** describes login + selfContext fields | Review pass | Manual (doc review) |
| TC-15 | Integration | Normal | Skills updated — auth-permission-domain / api-permission-map | Wording matches shipped behavior | Manual |

### Test scenarios

#### Scenario 1: Operator logs in with 사번 (local)

1. Set `employee_number` for test user to a known string; password known.
2. POST `/api/auth/login` with new contract.
3. GET `/api/auth/me` and open main screen self-context.
4. **Verification:** Login succeeds; displayed identifier is 사번; internal id not shown as “사용자 ID”.

#### Scenario 2: Legacy integration sends numeric id only

1. Send login with numeric `userId` only (if still supported).
2. **Verification:** Session works within deprecation policy; monitoring/alerts if deprecated.

### Test data

- Provide **executable SQL** in implementation/QA notes: insert or update `app_user` rows with distinct `id`, `username`, `employee_number` to reproduce mismatch cases; include one row with `employee_number` NULL for edge testing.

### Test environment

- Frontend: `http://localhost:3001` (or per contract)
- Backend: `http://localhost:9200`
- Database: PostgreSQL per project setup

### 3.5 Browser automation verification (optional)

- **Applicable TCs:** TC-10, TC-12 (manual/browser portions).
- **Procedure:** Login → navigate to screens with user/requester blocks → `browser_snapshot` to confirm label and value for 사번.
- **Reference:** `docs/workflow/BROWSER-AUTOMATION-VERIFICATION-POLICY.md`

#### 3.5.1 QA browser record (2026-04-09)

- **Tool:** cursor-ide-browser `browser_navigate` → `browser_lock` → `browser_wait_for` (“사번”) → `browser_snapshot` → `browser_unlock`.
- **Base URL:** `http://localhost:3001`
- **TC-09 (login surface):** Pass — first field accessible name **“사용자 ID (사번) \*”**, placeholder **“사번을 입력하세요 (예: EMP-2026-0001)”**, control type textbox (not integer-only).
- **TC-10 (post-login self block):** Partial — login shell verified; **post-auth locked user block** not exercised (no test credentials in this run).
- **TC-12:** Partial — permission-group add-user dialog not opened in browser; implementation and unit coverage elsewhere reviewed as low risk (see §5 residual).

## 4. Checklist

### Frontend verification

- [x] API parameters validated
- [x] UI behavior confirmed
- [x] Error handling verified

### Backend verification

- [x] API test cases written and run
- [x] Logs checked
- [x] Performance checked (if applicable) — N/A for this change

### Integration

- [x] End-to-end flow tested (automated suite + health; AD bind not in CI)
- [x] Edge cases tested (see §3 mapping below)

### Documentation

- [x] Requirement doc completed
- [x] Code comments added (if applicable)

## 5. Test results

### Test run date

- **2026-04-09** (QA Step 5)

### Test results

#### §3 coverage mapping

| ID | Result | Evidence |
|----|--------|----------|
| TC-01 | Pass | `AuthServiceTest.login_withEmployeeNumber_primaryPath_succeeds` — trim, resolves to correct `id`, `selfContext.employeeNumber` normalized |
| TC-02 | Pass | Legacy `LoginRequest(Long userId, password)` paths in `AuthServiceTest` (`login_populatesAuthoritativeSelfContext`, etc.) and `AuthControllerTest.login_returnsSelfContextInUserPayload` |
| TC-03 | Pass (impl) | `AuthService.loginLocal` throws `INVALID_CREDENTIALS` when no row for trimmed 사번; **no dedicated JUnit named for unknown-사번** — same branch as “user not found” for employee path |
| TC-04 | Pass | `AuthServiceTest.login_withDuplicatedActiveEmployeeNumber_throwsDuplicatedCode` → `USER_EMPLOYEE_NUMBER_DUPLICATED` |
| TC-05 | Pass | `login_withBothEmployeeNumberAndUserId_throwsInvalidInput`, `login_withNeitherEmployeeNumberNorUserId_throwsInvalidInput` |
| TC-06 | Pass | `AuthServiceTest` / `AuthControllerTest` assert `selfContext.employeeNumber` |
| TC-07 | Pass | Backend: `resolveSelfContext` returns null `employeeNumber` when DB null; Frontend: `UserManagement.test.js` expects **“사번 미등록”** when grid user lacks 사번; `getEmployeeNumberDisplay` / `UserContextFilterBlock` default fallback |
| TC-08 | N/A (env) | **AD bind not executed in QA run**; `AuthServiceTest.login_adMode_withEmployeeNumberOrUserId_rejectedAsInvalidInput` covers **reject** `employeeNumber`/`userId` in AD mode |
| TC-09 | Pass | `LoginForm.test.js` — body `{ employeeNumber, password }`, label 사번 |
| TC-10 | Partial | Jest: `UserContextFilterBlock.test.js` locked display shows `EMP-001`; Browser: login page Pass; full post-login navigation skipped (credentials) |
| TC-11 | Pass | `UserManagement.test.js` — prefers `employeeNumber` / snake_case |
| TC-12 | Partial | No dedicated Jest for panel; `PermissionGroupPanel.js` uses 사번 display + fallback; manual dialog not opened |
| TC-13 | Pass | Full `mvn test` green including `SearchHistoryControllerTest` etc.; path `{userId}` unchanged by contract |
| TC-14 | Pass | Manual review: `docs/api-definition.md` documents XOR login and `selfContext.employeeNumber` |
| TC-15 | Pass | QA updated `.cursor/skills/auth-permission-domain/SKILL.md`, `api-permission-map/SKILL.md`, `search-consistency-domain/SKILL.md`; ran `node scripts/generate-treemap.js` → `docs/cursor-tools-treemap.html` |

#### Frontend

- **Command:** `cd frontend && npm test -- --watchAll=false`
- **Result:** **Pass** (exit 0)

#### Backend

- **Command:** `cd backend && mvn test -q`
- **Result:** **Pass** (exit 0)

**Verification (restart + health):**

- **Command:** `./scripts/dev-services.sh all restart`; waited for services; `curl` checks.
- **Backend** `GET http://localhost:9200/api/health` → **200**, JSON `success:true`
- **Frontend** `http://localhost:3001` → **HTTP 200**
- **DB** `GET http://localhost:9200/api/db/test` → **`data.connected:true`**

**Outcome:**

- **Pass** — ready for commit (scope: employee-number identifier; see residual notes).

### Issues found and resolution

- **Skills/docs drift (TC-15):** Step 4 had not refreshed Cursor skills to match shipped auth/selfContext behavior. **Resolution:** QA aligned `auth-permission-domain`, `api-permission-map`, `search-consistency-domain` skills and regenerated treemap (2026-04-09).

### Next steps

- Optional: add explicit `AuthServiceTest` for local login with **unknown 사번** (TC-03) for clearer regression naming.
- Optional: browser smoke logged-in user for TC-10/TC-12 when a safe non-prod test account is available.
- §7 Korean final version: per `DOCUMENT-LANGUAGE-POLICY.md`, **Requirements** may add after sign-off.

### Residual risk

- **AD full path (TC-08):** Not exercised against a real directory in this run; covered by unit rejection rules and code review.
- **Unknown-사번 (TC-03):** Behavior verified in source; no single named JUnit.
- **Post-login UI (TC-10/TC-12):** Partial browser depth without credentials.

## 6. Error remedy result (cause and action) — for error/bug fix requirements only

N/A (feature requirement).

---

## 7. Final version (Korean) — add after all verification is complete

*(Per `docs/workflow/DOCUMENT-LANGUAGE-POLICY.md` — after QA verification.)*

---

**Author:** Requirements (subagent)  
**Date:** 2026-04-09  
**Status:** QA verified (Step 5 complete; commit by QA)  
