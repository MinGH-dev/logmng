# 20250304 - Permission holder vs non-holder function verification

## 1. User requirement

### Requirement description

When a permission group grants **modify** (write) or **approve** functions to its members, the system must ensure:

1. **Holders**: Each user who has the function (write or approve) in their permission group can use the corresponding APIs and features normally.
2. **Non-holders**: Users who do **not** have the function (e.g. screen access but write=false, or no approve) must **not** be able to perform those actions; the server must deny with a clear error (403 and appropriate code).

This requirement is to **verify** the current behaviour by analysis and tests, not to change design. Any gap found (e.g. missing check or wrong error code) will be handled in a separate requirement.

### User scenario

1. Admin configures a permission group with **write** enabled for user-management (or user-permission-hierarchy). Members can create/update/delete permission groups and assign/unassign users.
2. Admin configures a permission group with **write=false** for the same screen. Members can open the screen and read data but must not create/update/delete groups or assign users.
3. Admin configures **approve** for search-history/pending-approvals and designates some users as decrypt_approver. Those users can open pending list and approve/reject. Others with the screen but without approve (or not approver) must not call approve/reject APIs.
4. **Problem**: Without verification, it is unclear whether holders consistently succeed and non-holders are consistently denied with the correct HTTP status and error code.

### Expected outcome

- **Holder (write)**: User with `screenFunctions[user-management].write === true` or `screenFunctions[user-permission-hierarchy].write === true` can call POST/PUT/DELETE permission-groups and assign/unassign user APIs → 200/201 success.
- **Non-holder (write)**: User with screen access but `write === false` for both screens → same write APIs → **403** with **FUNCTION_NOT_ALLOWED**.
- **Holder (approve)**: User who is decrypt_approver (or is_system_admin) and has `approve === true` for search-history or pending-approvals → GET pending, POST approve, POST reject → **200** success.
- **Non-holder (approve)**: User with screen but not decrypt_approver, or approve=false → approve-gated APIs → **403** with **FUNCTION_NOT_ALLOWED**.
- **No screen access**: User without the required screen in `allowedScreenIds` → **403** with **FORBIDDEN** (interceptor or controller).

---

## 2. Design

### 2.1 Security review (optional; when PII / decryption / access control)

- [x] Access control only (no new PII/decryption scope). Verification of existing function checks.
- **Risks**: None new; we are verifying that write/approve are enforced so non-holders cannot escalate.
- **Recommendation**: Confirm backend returns FUNCTION_NOT_ALLOWED (not FORBIDDEN) when screen is allowed but function is denied, so clients can show the right message.

### Technical design

#### Problem analysis

1. **Write function** is enforced in `PermissionGroupController` via `requireWriteForManagement()` → `AuthService.hasWriteForManagementScreens(request)`. It requires `screenFunctions` to have `write === true` for user-management **or** user-permission-hierarchy. Denial: 403 `FUNCTION_NOT_ALLOWED`.
2. **Approve function** is enforced in `SearchHistoryController` via `requireApproverOrAdmin()`, which checks (a) user is decrypt_approver or is_system_admin, and (b) `AuthService.hasApproveForSearchHistory(request)` (approve for search-history or pending-approvals). Denial: 403 `FUNCTION_NOT_ALLOWED`.
3. **Screen access** is enforced by `ScreenAccessInterceptor` (path → required screen) and controller methods like `requireUserManagementAccess`. Denial: 403 `FORBIDDEN`.
4. **Derivation**: For management screens, when `permission_group_screen.write` is null, backend derives write=true (read implies write). To test non-holder write=false, DB must have explicit `write=false`.

#### Solution approach

**No implementation change in this requirement.** Scope is analysis and test design.

- **Frontend**: Not in scope for this verification (backend API behaviour only). UI may hide/disable buttons based on `screenFunctions`; that is a separate consistency check.
- **Backend**: Confirm that all write-gated and approve-gated endpoints use the above checks and return the expected status/code. Add or adjust tests if gaps are found in a follow-up.

**Known gap — DepartmentController**: Currently provides only read APIs (`GET /api/departments`, `GET /api/departments/user-permission-hierarchy`). These are screen-access gated but have **no write endpoints** (POST/PUT/DELETE) and no `requireWriteForManagement` checks. If department CRUD is later required, write-gated APIs must be added. This does not affect current verification scope but is documented for completeness. See: `api-permission-map` skill §Gaps.

### Scope of functions and APIs

| Function | Screens (permission group) | Backend check | Denial code | APIs |
|----------|----------------------------|---------------|-------------|------|
| **Write** | user-management, user-permission-hierarchy | `requireWriteForManagement` → `hasWriteForManagementScreens` | FUNCTION_NOT_ALLOWED | POST/ PUT/DELETE `/api/permission-groups`, POST/DELETE `/api/permission-groups/{id}/users` |
| **Approve** | search-history, pending-approvals | `requireApproverOrAdmin` → decrypt_approver + `hasApproveForSearchHistory` | FUNCTION_NOT_ALLOWED | GET `/api/search-history/pending`, POST `/api/search-history/{id}/approve`, POST `/api/search-history/{id}/reject` |

APIs that only require **screen access** (no write/approve check): GET permission-groups, GET permission-groups/{id}, GET permission-groups/{id}/users, GET users, GET departments, etc. Denial when screen missing: FORBIDDEN.

### Change file list

**(Verification-only. No code change unless a follow-up requirement adds tests or fixes.)**

#### Frontend
- None.

#### Backend
- Optional later: add or extend integration tests for holder/non-holder (per follow-up).

### Database changes

None. Test data: at least one permission group with write=true, one with write=false (explicit in permission_group_screen), and users assigned to each; decrypt_approver and non-approver users for approve tests.

---

## 3. Test approach

### Test case list (required)

| ID | Type | Scenario (input / condition) | Expected result | Verification |
|----|------|------------------------------|-----------------|--------------|
| TC-01 | Normal | User has write=true (user-management or user-permission-hierarchy). Call POST /api/permission-groups (valid body). | 201 Created | Integration (curl/login + request) |
| TC-02 | Normal | Same holder. Call PUT /api/permission-groups/{id} (valid body). | 200 OK | Integration |
| TC-03 | Normal | Same holder. Call DELETE /api/permission-groups/{id}. | 200 OK | Integration |
| TC-04 | Normal | Same holder. Call POST /api/permission-groups/{id}/users (userId). | 200/201 | Integration |
| TC-05 | Normal | Same holder. Call DELETE /api/permission-groups/{id}/users/{userId}. | 200 OK | Integration |
| TC-06 | Exception | User has screen (user-management or user-permission-hierarchy) but write=false. Call POST /api/permission-groups. | 403, body code FUNCTION_NOT_ALLOWED | Integration |
| TC-07 | Exception | Same non-holder (write=false). Call PUT /api/permission-groups/{id}. | 403, FUNCTION_NOT_ALLOWED | Integration |
| TC-08 | Exception | Same non-holder. Call POST /api/permission-groups/{id}/users. | 403, FUNCTION_NOT_ALLOWED | Integration |
| TC-09 | Normal | User is decrypt_approver and has approve=true for pending-approvals (or search-history). Call GET /api/search-history/pending. | 200 OK | Integration |
| TC-10 | Normal | Same approver. Call POST /api/search-history/{id}/approve. | 200 OK | Integration |
| TC-11 | Normal | Same approver. Call POST /api/search-history/{id}/reject. | 200 OK | Integration |
| TC-12a | Exception | User has pending-approvals screen but is **not** decrypt_approver and not is_system_admin. Call GET /api/search-history/pending. | 403, FUNCTION_NOT_ALLOWED | Integration |
| TC-12b | Edge | User **is** decrypt_approver but has **approve=false** (explicit in DB) for pending-approvals. Call GET /api/search-history/pending. | 403, FUNCTION_NOT_ALLOWED (explicit deny overrides approver status) | Integration |
| TC-13 | Exception | Same non-approver (TC-12a user). Call POST /api/search-history/{id}/approve. | 403, FUNCTION_NOT_ALLOWED | Integration |
| TC-14 | Exception | User has no screen for permission-groups (not in allowedScreenIds). Call POST /api/permission-groups. | 403, FORBIDDEN | Integration |
| TC-15 | Exception | User has no pending-approvals screen. Call GET /api/search-history/pending. | 403, FORBIDDEN | Integration |
| TC-SYS1 | Edge | **is_system_admin** user calls all write-gated APIs (POST/PUT/DELETE /api/permission-groups, POST/DELETE /api/permission-groups/{id}/users). | All succeed (bypass) | Integration |
| TC-SYS2 | Edge | **is_system_admin** user calls all approve-gated APIs (GET pending, POST approve, POST reject). | All succeed (bypass) | Integration |

### Test scenarios

#### Scenario 1: Write holder

1. Log in as user in a permission group that has user-management (or user-permission-hierarchy) with **write=true** (or null so derived true).
2. Create group: POST /api/permission-groups with valid name.
3. Update group: PUT /api/permission-groups/{id}.
4. Assign user: POST /api/permission-groups/{id}/users.
5. Unassign: DELETE /api/permission-groups/{id}/users/{userId}.
6. **Verification**: All return 200/201; no 403.

#### Scenario 2: Write non-holder

1. Log in as user in a permission group that has user-management (or user-permission-hierarchy) with **write=false** (explicit in DB).
2. Call POST /api/permission-groups, PUT /api/permission-groups/{id}, POST /api/permission-groups/{id}/users.
3. **Verification**: Each returns 403 and response body error code is FUNCTION_NOT_ALLOWED (not FORBIDDEN).

#### Scenario 3: Approve holder

1. Log in as user who is in decrypt_approver and has approve for pending-approvals (or search-history).
2. Call GET /api/search-history/pending, POST /api/search-history/{id}/approve, POST /api/search-history/{id}/reject (with valid id).
3. **Verification**: 200 OK where applicable (pending may be empty; approve/reject need existing pending request).

#### Scenario 4a: Approve non-holder (not decrypt_approver)

1. Log in as user who has pending-approvals screen but is **not** in decrypt_approver and not is_system_admin.
2. Call GET /api/search-history/pending and POST /api/search-history/{id}/approve.
3. **Verification**: 403 with FUNCTION_NOT_ALLOWED.

#### Scenario 4b: Approve denied by explicit approve=false (edge case)

1. Log in as user who **is** in decrypt_approver but has **approve=false** (explicit in DB) for pending-approvals.
2. Call GET /api/search-history/pending.
3. **Verification**: 403 with FUNCTION_NOT_ALLOWED. Explicit deny in DB overrides decrypt_approver status.

#### Scenario 5: No screen (FORBIDDEN)

1. Log in as user whose allowedScreenIds do **not** include user-management or user-permission-hierarchy.
2. Call POST /api/permission-groups or GET /api/permission-groups.
3. **Verification**: 403 with FORBIDDEN (interceptor or controller).
4. Same for user without pending-approvals: GET /api/search-history/pending → 403 FORBIDDEN.

#### Scenario 6: System admin bypass

1. Log in as **is_system_admin** user.
2. Call all write-gated APIs: POST/PUT/DELETE /api/permission-groups, POST/DELETE /api/permission-groups/{id}/users.
3. Call all approve-gated APIs: GET /api/search-history/pending, POST approve, POST reject.
4. **Verification**: All return 200/201 (system admin bypasses all permission checks).

### Test data

- Permission group **PG-Write**: user-management or user-permission-hierarchy with **write=true** (or null). User **holder-write** assigned.
- Permission group **PG-NoWrite**: same screen(s) with **write=false** in permission_group_screen. User **nonholder-write** assigned.
- **Approver user**: in decrypt_approver; permission group with pending-approvals (or search-history) and approve=true or null → **holder-approve**.
- **Non-approver user**: permission group with pending-approvals but user not in decrypt_approver, or approve=false → **nonholder-approve**.
- **Approver-deny user** (TC-12b): in decrypt_approver AND has pending-approvals screen, but **approve=false** (explicit in DB) → tests explicit deny overriding approver status.
- **No-screen user**: permission group with only e.g. main, no user-management/pending-approvals → for TC-14, TC-15.

**Important — explicit DB setup for non-holder tests**: The derivation rule in `AuthService.resolveScreenFunctions` is "read implies write" for management screens when `permission_group_screen.write` is null. Therefore TC-06~08 (write non-holder) require **`write = false` explicitly** in DB (not null). Same for TC-12b (approve=false). If init-data does not include these rows, set them before testing:

```sql
-- PG-NoWrite: explicit write=false for user-management
UPDATE permission_group_screen
SET write = false
WHERE permission_group_id = <PG-NoWrite-id>
  AND screen_id = 'user-management';

-- Approver-deny: explicit approve=false for pending-approvals
UPDATE permission_group_screen
SET approve = false
WHERE permission_group_id = <approver-deny-group-id>
  AND screen_id = 'pending-approvals';
```

### Test environment

- Frontend: http://localhost:3001 (optional for this backend-focused verification)
- Backend: http://localhost:9200
- Database: PostgreSQL (per project)

### 3.5 Browser automation verification (optional)

Not required for this backend API verification. If UI consistency (e.g. buttons hidden when write=false) is verified later, add TCs and §3.5 in that requirement.

---

## 4. Checklist

### Frontend verification
- [ ] N/A (backend verification only)

### Backend verification
- [ ] Test cases TC-01–TC-15, TC-12b, TC-SYS1, TC-SYS2 run (integration/curl)
- [ ] Holder cases return 200/201
- [ ] Non-holder write returns 403 FUNCTION_NOT_ALLOWED
- [ ] Non-holder approve returns 403 FUNCTION_NOT_ALLOWED
- [ ] Edge: decrypt_approver + approve=false → 403 FUNCTION_NOT_ALLOWED (TC-12b)
- [ ] No-screen returns 403 FORBIDDEN
- [ ] System admin bypass: all write and approve APIs succeed (TC-SYS1, TC-SYS2)

### Integration
- [ ] End-to-end flow: login → call API → assert status and error code
- [ ] Edge cases: write=false, approve=false, no screen, system admin bypass

### Documentation
- [ ] Requirement doc completed
- [ ] §5 Test results filled after runs

---

## 5. Test results

### Test run date
- [To be filled]

### Test results

#### Backend
- [ ] Pass / [ ] Fail
- [Result description]

**Commands:**

```bash
# ===== Login (save session cookies) =====

# holder-write: user with write=true for user-management
curl -s -X POST -H "Content-Type: application/json" \
  -d '{"userId":"<holder-w-id>","password":"<password>"}' \
  -c holder-w.txt http://localhost:9200/api/auth/login

# nonholder-write: user with write=false (explicit) for user-management
curl -s -X POST -H "Content-Type: application/json" \
  -d '{"userId":"<nonholder-w-id>","password":"<password>"}' \
  -c nonholder-w.txt http://localhost:9200/api/auth/login

# holder-approve: decrypt_approver with approve for pending-approvals
curl -s -X POST -H "Content-Type: application/json" \
  -d '{"userId":"<holder-a-id>","password":"<password>"}' \
  -c holder-a.txt http://localhost:9200/api/auth/login

# nonholder-approve: not decrypt_approver, has pending-approvals screen
curl -s -X POST -H "Content-Type: application/json" \
  -d '{"userId":"<nonholder-a-id>","password":"<password>"}' \
  -c nonholder-a.txt http://localhost:9200/api/auth/login

# approver-deny: IS decrypt_approver but approve=false (explicit)
curl -s -X POST -H "Content-Type: application/json" \
  -d '{"userId":"<approver-deny-id>","password":"<password>"}' \
  -c approver-deny.txt http://localhost:9200/api/auth/login

# no-screen: user without user-management or pending-approvals
curl -s -X POST -H "Content-Type: application/json" \
  -d '{"userId":"<noscreen-id>","password":"<password>"}' \
  -c noscreen.txt http://localhost:9200/api/auth/login

# sysadmin: is_system_admin user
curl -s -X POST -H "Content-Type: application/json" \
  -d '{"userId":"admin","password":"<password>"}' \
  -c sysadmin.txt http://localhost:9200/api/auth/login

# ===== Write holder (TC-01 ~ TC-05) =====

# TC-01: POST permission-groups → 201
curl -s -w "\nHTTP %{http_code}\n" -X POST -H "Content-Type: application/json" \
  -d '{"code":"tc01","name":"TC01 Test Group"}' \
  -b holder-w.txt http://localhost:9200/api/permission-groups

# TC-02: PUT permission-groups/{id} → 200
curl -s -w "\nHTTP %{http_code}\n" -X PUT -H "Content-Type: application/json" \
  -d '{"code":"tc01","name":"TC01 Updated"}' \
  -b holder-w.txt http://localhost:9200/api/permission-groups/<id>

# TC-03: DELETE permission-groups/{id} → 200
curl -s -w "\nHTTP %{http_code}\n" -X DELETE \
  -b holder-w.txt http://localhost:9200/api/permission-groups/<id>

# TC-04: POST assign user → 200/201
curl -s -w "\nHTTP %{http_code}\n" -X POST -H "Content-Type: application/json" \
  -d '{"userId":"<target-user>"}' \
  -b holder-w.txt http://localhost:9200/api/permission-groups/<id>/users

# TC-05: DELETE unassign user → 200
curl -s -w "\nHTTP %{http_code}\n" -X DELETE \
  -b holder-w.txt http://localhost:9200/api/permission-groups/<id>/users/<userId>

# ===== Write non-holder (TC-06 ~ TC-08) =====

# TC-06: POST → 403 FUNCTION_NOT_ALLOWED
curl -s -w "\nHTTP %{http_code}\n" -X POST -H "Content-Type: application/json" \
  -d '{"code":"tc06","name":"Should Fail"}' \
  -b nonholder-w.txt http://localhost:9200/api/permission-groups

# TC-07: PUT → 403 FUNCTION_NOT_ALLOWED
curl -s -w "\nHTTP %{http_code}\n" -X PUT -H "Content-Type: application/json" \
  -d '{"code":"tc06","name":"Should Fail"}' \
  -b nonholder-w.txt http://localhost:9200/api/permission-groups/<id>

# TC-08: POST assign → 403 FUNCTION_NOT_ALLOWED
curl -s -w "\nHTTP %{http_code}\n" -X POST -H "Content-Type: application/json" \
  -d '{"userId":"<target-user>"}' \
  -b nonholder-w.txt http://localhost:9200/api/permission-groups/<id>/users

# ===== Approve holder (TC-09 ~ TC-11) =====

# TC-09: GET pending → 200
curl -s -w "\nHTTP %{http_code}\n" \
  -b holder-a.txt http://localhost:9200/api/search-history/pending

# TC-10: POST approve → 200
curl -s -w "\nHTTP %{http_code}\n" -X POST \
  -b holder-a.txt http://localhost:9200/api/search-history/<pending-id>/approve

# TC-11: POST reject → 200
curl -s -w "\nHTTP %{http_code}\n" -X POST \
  -b holder-a.txt http://localhost:9200/api/search-history/<pending-id>/reject

# ===== Approve non-holder (TC-12a, TC-12b, TC-13) =====

# TC-12a: not decrypt_approver → 403 FUNCTION_NOT_ALLOWED
curl -s -w "\nHTTP %{http_code}\n" \
  -b nonholder-a.txt http://localhost:9200/api/search-history/pending

# TC-12b: decrypt_approver + approve=false → 403 FUNCTION_NOT_ALLOWED
curl -s -w "\nHTTP %{http_code}\n" \
  -b approver-deny.txt http://localhost:9200/api/search-history/pending

# TC-13: non-approver POST approve → 403 FUNCTION_NOT_ALLOWED
curl -s -w "\nHTTP %{http_code}\n" -X POST \
  -b nonholder-a.txt http://localhost:9200/api/search-history/<pending-id>/approve

# ===== No screen (TC-14, TC-15) =====

# TC-14: no user-management screen → 403 FORBIDDEN
curl -s -w "\nHTTP %{http_code}\n" -X POST -H "Content-Type: application/json" \
  -d '{"code":"tc14","name":"Should Fail"}' \
  -b noscreen.txt http://localhost:9200/api/permission-groups

# TC-15: no pending-approvals screen → 403 FORBIDDEN
curl -s -w "\nHTTP %{http_code}\n" \
  -b noscreen.txt http://localhost:9200/api/search-history/pending

# ===== System admin bypass (TC-SYS1, TC-SYS2) =====

# TC-SYS1: sysadmin write → all succeed
curl -s -w "\nHTTP %{http_code}\n" -X POST -H "Content-Type: application/json" \
  -d '{"code":"sys01","name":"Admin Group"}' \
  -b sysadmin.txt http://localhost:9200/api/permission-groups

# TC-SYS2: sysadmin approve → all succeed
curl -s -w "\nHTTP %{http_code}\n" \
  -b sysadmin.txt http://localhost:9200/api/search-history/pending
```

**Outcome:**

- [ ] TC-01: [HTTP code, response]
- [ ] TC-02: [HTTP code, response]
- [ ] TC-03: [HTTP code, response]
- [ ] TC-04: [HTTP code, response]
- [ ] TC-05: [HTTP code, response]
- [ ] TC-06: [HTTP 403, code=FUNCTION_NOT_ALLOWED]
- [ ] TC-07: [HTTP 403, code=FUNCTION_NOT_ALLOWED]
- [ ] TC-08: [HTTP 403, code=FUNCTION_NOT_ALLOWED]
- [ ] TC-09: [HTTP 200]
- [ ] TC-10: [HTTP 200]
- [ ] TC-11: [HTTP 200]
- [ ] TC-12a: [HTTP 403, code=FUNCTION_NOT_ALLOWED]
- [ ] TC-12b: [HTTP 403, code=FUNCTION_NOT_ALLOWED]
- [ ] TC-13: [HTTP 403, code=FUNCTION_NOT_ALLOWED]
- [ ] TC-14: [HTTP 403, code=FORBIDDEN]
- [ ] TC-15: [HTTP 403, code=FORBIDDEN]
- [ ] TC-SYS1: [all succeed]
- [ ] TC-SYS2: [all succeed]

### Issues found and resolution

- [To be filled if any]

### Next steps

1. Run TC-01–TC-15, TC-12b, TC-SYS1, TC-SYS2 per test data.
2. If any gap (e.g. missing check or wrong code), create follow-up requirement and fix.
3. Optionally add integration tests to backend for holder/non-holder.

---

## 6. Error remedy result — for error/bug fix requirements only

N/A (verification requirement).

---

**Author**: Agent (new analysis, no reference to existing requirement docs)  
**Date**: 2025-03-04  
**Status**: In progress
