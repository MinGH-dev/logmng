# 20250303-permission-group-checkbox-not-working-bugfix-2 — Write/approve checkbox still does not toggle (re-verification fail)

**Parent requirement ID**: `20250303-permission-group-checkbox-not-working`  
**Previous bugfix**: `20250303-permission-group-checkbox-not-working-bugfix-1.md`  
**Bugfix sequence**: 2

---

## 1. Discovery

- **When**: During Step 5 re-verification of bugfix-1 (permission group checkbox fix).
- **What failed**: TC-01 (Edit dialog — uncheck 수정 (write)): Click on write checkbox ("사용자 관리 수정", ref e105) did not uncheck. `browser_is_checked` remained yes after one and two clicks. Checkbox visual/state change did not occur.

---

## 2. Error scope

- **Failure scope**: frontend
- **Layer**: frontend
- **Symptom**: Write checkbox in permission group edit dialog does not toggle on click (no visual/state change).
- **Impact**: Admin cannot change write/approve per screen; permission group screen function configuration still broken.

---

## 3. Cause (estimated)

- Click may not be reaching the actual `<input type="checkbox">` (overlay, parent capture, or pointer-events still blocking).
- Or `onChange` fires but state (e.g. `editAllowedScreens`) is not updating / re-render not reflecting (e.g. same reference, or dialog overlay re-capturing).
- Bugfix-1 added: htmlFor/id on write/approve inputs, stopPropagation on `.screen-selection-functions`, `.permission-group-dialog { pointer-events: auto }`. Verification was run with cursor-ide-browser; checkbox ref e105 was clicked but remained checked.

---

## 4. Action

**Delegate to Frontend** (failure scope: frontend):

1. Confirm in dev tools that clicking the write checkbox (e.g. "수정" for 사용자 관리) triggers the input’s `onChange` and that parent state updates (e.g. `setEditAllowedScreens` with new array).
2. If click does not reach input: inspect overlay/z-index and pointer-events; ensure the dialog content (and `.screen-selection-functions` container) receive clicks. Consider ensuring the checkbox input or its label is the hit target (e.g. label `htmlFor` / input `id`).
3. If state updates but UI does not: ensure `ScreenSelectionTree` receives a new `selectedScreens` reference and that the write/approve checked state is derived correctly from it; check for any memo or ref that could prevent re-render.
4. Re-run verification: TC-01 through TC-07 per bugfix-1 §5 (same test cases). QA will re-verify after fix and restart.

**Actual files changed (Frontend implementation):**

| File | Change |
|------|--------|
| `frontend/src/components/PermissionGroupManagement/ScreenSelectionTree.js` | Write/approve: input and label as siblings (no wrapping label); label `onClick` with `preventDefault()` + `changeWrite`/`changeApprove` toggle so label click updates state even if native association fails. |
| `frontend/src/components/PermissionGroupManagement/PermissionGroupPanel.js` | `onChange={(next) => setEditAllowedScreens(() => next)}` so parent uses functional updater and always applies child's array. |

---

## 5. Verification

- **Same as bugfix-1**: TC-01 through TC-07 from `docs/requirements/20250303-permission-group-checkbox-not-working.md` §3.
- **Procedure**: After Frontend fix and restart, QA re-runs verification and records results in this doc (§5 Test results).
- **Success**: All TC-01–TC-07 pass.

### §5 Test results

**Date**: 2025-03-03  
**Scope**: Frontend. Build/restart confirmed done before verification.

**Health check**: Backend `curl http://localhost:9200/api/health` → 200, JSON OK. Frontend `curl http://localhost:3001` → 200.

**Browser automation**: Tool: cursor-ide-browser. Base URL: http://localhost:3001. Login: admin / admin123. Navigated to 권한 그룹 관리, opened Edit for ADMIN group.

| ID | Result | Notes |
|----|--------|-------|
| TC-01 | **Fail** | Edit dialog — 수정 (write) for 사용자 관리: clicked checkbox (ref e110) and label "수정" (ref e133). Expected: checkbox unchecks immediately. Actual: `browser_is_checked` (e110) remained yes after click(s). Write checkbox did not toggle. |
| TC-02 | Skip | Could not check 수정 after uncheck (blocked by TC-01). |
| TC-03 | Pass | Edit dialog — 승인 (approve) for 승인 대기: checkbox (ref e106) toggles on click; checked state updates. |
| TC-04 | Pass | Edit dialog — uncheck 승인 (e106): click unchecked the approve checkbox; `browser_is_checked`(e106) → no. |
| TC-05 | Not run | Save → PUT/GET allowedScreens not executed (blocked by TC-01 failure). |
| TC-06 | Not run | Initial load with write/approve false not executed. |
| TC-07 | Not run | Create dialog write/approve toggle not executed. |

**Summary**: TC-01 fails (write checkbox does not respond to click). Approve checkbox (TC-03, TC-04) works. Re-verification **not** passed; do not commit. Next: create bugfix-3 or hand off to Requirements with failure scope **frontend** (write checkbox click/label not updating state or not receiving events).

---

**Related**: `docs/requirements/20250303-permission-group-checkbox-not-working-bugfix-1.md`, `docs/requirements/20250303-permission-group-checkbox-not-working.md`

**Author**: QA subagent  
**Date**: 2025-03-03  
**Status**: Re-verification run 2025-03-03; TC-01 failed (write checkbox does not toggle). Hand off to Requirements → Frontend for bugfix-3.
