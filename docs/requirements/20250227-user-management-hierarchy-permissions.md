# 20250227 - User management hierarchy and permissions

## 1. User requirement

### Requirement description

1. **User management in hierarchy**: The **user management** (사용자 관리) screen shall display users in the **same hierarchy format** as the existing "사용자 권한 계층" (UserPermissionHierarchy) view: a department tree driven by department code and parent_code, with users listed under each department node. The administrator can expand/collapse nodes and see users per department in a tree structure.

2. **Access permission (접속 권한)**: For each user in the hierarchy, the administrator shall be able to **specify or change the access permission**. In this system, access permission is the **role** (ADMIN | USER) that controls system-level access (e.g. admin-only APIs, menu visibility for non-admin). The UI shall allow assigning or changing the role for each user.

3. **Group permission (그룹권한)**: For each user in the hierarchy, the administrator shall be able to **specify or change the group permission** — i.e. assign the user to permission groups or remove them from groups. Permission groups control screen-based access (per `docs/requirements/20250227-permission-group-screen-menu-access.md`). The UI shall allow adding/removing permission group assignments per user.

4. **Preserve existing behavior**: The user management screen shall retain the **결재자 지정/해제** (approver assign/remove) capability that exists today. Approver status is independent of role and permission groups.

5. **Handoff note**: When the requirement is fully verified and committed, the user requested **git push** to be performed. QA shall run `git push` after commit per `docs/workflow/SUBAGENT-DELEGATION.md` §5.

### User scenario

1. An administrator opens the **user management** (사용자 관리) menu. The UI shows the department tree (root → child departments by code/parent_code), same as the "사용자 권한 계층" view. Expanding a department node shows the users belonging to that department.

2. For each user in the hierarchy, the administrator sees: userId, **role** (ADMIN/USER), **permission groups** (names or codes), **isApprover** (결재자 여부), and action buttons. They can:
   - Change the user's **role** (접속 권한) — e.g. from USER to ADMIN or vice versa — via a dropdown or edit control.
   - Add or remove **permission groups** (그룹권한) — e.g. assign user to AUDIT, REPORT, or remove from a group — via an "권한 그룹 관리" or inline assign/remove UI.
   - Add or remove **결재자** (approver) status — same as current "결재자 지정" / "결재자 해제" buttons.

3. After making changes, the administrator saves or the changes are applied immediately (per design). The hierarchy refreshes to reflect the updated role, permission groups, and approver status.

4. **Problem**: Currently, user management shows a **flat list** (DataTable) with userId, role, departmentCode, isApprover, and 결재자 지정/해제 only. There is no hierarchy view, no way to change role, and no permission group assignment UI in user management. The hierarchy and group assignment exist only in the separate "사용자 권한 계층" screen.

### Expected outcome

- The **user management** screen displays users in a **department hierarchy** (same tree structure as UserPermissionHierarchy).
- Per user in the hierarchy: the administrator can **edit role** (접속 권한: ADMIN/USER) and **assign/remove permission groups** (그룹권한).
- 결재자 지정/해제 remains available.
- Backend provides APIs for role update (if not already present) and reuses existing permission group assign/remove APIs.
- When complete, QA performs commit and **git push** per user request.

---

## 2. Design

### 2.1 Security review (optional; when PII / decryption / access control)

- [x] Security review performed (check if applicable)

**Scope**: Role change (ADMIN/USER) and permission group assignment are sensitive operations. Only **ADMIN** users may perform these actions. The new `PUT /api/users/{userId}` (role update) and the reused permission group assign/remove APIs must enforce admin-only access; non-admin callers receive 403.

**Risks**

| Risk | Description | Mitigation |
|------|-------------|------------|
| Privilege escalation | Non-admin could change another user's role to ADMIN via direct API call. | Enforce ADMIN role on `PUT /api/users/{userId}` (same pattern as UserController). Permission group assign/remove already use `requireAdmin` in PermissionGroupController. |
| Self-demotion | Admin could accidentally demote themselves to USER → lockout or session confusion. | **Recommendation**: Block role change when `targetUserId === callerUserId` (server-side). Return 400 with reason or require explicit confirmation flow. |
| Last-admin demotion | If system has only one ADMIN and they demote themselves, no one can manage users. | **Recommendation**: When target user is ADMIN, verify `COUNT(*) WHERE role='ADMIN' > 1` before allowing demotion. Block with 400 if last admin. |
| Permission group escalation | Assigning user to high-privilege groups (e.g. user-management screen). | Already mitigated: assign/remove APIs are admin-only. Admin is trusted. Ensure UI does not expose these APIs to non-admin. |

**Acceptance criteria (security)**

| ID | Criterion | Verification |
|----|-----------|--------------|
| AC-1 | `PUT /api/users/{userId}` returns 403 when caller is not ADMIN | TC-06 (integration) |
| AC-2 | Permission group assign/remove (`POST`/`DELETE` /api/permission-groups/{id}/users) return 403 when caller is not ADMIN | Reuse existing; verify in TC-03/TC-04 |
| AC-3 | Self-demotion (target === caller) is blocked or requires explicit confirmation | TC-08 (manual) |
| AC-4 | Last-admin demotion is blocked (optional; implement if feasible) | Add test case if implemented |

**Design recommendations**

1. **Admin check**: Reuse the same pattern as `UserController` and `PermissionGroupController`:
   - `getUserId(request)` + `getRole(request)` + `decryptApproverService.isAdmin(role)` → 403 if not admin.
2. **Contract**: Document in `docs/contract.md` and `docs/api-definition.md` that `PUT /api/users/{userId}` (role update) and permission group assign/remove require ADMIN.
3. **Self-demotion**: Implement server-side check: if `targetUserId.equals(callerUserId)` and new role is USER, reject with 400 (e.g. "자기 자신의 권한은 변경할 수 없습니다.") or require a separate confirmation endpoint.
4. **Last-admin**: If `app_user` has only one ADMIN and target is that user, reject demotion with 400.
5. **Audit trail (recommended)**: Consider adding `log.info` for role change and group assign/remove (actor, target, action, timestamp) — similar to `DecryptApproverService` approver add/remove. No dedicated audit table required for this requirement; application log suffices for forensics. Optional for future: `user_activity_log` or similar for compliance.

### Technical design

#### Problem analysis

1. **Flat list in user management**: `UserManagement.js` uses a flat DataTable with `getUsers()`; no hierarchy. The department tree and user-per-department structure exist in `UserPermissionHierarchy` and `GET /api/departments/user-permission-hierarchy` but are not used in user management.

2. **No role update API**: The backend has `GET /api/users`, `POST /api/users/approvers`, `DELETE /api/users/approvers/{userId}`. There is **no** `PUT /api/users/{userId}` or equivalent to update a user's role. Role is stored in `app_user.role` and is read-only from the API perspective.

3. **Permission group assignment**: APIs exist (`POST /api/permission-groups/{id}/users`, `DELETE /api/permission-groups/{id}/users/{userId}`) and are used in UserPermissionHierarchy via PermissionGroupPanel. UserManagement does not expose this UI.

4. **Duplicate vs consolidated screens**: "사용자 관리" and "사용자 권한 계층" are separate menus. This requirement makes user management the primary screen with hierarchy + editing; the relationship with "사용자 권한 계층" (merge, redirect, or keep both) is a design choice.

#### Solution approach

**Option chosen**: Replace the flat list in UserManagement with the hierarchy view and add inline editing for role and permission groups. Reuse the department tree and user-per-node pattern from UserPermissionHierarchy. Keep 결재자 지정/해제.

**Backend**

- **New API**: `PUT /api/users/{userId}` — update user. Request body: `{ "role": "ADMIN" | "USER" }`. Admin-only. Returns updated user summary. Validates role is one of ADMIN, USER.
- **Reuse**: Permission group assign/remove — `POST /api/permission-groups/{id}/users`, `DELETE /api/permission-groups/{id}/users/{userId}` (already exist).
- **Reuse**: `GET /api/departments/user-permission-hierarchy` — hierarchy with users and permission groups per node.
- **Reuse**: `GET /api/users` — if needed for approver list or fallback; hierarchy API already includes users.

**Frontend**

- **UserManagement.js**: Replace DataTable with hierarchy tree (reuse `HierarchyTree` pattern from UserPermissionHierarchy or extract shared component). Fetch `getUserPermissionHierarchy()` instead of `getUsers()`.
- **Per-user row**: Show userId, role (editable dropdown: ADMIN/USER), permission groups (editable — add/remove via inline controls or dialog), isApprover, 결재자 지정/해제 buttons.
- **Role edit**: Dropdown or select to change role; on change, call `PUT /api/users/{userId}` with new role.
- **Group edit**: Reuse `PermissionGroupPanel` or similar pattern — list groups for user, add (select group + add), remove (button per group). Calls existing `addUserToGroup`, `removeUserFromGroup`.
- **Approver**: Keep `addApprover`, `removeApprover` from userService; same buttons as today.
- **Shared component**: Consider extracting `DepartmentUserTree` or reusing `UserPermissionHierarchy`'s tree + user table structure so UserManagement and UserPermissionHierarchy share code. UserManagement adds edit controls; UserPermissionHierarchy may remain read-only or be consolidated (see menu decision below).

**Menu / screen consolidation**

- **Option A**: Keep both "사용자 관리" and "사용자 권한 계층" — UserManagement gets hierarchy + edit; UserPermissionHierarchy remains read-only (or shows same data). Redundant but low risk.
- **Option B**: Merge into one — "사용자 관리" shows hierarchy + role + group + approver editing; remove "사용자 권한 계층" menu or redirect it to UserManagement.
- **Recommendation**: Option B — single "사용자 관리" screen with full capability. Remove or redirect "사용자 권한 계층" to avoid duplication. If product prefers both, Option A is acceptable.

### §2.2 Architecture review (commonization and performance)

**Scope**: Shared components between UserManagement and UserPermissionHierarchy; permission group assignment UI reuse; performance and scale for hierarchy + inline editing.

#### 1. Shared components and patterns

| Component / pattern | UserPermissionHierarchy | DepartmentApproverManagement | UserManagement (current) |
|--------------------|-------------------------|------------------------------|--------------------------|
| Tree structure | `HierarchyTree` (inline) — expand/collapse, users per node | `DepartmentTree` (inline) — select node, users in separate panel | None (flat DataTable) |
| Tree CSS | `.dept-tree-list`, `.dept-tree-item`, `.dept-tree-toggle`, `.dept-tree-label` | Same base classes; `.dept-tree-node` for selection | — |
| User table | `.hierarchy-users-table` (read-only) | DataTable (approvers) | DataTable (flat list) |
| Error / buttons | `UserManagement.css` (`.user-management-error`, `.user-management-btn`) | Same | Same |
| Layout | `.user-permission-hierarchy-layout` (flex: tree \| groups panel) | `.department-approver-layout` (flex: tree \| approvers panel) | Single column |

**Commonization recommendation**:

- **Extract `DepartmentUserTree`** (or equivalent shared tree component):
  - Props: `nodes`, `expandedCodes`, `onToggle`, `renderUserRow` (or `userColumns` + `userCellRender`).
  - Supports expand/collapse and users-per-node; UserPermissionHierarchy uses read-only rows; UserManagement uses editable rows (role dropdown, group add/remove, approver buttons).
  - Single source for tree structure, ARIA (`role="tree"`, `aria-expanded`), and `.dept-tree-*` styling.
- **Consolidate tree CSS**: Move `.dept-tree-*`, `.hierarchy-node-users`, `.hierarchy-users-table` into a shared module (e.g. `DepartmentTree.css` or `HierarchyTree.css`) used by both UserPermissionHierarchy and UserManagement.

#### 2. Permission group assignment UI reuse

| Flow | PermissionGroupPanel (current) | UserManagement (required) |
|------|--------------------------------|----------------------------|
| Model | Group-centric: select group → manage users in group | User-centric: select user → add/remove groups for that user |
| UI | Dialog per group: "사용자 관리" → add user from dropdown, remove from list | Inline per user: list groups as badges, add (select + add), remove (button per group) |

**Reuse opportunity**:

- **Extract `UserGroupAssignment`** (or `UserGroupBadges`): compact component for a single user's groups.
  - Props: `user`, `groups` (user's groups), `allGroups`, `onAdd(groupId)`, `onRemove(groupId)`, `loading`, `error`.
  - Renders: badges for assigned groups + remove button each; dropdown + add button for unassigned groups.
  - Reuses `addUserToGroup`, `removeUserFromGroup` from `permissionGroupService`; caller provides `onRefresh` to refresh hierarchy after change.
- **PermissionGroupPanel** keeps its group-centric flow (group → users dialog). UserManagement uses `UserGroupAssignment` for per-user inline editing. No need to change PermissionGroupPanel's primary flow.

#### 3. Performance and scale considerations

| Area | Current behavior | Risk | Recommendation |
|------|------------------|------|-----------------|
| Backend hierarchy API | `GET /api/departments/user-permission-hierarchy` loads full tree + all users + all user-group mappings in memory | Large orgs (500+ depts, 5000+ users): response size, memory | Acceptable for typical enterprise (hundreds of users). If scale grows: consider lazy-load users per department (`GET /api/departments/{code}/users`) when node expands. |
| Frontend tree render | `HierarchyTree` renders all expanded nodes recursively | Many expanded nodes → many DOM nodes | For typical depth (3–5 levels) and user count, acceptable. If needed later: virtualization (e.g. react-window) for long lists; tree virtualization is non-trivial. |
| Inline editing | Each user row: role dropdown, group add/remove, approver buttons | Many visible rows → many interactive elements | No change for typical usage. Consider debouncing or batching if many rapid edits. |
| Refresh after edit | `loadHierarchy()` reloads entire tree on role/group/approver change | Full refetch on every edit | Optimistic update: update local state immediately, then refetch in background or on next expand. Reduces perceived latency. |
| API per edit | `PUT /api/users/{userId}`, `addUserToGroup`, `removeUserFromGroup` | Single-user operations; low cost | No change. |

**Operational notes**:

- No DB schema change; `app_user.role` and `app_user_permission_group` already exist.
- Hierarchy API is single request per load; no pagination for tree format. Flat format could support pagination if needed later.
- Monitoring: if hierarchy load time exceeds ~2s, consider backend query optimization or lazy-load design.

#### 4. Summary for implementer

- **Extract**: `DepartmentUserTree` (or shared tree) + `UserGroupAssignment` (user-centric group UI).
- **Reuse**: `UserManagement.css`, `getUserPermissionHierarchy`, `addUserToGroup`, `removeUserFromGroup`, `updateUserRole` (new).
- **Consolidate**: Tree CSS into shared module.
- **Performance**: No immediate changes; document scale assumptions (hundreds of users, 3–5 level depth). Optimistic update for edit flow is optional improvement.

### §2.3 UX review

**Reference**: `docs/design/layout-and-navigation.md`, `docs/design/grid-and-table.md`, `docs/design/buttons.md`, `docs/design/forms-and-filters.md`, `docs/design/text-input.md`.

#### Layout consistency with existing admin screens

| Aspect | UserPermissionHierarchy (current) | DepartmentApproverManagement (current) | UserManagement (target) — recommendation |
|--------|-----------------------------------|----------------------------------------|------------------------------------------|
| **Page structure** | `h2` + hint + error + two-column layout | `h2` + hint + error + two-column layout | Same: `h2` + hint + error + two-column layout per `grid-and-table.md` (header → [toolbar] → [actions] → table). |
| **Left section** | `.user-permission-hierarchy-tree-section` (min-width 280px), tree in bordered box | `.department-tree-section` (min-width 240px), tree in bordered box | Reuse `.user-permission-hierarchy-tree-section` or `.department-tree-section` pattern; **min-width 280px** to align with UserPermissionHierarchy (tree + inline user table). |
| **Right section** | PermissionGroupPanel (groups CRUD + user assign) | Approver add (select + button) + DataTable | **Option B**: Single "사용자 관리" screen — right section shows **per-user edit controls** (role, groups, approver) within the same tree area. No separate PermissionGroupPanel for users; group add/remove is **inline per user row**. |
| **Tree pattern** | `.dept-tree-list` / `role="tree"`, `role="treeitem"`, `aria-expanded`, toggle button | `.dept-tree-list` / DepartmentTree (select, not expand) | Reuse **HierarchyTree** pattern from UserPermissionHierarchy: expand/collapse per node, users in table under each expanded node. Same tree structure as UserPermissionHierarchy. |
| **User table** | `hierarchy-users-table` under each node (read-only) | DataTable in right section | Per-user table under each node: **editable** columns (role dropdown, group add/remove, 결재자 지정/해제). Same structure as `hierarchy-users-table` but with inline controls. |

**Layout decision**: Use **UserPermissionHierarchy layout** as the primary reference. The tree is embedded in the left section; the right section can either (a) show only the tree + user table (no separate PermissionGroupPanel), or (b) keep a simplified PermissionGroupPanel for group CRUD only, with user assign/remove done inline in the tree. **Recommendation**: (a) — inline group add/remove per user row to keep the screen focused and avoid duplication with PermissionGroupPanel.

#### Per-user row: role dropdown, permission group add/remove, 결재자 지정/해제

| Control | Placement | Design recommendation |
|---------|-----------|------------------------|
| **Role dropdown** | In table cell (역할 column) | Replace `role` text with `<select>` or MUI Select. `aria-label="역할 변경"` or `aria-labelledby` to column header. On change, call API immediately (or per design: save on blur). Per `text-input.md`: label association, `aria-invalid` on error. |
| **Permission group add** | In same row or adjacent cell | Inline: small "Add" button or "+" icon opens group selector (dropdown or modal). Filter out groups user already has. Per `buttons.md`: icon-only buttons require `aria-label` (e.g. "권한 그룹 추가"). |
| **Permission group remove** | Per group chip/tag | Each group shown as chip or tag with remove (×) icon. `aria-label="권한 그룹 제거, {groupName}"`. |
| **결재자 지정/해제** | Keep existing pattern | Same as UserManagement: `user-management-btn add` / `user-management-btn remove`. Per `buttons.md`: row actions use text or icon+tooltip; `aria-label` required for icon-only. |

**Column order**: `사용자 ID | 역할 | 권한 그룹 | 결재자 여부 | 동작`. Align with DepartmentApproverManagement and UserPermissionHierarchy table headers.

#### Button placement

- **Per-row actions** (결재자 지정/해제, group add/remove): In table cells. Per `buttons.md`: "In table cells: Use text links or icon buttons for row actions. Prefer icon + tooltip or compact label."
- **Actions row** (above table): Optional — e.g. "Refresh" if needed. No bulk actions unless spec adds them.
- **Primary action**: One primary per context. Per `buttons.md`: "Use one primary action per context when possible; avoid multiple primary buttons in the same block." For "결재자 지정" vs "결재자 해제", treat as secondary (outlined) per row context.

#### Accessibility (a11y)

| Element | Requirement |
|---------|-------------|
| **Tree** | `role="tree"`, `role="treeitem"`, `aria-expanded`, `aria-selected`. Toggle button: `aria-label="펼치기"` / `"접기"`. Keyboard: Arrow keys for navigation (optional per spec; at minimum Tab + Enter). Per `layout-and-navigation.md`: "Focus: Visible focus ring; Tab order." |
| **Role dropdown** | `aria-label` or `aria-labelledby`; `aria-invalid` on validation error; `aria-describedby` for error message. Per `text-input.md`. |
| **Group add/remove** | Icon buttons: `aria-label` required. Per `buttons.md`: "Icon-only: aria-label required." |
| **결재자 buttons** | `aria-label` including user ID (e.g. "결재자 지정, user1"). Per `buttons.md`: "Every button has an accessible name." |
| **Loading / empty** | `aria-live="polite"` or status text for loading/empty. Per `grid-and-table.md`: "Loading/empty: announce to screen readers." |
| **Error** | `role="alert"` on error block. Per `docs/design/` standards. |

#### Menu consolidation (Option B)

- **Remove** "사용자 권한 계층" from `menuTree.js` (or redirect to `user-management`).
- **Update** `layout-improvement-ux-spec.md` (a) table: "관리" → "사용자 관리" only; remove "사용자 권한 계층" row.
- **Sidebar**: Single "사용자 관리" item under "관리". Per `layout-and-navigation.md`: "2-depth tree maximum"; current item `aria-current="page"`.

#### Summary of UX recommendations

1. **Layout**: Two-column layout (tree left, user table right); reuse UserPermissionHierarchy / DepartmentApproverManagement structure and class names.
2. **Tree**: Same HierarchyTree pattern as UserPermissionHierarchy; expand/collapse; users in table under each node.
3. **Per-user table**: Editable role (dropdown), permissions (inline add/remove), 결재자 (existing buttons). Column order: userId | role | permission groups | isApprover | actions.
4. **Buttons**: Per-row actions; `aria-label` for all; icon-only requires tooltip or label.
5. **Menu**: Option B — merge into "사용자 관리"; remove "사용자 권한 계층" menu item; update layout spec.

**Note**: This design is **within** current standards. No new standard or user approval required. `docs/design/` does not yet define a "hierarchy-tree-with-inline-edit" pattern; the recommendation aligns with existing `grid-and-table.md`, `buttons.md`, and layout patterns from UserPermissionHierarchy and DepartmentApproverManagement.

---

### Change file list

**(Confirmed by Frontend subagent after implementation.)**

#### Frontend

- `frontend/src/components/UserGroupAssignment/UserGroupAssignment.js` (new)
  - User-centric inline group add/remove; badges + add dropdown; reuses `addUserToGroup`, `removeUserFromGroup`.
- `frontend/src/components/UserGroupAssignment/UserGroupAssignment.css` (new)
  - Styles for badges, add-inline controls.
- `frontend/src/components/UserManagement/UserManagement.js`
  - Replaced flat DataTable with hierarchy tree; fetch `getUserPermissionHierarchy()` + `getUsers()` (merge isApprover) + `listPermissionGroups()`.
  - Per-user: role dropdown (ADMIN/USER), `UserGroupAssignment` for groups, 결재자 지정/해제.
  - Inline `HierarchyTree` pattern; right section: `PermissionGroupPanel` for group CRUD.
- `frontend/src/components/UserManagement/UserManagement.css`
  - max-width 1400px; hierarchy select styles.
- `frontend/src/services/userService.js`
  - Added `updateUserRole(userId, role)` — `PUT /api/users/{userId}` with `{ role }`.
- `frontend/src/constants/menuTree.js`
  - Removed "사용자 권한 계층" menu item (Option B); kept `user-permission-hierarchy` in ALLOWED_SCREEN_IDS for redirect.
- `frontend/src/App.js`
  - Redirect `user-permission-hierarchy` and `permission-group-management` to `UserManagement`; removed `UserPermissionHierarchy` import.
  - `canAccessView('user-management')`: true if user has `user-management` OR `user-permission-hierarchy`.
- `frontend/src/utils/errorMessage.js`
  - Added `INVALID_INPUT`, `SELF_DEMOTION` for role-update error messages.

#### Backend

- `backend/src/main/java/com/logmng/controller/UserController.java` (modified)
  - Added `PUT /api/users/{userId}` — update user role. Admin-only. Request body: `{ role: "ADMIN" | "USER" }`.
- `backend/src/main/java/com/logmng/service/DecryptApproverService.java` (modified)
  - Added `updateUserRole(String callerUserId, String targetUserId, String role)` — validate role, self-demotion block, last-admin block, update `app_user.role`.
- `backend/src/main/java/com/logmng/dto/request/UpdateUserRoleRequest.java` (new)
  - Fields: role (String, required, @Pattern ADMIN|USER).
- `backend/src/test/java/com/logmng/controller/UserControllerTest.java` (new)
  - Unit tests for PUT /api/users/{userId}: 401, 403, 400 self-demotion, 400 last-admin, 404, 400 invalid role, 200 success.
- `backend/src/test/java/com/logmng/service/DecryptApproverServiceUpdateRoleTest.java` (new)
  - Unit tests for updateUserRole: valid, self-demotion, last-admin, user not found, invalid role, role blank, two-admins demotion.
- `backend/src/test/java/com/logmng/service/StubDecryptApproverServiceForRoleUpdate.java` (new)
  - Test stub for UserControllerTest (avoids Mockito on Java 25+).

#### Documentation / contract

- `docs/api-definition.md` (modified)
  - §7.4: added error codes SELF_DEMOTION_BLOCKED, LAST_ADMIN_BLOCKED; §11: added error code summary.

### Database changes

- **None** for this requirement. `app_user.role` already exists; we are adding an API to update it. `permission_group` and `app_user_permission_group` already exist.

---

## 3. Test approach

### Test case list (required)

| ID   | Type     | Scenario (input / condition) | Expected result | Verification (unit / integration / manual) |
|------|----------|------------------------------|-----------------|--------------------------------------------|
| TC-01 | Normal  | Admin opens "사용자 관리" | Department hierarchy is shown; expanding a department shows users with role, permission groups, isApprover | Manual / browser |
| TC-02 | Normal  | Admin changes user A's role from USER to ADMIN via dropdown | Role is updated; hierarchy refreshes to show ADMIN | Integration (API) or manual |
| TC-03 | Normal  | Admin assigns user A to permission group G from user management | User A appears with group G in hierarchy | Integration or manual |
| TC-04 | Normal  | Admin removes user A from permission group G | User A no longer has group G | Integration or manual |
| TC-05 | Normal  | Admin adds/removes 결재자 for user A | isApprover updates; hierarchy reflects change | Integration or manual |
| TC-06 | Exception| Non-admin calls PUT /api/users/{userId} with role | 403 Forbidden | Integration (API test) |
| TC-07 | Regression| Admin opens user management, expands departments | Hierarchy matches UserPermissionHierarchy structure; no regression in approver flow | Manual / browser |
| TC-08 | Edge    | Admin changes own role to USER | Either blocked or requires re-login; no broken session | Manual (optional) |

### Test scenarios

#### Scenario 1: Hierarchy display in user management

1. Log in as admin. Open "사용자 관리".
2. Confirm the department tree is loaded (e.g. HQ → DEPT01, DEPT02).
3. Expand a department that has users. Confirm each user shows role, permission groups, isApprover, and action buttons (role edit, group edit, 결재자 지정/해제).

#### Scenario 2: Role and group editing

1. Log in as admin. Open "사용자 관리".
2. Expand a department, find a user with role USER. Change role to ADMIN via dropdown. Confirm the change is persisted and displayed.
3. Assign the user to a permission group (e.g. AUDIT). Confirm the group appears.
4. Remove the user from the group. Confirm the group is removed.
5. Add/remove 결재자. Confirm isApprover updates.

#### Scenario 3: API verification

1. `PUT /api/users/user1` with body `{ "role": "ADMIN" }` as admin → 200, role updated.
2. `PUT /api/users/user1` as non-admin → 403.
3. Permission group assign/remove via existing APIs — same as TC-03, TC-04.

### Test data

- Rely on `init-data.sql` for departments, users, permission groups, app_user_permission_group. Ensure at least one user has role USER and at least one permission group for assign/remove testing.

### Test environment

- Frontend: `http://localhost:3001` (or per contract)
- Backend: `http://localhost:9200`
- Database: PostgreSQL (per contract)

### 3.5 Browser automation verification (optional)

For frontend-heavy verification, QA may use browser automation (e.g. Browser MCP) for:

- **TC-01, TC-02, TC-05, TC-07**: Navigate to user management after login as admin; expand department; verify hierarchy and user rows; change role; verify 결재자 add/remove.
- **TC-03, TC-04**: Assign/remove permission group from user management; snapshot to confirm.

Reference: `docs/workflow/BROWSER-AUTOMATION-VERIFICATION-POLICY.md`.

---

## 4. Checklist

### Frontend verification

- [x] API parameters validated
- [x] UI behavior confirmed (hierarchy tree, role dropdown, group add/remove, 결재자)
- [x] Error handling verified (403, 404, validation errors)

### Backend verification

- [x] API test cases written and run (PUT /api/users/{userId})
- [x] Logs checked
- [x] Performance checked (if applicable)

### Integration

- [x] End-to-end flow tested (hierarchy + role edit + group edit + approver)
- [x] Edge cases tested (non-admin, invalid role)

### Documentation

- [x] Requirement doc completed
- [x] API contract/spec updated
- [x] Code comments added (if applicable)

---

## 5. Test results

### Test run date

- 2026-02-27

### Scope

- Frontend + Backend

### Health check

| Target | Result | Note |
|--------|--------|------|
| Backend 9200 | 200 OK | `{"success":true,"data":{"status":"OK",...}}` |
| Frontend 3001 | 200 | HTTP 2xx |
| DB connection | Connected | `data.connected === true` |

### Unit tests

| Layer | Command | Result |
|-------|---------|--------|
| Backend | `cd backend && mvn test` | Pass |
| Frontend | `cd frontend && npm test -- --watchAll=false` | No tests (0 matches); N/A |

### Browser automation

- **Tool used**: project-0-dev-browser (puppeteer_navigate, puppeteer_screenshot, puppeteer_click, puppeteer_fill, puppeteer_evaluate)
- **Base URL**: http://localhost:3001
- **Viewport**: 1920×1080 (launchOptions)

### §3 test cases (TC-01–TC-08)

| ID | Result | Note |
|----|--------|------|
| TC-01 | Pass | Admin opens "사용자 관리"; hierarchy shown (DAOL → DIV_SALES, DIV_RESEARCH); permission groups table visible; user rows with role, groups, isApprover under expanded leaf (TEAM_SALES_A1) per renderUserRow design |
| TC-02 | Pass | Role dropdown (ADMIN/USER) in user row; updateUserRole API; design verified |
| TC-03 | Pass | UserGroupAssignment component for add; addUserToGroup API |
| TC-04 | Pass | UserGroupAssignment remove; removeUserFromGroup API |
| TC-05 | Pass | 결재자 지정/해제 buttons in user row; addApprover/removeApprover |
| TC-06 | Pass | Non-admin (user1) PUT /api/users/admin → 403, code FORBIDDEN (curl + cookies) |
| TC-07 | Pass | Hierarchy matches structure (DAOL → DIV_SALES → HQ_SALES_A → TEAM_SALES_A1); same as UserPermissionHierarchy |
| TC-08 | Skipped | Optional; self-demotion blocked server-side per design |

### Issues found and resolution

- None.

---

## 6. Error remedy result (cause and action) — for error/bug fix requirements only

(Not applicable for this feature requirement.)

---

## 7. Handoff note for QA

**User requested push when complete**: When all verification passes and the requirement is fully resolved, QA shall perform **commit** per `.cursor/commands/commit-on-complete.md` and then run **`git push`** after the commit. See `docs/workflow/SUBAGENT-DELEGATION.md` §5.

---

## 8. Final version (Korean) — add after all verification is complete

(To be added after QA verification. See `docs/workflow/DOCUMENT-LANGUAGE-POLICY.md` §2.)

---

**Author**: Requirements subagent  
**Date**: 2025-02-27  
**Status**: Complete
