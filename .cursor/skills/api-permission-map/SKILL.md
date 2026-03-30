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
- `GET /api/activity-log/{id}` uses the same effective scope as search (`self` → owner check; `team` → same-department allowlist; `all` → no extra row filter). A principal must not read detail for a row they could not see in search (MF-02).
- When permission tests cover a scope-sensitive API, include regression cases for controller normalization and service/query enforcement so hidden request fields cannot bypass the contract.
- **Search screen decrypt UI**: The search (main) screen must hide or disable decrypt actions (approval request button and per-row decrypt) when the user lacks main decrypt (`screenFunctions.main.decrypt` false and not system admin); the UI shows "복호화 권한이 없습니다." (req 20260317-search-decrypt-permission-ui).

## References

- `docs/contract.md`
- `specs/permission-group-hierarchy.spec.yaml`
- permission-related backend/frontend code in the current scope
