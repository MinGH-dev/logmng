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
- Approval-only roles must stay aligned with approver business rules and screen-access limits.

## References

- `docs/contract.md`
- `specs/permission-group-hierarchy.spec.yaml`
- `docs/requirements/TOPIC-INDEX.md`
