# 20260318 - Search history not showing for user2 (root cause and fix)

## 1. User requirement

### Requirement description

When logged in as **user2**, the Search History screen (검색 이력) shows **no search history data** (empty list). The same screen may show data for other users (e.g. user1 or admin) or in other environments. The requirement is to **identify the root cause** and **apply a fix** so that user2 sees the correct search history data according to scope (self / team / all) and contract.

### User scenario

1. User logs in as **user2** (numeric user ID 20260002 per init-data; password per contract).
2. User opens the Search History screen (검색 이력 / 복호화 승인).
3. **Problem**: The list is empty (no rows), even when search history rows for user2 are expected (e.g. seed data or prior searches).
4. User expects: user2 sees their own search history when scope is **self**, or team members’ history when scope is **team**, or all when scope is **all**, consistent with `docs/contract.md` and search-history API behavior.

### Expected outcome

- **Root cause** is identified and documented (e.g. in §2 and §6 of this doc).
- **Fix** is applied so that when user2 (or any user with valid access) opens the Search History screen:
  - For **scope=self**: the list shows rows where `search_history.user_id` equals the current user’s numeric `app_user.id`.
  - For **scope=team**: the list shows rows for users in the same department (numeric `app_user.id` in allowed set).
  - For **scope=all**: the list shows all rows permitted by screen access.
- **Data and schema** are consistent: `search_history.user_id` stores numeric `app_user.id`; list API filter and JOIN use the same semantics (contract: `docs/contract.md`, req 20260316).
- **Init-data / migration**: If the cause is missing seed data for user2 or wrong `user_id` semantics in DB, init-data or migration must be corrected so that verification can pass without ad-hoc SQL.

---

## 2. Design

### 2.1 Security review (optional)

This requirement is a **bugfix** for visibility of search history list data. It does not introduce new PII or decryption scope. Access control remains: list API uses current user from auth/session and scope from permission groups; no client-supplied user id for ownership. No separate Security review required unless the fix changes scope or permission rules.

### Technical design

#### Codebase summary

- **Backend — list API**: `SearchHistoryController.list()` (GET `/api/search-history`) resolves `currentUserId` via `getCurrentUserId(httpRequest)` (from session or `LoginResponse.userId` / `selfContext.userId` / `appUserResolver.getIdByUsername`). Scope is resolved with `ScopeHelper.resolveScope(SEARCH_HISTORY, isSystemAdmin, getScreenScopes(httpRequest))`. For **scope=self** it sets `listRequest.setUserId(currentUserId)`; for **scope=team** it sets `listRequest.setAllowedUserIds(DepartmentScopeHelper.getNumericUserIdsInSameDepartment(dataSource, currentUserId))`; for **scope=all** it sets department/username/userId from request params only.
- **Backend — service**: `SearchHistoryService.buildListQuerySpec()` filters by `request.getUserId()` (`sh.user_id::text = ?` with numeric id as string) or `request.getAllowedUserIds()` (`sh.user_id::text IN (...)`). The list SQL uses `FROM search_history sh LEFT JOIN app_user au ON au.id = sh.user_id::bigint LEFT JOIN department d ...`. So the JOIN assumes `search_history.user_id` is numeric (BIGINT or VARCHAR holding digit string); if `user_id` holds username (e.g. `'user2'`), the cast `sh.user_id::bigint` can fail or the filter by numeric id will not match.
- **Contract/schema**: `docs/contract.md` and req 20260316 state that `search_history.user_id` is numeric `app_user.id`; list filter and response use numeric userId. Schema: `schema.sql` defines `search_history.user_id BIGINT NOT NULL` with FK to `app_user(id)`. Legacy migration `migrate-search-history-user-id-to-username.sql` had converted id to username; the canonical direction is `migrate-search-history-user-id-to-bigint.sql` (user_id numeric).
- **Init-data**: `init-data.sql` inserts search history seed rows with `INSERT INTO search_history (user_id, ...) SELECT u.id, v.log_type, ... FROM (VALUES ('admin',...), ('user1',...), ('user2',...)) AS v(username, ...) JOIN app_user u ON u.username = v.username WHERE NOT EXISTS (SELECT 1 FROM search_history LIMIT 1)`. So **insert runs only when the table is empty**. If the table already had rows (e.g. from a previous run or another seed path), user2’s row is never inserted; user2 then sees an empty list when scope=self or when scope=team but no other rows exist for that user.
- **Auth/session**: `AuthController` stores `userId` (Long) and `username` in session on login. `AuthService.getCurrentUserInfoInternal()` reads session `userId`; if missing, it resolves from `username` via `resolveSelfContext(uname)` or `appUserResolver.getIdByUsername(uname)`. So user2 should get `currentUserId = 20260002` after login if session is correct.
- **Frontend**: `SearchHistoryList.js` calls `getSearchHistoryList(params)`; for scope=self it does not send requester filters (`effectiveRequesterFilters` is null). Backend alone determines scope and filter; no frontend bug identified for “empty list” unless the API is called with wrong credentials or the response is misinterpreted.

#### Problem analysis

1. **Init-data conditional insert**: The seed block inserts rows for admin, user1, and user2 **only when `NOT EXISTS (SELECT 1 FROM search_history LIMIT 1)`**. If `search_history` already had one or more rows when init-data was applied (e.g. table not empty from an earlier run or from another script), the entire insert is skipped. Then user2 has **no rows**; for scope=self the list correctly returns 0 rows for user2, which appears as “data not showing.”
2. **search_history.user_id type/semantics mismatch**: If the database still has `search_history.user_id` as VARCHAR and contains **username** values (e.g. from legacy `migrate-search-history-user-id-to-username.sql` or old schema), then:
   - The list filter `sh.user_id::text = '20260002'` (current user id) matches no rows (rows have `user_id = 'user2'`).
   - The JOIN `au.id = sh.user_id::bigint` would fail on non-numeric strings (e.g. `'user2'`), causing a SQL error rather than empty list; if the JOIN were changed to `au.username = sh.user_id`, then filter by numeric id would still be wrong. So the most likely data-cause for **empty list** is (1) or missing rows, not mixed semantics with username in column (which would more likely cause errors).
3. **Scope or currentUserId resolution**: If for user2 `getCurrentUserId` returned null, the controller would return 401; the user would not see the screen with an empty list. If scope were resolved incorrectly (e.g. team with empty `allowedUserIds`), the query would add `1 = 0` and return 0 rows. So verification must confirm for user2: session/auth yields non-null `currentUserId` (20260002), and scope and `allowedUserIds` (when team) are as expected.

#### Solution approach

**Backend:**

- **Verify and fix list flow for user2**: Ensure `getCurrentUserId(httpRequest)` returns 20260002 for user2 after login (session has `userId` or fallback from username). Ensure scope for search-history for user2’s groups is resolved (e.g. default team) and `listRequest.setUserId(currentUserId)` or `listRequest.setAllowedUserIds(...)` is set accordingly. Add or adjust logging (no PII) to aid diagnosis if needed.
- **Defensive handling**: If `search_history.user_id` is still VARCHAR in some environments, the list query JOIN uses `sh.user_id::bigint`; ensure that either (a) migration to BIGINT is applied and init-data uses numeric id, or (b) backend tolerates VARCHAR and matches both id and username where contract allows (prefer (a) per contract).

**DB:**

- **Init-data**: Change the search history seed insert so that **user2 (and other test users) get at least one row** even when the table is not empty, or document that seed is “empty table only” and add a separate idempotent script that inserts missing user2 (and optionally user1/admin) rows by `app_user.id` so that verification can pass. Option: use `INSERT ... ON CONFLICT DO NOTHING` or per-user conditional inserts so that each of admin, user1, user2 has at least one row when init-data runs.
- **Migration**: Ensure `migrate-search-history-user-id-to-bigint.sql` (or equivalent) is applied so that `search_history.user_id` is BIGINT and matches `app_user.id`; any backfill from username to id must be done in migration, not left as username in column.

**Frontend:**

- No change required for “empty list” cause unless investigation finds a wrong API call or missing credentials (e.g. not sending cookies). If the fix involves a new API contract or error code, align with `docs/api-definition.md` and frontend error handling.

**Contract / docs:**

- No API shape change expected. If the fix changes when list returns 200 vs 401 or the semantics of scope, update `docs/contract.md` / `docs/api-definition.md` accordingly.

### Affected scopes and change targets (verification)

Per `docs/workflow/REQUIREMENTS-CHANGE-TARGET-CHECKLIST.md`:

| Scope | Affected? | §2 subsection and change file list complete? |
|-------|------------|-----------------------------------------------|
| Backend | Yes | Yes |
| Frontend (config UI + view screen) | No (unless bug found) | N/A |
| DB | Yes | Yes |
| Contract / Spec | Only if behavior doc change | Optional |
| Cursor tools (skills, specs) | No | N/A |

This requirement is a **bugfix** (root cause + fix). Pattern 3.1 (scope-supporting screen) and 3.2 (permission) are touched only to the extent of verifying current behavior; the fix may be backend + DB only.

### Planned change file list (expected change targets)

**(Planned at authoring. Implementing agent (Step 4) confirms or amends this list when implementation is complete.)**

#### Backend

- `backend/src/main/java/com/logmng/controller/SearchHistoryController.java`
  - Verify and, if needed, fix resolution of `currentUserId` and scope so that user2 gets correct list filter (self: userId=20260002; team: allowedUserIds including 20260002). Add diagnostic logging only if needed (no PII).
- `backend/src/main/java/com/logmng/service/SearchHistoryService.java`
  - Confirm list query JOIN and filter use numeric `app_user.id` and that `search_history.user_id` is treated consistently (BIGINT or VARCHAR digits). Fix if any path assumes username in `user_id`.
- `backend/src/test/java/com/logmng/controller/SearchHistoryControllerTest.java` (or equivalent)
  - Add or extend test: user2 (or non-admin user with scope self/team) receives 200 and list data when search_history has rows for that user (or allowed set).

#### DB

- `backend/src/main/resources/db/init-data.sql`
  - Ensure search history seed data includes at least one row for user2 (and optionally user1, admin) so that “user2 sees data” can be verified. Options: (1) Idempotent insert that adds missing users’ rows (by app_user.id) when not present; or (2) Remove or relax `WHERE NOT EXISTS (SELECT 1 FROM search_history LIMIT 1)` and use conflict-safe insert so that admin, user1, user2 each get a row. Implementer must not break existing environments; prefer idempotent per-user or conditional insert.
- `backend/src/main/resources/db/schema.sql` / migrations
  - No change required unless a new migration is needed to backfill or fix `user_id` type in environments where it is still VARCHAR.

#### Frontend

- None (unless investigation finds a frontend bug; then add the relevant component or service).

#### Contract / Spec

- `docs/contract.md` or `docs/api-definition.md`
  - Update only if the fix changes documented list API behavior or scope semantics.

---

## 3. Test approach

### Test case list (required)

| ID | Scope | Type | Scenario (input / condition) | Expected result | Verification (unit / integration / manual) |
|----|-------|------|------------------------------|-----------------|--------------------------------------------|
| TC-01 | Backend | Normal | user2 logged in; GET /api/search-history with valid session (scope=self or team). DB has at least one search_history row with user_id = 20260002 (or in same department for team). | 200; response body includes list with at least one row for user2 (or team members). | Integration (curl with session cookie or E2E) |
| TC-02 | Backend | Normal | user2 logged in; GET /api/search-history; scope=self. DB has no search_history row for user_id = 20260002. | 200; list empty; totalCount 0. | Integration |
| TC-03 | Backend | Normal | user2 logged in; GET /api/search-history; scope=team. Same department (e.g. user1, user2) has rows. | 200; list includes rows for users in same department (e.g. user1, user2). | Integration |
| TC-04 | Backend | Unit | SearchHistoryController list: currentUserId resolved from session or username; for scope=self, listRequest.getUserId() equals currentUserId. | No NPE; listRequest populated as per scope. | Unit (mvn test) |
| TC-05 | DB | Normal | After applying init-data (or corrective script), SELECT FROM search_history WHERE user_id = 20260002 returns at least one row (for dev seed). | At least one row. | Manual SQL or script |
| TC-06 | Integration | Normal | Login as user2 (userId 20260002), open Search History screen in browser. | List shows user2’s search history (or team’s if scope=team) when seed data exists. | Manual / browser |

### Test scenarios

#### Scenario 1: user2 sees own history (scope=self)

1. Apply DB so that `search_history` has at least one row with `user_id = 20260002`.
2. Log in as user2 (numeric id 20260002).
3. Open Search History screen (or GET /api/search-history with session).
4. **Verification**: List is non-empty and rows shown are for user2 (requester user id 20260002).

#### Scenario 2: user2 sees team history (scope=team)

1. Ensure user2’s permission group gives search-history scope team; ensure user1 and user2 share department (e.g. TEAM_SALES_A1).
2. Ensure `search_history` has rows for user_id 20260001 and 20260002.
3. Log in as user2; open Search History.
4. **Verification**: List includes rows for both user1 and user2 (same department).

#### Scenario 3: Seed data includes user2

1. Run init-data (or corrective script) on a DB where search_history may already have rows.
2. **Verification**: SELECT * FROM search_history WHERE user_id = 20260002 returns at least one row (for dev seed).

### Test data

- **user2**: app_user.id = 20260002, username = 'user2', department_code = 'TEAM_SALES_A1' (per init-data.sql).
- **search_history**: Rows with user_id = 20260002 (numeric) for scope=self test; rows with user_id in (20260001, 20260002) for scope=team. Use init-data or:  
  `INSERT INTO search_history (user_id, log_type, search_params, requested_at, expires_at, approval_status) SELECT 20260002, 'pb_send', '{}', CURRENT_TIMESTAMP - interval '1 hour', CURRENT_TIMESTAMP + interval '30 days', 'PENDING' FROM app_user WHERE id = 20260002 LIMIT 1 ON CONFLICT DO NOTHING;` (adjust if no unique constraint; idempotent pattern).

### Test environment

- Frontend: http://localhost:3001 (or per contract)
- Backend: http://localhost:9200
- Database: PostgreSQL (logmng per contract)

---

## 4. Checklist

### Frontend verification

- [ ] Not applicable unless frontend bug found (no change planned).

### Backend verification

- [ ] List API returns 200 for user2 when DB has rows for user2 (or team).
- [ ] currentUserId and scope resolved correctly for user2.
- [ ] Unit/integration tests added or updated per §3.

### Integration

- [ ] user2 login → Search History screen shows expected list (own or team).
- [ ] Seed or init-data includes user2 row where required.

### Documentation

- [ ] Requirement doc completed (§1, §2, §3).
- [ ] §6 Error remedy result filled after fix (root cause, actions, result).

---

## 5. Test results

### Test run date

- [Date and time]

### Test results

#### Backend

[Pass / Fail]

- [Result description]

#### Integration

[Pass / Fail]

- [Result description]

**Commands (examples):**

```bash
# Login as user2 (numeric id 20260002), then:
curl -s -b cookies.txt "http://localhost:9200/api/search-history?page=1&pageSize=20"
# Expect 200 and data.data array with at least one row when seed exists.
```

### Issues found and resolution

(To be filled when fix is implemented and verified.)

### Next steps

1. Implement root cause fix (Backend and/or DB per §2).
2. Run §3 test cases; record results in §5.
3. Complete §6 Error remedy result.

---

## 6. Error remedy result (cause and action) — for error/bug fix requirements only

(To be filled after fix is implemented and verified.)

- **Requirement ID**: 20260318-search-history-user2-not-showing
- **Root cause**: [To be filled by implementer/QA]
- **Actions taken**: [Summary of code and data changes]
- **Result**: [Verification method and result]
- **Completed**: yyyy-MM-dd HH:mm

---

**Author**: Requirements subagent  
**Date**: 2026-03-18  
**Status**: In progress
