# 20250303 - Remove role from user management; single system administrator

## 1. User requirement

### Requirement description

1. **Remove role from user management**: The **role** (ADMIN/USER) concept is no longer needed in user management. The user management screen shall not display or allow editing of user roles. The role column and role selector (dropdown) shall be removed from the user management UI.

2. **Single system administrator**: One admin account shall act as the **system administrator**. This account is identified by `is_system_admin = true` in the database. It is immutable (cannot be modified, deleted, or have its role changed) per `docs/requirements/20250303-permission-group-delete-system-admin-protection.md`.

3. **Admin access = is_system_admin**: Admin-only API and screen access shall be determined by `is_system_admin`, not by `role`. Only users with `is_system_admin = true` may access user management, permission groups, hierarchy, and other admin-only features.

4. **Preserve invariants**: The following invariants from past requirements shall be preserved:
   - At least one system administrator must always exist.
   - System administrators cannot be modified or deleted.
   - Non-admin users access screens based on their permission groups only.
   - Permission group assignment/removal remains admin-only.

### User scenario

1. An administrator (system admin) opens **user management** (사용자 관리). The user list shows: User ID, rank, position, permission groups, approver status. **No role column or role selector** is displayed.

2. The system administrator sees the "시스템 관리자" badge on their own row (or the designated system admin user). Other users do not have a role dropdown; they are managed only via permission groups and approver status.

3. A non-system-admin user logs in. They can access only screens allowed by their permission groups. They cannot access user management or other admin-only features.

4. **Problem**: Currently, user management shows a role column with ADMIN/USER dropdown. Multiple users can have `role = 'ADMIN'`. The new design simplifies this: only one system administrator (`is_system_admin = true`) has full admin access; all others are managed via permission groups.

### Expected outcome

- **User management UI**: No role column, no role dropdown. Columns: User ID, rank, position, permission groups, approver status. System administrator badge shown for the designated admin.
- **API**: `PUT /api/users/{userId}` (role update) returns **410 Gone** with migration notice. `GET /api/users` and hierarchy APIs no longer expose `role` in the response; only `isSystemAdmin` is used.
- **Access control**: Admin-only APIs and screens use `is_system_admin = true` to determine access, not `role = 'ADMIN'`.
- **Session**: Login stores `isSystemAdmin` (boolean) in session; admin checks use this instead of `role`.
- **System administrator protection**: Unchanged — system admin is immutable; minimum one system admin required.

---

## 2. Design

### 2.1 Security review (optional; when PII / decryption / access control)

- [x] Security review performed (check if applicable)

**Scope**: Role removal; admin identification by `is_system_admin` only; access control and lockout prevention.

**Risks**

| Risk | Description | Mitigation |
|------|-------------|------------|
| Access control mismatch | If APIs still use `role='ADMIN'` while UI removes role, `role` and `is_system_admin` inconsistency may cause wrong allow/deny | Unify all admin checks to `is_system_admin` only. Store `isSystemAdmin` in session and use it for all admin decisions |
| Lockout | Single system admin; if password lost or account inaccessible, recovery impossible | Document recovery procedure in `docs/security-guide.md` (e.g. DB direct update) |
| Privilege escalation | User could set `is_system_admin` via API | `is_system_admin` is **never** settable via user APIs. Only init-data, migration, or DB direct edit |
| Session/DB inconsistency | Legacy code or session still references `role` | Migration: (1) Store `isSystemAdmin` in session at login, (2) Replace all `role`-dependent code with `isSystemAdmin`, (3) Optionally keep `role` column in DB but do not use in application logic |

**Acceptance criteria (security)**

- [ ] Admin-only API access: only `is_system_admin == true` users allowed
- [ ] Session: login stores `isSystemAdmin`; all admin checks use it
- [ ] Screen bypass: `is_system_admin == true` → full screen access (same as current ADMIN)
- [ ] No privilege escalation: user APIs cannot set or change `is_system_admin`
- [ ] Lockout prevention: at least one system admin; last system admin demotion/deletion → 400
- [ ] Immutable system admin: system admin cannot be modified or deleted (existing requirement)
- [ ] Audit: log system admin modification/deletion attempts (actor, target, action, result)

**Design recommendations**

1. **Single admin criterion**: Use `is_system_admin` as the sole admin identifier.
2. **Session structure**: Store `isSystemAdmin` (boolean) instead of `role`. Add `isSystemAdmin` to `LoginResponse`; deprecate or remove `role`.
3. **API changes**: Replace `getRole(request)` with `isSystemAdmin(request)` or `getCurrentUserInfo().isSystemAdmin()`.
4. **DB**: Option A — keep `role` column for backward compatibility but do not use in application logic. Option B — remove `role` column; use only `is_system_admin` (long-term simplification).
5. **Recovery procedure**: Document system admin account recovery in `docs/security-guide.md`.

### Technical design

#### Problem analysis

1. **Role in user management**: `UserManagement.js` shows a role column with ADMIN/USER dropdown. `HierarchyTree` passes `onRoleChange` and renders role per user. `updateUserRole` in `userService.js` calls `PUT /api/users/{userId}`.

2. **Admin check uses role**: `UserController`, `PermissionGroupController`, `DepartmentController`, `SearchHistoryController` use `getRole(request)` and `decryptApproverService.isAdmin(role)`. `AuthService` loads `role` from DB and stores it in session. `ScreenAccessInterceptor` checks `"ADMIN".equals(userInfo.getRole())`.

3. **API and DTOs**: `UserListItemResponse`, `UserPermissionSummary`, `LoginResponse` include `role`. `GET /api/users` and hierarchy APIs return `role` in responses.

4. **PUT /api/users/{userId}**: Updates `app_user.role`. With role removal, this endpoint's purpose disappears.

#### Solution approach

**Frontend**

- **UserManagement.js**: Remove role column from table header and `renderUserRow`. Remove `onRoleChange` prop and `updateUserRole` calls. Remove role dropdown and role-related `aria-label`. Keep system administrator badge for `isSystemAdmin` users.
- **HierarchyTree**: Remove `onRoleChange` prop; remove "역할" column from table.
- **userService.js**: Remove or deprecate `updateUserRole`. If frontend still has dead code calling it, remove those calls.
- **Permission checks**: Replace `user?.role === 'ADMIN'` with `user?.isSystemAdmin === true` where applicable.

**Backend**

- **PUT /api/users/{userId}**: Return **410 Gone** with body `{ "success": false, "error": "...", "code": "ENDPOINT_REMOVED" }` and message indicating role update is deprecated.
- **UserController.getRole**: Replace with `isSystemAdmin(request)` — read `isSystemAdmin` from session (set at login).
- **DecryptApproverService**: Add `isAdminBySystemAdmin(boolean isSystemAdmin)` or change `isAdmin(String role)` to accept `isSystemAdmin`; all callers pass `isSystemAdmin` from session. `updateUserRole` can remain but `UserController` no longer calls it (410 instead).
- **AuthService**: On login, load `is_system_admin` from `app_user`; set `session.setAttribute("isSystemAdmin", Boolean)`. Set `LoginResponse.setIsSystemAdmin(...)`. Optionally keep `role` in session for gradual migration but prefer `isSystemAdmin`.
- **ScreenAccessInterceptor**: Replace `"ADMIN".equals(userInfo.getRole())` with `userInfo.isSystemAdmin()`.
- **All controllers** (UserController, PermissionGroupController, DepartmentController, SearchHistoryController): Use `isSystemAdmin(request)` and `decryptApproverService.isAdminBySystemAdmin(isSystemAdmin)` (or equivalent).
- **SearchHistoryService.listPending**: Accept `isSystemAdmin` instead of `role` for admin check.

**API response changes**

- **GET /api/users**: Remove `role` from `UserListItemResponse` in API response (or keep in DTO for internal use but exclude from JSON). Contract: do not expose `role`.
- **Hierarchy APIs**: Remove `role` from `UserPermissionSummary` in response. Expose only `isSystemAdmin`.
- **GET /api/auth/me**: Return `isSystemAdmin` instead of (or in addition to) `role`. Frontend uses `isSystemAdmin` for admin checks.

**Database (DBA recommendation)**

- **Option A (short-term, safer)**: Keep `role` column. Application logic uses only `is_system_admin`. `role` remains for backward compatibility; no schema change.
- **Option B (long-term)**: Remove `role` column. Migration: `ALTER TABLE app_user DROP CONSTRAINT chk_app_user_role; ALTER TABLE app_user DROP COLUMN role;` after all code uses `is_system_admin` only. Update init-data.
- **Migration**: Ensure `is_system_admin = true` for at least one user. If `role='ADMIN'` users exist with `is_system_admin = false`, consider `UPDATE app_user SET is_system_admin = true WHERE username = 'admin'` (or designated user).

**Contract / Spec**

- `docs/contract.md`: Change "role=ADMIN" → "is_system_admin = true" for screen access. Document PUT /api/users/{userId} as 410 Gone.
- `docs/api-definition.md`: §7.1 GET /api/users — remove `role` from response; §7.2 PUT — 410 Gone; §2.1 login — `isSystemAdmin`; §6.1.5–6.1.7, §12 — admin = `is_system_admin = true`.
- `specs/user-management.spec.yaml`: Remove role from GET response; document PUT as 410 Gone.
- `specs/permission-group-hierarchy.spec.yaml`: Admin = `is_system_admin = true`; remove role from hierarchy response.

### Change file list

**(Backend: confirmed by Backend subagent. Frontend: to be confirmed by Frontend subagent.)**

#### Frontend

- `frontend/src/components/UserManagement/UserManagement.js`
  - Removed role column from table header; removed role dropdown and `onRoleChange` from `renderUserRow`; removed `updateUserRole` import and `handleRoleChange`; kept system admin badge in User ID cell; replaced `user?.role === 'ADMIN'` with `user?.isSystemAdmin === true`
- `frontend/src/components/UserManagement/UserManagement.css`
  - Removed role dropdown select styles; kept `.system-admin-badge`
- `frontend/src/components/UserPermissionHierarchy/UserPermissionHierarchy.js`
  - Removed "역할" column from HierarchyTree table; added system admin badge in User ID cell; replaced `user?.role === 'ADMIN'` with `user?.isSystemAdmin === true`
- `frontend/src/components/PermissionGroupManagement/PermissionGroupPanel.js`
  - Removed "역할" column from users-in-group dialog table; replaced `user?.role === 'ADMIN'` with `user?.isSystemAdmin === true`
- `frontend/src/components/PermissionGroupManagement/PermissionGroupManagement.js`
  - Replaced `user?.role === 'ADMIN'` with `user?.isSystemAdmin === true`
- `frontend/src/services/userService.js`
  - Removed `updateUserRole` export; updated JSDoc
- `frontend/src/App.js`
  - Replaced `role === 'ADMIN'` with `isSystemAdmin` for `canAccessView`, `handleLogin` minimalUserData, auth check merge, and `AppSidebar` isAdmin prop
- `frontend/src/utils/security.js`
  - `saveMinimalUserData`: store `isSystemAdmin` instead of `role`

#### Backend

- `backend/src/main/java/com/logmng/controller/UserController.java`
  - `PUT /api/users/{userId}`: return 410 Gone; add `isSystemAdmin(request)`; replace `getRole` with `isSystemAdmin` for GET
- `backend/src/main/java/com/logmng/controller/AuthController.java`
  - Session: store `isSystemAdmin`; GET /api/auth/me: return `isSystemAdmin`
- `backend/src/main/java/com/logmng/service/AuthService.java`
  - Load `is_system_admin` from DB on login; set in `LoginResponse`; session stores `isSystemAdmin`
- `backend/src/main/java/com/logmng/service/DecryptApproverService.java`
  - Add `isAdmin(boolean isSystemAdmin)` overload; `listUsers`, `getUserSummary`: optionally stop populating `role` in DTO (or keep for internal use)
- `backend/src/main/java/com/logmng/controller/PermissionGroupController.java`
  - Replace `getRole` + `isAdmin(role)` with `isSystemAdmin` + `isAdmin(isSystemAdmin)`
- `backend/src/main/java/com/logmng/controller/DepartmentController.java`
  - Same as PermissionGroupController
- `backend/src/main/java/com/logmng/controller/SearchHistoryController.java`
  - Same; pass `isSystemAdmin` to `listPending`
- `backend/src/main/java/com/logmng/service/SearchHistoryService.java`
  - `listPending`: accept `boolean isSystemAdmin` instead of `String role`
- `backend/src/main/java/com/logmng/config/ScreenAccessInterceptor.java`
  - Replace `userInfo.getRole() == "ADMIN"` with `userInfo.isSystemAdmin()`
- `backend/src/main/java/com/logmng/dto/response/LoginResponse.java`
  - Add `isSystemAdmin`; deprecate or remove `role` from response
- `backend/src/main/java/com/logmng/dto/response/UserListItemResponse.java`
  - Remove `role` from JSON serialization (or keep for internal use, exclude with `@JsonIgnore`) per contract
- `backend/src/main/java/com/logmng/dto/response/UserPermissionSummary.java`
  - Same as UserListItemResponse
- `backend/src/main/java/com/logmng/dto/request/UpdateUserRoleRequest.java`
  - No longer used (PUT returns 410); can deprecate or remove
- `backend/src/test/java/com/logmng/controller/UserControllerTest.java` — update for 410, isSystemAdmin; remove role-update success tests; add listUsers tests
- `backend/src/test/java/com/logmng/service/StubDecryptApproverService.java` — add isAdmin(boolean) override
- `backend/src/test/java/com/logmng/service/StubDecryptApproverServiceForRoleUpdate.java` — add isAdmin(boolean), listUsers override

#### Contract / Spec

- `docs/contract.md`
  - Screen access: `role=ADMIN` → `is_system_admin = true`; PUT /api/users/{userId} 410 Gone
- `docs/api-definition.md`
  - §7.1, §7.2, §2.1, §6.1.5–6.1.7, §12 — admin = is_system_admin; PUT 410
- `specs/user-management.spec.yaml`
  - GET response: remove role; PUT: 410 Gone
- `specs/permission-group-hierarchy.spec.yaml`
  - Admin = is_system_admin; hierarchy response: remove role

### Database changes

- **Option A**: No schema change. Keep `role` column; application does not use it for access control.
- **Option B**: Remove `role` column and `chk_app_user_role` constraint. Migration script: backfill `is_system_admin` for designated admin; then `ALTER TABLE app_user DROP CONSTRAINT chk_app_user_role; ALTER TABLE app_user DROP COLUMN role;`
- **Init-data**: Ensure one user has `is_system_admin = true` (e.g. `admin`).

---

## 3. Test approach

### Test case list (required)

| ID | Type | Scenario (input / condition) | Expected result | Verification |
|----|------|------------------------------|-----------------|--------------|
| TC-01 | Normal | System admin opens user management | User list shown **without** role column; system admin badge visible for admin user | Manual / browser |
| TC-02 | Normal | Non-admin user opens user management | 403 or redirect; "관리자만 접근 가능" message | Manual / browser |
| TC-03 | Exception | Call PUT /api/users/{userId} with role | 410 Gone, `code: "ENDPOINT_REMOVED"` | Integration (curl) |
| TC-04 | Normal | GET /api/users as system admin | 200 OK; response does **not** include `role` field (or contract-compliant) | Integration |
| TC-05 | Normal | GET /api/auth/me as system admin | Response includes `isSystemAdmin: true` | Integration |
| TC-06 | Normal | Login as system admin; access admin-only API | 200 OK | Integration |
| TC-07 | Exception | Login as non-admin; access admin-only API (e.g. GET /api/users) | 403 Forbidden | Integration |
| TC-08 | Normal | User management: permission groups, approver, rank, position | All editable; no role column | Manual / browser |
| TC-09 | Regression | System admin badge on designated user | Badge visible; no role dropdown | Manual / browser |
| TC-10 | Edge | Hierarchy API response | No `role` in user objects; `isSystemAdmin` present | Integration |

### Test scenarios

#### Scenario 1: Admin-only access

1. Log in as system admin (`is_system_admin = true`).
2. Open user management. Verify user list loads; no role column.
3. Call `GET /api/users`. Verify 200; no `role` in response (or per contract).
4. Log in as non-admin. Try to open user management. Verify 403 or redirect.
5. Call `GET /api/users` as non-admin. Verify 403.

#### Scenario 2: User list without role

1. Log in as system admin. Open user management.
2. Expand a department. Verify table columns: User ID, rank, position, permission groups, approver status. **No "역할" column.**
3. Verify system admin user has "시스템 관리자" badge.
4. Verify no role dropdown on any row.

#### Scenario 3: PUT /api/users/{userId} deprecated

1. Call `PUT /api/users/user1` with body `{ "role": "ADMIN" }` as system admin.
2. Verify 410 Gone; response includes `ENDPOINT_REMOVED` or similar.
3. Verify user's data unchanged.

#### Scenario 4: Session and screen access

1. Log in as system admin. Check `GET /api/auth/me`. Verify `isSystemAdmin: true`.
2. Access admin-only screens (user management, permission hierarchy). Verify access allowed.
3. Log in as non-admin. Verify `isSystemAdmin: false`. Verify admin screens return 403 or redirect.

### Test data

- System admin user (e.g. `admin` with `is_system_admin = true`).
- Non-admin user (e.g. `user1` with `is_system_admin = false`).
- Departments and permission groups per init-data.

### Test environment

- Frontend: `http://localhost:3001` (per contract)
- Backend: `http://localhost:9200`
- Database: PostgreSQL per `docs/contract.md`

### 3.5 Browser automation verification (optional)

**Applicable TCs**: TC-01, TC-02, TC-08, TC-09

**Procedure per TC** (brief):

- TC-01: Navigate → login as system admin → user management → snapshot to confirm no role column, system admin badge visible.
- TC-02: Login as non-admin → try user management → snapshot to confirm 403 or redirect.
- TC-08: Expand department → verify columns (no role) → verify permission groups and approver editable.
- TC-09: Locate system admin row → snapshot to confirm badge, no role dropdown.

**Reference**: `docs/workflow/BROWSER-AUTOMATION-VERIFICATION-POLICY.md`

---

## 4. Checklist

### Frontend verification

- [x] Role column and dropdown removed from user management
- [x] System admin badge visible
- [x] Admin checks use `isSystemAdmin` instead of `role`
- [x] No calls to `updateUserRole`

### Backend verification

- [x] PUT /api/users/{userId} returns 410 Gone
- [x] All admin checks use `is_system_admin` / `isSystemAdmin`
- [x] GET /api/users and hierarchy do not expose `role` (per contract)
- [x] Login and /api/auth/me return `isSystemAdmin`
- [x] Unit tests updated; 410 tests added

### Integration

- [x] End-to-end: system admin access, non-admin 403
- [x] Session stores `isSystemAdmin`; screen access works

### Documentation

- [x] Requirement doc completed
- [x] Contract and api-definition updated
- [x] Specs updated

---

## 5. Test results

### Test run date

- 2025-03-03 (QA verification)
- 2025-03-03 (Re-verification after bugfix-1)

### Health check

- Backend 9200: 200 OK
- Frontend 3001: 200 OK
- DB: connected

### Integration tests (curl)

| ID | Result | Note |
|----|--------|------|
| TC-03 | Pass | PUT /api/users/user1 → 410 Gone, `code: "ENDPOINT_REMOVED"` |
| TC-04 | Pass | GET /api/users response excludes `role`; includes `isSystemAdmin` |
| TC-05 | Pass | GET /api/auth/me includes `isSystemAdmin: true` |
| TC-06 | Pass | Admin access GET /api/users → 200 |
| TC-07 | Pass | Non-admin (user2) GET /api/users → 403 |
| TC-10 | Pass | Hierarchy API user objects exclude `role`; include `isSystemAdmin` |

### Browser tests (puppeteer MCP, base http://localhost:3001)

| ID | Result | Note |
|----|--------|------|
| TC-01 | Pass | System admin sees user list; no "관리자만 접근할 수 있습니다" |
| TC-02 | Pass | Non-admin (user2) sees "관리자만 접근할 수 있습니다" |
| TC-08 | Pass | Columns: ID, rank, position, permission groups, approver; no role column |
| TC-09 | Pass | No role dropdown; system admin badge (admin not in dept tree) |

### Issues found and resolution

- **Bugfix child created**: `docs/requirements/20250303-remove-role-single-admin-bugfix-1.md`
- **Failure scope**: backend
- **Resolution**: Backend implemented isSystemAdmin in login/me; PUT 410; removed role from UserListItemResponse, UserPermissionSummary. Re-verification: all TC-01–TC-10 pass.

---

## 6. Error remedy result (cause and action) — for error/bug fix requirements only

(Not applicable for this feature requirement.)

---

## 7. Final version (Korean) — add after all verification is complete

(To be added after QA verification. See `docs/workflow/DOCUMENT-LANGUAGE-POLICY.md` §2.)

---

**Author**: Requirements subagent (with RequirementsPastSearch, Security, Contract, DBA feedback)
**Date**: 2025-03-03
**Status**: Complete
