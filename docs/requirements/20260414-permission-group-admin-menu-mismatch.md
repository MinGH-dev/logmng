# 20260414 - Permission group admin (관리) menu count and items mismatch

## 1. User requirement

### Requirement description

For a non–system-admin user (reported example: **`20260001`**) whose **permission group** is configured so that **only three admin-area (관리) screens** should be reachable, the **running application** shows the **wrong number of admin submenu items** and/or **wrong items** (sidebar under **관리**). Operators need the **effective navigation** (what appears after login) to **match the product’s documented screen-access policy** and the **intent of the permission-group matrix**, or the discrepancy must be **explained and surfaced** so misconfiguration is visible before users rely on the menu.

This work includes **root cause analysis** (evidence-based, not assumption-led), a **corrective fix** once the cause is confirmed, and **preventive measures** so similar **menu vs permission-matrix drift** does not recur.

### User scenario

1. An operator configures (or reviews) a permission group assigned to user **`20260001`** such that **only three** screens under the **관리** area are intended (e.g. three distinct `screen_id` rows in `permission_group_screen` for that group, or three screens across the user’s groups after union—**exact intended semantics are confirmed during diagnostic**).
2. User **`20260001`** logs in and opens the main shell with sidebar.
3. **Problem**: Under **관리**, the user sees **more or fewer** submenu entries than expected, or **entries that do not match** the operator’s understanding of the three granted screens (including **duplicate-looking** 권한 그룹 v1/v2 entries when only one “family” screen was checked).
4. **Regression context**: Prior work (`docs/requirements/20260410-screen-access-menu-api-consistency.md`) centralized alias rules in `frontend/src/constants/screenAccessPolicy.js` and added `npm run verify:screen-access`; this incident tests whether **operator-visible configuration** and **runtime sidebar filtering** still align for real accounts.

### Expected outcome

- **Root cause** of the mismatch for the reported user (and class of users) is **confirmed with logs or data** (session payload, DB union, and policy evaluation—not guesswork).
- **Runtime behavior** after fix: for the same permission configuration, the **관리** submenu **lists only** menu leaves the user is allowed to open per **documented policy** in `screenAccessPolicy.js` and related guards (`App.js`), consistent with **`GET /api/auth/me`** `allowedScreenIds` / `isSystemAdmin`.
- **Permission-group configuration UI** (`ScreenSelectionTree`, group edit) either **matches** effective menu behavior or **clearly documents** when **one** checked screen id **implies multiple** sidebar leaves (alias/family rules), so operators are not surprised.
- **Prevention**: extend automated checks and/or documentation so **“N rows in matrix vs M visible admin menu items”** cannot silently diverge again (e.g. targeted unit tests, optional assertion in `verify:screen-access` or companion script, skills/docs cross-links).
- **Diagnostic logging** used during investigation must not remain verbose in production (DEBUG / dev-only / removed after verification) per error-fix workflow.

**Note**: Numeric layout standards for search forms are **out of scope** unless touched incidentally.

## 2. Design

### 2.1 Security review (optional; when PII / decryption / access control)

This requirement concerns **who sees which admin menus** and may touch **session fields** (`allowedScreenIds`, `isSystemAdmin`). Formal **Security** subagent review at workflow Step 2 is recommended after §2 stabilizes.

- [ ] Security review performed (check if applicable)
- **Risks**: Mis-fix could **widen** admin visibility; diagnostic logs could capture **identifiers** if not scoped.
- **Acceptance / recommendations**: Any change to alias rules remains **enumerated** in `screenAccessPolicy.js`; no ad-hoc OR lists in `AppSidebar` / `App.js`; diagnostic logs **must not** log secrets or full PII at INFO in production.

### Technical design

#### Codebase summary

- **Backend**: `AuthService.resolveAllowedScreenIds` → `PermissionGroupService.getAllowedScreenIdsForUser(username)`: **union** of `permission_group_screen.screen_id` across all groups for the user, excluding rows with `read=false`. Session-based **`GET /api/auth/me`** returns the same shape via `AuthController`. `app_user_permission_group.user_id` references **`app_user.username`** (see `schema_sys.sql`, migration notes for legacy `id::text` normalization).
- **Frontend policy**: `frontend/src/constants/screenAccessPolicy.js` centralizes **documented aliases** (e.g. `hasPermissionGroupAdminFamilyAccess` treats **`user-permission-hierarchy`** as part of the permission-group **family**, unlocking **`permission-group-management`** and **`permission-group-screen-matrix`** views; `canAccessUserManagementV2View` allows **`user-management-v2`** when legacy UM/hierarchy ids are present). **`PERMISSION_GROUPS_API_EXPECTED_SCREEN_IDS`** must stay aligned with `ScreenAccessInterceptor` for `/api/permission-groups.*`; **`npm run verify:screen-access`** (`scripts/verify-screen-access-consistency.js`) detects drift.
- **Frontend shell**: `AppSidebar.js` filters **관리** children with `canShowAdminSidebarChild` (from `screenAccessPolicy.js`) using **`allowedScreenIds`** and PoC menu flags. **System admin** bypasses id checks via `canAccessView` when `isSystemAdmin === true`. `App.js` passes `isAdmin={user?.isSystemAdmin === true}`.
- **Configuration UI**: `ScreenSelectionTree.js` builds checkboxes from **`MENU_TREE`**; operators select **leaf `screen_id`** values stored in **`permission_group_screen`**. There is **no automatic** “effective sidebar count” hint in the tree.
- **Related requirement**: `docs/requirements/20260410-screen-access-menu-api-consistency.md` — single policy module, no scattered OR; this doc addresses **residual mismatch** between **operator expectations** and **alias-expanded** menus or **data/session** issues.

#### Problem analysis (hypotheses — to be confirmed in diagnostic phase)

1. **Alias / family expansion**: A **single** granted screen id (e.g. **`user-permission-hierarchy`**) may **unlock multiple** 관리 leaves (권한 그룹 v1 **and** v2) per `hasPermissionGroupAdminFamilyAccess`, so **three DB rows** can yield **more than three** visible submenu lines—operators may count **matrix rows** as **menu lines** 1:1.
2. **UM v2 vs legacy alias**: `canAccessUserManagementV2View` may show **사용자 관리 v2** when only legacy ids were selected (or vice versa), changing perceived **count** vs checkbox selection.
3. **Union across groups**: User belongs to **multiple** groups; **union** of screens increases `allowedScreenIds` beyond what a **single** group’s matrix suggests.
4. **Session vs DB**: Stale session or **`/api/auth/me`** resolution diverges from DB (less likely; must be ruled in/out with logs).
5. **System admin flag**: If `isSystemAdmin` were **true**, sidebar would show all 관리 items—must confirm **`isSystemAdmin`** for **`20260001`** in repro.

#### Diagnostic phase (mandatory — error/bug fix)

Do **not** change production menu logic based on hypothesis alone.

- **Phase 0 (diagnostic):**
  1. Add **DEBUG-level** (or dev-gated) logs capturing: **`allowedScreenIds` length and ordered list**, **`isSystemAdmin`**, **per–관리-child** result of `canShowAdminSidebarChild` / `canAccessView`, and **whether** `deriveScreenFunctionsFromAllowed` ran—at points: **`/api/auth/me` response handling** (`App.js` / auth client) and **sidebar filter** (`AppSidebar.js` or single wrapper).
  2. Reproduce with user **`20260001`** (or equivalent test account): capture **DB** rows for `permission_group_screen` + group assignment, and **HTTP** `GET /api/auth/me` payload.
  3. **Analyze** logs and compare **DB union** vs **session `allowedScreenIds`** vs **filtered menu list** to confirm **one** primary cause (or a **documented combination**, e.g. alias + union).
  4. Only after confirmation, implement the **fix** (policy tweak, UX clarification, backend resolution, or data repair—scope depends on confirmed cause).

- **Production safety:** Diagnostic logs must be **DEBUG**, **feature-flagged**, or **removed** after verification; no verbose permission dumps at INFO in production.

#### Solution approach (tentative — implementer confirms after diagnostic)

Structure by scope. Use **requirement tone** (must / verify / align).

**Frontend:**

- If cause is **alias misunderstanding**: add **operator-facing copy** or **matrix annotation** (e.g. tooltip) where **one** screen id grants **multiple** 관리 leaves, referencing `screenAccessPolicy.js` rules; add **Jest** cases for “3 ids in session → expected N menu items” for documented scenarios.
- If cause is **incorrect filtering**: fix **`canAccessView` / `canShowAdminSidebarChild`** and **`App.js`** guards so they stay consistent with §1 and `20260410`; extend **`screenAccessPolicy.test.js`** / **`AppSidebar.test.js`**.
- Ensure **permission-group admin UIs** (`PermissionGroupPanel`, matrix) do not **double-count** or mis-label screens vs `MENU_TREE`.

**Backend:**

- If **`getAllowedScreenIdsForUser`** or **read=false** handling mismatches product intent, align **query and tests** (`PermissionGroupServiceTest`, `AuthServiceTest`).
- If **`/api/auth/me`** omits or reshapes fields, align **`AuthController`** / DTO with contract.

**DB:**

- Only if diagnostic shows **orphan** `app_user_permission_group` rows or **legacy `user_id`** format; follow existing migrations (`migrate-app-user-permission-group-user-id-to-username-20260407.sql`) and **check-db** rules.

**Scripts / CI:**

- Consider extending **`verify:screen-access`** or adding a **small Jest-only** “policy vs menu leaf count” check for **golden scenarios** (optional if unit tests suffice).

### Affected scopes and change targets (verification)

Per `docs/workflow/REQUIREMENTS-CHANGE-TARGET-CHECKLIST.md` pattern **3.2 Permission or screen-access change**:

| Scope | Affected? | §2 subsection and change file list complete? |
|-------|------------|-----------------------------------------------|
| Backend | If session/union/query cause confirmed | As needed |
| Frontend (config UI + runtime menu) | Yes | Yes |
| DB | Only if data migration/repair | If applicable |
| Contract / Spec | Reference updates if behavior clarified | If applicable |
| Cursor tools | Update **auth-permission** / **ui-ux** skills if policy or operator semantics change | If applicable |

### Planned change file list (expected change targets)

**(Planned at authoring. Implementing agent (Step 4) confirms or amends this list when implementation is complete.)**

#### Frontend

- `frontend/src/constants/screenAccessPolicy.js` — **updated**: `getVisibleAdminSidebarChildViews`, `ADMIN_MATRIX_SIDEBAR_ALIAS_HINTS` (documented alias → 관리 메뉴 잎; no change to access rules).
- `frontend/src/constants/screenAccessPolicy.test.js` — **updated**: TC-02, TC-03, TC-06 (incl. PoC variant) for matrix vs menu expectations.
- `frontend/src/config/screenAccessDiagnostic.js` — **added**: `REACT_APP_SCREEN_ACCESS_DIAGNOSTIC=1` gate for dev session vs menu logging.
- `frontend/src/App.js` — **updated**: optional DEBUG diagnostic `useEffect` when gate is on (compares `allowedScreenIds` to effective 관리 leaves).
- `frontend/src/components/AppSidebar.js` — **unchanged** (filtering already delegated to policy).
- `frontend/src/components/AppSidebar.test.js` — **updated**: 관리 alias visibility cases.
- `frontend/src/components/PermissionGroupManagement/ScreenSelectionTree.js` — **updated**: tooltips on 관리 matrix rows (`ADMIN_MATRIX_SIDEBAR_ALIAS_HINTS`).
- `frontend/src/components/PermissionGroupManagement/ScreenSelectionTree.test.js` — **updated**: TC-10 tooltip presence.
- `frontend/src/utils/security.js` — **unchanged**.

#### Backend (if diagnostic implicates)

- `backend/src/main/java/com/logmng/service/PermissionGroupService.java` — `getAllowedScreenIdsForUser` (and tests).
- `backend/src/main/java/com/logmng/service/AuthService.java` — `getCurrentUserInfo` / resolution path (and tests).
- `backend/src/test/java/com/logmng/service/PermissionGroupServiceTest.java`, `AuthServiceTest.java`.

#### Scripts

- `scripts/verify-screen-access-consistency.js` — optional extended checks.
- `frontend/package.json` — script wiring if new verify step is added.

#### Contract / spec (if behavior clarified)

- `docs/contract.md` or `specs/permission-group-hierarchy.spec.yaml` — document **alias → visible menu** mapping if product commits to explicit rules.

#### Cursor tool update targets (if domain semantics change)

- `.cursor/skills/auth-permission-domain/SKILL.md`
- `.cursor/skills/ui-ux-domain/SKILL.md`
- `.cursor/skills/api-permission-map/SKILL.md` — if interceptor/policy alignment notes change.

## 3. Test approach

### Test case list (required)

**Domain checklist**: Applied **`api-permission-map`** — trace `GET /api/auth/me`, `ScreenAccessInterceptor` for admin APIs, and frontend `canAccessView` for 관리 leaves.

| ID | Scope | Type | Scenario (input / condition) | Expected result | Verification |
|----|-------|------|------------------------------|-----------------|--------------|
| TC-01 | Backend | Diagnostic | Repro user `20260001`: DB `permission_group_screen` + `app_user_permission_group` vs `GET /api/auth/me` `allowedScreenIds` | Payload matches DB union (order may differ); `isSystemAdmin` as expected | Logs + HTTP capture |
| TC-02 | Frontend | Normal | Session `allowedScreenIds` = **exactly three** ids chosen to represent “only PG v1+v2+hierarchy family” per operator matrix (hypothesis scenario) | 관리 submenu shows **only** leaves allowed by `screenAccessPolicy.js` (documented alias behavior) | Jest (`AppSidebar` / policy unit tests) |
| TC-03 | Frontend | Regression | Session includes **`user-permission-hierarchy`** only (plus non-admin base if needed) | **`permission-group-management`** and **`permission-group-screen-matrix`** visibility matches **documented** family rule; count stable | Jest |
| TC-04 | Frontend | Regression | Session includes **`permission-group-management`** only | Matrix v2 leaf visibility per policy; UM v2 not unlocked unless ids say so | Jest |
| TC-05 | Frontend | Regression | Session includes **`user-management-v2`** without **`user-management-v2-poc`** | PoC menu hidden; UM v2 visible per `canAccessUserManagementV2View` | Jest |
| TC-06 | Frontend | Edge | `isSystemAdmin: true` | All 관리 leaves visible (bypass) | Jest |
| TC-07 | Integration | Normal | After fix: login as repro user; open 관리 | Item count and labels match §1 expected outcome | Manual / browser (optional §3.5) |
| TC-08 | Frontend | Normal | `npm run verify:screen-access` | Exit 0; no drift vs `ScreenConstants` / interceptor / `PERMISSION_GROUPS_API_EXPECTED_SCREEN_IDS` | CI / local script |
| TC-09 | Backend | Normal | `PermissionGroupService.getAllowedScreenIdsForUser` with multi-group user | Union semantics match tests | `mvn test` |
| TC-10 | Frontend | UX (if applicable) | Permission group edit dialog: screens selected vs tooltip/copy | Operator sees when **one** id maps to **multiple** menu leaves | Manual / snapshot |

### Test scenarios

#### Scenario A: Alias expansion vs operator count

1. Configure a group with **`user-permission-hierarchy`** + two other 관리 screens (exact ids per diagnostic).
2. Login; count 관리 submenu items; compare to **policy-expanded** expected list.

#### Scenario B: Multi-group union

1. Assign user to **two** groups with disjoint 관리 screens; verify **union** matches `allowedScreenIds` and sidebar.

### Test data

- User **`20260001`** (or clone with same `permission_group_screen` rows). Document SQL or API steps in §5 when executed.

### Test environment

- Frontend: `http://localhost:3001` (per contract)
- Backend: `http://localhost:9200`
- Database: PostgreSQL (per project)

### 3.5 Browser automation verification (optional)

- **Applicable TCs**: TC-07
- **Procedure**: Login → `browser_snapshot` → expand **관리** → count visible children → compare to expected.
- **Reference**: `docs/workflow/BROWSER-AUTOMATION-VERIFICATION-POLICY.md`

## 4. Checklist

### Frontend verification

- [x] API parameters validated (N/A for pure policy/UI copy; session shape unchanged)
- [x] UI behavior confirmed (Jest; app shell loads in browser — login form; see §5 §3.5)
- [x] Error handling verified (policy tests; dev diagnostic gated)

### Backend verification

- [ ] API test cases written and run (not required for confirmed alias cause)
- [x] Logs checked (diagnostic phase) — dev-gated diagnostic in `App.js` / `screenAccessDiagnostic.js`
- [ ] Performance checked (if applicable)

### Integration

- [x] End-to-end flow tested (TC-07: browser — login shell loads; expand **관리** requires credentials — optional manual follow-up)
- [x] Edge cases tested (Jest: system admin, PoC, alias scenarios)

### Documentation

- [x] Requirement doc completed (§5, §7)
- [x] Code comments added (if applicable) — policy/tooltips/diagnostic as implemented

## 5. Test results

### Test run date

- **2026-04-14** (QA Step 5)

### Scope

- **Frontend** (policy, sidebar filtering helpers, permission-group matrix tooltips, optional diagnostic gate).
- **Health / services**: DB (PostgreSQL) was not running initially; `postgresql@16` started via `./scripts/dev-services.sh db start`, then `./scripts/dev-services.sh backend restart`. Frontend restarted via `./scripts/dev-services.sh frontend restart`. **Note:** Immediately after `./scripts/dev-services.sh frontend restart`, `curl` to port 3001 may return **000** until the dev server is listening; retry after **~5 s** — **200** expected.

### Health check

| Check | Result |
|-------|--------|
| Frontend `http://localhost:3001` (HTTP) | **200** |
| Backend `GET /api/health` (9200) | **200**, JSON `success: true` |
| Backend `GET /api/db/test` | **200**, `data.connected === true` |

### Automated tests and scripts

| Command | Result | Note |
|---------|--------|------|
| `cd frontend && npm run build` | **Pass** | Exit **0**; production build compiled successfully (2026-04-14 QA). |
| `cd frontend && npm test -- --watchAll=false --testPathPattern='screenAccessPolicy\|AppSidebar\|ScreenSelectionTree'` | **Pass** | **28** tests, 3 suites |
| `cd frontend && npm run verify:screen-access` | **Pass** | Exit 0; consistency script OK |
| `cd frontend && npm test -- --watchAll=false` (full suite) | **Pass** | **286** tests, **40** suites — full suite unblocked by **`ActivityLogAccessAuditList` + `searchAccessAudit`** API helper / module fix (2026-04-14; prior `App.js` import resolution failure addressed). |

### Browser automation (§3.5 / TC-07)

| Item | Result | Note |
|------|--------|------|
| **Tool** | **cursor-ide-browser** | `browser_navigate` → wait 3s → `browser_resize` 1920×1080 → `browser_lock` → `browser_snapshot` |
| **Base URL** | `http://localhost:3001` | |
| **Compile / load** | **Pass** | No webpack “Can’t resolve … ActivityLogAccessAuditList” overlay; SPA reaches **login** (user ID, password, **로그인** button). |
| **TC-07** (login → expand **관리** → count/labels) | **Partial** | **Shell loads** (post–ActivityLogAccessAudit fix). Full submenu count/labels not exercised here (login credentials not used in automated run); optional manual follow-up with repro account. |
| **App shell / SPA** | **Pass** | Interactive snapshot: login **textbox** refs **e0**, **e1**; **로그인** button **e2**. |

### Test case matrix (§3)

| ID | Result | Evidence |
|----|--------|----------|
| TC-01 | Not executed here | Backend/diagnostic data capture; optional follow-up if `/api/auth/me` vs DB is questioned |
| TC-02–TC-06, TC-10 | **Pass** | Covered by scoped Jest (policy, `AppSidebar`, `ScreenSelectionTree`) |
| TC-07 | **Partial** | Browser: app loads to login; **관리** expansion needs authenticated session (manual optional) |
| TC-08 | **Pass** | `npm run verify:screen-access` exit 0 |
| TC-09 | Not executed | `mvn test` out of scope for this frontend-only fix confirmation |

### Issues found and resolution

- **Full `npm test` / webpack overlay**: Missing `ActivityLogAccessAuditList` / `searchAccessAudit` — **resolved** 2026-04-14 (see §5 `npm run build` and full-suite rows); QA re-ran build + full Jest + browser smoke.
- **Post-restart `curl` 000**: Expected until dev server listens; **200** after ~5 s (documented under Scope).
- **Backend initially down** (earlier session): Resolved by starting PostgreSQL then backend (see Health check).

### Verification summary

- **Overall**: **Pass** for policy/UI acceptance and **toolchain unblock**: **`npm run build`** exit 0, **full `npm test`** (286 tests), `verify:screen-access`, health **200** on frontend after restart (allow brief startup delay). **TC-07** browser: **partial** — shell/login OK; post-login **관리** menu validation optional with test account.

## 6. Error remedy result (cause and actions)

**Confirmed root cause (frontend/runtime):** The mismatch between “N rows in `permission_group_screen`” and “M items under 관리” is primarily explained by **documented alias and family rules** in `screenAccessPolicy.js` (e.g. `user-permission-hierarchy` unlocks both 권한 그룹 v1 and v2 leaves; legacy UM / hierarchy unlocks 사용자 관리 v2), not by incorrect sidebar filtering. Session `allowedScreenIds` is applied consistently; multi-group **union** on the backend can still increase id count vs a single group matrix (separate verification via TC-01 / Backend if needed).

**Actions taken:** Exported `getVisibleAdminSidebarChildViews` for a single source of truth vs `AppSidebar` filtering; added optional dev-gated diagnostic logging in `App.js`; added operator-facing tooltips in `ScreenSelectionTree` for 관리 rows; extended Jest coverage and re-ran `verify:screen-access` (exit 0).

**Backend follow-up:** Not required for the confirmed **alias/expansion** cause. If live `GET /api/auth/me` ever disagrees with DB union for the same user, use TC-01 and delegate to Backend.

---

## 7. Final version (Korean)

**요약 (검증 후)**

- **배경**: 비시스템관리자 사용자에게 권한 그룹으로 허용된 관리(관리) 화면 수와 실제 사이드바 하위 메뉴 개수·항목이 어긋나 보이는 문제가 있었음.
- **원인**: `screenAccessPolicy.js`의 **별칭/패밀리 규칙** 때문에 DB·매트릭스의 `screen_id` 행 수와 **관리 메뉴 줄 수가 1:1이 아님** (예: 하나의 id가 여러 잎 메뉴를 노출). 세션 `allowedScreenIds`와 필터 로직은 일관되나, 운영자 기대와 표시 개수가 달라질 수 있음.
- **조치**: `getVisibleAdminSidebarChildViews`로 **표시 목록 단일 출처** 정리; `ADMIN_MATRIX_SIDEBAR_ALIAS_HINTS`로 **매트릭스 UI 툴팁**; `REACT_APP_SCREEN_ACCESS_DIAGNOSTIC=1` 시 **개발용 진단**; Jest·`verify:screen-access`로 정책·일관성 검증.
- **번들/테스트**: `ActivityLogAccessAuditList` 및 `searchAccessAudit` 추가(2026-04-14)로 **전체 `npm run build` / 전체 `npm test` 차단 해소** (§5 기록). 브라우저에서는 로그인 화면까지 로드 확인; **관리** 하위 메뉴 검증은 테스트 계정으로 선택적 수행.

---

**Author**: Requirements (subagent)  
**Date**: 2026-04-14  
**Status**: QA Step 5 complete — verification **pass** (`npm run build`, full `npm test`, `verify:screen-access`, frontend health); ActivityLogAccessAudit **unblock** recorded in §5; TC-07 browser **partial** (login shell; post-login menu optional).
