---
name: search-consistency-domain
description: Screen-by-screen search/filter consistency, shared user axes, and scope=self hiding rules.
---

# Search consistency domain

Use this skill when the task changes search/filter UI on user-context screens such as activity-log, statistics, user-management, permission-group-management, search-history, or pending-approvals.

## Core rules

- Use unified user axes: department, user name, user ID.
- Keep user-block ordering consistent across aligned screens.
- Group titles belong above their fields, not inline.
- When `scope=self`, hide user filters that should be fixed to the current user.
- When aligning activity-log and statistics, keep user-block field size visually consistent across screens.

## References

- `docs/design/search-fields-by-screen.md`
- `docs/design/search-field-definition-items.md`
- `docs/design/forms-and-filters.md`
- `docs/workflow/ANALYSIS-user-field-size-activity-log-vs-statistics.md`
