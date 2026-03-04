# 20250304 - Permission scope team and approval pending

**Type**: Feature  
**Scope**: Approval pending filtering (document/verify), permission group scope 'team' (DB, Backend, Frontend), default scope 'team', Cursor infrastructure.  
**Related**: `docs/requirements/20250303-activity-statistics-self-only-scope.md`, `docs/requirements/20250303-screen-function-availability.md`, `.cursor/skills/auth-permission-domain/SKILL.md`, `.cursor/skills/search-history-decrypt-domain/SKILL.md`, `.cursor/skills/department-approver-domain/SKILL.md`

---

## 1. User requirement

### Requirement description

1. **Approval pending (승인 대기)**: When a team leader (department-scoped approver) opens the approval pending screen, they must see **only** approval requests from requesters in their scope (their department). System administrators must see all pending requests. This behavior is already implemented via `canApproveForRequester(approverUserId, requester)` in `SearchHistoryService.listPending`; this requirement documents it and adds verification test cases.

2. **Permission group scope**: Currently the permission group screen configuration supports scope **self** (본인) and **all** (전체) for activity-log, statistics, and search-history. The user requests:
   - Add **team** (팀) as a scope option: data visible is limited to the same department as the current user.
   - Make **team** the **default** scope for scope-supported screens when creating or configuring permission group screen entries (so new selections default to 팀(team) instead of 본인(self)).

### User scenario

1. An administrator or permission manager opens the permission group management screen and configures allowed screens for a permission group. For activity-log, statistics, or search-history, they choose a scope: **본인(self)**, **팀(team)**, or **전체(all)**. When they add a new scope-supported screen, the default selected scope is **팀(team)**.
2. A user whose permission group has scope **team** for search-history (or activity-log, statistics) logs in. They see only data belonging to users in the **same department** as themselves (e.g. only their team members’ activity, statistics, or search history).
3. A **team leader** (user registered as decrypt_approver for their department) opens the approval pending screen. They see **only** pending decryption approval requests from requesters in their department (or for whom they are allowed to approve per `canApproveForRequester`). They do not see pending requests from other departments.
4. A **system administrator** opens the approval pending screen. They see **all** pending requests regardless of department.
5. **Problem**: Without the team scope and default, organizations that want “team only” visibility must use “self” (too narrow) or “all” (too broad). Approval pending behavior for team leaders, although implemented, is not explicitly documented or verified by test cases.

### Expected outcome

- **Approval pending**: Team leaders (department-scoped approvers) see only team members’ pending approval requests; system admins see all. This is documented and verified by test cases.
- **Scope team**: Users with scope **team** for activity-log, statistics, or search-history see only data from the same department (same `department_code` as the current user). Scope options in the UI are 본인(self), 팀(team), 전체(all).
- **Default scope**: When a new scope-supported screen is added to a permission group, the default scope is **team** (팀). Existing rows with NULL scope may be treated as default 'team' for scope-supported screens (per implementation choice: migration or runtime default).
- **Cursor infrastructure**: Auth-permission-domain skill and contract/specs mention scope values **self | team | all** and default **team**.

---

## 2. Design

**Note**: The **change file list** in §2 is **tentative**. The implementing agent (Step 4) **must confirm or update** it with actual files changed when implementation is complete. See `docs/workflow/AGENT-COLLABORATION-ON-REQUIREMENT.md` §1.2.

### 2.1 Security review (optional; when PII / decryption / access control)

Approval pending and scope **team** involve **access control** (who sees which data). When the **Security** subagent has reviewed, summarize risks, acceptance criteria, and design recommendations here. Reference: `docs/workflow/CURSOR-SUBAGENTS-DESIGN.md`, `docs/workflow/WORKFLOW_CHECKLIST.md`.

- [ ] Security review performed (check if applicable)
- **Risks**: (placeholder) Scope 'team' exposes data of all users in the same department; ensure department_code is correctly assigned and not spoofable. Approval list must remain filtered by canApproveForRequester so approvers cannot see or act on out-of-scope requests.
- **Acceptance / recommendations**: (placeholder) List pending and approve/reject APIs must enforce canApproveForRequester server-side. Activity-log, statistics, search-history list APIs must filter by effective scope (self / team / all) server-side; default team must not widen visibility beyond same department.

### Technical design

#### Problem analysis

1. **Approval pending**: Backend already filters pending list by `canApproveForRequester(approverUserId, requester)` in `SearchHistoryService.listPending` (and approve/reject use the same check). Department-scoped approvers (팀장) therefore already see only requesters in their department (or hierarchy). The gap is **documentation** and **verification**: no explicit requirement or test case confirms that a team leader sees only team members’ requests and an admin sees all.

2. **Scope values**: Today `permission_group_screen.scope` allows only `'self'` and `'all'` (DB CHECK constraint). Default for scope-supported screens is effectively `'self'` (NULL or missing → self). There is no **team** option, so organizations cannot choose “same department only” without granting “all”.

3. **Scope resolution**: `ScopeHelper.resolveScope` and `PermissionGroupService.getScreenScopesForUser` only handle self/all. Activity-log, statistics, and search-history list APIs use scope only to choose “current user only” vs “all”; they do not filter by department.

4. **Frontend**: `ScreenSelectionTree` shows scope options “본인만” (self) and “전체” (all), and defaults new scope-supported screens to `'self'`. It must show **팀(team)** and default to **team**.

5. **Cursor infrastructure**: `.cursor/skills/auth-permission-domain/SKILL.md` and `docs/contract.md` (and specs if any) describe scope as self | all and default self; they must be updated to self | team | all and default team.

#### Solution approach

**Approval pending (document and verify)**

- **Backend**: No code change required. Keep using `SearchHistoryService.listPending(approverUserId, isSystemAdmin, ...)` which already filters by `isSystemAdmin` (all) or `decryptApproverService.canApproveForRequester(approverUserId, requester)` (department-scoped). Document this in the requirement and add §3 test cases: (1) team leader sees only team members’ pending requests, (2) admin sees all.

**DB**

- **permission_group_screen.scope**: Allow value `'team'` in addition to `'self'` and `'all'`.
  - Update CHECK constraint: `scope IS NULL OR scope IN ('self', 'all', 'team')`.
  - Default for scope-supported screens: set default to `'team'` when inserting new rows for activity-log, statistics, search-history (or define that NULL means `'team'` at runtime for those screens; implementation choice). Migration script to add `'team'` to CHECK and optionally backfill NULL scope for scope-supported screens to `'team'`.

**Backend**

- **ScopeHelper.resolveScope**: When `screenScopes` contains `'team'` for the screen, return `'team'`. Order: isSystemAdmin → all; else from screenScopes: all → team → self; default (missing/null) → `'team'` for scope-supported screens (per product decision).
- **AuthService / PermissionGroupService.getScreenScopesForUser**: Pass through `'team'` from DB (do not normalize to self). Effective values: `'self'`, `'team'`, `'all'`.
- **Activity-log, statistics, search-history list APIs**: When effective scope is `'team'`, filter data by same department: current user’s `app_user.department_code`; for list endpoints, restrict to users/rows where the data owner’s (or target’s) department_code equals the current user’s department_code. Use `DepartmentService` if hierarchy is needed (e.g. same department only vs include descendants: per product decision; minimum is same department_code).
- **SearchHistoryService**: `list`, `reRequest`, `getDetail` today take `scopeAll` (boolean). Extend to support scope `'team'`: e.g. when scope is team, filter by requester’s department_code = current user’s department_code (for list: only rows where user_id is in same department; reRequest/getDetail: allow if requester is in same department).
- **PermissionGroupService** (create/update allowed screens): Accept `scope` value `'team'` in validation (allow self | team | all); persist and return in GET. Default new scope-supported screen entry to `'team'` when scope not provided.

**Frontend**

- **ScreenSelectionTree** (or equivalent): Add scope option **팀(team)**. Options: 본인(self), 팀(team), 전체(all). When adding a new scope-supported screen, default scope to **team** (not self). Display and persist `scope: 'team'` in allowedScreens.

**Cursor infrastructure**

- **.cursor/skills/auth-permission-domain/SKILL.md**: Update scope description to **self | team | all**; default **team** for scope-supported screens.
- **docs/contract.md** (and specs if they mention scope): Update scope values and default to include **team** and default **team**.

### Change file list

**(Tentative. Implementing agent (Step 4) confirms or updates with actual files changed.)**

#### Frontend

- `frontend/src/components/PermissionGroupManagement/ScreenSelectionTree.js`
  - Add scope option 팀(team); options: self, team, all. Default new scope-supported screen to `'team'`. Normalize and display scope value `'team'`.
- `frontend/src/constants/screenFunctionDescriptions.js` (or where SCOPE_OPTIONS / labels live)
  - Add team option and label if constants are shared.

#### Backend

- `backend/src/main/java/com/logmng/util/ScopeHelper.java`
  - Handle `'team'` from screenScopes; return `'team'` when present; default (null/missing) for scope-supported screens → `'team'`.
- `backend/src/main/java/com/logmng/service/AuthService.java`
  - No change if getScreenScopesForUser is in PermissionGroupService; ensure screenScopes passed to frontend include `'team'`.
- `backend/src/main/java/com/logmng/service/PermissionGroupService.java`
  - getScreenScopesForUser: pass through `'team'` (effective = all | team | self). Validate scope in create/update: allow `'self'`, `'team'`, `'all'`. Default new scope-supported screen to `'team'` when inserting.
- `backend/src/main/java/com/logmng/controller/UserActivityLogController.java`
  - When scope is `'team'`, filter activity log list by same department (current user’s department_code; filter by target user_id in same department).
- `backend/src/main/java/com/logmng/controller/ActivityStatisticsController.java`
  - When scope is `'team'`, filter statistics by same department (user list or data filter by department_code).
- `backend/src/main/java/com/logmng/controller/SearchHistoryController.java`
  - Resolve scope to self | team | all; when `'team'`, call SearchHistoryService with team-scoped filter (e.g. scopeTeam=true or scope param).
- `backend/src/main/java/com/logmng/service/SearchHistoryService.java`
  - Extend list/reRequest/getDetail to support team scope: filter by requester department_code = current user’s department_code.
- `backend/src/main/java/com/logmng/service/UserActivityLogService.java`
  - Support team-scoped list (filter by same department).
- `backend/src/main/java/com/logmng/service/ActivityStatisticsService.java`
  - Support team-scoped aggregation (filter by same department).
- `backend/src/main/java/com/logmng/constants/ScreenConstants.java`
  - Comment or constant for scope-supported screens unchanged; scope values may be documented as self | team | all.
- `backend/src/main/java/com/logmng/dto/response/LoginResponse.java`
  - Comment: per-screen scope value 'self' | 'team' | 'all'.

#### Database

- `backend/src/main/resources/db/schema.sql`
  - permission_group_screen: CHECK (scope IS NULL OR scope IN ('self', 'all', 'team')).
- `backend/src/main/resources/db/migrate-*.sql` (new or existing)
  - Migration: alter CHECK to include 'team'; optionally set default scope to 'team' for existing NULL scope on scope-supported screens.

#### Cursor / docs

- `.cursor/skills/auth-permission-domain/SKILL.md`
  - Scope: self | team | all; default team for scope-supported screens.
- `docs/contract.md`
  - Scope values and default: include team, default team (where applicable).

---

## 3. Test approach

### Test case list (required)

Domain-specific completeness: **search-history-decrypt-domain** §3 checklist applied for approval-related test cases (approver vs admin, approval flow, §5 curl commands). See below.

| ID | Type | Scenario (input / condition) | Expected result | Verification (unit / integration / manual) |
|----|------|------------------------------|-----------------|--------------------------------------------|
| TC-01 | Normal | **Approval pending – team leader**: User A is decrypt_approver for dept D1; requesters B, C in D1; requester E in D2. A calls GET pending-approvals (or list pending API). | Response contains only B and C’s pending requests; no requests from E. | Integration (login as A, call list pending API; assert only D1 requesters’ items). |
| TC-02 | Normal | **Approval pending – admin**: System admin calls GET pending-approvals. | Response contains all pending requests (all departments). | Integration (login as admin, call list pending API; assert count ≥ 0, no department filter). |
| TC-03 | Normal | **Scope team – search-history list**: User U has scope **team** for search-history; U’s department_code = D1. Another user V in D1 has search history; user W in D2 has search history. U calls search-history list. | U sees only history for U and V (same department); not W’s. | Integration (setup U,V,W; login as U; call list; assert only same-department rows). |
| TC-04 | Normal | **Scope team – activity-log**: User U has scope **team** for activity-log; U in D1. Same-department users’ activity exists. U calls activity-log list. | U sees only activity for same department (D1). | Integration (login as U; call activity-log API; assert filtered by department). |
| TC-05 | Normal | **Scope team – statistics**: User U has scope **team** for statistics; U in D1. U calls statistics API. | Response contains only statistics for same department (D1). | Integration (login as U; call statistics API; assert department filter). |
| TC-06 | Normal | **Default scope team**: Create or update permission group: add screen **search-history** (or activity-log) without supplying scope. | Stored scope is **team** (or UI shows 팀 as selected for new scope-supported screen). | Integration or UI test (create/update group with new scope screen; GET group or UI snapshot; assert scope=team). |
| TC-07 | Normal | **Scope options in UI**: Open permission group screen selection; select activity-log (or statistics/search-history). | Scope dropdown shows 본인(self), 팀(team), 전체(all); default for new selection is 팀. | Manual or browser automation. |
| TC-08 | Exception | **Approval – non-approver**: User without decrypt_approver and not is_system_admin calls list pending. | 403 or empty list per contract (no privilege to see any pending). | Integration. |
| TC-09 | Edge | **Approver vs admin**: Decrypt_approver (department-scoped) cannot approve a requester in another department; admin can approve any. | Approve API: 403 for approver when requester not in scope; 200 for admin. | Integration (search-history-decrypt-domain: approver vs admin TC). |
| TC-10 | Edge | **Scope self unchanged**: User with scope **self** for search-history sees only own data. | No regression; list returns only current user’s rows. | Integration. |

### Test scenarios

#### Scenario 1: Approval pending – team leader sees only team members

1. Set up: Department D1 with users A (decrypt_approver for D1), B, C; Department D2 with user E. Create pending search-history requests for B, C, E.
2. Log in as A. Call GET list pending (e.g. GET /api/search-history/pending or equivalent).
3. **Verification**: Response includes only B and C’s pending items; E’s request is not in the list.

#### Scenario 2: Approval pending – admin sees all

1. Log in as system admin. Call GET list pending.
2. **Verification**: Response includes all pending requests (B, C, E from Scenario 1).

#### Scenario 3: Scope team – same-department data only

1. Set up: User U with permission group that has scope **team** for search-history (and activity-log, statistics). U.department_code = D1. Users V (D1), W (D2) have data.
2. Log in as U. Call search-history list, activity-log list, statistics.
3. **Verification**: Only data for U and V (same department D1); no data for W.

#### Scenario 4: Default scope team for new screen

1. Create or edit a permission group; add screen **activity-log** (or search-history) without specifying scope.
2. **Verification**: Stored scope is **team**; UI shows 팀(team) as selected.

### Test data

- **Departments**: D1, D2 (e.g. in department table).
- **Users**: A (approver for D1), B, C (D1), E (D2), U (D1, permission group with scope team for scope-supported screens), V (D1), W (D2).
- **decrypt_approver**: A with department_code = D1 (or NULL for global; for TC-01 use D1).
- **Permission group**: One group with scope **team** for activity-log, statistics, search-history (for TC-03–TC-05, TC-10); one with scope **self** (for TC-10).
- **Search history**: PENDING records for B, C, E (for TC-01, TC-02). Search history rows for U, V, W (for TC-03).
- When derivation rules or defaults apply, provide **executable SQL** (INSERT/UPDATE) so QA can set up test data.

### Test environment

- Frontend: `http://localhost:3001` (or per contract)
- Backend: `http://localhost:9200`
- Database: PostgreSQL (per project)

### 3.5 Browser automation verification (optional)

- **Applicable TCs**: TC-06, TC-07 (permission group UI: default scope team, scope options 본인/팀/전체).
- **Procedure**: Log in as admin → open permission group management → create or edit group → add activity-log or search-history → confirm scope dropdown shows 본인, 팀, 전체 and default is 팀; confirm saved scope is team.
- **Reference**: `docs/workflow/BROWSER-AUTOMATION-VERIFICATION-POLICY.md`.

### §3 Completeness (search-history-decrypt-domain)

- [x] **Approver vs admin TC**: TC-01, TC-02, TC-09 — team leader sees only team; admin sees all; approver cannot approve out-of-scope.
- [x] **Approval flow TC**: Existing approval flow (PENDING → approve → APPROVED) unchanged; list pending filtered by canApproveForRequester.
- [x] **§5 curl commands**: §5 must provide one executable command per TC (login as A, admin, U, etc. + list pending / list search-history / activity-log / statistics) so QA can run each TC.

---

## 4. Checklist

### Frontend verification

- [ ] Scope options 본인/팀/전체 displayed; default for new scope-supported screen is 팀.
- [ ] API parameters (allowedScreens with scope=team) validated.
- [ ] Error handling verified.

### Backend verification

- [ ] API test cases written and run (list pending, list search-history/activity-log/statistics with scope team).
- [ ] Logs checked.
- [ ] Performance checked (if applicable).

### Integration

- [ ] End-to-end: team leader sees only team pending; admin sees all; scope team filters by department.
- [ ] Edge cases: scope self unchanged; approver cannot approve other department.

### Documentation

- [ ] Requirement doc completed.
- [ ] auth-permission-domain SKILL and contract updated (scope self | team | all; default team).

---

## 5. Test results

### Test run date

- [Date and time]

### Test results

#### Frontend

[Pass / Fail]
- [Result description]

#### Backend

[Pass / Fail]
- [Result description]

**Commands:**

Provide **one executable command per TC** in §3 (login + request). Do not use "example pattern" for a subset; cover every TC so QA can copy-paste and run.

```bash
# TC-01: Login as team leader A, list pending
# TC-02: Login as admin, list pending
# TC-03–TC-05: Login as U, list search-history / activity-log / statistics
# TC-06–TC-07: UI or API for permission group scope
# TC-08–TC-10: As specified
```

**Outcome:**

- [Item 1]
- [Item 2]

### Issues found and resolution

#### Issue 1: [Name]

**Cause**: [Cause description]

**Resolution**:

1. [Resolution 1]
2. [Resolution 2]

### Next steps

1. [Next step 1]
2. [Next step 2]

---

**Author**: Requirements subagent  
**Date**: 2026-03-04  
**Status**: In progress
