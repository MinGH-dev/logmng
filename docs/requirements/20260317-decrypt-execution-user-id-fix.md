# 20260317 - Decrypt execution error after approval: user_id as single source of truth (bugfix)

**Parent requirement**: [20260316-decrypt-approval-use-user-id-everywhere](20260316-decrypt-approval-use-user-id-everywhere.md). Approval flow was migrated to numeric `app_user.id`; the **decrypt execution** path (POST /api/logs/decrypt) or related data/mapping may still rely on username or pre-migration schema, causing an error when the user executes decryption after receiving approval.

---

## 1. User requirement

### Requirement description

After receiving decryption approval, an error occurs when executing decryption. The user requests: (1) Check the logs and fix the root cause. (2) Ensure all data and mapping use **user_id** (numeric `app_user.id`) as the single source of truth; improve mapping to use user_id everywhere and remove or narrow user_name-based logic that could cause the post-approval decrypt error.

### User scenario

1. A requester creates a decryption (search-history) approval request; an approver approves it (APPROVED, within validity).
2. The requester (or an allowed user) opens the approved search result and attempts to decrypt a row (e.g. POST `/api/logs/decrypt/{logType}` with `searchHistoryId`, `guid`, `status`).
3. **Problem**: An error occurs when executing decryption (e.g. 403 DECRYPTION_NOT_APPROVED, 500, or other failure) despite the approval being granted.
4. **User hint**: When mapping user_id to user_name, reflect all data based on user_id and improve mapping to use user_id (not user_name).

### Expected outcome

- **No error when executing decryption after approval**: For an approved, non-expired search_history that belongs to the current user (by numeric `user_id`), the decrypt API returns 200 with decrypted data when the row is in the approved snapshot; no 500 and no incorrect 403.
- **user_id-based data and mapping**: All data and logic on the decrypt execution path (and data used by it, e.g. search_history rows, snapshot, approval checks) use **user_id** (Long / BIGINT) as the single source of truth. Username is used only for **display** (e.g. approvedBy label); no permission or validation depends on username in a way that can cause id/username resolution failure or inconsistency.
- **Stability**: No server error (500) due to id/username resolution or type mismatch in the decrypt execution path; allowed case → 200, disallowed → 403 with a clear code (e.g. DECRYPTION_NOT_APPROVED, ROW_NOT_IN_APPROVED_SNAPSHOT).

---

## 2. Design

### 2.1 Security review (optional)

- [ ] Security review performed (check if applicable)
- **Context**: This requirement does not relax decryption policy. It ensures the decrypt execution path and related data use numeric user_id consistently so that approval and ownership checks do not fail or throw due to username resolution or mixed identifier types.
- **Risks**: None expected if migrations are applied and code consistently uses Long/BIGINT for ownership and approval checks.

### Technical design

#### Codebase summary (decrypt execution path and user_id/username usage)

- **Decrypt execution path**:
  - **DecryptController** (`DecryptController.java`): Receives `searchHistoryId` and gets `currentUserId` (Long) from `authService.getCurrentUserInfo(httpRequest).getUserId()`. Calls `searchHistoryService.isValidApprovalForUser(searchHistoryId, currentUserId)` then `searchHistoryService.isRowInApprovedSnapshot(searchHistoryId, logType, guid)`; then `logDbService.decryptRow(logType, guid, status)`.
  - **SearchHistoryService.isValidApprovalForUser(Long, Long)**: Runs `SELECT 1 FROM search_history WHERE id = ? AND user_id = ? AND approval_status = 'APPROVED' AND expires_at > CURRENT_TIMESTAMP` with `bindUserId(ps, 2, userId)`. So it is already id-based **if** `search_history.user_id` is BIGINT and stores `app_user.id`. If `search_history.user_id` is still VARCHAR (pre-migration) or contains username, the match fails → 403 DECRYPTION_NOT_APPROVED.
  - **SearchHistoryService.isRowInApprovedSnapshot**: Queries `search_history_approved_row` by `search_history_id`, `log_type`, `row_id` only; no user_id/username. Safe.
  - **LogDbService.decryptRow**: No user identifier; performs crypto and row lookup. No change.

- **Current user resolution**:
  - **AuthService.getCurrentUserInfo**: Builds `LoginResponse` from session. Session may store `userId` (Long) or only `username`. If `sessionUserId` is null, it builds response from `session.getAttribute("username")` and sets `resp.setUserId(uid)` where `uid = selfContext.getUserId() != null ? selfContext.getUserId() : appUserResolver.getIdByUsername(uname)`. If `uid` is null (e.g. user deleted or resolver failure), `userId` is not set → DecryptController sees `currentUser.getUserId() == null` → 401. So ensuring session stores and returns numeric userId consistently avoids 401 from missing id.
  - **SearchHistoryController.getCurrentUserId**: Fallback `getIdByUsername(user.getUsername())` when `user.getUserId()` is null; used for list/detail, not for DecryptController. DecryptController uses only `getCurrentUserInfo(...).getUserId()`.

- **search_history and list/detail**:
  - **Schema** (`schema.sql`): `search_history.user_id BIGINT NOT NULL`, `approved_by_user_id BIGINT NULL`. List query uses `FROM search_history sh LEFT JOIN app_user au ON au.id = sh.user_id::bigint`. If `user_id` is still VARCHAR with username, `sh.user_id::bigint` can fail (invalid cast) or produce null → JOIN fails → list can throw or return incorrect data.
  - **buildListQuerySpec**: Filters by `sh.user_id::text = ?` with param `String.valueOf(userId)` to support both VARCHAR and BIGINT (req 20260316 bugfix-1). After migration to BIGINT, binding numeric Long directly (or as string "123") against BIGINT column is consistent.
  - **bindUserId / toLongUserId**: Already support Long; `toLongUserId` parses numeric string to Long for ResultSet. No username in decrypt path.

- **Approval path** (already id-based per 20260316): `approve()` uses `canApproveForRequester(approverUserId, requesterUserIdLong)` (Long, Long); writes `approved_by_user_id` and display `approved_by`. Snapshot insert uses only `search_history_id`, `log_type`, `row_id`. No username in permission or snapshot.

#### Problem analysis

1. **Pre-migration or mixed search_history.user_id**: If `search_history.user_id` is still VARCHAR (migration `migrate-search-history-user-id-to-bigint.sql` not applied) or contains username in some rows, `isValidApprovalForUser(searchHistoryId, currentUserId)` compares Long (currentUserId) with VARCHAR (e.g. "user1"). In PostgreSQL, binding Long to the prepared statement may coerce to string; the row has "user1" → no match → 403 DECRYPTION_NOT_APPROVED even though the row is approved and belongs to the same user by identity.
2. **List JOIN cast failure**: The list query uses `sh.user_id::bigint` in the JOIN. If `user_id` is VARCHAR and stores username, the cast fails at query execution → SQLException → 500 on search-history list. That can prevent the user from seeing the approved row or from using the correct searchHistoryId for decrypt.
3. **Session/userId not set**: If session has only username (e.g. old session before login set userId) and `getIdByUsername` or `resolveSelfContext` returns null, `LoginResponse.userId` is null → DecryptController returns 401. Ensuring login and getCurrentUserInfo always set userId when the user is authenticated removes this failure mode.
4. **Remaining username usage**: Any remaining use of username for permission or validation (e.g. in a code path called indirectly by decrypt) that can throw or return wrong result when id↔username resolution fails or is inconsistent should be removed or narrowed to display-only.

#### Solution approach

Structure by scope for handoff.

**Backend**

- **Decrypt execution path**: Confirm and document that DecryptController, `isValidApprovalForUser`, and `isRowInApprovedSnapshot` use only Long/BIGINT for user and search_history ownership. No code change if already id-based; ensure no hidden username dependency (e.g. in AuthService.getCurrentUserInfo when building response from session).
- **AuthService.getCurrentUserInfo**: When building LoginResponse from session, ensure `userId` is always set when the user is authenticated (session has userId or username). When session has only username, set `resp.setUserId(uid)` only if `uid != null` (current behavior); consider logging when uid is null despite valid username so operators can diagnose. No 500 from getCurrentUserInfo (already never throws).
- **SearchHistoryService list query**: The list FROM clause uses `sh.user_id::bigint`. If the project guarantees that `search_history.user_id` is always BIGINT (migration applied), the cast is safe. If there is a mixed state (some DBs not migrated), the existing `bindUserId` and `sh.user_id::text = ?` with String.valueOf(userId) in WHERE already support both; the JOIN `au.id = sh.user_id::bigint` can fail on VARCHAR. **Solution**: Ensure migration is applied before or as part of this fix so that `search_history.user_id` is BIGINT everywhere; document migration order in setup/runbook. Optionally, make the list JOIN robust to VARCHAR (e.g. use a safe cast or two-path JOIN) only if product accepts temporary compatibility; prefer migration as the fix.
- **Logging**: Add or align log messages in the decrypt execution path (DecryptController, isValidApprovalForUser, isRowInApprovedSnapshot) so that on 403/500 the logs contain searchHistoryId, currentUserId, and (if safe) whether the row was found and approval status, to aid diagnosis without exposing PII.
- **Tests**: Extend or add tests for decrypt execution: (1) approved search_history with user_id BIGINT, currentUserId matches → 200; (2) search_history.user_id matches currentUserId but approval_status not APPROVED or expired → 403 DECRYPTION_NOT_APPROVED; (3) row not in snapshot → 403 ROW_NOT_IN_APPROVED_SNAPSHOT; (4) no 500 when id-based path is used (e.g. mock or integration with migrated schema).

**DB**

- **Migration order**: Document or ensure that `migrate-search-history-user-id-to-bigint.sql` is applied before (or together with) `migrate-decrypt-approval-use-user-id.sql` where relevant, so that `search_history.user_id` is BIGINT and all rows store `app_user.id`. No schema change in this requirement if migrations already exist; only verify runbook/setup so that decrypt execution runs against migrated DB.
- **Verification**: If needed, add a small verification step (e.g. in setup or health) that `search_history.user_id` column type is bigint so that operators can confirm migration state.

**Contract / Spec**

- Update only if error response shape or codes for decrypt API change. Current codes (DECRYPTION_NOT_APPROVED, ROW_NOT_IN_APPROVED_SNAPSHOT, DECRYPTION_FAILED) remain; document that ownership and approval checks use numeric user_id.

**Cursor tools**

- **`.cursor/skills/search-history-decrypt-domain/SKILL.md`**: State that the **decrypt execution** path (POST /api/logs/decrypt) uses numeric `app_user.id` only: `isValidApprovalForUser(searchHistoryId, currentUserId)` checks `search_history.user_id = currentUserId` (BIGINT); no username in this path. Display (e.g. approvedBy) may still resolve from approved_by_user_id.

### Affected scopes and change targets (verification)

**Before finalizing §2**, the Requirements author has run the change target checklist per `docs/workflow/REQUIREMENTS-CHANGE-TARGET-CHECKLIST.md`.

| Scope | Affected? | §2 subsection and change file list complete? |
|-------|-----------|-----------------------------------------------|
| Backend | Yes | Yes |
| Frontend (config UI + view screen) | No | N/A |
| DB | Yes (migration order / verification only) | Yes |
| Contract / Spec | Only if error/docs change | Yes (minimal) |
| Cursor tools (skills) | Yes (search-history-decrypt-domain) | Yes |

**Change target verification**: Backend is the primary implementer (confirm path is id-based, logging, tests). DB scope is migration order and optional verification script. Contract/spec: document that decrypt ownership/approval use user_id. Cursor skill: update domain description for decrypt execution path.

### Planned change file list (expected change targets)

**(Planned at authoring. Implementing agent (Step 4) confirms or amends this list when implementation is complete.)**

#### Backend

- `backend/src/main/java/com/logmng/controller/DecryptController.java`
  - Confirm no username usage; align log messages (searchHistoryId, currentUserId) on 403/500 for diagnosis.
- `backend/src/main/java/com/logmng/service/SearchHistoryService.java`
  - Confirm `isValidApprovalForUser` and list query use only Long/BIGINT; ensure list JOIN is safe when user_id is BIGINT (migration applied); add or align logging on validation failure if needed.
- `backend/src/main/java/com/logmng/service/AuthService.java`
  - Ensure getCurrentUserInfo sets userId whenever the user is authenticated; consider log when username present but userId resolution returns null.
- `backend/src/test/java/com/logmng/controller/DecryptControllerTest.java` (or equivalent)
  - Add or extend tests: decrypt after approval with user_id match → 200; no 500 in id-based path; 403 when not approved or row not in snapshot.
- `backend/src/test/java/com/logmng/service/SearchHistoryServiceTest.java`
  - Add or extend: isValidApprovalForUser with BIGINT user_id row returns true when ids match and status APPROVED and not expired.

#### DB

- `backend/src/main/resources/db/setup.sh` or `backend/DB_SETUP_GUIDE.md` (or runbook)
  - Document migration order: ensure migrate-search-history-user-id-to-bigint is applied so that search_history.user_id is BIGINT before relying on decrypt execution path.

#### Contract / Spec

- `docs/contract.md` or `docs/api-definition.md`
  - Document that POST /api/logs/decrypt ownership and approval checks use numeric user_id (search_history.user_id and current user id); error codes unchanged.

#### Cursor tools

- `.cursor/skills/search-history-decrypt-domain/SKILL.md`
  - Update: decrypt execution path uses numeric app_user.id only; isValidApprovalForUser(searchHistoryId, currentUserId) checks search_history.user_id = currentUserId (BIGINT); no username in decrypt execution path.

**Actual files changed (Step 4 Backend, confirmed):**

- `backend/src/main/java/com/logmng/controller/DecryptController.java` — log messages (searchHistoryId, currentUserId) on 403 DECRYPTION_NOT_APPROVED, 403 ROW_NOT_IN_APPROVED_SNAPSHOT, 500 DECRYPTION_FAILED.
- `backend/src/main/java/com/logmng/service/SearchHistoryService.java` — debug logging in isValidApprovalForUser and isRowInApprovedSnapshot when no row found.
- `backend/src/main/java/com/logmng/service/AuthService.java` — warn log when session has username but userId resolution returns null.
- `backend/src/test/java/com/logmng/webtest/DecryptControllerTest.java` — added decryptRow_whenValidApprovalFalse_returns403DecryptionNotApproved.
- `backend/src/test/java/com/logmng/service/SearchHistoryServiceTest.java` — added isValidApprovalForUser_returnsFalseWhenExpired, isValidApprovalForUser_returnsFalseWhenStatusNotApproved.
- `backend/DB_SETUP_GUIDE.md` — documented that decrypt execution path requires migrate-search-history-user-id-to-bigint.
- `backend/src/main/resources/db/setup.sh` — comment that 4c must be applied before relying on decrypt execution path.
- `docs/contract.md` — documented decrypt ownership/approval use numeric user_id.
- `docs/api-definition.md` — documented decrypt ownership/approval use numeric user_id.
- `.cursor/skills/search-history-decrypt-domain/SKILL.md` — decrypt execution path uses numeric app_user.id only.

Unit tests: `mvn test` — exit 0.

---

## 3. Test approach

### Test case list (required)

| ID | Scope | Type | Scenario (input / condition) | Expected result | Verification (unit / integration / manual) |
|----|-------|------|------------------------------|-----------------|--------------------------------------------|
| TC-01 | Backend | Normal | Decrypt after approval: search_history has user_id = currentUserId (BIGINT), approval_status = APPROVED, not expired; row in snapshot. | 200 with decrypted data. | Unit or integration (DecryptControllerTest / webtest) |
| TC-02 | Backend | Exception | Same user, approved search_history, but row not in search_history_approved_row. | 403 ROW_NOT_IN_APPROVED_SNAPSHOT. | Unit or integration |
| TC-03 | Backend | Exception | searchHistoryId not owned by current user (user_id != currentUserId). | 403 DECRYPTION_NOT_APPROVED. | Unit or integration |
| TC-04 | Backend | Exception | search_history APPROVED but expired (expires_at < now). | 403 DECRYPTION_NOT_APPROVED. | Unit or integration |
| TC-05 | Backend | Regression | Decrypt execution path uses only Long; no username in isValidApprovalForUser or isRowInApprovedSnapshot. | No 500 from id/username resolution. | Unit (SearchHistoryServiceTest) or code review |
| TC-06 | Backend | Edge | getCurrentUserInfo: session has username only; getIdByUsername returns valid id. | LoginResponse.userId set; decrypt with that user's approved searchHistoryId succeeds when row in snapshot. | Unit (AuthService) or integration |
| TC-07 | DB / Backend | Migration | After migrate-search-history-user-id-to-bigint, search_history.user_id is BIGINT; isValidApprovalForUser matches by id. | Decrypt after approval returns 200 when row in snapshot. | Integration or manual DB check |

### Test scenarios

#### Scenario 1: Decrypt after approval (happy path)

1. Create search_history with user_id = requester's app_user.id (BIGINT), approval_status = APPROVED, expires_at > now.
2. Insert search_history_approved_row for that search_history_id, log_type, row_id (guid).
3. Call POST /api/logs/decrypt/java_fw_imglog with searchHistoryId, guid, status as the requester (currentUserId = requester id).
4. **Verification**: 200; response contains decrypted data.

#### Scenario 2: Not approved or wrong user

1. Same as above but search_history.user_id differs from currentUserId (or status not APPROVED / expired).
2. **Verification**: 403 DECRYPTION_NOT_APPROVED.

#### Scenario 3: Row not in snapshot

1. search_history approved and owned by current user; search_history_approved_row does not contain (search_history_id, log_type, guid).
2. **Verification**: 403 ROW_NOT_IN_APPROVED_SNAPSHOT.

### Test data

- app_user rows with known ids; search_history with user_id BIGINT, approval_status APPROVED, expires_at in future; search_history_approved_row with matching (search_history_id, log_type, row_id). Use existing init-data or test fixtures; ensure DB has run migrate-search-history-user-id-to-bigint so user_id is BIGINT.

### Test environment

- Backend: per contract (e.g. http://localhost:9200). Database: PostgreSQL; migrations applied per setup guide.

---

## 4. Checklist

### Frontend verification

- [ ] Not applicable (no frontend change).

### Backend verification

- [ ] Decrypt execution path confirmed id-based; tests added or extended and run.
- [ ] No 500 in decrypt after approval when data is consistent (BIGINT user_id, APPROVED, row in snapshot).
- [ ] Logging aligned for diagnosis (searchHistoryId, currentUserId on 403/500).

### Integration

- [ ] End-to-end: approve → decrypt same row → 200; decrypt without approval or wrong user → 403.

### Documentation

- [x] Requirement doc completed.
- [ ] Contract/api-definition and skill updated if changed.
- [ ] Migration order documented where applicable.

---

## 5. Test results

### Test run date

- (To be filled after implementation and QA.)

### Test results

- (To be filled.)

---

## 6. Error remedy result (cause and action) — for error/bug fix requirements only

- **Requirement ID**: 20260317-decrypt-execution-user-id-fix
- **Root cause**: (To be filled after verification.)
- **Actions taken**: (To be filled.)
- **Result**: (To be filled.)
- **Completed**: (To be filled.)

---

**Author**: Requirements subagent  
**Date**: 2026-03-17  
**Status**: Done
