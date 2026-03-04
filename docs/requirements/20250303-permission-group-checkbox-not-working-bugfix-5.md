# 20250303-permission-group-checkbox-not-working-bugfix-5 — Write checkbox still does not toggle in edit dialog (approve works)

**Parent requirement ID**: `20250303-permission-group-checkbox-not-working`  
**Previous bugfix**: `20250303-permission-group-checkbox-not-working-bugfix-4.md`  
**Bugfix sequence**: 5

---

## 1. Discovery

- **When**: During Step 5 verification of bugfix-4 (per parent §3 test cases TC-01–TC-07).
- **What failed**: **TC-01** (Edit dialog — uncheck 수정 (write) for 사용자 관리): Click on write checkbox (ref e110 "사용자 관리 수정") and on label (ref e133 "수정") did not uncheck; snapshot showed e110 remained `checked`. **TC-03, TC-04 passed**: Approve checkbox (승인 대기 승인 ref e106) toggled correctly in the same run.

---

## 2. Error scope

- **Failure scope**: frontend
- **Layer**: frontend
- **Symptom**: **Write** (수정) checkbox in permission group edit dialog does not toggle on click; **approve** (승인) checkbox does toggle.
- **Impact**: Admin cannot change write permission per screen in edit dialog; approve is working.

---

## 3. Cause (estimated)

- Bugfix-4 ensured edit dialog `onChange` uses direct value `setEditAllowedScreens(next)` and ScreenSelectionTree uses single event path (`onChange` with `e.target.checked`). Approve works, write does not in the same dialog → suggests:
  - **Write input/label not receiving or handling events**: e.g. different DOM order, overlay, or React synthetic event handling for the write row (사용자 관리 수정) vs approve row (승인 대기 승인).
  - **Controlled value for write not updating**: Parent state for write may not be updated when changeWrite runs (e.g. closure or wrong view/screenId).
  - **Automation vs real browser**: cursor-ide-browser click may hit a different target for write; manual verification recommended to confirm whether the issue is automation-only or also in real browser.

---

## 4. Action

**Delegate to Frontend** (failure scope: frontend):

1. Confirm in `ScreenSelectionTree.js` that the **write** checkbox branch (사용자 관리, 권한 그룹 관리, etc.) uses the same pattern as the approve branch: `onChange={(e) => changeWrite(view, e.target.checked)}` with correct `view` (e.g. `child.view`). Ensure the **label** for write is associated with the input (e.g. `htmlFor` or wrapping) so that label clicks trigger the input.
2. Add temporary logging in `changeWrite(view, checked)` to verify (a) it is invoked on click, (b) `view` is correct, (c) `onChange(next)` is called with the updated array. Remove after verification.
3. Check for any CSS or wrapper (e.g. `.screen-selection-fn-checkbox`) that could block pointer events only for the write row (e.g. first write row in the list).
4. After fix: build and restart. QA re-runs TC-01–TC-07 and records in this doc §5.

---

## 5. Verification

- Same as parent: TC-01 through TC-07. Success: all pass, including TC-01 (write checkbox toggles in edit dialog).

### §5 Test results

*(To be filled by QA after re-verification.)*

---

**Related**: `docs/requirements/20250303-permission-group-checkbox-not-working.md`, `docs/requirements/20250303-permission-group-checkbox-not-working-bugfix-4.md`

**Status**: Open (hand off to Requirements; delegate to Frontend by scope)
