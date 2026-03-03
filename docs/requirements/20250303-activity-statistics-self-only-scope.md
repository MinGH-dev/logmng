# 20250303 - Activity statistics and activity log self-only scope for non-admin users

## 1. User requirement

### Requirement description

When a user has access to the **activity log**, **search history**, or **statistics** screens (via permission groups or `is_system_admin`), the **data scope** must differ by role and by permission group configuration:

- **System administrator** (`is_system_admin = true`): Full scope — can view all users' activity logs, search history, and statistics. Filter controls (user, department, IP) remain available. Scope from permission group is ignored.
- **General user** (non-admin, with screen access via permission group): Scope is **configurable per screen** in the permission group. When creating or editing a permission group, for screens that support scope (activity-log, statistics, search-history), the administrator selects "본인만" (self) or "전체" (all). Default: "본인만".
  - **Scope = self**: User sees only their own data. Filter controls that would expand scope beyond the current user are hidden or disabled.
  - **Scope = all**: User sees all users' data. Filter controls (user, department, IP) are available.

**Important**: Scope is **configurable in the Permission Group UI**, not implicit only. Administrators choose per-screen scope when assigning screens to a permission group.

### User scenario

1. **Admin user**: A system administrator logs in and opens the activity log, search history, or statistics screen. They see user/department/IP filter controls and can query any user's data. Behavior is unchanged from current implementation.

2. **General user with scope=self**: A non-admin user whose permission group has `activity-log`, `search-history`, or `statistics` with scope "본인만" (self) logs in and opens those screens. They see only their own data. Filter controls for user, department, or IP are hidden or disabled.

3. **General user with scope=all**: A non-admin user whose permission group has one of those screens with scope "전체" (all) logs in and opens that screen. They see all users' data. Filter controls are available.

4. **Permission group configuration**: An administrator configures a permission group and assigns screens (activity-log, statistics, search-history). For each of these screens, a scope dropdown appears: "본인만" (self) | "전체" (all). Default: "본인만".

5. **Problem**: Currently, non-admin users with screen access can potentially query other users' data if the API accepts `userId`, `department`, or `ip` parameters and does not enforce ownership. `UserActivityLogService.searchActivityLogs` accepts `userId` in the request body; `ActivityStatisticsService` accepts `userId`, `department`, `ip` as query params. Without enforcement, a non-admin could abuse these parameters to view others' data.

### Expected outcome

- **Admin**: Full access to all users' activity logs, search history, and statistics. All filter controls available. Permission group scope is ignored.
- **Non-admin with scope=self**: Activity log, search history, and statistics show only the current user's data. User/department/IP filter controls are hidden or disabled on the frontend; backend enforces `userId = currentUser` regardless of request parameters.
- **Non-admin with scope=all**: Activity log, search history, and statistics show all users' data. Filter controls are available. Backend passes request params as-is.
- **Permission groups**: Screen IDs (activity-log, statistics, search-history) remain as-is. **New scope field** per screen in permission group configuration. Scope dropdown ("본인만" | "전체") shown next to each scope-supporting screen in ScreenSelectionTree. Default: "본인만".
- **Security**: Backend APIs must enforce scope at the service/controller layer so that even if the frontend is bypassed, non-admins cannot access other users' data beyond their configured scope.

---

## 2. Design

### 2.1 Security review (optional; when PII / decryption / access control)

When **Security** subagent has reviewed: summarize risks, acceptance criteria, and design recommendations here. Reference: `docs/workflow/CURSOR-SUBAGENTS-DESIGN.md`, `docs/workflow/WORKFLOW_CHECKLIST.md`.

- [x] Security review performed (check if applicable)
- **Scope**: PII/access control — activity logs, search history, and statistics may contain user identifiers and activity metadata. Non-admin users must not access other users' data.
- **Risks**:
  - **Scope bypass / parameter tampering**: Non-admin with scope=self could send `userId=otherUser`, `department`, or `ip` in API requests to bypass frontend restrictions. Backend must enforce scope server-side and ignore or override client-supplied parameters when scope=self.
  - **Privilege escalation**: If `is_system_admin` check is missing or incorrect, a non-admin could gain full scope. Ensure admin check is performed consistently before any scope resolution.
  - **IDOR (Insecure Direct Object Reference)**: `getActivityLogDetail(id)`, `getById(id)` for search-history — non-admin with scope=self could access another user's record by ID if ownership check is missing. Must verify `user_id == currentUser` before returning.
  - **Scope configuration tampering**: `screenScopes` in auth response must be derived server-side from DB (`permission_group_screen.scope`). Never trust client-supplied scope; always resolve from authenticated user + permission group.
  - **Default scope ambiguity**: NULL or missing scope in DB must be treated as 'self' consistently across all controllers and services.
  - **Frontend-only enforcement**: Backend must never rely on frontend to restrict scope. Even if filters are hidden, a client can call APIs directly with arbitrary parameters.
- **Acceptance / recommendations**:
  - Backend MUST enforce scope at service/controller layer for every API that accepts `userId`, `department`, `ip`, or record ID. No exception.
  - Scope resolution order: (1) `is_system_admin = true` → full scope; (2) `is_system_admin = false` → use permission group scope for that screen; (3) NULL/missing scope → treat as 'self'.
  - For scope=self: always override `userId` with current user; ignore `department`, `ip`; for `getById(id)` / `getActivityLogDetail(id)` verify ownership and return 403 if `user_id != currentUser`.
  - `screenScopes` in login/me response must be computed from DB (permission_group_screen.scope), never from request body or client state.
  - Consider audit logging when non-admin with scope=self sends `userId=otherUser` (or similar) — log for security monitoring without exposing to client.
  - Unit/integration tests must cover parameter tampering (TC-02, TC-06, TC-08, TC-12). Verify backend returns only current user's data or 403, regardless of request params.

### Technical design

#### Problem analysis

1. **ActivityStatisticsService**: Accepts `userId`, `department`, `ip` as query parameters. When these are empty, it returns all data. A non-admin with screen access could omit filters or pass arbitrary values to view others' data.

2. **UserActivityLogService.searchActivityLogs**: Accepts `UserActivityLogSearchRequest` with `userId`, `username`, `ipAddress`, etc. The request body is client-controlled; a non-admin could set `userId` to another user and retrieve their activity logs.

3. **UserActivityLogService.getActivityLogDetail**: Fetches by `id` only. A non-admin could request another user's log detail by ID if no ownership check exists.

4. **SearchHistoryService.list**: Already filters by `userId` from the controller (current user). This is correct. Need to verify `getById`, `reRequest`, and other operations enforce ownership for non-admins.

5. **Frontend**: Statistics and activity-log screens expose user/department/IP filter controls. For non-admins with scope=self, these must be hidden or disabled so users cannot attempt to expand scope.

6. **Permission groups**: `specs/permission-group-hierarchy.spec.yaml` §4 defines screen IDs; no scope concept today. Scope must become configurable per screen.

#### Solution approach

**Design principle**: Scope is **configurable in Permission Group UI** per screen. For screens that support scope (activity-log, statistics, search-history), show a scope dropdown: "본인만" (self) | "전체" (all). Default: "본인만".

**Scope resolution logic:**

- **is_system_admin = true**: Always full scope. Ignore permission group scope.
- **is_system_admin = false**: Use scope from permission group for that screen.
  - If screen has scope "all": user sees all data; filter controls available.
  - If screen has scope "self" or scope not set: user sees only own data; filter controls hidden or disabled.
  - NULL or missing scope in DB = "self".

**Backend:**

1. **ActivityStatisticsController** and **ActivityStatisticsService**:
   - Inject current user, `is_system_admin`, and per-screen scope (e.g. from auth context or `screenScopes`).
   - When `is_system_admin = true`: Pass request params as-is (full scope).
   - When `is_system_admin = false` and scope = "self": Override `userId` with current user; ignore `department`, `ip`. Pass only `userId = currentUser`.
   - When `is_system_admin = false` and scope = "all": Pass request params as-is.

2. **UserActivityLogController** and **UserActivityLogService**:
   - Same logic: admin → full; non-admin + scope=self → override userId; non-admin + scope=all → pass as-is.
   - For `getActivityLogDetail(id)`: When non-admin + scope=self, verify ownership; return 403 if `user_id != currentUser`. When scope=all, allow.

3. **SearchHistoryService**:
   - Same scope resolution. When scope=self, enforce ownership on list/getById/reRequest/etc. When scope=all, allow cross-user access for non-admins.

4. **ActivityStatisticsService.getUsers()**, **getIps()**:
   - When scope=self: Return only current user / current user's IPs. When scope=all: Return all.

**Frontend:**

1. **ScreenSelectionTree** (Permission Group UI):
   - For screens activity-log, statistics, search-history: show scope dropdown next to each checkbox: "본인만" (self) | "전체" (all). Default: "본인만".

2. **Statistics screens** (`ActivityStatistics`, `StatisticsView`, `StatisticsFilters`):
   - When scope=self (from auth/screenScopes): Hide or disable user, department, IP filter controls. When scope=all: Show filters.

3. **Activity log screens** (`UserActivityLogList`, `UserActivityLogSearchForm`):
   - Same: scope=self → hide filters; scope=all → show filters.

4. **Search history**:
   - scope=self → list/detail enforce self-only; scope=all → show all.

**Permission groups**: Screen IDs stay as-is. **New scope field** per screen in `permission_group_screen` table.

**API changes:**

- **GET/POST/PUT permission-groups**: Extend `allowedScreens` to include scope per screen, e.g. `allowedScreens: [{ screenId: string, scope?: 'self'|'all' }]` or keep `allowedScreens: string[]` and add `screenScopes: Record<string, 'self'|'all'>`.
- **Auth response (login/me)**: Include `screenScopes` or per-screen scope in allowedScreenIds so frontend knows whether to show filters (scope=self → hide; scope=all → show).

### Change file list

**(Confirmed by Backend subagent. Actual files changed.)**

#### Backend

- `backend/src/main/java/com/logmng/dto/response/AllowedScreenItem.java` (new)
  - DTO for `{ screenId, scope? }` in allowedScreens.

- `backend/src/main/java/com/logmng/dto/request/AllowedScreenListDeserializer.java` (new)
  - Deserializer for allowedScreens: accepts string[] or `[{ screenId, scope? }]`.

- `backend/src/main/java/com/logmng/dto/response/PermissionGroupResponse.java`
  - `allowedScreens` changed from `List<String>` to `List<AllowedScreenItem>`.

- `backend/src/main/java/com/logmng/dto/request/PermissionGroupCreateRequest.java`
  - `allowedScreens` changed to `List<AllowedScreenItem>` with `@JsonDeserialize(AllowedScreenListDeserializer)`.

- `backend/src/main/java/com/logmng/dto/request/PermissionGroupUpdateRequest.java`
  - Same as CreateRequest.

- `backend/src/main/java/com/logmng/dto/response/LoginResponse.java`
  - Added `screenScopes: Map<String, String>`.

- `backend/src/main/java/com/logmng/constants/ScreenConstants.java`
  - Added `SCREENS_WITH_SCOPE`, `supportsScope()`.

- `backend/src/main/java/com/logmng/util/ScopeHelper.java` (new)
  - `resolveScope(screenId, isSystemAdmin, screenScopes)` helper.

- `backend/src/main/java/com/logmng/service/PermissionGroupService.java`
  - `loadAllowedScreens` returns `List<AllowedScreenItem>` with scope; `saveAllowedScreens` persists scope.
  - Added `getScreenScopesForUser()` for activity-log, statistics, search-history.
  - `validateAllowedScreens` updated for AllowedScreenItem.

- `backend/src/main/java/com/logmng/service/AuthService.java`
  - `resolveScreenScopes()`; login and getCurrentUserInfo set `screenScopes` in response.

- `backend/src/main/java/com/logmng/controller/AuthController.java`
  - Session stores `screenScopes`; check endpoint returns `screenScopes`.

- `backend/src/main/java/com/logmng/controller/ActivityStatisticsController.java`
  - Scope enforcement: scope=self → override userId with current user, ignore department/ip; getUsers/getIps filter by current user.

- `backend/src/main/java/com/logmng/controller/UserActivityLogController.java`
  - searchActivityLogs: scope=self → override request.userId; getActivityLogDetail: ownership check when scope=self.

- `backend/src/main/java/com/logmng/controller/SearchHistoryController.java`
  - list, getDetail, reRequest: pass scopeAll from ScopeHelper; scope=self enforces ownership.

- `backend/src/main/java/com/logmng/service/ActivityStatisticsService.java`
  - `getUsers(userIdFilter)`, `getIps(userIdFilter)` for scope=self filtering.

- `backend/src/main/java/com/logmng/service/UserActivityLogService.java`
  - `getActivityLogDetail(id, currentUserIdForOwnership)`: 403 when scope=self and not owner.

- `backend/src/main/java/com/logmng/service/SearchHistoryService.java`
  - `list(..., scopeAll)`, `getDetail(..., scopeAll)`, `reRequest(..., scopeAll)`: scope=all skips ownership check.

#### Frontend

- `frontend/src/components/PermissionGroupManagement/ScreenSelectionTree.js`
  - For screens activity-log, statistics, search-history: add scope dropdown next to each checkbox: "본인만" (self) | "전체" (all). Default: "본인만". onChange receives `[{ screenId, scope? }]`.

- `frontend/src/components/PermissionGroupManagement/ScreenSelectionTree.css`
  - Styles for scope dropdown (`.screen-selection-scope`, `.screen-selection-item` flex).

- `frontend/src/components/PermissionGroupManagement/PermissionGroupPanel.js`
  - Handle allowedScreens as `[{ screenId, scope? }]`.
  - Normalize on load; send scope to create/update API.

- `frontend/src/components/ActivityStatistics.js`
  - Use `user.screenScopes?.statistics`: scope=self → hide filters, omit userId/department/ip from API; scope=all → show filters.

- `frontend/src/components/StatisticsFilters.js`
  - When `hideUserFilters`: hide user, department, IP filter controls.

- `frontend/src/components/UserActivityLog/UserActivityLogSearchForm.js`
  - When `hideUserFilters`: hide userId, username, ipAddress fields.

- `frontend/src/components/UserActivityLog/UserActivityLogList.js`
  - When scope=self: omit userId, username, ipAddress from search params; pass `hideUserFilters` to form.

- `frontend/src/App.js`
  - Pass `user` to UserActivityLogList, ActivityStatistics, SearchHistoryList.
  - Merge `screenScopes` from auth/check and login response.

- `frontend/src/utils/security.js`
  - Add `screenScopes` to saveMinimalUserData.

#### Database

- Migration script (e.g. `backend/src/main/resources/db/migrate-permission-group-screen-scope.sql`)
  - Add `scope VARCHAR(10)` column to `permission_group_screen` table. Values: 'self', 'all'. NULL or missing = 'self'.
  - Init-data if needed for existing rows.

#### Spec / contract

- `specs/permission-group-hierarchy.spec.yaml`
  - Extend allowedScreens or add screenScopes. Document scope per screen for activity-log, statistics, search-history.

- `docs/api-definition.md` (or `docs/contract.md`)
  - Document API change: GET/POST/PUT permission-groups extend allowedScreens to include scope per screen, e.g. `allowedScreens: [{ screenId: string, scope?: 'self'|'all' }]` or `screenScopes: Record<string, 'self'|'all'>`. Auth response (login/me) includes `screenScopes` so frontend knows whether to show filters.

### Database changes

- Add `scope VARCHAR(10)` column to `permission_group_screen` table.
- Values: `'self'`, `'all'`. NULL or missing = `'self'`.
- Migration script required. Init-data for existing rows if needed.

### § DBA 검토 (permission_group_screen.scope)

**Input**: Requirement doc §2, schema.sql `permission_group_screen` table.

**Design notes for DB subagent** (no code; DB subagent implements):

1. **Column definition**
   - `scope VARCHAR(10) NULL` — values `'self'`, `'all'`, or NULL.
   - `VARCHAR(10)` is sufficient (`'self'` = 4 chars, `'all'` = 3 chars).
   - Optional: `CHECK (scope IS NULL OR scope IN ('self', 'all'))` to enforce domain. Recommend adding for data integrity.

2. **Migration approach**
   - **New migration file**: `backend/src/main/resources/db/migrate-permission-group-screen-scope.sql`.
   - Use `ALTER TABLE permission_group_screen ADD COLUMN scope VARCHAR(10) DEFAULT NULL`.
   - **No backfill required**: Application treats NULL as `'self'` per requirement. Existing rows remain NULL.
   - **schema.sql**: Update `permission_group_screen` CREATE TABLE to include `scope VARCHAR(10) NULL` for fresh installs.

3. **Index considerations**
   - **Current query patterns**: (a) `SELECT screen_id [, scope] FROM permission_group_screen WHERE permission_group_id = ?`; (b) JOIN with `app_user_permission_group` for user's screen scopes.
   - **PK**: `(permission_group_id, screen_id)` already covers lookups by `permission_group_id`.
   - **Existing index**: `idx_permission_group_screen_screen` on `(screen_id)` for reverse lookup — unchanged.
   - **Conclusion**: No new index needed. Scope is a small, low-cardinality column; PK and existing index suffice.

4. **Backward compatibility**
   - Existing rows: After migration, `scope` = NULL. Application logic: NULL or missing → `'self'`.
   - **Init-data**: Current `INSERT INTO permission_group_screen (permission_group_id, screen_id)` omits scope. After migration, new rows get NULL. No init-data change required for semantics; NULL = `'self'` is correct.
   - **Insert path**: Backend `PermissionGroupService.saveAllowedScreens` will need to include `scope` in INSERT when creating/updating. Migration only adds column; application handles read/write.

5. **Scope applicability**
   - Scope is meaningful only for screens: `activity-log`, `statistics`, `search-history`. For other screens, scope is ignored; NULL is acceptable.

**Output**: DB subagent implements migration file and schema.sql update.

---

## 3. Test approach

### Test case list (required)

| ID | Type | Scenario (input / condition) | Expected result | Verification (unit / integration / manual) |
|----|------|------------------------------|-----------------|--------------------------------------------|
| TC-01 | Normal | Admin calls GET /api/statistics/activity/daily with userId=otherUser | Returns other user's statistics | Integration (curl with admin session) |
| TC-02 | Normal | Non-admin with scope=self calls GET /api/statistics/activity/daily with userId=otherUser | Returns only current user's statistics; userId param ignored | Integration |
| TC-03 | Normal | Non-admin with scope=self calls GET /api/statistics/activity/daily with no params | Returns only current user's statistics | Integration |
| TC-04 | Normal | Non-admin with scope=all calls GET /api/statistics/activity/daily with userId=otherUser | Returns other user's statistics | Integration |
| TC-05 | Normal | Admin calls POST /api/activity-log/search with userId=otherUser | Returns other user's activity logs | Integration |
| TC-06 | Exception | Non-admin with scope=self calls POST /api/activity-log/search with userId=otherUser | Returns only current user's logs; userId overwritten | Integration |
| TC-07 | Normal | Non-admin with scope=all calls POST /api/activity-log/search with userId=otherUser | Returns other user's activity logs | Integration |
| TC-08 | Exception | Non-admin with scope=self calls GET /api/activity-log/{id} where id belongs to another user | 403 or 404 | Integration |
| TC-09 | Normal | Non-admin with scope=self calls GET /api/activity-log/{id} where id belongs to current user | Returns log detail | Integration |
| TC-10 | Normal | Non-admin with scope=all calls GET /api/activity-log/{id} where id belongs to another user | Returns log detail | Integration |
| TC-11 | Normal | Non-admin with scope=self calls GET /api/search-history | Returns only current user's list | Integration |
| TC-12 | Exception | Non-admin with scope=self calls GET /api/search-history/{id} where id belongs to another user | 403 or 404 | Integration |
| TC-13 | Normal | Non-admin with scope=all calls GET /api/search-history | Returns all users' list (or filterable) | Integration |
| TC-14 | Edge | Non-admin with scope=self and activity-log screen access: frontend hides user/IP filters | Filter controls not visible | Manual / browser automation |
| TC-15 | Edge | Non-admin with scope=all and activity-log screen access: frontend shows user/IP filters | Filter controls visible | Manual / browser automation |
| TC-16 | Edge | Admin with activity-log screen access: frontend shows user/IP filters | Filter controls visible | Manual / browser automation |
| TC-17 | Normal | Permission group create/update: scope dropdown shown for activity-log, statistics, search-history; default "본인만" | Scope persisted; auth response includes screenScopes | Integration / manual |

### Test scenarios

#### Scenario 1: Admin full scope

1. Log in as system administrator.
2. Open activity log, statistics, and search history screens.
3. Use user/department/IP filters to query different users.
4. **Verification**: Data for all users is returned. Permission group scope is ignored.

#### Scenario 2: Non-admin with scope=self (from permission group)

1. Create a permission group with activity-log, statistics, search-history screens and scope "본인만" (self) for each.
2. Assign a non-admin user to that permission group.
3. Log in as that non-admin user and open each screen.
4. **Verification**: Only current user's data is shown. User/IP filter controls are hidden or disabled.
5. Attempt to call API directly with userId=otherUser (e.g. via curl or dev tools).
6. **Verification**: Backend returns only current user's data or 403.

#### Scenario 3: Non-admin with scope=all (from permission group)

1. Create a permission group with activity-log, statistics, or search-history screen and scope "전체" (all).
2. Assign a non-admin user to that permission group.
3. Log in as that non-admin user and open the screen.
4. **Verification**: All users' data is shown. User/IP filter controls are visible and functional.
5. Call API with userId=otherUser.
6. **Verification**: Backend returns other user's data.

#### Scenario 4: Activity log detail ownership (scope=self)

1. As non-admin with scope=self, obtain an activity log ID that belongs to another user (e.g. from admin session or DB).
2. Call GET /api/activity-log/{id} as non-admin.
3. **Verification**: 403 or 404.

#### Scenario 5: Permission group UI — scope dropdown

1. As admin, open permission group create or edit form.
2. Select activity-log, statistics, or search-history screen.
3. **Verification**: Scope dropdown appears next to each: "본인만" | "전체". Default: "본인만".
4. Save permission group.
5. **Verification**: Scope is persisted; login/me response includes screenScopes for that user.

### Test data

- At least three users: (1) one with `is_system_admin = true`; (2) one non-admin with permission group containing activity-log, statistics, search-history with scope=self; (3) one non-admin with permission group containing at least one of those screens with scope=all.
- Activity logs and search history records for multiple users.

### Test environment

- Frontend: `http://localhost:3001` (or per contract)
- Backend: `http://localhost:9200`
- Database: PostgreSQL (per docs/contract.md)

### 3.5 Browser automation verification (optional)

- **Applicable TCs**: TC-14, TC-15, TC-16, TC-17.
- **Procedure**: Login as admin → navigate to activity-log/statistics → `browser_snapshot` → verify filter controls visible. Logout, login as non-admin with scope=self → navigate → verify filter controls hidden. Logout, login as non-admin with scope=all → navigate → verify filter controls visible. For TC-17: open permission group create/edit → select activity-log/statistics/search-history → verify scope dropdown with default "본인만".
- **Reference**: `docs/workflow/BROWSER-AUTOMATION-VERIFICATION-POLICY.md`.

---

## 4. Checklist

### Frontend verification

- [ ] ScreenSelectionTree shows scope dropdown ("본인만" | "전체") for activity-log, statistics, search-history; default "본인만"
- [ ] Filter controls hidden/disabled for non-admin with scope=self on statistics and activity-log
- [ ] Filter controls visible for non-admin with scope=all
- [ ] API calls use current user only when scope=self; pass params as-is when scope=all
- [ ] Error handling verified (403/404 when scope=self and accessing others' data)

### Backend verification

- [ ] Scope resolution: is_system_admin=true → full; is_system_admin=false → use permission group scope
- [ ] Scope enforcement in ActivityStatisticsController, UserActivityLogController, SearchHistoryController
- [ ] Ownership check for getActivityLogDetail, search-history getById/reRequest when scope=self
- [ ] PermissionGroupService and AuthService include screenScopes in response
- [ ] Unit/integration tests pass

### Database

- [ ] Migration adds scope column to permission_group_screen; NULL = 'self' (per § DBA 검토)
- [ ] schema.sql updated for fresh installs; init-data backfill not required (NULL = 'self')

### Integration

- [ ] End-to-end: non-admin with scope=self sees only own data
- [ ] End-to-end: non-admin with scope=all sees all data
- [ ] End-to-end: admin sees all data (scope ignored)
- [ ] Direct API abuse (userId=otherUser) blocked for non-admin with scope=self

### Documentation

- [ ] Requirement doc completed
- [ ] Spec/contract updated with scope behavior and API changes

---

## 5. Test results

### Test run date

- 2026-03-03 (QA verification)

### Test results

#### Frontend

- **Unit tests**: No tests found (`npm test -- --watchAll=false` → exit 1, 0 matches). N/A.
- **Browser (TC-14–TC-17)**: Skipped — integration failures (TC-02, TC-06, TC-08) blocked full verification. Will re-run after bugfix.

#### Backend

- **Unit tests**: Pass (`cd backend && mvn test` → exit 0).
- **Integration (curl)**:

| TC | Result | Note |
|----|--------|------|
| TC-01 | Pass | Admin GET statistics with userId=user2 → user2's data |
| TC-02 | **Pass** (bugfix-2) | user1 (scope=self) with userId=user2 → user1's statistics only |
| TC-03 | Pass | user1 (scope=self) no params → user1's data |
| TC-04 | Pass | user2 (scope=all) with userId=user1 → user1's data |
| TC-05 | Pass | Admin POST activity-log/search userId=user2 → user2's logs |
| TC-06 | **Pass** (bugfix-2) | user1 (scope=self) with userId=user2 → user1's logs only |
| TC-07 | Pass | user2 (scope=all) with userId=user1 → user1's logs |
| TC-08 | **Pass** (bugfix-2) | user1 (scope=self) GET activity-log/{id} (user2's) → 403 Forbidden |
| TC-09 | Pass | user1 (scope=self) GET own activity-log → 200 |
| TC-10 | Pass | user2 (scope=all) GET user1's activity-log → 200 |
| TC-11 | Pass | user1 (scope=self) GET search-history → own list |
| TC-12 | Pass | user1 (scope=self) GET search-history/{id} (user2's) → 403 |
| TC-13 | Pass | user2 (scope=all) GET search-history → all users' list |

**Commands:**
```bash
cd backend && mvn test
cd frontend && npm test -- --watchAll=false
```

**Outcome:**
- Backend mvn test: pass.
- Frontend: no test files.
- Health: backend 9200 OK, frontend 3001 OK, DB connected.

### Issues found and resolution

- **TC-02, TC-06, TC-08**: Scope enforcement not applied for non-admin with scope=self. **Bugfix-1**: `docs/requirements/20250303-activity-statistics-self-only-scope-bugfix-1.md` — insufficient; re-verification still failed. **Bugfix-2**: `docs/requirements/20250303-activity-statistics-self-only-scope-bugfix-2.md` — AuthService DB-based scope resolution; TC-02, TC-06, TC-08 **Pass** after fix.

### Next steps

1. ~~DB migration for permission_group_screen.scope.~~ Done.
2. ~~Backend/Frontend implementation per §2.~~ Done.
3. ~~Bugfix-1~~ (insufficient). ~~Bugfix-2~~ (scope from AuthService/DB). Done.
4. ~~QA re-verification after bugfix.~~ Done (2026-03-03).

---

**Author**: Requirements subagent
**Date**: 2025-03-03
**Status**: In progress
