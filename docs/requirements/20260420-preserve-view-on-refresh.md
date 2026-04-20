# 20260420 - Preserve main menu view on browser refresh

## 1. User requirement

### Requirement description

The SPA uses React local state for the active main menu screen (`currentView` in `App.js`), not React Router for most views. After a full page reload (browser refresh), the user must land on the **same main menu screen** they were viewing immediately before the refresh, whenever that screen is still **allowed** for their session. Today, behavior tends to reset toward defaults (for example `pb-feplog` or the first allowed screen in menu order), which forces repeated navigation.

### User scenario

1. An authenticated user opens an allowed screen from the sidebar (for example Activity Log, Search History, or PB FEP log search).
2. The user refreshes the page (F5 / browser reload) or the tab is restored after a crash.
3. **Problem**: The app does not restore the previous screen; the user sees a default or first-allowed screen instead of the one they were using.

### Expected outcome

- After refresh, the user returns to the **same allowed** main menu screen they had selected, without requiring URL deep links for every screen.
- If the URL matches an existing **deep-link** rule (for example `/user-management/hr-sync-poc`, `/user-management/poc-v2`), that rule **still wins** over any stored screen, unchanged in intent.
- If a stored screen id is **not** allowed for the current user (permission change, policy mismatch), the app **falls back** to current product behavior (for example first allowed screen per `ORDERED_SCREEN_IDS` / `getFirstAllowedScreen`), and must **not** show a screen the user cannot access.
- Logout, no-permission logout, and `clearUserData` must **clear** stored view state so another user on the same machine does not inherit the previous user’s tab intent (session-scoped storage must still be explicitly cleared on logout for consistency).
- Storage must be **tab-scoped** (for example `sessionStorage`), must **not** introduce new PII beyond what is already implied by “which screen was open,” and must use a **namespaced** key (for example `logmng_last_view` or a shared constant).

**Authoring note (orchestration):** §1 is aligned with the user’s Korean-context intent. §2 incorporates codebase investigation (`frontend/src/App.js`, `frontend/src/utils/security.js`) and domain baseline from `auth-permission-domain` / `api-permission-map` (screen access must follow `screenAccessPolicy.js` and the same rules as the sidebar, not ad-hoc checks).

## 2. Design

### 2.1 Security review (optional; when PII / decryption / access control)

This requirement touches **access control** (which screen may be shown) but does not widen API access. Stored value is a **screen id** only (no credentials).

- [x] Security considerations captured at requirement level (formal Security subagent review may follow workflow Step 2 if needed)
- **Risks:** Restoring a view the user must not see would be a **policy violation**; stale storage after permission downgrade could briefly confuse UX if not validated before restore.
- **Acceptance / recommendations:** Restore only after validating with the **same** helpers used elsewhere (`policyCanAccessView` / `canNonAdminAccessCurrentView` and `getAllowedScreenIds` / system admin rules). **Clear** storage on logout and `clearUserData`. Prefer **sessionStorage** (tab-scoped). Do not store decrypt payloads or user identifiers in this key.

### Technical design

#### Codebase summary

- **`App.js`**: `currentView` is `useState('pb-feplog')`. Navigation uses `handleNavigate`, `handleReSearchFromHistory`, and several `useEffect` hooks: permission sync (`canNonAdminAccessCurrentView`), deep links for `hr-sync-poc` and `user-management-v2-poc`, logout / no-permission handlers, and an inline navigation to `activity-log-access-audit` from the activity log view. `checkAuthStatus` calls `/auth/check` and merges user into state; there is no persistence of `currentView` today.
- **`utils/security.js`**: `clearUserData` removes secure storage keys for user/tokens/log type; it does not currently clear any session-only view key.
- **Deep links**: Pathname checks in `useEffect` depend on `window.location.pathname`; these must remain **higher priority** than stored view restore.

#### Problem analysis

1. **Ephemeral UI state**: `currentView` resets on full reload because it lives only in React state.
2. **Default bias**: Initial state and permission effects push users toward `pb-feplog` or first allowed screen, not last intentional selection.
3. **Policy alignment**: Any restore path must reuse centralized policy (`frontend/src/constants/screenAccessPolicy.js`) — consistent with `docs/requirements/20260410-screen-access-menu-api-consistency.md` and `auth-permission-domain` skill.

#### Diagnostic phase (mandatory for error/bug fix only)

*Not applicable — feature / UX requirement, not an error-fix requirement.*

#### Solution approach

Structure by scope so each implementing agent receives only its relevant section during handoff.

**Frontend:**

- Introduce **tab-scoped** persistence (recommend `sessionStorage`) for the last **allowed** screen id, under a **namespaced** key (constant in `frontend/src/utils` or equivalent).
- **Restore priority** after `auth/check` succeeds and `user` is available (and session has effective app access):
  1. **Deep link pathname rules** — existing behavior for `/user-management/hr-sync-poc` and `/user-management/poc-v2` **unchanged** and must run **before** or **take precedence over** stored view restore.
  2. If no deep link applies (or deep link did not set a view): if stored id exists and **`policyCanAccessView` / `canNonAdminAccessCurrentView`** (same rules as sidebar) allow it, set `currentView` to that id.
  3. Otherwise keep **current** product behavior (initial default + permission effects / `getFirstAllowedScreen`).
- **Persist** the screen id whenever the user intentionally changes the main view, including at minimum:
  - `handleNavigate`
  - `handleReSearchFromHistory`
  - Any other **direct** `setCurrentView` used for user-driven navigation (audit all call sites in `App.js`; includes navigation from activity log to `activity-log-access-audit`).
- When permission effects **correct** `currentView` to `getFirstAllowedScreen(user)`, storage must **not** keep a disallowed id as the “last view”; update or clear storage so the next refresh does not re-apply an invalid id.
- **Clear** stored view on **`clearUserData`**, **`handleLogout`**, and **`handleNoPermissionConfirm`** (or equivalent paths) so logout resets tab intent.
- Optional small helper module (for example `frontend/src/utils/lastViewStorage.js`) for get/set/clear + key constant — keeps `App.js` readable and testable.

**Backend:**

- None required for this requirement (client-only persistence; `/auth/check` contract unchanged).

**DB:**

- None.

### Affected scopes and change targets (verification)

Per `docs/workflow/REQUIREMENTS-CHANGE-TARGET-CHECKLIST.md`: pattern §2.4 (search/filter UI consistency) **does not apply**.

| Scope | Affected? | §2 subsection and change file list complete? |
|-------|------------|-----------------------------------------------|
| Backend | No | N/A |
| Frontend (config UI + view screen) | Yes | Yes — view restore and navigation only (no permission-group config UI). |
| DB | No | N/A |
| Contract / Spec | No | N/A |
| Cursor tools (skills, specs) | No | N/A |

### Planned change file list (expected change targets)

**(Planned at authoring. Implementing agent (Step 4) confirms or amends this list when implementation is complete.)**

#### Frontend

- `frontend/src/App.js`
  - Implemented: `didRestoreLastViewRef` + `lastViewRestoreDone` gate; restore `useEffect` **after** deep-link `useEffect`s; `isViewAllowedForLastView` aligned with sidebar policy; persist `useEffect` after restore (updates/clears storage on permission-corrected views). Intentional navigation covered via `currentView` sync (includes `handleNavigate`, `handleReSearchFromHistory`, inline `setCurrentView`, deep links).
- `frontend/src/utils/security.js`
  - `clearUserData` calls `clearLastViewStorage()` from `lastViewStorage.js` (no circular import).
- `frontend/src/utils/lastViewStorage.js`
  - `LOGMNG_LAST_VIEW_SESSION_KEY`, `getLastViewId`, `setLastViewId`, `clearLastViewStorage`.
- `frontend/src/App.test.js`
  - TC-02 deep link vs storage, TC-03 disallowed stored, TC-04 `clearUserData`, navigation persistence (mock `sessionStorage` + `window.location`).

#### Backend

- None.

#### DB

- None.

## 3. Test approach

### Test case list (required)

| ID | Scope | Type | Scenario (input / condition) | Expected result | Verification (unit / integration / manual) |
|----|-------|------|------------------------------|-----------------|--------------------------------------------|
| TC-01 | Frontend | Normal | Authenticated user on an allowed screen; refresh (F5) | Same main menu screen is shown after reload | Manual / browser; Unit where state logic is extracted |
| TC-02 | Frontend | Normal | URL path is `/user-management/hr-sync-poc` (or `/user-management/poc-v2`) and user may access that deep link; stored view differs | Deep link target wins; stored view does not override | Unit (ordering / branch tests) + Manual |
| TC-03 | Frontend | Edge | Stored screen id is **not** allowed for current user (`allowedScreenIds` / non-admin rules) | App shows first allowed (or existing default behavior); no access to forbidden screen | Unit + Manual |
| TC-04 | Frontend | Normal | User logs out or no-permission flow clears session | Last-view storage key is removed (or empty); next login/refresh does not restore previous user’s screen | Unit (`clearUserData` / logout handlers) + Manual |
| TC-05 | Frontend | Normal | User navigates via sidebar (`handleNavigate`) to allowed screen B, then refreshes | Screen B is restored | Manual / browser; Unit optional |
| TC-06 | Frontend | Normal | User triggers re-search from history (`handleReSearchFromHistory`) to PB FEP or Image log view, then refreshes | Correct resolved view (`resolvePbFeplogViewForUser` / imagelog) is restored | Manual; Unit optional |
| TC-07 | Frontend | Regression | System admin or menu-order edge (`ORDERED_SCREEN_IDS` / `getFirstAllowedScreen`) | Restore and fallback still match sidebar policy | Manual or Unit per extracted helpers |

### Test scenarios

#### Scenario 1: Refresh preserves allowed screen

1. Log in as a user with access to at least two main screens.
2. Open a non-default allowed screen (not the first in menu order).
3. Refresh the page.
4. Verify the same screen is active and content matches.

#### Scenario 2: Deep link overrides storage

1. Navigate to an allowed screen and refresh once (to populate storage).
2. Manually set URL to a valid deep-link path (hr-sync-poc or poc-v2) and reload.
3. Verify the deep-link view is shown.

#### Scenario 3: Logout clears stored view

1. Navigate to a screen, refresh to confirm storage is used.
2. Log out; log in as a different user (or same user in a clean session).
3. Verify the app does not jump to the previous user’s last screen solely from stale storage (storage cleared on logout).

### Test data

- Users with distinct `allowedScreenIds` (including one user **without** access to the screen another user had stored — use separate sessions or manipulate storage in unit tests).
- No new SQL required.

### Test environment

- Frontend: `http://localhost:3001` (or per project contract)
- Backend: `http://localhost:9200` (for real login / `auth/check`)
- Database: per local dev (existing)

### 3.5 Browser automation verification (optional — for frontend-heavy requirements)

- **Applicable TCs:** TC-01, TC-02, TC-05, TC-06 (and TC-04 for logout).
- **Procedure:** After login, `browser_navigate` / sidebar navigation per TC; full refresh via browser reload; `browser_snapshot` to assert active menu/screen; for TC-02, set URL then reload and snapshot.
- **Reference:** `docs/workflow/BROWSER-AUTOMATION-VERIFICATION-POLICY.md`

## 4. Checklist

### Frontend verification

- [ ] Restore order respects deep links and policy
- [ ] No unauthorized screen shown after refresh
- [ ] Logout and `clearUserData` clear last-view storage

### Backend verification

- [ ] N/A

### Integration

- [ ] End-to-end refresh flow tested (manual or browser automation)

### Documentation

- [ ] Requirement doc completed
- [ ] Code comments added only where non-obvious (optional)

## 5. Test results

### Test run date

- 2026-04-20

### Test results

#### Frontend

- `cd frontend && npm test -- --watchAll=false` — exit 0 (333 tests; includes `App.test.js` last-view cases TC-02–TC-04 + navigation persist).
- `cd frontend && CI=false npm run build` — exit 0.

#### Backend

- N/A

**Commands:**

```bash
cd /Volumes/T7/dev/logmng_frontend/dev/frontend && npm test -- --watchAll=false
cd /Volumes/T7/dev/logmng_frontend/dev/frontend && CI=false npm run build
```

**Outcome:**

- Automated unit coverage for last-view restore ordering, disallowed storage clear, `clearUserData` clearing session key, and sidebar navigation persistence. Manual TC-01 / TC-05–TC-07 per §3 optional.

### Issues found and resolution

- (None yet)

### Next steps

1. Frontend (Step 4) implements per §2 and confirms change file list.
2. QA runs §3 TCs, updates §5, then commit per `.cursor/commands/commit-on-complete.md`.

## 6. Error remedy result (cause and action) — for error/bug fix requirements only

N/A

---

**Author**: Requirements (orchestrated)
**Date**: 2026-04-20
**Status**: In progress
