# UX Grid/Table Compliance Review Report

**Requirement**: `docs/requirements/20260226-ux-grid-review-and-push.md`  
**Design spec**: `docs/design/grid-and-table.md`  
**Standards**: `docs/workflow/CONSISTENCY-STANDARDS.md` §6  
**Reviewed by**: UX subagent (design review only; no code edits).

---

## Summary

- **Containers / structure / class names**: All 9 screens use the shared `DataTable` component, which correctly provides `.log-table-container` → `.table-wrapper` → `<table class="log-table">`, sticky `thead`, `.loading-container`, and `.pagination` (when `totalPages > 1`). **Compliant** at the component level.
- **Page root**: Screens use screen-specific roots (e.g. `.log-grid`, `.search-history-list`, `.pending-approvals`). Design allows “`.data-grid` or screen-specific root”; **compliant**.
- **Gaps found**: (1) **Page size default 20** not met on log search / image log (they use 10). (2) **Rows-per-page control** (numeric input, +/−, Enter) is **missing app-wide** (not in DataTable nor any screen). (3) **Sorting required** but **not implemented** on 5 screens (search history, pending approvals, user management, department approvers, activity log). (4) **Activity log** uses **pagination outside** the table container and custom pagination classes instead of DataTable’s built-in pagination. (5) **Full pagination** in DataTable lacks an explicit “Page X of Y” aria exposure for non-simple mode.

---

## Per-screen compliance and gaps

### 1. Log search (LogGrid + LogTable)

| Criterion | Status | Note |
|-----------|--------|------|
| Container / wrapper / table / classes | Compliant | Via DataTable. |
| Sticky header, sortable headers, aria-sort, keyboard | Compliant | LogTable passes sortable columns, onSort, sortConfig. |
| Loading / empty inside container | Compliant | DataTable. |
| Pagination (under wrapper, when totalPages > 1) | Compliant | Passed to DataTable. |
| **Page size default 20** | **Gap** | LogGrid uses `pageSize: 10` in API requests (`LogGrid.js` ~118, 195, 234). Must be 20 per design. |
| Rows-per-page control (+/−, Enter) | **Gap** | Not present (app-wide; see below). |

---

### 2. Image log (LogGrid + ImageLogTable)

| Criterion | Status | Note |
|-----------|--------|------|
| Container / wrapper / table / classes | Compliant | Via DataTable. |
| Sticky header, sortable headers, aria-sort, keyboard | Compliant | ImageLogTable passes sortable columns, onSort, sortConfig. |
| Loading / empty inside container | Compliant | DataTable. |
| Pagination | Compliant | Passed from LogGrid to ImageLogTable. |
| **Page size default 20** | **Gap** | Same LogGrid; uses `pageSize: 10`. Must be 20. |
| Rows-per-page control | **Gap** | Not present (app-wide). |

---

### 3. Search history (SearchHistoryList)

| Criterion | Status | Note |
|-----------|--------|------|
| Container / wrapper / table / classes | Compliant | Via DataTable. |
| Loading / empty inside container | Compliant | DataTable. |
| Pagination (simple, when totalPages > 1) | Compliant | Passed to DataTable. |
| Page size default 20 | Compliant | `getSearchHistoryList(pageNum, 20, ...)` in service. |
| **Sorting (required for all grids)** | **Gap** | All columns `sortable: false`; no `sortConfig`/`onSort` passed. Design and CONSISTENCY-STANDARDS §6 require sorting on every data table. |
| Rows-per-page control | **Gap** | Not present (app-wide). |

---

### 4. Pending approvals (PendingApprovals)

| Criterion | Status | Note |
|-----------|--------|------|
| Container / wrapper / table / classes | Compliant | Via DataTable. |
| Loading / empty inside container | Compliant | DataTable. |
| Pagination (simple) | Compliant | Passed when totalPages > 1. |
| Page size default 20 | Compliant | `getPendingList(pageNum, 20)`. |
| **Sorting (required)** | **Gap** | All columns `sortable: false`; no `sortConfig`/`onSort`. |
| Rows-per-page control | **Gap** | Not present (app-wide). |

---

### 5. User management (UserManagement)

| Criterion | Status | Note |
|-----------|--------|------|
| Container / wrapper / table / classes | Compliant | Via DataTable. |
| Loading / empty inside container | Compliant | DataTable. |
| Pagination | N/A | No server-side paging; single list. |
| **Sorting (required)** | **Gap** | All columns `sortable: false`; no `sortConfig`/`onSort`. At least one sortable column (e.g. userId) should be provided per design. |

---

### 6. Department approvers (DepartmentApproverManagement)

| Criterion | Status | Note |
|-----------|--------|------|
| Container / wrapper / table / classes | Compliant | Via DataTable. |
| Loading / empty inside container | Compliant | DataTable. |
| Pagination | N/A | List per selected department; no paging in current implementation. |
| **Sorting (required)** | **Gap** | All columns `sortable: false`; no `sortConfig`/`onSort`. |

---

### 7. Activity log (UserActivityLogList + UserActivityLogTable)

| Criterion | Status | Note |
|-----------|--------|------|
| Container / wrapper / table / classes | Compliant | DataTable provides correct structure. |
| Loading / empty inside container | Compliant | DataTable. |
| Page size default 20 | Compliant | `pageSize: 20` in UserActivityLogList. |
| **Sorting (required)** | **Gap** | UserActivityLogTable: all columns `sortable: false`; no `sortConfig`/`onSort`. |
| **Pagination placement and pattern** | **Gap** | Pagination is rendered **outside** DataTable (in UserActivityLogList), as a sibling of UserActivityLogTable. Design: “Place directly under `.table-wrapper`, **inside** the table container” and use “a consistent class (e.g. `.pagination`)”. Pagination should be passed into DataTable so it sits inside `.log-table-container` under `.table-wrapper`. |
| **Pagination class consistency** | **Gap** | UserActivityLogList uses custom `.pagination-button` and its own `.pagination`/`.pagination-info` layout; UserActivityLog.css redefines `.pagination`. Standard is DataTable’s `.pagination` with `.page-btn` and `.pagination-info`. Should use DataTable’s pagination prop for consistent structure and classes. |
| Rows-per-page control | **Gap** | Not present (app-wide). |

---

### 8. User statistics (UserStatisticsTable)

| Criterion | Status | Note |
|-----------|--------|------|
| Container / wrapper / table / classes | Compliant | Via DataTable. |
| Sortable headers, aria-sort, keyboard | Compliant | sortConfig, onSort passed; sortable columns. |
| Loading / empty | Compliant | Empty state via EmptyTableBody. |
| Pagination | N/A | Client-side data; no server paging. |
| **Verdict** | **Compliant** | No gaps for this screen relative to design (given no server pagination). |

---

### 9. Activity statistics — table (StatisticsTable + ActivityStatistics)

| Criterion | Status | Note |
|-----------|--------|------|
| Container / wrapper / table / classes | Compliant | Via DataTable. |
| Sortable headers, aria-sort, keyboard | Compliant | sortConfig, onSort passed. |
| Empty state | Compliant | EmptyTableBody. |
| Pagination | N/A | Client-side daily/monthly data. |
| **Verdict** | **Compliant** | No gaps for this screen. |

---

## App-wide / shared component gaps

These apply across screens or to the shared DataTable only.

| Gap | Reference | Recommendation |
|-----|-----------|----------------|
| **Rows-per-page control missing** | `grid-and-table.md` § “Page size (rows per page)”; CONSISTENCY-STANDARDS §6 | Add to DataTable (or single shared place): “Rows per page” (or “표시 건수”) with numeric input, + and − buttons (apply immediately on click), and apply on Enter for typed value. Default 20; validate min/max (e.g. 1–100). |
| **Page size default 10 on log/image search** | Same § “Page size” | Change LogGrid API request `pageSize` from 10 to 20 for both log search and image log. |
| **Full pagination aria** | `grid-and-table.md` § Accessibility: “Pagination: accessible labels (e.g. ‘Page 1 of 5’)” | In DataTable, for non-simple pagination, add an accessible “Page X of Y” label (e.g. `aria-live="polite"` or `aria-label` on the pagination block) so screen readers get current page context. |

---

## Conclusion

- **Fully compliant (no changes needed for this review)**: **User statistics**, **Activity statistics (table)**.
- **Screens with gaps (remediation needed)**:
  - **Log search**, **Image log**: page size default 20 + rows-per-page control (and app-wide control).
  - **Search history**, **Pending approvals**, **User management**, **Department approvers**, **Activity log**: add sorting (at least one sortable column, sortConfig, onSort, same UX as other tables).
  - **Activity log** only: move pagination inside the table container via DataTable’s pagination prop and remove duplicate/custom pagination markup and `.pagination` overrides in UserActivityLog.css.

If Frontend implements the above, all 9 screens will align with `docs/design/grid-and-table.md` and CONSISTENCY-STANDARDS §6. No code edits were made in this review; this document is review-only.
