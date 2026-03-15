# 공통 계약 (Contract)

프론트엔드·백엔드·DB 작업 시 **이 문서와 참조 스펙을 기준**으로 한다. API·스키마 변경 시 여기에 맞추거나, 변경 시 이 문서/스펙을 먼저 갱신한다.

## 환경·포트 (단일 진실)

| 구분 | 값 | 설정 위치 |
|------|-----|-----------|
| 백엔드 API | http://localhost:9200/api | backend application.yml, frontend .env REACT_APP_API_BASE_URL |
| 프론트엔드 | http://localhost:3001 | frontend/.env PORT |
| DB | localhost:5432, DB logmng | backend application.yml datasource |

## API 규격

- **정의 위치**: `docs/api-definition.md`(현재 구현 API 목록·요청/응답), `specs/*.spec.yaml` 또는 기능별 요건 문서의 API 섹션. 새 API/변경 시 해당 스펙을 먼저 작성·수정한다.
- **구현**: 백엔드는 스펙에 정의된 경로·메서드·요청/응답 형식을 따른다. 프론트엔드는 동일 스펙을 참고해 호출한다.
- **공통 베이스**: `/api` (백엔드 context-path 아님 경우 application.yml 기준).
- **API 정의서**: [docs/api-definition.md](api-definition.md) — 인증, 헬스, 로그 타입, DB 로그 검색/상세/복호화, 검색 추천, 검색 이력(승인 대기·승인·반려 포함), 사용자 관리(결재자 지정, PUT /api/users/{userId} 410 Gone), 활동 이력, DB 테스트 등 구현된 API 전부. 복호화 결재자 관련 API는 api-definition §6.1.5·6.1.6·6.1.7 및 §7 참고. **부서별 결재자·부서 멤버·팀장 지정**: api-definition §12. **권한 그룹·사용자 권한 계층**: api-definition §14, 스펙 `specs/permission-group-hierarchy.spec.yaml`. 권한 그룹 CRUD 및 그룹별 사용자 할당/해제는 **사용자 권한 계층** 화면에서만 제공하며, 별도의 "권한 그룹 관리" 메뉴/화면은 두지 않는다.
- **공유 부서 필터 옵션 계약**: 활동 이력(`activity-log`), 활동 통계(`statistics`), 검색 이력(`search-history`)의 부서 콤보박스는 공유 엔드포인트 `GET /api/filter-options/departments?screen={screenId}`를 사용한다. 이 엔드포인트는 **편집 가능한 부서 옵션 목록**의 권위 소스이며, `screen`은 `activity-log | statistics | search-history` 중 하나다. 백엔드는 해당 화면의 접근 권한과 scope를 기준으로 옵션을 계산한다. 응답은 프론트가 바로 `<select>`에 넣는 `string[]`이며 `"전체"` 옵션은 클라이언트가 로컬로 추가한다. 확인된 규칙: `scope=team`이면 **현재 사용자의 자기 부서만** 옵션에 포함되어야 하며, 다른 부서는 노출하지 않는다. `scope=self`에서는 이 엔드포인트를 잠긴 self-context 표시값의 권위 소스로 간주하지 않는다. self 화면의 고정 표시값(`department`, `username`, `userId`)은 `GET /api/auth/me` 등 auth/current-user payload를 기준으로 표시해야 한다. 이 계약은 관리자/관리화면용 `GET /api/departments`와 분리된다. 기존 `GET /api/statistics/departments`는 새 개발의 기준이 아니며, 구현 전환 중에도 새 공유 API는 editable options source로만 간주한다. 상세 요청/응답/소비 화면은 `docs/api-definition.md`의 공유 필터 옵션 섹션 및 `specs/permission-group-hierarchy.spec.yaml` §4.3을 따른다.

## DB 스키마

- **정의 위치**: `backend/src/main/resources/db/schema.sql` 및 필요 시 `specs/` 내 스키마 기술.
- **변경**: 스키마 변경 시 schema.sql(또는 마이그레이션)을 먼저 반영하고, 백엔드 코드·API 스펙을 그에 맞춘다.
- **권한 그룹 관련 테이블** (요건 20250227, 20250303, 20260306): `permission_group` (id, code, name, description, sort_order), `app_user_permission_group` (user_id = app_user.username, permission_group_id → permission_group.id), `permission_group_screen` (permission_group_id, screen_id, scope, read, write, approve, decrypt — read/write/approve/decrypt BOOLEAN NULL; decrypt는 main 전용, 복호화 요청 권한; scope='self'|'team'|'all', activity-log·statistics·search-history·pending-approvals에 적용, 생략/NULL 시 기본값 'team'; NULL=derived). 상세: `specs/permission-group-hierarchy.spec.yaml` §2.1, schema.sql.

## 시스템 관리자 보호 (System administrator protection)

- **요건**: `docs/requirements/20250303-permission-group-delete-system-admin-protection.md`
- **규칙**: `is_system_admin = true`인 사용자는 역할 변경·삭제 불가. 최소 1명의 시스템 관리자 유지. `PUT /api/users/{userId}` 시 `SYSTEM_ADMIN_IMMUTABLE` 또는 `LAST_SYSTEM_ADMIN_BLOCKED` 반환. 상세: `docs/api-definition.md` §7.2, §11.

## 화면 기반 접근 제어 (Screen-based access)

- **요건**: `docs/requirements/20250227-permission-group-screen-menu-access.md`, `docs/requirements/20250303-screen-function-availability.md`
- **화면 ID 목록**: main, search-history, activity-log, statistics, pending-approvals, user-management, department-approvers, user-permission-hierarchy, permission-group-management. 상세 및 화면↔API 매핑: `specs/permission-group-hierarchy.spec.yaml` §4.
- **규칙**: `is_system_admin = true` 사용자는 모든 화면 접근. 비관리자는 자신의 권한 그룹 중 하나라도 해당 화면을 허용해야 접근 가능. 화면에 대응하는 API 호출 시 사용자가 해당 화면을 갖지 않으면 403 반환. (req 20250303: role 제거, is_system_admin 사용)
- **화면별 범위(scope)**: activity-log, statistics, search-history, pending-approvals는 권한 그룹별 scope('self'|'team'|'all') 적용, 기본값 'team' (생략/NULL 시). is_system_admin=false일 때 scope='self' → 본인 데이터만(승인 대기: 본인 요청만); scope='team' → 동일 부서(department_code)만; scope='all' → 전체. applicable shared-pattern 화면에서 effective `scope=self`는 user/requester block을 숨기는 의미가 아니라 **visible locked self-context**를 의미한다. 즉 `department -> username -> userId` 순서의 세 필드는 화면에 계속 보이되 현재 인증 사용자 값으로 고정되고 수정할 수 없다. 이때 잠긴 self-context 표시값의 권위 소스는 auth/current-user payload이며, `userId`의 canonical meaning은 `app_user.username`이다. 특히 `activity-log`에서 effective `scope=self`이면 `department`(빈 값, `all`, `ALL`, `전체` 등 전체 표현 포함), `username`, `userId`, `departmentCode`, `ipAddress` 및 동등한 사용자 범위 확장 입력은 조회 범위를 넓히지 못하며, 백엔드는 이를 무시하거나 현재 사용자 기준 값으로 안전하게 override하여 결과를 항상 현재 사용자 본인 로그로 고정해야 한다. `search-history`의 requester filter(`department`, `username`, `userId`)는 조회 범위를 넓히지 않는 narrowing-only 조건이며, `scope=self`에서는 무시되고 `scope=team/all`에서만 허용 집합 내부 추가 필터로 적용된다. 상세: `specs/permission-group-hierarchy.spec.yaml` §4.3, `docs/requirements/20250303-activity-statistics-self-only-scope.md`, `docs/requirements/20250304-team-scope-default-and-approval.md`, `docs/requirements/20260305-pending-approvals-scope-same-as-search-history.md`.
- **승인 범위**: 승인(approve) 가능 범위는 부서로 고정되어 있으며 권한 설정에서 변경할 수 없음. scope 드롭다운은 조회(목록) 범위만 적용됨. (`specs/permission-group-hierarchy.spec.yaml` §1.1 Scope values, `docs/workflow/CONSISTENCY-STANDARDS.md` §7.)
- **auth/current-user self-context 계약**: 로그인 시 사용하는 식별자는 **user ID**(`app_user.username`)이다. 로그인 UI·문서에서는 이 값을 "사용자 ID"(user ID)로 표기한다. `POST /api/auth/login`의 `user` payload와 `GET /api/auth/me` 응답은 self-scoped 화면 고정 표시용 `selfContext`를 포함해야 한다. 최소 필드는 `department: string | null`, `username: string`, `userId: string`이다. **`userId`**는 canonical **`app_user.username`**(user ID)로 고정한다. **`selfContext.username`**은 현재 사용자의 **표시 이름(display name, 사용자명)**이다: `app_user.name`이 존재하고 비어 있지 않으면 그 값을 사용하고, 그렇지 않으면 `app_user.username`을 사용한다. `scope=self` 화면은 이 값을 화면 표시의 권위 소스로 사용하고, 공유 filter-options 응답이나 사용자가 조작한 필터 입력을 권위 값으로 승격하면 안 된다.
- **screenFunctions** (req 20250303-screen-function-availability): 로그인·GET /api/auth/me 응답에 `screenFunctions: Record<screenId, { read, write?, approve?, decrypt? }>` 포함. 화면별 read/write/approve/decrypt 가능 여부. main은 read + optional decrypt(복호화 요청 권한); decrypt는 권한관리에서 부여/해제 가능(req 20260306). **screenFunctions explicit storage**: permission_group_screen.read/write/approve/decrypt에 명시 저장 시 해당 값 사용; NULL이면 기존 derivation 규칙 적용. 상세: `specs/permission-group-hierarchy.spec.yaml` §4.4.
- **API function-level enforcement**: decrypt(복호화 요청), approve(승인/반려), write(생성·수정·삭제) API는 해당 function 권한 검증. 권한 없으면 403, `code: "FUNCTION_NOT_ALLOWED"`. 복호화 API(POST /api/logs/decrypt/*)는 main 화면 접근 + screenFunctions.main.decrypt 필요; 없으면 403. write API는 function과 scope 모두 검증; scope=self일 때 타인 데이터 수정 시 403.
- **승인 전용 권한 그룹 (approval-only)**: `allowedScreenIds`에 `main` 없이 `pending-approvals`만 가진 그룹(예: APPROVE_USER, TEAM_APPROVER 등)은 **그룹 이름과 무관하게** 동일 UX/API 규칙(리다이렉트, 메뉴 필터링, 로그 API 403, 액션 숨김) 적용. 상세: `specs/permission-group-hierarchy.spec.yaml` §5.

이 문서는 dev 워크스페이스 전용이다. 변경 시 docs/README.md 등과 맞춘다.
