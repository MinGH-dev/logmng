# UX role separation design

This document defines the boundary between UX review and Frontend implementation.

## 1. UX owns

- design review
- accessibility review
- interaction guidance
- alignment with `docs/design/*`

## 2. Frontend owns

- React, CSS, and implementation details
- applying contract and UX guidance in code
- implementing shared UI fixes in frontend-owned files

## 3. When to involve UX

Use UX when the task changes layout, navigation, forms, a11y, component consistency, or interaction patterns.

## 4. When Frontend can proceed directly

Frontend can implement directly when the standard is already defined and the task is a straightforward application of that standard.

## 5. Undefined-standard rule

If a required design standard is missing or conflicting:

1. identify the missing or conflicting standard
2. explain why the standard is needed
3. ask for approval to define or revise the standard
4. proceed after approval

## References

- `docs/design/README.md`
- `docs/workflow/SUBAGENT-DELEGATION.md`
- `docs/cursor-subagents/frontend.md`
- `.cursor/agents/UX.mdc`
