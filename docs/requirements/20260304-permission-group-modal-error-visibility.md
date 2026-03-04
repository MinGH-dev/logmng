# 20260304 - Permission group modal error visibility and underlying error fix

## 1. User requirement

### Requirement description

In **group permission management** (그룹별 권한관리), when granting the APPROVE_USER group's "로그 검색이력" (search-history) permission with scope "팀" (team), an error can occur on save. The error message is currently rendered at the top of the panel (outside the modal). Because the modal overlay has a higher z-index (1300), the message appears **behind** the overlay and the user cannot see or confirm it.

The requirement has two parts, in order: **(1) UX/tool improvement first** — show submit/API errors **inside** the create, edit, and delete modals so they are always visible; **(2)** then investigate and fix the underlying error (if any) when saving APPROVE_USER + search-history + team, and any related improvements, following role delegation rules.

### User scenario

1. User opens **Permission group management** and opens the **Edit** dialog for a permission group (e.g. APPROVE_USER).
2. User sets "로그 검색이력" (search-history) permission with scope "팀" (team) and clicks **Save**.
3. The update API returns an error (e.g. validation or 4xx/5xx).
4. **Problem**: The error message is rendered at the panel top (outside the modal). The create/edit/delete modal overlay has `z-index: 1300`, so the error div (in normal document flow, no z-index) appears **behind** the overlay. The user cannot see or read the message.
5. User expects to see the error **inside** the same modal so they can read it, correct the input, and retry without closing the dialog.

### Expected outcome

- **Part 1 (UX)**: Create, edit, and delete modals show their API/validation errors **inside** the modal content (same pattern as the users dialog with `usersDialogError`), so the user always sees the message. Error block uses `role="alert"` and consistent styling (e.g. `.user-management-error`). Placement: inside the dialog content area, e.g. above the action buttons.
- **Part 2 (Underlying error)**: After errors are visible, reproduce the failure for APPROVE_USER + search-history + scope team; identify root cause (caller auth, payload, backend validation, or DB). Either fix the bug so the save succeeds, or document the rule and return a clear, visible error inside the modal.
- **Regression**: Create and delete modals also show their API/validation errors inside the dialog, not only the edit modal.

---

## 2. Design

### 2.1 Security review (optional)

Not required for this requirement (no new PII/decryption/access scope). If the underlying error is 403, it is a visibility and messaging issue; the fix does not change access control.

### Technical design

#### Codebase summary

- **Frontend — PermissionGroupPanel.js**
  - A single panel-level `error` state (line 42) is used for list load failure, create submit failure, edit submit failure, and delete confirm failure. This `error` is rendered once at the top of the panel (lines 332–336) in a `user-management-error` div with `role="alert"`.
  - Create, edit, and delete dialogs do **not** have their own error state or error UI; they only call `setError(...)` on failure. The **users** dialog is different: it has `usersDialogError` (line 56) and renders it **inside** the dialog body (line 516), so user-assignment errors are visible inside the modal.
- **Frontend — PermissionGroupManagement.css**
  - `.permission-group-dialog-overlay` has `z-index: 1300`; the panel-level error div is in the main panel DOM and has no z-index, so it sits behind the overlay when a modal is open.
- **Backend**
  - PermissionGroupController: `PUT /api/permission-groups/{id}` checks `requireUserManagementAccess` and `requireWriteForManagement`; calls `PermissionGroupService.update(id, body)`.
  - PermissionGroupService: `validateAllowedScreens()` validates screenId, scope (`self`/`team`/`all` for scope-supporting screens including search-history), and screen functions. **No rule by group code** (e.g. APPROVE_USER) forbidding search-history + team. Possible error sources: 403 (caller), 400 (invalid payload/scope/screen), 404 (group not found), 500 (DB/runtime). Root cause for the user-reported error is to be confirmed **after** errors are visible in the modal.

#### Problem analysis

1. **Error visibility**: The panel-level error is rendered outside the modal. The overlay’s z-index (1300) places it above the error div, so when create/edit/delete set `error` on API failure, the message appears behind the modal and is not visible.
2. **Underlying error**: When saving APPROVE_USER + search-history + scope team, an error may be returned (400/403/500). Backend codebase has no explicit rule forbidding this combination; the actual cause (payload, validation, DB, or env) must be reproduced and fixed once the user can see the response in the modal.

#### Solution approach

**Part 1 — Show errors inside modals (UX/tool improvement)**

- Introduce per-dialog error state for create, edit, and delete (e.g. `createDialogError`, `editDialogError`, `deleteDialogError`, or a single keyed state). Render each error **inside** the corresponding modal content (e.g. above the form or action buttons), using the same `user-management-error` class and `role="alert"` as the users dialog.
- On create/edit/delete submit failure, set the relevant per-dialog error instead of (or in addition to) the panel-level `error`. Optionally restrict the panel-level `error` to list-load failures only. Clear the per-dialog error when the modal opens or when the user cancels/closes the modal.
- **UX**: Place the error block inside the dialog content, above or near the primary/secondary action buttons; use `role="alert"` for screen readers; optionally move focus to the error or primary button when an error is shown. Reuse existing `.user-management-error` styling for consistency.

**Part 2 — Fix underlying error**

- After Part 1 is deployed: reproduce the error with the same payload and user (APPROVE_USER group, search-history, scope team). Capture status code and response body.
- If root cause is **backend** (e.g. incorrect validation, DB constraint, or bug in service/controller): Backend agent fixes it and documents any intentional rule. If root cause is **frontend** (e.g. wrong payload or serialization): Frontend agent fixes it. If the combination is intentionally disallowed, document the rule and ensure the API returns a clear, visible error message that is shown inside the modal.

### Change file list

**(Confirmed by Frontend agent after Part 1 implementation.)**

#### Frontend

- `frontend/src/components/PermissionGroupManagement/PermissionGroupPanel.js`
  - Added state: `createDialogError`, `editDialogError`, `deleteDialogError`.
  - Create modal: render `createDialogError` inside dialog (above form); set on validation/API failure in `handleCreateSubmit`; clear on open and cancel.
  - Edit modal: render `editDialogError` inside dialog; set in `handleEditSubmit` on failure; clear in `openEdit` and on cancel.
  - Delete modal: render `deleteDialogError` inside dialog; set in `handleDeleteConfirm` on catch; clear on open and cancel.
  - Panel-level `error` is now used only for list-load failure (`loadGroups`); create/edit/delete no longer set it.
  - Reused `.user-management-error` and `role="alert"` for in-dialog error blocks.
- `frontend/src/components/PermissionGroupManagement/PermissionGroupManagement.css` — **not changed** (existing `.user-management-error` from UserManagement.css is sufficient).

#### Backend

- **None until root cause is confirmed.** If the root cause is backend (e.g. PermissionGroupService, PermissionGroupController, or DTO/deserializer), the implementing agent will add the relevant files (e.g. `PermissionGroupService.java`, `PermissionGroupController.java`) to §2 when implementation is done.

### Database changes

None expected for Part 1. Part 2 may require DB or schema change only if the underlying error is due to a constraint or migration; to be confirmed after reproduction.

---

## 3. Test approach

### Test case list (required)

| ID   | Type      | Scenario (input / condition)                                                                                                                                 | Expected result                                                                 | Verification   |
|------|-----------|---------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------|----------------|
| TC-01 | Exception | Edit permission group; set search-history scope to **team** for a group that has approve (e.g. APPROVE_USER); submit (Save).                                   | If the API returns an error, the error message is visible **inside** the edit modal (not behind the overlay).                                 | Manual / browser |
| TC-02 | Normal/Exception | After Part 2 fix: Edit (or create) permission group with APPROVE_USER and search-history scope **team**; submit.                                               | Either (a) save succeeds, or (b) a clear, visible error is shown **inside** the modal (e.g. validation message).                              | Manual / browser |
| TC-03 | Edge / Regression | Create modal: trigger an API or validation error (e.g. duplicate code, invalid payload); submit.                                                               | Error message is displayed **inside** the create modal.                         | Manual / browser |
| TC-04 | Edge / Regression | Delete modal: trigger an API error (e.g. 403 or "group in use"); confirm delete.                                                                               | Error message is displayed **inside** the delete modal.                         | Manual / browser |

### Test scenarios

#### Scenario 1: Edit modal — error visibility (TC-01)

1. Open Permission group management; open **Edit** for a group that has approve on search-history.
2. Set search-history scope to **team**; click **Save**.
3. If the API returns an error: confirm the error text is shown **inside** the edit modal (e.g. inline message or alert within the modal body), not behind the overlay or only in a toast.

#### Scenario 2: Post-fix save or visible error (TC-02)

1. After Part 2 is deployed: edit (or create) a permission group with APPROVE_USER and search-history scope **team**; submit.
2. Verify either save succeeds, or a clear error is shown **inside** the same modal.

#### Scenario 3: Create and delete modal error visibility (TC-03, TC-04)

1. **Create**: In the create modal, submit with data that causes an API/validation error; confirm the error appears **inside** the create modal.
2. **Delete**: In the delete confirmation modal, confirm delete when the API would return an error; confirm the error appears **inside** the delete modal.

### Test data

- Permission group with approve on search-history (e.g. APPROVE_USER or equivalent).
- User with permission to manage permission groups (e.g. system admin or group with write on permission-group-management).

### Test environment

- Frontend: `http://localhost:3001` (or per contract)
- Backend: `http://localhost:9200`
- Database: per project setup

### 3.5 Browser automation verification (optional)

- **Applicable TCs**: TC-01, TC-02, TC-03, TC-04.
- **Procedure**: Navigate to Permission group management → open Create / Edit / Delete modal → perform steps in scenario → take snapshot and verify error message is rendered **inside** the modal container (selector within `.permission-group-dialog` or equivalent), not only in a global toast or page-level alert.
- **Reference**: `docs/workflow/BROWSER-AUTOMATION-VERIFICATION-POLICY.md`.

---

## 4. Checklist

### Frontend verification

- [ ] Per-dialog error state and in-modal error UI implemented for create, edit, delete.
- [ ] Error visible inside modal when API returns error; `role="alert"` and styling consistent with users dialog.
- [ ] Create/edit/delete submit handlers set per-dialog error on failure; clear on open/cancel.

### Backend verification

- [ ] If root cause is backend: fix applied and verified; no unnecessary change if root cause is frontend-only.
- [ ] API test or manual verification for APPROVE_USER + search-history + team payload.

### Integration

- [ ] TC-01–TC-04 verified (manual or browser automation).
- [ ] Part 1 (modal error visibility) verified before Part 2 (underlying error fix).

### Documentation

- [ ] Requirement doc §2 change file list updated by implementing agent(s) with actual files changed.
- [ ] If underlying behavior is intentional (e.g. rule disallowing a combination), documented in code or requirement.

---

## 5. Test results

### Test run date

- [Date and time]

### Test results

#### Frontend

[Pass / Fail]

- [Result description]

#### Backend

[Pass / Fail]

- [Result description]

**Commands:**

```bash
# TC-01 / TC-02: Manual — open Permission group management → Edit group → set search-history scope team → Save; verify error in modal.
# TC-03: Manual — Create modal, submit duplicate code or invalid data; verify error in create modal.
# TC-04: Manual — Delete modal, confirm when API returns error; verify error in delete modal.
```

**Outcome:**

- [Item 1]
- [Item 2]

### Issues found and resolution

#### Issue 1: [Name]

**Cause**: [Cause description]

**Resolution**:

1. [Resolution 1]
2. [Resolution 2]

### Next steps

1. Implement Part 1 (show errors inside modals); verify TC-01, TC-03, TC-04.
2. Reproduce APPROVE_USER + search-history + team error; implement Part 2 per root cause; verify TC-02.
3. Update §2 change file list with actual files changed; complete §5 and §6 when done.

---

## 6. Error remedy result (cause and action) — for error/bug fix requirements only

Record root cause and actions under this requirement. Template: `docs/template/ERROR_FIX_RESULT_TEMPLATE.md`.

- **Requirement ID**: 20260304-permission-group-modal-error-visibility
- **Root cause**: [To be filled after Part 2 investigation — e.g. backend validation, DB constraint, or frontend payload.]
- **Actions taken**: [Summary of Part 1 (in-modal error) and Part 2 (underlying fix).]
- **Result**: [Verification method and result.]
- **Completed**: yyyy-MM-dd HH:mm

---

## 7. Final version (Korean) — add after all verification is complete

(Add after QA verification; see `docs/workflow/DOCUMENT-LANGUAGE-POLICY.md`.)

### 요건 요약 (한글)

- **요건 설명**: [§1 요약]
- **기대 결과**: [§1 기대 결과 요약]
- **검증 결과**: [§5 요약, 통과/실패]

---

**Author**: Requirements (orchestrated from Frontend, UX, Backend, QA input)  
**Date**: 2026-03-04  
**Status**: In progress
