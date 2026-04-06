# 20260406 - Permission group save fails: invalid screen ID `screen-display-labels`

## 1. User requirement

### Requirement description

When a system administrator edits **permission groups** (screen permissions) and saves, the application shows the error **"유효하지 않은 화면 ID입니다: screen-display-labels"** and the save fails with **400** and error code **`INVALID_SCREEN_ID`**. The reported ID **`screen-display-labels`** matches the **menu / view id** used for the **Screen display names** (화면 표시 이름) admin feature.

Administrators must be able to complete permission group updates without this failure when the UI includes that screen in the permission matrix, or the UI must not offer or persist a screen id that the server rejects—**consistent with** `docs/contract.md`, `specs/permission-group-hierarchy.spec.yaml`, and backend validation.

### User scenario

1. A system administrator opens **permission group management** (v1 panel or v2 matrix).
2. The admin edits **screen permissions** for a group and includes or retains the row corresponding to **화면 표시 이름** / **`screen-display-labels`**.
3. The admin clicks **Save**.
4. **Problem**: The UI shows **"유효하지 않은 화면 ID입니다: screen-display-labels"**; the update does not persist (400 `INVALID_SCREEN_ID`).

### Expected outcome

- Saving a permission group **succeeds** (200) when the configuration is valid per product rules; **no** spurious `INVALID_SCREEN_ID` for **`screen-display-labels`** if that screen is part of the supported permission model.
- **Alignment**: Frontend **menu / matrix** screen ids, backend **`ScreenConstants`** allowlist, and **spec §4.1** allowed screen list stay **consistent** for permission-group CRUD.
- **Product rule (to confirm after diagnostic)**: Either **`screen-display-labels`** is a **grantable** screen in permission groups (then server + contract + spec + client constants must include it with correct read/write/approve/decrypt rules), or it is **not grantable** (e.g. system-admin-only entry point only)—then the **configuration UI must not** send it in `allowedScreens` / matrix must exclude it. The fix must follow the confirmed rule.

**Note**: Numeric/layout standards from design docs do not apply to this bugfix.

## 2. Design

### 2.1 Security review (optional; when PII / decryption / access control)

This bugfix touches **permission group** and **screen allowlists** (access control metadata). It does **not** expand decryption or PII exposure by itself; the chosen fix must preserve **least privilege** (e.g. if `screen-display-labels` remains system-admin-only for **writes**, permission-group semantics must not imply broader **PUT** rights than `docs/contract.md` for `/api/screen-display-labels`).

- [ ] Security review performed (check if applicable)
- Risks: Misconfigured allowlist could expose admin-only routes to non–system-admin users if routing ignores `systemAdminOnly` / server checks.
- Acceptance / recommendations: Enforce **server-side** authorization for `/api/screen-display-labels` **PUT** as today; permission-group `allowedScreens` only controls **navigation / screen access** patterns consistent with existing enforcement. Any new `screen_id` must be reflected in **spec §4.3** API mapping if applicable.

### Technical design

#### Codebase summary

1. **Frontend — menu and matrix**  
   - `frontend/src/constants/menuTree.js`: **`MENU_TREE`** includes an admin child **`screen-display-labels`** (`view: 'screen-display-labels'`, `systemAdminOnly: true`).  
   - **`ALLOWED_SCREEN_IDS`** (exported array) **does not** include **`screen-display-labels`** (list ends at `permission-group-management`).  
   - **`PermissionGroupScreenMatrix`**: builds rows via **`flattenMenuTreeToRows(menuTree ?? MENU_TREE)`**, so the matrix includes **all** leaf screens from **`MENU_TREE`**, including **`screen-display-labels`**.  
   - **`SCREEN_DISPLAY_LABEL_FORM_IDS`**: lists screens whose **labels** are edited in the display-labels settings form; it **does not** include the literal id `screen-display-labels` (that list is for **labelled** application screens, not the settings page id).

2. **Backend — validation**  
   - `backend/src/main/java/com/logmng/constants/ScreenConstants.java`: **`ALL_ALLOWED_SCREENS`** includes many ids (e.g. `permission-group-screen-matrix`) but **does not** include **`screen-display-labels`**.  
   - `backend/src/main/java/com/logmng/service/PermissionGroupService.java`: validates each `allowedScreens[].screenId`; on failure throws **`CustomException.badRequest("유효하지 않은 화면 ID입니다: " + …, "INVALID_SCREEN_ID")`** (same message pattern as reported).

3. **Contract / spec**  
   - `specs/permission-group-hierarchy.spec.yaml` **§4.1 Allowed screen ID list** table lists screens through **`permission-group-management`** and states validation against **§4**; it **does not** list **`screen-display-labels`**.  
   - `docs/contract.md`: **GET/PUT `/api/screen-display-labels`** is documented separately; **`screenId`** on PUT is validated against a **server whitelist** for **label** configuration, which is a **different** concern from permission-group **`allowedScreens`**, but the **same string** `screen-display-labels` is used as a **navigation view id** in the frontend.

#### Problem analysis

1. **Mismatch**: The permission UI **derives selectable screens from `MENU_TREE`**, which contains **`screen-display-labels`**, while permission-group **POST/PUT** validation uses **`ScreenConstants`** / spec **§4.1**, where **`screen-display-labels`** is **absent**. Saving sends **`screenId: "screen-display-labels"`**, which fails **`ScreenConstants.isValid`** → **400** **`INVALID_SCREEN_ID`**.

2. **Related prior work**: `docs/requirements/20260318-permission-group-menu-invalid-screen-id-imagelog.md` addressed a **legacy typo** and **normalization** for another screen id; here the id is **canonical** in the menu tree but **missing** from the permission allowlist.

#### Diagnostic phase (mandatory for error/bug fix only)

Do **not** change business logic based on hypothesis alone. **Confirm** the failure path and payload from logs before the fix.

- **Phase 0 (diagnostic)**  
  1. Add **diagnostic (DEBUG)** logging in **suspected areas**:  
     - **Backend**: `PermissionGroupService` (or single validation helper) — log **each** `allowedScreens` item’s **`screenId`** (and outcome of `ScreenConstants.isValid` / `findFirstInvalid`) for **POST/PUT permission-groups** only; log **request id** or group **id** for correlation.  
     - Optionally **Backend**: entry log for **PUT** body size / number of screens (no PII in messages).  
     - **Frontend (dev-only)**: if needed, **temporary** `logger.debug` before save with **list of `screenId`s** in the payload (guard with `process.env.NODE_ENV === 'development'` or existing logger level), **removed or downgraded** after verification.  
  2. **Reproduce** the error: enable **화면 표시 이름** in the matrix (or load a group that already has it from a partial round-trip if any), **Save**, capture **backend DEBUG** lines and **HTTP 400** body.  
  3. **Analyze**: Confirm that the **first invalid** id is **`screen-display-labels`** and that the value originates from the **matrix / MENU_TREE** (not a typo or DB corruption).  
  4. **Only after** cause is confirmed, implement the **fix** (allowlist + spec + contract **or** UI exclusion), per product rule below.

- **Production safety**: Diagnostic logs must be **DEBUG** (off in production default), or behind a **dev flag**, or **removed** after verification. They must **not** log secrets or unnecessary user PII.

#### Solution approach (tentative — confirm after diagnostic)

Structure by scope. **Product** must confirm whether **`screen-display-labels`** should be **grantable** via permission groups.

**Frontend:**

- **If** the screen must **not** be grantable: **Filter** `MENU_TREE` rows (or matrix flatten input) so **`systemAdminOnly`** leaves or **`screen-display-labels`** specifically do **not** appear as assignable permission rows, **or** strip that id in **`toAllowedScreensPayload`** / save path—must match **contract** and **no phantom rows** in GET/PUT round-trip.  
- **If** the screen **is** grantable: Add **`screen-display-labels`** to **`ALLOWED_SCREEN_IDS`** (and any **ordered** lists used for first-screen / UX) **only if** contract lists it; align **tooltips** / **`screenFunctionDescriptions`** with read/write rules.

**Backend:**

- **If** grantable: Add **`screen-display-labels`** to **`ScreenConstants`** (`ALL_ALLOWED_SCREENS` and, per **§1.1.1**-style rules, **`SCREENS_WITH_WRITE`** / approve / decrypt sets as appropriate—likely **read-only** navigation if PUT remains system-admin-only). Extend **`PermissionGroupService`** validation only as needed. Add/adjust **unit tests** (`PermissionGroupServiceTest`).  
- **If** not grantable: **No** allowlist addition; ensure **validation** error messages remain clear; optional **DEBUG** log when stripping is expected (not required if Frontend fixes solely).

**DB:**

- **Likely none** unless existing **`permission_group_screen`** rows store **`screen-display-labels`** and require **migration** to remove or rename; diagnostic should confirm whether **DB** holds this id or only the **client** sends it.

**Contract / spec:**

- **If** grantable: Update **`specs/permission-group-hierarchy.spec.yaml`** **§4.1** table and **§1.1.1** validation row for **`screen-display-labels`**; update **`docs/contract.md`** and **`docs/api-definition.md`** if **allowed screen ids** for auth / permission groups change.  
- **If** not grantable: Document in spec **that** this view is **excluded** from permission-group assignable screens (and **why**), so Frontend/backend stay aligned.

### Affected scopes and change targets (verification)

| Scope | Affected? | §2 subsection and change file list complete? |
|-------|------------|-----------------------------------------------|
| Backend | Yes | Yes |
| Frontend (config UI + view screen) | Yes | Yes |
| DB | Optional (only if stored rows) | — |
| Contract / Spec | Yes | Yes |
| Cursor tools (skills, specs) | Optional | Update `api-permission-map` / `auth-permission-domain` skills only if permission model text changes |

### Planned change file list (expected change targets)

**(Planned at authoring. Implementing agent (Step 4) confirms or amends this list when implementation is complete.)**

#### Frontend

- `frontend/src/constants/menuTree.js`  
  - **Verify** whether **`ALLOWED_SCREEN_IDS`**, **`ORDERED_SCREEN_IDS`**, and matrix behavior must include **`screen-display-labels`** after product decision; or **exclude** this id from permission-matrix sources.  
- `frontend/src/components/PermissionGroupScreenMatrix/PermissionGroupScreenMatrix.js` / `frontend/src/components/PermissionGroupScreenMatrix/allowedScreensMatrixUtils.js`  
  - **Verify** flatten/payload paths so **unsupportable** ids are not sent.  
- `frontend/src/components/PermissionGroupManagement/PermissionGroupPanel.js` (if v1 still flattens **`MENU_TREE`**)  
  - Same alignment as matrix.

#### Backend

- `backend/src/main/java/com/logmng/constants/ScreenConstants.java`  
  - **If** grantable: add constant + allowlist (+ `supportsWrite` / etc. per spec).  
- `backend/src/main/java/com/logmng/service/PermissionGroupService.java`  
  - Diagnostic **DEBUG** logs (Phase 0); validation aligned with final rule.  
- `backend/src/test/java/com/logmng/service/PermissionGroupServiceTest.java` (and related controller tests)  
  - New cases for **`screen-display-labels`** acceptance or exclusion.

#### DB

- [None] unless diagnostic finds **`permission_group_screen`** rows requiring migration — then migration script under **`backend/src/main/resources/db/`** per **DB** agent.

#### Contract / spec

- `specs/permission-group-hierarchy.spec.yaml` — **§4.1**, **§1.1.1** as needed.  
- `docs/contract.md`, `docs/api-definition.md` — permission-group allowed screen ids / error behavior if updated.

#### Cursor tool update targets

- Optional: `.cursor/skills/ui-ux-domain/SKILL.md` or **menu** docs if navigation rules for **`screen-display-labels`** change.

## 3. Test approach

### Test case list (required)

| ID | Scope | Type | Scenario (input / condition) | Expected result | Verification (unit / integration / manual) |
|----|-------|------|------------------------------|-----------------|--------------------------------------------|
| TC-01 | Backend | Normal | PUT permission-group with `allowedScreens` containing only ids in **updated** allowlist including **`screen-display-labels`** **if** product allows it | 200; no `INVALID_SCREEN_ID` | Unit (`mvn test`) |
| TC-02 | Backend | Exception | PUT permission-group with `allowedScreens` containing **`screen-display-labels`** **if** product disallows it | 400 `INVALID_SCREEN_ID` **or** id stripped only if contract says so — **align with** final rule | Unit |
| TC-03 | Frontend | Normal | Open matrix, enable **화면 표시 이름** row, Save | **If** grantable: success snackbar / no error **If** not grantable: row not present or save does not send id | Unit (`npm test`) or manual |
| TC-04 | Integration | Normal | Admin saves group after selecting screens including **화면 표시 이름** | Matches TC-01/03 expected HTTP outcome | Manual or API integration |
| TC-05 | Regression | Normal | Save group with **java-fw-imagelog**, **permission-group-screen-matrix**, **activity-log** unchanged | Still 200; no regression | Unit + manual spot-check |

### Test data

- Use existing admin session; at least one permission group editable in dev. No special SQL unless diagnostic finds DB-stored **`screen-display-labels`** rows.

### Test environment

- Frontend: `http://localhost:3001` (per project)  
- Backend: `http://localhost:9200`  
- Database: PostgreSQL (per contract)

### 3.5 Browser automation verification (optional)

- **Applicable TCs**: TC-03, TC-04 (manual/browser).  
- **Procedure**: Login as system admin → open permission group matrix → reproduce save → confirm no invalid screen id error.

## 4. Checklist

### Frontend verification
- [x] API parameters validated (aligned with contract/spec allowlist)
- [x] UI behavior confirmed (app loads; login shell at `http://localhost:3001`; full matrix save not exercised — no admin credentials in QA run)
- [x] Error handling verified (unit tests cover allowlist; manual TC-03/04 full flow deferred)

### Backend verification
- [x] API test cases written and run (`PermissionGroupServiceTest` includes `screen-display-labels` path)
- [x] Logs checked (DEBUG diagnostic removed or gated after fix)
- [ ] Performance checked (if applicable) — N/A for allowlist-only change

### Integration
- [ ] End-to-end flow tested — browser: login gate; API integration not run in this QA pass
- [x] Edge cases tested (regression TC-05 via suite; see §5)

### Documentation
- [x] Requirement doc completed
- [x] Code comments added (if applicable) — as implemented by dev agents

## 5. Test results

### Test run date
- **2026-04-06** (QA verification)

### Test results

| Layer | Command | Result | Notes |
|-------|---------|--------|-------|
| Backend | `cd backend && mvn test` | **Pass** (exit 0) | Full test suite |
| Frontend | `cd frontend && npm test -- --watchAll=false` | **Pass** | Test Suites: 27 passed; Tests: 145 passed |
| Verify | `./scripts/dev-services.sh all restart` | **Pass** | Backend + DB + frontend restarted |
| Health | `curl http://localhost:9200/api/health` | **Pass** | HTTP 200, JSON `success: true` |
| Health | `curl http://localhost:3001` | **Pass** | HTTP 200 |
| Browser (step 3.5) | cursor-ide-browser: `browser_navigate` → wait → `browser_snapshot` | **Pass (smoke)** | Base URL `http://localhost:3001`: app title "로그 관리 시스템", login form (사용자 ID, 비밀번호, 로그인) visible. **TC-03/TC-04** (admin matrix → save with `screen-display-labels`) **not executed** — login required; no credentials in automation. |

**TC mapping:** TC-01 covered by backend tests + suite green. TC-02 N/A under fix (A) grantable. TC-03/04 partial — unit tests + smoke load only. TC-05 implicit via full suite pass.

**Commands (record):**
```text
cd backend && mvn test
cd frontend && npm test -- --watchAll=false
./scripts/dev-services.sh all restart
curl -s http://localhost:9200/api/health
curl -s -o /dev/null -w "%{http_code}" http://localhost:3001
```

## 6. Error remedy result (cause and action) — for error/bug fix requirements only

- **Requirement ID**: `20260406-permission-group-invalid-screen-id-screen-display-labels`
- **Root cause**: **Allowlist mismatch** — `screen-display-labels` exists in `MENU_TREE` and the permission matrix rows, but was **missing** from backend `ScreenConstants` / permission-group validation and from frontend `ALLOWED_SCREEN_IDS` and spec **§4.1**, so PUT sent `screenId: "screen-display-labels"` and failed with **400** `INVALID_SCREEN_ID`.
- **Actions taken**: **Fix (A) — grantable screen**: Added **`screen-display-labels`** to backend allowlist and write/permission sets per product rules (read-only for group config where applicable); updated **`frontend/src/constants/menuTree.js`** `ALLOWED_SCREEN_IDS`; updated **`specs/permission-group-hierarchy.spec.yaml`**, **`docs/contract.md`**, **`docs/api-definition.md`**; added/updated unit tests (backend `PermissionGroupServiceTest`, frontend `menuTree.test.js`, `allowedScreensMatrixUtils.test.js`).
- **Result**: Unit tests pass; dev stack restart + health checks pass; browser smoke shows app loads. Spurious `INVALID_SCREEN_ID` for **`screen-display-labels`** resolved for aligned save path per contract.
- **Completed**: **2026-04-06** (QA)

---

## 7. Final version (Korean) — add after all verification is complete

### 요약

- **현상**: 권한 그룹 저장 시 **`screen-display-labels`(화면 표시 이름)** 행이 포함되면 서버가 **유효하지 않은 화면 ID**로 거부(400, `INVALID_SCREEN_ID`)했습니다.
- **원인**: 메뉴·매트릭스에는 있으나 **권한 그룹 허용 화면 목록(백엔드·프론트·스펙)**에 빠져 있었습니다.
- **조치**: **`screen-display-labels`**를 허용 목록·스펙·계약 문서에 반영하고, 테스트로 검증했습니다.
- **검증**: `mvn test` / `npm test` 통과, 재기동 후 헬스 체크 및 브라우저에서 로그인 화면까지 스모크 확인(관리자 로그인 없이 매트릭스 저장 시나리오는 미실행).

---

**Author**: Requirements (subagent)  
**Date**: 2026-04-06  
**Status**: Complete (QA verified 2026-04-06)
