# API 정의서

현재 백엔드에 구현된 API 목록 및 요청/응답 규격이다.  
**베이스 URL**: `http://localhost:9200/api` (환경·포트는 `docs/contract.md` 참고).

---

## 1. 공통 응답 형식

모든 API는 아래 공통 래퍼로 감싼다.

| 필드 | 타입 | 설명 |
|------|------|------|
| success | boolean | 성공 여부 |
| data | object / array | 성공 시 응답 본문 (엔드포인트별 상이) |
| message | string | (선택) 안내 메시지 |
| error | string | 실패 시 오류 메시지 |
| code | string | 실패 시 오류 코드 |

- 성공: `success: true`, `data`에 실제 데이터.
- 실패: `success: false`, `error`, `code` 사용. HTTP 상태는 400/500 등 적절히 반환.

---

## 2. 인증 (Auth)

**Base path**: `/api/auth`

### 2.1 로그인

- **POST** `/api/auth/login`
- **Request body** (JSON)

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| username | string | O | 사용자명 |
| password | string | O | 비밀번호 |

- **Response (data)**: `{ "user": LoginResponse }`
  - `user.username`: string
  - `user.loginTime`: string (yyyy-MM-dd'T'HH:mm:ss)
  - `user.clientIP`: string
  - `user.allowedScreenIds`: string[] (요건 20250227-permission-group-screen-menu-access) — 사용자 권한 그룹들의 접근 가능 화면 합집합. ADMIN은 전체 화면 또는 생략(전체 접근).

### 2.2 로그아웃

- **POST** `/api/auth/logout`
- **Request body**: 없음
- **Response (data)**: null

### 2.3 인증 상태 확인

- **GET** `/api/auth/check`
- **Response (data)**:
  - `authenticated`: boolean
  - `message`: string

### 2.4 현재 사용자 정보 (GET /api/auth/me, 선택)

- **GET** `/api/auth/me` — 로그인 사용자 정보 반환. 구현 시 응답에 `allowedScreenIds: string[]` 포함 (요건 20250227-permission-group-screen-menu-access). 로그인 응답과 동일 규칙.

---

## 3. 헬스 체크

- **GET** `/api/health`
- **Response (data)**:
  - `status`: string (예: "OK")
  - `timestamp`: string (yyyy-MM-dd'T'HH:mm:ss)
  - `message`: string

---

## 4. 로그 타입 (Log Types)

**Base path**: `/api/log-types`

### 4.1 로그 타입 목록 조회

- **GET** `/api/log-types`
- **Response (data)**: 배열. 각 항목:
  - `id`: string (예: "pb_feplog", "java_fw_imglog")
  - `name`: string
  - `description`: string
  - `tables`: string[] (예: ["pb_send", "pb_recv"], ["imagelog"])

### 4.2 로그 타입 상세 조회

- **GET** `/api/log-types/{typeId}`
- **Path**: `typeId` — "pb_feplog" | "java_fw_imglog"
- **Response (data)**: 단일 로그 타입 객체 (4.1과 동일 구조)
- **에러**: 존재하지 않는 typeId 시 `code: "LOG_TYPE_NOT_FOUND"`

### 4.3 로그 타입별 필드 메타데이터 조회

- **GET** `/api/log-types/{typeId}/fields`
- **Path**: `typeId` — 현재 **java_fw_imglog** 만 지원
- **Response (data)**: `FieldMetadataResponse[]`
  - `name`, `label`, `type`, `operatorsAllowed`, `isSortable`, `isFacetable`, `valueSource`, `enumValues`, `suggestApi`, `isEncrypted`
- **에러**: java_fw_imglog 외 타입 시 `code: "UNSUPPORTED_LOG_TYPE"`

---

## 5. DB 로그 (Logs DB)

**Base path**: `/api/logs/db-refactored`

### 5.1 로그 검색

- **POST** `/api/logs/db-refactored/search`
- **Request body** (JSON): `LogDbSearchRequest`

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| logType | string | | 기본 "pb_feplog" |
| startDate | string | | yyyy-MM-dd HH:mm:ss 등 |
| endDate | string | | yyyy-MM-dd HH:mm:ss 등 (미입력 시 오늘 23:59:59) |
| mediaCode / media_gb | string | | |
| trCode / tr_code | string | | |
| loginId | string | | |
| accountNumbers | string[] | | |
| application | string | | 이미지로그 |
| servicegroup | string | | 이미지로그 |
| service | string | | 이미지로그 |
| datastring | string | | 이미지로그 |
| headerstring | string | | 이미지로그 |
| keywords | string[] | | 이미지로그 |
| decryptData | boolean | | 기본 false |
| page | integer | | 기본 1 |
| pageSize | integer | | 기본 10 |
| sortField | string | | 기본 "log_timestamp" |
| sortDirection | string | | 기본 "desc" |
| displayTemplate | string | | 기본 "detailed" |

- **Response (data)**: `LogDbSearchResponse`
  - `data`: object[] (로그 행 목록)
  - `pagination`: `{ currentPage, totalPages, totalCount }`

### 5.2 로그 상세 조회

- **GET** `/api/logs/db-refactored/{logType}/{type}/{identifier}`
- **Path**:
  - pb_feplog: `type` = "send" | "recv", `identifier` = id (Long)
  - java_fw_imglog: `type` 무시, `identifier` = guid (String)
- **Response (data)**: Map (로그 한 건 상세)

### 5.3 복호화된 데이터 조회

- **GET** `/api/logs/db-refactored/{logType}/{type}/{identifier}/decrypt`
- **Path**: 5.2와 동일
- **Response (data)**: Map (복호화된 필드 포함)

### 5.4 로그 통계 조회

- **POST** `/api/logs/db-refactored/stats`
- **Request body**: `LogDbSearchRequest` (5.1과 동일)
- **Response (data)**: 현재 구현 예정 메시지 반환 (`message` 등)

### 5.5 스키마 정보 조회

- **GET** `/api/logs/db-refactored/schema`
- **Response (data)**: `{ tables: string[], message?: string }`

### 5.6 DB 연결 상태 확인

- **GET** `/api/logs/db-refactored/health`
- **Response (data)**: `{ status: "OK", message: string }`

### 5.7 고급 검색 (AST 기반)

- **POST** `/api/logs/db-refactored/advanced-search`
- **Request body** (JSON): `AdvancedSearchRequest`

| 필드 | 타입 | 설명 |
|------|------|------|
| logType | string | 현재 **java_fw_imglog** 만 지원 |
| queryText | string | 선택 |
| startDate | string | |
| endDate | string | |
| filters | FilterCondition[] | field, operator, value |
| sort | SortCondition[] | field, direction ("asc"\|"desc") |
| pagination | { page, pageSize } | 기본 page=1, pageSize=50 |
| decryptData | boolean | 기본 false |

- **Response (data)**: `LogDbSearchResponse` (5.1과 동일)
- **에러**: java_fw_imglog 외 타입 시 `code: "UNSUPPORTED_LOG_TYPE"`

---

## 6. 검색 추천 (Search Suggest)

- **GET** `/api/search/suggest`
- **Query**:
  - `logType`: string (필수) — 현재 **java_fw_imglog** 만 지원
  - `context`: string — "field" | "operator" | "value"
  - `prefix`: string (선택)
  - `fieldName`: string (선택)
- **Response (data)**: `List<Map<String, Object>>` (추천 목록)
- **에러**: java_fw_imglog 외 타입 시 `code: "UNSUPPORTED_LOG_TYPE"`

---

## 6.1 검색 이력 (Search History) — 복호화 승인 부가 기능

**Base path**: `/api/search-history`

- 검색 이력은 "복호화 승인 요청"이 발생한 검색을 저장하며, 사용자별 최근 이력 목록·재요청·재조회를 지원한다.
- 승인 유효 기간: 요청일시 + 1일. 만료 시 재요청 가능.

### 6.1.1 검색 이력 저장

- **POST** `/api/search-history`
- **Request body** (JSON):

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| logType | string | O | 로그 타입 (pb_feplog, java_fw_imglog 등) |
| searchParams | object | O | 검색 조건 (LogDbSearchRequest 또는 AdvancedSearchRequest와 동일한 구조의 JSON) |

- **Response (data)**: `{ "id": number, "requestedAt": string (yyyy-MM-dd'T'HH:mm:ss), "expiresAt": string, "approvalStatus": "PENDING" }`
- **에러**: 비인증 401, logType/searchParams 누락 400

### 6.1.2 검색 이력 목록 조회

- **GET** `/api/search-history`
- **Query**: `page` (기본 1), `pageSize` (기본 20), `sortField` (기본 requested_at), `sortDirection` (기본 desc)
- **Response (data)**: `SearchHistoryListResponse`
  - `data`: 배열. 각 항목: `seq` (목록 순번), `id`, `requestedAt`, `expiresAt`, `approvalStatus` (PENDING | APPROVED | EXPIRED | REJECTED), `searchParamsSummary` (요약 문자열 또는 키 필드만), `isExpired` (boolean, 만료 여부), 결재 이력(선택·nullable): `approvedBy` (string), `approvedAt` (string), `rejectedBy` (string), `rejectedAt` (string), `rejectionReason` (string)
  - `pagination`: `{ currentPage, totalPages, totalCount }`
- **에러**: 비인증 401

### 6.1.3 검색 이력 재요청 (만료 건)

- **POST** `/api/search-history/{id}/re-request`
- **Path**: `id` — 검색 이력 ID (Long)
- **Response (data)**: `{ "id": number, "approvalStatus": "PENDING", "requestedAt": string, "expiresAt": string }`
- **에러**: 403(타 사용자 소유), 404(없음), 400(이미 PENDING/APPROVED 등 재요청 불가)

### 6.1.4 검색 이력 상세 조회 (재조회용)

- **GET** `/api/search-history/{id}`
- **Path**: `id` — 검색 이력 ID (Long)
- **Response (data)**: `id`, `logType`, `searchParams` (object, 전체 검색 조건), `requestedAt`, `expiresAt`, `approvalStatus`, 결재 이력(선택·nullable): `approvedBy`, `approvedAt`, `rejectedBy`, `rejectedAt`, `rejectionReason`
- **에러**: 403(타 사용자 소유), 404(없음)

### 6.1.5 승인 대기 목록 조회 (결재자·관리자 전용)

- **GET** `/api/search-history/pending`
- **권한**: 결재자(decrypt_approver에 등록된 사용자) 또는 관리자(role=ADMIN)만 호출 가능. 그 외 403.
- **Query**: `page` (기본 1), `pageSize` (기본 20)
- **Response (data)**: `SearchHistoryPendingListResponse`
  - `data`: 배열. 각 항목: `id`, `requester` (요청자 username), `searchParamsSummary` (요약 문자열), `requestedAt` (yyyy-MM-dd'T'HH:mm:ss), 기타 목록용 필드
  - `pagination`: `{ currentPage, totalPages, totalCount }`
- **에러**: 401 비인증, 403 결재자/관리자 아님 → `code: "FORBIDDEN_NOT_APPROVER"` 또는 `"NOT_APPROVER"`

### 6.1.6 검색 이력 승인 (결재자·관리자 전용)

- **POST** `/api/search-history/{id}/approve`
- **Path**: `id` — 검색 이력 ID (Long)
- **권한**: 결재자 또는 관리자만 호출 가능. 그 외 403.
- **Request body**: 없음
- **Response (data)**: `{ "id": number, "approvalStatus": "APPROVED", "approvedBy": string, "approvedAt": string (yyyy-MM-dd'T'HH:mm:ss) }`
- **에러**: 401 비인증, 403 결재자/관리자 아님 → `code: "FORBIDDEN_NOT_APPROVER"` 또는 `"NOT_APPROVER"`, 404 해당 이력 없음

### 6.1.7 검색 이력 반려 (결재자·관리자 전용)

- **POST** `/api/search-history/{id}/reject`
- **Path**: `id` — 검색 이력 ID (Long)
- **권한**: 결재자 또는 관리자만 호출 가능. 그 외 403.
- **Request body** (JSON, 선택):

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| rejectionReason | string | X | 반려 사유 |

- **Response (data)**: `{ "id": number, "approvalStatus": "REJECTED", "rejectedBy": string, "rejectedAt": string, "rejectionReason": string \| null }`
- **에러**: 401 비인증, 403 결재자/관리자 아님 → `code: "FORBIDDEN_NOT_APPROVER"` 또는 `"NOT_APPROVER"`, 404 해당 이력 없음

---

## 7. 사용자 관리 (관리자 전용)

**Base path**: `/api/users`

모든 API는 **관리자(role=ADMIN)** 만 호출 가능. 그 외 403.

### 7.1 사용자 목록 조회

- **GET** `/api/users`
- **Response (data)**: 배열. 각 항목:
  - `userId` (또는 `username`): string — 로그인 ID
  - `role`: string — "ADMIN" | "USER"
  - `departmentCode`: string | null — 부서코드
  - `position`: string | null — 직책
  - `rank`: string | null — 직급
  - `isApprover`: boolean — decrypt_approver 테이블에 존재 여부(복호화 결재자 여부)
- **에러**: 401 비인증, 403 관리자 아님 → `code: "FORBIDDEN"` 등

### 7.2 사용자 역할 변경 (요건 20250227-user-management-hierarchy-permissions)

- **PUT** `/api/users/{userId}`
- **Path**: `userId` — 역할을 변경할 사용자 ID(username, app_user.username)
- **권한**: 관리자(role=ADMIN)만 호출 가능. 그 외 403, `code: "FORBIDDEN"`.
- **Request body** (JSON):

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| role | string | O | "ADMIN" \| "USER" — 유효값만 허용 |

- **Response (data)**: 업데이트된 사용자 요약 객체
  - `userId` (또는 `username`): string
  - `role`: string — "ADMIN" \| "USER" (변경 후 값)
  - `departmentCode`: string \| null
  - `isApprover`: boolean
- **에러**:
  - 401 비인증
  - 403 관리자 아님 → `code: "FORBIDDEN"`
  - 404 해당 사용자 없음 → `code: "USER_NOT_FOUND"`
  - 400 role 누락 또는 ADMIN/USER 외 값 → `code: "INVALID_INPUT"`
  - 400 자기 자신 역할 변경 시도 → `code: "SELF_DEMOTION_BLOCKED"`
  - 400 마지막 관리자 강등 시도 → `code: "LAST_ADMIN_BLOCKED"`

---

## 8. 사용자 활동 이력 (Activity Log)

**Base path**: `/api/activity-log`

### 8.1 활동 이력 검색

- **POST** `/api/activity-log/search`
- **Request body** (JSON):

| 필드 | 타입 | 설명 |
|------|------|------|
| startDate | string | yyyy-MM-dd HH:mm:ss 등 |
| endDate | string | |
| userId | string | |
| username | string | |
| actionType | string | |
| ipAddress | string | |
| page | integer | 기본 1 |
| pageSize | integer | 기본 20 |
| sortField | string | 기본 "created_at" |
| sortDirection | string | 기본 "desc" |

- **Response (data)**: `UserActivityLogResponse`
  - `data`: object[] (활동 이력 목록)
  - `pagination`: `{ currentPage, totalPages, totalCount }`

### 8.2 활동 이력 상세 조회

- **GET** `/api/activity-log/{id}`
- **Path**: `id` — Long
- **Response (data)**: Map (활동 이력 한 건 상세)

---

## 9. DB 테스트 (개발/운영 점검용)

**Base path**: `/api/db`

### 9.1 DB 연결 테스트

- **GET** `/api/db/test`
- **Response (data)**: Map
  - `connected`: boolean
  - `databaseProductName`, `databaseProductVersion`, `driverName`, `driverVersion`, `url`, `username`
  - `readOnly`, `autoCommit`
  - `pb_send_table_exists`, `pb_recv_table_exists`
  - `pb_send_count`, `pb_recv_count`
  - 실패 시: `error`, `errorClass`

### 9.2 테이블 스키마 정보 조회

- **GET** `/api/db/schema`
- **Response (data)**: Map
  - `pb_send_columns`, `pb_recv_columns`: 컬럼별 `{ name, type, size, nullable }`
  - 실패 시: `error`

---

## 10. 복호화 (단일 로우)

**Base path**: `/api/logs/decrypt`

### 10.1 단일 로우 복호화

- **POST** `/api/logs/decrypt/{logType}`
- **Path**: `logType` — 현재 **java_fw_imglog** 만 지원
- **Request body** (JSON): `{ "guid": string (필수), "status"?: string, "searchHistoryId": number (필수) }` — searchHistoryId는 이번 검색에 대한 승인된 검색 이력 ID. 해당 건이 본인 소유·APPROVED·미만료일 때만 복호화 허용.
- **Response (data)**: Map (복호화된 필드)
- **에러**:
  - 401 미로그인: `code: "UNAUTHORIZED"`
  - 403 복호화 미승인: `code: "DECRYPTION_NOT_APPROVED"` — searchHistoryId 없거나, 해당 검색 이력이 본인 소유·승인·미만료가 아님.
  - 403 스냅샷 미포함: `code: "ROW_NOT_IN_APPROVED_SNAPSHOT"` — 승인된 검색 결과(승인 시점 스냅샷)에 포함된 row만 복호화 가능. 해당 guid가 스냅샷에 없음. (참고: `docs/requirements/20260224-decryption-snapshot-final-design-en.md`)
  - java_fw_imglog 외: `code: "UNSUPPORTED_LOG_TYPE"`
  - guid 누락: `code: "MISSING_GUID"`
  - 복호화 실패: `code: "DECRYPTION_FAILED"`

---

## 11. 에러 코드 요약

| code | 의미 |
|------|------|
| LOG_TYPE_NOT_FOUND | 존재하지 않는 로그 타입 |
| UNSUPPORTED_LOG_TYPE | 해당 로그 타입 미지원 (현재 java_fw_imglog만 지원하는 API) |
| DECRYPTION_NOT_APPROVED | 복호화 승인 후 이용 가능 (403) |
| ROW_NOT_IN_APPROVED_SNAPSHOT | 승인된 검색 결과에 포함된 항목만 복호화 가능 (403) |
| MISSING_GUID | 복호화 시 guid 필수 |
| DECRYPTION_FAILED | 복호화 처리 실패 |
| FORBIDDEN_NOT_APPROVER | 승인/반려·대기목록 API 호출 권한 없음(결재자 또는 관리자만 가능) (403) |
| NOT_APPROVER | 위와 동일 의미. 구현 시 하나로 통일 가능 |
| DEPARTMENT_NOT_FOUND | 부서 없음 (404) |
| ALREADY_APPROVER | 해당 부서에 이미 결재자로 등록됨 (400) |
| USER_NOT_IN_DEPARTMENT | 지정한 사용자가 해당 부서 소속이 아님. 부서별 결재자로 추가 불가 (400) |
| FORBIDDEN | 권한 없음(예: 부서/결재자 API는 관리자 전용) (403) |
| INVALID_INPUT | 부서코드/userId 등 입력값 비어 있음 또는 형식 오류 (400) |
| PERMISSION_GROUP_NOT_FOUND | 해당 ID의 권한 그룹 없음 (404) |
| PERMISSION_GROUP_HAS_USERS | 삭제 시 해당 그룹에 사용자 배정 있음 (400) |
| USER_ALREADY_IN_GROUP | 해당 사용자가 이미 그룹에 배정됨 (400) |
| INVALID_SCREEN_ID | allowedScreens에 허용 목록에 없는 screen_id 포함 (400) |
| USER_NOT_FOUND | 해당 사용자 없음 (404) |
| SELF_DEMOTION_BLOCKED | 자기 자신의 역할 변경 시도 (400) |
| LAST_ADMIN_BLOCKED | 마지막 관리자 강등 시도 (400) |

---

## 12. 부서 계층 (관리자 전용)

**Base path**: `/api/departments`

부서는 code·parent_code·name으로 계층 구조. 루트는 parent_code null.  
부서 API는 **관리자(role=ADMIN)** 만 호출 가능. 그 외 403.  
부서별 결재자·멤버·팀장 지정 API는 제거됨(팀장 자동 지정으로 대체).

### 12.1 부서 트리 조회

- **GET** `/api/departments`
- **Query**: `format` — "tree"(기본) | "flat"
- **Response (data)**:
  - tree: 배열, 각 노드 `{ "code", "parentCode", "name", "sortOrder", "children": [] }`. 루트만 최상위, 하위는 children 재귀.
  - flat: `[{ "code", "parentCode", "name", "sortOrder" }, ...]`
- **에러**: 401, 403

---

## 13. 참고

- **환경·포트**: `docs/contract.md`
- **사용자 관리·전산요청서·인사배치 등 (미구현 API)**: `specs/user-management.spec.yaml`
- **정의 위치**: 이 문서는 현재 구현 기준. API 추가/변경 시 이 문서와 `specs/*.spec.yaml`을 먼저 갱신할 것.

---

## 14. 권한 그룹 및 사용자 권한 계층 (관리자 전용)

**Base path (권한 그룹)**: `/api/permission-groups`  
**사용자 권한 계층**: `GET /api/departments/user-permission-hierarchy`

요건: `docs/requirements/20250227-user-permission-hierarchy-group.md`, `docs/requirements/20250227-permission-group-screen-menu-access.md`. 상세 스펙: `specs/permission-group-hierarchy.spec.yaml`.  
모든 API는 **관리자(role=ADMIN)** 만 호출 가능. 그 외 403, `code: "FORBIDDEN"`.  
**화면 기반 접근**: 화면에 대응하는 API는 사용자가 해당 화면을 권한 그룹으로 허용받았거나 ADMIN이어야 함. 그 외 403. 화면↔API 매핑: `specs/permission-group-hierarchy.spec.yaml` §4.3.

### 14.1 권한 그룹 목록 조회

- **GET** `/api/permission-groups`
- **Response (data)**: 배열. 각 항목: `id` (number), `code` (string), `name` (string), `description` (string | null), `sortOrder` (number, 선택), `allowedScreens` (string[], 요건 20250227-permission-group-screen-menu-access)
- **에러**: 401, 403

### 14.2 권한 그룹 생성

- **POST** `/api/permission-groups`
- **Request body** (JSON): `code` (string, 필수), `name` (string, 필수), `description` (string, 선택), `sortOrder` (number, 선택, 기본 0), `allowedScreens` (string[], 선택 — 허용 화면 목록; 그 외 400 `INVALID_SCREEN_ID`)
- **Response (data)**: 생성된 권한 그룹 객체 (동일 필드 + `id`, `allowedScreens`)
- **Status**: 201
- **에러**: 400 (code/name 누락·중복), 401, 403

### 14.3 권한 그룹 상세 조회

- **GET** `/api/permission-groups/{id}`
- **Path**: `id` — 권한 그룹 ID (Long)
- **Response (data)**: 단일 권한 그룹 객체
- **에러**: 401, 403, 404 → `code: "PERMISSION_GROUP_NOT_FOUND"`

### 14.4 권한 그룹 수정

- **PUT** `/api/permission-groups/{id}`
- **Path**: `id` — 권한 그룹 ID (Long)
- **Request body** (JSON): `code`, `name`, `description`, `sortOrder`, `allowedScreens` (string[], 모두 선택). `allowedScreens`는 허용 화면 목록에 있는 값만; 그 외 400 `INVALID_SCREEN_ID`
- **Response (data)**: 수정된 권한 그룹 객체
- **에러**: 400, 401, 403, 404 → "PERMISSION_GROUP_NOT_FOUND"

### 14.5 권한 그룹 삭제

- **DELETE** `/api/permission-groups/{id}`
- **Path**: `id` — 권한 그룹 ID (Long)
- **Response (data)**: null 또는 성공 메시지. Status 200 또는 204.
- **동작**: 해당 그룹에 사용자가 배정되어 있으면 400, `code: "PERMISSION_GROUP_HAS_USERS"` 반환 후 삭제하지 않음 (cascade 미적용).
- **에러**: 401, 403, 404, 400 (사용자 배정 있음)

### 14.6 권한 그룹에 사용자 배정

- **POST** `/api/permission-groups/{id}/users`
- **Path**: `id` — 권한 그룹 ID (Long)
- **Request body** (JSON): `{ "userId": string }` — app_user.username
- **Response (data)**: `{ "userId", "permissionGroupId", "permissionGroupCode" }` 또는 동일 의미 객체
- **Status**: 201 또는 200
- **에러**: 400 (userId 누락), 401, 403, 404 그룹 → "PERMISSION_GROUP_NOT_FOUND", 404 사용자 → "USER_NOT_FOUND", 400 이미 배정 → `code: "USER_ALREADY_IN_GROUP"`

### 14.7 권한 그룹에서 사용자 제거

- **DELETE** `/api/permission-groups/{id}/users/{userId}`
- **Path**: `id` — 그룹 ID (Long), `userId` — 사용자명(username, string)
- **Response (data)**: null 또는 성공 메시지. Status 200 또는 204.
- **에러**: 401, 403, 404

### 14.8 권한 그룹별 사용자 목록 (선택)

- **GET** `/api/permission-groups/{id}/users`
- **Path**: `id` — 권한 그룹 ID (Long)
- **Response (data)**: 배열. 각 항목: `userId`, `username`, `departmentCode`, `role` 등 사용자 요약
- **에러**: 401, 403, 404

### 14.9 사용자 권한 계층 조회

- **GET** `/api/departments/user-permission-hierarchy`
- **Query**: `format` — "tree"(기본) | "flat"
- **Response (data)**:
  - **tree**: 루트 노드 배열. 각 노드: `code`, `parentCode`, `name`, `sortOrder`, `children` (재귀), `users` (배열). `users` 각 항목: `userId` (username), `role`, `position` (직책), `rank` (직급), `permissionGroups` (배열: `{ id, code, name }`).
  - **flat**: 부서 노드 배열에 `users` 포함(동일 구조), `children` 없음.
- **에러**: 401, 403

- 기존 부서 트리(code/parent_code)와 동일 구조; 부서별로 해당 department_code를 가진 app_user 목록과 각 사용자의 권한 그룹(permission_group) 목록을 붙여 반환.
