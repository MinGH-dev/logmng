# Date search (date range) — design standard

Reusable standard for **date or datetime range** inputs in search and filter forms.

---

## Scope

- **Start and end**: Start date (and optionally time) and end date (and optionally time).
- **Input type**: Use `date` or `datetime-local` for native pickers, or a consistent date/datetime picker component (e.g. MUI DatePicker) across the app.
- **Labeling**: Clear labels: "Start date", "End date" (or "From" / "To"). For datetime: "Start date and time", "End date and time".

---

## Validation

- **Order**: Start ≤ End. If start is after end, show validation error and block submit or correct on change (e.g. set end = start when start changes and end was before start).
- **Optional bounds**: Min/max date (e.g. no future dates for "date of log") when required by the spec.

---

## Consistency with search forms

- Place date range in the same filter block as other criteria (e.g. keyword, type). Use the same form layout as in `forms-and-filters.md` (grid/flex, submit/reset).
- Timezone: If the backend or domain uses a specific timezone (e.g. UTC or server time), show a short note near the inputs (e.g. "Times in UTC") so users are not confused.

---

## Accessibility

- **Labels**: Each date/datetime input has an associated label (`<label for="...">` or `aria-label`).
- **Errors**: Validation errors use `aria-invalid` and `aria-describedby` as in `text-input.md`.
- **Picker**: If using a custom picker, ensure keyboard navigation and ARIA roles (e.g. calendar role, month/year selection).

---

*Related: `text-input.md`, `forms-and-filters.md`.*
