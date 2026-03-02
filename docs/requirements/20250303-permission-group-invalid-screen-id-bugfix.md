# 20250303 - Permission group invalid screen ID bugfix

## 1. User requirement

### Requirement description

When an administrator edits a permission group and selects "권한 그룹 관리" (permission-group-management) as an allowed screen, the save operation fails with a 400 error: "유효하지 않은 화면 ID입니다: permission-group-management" (INVALID_SCREEN_ID). The backend rejects the screen ID because it is not in its allowed list, even though the frontend offers it as a valid option.

### User scenario

1. An administrator opens the **permission group management** screen (권한 그룹 관리).
2. The administrator edits an existing permission group (e.g. GENERAL_USER) and selects "권한 그룹 관리" in the allowed screens tree.
3. The administrator clicks **Save** (저장).
4. **Problem**: The backend returns 400 with `INVALID_SCREEN_ID` — "유효하지 않은 화면 ID입니다: permission-group-management". The update fails.
5. **Expected**: The update should succeed with 200; the permission group should be saved with `permission-group-management` in its `allowedScreens`.

### Expected outcome

- PUT `/api/permission-groups/{id}` with `allowedScreens` including `permission-group-management` returns **200** and the updated group.
- No `INVALID_SCREEN_ID` error when `permission-group-management` is included in `allowedScreens`.
- Frontend and backend share the same allowed screen ID list; contract and spec are the single source of truth.

---

## 2. Design

### 2.1 Security review (optional)

- [ ] Security review performed (check if applicable)

**Scope**: No new data exposure. Adding `permission-group-management` to the allowed list aligns backend validation with the existing frontend menu. Same access control as today. No security changes required.

### Technical design

#### Problem analysis

1. **Frontend–backend mismatch**: The frontend `menuTree.js` (ALLOWED_SCREEN_IDS and MENU_TREE) includes `permission-group-management` as a selectable screen. The backend `ScreenConstants.java` does not include it in its allowed list.

2. **Spec not updated**: The spec `specs/permission-group-hierarchy.spec.yaml` §4.1 lists allowed screen IDs. It still includes `department-approvers` (removed per 20250227-remove-department-approver-screen) and does **not** include `permission-group-management` (added per 20250227-permission-group-separate-menu).

3. **Validation flow**: On PUT/POST permission-groups, the backend validates each value in `allowedScreens` against `ScreenConstants.findFirstInvalid()`. Any value not in the allowed list triggers 400 `INVALID_SCREEN_ID`.

4. **Root cause**: When 20250227-permission-group-separate-menu added the "권한 그룹 관리" menu and screen ID `permission-group-management`, the frontend was updated (menuTree.js) but the backend `ScreenConstants` and the spec §4.1 were not updated.

#### Solution approach

**Backend**

- Add `permission-group-management` to `ScreenConstants.java` (ALL_ALLOWED_SCREENS or equivalent).

**Contract / Spec**

- Update `specs/permission-group-hierarchy.spec.yaml` §4.1:
  - Remove `department-approvers` (already removed from frontend per 20250227-remove-department-approver-screen).
  - Add `permission-group-management` (권한 그룹 관리).

**Documentation**

- If `docs/api-definition.md` contains an explicit allowed screen list, update it to match §4.1. Otherwise, the spec is the source of truth; api-definition references the spec.

### Change file list

**(Confirmed by Backend subagent. Spec/docs updates: Contract/Documentation scope.)**

#### Backend (done)

- `backend/src/main/java/com/logmng/constants/ScreenConstants.java`
  - Added constant `PERMISSION_GROUP_MANAGEMENT = "permission-group-management"`.
  - Included in `ALL_ALLOWED_SCREENS`.

#### Spec / Contract (pending — Contract subagent)

- `specs/permission-group-hierarchy.spec.yaml`
  - §4.1: Remove `department-approvers`; add `permission-group-management` to the allowed screen ID table.

#### Documentation (pending — Documentation subagent)

- `docs/api-definition.md`
  - If it has an explicit allowed screen list section, update to match spec §4.1. Otherwise, no change (spec is referenced).

### Database changes

None.

---

## 3. Test approach

### Test case list (required)

| ID | Type | Scenario (input / condition) | Expected result | Verification |
|----|------|------------------------------|-----------------|--------------|
| TC-01 | Normal | Admin edits permission group, selects "권한 그룹 관리" in allowed screens, saves | PUT returns 200; group has `permission-group-management` in allowedScreens; no INVALID_SCREEN_ID | Integration (curl) or manual |
| TC-02 | Normal | Admin creates new permission group with `allowedScreens: ['permission-group-management']` | POST returns 201; created group includes permission-group-management | Integration |
| TC-03 | Regression | Admin edits group with other valid screens (main, search-history, etc.) | PUT returns 200; no regression | Integration |

### Test scenarios

#### Scenario 1: Permission group edit with permission-group-management

1. Log in as admin.
2. Open permission group management screen.
3. Edit an existing group (e.g. GENERAL_USER).
4. Select "권한 그룹 관리" in the allowed screens tree.
5. Click Save.
6. **Verification**: Response 200; GET the group and confirm `allowedScreens` includes `permission-group-management`; no INVALID_SCREEN_ID.

#### Scenario 2: Create group with permission-group-management

1. Log in as admin.
2. Create a new permission group with `allowedScreens: ['main', 'permission-group-management']`.
3. **Verification**: POST returns 201; GET the created group shows both screen IDs.

### Test data

- Existing permission group (e.g. GENERAL_USER) for edit.
- Admin user for API calls.

### Test environment

- Frontend: `http://localhost:3001`
- Backend: `http://localhost:9200`
- Database: PostgreSQL (per contract)

### 3.5 Browser automation verification (optional)

- **Applicable TCs**: TC-01 (manual-browser).
- **Procedure**: Log in as admin → navigate to 권한 그룹 관리 → edit group → select "권한 그룹 관리" in tree → Save → confirm no error toast; reload and confirm allowed screens include permission-group-management.

---

## 4. Checklist

### Backend verification

- [x] ScreenConstants updated with permission-group-management
- [x] PUT/POST permission-groups accepts permission-group-management
- [x] No INVALID_SCREEN_ID for permission-group-management

### Contract / Spec verification

- [ ] spec §4.1 updated (department-approvers removed, permission-group-management added) — pending Contract subagent
- [ ] api-definition aligned if it has screen list — pending Documentation subagent

### Integration

- [x] End-to-end: edit group with permission-group-management → 200 success
- [x] Regression: other valid screens still work

### Documentation

- [x] Requirement doc completed
- [x] §6 Error remedy result filled after verification

---

## 5. Test results

### Test run date

- 2026-03-03 (QA verification)

### Test results

#### Backend

**Pass** — `mvn test` exit 0 (confirmed by Backend handoff).

#### Integration (curl)

| TC | Scenario | HTTP | Result |
|----|----------|------|--------|
| TC-01 | PUT permission group 5 with `allowedScreens` including `permission-group-management` | 200 | Pass — response includes `permission-group-management` in allowedScreens |
| TC-02 | POST create group with `allowedScreens: ["main","permission-group-management"]` | 201 | Pass — created group id 31 includes both |
| TC-03 | PUT group 9 with `main`, `search-history` (regression) | 200 | Pass — no regression |

**Commands used:**

```bash
# Login
curl -c cookies -b cookies -X POST http://localhost:9200/api/auth/login \
  -H "Content-Type: application/json" -d '{"username":"admin","password":"admin123"}'

# TC-01
curl -b cookies -X PUT http://localhost:9200/api/permission-groups/5 \
  -H "Content-Type: application/json" \
  -d '{"code":"GENERAL_USER","name":"일반 사용자 그룹","description":"...","allowedScreens":["activity-log","main","pending-approvals","search-history","statistics","permission-group-management"],"sortOrder":0}'

# TC-02
curl -b cookies -X POST http://localhost:9200/api/permission-groups \
  -H "Content-Type: application/json" \
  -d '{"code":"QA_TC02","name":"QA TC-02 테스트","allowedScreens":["main","permission-group-management"],"sortOrder":99}'

# TC-03
curl -b cookies -X PUT http://localhost:9200/api/permission-groups/9 \
  -H "Content-Type: application/json" \
  -d '{"code":"VIEWER","name":"조회자","allowedScreens":["activity-log","main","search-history","statistics"],"sortOrder":0}'
```

**Outcome:** All TCs pass. INVALID_SCREEN_ID no longer occurs for `permission-group-management`.

### Issues found and resolution

None. Backend rebuild was required before restart (dev-services.sh uses existing JAR if present); after `mvn clean package -DskipTests` and restart, verification passed.

---

## 6. Error remedy result (cause and action) — for error/bug fix requirements only

- **Requirement ID**: 20250303-permission-group-invalid-screen-id-bugfix
- **Root cause**: Backend `ScreenConstants.java` did not include `permission-group-management` in `ALL_ALLOWED_SCREENS`, while the frontend menu (menuTree.js) offered it. PUT/POST validation rejected it with INVALID_SCREEN_ID.
- **Actions taken**: Added `PERMISSION_GROUP_MANAGEMENT = "permission-group-management"` to `ScreenConstants.java` and included it in `ALL_ALLOWED_SCREENS`.
- **Result**: PUT and POST permission-groups now accept `permission-group-management`; TC-01, TC-02, TC-03 all pass.
- **Completed**: 2026-03-03 08:21

---

**Author**: Requirements subagent  
**Date**: 2025-03-03  
**Status**: Complete
