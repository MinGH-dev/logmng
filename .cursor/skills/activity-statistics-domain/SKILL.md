---
name: activity-statistics-domain
description: Activity statistics scope behavior, related APIs, filters, and export flow.
---

# Activity statistics domain

Use this skill for activity-statistics scope questions (`self`, `team`, `all`), statistics APIs, filter visibility, and statistics export behavior.

## Core points

- Statistics scope follows the authenticated user's effective permission scope unless system-admin rules override it.
- `scope=self` should lock requests to the current user and hide incompatible user filters.
- `scope=all` keeps request parameters available.
- Statistics API usage and filter option APIs must stay aligned with the contract/spec.

## References

- `docs/api-definition.md`
- `docs/contract.md`
- `docs/requirements/TOPIC-INDEX.md`
