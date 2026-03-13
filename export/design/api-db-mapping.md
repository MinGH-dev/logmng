# API–DB Mapping (for design reference)

Mapping of API endpoints to DB tables and main request/response fields. Use when aligning **request field sizes**, **result column widths**, and **validation rules** with the backend. Source: `docs/api-definition.md`, backend controllers and DTOs, DB schema.

---

## 1. Auth (no direct DB table for “session”; app_user used for login)

| API | Method | Main DB / entity | Request body fields (key) | Response (key) |
|-----|--------|------------------|---------------------------|----------------|
| /api/auth/login | POST | app_user, permission_group, permission_group_screen, decrypt_approver (for derive) | username, password | user (username, isSystemAdmin, allowedScreenIds, screenScopes, screenFunctions) |
| /api/auth/logout | POST | — | — | — |
| /api/auth/check | GET | — | — | authenticated, message |
| /api/auth/me | GET | app_user, permission_group_screen (via group) | — | same as login user object |

**Design note**: `screenScopes` and `screenFunctions` drive which filters are visible (e.g. user block when scope=self) and which actions are enabled (approve, decrypt, write).

---

## 2. Log types (metadata; no DB table, config/code)

| API | Method | Main DB / entity | Request | Response (key) |
|-----|--------|------------------|---------|----------------|
| /api/log-types | GET | — | — | id, name, description, tables |
| /api/log-types/{typeId} | GET | — | — | id, name, description, tables |
| /api/log-types/{typeId}/fields | GET | — | — | FieldMetadataResponse[] (java_fw_imglog) |

---

## 3. DB logs (검색하기 main)

| API | Method | Main DB / entity | Request body (key) | Response (key) |
|-----|--------|------------------|--------------------|-----------------|
| /api/logs/db-refactored/search | POST | pb_send, pb_recv (pb_feplog); imagelog (java_fw_imglog) | logType, startDate, endDate, mediaCode/media_gb, tr_code, loginId, application, servicegroup, service, datastring, headerstring, keywords, decryptData, page, pageSize, sortField, sortDirection | data[], pagination |
| /api/logs/db-refactored/advanced-search | POST | imagelog | logType, queryText, startDate, endDate, filters[], sort[], pagination, decryptData | data[], pagination |
| /api/logs/db-refactored/{logType}/{type}/{identifier} | GET | pb_send/pb_recv or imagelog | path | Map (row) |
| /api/logs/db-refactored/{logType}/{type}/{identifier}/decrypt | GET | imagelog + decrypt service | path | Map (decrypted) |
| /api/logs/decrypt/{logType} | POST | search_history, search_history_approved_row, imagelog | guid, searchHistoryId | Map (decrypted) |

**Field size reference**: See `db-definition.md` for pb_send/pb_recv (e.g. media_code VARCHAR(10), tr_code VARCHAR(20), user_id VARCHAR(50), ip_address VARCHAR(45)) and imagelog (application/servicegroup/service VARCHAR(256), guid VARCHAR(256), datastring/headerstring TEXT).

---

## 4. Search suggest

| API | Method | Main DB / entity | Query params | Response |
|-----|--------|------------------|--------------|----------|
| /api/search/suggest | GET | (imagelog / metadata) | logType, context, prefix, fieldName | List<Map> |

---

## 5. Search history (검색 이력)

| API | Method | Main DB / entity | Request / query | Response (key) |
|-----|--------|------------------|-----------------|----------------|
| /api/search-history | POST | search_history | logType, searchParams | id, requestedAt, expiresAt, approvalStatus |
| /api/search-history | GET | search_history | page, pageSize, sortField, sortDirection | data[], pagination (id, requestedAt, expiresAt, approvalStatus, searchParamsSummary, approvedBy, rejectedBy, etc.) |
| /api/search-history/{id} | GET | search_history | path id | id, logType, searchParams, requestedAt, expiresAt, approvalStatus, approvedBy, rejectedBy, rejectionReason |
| /api/search-history/{id}/re-request | POST | search_history | — | id, approvalStatus, requestedAt, expiresAt |
| /api/search-history/pending | GET | search_history (filter PENDING, approver check) | page, pageSize | data[], pagination |
| /api/search-history/{id}/approve | POST | search_history, search_history_approved_row | — | id, approvalStatus, approvedBy, approvedAt |
| /api/search-history/{id}/reject | POST | search_history | rejectionReason (optional) | id, approvalStatus, rejectedBy, rejectedAt, rejectionReason |

**Scope**: List/detail scope from permission (self/team/all). Approval scope is department; approver must be decrypt_approver or is_system_admin and canApproveForRequester.

---

## 6. Users (관리자 전용)

| API | Method | Main DB / entity | Request / query | Response (key) |
|-----|--------|------------------|-----------------|----------------|
| /api/users | GET | app_user, decrypt_approver (derived isApprover) | — | userId, isSystemAdmin, departmentCode, position, rank, isApprover |
| PUT /api/users/{userId} | PUT | — | (410 Gone) | — |

---

## 7. Activity log (활동 이력)

| API | Method | Main DB / entity | Request body (key) | Response (key) |
|-----|--------|------------------|--------------------|----------------|
| /api/activity-log/search | POST | user_activity_log | startDate, endDate, userId, username, department, actionType, ipAddress, page, pageSize, sortField, sortDirection | data[], pagination |
| /api/activity-log/{id} | GET | user_activity_log | path id | Map (row) |

**Field size reference**: user_id/username VARCHAR(100), action_type VARCHAR(50), ip_address VARCHAR(45). See `db-definition.md` §5.

---

## 8. Statistics (활동로그 통계)

| API | Method | Main DB / entity | Query params (key) | Response (key) |
|-----|--------|------------------|--------------------|----------------|
| /api/statistics/activity/daily | GET | user_activity_log (aggregated) | startDate, endDate, logType, userId, department, ip, username | dailyStats, summary |
| /api/statistics/activity/monthly | GET | user_activity_log (aggregated) | year, month, logType, userId, department, ip, username | monthlyStats, summary |
| /api/statistics/activity/users/all | GET | user_activity_log (aggregated) | startDate, endDate, same filters | user-level stats |
| /api/statistics/activity/export | GET | user_activity_log | type, same filters (blob) | CSV blob |
| /api/statistics/departments | GET | department | — | list for filter |
| /api/statistics/users | GET | app_user (or activity view) | — | list for filter |
| /api/statistics/ips | GET | user_activity_log (distinct) | — | list for filter |

**Design note**: Filter params (userId, username, department, ip) align with activity-log search; scope=self forces backend to ignore these and return current user only.

---

## 9. Departments (관리자 전용)

| API | Method | Main DB / entity | Query / body | Response (key) |
|-----|--------|------------------|--------------|-----------------|
| /api/departments | GET | department | format=tree \| flat | tree or flat list (code, parentCode, name, sortOrder, children?) |
| /api/departments/user-permission-hierarchy | GET | department, app_user, app_user_permission_group, permission_group | format=tree \| flat | tree with users[], permissionGroups[] |

---

## 10. Permission groups (관리자 전용)

| API | Method | Main DB / entity | Request body (key) | Response (key) |
|-----|--------|------------------|--------------------|----------------|
| /api/permission-groups | GET | permission_group, permission_group_screen | — | id, code, name, description, sortOrder, allowedScreens[] |
| /api/permission-groups | POST | permission_group, permission_group_screen | code, name, description, sortOrder, allowedScreens[] | created group |
| /api/permission-groups/{id} | GET | permission_group, permission_group_screen | — | same |
| /api/permission-groups/{id} | PUT | permission_group, permission_group_screen | same | updated group |
| /api/permission-groups/{id} | DELETE | permission_group | — | 400 if group has users |
| /api/permission-groups/{id}/users | POST | app_user_permission_group | userId | — |
| /api/permission-groups/{id}/users/{userId} | DELETE | app_user_permission_group | — | — |
| /api/permission-groups/{id}/users | GET | app_user_permission_group, app_user | — | users[] |

**allowedScreens** shape: `{ screenId, scope?, read?, write?, approve?, decrypt? }`. screenId from fixed list; scope for activity-log, statistics, search-history, pending-approvals.

---

## 11. DB test / health (ops)

| API | Method | Main DB / entity | Response |
|-----|--------|------------------|----------|
| /api/db/test | GET | connection, pb_send, pb_recv | connected, table_exists, counts |
| /api/db/schema | GET | pb_send, pb_recv | column metadata |
| /api/health | GET | — | status, timestamp, message |
| /api/logs/db-refactored/health | GET | — | status, message |
| /api/logs/db-refactored/schema | GET | — | tables |

---

## Design usage

- **Request field maxLength**: For any API request field that maps to a DB column, use the column size from `db-definition.md` (e.g. userId → 100, department → 50, ipAddress → 45).
- **Result columns**: List/detail column widths can follow the same sizes so that values are not truncated.
- **Scope-aware fields**: For activity-log, statistics, search-history, pending-approvals, when scope=self the backend ignores user/department/ip filters; design standards should state that the “user context” block is hidden when scope=self.
