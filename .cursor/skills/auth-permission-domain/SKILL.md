---
name: auth-permission-domain
description: >
  Auth and permission domain: is_system_admin, permission group (single per user), screen-based access
  (all screens including user-management), scope (self/team/all). Use when user asks about
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

## Approval-only permission group

**Definition (condition-based, group name irrelevant)**: A permission group is **approval-only** when it satisfies ALL of these conditions:
- `allowedScreenIds` does **NOT** include `main` (no log search access)
- `allowedScreenIds` **includes** `pending-approvals` (approval screen access)
- (Optional) `approve=true` for the pending-approvals screen (can perform approve/reject)

This definition applies to **any** group matching the condition — e.g. APPROVE_USER, TEAM_APPROVER, REGIONAL_APPROVER. The group name/code is irrelevant.

**Business rule**: Team leaders (팀장) must NOT view logs. They act as decrypt approvers only. Create a permission group (e.g. `APPROVE_USER`) matching the approval-only condition, and register the team leader in `decrypt_approver` table. This gives:
- ✅ Can see pending approval requests (pending-approvals screen)
- ✅ Can approve/reject decrypt requests (decrypt_approver + approve function)
- ❌ Cannot search/view logs (no `main` screen)
- ❌ Cannot view search history (no `search-history` screen, unless explicitly granted)

**Applicable rules (for all approval-only groups, regardless of name)**:
- **Redirect**: After login, if `main` is not in `allowedScreenIds`, redirect to the first allowed screen (e.g. pending-approvals).
- **Menu**: Sidebar shows only screens in `allowedScreenIds`. No `main` → no "로그 검색", "통계", etc.
- **API**: `main` not in `allowedScreenIds` → log search API returns 403. `pending-approvals` + `decrypt_approver` → approve/reject API allowed.
- **Action hiding**: Approvers should ONLY have approval-related actions. Non-approval actions on screens they access must be hidden or disabled:
  - `search-history` screen → **재조회** (re-search): hidden for users without `main` screen
  - `search-history` screen → **재요청** (re-request): hidden for users without `main` screen
  - `search-history` screen → **자세히 보기** (view detail): allowed (provides approval context)
  - `pending-approvals` screen → **승인/반려**: allowed (core approver function, gated by `canApprove`)

**General rule**: If a screen action button navigates to or depends on another screen the user doesn't have access to, that button must be hidden. Use `allowedScreenIds.includes('main')` as the condition for search-related actions.

**Constraint**: Frontend must support users whose `allowedScreenIds` does NOT include `main`. The initial view and fallback must redirect to the first allowed screen, not hardcode `main`. See req `20260304-approve-only-permission-group`.

## Quick reference

- **All screen APIs** (user-management, permission-groups, hierarchy, main, search-history, etc.): `is_system_admin` OR user has screen in `allowedScreenIds` (from user's single permission group).
- **Scope** (activity-log, statistics, search-history only): `self` = own data; `team` = same department (default when omitted); `all` = full. `is_system_admin` → always full.
- **is_system_admin**: DB column `app_user.is_system_admin`. Not settable via API; init-data or DB direct edit only.
- **Approval-only permission group**: Any group where `allowedScreenIds` has no `main` + has `pending-approvals` (e.g. APPROVE_USER, TEAM_APPROVER). Same redirect/menu/API rules apply regardless of group name. Must be in `decrypt_approver` to approve. Frontend must not assume `main` is always available.

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
