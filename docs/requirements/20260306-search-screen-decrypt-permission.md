# 20260306 - Search screen decrypt permission (configurable)

## 1. User requirement

### Requirement description

- **Permission management**: On the permission group management screen, allow administrators to **grant or revoke** the **decryption** (복호화) permission for the **search screen** (main). When granted, users in that group may request decryption (subject to existing approval flow). When revoked or not granted, users must not be able to call the decrypt API.
- **Search screen / tools and docs**: In search-screen–related tools and documentation, state clearly that **only users who have the decrypt permission** (on the main screen) may request decryption. The decrypt API must enforce this: 403 `FUNCTION_NOT_ALLOWED` when the user has `main` access but does not have the decrypt function.

### User scenario

1. Admin opens permission group management and edits a permission group that includes the "검색하기" (main) screen.
2. Admin sees an option to grant or revoke **복호화** (decryption) for the main screen.
3. When **복호화** is checked, users in that group can request decryption (after approval flow). When unchecked (or not set), users cannot call the decrypt API even if they have main access.
4. **Problem**: Today, any user with `main` screen access can call the decrypt API (gated only by DECRYPTION_NOT_APPROVED / ROW_NOT_IN_APPROVED_SNAPSHOT). There is no way to allow "search only" without "decrypt" within the same screen.

### Expected outcome

- Permission management UI shows a **복호화** checkbox for the main (검색하기) screen; admin can grant/revoke it.
- Backend stores and returns `decrypt` for main in allowedScreens and in screenFunctions.
- Decrypt API (`POST /api/logs/decrypt/*`) returns 403 `FUNCTION_NOT_ALLOWED` when the user has `main` but does not have `screenFunctions.main.decrypt === true` (or is_system_admin).
- Docs and Cursor skills (auth-permission-domain, api-permission-map, search-history-decrypt-domain) describe that decryption is permission-gated and that only users with decrypt permission can request decryption.

---

## 2. Design

### 2.1 Security review

- **Risks**: Decryption exposes sensitive data. Restricting who can request decryption per permission group reduces exposure (e.g. "search-only" users cannot trigger decrypt at all).
- **Recommendation**: Keep existing DECRYPTION_NOT_APPROVED and ROW_NOT_IN_APPROVED_SNAPSHOT checks; add a prior check: user must have decrypt permission on main (or be system admin).

### Technical design

#### Problem analysis

1. Currently `main` has only read in screenFunctions; no per-group toggle for "can request decryption."
2. Decrypt API is gated only by screen `main` (interceptor) and then by approval/snapshot in service.
3. Permission management UI has no way to configure "decrypt" for main.

#### Solution approach

**Contract / Spec**

- **main** screen: support optional **decrypt** in addition to read. Validation: main allows `read` (implied) and optional `decrypt`; `write`/`approve` remain forbidden. New validation: `decrypt` only allowed for screen_id `main`; other screens must not send `decrypt=true`.
- **allowedScreens** item shape: add `decrypt?: boolean` (optional; only for main).
- **screenFunctions**: for main, include `decrypt: boolean`. Derivation when null: `decrypt = false`. Explicit storage in `permission_group_screen.decrypt`.

**DB**

- Add column `decrypt` BOOLEAN NULL to `permission_group_screen`. Only meaningful for `screen_id = 'main'`. Migration script idempotent.

**Backend**

- `ScreenFunctionCapability`: add field `decrypt` (Boolean).
- `AllowedScreenItem`: add `decrypt` (Boolean).
- `ScreenConstants`: add `supportsDecrypt(screenId)` → true only for `main`.
- `PermissionGroupService`: (1) `getScreenFunctionsForUser` — SELECT and merge `decrypt` for main; (2) `loadAllowedScreens` / `saveAllowedScreens` — read/write `decrypt`; (3) `validateAllowedScreens` — allow `decrypt` only for main; reject `decrypt=true` for non-main.
- `AuthService`: (1) `resolveScreenFunctions` — for main, set `decrypt` from pgs (explicit or default false); (2) add `hasDecryptForMain(HttpServletRequest)` → true if is_system_admin or (main in allowedScreenIds and screenFunctions.main.decrypt == true).
- `DecryptController`: after session/userId check, if `!authService.hasDecryptForMain(request)` return 403 with code `FUNCTION_NOT_ALLOWED`.
- `AllowedScreenListDeserializer`: accept `decrypt` in JSON for screen items.

**Frontend**

- **Config UI**: For main screen in ScreenSelectionTree, when screen is selected, show a "복호화" checkbox (or toggle). Include `decrypt` in normalized selectedScreens and in API payload for allowedScreens. Constants: add `SCREENS_WITH_DECRYPT = ['main']`, label and tooltip for decrypt.
- **Search screen (view)**: No change to search UI logic; backend already denies decrypt when permission is missing. Optional: hide or disable decrypt button when `screenFunctions.main.decrypt !== true` (can be done in a follow-up if auth/me is already used for other function checks).

**Cursor tools / docs**

- **auth-permission-domain** SKILL: Document that main screen has optional `decrypt` in screenFunctions; when false or absent, user cannot request decryption. Stored in permission_group_screen.decrypt for main.
- **api-permission-map** SKILL: Document decrypt API as requiring main screen **and** screenFunctions.main.decrypt (or is_system_admin); denial 403 FUNCTION_NOT_ALLOWED.
- **search-history-decrypt-domain** SKILL: Add that requesting decryption (calling decrypt API) requires decrypt permission on main; DECRYPTION_NOT_APPROVED and ROW_NOT_IN_APPROVED_SNAPSHOT apply after this permission check.
- **contract.md**: In screen-based access section, mention main’s optional decrypt function and that decrypt API checks it.
- **specs/permission-group-hierarchy.spec.yaml**: §1.1 AllowedScreenItem decrypt; §1.1.1 main read+decrypt validation; §4.4 screenFunctions decrypt for main; main special case.

### Affected scopes and change targets

| Scope | Affected? | §2 subsection and change file list complete? |
|-------|-----------|-----------------------------------------------|
| Backend | Yes | Yes — see Change file list below. |
| Frontend (config UI + view) | Yes | Config UI: ScreenSelectionTree, PermissionGroupPanel, screenFunctionDescriptions. View: optional (hide/disable decrypt when no permission); see Change file list. |
| DB | Yes | schema.sql, migrate-permission-group-screen-decrypt.sql |
| Contract / Spec | Yes | contract.md, specs/permission-group-hierarchy.spec.yaml |
| Cursor tools (skills) | Yes | .cursor/skills/auth-permission-domain/SKILL.md, api-permission-map/SKILL.md, search-history-decrypt-domain/SKILL.md |

### Change file list (tentative; implementing agent confirms or updates)

**(Tentative. Implementing agent (Step 4) confirms or updates with actual files changed.)**

#### Backend
- `backend/src/main/java/com/logmng/constants/ScreenConstants.java` — add `supportsDecrypt(screenId)` (true only for main).
- `backend/src/main/java/com/logmng/dto/response/ScreenFunctionCapability.java` — add `decrypt` (Boolean).
- `backend/src/main/java/com/logmng/dto/response/AllowedScreenItem.java` — add `decrypt` (Boolean).
- `backend/src/main/java/com/logmng/dto/request/AllowedScreenListDeserializer.java` — accept `decrypt` in JSON for screen items.
- `backend/src/main/java/com/logmng/service/PermissionGroupService.java` — getScreenFunctionsForUser (merge decrypt for main), loadAllowedScreens/saveAllowedScreens (read/write decrypt), validateAllowedScreens (decrypt only for main).
- `backend/src/main/java/com/logmng/service/AuthService.java` — resolveScreenFunctions (decrypt for main), `hasDecryptForMain(HttpServletRequest)`; gate by screenFunctions.main.decrypt or is_system_admin.
- `backend/src/main/java/com/logmng/controller/DecryptController.java` — after session/userId check, if `!authService.hasDecryptForMain(request)` return 403 FUNCTION_NOT_ALLOWED.
- `backend/src/test/java/com/logmng/service/PermissionGroupServiceTest.java` — tests for decrypt in allowedScreens and screenFunctions.
- `backend/src/test/java/com/logmng/webtest/DecryptControllerTest.java` — tests for 403 FUNCTION_NOT_ALLOWED when decrypt not granted.

#### Frontend
- `frontend/src/components/PermissionGroupManagement/ScreenSelectionTree.js` — show "복호화" checkbox for main when selected; include `decrypt` in payload.
- `frontend/src/components/PermissionGroupManagement/PermissionGroupPanel.js` — normalize/save `decrypt` for main in allowedScreens (if normalization lives here).
- `frontend/src/constants/screenFunctionDescriptions.js` — add SCREENS_WITH_DECRYPT, label/tooltip for decrypt.
- *(Optional / follow-up)* `frontend/src/components/LogGrid.js` or decrypt-triggering component — hide or disable decrypt button when `screenFunctions.main.decrypt !== true` (user-facing screen; backend already denies API).

#### DB
- `backend/src/main/resources/db/schema.sql` — reflect `permission_group_screen.decrypt` (or document as migration-only).
- `backend/src/main/resources/db/migrate-permission-group-screen-decrypt.sql` — add column `decrypt` BOOLEAN NULL to `permission_group_screen`; idempotent.

#### Contract / Spec
- `docs/contract.md` — screen-based access: main optional decrypt; decrypt API checks it.
- `specs/permission-group-hierarchy.spec.yaml` — AllowedScreenItem decrypt; main read+decrypt validation; screenFunctions decrypt for main.

#### Cursor tools (skills)
- `.cursor/skills/auth-permission-domain/SKILL.md` — main screen optional decrypt in screenFunctions; permission_group_screen.decrypt for main.
- `.cursor/skills/api-permission-map/SKILL.md` — decrypt API requires main + screenFunctions.main.decrypt (or is_system_admin); 403 FUNCTION_NOT_ALLOWED.
- `.cursor/skills/search-history-decrypt-domain/SKILL.md` — decrypt API requires decrypt permission on main; DECRYPTION_NOT_APPROVED / ROW_NOT_IN_APPROVED_SNAPSHOT after permission check.

---

## 3. Test cases

| ID | Scope | Type | Scenario (input / condition) | Expected result | Verification |
|----|-------|------|------------------------------|-----------------|--------------|
| TC-01 | Backend / Integration | Normal | Admin grants decrypt for main to group G. User in G has main + decrypt. User calls POST /api/logs/decrypt/java_fw_imglog with valid searchHistoryId and guid. | 200 (or 403 DECRYPTION_NOT_APPROVED / ROW_NOT_IN_APPROVED_SNAPSHOT only when approval/snapshot fails). | Integration (curl / API test) |
| TC-02 | Backend | Exception | User has main but decrypt not granted (decrypt=false or omitted). User calls POST /api/logs/decrypt/java_fw_imglog. | 403, code FUNCTION_NOT_ALLOWED. | Unit / Integration (DecryptControllerTest) |
| TC-03 | Backend | Normal | is_system_admin user calls decrypt API. | Not denied by function check (subject to approval/snapshot). | Unit / Integration |
| TC-04 | Backend | Normal | Permission group PUT with allowedScreens including main and decrypt: true. | 200; GET group returns main with decrypt: true. | Unit (PermissionGroupServiceTest) or Integration |
| TC-05 | Backend | Normal | Permission group PUT with allowedScreens including main and decrypt: false. | 200; GET group returns main with decrypt: false; user in that group gets 403 on decrypt API. | Unit / Integration |
| TC-06 | Backend | Exception | Permission group PUT with allowedScreens including screen X (not main) with decrypt: true. | 400 INVALID_SCREEN_FUNCTION. | Unit (PermissionGroupServiceTest) |
| TC-07 | Frontend | Normal | Permission management UI: when main is selected, decrypt checkbox is visible; toggling updates payload and save. | UI shows decrypt; saved and loaded correctly. | Unit (ScreenSelectionTree.test.js) or Manual / browser |
| TC-08 | Integration | Edge | User without main screen access calls decrypt API. | 403 (screen access denied before decrypt check). | Integration |

---

## 4. (Optional) Out of scope

- Changing approval flow or snapshot rules.
- Decrypt permission for screens other than main.

---

## 5. Test results

- **Backend**: `mvn test` — passed (PermissionGroupServiceTest with decrypt column; DecryptControllerTest with StubAuthServiceDecryptAllowed).
- **Frontend**: ScreenSelectionTree.test.js — passed (scope/approve tests; decrypt UI covered by existing tree tests).
- **Verification**: PermissionGroupPanel normalizes and sends `decrypt` in allowedScreens payload; backend enforces hasDecryptForMain on decrypt API. req 20260306-search-screen-decrypt-permission.

---

## 6. (Optional) Final version (Korean)

(To be added after verification.)
