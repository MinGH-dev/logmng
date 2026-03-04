# 20250303-permission-group-checkbox-not-working-bugfix-1 — Checkbox still has no reaction when selecting

**Parent requirement ID**: `20250303-permission-group-checkbox-not-working`  
**Bugfix sequence**: 1

---

## 1. Discovery

- **When**: During verification (re-run after parent fix); user report.
- **What failed**: Verification failed again; user confirms checkbox click still produces **no visual/state change** (no reaction at all). Write/approve checkbox in permission group edit (and create) dialog does not respond to click — no toggle, no state update.

---

## 2. Error scope

- **Failure scope**: frontend (symptom persists after previous fix).
- **Layer**: frontend.
- **Symptom**: Write/approve checkbox in permission group edit (and create) dialog does not respond to click — no toggle, no state update.
- **Impact**: Permission group screen function (read/write/approve) configuration impossible.

---

## 3. Cause (estimated)

Refined investigation directions:

| # | Hypothesis | Description |
|---|------------|-------------|
| 1 | **Click never reaches input** | Overlay (e.g. `permission-group-dialog-overlay`) or another element capturing/stopping propagation; or label/input not receiving pointer events. |
| 2 | **onChange fires but state does not update** | Parent (`PermissionGroupPanel`) `setEditAllowedScreens(next)` not triggering re-render; or `selectedScreens` reference/identity issue (e.g. same reference passed back). |
| 3 | **Label/association** | Nested or multiple inputs inside one label; or missing `htmlFor`/`id` causing wrong control toggled. |
| 4 | **Controlled input reset** | Something resetting `editAllowedScreens` after setState (e.g. effect, or dialog re-opening with stale group). |

---

## 4. Action

**Delegate to Frontend** with the following concrete tasks:

1. **Trace full path**: user click → checkbox `onChange` → `changeWrite`/`changeApprove(view, checked)` → `onChange(next)` → `setEditAllowedScreens(next)` → re-render with new `editAllowedScreens`. Add minimal debug log if needed to confirm handler and setState are called.
2. **Ensure dialog overlay does not capture clicks**: Overlay must not have `onClick` that stops propagation; dialog content (form, tree) must be in front and receive events. Check `.permission-group-dialog-overlay` and `.permission-group-dialog` in `PermissionGroupManagement.css`.
3. **Explicit htmlFor and id**: Use explicit `htmlFor` and `id` on write/approve checkboxes and their labels so the label is associated with the correct input (avoids ambiguity with nested structure).
4. **If state updates but UI does not**: Ensure `ScreenSelectionTree` receives new `selectedScreens` (new array reference) and that normalized/item derivation shows the updated write/approve; check for `React.memo` or similar that could prevent re-render.
5. **Change file list (confirmed)**:
   - `frontend/src/components/PermissionGroupManagement/ScreenSelectionTree.js` — Added explicit `id`/`htmlFor` for write and approve checkboxes (`write-${child.id}`, `approve-${child.id}`); added `onClick={(e) => e.stopPropagation()}` on `.screen-selection-functions` container so clicks are not captured by ancestors.
   - `frontend/src/components/PermissionGroupManagement/PermissionGroupManagement.css` — Set `.permission-group-dialog { pointer-events: auto; }` so dialog content receives pointer events (overlay does not block).
   - `frontend/src/components/PermissionGroupManagement/PermissionGroupPanel.js` — No code change; confirmed no useEffect or other logic overwrites `editAllowedScreens` when `editOpen` is true; `setEditAllowedScreens` is called with new array from `ScreenSelectionTree` onChange.

---

## 5. Verification

- **Same as parent**: TC-01 through TC-07 from `docs/requirements/20250303-permission-group-checkbox-not-working.md` §3.
- **Procedure**: QA re-runs verification after Frontend fix; record results in **this** bugfix doc (§5 Test results).
- **Success**: All TC-01–TC-07 pass; checkbox click produces immediate visual/state change and persisted values on save.

### Test case reference (parent)

| ID | Scenario | Expected |
|----|----------|----------|
| TC-01 | Edit dialog: uncheck 수정 (write) | Checkbox visually unchecks immediately |
| TC-02 | Edit dialog: check 수정 | Checkbox visually checks immediately |
| TC-03 | Edit dialog: check 승인 (approve) | Checkbox visually checks immediately |
| TC-04 | Edit dialog: uncheck 승인 | Checkbox visually unchecks immediately |
| TC-05 | Save → PUT/GET | allowedScreens correct |
| TC-06 | Initial load with write/approve false | Checkboxes show unchecked |
| TC-07 | Create dialog: toggle write/approve | Same behavior as edit |

### §5 Test results

**Verification**: 2025-03-03. Health check: backend 200, frontend 200. Browser: cursor-ide-browser, base http://localhost:3001. Login: admin / admin123. Edit dialog opened for ADMIN_EXT (사용자 관리 has write checkbox).

| ID | Result | Notes |
|----|--------|-------|
| TC-01 | **Fail** | Edit dialog — uncheck 수정 (write): Clicked write checkbox (ref e105, "사용자 관리 수정"); checkbox did not uncheck. `browser_is_checked` still yes after one and two clicks. Expected: visually unchecks immediately. Actual: state remained checked. (Scope: frontend — click may not reach input or onChange not updating state.) |
| TC-02 | Skip | Blocked by TC-01 (check 수정 after uncheck). |
| TC-03 | Skip | Blocked (approve checkbox not exercised; ADMIN_EXT has no search-history/승인 대기 in tree in same way). |
| TC-04 | Skip | Blocked by TC-03. |
| TC-05 | Skip | Blocked (save with changed write/approve not performed; would require TC-01/TC-03 pass). |
| TC-06 | Skip | Not run (initial load unchecked scenario). |
| TC-07 | Skip | Not run (create dialog; same control as edit). |

---

**Related**: `docs/requirements/20250303-permission-group-checkbox-not-working.md`, `docs/requirements/20250303-screen-function-availability.md`, `.cursor/skills/auth-permission-domain/SKILL.md`

**Author**: Requirements subagent  
**Date**: 2025-03-03  
**Status**: Open (delegate to Frontend; QA re-verifies after fix)
