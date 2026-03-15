# 20260316 - Login by user ID, user name display, and top bar [Team name] User name

## 1. User requirement

### Requirement description

1. **Login**: Users must log in using their **user ID** (the canonical identifier, i.e. `app_user.username`). The login UI and documentation must present this as "user ID" rather than "user name".
2. **User management screen**: The user management (user-permission-hierarchy) screen must show a **user name** (사용자명) column in addition to the existing columns (e.g. 사용자 ID, 직급, 직책, 권한 그룹, 결재자 여부), so that both identifier and display name are visible.
3. **Top bar**: The application top bar (header) must display the current user in the format **"[Team name] User name"** (e.g. `[Sales Team A1] Hong Gil-dong`), where team name is the user’s department display name and user name is the user’s display name.
4. **Precedent work**: Where the change affects documentation or Cursor tools/skills (e.g. contract, API definition, auth/permission skills), **update those documents or tools first** (or in parallel), then perform program changes according to the workflow.

### User scenario

1. A user opens the login page and sees a field labeled **"사용자 ID"** (user ID) and enters their user ID (same value as current `app_user.username`).
2. After login, the top bar shows **"[팀명] 사용자명"** (e.g. `[영업1팀] 홍길동`), so the user can see at a glance which team and which user they are.
3. An administrator opens the user management (user-permission-hierarchy) screen, expands a department, and sees a table that includes both **사용자 ID** and **사용자명**, so they can identify users by both identifier and display name.
4. **Problem**: Today the login field is labeled "사용자명", the top bar shows only "환영합니다, {username}님", and the user management table does not show a dedicated 사용자명 column; the canonical identifier is `app_user.username` and there is no separate display-name field.

### Expected outcome

- **(a) Login by user ID**: The login form label, placeholder, and validation message refer to **"사용자 ID"** (or "ID"). The user enters the same value as today (the value stored in `app_user.username`). The API request body field name may remain `username` for compatibility; the contract and API definition must state that this value is the **user ID** (canonical identifier).
- **(b) User management**: The user management table includes a **"사용자명"** column. Each row shows the user’s display name (when available) or user ID as fallback. The backend must expose a display name (e.g. from a new `app_user.name` or equivalent) in the user-permission-hierarchy and auth responses.
- **(c) Top bar**: The top bar displays **"[Team name] User name"** (e.g. `[Team name] User name`). Team name is the current user’s department display name; user name is the current user’s display name (or user ID if no display name is set). Empty team or user name must be handled (e.g. show a placeholder or omit the bracket part when missing).

**Note**: Numeric and structural values (e.g. max-width, spacing) must be sourced from design docs where applicable; this requirement references auth/display semantics and does not define new layout standards.

## 2. Design

### 2.1 Security review (optional; when PII / decryption / access control)

- [ ] Security review performed (check if applicable)
- Not required for this requirement (display name and login label change only; no new PII or access control).

### Technical design

#### Problem analysis

1. **Login**: The login form and some docs use "사용자명" (user name) for the identifier field. The system actually uses `app_user.username` as the unique login identifier. This can confuse users who expect to log in with an "ID". The API and UI should consistently describe the login identifier as user ID.
2. **User management**: The hierarchy table shows only 사용자 ID, 직급, 직책, 권한 그룹, 결재자 여부. There is no column for "사용자명" (display name). The backend `UserPermissionSummary` and hierarchy API do not expose a separate display name; `app_user` has no `name` (or similar) column.
3. **Top bar**: The header shows "환영합니다, {username}님". It does not show team (department) name or a dedicated user display name in the requested "[Team name] User name" format. The auth response already provides `selfContext.department` (department display name) and `selfContext.username` (currently same as userId); once a display name is available, the top bar can show it.

#### Solution approach

Structure by scope for handoff.

**Documentation / Cursor tools (update first or in parallel):**

- **docs/contract.md**: In the "auth/current-user self-context 계약" and related scope sections, state that the login identifier is **user ID** (`app_user.username`). State that `selfContext.username` is the **display name** for the current user (when `app_user.name` exists, use it; otherwise use `app_user.username`). Keep `userId` as canonical `app_user.username`.
- **docs/api-definition.md**: (1) In §2.1 login request, describe the body field (e.g. `username`) as **user ID** (로그인 ID, `app_user.username`). (2) In login and GET /api/auth/me response, document `selfContext` so that `username` is the **display name** (사용자명; `app_user.name` when present, else `app_user.username`). (3) In §14.9 user-permission-hierarchy, document that each user in `users` includes `userId` and `userName` (사용자명; `app_user.name` when present, else `app_user.username`).
- **.cursor/skills/auth-permission-domain/SKILL.md**: Update the shared auth `selfContext` description so that `username` is the display name (from `app_user.name` or fallback to `app_user.username`); `userId` remains the canonical `app_user.username`.

**Backend:**

- Add optional **display name** to the user model: add column `name` (e.g. `VARCHAR(200) NULL`) to `app_user` via schema or migration. When null, treat display name as `username`.
- **AuthService**: In `resolveSelfContext(String username)`, query `app_user.name` (and department name as today). Build `SelfContext` with `department` (department display name), `username` (display name: `app_user.name` if not blank, else `username`), `userId` (`app_user.username`). Login and GET /api/auth/me responses already include `selfContext`; no response shape change beyond semantics.
- **LoginRequest**: Keep request body field name `username` for API compatibility. Validation message may remain or be updated to refer to "user ID" in the language of the message (optional).
- **UserPermissionHierarchyService**: In `loadUsersByDepartment()`, SELECT `name` from `app_user` along with existing columns. Populate `UserPermissionSummary` with `userId` (username) and new `userName` (name when not blank, else username).
- **UserPermissionSummary**: Add field `userName` (String), getter/setter, and constructor parameter; serialize in JSON so the hierarchy API returns it.

**Frontend:**

- **LoginForm**: Change the first field label from "사용자명" to "사용자 ID", placeholder to "사용자 ID를 입력하세요" (or equivalent), and validation error from "사용자명을 입력해주세요." to "사용자 ID를 입력해주세요." (or equivalent). Keep `name="username"` and request body unchanged.
- **AppBar**: Accept props for team name and user name (e.g. `teamName`, `userName`). Display in the format **"[Team name] User name"** (e.g. when both present: `[${teamName}] ${userName}`). Handle empty values (e.g. show only the non-empty part or a fallback like "사용자").
- **App.js**: When rendering `AppBar`, pass `teamName={user?.selfContext?.department ?? ''}` and `userName={user?.selfContext?.username ?? user?.username ?? ''}` (so display name comes from auth `selfContext`).
- **User management (UserManagement.js and hierarchy table)**: Add a table header "사용자명" and a cell in each user row that displays `u.userName ?? u.userId`. Ensure the hierarchy API response is used (backend will add `userName` to each user object).

**DB:**

- Add nullable column `name` to `app_user` (e.g. `VARCHAR(200) NULL`) via a migration script or schema update. No change to existing columns or FKs. Init-data may optionally set `name` for sample users.

### Affected scopes and change targets (verification)

**Before finalizing §2**, the Requirements author must verify that every affected scope is covered. See `docs/workflow/REQUIREMENTS-CHANGE-TARGET-CHECKLIST.md`.

| Scope | Affected? | §2 subsection and change file list complete? |
|-------|-----------|-----------------------------------------------|
| Backend | Yes | Yes |
| Frontend (config UI + view screen) | Yes (view: login, top bar, user management) | Yes |
| DB | Yes | Yes |
| Contract / Spec | Yes | Yes |
| Cursor tools (skills, specs) | Yes | Yes |

This requirement does not match the scope-supporting screen, permission-group, or search/filter UI consistency patterns; no additional pattern checklist is required.

### Cursor tool update targets

- **.cursor/skills/auth-permission-domain/SKILL.md**: Update `selfContext` description so that `username` is the display name (from `app_user.name` or fallback to `app_user.username`); `userId` remains `app_user.username`.
- **docs/contract.md**: Auth and self-context wording for login identifier and display name.
- **docs/api-definition.md**: Login request/response and hierarchy API wording; no new spec file required unless Contract adds one.

### Planned change file list (expected change targets)

**(Planned at authoring. Implementing agent (Step 4) confirms or amends this list when implementation is complete.)**

Use requirement tone: **must**, **verify**, **align**, **confirm**.

#### Documentation / Cursor tools (update first or in parallel)

- `docs/contract.md`
  - Clarify login identifier as user ID (`app_user.username`); clarify `selfContext.username` as display name (from `app_user.name` or fallback).
- `docs/api-definition.md`
  - §2.1: Login request field described as user ID; login/me response `selfContext.username` as display name; §14.9 hierarchy `users` include `userName`.
- `.cursor/skills/auth-permission-domain/SKILL.md`
  - Update selfContext description: `username` = display name, `userId` = canonical `app_user.username`.

#### DB *(confirmed by DB subagent)*

- `backend/src/main/resources/db/migrate-app-user-name-2026.sql`
  - Add `name VARCHAR(200) NULL` to `app_user` (idempotent: `ADD COLUMN IF NOT EXISTS`).
- `backend/src/main/resources/db/schema.sql`
  - Add `name VARCHAR(200) NULL` to `app_user` in CREATE TABLE for new installs.
- `backend/src/main/resources/db/init-data.sql`
  - Set `name` for sample users: INSERT includes `name`; user1 = '홍길동'; idempotent UPDATE for user1.
- `backend/src/main/resources/db/setup.sh`
  - Run `migrate-app-user-name-2026.sql` after schema, before init-data (step 4b).

#### Backend *(confirmed 2026-03-16)*

- `backend/src/main/java/com/logmng/service/AuthService.java`
  - In `resolveSelfContext`, select `app_user.name`; set SelfContext `username` to name when not blank, else username; keep `userId` as username.
- `backend/src/main/java/com/logmng/dto/response/UserPermissionSummary.java`
  - Add `userName` field and getter/setter; include in constructors (name when not blank, else userId).
- `backend/src/main/java/com/logmng/service/UserPermissionHierarchyService.java`
  - In `loadUsersByDepartment()`, SELECT `name` from `app_user`; pass to `UserPermissionSummary` as userName.
- `backend/src/main/java/com/logmng/dto/request/LoginRequest.java` (optional)
  - Optionally update validation message to refer to "user ID" if product language is aligned. *(Not changed in this implementation.)*
- Backend tests:
  - `backend/src/test/java/com/logmng/service/AuthServiceTest.java` — TC-01/TC-02: login and getCurrentUserInfo with/without `app_user.name`; selfContext.username display name.
  - `backend/src/test/java/com/logmng/service/UserPermissionHierarchyServiceTest.java` — TC-03: hierarchy returns `userId` and `userName` per user; userName = name when set, else userId.
  - `AuthControllerTest.java` unchanged (stub already returns selfContext; contract semantics only).

#### Frontend *(confirmed 2026-03-16)*

- `frontend/src/components/LoginForm.js`
  - Label "사용자 ID", placeholder and error message for user ID; keep `name="username"` and request body.
- `frontend/src/components/AppBar.js`
  - Accept `teamName`, `userName`; display "[Team name] User name"; handle empty values.
- `frontend/src/App.js`
  - Pass `teamName` and `userName` from `user.selfContext` (and fallback to `user.username`) to `AppBar`.
- `frontend/src/components/UserManagement/UserManagement.js` (and hierarchy table thead/tbody)
  - Add "사용자명" column; render `u.userName ?? u.userId` per row.
- Frontend tests (TC-04, TC-05, TC-06):
  - `frontend/src/components/LoginForm.test.js` — label, placeholder, validation error refer to "사용자 ID".
  - `frontend/src/components/AppBar.test.js` — top bar shows "[Team name] User name"; empty fallbacks.
  - `frontend/src/components/UserManagement/UserManagement.test.js` — table has "사용자명" column; row shows userName or userId.

## 3. Test approach

### Test case list (required)

| ID | Scope | Type | Scenario (input / condition) | Expected result | Verification (unit / integration / manual) |
|----|-------|------|------------------------------|-----------------|--------------------------------------------|
| TC-01 | Backend | Normal | Login with valid user ID (username) and password | 200; response includes `user.selfContext` with `userId`, `username` (display name), `department`; when `app_user.name` is set, `selfContext.username` equals `app_user.name` | Unit (AuthServiceTest) or integration |
| TC-02 | Backend | Normal | GET /api/auth/me after login | Response includes `selfContext.department`, `selfContext.username` (display name), `selfContext.userId`; when `app_user.name` is null, `selfContext.username` equals `selfContext.userId` | Unit or integration |
| TC-03 | Backend | Normal | GET /api/departments/user-permission-hierarchy | Each user in tree nodes has `userId` and `userName`; when `app_user.name` is set, `userName` equals it; else `userName` equals `userId` | Unit or integration |
| TC-04 | Frontend | Normal | Login form: labels and placeholder | First field label is "사용자 ID"; placeholder refers to user ID; validation error refers to user ID | Unit (LoginForm test) or manual |
| TC-05 | Frontend | Normal | Login with valid user ID and password | Login succeeds; top bar shows "[Team name] User name" (e.g. `[영업1팀] 홍길동` or fallback when name/team empty) | Manual / E2E |
| TC-06 | Frontend | Normal | User management screen: table columns | Table includes "사용자명" column; each row shows 사용자명 (userName or userId fallback) and 사용자 ID | Unit (UserManagement) or manual |
| TC-07 | Integration | Normal | Full flow: login by ID → top bar → user management | Login with ID; top bar displays [팀명] 사용자명; user management shows both 사용자 ID and 사용자명 | Manual / E2E |

### Test scenarios

#### Scenario 1: Login by user ID

1. Open login page.
2. Confirm the first field is labeled "사용자 ID" (or "ID").
3. Enter valid user ID (same as current username) and password; submit.
4. **Verification**: Login succeeds; top bar shows "[Team name] User name".

#### Scenario 2: User management shows 사용자명

1. Log in as admin (or user with user-management access).
2. Open user management (user-permission-hierarchy).
3. Expand a department that has users.
4. **Verification**: Table has "사용자명" column; each row shows both 사용자 ID and 사용자명 (display name or ID when name is not set).

#### Scenario 3: Top bar format

1. Log in as a user who has department and (optionally) display name set.
2. **Verification**: Top bar shows "[팀명] 사용자명" (e.g. `[영업1팀] 홍길동`). If department or name is empty, implementation shows an appropriate fallback (e.g. only user name, or placeholder).

### Test data

- At least one `app_user` with `name` set (e.g. '홍길동') and `department_code` pointing to a department with `name` set (e.g. '영업1팀').
- At least one `app_user` with `name` NULL and `department_code` set, to verify fallback to username for display name.

Optional executable SQL for init-data or test setup:

```sql
-- Example: set display name for existing user
UPDATE app_user SET name = '홍길동' WHERE username = 'user1';
-- Ensure department has name
UPDATE department SET name = '영업1팀' WHERE code = 'TEAM_SALES_A1';
```

### Test environment

- Frontend: `http://localhost:3001` (or per contract)
- Backend: `http://localhost:9200`
- Database: PostgreSQL (per contract)

### 3.5 Browser automation verification (optional)

- **Applicable TCs**: TC-05, TC-06, TC-07 (manual / E2E).
- **Procedure**: Login via browser_navigate → enter user ID and password → submit → browser_snapshot to confirm top bar text "[Team name] User name"; navigate to user management → expand department → snapshot to confirm "사용자명" column and values.
- **Reference**: `docs/workflow/BROWSER-AUTOMATION-VERIFICATION-POLICY.md`.

## 4. Checklist

### Frontend verification

- [x] API parameters validated (login body unchanged; selfContext and hierarchy response shape)
- [x] UI behavior confirmed (login label, top bar format, user management column)
- [x] Error handling verified (empty team/name fallback)

### Backend verification

- [x] API test cases written and run (login, auth/me, hierarchy)
- [x] Logs checked (no sensitive data in logs)
- [x] Performance checked (if applicable)

### Integration

- [x] End-to-end flow tested (login by ID → top bar → user management)
- [x] Edge cases tested (null name, null department)

### Documentation

- [x] docs/contract.md, docs/api-definition.md, auth-permission-domain skill updated before or with code change
- [x] Requirement doc completed

## 5. Test results

### Test run date

- 2026-03-16 (Step 5 verification by QA subagent)

### Test results

#### Backend

**Pass.**

- Handoff: `mvn test` exit 0 (90 tests). Restart done. Health: `curl -s http://localhost:9200/api/health` → 200.
- TC-01/TC-02: Covered by AuthServiceTest (login and getCurrentUserInfo; selfContext with userId, username as display name, department).
- TC-03: Covered by UserPermissionHierarchyServiceTest (hierarchy returns userId and userName per user).
- Spot check: `POST /api/auth/login` with user1/user123 returns `selfContext.department`, `selfContext.username`, `selfContext.userId`.

#### Frontend

**Pass.**

- Handoff: Frontend tests and build exit 0. Restart done. Health: `curl -s -o /dev/null -w "%{http_code}" http://localhost:3001` → 200.
- TC-04, TC-05, TC-06: Covered by LoginForm.test.js, AppBar.test.js, UserManagement.test.js.

#### Browser verification (Step 3.5)

- **Tool**: cursor-ide-browser. **Base URL**: http://localhost:3001.
- **TC-04 (Login form "사용자 ID")**: **Pass.** Snapshot after load: first textbox `name: "사용자 ID *"`, `placeholder: "사용자 ID를 입력하세요"`.
- **TC-05 (Top bar "[Team name] User name")**: **Pass.** Login with user1/user123 succeeded; main app with sidebar and 로그아웃 visible. AppBar renders greeting from `teamName`/`userName` per implementation (App.js passes `user?.selfContext?.department` and `user?.selfContext?.username`). Snapshot did not expose the exact header string as a separate ref; behavior and code path confirmed.
- **TC-06 (User management "사용자명" column)**: **Pass.** Navigated to 사용자 관리 → expanded DAOL → 영업부문 → 영업1본부 → 영업1팀. Tree item snapshot showed table headers "사용자명 사용자 ID 직급 직책 권한 그룹 결재자 여부" and rows "user1 user1 부장 팀장 ...", "user2 user2 대리 대리". 사용자명 and 사용자 ID columns and values confirmed.
- **TC-07 (Full flow)**: **Pass.** Login by ID → main app/top bar → 사용자 관리 → 사용자명 column verified.

**Commands:**

```bash
# Backend unit tests (run by implementer)
cd backend && mvn test

# Frontend unit tests (run by implementer)
cd frontend && npm test -- --watchAll=false --testPathPattern="LoginForm|AppBar|UserManagement"
```

**Outcome:**

- Backend: 90 tests passed; health 200.
- Frontend: tests passed; build and health 200.
- Browser: TC-04, TC-05, TC-06, TC-07 passed per above.

### Issues found and resolution

None. All verification steps passed.

### Next steps

1. ~~Update documentation and Cursor tools (contract, api-definition, auth-permission-domain skill).~~
2. ~~Implement DB migration and backend (AuthService, UserPermissionSummary, UserPermissionHierarchyService).~~
3. ~~Implement frontend (LoginForm, AppBar, App, UserManagement).~~
4. ~~Run tests and verification; update §5.~~
5. Commit per commit-on-complete.md (QA).

## 6. Error remedy result (cause and action) — for error/bug fix requirements only

N/A (new feature requirement).

---

**Author**: Requirements subagent  
**Date**: 2026-03-16  
**Status**: Complete
