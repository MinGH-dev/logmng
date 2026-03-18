# Frontend-scope validation: 20260318-menu-and-permission-restructure

**Requirement doc**: `docs/requirements/20260318-menu-and-permission-restructure.md`  
**Scope**: Frontend only (no implementation; consistency and implementability check)  
**Date**: 2026-03-18

---

## 1. Summary

Validation of the requirement document from the **Frontend** scope: §2 design and Planned change file list vs. actual codebase, contract/spec alignment, and §3 test cases TC-01–TC-04.

**Result**: **Pass with one gap** — one file is missing from the Planned change file list; all other Frontend items are consistent and implementable.

---

## 2. §2 and Planned change file list vs. codebase

### 2.1 File paths and existence

| Doc path | Actual path | Exists |
|----------|-------------|--------|
| `frontend/src/constants/menuTree.js` | `frontend/src/constants/menuTree.js` | ✓ |
| `frontend/src/App.js` | `frontend/src/App.js` | ✓ |
| `frontend/src/components/AppSidebar.js` | `frontend/src/components/AppSidebar.js` | ✓ |
| `frontend/src/components/PermissionGroupManagement/ScreenSelectionTree.js` | same | ✓ |
| `frontend/src/components/PermissionGroupManagement/PermissionGroupPanel.js` | same | ✓ |
| `frontend/src/constants/screenFunctionDescriptions.js` | same | ✓ |
| `frontend/src/components/LogGrid.js` | `frontend/src/components/LogGrid.js` | ✓ |
| `frontend/src/utils/security.js` | `frontend/src/utils/security.js` | ✓ |

All eight Frontend files in the Planned change list exist at the stated paths.

### 2.2 Current state vs. doc description

- **menuTree.js**: "로그 검색" has children `search-main` (검색하기), `search-history` (검색 이력); "이력·승인" has `activity-log`, `pending-approvals` (승인 대기). `ALLOWED_SCREEN_IDS` includes `main`. Doc’s “current” description matches.
- **App.js**: `currentView` includes `main`; `getFirstAllowedScreen` fallback `'main'`; `handleSearchMain`; LogGrid only when `currentView === 'main'` and `selectedLogType`; `hasDecryptPermission` uses `getScreenFunctions(user)?.['main']?.decrypt`. Matches doc.
- **AppSidebar**: `onSearchMain` prop; `handleChildClick` calls `onSearchMain()` when `child.view === 'main'`. Matches doc.
- **ScreenSelectionTree**: Uses `MENU_TREE` and `screenFunctionDescriptions` (`SCREENS_WITH_DECRYPT`). Doc’s “driven by MENU_TREE and screenFunctionDescriptions” is correct.
- **PermissionGroupPanel**: `normalizeAllowedScreens` uses `const decryptScreens = ['main']`. Doc’s “decryptScreens list must include both [pb-feplog, java-fw-imagelog]” is the intended change; no conflict.
- **screenFunctionDescriptions.js**: `SCREENS_WITH_DECRYPT = ['main']`. Doc’s “add pb-feplog, java-fw-imagelog; remove main or leave deprecated” is consistent.
- **LogGrid.js**: `fetchDecryptionAllowed` uses `screen=main` (line 76). Doc’s “Pass or derive screen (pb-feplog vs java-fw-imagelog) from logType” is the intended change; no conflict.
- **security.js**: `deriveScreenFunctionsFromAllowed` exists and sets `read: true` per screenId; no per-screen decrypt derivation today. Doc’s “if still used” note is implementable (e.g. backend sends `screenFunctions`, or frontend uses `screenFunctionDescriptions` for decrypt capability).

### 2.3 Contract and spec

- **docs/contract.md**: “화면 ID 목록” currently lists `main`, `search-history`, `activity-log`, … (no `pb-feplog`, `java-fw-imagelog`). The requirement explicitly includes Contract/Spec update in scope; Frontend is to align with contract once updated. No inconsistency.
- **specs/permission-group-hierarchy.spec.yaml**: §4.1 lists `main`, `search-history`, `pending-approvals`, etc. Requirement §2 says spec will be updated (§4.1, §1.1.1, §4.3, §4.4, §5). Frontend implementation is consistent with “implement after or in sync with spec/contract.”

---

## 3. §3 Test cases TC-01–TC-04 (Frontend)

| ID   | Scope    | Scenario | Feasibility |
|------|----------|----------|-------------|
| TC-01 | Frontend | User with only pb-feplog → sidebar “로그 검색” shows only “PB FEP Log”; click opens PB FEP Log search | ✓ Requires backend (or mock) to return `allowedScreenIds: ['pb-feplog']`. Then sidebar filtering and navigation are verifiable manually/in browser. |
| TC-02 | Frontend | User with both pb-feplog and java-fw-imagelog → both items under “로그 검색”; each click opens correct log type | ✓ Same; verifiable via menu and view/logType. |
| TC-03 | Frontend | Sidebar “이력·승인” order: 활동 이력 → 검색 이력 → 복호화 승인 관리 | ✓ Purely menu order; no backend dependency. |
| TC-04 | Frontend | Permission group edit: “로그 검색” section has “PB FEP Log”, “Java FW Image Log” with optional decrypt each; no “검색하기” | ✓ Driven by MENU_TREE and screenFunctionDescriptions; verifiable in create/edit dialog. |

All four are correctly scoped to Frontend and feasible (manual/browser or with backend/mock for TC-01/TC-02).

---

## 4. Report

### (a) Inconsistencies between requirement doc and frontend codebase or contract

- **None.** The doc’s “current” state matches the codebase. The “target” state (new views `pb-feplog`, `java-fw-imagelog`; search history under 이력·승인; label 복호화 승인 관리; removal/deprecation of `main` for log search) is described consistently. Contract/spec do not yet define `pb-feplog`/`java-fw-imagelog`; the requirement assigns their update to the same change, so Frontend is not in conflict.

### (b) Missing or incorrect file/constant/route in the doc

- **Missing file in Planned change list**  
  - **File**: `frontend/src/components/PendingApprovals/PendingApprovals.js`  
  - **Reason**: §1 requires renaming “the menu and **screen** label” to “복호화 승인 관리”. The menu label is covered by `menuTree.js`. The **screen (page) label** is in PendingApprovals: `<h2>승인 대기</h2>` and `ariaLabel="승인 대기 목록"` (and any other “승인 대기” copy in that component). To satisfy “Label ‘복호화 승인 관리’ is used **everywhere** the former ‘승인 대기’ was shown (menu, **page title**, a11y)”, this file should be in the Frontend change list.  
  - **Recommendation**: Add to §2 “Planned change file list” → Frontend:  
    - `frontend/src/components/PendingApprovals/PendingApprovals.js` — Update page title and a11y from “승인 대기” to “복호화 승인 관리” (e.g. `<h2>`, `ariaLabel` for the list).

- **Paths and constants**: No incorrect paths or constants found. All listed Frontend paths exist; described changes (new views, labels, screen IDs, decrypt/allowed screen param) align with existing structure and contract/spec evolution.

### (c) Pass/fail summary for Frontend-scope consistency

- **Pass with one gap.**  
  - **Pass**: §2 and the eight listed Frontend files match the codebase; described behavior (menu, views, permission UI, decrypt/allowed, security derivation) is consistent and implementable. §3 TC-01–TC-04 are feasible and correctly scoped for Frontend. Contract/spec are explicitly in scope for update; no Frontend contradiction.  
  - **Gap**: Add `frontend/src/components/PendingApprovals/PendingApprovals.js` to the Planned change list for the “복호화 승인 관리” **screen** label and a11y so the doc fully matches implementability.

---

## 5. Optional follow-up

- When implementing, consider updating **ScreenSelectionTree.test.js** (and any other tests that assert “승인 대기” labels) so that after the menu label becomes “복호화 승인 관리”, assertions use the new label (e.g. “복호화 승인 관리 조회 범위”). The requirement does not need to list test files explicitly; this is an implementation detail.
