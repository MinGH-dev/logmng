# QA feedback to UX — Activity Log & Statistics search/filter UI

**Requirement**: `docs/requirements/20260310-search-screens-qa-ux-redesign-handoff.md`  
**Deliverable**: §2.1 “QA feedback to UX” (TC-01)  
**Date**: 2026-03-10  
**Design reference**: `docs/design/forms-and-filters.md`, `docs/analysis-search-consistency-by-screen.md`, `docs/requirements/20260310-search-ui-unify.md`

---

## 1. User Activity Log (사용자 활동 이력)

### 1.1 Current state

- **Layout**: Single form (`.activity-log-search-form`) with collapsible filter body. Structure: header with “필터 접기/펼치기” toggle → filter body (when expanded) → action buttons at bottom.
- **Field order** (when expanded):
  1. **Date range**: 시작 날짜, 종료 날짜 (`datetime-local`) in one row.
  2. **User-context block** (via `UserContextFilterBlock`): 부서 → 사용자명 → 사용자 ID (single row, group label “사용자”). Hidden when `hideUserFilters === true` (scope=self).
  3. **Screen-specific block** (“기타 조건”): 액션 타입 (select), IP 주소 (text). IP only when `!hideUserFilters`.
- **Grouping**: Date row → user-context block (fieldset with legend “사용자”) → screen-specific block with heading “기타 조건”. Clear separation between user block and “기타 조건”.
- **Buttons**: “검색” (primary submit), “초기화” (secondary reset). Both at bottom of form (`.search-form-actions`). Submit triggers validation (date range start ≤ end); reset clears all fields and re-runs search with default dates.
- **User-context block**: Implemented by `UserContextFilterBlock.js` with order **부서 → 사용자명 → 사용자 ID** and label “사용자”. Same component used for activity log and statistics.

**Related files**: `UserActivityLogSearchForm.js`, `UserActivityLogList.js`, `UserActivityLog.css`, `UserContextFilterBlock.js` / `UserContextFilterBlock.css`. Form styles live in `UserActivityLog.css` (not `SearchForm.css` for this screen).

### 1.2 Checklist vs design standards

| Criterion | Result | Note |
|----------|--------|------|
| User-context order 부서 → 이름 → 사용자 ID | **Pass** | `UserContextFilterBlock` renders 부서 → 사용자명 → 사용자 ID. |
| User-context grouping (one block, group label) | **Pass** | Single block with legend “사용자”; screen-specific in separate “기타 조건” block. |
| Explicit Search and Reset buttons | **Pass** | “검색” and “초기화” present; both wired. |
| Compact variant (row/block gap, padding, control height) | **Pass** | Padding 12px 16px; row gap 10px; block margin 14px; controls 34px; per `UserActivityLog.css` and `UserContextFilterBlock.css`. |
| Collapsible filter with aria-expanded/aria-controls | **Pass** | Toggle has `aria-expanded`, `aria-controls={FILTERS_BODY_ID}`, body has `id` and `hidden`. |
| Validation / error display (date range) | **Pass** | Date range error shown; `aria-invalid` and `aria-describedby` on inputs. |
| scope=self → hide user-context block | **Pass** | `hideUserFilters` passed from container; `UserContextFilterBlock` returns null when true. |

### 1.3 Issues and improvement needs

1. **Label consistency**: “초기화” is used for Reset. Design doc mentions “Reset” or “Clear”; if the product standard is Korean, consider aligning with other screens (Statistics has no Reset—see cross-screen).
2. **Minor**: “기타 조건” groups 액션 타입 and IP; design does not require a different label, but UX may want to align with Statistics (“기타 조건” is used there too).
3. **Screenshot recommended**: Full activity-log search form (expanded) with scope ≠ self, to confirm visual hierarchy and spacing for UX redesign.

### 1.4 Testability notes

- **Easy to verify**: Field order (DOM order of labels/inputs), presence of 검색/초기화, collapse/expand, scope=self hiding (switch user or scope and reload).
- **Manual/browser**: Date validation (start > end) shows error and blocks submit; reset clears fields and re-submits.
- **Automation**: Stable IDs on form and inputs (`startDate`, `endDate`, `activity-log-search-*` for user-context via `idPrefix`); filter body `id="activity-log-search-filters-body"` for aria-controls. No blocker for QA.

---

## 2. Activity Statistics (활동로그 통계)

### 2.1 Current state

- **Layout**: Filter area is a **div** (`.statistics-filters`), not a `<form>`. Order: header (“검색 조건” + “필터 접기/펼치기”) → body (when expanded): (1) 로그 타입 block, (2) `UserContextFilterBlock` (“사용자”: 부서 → 사용자명 → 사용자 ID), (3) “기타 조건” (IP select when `!hideUserFilters`). **Search/Apply and Reset are not inside the filter component**: they live in the parent `ActivityStatistics.js` in `.statistics-action-controls` (same row as “그래프/표” toggle and “Excel 다운로드”). So the “조회” button is the effective “Search” for the filters; there is **no Reset/Clear** for the filter values.
- **Field order**: 로그 타입 → user-context block (부서 → 사용자명 → 사용자 ID) → 기타 조건 (IP). User-context order matches design.
- **Grouping**: 로그 타입 in its own block; user-context in one block; IP under “기타 조건”. Date range is **outside** `StatisticsFilters` (in `StatisticsHeader`: 일별 = 시작일/종료일, 월별 = 연도/월).
- **Buttons**: “조회” (search) and “Excel 다운로드” (conditional) in `statistics-action-controls`; **no Reset/초기화** for filters.
- **User-context block**: Same `UserContextFilterBlock`; order and label correct.

**Related files**: `StatisticsFilters.js`, `ActivityStatistics.js`, `StatisticsHeader.js`, `StatisticsFilters.css`, `ActivityStatistics.css`, `UserContextFilterBlock.js` / `UserContextFilterBlock.css`.

### 2.2 Checklist vs design standards

| Criterion | Result | Note |
|----------|--------|------|
| User-context order 부서 → 이름 → 사용자 ID | **Pass** | Same `UserContextFilterBlock` as activity log. |
| User-context grouping (one block, group label) | **Pass** | One block “사용자”; 로그 타입 and IP in separate blocks. |
| Explicit Search and Reset buttons | **Fail / Gap** | “조회” acts as Search, but there is **no Reset** for filter values. Design: “Provide explicit 'Search' / 'Apply' and 'Reset' (or 'Clear') buttons.” |
| Compact variant (row/block gap, padding, control height) | **Pass** | Padding 12px 16px; gap 10px; controls 34px in `StatisticsFilters.css` and `UserContextFilterBlock.css`. |
| Collapsible filter with aria-expanded/aria-controls | **Pass** | Toggle and body `id` present; `hidden` on body. |
| scope=self → hide user-context block | **Pass** | `hideUserFilters` passed; user block and “기타 조건” (IP) hidden when scope=self. |

### 2.3 Issues and improvement needs

1. **Missing Reset**: Statistics has no “초기화” or “Reset” for the filter set (로그 타입, 부서, 사용자명, 사용자 ID, IP). Users cannot one-click clear filters to a default state. **Recommendation**: Add a Reset button next to “조회” that clears filter fields and optionally re-runs with defaults (or only clear and let user press “조회”).
2. **Button placement**: Search (“조회”) is in a separate bar (`statistics-action-controls`) below the filter panel, alongside view toggle and Excel. Design standard implies Submit/Reset next to the filter block; UX may recommend moving “조회” and new “초기화” into or directly under the filter area for consistency with Activity Log.
3. **Semantic form**: Filter area is a div, not a form. For accessibility and keyboard submit, consider wrapping filter controls in a `<form>` and using a submit button for “조회”.
4. **Screenshot recommended**: Statistics filter panel (expanded) + action bar (조회, Excel, 그래프/표) with scope ≠ self, to show current separation and support placement decisions for Reset and optional form semantics.

### 2.4 Testability notes

- **Easy to verify**: User-context order and grouping (same as activity log); collapse/expand; scope=self hiding (user-context and IP hidden).
- **Gap**: No Reset to clear—manual test would be “change each filter then manually clear” (no single action). Automation can still set values and click “조회”.
- **Date range**: Lives in `StatisticsHeader`; validation (start ≤ end for 일별) and error display are in parent; `dateRangeInvalid` and `dateRangeErrorId` support a11y. No blocker for QA once Reset is defined (e.g. “초기화” clears filters only vs. clears and re-runs).

---

## 3. Cross-screen

### 3.1 Inconsistencies

| Aspect | User Activity Log | Activity Statistics |
|--------|-------------------|----------------------|
| **Search button label** | “검색” | “조회” |
| **Reset button** | Present (“초기화”) | **Missing** |
| **Button placement** | Inside form, below filter body (`.search-form-actions`) | In separate bar below filters (`.statistics-action-controls`) with view toggle and Excel |
| **Filter container** | `<form>` with submit/reset | `<div>`; no form wrapper |
| **User-context block** | Same component and order (부서 → 사용자명 → 사용자 ID) | Same |
| **Screen-specific block label** | “기타 조건” | “기타 조건” |
| **Date range** | Inside search form (시작/종료 datetime-local) | Outside filters (StatisticsHeader: 일별 시작/종료 or 월별 연/월) |

### 3.2 Summary for UX

- **Unified** across both: user-context order and grouping (부서 → 이름 → 사용자 ID, one block “사용자”), compact variant, collapsible panel, scope=self hiding.
- **To align**: (1) Add Reset on Statistics and optionally unify label “검색” vs “조회”; (2) Consider consistent placement of Search/Reset relative to the filter block (e.g. both screens: buttons directly under filter body); (3) Consider wrapping Statistics filters in a form and using one primary (Search) and one secondary (Reset) per `docs/design/forms-and-filters.md` and `buttons.md`.

Reference: `docs/analysis-search-consistency-by-screen.md` (axes, scope=self), `docs/requirements/20260310-search-ui-unify.md` (unified concept).

---

## 4. Optional — screenshot suggestions

- **Screenshot recommended**: User Activity Log — search form expanded, scope ≠ self, showing date row, 사용자 block, 기타 조건, and 검색/초기화 buttons.
- **Screenshot recommended**: Activity Statistics — filter panel expanded + action bar (조회, Excel, 그래프/표), scope ≠ self, to illustrate missing Reset and placement of actions.

*(QA did not capture screenshots; UX may add them when producing the redesign.)*

---

**End of QA feedback to UX.** Hand off this deliverable to UX for the redesign per §2.2 of the requirement doc.
