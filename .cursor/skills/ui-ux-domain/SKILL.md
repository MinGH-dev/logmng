---
name: ui-ux-domain
description: Menu tree, screen access, admin-only visibility, and sidebar/view behavior.
---

# UI / UX domain

Use this skill for questions about menu structure, screen visibility, `adminOnly`, `canAccessView`, sidebar behavior, and permission-driven screen access.

## Core points

- `is_system_admin` has full screen access.
- Non-admin access depends on `allowedScreenIds` and screen-specific rules.
- Admin-only menu groups should remain hidden for non-admin users.
- Menu and view access must stay aligned with `docs/contract.md` and frontend menu definitions.

## References

- `docs/contract.md`
- `docs/design/search-fields-by-screen.md`
- `docs/design/search-field-definition-items.md`
- `frontend/src/constants/menuTree.js`
