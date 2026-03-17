# 20260316 - Search history grid: requester column, search conditions modal, remove approval history column

## 1. User requirement

### Requirement description

On the **search history** screen (검색 이력), the following changes are requested:

1. **Add a “검색한 사용자 정보” (requester / searched-by user) column** to the grid. Show the user who performed the search: department, display name (사용자명), and user ID (numeric or login id) in that column.
2. **Show search conditions in a modal** instead of inline in the grid. Remove the inline “검색 조건” cell that currently displays `searchParamsSummary` text. Replace it with a trigger (button or link, e.g. “검색 조건 보기”) that opens a modal. The modal must show the full search-params detail (same content as the existing “검색 조건 상세” in the detail modal, e.g. via `SearchParamsDetailView`).
3. **Remove the “결재 이력” (approval history) column** from the grid. Delete the approval-history column and the `ApprovalHistoryCell` usage. Approval status (복호화 승인 여부) remains; only the approval-history column is removed.

### User scenario

1. User opens the search history screen and sees the list of search-history rows.
2. **Requester info**: User sees a new column “검색한 사용자 정보” (or equivalent label) showing, per row, the requester’s department, display name, and user ID (or login id), so they can identify who performed each search without opening detail.
3. **Search conditions**: User no longer sees a long inline “검색 조건” text in the grid. Instead, the grid shows a control (e.g. “검색 조건 보기”) in that column. When the user clicks it, a modal opens and shows the full search conditions (same structure as the existing “검색 조건 상세” in “자세히 보기”).
4. **Approval history**: User no longer sees the “결재 이력” column (승인/반려 시 결재자·일시 요약). Approval status (복호화 승인 여부) and other columns (일시, 만료일시, 동작) remain.

### Expected outcome

- **Grid columns** (after change): 순번, 일시, **검색한 사용자 정보** (new), **검색 조건** (replaced by a trigger that opens a modal; no inline summary text), 복호화 승인 여부, 만료일시, 동작. The “결재 이력” column is removed.
- **Requester info column**: Displays requester’s department, display name (사용자명), and user ID (numeric `app_user.id` or login id `username`) for each row. Semantics align with the requester block in `docs/design/search-fields-by-screen.md` §4 (부서, 사용자명, 사용자 ID). When the backend does not return requester data (e.g. no join), the cell shows a fallback (e.g. “—” or empty) without error.
- **Search conditions**: Shown only in a modal opened by the new trigger. The modal reuses the same search-params detail content as the existing “자세히 보기” modal (e.g. `SearchParamsDetailView`). Inline `searchParamsSummary` text is no longer rendered in the grid.
- **Approval history column**: Removed from the grid and from column config. No `ApprovalHistoryCell` in the table body.
- **Accessibility**: The new “검색 조건 보기” control and the requester column have appropriate labels (e.g. `aria-label`). Modal is focusable and closable (e.g. Escape, overlay click) per existing detail modal behavior.

---

## 2. Design

### 2.1 Security review (optional)

- Not required for this change. Requester info (department, display name, user ID) is already within the scope of the search-history list (scope enforcement unchanged). No new PII or decryption scope.

### Technical design

#### Codebase summary

- **Frontend — SearchHistoryList.js**
  - `SEARCH_HISTORY_COLUMNS` defines columns: seq, requested_at, searchParamsSummary, approvalStatus, approvalHistory, expiresAt, actions.
  - Each row renders: seq, requestedAt, a `<td className="search-history-summary">` with `row.searchParamsSummary`, approval status label, `<ApprovalHistoryCell row={row} />`, expiresAt, action buttons (재조회, 자세히 보기, 재요청). “자세히 보기” opens a detail modal that includes “검색 조건 상세” via `SearchParamsDetailView(searchParams)` from `getSearchHistoryDetail(row.id)`.
  - `isRequester` is derived as `row.userId === user?.username` (likely should use numeric id vs current user for consistency; backend list returns `userId` as numeric `app_user.id`).
- **Backend — SearchHistoryService.java**
  - `list(SearchHistoryListRequest)` builds the list with: `FROM search_history sh LEFT JOIN app_user au ON au.username = sh.user_id`. SELECT includes `au.id AS "userId"`, search_history fields, and `putApprovalFields` (approvedBy, approvedAt, rejectedBy, rejectedAt, rejectionReason). It does **not** currently return requester’s department, display name, or login id (username). `buildSummary(search_params)` produces `searchParamsSummary`; full `search_params` is not in the list response.
  - `app_user` has: `id`, `username`, `name`, `department_code`, etc. (`docs/…/schema.sql`). So requester department (code or resolved name), display name (name or username), and login id (username) can be added from the existing join.
- **Design**
  - `docs/design/search-fields-by-screen.md` §4 defines the search-history screen and requester block (부서, 사용자명, 사용자 ID). The new grid column should display the same three axes for the requester.
  - `docs/design/grid-and-table.md`: table structure, column semantics, detail modal/drill-down. Search conditions modal is a detail-style view (modal opened from row action).

#### Problem analysis

1. **Requester info in grid**: The list API returns only numeric `userId` (app_user.id). The grid does not show department, display name, or login id, so users cannot identify the requester at a glance.
2. **Search conditions inline**: The grid shows `searchParamsSummary` in a table cell, which is long and clutters the table. The requirement is to show full search conditions only in a modal.
3. **Approval history column**: The “결재 이력” column and `ApprovalHistoryCell` are to be removed; approval status remains.

#### Solution approach

**Backend**

- In `SearchHistoryService.list()`, extend the SELECT to include requester fields from the existing `app_user` join: e.g. `au.department_code`, `au.name`, `au.username`. Add to each row map: `requesterDepartmentCode` (or resolved department name if a join to `department` is added), `requesterDisplayName` (au.name if present and non-empty, else au.username), `requesterUsername` (au.username, login id). Keep existing `userId` (numeric) for compatibility and for “is requester” checks. Document the new list response fields in `docs/api-definition.md` (§6.1.2).
- No change to list request parameters or to the detail endpoint. No schema change; only list response shape change.

**Frontend**

- **Requester column**: Add a column “검색한 사용자 정보” (or equivalent). For each row, render department, display name, and user ID (or login id) from `row.requesterDepartmentCode` / `row.requesterDisplayName` / `row.requesterUsername` (or fallback from `row.userId`). Use the same semantic order as the requester block (부서 → 사용자명 → 사용자 ID) per `docs/design/search-fields-by-screen.md` §4. If backend does not send the new fields yet, show a safe fallback (e.g. “—” or empty).
- **Search conditions modal**: Remove the `<td>` that displays `row.searchParamsSummary`. Replace with a `<td>` containing a button or link “검색 조건 보기” (or “자세히 보기” only for conditions). On click: open a modal that fetches detail via `getSearchHistoryDetail(row.id)` and displays only the search-params section (e.g. `SearchParamsDetailView(detail.searchParams)`) in the modal. Reuse existing modal patterns (focus, Escape, overlay click to close). Optionally reuse the same detail modal and show only the search-params block when opened from this trigger, or use a separate lighter modal; implementation choice is left to Frontend.
- **Remove approval history**: Remove `approvalHistory` from `SEARCH_HISTORY_COLUMNS`. Remove `<ApprovalHistoryCell row={row} />` from the row. Adjust `emptyColSpan` / `colSpan` (e.g. from 7 to 6) so empty state and layout remain correct.
- **isRequester**: Confirm comparison with current user uses the same identifier as the backend (numeric `userId` vs `user.id` or `user.userId` from auth). Fix if currently comparing `row.userId` to `user?.username` (type mismatch).

**Contract / API**

- Update `docs/api-definition.md` §6.1.2 (검색 이력 목록 조회) so the list response includes the new requester fields: e.g. `requesterDepartmentCode` (string | null), `requesterDisplayName` (string | null), `requesterUsername` (string | null, login id).

**Design reference**

- Requester column content semantics: `docs/design/search-fields-by-screen.md` §4 (requester block: 부서, 사용자명, 사용자 ID).
- Grid and table structure: `docs/design/grid-and-table.md`.
- This change does not modify the search/filter **form** (toolbar); it only adds a grid column and a modal. Pattern §2.4 (search/filter UI consistency) in REQUIREMENTS-CHANGE-TARGET-CHECKLIST applies to filter form layout, not to this grid column. The requester **column** should still follow the same semantic axes (department, display name, user ID) as the requester block for consistency.

### Affected scopes and change targets (verification)

Verified per `docs/workflow/REQUIREMENTS-CHANGE-TARGET-CHECKLIST.md`.

| Scope | Affected? | §2 subsection and change file list complete? |
|-------|-----------|-----------------------------------------------|
| Backend | Yes | Yes |
| Frontend (config UI + view screen) | Yes (view screen only) | Yes |
| DB | No | N/A |
| Contract / Spec | Yes | Yes |
| Cursor tools (skills, specs) | Optional (search-history skill may mention list shape) | Listed below |

### Planned change file list (expected change targets)

**(Planned at authoring. Implementing agent (Step 4) confirms or amends this list when implementation is complete.)**

#### Frontend

- `frontend/src/components/SearchHistory/SearchHistoryList.js`
  - Add column “검색한 사용자 정보” and render requester department, display name, user ID (or login id) from new row fields; fallback when absent.
  - Replace inline searchParamsSummary cell with a trigger (“검색 조건 보기”) that opens a modal; modal shows search-params detail (e.g. `SearchParamsDetailView`) from `getSearchHistoryDetail(row.id)`.
  - Remove `approvalHistory` from `SEARCH_HISTORY_COLUMNS` and remove `<ApprovalHistoryCell row={row} />`.
  - Set `emptyColSpan` / `colSpan` to the new column count (7: seq, 일시, 검색한 사용자 정보, 검색 조건 트리거, 복호화 승인 여부, 만료일시, 동작).
  - Align `isRequester` with backend (numeric userId vs current user id) if needed.

#### Backend

- `backend/src/main/java/com/logmng/service/SearchHistoryService.java`
  - In `list()`, extend SELECT to include requester fields from `app_user` (e.g. `au.department_code`, `au.name`, `au.username`) and put them in each row map (e.g. `requesterDepartmentCode`, `requesterDisplayName`, `requesterUsername`).

#### Contract / Spec

- `docs/api-definition.md`
  - In §6.1.2 (검색 이력 목록 조회), document new list response fields: `requesterDepartmentCode`, `requesterDisplayName`, `requesterUsername`. Existing `userId` and `searchParamsSummary` remain in the API; frontend stops rendering `searchParamsSummary` inline and shows search conditions only in the modal.

#### Cursor tool update targets (optional)

- `.cursor/skills/search-history-decrypt-domain/SKILL.md` — If the skill describes list response shape, add a note that list rows include requester info (department, display name, login id) for the grid column.

---

## 3. Test approach

### Test case list (required)

| ID | Scope | Type | Scenario (input / condition) | Expected result | Verification (unit / integration / manual) |
|----|-------|------|-----------------------------|-----------------|--------------------------------------------|
| TC-01 | Backend | Normal | Call GET /api/search-history with valid auth; scope allows multiple requesters. | Response `data` rows include `requesterDepartmentCode`, `requesterDisplayName`, `requesterUsername` (or null when join has no app_user). | Unit (SearchHistoryServiceTest) or integration |
| TC-02 | Backend | Edge | List row where `sh.user_id` has no matching `app_user`. | Row still returned; requester fields null or empty; no NPE. | Unit |
| TC-03 | Frontend | Normal | Open search history screen; list has rows. | Grid shows “검색한 사용자 정보” column with department, display name, user ID (or login id) per row. | Manual / browser |
| TC-04 | Frontend | Normal | Click “검색 조건 보기” (or equivalent) on a row. | Modal opens and shows full search conditions (same content as SearchParamsDetailView). Modal closes on Escape or overlay click. | Manual / browser |
| TC-05 | Frontend | Normal | Load search history grid. | No “결재 이력” column; no ApprovalHistoryCell; “복호화 승인 여부” column still present. | Manual / browser or unit (column config) |
| TC-06 | Frontend | Regression | Open “자세히 보기” from a row. | Detail modal still shows log type and “검색 조건 상세” as before. | Manual / browser |
| TC-07 | Integration | Normal | List then open search conditions modal for a row. | Modal content matches search params from GET /api/search-history/{id} for that row. | Integration / manual |

### Test scenarios

#### Scenario 1: Requester column and modal

1. Log in and open the search history screen.
2. Confirm the new “검색한 사용자 정보” column shows department, display name, and user ID (or login id) for each row.
3. Click “검색 조건 보기” on one row; confirm the modal opens and shows the same search-params detail as in “자세히 보기”.
4. Close the modal (Escape or overlay); confirm the grid is unchanged.

#### Scenario 2: Approval history removed

1. Load the search history grid.
2. Confirm the “결재 이력” column is not present.
3. Confirm “복호화 승인 여부”, “만료일시”, and “동작” columns are still present and correct.

### Test data

- Search-history rows with varied `user_id` (existing app_user usernames). At least one row with `app_user` having department_code, name, and username set; optionally one row where `user_id` has no matching `app_user` to verify null handling.

### Test environment

- Frontend: per contract (e.g. http://localhost:3001)
- Backend: per contract (e.g. http://localhost:9200)
- Database: PostgreSQL per project setup

---

## 4. Checklist

### Frontend verification

- [x] New requester column displays and falls back when fields missing
- [x] Search conditions modal opens from trigger and shows SearchParamsDetailView content
- [x] Approval history column and ApprovalHistoryCell removed; colSpan/emptyColSpan updated
- [x] Accessibility: trigger and modal have appropriate labels and keyboard/close behavior

### Backend verification

- [x] List response includes requester fields; existing tests updated or added (TC-01, TC-02)
- [x] No NPE when app_user is missing for a row (LEFT JOIN; nulls handled)

### Integration

- [x] List API returns new fields; frontend grid and modal behave as specified
- [ ] “자세히 보기” detail modal unchanged

### Documentation

- [x] Requirement doc completed
- [x] docs/api-definition.md §6.1.2 updated with new list response fields

---

## 5. Test results

### Test run date

- 2026-03-16

### Test results

#### Frontend

- SearchHistoryList.test.js: 5 tests passed (requester column, 검색 조건 보기 trigger, no approval column, footer/pagination, self scope).

#### Backend

- SearchHistoryServiceTest: all tests passed (list returns requester fields; H2 app_user extended with name column).

**Commands:**

- `cd backend && mvn test -q -Dtest=SearchHistoryServiceTest`
- `cd frontend && npm test -- --testPathPattern=SearchHistoryList.test --watchAll=false`

**Outcome:**

- Backend and frontend tests passed. Implementation complete.

### Issues found and resolution

(To be filled if any)

### Next steps

(To be filled)

---

## 6. Error remedy result (cause and action) — for error/bug fix requirements only

N/A — this is a feature change, not an error fix.

---

**Author**: Requirements subagent  
**Date**: 2026-03-16  
**Status**: Implemented (Backend + Frontend); verification and commit pending per workflow.
