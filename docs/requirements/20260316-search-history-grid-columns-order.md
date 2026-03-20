# 20260316 - Search history grid column order (부서, 사용자ID, 사용자명)

## 1. User requirement

### Requirement description

On the search history (검색 이력) screen, the grid currently shows a single combined column "검색한 사용자 정보" (requester info) after sequence number (순번) and date/time (일시). The user requests that this be replaced by **three separate columns** in the order: **부서**, **사용자ID**, **사용자명**. Any documents or tools that describe the grid layout must be updated to match, and changes must align with the project’s delegation and workflow.

### User scenario

1. User (e.g. user2) logs in and opens the search history (검색 이력) screen.
2. The grid loads with search history list data.
3. **Current behavior**: Column order is 순번, 일시, **검색한 사용자 정보** (one cell with "dept / name / uid" combined), 검색 조건, 복호화 승인 여부, 만료일시, 동작.
4. **Problem**: The requester information is shown in a single combined column; the user wants distinct columns for 부서, 사용자ID, and 사용자명 for clearer scanning and consistency with other screens.

### Expected outcome

- The search history grid shows columns in this order: **순번**, **일시**, **부서**, **사용자ID**, **사용자명**, 검색 조건, 복호화 승인 여부, 만료일시, 동작.
- **부서**: One column showing requester department (name when available, otherwise code); data from `requesterDepartmentName` / `requesterDepartmentCode`.
- **사용자ID**: One column showing requester login ID; data from `requesterUsername`.
- **사용자명**: One column showing requester display name; data from `requesterDisplayName` (fallback to `requesterUsername` when display name is empty).
- Design document(s) and Cursor skill(s) that describe the search-history grid are updated to state that the grid uses these three separate columns (부서, 사용자ID, 사용자명) instead of a single "검색한 사용자 정보" column.
- No backend API change: the list API already returns `requesterDepartmentName`, `requesterDepartmentCode`, `requesterDisplayName`, `requesterUsername` per row.

**Design reference**: Grid column definition and axis order for search-history are described in `docs/design/search-fields-by-screen.md` §4; that section must be updated to the three-column layout. Requester field semantics follow the same document and `docs/api-definition.md` (Search History list response).

---

## 2. Design

### 2.1 Security review (optional; when PII / decryption / access control)

Not applicable. No change to access control, decryption scope, or PII handling. Requester data is already shown; only the column layout changes.

### Technical design

#### Codebase summary

- **Frontend**: `frontend/src/components/SearchHistory/SearchHistoryList.js` defines the search history grid. `SEARCH_HISTORY_COLUMNS` has one column `{ key: 'requesterInfo', label: '검색한 사용자 정보' }`. Row rendering uses a single `RequesterInfoCell` that outputs one `<td>` with department, display name, and username joined by " / ". The API response already provides per-row `requesterDepartmentName`, `requesterDepartmentCode`, `requesterDisplayName`, `requesterUsername`. `DataTable` is given `emptyColSpan={7}` and `EmptyTableBody colSpan={7}`.
- **Design**: `docs/design/search-fields-by-screen.md` §4.1 describes the requester filter block and §4 (paragraph "그리드 '검색한 사용자 정보' 컬럼") states that the grid shows a single "검색한 사용자 정보" column with axis order 부서 → 사용자명 → 사용자 ID.
- **Cursor**: `.cursor/skills/search-history-decrypt-domain/SKILL.md` states that list rows include requester fields and the grid can show "department name and user name (부서·사용자명·사용자 ID)" in one combined form.
- **API**: `docs/api-definition.md` §6.1 documents the search-history list response fields; no change to request/response shape is required.

#### Problem analysis

1. The grid uses a single "검색한 사용자 정보" column and one cell component that concatenates 부서, 사용자명, 사용자 ID. The user wants **three separate columns** in the order 부서, 사용자ID, 사용자명.
2. Column count increases from 7 to 9; `emptyColSpan` and `colSpan` for the empty state must be updated.
3. Design doc and skill still describe the old single-column layout and must be updated so that future work and agents use the correct column model.

#### Solution approach

**Frontend:**

- Replace the single `requesterInfo` column in `SEARCH_HISTORY_COLUMNS` with three columns: **부서** (e.g. `requesterDepartment` or `requesterDept`), **사용자ID** (e.g. `requesterUsername`), **사용자명** (e.g. `requesterDisplayName`), in that order, immediately after 일시.
- Replace the single `RequesterInfoCell` usage in the row with three `<td>` cells (or three small cell components) that render:
  - 부서: `requesterDepartmentName` when present and non-empty, else `requesterDepartmentCode`, else "—".
  - 사용자ID: `requesterUsername` (or equivalent), else "—".
  - 사용자명: `requesterDisplayName` when present and non-empty, else `requesterUsername`, else "—".
- Update `emptyColSpan` and `EmptyTableBody` `colSpan` from 7 to **9** to account for the two additional columns.
- Keep sortable flags as needed (e.g. these three columns can remain non-sortable unless product requests server-side sort).
- Ensure existing tests that depend on column count or header text are updated; add or extend tests to verify column order and headers (순번, 일시, 부서, 사용자ID, 사용자명, …) per §3.

**Backend:**  
No change. List API already returns the required fields.

**DB:**  
No change.

**Docs:**

- **docs/design/search-fields-by-screen.md**: In §4, update the paragraph that describes the grid "검색한 사용자 정보" column. State that the grid shows **three separate columns** in the order **부서**, **사용자ID**, **사용자명** (not a single combined column). Data source: `requesterDepartmentName`/`requesterDepartmentCode` for 부서, `requesterUsername` for 사용자ID, `requesterDisplayName` (fallback `requesterUsername`) for 사용자명.
- **docs/api-definition.md**: Optional. If the project keeps a short note on how the search-history UI displays the list, add that the grid shows requester as three columns (부서, 사용자ID, 사용자명). If there is no such note, no change is required.

**Cursor tool update targets:**

- **.cursor/skills/search-history-decrypt-domain/SKILL.md**: Update the sentence about list/grid so that it states the grid shows **three columns** for requester: **부서**, **사용자ID**, **사용자명** (in that order), instead of a single combined "검색한 사용자 정보" column. Keep the reference to API fields (`requesterDepartmentName`, `requesterDepartmentCode`, `requesterDisplayName`, `requesterUsername`).

### Affected scopes and change targets (verification)

Per `docs/workflow/REQUIREMENTS-CHANGE-TARGET-CHECKLIST.md`:

| Scope | Affected? | §2 subsection and change file list complete? |
|-------|-----------|-----------------------------------------------|
| Backend | No | N/A |
| Frontend (config UI + view screen) | Yes (view screen only) | Yes |
| DB | No | N/A |
| Contract / Spec | No (optional doc note only) | Yes |
| Cursor tools (skills, specs) | Yes | Yes |

This requirement does not match scope-supporting screen, permission change, or API/error-code change patterns. It is a single-screen grid column layout change; search/filter UI consistency pattern (§2.4) does not apply (no alignment across multiple screens).

### Planned change file list (expected change targets)

**(Planned at authoring. Implementing agent (Step 4) confirms or amends this list when implementation is complete.)**  
*Confirmed 2026-03-16: all listed files changed as above; no amendments.*

#### Frontend

- `frontend/src/components/SearchHistory/SearchHistoryList.js`
  - Replace single `requesterInfo` column with three columns: 부서, 사용자ID, 사용자명 (in that order) in `SEARCH_HISTORY_COLUMNS`.
  - Replace single `RequesterInfoCell` row cell with three cells (or equivalent) rendering 부서, 사용자ID, 사용자명 from `requesterDepartmentName`/`requesterDepartmentCode`, `requesterUsername`, `requesterDisplayName` (with fallbacks).
  - Update `emptyColSpan` and table body empty `colSpan` from 7 to 9.
  - Remove or refactor `RequesterInfoCell` if it is no longer used.
- `frontend/src/components/SearchHistory/SearchHistoryList.test.js` (if present)
  - Update or add tests so that at least one TC verifies column order and headers (순번, 일시, 부서, 사용자ID, 사용자명, …). Implementing agent must add or extend test code for automated verification of this TC where applicable.

#### Docs

- `docs/design/search-fields-by-screen.md`
  - In §4 (search-history), update the grid column description: replace the single "검색한 사용자 정보" column description with three separate columns (부서, 사용자ID, 사용자명) and their data sources.
- `docs/api-definition.md`
  - Optional: if a sentence exists that describes how the search-history grid displays the list, update it to state that requester is shown as three columns (부서, 사용자ID, 사용자명). Otherwise no change required.

#### Cursor tools

- `.cursor/skills/search-history-decrypt-domain/SKILL.md`
  - Update the list/grid description so that the grid is stated to show **three columns** for requester: 부서, 사용자ID, 사용자명 (in that order), and that the API fields `requesterDepartmentName`, `requesterDepartmentCode`, `requesterDisplayName`, `requesterUsername` feed these columns.

---

## 3. Test approach

### Test case list (required)

| ID | Scope | Type | Scenario (input / condition) | Expected result | Verification (unit / integration / manual) |
|----|-------|------|------------------------------|-----------------|---------------------------------------------|
| TC-01 | Frontend | Normal | Open search history screen as user2 (or any user with list data). Load grid. | Grid column headers appear in order: 순번, 일시, 부서, 사용자ID, 사용자명, 검색 조건, 복호화 승인 여부, 만료일시, 동작. | Unit (npm test) or Manual / browser |
| TC-02 | Frontend | Normal | Same as TC-01; at least one row has requester data. | 부서 column shows department name or code; 사용자ID column shows login ID; 사용자명 column shows display name (or login ID fallback). No combined "dept / name / uid" in a single cell. | Unit or Manual / browser |
| TC-03 | Frontend | Edge | Open search history screen when list is empty. | Empty state message is shown; table layout does not break (colSpan matches column count 9). | Unit or Manual / browser |
| TC-04 | Docs | Normal | Read `docs/design/search-fields-by-screen.md` §4 and `.cursor/skills/search-history-decrypt-domain/SKILL.md`. | Both state that the search-history grid shows three requester columns: 부서, 사용자ID, 사용자명 (in that order). | Manual |

### Test scenarios

#### Scenario 1: Column order and headers

1. Log in as a user who can access search history (e.g. user2).
2. Navigate to 검색 이력 (search history).
3. Confirm the grid loads and column headers are, in order: 순번, 일시, 부서, 사용자ID, 사용자명, 검색 조건, 복호화 승인 여부, 만료일시, 동작.
4. Verification: Snapshot or DOM check for header cells or test assertion on column config.

#### Scenario 2: Requester data in three columns

1. With at least one row in the list that has requester data.
2. Check that 부서 cell shows only department (name or code), 사용자ID cell shows only login ID, 사용자명 cell shows only display name (or username).
3. Verification: No single cell contains "dept / name / uid" concatenation.

#### Scenario 3: Empty list and documentation

1. With empty list, confirm empty state renders without layout error (colSpan = 9).
2. Read design doc §4 and skill file; confirm they describe three columns (부서, 사용자ID, 사용자명).

### Test data

- Use existing test users (e.g. user2) and search history data from init-data or test fixtures. No new DB or API test data required for column layout.

### Test environment

- Frontend: `http://localhost:3001` (or per contract)
- Backend: `http://localhost:9200`
- Database: per project setup

### 3.5 Browser automation verification (optional)

- **Applicable TCs**: TC-01, TC-02, TC-03 (column order, cell content, empty state).
- **Procedure**: Log in → open search history menu → `browser_snapshot` to capture table headers and first row cells; verify header order and that requester appears in three columns. For empty list, confirm empty message and no layout break.
- **Reference**: `docs/workflow/BROWSER-AUTOMATION-VERIFICATION-POLICY.md`.

---

## 4. Checklist

### Frontend verification

- [ ] Column definitions and row cells updated (부서, 사용자ID, 사용자명)
- [ ] emptyColSpan and colSpan set to 9
- [ ] UI behavior and empty state confirmed

### Backend verification

- [ ] No API change; N/A

### Integration

- [ ] Grid loads with correct columns; empty list does not break layout

### Documentation

- [ ] Requirement doc completed
- [ ] Design doc (§4) and skill (search-history-decrypt-domain) updated

---

## 5. Test results

### Test run date

- 2026-03-16 (Frontend implementation)

### Test results

#### Frontend

- **Command**: `cd frontend && npm test -- --watchAll=false --testPathPattern="SearchHistoryList"`
- **Result**: All tests passed (TC-07, TC-09, TC-02 one-page, TC-06 multi-page, TC-01 column headers, TC-02 requester cells, TC-03 empty colSpan 9, TC-10 self scope).

#### Backend

N/A

**Commands:**

- TC-01/02/03 (Frontend): `cd frontend && npm test -- --watchAll=false --testPathPattern="SearchHistoryList"`

**Outcome:**

- Pass. Column order (순번, 일시, 부서, 사용자ID, 사용자명, …), three requester cells, empty state colSpan=9 verified by unit tests.

### Issues found and resolution

- TC-03 initially failed: `renderAndWaitForInitialLoad` waits for "검색 조건 보기" button which does not exist when list is empty. Fixed by waiting for `getSearchHistoryList` and empty message text instead.

### Next steps

1. ~~Implement Frontend and doc/skill changes per §2.~~ Done.
2. ~~Run unit tests and optional browser verification; record in §5.~~ Done.
3. Hand off to QA for verification per workflow.

---

## 6. Error remedy result (cause and action) — for error/bug fix requirements only

Not applicable (feature/UX change).

---

## 7. Final version (Korean) — optional summary

### 요약 (한국어)

- **요구사항**: 검색 이력 그리드에서 기존 단일 컬럼 "검색한 사용자 정보"를 제거하고, **부서**, **사용자ID**, **사용자명** 세 개의 개별 컬럼으로 표시 (순서: 순번 → 일시 → 부서 → 사용자ID → 사용자명 → 검색 조건 → 복호화 승인 여부 → 만료일시 → 동작).
- **기대 결과**: 그리드 컬럼 순서 및 헤더가 위와 같고, 설계 문서·스킬 파일이 이 레이아웃에 맞게 수정됨. 백엔드 API 변경 없음.
- **검증**: §3 TC-01로 컬럼 순서 및 헤더 확인; TC-02로 셀 내용(세 컬럼 분리) 확인; TC-04로 문서·스킬 반영 확인.

---

**Author**: Requirements subagent  
**Date**: 2026-03-16  
**Status**: In progress
