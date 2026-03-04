# 20250303-user-management-permission-group-access-bugfix-1 — Permission-group user still sees "관리자만 접근할 수 있습니다"

**Parent requirement ID**: `20250303-user-management-permission-group-access`  
**Bugfix sequence**: 1

---

## 1. Discovery

- **When**: During QA verification (browser automation, step 3.5)
- **What failed**: user2 with `user-management` in `allowedScreenIds` (per API `POST /api/auth/login`) navigates to 사용자 관리 but the UserManagement component displays "관리자만 접근할 수 있습니다" instead of the hierarchy and user list.

## 2. Error scope

- **Failure scope**: frontend
- **Layer**: frontend
- **Symptom**: User with permission-group access to user-management still sees forbidden message.
- **Impact**: TC-01 fails. Non-admin users who have `user-management` or `user-permission-hierarchy` in `allowedScreenIds` cannot use the user-management screen despite the fix in UserManagement.js.

## 3. Cause (estimated)

- UserManagement.js has `canAccessUserManagement` logic (lines 121–124) and uses it for `loadHierarchy` and the forbidden early return (lines 126, 205). The code appears correct.
- Possible causes: (a) `user` prop passed to UserManagement does not have the expected `allowedScreenIds` structure at render time; (b) stale data from `getMinimalUserData` / `saveMinimalUserData` overriding fresh login response; (c) timing/initialization issue where `user` is not yet populated when UserManagement first renders.
- API verification: `curl POST /api/auth/login` for user2 returns `allowedScreenIds: ["activity-log","main","pending-approvals","search-history","statistics","user-management"]`. Backend is correct.

## 4. Action

### Investigation

- Investigate why `user.allowedScreenIds` in UserManagement does not include `user-management` at render time despite login API returning it.
- Check: App.js `handleLogin` → `minimalUserData` → `setUser`; LoginForm `onLogin(userData)` receives `result.data?.user` or `result.data`.
- Check: `saveMinimalUserData` / `getMinimalUserData` usage in `checkAuthStatus` — ensure fresh login data is not overwritten by stale localStorage.
- Add defensive logging or verify `user` prop in UserManagement during development.
- If `user` is correct, re-verify `canAccessUserManagement` expression (array check, includes).

### Tentative change list (Frontend confirms or updates after fix)

| File | Change |
|------|--------|
| `frontend/src/utils/security.js` | Add `getAllowedScreenIds(user)` helper to normalize `allowedScreenIds` from camelCase/snake_case; use in `saveMinimalUserData`. |
| `frontend/src/App.js` | Use `getAllowedScreenIds` in `canAccessView`, `handleLogin`, `checkAuthStatus` merge, redirect `useEffect`, and `AppSidebar` prop; fix redirect logic for user-management/user-permission-hierarchy views. |
| `frontend/src/components/LoginForm.js` | Prefer `result.data?.user` for login response; fallback to `result.data` when it has `username` (flat shape). |
| `frontend/src/components/UserManagement/UserManagement.js` | Use `getAllowedScreenIds(user)` for `canAccessUserManagement` (defensive camelCase/snake_case). |

## 5. Verification (re-test plan)

- **Re-run TC-01**: user2 (or user3) with user-management in allowedScreenIds → navigate to 사용자 관리 → hierarchy and user list visible, no "관리자만 접근할 수 있습니다".
- **Tool**: project-0-dev-browser (puppeteer). Base URL: http://localhost:3001.
- **After fix**: restart frontend, login as user2, navigate to 사용자 관리, confirm Pass.
- **Handoff**: When fix + build/restart done → hand off to **QA** for re-verification. QA re-runs verification; when all pass, QA updates §5 in parent doc and commits per commit-on-complete.md.

---

## Handoff to Frontend

**Task**: Fix TC-01 failure — user with `user-management` in allowedScreenIds still sees "관리자만 접근할 수 있습니다" in UserManagement.

**Parent requirement**: `docs/requirements/20250303-user-management-permission-group-access.md`  
**Bugfix doc**: `docs/requirements/20250303-user-management-permission-group-access-bugfix-1.md`

**§5 failure detail (from parent)**:
- TC-01 Fail: user2 with user-management in allowedScreenIds (per API POST /api/auth/login) → "관리자만 접근할 수 있습니다" shown; hierarchy not displayed.
- Expected: no forbidden message, hierarchy visible.
- Actual: hasForbidden=true, hasHierarchy=false.

**When issue closed** (fix + build/restart done): hand off to **QA** for re-verification. QA re-runs verification; when all pass, QA commits.
