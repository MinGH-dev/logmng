# Text input — design standard

Reusable standard for **single-line and multiline text inputs** (search, filters, forms).

---

## Single-line input

- **Label**: Visible label associated with the control (`<label for="...">` or `aria-label`). Placeholder is not a substitute for a label.
- **Placeholder**: Optional hint (e.g. "Enter keyword"). Keep short; do not put required rules only in placeholder.
- **Width**: Full width in form layout, or fixed width when the field has a natural size (e.g. code, short ID). Use consistent max-width or grid column so layouts do not break.
- **Error state**: Show error message below or next to the field; set `aria-invalid="true"` and link message with `aria-describedby` so screen readers announce it.
- **Disabled**: Visually and semantically disabled; no hover/focus interaction. `disabled` or `aria-disabled` as appropriate.

---

## Multiline (textarea)

- **Label**: Same as single-line; ensure association with the textarea.
- **Rows**: Default visible rows (e.g. 3–5); resizable only if the design allows and does not break layout.
- **Placeholder / error / disabled**: Same rules as single-line (placeholder optional, error with `aria-invalid` and `aria-describedby`, disabled state clear).

---

## Validation and required

- **Required**: Indicate in label (e.g. asterisk + "Required" in legend or title). Set `aria-required="true"` (or `required`) so assistive tech can announce it.
- **Validation**: Inline error after submit or on blur, depending on spec. Error message must be programmatically associated (`aria-describedby`) and `aria-invalid` set when invalid.

---

## Accessibility

- **Label association**: Every input has a visible or programmatic label (`<label for="id">`, `aria-label`, or `aria-labelledby`).
- **Error**: `aria-invalid="true"` when invalid; error text referenced by `aria-describedby`.
- **Required**: `required` or `aria-required="true"` when the field is mandatory.
- **Focus**: Visible focus ring; no removal of outline without an equivalent focus style.

---

*For search forms and filter layout, see `forms-and-filters.md` and `date-search.md`.*
