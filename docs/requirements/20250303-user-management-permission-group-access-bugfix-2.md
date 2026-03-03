# 20250303-user-management-permission-group-access-bugfix-2 — Backend API 403 for user-management-only users

**Parent requirement ID**: `20250303-user-management-permission-group-access`  
**Bugfix sequence**: 2

---

## 1. Discovery

- **When**: During QA re-verification (browser automation, step 3.5) after bugfix-1
- **What failed**: user2 with `user-management` in `allowedScreenIds` navigates to 사용자 관리. Frontend `canAccessUserManagement` is correct (UI layout and hint render). But API calls fail with 403, causing the error message "관리자만 접근할 수 있습니다" to appear in the error div. TC-01 still fails.

## 2. Error scope

- **Failure scope**: backend
- **Layer**: backend
- **Symptom**: ScreenAccessInterceptor requires `user-permission-hierarchy` for `/api/departments/user-permission-hierarchy` and `/api/permission-groups`, but users with only `user-management` should also access these APIs when using the user-management view.
- **Impact**: Users with `user-management` (but not `user-permission-hierarchy`) in allowedScreenIds cannot load hierarchy or permission groups; UserManagement shows API error.

## 3. Cause (estimated)

- **ScreenAccessInterceptor** (`backend/src/main/java/com/logmng/config/ScreenAccessInterceptor.java`) maps:
  - `/api/departments/user-permission-hierarchy` → required screen `user-permission-hierarchy`
  - `/api/permission-groups/*` → required screen `user-permission-hierarchy`
  - `/api/users/*` → required screen `user-management`
- Per parent requirement and contract: access to user-management view = `is_system_admin` OR `allowedScreenIds` includes `user-management` OR `user-permission-hierarchy`.
- User2 has `user-management` but not `user-permission-hierarchy`. So:
  - GET /api/users → 200 ✓
  - GET /api/departments/user-permission-hierarchy → 403 ✗
  - GET /api/permission-groups → 403 ✗
- UserManagement calls all three in parallel. Hierarchy and permission-groups fail → `setError('관리자만 접근할 수 있습니다.')` (from 403 handler) → user sees error message and empty tree.

## 4. Action

### Change list

| File | Change |
|------|--------|
| `backend/src/main/java/com/logmng/config/ScreenAccessInterceptor.java` | For paths that serve the user-management view (departments/user-permission-hierarchy, permission-groups), accept **either** `user-management` OR `user-permission-hierarchy`. Implement by: (a) adding a helper that checks `allowed.contains(requiredScreen)` for multiple required screens when the path serves user-management, or (b) mapping those paths to both screens and allowing if user has either. |

**Implementation (Backend)**: PathScreenRule extended to support `List<String> screenIds`. Paths `/api/departments/user-permission-hierarchy` and `/api/permission-groups.*` now accept either `user-management` OR `user-permission-hierarchy`. Access check: `requiredScreens.stream().anyMatch(allowed::contains)`.

### Spec/contract alignment

- `specs/permission-group-hierarchy.spec.yaml` §4.3: user-management → /api/users; user-permission-hierarchy → /api/permission-groups, /api/departments/user-permission-hierarchy. The parent requirement 20250303 states both screens grant access to the user-management view. Contract and requirement take precedence: APIs that serve the user-management view should accept either screen.

## 5. Verification (re-test plan)

- **Re-run TC-01**: user2 (user-management only) → 사용자 관리 → hierarchy and user list visible, no "관리자만 접근할 수 있습니다".
- **Re-run TC-02, TC-04** (regression).
- **When all pass**: QA updates §5 in parent doc and commits per commit-on-complete.md.
- **Handoff**: When fix + build/restart done → hand off to **QA** for re-verification.

---

## Handoff to Backend

**Task**: Fix backend ScreenAccessInterceptor so users with `user-management` (without `user-permission-hierarchy`) can call `/api/departments/user-permission-hierarchy` and `/api/permission-groups` when accessing the user-management view.

**Parent requirement**: `docs/requirements/20250303-user-management-permission-group-access.md`  
**Bugfix doc**: `docs/requirements/20250303-user-management-permission-group-access-bugfix-2.md`

**§5 failure detail (from re-verification)**:
- TC-01 Fail: user2 with user-management in allowedScreenIds → navigates to 사용자 관리 → "관리자만 접근할 수 있습니다" shown in error div; hierarchy empty. Root cause: API 403 for GET /api/departments/user-permission-hierarchy and GET /api/permission-groups (backend requires user-permission-hierarchy; user2 has only user-management).
- Expected: no forbidden/error message, hierarchy visible.
- Actual: hasForbidden=true (from API error handler), hasHierarchy=true (layout) but tree empty due to API failure.

**Failure scope**: backend

**When issue closed** (fix + build/restart done): hand off to **QA** for re-verification. QA re-runs verification; when all pass, QA updates §5 and commits.
