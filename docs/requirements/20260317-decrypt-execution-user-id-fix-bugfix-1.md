# 20260317-decrypt-execution-user-id-fix-bugfix-1 — Decrypt 500 caused by ActivityLogAspect serializing HttpServletRequest

**Parent requirement**: [20260317-decrypt-execution-user-id-fix](20260317-decrypt-execution-user-id-fix.md)  
**Bugfix sequence**: 1

## 1. Discovery

- **When**: During verification / user execution of decryption after approval.
- **What failed**: POST `/api/logs/decrypt/{logType}` returns 500 (or broken response) when the user executes decryption after approval, even though controller and approval logic are correct.

## 2. Error scope

- **Failure scope**: backend
- **Layer**: backend (AOP aspect)
- **Symptom**: ActivityLogAspect serializes all controller parameters (including `HttpServletRequest`) to JSON for activity logging; Jackson serializes `RequestFacade` and accesses `asyncContext`, which throws if the request is not in async mode.
- **Impact**: Decrypt API (and any @ActivityLog endpoint that has `HttpServletRequest`/`HttpServletResponse` in parameters).

## 3. Cause

- ActivityLogAspect builds `requestParams` by calling `ObjectMapper.writeValueAsString(args[i])` for every non–LogDbSearchRequest parameter. When a parameter is `HttpServletRequest` (e.g. `httpRequest` in DecryptController.decryptRow), Jackson serializes the container’s `RequestFacade`, which triggers `getAsyncContext()` and throws: *It is illegal to call this method if the current request is not in asynchronous mode*.

## 4. Action

- **ActivityLogAspect**: Skip serializing Servlet API parameters. Treat `HttpServletRequest` and `HttpServletResponse` as non-serializable; put placeholders (e.g. `"<HttpServletRequest>"`, `"<HttpServletResponse>"`) in `requestParams` instead of passing them to `ObjectMapper.writeValueAsString`. Added `isNonSerializableServletParam(Object)` and `getPlaceholderForServletParam(Object)`; applied in both the main param loop and the catch fallback.
- **ActivityLogAspectTest**: New unit test ensuring that when the join point has `HttpServletRequest` in args and `includeParams=true`, the aspect does not throw and stores `"<HttpServletRequest>"` in `requestParams` for that parameter.
- **Comment**: In ActivityLogAspect, document why request/response are not serialized (Servlet internals / asyncContext not safely serializable).

## 5. Verification

- **mvn test**: All tests pass (including ActivityLogAspectTest and DecryptControllerTest). Completed.
- Restart backend; POST `/api/logs/decrypt/{logType}` with valid approval returns 200 and activity log is saved without aspect throwing. (QA / manual verification.)
