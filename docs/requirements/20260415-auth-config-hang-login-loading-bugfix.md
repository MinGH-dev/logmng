# 20260415-auth-config-hang-login-loading-bugfix

## 1. User requirement

### Requirement description
The login page can remain stuck at `로그인 설정을 불러오는 중…` when `GET /api/auth/config` hangs and does not return. At the same time, `GET /api/health` and `GET /api/auth/check` return 200, so the backend is partially healthy and the issue is isolated to the auth-config path and its client handling.

### User scenario
1. A user opens the web login page.
2. The frontend calls `GET /api/auth/config` to decide login mode (local vs AD).
3. The request does not complete (hangs / no response body and no timeout handling), so `modeLoading` never ends.
4. **Problem**: The page stays on `로그인 설정을 불러오는 중…` and the login form never appears.

### Expected outcome
- The login form must become usable even if `GET /api/auth/config` is delayed or hangs.
- The system must confirm the actual root cause with diagnostics before changing logic.
- Normal path behavior must be preserved: when `/api/auth/config` responds correctly, the returned login mode must still drive the UI.

## 2. Design

### Technical design

#### Problem analysis
1. Frontend `fetchAuthLoginMode()` currently has fallback logic for error responses and exceptions, but no explicit timeout for a hanging request, so unresolved Promises can keep login mode loading indefinitely.
2. Backend currently exposes `/api/auth/check` and `/api/auth/login` but the `/api/auth/config` route behavior and execution path diagnostics are insufficient for hang triage.
3. Interceptor and route chain behavior for `/api/auth/config` must be verified with evidence, because `/api/auth/*` exclusions can bypass auth checks but still leave controller/service-level hang points.

#### Diagnostic phase (mandatory for error/bug fix only)
- **Phase 0 (diagnostic) must run before fix logic:**
  1. Add diagnostic logs at DEBUG level around the `/api/auth/config` path (controller entry/exit, service/property read, elapsed time, interceptor pass/skip decisions).
  2. Reproduce the issue where login UI is stuck and capture frontend + backend logs with timestamps.
  3. Verify whether the request reaches controller, where it blocks, and whether any interceptor/serialization/IO branch is involved.
  4. Confirm root cause from logs, then implement targeted fixes.
- **Production safety:** Diagnostic logs must be DEBUG-only or behind a dev-only toggle, and must be removed or downgraded after fix verification so verbose diagnostics do not run in production.

#### Solution approach

**Frontend:**
- Add timeout/abort handling for `GET /api/auth/config` so hanging responses cannot block login UI forever.
- When timeout or network hang occurs, fail closed to a safe fallback mode (`REACT_APP_AUTH_LOGIN_MODE` or default `local`) and render the login form.
- Keep current success-path behavior unchanged when `/api/auth/config` responds with valid mode.

**Backend:**
- Verify and stabilize `/api/auth/config` route behavior so it always returns a bounded, non-blocking response under normal conditions.
- Add focused diagnostic points in auth config route/interceptor chain to identify blocking location and branch outcome.
- After root cause confirmation, apply minimal fix in the confirmed layer (controller/service/config/interceptor), not speculative broad changes.

**DB:**
- No schema migration is planned for this bugfix unless diagnostics prove a DB dependency in auth config path.

### Affected scopes and change targets (verification)

| Scope | Affected? | §2 subsection and change file list complete? |
|-------|------------|-----------------------------------------------|
| Backend | [x] Yes / [ ] No | [x] |
| Frontend (config UI + view screen) | [x] Yes / [ ] No | [x] |
| DB | [ ] Yes / [x] No | [x] |
| Contract / Spec | [ ] Yes / [x] No | [x] |
| Cursor tools (skills, specs) | [ ] Yes / [x] No | [x] |

### Planned change file list (expected change targets)

**(Planned at authoring. Implementing agent (Step 4) confirms or amends this list when implementation is complete.)**

#### Frontend
- `frontend/src/services/authConfigService.js`
  - Must add bounded timeout/abort behavior for `/auth/config` fetch and deterministic fallback on timeout/hang.
- `frontend/src/components/LoginForm.js`
  - Must ensure mode-loading state exits when auth-config fetch times out/falls back and must preserve normal mode-based form rendering.
- `frontend/src/components/LoginForm.test.js`
  - Must add/extend tests for timeout fallback and normal auth-config success behavior.

#### Backend
- `backend/src/main/java/com/logmng/controller/AuthController.java`
  - Must add/verify `GET /api/auth/config` bounded response path and debug diagnostics around entry/exit and mode resolution.
- `backend/src/main/java/com/logmng/config/AuthInterceptor.java`
  - Must verify and (if needed) adjust debug diagnostics for `/api/auth/config` pass-through behavior.
- `backend/src/main/java/com/logmng/config/ScreenAccessInterceptor.java`
  - Must verify and (if needed) adjust diagnostics so `/api/auth/config` exclusion behavior is explicit during reproduction.
- `backend/src/test/java/com/logmng/controller/AuthControllerTest.java`
  - Must add/extend tests for `/api/auth/config` normal response and non-hanging behavior expectations.

#### DB
- No planned DB file changes at authoring stage.

## 3. Test approach

### Test case list (required)

| ID | Scope | Type | Scenario (input / condition) | Expected result | Verification (unit / integration / manual) |
|----|-------|------|------------------------------|-----------------|--------------------------------------------|
| TC-01 | Backend | Normal | `GET /api/auth/config` with valid runtime config | 200 response with expected mode payload and bounded latency | Unit (mvn test) |
| TC-02 | Backend | Edge | Diagnostic mode enabled, reproduce auth-config request | Logs show route entry, interceptor decisions, and route exit with elapsed time; root cause evidence captured | Integration (backend logs + curl) |
| TC-03 | Frontend | Exception | `/api/auth/config` hangs (mock unresolved Promise / timeout) | UI exits loading state and shows login form via fallback mode | Unit (npm test) |
| TC-04 | Frontend | Normal | `/api/auth/config` returns `ad` | Login form renders AD principal field and does not use fallback | Unit (npm test) |
| TC-05 | Integration | Regression | `/api/health` and `/api/auth/check` are 200 while `/api/auth/config` timeout is simulated | Login UI still becomes usable; no indefinite "loading config" screen | Integration (browser + API) |
| TC-06 | Integration | Normal | All auth endpoints normal (`/api/auth/config`, `/api/auth/check`, `/api/auth/login`) | Existing login flow works unchanged in both local/ad mode paths | Integration (curl / browser) |

### Test scenarios

#### Scenario 1: Hang reproduction and fallback
1. Configure/mimic `/api/auth/config` hanging behavior.
2. Open login page and observe loading state transition.
3. Verify fallback rendering and confirm diagnostics in backend/frontend logs.

#### Scenario 2: Normal mode behavior regression check
1. Return valid `/api/auth/config` response (`local` and `ad`).
2. Open login page in each mode.
3. Verify mode-specific login form controls and successful login path remain intact.

### Test data
- Runtime mode values: `local`, `ad`.
- Timeout condition: unresolved or delayed `/api/auth/config` response exceeding client timeout threshold.

### Test environment
- Frontend: `http://localhost:3001`
- Backend: `http://localhost:9200`
- Database: PostgreSQL (no schema change expected)

## 4. Checklist

### Frontend verification
- [ ] API parameters validated
- [ ] UI behavior confirmed
- [ ] Error handling verified

### Backend verification
- [ ] API test cases written and run
- [ ] Logs checked
- [ ] Performance checked (if applicable)

### Integration
- [ ] End-to-end flow tested
- [ ] Edge cases tested

### Documentation
- [ ] Requirement doc completed
- [ ] Code comments added (if applicable)

## 5. Test results

### Test run date
- 2026-04-15

### Test results

#### Frontend
- Command: `cd /Volumes/T7/dev/logmng_frontend/dev/frontend && npm test -- --watchAll=false`
- Result: **FAIL**
- Summary:
  - Jest summary: `Test Suites: 1 failed, 39 passed, 40 total`
  - Jest summary: `Tests: 1 failed, 292 passed, 293 total`
  - Failing test location: `src/components/UserManagement/UserManagement.test.js`
  - Note: Failure is in UserManagement test scope, not in login auth-config path.

#### Backend
- Command: `cd /Volumes/T7/dev/logmng_frontend/dev/backend && mvn test`
- Result: **PASS**
- Summary:
  - Maven summary: `Tests run: 510, Failures: 0, Errors: 0, Skipped: 0`
  - Build result: `BUILD SUCCESS`

#### Runtime verification (curl)
- Service readiness:
  - Restart executed: `./scripts/dev-services.sh backend restart && ./scripts/dev-services.sh frontend restart`
  - Backend `9200`: running
  - Frontend `3001`: running
- Commands:
  - `curl http://127.0.0.1:9200/api/health`
  - `curl http://127.0.0.1:9200/api/auth/check`
  - `curl --connect-timeout 5 --max-time 8 http://127.0.0.1:9200/api/auth/config`
- Responses:
  - `/api/health`: HTTP 200, body includes `status: OK`
  - `/api/auth/check`: HTTP 200, body includes `authenticated: false`
  - `/api/auth/config`: HTTP 200, response time `0.005216s`, body includes login mode keys:
    - `authLoginMode`
    - `loginMode`

### Pass/fail matrix

| ID | Check | Command / Method | Expected | Actual | Result |
|----|-------|------------------|----------|--------|--------|
| R-01 | Backend unit tests | `mvn test` | All tests pass | 510 run, 0 fail, BUILD SUCCESS | PASS |
| R-02 | Frontend unit tests | `npm test -- --watchAll=false` | All tests pass | 1 failed, 292 passed | FAIL |
| R-03 | Backend health API | `curl /api/health` | HTTP 200 | HTTP 200 | PASS |
| R-04 | Auth check API | `curl /api/auth/check` | HTTP 200 | HTTP 200 | PASS |
| R-05 | Auth config API response | `curl /api/auth/config` | HTTP 200 + login mode keys | HTTP 200 + `authLoginMode`, `loginMode` | PASS |
| R-06 | Regression: no hang on `/api/auth/config` | curl with timeout (`--connect-timeout 5 --max-time 8`) | No hang / bounded response | Returned in `0.005216s` | PASS |

### Issues found and resolution
- Frontend test suite has one unrelated failure in `UserManagement` test scope. This does not block confirmation that `/api/auth/config` no longer hangs, but overall frontend test gate is currently red.

### Next steps
1. Fix failing `UserManagement` frontend test and re-run `npm test -- --watchAll=false`.
2. If frontend test suite becomes fully green, update §5 and proceed with final closure updates (§6 and final version section).

## 6. Error remedy result (cause and action) — for error/bug fix requirements only

- **Requirement ID**: 20260415-auth-config-hang-login-loading-bugfix
- **Root cause**: To be confirmed from diagnostic logs.
- **Actions taken**: To be filled after implementation.
- **Result**: To be filled after verification.
- **Completed**: -

---

## 7. Final version (Korean) — add after all verification is complete

Will be added after QA-complete verification.

---

**Author**: Requirements subagent
**Date**: 2026-04-15
**Status**: In progress
