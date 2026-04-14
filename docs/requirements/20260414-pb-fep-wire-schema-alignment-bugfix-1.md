# 20260414-pb-fep-wire-schema-alignment-bugfix-1 - PB FEP UI datetime-local serialization returns empty result set

**Parent requirement ID**: `20260414-pb-fep-wire-schema-alignment`  
**Bugfix sequence**: 1  
**Failure scope**: `frontend`

## 1. Discovery

- **When**: During QA re-verification after parent requirement rollout
- **What failed**:
  - API `curl` using `startDate: "2026-04-13 00:00:00"` and matching end date returns rows as expected.
  - Browser UI sends `datetime-local` value (`2026-04-13T00:00` style) from PB FEP search form.
  - API responds `200`, but response `pagination.totalCount=0` and empty `data` for that UI payload.
- **Conclusion at discovery**:
  - DB wire schema/partitioning from the parent requirement is functioning for the same business query when date format matches backend expectation.
  - The remaining blocker is date serialization/format handling between PB FEP frontend request payload and backend parsing behavior.

## 2. Error scope

- **Failure scope**: `frontend` (set by QA evidence)
- **Layer**: `frontend` request payload normalization path for PB FEP search
- **Symptom**: PB FEP screen query returns no rows only when date values come from browser `datetime-local` format (`YYYY-MM-DDTHH:mm`).
- **Impact**:
  - Screen: PB FEP(old) search (`pb-feplog`) is not trustworthy for users because visible result can be empty despite valid data.
  - API endpoint in flow: `POST /api/logs/db-refactored/search` (and potentially the PB FEP wireframe endpoint if it reuses the same date-building path).

## 3. Cause (estimated)

- Frontend currently forwards `datetime-local` raw values without normalization to the backend-expected date-time string format used by successful API calls.
- Request-building logic for PB FEP search does not enforce a canonical outbound date format shared with backend contract expectations.
- Existing screen/tests did not include regression coverage for `datetime-local` (`YYYY-MM-DDTHH:mm`) outbound payload compatibility.

## 4. Action

- **Primary fix direction (frontend-first)**:
  - Normalize PB FEP request date params in frontend before API call so outbound `startDate`/`endDate` are converted to backend-compatible format (e.g., `YYYY-MM-DD HH:mm:ss`) consistently.
  - Apply this normalization only to the PB FEP request-construction path (or shared serializer used by PB screens) to avoid unintended behavior changes on unrelated screens.
- **Compatibility guardrail**:
  - Preserve existing search behavior and payload shape for already-working screens.
  - If product chooses backend enhancement instead, backend may accept ISO-T (`YYYY-MM-DDTHH:mm`) in addition to existing format, but the frontend bugfix should still keep current screens stable.
- **Expected change targets (tentative, frontend scope)**:
  - `frontend/src/components/LogGrid.js` (PB FEP request body assembly path)
  - `frontend/src/components/SearchForm.js` (date field normalization hook if request payload is assembled there)
  - Related frontend PB search tests (`SearchForm.test.js`, `LogGrid.test.js`) to lock regression

## 5. Verification

### Test execution summary (QA re-verification)

- **Date**: 2026-04-14
- **Scope**: Frontend bugfix re-verification after `LogGrid.js` datetime-local normalization update
- **Environment**: local (`frontend:3001`, `backend:9200`)

| ID | Check | Command / Procedure | Result | Evidence |
|----|-------|---------------------|--------|----------|
| TC-01 | Frontend unit regression (changed area) | `cd frontend && npm test -- --watchAll=false --runInBand LogGrid.test.js` | **Pass** | 1 suite / 11 tests passed; includes `pb-feplog legacy search normalizes datetime-local values to backend format` |
| TC-02 | API baseline with backend-expected datetime format | `POST /api/logs/db-refactored/search` with `startDate/endDate: "2026-04-13 00:00:00" ~ "2026-04-13 23:59:59"` | **Pass** | `success: true`, `pagination.totalCount: 4` (known dataset, `loginId=local_decrypt`) |
| TC-02a | API comparison payload for UI-equivalent filter | Same endpoint with `trCode: "SLDECT01"` | **Pass** | `success: true`, `pagination.totalCount: 1` |
| TC-03 | Browser E2E (prior blocker) | Browser MCP (`cursor-ide-browser`), login -> PB FEP(old) form input `2026-04-13T00:00` / `2026-04-13T23:59:59`, `loginId=local_decrypt`, `trCode=SLDECT01` -> search | **Pass** | Grid rendered non-zero row (`log_timestamp 2026-04-13 10:20:00`, `tr_code SLDECT01`) and no false-empty result |
| TC-04 | Minimal non-pb_feplog regression | Browser: switch to `Java FW Image Log` and execute search; network status check | **Pass** | UI route/screen loads normally; `POST /api/logs/db-refactored/search` returns HTTP 200; no functional blocker observed |

### Restart and health check

| Item | Command | Result |
|------|---------|--------|
| Frontend restart | `./scripts/dev-services.sh frontend restart` | **Pass** |
| Backend health | `curl -sS http://localhost:9200/api/health` | **Pass** (HTTP 200) |
| Frontend health | `curl -sS -o /dev/null -w "%{http_code}" http://localhost:3001` | **Pass** (HTTP 200) |

### Browser verification detail (required for frontend scope)

- **Tool / base URL**: `cursor-ide-browser` / `http://localhost:3001`
- **Executed flow**:
  1. Login (`20261001` / `user123`)
  2. Open `PB FEP(old)` screen
  3. Input datetime-local values (`YYYY-MM-DDTHH:mm[:ss]`) and search
  4. Confirm non-zero row rendering for known data condition
- **Network checks**:
  - `POST /api/logs/db-refactored/search` returned **200** during PB FEP(old) search
  - Java FW minimal regression check also kept search call at **200**
- **Console checks**:
  - No blocking error observed for this scenario
  - Search success logs observed (`✅ 검색 성공`)

### Conclusion

- **Result**: Re-verification **PASS**
- **Prior blocker status**: **Closed**
  - Previously reproduced false-empty result (`datetime-local` from UI causing empty grid despite existing data) is no longer reproduced under the verified PB FEP(old) condition.
  - API baseline behavior with backend-expected datetime format remains correct.

### Minimal test plan for this bugfix

| ID | Scope | Type | Scenario | Expected result | Verification |
|----|-------|------|----------|-----------------|--------------|
| TC-01 | Frontend | Regression | PB FEP form uses `datetime-local` input (`2026-04-13T00:00` / `2026-04-13T23:59`) and sends search request | Outbound request date params are normalized to backend-compatible format; API returns non-empty rows for known dataset | Frontend unit + browser/manual |
| TC-02 | Integration | Regression | Same search conditions compared between curl (`YYYY-MM-DD HH:mm:ss`) and browser UI | Browser result count matches curl baseline for same filters | Manual/API+browser |
| TC-03 | Frontend | Safety | Run existing PB/other log search tests after normalization change | Existing screens and search flows remain unchanged (no regression) | Frontend unit (`npm test`) |

### Re-verification completion criteria

- QA reruns PB FEP(old) browser flow and confirms `검색` with UI date inputs no longer yields false empty results.
- For equivalent query conditions, browser and curl return aligned `totalCount`.
- Failure scope can be closed as frontend once the above checks pass.

---

**Author**: Requirements subagent  
**Date**: 2026-04-14  
**Status**: Draft for Frontend handoff
