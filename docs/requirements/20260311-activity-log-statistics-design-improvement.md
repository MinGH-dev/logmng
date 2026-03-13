# 20260311 — Activity Log and Statistics design improvement

## 0. Scope and supersedes

- **Scope**: This document is the single requirement for aligning the **User Activity Log** (사용자 활동 이력) and **Activity Statistics** (활동로그 통계) screens with the project's design standards (layout, filter group title, panel width, spacing, user block field size, form semantics, and compact variant).
- **Supersedes**: None. It does not supersede feature-improvement or scope docs (e.g. `docs/requirements/20260206-activity-log-statistics-improvement.md` if present); it focuses solely on design-standards alignment.

---

## 1. User requirement

### Requirement description

Users who use both the **User Activity Log** and **Activity Statistics** screens expect a consistent search/filter experience: same visual layout, group titles above fields, same panel width, compact spacing, and **same size for the user block fields** (부서, 사용자명, 사용자 ID) on both screens. Currently, layout, group title placement, panel width, spacing, or user block field width may diverge from the design standards or between the two screens, causing confusion and perceived inconsistency.

### User scenario

1. User opens the **User Activity Log** screen and sees the filter area (date range, user block, 기타 조건, Search/Reset).
2. User opens the **Activity Statistics** screen and sees the filter area (date/period, 로그 타입, user block, 기타 조건, Search/Reset).
3. **Problem**: Layout, group title placement, panel width, spacing, or **user block field size** (부서, 사용자명, 사용자 ID) may differ between the two screens or from the design standards defined in `docs/design/forms-and-filters.md`, `docs/design/search-fields-by-screen.md`, and related design docs.
4. **Expectation**: Both screens match the design standards: filter group title above fields, same panel width, compact variant spacing, **same user block field size on both screens**, Search/Reset in the filter actions row, proper form semantics, activity log row1 = dates / row2 = rest, single row for non-date blocks, and scope=self behaviour unchanged.

### Expected outcome

- **Filter group title**: Group titles (e.g. "사용자", "기타 조건") are placed **above** their fields, not inline. Per `docs/design/forms-and-filters.md` § Filter group title placement.
- **Same panel width**: Search/filter panel uses the same width constraints (e.g. page container or same max-width) on both activity-log and statistics. Per `docs/design/forms-and-filters.md` § Search form panel width and `docs/design/search-fields-by-screen.md` §3 (통계 패널 너비).
- **Compact variant spacing**: Row/field gap 8–12px, block-to-block gap 12–16px, container padding 12–16px. Per `docs/design/forms-and-filters.md` § Compact variant.
- **User block field size (동일 크기)**: The user block fields — **부서**, **사용자명**, **사용자 ID** — have the **same width/size** on both the activity-log and statistics screens. Per `docs/design/search-fields-by-screen.md` §3 ("활동 이력과 동일"), §4 (화면 간 공통 규칙) and `docs/design/search-field-definition-items.md` §4, §4.5 (Width by max character count). Layout must not squeeze the user block (e.g. avoid sharing a single `1fr` cell with another control).
- **Search/Reset in filter actions row**: Primary ("검색") and secondary ("초기화") buttons appear in a dedicated filter actions row directly under the filter body on both screens. Per `docs/design/forms-and-filters.md` and `docs/design/UX-REDESIGN-activity-log-statistics-search.md`.
- **Form semantics**: Filter area is a `<form>` with one primary (Search) and one secondary (Reset); submit and reset behave correctly. Per `docs/design/forms-and-filters.md` and `docs/design/UX-REDESIGN-activity-log-statistics-search.md`.
- **Activity log layout**: Row1 = date fields only; row2 = user block, 기타 조건, and filter actions (single row for non-date blocks). Per `docs/design/forms-and-filters.md` § Single row for non-date.
- **scope=self behaviour**: When scope=self, user block and related screen-specific fields remain hidden; no change to this behaviour.
- **Optional field constraints**: Apply optional field constraints (e.g. maxLength, placeholders) only as defined in design docs or if product confirms; do not infer beyond design docs.

**Design doc references**: Implementers must read and apply from `docs/design/forms-and-filters.md`, `docs/design/search-fields-by-screen.md`, `docs/design/search-field-definition-items.md`, `docs/design/css-standard-and-exceptions.md`, `frontend/src/styles/search-filter-standard.css`, and `docs/design/UX-REDESIGN-activity-log-statistics-search.md`.

---

## 2. Design

### 2.1 Security review (optional)

Not applicable — this requirement does not change PII, decryption scope, or access control. scope=self behaviour is unchanged.

### Technical design

#### Codebase summary

- **User Activity Log**: `UserActivityLogSearchForm.js` (form with date row, user block via `UserContextFilterBlock`, 기타 조건, Search/Reset); `UserActivityLog.css` uses `search-filter-standard.css` vars, row1 = dates, row2 = user block + 기타 조건 + actions. Container `activity-log-list-container` max-width 1400px.
- **Activity Statistics**: `ActivityStatistics.js` (container); `StatisticsHeader.js` (date/period); `StatisticsFilters.js` (form with date/period block, 로그 타입, `UserContextFilterBlock`, 기타 조건, Search/Reset). `StatisticsFilters.css` uses `.statistics-filters__row-1` with `grid-template-columns: minmax(var(--sf-field-date-min), 180px) 1fr` — the second column `1fr` holds both 로그 타입 and `UserContextFilterBlock`, which can squeeze the user block.
- **UserContextFilterBlock**: Shared component (`UserContextFilterBlock.js`, `UserContextFilterBlock.css`) used on both screens; order 부서 → 사용자명 → 사용자 ID. CSS uses `grid-template-columns: repeat(3, minmax(100px, 1fr))` (compact) but does **not** apply block-level width vars `--sf-field-user-block-min` / `--sf-field-user-block-max` from `search-filter-standard.css`.
- **Shared standard**: `frontend/src/styles/search-filter-standard.css` defines `--sf-field-user-block-min`, `--sf-field-user-block-max`, `--sf-field-date-block-*`, compact variant vars, and `.sf-compact-panel` for single-application-point control sizing.

#### Problem analysis

1. **User block field size**: On statistics, the user block shares a single `1fr` cell with 로그 타입 in `.statistics-filters__row-1`, so the user block gets less width than on activity log, where row2 gives the user block more space. UserContextFilterBlock does not use `var(--sf-field-user-block-*)` for block-level width, so the same block can render with different effective field sizes on the two screens.
2. **Layout**: Statistics row-1 layout can squeeze the user block; activity log row1/row2 split (dates vs rest) is correct; statistics should ensure the user block has sufficient width (e.g. dedicated column or min-width) so field sizes match activity log.
3. **Panel width / spacing**: Both screens use compact variant and similar container max-width; alignment of panel width and spacing to the same design values must be verified and any gaps closed.
4. **Form semantics and buttons**: Statistics already uses `<form>` and Search/Reset in a filter actions row per UX-REDESIGN; activity log already has form and actions. Confirm both follow the same pattern and labels ("검색", "초기화").

#### Solution approach

**Frontend (only):** Design standards are enforced via CSS and layout; no backend or API change is required unless a design doc explicitly implies one.

- **User block field size**: Apply the **same user block field width** on both screens: use `var(--sf-field-user-block-min)`, `var(--sf-field-user-block-max)` (or the same min/max from `docs/design/search-fields-by-screen.md` and `search-field-definition-items.md` §4.5) so that 부서, 사용자명, 사용자 ID have the same min/max width and visual size on activity-log and statistics. Ensure the **layout does not squeeze the user block**: on statistics, avoid placing the user block and another control (e.g. 로그 타입) in a single `1fr` cell; give the user block its own column or sufficient min-width so it receives at least the same effective width as on activity log.
- **UserContextFilterBlock**: Use block-level width (e.g. `var(--sf-field-user-block-max)`) or the same grid/field sizing on both screens so the block does not stretch to different widths. Component CSS may only add layout and width-by-role; control height/padding come from the standard wrapper (`.sf-compact-panel`) per `docs/design/css-standard-and-exceptions.md` §3.1.
- **StatisticsFilters layout**: Adjust row-1 (or equivalent) so the user block is not in the same cell as 로그 타입; e.g. separate columns for date/period block, 로그 타입, and user block, or ensure user block has min-width consistent with `--sf-field-user-block-min` so field sizes match activity log.
- **Panel width and spacing**: Verify both screens use the same container max-width (e.g. 1400px) and compact variant vars from `search-filter-standard.css`; fix any deviation.
- **Form and buttons**: Confirm both screens use `<form>`, one primary (검색) and one secondary (초기화) in the filter actions row; no change if already correct.
- **Design doc references**: All numeric and structural values (width, spacing, row layout) must be **sourced from** `docs/design/forms-and-filters.md`, `docs/design/search-fields-by-screen.md`, `docs/design/search-field-definition-items.md`, `docs/design/css-standard-and-exceptions.md`, and `frontend/src/styles/search-filter-standard.css`. For activity-log and statistics search UX, also apply `docs/design/UX-REDESIGN-activity-log-statistics-search.md`.

**Implementation note for Frontend (verbatim from REQUIREMENTS-CHANGE-TARGET-CHECKLIST.md §2.4):**

Implementer must read and apply field-level and layout values from **docs/design/search-field-definition-items.md**, **docs/design/search-fields-by-screen.md**, and **docs/design/forms-and-filters.md** when changing form/filter CSS or components; apply layout and structural rules from forms-and-filters.md (e.g. § Single row for non-date, § Form per mode, § Width by role). Requirement §2 numeric values (e.g. 8–12px) are consistent with those docs but must be verified or sourced from the docs. **If any required standard for layout, field sizing, spacing, icon usage, label placement, or control semantics is not defined or is ambiguous in the design docs, the implementer must not infer or hardcode a solution. The implementer must first inform the user of the undefined standard items, explain why each is needed, propose a recommended standard draft, and request feedback so the standard can be explicitly defined before implementation proceeds.**

**Backend:** No change unless a design doc explicitly requires an API or behavioural change.

**DB:** No change.

### Affected scopes and change targets (verification)

| Scope | Affected? | §2 subsection and change file list complete? |
|-------|-----------|---------------------------------------------|
| Backend | No | N/A |
| Frontend (view screens) | Yes | Yes |
| DB | No | N/A |
| Contract / Spec | No | N/A |
| Cursor tools (skills, specs) | No | N/A |

### Planned change file list (expected change targets)

#### Frontend

- `frontend/src/components/UserActivityLog/UserActivityLog.css`
  - Verify layout (row1 = dates, row2 = rest), compact variant and panel width; ensure user block uses same width-by-role as statistics (e.g. `var(--sf-field-user-block-*)` or design doc values). Must not squeeze user block.
- `frontend/src/components/UserActivityLog/UserActivityLogSearchForm.js`
  - Confirm form semantics, Search/Reset labels and placement; no structural change if already correct.
- `frontend/src/components/StatisticsFilters.js`
  - Confirm form semantics, Search/Reset in filter actions row; ensure date/period block, 로그 타입, and user block are structured so user block has sufficient width (not sharing a single 1fr with 로그 타입).
- `frontend/src/components/StatisticsFilters.css`
  - Align layout so user block has its own column or sufficient min-width (e.g. use `var(--sf-field-user-block-min)`, `var(--sf-field-user-block-max)`); avoid user block and 로그 타입 sharing one `1fr` cell. Apply same panel width and compact variant as activity log.
- `frontend/src/components/common/UserContextFilterBlock.css`
  - Apply same user block field width on both screens: use block-level width (e.g. `var(--sf-field-user-block-min)`, `var(--sf-field-user-block-max)`) or same grid/field sizing so 부서, 사용자명, 사용자 ID have the same min/max width on activity-log and statistics. Do not re-declare control height/padding (from .sf-compact-panel); only layout and width by role.
- `frontend/src/components/common/UserContextFilterBlock.js`
  - No change unless markup or structure is needed for width/layout; confirm ids and a11y.
- `frontend/src/components/ActivityStatistics.js` / `StatisticsHeader.js`
  - Verify filter root has `.sf-compact-panel` so control sizing is from standard; confirm date/period block and panel width.
- `frontend/src/styles/search-filter-standard.css`
  - Only if design docs require a new or updated variable; otherwise use existing `--sf-field-user-block-*` and compact variant vars.

### Actual change file list (implementing agent)

| File | Change |
|------|--------|
| `frontend/src/components/common/UserContextFilterBlock.css` | Block-level min/max width `var(--sf-field-user-block-min/max)`; compact row 3 equal columns; removed control min-width/max-width override; layout and width-by-role only. |
| `frontend/src/components/StatisticsFilters.css` | Row-1 grid: separate columns for 로그 타입 (140px–180px) and user block `minmax(var(--sf-field-user-block-min), var(--sf-field-user-block-max))` so user block not squeezed. |
| `frontend/src/components/UserActivityLog/UserActivityLog.css` | User block in row-2: min-width/max-width `var(--sf-field-user-block-min/max)` so same width-by-role as statistics. |
| `frontend/src/components/StatisticsFilters.js` | Added `usernameMaxLength={5}` to UserContextFilterBlock for consistency with activity log (search-field-definition-items §4.5). |
| `frontend/src/components/UserActivityLog/UserActivityLogSearchForm.js` | No change (form semantics, Search/Reset already correct). |
| `frontend/src/components/common/UserContextFilterBlock.js` | No change (ids and a11y confirmed). |
| `frontend/src/components/ActivityStatistics.js`, `StatisticsHeader.js` | No change (filter root has .sf-compact-panel; container max-width 1400px verified). |
| `frontend/src/styles/search-filter-standard.css` | No change (existing --sf-field-user-block-* used). |

---

## 3. Test approach

### Test case list (required)

| ID | Scope | Type | Scenario (input / condition) | Expected result | Verification (unit / integration / manual) |
|----|-------|------|------------------------------|----------------|--------------------------------------------|
| TC-01 | Frontend | Normal | Open User Activity Log; note filter layout: row1 = dates, row2 = user block + 기타 조건 + Search/Reset. | Group titles above fields; same panel width as design; compact spacing; Search and Reset in filter actions row. | Manual / browser |
| TC-02 | Frontend | Normal | Open Activity Statistics; note filter layout: date/period block, 로그 타입, user block, 기타 조건, Search/Reset. | Group titles above fields; same panel width as activity log; compact spacing; Search and Reset in filter actions row; user block not squeezed. | Manual / browser |
| TC-03 | Frontend | Normal | **User block field size**: On Activity Log, measure or capture width of 부서, 사용자명, 사용자 ID fields (or their container). On Statistics, measure the same. | Same min/max width and visual size for 부서, 사용자명, 사용자 ID on both screens. Per `docs/design/search-fields-by-screen.md` §3, §4 and search-field-definition-items.md §4, §4.5. | Manual / browser |
| TC-04 | Frontend | Normal | scope=self: Open Activity Log and Statistics with scope=self. | User block and related screen-specific fields are hidden on both; no regression. | Manual / browser |
| TC-05 | Frontend | Normal | Filter area on both screens is a `<form>`; Submit (검색) and Reset (초기화) work. | Form landmark present; submit runs search; reset clears fields (and optionally re-runs per product). | Manual / browser |
| TC-06 | Frontend | a11y | Keyboard and screen reader: focus order, group labels, button labels. | Form has accessible name; group titles associated; buttons have visible or aria-label text; focus order logical. | Manual / browser |

### Test scenarios

#### Scenario 1: Layout and user block size

1. Open User Activity Log; expand filter; confirm row1 = dates only, row2 = user block + 기타 조건 + actions.
2. Open Activity Statistics; confirm date/period block, then 로그 타입, user block, 기타 조건, and actions row.
3. Compare width of 부서, 사용자명, 사용자 ID between the two screens (visual or dev tools).
4. **Verification**: Same layout rules and same user block field size on both screens.

#### Scenario 2: Form and buttons

1. On both screens, confirm filter is a `<form>` and Search ("검색") and Reset ("초기화") are in the filter actions row.
2. Submit and reset on each screen; confirm behaviour.
3. **Verification**: Same pattern and semantics on both screens.

### Test data

- User with scope=all (or team) so user block and 기타 조건 are visible on both screens.
- User with scope=self to verify hiding (TC-04).

### Test environment

- Frontend: `http://localhost:3001` (or per contract)
- Backend: `http://localhost:9200`

### 3.5 Browser automation verification (optional)

- **Applicable TCs**: TC-01, TC-02, TC-03, TC-04, TC-05, TC-06.
- **Procedure**: Navigate to activity-log and statistics; use browser snapshot to inspect filter structure and (where supported) computed width of user block fields; compare between screens. Verify form landmark and buttons.
- **Reference**: `docs/workflow/BROWSER-AUTOMATION-VERIFICATION-POLICY.md`.

---

## 4. Checklist

### Frontend verification

- [ ] Layout (row1 = dates, row2 = rest on activity log; statistics user block not squeezed) verified
- [ ] User block field size (부서, 사용자명, 사용자 ID) same on both screens
- [ ] Panel width and compact variant spacing aligned with design docs
- [ ] Form semantics and Search/Reset labels and placement correct
- [ ] scope=self behaviour unchanged

### Backend verification

- [ ] N/A (no backend change)

### Integration

- [ ] Both screens tested with scope=all and scope=self

### Documentation

- [ ] Requirement doc completed; design doc references in §1 and §2

---

## 5. Test results

### Test run date

- [Date and time]

### Test results

#### Frontend

[Pass / Fail]

- [Result description]

**Commands:**

[One executable or manual step per TC where applicable]

**Outcome:**

- [Item 1]
- [Item 2]

### Issues found and resolution

[To be filled when tests run]

### Next steps

1. Implement Frontend changes per §2 and planned change file list.
2. Run tests and verification; update §5.
3. QA sign-off and commit per workflow.

---

## 6. Error remedy result (cause and action) — for error/bug fix requirements only

N/A (design improvement requirement).

---

**Author**: Requirements subagent  
**Date**: 2026-03-11  
**Status**: In progress
