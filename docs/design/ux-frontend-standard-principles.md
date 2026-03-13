# UX/Frontend standard-first principles

This document defines **mandatory behavior** for any agent or implementer working on search, filter, detail, icon, or field layout. **Common standards take precedence over per-screen ad-hoc implementation.** When a standard is missing or unclear, do not implement arbitrarily; inform the user and ask for standard definition first.

**Related**: `forms-and-filters.md`, `search-fields-by-screen.md`, `css-standard-and-exceptions.md`, `.cursor/rules/ux-frontend-standard-first.mdc`, `.cursor/rules/search-filter-form-design.mdc`.

---

## 1. Basic principles

1. **Same item, same meaning, same role** → the same standard applies regardless of screen.
2. **Prefer common standards** over per-screen ad-hoc implementation.
3. **Reuse existing standards**: if a common standard is already defined, it must be used as-is.
4. **Do not proceed arbitrarily** when the standard is missing or unclear.
5. **Standard missing**: inform the user first, define the standard in detail, then ask for feedback before implementing.

---

## 2. Required behavior when standard is undefined

If **any** of the following is not defined or is ambiguous, **do not implement**. First inform the user.

| Standard area | Description | Why it is needed |
|---------------|-------------|------------------|
| **Common layout rules** | Page/section structure, container width, spacing between blocks | Same structure across screens avoids visual inconsistency. |
| **Search/filter field placement** | Row order, block order, group titles, actions row | Same placement pattern so users find filters in the same place. |
| **Detail screen field alignment** | Label position, value alignment, spacing, emphasis | Same meaning → same layout so detail screens feel consistent. |
| **Icon meaning, size, position** | Which icon for which state/action; size; alignment; spacing from text | Same meaning → same icon; no mixed icon sets per screen. |
| **Input/select/button size** | Height, padding, border-radius; single application point (wrapper) | Same controls look the same on every screen. |
| **Width by role** | min/max width for date, single-select, user block, extra block, etc. | Same role → same width so fields do not look different per screen. |
| **Label, placeholder, button order** | Canonical labels (e.g. "검색", "초기화"); placeholder text; primary/secondary order | Same actions and hints across search/filter UIs. |
| **Common field expression** | Status, date, user, amount, code, description display format | Same data type → same format (e.g. date format, user display). |

**Required response format** when one or more of the above are undefined or ambiguous:

1. **List** the undefined or ambiguous standard items clearly.
2. **Explain briefly** why each is needed (e.g. "so the same role has the same width on all screens").
3. **Do not implement**; ask the user to confirm that the standard should be defined in detail first, then proceed.
4. **Optionally** propose a recommended standard draft so the user can decide quickly.

**Implementer obligation** (for requirement docs and handoffs): If any required standard for layout, field sizing, spacing, icon usage, label placement, or control semantics is not defined or is ambiguous in the design docs, the implementer must not infer or hardcode a solution. The implementer must first inform the user of the undefined standard items, explain why each is needed, propose a recommended standard draft, and request feedback so the standard can be explicitly defined before implementation proceeds.

**Example response**:

- "This task involves date field width, detail field label alignment, and status icon usage, which are not yet defined in the common standards."
- "To keep the same meaning consistent across screens, these standards need to be defined first."
- "Please confirm that we should define these standards in detail (e.g. in `docs/design/...`) and then proceed with implementation."
- "If needed, I can draft the standard definitions for your review."

---

## 3. Search/filter UI standard (enforcement)

1. Input, select, and button in the search/filter area must be styled **only inside the common wrapper class** (e.g. `.sf-compact-panel`). See `frontend/src/styles/search-filter-standard.css` and `css-standard-and-exceptions.md` §3.
2. **Do not** re-define height, padding, border, or border-radius for `input`, `select`, `button`, or `.form-control` in a screen or component CSS.
3. All search/filter-related controls use the **same standard variable set** (`var(--sf-*)`); no exceptions without a documented exception (see `css-standard-and-exceptions.md` §4–5).
4. **Do not hardcode** `height`, `padding`, `border-radius`, `gap`, `min-width`, `max-width` in component CSS when those values are already in the standard; use variables or the wrapper.
5. **New values**: define them in the common standard (tokens or `search-filter-standard.css`) first, then use them.

---

## 4. Role-based standard

Same **role** → same size system. Roles include (but are not limited to):

- Date/period input
- Single-select dropdown
- Multi-condition block
- User-context block (부서, 사용자명, 사용자 ID)
- Text search field
- Action buttons (search, reset, download, etc.)

**Rules**:

1. Same role uses the **same min/max width** on all screens. Use `var(--sf-field-*-min)`, `var(--sf-field-*-max)` or the values in `search-fields-by-screen.md`.
2. **Do not** use different arbitrary numbers per screen (e.g. 180px on one, 220px on another for the same role).
3. **Exceptions**: if a screen truly needs a different width for a role, define it as a **role modifier** in the common standard layer, not as a one-off in component CSS (unless documented in the Exception index).

---

## 5. Detail screen standard

Detail screens (modals, side panels, detail pages) also follow common standards.

1. Same meaning → same **label position**, **value alignment**, **spacing**, and **emphasis**.
2. Common information (status, datetime, user, amount, code, description) must be expressed the **same way** across screens (same format, same label wording where applicable).
3. **Do not** use different icons, different label names, or different alignment for the same meaning.

---

## 6. Icon standard

1. Icons are for **meaning**, not decoration.
2. Same meaning (state, action, or information) → **same icon**.
3. **Do not** use different icons for the same meaning on different screens.
4. Icon **size**, **alignment**, **margin**, and **spacing from text** follow the common standard.
5. If the icon set or mapping is **undefined**, inform the user and ask for a standard definition before implementing.

---

## 7. Structure standard

The following are defined by the common standard and must not be invented per screen:

- Label position (above field, inline, etc.)
- Date range notation (start–end format, timezone)
- Placeholder wording rules
- Button order (primary then secondary; e.g. 검색 then 초기화)
- Icon position relative to label or value
- Field-to-field gap
- Row alignment (e.g. align end for single row of fields)
- Required vs optional indication

Reference: `forms-and-filters.md`, `search-fields-by-screen.md`, `buttons.md`, `date-search.md`.

---

## 8. Shared block reuse

Blocks reused on multiple screens (e.g. `UserContextFilterBlock`) must keep the **same visual system** even when layout (flex vs grid) differs.

1. Use **max-width** or a **common grid column rule** when needed so the block does not stretch to different widths on different screens.
2. Consider responsive and line-wrap impact before adding max-width.
3. Prefer **unifying the shared block rules** over per-screen fixes.

---

## 9. Prohibited (금지 사항)

1. **Overriding** an existing common standard with a screen-specific style for the same meaning.
2. Applying **different height, padding, or width** to the same meaning/role on different screens.
3. **Adding raw px (or other numeric values)** for size/gap without a standard definition (use `var(--sf-*)` or add the value to the standard first).
4. Using **different icons** for the same meaning/state/action on different screens.
5. **Implementing without user confirmation** when the standard is unclear or missing for the task.

---

## 10. Required workflow (필수 작업 순서)

Before implementing search, filter, detail, icon, or field layout:

1. **Classify** the request: which common **role(s)** and **standard areas** does it touch? (e.g. date field, user-context block, detail label alignment.)
2. **Check** whether a common standard already exists for those roles/areas (`docs/design/`, `search-filter-standard.css`, `search-fields-by-screen.md`).
3. **If a standard exists**: apply it as-is; do not re-define or override in component CSS.
4. **If the standard is missing or unclear**: do **not** implement. Inform the user with:
   - List of undefined/ambiguous items
   - Brief reason each is needed
   - Request for feedback: "Define the standard in detail first, then proceed."
   - Optional: recommended standard draft
5. **After** user confirmation or standard confirmation, proceed with implementation.

---

## 11. Response principle (응답 원칙)

- **Before implementing**: check for standard conflict or gap. If the task touches layout, search/filter, detail, icon, or field sizing, verify that the relevant standard exists and is clear.
- **If unclear**: do **not** write code first. Explain what standard definition is needed.
- **To the user**: include (1) undefined standard items, (2) needed definition detail, (3) recommended standard draft if possible, (4) explicit request: "Please confirm that we define this standard first and then proceed."

---

*This document is the single reference for "standard-first" behavior. Rules (e.g. `ux-frontend-standard-first.mdc`, `search-filter-form-design.mdc`) reference it and enforce the same workflow.*
