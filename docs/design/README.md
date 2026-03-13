# Design standards

This folder contains **abstract, reusable UI design standards** for the project. Each document defines patterns, structure, class names or component usage, and accessibility by **component type** — no single screen is the reference.

**Owner**: UX subagent (design system owner). For every design or UX review, consult the relevant doc and ensure recommendations align with it.

**표준정의 단일 소스**: 화면별 검색/필터 필드 정의는 **`search-fields-by-screen.md`만** 사용한다. `search-field-definition-items.md`는 스키마(정의 항목)와 §4 공통 규칙만 담고, 화면별 구체 값은 중복 기술하지 않는다. 갱신 시 search-fields-by-screen만 수정하고, definition-items §4(화면 간 동일 적용)에 맞는지 검토할 것.

## Default lookup rule for screen-related agents

- **UX and Frontend agents must not rely only on handoff text** to know which UI standards apply. Even when the requirement or handoff does **not** explicitly cite a design doc, the agent must **read this index first** and then open the relevant standard docs for the task.
- **Minimum step for any screen-related work**: Start from `docs/design/README.md`, classify the task by concern, then read the applicable documents below before review or implementation.
- **Concern → default standard bundle**:
  - **Layout / navigation / shell / z-index**: `layout-and-navigation.md`
  - **Grid / list / table / pagination / rows-per-page**: `grid-and-table.md`
  - **Forms / filters / search panels**: `forms-and-filters.md`, `date-search.md`, `search-fields-by-screen.md`, `search-field-definition-items.md`
  - **Buttons / inputs / common controls**: `buttons.md`, `text-input.md`
  - **CSS standard / override handling**: `css-standard-and-exceptions.md`
  - **Undefined or conflicting standards**: `ux-frontend-standard-principles.md`
- **Same standard bundle for UX and Frontend**: UX reviews and Frontend implementations should read the same relevant docs so that omission in a handoff does not create divergent interpretations.

## Documents

| Document | Purpose |
|----------|---------|
| [grid-and-table.md](grid-and-table.md) | Data table screens: page structure (header → toolbar → actions → table), table container/wrapper/table classes, sticky header, sortable headers, column rules, pagination, page size (default 20, +/- and Enter), **search field assignment from DB schema by attribute** unless user requests otherwise, loading/empty. When to use vs form grids. |
| [layout-and-navigation.md](layout-and-navigation.md) | App shell: left sidebar + right work area + top user bar; 2-depth menu; current item highlight; collapsible sidebar; MUI (Drawer, List, AppBar). |
| [buttons.md](buttons.md) | Button types (primary, secondary, danger, disabled), size, placement, icon buttons, accessibility. |
| [text-input.md](text-input.md) | Single-line and multiline inputs: label, placeholder, error, disabled, width, accessibility. |
| [date-search.md](date-search.md) | Date/datetime range: start/end, validation (start ≤ end), labeling, timezone note, consistency with search forms. |
| [forms-and-filters.md](forms-and-filters.md) | Form layout (grid/flex), filter groups, submit/reset, error display. |
| [search-field-definition-items.md](search-field-definition-items.md) | Field-level definition schema (size, controlType, constraints, data source). Referenced by search-fields-by-screen. |
| [search-fields-by-screen.md](search-fields-by-screen.md) | Per-screen search field definitions (검색하기, 활동 이력). Same-name-different-context fields → ask user direction. |
| [css-standard-and-exceptions.md](css-standard-and-exceptions.md) | **Frontend CSS**: Standard CSS single source (`frontend/src/styles/search-filter-standard.css`), exception management (where to put user-requested overrides, comment + index to avoid 중첩). |
| [ux-frontend-standard-principles.md](ux-frontend-standard-principles.md) | **Standard-first behavior**: When to prefer common standards, required behavior when standard is undefined (inform user, do not implement first), prohibited list, required workflow, role-based and structure standards, icon and detail screen standards. Rules: `ux-frontend-standard-first.mdc`, `search-filter-form-design.mdc`. |

## Other files in this folder

- **layout-improvement-ux-spec.md**: Project-specific layout improvement spec (menu tree, view mapping). Referenced by `layout-and-navigation.md` for implementation detail.

## Approval when outside or conflicting with standards

When a request concerns a **specific screen or feature** and something is **not in** the current standards or **conflicts with** them, the **UX agent** must:

1. State that it is "현재 표준에 없음" or "현재 표준과 맞지 않음" and which doc/rule is missing or conflicting.
2. Ask the user for **approval** to define (or update) a standard for that screen/feature.
3. Proceed with design or with drafting the new/updated standard **only after** the user approves.

See `.cursor/agents/UX.mdc` § "Approval when outside or conflicting with standards".

**When a standard is missing or ambiguous**: When a **standard is missing or ambiguous** for the task (e.g. no definition for a new field role, control size, or icon), do **not** implement; inform the user and ask for standard definition first. See `ux-frontend-standard-principles.md` §2 (checklist) and §10 (workflow).

## Accessibility (a11y) checklist

Target: WCAG 2.1 AA. Apply when adding or modifying UI components.

### Interactive elements
- [ ] **Keyboard navigation**: All interactive elements (buttons, links, inputs, menus) reachable and operable via Tab/Enter/Escape.
- [ ] **Focus ring**: Visible focus indicator on all focusable elements (do not remove `outline`).
- [ ] **aria-label**: Icon-only buttons and links have `aria-label` or `aria-labelledby`.
- [ ] **Disabled state**: `disabled` attribute set (not just visual); tooltip explains why (see `ACTION_DISABLED_TOOLTIPS`).

### Color and contrast
- [ ] **Text contrast**: Minimum 4.5:1 for normal text, 3:1 for large text (18px+ or 14px+ bold).
- [ ] **Non-color indicators**: Do not rely on color alone to convey state (add icon, text, or pattern).

### Forms
- [ ] **Labels**: Every input has a visible `<label>` or `aria-label`.
- [ ] **Error messages**: Error state announced to screen readers (`aria-describedby` or `role="alert"`).
- [ ] **Required fields**: Indicated visually and with `aria-required="true"`.

### Tables
- [ ] **Table headers**: `<th>` with `scope="col"` or `scope="row"`.
- [ ] **Sortable columns**: Sort state conveyed via `aria-sort`.

### General
- [ ] **Page title**: Each view/screen has a descriptive `<title>` or heading.
- [ ] **Skip link**: "Skip to content" link for keyboard users (optional but recommended).

Reference: `docs/design/buttons.md` §a11y, UX.mdc §WCAG.

## For agents and humans

- **UX agent**: Must consult these standards for every design review and reference them in recommendations. When outside or conflicting, obtain user approval before defining or changing a standard.
- **Frontend**: Implements UI per contract and these standards; does not define the design system.
- **Role separation** (definition vs implementation, responsibility matrix, agent/skill mapping): **`docs/workflow/UX-ROLE-SEPARATION-DESIGN.md`**.
- **Index**: `docs/cursor-subagents/ux-design.md` lists these docs and gives a short summary for the UX subagent prompt.
