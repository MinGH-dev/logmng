# 20260310 - Search UI unified concept (동일 컨셉)

## 1. User requirement

### Parent reference

This requirement defines the **unified UI/UX concept** for all search and filter interfaces. It builds on:

- **docs/requirements/20260310-search-consistency-all-screens.md** — per-screen table of required axes (부서, 이름, 사용자 ID; scope=self hide) and change file list.
- **docs/requirements/20260310-search-consistency-phase1.md** — Phase 1 implementation (activity-log + statistics: add department / name).

This doc adds the **“동일한 컨셉” (unified concept)** layer: **same field order**, **same visual grouping**, **same labels**, and optional **shared component** so that every screen with user-context search/filter looks and behaves consistently.

### Requirement description

Unify all search UIs across the application to the **same concept**: consistent **layout**, **field order** (부서 → 이름 → 사용자 ID), **visual grouping** (user-context block vs screen-specific block), and **scope=self** hiding rule. Where user-context filters exist or will be added, they must follow the same order, labels, and grouping so users experience one coherent pattern.

### User scenario

1. A user moves between **activity-log**, **statistics**, **search-history**, **pending-approvals**, **user-management**, and **permission-group-management**. They expect the “user/requester” filters to appear in the **same order** (부서 → 이름 → 사용자 ID) and with the **same labels** (e.g. “사용자명”) so they do not have to re-learn each screen.
2. When scope is **self** (본인만), the user/department filter block is **hidden** on scope-supporting screens (activity-log, statistics, search-history, pending-approvals); this behaviour is already specified in the parent docs and must be preserved.
3. **Problem**: Today, activity-log shows **부서 → 사용자 ID → 사용자명**; statistics shows **사용자 ID → 사용자명 → 부서 → IP**. Order and grouping differ, so the “same concept” is not met. Search-history, pending-approvals, user-management, and permission-group user picker either lack user-context filters or will add them later; they must follow the unified concept from the start or when added.

### Expected outcome

- **Unified field order**: On every screen that shows user-context filters, the order is **부서 → 이름(사용자명) → 사용자 ID** (then screen-specific fields such as IP, action type, log type).
- **Unified visual grouping**: “User/requester” filters (부서, 이름, 사용자 ID) are in **one block** (with optional group label “사용자” or “요청자”); screen-specific filters are in a separate block (same form, below or beside).
- **Unified labels**: Use **“사용자명”** consistently for the name field in all search/filter forms (docs may still say “이름(사용자명)”).
- **scope=self**: The whole user/department filter block is hidden when scope is self; no change to existing rule.
- **Optional shared component**: A reusable **UserContextFilterBlock** (or equivalent component/hook) ensures order, labels, grouping, and scope-based visibility in one place so all screens stay aligned.
- **main (로그 검색)**: No user-context axes; date + log type + type-specific fields only (unchanged).

---

## 2. Design

### Codebase summary

- **Frontend — activity-log**: `UserActivityLogSearchForm.js` renders date range, then when `!hideUserFilters`: **부서 → 사용자 ID → 사용자명** (department, userId, username). Order should be **부서 → 사용자명 → 사용자 ID**. IP and action type are in a separate row; grouping is present but field order is wrong.
- **Frontend — statistics**: `StatisticsFilters.js` renders log type, then when `!hideUserFilters`: **사용자 ID → 사용자명 → 부서 → IP** in one row. Order should be **부서 → 사용자명 → 사용자 ID**, then IP; user-context block should be visually grouped.
- **Frontend — search-history, pending-approvals, user-management, permission-group**: Search-history and pending-approvals may have no requester filter UI yet; user-management has department tree but no search form; permission-group user picker may have userId only. When these get user-context filters, they must use the same order and grouping (and optional shared component).
- **Frontend — main**: `SearchForm.js`, `ImageLogSearchForm.js`, `AdvancedSearchForm.js` — log-domain only; no user 3 axes. No change for this requirement.
- **Design reference**: `docs/design/forms-and-filters.md` — grouping, submit/reset, spacing; consistent with one “user/requester” block.
- **Backend**: No API contract change for “concept” alone; filter param names (department, username, userId) are already defined in parent requirements. Backend is out of scope unless a future phase adds new params.
- **Single source of rules**: `.cursor/skills/search-consistency-domain/SKILL.md`, `docs/analysis-search-consistency-by-screen.md` — axes and scope=self; this doc adds **order** and **grouping** as the standard.

### Problem analysis

1. **Inconsistent field order**: Activity-log: 부서, 사용자 ID, 사용자명. Statistics: 사용자 ID, 사용자명, 부서, IP. The analysis and all-screens doc recommend **부서 → 이름 → 사용자 ID**; current code does not match.
2. **Inconsistent grouping**: Activity-log has user filters in one row and IP/action in another; statistics has all in one row with different order. No single “user-context block” pattern.
3. **Labels**: “사용자명” vs “이름” may differ across screens; one consistent label is needed.
4. **Future screens**: Search-history, pending-approvals, user-management, permission-group will add or already plan user/requester filters; without a defined concept (order, grouping, shared component), each may implement differently.

### Solution approach

Orchestrated from **search-consistency-domain** skill, **docs/analysis-search-consistency-by-screen.md**, **docs/design/forms-and-filters.md**, and **UX subagent** recommendations (readonly §2 design input).

**Unified concept (동일 컨셉)**

1. **Field order**: **부서 → 이름(사용자명) → 사용자 ID** on every user-context screen. Rationale: broad (department) → narrow (name) → single (userId); reduces cognitive load and keeps the same pattern across screens.
2. **Visual grouping**: One block for “사용자/요청자” (부서, 이름, 사용자 ID). Screen-specific fields (IP, action type, log type, date) in a separate block (below or beside), with clear separation (heading, spacing). scope=self → hide the whole user/requester block.
3. **Labels**: Use **“사용자명”** in the UI everywhere; requirement/spec text may keep “이름(사용자명)” for clarity.
4. **Shared component**: Prefer a **UserContextFilterBlock** (or equivalent) reused on activity-log, statistics, and (when added) search-history, pending-approvals, user-management, permission-group user picker. Props: block label (“사용자” / “요청자”), scope-based visibility (hideUserFilters), values and onChange. Screen-specific fields stay in each screen’s form.
5. **Submit/Reset**: One primary “Search”/“Apply” and one “Reset” per form; follow `docs/design/forms-and-filters.md`.

**Frontend**

- **activity-log**: In `UserActivityLogSearchForm.js`, reorder user-context fields to **부서 → 사용자명 → 사용자 ID**. Keep user-context in one block; IP and action type in another. Optionally replace the inline block with `UserContextFilterBlock`.
- **statistics**: In `StatisticsFilters.js`, reorder to **부서 → 사용자명 → 사용자 ID**, then IP. Group user-context in one block; log type and IP in separate logical blocks. Optionally use `UserContextFilterBlock`.
- **search-history, pending-approvals**: When requester filters are added (per all-screens doc), use the same order (부서 → 사용자명 → 사용자 ID) and same block grouping; hide block when scope=self. Prefer `UserContextFilterBlock` with label “요청자”.
- **user-management**: When search form is added (per all-screens doc), use 부서 → 사용자명 → 사용자 ID and same block. No scope=self hide (management screen).
- **permission-group-management**: When user picker gets department/name filters, use same order and labels; no scope hide.
- **Shared component (optional)**: Add `UserContextFilterBlock.js` (or under `components/common/`) with props for blockLabel, hideUserFilters, departmentList, userList, values, onChange; used by activity-log, statistics, and future screens. Implementing agent may choose per-screen consistency without a new component if preferred; order and grouping are mandatory.
- **Configuration UI**: No change; scope is configured in permission group edit (ScreenSelectionTree, PermissionGroupPanel). View screens already use `user.screenScopes[screenId]` for hideUserFilters.

**Backend**

- No change for this requirement. Filter param names and scope behaviour are defined in 20260310-search-consistency-phase1 and 20260310-search-consistency-all-screens.

**DB**

- None.

**Contract / Spec**

- Optional: In `docs/api-definition.md` or `docs/design/forms-and-filters.md`, add a short note that user-context filter **order** in the UI is **부서 → 이름(사용자명) → 사용자 ID** for consistency. No API change.

**Cursor tools**

- **.cursor/skills/search-consistency-domain/SKILL.md**: Reference this requirement and state that **field order** is **부서 → 이름(사용자명) → 사용자 ID** and that a single user-context block is the standard.
- **docs/analysis-search-consistency-by-screen.md**: Optional one-line pointer to this doc for “unified concept” (order, grouping, labels).

### Affected scopes and change targets (verification)

Before finalizing §2, the checklist in `docs/workflow/REQUIREMENTS-CHANGE-TARGET-CHECKLIST.md` was applied.

| Scope | Affected? | §2 subsection and change file list complete? |
|-------|-----------|-----------------------------------------------|
| Backend | No | N/A — concept only; API already defined in parent docs. |
| Frontend (config UI + view screen) | Yes | Yes — view screens (activity-log, statistics; and when added search-history, pending-approvals, user-management, permission-group). Config UI not changed (scope already configured). |
| DB | No | N/A. |
| Contract / Spec | Optional | Optional — design/API doc note on field order. |
| Cursor tools (skills, analysis) | Yes | Yes — search-consistency-domain, analysis doc. |

**Domain pattern**: This requirement matches “search/filter consistency” (form layout, field order). Touchpoints: Frontend view components only; no new scope-supporting screen, no API change. All view screens with user-context search/filter are listed in the change file list below.

### Change file list

**(Confirmed by Step 4 Frontend. Actual files changed.)**

#### Frontend

- `frontend/src/components/UserActivityLog/UserActivityLogSearchForm.js`
  - Reorder user-context fields to **부서 → 사용자명 → 사용자 ID**. Keep one block for user-context, separate row/block for IP and action type. Use label “사용자명”.
- `frontend/src/components/StatisticsFilters.js`
  - Reorder to **부서 → 사용자명 → 사용자 ID**, then IP. Group user-context in one block; log type and IP in separate blocks. Use label “사용자명”.
- `frontend/src/components/SearchHistory/SearchHistoryList.js` (when requester filters are added)
  - Add requester filter block with order 부서 → 사용자명 → 사용자 ID; hide when scope=self. Prefer shared component if present.
- `frontend/src/components/PendingApprovals/PendingApprovals.js` (or sibling search form, when requester filters are added)
  - Same as search-history: 부서 → 사용자명 → 사용자 ID; hide when scope=self.
- `frontend/src/components/UserManagement/UserManagement.js` (when search form is added)
  - Search form with 부서 → 사용자명 → 사용자 ID; same block grouping.
- `frontend/src/components/PermissionGroupManagement/*` (user picker, when department/name filters are added)
  - Same order and labels for filtering user candidates.
- (UserContextFilterBlock implemented as above; reuse for search-history, pending-approvals when requester filters are added.)

#### Backend

- None for this requirement.

#### DB

- None.

#### Contract / Spec

- `docs/design/forms-and-filters.md` — **Done**: Added "User-context filter order and grouping" subsection (부서 → 이름(사용자명) → 사용자 ID, one block, reference to this requirement).

#### Cursor tools

- `.cursor/skills/search-consistency-domain/SKILL.md`
  - Add reference to this requirement; state standard **field order** (부서 → 이름 → 사용자 ID) and **single user-context block**.
- `docs/analysis-search-consistency-by-screen.md`
  - Optional: One-line pointer to `docs/requirements/20260310-search-ui-unify.md` for unified concept (order, grouping, labels).

---

## 3. Test approach

### Test case list (required)

Scope tags support Frontend handoff and QA verification. Only TCs that apply to the **unified concept** (order, grouping, labels) are listed; scope=self behaviour is covered in parent docs.

| ID | Scope | Type | Scenario (input / condition) | Expected result | Verification |
|----|-------|------|-----------------------------|-----------------|--------------|
| TC-01 | Frontend | Normal | activity-log: scope=team or all, open search form | User-context block shows **부서 → 사용자명 → 사용자 ID** in that order (then IP, action type in separate block). | Manual / browser |
| TC-02 | Frontend | Normal | statistics: scope=team or all, open filters | User-context block shows **부서 → 사용자명 → 사용자 ID** in that order (then IP); label “사용자명” used. | Manual / browser |
| TC-03 | Frontend | Normal | activity-log: scope=self | User/department filter block hidden (unchanged). | Manual / browser |
| TC-04 | Frontend | Normal | statistics: scope=self | User/department filter block hidden (unchanged). | Manual / browser |
| TC-05 | Frontend | Normal | Compare activity-log and statistics (scope ≠ self) | Same field order (부서, 사용자명, 사용자 ID) and same label “사용자명” on both screens. | Manual / browser |
| TC-06 | Frontend | Normal | When search-history has requester filters | Requester block shows 부서 → 사용자명 → 사용자 ID; hidden when scope=self. | Manual / browser (when implemented) |
| TC-07 | Frontend | Normal | When pending-approvals has requester filters | Same as TC-06. | Manual / browser (when implemented) |
| TC-08 | Frontend | Normal | main (로그 검색) | No user-context axes; only date, log type, type-specific fields. | Manual / browser |

### Test scenarios

#### Scenario 1: Field order and labels (activity-log and statistics)

1. Set scope=team or all for activity-log and statistics.
2. Open activity-log: confirm search form shows 부서, then 사용자명, then 사용자 ID (then action type, IP).
3. Open statistics: confirm filter form shows 부서, then 사용자명, then 사용자 ID (then IP). Label “사용자명” on both.
4. Confirm both screens use the same order and label.

#### Scenario 2: scope=self unchanged

1. Set scope=self for activity-log and statistics.
2. Open each screen; confirm user/department filter block is hidden and list/data is current user only.

### Test data

- At least one permission group with scope=team or all for activity-log and statistics; one with scope=self. No special data beyond existing setup.

### Test environment

- Frontend: http://localhost:3001 (or per contract).
- Backend: http://localhost:9200 (per contract).

---

## 4. Checklist

### Frontend verification

- [ ] activity-log: field order 부서 → 사용자명 → 사용자 ID; user-context in one block.
- [ ] statistics: field order 부서 → 사용자명 → 사용자 ID; user-context in one block; label “사용자명”.
- [ ] scope=self: user block hidden on activity-log and statistics.
- [ ] When other screens get user/requester filters: same order and grouping.

### Backend verification

- [ ] N/A for this requirement (no API change).

### Integration

- [ ] Visual comparison: activity-log and statistics show same order and labels when scope ≠ self.

### Documentation

- [ ] Requirement doc completed; optional update to forms-and-filters.md or api-definition (field order).
- [ ] Cursor skill and analysis doc reference this requirement.

---

## 5. Test results

### Test run date

- (To be filled when tests are run.)

### Test results

(To be filled after implementation and QA verification.)

---

---

## § UX review

*Added by UX subagent. Design recommendations for §2 and Frontend implementation; no code changes by UX.*

### 1. Layout and visual hierarchy

- **Field order**: The proposed order **부서 → 사용자명 → 사용자 ID** (broad → narrow → single) is appropriate. It matches a natural “organization → person → identifier” mental model and reduces re-learning when switching screens. **No change recommended.**
- **Block label**: Use **“사용자”** for activity-log, statistics, user-management, and permission-group (who is the *user* being filtered). Use **“요청자”** only for search-history and pending-approvals (who *requested* the action). This keeps semantics clear without changing the shared field order or layout.
- **Separation between blocks**: Align with **`docs/design/forms-and-filters.md`**: one logical block for “user/requester” (부서, 사용자명, 사용자 ID), a **separate block** for screen-specific fields (IP, action type, log type, date). Use **clear separation**: e.g. a short group heading (“사용자” / “요청자” and “기타 조건” or screen-specific label), or consistent **vertical spacing** (e.g. 16–24px gap between blocks). Group title placement: **`docs/design/forms-and-filters.md` §Filter group title placement** (공통 규칙). Same form container; avoid a single undifferentiated row of all fields.
- **Spacing**: Reuse the design system’s form spacing (e.g. 16px or 24px gap between rows/columns per `forms-and-filters.md`). Keep spacing **consistent** between activity-log and statistics so the “same concept” is visually obvious.

### 2. Accessibility (a11y)

- **Labels**: Every filter input must have a **visible `<label>`** or programmatic equivalent (`aria-label` / `aria-labelledby`) per **`docs/design/text-input.md`**. Use the unified label **“사용자명”** for the name field in the UI; ensure the same string is used in the accessible name so screen readers hear it consistently.
- **Focus order**: Tab order should follow **visual order**: 부서 → 사용자명 → 사용자 ID, then screen-specific fields (e.g. IP, action type), then Search/Apply, then Reset. No tab index overrides unless required for a modal or trap; keep source order = focus order.
- **Grouping for screen readers**: Wrap the user-context block in a **`<fieldset>` with `<legend>`** (e.g. “사용자” or “요청자”) so assistive tech announces the group. If a `<fieldset>` is not used, give the container a **`role="group"`** and **`aria-labelledby`** pointing to the block heading. This matches the “filter groups” idea in `forms-and-filters.md` and improves navigation for screen-reader users.
- **Errors and required**: If any field is required or has validation, follow **`docs/design/text-input.md`**: `aria-invalid`, `aria-describedby` for error text, and `aria-required` where applicable.

### 3. Responsive behaviour and density

- **Narrow viewports**: Follow **`docs/design/forms-and-filters.md`** (e.g. grid with `minmax(200px, 1fr)` or flex wrap). On narrow screens, stack the three user-context fields **vertically** (one per row) rather than squeezing them into one row, so labels and inputs remain readable. Screen-specific block can stack as well.
- **Density (activity-log vs statistics)**: Activity-log may have more fields (date, IP, action type); statistics has log type and IP. It is acceptable for **form density to differ by screen** as long as **(1)** the **user-context block** (부서, 사용자명, 사용자 ID) is **identical in order and grouping** and **(2)** the same spacing/gap rules apply within that block. No need to force the same total row count; consistency of the *user block* is the goal.

### 4. Consistency with existing design

- **Alignment**: The requirement aligns with **`docs/design/forms-and-filters.md`**: filter groups, explicit Submit/Reset, and a single primary action per form. The “one block for user/requester, separate block for screen-specific” pattern fits the “group related fields” rule in that doc.
- **Suggested design-doc update**: Add to **`docs/design/forms-and-filters.md`** (or a short “User-context filters” subsection):
  - **User-context filter order**: On all screens that show user/requester filters, the **UI order** is **부서 → 이름(사용자명) → 사용자 ID**. These three fields are grouped in one block (with group label “사용자” or “요청자” by screen); screen-specific filters (IP, log type, date, action type, etc.) sit in a separate block. Reference: `docs/requirements/20260310-search-ui-unify.md`.

This makes the unified concept part of the design standard so future screens (search-history, pending-approvals, user-management, permission-group) follow it without re-deriving from the requirement alone.

### 5. Shared component (UserContextFilterBlock)

- **UX recommendation**: **Introducing a shared component is recommended** for consistency and maintainability. One component (or hook + presentational block) that owns **order**, **labels**, **grouping**, and **scope-based visibility** ensures that (1) all screens stay aligned as new fields or screens are added, and (2) any future a11y or layout change to the user-context block is done once. From a pure UX perspective, the same visual and interaction pattern should be implemented once and reused; a shared component is the most reliable way to enforce that. The requirement’s “optional” is acceptable for implementation flexibility (e.g. adopt in Phase 1 for activity-log and statistics, then reuse for search-history and pending-approvals when those filters are added).

---

**Author**: Requirements subagent  
**Date**: 2026-03-10  
**Status**: In progress (handoff-ready for Step 4 Frontend; §5 to be filled by QA after verification)
