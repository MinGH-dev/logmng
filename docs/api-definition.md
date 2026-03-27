# API 정의서

현재 백엔드에 구현된 API 목록 및 요청/응답 규격이다.  
**베이스 URL**: `http://localhost:9200/api` (환경·포트는 `docs/contract.md` 참고).

**User ID (userId)**: 요청/응답/경로/쿼리에서 **`userId`**는 **numeric** **`app_user.id`**(JSON type: number, 예: 20269999, 20260001)이다. 로그인 시 사용자 식별은 **userId (numeric)** 만 사용하며, username(문자열)으로는 로그인할 수 없다. **Breaking change**: 동일 릴리즈부터 클라이언트는 모든 API의 userId를 숫자 타입으로 처리해야 하며, 문자열(username) 기반 userId는 지원하지 않는다.

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
- 로그인 시 사용자 식별은 **userId (numeric)** 만 사용하며, username(문자열)으로는 로그인할 수 없다.
- **Request body** (JSON)

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| userId | number | O | **사용자 ID** (numeric `app_user.id`, 예: 20269999, 20260001). 로그인은 이 숫자 ID로만 가능. |
| password | string | O | 비밀번호 |

- **Response (data)**: `{ "user": LoginResponse }`
  - `user.username`: string
  - `user.loginTime`: string (yyyy-MM-dd'T'HH:mm:ss)
  - `user.clientIP`: string
  - `user.isSystemAdmin`: boolean — 시스템 관리자 여부 (req 20250303). true면 전체 화면 접근.
  - `user.allowedScreenIds`: string[] (요건 20250227-permission-group-screen-menu-access) — 사용자 권한 그룹들의 접근 가능 화면 합집합.
  - `user.screenScopes`: Record<string, 'self'|'team'|'all'> (요건 20250303, 20260305) — 화면별 **조회(목록) 범위**. key=screen_id (activity-log, statistics, search-history, pending-approvals), value='self'(본인)|'team'(부서)|'all'(전체). is_system_admin=true이면 생략 가능(프론트는 전체로 처리). **용도**: 목록/조회에만 적용; scope=self → 본인; scope=team → 동일 부서; scope=all → 전체. **승인 범위는 부서로 고정**이며 변경 불가(권한 설정에서 선택하는 scope는 조회 범위만 해당).
  - `user.screenFunctions`: Record<string, { read: boolean, write?: boolean, approve?: boolean, decrypt?: boolean }> (요건 20250303, 20260318) — 화면별 기능 가능 여부. key=screen_id (pb-feplog, java-fw-imagelog, search-history, pending-approvals 등), value=read(필수), write(수정 지원 화면만), approve(search-history·pending-approvals만), decrypt(로그 검색 화면 pb-feplog·java-fw-imagelog 전용, 복호화 요청 권한). pb-feplog·java-fw-imagelog는 read + optional decrypt; decrypt는 권한관리에서 부여/해제. **용도**: 버튼/액션 enable·disable, 비활성 시 툴팁 표시.
  - `user.selfContext`: `{ department: string | null, username: string, userId: number }` — self-scoped user/requester block의 **visible locked self-context** 표시값. `scope=self` 화면에서 Department, Username, User ID를 고정 표시할 때 사용하는 권위 소스다. **`userId`**는 **numeric** **`app_user.id`**(JSON number, 예: 20269999, 20260001)이다. **`username`**은 **표시 이름(사용자명)**: `app_user.name`이 존재하고 비어 있지 않으면 그 값, 그렇지 않으면 `app_user.username`을 사용한다.

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

- **GET** `/api/auth/me` — 로그인 사용자 정보 반환. `isSystemAdmin: boolean`, `allowedScreenIds: string[]`, `screenScopes: Record<string, 'self'|'team'|'all'>`, `screenFunctions: Record<string, { read, write?, approve?, decrypt? }>`, `selfContext: { department: string | null, username: string, userId: number }` 포함 (req 20250303, 20260305, 20260313). screenScopes는 조회(목록) 범위(본인/부서/전체) 결정용. 승인 범위는 부서 고정·변경 불가. screenFunctions는 화면별 read/write/approve/decrypt 가능 여부로 버튼·액션 enable·disable용. **`search-history`·`pending-approvals`의 `approve`**: `docs/contract.md` **「복호화 승인 자격」** — 권한 그룹 `permission_group_screen.approve`를 먼저 반영한 뒤 **`is_system_admin=true`이면 해당 화면 `approve`는 유효하지 않음(false)**; `ADMIN_EXT` 등 **`is_system_admin=false`인 사용자는 그룹 설정만** 따름. **`selfContext`**는 applicable shared-pattern 화면에서 `scope=self`일 때 보이는 잠금 self-context 표시값의 권위 소스다. **`selfContext.userId`**는 **numeric** **`app_user.id`**(JSON number). **`username`**은 **표시 이름(사용자명)**: `app_user.name`이 존재하고 비어 있지 않으면 그 값, 그렇지 않으면 `app_user.username`을 사용한다.

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
- **권한**: 요청한 `logType`에 대응하는 화면(pb_feplog→pb-feplog, java_fw_imglog→java-fw-imagelog) 접근 필요. 해당 화면 없으면 403 `LOG_TYPE_NOT_ALLOWED` (req 20260318).
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
| sortSpecs | { field: string, direction: string }[] | | **pb_feplog only**: ordered multi-column sort; when non-empty, overrides sortField/sortDirection. `field` must be an allowlisted column (e.g. log_timestamp, tr_code, user_id). |
| displayTemplate | string | | 기본 "detailed" |

- **Response (data)**: `LogDbSearchResponse`
  - `data`: object[] (로그 행 목록)
  - `pagination`: `{ currentPage, totalPages, totalCount }`

### 5.1.1 PB FEP wireframe log search (`pb-fep-log-search`)

- **POST** `/api/logs/db-refactored/pb-fep-log-search`
- **Purpose**: Dedicated search for screen ID **`pb-fep-log-search`** only (wireframe IA / column names). Returns the same PB FEP UNION (`pb_feplog`) as legacy search but **each row uses wireframe field names** per requirement `docs/requirements/20260326-pb-fep-log-search-screen-wireframe.md` §2.D.
- **Legacy unchanged**: **`POST /api/logs/db-refactored/search`** (including `logType=pb_feplog` for **`pb-feplog`**) keeps its existing contract and response shape. New clients for **`pb-fep-log-search`** must call this path; legacy **`pb-feplog`** must **not** be repointed here without a separate requirement.
- **Auth / access**: Same family as PB FEP log search and screen **`pb-fep-log-search`**: user must satisfy existing log-type and screen checks for **`pb_feplog`** / PB FEP (see `docs/contract.md` API function-level enforcement: **`pb-feplog` or `pb-fep-log-search`** for `pb_feplog` access). Unauthenticated **401**; insufficient screen / log-type access **403** `LOG_TYPE_NOT_ALLOWED` (or equivalent `FUNCTION_NOT_ALLOWED` where applicable).

- **Request body** (JSON): **`LogDbSearchRequest`** subset / same shape as §5.1 for shared fields.

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| startDate | string | O (product) | Combined range start (wireframe: 조회일자 + 시작시간); format as in §5.1. |
| endDate | string | O (product) | Combined range end (조회일자 + 종료시간); server validates start ≤ end. |
| loginId | string | O | Non-blank; filters DB `user_id`. |
| trCode / tr_code | string | X | Optional TR filter. |
| keywords | string[] | X | Optional tokens (e.g. from comma-separated UI input). |
| logType | string | X | May default to **`pb_feplog`** or be implied by this route; server validates PB FEP only. |
| page | integer | X | Default **1**. |
| pageSize | integer | X | Wireframe default **25**; allowed **25 / 50 / 100** (validate if outside). |
| displayTemplate | string | X | Optional; same meaning as §5.1 if used. |
| sortField / sortDirection | string | X | Legacy single-column fallback when **`sortSpecs`** is empty (implementation-defined; initial UI sort is `log_timestamp` **desc**). |
| sortSpecs | { field: string, direction: string }[] | X | **Cumulative** multi-column sort (ordered). **`field`** must be **allowlisted** (wireframe semantics / DB column map per `docs/contract.md` PB FEP wireframe section). Unknown **`field`** → **400**. When non-empty, overrides **`sortField` / `sortDirection`** for ordering. |
| decryptData | boolean | X | As §5.1 if applicable to PB FEP behavior. |
| (기타 LogDbSearchRequest 필드) | — | X | 이미지로그 전용 등 PB FEP와 무관한 필드는 무시 가능. |

- **Response (data)**: Same envelope as §5.1 — object with:
  - **`data`**: `object[]` — each row is a **wireframe-keyed** map (not legacy `/search` column names for this screen):
    - **Stable keys**: `id` (number, row id), `log_type` (string; branch discriminator with `id` for unique row keys — e.g. send vs recv).
    - **Columns**: `log_timestamp`, `tr_code`, `login_id`, `msg_code`, `bmsg`, `log_ch_cd`, `send_recv` (`SEND` \| `RECV`), `src_ip`, `dest_ip`, `app_id`, `data`.
    - **Stream / expand (optional)**: `request_data`, `response_data` (or equivalent) when needed for expanded STREAM DATA; masking/decrypt follows PB FEP rules in `LogDbService` / contract.
  - **`pagination`**: `{ currentPage, totalPages, totalCount }`

- **에러** (요약):
  - **400** — range/login validation, invalid **`sortSpecs.field`** (allowlist), bad **`pageSize`**; `code` e.g. **`INVALID_INPUT`** or **`BAD_REQUEST`** (implementation aligns).
  - **401** — 비인증.
  - **403** — **`LOG_TYPE_NOT_ALLOWED`**, **`FUNCTION_NOT_ALLOWED`** — PB FEP / **`pb-fep-log-search`** 접근 또는 기능 없음.

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

**Storage and join**: `search_history.user_id` stores numeric **`app_user.id`**. List and all search-history operations use join **app_user ON app_user.id = search_history.user_id**. Requester display (requesterUsername, requesterDisplayName, department) is resolved via this join from app_user/department. API `userId` (query params and response) is numeric `app_user.id`.

**화면별 범위(scope)**: is_system_admin=false일 때 권한 그룹의 search-history scope 적용. scope='self' → 현재 요청자 본인 데이터만 반환하며 requester filter(`department`, `username`, `userId`)는 무시한다. 이때 requester block은 숨기지 않고 `department -> username -> userId` 순서의 visible locked self-context를 표시하며, 표시값은 auth/current-user payload의 `selfContext`를 기준으로 한다. scope='team' → 동일 부서 요청자만 반환하며 requester filter는 그 허용 집합 안에서만 추가 좁힘; scope='all' → 전체 가시 집합에 requester filter를 적용. requester filter는 scope를 넓히지 않으며, 상세 규칙은 `specs/permission-group-hierarchy.spec.yaml` §4.3을 따른다.

- 검색 이력은 "복호화 승인 요청"이 발생한 검색을 저장하며, 사용자별 최근 이력 목록·재요청·재조회를 지원한다.
- 승인 유효 기간: 요청일시 + 1일. 만료 시 재요청 가능.

### 6.1.1 검색 이력 저장

- **POST** `/api/search-history`
- **Current user**: Resolved from auth/session as numeric `app_user.id`; stored in `search_history.user_id`. Join semantics: **app_user.id = search_history.user_id** (req 20260316).
- **Request body** (JSON):

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| logType | string | O | 로그 타입 (pb_feplog, java_fw_imglog 등) |
| searchParams | object | O | 검색 조건 (LogDbSearchRequest 또는 AdvancedSearchRequest와 동일한 구조의 JSON) |
| requestReason | string | X 또는 O (제품 결정) | 요청 사유. 최대 길이 500자(제품에서 조정 가능). 초과 시 400. (req 20260317) |
| searchResultTotalCount | number | X | 선택. `decryptionTargetCount`와 **둘 다** 보낼 때만 유효(≥0). 둘 다 생략 시 서버가 동일 검색으로 `pagination.totalCount` 및 첫 `min(total, 10000)`건에서 암호화 대상 건수를 계산해 저장. 한쪽만내면 400. |
| decryptionTargetCount | number | X | 선택. 위와 동일. `java_fw_imglog`만 암호화 행 판별; `pb_feplog` 등은 0. 검색 실패·totalCount 미반환 시 두 필드 모두 DB에 NULL로 저장될 수 있음. |

- **Response (data)**: `{ "id": number, "requestedAt": string, "expiresAt": string, "approvalStatus": "PENDING", "searchResultTotalCount": number \| null, "decryptionTargetCount": number \| null }` — 저장된 스냅샷 값(레거시 행 생성 API는 null).
- **에러**: 비인증 401, logType/searchParams 누락 400, requestReason 길이 초과 400, 카운트 override 한쪽만 전달 400, searchResultTotalCount/decryptionTargetCount &lt; 0 → 검증 오류 400

### 6.1.2 검색 이력 목록 조회

- **GET** `/api/search-history`
- **List join**: **app_user.id = search_history.user_id**. Requester filter `userId` is exact match on `search_history.user_id` (numeric). Response `userId` is numeric `app_user.id`.
- **Query**:
  - `department` (선택) — requester 부서 코드/값 exact match. `scope=self`에서는 무시.
  - `username` (선택) — requester 사용자명 partial match (`LIKE`). `scope=self`에서는 무시.
  - `userId` (선택) — requester 사용자 ID exact match (numeric `app_user.id`, JSON number); filters by `search_history.user_id`. `scope=self`에서는 무시.
  - `requestedAtFrom` (선택) — 요청일시 범위 시작. 형식: **yyyy-MM-dd HH:mm:ss**. `requested_at >= requestedAtFrom`. (req 20260317)
  - `requestedAtTo` (선택) — 요청일시 범위 종료. 형식: **yyyy-MM-dd HH:mm:ss**. `requested_at <= requestedAtTo` (또는 end-of-day 해석). (req 20260317)
  - `approvalStatus` (선택, **다중값**) — 복호화 승인 여부. **동일 이름 반복**으로 전달: `approvalStatus=PENDING&approvalStatus=APPROVED`. 값: PENDING, APPROVED, REJECTED, EXPIRED. 비어 있으면 해당 조건 없음. (req 20260317)
  - `requestReason` (선택) — 요청사유 부분 검색. 백엔드 `request_reason ILIKE '%value%'`. (req 20260317)
  - `page` (기본 1)
  - `pageSize` (기본 20)
  - `sortField` (기본 requested_at)
  - `sortDirection` (기본 desc)
- **Filter / paging interaction**:
  - requester filter는 `scope=self/team/all`의 기존 가시 범위를 넓히지 않고, 허용된 결과 집합만 추가로 좁힌다.
  - 필터 변경 또는 `pageSize` 변경 시 현재 페이지는 `1`로 재설정한다.
  - 백엔드는 목록 데이터와 `pagination.totalCount` / `pagination.totalPages`를 동일한 filter set으로 계산해야 한다.
- **Response (data)**: `SearchHistoryListResponse`
  - `data`: 배열. 각 항목: `seq` (목록 순번), `id`, `userId` (number, `app_user.id`), `requesterDepartmentCode` (string | null, 검색한 사용자 부서 코드), `requesterDepartmentName` (string | null, 검색한 사용자 부서 표시명; `department.name`, 없으면 null), `requesterDisplayName` (string | null, 검색한 사용자 표시명; `app_user.name` 없으면 `username`), `requesterUsername` (string | null, 검색한 사용자 로그인 ID), `logType`, `requestedAt`, `expiresAt`, `approvalStatus` (PENDING | APPROVED | EXPIRED | REJECTED), `requestReason` (string | null, 요청 사유; req 20260317), `searchParamsSummary` (요약 문자열; 그리드에서는 미표시·모달에서만 사용), `isExpired` (boolean, 만료 여부), **`searchResultTotalCount` (number \| null, 요청 시점 검색 총건)**, **`decryptionTargetCount` (number \| null, 동일 스냅샷 윈도에서 복호화 대상 건수; 레거시 행 null)**, 결재 이력(선택·nullable): `approvedBy` (string, 표시용; req 20260316: `approved_by_user_id`로 username 해석, 없으면 `approved_by`), `approvedAt` (string), `rejectedBy` (string), `rejectedAt` (string), `rejectionReason` (string)
  - UI 그리드: 요청자 정보는 **부서**, **사용자ID**, **사용자명** 세 개 컬럼으로 표시.
  - `pagination`: `{ currentPage, totalPages, totalCount }`
- **에러**: 비인증 401, requestedAtFrom/requestedAtTo 형식 오류 시 400 BAD_REQUEST (형식: yyyy-MM-dd HH:mm:ss, req 20260317)

### 6.1.3 검색 이력 재요청 (만료 건)

- **POST** `/api/search-history/{id}/re-request`
- **Path**: `id` — 검색 이력 ID (Long)
- **Response (data)**: `{ "id": number, "approvalStatus": "PENDING", "requestedAt": string, "expiresAt": string }`
- **에러**: 403(타 사용자 소유), 404(없음), 400(이미 PENDING/APPROVED 등 재요청 불가)

### 6.1.4 검색 이력 상세 조회 (재조회용)

- **GET** `/api/search-history/{id}`
- **Path**: `id` — 검색 이력 ID (Long)
- **Response (data)**: `id`, `logType`, `searchParams` (object, 전체 검색 조건), `requestedAt`, `expiresAt`, `approvalStatus`, `requestReason` (string | null, 요청 사유; req 20260317), **`searchResultTotalCount` (number \| null), `decryptionTargetCount` (number \| null)** — DB 저장 스냅샷(레거시 null), 결재 이력(선택·nullable): `approvedBy` (표시용; req 20260316: `approved_by_user_id`→username, 없으면 `approved_by`), `approvedAt`, `rejectedBy`, `rejectedAt`, `rejectionReason`. **복호화 요청 대상 (항상 포함, req 20260320)**: `decryptionRequestedRows`: `{ application: string | null, serviceGroup: string | null, guid: string, status: string }[]` (java_fw_imglog의 `status`는 행의 비즈니스 status; 스냅샷·복합 키와 정렬), `decryptionRequestedCount`: number. **출처**: `APPROVED`이고 `search_history_approved_row`에 행이 있으면 DB 스냅샷을 사용(없으면 승인과 동일하게 저장 검색 재실행: `search_params`+`logType`, 최대 1만 건, 암호화 데이터가 있는 행만). `PENDING` / `REJECTED` / `EXPIRED`는 항상 저장 검색 재실행으로 수집(동일 규칙). java_fw_imglog는 `(guid, status)`로 로그 DB 보강; 로그 DB 장애·미존재 시 해당 항목은 null. `search_params` 파싱 실패·검색 실패 시 빈 배열·0.
- **에러**: 403(타 사용자 소유), 404(없음)

### 6.1.5 승인 대기 목록 조회 (복호화 승인 권한 보유자 전용)

- **GET** `/api/search-history/pending`
- **권한**: `GET /api/auth/me`의 **`screenFunctions.pending-approvals.approve === true`** 인 사용자만 호출 가능(`docs/contract.md` 「복호화 승인 자격」: 권한 그룹 화면별 승인 반영 후 **`is_system_admin`이면 상쇄되어 false**). **`is_system_admin=true`만으로는 호출 불가**. 그 외 403 `FORBIDDEN_NOT_APPROVER` / `NOT_APPROVER`.
- **Scope (검색 이력과 동일 규칙, req 20260305)**: is_system_admin=false일 때 권한 그룹의 pending-approvals scope 적용. scope='self' → 요청자(requester)=현재 사용자인 건만; scope='team' → 동일 부서 요청자만(그 중 `canApproveForRequester` 충족); scope='all' → `canApproveForRequester` 충족 건. **`canApproveForRequester`**: 승인자·요청자 **`department_code` 동일**할 때만 true(상위 부서 체인·전역 결재자 없음). auth 응답 `screenScopes['pending-approvals']`에 따라 백엔드가 목록 필터.
- **Query**: `page` (기본 1), `pageSize` (기본 20)
- **Response (data)**: `SearchHistoryPendingListResponse`
  - `data`: 배열. 각 항목: `id`, `requester` (요청자 username), `searchParamsSummary` (요약 문자열), `requestedAt` (yyyy-MM-dd'T'HH:mm:ss), `searchResultTotalCount` (number \| null), `decryptionTargetCount` (number \| null), 기타 목록용 필드
  - `pagination`: `{ currentPage, totalPages, totalCount }`
- **에러**: 401 비인증, 403 유효 승인 권한 없음 → `code: "FORBIDDEN_NOT_APPROVER"` 또는 `"NOT_APPROVER"`

### 6.1.6 검색 이력 승인 (복호화 승인 권한 보유자 전용)

- **POST** `/api/search-history/{id}/approve`
- **Path**: `id` — 검색 이력 ID (Long)
- **권한**: **`screenFunctions.search-history.approve`** 또는 **`pending-approvals.approve`** 중 **하나라도 effective true** (`docs/contract.md` 「복호화 승인 자격」: 그룹 `approve` 반영 후 **`is_system_admin`이면 상쇄**). **`is_system_admin=true`만으로는 승인 불가**. 그 외 403 `FORBIDDEN_NOT_APPROVER` / `NOT_APPROVER`.
- **요청 단위 부서 제한**: 서비스 레이어에서 `canApproveForRequester(승인자, 요청자)` — **승인자 `department_code` = 요청자 `department_code`** 일 때만 true. 미충족 시 403 `FUNCTION_NOT_ALLOWED`.
- **Request body**: 없음
- **Response (data)**: `{ "id": number, "approvalStatus": "APPROVED", "approvedBy": string (표시용; req 20260316: approved_by_user_id→username), "approvedAt": string (yyyy-MM-dd'T'HH:mm:ss) }`. 내부 저장: `approved_by_user_id` (numeric), `approved_by` (표시 보조).
- **에러**: 401 비인증, 403 유효 승인 권한 없음 → `code: "FORBIDDEN_NOT_APPROVER"` 또는 `"NOT_APPROVER"`, 403 동일 부서 규칙 불충족 등 → `code: "FUNCTION_NOT_ALLOWED"`, 404 해당 이력 없음. 400: search_params 파싱 실패 → `code: "INVALID_SEARCH_PARAMS"`; 기타 승인 처리 중 예외 → `code: "APPROVAL_ERROR"` (req 20260316-decrypt-approve-cross-user-server-error, cross-user 승인 시 500 미발생).

### 6.1.7 검색 이력 반려 (복호화 승인 권한 보유자 전용)

- **POST** `/api/search-history/{id}/reject`
- **Path**: `id` — 검색 이력 ID (Long)
- **권한**: §6.1.6과 동일(effective `screenFunctions` 승인·`is_system_admin` 상쇄).
- **요청 단위 부서 제한**: 승인 API와 동일하게 `canApproveForRequester` — **동일 `department_code`** 만 허용. 미충족 시 403 `FUNCTION_NOT_ALLOWED`.
- **Request body** (JSON, 선택):

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| rejectionReason | string | X | 반려 사유 |

- **Response (data)**: `{ "id": number, "approvalStatus": "REJECTED", "rejectedBy": string, "rejectedAt": string, "rejectionReason": string \| null }`
- **에러**: 401 비인증, 403 유효 승인 권한 없음 → `code: "FORBIDDEN_NOT_APPROVER"` 또는 `"NOT_APPROVER"`, 403 동일 부서 규칙 불충족 등 → `code: "FUNCTION_NOT_ALLOWED"`, 404 해당 이력 없음

---

## 7. 사용자 관리 (관리자 전용)

**Base path**: `/api/users`

모든 API는 **관리자(is_system_admin=true)** 만 호출 가능. 그 외 403. (req 20250303)

### 7.1 사용자 목록 조회

- **GET** `/api/users`
- **Response (data)**: 배열. 각 항목:
  - `userId`: number — numeric `app_user.id` (사용자 ID)
  - `isSystemAdmin`: boolean — 시스템 관리자 여부 (수정·삭제 불가)
  - `departmentCode`: string | null — 부서코드
  - `position`: string | null — 직책
  - `rank`: string | null — 직급
  - ~~`isApprover`~~ **제거** (req 20260323): 복호화 승인 여부는 권한 그룹·화면별 승인으로만 판단; 사용자 목록에 결재자 플래그를 두지 않음. 하위 호환으로 잠시 `false` 고정 등을 둘 경우 계약·릴리즈 노트에 명시.
- **에러**: 401 비인증, 403 관리자 아님 → `code: "FORBIDDEN"` 등

### 7.2 사용자 역할 변경 — 410 Gone (req 20250303)

- **PUT** `/api/users/{userId}`
- **Path**: `userId` — numeric `app_user.id` (JSON path parameter)
- **권한**: 관리자(is_system_admin=true)만 호출 가능. 그 외 403.
- **Response**: **410 Gone** — 역할 변경 API 제거됨. 권한은 권한 그룹으로 관리.
  - `success`: false
  - `code`: "ENDPOINT_REMOVED"

---

## 8. 사용자 활동 이력 (Activity Log)

**Base path**: `/api/activity-log`

**화면별 범위(scope)**: is_system_admin=false일 때 권한 그룹의 activity-log scope 적용. scope='self' → user/requester block은 숨기지 않고 visible locked self-context로 유지된다. `department`, `username`, `userId`는 auth/current-user payload의 `selfContext` 기준으로 표시되고 수정할 수 없으며, `userId`는 **numeric** **`app_user.id`**이다. 검색 실행 시 userId는 현재 인증 사용자로 강제되고, username, department(또는 departmentCode), ipAddress 등 사용자·부서 관련 파라미터와 동등한 widening 입력은 무시되거나 안전하게 override되며, 현재 사용자 데이터만 반환한다. `department`에 빈 값, `all`, `ALL`, `전체` 등 "전체" 의미 표현이 들어와도 범위를 넓히지 못한다. scope='team'/'all' → 요청의 department 등 필터 적용. 상세: `specs/permission-group-hierarchy.spec.yaml` §4.3.

### 8.1 활동 이력 검색

- **POST** `/api/activity-log/search`
- **Request body** (JSON):

| 필드 | 타입 | 설명 |
|------|------|------|
| startDate | string | yyyy-MM-dd HH:mm:ss 등 |
| endDate | string | |
| userId | number | (선택) 사용자 ID (numeric `app_user.id`). `scope=self`이면 클라이언트 입력값과 관계없이 현재 인증 사용자로 강제되며, 타 사용자 값으로 범위를 넓힐 수 없다. |
| username | string | (선택) 사용자명 필터. `scope=self`이면 무시되며 결과 범위를 넓히지 못한다. |
| department | string | (선택) 부서 필터. `scope=self`이면 무시되며, 빈 값, `all`, `ALL`, `전체` 등 전체 의미 표현도 범위를 넓히지 못한다. `scope=team/all`일 때만 적용. body에 departmentCode로 보내도 동일 필드로 처리. (req 20260310) |
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

### 8.3 활동 로그 통계 (Activity Statistics)

**Base path**: `/api/statistics/activity`

- **GET** `/api/statistics/activity/daily`, **GET** `/api/statistics/activity/monthly`, **GET** `/api/statistics/activity/users/all`, **GET** `/api/statistics/activity/export`
- **Query params** (공통 필터): `startDate`, `endDate` (일별/export), `year`, `month` (월별), `logType`, `userId` (number, `app_user.id`), `department`, `ip`, `username` (또는 `name`, 사용자명 LIKE 필터). (req 20260310)
- **화면별 범위(scope)**: statistics 화면 scope 적용. scope='self'일 때 user/requester block은 숨기지 않고 visible locked self-context로 유지된다. `department`, `username`, `userId`는 auth/current-user payload의 `selfContext` 기준으로 표시되고 수정할 수 없으며, `userId`는 **numeric** **`app_user.id`**이다. API 처리에서는 userId, username/name, department, ip 등 사용자·부서 관련 파라미터를 무시하고 현재 사용자 데이터만 반환한다. scope=team/all일 때는 전달된 필터 적용.

### 8.3.1 공유 부서 필터 옵션 조회

- **GET** `/api/filter-options/departments`
- **의도 / 소비 화면**: 활동 이력(`activity-log`), 활동 통계(`statistics`), 검색 이력(`search-history`)의 부서 필터 콤보박스가 공통으로 사용하는 **editable department option source**.
- **Query**:
  - `screen` (필수) — 호출 화면 컨텍스트. `activity-log` | `statistics` | `search-history`
- **권한 / 접근 모델**:
  - 인증 필요. 비인증 시 401.
  - 요청한 `screen`에 대한 접근 권한이 있는 사용자(또는 `is_system_admin=true`)만 호출 가능. 백엔드는 `screen` 값에 해당하는 화면의 권한과 scope를 적용해 결과를 계산한다.
  - 이 엔드포인트는 관리자/관리화면용 `GET /api/departments`와 별개다. 검색 필터 소비자는 부서 관리 API 권한을 요구하지 않는다.
- **Response (data)**: `string[]`
  - 각 항목은 필터 select에서 바로 `value`와 표시 문자열로 사용하는 부서 옵션 값이다.
  - 응답에는 `"전체"`를 포함하지 않는다. `"전체"` 기본 옵션은 각 화면 클라이언트가 로컬로 추가한다.
  - 이 응답은 편집 가능한 선택지 계약이다. effective `scope=self`에서 보이는 locked Department 표시값의 권위 소스는 이 엔드포인트가 아니라 auth/current-user payload의 `selfContext.department`다.
- **Scope behavior**:
  - `scope=self`: 해당 화면은 부서 필터를 숨기지 않고 visible locked self-context를 표시한다. 이 엔드포인트는 editable options source이므로 빈 배열 `[]`를 반환해도 되고 호출하지 않아도 된다. 중요한 점은 이 응답이 locked Department 표시값의 권위 소스가 아니라는 것이다.
  - `scope=team`: **현재 사용자의 자기 부서만** 반환한다. 응답은 0개 또는 1개의 자기 부서 옵션이어야 하며, 같은 조직의 다른 부서나 전체 부서 목록을 노출하지 않는다.
  - `scope=all` 또는 `is_system_admin=true`: 현재 생성된 부서 데이터셋에서 필터에 사용 가능한 부서 옵션 전체를 반환한다.
- **예시**:
  - `GET /api/filter-options/departments?screen=activity-log`
  - `GET /api/filter-options/departments?screen=statistics`
  - `GET /api/filter-options/departments?screen=search-history`
- **에러**:
  - 400 `INVALID_SCREEN_ID` — `screen`이 누락되었거나 지원하지 않는 값
  - 401 비인증
  - 403 `FORBIDDEN` — 요청한 `screen` 접근 권한 없음

### 8.3.2 구형 통계 부서 목록 엔드포인트 (전환 메모)

- `GET /api/statistics/departments`는 더 이상 권위 있는 계약이 아니다.
- 구현 전환 중 임시 호환이 필요하더라도, 새 개발과 문서 기준은 항상 `GET /api/filter-options/departments?screen=...`를 사용한다.

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

**Base path**: `/api/logs/decrypt` (POST), **Base path (허용 목록)**: `/api/decrypt/allowed` (GET)

**승인 소스 (req 20260318, 20260320)**: 복호화 허용 여부는 **decryption-allowed store**에서만 결정된다. java_fw_imglog는 **(user_id, screen, guid, row_status)** 복합 키. `search_history`/`search_history_approved_row`는 감사·이력용이며, POST decrypt의 권한 판단에는 사용하지 않는다.

### 10.1 복호화 허용 목록 조회 (GET)

- **GET** `/api/decrypt/allowed`
- **권한**: 인증 필요. 요청한 `screen`에 대한 접근 + screenFunctions[screen].decrypt (또는 is_system_admin) 필요. 그 외 403 `FUNCTION_NOT_ALLOWED`.
- **Query**:
  - `screen` (필수) — 화면 ID. `pb-feplog` 또는 `java-fw-imagelog` (main은 이전 호환용). 해당 사용자·화면에 대한 허용 GUID 목록과 유효기간 반환.
- **Response (data)**: `{ "screen": string, "validUntil": string (yyyy-MM-dd'T'HH:mm:ss 또는 ISO-8601), "guids": string[], "allowedRows": { "guid": string, "status": string }[] }`
  - `screen`: 요청한 screen 값.
  - `validUntil`: 현재 사용자·화면에 대한 복호화 허용 유효 종료 시각. 이 시각 이전에만 복호화 가능.
  - `allowedRows`: java_fw_imglog 복합 허용 목록(권위). 각 항목은 `(guid, status)` 일치 시에만 POST decrypt 허용.
  - `guids`: 허용된 guid의 distinct 목록(하위 호환·요약용). 없으면 빈 배열 `[]`.
- **에러**:
  - 401 미로그인: `code: "UNAUTHORIZED"`
  - 403 복호화 권한 없음: `code: "FUNCTION_NOT_ALLOWED"`
  - 400 `screen` 누락 또는 미지원 값: `code: "INVALID_SCREEN_ID"` (또는 동일 의미 코드)

### 10.2 단일 로우 복호화 (POST)

- **POST** `/api/logs/decrypt/{logType}`
- **권한 (req 20260318)**: **logType에 대응하는 화면**(java_fw_imglog→java-fw-imagelog) 접근 + screenFunctions[screen].decrypt === true (또는 is_system_admin). 권한 없으면 403 `code: "FUNCTION_NOT_ALLOWED"`. 권한관리에서 pb-feplog·java-fw-imagelog에 대해 "복호화" 권한 부여/해제 가능.
- **Path**: `logType` — 현재 **java_fw_imglog** 만 지원
- **Request body** (JSON): `{ "guid": string (필수), "status": string (java_fw_imglog 필수), "searchHistoryId"?: number (선택, 감사용) }`
  - **guid**: 필수. 복호화 대상 row의 GUID.
  - **status**: **java_fw_imglog에서 필수**(공백만 불가). 행의 비즈니스 status; decryption-allowed store의 `row_status`와 정규화(trim) 후 일치해야 함.
  - **searchHistoryId**: 선택. **승인 판단에는 사용하지 않음.** 감사·추적용으로만 전달 가능. 생략 가능.
  - **승인 판단 (req 20260318, 20260320)**: logType→screen 매핑 후, 현재 사용자·screen·**(guid, status)** 가 decryption-allowed store에 있고 valid_until > now 일 때만 복호화 허용.
- **Response (data)**: Map (복호화된 필드)
- **에러**:
  - 401 미로그인: `code: "UNAUTHORIZED"`
  - 403 복호화 권한 없음: `code: "FUNCTION_NOT_ALLOWED"` — 해당 로그 타입 화면의 복호화(decrypt) 권한이 없음. 권한 그룹에서 해당 화면에 복호화 권한 부여 필요. **검색 화면 UI (req 20260317-search-decrypt-permission-ui)**: 권한 없을 때 복호화 액션 비활성/숨기고 "복호화 권한이 없습니다." 표시.
  - 403 복호화 미허용: `code: "DECRYPTION_NOT_APPROVED"` — guid가 decryption-allowed store에 없거나 valid_until이 만료된 경우.
  - java_fw_imglog 외: `code: "UNSUPPORTED_LOG_TYPE"`
  - guid 누락: `code: "MISSING_GUID"`
  - status 누락·공백(java_fw_imglog): `code: "MISSING_STATUS"`
  - 복호화 실패: `code: "DECRYPTION_FAILED"`

---

## 11. 에러 코드 요약

| code | 의미 |
|------|------|
| LOG_TYPE_NOT_FOUND | 존재하지 않는 로그 타입 |
| UNSUPPORTED_LOG_TYPE | 해당 로그 타입 미지원 (현재 java_fw_imglog만 지원하는 API) |
| DECRYPTION_NOT_APPROVED | 복호화 미허용: decryption-allowed store에 **(guid, status)** 없음 또는 valid_until 만료 (403). req 20260318, 20260320. |
| ROW_NOT_IN_APPROVED_SNAPSHOT | (선택) 위와 동일 의미로 사용 가능. 제품에서 DECRYPTION_NOT_APPROVED 로 통일 가능 (403). |
| MISSING_GUID | 복호화 시 guid 필수 |
| MISSING_STATUS | java_fw_imglog 복호화 시 status 필수(공백 불가) (400). req 20260320. |
| DECRYPTION_FAILED | 복호화 처리 실패 |
| FORBIDDEN_NOT_APPROVER | 승인/반려·대기목록 API: **effective** `screenFunctions` 승인 없음 또는 `is_system_admin`만인 경우 등 (403). `docs/contract.md` 「복호화 승인 자격」 |
| NOT_APPROVER | 위와 동일 의미. 구현 시 하나로 통일 가능 |
| DEPARTMENT_NOT_FOUND | 부서 없음 (404) |
| ALREADY_APPROVER | 해당 부서에 이미 결재자로 등록됨 (400) |
| USER_NOT_IN_DEPARTMENT | 지정한 사용자가 해당 부서 소속이 아님. 부서별 결재자로 추가 불가 (400) |
| FORBIDDEN | 권한 없음(예: 부서/결재자 API는 관리자 전용) (403) |
| INVALID_INPUT | 부서코드/userId 등 입력값 비어 있음 또는 형식 오류 (400) |
| PERMISSION_GROUP_NOT_FOUND | 해당 ID의 권한 그룹 없음 (404) |
| PERMISSION_GROUP_HAS_USERS | 삭제 시 해당 그룹에 사용자 배정 있음 (400) |
| USER_ALREADY_IN_GROUP | 해당 사용자가 이미 그룹에 배정됨 (400) |
| INVALID_SCREEN_ID | 허용 목록에 없는 screen_id 포함 또는 공유 필터 옵션 API의 `screen` 파라미터가 지원되지 않음 (400) |
| INVALID_SCREEN_FUNCTION | 화면별 read/write/approve 조합이 허용되지 않음 (400). main read-only: main에 write=true 또는 approve=true; approve 미지원 화면에 approve=true; write 미지원 화면에 write=true. POST/PUT permission-groups 시 `specs/permission-group-hierarchy.spec.yaml` §1.1.1 검증. |
| USER_NOT_FOUND | 해당 사용자 없음 (404) |
| SELF_DEMOTION_BLOCKED | 자기 자신의 역할 변경 시도 (400) |
| LAST_ADMIN_BLOCKED | 마지막 관리자 강등 시도 (400) |
| SYSTEM_ADMIN_IMMUTABLE | 대상이 시스템 관리자(수정·삭제 불가) (400) |
| LAST_SYSTEM_ADMIN_BLOCKED | 강등 시 시스템 관리자가 0명이 됨 (400) |
| FUNCTION_NOT_ALLOWED | 해당 기능(approve/write 등) 권한 없음. 403 반환 시 사용. 내부 구조·리소스 존재 여부 노출 금지 (403) |
| LOG_TYPE_NOT_ALLOWED | 요청한 logType에 해당하는 화면(pb-feplog/java-fw-imagelog) 접근 권한 없음. 로그 검색·상세·복호화 API에서 403 (req 20260318). **`POST /api/logs/db-refactored/pb-fep-log-search`** 에서 **`pb-fep-log-search` / PB FEP 접근 없음 시 동일 계열 (req 20260326). |

---

## 12. 부서 계층 (관리자 전용)

**Base path**: `/api/departments`

부서는 code·parent_code·name으로 계층 구조. 루트는 parent_code null.  
부서 API는 **관리자(is_system_admin=true)** 만 호출 가능. 그 외 403.  
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
- **PB FEP 와이어프레임 검색 (`pb-fep-log-search`)**: `specs/log-db-pb-fep-log-search.spec.yaml`, 본 문서 §5.1.1.
- **정의 위치**: 이 문서는 현재 구현 기준. API 추가/변경 시 이 문서와 `specs/*.spec.yaml`을 먼저 갱신할 것.

---

## 14. 권한 그룹 및 사용자 권한 계층 (관리자 전용)

**Base path (권한 그룹)**: `/api/permission-groups`  
**사용자 권한 계층**: `GET /api/departments/user-permission-hierarchy`

요건: `docs/requirements/20250227-user-permission-hierarchy-group.md`, `docs/requirements/20250227-permission-group-screen-menu-access.md`, `docs/requirements/20250303-activity-statistics-self-only-scope.md`. 상세 스펙: `specs/permission-group-hierarchy.spec.yaml`.  
모든 API는 **관리자(is_system_admin=true)** 만 호출 가능. 그 외 403, `code: "FORBIDDEN"`.  
**화면 기반 접근**: 화면에 대응하는 API는 사용자가 해당 화면을 권한 그룹으로 허용받았거나 is_system_admin=true이어야 함. 그 외 403. 화면↔API 매핑: `specs/permission-group-hierarchy.spec.yaml` §4.3.  
**화면별 범위(scope)**: activity-log, statistics, search-history, pending-approvals 화면은 권한 그룹에서 화면별 scope('self'|'team'|'all') 설정 가능. scope='self' → 본인 데이터만(또는 본인 요청만)이며 applicable shared-pattern 화면에서는 user/requester block을 숨기지 않고 `department -> username -> userId`의 visible locked self-context를 표시한다. 이 표시값의 권위 소스는 auth/current-user payload의 `selfContext`이고, **`userId`**는 **numeric** **`app_user.id`**이다. search-history requester filter는 `scope=self`에서 무시된다. scope='team' → 동일 부서 범위; scope='all' → 전체. is_system_admin=false일 때만 적용. 상세: `specs/permission-group-hierarchy.spec.yaml` §4.2, §4.3.

### 14.1 권한 그룹 목록 조회

- **GET** `/api/permission-groups`
- **Response (data)**: 배열. 각 항목: `id` (number), `code` (string), `name` (string), `description` (string | null), `sortOrder` (number, 선택), `allowedScreens` (배열: `AllowedScreenItem[]`). `AllowedScreenItem`: `{ screenId, scope?, read?, write?, approve?, decrypt? }`. scope는 activity-log, statistics, search-history, pending-approvals에만 적용; read/write/approve/decrypt는 화면별 명시적 체크박스다. main은 read-only이며 optional `decrypt`만 허용한다. 검증 실패 시 400 `INVALID_SCREEN_FUNCTION`. 상세: `specs/permission-group-hierarchy.spec.yaml` §1.1, §1.1.1.
- **에러**: 401, 403

### 14.2 권한 그룹 생성

- **POST** `/api/permission-groups`
- **Request body** (JSON): `code` (string, 필수), `name` (string, 필수), `description` (string, 선택), `sortOrder` (number, 선택, 기본 0), `allowedScreens` (배열: `{ screenId, scope?, read?, write?, approve? }[]`, 선택). screenId 검증 → 400 `INVALID_SCREEN_ID`; read/write/approve 조합 검증 → 400 `INVALID_SCREEN_FUNCTION`. scope 생략 시 'self'. backward compat: read/write/approve 생략 시 §1.1.1 기본값.
- **Response (data)**: 생성된 권한 그룹 객체 (동일 필드 + `id`, `allowedScreens`)
- **Status**: 201
- **에러**: 400 (code/name 누락·중복, INVALID_SCREEN_ID, INVALID_SCREEN_FUNCTION), 401, 403

### 14.3 권한 그룹 상세 조회

- **GET** `/api/permission-groups/{id}`
- **Path**: `id` — 권한 그룹 ID (Long)
- **Response (data)**: 단일 권한 그룹 객체 (`allowedScreens: [{ screenId, scope?, read?, write?, approve? }]` 포함)
- **에러**: 401, 403, 404 → `code: "PERMISSION_GROUP_NOT_FOUND"`

### 14.4 권한 그룹 수정

- **PUT** `/api/permission-groups/{id}`
- **Path**: `id` — 권한 그룹 ID (Long)
- **Request body** (JSON): `code`, `name`, `description`, `sortOrder`, `allowedScreens` (배열: `{ screenId, scope?, read?, write?, approve? }[]`, 모두 선택), `changeReason` (string, 선택, 최대 2000자 — 관리 UI에서 저장 시 입력 권장; DB 컬럼에 저장하지 않으며 비어 있지 않으면 서버 로그에 감사용으로 기록). screenId 검증 → `INVALID_SCREEN_ID`; read/write/approve 검증 → `INVALID_SCREEN_FUNCTION`. scope 생략 시 'self'. `changeReason` 초과 → 400 `INVALID_INPUT`.
- **Response (data)**: 수정된 권한 그룹 객체
- **에러**: 400 (INVALID_SCREEN_ID, INVALID_SCREEN_FUNCTION), 401, 403, 404 → "PERMISSION_GROUP_NOT_FOUND"

### 14.5 권한 그룹 삭제

- **DELETE** `/api/permission-groups/{id}`
- **Path**: `id` — 권한 그룹 ID (Long)
- **Response (data)**: null 또는 성공 메시지. Status 200 또는 204.
- **동작**: 해당 그룹에 사용자가 배정되어 있으면 400, `code: "PERMISSION_GROUP_HAS_USERS"` 반환 후 삭제하지 않음 (cascade 미적용).
- **에러**: 401, 403, 404, 400 (사용자 배정 있음)

### 14.6 권한 그룹에 사용자 배정

- **POST** `/api/permission-groups/{id}/users`
- **Path**: `id` — 권한 그룹 ID (Long)
- **Request body** (JSON): `{ "userId": number }` — numeric `app_user.id`
- **Response (data)**: `{ "userId", "permissionGroupId", "permissionGroupCode" }` (userId는 number) 또는 동일 의미 객체
- **Status**: 201 또는 200
- **에러**: 400 (userId 누락), 401, 403, 404 그룹 → "PERMISSION_GROUP_NOT_FOUND", 404 사용자 → "USER_NOT_FOUND", 400 이미 배정 → `code: "USER_ALREADY_IN_GROUP"`

### 14.7 권한 그룹에서 사용자 제거

- **DELETE** `/api/permission-groups/{id}/users/{userId}`
- **Path**: `id` — 그룹 ID (Long), `userId` — numeric `app_user.id`
- **Response (data)**: null 또는 성공 메시지. Status 200 또는 204.
- **에러**: 401, 403, 404

### 14.8 권한 그룹별 사용자 목록 (선택)

- **GET** `/api/permission-groups/{id}/users`
- **Path**: `id` — 권한 그룹 ID (Long)
- **Response (data)**: 배열. 각 항목: `userId` (number, `app_user.id`), `username`, `departmentCode`, `isSystemAdmin` 등 사용자 요약 (role 제외, req 20250303)
- **에러**: 401, 403, 404

### 14.9 사용자 권한 계층 조회

- **GET** `/api/departments/user-permission-hierarchy`
- **Query**: `format` — "tree"(기본) | "flat"
- **Response (data)**:
  - **tree**: 루트 노드 배열. 각 노드: `code`, `parentCode`, `name`, `sortOrder`, `children` (재귀), `users` (배열). `users` 각 항목: **`userId`** (number, `app_user.id`, 사용자 ID), **`userName`** (string, 사용자명: `app_user.name`이 존재하고 비어 있지 않으면 그 값, 그렇지 않으면 `app_user.username`), `isSystemAdmin` (boolean, 시스템 관리자 여부), `position` (직책), `rank` (직급), `permissionGroups` (배열: `{ id, code, name }`). role 제외 (req 20250303).
  - **flat**: 부서 노드 배열에 `users` 포함(동일 구조), `children` 없음.
- **에러**: 401, 403

- 기존 부서 트리(code/parent_code)와 동일 구조; 부서별로 해당 department_code를 가진 app_user 목록과 각 사용자의 권한 그룹(permission_group) 목록을 붙여 반환.
