# 20260317 - Activity statistics: department/approver-group scope error and user_id alignment

## 1. User requirement

### Requirement description

1. **Error when querying activity statistics by approver group / department** — On the activity statistics screen, when the user’s **permission (approver group)** is set to **department-level scope** (부서단위, scope=team), an error occurs during statistics query. The error must be fixed.
2. **Log-based diagnosis** — The issue was just reproduced; the implementer must **check backend logs** (e.g. backend console, `backend/logs/` if present, or application log output) to identify the exact exception or failure when statistics are requested with scope=team or department filter, and use that to confirm root cause before implementing the fix.
3. **user_id vs user_name mapping** — If **user_id** and **user_name** are wrongly mapped anywhere in the activity statistics flow (request params, scope resolution, service SQL, or response), the system must **align to user_id** (numeric `app_user.id`) where appropriate, so that semantics are consistent with the contract and with search-history (req 20260316).
4. **No main-agent implementation** — The main agent must not implement; after this requirement doc is complete, implementation is delegated to the responsible subagent(s) (Backend, Frontend, DB, etc.) per workflow.

### User scenario

1. A user whose **permission group** has the **statistics** screen with **department scope** (부서단위, scope=team) opens the activity statistics screen.
2. The user (or the frontend on load) requests statistics data: e.g. user list (`GET /api/statistics/users`), IP list (`GET /api/statistics/ips`), daily/monthly statistics, or department filter options.
3. **Problem**: An error occurs (e.g. server error, exception, or wrong/empty data). The user expects the statistics screen to work correctly when scope=team (department-level).
4. The user also expects that wherever **user_id** and **user_name** are mixed or incorrectly mapped, the system uses **user_id** (numeric `app_user.id`) consistently so that filtering, scope enforcement, and display are correct.

### Expected outcome

- **No error** when activity statistics is queried with **approver group / department scope** (scope=team): user list, IP list, daily/monthly statistics, export, and filter options must complete successfully and return correct data for the allowed scope.
- **Implementer confirms root cause** using backend logs (stack trace, exception message, and failing code path) before applying the fix.
- **Consistent user identifier**: Where the contract or API defines **userId** as numeric `app_user.id`, request parameters, scope resolution (e.g. allowedUserIds if aligned to numeric id), service SQL, and responses must use **user_id** (numeric) consistently. If the current implementation uses username in a path that should use user_id, it must be aligned to user_id; any wrong user_id/user_name mapping must be corrected.
- **Documentation**: Contract and api-definition (and specs/skills if the domain model changes) must reflect the chosen identifier semantics for activity statistics so that future changes stay consistent.

---

## 2. Design

### 2.1 Security review (optional)

This requirement fixes an error in scope=team statistics and may align activity statistics to numeric user_id. It does not expand PII or decryption scope. If the fix changes how scope=team is enforced (e.g. allowlist from username to numeric id), the implementer must ensure scope boundaries are not widened (only users in the same department remain in the allowlist). No formal Security subagent review is required unless the implementer identifies an access-control impact.

### Technical design

#### Codebase summary

- **Activity statistics flow**
  - **ActivityStatisticsController** applies scope (self/team/all) via `applyScopeForStatistics` and, for scope=team, calls `DepartmentScopeHelper.getUserIdsInSameDepartment(dataSource, currentUser)` which returns **usernames** (`List<String>`). This list is passed as `allowedUserIds` to **ActivityStatisticsService**.
  - The controller resolves request param **userId** (Long) to username via `resolveUserIdParam(userId)` → `appUserResolver.getUsernameById(userId)` before passing to the service. So the API accepts numeric `userId` and the service receives **username** (String) for the single-user filter.
  - **ActivityStatisticsService** uses `allowedUserIds` and `userId` (String) in SQL: `WHERE u.user_id IN (?,?,...)` or `WHERE u.user_id = ?`, with `user_activity_log u INNER JOIN app_user a ON u.user_id = a.username`. So **user_activity_log.user_id** is treated as **username** (VARCHAR); statistics scope=team is **username-based**.
  - **GET /api/statistics/users** and **GET /api/statistics/ips** also use `getUserIdsInSameDepartment` for scope=team and pass the resulting list to the service; the service filters by `u.user_id` (username).
- **Schema**
  - **user_activity_log** (`backend/src/main/resources/db/schema_user_activity_log.sql`): `user_id VARCHAR(100) NOT NULL` — stores **username**. There is no `department` column on `user_activity_log`; the service accepts a `department` parameter but **does not** add it to the WHERE clause (see comment in service: user_activity_log에 department 컬럼 없음).
- **DepartmentScopeHelper**
  - `getUserIdsInSameDepartment(DataSource, String username)` returns **List&lt;String&gt;** (usernames in same department).
  - `getNumericUserIdsInSameDepartment(DataSource, Long userId)` returns **List&lt;Long&gt;** (numeric `app_user.id` in same department); used by **search-history** (req 20260316), not by activity statistics.
- **Search-history (for comparison)**
  - Search-history uses **numeric** `app_user.id` everywhere: `search_history.user_id` is BIGINT, scope=team uses `DepartmentScopeHelper.getNumericUserIdsInSameDepartment`, and list/filter use numeric id. Activity statistics was **not** in that scope and remains **username-based** in the current codebase.
- **Contract / API**
  - `docs/contract.md` and `docs/api-definition.md`: Statistics API query params include `userId` (number, `app_user.id`). Response from `/api/statistics/users` returns `userId` (numeric) and `userName` (string). Self-context and filter semantics use **numeric** `app_user.id` as canonical userId.

#### Problem analysis

1. **Observed failure** — When the user’s permission group has statistics with **department scope** (scope=team), an error occurs. The exact cause is not confirmed in this doc; the implementer **must check backend logs** (console or log files) for the exception when statistics are requested with scope=team (e.g. calls to `/api/statistics/users`, `/api/statistics/ips`, `/api/statistics/activity/daily`, or filter-options), and use that to confirm root cause.
2. **Possible causes (to be verified against logs)**
   - **Identifier mismatch**: A code path might pass **numeric user_id** where the service or SQL expects **username** (e.g. comparing to `user_activity_log.user_id` VARCHAR), or the opposite, leading to SQL or type errors or wrong results.
   - **Null or invalid current user**: For scope=team, `userInfo.getUsername()` might be null or blank in some auth/approver-group context, causing `getUserIdsInSameDepartment` to receive an invalid input or the controller to throw before calling the service.
   - **Department filter not applied**: The statistics service accepts `department` but does **not** add it to the WHERE clause (no department column on `user_activity_log`). If the UI or contract implies filtering by department and some path assumes department is applied, behavior may be inconsistent or a different code path may fail when department is present.
   - **Exception in DepartmentScopeHelper or AppUserResolver**: SQLException or NPE in `getUserIdsInSameDepartment` or in `getUsernameById` when resolving request params could propagate as 500 or bad response.
3. **User’s alignment request** — The user asked that if user_id and user_name are wrongly mapped, the system should **align to user_id** (numeric `app_user.id`). So the solution must either: (a) fix only the immediate error (if the cause is a local bug, e.g. null handling or wrong type in one path), or (b) align activity statistics to use **numeric user_id** for scope and filtering (similar to search-history), which may require schema change (`user_activity_log.user_id` or an additional column), migration, and controller/service changes. The implementer must choose based on root cause and product decision; the requirement is that **any** wrong user_id/username mapping be corrected and, where appropriate, aligned to user_id.

#### Solution approach

- **Backend**
  1. **Confirm root cause**: Check backend logs for the exact exception and stack trace when statistics are requested with scope=team or department filter. Document the failing endpoint and cause in §6 (Error remedy result) or in the implementation.
  2. **Fix the error**: Depending on cause: (a) fix null/blank username handling for scope=team; (b) ensure all values passed to the service for `user_id` filtering are of the type expected by the current schema (username for VARCHAR `user_activity_log.user_id`); (c) if aligning to numeric user_id, introduce numeric id for scope/allowlist and either migrate `user_activity_log.user_id` to store numeric id (with migration and JOIN change) or add a separate mechanism (e.g. resolve allowlist to usernames only at service boundary) so that SQL remains valid. If department filter is required by contract, implement department-based filtering (e.g. via JOIN with app_user/department) and document that `user_activity_log` has no department column so filtering is done via user list.
  3. **Align to user_id where appropriate**: If the decision is to align activity statistics to numeric user_id: use `DepartmentScopeHelper.getNumericUserIdsInSameDepartment` for scope=team and pass numeric ids to the service; change the service to filter by numeric id (which requires schema change: `user_activity_log.user_id` as BIGINT referencing `app_user.id`, or a new column, plus migration). If the decision is to keep username-based storage, ensure request param `userId` (Long) is always resolved to username before use, and that no path passes Long to WHERE on `user_activity_log.user_id`; fix any wrong mapping in response (e.g. ensure userId in response is numeric and userName is display name).
  4. **Logging**: Add or adjust logs (without PII) so that scope=team and department-filter paths are traceable for future diagnosis.
- **DB**
  - Only if the solution includes **storing or filtering by numeric user_id** in activity statistics: add migration and schema change (e.g. `user_activity_log.user_id` to BIGINT, or new column + backfill), and update setup/scripts. Otherwise no DB change.
- **Frontend**
  - No change required unless the fix changes API request/response shape or error codes; then update API client and error handling to match contract.
- **Contract / Spec**
  - Update `docs/contract.md` and `docs/api-definition.md` if activity statistics identifier semantics change (e.g. scope=team allowlist defined as numeric ids, or clarification that statistics still use username internally). Keep docs in sync with implementation.

#### Affected scopes and change targets (verification)

Per `docs/workflow/REQUIREMENTS-CHANGE-TARGET-CHECKLIST.md`:

| Scope | Affected? | §2 subsection and change file list complete? |
|-------|-----------|-----------------------------------------------|
| Backend | Yes | Yes |
| Frontend (config UI + view screen) | Only if API/error handling changes | Yes (conditional) |
| DB | Only if schema/migration for user_id alignment | Yes (conditional) |
| Contract / Spec | Yes (identifier semantics or error behavior) | Yes |
| Cursor tools (skills, specs) | Yes if domain model (statistics identifier) changes | Yes |

This requirement matches **domain pattern 3.1 (Scope-supporting screen)** and **3.3 (API or error-code change)**. Touchpoints: scope resolution, controller/service filtering, contract/spec, and optionally DB and skills. Pattern **3.4 (Search/filter UI consistency)** does not apply; no §2.4 verification table required.

### Planned change file list (expected change targets)

**(Planned at authoring. Implementing agent (Step 4) confirms or amends this list when implementation is complete.)**

#### Backend

- `backend/src/main/java/com/logmng/controller/ActivityStatisticsController.java`
  - Must ensure scope=team path does not throw; verify or fix current-user resolution and DepartmentScopeHelper usage; if aligning to numeric user_id, use getNumericUserIdsInSameDepartment and pass numeric allowlist (service must then support it).
- `backend/src/main/java/com/logmng/service/ActivityStatisticsService.java`
  - Must use allowedUserIds and userId in a way consistent with schema and contract; if schema remains username-based, accept only username(s); if aligned to numeric, accept numeric allowlist and filter via JOIN on app_user.id; fix any wrong userId/userName mapping in response; add department filter to WHERE if contract requires it (via JOIN app_user/department).
- `backend/src/main/java/com/logmng/util/DepartmentScopeHelper.java`
  - No change if statistics stay username-based; already has getNumericUserIdsInSameDepartment for search-history. If activity statistics aligns to numeric id, controller will use existing getNumericUserIdsInSameDepartment.
- `backend/src/test/java/com/logmng/controller/ActivityStatisticsControllerTest.java` (and related service tests)
  - Must add or extend tests for scope=team statistics (user list, IP list, daily/monthly) so that the error is covered and regression prevented; verify user_id/username semantics per contract.

#### DB

- Only if solution includes numeric user_id for activity statistics:
  - `backend/src/main/resources/db/schema_user_activity_log.sql` (or new migration file)
  - Migration script and setup/apply scripts; init-data if needed.

#### Contract / Spec

- `docs/contract.md`
  - Must state activity statistics scope=team and user filter semantics (username vs numeric user_id) consistent with implementation.
- `docs/api-definition.md`
  - Must state statistics API params and response (userId, userName) and any error behavior clarified by the fix.

#### Frontend

- Only if API request/response or error codes change:
  - `frontend/src/services/api.js` (or statistics API client)
  - Update params or response handling; error handling if new codes or behavior.

#### Cursor tool update targets

- If activity statistics identifier semantics change (e.g. scope=team uses numeric id):
  - `.cursor/skills/activity-statistics-domain/SKILL.md` — Update to describe scope=team allowlist and user filter by user_id (numeric) or username as implemented.
- No change to rules or commands unless workflow for statistics changes.

---

## 3. Test approach

### Test case list (required)

| ID | Scope | Type | Scenario (input / condition) | Expected result | Verification (unit / integration / manual) |
|----|-------|------|------------------------------|-----------------|--------------------------------------------|
| TC-01 | Backend | Normal | User with statistics scope=team calls GET /api/statistics/users | 200; list of users in same department; no exception | Unit (mvn test) or Integration |
| TC-02 | Backend | Normal | Same user calls GET /api/statistics/ips | 200; list of IPs for users in same department; no exception | Unit or Integration |
| TC-03 | Backend | Normal | Same user calls GET /api/statistics/activity/daily (with or without filters) | 200; daily statistics for scope=team; no exception | Unit or Integration |
| TC-04 | Backend | Normal | Same user calls GET /api/statistics/activity/monthly | 200; monthly statistics for scope=team; no exception | Unit or Integration |
| TC-05 | Backend | Edge | scope=team and current user has no department (or null username) | No 500; empty or singleton list per design; no uncaught exception | Unit |
| TC-06 | Backend | Regression | Request param userId (numeric) passed to statistics API | Resolved to username for current schema, or used as numeric per design; correct filtering and response userId/userName | Unit |
| TC-07 | Integration | Normal | Permission group with statistics scope=team: open statistics screen, load user list and run a statistics query | No server error; data consistent with department scope | Manual / browser or Integration |
| TC-08 | Backend | Regression | Response from /api/statistics/users: userId is numeric, userName is display name | Matches contract; no swapped or wrong mapping | Unit |

### Test scenarios

#### Scenario 1: Scope=team statistics (approver group / department)

1. Set up a user whose permission group has the statistics screen with scope=team (부서단위).
2. Log in as that user and open the activity statistics screen.
3. Trigger loading of user list, IP list, and daily or monthly statistics.
4. **Verification**: All requests return 200 (or documented error for invalid input); no 500; data limited to same department when scope=team.

#### Scenario 2: user_id / user_name mapping

1. Call GET /api/statistics/users (scope=team or all).
2. **Verification**: Response items have `userId` (number, app_user.id) and `userName` (string, display name); use one `userId` as query param for GET /api/statistics/activity/daily; backend must filter correctly (no type error, correct result set).

### Test data

- At least two users in the same department (same department_code in app_user) and one user in another department.
- Permission group that grants statistics screen with scope=team to the first user.
- user_activity_log rows with user_id (username or numeric per current schema) for those users so that statistics queries return non-empty data where expected.

### Test environment

- Frontend: per contract (e.g. http://localhost:3001)
- Backend: per contract (e.g. http://localhost:9200)
- Database: PostgreSQL per project setup

### 3.5 Browser automation verification (optional)

- **Applicable TCs**: TC-07.
- **Procedure**: Log in as user with statistics scope=team, navigate to statistics screen, trigger user list and statistics load, capture snapshot and network/response; confirm no 5xx and data present. Reference: `docs/workflow/BROWSER-AUTOMATION-VERIFICATION-POLICY.md`.

---

## 4. Checklist

### Frontend verification

- [ ] API parameters and response handling aligned with contract (if API changed)
- [ ] Error handling verified for statistics endpoints (if error behavior changed)

### Backend verification

- [ ] Root cause confirmed from backend logs and documented
- [ ] API test cases written and run (scope=team and user_id mapping)
- [ ] No uncaught exception in scope=team or department-filter path

### Integration

- [ ] End-to-end flow tested with permission group scope=team (statistics)
- [ ] user_id vs user_name mapping verified in request and response

### Documentation

- [ ] Requirement doc completed
- [ ] Contract and api-definition updated if identifier semantics or errors changed

---

## 5. Test results

### Test run date

- 2026-03-17

### Test results

#### Backend

Pass

- ActivityStatisticsControllerTest (including ScopeTeam: getUsers, getIps, getDaily, getMonthly with scope=team → 200)
- ActivityStatisticsServiceTest (getUsers/getIps empty allowlist; getUsers userId/userName mapping)

#### Integration

- Not run in this step (QA/verification per workflow).

**Commands:**

```bash
cd backend && mvn test -q -Dtest=ActivityStatisticsControllerTest,ActivityStatisticsServiceTest
```

### Issues found and resolution

[To be filled after implementation and test run.]

### Next steps

1. Implementer checks backend logs and confirms root cause.
2. Apply fix and run TC-01–TC-08; record results in §5.
3. Update §6 (Error remedy result) with root cause and actions.

---

## 6. Error remedy result (cause and action) — for error/bug fix requirements only

- **Requirement ID**: 20260317-activity-statistics-department-approver-error
- **Root cause**: When scope=team, `DepartmentScopeHelper.getUserIdsInSameDepartment` can return an **empty list** (e.g. null/blank current username or user has no department). The service treated **empty allowedUserIds** as “no filter” and returned **all users’ data**, causing wrong scope (data leak) or downstream errors. No type mismatch was found: activity statistics remain username-based (`user_activity_log.user_id` = username); the bug was empty-allowlist handling only.
- **Actions taken**:
  - **ActivityStatisticsService**: (1) `getUsers(userIdFilter, allowedUserIds)`: when `allowedUserIds != null && allowedUserIds.isEmpty()`, return empty list immediately (no query). (2) `getIps(...)`: same early return for empty allowedUserIds. (3) `buildDailyMonthlyWhere`: when `allowedUserIds != null && allowedUserIds.isEmpty()`, add `AND 1=0` so daily/monthly queries return no rows. This keeps scope=team with “no department” or null username safe without schema change.
  - **ActivityStatisticsController**: For `getUsers` and `getIps`, when scope=team and `userInfo.getUsername()` is null or blank, set `allowedUserIds = Collections.emptyList()` explicitly instead of calling `getUserIdsInSameDepartment` with null (defensive; helper already returns empty for null).
  - **Tests**: Added `ActivityStatisticsServiceTest` (getUsers/getIps empty allowlist; getUsers with one user → userId numeric, userName display). Extended `ActivityStatisticsControllerTest` with nested `ScopeTeam` (TC-01–TC-04: GET /api/statistics/users, /ips, /activity/daily, /activity/monthly with scope=team → 200). Fixed pre-existing `SearchHistoryControllerTest` stub (throw Throwable → wrap in RuntimeException) for compilation.
- **Result**: `cd backend && mvn test -q -Dtest=ActivityStatisticsControllerTest,ActivityStatisticsServiceTest` — all tests pass. Scope=team with empty allowlist now returns empty data (no 500, no leak). Identifier semantics unchanged: request param `userId` (numeric) is still resolved to username for filtering; response `userId`/`userName` remain numeric id and display name per contract.
- **Completed**: 2026-03-17 (Backend implementer)

---

**Author**: Requirements subagent  
**Date**: 2026-03-17  
**Status**: In progress
