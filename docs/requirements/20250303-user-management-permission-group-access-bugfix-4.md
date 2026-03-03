# 20250303-user-management-permission-group-access-bugfix-4 — UserController still uses isAdmin (blocks user3)

**Parent requirement ID**: `docs/requirements/20250303-user-management-permission-group-access.md`  
**Bugfix sequence**: 4

---

## 1. Discovery

- **When**: During user verification after bugfix-3 (parent requirement marked Resolved)
- **What failed**: user3 (ADMIN_EXT with user-management, user-permission-hierarchy) still sees "관리자만 접근할 수 있습니다" when accessing 사용자 관리 menu.

## 2. Error scope

- **Failure scope**: backend
- **Layer**: backend
- **Symptom**: UserController.listUsers uses `decryptApproverService.isAdmin(isSystemAdmin(request))` — checks only `isSystemAdmin`. Users with `allowedScreenIds` containing user-management/user-permission-hierarchy receive 403 from GET /api/users.
- **Impact**: UserManagement screen calls `getUsers()` (GET /api/users). Any 403 from that API triggers the catch block and displays "관리자만 접근할 수 있습니다." even when hierarchy and permission-groups APIs return 200.

## 3. Cause (estimated)

### Root cause (confirmed by Backend and Frontend subagent analysis)

1. **UserController.listUsers** (`backend/src/main/java/com/logmng/controller/UserController.java` lines 54–56):
   ```java
   if (!decryptApproverService.isAdmin(isSystemAdmin(request))) {
       throw CustomException.forbidden("관리자만 사용자 목록을 조회할 수 있습니다.", "FORBIDDEN");
   }
   ```
   - `isAdmin` checks only `isSystemAdmin`; ignores `allowedScreenIds`.
   - user3 has `isSystemAdmin=false` but `allowedScreenIds` includes user-management, user-permission-hierarchy → 403.

2. **Bugfix-3 scope gap**: bugfix-3 fixed DepartmentController and PermissionGroupController to use `requireUserManagementAccess` (AuthService.canAccessUserManagementView). UserController was **not** included in that fix.

3. **UserManagement load flow**: `loadHierarchy` calls `Promise.all([getUserPermissionHierarchy, getUsers, listPermissionGroups])`. If any returns 403, the catch block sets `error = '관리자만 접근할 수 있습니다.'` (UserManagement.js line 155).

### Secondary checks (if issue persists after fix)

- **DB init-data**: Verify `app_user_permission_group` has (user3, ADMIN_EXT) and `permission_group_screen` has (ADMIN_EXT, user-management), (ADMIN_EXT, user-permission-hierarchy). Run init-data.sql if missing.
- **localStorage**: If user logged in before fix, clear localStorage or re-login to refresh user state.

## 4. Action

### Change list (confirmed)

| File | Change |
|------|--------|
| `backend/src/main/java/com/logmng/controller/UserController.java` | Injected AuthService; added `requireUserManagementAccess(request)`; replaced `decryptApproverService.isAdmin(isSystemAdmin(request))` with `requireUserManagementAccess` for `listUsers()` and `updateUserRole()`. Removed unused `getUserId`, `isSystemAdmin`. Aligned with DepartmentController/PermissionGroupController pattern. |
| `backend/src/test/java/com/logmng/controller/UserControllerTest.java` | Updated to inject AuthService; use StubAuthServiceForUserController for access check. |
| `backend/src/test/java/com/logmng/service/StubAuthServiceForUserController.java` | New stub for AuthService (Mockito cannot mock on Java 25+). |

### Implementation approach

- Add `AuthService` dependency to UserController.
- Add private method `requireUserManagementAccess(HttpServletRequest request)` that throws 403 if `!authService.canAccessUserManagementView(request)`.
- Replace both occurrences of `decryptApproverService.isAdmin(isSystemAdmin(request))` in listUsers and updateUserRole with `requireUserManagementAccess(request)`.
- Keep DecryptApproverService for `listUsers()` data call (decryptApproverService.listUsers) — only the access check changes.

### Spec/contract alignment

- `specs/permission-group-hierarchy.spec.yaml` §4.3: user-management and user-permission-hierarchy both grant access to user-management view and its APIs (users, hierarchy, permission-groups).
- `docs/contract.md` §화면 기반 접근 제어: same rule.

## 5. Verification (§3 Test plan)

- **TC-01 (primary)**: user3 (ADMIN_EXT with user-management, user-permission-hierarchy) → 사용자 관리 → hierarchy, user list, permission groups all visible; no "관리자만 접근할 수 있습니다"; no 403 from GET /api/users, /api/departments/user-permission-hierarchy, /api/permission-groups.
- **TC-02 (regression)**: user1 (is_system_admin=true) → 사용자 관리 → unchanged behavior.
- **TC-03 (regression)**: user2 (GENERAL_USER only, no user-management) → route guard or component blocks; "관리자만 접근할 수 있습니다" or 403 expected.

### §5 Test results

- **Date**: 2026-03-03
- **Health check**: Backend 9200: 200 OK. Frontend 3001: 200. DB: connected.
- **TC-01**: **Pass** — user3 (ADMIN_EXT) login → GET /api/users, /api/departments/user-permission-hierarchy, /api/permission-groups all return 200 with data. No "관리자만 접근할 수 있습니다"; no 403.
- **TC-02**: **Pass** — admin (is_system_admin=true) → GET /api/users returns 200.
- **TC-03**: Skip — no user with GENERAL_USER only in current DB.
- **Note**: Backend required `mvn clean package` and restart; previous jar was stale.

---

## Handoff to Backend

**Task**: Fix UserController so users with `user-management` or `user-permission-hierarchy` in `allowedScreenIds` can call GET /api/users. Replace `decryptApproverService.isAdmin(isSystemAdmin(request))` with `authService.canAccessUserManagementView(request)` for listUsers and updateUserRole.

**Parent requirement**: `docs/requirements/20250303-user-management-permission-group-access.md`  
**Bugfix doc**: `docs/requirements/20250303-user-management-permission-group-access-bugfix-4.md`

**Failure scope**: backend

**When issue closed** (fix + build/restart done): hand off to **QA** for re-verification. QA re-runs verification per parent §3; when all pass, QA updates §5 in parent and this doc, then commits per commit-on-complete.md.
