# 20260316 - Self-scope user block visible and search conditions

## 1. User requirement

### Requirement description

When a user with effective `scope=self` on **User Activity Log** (사용자활동이력) or **Activity Log Statistics** (활동로그통계) opens these screens, two bugs occur:

1. **User context block (부서, 사용자명, 사용자 ID)**  
   The block is correctly non-editable when scope=self, but the **values are not visible** — the current user's department, name, and user ID are hidden or blank instead of being shown as read-only.

2. **Other search/filter conditions**  
   The "기타조건" (other conditions) block, "로그타입" (log type) where applicable, and other defined search/filter conditions **do not appear** on the screen when scope=self, so the user cannot see or use them.

This requirement fixes both behaviors so that when scope=self: (a) the user context block shows the current user's information and is read-only; (b) all defined search/filter conditions (including 기타조건, 로그타입, and any other filters) remain visible on both screens. Numeric and structural values (e.g. field width, spacing, panel width) must be sourced from design docs; this doc references `docs/design/search-fields-by-screen.md`, `docs/design/search-field-definition-items.md`, and `docs/design/forms-and-filters.md` for traceability. When aligning search/filter UI across activity log and statistics, layout, group title placement, spacing, form panel width, and **user block field size** (department, user name, user ID) must be the same on both screens per `docs/design/search-fields-by-screen.md` §3, §4 and `docs/workflow/REQUIREMENTS-CHANGE-TARGET-CHECKLIST.md` §2.4.

### User scenario

1. User2 (or any user whose effective scope on User Activity Log and Activity Log Statistics is `self`) logs in and opens **User Activity Log** or **Activity Log Statistics**.
2. The screen shows the search/filter form with date (or period) and the user context block (부서, 사용자명, 사용자 ID).
3. **Problem (bug 1)**: The user context block is read-only as intended, but the **values** (current user's department, name, user ID) are not visible — fields appear empty or the block gives no indication of whose context is locked.
4. **Problem (bug 2)**: The "기타조건" section (e.g. 액션 타입, IP 주소 on activity log; IP 주소 on statistics) and, on statistics, the "로그 타입" section do not appear when scope=self, so the user cannot see or use those filters.
5. The user expects to see their own identity in the user block (read-only) and to see all search/filter conditions (기타조건, 로그타입, etc.) so that they can still narrow results by log type, action type, IP, etc., within their self-scoped data.

### Expected outcome

- When effective scope is `self` on **User Activity Log** and **Activity Log Statistics**:
  - **User context block (부서, 사용자명, 사용자 ID)** is **visible** and shows the **current user's** department, username, and user ID from the auth current-user self-context contract; the block is **read-only** (non-editable). The same user-block field order (department → username → userId) and the **same width/size** for department, user name, and user ID as on the aligned screens (activity log and statistics) must be used so the block is not squeezed or visually inconsistent.
  - **All defined search/filter conditions** are **visible**: "기타조건" (other conditions), "로그타입" (log type) on statistics, and any other filters defined for each screen (e.g. 액션 타입, IP 주소 on activity log; 로그 타입, IP 주소 on statistics) remain on screen and usable when scope=self.
- Backend enforcement of scope=self (current-user-only) is unchanged; visible fields are presentation-only and do not widen scope.
- Design docs and shared UI behavior consistently describe **visible, fixed to current user, not editable** for the user block and **visible** for other filter blocks when scope=self, and are updated so future changes do not revert to hiding the user block or other conditions.

---

## 2. Design

### 2.1 Security review (optional; when PII / decryption / access control)

- [ ] Security review performed (check if applicable)
- This requirement does not widen access; it only corrects visibility of already self-scoped data and filter UI. Backend remains authoritative for scope=self.

### Technical design

#### Problem analysis

1. **User block values not visible**: The shared `UserContextFilterBlock` when `mode='locked'` displays `lockedValues` (department, username, userId). If the parent does not pass `selfContext`/`lockedValues`, or if the auth response does not provide `selfContext`, the displayed values are blank. Implementation may already pass `selfContext` from `getSelfContext(user)`; the bug may be due to auth response not populating `user.selfContext` in some paths, or the block/container being hidden or styled so that values are not visible. The requirement must ensure the block is rendered with visible read-only values from the auth current-user source.

2. **Other conditions hidden when scope=self**: In `UserActivityLogSearchForm.js` and `StatisticsFilters.js`, the "기타 조건" (extra) block is wrapped in `{!isSelfScope && (...)}`, so when scope=self the entire block is not rendered. On statistics, "로그 타입" is outside that condition and is always rendered; on activity log there is no separate "로그 타입" block (action type and IP are in 기타조건). The design doc `docs/design/search-fields-by-screen.md` currently states for activity log (§2) and statistics (§3) that scope=self implies **hidden** for user block and extra block. That wording contradicts the intended standard (req 20260313) of **visible, fixed to current user** for the user block and should be updated; and the extra block (기타조건) must be shown when scope=self so that all filter conditions are visible.

3. **Design doc alignment**: `docs/design/search-fields-by-screen.md` and, where applicable, `docs/design/search-field-definition-items.md` (scopeWhenSelf) still encode "scope=self → hide user block / hide extra block". They must be updated to: user block = visible, fixed to current user, read-only; extra block (기타조건) and log-type block = visible, when scope=self.

#### Solution approach

**Frontend:**

- **User context block**: Ensure when scope=self the user context block is **always rendered** (not hidden) and receives non-empty `lockedValues`/`selfContext` from the auth current-user payload (e.g. `getSelfContext(user)`). Verify that the auth response provides `selfContext` (or `user.selfContext` / `user.self_context`) so the shared block can display department, username, userId. Keep the block read-only (mode=locked) and use the same block order and field width/size as on the aligned screen per `docs/design/search-fields-by-screen.md` and `docs/design/search-field-definition-items.md`.
- **Other search/filter conditions**: Remove the conditional that hides the "기타 조건" block when `isSelfScope` on both **User Activity Log** (`UserActivityLogSearchForm`) and **Activity Log Statistics** (`StatisticsFilters`). Ensure "로그 타입" on statistics remains visible (it is already outside the hide condition; verify no other logic hides it). Result: when scope=self, 기타조건 (액션 타입, IP 주소 on activity log; IP 주소 on statistics) and 로그타입 (statistics) are all visible.
- **Shared primitive**: The fix applies to the shared `UserContextFilterBlock` (ensure it displays locked values when provided) and to the two screen-specific forms that wrap it and the extra block. Do not reduce the fix to one screen; both User Activity Log and Activity Log Statistics must be updated.
- **Design doc implementation**: Read and apply field-level and layout values from `docs/design/search-field-definition-items.md` and `docs/design/search-fields-by-screen.md` when changing form/filter CSS or components. If any required standard for layout, field sizing, spacing, or control semantics is not defined or is ambiguous in those docs, do not infer or hardcode; inform the user of the undefined items, explain why each is needed, propose a recommended standard draft, and request feedback before implementation. See `docs/design/ux-frontend-standard-principles.md` §2.

**Backend:**

- No change to API behavior or scope enforcement is required. Optionally verify that auth endpoints (`/api/auth/check`, `/api/auth/me` or equivalent) consistently return `selfContext` (department, username, userId) so the frontend can display locked user block values when scope=self.

**Design docs:**

- Update `docs/design/search-fields-by-screen.md`: For activity log (§2) and statistics (§3), change scopeWhenSelf from "user block + extra block = hidden" to "user block = visible, fixed to current user, read-only; extra block (기타조건) = visible". Ensure 로그타입 (statistics) is described as visible for all scopes.
- Update `docs/design/search-field-definition-items.md` if scopeWhenSelf is used for these screens so that "visible" (and where applicable "visible, fixed, read-only") is the standard for user block and extra block when scope=self.

### Affected scopes and change targets (verification)

**Before finalizing §2**, the Requirements author has verified that every affected scope is covered and that no touchpoint is missed per `docs/workflow/REQUIREMENTS-CHANGE-TARGET-CHECKLIST.md`.

| Scope | Affected? | §2 subsection and change file list complete? |
|-------|-----------|-----------------------------------------------|
| Backend | [ ] Yes / [x] No | N/A — optional verification of auth selfContext only |
| Frontend (config UI + view screen) | [x] Yes / [ ] No | [x] |
| DB | [ ] Yes / [x] No | [ ] |
| Contract / Spec | [ ] Yes / [x] No | [ ] (optional: doc wording for self-context display) |
| Cursor tools (skills, specs) | [ ] Yes / [x] No | [ ] |

Pattern **§2.4 Search/filter UI consistency** applies (alignment of activity log and statistics search/filter UI). Verification:

- §1 Expected outcome includes an explicit bullet that user block fields (department, user name, user ID) have the **same width/size** on both aligned screens. **Done** (§1 above).
- §2 and change file list include applying the same user-block width and ensuring layout does not squeeze the user block. **Done** (solution and change list below).
- §3 includes at least one TC that compares user-block field size across the aligned screens. **Done** (§3).

**Implementation note for Frontend (pattern §2.4):**  
Implementer must read and apply field-level and layout values from `docs/design/search-field-definition-items.md` and `docs/design/search-fields-by-screen.md` when changing form/filter CSS or components; requirement §2 numeric values are consistent with those docs but must be verified or sourced from the docs. If any required standard for layout, field sizing, spacing, icon usage, label placement, or control semantics is not defined or is ambiguous in the design docs, the implementer must not infer or hardcode a solution. The implementer must first inform the user of the undefined standard items, explain why each is needed, propose a recommended standard draft, and request feedback so the standard can be explicitly defined before implementation proceeds. See `docs/design/ux-frontend-standard-principles.md` §2 and `docs/workflow/HANDOFF-CHECKLIST.md` Frontend § Design doc implementation.

### Planned change file list (expected change targets)

**(Planned at authoring. Implementing agent (Step 4) confirms or amends this list when implementation is complete.)**

#### Frontend (confirmed 2026-03-16)

- `frontend/src/components/common/UserContextFilterBlock.js`
  - No change: already displays lockedValues when mode=locked; no conditional or style hides values.
- `frontend/src/components/UserActivityLog/UserActivityLogSearchForm.js`
  - **Done**: Removed the condition that hid the "기타 조건" block when isSelfScope; 액션 타입 and IP 주소 are now visible when scope=self. UserContextFilterBlock already receives selfContext and is rendered when isSelfScope.
- `frontend/src/components/StatisticsFilters.js`
  - **Done**: Removed the condition that hid the "기타 조건" block when isSelfScope; IP 주소 and block title are now visible when scope=self. "로그 타입" was already visible for all scopes.
- `frontend/src/components/UserActivityLog/UserActivityLogList.js`
  - No change: already passes selfContext from getSelfContext(user) to UserActivityLogSearchForm.
- `frontend/src/components/ActivityStatistics.js`
  - No change: already passes selfContext (lockedSelfFilters) to StatisticsFilters.
- `frontend/src/components/UserActivityLog/UserActivityLogList.test.js`
  - **Done**: Updated expectations so that when scope=self, 기타 조건 (액션 타입, IP 주소) are expected to be visible (req 20260316).
- `docs/design/search-fields-by-screen.md`
  - **Done**: Updated activity log (§2) and statistics (§3): scopeWhenSelf for user block = "visible, fixed to current user, read-only"; for extra block = "visible".
- `docs/design/search-field-definition-items.md`
  - **Done**: Updated scopeWhenSelf description for activity-log/statistics user and extra blocks to "visible" (user block: "visible, fixed, read-only").

#### Backend

- None required. Optionally: verify auth response includes selfContext for display when scope=self (e.g. AuthController / AuthService / LoginResponse).

#### DB

- None.

---

## 3. Test approach

### Test case list (required)

| ID | Scope | Type | Scenario (input / condition) | Expected result | Verification (unit / integration / manual) |
|----|-------|------|------------------------------|-----------------|--------------------------------------------|
| TC-01 | Frontend | Normal | User with scope=self on User Activity Log opens the screen. | User context block (부서, 사용자명, 사용자 ID) is visible and shows the current user's department, username, and user ID; fields are read-only. | Manual / browser |
| TC-02 | Frontend | Normal | Same user on User Activity Log does not change department/username/userId. | User cannot edit the user context fields (no dropdown/input interaction). | Manual / browser |
| TC-03 | Frontend | Normal | User with scope=self on User Activity Log opens the screen. | "기타 조건" (액션 타입, IP 주소) is visible. | Manual / browser |
| TC-04 | Frontend | Normal | User with scope=self on Activity Log Statistics opens the screen. | User context block is visible with current user's department, username, user ID; read-only. | Manual / browser |
| TC-05 | Frontend | Normal | Same user on Activity Log Statistics. | "로그 타입" and "기타 조건" (IP 주소) are visible. | Manual / browser |
| TC-06 | Frontend | Normal | Compare User Activity Log and Activity Log Statistics with scope=self. | User block fields (department, user name, user ID) have the same width/size on both screens; block is not squeezed. | Manual / browser |
| TC-07 | Integration | Normal | Login as user2 (self-scope on both screens), open User Activity Log, then Activity Log Statistics. | On both screens: user block shows user2 info read-only; 기타조건 and 로그타입 (statistics) visible. | Integration / browser |

### Test scenarios

#### Scenario 1: Self-scope user sees own identity and all filters (User Activity Log)

1. Log in as a user whose effective scope on User Activity Log is `self`.
2. Open User Activity Log.
3. Verify the user context block shows 부서, 사용자명, 사용자 ID with the current user's values and is read-only.
4. Verify "기타 조건" (액션 타입, IP 주소) is visible and usable.

#### Scenario 2: Self-scope user sees own identity and all filters (Activity Log Statistics)

1. Log in as the same (or another) user with scope=self on Activity Log Statistics.
2. Open Activity Log Statistics.
3. Verify the user context block shows current user's 부서, 사용자명, 사용자 ID read-only.
4. Verify "로그 타입" and "기타 조건" (IP 주소) are visible and usable.

#### Scenario 3: User block field size alignment

1. With scope=self, open User Activity Log and note the size of the user block fields.
2. Open Activity Log Statistics and compare the user block field width/size.
3. Verify they match and the user block is not squeezed (e.g. not sharing a single 1fr cell with another block in a way that shrinks it).

### Test data

- A user whose effective screen scope for `activity-log` and `statistics` is `self` (e.g. user2 or a non-admin with self scope on these screens). Auth response must include `selfContext` (department, username, userId) for that user so the frontend can display locked values.

### Test environment

- Frontend: `http://localhost:3001` (or per contract)
- Backend: `http://localhost:9200`
- Database: per project setup

### 3.5 Browser automation verification (optional)

- **Applicable TCs**: TC-01 through TC-07 (manual / browser or integration).
- **Procedure**: Login → navigate to User Activity Log → snapshot to confirm user block values and 기타조건 visible; navigate to Activity Log Statistics → snapshot to confirm user block, 로그 타입, 기타조건 visible; compare user block field size between screens.
- **Reference**: `docs/workflow/BROWSER-AUTOMATION-VERIFICATION-POLICY.md`.

---

## 4. Checklist

### Frontend verification

- [ ] API parameters validated (selfContext from auth)
- [ ] UI behavior confirmed (user block visible with values; 기타조건/로그타입 visible when scope=self)
- [ ] Error handling verified

### Backend verification

- [ ] No API change required; optional check that auth returns selfContext
- [ ] Logs checked (if auth path changed)
- [ ] Performance checked (if applicable)

### Integration

- [ ] End-to-end flow tested (login as self-scope user, both screens)
- [ ] Edge cases tested (empty selfContext, missing auth field)

### Documentation

- [ ] Requirement doc completed
- [ ] Design docs updated (search-fields-by-screen, search-field-definition-items as needed)

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
# TC-01–TC-07: manual or browser automation per §3.5
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

- **Requirement ID**: 20260316-self-scope-user-block-visible-and-search-conditions
- **Root cause**: [To be filled after fix]
- **Actions taken**: [Summary of changes]
- **Result**: [Verification method and result]
- **Completed**: yyyy-MM-dd HH:mm

---

## 7. Final version (Korean) — add after all verification is complete

(Add Korean summary per `docs/workflow/DOCUMENT-LANGUAGE-POLICY.md` §2.3 when verification is complete.)

---

**Author**: Requirements subagent  
**Date**: 2026-03-16  
**Status**: In progress
