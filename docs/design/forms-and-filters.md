# Forms and filters — design standard

Reusable standard for **form layout** and **filter groups** (search forms, filter panels, settings). Complements layout, text-input, buttons, and date-search standards.

---

## Form layout

- **Structure**: Group related fields (e.g. keyword, date range, type). Use a single container for the whole form or filter block.
- **Grid or flex**: Use CSS Grid or flex for field groups. Example: `grid-template-columns: repeat(auto-fit, minmax(200px, 1fr))` so fields wrap on small screens and stay aligned.
- **Spacing**: Consistent gap between rows and columns (e.g. 16px or 24px). Align with the rest of the design system.

---

## Filter groups (search / list filters)

- **Grouping**: Put filters that belong together in one row or block (e.g. keyword + date range + type in one toolbar or panel).
- **Submit / reset**: Provide explicit "Search" / "Apply" and "Reset" (or "Clear") buttons. Do not rely only on "search on every keystroke" unless the spec requires it; explicit submit reduces accidental API calls and is clearer for accessibility.
- **Error display**: Show validation errors next to or below the relevant field; do not only use a single top-of-form message unless it is a general error. See `text-input.md` and `date-search.md` for field-level error and ARIA.

---

## Buttons in forms

- **Primary**: Submit / Search / Save. One primary per form or filter block.
- **Secondary**: Reset / Clear / Cancel. Place next to primary; follow `buttons.md` for type and placement.

---

## When this applies

- **Search forms**, filter panels above tables, settings forms: use this layout and grouping.
- **Data tables**: Table structure is defined in `grid-and-table.md`; filters that sit above the table follow this document and sit in the "optional toolbar" or "header" area of the page structure.

---

*Related: `text-input.md`, `date-search.md`, `buttons.md`, `grid-and-table.md`.*
