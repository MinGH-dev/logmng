# 20260316 - Fix 500 on Search History screen (auth/check and search-history list APIs)

## 1. User requirement

### Requirement description

On the Search History (Decryption Approval) screen, two APIs return HTTP 500 with the message "서버 오류가 발생했습니다.": **GET /api/auth/check** ("인증 상태 확인 실패") and **GET /api/search-history** ("검색 이력 목록 조회 실패"). This requirement defines the expected behaviour, root cause (uncaught exceptions in controller code paths), and the solution so that valid sessions and requests return 200 (or 401 when unauthenticated) instead of 500.

### User scenario

1. User is logged in (or the app checks auth on load) and navigates to or loads the Search History screen.
2. Frontend calls **GET /api/auth/check** to verify authentication and obtain user info (e.g. `selfContext`, `screenScopes`, `screenFunctions`).
3. **Problem**: The response is 500 with "서버 오류가 발생했습니다." / "인증 상태 확인 실패" instead of 200 with `authenticated: true` and user data (or 200 with `authenticated: false` when not logged in).
4. Frontend calls **GET /api/search-history** with query params (e.g. `page`, `pageSize`, `sortDirection=desc`, requester filters).
5. **Problem**: The response is 500 with "서버 오류가 발생했습니다." / "검색 이력 목록 조회 실패" instead of 200 with list data (or empty list) or 401 when the user cannot be resolved.

### Expected outcome

- **GET /api/auth/check**: Returns **200** when the user has a valid session (with `authenticated: true` and user payload), or 200 with `authenticated: false` when not logged in. No 500 from response serialization or auth logic.
- **GET /api/search-history**: Returns **200** with data or empty list when the user is authenticated and params are valid. If the current user cannot be resolved (e.g. session inconsistent), returns **401** instead of 500. No 500 from `getCurrentUserId()`, `resolveScope`, `getScreenScopes`, or `DepartmentScopeHelper` in the controller path.

---

## 2. Design

### 2.1 Security review (optional)

- [ ] Security review performed
- Not required for this minimal error-handling fix unless the root cause involves new auth/scope behaviour. Returning 401 instead of 500 for unresolved user preserves correct semantics.

### Technical design

#### Codebase summary

- **SearchHistoryController** (`backend/.../SearchHistoryController.java`): `list()` builds `currentUserId` via `getCurrentUserId(httpRequest)`, then parses query params, resolves scope via `ScopeHelper.resolveScope(...)` and `getScreenScopes(httpRequest)`, and for `scope=team` calls `DepartmentScopeHelper.getNumericUserIdsInSameDepartment(dataSource, currentUserId)`. Only `searchHistoryService.list(listRequest)` is inside a try-catch; the rest of the path runs outside and any uncaught exception propagates to `GlobalExceptionHandler` → 500.
- **getCurrentUserId** (controller): Calls `authService.getCurrentUserInfo(request)` then, when user has username but null userId, calls `appUserResolver.getIdByUsername(user.getUsername())`. `AuthService.getCurrentUserInfo` is defensive and returns null on exception; `AppUserResolver.getIdByUsername` catches only `SQLException` and returns null — any other throw (e.g. NPE, unchecked) propagates from the controller.
- **AuthController** (`backend/.../AuthController.java`): `check()` calls `authService.checkAuth(httpRequest)` then, when authenticated, `authService.getCurrentUserInfo(httpRequest)` and builds a `Map` with `username`, `isSystemAdmin`, `allowedScreenIds`, `screenScopes`, `screenFunctions`, `selfContext`. Response is serialized by Jackson. Any exception during map building or serialization (e.g. getter throwing, non-serializable type) would not be caught and would result in 500.
- **AuthService**: `getCurrentUserInfo()` wraps internal logic in try-catch and returns null on any exception. `checkAuth()` is defensive and returns false on exception. So 500 from auth/check is likely from building the response map or from Jackson serialization when `userInfo` contains problematic values.
- **GlobalExceptionHandler**: Catches `Exception` and returns 500 with "서버 오류가 발생했습니다.".

#### Problem analysis

1. **Search history list 500**: In `SearchHistoryController.list()`, the try-catch only wraps `searchHistoryService.list(listRequest)`. The following are **outside** the try block and can throw:
   - (a) **getCurrentUserId(httpRequest)** — When session has username but no userId, it calls `appUserResolver.getIdByUsername(user.getUsername())`. `AppUserResolver.getIdByUsername` catches only `SQLException`; any other exception (e.g. NPE, RuntimeException from connection or environment) propagates → 500.
   - (b) **ScopeHelper.resolveScope(...)**, **getScreenScopes(httpRequest)** — Unlikely to throw but not guarded; e.g. `getScreenScopes` casts session attribute to `Map` and could throw `ClassCastException` if the attribute was stored with wrong type.
   - (c) **DepartmentScopeHelper.getNumericUserIdsInSameDepartment(...)** when scope=team — Implementation catches `SQLException` and returns a fallback list; any other throw would propagate → 500.
   Any of these throws causes `GlobalExceptionHandler` to return 500 with "검색 이력 목록 조회 실패" (if the exception is logged with that message) or generic "서버 오류가 발생했습니다.".

2. **Auth check 500**: `AuthController.check()` calls `checkAuth()` then `getCurrentUserInfo()`. `getCurrentUserInfo` already has try-catch and returns null. So 500 may come from: (a) building the response `Map` (e.g. null dereference when copying from `userInfo`), (b) Jackson serialization of the response (e.g. `selfContext`, `screenFunctions` or nested types causing serialization failure), or (c) an edge case in `checkAuth` or elsewhere that is not yet defensive.

3. **Expected behaviour**: When the current user cannot be resolved (e.g. session inconsistent or resolver throws), the list endpoint should return **401** (or 200 with empty list only when the failure is after user resolution, per product choice). Auth/check must never return 500 for valid or unauthenticated requests; response DTOs must be serializable and building the map must not throw.

#### Solution approach

Structure by scope so the implementing agent receives only its relevant section during handoff.

**Backend**

- **SearchHistoryController.list()**: Ensure no uncaught exception in the path from entry to response.
  - **Option A**: Wrap **getCurrentUserId(httpRequest)** in try-catch in the controller (or in a helper used by list). If an exception occurs, treat as "user not resolved" and return **401** with a clear message (e.g. "로그인이 필요합니다." / "UNAUTHORIZED") instead of letting the exception propagate to GlobalExceptionHandler.
  - **Option B**: Extend the existing try block in `list()` to include **getCurrentUserId**, **resolveScope**, **getScreenScopes**, and **DepartmentScopeHelper** so that any exception in that path is caught; then either return **401** (when the exception is from user resolution) or **200 with empty list** (when the exception is from scope/helper), and log the error. Prefer 401 when the failure is clearly "current user cannot be determined".
  - Ensure **getCurrentUserId** is never invoked outside a context that handles exceptions (either controller try-catch or a safe wrapper that returns null on any throw). If the controller keeps calling `getCurrentUserId` before the try, it **must** be wrapped so that any throw from it (e.g. from `appUserResolver.getIdByUsername`) results in 401, not 500.
- **AuthController.check()**: Ensure response building and serialization cannot throw. Add defensive null checks when putting `userInfo` fields into the response map. Ensure `LoginResponse` and nested types (`SelfContext`, `ScreenFunctionCapability`) are fully serializable; if any getter or type can cause Jackson to throw, fix or exclude that field when null/problematic. Optionally wrap the entire response building and return in try-catch and on exception return 200 with `authenticated: false` and a minimal payload (or 500 only for truly unexpected server state, per product policy).
- **AppUserResolver.getIdByUsername** (optional hardening): Document that callers must not rely on it never throwing (e.g. it only catches `SQLException`). Alternatively, make it catch `Exception` and return null so that any unexpected throw does not propagate to controllers. This is an optional defensive measure; the primary fix is in the controller handling.
- **Tests**: Add or adjust unit/integration tests so that: (1) GET /api/auth/check with valid session returns 200 and with no/invalid session returns 200 and `authenticated: false`; (2) GET /api/search-history when `getCurrentUserId` would throw (e.g. mock throwing) returns 401 or 200 with empty list, not 500; (3) edge session state (e.g. username present, userId missing) does not result in 500.

**Frontend**

- No change required for this requirement; the fix is backend-only. Frontend already displays server error when it receives 5xx; once backend returns 200 or 401, the screen will behave correctly.

**DB**

- No schema or migration change.

### Affected scopes and change targets (verification)

**Before finalizing §2**, the Requirements author has verified that every affected scope is covered and that no touchpoint is missed per `docs/workflow/REQUIREMENTS-CHANGE-TARGET-CHECKLIST.md`.

| Scope | Affected? | §2 subsection and change file list complete? |
|-------|-----------|-----------------------------------------------|
| Backend | [x] Yes | [x] |
| Frontend (config UI + view screen) | [ ] No | — |
| DB | [ ] No | — |
| Contract / Spec | [ ] No | — |
| Cursor tools (skills, specs) | [ ] No | — |

This requirement is an **API/error-handling** fix (pattern §3.3). Touchpoints: Backend controller/service and exception handling; contract/spec only if error response shape or status codes are documented (optional update to contract.md if 401 is explicitly specified for "user not resolved" for list endpoint).

### Planned change file list (expected change targets)

**(Planned at authoring. Implementing agent (Step 4) confirms or amends this list when implementation is complete.)**

#### Backend

- `backend/src/main/java/com/logmng/controller/SearchHistoryController.java`
  - Ensure **getCurrentUserId** is not the source of uncaught exceptions: either wrap its use in `list()` (and any other method that uses it) in try-catch and return 401 when resolution fails with an exception, or extend the existing try block in `list()` to include getCurrentUserId, resolveScope, getScreenScopes, and DepartmentScopeHelper, and map exceptions to 401 or 200 with empty list (and log). Must verify that no code path from `list()` entry to response can throw to GlobalExceptionHandler for these cases.
- `backend/src/main/java/com/logmng/controller/AuthController.java`
  - Ensure **check()** response building and serialization cannot throw: add defensive null checks when populating the response map from `userInfo`; ensure nested objects (e.g. `selfContext`, `screenFunctions`) are only added when non-null or are serializable. Optionally wrap response build in try-catch and return 200 with `authenticated: false` on any exception if product accepts that.
- `backend/src/main/java/com/logmng/service/AppUserResolver.java` (optional)
  - Consider catching `Exception` in **getIdByUsername** (and **getUsernameById** if desired) and returning null so that callers never see an unexpected throw. Document behaviour. Implementing agent may skip if controller-level handling is sufficient.
- `backend/src/main/java/com/logmng/exception/GlobalExceptionHandler.java`
  - No mandatory change; existing handler already returns 500 for uncaught exceptions. If any new exception type should return 4xx, add a dedicated handler.
- Unit/integration tests (e.g. `SearchHistoryControllerTest`, `AuthController` tests or webtest)
  - Add or adjust tests: auth/check returns 200 with valid session and with no session; search-history list returns 401 or 200 (empty) when current user resolution throws or fails, and returns 200 with data when resolution succeeds and service returns data.

**Actual change list (Step 4):** `SearchHistoryController.java`, `AuthController.java`, `AppUserResolver.java`, `SearchHistoryControllerTest.java`, `AuthControllerTest.java`. No change to `GlobalExceptionHandler.java`.

#### Frontend

- None.

#### DB

- None.

---

## 3. Test approach

### Test case list (required)

| ID | Scope | Type | Scenario (input / condition) | Expected result | Verification (unit / integration / manual) |
|----|-------|------|------------------------------|-----------------|--------------------------------------------|
| TC-01 | Backend | Normal | GET /api/auth/check with valid session (userId and username in session) | 200, body.authenticated === true, body has username, allowedScreenIds, screenScopes, screenFunctions, selfContext | Unit or integration (mvn test / curl) |
| TC-02 | Backend | Normal | GET /api/auth/check with no session or invalid session | 200, body.authenticated === false | Unit or integration |
| TC-03 | Backend | Normal | GET /api/search-history with valid session and valid query params (page, pageSize, sortDirection, etc.) | 200, body.data with list (or empty array) and pagination | Unit or integration |
| TC-04 | Backend | Edge | GET /api/search-history when current user resolution would throw (e.g. mock AppUserResolver.getIdByUsername throwing RuntimeException, or session with username but no userId and resolver throws) | 401 with message indicating login required (or 200 with empty list per implementation choice); **not** 500 | Unit (mock) or integration |
| TC-05 | Backend | Edge | GET /api/search-history when getCurrentUserInfo returns null (no/invalid session) | 401 | Unit or integration |
| TC-06 | Integration | Regression | Open Search History screen with valid login; call auth/check then search-history list | Both return 200 (auth with authenticated: true; list with data or empty); no 500 | Integration / manual |

### Test scenarios

#### Scenario 1: Valid session – auth check and list

1. Log in; obtain session cookie.
2. Call GET /api/auth/check with session. Expect 200 and `authenticated: true`.
3. Call GET /api/search-history with session and valid params. Expect 200 with list or empty list.

#### Scenario 2: Unresolved user – no 500

1. Simulate or mock a state where `getCurrentUserId` would throw (e.g. session has username but no userId and `appUserResolver.getIdByUsername` throws).
2. Call GET /api/search-history. Expect 401 (or 200 with empty list); must not be 500.

#### Scenario 3: No session – auth check

1. Call GET /api/auth/check without session. Expect 200 with `authenticated: false`.

### Test data

- Use existing app_user and session setup. For TC-04, test may use a mock that throws when `getIdByUsername` is called, or a DB state that triggers the re-entry path (username in session, userId missing) and a resolver that throws.

### Test environment

- Backend: `http://localhost:9200`
- Frontend: per contract (e.g. `http://localhost:3001`)
- Database: PostgreSQL per project setup

### 3.5 Browser automation verification (optional)

- Applicable TCs: TC-06 (full screen flow).
- Procedure: Navigate to Search History screen after login; confirm no "서버 오류" message; confirm list or empty state loads.

---

## 4. Checklist

### Backend verification

- [ ] API test cases written and run (TC-01–TC-05)
- [ ] Logs checked (no unexpected stack traces for auth/check or list when session is valid or clearly unauthenticated)
- [ ] Edge session state and resolver throw lead to 401 or empty list, not 500

### Integration

- [ ] End-to-end flow tested (open Search History screen → auth/check and list return 200 or 401 as expected)
- [ ] Edge cases tested (TC-04, TC-05)

### Documentation

- [ ] Requirement doc completed
- [ ] Code comments added where behaviour (e.g. 401 vs empty list) is non-obvious

---

## 5. Test results

### Test run date

- 2026-03-16 (Step 5 verification)
- **Re-verification**: 2026-03-16 (after bugfix-1; Backend fix deployed)

### Test results

#### Build and restart

- **Build**: Handoff: `mvn test` exit 0 (AuthControllerTest, SearchHistoryControllerTest). Re-run: `mvn test -Dtest=AuthControllerTest,SearchHistoryControllerTest` — exit 0.
- **Restart**: Handoff stated backend restart done. Health: `curl -s http://localhost:9200/api/health` → 200, JSON `status: OK`.
- **Re-verification**: Health `curl -s http://localhost:9200/api/health` → 200. Unit tests: `mvn test -Dtest=AuthControllerTest,SearchHistoryControllerTest` → BUILD SUCCESS (11 tests, 0 failures).

#### §3 test cases (initial + re-verification)

| ID   | Result | Note |
|------|--------|------|
| TC-01 | **Pass** | GET /api/auth/check with valid session (after login) → 200, `data.authenticated === true`, body has username, allowedScreenIds, screenScopes, screenFunctions, selfContext. Re-verified with session cookie. |
| TC-02 | **Pass** | GET /api/auth/check without session → 200, `data.authenticated === false`. |
| TC-03 | **Pass** | GET /api/search-history with valid session → **200** with `data.data` (list) and `data.pagination`. (Was Fail before bugfix-1; fixed.) |
| TC-04 | **Pass** | Unit test: list when service throws → 200 with empty data (SearchHistoryControllerTest). User resolution throw → 401. |
| TC-05 | **Pass** | GET /api/search-history without session → 401. |
| TC-06 | **Pass** | Integration: auth/check 200 + search-history list 200 with valid session; no 500. |

**Re-verification commands:**

```bash
# Health
curl -s http://localhost:9200/api/health

# TC-02, TC-05 (no session)
curl -s http://localhost:9200/api/auth/check
curl -s -o /dev/null -w "%{http_code}" "http://localhost:9200/api/search-history?page=1&pageSize=20&sortDirection=desc"

# Login then TC-01, TC-03, TC-06
curl -s -c /tmp/cookies_search_history.txt -b /tmp/cookies_search_history.txt -X POST http://localhost:9200/api/auth/login -H "Content-Type: application/json" -d '{"userId": 20260001, "password": "user123"}'
curl -s -b /tmp/cookies_search_history.txt http://localhost:9200/api/auth/check
curl -s -b /tmp/cookies_search_history.txt "http://localhost:9200/api/search-history?page=1&pageSize=20&sortDirection=desc"
```

### Issues found and resolution

- **TC-03 / TC-06** (initial): With valid session, GET /api/search-history returned 500. **Bugfix child**: `docs/requirements/20260316-search-history-auth-500-fix-bugfix-1.md`. Backend fixed (user_id VARCHAR vs BIGINT binding; controller try-catch). **Re-verification**: All TCs pass; bugfix-1 resolved.

---

## 6. Error remedy result (cause and action) — for error/bug fix requirements only

- **Requirement ID**: 20260316-search-history-auth-500-fix
- **Root cause**: (1) Uncaught exceptions in SearchHistoryController.list() path (getCurrentUserId, resolveScope, getScreenScopes, DepartmentScopeHelper) and defensive gaps in AuthController.check() response build/serialization. (2) **Bugfix-1**: DB column `search_history.user_id` is VARCHAR; code bound Long → PostgreSQL "operator does not exist: character varying = bigint"; exception inside list() caused 500.
- **Actions taken**: Backend implemented try-catch in list() and check(); AppUserResolver catches Exception; unit tests added. Bugfix-1: SearchHistoryService binds user_id as String with `::text`/`::bigint`; controller catch extended to Throwable, returns 200 with empty list on throw.
- **Result**: Re-verification 2026-03-16: all §3 TCs pass (TC-01–TC-06). Auth/check and search-history list return 200 with valid session; no 500.
- **Completed**: Yes (2026-03-16; bugfix-1 resolved)

---

**Author**: Requirements subagent  
**Date**: 2026-03-16  
**Status**: Completed. Re-verification passed after bugfix-1; §5/§6 updated; committed per commit-on-complete.
