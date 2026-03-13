# Analysis: Search window size not aligned & tool improvement

**Date**: 2026-03-11  
**Trigger**: User asked why "검색 창 크기" (search window size) was not made the same between activity log and statistics, and whether tools have room for improvement.  
**Scope**: Analysis only; no code or requirement changes.

---

## 1. Why search window size was not aligned

### 1.1 What was in scope (20260311)

Requirement **20260311-statistics-search-align-activity-log** explicitly scoped:

- **In scope**: Filter group **title placement** (“기타 조건” above fields, not inline), **block structure** (`role="group"`, `aria-labelledby`), **compact spacing** (block/row gap) to match activity log.
- **Out of scope**: “No change to **field order** or **scope=self** behavior”; “This requirement **focuses only on** layout and group title placement for the statistics ‘기타 조건’ block.”

**Search window / form panel size (width or height)** was never stated in:

- §1 (user requirement, scenario, expected outcome)
- §2 (codebase summary, problem analysis, solution)
- Design refs: `forms-and-filters.md`, `analysis-search-consistency-by-screen.md`

So the omission is **scope choice**: the requirement was written and implemented to “align with activity log” only for **layout and title placement**, not for **form/panel dimensions**.

### 1.2 What the tools actually cover

| Source | What it says about size/width |
|--------|-------------------------------|
| **docs/design/forms-and-filters.md** | Compact variant: row/field gap 8–12px, block-to-block 12–16px, **container padding** 12–16px. **No** “form width” or “search panel width” or “same width as activity log”. |
| **docs/analysis-search-consistency-by-screen.md** | Field order (부서·이름·사용자ID), scope=self, which screens get which axes. **No** form width or panel size. |
| **.cursor/skills/search-consistency-domain/SKILL.md** | Field order, filter group title placement, scope=self. **No** form panel width/size. |
| **docs/design/UX-REDESIGN-activity-log-statistics-search.md** | Button placement, form semantics, compact variant. **No** form width or panel size. |
| **REQUIREMENTS-CHANGE-TARGET-CHECKLIST.md** | Scope touchpoints (Backend, Frontend, DB, Contract, Cursor); domain patterns (scope-supporting screen, permission group, API/error). **No** “search/filter visual consistency” pattern that includes width/size. |

So **none of the current tools** require or suggest “search window size” or “form panel width” alignment between activity log and statistics. If the author had wanted size alignment, they would have had to add it from scratch; there is no checklist or design rule that would have prompted it.

### 1.3 Current state in code

- **Page-level container**: Both screens use the **same** outer container: `max-width: 1400px`, `padding: 20px`, `margin: 0 auto` (activity log: `.activity-log-list-container`; statistics: `.activity-statistics`). So the **page** width is already aligned.
- **Search form panel**: Neither the activity log search form nor the statistics filters panel has an explicit **max-width** or **width** on the form itself; both stretch to 100% of the container. So at container level they are the same.
- **Difference**: The **internal layout** differs (e.g. statistics row-1 uses `grid-template-columns: minmax(140px, 180px) 1fr`; activity log row-1 is flex with date fields 140–220px). So the **visual distribution** (column widths, wrapping) can make the two forms *feel* different in size even though the outer container is the same. “검색 창 크기” could mean:
  - Same **form panel** width → already same (100% of 1400px container), or
  - Same **column/field** widths and same “footprint” → not specified in requirement or design, so not done.

**Conclusion**: Search window size was not aligned because (1) the requirement was deliberately limited to layout/title placement, and (2) no design doc or checklist mentions form/panel width or cross-screen size alignment, so it was never raised as a criterion.

---

## 2. Tool improvement suggestions

The following are **optional** improvements so that future “align search UI” work can consider **size/width** and not miss it.

### 2.1 Design doc: forms-and-filters.md

- **Add** a short subsection (e.g. **“Search form panel width (user-context screens)”**):
  - On activity-log and statistics, the search/filter **panel** should use the same width constraints (e.g. full width of the page container, or the same explicit max-width if one is set) so both screens present the same visual footprint.
  - If the product defines an explicit max-width for the search form (e.g. 900px), apply it to both screens; otherwise “full width of page container” is the default.

This gives Requirements and Frontend a single rule to cite when “검색 창 크기 동일화” is requested.

### 2.2 REQUIREMENTS-CHANGE-TARGET-CHECKLIST.md

- **Add** a domain-specific pattern **§2.4 “Search/filter UI consistency (activity-log, statistics, etc.)”**:
  - When the requirement is to **align** search/filter UI between two user-context screens (e.g. statistics with activity log), ensure §2 and the change file list cover:
    - Layout (group title placement, block structure).
    - **Form/panel width** (same container or same explicit width).
    - Spacing (compact variant).
    - a11y (role="group", aria-labelledby).
  - Reference: `docs/design/forms-and-filters.md`, `docs/analysis-search-consistency-by-screen.md`.

This makes “form width” a **checkable** touchpoint when the requirement type is “search consistency”.

### 2.3 search-consistency-domain (SKILL.md)

- In **Quick reference**, add one bullet:
  - **Form panel width**: For activity-log and statistics, the search/filter panel should use the same width constraints (full width of page container or same max-width) so both screens look the same size. See `docs/design/forms-and-filters.md` when the requirement mentions “검색 창 크기” or “동일 크기”.

So agents that read the skill when handling “검색 통일” or “통계 검색창 개선” are reminded of width/size.

### 2.4 REQUIREMENTS-AUTHORING-WORKFLOW.md

- In **step 3 (Codebase investigation)**, add an optional note:
  - When the requirement is to **align screen A with screen B** (e.g. statistics search with activity log search), list not only layout and spacing but also **form/panel dimensions** (width, and height if relevant) so the solution can include size alignment if the user expects it.

This reduces the chance that Requirements only compares “layout” and omits “size”.

### 2.5 Requirement template (optional)

- In the **Expected outcome** or §2 guidance, add an optional line:
  - “If aligning two search/filter UIs, consider: layout, group title placement, spacing, **and form panel width/size**.”

So authors are prompted to consider size when writing “활동 이력 참고해서 통계 검색창 개선” type requirements.

---

## 3. Summary

| Question | Answer |
|----------|--------|
| **Why wasn’t search window size aligned?** | The requirement 20260311 was scoped only to layout and group title placement. No design doc or checklist mentioned “search window size” or “form panel width,” so it was never added to §1/§2. Page container is already the same (1400px); form-level width/size was not specified. |
| **Is there room to improve the tools?** | Yes. Adding (1) a “form panel width” rule in `forms-and-filters.md`, (2) a “search/filter consistency” pattern in REQUIREMENTS-CHANGE-TARGET-CHECKLIST that includes width/size, (3) a width bullet in search-consistency-domain SKILL, (4) an authoring note in REQUIREMENTS-AUTHORING-WORKFLOW for align-A-with-B requirements, and (5) an optional template prompt would make “검색 창 크기” a first-class, checkable concern in future work. |

No code or requirement changes were made in this analysis; the above are recommendations for the maintainers.
