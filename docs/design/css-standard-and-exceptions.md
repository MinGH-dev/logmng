# CSS standard and exception management (중첩 방지)

Design standard for **frontend standard CSS** and **user-requested exceptions** so that a single source of truth is maintained and overlapping/conflicting overrides (중첩) are avoided.

**Related**: `forms-and-filters.md` § Compact variant, `search-fields-by-screen.md`, `search-field-definition-items.md`, `ux-frontend-standard-principles.md`. This doc defines **how** to implement and maintain the corresponding CSS. **Standard-first**: when a standard is undefined or unclear, do not implement; inform the user and ask for standard definition first per `ux-frontend-standard-principles.md` §2 and §10.

---

## 1. Principle: one standard, one place for exceptions

- **Standard CSS**: One shared file (or one set of CSS custom properties) holds the **design-system values** for search/filter UI (container padding, row gap, block gap, control height, button size, etc.). It is derived from `forms-and-filters.md`, `search-fields-by-screen.md`, and is the **single source** for those values.
- **Component CSS**: Each component’s CSS handles **layout/structure** (grid columns, block names, component-specific wrappers) and **only documented exceptions**. It must **not** redefine the standard values unless it is an approved exception.
- **Exceptions**: When a **user request** (or approved design change) requires a **screen-specific** deviation from the standard (e.g. “통계 화면만 버튼 크게”), that deviation lives **only** in the component’s CSS file and **must** be documented so that:
  - Future changes don’t “fix” it back to standard by mistake.
  - There is no second “standard” or competing layer (no nesting).

---

## 2. Where the standard lives

- **File**: `frontend/src/styles/search-filter-standard.css`
- **Contents**:
  - **CSS custom properties** (variables) for compact variant: e.g. `--sf-container-padding`, `--sf-row-gap`, `--sf-block-gap`, `--sf-control-height`, `--sf-control-padding`, `--sf-btn-min-size`.
  - **Optional**: Base utility classes (e.g. `.sf-compact-panel`, `.sf-control`, `.sf-btn`) that use those variables, so components can opt in by class instead of repeating values.
- **Design source**: Values in this file must match `docs/design/forms-and-filters.md` § Compact variant and `docs/design/search-fields-by-screen.md` (field width/height/padding). When the design doc changes, **only this file** is updated for those numbers (no duplication in component CSS).

---

## 3. How components use the standard

### 3.1 Single application point for control sizing

Control sizing (height, min-height, padding, border, border-radius, font-size) for search/filter **inputs**, **selects**, and **filter buttons** must be applied **only once**, from `search-filter-standard.css`, via the **standard wrapper class** (e.g. `.sf-compact-panel`).

- **Components must**: Add the wrapper class to the **form or filter root** (e.g. the element that wraps the whole search/filter panel, including header date range where applicable so that e.g. StatisticsHeader date inputs get the same height as activity-log date inputs).
- **Components must not**: Re-declare `height`, `min-height`, `padding`, `border`, `border-radius`, or `font-size` for `.form-control` or filter buttons inside that root. Repeating these in component CSS (or in shared blocks like UserContextFilterBlock) causes inconsistent field sizes across screens when specificity or load order varies.
- **Component CSS may only add**: Layout (grid/flex, column definitions, gaps, block structure), **width by role** (min-width/max-width using `var(--sf-field-*-min)`, `var(--sf-field-*-max)` or the same values from the design doc), and **documented exceptions** (see §4).

### 3.2 Import and variables

1. **Import order**: Component CSS imports the standard first, then defines layout and exceptions.
   ```css
   @import '../styles/search-filter-standard.css';
   /* component layout and exceptions below */
   ```
   Or the app entry (e.g. `index.js`) imports `search-filter-standard.css` once so variables are available everywhere.

2. **Use variables for standard values**: In component CSS, use `var(--sf-row-gap)` etc. instead of hardcoding `10px`. That way the single source is the standard file. *Existing component CSS can migrate gradually (new or touched files first).*

3. **Component file only adds**:
   - Layout (grid columns, flex order, block structure).
   - Width by role (same min/max for same role across screens; use standard width vars where defined).
   - **Documented exceptions** (see §4). If something matches the standard, do **not** repeat it; use the variable or the standard class.

---

## 4. Exception management (사용자 요청에 따른 별도 사항)

When a user or requirement asks for something **different from the standard** for a **specific screen or component** (e.g. “통계 검색 버튼만 오른쪽 끝에 크게”):

### 4.1 Rule: one place per exception, always documented

- **Where**: The exception is implemented **only** in that component’s CSS file (e.g. `StatisticsFilters.css`). Do **not** add it to the standard file (that would mix “global standard” with “screen-specific” and cause nesting).
- **Comment**: Every exception must have a **comment** directly above the rule:
  ```css
  /* Exception (req 20260311-xxx or design §X): 통계 검색 버튼만 오른쪽 끝 배치, 사용자 요청 */
  .statistics-filters .statistics-filters__actions { ... }
  ```
  So that anyone reading the file sees it’s intentional and knows the reason (requirement doc or design section).

### 4.2 Exception index (중첩 방지용)

- **File**: This document, §5 **Exception index** table (below).
- **Purpose**: One place to list **which screens/components have which exceptions**. When adding a new override, add a row so that:
  - We don’t add a **second** override for the same thing elsewhere (no overlapping overrides).
  - We can review periodically whether the exception is still needed or should be folded back into the standard.
- **When adding an exception**: (1) Implement in component CSS with comment. (2) Add one row to the Exception index (§5) with: Screen/component | Selector (or description) | Reason (req or design ref).

### 4.3 When to use standard vs exception

- **Change applies to all search/filter screens** (e.g. “모든 검색 폼 간격 12px로”) → Update **only** `search-filter-standard.css` (and design doc if needed). Do **not** add per-component overrides.
- **Change applies to one screen only** (e.g. “통계만 IP 필드 너비 200px”) → Implement in that **component’s CSS** with comment, and **add to Exception index**. Do **not** put it in the standard file.

### 4.4 Prohibited (CSS layer)

- **Do not** hardcode `height`, `padding`, `border-radius`, `gap`, `min-width`, or `max-width` in component CSS when that value is already in the standard; use `var(--sf-*)` or the wrapper so the standard is the single source.
- **Do not** override the same-meaning control (e.g. date input, search button) with different size or spacing per screen; same role → same standard.
- **Do not** implement when the standard for the task is undefined or ambiguous; follow `ux-frontend-standard-principles.md` §2 and §10 (inform user, request standard definition first).

---

## 5. Exception index

| Screen / component | Selector or description | Reason (req or design) |
|-------------------|-------------------------|-------------------------|
| (none yet)        | —                       | —                       |

*When you add an exception, add a row here and keep the table updated so overrides stay in one place per concern.*

---

## 6. Summary (중첩 방지 요약)

| Question | Answer |
|----------|--------|
| Where is the standard? | `frontend/src/styles/search-filter-standard.css` (variables + wrapper class `.sf-compact-panel`). Design source: `forms-and-filters.md`, `search-fields-by-screen.md`. |
| How is control sizing applied? | **Single application point**: Form/filter roots use the wrapper class so height, padding, border, font-size for inputs/selects/buttons come **only** from the standard file. Components do not re-declare these. |
| Where do exceptions go? | **Only** in the component’s own CSS file (e.g. `StatisticsFilters.css`), with a comment and a row in §5 Exception index. |
| Where do exceptions **not** go? | Not in the standard file; not in a second “standard-overrides.css”; not without a comment and index row. |
| How to avoid nesting? | (1) Standard = one file. (2) Wrapper class = one place where control sizing is applied. (3) Exceptions = one place per component + one index. (4) Don’t create “standard for statistics” or multiple layers of overrides; use “standard + documented exception” only. |

---

*Related: `forms-and-filters.md`, `search-fields-by-screen.md`, `ANALYSIS-design-doc-conflict-root-cause.md`.*
