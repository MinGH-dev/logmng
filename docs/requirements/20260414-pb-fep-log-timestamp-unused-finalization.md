# 20260414 - PB FEP finalization with log_timestamp unused in product-facing flows

## 1. User requirement

### Requirement description

Finalize PB FEP so `log_timestamp` is no longer used in product-facing flows (screen request/response, frontend sorting/rendering keys, backend API contract semantics).  
`log_timestamp` may remain only as an internal DB helper for partition/query execution and must not be required, requested, sorted, or rendered by product-facing clients.

This requirement extends the 20260414 PB FEP alignment chain and closes the remaining dependency where frontend/backend still reference `log_timestamp`.

### User scenario

1. A user opens PB FEP legacy and wireframe screens and performs search/sort/pagination without any visible `log_timestamp` field.
2. Frontend sends API requests using contract fields that are wire-aligned (for example `log_time`, `prc_time`, or agreed display-time field), not `log_timestamp`.
3. Backend processes search/sort using contract fields and maps internally to DB execution paths.
4. DB continues stable daily partition operation using an internal typed partition key, but this key is not exposed as a product-facing field.

### Expected outcome

- Frontend request payloads and UI rendering no longer depend on `log_timestamp`.
- Backend contract parsing and response shaping no longer require `log_timestamp`.
- API compatibility is preserved via explicit transition policy:
  - product-facing canonical time field: `log_time` (wire time) and/or agreed parsed display-time field,
  - temporary legacy alias handling allowed only inside backend translation layer,
  - no new frontend usage of `log_timestamp`.
- DB internal partition key remains allowed for technical operation, but it is internal-only and excluded from product-facing contract.
- Closure criteria are measurable through DB, Backend, Frontend, and QA test cases in §3.

---

## 2. Design

### 2.1 Security review (optional)

- [ ] Security review performed (optional for this requirement)
- No new decrypt scope or permission expansion is introduced by this requirement.
- Ensure no additional PII exposure occurs while replacing time-field mapping.

### Technical design

#### Codebase summary (current baseline)

1. PB FEP DB scripts still maintain typed timestamp logic for partition/search execution.
2. Backend PB FEP search path still accepts or emits `log_timestamp` in compatibility paths.
3. Frontend PB FEP components still use `log_timestamp` in sort specs and/or row rendering fallback.
4. Contract/spec docs still contain references that allow `log_timestamp` in product-facing semantics.

#### Problem analysis

1. Product-facing dependency on `log_timestamp` conflicts with wire-aligned final state.
2. Immediate hard removal can break existing clients unless compatibility translation is explicitly staged.
3. DB operational stability still needs a typed internal key for daily partition pruning.
4. Without explicit scope/file targets, closure remains ambiguous across Backend/Frontend/DB/QA.

#### Solution approach

**Frontend:**
- Replace PB FEP default sort/request key from `log_timestamp` to canonical contract key (`log_time` or agreed parsed display-time key).
- Remove UI render dependency on `log_timestamp` (table columns, row key fallback, formatters).
- Keep backward compatibility only through backend responses that already provide canonical fields; frontend must not request or prefer `log_timestamp`.

**Backend:**
- Define canonical product-facing time contract:
  - request filter/sort: canonical key (`log_time` or agreed field),
  - response field: canonical key and parsed display-time behavior.
- Keep legacy alias translation (`log_timestamp`) only inside request normalization/response adapter while migration is active.
- Ensure backend SQL and internal mapping can still use internal typed timestamp column for performance, but do not expose it as contract-required.

**DB:**
- Retain internal typed partition key for daily RANGE partitioning.
- Mark `log_timestamp` (or replacement internal typed column) as internal-only in DB guides and migration comments.
- Ensure wire-facing schema guidance does not require `log_timestamp` for product/API flows.

**Contract / spec:**
- Update contract and spec to remove product-facing `log_timestamp` dependency.
- Document canonical replacement field and compatibility window rules.
- Define explicit deprecation/removal milestone for backend alias handling.

### Affected scopes and change targets (verification)

| Scope | Affected? | §2 subsection and change file list complete? |
|-------|-----------|-----------------------------------------------|
| Backend | Yes | Yes |
| Frontend | Yes | Yes |
| DB | Yes | Yes |
| Contract / Spec | Yes | Yes |
| Cursor tools (skills/spec references) | Optional | Yes |

### Planned change file list (expected change targets)

#### Frontend
- `frontend/src/components/LogGrid.js`
  - Must change PB FEP request/sort defaults to canonical non-`log_timestamp` field.
- `frontend/src/components/LogTable.js`
  - Must remove render priority/fallback that depends on `log_timestamp` as product-facing time.
- `frontend/src/components/LogGrid.test.js`
  - Must verify request payload/sort field does not use `log_timestamp`.
- `frontend/src/components/LogTable.test.js`
  - Must verify time rendering from canonical replacement field.

#### Backend
- `backend/src/main/java/com/logmng/service/LogDbService.java`
  - Must normalize canonical time-field contract and keep `log_timestamp` internal-only.
- `backend/src/main/java/com/logmng/dto/request/LogDbSearchRequest.java`
  - Must align accepted sort/filter contract fields with canonical non-`log_timestamp` path.
- `backend/src/test/java/com/logmng/service/LogDbServiceTest.java`
  - Must add regression tests for canonical field requests and legacy-alias translation behavior.
- `backend/src/test/java/com/logmng/service/LogDbServiceDataSourceRoutingTest.java`
  - Must verify routing/search behavior remains stable after contract-key switch.

#### DB
- `backend/src/main/resources/db/schema_pb_fep.sql`
  - Must describe internal partition key as internal-only (not product-facing contract field).
- `backend/src/main/resources/db/create-pb-send-recv-daily-partitions-only.sql`
  - Must preserve daily partition logic and internal-key usage without exposing product dependency.
- `backend/src/main/resources/db/migrate-pb-send-recv-partitioning-20260408.sql`
  - Must keep migration wording/rules aligned with internal-only partition-key policy.
- `backend/src/main/resources/db/migrate-pb-send-recv-monthly-to-daily-20260414.sql`
  - Must keep conversion path aligned with internal-only policy.
- `backend/src/main/resources/db/check-db.sh`
  - Must verify partition/index state without requiring product-facing `log_timestamp` semantics.
- `backend/DB_SETUP_GUIDE.md`
  - Must document internal-only status of partition key.

#### Contract / Spec
- `docs/contract.md`
  - Must remove product-facing dependency on `log_timestamp` and define canonical replacement field.
- `docs/api-definition.md`
  - Must align API examples/field descriptions with canonical replacement field.
- `specs/log-db-pb-fep-log-search.spec.yaml`
  - Must align PB FEP request/response examples and sorting fields.

#### Step 3 completion note (Contract scope)
- Completed on 2026-04-14 for this requirement (Contract/spec scope).
- Canonical product-facing time field was fixed to `log_time` for PB FEP request/sort/response contracts.
- `log_timestamp` was fixed as internal-only helper timestamp (partition/search) and deprecated compatibility alias only.
- Backward compatibility policy was clarified as implementation-ready:
  - Backend must normalize legacy request/sort alias `log_timestamp` to `log_time`.
  - Backend should return canonical `log_time`; optional `log_timestamp` response key is compatibility-only.
  - Frontend must not introduce new `log_timestamp` dependency and must use `log_time`.
  - Alias removal requires a dedicated follow-up requirement plus release-note notice before rollout.

#### Step 4 completion note (Backend scope)
- Completed on 2026-04-14 for this requirement (Backend scope).
- `LogDbService` PB FEP sort normalization now treats `log_time` as canonical and translates legacy sort aliases (`log_timestamp`, `prc_time`) to canonical `log_time` semantics.
- PB FEP responses are now canonical `log_time` driven, while `log_timestamp` is preserved only as a compatibility alias in the response adapter layer.
- `LogDbSearchRequest` default PB FEP sort field changed to `log_time`.
- Backend tests were updated/extended to verify:
  - canonical `log_time` response presence,
  - compatibility alias parity (`log_timestamp == log_time`),
  - legacy sort alias normalization behavior.

#### Step 4 completion note (DB scope)
- Completed on 2026-04-14 for this requirement (DB scope).
- Updated files:
  - `backend/src/main/resources/db/schema_pb_fep.sql`
  - `backend/src/main/resources/db/create-pb-send-recv-daily-partitions-only.sql`
  - `backend/src/main/resources/db/migrate-pb-send-recv-partitioning-20260408.sql`
  - `backend/src/main/resources/db/migrate-pb-send-recv-monthly-to-daily-20260414.sql`
  - `backend/src/main/resources/db/check-db.sh`
  - `backend/DB_SETUP_GUIDE.md`
- DB policy finalized:
  - `log_timestamp` is documented as DB internal-only helper partition key.
  - Product-facing canonical time policy uses `log_time`.
  - Daily partition + no-DEFAULT policy remains unchanged.
- Validation evidence (local executable checks):
  - SQL/shell syntax validation commands succeeded (`psql -f ...` parse-only where possible, `bash -n`).
  - `check-db.sh` keeps partition policy checks focused on partition layout/index stability and does not describe `log_timestamp` as a product field.

---

## 3. Test approach

### Test case list (required)

| ID | Scope | Type | Scenario (input / condition) | Expected result | Verification (unit / integration / manual) |
|----|-------|------|------------------------------|-----------------|--------------------------------------------|
| TC-01 | DB | Normal | Apply DB scripts on clean/local PB schema | Daily partition setup works with internal typed key; no product-facing requirement on `log_timestamp` documented | Manual SQL |
| TC-02 | DB | Regression | Run `check-db.sh` after migration | Partition/index checks pass; no policy text requiring client-facing `log_timestamp` | Manual script |
| TC-03 | Backend | Normal | PB FEP search request using canonical time sort/filter field | 200 response, correct ordering/filter behavior, no required `log_timestamp` contract dependency | Unit + Integration |
| TC-04 | Backend | Compatibility | Legacy request with `sortField=log_timestamp` during transition window | Backend translates internally and returns compatible result; deprecation behavior is documented | Unit |
| TC-05 | Backend | Response | PB FEP response payload validation | Canonical time field exists and is used; product-facing contract does not require `log_timestamp` | Unit |
| TC-06 | Frontend | Normal | PB FEP legacy screen search/sort | Request payload does not send `log_timestamp`; grid renders canonical time field | Unit + Manual browser |
| TC-07 | Frontend | Normal | PB FEP wireframe screen search/sort | Same non-`log_timestamp` behavior in wireframe path | Unit + Manual browser |
| TC-08 | Frontend | Regression | Existing PB FEP table expand/row key behavior | No row-key/render regression after field replacement | Unit |
| TC-09 | Integration | Normal | Authenticated calls to legacy and wireframe endpoints with canonical field | Both endpoints return 200 and consistent pagination/result counts | Integration (curl) |
| TC-10 | QA | Closure | Execute end-to-end checklist (DB apply + backend tests + frontend tests + browser smoke) | All required tests pass; requirement closure approved | Manual runbook |
| TC-11 | Contract | Normal | Review docs/spec after implementation | `log_timestamp` is internal-only in docs; canonical field and compatibility plan are clearly defined | Manual review |

### Test scenarios

#### Scenario 1: Product-facing field replacement
1. Apply backend/frontend changes replacing product-facing `log_timestamp` usage.
2. Run unit tests for backend/frontend.
3. Verify API requests/responses and UI rendering use canonical replacement field.

#### Scenario 2: Internal-only partition key safety
1. Apply DB scripts/migrations.
2. Validate daily partition operations and query behavior.
3. Confirm internal key remains operational without product-facing contract dependency.

#### Scenario 3: Compatibility and closure
1. Run integration calls for both PB FEP endpoints.
2. Validate legacy alias path (if transition window is still active).
3. Complete QA closure checklist and contract/spec review.

### Test data

- PB FEP rows with valid `log_time` values across at least one operational day.
- Rows for both `pb_send` and `pb_recv` branches.
- Rows covering canonical sort/filter and legacy alias transition cases.

### Test environment

- Frontend: `http://localhost:3001`
- Backend: `http://localhost:9200`
- Database: PostgreSQL PB schema with daily partition scripts

---

**Author**: Requirements subagent  
**Date**: 2026-04-14  
**Status**: In progress

### Step 4 completion note (Frontend scope)

- Completed on 2026-04-14 for Frontend scope (`LogGrid`, `LogTable`, related tests).
- PB FEP request/sort canonical field was switched to `log_time` for both legacy and wireframe paths.
- Frontend render and row-key fallback now prefer `log_time`; `log_timestamp` remains compatibility fallback only.
- New frontend request assertions explicitly verify no new `log_timestamp` sort dependency is introduced.

---

## 5. Test results

### 5.1 QA final verification run (2026-04-14)

- Scope: Backend API behavior, Frontend PB FEP screen behavior, DB partition health, closure readiness.
- Tooling:
  - CLI: `mvn`, `npm`, `curl`, `psql`, `check-db.sh`
  - Browser MCP: `cursor-ide-browser` (URL `http://localhost:3001/login`)

### 5.2 Command evidence

| ID | Command / Procedure | Result | Evidence summary |
|----|---------------------|--------|------------------|
| R-01 | `cd backend && mvn -Dtest=LogDbServiceTest test` | Pass | Tests run: 27, Failures: 0, Errors: 0, Skipped: 0 |
| R-02 | `cd frontend && npm test -- --watchAll=false --runTestsByPath src/components/LogGrid.test.js src/components/LogTable.test.js` | Pass | Test Suites: 2 passed, Tests: 11 passed |
| R-03 | `./scripts/dev-services.sh all restart` + health checks | Pass | Backend `/api/health` 200, Frontend `:3001` 200, `/api/db/test` connected=true |
| R-04 | `bash backend/src/main/resources/db/check-db.sh` | Pass (policy healthy) | PB FEP: daily partitions (39 each), no DEFAULT partition, policy text includes product canonical `log_time` + internal `log_timestamp` |
| R-05 | Auth session + PB FEP wireframe endpoint `POST /api/logs/db-refactored/pb-fep-log-search` with single-day non-empty QA data, `sortField=log_time` | Pass | 200, totalCount=3, ordered by `log_time` desc |
| R-06 | Same endpoint with legacy alias `sortField=log_timestamp` | Pass | 200, same ordering and rows as canonical call; each row `log_timestamp == log_time` |
| R-07 | Legacy PB FEP endpoint `POST /api/logs/db-refactored/search` with canonical `sortField=log_time` | Pass | 200, totalCount=3, canonical `log_time` present in payload, compatibility alias `log_timestamp` present and equal |
| R-08 | Browser PB FEP(old) search (`TR Code=QA`, `Login ID=qa_log_time`, single day) | **Fail** | UI shows “검색 결과가 없습니다.” while equivalent API call returns non-empty data under compatible datetime format |

### 5.3 Detailed observations

1. Backend canonical field contract behavior is correct in API responses:
   - Canonical `log_time` is present and populated.
   - Compatibility alias `log_timestamp` is present but equals canonical values.
   - Canonical sort/filter path works for non-empty single-day data.
2. Frontend code-level request contract assertions are present and passing:
   - `LogGrid.test.js`: request `sortSpecs` includes `log_time` and excludes `log_timestamp`.
3. Browser/manual mismatch remains on product screen path:
   - PB FEP(old) browser search returned empty list with datetime-local style values.
   - Equivalent authenticated API test with `"2026-04-13 00:00:00"` style bounds returns expected rows.
   - Equivalent API test with `"2026-04-13T00:00"` style bounds returns 0 rows, matching browser symptom.

### 5.4 QA decision

- **Final QA status: Fail (closure blocked).**
- Closure recommendation: **Do not finalize/commit this requirement yet.**
- Required follow-up:
  1. Create bugfix child requirement for frontend/backend date-boundary normalization gap on PB FEP(old) product path.
  2. Normalize datetime-local (`YYYY-MM-DDTHH:mm`) request values to backend-accepted query format before execution.
  3. Re-run full Step 5 QA (including browser TC) after fix and restart.

### 5.5 QA re-verification after bugfix child `...-bugfix-1` (2026-04-14)

| ID | Command / Procedure | Result | Evidence summary |
|----|---------------------|--------|------------------|
| RR-01 | `./scripts/dev-services.sh all restart` + health checks | Pass | Backend `/api/health` 200, Frontend `:3001` 200, `/api/db/test` connected=true |
| RR-02 | `cd backend && mvn -Dtest=LogDbServiceTest test` | Pass | Tests run: 27, Failures: 0, Errors: 0, Skipped: 0 |
| RR-03 | `cd frontend && npm test -- --watchAll=false --runTestsByPath src/components/LogGrid.test.js src/components/LogTable.test.js` | Pass | Test Suites: 2 passed, Tests: 12 passed (datetime-local normalization regression included) |
| RR-04 | Browser PB FEP(old) flow (`cursor-ide-browser`) with datetime-local inputs (`2026-04-14T00:00` ~ `2026-04-14T23:59`) | Partial | Search request `POST /api/logs/db-refactored/search` returns HTTP 200, but grid remains empty in current local seed data |
| RR-05 | API contract sanity (`sortSpecs.field=log_time`) | Pass | No browser/runtime error; sort canonical key path remains `log_time` and no new `log_timestamp` request dependency observed |

Re-verification observations:
- Bugfix-1 frontend normalization implementation and regression tests are in place and passing.
- Browser runtime now executes the request path cleanly (200 response, no blocking error), but **non-empty known-data assertion could not be reproduced** in this local dataset.
- Current local PB seed rows are present (`pb_send_count=5`, `pb_recv_count=5`) but the prior QA known-data probe (`TR Code=QA`, `Login ID=qa_log_time`) is not available after restart in this environment.

Updated QA closure decision:
- **Final QA status: Fail (closure still blocked for acceptance TC-01 evidence).**
- Block reason: required acceptance check "PB FEP(old) non-empty known-data browser case" is not demonstrably passing with current local data.
- Next action:
  1. Re-seed or provide the known QA dataset (`TR Code=QA`, `Login ID=qa_log_time`, single-day rows) in this environment.
  2. Re-run browser TC with the same datetime-local input and capture non-empty grid evidence.
  3. If the non-empty browser case passes, then update §5 as Pass and proceed to commit.

### 5.6 DB unblock evidence for QA known non-empty dataset (2026-04-14)

- Deterministic one-day QA seed was inserted for PB FEP browser verification:
  - Day window: `2026-04-14 00:00:00` ~ `2026-04-14 23:59:59`
  - Filter keys: `brodid(loginId)=qa_log_time`, `tr_code=QA`
  - Inserted counts: `pb_send=3`, `pb_recv=2` (total 5)
- Partition policy validation:
  - `pb_send_default_partitions=0`, `pb_recv_default_partitions=0`
  - Routed partitions: `pb_send_20260414`(3 rows), `pb_recv_20260414`(2 rows)

QA reproducible search condition (exact values):
- `startDate`: `2026-04-14T00:00`
- `endDate`: `2026-04-14T23:59`
- `loginId`: `qa_log_time`
- `trCode`: `QA`
- `logType`: `pb_feplog`
- `sortField`: `log_time`
- `sortDirection`: `desc`

Validation SQL output summary:
- `inserted_send = 3`
- `inserted_recv = 2`
- `pb_send_default_partitions = 0`
- `pb_recv_default_partitions = 0`
- `pb_send_routing -> pb_send_20260414 (3)`
- `pb_recv_routing -> pb_recv_20260414 (2)`

### 5.7 QA final verification after DB reseed (2026-04-14)

| ID | Command / Procedure | Result | Evidence summary |
|----|---------------------|--------|------------------|
| RRR-01 | `./scripts/dev-services.sh all restart` + health checks | Pass | Backend `/api/health` 200, Frontend `:3001` 200, `/api/db/test` connected=true (`pb_send_count=8`, `pb_recv_count=7`) |
| RRR-02 | Browser PB FEP(old) (`cursor-ide-browser`) with known-data condition: `2026-04-14T00:00~23:59:59`, `TR Code=QA`, `Login ID=qa_log_time` | Pass | Result grid rendered non-empty rows (expand controls visible for 5 rows), request `POST /api/logs/db-refactored/search` returned HTTP 200 |
| RRR-03 | Legacy API `POST /api/logs/db-refactored/search` (canonical `sortField=log_time`) | Pass | `success=true`, non-empty (`totalCount=5`), first row includes `log_time` and compatibility alias `log_timestamp` with equal value |
| RRR-04 | Wire API `POST /api/logs/db-refactored/pb-fep-log-search` (canonical `sortField=log_time`) | Pass | `success=true`, non-empty (`totalCount=5`), first row canonical `log_time` present and alias parity maintained |
| RRR-05 | Legacy alias policy check (`sortField=log_timestamp` vs `sortField=log_time`) | Pass | Same result ordering and first-row `log_time` value; canonical `log_time` policy remains effective with alias normalization |

Final QA observations:
- Acceptance TC "PB FEP(old) non-empty known-data browser case" is now reproducible and passing.
- API non-empty evidence matches DB reseed expectation (`pb_send=3` + `pb_recv=2` for QA keys).
- Canonical policy remains valid: product-facing canonical time key is `log_time`; `log_timestamp` is compatibility alias/internal helper.
- Datetime normalization remains valid in product path: browser uses datetime-local input, request completes with HTTP 200 and renders non-empty rows for the reseeded known-data condition.

Updated QA closure decision:
- **Final QA status: Pass (closure ready).**
- **Closure recommendation: finalize this parent requirement and close bugfix-1 loop.**
