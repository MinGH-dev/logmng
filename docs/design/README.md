# Design standards

This folder contains **abstract, reusable UI design standards** for the project. Each document defines patterns, structure, class names or component usage, and accessibility by **component type** — no single screen is the reference.

**Owner**: UX subagent (design system owner). For every design or UX review, consult the relevant doc and ensure recommendations align with it.

## Documents

| Document | Purpose |
|----------|---------|
| [grid-and-table.md](grid-and-table.md) | Data table screens: page structure (header → toolbar → actions → table), table container/wrapper/table classes, sticky header, sortable headers, column rules, pagination, page size (default 20, +/- and Enter), **search field assignment from DB schema by attribute** unless user requests otherwise, loading/empty. When to use vs form grids. |
| [layout-and-navigation.md](layout-and-navigation.md) | App shell: left sidebar + right work area + top user bar; 2-depth menu; current item highlight; collapsible sidebar; MUI (Drawer, List, AppBar). |
| [buttons.md](buttons.md) | Button types (primary, secondary, danger, disabled), size, placement, icon buttons, accessibility. |
| [text-input.md](text-input.md) | Single-line and multiline inputs: label, placeholder, error, disabled, width, accessibility. |
| [date-search.md](date-search.md) | Date/datetime range: start/end, validation (start ≤ end), labeling, timezone note, consistency with search forms. |
| [forms-and-filters.md](forms-and-filters.md) | Form layout (grid/flex), filter groups, submit/reset, error display. |

## Other files in this folder

- **layout-improvement-ux-spec.md**: Project-specific layout improvement spec (menu tree, view mapping). Referenced by `layout-and-navigation.md` for implementation detail.

## Approval when outside or conflicting with standards

When a request concerns a **specific screen or feature** and something is **not in** the current standards or **conflicts with** them, the **UX agent** must:

1. State that it is "현재 표준에 없음" or "현재 표준과 맞지 않음" and which doc/rule is missing or conflicting.
2. Ask the user for **approval** to define (or update) a standard for that screen/feature.
3. Proceed with design or with drafting the new/updated standard **only after** the user approves.

See `.cursor/agents/UX.mdc` § "Approval when outside or conflicting with standards".

## For agents and humans

- **UX agent**: Must consult these standards for every design review and reference them in recommendations. When outside or conflicting, obtain user approval before defining or changing a standard.
- **Frontend**: Implements UI per contract and these standards; does not define the design system.
- **Index**: `docs/cursor-subagents/ux-design.md` lists these docs and gives a short summary for the UX subagent prompt.
