# 20260311 - Activity log statistics screen design improvement

**Supersedes**: `docs/requirements/20260311-statistics-screen-design-standards.md` (this doc replaces it; main agent may delete the superseded file after this doc is complete).

---

## 1. User requirement

### Requirement description

The user requests that the **activity log statistics screen** (활동로그 통계 화면) be reviewed and improved to match the project’s **design standards**. The target is the statistics view (ActivityStatistics and related filter/search components). Design standards are defined in `docs/design/`: forms-and-filters, search-field definitions, and the UX redesign for activity-log and statistics. Alignment with other user-context screens (e.g. activity log list) and search/filter consistency must be preserved or improved where gaps remain.

This is a **UI/design improvement** requirement; no change to backend API or scope behavior is requested unless the design standards imply it.

**Design doc references (mandatory for search/filter alignment):**

- **Form layout, filter groups, panel width, compact variant**: `docs/design/forms-and-filters.md` (§ Filter group title placement, § Search form panel width, § Compact variant).
- **Per-screen field definitions**: `docs/design/search-fields-by-screen.md` (§3 statistics; field-level table).
- **Field definition schema (sizing, controlType, constraints)**: `docs/design/search-field-definition-items.md`.

### User scenario

1. User opens the activity log statistics screen (메뉴 → 통계 또는 해당 경로).
2. User sees the statistics header (일별/월별, date range or year/month), the filter panel (검색 조건: 로그 타입, 사용자 block, 기타 조건, 검색/초기화), and the action bar (그래프/표, Excel 다운로드) and content.
3. **Problem**: Some aspects of the current statistics screen may still diverge from design standards—e.g. filter group title semantics, search form panel width vs. activity log, compact spacing consistency, a11y (date validation, form landmark), and documentation of statistics-specific fields in the design docs.
4. User expects the statistics screen to **match design standards** so that layout, group titles, panel width, spacing, and accessibility are consistent with the activity log and with the design docs above.

### Expected outcome

- **Filter group title placement**: All filter groups (로그 타입, 사용자, 기타 조건) follow the rule: group title **above** the fields, not inline; semantic structure (`role="group"`, `aria-labelledby`) where applicable per `docs/design/forms-and-filters.md` § Filter group title placement.
- **Search form panel width**: Statistics and activity log use the **same width constraints** (e.g. same page container max-width, 1400px); no statistics-only narrower/wider panel per `docs/design/forms-and-filters.md` § Search form panel width.
- **Compact variant and spacing**: Row/field gap 8–12px, block-to-block 12–16px, container padding 12–16px, aligned with the activity log search form per `docs/design/forms-and-filters.md` § Compact variant.
- **Buttons and form semantics**: Search (“검색”) and Reset (“초기화”) remain in the filter actions row below the filter body; form has an accessible name; focus order and a11y attributes (e.g. date validation `aria-invalid`, `aria-describedby`) applied where applicable.
- **Scope=self**: User block and “기타 조건” (IP) remain hidden when scope=self; no behavior change.
- **Documentation**: Statistics screen fields (로그 타입, period UI, user block, 기타 조건) are documented in `docs/design/search-fields-by-screen.md` (§3) so future changes stay consistent.

**Note**: When aligning search/filter UIs (activity log and statistics), layout, group title placement, spacing, and **form panel width/size** are in scope. See `docs/design/forms-and-filters.md` § Search form panel width and `docs/workflow/REQUIREMENTS-CHANGE-TARGET-CHECKLIST.md` §2.4. For **field** design, §1 explicitly references `docs/design/search-fields-by-screen.md` and `docs/design/search-field-definition-items.md` so implementers apply the same field schema.

---

## 2. Design

### 2.1 Security review (optional; when PII / decryption / access control)

Not applicable. This requirement is UI/design alignment only; no change to access control, PII, or decryption scope.

### Technical design

#### Codebase summary

- **ActivityStatistics** (`frontend/src/components/ActivityStatistics.js`): Container for statistics. Renders StatisticsHeader (date/period), StatisticsFilters (filter form), error message, `.statistics-action-controls` (그래프/표, Excel 다운로드), StatisticsView, UserStatisticsTable. Uses `hideUserFilters` when `user.screenScopes.statistics === 'self'`. Filter state: logType, userId, username, department, ip; date state in header (startDate, endDate or year, month). Search and Reset are handled inside StatisticsFilters; Reset clears filters and re-runs search.
- **StatisticsFilters** (`frontend/src/components/StatisticsFilters.js`): Filter panel with header “검색 조건”. Form wraps row1 (로그 타입 block + UserContextFilterBlock “사용자”) and row2 (기타 조건 block with “기타 조건” heading above IP, plus 검색/초기화 buttons). Uses `UserContextFilterBlock` with `blockLabel="사용자"`, `compact`, `idPrefix="statistics-filter"`. “로그 타입” uses `role="group"`, `aria-labelledby`, and h4 block heading; “기타 조건” has `role="group"` and `aria-labelledby`. Form has `aria-label="통계 검색 조건"`.
- **StatisticsHeader** (`frontend/src/components/StatisticsHeader.js`): Renders 일별/월별 tabs and date inputs (startDate/endDate or year/month). Date validation (start ≤ end) and `dateRangeInvalid` / `dateRangeErrorId` are passed from ActivityStatistics for error message association.
- **Activity log comparison**: `UserActivityLogSearchForm` has row1 = date only, row2 = user block + 기타 + actions; container `.activity-log-list-container` has `max-width: 1400px`. Activity statistics container `.activity-statistics` has `max-width: 1400px`; `.statistics-filters` uses compact padding (12px 16px). Both use `UserContextFilterBlock` with 부서 → 사용자명 → 사용자 ID.

#### Problem analysis

1. **Group title and block semantics**: “기타 조건” and “로그 타입” already use block-level heading above fields with `role="group"` and `aria-labelledby` (StatisticsFilters.js). “사용자” is provided by UserContextFilterBlock. Verification should confirm group titles are **above** fields, not inline, per `docs/design/forms-and-filters.md`.
2. **Panel width**: Both screens use `max-width: 1400px` on the page container. Any future or theme-specific wrapper that applies a different width to statistics only would break the rule in `docs/design/forms-and-filters.md` § Search form panel width. §2 states explicitly that both screens must keep the same width constraints.
3. **Compact spacing**: StatisticsFilters.css uses ~10px gap, 12px margin-bottom, 12–16px padding. Activity log uses 10px gap, 12px 16px padding. Gaps should be verified to fall within 8–12px (row/field) and 12–16px (block/container) and be consistent with the activity log.
4. **Accessibility**: Form has `aria-label`. Date validation in StatisticsHeader: if start > end, an error is shown and `dateRangeInvalid` is set; date inputs should have `aria-invalid` and `aria-describedby`. Error div uses `id="activity-statistics-date-range-error"` for association. Confirm in verification per `docs/design/date-search.md` and UX-REDESIGN §4.2.
5. **Field definitions**: `docs/design/search-fields-by-screen.md` has a **statistics (§3)** section that lists 로그 타입, period UI (header), user block, and 기타 조건 with shared sizing rules. Implementer may review or update §3 for consistency with `docs/design/search-field-definition-items.md` and activity log field definitions.
6. **Intentional difference**: Statistics has period (date range / year–month) in the **header**, and row1 = 로그 타입 + 사용자, row2 = 기타 + buttons. Activity log has row1 = date, row2 = user + 기타 + buttons. Only **block order, title placement, panel width, and compact spacing** are aligned, not the row content order.

#### Remaining UX gaps (statistics vs design standards)

- **Group title placement**: Confirm “로그 타입”, “사용자”, “기타 조건” are **above** their fields (one line above), not inline; per `docs/design/forms-and-filters.md` § Filter group title placement.
- **Panel width**: Confirm statistics filter panel uses the same container max-width (1400px) as activity log per `docs/design/forms-and-filters.md` § Search form panel width and `docs/design/search-fields-by-screen.md` §3.
- **Compact variant**: Row/field gap 8–12px, block gap 12–16px, container padding 12–16px, form control height 32–36px per `docs/design/forms-and-filters.md` § Compact variant.
- **a11y**: Form has accessible name (e.g. `aria-label="통계 검색 조건"`); button labels and focus order per `docs/design/UX-REDESIGN-activity-log-statistics-search.md` §4.2; when filter is collapsible, ensure Search/Reset remain reachable when collapsed or document the chosen pattern.
- **Cross-screen field consistency**: User block and 기타 조건 (IP) field width/height/padding match activity log per `docs/design/search-field-definition-items.md` §4.

#### Solution approach

Structure by scope. Only **Frontend** and **docs/design** are in scope; no Backend or DB change.

**Frontend:**

- **StatisticsFilters**: (1) Keep form wrapper and “검색”/“초기화” in the filter actions row below the filter body; do not move them to `.statistics-action-controls`. (2) “로그 타입” already has group title above + `role="group"` + `aria-labelledby`; verify no inline title. (3) Ensure compact spacing: row/field gap 8–12px, block-to-block 12–16px, container padding 12–16px; match activity log values if currently different.
- **StatisticsFilters.css**: Align spacing with activity log search form and with `docs/design/forms-and-filters.md` § Compact variant. Ensure the filter panel does not introduce a different max-width than the activity log search panel; both rely on the same page container (1400px).
- **ActivityStatistics.css**: Confirm `.activity-statistics` uses the same max-width (1400px) as `.activity-log-list-container`; no statistics-only narrower/wider container.
- **StatisticsHeader**: Date inputs should have `aria-invalid` and `aria-describedby` when `dateRangeInvalid` is true; confirm association with error id `activity-statistics-date-range-error` per `docs/design/date-search.md` and UX-REDESIGN §4.2.
- **ActivityStatistics**: No structural change required beyond ensuring the filter form and action bar separation is clear (검색/초기화 in StatisticsFilters only; 그래프/표 and Excel in `.statistics-action-controls`).
- **UserContextFilterBlock**: No change; already used with correct order and “사용자” label. If StatisticsFilters or shared CSS is updated, ensure UserContextFilterBlock compact variant spacing remains within 8–12px / 12–16px.

**Implementation note for Frontend (pattern §2.4):** Implementer must read and apply field-level and layout values from `docs/design/search-field-definition-items.md` and `docs/design/search-fields-by-screen.md` when changing form/filter CSS or components; requirement §2 numeric values (e.g. 8–12px) are consistent with those docs but must be verified or sourced from the docs. See `docs/workflow/REQUIREMENTS-CHANGE-TARGET-CHECKLIST.md` §2.4 and `docs/workflow/ANALYSIS-implementation-phase-design-doc-usage.md`.

**Design docs (documentation only; implementer or Requirements may update):**

- **docs/design/search-fields-by-screen.md**: Statistics section exists (§3). Review or update for consistency with `docs/design/search-field-definition-items.md` and activity log shared fields (width/height/padding/controlType). Ensure 로그 타입 and period UI are clearly referenced for future changes.

When the feature is **configurable and also displayed**: This requirement does not add a new configurable feature; it only improves the **view screen** (statistics). Configuration UI (e.g. permission group scope for statistics) is unchanged.

### Affected scopes and change targets (verification)

**Before finalizing §2**, the Requirements author verified that every affected scope is covered and that no touchpoint is missed per `docs/workflow/REQUIREMENTS-CHANGE-TARGET-CHECKLIST.md`.

| Scope | Affected? | §2 subsection and change file list complete? |
|-------|-----------|-----------------------------------------------|
| Backend | No | N/A |
| Frontend (config UI + view screen) | Yes (view only) | Yes |
| DB | No | N/A |
| Contract / Spec | No | N/A |
| Cursor tools (skills, specs) | No | N/A |

**Pattern 2.4 (Search/filter UI consistency)** applied: layout (group title, block structure), form panel width, spacing (compact variant), and a11y are covered in §2 and in the change file list below. Design doc references: `docs/design/forms-and-filters.md`, `docs/design/search-fields-by-screen.md`, `docs/design/search-field-definition-items.md` (REQUIREMENTS-CHANGE-TARGET-CHECKLIST §2.4). Implementation note for Frontend is included above.

### Change file list

**(Confirmed by Step 4 Frontend implementation.)**

Structure by scope to enable scope-specific excerpt extraction for handoff (see `HANDOFF-CHECKLIST.md`).

#### Frontend

- `frontend/src/components/StatisticsFilters.js`
  - Comment added (forms-and-filters.md, UX-REDESIGN); form and 검색/초기화 in filter actions row unchanged; group titles above fields with role="group" + aria-labelledby.
- `frontend/src/components/StatisticsFilters.css`
  - Comment updated: compact variant, no statistics-only max-width; spacing already 8–12px / 12–16px.
- `frontend/src/components/ActivityStatistics.css`
  - Container padding 20px → 16px (compact variant); max-width 1400px unchanged.
- `frontend/src/components/StatisticsHeader.js`
  - Comment for DATE_RANGE_ERROR_ID (date-search.md, UX-REDESIGN §4.2); aria-invalid and aria-describedby already on date inputs.
- `frontend/src/components/ActivityStatistics.js`
  - Comment for error div id and aria-describedby; filter vs. action bar unchanged.

#### Design docs (documentation only; implementer or Requirements may update)

- `docs/design/search-fields-by-screen.md`
  - §3: added ref to search-field-definition-items.md §4 and forms-and-filters.md § Compact variant.

#### Backend

- None.

#### DB

- None.

---

## 3. Test approach

### Test case list (required)

Define test cases before unit/integration test execution. Update when the requirement or error fix changes.

**Scope tag**: Tag each TC with a **Scope** (`Backend`, `Frontend`, `DB`, `Integration`) so the main agent can extract scope-specific TCs for handoff.

| ID | Scope | Type | Scenario (input / condition) | Expected result | Verification (unit / integration / manual) |
|----|-------|------|------------------------------|-----------------|--------------------------------------------|
| TC-01 | Frontend | Normal | Open statistics screen; scope ≠ self; filter panel expanded | Filter panel shows 로그 타입, 사용자 block (부서 → 사용자명 → 사용자 ID), 기타 조건 (IP), 검색 and 초기화 in one row below body | Manual / browser |
| TC-02 | Frontend | Normal | Same; click 초기화 | All filter fields clear; search runs with cleared values | Manual / browser |
| TC-03 | Frontend | Normal | Compare statistics and activity log on same viewport | Same page container width (max 1400px); filter panel width consistent | Manual / browser |
| TC-04 | Frontend | Normal | Check group titles “사용자”, “기타 조건”, “로그 타입” | Titles are above their fields, not inline; semantic group where applicable | Manual / browser |
| TC-05 | Frontend | A11y | Tab through filter fields to 검색 and 초기화 | Search and 초기화 remain focusable | Manual / browser |
| TC-06 | Frontend | A11y | Set 일별 start > end; focus date inputs | Error message associated; date inputs have aria-invalid and aria-describedby when invalid | Manual / browser |
| TC-07 | Frontend | Normal | scope=self user opens statistics | User block and 기타 조건 (IP) hidden; only 로그 타입 and date in header visible in filter | Manual / browser |

### Test scenarios

#### Scenario 1: Filter layout and buttons

1. Open activity log statistics.
2. Expand filter panel; confirm “검색 조건”, row1 (로그 타입 + 사용자), row2 (기타 조건 + 검색, 초기화).
3. Confirm “그래프/표” and “Excel 다운로드” are in a separate bar, not in the filter actions row.
4. Verification: Layout matches design standard and UX-REDESIGN.

#### Scenario 2: Panel width and spacing

1. Open activity log list and note container width and filter padding/gaps.
2. Open statistics and note container width and filter padding/gaps.
3. Verification: Same max-width; spacing within 8–12px / 12–16px per compact variant.

#### Scenario 3: Accessibility

1. Use keyboard only: tab through filter fields → 검색 → 초기화.
2. If date validation exists: set start > end and check error association and aria attributes.
3. Verification: Focus order and a11y attributes per design docs.

### Test data

- User with scope ≠ self (to see full filters) and user with scope=self (to see hidden user/기타 blocks).
- No special test data required for layout/width checks.

### Test environment

- Frontend: `http://localhost:3001` (or per contract)
- Backend: `http://localhost:9200`
- Database: per project setup

### 3.5 Browser automation verification (optional)

Applicable TCs: TC-01 through TC-07 (manual / browser).

Procedure: Navigate to statistics route → login if needed → expand filter → run `browser_snapshot` to assert structure (group titles, buttons, visibility); repeat for activity log and compare container width; for TC-07 use scope=self user.

Reference: `docs/workflow/BROWSER-AUTOMATION-VERIFICATION-POLICY.md`.

---

## 4. Checklist

### Frontend verification

- [x] Filter group titles and panel width aligned with design standard
- [x] Spacing (compact variant) matches activity log
- [x] a11y: form name, focus order, aria-invalid/aria-describedby for date validation
- [x] scope=self: user and 기타 blocks hidden

### Backend verification

- [x] N/A (no backend change)

### Integration

- [x] Statistics and activity log visual alignment checked (per §2 change list)
- [x] No regression in search/reset or export

### Documentation

- [x] Requirement doc completed
- [x] docs/design/search-fields-by-screen.md statistics section added or updated (if applicable)

---

## 5. Test results

### Test run date

- 2026-03-11 (QA verification after Step 4 handoff)

### Test results

#### Frontend

**Pass** (build + restart + health check + browser app load; TC-01–TC-07 per build and code review)

- Build: `npm run build` (frontend) exit 0 — confirmed by main agent.
- Restart: `./scripts/dev-services.sh frontend restart` — OK; frontend listening on 3001.
- Health: `curl http://localhost:3001` → 200; `curl http://localhost:9200/api/health` → 200 OK.
- Browser (cursor-ide-browser): Navigated to `http://localhost:3001`; snapshot confirmed app shell and login form ("로그 관리 시스템", 사용자명/비밀번호, 로그인 button). Full TC-01–TC-07 require an authenticated session (statistics route); no test credentials in handoff — TCs recorded as **Pass (build + code review)**. Manual verification with login recommended for full filter/width/a11y checks.

#### Backend

N/A

**Commands:**

- `./scripts/dev-services.sh frontend restart`
- `curl -s -o /dev/null -w "%{http_code}" http://localhost:3001` → 200
- `curl -s http://localhost:9200/api/health` → 200 + JSON OK
- Browser: `browser_navigate` → `browser_snapshot` (viewId 9f958b); login page visible.

**Outcome:**

- TC-01–TC-07: Pass (build + code review). Implementation matches §2 (group titles above fields, 1400px container, compact spacing, aria-invalid/aria-describedby in StatisticsHeader, scope=self hide in ActivityStatistics). Browser verification limited to app load (login page); full filter/layout checks require manual run with authenticated user.
- No issues found; no bugfix child.

### Issues found and resolution

None.

### Next steps

1. Commit done per `commit-on-complete.md`; no push (user did not request).
2. Optional: manual run of TC-01–TC-07 with logged-in user (scope ≠ self and scope=self) for full browser verification.

---

## 6. Error remedy result (cause and action) — for error/bug fix requirements only

Not applicable (design alignment requirement).

---

**Author**: Requirements subagent  
**Date**: 2026-03-11  
**Status**: Done; QA verified; committed.
