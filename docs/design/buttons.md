# Buttons — design standard

Reusable standard for **button** types, placement, and accessibility across the app.

---

## Button types

| Type       | Use case                          | Visual (typical)              |
|-----------|------------------------------------|--------------------------------|
| **Primary** | Main action (submit, save, confirm) | Filled, primary color          |
| **Secondary** | Alternative or cancel-next flow   | Outlined or subtle fill        |
| **Danger** | Destructive or high-risk (delete, revoke) | Filled or outlined, error/danger color |
| **Disabled** | Action not available              | Muted, no hover/click           |

Use one primary action per context when possible; avoid multiple primary buttons in the same block.

---

## Size and styling

- **Size**: Consistent height (e.g. default ~36px, large ~40px). Padding and border-radius aligned with the design system (e.g. MUI default radius).
- **Padding**: Enough horizontal padding for label and optional icon; minimum touch target ~44×44px for icon-only.
- **Border-radius**: Same as other form controls (e.g. 4px or 8px).
- **Hover / active**: Clear hover and active states (background or border change). No hover effect when disabled.

---

## Placement

- **Actions row**: Buttons that apply to a whole list or section (e.g. above a table: "Bulk approve", "Export"). Place in a dedicated row (e.g. `.data-grid-actions`).
- **Inline with form**: Submit / Reset next to or below the form. Primary on the left (or right per locale); secondary/cancel adjacent.
- **In table cells**: Use text links or icon buttons for row actions (e.g. View, Approve). Prefer icon + tooltip or compact label to avoid clutter.
- **Modals / dialogs**: Primary action (e.g. Confirm) and secondary (Cancel) at bottom; primary emphasized.

---

## Icon buttons

- **Use**: Single clear action (e.g. sidebar toggle, logout, row action). Always pair with `aria-label` (e.g. "Close sidebar", "Delete row").
- **Size**: Minimum touch target; icon size consistent (e.g. 24px).
- **Placement**: Toolbars, table rows, header. Do not use as the only way to perform a critical action without a visible label somewhere (e.g. in menu or tooltip).

---

## Accessibility

- **Focus**: Visible focus ring (keyboard). No removal of outline without an equivalent focus style.
- **Labels**: Every button has an accessible name: visible text or `aria-label`. Icon-only: `aria-label` required.
- **Disabled**: Use `disabled` and avoid only color to convey state; optional `aria-disabled` if behavior is custom.
- **Danger**: Ensure sufficient color contrast and that danger is not indicated by color alone (e.g. icon or label).

---

*Align with MUI Button / IconButton when using MUI. For form layout and filter groups, see `forms-and-filters.md`.*
