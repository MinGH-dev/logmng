# UX redesign — Activity Log & Statistics search/filter UI

**Requirement**: `docs/requirements/20260310-search-screens-qa-ux-redesign-handoff.md` §2.2 (TC-02)  
**Input**: `docs/workflow/QA-FEEDBACK-TO-UX-activity-log-statistics-search.md`  
**Design standards**: `docs/design/forms-and-filters.md`, `docs/design/buttons.md`, `docs/requirements/20260310-search-ui-unify.md`, `docs/analysis-search-consistency-by-screen.md`  
**Date**: 2026-03-10  
**Purpose**: Single source of truth for Frontend implementation; no code—component/block-level only.

---

## 1. Design recommendations

### 1.1 User Activity Log (사용자 활동 이력)

**Reference**: `docs/design/forms-and-filters.md` (filter groups, submit/reset, user-context order, compact variant), `docs/design/buttons.md` (primary/secondary placement).

- **Current state (QA)**: Form with correct user-context order (부서 → 사용자명 → 사용자 ID), Search (“검색”) and Reset (“초기화”) present and wired; collapsible filter with a11y; scope=self hides user block. Aligns with design standards.
- **Recommendations**:
  1. **No structural change** to the filter area or button placement; keep Search and Reset in `.search-form-actions` below the filter body per existing pattern.
  2. **Label standard**: Treat “검색” and “초기화” as the **canonical labels** for Search and Reset on search/filter UIs in this product. Use the same labels on Activity Statistics for cross-screen consistency (see §1.3).
  3. **Optional**: If the product later adopts “Reset”/“Clear” in design docs, align terminology in `forms-and-filters.md`; for this redesign, keep “초기화” as the Reset label for both screens.

### 1.2 Activity Statistics (활동로그 통계)

**Reference**: `docs/design/forms-and-filters.md` (explicit Search and Reset, filter groups, form structure), `docs/design/buttons.md` (primary Submit, secondary Reset next to primary).

- **Problems (QA)**: No Reset button; “조회” is in a separate action bar (not next to filters); filter area is a `div` not a `<form>`.
- **Recommendations**:
  1. **Add Reset**: Add an explicit **Reset** button (“초기화”) that clears all filter values (로그 타입, 부서, 사용자명, 사용자 ID, IP). Behavior: clear fields to default/empty; optionally re-run query with cleared values (product decision). One primary (“검색” or “조회”—see §1.3) and one secondary (“초기화”) per `forms-and-filters.md` and `buttons.md`.
  2. **Button placement**: Place **Search** and **Reset** in the same relative position pattern as Activity Log: **directly under the filter body** (inside or immediately below the filter block), not in the same row as “그래프/표” and “Excel 다운로드”. Recommended: a dedicated actions row (e.g. “filter actions”) below the expanded filter content, containing only “검색” (primary) and “초기화” (secondary). The view toggle (그래프/표) and “Excel 다운로드” remain in the existing `.statistics-action-controls` bar; that bar is for view/export, not for filter submit/reset.
  3. **Form semantics**: Wrap the **filter controls** (로그 타입, user-context block, 기타 조건) in a **`<form>`** so that:
     - Submit (Search) is a proper `type="submit"` (or equivalent) and Reset is `type="reset"` or a button that programmatically clears and optionally submits.
     - Keyboard submit (Enter in a field) and accessibility (form landmark, labels) follow `forms-and-filters.md` and `text-input.md`/`buttons.md`.
  4. **User-context order and grouping**: Keep current order (부서 → 사용자명 → 사용자 ID) and grouping (“사용자” block); no change. Date range remains in StatisticsHeader (일별/월별); no structural change to date placement.
  5. **Compact variant**: Retain current compact variant (padding, gap, control height) per `StatisticsFilters.css` and `UserContextFilterBlock.css`; no change.

### 1.3 Cross-screen alignment

**Reference**: `docs/design/forms-and-filters.md`, `docs/requirements/20260310-search-ui-unify.md` (unified concept: same labels, same pattern).

- **Button labels**: Use **“검색”** for the primary Search/Apply action and **“초기화”** for Reset on **both** User Activity Log and Activity Statistics. Activity Statistics currently uses “조회”; change to **“검색”** so both screens share the same label and users get a consistent mental model. If product prefers to keep “조회” on Statistics for domain nuance, document that exception in the design doc and keep “검색” on Activity Log; the redesign recommends **unifying to “검색” and “초기화”** on both.
- **Button placement pattern**: Both screens: **Search and Reset appear in a single row directly under the filter body** (Activity Log already does this; Statistics to be updated so filter submit/reset are not in the view/export bar).
- **Form semantics**: Both screens: filter area is a **`<form>`** with one primary (Search) and one secondary (Reset). Activity Log already is a form; Statistics to be wrapped in a form.
- **User-context block**: Already aligned (same `UserContextFilterBlock`, order 부서 → 사용자명 → 사용자 ID, group label “사용자”); no change.

---

## 2. Acceptance criteria per screen

### 2.1 User Activity Log

- User-context block is visible when scope ≠ self; order is **부서 → 사용자명 → 사용자 ID**; group label “사용자”.
- **Search** (“검색”) and **Reset** (“초기화”) buttons are present and wired; both in the same actions row below the filter body.
- Filter area is a `<form>`; Submit and Reset behave correctly (validation on submit, reset clears fields and re-runs or clears only per spec).
- scope=self: user-context block and related screen-specific fields (e.g. IP) are hidden.
- Collapsible filter has correct `aria-expanded` and `aria-controls`; compact variant (padding, gap) per `forms-and-filters.md`.

### 2.2 Activity Statistics

- **Reset** button (“초기화”) is present next to **Search** (“검색” or “조회” per product decision; redesign recommends “검색”).
- Search and Reset are in the **same relative position pattern** as Activity Log: **directly under the filter body** (filter actions row), not in the same row as “그래프/표” and “Excel 다운로드”.
- Filter controls (로그 타입, user-context block, 기타 조건) are wrapped in a **`<form>`**; Search is submit, Reset clears filter values (and optionally re-runs).
- User-context order **부서 → 사용자명 → 사용자 ID** and group label “사용자”; same as Activity Log.
- scope=self: user-context block and “기타 조건” (IP) are hidden.
- Compact variant and collapsible behavior unchanged; a11y (toggle `aria-expanded`/`aria-controls`) preserved.

### 2.3 Cross-screen

- **Both screens**: Search and Reset use the same label pair: **“검색”** and **“초기화”** (unless product explicitly keeps “조회” for Statistics and documents it).
- **Both screens**: Search and Reset are in the same relative position pattern (actions row directly under the filter body).
- **Both screens**: Filter area is a `<form>` with one primary and one secondary button per `forms-and-filters.md` and `buttons.md`.

---

## 3. Component/block-level changes

### 3.1 User Activity Log

- **UserActivityLogSearchForm**: Confirm labels are “검색” (primary) and “초기화” (secondary). No structural change to form or button placement.
- **UserContextFilterBlock**: No change (order and grouping already correct).

### 3.2 Activity Statistics

- **StatisticsFilters** (or equivalent filter block):
  - **Wrap** the filter content (로그 타입 block, UserContextFilterBlock, 기타 조건) in a **`<form>`** element. Ensure the form has an accessible name (e.g. `aria-label` or visually hidden title) such as “검색 조건” or “통계 검색 조건”.
  - **Add** a **Reset** button (“초기화”), secondary style, next to the Search button.
  - **Move** the Search (“조회” → “검색” per recommendation) and the new Reset into a **filter actions row** that sits **inside or directly below** the filter body (expanded content). This row contains only Search and Reset. Do not place these two buttons in `.statistics-action-controls` with 그래프/표 and Excel.
  - **Behavior**: Submit (Search) runs the current filter query; Reset clears all filter fields (로그 타입, 부서, 사용자명, 사용자 ID, IP) to default/empty and optionally re-submits with cleared values (product to decide: clear only vs clear and re-run).
- **ActivityStatistics** (container):
  - **Keep** “그래프/표” toggle and “Excel 다운로드” in `.statistics-action-controls`. Ensure the filter form’s Search/Reset are not duplicated in this bar; the bar is for view mode and export only.
- **UserContextFilterBlock**: No change (already used with correct order and grouping).

### 3.3 Shared / cross-cutting

- **Button labels**: Use **“검색”** and **“초기화”** on both User Activity Log and Activity Statistics unless the product documents an exception (e.g. “조회” for Statistics).
- **Design doc**: If “조회” is kept for Statistics, add a short note in `docs/design/forms-and-filters.md` (or this redesign doc) that Statistics may use “조회” for the primary action with “초기화” for Reset, and that Activity Log uses “검색”/“초기화”; otherwise prefer one label set for both.

---

## 4. Optional: layout and accessibility notes

### 4.1 Layout

- **Filter actions row**: Same horizontal layout as Activity Log’s `.search-form-actions`: primary (검색) and secondary (초기화) in one row, with consistent spacing (e.g. 8–12px gap) per `forms-and-filters.md` compact variant and `buttons.md`.
- **Statistics page structure**: Order remains: [StatisticsHeader with date/period] → [Filter panel: header + expandable body + **new filter actions row**] → [Action bar: 그래프/표, Excel] → [Content]. No change to z-index or overlay; filter panel is in normal flow.

### 4.2 Accessibility

- **Form**: The new Statistics filter form must have an accessible name (`aria-label` or associated legend/title) so screen readers identify it as the search/filter form.
- **Buttons**: Search and Reset must have visible text (“검색”, “초기화”) or equivalent `aria-label`; focus order should go filter fields → Search → Reset (or Reset → Search per locale).
- **Collapsible**: Existing `aria-expanded` and `aria-controls` on the filter toggle and body must remain; when the panel is collapsed, ensure Search/Reset are still reachable (e.g. in the filter header row or always-visible strip) per `forms-and-filters.md` optional note, or keep the actions row visible when collapsed—implementation choice by Frontend to match Activity Log behavior.
- **Validation**: If Statistics has date or other validation (e.g. 일별 start ≤ end), keep field-level error display and `aria-invalid` / `aria-describedby` per `docs/design/date-search.md` and `text-input.md`.

### 4.3 Reference

- **UX role**: `docs/workflow/UX-ROLE-SEPARATION-DESIGN.md` §3 (search/filter UI: definition in design docs, implementation by Frontend, review by UX).
- **Design standards**: `docs/design/forms-and-filters.md`, `docs/design/buttons.md`, `docs/design/text-input.md`, `docs/design/date-search.md`.

---

**End of UX redesign.** Frontend implements from this document plus the requirement `docs/requirements/20260310-search-screens-qa-ux-redesign-handoff.md` §2 and design standard refs. QA verifies against §2 acceptance criteria and §3 test cases (TC-03–TC-06).
