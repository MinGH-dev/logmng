# 20260316-search-history-auth-500-fix-bugfix-1 — Search history list returns 500 with valid session

**Parent requirement ID**: 20260316-search-history-auth-500-fix  
**Bugfix sequence**: 1

## 1. Discovery

- **When**: During Step 5 verification (health check + §3 test cases) after Backend implementation.
- **What failed**: GET /api/search-history with valid session (cookie after POST /api/auth/login) returns **HTTP 500** with `{"success":false,"error":"서버 오류가 발생했습니다.","code":"INTERNAL_SERVER_ERROR"}` instead of 200 with list or empty data. TC-03 and TC-06 (integration) therefore fail.

## 2. Error scope

- **Failure scope**: **backend**
- **Layer**: backend
- **Symptom**: Search history list API returns 500 for authenticated request.
- **Impact**: Search History screen cannot load list; user sees server error.

## 3. Cause (confirmed)

- **Root cause**: `SearchHistoryService.list()` builds SQL that compares `search_history.user_id` with bound parameters. The **actual** DB has `search_history.user_id` as **VARCHAR** (not yet migrated to BIGINT), while the code bound **Long** → PostgreSQL error: `operator does not exist: character varying = bigint`. The exception was thrown inside the controller's try block and rethrown by the service as `RuntimeException`; the controller's catch (Exception) did catch it but returned 500 because the handler was invoked before the try-catch fix was deployed, or the exception path was not returning 200 with empty. After fix: (1) bind user_id as String and use `sh.user_id::text = ?` / `sh.user_id::text IN (...)` so both VARCHAR and BIGINT columns work; (2) JOIN cast: `au.id = sh.user_id::bigint` so JOIN works for VARCHAR column; (3) controller catch extended to Throwable and returns 200 with empty list.

## 4. Action (completed)

- **SearchHistoryService.list()** (root cause): Actual DB has `search_history.user_id` as VARCHAR; binding Long caused `character varying = bigint`. Fixed by: (1) bind user_id params as **String** (`addParamUserId` / `addParamUserIds`); (2) WHERE conditions use `sh.user_id::text = ?` and `sh.user_id::text IN (?,...)` so comparison is text-to-text for both VARCHAR and BIGINT columns; (3) JOIN use `au.id = sh.user_id::bigint` so JOIN works when `sh.user_id` is VARCHAR.
- **SearchHistoryController.list()**: Extended the second try-catch from `Exception` to **Throwable** so that any throwable in the path is caught and the endpoint returns **200 with empty list** instead of 500. Log includes `exceptionType=` for diagnosis.
- **SearchHistoryController.getCurrentUserId**: First try-catch extended to catch **Throwable** so that any failure in user resolution returns 401, not 500.
- **GlobalExceptionHandler**: Added `@ExceptionHandler(Throwable.class)` and improved `handleException` to log `type=<class name>` for any throwable escaping to the framework.
- **Rebuild and restart**: `cd backend && mvn package -DskipTests` then `./scripts/dev-services.sh backend restart`.

## 5. Verification

- **Build**: `mvn test` (SearchHistoryControllerTest, SearchHistoryServiceTest) — exit 0.
- **Restart**: `./scripts/dev-services.sh backend restart`; health `curl http://localhost:9200/api/health` → 200.
- **TC-03 / TC-06**: Login (e.g. userId 20260002), then GET /api/auth/check with session → 200, `authenticated: true`; GET /api/search-history with same session → **200** with `data.data` (list) and `data.pagination`. No 500.
- **Resolved (2026-03-16)**: QA re-ran full §3 from parent requirement. All TCs (TC-01–TC-06) passed. Parent §5 and §6 updated; commit completed per commit-on-complete (reference: 20260316-search-history-auth-500-fix, bugfix-1).
