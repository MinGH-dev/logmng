# Root-cause analysis: Search history grid — empty 부서, 사용자ID, 사용자명

**Scope:** Backend list API and SearchHistoryService only. Analysis only; no implementation.

**Defect:** After the previous fix (populate requesterUsername/requesterDisplayName from `sh.user_id` when JOIN fails), the search history grid **still** shows no data in 부서, 사용자ID, 사용자명. User asks if the cause is that **existing search history does not store that information**.

---

## 1. List response — current code behaviour

**File:** `backend/src/main/java/com/logmng/service/SearchHistoryService.java`, method `list()`.

### 1.1 SELECT and `sh.user_id`

- The list SQL **does** select `sh.user_id` and exposes it as an alias:
  - Line 151–155:  
    `SELECT sh.id, sh.user_id AS "shUserId", au.id AS "userId", au.department_code AS "requesterDepartmentCode", d.name AS "requesterDepartmentName", au.name AS "requesterDisplayName", au.username AS "requesterUsername", ...`
- So **(a) `sh.user_id` is in the SELECT** as `"shUserId"`.

### 1.2 Row map and fallback when JOIN is null/blank

- For each row (lines 166–177):
  - `String shUserId = rs.getString("shUserId");`
  - `String reqUsername = rs.getString("requesterUsername");`
  - `String reqDisplayName = rs.getString("requesterDisplayName");`
  - `String effectiveUsername = (reqUsername != null && !reqUsername.isBlank()) ? reqUsername : shUserId;`
  - `row.put("requesterDisplayName", (reqDisplayName != null && !reqDisplayName.isBlank()) ? reqDisplayName : effectiveUsername);`
  - `row.put("requesterUsername", effectiveUsername);`
- So **(b) when the JOIN result is null/blank**, `requesterUsername` and `requesterDisplayName` are set from `shUserId`. The condition is correct and the code path is reached for every row.

### 1.3 부서 (department)

- `requesterDepartmentCode` and `requesterDepartmentName` are taken only from the JOIN (`au`, `d`). There is **no fallback** for department when the JOIN fails (by design: `search_history` does not store department). So when LEFT JOIN finds no matching `app_user`, 부서 will always be empty.

### 1.4 Unit test

- `SearchHistoryServiceTest.list_whenAppUserJoinDoesNotMatch_populatesRequesterUsernameAndDisplayNameFromShUserId()` (lines 339–360) verifies:
  - For a row with no matching `app_user` (“orphan-user”), `requesterUsername` and `requesterDisplayName` are set from `sh.user_id` (“orphan-user”), and `requesterDepartmentCode` / `requesterDepartmentName` are null.
- So the fallback behaviour is **correct and covered by tests**.

**Conclusion for list:** The list logic is correct. 사용자ID and 사용자명 will be empty **only if** `shUserId` (i.e. `sh.user_id`) is null or blank for that row. 부서 is empty whenever the JOIN fails (no app_user), with no fallback by design.

---

## 2. Insert path — where `search_history.user_id` comes from

**Create flow:**

- Controller: `SearchHistoryController.create()` (lines 107–121).
  - `String userId = getCurrentUsername(httpRequest);` → from `AuthService.getCurrentUserInfo(request).getUsername()`.
  - `searchHistoryService.create(userId, request);`
- Service: `SearchHistoryService.create(String userId, ...)` (lines 50–95).
  - INSERT: `user_id` is set with `ps.setString(1, userId)` (line 72).

So **new** search history rows get `user_id` from the **current logged-in user (username)**. There is no other application code path that inserts into `search_history`; the only INSERT is in `SearchHistoryService.create()`.

**Schema:** `search_history.user_id` is `VARCHAR(100) NOT NULL` in `schema.sql` and `migrate-search-history.sql`. So new inserts cannot store NULL. They could still store an **empty string** if `userId` were ever "" (controller returns 401 when `userId` is null or blank, so normally that should not happen for create).

**Conclusion for insert:** New rows are always created with `user_id` set from the current user. Empty requester columns are **not** explained by the current insert path; they point to **existing** data that was created before this was enforced, or with null/empty `user_id` by some other means (e.g. manual DB insert, migration, or old code path).

---

## 3. Root cause

| Cause | Description |
|-------|-------------|
| **(A) Data** | Existing rows in the DB have **null or empty** `search_history.user_id`. Then `shUserId` is null/blank, so the fallback still yields empty 사용자ID and 사용자명. 부서 stays empty when there is no matching `app_user` (no fallback for department). |
| **(B) Code** | Fallback not applied (e.g. wrong column name, shUserId not selected, or not put in the response map). **Ruled out:** SELECT has `shUserId`, row map uses it in `effectiveUsername`, and the unit test confirms the behaviour. |
| **(C) Other** | E.g. frontend not mapping response fields, or a different API/endpoint. Out of scope for this backend-only analysis. |

**Conclusion:** The remaining empty-grid behaviour is **root cause (A) — data**. Existing search history rows likely have **null or empty `user_id`**. The list code and fallback are correct and tested; when `sh.user_id` has a value, 사용자ID and 사용자명 are filled from it when the JOIN fails.

---

## 4. Recommendation

### 4.1 No backend code change required for the fallback

The current list implementation and fallback logic are correct. No change is needed in `SearchHistoryService.list()` for the requester columns when `user_id` is present.

### 4.2 Data remediation (recommended)

1. **Inspect the database**
   - Run:  
     `SELECT id, user_id, LENGTH(COALESCE(user_id,'')) AS len FROM search_history;`
   - Check for rows where `user_id` is NULL or `TRIM(user_id) = ''`.

2. **If such rows exist**
   - **Option A (preferred):** Backfill only where possible (e.g. infer requester from other data or leave as “unknown” and document). If there is no way to infer, leave `user_id` as-is but document that those rows will show empty requester in the grid.
   - **Option B:** Add a one-off script or migration that sets `user_id` to a sentinel value (e.g. `'(unknown)'`) for NULL/empty, so the grid at least shows something. Coordinate with product/security before introducing a new sentinel.
   - **Option C:** Do not backfill; accept that old rows without `user_id` will show empty 부서/사용자ID/사용자명. Ensure all **new** rows continue to get `user_id` from the current user (already the case).

3. **Document**
   - In a requirement or runbook: that search history list shows requester from `app_user` when JOIN matches, and from `search_history.user_id` when it does not; and that rows with null/empty `user_id` will have empty requester columns. Document any backfill or sentinel value used.

### 4.3 If the frontend or API contract is in scope later

- Confirm the list API response shape includes `requesterUsername`, `requesterDisplayName`, `requesterDepartmentCode`, `requesterDepartmentName` and that the frontend maps them to the grid columns “사용자ID”, “사용자명”, “부서”. This analysis did not verify the frontend or the exact API contract field names.

---

## 5. Summary table

| Item | Finding |
|------|--------|
| **List: sh.user_id in SELECT?** | Yes, as `sh.user_id AS "shUserId"`. |
| **List: fallback when JOIN null/blank?** | Yes; `effectiveUsername = reqUsername else shUserId`, and both requesterUsername and requesterDisplayName use it. |
| **List: condition bug?** | No; fallback is applied for every row. |
| **Insert: user_id source?** | Current user: `getCurrentUsername(httpRequest)` → `create(userId, request)`. New rows always have `user_id` set. |
| **Root cause** | **(A) Data:** existing rows with null/empty `user_id`; fallback has nothing to show. |
| **Code fix needed?** | No. Recommend data check and, if needed, backfill or sentinel and documentation. |
