# 20260313 - Activity log and statistics design standards alignment

## §0 Scope and supersedes

This document is the **single requirement** for **activity-log** (사용자 활동 이력) and **Activity Statistics** (활동로그 통계) **design standards alignment** only. It covers: layout (row1 = dates, row2 = rest), filter group title placement, search form panel width, compact variant spacing, form semantics (Search/Reset, `<form>`), optional field constraints, and accessibility. It does **not** cover feature changes (e.g. 일별/월별 통합, Excel, 그래프, 검색 조건 확장); see `docs/requirements/20260206-activity-log-statistics-improvement.md` for those.

- **Supersedes**: `docs/requirements/20260311-activity-log-statistics-design-improvement.md` (removed). That document is no longer in the repo; this one replaces it.
- **Does not supersede**: `docs/requirements/20260206-activity-log-statistics-improvement.md` — that requirement is for **feature** improvement (일별/월별 통합, Excel, 그래프, 검색 조건 확장), not design-standards alignment. Keep it as a separate requirement.

---

## 1. User requirement

### Requirement description

Improve the **User Activity Log** (사용자 활동 이력) and **Activity Statistics** (활동로그 통계) screens to align with the project’s **design standards**. Improvements apply to layout, filter group title placement, search form panel width, compact variant spacing, form semantics (Search/Reset, `<form>`), optional field constraints, and accessibility. No backend API or scope behaviour change is required unless the design docs imply it.

**Design doc references (mandatory for this requirement):**

- **Form layout, filter groups, panel width, compact variant, block tiers**: `docs/design/forms-and-filters.md` (§ Filter group title placement, § Search form panel width, § Compact variant, § Single application point, § Width by role, § Filter block tiers, § Single row for non-date).
- **Per-screen field definitions (표준정의 단일 소스)**: `docs/design/search-fields-by-screen.md` (§2 activity-log, §3 statistics; field-level tables).
- **Field definition schema (sizing, controlType, constraints, cross-field rules)**: `docs/design/search-field-definition-items.md` (§4 cross-field rules).
- **CSS standard and exceptions**: `docs/design/css-standard-and-exceptions.md`; standard values and wrapper class in `frontend/src/styles/search-filter-standard.css`.
- **UX redesign (both screens)**: `docs/design/UX-REDESIGN-activity-log-statistics-search.md`.

When the requirement defines or aligns search/filter **fields** or **layout**, §1 explicitly references both `docs/design/search-fields-by-screen.md` and `docs/design/search-field-definition-items.md` so implementers apply the same field schema. When **pattern §2.4** (search/filter UI consistency) applies, §2 includes an **Implementation note for Frontend** (see §2).

### User scenario

1. User opens **User Activity Log** or **Activity Statistics** and sees the search/filter area (date row, user-context block, screen-specific block, Search/Reset).
2. **Problem**: Layout, group title placement, panel width, spacing, or button placement may diverge from design standards; the two screens may look or behave differently from each other or from the design docs.
3. User expects **both screens** to match design standards: same group title placement (above fields, not inline), same panel width (e.g. page container max-width 1400px), same compact spacing (8–12px row/field gap, 12–16px block gap, 12–16px container padding), same button labels (“검색”, “초기화”) and placement (filter actions row below filter body), and form semantics (`<form>`, one primary Search, one secondary Reset). Activity log: row 1 = dates only, row 2 = user block + 기타 조건 + actions; no “필터 접기” unless design doc specifies otherwise.

### Expected outcome

- **Filter group title placement**: All filter groups (e.g. “사용자”, “기타 조건”, “로그 타입”) follow the rule: group title **above** the fields, not inline; semantic structure (`role="group"`, `aria-labelledby`) per `docs/design/forms-and-filters.md` § Filter group title placement.
- **Search form panel width**: Activity log and statistics use the **same width constraints** (e.g. page container max-width 1400px); no screen-specific narrower/wider panel per `docs/design/forms-and-filters.md` § Search form panel width.
- **Compact variant and spacing**: Row/field gap 8–12px, block-to-block 12–16px, container padding 12–16px, form control height 32–36px per `docs/design/forms-and-filters.md` § Compact variant; use `frontend/src/styles/search-filter-standard.css` (e.g. `var(--sf-*)`) where applicable; exceptions only in component CSS with comment and Exception index per `docs/design/css-standard-and-exceptions.md`.
- **User block field size (동일 크기)**: 부서, 사용자명, 사용자 ID 필드는 활동 이력과 통계에서 **동일한 min/max width 및 시각적 크기**로 표시한다. Per `docs/design/search-fields-by-screen.md` §3 (활동 이력과 동일), §4 (화면 간 동일 적용) and `docs/design/search-field-definition-items.md` §4, §4.5; use block-level width `var(--sf-field-user-block-min)`, `var(--sf-field-user-block-max)` or same grid/field sizing on both screens so the user block does not appear narrower on statistics (e.g. avoid sharing a single `1fr` cell with 로그 타입).
- **Buttons and form semantics**: Both screens: Search (“검색”) and Reset (“초기화”) in a **filter actions row** directly below the filter body; filter area is a `<form>` with one primary and one secondary; Statistics does not place Search/Reset in the same row as “그래프/표” and “Excel 다운로드” per `docs/design/UX-REDESIGN-activity-log-statistics-search.md`.
- **Activity log layout**: Row 1 = 시작 날짜, 종료 날짜 only; Row 2 = user-context block (부서 → 사용자명 → 사용자 ID) + 기타 조건 (액션 타입, IP) + 검색, 초기화. No “필터 접기” toggle; filter body always visible (unless design doc later specifies collapsible).
- **Single row for non-date (날짜 제외 단일 행)**: Except for the date/period block, all other filter blocks (로그 타입, 사용자, 기타 조건) and Search/Reset are placed in a **single row** (e.g. row2). The date/period block stays in a separate row (row1 or header). Per `docs/design/forms-and-filters.md` § Single row for non-date.
- **Form per mode (모드별 폼 — when date fields cannot be unified)**: When a screen has a period mode (e.g. 일별 / 월별) and date-related search fields (일자/일시 등) cannot be unified into one set (different field sets per mode), design **separate forms per mode** (e.g. 일별 form, 월별 form) and load the corresponding form on mode switch; do not mix both field sets conditionally in a single form. Per `docs/design/forms-and-filters.md` § Form per mode.
- **Scope=self**: User block and screen-specific filters (e.g. IP, 기타 조건) remain hidden when scope=self; no behaviour change.
- **Optional field constraints** (implement only if product confirms): 사용자명 max 5 characters (한글), 사용자 ID max 8 digits on activity log per `docs/design/search-fields-by-screen.md`; implement only if explicitly confirmed.
- **Documentation**: Both screens’ fields and layout must be documented or consistent with `docs/design/search-fields-by-screen.md` (§2, §3) and `docs/design/search-field-definition-items.md`.

**Note**: Layout, group title placement, spacing, and **form panel width/size** are in scope. See `docs/design/forms-and-filters.md` § Search form panel width and `docs/workflow/REQUIREMENTS-CHANGE-TARGET-CHECKLIST.md` §2.4.

---

## 2. Design

### 2.1 Security review (optional; when PII / decryption / access control)

Not applicable. This requirement is UI/design alignment only; no change to access control, PII, or decryption scope.

### Technical design

#### Codebase summary

- **User Activity Log**
  - **UserActivityLogSearchForm.js**: Form with row1 (시작/종료 날짜) and row2 (UserContextFilterBlock “사용자”, 기타 조건 block, 검색/초기화). May have collapsible “필터 접기” or single-row legacy; target state: row1 = dates only, row2 = rest; no collapsible toggle unless design doc says otherwise.
  - **UserActivityLog.css**: `.activity-log-list-container` max-width 1400px; `.activity-log-search-form` padding; row/block/heading/actions classes. Compact variant values must align with `search-filter-standard.css` (e.g. `var(--sf-row-gap)`, `var(--sf-block-gap)`).
  - **UserContextFilterBlock.js / .css**: Shared block 부서 → 사용자명 → 사용자 ID; used by both activity log and statistics; compact variant; optional props for usernameMaxLength / userId max length if product confirms.

- **Activity Statistics**
  - **ActivityStatistics.js**: Container; renders StatisticsHeader (date/period), StatisticsFilters (filter form), `.statistics-action-controls` (그래프/표, Excel 다운로드). Uses `hideUserFilters` when scope=self. Search and Reset must live inside StatisticsFilters (filter actions row), not in `.statistics-action-controls`.
  - **StatisticsFilters.js**: Filter panel “검색 조건”; form must wrap row1 (로그 타입 + UserContextFilterBlock “사용자”), row2 (기타 조건 block + 검색, 초기화). Must use `<form>`, `aria-label` (e.g. “통계 검색 조건”), and group titles **above** fields (no inline “기타 조건”); “검색” and “초기화” in filter actions row below body.
  - **StatisticsFilters.css**: Spacing 8–12px / 12–16px; no statistics-only max-width; use standard vars where possible; “기타 조건” heading block-level above fields (remove inline heading pattern if present).
  - **ActivityStatistics.css**: `.activity-statistics` max-width 1400px (same as `.activity-log-list-container`); container padding per compact variant.
  - **StatisticsHeader.js**: Date validation (start ≤ end); `aria-invalid`, `aria-describedby` for date range error per `docs/design/date-search.md`.

- **Shared**
  - **frontend/src/styles/search-filter-standard.css**: Defines `--sf-container-padding`, `--sf-row-gap`, `--sf-block-gap`, `--sf-control-height`, `--sf-control-padding`, `--sf-btn-*`. Components must use these for standard values; exceptions only in component CSS with comment + `docs/design/css-standard-and-exceptions.md` §5 Exception index.

#### Problem analysis

1. **Inconsistent layout between screens**: Activity log may use different row split or collapsible; statistics may have “기타 조건” title inline vs activity log above-fields; both must follow the same design rules (group title above, same panel width, same compact spacing).
2. **Statistics filter semantics**: Statistics filter must be a `<form>` with Search and Reset in the filter actions row (not in the view/export bar); Reset must be present and clear all filter fields.
3. **Panel width and spacing**: Both screens must share the same page container max-width (1400px) and same compact variant (8–12px, 12–16px, 12–16px padding); any hardcoded values in component CSS should migrate to standard vars or match the standard.
4. **CSS exceptions**: Screen-specific overrides must be documented in component CSS with a comment and in `docs/design/css-standard-and-exceptions.md` §5 Exception index to avoid nesting and duplicate “standards”.
5. **Activity log row layout**: If current implementation is single-row or different split, align to row1 = dates only, row2 = user block + 기타 조건 + actions; remove “필터 접기” so filter body is always visible unless design doc specifies otherwise.
6. **Accessibility**: Form landmark, group/label association, button labels, focus order, and date validation (`aria-invalid`, `aria-describedby`) per `docs/design/UX-REDESIGN-activity-log-statistics-search.md` §4.2 and `docs/design/date-search.md`.

#### Solution approach

Structure by scope. Only **Frontend** and **docs/design** are in scope; no Backend or DB change.

**Frontend:**

- **UserActivityLogSearchForm.js**: (1) Ensure row1 contains only 시작 날짜 and 종료 날짜; row2 contains UserContextFilterBlock, 기타 조건 block (title above fields), and 검색/초기화. (2) Remove “필터 접기” toggle and collapsible state so filter body is always visible (unless design doc specifies collapsible). (3) Labels “검색” and “초기화” per design. (4) Optional: add username max 5 chars, userId max 8 digits if product confirmed (via UserContextFilterBlock props).
- **UserActivityLog.css**: Align spacing with `docs/design/forms-and-filters.md` § Compact variant; use `var(--sf-*)` from `search-filter-standard.css` where applicable; keep `.activity-log-list-container` max-width 1400px; ensure group titles are block-level above fields.
- **StatisticsFilters.js**: (1) Wrap filter content in `<form>` with `aria-label="통계 검색 조건"`. (2) “기타 조건” group title **above** its fields (block-level), same pattern as activity log; `role="group"` and `aria-labelledby`. (3) Search (“검색”) and Reset (“초기화”) in filter actions row inside/below filter body, not in `.statistics-action-controls`. (4) Reset clears all filter fields (로그 타입, 부서, 사용자명, 사용자 ID, IP) and optionally re-runs with cleared values. (5) When 일별/월별 date fields cannot be unified (different field sets per mode), use **separate form per mode** per `docs/design/forms-and-filters.md` § Form per mode (e.g. 일별 form vs 월별 form; load corresponding form on mode switch).
- **StatisticsFilters.css**: Use standard vars for gap/padding where possible; ensure “기타 조건” heading is above fields (no inline class for this block); same compact spacing as activity log; no statistics-only max-width. Ensure row-1 layout gives the user block sufficient width (e.g. separate column or min-width for user block) so user block field sizes match activity log; avoid user block and 로그 타입 sharing a single `1fr` cell so the user block is not squeezed.
- **ActivityStatistics.css**: Confirm `.activity-statistics` max-width 1400px and container padding 12–16px (compact variant); same effective width as activity log.
- **ActivityStatistics.js**: Keep “그래프/표” and “Excel 다운로드” in `.statistics-action-controls` only; do not duplicate Search/Reset there.
- **StatisticsHeader.js**: Date inputs: `aria-invalid` and `aria-describedby` when date range invalid; error id (e.g. `activity-statistics-date-range-error`) for association per `docs/design/date-search.md`.
- **UserContextFilterBlock.js / .css**: Apply same user block field width on both screens: use `var(--sf-field-user-block-min)`, `var(--sf-field-user-block-max)` for the block container or ensure grid/field min/max (e.g. from `search-field-definition-items.md` §4.5) is identical on activity log and statistics; ensure compact spacing uses standard or same values as both screens. If product confirms, add optional `usernameMaxLength`, userId 8-digit constraint for activity-log usage.
- **search-filter-standard.css**: No change unless design doc values are updated; components must consume `var(--sf-*)` and avoid duplicating values. New exceptions only in component CSS with comment + Exception index.

**Implementation note for Frontend (pattern §2.4):** Implementer must read and apply field-level and layout values from `docs/design/search-field-definition-items.md`, `docs/design/search-fields-by-screen.md`, `docs/design/forms-and-filters.md`, and `docs/design/UX-REDESIGN-activity-log-statistics-search.md` when changing form/filter CSS or components; requirement §2 numeric values (e.g. 8–12px) are consistent with those docs but must be verified or sourced from the docs. For CSS, use `frontend/src/styles/search-filter-standard.css` for standard values (e.g. `var(--sf-*)`, `.sf-compact-panel`); for screen-specific exceptions, use component CSS only with a comment and add a row to `docs/design/css-standard-and-exceptions.md` §5 Exception index. **If any required standard for layout, field sizing, spacing, icon usage, label placement, or control semantics is not defined or is ambiguous in the design docs, the implementer must not infer or hardcode a solution. The implementer must first inform the user of the undefined standard items, explain why each is needed, propose a recommended standard draft, and request feedback so the standard can be explicitly defined before implementation proceeds.** See `docs/design/ux-frontend-standard-principles.md` §2 and §10, and `docs/workflow/ANALYSIS-implementation-phase-design-doc-usage.md`.

**Design docs (documentation only; implementer or Requirements may update):**

- **docs/design/search-fields-by-screen.md**: §2 (activity-log) and §3 (statistics) define fields; verify consistency with `docs/design/search-field-definition-items.md` and cross-screen rules (§4). Ensure panel width and compact variant are referenced.
- **docs/design/css-standard-and-exceptions.md**: §5 Exception index — when adding a screen-specific CSS override, add one row (Screen/component | Selector | Reason).

### Affected scopes and change targets (verification)

**Before finalizing §2**, the Requirements author must verify that every affected scope is covered per `docs/workflow/REQUIREMENTS-CHANGE-TARGET-CHECKLIST.md`.

| Scope | Affected? | §2 subsection and change file list complete? |
|-------|-----------|-----------------------------------------------|
| Backend | No | N/A |
| Frontend (config UI + view screen) | Yes (view only) | Yes |
| DB | No | N/A |
| Contract / Spec | No | N/A |
| Cursor tools (skills, specs) | No | N/A |

**Pattern §2.4 (Search/filter UI consistency)** applied: layout (group title, block structure), form panel width, spacing (compact variant), CSS standard and exceptions, and a11y are covered. Design doc references: `docs/design/forms-and-filters.md`, `docs/design/search-fields-by-screen.md`, `docs/design/search-field-definition-items.md`, `docs/design/css-standard-and-exceptions.md`, `docs/design/UX-REDESIGN-activity-log-statistics-search.md`, `frontend/src/styles/search-filter-standard.css`. Implementation note for Frontend is included above.

**Change target verification:** Completed per `docs/workflow/REQUIREMENTS-CHANGE-TARGET-CHECKLIST.md` (all scopes and pattern §2.4 checked before finalizing §2).

### Planned change file list (expected change targets)

**(Planned at authoring. Implementing agent (Step 4) confirms or amends this list when implementation is complete.)**

*Implementation complete (Frontend). Verified 2026-03-13: layout, form semantics, a11y, and compact spacing aligned with requirement. Files actually changed in final verification pass: StatisticsFilters.js (row2 = 기타 조건 + 검색/초기화 in same row), StatisticsFilters.css (actions inside row-2, margin-top adjusted), ActivityStatistics.css (statistics-action-controls use var(--sf-block-gap), var(--sf-container-padding)).*

Structure by scope to enable scope-specific excerpt extraction for handoff (see `HANDOFF-CHECKLIST.md`).

#### Frontend

- `frontend/src/components/UserActivityLog/UserActivityLogSearchForm.js`
  - Row1 = dates only; row2 = user block + 기타 조건 + 검색/초기화; remove “필터 접기” and collapsible state; ensure form and labels “검색”/“초기화”.
- `frontend/src/components/UserActivityLog/UserActivityLog.css`
  - Compact variant spacing via var(--sf-container-padding), var(--sf-row-gap), var(--sf-block-gap), var(--sf-control-*), var(--sf-btn-*); group titles above fields; max-width 1400px on container unchanged.
- `frontend/src/components/common/UserContextFilterBlock.js`
  - Verify no structural change required; confirm or add optional usernameMaxLength / userId max 8 for activity-log if product confirms.
- `frontend/src/components/common/UserContextFilterBlock.css`
  - Compact spacing aligned with standard: var(--sf-block-gap), var(--sf-row-gap), var(--sf-control-*), var(--sf-focus-*). Apply same user block field width on both screens (e.g. block min/max via var(--sf-field-user-block-min), var(--sf-field-user-block-max) or same grid column min/max) so activity log and statistics show identical field sizes.
- `frontend/src/components/StatisticsFilters.js`
  - Form wrapper with aria-label; “기타 조건” title above fields with role="group" and aria-labelledby; Search and Reset in filter actions row below body; Reset clears all filters.
- `frontend/src/components/StatisticsFilters.css`
  - Block-level “기타 조건” heading; compact spacing aligned with activity log and search-filter-standard.css; no inline heading for 기타 조건.
- `frontend/src/components/ActivityStatistics.css`
  - max-width 1400px unchanged; container padding set to var(--sf-container-padding) (12–16px).
- `frontend/src/components/ActivityStatistics.js`
  - Verify Search/Reset only in StatisticsFilters; .statistics-action-controls has only 그래프/표 and Excel.
- `frontend/src/components/StatisticsHeader.js`
  - Type toggle only (일별/월별). Date inputs moved to StatisticsFilters; aria-invalid/aria-describedby on date inputs in StatisticsFilters.
- `frontend/src/components/StatisticsHeader.css`
  - Unused date-selector styles removed (date inputs moved to StatisticsFilters).
- `frontend/src/styles/search-filter-standard.css`
  - No change unless design doc values are updated; components must reference var(--sf-*) only.

#### Design docs (documentation only)

- `docs/design/search-fields-by-screen.md`
  - §2, §3: confirm consistency with search-field-definition-items.md and panel width / compact variant refs.
- `docs/design/css-standard-and-exceptions.md`
  - §5 Exception index: add row when a new screen-specific CSS exception is introduced.

#### Backend

- None.

#### DB

- None.

---

## 3. Test approach

### Test case list (required)

| ID | Scope | Type | Scenario (input / condition) | Expected result | Verification (unit / integration / manual) |
|----|-------|------|------------------------------|-----------------|--------------------------------------------|
| TC-01 | Frontend | Normal | Open User Activity Log; scope ≠ self | Row1 = 시작/종료 날짜 only; Row2 = 사용자 block + 기타 조건 (title above fields) + 검색, 초기화; no “필터 접기”; filter body always visible | Manual / browser |
| TC-02 | Frontend | Normal | Open Activity Statistics; scope ≠ self | Filter panel: 로그 타입, 사용자 block, 기타 조건 (title above fields), 검색 and 초기화 in filter actions row below body; “그래프/표” and Excel in separate bar | Manual / browser |
| TC-03 | Frontend | Normal | Click 초기화 on Statistics | All filter fields clear; search runs with cleared values (or clear only per product) | Manual / browser |
| TC-04 | Frontend | Normal | Compare activity log and statistics on same viewport | Same page container width (max 1400px); filter panel width and compact spacing consistent | Manual / browser |
| TC-05 | Frontend | Normal | Check group titles “사용자”, “기타 조건”, “로그 타입” on both screens | Titles are above their fields, not inline; semantic group (role="group", aria-labelledby) where applicable | Manual / browser |
| TC-06 | Frontend | A11y | Tab through filter fields to 검색 and 초기화 on both screens | Search and 초기화 focusable; form has accessible name | Manual / browser |
| TC-07 | Frontend | A11y | Statistics: set 일별 start > end; focus date inputs | Error message associated; date inputs have aria-invalid and aria-describedby when invalid | Manual / browser |
| TC-08 | Frontend | Normal | scope=self user opens activity log | User block and 기타 조건 (액션 타입, IP) hidden | Manual / browser |
| TC-09 | Frontend | Normal | scope=self user opens statistics | User block and 기타 조건 (IP) hidden; only 로그 타입 and date in header visible in filter | Manual / browser |
| TC-10 | Frontend | Normal | Compare user block fields (부서, 사용자명, 사용자 ID) on activity log and statistics | Same min/max width and visual size; user block not squeezed (e.g. statistics row-1 gives user block sufficient width; block uses var(--sf-field-user-block-*) or same grid/field sizing) | Manual / browser |

### Test scenarios

#### Scenario 1: Activity log layout and buttons

1. Open User Activity Log (scope ≠ self).
2. Confirm row1 = 시작 날짜, 종료 날짜; row2 = 사용자 block + 기타 조건 (title above) + 검색, 초기화.
3. Confirm no “필터 접기”.
4. Verification: Matches design standard and UX-REDESIGN.

#### Scenario 2: Statistics layout and buttons

1. Open Activity Statistics (scope ≠ self).
2. Confirm filter form with 로그 타입, 사용자, 기타 조건 (title above); 검색 and 초기화 in filter actions row; 그래프/표 and Excel in separate bar.
3. Click 초기화; confirm fields clear.
4. Verification: Matches design standard and UX-REDESIGN.

#### Scenario 3: Panel width and spacing (both screens)

1. Open activity log and note container width and filter padding/gaps.
2. Open statistics and note container width and filter padding/gaps.
3. Verification: Same max-width (1400px); spacing within 8–12px / 12–16px per compact variant.

#### Scenario 5: User block field size (TC-10)

1. Open User Activity Log (scope ≠ self) and note the width of 부서, 사용자명, 사용자 ID inputs in the “사용자” block.
2. Open Activity Statistics (scope ≠ self) and note the width of the same three fields in the “사용자” block.
3. Verification: Same min/max width and visual size; statistics user block is not narrower due to sharing a single column with 로그 타입.

#### Scenario 4: Accessibility

1. Tab through filter fields → 검색 → 초기화 on both screens.
2. Statistics: set start > end for 일별; check error association and aria attributes.
3. Verification: Focus order and a11y per design docs.

### Test data

- User with scope ≠ self (full filters) and user with scope=self (user/기타 blocks hidden). No special data for layout/width checks.

### Test environment

- Frontend: `http://localhost:3001` (or per contract)
- Backend: `http://localhost:9200`
- Database: per project setup

### 3.5 Browser automation verification (optional)

**Applicable TCs**: TC-01 through TC-09 (manual / browser).

**Procedure**: Navigate to activity log and statistics routes → login if needed → assert structure (row layout, group titles, buttons, visibility); compare container width; for TC-08/TC-09 use scope=self user.

**Reference**: `docs/workflow/BROWSER-AUTOMATION-VERIFICATION-POLICY.md`.

---

**Author**: Requirements subagent  
**Date**: 2026-03-13  
**Status**: Ready for implementation
