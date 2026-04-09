---
name: search-consistency-domain
description: Screen-by-screen search/filter consistency, shared user axes, and visible locked self rules for applicable scope=self user/requester blocks.
---

# Search consistency domain

Use this skill when the task changes search/filter UI on user-context screens such as activity-log, statistics, user-management, permission-group-management, search-history, or pending-approvals.

## Core rules

- Use unified user axes: department, user name, user ID.
- Keep user-block ordering consistent across aligned screens.
- Group titles belong above their fields, not inline.
- When `scope=self` on an applicable user-context or requester-context screen, keep the block visible and use the wording **visible, fixed to current user, not editable**.
- Preserve locked self ordering as `department -> username -> userId`. In API/UI, userId is numeric `app_user.id` (req 20260316-user-id-numeric-userid-naming).
- Use the authenticated current-user payload or equivalent auth-owned current-user context as the authoritative source for locked self display values.
- For `activity-log` with `scope=self`, keep frontend request/reset behavior aligned with backend enforcement: visible locked self fields must stay fixed to current-user values, and any client identity values that could widen scope must be cleared, ignored, or normalized consistently with backend enforcement.
- **User Management v2** (`user-management-v2`): When `screenScopes['user-management-v2'] === 'self'`, the user filter block shows **visible locked self** (department → username → userId) from auth `selfContext`, read-only; widening search inputs are not used for that scope (backend enforcement remains authoritative).
- When aligning activity-log and statistics, keep user-block field size visually consistent across screens.
- For shared `select` fields, document the real authoritative API/domain source in docs and handoffs, not only a prop name such as `departmentList`.
- Treat the `department` select on activity-log, statistics, and search-history as one shared option contract; the authoritative source is the new shared filter-options API for department options, not `/api/statistics/departments`. Keep source wording, response shape, empty option behavior, and scope rule aligned across those screens.
- For that shared `department` contract, `scope=team` shows only the current user's own department unless a later requirement explicitly changes the rule.
- Visible locked self fields are presentation-only. Backend self-scope enforcement remains authoritative.

## References

- `docs/design/search-fields-by-screen.md`
- `docs/design/search-field-definition-items.md`
- `docs/design/forms-and-filters.md`
- `docs/workflow/ANALYSIS-user-field-size-activity-log-vs-statistics.md`
