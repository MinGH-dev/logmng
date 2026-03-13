# Screen User Actions (for design reference)

**User actions per screen**: what the user can do on each view, including conditions (scope, role, screenFunctions). Use when writing design standards so that all interactions (buttons, links, filters, modals) are covered. Source: `specs/permission-group-hierarchy.spec.yaml`, frontend components, `docs/api-definition.md`.

---

## 1. main (검색하기)

| Action | Description | Condition | UI element |
|--------|-------------|-----------|------------|
| Select log type | Choose pb_feplog or java_fw_imglog | — | Log type selector |
| Enter search criteria | Set date range, media/tr/loginId (pb_feplog) or application/servicegroup/service/datastring/headerstring/keywords (java_fw_imglog) | — | Search form fields |
| Search | Run search (POST /api/logs/db-refactored/search or advanced-search) | — | Search button |
| View results | See paginated list | — | Result grid/table |
| Sort | Change sort field/direction | — | Column header or sort control |
| Paginate | Change page/pageSize | — | Pagination |
| View detail | Open single row (GET …/identifier) | — | Row click or link |
| Request decryption (with approval) | Save search as search_history (POST), then later decrypt single row | screenFunctions.main.decrypt === true | “복호화 요청” flow; decrypt option in form when keywords used |
| Decrypt single row | POST /api/logs/decrypt (after search approved) | screenFunctions.main.decrypt; searchHistoryId valid, row in approved snapshot | Decrypt button on row |
| Open advanced search | Switch to query-builder UI (java_fw_imglog) | — | Tab or link |
| Use suggest | Get field/operator/value suggestions | java_fw_imglog | Autocomplete/suggest UI |

**Scope**: N/A (main is not a user-scoped list). **Hidden/disabled**: Decrypt and “복호화 요청” when screenFunctions.main.decrypt is false; show tooltip or message on deny.

---

## 2. search-history (검색 이력)

| Action | Description | Condition | UI element |
|--------|-------------|-----------|------------|
| View list | See own/team/all search history (scope applies) | — | Table/list |
| Sort/paginate | sortField, sortDirection, page, pageSize | — | Table header, pagination |
| View detail | GET /api/search-history/{id} | Own or in-scope | “자세히 보기” / detail modal |
| Re-search | Navigate to main and run same search (searchParams) | — | “재조회” button |
| Re-request | POST …/re-request when status is EXPIRED | Own record; status EXPIRED | “재요청” button (disabled when not EXPIRED or not own) |

**Scope**: self → own only; team → same department; all → all. **No approve/reject on this screen** (that is pending-approvals).

---

## 3. activity-log (활동 이력)

| Action | Description | Condition | UI element |
|--------|-------------|-----------|------------|
| Set filters | startDate, endDate; department, username, userId; actionType, ipAddress | When scope ≠ self: user and extra blocks visible. When scope=self: user block and extra block **hidden** | Search form |
| Search | POST /api/activity-log/search | — | Search button |
| View list | Paginated activity log | — | Table |
| Sort/paginate | sortField, sortDirection, page, pageSize | — | Table header, pagination |
| View detail | GET /api/activity-log/{id} | — | Row click or “상세” |

**Scope**: self → only current user’s data, filters for user/department/ip hidden; team → same department; all → filters applied as entered.

---

## 4. statistics (활동로그 통계)

| Action | Description | Condition | UI element |
|--------|-------------|-----------|------------|
| Switch mode | Daily vs monthly | — | Tabs or toggle (일별/월별) |
| Set date range (daily) | startDate, endDate | — | Date inputs in header |
| Set period (monthly) | year, month | — | Year/month selects |
| Set filters | logType, department, username, userId, ip | When scope ≠ self; when scope=self **hidden** | Filter panel (UserContextFilterBlock + IP) |
| Load stats | GET daily or monthly + users/all | — | On mode/date/filter change or “검색” |
| Export | GET /api/statistics/activity/export (CSV) | — | “내보내기” / Export button |
| View charts/tables | See dailyStats, monthlyStats, user stats | — | Charts, tables |

**Scope**: Same as activity-log; scope=self hides user and extra (IP) blocks and backend returns current user only.

---

## 5. pending-approvals (승인 대기)

| Action | Description | Condition | UI element |
|--------|-------------|-----------|------------|
| View list | See pending requests (scope: self=own requests, team=department, all=all) | screenFunctions['pending-approvals'].approve OR read-only list | Table |
| Approve | POST /api/search-history/{id}/approve | screenFunctions['pending-approvals'].approve === true; canApproveForRequester(approver, requester) | “승인” button |
| Reject | POST /api/search-history/{id}/reject; optional rejectionReason | Same as approve | “반려” button; optional modal for reason |
| Paginate | page, pageSize | — | Pagination |

**Scope**: self → only rows where requester = current user; team → same department + canApproveForRequester; all → all approvable. **Disabled**: Approve/Reject when approve is false or not approver for that requester; show tooltip or message.

---

## 6. user-management (사용자 관리)

| Action | Description | Condition | UI element |
|--------|-------------|-----------|------------|
| View user list | GET /api/users | — | Table (userId, isSystemAdmin, departmentCode, position, rank, isApprover) |
| (No role change) | PUT /api/users 410 Gone | — | — |

**Note**: Role is managed via permission groups (user-permission-hierarchy / permission-group-management). Design standards should not add “edit role” here.

---

## 7. user-permission-hierarchy (사용자 권한 계층)

| Action | Description | Condition | UI element |
|--------|-------------|-----------|------------|
| View tree | GET /api/departments/user-permission-hierarchy (tree or flat) | — | Department tree with users and permission groups |
| View group list | GET /api/permission-groups | — | List or sidebar |
| Create group | POST /api/permission-groups | screenFunctions write for this screen | “추가” / Create |
| Edit group | PUT /api/permission-groups/{id} | write | Edit form (code, name, description, allowedScreens) |
| Delete group | DELETE /api/permission-groups/{id} | write; 400 if group has users | Delete button |
| Set allowed screens | allowedScreens: screenId, scope, read, write, approve, decrypt | write | Screen tree + scope dropdown + checkboxes |
| Add user to group | POST /api/permission-groups/{id}/users | write | “사용자 배정” |
| Remove user from group | DELETE /api/permission-groups/{id}/users/{userId} | write | Remove button |
| View group users | GET /api/permission-groups/{id}/users | — | User list in panel |

**Scope**: Allowed only for screens that support scope (activity-log, statistics, search-history, pending-approvals); approval scope is fixed to department (dropdown disabled when approve=true).

---

## 8. permission-group-management (권한 그룹 관리)

Same actions as **user-permission-hierarchy** (§7). May be same view in UI.

---

## 9. department-approvers (부서별 결재자)

If this screen is implemented:

| Action | Description | Condition | UI element |
|--------|-------------|-----------|------------|
| View department tree | GET /api/departments | — | Tree |
| View/add/remove approvers | Per-department decrypt_approver APIs | write | List, Add/Remove buttons |

---

## Summary: conditional visibility and disable rules

| Screen | Hide when | Disable when |
|--------|-----------|--------------|
| main | — | Decrypt / “복호화 요청” when screenFunctions.main.decrypt false |
| search-history | — | Re-request when not EXPIRED or not own |
| activity-log | User block + extra block when scope=self | — |
| statistics | User block + extra block when scope=self | — |
| pending-approvals | — | Approve/Reject when !approve or !canApproveForRequester |
| user-management | — | — |
| user-permission-hierarchy / permission-group-management | — | Create/Edit/Delete/Add user when !write |

Use this document together with **screen-api-mapping.md** and **api-db-mapping.md** so that design standards define layout, field sizes, and behavior for every user action and every conditional state (scope=self, approve=false, etc.).
