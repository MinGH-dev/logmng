# 20250303 - Screen function checkbox selection (read/write/approve)

## 1. User requirement

### Requirement description

1. **Explicit checkbox selection**: When configuring a permission group's allowed screens, allow the administrator to **explicitly select read, write, and approve via checkboxes** per screen, instead of these being derived automatically.

2. **Current vs desired**: The parent requirement (20250303-screen-function-availability) defines a common format for screen function availability: read from `allowedScreenIds`, write derived from screen type (read implies write for management screens), approve from `decrypt_approver`. The user wants **explicit checkbox selection** so the admin can grant or revoke each function independently per screen.

3. **Scope**: Applies to the permission group config UI (ScreenSelectionTree) and the backend storage/derivation of `screenFunctions`. No change to the auth response shape; only the source of truth changes from derivation to explicit storage with backward-compatible fallback.

### User scenario

1. An administrator opens the permission group create or edit dialog and selects screens (e.g. main, search-history, user-management).

2. **Current behavior**: When a screen is selected, the UI shows a summary like "부여되는 권한: 조회" or "부여되는 권한: 조회, 수정" — these are **derived** and not editable. The admin cannot revoke write for user-management while keeping read, or enable approve for search-history without being a decrypt_approver.

3. **Desired behavior**: When a screen is selected, the admin sees **checkboxes** for 조회(read), 수정(write), 승인(approve) where applicable. They can:
   - Uncheck **write** for user-management to grant read-only access to that screen.
   - Check **approve** for search-history/pending-approvals to enable approve UI for users who are decrypt_approvers (checkbox = "enable approve when user is approver").
   - For main: only read is shown (read-only screen).

4. **Problem**: The current design does not allow fine-grained control. Admins cannot grant read-only access to management screens or explicitly enable approve per screen. The user wants explicit control via checkboxes.

### Expected outcome

- **UI**: ScreenSelectionTree shows checkboxes for read, write, approve per screen when the screen is selected. main: read only. search-history, pending-approvals: read + approve. user-management, department-approvers, user-permission-hierarchy, permission-group-management: read + write.
- **Storage**: `permission_group_screen` stores `read`, `write`, `approve` per row. API `allowedScreens` shape extended to `{ screenId, scope?, read?, write?, approve? }`.
- **Backward compatibility**: Existing rows without read/write/approve (NULL) use current derivation. Clients that omit these fields continue to work.
- **approve semantics**: approve checkbox = "enable approve for this screen when user is decrypt_approver". Actual approve still requires `decrypt_approver` or `is_system_admin`; checkbox only gates whether the screen grants approve when user is approver.
- **read**: Selecting a screen defaults read=true. UX recommends not allowing read uncheck (uncheck would mean no access — use screen deselection instead).

---

## 2. Design

### 2.1 Security review

**Scope**: `permission_group_screen` extension, explicit read/write/approve storage, permission group config API and UI, `screenFunctions` derivation change.

**Reference**: `docs/security-guide.md`, `docs/requirements/20250303-screen-function-availability.md` §2.1, `specs/permission-group-hierarchy.spec.yaml` §4.4.

#### Risks

| Risk | Description | Mitigation |
|------|-------------|------------|
| **Client manipulation** | PUT/POST `allowedScreens` with `read`, `write`, `approve` could be manipulated by non-admin clients. | Permission group CRUD is **admin-only** (is_system_admin=true). Server validates and stores to DB. `screenFunctions` is **always computed server-side from DB**. Never trust client values. |
| **Privilege creep** | approve checkbox might grant approve without `decrypt_approver`. | **approve = (pgs.approve) AND (decrypt_approver canApproveForRequester OR is_system_admin)**. Checkbox only enables approve when user is approver. |
| **write without scope** | write=true with scope=self could allow editing others' data. | Write APIs validate **function(write) AND scope**. scope=self → reject edits targeting others' data with 403. Existing spec §4.4 unchanged. |
| **Information disclosure** | `screenFunctions` might expose other users' permissions. | `screenFunctions` is **always for the current user only**. Same as `allowedScreenIds`/`screenScopes`. |
| **Backward migration** | Existing `permission_group_screen` rows without read/write/approve could cause derivation errors. | **NULL → use existing derivation**. Migration script adds columns; existing rows remain NULL. |

#### approve vs decrypt_approver interaction

| Rule | Requirement |
|------|-------------|
| **approve checkbox** | "Enable approve for this screen when user is approver." `permission_group_screen.approve = true` → approve UI enabled for approvers. |
| **Actual approve** | approve requires **decrypt_approver** (canApproveForRequester) or **is_system_admin**. |
| **Derivation** | `approve = (pgs.approve OR pgs.approve IS NULL) AND (decrypt_approver canApproveForRequester OR is_system_admin)`. |
| **API enforcement** | approve/reject APIs continue to validate `canApproveForRequester` or `is_system_admin`. `screenFunctions.approve` is for UI only. |

#### read / write behavior

| Concern | Rule |
|---------|------|
| **read uncheck** | read=false → no access to screen. Exclude from `allowedScreenIds`. |
| **read default** | Screen selected → read=true default. read omitted → same as current (allowedScreenIds implies read=true). |
| **write explicit** | write is **explicit checkbox** for management screens. read=true with write=false is valid (read-only management access). |
| **write + scope** | write=true with scope=self → editing others' data returns 403. Existing spec §4.4 unchanged. |

#### Server-side vs client-side

| Principle | Recommendation |
|-----------|----------------|
| **Client (UI)** | `screenFunctions` drives button/menu visibility. UI is UX only. |
| **Server (mandatory)** | All function-level APIs (approve, reject, write) validate server-side. 403 when lacking. |
| **Defense in depth** | Frontend: UI control; Backend: API validation. Either failure alone must not compromise security. |

#### PII / decryption scope

- **No new PII**: `screenFunctions` and `permission_group_screen` store only function flags (read/write/approve); no PII.
- **Decryption scope unchanged**: approve checkbox does not expand decryption scope. Actual approve still requires `decrypt_approver` (canApproveForRequester) or `is_system_admin`. Checkbox only gates whether the screen grants approve UI when the user is an approver.
- **Approve data exposure**: approve exposes search history and requester info; unchanged from parent. Approve APIs continue to validate `canApproveForRequester` or `is_system_admin`.

#### Acceptance criteria (security)

- [ ] **Permission group API**: PUT/POST `allowedScreens` read/write/approve validated server-side. Store to DB after validation. `INVALID_SCREEN_ID`, `INVALID_SCREEN_FUNCTION` validation.
- [ ] **screenFunctions derivation**: Always computed server-side from DB. No client input.
- [ ] **approve**: approve checkbox is **additional condition** to decrypt_approver/is_system_admin. Non-approver → approve always false.
- [ ] **write + scope**: Write APIs validate function and scope. scope=self → 403 for others' data.
- [ ] **Backward compat**: NULL read/write/approve → existing derivation. Migration preserves behavior.
- [ ] **Error response**: 403 `FUNCTION_NOT_ALLOWED` uses generic message. No resource existence or internal structure disclosure.
- [ ] **Admin-only**: Permission group CRUD and read/write/approve config are admin-only.

#### Additional recommendations (optional)

- **Audit logging**: Consider logging 403 `FUNCTION_NOT_ALLOWED` for security monitoring and privilege-escalation detection (per parent 20250303-screen-function-availability §2.1).
- **Permission revocation**: `screenFunctions` is computed at login/me. Revoked permissions (e.g. approve unchecked) take effect on next token refresh or re-login. Immediate revocation would require token invalidation (out of scope).

### Technical design

#### Problem analysis

1. **Derived only**: Current design derives read from allowedScreenIds, write from screen type (read implies write for management screens), approve from decrypt_approver. No explicit storage.
2. **No fine-grained control**: Admin cannot grant read-only access to user-management or explicitly enable approve per screen.
3. **UI shows summary only**: ScreenSelectionTree displays "부여되는 권한: 조회, 수정" as derived text; not editable.
4. **permission_group_screen**: Only stores (permission_group_id, screen_id, scope). No read/write/approve columns.

#### Solution approach

**Database**

- Add `read`, `write`, `approve` columns (BOOLEAN NULL) to `permission_group_screen`.
- NULL = use existing derivation. No DEFAULT to preserve backward compatibility.
- Migration: `ALTER TABLE permission_group_screen ADD COLUMN IF NOT EXISTS read BOOLEAN` (and write, approve).
- schema.sql: Add columns to CREATE TABLE for new installs.
- init-data: No change; existing INSERTs omit new columns → NULL.

**API / Contract**

- Extend `AllowedScreenItem`: `{ screenId, scope?, read?, write?, approve? }`.
- Validation: main → read only (write/approve invalid). write/approve only for screens that support them. New error: `INVALID_SCREEN_FUNCTION` (400).
- Backward compat: omit read/write/approve → NULL in DB → derivation.

**Backend**

- `AllowedScreenItem` DTO: add read, write, approve (Boolean).
- `PermissionGroupService`: load/save read, write, approve to/from permission_group_screen.
- `AuthService.resolveScreenFunctions`: when pgs.read/write/approve are non-null, use them; else use existing derivation. approve = (pgs.approve OR null) AND (decrypt_approver OR is_system_admin).
- Validation: reject main with write=true or approve=true; reject write/approve on unsupported screens.

**Frontend**

- `ScreenSelectionTree`: When screen selected, show checkboxes for read, write, approve where applicable.
- **Layout**: Inline. Same row as screen checkbox + scope dropdown.
- **Defaults**: read=true (or hidden/disabled — always true when screen selected). write=true for management screens. approve=false.
- **read**: UX recommends not allowing uncheck (use screen deselection). Or show as label only.
- **write**: Only for user-management, department-approvers, user-permission-hierarchy, permission-group-management.
- **approve**: Only for search-history, pending-approvals. Tooltip: "결재자 지정 필요".
- **a11y**: role="group", aria-label per checkbox, Tooltip with aria-describedby.
- **onChange**: Send `[{ screenId, scope?, read?, write?, approve? }]` to parent.

**Per-screen mapping**

| screen_id | read | write | approve | Notes |
|-----------|------|-------|---------|-------|
| main | ✓ only | — | — | read-only; no write/approve checkboxes |
| search-history | ✓ | — | ✓ | approve checkbox |
| activity-log, statistics | ✓ | — | — | scope only |
| pending-approvals | ✓ | — | ✓ | approve checkbox |
| user-management, department-approvers, user-permission-hierarchy, permission-group-management | ✓ | ✓ | — | write checkbox |

### Change file list

**(Confirmed by Backend subagent (Step 4).)**

#### Contract / spec

- `specs/permission-group-hierarchy.spec.yaml`
  - §1.1 AllowedScreenItem: add read, write, approve. §1.1.1 validation rules. §2.1 permission_group_screen schema. §3 INVALID_SCREEN_FUNCTION. §4.4 explicit vs derived.
- `docs/contract.md`
  - permission_group_screen columns, screenFunctions explicit vs derived.
- `docs/api-definition.md`
  - allowedScreens shape, INVALID_SCREEN_FUNCTION.

#### Backend

- `backend/src/main/resources/db/schema.sql`
  - permission_group_screen: add read, write, approve BOOLEAN NULL.
- `backend/src/main/resources/db/migrate-permission-group-screen-functions.sql` (new)
  - ADD COLUMN read, write, approve.
- `backend/src/main/java/com/logmng/dto/response/AllowedScreenItem.java`
  - Add read, write, approve (Boolean).
- `backend/src/main/java/com/logmng/dto/request/AllowedScreenListDeserializer.java`
  - Parse read, write, approve from JSON objects.
- `backend/src/main/java/com/logmng/service/PermissionGroupService.java`
  - loadAllowedScreens: SELECT read, write, approve. saveAllowedScreens: INSERT with read, write, approve.
  - validateScreenFunctions: reject main with write/approve; reject write/approve on unsupported screens. 400 INVALID_SCREEN_FUNCTION.
  - getScreenFunctionsForUser: per-screen read/write/approve from permission_group_screen for user's groups.
  - getAllowedScreenIdsForUser: exclude screens with read=false.
- `backend/src/main/java/com/logmng/service/AuthService.java`
  - resolveScreenFunctions: use pgs.read/write/approve when non-null; else derivation. approve = (pgs.approve OR null) AND (decrypt_approver OR is_system_admin).
  - hasApproveForSearchHistory: for approve/reject APIs.
- `backend/src/main/java/com/logmng/controller/PermissionGroupController.java` or service
  - Validate read/write/approve per screen; reject INVALID_SCREEN_FUNCTION (in PermissionGroupService.validateAllowedScreens).
- `backend/src/main/java/com/logmng/controller/SearchHistoryController.java`
  - requireApproverOrAdmin: add screenFunctions.approve check via authService.hasApproveForSearchHistory.

#### Frontend

- `frontend/src/components/PermissionGroupManagement/ScreenSelectionTree.js`
  - Add checkboxes for read (label only), write, approve when screen selected. Inline layout. onChange sends [{ screenId, scope?, read?, write?, approve? }]. a11y: role="group", aria-label, aria-describedby for approve tooltip.
- `frontend/src/components/PermissionGroupManagement/ScreenSelectionTree.css`
  - Styles for function checkboxes, sr-only for tooltip. Removed unused summary styles.
- `frontend/src/constants/screenFunctionDescriptions.js`
  - Added APPROVE_CHECKBOX_TOOLTIP ('결재자 지정 필요'). Reuse FUNCTION_LABELS, SCREENS_WITH_WRITE, SCREENS_WITH_APPROVE.
- `frontend/src/components/PermissionGroupManagement/PermissionGroupPanel.js`
  - normalizeAllowedScreens: preserve read, write, approve from API. toAllowedScreensPayload: build API payload with read/write/approve. handleCreateSubmit/handleEditSubmit use toAllowedScreensPayload.

#### Documentation

- `docs/requirements/20250303-screen-function-checkbox-selection.md` (this doc)
- `docs/requirements/TOPIC-INDEX.md`
  - Add entry under permission | access-control.

### Database changes

- **permission_group_screen**: Add `read BOOLEAN NULL`, `write BOOLEAN NULL`, `approve BOOLEAN NULL`.
- Migration script: idempotent ADD COLUMN IF NOT EXISTS.
- No change to init-data; existing rows remain NULL → derivation.

---

## 3. Test approach

### Test case list (required)

| ID | Type | Scenario (input / condition) | Expected result | Verification |
|----|------|------------------------------|-----------------|--------------|
| TC-01 | Normal | Admin creates permission group with user-management, write unchecked | screenFunctions['user-management'] = { read: true, write: false } | Integration (login/me) |
| TC-02 | Normal | Admin creates group with search-history, approve checked; user is decrypt_approver | screenFunctions['search-history'].approve = true | Integration |
| TC-03 | Normal | Same group, user is not decrypt_approver | screenFunctions['search-history'].approve = false | Integration |
| TC-04 | Normal | Existing permission_group_screen row (NULL read/write/approve) | Derivation unchanged; read=true, write from screen type, approve from decrypt_approver | Integration |
| TC-05 | Exception | POST permission-groups with main, write=true | 400, INVALID_SCREEN_FUNCTION | Integration |
| TC-06 | Exception | POST with approve=true for user-management | 400, INVALID_SCREEN_FUNCTION | Integration |
| TC-07 | Normal | User with user-management read-only (write=false) calls user create API | 403, FUNCTION_NOT_ALLOWED | Integration |
| TC-08 | Backward | Client sends allowedScreens without read/write/approve | Stored as NULL; derivation used; behavior unchanged | Integration |
| TC-09 | Normal | Admin opens permission group edit, selects user-management | Checkboxes for 조회, 수정 visible; write default true | Manual / browser |
| TC-10 | Normal | Admin selects search-history | Checkboxes for 조회, 승인 visible; approve default false; tooltip "결재자 지정 필요" | Manual / browser |

### Test scenarios

#### Scenario 1: Explicit write=false for user-management

1. Create permission group with user-management, write=false (read=true).
2. Assign user to group. Log in.
3. GET /api/auth/me → screenFunctions['user-management'] = { read: true, write: false }.
4. Call POST /api/users (or similar write API) → 403 FUNCTION_NOT_ALLOWED.

#### Scenario 2: approve checkbox + decrypt_approver

1. Create group with search-history, approve=true.
2. User A: decrypt_approver. User B: not decrypt_approver. Both in group.
3. User A login → screenFunctions['search-history'].approve = true.
4. User B login → screenFunctions['search-history'].approve = false.
5. User B calls approve API → 403.

#### Scenario 3: Backward compatibility

1. Existing permission_group_screen rows (no read/write/approve columns or NULL).
2. User logs in. screenFunctions derived as before (read from allowedScreenIds, write from screen type, approve from decrypt_approver).
3. No regression.

#### Scenario 4: Permission group config UI

1. Admin opens 권한 그룹 추가 or 수정.
2. Select 검색하기 → only "조회" (no write/approve checkboxes).
3. Select 사용자 관리 → 조회, 수정 checkboxes. Write default true.
4. Select 검색 이력 → 조회, 승인 checkboxes. Approve default false. Tooltip on approve.
5. Uncheck 수정 for 사용자 관리 → save → verify user gets read-only.

### Test data

- Use init-data: GENERAL_USER, permission groups, decrypt_approver.
- Add test permission groups with explicit read/write/approve for TC-01–TC-03, TC-07.

### Test environment

- Frontend: http://localhost:3001 (per contract)
- Backend: http://localhost:9200
- Database: PostgreSQL (per contract)

### 3.5 Browser automation verification (optional)

- **TC-09, TC-10**: Admin → 권한 그룹 추가/수정 → select screen → verify checkboxes for read/write/approve, defaults, tooltips.
- **Reference**: `docs/workflow/BROWSER-AUTOMATION-VERIFICATION-POLICY.md`.

---

## 4. Checklist

### Frontend verification

- [ ] ScreenSelectionTree shows read/write/approve checkboxes when screen selected
- [ ] Per-screen mapping: main read-only; management screens read+write; search-history/pending-approvals read+approve
- [ ] Defaults: read=true, write=true for management, approve=false
- [ ] onChange sends read, write, approve to parent
- [ ] Tooltips from screenFunctionDescriptions.js
- [ ] a11y: role="group", aria-label, aria-describedby for tooltips

### Backend verification

- [ ] permission_group_screen read, write, approve columns
- [ ] AuthService.resolveScreenFunctions uses explicit values when non-null; else derivation
- [ ] approve = (pgs.approve OR null) AND (decrypt_approver OR is_system_admin)
- [ ] Validation: INVALID_SCREEN_FUNCTION for main with write/approve, unsupported screens
- [ ] Write APIs return 403 when write=false

### Integration

- [ ] TC-01 through TC-08 pass
- [ ] Backward compatibility verified
- [ ] approve + decrypt_approver interaction verified

### Documentation

- [ ] Requirement doc completed
- [ ] specs §1.1, §2.1, §4.4 updated
- [ ] TOPIC-INDEX.md updated

---

## 5. Test results

**Date**: 2025-03-03  
**Verification**: Step 5 QA (health check, TC-01–TC-10, browser). Re-verification after bugfix-1.

### Health check

| Check | Result |
|-------|--------|
| Backend 9200 | Pass — 200, JSON OK |
| Frontend 3001 | Pass — 200 |
| DB | Pass — connected |

### Integration (API)

| ID | Result | Note |
|----|--------|------|
| TC-01 | **Pass** | user-management write=false stored; user_tc01 (group 50 only) → screenFunctions['user-management'] = { read: true, write: false } |
| TC-02 | Pass | user1 (decrypt_approver) + search-history approve=true → screenFunctions['search-history'].approve = true |
| TC-03 | Pass | user2 (not approver) + same group → screenFunctions['search-history'].approve = false |
| TC-04 | Pass | user3 (GENERAL_USER, NULL rows) → derivation works |
| TC-05 | **Pass** | POST main write=true → 400 INVALID_SCREEN_FUNCTION |
| TC-06 | **Pass** | POST user-management approve=true → 400 INVALID_SCREEN_FUNCTION |
| TC-07 | **Pass** | user_tc01 (write=false) calls POST permission-groups/50/users → 403 FUNCTION_NOT_ALLOWED |
| TC-08 | Pass | allowedScreens without read/write/approve → stored NULL; derivation used |

### Browser (TC-09, TC-10)

| ID | Result | Note |
|----|--------|------|
| TC-09 | Skip | Backend-only re-verification. Manual verification recommended for checkbox UI. |
| TC-10 | Skip | Same as TC-09 |

### Bugfix child

- **20250303-screen-function-checkbox-selection-bugfix-1**: Backend — AllowedScreenListDeserializer fix. **Closed** — re-verification passed.

**Commit**: Performed per commit-on-complete.md (req 20250303-screen-function-checkbox-selection, bugfix-1).

---

**Author**: Requirements subagent  
**Date**: 2025-03-03  
**Status**: Complete (bugfix-1 closed; verification passed)  
**Related**: docs/requirements/20250303-screen-function-availability.md, specs/permission-group-hierarchy.spec.yaml §4.4, .cursor/skills/auth-permission-domain/SKILL.md
