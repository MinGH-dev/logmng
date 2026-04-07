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
- Auth current-user payloads (`/api/auth/login` user payload, `/api/auth/check`, `/api/auth/me`) are the authoritative source for locked self-context display values on applicable self-scoped screens.
- The shared auth `selfContext` contract is `department`, `username`, `userId`. In API/UI, **userId** is **numeric** `app_user.id` (e.g. 20269999, 20260001). **`auth.login.mode`** (`application.yml` / **`application-{profile}.yml`**) selects exactly one path: **`local`** — `POST /api/auth/login` body **`userId`** (number) + **`password`** (vs `password_hash`); **`ad`** — body **`principal`** (string) + **`password`** (LDAP bind; no password stored in `app_user`). After AD success, **`app_user`** is resolved via **`app_user_external_identity`** + **`ext_employee`** (e.g. employee_number / email match to principal). Misconfiguration (invalid mode, `ad` without required **`auth.ad.*`**) is **fail-closed** (startup failure). `username` in selfContext is the **display name** (사용자명): `app_user.name` when present and not blank, otherwise `app_user.username`.
- **Zero-permission sessions** (`is_system_admin=false` and empty `allowedScreenIds`): protected APIs return **403**; allowed: `GET /api/auth/check`, `GET /api/auth/me`, `POST /api/auth/logout` (see `specs/external-identity-auth.spec.yaml` §5).
- For `activity-log`, effective `scope=self` is a hard backend boundary: force the current authenticated user and ignore widening inputs such as `department`, `departmentCode`, `username`, `userId`, `ipAddress`, and any server-only user-list fields.
- For `activity-log`, `scope=team` means "same-department allowlist first, optional filters second"; `scope=all` is the only legitimate cross-user search mode.
- Permission-group admin APIs record structured `permissionGroupAuditV1` in activity `action_detail` (allowlisted fields; `includeParams=false` on mutating endpoints — no raw body without a dedicated sanitizer). See `specs/activity-permission-group-audit.spec.yaml`.
- Approval-only roles must stay aligned with approver business rules and screen-access limits.

## References

- `docs/contract.md`
- `specs/permission-group-hierarchy.spec.yaml`
- `docs/requirements/TOPIC-INDEX.md`
