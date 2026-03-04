# 20250304 - Permission function holder vs non-holder verification

**Superseded by**: `docs/requirements/20250304-permission-group-function-verification.md` (single source of truth for permission group function verification).

---

## 1. User requirement

### Requirement description

When a permission group grants **function-level permissions** (e.g. **write** for management screens such as permission-group create/update/delete and user-group assignment, or **approve** for search-history decryption approval/rejection), the system must ensure:

1. **Holder**: Every user who **holds** that function (granted via their permission group with write/approve enabled) can use the function normally — the corresponding APIs return success (2xx).
2. **Non-holder**: Every user who **does not hold** that function (e.g. has the screen with read-only, or has no screen at all) **cannot** exercise it — the corresponding APIs return 403 with the correct error code (FORBIDDEN when the user lacks the screen; FUNCTION_NOT_ALLOWED when the user has the screen but lacks the function).

This requirement is a **verification/review** of the existing implementation: confirm that backend enforcement and error codes behave as above, and document test cases so that holder vs non-holder behaviour can be regression-tested.

### User scenario

1. An administrator configures a permission group with **user-management** (or user-permission-hierarchy) with **write** enabled, and assigns user A to that group.
2. The same or another administrator configures another group with **user-management** with **read only** (write disabled or not granted), and assigns user B to that group.
3. User A (holder of write) performs: create/update/delete permission group, assign/unassign user to group. **Expected**: APIs return 201/200.
4. User B (non-holder of write) attempts the same write operations. **Expected**: APIs return 403 with code `FUNCTION_NOT_ALLOWED`.
5. A user C has no user-management (and no user-permission-hierarchy) screen. User C calls any permission-group or user-management API. **Expected**: 403 with code `FORBIDDEN`.
6. For **approve**: A user D is assigned a group that has **pending-approvals** (or search-history) with **approve** enabled, and D is designated as decrypt_approver. User D calls GET /api/search-history/pending, POST approve, POST reject. **Expected**: 200/201.
7. A user E has pending-approvals screen with **approve** disabled (or is not decrypt_approver). User E attempts the same approve APIs. **Expected**: 403 with code `FUNCTION_NOT_ALLOWED`.
8. **Problem**: Without explicit verification, it is unclear whether every write/approve API consistently enforces holder vs non-holder and returns the correct error code; regression could allow non-holders to perform privileged actions.

### Expected outcome

- A single requirement document that states the holder/non-holder rules and the expected HTTP and error codes.
- A test plan (§3) with concrete test cases: system-admin bypass, holder success, non-holder (no screen) FORBIDDEN, non-holder (screen but no function) FUNCTION_NOT_ALLOWED, and explicit deny (write=false / approve=false in DB).
- Verification that the current backend implements these rules correctly; any gap or bug is recorded and fixed under this requirement.

---

## 2. Design

### 2.1 Security review (optional; when PII / decryption / access control)

This requirement concerns **access control** (who can perform write/approve). No new data exposure; verification only.

- [ ] Security review performed (check if applicable) — optional for verification-only requirement.
- **Risks**: Misconfiguration (e.g. write=false not enforced) could allow privilege escalation.
- **Acceptance**: All write-gated and approve-gated APIs must deny non-holders with 403 and the correct code; system admin bypass must remain.

### Technical design

#### Problem analysis

1. **Multiple layers**: Access is enforced in two layers — (1) screen access (interceptor or controller) → FORBIDDEN; (2) function (write/approve) → FUNCTION_NOT_ALLOWED. Test cases must cover both.
2. **Derivation rules**: For management screens, `write` is derived as true when `permission_group_screen.write` is null (read implies write). To test “non-holder of write”, test data must set **explicit** `write=false` in DB (or via API when creating/updating the group).
3. **Approve gate**: Approve requires both (a) decrypt_approver (or is_system_admin) and (b) screenFunctions.approve for search-history or pending-approvals. Non-holder cases: user without decrypt_approver role, or user with approve=false in permission group.
4. **Coverage**: Only some controllers enforce function-level checks. Others are screen-access-only (no write/approve check). The requirement focuses on **APIs that are specified as write-gated or approve-gated** in the contract/spec and in the codebase.

#### Solution approach

**No new feature development** — this is a verification requirement. The approach is:

- **Backend**: Confirm that every write-gated and approve-gated endpoint calls the appropriate check (requireWriteForManagement, requireApproverOrAdmin) and returns 403 with code FUNCTION_NOT_ALLOWED when the check fails. Confirm that screen-level denial returns FORBIDDEN. Document any endpoint that should be gated but is not (gaps).
- **Test plan**: Add test cases that use distinct users (holder, non-holder with screen but no function, non-holder without screen, system admin) and assert HTTP status and error code. Include explicit deny cases (write=false, approve=false) with test data SQL or API setup.

**Write-gated APIs (current implementation):**

- Controller: `PermissionGroupController`. Check: `requireWriteForManagement(request)` (after `requireUserManagementAccess`).
- Logic: `hasWriteForManagementScreens(request)` — true if is_system_admin or (screenFunctions for user-management or user-permission-hierarchy has write=true).
- Endpoints: POST /api/permission-groups, PUT /api/permission-groups/{id}, DELETE /api/permission-groups/{id}, POST /api/permission-groups/{id}/users, DELETE /api/permission-groups/{id}/users/{userId}.
- Denial code: `FUNCTION_NOT_ALLOWED`.

**Approve-gated APIs (current implementation):**

- Controller: `SearchHistoryController`. Check: `requireApproverOrAdmin(request)`.
- Logic: (decrypt_approver OR is_system_admin) AND (hasApproveForSearchHistory(request) — screenFunctions for search-history or pending-approvals with approve=true).
- Endpoints: GET /api/search-history/pending, POST /api/search-history/{id}/approve, POST /api/search-history/{id}/reject.
- Denial code: `FUNCTION_NOT_ALLOWED`.

**Screen-access-only (no function check):**

- GET /api/permission-groups, GET /api/permission-groups/{id}, GET /api/permission-groups/{id}/users, GET /api/users, GET /api/departments, etc. Denial when user lacks the required screen: `FORBIDDEN` (from interceptor or controller).

**Known gaps (no write enforcement where one might expect):**

- UserController: No write-gated mutation endpoints (PUT /api/users/{userId} returns 410 Gone). User assignment is done via PermissionGroupController (write-gated).
- DepartmentController: Only GET endpoints; no POST/PUT/DELETE. Write enforcement deferred until CRUD exists.

### Change file list

**(Tentative. This is a verification requirement; no code change is required unless gaps or bugs are found. Implementing agent confirms.)**

#### Frontend

- None for verification-only. If UI is found to expose write/approve actions to non-holders without backend enforcement, those would be fixed in the relevant components.

#### Backend

- None planned. If a missing check or wrong error code is found, the change would be in the controller (e.g. PermissionGroupController, SearchHistoryController) or AuthService.

### Database changes

None. Test data may require permission groups and users with explicit write=false or approve=false (via existing permission_group_screen table or API).

---

## 3. Test approach

### Test case list (required)

**Completeness checklist** (permission/access-control):

- [x] **System admin bypass TC**: Include at least one TC where is_system_admin user calls write and approve gated APIs → expect success.
- [x] **Explicit deny edge cases**: Include TCs with write=false and approve=false (explicit in DB or via group config) to verify override of derivation (read→write) and approve gate.
- [x] **Error code distinction**: Separate TCs for FORBIDDEN (no screen) vs FUNCTION_NOT_ALLOWED (screen OK, function denied).
- [x] **Known gaps noted**: UserController has no write endpoints; DepartmentController has no write endpoints. Not applicable for holder/non-holder of write for permission-groups and approve.
- [x] **§5 curl commands**: Provide login + per-TC curl commands so QA can execute directly.
- [x] **Test data SQL**: Include SQL or API steps to set write=false / approve=false where derivation applies.

| ID | Type | Scenario (input / condition) | Expected result | Verification |
|----|------|------------------------------|-----------------|--------------|
| TC-01 | Normal | System admin: POST /api/permission-groups (create), PUT /api/permission-groups/{id}, DELETE (or POST/DELETE users). | 201 / 200 | Integration (curl) |
| TC-02 | Normal | System admin: GET /api/search-history/pending, POST approve, POST reject. | 200 / 201 | Integration (curl) |
| TC-03 | Normal | **Holder (write)**: User has user-management or user-permission-hierarchy with write=true (or derived). POST /api/permission-groups, PUT, DELETE, POST/DELETE users. | 201 / 200 | Integration (curl) |
| TC-04 | Normal | **Holder (approve)**: User is decrypt_approver and has pending-approvals (or search-history) with approve=true. GET /pending, POST approve, POST reject. | 200 / 201 | Integration (curl) |
| TC-05 | Exception | **Non-holder (no screen)**: User has neither user-management nor user-permission-hierarchy. GET /api/permission-groups. | 403, code FORBIDDEN | Integration (curl) |
| TC-06 | Exception | **Non-holder (no screen)**: Same user. POST /api/permission-groups. | 403, code FORBIDDEN | Integration (curl) |
| TC-07 | Exception | **Non-holder (screen, no write)**: User has user-management (or user-permission-hierarchy) with **write=false** (explicit in DB). POST /api/permission-groups. | 403, code FUNCTION_NOT_ALLOWED | Integration (curl) |
| TC-08 | Exception | **Non-holder (screen, no write)**: Same user. PUT /api/permission-groups/{id}, DELETE, POST/DELETE users. | 403, code FUNCTION_NOT_ALLOWED | Integration (curl) |
| TC-09 | Exception | **Non-holder (approve)**: User has pending-approvals screen but approve=false (or is not decrypt_approver). GET /api/search-history/pending. | 403, code FUNCTION_NOT_ALLOWED | Integration (curl) |
| TC-10 | Exception | **Non-holder (approve)**: Same. POST /api/search-history/{id}/approve, POST reject. | 403, code FUNCTION_NOT_ALLOWED | Integration (curl) |
| TC-11 | Edge | **Explicit write=false**: Create permission group with user-management with write=false; assign user; login as that user; call POST /api/permission-groups. | 403, code FUNCTION_NOT_ALLOWED | Integration (curl) + test data |
| TC-12 | Edge | **Explicit approve=false**: Group with pending-approvals approve=false; user is decrypt_approver; GET /pending. | 403, code FUNCTION_NOT_ALLOWED (approve denied by group) | Integration (curl) + test data |

### Test scenarios

#### Scenario 1: Write holder and non-holder

1. Create two permission groups: G1 with user-management (write=true or omitted for derivation), G2 with user-management (write=false explicit).
2. Create users U1 (in G1 only), U2 (in G2 only). Ensure neither is system admin.
3. Login as U1; call POST /api/permission-groups with valid body → expect 201.
4. Login as U2; call POST /api/permission-groups with valid body → expect 403, code FUNCTION_NOT_ALLOWED.
5. Login as U1; call PUT /api/permission-groups/{id}, DELETE, POST/DELETE users → expect 200/201.
6. Login as U2; same calls → expect 403, code FUNCTION_NOT_ALLOWED.

#### Scenario 2: Approve holder and non-holder

1. Ensure decrypt_approver is set for a test user D. Create group with pending-approvals and approve=true; assign D.
2. Create user E with pending-approvals and approve=false (or E not in decrypt_approver). Create a pending search-history request (if needed).
3. Login as D; GET /api/search-history/pending → 200; POST approve/reject → 201.
4. Login as E; GET /api/search-history/pending → 403, code FUNCTION_NOT_ALLOWED; POST approve/reject → 403.

#### Scenario 3: No screen (FORBIDDEN)

1. User F has only main (or activity-log, statistics) — no user-management, no user-permission-hierarchy.
2. Login as F; GET /api/permission-groups → 403, code FORBIDDEN; POST /api/permission-groups → 403, code FORBIDDEN.

### Test data

- **Explicit write=false**: When creating or updating a permission group via API, set allowedScreens to include `{ "screenId": "user-management", "read": true, "write": false }`. Or via SQL: insert/update `permission_group_screen` with write=false for that group and screen.
- **Explicit approve=false**: Set allowedScreens to include `{ "screenId": "pending-approvals", "read": true, "approve": false }`, or set approve=false in permission_group_screen.
- **decrypt_approver**: Ensure test users who must act as approvers are present in decrypt_approver table (or equivalent per schema).
- **Users**: At least: one system admin, one write-holder (group with write), one write-non-holder (group with write=false), one user with no management screens, one approve-holder (decrypt_approver + group with approve), one approve-non-holder (approve=false or not decrypt_approver).

### Test environment

- Frontend: http://localhost:3001 (optional for API-only verification)
- Backend: http://localhost:9200
- Database: PostgreSQL (per contract)

### 3.5 Browser automation verification (optional)

Applicable if UI is verified (e.g. write buttons hidden for non-holder). For API-only verification, §3.5 can be skipped. If added later: list TC IDs that can be checked via browser (e.g. TC-07, TC-08: non-holder should not see or should be blocked on create/update/delete group actions).

---

## 4. Checklist

### Frontend verification

- [ ] If UI exposes write/approve: buttons/actions hidden or disabled when screenFunctions.write / approve is false (optional for this verification-only requirement).
- [ ] Error handling: 403 responses with FUNCTION_NOT_ALLOWED or FORBIDDEN shown appropriately.

### Backend verification

- [ ] All write-gated endpoints (PermissionGroupController) call requireWriteForManagement and return 403 FUNCTION_NOT_ALLOWED when check fails.
- [ ] All approve-gated endpoints (SearchHistoryController) call requireApproverOrAdmin and return 403 FUNCTION_NOT_ALLOWED when check fails.
- [ ] Screen-level denial returns 403 FORBIDDEN (interceptor or controller).

### Integration

- [ ] End-to-end: login as holder → write/approve APIs succeed; login as non-holder → 403 with correct code.
- [ ] Edge cases: explicit write=false, approve=false verified.

### Documentation

- [ ] Requirement doc (§1, §2, §3) completed.
- [ ] §5 filled after test execution; §6 if bugs found and fixed.

---

## 5. Test results

### Test run date

- [To be filled]

### Test results

#### Frontend

[Pass / Fail / N/A — API-only verification]

#### Backend

[Pass / Fail]

**Commands:**

(Provide complete curl: login for each role, then one curl per TC. Example pattern below; actual IDs and tokens to be replaced.)

```bash
# Login as system admin
curl -s -X POST http://localhost:9200/api/auth/login -H "Content-Type: application/json" -d '{"username":"admin","password":"..."}' -c cookies.txt

# TC-01: System admin POST /api/permission-groups
curl -s -X POST http://localhost:9200/api/permission-groups -b cookies.txt -H "Content-Type: application/json" -d '{"code":"tg1","name":"Test Group 1","allowedScreens":[]}' -w "\n%{http_code}\n"

# Login as write-holder (user in group with user-management write=true)
curl -s -X POST http://localhost:9200/api/auth/login -H "Content-Type: application/json" -d '{"username":"holder_w","password":"..."}' -c cookies_holder.txt

# TC-03: Holder POST /api/permission-groups
curl -s -X POST http://localhost:9200/api/permission-groups -b cookies_holder.txt -H "Content-Type: application/json" -d '{"code":"tg2","name":"Test Group 2","allowedScreens":[]}' -w "\n%{http_code}\n"

# Login as non-holder (write=false)
curl -s -X POST http://localhost:9200/api/auth/login -H "Content-Type: application/json" -d '{"username":"nonholder_w","password":"..."}' -c cookies_non.txt

# TC-07: Non-holder POST /api/permission-groups → expect 403 and body with code FUNCTION_NOT_ALLOWED
curl -s -X POST http://localhost:9200/api/permission-groups -b cookies_non.txt -H "Content-Type: application/json" -d '{"code":"tg3","name":"Test Group 3","allowedScreens":[]}' -w "\n%{http_code}\n"
```

**Outcome:**

- [To be filled after run]

### Issues found and resolution

- [To be filled if any]

### Next steps

1. Execute §3 test cases (manual or automated).
2. Record results in §5; fix any bugs under this requirement and re-test.
3. Add § Final version (Korean) after verification complete.

---

## 6. Error remedy result (cause and action) — for error/bug fix requirements only

(Use when bugs are found during verification and fixed. Template: REQUIREMENT_TEMPLATE §6.)

- **Requirement ID**: 20250304-permission-function-holder-nonholder-verification
- **Root cause**: —
- **Actions taken**: —
- **Result**: —
- **Completed**: —

---

## 7. Final version (Korean) — add after all verification is complete

(Add after QA verification. See DOCUMENT-LANGUAGE-POLICY §2.3.)

### 요건 요약 (한글)

- **요건 설명**: 권한 그룹에서 수정(write)/승인(approve) 기능을 허용한 경우, 권한 보유자(holder)는 해당 기능을 정상 사용할 수 있고, 권한 미보유자(non-holder)는 해당 기능을 행사할 수 없어야 한다. 이에 대한 검증 요구사항 및 테스트 계획을 정리한 문서.
- **기대 결과**: 보유자 → API 성공(2xx); 미보유자(화면 없음) → 403 FORBIDDEN; 미보유자(화면 있으나 기능 없음) → 403 FUNCTION_NOT_ALLOWED. 시스템 관리자는 모든 검사 우회.
- **검증 결과**: [§5 요약, 검증 완료 후 기입]

---

**Author**: (Agent)
**Date**: 2025-03-04
**Status**: In progress
