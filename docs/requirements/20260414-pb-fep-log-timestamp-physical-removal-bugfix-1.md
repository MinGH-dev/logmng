# 20260414 - PB FEP old search residual blocker bugfix-1

## 1. User requirement

### Requirement description
After physical removal of `log_timestamp` from PB FEP tables, DB and API baseline checks pass, but PB FEP(old) non-empty search still returns 0 rows on the legacy path. This child bugfix requirement isolates the residual blocker and requires endpoint/filter mapping alignment for old search.

### User scenario
1. QA/operator prepares known PB FEP data (`TR Code=QA`, `Login ID=qa_log_time`) within the selected date range.
2. User opens PB FEP(old) screen and executes search using matching filters.
3. API request succeeds (HTTP 200 / success shape), but UI and API payload still show empty result (`totalCount=0`).
4. **Problem**: old endpoint/filter mapping does not retrieve known existing data after transition.

### Expected outcome
- Legacy endpoint used by PB FEP(old) (`/api/logs/db-refactored/search`) must return non-empty rows when known matching data exists.
- Old screen filter keys and backend query mapping must be aligned (field name, normalization, null/blank behavior, date range semantics).
- Result parity must be verified between old endpoint path and canonical PB FEP search path for the same dataset and filter set.

## 2. Design

### Technical design

#### Problem analysis
1. DB physical removal target is complete, but residual blocker remains on old search retrieval path.
2. Runtime evidence shows successful request handling with empty data, indicating query/filter mapping mismatch rather than API transport failure.
3. Without diagnostic-first tracing of filter transformation and SQL binding, root cause cannot be confirmed safely.

#### Diagnostic phase (mandatory for error/bug fix)
- **Phase 0 (diagnostic-first):**
  1. Add DEBUG-only logs at old endpoint pipeline: request payload, normalized filter DTO, effective query conditions, bound parameters, and result row count.
  2. Reproduce with fixed known dataset and deterministic filter set (TR Code, Login ID, time range).
  3. Capture logs for both paths:
     - old endpoint `/api/logs/db-refactored/search`
     - canonical endpoint `/api/logs/db-refactored/pb-fep-log-search`
  4. Compare transformed filters and SQL conditions to identify exact divergence point.
  5. Confirm root cause from logs before implementing logic changes.
- **Production safety:** Diagnostic logs must remain DEBUG-only and must be removed/downgraded after verification.

#### Solution approach
**Backend:**
- Align legacy endpoint filter-to-query mapping with canonical PB FEP mapping for common fields:
  - `TR Code`, `Login ID`, `log_time` range, and nullable/blank handling.
- Ensure date/time boundary logic is identical between old and canonical paths.
- Ensure query builder does not rely on removed/legacy timestamp aliases.

**Frontend:**
- Verify PB FEP(old) request payload keys match backend-expected legacy mapping contract.
- Align filter serialization (empty string vs null omission) with backend mapping assumptions.

**Contract / Spec:**
- Clarify old endpoint filter field semantics and parity rules with canonical endpoint for shared filters.

### Planned change file list (expected targets)

#### Backend
- `backend/src/main/java/com/logmng/service/LogDbService.java`
  - Must add diagnostic logging points and align old endpoint filter mapping.
- `backend/src/main/java/com/logmng/dto/request/LogDbSearchRequest.java`
  - Must verify/align legacy filter field handling where needed.
- `backend/src/test/java/com/logmng/service/LogDbServiceTest.java`
  - Must add regression tests for old endpoint non-empty retrieval and filter mapping parity.

#### Frontend
- `frontend/src/components/LogGrid.js`
  - Must verify old screen filter payload mapping to legacy endpoint.
- `frontend/src/components/LogTable.js`
  - Must verify rendering path for non-empty old endpoint result.
- `frontend/src/components/LogGrid.test.js`
  - Must add/update tests for old endpoint filter serialization and non-empty render.
- `frontend/src/components/LogTable.test.js`
  - Must add/update tests for old endpoint result alignment.

#### Contract / Spec / docs
- `docs/contract.md`
  - Must document old endpoint/filter mapping alignment rule with canonical path.
- `specs/log-db-pb-fep-log-search.spec.yaml`
  - Must reflect shared filter semantics/parity for old and canonical search behavior where applicable.

## 3. Test approach

### Test case list (required)
| ID | Scope | Type | Scenario (input / condition) | Expected result | Verification |
|----|-------|------|------------------------------|-----------------|--------------|
| TC-01 | Backend | Diagnostic | Run old endpoint with known dataset and fixed filters while DEBUG logs enabled | Logs show request -> normalized filters -> bound query conditions -> non-empty or mismatch evidence | Integration |
| TC-02 | Backend | Diagnostic parity | Run canonical endpoint with same dataset/filters and compare log traces | Divergence point between old vs canonical mapping is explicitly identified | Integration |
| TC-03 | Backend | Regression | Apply mapping fix and call old endpoint `/api/logs/db-refactored/search` with known matching filters | Response `totalCount > 0` and rows returned | Unit + Integration |
| TC-04 | Backend | Regression parity | Execute old and canonical endpoints with same filter set | Both paths return equivalent row inclusion for shared fields | Integration |
| TC-05 | Frontend | Regression | PB FEP(old) screen search (`TR Code=QA`, `Login ID=qa_log_time`, matching range) | UI table shows non-empty rows and no runtime error | Unit + Manual/browser |
| TC-06 | Frontend | Edge | Empty/blank filter combinations on PB FEP(old) | Serialization matches backend expectations (null/blank/omit), no false-empty due to mapping | Unit |
| TC-07 | Contract | Regression | Review contract/spec docs for old endpoint filter semantics | Shared filter semantics and parity notes are documented and consistent | Doc review |

### Test scenarios
#### Scenario 1: Diagnostic-first root cause confirmation
1. Seed known PB FEP rows in selected date range.
2. Enable DEBUG logs for old/canonical search mapping path.
3. Execute identical filter set on both endpoints.
4. Compare normalization and SQL condition traces.
5. Confirm root cause before code fix.

#### Scenario 2: Old endpoint non-empty recovery
1. Apply mapping alignment fix.
2. Call old endpoint with known matching filters.
3. Verify API non-empty response and frontend non-empty rendering.

#### Scenario 3: Mapping parity and edge handling
1. Run parity checks for shared filters on old vs canonical paths.
2. Run empty/blank/null filter edge cases.
3. Verify no unintended zero-result behavior from serialization mismatch.

### Test data
- Known PB FEP dataset including rows for `TR Code=QA`, `Login ID=qa_log_time`, and matching `log_time` range.
- Additional rows outside range for negative control.

### Test environment
- Backend: `http://localhost:9200`
- Frontend: `http://localhost:3001`
- DB: PostgreSQL runtime schema with physical removal already completed.

---

**Author**: Requirements (subagent)  
**Date**: 2026-04-14  
**Status**: In progress

## 5. Test results

### Test run date
- 2026-04-14 (QA close check rerun after backend fix handoff)

### Scope verdict
- Overall: **FAIL** (child blocker unresolved).

### Detailed results (requested close-check set)
1. **DB (`log_timestamp` physically absent, no-default policy)**: **PASS**
   - `pb_send`/`pb_recv` `log_timestamp` column check returned 0 rows.
   - Parent partition `DEFAULT` check returned 0 rows.
2. **Ingest without `log_timestamp`**: **PASS**
   - Probe insert using `log_time` path succeeded and partition routing worked.
3. **Legacy endpoint `/api/logs/db-refactored/search` non-empty (known single-day data)**: **FAIL**
   - API call succeeds (HTTP 200 / success) but `totalCount=0`, rows empty.
4. **Browser PB FEP(old) same condition non-empty**: **FAIL**
   - Browser MCP flow executed with `TR Code=QA`, `Login ID=qa_log_time`, same day window.
   - Search request fired (`POST /api/logs/db-refactored/search` 200), but UI non-empty acceptance not met.
5. **`sortField=log_timestamp` rejection**: **PASS**
   - `/api/logs/db-refactored/pb-fep-log-search` rejects with `INVALID_INPUT`.

### Evidence update: deterministic reseed for legacy old endpoint
- Reseed script: `backend/src/main/resources/db/seed-pb-fep-qa-known-data-20260414.sql`
- Reseed run (2026-04-14): `psql -U logmng -h localhost -p 5432 -d logmng -v ON_ERROR_STOP=1 -f backend/src/main/resources/db/seed-pb-fep-qa-known-data-20260414.sql` (**PASS**)
- Deterministic condition values (single-day):
  - Date range (`log_time`): `20260414000000` ~ `20260414235959`
  - `tr_code`: `QA`
  - `brodid` (loginId): `qa_log_time`
- Verification SQL counts (`> 0`):
  - `pb_send`: `3`
  - `pb_recv`: `2`
  - `pb_total` (`pb_send UNION ALL pb_recv`): `5`
- Constraint/policy compliance:
  - Seed uses canonical `log_time` filter and does **not** reference removed `log_timestamp`.
  - Intended for parents already partitioned by `log_time` with **no DEFAULT partition** (policy unchanged).

### Close recommendation
1. Child bugfix-1 remains **open** (close criteria not satisfied).
2. Next action: backend old-path retrieval/mapping correction for same-day known-condition query.
3. QA will close and commit docs only after #3/#4 pass.

### Final QA rerun after DB reseed (2026-04-14)
- Reseed status: **Available and applied**.
  - `psql -U ghmin -h localhost -p 5432 -d logmng -v ON_ERROR_STOP=1 -f backend/src/main/resources/db/seed-pb-fep-qa-known-data-20260414.sql`
  - DB verification: known rows exist (`pb_send=3`, `pb_recv=2`) for `TR Code=QA`, `Login ID=qa_log_time`, date `2026-04-14`.

- Requested close-check results:
1. **Legacy endpoint non-empty (`/api/logs/db-refactored/search`)**: **FAIL**
   - Response: `success=true`, but `data=[]`, `pagination.totalCount=0`.
2. **Browser PB FEP(old) non-empty with same condition**: **FAIL**
   - Browser MCP (`cursor-ide-browser`) search with same condition shows no result rows.
   - Network: `POST /api/logs/db-refactored/search` HTTP 200.
   - Console: search success logs present, but non-empty acceptance criterion unmet.
3. **DB physical removal / no-default policy**: **PASS**
   - `log_timestamp` absent on `pb_send`/`pb_recv`.
   - No `DEFAULT` partition found on PB parent tables.
   - Ingest probe insert without `log_timestamp` succeeds and routes to day partition.
4. **Docs update + close recommendation**: **DONE**
5. **Commit docs if all pass**: **SKIPPED** (because #1/#2 failed).

### Updated close recommendation
1. Keep child requirement **open**.
2. Backend owner should fix old endpoint retrieval parity for known-condition query on legacy path.
3. QA reruns the same condition set after backend fix; commit only when all requested checks pass.

### Final closure QA rerun (2026-04-14, after backend old-endpoint fix)
- Overall: **PASS (child close-ready)**.
- Rerun condition: PB FEP(old), `TR Code=QA`, `Login ID=qa_log_time`, single-day window `2026-04-14`.
- Required check results:
1. **Legacy old endpoint non-empty (`/api/logs/db-refactored/search`)**: **PASS**
   - Response returned success with `pagination.totalCount=5` and non-empty row list.
2. **Browser PB FEP(old) same condition non-empty**: **PASS**
   - Browser MCP (`cursor-ide-browser`) search produced non-empty table state (multiple expandable row controls visible).
   - Corresponding network call `POST /api/logs/db-refactored/search` returned HTTP `200`.
3. **DB physical removal + no-default policy still valid**: **PASS**
   - `pb_send`/`pb_recv` remain without `log_timestamp`; no DEFAULT partition detected.
4. **`sortField=log_timestamp` rejection still enforced**: **PASS**
   - `/api/logs/db-refactored/pb-fep-log-search` returns `INVALID_INPUT` for `sortField=log_timestamp`.
5. **Docs update + commit gate**: **PASS**
   - This child doc and parent doc were updated with final closure evidence.

### Final close recommendation
1. Child blocker is resolved and this child requirement is **ready to close**.
2. QA accepts closure for parent/child chain under the specified known condition.

### Backend residual blocker diagnosis/fix note (2026-04-14)
- Root cause confirmed on legacy path (`/api/logs/db-refactored/search`):
  - `endDate` passed as same-day midnight in ISO form (`yyyy-MM-ddTHH:mm:ss.SSS`) was not expanded to end-of-day.
  - Old path SQL uses lexical predicate on `log_time`:
    - `log_time >= toPbFeplogLogTimeLexical(startDateTime)`
    - `log_time <= toPbFeplogLogTimeLexical(endDateTime)`
  - With unexpanded midnight endDate, effective WHERE became `log_time <= YYYYMMDD000000`, excluding known same-day rows.
- Fix applied:
  - `LogDbSearchRequest#getEndDateAsDateTime` now treats date-only / explicit start-of-day (including ISO midnight forms) as end-of-day (`23:59:59.999`) for legacy PB FEP search semantics.
  - Existing contract rule remains unchanged: `sortField=log_timestamp` is still rejected with `INVALID_INPUT`.
- Regression tests added:
  - Legacy same-day known format range test retained and passing.
  - New regression: ISO midnight endDate (`2026-04-14T00:00:00.000`) returns same-day known rows (non-empty).

### Backend fix note (2026-04-14, residual blocker)
- Root cause confirmed in old PB FEP path SQL/date binding:
  - Legacy `/api/logs/db-refactored/search` used `log_time` predicates with `Timestamp` parameters, while runtime PB FEP schema stores `log_time` as lexical string `yyyyMMddHHmmss`.
  - This type/format mismatch made same-day known-condition filtering unstable and could produce empty result despite existing rows.
- Fix applied:
  - In `LogDbService`, PB FEP date predicates now bind normalized lexical values (`yyyyMMddHHmmss`) for both start/end bounds, matching physical `log_time` schema.
  - Existing contract behavior kept: `sortField=log_timestamp` is still rejected with `INVALID_INPUT`.
- Regression tests added:
  - Added old-path known-format same-day test in `LogDbServiceTest` to ensure known rows are returned for `2026-04-14 00:00:00 ~ 23:59:59`.
  - Updated PB fixtures/schema in backend unit test resources to use PB physical `log_time` string format.

### Closure QA rerun (2026-04-14, after old-endpoint fix handoff)
- Rerun condition (same as close criteria):
  - `startDate=endDate=2026-04-14`, `TR Code=QA`, `Login ID=qa_log_time`.
- Restart/health:
  - `./scripts/dev-services.sh all restart` pass.
  - `GET /api/health` 200, frontend 3001 HTTP 200, `GET /api/db/test` connected=true.
- Requested checks:
  1. **Old endpoint non-empty** (`/api/logs/db-refactored/search`): **FAIL**
     - Authenticated response: `success=true`, `pagination.totalCount=0`, `data=[]`.
  2. **Browser PB FEP(old) non-empty same condition**: **FAIL (blocked by API empty)**
     - Same runtime condition through old path remains empty; browser close criterion not met.
  3. **DB physical removal + no-default pass**: **PASS**
     - `pb_send`/`pb_recv` `log_timestamp` absent.
     - no `DEFAULT` partition attached.
     - insert probe without `log_timestamp` routed successfully.
  4. **`sortField=log_timestamp` rejected**: **PASS**
     - `pb-fep-log-search` returns `INVALID_INPUT` for `sortField=log_timestamp`.
- Close/commit decision:
  - Child requirement remains **open**.
  - **No commit** in this run (must-pass #1/#2 failed).
