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
- The shared auth `selfContext` contract is `department`, `username`, `userId`. In API/UI, **userId** is **numeric** `app_user.id` (e.g. 20269999, 20260001). The **login identifier** is `app_user.username` (e.g. "admin", "user1"). `username` is the **display name** (사용자명): `app_user.name` when present and not blank, otherwise `app_user.username`. Login is by login ID (app_user.username); responses expose userId as numeric id.
- For `activity-log`, effective `scope=self` is a hard backend boundary: force the current authenticated user and ignore widening inputs such as `department`, `departmentCode`, `username`, `userId`, `ipAddress`, and any server-only user-list fields.
- For `activity-log`, `scope=team` means "same-department allowlist first, optional filters second"; `scope=all` is the only legitimate cross-user search mode.
- Approval-only roles must stay aligned with approver business rules and screen-access limits.

## References

- `docs/contract.md`
- `specs/permission-group-hierarchy.spec.yaml`
- `docs/requirements/TOPIC-INDEX.md`
