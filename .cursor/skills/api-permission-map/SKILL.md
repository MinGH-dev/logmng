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
- **Provisioning (req 20260407)**: Admin-only (same guard as user management: `AuthService.canAccessUserManagementView`). **`POST /api/provisioning/external-employees/search`**, **`POST /api/provisioning/external-departments/search`**, **`POST /api/provisioning/users/from-external-employee`** — unauthenticated **401**, non-admin **403**, success **200** with data; duplicate external key **409** `EXTERNAL_IDENTITY_CONFLICT`. Screen access: `ScreenAccessInterceptor` maps **`/api/provisioning/**`** to **user-management** OR **user-permission-hierarchy** (same as admin provisioning spec).
- **User Management v2 read scope (req 20260409)**: `ScreenAccessInterceptor` requires **`user-management-v2`** (or system admin) for **`GET /api/users`**, **`GET /api/departments/user-permission-hierarchy`**, and **`/api/user-management-v2/**`**. Effective read scope comes from `UserManagementReadScopeResolver` + `ScopeHelper` / `DepartmentScopeHelper` (same request attribute). Mutations under `/api/user-management-v2/**` validate write via `screenFunctions['user-management-v2'].write` and reject out-of-scope targets with **403** `FUNCTION_NOT_ALLOWED` where applicable.

## References

- `docs/contract.md`
- `specs/permission-group-hierarchy.spec.yaml`
- permission-related backend/frontend code in the current scope
