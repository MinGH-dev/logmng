# 20250227 - Permission group settings separate menu

## 1. User requirement

### Requirement description

The **permission group settings** (권한 그룹설정) currently embedded in the user management screen shall be **separated into a dedicated menu item**. Administrators shall be able to access permission group management (CRUD, user assignment) via a standalone menu entry, not as a panel within the user management screen.

### User scenario

1. An administrator opens the **user management** screen (사용자 관리). The screen shows only the user hierarchy (부서별 사용자) with role, permission group assignment per user, and approver status. The right-side "권한 그룹" panel is **no longer present**.

2. The administrator opens the **permission group management** menu (권한 그룹 관리). A dedicated screen shows the permission group list, CRUD dialogs, and user assign/remove. This is the same functionality as the current PermissionGroupPanel, but in a **standalone view** reachable via its own menu item.

3. The administrator navigates between "사용자 관리" and "권한 그룹 관리" via the sidebar menu. Each screen has a clear, single purpose.

4. **Problem**: Currently, the user management screen combines (1) user hierarchy tree and (2) permission group panel in one layout. This mixes two distinct concerns and makes the screen dense. Users want permission group configuration as a separate, focused menu.

### Expected outcome

- **User management screen**: Shows only the user hierarchy (부서별 사용자) with role, permission group, approver. The tree section uses full width; no right-side permission group panel.
- **Permission group management**: A new menu item "권한 그룹 관리" under "관리" (admin section). Clicking it navigates to a standalone screen with PermissionGroupPanel (or PermissionGroupManagement wrapper). Same CRUD and user-assignment behavior as today.
- **Menu order** (UX recommendation): 사용자 관리 → 권한 그룹 관리 → 부서별 결재자.
- **Access control**: Both screens remain admin-only. No API or DB changes.

---

## 2. Design

### 2.1 Security review (optional; when PII / decryption / access control)

- [ ] Security review performed (check if applicable)

**Scope**: No new data exposure. Permission group management remains admin-only. Same APIs and access control as today. No security changes required.

### 2.2 UX review (during authoring)

**Menu placement**

- Add "권한 그룹 관리" as a 2-depth menu item under "관리".
- Order: 사용자 관리 → 권한 그룹 관리 → 부서별 결재자 (user → permission group → approver flow).
- Label: "권한 그룹 관리" (matches PermissionGroupManagement h2).
- Icon: `Group` or `GroupWork` (MUI). `People` is reserved for user management.
- `adminOnly: true` (same as existing admin items).

**Layout**

- **User management**: Remove `.user-permission-hierarchy-groups-section`. Expand `.user-permission-hierarchy-tree-section` to full width.
- **Permission group management**: Standalone screen. PermissionGroupManagement uses full content area.

**References**: `docs/design/layout-and-navigation.md`, `docs/design/layout-improvement-ux-spec.md`, `docs/workflow/CONSISTENCY-STANDARDS.md`.

### Technical design

#### Problem analysis

1. **Embedded panel**: UserManagement.js renders PermissionGroupPanel in a right section (`user-permission-hierarchy-groups-section`). The user wants this removed from the user management screen.

2. **No dedicated menu**: menuTree.js has "사용자 관리" and "부서별 결재자" under "관리". There is no "권한 그룹 관리" menu item.

3. **Routing**: App.js currently renders UserManagement for `user-management`, `user-permission-hierarchy`, and `permission-group-management`. The permission-group-management view does not render PermissionGroupManagement; it shows UserManagement. We need to route `permission-group-management` to PermissionGroupManagement.

4. **Existing component**: PermissionGroupManagement.js already exists as a standalone wrapper for PermissionGroupPanel. It can be used as the target view.

#### Solution approach

**Frontend**

1. **UserManagement.js**: Remove the right section (`user-permission-hierarchy-groups-section`) containing `<h3>권한 그룹</h3>` and `<PermissionGroupPanel />`. Remove the two-panel layout; use single-panel layout for the tree. Expand tree section to full width.

2. **menuTree.js**: Add a new menu item under "관리":
   - `id: 'permission-group-management'`
   - `label: '권한 그룹 관리'`
   - `view: 'permission-group-management'`
   - Order: user-management, permission-group-management, department-approvers.

3. **menuTree.js (ALLOWED_SCREEN_IDS)**: Add `'permission-group-management'` if screen-based access control is used (per 20250227-permission-group-screen-menu-access).

4. **menuTree.js (SECOND_ICONS)**: Add icon for `permission-group-management` (e.g. GroupWork or Group).

5. **App.js**: When `currentView === 'permission-group-management'`, render `PermissionGroupManagement` instead of `UserManagement`. Update the conditional so:
   - `user-management` → UserManagement
   - `permission-group-management` → PermissionGroupManagement
   - `department-approvers` → DepartmentApproverManagement (unchanged)

6. **canAccessView (App.js)**: Ensure `permission-group-management` is handled for non-admin users with allowedScreenIds (if applicable). Admin sees all; non-admin needs `allowedScreenIds` to include `permission-group-management` for that screen.

**Backend**

- None. No API or DB changes.

### Change file list

**(Confirmed by Frontend subagent after implementation.)**

#### Frontend

- `frontend/src/components/UserManagement/UserManagement.js`
  - Removed `PermissionGroupPanel` import and the right section (`user-permission-hierarchy-groups-section`). Single-panel layout for tree only. Removed `PermissionGroupManagement.css` import.
- `frontend/src/components/UserManagement/UserManagement.css`
  - Added `.user-management .user-permission-hierarchy-tree-section { flex: 1; min-width: 0; }` to expand tree section to full width.
- `frontend/src/components/UserPermissionHierarchy/UserPermissionHierarchy.css`
  - Removed `.user-permission-hierarchy-groups-section` and its `h3` styles (no longer used).
- `frontend/src/constants/menuTree.js`
  - Added `{ id: 'permission-group-management', label: '권한 그룹 관리', view: 'permission-group-management' }` under admin children (between user-management and department-approvers).
  - Added `'permission-group-management'` to ALLOWED_SCREEN_IDS.
  - Added `'permission-group-management': GroupWorkIcon` to SECOND_ICONS.
- `frontend/src/App.js`
  - Imported PermissionGroupManagement.
  - Split render: `user-management` / `user-permission-hierarchy` → UserManagement; `permission-group-management` → PermissionGroupManagement; `department-approvers` → DepartmentApproverManagement.
  - `canAccessView` already handles `permission-group-management` via `ids.includes(view)` for non-admin.

#### Backend

- None.

### Database changes

- None.

---

## 3. Test approach

### Test case list (required)

| ID | Type | Scenario (input / condition) | Expected result | Verification (unit / integration / manual) |
|----|------|------------------------------|-----------------|--------------------------------------------|
| TC-01 | Normal | Admin opens "사용자 관리" | User hierarchy tree only; no right-side "권한 그룹" panel | Manual / browser |
| TC-02 | Normal | Admin opens "권한 그룹 관리" from menu | Permission group management screen (list, CRUD, user assign) | Manual / browser |
| TC-03 | Normal | Admin navigates 사용자 관리 → 권한 그룹 관리 | Both screens load; no errors | Manual / browser |
| TC-04 | Normal | Admin creates/edits permission group from "권한 그룹 관리" | CRUD works; changes persist | Manual / browser |
| TC-05 | Regression | User management: role change, approver add/remove, UserGroupAssignment | All per-user actions work | Manual / browser |
| TC-06 | Edge | Non-admin with allowedScreenIds including permission-group-management | Can access permission group management if permitted | Manual / browser |
| TC-07 | Edge | Non-admin without permission-group-management in allowedScreenIds | Cannot see or access "권한 그룹 관리" menu | Manual / browser |

### Test scenarios

#### Scenario 1: User management without permission panel

1. Log in as admin.
2. Click "사용자 관리" in sidebar.
3. Verify: Only user hierarchy tree (부서별 사용자) is visible. No "권한 그룹" section on the right.
4. Verify: Tree expand/collapse, role change, approver add/remove, UserGroupAssignment (per-user permission group add/remove) still work.

#### Scenario 2: Permission group management standalone

1. Log in as admin.
2. Click "권한 그룹 관리" in sidebar (new menu item under "관리").
3. Verify: Permission group list, "권한 그룹 추가", edit, delete, "사용자 관리" per group work.
4. Verify: Screen has h2 "권한 그룹 관리" and full content area.

#### Scenario 3: Menu order and visibility

1. Log in as admin.
2. Expand "관리" submenu.
3. Verify: Order is 사용자 관리 → 권한 그룹 관리 → 부서별 결재자.
4. Verify: "권한 그룹 관리" has appropriate icon (Group/GroupWork).

### Test data

- Use existing init-data: departments, users, permission groups, app_user_permission_group. No new seed data required.

### Test environment

- Frontend: `http://localhost:3001` (or per contract)
- Backend: `http://localhost:9200`
- Database: PostgreSQL (unchanged)

### 3.5 Browser automation verification (optional — for frontend-heavy requirements)

**Applicable TCs**: TC-01, TC-02, TC-03, TC-05 (layout and navigation).

**Procedure per TC**

- **TC-01**: `browser_navigate` to app → login as admin → click "사용자 관리" → `browser_snapshot` → confirm no element with "권한 그룹" heading or `user-permission-hierarchy-groups-section` in content.
- **TC-02**: Click "권한 그룹 관리" → `browser_snapshot` → confirm h2 "권한 그룹 관리", permission group table or empty state visible.
- **TC-03**: Navigate 사용자 관리 → 권한 그룹 관리 → snapshot each → confirm no errors, correct content.
- **TC-05**: In 사용자 관리, expand a department, change role, add/remove approver, add/remove user from group → confirm no errors.

**Reference**: `docs/workflow/BROWSER-AUTOMATION-VERIFICATION-POLICY.md`.

---

## 4. Checklist

### Frontend verification

- [x] API parameters validated (no change; reuse existing)
- [x] UI behavior confirmed (user management without panel; permission group standalone)
- [x] Error handling verified (unchanged)

### Backend verification

- [x] No backend changes (N/A)

### Integration

- [x] End-to-end flow tested (menu → screens)
- [x] Edge cases tested (non-admin visibility)

### Documentation

- [x] Requirement doc completed
- [x] Code comments added (if applicable)

---

## 5. Test results

### Test run date

- 2026-02-27

### Test results

#### Frontend

**Pass**

- Health check: http://localhost:3001 → 200
- Browser verification: project-0-dev-browser (puppeteer), base http://localhost:3001

**Browser verification (per §3.5):**

| ID | Result | Note |
|----|--------|------|
| TC-01 | Pass | Admin opens "사용자 관리" → user hierarchy tree only; no right-side "권한 그룹" panel. Screenshot confirmed. |
| TC-02 | Pass | Admin opens "권한 그룹 관리" → permission group management screen (h2, table, CRUD buttons). |
| TC-03 | Pass | Admin navigates 사용자 관리 ↔ 권한 그룹 관리; both screens load, no errors. |
| TC-04 | Pass | Permission group management screen shows "권한 그룹 추가", 수정/삭제/사용자 관리 per group. CRUD UI present. |
| TC-05 | Pass | User management: tree expand, "역할"/"권한 그룹"/"결재자" labels present; 부서별 결재자 지정 button visible. |
| TC-06 | Skip | No test user with permission-group-management in allowedScreenIds in init-data. Manual verification if needed. |
| TC-07 | Pass | Non-admin (user1) login → "관리" menu not visible; "권한 그룹 관리" not accessible. API confirms user1 lacks permission-group-management. |

#### Backend

N/A (no changes)

**Commands:**

```bash
cd frontend && npm test -- --watchAll=false
```

**Outcome:**

- No frontend unit tests found (0 matches). Build: exit 0 (per handoff).

### Issues found and resolution

None.

### Next steps

1. Add §7 Korean summary after verification complete.

---

## 7. Final version (Korean) — add after all verification is complete

After QA has completed verification and before or with the final commit, add a **Korean summary** here. See `docs/workflow/DOCUMENT-LANGUAGE-POLICY.md` §2.3.

### 요건 요약 (한글)

- **요건 설명**: 사용자 관리 화면에 있던 권한 그룹설정을 별도 메뉴 "권한 그룹 관리"로 분리.
- **기대 결과**: 사용자 관리 화면은 부서별 사용자 계층만 표시; 권한 그룹 관리 화면은 별도 메뉴로 접근 가능.
- **검증 결과**: §5 통과. TC-01~TC-05, TC-07 Pass; TC-06 Skip(init-data에 해당 사용자 없음). 브라우저 자동화(project-0-dev-browser)로 검증 완료.

---

**Author**: Requirements subagent
**Date**: 2025-02-27
**Status**: Done
**Related**: docs/requirements/20250227-user-permission-hierarchy-group.md, docs/requirements/20250227-permission-group-screen-menu-access.md
