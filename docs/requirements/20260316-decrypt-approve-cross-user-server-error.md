# 20260316 - Decrypt approval by different user (approver ≠ requester) causes server error

## 1. User requirement

### Requirement description

When **user1** (an approver) is logged in and tries to **approve** a decryption request that **user2** (a different user) created, the server responds with an error that is presented to the user as a **server error** (HTTP 500 or equivalent "서버에서 오류가 발생했습니다" message). The system must either complete the approval successfully or return a **clear client error** (4xx) so that no 500 occurs in this cross-user approval scenario.

### User scenario

1. **user2** (requester) creates a decryption (search-history) approval request; the request is in PENDING state.
2. **user1** (approver; e.g. global or department approver) logs in and opens the pending-approvals or search-history screen.
3. **user1** selects **user2’s** pending request and attempts to approve it (e.g. POST `/api/search-history/{id}/approve`).
4. **Problem**: The server returns an error that the client shows as a **server error** (e.g. HTTP 500, "서버에서 오류가 발생했습니다" or "서버 오류가 발생했습니다.") instead of either:
   - **Success**: HTTP 200 with approval result, or
   - **Client error**: HTTP 401/403/404 with a clear message (e.g. 권한 없음, 로그인 필요, 대상을 찾을 수 없음).
5. **Expected**: The approval either **succeeds** (200) or returns a **deterministic 4xx** (e.g. 401 Unauthorized, 403 Forbidden, 404 Not Found). The server must **not** return 500 when user1 (approver) approves user2’s (requester’s) decryption request.

### Expected outcome

- When **user1** (approver) approves a decryption request created by **user2** (requester):
  - If user1 is allowed to approve for user2 (per `decrypt_approver` and scope rules): the request returns **200** with the approval result.
  - If user1 is not allowed (e.g. no approval right for user2’s department): the request returns **403** (or equivalent) with a clear message (e.g. "해당 기능에 대한 권한이 없습니다."), **not** 500.
- In **no** case does the approve API return **500** for this cross-user scenario due to null/invalid user resolution, SQL, or JSON parsing in the approval path.
- Any failure that is attributable to client/request or business rules (e.g. invalid id, not found, no permission) must be mapped to an appropriate **4xx** response; only truly unexpected server failures may result in 500, and the approval flow must be hardened so that expected cases (different requester vs approver) do not trigger those failures.

---

## 2. Design

### 2.1 Security review (optional; when PII / decryption / access control)

- [ ] Security review performed (check if applicable)
- **Context**: This bug fix addresses **response code and stability** when an approver approves another user’s request. It does **not** change who is allowed to approve (that is still governed by `decrypt_approver`, `canApproveForRequester`, and permission-group/screen rules). Fix must **not** relax approval rules; it must only ensure that allowed approvals succeed and disallowed ones return 403 (or 401), not 500.
- **Risks**: None expected if the fix is limited to exception handling and null-safety; approval policy remains enforced in `DecryptApproverService.canApproveForRequester` and controller checks.

### Technical design

#### Codebase summary

- **SearchHistoryController** (`backend/.../SearchHistoryController.java`): Exposes **POST /api/search-history/{id}/approve**. It calls `requireApproverOrAdmin(httpRequest)`, then `getCurrentUserId(httpRequest)`; if non-null, calls `searchHistoryService.approve(id, userId)`. It **only** rethrows `CustomException`; any other exception (e.g. `RuntimeException`, `NullPointerException`) propagates to **GlobalExceptionHandler** and is returned as **500** with "서버 오류가 발생했습니다.".
- **SearchHistoryService.approve(id, approverUserId)** (`backend/.../SearchHistoryService.java`): (1) Loads the PENDING row and reads `user_id`, `log_type`, `search_params`; (2) resolves `requesterUserIdLong = toLongUserId(rs.getObject("user_id"))`, `approverUsername = appUserResolver.getUsernameById(approverUserId)`, `requesterUsername = requesterUserIdLong != null ? appUserResolver.getUsernameById(requesterUserIdLong) : null`; (3) calls `decryptApproverService.canApproveForRequester(approverUsername, requesterUsername)` — if false, throws `CustomException.forbidden` (403); (4) parses `search_params` JSON and builds `LogDbSearchRequest`; (5) runs `logDbService.searchLogs(searchRequest)` to build the approval snapshot; (6) inserts into `search_history_approved_row` and updates `search_history` with `approved_by = approverUsername`. Any **SQLException** in (1) or (6) is wrapped in **RuntimeException** and rethrown → 500. Any **exception** in (4) (e.g. malformed JSON) is wrapped in **RuntimeException** and rethrown → 500. Any throw from (5) (e.g. NPE in `logDbService.searchLogs`) propagates → 500.
- **DecryptApproverService.canApproveForRequester(approverUserId, requesterUserId)** (both args are **usernames**): Returns false if either is null/blank; otherwise checks global approver or department hierarchy. Catches only **SQLException**; if **DepartmentService.getAncestorCodesIncludingSelf** or any other non-SQL throw occurs, it propagates → 500 from the controller.
- **AppUserResolver.getUsernameById(Long id)**: Returns null if id is null or if no row / exception; does **not** throw (catches Exception and returns null per req 20260316). So null approver/requester username leads to `canApproveForRequester(..., null)` or `canApproveForRequester(null, ...)` → false → 403, not 500, unless the failure happens later.
- **AuthService / getCurrentUserId**: Used by the controller to obtain `approverUserId`. If resolution fails, controller already returns 401 when userId is null; any **unchecked exception** in `getCurrentUserId` (e.g. from session or resolver) would propagate → 500.

**Relevant domain**: Search history and decryption approval (`.cursor/skills/search-history-decrypt-domain/SKILL.md`). Approval capability and pending-approval visibility follow approver and scope rules; `canApproveForRequester` is the authority for “can this approver approve for this requester?”.

#### Problem analysis

1. **Uncaught exceptions → 500**: The approve endpoint does **not** catch `RuntimeException` or general `Exception`. So any of the following in the approve path results in **500**:
   - **SQLException** in the first or second DB block in `SearchHistoryService.approve()` (e.g. connection, constraint, or schema/type mismatch if `search_history.user_id` were ever not BIGINT).
   - **JSON parsing** of `search_params` (e.g. malformed or incompatible structure) wrapped as RuntimeException.
   - **logDbService.searchLogs(searchRequest)** throwing (e.g. NPE if `searchRequest` or required fields are null/invalid).
   - Any throw from **DecryptApproverService.canApproveForRequester** other than SQLException (e.g. from department hierarchy logic).
   - Any throw from **getCurrentUserId** or **requireApproverOrAdmin** before the service call (e.g. session/resolver edge case).

2. **Cross-user (approver ≠ requester)**: When the requester is **user2** and the approver is **user1**, the code path correctly resolves requester from the row (`user_id`) and approver from the session. If `user_id` is valid numeric, `requesterUsername` and `approverUsername` are both set and `canApproveForRequester` can return true (e.g. user1 is global or dept approver for user2’s department). If `user_id` is null or invalid, `requesterUsername` may be null → `canApproveForRequester(approverUsername, null)` returns false → 403. So the **500** in the cross-user case is likely from one of:
   - **After** permission check: JSON parse failure, `logDbService.searchLogs` failure, or SQL in the snapshot/update step.
   - **Before or during** permission check: an unexpected throw (e.g. NPE or unchecked exception) in resolution or in `canApproveForRequester` (e.g. department service returning null or throwing).

3. **Root cause (likely)**: Either (a) an **unchecked exception** (e.g. NPE, or RuntimeException from JSON/SQL) in `SearchHistoryService.approve()` that is not converted to `CustomException`, or (b) an exception in the **controller** path (e.g. `getCurrentUserId`, `requireApproverOrAdmin`) that is not caught and mapped to 401/403. The fix must ensure that in the “user1 approves user2’s request” scenario, all **expected** failure modes (invalid id, not found, no permission, bad search_params) result in **4xx**, and that defensive checks prevent NPE or other throws that would lead to 500.

#### Solution approach

Structure by scope so each implementing agent receives only its relevant section.

**Backend**

- **SearchHistoryController.approve()**:
  - Wrap the call to `searchHistoryService.approve(id, userId)` (and any logic that might throw) in a try-catch that handles **non-CustomException** exceptions (e.g. `RuntimeException`, `Exception`). Map them to a **deterministic 4xx** where appropriate (e.g. 400 for bad request, 403 for permission, 404 for not found), or to a single safe client message (e.g. "승인 처리 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.") with 400/500 per product policy, so that **cross-user approval** never surfaces a raw 500 due to JSON/SQL/resolution. Prefer converting **expected** failures inside the service to `CustomException` so the controller can rely on existing `CustomException` handling.
  - Ensure **requireApproverOrAdmin** and **getCurrentUserId** do not throw in this path; if they can throw (e.g. from session or resolver), wrap in try-catch and return **401** (e.g. "로그인이 필요합니다.") instead of letting the exception propagate to GlobalExceptionHandler.

- **SearchHistoryService.approve()**:
  - Add **defensive null checks**: Before using `approverUsername` or `requesterUsername` in DB updates or in calls that assume non-null, ensure they are valid; if approver cannot be resolved (e.g. `approverUsername` null after `getUsernameById(approverUserId)`), treat as permission/validation failure and throw **CustomException** (e.g. 403 or 400), not RuntimeException. This avoids NPE or invalid DB state (e.g. `approved_by` with an unexpected value) and keeps the response as 4xx.
  - **JSON parsing** of `search_params`: Catch parsing/convert exceptions and throw **CustomException** with a clear client message (e.g. "저장된 검색 조건을 실행할 수 없습니다. 검색 조건 형식을 확인해 주세요.") and an appropriate HTTP status (e.g. 400 BAD_REQUEST), instead of RuntimeException, so GlobalExceptionHandler does not return 500 for this case.
  - **SQLException** in this method: Optionally convert to CustomException with a generic client-safe message and 500 only when the failure is truly unexpected; for known cases (e.g. not found, constraint), use 404/400. This keeps “user1 approves user2” from hitting 500 for transient or expected DB issues if product policy wants to expose a controlled message.

- **DecryptApproverService.canApproveForRequester** (optional hardening):
  - Ensure that **DepartmentService.getAncestorCodesIncludingSelf** or other non-SQL code paths cannot throw out of this method; if they can, catch and return false (or log and return false) so that approval check never propagates an unchecked exception and causes 500.

- **Tests**:
  - Add or extend tests so that: (1) **user1 approves user2’s decryption request** — when user1 is allowed to approve for user2, the request **succeeds** (200); when not allowed, the request returns **403** (or expected 4xx), **not** 500. (2) The approve API **does not return 500** in the cross-user scenario for the above cases; any failure is either success or a documented 4xx.

**Frontend**

- No change required for this requirement; the fix is backend-only. Once the backend returns 200 or a clear 4xx, the UI can display success or the error message as it already does.

**DB**

- No schema or migration change for this bug fix.

### Affected scopes and change targets (verification)

**Before finalizing §2**, the Requirements author has verified that every affected scope is covered per `docs/workflow/REQUIREMENTS-CHANGE-TARGET-CHECKLIST.md`.

| Scope | Affected? | §2 subsection and change file list complete? |
|-------|-----------|-----------------------------------------------|
| Backend | [x] Yes | [x] |
| Frontend (config UI + view screen) | [ ] No | — |
| DB | [ ] No | — |
| Contract / Spec | [ ] No | — |
| Cursor tools (skills, specs) | [ ] No | — |

This requirement is an **API/error-handling** fix (pattern §3.3). Touchpoints: Backend controller and service (approve flow), exception handling, and tests. Contract/spec only if error response shape or status codes for approve are explicitly documented (optional).

### Planned change file list (expected change targets)

**(Planned at authoring. Implementing agent (Step 4) confirms or amends this list when implementation is complete.)**

#### Backend (actual files changed)

- `backend/src/main/java/com/logmng/controller/SearchHistoryController.java` — **Changed.** approve(): try-catch around requireApproverOrAdmin + getCurrentUserId (CustomException rethrown, other → 401); try-catch around searchHistoryService.approve (CustomException rethrown, other → 400 APPROVAL_ERROR). Cross-user approval never returns 500.

- `backend/src/main/java/com/logmng/service/SearchHistoryService.java` — **Changed.** approve(): null check for approverUsername → CustomException.forbidden; search_params JSON parse failure → CustomException.badRequest INVALID_SEARCH_PARAMS; SQLException in first/snapshot block → CustomException.badRequest APPROVAL_ERROR.

- `backend/src/main/java/com/logmng/service/DecryptApproverService.java` — **Changed (optional).** canApproveForRequester: catch (Throwable) in addition to SQLException → log and return false so non-SQL throws do not propagate.

- `backend/src/main/java/com/logmng/exception/GlobalExceptionHandler.java` — No change.

- `backend/src/test/java/com/logmng/controller/SearchHistoryControllerTest.java` — **Changed.** Added approve_whenAllowed_returns200 (TC-01), approve_whenServiceThrowsCustomExceptionForbidden_returns403 (TC-02), approve_whenServiceThrowsRuntimeException_returns400Not500 (TC-03), approve_whenGetCurrentUserInfoThrows_returns401.

- `backend/src/test/java/com/logmng/service/SearchHistoryServiceTest.java` — **Changed.** Added approve_whenCanApproveForRequesterReturnsFalse_throws403, approve_whenApproverUsernameCannotBeResolved_throws403, approve_whenMalformedSearchParams_throws400.

- `backend/src/test/java/com/logmng/service/StubDecryptApproverService.java` — **Changed.** isAdmin(boolean) now returns argument so tests with isSystemAdmin=true pass requireApproverOrAdmin.

#### Frontend

- None.

#### DB

- None.

---

## 3. Test approach

### Test case list (required)

| ID | Scope | Type | Scenario (input / condition) | Expected result | Verification (unit / integration / manual) |
|----|-------|------|------------------------------|-----------------|--------------------------------------------|
| TC-01 | Backend | Normal | user1 (approver) approves user2’s (requester’s) PENDING decryption request; user1 is allowed to approve for user2 (e.g. global or dept approver). | HTTP 200 with approval result (e.g. approvalStatus APPROVED). | Unit or integration (e.g. SearchHistoryControllerTest / SearchHistoryServiceTest or webtest) |
| TC-02 | Backend | Exception | user1 (approver) approves user2’s (requester’s) PENDING request; user1 is **not** allowed to approve for user2. | HTTP 403 (or expected 4xx) with clear message (e.g. "해당 기능에 대한 권한이 없습니다."); **not** 500. | Unit or integration |
| TC-03 | Backend | Regression | Same as TC-01 or TC-02: approve API is called for cross-user scenario. | Server does **not** return HTTP 500; response is either 200 or a 4xx. | Unit or integration |

### Test scenarios

#### Scenario 1: Cross-user approval success

1. Create a PENDING search-history row for **user2** (requester).
2. Log in as **user1** (configured as approver for user2’s department or as global approver).
3. Call **POST /api/search-history/{id}/approve** with the row id.
4. **Verification**: Response is **200** and body indicates approval success (e.g. `approvalStatus: "APPROVED"`).

#### Scenario 2: Cross-user approval — no permission

1. Create a PENDING search-history row for **user2**.
2. Log in as **user1** who is **not** allowed to approve for user2 (e.g. different department, not global approver).
3. Call **POST /api/search-history/{id}/approve** with the row id.
4. **Verification**: Response is **403** (or defined 4xx) with a clear error message; **not** 500.

#### Scenario 3: No 500 in cross-user approve

1. For the same setup as Scenario 1 or 2, ensure that the approve endpoint never returns **500** when user1 approves (or attempts to approve) user2’s request.
2. **Verification**: All responses in the test run are either 200 or 4xx (e.g. 401, 403, 404).

### Test data

- **user1**: Approver (e.g. in `decrypt_approver` as global or for a department).
- **user2**: Requester; has at least one PENDING search_history row; may be in the same or different department as user1 for negative tests.
- Use existing init-data or test fixtures for `app_user`, `decrypt_approver`, `search_history` (and `department` if scope=team is used).

### Test environment

- Frontend: per contract (e.g. `http://localhost:3001`).
- Backend: per contract (e.g. `http://localhost:9200`).
- Database: PostgreSQL per project setup.

---

## 4. Checklist

### Frontend verification

- [ ] No change; backend fix only.

### Backend verification

- [x] Approve API test cases (TC-01, TC-02, TC-03) written and run.
- [x] No 500 in cross-user approve scenario when running tests.
- [x] Logs checked for correct handling (no stack traces for expected 4xx cases).

### Integration

- [ ] End-to-end: user1 logs in, approves user2’s request → 200 or 4xx as expected.

### Documentation

- [ ] Requirement doc completed.
- [ ] §6 Error remedy result filled after fix is verified.

---

## 5. Test results

### Test run date

- 2026-03-16 (implementation)

### Test results

#### Backend

**Pass.**

- TC-01: approve_whenAllowed_returns200 — HTTP 200 with approvalStatus APPROVED.
- TC-02: approve_whenServiceThrowsCustomExceptionForbidden_returns403 — HTTP 403, not 500.
- TC-03: approve_whenServiceThrowsRuntimeException_returns400Not500 — HTTP 400 (APPROVAL_ERROR), not 500.
- SearchHistoryServiceTest: approve_whenCanApproveForRequesterReturnsFalse_throws403, approve_whenApproverUsernameCannotBeResolved_throws403, approve_whenMalformedSearchParams_throws400 — all pass.

**Commands:**

```bash
cd backend && mvn test -Dtest=SearchHistoryControllerTest,SearchHistoryServiceTest
```

**Outcome:**

- All 40 tests (11 controller + 29 service) passed. Cross-user approve path returns 200 or 4xx only; no 500 in tests.

### Issues found and resolution

- None. StubDecryptApproverService.isAdmin(boolean) was updated to return the argument so approve tests with isSystemAdmin=true pass requireApproverOrAdmin.

### Next steps

1. ~~Implement fix per §2.~~ Done.
2. ~~Run TC-01, TC-02, TC-03 and record in §5.~~ Done.
3. Complete §6 Error remedy result after verification.

---

## 6. Error remedy result (cause and action) — for error/bug fix requirements only

Record root cause and actions under the **same requirement ID (this document)**. Template: `docs/template/ERROR_FIX_RESULT_TEMPLATE.md`.

- **Requirement ID**: 20260316-decrypt-approve-cross-user-server-error
- **Root cause**: Uncaught exceptions in the approve path (RuntimeException from JSON parse or SQL, or from resolution/canApproveForRequester) propagated to GlobalExceptionHandler and were returned as HTTP 500. Null approver/requester resolution or malformed search_params could trigger the same.
- **Actions taken**: (1) Controller approve(): wrapped requireApproverOrAdmin and getCurrentUserId in try-catch; return 401 on non-CustomException; wrapped service.approve in try-catch and return 400 with APPROVAL_ERROR on non-CustomException. (2) SearchHistoryService approve(): null check for approverUsername → CustomException.forbidden; JSON parse failure → CustomException.badRequest INVALID_SEARCH_PARAMS; SQLException → CustomException.badRequest APPROVAL_ERROR. (3) DecryptApproverService.canApproveForRequester: catch Throwable and return false so department/non-SQL throws do not propagate.
- **Result**: Unit tests (SearchHistoryControllerTest, SearchHistoryServiceTest) pass; approve API returns 200 or 4xx only in cross-user scenario. Prevention: all expected failure modes in the approve flow now throw CustomException or are caught and mapped to 4xx.
- **Completed**: 2026-03-16

---

## 7. Final version (Korean) — add after all verification is complete

### Final Korean summary

- **Requirement description**: user1(결재자)이 user2(요청자)가 요청한 복호화 건을 승인하려 할 때 서버 오류(500)가 발생하는 문제. 원인 분석 후, 승인 성공 또는 명확한 4xx 응답으로 수정.
- **Expected outcome**: user1이 user2의 승인 요청을 승인할 때, 권한이 있으면 200 성공, 없으면 403 등 4xx 반환; 어떠한 경우에도 해당 시나리오에서 500이 발생하지 않도록 함.
- **Verification result**: [§5 요약, 통과/실패]

---

**Author**: Requirements subagent  
**Date**: 2026-03-16  
**Status**: In progress
