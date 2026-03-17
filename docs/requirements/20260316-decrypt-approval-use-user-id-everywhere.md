# 20260316 - Decrypt approval: use user_id (numeric) everywhere in approval flow (bugfix child)

**Parent requirement**: [20260316-decrypt-approve-cross-user-server-error](20260316-decrypt-approve-cross-user-server-error.md). Error still occurs after the parent fix. This doc addresses **hypothesis 2** (user_id vs username mapping) and clarifies **hypothesis 1** (approver-at-request-time).

---

## 1. User requirement

### Requirement description

After the parent fix (exception handling so cross-user approval returns 200 or 4xx), the user still sees an error when **user1** (approver) approves **user2**'s decryption request. The user requests: (1) **Clarification** on whether "user2 requested when user1 was not in the approver group" could be the cause — document that the **current** check uses **current** decrypt_approver membership and **current** app_user department; therefore a 500 is **not** expected from that alone (disallowed case should yield 403). (2) **Replace all usages of user_name (username) with user_id (numeric app_user.id)** in the approval/decrypt flow so that permission checks and storage use consistent identifiers and username-resolution errors (e.g. id↔username mapping mistakes) are eliminated.

### User scenario

1. **user2** (requester) creates a decryption (search-history) approval request; the request is PENDING.
2. **user1** (approver) logs in and attempts to approve **user2**'s pending request (e.g. POST `/api/search-history/{id}/approve`).
3. **Problem**: An error still occurs (e.g. 500 or incorrect 403) despite the parent fix.
4. **User hypotheses**:
   - **Hypothesis 1**: user1 might not have been in the approver group when user2 created the request. The user wants to know if that could cause the error.
   - **Hypothesis 2**: user_id vs user_name mapping may be wrong; the user wants **all** places that use **user_name** in the approval/decrypt flow to use **user_id** instead.
5. **Expected**: (1) It is documented that the system uses **current** approver membership and **current** department — no "snapshot at request time"; so 500 is not expected from hypothesis 1 alone (if not allowed → 403). (2) Permission checks and persistence in the approval flow use **numeric app_user.id** (user_id) consistently; display-only fields (e.g. approvedBy label in UI) may still resolve to username for display.

### Expected outcome

- **Clarification (hypothesis 1)**: The approval logic uses **current** membership in `decrypt_approver` and **current** `app_user.department_code`. There is no "who was approver when the request was created" snapshot. So "user1 was not approver when user2 requested" would normally result in **403** (no permission), not 500. If the product later wants request-time approver snapshot, that is a **separate feature**; this requirement focuses on hypothesis 2 and stability.
- **Identifier consistency (hypothesis 2)**:
  - **decrypt_approver**: Permission checks use **app_user.id** (BIGINT). Schema supports this (e.g. add `app_user_id BIGINT`; backfill from username; use in code). Optionally keep `user_id` VARCHAR for backward compatibility until deprecated, per product decision.
  - **search_history**: Requester is already `user_id BIGINT` = app_user.id. **Approver** is stored as **approved_by_user_id BIGINT** (and optionally `approved_by` VARCHAR for display/backward compat). On approve, code sets `approved_by_user_id`; list/detail APIs may return a display string (e.g. username) resolved from `approved_by_user_id` or `approved_by`.
  - **DecryptApproverService**: `isApprover(Long appUserId)`, `canApproveForRequester(Long approverUserId, Long requesterUserId)`; internal lookups use numeric id (decrypt_approver.app_user_id, app_user.id for department).
  - **SearchHistoryService.approve**: Passes Long approverUserId and Long requesterUserId to `canApproveForRequester`; writes `approved_by_user_id` (and optionally approved_by for display); no id→username resolution for permission or storage.
- **Stability**: No 500 in cross-user approve due to username resolution or id/username mismatch; allowed case → 200, disallowed → 403.

---

## 2. Design

### 2.1 Security review (optional)

- [ ] Security review performed (check if applicable)
- **Context**: This change does not relax approval rules. It switches the approval path from username to numeric user id for consistency and to avoid resolution errors. Approval policy (decrypt_approver, department hierarchy) remains enforced; only the identifier type (id vs username) changes for internal checks and storage.
- **Risks**: None expected if migration correctly backfills app_user_id from existing username-based data.

### Technical design

#### Codebase summary (username usage in decrypt-approval flow)

- **decrypt_approver table** (`backend/src/main/resources/db/schema.sql`): `user_id VARCHAR(100)` stores **username** (schema comment: "user_id = app_user.username"). Used by DecryptApproverService for `isApprover` and `canApproveForRequester` (both pass username; query `WHERE user_id = ?`).
- **search_history table**: `user_id BIGINT` = requester's app_user.id (already numeric). `approved_by VARCHAR(100)` = approver **username**; written on approve in SearchHistoryService (e.g. `ps.setString(1, approverUsername)`).
- **init-data** (`init-data.sql`): decrypt_approver inserts use username ('user1'). search_history sample uses app_user.id via JOIN on username.
- **DecryptApproverService** (`DecryptApproverService.java`):
  - `isApprover(String userId)` — parameter is username; queries `decrypt_approver WHERE user_id = ?` (VARCHAR).
  - `canApproveForRequester(String approverUserId, String requesterUserId)` — both usernames; queries decrypt_approver by username; gets requester department via `app_user WHERE username = ?`.
  - `listUsers()` and other methods use username for isApprover(username) and app_user lookups by username; list/detail display can keep resolving id→username for UI.
- **SearchHistoryService** (`SearchHistoryService.java`):
  - `approve(id, approverUserId)` where approverUserId is **Long**: resolves `approverUsername = appUserResolver.getUsernameById(approverUserId)`, `requesterUsername = getUsernameById(requesterUserIdLong)`; calls `canApproveForRequester(approverUsername, requesterUsername)`; writes `approved_by = approverUsername`.
  - Pending list: same pattern — requesterId from row, requesterUsername from getUsernameById; canApproveForRequester(approverUsername, requesterUsername).
  - List/detail mapping: `approvedBy` from `rs.getString("approved_by")` (username); can remain display-only after migration (resolve from approved_by_user_id or keep approved_by for display).
- **SearchHistoryController**: Uses `getCurrentUserId()` (Long); passes Long to service. No change except ensuring service receives Long and does not depend on username for permission.
- **AppUserResolver**: `getUsernameById(Long)` used in approval path to convert id→username; after this requirement, approval path should not need this for permission or storage (only for display if needed).

#### Problem analysis

1. **Mixed identifiers**: The approval path converts approver/requester from **id** (Long) to **username** (String) for `canApproveForRequester` and for storing `approved_by`. If id→username resolution fails or is inconsistent (e.g. null, wrong user), permission check or DB update can behave incorrectly or throw, contributing to 500 or wrong 403.
2. **decrypt_approver by username**: Decrypt_approver.user_id is VARCHAR storing username. Any mismatch (e.g. case, trim, or missing row in app_user) can make permission checks inconsistent with who the user actually is (by id).
3. **approved_by as username**: Storing approver as username ties approval history to a string that can change (e.g. username rename); numeric id is stable and unambiguous.

#### Solution approach

Structure by scope for handoff.

**DB**

- **decrypt_approver**:
  - Add column `app_user_id BIGINT NULL REFERENCES app_user(id)` (or NOT NULL after backfill). Backfill: `UPDATE decrypt_approver da SET app_user_id = u.id FROM app_user u WHERE u.username = da.user_id`.
  - Use `app_user_id` in application code for permission checks. Optionally keep `user_id` VARCHAR for backward compatibility until deprecated, or migrate to drop it per product decision (document in migration script).
  - Update schema.sql and any init-data that inserts into decrypt_approver to use app_user_id (or keep inserting by username and run backfill; init-data currently uses 'user1').
- **search_history**:
  - Add column `approved_by_user_id BIGINT NULL REFERENCES app_user(id)`. Backfill: `UPDATE search_history sh SET approved_by_user_id = u.id FROM app_user u WHERE u.username = sh.approved_by` where approved_by IS NOT NULL.
  - On approve, update code to set `approved_by_user_id` to the approver's app_user.id. Optionally keep setting `approved_by` (username) for display/backward compat, or deprecate once list/detail resolve from approved_by_user_id.
  - Migration script: idempotent ADD COLUMN + backfill; document in schema comment.

**Backend**

- **DecryptApproverService**:
  - Change to **id-based** API: `isApprover(Long appUserId)`, `canApproveForRequester(Long approverUserId, Long requesterUserId)`.
  - Internal lookups: query `decrypt_approver` by `app_user_id` (or by user_id if still present); get requester department via `app_user.id` (e.g. `SELECT department_code FROM app_user WHERE id = ?`). Remove use of username for permission logic.
  - `listUsers()` and other methods that need "is this user an approver?" must pass id (or resolve id from username only for display); `isApprover(String username)` callers must be updated to resolve username→id once at boundary or use id.
- **SearchHistoryService.approve**:
  - Take approverUserId (Long) and requesterUserId (Long). Call `canApproveForRequester(approverUserId, requesterUserId)` with Longs; no getUsernameById for permission.
  - Write `approved_by_user_id = approverUserId` (and optionally approved_by for display) in UPDATE search_history. Ensure SELECT/INSERT/UPDATE use the new column where applicable.
- **SearchHistoryService** (pending list, reject, etc.): Use Long approver/requester ids when calling DecryptApproverService; resolve to username only for display (e.g. approvedBy label in response) if needed.
- **SearchHistoryController**: Already passes getCurrentUserId() (Long) to service; confirm service uses Long only for approval path.
- **AppUserResolver**: Still used where **display** of username is needed (e.g. approvedBy string in list/detail). Permission and storage use id; display can resolve id→username in API response builders.
- **Tests**: Update DecryptApproverService tests (isApprover(Long), canApproveForRequester(Long, Long)); SearchHistoryServiceTest and SearchHistoryControllerTest to use id-based stubs and assert approved_by_user_id or approvedBy display as required. StubDecryptApproverService must support id-based canApproveForRequester.

**Frontend**

- No change required for **permission or API contract** if approve request/response and list/detail **response shape** are unchanged (e.g. approvedBy still returned as a string for display). If API response renames or adds fields (e.g. approvedByUserId + approvedBy), frontend may need to use the new field for display; contract must define this.

**Contract / Spec**

- Update contract and api-definition if approve response or list/detail response add or change fields (e.g. approvedByUserId, or approvedBy still as display string). Error codes unchanged from parent.

#### Cursor tool update targets

- **`.cursor/skills/search-history-decrypt-domain/SKILL.md`**: Update to state that approval path uses **numeric app_user.id** for permission and storage; decrypt_approver and search_history approval columns use app_user_id; display (e.g. approvedBy in list/detail) may still show username resolved from id.

### Affected scopes and change targets (verification)

**Before finalizing §2**, the Requirements author has run the change target checklist per `docs/workflow/REQUIREMENTS-CHANGE-TARGET-CHECKLIST.md`.

| Scope | Affected? | §2 subsection and change file list complete? |
|-------|-----------|-----------------------------------------------|
| Backend | Yes | Yes |
| Frontend (config UI + view screen) | Only if API response shape for approve/list/detail changes (e.g. approvedBy) | Yes (noted as optional) |
| DB | Yes | Yes |
| Contract / Spec | Yes (if API/DB contract changes) | Yes |
| Cursor tools (skills, specs) | Yes (search-history-decrypt-domain) | Yes |

**Change target verification**: Backend and DB are the primary affected scopes. Frontend is affected only if the API response shape for approve or list/detail changes (e.g. approvedBy field source or new approvedByUserId). Contract/spec must be updated if API or DB contract changes. Cursor skill search-history-decrypt-domain must reflect the new identifier model.

### Planned change file list (expected change targets)

**(Planned at authoring. Implementing agent (Step 4) confirms or amends this list when implementation is complete.)**

#### Backend (implemented 2026-03-17)

- `backend/src/main/java/com/logmng/service/DecryptApproverService.java` — Changed to id-based: isApprover(Long), canApproveForRequester(Long, Long); query decrypt_approver by app_user_id; requester department by app_user.id. listUsers/getUserSummary use isApprover(id).
- `backend/src/main/java/com/logmng/service/SearchHistoryService.java` — approve(): call canApproveForRequester(approverUserId, requesterUserId) with Longs; write approved_by_user_id and approved_by (display); permission path uses Long only. listPending, reject: use Longs for canApproveForRequester. List/detail: putApprovalFieldsFromRs + resolveApprovedByDisplay from approved_by_user_id or approved_by.
- `backend/src/main/java/com/logmng/controller/SearchHistoryController.java` — requireApproverOrAdmin uses getCurrentUserId() and isApprover(userId) (Long).
- `backend/src/main/java/com/logmng/service/AuthService.java` — resolveScreenFunctions: resolve username→id via AppUserResolver.getIdByUsername, then isApprover(userId).
- `backend/src/main/java/com/logmng/service/AppUserResolver.java` — Unchanged; used for display resolution (approvedBy from approved_by_user_id).
- `backend/src/test/java/com/logmng/service/StubDecryptApproverService.java` — Override isApprover(Long), canApproveForRequester(Long, Long).
- `backend/src/test/java/com/logmng/service/SearchHistoryServiceTest.java` — H2 table search_history: added approved_by_user_id, user_id BIGINT; approve tests use id-based canApproveForRequester; approve_whenApproverNotAllowed_throws403 for unknown approver id.
- `backend/src/test/java/com/logmng/controller/SearchHistoryControllerTest.java` — No signature change; approve tests use StubDecryptApproverService (id-based).
- `backend/src/test/java/com/logmng/service/DecryptApproverServiceUpdateRoleTest.java` — H2 decrypt_approver table: added app_user_id column.

#### DB (confirmed 2026-03-17)

- `backend/src/main/resources/db/schema.sql` — decrypt_approver: added app_user_id BIGINT NULL REFERENCES app_user(id), comment. search_history: added approved_by_user_id BIGINT NULL REFERENCES app_user(id), comment. Index idx_decrypt_approver_app_user added.
- `backend/src/main/resources/db/migrate-decrypt-approval-use-user-id.sql` — New idempotent migration: ADD COLUMN IF NOT EXISTS for both columns; FK only if not already present; backfill decrypt_approver from user_id (username), search_history from approved_by (username); index on decrypt_approver(app_user_id).
- `backend/src/main/resources/db/init-data.sql` — decrypt_approver: INSERT now sets app_user_id from app_user WHERE username = 'user1'; comment documents backfill for existing DBs.

#### Contract / Spec (implemented 2026-03-17)

- `docs/contract.md` — No change (response shape unchanged; approvedBy remains display string).
- `docs/api-definition.md` — Updated: list/detail/approve response `approvedBy` documented as display string resolved from `approved_by_user_id` when present, else `approved_by`; approve response notes internal storage `approved_by_user_id`, `approved_by`.

#### Cursor tools

- `.cursor/skills/search-history-decrypt-domain/SKILL.md` — Update domain description: approval path uses numeric app_user.id; decrypt_approver.app_user_id and search_history.approved_by_user_id; display (approvedBy) may resolve from id.

#### Frontend

- Only if API response shape for approve or list/detail changes (e.g. approvedBy source or new field). List in change file list only when contract change requires frontend change.

---

## 3. Test approach

### Test case list (required)

| ID | Scope | Type | Scenario (input / condition) | Expected result | Verification (unit / integration / manual) |
|----|-------|------|------------------------------|-----------------|--------------------------------------------|
| TC-01 | Backend | Normal | user1 (approver) approves user2's (requester's) PENDING decryption request using **id-based** logic; user1 is allowed to approve for user2. | HTTP 200 with approval result; approved_by_user_id and/or approvedBy set correctly. | Unit (SearchHistoryControllerTest / SearchHistoryServiceTest) or integration |
| TC-02 | Backend | Exception | user1 (approver) approves user2's request; user1 is **not** allowed to approve for user2 (id-based canApproveForRequester returns false). | HTTP 403 with clear message; not 500. | Unit or integration |
| TC-03 | Backend | Regression | Cross-user approve (user1 approves user2's request). | Server does **not** return HTTP 500; response is 200 or 4xx. | Unit or integration |
| TC-04 | DB / Backend | Migration | After migration, decrypt_approver has app_user_id populated; search_history has approved_by_user_id populated where approved; permission checks use numeric id. | Queries use app_user_id / approved_by_user_id; backfill consistent with existing username data. | Unit or integration / manual DB check |
| TC-05 | Backend / Integration | Optional | List or detail API returns approvedBy for UI. | approvedBy display still shows username (resolved from approved_by_user_id or approved_by). | Unit or integration |

### Test scenarios

#### Scenario 1: Id-based approval success

1. Create PENDING search_history row for user2 (requester, user_id = app_user.id).
2. Log in as user1 (approver; decrypt_approver has app_user_id = user1's id for global or dept).
3. Call POST /api/search-history/{id}/approve with row id.
4. **Verification**: 200; approval stored with approved_by_user_id = user1's app_user.id; approvedBy in response (if present) shows user1's username for display.

#### Scenario 2: Id-based no permission

1. Same as above but user1 is not allowed to approve for user2 (id-based canApproveForRequester(user1Id, user2Id) = false).
2. **Verification**: 403; no 500.

#### Scenario 3: No 500 in cross-user approve

1. Ensure approve endpoint never returns 500 when user1 approves (or attempts to approve) user2's request.
2. **Verification**: All responses 200 or 4xx.

#### Scenario 4: Migration and backfill

1. Run migration: add app_user_id to decrypt_approver, backfill; add approved_by_user_id to search_history, backfill.
2. **Verification**: Existing rows have app_user_id/approved_by_user_id set; new approvals set approved_by_user_id.

### Test data

- user1, user2 in app_user; decrypt_approver has row(s) with app_user_id = user1's id (after migration). search_history with user_id = user2's id (BIGINT), PENDING. Use existing init-data or test fixtures; after migration, ensure decrypt_approver init uses app_user_id or backfill.

### Test environment

- Backend: per contract (e.g. http://localhost:9200). Database: PostgreSQL per project setup.

---

## 4. Checklist

### Frontend verification

- [ ] No change, or [ ] approvedBy/approvedByUserId display verified if API shape changed.

### Backend verification

- [ ] Id-based DecryptApproverService and SearchHistoryService.approve tests written and run.
- [ ] No 500 in cross-user approve; 200 or 403 as expected.
- [ ] Migration and backfill tested.

### Integration

- [ ] End-to-end: user1 approves user2's request → 200 or 403; list/detail shows approvedBy if applicable.

### Documentation

- [ ] Requirement doc completed.
- [ ] Contract/api-definition and skill updated if changed.

---

## 5. Test results

### Test run date

- (To be filled after implementation and QA.)

### Test results

- (To be filled.)

---

## 6. Error remedy result (cause and action) — for error/bug fix requirements only

- **Requirement ID**: 20260316-decrypt-approval-use-user-id-everywhere
- **Root cause**: (To be filled after verification.)
- **Actions taken**: (To be filled.)
- **Result**: (To be filled.)
- **Completed**: (To be filled.)

---

**Author**: Requirements subagent  
**Date**: 2026-03-17  
**Status**: In progress
