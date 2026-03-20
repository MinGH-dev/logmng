# 20260317 - Search screen decrypt permission UI and request-reason modal

## 1. User requirement

### Requirement description

- **Permission enforcement (UI)**: On the "검색하기" (search) screen, users whose permission group does **not** have the decrypt feature enabled can still trigger decrypt-related actions (e.g. "복호화 승인 요청" button and per-row "복호화" button). This is a permission enforcement bug: the backend correctly returns 403 for the decrypt API when the user lacks `screenFunctions.main.decrypt`, but the UI does not gate these actions.
- **Request reason placement**: The "요청 사유" (request reason) input is currently on the main search form. The requirement is to collect it in a **modal** that opens when the user submits a decrypt approval request (i.e. at approval-request time), not as a persistent field on the main search screen.

### User scenario

1. User logs in and has access to the "검색하기" (main) screen but their permission group does **not** have decrypt checked (search-only user).
2. User opens the search screen, selects a log type (e.g. 이미지 로그), runs a search.
3. **Problem**: The user can still see and use "복호화 승인 요청" and the per-row "복호화" button; only when they actually call the decrypt API does the backend return 403. The UI should prevent this by disabling/hiding decrypt actions and showing a clear message.
4. User whose permission group **has** decrypt: Clicks "복호화 승인 요청". **Current**: Request reason is a field on the main form. **Required**: A modal opens; user enters 요청 사유 in the modal and submits; the reason is sent with the approval request and the modal closes.

### Expected outcome

- When the user does **not** have decrypt request permission (`screenFunctions.main.decrypt !== true` and not system admin):
  - The decrypt request action (button/control) is **disabled** or hidden.
  - The message **"복호화 권한이 없습니다."** (You do not have decrypt permission) is displayed where appropriate (e.g. in the decrypt column or next to the disabled control).
  - The "복호화 승인 요청" button is disabled or hidden, and the same message is shown.
- When the user **has** decrypt permission:
  - "복호화 승인 요청" opens a **modal**; the user enters **요청 사유** (request reason) in the modal and submits; the request is sent with that reason and the modal closes.
  - The main search screen form **no longer** contains a persistent "요청 사유" input; request reason is collected only in the modal at approval-request time.
- Backend behavior remains unchanged: decrypt API and (if applicable) search-history create continue to enforce permission and return 403 when the user lacks decrypt.

---

## 2. Design

### 2.1 Security review

- **Scope**: Access control (decrypt permission) and UX alignment with backend enforcement.
- **Risks**: Without UI gating, users without decrypt permission can still attempt requests and see 403; this is confusing and may encourage bypass attempts. Aligning UI with backend (disable/hide + message) reduces confusion and makes permission boundaries clear.
- **Recommendation**: Keep backend enforcement as-is (DecryptController already returns 403 `FUNCTION_NOT_ALLOWED` when `!hasDecryptForMain`). Frontend must derive `hasDecryptPermission` from the same source of truth (`screenFunctions.main.decrypt` or `isSystemAdmin`) and disable/hide decrypt actions and show "복호화 권한이 없습니다." when the user lacks permission. No new API or permission model; UI-only enforcement to match backend.

### Technical design

#### Codebase summary

- **Frontend — search screen**: The main (검색하기) view is rendered in `App.js` when `currentView === 'main'` and `selectedLogType` is set; it renders `LogGrid` with `logType`, `initialSearchParams`, `initialSearchApprovalId`, `onInitialSearchDone`. `LogGrid` does **not** receive `user` or any permission flag; it always shows the "복호화 승인 요청" button and the inline "요청 사유 (필수)" input, and passes `searchHistoryId` to `ImageLogTable`. `ImageLogTable` renders a "복호화" button per row and calls `POST /api/logs/decrypt/{logType}`; it has no prop for decrypt permission.
- **Frontend — auth**: `App.js` holds `user` state (from `GET /api/auth/check` and login). The response includes `screenFunctions` (e.g. `screenFunctions.main.decrypt`). Other screens (e.g. `PendingApprovals`, `UserManagement`, `PermissionGroupManagement`) use `getScreenFunctions(user)` to enable/disable actions (e.g. `canApprove = screenFunctions?.['pending-approvals']?.approve === true`). The search screen does not use `screenFunctions` for decrypt.
- **Backend**: `DecryptController` already checks `authService.hasDecryptForMain(httpRequest)` and returns 403 with `FUNCTION_NOT_ALLOWED` when the user lacks main decrypt permission. Search-history create (`POST /api/search-history`) does not currently reject callers who lack main decrypt; it creates a pending request. Optionally, backend could reject create when `!hasDecryptForMain` to avoid creating pending requests from users without decrypt (design note; not mandatory for this requirement).

#### Problem analysis

1. **Permission UI gap**: LogGrid and ImageLogTable never receive or use `screenFunctions.main.decrypt`. So users without decrypt permission still see and can click "복호화 승인 요청" and per-row "복호화"; they only get 403 after the API call.
2. **Request reason on main form**: Request reason is a persistent input in `LogGrid` next to the "복호화 승인 요청" button. The requirement is to collect it in a modal that opens when the user clicks that button, and remove it from the main form.

#### Solution approach

**Frontend**

- **App.js**: When rendering `LogGrid` for the main view, compute `hasDecryptPermission = user?.isSystemAdmin === true || getScreenFunctions(user)?.['main']?.decrypt === true` and pass it as a prop (e.g. `hasDecryptPermission`) to `LogGrid`.
- **LogGrid.js**:
  - Accept `hasDecryptPermission` (boolean). When `hasDecryptPermission === false`: do not show the inline "요청 사유" input; show the "복호화 승인 요청" button as disabled (or hide it) and display the message "복호화 권한이 없습니다." in the actions area (e.g. next to or instead of the button). When `hasDecryptPermission === true`: remove the inline "요청 사유" field from the main form; instead, when the user clicks "복호화 승인 요청", open a **modal** that contains a text input for "요청 사유" (required, max 500 chars) and a submit button; on submit, call `createSearchHistory(logType.id, searchParams, reason)` with the modal value, then close the modal and show success/error as today.
  - Pass `hasDecryptPermission` to `ImageLogTable` so the table can disable or hide the per-row decrypt action and show "복호화 권한이 없습니다." (e.g. in the decrypt column or as tooltip) when false.
- **ImageLogTable.js**: Accept `hasDecryptPermission` (boolean). When `hasDecryptPermission === false`: for the decrypt column, do not render an active "복호화" button; render disabled button or static text "복호화 권한이 없습니다." (or tooltip with that message). When `hasDecryptPermission === true`, keep current behavior (decrypt button per row).
- **Configuration UI**: No change; permission group management already allows granting/revoking decrypt for main (req 20260306).

**Backend**

- No change required for this requirement. DecryptController already enforces decrypt permission and returns 403. Optionally, SearchHistoryController create could reject requests when the user does not have decrypt for main (to avoid creating pending requests from search-only users); if implemented, return 403 with a clear code (e.g. `FUNCTION_NOT_ALLOWED`).

**Contract / Spec**

- In `docs/contract.md` or `docs/api-definition.md`, state that when `screenFunctions.main.decrypt` is false (and user is not system admin), the search screen UI must disable or hide decrypt-related actions and display "복호화 권한이 없습니다." (or equivalent). This aligns doc with the new UI behavior.

**Cursor tool update targets**

- **`.cursor/skills/search-history-decrypt-domain/SKILL.md`**: Note that the search screen (main) decrypt UI (approval request button and per-row decrypt button) is gated by `screenFunctions.main.decrypt`; when the user lacks decrypt permission, the UI disables these actions and shows "복호화 권한이 없습니다."
- **`.cursor/skills/api-permission-map/SKILL.md`**: Already describes decrypt API permission; optionally add that the search screen UI must hide/disable decrypt actions when the user lacks main decrypt.

### Affected scopes and change targets (verification)

Per `docs/workflow/REQUIREMENTS-CHANGE-TARGET-CHECKLIST.md`:

| Scope | Affected? | §2 subsection and change file list complete? |
|-------|-----------|-----------------------------------------------|
| Backend | No (optional note only) | Yes |
| Frontend (config UI + view screen) | Yes | Yes — view screen (LogGrid, ImageLogTable); config UI unchanged |
| DB | No | Yes |
| Contract / Spec | Yes | Yes — one-line doc alignment |
| Cursor tools (skills, specs) | Yes | Yes — skills update targets listed |

Pattern used: **Permission or screen-access change** (§3.2). Touchpoints: frontend view screen (search main) for permission-based disable/message and modal; contract/spec and auth/permission-related skills for consistency.

### Planned change file list (expected change targets)

**(Planned at authoring. Implementing agent (Step 4) confirms or amends this list when implementation is complete.)**

#### Frontend

- `frontend/src/App.js`
  - Compute `hasDecryptPermission` from `user` (e.g. `getScreenFunctions(user)?.['main']?.decrypt === true` or `user?.isSystemAdmin === true`) and pass it as prop `hasDecryptPermission` to `LogGrid` when rendering the main view.
- `frontend/src/components/LogGrid.js`
  - Add prop `hasDecryptPermission`. When false: disable or hide "복호화 승인 요청" button and show "복호화 권한이 없습니다."; remove or hide the inline "요청 사유" input. When true: remove the inline "요청 사유" from the main form; add a modal that opens on "복호화 승인 요청" click, with request-reason input and submit; on submit call `createSearchHistory` with the modal reason and close modal.
  - Pass `hasDecryptPermission` to `ImageLogTable`.
- `frontend/src/components/ImageLogTable.js`
  - Add prop `hasDecryptPermission`. When false: in the decrypt column, do not render an active decrypt button; show disabled control or text "복호화 권한이 없습니다." When true: keep current per-row decrypt button behavior.
- `frontend/src/components/LogGrid.css`
  - Styles for the request-reason modal (`.log-grid-request-reason-modal-overlay`, `.log-grid-request-reason-modal`, etc.) and the permission message (`.decrypt-permission-message` in actions area).
- `frontend/src/components/ImageLogTable.css`
  - `.decrypt-permission-message` for the decrypt column when user lacks permission (req 20260317).

#### Backend

- No file changes required. Optional: `SearchHistoryController` create could return 403 when `!hasDecryptForMain`; if implemented, add to change list.

#### Contract / Spec

- `docs/contract.md` or `docs/api-definition.md`
  - Add a short note that when the user does not have decrypt permission for main (`screenFunctions.main.decrypt` false and not system admin), the search screen UI disables or hides decrypt actions and displays "복호화 권한이 없습니다."

#### Cursor tools

- `.cursor/skills/search-history-decrypt-domain/SKILL.md`
  - Add that search screen decrypt UI (approval request + per-row decrypt) is gated by `screenFunctions.main.decrypt`; when absent, UI shows "복호화 권한이 없습니다."
- `.cursor/skills/api-permission-map/SKILL.md` (optional)
  - Note that search screen UI must hide/disable decrypt actions when user lacks main decrypt.

---

## 3. Test approach

### Test case list (required)

| ID | Scope | Type | Scenario (input / condition) | Expected result | Verification (unit / integration / manual) |
|----|-------|------|-----------------------------|-----------------|--------------------------------------------|
| TC-01 | Frontend | Normal | User has `screenFunctions.main.decrypt === true`; on search screen, click "복호화 승인 요청". | Modal opens with 요청 사유 input; user enters reason and submits; createSearchHistory called with reason; modal closes; success/error shown. | Unit (React test) or Manual |
| TC-02 | Frontend | Normal | User has decrypt permission; submit request from modal with empty 요청 사유. | Validation error (e.g. "요청 사유를 입력해 주세요."); request not sent or same as current behavior. | Unit or Manual |
| TC-03 | Frontend | Normal | User does **not** have decrypt permission (`screenFunctions.main.decrypt` false, not system admin); on search screen. | "복호화 승인 요청" is disabled or hidden; message "복호화 권한이 없습니다." is visible; no inline 요청 사유 field. | Unit or Manual |
| TC-04 | Frontend | Normal | User does not have decrypt permission; image log table is shown. | Per-row decrypt control is disabled or shows "복호화 권한이 없습니다."; no active decrypt button. | Unit or Manual |
| TC-05 | Frontend | Regression | User with decrypt permission runs search and clicks per-row "복호화". | Decrypt API is called as today; no regression. | Integration or Manual |
| TC-06 | Backend | Regression | User without decrypt permission calls POST /api/logs/decrypt/{logType} directly. | 403, code FUNCTION_NOT_ALLOWED (existing behavior). | Unit or Integration |
| TC-07 | Integration | Normal | Login as user without main decrypt; open search screen; verify UI shows message and disabled controls; login as user with main decrypt; verify modal flow for approval request. | As per TC-03, TC-04, TC-01. | Manual / browser |

### Test scenarios

#### Scenario 1: Permission-based UI (no decrypt)

1. Log in as a user whose permission group has main screen but **decrypt unchecked**.
2. Open "검색하기", select image log type, run a search.
3. Verify: "복호화 승인 요청" is disabled or hidden; "복호화 권한이 없습니다." is shown; no 요청 사유 field on main form.
4. Verify: In the result table, decrypt column shows disabled or "복호화 권한이 없습니다.", not an active button.

#### Scenario 2: Request reason in modal (with decrypt)

1. Log in as a user with decrypt permission on main.
2. Open "검색하기", select image log type, run a search.
3. Click "복호화 승인 요청".
4. Verify: A modal opens with 요청 사유 input (required, max 500).
5. Enter reason, submit. Verify: createSearchHistory is called with that reason; modal closes; success or error message shown.
6. Verify: Main form does not contain a persistent "요청 사유" field.

### Test data

- Two test users (or permission groups): one with main + decrypt, one with main but no decrypt. Use existing auth and permission group setup; no new DB fixtures required for UI tests.

### Test environment

- Frontend: http://localhost:3001 (or per contract)
- Backend: http://localhost:9200
- Database: per project (e.g. PostgreSQL)

### 3.5 Browser automation verification (optional)

- **Applicable TCs**: TC-03, TC-04, TC-01, TC-07 (manual/browser).
- **Procedure**: Use browser MCP to navigate to search screen, log in as user without decrypt → snapshot to confirm message and disabled controls; log in as user with decrypt → click "복호화 승인 요청" → snapshot to confirm modal with 요청 사유 input. Reference: `docs/workflow/BROWSER-AUTOMATION-VERIFICATION-POLICY.md`.

---

## 4. Checklist

### Frontend verification

- [ ] API parameters validated (createSearchHistory with reason from modal)
- [ ] UI behavior confirmed (permission message, disabled controls, modal flow)
- [ ] Error handling verified (empty reason, 403 from API)

### Backend verification

- [ ] No change required; optional create rejection can be tested if implemented
- [ ] Regression: decrypt API still returns 403 when user lacks main decrypt

### Integration

- [ ] End-to-end: user without decrypt sees message and disabled UI; user with decrypt uses modal for request reason
- [ ] Edge cases: system admin always has decrypt; modal validation

### Documentation

- [x] Requirement doc completed
- [x] Contract/spec and skills updated as in change list

---

## 5. Test results

### Test run date

- 2026-03-17 (Frontend implementation)

### Test results

#### Frontend

- **Build**: `npm run build` — exit 0 (success).
- **Unit tests**: `npm test -- --watchAll=false` — 11 passed, 1 failed (59 tests total). The single failure is in `UserActivityLogList.test.js` (request params assertion); it is pre-existing and unrelated to this requirement (LogGrid/ImageLogTable decrypt permission UI).
- **§3 TC verification**: TC-01–TC-07 to be run by QA (manual/browser or integration).

#### Backend

- No change; N/A.

**Commands:**

- `cd frontend && npm run build`
- `cd frontend && npm test -- --watchAll=false`

**Outcome:**

- Build: success. Tests: 1 pre-existing failure; new code (App, LogGrid, ImageLogTable) has no dedicated unit tests yet; QA to run §3 test cases.

### Issues found and resolution

- None for this requirement. UserActivityLogList.test.js failure is out of scope.

### Next steps

1. ~~Implement Frontend changes (App, LogGrid, ImageLogTable, optional CSS).~~ Done.
2. ~~Update contract/spec and Cursor skills per change list.~~ Done.
3. QA: Run §3 test cases (TC-01–TC-07) and record results; optional browser automation per §3.5.

---

## 6. Error remedy result (cause and action) — for error/bug fix requirements only

- **Requirement ID**: 20260317-search-decrypt-permission-ui
- **Root cause**: Search screen UI did not use `screenFunctions.main.decrypt` to gate decrypt actions; request reason was on main form instead of in a submit-time modal.
- **Actions taken**: [To be filled after implementation]
- **Result**: [To be filled after verification]
- **Completed**: —

---

**Author**: Requirements subagent  
**Date**: 2026-03-17  
**Status**: Implementation complete (Frontend); QA verification pending
