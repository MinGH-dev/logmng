# 20250304 - Permission group function verification (modify / approve)

**Type**: Verification / audit  
**Scope**: No code changes. Define scope of permission checks and test approach to verify (1) permission holders can use allowed functions (modify, approve), (2) non-holders cannot exercise them (no privilege escalation).  
**Related**: `docs/requirements/20250303-screen-function-availability.md`, `docs/requirements/20250303-user-management-permission-group-access.md`, `specs/permission-group-hierarchy.spec.yaml` §4.3–§4.4, `.cursor/skills/auth-permission-domain/SKILL.md`

---

## 1. User requirement

### Requirement description

When a permission group allows functions such as **modify (write)** or **approve**, the system must ensure:

1. **Permission holders** can use those allowed functions normally (API succeeds, UI elements enabled).
2. **Non-holders** cannot exercise those functions: API returns 403 with `FUNCTION_NOT_ALLOWED` (or equivalent), and UI either hides or disables the corresponding actions with an appropriate message (e.g. "승인 권한이 없습니다", "수정 권한이 없습니다").

This requirement is a **verification/audit**: define the scope of screens and APIs that enforce function-level access (write, approve), and define concrete test cases so QA can confirm both positive behavior (holder can use) and negative behavior (non-holder cannot — no privilege escalation).

### User scenario

1. An administrator configures permission groups with per-screen **read**, **write**, and **approve** (where applicable). For example: a group grants "검색 이력" with approve, or "사용자 관리" with write.
2. A user who is a **permission holder** (has the group with write or approve for the screen) logs in and opens the relevant screen. They can perform the allowed actions: e.g. approve/reject on pending-approvals, or create/edit users on user-management.
3. A user who **lacks** that function (e.g. has the screen with read only, or no decrypt_approver for approve) must not be able to perform the action: calling the API directly returns 403; on the UI, the action is disabled or hidden with a clear reason.
4. **Problem**: Without a structured verification, it is unclear whether every screen/API that should enforce write or approve actually does so, and whether the UI consistently disables or hides actions for non-holders.

### Expected outcome

- A **defined scope** of screens and APIs that use permission-group-based **write** or **approve** checks (documented in §2).
- **Test plan (§3)** with concrete test cases:
  - **(a) Holder**: User with the allowed function can use it (API 200, UI enabled).
  - **(b) Non-holder**: User without the function cannot use it (API 403 `FUNCTION_NOT_ALLOWED` or equivalent; UI button/action disabled or hidden with tooltip/message).
- **Acceptance criteria**: All listed APIs enforce function-level checks; all listed screens drive UI enable/disable from `screenFunctions` (or equivalent). No privilege escalation: non-holders never succeed at write or approve via API or UI.

---

## 2. Design

### 2.1 Security review (optional; when PII / decryption / access control)

**Access control is in scope** (write and approve gates). After this requirement doc is complete, the **Security** subagent may review §2.1 to confirm risks and acceptance criteria for function-level enforcement and privilege-escalation prevention. Reference: `docs/workflow/CURSOR-SUBAGENTS-DESIGN.md`, `docs/workflow/WORKFLOW_CHECKLIST.md`.

- [ ] Security review performed (to be scheduled after doc complete)
- **Risks (draft)**: Privilege escalation if write/approve APIs do not validate function permission; UI-only checks are bypassable.
- **Acceptance (draft)**: All write and approve APIs must validate server-side; 403 `FUNCTION_NOT_ALLOWED` when lacking; UI reflects `screenFunctions` for UX only.

### Technical design

#### Scope of permission checks (what to verify)

Function-level enforcement is already specified in:

- **Contract**: `docs/contract.md` — screenFunctions, API function-level enforcement (403 `FUNCTION_NOT_ALLOWED` for approve/write).
- **Spec**: `specs/permission-group-hierarchy.spec.yaml` §4.3 (screen–API mapping), §4.4 (screenFunctions, API enforcement for approve and write).
- **Requirement**: `docs/requirements/20250303-screen-function-availability.md` — per-screen read/write/approve, API enforcement, UI disable + tooltip.

This document defines the **verification scope** (screens and APIs to test) and the **test approach** (§3).

#### Screens and functions in scope

| screen_id | write | approve | Notes |
|-----------|-------|---------|--------|
| main | — | — | Read-only; no write/approve to verify. |
| search-history | — | ✓ | approve: decrypt_approver or is_system_admin; group may allow approve for screen. |
| activity-log | — | — | Read + scope only. |
| statistics | — | — | Read + scope only. |
| pending-approvals | — | ✓ | approve: same as search-history. |
| user-management | ✓ | — | write: create/edit users, assign groups, approver status. |
| department-approvers | ⚠ (no write endpoints) | — | DepartmentController currently has only GET endpoints (list, hierarchy); no POST/PUT/DELETE CRUD for approvers exists. `requireWriteForManagement()` is not applied. Write enforcement deferred until CRUD endpoints are implemented. |
| user-permission-hierarchy | ✓ | — | write: hierarchy view + management actions. |
| permission-group-management | ✓ | — | write: permission group CRUD, user-group assign. |

#### APIs to verify (function-level)

**Approve (must return 403 FUNCTION_NOT_ALLOWED when user lacks approve)**

Checked by `requireApproverOrAdmin()` in `SearchHistoryController`: validates (decrypt_approver OR is_system_admin) AND screenFunctions.approve.

- `GET /api/search-history/pending` — pending list (also gated by approve)
- `POST /api/search-history/{id}/approve`
- `POST /api/search-history/{id}/reject`

**Write (must return 403 FUNCTION_NOT_ALLOWED when user lacks write for the corresponding screen)**

Checked by `requireWriteForManagement()` in controllers: validates `screenFunctions[user-management or user-permission-hierarchy].write === true`.

- **Permission groups** (PermissionGroupController — `requireWriteForManagement()` applied):
  - `POST /api/permission-groups` (create)
  - `PUT /api/permission-groups/{id}` (update)
  - `DELETE /api/permission-groups/{id}` (delete)
  - `POST /api/permission-groups/{id}/users` (assign user)
  - `DELETE /api/permission-groups/{id}/users/{userId}` (unassign user)

**Read-only APIs (screen access check only — `requireUserManagementAccess()` / `requireDepartmentAccess()`, no write function check)**

These APIs check screen access (FORBIDDEN) but NOT write function (FUNCTION_NOT_ALLOWED). They are NOT write-gated:

- `GET /api/users` — user list (UserController; screen access only)
- `PUT /api/users/{userId}` — **410 Gone** (endpoint removed; no write check)
- `GET /api/permission-groups` — list (screen access only)
- `GET /api/permission-groups/{id}` — detail (screen access only)
- `GET /api/permission-groups/{id}/users` — group members (screen access only)
- `GET /api/departments` — department tree (screen access only)
- `GET /api/departments/user-permission-hierarchy` — hierarchy (screen access only)

**Gaps identified (no write enforcement)**

- **UserController**: No write function check (`requireWriteForManagement`) on any endpoint. `PUT /api/users/{userId}` returns 410 Gone. Actual user-management write operations (assign/unassign groups) are in PermissionGroupController.
- **DepartmentController**: No POST/PUT/DELETE endpoints exist. No write function check. Department approver CRUD is not yet implemented.

**Error code distinction**

| Denial reason | HTTP | Code | Example |
|---------------|------|------|---------|
| Screen access denied (not in allowedScreenIds) | 403 | `FORBIDDEN` | User without user-management calls GET /api/users |
| Function denied (screen accessible but write/approve=false) | 403 | `FUNCTION_NOT_ALLOWED` | User with user-management but write=false calls POST /api/permission-groups |

#### UI behavior to verify

- **Pending-approvals / search-history**: When `screenFunctions[screen].approve === false`, approve/reject buttons disabled with tooltip (e.g. "승인 권한이 없습니다").
- **User-management, department-approvers, user-permission-hierarchy, permission-group-management**: When `screenFunctions[screen].write === false`, create/edit/delete/assign actions disabled with tooltip (e.g. "수정 권한이 없습니다").
- **main**: No approve/write actions shown (read-only).

#### Problem analysis (verification focus)

1. **Coverage**: Ensure every API that performs a write or approve action is listed and tested for both holder (success) and non-holder (403).
2. **UI consistency**: Ensure every screen that exposes write or approve actions uses `screenFunctions` (or equivalent) to enable/disable; non-holders see disabled or hidden controls with clear messaging.
3. **No privilege escalation**: Direct API calls (e.g. curl or automation) by a non-holder must result in 403, not 200.

#### Solution approach (verification only)

- **No implementation**: This is an audit/verification requirement. No code changes.
- **Deliverables**: (1) Scope table (screens/APIs) as above; (2) §3 test cases for holder vs non-holder; (3) Optional: checklist of APIs/screens to tick off during QA.

### Change file list

**N/A – verification only.** No frontend, backend, or database changes. Implementing agents do not apply. QA uses this doc and §3 to run verification; any defects found may be tracked in bugfix child requirements.

#### Frontend

- None.

#### Backend

- None.

#### Contract / spec

- None (contract and spec already define function-level enforcement).

### Database changes

None.

---

## 3. Test approach

### Test case list (required)

| ID | Type | Scenario (input / condition) | Expected result | Verification |
|----|------|------------------------------|-----------------|---------------|
| TC-01 | Normal | **Holder – approve**: `user1` (decrypt_approver, has pending-approvals/search-history) | `GET /api/search-history/pending` → 200; `POST .../approve` → 200; `POST .../reject` → 200; UI approve/reject buttons enabled | API (curl) + optional browser |
| TC-02 | Exception | **Non-holder – approve**: `user2` (has pending-approvals screen but NOT decrypt_approver) | `GET /api/search-history/pending` → 403 `FUNCTION_NOT_ALLOWED`; `POST .../approve` → 403 `FUNCTION_NOT_ALLOWED`; UI buttons disabled with tooltip | API (curl) + browser |
| TC-03 | Normal | **Holder – write (permission-groups)**: `user3` (has user-management with write=true) | `POST /api/permission-groups` → 201; `PUT /api/permission-groups/{id}` → 200; `DELETE /api/permission-groups/{id}` → 200; `POST .../users` (assign) → 201; `DELETE .../users/{userId}` (unassign) → 200; UI create/edit/delete/assign enabled | API (curl) + optional browser |
| TC-04 | Exception | **Non-holder – write (permission-groups)**: User with user-management screen but write=false (read-only) | `POST /api/permission-groups` → 403 `FUNCTION_NOT_ALLOWED`; `PUT`, `DELETE`, assign, unassign → 403 `FUNCTION_NOT_ALLOWED`; UI create/edit/delete/assign disabled with tooltip | API (curl) + browser |
| TC-05 | Normal | **Read-only APIs (screen access)**: `user3` (has user-management screen) | `GET /api/users` → 200; `GET /api/permission-groups` → 200; `GET /api/permission-groups/{id}` → 200 (screen access check only, no write check) | API (curl) |
| TC-06 | Exception | **No screen access**: User without user-management/user-permission-hierarchy screen | `GET /api/users` → 403 `FORBIDDEN`; `GET /api/permission-groups` → 403 `FORBIDDEN` (screen access denied, not FUNCTION_NOT_ALLOWED) | API (curl) |
| TC-07 | Gap | **Department write (NOT IMPLEMENTED)**: DepartmentController has no POST/PUT/DELETE endpoints | No write APIs to test; `GET /api/departments` uses `requireDepartmentAccess()` (screen check only, no write check). Gap: write enforcement deferred until CRUD is implemented | Documentation only |
| TC-08 | Edge | **Deprecated endpoint**: `PUT /api/users/{userId}` | Always returns **410 Gone** (`ENDPOINT_REMOVED`) regardless of write permission; no `requireWriteForManagement()` check | API (curl) |
| TC-09 | Edge | **main screen**: `user2` (has main only or main + limited screens) | No write/approve actions available on main; `screenFunctions.main` = `{ read: true }` only | API (login/me) + browser |
| TC-10 | Regression | **is_system_admin**: `admin` | All write and approve APIs succeed (200/201); all `screenFunctions` have write/approve where applicable; UI fully enabled | API (curl) + browser |

### Test scenarios

#### Scenario 1: Approve – holder can use, non-holder cannot

**Holder** (`user1` — decrypt_approver):

```bash
# Step 1: Login as user1
curl -s -X POST -H "Content-Type: application/json" \
  -d '{"username":"user1","password":"password1"}' \
  -c /tmp/user1.cookie \
  http://localhost:9200/api/auth/login

# Step 2: Verify screenFunctions includes approve=true
curl -s -b /tmp/user1.cookie http://localhost:9200/api/auth/me | jq '.data.screenFunctions["pending-approvals"]'
# Expected: { "read": true, "approve": true }

# Step 3: Access pending list (approve-gated)
curl -s -b /tmp/user1.cookie http://localhost:9200/api/search-history/pending
# Expected: 200 with data

# Step 4: Approve (use a valid pending ID)
curl -s -X POST -b /tmp/user1.cookie http://localhost:9200/api/search-history/{id}/approve
# Expected: 200

# Step 5: Reject (use another valid pending ID)
curl -s -X POST -b /tmp/user1.cookie \
  -H "Content-Type: application/json" -d '{"rejectionReason":"test"}' \
  http://localhost:9200/api/search-history/{id}/reject
# Expected: 200
```

**Non-holder** (`user2` — has pending-approvals screen but NOT decrypt_approver):

```bash
# Step 1: Login as user2
curl -s -X POST -H "Content-Type: application/json" \
  -d '{"username":"user2","password":"password2"}' \
  -c /tmp/user2.cookie \
  http://localhost:9200/api/auth/login

# Step 2: Verify screenFunctions includes approve=false
curl -s -b /tmp/user2.cookie http://localhost:9200/api/auth/me | jq '.data.screenFunctions["pending-approvals"]'
# Expected: { "read": true, "approve": false }

# Step 3: Attempt pending list → 403
curl -s -b /tmp/user2.cookie http://localhost:9200/api/search-history/pending
# Expected: 403, code: "FUNCTION_NOT_ALLOWED"

# Step 4: Attempt approve → 403
curl -s -X POST -b /tmp/user2.cookie http://localhost:9200/api/search-history/1/approve
# Expected: 403, code: "FUNCTION_NOT_ALLOWED"

# Step 5: Attempt reject → 403
curl -s -X POST -b /tmp/user2.cookie \
  -H "Content-Type: application/json" -d '{"rejectionReason":"test"}' \
  http://localhost:9200/api/search-history/1/reject
# Expected: 403, code: "FUNCTION_NOT_ALLOWED"
```

#### Scenario 2: Write (permission-groups) – holder can use, non-holder cannot

**Holder** (`user3` — has user-management/user-permission-hierarchy with write=true):

```bash
# Step 1: Login as user3
curl -s -X POST -H "Content-Type: application/json" \
  -d '{"username":"user3","password":"password3"}' \
  -c /tmp/user3.cookie \
  http://localhost:9200/api/auth/login

# Step 2: Verify screenFunctions includes write=true
curl -s -b /tmp/user3.cookie http://localhost:9200/api/auth/me | jq '.data.screenFunctions["user-management"]'
# Expected: { "read": true, "write": true }

# Step 3: Create permission group
curl -s -X POST -b /tmp/user3.cookie \
  -H "Content-Type: application/json" \
  -d '{"name":"test-group-verify","description":"verification test"}' \
  http://localhost:9200/api/permission-groups
# Expected: 201

# Step 4: Update permission group
curl -s -X PUT -b /tmp/user3.cookie \
  -H "Content-Type: application/json" \
  -d '{"name":"test-group-verify-updated"}' \
  http://localhost:9200/api/permission-groups/{id}
# Expected: 200

# Step 5: Assign user to group
curl -s -X POST -b /tmp/user3.cookie \
  -H "Content-Type: application/json" \
  -d '{"userId":"user2"}' \
  http://localhost:9200/api/permission-groups/{id}/users
# Expected: 201

# Step 6: Unassign user
curl -s -X DELETE -b /tmp/user3.cookie \
  http://localhost:9200/api/permission-groups/{id}/users/user2
# Expected: 200

# Step 7: Delete permission group
curl -s -X DELETE -b /tmp/user3.cookie \
  http://localhost:9200/api/permission-groups/{id}
# Expected: 200
```

**Non-holder** (user with user-management screen but write=false — requires test data setup or use user without write):

```bash
# Login as non-write user (write=false for user-management)
# Step 1: Attempt create permission group → 403
curl -s -X POST -b /tmp/nonwrite.cookie \
  -H "Content-Type: application/json" \
  -d '{"name":"should-fail"}' \
  http://localhost:9200/api/permission-groups
# Expected: 403, code: "FUNCTION_NOT_ALLOWED"

# Step 2: Attempt update → 403
curl -s -X PUT -b /tmp/nonwrite.cookie \
  -H "Content-Type: application/json" \
  -d '{"name":"should-fail"}' \
  http://localhost:9200/api/permission-groups/1
# Expected: 403, code: "FUNCTION_NOT_ALLOWED"

# Step 3: Attempt assign → 403
curl -s -X POST -b /tmp/nonwrite.cookie \
  -H "Content-Type: application/json" \
  -d '{"userId":"user2"}' \
  http://localhost:9200/api/permission-groups/1/users
# Expected: 403, code: "FUNCTION_NOT_ALLOWED"

# Step 4: Attempt delete → 403
curl -s -X DELETE -b /tmp/nonwrite.cookie http://localhost:9200/api/permission-groups/1
# Expected: 403, code: "FUNCTION_NOT_ALLOWED"
```

#### Scenario 3: Screen access vs function denial (error code distinction)

```bash
# User WITHOUT user-management screen → FORBIDDEN (screen access denied)
curl -s -b /tmp/no_screen_user.cookie http://localhost:9200/api/users
# Expected: 403, code: "FORBIDDEN"

# User WITH user-management screen but write=false → FUNCTION_NOT_ALLOWED (function denied)
curl -s -X POST -b /tmp/nonwrite.cookie \
  -H "Content-Type: application/json" \
  -d '{"name":"test"}' \
  http://localhost:9200/api/permission-groups
# Expected: 403, code: "FUNCTION_NOT_ALLOWED"
```

#### Scenario 4: Direct API (no privilege escalation)

For **every** write and approve API in scope, call it with a non-holder session:

| API | Non-holder | Expected |
|-----|-----------|----------|
| `GET /api/search-history/pending` | user2 (not approver) | 403 `FUNCTION_NOT_ALLOWED` |
| `POST /api/search-history/{id}/approve` | user2 | 403 `FUNCTION_NOT_ALLOWED` |
| `POST /api/search-history/{id}/reject` | user2 | 403 `FUNCTION_NOT_ALLOWED` |
| `POST /api/permission-groups` | non-write user | 403 `FUNCTION_NOT_ALLOWED` |
| `PUT /api/permission-groups/{id}` | non-write user | 403 `FUNCTION_NOT_ALLOWED` |
| `DELETE /api/permission-groups/{id}` | non-write user | 403 `FUNCTION_NOT_ALLOWED` |
| `POST /api/permission-groups/{id}/users` | non-write user | 403 `FUNCTION_NOT_ALLOWED` |
| `DELETE /api/permission-groups/{id}/users/{uid}` | non-write user | 403 `FUNCTION_NOT_ALLOWED` |

**Verification**: Every call returns 403 with the expected code. No 200 for write/approve actions by non-holders.

#### Scenario 5: Deprecated endpoint (PUT /api/users/{userId})

```bash
# Any user with screen access → 410 Gone (not 403)
curl -s -X PUT -b /tmp/user3.cookie \
  -H "Content-Type: application/json" -d '{}' \
  http://localhost:9200/api/users/someuser
# Expected: 410 Gone, code: "ENDPOINT_REMOVED"
# Note: This endpoint has NO write function check — it always returns 410.
```

### Test data

**Users (from init-data)**

| User | is_system_admin | decrypt_approver | Screens | write | approve | Role in tests |
|------|----------------|-----------------|---------|-------|---------|---------------|
| `admin` | ✓ | — | all | all | all | TC-10 regression: system admin bypasses all checks |
| `user1` | ✗ | ✓ | pending-approvals, search-history, etc. | — | ✓ (via decrypt_approver) | TC-01 approve holder |
| `user2` | ✗ | ✗ | pending-approvals, search-history, main, etc. | — | ✗ | TC-02 approve non-holder, TC-09 main |
| `user3` | ✗ | ✗ | user-management, user-permission-hierarchy | ✓ (derived) | — | TC-03 write holder |
| (TBD) | ✗ | ✗ | user-management (read-only, write=false) | ✗ | — | TC-04 write non-holder (**requires test data setup**: permission group with write=false for user-management) |

**Note on TC-04 test data**: Currently, the derivation rule is `read implies write` for management screens (when pgs.write is null). To test write=false, a permission_group_screen row with explicit `write=false` must exist. If init-data does not include such a user, create a test permission group with write=false before running TC-04.

**Prerequisite data**

- At least one `search_history` row with status `PENDING` for approve/reject tests (TC-01, TC-02).
- At least one existing permission group (for TC-03 update/delete tests).
- Existing users and departments per init-data.

### Test environment

- Frontend: `http://localhost:3001` (per contract)
- Backend: `http://localhost:9200`
- Database: PostgreSQL (per contract)

### 3.5 Browser automation verification (optional)

For UI verification, QA may use browser automation (see `docs/workflow/BROWSER-AUTOMATION-VERIFICATION-POLICY.md`).

**Important**: Browser verification should be performed **after** all API tests (Scenarios 1–5) pass. API-level enforcement is the security boundary; UI is UX-only.

- **Applicable TCs**: TC-01, TC-02 (pending-approvals: approve/reject buttons enabled/disabled), TC-03, TC-04 (permission-group-management: create/edit/delete/assign enabled/disabled), TC-09 (main: no write/approve actions).
- **Not applicable**: TC-07 (department write — no CRUD UI exists yet).
- **Procedure**:
  1. Log in as holder (`user1` for approve, `user3` for write) → navigate to screen → `browser_snapshot` → confirm buttons enabled and no "권한이 없습니다" tooltip.
  2. Log in as non-holder (`user2` for approve, write=false user for write) → same screen → `browser_snapshot` → confirm buttons disabled and tooltip/message present (e.g. "승인 권한이 없습니다", "수정 권한이 없습니다").
  3. On main screen: confirm no approve/reject/write buttons exist.

---

## 4. Checklist

### Frontend verification

- [ ] All screens with write/approve use `screenFunctions` (or equivalent) to enable/disable actions.
- [ ] Non-holders see disabled buttons with tooltip/message (no silent hide without explanation where UX requires it).
- [ ] main has no write/approve actions.

### Backend verification

- [ ] All approve APIs (pending, approve, reject) validate decrypt_approver or is_system_admin; return 403 FUNCTION_NOT_ALLOWED when lacking.
- [ ] All permission-group write APIs (POST, PUT, DELETE, assign, unassign) validate `requireWriteForManagement()`; return 403 FUNCTION_NOT_ALLOWED when lacking.
- [ ] No write/approve API succeeds for a non-holder (no privilege escalation).
- [ ] `PUT /api/users/{userId}` returns 410 Gone (deprecated; no write check — acceptable).
- [ ] Gap documented: DepartmentController has no write CRUD endpoints; write enforcement deferred.

### Integration

- [ ] Holder test cases (TC-01, TC-03, TC-10) pass: API 200/201.
- [ ] Non-holder test cases (TC-02, TC-04) pass: API 403 FUNCTION_NOT_ALLOWED and UI disabled.
- [ ] Screen access test (TC-05 read-only APIs pass, TC-06 no-screen → FORBIDDEN).
- [ ] Gap/edge test cases (TC-07 documented, TC-08 410 Gone confirmed).
- [ ] Scenario 4 (direct API) executed for all listed write/approve APIs — no 200 for non-holders.

### Documentation

- [ ] Requirement doc completed.
- [ ] §5 Test results filled after verification.
- [ ] If Security reviewed §2.1, checklist updated.

---

## 5. Test results

### Test run date

- [Date and time]

### Test results

#### Frontend

- [ ] Pass / Fail — UI enable/disable and tooltips per screenFunctions.

#### Backend

- [ ] Pass / Fail — 403 FUNCTION_NOT_ALLOWED for non-holders on all write/approve APIs.

**Commands**: See §3 Scenarios 1–5 for full curl commands (session-cookie-based auth, not Bearer token).

**Outcome:**

- [Record per TC: TC-01 through TC-10 results]

### Issues found and resolution

- [If any test fails, record here and create bugfix child requirement; re-verify after fix.]

### Next steps

- Run verification per §3; record results in §5.
- If Security review is requested, hand off requirement doc to Security subagent for §2.1.
- After all pass, add doc to `docs/requirements/TOPIC-INDEX.md` under permission / access-control.

---

**Author**: Requirements subagent  
**Date**: 2025-03-04  
**Status**: In progress (verification pending)
