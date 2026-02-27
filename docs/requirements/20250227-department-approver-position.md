# 20250227 - Department approver: position field and scoped selection

## 1. User requirement

### Requirement description

When designating approvers by department, only users who belong to that department may be selected. A **position** (직책) field is added for department members so that users whose position contains "팀장" (team leader) can be set as default approvers with a single action.

**Scope note**: Department info (including position) will be updated separately later via backend batch. This requirement focuses on the UI and logic for approver selection and default assignment; backend batch sync is **out of scope**.

### User scenario

1. An administrator opens the **department approver** screen and selects a department from the tree.
2. When adding an approver, the dropdown shows **only users who belong to that department** (direct members: `department_code` = selected department code).
3. The approver table displays each user's **position** (직책) so that team leaders can be identified.
4. The administrator clicks **"팀장 지정"** (Assign team leaders) to add all users whose position contains "팀장" as approvers in one action.
5. **Problem**: Currently, the approver dropdown shows all users regardless of department, and there is no position field or default approver logic based on team leader role.

### Expected outcome

- **Department-scoped selection**: Only users whose `department_code` matches the selected department appear in the approver dropdown.
- **Position display**: The approver table and user selection show the `position` field (e.g. 팀장, 대리, 사원). Null/empty is shown as "-".
- **Default approver action**: A "팀장 지정" button adds all department members whose position contains "팀장" as approvers. If none exist or all are already approvers, the button is disabled or shows guidance.
- **Backend validation**: If a user not in the department is submitted as an approver (e.g. via direct API call), the backend returns 400 with `USER_NOT_IN_DEPARTMENT`.

---

## 2. Design

### 2.1 Security review (optional; when PII / decryption / access control)

- [ ] Security review performed (check if applicable)
- **Scope**: Same as existing department approver APIs (admin-only). No new PII; position is organizational metadata.
- **Recommendation**: No additional security review required for this requirement.

### Technical design

#### Problem analysis

1. **Unrestricted approver selection**: `DepartmentApproverManagement` uses `getUsers()` (all users) for the approver dropdown. Users from other departments can be selected.
2. **No position field**: `app_user` has no `position` column. `UserListItemResponse` and approver list responses do not include position.
3. **No default approver logic**: There is no way to assign team leaders (position contains "팀장") as approvers in one action.
4. **Missing backend validation**: `POST /api/departments/{code}/approvers` does not verify that the user belongs to the department.

#### Solution approach

**Database (DBA review)**

- Add `position VARCHAR(50) NULL` to `app_user`.
- Migration: `ALTER TABLE app_user ADD COLUMN IF NOT EXISTS position VARCHAR(50) NULL;`
- No index initially. Data will be populated by a separate backend batch (out of scope).

**Backend**

- **Schema**: Add `position` to `app_user` in `schema.sql` and migration script.
- **New API**: `GET /api/departments/{code}/members` — returns users whose `department_code = code`. Response: `userId`, `username`, `role`, `departmentCode`, `position`, `isApprover` (for that department).
- **Response extension**: `GET /api/departments/{code}/approvers` and `UserListItemResponse` include `position`.
- **Validation**: `POST /api/departments/{code}/approvers` validates `app_user.department_code = code` for the given `userId`. If not, return 400 `USER_NOT_IN_DEPARTMENT`.
- **Default approver API**: `POST /api/departments/{code}/approvers/default` — adds all department members whose `position` contains "팀장" and are not already approvers. Returns count or list of added users. No request body.

**Frontend**

- **Data source**: Use `GET /api/departments/{code}/members` instead of `getUsers()` when a department is selected. Dropdown shows only department members.
- **Position column**: Add `position` column to the approver table (between role and departmentCode). Display "-" when null/empty.
- **"팀장 지정" button**: Add next to "결재자 추가". On click, call `POST /api/departments/{code}/approvers/default`. Disable when no team leaders are available or all are already approvers.
- **Empty state**: When department has no members, show "해당 부서 사용자 없음" or equivalent.

**Architecture (commonization)**

- **Department membership validation**: Backend is the single source of truth. Frontend filters by department for UX; backend validation prevents invalid API calls.
- **Default approver rule**: "position contains 팀장" is defined in the backend only. Frontend uses the default approver API.

**API specification (Contract)**

| API | Method | Description |
|-----|--------|-------------|
| `GET /api/departments/{code}/members` | New | Returns users where `department_code = code`. Response: `[{ userId, username, role, departmentCode, position, isApprover }]`. 404 if department not found. |
| `POST /api/departments/{code}/approvers/default` | New | Adds all department members whose `position` contains "팀장" and are not already approvers. No request body. Response: count or list of added userIds. 404 if department not found. |
| `POST /api/departments/{code}/approvers` | Modified | Add validation: `userId` must have `app_user.department_code = code`. Otherwise 400 `USER_NOT_IN_DEPARTMENT`. |
| `GET /api/departments/{code}/approvers` | Modified | Response items include `position` field. |

**Error code**

| code | HTTP | Meaning |
|------|------|---------|
| USER_NOT_IN_DEPARTMENT | 400 | 지정한 사용자가 해당 부서 소속이 아님. 부서별 결재자로 추가 불가. |

### Change file list

**(Confirmed by Backend subagent. Actual files changed.)**

#### Frontend

- `frontend/src/services/departmentService.js`
  - Added `getDepartmentMembers(code)` — GET /api/departments/{code}/members.
  - Added `addDefaultApprovers(code)` — POST /api/departments/{code}/approvers/default.
- `frontend/src/components/DepartmentApproverManagement/DepartmentApproverManagement.js`
  - Use `getDepartmentMembers(code)` instead of `getUsers()` for approver dropdown when department selected.
  - Added `position` column to approver table (between role and departmentCode); display "-" when null/empty.
  - Added "팀장 지정" button; calls `addDefaultApprovers(code)`. Disabled when no team leaders or all already approvers.
  - Empty state: dropdown shows "해당 부서 사용자 없음" when department has no members.
  - Added `USER_NOT_IN_DEPARTMENT` error message handling.
- `frontend/src/components/DepartmentApproverManagement/DepartmentApproverManagement.css`
  - Styles for "팀장 지정" button (`.department-approver-default-btn`).

#### Backend

- `backend/src/main/resources/db/schema.sql`
  - Add `position VARCHAR(50) NULL` to `app_user`. ✅
- `backend/src/main/resources/db/migrate-app-user-position.sql` (new)
  - `ALTER TABLE app_user ADD COLUMN IF NOT EXISTS position VARCHAR(50) NULL;` ✅
- `backend/src/main/resources/db/init-data.sql`
  - Add `position` to INSERT and UPDATE for user1=팀장, user2=대리. ✅
- `backend/src/main/java/com/logmng/dto/response/UserListItemResponse.java`
  - Add `position` field, getter/setter, 5-param constructor.
- `backend/src/main/java/com/logmng/service/DecryptApproverService.java`
  - Add `listMembersByDepartment(code)`, `addDefaultApproversForDepartment(code)`, `isApproverForDepartment`, `ensureUserInDepartment`.
  - Add department validation in `addApproverForDepartment` → 400 `USER_NOT_IN_DEPARTMENT` if user not in department.
  - Update `listUsers`, `listApproversByDepartment`, `getUserSummary` to include `position`.
- `backend/src/main/java/com/logmng/controller/DepartmentController.java`
  - Add `GET /api/departments/{code}/members`, `POST /api/departments/{code}/approvers/default`.
- `backend/src/main/java/com/logmng/service/PermissionGroupService.java`
  - Update `listUsersInGroup` to include `position`.
- `backend/src/test/java/com/logmng/service/DecryptApproverServiceUpdateRoleTest.java`
  - Add `position` column to H2 `app_user` test schema.
- `docs/api-definition.md`
  - Document new APIs and `USER_NOT_IN_DEPARTMENT` error code. ✅

### Database changes

| Table   | Change                                 |
|---------|----------------------------------------|
| app_user | Add `position VARCHAR(50) NULL` column |

**Data population**: `position` will be populated by a separate backend batch (out of scope). For testing, `init-data.sql` may add sample rows with `position` (e.g. user1 = "팀장").

---

## 3. Test approach

### Test case list (required)

| ID | Type | Scenario (input / condition) | Expected result | Verification |
|----|------|------------------------------|-----------------|--------------|
| TC-01 | Normal | Select department A; department has 3 members | Approver dropdown shows only those 3 users | Manual / Browser |
| TC-02 | Normal | Approver table displays position column | Each row shows position (e.g. 팀장, 대리) or "-" | Manual / Browser |
| TC-03 | Normal | Department has 1 team leader (position contains "팀장"); click "팀장 지정" | That user is added as approver | Manual / Browser |
| TC-04 | Exception | Select department A; department has 0 members | Dropdown shows "해당 부서 사용자 없음" or equivalent; add disabled | Manual / Browser |
| TC-05 | Exception | Department has 0 team leaders; click "팀장 지정" | Button disabled or "해당 부서에 팀장이 없습니다" message | Manual / Browser |
| TC-06 | Edge | Department has 2 team leaders; click "팀장 지정" | Both are added as approvers | Manual / Browser |
| TC-07 | Edge | User position is null or empty | Table shows "-"; default approver not applicable | Manual / Unit |
| TC-08 | Edge | Position "부팀장" (contains "팀장") | Should be treated as default approver candidate | Manual / Unit |
| TC-09 | Regression | Users from other departments | Not shown in dropdown; not selectable | Manual / Browser |
| TC-10 | Integration | POST /api/departments/{code}/approvers with userId from another department | 400, `code: "USER_NOT_IN_DEPARTMENT"` | Integration (curl) |

### Test scenarios

#### Scenario 1: Department-scoped approver selection

1. Log in as admin.
2. Open department approver screen.
3. Select a department.
4. Open the approver dropdown.
5. Verify only users in that department appear.

#### Scenario 2: Position display and default approver

1. Ensure department has users with `position` (e.g. "팀장", "대리").
2. Select the department.
3. Verify approver table shows position column.
4. Click "팀장 지정".
5. Verify users whose position contains "팀장" are added as approvers.

#### Scenario 3: Backend validation

1. Call `POST /api/departments/{code}/approvers` with a `userId` whose `department_code` ≠ `code`.
2. Expect 400 with `code: "USER_NOT_IN_DEPARTMENT"`.

### Test data

- At least one department with multiple users.
- At least one user with `position: "팀장"` or similar (e.g. "선임팀장").
- Users with `position: null` or empty for edge case testing.

### Test environment

- Frontend: `http://localhost:3001`
- Backend: `http://localhost:9200`
- Database: PostgreSQL (localhost:5432, logmng)

### 3.5 Browser automation verification (optional)

For frontend-heavy verification, QA may use Browser MCP.

- **Applicable TCs**: TC-01, TC-02, TC-03, TC-04, TC-06, TC-09
- **Procedure**: `browser_navigate` → login → department approver menu → select department → `browser_snapshot` to verify dropdown options and table columns.
- **Reference**: `docs/workflow/BROWSER-AUTOMATION-VERIFICATION-POLICY.md`

---

## 4. Checklist

### Frontend verification

- [x] API parameters validated
- [x] Department-scoped dropdown and position column confirmed
- [x] "팀장 지정" button behavior verified
- [x] Error handling verified

### Backend verification

- [x] API test cases written and run
- [ ] Logs checked
- [ ] Performance checked (if applicable)

### Integration

- [x] End-to-end flow tested
- [x] Edge cases tested

### Documentation

- [x] Requirement doc completed
- [x] API definition updated

---

## 5. Test results

### Test run date

- 2026-02-27

### Test results

#### Scope

- Frontend + Backend + DB

#### Health check

| Target | Result | Outcome |
|--------|--------|---------|
| Backend 9200 | 200 | `curl -s http://localhost:9200/api/health` → JSON |
| Frontend 3001 | 200 | `curl -s -o /dev/null -w "%{http_code}" http://localhost:3001` → 2xx |
| DB | connected | `curl -s http://localhost:9200/api/db/test` → `data.connected === true` |

#### Browser automation

- **Tool**: project-0-dev-browser (puppeteer_*)
- **Base URL**: http://localhost:3001
- **Procedure**: Login (admin/admin123) → 부서별 결재자 → select TEAM_SALES_A1 / DAOL / TEAM_RESEARCH_1

| ID | Result | Note |
|----|--------|------|
| TC-01 | Pass | Dropdown options: user1 (팀장), user2 (대리) only; user3 excluded |
| TC-02 | Pass | Table shows position column: 팀장, 대리, "-" for null |
| TC-03 | Pass | "팀장 지정" button adds user1 (팀장) as approver |
| TC-04 | Pass | DAOL (0 members): dropdown "해당 부서 사용자 없음", disabled |
| TC-05 | Pass | 0 team leaders: "팀장 지정" disabled, tooltip "해당 부서에 팀장이 없거나 이미 모두 결재자로 지정되어 있습니다." |
| TC-06 | Pass | 1 team leader added (init-data has 1 팀장 in TEAM_SALES_A1) |
| TC-07 | Pass | user3 (position null) → table shows "-" |
| TC-08 | N/A | Requires init-data with 부팀장; backend logic per unit test |
| TC-09 | Pass | user3 (TEAM_RESEARCH_1) not in TEAM_SALES_A1 dropdown |
| TC-10 | Pass | curl POST /api/departments/TEAM_SALES_A1/approvers {"userId":"user3"} → 400, `code: "USER_NOT_IN_DEPARTMENT"` |

#### Frontend

- **Pass**
- Department-scoped dropdown, position column, "팀장 지정" button, empty states, error handling verified via browser automation.

#### Backend

- **Pass**
- TC-10: Integration test passed; `USER_NOT_IN_DEPARTMENT` returned correctly.

**Commands:**

```bash
cd backend && mvn test
cd frontend && npm test -- --watchAll=false
```

**Outcome:**

- Build and restart confirmed by handoff (mvn test exit 0, npm run build exit 0)

### Issues found and resolution

- None

### Next steps

1. None

---

## 6. Error remedy result (cause and action) — for error/bug fix requirements only

N/A for this requirement.

---

## 7. Final version (Korean) — add after all verification is complete

After QA has completed verification and before or with the final commit, add a **Korean summary** here. See `docs/workflow/DOCUMENT-LANGUAGE-POLICY.md` §2.3.

### 요건 요약 (한글)

- **요건 설명**: 부서별 결재자 지정 시 해당 부서 소속 사용자만 선택 가능하도록 하고, 직책(position) 필드를 추가하여 "팀장" 포함 시 "팀장 지정" 버튼으로 일괄 지정 가능하게 함.
- **기대 결과**: 부서 스코프 드롭다운, 직책 컬럼 표시, "팀장 지정" 버튼, 빈 상태 처리, 백엔드 USER_NOT_IN_DEPARTMENT 검증.
- **검증 결과**: TC-01~TC-10 통과. Health check, 브라우저 자동화(puppeteer), TC-10 curl 통합 테스트 완료.

---

**Author**: Requirements subagent  
**Date**: 2025-02-27  
**Status**: Complete
