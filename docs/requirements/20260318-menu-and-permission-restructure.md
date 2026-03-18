# 20260318 - Menu and permission restructure

## 1. User requirement

### Requirement description

Restructure the sidebar menu and permission model as follows:

1. **Search History (검색 이력)**  
   - Menu label: "검색 이력" (unchanged).  
   - Menu location: under the "이력·승인" group, on the row immediately after "활동 이력" (Activity Log).

2. **Log search (검색하기)**  
   - Remove the "검색하기" menu item.  
   - Under "로그 검색", show two menu items directly: "PB FEP Log" and "Java FW Image Log".  
   - In permission groups, replace the single "검색하기" permission with two separate targets: "PB FEP Log" and "Java FW Image Log", each configurable with read and optional decrypt independently.

3. **Pending Approval (승인 대기)**  
   - Rename the menu and screen label to "복호화 승인 관리".  
   - The screen ID remains `pending-approvals`; only the display label changes.

### User scenario

1. Admin opens the permission group edit screen and sees under "로그 검색" two checkable items: "PB FEP Log" and "Java FW Image Log", each with optional decrypt; "검색하기" is no longer present.  
2. User with only "PB FEP Log" access sees in the sidebar under "로그 검색" only "PB FEP Log"; selecting it opens the log search view for that log type.  
3. User with only "Java FW Image Log" (and decrypt) can request decryption for that log type only; search/decrypt for the other log type is not allowed.  
4. In the sidebar, under "이력·승인", the order is: "활동 이력" → "검색 이력" → "복호화 승인 관리".  
5. **Problem**: Current single "검색하기" (main) does not allow per–log-type permission; menu mixes search entry with log-type selection.

### Expected outcome

- Sidebar "이력·승인": first row "활동 이력", second row "검색 이력", third row "복호화 승인 관리".  
- Sidebar "로그 검색": only "PB FEP Log" and "Java FW Image Log" as direct items; no "검색하기" row.  
- Permission configuration: two separate screens (e.g. `pb-feplog`, `java-fw-imagelog`) with read and optional decrypt each; API and decrypt enforcement are per log type (pb_feplog ↔ pb-feplog screen, java_fw_imglog ↔ java-fw-imagelog screen).  
- Label "복호화 승인 관리" is used everywhere the former "승인 대기" was shown (menu, page title, a11y); screen_id remains `pending-approvals`.

**Note**: Menu and permission structure must align with `docs/contract.md`, `specs/permission-group-hierarchy.spec.yaml` §4, and frontend `menuTree.js` / `ScreenSelectionTree`. When the requirement defines or changes screen IDs or permission targets, §2 and the change file list must cover backend constants, contract/spec, and Cursor skills so implementers apply the same screen set.

---

## 2. Design

### 2.1 Security review (optional; when PII / decryption / access control)

- **Log-type vs screen**: Every search and decrypt API must validate the requested `logType` against the caller’s allowed screens (e.g. `pb_feplog` → `pb-feplog`, `java_fw_imglog` → `java-fw-imagelog`). Reject with 403 (e.g. `FUNCTION_NOT_ALLOWED` or `LOG_TYPE_NOT_ALLOWED`) when the log type is not allowed.  
- **Decrypt scope**: Decrypt permission remains per screen (per log type). Decrypt-allowed store and `GET /api/decrypt/allowed` must use the same screen IDs (`pb-feplog`, `java-fw-imagelog`) so a user with only one screen cannot decrypt the other log type.  
- **Audit**: Activity/audit logs should record which screen (or log type) was used for search/decrypt so access can be reviewed per log type.  
- **Backward compatibility**: Document whether existing `main` is migrated to both new screens or removed; if `main` is dropped, document re-configuration or a DB migration for `permission_group_screen`.

- [ ] Security review performed (check if applicable)

### Technical design

#### Problem analysis

1. "검색 이력" currently lives under "로그 검색"; it should be under "이력·승인" after "활동 이력".  
2. A single "검색하기" (main) screen does not allow per–log-type permission; the product requires separate permission for "PB FEP Log" and "Java FW Image Log".  
3. "승인 대기" label should be "복호화 승인 관리" without changing screen_id or API contract.

#### Solution approach

Structure by scope for handoff.

**Frontend**

- **Menu (menuTree.js)**:  
  - "로그 검색": remove child `search-main` (검색하기). Add two children: e.g. `pb-feplog` (label "PB FEP Log", view `pb-feplog`) and `java-fw-imagelog` (label "Java FW Image Log", view `java-fw-imagelog`).  
  - "이력·승인": add child for "검색 이력" after "활동 이력" (id `search-history`, view `search-history`). Remove "검색 이력" from under "로그 검색".  
  - "이력·승인": rename "승인 대기" label to "복호화 승인 관리"; id remains `pending-approvals`, view `pending-approvals`.  
- **ALLOWED_SCREEN_IDS**: add `pb-feplog`, `java-fw-imagelog`; remove `main` (or keep for backward compat per migration choice).  
- **SECOND_ICONS**: add entries for the two new view IDs.  
- **App.js**: `currentView` must allow `pb-feplog` and `java-fw-imagelog`; `getFirstAllowedScreen` must use the new screen set (e.g. first of allowed list that is in the new menu order). Remove or replace `handleSearchMain` / log-type selector as entry to search: navigation to log search is by menu to `pb-feplog` or `java-fw-imagelog`. Render LogGrid for `currentView === 'pb-feplog'` or `currentView === 'java-fw-imagelog'` with the corresponding logType.  
- **AppSidebar**: remove or replace `onSearchMain`; menu items for the two log types navigate to their view IDs.  
- **Permission config**: `ScreenSelectionTree` uses `MENU_TREE`, so the new two items appear under "로그 검색"; `PermissionGroupPanel` and `screenFunctionDescriptions.js` must treat `pb-feplog` and `java-fw-imagelog` as screens with read + optional decrypt (no write/approve).  
- **Decrypt / allowed**: `GET /api/decrypt/allowed?screen=...` and decrypt API calls must use `screen=pb-feplog` or `screen=java-fw-imagelog` as appropriate for the current log type; hide or disable decrypt when the user lacks that screen’s decrypt.

**Backend**

- **ScreenConstants**: Add `PB_FEPLOG = "pb-feplog"`, `JAVA_FW_IMAGELOG = "java-fw-imagelog"`. Remove `MAIN` from `ALL_ALLOWED_SCREENS` or retain only for migration/deprecation. Add both new IDs to `SCREENS_WITH_DECRYPT`.  
- **ScreenAccessInterceptor**: Path rules for `/api/logs/db-refactored/*`, `/api/logs/decrypt/*`, `/api/search/*` must require the screen that corresponds to the request’s log type (e.g. logType=pb_feplog → `pb-feplog`, logType=java_fw_imglog → `java-fw-imagelog`). Reject with 403 when the user does not have that screen.  
- **AuthService**: `getAllAllowedScreens()` and screen function derivation must include `pb-feplog` and `java-fw-imagelog` (read + optional decrypt); remove or map `main` per migration strategy.  
- **DecryptAllowedController / DecryptController**: Accept `screen=pb-feplog` or `screen=java-fw-imagelog`; validate that the user has that screen and decrypt for it.  
- **PermissionGroupService**: Validate allowedScreens: `pb-feplog` and `java-fw-imagelog` allow read + optional decrypt; no write/approve. If migrating `main` → both, add migration or one-time script to duplicate main to both new screens in `permission_group_screen`.  
- **SearchHistoryService**: Decryption-allowed store must use the same screen IDs (e.g. `pb-feplog`, `java-fw-imagelog`) when recording or checking allowed GUIDs.

**DB**

- **permission_group_screen**: New rows use `screen_id` in (`pb-feplog`, `java-fw-imagelog`). Optional migration: for each row with `screen_id = 'main'`, insert two rows with `pb-feplog` and `java-fw-imagelog` with same read/decrypt and then remove or leave `main` deprecated.  
- No schema change to columns; only allowed values for `screen_id` expand.

**Contract / Spec**

- **docs/contract.md**: Update "화면 ID 목록" to include `pb-feplog`, `java-fw-imagelog`; remove or deprecate `main`. Update screen-based access and decrypt to reference the two new screens. Approval-only: "main 없이" → "로그 검색 화면(pb-feplog, java-fw-imagelog) 없이".  
- **docs/api-definition.md**: Update auth response and decrypt/allowed API descriptions to the new screen set; path→screen mapping for log search and decrypt by log type.  
- **specs/permission-group-hierarchy.spec.yaml**: §4.1 add `pb-feplog`, `java-fw-imagelog`; §1.1.1 add validation rows for both (read + optional decrypt); §4.3 path→screen table; §4.4 screenFunctions; §5 approval-only condition.

### Affected scopes and change targets (verification)

Per `docs/workflow/REQUIREMENTS-CHANGE-TARGET-CHECKLIST.md`:

| Scope | Affected? | §2 subsection and change file list complete? |
|-------|-----------|-----------------------------------------------|
| Backend | Yes | Yes |
| Frontend (config UI + view screen) | Yes | Yes |
| DB | Yes (migration optional) | Yes |
| Contract / Spec | Yes | Yes |
| Cursor tools (skills, specs) | Yes | Yes |

Pattern **3.2 Permission or screen-access change** applies: backend access checks and auth response, frontend menu/sidebar and permission configuration UI, contract/spec permission mapping, auth/permission-related skills.

### Cursor tool update targets

- `.cursor/skills/auth-permission-domain/SKILL.md`: Update screen access and permission model (main → pb-feplog / java-fw-imagelog).  
- `.cursor/skills/ui-ux-domain/SKILL.md`: Update menu tree and screen IDs (MENU_TREE, allowedScreenIds).  
- `.cursor/skills/api-permission-map/SKILL.md`: Update API → screen mapping for log search and decrypt (per log type).

### Planned change file list (expected change targets)

**(Planned at authoring. Implementing agent (Step 4) confirms or amends this list when implementation is complete.)**

#### Frontend

- `frontend/src/constants/menuTree.js`  
  - Update MENU_TREE: "로그 검색" children to PB FEP Log and Java FW Image Log only; move "검색 이력" under "이력·승인" after "활동 이력"; rename "승인 대기" to "복호화 승인 관리". Update ALLOWED_SCREEN_IDS and SECOND_ICONS.
- `frontend/src/App.js`  
  - Extend currentView for pb-feplog and java-fw-imagelog; update getFirstAllowedScreen for new screen set; replace or remove handleSearchMain; render LogGrid by view ID and corresponding logType.
- `frontend/src/components/AppSidebar.js`  
  - Remove or replace onSearchMain; navigate to pb-feplog / java-fw-imagelog by menu click.
- `frontend/src/components/PermissionGroupManagement/ScreenSelectionTree.js`  
  - Ensure new screen IDs from MENU_TREE get read + optional decrypt in normalizeSelected and toggle/changeDecrypt (already driven by MENU_TREE and screenFunctionDescriptions).
- `frontend/src/components/PermissionGroupManagement/PermissionGroupPanel.js`  
  - Normalize allowedScreens to include pb-feplog and java-fw-imagelog; decryptScreens list must include both.
- `frontend/src/constants/screenFunctionDescriptions.js`  
  - Add pb-feplog and java-fw-imagelog to SCREENS_WITH_DECRYPT; remove main or leave deprecated.
- `frontend/src/components/LogGrid.js`  
  - Pass or derive screen for decrypt/allowed API (pb-feplog vs java-fw-imagelog) from logType.
- `frontend/src/utils/security.js`  
  - Derive screenFunctions for pb-feplog and java-fw-imagelog when deriving from allowedScreenIds (if still used).

#### Backend

- `backend/src/main/java/com/logmng/constants/ScreenConstants.java`  
  - Add pb-feplog, java-fw-imagelog; remove or deprecate MAIN in ALL_ALLOWED_SCREENS; add both to SCREENS_WITH_DECRYPT.
- `backend/src/main/java/com/logmng/config/ScreenAccessInterceptor.java`  
  - Map log search and decrypt paths to screen by logType (pb_feplog → pb-feplog, java_fw_imglog → java-fw-imagelog); require that screen for the request.
- `backend/src/main/java/com/logmng/service/AuthService.java`  
  - Include new screen IDs in getAllowedScreens and screen function derivation; handle main migration or removal.
- `backend/src/main/java/com/logmng/controller/DecryptAllowedController.java`  
  - Accept screen=pb-feplog and screen=java-fw-imagelog; validate and return allowed for that screen.
- `backend/src/main/java/com/logmng/controller/DecryptController.java`  
  - Validate decrypt permission for the screen corresponding to the log type being decrypted.
- `backend/src/main/java/com/logmng/service/PermissionGroupService.java`  
  - Validate read/decrypt for pb-feplog and java-fw-imagelog; support migration of main to both if chosen.
- `backend/src/main/java/com/logmng/service/SearchHistoryService.java`  
  - Use pb-feplog / java-fw-imagelog when calling decryption-allowed store (by log type or screen).
- Backend unit tests for ScreenConstants, AuthService, PermissionGroupService, DecryptController, DecryptAllowedController, ScreenAccessInterceptor (path → screen by logType).

#### DB

- Optional: `backend/src/main/resources/db/migrate-main-to-pb-feplog-java-fw-imagelog.sql` (or equivalent)  
  - For each permission_group_screen row with screen_id = 'main', insert rows for pb-feplog and java-fw-imagelog with same read/decrypt; optionally delete main rows. Document in requirement or runbook.

#### Contract / Spec

- `docs/contract.md`  
  - Update screen ID list, screen-based access, decrypt and approval-only wording to pb-feplog / java-fw-imagelog.
- `docs/api-definition.md`  
  - Update auth response and decrypt/allowed API to new screen set; path→screen by log type.
- `specs/permission-group-hierarchy.spec.yaml`  
  - §4.1, §1.1.1, §4.3, §4.4, §5 as described in §2 Contract/Spec.

#### Cursor skills

- `.cursor/skills/auth-permission-domain/SKILL.md`  
  - Screen access and permission model (main → two log-type screens).
- `.cursor/skills/ui-ux-domain/SKILL.md`  
  - Menu tree and screen IDs.
- `.cursor/skills/api-permission-map/SKILL.md`  
  - API → screen mapping for log search and decrypt.

---

## 3. Test approach

### Test case list (required)

| ID | Scope | Type | Scenario (input / condition) | Expected result | Verification (unit / integration / manual) |
|----|-------|------|------------------------------|-----------------|-------------------------------------------|
| TC-01 | Frontend | Normal | Open app as user with only pb-feplog allowed; open sidebar. | "로그 검색" has only "PB FEP Log"; no "검색하기" or "Java FW Image Log". Click opens PB FEP Log search. | Manual / browser |
| TC-02 | Frontend | Normal | Open app as user with both pb-feplog and java-fw-imagelog. | "로그 검색" shows "PB FEP Log" and "Java FW Image Log". Each click opens correct log type. | Manual / browser |
| TC-03 | Frontend | Normal | Sidebar "이력·승인" order. | First "활동 이력", second "검색 이력", third "복호화 승인 관리". | Manual / browser |
| TC-04 | Frontend | Normal | Permission group edit: "로그 검색" section. | Two checkboxes "PB FEP Log", "Java FW Image Log"; each has optional decrypt; no "검색하기". | Manual / browser |
| TC-05 | Backend | Normal | POST permission-groups with allowedScreens including pb-feplog, java-fw-imagelog (read + decrypt). | 201; stored with both screens and decrypt true. | Unit (PermissionGroupServiceTest) |
| TC-06 | Backend | Normal | GET /api/logs/db-refactored/search with logType=java_fw_imglog; user has java-fw-imagelog only. | 200 and search allowed. | Integration / unit |
| TC-07 | Backend | Exception | GET /api/logs/db-refactored/search with logType=java_fw_imglog; user has only pb-feplog. | 403 FUNCTION_NOT_ALLOWED or LOG_TYPE_NOT_ALLOWED. | Unit / integration |
| TC-08 | Backend | Normal | GET /api/decrypt/allowed?screen=java-fw-imagelog with user that has java-fw-imagelog decrypt. | 200; response includes screen and guids. | Unit (DecryptAllowedController) |
| TC-09 | Backend | Exception | GET /api/decrypt/allowed?screen=java-fw-imagelog with user that has only pb-feplog. | 403. | Unit |
| TC-10 | Integration | Normal | User with only java-fw-imagelog decrypt requests decryption for that log type. | Decrypt allowed and decrypt API succeeds for that type only. | Integration |
| TC-11 | Backend | Normal | Auth login response allowedScreenIds. | Contains pb-feplog and/or java-fw-imagelog as configured; no main (or main deprecated per migration). | Unit (AuthServiceTest) |

### Test scenarios

#### Scenario 1: Menu and navigation

1. Log in as user with only PB FEP Log.  
2. Open sidebar; confirm "로그 검색" shows only "PB FEP Log".  
3. Click "PB FEP Log"; confirm log search view for PB FEP Log.  
4. Under "이력·승인", confirm order: 활동 이력 → 검색 이력 → 복호화 승인 관리.

#### Scenario 2: Per–log-type permission

1. Create permission group with only java-fw-imagelog (read + decrypt).  
2. Assign user; log in.  
3. Call GET /api/logs/db-refactored/search with logType=java_fw_imglog → 200.  
4. Call GET /api/logs/db-refactored/search with logType=pb_feplog → 403.

#### Scenario 3: Permission config UI

1. Log in as admin; open permission group create/edit.  
2. Under "로그 검색", confirm "PB FEP Log" and "Java FW Image Log" with decrypt toggle each; no "검색하기".

### Test data

- Permission groups with main only (for migration test if migration is implemented).  
- Permission groups with pb-feplog and/or java-fw-imagelog.  
- Users assigned to groups that have only one of the two log-type screens.

### Test environment

- Frontend: http://localhost:3001 (or per contract).  
- Backend: http://localhost:9200.  
- Database: PostgreSQL per docs/contract.md.

### 3.5 Browser automation verification (optional)

- **Applicable TCs**: TC-01, TC-02, TC-03, TC-04.  
- **Procedure**: Login → open sidebar → snapshot; assert menu labels and order; open permission group edit → assert "로그 검색" section.  
- **Reference**: `docs/workflow/BROWSER-AUTOMATION-VERIFICATION-POLICY.md`.

---

## 4. Checklist

### Frontend verification

- [ ] Menu structure and labels match §1 (이력·승인 order, 복호화 승인 관리, PB FEP Log / Java FW Image Log only under 로그 검색).  
- [ ] Permission config shows two log-type screens with read + optional decrypt.  
- [ ] Navigation and first-allowed-screen use new screen IDs; decrypt/allowed uses correct screen param.

### Backend verification

- [ ] ScreenConstants and path→screen by logType implemented; 403 when log type not allowed.  
- [ ] Auth response includes pb-feplog / java-fw-imagelog; decrypt and allowed APIs use same screen set.  
- [ ] Unit/integration tests for permission validation and log-type enforcement.

### Integration

- [ ] End-to-end: login → menu → search per log type; decrypt only for allowed log type.  
- [ ] Contract and spec updated; Cursor skills updated.

### Documentation

- [ ] Requirement doc completed.  
- [ ] Contract, api-definition, permission-group-hierarchy.spec.yaml and skills updated.

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
# Backend
cd backend && mvn test

# Frontend
cd frontend && npm test -- --watchAll=false
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

1. [Next step 1]  
2. [Next step 2]

---

## 6. Error remedy result (cause and action) — for error/bug fix requirements only

*(Omit — this requirement is a feature/restructure, not an error fix.)*

---

## 7. Final version (Korean) — add after all verification is complete

*(Add after QA verification and before or with final commit. See `docs/workflow/DOCUMENT-LANGUAGE-POLICY.md` §2.3.)*

### Final Korean summary

- **Requirement description**: (요약)  
- **Expected outcome**: (요약)  
- **Verification result**: (§5 요약, pass/fail)

---

**Author**: Requirements subagent  
**Date**: 2026-03-18  
**Status**: In progress
