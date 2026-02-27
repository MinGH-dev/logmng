# 20250227 - Permission group screen/menu access and general user group

## Handoff note for QA

**User requested push after commit.** Per `docs/workflow/SUBAGENT-DELEGATION.md` §5, QA shall run `git push` after commit when verification passes.

---

## 1. User requirement

### Requirement description

1. **Configurable screen/menu access per permission group**: Permission groups shall be able to select which screens/menus they can access. Each permission group should have configurable screen/menu access (e.g. which menu items or routes are visible/accessible to users in that group). Administrators configure this when creating or editing a permission group.

2. **General user group ("일반 사용자 그룹")**: Add a "일반 사용자 그룹" (general user group) as a sample/default group. This group should exist in init-data or seed data so it can be used for typical users who need basic screen access (e.g. log search, search history, activity log, statistics, pending approvals) without admin-only screens.

3. **Push after verification**: After verification and commit, run `git push` (handoff to QA includes this).

### User scenario

1. An administrator opens the **user permission hierarchy** view (or permission group management flow). When creating or editing a permission group, they see a section **"접근 화면"** (Accessible screens) where they can select which menu items (screens) users in that group can access. The UI shows the menu structure (e.g. 로그 검색 → 검색하기, 검색 이력; 이력·승인 → 활동 이력, 승인 대기; 통계 → 활동로그 통계; 관리 → 사용자 관리, 부서별 결재자, 사용자 권한 계층) with checkboxes. The administrator checks the screens to allow and saves.

2. A user assigned to a permission group (e.g. GENERAL_USER) logs in. The sidebar menu shows only the screens that at least one of their permission groups allows. Screens not in any of their groups' allowed list are hidden. If the user is ADMIN, they see all menus (existing behavior); otherwise, menu visibility is driven by their permission groups' allowed screens.

3. After initial setup or reset (e.g. running init/seed scripts), the system contains the **GENERAL_USER** permission group with default allowed screens (main, search-history, activity-log, statistics, pending-approvals — non-admin screens only). Sample users (e.g. user1, user2) can be assigned to GENERAL_USER so they have typical access without admin privileges.

4. **Problem**: Currently, permission groups only control assignment (who belongs to which group); they do not control which screens/menus are accessible. Menu visibility is driven only by `isAdmin` (admin-only items) vs. all non-admin items. There is no fine-grained screen access per group, and no "일반 사용자 그룹" for typical users.

### Expected outcome

- Each permission group has a configurable set of **allowed screen IDs** (e.g. main, search-history, activity-log, statistics, pending-approvals, user-management, department-approvers, user-permission-hierarchy).
- The **user permission hierarchy** (or group management) UI includes screen/menu selection when creating or editing a permission group. The selection uses a 2-depth structure matching the sidebar menu (1차: 로그 검색, 이력·승인, 통계, 관리; 2차: leaf screens with checkboxes).
- **Menu visibility** for non-admin users is filtered by their permission groups' allowed screens. A screen is visible if the user is ADMIN or if at least one of their groups allows that screen.
- **GENERAL_USER** group exists in init-data with default allowed screens (main, search-history, activity-log, statistics, pending-approvals). Sample users can be assigned to GENERAL_USER.
- **Backend** enforces screen access: APIs that correspond to screen-based access return 403 when the user does not have the required screen in any of their groups (or is not ADMIN).
- API and DB changes are documented in contract/spec and schema; frontend follows existing patterns.

---

## 2. Design

### 2.1 Security review

**Scope**: Permission group screen/menu access configuration; GENERAL_USER sample group.

**Risks**

| Risk | Description | Mitigation |
|------|-------------|------------|
| **API bypass** | If the frontend only hides menus and the backend does not validate screen access, direct API calls could bypass restrictions. | All APIs that correspond to screen-based access must validate that the user has the required screen in at least one of their permission groups (or is ADMIN); otherwise return 403. |
| **Screen metadata exposure** | Group-level allowed screen lists or full screen registry API exposed to non-admins could reveal internal structure. | Screen configuration CRUD APIs are **admin-only**. Users receive only their own `allowedScreenIds` (e.g. in login/me response). |
| **Privilege creep** | Misconfiguration could grant sensitive screens (decrypt, approvers, user management) to groups that should not have them. | Admin UI may highlight sensitive screens; document screen sensitivity in design. |
| **GENERAL_USER over-grant** | If GENERAL_USER default includes admin screens, typical users get excessive access. | GENERAL_USER default allowed screens: **non-sensitive only** (main, search-history, activity-log, statistics, pending-approvals). Document in init-data. |
| **Admin vs screen-based mix** | Mixing admin-only (role=ADMIN) and screen-based access can make validation unclear. | Clear rule: **Admin-only** = role=ADMIN; **Screen-based** = role=ADMIN OR user has a group that allows the screen. |

**Acceptance criteria (security)**

- **Per-screen API validation**: All APIs that enforce screen-based access check that the user has the required screen (or is ADMIN); otherwise 403.
- **Screen config APIs**: Group allowed-screen CRUD is **admin-only** (role=ADMIN). Non-admin calls return 403.
- **User allowed screens**: Login or `GET /api/auth/me` (or equivalent) returns only the caller's `allowedScreenIds`; not other users' or full group config.
- **GENERAL_USER default**: init-data defines GENERAL_USER with non-sensitive screens only.
- **Contract**: Document screen IDs and validation rules in contract/spec.

**Design recommendations**

- **Backend enforcement**: Introduce a shared mechanism (interceptor, filter, or `@PreAuthorize`-like) for screen-based access. Each protected endpoint: `role=ADMIN` OR `user.hasPermissionGroupWithScreen(screenCode)`.
- **Screen–route mapping**: Contract/spec defines screen code ↔ API path mapping. Admin-only screens (user-management, department-approvers, user-permission-hierarchy) may require ADMIN or explicit group assignment.
- **Single source of truth**: Allowed screens stored in DB (`permission_group_screen`). Backend loads at request time (or caches per session).
- **GENERAL_USER default**: Non-sensitive screens only. Admin screens require ADMIN role or separate group (e.g. ADMIN_EXT).

### 2.2 Architecture review (screen/menu access)

**Commonization**

- **Screen ID source**: Screen IDs align with frontend `MENU_TREE` `view` values. Backend maintains an allowed list in contract/spec for validation. `PUT` with invalid `screen_id` returns 400 `INVALID_SCREEN_ID`.
- **Frontend**: `MENU_TREE` remains the source for labels, icons, hierarchy. Allowed visibility is driven by `user.allowedScreenIds` from backend (login/me response).

**Performance**

- **Load timing**: Include `allowedScreenIds` in login response or `GET /api/auth/me`. Frontend stores in user/session state; no per-request fetch for menu filtering.
- **Group–screen mapping change**: Takes effect on next login (or optional "refresh permissions" action).

**Frontend–backend sync**

- Contract/spec defines allowed screen IDs. When adding a new screen: update contract, backend constants, `MENU_TREE`, and optionally init-data.

### Technical design

#### Problem analysis

1. **No screen/menu access per group**: Permission groups only link users to groups; there is no concept of "which screens can this group access". Menu visibility is binary: admin sees all, non-admin sees non-admin items.

2. **No GENERAL_USER group**: init-data has AUDIT, REPORT, ADMIN_EXT but no "일반 사용자" group for typical users who need basic access.

3. **No backend enforcement for screen access**: Even if the frontend hides menus, direct API calls could access protected resources.

#### Solution approach

**Database**

- **New table: `permission_group_screen`**
  - Columns: `permission_group_id` (BIGINT FK → permission_group(id) ON DELETE CASCADE), `screen_id` (VARCHAR(50))
  - PK: (permission_group_id, screen_id)
  - Index on (screen_id) for "which groups can access screen S" queries (optional)
- **init-data**: Insert GENERAL_USER into `permission_group`; insert rows into `permission_group_screen` for GENERAL_USER with screens: main, search-history, activity-log, statistics, pending-approvals. Optionally assign user1, user2 to GENERAL_USER (in addition to existing groups).

**Backend**

- Extend `GET /api/permission-groups` and `GET /api/permission-groups/{id}` response with `allowedScreens: string[]`.
- Extend `PUT /api/permission-groups/{id}` request body with `allowedScreens?: string[]`. Validate each value against allowed screen list; 400 `INVALID_SCREEN_ID` if invalid.
- Extend login or `GET /api/auth/me` response with `allowedScreenIds: string[]` — union of allowed screens from all of the user's permission groups. If user is ADMIN, return all screens (or omit and treat ADMIN as full access).
- Add screen-based access validation for APIs that correspond to screens. Document screen ↔ API mapping in spec.
- New error code: `INVALID_SCREEN_ID` (400).

**Frontend**

- **Group create/edit**: Add "접근 화면" section with 2-depth checkbox tree (1차: 로그 검색, 이력·승인, 통계, 관리; 2차: leaf screens). Use MENU_TREE labels. Save `allowedScreens` with group.
- **Menu filtering**: `AppSidebar` filters `MENU_TREE` by `user.allowedScreenIds`. Show item if: `isAdmin` OR (item has `view` and `view` is in `allowedScreenIds`). Admin-only items: still require `isAdmin`; for non-admin, also require screen in allowed list if screen-based access applies.
- **Route guard**: When navigating to a view, redirect to main (or 403) if user lacks the screen and is not ADMIN.

### § DBA 검토 (permission_group_screen)

- **Table**: `permission_group_screen(permission_group_id, screen_id)` with composite PK.
- **FK**: `permission_group_id` → permission_group(id) ON DELETE CASCADE.
- **screen_id**: VARCHAR(50). Composite unique via PK.
- **Index**: Optional `idx_permission_group_screen_screen` on (screen_id) for reverse lookup.
- **init-data**: GENERAL_USER + permission_group_screen rows; idempotent with ON CONFLICT DO NOTHING.

### § UX review (screen/menu selection)

- **Placement**: "접근 화면" section inside group create/edit dialog, below code/name/description.
- **UI pattern**: 2-depth tree with checkboxes. 1차: menu group headers (로그 검색, 이력·승인, 통계, 관리). 2차: leaf screens with checkboxes (검색하기, 검색 이력, 활동 이력, 승인 대기, 활동로그 통계, 사용자 관리, 부서별 결재자, 사용자 권한 계층).
- **Labels**: Use MENU_TREE Korean labels.
- **Consistency**: Reuse PermissionGroupPanel dialog styles, form rows, buttons; reuse DepartmentApproverManagement tree styling for the checkbox tree.
- **a11y**: `role="checkbox"`, `aria-checked`, `role="group"` for 1차 sections; keyboard operable.

### Change file list

**(Confirmed by Backend subagent after implementation.)**

#### Database

- `backend/src/main/resources/db/schema.sql`
  - Add `permission_group_screen` table. (Already applied per user.)
- `backend/src/main/resources/db/init-data.sql`
  - Insert GENERAL_USER into permission_group; insert permission_group_screen rows for GENERAL_USER; optionally assign user1, user2 to GENERAL_USER. (Already applied per user.)

#### Backend

- `backend/src/main/java/com/logmng/constants/ScreenConstants.java` (new)
  - Allowed screen ID list; validation helpers.
- `backend/src/main/java/com/logmng/controller/PermissionGroupController.java`
  - No controller change; service handles allowedScreens.
- `backend/src/main/java/com/logmng/service/PermissionGroupService.java`
  - Load/save permission_group_screen; validate screen IDs; getAllowedScreenIdsForUser.
- `backend/src/main/java/com/logmng/controller/AuthController.java`
  - Include allowedScreenIds in login response; extend /check with allowedScreenIds; add GET /api/auth/me.
- `backend/src/main/java/com/logmng/config/ScreenAccessInterceptor.java` (new)
  - Validate user has screen (or is ADMIN) for protected endpoints; path↔screen mapping per spec §4.3.
- `backend/src/main/java/com/logmng/config/WebConfig.java`
  - Register ScreenAccessInterceptor.
- `backend/src/main/java/com/logmng/dto/response/PermissionGroupResponse.java`
  - Add allowedScreens.
- `backend/src/main/java/com/logmng/dto/request/PermissionGroupCreateRequest.java`
  - Add allowedScreens.
- `backend/src/main/java/com/logmng/dto/request/PermissionGroupUpdateRequest.java`
  - Add allowedScreens.
- `backend/src/main/java/com/logmng/dto/response/LoginResponse.java`
  - Add allowedScreenIds.
- `backend/src/main/java/com/logmng/service/AuthService.java`
  - Inject PermissionGroupService; resolveAllowedScreenIds; getCurrentUserInfo.

#### Frontend

- `frontend/src/constants/menuTree.js` (new)
  - Shared MENU_TREE, SECOND_ICONS, ALLOWED_SCREEN_IDS.
- `frontend/src/components/PermissionGroupManagement/ScreenSelectionTree.js` (new)
  - 2-depth checkbox tree for allowed screens selection.
- `frontend/src/components/PermissionGroupManagement/ScreenSelectionTree.css` (new)
  - Styles for screen selection tree.
- `frontend/src/components/PermissionGroupManagement/PermissionGroupPanel.js`
  - Add "접근 화면" section with ScreenSelectionTree in create/edit dialog; pass allowedScreens to create/update.
- `frontend/src/components/PermissionGroupManagement/PermissionGroupManagement.css`
  - .permission-group-form-label style.
- `frontend/src/components/AppSidebar.js`
  - Import MENU_TREE from constants; filter menu by allowedScreenIds when not admin.
- `frontend/src/App.js`
  - Pass allowedScreenIds to sidebar; route guard (canAccessView); store allowedScreenIds in user state.
- `frontend/src/utils/security.js`
  - saveMinimalUserData: store allowedScreenIds.

#### Contract / spec

- `specs/permission-group-hierarchy.spec.yaml` (or new spec)
  - Document allowedScreens in API; screen ID list; INVALID_SCREEN_ID.
- `docs/contract.md`
  - Reference new table and screen access behavior.

---

## 3. Test approach

### Test case list (required)

| ID | Type | Scenario (input / condition) | Expected result | Verification |
|----|------|-----------------------------|-----------------|---------------|
| TC-01 | Normal | Admin creates permission group with allowed screens (e.g. main, search-history) | Group saved with allowedScreens; GET returns them | Integration / manual |
| TC-02 | Normal | Admin edits group, changes allowed screens | PUT with allowedScreens; GET returns updated list | Integration / manual |
| TC-03 | Normal | User in GENERAL_USER logs in | Login/me returns allowedScreenIds including main, search-history, activity-log, statistics, pending-approvals | Integration |
| TC-04 | Normal | Non-admin user with only GENERAL_USER opens app | Sidebar shows only GENERAL_USER allowed screens; admin screens hidden | Manual / browser |
| TC-05 | Normal | Non-admin user with group that allows user-management | Sidebar shows 사용자 관리; can navigate | Manual / browser |
| TC-06 | Exception | Non-admin calls API for screen they lack | 403 Forbidden | Integration |
| TC-07 | Exception | PUT permission group with invalid screen_id | 400, code INVALID_SCREEN_ID | Integration |
| TC-08 | Normal | Load init-data; list permission groups | GENERAL_USER exists with default allowed screens | Integration |
| TC-09 | Edge | User in multiple groups (GENERAL_USER + AUDIT) | allowedScreenIds = union of both groups' screens | Integration |

### Test scenarios

#### Scenario 1: Configure screen access per group

1. Log in as admin. Open user permission hierarchy (or group management).
2. Create a new permission group with code "VIEWER", name "조회자".
3. In "접근 화면" section, select main, search-history, activity-log. Save.
4. Confirm GET /api/permission-groups returns the group with allowedScreens.
5. Assign user1 to VIEWER. Log in as user1. Confirm sidebar shows only those screens.

#### Scenario 2: GENERAL_USER and init-data

1. Run schema + init-data from clean state.
2. Confirm GENERAL_USER exists in permission_group.
3. Confirm permission_group_screen has rows for GENERAL_USER with main, search-history, activity-log, statistics, pending-approvals.
4. Assign user2 to GENERAL_USER. Log in as user2. Confirm menu matches GENERAL_USER allowed screens.

#### Scenario 3: API enforcement

1. Log in as non-admin user with only GENERAL_USER (no admin screens).
2. Call GET /api/users (user management API) directly. Expect 403.
3. Call GET /api/permission-groups directly. Expect 403 (admin-only).

### Test data

- Rely on init-data for GENERAL_USER, permission_group_screen, and sample user–group assignments.
- Ensure screen IDs in tests match contract/spec (main, search-history, activity-log, statistics, pending-approvals, user-management, department-approvers, user-permission-hierarchy).

### Test environment

- Frontend: http://localhost:3001 (per contract)
- Backend: http://localhost:9200
- Database: PostgreSQL (per contract)

### 3.5 Browser automation verification (optional)

For frontend-heavy verification, QA may use browser automation:

- **TC-04, TC-05**: Navigate as non-admin; take snapshot; verify sidebar shows/hides correct menus.
- **TC-01, TC-02**: Create/edit group with screen selection; verify dialog and saved state.

Reference: `docs/workflow/BROWSER-AUTOMATION-VERIFICATION-POLICY.md`.

---

## 4. Checklist

### Frontend verification

- [ ] API parameters validated (allowedScreens in create/update)
- [ ] UI behavior confirmed (screen selection in dialog, menu filtering)
- [ ] Error handling verified (INVALID_SCREEN_ID, 403)

### Backend verification

- [ ] API test cases written and run
- [ ] Screen-based access enforcement verified
- [ ] Logs checked (no PII in logs)

### Integration

- [ ] End-to-end flow tested (configure group → assign user → login → menu filtered)
- [ ] Edge cases tested (invalid screen_id, multiple groups union)

### Documentation

- [ ] Requirement doc completed
- [ ] API contract/spec updated
- [ ] Screen ID list documented

---

## 5. Test results

### Test run date

- 2026-02-27

### Scope

- Frontend + Backend + DB

### Health check

| Target | Result | Note |
|--------|--------|------|
| Backend 9200 | 200 OK | JSON health response |
| Frontend 3001 | 200 | Serves app |
| DB connection | connected | data.connected === true |

### §3 test cases (TC-01–TC-09)

| ID | Result | Note |
|----|--------|------|
| TC-01 | Pass | Admin created VIEWER group with allowedScreens; GET returns them |
| TC-02 | Pass | PUT updated allowedScreens; GET returns updated list |
| TC-03 | Pass | user2 (GENERAL_USER) login returns allowedScreenIds: main, search-history, activity-log, statistics, pending-approvals |
| TC-04 | Pass | user2 (GENERAL_USER only) sidebar shows only GENERAL_USER screens; admin section hidden |
| TC-05 | Pass | user2 with USER_MGT_TEST (user-management) — sidebar shows "관리" / "사용자 관리" (verified after bugfix-1) |
| TC-06 | Pass | Non-admin GET /api/users → 403 FORBIDDEN |
| TC-07 | Pass | PUT with invalid screen_id → 400 INVALID_SCREEN_ID |
| TC-08 | Pass | GENERAL_USER exists with main, search-history, activity-log, statistics, pending-approvals |
| TC-09 | Pass | user1 (GENERAL_USER + AUDIT + REPORT) allowedScreenIds = union of groups |

### Browser automation (cursor-ide-browser)

- **Tool**: cursor-ide-browser
- **Base URL**: http://localhost:3001
- **TC-04**: Pass — logged in as user2 (GENERAL_USER only), snapshot showed sidebar: 로그 검색, 이력·승인, 통계 only; no 관리 section.
- **TC-05**: Fail — logged in as user2 (GENERAL_USER + USER_MGT_TEST with user-management). API confirms allowedScreenIds includes user-management. Sidebar still shows only 로그 검색, 이력·승인, 통계; no 관리 section. **Detail**: selector/ref — snapshot `filteredTree`; expected — "관리" section with "사용자 관리" visible; actual — admin section filtered out. Root cause: AppSidebar.js line 28 `if (node.adminOnly && !isAdmin) return false;` excludes admin node for all non-admins without checking allowedScreenIds.

### Issues found and resolution

- **TC-05 Fail**: Bugfix child created: `docs/requirements/20250227-permission-group-screen-menu-access-bugfix-1.md`. **Failure scope**: frontend. Hand off to **Requirements**; Requirements delegates to **Frontend** to fix AppSidebar adminOnly filter logic.

### Bugfix re-verification (2026-02-27)

- **Bugfix doc**: `docs/requirements/20250227-permission-group-screen-menu-access-bugfix-1.md`
- **Tool**: project-0-dev-browser (puppeteer)
- **TC-05 (primary)**: **Pass** — user2 with USER_MGT_TEST → sidebar shows "관리" section with "사용자 관리".
- **TC-04 (regression)**: **Pass** — user1 with GENERAL_USER only → no "관리" section. Both TCs pass; bugfix verified.

---

## 6. Error remedy result — for error/bug fix requirements only

(Not applicable.)

---

## 7. Final version (Korean) — add after all verification is complete

(To be added after QA verification. See `docs/workflow/DOCUMENT-LANGUAGE-POLICY.md` §2.)

---

**Author**: Requirements subagent  
**Date**: 2025-02-27  
**Status**: In progress  
**Related**: docs/requirements/20250227-user-permission-hierarchy-group.md
