# 20250303-remove-role-single-admin-bugfix-1 — Backend: isSystemAdmin in login/me, PUT 410, remove role from responses

**Parent requirement ID**: `20250303-remove-role-single-admin`  
**Bugfix sequence**: 1

## 1. Discovery

- **When**: During QA verification (restart + health check + §3 test cases)
- **What failed**:
  - TC-01: System admin sees "관리자만 접근할 수 있습니다" instead of user list — frontend treats admin as non-admin because `isSystemAdmin` not in login/me response
  - TC-03: PUT /api/users/{userId} returns 200 with user data; expected 410 Gone with `code: "ENDPOINT_REMOVED"`
  - TC-04: GET /api/users response includes `role` field; contract says no role
  - TC-05: GET /api/auth/me does not include `isSystemAdmin` in response
  - TC-10: Hierarchy API (`GET /api/departments/user-permission-hierarchy`) user objects include `role`; expected no role, only `isSystemAdmin`

## 2. Error scope

- **Failure scope**: **backend**
- **Layer**: backend
- **Symptom**: Login/auth APIs do not expose `isSystemAdmin`; PUT /api/users still processes role updates; user list and hierarchy responses expose `role`
- **Impact**: Admin access broken (frontend cannot identify system admin); role update API not deprecated; contract violation (role in responses)

**변경 파일 목록 (Backend bugfix 적용 후 확정)**:
- Backend 코드: AuthService, AuthController, UserController, LoginResponse, UserListItemResponse, UserPermissionSummary — 이미 구현됨 (변경 없음)
- `docs/api-definition.md` — §12, §14, §14.8, §14.9: role→is_system_admin, role 제외 문서화
- `specs/permission-group-hierarchy.spec.yaml` — §1.2, §4.3: role 제외, is_system_admin 문서화

## 3. Cause (estimated)

1. **LoginResponse / AuthController**: `isSystemAdmin` not added to login response or GET /api/auth/me; or AuthService not loading/setting it
2. **UserController**: PUT /api/users/{userId} still invokes role-update logic instead of returning 410 Gone
3. **UserListItemResponse, UserPermissionSummary**: `role` field still serialized in JSON; need `@JsonIgnore` or removal per contract
4. **Hierarchy API (UserPermissionSummary in department tree)**: Same — `role` still in user objects in response

### 3.1 Re-verification test cases

After Backend fix, QA re-runs these. All must pass before closing this bugfix.

| ID | Scenario | Expected result |
|----|----------|-----------------|
| TC-01 | System admin opens user management | User list shown; no role column; system admin badge visible; **not** "관리자만 접근할 수 있습니다" |
| TC-02 | Non-admin opens user management | 403 or redirect; "관리자만 접근 가능" message |
| TC-03 | PUT /api/users/{userId} with role body | **410 Gone**; `{ "success": false, "error": "...", "code": "ENDPOINT_REMOVED" }` |
| TC-04 | GET /api/users as system admin | 200 OK; response does **not** include `role` field |
| TC-05 | GET /api/auth/me as system admin | Response includes `isSystemAdmin: true` |
| TC-06 | Login as system admin; access admin-only API | 200 OK |
| TC-07 | Login as non-admin; access GET /api/users | 403 Forbidden |
| TC-08 | User management: permission groups, approver, rank, position | All editable; no role column |
| TC-09 | System admin badge on designated user | Badge visible; no role dropdown |
| TC-10 | GET /api/departments/user-permission-hierarchy | User objects in response **exclude** `role`; include `isSystemAdmin` |

## 4. Action (implemented)

- **AuthService / LoginResponse**: Ensure login loads `is_system_admin` from DB; set `LoginResponse.setIsSystemAdmin(...)`; session stores `isSystemAdmin` — already implemented
- **AuthController**: GET /api/auth/me returns `isSystemAdmin` in response (from session or userInfo)
- **UserController**: PUT /api/users/{userId} — return 410 Gone with `{ "success": false, "error": "...", "code": "ENDPOINT_REMOVED" }`; do not call update logic
- **UserListItemResponse**: Exclude `role` from JSON (e.g. `@JsonIgnore` on getter or remove field from serialization)
- **UserPermissionSummary** (used in hierarchy): Same — exclude `role` from JSON
- **Contract / api-definition**: Confirm §2.1 login, §2.4 /api/auth/me, §7.1, §14.9 document `isSystemAdmin` and no `role`

## 5. Verification

- Re-run TC-01, TC-03, TC-04, TC-05, TC-10 after Backend fix
- TC-02, TC-06, TC-07, TC-08, TC-09: Re-verify after fix
- When all pass, QA updates parent §5 and commits

**Result (2025-03-03)**: All TC-01–TC-10 pass. Parent §5 updated. Committed.

## 6. Error remedy result

- **Cause**: LoginResponse/me lacked `isSystemAdmin`; PUT still processed role; UserListItemResponse/UserPermissionSummary exposed `role`.
- **Action**: Backend added `isSystemAdmin` to login/me; PUT returns 410 Gone; `@JsonIgnore` on role in DTOs.
- **Verification**: Re-run passed. Bugfix closed.
