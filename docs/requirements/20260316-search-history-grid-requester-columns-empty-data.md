# 20260316 - Search history grid requester columns empty (root-cause analysis)

## 1. Defect summary

**Defect:** On the search history screen, the grid columns **부서**, **사용자ID**, **사용자명** appear (after 20260316-search-history-grid-columns-order) but show **no data** — cells are empty.

**Scope of this doc:** Root-cause analysis (frontend-focused). No implementation until this requirement is updated and implementation is delegated.

---

## 2. Root-cause analysis report

### 2.1 Current frontend column definition and value source

**File:** `frontend/src/components/SearchHistory/SearchHistoryList.js`

| Column label | Column `key` (SEARCH_HISTORY_COLUMNS) | Where cell value is read from | Notes |
|--------------|----------------------------------------|-------------------------------|--------|
| 부서         | `requesterDepartment`                  | `getRequesterCellValues(row)` → `department` | Not from `row.requesterDepartment` |
| 사용자ID     | `requesterUsername`                    | `getRequesterCellValues(row)` → `requesterUsername` | Not from `row[column.key]` |
| 사용자명     | `requesterDisplayName`                 | `getRequesterCellValues(row)` → `requesterDisplayName` | Not from `row[column.key]` |

- **Row render path:** `list.map((row) => { const { department, requesterUsername, requesterDisplayName } = getRequesterCellValues(row); return (<tr>... <td>{department}</td> <td>{requesterUsername}</td> <td>{requesterDisplayName}</td> ... })`. The table body is **not** rendered by DataTable using `column.key`; it is fully controlled by this explicit map. So the three requester cells always use the return value of `getRequesterCellValues(row)`.
- **Data source for list:** `setList(result.data.data || [])` — each `row` is an element of the list API response `data.data` array (no frontend normalization of row shape).

**getRequesterCellValues(row)** (lines 49–60):

- **부서:** `department` = `requesterDepartmentName` or `requester_department_name` (trimmed) if non-empty; else `requesterDepartmentCode` or `requester_department_code` or `requesterDepartment`; else `'—'`.
- **사용자ID:** `requesterUsername` = `requesterUsername` or `requester_username` (string) or `'—'`.
- **사용자명:** `requesterDisplayName` = `requesterDisplayName` or `requester_display_name` (trimmed) if non-empty; else username; else `'—'`.

So the frontend **never** reads `row.requesterDepartment` (column key) for the 부서 cell; it reads `requesterDepartmentName` / `requesterDepartmentCode` (and snake_case variants). Column keys are used only for headers and sort; they are **not** used to render body cells.

### 2.2 What the list API is specified to return

**Ref:** `docs/api-definition.md` §6.1 (GET /api/search-history), `docs/requirements/20260316-search-history-grid-columns-order.md`, `.cursor/skills/search-history-decrypt-domain/SKILL.md`.

- List response: `data` array; each item must include (among others): `requesterDepartmentCode`, `requesterDepartmentName`, `requesterDisplayName`, `requesterUsername` (camelCase).
- Backend implementation (`SearchHistoryService.list()`): Builds `Map<String, Object>` per row with `row.put("requesterDepartmentCode", ...)`, `row.put("requesterDepartmentName", ...)`, `row.put("requesterDisplayName", ...)`, `row.put("requesterUsername", ...)` and returns `SearchHistoryListResponse(results, pagination)`. No DTO per row; Jackson serializes the list of maps with keys as-is (camelCase). FROM clause joins `app_user` and `department` so requester columns are selected.

So **by spec and current backend code**, the API should return camelCase requester fields per row. The frontend supports both camelCase and snake_case in `getRequesterCellValues`.

### 2.3 Why cells could appear empty

- **Frontend logic:** `getRequesterCellValues` always returns three **strings** (value or `'—'`). It does not return `undefined`. So `<td>{department}</td>`, `<td>{requesterUsername}</td>`, `<td>{requesterDisplayName}</td>` should never render “nothing” unless the destructured variables were not from `getRequesterCellValues` or the component tree is wrong — and the code shows they are from that function.
- **Realistic causes for empty-looking cells:**
  1. **API response does not contain requester fields** in each row (e.g. different code path, different environment, or list response built without the requester SELECT/JOIN or without putting those keys in the map). Then `row.requesterDepartmentName`, `row.requesterUsername`, etc. are all `undefined`, and `getRequesterCellValues` would still return `'—'` for each. So strictly “empty” (blank) would not occur unless:
     - There is another consumer that renders by `row[column.key]` (e.g. `row.requesterDepartment`) — **not the case** in SearchHistoryList; or
     - The user reports “empty” as in “no real data” (i.e. they see “—”) — then the root cause is **backend not populating** requester fields (or not returning them).
  2. **Response shape different from expected:** e.g. rows under another key, or requester data nested (e.g. `row.requester.username`). Then the frontend would read `undefined` and show “—”. Again, that points to contract/backend or a need to adapt the frontend to the actual shape.
  3. **Snake_case only with different names:** Frontend already handles `requester_department_name`, `requester_department_code`, `requester_username`, `requester_display_name`. If the backend used different snake_case names, those would not be read — fix would be either backend to match or frontend to add fallbacks.

**Conclusion:** From the current frontend code alone, the three requester columns are wired to `getRequesterCellValues(row)` and should display at least “—”. True blank cells are only plausible if (a) the row object does not have the expected requester keys and the fallbacks still yield something (they yield “—”), or (b) there is a different code path or build that does not use `getRequesterCellValues`. The most likely explanation for “no data” (empty or “—”) is that **the list API response does not include the requester fields** in the rows (backend/contract), or they are under a different shape (contract/frontend adaptation).

### 2.4 Recommended next steps and fix ownership

1. **Confirm actual API response (recommended first step)**  
   - Call `GET /api/search-history` (e.g. from browser network tab or curl) and inspect one response row.  
   - Check that each item in `data.data` has: `requesterDepartmentName` or `requesterDepartmentCode`, `requesterUsername`, `requesterDisplayName` (or their snake_case equivalents).  
   - If these keys are **absent** or under a different path (e.g. nested object): **Backend (or contract) fix** — ensure the list endpoint returns requester fields per `docs/api-definition.md` and that the frontend receives the same shape (or document the actual shape and adapt frontend in one place, e.g. a normalizer or inside `getRequesterCellValues`).

2. **If the API does return requester fields (camelCase or snake_case) on each row**  
   - Frontend should already show values or “—”. Then check for middleware or wrappers that might mutate `result.data.data`, or for a different bundle/env (e.g. old build).  
   - If the only issue is “— everywhere” because all values are null, the fix is **Backend** (populate from JOIN / correct columns).

3. **Concrete fix recommendations**
   - **If backend does not send requester fields in list response:**  
     - **Backend:** Ensure `SearchHistoryService.list()` SELECT includes requester columns and that the built `Map` per row includes `requesterDepartmentCode`, `requesterDepartmentName`, `requesterDisplayName`, `requesterUsername` (and that the same response is returned in the environment where the defect is seen).  
     - **Frontend:** No change required for key names; optional: add a single normalizer that maps actual response shape to the shape expected by `getRequesterCellValues` (e.g. if backend later returns `requester: { departmentName, username, displayName }`).
   - **If backend sends snake_case only and frontend still misses:**  
     - **Frontend:** Extend `getRequesterCellValues` with the exact snake_case keys observed in the response (already partially done).  
   - **If backend sends requester in a nested object:**  
     - **Frontend-only fix:** In `getRequesterCellValues(row)`, read from e.g. `row.requester` and then from `requester.departmentName`, `requester.username`, etc., with fallbacks to current top-level keys so both shapes are supported during transition.

### 2.5 Summary table

| Item | Detail |
|------|--------|
| Column keys (header/sort) | 부서: `requesterDepartment`, 사용자ID: `requesterUsername`, 사용자명: `requesterDisplayName` |
| Cell value source | Always `getRequesterCellValues(row)` → `department`, `requesterUsername`, `requesterDisplayName` (not `row[column.key]`) |
| API contract | Per row: `requesterDepartmentName`, `requesterDepartmentCode`, `requesterDisplayName`, `requesterUsername` (camelCase) |
| Backend implementation | Service builds map with those camelCase keys; JOIN to app_user and department |
| Root cause (most likely) | List API response in the affected environment does not include requester fields in each row (backend/contract) or uses a different shape (nested/snake_case) |
| Recommended fix | (1) Verify actual response shape. (2) If keys missing or wrong shape → Backend (and/or contract) to return requester per spec; optionally Frontend normalizer. (3) If only null values → Backend to populate; Frontend already shows “—”. |

### 2.6 Planned change file list (implementation)

| File | Change |
|------|--------|
| `backend/src/main/java/com/logmng/service/SearchHistoryService.java` | Add `sh.user_id AS "shUserId"` to list SELECT; in row mapping, when `requesterUsername` (from JOIN) is null/blank set it from `shUserId`; when `requesterDisplayName` is null/blank set from same effective username. Department fields left as null when JOIN fails. |
| `backend/src/test/java/com/logmng/service/SearchHistoryServiceTest.java` | Add test `list_whenAppUserJoinDoesNotMatch_populatesRequesterUsernameAndDisplayNameFromShUserId` (row with `user_id` not in `app_user` still has requesterUsername/requesterDisplayName from `sh.user_id`). |

### 2.7 Root cause and fix summary

- **Root cause:** The list query uses `LEFT JOIN app_user au ON au.username = sh.user_id`. When `search_history.user_id` does not exist in `app_user.username` (e.g. user removed or data inconsistency), the JOIN yields null for all `au.*` and `d.*` columns, so the API returned null for `requesterUsername`, `requesterDisplayName`, and department fields. The grid then showed empty or "—" for 사용자ID/사용자명/부서.
- **Fix:** Backend-only. (1) Select `sh.user_id` in the list query. (2) When mapping each row, if `requesterUsername` is null or blank, set it to `sh.user_id`; if `requesterDisplayName` is null or blank, set it to that same effective username. Department columns remain null when the JOIN does not match; frontend already shows "—" for those.

---

## 3. Test approach (for when fix is implemented)

- **TC-1:** Call GET /api/search-history and assert one row has `requesterDepartmentCode` or `requesterDepartmentName`, `requesterUsername`, `requesterDisplayName` (or agreed snake_case/nested equivalent).
- **TC-2:** Open search history screen with at least one row; assert 부서, 사용자ID, 사용자명 cells are not blank (value or “—”).
- **TC-3:** If a normalizer is added, unit-test it with the actual API response shape.
- **TC-4 (Backend unit):** When `app_user` LEFT JOIN does not match (e.g. `search_history.user_id` not in `app_user.username`), list row still has `requesterUsername` and `requesterDisplayName` set from `sh.user_id` (SearchHistoryServiceTest).

---

## 4. References

- `frontend/src/components/SearchHistory/SearchHistoryList.js` (SEARCH_HISTORY_COLUMNS, getRequesterCellValues, list map render)
- `docs/api-definition.md` §6.1 (GET /api/search-history list response)
- `backend/src/main/java/com/logmng/service/SearchHistoryService.java` (list(), SELECT and row map build)
- `docs/requirements/20260316-search-history-grid-columns-order.md` (column layout and data source)
- `.cursor/skills/search-history-decrypt-domain/SKILL.md` (list/grid requester columns)

---

---

## 5. Test results

| Run | Command | Result |
|-----|--------|--------|
| Backend unit | `cd backend && mvn test` | **PASS** (exit 0). All tests including `SearchHistoryServiceTest.list_whenAppUserJoinDoesNotMatch_populatesRequesterUsernameAndDisplayNameFromShUserId` pass. |

---

## 6. Error remedy result

- **Root cause:** List API builds requester fields from `LEFT JOIN app_user` / `department`. When `search_history.user_id` has no matching `app_user.username`, JOIN columns are null and the response had null `requesterUsername`/`requesterDisplayName`, so the grid showed empty/"—" for 사용자ID·사용자명.
- **Fix applied:** In `SearchHistoryService.list()`, added `sh.user_id AS "shUserId"` to the SELECT and, when mapping each row, set `requesterUsername` to JOIN value or `shUserId` when null/blank; set `requesterDisplayName` to JOIN value or that effective username. Department fields unchanged (can remain null; frontend shows "—").
- **Verification:** Unit test added for the no-match case; full `mvn test` passes. QA to verify on search history screen (restart + health check per handoff).

---

**Author:** Frontend subagent (root-cause analysis only)  
**Date:** 2026-03-16  
**Status:** Analysis complete; Backend fix implemented; §5/§6 recorded. QA verification and commit pending.
