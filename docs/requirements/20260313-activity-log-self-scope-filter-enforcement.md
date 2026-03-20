# 20260313 - Activity log self-scope filter enforcement

## 1. User requirement

### Requirement description
Fix the `activity-log` scope-enforcement regression for non-admin users whose effective screen scope is `self`. In the reported scenario, `user2` is configured as `scope=self` for **User Activity Log**, but selecting the Department filter as "all" or searching by `username` / `userId` still returns logs for other users. This bugfix must restore the existing permission contract first, then update any stale permission-related docs, specs, or Cursor tooling guidance that could mislead future implementations.

This is an error-fix requirement, not a new feature. The intended contract already exists in `docs/contract.md`, `docs/api-definition.md`, and `specs/permission-group-hierarchy.spec.yaml`: `activity-log` with `scope=self` must stay fixed to the current authenticated user, and user-range filters must not widen that scope.

### User scenario
1. A non-admin user opens **User Activity Log** with effective `screenScopes['activity-log'] === 'self'`.
2. The user changes the Department filter to the local "All / 전체" selection, or types another user's `username` / `userId` into the search form.
3. The user runs search and expects the result to remain limited to the current authenticated user's own activity logs.
4. **Problem**: the current behavior can return logs for all users or for users outside the current user's permitted scope, which violates the `self` access-control contract.
5. The user expects both the visible UI behavior and the backend-enforced query behavior to stay aligned with the same `self` rule.
6. If permission-related docs, specs, or Cursor tools describe the scope behavior incompletely or ambiguously, they must be corrected in the same work so the regression does not recur.

### Expected outcome
- `activity-log` with effective `scope=self` must always return only the current authenticated user's logs, regardless of Department "All / 전체", `username`, `userId`, or equivalent user-range inputs.
- For `activity-log` with effective `scope=self`, the backend must treat user-range parameters as non-authoritative and must ignore or safely override them before query execution.
- The frontend must keep the `scope=self` user filter hiding and request sanitization behavior aligned with the authenticated screen scope so the UI does not suggest or attempt forbidden widening.
- `scope=team` must remain a narrowing-only view of the current user's same-department user set, and `scope=all` must remain the only mode that can legitimately return all users' logs.
- `docs/contract.md`, `docs/api-definition.md`, and `specs/permission-group-hierarchy.spec.yaml` must remain synchronized with the real `activity-log` enforcement behavior, including `username` / `userId` regression coverage.
- If permission-related Cursor guidance is stale, the relevant `.cursor/skills/**`, rules, and spec references must be updated so future agents and implementers read the same `activity-log self` contract.

## 2. Design

### 2.1 Security review (optional; when PII / decryption / access control)
- [x] Security review performed
- Risks:
  - If a `scope=self` user can widen results through Department "All / 전체", `username`, or `userId`, the screen permits horizontal privilege escalation and unauthorized exposure of other users' activity logs.
  - UI-only hiding is insufficient. If the backend does not enforce the same rule, direct API requests can bypass the UI and still retrieve out-of-scope data.
  - If request normalization differs across `self`, `team`, and `all`, future regressions can reintroduce hidden widening paths even when the current UI looks correct.
- Acceptance / recommendations:
  - The backend must remain the single source of truth for `activity-log` scope enforcement.
  - `scope=self` must safely ignore or override `department`, `username`, `userId`, and other user-range inputs before query execution.
  - The requirement must preserve data minimization in logs and errors; debugging or rejection paths must not overexpose requested third-party identifiers.

### 2.2 Codebase summary
- **Frontend**
  - `frontend/src/components/UserActivityLog/UserActivityLogList.js` calculates `hideUserFilters` from `user.screenScopes['activity-log']` and strips `userId`, `username`, `department`, and `ipAddress` from outgoing search requests only when that scope is `self`.
  - `frontend/src/components/UserActivityLog/UserActivityLogSearchForm.js` renders the shared user-context block and extra conditions and hides them when `hideUserFilters` is true.
  - `frontend/src/components/UserActivityLog/UserActivityLogList.test.js` currently covers department-option loading from the shared filter-options API, but it does not yet cover the `self` regression for `username` / `userId` / Department "All / 전체" search attempts.
- **Backend**
  - `backend/src/main/java/com/logmng/controller/UserActivityLogController.java` resolves `activity-log` scope and, for `self`, forces `request.userId` to the current username while clearing `username`, `department`, and `ipAddress`; for `team`, it derives `allowedUserIds` from the current user's department.
  - `backend/src/main/java/com/logmng/service/UserActivityLogService.java` applies SQL filtering from `allowedUserIds`, `userId`, `department`, `username`, `actionType`, and `ipAddress`.
  - `backend/src/main/java/com/logmng/service/AuthService.java` and the login/session path are responsible for surfacing `screenScopes` to the frontend and to controller-side authorization logic.
  - `backend/src/test/java/com/logmng/controller/UserActivityLogControllerTest.java` covers `scope=self` with `userId` and `department`, but it does not yet cover `username` regression or broader scope-enforcement consistency.
- **Contract / Specs / docs**
  - `docs/contract.md`, `docs/api-definition.md`, and `specs/permission-group-hierarchy.spec.yaml` already describe `activity-log scope=self` as current-user-only behavior.
  - The current contract wording must be rechecked for explicit `username` regression coverage and for consistent wording between `activity-log`, shared filter-option guidance, and general screen-scope rules.
- **Known uncertainty**
  - TODO: verify whether the reported live failure used a seeded `user2` account whose effective `activity-log` scope is actually `self` in the current local dataset, or whether QA/setup must create or reassign an equivalent self-scoped user before reproduction.
  - TODO: verify whether the failing Department "all" path is represented by an empty string, a local "All / 전체" label, or another normalized value in the live request path. The implementation must cover every representation that means "all".

### 2.3 Technical design

#### Problem analysis
1. The documented `activity-log self` contract says user-range filters must not widen scope, but the reported runtime behavior indicates a drift between that contract and the effective screen behavior.
2. The regression can originate in multiple layers: stale or incorrect `screenScopes` delivery, incorrect frontend hiding/sanitization, incomplete controller normalization, or service/query logic that still honors widened user-range filters after controller-side adjustments.
3. Existing automated coverage is too narrow for this bug class. Current controller tests cover `self + userId + department`, but not `self + username`, not the local "all department" representation, and not regression protection for `team` narrowing versus `all`.
4. Because `activity-log`, `statistics`, and `search-history` already share scope vocabulary and permission-spec references, stale permission docs or Cursor skills can cause repeated implementation drift even if this specific screen is fixed in code.

#### Solution approach
**Frontend:**
- Verify that `frontend/src/components/UserActivityLog/UserActivityLogList.js` consumes the latest authenticated `screenScopes['activity-log']` state and does not send `department`, `username`, `userId`, or `ipAddress` when the effective scope is `self`.
- Verify that `frontend/src/components/UserActivityLog/UserActivityLogSearchForm.js` keeps the shared user-context block and extra conditions hidden for `scope=self`, and that reset/search flows do not restore prohibited user-range parameters for that scope.
- Add regression coverage so the `activity-log` screen behavior is exercised for `scope=self`, including request sanitization and hidden-filter behavior.

**Backend:**
- Verify that the effective `activity-log` scope delivered by `AuthService` / session state is correct for the authenticated user and remains the authoritative source for enforcement.
- Verify that `UserActivityLogController` normalizes `scope=self` requests by forcing the current user and clearing every user-range filter that can widen scope, including `department`, `username`, `userId`, and equivalent "all" representations.
- Verify that `scope=team` continues to narrow to the same-department user set first, then applies optional filters only inside that permitted set, and that `scope=all` remains the only path that returns all users.
- Add or extend backend automated tests so controller and service/query logic cover `self + userId`, `self + username`, `self + department/all`, `team + widening attempt`, and `all + legitimate cross-user search`.

**Contract / Spec / docs:**
- Verify that `docs/contract.md`, `docs/api-definition.md`, and `specs/permission-group-hierarchy.spec.yaml` explicitly describe `activity-log self` as ignoring or overriding `department`, `username`, and `userId`, not only some subset of those parameters.
- If the contract wording is less explicit than the required behavior, update it in the same implementation so code, docs, and tests describe the same access-control rule.

**Cursor tool update targets:**
- `.cursor/skills/auth-permission-domain/SKILL.md`
  - Must stay aligned with the rule that screen scope controls list/view range and that `activity-log self` cannot be widened by user-range filters.
- `.cursor/skills/api-permission-map/SKILL.md`
  - Must remain aligned with the effective API permission check path for `activity-log` search and with requirement-doc completeness for access-control tests.
- `.cursor/skills/search-consistency-domain/SKILL.md`
  - Must continue to state that `scope=self` hides user filters on user-context screens and must not let frontend/request behavior contradict backend enforcement.
- `specs/permission-group-hierarchy.spec.yaml`
  - Must remain the single contract reference for `activity-log` scope enforcement, including explicit `username` / `userId` / Department "all" behavior if current wording is not precise enough.

### Affected scopes and change targets (verification)

| Scope | Affected? | §2 subsection and change file list complete? |
|-------|-----------|-----------------------------------------------|
| Backend | Yes | Yes |
| Frontend (config UI + view screen) | Yes (view screen only; no permission-config UI change is planned unless stale scope delivery is traced there) | Yes |
| DB | No | N/A |
| Contract / Spec | Yes | Yes |
| Cursor tools (skills, specs) | Yes | Yes |

**Applied pattern checks from `docs/workflow/REQUIREMENTS-CHANGE-TARGET-CHECKLIST.md`:**
- **3.1 Scope-supporting screen**: covered for `activity-log` scope resolution, controller/service filtering, contract/spec verification, and auth/permission tool references.
- **3.2 Permission or screen-access change**: treated as an enforcement-repair case; the requirement verifies backend access checks and auth/session scope propagation without introducing a new permission model.

**Change target verification:** completed against `docs/workflow/REQUIREMENTS-CHANGE-TARGET-CHECKLIST.md` before finalizing §2.

### Planned change file list (expected change targets)

**(Planned at authoring. Implementing agent (Step 4) confirms or amends this list when implementation is complete.)**

#### Frontend
- `frontend/src/components/UserActivityLog/UserActivityLogList.js`
  - Must verify and, if needed, correct `activity-log self` request sanitization so Department "All / 전체", `username`, and `userId` cannot widen scope from the screen layer.
- `frontend/src/components/UserActivityLog/UserActivityLogSearchForm.js`
  - Must verify and, if needed, correct `scope=self` hidden-filter behavior so the screen does not expose or restore forbidden user-range inputs.
- `frontend/src/components/UserActivityLog/UserActivityLogList.test.js`
  - Must add regression coverage for `scope=self` sanitization and hidden-filter behavior, not only department-option loading.

#### Backend
- `backend/src/main/java/com/logmng/controller/UserActivityLogController.java`
  - Must verify and, if needed, correct request normalization for `scope=self` and `scope=team`.
- `backend/src/main/java/com/logmng/service/UserActivityLogService.java`
  - Must verify and, if needed, correct SQL/query filtering so widened user-range filters cannot bypass enforced scope.
- `backend/src/main/java/com/logmng/service/AuthService.java`
  - Must verify that `activity-log` scope is delivered correctly to controller logic and frontend consumers for the authenticated user.
- `backend/src/main/java/com/logmng/util/ScopeHelper.java`
  - Must verify that `activity-log` scope resolution still matches the documented contract for `self`, `team`, and `all`.
- `backend/src/main/java/com/logmng/dto/request/UserActivityLogSearchRequest.java`
  - Must keep server-only scope-enforcement fields non-authoritative from client input.
- `backend/src/test/java/com/logmng/controller/UserActivityLogControllerTest.java`
  - Must extend automated coverage for `self + username`, `self + department/all`, and `team/all` regression boundaries.
- `backend/src/test/java/com/logmng/service/UserActivityLogServiceTest.java`
  - Must be added or extended so service/query enforcement is covered independently of controller normalization.
- `backend/src/test/java/com/logmng/util/ScopeHelperTest.java`
  - Must cover shared normalization for all-like department values and server-managed allowlists.

#### Contract / Spec / docs
- `docs/contract.md`
  - Must verify and, if needed, clarify `activity-log self` handling for `department`, `username`, and `userId`.
- `docs/api-definition.md`
  - Must verify and, if needed, clarify `POST /api/activity-log/search` scope behavior and ignored/overridden filter semantics.
- `specs/permission-group-hierarchy.spec.yaml`
  - Must verify and, if needed, clarify `activity-log` screen-row wording under screen-based API enforcement so the `self` rule covers `department`, `username`, and `userId` explicitly.

#### Cursor tools
- `.cursor/skills/auth-permission-domain/SKILL.md`
  - Must stay aligned with the repaired `activity-log self` scope behavior.
- `.cursor/skills/api-permission-map/SKILL.md`
  - Must stay aligned with the effective permission-enforcement path and required regression coverage.
- `.cursor/skills/search-consistency-domain/SKILL.md`
  - Must stay aligned with `scope=self` hide/omit behavior for user-context screens if the current wording is incomplete.

**Frontend implementation note (actual Step 4 change set):**
- Actual frontend code/test changes were applied in `frontend/src/components/UserActivityLog/UserActivityLogList.js`, `frontend/src/components/UserActivityLog/UserActivityLogSearchForm.js`, and `frontend/src/components/UserActivityLog/UserActivityLogList.test.js`.
- Actual frontend Cursor guidance change was applied in `.cursor/skills/search-consistency-domain/SKILL.md` so `activity-log scope=self` explicitly covers request sanitization for Department "All / 전체", `username`, `userId`, and `ipAddress`.

**Backend implementation note (actual Step 4 change set):**
- `AuthService.java` was inspected for `screenScopes['activity-log']` delivery and did not require a code change for this bugfix.
- Actual backend code/test changes are expected in `UserActivityLogController`, `UserActivityLogService`, `UserActivityLogSearchRequest`, `ScopeHelper`, `UserActivityLogControllerTest`, `UserActivityLogServiceTest`, and `ScopeHelperTest`.
- Actual Cursor guidance changes are expected in `.cursor/skills/auth-permission-domain/SKILL.md` and `.cursor/skills/api-permission-map/SKILL.md`.

#### DB
- None.

## 3. Test approach

### Test case list (required)

| ID | Scope | Type | Scenario (input / condition) | Expected result | Verification (unit / integration / manual) |
|----|-------|------|------------------------------|-----------------|--------------------------------------------|
| TC-01 | Backend | Regression | Authenticated `activity-log` user with effective `scope=self` calls `POST /api/activity-log/search` with `userId=<otherUser>` | Response contains only the current authenticated user's logs | Unit / integration |
| TC-02 | Backend | Regression | Authenticated `activity-log` user with effective `scope=self` calls `POST /api/activity-log/search` with `username=<otherUserName>` | Response contains only the current authenticated user's logs; `username` does not widen scope | Unit / integration |
| TC-03 | Backend | Regression | Authenticated `activity-log` user with effective `scope=self` calls `POST /api/activity-log/search` with Department set to the local "All / 전체" representation | Response contains only the current authenticated user's logs; department "all" does not widen scope | Unit / integration |
| TC-04 | Backend | Regression | Authenticated `activity-log` user with effective `scope=self` sends combined widened inputs (`department`, `username`, `userId`) in one request | Response still contains only the current authenticated user's logs; every user-range filter is ignored or safely overridden | Unit / integration |
| TC-05 | Backend | Normal | Authenticated `activity-log` user with effective `scope=team` searches with `username` / `userId` for a user outside the same department | Response stays inside the same-department allowed user set and does not return out-of-team rows | Unit / integration |
| TC-06 | Backend | Normal | Authenticated `activity-log` user with effective `scope=all` searches with `username` / `userId` for another user | Response legitimately returns matching rows across users, proving only `all` allows that scope | Unit / integration |
| TC-07 | Frontend | Regression | Render **User Activity Log** for a user whose `screenScopes['activity-log']` is `self` | User-context and extra-condition blocks stay hidden, and outgoing search requests omit `department`, `username`, `userId`, and `ipAddress` | Unit / manual / browser |
| TC-08 | Frontend | Normal | Render **User Activity Log** for a user whose `screenScopes['activity-log']` is `team` or `all` | User-context filters remain visible and normal filter submission remains available for the permitted scope | Unit / manual / browser |
| TC-09 | Integration | Regression | Login as the reported self-scoped user (requested scenario: `user2`) and search by Department "All / 전체", `username`, and `userId` on **User Activity Log** | Every search result remains limited to that authenticated user's own logs | Integration / browser |
| TC-10 | Contract / Docs | Documentation | Review `docs/contract.md`, `docs/api-definition.md`, `specs/permission-group-hierarchy.spec.yaml`, and listed Cursor tools after implementation | `activity-log self` enforcement is described consistently for `department`, `username`, and `userId` | Manual review |

### Test scenarios

#### Scenario 1: Self scope cannot be widened from the API
1. Prepare an authenticated non-admin user whose effective `activity-log` scope is `self`.
2. Call `POST /api/activity-log/search` with `department`, `username`, and `userId` values that target another user or imply all users.
3. Verify that the backend normalizes the request and returns only the current authenticated user's rows.

#### Scenario 2: Team scope remains narrowing-only
1. Prepare an authenticated non-admin user whose effective `activity-log` scope is `team`.
2. Search with `department`, `username`, or `userId` values that target a user outside the current user's department.
3. Verify that the result set stays inside the allowed same-department user set and does not widen to all users.

#### Scenario 3: Frontend self behavior stays aligned with backend enforcement
1. Render **User Activity Log** with `screenScopes['activity-log'] === 'self'`.
2. Verify that user-context and extra-condition filters are hidden.
3. Trigger initial load, search, reset, and page-related requests.
4. Verify that outgoing requests omit user-range parameters and that visible UI behavior matches the backend contract.

#### Scenario 4: All scope still supports legitimate cross-user search
1. Prepare an authenticated user whose effective `activity-log` scope is `all`.
2. Search by another user's `username` or `userId`.
3. Verify that matching rows across users are returned, confirming that the repaired enforcement does not break legitimate `all` behavior.

### Test data
- One authenticated non-admin user whose effective `activity-log` scope is `self`.
- One authenticated non-admin user whose effective `activity-log` scope is `team`.
- One authenticated admin or `scope=all` user for cross-user search regression coverage.
- At least two distinct activity-log owners in different departments so same-user, same-department, and cross-department cases are all observable.
- TODO: confirm whether the existing local seed maps `user2` to `activity-log=self`; if not, create or reassign an equivalent self-scoped user before QA verification.

### Test environment
- Frontend: `http://localhost:3001`
- Backend: `http://localhost:9200`
- Database: project default database with seeded `app_user`, permission-group, and `user_activity_log` data

### 3.5 Browser automation verification (optional — for frontend-heavy requirements)
- **Applicable TCs**: TC-07, TC-08, TC-09
- **Procedure per TC**: login as the relevant user, open **User Activity Log**, inspect whether the user-context block is hidden or visible per scope, run searches, and confirm the rendered table and network requests stay within the expected scope contract.

## 4. Checklist

### Frontend verification
- [ ] `activity-log self` hides user-context and extra-condition filters
- [ ] `activity-log self` omits user-range parameters from requests
- [ ] `team` / `all` UI behavior remains intact

### Backend verification
- [ ] `activity-log self` returns only the current authenticated user's logs
- [ ] `username`, `userId`, and Department "all" cannot widen `self`
- [ ] `team` remains narrowing-only
- [ ] `all` remains the only scope that can legitimately return all users
- [ ] Automated controller/service tests cover the repaired behavior

### Integration
- [ ] Browser/API verification completed with a self-scoped user
- [ ] Browser/API verification completed with team/all regression coverage

### Documentation
- [ ] Requirement doc completed
- [ ] Contract/spec wording verified or updated
- [ ] Cursor tool guidance verified or updated if stale

## 5. Test results

### Test run date
- 2026-03-13 20:05 KST

### Test results

#### Frontend
Partial pass
- Automated unit test passed: `UserActivityLogList.test.js` covers `scope=self` hidden-filter behavior and request sanitization, plus `team` / `all` visible-filter behavior and normal filter submission.
- Frontend build passed, and HTTP reachability check returned `200` from `http://localhost:3001`.
- Browser/manual verification for the live screen remains pending because this QA step did not include an authenticated browser session for the required self-scoped and team/all-scoped users.

#### Backend
Pass
- Targeted backend regression tests passed for `UserActivityLogControllerTest`, `UserActivityLogServiceTest`, and `ScopeHelperTest` (reported: 18 passed).
- Full backend test suite passed (`mvn test`, reported: 82 passed).
- Health verification passed: `curl http://localhost:9200/api/health` returned success on the restarted backend.
- Worktree review confirms the backend fix centralizes `activity-log` scope enforcement in `ScopeHelper.applyActivityLogSearchScope(...)`, ignores client-controlled widening inputs for `scope=self`, and keeps `team` allowlist filtering narrowing-only in service/query execution.

**Commands:**

```bash
cd backend && mvn test -Dtest=UserActivityLogControllerTest,UserActivityLogServiceTest,ScopeHelperTest
cd backend && mvn test
curl -s http://localhost:9200/api/health
cd frontend && CI=true npm test -- --watchAll=false --runTestsByPath src/components/UserActivityLog/UserActivityLogList.test.js
cd frontend && CI=false npm run build
curl -s -o /dev/null -w '%{http_code}' http://localhost:3001
```

**Outcome:**
- Passed by automated evidence: TC-01, TC-02, TC-03, TC-04, TC-05, TC-06, TC-07 (unit portion), TC-08 (unit portion), TC-10
- Pending live verification: TC-07 (manual/browser portion), TC-08 (manual/browser portion), TC-09
- QA status for this requirement is **partially complete**, not fully complete.

### Issues found and resolution
- No new functional failure was found in the supplied automated test/build/restart evidence.
- Remaining gap: live browser/API verification with authenticated users for `scope=self`, `scope=team`, and `scope=all` has not yet been executed in this QA step, so final end-to-end confirmation is still open.

### Next steps
1. Execute authenticated browser/API verification for TC-09 and the browser portions of TC-07 and TC-08 using real users whose effective `activity-log` scopes are `self`, `team`, and `all`.
2. If those remaining checks pass, update §4 checklist and §7 Final version (Korean). Commit only if explicitly requested by the user or by the delegated workflow.

## 6. Error remedy result (cause and action) — for error/bug fix requirements only
- **Requirement ID**: `20260313-activity-log-self-scope-filter-enforcement`
- **Root cause**: `activity-log` scope enforcement had drifted across layers. The controller previously handled only part of the `scope=self` normalization inline, the query layer could still honor widened user-range inputs unless they were fully normalized, and regression coverage did not explicitly protect `username`, combined widening inputs, or department "all" representations. This left room for `scope=self` requests to behave inconsistently with the documented current-user-only contract.
- **Actions taken**: The implementation moved `activity-log` search normalization into shared backend scope helpers, forced current-user-only behavior for `scope=self`, normalized department "all" representations, treated server-managed allowlists as non-client-authoritative, and preserved narrowing-only behavior for `scope=team`. The frontend now clears and omits hidden user-range filters when `screenScopes['activity-log'] === 'self'`, and regression tests were added/extended on both backend and frontend. Contract/spec/Cursor guidance was also updated so the documented behavior matches the repaired enforcement path.
- **Result**: Automated backend regression tests, full backend test suite, frontend targeted unit test, frontend build, backend health check, and frontend reachability check all passed. Documentation review confirms the contract/spec wording now explicitly covers `department`, `username`, and `userId` widening attempts for `activity-log scope=self`. End-to-end authenticated browser verification is still pending, so the bugfix is **partially verified** rather than fully closed.
- **Completed**: 2026-03-13 20:05

---

## 7. Final version (Korean) — add after all verification is complete

### Final Korean summary
- **Requirement description**: To be added after verification
- **Expected outcome**: To be added after verification
- **Verification result**: To be added after verification

---

**Author**: Requirements subagent
**Date**: 2026-03-13
**Status**: In progress
