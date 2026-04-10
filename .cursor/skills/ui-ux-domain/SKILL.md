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
- **Sidebar / view gates**: use **`frontend/src/constants/screenAccessPolicy.js`** (`canShowAdminSidebarChild`, `canAccessView`, etc.) together with **`frontend/src/constants/menuTree.js`**. Run **`npm run verify:screen-access`** from `frontend/` to catch allowlist drift vs `ScreenConstants` (req `20260410-screen-access-menu-api-consistency`).

## Login entry points

- Primary and alternate entry use the **same bundle**; see `frontend/src/config/runtimeApi.js` (hash routes `/#/login`, `/#/entry`, `PUBLIC_URL` / proxy notes).
- **Zero screen permissions** after auth: a blocking **NoPermissionDialog** is shown (no main shell / sidebar); confirm runs logout and returns to login with history replace. Exact copy is fixed in `NoPermissionDialog.js` per requirement `20260407-external-dept-employee-ad-login`.

## References

- `docs/contract.md`
- `docs/design/search-fields-by-screen.md`
- `docs/design/search-field-definition-items.md`
- `frontend/src/constants/menuTree.js`
- `frontend/src/constants/screenAccessPolicy.js`
