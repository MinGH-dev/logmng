# 20260414-pb-fep-log-timestamp-unused-finalization-bugfix-1 — PB FEP(old) datetime-local query returns empty results

**Parent requirement ID**: `20260414-pb-fep-log-timestamp-unused-finalization`  
**Bugfix sequence**: 1

## 1. Discovery

- **When**: During Step 5 QA final verification (2026-04-14)
- **What failed**: Browser verification on PB FEP(old) product path returned empty results for non-empty single-day QA data.

## 2. Error scope

- **Failure scope**: frontend
- **Layer**: frontend + backend integration boundary (request datetime format handling)
- **Symptom**: PB FEP(old) screen shows "검색 결과가 없습니다." while equivalent authenticated API request returns rows.
- **Impact**: Product-facing PB FEP(old) search/sort closure is blocked.

## 3. Cause (estimated)

- The screen sends datetime-local style bounds (`YYYY-MM-DDTHH:mm`), which are accepted by UI controls but resolve to zero-result behavior in legacy PB FEP search path.
- Backend/API path returns expected data when bounds are sent in compatible space-separated format (`YYYY-MM-DD HH:mm:ss`), indicating an input normalization mismatch.

## 4. Action

- Investigate and fix request datetime normalization for PB FEP(old) search path:
  - Frontend request builder for PB FEP(old) should normalize datetime-local input before API submission, or
  - Backend input parser for legacy PB FEP search should accept datetime-local format consistently.
- Preserve canonical contract policy:
  - canonical product-facing time key remains `log_time`,
  - `log_timestamp` stays compatibility-only/internal.

## 5. Verification

- Initial failure evidence (QA, 2026-04-14):
  - Browser PB FEP(old) search with `TR Code=QA`, `Login ID=qa_log_time`, single-day bounds produced empty grid.
  - Direct API (`POST /api/logs/db-refactored/search`) with same filter and `YYYY-MM-DD HH:mm:ss` bounds returned `totalCount=3`.
  - Direct API with datetime-local bounds (`YYYY-MM-DDTHH:mm`) returned `totalCount=0` (symptom match).
- Next step: Requirements should delegate by scope, implement fix, restart services, then return to QA for full re-verification.

### 5.1 QA re-verification after frontend fix (2026-04-14)

- Build/restart gate:
  - `./scripts/dev-services.sh all restart` completed.
  - Health check pass: backend 9200, frontend 3001, DB connectivity OK.
- Regression tests:
  - `cd backend && mvn -Dtest=LogDbServiceTest test` → pass (27/27).
  - `cd frontend && npm test -- --watchAll=false --runTestsByPath src/components/LogGrid.test.js src/components/LogTable.test.js` → pass (12/12), including datetime-local normalization test.
- Browser verification (`cursor-ide-browser`):
  - PB FEP(old) with datetime-local input (`2026-04-14T00:00` ~ `2026-04-14T23:59`) and required filters executes request (`POST /api/logs/db-refactored/search`, HTTP 200) without browser error.
  - Grid remained empty in current local seed dataset, so the original "non-empty known-data browser case" acceptance evidence is not reproducible in this environment yet.

### 5.2 Bugfix child QA decision

- **Bugfix implementation status**: Code/test level fix confirmed (Pass).
- **Acceptance closure status**: **Fail/Blocked** (known-data non-empty browser evidence missing in current local data).
- Required follow-up:
  1. Restore/provide known QA dataset used by parent TC (`TR Code=QA`, `Login ID=qa_log_time`, single-day rows).
  2. Re-run browser TC and capture non-empty result evidence.

### 5.3 DB dataset provision evidence (2026-04-14)

- QA unblock dataset provisioned for bugfix re-check:
  - One-day range: `2026-04-14 00:00:00` ~ `2026-04-14 23:59:59`
  - Deterministic keys: `brodid(loginId)=qa_log_time`, `tr_code=QA`
  - Row counts after seed: `pb_send=3`, `pb_recv=2`
- No-DEFAULT policy and partition routing evidence:
  - `pb_send_default_partitions=0`, `pb_recv_default_partitions=0`
  - All seeded rows routed to daily children:
    - `pb_send_20260414`: 3 rows
    - `pb_recv_20260414`: 2 rows
- QA should use browser/API search values:
  - `startDate=2026-04-14T00:00`, `endDate=2026-04-14T23:59`
  - `loginId=qa_log_time`, `trCode=QA`, `logType=pb_feplog`

### 5.4 Final QA verification after reseed (2026-04-14)

- Build/restart gate:
  - `./scripts/dev-services.sh all restart` completed.
  - Health checks passed: backend 9200, frontend 3001, DB connectivity OK.
- Browser verification (`cursor-ide-browser`):
  - PB FEP(old) with datetime-local bounds (`2026-04-14T00:00` ~ `2026-04-14T23:59:59`), `TR Code=QA`, `Login ID=qa_log_time`.
  - Result: non-empty grid rendered (5 rows visible via expand controls), request `POST /api/logs/db-refactored/search` returned HTTP 200.
- API parity verification:
  - Legacy endpoint (`/api/logs/db-refactored/search`) with canonical `sortField=log_time` returned non-empty (`totalCount=5`).
  - Wire endpoint (`/api/logs/db-refactored/pb-fep-log-search`) with canonical `sortField=log_time` returned non-empty (`totalCount=5`).
  - Canonical/alias policy check (`log_time` vs `log_timestamp` sortField) returned equivalent top-row ordering/value.

### 5.5 Bugfix child final QA decision

- **Bugfix implementation status**: Pass.
- **Acceptance closure status**: **Pass** (known-data non-empty browser evidence confirmed).
- Recommendation:
  1. Close this bugfix child as resolved.
  2. Apply final pass evidence to parent requirement §5 and proceed with closure commit.

## 6. Implementation notes (Frontend)

- Updated `frontend/src/components/LogGrid.js`:
  - Added PB FEP(old) request normalization for `startDate`/`endDate`.
  - `YYYY-MM-DDTHH:mm` and `YYYY-MM-DDTHH:mm:ss` are normalized to backend-compatible `YYYY-MM-DD HH:mm:ss`.
  - Normalization is applied before both request payload creation and `searchParams` state persistence so pagination/sort re-requests keep the normalized format.
- Canonical sort key policy preserved:
  - PB FEP requests still default to `sortSpecs=[{field: "log_time", direction: "desc"}]`.
  - No production path reintroduces `log_timestamp` as canonical sort key.

## 7. Regression evidence

- Added test in `frontend/src/components/LogGrid.test.js`:
  - `pb-feplog legacy initialSearchParams datetime-local gets normalized to space format`
  - Verifies request body conversion:
    - `startDate: 2026-04-14T00:00 -> 2026-04-14 00:00:00`
    - `endDate: 2026-04-14T23:59 -> 2026-04-14 23:59:00`
  - Verifies canonical sort policy remains `log_time` and excludes `log_timestamp`.
