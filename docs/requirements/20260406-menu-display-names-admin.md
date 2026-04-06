# 20260406 - Admin-configurable menu display labels

**Language**: §1, §2, §3 authored in **English** first. **Korean final section (§7)**: pending verification — add after QA completes verification per `docs/workflow/DOCUMENT-LANGUAGE-POLICY.md`.

**Commit**: Commits closing this requirement must reference this document (e.g. `req 20260406-menu-display-names-admin` or `docs/requirements/20260406-menu-display-names-admin.md`).

**Related (extension)**: `docs/requirements/20260407-screen-menu-parent-order.md` — admin-configurable **top-level parent group** and **leaf sort order** for the sidebar (same admin area; stable `screen_id` / `currentView`).

---

## 1. User requirement

### Requirement description

End users must see **clear, business-oriented screen names** in the application sidebar (and related UI) that are **managed by administrators**, without exposing internal **version strings** in labels (e.g. "PB FEP v1.0.0", "권한 그룹 관리 v1.0.0"). Technical identifiers used for **routing, permissions, and APIs** — `screen_id` / `currentView` values such as `pb-feplog`, `pb-fep-log-search`, `permission-group-management` — must **remain unchanged**. The product must separate:

- **Technical id** (`screen_id`): stable key for navigation, permission checks, and backend contracts.
- **User-facing label** (`label_user`): text shown to all authenticated users in the menu and in primary screen chrome where the product displays a human-readable screen title.
- **Optional admin-only label** (`label_admin`): short internal note (e.g. version or migration hint) visible **only** in admin configuration UI, not to general end users.

### User scenario

1. An administrator opens an admin settings screen for **menu / screen display names**.
2. The admin sets `label_user` for `pb-feplog` to e.g. "PB FEP 로그 검색" and optionally sets `label_admin` to "legacy v1" for their own reference.
3. A standard user logs in and opens the sidebar: they see the new **user label**, not the old hardcoded version string.
4. The user navigates between screens: `currentView` and URLs/routing behavior stay the same; permission checks still use `screen_id`.
5. If the label service is unavailable, the app still loads; the UI **falls back** to built-in default labels from the frontend menu configuration.

### Expected outcome

- Sidebar and other user-visible menu labels reflect **admin-configured** `label_user` values where defined.
- **`screen_id` / `currentView`** and permission semantics are **unchanged**; no regression in access control or API routing.
- **Non-admin users** cannot change labels; **admin-only** APIs enforce least privilege.
- **Audit**: Changes to labels record who changed what and when (and optionally before/after values per project audit standards).
- **Security**: Stored and returned text is validated (whitelist of `screen_id`, length limits, XSS-safe plain text — no HTML execution from stored labels).
- **Resilience**: If label fetch fails after login, the app uses **frontend defaults** (current hardcoded menu / `LOG_TYPE_BY_VIEW` names) without breaking navigation.

**Note**: Numeric or visual layout standards for a new admin screen are not defined in this requirement; align with `docs/design/` and `docs/workflow/REQUIREMENTS-CHANGE-TARGET-CHECKLIST.md` during implementation. Menu label strings themselves are product content, not design-token measurements.

---

## 2. Design

### 2.1 Security review (optional; when PII / decryption / access control)

This feature touches **access control** (admin-only writes), **stored presentation strings** (XSS if rendered unsafely), and **operational transparency** (audit). It does **not** involve decryption of log payloads or approval workflows. Review aligns with `docs/contract.md` (screen-display-labels bullets), `specs/menu-display-labels.spec.yaml`, and `docs/security-guide.md` (least privilege, defensive validation, safe logging).

- [x] Security review performed (recommended)
- [ ] Implementation verified against §2.1 acceptance criteria (complete after Step 4 + QA)

#### Risks

| Risk | Notes |
|------|--------|
| **Broken access control on writes** | If PUT/PATCH is not consistently gated on `is_system_admin` (or equivalent server-side check), any authenticated user could change global menu text (integrity / social-engineering surface). |
| **Stored XSS** | If `labelUser` / `labelAdmin` are rendered via `dangerouslySetInnerHTML`, raw HTML in stored strings, or unsafe DOM APIs, labels become an XSS vector. |
| **Admin-only string leakage** | Requirement §1 states `label_admin` is for **admin UI only**. If **GET** returns `labelAdmin` to all authenticated clients, non-admins could read internal notes (network tab, compromised client, or mistaken UI binding). |
| **IDOR / injection via `screenId`** | Accepting arbitrary `screenId` values could widen persistence or confuse merges; must reject unknown ids (**400** `INVALID_SCREEN_ID`). |
| **Audit gaps** | Without durable records of who changed which label and when, accountability and incident response for malicious or mistaken edits are weakened. |
| **Over-logging** | Logging full label bodies in application logs may increase sensitivity of operational logs (product text, possible internal notes); prefer structured audit + minimal app logs. |

#### Acceptance criteria

- [ ] **Write authorization**: Only **system administrators** (`is_system_admin === true`, per contract) can mutate labels; others receive **403** `FORBIDDEN` with no partial apply. Enforcement is **server-side** (not UI-only).
- [ ] **GET scope**: **GET** `/api/screen-display-labels` requires a **valid session** (**401** if not authenticated), consistent with `specs/menu-display-labels.spec.yaml`. Any authenticated user who loads the app shell may receive **user-facing** label data; **screen access** remains enforced separately via `allowedScreenIds` / routing (GET does not grant navigation rights).
- [ ] **`labelAdmin` disclosure**: Either **omit `labelAdmin`** from GET for non-admin principals, or return **null** and ensure **only** admin settings UI requests extended admin fields—so non-admins cannot rely on receiving internal notes (aligns with §1 “admin-only”).
- [ ] **Validation**: Server enforces **whitelist** for `screenId`, **max length**, and **plain-text** rules (**400** `INVALID_SCREEN_ID` / `INVALID_INPUT` per contract). Client treats values as **plain text** (React default text binding or explicit escape); **no** `dangerouslySetInnerHTML` for these fields.
- [ ] **Audit**: Successful admin mutations emit an **audit / activity** record with actor id, timestamp, and **which** `screenId`(s) changed (before/after optional per project audit standards); production **application** logs do not dump full label strings at INFO unless justified.

#### Recommendations

1. **Admin-only writes**: Mirror existing system-admin patterns used for other settings APIs (same interceptor / guard / service check); add integration tests for **403** for a non-admin session cookie.
2. **XSS / plain text in React**: Render `labelUser` (and any admin-only copy in admin UI) as **text nodes** or components that escape by default; avoid passing stored strings into HTML sinks. If rich text is ever required, that would be a **separate**, explicitly threat-modeled change (out of scope here).
3. **GET contract vs. privacy of `labelAdmin`**: Prefer **server-side filtering**: non-admin GET returns only fields needed for sidebar/chrome (**at minimum** `screenId` + `labelUser`). Reserve `labelAdmin` for admin GET or admin-only response shape to match the “internal note” intent.
4. **Authenticated GET**: Keeping GET available to **all authenticated users** is acceptable for **labelUser** (display strings only, no new access to restricted screens). Document that this endpoint is **not** a substitute for permission checks.
5. **Audit logging**: Use the same facility as other admin mutations (`user_activity_log` or equivalent); include action type distinguishable from unrelated admin events. Avoid logging entire request bodies at default levels if they duplicate PII-heavy patterns elsewhere—labels are usually not PII but may contain operational jargon; still follow `docs/security-guide.md` for logger usage.
6. **Rate / abuse (optional hardening)**: Low priority; if abuse is a concern, consider light rate limits on PUT/PATCH per admin session (product decision).

**Reviewer notes (2026-04-06):** No decryption or PII processing in scope; primary controls are **least privilege on writes**, **input validation**, **safe rendering**, **auditability**, and **tight disclosure** of `labelAdmin`.

### Technical design

#### Problem analysis

1. **Hardcoded labels**: `frontend/src/constants/menuTree.js` defines `MENU_TREE` with fixed `label` strings (e.g. `PB FEP v1.0.0`, `PB FEP v2.0.0`, versioned admin entries). `frontend/src/App.js` defines `LOG_TYPE_BY_VIEW` with `name` fields used for the log search area heading (`LogGrid` renders `logType?.name`).
2. **Identifiers are already stable**: `view` / `currentView` values (`pb-feplog`, `pb-fep-log-search`, etc.) are the correct keys for permissions and routing; the problem is **presentation strings** embedded in code.
3. **Shared consumption**: `MENU_TREE` is imported by `AppSidebar`, `ScreenSelectionTree` (permission group management), and permission group screen matrix utilities — any label strategy must keep **one source of defaults** and **merge** server-provided overrides consistently (or deliberately scope admin UI vs end-user menu if product requires).

#### Solution approach

Structure by scope. Implementing agents confirm or amend the file list in §2 when implementation is complete.

**Frontend:**

- Keep **default labels** in `menuTree.js` (and default `LOG_TYPE_BY_VIEW` names in `App.js` or a small shared module) as **fallback**.
- After authentication (bootstrap or post-login), **GET** merged menu labels from the backend; **merge** overrides by `screen_id` into:
  - Sidebar rendering (`AppSidebar` / `MENU_TREE` derivation).
  - `logType.name` passed into `LogGrid` for the active view (so the main content title matches the representative name, not necessarily the old version string).
- **Admin UI**: CRUD or form to edit `label_user` and optional `label_admin` per allowed `screen_id` (exact UX: table vs modal — implementer aligns with existing admin patterns).
- **Failure mode**: If GET fails or times out, use defaults only; do not block login. Optionally show a non-blocking notice for admins only (product decision).

**Backend:**

- **GET** (authenticated): return a map or list of `{ screen_id, label_user, label_admin? }` for all configurable entries (or only those the deployment supports). May be combined with existing session/bootstrap endpoints only if contract review agrees; otherwise a dedicated resource.
- **PUT/PATCH** (admin-only): update labels; validate `screen_id` against a **server-side whitelist** aligned with `docs/contract.md` / permission screen list; validate length and character class; reject unknown ids with 400.
- **Audit**: persist actor user id, timestamp, and changed fields (align with existing `user_activity_log` or admin audit patterns).

**DB:**

- Persist `screen_id` → `label_user`, optional `label_admin`, audit columns. Options: dedicated table (e.g. `screen_display_label`) or JSON in a settings table — **DB agent** selects normalized vs JSON per performance and migration practice.

**Contract:**

- Document new endpoints and response shapes in `docs/contract.md` (and `specs/` if the project adds a spec file for this feature).

### Affected scopes and change targets (verification)

| Scope | Affected? | §2 subsection and change file list complete? |
|-------|------------|-----------------------------------------------|
| Backend | Yes | [x] |
| Frontend (config UI + view screen) | Yes | [x] |
| DB | Yes | [x] |
| Contract / Spec | Yes | [x] |
| Cursor tools (skills, specs) | Optional — update `ui-ux-domain` or menu notes if behavior is documented there | [ ] |

### Planned change file list (expected change targets)

**(Planned at authoring. Implementing agent (Step 4) confirms or amends this list when implementation is complete.)**

#### Frontend

- `frontend/src/constants/menuTree.js`  
  - Remain the **canonical default** tree; document that runtime labels may override `label` per `id`/`view`; ensure `ALLOWED_SCREEN_IDS` / `ORDERED_SCREEN_IDS` stay aligned with contract.
- `frontend/src/App.js`  
  - Fetch or receive menu label map after auth; **merge** into props for sidebar and into `LOG_TYPE_BY_VIEW` (or equivalent) so `logType.name` reflects `label_user`.
- `frontend/src/components/AppSidebar.js`  
  - Render menu item text from **merged** labels (defaults + API).
- `frontend/src/components/LogGrid.js`  
  - Continue to use `logType.name` for headings; no version strings if parent passes merged names (verify no other hardcoded PB FEP version titles in the same view).
- `frontend/src/components/PermissionGroupManagement/ScreenSelectionTree.js`  
  - Align displayed names with merged labels for consistency in admin permission UI (or show `label_admin` where product prefers — **confirm** with product).
- `frontend/src/components/PermissionGroupScreenMatrix/PermissionGroupScreenMatrix.js` and/or `frontend/src/components/PermissionGroupScreenMatrix/allowedScreensMatrixUtils.js`  
  - If matrix rows derive human-readable names from `MENU_TREE`, apply same merge logic.
- **New** (paths indicative): e.g. `frontend/src/api/menuLabelsApi.js`, `frontend/src/hooks/useMenuLabels.js`, `frontend/src/components/AdminMenuLabelsSettings/…`  
  - Client fetch, merge helper, and admin settings UI.
- **Tests**: e.g. `AppSidebar.test.js`, `menuLabels.merge.test.js` (or colocated tests) — cover fallback and merge behavior.

#### Backend

- **New or extended** REST controller and service (exact package per project layout), e.g. `…/MenuLabelController.java`, `…/MenuLabelService.java`, DTOs, validation, and **admin authorization** (same gate as other system-admin-only settings).
- Repository / DAO for persisted labels; integration tests for GET/PUT and denial for non-admin.

#### DB

- New migration / DDL: table or settings storage for `screen_id`, `label_user`, optional `label_admin`, `updated_at`, `updated_by` (and created fields if required).

#### Contract / documentation

- `docs/contract.md` — New endpoints, authz, error codes (`403` for non-admin write, `400` for invalid `screen_id`).
- Optional: `specs/menu-display-labels.spec.yaml` if the project maintains a YAML spec for this feature.

---

## 3. Test approach

### Test case list (required)

**Permission / API completeness**: Include explicit cases for **authenticated GET**, **non-admin PUT denial**, **admin PUT success**, and **invalid screen_id** rejection. Trace admin checks to the same enforcement pattern as other admin APIs per `docs/contract.md`.

| ID | Scope | Type | Scenario (input / condition) | Expected result | Verification (unit / integration / manual) |
|----|-------|------|------------------------------|-----------------|--------------------------------------------|
| TC-01 | Backend | Normal | Authenticated user GET menu labels | 200; JSON contains `screen_id` keys and `label_user` strings | Unit / integration (mvn test) |
| TC-02 | Backend | Exception | Non-admin PUT/PATCH label update | 403; no DB change | Integration (mvn test or REST test) |
| TC-03 | Backend | Normal | System admin PUT valid `screen_id` and `label_user` | 200; persisted; audit row or activity log entry recorded | Integration |
| TC-04 | Backend | Edge | PUT unknown or disallowed `screen_id` | 400 `INVALID_SCREEN_ID` or contract-equivalent | Unit / integration |
| TC-05 | Backend | Edge | PUT `label_user` exceeding max length | 400 validation error | Unit |
| TC-06 | Frontend | Normal | After admin sets `label_user` for `pb-feplog`, user reloads app | Sidebar shows new label for that screen | Unit (merge logic) + manual / browser |
| TC-07 | Frontend | Regression | Navigate to `pb-fep-log-search` vs `pb-feplog` | `currentView` and LogGrid API paths unchanged from pre-feature behavior | Unit / manual |
| TC-08 | Frontend | Edge | Menu labels GET fails (network error) | App shows default labels; user can navigate; no crash | Unit / manual |
| TC-09 | Integration | Normal | End-to-end: admin updates label → non-admin session sees new sidebar label | Matches stored value | Manual / browser MCP optional |

### Test scenarios

#### Scenario 1: Admin configures labels

1. Log in as system admin. Open menu label settings.  
2. Set `label_user` for `pb-fep-log-search` to a string without version numbers. Save.  
3. Verify persistence (reload settings) and audit log if applicable.

#### Scenario 2: Standard user sees updated sidebar

1. Log in as non-admin with access to PB FEP screens.  
2. Confirm sidebar shows admin-defined `label_user`.  
3. Switch views; confirm routing and log search API URLs are unchanged.

#### Scenario 3: Fallback

1. Simulate API failure for GET labels (mock or disconnect).  
2. Confirm defaults from `menuTree.js` / `LOG_TYPE_BY_VIEW` appear and navigation works.

### Test data

- At least one admin user and one non-admin user with `pb-feplog` / `pb-fep-log-search` in `allowedScreenIds`.  
- Provide **executable SQL** or seed steps for label rows once schema exists (implementer adds exact INSERTs in §5 when stable).

### Test environment

- Frontend: `http://localhost:3001` (or per contract)  
- Backend: `http://localhost:9200`  
- Database: PostgreSQL (per project)

### 3.5 Browser automation verification (optional)

- **Applicable TCs**: TC-06, TC-09 (sidebar visible text after admin change).  
- **Procedure**: Login as admin → set label → logout → login as non-user → `browser_snapshot` sidebar for expected text.  
- **Reference**: `docs/workflow/BROWSER-AUTOMATION-VERIFICATION-POLICY.md`

---

## 4. Checklist

### Frontend verification

- [ ] API parameters validated  
- [ ] UI behavior confirmed (sidebar + log view title)  
- [ ] Error handling verified (fallback)  

### Backend verification

- [ ] API test cases written and run  
- [ ] Logs checked (no sensitive data in logs)  
- [ ] Admin-only enforcement verified  

### Integration

- [ ] End-to-end flow tested  
- [ ] Routing and permissions regression tested  

### Documentation

- [ ] Requirement doc completed  
- [ ] `docs/contract.md` updated  

---

## 5. Test results

### Test run date

- (Pending)

### Test results

#### Frontend

- (Pending)

#### Backend

- (Pending)

**Commands:**

- (QA: one executable command per TC in §3 after implementation)

**Outcome:**

- (Pending)

### Issues found and resolution

- (None yet)

### Next steps

1. Confirm implementation against §2.1 acceptance criteria (after Step 4).  
2. Contract update and implementation handoff per `docs/workflow/HANDOFF-CHECKLIST.md`.

---

## 6. Error remedy result (cause and action)

Not applicable (feature requirement, not an error-fix-only doc).

---

## 7. Final version (Korean)

**Pending verification** — add after QA completes verification per `docs/workflow/DOCUMENT-LANGUAGE-POLICY.md`.

---

**Author**: Requirements (subagent)  
**Date**: 2026-04-06  
**Status**: In progress  
