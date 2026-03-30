---
name: activity-statistics-domain
description: Activity statistics scope behavior, related APIs, filters, and export flow.
---

# Activity statistics domain

Use this skill for activity-statistics scope questions (`self`, `team`, `all`), statistics APIs, filter visibility, and statistics export behavior.

## Core points

- Statistics scope follows the authenticated user's effective permission scope unless system-admin rules override it.
- `scope=self` should lock requests to the current user while keeping the aligned user-context block visible and fixed to auth/current-user `selfContext`.
- `scope=team` department options should show only the authenticated user's own department.
- `scope=all` keeps request parameters available.
- Statistics API usage and filter option APIs must stay aligned with the contract/spec.
- The `department` filter contract is shared with activity-log and search-history; the authoritative source is the new shared filter-options API for department options, not `/api/statistics/departments`. Docs and handoffs must name that shared API, its response shape, and the scope rules, not only a prop such as `departmentList`.
- **Activity log — permission-group audit**: For `PERMISSION_GROUP_*` and assign/unassign action types, persisted `action_detail` may include structured `permissionGroupAuditV1` (see `specs/activity-permission-group-audit.spec.yaml`). `GET /api/activity-log/{id}` returns the same stored JSON with `action_detail` parsed from DB (String/CLOB); scope rules match `POST /api/activity-log/search` (requirement `20260330-permission-group-activity-detail-audit`).

## References

- `docs/api-definition.md`
- `docs/contract.md`
- `docs/requirements/TOPIC-INDEX.md`
