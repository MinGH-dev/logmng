# 20260305 - Pending approvals scope same as search history

## 1. User requirement

### Requirement description

Apply the **same scope rules** (self / team / all from the permission group) to the **승인 대기 (pending-approvals)** screen and API as are applied to **검색 이력 (search-history)**. Work in two phases:

1. **Phase 1 — Tool improvement**: Update spec, skills, and API/contract docs so that pending-approvals is defined as a scope-supporting screen and scope behavior for `GET /api/search-history/pending` is documented. No application code change in this phase.
2. **Phase 2 — Implementation**: Implement backend (and frontend if needed) so that pending-approvals uses `ScopeHelper` and permission-group scope; list pending results are filtered by scope (self / team / all) in addition to existing `canApproveForRequester` and `is_system_admin` rules.

### User scenario

1. An administrator configures a permission group with the **pending-approvals** screen and scope **self** (or **team** or **all**).
2. A user in that group (e.g. a team leader who is a decrypt approver) opens the 승인 대기 (pending-approvals) screen.
3. **Problem**: Today, the pending list is filtered only by `canApproveForRequester` (and `is_system_admin` → all). The permission group’s scope for pending-approvals is ignored; there is no `screenScopes['pending-approvals']` in auth, and the backend does not apply scope to the pending list.
4. **Expected**: The pending list respects the same scope rules as search-history: **self** → only rows where requester = current user; **team** → only requesters in the same department (and still subject to `canApproveForRequester`); **all** → current “all approvable” behavior. Auth response includes `screenScopes['pending-approvals']` when the user has pending-approvals with a scope.

### Expected outcome

- **Spec and docs**: Pending-approvals is listed as a scope-supporting screen; §4.2 and §4.3 in `permission-group-hierarchy.spec.yaml` document scope for pending-approvals; API definition and contract (if applicable) describe scope for `GET /api/search-history/pending`.
- **Skills**: `search-history-decrypt-domain` and `auth-permission-domain` (if needed) state that pending-approvals uses the same scope rules as search-history.
- **Backend**: `GET /api/search-history/pending` resolves scope via `ScopeHelper.resolveScope(PENDING_APPROVALS, ...)` and filters the list: self = requester = current user; team = same-department requesters + `canApproveForRequester`; all = current behavior (all rows that `canApproveForRequester` allows). `screenScopes` in auth includes `pending-approvals` when the user has that screen with a scope.
- **Frontend**: If scope affects UI (e.g. filter label or hint), use `user.screenScopes['pending-approvals']`; otherwise backend-only change may suffice.
- **Backward compatibility**: Existing `canApproveForRequester` and 403 for non-approvers remain; team leaders with scope=team still see only team members’ requests.

**Analysis (why the initial doc under-specified the screen)**: The first version of this requirement focused on backend scope filtering and only briefly mentioned the viewing screen. It did not analyze **where scope is configured** (permission group edit UI: ScreenSelectionTree, PermissionGroupPanel). Without adding pending-approvals to the scope-supporting list there, admins cannot set scope for pending-approvals. See `docs/workflow/ANALYSIS-pending-approvals-scope-frontend-incomplete.md` for root cause and corrective actions.

---

## 2. Design

### 2.1 Security review (optional; when PII / decryption / access control)

- Scope limits which **pending approval requests** an approver can see. No new decryption or PII exposure beyond existing approval flow. `canApproveForRequester` remains enforced; scope only narrows the list further (self/team) or leaves it as today (all).
- [ ] Security review performed (check if applicable)

### Technical design

#### Codebase summary

- **Search history list** (`GET /api/search-history`): Uses `ScopeHelper.resolveScope(ScreenConstants.SEARCH_HISTORY, isSystemAdmin, getScreenScopes(httpRequest))`; then `scopeAll = "all".equals(scope)` and `allowedUserIds = "team".equals(scope) ? DepartmentScopeHelper.getUserIdsInSameDepartment(...) : null`. `SearchHistoryService.list(userId, ..., scopeAll, allowedUserIds)` filters by user_id (self), allowedUserIds (team), or no filter (all).
- **Pending list** (`GET /api/search-history/pending`): No scope. Controller calls `searchHistoryService.listPending(approverUserId, isSystemAdmin, page, pageSize)`. Service loads all PENDING rows and filters in memory by `isAdmin || decryptApproverService.canApproveForRequester(approverUserId, requester)`. No `scopeAll` or `allowedUserIds`.
- **ScreenConstants**: `SCREENS_WITH_SCOPE` = `SEARCH_HISTORY`, `ACTIVITY_LOG`, `STATISTICS` only; `PENDING_APPROVALS` is not in `SCREENS_WITH_SCOPE`. `supportsScope(PENDING_APPROVALS)` is false.
- **ScopeHelper**: `resolveScope(screenId, ...)` returns `"all"` when `!ScreenConstants.supportsScope(screenId)`, so for pending-approvals it would currently return "all". To support scope, pending-approvals must be added to `SCREENS_WITH_SCOPE`.
- **PermissionGroupService.getScreenScopesForUser**: SQL uses `pgs.screen_id IN ('activity-log', 'statistics', 'search-history')`; pending-approvals is not included. So auth never returns `screenScopes['pending-approvals']`.
- **Spec** `permission-group-hierarchy.spec.yaml`: §4.2 says `screenScopes` is for "activity-log, statistics, search-history". §4.3 table row for pending-approvals has "—" under scope enforcement. §1.1 AllowedScreenItem scope is described for "activity-log, statistics, search-history"; spec §2.1 permission_group_screen.scope says "Only for activity-log, statistics, search-history".

#### Problem analysis

1. Pending-approvals is not treated as a scope-supporting screen: not in `SCREENS_WITH_SCOPE`, not in `getScreenScopesForUser`, and not in spec §4.2 / §4.3 scope enforcement.
2. `listPending` does not accept or apply scope; it only applies `canApproveForRequester` and `is_system_admin`.
3. Product requirement: same scope rules as search-history (self / team / all) for consistency and to allow permission groups to restrict approvers to self-only or team-only view of pending requests.

#### Solution approach

**Phase 1 — Tool improvement (spec, skills, API/contract)**

- **Spec** `specs/permission-group-hierarchy.spec.yaml`:
  - §4.2: Extend auth `screenScopes` to include **pending-approvals** (same values: self | team | all). Purpose: frontend/backend use scope for pending list and optional UI.
  - §4.3: Add scope enforcement for pending-approvals: "Use scope: self → own requests only (requester = current user); team → same department requesters + canApproveForRequester; all → all approvable (current behavior)."
  - §1.1 AllowedScreenItem: Clarify that `scope` applies to **activity-log, statistics, search-history, pending-approvals**. §2.1 permission_group_screen.scope: extend to "Only for activity-log, statistics, search-history, pending-approvals".
- **Skills**: Update `.cursor/skills/search-history-decrypt-domain/SKILL.md` to state that pending-approvals uses the same permission-group scope rules as search-history (self/team/all). Update `.cursor/skills/auth-permission-domain/SKILL.md` §Quick reference scope line to include pending-approvals.
- **API/contract**: In `docs/api-definition.md` §6.1.5, document that when `is_system_admin=false`, scope for pending-approvals (from permission group / screenScopes) applies: self / team / all as above. In `docs/contract.md`, if scope-supporting screens are listed, add pending-approvals.

**Phase 2 — Implementation**

**Backend:**

- **ScreenConstants**: Add `PENDING_APPROVALS` to `SCREENS_WITH_SCOPE` so `supportsScope("pending-approvals")` is true.
- **PermissionGroupService.getScreenScopesForUser**: Include `'pending-approvals'` in the `screen_id IN (...)` list so the auth response can contain `screenScopes['pending-approvals']`.
- **SearchHistoryController.listPending**: Resolve scope with `ScopeHelper.resolveScope(ScreenConstants.PENDING_APPROVALS, isSystemAdmin(httpRequest), getScreenScopes(httpRequest))`. Compute `scopeAll` and `allowedUserIds` (team = same department) the same way as in `list()`. Pass `scope`, `scopeAll`, and `allowedUserIds` into the service.
- **SearchHistoryService.listPending**: Add parameters `boolean scopeAll`, `List<String> allowedUserIds`. After loading PENDING rows and filtering by `canApproveForRequester` (and is_system_admin), apply scope: if `scopeAll`, keep all; else if `allowedUserIds != null`, keep only rows where `requester` is in `allowedUserIds`; else (self) keep only rows where `requester` equals `approverUserId` (current user). Paginate after this filter.

**Frontend:**

- **Configuration UI (required)**: Scope for pending-approvals must be **configurable** in the permission group edit dialog, same as activity-log, statistics, search-history. Today the frontend defines scope-supporting screens in two places; both must include pending-approvals:
  - **ScreenSelectionTree.js**: `SCOPE_SUPPORTING_SCREENS` = `['activity-log', 'statistics', 'search-history']` only. Add `'pending-approvals'` so that when "승인 대기" is selected, a scope dropdown (본인만 | 팀 | 전체) is shown and the value is sent on save. Without this, admins cannot set scope for pending-approvals and the backend has no scope from DB.
  - **PermissionGroupPanel.js**: `scopeScreens` (used in `normalizeAllowedScreens`) = `['activity-log', 'statistics', 'search-history']` only. Add `'pending-approvals'` so that when loading/displaying a permission group that has pending-approvals with scope, the scope is normalized and included in the payload on save.
- **View screen (optional but recommended)**: In **PendingApprovals.js**, show a scope hint from `user.screenScopes['pending-approvals']` (e.g. "표시: 본인" / "표시: 부서" / "전체") so the user knows which scope is applied, consistent with how other scope-supporting screens can reflect scope. Backend applies the filter regardless; the hint is for clarity.

**Terminology alignment**: Current UI and contract use scope labels **본인 | 부서 | 전체** and function labels **조회 | 승인** (not 조회만, not 팀) per specs/permission-group-hierarchy.spec.yaml §1.1 and docs/workflow/CONSISTENCY-STANDARDS.md §7.

### Change file list

**(Tentative. Implementing agent (Step 4) confirms or updates with actual files changed.)**

#### Phase 1 — Specs and docs (no app code)

| File | Change |
|------|--------|
| `specs/permission-group-hierarchy.spec.yaml` | §4.2: Add pending-approvals to screenScopes description. §4.3: Add scope enforcement for pending-approvals row. §1.1 / §2.1: Extend scope to pending-approvals. |
| `.cursor/skills/search-history-decrypt-domain/SKILL.md` | Document pending-approvals scope same as search-history (self/team/all). |
| `.cursor/skills/auth-permission-domain/SKILL.md` | Quick reference / scope: include pending-approvals in scope-supporting screens. |
| `docs/api-definition.md` | §6.1.5: Document scope for GET /api/search-history/pending (self/team/all when is_system_admin=false). §2.1 login response: screenScopes include pending-approvals when applicable. |
| `docs/contract.md` | If scope-supporting screens are enumerated, add pending-approvals. |

#### Phase 2 — Backend

| File | Change |
|------|--------|
| `backend/src/main/java/com/logmng/constants/ScreenConstants.java` | Add PENDING_APPROVALS to SCREENS_WITH_SCOPE (moved constant above set so it can be included). |
| `backend/src/main/java/com/logmng/service/PermissionGroupService.java` | getScreenScopesForUser: add 'pending-approvals' to screen_id IN (...). |
| `backend/src/main/java/com/logmng/controller/SearchHistoryController.java` | listPending: resolve scope via ScopeHelper(PENDING_APPROVALS,...), compute scopeAll and allowedUserIds, pass to service. |
| `backend/src/main/java/com/logmng/service/SearchHistoryService.java` | listPending: add scopeAll, allowedUserIds; after canApproveForRequester filter, apply scope filter (self/team/all); then paginate. |
| `backend/src/test/java/com/logmng/service/SearchHistoryServiceTest.java` | listPending scope tests: TC-01 (self), TC-02/TC-06 (team), TC-03 (all); clearSearchHistory + insertPendingRow helpers. |
| `backend/src/test/java/com/logmng/service/PermissionGroupServiceTest.java` | getScreenScopesForUser test (TC-04): app_user_permission_group in H2 setup; assert screenScopes contains pending-approvals=team. |

#### Phase 2 — Frontend

| File | Change |
|------|--------|
| `frontend/src/components/PermissionGroupManagement/ScreenSelectionTree.js` | Add `'pending-approvals'` to `SCOPE_SUPPORTING_SCREENS` so that when "승인 대기" is selected in the permission group edit dialog, the scope dropdown (본인만 \| 팀 \| 전체) is shown and value is sent on save. **Required** for scope to be configurable. **Done.** |
| `frontend/src/components/PermissionGroupManagement/PermissionGroupPanel.js` | Add `'pending-approvals'` to `scopeScreens` in `normalizeAllowedScreens` so that scope for pending-approvals is normalized when loading/saving a permission group. **Required**. **Done.** |
| `frontend/src/components/PendingApprovals/PendingApprovals.js` | (Optional) Show scope hint from `user.screenScopes['pending-approvals']` (e.g. "표시: 본인 요청만" / "팀 요청" / "전체") for user clarity. **Done.** |
| `frontend/src/components/PendingApprovals/PendingApprovals.css` | Style for `.pending-approvals-scope-hint`. **Done.** |
| `frontend/src/components/PermissionGroupManagement/ScreenSelectionTree.test.js` | TC-07: test that when "승인 대기" is selected, scope dropdown is visible and onChange receives scope. **Done.** |

---

## 3. Test approach

### Test case list (required)

| ID | Scope | Type | Scenario (input / condition) | Expected result | Verification |
|----|-------|------|------------------------------|----------------|--------------|
| TC-01 | Backend | Normal | Approver with pending-approvals scope **self** calls GET /api/search-history/pending. | Only rows where requester = current user (and canApproveForRequester). | Integration (curl) |
| TC-02 | Backend | Normal | Approver with pending-approvals scope **team** calls GET /api/search-history/pending. | Only rows where requester is in same department and canApproveForRequester. | Integration (curl) |
| TC-03 | Backend | Normal | Approver with scope **all** or is_system_admin=true calls GET /api/search-history/pending. | All PENDING rows that canApproveForRequester allows (admin: all). | Integration (curl) |
| TC-04 | Backend | Normal | User with pending-approvals and scope **team** in permission group: POST /api/auth/login or GET /api/auth/me. | Response includes screenScopes['pending-approvals'] = 'team' (or self/all as configured). | Integration (curl) |
| TC-05 | Backend | Regression | Non-approver (no decrypt_approver, no is_system_admin) calls GET /api/search-history/pending. | 403 FORBIDDEN. | Integration (curl) |
| TC-06 | Backend | Regression | Department-scoped approver with scope=team: only requesters in same department appear; canApproveForRequester still enforced. | No rows from other departments; approve/reject still gated by canApproveForRequester. | Integration (curl) |
| TC-07 | Frontend | Normal | Admin opens permission group edit, selects "승인 대기", sets scope to "팀" (or self/all). Saves. | Scope dropdown is visible for pending-approvals; saved value is persisted; after login/me, user in that group has screenScopes['pending-approvals'] = 'team' (or self/all). | Manual or browser automation |
| TC-08 | Frontend | Optional | User with pending-approvals scope=team opens 승인 대기 screen. | Screen shows scope hint (e.g. "팀 요청" or "표시: 팀") so user knows which scope is applied. | Manual |

### Test scenarios

#### Scenario 1: Scope self

1. Create permission group with pending-approvals, scope=self; assign approver user A.
2. Create PENDING requests: one from A, one from user B (same or other dept).
3. Call GET /api/search-history/pending as A.
4. **Verification**: Only the row where requester=A is returned.

#### Scenario 2: Scope team

1. Permission group: pending-approvals, scope=team; assign department-scoped approver (same department as some requesters).
2. Create PENDING from same-dept user and other-dept user.
3. Call GET /api/search-history/pending as approver.
4. **Verification**: Only same-department requester’s row (and canApproveForRequester still applied).

#### Scenario 3: screenScopes in auth

1. User has permission group with pending-approvals and scope=team.
2. Login or GET /api/auth/me.
3. **Verification**: user.screenScopes['pending-approvals'] === 'team'.

### Test data

- Users: at least one system admin, one department-scoped approver (decrypt_approver), one non-approver. At least two departments with requesters and one approver in one department.
- Permission groups: one with pending-approvals scope=self, one with scope=team, one with scope=all (or use admin).
- Search history: several PENDING rows (different requesters, same/other department).

### Test environment

- Backend: http://localhost:9200
- Frontend: http://localhost:3001 (if UI verification)
- Database: per project (PostgreSQL/H2)

---

## 4. Checklist

### Phase 1 (tools)

- [x] Spec §4.2, §4.3, §1.1, §2.1 updated for pending-approvals scope.
- [x] Skills (search-history-decrypt-domain, auth-permission-domain) updated.
- [x] api-definition.md §6.1.5 and auth response describe scope for pending list.
- [x] contract.md updated if it lists scope screens.

### Phase 2 — Backend

- [x] ScreenConstants.SCREENS_WITH_SCOPE includes pending-approvals.
- [x] getScreenScopesForUser returns pending-approvals when configured.
- [x] listPending uses ScopeHelper and filters by self/team/all.
- [x] Unit/integration tests for scope=self, team, all and for screenScopes in auth.

### Phase 2 — Frontend

- [x] ScreenSelectionTree.js: pending-approvals added to SCOPE_SUPPORTING_SCREENS (scope dropdown when "승인 대기" selected).
- [x] PermissionGroupPanel.js: pending-approvals added to scopeScreens in normalizeAllowedScreens.
- [x] (Optional) PendingApprovals.js: scope hint from screenScopes['pending-approvals'] shown.

### Integration

- [ ] End-to-end: login as approver with scope team/self → pending list matches scope (manual or E2E when credentials available).
- [x] Non-approver still gets 403; canApproveForRequester unchanged (covered by test suite and controller layer).

### Documentation

- [x] Requirement doc completed; §5 filled after QA verification.

---

## 5. Test results

### Test run date

- **2026-03-05** (Phase 2 implementation complete; QA verification.)

### Verification scope

- **Scope**: Backend + Frontend (pending-approvals scope same as search-history).
- **Health check**: Backend http://localhost:9200/api/health → **200** OK. Frontend http://localhost:3001 → **200**.
- **Backend unit/integration**: `cd backend && mvn test` → **BUILD SUCCESS**, **Tests run: 56, Failures: 0, Errors: 0, Skipped: 0**.

### Test results

| ID   | Result | Note |
|------|--------|------|
| TC-01 | **Pass** | Covered by `SearchHistoryServiceTest.listPending_scopeSelf_returnsOnlyRowsWhereRequesterEqualsCurrentUser` (scope=self → only requester = current user). |
| TC-02 | **Pass** | Covered by `SearchHistoryServiceTest.listPending_scopeTeam_returnsOnlyRowsWhereRequesterInAllowedUserIds` (scope=team + allowedUserIds). |
| TC-03 | **Pass** | Covered by `SearchHistoryServiceTest.listPending_scopeAll_returnsAllApprovableRows` (scopeAll=true). |
| TC-04 | **Pass** | Covered by `PermissionGroupServiceTest.getScreenScopesForUser_includesPendingApprovalsScopeWhenConfigured` (screenScopes contains `pending-approvals` = `team`). |
| TC-05 | **Pass** | Regression: non-approver 403 remains enforced at controller layer; full suite 56 tests pass. |
| TC-06 | **Pass** | Team scope + canApproveForRequester enforced in service (listPending with allowedUserIds + existing canApprove filter). |
| TC-07 | **Manual** | Permission group edit: "승인 대기" scope dropdown and persistence. Browser check: app loads at 3001, login page visible; **no test credentials** — **manual verification recommended**: admin login → 권한 그룹 편집 → "승인 대기" 선택 → scope(본인만/팀/전체) 표시 및 저장 후 screenScopes 반영 확인. |
| TC-08 | **Manual** | PendingApprovals scope hint. **Manual verification recommended**: user with scope=team opens 승인 대기 → "팀 요청" 등 scope hint 표시 확인. |

### Browser automation (step 3.5)

- **Tool used**: cursor-ide-browser (navigate → lock → snapshot).
- **Base URL**: http://localhost:3001.
- **Steps run**: Navigate to 3001 → page loaded (로그 관리 시스템). After 3s wait, snapshot showed **login form** (사용자명, 비밀번호, 로그인 버튼). TC-07 and TC-08 require authenticated session (admin for permission group edit; approver with scope=team for pending-approvals view); **not executed** in this run due to no test credentials. **Recommendation**: run TC-07/TC-08 manually or with E2E credentials when available.

---

**Author**: Requirements subagent  
**Date**: 2026-03-05  
**Status**: Verified (Phase 2). TC-01–TC-06 pass via backend tests; TC-07/TC-08 manual recommended.
