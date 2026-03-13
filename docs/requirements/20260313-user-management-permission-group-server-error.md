# 20260313 - User management and permission-group management server error

## 1. User requirement

### Requirement description

Investigate and fix the issue where both the **User Management** screen and the **Permission Group Management** screen display a generic server error message ("서버에서 오류가 발생했습니다") instead of loading their data normally.

This is an **error-fix requirement** for a shared management flow. The request is not limited to one screen because the current codebase shows that both screens depend on overlapping frontend services and backend management APIs. The requirement must therefore cover the likely shared failure path across:

- frontend screen components and shared API/error utilities,
- backend screen access checks and management controllers,
- auth/session response fields used for route and action gating,
- DB schema or migration drift for permission-group-related tables.

### User scenario

1. An admin or permission-granted management user signs in and opens **User Management**.
2. The screen attempts to load department hierarchy, users, and permission groups.
3. The same user opens **Permission Group Management**.
4. The screen attempts to load permission groups and user data for assignment.
5. **Problem**: one or more shared API calls fail and the UI ends up showing a generic server error message instead of a usable management screen.

### Expected outcome

- The root cause is identified at the correct ownership level: frontend parsing/error handling, backend access-control logic, backend management service/controller behavior, or DB schema/migration drift.
- **User Management** and **Permission Group Management** both load successfully when the signed-in user has the required management screen access.
- If access is denied, the UI must show a permission-specific message based on stable error code/status handling, not a misleading generic server failure.
- If a backend or DB problem occurs, the backend must return a stable error code or at least a clearly classifiable failure path so the frontend can distinguish permission problems from internal failures.
- Route guard, sidebar visibility, screen-level read access, and write action control must remain aligned with `allowedScreenIds`, `screenFunctions`, and server-side enforcement.
- The fix must not broaden management access beyond the contract for `user-management`, `user-permission-hierarchy`, and `permission-group-management`.

---

## 2. Design

### 2.1 Security review (lightweight)

- [x] Security review performed
- Scope: access control, auth response consistency, and management-screen permission enforcement.
- Risks:
  - A UI-side fix could accidentally broaden access if frontend guard logic is relaxed without matching server rules.
  - Generic error handling can hide whether the real problem is `FORBIDDEN`, `FUNCTION_NOT_ALLOWED`, stale auth state, or an actual internal server failure.
  - `allowedScreenIds` and `screenFunctions` must not be trusted as the only enforcement layer; server-side checks must remain authoritative.
- Acceptance / recommendations:
  - The server must remain the single source of truth for management API access.
  - Read access and write access must stay separated; list view may load while create/update/delete must still require `screenFunctions.write === true`.
  - `POST /api/auth/login`, `GET /api/auth/check`, and `GET /api/auth/me` must expose a consistent permission shape for `isSystemAdmin`, `allowedScreenIds`, and `screenFunctions`.
  - Frontend must map permission-denied responses to permission-specific UI messages without exposing internal authorization details.

### Technical design

#### Codebase summary

- **Frontend screen flow**
  - `frontend/src/components/UserManagement/UserManagement.js` loads three shared management calls in `Promise.all`: hierarchy, users, and permission groups. A failure in any one of them collapses the screen into one top-level error state.
  - `frontend/src/components/PermissionGroupManagement/PermissionGroupManagement.js` and `frontend/src/components/PermissionGroupManagement/PermissionGroupPanel.js` load permission-group data and user data from the same management API family.
  - `frontend/src/services/userService.js` and `frontend/src/services/permissionGroupService.js` both parse backend `ApiResponse` wrappers, but they do so with separate implementations.
  - `frontend/src/utils/errorMessage.js` maps only a subset of backend error codes. Unknown failures fall through to raw backend messages or generic fallback text.

- **Frontend auth / access gating**
  - `frontend/src/App.js`, `frontend/src/components/AppSidebar.js`, and `frontend/src/utils/security.js` derive visible views and action availability from `isSystemAdmin`, `allowedScreenIds`, and `screenFunctions`.
  - `App.js` already treats `permission-group-management` and `user-permission-hierarchy` as partially equivalent for route access, so any backend mismatch can surface as "screen visible, API fails" behavior.

- **Backend management APIs**
  - `backend/src/main/java/com/logmng/controller/UserController.java`, `PermissionGroupController.java`, and `DepartmentController.java` participate in the user-management / permission-management flow.
  - `backend/src/main/java/com/logmng/config/ScreenAccessInterceptor.java` applies path-to-screen access checks before controller handling.
  - `backend/src/main/java/com/logmng/service/AuthService.java` calculates `allowedScreenIds`, `screenScopes`, and `screenFunctions`, and also exposes helper checks used by management controllers.

- **Backend service / DB coupling**
  - `backend/src/main/java/com/logmng/service/PermissionGroupService.java` reads `permission_group_screen.scope`, `read`, `write`, `approve`, and `decrypt`. These columns are assumed by runtime code.
  - `backend/src/main/java/com/logmng/service/UserPermissionHierarchyService.java` and `DecryptApproverService.java` query `app_user` and management-related tables that are part of the same screen load flow.
  - `backend/src/main/resources/db/schema.sql` defines `permission_group_screen` with `scope`, `read`, `write`, `approve`, and `decrypt`, while separate migration scripts exist for older environments.

#### Problem analysis

1. **Shared failure propagation across both screens**
   - Both screens depend on the same management API set. A failure in `GET /api/permission-groups`, `GET /api/users`, or `GET /api/departments/user-permission-hierarchy` can surface as the same generic management-screen failure.
   - `UserManagement` is especially sensitive because `Promise.all(...)` fails the entire load when any one dependency rejects.

2. **Probable DB schema / migration drift**
   - `PermissionGroupService` currently assumes the deployed DB includes `permission_group_screen.scope`, `read`, `write`, `approve`, and `decrypt`, and that the scope constraint accepts `team`.
   - If an environment has older schema state or missing migration application, management API reads or writes can fail with SQL exceptions that the backend converts to a generic internal server error.

3. **Access-control drift between frontend and backend**
   - Screen visibility, route access, controller checks, and interceptor rules are distributed across multiple files and have historically required repeated bug fixes in this domain.
   - A mismatch among `App.js`, `AppSidebar.js`, `ScreenAccessInterceptor`, and controller/helper logic can make the screen appear accessible while one of the underlying APIs still returns `403` or `FUNCTION_NOT_ALLOWED`.

4. **Error normalization is incomplete**
   - Backend runtime SQL/service failures in `PermissionGroupService` and related services currently bubble up as generic `RuntimeException`, which the global exception handler converts to `INTERNAL_SERVER_ERROR`.
   - Frontend error mapping does not reliably distinguish management permission problems from internal backend failures, so the user sees an opaque generic error message.

5. **Auth response and stale client-state consistency must be verified**
   - `auth/check`, saved local user data, and screen-function fallback logic are merged in `App.js`.
   - If permission fields are stale or inconsistent across auth endpoints, the UI can render a management view based on outdated screen access assumptions and then fail when data APIs are called.

#### Solution approach

**Frontend:**

- `UserManagement.js` must classify shared-load failures so that permission-denied responses, missing management capability, and internal backend failures are not all rendered as the same user-facing state.
- `PermissionGroupManagement.js` and `PermissionGroupPanel.js` must align their load and action error handling with the same management error contract used by `UserManagement.js`.
- `userService.js` and `permissionGroupService.js` must normalize backend `ApiResponse` parsing and propagate `status`, `code`, and meaningful message fields consistently.
- `utils/errorMessage.js` must cover management-domain codes and distinguish at least:
  - screen access denied,
  - function/write denied,
  - known permission-group data errors,
  - genuine internal server failure.
- `App.js`, `AppSidebar.js`, and `utils/security.js` must verify that route/menu behavior stays aligned with the server contract and does not rely on stale saved user state to over-grant management access.

**Backend:**

- `ScreenAccessInterceptor.java`, `AuthService.java`, `UserController.java`, `DepartmentController.java`, and `PermissionGroupController.java` must be reviewed together so all user-management / permission-group APIs use the same management-screen access model.
- Management controllers must continue to enforce read access and write access separately; any fix must preserve `FUNCTION_NOT_ALLOWED` for write operations when appropriate.
- `PermissionGroupService.java` and any shared management service that can fail due to schema/runtime issues must convert predictable data-layer problems into stable application-level failures where feasible, instead of leaking all such cases to generic `INTERNAL_SERVER_ERROR`.
- The root cause for the user-visible failure must be narrowed to the exact failing endpoint(s) among:
  - `GET /api/permission-groups`
  - `GET /api/users`
  - `GET /api/departments/user-permission-hierarchy`
  - follow-up permission-group user assignment APIs if reproduction shows dialog-level failures as well.

**DB:**

- Verify the deployed schema for `permission_group_screen` includes `scope`, `read`, `write`, `approve`, and `decrypt`.
- Verify the deployed `scope` constraint accepts `self`, `team`, and `all`, consistent with current runtime code and requirements.
- If drift is found, implementation must include the minimum migration or schema-alignment action required by the environment rather than relying on application-side workarounds.

**Contract / Spec:**

- `docs/contract.md` and `specs/permission-group-hierarchy.spec.yaml` must be verified against the actual management screen/API mapping and auth response fields used by the fix.
- If implementation changes which error code, message contract, or access mapping is relied upon, the relevant contract/spec documentation must be updated in the same requirement flow.

### Affected scopes and change targets (verification)

**Before finalizing §2**, the Requirements author verified the affected scopes per `docs/workflow/REQUIREMENTS-CHANGE-TARGET-CHECKLIST.md`.

| Scope | Affected? | §2 subsection and change file list complete? |
|-------|-----------|-----------------------------------------------|
| Backend | Yes | Yes |
| Frontend (config UI + view screen) | Yes | Yes |
| DB | Yes | Yes |
| Contract / Spec | Yes | Yes |
| Cursor tools (skills, specs) | No | N/A |

**Pattern checks applied:**

- **3.2 Permission or screen-access change**: included shared frontend guards, backend screen-access enforcement, auth response fields, and permission-related contract/spec references.
- **3.3 API or error-code change**: included frontend API/error handling, backend controller/service/error paths, and contract/spec verification.

**Change target verification result:**

- User Management and Permission Group Management were both treated as affected consumers.
- Shared frontend service/error utilities were included rather than scoping the issue to one screen component only.
- Shared backend access-control touchpoints (`ScreenAccessInterceptor`, `AuthService`, management controllers) were included.
- DB schema and migration files were included because current runtime code depends on permission-group-screen columns and constraints that may drift by environment.

### Planned change file list (expected change targets)

**(Planned at authoring. Implementing agent (Step 4) confirms or amends this list when implementation is complete.)**

#### Frontend

- `frontend/src/components/UserManagement/UserManagement.js`
  - Verify shared-load failure classification and management-screen error rendering for hierarchy/users/groups.
- `frontend/src/components/PermissionGroupManagement/PermissionGroupManagement.js`
  - Verify top-level permission-group screen access and load-state messaging align with the shared management error contract.
- `frontend/src/components/PermissionGroupManagement/PermissionGroupPanel.js`
  - Align permission-group list and related action error handling with the same management error contract used by User Management.
- `frontend/src/services/userService.js`
  - Normalize `ApiResponse` parsing and error propagation for management-user queries.
- `frontend/src/services/permissionGroupService.js`
  - Normalize `ApiResponse` parsing and error propagation for permission-group and hierarchy-related queries.
- `frontend/src/utils/errorMessage.js`
  - Add or verify mapping for management-domain permission and internal-error cases.
- `frontend/src/App.js`
  - Verify route access and auth-state merge behavior remain aligned with backend management access rules.
- `frontend/src/components/AppSidebar.js`
  - Verify menu visibility remains aligned with the same management-screen access model.
- `frontend/src/utils/security.js`
  - Verify minimal saved user data and permission helpers do not preserve stale management access state.

#### Backend

- `backend/src/main/java/com/logmng/config/ScreenAccessInterceptor.java`
  - Verify path-to-screen mapping for user-management, permission-group-management, and hierarchy-related APIs.
- `backend/src/main/java/com/logmng/service/AuthService.java`
  - Verify management access helper logic and auth response field consistency for `allowedScreenIds` and `screenFunctions`.
- `backend/src/main/java/com/logmng/controller/AuthController.java`
  - Verify `login`, `check`, and `me` expose consistent permission fields used by frontend gating.
- `backend/src/main/java/com/logmng/controller/UserController.java`
  - Verify user-management list access and known management failure handling.
- `backend/src/main/java/com/logmng/controller/DepartmentController.java`
  - Verify hierarchy endpoint access and failure handling because User Management depends on this API.
- `backend/src/main/java/com/logmng/controller/PermissionGroupController.java`
  - Verify permission-group list and related management endpoint behavior.
- `backend/src/main/java/com/logmng/service/PermissionGroupService.java`
  - Verify permission-group data loading against current DB schema and convert predictable schema/runtime issues into stable failures where appropriate.
- `backend/src/main/java/com/logmng/service/UserPermissionHierarchyService.java`
  - Verify hierarchy query path because it is part of the same User Management load.
- `backend/src/main/java/com/logmng/service/DecryptApproverService.java`
  - Verify user list query path if reproduction identifies `GET /api/users` as the failing endpoint.
- `backend/src/main/java/com/logmng/exception/GlobalExceptionHandler.java`
  - Verify management-domain errors are not unnecessarily collapsed into an opaque internal server error when a stable business/application error can be returned.

#### DB

- `backend/src/main/resources/db/schema.sql`
  - Verify `permission_group_screen` column and constraint definition match the runtime code assumptions.
- `backend/src/main/resources/db/migrate-permission-group-screen-scope.sql`
  - Verify older environments are not left on pre-`team` scope constraints.
- `backend/src/main/resources/db/migrate-permission-group-screen-scope-team.sql`
  - Verify this migration is applied where needed.
- `backend/src/main/resources/db/migrate-permission-group-screen-functions.sql`
  - Verify `read`, `write`, and `approve` columns exist where runtime code expects them.
- `backend/src/main/resources/db/migrate-permission-group-screen-decrypt.sql`
  - Verify `decrypt` column exists where runtime code expects it.
- `backend/src/main/resources/db/init-data.sql`
  - Verify seeded management permission groups still match the management-screen/API contract used in reproduction and regression tests.

#### Contract / Spec

- `docs/contract.md`
  - Verify management screen/API mapping and auth response assumptions used by the fix.
- `specs/permission-group-hierarchy.spec.yaml`
  - Verify permission-group, user-management, hierarchy, and auth response behavior remain aligned with implementation.
- `docs/api-definition.md`
  - Update only if the fix changes documented management error behavior or auth/API response handling.

## 3. Test approach

### Test case list (required)

| ID | Scope | Type | Scenario (input / condition) | Expected result | Verification (unit / integration / manual) |
|----|-------|------|------------------------------|-----------------|--------------------------------------------|
| TC-01 | Integration | Exception | Reproduce the current issue on **User Management** with a management-capable user. Capture the exact failing API among hierarchy, users, and permission groups. | The failing endpoint and its status/code/message are identified; the requirement is implemented against the true shared failure path rather than guesswork. | Integration (browser + network / backend log) |
| TC-02 | Integration | Exception | Reproduce the current issue on **Permission Group Management** with the same user/session. Capture the exact failing API among group list and user-list dependencies. | The failing endpoint and its status/code/message are identified, and overlap with TC-01 is confirmed or ruled out. | Integration (browser + network / backend log) |
| TC-03 | Backend | Exception | Environment with missing or outdated `permission_group_screen` schema elements (or equivalent test setup) invokes `GET /api/permission-groups`. | Backend either succeeds with aligned schema or returns a stable, diagnosable failure path; no silent ambiguity about missing columns/constraints. | Integration or unit/integration DB test |
| TC-04 | Backend | Regression | User with valid management access calls `GET /api/users`, `GET /api/departments/user-permission-hierarchy`, and `GET /api/permission-groups`. | All required management endpoints return success and share the same access model. | Integration |
| TC-05 | Backend | Regression | User without management screens calls the same management APIs. | Backend returns `403` with the expected permission error code/message shape; access is not broadened by the fix. | Integration |
| TC-06 | Frontend | Regression | Management-capable user opens **User Management** after the fix. | The screen loads hierarchy, users, and permission groups successfully without a generic server error. | Manual / browser |
| TC-07 | Frontend | Regression | Management-capable user opens **Permission Group Management** after the fix. | The screen loads permission groups and related user assignment data successfully without a generic server error. | Manual / browser |
| TC-08 | Frontend | Exception | Backend returns `403 FORBIDDEN` or `FUNCTION_NOT_ALLOWED` for a management API. | The UI shows a permission-specific message rather than the generic server failure message. | Unit or manual / browser |
| TC-09 | Frontend | Exception | Backend returns a genuine internal failure for a management API. | The UI shows a generic internal-error message that is clearly distinguished from permission denial. | Unit or manual / browser |
| TC-10 | Integration | Regression | `POST /api/auth/login`, `GET /api/auth/check`, and `GET /api/auth/me` are exercised for the same management-capable user. | `isSystemAdmin`, `allowedScreenIds`, and `screenFunctions` are consistent enough that the UI and server enforce the same management access assumptions. | Integration |

### Test scenarios

#### Scenario 1: Shared failing endpoint identification

1. Sign in as a user expected to access management screens.
2. Open **User Management** and record the status/code/message of hierarchy, users, and permission-group APIs.
3. Open **Permission Group Management** and record the status/code/message of list/user APIs.
4. Verification: determine whether one shared endpoint causes both failures or whether there are separate but related failures.

#### Scenario 2: Schema drift verification

1. Verify the deployed DB structure for `permission_group_screen`.
2. Exercise `GET /api/permission-groups` and any write path involved in reproduction.
3. Verification: confirm whether runtime code and DB schema/migrations are aligned.

#### Scenario 3: Permission-denied regression

1. Sign in as a user without `user-management`, `user-permission-hierarchy`, or `permission-group-management`.
2. Attempt to open both management screens or call the related APIs.
3. Verification: access is still denied with permission-specific handling, not silently broadened.

#### Scenario 4: Management-capable success path

1. Sign in as a user whose permissions should allow management access.
2. Open **User Management** and **Permission Group Management**.
3. Verification: both screens load their data normally and no generic server error is shown.

### Test data

- One user with valid management access through `user-management`, `user-permission-hierarchy`, or `permission-group-management`.
- One user without any management screen access.
- A DB state representative of the deployed environment, including verification of `permission_group_screen` schema/migration status.

### Test environment

- Frontend: `http://localhost:3001`
- Backend: `http://localhost:9200`
- Database: PostgreSQL per project setup

### 3.5 Browser automation verification (optional)

- **Applicable TCs**: TC-01, TC-02, TC-06, TC-07
- **Procedure per TC**:
  - `browser_navigate` to the app,
  - sign in with a management-capable user,
  - open **User Management** and **Permission Group Management**,
  - use browser/network inspection or backend log correlation to confirm which API fails or succeeds,
  - verify the user-facing error state for permission-denied vs internal-error cases.
- **Reference**: `docs/workflow/BROWSER-AUTOMATION-VERIFICATION-POLICY.md`

## 4. Checklist

### Frontend verification
- [ ] Shared management API failures are classified consistently across both affected screens.
- [ ] Permission-denied and internal-error messages are distinguished for management APIs.
- [ ] Saved user-state merge does not preserve stale management access.

### Backend verification
- [ ] Management API access rules are aligned across interceptor, auth helpers, and controllers.
- [ ] Known schema/runtime failures are diagnosable and not all collapsed into opaque generic errors.
- [ ] Read vs write permission enforcement remains intact.

### Integration
- [x] Both affected screens are rechecked because they share API dependencies.
- [x] The true failing endpoint(s) are identified and verified after the fix.
- [ ] Non-management users remain blocked.

### Documentation
- [x] Requirement doc completed.
- [ ] Contract/spec docs updated if management access or error-handling contract changes.

## 5. Test results

### Test run date
- 2026-03-13

### Test results

#### Frontend
- Frontend reachability check passed at `http://localhost:3001`.
- **User Management** loaded without the generic server error after DB schema alignment.
- **Permission Group Management** loaded without the generic server error after DB schema alignment.
- No additional issue was found within this verification scope.

#### Backend
- Root cause was confirmed as runtime DB schema drift in PostgreSQL: the running `permission_group_screen` table was missing the `decrypt` column while `PermissionGroupService` queried that column.
- The existing migration file `backend/src/main/resources/db/migrate-permission-group-screen-decrypt.sql` was applied to the local DB successfully.
- DB apply verification passed:
  - migration apply command exited with `0`,
  - column verification returned `decrypt|boolean|YES`,
  - a query selecting `decrypt` succeeded.
- Service and API regression checks passed:
  - backend health check OK,
  - `GET /api/permission-groups` returned `200`,
  - `GET /api/users` returned `200`,
  - `GET /api/departments/user-permission-hierarchy?view=tree` returned `200`.

**Commands:**

```bash
# Applied local PostgreSQL migration:
#   backend/src/main/resources/db/migrate-permission-group-screen-decrypt.sql
# Verified schema column metadata:
#   decrypt|boolean|YES
# Verified SELECT using permission_group_screen.decrypt
# Verified backend health, frontend reachability, and management API 200 responses
```

**Outcome:**
- The failure was traced to DB schema drift, not to frontend route gating or a backend permission-rule regression in this verification scope.
- After applying the existing DB migration, the previously failing management API and its dependent screens recovered successfully.
- No additional issue was found within the scope of this DB migration verification.

### Issues found and resolution

#### Issue 1: Runtime schema drift in `permission_group_screen`
**Cause**: The running PostgreSQL schema was behind the runtime expectation and lacked the `decrypt` column required by `PermissionGroupService`, which caused `GET /api/permission-groups` to fail with `500` and both management screens to surface a generic server error.

**Resolution**:
1. Applied the existing migration `backend/src/main/resources/db/migrate-permission-group-screen-decrypt.sql` to the local DB.
2. Verified that the `decrypt` column exists as `boolean` and nullable (`decrypt|boolean|YES`).
3. Re-ran DB/API/screen verification and confirmed recovery of the affected management flow.

### Next steps
1. Ensure environments that may predate this migration are checked for the same `permission_group_screen.decrypt` schema drift before release or deployment verification.

## 6. Error remedy result (cause and action) — for error/bug fix requirements only

- **Requirement ID**: 20260313-user-management-permission-group-server-error
- **Root cause**: Runtime DB schema drift. The running PostgreSQL `permission_group_screen` table was missing the `decrypt` column, while backend `PermissionGroupService` queried `decrypt`, causing `GET /api/permission-groups` to fail with `500` and both management screens to show a generic server error.
- **Actions taken**: Applied the existing migration file `backend/src/main/resources/db/migrate-permission-group-screen-decrypt.sql` to the local DB, then verified the applied schema (`decrypt|boolean|YES`), confirmed a query selecting `decrypt` succeeded, and re-ran backend/frontend/API verification for the affected management flow.
- **Result**: Backend health was OK, frontend was reachable, `GET /api/permission-groups` returned `200`, `GET /api/users` returned `200`, `GET /api/departments/user-permission-hierarchy?view=tree` returned `200`, and both **User Management** and **Permission Group Management** loaded without the generic server error. No additional issue was found in this verification scope.
- **Completed**: 2026-03-13

---

**Author**: Requirements subagent
**Date**: 2026-03-13
**Status**: Verified after DB migration
