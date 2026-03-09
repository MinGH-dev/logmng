---
name: api-permission-map
description: >
  API permission enforcement map: every API endpoint → controller → permission check method
  → error code on denial. Use when writing test plans for permission verification, when
  asking about 403/FUNCTION_NOT_ALLOWED/FORBIDDEN for specific APIs, or when planning
  permission-group function verification. API 권한 검증 테스트 계획, 403 에러 코드 매핑,
  write/approve 기능 검증 관련 작업 시 사용.
---

# API Permission Enforcement Map

**Skill usage visibility**: When you use this skill to answer, state at the start of your response: `[Skill used: api-permission-map]`

Use when writing **test plans for permission verification**, determining **expected error codes per API**, or verifying **write/approve function enforcement**.

## Three-layer access control

```
Request → Layer 1: ScreenAccessInterceptor (screen access)
        → Layer 2: Controller method (function-level: write, approve)
        → Layer 3: Service (business rules: canApproveForRequester, scope)
```

- **Layer 1** denies with `FORBIDDEN` (screen not in allowedScreenIds).
- **Layer 2** denies with `FUNCTION_NOT_ALLOWED` (screen accessible but function denied).
- **Layer 3** denies with business-specific codes (e.g. `DECRYPTION_NOT_APPROVED`).
- **is_system_admin** bypasses all layers.

## Decrypt-gated APIs (req 20260306)

Controller: `DecryptController`
Check method: `authService.hasDecryptForMain(request)` (after session check)
Logic: is_system_admin OR (main in allowedScreenIds AND screenFunctions.main.decrypt === true)
Denial code: `FUNCTION_NOT_ALLOWED`

| Method | Path | Notes |
|--------|------|-------|
| POST | `/api/logs/decrypt/{logType}` | Single row decrypt; requires decrypt permission on main. Then DECRYPTION_NOT_APPROVED / ROW_NOT_IN_APPROVED_SNAPSHOT apply in service. |

## Approve-gated APIs

Controller: `SearchHistoryController`
Check method: `requireApproverOrAdmin(request)`
Logic: (decrypt_approver OR is_system_admin) AND screenFunctions.approve
Denial code: `FUNCTION_NOT_ALLOWED`

**Approval scope**: 승인 가능 범위(누가 승인 대상인지)는 부서(canApproveForRequester)로 고정되며, 권한 그룹 scope는 목록(조회) 범위만 적용됨. (spec §Scope values.)

| Method | Path | Notes |
|--------|------|-------|
| GET | `/api/search-history/pending` | Pending list; also gated by approve |
| POST | `/api/search-history/{id}/approve` | Approve request |
| POST | `/api/search-history/{id}/reject` | Reject request |

## Write-gated APIs

Controller: `PermissionGroupController`
Check method: `requireWriteForManagement(request)` (after `requireUserManagementAccess`)
Logic: screenFunctions[user-management OR user-permission-hierarchy].write === true
Denial code: `FUNCTION_NOT_ALLOWED`

| Method | Path | Notes |
|--------|------|-------|
| POST | `/api/permission-groups` | Create group |
| PUT | `/api/permission-groups/{id}` | Update group |
| DELETE | `/api/permission-groups/{id}` | Delete group |
| POST | `/api/permission-groups/{id}/users` | Assign user to group |
| DELETE | `/api/permission-groups/{id}/users/{userId}` | Unassign user from group |

## Screen-access-only APIs (no function-level check)

These APIs check **screen access** (Layer 1 interceptor or controller `requireUserManagementAccess` / `requireDepartmentAccess`) but do NOT check write/approve function. Denial code is `FORBIDDEN`, not `FUNCTION_NOT_ALLOWED`.

| Method | Path | Controller | Check method | Screen(s) required |
|--------|------|------------|-------------|-------------------|
| GET | `/api/users` | UserController | requireUserManagementAccess | user-management OR user-permission-hierarchy |
| GET | `/api/permission-groups` | PermissionGroupController | requireUserManagementAccess | user-management OR user-permission-hierarchy |
| GET | `/api/permission-groups/{id}` | PermissionGroupController | requireUserManagementAccess | user-management OR user-permission-hierarchy |
| GET | `/api/permission-groups/{id}/users` | PermissionGroupController | requireUserManagementAccess | user-management OR user-permission-hierarchy |
| GET | `/api/departments` | DepartmentController | requireDepartmentAccess | department-approvers OR user-permission-hierarchy |
| GET | `/api/departments/user-permission-hierarchy` | DepartmentController | requireUserManagementAccess | user-management OR user-permission-hierarchy |

## Scope-enforced APIs (no function check, scope only)

| Method | Path | Controller | Screen | Scope enforcement |
|--------|------|------------|--------|-------------------|
| GET | `/api/search-history` | SearchHistoryController | search-history | self/team/all via ScopeHelper; list only |
| GET | `/api/activity-log/*` | UserActivityLogController | activity-log | self/all via ScopeHelper |
| GET | `/api/statistics/*` | ActivityStatisticsController | statistics | self/all via ScopeHelper |

## Requester-only APIs (no admin/scope bypass)

These APIs allow access **only when** the record's `user_id` equals the current user. **is_system_admin** and scope (all/team) do **not** bypass; only the requester can call them. Denial: 403, FUNCTION_NOT_ALLOWED. Ref: req 20260304-search-history-action-requester-only, search-history-decrypt-domain SKILL.

| Method | Path | Controller | Notes |
|--------|------|------------|--------|
| GET | `/api/search-history/{id}` | SearchHistoryController | Detail for re-search; requester only |
| POST | `/api/search-history/{id}/re-request` | SearchHistoryController | Re-request expired; requester only |

## Deprecated endpoints

| Method | Path | Response | Notes |
|--------|------|----------|-------|
| PUT | `/api/users/{userId}` | **410 Gone** (`ENDPOINT_REMOVED`) | No write function check; always returns 410 |
| POST | `/api/users/approvers` | **410 Gone** | Removed |
| DELETE | `/api/users/approvers` | **410 Gone** | Removed |

## No permission check (public or interceptor-excluded)

| Method | Path | Controller | Notes |
|--------|------|------------|-------|
| POST | `/api/auth/login` | AuthController | Public |
| POST | `/api/auth/logout` | AuthController | Public |
| GET | `/api/auth/check` | AuthController | Public |
| GET | `/api/auth/me` | AuthController | Auth only (session) |
| GET | `/api/health` | HealthController | Excluded from interceptor |
| GET | `/api/db/*` | DbTestController | Excluded from interceptor |
| GET | `/api/log-types` | LogTypeController | Excluded from interceptor |
| GET | `/api/search/suggest` | SearchSuggestController | screen: main |

## ScreenAccessInterceptor path-to-screen mapping

Source: `backend/src/main/java/com/logmng/config/ScreenAccessInterceptor.java`

| Path pattern | Required screen(s) |
|-------------|-------------------|
| `/api/departments/user-permission-hierarchy` | user-management OR user-permission-hierarchy |
| `/api/departments*` | department-approvers OR user-permission-hierarchy |
| `/api/permission-groups*` | user-management OR user-permission-hierarchy |
| `/api/search-history/pending*` | pending-approvals |
| `/api/search-history/{id}/approve*` | pending-approvals |
| `/api/search-history/{id}/reject*` | pending-approvals |
| `/api/search-history*` | search-history |
| `/api/activity-log*` | activity-log |
| `/api/statistics*` | statistics |
| `/api/users*` | user-management |
| `/api/logs/db-refactored*` | main |
| `/api/logs/decrypt*` | main (and screenFunctions.main.decrypt required in controller; see Decrypt-gated APIs) |
| `/api/search*` | main |

## Gaps (no write enforcement where expected)

- **UserController**: No `requireWriteForManagement()` on any endpoint. `PUT /api/users/{userId}` returns 410 Gone. Actual user write operations (assign/unassign) are in PermissionGroupController.
- **DepartmentController**: No POST/PUT/DELETE endpoints exist. Only GET. Write enforcement deferred until CRUD is implemented.
- **screenFunctions derivation**: `read implies write` for management screens when `permission_group_screen.write` is null. To test write=false, explicit `write=false` must be set in DB.

## Error code summary

| Denial layer | HTTP | Code | When |
|-------------|------|------|------|
| Screen access (interceptor) | 403 | `FORBIDDEN` | User does not have screen in allowedScreenIds |
| Screen access (controller) | 403 | `FORBIDDEN` | Controller's requireUserManagementAccess/requireDepartmentAccess fails |
| Function (approve) | 403 | `FUNCTION_NOT_ALLOWED` | User has screen but not decrypt_approver and not is_system_admin |
| Function (write) | 403 | `FUNCTION_NOT_ALLOWED` | User has screen but screenFunctions write=false |
| Deprecated | 410 | `ENDPOINT_REMOVED` | PUT /api/users/{userId} |

## Requirement doc completeness checklist

When writing a **requirement document** that involves permission verification, access control, or function-level checks (write/approve), apply this checklist before finalizing §3. Check every item:

- [ ] **System admin bypass TC**: At least one TC where `is_system_admin` user calls gated APIs → expect success (bypass all checks).
- [ ] **Explicit deny edge cases**: When derivation rules exist (null → true for write on management screens), include a TC with explicit deny value (`write=false` or `approve=false` in DB) to verify override.
- [ ] **Error code distinction**: Separate TCs for `FORBIDDEN` (no screen) vs `FUNCTION_NOT_ALLOWED` (screen OK but function denied).
- [ ] **Known gaps noted**: If a controller lacks expected write/approve endpoints, note in §2. See §Gaps above.
- [ ] **§5 curl — one per every TC**: Provide login (one `curl -c <role>.txt` per test role) + one `curl -b <role>.txt -w "\nHTTP %{http_code}\n"` per **every TC** in §3. Do NOT provide "example pattern" for a few TCs. QA must copy-paste and run.
- [ ] **Test data SQL**: Include **executable SQL** (INSERT/UPDATE with table, column, value) for setting explicit deny values. Not descriptions — actual statements:

```sql
-- write=false (overrides null→true derivation for management screens)
UPDATE permission_group_screen SET write = false
WHERE permission_group_id = <id> AND screen_id = 'user-permission-hierarchy';

-- approve=false (overrides approver derivation)
UPDATE permission_group_screen SET approve = false
WHERE permission_group_id = <id> AND screen_id = 'pending-approvals';
```

## When to use

- Writing test plans for permission/function verification
- Determining expected HTTP status and error code for a specific API
- Planning holder vs non-holder test scenarios
- Verifying write/approve enforcement coverage
- 403 FORBIDDEN vs FUNCTION_NOT_ALLOWED distinction

## Related skills

- `auth-permission-domain`: Conceptual permission model (is_system_admin, single permission group per user, screen access)
- `error-codes-domain`: Full error code list (api-definition.md §11)

## Code references

| Concern | Location |
|---------|----------|
| Screen-to-path interceptor | `backend/src/main/java/com/logmng/config/ScreenAccessInterceptor.java` |
| Approve check | `backend/src/main/java/com/logmng/controller/SearchHistoryController.java` → requireApproverOrAdmin |
| Write check | `backend/src/main/java/com/logmng/controller/PermissionGroupController.java` → requireWriteForManagement |
| screenFunctions computation | `backend/src/main/java/com/logmng/service/AuthService.java` → resolveScreenFunctions |
| hasWriteForManagementScreens | `backend/src/main/java/com/logmng/service/AuthService.java` |
| hasApproveForSearchHistory | `backend/src/main/java/com/logmng/service/AuthService.java` |

## References

- Contract: docs/contract.md §화면 기반 접근 제어
- Spec: specs/permission-group-hierarchy.spec.yaml §4.3, §4.4
- Requirement: docs/requirements/20250303-screen-function-availability.md
- Verification: docs/requirements/20250304-permission-group-function-verification.md
