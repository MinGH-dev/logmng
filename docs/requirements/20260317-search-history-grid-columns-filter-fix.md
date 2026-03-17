# 20260317 - Search History grid column sizing and filter behavior fix

## 1. User requirement

### Requirement description

Two improvements for the Search History (검색 이력) screen: (1) **Grid column size optimization** — reduce column widths for **User ID** (사용자ID) and **Search condition** (검색 조건) to the minimum needed for their content; (2) **Search field filtering** — ensure that filtering by **검색일시** (date/time range), **복호화** (decryption approval status), and **요청 사유** (request reason) works correctly. If it currently does not work or behaves incorrectly, the requirement captures the expected behavior and provides enough context for implementers to diagnose whether the issue is in the frontend (params not sent or wrong format), the backend (params ignored or wrong query), or the API contract.

### User scenario

1. User opens the Search History screen and views the result grid.
2. **Problem (columns)**: The **User ID** column uses more horizontal space than needed; user IDs are numeric and at most 8 digits. The **Search condition** column only needs space for the "검색 조건 보기" button; excess width wastes space.
3. **Problem (filters)**: User sets **검색일시** (start/end), **복호화** (approval status: 대기/승인/반려/만료), or **요청 사유** (request reason) and clicks Search. The list does not narrow as expected — filtering does not work or returns incorrect results for one or more of these fields.
4. User expects: (a) User ID column width to fit 8-digit numbers; Search condition column width to fit the button only; (b) all three filter types to apply so the list reflects the selected date range, approval status(es), and request reason (partial match).

### Expected outcome

- **User ID column**: Column width is constrained to the space needed for **8-digit numeric user IDs** (e.g. min-width/max-width or fixed width that fits "12345678" without excess). Reference: `docs/design/search-fields-by-screen.md` §4.1 (userId maxLength 8, digits only) and §4 (grid requester columns).
- **Search condition column**: Column width is constrained to the **size of the control (button)** in that column ("검색 조건 보기"); no extra width beyond what the button needs.
- **Filter behavior**: When the user sets **검색일시 (시작/종료)**, **복호화** (one or more of 대기/승인/반려/만료), or **요청 사유** and clicks Search:
  - **검색일시**: Only rows with `requested_at` within the selected range (inclusive) are returned. API params: `requestedAtFrom`, `requestedAtTo` in format `yyyy-MM-dd HH:mm:ss` (per `docs/api-definition.md` §6.1.2).
  - **복호화**: Only rows whose `approval_status` is in the selected set are returned. API: repeated `approvalStatus` query param (e.g. `approvalStatus=PENDING&approvalStatus=APPROVED`).
  - **요청 사유**: Only rows whose request reason matches the entered text (partial/ILIKE) are returned. API param: `requestReason`; backend applies `request_reason ILIKE '%value%'`.
- If filtering currently fails, implementers must **verify** (1) frontend sends the correct param names and formats on Search; (2) backend receives and applies them in the list query; (3) API response contains only rows that satisfy the filters. The requirement doc does not assume the root cause (frontend vs backend); it states the expected behavior so that diagnosis and fix can be done in the appropriate layer.

**Note**: Column width values must be sourced from or aligned with design docs. This requirement references `docs/design/search-fields-by-screen.md` §4 (search-history grid columns) and `docs/design/search-field-definition-items.md` §4.5 (width by role / 8-digit userId). For filter semantics, reference `docs/api-definition.md` §6.1.2 and `docs/contract.md`.

## 2. Design

### 2.1 Security review (optional)

Not applicable (no PII, decryption scope, or access-control change).

### Technical design

#### Codebase summary

- **Backend**: `SearchHistoryController` list endpoint accepts `requestedAtFrom`, `requestedAtTo`, `approvalStatus` (List), `requestReason` and maps them into `SearchHistoryListRequest`. `SearchHistoryService.buildListQuerySpec()` applies: `requested_at >= ?` / `requested_at <= ?` (parsed with `DateTimeFormatter "yyyy-MM-dd HH:mm:ss"`), `approval_status IN (...)`, and `request_reason ILIKE ?`. Parsing failure for dates is logged and the condition is skipped (no exception).
- **Frontend**: `SearchHistoryList.js` holds state for `requestedAtFrom`, `requestedAtTo`, `approvalStatuses`, `requestReason` and their `applied*` counterparts. On Search, it passes `appliedRequestedAtFrom`, `appliedRequestedAtTo`, `appliedApprovalStatuses`, `appliedRequestReason` to `getSearchHistoryList()`. `searchHistoryService.js` builds query params: `requestedAtFrom`/`requestedAtTo` (only if non-empty string), repeated `approvalStatus` for each selected status, `requestReason` if non-empty. Date values are converted via `toApiDatetime()` from `datetime-local` format (`yyyy-MM-ddThh:mm`) to `yyyy-MM-dd hh:mm:00` (or substring 0–19).
- **Grid**: `SEARCH_HISTORY_COLUMNS` defines columns including `requesterUsername` (label "사용자ID") and `searchConditions` (label "검색 조건"). The table is rendered without per-column width constraints; column widths are browser-default.

#### Problem analysis

1. **User ID column**: The grid column "사용자ID" displays numeric `app_user.id` (8 digits per design). No width constraint is defined; the column may be wider than necessary.
2. **Search condition column**: The column contains only a button "검색 조건 보기". No width constraint is defined; the column may be wider than necessary.
3. **Filter behavior**: User reports that filtering by 검색일시, 복호화, or 요청 사유 does not work or behaves incorrectly. Possible causes (to be confirmed by implementer): (a) frontend does not send params (e.g. empty applied values, wrong structure); (b) frontend sends wrong format (e.g. date format backend cannot parse); (c) backend ignores params (e.g. parsing fails silently, param not bound); (d) backend query or response is wrong. The API contract and existing backend code indicate that all three filters are supported; the requirement ensures expected behavior is documented and that verification steps can isolate frontend vs backend.

#### Solution approach

**Frontend:**

- **Grid column sizing**: Add width constraints for the **사용자ID** (requesterUsername) and **검색 조건** (searchConditions) columns so that:
  - 사용자ID: width fits 8-digit numbers (e.g. min-width/max-width per `docs/design/search-field-definition-items.md` §4.5 for 8-digit userId role, or a narrow fixed width that fits "12345678").
  - 검색 조건: width fits the button only (e.g. min-width/max-width or width that contains the "검색 조건 보기" button without excess). Use `docs/design/search-fields-by-screen.md` §4 (grid column semantics) and `docs/design/grid-and-table.md` if column width standards exist; otherwise align with design doc and document any exception per `docs/design/css-standard-and-exceptions.md`.
- **Filter behavior**: Verify that on Search (and initial load when default date is applied):
  - `requestedAtFrom` and `requestedAtTo` are sent in API format `yyyy-MM-dd HH:mm:ss` when the user has set a date range (e.g. via `toApiDatetime()` output).
  - `approvalStatus` is sent as repeated query params for each selected status when the user has selected one or more approval statuses.
  - `requestReason` is sent as a single query param when the user has entered non-empty text.
  - If any of these are missing or wrong, fix the frontend so that the correct params are sent. If frontend is correct, the issue is backend or contract; document findings for backend handoff.

**Backend:**

- **Filter behavior**: Verify that the list endpoint receives `requestedAtFrom`, `requestedAtTo`, `approvalStatus` (list), and `requestReason` and that `SearchHistoryService.buildListQuerySpec()` applies them. If parsing of dates fails (e.g. format mismatch), either fix parsing or return a clear validation error instead of silently skipping the condition. Ensure `approvalStatus` multi-value binding is correct (Spring `List<String>`). No change required if current code already applies all filters correctly; verification and tests are required.

**Contract / API:**

- No change to request/response shape. `docs/api-definition.md` §6.1.2 already describes the query params and semantics. If implementer finds a discrepancy between doc and code, update the doc to match the implemented behavior.

**Implementation note for Frontend (grid columns):**

When changing Search History grid column widths, the implementer must read and apply from `docs/design/search-fields-by-screen.md` §4 (search-history grid columns, requester column order and semantics) and `docs/design/search-field-definition-items.md` §4.5 (width by role; userId 8-digit). For table layout and column width standards, apply `docs/design/grid-and-table.md` if applicable. For any screen-specific exception (e.g. a column width not defined in the design doc), use component CSS only with a comment and document in the Exception index per `docs/design/css-standard-and-exceptions.md`. If a required standard is undefined or ambiguous, do not infer or hardcode; inform the user, explain why it is needed, propose a recommended draft, and request feedback before implementation.

### Affected scopes and change targets (verification)

**Before finalizing §2**, the Requirements author has verified that every affected scope is covered. See `docs/workflow/REQUIREMENTS-CHANGE-TARGET-CHECKLIST.md`.

| Scope | Affected? | §2 subsection and change file list complete? |
|-------|-----------|-----------------------------------------------|
| Backend | Yes (verification / possible fix) | Yes |
| Frontend (config UI + view screen) | Yes | Yes |
| DB | No | N/A |
| Contract / Spec | No (optional: doc sync if discrepancy found) | Optional |
| Cursor tools (skills, specs) | No | N/A |

This requirement does not change DB schema. It may touch API behavior only if filter params are currently ignored or misapplied. Pattern 3.4 (search/filter UI consistency) partially applies for grid column sizing; design doc references are included.

### Planned change file list (expected change targets)

**(Planned at authoring. Implementing agent (Step 4) confirms or amends this list when implementation is complete.)**

Use requirement tone: **must**, **verify**, **align**. Implementing agent confirms or amends when done.

#### Frontend

- `frontend/src/components/SearchHistory/SearchHistoryList.js`
  - Add or pass column width constraints for **사용자ID** (requesterUsername) and **검색 조건** (searchConditions) so that User ID column fits 8-digit numbers and Search condition column fits the button only.
  - Verify that on Search and initial load, list request params include `requestedAtFrom`, `requestedAtTo` (when date range is set), `approvalStatus` (repeated, when approval filter has selection), and `requestReason` (when non-empty); fix if params are missing or wrongly formatted.
- `frontend/src/components/SearchHistory/SearchHistory.css`
  - If column widths for 사용자ID and 검색 조건 are implemented via component CSS, add scoped rules (and document exception in design if applicable). Align with `docs/design/css-standard-and-exceptions.md`.
- `frontend/src/components/SearchHistory/SearchHistoryList.test.js`
  - Add or extend tests: column widths or DOM/CSS assertions for 사용자ID and 검색 조건; tests that Search sends date range, approvalStatuses, and requestReason when set; tests that filtered results (or mock response) reflect applied filters.
- `frontend/src/services/searchHistoryService.js`
  - Verify param building for `requestedAtFrom`, `requestedAtTo`, `approvalStatus` (repeated), `requestReason`; fix if any filter param is omitted or wrong.

**Frontend implementation confirmed (Step 4):** Column width constraints for 사용자ID and 검색 조건 implemented in `SearchHistory.css` only (nth-child(4) and nth-child(6)); exception documented in `docs/design/css-standard-and-exceptions.md` §5. No change to `SearchHistoryList.js` for column config (DataTable uses CSS by column index). Filter params verified: `SearchHistoryList` already passes `appliedRequestedAtFrom`/`appliedRequestedAtTo` (via `toApiDatetime`), `appliedApprovalStatuses`, `appliedRequestReason`; `searchHistoryService.js` builds query params correctly (repeated `approvalStatus`, date format `yyyy-MM-dd HH:mm:ss`). No code change needed in `SearchHistoryList.js` or `searchHistoryService.js` for filter behavior. Tests added: TC-01 (사용자ID column), TC-02 (검색 조건 column), TC-09 (integration: all three filters sent together).

#### Backend

- `backend/src/main/java/com/logmng/controller/SearchHistoryController.java`
  - Confirmed: `requestedAtFrom`, `requestedAtTo`, `approvalStatus`, `requestReason` are received and passed to service. Added rethrow of `IllegalArgumentException` so invalid date format returns 400.
- `backend/src/main/java/com/logmng/service/SearchHistoryService.java`
  - Verified: `buildListQuerySpec()` applies date range (`requested_at >= ?` / `<= ?`), approval status list (`IN (...)`), and request reason (`ILIKE ?`). Date parse failure now throws `IllegalArgumentException` (clear validation error) instead of silent skip.
- `backend/src/main/java/com/logmng/exception/GlobalExceptionHandler.java`
  - Added `@ExceptionHandler(IllegalArgumentException.class)` → 400 BAD_REQUEST (req 20260317 date validation).
- `backend/src/test/java/com/logmng/controller/SearchHistoryControllerTest.java` and/or `backend/src/test/java/com/logmng/service/SearchHistoryServiceTest.java`
  - Added: `list_whenServiceThrowsIllegalArgumentException_returns400` (controller); `list_whenRequestedAtFromInvalidFormat_throwsIllegalArgumentException` (service). Existing tests cover TC-06/07/08: `list_filtersByRequestedAtFromAndTo`, `list_filtersByApprovalStatusesIn`, `list_filtersByRequestReasonIlike`.

#### Design doc

- `docs/design/search-fields-by-screen.md`
  - If grid column width standards for 사용자ID (8-digit) or 검색 조건 (button) are not yet documented in §4, add or update so that the requirement stays reference-only.

#### Contract / Spec

- `docs/api-definition.md`
  - Updated §6.1.2 에러: requestedAtFrom/requestedAtTo 형식 오류 시 400 BAD_REQUEST 명시.

## 3. Test approach

### Test case list (required)

| ID | Scope | Type | Scenario (input / condition) | Expected result | Verification (unit / integration / manual) |
|----|-------|------|------------------------------|-----------------|--------------------------------------------|
| TC-01 | Frontend | Normal | Open Search History grid with data; inspect User ID column. | 사용자ID column width fits 8-digit numbers; no excessive empty space. | Unit (DOM/CSS) or Manual |
| TC-02 | Frontend | Normal | Inspect Search condition column. | 검색 조건 column width fits the "검색 조건 보기" button; no excessive empty space. | Unit or Manual |
| TC-03 | Frontend | Normal | Set 검색일시 (start and end), click Search. | Request URL or service call includes `requestedAtFrom` and `requestedAtTo` in format `yyyy-MM-dd HH:mm:ss`. List results are within the selected range. | Unit (mock) or Integration |
| TC-04 | Frontend | Normal | Select one or more 복호화 options, click Search. | Request includes repeated `approvalStatus` for each selected value. List results have only the selected status(es). | Unit or Integration |
| TC-05 | Frontend | Normal | Enter 요청 사유 text, click Search. | Request includes `requestReason`. List results have request reason matching the text (partial). | Unit or Integration |
| TC-06 | Backend | Normal | GET /api/search-history with `requestedAtFrom` and `requestedAtTo`. | Response rows have `requestedAt` within [from, to]. | Unit (service) or Integration |
| TC-07 | Backend | Normal | GET /api/search-history with multiple `approvalStatus` values. | Response rows have `approvalStatus` in the given set. | Unit or Integration |
| TC-08 | Backend | Normal | GET /api/search-history with `requestReason` non-empty. | Response rows have `request_reason` matching ILIKE. | Unit or Integration |
| TC-09 | Integration | Regression | Full flow: set date range + approval status + request reason, Search. | List shows only rows that satisfy all three filters. | Integration or Manual |

### Test scenarios

#### Scenario 1: Grid column width

1. Open Search History and load a list with several rows.
2. Confirm 사용자ID column is narrow (fits 8 digits).
3. Confirm 검색 조건 column is narrow (fits the button).

#### Scenario 2: Date filter

1. Set 검색일시 (시작) to a specific date 00:00, (종료) to same or later date 23:59.
2. Click Search.
3. Confirm every row’s 검색일시 is within the range (and that changing the range changes the list).

#### Scenario 3: Approval and request reason filters

1. Select only "승인" in 복호화, click Search; confirm only approved rows appear.
2. Enter part of a known 요청 사유, click Search; confirm only matching rows appear.
3. Combine date + approval + request reason; confirm all filters apply.

### Test data

- Search history rows with varied `requested_at`, `approval_status` (PENDING, APPROVED, REJECTED, EXPIRED), and `request_reason` (some with known substrings). Use existing data or create via normal search + request flow.

### Test environment

- Frontend: `http://localhost:3001` (or per contract)
- Backend: `http://localhost:9200`
- Database: per project setup

### 3.5 Browser automation verification (optional)

Applicable TCs: TC-01, TC-02 (column width), TC-03–TC-05, TC-09 (filter behavior). QA may use Browser MCP to open Search History, set filters, submit Search, and snapshot to verify column widths and that the list reflects filters. Reference: `docs/workflow/BROWSER-AUTOMATION-VERIFICATION-POLICY.md`.

## 4. Checklist

### Frontend verification

- [ ] 사용자ID column width fits 8-digit numbers
- [ ] 검색 조건 column width fits the button
- [ ] Search sends requestedAtFrom, requestedAtTo, approvalStatus(es), requestReason when set
- [ ] Filtered list results match applied filters (or hand off to Backend if frontend params are correct)

### Backend verification

- [ ] List endpoint receives and applies requestedAtFrom, requestedAtTo, approvalStatus, requestReason
- [ ] Date parsing uses `yyyy-MM-dd HH:mm:ss`; failed parse is handled (log or validation)
- [ ] Unit/integration tests added for date, approval, and request-reason filters

### Integration

- [ ] End-to-end: set all three filter types and confirm list is correctly filtered

### Documentation

- [ ] Requirement doc completed
- [ ] Design doc updated if column width standards were missing

## 5. Test results

### Test run date

- [Date and time]

### Test results

#### Frontend

[Pass / Fail]

- [Result description]

#### Backend

[Pass / Fail]

- [Result description]

**Commands:**

(One executable command or step per TC where applicable; QA fills when running verification.)

**Outcome:**

- [Item 1]
- [Item 2]

### Issues found and resolution

(To be filled when tests or verification reveal issues.)

### Next steps

(To be filled after §5 is run.)

---

**Author**: Requirements subagent  
**Date**: 2026-03-17  
**Status**: In progress
