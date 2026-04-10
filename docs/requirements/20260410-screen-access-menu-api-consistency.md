# 20260410 - Screen access, menu, and API consistency (single source + validation)

## 1. User requirement

### Requirement description

Screen and menu access is implemented in several places (sidebar filtering, route/view guards, backend path rules, and controller-level checks). Over time this produces **drift**: the UI can hide or show items inconsistently with what the backend allows or denies, and **broad OR conditions** (especially for PoC clones and legacy aliases) can leave menus or deep links reachable after a permission is revoked. The product needs a **sustainable model**: a clear single source of truth (or generated artifacts kept in lockstep), **automated checks** that fail when frontend and backend diverge, and a **central, documented policy** for when one screen id may satisfy access for another (aliases / PoC rules).

### User scenario

1. An operator grants a group **only** `permission-group-screen-matrix` (v2 matrix) so admins can edit the screen–permission matrix without legacy v1 management, **or** grants **only** `permission-group-management`.
2. A user should see the **admin** submenu entries and open the corresponding view **if and only if** their session `allowedScreenIds` (and function flags where applicable) match product policy; APIs used by that view should return **200** when access is intended and **403** when not—**without** relying on unrelated screen ids unless those aliases are explicitly documented.
3. An operator revokes **`user-management-v2-poc`** (and related PoC flags) from a group expecting the PoC menu and PoC APIs to be unusable.
4. **Problem**: Today, **sidebar and `App.js` guards** use **OR** lists (e.g. PoC view may pass if production `user-management-v2` or `user-permission-hierarchy` is still present). **`permission-group-screen-matrix`** is treated like **`permission-group-management`** in some places but **`ALLOWED_SCREEN_IDS`** in `menuTree.js` **omits** `permission-group-screen-matrix`; **`ORDERED_SCREEN_IDS`** omits it as well. **`ScreenAccessInterceptor`** maps `/api/permission-groups.*` to **`user-management` | `user-permission-hierarchy` | `user-management-v2`** only—**not** the permission-group admin screen ids—so a principal with **only** `permission-group-management` / **`permission-group-screen-matrix`** may be **denied** at the interceptor while the UI suggests access, or the inverse mismatch may appear depending on combination. Prior analysis also noted duplication between **`AppSidebar.js`** and **`App.js`**.

### Expected outcome

- **Single source of truth (or generated alignment)** for: canonical screen id set, **sidebar visibility rules**, **client route/view guards**, and **backend** `ScreenConstants` / `ScreenAccessInterceptor` path rules (and any controller gates that intentionally differ must be **documented with rationale**).
- **CI or npm script** that **fails the build** when frontend sets (menu allowlists, guard aliases, ordered/first-screen lists) **diverge** from backend allowlists / path-rule screen requirements (exact mechanism to be chosen in implementation: e.g. shared `screens.yaml` + codegen for Java/JS, or extract constants and diff in CI).
- **PoC / alias policy** is **centralized** (e.g. `screenAccessAliases.js` or shared module **generated from the same source**), with **comments** referencing this requirement and **unit tests** proving intended behavior (strict vs documented alias sets).
- **Product behavior** (pick one and implement consistently—**recommendation**: **documented aliases only**, no hidden OR beyond the central module + spec):
  - **Strict**: only the exact screen id grants menu + guard + API for that feature **unless** the central alias table lists an exception; **or**
  - **Documented aliases**: OR rules are allowed **only** where listed (e.g. legacy `user-management` → `user-management-v2` for specific views) and **PoC** paths must **not** inherit production ids unless explicitly listed.
- **`permission-group-screen-matrix`** is honored **everywhere** **`permission-group-management`** is honored for **admin submenu visibility**, **view guards**, and **backend enforcement** (interceptor and any `AuthService` helpers used by `PermissionGroupController`), aligned with **`ScreenConstants`** and **`specs/permission-group-hierarchy.spec.yaml`** §4.3 intent for permission-group family APIs.
- **`menuTree.js`**: **`ALLOWED_SCREEN_IDS`**, **`ORDERED_SCREEN_IDS`**, and **`MENU_TREE`** / **`SCREEN_DISPLAY_LABEL_FORM_IDS`** stay **consistent** with **`ScreenConstants.getAllAllowedScreens()`** (including screens present in backend but missing from frontend lists today, e.g. **`permission-group-screen-matrix`**, and any other gaps discovered during implementation).

**Note**: Numeric/layout UX standards are not in scope unless touched incidentally; this requirement is **access-control and consistency** only.

## 2. Design

### 2.1 Security review (optional; when PII / decryption / access control)

This requirement **changes access control surface** (who may see menus, reach views, and call APIs). Formal **Security** subagent review is recommended at workflow Step 2 to validate no unintended widening of decrypt, provisioning, or user-directory exposure.

- [x] Security review performed — **QA closure (2026-04-10)**: Implementation follows §2.1 mitigations: aliases are **enumerated** in `screenAccessPolicy.js` + tests; **no ad-hoc OR** outside that module and `menuTree` allowlists; `npm run verify:screen-access` and CI workflow enforce **fail-closed** drift detection; permission-group `/api/permission-groups.*` rules align with documented screen ids. Independent formal Security subagent Step 2 sign-off is not re-attached here; escalate if product needs a separate audit record.
- **Risks**:
  - **Over-broad OR** rules (PoC / legacy) can **retain access** after operators revoke a screen; tightening rules may **break** legacy groups that relied on undocumented aliases.
  - Misaligned interceptor vs UI may cause **confusing 403** or **exposure of navigation** that implies access.
  - Centralizing rules incorrectly could **widen** API access if path rules are generalized without matching function-bit checks.
- **Acceptance / recommendations**:
  - Any **alias** must be **enumerated** (code + spec); **no ad-hoc OR** in `AppSidebar` / `App.js` / `PermissionGroupPanel` beyond the shared module.
  - After changes, **regression tests** for permission-group APIs and UM v2 / PoC paths; **audit** permission-group admin actions remain unchanged in logging contract.
  - **Fail-closed** bias: if codegen/CI detects drift, **build fails** rather than silently allowing mismatch.

### Technical design

#### Codebase summary

- **Backend**: `ScreenConstants` defines `ALL_ALLOWED_SCREENS`, scope/write/approve/decrypt sets, and normalization. `ScreenAccessInterceptor` holds **ordered** `PATH_SCREEN_RULES` (regex → list of acceptable screen ids); access is granted if **any** required id is in `allowedScreenIds`. `PermissionGroupController` uses `AuthService.canAccessUserManagementView` and `hasWriteForManagementScreens` **in addition** to interceptor checks. `canAccessUserManagementView` includes `user-management`, `user-permission-hierarchy`, and `user-management-v2`; it does **not** currently name `permission-group-management` or `permission-group-screen-matrix`. `/api/permission-groups.*` path rule lists `USER_MANAGEMENT`, `USER_PERMISSION_HIERARCHY`, `USER_MANAGEMENT_V2` only.
- **Frontend**: `frontend/src/constants/menuTree.js` exports `MENU_TREE`, `ALLOWED_SCREEN_IDS`, `ORDERED_SCREEN_IDS`, `SCREEN_DISPLAY_LABEL_FORM_IDS`. **`ALLOWED_SCREEN_IDS`** and **`ORDERED_SCREEN_IDS`** omit **`permission-group-screen-matrix`** even though `MENU_TREE` includes the leaf. `AppSidebar.js` and `App.js` duplicate OR logic for `user-management-v2`, `user-management-v2-poc`, and permission-group views (`permission-group-management` **or** `user-permission-hierarchy`; **matrix** shares that branch but **not** `permission-group-screen-matrix`-only). `PermissionGroupPanel.js` and `PermissionGroupScreenMatrix.js` repeat similar gate patterns.
- **Spec**: `specs/permission-group-hierarchy.spec.yaml` §4.3 documents UM v2 and permission-group API access; **permission-group-screen-matrix** should be treated as part of the same permission-group admin family as **permission-group-management** for enforcement consistency (implementer to align spec tables with final interceptor/controller behavior — **Contract** Step 3).

#### Problem analysis

1. **Drift** between Java allowlists, interceptor path rules, and JS menu/guard lists causes **wrong menu visibility**, **wrong default first screen**, or **403 vs visible UI** contradictions.
2. **Duplicated OR logic** across files makes **PoC revoke** scenarios unreliable (e.g. `user-management-v2-poc` still satisfied by other ids).
3. **`permission-group-screen-matrix`** is a first-class screen in **`ScreenConstants`** and `MENU_TREE` but is **missing** from **`ALLOWED_SCREEN_IDS`** / **`ORDERED_SCREEN_IDS`** and is **not** a first-class alternative in backend path rules / `canAccessUserManagementView`, creating **inconsistent** behavior for matrix-only or management-only grants.
4. **No automated guard** prevents future edits from reintroducing drift.

#### Solution approach

Structure by scope. **Architecture** review at Step 3 is recommended for the chosen commonization approach (shared file vs codegen vs CI extract-and-diff).

**Frontend:**

- Introduce a **single module** (name illustrative: `screenAccessPolicy.js` or `screenAccessAliases.js`) exporting:
  - canonical **screen id sets** derived from or validated against backend (via shared source or CI);
  - **functions** such as `canShowMenuItem(view, allowedScreenIds, flags)`, `canAccessView(...)`, implementing **only** documented alias rules.
- Refactor **`AppSidebar.js`**, **`App.js`**, **`PermissionGroupPanel.js`**, **`PermissionGroupScreenMatrix.js`**, and any other consumers to **import** that module—**no scattered OR lists**.
- Fix **`menuTree.js`**: add **`permission-group-screen-matrix`** (and any other ids present in `ScreenConstants` but missing) to **`ALLOWED_SCREEN_IDS`** and **`ORDERED_SCREEN_IDS`** in an order consistent with **`MENU_TREE`** and product expectations; align **`SCREEN_DISPLAY_LABEL_FORM_IDS`** if needed.
- Add **unit tests** (Jest) for alias policy: matrix-only, management-only, hierarchy-only, PoC-only, revoke PoC, etc.

**Backend:**

- Update **`ScreenAccessInterceptor`** so `/api/permission-groups.*` (and any other permission-group–family paths the matrix uses) require screen ids **consistent** with spec: at minimum **`permission-group-management`** and **`permission-group-screen-matrix`** alongside existing **`user-management` / `user-permission-hierarchy` / `user-management-v2`** **if product confirms** those aliases remain (see **§1 expected outcome**); ensure **no** accidental widening of unrelated paths.
- Align **`AuthService.canAccessUserManagementView`** (and **`hasWriteForManagementScreens`** if spec requires write via permission-group screens) with the **same policy** as the interceptor for **permission-group controller** entry—**or** split a dedicated `canAccessPermissionGroupAdminApi` if Security/Contract prefers separation (must be documented in spec).
- Keep **`ScreenConstants`** as Java authority **or** reduce duplication via codegen from shared YAML; either way, **CI** must compare frontend and backend.

**Scripts / CI (optional scope, recommended):**

- Add **`npm run verify:screen-access`** (or equivalent) and/or **CI job** that:
  - parses `ScreenConstants` allowlist and/or a new **`screens/access-policy.yaml`**, and
  - compares **menuTree** ids, **policy module** exports, and **interceptor** path screen lists (via scripted extract or golden file).
- Document the command in **`docs/contract.md`** or **`docs/api-definition.md`** pointer if behavior is user-visible (Contract agent).

**DB:**

- None unless a future requirement ties screen metadata to DB (out of scope).

### Affected scopes and change targets (verification)

Per `docs/workflow/REQUIREMENTS-CHANGE-TARGET-CHECKLIST.md` (pattern **3.2 Permission or screen-access change**):

| Scope | Affected? | §2 subsection and change file list complete? |
|-------|------------|-----------------------------------------------|
| Backend | Yes | Yes |
| Frontend (config UI + view screen) | Yes — permission group **configuration** UI and **admin** views using gates | Yes |
| DB | No | N/A |
| Contract / Spec | Yes — §4.3 tables, path→screen mapping | Yes |
| Cursor tools (skills, specs) | Yes — permission/access skills | Yes |

### Planned change file list (expected change targets)

**(Planned at authoring. Implementing agent (Step 4) confirms or amends this list when implementation is complete.)**

#### Frontend

- `frontend/src/constants/menuTree.js` — align `ALLOWED_SCREEN_IDS`, `ORDERED_SCREEN_IDS` (and related exports) with backend canonical set; include **`permission-group-screen-matrix`**.
- `frontend/src/components/AppSidebar.js` — remove inline OR rules; use centralized policy module.
- `frontend/src/App.js` — same as sidebar for view guards and default-view logic.
- `frontend/src/components/PermissionGroupManagement/PermissionGroupPanel.js` — align entry gate with policy module.
- `frontend/src/components/PermissionGroupScreenMatrix/PermissionGroupScreenMatrix.js` — align entry gate with policy module.
- **New** `frontend/src/constants/screenAccessPolicy.js` (or agreed name) — centralized alias/OR rules + comments.
- **New or extended** `frontend/src/constants/screenAccessPolicy.test.js` (or colocated tests) — §3 scenarios.
- `frontend/package.json` — script for screen-access verification (if npm-driven).

#### Backend

- `backend/src/main/java/com/logmng/config/ScreenAccessInterceptor.java` — path rules for permission-group family include **`permission-group-management`** and **`permission-group-screen-matrix`** per §1.
- `backend/src/main/java/com/logmng/service/AuthService.java` — align `canAccessUserManagementView` / write checks with policy (or introduce dedicated helper per Security/Contract).
- `backend/src/test/java/com/logmng/config/ScreenAccessInterceptorTest.java` — new cases for matrix/management-only sessions.
- `backend/src/test/java/com/logmng/service/AuthServiceTest.java` — align with changed gates.

#### Contract / spec

- `specs/permission-group-hierarchy.spec.yaml` — §4.3 rows for permission-group screens and `/api/permission-groups` alignment.
- `docs/contract.md` / `docs/api-definition.md` — if documented behavior changes (Contract agent).

#### Scripts / CI

- **New** `scripts/verify-screen-access-consistency.js` (or `screens/access-policy.yaml` + comparator) — optional location under `scripts/` or repo root per project convention.
- `.github/workflows/*.yml` or existing CI config — invoke verify script when present.

#### Cursor tool update targets

- `.cursor/skills/auth-permission-domain/SKILL.md` — document single-source / alias policy after implementation.
- `.cursor/skills/ui-ux-domain/SKILL.md` — point to `screenAccessPolicy` (or final module name) and CI check.
- `.cursor/skills/api-permission-map/SKILL.md` — update `/api/permission-groups` and PoC path rows after interceptor change.

## 3. Test approach

### Test case list (required)

**Domain checklist**: Applied `api-permission-map` — trace interceptor + controller for permission-group and PoC paths; include regression for UM v2 shared GETs per spec.

| ID | Scope | Type | Scenario (input / condition) | Expected result | Verification (unit / integration / manual) |
|----|-------|------|------------------------------|-----------------|--------------------------------------------|
| TC-01 | Backend | Normal | Session `allowedScreenIds` = [`permission-group-screen-matrix`] only; `GET /api/permission-groups` | **200** (or product-documented auth path); not **403** solely due to missing UM screen ids if §2 alignment applies | Unit / integration (`mvn test`) |
| TC-02 | Backend | Normal | Session `allowedScreenIds` = [`permission-group-management`] only; `GET /api/permission-groups` | Same expectation as TC-01 per aligned policy | Unit / integration |
| TC-03 | Backend | Edge | Session has neither permission-group management/matrix nor UM/hierarchy/v2; `GET /api/permission-groups` | **403** `FORBIDDEN` | Unit / integration |
| TC-04 | Frontend | Normal | Mock user with only `permission-group-screen-matrix`; render sidebar admin children | **권한 그룹 관리 v2.0.0** visible per policy; v1 visibility matches policy (strict vs alias doc) | Unit (`npm test`) |
| TC-05 | Frontend | Normal | Mock user with only `permission-group-management`; matrix menu item visibility | Matches §1 policy (typically both v1/v2 admin items if spec says same gate) | Unit (`npm test`) |
| TC-06 | Frontend | Regression | User has `user-management-v2` but **not** `user-management-v2-poc`; PoC menu item | PoC entry **hidden** (and guard denies `user-management-v2-poc` view) | Unit (`npm test`) |
| TC-07 | Frontend | Regression | User has **only** `user-management-v2-poc` revoked; remaining ids per operator matrix | PoC routes/menus **not** accessible via undocumented OR | Unit (`npm test`) |
| TC-08 | Frontend | Normal | `ALLOWED_SCREEN_IDS` and `ORDERED_SCREEN_IDS` include every backend-allowed navigational screen id required by `MENU_TREE` | CI/npm verify script **exit 0** | Script in CI / `npm run` |
| TC-09 | Integration | Normal | Login as non-admin with matrix-only group; open matrix view; load permission groups | UI loads; API succeeds; no console 403 loop | Manual / browser (optional §3.5) |
| TC-10 | Integration | Normal | Login as non-admin with PoC revoked per §1; attempt deep link to PoC view | Blocked or redirected per product UX; API 403 | Manual / browser |

### Test scenarios

#### Scenario 1: Matrix-only permission group admin

1. Assign test user **only** `permission-group-screen-matrix` (+ required write flags per spec).
2. Open app: verify **admin** submenu and matrix view; call permission-group list API.
3. **Verification**: menu, guard, and API behavior match; no dependency on `user-permission-hierarchy` unless alias table says so.

#### Scenario 2: PoC revoke

1. Start from user with PoC id; remove **`user-management-v2-poc`** (and optional `hr-sync-poc` per product).
2. Refresh; attempt PoC menu and direct hash route.
3. **Verification**: no access via fallback OR to production UM ids unless explicitly documented.

### Test data

- Use existing permission-group test fixtures; add SQL or API steps to create groups with **only** matrix or **only** management screen rows (implementer documents executable steps in §5).

### Test environment

- Frontend: `http://localhost:3001` (per contract)
- Backend: `http://localhost:9200`
- Database: PostgreSQL (per project)

### 3.5 Browser automation verification (optional)

- **Applicable TCs**: TC-09, TC-10
- **Procedure**: Login → `browser_snapshot` → confirm menu visibility → navigate to matrix / PoC → confirm blocked or allowed per TC.
- **Reference**: `docs/workflow/BROWSER-AUTOMATION-VERIFICATION-POLICY.md`

## 4. Checklist

### Frontend verification

- [x] API parameters validated (policy module + Jest)
- [x] UI behavior confirmed (browser smoke: app shell + sidebar; §3.5)
- [ ] Error handling verified (unchanged UX paths; not separately exercised in this run)

### Backend verification

- [x] API test cases written and run (`mvn test`; interceptor + `AuthService` coverage)
- [ ] Logs checked (not required for this QA pass)
- [ ] Performance checked (if applicable) — N/A

### Integration

- [ ] End-to-end flow tested (TC-09/10 manual login scenarios not executed in this run)
- [x] Edge cases tested (unit + `verify:screen-access` script)

### Documentation

- [x] Requirement doc completed
- [x] Code comments added (if applicable) — policy module + script document intent

## 5. Test results

### Test run date

- **2026-04-10** (QA Step 5)

### Test results

#### Frontend

- **PASS** — `npm run verify:screen-access` (script `scripts/verify-screen-access-consistency.js`): exit 0; ALLOWED_SCREEN_IDS ↔ ScreenConstants, MENU_TREE vs ORDERED_SCREEN_IDS, permission-groups interceptor superset checks reported OK.
- **PASS** — `CI=true npm test -- --watchAll=false`: **39** suites, **256** tests passed.

#### Backend

- **PASS** — `cd backend && mvn test -q`: full suite green after **`IpUtil.collectIpCandidatesInTrustOrder`** made `public` (required for `ActivityLogAspect` cross-package call; otherwise `mvn compile` / `dev-services` backend build failed).

**Commands:**

```bash
cd backend && mvn test -q
cd frontend && npm run verify:screen-access
cd frontend && CI=true npm test -- --watchAll=false
./scripts/dev-services.sh all restart   # after backend compile fix
curl -s -o /dev/null -w "%{http_code}" http://localhost:3001   # → 200
curl -s http://localhost:9200/api/health                       # → 200 JSON OK
curl -s http://localhost:9200/api/db/test                       # → connected
```

**Verification (restart + health)** — **PASS** after `./scripts/dev-services.sh all restart` (initial restart had failed before `IpUtil` visibility fix). Frontend **200**, backend health **200**, DB test endpoint returns connected.

**Browser (§3.5 smoke)** — **PASS** — Tool: **cursor-ide-browser** MCP. URL: `http://localhost:3001`. Observed: app title “로그 관리 시스템”, sidebar with log search + admin section (e.g. 권한 그룹 관리, 사용자 관리, 사용자 관리 v2). TC-09/10 (role-specific login) not executed (would need dedicated test accounts).

**Outcome:**

- Automated tests and drift script: **PASS**.
- Runtime verify: **PASS** (health checks + browser smoke).

### Issues found and resolution

1. **Backend `mvn package` / `dev-services` compile failure** — `ActivityLogAspect` called `IpUtil.collectIpCandidatesInTrustOrder` from another package while the method was package-private. **Resolution**: declare method **`public`** in `IpUtil.java`. Re-ran `mvn test -q` and dev-services restart; health checks OK.

### Next steps

1. Optional: formal Security subagent archive if compliance needs a separate artifact.
2. Optional: manual TC-09/TC-10 with non-admin fixtures when test data is available.

## 6. Error remedy result (cause and action) — for error/bug fix requirements only

N/A (feature / consistency requirement; not a single error ticket)

---

## 7. Final version (Korean) — add after all verification is complete

*Deferred per `docs/workflow/DOCUMENT-LANGUAGE-POLICY.md` until QA verification completes.*

---

**Author**: Requirements (subagent)  
**Date**: 2026-04-10  
**Status**: Step 5 QA verification recorded (2026-04-10)
