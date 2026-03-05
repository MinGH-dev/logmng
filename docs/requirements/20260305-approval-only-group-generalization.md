# 20260305 - Approval-only permission group: tool generalization + UI improvement

## 1. User requirement

### Requirement description

This requirement has **two parts**:

**Part 1 — Tool generalization (documents, skills, specs only; no code changes):**
Currently, the "approval-only permission group" pattern is tied to the **name** "APPROVE_USER" across skills, specs, and requirement docs. This creates a brittle assumption: if an administrator creates a differently-named group (e.g. TEAM_APPROVER, REGIONAL_APPROVER) with the same screen configuration, the documentation and AI tooling will not recognize it as following the same rules. The improvement generalizes the definition to a **condition-based** one:

> **Approval-only permission group** = any group where `allowedScreenIds` has **no `main`** + **has `pending-approvals`**. The group name/code is irrelevant.

All rules (redirect, menu filtering, API 403, action hiding) apply identically to **any** group matching this condition.

**Part 2 — UI improvement (frontend code change):**
In the permission group management screen (`ScreenSelectionTree`), screens that support `approve` (search-history, pending-approvals) currently show a "조회 ✓" label plus an "승인" toggle button when checked. This two-element UI is semantically a single choice between two modes ("view only" vs "view + approve"), but visually it looks like two independent controls. The improvement replaces this with a **"조회만" | "승인"** single selection (radio group), making the intent clearer.

### User scenario

#### Part 1 scenario
1. An administrator creates a new permission group named "TEAM_APPROVER" with only `pending-approvals` screen.
2. A developer or AI agent looks up documentation/skills to understand the behavior.
3. **Problem**: Skills and specs describe rules under "APPROVE_USER pattern", implying rules only apply to a group literally named "APPROVE_USER". The developer is unsure whether TEAM_APPROVER follows the same redirect/menu/API rules.
4. **Expected**: Skills and specs define "approval-only permission group" by **condition** (no `main` + has `pending-approvals`), and explicitly state rules apply to any matching group regardless of name.

#### Part 2 scenario
1. An administrator opens Permission group management and edits (or creates) a group.
2. They check "로그 검색이력" (search-history) — a screen that supports `approve`.
3. **Current**: They see "조회 ✓" (always on) and a separate "승인" toggle button. The two controls imply read and approve are independently configurable, but read is always true — the only real choice is approve on/off.
4. **Expected**: They see a **"조회만" | "승인"** single selection (radio buttons). Selecting "조회만" sets read=true, approve=false. Selecting "승인" sets read=true, approve=true. The intent is immediately clear.

### Expected outcome

**Part 1:**
- Skills (`auth-permission-domain`, `search-history-decrypt-domain`, `department-approver-domain`) define rules under "Approval-only permission group" heading with condition-based definition (no `main` + has `pending-approvals`), not the name "APPROVE_USER".
- "APPROVE_USER" appears only as an **example** group code (e.g. "예: APPROVE_USER, TEAM_APPROVER").
- `specs/permission-group-hierarchy.spec.yaml` has a new section defining approval-only groups by condition.
- `docs/contract.md` has a one-line summary referencing the spec.
- Related requirement docs (`20260304-approve-only-permission-group`, `20260304-permission-group-modal-error-visibility`) soften "APPROVE_USER" to "승인 전용 권한 그룹(예: APPROVE_USER)".
- No code behavior changes — existing runtime logic already operates on `allowedScreenIds`/`screenFunctions`, not group names.

**Part 2:**
- For screens in `SCREENS_WITH_APPROVE` (search-history, pending-approvals), when checked, the UI shows **"조회만" | "승인"** as a radio group instead of "조회 ✓" label + approve toggle button.
- "조회만" → read=true, approve=false. "승인" → read=true, approve=true.
- Existing group data loads correctly: if approve=true, "승인" is pre-selected; if approve=false (or default), "조회만" is pre-selected.
- No API/backend payload changes — the same `{ screenId, read, approve }` shape is sent.
- Screens that support only `write` (not `approve`) are unaffected.

---

## 2. Design

### 2.1 Security review (optional; when PII / decryption / access control)

Part 1 is documentation-only and does not change runtime behavior. Part 2 changes only the frontend UI control for setting approve; the underlying data model and API payload remain identical. No new PII exposure, no change to access control logic.

- [x] Security review performed — not applicable (no PII, no access control change).

### Technical design

#### Problem analysis

1. **Name-coupled documentation (Part 1)**: Skills, specs, and requirement docs describe approval-only rules under "APPROVE_USER pattern", coupling the rule to a specific group name. The runtime code (`AuthService`, `ScreenAccessInterceptor`, `App.js`) already uses `allowedScreenIds` and `screenFunctions` — no group-name dependency exists in code. Only the documentation is name-coupled.

2. **Ambiguous dual-control UI (Part 2)**: The current `ScreenSelectionTree` renders "조회 ✓" (a non-interactive label) + an "승인" toggle button for approve-supporting screens. Since read is always true when a screen is checked, the only user decision is approve on/off. The two separate UI elements (label + toggle) suggest two independent choices, but in reality this is a single binary selection: "view only" or "view + approve". A radio group ("조회만" | "승인") directly conveys the single-choice nature.

#### Solution approach

**Documents/Tools (Part 1):**

- **`.cursor/skills/auth-permission-domain/SKILL.md`**: Rename section `## APPROVE_USER pattern (approval-only permission group)` → `## Approval-only permission group`. Redefine as condition-based: "any group where allowedScreenIds has no `main` + has `pending-approvals`". List APPROVE_USER, TEAM_APPROVER as examples. Update Quick reference entry.
- **`.cursor/skills/search-history-decrypt-domain/SKILL.md`**: Change "APPROVE_USER pattern (승인 전용 권한 그룹)" to "승인 전용 권한 그룹 (approval-only)" with condition-based wording. Reference `auth-permission-domain` SKILL for full definition.
- **`.cursor/skills/department-approver-domain/SKILL.md`**: Change "APPROVE_USER pattern" to "승인 전용 권한 그룹(조건: main 없음, pending-approvals 있음). 예: APPROVE_USER, TEAM_APPROVER 등.".
- **`specs/permission-group-hierarchy.spec.yaml`**: Add a new section (e.g. `## 5. Approval-only permission groups`) defining the condition, applicable rules (redirect, menu, API 403, action hiding), and examples.
- **`docs/contract.md`**: Add one line under "화면 기반 접근 제어" — approval-only groups are defined by condition (no main + pending-approvals), not by name; reference spec §5.
- **`docs/requirements/20260304-approve-only-permission-group.md`**: Add a paragraph stating this requirement applies to all groups matching the approval-only condition, not just APPROVE_USER.
- **`docs/requirements/20260304-permission-group-modal-error-visibility.md`**: Soften "APPROVE_USER" references to "승인 전용 권한 그룹(예: APPROVE_USER)".

**Frontend (Part 2):**

- **`frontend/src/components/PermissionGroupManagement/ScreenSelectionTree.js`**:
  - For screens where `SCREENS_WITH_APPROVE.includes(screenId)` and the screen is checked:
    - Remove the current pattern: `<span>조회 ✓</span>` + `<button>승인 {checked ? '✓' : ''}</button>`.
    - Replace with a radio group: two `<input type="radio">` elements labeled "조회만" and "승인".
    - "조회만" maps to `approve=false` (read is always true). "승인" maps to `approve=true`.
    - Default for newly checked screen: "조회만" (approve=false), consistent with current default.
    - Existing data: when editing a group with approve=true, "승인" is pre-selected.
  - For screens that do NOT support approve: keep current behavior (read label + write toggle).
  - Keep the Tooltip on the "승인" radio option (APPROVE_CHECKBOX_TOOLTIP).
  - a11y: use `role="radiogroup"` with `aria-label`, individual radios with `aria-checked`.

- **`frontend/src/components/PermissionGroupManagement/ScreenSelectionTree.css`**:
  - Add styles for the radio group (e.g. `.screen-selection-approve-radio`). Visually style as inline radio buttons or a compact segment, consistent with existing `.screen-selection-fn-toggle` aesthetic.

**Backend:**
- No changes. The API payload shape (`{ screenId, read, approve }`) is unchanged.

**DB:**
- No changes.

### Change file list

**(Tentative. Implementing agent (Step 4) confirms or updates with actual files changed.)**

#### Documents / Tools (Part 1)
- `.cursor/skills/auth-permission-domain/SKILL.md`
  - Rename "APPROVE_USER pattern" → "Approval-only permission group"; condition-based definition; APPROVE_USER as example only
- `.cursor/skills/search-history-decrypt-domain/SKILL.md`
  - Update "APPROVE_USER pattern" reference to condition-based "승인 전용 권한 그룹"
- `.cursor/skills/department-approver-domain/SKILL.md`
  - Update "APPROVE_USER pattern" reference to condition-based definition with examples
- `specs/permission-group-hierarchy.spec.yaml`
  - Add §5 "Approval-only permission groups" section
- `docs/contract.md`
  - Add one-line summary under "화면 기반 접근 제어"
- `docs/requirements/20260304-approve-only-permission-group.md`
  - Add "applies to all groups matching condition" paragraph
- `docs/requirements/20260304-permission-group-modal-error-visibility.md`
  - Soften "APPROVE_USER" → "승인 전용 권한 그룹(예: APPROVE_USER)"

#### Frontend (Part 2) — confirmed
- `frontend/src/components/PermissionGroupManagement/ScreenSelectionTree.js`
  - Replace "조회 ✓" + approve toggle with "조회만" | "승인" radio group for SCREENS_WITH_APPROVE
- `frontend/src/components/PermissionGroupManagement/ScreenSelectionTree.css`
  - Add `.screen-selection-approve-radio`, `.screen-selection-radio-option` styles for the radio group UI
- `frontend/src/components/PermissionGroupManagement/ScreenSelectionTree.test.js` *(new)*
  - Unit tests TC-15, TC-16, TC-17, TC-18
- `frontend/src/setupTests.js` *(new)*
  - Jest setup for `@testing-library/jest-dom`

#### Backend
- None

#### DB
- None

---

## 3. Test approach

### Test case list (required)

| ID | Scope | Type | Scenario (input / condition) | Expected result | Verification (unit / integration / manual) |
|----|-------|------|------------------------------|-----------------|--------------------------------------------|
| TC-01 | Documents | Normal | Read `auth-permission-domain` SKILL after update. Check section header and definition. | Section is titled "Approval-only permission group" (not "APPROVE_USER pattern"). Definition uses condition (no `main` + has `pending-approvals`). APPROVE_USER is listed as example only. | Manual (document review) |
| TC-02 | Documents | Normal | Read `search-history-decrypt-domain` SKILL after update. Check APPROVE_USER references. | "APPROVE_USER pattern" replaced with "승인 전용 권한 그룹 (approval-only)". Condition-based wording. References auth-permission-domain for full definition. | Manual (document review) |
| TC-03 | Documents | Normal | Read `department-approver-domain` SKILL after update. Check APPROVE_USER references. | "APPROVE_USER pattern" replaced with condition-based "승인 전용 권한 그룹". APPROVE_USER listed as example only. | Manual (document review) |
| TC-04 | Documents | Normal | Read `specs/permission-group-hierarchy.spec.yaml` after update. Check for approval-only section. | New section "Approval-only permission groups" exists with: condition definition, applicable rules (redirect, menu, API), and examples. | Manual (document review) |
| TC-05 | Documents | Normal | Read `docs/contract.md` after update. Check for approval-only summary. | One-line summary under "화면 기반 접근 제어" referencing approval-only groups by condition, not name. | Manual (document review) |
| TC-06 | Documents | Normal | Read `20260304-approve-only-permission-group.md` after update. | Contains paragraph stating requirement applies to all groups matching approval-only condition, not just APPROVE_USER. | Manual (document review) |
| TC-07 | Documents | Normal | Read `20260304-permission-group-modal-error-visibility.md` after update. | "APPROVE_USER" references softened to "승인 전용 권한 그룹(예: APPROVE_USER)" or equivalent. | Manual (document review) |
| TC-08 | Frontend | Normal | Open permission group edit modal. Check `search-history` screen (supports approve). Check is checked. | Shows "조회만" | "승인" radio selection. Default: "조회만" selected (approve=false). "조회 ✓" label + toggle button no longer shown. | Manual / browser |
| TC-09 | Frontend | Normal | Check `pending-approvals` screen in permission group modal. | Shows "조회만" | "승인" radio selection (same as search-history). | Manual / browser |
| TC-10 | Frontend | Normal | Select "승인" radio for search-history. Save the group. Reopen edit modal. | "승인" is pre-selected. Saved data has approve=true. API payload contains `{ screenId: 'search-history', read: true, approve: true }`. | Manual / browser |
| TC-11 | Frontend | Normal | Select "조회만" radio for pending-approvals. Save. Reopen. | "조회만" is pre-selected. approve=false in saved data. | Manual / browser |
| TC-12 | Frontend | Normal | Check a screen that supports only write (e.g. user-management), NOT approve. | Shows "조회 ✓" + "수정" toggle as before. No radio group for approve. No regression. | Manual / browser |
| TC-13 | Frontend | Edge | Uncheck a screen that had approve=true. Re-check it. | Radio defaults to "조회만" (approve=false), consistent with current default behavior for newly added screens. | Manual / browser |
| TC-14 | Frontend | Normal | Verify keyboard accessibility: tab to radio group, arrow keys to switch. | Radio group is keyboard-navigable. Focus ring visible. Screen reader announces "조회만" / "승인" with radio role. | Manual / browser |
| TC-15 | Frontend | Unit | Render ScreenSelectionTree with selectedScreens containing `{ screenId: 'search-history', approve: true }`. | "승인" radio is checked. DOM contains radio inputs, not toggle button for approve. | Unit (Jest / RTL) |
| TC-16 | Frontend | Unit | Render ScreenSelectionTree with selectedScreens containing `{ screenId: 'search-history', approve: false }`. | "조회만" radio is checked. | Unit (Jest / RTL) |
| TC-17 | Frontend | Unit | Click "승인" radio → onChange is called with approve=true for that screen. Click "조회만" → onChange called with approve=false. | onChange payload correct for each selection. | Unit (Jest / RTL) |
| TC-18 | Frontend | Regression | Render ScreenSelectionTree with a screen that has write but no approve (e.g. user-management). | Write toggle and "조회 ✓" still render correctly. No radio group. | Unit (Jest / RTL) |

### Test scenarios

#### Scenario 1: Document review — condition-based definition (TC-01 through TC-07)

1. After Part 1 changes are applied, open each updated file.
2. Search for "APPROVE_USER pattern" — should not appear as a section header or standalone rule label.
3. Search for "Approval-only permission group" or "승인 전용 권한 그룹" — should appear with condition-based definition.
4. Verify "APPROVE_USER" appears only as an example (e.g. "예: APPROVE_USER").

#### Scenario 2: Radio group for approve-supporting screens (TC-08, TC-09)

1. Open Permission group management. Create or edit a group.
2. Check "로그 검색이력" (search-history) — supports approve.
3. Verify: a radio group with "조회만" and "승인" appears (not "조회 ✓" + toggle button).
4. Check "승인 대기" (pending-approvals) — also supports approve.
5. Verify: same radio group UI appears.

#### Scenario 3: Save and reload approve value (TC-10, TC-11)

1. In a group edit modal, check search-history and select "승인".
2. Save. Reopen the same group in edit mode.
3. Verify "승인" radio is selected (approve=true loaded from API).
4. Change to "조회만", save, reopen → verify "조회만" selected.

#### Scenario 4: No regression for write-only screens (TC-12, TC-18)

1. Check "사용자 관리" (user-management) — supports write, NOT approve.
2. Verify: "조회 ✓" label + "수정" toggle appear (unchanged behavior). No radio group.

#### Scenario 5: Keyboard and a11y (TC-14)

1. Tab to the radio group for an approve-supporting screen.
2. Use arrow keys to switch between "조회만" and "승인".
3. Verify focus ring, screen reader announces role="radio" and checked state.

### Test data

- Existing permission groups with various screen configurations (some with approve=true, some without).
- At least one group with search-history + approve=true and one with pending-approvals + approve=false for edit-mode testing.
- No new SQL needed — use existing APPROVE_USER group from `init-data.sql` (req 20260304-approve-only-permission-group).

### Test environment

- Frontend: `http://localhost:3001`
- Backend: `http://localhost:9200`
- Database: PostgreSQL (local)

### 3.5 Browser automation verification (optional — for frontend-heavy requirements)

- **Applicable TCs**: TC-08, TC-09, TC-10, TC-11, TC-12, TC-14
- **Procedure**:
  - TC-08/TC-09: `browser_navigate` to login (system admin) → navigate to Permission group management → edit a group → check search-history / pending-approvals → `browser_snapshot` → confirm radio group ("조회만" / "승인") is present; confirm no "조회 ✓" label + approve toggle button.
  - TC-10/TC-11: Select "승인" → save → reopen → `browser_snapshot` → confirm "승인" selected. Switch to "조회만" → save → reopen → confirm "조회만" selected.
  - TC-12: Check user-management → `browser_snapshot` → confirm write toggle, no radio group.
  - TC-14: Use keyboard interaction to verify tab/arrow key navigation within radio group.
- **Reference**: `docs/workflow/BROWSER-AUTOMATION-VERIFICATION-POLICY.md`.

---

## 4. Checklist

### Frontend verification
- [ ] Approve-supporting screens (search-history, pending-approvals) show "조회만" | "승인" radio group
- [ ] "조회 ✓" label + approve toggle button removed for approve-supporting screens
- [ ] "조회만" → approve=false; "승인" → approve=true in onChange payload
- [ ] Existing approve=true data loads as "승인" pre-selected
- [ ] Default for newly checked screen is "조회만" (approve=false)
- [ ] Write-only screens (e.g. user-management) unchanged — "조회 ✓" + write toggle
- [ ] Radio group keyboard-accessible and screen-reader friendly (role, aria-checked)
- [ ] API payload shape unchanged (`{ screenId, read, approve }`)

### Backend verification
- [ ] No backend changes — verify API still accepts same payload shape

### Integration
- [ ] End-to-end: set "승인" for search-history, save group, reload, verify approve=true
- [ ] End-to-end: set "조회만", save, reload, verify approve=false

### Documentation
- [x] All three skills updated with condition-based definition
- [x] Spec has "Approval-only permission groups" section
- [x] contract.md has one-line summary
- [x] Related requirement docs softened from "APPROVE_USER" to "승인 전용 권한 그룹(예: APPROVE_USER)"
- [x] This requirement doc completed

---

## 5. Test results

### Test run date
- 2026-03-05

### Test results

#### Documents (Part 1) — TC-01 through TC-07: All PASS

| TC | Result | Detail |
|----|--------|--------|
| TC-01 | **PASS** | `auth-permission-domain/SKILL.md`: section header is "Approval-only permission group" (not "APPROVE_USER pattern"). Condition-based definition present (no `main` + has `pending-approvals`). APPROVE_USER listed as example only (e.g. APPROVE_USER, TEAM_APPROVER, REGIONAL_APPROVER). Quick reference updated. |
| TC-02 | **PASS** | `search-history-decrypt-domain/SKILL.md`: "APPROVE_USER pattern" replaced with "승인 전용 권한 그룹 (approval-only)". Condition-based wording: "allowedScreenIds에 main 없음 + pending-approvals 있음인 모든 권한 그룹(예: APPROVE_USER, TEAM_APPROVER 등 — 이름 무관)". References `auth-permission-domain` SKILL for full definition. |
| TC-03 | **PASS** | `department-approver-domain/SKILL.md`: "APPROVE_USER pattern" replaced with "승인 전용 권한 그룹 (approval-only)". Condition-based: "조건: allowedScreenIds에 main 없음 + pending-approvals 있음". Examples: "예: APPROVE_USER, TEAM_APPROVER 등". References `auth-permission-domain` SKILL. |
| TC-04 | **PASS** | `specs/permission-group-hierarchy.spec.yaml`: New section "# 5. Approval-only permission groups" with §5.1 (condition-based definition, group name irrelevant), §5.2 (applicable rules: redirect, menu, API, action hiding), §5.3 (examples: APPROVE_USER, TEAM_APPROVER, CUSTOM_APPROVER), §5.4 (references). |
| TC-05 | **PASS** | `docs/contract.md`: One-line summary under "화면 기반 접근 제어": "승인 전용 권한 그룹 (approval-only): allowedScreenIds에 main 없이 pending-approvals만 가진 그룹…그룹 이름과 무관하게 동일 UX/API 규칙 적용. 상세: specs §5." |
| TC-06 | **PASS** | `20260304-approve-only-permission-group.md`: Generalization note paragraph added — "This requirement applies to all approval-only permission groups — any group where allowedScreenIds has no main + has pending-approvals — not just the group named APPROVE_USER." References spec §5 and this generalization doc. |
| TC-07 | **PASS** | `20260304-permission-group-modal-error-visibility.md`: APPROVE_USER references softened to "승인 전용 권한 그룹(예: APPROVE_USER)" or "승인 전용 권한 그룹 such as APPROVE_USER". APPROVE_USER used only as example, not as the sole applicable group. |

#### Frontend (Part 2) — TC-15 through TC-18: All PASS

| TC | Result | Detail |
|----|--------|--------|
| TC-15 | **PASS** | search-history with approve=true → "승인" radio is checked. DOM contains radio inputs with role="radiogroup". |
| TC-16 | **PASS** | search-history with approve=false → "조회만" radio is checked. |
| TC-17 | **PASS** | Clicking "승인" radio → onChange called with approve=true; clicking "조회만" → onChange called with approve=false. |
| TC-18 | **PASS** | user-management (write, no approve) → write toggle and "조회 ✓" render correctly. No radio group rendered. |

**Commands:**

```bash
# TC-15/TC-16/TC-17/TC-18: Unit tests
cd frontend && npm test -- --watchAll=false --testPathPattern="ScreenSelectionTree"
# Result: 4 passed, 0 failed (10.789 s)
```

**Outcome:**
- Part 1 (Documents): 7/7 TCs passed — all files correctly generalized from "APPROVE_USER pattern" to condition-based "approval-only permission group" definition.
- Part 2 (Frontend): 4/4 unit tests passed — radio group UI correctly renders for approve-supporting screens, onChange payloads correct, no regression for write-only screens.
- Frontend build: succeeded (CI=false npm run build — exit 0, confirmed by Frontend subagent).
- TC-08 through TC-14 (browser/manual): Not executed in this run — require running dev server and browser. These TCs verify interactive behavior (save/reload, keyboard a11y) and can be verified in a follow-up browser session.

### Issues found and resolution
- None. All executed TCs passed without issues.

### Notes
- TC-08 through TC-14 are browser/manual TCs that require a running dev environment. They are deferred to a future browser verification session. The unit tests (TC-15 through TC-18) cover the core logic (radio rendering, state mapping, onChange behavior, regression).

---

**Author**: Agent (Requirements)
**Date**: 2026-03-05
**Status**: Verified (TC-01~TC-07, TC-15~TC-18 passed; TC-08~TC-14 deferred to browser session)
