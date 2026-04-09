# API 정의서

현재 백엔드에 구현된 API 목록 및 요청/응답 규격이다.  
**베이스 URL**: `http://localhost:9200/api` (환경·포트는 `docs/contract.md` 참고).

**User ID (userId)**: 요청/응답/경로/쿼리에서 **`userId`**는 **numeric** **`app_user.id`**(JSON type: number, 예: 20269999, 20260001)이다. 로그인 시 사용자 식별은 **userId (numeric)** 만 사용하며, username(문자열)으로는 로그인할 수 없다. **Breaking change**: 동일 릴리즈부터 클라이언트는 모든 API의 userId를 숫자 타입으로 처리해야 하며, 문자열(username) 기반 userId는 지원하지 않는다.

**사번 (`employeeNumber`, 선택)**: 일부 사용자 응답 객체에 **`employeeNumber`**(string \| null, `app_user.employee_number`)가 포함될 수 있다. HR 복제(`ext_employee.employee_number`)와 동일 문자열이며, 프로비저닝·관리 UI에서 **숫자형 `userId`와 별도로 사번을 표시**하는 데 쓴다. 값이 없으면 필드는 생략되거나 null이다.

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

**인증 모드 (요건 `docs/requirements/20260407-external-dept-employee-ad-login.md`)**  
백엔드 **`auth.login.mode`** (`application.yml` / **`application-{profile}.yml`**)가 **단일 진실**이다: **`local`** 또는 **`ad`**. 배포당 하나만 활성. 잘못된 값·`ad`인데 필수 디렉터리 설정 누락 등은 **fail-closed**(기동 실패 또는 로그인 전면 거부; 묵시 폴백 없음). 상세·설정 키 표: `docs/contract.md` § 인증 모드·디렉터리, **`specs/external-identity-auth.spec.yaml`**.

### 2.1 로그인

- **POST** `/api/auth/login`
- **요청 형식은 서버에 설정된 `auth.login.mode`에 의해 결정**된다. 클라이언트가 모드를 보내지 않는다.

#### 2.1.1 `auth.login.mode = local` (테이블 `password_hash`)

- 사용자 식별은 **userId (numeric `app_user.id`)** 만 사용한다(문자열 username으로 로그인하지 않음).
- **Request body** (JSON)

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| userId | number | O | **사용자 ID** (numeric `app_user.id`, 예: 20269999, 20260001). |
| password | string | O | 비밀번호(`password_hash` 검증 경로). |

- **실패(401) — local**: 존재하지 않는 `userId`·비밀번호 불일치 → `INVALID_CREDENTIALS`. **`app_user.deleted_at` 이 설정된(소프트 삭제) 계정** → `USER_ACCOUNT_DISABLED`(자격 증명과 구분하여 운영 확인용).

#### 2.1.2 `auth.login.mode = ad` (디렉터리/LDAP 바인드 등)

- **Request body** (JSON) — **AD 모드에서는 `userId` 대신 디렉터리 principal 사용**

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| principal | string | O | 디렉터리 로그인 식별자(예: UPN, `sAMAccountName` — 운영·`auth.ad.*`와 정합). **공백 불가.** |
| password | string | O | 디렉터리 비밀번호. **서버는 이 값을 `app_user`·로그·감사 본문에 저장하지 않는다.** |

- **성공 시**: 디렉터리 검증 후 **`app_user`** 를 **외부 직원 키 매핑**(예: `external_employee_id`, `external_source_system`)으로 조회·결합. 매핑되는 행이 없으면 **401** 및 요건에 따른 코드(미프로비저닝 정책은 스펙 참고).
- **`local` 모드에서 `principal`만 보내거나, `ad` 모드에서 `userId`만 보내는 경우** → **400** `INVALID_INPUT`(또는 동일 의미 코드).

- **Response (data)**: `{ "user": LoginResponse }`
  - `user.username`: string
  - `user.loginTime`: string (yyyy-MM-dd'T'HH:mm:ss)
  - `user.clientIP`: string
  - `user.isSystemAdmin`: boolean — 시스템 관리자 여부 (req 20250303). true면 전체 화면 접근.
  - `user.allowedScreenIds`: string[] (요건 20250227-permission-group-screen-menu-access) — 사용자 권한 그룹들의 접근 가능 화면 합집합.
  - `user.screenScopes`: Record<string, 'self'|'team'|'all'> (요건 20250303, 20260305) — 화면별 **조회(목록) 범위**. key=screen_id (activity-log, statistics, search-history, pending-approvals, **user-management-v2**), value='self'(본인)|'team'(부서)|'all'(전체). is_system_admin=true이면 생략 가능(프론트는 전체로 처리). **용도**: 목록/조회에만 적용; scope=self → 본인; scope=team → 동일 부서; scope=all → 전체. **`user-management-v2`**: 요건 **`docs/requirements/20260409-user-management-v2-read-scope.md`**, **`specs/user-management-v2.spec.yaml`** §2.2. **승인 범위는 부서로 고정**이며 변경 불가(권한 설정에서 선택하는 scope는 조회 범위만 해당).
  - `user.screenFunctions`: Record<string, { read: boolean, write?: boolean, approve?: boolean, decrypt?: boolean }> (요건 20250303, 20260318) — 화면별 기능 가능 여부. key=screen_id (pb-feplog, java-fw-imagelog, search-history, pending-approvals 등), value=read(필수), write(수정 지원 화면만), approve(search-history·pending-approvals만), decrypt(로그 검색 화면 pb-feplog·java-fw-imagelog 전용, 복호화 요청 권한). pb-feplog·java-fw-imagelog는 read + optional decrypt; decrypt는 권한관리에서 부여/해제. **`pending-approvals`**: **`read`** = 화면 접근 + `GET /api/search-history` 등 목록·상세 **조회**(요건 `20260407-pending-approvals-history-search-readonly-requester`); **`approve`** = 승인/반려 **액션만** (`POST .../approve`, `POST .../reject`). **용도**: 버튼/액션 enable·disable, 비활성 시 툴팁 표시.
  - `user.selfContext`: `{ department: string | null, username: string, userId: number }` — self-scoped user/requester block의 **visible locked self-context** 표시값. `scope=self` 화면에서 Department, Username, User ID를 고정 표시할 때 사용하는 권위 소스다. **`userId`**는 **numeric** **`app_user.id`**(JSON number, 예: 20269999, 20260001)이다. **`username`**은 **표시 이름(사용자명)**: `app_user.name`이 존재하고 비어 있지 않으면 그 값, 그렇지 않으면 `app_user.username`을 사용한다.

### 2.2 로그아웃

- **POST** `/api/auth/logout`
- **Request body**: 없음
- **Response (data)**: null
- **변경 없음**: 인증 모드와 무관하게 동일 계약. **권한 없음(제로 퍼미션) 모달** 후에도 클라이언트는 동일 엔드포인트로 세션 무효화(요건 `20260407-external-dept-employee-ad-login`).

### 2.3 인증 상태 확인

- **GET** `/api/auth/check`
- **Response (data)**:
  - `authenticated`: boolean
  - `message`: string

### 2.4 현재 사용자 정보 (GET /api/auth/me, 선택)

- **GET** `/api/auth/me` — 로그인 사용자 정보 반환. `isSystemAdmin: boolean`, `allowedScreenIds: string[]`, `screenScopes: Record<string, 'self'|'team'|'all'>`, `screenFunctions: Record<string, { read, write?, approve?, decrypt? }>`, `selfContext: { department: string | null, username: string, userId: number }` 포함 (req 20250303, 20260305, 20260313). screenScopes는 조회(목록) 범위(본인/부서/전체) 결정용. **`screenScopes['user-management-v2']`** 는 User Management v2 읽기 경로에 사용(요건 **`20260409-user-management-v2-read-scope`**). 승인 범위는 부서 고정·변경 불가. screenFunctions는 화면별 read/write/approve/decrypt 가능 여부로 버튼·액션 enable·disable용. **`pending-approvals`의 `read` vs `approve`**: `read`는 복호화 승인 관리 화면의 목록·검색 조회; `approve`는 승인/반려만(요건 `20260407-pending-approvals-history-search-readonly-requester`). **`search-history`·`pending-approvals`의 `approve`**: `docs/contract.md` **「복호화 승인 자격」** — 권한 그룹 `permission_group_screen.approve`를 먼저 반영한 뒤 **`is_system_admin=true`이면 해당 화면 `approve`는 유효하지 않음(false)**; `ADMIN_EXT` 등 **`is_system_admin=false`인 사용자는 그룹 설정만** 따름. **`selfContext`**는 applicable shared-pattern 화면에서 `scope=self`일 때 보이는 잠금 self-context 표시값의 권위 소스다. **`selfContext.userId`**는 **numeric** **`app_user.id`**(JSON number). **`username`**은 **표시 이름(사용자명)**: `app_user.name`이 존재하고 비어 있지 않으면 그 값, 그렇지 않으면 `app_user.username`을 사용한다.

### 2.4.1 자가 비밀번호 변경 (POST /api/auth/me/password)

- **요건**: `docs/requirements/20260408-my-page-local-password-and-profile.md`. **권위 스펙**: `specs/my-page-password.spec.yaml`.
- **POST** `/api/auth/me/password` — **세션의 `app_user.id`에 대해서만** 동작(관리자 대리 변경 없음).
- **`auth.login.mode=local`** 인 배포에서만 **성공 경로**를 허용한다. **`auth.login.mode=ad`** 인 배포에서는 **403**, 공통 `ApiResponse`, **`code`**: **`PASSWORD_CHANGE_NOT_ALLOWED`** (엔드유저 비밀번호는 디렉터리 관리; AD 비밀번호는 `app_user`에 저장하지 않음 — `docs/contract.md` 동일).
- **Request body** (JSON, camelCase)

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| currentPassword | string | O | 현재 비밀번호(HTTPS 상에서만 평문 전송). |
| newPassword | string | O | 변경할 비밀번호; 서버는 **해시만** 저장·로그에 평문 금지. |
| confirmNewPassword | string | O | `newPassword`와 동일해야 함(trim 후); 불일치 시 **400** `INVALID_INPUT`. |

- **성공**: **200**, `success: true`, **`data`**: `null` (또는 구현이 `{}`를 쓰면 스펙·코드 DOC-CODE-SYNC).
- **오류 (`ApiResponse`)**: 비인증 **401**; AD 모드·정책상 비허용 **403** `PASSWORD_CHANGE_NOT_ALLOWED`; 필드 누락·공백·`newPassword`/`confirmNewPassword` 불일치·(정의 시) 정책 위반 **400** `INVALID_INPUT`; 현재 비밀번호 불일치 **400** `INVALID_CREDENTIALS`(메시지는 사용자 추측 완화에 맞게 일반화 가능).
- **로컬 신규 사용자 초기 로그인 평문**: 운영·온보딩 기대값 **`user123`** (단일 기본값); 저장은 항상 해시 — `docs/contract.md` 「로컬 프로비저닝·온보딩」.
- **성공 후 세션** 유지 또는 무효화는 구현 선택; 확정 시 본 절·스펙에 반영(DOC-CODE-SYNC).

### 2.5 인증은 있으나 화면 권한 없음 — 보호 API (요건 `20260407-external-dept-employee-ad-login`)

- **비인증**(세션 없음·만료): 보호 리소스 → **401** `UNAUTHORIZED`(또는 프로젝트 기존 동일 의미 코드).
- **인증됨** + **시스템 관리자 아님** + **`allowedScreenIds`가 비어 있음**(유효 화면 0): **로그 검색·사용자 관리·프로비저닝 등 보호 API** → **403** `FORBIDDEN` / `FUNCTION_NOT_ALLOWED` 등(리소스별 기존 패턴 유지). **예외 허용(세션 유지·클라이언트 판단용)**: `GET /api/auth/check`, `GET /api/auth/me`, **`POST /api/auth/me/password`**(로컬 모드에서만 유효한 변경; AD는 **403** `PASSWORD_CHANGE_NOT_ALLOWED`), **`POST /api/auth/logout`** — 상세는 `specs/external-identity-auth.spec.yaml` §5, `specs/my-page-password.spec.yaml`.

---

## 2a. 외부 직원·부서 검색 및 사용자 프리프로비저닝 (관리자)

**Base path**: `/api/provisioning`  
**요건**: `docs/requirements/20260407-external-dept-employee-ad-login.md`  
**권한**: **사용자 관리 접근**과 동일 계열 — `is_system_admin=true` **또는** `allowedScreenIds`에 **`user-management`**, **`user-permission-hierarchy`**, **`user-management-v2`** 중 하나 이상 포함(`AuthService.canAccessUserManagementView`와 정합; 요건 **`20260409-user-management-v2-read-scope`**). 그 외 **403** `FORBIDDEN`. 비인증 **401**.

### 2a.1 외부 직원(`ext_employee`) 검색 (통합 검색)

- **POST** `/api/provisioning/external-employees/search`
- **목적**: 단일 엔드포인트로 **부서명** + **사용자 ID(인사정보)**(`ext_employee.employee_number`, 사용자 관리의 **사용자 ID**와 같은 8자리 등 숫자 표기 convention) + **직원명** 등을 함께(선택 조합) 필터링. `POST /api/provisioning/external-departments/search`는 하위 호환을 위해 유지되나, UI에서는 본 API로 통합 검색하는 것을 권장.
- **Request body** (JSON, 예): `keyword`(직원명, `display_name` ILIKE), `employeeNumber`(앞뒤 공백 trim 후 값이 있을 때만 적용, prefix LIKE), **`departmentName`**(부서 표시명, `ext_department.name` ILIKE; 선택), `externalDepartmentId`, `sourceSystem`, `page`, `pageSize` — 필드·상세는 `specs/external-identity-auth.spec.yaml` §4.1.
- **Response (data)**: 페이지 목록 + 총건수; 각 행은 복제 테이블의 비PII·표시용 필드, **`departmentName`**(조인된 `ext_department.name`, 없으면 null), 외부 키(등록 시 사용).  
  **프로비저닝 여부 (UI 중복 등록 비활성화용)**: `ext_employee` 행과 동일 `(source_system, external_employee_id)` 로 **`app_user_external_identity` 에 매핑이 있으면** 조인 결과로 다음 필드가 채워진다(매핑 없으면 `provisioned: false`, 나머지 선택 필드는 생략 또는 null).
  - **`provisioned`**: boolean — 매핑 행이 있으면 `true`
  - **`provisionedUsername`**: string (선택) — 연결된 `app_user.username`
  - **`provisionedAppUserId`**: number (선택) — 연결된 `app_user.id`(numeric, 로그인/표시 힌트)
  - 목록·건수 쿼리는 동일 `FROM`/`JOIN`/`WHERE` 를 사용한다(외부 킹당 매핑 최대 1행이므로 건수는 직원 행 수와 일치).

### 2a.2 외부 부서(`ext_department`) 검색

- **POST** `/api/provisioning/external-departments/search`
- **Request body** / **Response**: `specs/external-identity-auth.spec.yaml` §3.

### 2a.3 외부 직원 행으로 `app_user` 등록

- **POST** `/api/provisioning/users/from-external-employee`
- **Request body** (JSON, camelCase): 선택된 **`ext_employee`** 행 키 **`externalEmployeeId`**(필수), 선택 **`sourceSystem`**, 선택 **`departmentCode`** — 스펙 `specs/external-identity-auth.spec.yaml` §4.3.  
  **`changeReason`** (string, **필수**) — 등록·프로비저닝 **사유**. 권한 그룹 감사와 동일 필드명; **trim 후 비어 있지 않음**, **최대 500자**(초과·공백만 → **400** `INVALID_INPUT`). `departmentCode`는 생략 가능; 생략 시 해당 직원의 `external_department_id`에 대해 `department_org_link`에 행이 있으면 그 `department.code`로 `app_user.department_code` 설정.
- **성공**: 새 **`app_user`** + `app_user_external_identity` 저장(등록). **AD 비밀번호 저장 없음**. **`data`**: `userId`(number, `app_user.id`), `username`(string), **`employeeNumber`**(string \| 생략, `ext_employee.employee_number`와 동일·trim; 복제 행에 사번이 없으면 생략/null) — UI에서는 **사번을 주요 표시 식별자**로 쓰는 것을 권장한다.  
  **감사**: 성공 시 활동 로그에 **`USER_CREATE`**와 `action_detail`에 **`changeReason`**, **`targetUserId`**, **`employeeNumber`**, **`username`**, **`registrationSource`**: **`EXTERNAL_PROVISIONING`**(신규 기록 권장) 등 비민감 식별자만 포함(§8.0, `specs/activity-action-types.spec.yaml` §2.8·§3 **user_admin**).
- **충돌·중복 외부 키**: **409**, 코드 **`EXTERNAL_IDENTITY_CONFLICT`**. 응답 본문은 공통 `ApiResponse` 형식이며, **`data`**(실패 시에도 사용 가능)에 기존 계정 힌트가 있을 수 있다(매핑 조회 결과가 있을 때만).
- **사번 중복(활성 사용자 간)**: **409**, 코드 **`USER_EMPLOYEE_NUMBER_DUPLICATED`** — 다른 **활성** `app_user`(`deleted_at IS NULL`)가 이미 **같은 trim된 비-null `employee_number`**를 쓰는 경우(User Management v2 **`POST /api/user-management-v2/users/direct`** 와 동일 코드·메시지 계열; `specs/user-management-v2.spec.yaml` §4.4). 소프트 삭제된 행만 동일 사번이면 재사용 허용.
- **검증 실패**(필수 필드·**`changeReason`** 누락·공백·길이 초과 등): **400** `INVALID_INPUT`.
  - **`existingUsername`**: string (선택) — 이미 연결된 `app_user.username` (공백이 아닐 때만 포함)
  - **`existingAppUserId`**: number — 이미 연결된 `app_user.id`

---

## 2b. HR Sync PoC (preview-only)

**Base path**: `/api/hr-sync/poc`  
**요건**: `docs/requirements/20260408-external-hr-user-sync-security-db-design.md` (§2.6 PoC), 스냅샷·샘플·인력 조회 `docs/requirements/20260408-hr-sync-poc-snapshot-list-and-sample-data.md`, 미리보기 가시성·스냅샷 범위 `docs/requirements/20260408-hr-sync-poc-run-preview-visible-results.md`, PoC UM v2 격리 클론 `docs/requirements/20260408-poc-user-management-v2-isolated-clone.md`.  
**계약 요약**: `docs/contract.md` § HR Sync PoC (preview-only). **권위 스펙**: `specs/hr-sync-poc.spec.yaml`. **DOC-CODE-SYNC**: 구현 후 경로·필드·코드가 스펙과 다르면 코드와 동시에 본 문서·계약·스펙을 갱신한다 (`docs/workflow/DOC-CODE-SYNC.md`).

**플래그 (예)**: `HR_SYNC_POC_ENABLED`, `HR_SYNC_POC_DEFAULT_MODE` (기본 **`PREVIEW_ONLY`**), `HR_SYNC_POC_APPLY_ENABLED` (기본 off). `GET .../config`는 **비밀 없이** boolean/string만 반환한다.

### 2b.1 PoC 설정 조회

- **GET** `/api/hr-sync/poc/config`
- **Response (`data`)**: `{ "pocEnabled": boolean, "defaultMode": string, "applyEnabled": boolean }`
- **에러**: 비인증 **401**; PoC off 등 **403** `POC_DISABLED` (계약 `docs/contract.md`와 정렬).

### 2b.2 PoC 미리보기 (read-only)

- **POST** `/api/hr-sync/poc/preview`
- **계약**: **`app_user` / 애플리케이션 권한·프로덕션 권한 소스에 쓰기 없음** (preview 전용).
- **Request body** (JSON):

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| snapshotId | string | X | 스냅샷 식별자. |
| ingestRunId | string | X | 업스트림 적재 실행 식별자. |

- **Response (`data`)**:  
  `{ "previewId": string, "snapshotId": string, "classificationCounts": { "TRANSFER": number, "NEW_HIRE": number, "RESIGNED": number, "UNCHANGED": number, "PROFILE_UPDATE_NON_SECURITY": number, "CONFLICT": number, "ORPHAN": number }, "riskTier": string, "upstreamGateStatus": string, "messageCode": string }`  
  - **`classificationCounts` 범위**: 요청에 **비어 있지 않은 `snapshotId`가 있으면** 복제 **`ext_employee`의 해당 `snapshot_id` 행만** 집계한다. **`snapshotId`가 없으면** PoC **전역 stub**(스냅샷 미제한·예: 전체 테이블 기준 stub) — 상세 **`specs/hr-sync-poc.spec.yaml` §4.2**.
  - **빈 스냅샷(0행)**: **200**, 모든 분류 키 **0** — 정상; **404** 아님(스냅샷 인력 §2b.4와 구분).
  - `riskTier`: 요건의 자동화 위험 등급 계열(예: `AUTO` \| `CONDITIONAL` \| `APPROVAL_REQUIRED`) 또는 구현 placeholder 문자열.  
  - `upstreamGateStatus`: 업스트림 완료·매니페스트 게이트 상태; **연동 전**에는 placeholder(예: `NOT_READY` / `PLACEHOLDER`) 가능.  
  - `messageCode`: UI·클라이언트용 안전한 기계 판독 힌트(비밀 없음).

- **에러 (요약)**:
  - **400** `VALIDATION_ERROR` — 본문·식별자 검증 실패.
  - **403** `POC_DISABLED` — PoC 비활성 또는 호출 비허용.
  - **403** 또는 **503** 등 제품 정합 HTTP — `SYNC_SOURCE_NOT_READY` — 업스트림·완료 신호 미사용/미준비(placeholder 응답과 병행 가능; DOC-CODE-SYNC).
  - **503** `HR_SYNC_POC_PREVIEW_FAILED` — **`classificationCounts` 집계 중** 복제/DB 오류 등; **200**+전부 0으로 실패를 숨기지 않음(스펙 §4.2).

### 2b.3 PoC 스냅샷 목록 (read-only)

- **GET** `/api/hr-sync/poc/snapshots`
- **목적**: UI가 **서버가 제공하는 스냅샷 id** 중 하나를 고를 수 있도록, 복제데이터 기준 **사용 가능한 스냅샷**과 최소 메타데이터를 반환한다. `GET .../config`로 PoC 활성 여부를 확인한 뒤 호출하는 흐름과 정렬된다.
- **Response (`data`)**: `{ "snapshots": [ { "snapshotId": string, "label": string | null, "employeeCount": number, "maxImportedAt": string | null }, ... ] }` — 필드 의미·정렬은 **`specs/hr-sync-poc.spec.yaml` §4.3** 과 동일.
- **인증·권한**: 세션 필수; PoC 미활성 **403** `POC_DISABLED`; PoC 미리보기와 **동일 계열**의 화면·역할이 아니면 **403** `FORBIDDEN` / `FUNCTION_NOT_ALLOWED`(인터셉터·스펙 §3과 DOC-CODE-SYNC).
- **데이터 경계**: `app_user`·권한 테이블에는 **쓰지 않으며**, 응답에 **비밀·원시 매니페스트**를 넣지 않는다.

### 2b.4 스냅샷별 인력(복제 직원) 페이지 (read-only)

- **GET** `/api/hr-sync/poc/snapshots/{snapshotId}/employees`
- **목적**: 선택한 **`snapshotId`**에 대해 **`ext_employee`**(필요 시 `ext_department` 조인)를 **페이지 단위**로 읽어, PoC 인력 테이블에 표시할 **최소 필드**만 반환한다.
- **Query**:

| 파라미터 | 타입 | 기본 | 설명 |
|----------|------|------|------|
| page | integer | 1 | 1-based 페이지. |
| size | integer | 20 | 페이지 크기; **최대 100**(초과 **400** `VALIDATION_ERROR`). |

- **Response (`data`)**: `{ "snapshotId": string, "employees": [ ... ], "pagination": { "currentPage": number, "totalPages": number, "totalCount": number } }`  
  각 `employees[]` 항목: `displayName`, `jobTitle`, `departmentKey`, `departmentName`, `active`, 선택 `employeeNumber`(**PoC**: `ext_employee.employee_number` **전체**, 마스킹 없음). **`email` 미포함** — 스펙 §4.4.
- **에러 (요약)**:
  - **400** `VALIDATION_ERROR` — 잘못된 경로 `snapshotId`, `page`/`size` 범위 위반.
  - **401** — 비인증.
  - **403** `POC_DISABLED` — PoC off.
  - **403** `FORBIDDEN` / `FUNCTION_NOT_ALLOWED` — PoC 인력 API에 대한 접근 불가.
  - **404** `NOT_FOUND` — 해당 스냅샷에 복제 행이 없음(스펙이 **빈 200** 대신 **404**로 확정; 변경 시 DOC-CODE-SYNC).

### 2b.5 Apply(PoC) — 차단

- 요건: **PoC 기본은 `PREVIEW_ONLY`**; **`HR_SYNC_POC_APPLY_ENABLED` 미충족 시 변경 적용 불가.**
- 계약: **(a)** apply 전용 경로를 두었다면 **403**(또는 제품 정한 HTTP)으로 거부하거나, **(b)** **엔드포인트 미노출** — 구현 선택. 스케줄/API 우회 허용 없음.

### 2b.6 PoC User Management (UM v2 클론, read-only + stub)

**Prefix**: `/api/hr-sync/poc/user-mgmt`  
**화면 id**: **`user-management-v2-poc`** (`allowedScreenIds`에 별도 부여; 프로덕션 **`user-management-v2`** 와 독립 — `docs/contract.md`).

**보안 (다른 PoC API와 동일 계열)**

- 비인증 → **401**.
- **`HR_SYNC_POC_ENABLED` off** → **403** `POC_DISABLED`.
- 세션은 있으나 **`user-management-v2-poc`** 화면 권한 없음 → **403** `FORBIDDEN` / `FUNCTION_NOT_ALLOWED` (인터셉터·스펙 §3.1).

#### 2b.6.1 복제 부서 트리 (read-only)

- **GET** `/api/hr-sync/poc/user-mgmt/replica-departments/tree`
- **소스**: **`ext_department`** 만 (PoC/샘플 `source_system`; 쿼리 **`sourceSystem`** 기본 **`HR_SAMPLE`** — `specs/hr-sync-poc.spec.yaml` §4.5).
- **Response (`data`)**: `{ "sourceSystem": string, "roots": ReplicaDepartmentTreeNode[] }` — 노드: `departmentKey`, `parentDepartmentKey`, `name`, `sortOrder`, `children` (중첩).
- **쓰기 없음**: `app_user` · 앱 권한 · 프로덕션 Tree 미변경.

#### 2b.6.2 복제 사용자 목록 (read-only)

- **GET** `/api/hr-sync/poc/user-mgmt/replica-users`
- **소스**: **`ext_employee`** (+ `ext_department` 조인 표시명). 선택 **`snapshotId`**, **`departmentKey`**, **`sourceSystem`**(기본 `HR_SAMPLE`), **`page`** / **`size`** (size 최대 **100**).
- **Response (`data`)**: `{ "snapshotId": string | null, "departmentKey": string | null, "sourceSystem": string, "users": [...], "pagination": { "currentPage", "totalPages", "totalCount" } }`  
  `users[]` 항목은 §2b.4 `employees[]`와 동일 계열(`displayName`, `jobTitle`, `departmentKey`, `departmentName`, `active`, 선택 `employeeNumber`; **email 없음**).
- **`snapshotId`가 있고** 복제에 없거나 0건이면 **404** `NOT_FOUND`(§4.6 스펙 선택; `snapshotId` 생략 시 빈 목록은 **200**).
- **쓰기 없음**: `app_user` 미변경.

#### 2b.6.3 마이그레이션 미리보기 스텁 (비영속)

- **POST** `/api/hr-sync/poc/user-mgmt/actions/migrate-preview`
- **목적**: UI “마이그레이션 테스트” 동작을 **영향 없이** 연결.
- **성공 (200)**: `success: true`, **`data`**: `{ "persisted": false, "messageCode": "POC_ACTION_NOT_PERSISTED" }` — `ApiResponse.code`는 성공 시 통상 생략·성공 의미.
- **규범**: 이 핸들러는 **`app_user`** 및 애플리케이션 권한·프로덕션 Tree에 대해 **INSERT/UPDATE/DELETE를 수행하지 않는다**. 실제 반영을 암시하는 활동 감사도 남기지 않는다(스펙 §4.7).

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
- **저장 형식과 복호화**: DB에 저장된 암호문은 **ProObject 호환**(PBKDF2로 키 유도, `AES/CBC/PKCS5Padding`, Base64 페이로드)이며, **java_fw_imglog** 는 선택적 **`E002` 접두**가 있을 수 있다. **pb_feplog** 는 동일 알고리즘·`E002` 비적용. 서버는 위 형식 복호화 후 실패 시 **레거시** `ivHex:encryptedHex`(UTF-8 키)를 시도한다. 상세: `docs/contract.md` 암·복호화 표 직후 단락.

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

**화면별 범위(scope)**: is_system_admin=false일 때 **목록·상세의 유효 scope**는 아래 **`listContext`** 규칙에 따라 `search-history` 또는 `pending-approvals` 중 하나의 `screenScopes` 키를 선택한다(요건 `20260407-pending-approvals-history-search-readonly-requester`). **`listContext=search-history`**(또는 기본이 search-history로 해석되는 경우): 기존과 같이 **`screenScopes['search-history']`** 를 적용한다. **`listContext=pending-approvals`**: **`screenScopes['pending-approvals']`** 를 적용한다(검색 이력 화면과 동일한 self/team/all 해석 패턴; 세부는 `specs/permission-group-hierarchy.spec.yaml` §4.3, `docs/requirements/20260305-pending-approvals-scope-same-as-search-history.md` 정신 유지). scope='self' → 현재 요청자 본인 데이터만 반환하며 requester filter(`department`, `username`, `userId`)는 무시한다. 이때 requester block은 숨기지 않고 `department -> username -> userId` 순서의 visible locked self-context를 표시하며, 표시값은 auth/current-user payload의 `selfContext`를 기준으로 한다. scope='team' → 동일 부서 요청자만 반환하며 requester filter는 그 허용 집합 안에서만 추가 좁힘; scope='all' → 전체 가시 집합에 requester filter를 적용. requester filter는 scope를 넓히지 않으며, 상세 규칙은 `specs/permission-group-hierarchy.spec.yaml` §4.3을 따른다.

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
- **화면 접근(인터셉터)**: 인증 사용자가 **`search-history` 화면**을 가지거나, **`pending-approvals` 화면을 가지고 `screenFunctions.pending-approvals.read === true`** 인 경우 이 GET을 호출할 수 있다(요건 `20260407-pending-approvals-history-search-readonly-requester`). 후자는 복호화 승인 관리에서 **검색 이력 화면 권한 없이** 목록·필터만 사용하는 요청자·승인 전용 그룹을 위한 것이다. 둘 다 없으면 403.
- **`listContext` (선택)**: `search-history` \| `pending-approvals`. **어떤 화면의 `screenScopes`·필터 규칙으로 행을 제한할지** 지정한다. **생략 시 해석**: `allowedScreenIds`에 **`search-history`가 있으면** `search-history`; **`search-history`는 없고 `pending-approvals`만 있으면** `pending-approvals`; **두 화면 모두 있으면** `search-history`(검색 이력 화면과의 하위 호환). **복호화 승인 관리** UI는 행 집합이 기존 **`/pending` + pending-approvals scope** 와 정합되도록 **`listContext=pending-approvals`** 로 호출한다. 잘못된 값·권한 없는 컨텍스트(예: `listContext=search-history`인데 `search-history` 미보유)는 403 `FUNCTION_NOT_ALLOWED` 등 계약 코드.
- **List join**: **app_user.id = search_history.user_id**. Requester filter `userId` is exact match on `search_history.user_id` (numeric). Response `userId` is numeric `app_user.id`.
- **Query**:
  - `listContext` (선택) — 위 참조.
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
- **Query**: `listContext` (선택) — §6.1.2와 **동일한 값·생략 규칙**. 상세 접근은 **목록에서 해당 `listContext`로 가시한 행만** 허용한다(목록 가시성과 불일치 시 403). 호출 화면(검색 이력 vs 복호화 승인 관리)에 맞춰 목록과 동일한 `listContext`를 전달한다.
- **Response (data)**: `id`, `logType`, `searchParams` (object, 전체 검색 조건), `requestedAt`, `expiresAt`, `approvalStatus`, `requestReason` (string | null, 요청 사유; req 20260317), **`searchResultTotalCount` (number \| null), `decryptionTargetCount` (number \| null)** — DB 저장 스냅샷(레거시 null), 결재 이력(선택·nullable): `approvedBy` (표시용; req 20260316: `approved_by_user_id`→username, 없으면 `approved_by`), `approvedAt`, `rejectedBy`, `rejectedAt`, `rejectionReason`. **복호화 요청 대상 (항상 포함, req 20260320)**: `decryptionRequestedRows`: `{ application: string | null, serviceGroup: string | null, guid: string, status: string }[]` (java_fw_imglog의 `status`는 행의 비즈니스 status; 스냅샷·복합 키와 정렬), `decryptionRequestedCount`: number. **출처**: `APPROVED`이고 `search_history_approved_row`에 행이 있으면 DB 스냅샷을 사용(없으면 승인과 동일하게 저장 검색 재실행: `search_params`+`logType`, 최대 1만 건, 암호화 데이터가 있는 행만). `PENDING` / `REJECTED` / `EXPIRED`는 항상 저장 검색 재실행으로 수집(동일 규칙). java_fw_imglog는 `(guid, status)`로 로그 DB 보강; 로그 DB 장애·미존재 시 해당 항목은 null. `search_params` 파싱 실패·검색 실패 시 빈 배열·0.
- **에러**: 403(타 사용자 소유 또는 listContext 기준 비가시), 404(없음)

### 6.1.5 승인 대기 목록 조회 (복호화 승인 권한 보유자 전용)

- **GET** `/api/search-history/pending`
- **역할**: **PENDING 상태만** 빠르게 보는 전용 큐(레거시). **역사·상태 필터·검색 이력과 동일한 목록**이 필요하면 **`GET /api/search-history`** + §6.1.2 쿼리(`approvalStatus`, `requestedAtFrom` 등) + **`listContext=pending-approvals`** 를 사용한다(요건 `20260407-pending-approvals-history-search-readonly-requester`). `/pending`을 확장해 비-PENDING을 포함하는 모델은 **계약상 선택 사항**이며, 채택 시 동일 PR에서 본 문서·`specs/`를 갱신한다.
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

**접근**: 시스템 관리자 **`is_system_admin=true`** 또는 사용자 관리 계열 화면 권한(**`user-management`**, **`user-permission-hierarchy`**, **`user-management-v2`** 등 — `specs/permission-group-hierarchy.spec.yaml` §4.3, `AuthService.canAccessUserManagementView` 정합). 단순히 “관리자”라고 해서 **시스템 관리자만** 의미하지 않음; 권한 그룹으로 부여된 운영자도 해당 화면이 있으면 호출 가능. 그 외 **403**. (req 20250303, 20260409)

### 7.1 사용자 목록 조회

- **GET** `/api/users`
- **조회 범위(scope)**: 호출자가 **`user-management-v2`** 로 이 목록을 사용하는 경우(세션에 해당 화면이 있고 공유 API를 통해 로드하는 경우), 비시스템 관리자에게는 **`screenScopes['user-management-v2']`** (`self`|`team`|`all`, 기본 `team`)에 따라 사용자 행이 **서버 측 필터**된다. **`is_system_admin=true`** 는 전체 목록(기존과 동일). 클라이언트 쿼리로 범위를 넓히는 것은 불가(요건 **`20260409-user-management-v2-read-scope`**, **`specs/user-management-v2.spec.yaml`** §2.2). 레거시 **`user-management`** 만 있는 경우의 목록 동작은 구현·DOC-CODE-SYNC로 기존과 정합.
- **Response (data)**: 배열. 각 항목:
  - `userId`: number — numeric `app_user.id` (사용자 ID)
  - `employeeNumber`: string (선택) — `app_user.employee_number`, HR 사번과 동일; 없으면 생략
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

### 7.3 사용자 삭제 (req `20260407-user-management-consistency-delete-reason-activity-audit`)

- **DELETE** `/api/users/{userId}`
- **Path**: `userId` — number — numeric `app_user.id` (요청 URL 경로; JSON 타입은 number와 호환되는 정수 표기).
- **접근·권한**: **`GET /api/users`와 동일** — `is_system_admin=true` **또는** 사용자 관리 화면 접근(`user-management` / `user-permission-hierarchy` / **`user-management-v2`** 등 `AuthService.canAccessUserManagementView`와 정합). 비인증 **401**; 접근 불가 **403** `FORBIDDEN` 등.
- **조회 범위 밖 삭제 시도**: **`screenScopes['user-management-v2']`** 기준 대상이 스코프 밖이면 **`403`** **`FUNCTION_NOT_ALLOWED`** 또는 **`404`** **`USER_NOT_FOUND`** 등 — `specs/user-management-v2.spec.yaml` §2.3, `specs/permission-group-hierarchy.spec.yaml` §4.3.
- **Request body** (JSON, **필수** — 본문 없는 DELETE는 계약상 거부):  

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| changeReason | string | O | 삭제 **사유**. 권한 그룹 `permissionGroupAuditV1.changeReason`과 **동일 필드명**; **trim 후 비어 있지 않음**, **최대 500자**(MF-03·활동 로그 영속 한도와 정렬). 공백만·누락·초과 시 **400** `INVALID_INPUT` (TC-05). |

- **성공**: **200**, `success: true`. **`data`**: 제품이 정한 요약(예: 삭제된 `userId`) 또는 `null` — 구현과 동일하게 유지(DOC-CODE-SYNC). 물리 삭제 vs 소프트 삭제는 DB/백엔드 결정; 기본 목록(`GET /api/users`)에서 제외되는 동작과 일치해야 함 (TC-06).
- **감사**: 성공 시 **`USER_DELETE`** 활동 로그; `action_detail`에 **`changeReason`**, **`targetUserId`** 및 존재 시 **`employeeNumber`**, **`username`** 등만 허용(§8.0, `specs/activity-action-types.spec.yaml` §3 denylist).
- **오류 (요약·테스트 ID)**:
  - **400** `INVALID_INPUT` — `changeReason` 누락·공백·500자 초과 등 (TC-05).
  - **400** `LAST_SYSTEM_ADMIN_BLOCKED` / `SYSTEM_ADMIN_IMMUTABLE` — 마지막 시스템 관리자 삭제 불가, 대상이 시스템 관리자 등 기존 시스템 관리자 보호 규칙(`docs/contract.md`). HTTP는 §11·구현과 통일(통상 **400**; TC-07).
  - **404** `USER_NOT_FOUND` — 대상 사용자 없음.
  - **409** `USER_DELETE_REFERENCED` — 종속 데이터 등 **FK·참조 무결성**으로 삭제 불가; 부분 삭제 없음 (TC-08). 구현이 **400** + 동일 코드로 통일할 경우 계약·코드 동시 갱신.

### 7.4 User Management v2 — 부서 삭제

**Base path**: `/api/user-management-v2` — 계약·상세는 **`specs/user-management-v2.spec.yaml`** §2·§4.3.

- **DELETE** `/api/user-management-v2/departments/{departmentId}`
- **Path**: `departmentId` — 레거시 필드명이며 **부서 코드 문자열**(`departmentCode`와 동일 의미); 다른 v2 부서 API와 동일.
- **Request body** (JSON, **필수**): `changeReason` — trim 후 비어 있지 않음, **최대 500자** 등 v2 mutation 공통 규칙(스펙 표). 본문 생략·공백만·초과 시 **400** `INVALID_INPUT`.
- **접근·권한**: v2 스펙 §2 — 인증 + 사용자 관리 화면 접근; 변경 API는 **`screenFunctions['user-management-v2'].write`** (권장) 또는 레거시와 정합된 `user-management` write 또는 시스템 관리자. **401** / **403** `FORBIDDEN` / **403** `FUNCTION_NOT_ALLOWED` 패턴은 스펙과 동일. **유효 조회 범위 밖** 대상에 대한 변경은 §2.3·동일 문서 §7.1 scope 설명 참고.
- **성공**: **200**, `success: true`. **`data`**: 구현이 정한 요약(예: 삭제된 부서 코드) 또는 `null` — DOC-CODE-SYNC.
- **감사**: 성공 시 **`DEPARTMENT_DELETE`**; `action_detail` **department_admin** (`specs/activity-action-types.spec.yaml` §2.8·§3).
- **오류 (ApiResponse `code`)**:
  - **404** `DEPARTMENT_NOT_FOUND`
  - **409** `DEPARTMENT_HAS_CHILDREN` — 하위 부서 존재
  - **409** `DEPARTMENT_HAS_ACTIVE_USERS` — 해당 부서를 쓰는 사용자 존재
  - **409** `DEPARTMENT_ORG_LINK_REFERENCES` — `department_org_link` 등 조직 연계 참조 존재  
  (HTTP는 스펙·구현 정합; 통상 위와 같이 404/409.)

---

## 8. 사용자 활동 이력 (Activity Log)

**Base path**: `/api/activity-log`

**화면별 범위(scope)**: is_system_admin=false일 때 권한 그룹의 activity-log scope 적용. scope='self' → user/requester block은 숨기지 않고 visible locked self-context로 유지된다. `department`, `username`, `userId`는 auth/current-user payload의 `selfContext` 기준으로 표시되고 수정할 수 없으며, `userId`는 **numeric** **`app_user.id`**이다. 검색 실행 시 userId는 현재 인증 사용자로 강제되고, username, department(또는 departmentCode), ipAddress 등 사용자·부서 관련 파라미터와 동등한 widening 입력은 무시되거나 안전하게 override되며, 현재 사용자 데이터만 반환한다. `department`에 빈 값, `all`, `ALL`, `전체` 등 "전체" 의미 표현이 들어와도 범위를 넓히지 못한다. scope='team'/'all' → 요청의 department 등 필터 적용. 상세: `specs/permission-group-hierarchy.spec.yaml` §4.3.

### 8.0 `action_type` 단일 기준(OP-01) 및 `action_detail`

- **저장**: 각 행의 유형은 `action_type` 문자열( DB `user_activity_log.action_type`, 예: `VARCHAR(50)` )이다. **닫힌 코드 집합**·라벨·예비(provisional) 코드는 **`specs/activity-action-types.spec.yaml`** §2가 단일 기준이다. **인앱 복사·접근 감사(동일 테이블 재사용 시)** 등 추가 코드는 **§2.7** 및 요건 `20260330-audit-evidence-activity-log-conservative`를 따른다.
- **구현 상태**: 이미 기록되는 유형에는 `LOGIN`, `LOGOUT`, `SEARCH`, `VIEW`, `DECRYPT`, `ADVANCED_SEARCH` 등이 있으며, 메서드명 추론 시 `EXPORT`, `STATS_VIEW`, `SCHEMA_VIEW`, `UNKNOWN` 등이 사용될 수 있다(상세 표는 스펙 참고). **권한 그룹·사용자·부서 결재자·검색 이력·복호화 승인** 관련 코드는 요건 `20260330-activity-types-user-mgmt-permission-group`에 따라 Step 4에서 상수·기록 지점과 함께 확정한다.
- **`action_detail`**: JSON 객체. 카테고리별로 허용되는 키는 스펙 §3(권한 그룹 id, 대상 `userId`, `searchHistoryId` 등 **비민감 식별자**). 비밀번호·토큰·복호화 본문·세션 식별자는 넣지 않는다. **보수적 감사 요건**에 따른 **복사 페이로드**(잘림·`was_truncated`), **삭제 스냅샷**, **허용 필드만의 before/after** 는 `specs/activity-log-audit-evidence.spec.yaml` §3·§4가 고수준 권위이며, 필드 분류 매트릭스는 요건 §2 Solution approach 참고.
- **권한 그룹 관련 타입 (`PERMISSION_GROUP_CREATE` / `PERMISSION_GROUP_UPDATE` / `PERMISSION_GROUP_DELETE` / `ASSIGN_USER_TO_PERMISSION_GROUP` / `UNASSIGN_USER_FROM_PERMISSION_GROUP`)**  
  - **버전 스키마**: 구현 목표는 `action_detail` 내 **`permissionGroupAuditV1`** 객체(자세한 필드·`before`/`after`·중첩 `allowedScreens`는 `specs/activity-permission-group-audit.spec.yaml`).  
  - **`ASSIGN_USER_TO_PERMISSION_GROUP` / `UNASSIGN_USER_FROM_PERMISSION_GROUP`**: persisted **`permissionGroupAuditV1`** MUST populate **`before`** and **`after`** per **`specs/activity-permission-group-audit.spec.yaml`** §3.5 and requirement **`docs/requirements/20260407-permission-group-assign-unassign-audit-before-after.md`** — **assign:** `before` = previous group snapshot or **`null`** if no prior membership, `after` = new group snapshot; **unassign:** `before` = group being left, `after` = **`null`**. When `allowedScreens` is incomplete or omitted for size/policy, set **`allowedScreensTruncated`** per spec §3.2.  
  - **Denylist**: 부모 요건 `20260330-activity-types-user-mgmt-permission-group` §2.1 Security 및 동일 스펙 §6 — `password`, `token`, `refreshToken`, 원시 요청 본문 등 **금지**.  
  - **`changeReason` (잠정)**: `PERMISSION_GROUP_UPDATE`에서 클라이언트가 보낸 사유는 **`permissionGroupAuditV1.changeReason`**으로 영속할 때 **최대 500자**; 제품이 생략 정책으로 바꾸면 계약·스펙 우선 수정.  
  - **사용자 생명주기 (`USER_CREATE`, `USER_DELETE`)** (req `20260407-user-management-consistency-delete-reason-activity-audit`, `20260408-user-management-v2-activity-audit-detail-in-activity-log`): 외부 직원 **프로비저닝 성공** 시 **`USER_CREATE`**; **User Management v2 직접 등록** 성공 시에도 **`USER_CREATE`**(별도 `USER_CREATE_DIRECT` 코드는 계약에 두지 않음 — 구분은 `action_detail`). **사용자 삭제 성공** 시 **`USER_DELETE`**. `action_detail`는 **`changeReason`**, **`targetUserId`**(number, `app_user.id`), 선택 **`employeeNumber`**, **`username`**, v2 직접 등록 시 선택 **`name`**, **`departmentId`**(대상 부서 코드), **`rank`**, **`permissionGroupId`**, 그리고 **`USER_CREATE`**용 **`registrationSource`**: **`EXTERNAL_PROVISIONING`** \| **`USER_MANAGEMENT_V2_DIRECT`**(신규 기록은 구분값 설정 권장). 스펙 §3 **user_admin** / denylist 준수. 단일 기준: `specs/activity-action-types.spec.yaml` §2.4·§2.8·§3, **`GET /api/activity-log/action-types`**(TC-11).
  - **부서 트리 v2 (`DEPARTMENT_CREATE_ROOT` / `DEPARTMENT_CREATE_CHILD` / 계약 시 `DEPARTMENT_UPDATE`·`DEPARTMENT_DELETE`)**: 수동 부서 **생성·수정·삭제**는 **전용 `action_type`**으로 기록한다. **`USER_UPDATE`로 부서 변경을 대체하지 않는다**(스펙 §2.4·§2.8). `action_detail` 요약: **`changeReason`**, **`departmentCode`**, **`parentDepartmentCode`**(루트 생성 시 null), 선택 **`name`**, **`sortOrder`**, 수정 시 허용 키만의 **`before`/`after`** — §3 **department_admin**.
  - **상세 API 일관성**: 아래 `GET /api/activity-log/{id}` 응답의 `action_detail`은 DB에 저장된 JSON과 동일 계열이며, **검색(`POST /api/activity-log/search`)에서 볼 수 있는 행만** 상세 조회 가능해야 한다(요건 §2.1 AC-S2; MF-02는 백엔드 검증).

### 8.0.1 활동 유형 필터 옵션(선택 API)

- **GET** `/api/activity-log/action-types`
- **목적**: 활동 이력 화면의 **활동 유형** `<select>` 옵션을 서버 권위 목록으로 채운다. 프론트엔드 전역 하드코드 배열은 계약상 권장되지 않는다(폴백은 요건 문서 참고).
- **동등 경로**: 동일 응답 형식이면 `GET /api/filter-options/activity-action-types` 구현을 허용하나, **문서 기준 경로**는 `/api/activity-log/action-types` 이다.
- **권한**: 인증 필요. **activity-log** 화면에 대한 읽기 접근(및 기존 activity-log API와 동일한 `is_system_admin`·scope 처리)이 없으면 **403** `FORBIDDEN`. **401** 비인증.
- **Response (data)**: `{ code: string, label: string }[]` — `code`는 `POST /api/activity-log/search`의 `actionType`과 동일 값; `label`은 UI 표시용(한국어 등). 정렬: 기본 `code` 오름차순(스펙 §4.3).
- **스펙**: `specs/activity-action-types.spec.yaml` §4.

### 8.1 활동 이력 검색

- **POST** `/api/activity-log/search`
- **Request body** (JSON):

| 필드 | 타입 | 설명 |
|------|------|------|
| startDate | string | yyyy-MM-dd HH:mm:ss 등 |
| endDate | string | |
| userId | number | (선택) 사용자 ID (numeric `app_user.id`). `scope=self`이면 클라이언트 입력값과 관계없이 현재 인증 사용자로 강제되며, 타 사용자 값으로 범위를 넓힐 수 없다. |
| username | string | (선택) 사용자명 필터. `scope=self`이면 무시되며 결과 범위를 넓히지 못한다. |
| department | string | (선택) 부서 필터. **`department.name` 표시명**과 정확 일치(`GET /api/filter-options/departments` 옵션 문자열과 동일). `app_user.department_code` 직접 비교가 아니다. `scope=self`이면 무시되며, 빈 값, `all`, `ALL`, `전체` 등 전체 의미 표현도 범위를 넓히지 못한다. `scope=team/all`일 때만 적용. body에 departmentCode로 보내도 동일 필드로 처리. (req 20260310) |
| actionType | string | (선택) `user_activity_log.action_type` 정확 일치. 허용 코드는 `specs/activity-action-types.spec.yaml` §2(OP-01). 빈 값 = 전체 유형. |
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
- **Path**: `id` — Long — 대상 `user_activity_log.id`
- **마스킹 (요건 `20260330-audit-evidence-activity-log-conservative`)**: 응답의 **`action_detail`** 및 필요 시 **`ip_address`** 등 메타필드는 **호출자 역할**에 따라 **마스킹**된다. 비특권 사용자는 **인앱 복사 본문 전체**·민감 키 평문을 받지 않는다(목록에서 조회 가능한 행만 상세 허용 — 기존 MF-02·AC-S2 정신과 동일). 상세 필드 키·마스킹 규칙·복사 하위 구조 요약은 **`specs/activity-log-audit-evidence.spec.yaml`** §3.
- **Response (data)**: Map (활동 이력 한 건 상세). 일반적으로 목록 검색과 동일한 필드를 포함하며, **`action_detail`** 은 DB JSON을 파싱한 객체이되 **마스킹 뷰**일 수 있다.
  - **권한 그룹 계열 `action_type`**: `action_detail`에 **`permissionGroupAuditV1`** 가 있으면(요건 `20260330-permission-group-activity-detail-audit`; 배정/해제 **`before`/`after`** 의무는 `20260407-permission-group-assign-unassign-audit-before-after`) 감사 증빙은 해당 객체의 `schemaVersion`, `operation`, `before` / `after` (`PermissionGroupSnapshot`, `allowedScreens` = `AllowedScreenItem[]`; **assign/unassign** 시 §3.5 시맨틱 및 **`allowedScreensTruncated`** §3.2), `targetUserId`(배정/해제), 선택적 **`changeReason`**(잠정, 최대 500자)로 해석한다. 전체 규격·예시·denylist는 **`specs/activity-permission-group-audit.spec.yaml`**.
  - **`USER_CREATE` / `USER_DELETE`**: 평탄(중첩 객체 없이) **`action_detail`** 권장 키: **`changeReason`**(해당 API에서 필수일 때 영속), **`targetUserId`**(number), **`employeeNumber`**(string, 선택), **`username`**(string, 선택), **`USER_CREATE`**에 **`registrationSource`**: **`EXTERNAL_PROVISIONING`** \| **`USER_MANAGEMENT_V2_DIRECT`**. v2 직접 등록이면 추가로 **`name`**, **`departmentId`**(부서 코드), **`rank`**, **`permissionGroupId`** 등 비민감 식별자만(§3 **user_admin**). §3 denylist 동일 적용.
  - **부서 v2 (`DEPARTMENT_CREATE_ROOT` 등)**: 평탄 **`action_detail`** — **`changeReason`**, **`departmentCode`**, **`parentDepartmentCode`**(null 허용), 선택 **`name`**, **`sortOrder`**, 수정·삭제 시 **`before`/`after`**(허용 필드만). 상세 키 표: `specs/activity-action-types.spec.yaml` §3 **department_admin**.
  - **인앱 복사·삭제 스냅샷·변경 before/after** 고수준: `action_type` **`IN_APP_COPY`** 등(코드표 `specs/activity-action-types.spec.yaml` §2.7) 및 **`action_detail`** 내 `copyPayload` / `deleteSnapshot` / `before`·`after` — **`specs/activity-log-audit-evidence.spec.yaml`** §3, §4.
  - **DOC-CODE-SYNC**: 구현체는 저장·응답 형태를 위 스펙에 맞춘다; 차이가 있으면 코드와 동시에 계약을 갱신한다(`docs/workflow/DOC-CODE-SYNC.md`).

### 8.2.1 활동 이력 민감 본문 특권 공개 (접근 감사 필수)

- **POST** `/api/activity-log/{id}/privileged-reveal` (가칭 경로; 최종은 `specs/activity-log-audit-evidence.spec.yaml` §2와 동일해야 함)
- **목적**: 저장된 **전체 인앱 복사 본문** 등 `GET` 상세에서 마스킹된 민감 필드를 **평문으로** 반환. **성공 응답 전에** 서버는 **접근 감사 레코드**를 **append-only**로 기록해야 한다(요건 §2.1, TC-06).
- **권한**: **특권 역할**만(정확한 permission 모델은 PO/Security — `docs/contract.md` 부록 AAE-02). 부족 시 **403** `REVEAL_NOT_ALLOWED` 또는 `FUNCTION_NOT_ALLOWED`.
- **Request body** (JSON, 예):

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| revealKind | string | O | 예: **`COPY_BODY_FULL`** — 전체 복사 본문. 추가 값은 스펙 §2. |

- **Response (data)**: 대상 활동 로그 행의 민감 필드 포함 객체(예: 전체 복사 텍스트 및 메타). 정확한 키는 스펙 §2.
- **에러**: **401** 비인증; **403** 특권 없음 / 대상 행이 caller scope 밖; **404** 없음; **400** 알 수 없는 `revealKind`.

### 8.2.2 (대안·비권장) 쿼리로 공개

- 제품이 **`GET /api/activity-log/{id}?reveal=copy_body_full`** 를 도입할 경우에도 **접근 감사·권한 검사는 POST와 동일**해야 하며, **GET에 부작용이 있는 설계는 REST 관점에서 비권장**이다. 구현 시 계약·스펙에 명시한다.

### 8.2.3 활동 로그 접근 감사 목록 조회

- **GET** `/api/activity-log/access-audit` (가칭; 캐논은 `specs/activity-log-audit-evidence.spec.yaml` §4)
- **목적**: **누가** 언제 **어떤 활동 로그 행**에 대해 민감 상세·전체 복사 본문 등을 열람했는지 목록(요건 Screen 3 `activity-log-access-audit`).
- **권한**: **감사/준법** 등 제한 역할(정확한 화면·scope는 PO — 구현 시 `permission-group-hierarchy`와 정렬). caller의 **감사 scope** 밖 행은 반환하지 않는다(TC-07).
- **Query** (예시): `startDate`, `endDate`, `accessorUserId`(numeric `app_user.id`), `targetActivityLogId`(optional), `page`, `pageSize`, `sortField`, `sortDirection` — 전체는 스펙 §4.
- **Response (data)**: 페이지 목록; 행당 예: 접근자 `userId`·표시명, `accessedAt`, **`targetActivityLogId`**, **`accessType`** (`DETAIL_VIEW` \| `COPY_BODY_FULL` 등). 저장소가 별도 테이블이면 조인 규칙은 스펙·DB 스키마가 권위.
- **에러**: **401**; **403** 조회 권한 없음 `ACCESS_AUDIT_FORBIDDEN` 또는 `FORBIDDEN`.

### 8.2.4 (선택·TODO) 활동 로그 반출 승인

- **제3자 감사 패키지 export** 및 **승인 큐**는 요건 Screen 4 및 `docs/contract.md` 부록 AAE-04가 **확정된 뒤** API를 정의한다. 기존 검색 이력 승인 패턴(§6.1.x)과 유사할 수 있으나 **본 문서에는 아직 구현 API를 고정하지 않는다.**

### 8.3 활동 로그 통계 (Activity Statistics)

**Base path**: `/api/statistics/activity`

- **GET** `/api/statistics/activity/daily`, **GET** `/api/statistics/activity/monthly`, **GET** `/api/statistics/activity/users/all`, **GET** `/api/statistics/activity/export`
- **Query params** (공통 필터): `startDate`, `endDate` (일별/export), `year`, `month` (월별), `logType`, `userId` (number, `app_user.id`), `department`, `ip`, `username` (또는 `name`, 사용자명 LIKE 필터). (req 20260310)
- **화면별 범위(scope)**: statistics 화면 scope 적용. scope='self'일 때 user/requester block은 숨기지 않고 visible locked self-context로 유지된다. `department`, `username`, `userId`는 auth/current-user payload의 `selfContext` 기준으로 표시되고 수정할 수 없으며, `userId`는 **numeric** **`app_user.id`**이다. API 처리에서는 userId, username/name, department, ip 등 사용자·부서 관련 파라미터를 무시하고 현재 사용자 데이터만 반환한다. scope=team/all일 때는 전달된 필터 적용.
- **Decrypt counters (`totalDecrypts`, per-day `totalDecrypts`, user `decryptCount`)** — req `docs/requirements/20260408-activity-statistics-decrypt-unique-rows-per-day.md`, `docs/contract.md`: Counts reflect **`user_activity_log`** with **`action_type = 'DECRYPT'`** only (actual decrypt API audit), **not** decrypt-approval counts or other tables. Values are **distinct logical log rows per calendar day** using the per–log-type dedup key from the requirement (not raw audit row counts). **Daily series / rolled-up `totalDecrypts`**: one per dedup key per day **across all users** in the filtered scope. **Per-user `decryptCount`**: per user, distinct keys per day, summed over the selected range.

### 8.3.1 공유 부서 필터 옵션 조회

- **GET** `/api/filter-options/departments`
- **의도 / 소비 화면**: 활동 이력(`activity-log`), 활동 통계(`statistics`), 검색 이력(`search-history`), **복호화 승인 관리(`pending-approvals`)** 의 부서 필터 콤보박스가 공통으로 사용하는 **editable department option source**(요건 `20260407-pending-approvals-history-search-readonly-requester`).
- **Query**:
  - `screen` (필수) — 호출 화면 컨텍스트. `activity-log` | `statistics` | `search-history` | `pending-approvals`
- **권한 / 접근 모델**:
  - 인증 필요. 비인증 시 401.
  - 요청한 `screen`에 대한 접근 권한이 있는 사용자(또는 `is_system_admin=true`)만 호출 가능. 백엔드는 `screen` 값에 해당하는 화면의 권한과 scope를 적용해 결과를 계산한다(`pending-approvals`는 `screenScopes['pending-approvals']` 기준; `search-history`와 동일 패턴).
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
  - `GET /api/filter-options/departments?screen=pending-approvals`
- **에러**:
  - 400 `INVALID_SCREEN_ID` — `screen`이 누락되었거나 지원하지 않는 값
  - 401 비인증
  - 403 `FORBIDDEN` — 요청한 `screen` 접근 권한 없음

### 8.3.2 구형 통계 부서 목록 엔드포인트 (전환 메모)

- `GET /api/statistics/departments`는 더 이상 권위 있는 계약이 아니다.
- 구현 전환 중 임시 호환이 필요하더라도, 새 개발과 문서 기준은 항상 `GET /api/filter-options/departments?screen=...`를 사용한다.

### 8.4 화면·메뉴 표시 라벨 (Screen display labels)

**Base path**: `/api/screen-display-labels`  
요건·스펙: `docs/requirements/20260406-menu-display-names-admin.md`, `docs/requirements/20260407-screen-menu-parent-order.md`, `specs/menu-display-labels.spec.yaml`.

- **GET** `/api/screen-display-labels`
  - **권한**: 세션 인증 필수. 비인증 **401** `UNAUTHORIZED`. **모든 인증 사용자** 호출 가능(표시·메뉴 트리 메타데이터; 화면 접근은 `allowedScreenIds`·라우팅과 별개).
  - **Response (data)**: `ScreenDisplayLabelItem[]` — 각 항목 `{ screenId, labelUser, labelAdmin?, parentGroupId?, sortOrder }` (camelCase; `sortOrder`는 0 이상 정수). DB에 없는 화면은 목록에 없음.
  - **`labelAdmin`**: **`isSystemAdmin === true`** 인 경우에만 포함될 수 있음. 일반 사용자 응답에서는 필드 생략(또는 의미상 null과 동일).
  - **`parentGroupId`**: 저장된 값이 있으면 포함. 없으면 생략·null — 클라이언트는 `MENU_TREE` 기본값 사용(요건 20260407).
  - **`sortOrder`**: 항상 숫자로 포함(0 이상). 레거시 DB NULL은 **0**으로 반환.
- **PUT** `/api/screen-display-labels` (캐논니컬 쓰기 메서드; PATCH 미지원)
  - **권한**: 세션 인증 + **`is_system_admin === true`** 만. 비관리자 **403** `FORBIDDEN`.
  - **Request body**: `{ "labels": [ { "screenId", "labelUser", "labelAdmin?", "parentGroupId?", "sortOrder?" }, ... ] }` — 빈 배열 허용(노옵). **`sortOrder`** 생략·null → **0**으로 저장. 그 외 선택 필드 생략·null → DB NULL(클라이언트 기본 트리).
  - **검증**: `screenId`는 서버 화이트리스트(`ScreenConstants` / 계약 화면 ID 목록과 정합). 불가 시 **400** `INVALID_SCREEN_ID`. `labelUser`·`labelAdmin` 각 최대 256자; `labelUser`는 트림 후 비어 있으면 안 됨. **`parentGroupId`** 가 있으면 허용 집합(`log-search`, `history`, `statistics`, `admin`)만 — 그 외 **400** `INVALID_INPUT`. **`sortOrder`** 가 있으면 0 이상 정수 — 음수 **400** `INVALID_INPUT`.
  - **성공**: **200**, `success: true`, `data`는 갱신 후 스냅샷(`GET`과 동일하게 관리자는 `labelAdmin` 포함).
  - **감사**: 성공 시 `@ActivityLog` — `action_type`: **`SCREEN_DISPLAY_LABELS_UPDATE`** (활동 이력 필터 옵션에 노출되려면 `ActivityActionType`·`specs/activity-action-types.spec.yaml` OP-01과 일치).
  - **DB**: `screen_display_label` UPSERT (`parent_group_id`, `sort_order` 포함), `updated_by` = 현재 사용자 `app_user.id`.

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
  - 페이로드 복호화 실패(잘못된 암호문·키 불일치 등): **400** `code: "DECRYPTION_FAILED"` — 사용자용 안전 한국어 메시지(스택·내부 예외 메시지 미포함)
  - 해당 guid+status 행 없음(imagelog): **404** `code: "LOG_ROW_NOT_FOUND"`
  - DB 접근 등 서버 오류: **500** `code: "INTERNAL_SERVER_ERROR"`

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
| DECRYPTION_FAILED | 단일 로우 페이로드 복호화 실패(암호문/키 등). **400** — 사용자 메시지에 내부 예외·스택 미포함 |
| LOG_ROW_NOT_FOUND | POST decrypt: imagelog에 해당 guid+status 행 없음 (**404**) |
| FORBIDDEN_NOT_APPROVER | 승인/반려·대기목록 API: **effective** `screenFunctions` 승인 없음 또는 `is_system_admin`만인 경우 등 (403). `docs/contract.md` 「복호화 승인 자격」 |
| NOT_APPROVER | 위와 동일 의미. 구현 시 하나로 통일 가능 |
| DEPARTMENT_NOT_FOUND | 부서 없음 (**404**). User Management v2 **`DELETE /api/user-management-v2/departments/{departmentId}`** 등. |
| DEPARTMENT_HAS_CHILDREN | 부서 삭제 불가: 하위 부서 존재 (**409**). v2 부서 DELETE. |
| DEPARTMENT_HAS_ACTIVE_USERS | 부서 삭제 불가: 해당 부서를 참조하는 사용자(활성) 존재 (**409**). v2 부서 DELETE. |
| DEPARTMENT_ORG_LINK_REFERENCES | 부서 삭제 불가: `department_org_link` 등 조직 연계 참조 존재 (**409**). v2 부서 DELETE. |
| ALREADY_APPROVER | 해당 부서에 이미 결재자로 등록됨 (400) |
| USER_NOT_IN_DEPARTMENT | 지정한 사용자가 해당 부서 소속이 아님. 부서별 결재자로 추가 불가 (400) |
| FORBIDDEN | 권한 없음(예: 부서/결재자 API는 관리자 전용) (403) |
| UNAUTHORIZED | 비인증·세션 없음 (401). 보호 API 공통. |
| INVALID_AUTH_MODE | (선택) 서버 인증 모드 설정 오류로 로그인 불가 (503/401 — 구현 정합). 요건 `20260407-external-dept-employee-ad-login`. |
| AUTH_CONFIGURATION_ERROR | (선택) `auth.login.mode`·`auth.ad.*` misconfiguration — 기동 실패 또는 로그인 거부 시 (docs/contract.md). |
| DIRECTORY_AUTH_FAILED | AD/LDAP 모드에서 디렉터리 자격 증명 거부(401). 사용자 열거 방지 메시지. |
| APP_USER_NOT_PROVISIONED | AD 인증은 성공했으나 `app_user` 매핑 없음(401). |
| INVALID_CREDENTIALS | **local** 로그인: `userId` 없음·해당 행 없음·비밀번호 불일치 등(401). |
| USER_ACCOUNT_DISABLED | **local** 로그인: `app_user` 행은 있으나 `deleted_at` 이 설정된 비활성(소프트 삭제) 계정(401). |
| EXTERNAL_IDENTITY_CONFLICT | 동일 외부 키로 이미 등록됨(409). `POST /api/provisioning/users/from-external-employee`. **data**에 `existingUsername`(선택), `existingAppUserId`(있으면) 포함 가능. |
| USER_EMPLOYEE_NUMBER_DUPLICATED | 활성 `app_user` 간 동일한 trim 후 비-null `employee_number` (**409**). `POST /api/user-management-v2/users/direct` — `specs/user-management-v2.spec.yaml` §4.4; **`POST /api/provisioning/users/from-external-employee`** (외부 프로비저닝, 동일 규칙·코드). |
| INVALID_INPUT | 부서코드/userId 등 입력값 비어 있음 또는 형식 오류 (400) |
| PERMISSION_GROUP_NOT_FOUND | 해당 ID의 권한 그룹 없음 (404) |
| PERMISSION_GROUP_HAS_USERS | 삭제 시 해당 그룹에 사용자 배정 있음 (400) |
| USER_ALREADY_IN_GROUP | 해당 사용자가 이미 그룹에 배정됨 (400) |
| INVALID_SCREEN_ID | 허용 목록에 없는 screen_id 포함, 공유 필터 옵션 API의 `screen` 파라미터 미지원, 또는 PUT `/api/screen-display-labels`의 알 수 없는 `screenId` (400) |
| INTERNAL_SERVER_ERROR | 서버 내부 오류 (500). 화면 표시 라벨 조회/저장 DB 오류 등 |
| INVALID_SCREEN_FUNCTION | 화면별 read/write/approve 조합이 허용되지 않음 (400). main read-only: main에 write=true 또는 approve=true; approve 미지원 화면에 approve=true; write 미지원 화면에 write=true. POST/PUT permission-groups 시 `specs/permission-group-hierarchy.spec.yaml` §1.1.1 검증. |
| USER_NOT_FOUND | 해당 사용자 없음 (404) |
| USER_DELETE_REFERENCED | 사용자 삭제 불가: FK·참조 데이터 존재 등 (통상 **409**; 구현이 **400**이면 api-definition §7.3과 함께 통일). req `20260407-user-management-consistency-delete-reason-activity-audit` TC-08. |
| SELF_DEMOTION_BLOCKED | 자기 자신의 역할 변경 시도 (400) |
| LAST_ADMIN_BLOCKED | 마지막 관리자 강등 시도 (400) |
| SYSTEM_ADMIN_IMMUTABLE | 대상이 시스템 관리자(수정·삭제 불가) (400) |
| LAST_SYSTEM_ADMIN_BLOCKED | 강등 시 시스템 관리자가 0명이 됨 (400) |
| FUNCTION_NOT_ALLOWED | 해당 기능(approve/write 등) 권한 없음. 403 반환 시 사용. 내부 구조·리소스 존재 여부 노출 금지 (403) |
| LOG_TYPE_NOT_ALLOWED | 요청한 logType에 해당하는 화면(pb-feplog/java-fw-imagelog) 접근 권한 없음. 로그 검색·상세·복호화 API에서 403 (req 20260318). **`POST /api/logs/db-refactored/pb-fep-log-search`** 에서 **`pb-fep-log-search` / PB FEP 접근 없음 시 동일 계열 (req 20260326). |
| REVEAL_NOT_ALLOWED | 활동 로그 **특권 공개**(예: 전체 복사 본문) 권한 없음 (403). 요건 `20260330-audit-evidence-activity-log-conservative`. |
| ACCESS_AUDIT_FORBIDDEN | **접근 감사** 목록·조회 API에 대한 권한 없음 (403). |
| POC_DISABLED | HR Sync PoC 플래그 off 또는 PoC API 비활성 (통상 **403**). `GET/POST /api/hr-sync/poc/*`. |
| NOT_FOUND | HR Sync PoC **스냅샷 인력** `GET .../snapshots/{snapshotId}/employees`: 복제 데이터에 해당 스냅샷이 없거나 0건 (**404**). 스펙 `hr-sync-poc.spec.yaml` §4.4. |
| SYNC_SOURCE_NOT_READY | 업스트림 적재·완료 신호·매니페스트가 준비되지 않았거나 검증 실패; 초기 PoC에서 placeholder와 병행 가능 (**403** 또는 **503** 등 구현 정합). |
| HR_SYNC_POC_PREVIEW_FAILED | HR Sync PoC **`POST .../preview`** 에서 `classificationCounts` 집계 실패(복제/DB 오류 등); 통상 **503**. 빈 스냅샷의 정상 **200**+전부 0과 구분. 스펙 `hr-sync-poc.spec.yaml` §4.2. |
| VALIDATION_ERROR | HR Sync PoC preview 요청 본문·식별자 검증 실패 (**400**). |
| POC_ACTION_NOT_PERSISTED | 오류 코드가 아님. **`POST /api/hr-sync/poc/user-mgmt/actions/migrate-preview`** 성공 **`data.messageCode`** (스텁 비영속 응답). |

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
- **활동 로그 감사 증빙(마스킹·특권 공개·접근 감사)**: `specs/activity-log-audit-evidence.spec.yaml`, 요건 `docs/requirements/20260330-audit-evidence-activity-log-conservative.md`.
- **HR Sync PoC (preview-only)**: `specs/hr-sync-poc.spec.yaml`, `docs/contract.md` § HR Sync PoC; 본 문서 §2b.

---

## 14. 권한 그룹 및 사용자 권한 계층 (관리자 전용)

**Base path (권한 그룹)**: `/api/permission-groups`  
**사용자 권한 계층**: `GET /api/departments/user-permission-hierarchy`

요건: `docs/requirements/20250227-user-permission-hierarchy-group.md`, `docs/requirements/20250227-permission-group-screen-menu-access.md`, `docs/requirements/20250303-activity-statistics-self-only-scope.md`, `docs/requirements/20260406-permission-group-invalid-screen-id-screen-display-labels.md`. 상세 스펙: `specs/permission-group-hierarchy.spec.yaml`.  
**권한 그룹 `allowedScreens.screenId` 허용 목록**에 **`screen-display-labels`**(화면 표시 이름)가 포함되며, 그룹 설정상 **읽기만** 허용(`specs/permission-group-hierarchy.spec.yaml` §1.1.1); **`PUT /api/screen-display-labels`** 본문 저장은 **`is_system_admin=true`**만(표시용 **GET**은 모든 인증 사용자 — 본 문서 **§8.4**, `docs/contract.md`).  
모든 API는 **관리자(is_system_admin=true)** 만 호출 가능. 그 외 403, `code: "FORBIDDEN"`.  
**화면 기반 접근**: 화면에 대응하는 API는 사용자가 해당 화면을 권한 그룹으로 허용받았거나 is_system_admin=true이어야 함. 그 외 403. 화면↔API 매핑: `specs/permission-group-hierarchy.spec.yaml` §4.3. **`/api/permission-groups`** (`ScreenAccessInterceptor`): **`user-management`** 또는 **`user-permission-hierarchy`** 또는 **`user-management-v2`** 중 하나가 `allowedScreenIds`에 있으면 통과(UM v2 전용 운영자 포함; 요건 **`20260409-user-management-v2-permission-groups-api-access-bugfix`**). POST/PUT/DELETE 등 변경 API는 기존처럼 `canAccessUserManagementView` + 쓰기 권한 규칙을 따름.  
**화면별 범위(scope)**: activity-log, statistics, search-history, pending-approvals, **`user-management-v2`** 화면은 권한 그룹에서 화면별 scope('self'|'team'|'all') 설정 가능(기본 `team`). **`user-management-v2`** 는 **`GET /api/users`**, **`GET /api/departments/user-permission-hierarchy`**, v2 읽기 API에 적용 — 요건 **`docs/requirements/20260409-user-management-v2-read-scope.md`**. scope='self' → 본인 데이터만(또는 본인 요청만)이며 applicable shared-pattern 화면에서는 user/requester block을 숨기지 않고 `department -> username -> userId`의 visible locked self-context를 표시한다. 이 표시값의 권위 소스는 auth/current-user payload의 `selfContext`이고, **`userId`**는 **numeric** **`app_user.id`**이다. search-history requester filter는 `scope=self`에서 무시된다. **`GET /api/search-history`·`GET /api/search-history/{id}`**는 **`listContext`**로 `search-history` vs `pending-approvals` 중 어느 `screenScopes`를 적용할지 선택한다(§6.1.2). scope='team' → 동일 부서 범위; scope='all' → 전체. is_system_admin=false일 때만 적용. 상세: `specs/permission-group-hierarchy.spec.yaml` §4.2, §4.3.

### 14.1 권한 그룹 목록 조회

- **GET** `/api/permission-groups`
- **Response (data)**: 배열. 각 항목: `id` (number), `code` (string), `name` (string), `description` (string | null), `sortOrder` (number, 선택), `allowedScreens` (배열: `AllowedScreenItem[]`). `AllowedScreenItem`: `{ screenId, scope?, read?, write?, approve?, decrypt? }`. scope는 activity-log, statistics, search-history, pending-approvals, **user-management-v2** 에 적용(기본 `team`); read/write/approve/decrypt는 화면별 명시적 체크박스다. main은 read-only이며 optional `decrypt`만 허용한다. 검증 실패 시 400 `INVALID_SCREEN_FUNCTION`. 상세: `specs/permission-group-hierarchy.spec.yaml` §1.1, §1.1.1.
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
- **Response (data)**: 배열. 각 항목: `userId` (number, `app_user.id`), `username`, `departmentCode`, `isSystemAdmin`, **`employeeNumber`** (선택, string) 등 사용자 요약 (role 제외, req 20250303)
- **에러**: 401, 403, 404

### 14.9 사용자 권한 계층 조회

- **GET** `/api/departments/user-permission-hierarchy`
- **조회 범위(scope)**: 호출자가 User Management v2 데이터 로드에 이 API를 쓰는 경우, 비시스템 관리자에게는 **`screenScopes['user-management-v2']`** 에 따라 부서 트리·사용자 목록이 **서버 측으로** 축소·마스킹된다(`specs/permission-group-hierarchy.spec.yaml` §4.3, `specs/user-management-v2.spec.yaml` §2.2). **`is_system_admin=true`** 는 전체 트리. 화면 접근은 **`user-management-v2`** 가 **`user-management`** / **`user-permission-hierarchy`** 와 동일 계열로 허용되어야 한다(요건 **`20260409-user-management-v2-read-scope`**).
- **Query**: `format` — "tree"(기본) | "flat"
- **Response (data)**:
  - **tree**: 루트 노드 배열. 각 노드: `code`, `parentCode`, `name`, `sortOrder`, `children` (재귀), `users` (배열). `users` 각 항목: **`userId`** (number, `app_user.id`, 사용자 ID), **`userName`** (string, 사용자명: `app_user.name`이 존재하고 비어 있지 않으면 그 값, 그렇지 않으면 `app_user.username`), **`employeeNumber`** (string, 선택, `app_user.employee_number`), `isSystemAdmin` (boolean, 시스템 관리자 여부), `position` (직책), `rank` (직급), `permissionGroups` (배열: `{ id, code, name }`). role 제외 (req 20250303).
  - **flat**: 부서 노드 배열에 `users` 포함(동일 구조), `children` 없음.
- **에러**: 401, 403

- 기존 부서 트리(code/parent_code)와 동일 구조; 부서별로 해당 department_code를 가진 app_user 목록과 각 사용자의 권한 그룹(permission_group) 목록을 붙여 반환.
