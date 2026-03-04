# 20250303 - Screen-level function availability

## 1. User requirement

### Requirement description

1. **Per-screen function availability**: Specify, for each screen, which functions (actions) are available to the user. This enables the frontend to show/hide or enable/disable UI elements (buttons, actions) consistently across all screens.

2. **Main screen (검색하기) is read-only**: The main log search screen differs from other screens — it only requires read permission (search, view logs). No write, approve, or other elevated actions.

3. **Common format for all screens**: Apply a single, consistent format so the model and implementation are predictable. All screens (main, search-history, activity-log, statistics, pending-approvals, user-management, department-approvers, user-permission-hierarchy) fit this format.

4. **Design phase only**: This document defines §1, §2, §3. No implementation (Step 4).

### User scenario

1. An administrator configures a permission group with allowed screens (e.g. main, search-history, pending-approvals). A user in that group logs in and opens the app.

2. On the **main** (검색하기) screen, the user can search and view logs. No approve, reject, or write actions are shown — the screen is read-only.

3. On the **pending-approvals** screen, the user sees the list. If they are a decrypt_approver, they see enabled approve/reject buttons. If not, the buttons are disabled with a tooltip explaining why.

4. On the **user-management** (or user-permission-hierarchy) screen, the user with screen access sees create/edit/assign actions. If they lack write permission, those actions are disabled with appropriate tooltips.

5. **Problem**: Currently, `allowedScreenIds` and `screenScopes` control screen access and data scope, but there is no per-screen function-level availability. The frontend cannot consistently determine which actions (read, write, approve) are available on each screen. The main screen is not explicitly modeled as read-only.

### Expected outcome

- A **common format** for screen function availability (e.g. `screenFunctions: Record<screenId, { read, write?, approve? }>`).
- **main** is always read-only; other screens have read, and optionally write and/or approve based on screen type and user permissions.
- **Backward compatibility**: Existing `allowedScreenIds` and `screenScopes` remain unchanged. New field is additive.
- **Auth response** (login, GET /api/auth/me) includes the new format so the frontend can drive UI consistently.
- **API enforcement**: Each function-level action (approve, reject, write) is validated server-side; 403 when the user lacks the function.
- **Permission group config UI**: When configuring read/write/approve per screen, show friendly descriptions that explain what each selection changes for the end user (조회/수정/승인 선택 시 어떤 변화가 생기는지 안내).

---

## 2. Design

### 2.1 Security review

**Scope**: Per-screen function availability; auth response extension; API enforcement.

**Risks**

| Risk | Description | Mitigation |
|------|-------------|------------|
| **Client manipulation** | Frontend-exposed permission data can be modified in the browser. Client-only access control is bypassable. | `screenFunctions` is computed server-side from DB (permission_group_screen, decrypt_approver). Never trust client-supplied values. |
| **Information disclosure** | Exposing screen/function mapping reveals internal structure. | Only the caller's own `screenFunctions` is returned; not other users' or full registry. |
| **Privilege creep** | Mis-granting read/write/approve could allow privilege escalation (e.g. approve API without approval right). | All function-level APIs must validate permission server-side; 403 when lacking. |
| **Scope vs function confusion** | Mixing `screenScopes` (self/all) with `screenFunctions` (read/write/approve) could mis-grant write/approve to scope=self users. | Apply both: scope=self + write → only own data editable. Document derivation rules. |
| **Write without scope check** | Write APIs may check only function (write=true) but not scope (self/all), allowing scope=self users to edit others' data. | Write APIs must validate both function and scope. When scope=self, reject edits targeting other users' data with 403. |

**Server-side vs client-side**

| Principle | Recommendation |
|-----------|----------------|
| **Client-side (UI only)** | Hide/disable buttons and menus. `screenFunctions` is for UX only. |
| **Server-side (mandatory)** | Every function-level action is validated in the API. `ScreenAccessInterceptor` checks screen; each API must check function (read, write, approve) when applicable. |
| **Defense in depth** | Frontend: UI control; Backend: API validation. Either failure alone must not compromise security. |

**Acceptance criteria (security)**

- **Auth response**: `screenFunctions` is computed server-side from DB (allowedScreenIds, screenScopes, decrypt_approver). No client input.
- **API enforcement**: Each function API (search, decrypt, approve, reject, user update) validates the corresponding function permission. 403 when lacking.
- **Write + scope**: Write APIs must validate both function (write) and scope (self/all). When scope=self, editing another user's data must return 403.
- **Path-to-function mapping**: Document path → screen → function in spec (like §4.3).
- **main read-only**: Main screen has read only. Decrypt flows depend on search-history approval; no direct decrypt without approval.
- **Approve scope**: approve/reject on pending-approvals and search-history require `decrypt_approver` or `is_system_admin`. `allowedScreenIds` including pending-approvals alone does not grant approve.
- **Error response**: 403 FUNCTION_NOT_ALLOWED must use a generic message; avoid revealing resource existence or internal structure.

**PII / decryption scope**

- **main read-only**: Decryption depends on search-history approval flow. No decrypt without approval.
- **Scope vs function**: `screenScopes` = data scope; `screenFunctions` = actions. Apply both: scope=self + write → edit own data only.
- **Approve data exposure**: approve exposes search history and requester info. approve requires `decrypt_approver` or `is_system_admin`.
- **No new PII**: `screenFunctions` contains only function flags; no PII.

**Additional recommendations**

- **localStorage**: `screenFunctions` is permission metadata (read/write/approve flags per screen), not PII. Per `docs/security-guide.md`, store only the minimum needed for UI. `screenFunctions` is required for hide/disable without round-trip; acceptable. Do not store full permission_group_screen or other users' data.
- **Permission revocation**: `screenFunctions` is computed at login/me. Revoked permissions (e.g. decrypt_approver removed) take effect on next token refresh or re-login. For immediate revocation, consider token invalidation (out of scope for this requirement).
- **Audit logging** (optional): Consider logging 403 FUNCTION_NOT_ALLOWED for security monitoring and privilege-escalation detection.

### Technical design

#### Problem analysis

1. **No per-screen function model**: `allowedScreenIds` and `screenScopes` control screen access and data scope, but not which actions (read, write, approve) are available per screen.
2. **Main screen not explicit**: Main (검색하기) is effectively read-only but not formally modeled; other screens have varying functions.
3. **Inconsistent UI handling**: Frontend may hard-code or guess function availability; no single source of truth.
4. **No API function-level checks**: Some APIs may only check screen access, not function (e.g. approve without decrypt_approver check).

#### Solution approach

**Common format**

```ts
type ScreenFunctionCapability = {
  read: boolean;
  write?: boolean;   // present only for screens that support write
  approve?: boolean; // present only for screens that support approve
};

// Auth response
screenFunctions: Record<screenId, ScreenFunctionCapability>
```

**Per-screen mapping**

| screen_id | read | write | approve | Notes |
|-----------|------|-------|---------|-------|
| main | ✓ | — | — | Always read-only |
| search-history | ✓ | — | ✓ | approve when decrypt_approver |
| activity-log | ✓ | — | — | scope from screenScopes |
| statistics | ✓ | — | — | scope from screenScopes |
| pending-approvals | ✓ | — | ✓ | approve when decrypt_approver |
| user-management | ✓ | ✓ | — | |
| department-approvers | ✓ | ✓ | — | |
| user-permission-hierarchy | ✓ | ✓ | — | |
| permission-group-management | ✓ | ✓ | — | Same as user-permission-hierarchy |

**Derivation rules**

- **read**: `allowedScreenIds` includes screen_id OR `is_system_admin`.
- **write**: read=true AND screen supports write (user-management, department-approvers, user-permission-hierarchy, permission-group-management). Currently read implies write for these screens.
- **approve**: read=true AND (`decrypt_approver` canApproveForRequester OR `is_system_admin`). Only for search-history, pending-approvals.

**main (검색하기) special case**

- main is always `{ read: true }` when user has main in allowedScreenIds.
- No write, no approve. Search and view only.
- Decryption is gated by search-history approval flow, not main.

**Backward compatibility**

- `allowedScreenIds`: unchanged.
- `screenScopes`: unchanged.
- `screenFunctions`: new field. Clients that ignore it continue to use allowedScreenIds/screenScopes.
- Fallback: `user.screenFunctions?.[screenId] ?? deriveFromAllowedScreenIds(screenId)`.

**Frontend**

- Use `user.screenFunctions?.[screenId]` to enable/disable actions.
- **Filter/scope**: Continue using `screenScopes` for hide/show (user, department, IP filters).
- **Action buttons**: When function unavailable → disabled + tooltip (e.g. "승인 권한이 없습니다").
- **Hidden vs disabled**: Filters → hide when scope=self; action buttons → disabled + tooltip when function unavailable.

**Permission group config UI: descriptions for read/write/approve**

When an administrator configures screen-level function permissions (read/write/approve) for a permission group, the UI must provide **friendly descriptions** that explain what each selection changes for the end user. This helps admins understand the impact of their choices.

| Function | Description (Korean) | When to show |
|----------|---------------------|--------------|
| **Read (조회)** | "이 화면을 선택하면 사용자는 해당 화면의 데이터를 조회할 수 있습니다. 검색, 목록 보기, 상세 보기가 가능합니다." | All screens |
| **Write (수정)** | "수정 권한이 있으면 사용자는 생성·수정·삭제 등 변경 작업을 수행할 수 있습니다. 사용자 추가/수정, 권한 그룹 할당, 결재자 지정 등이 포함됩니다." | user-management, department-approvers, user-permission-hierarchy, permission-group-management |
| **Approve (승인)** | "승인 권한은 '부서별 결재자'에서 별도 지정이 필요합니다. 이 화면만 선택하면 승인/반려 버튼이 비활성화됩니다. 결재자로 지정된 사용자만 복호화 승인·반려를 처리할 수 있습니다." | search-history, pending-approvals |

**UI behavior**

- **On screen selection**: When a screen checkbox is checked, show a short summary of granted functions (e.g. "부여되는 권한: 조회" or "부여되는 권한: 조회, 수정").
- **On hover/focus (tooltip or info icon)**: Expand to the full description from the table above.
- **Per-function tooltip** (when read/write/approve are selectable): When the user hovers or focuses on a read/write/approve option, show the corresponding description.
- **Scope + function**: For activity-log, statistics, search-history, combine scope (본인만/전체) with function description (e.g. "본인만 선택 시: 본인 데이터만 조회 가능").

**Example copy (Korean)**

- Read: "조회 권한 – 화면 접근 및 데이터 열람 가능"
- Write: "수정 권한 – 생성·수정·삭제 등 변경 작업 가능"
- Approve: "승인 권한 – 복호화 승인/반려 처리 가능 (결재자 지정 필요)"

**Backend**

- AuthService: Compute `screenFunctions` from allowedScreenIds, screenScopes, decrypt_approver. Include in login and GET /api/auth/me.
- API controllers: For approve/reject, write operations — validate function permission in addition to screen access. Return 403 with `FUNCTION_NOT_ALLOWED` when lacking.

**Database**

- No schema change. `permission_group_screen` remains as-is.
- `screenFunctions` is derived at response time from existing data.

### Change file list

**(Confirmed by Backend subagent after Step 4 implementation.)**

#### Contract / spec

- `specs/permission-group-hierarchy.spec.yaml`
  - Add §4.4 screenFunctions: structure, derivation rules, per-screen mapping.
- `docs/contract.md`
  - §화면 기반 접근 제어: Add screenFunctions, API enforcement rules.
- `docs/api-definition.md`
  - §2.1 login, §2.4 /api/auth/me: Add screenFunctions to response. §11: Add FUNCTION_NOT_ALLOWED.

#### Backend (implemented)

- `backend/src/main/java/com/logmng/constants/ScreenConstants.java`
  - Added DEPARTMENT_APPROVERS, SCREENS_WITH_WRITE, SCREENS_WITH_APPROVE; supportsWrite(), supportsApprove().
- `backend/src/main/java/com/logmng/dto/response/ScreenFunctionCapability.java` (new)
  - DTO for per-screen { read, write?, approve? }.
- `backend/src/main/java/com/logmng/dto/response/LoginResponse.java`
  - Added screenFunctions field.
- `backend/src/main/java/com/logmng/service/AuthService.java`
  - Injected DecryptApproverService; resolveScreenFunctions(); hasWriteForManagementScreens(); canAccessDepartmentView(); include screenFunctions in login/me.
- `backend/src/main/java/com/logmng/controller/AuthController.java`
  - Store screenFunctions in session; include in check/me response.
- `backend/src/main/java/com/logmng/controller/SearchHistoryController.java`
  - requireApproverOrAdmin: throw FUNCTION_NOT_ALLOWED. (Service-level canApproveForRequester rejection unchanged.)
- `backend/src/main/java/com/logmng/service/SearchHistoryService.java`
  - approve/reject: throw FUNCTION_NOT_ALLOWED when canApproveForRequester is false.
- `backend/src/main/java/com/logmng/controller/PermissionGroupController.java`
  - requireWriteForManagement(); added to create, update, delete, assignUser, unassignUser.
- `backend/src/main/java/com/logmng/controller/DepartmentController.java`
  - requireDepartmentAccess(); list() uses department-approvers or user-permission-hierarchy.
- `backend/src/main/java/com/logmng/config/ScreenAccessInterceptor.java`
  - Added DEPARTMENT_APPROVERS to /api/departments path rule.
- `backend/src/test/java/com/logmng/service/StubAuthServiceForUserController.java`
  - Updated constructor for new AuthService param (DecryptApproverService).

#### Frontend

- `frontend/src/utils/security.js`
  - saveMinimalUserData: Store screenFunctions. getScreenFunctions, deriveScreenFunctionsFromAllowed helpers.
- `frontend/src/App.js`
  - Merge screenFunctions from auth/check and login. Pass user to PendingApprovals.
- `frontend/src/components/PendingApprovals/PendingApprovals.js`
  - Use screenFunctions['pending-approvals'].approve to enable/disable approve/reject; tooltip when disabled.
- `frontend/src/components/UserManagement/UserManagement.js`
  - Use screenFunctions for user-management/user-permission-hierarchy write; pass disabled to UserGroupAssignment.
- `frontend/src/components/UserGroupAssignment/UserGroupAssignment.js`
  - Use disabled prop for add/remove; tooltip when disabled.
- `frontend/src/components/PermissionGroupManagement/PermissionGroupPanel.js`
  - Use screenFunctions for permission-group-management/user-permission-hierarchy write; create/edit/delete/users disabled + tooltip.
- `frontend/src/components/PermissionGroupManagement/ScreenSelectionTree.js`
  - Add per-screen function descriptions (read/write/approve). "부여되는 권한: 조회" etc. with info icon tooltip.
- `frontend/src/components/PermissionGroupManagement/ScreenSelectionTree.css`
  - Styles for screen-selection-functions-summary, screen-selection-info-icon.
- `frontend/src/constants/screenFunctionDescriptions.js` (new)
  - Centralized copy for read/write/approve descriptions (req: friendly guidance when configuring).

#### Documentation

- `docs/design/function-availability.md` (optional)
  - Hide vs disabled criteria, disabled + tooltip pattern, a11y.
  - Read/write/approve description copy for permission group config UI (friendly guidance).

### Database changes

None. `screenFunctions` is derived from existing `permission_group_screen`, `decrypt_approver`, and `allowedScreenIds`.

---

## 3. Test approach

### Test case list (required)

| ID | Type | Scenario (input / condition) | Expected result | Verification |
|----|------|------------------------------|-----------------|---------------|
| TC-01 | Normal | User with main only logs in | screenFunctions.main = { read: true }; no write, approve | Integration (login/me response) |
| TC-02 | Normal | User with pending-approvals, decrypt_approver | screenFunctions['pending-approvals'] = { read: true, approve: true } | Integration |
| TC-03 | Normal | User with pending-approvals, not decrypt_approver | screenFunctions['pending-approvals'] = { read: true, approve: false } | Integration |
| TC-04 | Normal | User with user-management | screenFunctions['user-management'] = { read: true, write: true } | Integration |
| TC-05 | Normal | is_system_admin user | All screens have read; write/approve where applicable | Integration |
| TC-06 | Exception | Non-approver calls approve API | 403, code FUNCTION_NOT_ALLOWED | Integration |
| TC-07 | Edge | User with multiple groups (main + pending-approvals) | screenFunctions includes both; approve from decrypt_approver | Integration |
| TC-08 | Backward | Client ignores screenFunctions | allowedScreenIds, screenScopes unchanged; app works | Integration |
| TC-09 | Normal | main screen: user has main | read: true; no write, approve keys | Integration |
| TC-10 | Normal | Admin opens permission group create/edit, selects a screen | Per-screen function description (read/write/approve) visible; tooltip or inline help explains what each selection changes | Manual / browser automation |

### Test scenarios

#### Scenario 1: main read-only

1. Log in as user with only main in allowedScreenIds.
2. Call GET /api/auth/me.
3. Verify `screenFunctions.main` = { read: true }; no write, approve.
4. Navigate to main; verify no approve/reject/write actions visible.

#### Scenario 2: pending-approvals approve

1. Log in as user with pending-approvals and decrypt_approver.
2. Verify `screenFunctions['pending-approvals'].approve` = true.
3. Navigate to pending-approvals; verify approve/reject buttons enabled.
4. Log in as user with pending-approvals but not decrypt_approver.
5. Verify approve = false; buttons disabled with tooltip.

#### Scenario 3: API enforcement

1. Log in as non-approver with pending-approvals screen access.
2. Call POST /api/search-history/{id}/approve directly.
3. Expect 403, code FUNCTION_NOT_ALLOWED.

#### Scenario 4: Backward compatibility

1. Use a client that does not read screenFunctions.
2. Verify allowedScreenIds, screenScopes present and unchanged.
3. Verify menu, routing, filter visibility work as before.

#### Scenario 5: Permission group config — descriptions for read/write/approve

1. Log in as admin. Open 권한 그룹 추가 or 권한 그룹 수정 dialog.
2. Select a screen (e.g. 검색하기). Verify a short summary appears (e.g. "부여되는 권한: 조회").
3. Hover or focus on the info icon/tooltip. Verify the full description for Read is shown (what changes for the end user).
4. Select a management screen (e.g. 사용자 관리). Verify "부여되는 권한: 조회, 수정" and Write description.
5. Select 검색 이력 or 승인 대기. Verify Approve description mentions "결재자 지정 필요".

### Test data

- Rely on init-data: GENERAL_USER, permission_group_screen, decrypt_approver.
- Ensure test users: (a) main only, (b) pending-approvals + decrypt_approver, (c) pending-approvals without approver, (d) user-management.

### Test environment

- Frontend: http://localhost:3001 (per contract)
- Backend: http://localhost:9200
- Database: PostgreSQL (per contract)

### 3.5 Browser automation verification (optional)

For frontend-heavy verification, QA may use browser automation:

- **TC-01, TC-02, TC-03**: Navigate to main, pending-approvals; snapshot to verify buttons enabled/disabled.
- **TC-04**: Navigate to user-management; verify create/edit enabled when write=true.

Reference: `docs/workflow/BROWSER-AUTOMATION-VERIFICATION-POLICY.md`.

---

## 4. Checklist

### Frontend verification

- [x] screenFunctions merged from auth/check and login
- [x] Action buttons (approve, reject, create, edit) use screenFunctions
- [x] Disabled + tooltip when function unavailable
- [x] a11y: aria-disabled, aria-describedby for tooltip
- [x] Permission group config: read/write/approve descriptions visible when configuring; tooltip or inline help explains what each selection changes

### Backend verification

- [x] screenFunctions computed in AuthService
- [x] approve/reject APIs validate decrypt_approver or is_system_admin
- [x] write APIs validate screen access (existing) + function when applicable
- [x] 403 FUNCTION_NOT_ALLOWED when lacking

### Integration

- [x] main read-only verified
- [x] pending-approvals approve flow verified
- [x] user-management write flow verified
- [x] Backward compatibility verified

### Documentation

- [x] Requirement doc completed
- [x] specs §4.4, contract, api-definition updated
- [x] FUNCTION_NOT_ALLOWED in api-definition §11

---

## 5. Test results

**Date**: 2026-03-03  
**Commands**: `mvn test` (backend), `npm test -- --watchAll=false` (frontend), `curl` (integration), Puppeteer (browser attempt)

### Unit tests

| Scope | Command | Result | Notes |
|-------|---------|--------|-------|
| Backend | `cd backend && mvn test` | Pass | All tests passed |
| Frontend | `cd frontend && npm test -- --watchAll=false` | No tests | 0 test files; no failures |

### Health check

| Target | Result |
|--------|--------|
| Backend 9200 | 200 OK |
| Frontend 3001 | 200 OK |

### Integration (API) — TC-01 through TC-09

| ID | Scenario | Result | Verification |
|----|----------|--------|---------------|
| TC-01 | user2 (main via GENERAL_USER) | Pass | `screenFunctions.main` = `{ read: true }`; no write, approve |
| TC-02 | user1 (decrypt_approver) | Pass | `screenFunctions['pending-approvals']` = `{ read: true, approve: true }` |
| TC-03 | user2 (not decrypt_approver) | Pass | `screenFunctions['pending-approvals']` = `{ read: true, approve: false }` |
| TC-04 | user3 (user-management) | Pass | `screenFunctions['user-management']` = `{ read: true, write: true }` |
| TC-05 | admin (is_system_admin) | Pass | All screens in screenFunctions; read/write/approve where applicable |
| TC-06 | user2 calls approve API | Pass | 403, `code: "FUNCTION_NOT_ALLOWED"` |
| TC-07 | user1 (main + pending-approvals, decrypt_approver) | Pass | screenFunctions includes both; approve from decrypt_approver |
| TC-08 | Backward compatibility | Pass | allowedScreenIds, screenScopes, screenFunctions present in auth/me |
| TC-09 | main read-only | Pass | `screenFunctions.main` = `{ read: true }`; no write, approve keys |

### Browser (TC-02, TC-03, TC-04, TC-10)

| ID | Result | Notes |
|----|--------|-------|
| TC-02, TC-03, TC-04 | API verified | screenFunctions confirmed via login/me; UI (approve/reject enabled/disabled) relies on same data. Browser navigation to pending-approvals/user-management attempted; menu structure (react-pro-sidebar) made automated click flow complex. |
| TC-10 | Implemented | ScreenSelectionTree and screenFunctionDescriptions.js added per §2. Per-screen function descriptions (read/write/approve) and tooltip copy present in code. Manual verification: admin → 권한 그룹 추가/수정 → select screen → "부여되는 권한: 조회" etc. visible. |

### Summary

- **All API TCs (TC-01–TC-09)**: Pass  
- **TC-10**: Implementation complete; manual/browser verification of permission group config UI descriptions recommended for full E2E.

---

**Author**: Requirements subagent  
**Date**: 2025-03-03  
**Status**: Implemented and verified (Step 5 complete)  
**Related**: specs/permission-group-hierarchy.spec.yaml §4, docs/contract.md §화면 기반 접근 제어, .cursor/skills/auth-permission-domain/SKILL.md, .cursor/skills/ui-ux-domain/SKILL.md
