# Layout and navigation — design standard

Standard structure for **application shell**: left sidebar + right work area + top user bar. Apply to all new or changed screens so the app feels consistent.

---

## Structure

- **Left fixed sidebar**: Vertical menu fixed on the left. **Main navigation lives here only.** Do not duplicate menu buttons in the top header.
- **Right work area**: Main content area to the right of the sidebar. Only this area changes by route or selected menu.
- **Top user area**: One row at the top (of the right area or the whole app) for **user name, logout, etc.** No menu items in the top bar.

---

## Requirements

### Menu

- **2-depth tree maximum**: Primary items (e.g. Log search, Activity history) and optional second level. Avoid 3 or more depth.
- **Current menu item highlighted**: Active item clearly distinct (background, left bar, bold). Use `aria-current="page"` (or equivalent) on the current item for accessibility.
- **Sidebar collapsible**: User can collapse/expand the sidebar. Collapsed state: icons only or minimal width (e.g. 56–64px). Expanded: full labels (e.g. 240–256px).

### Components (MUI)

Use **Material-UI (MUI)** for layout and components:

- **Drawer**: Left navigation. `variant="permanent"` or `persistent`; `open` / `closed` for collapse.
- **List / ListItemButton / ListItemIcon / ListItemText**: Primary and secondary menu items. Second level: indent (e.g. inset or `pl`).
- **AppBar + Toolbar**: Top bar. Left: sidebar toggle; right: user info and logout.
- **IconButton**: Sidebar toggle, logout, etc.
- **Typography**: App title, user name, menu labels.
- **Collapse** (optional): Expand/collapse second-level items under a primary item.

### Tone

- **Simple, enterprise-internal**: Prioritize readability and efficiency over decoration. Restrained color, spacing, and typography.

---

## Sidebar collapse behavior

- **Expanded**: Full width (e.g. 240px), primary and secondary labels visible.
- **Collapsed**: Icon-only width (e.g. 56–64px). Secondary items: tooltip on hover or temporary popover on primary click.
- **Toggle**: IconButton in AppBar (e.g. left). `aria-label` reflects state: "Open sidebar" / "Close sidebar". Keyboard: focus + Enter/Space.

---

## What not to put in the top bar

- No menu items or nav links in the top bar. Navigation is only in the left sidebar.
- Back links like "← Main" or "← Log type selection" in the header should be removed; navigation is via the sidebar menu. Exception: context-specific back inside a content screen if the spec allows.

---

## Accessibility

- **Keyboard**: Toggle, all menu items, and logout must be focusable. Tab order: toggle → menu items → main content → user/logout.
- **ARIA**: Current page `aria-current="page"`; toggle `aria-label`; expandable menu `aria-expanded` where applicable.
- **Contrast and focus**: WCAG 2.1 AA (contrast, visible focus ring).

---

## z-index hierarchy

All custom overlays, modals, and fixed elements must follow this hierarchy to avoid occlusion conflicts.

| Layer | z-index | Examples |
|-------|---------|----------|
| Sidebar / Drawer | 1200 | MUI `theme.zIndex.drawer` |
| AppBar | 1201 | MUI `theme.zIndex.drawer + 1` |
| **Modal overlays** | **1300** | Custom dialog overlays (e.g. `.permission-group-dialog-overlay`) |
| MUI Modal/Dialog | 1300 | MUI `theme.zIndex.modal` |
| Toast / Snackbar | 1400 | MUI `theme.zIndex.snackbar` |
| Tooltip | 1500 | MUI `theme.zIndex.tooltip` |

- Custom modal overlays **must** use `z-index: 1300` or higher so they appear above the AppBar (1201).
- Do **not** use `z-index: 1000` or lower for modals — the AppBar will cover the modal header and close button.
- When UX reviews a screen with modals/dialogs, check z-index against this table.

---

## Review checklist

For every new menu or screen:

- Is the structure left sidebar + right work area + top user area?
- Is the menu at most 2 depth? Current item highlighted? Sidebar collapsible?
- Is MUI used for Drawer, List, AppBar, etc.?
- If the current UI is top-menu only, recommend migrating to "left sidebar + top user area" and state menu tree, collapse, and MUI usage.

---

*Related: `docs/design/layout-improvement-ux-spec.md` for project-specific menu tree and view mapping (Korean).*
