# 20260316 - Search history grid: department name and user name in result grid

## 1. User requirement

### Requirement description

On the **search history** (검색 이력) result grid, the user requests that **department and user name** (부서와 사용자명) be shown clearly.

**Current state (from requirement 20260316-search-history-grid-requester-and-modal):**

- The grid has a “검색한 사용자 정보” column that shows requester information in one cell: **department code** (e.g. `TEAM_SALES_A1`), **display name** (사용자명), and **login ID** (사용자 ID), formatted as “dept / name / uid” via `RequesterInfoCell` in `SearchHistoryList.js`.
- The backend list API returns `requesterDepartmentCode` (from `app_user.department_code`), `requesterDisplayName` (`app_user.name` or `username`), and `requesterUsername` (`app_user.username`). There is **no join to the `department` table**, so the grid does not show the department **name** (e.g. “영업1팀”).

**Scope clarification:**

- **(a) Department name**: Show the department **display name** (from the `department` table, e.g. “영업1팀”) in the grid, in addition to or instead of the department **code**. When department name is unavailable (e.g. no matching department row), fall back to the existing department code so the column still shows something meaningful.
- **(b) Visibility of department and user name**: Keep both “부서” and “사용자명” clearly visible in the grid. The existing single column with the three axes (부서 → 사용자명 → 사용자 ID) per `docs/design/search-fields-by-screen.md` §4 is retained; the improvement is to show **department name** (when available) and to keep **user name** (사용자명) and **user ID** (로그인 ID) in the same semantic order. No requirement for separate columns unless product confirms.

**Primary interpretation:** Implement **(a)** so the grid shows **department name** (e.g. “영업1팀”) when the backend can resolve it from the `department` table, with fallback to department code; and ensure **(b)** by keeping the current single “검색한 사용자 정보” column with clear ordering: department (name or code) → 사용자명 → 사용자 ID.

### User scenario

1. User opens the search history screen and sees the list of search-history rows.
2. In the “검색한 사용자 정보” column, the user expects to see **department name** (e.g. “영업1팀”) when the requester’s department has a name in the department master, instead of only the department code (e.g. `TEAM_SALES_A1`).
3. When the requester’s department has no matching row in the department table (or name is null), the cell shows the department code so the user can still identify the department.
4. The column continues to show **사용자명** (display name) and **사용자 ID** (login id) in the same cell, in the order 부서 → 사용자명 → 사용자 ID, so “부서와 사용자명” are both clearly visible.

### Expected outcome

- **Grid column “검색한 사용자 정보”**: For each row, the cell shows (1) **department**: department **name** (from `department.name`) when available, otherwise department **code** (`requesterDepartmentCode`); (2) **사용자명** (`requesterDisplayName`); (3) **사용자 ID** (`requesterUsername`). Format remains a single cell with the three values in order (e.g. “영업1팀 / 홍길동 / user1” or “TEAM_SALES_A1 / user1 / user1” when name is missing). Semantics align with the requester block in `docs/design/search-fields-by-screen.md` §4 (부서, 사용자명, 사용자 ID).
- **API**: The search history list response includes a new optional field **`requesterDepartmentName`** (string | null) so the frontend can display the department name. Existing fields `requesterDepartmentCode`, `requesterDisplayName`, and `requesterUsername` remain unchanged.
- **Backward compatibility**: When the backend does not return `requesterDepartmentName` (e.g. older clients), the frontend continues to use `requesterDepartmentCode` only, so behavior degrades gracefully.
- **Documentation and tools**: Contract and API docs are updated first; Cursor skills that describe the list response shape are updated so agents have correct domain knowledge. Then backend and frontend changes follow in delegation order.

---

## 2. Design

### 2.1 Security review (optional)

- Not required. No new PII or access control; only adding a display field (department name) already derivable from existing data (department code → department table).

### Technical design

#### Codebase summary

- **Backend — SearchHistoryService.java**
  - `list(SearchHistoryListRequest)` builds the list with `FROM search_history sh LEFT JOIN app_user au ON au.username = sh.user_id`. The SELECT includes `au.department_code AS "requesterDepartmentCode"`, `au.name AS "requesterDisplayName"`, `au.username AS "requesterUsername"`. There is **no join to the `department` table**, so department **name** is not available in the list response.
  - `app_user` has `department_code` (FK to `department.code`). Table `department` has `code`, `parent_code`, `name`, `sort_order` (`backend/src/main/resources/db/schema.sql`).
- **Frontend — SearchHistoryList.js**
  - `RequesterInfoCell` renders one cell: `dept` = `row.requesterDepartmentCode ?? row.requesterDepartment ?? ''`, `name` = `row.requesterDisplayName ?? row.requesterUsername ?? ''`, `uid` = `row.requesterUsername ?? (row.userId != null ? String(row.userId) : '')`, joined as `parts.join(' / ')`. So the grid currently shows **department code** only, not department name.
- **Contract / API**
  - `docs/api-definition.md` §6.1.2 documents the search history list response: `requesterDepartmentCode`, `requesterDisplayName`, `requesterUsername` are already documented; `requesterDepartmentName` is not.
- **Design**
  - `docs/design/search-fields-by-screen.md` §4 defines the search-history requester block (부서, 사용자명, 사용자 ID). The grid column should display the same three axes; this requirement adds **department name** as the preferred value for the “부서” axis when available.
- **Cursor tools**
  - `.cursor/skills/search-history-decrypt-domain/SKILL.md` describes search history and decryption; it does not currently detail the list response shape. Updating it to mention that list rows include requester department **name** (when available) and requester display name/login id keeps agent knowledge accurate.

#### Problem analysis

1. **Department shown as code only**: The list API returns only `requesterDepartmentCode`. Users see values like `TEAM_SALES_A1` instead of the department display name (e.g. “영업1팀”), which is less readable.
2. **No department name in list**: The backend does not join the `department` table in the list query, so the service cannot return `department.name` without a schema or query change. Adding a LEFT JOIN to `department` and returning `requesterDepartmentName` resolves this.
3. **Frontend must prefer name over code**: The frontend currently uses only `requesterDepartmentCode`. It must be updated to show `requesterDepartmentName` when present, with fallback to `requesterDepartmentCode`, so “부서와 사용자명” are both clearly visible in the same column.

#### Solution approach

**Docs and Cursor tools (first, per user instruction)**

- Update **docs/api-definition.md** §6.1.2 (검색 이력 목록 조회): Add to the list response data shape the new field `requesterDepartmentName` (string | null) — display name of the requester’s department from the `department` table; when null or missing, the frontend uses `requesterDepartmentCode`.
- Optionally update **docs/design/search-fields-by-screen.md** (e.g. §4 or a short note): State that the search-history grid “검색한 사용자 정보” column shows department **name** when available (from list API), otherwise department code; and that the three axes (부서, 사용자명, 사용자 ID) remain as defined.
- Update **.cursor/skills/search-history-decrypt-domain/SKILL.md**: Add a note that the search history list response includes requester fields: `requesterDepartmentName` (when available), `requesterDepartmentCode`, `requesterDisplayName`, `requesterUsername`, so the grid can show department name and user name.

**Backend**

- In **SearchHistoryService.list()**:
  - Extend the FROM clause to add `LEFT JOIN department d ON d.code = au.department_code` (in the same place where `SearchHistoryListQuerySpec.getFromAndWhereClause()` is built, i.e. the same class that constructs `FROM search_history sh LEFT JOIN app_user au ON au.username = sh.user_id`). Ensure the JOIN is added in the query-spec or in the main list SQL so both the count query and the list query use it.
  - Add to the SELECT list `d.name AS "requesterDepartmentName"` (or equivalent alias).
  - In the row map, put `requesterDepartmentName` from the result set (string | null). When `d` is not matched (e.g. `au.department_code` is null or no row in `department`), the value is null; the frontend will fall back to `requesterDepartmentCode`.
- No change to list request parameters, detail endpoint, or DB schema; only list response shape and query change.
- Add or extend unit tests (e.g. SearchHistoryServiceTest) so that list rows include `requesterDepartmentName` when the requester’s `app_user.department_code` matches a `department.code` with a non-null `name`, and null when there is no match or no department.

**Frontend**

- In **RequesterInfoCell** (SearchHistoryList.js): For the “부서” part of the cell, use `row.requesterDepartmentName` when present and non-empty; otherwise use `row.requesterDepartmentCode` (and any existing fallback such as `row.requesterDepartment`). Keep “사용자명” and “사용자 ID” as today (`requesterDisplayName` / `requesterUsername` with existing fallbacks). The display order remains: department (name or code) → 사용자명 → 사용자 ID. Ensure that when the backend does not send `requesterDepartmentName` (e.g. old API), the cell still shows only the code so behavior is backward compatible.
- No new columns; no change to column count or layout. Only the **value** shown for the first axis (부서) changes from code-only to name-when-available.

**Contract / API**

- Covered under “Docs and Cursor tools” above: `docs/api-definition.md` must document the new list response field `requesterDepartmentName`.

### Affected scopes and change targets (verification)

Verified per `docs/workflow/REQUIREMENTS-CHANGE-TARGET-CHECKLIST.md`.

| Scope | Affected? | §2 subsection and change file list complete? |
|-------|-----------|-----------------------------------------------|
| Backend | Yes | Yes |
| Frontend (config UI + view screen) | Yes (view screen only) | Yes |
| DB | No | N/A |
| Contract / Spec | Yes | Yes |
| Cursor tools (skills, specs) | Yes | Yes |

- **Pattern 3.3 (API change)**: Contract/docs and backend list response shape are covered. Frontend consumes the new field.
- **Pattern 3.4 (Search/filter UI consistency)**: Does not apply — this requirement changes only the **grid column content** (requester cell values), not the search/filter form layout or user-block field width.

### Planned change file list (expected change targets)

**(Planned at authoring. Implementing agent (Step 4) confirms or amends this list when implementation is complete.)**

**Order: documentation and tools first, then Backend, then Frontend (per user instruction and delegation).**

#### Documentation and contract

- `docs/api-definition.md`
  - In §6.1.2 (검색 이력 목록 조회), add to the list response data shape: `requesterDepartmentName` (string | null) — requester’s department display name from the department table; null when not resolved. Existing fields `requesterDepartmentCode`, `requesterDisplayName`, `requesterUsername` remain.

#### Design (optional)

- `docs/design/search-fields-by-screen.md`
  - Optionally add a short note (e.g. under §4 or the search-history grid) that the “검색한 사용자 정보” column shows department **name** when available from the list API, otherwise department code; axes remain 부서 → 사용자명 → 사용자 ID.

#### Cursor tool update targets

- `.cursor/skills/search-history-decrypt-domain/SKILL.md`
  - Add or update a note that the search history list response includes requester fields: `requesterDepartmentName` (when available), `requesterDepartmentCode`, `requesterDisplayName`, `requesterUsername`, so the grid can show department name and user name.

#### Backend

- `backend/src/main/java/com/logmng/service/SearchHistoryService.java`
  - In the list query: add `LEFT JOIN department d ON d.code = au.department_code` to the FROM clause (wherever `getFromAndWhereClause()` is defined or used for the list SELECT); add `d.name AS "requesterDepartmentName"` to the SELECT list; in the row mapping, put `requesterDepartmentName` (string | null) into each row. Ensure both the count query and the list query use the same FROM (with the department join).
- `backend/src/test/java/com/logmng/service/SearchHistoryServiceTest.java` (or equivalent)
  - Add or extend tests so that list response rows include `requesterDepartmentName` when the requester has a department with a name, and null when there is no department or no name.

#### Frontend

- `frontend/src/components/SearchHistory/SearchHistoryList.js`
  - In `RequesterInfoCell`, set the department part to `row.requesterDepartmentName` when present and non-empty, otherwise `row.requesterDepartmentCode ?? row.requesterDepartment ?? ''`. Keep 사용자명 and 사용자 ID logic unchanged. Ensure backward compatibility when `requesterDepartmentName` is absent.

---

## 3. Test approach

### Test case list (required)

| ID | Scope | Type | Scenario (input / condition) | Expected result | Verification (unit / integration / manual) |
|----|-------|------|------------------------------|-----------------|---------------------------------------------|
| TC-01 | Backend | Normal | Call GET /api/search-history with valid auth; at least one row has requester with `app_user.department_code` matching a `department` row that has `name` set. | Response `data` rows include `requesterDepartmentName` (department name) for that row; other requester fields unchanged. | Unit (SearchHistoryServiceTest) or integration |
| TC-02 | Backend | Edge | List row where requester’s `app_user.department_code` is null or has no matching `department` row. | Row still returned; `requesterDepartmentName` is null; no NPE or join failure. | Unit |
| TC-03 | Backend | Edge | List row where `department` row exists but `department.name` is null. | Row has `requesterDepartmentName` null; `requesterDepartmentCode` still present. | Unit |
| TC-04 | Frontend | Normal | Open search history screen; list has rows with `requesterDepartmentName` set. | “검색한 사용자 정보” column shows department **name** (e.g. “영업1팀”) as the first part, then 사용자명, then 사용자 ID. | Manual / browser |
| TC-05 | Frontend | Normal | List row with `requesterDepartmentName` null but `requesterDepartmentCode` set. | Cell shows department **code** as the first part, then 사용자명, then 사용자 ID. | Manual / browser |
| TC-06 | Frontend | Regression | List response does not include `requesterDepartmentName` (e.g. simulate old API). | Cell still shows department code and user name/ID without error (backward compatible). | Unit (mock response without field) or manual |
| TC-07 | Integration | Normal | GET /api/search-history returns rows; then open search history screen. | Grid “검색한 사용자 정보” shows department name when API returns it, and code when name is null. | Integration / manual |

### Test scenarios

#### Scenario 1: Department name in grid

1. Ensure test data has at least one `app_user` with `department_code` pointing to a `department` row with a non-null `name` (e.g. “영업1팀”).
2. Create or use an existing search history row for that user. Log in and open the search history screen.
3. Confirm the “검색한 사용자 정보” column shows the department **name** (e.g. “영업1팀”) as the first segment for that row, followed by 사용자명 and 사용자 ID.

#### Scenario 2: Fallback to department code

1. Use a requester whose `department_code` has no matching `department` row, or whose department has `name` null.
2. Load the search history list and locate that row.
3. Confirm the cell shows the department **code** as the first part and does not show a blank or error.

### Test data

- Search-history rows with requesters that have varied `app_user.department_code`: (1) code matching a `department` row with `name` set; (2) code with no department row or department.name null; (3) `app_user.department_code` null. Use existing init-data or add test data so TC-01–TC-03 and TC-04–TC-05 can be run. No schema change required.

### Test environment

- Frontend: per contract (e.g. http://localhost:3001)
- Backend: per contract (e.g. http://localhost:9200)
- Database: PostgreSQL per project setup

---

## 4. Checklist

### Frontend verification

- [x] RequesterInfoCell shows department name when `requesterDepartmentName` is present; fallback to code when null or absent
- [x] Backward compatibility when API does not return `requesterDepartmentName`
- [x] No layout or column count change

### Backend verification

- [x] List query joins `department` and returns `requesterDepartmentName`; unit tests (H2 department table added)
- [x] No NPE or join failure when department is missing or name is null (LEFT JOIN)

### Integration

- [x] List API returns new field; frontend grid displays department name or code as specified

### Documentation

- [x] Requirement doc completed
- [x] docs/api-definition.md §6.1.2 updated with `requesterDepartmentName`
- [x] .cursor/skills/search-history-decrypt-domain/SKILL.md updated (list response shape)

---

## 5. Test results

### Test run date

- 2026-03-16

### Test results

#### Frontend

- Pass — SearchHistoryList.test.js: 5 tests passed; RequesterInfoCell falls back to code when requesterDepartmentName absent in mock.

#### Backend

- Pass — SearchHistoryServiceTest: all tests passed; H2 schema extended with department table; list() returns requesterDepartmentName.

**Commands:**

- `cd backend && mvn test -q -Dtest=SearchHistoryServiceTest`
- `cd frontend && npm test -- --testPathPattern=SearchHistoryList --watchAll=false`

**Outcome:**

- Documentation and tools updated first; Backend then Frontend implemented; tests passed.

### Issues found and resolution

- **부서명/사용자명 미표시**: PostgreSQL JDBC가 ResultSet 컬럼 라벨을 소문자로 반환하는 경우 `rs.getString("requesterDepartmentName")` 등이 null을 반환할 수 있음. **대응**: Backend에서 requester 관련 컬럼을 컬럼 인덱스(3–6)로 읽도록 변경. Frontend에서 `requester_department_name` 등 snake_case 폴백 추가. init-data에 user2/user3 사용자명(김철수, 이영희) 추가.

### Next steps

(To be filled)

---

## 6. Error remedy result (cause and action) — for error/bug fix requirements only

N/A — this is a feature change.

---

**Author**: Requirements subagent  
**Date**: 2026-03-16  
**Status**: Implemented (docs/tools → Backend → Frontend); verification and commit pending per workflow.
