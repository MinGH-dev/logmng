# Grid and table — design standard

Generic, reusable standard for **data table** screens. Apply to any screen that displays list or tabular data (logs, activity, approvals, search history, etc.).

---

## Page structure (data table screens)

Use a consistent order for all data-table screens:

1. **Page wrapper**: One root block for the screen (e.g. `.data-grid` or screen-specific root class).
2. **Header block**: Title and optional short description (e.g. `h2` + description text).
3. **Optional toolbar**: Search mode, filters, or other controls between header and actions.
4. **Optional actions row**: Buttons or links that apply to the whole list (e.g. bulk approve, export). Use a dedicated class (e.g. `.data-grid-actions`).
5. **Table area**: The table component only. No repeated navigation or menu inside this area.

**Order**: `header → [toolbar] → [actions] → table`.

---

## Table structure and class names

- **Container**: `.log-table-container` (or equivalent data-table container). Full width, constrained height (e.g. `calc(100vh - 200px)`), `overflow: hidden`.
- **Scroll wrapper**: `.table-wrapper` — `overflow: auto`, contains only the `<table>` (and scrollbar styling).
- **Table**: `<table class="log-table">` — `border-collapse: collapse`; `table-layout: auto` or `fixed`; column widths by `%` and `min-width`; long content: `word-break` / `text-overflow: ellipsis` as needed.
- **Header**: `<thead>` with **sticky header** (e.g. `position: sticky; top: 0; z-index: 10`), background distinct from body.
- **Sortable headers**: Use a consistent pattern (e.g. `.sortable-header` with click handler and sort icon). Same interaction across all data tables.
- **Loading / empty state**: When loading or no data, show state inside the table container (e.g. `.loading-container` or overlay) without changing the outer page structure.
- **Pagination**: Place directly under `.table-wrapper`, inside the table container. Show only when `totalPages > 1` (or equivalent). Use a consistent class (e.g. `.pagination`) with first/prev/page numbers/next/last.

---

## Column and interaction rules

- **Column widths**: Prefer `%` + `min-width` per column so horizontal scroll appears only when necessary. Avoid fixed pixel widths for all columns when the table is full-width.
- **Row hover**: Consistent hover style for `<tbody tr>` (e.g. background change).
- **Detail / drill-down**: Use a detail modal or drawer opened from a row action (e.g. view detail, approve); do not replace the whole table with a single-row view unless the spec requires it.

---

## When to use this standard vs other layouts

- **Use this standard** (`.log-table-container` → `.table-wrapper` → `.log-table`): Any **list or tabular data** (logs, activity logs, approvals, search history, etc.). Same structure and class names so CSS and behavior stay consistent across the app.
- **Use other layouts**: **Form layouts** (search filters, filter panels, settings forms) — use CSS Grid or flex (e.g. `grid-template-columns: repeat(auto-fit, minmax(200px, 1fr))`). Those are control groups, not data tables.

---

## Accessibility

- Use semantic `<table>`, `<thead>`, `<tbody>`, `<th>`, `<td>`.
- Sortable headers: expose sort state (e.g. `aria-sort="ascending"` / `"descending"`), and ensure keyboard activation.
- Loading/empty: announce to screen readers (e.g. `aria-live="polite"` or status text).
- Pagination: accessible labels and keyboard navigation (e.g. "Page 1 of 5", focus management).

---

*This standard is component-type based; no single screen is the reference. For layout and navigation, see `layout-and-navigation.md`.*
