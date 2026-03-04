---
name: auth-permission-domain
description: >
  Auth and permission domain: is_system_admin, permission group (single per user), screen-based access
  (all screens including user-management), scope (self/all). Use when user asks about
  permissions, access control, is_system_admin, permission group, or screen access.
  Use for 403 Forbidden errors, 'Access Denied', '관리자만 접근할 수 있습니다',
  or when identifying which API/screen requires which permission.
  권한, 접근 제어, 관리자 권한, 권한 그룹, 화면 접근 제어 관련 질문 시 사용.
---

# Auth & Permission Domain

**Skill usage visibility**: When you use this skill to answer, state at the start of your response: `[Skill used: auth-permission-domain]`

Use for **permission, access control, and auth** in this repo. Scope: is_system_admin, permission group (single per user, req 20250304), screen-based access.

## Access check logic (single rule per contract/spec)

```
API request
    │
    ├─ is_system_admin = true? ──► ALLOW (bypass all checks)
    │
    └─ is_system_admin = false
           └─ required screen in allowedScreenIds? ──► ALLOW
           └─ else ──► 403 FORBIDDEN
```

**Key**: All screen APIs (including user-management, permission-groups, hierarchy) use the same rule: `is_system_admin` OR `allowedScreenIds` contains the required screen. Single source: `specs/permission-group-hierarchy.spec.yaml` §4.3, `docs/contract.md` §화면 기반 접근 제어.

## screenFunctions (per-screen function availability)

```
screenFunctions: Record<screenId, { read: boolean, write?: boolean, approve?: boolean }>
```

**Derivation rules** (AuthService.resolveScreenFunctions):
- **read**: always true if screen is in allowedScreenIds.
- **write**: only for management screens (user-management, department-approvers, user-permission-hierarchy, permission-group-management). When `permission_group_screen.write` is null → **derived as true** (read implies write). Explicit `write=false` in DB overrides.
- **approve**: only for search-history, pending-approvals. Requires (`decrypt_approver` OR `is_system_admin`). Explicit `approve=false` in DB overrides.

## Error code distinction (FORBIDDEN vs FUNCTION_NOT_ALLOWED)

| Layer | Code | Meaning |
|-------|------|---------|
| Screen access (interceptor or controller) | `FORBIDDEN` | User does not have the screen in allowedScreenIds |
| Function denied (write or approve) | `FUNCTION_NOT_ALLOWED` | User has screen access but lacks the specific function (write=false or not approver) |

For detailed API-to-permission-check mapping, see skill: `api-permission-map`.

## Quick reference

- **All screen APIs** (user-management, permission-groups, hierarchy, main, search-history, etc.): `is_system_admin` OR user has screen in `allowedScreenIds` (from user's single permission group).
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
| Permission group screen access (TC-05) | Path: `docs/requirements/20250227-permission-group-screen-menu-access.md` | §1, §3 TC-05 |
| Full list (전체 처리 이력) | Path: `docs/requirements/TOPIC-INDEX.md` | §permission | access-control — load only when user asks for comprehensive list |

## Code references

| Concern | Location |
|---------|----------|
| API path → screen (contract-compliant) | **backend/src/main/java/com/logmng/config/ScreenAccessInterceptor.java** |
| Controller access check (may diverge from contract) | **UserController**, **PermissionGroupController**, **DepartmentController** |
| Login, allowedScreenIds | **backend/src/main/java/com/logmng/service/AuthService.java** |
| Session, /api/auth/me | backend/src/main/java/com/logmng/controller/AuthController.java |
| Frontend access check (may diverge from contract) | **frontend/src/components/UserManagement/UserManagement.js** |
| Menu, canAccessView | frontend/src/App.js, frontend/src/constants/menuTree.js |

## Before answering

1. For screen access (user-management, permission-groups, hierarchy): Answer per contract/spec — `is_system_admin` OR `allowedScreenIds` contains the screen. The user's single permission group grants access.
2. For "user has group X but 403": Check (1) `allowedScreenIds` includes the required screen; (2) implementation may use `isSystemAdmin` only (bug). Reference: `specs/permission-group-hierarchy.spec.yaml` §4.3.
3. For scope questions: Only activity-log, statistics, search-history use scope. Others ignore.
4. **Requirement traceability**: When explaining design or "처리 이력", cite requirement doc (path + §section). Use **core** refs above; do **not** load full doc. For "전체 처리 이력", load `docs/requirements/TOPIC-INDEX.md` §permission only. Do **not** invoke RequirementsPastSearch for Q&A.

## References

- Contract: docs/contract.md
- Spec: specs/permission-group-hierarchy.spec.yaml
- Improvement design: docs/SKILL-DOCUMENT-IMPROVEMENT-DESIGN.md
- API permission enforcement map: `.cursor/skills/api-permission-map/SKILL.md`
