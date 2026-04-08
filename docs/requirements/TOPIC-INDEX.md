# Requirements Topic Index

For **RequirementsPastSearch** token optimization. Read this file first to find relevant doc IDs by topic, then read only §1 of those docs (offset=1, limit=90).

**Maintenance**: When adding a new requirement, add one line under the matching topic(s). Format: `- doc-id | one-line §1 summary`
**Automation**: When a requirement doc transitions to `- [x] Requirement doc completed`, the completion hook auto-adds it to the best-matching topic section in this file. If no topic clearly matches, it is added under `misc` and may be moved manually later.

---

## permission | access-control | 화면 접근 | 권한 그룹 | is_system_admin

- 20260407-permission-group-assign-unassign-audit-before-after | User management assign/unassign: persist permissionGroupAuditV1 before/after snapshots (prior group vs new group; unassign before+null after); pre-mutation capture; spec alignment
- 20260330-permission-group-activity-detail-audit | Permission group activity rows: audit-grade action_detail (before/after or field diff), detail UI and export path options; Security review for PII and scope
- 20260330-activity-types-user-mgmt-permission-group | Activity type taxonomy (login, search, admin, decrypt approval); user management and permission group flows emit auditable user_activity_log rows; filterable in activity history API/UI per contract
- 20260323-approver-eligibility-from-permission-group-only | 계약 `contract.md` 「복호화 승인 자격」: 그룹 `approve` 우선·**`is_system_admin`만 상쇄**; **ADMIN_EXT는 그룹만**+P2-2; `decrypt_approver` DROP; `isApprover` UI 제거. Q&A: `...-PRODUCT-QA.md`
- 20260317-search-decrypt-permission-ui | 검색하기 화면 복호화 권한 UI: 권한 없을 때 버튼 비활성화 및 "복호화 권한이 없습니다." 표시; 요청 사유는 승인 요청 시 모달에서 입력
- 20260306-search-screen-decrypt-permission | 검색하기(main) 화면 복호화 권한 부여/해제; 권한관리에서 복호화 체크; 복호화 API는 권한 있는 사용자만 요청 가능
- 20260306-approval-scope-fixed-department | Approval scope fixed to department (부서); scope selection read-only when approve selected in permission config
- 20260304-permission-group-modal-error-visibility | Show permission-group create/edit/delete errors inside modals; fix APPROVE_USER + search-history + team error
- 20250304-permission-group-function-verification | 권한 그룹 수정/승인: 보유자 정상 이용·미보유자 행사 불가 검증 (verification only; single source)
- 20250303-screen-function-availability | 화면별 기능 사용 가능 여부 (read/write/approve); main read-only; common format
- 20250303-screen-function-checkbox-selection | 조회/수정/승인 체크박스로 명시적 선택 (permission group config)
- 20250227-permission-group-screen-menu-access | 권한 그룹별 화면 접근 설정; ADMIN or group allows screen; GENERAL_USER
- 20250303-permission-group-management-access-fix | 권한 그룹 관리: user-permission-hierarchy 허용 시 "관리자만 접근" 오류 수정
- 20250227-permission-group-screen-menu-access-bugfix-1 | Admin 섹션: non-admin with user-management in allowedScreenIds → 사이드바 표시
- 20250303-user-management-permission-group-access | 사용자관리: 권한 그룹으로 user-management 허용 시 "관리자만 접근" 오류 수정
- 20250303-user-management-permission-group-access-bugfix-1 | Frontend: canAccessUserManagement
- 20250303-user-management-permission-group-access-bugfix-2 | Backend: hierarchy/permission-groups API accept user-management OR user-permission-hierarchy
- 20250227-permission-group-separate-menu | 권한 그룹 메뉴 분리
- 20250227-permission-management-in-hierarchy | 권한 관리를 사용자 권한 계층 화면에 통합
- 20250227-permission-user-management-close-button | 권한 그룹 사용자 할당 모달 닫기 버튼 가시성
- 20250303-permission-group-delete-system-admin-protection | 권한 그룹 삭제 제약; 시스템 관리자 보호 (is_system_admin)
- 20250303-permission-group-invalid-screen-id-bugfix | 권한 그룹 invalid screen_id 버그 수정
- 20250303-remove-role-single-admin | role 제거; is_system_admin만 admin 접근; 단일 시스템 관리자
- 20250303-remove-role-single-admin-bugfix-1 | login/me에 isSystemAdmin; PUT 410; role 응답 제거
- 20260317-search-decrypt-permission-ui | - **Permission enforcement (UI)**: On the "검색하기" (search) screen, users whose permission group does **not** have the decrypt feature enabled can still trigger decrypt-related actions (e.g. "복호화 승인 요청" button and per-row "복호화" button). This is a permission enforcement bug: the backend correctly returns 403 for the decrypt API when the user lacks `screenFunctions.main.decrypt`, but the UI does not gate these actions.
- 20260318-permission-group-menu-invalid-screen-id-imagelog | When an administrator opens the permission group management screen and tries to edit menu permissions (allowed screens) for a group, the modal shows the error: **"유효하지 않은 화면 ID입니다: java-fw_imagelog"** (Invalid screen ID: java-fw_imagelog). The backend rejects the screen ID and the save fails with 400 `INVALID_SCREEN_ID`, so the admin cannot complete the permission group update.
- 20260320-permission-group-screen-entry-error-migration-check | Permission group management screen shows an error on entry; verify root cause via diagnostics and objectively check DB migration applicability (`permission_group_screen` columns vs. `PermissionGroupService` SQL, migrate script inventory vs. `setup.sh`).
- 20260406-permission-group-invalid-screen-id-screen-display-labels | When a system administrator edits **permission groups** (screen permissions) and saves, the application shows the error **"유효하지 않은 화면 ID입니다: screen-display-labels"** and the save fails with **400** and error code **`INVALID_SCREEN_ID`**. The reported ID **`screen-display-labels`** matches the **menu / view id** used for the **Screen display names** (화면 표시 이름) admin feature.

## activity-log | statistics | 활동 로그 | 통계 | scope

- 20260408-user-stats-decrypt-count-logtype-json-escape | Activity statistics decrypt count was 0: path `logType` in activity log was JSON double-encoded; fix scalar param handling in ActivityLogAspect so `action_detail` matches statistics LIKE `"logType":"<id>"`
- 20260408-activity-log-detail-modal-viewport-centering | User Activity Log detail modal: center in main content area on small viewports; full reachability via scroll; frontend CSS/optional JSX only
- 20260407-permission-group-assign-unassign-audit-before-after | Assign/unassign permission-group activity: non-null before/after in permissionGroupAuditV1; ActivityAuditDetailEnricher + PermissionGroupAuditContext + service pre-capture
- 20260330-audit-evidence-activity-log-conservative | Resolve audit manual §8 with conservative defaults: uniform mutation envelope, soft delete or mandatory delete snapshot, allowlisted before/after, in-app copy truncation + privileged access audit, retention/crypto/export gates; SVG wireframe specs for list/detail/access-audit/optional policy
- 20260330-permission-group-activity-detail-audit | Enrich permission-group action_detail and activity-log detail UI for audit evidence; optional export/query; aligns with parent 20260330-activity-types-user-mgmt-permission-group
- 20260330-activity-types-user-mgmt-permission-group | Activity type taxonomy and audit for permission group / user management; queryable by action type + existing filters; contract/skill touchpoints
- 20260408-user-management-v2-activity-audit-detail-in-activity-log | User Management v2 mutations (dept tree, direct user create, related user-admin actions) must emit auditable user_activity_log rows with structured action_detail and visible activity-log detail; extend activity-action-types; align spec §5
- 20260311-activity-log-statistics-design-improvement | 활동 이력·통계 두 화면 디자인 표준 정렬: 그룹 제목·패널 너비·compact·사용자 블록 동일 크기·검색/초기화·폼 시맨틱·row1=날짜 row2=나머지
- 20260313-activity-log-statistics-design-standards | 활동 이력·통계 두 화면 디자인 표준 정렬: 그룹 제목·패널 너비·compact·검색/초기화·폼 시맨틱·row1=날짜 row2=나머지·필터 접기 제거
- 20260310-search-consistency-all-screens | All screens rule-compliant search; per-screen table (부서·이름·사용자ID, scope=self 숨김); re-review final doc
- 20260310-search-consistency-phase1 | Phase 1: activity-log (add 부서) + statistics (add 이름); scope=self hide; implementable handoff for Step 4
- 20260310-search-ui-unify | Search UI unified concept: same field order (부서→이름→사용자ID), grouping, labels; optional shared UserContextFilterBlock
- 20260310-search-screens-qa-ux-redesign-handoff | QA inspect activity-log + statistics search UI → feedback to UX → UX redesign → Frontend implementation; process and deliverables
- 20250304-team-scope-default-and-approval | 권한그룹 scope에 팀(team) 추가·기본값 팀; 승인 대기창 팀장은 팀원 요청만
- 20260206-activity-log-statistics | 활동로그 통계 화면 (월별/일별/사용자별)
- 20260206-activity-log-statistics-improvement | 활동 로그 통계 화면 개선
- 20260206-activity-log-statistics-improvement-test-results | 통계 개선 테스트 결과
- 20260206-activity-log-statistics-test-results | 활동로그 통계 테스트 결과
- 20260206-user-activity-log | 사용자 활동 이력 보관 및 조회
- 20260220-activity-log-today-empty-fix | 활동이력 오늘 날짜 조회 시 결과 없음 수정
- 20260220-activity-statistics-api-error-fix | 활동 로그 통계 조회 오류 수정
- 20260220-activity-statistics-no-anonymous-access | 통계 anonymous 제거, 미인증 조회 차단
- 20260220-activity-statistics-whole-equals-sum-of-logtypes | 통계 '전체' = 로그타입 합계 정합성
- 20250303-activity-statistics-self-only-scope | 비관리자 scope=self|all (activity-log, statistics, search-history)
- 20250303-activity-statistics-self-only-scope-bugfix-1 | scope 적용 미적용 (session/scope resolution)
- 20250303-activity-statistics-self-only-scope-bugfix-2 | TC-02, TC-06, TC-08 재검증
- 20260317-activity-statistics-department-approver-error | Fix error when activity statistics is queried by approver group/department (scope=team); align user_id vs user_name to user_id where wrong; implementer must check backend logs for root cause.
- 20260408-user-stats-decrypt-count-logtype-json-escape | User activity statistics (daily/monthly) must count **decrypt** actions per log type (e.g. `java_fw_imglog`, `pb_feplog`). After a user performs a decrypt, the **today** (and aggregated) decrypt counts must reflect that activity.
- 20260408-activity-statistics-decrypt-unique-rows-per-day | Decrypt KPI from `user_activity_log` DECRYPT only: **distinct logical rows per calendar day** (`java_fw_imglog`: logType+guid+status); not approval counts; daily aggregate global dedup; per-user dedup within user

## sidebar | layout | 사이드바 | 레이아웃

- 20260408-activity-log-detail-modal-viewport-centering | Activity Log detail modal positions relative to main content column (not clipped on short/narrow viewports); overlay scroll vs modal-body scroll; z-index vs shell
- 20260407-screen-menu-parent-order | Extends screen display labels: admin sets **closed-set** top-level `parentGroupId` (`MENU_TREE` groups) and per-leaf `sortOrder` in same admin UI; routing/`currentView` unchanged; MENU_TREE fallback; v1 **out of scope** for group title/order overrides
- 20260406-menu-display-names-admin | Admin-configurable sidebar and screen display labels (`label_user` / optional `label_admin`); stable `screen_id`/`currentView`; GET for authenticated users, admin-only PUT/PATCH; DB + audit; merge over `menuTree`/`LOG_TYPE_BY_VIEW` with fallback
- 20260225-sidebar-content-scroll-independent | 사이드바·컨텐츠 스크롤 독립화
- 20260225-sidebar-library-migration | 사이드바 라이브러리 도입 (펼침·스크롤 안정화)
- 20260225-sidebar-layout-no-overflow | 사이드바 펼침 시 콘텐츠 영역 가로 탈출 수정
- 20260225-sidebar-search-display-and-scroll | 사이드바 검색 메뉴 표시 및 스크롤 복구
- 20260225-sidebar-search-menu-hierarchy | 사이드바 검색 메뉴 계층 표시 개선
- 20260225-sidebar-submenu-expand-overflow | 사이드바 하위메뉴 펼침 시 다른 상위 메뉴 가림 수정
- 20260225-sidebar-topbar-layout-no-overlap | 사이드바·상단바 레이아웃 중첩 제거 및 스크롤 분리

## department | 부서 | 결재자 | hierarchy

- 20250304-team-scope-default-and-approval | 팀 scope·기본값; 팀장 승인 대기창 팀원만 (department-scoped)
- 20250227-department-approver-position | 부서 결재자: position 필드, 부서 범위 선택
- 20250227-dept-hierarchy-daol-structure | 부서 계층: 다올투자증권 구조 (4단계)
- 20250227-dept-hierarchy-sample-depth5 | 부서 계층 샘플 데이터 depth 5
- 20260225-department-approver-hierarchy | 부서별 결재자 지정 및 부서 계층 표시
- 20250227-remove-department-approver-screen-user-mgmt-improvements | 부서별 결재자 화면 제거; 사용자 관리 개선
- 20250227-remove-department-approver-screen-user-mgmt-improvements-bugfix-1 | POST /api/users/approvers 500 대신 404

## user-management | 사용자 관리 | hierarchy

- 20260408-my-page-local-password-and-profile | Local user create paths must default initial password to `user123` (hashed); profile read-only in My page modal; self-service password change API per Contract
- 20260330-activity-types-user-mgmt-permission-group | User management and permission group flows must emit typed activity-log events; canonical action_type list and activity-log filter alignment
- 20260310-search-consistency-all-screens | User-management·permission-group search form (부서·이름·사용자ID) per-screen table
- 20250227-user-management-hierarchy-permissions | 사용자 관리: 계층 표시, role·권한 그룹 편집
- 20250227-user-permission-hierarchy-group | 사용자 권한 계층 및 권한 그룹 관리
- 20250227-user-permission-hierarchy-group-bugfix-1 | DB schema·init-data 미적용
- 20250227-user2-approver-display-bugfix | user2 결재자 표시 오류
- 20250303-user-management-permission-group-access | 사용자관리: 권한 그룹으로 user-management 허용 시 "관리자만 접근" 오류 수정
- 20250303-user-management-permission-group-access-bugfix-1 | Frontend: canAccessUserManagement
- 20250303-user-management-permission-group-access-bugfix-2 | Backend: hierarchy/permission-groups API accept user-management OR user-permission-hierarchy
- 20260407-user-management-consistency-delete-reason-activity-audit | Administrators need a trustworthy User Management experience: identifiers shown when provisioning from the HR directory (`ext_employee`) must match what appears in the User Management list (`app_user`). Operators report a case where external search marks an employee as already registered (`provisioned`) and disables re-registration, but the corresponding user does not appear in the User Management table—undermining trust in provisioning and list data.
- 20260408-user-management-v2-activity-audit-detail-in-activity-log | User Management v2 mutations (dept tree, direct user create, related user-admin actions) must emit auditable user_activity_log rows with structured action_detail and visible activity-log detail; extend activity-action-types; align spec §5

## decryption | 복호화 | search-history | 검색 이력 | approval

- 20260407-pending-approvals-history-search-readonly-requester | 복호화 승인 관리: 이력 검색·조회, 요청자 읽기 전용, 승인자 워크플로 유지, 승인/반려 후 행 유지, 로그 검색(LogGrid) 검색/필터 UI 정렬, 계약·인터셉터·스코프 정합
- 20260318-search-history-create-server-error-bugfix | Bugfix: server error when submitting "복호화 승인 요청" (POST /api/search-history); Backend to identify root cause and fix so create returns 201 and UI shows success.
- 20260318-search-history-user2-not-showing | user2로 검색이력 화면에서 검색이력 데이터가 표시되지 않는 문제 원인 파악 및 조치
- 20260310-search-consistency-all-screens | All screens rule-compliant search; search-history·pending-approvals requester filters (부서·이름·사용자ID)
- 20260306-search-screen-decrypt-permission | 검색하기 화면 복호화 권한 부여/해제; 복호화 API는 main.decrypt 권한 있는 사용자만 요청 가능
- 20260306-approval-scope-fixed-department | Approval scope fixed to department; config UI shows 부서 read-only when approve selected
- 20260224-decryption-approver-designation | 복호화 결재자 지정 및 결재자 전용 승인
- 20260224-decryption-require-approval | 복호화 승인 없이 복호화 차단
- 20260224-search-history-decryption-approval | 검색 이력 및 복호화 승인 재요청
- 20260224-search-history-reload-and-detail-view | 검색 이력 재조회 시 조건 표시 및 자세히 보기
- 20260224-decryption-snapshot-final-design | 복호화 스냅샷 최종 설계
- 20260224-decryption-snapshot-final-design-en | (English)
- 20260224-decryption-snapshot-qa-test-scenarios | 복호화 스냅샷 QA 시나리오
- 20260224-decryption-approval-snapshot-guide | 복호화 승인 스냅샷 가이드
- 20260316-search-history-grid-requester-and-modal | On the **search history** screen (검색 이력), the following changes are requested:
- 20260316-search-history-grid-department-and-username | On the **search history** (검색 이력) result grid, the user requests that **department and user name** (부서와 사용자명) be shown clearly.
- 20260316-search-history-grid-requester-columns-empty-data | Bugfix: search history grid shows 부서, 사용자ID, 사용자명 column headers but cell data is empty; analysis and fix delegated to Backend and Frontend.
- 20260316-bugfix-search-history-screen-server-error | Bugfix: server error on search history screen entry; list API or current-user resolution suspected; implementer to identify root cause and fix.
- 20260316-search-history-auth-500-fix | Fix 500 on GET /api/auth/check and GET /api/search-history; uncaught exceptions in controller path (getCurrentUserId, resolveScope, getScreenScopes, DepartmentScopeHelper); return 200 or 401 instead of 500.
- 20260316-decrypt-approve-cross-user-server-error | When user1 (approver) approves user2's (requester's) decryption request, server returns 500; fix so approval succeeds or returns clear 4xx (no 500).
- 20260316-decrypt-approval-use-user-id-everywhere | Decrypt approval: use user_id (numeric) everywhere in approval flow; clarify current approver check; eliminate username-based permission/storage to avoid 500.
- 20260317-search-history-labels-layout-decrypt-dropdown | Search History: label 요청일시→검색일시 (form and grid); two-row layout (row1 date only, row2 requester|approval|request reason); 복호화 승인 여부 as dropdown with checkboxes.
- 20260317-search-history-screen-improvements | Search History screen: default date range d−7/d+0; 7d/15d/30d presets; 복호화 label; approval filter same background as editable fields; narrow seq/복호화 columns; paging aligned with other list screens.
- 20260317-search-history-grid-columns-filter-fix | Search History: grid column sizing (User ID 8-digit, Search condition button-only); ensure/fix filtering by 검색일시, 복호화, 요청 사유 (expected behavior and diagnosis guidance for frontend vs backend).
- 20260318-decryption-allowed-store-and-decrypt-ui | This requirement covers six related changes: (1) hide the decrypt button when there is no encrypted data; (2) on the search screen, show a dimmed decrypt button for GUIDs that have not received decryption approval, with an informative message on click; (3) change the decryption-approval model so that “who can decrypt what” is no longer stored in `search_history_approved_row`, and introduce a new store keyed by user, screen, approved GUIDs, and validity period; (4) when a user requests approval for a different search condition, refresh the decryption-allowed list and renew the validity period for that user; (5) when an approver approves a request, clean up expired approval records for that user; (6) keep `search_history_approved_row` as a full audit/history table with no removal of old rows.
- 20260318-decryption-approval-guids-encrypted-only | Decryption-approval GUID management must apply only to GUIDs (row identifiers) that correspond to rows **that have encrypted data**. Today, when an approver approves a search-history request, **all** row IDs from the search result are stored in `search_history_approved_row` (audit) and in `user_decryption_allowed` (authorization), regardless of whether each row contains encrypted content. This requirement restricts both stores so that only rows that actually have encrypted data are included in the snapshot and in the decryption-allowed set.
- 20260318-search-history-detail-modal-decryption-list | On the Search History (검색 이력) screen, in the action column of search results, the "view details" (자세히 보기) modal currently shows search conditions (로그 타입, 요청 사유, 검색 조건). The user requests that this modal also display: (1) the **list** of applications, service groups, and GUIDs that were requested for decryption (i.e. the rows in the approval snapshot), and (2) the **total count** of those items.
- 20260318-search-history-counts-display | Search history list: 검색건수 and 암호화건수 must display distinctly (e.g. search 48 / encryption 37); fix sourcing and display so two counts are correct; diagnostic then fix backend create/list and optional client send counts.
- 20260320-imagelog-guid-status-composite-key | For **Java FW Image Log** (`java_fw_imglog`, table `imagelog`), a single **guid** value is **not** sufficient to identify a row: the business identity is the **pair (guid, status)**. The product must treat this pair as the **canonical row key** everywhere: search-result rows, detail fetch, decryption execution, decryption-approval snapshot, decryption-allowed authorization store, search-history payloads, and all related APIs and UI.
- 20260408-pending-approvals-missing-reject-button-bugfix | Investigate and fix the bug where an approver user cannot see the **Reject** button on the `pending-approvals` screen while trying to process another user's decryption approval request.
- 20260408-activity-statistics-decrypt-unique-rows-per-day | Activity statistics decrypt counts: **unique decrypted rows per day** from `user_activity_log` (`DECRYPT` only), dedup key aligned with imagelog composite key; exclude approval-only action types

## image-log | imagelog | datastring

- 20260330-imagelog-dup-guid-sample-data | Seed data: two imagelog rows share guid `GUID-DUP-PRETTY-20260330` with different status (input/output) for Pretty/decrypt manual and integration testing; aligns with parent req 20260330-image-log-pretty-decrypt-row-key
- 20260320-imagelog-guid-status-composite-key | Java FW Image Log row identity is (guid + status); APIs, decryption-allowed store, approval snapshot, search-history detail, DB constraints, and UI must use the composite end-to-end
- 20260206-image-log-datastring-search | Image Log datastring 검색 기능 개선
- 20260206-image-log-decrypt-datastring-display | Image Log 복호화 시 datastring 필드 표시
- 20260224-image-log-encrypted-highlight-only | Image log 암호화 구간만 encrypted 하이라이트
- 20260225-image-log-search-no-results | 이미지 로그 검색 결과 없음
- 20260318-image-log-search-data-header-keyword-fix | On the Image Log (Java FW Image Log) screen, **data search** (datastring), **header search** (headerstring), and **keyword search** (keywords) are reported as not working properly. The user wants the **root cause identified** and the search behavior fixed so that data, header, and keyword filters behave as intended.

## grid | UX | ux-standards | 그리드

- 20260313-activity-log-statistics-design-standards | 활동 이력·통계 두 화면 디자인 표준 정렬 (그룹 제목·패널 너비·compact·검색/초기화·a11y)
- 20260310-search-screens-qa-ux-redesign-handoff | QA→UX redesign→Frontend handoff for activity-log and statistics search screens; deliverables and process
- 20260304-permission-group-modal-error-visibility | Error message visibility inside permission-group modals (not behind overlay)
- 20260226-grid-design-unification | 그리드 디자인 통일
- 20260226-ux-grid-review-and-push | UX grid review and push
- 20260226-ux-grid-review-report | UX grid review 리포트
- 20260225-ux-standards-compliance-audit | UX 표준 준수 감사 및 개선
- 20260225-ux-standards-compliance-audit-bugfix-1 | 날짜 역전 제출 시 aria-invalid/aria-describedby 미노출

## privacy | security | 개인정보 | IP | 로깅

- 20260206-privacy-security-improvement | 프론트엔드 개인정보 보호 및 보안 개선
- 20260206-privacy-security-improvement-summary | 개인정보 보호 개선 요약
- 20260206-privacy-security-improvement-test-results | 개인정보 보호 테스트 결과
- 20260206-ip-collection-and-decryption-logging | IP 수집 정확도 개선 및 복호화 로깅 강화
- 20260408-external-hr-user-sync-security-db-design | Design a secure synchronization policy between externally collected HR data and current user management.
- 20260408-hr-sync-poc-snapshot-list-and-sample-data | HR Sync PoC: sample `ext_*` data for multiple snapshot IDs, read-only snapshot list + personnel APIs, UI selector; zero-impact; PII/minimization in §2.1; DOC-CODE-SYNC `specs/hr-sync-poc.spec.yaml`

## database | datasource | schema | multi-db

- 20260408-hr-sync-poc-snapshot-list-and-sample-data | Optional `ext_employee.snapshot_id` (or thin `hr_sync_poc_snapshot`) + migration/init seed for ≥2 PoC snapshots; writes limited to `ext_*` / ETL role
- 20260320-multi-datasource-schema-configuration | Multi-datasource config: system+PB on DB A (`logmng_sys`, `logmng`), Java FW ImageLog on DB B (`public`); setup scripts + Spring Boot configurable URLs/schemas; single-DB dev backward compatible

## log-type | 로그 타입 | dynamic

- 20260208-dynamic-log-type-management | 동적 로그 타입 관리 기능
- 20260320-pb-feplog-empty-results-media-code-verification | PB FEP Log: verify empty search for media code SAAAA100 from 2023 — true data absence vs API/filter bug; read-only DB count vs POST search evidence
- 20260326-pb-fep-log-search-screen-wireframe | Screen `pb-fep-log-search` only: SVG v10 authoritative for visual/IA (vs notes v11: e.g. 조회일자+시작/종료시간); dedicated `POST .../pb-fep-log-search`; grid/toolbar/table/stream per SVG; §2.D DB mapping from `schema_pb_fep.sql`; legacy `pb-feplog` keeps `db-refactored/search` only
- 20260330-pb-fep-expand-all-cross-page | `pb-fep-log-search`: expand/collapse all across all pagination pages; 전체 접기 with opposite chevron; manual row toggles keep global control in sync (none / all / mixed)

## auth | logout | 로그인 | 로그아웃

- 20260408-my-page-local-password-and-profile | Local (non-AD) provisioning: initial password `user123` (stored hashed); My page modal from top-right user icon; read-only department/name; self password change; AD vs local behavior TBD in Contract
- 20260407-external-dept-employee-ad-login | External org replica (`ext_department`/`ext_employee`), admin provisioning search/register APIs, `auth.login.mode` local vs AD, zero-permission modal + server 403 on protected APIs; alternate login URL deployment note
- 20260225-logout-persist-after-refresh | 로그아웃 후 새로고침 시 로그인 상태 유지 버그

## misc | bugfix | pretty | highlighting

Auto-managed fallback section for requirement docs whose topic does not clearly match another section.

- 20260206-pretty-mode-highlighting-fix | Pretty 모드 하이라이팅 표시 문제 수정
- 20260316-login-id-user-name-display | 1. **Login**: Users must log in using their **user ID** (the canonical identifier, i.e. `app_user.username`). The login UI and documentation must present this as "user ID" rather than "user name".
- 20260316-user-id-numeric-userid-naming | Define canonical **user ID** values for the sample users and unify the naming of variables and fields that represent user ID across the system.
