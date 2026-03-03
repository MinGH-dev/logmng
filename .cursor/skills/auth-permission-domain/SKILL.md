---
name: auth-permission-domain
description: >
  Auth and permission domain: is_system_admin, permission groups, admin-only APIs,
  screen-based access, scope (self/all). Use when user asks about permissions,
  access control, admin-only, is_system_admin, permission group, or screen access.
  Use for 403 Forbidden errors, 'Access Denied', '관리자만 접근할 수 있습니다',
  or when identifying which API/screen requires admin privileges.
  권한, 접근 제어, 관리자 권한, 권한 그룹, 화면 접근 제어 관련 질문 시 사용.
---

# Auth & Permission Domain

**Skill usage visibility**: When you use this skill to answer, state at the start of your response: `[Skill used: auth-permission-domain]`

Use for **permission, access control, and auth** in this repo. Scope: is_system_admin, permission groups, screen-based access, admin-only APIs.

## Access check logic (Admin vs Screen)

```
API request
    │
    ├─ is_system_admin = true? ──► ALLOW (bypass all checks)
    │
    └─ is_system_admin = false
           │
           ├─ Admin-only API? (/api/users, /api/permission-groups, /api/departments)
           │      └─► 403 FORBIDDEN (permission groups cannot grant access)
           │
           └─ Screen-based API? (main, search-history, activity-log, etc.)
                  └─ allowedScreenIds contains required screen? ──► ALLOW
                  └─ else ──► 403 FORBIDDEN
```

**Key**: Admin-only APIs require `is_system_admin` only. Permission groups apply only to screen-based APIs.

## Quick reference

- **Admin-only APIs** (user management, permission groups, hierarchy): Require `is_system_admin = true` in DB. Permission groups **cannot** grant access to these APIs.
- **Screen access** (non-admin screens): `is_system_admin` OR user has screen in `allowedScreenIds` (from permission groups).
- **Scope** (activity-log, statistics, search-history only): `self` = own data; `all` = full. `is_system_admin` → always full.
- **is_system_admin**: DB column `app_user.is_system_admin`. Not settable via API; init-data or DB direct edit only.

## When to use

- Permission or access control questions
- 403 Forbidden, "관리자만 접근", "Access Denied"
- 권한 그룹, 화면 접근, is_system_admin vs permission group confusion
- Screen IDs, API↔screen mapping

## Document references

| Question type | Document | Section (exact header) |
|---------------|----------|------------------------|
| Screen IDs, API mapping | Path: `specs/permission-group-hierarchy.spec.yaml` | `# 4. Screen IDs and screen-based access` (§4.1, §4.2, §4.3) |
| Access rules summary | Path: `docs/contract.md` | `## 화면 기반 접근 제어 (Screen-based access)` |
| System admin protection | Path: `docs/contract.md` | `## 시스템 관리자 보호 (System administrator protection)` |
| is_system_admin detail | Path: `docs/requirements/20250303-remove-role-single-admin.md` | §1, §2 |

## Code references

| Concern | Location |
|---------|----------|
| API path → screen | **backend/src/main/java/com/logmng/config/ScreenAccessInterceptor.java** |
| Admin-only check | **UserController**, **PermissionGroupController**, **DepartmentController** |
| Login, allowedScreenIds | **backend/src/main/java/com/logmng/service/AuthService.java** |
| Session, /api/auth/me | backend/src/main/java/com/logmng/controller/AuthController.java |
| Frontend isAdmin check | **frontend/src/components/UserManagement/UserManagement.js** |
| Menu, canAccessView | frontend/src/App.js, frontend/src/constants/menuTree.js |

## Before answering

1. For admin-only (user-management, permission-groups, hierarchy): Answer with `is_system_admin` requirement. Permission groups do **not** grant access.
2. For "user has group X but 403": Likely missing `is_system_admin` in DB. Suggest `UPDATE app_user SET is_system_admin = true WHERE username = 'X'`.
3. For scope questions: Only activity-log, statistics, search-history use scope. Others ignore.

## References

- Contract: docs/contract.md
- Spec: specs/permission-group-hierarchy.spec.yaml
- Improvement design: docs/SKILL-DOCUMENT-IMPROVEMENT-DESIGN.md
