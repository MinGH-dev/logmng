# 20260318 - Search history list: distinct display of search count vs encryption (decryption target) count

## 1. User requirement

### Requirement description

On the Search History (검색 이력) list screen, the columns "검색건수" (search count) and "암호화건수" (encryption count) are both showing the same value (e.g. 48). They must represent two different metrics and be displayed distinctly: **search result total count** (total hits from the search at request time) vs **count of rows with encrypted data** (decryption-eligible items in the same snapshot window). The user expects to see values such as "search 48 / encryption (decryption target) 37" when the underlying data differs.

### User scenario

1. User performs a log search on the main (검색하기) screen that returns a total of 48 hits, of which 37 rows have encrypted data (decryption target).
2. User requests decryption approval ("복호화 승인 요청") with a reason; the system creates a search history record.
3. User opens the Search History (검색 이력) screen and views the list.
4. **Problem**: Both "검색건수" and "암호화건수" columns show 48 (or the same value). They should show 48 and 37 respectively (or the correct distinct values for that record).
5. User opens "자세히 보기" (view details) for that record; the modal should also show 검색건수 and 암호화건수 distinctly (e.g. 검색건수: 48, 암호화건수: 37).

### Expected outcome

- **List**: The Search History list table displays **검색건수** (search result total count) and **암호화건수** (decryption target count) as **two separate numeric values** sourced from the backend. When the stored values differ, the two columns show different numbers (e.g. 48 and 37). When either value is not yet computed or is null, the UI shows "미집계" per existing behavior.
- **Detail modal**: The "자세히 보기" modal shows **검색건수** and **암호화건수** in the summary section with the same semantics: distinct values from the API (searchResultTotalCount, decryptionTargetCount).
- **Data source**: Backend must store and return two distinct fields: `search_result_total_count` (total search hits at request time) and `decryption_target_count` (count of rows with encrypted data in the same snapshot window). Frontend must source list and detail display from these two fields without conflating them.
- **Create path**: When creating a search history record, the system must persist and later return two counts that reflect the correct semantics (total search count vs decryption target count). If the client can provide both counts at create time (from the current search result), it should send them so the server can store authoritative values; otherwise the server computes both from the stored search and must produce distinct values when they differ.

---

## 2. Design

### 2.1 Security review (optional; when PII / decryption / access control)

Not applicable for this requirement (display and sourcing of non-PII counts). No §2.1 security subsection required.

### Technical design

#### Codebase summary

- **Backend**
  - **Schema**: `search_history` has `search_result_total_count` and `decryption_target_count` (nullable INTEGER). Migration: `backend/src/main/resources/db/migrate-search-history-result-counts.sql`.
  - **Create**: `SearchHistoryService.create()` accepts optional `searchResultTotalCount` and `decryptionTargetCount` in the request; if **both** provided, they are stored as-is. If **both** omitted, the server calls `computeCountsAtCreate(logType, searchParams)`: it runs the same search with `pageSize=1` to get `pagination.totalCount`, then runs again with `pageSize = min(total, SNAPSHOT_MAX_ROWS)` and counts rows where `hasEncryptedData(logType, row)` to get the decryption target count. Result is stored in the two columns.
  - **List**: `SearchHistoryService.list()` selects `sh.search_result_total_count AS sh_sr_total`, `sh.decryption_target_count AS sh_dec_target` and maps them to response keys `searchResultTotalCount` and `decryptionTargetCount` via `putNullableIntegerColumn`.
  - **Detail**: GET `/api/search-history/{id}` reads the same two columns and returns `searchResultTotalCount`, `decryptionTargetCount` in the response.
  - **Contract**: `docs/api-definition.md` §6.1.1 (POST create: optional body fields), §6.1.2 (list response), §6.1.4 (detail response) already define these two fields.

- **Frontend**
  - **Create**: `searchHistoryService.createSearchHistory(logType, searchParams, requestReason)` sends only `logType`, `searchParams`, and optionally `requestReason`. It does **not** send `searchResultTotalCount` or `decryptionTargetCount`. So the backend always computes counts when creating from the main screen.
  - **List**: `SearchHistoryList.js` defines columns "검색건수" and "암호화건수", and for each row uses `getSearchResultTotalCount(row)` and `getDecryptionTargetCount(row)` (which read `searchResultTotalCount` / `decryption_target_count` and `decryptionTargetCount` / `decryption_target_count`). Cells render `formatListCount(searchTotal)` and `formatListCount(decryptTarget)`. So the list is designed to show two distinct values from the API.
  - **Detail modal**: `SearchHistoryDetailCounts` uses the same getters on `detailData` and displays "검색건수: {searchTotal}" and "암호화건수: {decryptTarget}".

#### Problem analysis

1. **Same value displayed for both columns**: The user observes both "검색건수" and "암호화건수" as 48. Possible causes: (a) the backend is storing the same value for both columns (create path: client never sends counts, so server computes — if server compute or a code path incorrectly sets both to the same value, DB would have 48/48); (b) the list or detail API maps or returns one value under both keys; (c) the frontend in some path uses the same value for both cells (we verified the list uses two different getters, so (c) is less likely unless the API returns the same key twice).
2. **Client does not send counts on create**: The frontend `createSearchHistory` never sends `searchResultTotalCount` or `decryptionTargetCount`. So the server always recomputes. If the server compute is wrong in some edge case (e.g. returning totalCount for both, or a bug in `hasEncryptedData` / pageSize handling), stored values would be wrong. Sending counts from the client when the current search result already has totalCount and encrypted count would make stored values authoritative and avoid recompute bugs for that flow.
3. **Legacy rows**: Rows created before the migration have NULL in both columns; the UI correctly shows "미집계". The issue at hand is when both columns show the **same** non-null value (48) instead of two different values.

#### Diagnostic phase (mandatory for error/bug fix)

Root cause must be confirmed from evidence before changing logic. Do not fix based on hypothesis alone.

- **Phase 0 (diagnostic):**
  1. Add **diagnostic (DEBUG)** logs in: (a) `SearchHistoryService.computeCountsAtCreate`: log the computed `totalInt` and `dec` (decryption count) before building `CountsAtCreate`; (b) after storing in DB in create: log the two values read back from the row; (c) in the list flow: log the two values read from ResultSet (`sh_sr_total`, `sh_dec_target`) for at least one row when returning the list.
  2. Reproduce the scenario: create a search history from the main screen where the search has a known total (e.g. 48) and a known decryption target count (e.g. 37), then open the search history list.
  3. Capture logs and (if possible) the actual API response for the list (e.g. one row’s `searchResultTotalCount` and `decryptionTargetCount`) and the DB row for that record (`search_result_total_count`, `decryption_target_count`).
  4. **Analyze**: Determine whether the DB has two distinct values, whether the list API returns two distinct values, and whether the UI receives them. Only after confirming where the values are lost or equalized, proceed to the logic/code fix.
- **Production safety:** All diagnostic logs must be at **DEBUG** level (or equivalent) so they are not emitted in production, or removed/downgraded after the fix is verified.

#### Solution approach

**Backend:**

- After diagnostic confirms the cause:
  - If the bug is in **computeCountsAtCreate** (e.g. wrong variable used for decryption count, or both set to totalCount): fix the computation so that `decryptionTargetCount` is derived only from the count of rows with `hasEncryptedData(logType, row)` in the fetched window, and `totalCount` is from `pagination.totalCount` only. Ensure both values are written to the correct columns.
  - If the bug is in **list or detail mapping** (e.g. one column overwritten or wrong key): fix the mapping so that `sh_sr_total` → `searchResultTotalCount` and `sh_dec_target` → `decryptionTargetCount` (and same for detail SELECT) without mixing.
- Keep existing contract: when client sends both `searchResultTotalCount` and `decryptionTargetCount` on create, store them as-is. When both omitted, compute and store both. No schema change.

**Frontend:**

- **Display**: List and detail modal already use two separate getters and two columns; no change needed unless diagnostic shows the API returns wrong keys. If the API is fixed and returns two distinct fields, the current UI will show them distinctly.
- **Create (optional but recommended):** When the main screen has the current search result with a total count and (if available) a count of encrypted/decryption-eligible rows, pass them in the create request so the server stores authoritative values. This requires:
  - Extending `createSearchHistory(logType, searchParams, requestReason, options)` (or equivalent) to accept optional `searchResultTotalCount` and `decryptionTargetCount` and include them in the POST body when both are provided.
  - In `LogGrid`, when calling create (e.g. in `handleRequestDecryptionApproval`), if the current search response has `pagination.totalCount` and a derived or provided "encrypted count" (decryption target count), pass both so the server stores them. If the client cannot compute the decryption target count, omit both and let the server compute (existing behavior).

**DB:**

- No schema or migration change. Columns already exist.

**Contract / Spec:**

- No change to API shape. `docs/api-definition.md` already documents optional `searchResultTotalCount` and `decryptionTargetCount` on POST create and their presence in list/detail responses. If the frontend is updated to send counts when available, the existing contract already allows it; optionally add a one-line note that the client may send both counts when it has them from the current search result.

### Affected scopes and change targets (verification)

Before finalizing §2, the Requirements author verified every affected scope and the change target checklist (`docs/workflow/REQUIREMENTS-CHANGE-TARGET-CHECKLIST.md`).

| Scope | Affected? | §2 subsection and change file list complete? |
|-------|-----------|-----------------------------------------------|
| Backend | Yes | Yes |
| Frontend (config UI + view screen) | Yes (view screen + create flow) | Yes |
| DB | No | N/A |
| Contract / Spec | Optional (note only) | Yes |
| Cursor tools (skills, specs) | Optional | Yes (skill reference only if needed) |

- This requirement does **not** match pattern 3.4 (search/filter UI consistency); it is a data sourcing and display fix for two numeric columns.
- Pattern 3.3 (API or error-code change): API shape is unchanged; optional client sending of existing body fields may be documented.

### Planned change file list (expected change targets)

**(Planned at authoring. Implementing agent (Step 4) confirms or amends this list when implementation is complete.)**

#### Backend

- `backend/src/main/java/com/logmng/service/SearchHistoryService.java`
  - Add DEBUG diagnostic logs in `computeCountsAtCreate` (computed total vs decryption count), and after insert in create (values read back), and in list when mapping row (sh_sr_total, sh_dec_target). After root cause is confirmed, fix the bug (compute or mapping) and remove or downgrade diagnostic logs.
- `backend/src/test/java/com/logmng/service/SearchHistoryServiceTest.java`
  - Add or extend tests so that when server computes counts, two distinct values are stored and returned (e.g. total 48, decryption 37). Ensure list and detail tests assert both fields independently.
- `backend/src/test/java/com/logmng/controller/SearchHistoryControllerTest.java` (if create/list contract is exercised)
  - Verify create with client-provided searchResultTotalCount and decryptionTargetCount stores and returns them; verify list returns both fields per row.

#### Frontend

- `frontend/src/services/searchHistoryService.js`
  - Extend `createSearchHistory` to accept optional `searchResultTotalCount` and `decryptionTargetCount` (e.g. via an options object or extra parameters). When both are provided (and are non-negative numbers), include them in the POST body so the server stores authoritative values.
- `frontend/src/components/LogGrid.js`
  - In `handleRequestDecryptionApproval`, when calling create, pass current search result’s total count (e.g. from pagination.totalCount) and, when available, the decryption target count (e.g. from current result set or from a known source). If only one is available, omit both and let the server compute (per API contract).
- `frontend/src/components/SearchHistory/SearchHistoryList.js`
  - No change required for list/detail display if the API returns two distinct fields; only verify that the two columns and detail summary use `getSearchResultTotalCount` and `getDecryptionTargetCount` (already the case). If diagnostic shows a different frontend bug, add it to the change list.
- `frontend/src/components/SearchHistory/SearchHistoryList.test.js`
  - Ensure tests expect distinct values for 검색건수 and 암호화건수 when the API returns distinct values (e.g. 48 and 37). Add or adjust a test that verifies list and detail show two different numbers when the mock API returns searchResultTotalCount: 48, decryptionTargetCount: 37.

#### DB

- None. Schema and migration already provide `search_result_total_count` and `decryption_target_count`.

#### Contract / Spec

- `docs/api-definition.md`
  - Optional: In §6.1.1 (POST create), add a short note that the client may send `searchResultTotalCount` and `decryptionTargetCount` when it has them from the current search result (e.g. from pagination and encrypted-row count); when both are sent, the server stores them and does not recompute.

### Cursor tool update targets

- `.cursor/skills/search-history-decrypt-domain/SKILL.md`: If the domain description of search history list/detail does not already state that list and detail show two distinct counts (search result total vs decryption target), add a one-line note so future agents do not conflate them. No change if already clear.

---

## 3. Test approach

### Test case list (required)

| ID | Scope | Type | Scenario (input / condition) | Expected result | Verification (unit / integration / manual) |
|----|-------|------|------------------------------|-----------------|--------------------------------------------|
| TC-01 | Backend | Normal | Create search history **without** client counts; server computes. Stored search returns totalCount=48 and 37 rows with hasEncryptedData in first min(48, SNAPSHOT_MAX_ROWS). | DB row has search_result_total_count=48, decryption_target_count=37. List and detail API return searchResultTotalCount=48, decryptionTargetCount=37. | Unit (mvn test) |
| TC-02 | Backend | Normal | Create search history **with** client sending searchResultTotalCount=100, decryptionTargetCount=25. | DB row has 100 and 25. List and detail return 100 and 25. | Unit (mvn test) |
| TC-03 | Backend | Edge | Create with server compute; stored search returns totalCount=0 or no pagination. | Both counts stored as 0 or null per existing behavior; no exception. | Unit |
| TC-04 | Frontend | Normal | List row has searchResultTotalCount=48, decryptionTargetCount=37 from API. | List table shows "48" in 검색건수 column and "37" in 암호화건수 column. Detail modal shows "검색건수: 48" and "암호화건수: 37". | Unit (npm test) |
| TC-05 | Frontend | Normal | Create from main screen with current search total 50 and decryption target 20; client sends both. | POST body includes searchResultTotalCount: 50, decryptionTargetCount: 20. After create, list shows 50 and 20 for that row. | Unit or integration |
| TC-06 | Integration | Normal | E2E: Perform search with mixed plain/encrypted rows → request decryption approval → open Search History list. | List shows two columns with distinct values (e.g. search total ≠ decryption target) when backend stores distinct values. | Manual / browser |
| TC-07 | Backend | Regression | List API returns multiple rows; each row has searchResultTotalCount and decryptionTargetCount. | No row has one field overwritten by the other; both keys remain distinct in response. | Unit (mvn test) |

### Test scenarios

#### Scenario 1: Server compute produces distinct counts

1. Create a search history record without sending counts; backend runs computeCountsAtCreate.
2. Stub or use a search that returns totalCount=48 and 37 rows with encrypted data in the first 48 rows.
3. Verify stored row has search_result_total_count=48, decryption_target_count=37.
4. Call list API and GET detail; verify response has searchResultTotalCount=48, decryptionTargetCount=37.

#### Scenario 2: List and detail display distinct values

1. Mock or use API that returns one row with searchResultTotalCount=48, decryptionTargetCount=37.
2. Render SearchHistoryList and open detail modal for that row.
3. Verify list cells show 48 and 37 in the respective columns; modal shows "검색건수: 48" and "암호화건수: 37".

#### Scenario 3: Client sends counts on create

1. From LogGrid, with current search result total 50 and decryption target 20, call create with both values.
2. Verify POST request body contains searchResultTotalCount and decryptionTargetCount.
3. Verify list/detail for the new record show 50 and 20.

### Test data

- Use existing search_history test data with non-null `search_result_total_count` and `decryption_target_count`. For distinct-value tests, use rows where the two columns differ (e.g. 48 and 37). SQL example for test DB: `UPDATE search_history SET search_result_total_count = 48, decryption_target_count = 37 WHERE id = ?;`

### Test environment

- Frontend: http://localhost:3001 (or per contract)
- Backend: http://localhost:9200
- Database: PostgreSQL (per contract)

---

## 4. Checklist

### Frontend verification

- [ ] API parameters (create with optional counts) validated
- [ ] List and detail UI show two distinct values when API returns them
- [ ] Error handling unchanged for create (400 when only one count sent)

### Backend verification

- [ ] Diagnostic phase run; root cause confirmed from logs
- [ ] Compute or mapping fix applied; unit tests added/updated
- [ ] List and detail return both fields correctly

### Integration

- [ ] E2E: create from main → list shows distinct 검색건수 / 암호화건수 when applicable
- [ ] Legacy rows (NULL counts) still show "미집계"

### Documentation

- [ ] Requirement doc completed
- [ ] docs/api-definition.md updated if client-send-counts behavior is documented

---

## 5. Test results

### Test run date

- 2026-03-18 (Backend scope)

### Test results

#### Frontend

[Pass / Fail]

- Not run in this Backend scope.

#### Backend

Pass

- `mvn test -Dtest=SearchHistoryServiceTest,SearchHistoryControllerTest` — exit 0. All tests passed including TC-01 (create 48/37 distinct), TC-02 (client override 100/25), TC-03 (totalCount 0), TC-07 (list multiple rows with distinct counts per row), create_computesAndStoresSearchResultTotalCountAndDecryptionTargetCount, list_includesSearchResultTotalCountAndDecryptionTargetCount, getDetail_includesStoredCountFields, create_withOptionalCountOverrides_returns201WithCounts.

**Commands:**

```bash
cd backend && mvn test -Dtest=SearchHistoryServiceTest,SearchHistoryControllerTest
cd frontend && npm test -- --watchAll=false --testPathPattern=SearchHistoryList
```

**Outcome:**

- Backend unit tests: all passed. Diagnostic DEBUG logs added in SearchHistoryService (computeCountsAtCreate, after insert, list first row); no logic bug found in backend — distinct values stored and returned.

### Issues found and resolution

- None. Backend already stored and returned searchResultTotalCount and decryptionTargetCount correctly; list/detail mapping uses sh_sr_total → searchResultTotalCount and sh_dec_target → decryptionTargetCount without mixing.

### Next steps

- QA: run full verification and E2E; if UI still shows same value for both columns, check frontend getters (getSearchResultTotalCount / getDecryptionTargetCount) and API response shape.

---

## 6. Error remedy result (cause and action) — for error/bug fix requirements only

- **Requirement ID**: 20260318-search-history-counts-display
- **Root cause (refined)**: Plain image log rows can contain **quoted bracket-wrapped values** that are not cipher payloads (e.g. `"size":"[100,200]"`, `"dims":"[1,2,3]"`). The previous fix used pattern `"\[[^\"]*\]"` so that any such value was treated as encrypted → every row was still counted as encrypted → decryption_target_count equalled search_result_total_count → both columns showed 48/48. The pattern was **too loose**.
- **Actions taken**: (1) **Tightened encrypted-style detection**: `containsEncryptedStylePayload` now requires the **content inside brackets** to have length ≥ 32 (`MIN_ENCRYPTED_PAYLOAD_LENGTH`). Pattern updated to capture inner content: `"\[([^\"]*)\]"`; return true only if at least one match has `group(1).length() >= 32`. Short plain values like `"[100,200]"` or `"[1,2,3]"` no longer match. (2) **Row keys**: Confirmed LogDbService image log search returns rows with keys `datastring`, `headerstring` (from JDBC `getColumnName`); `getFromRow(row, "datastring", "dataString")` already correct. (3) **Tests**: Added `hasEncryptedData_javaFwImglog_plainJsonWithShortBracketValue_returnsFalse` and `hasEncryptedData_javaFwImglog_plainJsonWithShortDimsBracket_returnsFalse`; updated all tests that expect encrypted rows to use a 32+ character payload (`LONG_ENC_PAYLOAD`) so they still pass (approve snapshot, create counts, getDetail decryption rows).
- **Result**: `mvn test -Dtest=SearchHistoryServiceTest,SearchHistoryControllerTest` — exit 0. All tests pass.
- **Completed**: 2026-03-18

---

**Author**: Requirements subagent  
**Date**: 2026-03-18  
**Status**: In progress
