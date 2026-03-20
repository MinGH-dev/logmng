# 20260317-decrypt-execution-user-id-fix-bugfix-1 — Decrypt 500 caused by ActivityLogAspect serializing HttpServletRequest

**Parent requirement**: [20260317-decrypt-execution-user-id-fix](20260317-decrypt-execution-user-id-fix.md)  
**Bugfix sequence**: 1

## 1. Discovery

- **When**: During verification / user execution of decryption after approval.
- **What failed**: POST `/api/logs/decrypt/{logType}` returns 500 (or broken response) when the user executes decryption after approval, even though controller and approval logic are correct.

## 2. Error scope

- **Failure scope**: backend
- **Layer**: backend (AOP aspect)
- **Symptom**: ActivityLogAspect serializes controller parameters to JSON for activity logging. Jackson can hit:
  - **RequestFacade / headerNames**: `No serializer found for class org.apache.tomcat.util.http.NamesEnumerator` (through reference chain: `RequestFacade["headerNames"]`) when any structure passed to ObjectMapper contains `HttpServletRequest` (e.g. direct param or a Map containing the request).
  - Previously: `getAsyncContext()` → *IllegalStateException*; `getParts()` → *InvalidContentTypeException* for application/json.
- **Impact**: Decrypt API (and any @ActivityLog endpoint with `HttpServletRequest`/`HttpServletResponse` in parameters or inside a Map/body).
- **Optional / user hypothesis**: If `search_history.user_id` is still VARCHAR (migration not applied), `isValidApprovalForUser(searchHistoryId, userId)` binds Long; no row matches → "복호화 거부(승인 미충족)". See §4 migration doc.

## 3. Cause

- ActivityLogAspect passed values to `ObjectMapper.writeValueAsString()` that could be or contain Servlet API types: (1) direct `HttpServletRequest` parameter (e.g. `httpRequest` in DecryptController.decryptRow); (2) a Map parameter whose value is the request (e.g. request body with `httpRequest` key). Jackson introspects RequestFacade and accesses `getHeaderNames()` → NamesEnumerator has no serializer.

## 4. Action

- **ActivityLogAspect**: Never pass `HttpServletRequest`/`HttpServletResponse` (or any `ServletRequest`/`ServletResponse`) to ObjectMapper on any code path.
  - **Interface check**: `isNonSerializableServletParam(arg)` uses `HttpServletRequest`, `HttpServletResponse`, `ServletRequest`, `ServletResponse`; placeholders (e.g. `"<HttpServletRequest>"`) are stored and **never** passed to `writeValueAsString`.
  - **Early replacement**: When iterating method arguments, if Servlet type → put placeholder and `continue`.
  - **Deep sanitize**: Before serializing any non–LogDbSearchRequest argument (or in catch fallback), pass value through `deepSanitizeForSerialization()` so nested Map/Collection values that are Servlet are replaced with placeholders (avoids NamesEnumerator when a Map contains the request).
  - **Catch path**: In parameter-processing catch, check Servlet type first; in fallback never call `objectMapper.writeValueAsString(args[i])` without sanitizing (use `deepSanitizeForSerialization(args[i])` then serialize).
  - **requestParams map**: `sanitizeParamsForSerialization()` now uses `deepSanitizeForSerialization()` so nested structures are sanitized before `ObjectMapper.writeValueAsString(paramsToSerialize)`.
- **ActivityLogAspectTest**: (1) `logActivity_doesNotSerializeHttpServletRequest_putsPlaceholderInRequestParams`; (2) `logActivity_withRequestThatThrowsOnGetParts_doesNotThrow_applicationJson`; (3) `logActivity_withMapContainingHttpServletRequest_doesNotThrow_placeholderInParams` (Map param containing request → no exception, placeholder in saved params).
- **Migration (user_id vs user_name)**: In **backend/DB_SETUP_GUIDE.md**, the section "search_history.user_id 규칙 및 마이그레이션" already states that decrypt execution requires `search_history.user_id` to be BIGINT; added **증상** note: if column is still VARCHAR with username values, run `migrate-search-history-user-id-to-bigint.sql`. Optional startup check: **SearchHistoryUserIdMigrationCheck** (ApplicationRunner) queries `information_schema.columns` for `search_history.user_id`; if `data_type` is not `bigint`, logs WARN with migration command and DB_SETUP_GUIDE reference.

## 5. Verification

- **mvn test**: All tests pass (including ActivityLogAspectTest and DecryptControllerTest). ActivityLogAspectTest includes:
  - `logActivity_doesNotSerializeHttpServletRequest_putsPlaceholderInRequestParams`
  - `logActivity_withRequestThatThrowsOnGetParts_doesNotThrow_applicationJson`
  - `logActivity_withMapContainingHttpServletRequest_doesNotThrow_placeholderInParams`
- Restart backend; POST `/api/logs/decrypt/{logType}` with `Content-Type: application/json` and valid approval returns 200 and activity log is saved without aspect throwing. (QA / manual verification.)
- If decrypt still fails with "승인 미충족", ensure `migrate-search-history-user-id-to-bigint.sql` has been applied (see DB_SETUP_GUIDE).
