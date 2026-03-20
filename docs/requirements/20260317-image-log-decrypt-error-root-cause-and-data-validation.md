# 20260317 - Image log decrypt error: root cause analysis and data validation

**Related**: [20260317-decrypt-execution-user-id-fix-bugfix-1](20260317-decrypt-execution-user-id-fix-bugfix-1.md) (Aspect serialization fix and migration note).

This requirement documents the **root cause analysis** and **data/DB validation** for the image-log decryption failure (user2 request → user1 approval → decrypt execution error). It does not replace the bugfix doc; it clarifies whether the failure is solely from the Aspect layer, from the data/approval path, or both, and specifies tests to cover both.

---

## 1. User requirement

### Requirement description

When an image log decryption flow runs as follows — **user2** requests decryption approval → **user1** approves → the user then **executes decryption** — an error occurs. Logs point to the **Aspect layer** (ActivityLogAspect). The user wants a **deep analysis** to find the **root cause** (not only the immediate exception) and to verify whether **data** could be the problem (e.g. `search_history.user_id` type or value, approval state, requester vs approver vs execution user).

### User scenario

1. **user2** (requester) creates a search and requests decryption approval for image log; the search_history row is PENDING then **APPROVED** by user1.
2. **user1** (approver) approves the request (e.g. POST `/api/search-history/{id}/approve` with snapshot).
3. Someone (either user2 or user1) calls **execute decryption** (e.g. POST `/api/logs/decrypt/java_fw_imglog` with `searchHistoryId`, `guid`, `status`).
4. **Problem**: An error occurs (500 or unexpected 403). Logs show the error in **ActivityLogAspect**.
5. **User expectation**: Decrypt should **succeed** when approval is valid and the execution user is allowed; or a **clear error** (e.g. 403 with a specific code) when data or approval is invalid.

### Expected outcome

- **Root cause** is clearly identified: (a) only Aspect serialization (Servlet type passed to ObjectMapper), (b) only data/approval path (e.g. `search_history.user_id` type or value, or execution user not allowed), or (c) both.
- **Decrypt execution succeeds** when: the request has a valid approved search_history for the **execution user**, the row is in the approved snapshot, and the Aspect does not receive Servlet types for serialization.
- **Clear error** when: approval is invalid, row not in snapshot, or execution user is not allowed — e.g. 403 with `DECRYPTION_NOT_APPROVED` or `ROW_NOT_IN_APPROVED_SNAPSHOT`, not 500.
- **Data/DB**: Document who may execute decrypt (requester vs approver); document that `search_history.user_id` must be BIGINT and store the **requester’s** `app_user.id`; document migration and startup check so operators can fix "승인 미충족" when the column is still VARCHAR/username.

---

## 2. Design

### 2.1 Security review (optional)

- [ ] Security review performed (check if applicable)
- **Context**: Decrypt execution is gated by `isValidApprovalForUser(searchHistoryId, currentUserId)` and `isRowInApprovedSnapshot`. This requirement only clarifies root cause and data rules; it does not relax access control. Execution is allowed only for the **requester** (search_history.user_id = current user id) with APPROVED, non-expired search_history and row in snapshot.

### Technical design

#### 2.2 Decrypt execution flow (codebase summary)

- **DecryptController.decryptRow** (`DecryptController.java`):
  - Signature: `decryptRow(@PathVariable String logType, @RequestBody Map<String, String> request, HttpServletRequest httpRequest)`.
  - Request body is **Map&lt;String, String&gt;** (JSON: searchHistoryId, guid, status). It does **not** contain HttpServletRequest; the Servlet object is a **separate** method parameter.
  - Flow: (1) Auth → 401 if not logged in. (2) Permission hasDecryptForMain → 403 if no decrypt right. (3) Approval isValidApprovalForUser → 403 DECRYPTION_NOT_APPROVED if false. (4) Snapshot isRowInApprovedSnapshot → 403 ROW_NOT_IN_APPROVED_SNAPSHOT if false. (5) logDbService.decryptRow.
- **ActivityLogAspect** (`ActivityLogAspect.java`):
  - Intercepts @ActivityLog methods; for includeParams(), builds a params map from method arguments. Arguments for decryptRow are: [logType, request, httpRequest]. The third argument is **HttpServletRequest**.
  - If any argument is passed to ObjectMapper.writeValueAsString() without sanitization, Jackson can hit RequestFacade (e.g. headerNames → NamesEnumerator) and throw. Bugfix-1 addresses this with: isNonSerializableServletParam and early replacement with placeholder; deepSanitizeForSerialization for non–LogDbSearchRequest values; sanitizeParamsForSerialization before final requestParams JSON.
- **SearchHistoryService.isValidApprovalForUser(Long searchHistoryId, Long userId)**:
  - SQL: `SELECT 1 FROM search_history WHERE id = ? AND user_id = ? AND approval_status = 'APPROVED' AND expires_at > CURRENT_TIMESTAMP LIMIT 1`.
  - Binds: `ps.setLong(1, searchHistoryId)`; `bindUserId(ps, 2, userId)` → `ps.setObject(index, userId)` (Long). So the second predicate is **user_id = current user id**.
  - **Semantics**: Only the row where **search_history.user_id** equals the **current user’s id** is considered valid. So **only the requester** (the user whose id is stored in `search_history.user_id`) can pass this check. The **approver** (e.g. user1) does **not** have a row with `user_id = user1’s id` for a request made by user2; that row has `user_id = user2’s id`. So if **user1** (approver) executes decrypt for user2’s approved request, `isValidApprovalForUser` returns **false** → 403 `DECRYPTION_NOT_APPROVED`. This is **by design** unless the product explicitly allows “approver may execute decrypt on behalf of requester.”
- **SearchHistoryService.isRowInApprovedSnapshot(Long searchHistoryId, String logType, String rowId)**:
  - Queries `search_history_approved_row` for the given search_history_id, log_type, row_id. No user_id check; ownership is already enforced by `isValidApprovalForUser`.
- **search_history.user_id** (schema and migration):
  - Canonical schema (`schema.sql`): `user_id BIGINT NOT NULL` (requester’s `app_user.id`). FK to `app_user(id)` in fresh install or after migration.
  - Legacy DBs may still have `user_id VARCHAR` (e.g. storing username). Migration: `migrate-search-history-user-id-to-bigint.sql` (idempotent). **SearchHistoryUserIdMigrationCheck** (ApplicationRunner) logs WARN at startup if `information_schema.columns` shows `search_history.user_id` is not `bigint`, with migration command and `DB_SETUP_GUIDE.md` reference.

#### 2.3 Root cause analysis

- **(a) Aspect serialization (surface cause)**  
  The **immediate** exception in the Aspect occurs when the Aspect serializes controller parameters. `decryptRow` has `HttpServletRequest httpRequest` as the third argument. If that (or any nested structure containing it) is passed to `ObjectMapper.writeValueAsString()`, Jackson fails (e.g. NamesEnumerator). **Conclusion**: The **surface** cause of a 500 in this flow is the Aspect passing Servlet types to ObjectMapper. **Fix**: Already addressed in [20260317-decrypt-execution-user-id-fix-bugfix-1](20260317-decrypt-execution-user-id-fix-bugfix-1.md) (early replacement, deepSanitizeForSerialization, sanitizeParamsForSerialization).

- **(b) Data/approval path**  
  - **Execution user = approver (user1)**  
    If **user1** (approver) runs decrypt after approving user2’s request: `currentUserId = user1’s id`, but `search_history.user_id = user2’s id`. So `isValidApprovalForUser(searchHistoryId, user1’s id)` returns **false** (no row matches). Controller returns **403 DECRYPTION_NOT_APPROVED**. So the “error” in that case is **not** a bug but **intended**: only the **requester** can execute decrypt with the current contract. If the product wants approver-to-execute, that would be a **contract/design change** (e.g. allow when `currentUserId = search_history.user_id OR currentUserId = approved_by_user_id`).
  - **search_history.user_id type or value**  
    - If `search_history.user_id` is still **VARCHAR** and stores **username** (e.g. "user2"): the code binds **Long** (e.g. 20260002). JDBC/PostgreSQL behavior may not match "user2" to 20260002; the query returns no row → **403 DECRYPTION_NOT_APPROVED** (“복호화 거부(승인 미충족)”). See `backend/DB_SETUP_GUIDE.md` § search_history.user_id 규칙 및 마이그레이션.
    - If `search_history.user_id` is VARCHAR but stores **numeric string** (e.g. "20260002"): driver may match; behavior is DB/driver-dependent. Canonical behavior requires **BIGINT** column and numeric `app_user.id` in `search_history.user_id`.
  - **Approval state / snapshot**  
    If the row is not APPROVED or is expired, or the row id is not in `search_history_approved_row`, the controller returns 403 with the appropriate code; no 500 from business logic.

- **(c) Combination**  
  Both can occur in the same environment: (1) 500 from Aspect if bugfix-1 is not applied; (2) 403 from data path if migration is not applied (user_id VARCHAR/username) or if the execution user is the approver rather than the requester. Applying bugfix-1 removes the 500; applying migration and executing as **requester** removes the data-path 403 for the “승인 미충족” case.

#### 2.4 Data / DB validation subsection

- **search_history.user_id type**: Must be **BIGINT** for the decrypt execution path. Code uses `bindUserId(ps, 2, userId)` with `Long`; canonical schema and `isValidApprovalForUser` expect `user_id = ?` to compare numeric id. If the column is VARCHAR with username values, no row matches → 403.
- **search_history.user_id value**: Must be the **requester’s** `app_user.id` (the user who requested the decryption approval). Approval stores `approved_by_user_id` (approver); execution check uses `user_id` (requester).
- **Who can execute decrypt (contract)**: With the current implementation, **only the requester** (the user whose id equals `search_history.user_id`) can pass `isValidApprovalForUser`. The approver (user1) cannot execute decrypt for a request made by user2 unless the product explicitly extends the rule (e.g. allow approver by changing the validation).
- **Checks**: (1) Apply `migrate-search-history-user-id-to-bigint.sql` when `search_history.user_id` is not BIGINT. (2) Use **SearchHistoryUserIdMigrationCheck** at startup to warn when the column type is not bigint. (3) Document in contract/spec that decrypt execution is allowed only for the requester (and optionally approver if product decides).

#### 2.5 Change target verification (REQUIREMENTS-CHANGE-TARGET-CHECKLIST)

| Scope | Affected? | §2 subsection and change file list complete? |
|-------|-----------|-----------------------------------------------|
| Backend | Yes | §2.2, §2.3; confirm bugfix-1 coverage; optional: clearer 403 message or contract doc |
| Frontend | No | — |
| DB | Yes (validation/migration doc) | §2.4; migration and startup check already in bugfix-1 / DB_SETUP_GUIDE |
| Contract / Spec | Optional | If we document “who may execute decrypt” (requester only) in api-definition or contract |
| Cursor tools | Optional | search-history-decrypt-domain skill already states execution uses numeric user_id and requester |

### Planned change file list (expected change targets)

**(Planned at authoring. Implementing agent (Step 4) confirms or amends this list when implementation is complete.)**

This requirement is **analysis and test-plan focused**. The **code fix** for the 500 is in [20260317-decrypt-execution-user-id-fix-bugfix-1](20260317-decrypt-execution-user-id-fix-bugfix-1.md). If verification shows that bugfix-1 is fully applied and no further code change is needed, the only deliverables are this requirement doc and §3 test cases (and optional contract/skill updates).

#### Actual files changed (Step 4 Backend implementation)

- **backend/src/test/java/com/logmng/webtest/DecryptControllerTest.java** — Added @DisplayName for TC-01, TC-02, TC-04, TC-05; added `decryptRow_requesterExecutesWithValidApproval_returns200` (TC-01), `decryptRow_whenUserIdMismatchOrWrongType_returns403DecryptionNotApproved` (TC-04); made StubAuthServiceDecryptAllowed configurable (currentUserId/currentUsername) for requester vs approver tests.
- **backend/src/test/java/com/logmng/aspect/ActivityLogAspectTest.java** — Added @DisplayName for TC-03 to existing test `logActivity_doesNotSerializeHttpServletRequest_putsPlaceholderInRequestParams`.
- **docs/api-definition.md** — Added one sentence under §10.1: execution allowed only for **requester** (search_history.user_id = current user id); approver executing same searchHistoryId → 403 DECRYPTION_NOT_APPROVED.
- **docs/requirements/20260317-image-log-decrypt-error-root-cause-and-data-validation.md** — §2 change list and §5 test results updated.

**Follow-up (diagnostic logging and 403 subcode, same requirement):**

- **backend/src/main/java/com/logmng/service/ApprovalFailureReason.java** — New enum: ROW_NOT_FOUND, USER_MISMATCH, NOT_APPROVED, EXPIRED (diagnostic only, no PII).
- **backend/src/main/java/com/logmng/service/ApprovalFailureDiagnostic.java** — New DTO for diagnostic (reason, rowUserId, approvalStatus, expired).
- **backend/src/main/java/com/logmng/service/SearchHistoryService.java** — Added `getApprovalFailureReason(searchHistoryId, userId)` returning Optional&lt;ApprovalFailureDiagnostic&gt; for logging and 403 subcode.
- **backend/src/main/java/com/logmng/controller/DecryptController.java** — When 403 DECRYPTION_NOT_APPROVED: call getApprovalFailureReason, log at INFO "복호화 승인 검사 실패(진단): searchHistoryId=..., currentUserId=..., reason=..., rowUserId=..., approvalStatus=..., expired=..."; when reason is USER_MISMATCH return `detailCode` "EXECUTOR_NOT_REQUESTER".
- **backend/src/main/java/com/logmng/dto/response/ApiResponse.java** — Added optional `detailCode` and `failure(error, code, detailCode)`.
- **backend/src/test/java/com/logmng/service/StubSearchHistoryService.java** — Override getApprovalFailureReason to return Optional.empty() for tests.
- **backend/DB_SETUP_GUIDE.md** — Added § "복호화 403 시 점검" with diagnostic SQL and interpretation (compare search_history.user_id to requester app_user.id).
- **docs/api-definition.md** — §10.1: 403 DECRYPTION_NOT_APPROVED 시 `detailCode: "EXECUTOR_NOT_REQUESTER"` 포함 가능하다고 명시.

**Confirmed (no code change):** `ActivityLogAspect.java` — bugfix-1 fully applied. **Verified:** `SearchHistoryUserIdMigrationCheck.java`, `migrate-search-history-user-id-to-bigint.sql`, and `backend/DB_SETUP_GUIDE.md` § search_history.user_id exist and are documented.

#### Backend

- **Confirm** (no change if already done): `ActivityLogAspect.java` — Servlet params never passed to ObjectMapper; deepSanitize and sanitizeParamsForSerialization used. (Per bugfix-1.)
- **Optional**: Improve log or user-facing message when 403 DECRYPTION_NOT_APPROVED is due to “execution user is not the requester” (e.g. when `search_history.user_id != currentUserId`) so operators can distinguish from “user_id column type/value” issues. Implement only if product confirms.

#### DB

- No schema change in this requirement. Migration and startup check are already specified in bugfix-1 and `backend/DB_SETUP_GUIDE.md`. **Verify** that `migrate-search-history-user-id-to-bigint.sql` and **SearchHistoryUserIdMigrationCheck** are present and documented.

#### Contract / Spec

- **Optional**: In `docs/contract.md` or `docs/api-definition.md`, state that POST `/api/logs/decrypt/{logType}` execution is allowed only for the **requester** (the user whose id is `search_history.user_id`) when the search_history is APPROVED and the row is in the approved snapshot. Implement only if product confirms.

#### Cursor tools

- **Optional**: `.cursor/skills/search-history-decrypt-domain/SKILL.md` — add one line that decrypt **execution** is allowed only for the requester (search_history.user_id = current user id). Implement only if product confirms.

---

## 3. Test approach

### Test case list (required)

| ID | Scope | Type | Scenario (input / condition) | Expected result | Verification (unit / integration / manual) |
|----|-------|------|------------------------------|-----------------|--------------------------------------------|
| TC-01 | Backend | Normal | Image log: user2 requested, user1 approved; **user2** (requester) executes decrypt with valid searchHistoryId, guid in snapshot. | 200; decrypted data returned; Aspect does not throw (no Servlet serialization). | Integration (curl) or unit (DecryptControllerTest) |
| TC-02 | Backend | Exception | Same approval; **user1** (approver) executes decrypt with same searchHistoryId, guid. | 403, code `DECRYPTION_NOT_APPROVED` (execution user is not requester). | Integration or unit |
| TC-03 | Backend | Normal | Decrypt endpoint called with valid approval and snapshot; request body is JSON (no HttpServletRequest in body). Aspect builds params from (logType, request Map, httpRequest). | No 500 from Aspect; activity log saved with placeholders for Servlet param. | Unit (ActivityLogAspectTest) |
| TC-04 | Backend | Edge | search_history.user_id is wrong type (e.g. VARCHAR with username) or wrong value (e.g. different user id); current user is requester but row not found. | 403 `DECRYPTION_NOT_APPROVED`; no 500; log message indicates approval check failed. | Integration (DB with pre-migration schema) or unit with mocked DB |
| TC-05 | Backend | Edge | searchHistoryId valid and APPROVED, but guid not in search_history_approved_row. | 403 `ROW_NOT_IN_APPROVED_SNAPSHOT`. | Unit or integration |
| TC-06 | Backend | Optional | Startup: search_history.user_id column is not BIGINT. | SearchHistoryUserIdMigrationCheck logs WARN with migration command and DB_SETUP_GUIDE reference. | Integration (startup with DB before migration) or manual |

### Test scenarios

#### Scenario 1: Requester executes after approval (happy path)

1. user2 creates search, requests approval; user1 approves with snapshot including target row.
2. user2 calls POST `/api/logs/decrypt/java_fw_imglog` with body `{ "searchHistoryId": &lt;id&gt;, "guid": "&lt;guid&gt;", "status": "..." }`.
3. **Verification**: 200, response contains decrypted data; activity log entry created; no Aspect exception.

#### Scenario 2: Approver executes (expected 403)

1. Same approval as above.
2. user1 calls POST `/api/logs/decrypt/java_fw_imglog` with same searchHistoryId and guid.
3. **Verification**: 403, `DECRYPTION_NOT_APPROVED`; no 500.

#### Scenario 3: Aspect does not serialize Servlet types

1. Any @ActivityLog method with HttpServletRequest (e.g. decryptRow) is invoked.
2. **Verification**: ActivityLogAspectTest covers: direct HttpServletRequest → placeholder; Map containing HttpServletRequest → no exception, placeholder in params; no call to ObjectMapper with raw Servlet type.

#### Scenario 4: search_history.user_id type or value wrong

1. DB has search_history with user_id VARCHAR and username value, or user_id = other user’s id; current user is the intended requester.
2. **Verification**: 403 DECRYPTION_NOT_APPROVED (or after migration, 200 when data is correct).

### Test data

- Two users: user1 (approver), user2 (requester). search_history row owned by user2 (user_id = user2’s app_user.id), APPROVED, with search_history_approved_row containing the target log_type and row_id.
- Optional: search_history with user_id VARCHAR/username for TC-04 (pre-migration scenario).

### Test environment

- Backend: `http://localhost:9200`
- Database: PostgreSQL (schema with search_history, search_history_approved_row, app_user); optionally run with and without migrate-search-history-user-id-to-bigint.

---

## 4. Checklist

### Backend verification

- [x] Aspect never passes Servlet types to ObjectMapper (bugfix-1).
- [x] Decrypt 403 cases (DECRYPTION_NOT_APPROVED, ROW_NOT_IN_APPROVED_SNAPSHOT) covered by tests.
- [x] Optional: Migration check (SearchHistoryUserIdMigrationCheck) verified at startup when user_id is not BIGINT (implementation and DB_SETUP_GUIDE verified).

### Documentation

- [x] Requirement doc completed.
- [x] Optional: Contract or api-definition updated with “who may execute decrypt” (requester only) — docs/api-definition.md §10.1.

---

## 5. Test results

(Filled when tests were run per §3.)

### Test run date

- 2026-03-17 (Backend implementation verification)

### Test results

| ID | Result | Notes |
|----|--------|--------|
| TC-01 | Pass | `DecryptControllerTest.decryptRow_requesterExecutesWithValidApproval_returns200` — requester (stub user 20260001), validApproval true, row in snapshot → 200, data returned. |
| TC-02 | Pass | `DecryptControllerTest.decryptRow_whenValidApprovalFalse_returns403DecryptionNotApproved` — approver scenario (validApproval false) → 403 DECRYPTION_NOT_APPROVED. |
| TC-03 | Pass | `ActivityLogAspectTest.logActivity_doesNotSerializeHttpServletRequest_putsPlaceholderInRequestParams` — decryptRow args (logType, Map, httpRequest); httpRequest stored as `<HttpServletRequest>`; no 500. |
| TC-04 | Pass | `DecryptControllerTest.decryptRow_whenUserIdMismatchOrWrongType_returns403DecryptionNotApproved` — stub currentUserId 20260002, validApproval false (simulates wrong user_id type/value) → 403 DECRYPTION_NOT_APPROVED. |
| TC-05 | Pass | `DecryptControllerTest.decryptRow_whenRowNotInApprovedSnapshot_returns403WithCode` — validApproval true, rowInApprovedSnapshot false → 403 ROW_NOT_IN_APPROVED_SNAPSHOT. |
| TC-06 | Optional | Not run (startup with non-BIGINT user_id would require DB fixture). SearchHistoryUserIdMigrationCheck implementation and DB_SETUP_GUIDE verified. |

**Command:** `cd backend && mvn test -q` — exit 0 (full backend test suite).

### Verification (restart + health check)

- **Date**: 2026-03-17
- **Steps**: `./scripts/dev-services.sh backend restart`; wait 7 s; `curl -s http://localhost:9200/api/health`.
- **Result**: Pass — HTTP 200, JSON `{"success":true,"data":{"status":"OK",...}}`. Backend-only scope; browser check skipped per verify.md.

---

## 6. Error remedy result (cause and action) — for error/bug fix requirements only

- **Requirement ID**: 20260317-image-log-decrypt-error-root-cause-and-data-validation
- **Root cause**: (1) **Surface**: ActivityLogAspect serializing HttpServletRequest (or nested Map containing it) → 500. (2) **Data path**: (a) search_history.user_id not BIGINT or stores username → no row match → 403; (b) execution user is approver not requester → 403 by design.
- **Actions taken**: Aspect fix and migration/startup check per bugfix-1; this doc clarifies root cause and adds §3 test cases and data-validation subsection.
- **Result**: Backend verification complete. ActivityLogAspect bugfix-1 confirmed; DecryptController + Aspect tests added/updated (TC-01–TC-05); api-definition updated for requester-only execution. All backend tests pass.
- **Completed**: 2026-03-17 (Backend implementation and test run)
- **Follow-up**: Diagnostic logging when isValidApprovalForUser is false (INFO log with searchHistoryId, currentUserId, reason, rowUserId, approvalStatus, expired); optional 403 subcode EXECUTOR_NOT_REQUESTER when reason is USER_MISMATCH; DB_SETUP_GUIDE § 복호화 403 시 점검 with diagnostic SQL.
- **Recommendation for user**: If 403 persists after migration, run the diagnostic SQL in `backend/DB_SETUP_GUIDE.md` § "복호화 403 시 점검" with the **failing searchHistoryId** and compare that row’s `user_id` with the requester’s `app_user.id`; also check backend log line "복호화 승인 검사 실패(진단)" for currentUserId vs rowUserId.
