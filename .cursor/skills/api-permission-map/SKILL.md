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
- Include auth/current-user contract checks when self-scoped UI behavior depends on backend-owned locked identity values; verify `selfContext.department`, `selfContext.username`, and canonical `selfContext.userId`.
- `POST /api/activity-log/search` must be traced through `UserActivityLogController` scope resolution and the downstream query enforcement. Verify that `scope=self` ignores widening filters, `scope=team` applies allowlist-first narrowing, and `scope=all` alone preserves legitimate cross-user search.
- When permission tests cover a scope-sensitive API, include regression cases for controller normalization and service/query enforcement so hidden request fields cannot bypass the contract.

## References

- `docs/contract.md`
- `specs/permission-group-hierarchy.spec.yaml`
- permission-related backend/frontend code in the current scope
