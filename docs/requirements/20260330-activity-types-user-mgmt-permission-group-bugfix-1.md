# 20260330-activity-types-user-mgmt-permission-group-bugfix-1 — GET /api/activity-log/action-types returns 400 (conflicts with /{id})

**Parent requirement ID**: `20260330-activity-types-user-mgmt-permission-group`  
**Bugfix sequence**: 1

## 1. Discovery

- **When**: During Step 5 verification (2026-03-30)
- **What failed**:
  - After `POST /api/auth/login` (session cookie), `GET http://localhost:9200/api/activity-log/action-types` returns **HTTP 400** with body `{"success":false,"error":"요청 파라미터가 올바르지 않습니다. (id)","code":"BAD_REQUEST"}`.
  - Unauthenticated `GET` correctly returns **401** `UNAUTHORIZED` (endpoint is protected).

## 2. Error scope

- **Failure scope**: **backend**
- **Layer**: backend
- **Symptom**: Static path `action-types` is handled as path variable `{id}` for activity-log detail; `Long` conversion fails for segment `action-types`.
- **Impact**: `GET /api/activity-log/action-types` (TC-15) is unusable at runtime; frontend falls back to `FALLBACK_ACTIVITY_ACTION_TYPE_OPTIONS` (missing permission-group and other canonical codes), failing TC-11 / §3.5 expectations for API-driven filter options.

## 3. Cause (confirmed)

- Spring MVC registers both `@GetMapping("/action-types")` and `@GetMapping("/{id}")` under `/api/activity-log`. At runtime the **variable** mapping wins for the segment `action-types`, so the detail handler runs and fails id parsing.
- `UserActivityLogControllerTest` uses **standalone** `MockMvc` for the controller only; full-application handler ordering/pattern resolution differs, so the test `getActionTypes_returnsSortedCodeAndLabel` can pass while the running app fails.

## 4. Action (for Backend implementer)

- **Preferred**: Restrict detail mapping to numeric ids, e.g. `@GetMapping("/{id:\\d+}")` on `getActivityLogDetail`, so `/action-types` is not captured as `{id}`.
- **Alternative**: Move `getActionTypes` to a non-conflicting path (contract change) or register a dedicated `@Controller` with higher-specificity ordering; confirm with Contract if path changes.
- **Tests**: Add (or extend) an **integration test** with `@SpringBootTest` + `MockMvc` or `TestRestTemplate` hitting `GET /api/activity-log/action-types` with auth so full mapping is verified.
- **Files**: `backend/src/main/java/com/logmng/controller/UserActivityLogController.java` (primary); tests under `backend/src/test/java/...`.

## 5. Verification

- **Done (2026-03-30, QA re-verification)**:
  - Backend: `UserActivityLogController` uses `@GetMapping("/{id:\\d+}")` for detail; **`mvn package -DskipTests`** + `./scripts/dev-services.sh backend restart` so the running JAR includes the fix.
  - `GET /api/activity-log/action-types` without cookie → **401**; with session cookie after `POST /api/auth/login` → **200**, `success: true`, `{code,label}` list includes `PERMISSION_GROUP_*` and related codes from `ActivityActionType`.
  - Browser (Cursor IDE Browser MCP): **활동 이력** → **액션 타입** — after async load (~few seconds), options show full server list (e.g. 23 entries including 권한 그룹 관련 labels), not fallback-only.
  - Parent requirement **§5** updated; this bugfix **closed**.
