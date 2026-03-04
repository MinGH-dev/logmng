# 20250304 - Single permission group per user

## 1. User requirement

### Requirement description
Change the permission group assignment model from many-to-many (a user can have multiple permission groups) to one-to-one (each user can have at most one permission group). This simplifies user management and prevents ambiguity from overlapping permission groups.

### User scenario
1. Admin navigates to User Management → selects a user in the permission hierarchy.
2. The user currently has multiple permission group badges with "+" add buttons and "×" remove buttons.
3. Admin wants to assign a permission group — the current UI allows adding multiple groups.
4. **Problem**: Multiple permission groups create ambiguity (union of screens, "most permissive" merge of write/approve/scope). A single group per user is clearer and simpler.

### Expected outcome
- Each user can have **at most one** permission group.
- The UI shows a **single dropdown/select** (not badges with add/remove).
- Assigning a new permission group **replaces** the existing one.
- Removing a permission group leaves the user with no group (no screen access except for system admins).
- The `user-permission-hierarchy` API response `permissionGroups` array contains 0 or 1 elements.
- Backend enforces the single-group constraint (DB UNIQUE constraint on `app_user_permission_group.user_id`).
- No "union" or "merge" logic needed for `allowedScreenIds`, `screenScopes`, or `screenFunctions`.

## 2. Design

### Technical design

#### Problem analysis
1. `app_user_permission_group` join table has composite PK `(user_id, permission_group_id)` — allows multiple rows per user.
2. `PermissionGroupService.getAllowedScreenIdsForUser()` unions screens across all groups.
3. `PermissionGroupService.getScreenFunctionsForUser()` uses `mergeScreenFunction()` to pick most permissive values across groups.
4. `PermissionGroupService.getScreenScopesForUser()` picks "all" if any group has "all".
5. Frontend `UserGroupAssignment` renders badges for each group with add/remove UI.
6. `PermissionGroupService.assignUser()` only checks `USER_ALREADY_IN_GROUP` for same-group, not cross-group.

#### Solution approach

**Database:**
- Add migration to enforce UNIQUE on `app_user_permission_group.user_id`. Before migration, clean up any users with multiple groups (keep last assigned).
- Migration SQL: `ALTER TABLE app_user_permission_group ADD CONSTRAINT uq_user_permission_group_user UNIQUE (user_id);`

**Backend:**
- `PermissionGroupService.assignUser()`: Before insert, delete any existing row for the user (auto-replace). Remove `USER_ALREADY_IN_GROUP` check (now allowed to replace). Keep `USER_ALREADY_IN_GROUP` only when user is already in the **same** group.
- `getAllowedScreenIdsForUser()`: No logic change needed (SQL already works for 0 or 1 group).
- `getScreenFunctionsForUser()`: No logic change needed (merge is harmless with 1 group; but simplification is optional).
- `getScreenScopesForUser()`: Same — works correctly with 0 or 1 group.

**Frontend:**
- `UserGroupAssignment.js`: Replace badge-based multi-group UI with a single `<select>` dropdown.
  - Shows "— 없음 —" when no group assigned.
  - Shows current group as selected option.
  - Changing selection calls `addUserToGroup()` (backend auto-replaces).
  - Selecting "— 없음 —" calls `removeUserFromGroup()` to remove current group.
  - No "+" add / "×" remove buttons needed.

**Spec/Contract:**
- Update `specs/permission-group-hierarchy.spec.yaml` to reflect single-group model.
- Update Cursor skills (`auth-permission-domain`, `api-permission-map`) to reflect single-group model.

### Change file list

**(Tentative. Implementing agent (Step 4) confirms or updates with actual files changed.)**

#### Frontend
- `frontend/src/components/UserGroupAssignment/UserGroupAssignment.js`
  - Replace badge+add/remove UI with single dropdown select
- `frontend/src/components/UserGroupAssignment/UserGroupAssignment.css`
  - Update styles for single select layout

#### Backend
- `backend/src/main/java/com/logmng/service/PermissionGroupService.java`
  - `assignUser()`: auto-replace existing group (delete old, insert new)

#### Database
- `backend/src/main/resources/db/schema.sql`
  - Add UNIQUE constraint on `app_user_permission_group.user_id`

#### Spec / Cursor tools
- `specs/permission-group-hierarchy.spec.yaml`
  - Update multi-group references to single-group model
- `.cursor/skills/auth-permission-domain/SKILL.md`
  - Update "permission groups" references to singular
- `.cursor/skills/api-permission-map/SKILL.md`
  - Update "permission groups" reference

### Database changes
- Add UNIQUE constraint on `app_user_permission_group.user_id` to enforce single group per user.
- Migration: clean up duplicate rows if any exist before adding constraint.

## 3. Test approach

### Test case list (required)

| ID | Type | Scenario (input / condition) | Expected result | Verification (unit / integration / manual) |
|----|------|------------------------------|-----------------|--------------------------------------------|
| TC-01 | Normal | Assign a permission group to user with no group | User has the assigned group | Integration (curl) |
| TC-02 | Normal | Assign a different group to user who already has one | Old group replaced by new group | Integration (curl) |
| TC-03 | Normal | Assign same group to user who already has it | 400 USER_ALREADY_IN_GROUP | Integration (curl) |
| TC-04 | Normal | Remove user from group | User has no group | Integration (curl) |
| TC-05 | Normal | User-permission-hierarchy shows single group per user | permissionGroups array has 0 or 1 elements | Integration (curl) |
| TC-06 | Edge | System admin assigns group to self | Works (system admin can have a group too) | Integration (curl) |
| TC-07 | Edge | DB UNIQUE constraint prevents duplicate user_id | INSERT of second row for same user fails at DB level | Manual (SQL) |
| TC-08 | UI | Frontend shows single dropdown (not badges) | Single select with current group selected or "없음" | Manual / Browser |
| TC-09 | UI | Change selection in dropdown → auto-replaces group | After change, user has new group; previous removed | Manual / Browser |
| TC-10 | UI | Select "없음" → removes group | User has no group; allowedScreenIds becomes empty | Manual / Browser |

### Test scenarios

#### Scenario 1: Assign group to user with no group
1. Login as system admin
2. POST `/api/permission-groups/{groupId}/users` with `{ "userId": "testuser" }`
3. Verify: GET `/api/departments/user-permission-hierarchy` → testuser has 1 permission group

#### Scenario 2: Replace group (auto-replace)
1. User "testuser" already has group A
2. POST `/api/permission-groups/{groupB_id}/users` with `{ "userId": "testuser" }`
3. Verify: testuser now has only group B (group A removed)

#### Scenario 3: Same group re-assignment
1. User "testuser" already has group A
2. POST `/api/permission-groups/{groupA_id}/users` with `{ "userId": "testuser" }`
3. Verify: 400 USER_ALREADY_IN_GROUP

#### Scenario 4: Frontend single select
1. Login as admin, navigate to user management
2. Click a user → see single dropdown for permission group
3. Change selection → group auto-replaces
4. Select "없음" → group removed

### Test data
- System admin user: `admin` (is_system_admin=true)
- Test user: existing user with a permission group assigned
- Multiple permission groups available (e.g. "기본 권한", "관리자 권한")

### Test environment
- Frontend: `http://localhost:3001`
- Backend: `http://localhost:9200`
- Database: PostgreSQL

## 4. Checklist

### Frontend verification
- [ ] Single dropdown renders instead of badges
- [ ] Dropdown shows all available groups + "없음"
- [ ] Changing selection triggers API call and refreshes
- [ ] Error handling on API failure

### Backend verification
- [ ] assignUser auto-replaces existing group
- [ ] USER_ALREADY_IN_GROUP only for same group
- [ ] UNIQUE constraint in schema.sql
- [ ] allowedScreenIds works correctly with single group

### Integration
- [ ] End-to-end: change group in UI → verify in hierarchy API
- [ ] Edge case: system admin user with group

### Documentation
- [ ] Requirement doc completed
- [ ] Spec updated
- [ ] Cursor skills updated

## 5. Test results

### Test run date
- 2025-03-04

### Test results

#### Backend
Pass
- TC-01: Assign group to user with no group → 201 Created (PASS)
- TC-02: Auto-replace group (ADMIN→GENERAL_USER) → 201 Created, old group removed (PASS)
- TC-03: Same group re-assignment → 400 USER_ALREADY_IN_GROUP (PASS)
- TC-04: Remove user from group → 200 OK (PASS)
- TC-05: user-permission-hierarchy → all users have 0 or 1 group (PASS)
- TC-07: DB UNIQUE constraint → INSERT second row fails with "duplicate key" (PASS)

#### Frontend
Pass
- TC-08: Single dropdown rendered (not badges) → combobox with all groups + "없음" (PASS)
- TC-09: Change selection auto-replaces group → user1 changed from TC02_APPROVE to ADMIN (PASS)

**Commands:**

```bash
# Login
curl -s -c /tmp/admin.txt -X POST http://localhost:9200/api/auth/login -H 'Content-Type: application/json' -d '{"username":"admin","password":"admin123"}'

# TC-01: Assign to user with no group
curl -s -b /tmp/admin.txt -X DELETE http://localhost:9200/api/permission-groups/3/users/admin
curl -s -b /tmp/admin.txt -X POST http://localhost:9200/api/permission-groups/3/users -H 'Content-Type: application/json' -d '{"userId":"admin"}' -w "\nHTTP %{http_code}\n"

# TC-02: Auto-replace
curl -s -b /tmp/admin.txt -X POST http://localhost:9200/api/permission-groups/5/users -H 'Content-Type: application/json' -d '{"userId":"admin"}' -w "\nHTTP %{http_code}\n"

# TC-03: Same group re-assignment
curl -s -b /tmp/admin.txt -X POST http://localhost:9200/api/permission-groups/5/users -H 'Content-Type: application/json' -d '{"userId":"admin"}' -w "\nHTTP %{http_code}\n"

# TC-04: Remove
curl -s -b /tmp/admin.txt -X DELETE http://localhost:9200/api/permission-groups/5/users/admin -w "\nHTTP %{http_code}\n"

# TC-05: Hierarchy check
curl -s -b /tmp/admin.txt 'http://localhost:9200/api/departments/user-permission-hierarchy?format=flat' | python3 -m json.tool
```

**Outcome:**
- All backend TCs pass
- Frontend renders single dropdown correctly
- Auto-replace works end-to-end (UI → API → DB)

---

**Author**: AI Agent
**Date**: 2025-03-04
**Status**: In progress
