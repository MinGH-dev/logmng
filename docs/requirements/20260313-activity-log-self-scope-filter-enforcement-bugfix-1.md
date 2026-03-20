# 20260313-activity-log-self-scope-filter-enforcement-bugfix-1 — Verify runtime scope source for user2 before self-only bugfix rework

**Parent requirement ID**: `20260313-activity-log-self-scope-filter-enforcement`  
**Bugfix sequence**: 1

---

## §1. Failure description / user impact

### Discovery

- **When**: During user QA/manual verification after the parent requirement implementation
- **What failed**:
  - Logged in as `user2`, **User Activity Log** still showed all users in the same department instead of current-user-only rows.
  - When searching for other-department user `user3`, the screen returned the current department user set rather than current-user-only rows.
  - The expected behavior remained current-user-only because `user2` was assumed to have `activity-log scope=self`.

### User impact

- If `user2` is truly `scope=self` at runtime, the behavior is an access-control regression that exposes other users' activity logs.
- If `user2` is actually `scope=team` at runtime, the system is behaving consistently with the runtime scope, but the verification data/setup is incorrect and can hide a configuration or contract-drift problem.
- In either case, the current verification result is not reliable until the source of truth for `screenScopes['activity-log']` is re-verified from DB permission mapping through login/session to frontend rendering.

### Current observation summary

- The observed behavior is more consistent with `scope=team` than with a broken `scope=self` enforcement path:
  - same-department users remain visible
  - searching an out-of-department user narrows back to the in-department result set instead of widening to all users
- Therefore this child requirement must first determine whether the failure is:
  1. a true `scope=self` enforcement bug,
  2. a runtime permission/source-of-truth mismatch, or
  3. a verification/setup assumption error about `user2`.

---

## §2. Error scope, cause, fix design, change-target verification

### 2.1 Security review

- [x] Security review performed
- **Risks**:
  - If a user expected to be `scope=self` can view same-department users, the screen permits horizontal access beyond the intended current-user boundary.
  - If null/blank scope configuration is silently interpreted as a broader scope, configuration omissions can turn into hidden over-permission states.
  - Frontend hiding is not a security control. If runtime `screenScopes['activity-log']` is wrong or stale, the UI and backend can consistently expose a broader scope while still appearing internally consistent.
- **Acceptance / recommendations**:
  - The requirement must verify the authoritative scope chain end to end: permission source data -> login/session `screenScopes` -> frontend branch -> backend effective scope.
  - `activity-log` with effective `scope=self` must continue to ignore or override widening user-range inputs such as `department`, `username`, `userId`, `departmentCode`, and `ipAddress`.
  - The team must explicitly confirm whether `null/blank -> team` is still the intended contract for scope-supporting screens. If that default remains intentional, seeded test users and QA procedures must stop assuming `self` without checking runtime scope first. If that default is no longer acceptable for security-sensitive screens, contract, implementation, and tests must change together.
  - Verification artifacts must prove the runtime value of `screenScopes['activity-log']` for the tested account before evaluating the search result as pass/fail.

### 2.2 Codebase summary

- **Data / permission source**
  - `backend/src/main/resources/db/init-data.sql` inserts `permission_group_screen` rows for `GENERAL_USER` and `ADMIN_EXT` without explicit `scope` values.
  - The current project contract and backend code interpret omitted or null scope as `team`, not `self`, for scope-supporting screens.
  - The project also documents single permission-group assignment per user, so comments or legacy assumptions about multi-group membership are not sufficient proof of the live runtime scope for `user2`.
- **Backend / auth-session**
  - `backend/src/main/java/com/logmng/service/PermissionGroupService.java` resolves per-screen scope from `permission_group_screen` and maps null/blank to `team`.
  - `backend/src/main/java/com/logmng/service/AuthService.java` exposes those resolved scopes as the authenticated user's `screenScopes`.
  - `backend/src/main/java/com/logmng/util/ScopeHelper.java` also defaults missing scope to `team`.
  - `backend/src/main/java/com/logmng/controller/UserActivityLogController.java` applies current-user-only enforcement only when the effective scope is actually resolved as `self`.
- **Frontend**
  - `frontend/src/components/UserActivityLog/UserActivityLogList.js` hides user filters and sanitizes outgoing requests only when `user.screenScopes['activity-log'] === 'self'`.
  - `frontend/src/components/UserActivityLog/UserActivityLogSearchForm.js` clears hidden user-context values only under the same `self` branch.
- **Contract / docs**
  - `docs/contract.md`, `docs/api-definition.md`, and `specs/permission-group-hierarchy.spec.yaml` document `scope=self` as current-user-only and document omitted/null scope as `team`.
- **Implication**
  - The parent requirement fixed the `self` enforcement path, but the reported live behavior now points first to a runtime-scope or test-data mismatch rather than immediately to a broken `self` normalization path.

### 2.3 Problem analysis

1. The failure report assumes `user2` is `activity-log scope=self`, but the current configuration evidence points to `user2` resolving to `team` unless a live DB override or permission-group edit explicitly sets `self`.
2. The observed result pattern matches `team` semantics: same-department rows remain visible, while an out-of-department user search does not widen to all users.
3. Because both frontend behavior and backend enforcement are driven by the same runtime scope value, a wrong or stale `screenScopes['activity-log']` can make the application appear consistently "wrong" even when each layer is following the runtime contract it received.
4. The current QA/manual verification did not first prove the tested account's effective runtime scope from source data through session. That gap prevents a reliable conclusion about whether the parent bugfix failed.
5. This is therefore a mixed error-fix requirement:
   - primary suspicion: permission/source-of-truth mismatch or verification-data mismatch
   - secondary suspicion: defaulting-policy drift with security impact
   - lower-probability fallback: an actual runtime `self` enforcement bug still exists despite the parent fix

### 2.4 Solution approach

**Data / permission source:**
- Verify the live permission-group assignment for `user2` and the actual `permission_group_screen.scope` value for `activity-log`.
- If `user2` is intended to be the canonical self-scoped QA user, make that mapping explicit in seed/config/setup rather than relying on null/default behavior or outdated assumptions.
- Verify whether the current seed comments, verification notes, and live DB state describe the same test-user scope contract.

**Backend / auth-session:**
- Verify the full scope resolution chain for `activity-log`: `PermissionGroupService.getScreenScopesForUser(...)` -> `AuthService.resolveScreenScopes(...)` / login response -> `ScopeHelper.resolveScope(...)` -> `UserActivityLogController`.
- Add or extend automated coverage for "DB permission mapping -> runtime `screenScopes` -> effective controller scope" so future QA failures can distinguish configuration mismatch from enforcement regression.
- If product/security decides that null/blank scope must no longer widen to `team`, update the contract and backend defaulting logic together; otherwise retain the current contract and fix the seeded test-user mapping and QA procedure instead.

**Frontend:**
- Verify the runtime `user.screenScopes['activity-log']` value used by **User Activity Log** before treating visible filters or department-wide results as a frontend bug.
- Keep the screen logic aligned with backend enforcement: `self` must hide and sanitize, while `team` / `all` must remain consistent with the authenticated runtime scope.
- Add regression or diagnostic coverage that proves the screen follows the delivered runtime scope instead of assumed test-account scope.

**Contract / docs / Cursor tools:**
- Verify whether the current contract wording is already sufficient for the null/blank default policy and for the requirement to confirm runtime `screenScopes` during QA.
- If the intended policy or seeded QA-user mapping changes, update `docs/contract.md`, `docs/api-definition.md`, `specs/permission-group-hierarchy.spec.yaml`, and the relevant permission/auth Cursor skills in the same work so future agents do not repeat the wrong assumption.

### 2.5 Affected scopes and change-target verification

| Scope | Affected? | §2 subsection and change file list complete? |
|-------|-----------|-----------------------------------------------|
| Backend | Yes | Yes |
| Frontend (config UI + view screen) | Yes (view-screen verification and runtime-scope consumption; permission-config UI only if seeded mapping is corrected there) | Yes |
| DB | Yes (seed / live permission data verification) | Yes |
| Contract / Spec | Yes | Yes |
| Cursor tools (skills, specs) | Yes (if scope defaulting policy or canonical QA-user mapping changes) | Yes |

**Applied pattern checks from `docs/workflow/REQUIREMENTS-CHANGE-TARGET-CHECKLIST.md`:**
- **3.1 Scope-supporting screen**: covered for runtime scope source, auth/session propagation, frontend branch behavior, backend effective enforcement, and contract/spec alignment.
- **3.2 Permission or screen-access change**: treated as a permission/source-of-truth verification and repair case rather than a new permission model.

**Change target verification:** completed against `docs/workflow/REQUIREMENTS-CHANGE-TARGET-CHECKLIST.md` before finalizing §2.

### 2.6 Planned change file list (expected change targets)

#### DB / setup
- `backend/src/main/resources/db/init-data.sql`
  - Must verify whether the seeded `user2` mapping and any canonical self-scoped QA user are explicit and aligned with the current scope contract.

#### Backend
- `backend/src/main/java/com/logmng/service/PermissionGroupService.java`
  - Must verify the authoritative `activity-log` scope resolution path from `permission_group_screen.scope`, including null/blank handling.
- `backend/src/main/java/com/logmng/service/AuthService.java`
  - Must verify that login/session/current-user responses surface the same effective `screenScopes['activity-log']` seen by frontend and controller code.
- `backend/src/main/java/com/logmng/util/ScopeHelper.java`
  - Must verify that defaulting and normalization rules stay aligned with the intended contract for `self`, `team`, and `all`.
- `backend/src/main/java/com/logmng/controller/UserActivityLogController.java`
  - Must verify that the repaired `scope=self` enforcement still applies when runtime scope is truly `self`, and that observed `team` behavior is not misclassified as a controller regression.
- `backend/src/test/java/com/logmng/controller/UserActivityLogControllerTest.java`
  - Must add or extend coverage for runtime-scope proof and for the distinction between real `self` enforcement and valid `team` behavior.

#### Frontend
- `frontend/src/components/UserActivityLog/UserActivityLogList.js`
  - Must verify that UI branching and request sanitization depend only on delivered runtime `screenScopes['activity-log']`, not on a stale verification assumption.
- `frontend/src/components/UserActivityLog/UserActivityLogSearchForm.js`
  - Must verify that hidden or visible filter behavior stays aligned with the same runtime scope value.
- `frontend/src/components/UserActivityLog/UserActivityLogList.test.js`
  - Must add or extend coverage proving that the screen behaves as `self` only when runtime scope is `self`, and otherwise behaves as `team` or `all`.

#### Contract / Spec / docs
- `docs/contract.md`
  - Must verify and, if needed, clarify the null/blank scope default policy and the requirement to confirm runtime `screenScopes` for QA users before evaluating access-control behavior.
- `docs/api-definition.md`
  - Must verify and, if needed, clarify how login/current-user responses expose `screenScopes` and how QA should interpret `activity-log` results for `self` vs `team`.
- `specs/permission-group-hierarchy.spec.yaml`
  - Must verify and, if needed, clarify the authoritative scope source, null/default behavior, and canonical test-user expectations for scope-sensitive verification.

#### Cursor tools
- `.cursor/skills/auth-permission-domain/SKILL.md`
  - Must stay aligned with the confirmed runtime source of `activity-log` scope and any updated defaulting/security policy.
- `.cursor/skills/api-permission-map/SKILL.md`
  - Must stay aligned with the requirement to trace runtime scope from permission data through controller enforcement.

---

## §3. Test plan

### Test case list

| ID | Scope | Type | Scenario (input / condition) | Expected result | Verification |
|----|-------|------|------------------------------|-----------------|-------------|
| TC-01 | Integration | Discovery | Login as `user2` and inspect login or `/api/auth/me` response for `screenScopes['activity-log']` | The runtime scope is captured as explicit evidence (`self`, `team`, or `all`) before any behavior judgment | Integration / manual |
| TC-02 | Backend | Discovery | Inspect the live permission-group assignment for `user2` and the linked `permission_group_screen.scope` value for `activity-log` | DB/config source matches the runtime scope returned to the client | Integration / manual |
| TC-03 | Backend | Regression | If TC-01/TC-02 prove `user2` is truly `scope=self`, call `POST /api/activity-log/search` with `department=All`, `username=user3`, and `userId=user3` | Response contains only `user2` rows; widening inputs are ignored or overridden | Unit / integration |
| TC-04 | Integration | Classification | If TC-01/TC-02 prove `user2` is actually `scope=team`, repeat the reported browser/API searches | Results are reclassified as valid `team` behavior, and QA switches to an explicitly self-scoped user for `self` verification | Integration / manual / browser |
| TC-05 | Frontend | Regression | Render **User Activity Log** with `screenScopes['activity-log'] = self`, then with `team` | `self` hides and sanitizes; `team` shows user filters and does not follow the `self` branch | Unit / manual / browser |
| TC-06 | Contract / Docs | Documentation | Review contract/spec/skill updates after the fix | Runtime scope source, null/default behavior, and QA verification steps are documented consistently | Manual review |

### Test scenarios

#### Scenario 1: Prove the tested account's runtime scope before reproducing the bug
1. Login as `user2`.
2. Capture the authenticated `screenScopes['activity-log']` value from login response, `/api/auth/me`, or an equivalent verified session source.
3. Record the observed scope and only then interpret UI/API behavior.

#### Scenario 2: Compare runtime scope to permission source data
1. Identify the live permission group assigned to `user2`.
2. Inspect the linked `permission_group_screen.scope` for `activity-log`.
3. Verify that the DB/config value and runtime `screenScopes['activity-log']` agree.

#### Scenario 3: Re-run search behavior with a proven self-scoped user
1. Use a user whose runtime scope is proven as `self`.
2. Search with widened inputs (`department=All`, `username`, `userId`) that target another user.
3. Verify that the result remains current-user-only.

#### Scenario 4: Re-run search behavior with a proven team-scoped user
1. Use a user whose runtime scope is proven as `team`.
2. Search same-department and other-department users on **User Activity Log**.
3. Verify that results stay inside the department allowlist and are not misreported as a `self` regression.

### Test data

- `user2` only if TC-01 and TC-02 prove that its runtime `activity-log` scope matches the intended verification scenario.
- One explicitly self-scoped non-admin user for `self` verification if `user2` is not self at runtime.
- One same-department peer user and one out-of-department user (`user3` or equivalent) so `self` vs `team` behavior can be distinguished.
- If the project keeps null/blank scope as `team`, QA must not rely on an unlabeled/default-scoped user as the canonical `self` test account.

### Test environment

- Frontend: `http://localhost:3001`
- Backend: `http://localhost:9200`
- Database: project default database with live `permission_group`, `permission_group_screen`, and `app_user_permission_group` data

---

## §5. Verification

- Pending. This child requirement formalizes the investigation and repair scope; implementation and re-verification will update this section.

---

## Handoff

- **To Backend**: verify the authoritative runtime scope chain and repair source-of-truth drift, defaulting behavior, or enforcement only where the investigation proves it is necessary. After fix + build + restart, hand off to **QA**.
- **To Frontend**: verify runtime-scope consumption and screen branching only after backend/runtime scope evidence is confirmed. After fix + build + restart, hand off to **QA**.
- **To QA**: begin every re-verification run by proving the tested account's runtime `screenScopes['activity-log']` value, then evaluate the search result against the correct scope contract.
