# 공통 계약 (Contract)

프론트엔드·백엔드·DB 작업 시 **이 문서와 참조 스펙을 기준**으로 한다. API·스키마 변경 시 여기에 맞추거나, 변경 시 이 문서/스펙을 먼저 갱신한다.

## 환경·포트 (단일 진실)

| 구분 | 값 | 설정 위치 |
|------|-----|-----------|
| 백엔드 API | http://localhost:9200/api | backend `spring` / env; browser base URL: frontend `REACT_APP_API_BASE_URL` at build, **or runtime** `window.__LOGMNG_RUNTIME_CONFIG__.apiBaseUrl` from `/runtime-config.js` (JDK static server: env **`LOGMNG_API_BASE_URL`** or `REACT_APP_API_BASE_URL`) |
| 프론트엔드 | http://localhost:3001 | frontend/.env PORT |
| DB — Primary (A) | 예: `localhost:5432`, DB `logmng` (배포마다 상이) | `spring.datasource.*` — 시스템 데이터 + PB FEP 로그 동일 JDBC 풀 |
| DB — Secondary ImageLog (B) | 별도 DB·풀 또는 Primary와 동일 | `app.datasource.imagelog.*` — Java FW ImageLog(`imagelog`) 전용 풀 |

### DB 다중 데이터소스·스키마

요건: `docs/requirements/20260320-multi-datasource-schema-configuration.md`.

- **Primary (A)**: `spring.datasource.*` 의미는 기존과 같다. 애플리케이션 시스템 데이터와 PB FEP 로그가 **동일 DB A**에서 이 풀로 접근한다.
- **Secondary ImageLog (B)**: ImageLog 전용 두 번째 Hikari 풀. 아래 `app.datasource.imagelog.*` 및 스키마 속성으로 구성한다.
- **단일 DB 개발**: `app.datasource.imagelog.url`이 비어 있으면 애플리케이션은 **Primary 데이터소스를 재사용**한다(로컬 단일 DB). JDBC URL·비밀번호 등 민감값은 문서/저장소에 실제 값을 적지 말고 환경 변수로만 설정한다.

**스키마 이름 (backend `application.yml` / env)**

| 속성 | 환경 변수 | 기본값 |
|------|-----------|--------|
| `app.db.schema.sys` | `APP_DB_SCHEMA_SYS` | `public` |
| `app.db.schema.pb` | `APP_DB_SCHEMA_PB` | `public` |
| `app.db.schema.imagelog` | `APP_DB_SCHEMA_IMAGELOG` | `public` |

**Secondary ImageLog JDBC (`app.datasource.imagelog.*` / env)**

| 속성 | 환경 변수 |
|------|-----------|
| `app.datasource.imagelog.url` | `APP_DATASOURCE_IMAGELOG_URL` |
| `app.datasource.imagelog.username` | `APP_DATASOURCE_IMAGELOG_USERNAME` |
| `app.datasource.imagelog.password` | `APP_DATASOURCE_IMAGELOG_PASSWORD` |
| `app.datasource.imagelog.driver-class-name` | `APP_DATASOURCE_IMAGELOG_DRIVER` |
| `app.datasource.imagelog.fail-fast` | `APP_DATASOURCE_IMAGELOG_FAIL_FAST` |
| `app.datasource.imagelog.initialization-fail-timeout-ms` | `APP_DATASOURCE_IMAGELOG_INIT_FAIL_TIMEOUT_MS` |
| `app.cors.allowed-origins` (comma-separated UI origins) | `CORS_ALLOWED_ORIGINS` |

**암·복호화 (`app.security` / `app.decryption` → env)**

| 속성 | 환경 변수 (둘 중 하나 또는 동시; 우선은 `application.yml` 플레이스홀더 순서) |
|------|-----------|
| `app.security.encryption-key` | `ENCRYPTION_KEY`, 또는 `APP_SECURITY_ENCRYPTION_KEY` |
| `app.decryption.enabled` | `DECRYPTION_ENABLED`, 또는 `APP_DECRYPTION_ENABLED` |
| `app.decryption.auto-decrypt-on-keyword-search` | `AUTO_DECRYPT_ON_KEYWORD_SEARCH`, 또는 `APP_DECRYPTION_AUTO_DECRYPT_ON_KEYWORD_SEARCH` |
| `app.decryption.failure-handling` (`fallback` \| `skip` \| `error`) | `FAILURE_HANDLING`, 또는 `APP_DECRYPTION_FAILURE_HANDLING` |

**Static UI browser→API base (no rebuild)** — env `LOGMNG_API_BASE_URL` (preferred) or `REACT_APP_API_BASE_URL` on the JDK static-server process; served as `/runtime-config.js`. See `scripts/offline-bundle/README-OFFLINE.md` § API URL.

**Air-gap bundle**: `scripts/package-airgap-bin.sh` fills `bin/` with backend fat JAR, static UI (`www/`), and JDK-only static server JAR; see `bin/README.md`. **Full offline server package** (DB scripts + installer): `scripts/release-build.sh` (default, no args) or `scripts/build-offline-bundle.sh` → `dist/logmng-offline-*.tar.gz`; optionally run `scripts/download-psql-for-bundle.sh` first to bundle `psql` debs. On the target host extract and run `./install-offline.sh all` (see `scripts/offline-bundle/README-OFFLINE.md`).

## API 규격

- **정의 위치**: `docs/api-definition.md`(현재 구현 API 목록·요청/응답), `specs/*.spec.yaml` 또는 기능별 요건 문서의 API 섹션. 새 API/변경 시 해당 스펙을 먼저 작성·수정한다. **활동 이력 `action_type` 코드·선택 `GET /api/activity-log/action-types`**: `specs/activity-action-types.spec.yaml`.
- **구현**: 백엔드는 스펙에 정의된 경로·메서드·요청/응답 형식을 따른다. 프론트엔드는 동일 스펙을 참고해 호출한다.
- **공통 베이스**: `/api` (백엔드 context-path 아님 경우 application.yml 기준).

### PB FEP wireframe search API (English contract summary)

**Requirement**: `docs/requirements/20260326-pb-fep-log-search-screen-wireframe.md` (§2.G, §2.D).

| Item | Definition |
|------|------------|
| **Path** | `POST /api/logs/db-refactored/pb-fep-log-search` (exact; wireframe screen **`pb-fep-log-search`** only). |
| **Purpose** | Return PB FEP union rows (`pb_send` / `pb_recv`, log type **`pb_feplog`**) with **wireframe-facing JSON keys** for the **`pb-fep-log-search`** UI. |
| **Does not replace** | **`POST /api/logs/db-refactored/search`** remains the contract for legacy **`pb-feplog`** and all other `logType` callers; this path must not change legacy **`pb_feplog`** response shape or behavior on `/search`. |
| **Auth / access** | Same enforcement family as today’s log search for **PB FEP** / log type **`pb_feplog`**: user must have screen access compatible with PB FEP search — i.e. **`pb-fep-log-search`** (and the same **`pb_feplog`** / **`pb-feplog`** rules as in this document’s API function-level enforcement). Do not weaken checks relative to **`POST .../search`** for `pb_feplog`. |
| **Request body** | Align with **`LogDbSearchRequest`**: datetime bounds (`startDate`, `endDate` — client resolves wireframe **조회일자 + 시작시간/종료시간** to full bounds per product rules), **`loginId`** (required, non-blank), optional **`trCode`** / **`tr_code`**, **`keywords`** (`string[]`, comma-split tokens from UI may be sent as array), **`page`**, **`pageSize`**, optional **`displayTemplate`**, and **`sortSpecs`**: ordered `{ field, direction }[]` for **cumulative** multi-column sort. Server applies an **allowlist** of sort `field` values matching wireframe semantics (reject unknown fields; **no SQL injection**). When `sortSpecs` is non-empty, it takes precedence over legacy **`sortField` / `sortDirection`** for ordering (same pattern as PB FEP on `/search`). **Screen defaults** (product): **`pageSize`** default **25**; allowed **25 / 50 / 100** for this screen unless implementation maps from request with validation. Other fields on `LogDbSearchRequest` may be omitted or ignored when irrelevant to PB FEP. |
| **Response envelope** | Common **`ApiResponse`**: `success`, `data` object with **`data`**: **array** of row objects and **`pagination`**: `{ currentPage, totalPages, totalCount }` — same family as **`LogDbSearchResponse`** on **`POST .../search`**. |
| **Row object (wireframe keys)** | Each element of **`data`** uses **stable keys** for UI and **`expandedRowKeys`**: include **`id`** (numeric row id), **`log_type`** or equivalent branch discriminator (**`send`** / **`recv`** or service-defined token pairing with `id` for uniqueness across UNION), plus: **`log_timestamp`**, **`tr_code`**, **`login_id`** (from DB `user_id`), **`msg_code`** (from `status_code`, display string per formatter), **`bmsg`** (`error_message`), **`log_ch_cd`** (`device_type`), **`send_recv`** (**`SEND`** / **`RECV`** by union branch), **`src_ip`** (`ip_address`), **`dest_ip`** (placeholder: empty string or em dash **`—`** until a dedicated column exists; do not duplicate `src_ip`), **`app_id`** (prefer `session_id`; empty if null), **`data`** (cell summary / stream source per product and decrypt rules). Optionally **`request_data`** / **`response_data`** when needed for expanded **STREAM DATA** lines; behavior follows existing PB FEP decrypt/display rules. |
| **Errors** | Validation and allowlist failures: **400** with project error `code` (e.g. `INVALID_INPUT`). Missing PB FEP / screen permission: **403** `LOG_TYPE_NOT_ALLOWED` or **`FUNCTION_NOT_ALLOWED`** per existing patterns. See `docs/api-definition.md` §5.1.1 and `specs/log-db-pb-fep-log-search.spec.yaml`. |
- **API 정의서**: [docs/api-definition.md](api-definition.md) — 인증, 헬스, 로그 타입, DB 로그 검색/상세/복호화, 검색 추천, 검색 이력(승인 대기·승인·반려 포함), 사용자 관리(결재자 지정, PUT /api/users/{userId} 410 Gone; path `userId`는 numeric `app_user.id`), 활동 이력, DB 테스트 등 구현된 API 전부. 복호화 결재자 관련 API는 api-definition §6.1.5·6.1.6·6.1.7 및 §7 참고. **부서별 결재자·부서 멤버·팀장 지정**: api-definition §12. **권한 그룹·사용자 권한 계층**: api-definition §14, 스펙 `specs/permission-group-hierarchy.spec.yaml`. 권한 그룹 CRUD 및 그룹별 사용자 할당/해제는 **사용자 권한 계층** 화면에서만 제공하며, 별도의 "권한 그룹 관리" 메뉴/화면은 두지 않는다. **PB FEP 와이어프레임 전용 검색**: `POST /api/logs/db-refactored/pb-fep-log-search` — api-definition §5.1.1, 스펙 `specs/log-db-pb-fep-log-search.spec.yaml`.
- **공유 부서 필터 옵션 계약**: 활동 이력(`activity-log`), 활동 통계(`statistics`), 검색 이력(`search-history`)의 부서 콤보박스는 공유 엔드포인트 `GET /api/filter-options/departments?screen={screenId}`를 사용한다. 이 엔드포인트는 **편집 가능한 부서 옵션 목록**의 권위 소스이며, `screen`은 `activity-log | statistics | search-history` 중 하나다. 백엔드는 해당 화면의 접근 권한과 scope를 기준으로 옵션을 계산한다. 응답은 프론트가 바로 `<select>`에 넣는 `string[]`이며 `"전체"` 옵션은 클라이언트가 로컬로 추가한다. 확인된 규칙: `scope=team`이면 **현재 사용자의 자기 부서만** 옵션에 포함되어야 하며, 다른 부서는 노출하지 않는다. `scope=self`에서는 이 엔드포인트를 잠긴 self-context 표시값의 권위 소스로 간주하지 않는다. self 화면의 고정 표시값(`department`, `username`, `userId`)은 `GET /api/auth/me` 등 auth/current-user payload를 기준으로 표시해야 하며, `userId`는 numeric `app_user.id`이다. 이 계약은 관리자/관리화면용 `GET /api/departments`와 분리된다. 기존 `GET /api/statistics/departments`는 새 개발의 기준이 아니며, 구현 전환 중에도 새 공유 API는 editable options source로만 간주한다. 상세 요청/응답/소비 화면은 `docs/api-definition.md`의 공유 필터 옵션 섹션 및 `specs/permission-group-hierarchy.spec.yaml` §4.3을 따른다.
- **활동 유형(`action_type`) 단일 기준 (요건 `20260330-activity-types-user-mgmt-permission-group`, OP-01)**: `user_activity_log.action_type`에 저장되는 값은 **대소문자 구분 UPPER_SNAKE_CASE** 문자열이며, 허용 코드의 **닫힌 집합**은 `specs/activity-action-types.spec.yaml` §2와 백엔드 `ActivityActionType`(또는 동등 상수)이 공유한다. `POST /api/activity-log/search`의 **`actionType`** 필터는 이 코드와 정확히 일치한다(빈 값 = 전체 유형). **활동 유형 필터 드롭다운** 옵션은 프론트엔드 하드코드 목록이 아니라 **권위 있는 목록**을 쓰는 것이 계약상 기대이며, 선택적 **`GET /api/activity-log/action-types`**(동등 경로: `GET /api/filter-options/activity-action-types`)가 `code` + `label` 배열을 반환한다. 인증·접근은 **activity-log** 화면 읽기 권한과 동일 계열이다. `action_detail` JSON은 카테고리별 비민감 식별자·메타데이터만 담으며, 비밀번호·토큰·복호화 본문은 저장하지 않는다. 상세 코드표·`action_detail` 범주·통계 KPI와의 관계(OP-02)는 `specs/activity-action-types.spec.yaml`을 따른다.
- **권한 그룹 감사 `action_detail` (요건 `20260330-permission-group-activity-detail-audit`, MF-01)**: `PERMISSION_GROUP_*`, `ASSIGN_USER_TO_PERMISSION_GROUP`, `UNASSIGN_USER_FROM_PERMISSION_GROUP` 등 권한 그룹 관련 `action_type`에 대해, 구현 목표 스키마는 **`specs/activity-permission-group-audit.spec.yaml`**의 **`permissionGroupAuditV1`** 객체이다. `before` / `after`는 `PermissionGroupCreateRequest`·`PermissionGroupUpdateRequest`·`AllowedScreenItem`과 정렬된 **`PermissionGroupSnapshot`**(중첩 `allowedScreens`)을 사용한다(OP-PG-01). **Denylist**(비밀번호·토큰·원시 요청 본문 등)는 부모 요건 `20260330-activity-types-user-mgmt-permission-group` §2.1 Security 및 해당 스펙 §6을 따른다.
- **`changeReason` (MF-03, OP-PG-02, 잠정)**: 계약상 잠정안은 **`permissionGroupAuditV1.changeReason`**으로 **영속**하되 **최대 500자**로 절단한다. 제품이 **v1에서 생략**으로 바꾸면 계약·스펙을 먼저 수정한 뒤 구현한다.
- **DOC-CODE-SYNC (본 기능)**: Step 4 구현 시 백엔드·프론트는 저장/표시되는 `action_detail`이 **`activity-permission-group-audit.spec.yaml`**과 일치하도록 맞춘다. 의도적 차이가 있으면 같은 변경에서 계약·스펙을 갱신한다(`docs/workflow/DOC-CODE-SYNC.md`). **활동 이력 내보내기(export)** 는 미구현; 구현 시 MF-05(열·마스킹·검색과 동일 scope)를 추가한다.

## DB 스키마

- **정의 위치**: `backend/src/main/resources/db/schema.sql` 및 필요 시 `specs/` 내 스키마 기술.
- **변경**: 스키마 변경 시 schema.sql(또는 마이그레이션)을 먼저 반영하고, 백엔드 코드·API 스펙을 그에 맞춘다.
- **권한 그룹 관련 테이블** (요건 20250227, 20250303, 20260306): `permission_group` (id, code, name, description, sort_order), `app_user_permission_group` (user_id = app_user.username, permission_group_id → permission_group.id), `permission_group_screen` (permission_group_id, screen_id, scope, read, write, approve, decrypt — read/write/approve/decrypt BOOLEAN NULL; decrypt는 main 전용, 복호화 요청 권한; scope='self'|'team'|'all', activity-log·statistics·search-history·pending-approvals에 적용, 생략/NULL 시 기본값 'team'; NULL=derived). 상세: `specs/permission-group-hierarchy.spec.yaml` §2.1, schema.sql.
- **검색 이력(search_history)**: `search_history.user_id`는 numeric **`app_user.id`**를 저장한다. 모든 조인은 **app_user.id = search_history.user_id**를 사용한다. `search_history.user_id`에 username을 저장하지 않는다. API의 requester filter·응답의 userId는 numeric `app_user.id`이다. **요청 사유**: POST body·목록/상세 응답에 `requestReason`; 목록 조회 쿼리에 `requestedAtFrom`, `requestedAtTo`, `approvalStatus`(다중), `requestReason`. **상세 조회 응답 (req 20260318, 20260320)**: APPROVED일 때 `decryptionRequestedRows`(`application`, `serviceGroup`, `guid`, **`status`**), `decryptionRequestedCount` 포함; 비승인 시 생략 또는 null. 상세: docs/api-definition.md §6.1.
- **복호화 허용 저장소 (decryption-allowed, req 20260318, 20260320)**: java_fw_imglog는 **(guid, status)** 복합 키로 허용 여부를 판단한다. **decryption-allowed store**(예: `user_decryption_allowed`) 키: user_id(BIGINT), screen, guid, **row_status**; valid_until. `search_history_approved_row`는 **(row_id, row_status)** 복합 PK로 감사/스냅샷용이며, 복호화 권한 판단에는 decryption-allowed store만 사용한다. **저장 대상**: 승인 시 **암호화된 데이터가 있는 행**만 스냅샷·허용 집합에 포함. 정의는 아래 "Decryption approval — rows with encrypted data only" 참고. 스키마·마이그레이션: schema.sql 및 db 마이그레이션 스크립트.

## Decryption approval — rows with encrypted data only

요건: `docs/requirements/20260318-decryption-approval-guids-encrypted-only.md`. 승인 스냅샷 및 decryption-allowed 집합에는 **암호화된 데이터가 있는 행의 row ID만** 포함된다. 백엔드는 이 정의를 단일 진실 원천으로 구현한다.

1. **java_fw_imglog — "has encrypted data" 정의 (단일 진실)**  
   한 행이 암호화된 데이터를 가진다고 정의하는 조건(iff):  
   `(datastring != null && datastring.contains("["))` OR `(headerstring != null && headerstring.contains("["))` OR `(data != null && !((String)data).trim().isEmpty())` OR `(header != null && !((String)header).trim().isEmpty())`.  
   위 조건을 만족하는 행만 승인 시 스냅샷·허용 집합에 포함된다.

2. **저장 제한**  
   `POST /api/search-history/{id}/approve` 처리 시, 검색 결과 중 **has encrypted data**가 true인 행의 **(row_id, row_status)** 만 (1) `search_history_approved_row`에 insert되고 (2) `user_decryption_allowed`(decryption-allowed store)에 **복합 키**로 반영된다. 평문만 있는 행은 두 저장소 모두에 넣지 않는다.

3. **pb_feplog**  
   현재 복호화 미지원. 승인 스냅샷·decryption-allowed에 pb_feplog 행을 넣지 않거나, 추후 스펙에서 정의할 때까지 해당 로그 타입은 "rows with encrypted data" 규칙의 적용 대상이 아니다.

## 시스템 관리자 보호 (System administrator protection)

- **요건**: `docs/requirements/20250303-permission-group-delete-system-admin-protection.md`
- **규칙**: `is_system_admin = true`인 사용자는 역할 변경·삭제 불가. 최소 1명의 시스템 관리자 유지. `PUT /api/users/{userId}` 시 `SYSTEM_ADMIN_IMMUTABLE` 또는 `LAST_SYSTEM_ADMIN_BLOCKED` 반환. Path `{userId}`는 numeric `app_user.id`이다. 상세: `docs/api-definition.md` §7.2, §11.

## 화면 기반 접근 제어 (Screen-based access)

- **요건**: `docs/requirements/20250227-permission-group-screen-menu-access.md`, `docs/requirements/20250303-screen-function-availability.md`
- **화면 ID 목록**: main(폐지 예정), pb-feplog, pb-fep-log-search, java-fw-imagelog, search-history, activity-log, statistics, pending-approvals, user-management, department-approvers, user-permission-hierarchy, permission-group-management. 로그 검색: pb-feplog(PB FEP Log), pb-fep-log-search(PB FEP 로그 검색, 동일 API logType `pb_feplog`, 별도 권한), java-fw-imagelog(Java FW Image Log). 상세 및 화면↔API 매핑: `specs/permission-group-hierarchy.spec.yaml` §4.
- **규칙**: `is_system_admin = true` 사용자는 모든 화면 접근. 비관리자는 자신의 권한 그룹 중 하나라도 해당 화면을 허용해야 접근 가능. 화면에 대응하는 API 호출 시 사용자가 해당 화면을 갖지 않으면 403 반환. (req 20250303: role 제거, is_system_admin 사용)
- **화면별 범위(scope)**: activity-log, statistics, search-history, pending-approvals는 권한 그룹별 scope('self'|'team'|'all') 적용, 기본값 'team' (생략/NULL 시). is_system_admin=false일 때 scope='self' → 본인 데이터만(승인 대기: 본인 요청만); scope='team' → 동일 부서(department_code)만; scope='all' → 전체. applicable shared-pattern 화면에서 effective `scope=self`는 user/requester block을 숨기는 의미가 아니라 **visible locked self-context**를 의미한다. 즉 `department -> username -> userId` 순서의 세 필드는 화면에 계속 보이되 현재 인증 사용자 값으로 고정되고 수정할 수 없다. 이때 잠긴 self-context 표시값의 권위 소스는 auth/current-user payload이며, **`userId`**는 **numeric** **`app_user.id`**(예: 20269999, 20260001)이다. 특히 `activity-log`에서 effective `scope=self`이면 `department`(빈 값, `all`, `ALL`, `전체` 등 전체 표현 포함), `username`, `userId`, `departmentCode`, `ipAddress` 및 동등한 사용자 범위 확장 입력은 조회 범위를 넓히지 못하며, 백엔드는 이를 무시하거나 현재 사용자 기준 값으로 안전하게 override하여 결과를 항상 현재 사용자 본인 로그로 고정해야 한다. `search-history`의 requester filter(`department`, `username`, `userId`)는 조회 범위를 넓히지 않는 narrowing-only 조건이며, `scope=self`에서는 무시되고 `scope=team/all`에서만 허용 집합 내부 추가 필터로 적용된다. 상세: `specs/permission-group-hierarchy.spec.yaml` §4.3, `docs/requirements/20250303-activity-statistics-self-only-scope.md`, `docs/requirements/20250304-team-scope-default-and-approval.md`, `docs/requirements/20260305-pending-approvals-scope-same-as-search-history.md`.
- **승인 범위**: 승인(approve) 가능 범위는 부서로 고정되어 있으며 권한 설정에서 변경할 수 없음. scope 드롭다운은 조회(목록) 범위만 적용됨. (`specs/permission-group-hierarchy.spec.yaml` §1.1 Scope values, `docs/workflow/CONSISTENCY-STANDARDS.md` §7.)
- **복호화 승인 자격 (검색 이력·승인 대기)** (요건 `docs/requirements/20260323-approver-eligibility-from-permission-group-only.md`, `docs/requirements/20260323-approver-eligibility-from-permission-group-only-PRODUCT-QA.md`):
  - **권위(source of truth)**: `app_user_permission_group` → `permission_group_screen`에서 **`search-history`** 및/또는 **`pending-approvals`** 에 대해 **`approve`(승인)** 가 허용된 경우에만, 해당 화면이 관여하는 **복호화 흐름의 승인·반려**를 수행할 수 있다. 레거시 `decrypt_approver` 테이블은 승인 **자격** 판단에 사용하지 않으며, 마이그레이션 후 스키마에서 제거한다.
  - **“관리자(admin)” 계정의 정의(본 항·승인 정책에 한함)**: **`app_user.is_system_admin = true`인 사용자만** “시스템 관리자 계정”으로 본다. **`ADMIN_EXT` 등 권한 그룹을 통해 넓은 관리 기능을 가진 사용자**이더라도 **`is_system_admin = false`이면** 본 승인 정책에서 관리자로 **분류하지 않는다**. 그들의 승인 가능 여부는 **권한 그룹에 지정된 화면별 `approve`와 아래 동일 부서 규칙만**으로 결정하며, 그룹 배치·운영은 운영자 관리에 맡긴다.
  - **공통 판별(구현 모델)**: 승인 자격은 **먼저** 권한 그룹 기준으로 “해당 화면에 승인 권한을 가진 사용자인지”를 판정한다. **`is_system_admin = true`인 경우에 한해**, 위에서 그룹으로 승인이 부여되어 있더라도 **검색 이력·승인 대기 관련 승인·반려는 불가**로 한다(해당 화면의 유효 승인 권한을 시스템 관리자 플래그가 **상쇄**). “admin이면 무조건 false” 단일 분기로 그룹 검사를 생략하지 않고, **그룹 기반 승인 보유 여부를 계산한 뒤 `is_system_admin`이면 제외**하는 방식이 계약상 권장 모델이다.
  - **요청 단위(동일 부서)**: 특정 요청에 대한 승인·반려는 **승인자의 `department_code`와 요청자의 `department_code`가 동일할 때만** 허용한다(상위 부서 체인 기준 승인은 사용하지 않음).
- **auth/current-user self-context 계약**: 로그인 식별자는 **`app_user.id`** (numeric, 사용자 ID)만 사용한다. 로그인 UI와 API에서는 사용자가 **숫자 사용자 ID**(예: 20269999, 20260001)를 입력한다. `POST /api/auth/login` 요청 body는 **userId (number)** 와 **password** 를 사용한다. (username 필드는 로그인 요청에서 제거.) API/UI에서 노출하는 **canonical "userId"**는 **numeric** **`app_user.id`**(예: 20269999, 20260001)이다. `POST /api/auth/login`의 `user` payload와 `GET /api/auth/me` 응답은 self-scoped 화면 고정 표시용 `selfContext`를 포함해야 한다. 최소 필드는 `department: string | null`, `username: string`, `userId: number`이다. **`selfContext.userId`**는 **numeric** **`app_user.id`**(user ID)이다. **`selfContext.username`**은 현재 사용자의 **표시 이름(display name, 사용자명)**이다: `app_user.name`이 존재하고 비어 있지 않으면 그 값을 사용하고, 그렇지 않으면 `app_user.username`을 사용한다. `scope=self` 화면은 이 값을 화면 표시의 권위 소스로 사용하고, 공유 filter-options 응답이나 사용자가 조작한 필터 입력을 권위 값으로 승격하면 안 된다.
- **screenFunctions** (req 20250303-screen-function-availability): 로그인·GET /api/auth/me 응답에 `screenFunctions: Record<screenId, { read, write?, approve?, decrypt? }>` 포함. 화면별 read/write/approve/decrypt 가능 여부. pb-feplog, java-fw-imagelog는 read + optional decrypt(복호화 요청 권한); decrypt는 권한관리에서 부여/해제 가능(req 20260318). **screenFunctions explicit storage**: permission_group_screen.read/write/approve/decrypt에 명시 저장 시 해당 값 사용; NULL이면 기존 derivation 규칙 적용. 상세: `specs/permission-group-hierarchy.spec.yaml` §4.4. **`search-history`·`pending-approvals`의 `approve`**: 위 **「복호화 승인 자격」** 을 따른다(권한 그룹의 `approve`를 먼저 반영한 뒤 **`is_system_admin`이면 해당 화면 approve를 유효하지 않음**으로 산출). `ADMIN_EXT` 등 비시스템관리자는 그룹 `approve`만으로 판단한다.
- **API function-level enforcement**: decrypt(복호화 요청), approve(승인/반려), write(생성·수정·삭제) API는 해당 function 권한 검증. 권한 없으면 403, `code: "FUNCTION_NOT_ALLOWED"`. 로그 검색 API **logType=pb_feplog** 접근: 사용자 `allowedScreenIds`에 **pb-feplog 또는 pb-fep-log-search** 중 하나 이상 필요. **java_fw_imglog**는 **java-fw-imagelog** 화면 필요. 해당 화면 없으면 403 `LOG_TYPE_NOT_ALLOWED`. 복호화 허용 목록(GET /api/decrypt/allowed)에서 `screen=pb-fep-log-search` 요청 시 저장·조회는 **pb-feplog**와 동일 키로 통합. 복호화 API(POST /api/logs/decrypt/*)는 해당 logType의 screen 접근 + decrypt 권한( pb-fep 계열 화면 중 하나에 decrypt 있으면 허용 ) 필요. **복호화 승인 소스 (req 20260318, 20260320)**: 복호화 허용 여부는 **decryption-allowed store**에서만 결정되며, java_fw_imglog는 **(guid, status)** 일치가 필요하다. POST /api/logs/decrypt/java_fw_imglog body에 **`status` 필수**(누락·공백 시 400 `MISSING_STATUS`). POST /api/logs/decrypt 는 **searchHistoryId를 승인 판단에 사용하지 않으며**, searchHistoryId는 감사(audit)용으로만 선택 전달 가능하다. **GET /api/decrypt/allowed**: 쿼리 `screen`, 응답 `{ screen, validUntil, guids, allowedRows: [{ guid, status }] }` — `allowedRows`가 UI 복합 허용 판단의 권위이며 `guids`는 하위 호환용 distinct guid 목록. 상세: `docs/api-definition.md` §10. write API는 function과 scope 모두 검증; scope=self일 때 타인 데이터 수정 시 403. **검색 화면 복호화 UI (req 20260317-search-decrypt-permission-ui)**: 사용자에게 해당 로그 타입 화면의 decrypt 권한이 없으면 복호화 액션 비활성/숨김 및 "복호화 권한이 없습니다." 표시.
- **승인 전용 권한 그룹 (approval-only)**: `allowedScreenIds`에 로그 검색 화면(pb-feplog, java-fw-imagelog) 없이 `pending-approvals`만 가진 그룹은 **그룹 이름과 무관하게** 동일 UX/API 규칙(리다이렉트, 메뉴 필터링, 로그 API 403, 액션 숨김) 적용. 상세: `specs/permission-group-hierarchy.spec.yaml` §5.

이 문서는 dev 워크스페이스 전용이다. 변경 시 docs/README.md 등과 맞춘다.
