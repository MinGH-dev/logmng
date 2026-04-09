---
name: auth-permission-domain
description: Auth, permission-group, screen access, and system-admin behavior.
---

# Auth / permission domain

Use this skill for questions about permissions, screen access, `is_system_admin`, permission groups, and access-denied behavior.

## Core points

- Screen access is determined by system-admin status or `allowedScreenIds`, plus any screen-specific rules.
- Decrypt capability requires the appropriate screen/function permission when that rule applies.
- Scope values such as `self`, `team`, and `all` affect list/view range; they do not automatically redefine every business rule.
- **User Management v2** (`user-management-v2`): read/list scope is in `screenScopes['user-management-v2']` (`self` / `team` / `all`, default `team`). It applies to shared GETs used by the v2 UI (`GET /api/users`, `GET /api/departments/user-permission-hierarchy`, `GET /api/user-management-v2/quick-entry/options`, etc.). System admins effectively have `all`. Legacy `user-management` without v2 does not use this key for those shared APIs.
- Auth current-user payloads (`/api/auth/login` user payload, `/api/auth/check`, `/api/auth/me`) are the authoritative source for locked self-context display values on applicable self-scoped screens.
- The shared auth `selfContext` contract is `department`, `username`, `userId`, **`employeeNumber`**. **`userId`** is the **technical** numeric `app_user.id` (joins, paths, integrations). **`employeeNumber`** is the **human-facing** 사번 (`app_user.employee_number`, string or null); UI labels “사용자 ID” / “User ID” in login and locked self blocks use **사번** from `employeeNumber`, not the PK. **`auth.login.mode`** selects: **`local`** — `POST /api/auth/login` body must include exactly one of **`employeeNumber`** (string, trimmed; primary) or **deprecated `userId`** (number, legacy) **plus** **`password`**; sending both or neither is **`INVALID_INPUT`**. **`ad`** — body **`principal`** + **`password`** only; **`employeeNumber`/`userId` in the body are rejected** (`INVALID_INPUT`). Directory check is **JNDI simple bind** on **`auth.ad.ldap-url`** with UPN = principal if it contains `@`, else **`principal + "@" + auth.ad.domain`** (env **`AUTH_AD_DOMAIN`**). **`manager-dn` / `user-search-*`** are legacy optional YAML keys and **not** used for bind. After AD success, **`app_user`** is resolved via **`ExternalIdentityService.findAppUserIdForDirectoryPrincipal`**, then responses expose **`selfContext.employeeNumber`** from `app_user`. Misconfiguration (invalid mode, `ad` without required **`auth.ad.ldap-url`** / **`auth.ad.domain`**) is **fail-closed** (startup failure). `username` in selfContext is the **display name** (사용자명): `app_user.name` when present and not blank, otherwise `app_user.username`.
- **Zero-permission sessions** (`is_system_admin=false` and empty `allowedScreenIds`): protected APIs return **403**; allowed: `GET /api/auth/check`, `GET /api/auth/me`, `POST /api/auth/logout` (see `specs/external-identity-auth.spec.yaml` §5).
- For `activity-log`, effective `scope=self` is a hard backend boundary: force the current authenticated user and ignore widening inputs such as `department`, `departmentCode`, `username`, `userId`, `ipAddress`, and any server-only user-list fields.
- For `activity-log`, `scope=team` means "same-department allowlist first, optional filters second"; `scope=all` is the only legitimate cross-user search mode.
- Permission-group admin APIs record structured `permissionGroupAuditV1` in activity `action_detail` (allowlisted fields; `includeParams=false` on mutating endpoints — no raw body without a dedicated sanitizer). See `specs/activity-permission-group-audit.spec.yaml`.
- Approval-only roles must stay aligned with approver business rules and screen-access limits.

## References

- `docs/contract.md`
- `specs/permission-group-hierarchy.spec.yaml`
- `docs/requirements/TOPIC-INDEX.md`
