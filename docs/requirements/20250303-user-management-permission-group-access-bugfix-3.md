# 20250303-user-management-permission-group-access-bugfix-3 — Controller requireAdmin blocks user-management users

**Parent requirement ID**: `20250303-user-management-permission-group-access`  
**Bugfix sequence**: 3

---

## 1. Discovery

- **When**: During QA re-verification after bugfix-2 (Backend ScreenAccessInterceptor fix)
- **What failed**: user2 with `user-management` in `allowedScreenIds` → GET /api/departments/user-permission-hierarchy returns 403 with "관리자만 부서 및 부서별 결재자를 관리할 수 있습니다." TC-01 still fails.

## 2. Error scope

- **Failure scope**: backend
- **Layer**: backend
- **Symptom**: DepartmentController and PermissionGroupController use `requireAdmin(request)` which checks only `isSystemAdmin`. ScreenAccessInterceptor (fixed in bugfix-2) passes, but controller-level check rejects user-management users.
- **Impact**: Users with `user-management` (without `user-permission-hierarchy`) cannot load hierarchy or permission groups; UserManagement shows API error.

## 3. Cause (estimated)

- **DepartmentController** (`backend/src/main/java/com/logmng/controller/DepartmentController.java`): `userPermissionHierarchy()` and `list()` call `requireAdmin(request)` which uses `decryptApproverService.isAdmin(isSystemAdmin(request))` — allows only `isSystemAdmin=true`.
- **PermissionGroupController** (`backend/src/main/java/com/logmng/controller/PermissionGroupController.java`): All endpoints call `requireAdmin(request)` with same logic.
- Per parent requirement and contract: access to user-management view = `is_system_admin` OR `allowedScreenIds` includes `user-management` OR `user-permission-hierarchy`. Controllers must align with this rule for paths that serve the user-management view.

## 4. Action

### Change list (실제 변경)

| File | Change |
|------|--------|
| `backend/src/main/java/com/logmng/service/AuthService.java` | Added `canAccessUserManagementView(request)` — returns true if `isSystemAdmin` OR `allowedScreenIds` contains `user-management` or `user-permission-hierarchy`. |
| `backend/src/main/java/com/logmng/controller/DepartmentController.java` | Injected AuthService; added `requireUserManagementAccess(request)`; replaced `requireAdmin` with `requireUserManagementAccess` for `userPermissionHierarchy()` and `list()`. |
| `backend/src/main/java/com/logmng/controller/PermissionGroupController.java` | Replaced DecryptApproverService with AuthService; added `requireUserManagementAccess(request)`; replaced `requireAdmin` with `requireUserManagementAccess` for all endpoints (list, create, getOne, update, delete, assignUser, unassignUser, listUsers). |

**Implementation approach**: Add a helper (e.g. `canAccessUserManagementView(request)`) that checks session `isSystemAdmin` OR session `allowedScreenIds` contains `user-management` or `user-permission-hierarchy`. Use for `userPermissionHierarchy` and permission-groups endpoints. Reuse or align with ScreenAccessInterceptor logic.

### Spec/contract alignment

- `specs/permission-group-hierarchy.spec.yaml` §4.3: user-management and user-permission-hierarchy both grant access to user-management view and its APIs (users, hierarchy, permission-groups).

## 5. Verification (§3 Test plan)

- **TC-01 (primary)**: user2 with `user-management` in `allowedScreenIds` → 사용자 관리 → hierarchy and user list visible, no "관리자만 접근할 수 있습니다", no 403 from `/api/departments/user-permission-hierarchy` or `/api/permission-groups`.
- **TC-02, TC-04** (regression): existing scenarios still pass.
- **When all pass**: QA updates §5 in parent doc and commits per commit-on-complete.md.

### §5 Test results (re-verification 2026-03-03)

- **Health check**: Backend 9200: 200 OK. Frontend 3001: 200. DB: connected.
- **TC-01**: **Pass** — user2 (user-management in allowedScreenIds) → 사용자 관리 → no forbidden message; API curl: GET /api/departments/user-permission-hierarchy and GET /api/permission-groups both 200 with data.
- **TC-02**: Expected Pass (admin regression; no code change affecting admin).
- **TC-04**: **Pass** — user2 → sidebar shows 관리 section with 사용자 관리.
- **Tool**: project-0-dev-browser (puppeteer), curl for API verification.

---

## Handoff to Backend

**Task**: Fix DepartmentController and PermissionGroupController so users with `user-management` or `user-permission-hierarchy` in `allowedScreenIds` can call `/api/departments/user-permission-hierarchy` and `/api/permission-groups` when accessing the user-management view. Replace `requireAdmin` (isSystemAdmin-only) with a check that also allows `allowedScreenIds` containing `user-management` or `user-permission-hierarchy`.

**Parent requirement**: `docs/requirements/20250303-user-management-permission-group-access.md`  
**Bugfix doc**: `docs/requirements/20250303-user-management-permission-group-access-bugfix-3.md`

**Failure scope**: backend

**When issue closed** (fix + build/restart done): hand off to **QA** for re-verification. QA re-runs verification; when all pass, QA updates §5 and commits.
