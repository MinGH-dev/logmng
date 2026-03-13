---
name: api-permission-map
description: API permission enforcement mapping for controllers, checks, and denial behavior.
---

# API permission map

Use this skill when planning permission-verification tests or when tracing which permission check protects an API.

## Core points

- Map API endpoint -> controller -> permission check -> denial result.
- Keep permission verification aligned with `docs/contract.md` and permission specs.
- Distinguish list/view scope from approval authority when both are involved.

## References

- `docs/contract.md`
- `specs/permission-group-hierarchy.spec.yaml`
- permission-related backend/frontend code in the current scope
