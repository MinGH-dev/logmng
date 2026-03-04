# 20250304 - Write/Approve holder vs non-holder verification

**Superseded by**: `docs/requirements/20250304-permission-group-function-verification.md` (single source of truth for permission group function verification).

---

## 1. User requirement

### Requirement description

When a permission group allows **write** (e.g. modify permission groups, assign/unassign users) or **approve** (e.g. approve/reject search-history decryption requests), we must verify:

1. **Permission holders**: Users who have the corresponding function (write or approve) can use those functions normally (APIs return success).
2. **Non-holders**: Users who do **not** have the function cannot exercise it; they must receive 403 with an appropriate error code (FORBIDDEN when they lack screen access, FUNCTION_NOT_ALLOWED when they have screen access but lack the function).

This requirement is a **verification/analysis** requirement: define the test scope and test cases so that holder success and non-holder denial are systematically checked. No new feature implementation; only test design and execution.

### User scenario

1. An administrator configures permission groups and assigns **write** and/or **approve** per screen (e.g. user-permission-hierarchy with write, pending-approvals with approve).
2. Some users are assigned to groups that have write/approve; others are assigned to groups that have only read (or explicit write=false / approve=false).
3. **Problem**: Without verification, we cannot be sure that (a) holders can actually call write/approve APIs, and (b) non-holders are correctly blocked from calling them.

### Expected outcome

- A single requirement document that defines:
  - Scope of verification: which APIs are write-gated, which are approve-gated.
  - Roles: system admin (bypass), holder (has function), non-holder (has screen but not function, or no screen).
  - Test cases: system admin bypass, holder success per function, non-holder denial (FORBIDDEN vs FUNCTION_NOT_ALLOWED), and explicit deny (e.g. write=false in DB).
- §5 test results filled after QA runs the tests (curl or browser).
- Traceability: each commit that closes this requirement references this document.

---

## 2. Design

### 2.1 Security review (optional; when PII / decryption / access control)

This requirement concerns **access control** (who can call write/approve APIs). Security subagent may review if desired; summary:

- **Risks**: Incorrect enforcement would allow non-holders to create/update/delete permission groups or approve decryption.
- **Acceptance**: All test cases in §3 must pass: holders succeed, non-holders receive 403 with correct code.

- [ ] Security review performed (check if applicable)

### Technical design

#### Problem analysis

1. **Write** and **approve** are enforced in different layers: screen access (interceptor or controller) → FORBIDDEN; function check (controller) → FUNCTION_NOT_ALLOWED.
2. **Holder** definition differs by function:
   - **Write**: User has user-management OR user-permission-hierarchy in allowedScreenIds and `screenFunctions` for that screen has write=true (explicit or derived: null → true for management screens).
   - **Approve**: User is (decrypt_approver OR is_system_admin) AND `screenFunctions` for search-history or pending-approvals has approve=true (explicit or derived: pgs.approve not false and approver/admin).
3. **Non-holder** cases: (a) no screen → FORBIDDEN; (b) screen but write=false or not approver → FUNCTION_NOT_ALLOWED.
4. **System admin** bypasses all checks; must have at least one TC confirming bypass.

#### Solution approach

- **No code change** for this requirement. Rely on existing enforcement:
  - **Write**: `PermissionGroupController` — `requireUserManagementAccess` then `requireWriteForManagement` on POST/PUT/DELETE permission-groups and POST/DELETE group users.
  - **Approve**: `SearchHistoryController` — `requireApproverOrAdmin` on GET /pending, POST /approve, POST /reject.
- **Deliverable**: Requirement doc with §1, §2, §3 (test cases with one curl per TC and test data SQL where derivation rules apply). QA runs tests and records §5.

**Frontend:** None (verification is API-level; optional browser TCs can be added in §3.5).

**Backend:** No change; verification only.

### Scope of verification (API map)

**Write-gated APIs** (PermissionGroupController, `requireWriteForManagement` after screen access):

| Method | Path |
|--------|------|
| POST | /api/permission-groups |
| PUT | /api/permission-groups/{id} |
| DELETE | /api/permission-groups/{id} |
| POST | /api/permission-groups/{id}/users |
| DELETE | /api/permission-groups/{id}/users/{userId} |

**Approve-gated APIs** (SearchHistoryController, `requireApproverOrAdmin`):

| Method | Path |
|--------|------|
| GET | /api/search-history/pending |
| POST | /api/search-history/{id}/approve |
| POST | /api/search-history/{id}/reject |

**Screen-access-only (no function check)** — only FORBIDDEN possible when screen missing:

- GET /api/permission-groups, GET /api/permission-groups/{id}, GET /api/permission-groups/{id}/users, GET /api/users, GET /api/departments, etc.

**Known gaps (no write enforcement):**

- UserController: no write-gated endpoints (PUT /api/users/{userId} is 410 Gone).
- DepartmentController: no POST/PUT/DELETE; only GET. No write TCs for department CRUD.

### Change file list

No implementation changes. This is a verification requirement.

#### Frontend
- None.

#### Backend
- None.

### Database changes
None. Test data (e.g. permission_group_screen.write=false) uses existing schema.

---

## 3. Test approach

### Test case list (required)

**Completeness checklist** (permission/access-control):

- [x] **System admin bypass TC**: At least one TC where is_system_admin user calls write and approve APIs → expect success.
- [x] **Explicit deny**: TC with write=false in DB (or approve=false) to verify override of derivation (null→true for write).
- [x] **Error code distinction**: FORBIDDEN (no screen) vs FUNCTION_NOT_ALLOWED (screen OK, function denied) with separate TCs.
- [x] **Known gaps noted**: UserController/DepartmentController write not in scope (§2).
- [x] **§5 curl commands**: One login per role + one curl per TC (see §5 structure below).
- [x] **Test data SQL**: Executable SQL for write=false in Test data section.

| ID | Type | Scenario (input / condition) | Expected result | Verification |
|----|------|------------------------------|-----------------|--------------|
| TC-01 | Normal | System admin: POST /api/permission-groups (create) | 201, success | integration (curl) |
| TC-02 | Normal | System admin: PUT /api/permission-groups/{id} (update) | 200, success | integration (curl) |
| TC-03 | Normal | System admin: GET /api/search-history/pending | 200, success | integration (curl) |
| TC-04 | Normal | System admin: POST /api/search-history/{id}/approve | 200 or 4xx per business (e.g. NOT_FOUND) | integration (curl) |
| TC-05 | Normal | Holder (write): User has user-permission-hierarchy with write=true (or derived). POST /api/permission-groups | 201, success | integration (curl) |
| TC-06 | Normal | Holder (write): Same user. PUT /api/permission-groups/{id} | 200, success | integration (curl) |
| TC-07 | Normal | Holder (write): Same user. POST /api/permission-groups/{id}/users | 201 or 200, success | integration (curl) |
| TC-08 | Normal | Holder (approve): User is decrypt_approver and has pending-approvals with approve. GET /api/search-history/pending | 200, success | integration (curl) |
| TC-09 | Normal | Holder (approve): Same user. POST /api/search-history/{id}/approve | 200 or 4xx per business | integration (curl) |
| TC-10 | Exception | Non-holder (no screen): User has no user-management, no user-permission-hierarchy. POST /api/permission-groups | 403, FORBIDDEN | integration (curl) |
| TC-11 | Exception | Non-holder (screen, no write): User has user-permission-hierarchy with write=false (explicit in DB). POST /api/permission-groups | 403, FUNCTION_NOT_ALLOWED | integration (curl) |
| TC-12 | Exception | Non-holder (screen, no write): Same user. GET /api/permission-groups | 200 (read allowed) | integration (curl) |
| TC-13 | Exception | Non-holder (approve): User has search-history/pending-approvals but is not decrypt_approver and not is_system_admin. GET /api/search-history/pending | 403, FUNCTION_NOT_ALLOWED | integration (curl) |
| TC-14 | Exception | Non-holder (approve): Same user. POST /api/search-history/{id}/approve | 403, FUNCTION_NOT_ALLOWED | integration (curl) |
| TC-15 | Edge | User has pending-approvals screen but approve=false in DB (explicit deny). GET /api/search-history/pending | 403, FUNCTION_NOT_ALLOWED | integration (curl) |

### Test scenarios

#### Scenario 1: System admin bypass
1. Log in as user with is_system_admin=true.
2. Call POST /api/permission-groups, PUT /api/permission-groups/{id}, GET /api/search-history/pending, POST /api/search-history/{id}/approve.
3. All return 2xx (or 4xx only for business reasons, e.g. NOT_FOUND), never 403 FORBIDDEN or FUNCTION_NOT_ALLOWED.

#### Scenario 2: Write holder success
1. Create permission group with user-permission-hierarchy, write=true (or omitted so derived true). Assign test user to that group.
2. Log in as that user. Call POST /api/permission-groups, PUT, POST .../users, DELETE .../users/{userId}.
3. Write operations return 201/200; GET /api/permission-groups returns 200.

#### Scenario 3: Write non-holder denial
1. User has user-permission-hierarchy but permission_group_screen.write=false for that screen (use Test data SQL).
2. Log in as that user. POST /api/permission-groups → 403 FUNCTION_NOT_ALLOWED. GET /api/permission-groups → 200.

#### Scenario 4: Approve holder success
1. User is in decrypt_approver and has pending-approvals (and approve=true or derived). Log in.
2. GET /api/search-history/pending → 200. POST /api/search-history/{id}/approve → 200 or business 4xx.

#### Scenario 5: Approve non-holder denial
1. User has pending-approvals screen but is not decrypt_approver (and not is_system_admin). Log in.
2. GET /api/search-history/pending → 403 FUNCTION_NOT_ALLOWED. POST .../approve → 403 FUNCTION_NOT_ALLOWED.

### Test data

- **System admin user**: One test user with is_system_admin=true (e.g. from init-data or DB).
- **Write holder**: User assigned to a permission group that has user-permission-hierarchy (and write=true or null). Ensure at least one such group and user exist.
- **Write non-holder**: User with user-permission-hierarchy screen but write=false. Use SQL below to set write=false for that group/screen.
- **Approve holder**: User who is in decrypt_approver and has pending-approvals (approve true or null). Ensure decrypt_approver row and permission group with pending-approvals exist.
- **Approve non-holder**: User with pending-approvals (or search-history) screen but not in decrypt_approver and not is_system_admin.
- **Explicit approve=false**: One permission group with pending-approvals and approve=false; user in that group and in decrypt_approver → still must be denied approve APIs (explicit deny overrides).

**Executable SQL for explicit write=false (TC-11, TC-12):**

```sql
-- Set write=false for user-permission-hierarchy for a specific permission group.
-- Replace <permission_group_id> with the ID of the group used for "write non-holder" user.
UPDATE permission_group_screen
SET write = false
WHERE permission_group_id = <permission_group_id>
  AND screen_id = 'user-permission-hierarchy';
```

**Executable SQL for explicit approve=false (TC-15):**

```sql
-- Set approve=false for pending-approvals for a specific permission group.
UPDATE permission_group_screen
SET approve = false
WHERE permission_group_id = <permission_group_id>
  AND screen_id = 'pending-approvals';
```

### Test environment

- Frontend: http://localhost:3001 (optional for UI checks)
- Backend: http://localhost:9200
- Database: PostgreSQL (per docs/contract.md)

### 3.5 Browser automation verification (optional)

Applicable TCs: Any TC that requires UI (e.g. confirm write button enabled/disabled for holder vs non-holder). For this requirement, **API-level curl is primary**; browser TCs can be added later if needed.

- Procedure: Login → navigate to user-permission-hierarchy or pending-approvals → snapshot → verify button visibility/disabled state per role.
- Reference: docs/workflow/BROWSER-AUTOMATION-VERIFICATION-POLICY.md (if present).

---

## 4. Checklist

### Frontend verification
- [ ] N/A (no frontend change)

### Backend verification
- [ ] All §3 TCs executed (curl)
- [ ] 403 responses show correct code (FORBIDDEN vs FUNCTION_NOT_ALLOWED)
- [ ] System admin bypass confirmed

### Integration
- [ ] Holder success for write and approve
- [ ] Non-holder denial for write and approve
- [ ] Explicit deny (write=false, approve=false) verified

### Documentation
- [ ] Requirement doc §1–§3 complete
- [ ] §5 filled after test run

---

## 5. Test results

### Test run date
- [ ] _To be filled by QA_

### Test results

#### Frontend
N/A.

#### Backend
- [ ] _Pass / Fail_
- [ ] _Result description_

**Commands:**

Structure: (1) Login block — one curl -c &lt;role&gt;.txt per role (system_admin, write_holder, write_nonholder, approve_holder, approve_nonholder). (2) TC block — one curl -b &lt;role&gt;.txt -w "\nHTTP %{http_code}\n" per TC-ID. (3) Outcome checklist per TC-ID.

```bash
# ===== Login =====
# System admin
# curl -s -X POST -H "Content-Type: application/json" \
#   -d '{"userId":"<sysadmin_user>","password":"<pw>"}' \
#   -c sysadmin.txt http://localhost:9200/api/auth/login

# Write holder
# curl -s -X POST -H "Content-Type: application/json" \
#   -d '{"userId":"<write_holder_user>","password":"<pw>"}' \
#   -c write_holder.txt http://localhost:9200/api/auth/login

# Write non-holder (user-permission-hierarchy with write=false)
# curl -s -X POST -H "Content-Type: application/json" \
#   -d '{"userId":"<write_nonholder_user>","password":"<pw>"}' \
#   -c write_nonholder.txt http://localhost:9200/api/auth/login

# Approve holder (decrypt_approver + pending-approvals)
# curl -s -X POST -H "Content-Type: application/json" \
#   -d '{"userId":"<approve_holder_user>","password":"<pw>"}' \
#   -c approve_holder.txt http://localhost:9200/api/auth/login

# Approve non-holder (pending-approvals screen but not decrypt_approver)
# curl -s -X POST -H "Content-Type: application/json" \
#   -d '{"userId":"<approve_nonholder_user>","password":"<pw>"}' \
#   -c approve_nonholder.txt http://localhost:9200/api/auth/login

# ===== TC-01: System admin POST /api/permission-groups =====
# curl -s -w "\nHTTP %{http_code}\n" -X POST -H "Content-Type: application/json" \
#   -d '{"code":"tc01-g","name":"TC01 Group","allowedScreens":[{"screenId":"user-permission-hierarchy"}]}' \
#   -b sysadmin.txt http://localhost:9200/api/permission-groups
# Expected: 201, success

# ===== TC-02: System admin PUT /api/permission-groups/{id} =====
# curl -s -w "\nHTTP %{http_code}\n" -X PUT -H "Content-Type: application/json" \
#   -d '{"name":"TC01 Group Updated"}' \
#   -b sysadmin.txt http://localhost:9200/api/permission-groups/<id>
# Expected: 200, success

# ===== TC-03: System admin GET /api/search-history/pending =====
# curl -s -w "\nHTTP %{http_code}\n" -b sysadmin.txt "http://localhost:9200/api/search-history/pending"
# Expected: 200, success

# ===== TC-04: System admin POST /api/search-history/{id}/approve =====
# curl -s -w "\nHTTP %{http_code}\n" -X POST -b sysadmin.txt "http://localhost:9200/api/search-history/<id>/approve"
# Expected: 200 or 404, not 403

# ===== TC-05: Write holder POST /api/permission-groups =====
# curl -s -w "\nHTTP %{http_code}\n" -X POST -H "Content-Type: application/json" \
#   -d '{"code":"tc05-g","name":"TC05 Group","allowedScreens":[{"screenId":"user-permission-hierarchy"}]}' \
#   -b write_holder.txt http://localhost:9200/api/permission-groups
# Expected: 201, success

# ===== TC-06: Write holder PUT /api/permission-groups/{id} =====
# curl -s -w "\nHTTP %{http_code}\n" -X PUT -H "Content-Type: application/json" \
#   -d '{"name":"TC05 Group Updated"}' \
#   -b write_holder.txt http://localhost:9200/api/permission-groups/<id>
# Expected: 200, success

# ===== TC-07: Write holder POST /api/permission-groups/{id}/users =====
# curl -s -w "\nHTTP %{http_code}\n" -X POST -H "Content-Type: application/json" \
#   -d '{"userId":"<some_user>"}' \
#   -b write_holder.txt http://localhost:9200/api/permission-groups/<id>/users
# Expected: 201 or 200, success

# ===== TC-08: Approve holder GET /api/search-history/pending =====
# curl -s -w "\nHTTP %{http_code}\n" -b approve_holder.txt "http://localhost:9200/api/search-history/pending"
# Expected: 200, success

# ===== TC-09: Approve holder POST /api/search-history/{id}/approve =====
# curl -s -w "\nHTTP %{http_code}\n" -X POST -b approve_holder.txt "http://localhost:9200/api/search-history/<id>/approve"
# Expected: 200 or 404, not 403

# ===== TC-10: Non-holder (no screen) POST /api/permission-groups =====
# Use user with only e.g. main or activity-log; no user-management, no user-permission-hierarchy.
# curl -s -w "\nHTTP %{http_code}\n" -X POST -H "Content-Type: application/json" \
#   -d '{"code":"x","name":"X","allowedScreens":[{"screenId":"main"}]}' \
#   -b no_screen_user.txt http://localhost:9200/api/permission-groups
# Expected: 403, FORBIDDEN

# ===== TC-11: Non-holder (write=false) POST /api/permission-groups =====
# curl -s -w "\nHTTP %{http_code}\n" -X POST -H "Content-Type: application/json" \
#   -d '{"code":"tc11-g","name":"TC11","allowedScreens":[{"screenId":"user-permission-hierarchy","write":false}]}' \
#   -b write_nonholder.txt http://localhost:9200/api/permission-groups
# Expected: 403, FUNCTION_NOT_ALLOWED

# ===== TC-12: Same user GET /api/permission-groups (read allowed) =====
# curl -s -w "\nHTTP %{http_code}\n" -b write_nonholder.txt http://localhost:9200/api/permission-groups
# Expected: 200, success

# ===== TC-13: Approve non-holder GET /api/search-history/pending =====
# curl -s -w "\nHTTP %{http_code}\n" -b approve_nonholder.txt "http://localhost:9200/api/search-history/pending"
# Expected: 403, FUNCTION_NOT_ALLOWED

# ===== TC-14: Approve non-holder POST /api/search-history/{id}/approve =====
# curl -s -w "\nHTTP %{http_code}\n" -X POST -b approve_nonholder.txt "http://localhost:9200/api/search-history/1/approve"
# Expected: 403, FUNCTION_NOT_ALLOWED

# ===== TC-15: Explicit approve=false GET /api/search-history/pending =====
# User: decrypt_approver but permission group has pending-approvals with approve=false.
# curl -s -w "\nHTTP %{http_code}\n" -b explicit_approve_deny.txt "http://localhost:9200/api/search-history/pending"
# Expected: 403, FUNCTION_NOT_ALLOWED
```

**Outcome:**
- [ ] TC-01: 201
- [ ] TC-02: 200
- [ ] TC-03: 200
- [ ] TC-04: 200 or 404
- [ ] TC-05: 201
- [ ] TC-06: 200
- [ ] TC-07: 201 or 200
- [ ] TC-08: 200
- [ ] TC-09: 200 or 404
- [ ] TC-10: 403 FORBIDDEN
- [ ] TC-11: 403 FUNCTION_NOT_ALLOWED
- [ ] TC-12: 200
- [ ] TC-13: 403 FUNCTION_NOT_ALLOWED
- [ ] TC-14: 403 FUNCTION_NOT_ALLOWED
- [ ] TC-15: 403 FUNCTION_NOT_ALLOWED

### Issues found and resolution

_To be filled when issues occur._

### Next steps

1. QA runs all curl TCs and records results in §5.
2. If any TC fails, create bugfix child requirement and fix; re-run until pass.
3. Add §7 Final version (Korean) after verification complete.

---

## 6. Error remedy result (cause and action) — for error/bug fix requirements only

N/A (verification requirement).

---

## 7. Final version (Korean) — add after all verification is complete

_To be added after QA verification._

### 요건 요약 (한글)
- **요건 설명**: 권한 그룹에서 수정/승인 등 기능을 허용한 경우, 권한 보유자가 해당 기능을 정상 사용할 수 있는지, 권한 미보유자가 해당 기능을 사용할 수 없는지 검증한다.
- **기대 결과**: 보유자 → API 성공, 미보유자 → 403 (FORBIDDEN 또는 FUNCTION_NOT_ALLOWED).
- **검증 결과**: _(§5 요약)_

---

**Author**: (main agent, new analysis)
**Date**: 2025-03-04
**Status**: In progress
