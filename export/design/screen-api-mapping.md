# Screen–API Mapping (for design reference)

Each **screen (view)** and the **APIs it uses**. Use when designing per-screen layouts so that search/filter fields and result columns align with the request/response of these APIs. Access: user must have the screen in `allowedScreenIds` or be `is_system_admin`. Source: `specs/permission-group-hierarchy.spec.yaml` §4.3, frontend `App.js`, `menuTree.js`, and service/component API calls.

---

## Screen ID list (allowed)

| screen_id | Description (menu label) |
|-----------|--------------------------|
| main | 검색하기 (log search main) |
| search-history | 검색 이력 |
| activity-log | 활동 이력 |
| statistics | 활동로그 통계 |
| pending-approvals | 승인 대기 |
| user-management | 사용자 관리 |
| user-permission-hierarchy | 사용자 권한 계층 (redirect to user-management in UI) |
| permission-group-management | 권한 그룹 관리 |

---

## 1. main (검색하기)

**Required permission**: `allowedScreenIds` includes `main` or is_system_admin.

| Purpose | API | Method | Request/response notes |
|--------|-----|--------|-------------------------|
| Log type list | /api/log-types | GET | id, name, description, tables |
| Log type fields (java_fw_imglog) | /api/log-types/{typeId}/fields | GET | For advanced search field metadata |
| Search (pb_feplog / java_fw_imglog) | /api/logs/db-refactored/search | POST | LogDbSearchRequest: startDate, endDate, logType, mediaCode, tr_code, loginId, application, servicegroup, service, datastring, headerstring, keywords, decryptData, page, pageSize, sortField, sortDirection |
| Advanced search | /api/logs/db-refactored/advanced-search | POST | AdvancedSearchRequest (java_fw_imglog) |
| Suggest | /api/search/suggest | GET | logType, context, prefix, fieldName |
| Detail | /api/logs/db-refactored/{logType}/{type}/{identifier} | GET | Single row |
| Decrypt (single row) | /api/logs/decrypt/{logType} | POST | guid, searchHistoryId (main + screenFunctions.main.decrypt) |
| Save search for approval | /api/search-history | POST | logType, searchParams (when user requests decryption flow) |

**Scope**: No scope (main is not user-context list). **Functions**: read; decrypt only if screenFunctions.main.decrypt is true.

**Design**: Search form fields must match LogDbSearchRequest / AdvancedSearchRequest. Field widths and maxLength should follow `db-definition.md` (e.g. media_code 10, tr_code 20, application/service 256).

---

## 2. search-history (검색 이력)

**Required permission**: `allowedScreenIds` includes `search-history` or is_system_admin. **Scope**: self | team | all (list/detail only; approval scope is department).

| Purpose | API | Method | Request/response notes |
|--------|-----|--------|-------------------------|
| List | /api/search-history | GET | page, pageSize, sortField, sortDirection → data[], pagination |
| Detail | /api/search-history/{id} | GET | id in path |
| Re-request (expired) | /api/search-history/{id}/re-request | POST | id in path |
| Re-search (navigate to main with params) | (use stored searchParams; call /api/logs/db-refactored/search or advanced-search from main) | — | — |

**Design**: List columns from GET /api/search-history response (id, requestedAt, expiresAt, approvalStatus, searchParamsSummary, approvedBy, rejectedBy, etc.). No search/filter API on this screen; optional client-side filter by status. Date/time and status field widths from `db-definition.md` (search_history).

---

## 3. activity-log (활동 이력)

**Required permission**: `allowedScreenIds` includes `activity-log` or is_system_admin. **Scope**: self | team | all; when self, user/department/ip filters hidden and backend overrides to current user.

| Purpose | API | Method | Request/response notes |
|--------|-----|--------|-------------------------|
| Search | /api/activity-log/search | POST | UserActivityLogSearchRequest: startDate, endDate, userId, username, department, actionType, ipAddress, page, pageSize, sortField, sortDirection |
| Detail | /api/activity-log/{id} | GET | id in path |
| Department list (for filter) | /api/statistics/departments or /api/departments (flat) | GET | Options for department select |
| User list (for filter, if any) | /api/statistics/users or equivalent | GET | Options for userId/username (when scope ≠ self) |

**Design**: Search form fields must match UserActivityLogSearchRequest. Field sizes: user_id/username 100, action_type 50, ip_address 45, department from department.code/name (50/200). When scope=self, hide “user” block and “extra” block (actionType, ipAddress) per project design rules.

---

## 4. statistics (활동로그 통계)

**Required permission**: `allowedScreenIds` includes `statistics` or is_system_admin. **Scope**: self | team | all; when self, user/department/ip filters hidden.

| Purpose | API | Method | Request/response notes |
|--------|-----|--------|-------------------------|
| Daily stats | /api/statistics/activity/daily | GET | startDate, endDate, logType, userId, department, ip, username |
| Monthly stats | /api/statistics/activity/monthly | GET | year, month, same filters |
| All users stats | /api/statistics/activity/users/all | GET | startDate, endDate, same filters |
| Export | /api/statistics/activity/export | GET | type (daily/monthly), same filters → blob |
| Log type list | /api/log-types (or statistics-specific) | GET | For logType filter |
| Departments | /api/statistics/departments | GET | For department filter |
| Users | /api/statistics/users | GET | For user filter |
| IPs | /api/statistics/ips | GET | For IP filter |

**Design**: Filter panel fields must match the query params above. Reuse same user-context block and extra block (IP) sizing as activity-log; when scope=self hide user and extra blocks.

---

## 5. pending-approvals (승인 대기)

**Required permission**: `allowedScreenIds` includes `pending-approvals` or is_system_admin. **Scope**: self (own requests only) | team | all. **Function**: approve only if screenFunctions['pending-approvals'].approve === true (and user is decrypt_approver or is_system_admin).

| Purpose | API | Method | Request/response notes |
|--------|-----|--------|-------------------------|
| Pending list | /api/search-history/pending | GET | page, pageSize → data[], pagination |
| Approve | /api/search-history/{id}/approve | POST | id in path |
| Reject | /api/search-history/{id}/reject | POST | id in path, body: rejectionReason (optional) |

**Design**: List columns from pending response. Buttons: Approve, Reject. Reject may open modal for rejection reason (rejection_reason TEXT in DB). No search form; list is server-filtered by PENDING and scope.

---

## 6. user-management (사용자 관리)

**Required permission**: `allowedScreenIds` includes `user-management` or is_system_admin. Admin-only screen.

| Purpose | API | Method | Request/response notes |
|--------|-----|--------|-------------------------|
| User list | /api/users | GET | userId, isSystemAdmin, departmentCode, position, rank, isApprover |

**Design**: Table columns from GET /api/users. No write API (PUT /api/users 410 Gone); role is managed via permission groups. Field sizes from app_user (username 100, department_code 50, position/rank 50).

---

## 7. user-permission-hierarchy (사용자 권한 계층)

**Required permission**: `allowedScreenIds` includes `user-permission-hierarchy` or is_system_admin. In UI may redirect to same view as user-management; both use permission-group and hierarchy APIs.

| Purpose | API | Method | Request/response notes |
|--------|-----|--------|-------------------------|
| Department tree with users | /api/departments/user-permission-hierarchy | GET | format=tree | flat; tree has users[], permissionGroups[] |
| Permission group list | /api/permission-groups | GET | id, code, name, description, allowedScreens[] |
| Group detail | /api/permission-groups/{id} | GET | — |
| Create group | /api/permission-groups | POST | code, name, description, sortOrder, allowedScreens[] |
| Update group | /api/permission-groups/{id} | PUT | same |
| Delete group | /api/permission-groups/{id} | DELETE | 400 if group has users |
| Add user to group | /api/permission-groups/{id}/users | POST | userId |
| Remove user from group | /api/permission-groups/{id}/users/{userId} | DELETE | — |
| Group users | /api/permission-groups/{id}/users | GET | users[] |

**Design**: allowedScreens items: screenId (fixed set), scope (self|team|all for scope-supporting screens), read, write, approve, decrypt (main only). Field sizes: permission_group.code 50, name 200; screen_id from fixed list.

---

## 8. permission-group-management (권한 그룹 관리)

**Required permission**: `allowedScreenIds` includes `permission-group-management` or is_system_admin. Same APIs as user-permission-hierarchy (see §7).

---

## 9. department-approvers (부서별 결재자)

**Screen ID** exists in spec; in current menu tree the “부서별 결재자” may be under admin. If implemented:

| Purpose | API | Method |
|--------|-----|--------|
| Department tree | /api/departments | GET |
| Approvers per department | /api/departments/{code}/approvers | GET/POST/DELETE (if implemented) |

**Design**: department code 50, user_id 100.

---

## Summary table (screen → APIs)

| screen_id | Main APIs |
|-----------|-----------|
| main | /api/log-types, /api/log-types/{id}/fields, /api/logs/db-refactored/search, /api/logs/db-refactored/advanced-search, /api/search/suggest, /api/logs/db-refactored/…/decrypt, /api/logs/decrypt/{logType}, /api/search-history (POST when saving for approval) |
| search-history | /api/search-history (GET list, GET {id}, POST {id}/re-request) |
| activity-log | /api/activity-log/search, /api/activity-log/{id}, /api/statistics/departments, /api/statistics/users |
| statistics | /api/statistics/activity/daily, monthly, users/all, export, /api/statistics/departments, users, ips, /api/log-types |
| pending-approvals | /api/search-history/pending, /api/search-history/{id}/approve, /api/search-history/{id}/reject |
| user-management | /api/users |
| user-permission-hierarchy | /api/departments/user-permission-hierarchy, /api/permission-groups (CRUD), /api/permission-groups/{id}/users |
| permission-group-management | Same as user-permission-hierarchy |

Use this mapping so that **each screen’s search/filter and list/detail UI** is defined against the correct API request/response and DB field sizes in `db-definition.md` and `api-db-mapping.md`.
