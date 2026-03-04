# 20250303-permission-group-checkbox-not-working-bugfix-4 — Edit dialog onChange direct value (fundamental fix)

**Parent requirement ID**: `20250303-permission-group-checkbox-not-working`  
**Previous bugfix**: `20250303-permission-group-checkbox-not-working-bugfix-3.md`  
**Bugfix sequence**: 4

---

## 1. Discovery

- **When**: After bugfix-3 verification; TC-01 and TC-03 failed (write/approve checkboxes did not toggle in browser).
- **What**: Frontend implemented the fundamental fix: edit dialog passes **direct value** to `onChange` (e.g. `onChange={(next) => setEditAllowedScreens(next)}`); ScreenSelectionTree already uses a single event path (`onChange` with `e.target.checked`). No intermediate wrapper or stale closure.

---

## 2. Error scope (bugfix-3)

- **Failure scope**: frontend
- **Symptom**: Write and approve checkboxes in permission group edit dialog did not respond to click in automation.

---

## 3. Cause (addressed in bugfix-4)

- Parent (PermissionGroupPanel) may have been passing a setter that did not receive the updated array correctly, or state update was not triggering re-render with the new value. Fix: ensure edit dialog uses direct `setEditAllowedScreens(next)` so React receives the new array and re-renders.

---

## 4. Action

**Delegate to Frontend** (done):

- **PermissionGroupPanel.js**: Edit dialog — ensure `onChange` for ScreenSelectionTree is `(next) => setEditAllowedScreens(next)` (direct value). Initial load: `setEditAllowedScreens(normalizeAllowedScreens(group?.allowedScreens))`.
- **ScreenSelectionTree.js**: No structural change required; single event path for write/approve (`onChange` with `e.target.checked`) already in place.
- Build and frontend restart confirmed done (handoff).

---

## 5. Verification

- Run verification per `.cursor/commands/verify.md` (health check; frontend).
- Execute parent §3 test cases **TC-01 through TC-07** (edit: uncheck/check 수정, uncheck/check 승인; save→PUT/GET; initial load; create dialog toggle).
- Record results in **§5 Test results** below.

### §5 Test results

### Test run date

- 2025-03-04

### Health check

| Target | Command / Check | Result |
|--------|-----------------|--------|
| Backend 9200 | `curl -s http://localhost:9200/api/health` | 200, JSON OK |
| Frontend 3001 | `curl -s -o /dev/null -w "%{http_code}" http://localhost:3001` | 200 |

### Browser automation (TC-01–TC-07)

**Tool**: cursor-ide-browser. **Base URL**: http://localhost:3001. Login: admin / admin123. Navigated to 권한 그룹 관리, opened Edit for ADMIN group.

| ID | Result | Notes |
|----|--------|-------|
| TC-01 | **Fail** | Edit dialog — 수정 (write) for 사용자 관리: clicked checkbox ref e110 ("사용자 관리 수정") and label ref e133 ("수정"). Expected: checkbox unchecks immediately. Actual: ref e110 remained `checked` after both clicks. Write checkbox did not toggle. |
| TC-02 | Skip | Blocked by TC-01 (could not verify check after uncheck). |
| TC-03 | Pass | 승인 (approve) for 승인 대기 ref e106: click toggled state (uncheck observed). |
| TC-04 | Pass | 승인 uncheck confirmed; second click toggled back. |
| TC-05 | Not run | Blocked by TC-01; save→PUT/GET not executed. |
| TC-06 | Not run | Initial load not executed. |
| TC-07 | Not run | Create dialog not executed. |

### Failure detail (TC-01)

- **What was checked**: Checkbox ref e110 (name "사용자 관리 수정"), label ref e133 (name "수정") in edit dialog.
- **Expected**: On click, checkbox visually unchecks (state changes to unchecked).
- **Actual**: After click on e110 and on e133, snapshot still showed e110 with `states: [checked, readonly]` (or [active, focused, checked, readonly]). Write checkbox did not respond.

### Verification summary

- **Health**: Backend 9200 OK, frontend 3001 OK.
- **Browser**: cursor-ide-browser; login → 권한 그룹 관리 → Edit ADMIN → edit dialog. TC-01 **Fail** (write checkbox does not toggle). TC-03, TC-04 **Pass** (approve checkbox toggles). TC-02, TC-05–TC-07 skipped or not run.
- **Re-verification**: Not passed. **Do not commit.** Create bugfix-5; hand off to Requirements; failure scope: **frontend**.

---

**Related**: `docs/requirements/20250303-permission-group-checkbox-not-working.md`, `docs/requirements/20250303-permission-group-checkbox-not-working-bugfix-3.md`

**Status**: Open (TC-01 failed; bugfix-5 created; hand off to Requirements)
