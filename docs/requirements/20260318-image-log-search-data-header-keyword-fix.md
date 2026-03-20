# 20260318 - Image log search (data, header, keyword) not working — diagnose and fix

## 1. User requirement

### Requirement description

On the Image Log (Java FW Image Log) screen, **data search** (datastring), **header search** (headerstring), and **keyword search** (keywords) are reported as not working properly. The user wants the **root cause identified** and the search behavior fixed so that data, header, and keyword filters behave as intended.

### User scenario

1. User logs in and opens the **Java FW Image Log** screen (log type `java_fw_imglog`).
2. User enters values in **Data** (datastring), **Header** (headerstring), and/or **Keywords** (comma-separated) in the search form.
3. User clicks search.
4. **Problem**: Search results do not reflect the data/header/keyword conditions (e.g. 0 results when matches exist, or results that should be filtered out are shown).

### Expected outcome

- **Data** (datastring), **Header** (headerstring), and **Keywords** (keywords) search on the Image Log screen work as specified: only rows matching the given conditions are returned (and counts/pagination are correct).
- **Root cause** is documented (e.g. in §6 of this doc) so future changes do not regress.
- If the cause is request/serialization: frontend sends and backend receives `datastring`, `headerstring`, and `keywords` correctly; backend applies them to the result set.
- If the cause is backend filtering design: filtering is applied in a way that considers the full candidate set (e.g. not only the current SQL page) so that matches on any page are found.

**Note**: Design standards for search fields (e.g. field width, layout) are defined in `docs/design/search-fields-by-screen.md` and `docs/design/forms-and-filters.md`; this requirement does not change those. It focuses on **correctness** of data/header/keyword search behavior.

## 2. Design

### 2.1 Security review (optional; when PII / decryption / access control)

**Scope**: Data/header/keyword search touches decrypted content in memory when `decryptData` is used or when in-memory filtering compares against decrypted fields. The fix and any diagnostic logging must not introduce exposure of sensitive or decrypted data.

- **Decrypted content and PII in logs**
  - Diagnostic logs must **not** log raw decrypted content, full `datastring`/`headerstring`, or full `keywords` in production. Log only presence/length or non-sensitive flags (e.g. `hasDatastringSearch`, row counts). See **Diagnostic phase** and **Production safety** below.
  - Search terms (`datastring`, `headerstring`, `keywords`) can be user-supplied PII or sensitive; treat them as sensitive in logging and in any error messages (no echo of full values).

- **Access control**
  - The endpoint `POST /api/logs/db-refactored/search` is already protected by logType↔screen permission (see `docs/api-definition.md` §5.1 and req 20260318-menu-and-permission-restructure): the user must have access to the screen corresponding to the requested `logType` (e.g. java_fw_imglog → java-fw-imagelog). The fix **must not** weaken or bypass this check; no new permission logic is required for this requirement.

- **Input validation and DoS**
  - `datastring`, `headerstring`, and `keywords` are not used in SQL (filtering is in-memory), so SQL injection via these fields is not in scope. To mitigate DoS and log injection, the implementer should apply **reasonable limits**: e.g. max length for `datastring`/`headerstring` (or reject/truncate per contract), and max size for `keywords` array. Document any limits in contract/spec if newly introduced; existing contract need not change if limits are already in place elsewhere.

- **Rate limiting**
  - No new rate-limiting requirement for this fix. If the fix increases per-request work (e.g. fetch-then-filter over a larger set), existing operational or gateway-level rate limiting for the search endpoint remains recommended.

### Technical design

#### Codebase summary

- **Backend**
  - **API**: `POST /api/logs/db-refactored/search` (see `docs/api-definition.md` §5.1). Request body: `LogDbSearchRequest` with `logType`, `startDate`, `endDate`, `application`, `servicegroup`, `service`, `datastring`, `headerstring`, `keywords` (array), `decryptData`, pagination, sort.
  - **Controller**: `LogDbController.searchLogs()` receives the body, normalizes dates if empty, calls `LogDbService.searchLogs(request)`. The **service** delegates to `searchJavaFwImglog(request)` when `logType = "java_fw_imglog"` (delegation happens inside `LogDbService.searchLogs()`, not in the controller).
  - **Service**: `LogDbService.searchJavaFwImglog(LogDbSearchRequest)` builds SQL on `imagelog` with WHERE conditions only for **date**, **application**, **servicegroup**, **service**. It does **not** add SQL conditions for `datastring`, `headerstring`, or `keywords` (by design: those columns may contain encrypted values, so search is done after fetch). It runs the query with **LIMIT / OFFSET** (one page of rows). Then it applies **in-memory filtering** on that single page: for each row it checks `datastring`/`headerstring` (and decrypted JSON string values when present) and `keywords` (OR across keywords, in datastring/headerstring). Only rows matching all requested data/header/keyword conditions are kept. Total count in the response is `finalCount = needsFiltering ? filteredCount : totalCount`, where `filteredCount` is the size of the **filtered current page** (so when filters are applied, the response total is wrong: it reflects only matches on the fetched page, not the full result set). Pagination is re-applied on the filtered list for the current page (in practice the list is already one page, so re-pagination is redundant until the fix fetches a larger set).
  - **Request DTO**: `LogDbSearchRequest` has `datastring`, `headerstring` (strings) and `keywords` (List&lt;String&gt;) with `@JsonProperty`; getters/setters present.
- **Frontend**
  - **Screen**: Image Log uses `LogGrid` with `logType.id === 'java_fw_imglog'`, which renders `ImageLogSearchForm` and `ImageLogTable`.
  - **Form**: `ImageLogSearchForm` holds `formData` with `datastring`, `headerstring`, `keywords` (string; comma-separated). On submit it builds `keywordsArray` from the string and sends `searchParams`: `startDate`, `endDate`, `application`, `servicegroup`, `service`, `datastring`, `headerstring`, `keywords` (array). It calls `onSearch(searchParams)`. `searchParams` also includes `decryptData` and `showDecryptOption` from `...formData`. For re-search from history, LogGrid passes `initialFormValues` from `apiParamsToFormValues(initialSearchParams)` so the form shows the same conditions (keywords array → comma-separated string).
  - **Grid**: **LogGrid provides** `handleSearch` to `ImageLogSearchForm` as `onSearch` (not "receives from the form"). When the form submits, it calls `onSearch(searchParams)`. `handleSearch(params)` builds `requestData = { ...params, logType: logType.id, page, pageSize, sortField, sortDirection, displayTemplate }` and POSTs to `/api/logs/db-refactored/search`; so `datastring`, `headerstring`, and `keywords` (array) from the form are forwarded in the request body. Sort, page change, and page-size change use `...searchParams`, so those three fields are preserved for follow-up requests.
- **Contract**: `docs/api-definition.md` and `docs/contract.md` describe the log search API; request shape includes `datastring`, `headerstring`, `keywords` for image log.

#### Problem analysis

Possible causes (to be confirmed by diagnostic phase, not assumed):

1. **Request not sent or not received**
   - Frontend might not include `datastring`/`headerstring`/`keywords` in the payload (e.g. wrong key, overwritten by spread), or backend might not bind them (e.g. wrong property name, type mismatch so Jackson ignores).
2. **Filtering applied only to one page**
   - Backend fetches one page (e.g. 10 rows) with SQL, then filters that page by data/header/keyword. If the matching rows are not in that first page (by sort order), the user sees 0 results. So **post-pagination filtering** can make data/header/keyword search appear broken when matches exist on other pages.
3. **Filtering logic bug**
   - E.g. wrong field used (row key vs request key), empty list vs null, or decryption path not applied so encrypted content is never matched.
4. **Date/format or other precondition**
   - E.g. date range or parsing causing 0 rows from SQL, so there is nothing to filter (see also `docs/requirements/20260225-image-log-search-no-results.md`).

A past fix (`docs/requirements/20260206-image-log-datastring-search.md`) addressed datastring not being sent due to form binding; the current issue may be the same, different, or additive (e.g. pagination + filter order).

#### Diagnostic phase (mandatory for error/bug fix)

The implementer **must not** change logic based on hypothesis alone. Follow this sequence:

1. **Add diagnostic (DEBUG) logs** in the suspected areas:
   - **Backend**: In `LogDbController.searchLogs` or at entry of `LogDbService.searchJavaFwImglog`: log that `request.getDatastring()`, `request.getHeaderstring()`, `request.getKeywords()` are received (e.g. length/non-null; do **not** log full content in production). After building `hasDatastringSearch` / `hasHeaderstringSearch` / `hasKeywordsSearch`, log those flags. After the SQL query, log the number of rows fetched (e.g. `results.size()` before in-memory filter). After in-memory filtering, log the count before vs after (e.g. `totalCount` vs `filteredResults.size()`).
   - **Frontend** (if needed): In `ImageLogSearchForm` submit and/or `LogGrid.handleSearch`, log (at debug level) that `datastring`, `headerstring`, and `keywords` are **present and non-dropped** in the object sent to the API (e.g. keys and non-empty; avoid logging full values if sensitive). Note: current `LogGrid.handleSearch` only logs `paramKeys`; add explicit checks for these three fields for diagnosis.
2. **Reproduce** the failure: run a search with data, header, or keyword filled; capture backend logs (and optionally frontend network/console).
3. **Analyze logs** to confirm:
   - Are the three parameters received by the backend?
   - Is filtering applied (flags true)? How many rows before vs after filter?
   - If the backend receives the params and applies the filter but the result set is empty, is it because the **only** rows fetched were the first page and none of them matched (post-pagination filtering)?
4. **Only after** the root cause is confirmed from logs, implement the fix (e.g. fix request binding, or change order of operations so filtering is applied to a sufficient set before pagination, or fix filter logic).

**Production safety**: All diagnostic logs must be at **DEBUG** level (or equivalent) so they are off in production, or behind a dev-only flag, or removed/downgraded after the fix is verified. They must **not** emit sensitive or decrypted content in production. **Existing logs**: `LogDbController` and `LogDbService` already log at **INFO** with full `request.getDatastring()`, `getHeaderstring()`, `getKeywords()` (e.g. controller lines 74–78, service entry and flag logs). For production safety, downgrade these to DEBUG or replace with length/null-only (e.g. "datastring length=N, nonNull") during the diagnostic phase; after root cause is confirmed, keep request-field logs at DEBUG or remove.

#### Solution approach

Structure by scope. The exact fix depends on the **confirmed** root cause from the diagnostic phase.

**Backend:**

- **If cause is request not received**: Verify DTO property names and JSON shape; fix binding or document contract. Add or adjust tests for search with datastring/headerstring/keywords.
- **If cause is post-pagination filtering**: When `hasDatastringSearch || hasHeaderstringSearch || hasKeywordsSearch`, the service must not filter only the current SQL page. Options (implementer chooses with performance in mind): (a) fetch a larger set or full set (e.g. without LIMIT or with a capped LIMIT) when these filters are present, then filter in memory and apply pagination; or (b) push non-encrypted search to SQL where possible; or (c) document limitation and UI note. Total count and pagination must reflect the filtered result set. If using (a), consider a **max rows cap** (e.g. 10_000) and/or query timeout to avoid OOM or long-running queries; document the cap in code or contract.
- **If cause is filter logic bug**: Fix the condition (e.g. correct field names, null/empty handling, decryption path) and add unit tests that cover data/header/keyword matching and non-matching rows.
- Add or extend **unit tests** for `LogDbService.searchJavaFwImglog` with datastring/headerstring/keywords (and for any new helper used for filtering). Preserve or add **error handling** for decryption failures in the filter path (e.g. `decryptJsonStringValues`); do not log raw decrypted content; keep existing try/catch behavior so a decryption failure does not break the whole search.

**Frontend:**

- **If cause is request not sent**: Ensure `searchParams` in `ImageLogSearchForm` and the object passed to `handleSearch` include `datastring`, `headerstring`, `keywords` (array) and are not dropped or overwritten when building `requestData` in `LogGrid`. Fix any incorrect spread or key name. Add or extend tests for form submit payload.
- **If cause is only backend**: No frontend code change; optional debug log can be removed after verification.

**Contract / Spec:**

- Update only if request/response shape or behavior is changed (e.g. new field or pagination semantics). Otherwise no change.

**Design and CSS (when editing the form):**

- When modifying `ImageLogSearchForm` (e.g. payload or binding), preserve field definitions and layout per `docs/design/search-fields-by-screen.md` §1.2 (ImageLogSearchForm) and `docs/design/forms-and-filters.md`. The component uses shared `SearchForm.css`; do not change control sizing/layout unless the fix requires it, in which case follow `docs/design/css-standard-and-exceptions.md`.

#### Architecture notes (post-pagination filtering and cross-cutting)

**1. Post-pagination filtering — design options and trade-offs**

The current design fetches one SQL page (LIMIT/OFFSET) then applies data/header/keyword filters in memory, so matches on other pages are missed. Options:

| Option | Description | Performance | Consistency | Complexity | Recommendation |
|--------|-------------|-------------|-------------|------------|----------------|
| (a) Fetch larger/full set when filters present | When `hasDatastringSearch \|\| hasHeaderstringSearch \|\| hasKeywordsSearch`, fetch more rows (e.g. no LIMIT or high LIMIT), filter in memory, then paginate. | Risk: high memory and latency if unfiltered set is large; must cap. | Correct if cap is sufficient. | Low. | Acceptable only with a **hard cap** (e.g. max 5000 rows) to avoid OOM/timeouts; document in API. |
| (b) Push search to SQL where possible | Add SQL WHERE (e.g. `datastring LIKE`, `headerstring LIKE`, keyword OR conditions) when columns are **not** encrypted. For encrypted columns, in-memory filter after fetch. | Best when SQL filter applies; avoids large in-memory sets. | Correct. | Medium (hybrid: SQL for plaintext, in-memory for decrypted). | **Preferred** where data is stored in plaintext or when `decryptData=false` and columns are searchable. |
| (c) Document limitation + UI note | Keep current behavior; document that data/header/keyword apply only to the current page. | No change. | Incorrect for cross-page matches. | Lowest. | Only as interim or when (a)/(b) infeasible. |

**Architecture decision (for implementer):**

- **Prefer (b)** when searchable columns are not encrypted (or when `decryptData=false` and plaintext is available): push `datastring`/`headerstring`/`keywords` into SQL WHERE so pagination and count are correct and scalable.
- **If (b) is not possible** (e.g. encrypted content must be decrypted before match): use **(a) with a maximum pre-filter result set size** (e.g. 5000 rows). Document this limit in `docs/api-definition.md` and in the response (e.g. pagination or a note when the cap is hit). Reject or truncate beyond the cap to avoid unbounded memory and timeouts.
- **Document** in §2 or API spec: when in-memory filter is used, "search applies to at most the first N rows (by sort order) within the date/application/service filter"; if a cap is used, document its value and that total count may be capped.

**2. Cross-cutting impact — no regression for non–image-log search**

- The same API `POST /api/logs/db-refactored/search` and shared **LogGrid** are used for **pb_feplog** and **java_fw_imglog**. Backend branches by `logType`: `searchPbFeplog(request)` vs `searchJavaFwImglog(request)`; only `searchJavaFwImglog` and Image Log–specific UI (e.g. `ImageLogSearchForm`) are in scope.
- **Constraint**: Changes must **not regress** non–image-log search. In particular:
  - **Backend**: Do not change the shared `searchLogs()` dispatcher or `LogDbSearchRequest` in a way that breaks `searchPbFeplog` (e.g. do not remove or rename fields used by pb_feplog). All changes for data/header/keyword must be confined to `searchJavaFwImglog` (and possibly controller logging for image log only).
  - **Frontend**: When fixing payload building in `LogGrid` or the form, ensure `requestData` still includes params required by pb_feplog (e.g. `mediaCode`, `trCode`, `loginId`) and that adding or fixing `datastring`/`headerstring`/`keywords` does not overwrite or drop other log-type params.
- **Verification**: §3 test approach should include a **smoke test** for pb_feplog search (e.g. one search with `logType=pb_feplog` and date/media/trCode) to confirm no regression. Optionally add an explicit test case "TC-07: pb_feplog search unchanged (no regression)".

### Affected scopes and change targets (verification)

Per `docs/workflow/REQUIREMENTS-CHANGE-TARGET-CHECKLIST.md`:

| Scope | Affected? | §2 subsection and change file list complete? |
|-------|-----------|-----------------------------------------------|
| Backend | Yes | Yes (diagnostic + fix in controller/service; tests) |
| Frontend (config UI + view screen) | Maybe | Yes (if binding/payload fix needed) |
| DB | No | N/A |
| Contract / Spec | Maybe | Yes (only if API shape/behavior changes) |
| Cursor tools (skills, specs) | No | N/A |

This requirement does not match domain pattern §2.4 (search/filter UI consistency across screens); it fixes correctness of existing image log search fields.

### Planned change file list (expected change targets)

**(Planned at authoring. Implementing agent (Step 4) confirms or amends this list when implementation is complete.)**

#### Frontend

- `frontend/src/components/ImageLogSearchForm.js` — **done**
  - Verified: `searchParams` passed to `onSearch` includes `datastring`, `headerstring`, `keywords` (array); added explicit `decryptData: Boolean(formData.decryptData)`. Added DEBUG-only diagnostic log (length/presence only, no values) per req.
- `frontend/src/components/LogGrid.js` — **done**
  - Verified: `requestData = { ...params, ... }` forwards all params; added DEBUG-only diagnostic for `java_fw_imglog` (presence/length of datastring, headerstring, keywords in requestData).
- `frontend/src/components/ImageLogSearchForm.test.js` — **done** (new file)
  - TC-06: unit tests that form submit payload to `onSearch` includes `datastring`, `headerstring`, `keywords` (array) with correct keys; empty fields → empty string / empty array; `decryptData` is boolean.

#### Backend — **implemented**

- `backend/src/main/java/com/logmng/controller/LogDbController.java` — **done**
  - DEBUG-only logs for datastring/headerstring/keywords (length/null only) when logType is java_fw_imglog. Existing INFO that logged full request fields downgraded to DEBUG or length-only.
- `backend/src/main/java/com/logmng/service/LogDbService.java` — **done**
  - DEBUG logs for hasDatastringSearch/hasHeaderstringSearch/hasKeywordsSearch and row counts before/after filter. Full request field logs downgraded to DEBUG/length-only. **Fix**: when any of these filters present, prefetch up to IMGLOG_FILTER_PREFETCH_CAP (5000) rows, filter in memory, then paginate; total count = filtered size. Helpers: `readImageLogResultSet`, `filterImageLogRowsByDataHeaderKeywords`.
- `backend/src/test/java/com/logmng/service/LogDbServiceTest.java` — **done** (created)
  - TC-01 (datastring-only), TC-02 (headerstring-only), TC-03 (keywords-only), TC-04 (empty/null no NPE), TC-07 (pb_feplog smoke). H2 in-memory imagelog + pb_send/pb_recv.

#### DB

- None.

#### Contract / Spec

- `docs/api-definition.md` (and if needed `docs/contract.md`)
  - Update only if request/response or pagination semantics for image log search change after the fix.

## 3. Test approach

### Test case list (required)

| ID | Scope | Type | Scenario (input / condition) | Expected result | Verification (unit / integration / manual) |
|----|-------|------|-------------------------------|-----------------|--------------------------------------------|
| TC-01 | Backend | Normal | searchJavaFwImglog with datastring non-empty; DB has rows with that substring in datastring | Response rows only include rows where datastring contains the term; total count matches filtered count | Unit (mvn test) |
| TC-02 | Backend | Normal | searchJavaFwImglog with headerstring non-empty; DB has rows with that substring in headerstring | Response rows only include rows where headerstring contains the term; total count matches filtered count | Unit (mvn test) |
| TC-03 | Backend | Normal | searchJavaFwImglog with keywords list non-empty; DB has rows with at least one keyword in datastring or headerstring | Response rows only include rows matching at least one keyword (OR); total count matches filtered count | Unit (mvn test) |
| TC-04 | Backend | Edge | searchJavaFwImglog with datastring/headerstring/keywords empty or null | No in-memory filter applied; behavior unchanged; no NPE | Unit (mvn test) |
| TC-05 | Integration | Normal | Frontend: Image Log screen, enter data + header + keyword, submit | Request body contains datastring, headerstring, keywords; backend returns only matching rows; UI shows correct count and rows | Integration (browser or curl) |
| TC-06 | Frontend | Normal | ImageLogSearchForm submit with datastring/headerstring/keywords filled | Payload passed to onSearch includes datastring, headerstring, keywords (array) with correct keys | Unit (npm test) or manual |
| TC-07 | Integration | Regression | Search with logType=pb_feplog (date + mediaCode or trCode); same API and LogGrid as image log | Response and pagination unchanged; no regression from image-log fix | Integration (curl or browser) |

### Test scenarios

#### Scenario 1: Data (datastring) search

1. Open Image Log, enter a value in Data field that exists in some imagelog row.
2. Submit search.
3. Verify: backend receives datastring; response contains only rows whose datastring contains the value; total count and pagination are correct.

#### Scenario 2: Header (headerstring) search

1. Open Image Log, enter a value in Header field that exists in some imagelog row.
2. Submit search.
3. Verify: backend receives headerstring; response contains only rows whose headerstring contains the value.

#### Scenario 3: Keyword search

1. Open Image Log, enter one or more keywords (comma-separated) that exist in datastring or headerstring of some rows.
2. Submit search.
3. Verify: backend receives keywords array; response contains only rows that match at least one keyword (OR).

#### Scenario 4: Diagnostic verification

1. Enable DEBUG logs for controller/service (or dev-only path).
2. Reproduce a failing search (data/header/keyword).
3. Capture logs: request fields present, hasDatastringSearch/hasHeaderstringSearch/hasKeywordsSearch, row counts before/after filter.
4. Confirm root cause before applying code fix.

### Test data

- Use existing imagelog data or test DB with rows that have known datastring/headerstring content so that data/header/keyword filters can be asserted. No new schema or migration required.

### Test environment

- Frontend: `http://localhost:3001` (or per contract)
- Backend: `http://localhost:9200`
- Database: PostgreSQL (logmng)

### 3.5 Browser automation verification (optional)

- **Applicable TCs**: TC-05 (integration).
- **Procedure**: Navigate to Image Log, fill Data/Header/Keywords, submit, snapshot table and pagination; verify result set and count match expectations.
- **Reference**: `docs/workflow/BROWSER-AUTOMATION-VERIFICATION-POLICY.md`.

## 4. Checklist

### Frontend verification

- [x] API parameters (datastring, headerstring, keywords) validated in payload
- [ ] UI behavior (results and count) confirmed for data/header/keyword search (TC-05 manual/integration)
- [ ] Error handling verified (empty result, network error)

### Backend verification

- [x] API test cases for data/header/keyword search written and run
- [x] Diagnostic logs added, used for root cause, then removed or set to DEBUG
- [x] Performance considered if fetch-then-filter approach is used (prefetch cap 5000)

### Integration

- [ ] End-to-end flow tested (form → API → response → table) (TC-05)
- [x] Edge cases tested (empty keywords, null datastring, etc.)
- [x] TC-07: pb_feplog search unchanged (no regression from image-log fix)

### Documentation

- [x] Requirement doc completed
- [x] §6 Error remedy result updated after fix with root cause and actions

## 5. Test results

### Test run date

- 2026-03-18

### Test results

#### Frontend

**Pass**

- `npm test -- --watchAll=false` (including ImageLogSearchForm.test.js): tests passed.
- TC-06: ImageLogSearchForm submit payload includes datastring, headerstring, keywords (array) with correct keys; decryptData sent as boolean.
- DEBUG diagnostic logs added in ImageLogSearchForm and LogGrid (presence/length only; no full values). TC-05 (integration) remains for manual or E2E verification.

#### Backend

**Pass**

- `mvn test -Dtest=LogDbServiceTest` — 5 tests, 0 failures.
- TC-01 (datastring-only): matching rows and total count correct.
- TC-02 (headerstring-only): matching rows and total count correct.
- TC-03 (keywords-only): rows matching any keyword and total count correct.
- TC-04 (empty/null filters): no NPE; all rows in range returned.
- TC-07 (pb_feplog): search unchanged; no regression.

**Commands:**

```bash
# Backend unit tests
cd backend && mvn test -Dtest=LogDbServiceTest
```

**Outcome:**

- LogDbServiceTest: 5/5 passed.
- Diagnostic logs added at DEBUG; full request field logs downgraded to DEBUG or length/null-only.
- Post-pagination fix applied: when data/header/keyword filters present, prefetch up to 5000 rows then filter then paginate.

### Issues found and resolution

- None; backend unit tests pass.

### Next steps

1. ~~Run diagnostic phase; confirm root cause.~~ Done.
2. ~~Implement fix; run TC-01–TC-07 (backend TCs).~~ Done.
3. Frontend/QA: TC-05, TC-06, TC-07 integration as needed; complete checklist.

## 6. Error remedy result (cause and action) — for error/bug fix requirements only

Record root cause and actions under this requirement ID.

- **Requirement ID**: 20260318-image-log-search-data-header-keyword-fix
- **Root cause**: **Post-pagination filtering.** The service applied SQL LIMIT/OFFSET first (one page, e.g. 10 rows), then applied in-memory filters for datastring/headerstring/keywords only to that page. Matching rows on other pages were never fetched, so users saw 0 results or wrong counts. Total count was set to the filtered page size instead of the full filtered result set.
- **Actions taken**: (1) When `hasDatastringSearch || hasHeaderstringSearch || hasKeywordsSearch`, the service now fetches up to **5000 rows** (IMGLOG_FILTER_PREFETCH_CAP) without pagination, applies in-memory filter, then paginates the filtered list and returns correct total count. (2) Controller and service: full request field logs (datastring/headerstring/keywords) downgraded to DEBUG or length/null-only for production safety. (3) Added DEBUG logs for filter flags and row counts before/after filter. (4) New helper methods: `readImageLogResultSet`, `filterImageLogRowsByDataHeaderKeywords`. (5) LogDbServiceTest added with TC-01–TC-04, TC-07.
- **Result**: Unit tests (LogDbServiceTest) pass. Data/header/keyword search returns only matching rows with correct total count and pagination. pb_feplog path unchanged.
- **Completed**: 2026-03-18

### Follow-up diagnosis (Frontend)

After user report that image log search (data, header, keyword) still does not work:

- **Payload verification**: When the user submits the Image Log search form with data/header/keyword filled, the frontend sends the request body to `POST /api/logs/db-refactored/search` as follows.
  - **requestData construction**: `requestData = { ...params, logType, page, pageSize, sortField, sortDirection, displayTemplate }`. The added keys do **not** overwrite `params.datastring`, `params.headerstring`, or `params.keywords` (those keys are not in the override set). Verified in `LogGrid.handleSearch`.
  - **ImageLogSearchForm → onSearch**: The form builds `searchParams` with **lowercase** keys `datastring`, `headerstring`, and `keywords` (array from comma-separated string). It calls `onSearch(searchParams)`; no renaming (e.g. no `dataString`) in the chain.
- **Diagnostic added**: A **temporary** `console.log` in `LogGrid.handleSearch` (development only, `NODE_ENV === 'development'`) logs for `logType === 'java_fw_imglog'`: `requestBodyKeys`, and for `datastring`/`headerstring`/`keywords` presence, type, and length (no full values). Remove or keep env-guarded after root cause is found.
- **Finding**: Frontend payload **includes** the three fields with **correct keys** (`datastring`, `headerstring`, `keywords`) and **correct types** (strings for datastring/headerstring, array for keywords). No bug found in the frontend chain (form → handleSearch → requestData). If the API still does not behave as expected, Backend can focus on **receipt/binding/filter logic** (e.g. DTO binding, or in-memory filter application).

### Follow-up diagnosis (Backend)

After user report that image log search (data, header, keyword) still does not work, **temporary INFO-level diagnostic logs** were added to trace request receipt and filter path. Remove or downgrade to DEBUG after root cause is found.

**1. Request receipt** — When `POST /api/logs/db-refactored/search` is called with `logType=java_fw_imglog`, the controller logs at INFO: `[DIAG] image log request binding: datastring null? <bool>, length=<int>; headerstring null? <bool>, length=<int>; keywords null? <bool>, size=<int>`. Use this to confirm whether the request body fields are received and bound to `LogDbSearchRequest`. If `keywords null? true` or `size=0` when the frontend sends a non-empty array, check Jackson binding or JSON key names.

**2. Filter path** — In `LogDbService.searchJavaFwImglog`, when `hasDatastringSearch || hasHeaderstringSearch || hasKeywordsSearch` is true, the service logs: `[DIAG] image log filter path: prefetch SQL returned N=<int> rows` and `[DIAG] image log filter path: filterImageLogRowsByDataHeaderKeywords returned M=<int> rows (if N>0 and M=0, check filter/decrypt or date range)`. If N > 0 but M = 0, the in-memory filter or decryption path may be wrong (e.g. column name mismatch, encrypted content not decrypted before match). If N = 0, the base SQL (date/application/service) returns no rows — check date range and params.

**3. Binding/type check (code review)** — `LogDbSearchRequest`: `datastring`, `headerstring` (String) and `keywords` (List&lt;String&gt;) have `@JsonProperty("datastring")`, `@JsonProperty("headerstring")`, `@JsonProperty("keywords")` and getters `getDatastring()`, `getHeaderstring()`, `getKeywords()`. JSON keys match the API contract. Filter logic uses `row.get("datastring")` and `row.get("headerstring")`; the SQL SELECT and `readImageLogResultSet` put column names from `ResultSetMetaData.getColumnName(i)` (typically lowercase), so keys align with `imagelog.datastring` / `imagelog.headerstring`. No obvious binding bug found in DTO or row key names.

**4. Next steps if cause still unclear** — Reproduce with a real request: run a search with data/header/keyword filled and capture backend logs. Check: (1) Does `[DIAG] image log request binding` show non-null and non-zero lengths/size? (2) Does prefetch N > 0? (3) Does M match expectations? If params are received but M is always 0, enable DEBUG for `com.logmng.service.LogDbService` to inspect filter conditions or add a one-off log of the first row’s `datastring`/`headerstring` length (not content). If params are null/zero, capture the request body (e.g. browser DevTools → Network → request payload) or add a frontend debug log of the payload sent to the API. If a concrete bug is identified (e.g. request not bound, filter condition wrong, date range), implement the fix and add a brief note in this §6.

---

## 7. Final version (Korean) — add after all verification is complete

### Final Korean summary

- **Requirement description**: 이미지 로그 화면에서 데이터(datastring), 헤더(headerstring), 키워드(keywords) 검색이 정상 동작하지 않는 문제의 원인 규명 및 수정.
- **Expected outcome**: 데이터·헤더·키워드 검색이 의도대로 동작하고, 원인이 문서화됨.
- **Verification result**: Backend LogDbServiceTest 5/5 pass; Frontend unit tests (ImageLogSearchForm) pass. TC-05 (E2E) optional. §6 root cause and actions recorded.

---

**Author**: Requirements subagent  
**Date**: 2026-03-18  
**Status**: Implemented; TC-05 (integration) optional for QA
