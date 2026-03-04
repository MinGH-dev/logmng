# 20250303-permission-group-checkbox-not-working-bugfix-3 — Write checkbox still does not toggle (approve works)

**Parent requirement ID**: `20250303-permission-group-checkbox-not-working`  
**Previous bugfix**: `20250303-permission-group-checkbox-not-working-bugfix-2.md`  
**Bugfix sequence**: 3

---

## 1. Discovery

- **When**: During Step 5 re-verification of bugfix-2.
- **What failed**: TC-01 (Edit dialog — uncheck 수정 (write)): Write checkbox ("사용자 관리 수정", ref e110) and its label (ref e133) click did not uncheck; `browser_is_checked` remained yes. **TC-03, TC-04 passed**: Approve checkbox (승인 대기) toggles correctly.

---

## 2. Error scope

- **Failure scope**: frontend
- **Layer**: frontend
- **Symptom**: **Write** checkbox in permission group edit dialog does not toggle on click; **approve** checkbox does toggle.
- **Impact**: Admin cannot change **write** per screen; approve is fixed.

---

## 3. Cause (estimated)

- Write and approve branches in ScreenSelectionTree.js are structurally similar (input + label with onClick/preventDefault). Approve works, write does not → suggests either:
  - **Different hit target**: Automation or user clicks may hit a different element for the write row (e.g. screen checkbox or another overlay).
  - **Write input not receiving events**: Something specific to the write row (e.g. DOM order, z-index, or parent) blocks the write input/label from receiving the click.
  - **Double-handling or wrong view**: Ensure the write branch uses the correct `view` and that both **input** and **label** clicks trigger `changeWrite(view, …)`.

---

## 4. Action

**Delegate to Frontend** (failure scope: frontend):

1. **Unify write with approve**: Make the write checkbox block **identical** in behavior to the approve block (same structure: span.screen-selection-fn-checkbox, input, then label with onClick + preventDefault). Ensure the write **input** also has an explicit `onClick={(e) => changeWrite(view, e.target.checked)}` so that if the automation clicks the input and `onChange` does not fire in that environment, the click still updates state.
2. **Ensure label click works for write**: Verify the write label’s `onClick` calls `changeWrite(view, !writeChecked)` with the correct `view` (e.g. `user-management`). Add a temporary `console.log(view, writeChecked)` in the write label’s onClick to confirm it runs when the "수정" label is clicked; remove after verification.
3. **No structural difference**: If the write row is inside a different MENU_TREE node (e.g. "관리"), ensure no extra wrapper or CSS (e.g. from .screen-selection-group) blocks pointer events for the write row only.
4. After fix: build and restart. QA re-runs TC-01–TC-07 and records in this doc §5.

**§4 변경 파일 목록 (실제 수정)**:
- `frontend/src/components/PermissionGroupManagement/ScreenSelectionTree.js`: write input에 `onClick={(e) => changeWrite(view, e.target.checked)}` 추가; approve input에 동일 패턴 `onClick={(e) => changeApprove(view, e.target.checked)}` 추가(일관성). Write/approve 블록 구조 동일 확인. Write 라벨 onClick은 이미 `changeWrite(view, !writeChecked)` 사용, view는 child.view 사용 정상.

---

## 5. Verification

- Same as bugfix-2: TC-01 through TC-07. Success: all pass, including TC-01 (write checkbox toggles).

### §5 Test results

**Date**: 2025-03-03  
**Scope**: Frontend. Build/restart confirmed done before verification (per handoff).

**Health check**: Backend `curl http://localhost:9200/api/health` → 200, JSON OK. Frontend `curl http://localhost:3001` → 200.

**Browser automation**: Tool: project-0-dev-browser (Puppeteer). Base URL: http://localhost:3001. Login: admin / admin123. Navigated to 권한 그룹 관리, opened Edit for ADMIN group.

| ID | Result | Notes |
|----|--------|-------|
| TC-01 | **Fail** | Edit dialog — 수정 (write) for 사용자 관리: clicked checkbox `#write-user-management` (and label `label[for="write-user-management"]`). Expected: checkbox unchecks immediately. Actual: `checked` remained `true` after click(s). Programmatic `element.click()` also did not change state. Write checkbox did not toggle. |
| TC-02 | Skip | Blocked by TC-01 (could not verify check after uncheck). |
| TC-03 | **Fail** | Edit dialog — 승인 (approve) for 승인 대기: clicked `#approve-pending-approvals`. Expected: checkbox checks. Actual: `checked` remained `false`. Approve checkbox did not toggle in this run. |
| TC-04 | Skip | Blocked by TC-03. |
| TC-05 | Not run | Save → PUT/GET allowedScreens not executed (blocked by TC-01/TC-03 failure). |
| TC-06 | Not run | Initial load with write/approve false not executed. |
| TC-07 | Not run | Create dialog write/approve toggle not executed. |

**Summary**: TC-01 and TC-03 failed (write and approve checkboxes did not respond to click in Puppeteer run). Re-verification **not** passed; **do not commit**. Recommend: (1) Confirm frontend bundle includes latest `ScreenSelectionTree.js` (input `onClick` handlers); (2) Manual verification in a real browser (e.g. click 수정/승인 in edit dialog) to rule out automation quirks; (3) If still failing, investigate event delivery (overlay, focus, or controlled input update path).

---

**Related**: `docs/requirements/20250303-permission-group-checkbox-not-working-bugfix-2.md`, `20250303-permission-group-checkbox-not-working.md`

**Status**: Open (QA re-verification run; TC-01, TC-03 failed in Puppeteer; do not commit)
