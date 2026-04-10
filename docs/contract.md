# 공통 계약 (Contract)

프론트엔드·백엔드·DB 작업 시 **이 문서와 참조 스펙을 기준**으로 한다. API·스키마 변경 시 여기에 맞추거나, 변경 시 이 문서/스펙을 먼저 갱신한다.

## 환경·포트 (단일 진실)

| 구분 | 값 | 설정 위치 |
|------|-----|-----------|
| 백엔드 API | http://localhost:9200/api | backend `spring` / env; browser base URL: frontend `REACT_APP_API_BASE_URL` at build, **or runtime** `window.__LOGMNG_RUNTIME_CONFIG__.apiBaseUrl` from `/runtime-config.js` (JDK static server: env **`LOGMNG_API_BASE_URL`** or `REACT_APP_API_BASE_URL`) |
| 프론트엔드 | http://localhost:3001 | frontend/.env PORT |
| DB — Primary (A, logmng / system) | 예: `localhost:5432`, DB `logmng` (배포마다 상이) | **런타임:** `spring.datasource.*` / `SPRING_DATASOURCE_URL`·`SPRING_DATASOURCE_USERNAME`·`SPRING_DATASOURCE_PASSWORD`. **설치(`setup.sh`):** `DB_HOST`, `DB_PORT`, `DB_A_NAME`(미설정 시 레거시 `DB_NAME`), `DB_SUPERUSER`, 앱 역할 `DB_USER`·`DB_PASSWORD` 등. PB URL이 비어 있거나 Primary와 같으면 PB FEP도 동일 풀(sys+pb `search_path`) |
| DB — PB FEP (선택, 물리 분리 가능) | 별도 DB·인스턴스 또는 A와 동일 | **런타임:** `app.datasource.pb.*` / `APP_DATASOURCE_PB_*` — `pb_send`·`pb_recv` 전용 풀. **설치:** `DB_PB_NAME`, `DB_PB_HOST`, `DB_PB_PORT`, `DB_PB_SUPERUSER`(기본은 Primary 슈퍼유저와 동일). `DB_PB_NAME`이 비거나 `DB_A_NAME`과 같으면 PB DDL은 A에 적용(레거시 단일 DB) |
| DB — ImageLog (B, 물리 분리 가능) | 별도 DB 이름 또는 A와 동일 | **런타임:** `app.datasource.imagelog.*` / `APP_DATASOURCE_IMAGELOG_*` — Java FW ImageLog(`imagelog`). **설치:** `DB_B_NAME`(기본 `DB_A_NAME`), `SCHEMA_IMAGELOG`; B는 Primary와 **동일 호스트·포트**에서 DB 이름만 분리하는 모델(`setup.sh`의 `psql_admin`). JDBC는 `jdbc:postgresql://<DB_HOST>:<DB_PORT>/<DB_B_NAME>` |

### DB 다중 데이터소스·스키마

요건: `docs/requirements/20260320-multi-datasource-schema-configuration.md`(스키마·풀·A/B 모델), `docs/requirements/20260410-install-deploy-three-db-noninteractive.md`(3물리 DB·비대화형 설치·로그 경로·운영 스크립트 정렬).

- **Primary (A)**: `spring.datasource.*`. 시스템 테이블은 항상 이 풀에서 접근한다.
- **PB FEP (선택, A′)**: `app.datasource.pb.url`(환경 변수 `APP_DATASOURCE_PB_URL`)이 **비어 있거나** Primary JDBC URL과 **같으면** PB도 Primary 풀을 재사용하고 Hikari `connectionInitSql`는 **sys + pb + public**(`search_path`)을 유지한다(레거시 단일 DB·동일 호스트 멀티 스키마). URL이 Primary와 **다르면** Primary 풀은 **sys + public**만 두고, `pb_send`/`pb_recv`는 전용 Hikari 풀 `LogMngHikariPool-pb`에서 **pb 스키마 + public**으로 접근한다.
- **ImageLog (B)**: ImageLog 전용 Hikari 풀. 아래 `app.datasource.imagelog.*` 및 스키마 속성으로 구성한다.
- **운영 3분할 예**: 인스턴스(또는 DB) 세 개 — (1) 시스템 Primary, (2) PB FEP, (3) ImageLog — 각각 `spring.datasource.url`, `APP_DATASOURCE_PB_URL`, `APP_DATASOURCE_IMAGELOG_URL`로 지정 가능.
- **단일 DB 개발**: `app.datasource.pb.url`·`app.datasource.imagelog.url`이 비어 있으면 해당 도메인은 **Primary 풀 재사용**(로컬 단일 DB). JDBC URL·비밀번호 등 민감값은 문서/저장소에 실제 값을 적지 말고 환경 변수로만 설정한다.

**스키마 이름 (backend `application.yml` / env)**

| 속성 | 환경 변수 | 기본값 |
|------|-----------|--------|
| `app.db.schema.sys` | `APP_DB_SCHEMA_SYS` | `public` |
| `app.db.schema.pb` | `APP_DB_SCHEMA_PB` | `public` |
| `app.db.schema.imagelog` | `APP_DB_SCHEMA_IMAGELOG` | `public` |

**Optional PB FEP JDBC (`app.datasource.pb.*` / env)**

| 속성 | 환경 변수 |
|------|-----------|
| `app.datasource.pb.url` | `APP_DATASOURCE_PB_URL` |
| `app.datasource.pb.username` | `APP_DATASOURCE_PB_USERNAME` |
| `app.datasource.pb.password` | `APP_DATASOURCE_PB_PASSWORD` |
| `app.datasource.pb.driver-class-name` | `APP_DATASOURCE_PB_DRIVER` |
| `app.datasource.pb.fail-fast` | `APP_DATASOURCE_PB_FAIL_FAST` |
| `app.datasource.pb.initialization-fail-timeout-ms` | `APP_DATASOURCE_PB_INIT_FAIL_TIMEOUT_MS` |

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

### DB 설치·부트스트랩 환경 변수 (`backend/src/main/resources/db/setup.sh`)

비대화형 설치는 **필수 값을 환경(또는 `.env`에서 `set -a` … `source`)으로 export**한 뒤 `setup.sh`를 호출한다. PostgreSQL 클라이언트 비밀번호는 표준 **`PGPASSWORD`** 또는 스크립트가 읽는 **`PGPASSWORD_SUPER`**(설정 시 내부에서 `PGPASSWORD`로 전달)로 공급한다. **슈퍼유저 OS 사용자**는 **`DB_SUPERUSER`**(기본 `postgres`).

**`SETUP_MODE`** (기본 `full`)

| 값 | 의미 |
|------|------|
| `full` | Primary A·ImageLog B·(split-PB 시) PB DB까지 스크립트가 담당하는 전체 단계 |
| `sys_only` | 시스템 쪽 DDL/마이그레이션 위주; PB FEP 전용 DDL은 생략(split-PB일 때). 초기 데이터는 기본 생략 — 필요 시 `SYS_ONLY_LOAD_INIT_DATA=1` |
| `pb_only` | PB 전용 DB만 프로비저닝·PB DDL/마이그레이션; **`DB_PB_NAME` 필수**. A/ImageLog 단계 없음 |

**Primary / ImageLog 대상 클러스터 (동일 `DB_HOST`·`DB_PORT`)**

| 변수 | 기본(스크립트) | 설명 |
|------|----------------|------|
| `DB_HOST` | `localhost` | |
| `DB_PORT` | `5432` | |
| `DB_NAME` | `logmng` | 레거시; `DB_A_NAME` 미지정 시 A 이름으로 사용 |
| `DB_A_NAME` | `$DB_NAME` | 시스템 스키마 및(non-split 시) PB DDL이 붙는 DB |
| `DB_B_NAME` | `$DB_A_NAME` | ImageLog DB; A와 다르면 **물리 DB 분리** |
| `DB_USER` / `DB_PASSWORD` | `logmng` / … | 애플리케이션 역할 |
| `DB_ETL_USER` / `DB_ETL_PASSWORD` | `logmng_etl` / … | ETL 역할(스크립트가 생성·그랜트) |
| `SCHEMA_SYS` / `SCHEMA_PB` / `SCHEMA_IMAGELOG` | `public` | `APP_DB_SCHEMA_*`와 맞출 것 |

**PB 전용 클러스터·DB (split-PB)**

| 변수 | 설명 |
|------|------|
| `DB_PB_NAME` | 비어 있거나 `DB_A_NAME`과 같으면 PB는 A에 적용 |
| `DB_PB_HOST` / `DB_PB_PORT` | 기본 `DB_HOST` / `DB_PORT` |
| `DB_PB_SUPERUSER` | 기본 `DB_SUPERUSER` |

**초기 데이터·폐쇄망 옵션**

| 변수 | 설명 |
|------|------|
| `INIT_DATA_FILE` | 기본 `init-data.sql` |
| `SKIP_INIT_DATA` | `1`이면 init-data 실행 생략(DDL·마이그레이션은 유지) |
| `SYS_ONLY_LOAD_INIT_DATA` | `sys_only`에서도 init-data 실행하려면 `1` |
| `CLOSED_NETWORK_MINIMAL` | `1`이면 일부 샘플·선택 마이그레이션 생략(스크립트 주석 참고) |

### 애플리케이션 로그 파일 (Spring Boot 2.7)

배포 시 로그 파일 경로는 **외부화 속성**으로 지정한다. Spring Boot 2.7 **relaxed binding**: 환경 변수 **`LOGGING_FILE_NAME`** → `logging.file.name`. **기본값(미설정):** `logs/application.log` — 저장소 `application.yml` 기본과 동일하여 로컬·기존 배포와 하위 호환.

### `scripts/install_linux.sh` 비대화형 경로

대화형 메뉴·`read`는 **기본**; **`INSTALL_NONINTERACTIVE=1`**(또는 `true`/`yes`)이면 메뉴 없이 **`backend/src/main/resources/db/setup.sh`** 만 실행한다. 저장소 루트 **`.env`**는 `INSTALL_ENV_FILE`로 경로를 바꿀 수 있으며, 스크립트 시작 시 해당 파일이 있으면 `set -a && source && set +a`로 로드한다. 누락·무효 시 **stderr에 변수 이름만** 한 줄씩 출력하고 비0 종료(TC-05); 비밀번호·`PGPASSWORD` 값은 출력하지 않는다.

| 변수 | 설명 |
|------|------|
| `INSTALL_NONINTERACTIVE` | `1` / `true` / `yes` — 비대화형 경로 |
| `INSTALL_ENV_FILE` | 기본 `$REPO_ROOT/.env` |
| `INSTALL_WRITE_APP_ENV` | `1`이면 setup 성공 후 JDBC export 파일 생성(기본 `backend/.env.logmng.generated`, 경로는 `INSTALL_APP_ENV_OUT`) |
| 필수(비대화형) | `SETUP_MODE`(full\|sys_only\|pb_only), `DB_HOST`, `DB_PORT`, `DB_USER`, `DB_PASSWORD`, `DB_NAME` 또는 `DB_A_NAME`; `pb_only`일 때 `DB_PB_NAME` |
| `setup.sh` 호출 시 | 위 **DB 설치·부트스트랩** 표의 변수·`SETUP_MODE`를 그대로 export |
| 생성 export 키 | `write_env_file`: `SPRING_DATASOURCE_*`, `APP_DB_SCHEMA_*`, `APP_DATASOURCE_PB_*`, `APP_DATASOURCE_IMAGELOG_*`, **`LOGGING_FILE_NAME`**(선택) |

**오프라인 번들** `install-offline.sh`: 동일하게 **`INSTALL_NONINTERACTIVE=1`**이면 `db` / `configure` / `all`에서 프롬프트를 생략한다. 번들 루트 **`.env`**를 먼저 로드한 뒤 **`var/logmng.env`**를 로드한다(둘 다 있으면 후자가 덮어씀). `all` 단계는 `INSTALL_RUN_DB`, `INSTALL_RUN_CONFIGURE`, `INSTALL_RUN_START`(기본 각 `1`), `INSTALL_DB_SKIP`으로 구성을 줄일 수 있다. `configure` 비대화형 필수: `SERVER_PORT`, `FRONTEND_PORT`, `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `APP_DB_SCHEMA_SYS`, `APP_DB_SCHEMA_PB`, `APP_DB_SCHEMA_IMAGELOG`, `CORS_ALLOWED_ORIGINS`, `ENCRYPTION_KEY`.

### 인증 모드·디렉터리 (요건 `docs/requirements/20260407-external-dept-employee-ad-login.md`)

운영자는 **`application.yml`** 및 프로필별 **`application-{profile}.yml`**(예: `application-dev.yml`, `application-prod.yml`)에서 **배포(또는 활성 Spring 프로필)당 하나의 인증 모드만** 선언한다. 코드 변경 없이 YAML/프로필 전환으로 **local** vs **ad**를 선택한다. **핫 리로드로 모드 전환**은 본 요건 범위 밖이다.

| 속성 | 설명 |
|------|------|
| **`auth.login.mode`** | **필수(제품 규칙)**. 허용 값: **`local`** \| **`ad`** 만. **런타임에 정확히 하나**의 구현 경로만 활성. **`local`**: 기본 로그인 식별자는 **`employeeNumber` (`app_user.employee_number`) + `password`** 이고, 레거시 호환으로 **`userId`(`app_user.id`, 숫자) + `password`** 를 제한적으로 허용한다(아래 "사용자 식별자 의미" 및 deprecated 정책). **`ad`**: 기업 디렉터리(LDAP/LDAPS 바인드 등 구현 세부는 백엔드)로 자격 증명 검증 후 **`app_user`** 매핑. |
| **`auth.login.allow-local-in-production`** | 선택. 기본 **`false`**. **`true`**일 때만 **프로덕션으로 분류되는 활성 프로필**에서 `auth.login.mode=local` 허용(의도적 로컬/브레이크글래스). **`false`**이고 프로덕션 프로필인데 `mode=local`이면 **fail-closed**(기동 실패 등 — 구현과 동일하게 유지). 개발 프로필에서는 제품 기본 완화 가능. |

**로컬 프로비저닝·온보딩 (비밀번호 정책 요약)** — 요건 `docs/requirements/20260408-my-page-local-password-and-profile.md`:

- **`auth.login.mode=local`** 인 배포에서 **테이블 기반으로 신규 생성되는 애플리케이션 사용자**(관리자 등록·User Management·v2 직접 등록 등 `password_hash`를 설정하는 모든 로컬 경로)의 **최초 로그인 평문 비밀번호는 제품 기본값 `user123` 하나로 통일**한다(운영 문서·온보딩 안내용; DB·로그에는 **항상 해시만** 저장).
- **자가 비밀번호 변경**: 인증된 사용자가 **자신의** `password_hash`를 바꾸는 API는 **`POST /api/auth/me/password`** (요청: **`currentPassword`**, **`newPassword`**, **`confirmNewPassword`**(필수, `newPassword`와 일치); 응답: 공통 **`ApiResponse`**) — 규범 스펙 **`specs/my-page-password.spec.yaml`**. **`auth.login.mode=ad`** 인 배포에서는 이 API **비허용**: **HTTP 403**, **`ApiResponse.code`**: **`PASSWORD_CHANGE_NOT_ALLOWED`** (디렉터리 관리 비밀번호; `app_user`에 엔드유저 AD 비밀번호 저장 없음).
- 성공 후 **세션 유지 vs 강제 재로그인**은 구현 선택(동일 릴리즈에서 `docs/api-definition.md`와 코드 정합).

**디렉터리(`ad` 모드)** — 중첩 키 **`auth.ad.*`** (값은 환경·비밀은 env/시크릿만; 저장소에 평문 금지):

| 속성 | 설명 |
|------|------|
| **`auth.ad.ldap-url`** | **필수(`mode=ad`)**. LDAP/LDAPS provider URL(예: `ldaps://...:636`). 프로덕션은 **LDAPS 또는 LDAP+StartTLS** 및 인증서 검증(요건 §2.1). JNDI **`Context.PROVIDER_URL`**. |
| **`auth.ad.domain`** | **필수(`mode=ad`)**. UPN DNS 접미사; 로그인 **`principal`**에 `@`가 없으면 바인드 주체는 `principal + "@" + domain`, 이미 UPN이면 그대로 사용. 환경변수 예: **`AUTH_AD_DOMAIN`**. |
| **`auth.ad.manager-dn`** | (선택·레거시) 현재 인증 경로에서 **미사용**(예전 Spring LDAP 매니저 바인드용). |
| **`auth.ad.manager-password`** | (선택·레거시) **미사용**. |
| **`auth.ad.user-search-base`** | (선택·레거시) **미사용**. |
| **`auth.ad.user-search-filter`** | (선택·레거시) **미사용**. |
| **`auth.ad.connect-timeout-ms`** / **`auth.ad.read-timeout-ms`** | (선택) JNDI LDAP **`com.sun.jndi.ldap.connect.timeout`** / **`com.sun.jndi.ldap.read.timeout`**(밀리초 문자열). |

**Misconfiguration / fail-closed**

- `auth.login.mode` 가 허용 값이 아니거나 누락(제품이 필수로 정한 경우) → **기동 실패** 또는 **모든 로그인 거부** 중 하나로 일관되게 동작해야 하며, **다른 모드로의 묵시 폴백 금지**.
- `mode=ad` 인데 **`auth.ad.*` 필수 항목 누락·빈 값** → 동일하게 fail-closed.
- 구체적 HTTP/기동 오류 코드는 **`specs/external-identity-auth.spec.yaml`** 및 `docs/api-definition.md` 와 맞춘다.

**사용자 식별자 의미 (normative)**

- **Technical id (`userId`)**: API 경로/쿼리/내부 조인용 **숫자 `app_user.id`**. 기존 경로 파라미터 `{userId}` 및 `search_history.user_id` 등은 이 의미를 유지한다.
- **Human-facing id (`employeeNumber`)**: 로그인 화면·self-context·관리자 화면에서 사람이 인지하는 "사용자 ID(사번)"는 **`employeeNumber` = `app_user.employee_number`** 이다.
- **`selfContext` 계약**: `selfContext`는 최소 `{ department, username, userId, employeeNumber }`를 제공한다. 여기서 `userId`는 technical id, `employeeNumber`는 human-facing id다. `employeeNumber`는 `string | null`.
- **Null 사번 정책(전환기)**: 레거시 데이터 호환을 위해 `employee_number` NULL은 당분간 허용한다. NULL인 경우에도 API는 technical id를 human-facing "사용자 ID"로 승격하지 않는다. UI/문구는 "사번 미등록"(또는 동등한 명시적 fallback)으로 처리한다.
- **로컬 로그인 요청(전환기)**: `POST /api/auth/login` (`auth.login.mode=local`)은 **`employeeNumber`(권장)** 또는 **deprecated `userId`(숫자)** 중 **정확히 하나**를 허용한다. 둘 다 보내거나 둘 다 누락하면 `400 INVALID_INPUT`.
- **Deprecated 안내**: local 로그인의 `userId` 요청 필드는 하위호환 목적의 **deprecated** 경로다. 클라이언트는 `employeeNumber`로 이행해야 하며, 제거 시점은 릴리즈 노트/스펙에서 별도 공지한다.

**세션·권한 계약**

- 로그인 성공 후 **세션 쿠키**, **`GET /api/auth/me`**, **`GET /api/auth/check`** 응답 형태( `allowedScreenIds`, `screenFunctions`, `selfContext` 등)는 기존 계약을 유지한다. 단, `selfContext`에는 human-facing 식별자 필드 **`employeeNumber`**를 포함한다. **AD 모드는 엔드 사용자 AD 비밀번호를 `app_user`·로그·세션에 저장하지 않는다**(요건 §2.1).
- **인증은 되었으나 유효 화면 권한이 없음**(시스템 관리자 아님 + `allowedScreenIds` 비어 있음): **보호 API**는 **401이 아니라 403** 계열로 거부하는 것이 계약상 권장(세션은 존재). **예외**: **`POST /api/auth/logout`**, **`GET /api/auth/check`**, **`GET /api/auth/me`**, **`POST /api/auth/me/password`**(로컬 모드에서만 실질 동작; AD 모드는 **403** `PASSWORD_CHANGE_NOT_ALLOWED`) — 프론트 모달·라우팅·마이페이지 온보딩 판단용 — 상세는 `specs/external-identity-auth.spec.yaml` §5, **`specs/my-page-password.spec.yaml`**. **비인증** → **401**.

**외부 조직 복제 테이블(요약)**

- 읽기 전용 복제 **`ext_department`**, **`ext_employee`**(또는 DBA가 확정한 동등 이름)는 애플리케이션 런타임 역할에 **SELECT-only** 등 스키마/그랜트로 제한(요건 §2 DB). **사용자 등록(프로비저닝)** API는 이 테이블을 검색·선택해 **`app_user`** 생성 및 외부 키 연동을 수행한다 — API는 아래 스펙.
- **`POST /api/provisioning/users/from-external-employee`**: **활성** `app_user`(`deleted_at IS NULL`)끼리 **trim 후 값이 있는(비-null) `employee_number`**가 서로 같으면 안 된다(User Management v2 직접 등록과 동일 비즈니스 규칙). 다른 활성 사용자가 이미 같은 trim된 사번을 쓰면 **HTTP 409**, `ApiResponse.code` **`USER_EMPLOYEE_NUMBER_DUPLICATED`**(`specs/user-management-v2.spec.yaml` 직접 등록과 **동일 코드**). 복제 사번이 공백·없어 저장값이 **NULL**이면 이 중복 검사는 적용하지 않는다(여러 활성 행이 NULL 허용).
- **운영·DBA**: 레거시로 **활성 행 간 사번 중복**이 남아 있을 수 있다. 향후 PostgreSQL **부분 UNIQUE** 인덱스(예: `employee_number`에 `WHERE deleted_at IS NULL AND employee_number IS NOT NULL`)를 두려면 **환경별 데이터 정리**가 선행될 수 있다. 위 프로비저닝 경로는 애플리케이션에서 이 규칙을 강제하며, DB 제약 도입만으로 과거 중복이 해소된다고 가정하지 않는다.

**대체 로그인 URL**

- 동일 SPA·동일 **API 베이스 URL**(`docs/contract.md` 환경 표). **경로만 다른 진입점**(리버스 프록시, `PUBLIC_URL`, 향후 라우터)은 **배포·정적 호스팅** 관심사이며, 별도 API 베이스 변경을 요구하지 않는다.

**API 규칙** 항목: 신규 엔드포인트·오류 코드는 **`specs/external-identity-auth.spec.yaml`** 우선.

**암·복호화 (`app.security` / `app.decryption` → env)**

| 속성 | 환경 변수 (둘 중 하나 또는 동시; 우선은 `application.yml` 플레이스홀더 순서) |
|------|-----------|
| `app.security.encryption-key` | `ENCRYPTION_KEY`, 또는 `APP_SECURITY_ENCRYPTION_KEY` |
| `app.decryption.enabled` | `DECRYPTION_ENABLED`, 또는 `APP_DECRYPTION_ENABLED` |
| `app.decryption.auto-decrypt-on-keyword-search` | `AUTO_DECRYPT_ON_KEYWORD_SEARCH`, 또는 `APP_DECRYPTION_AUTO_DECRYPT_ON_KEYWORD_SEARCH` |
| `app.decryption.failure-handling` (`fallback` \| `skip` \| `error`) | `FAILURE_HANDLING`, 또는 `APP_DECRYPTION_FAILURE_HANDLING` |

- **`failure-handling` 범위**: 레거시 단일 필드 복호화(`CryptoUtil.decrypt`, `ivHex:encryptedHex` 등)에는 위 값이 그대로 적용된다. **로그 페이로드**(`decryptLogPayload`, java_fw_imglog·pb_feplog 컬럼 및 JSON 내부 `[]` 래핑 값)는 실패 시 **ciphertext를 평문처럼 반환하지 않는다**(fallback이어도 예외 또는 안내 문구로 처리).

**로그 페이로드 암·복호화 형식 (서버)**

- **권위 키 문자열**: `app.security.encryption-key` — ProObject AESEncryptor 호환 시 **PBKDF2**(`PBKDF2WithHmacSHA1`, 고정 salt, 70000회, 256bit)로 AES 키를 유도하고, 암호문은 **Base64(SALT16+IV16+ciphertext)** (Apache Commons Codec Base64와 동일 계열). **java_fw_imglog** 컬럼 값은 선택적 접두 **`E002`** 가 붙을 수 있으며, 복호화 시 제거 후 디코딩한다. **pb_feplog** 는 동일 알고리즘이나 **`E002` 제거를 하지 않는다**(저장 형식은 Base64 본문만).
- **레거시(개발/샘플 DB)**: `ivHex:encryptedHex`(키는 UTF-8 바이트) 형식은 복호화 시 **ProObject 호환 시도 후 실패하면** 서버가 자동으로 시도한다.

**Static UI browser→API base (no rebuild)** — env `LOGMNG_API_BASE_URL` (preferred) or `REACT_APP_API_BASE_URL` on the JDK static-server process; served as `/runtime-config.js`. See `scripts/offline-bundle/README-OFFLINE.md` § API URL.

**Air-gap bundle**: `scripts/package-airgap-bin.sh` (default `AIRGAP_BIN_ROOT` = repo `bin/`) fills that directory with backend fat JAR, static UI (`www/`), and JDK-only static server JAR; see `bin/README.md`. **Full offline server package** (DB scripts + installer): `scripts/release-build.sh` (default, no args) or `scripts/build-offline-bundle.sh` builds the same binaries **into** `dist/logmng-offline-*/bin/` (no copy to repo `bin/`) → `dist/logmng-offline-*.tar.gz`; optionally run `scripts/download-psql-for-bundle.sh` first to bundle PostgreSQL **16** `psql` client `.deb` files (PGDG bookworm amd64; see script header). On the target host extract and run `./install-offline.sh all` (see `scripts/offline-bundle/README-OFFLINE.md`).

## API 규격

- **정의 위치**: `docs/api-definition.md`(현재 구현 API 목록·요청/응답), `specs/*.spec.yaml` 또는 기능별 요건 문서의 API 섹션. 새 API/변경 시 해당 스펙을 먼저 작성·수정한다. **외부 조직 복제·AD 로그인·관리자 프리프로비저닝 (요건 `20260407-external-dept-employee-ad-login`)**: `auth.login.mode`, `POST /api/auth/login` 모드별 요청 형식, **`/api/provisioning/*`** — **`specs/external-identity-auth.spec.yaml`**. **마이페이지·로컬 전용 자가 비밀번호 변경 (요건 `20260408-my-page-local-password-and-profile`)**: **`POST /api/auth/me/password`** — **`specs/my-page-password.spec.yaml`** (AD 모드 **403** `PASSWORD_CHANGE_NOT_ALLOWED`). **활동 이력 `action_type` 코드·선택 `GET /api/activity-log/action-types`**: `specs/activity-action-types.spec.yaml`(User Management v2 부서/직접등록 감사·`USER_CREATE` 구분자는 요건 **`20260408-user-management-v2-activity-audit-detail-in-activity-log`**, 스펙 §2.8·§3 `department_admin` / `user_admin`). **활동 로그 보수적 감사 증빙(마스킹·특권 공개·접근 감사·선택 반출 승인)** — 요건 `docs/requirements/20260330-audit-evidence-activity-log-conservative.md`: 상세 API·접근 감사 조회·`action_detail` 하위 형태 요약은 **`specs/activity-log-audit-evidence.spec.yaml`**; 코드표 확장은 **`specs/activity-action-types.spec.yaml`** §2.7. **메뉴·화면 표시 라벨·사이드바 상위 그룹·정렬 `GET/PUT /api/screen-display-labels` (요건 `20260406-menu-display-names-admin`, `20260407-screen-menu-parent-order`)**: `specs/menu-display-labels.spec.yaml`. **User Management v2 수동 부서 트리/직접 사용자 등록/quick-entry (요건 `20260408-user-management-v2-manual-department-tree-and-quick-user-entry`)**: `POST /api/user-management-v2/departments/root`, `POST /api/user-management-v2/departments/{parentDepartmentId}/children`, `DELETE /api/user-management-v2/departments/{departmentId}` (요청 본문 JSON **`changeReason`** 필수; 충돌·참조 거부 시 `DEPARTMENT_HAS_CHILDREN`, `DEPARTMENT_HAS_ACTIVE_USERS`, `DEPARTMENT_ORG_LINK_REFERENCES`, 없음 시 `DEPARTMENT_NOT_FOUND` — **`specs/user-management-v2.spec.yaml`** §4.3), `POST /api/user-management-v2/users/direct`, `GET /api/user-management-v2/quick-entry/options` — **`specs/user-management-v2.spec.yaml`**. **조회 범위(scope)** — 요건 **`docs/requirements/20260409-user-management-v2-read-scope.md`**: 권한 그룹 **`user-management-v2`** 행에 **`scope`** `self`|`team`|`all`(기본 **`team`**, 생략/NULL 동일); **`GET /api/users`**, **`GET /api/departments/user-permission-hierarchy`**, v2 **GET** 등 읽기 경로에 서버 측 적용; 돌연변이는 유효 조회 범위 밖 대상 금지(`403` `FUNCTION_NOT_ALLOWED` 등 — 스펙 §2.3). **명명 호환성 주의**: v2 스펙의 `departmentId`/`parentDepartmentId`는 레거시 필드명이며, 실제 의미/타입은 각각 부서 코드 문자열(`departmentCode`/`parentDepartmentCode`)이다. **HR Sync PoC (preview-only, zero-impact)** (요건 `20260408-external-hr-user-sync-security-db-design` §2.6, 스냅샷·인력 목록 확장 `20260408-hr-sync-poc-snapshot-list-and-sample-data`, PoC UM v2 클론 `20260408-poc-user-management-v2-isolated-clone`): **`GET /api/hr-sync/poc/config`**, **`POST /api/hr-sync/poc/preview`**, **`GET /api/hr-sync/poc/snapshots`**, **`GET /api/hr-sync/poc/snapshots/{snapshotId}/employees`**, **`GET /api/hr-sync/poc/user-mgmt/replica-departments/tree`**, **`GET /api/hr-sync/poc/user-mgmt/replica-users`**, **`POST /api/hr-sync/poc/user-mgmt/actions/migrate-preview`** (stub — **`app_user` 미변경**) — **`specs/hr-sync-poc.spec.yaml`**; 본문 계약은 아래 § HR Sync PoC.
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
- **API 정의서**: [docs/api-definition.md](api-definition.md) — 인증, 헬스, 로그 타입, DB 로그 검색/상세/복호화, 검색 추천, 검색 이력(승인 대기·승인·반려 포함), 사용자 관리(결재자 지정, PUT /api/users/{userId} 410 Gone, **`DELETE /api/users/{userId}`** JSON 본문 **`changeReason` 필수** — §7.3; path `userId`는 numeric `app_user.id`), 활동 이력, DB 테스트 등 구현된 API 전부. 복호화 결재자 관련 API는 api-definition §6.1.5·6.1.6·6.1.7 및 §7 참고. **부서별 결재자·부서 멤버·팀장 지정**: api-definition §12. **권한 그룹·사용자 권한 계층**: api-definition §14, 스펙 `specs/permission-group-hierarchy.spec.yaml`. 권한 그룹 CRUD 및 그룹별 사용자 할당/해제는 **사용자 권한 계층** 화면에서만 제공하며, 별도의 "권한 그룹 관리" 메뉴/화면은 두지 않는다. **PB FEP 와이어프레임 전용 검색**: `POST /api/logs/db-refactored/pb-fep-log-search` — api-definition §5.1.1, 스펙 `specs/log-db-pb-fep-log-search.spec.yaml`.
- **공유 부서 필터 옵션 계약**: 활동 이력(`activity-log`), 활동 통계(`statistics`), 검색 이력(`search-history`), **복호화 승인 관리(`pending-approvals`)** 의 부서 콤보박스는 공유 엔드포인트 `GET /api/filter-options/departments?screen={screenId}`를 사용한다. 이 엔드포인트는 **편집 가능한 부서 옵션 목록**의 권위 소스이며, `screen`은 `activity-log | statistics | search-history | pending-approvals` 중 하나다. 백엔드는 해당 화면의 접근 권한과 scope를 기준으로 옵션을 계산한다(요건 `20260407-pending-approvals-history-search-readonly-requester`: `pending-approvals`는 검색 이력과 동일한 scope·옵션 패턴을 따르되 `screenScopes['pending-approvals']` 기준). 응답은 프론트가 바로 `<select>`에 넣는 `string[]`이며 `"전체"` 옵션은 클라이언트가 로컬로 추가한다. 확인된 규칙: `scope=team`이면 **현재 사용자의 자기 부서만** 옵션에 포함되어야 하며, 다른 부서는 노출하지 않는다. `scope=self`에서는 이 엔드포인트를 잠긴 self-context 표시값의 권위 소스로 간주하지 않는다. self 화면의 고정 표시값(`department`, `username`, `userId`)은 `GET /api/auth/me` 등 auth/current-user payload를 기준으로 표시해야 하며, `userId`는 numeric `app_user.id`이다. 이 계약은 관리자/관리화면용 `GET /api/departments`와 분리된다. 기존 `GET /api/statistics/departments`는 새 개발의 기준이 아니며, 구현 전환 중에도 새 공유 API는 editable options source로만 간주한다. 상세 요청/응답/소비 화면은 `docs/api-definition.md`의 공유 필터 옵션 섹션 및 `specs/permission-group-hierarchy.spec.yaml` §4.3을 따른다. **`POST /api/activity-log/search`의 `department` 요청 값은 `department.name`과 정확 일치(위 공유 옵션 문자열과 동일)로 필터하며, `department_code` 문자열을 그대로 보내도 매칭되지 않는다**(`department.name` ≠ 코드인 경우).
- **활동 유형(`action_type`) 단일 기준 (요건 `20260330-activity-types-user-mgmt-permission-group`, OP-01)**: `user_activity_log.action_type`에 저장되는 값은 **대소문자 구분 UPPER_SNAKE_CASE** 문자열이며, 허용 코드의 **닫힌 집합**은 `specs/activity-action-types.spec.yaml` §2와 백엔드 `ActivityActionType`(또는 동등 상수)이 공유한다. `POST /api/activity-log/search`의 **`actionType`** 필터는 이 코드와 정확히 일치한다(빈 값 = 전체 유형). **활동 유형 필터 드롭다운** 옵션은 프론트엔드 하드코드 목록이 아니라 **권위 있는 목록**을 쓰는 것이 계약상 기대이며, 선택적 **`GET /api/activity-log/action-types`**(동등 경로: `GET /api/filter-options/activity-action-types`)가 `code` + `label` 배열을 반환한다. 인증·접근은 **activity-log** 화면 읽기 권한과 동일 계열이다. `action_detail` JSON은 카테고리별 비민감 식별자·메타데이터만 담으며, 비밀번호·토큰·복호화 본문은 저장하지 않는다. 상세 코드표·`action_detail` 범주·통계 KPI와의 관계(OP-02)는 `specs/activity-action-types.spec.yaml`을 따른다.
- **Activity statistics — decrypt KPIs** (req `docs/requirements/20260408-activity-statistics-decrypt-unique-rows-per-day.md`): Response fields **`totalDecrypts`** (rolled-up and per-day buckets), per-day **`totalDecrypts`** in time series, and per-user **`decryptCount`** are derived **only** from **`user_activity_log`** rows with **`action_type = 'DECRYPT'`** (decrypt **execution** audit). They **must not** count approval workflow rows (`DECRYPT_APPROVAL_*`, `search_history`, etc.). Semantics: **distinct logical log rows per calendar day** within the statistics timezone, using the **per–log-type dedup key** defined in that requirement (e.g. `java_fw_imglog`: `logType` + `guid` + normalized `status`). **Daily global aggregate** (whole filtered population): one count per dedup key per calendar day across all users in scope. **Per-user breakdown**: for each user, distinct keys per day, then **sum** over days in the range (same user, same row, same day, multiple decrypts → contributes **once** for that user-day). See `docs/api-definition.md` §8.3 and `specs/activity-action-types.spec.yaml` §5 (OP-02).
- **활동 로그 보수적 감사·마스킹·인앱 복사·접근 감사 (요건 `20260330-audit-evidence-activity-log-conservative`)**:
  - **목록/기본 상세**: `GET /api/activity-log/{id}`는 호출자 역할에 따라 **`action_detail` 및 HTTP·IP 등 메타데이터를 마스킹**한 뷰를 반환한다(비특권은 전체 복사 본문·민감 키 평문 없음). 동일 검색·scope 규칙으로 **목록에서 볼 수 있는 행만** 상세 조회 가능(기존 AC-S2/MF-02 정신 유지).
  - **특권 공개(민감 본문)**: 전체 인앱 복사 본문 등 저장된 민감 필드를 평문으로 돌려주는 동작은 **부작용(접근 감사 기록)** 이 있으므로 **`GET` 쿼리만으로는 권장하지 않는다.** 계약 권장: **`POST /api/activity-log/{id}/privileged-reveal`**(또는 동등 **서브 리소스**)에 본문 `{ "revealKind": "COPY_BODY_FULL" }` 등 — 성공 시 **반드시 접근 감사 레코드**를 남긴 뒤 응답. 구현이 쿼리 파라미터를 택할 경우에도 동일 보안 의미·감사 의무를 문서·코드에서 일치시킨다.
  - **접근 감사 목록**: 누가 언제 어떤 활동 로그 행의 민감 상세를 열람했는지 조회하는 API — **`GET /api/activity-log/access-audit`**(가칭; 정확한 경로·쿼리·응답 필드는 `specs/activity-log-audit-evidence.spec.yaml`). 저장소는 **별도 테이블** 또는 **`user_activity_log`에 전용 `action_type`** 중 하나(요건 §2 Solution approach); 스키마는 DBA/Security와 `activity-log-audit-evidence` 스펙에 정렬.
  - **`action_type` 확장**: 인앱 복사 **`IN_APP_COPY`**(가칭), 접근 감사 행(동일 로그 테이블 재사용 시) **`ACCESS_AUDIT_VIEW`** 등 — `specs/activity-action-types.spec.yaml` §2.7. 백엔드 enum·`VARCHAR(50)` 길이는 최장 코드에 맞춘다.
  - **`action_detail` 고수준 형태**(전체 JSON 스키마는 스펙 파일이 권위): 복사 페이로드 — `truncatedText` 또는 `text`+**`was_truncated`**, **`length`**(또는 동등); 삭제 스냅샷 — 허용 키만 포함하는 **`deleteSnapshot`**; 변경 — 허용 필드만 **`before` / `after`**. 필드 분류(NEVER_PLAINTEXT / ALLOWLIST_PLAINTEXT 등)는 요건 §2 Solution approach.
  - **제3자 반출·승인**: 활동 로그 **내보내기(export)** 는 기존 계약상 미구현; 승인·최소 범위 패턴은 검색 이력 승인 계열과 유사하게 설계할 수 있으나 **PO 확정 전 TODO** — `specs/activity-log-audit-evidence.spec.yaml` §5 및 본 문서 부록.
- **권한 그룹 감사 `action_detail` (요건 `20260330-permission-group-activity-detail-audit`, MF-01; 배정/해제 스냅샷 의무 **`docs/requirements/20260407-permission-group-assign-unassign-audit-before-after.md`**)**: `PERMISSION_GROUP_*`, `ASSIGN_USER_TO_PERMISSION_GROUP`, `UNASSIGN_USER_FROM_PERMISSION_GROUP` 등 권한 그룹 관련 `action_type`에 대해, 구현 목표 스키마는 **`specs/activity-permission-group-audit.spec.yaml`**의 **`permissionGroupAuditV1`** 객체이다. `before` / `after`는 `PermissionGroupCreateRequest`·`PermissionGroupUpdateRequest`·`AllowedScreenItem`과 정렬된 **`PermissionGroupSnapshot`**(중첩 `allowedScreens`)을 사용한다(OP-PG-01). **`ASSIGN_USER_TO_PERMISSION_GROUP`**에서는 이전 그룹 스냅샷(또는 사전 그룹 없음이면 **`null`**)과 배정 후 **새** 그룹 스냅샷이 각각 **`before`** / **`after`**에 **필수**로 채워진다. **`UNASSIGN_USER_FROM_PERMISSION_GROUP`**에서는 떠나는 그룹 스냅샷이 **`before`**, 제거 반영으로 **`after`**는 **`null`**이어야 한다(상세 의미·`allowedScreensTruncated`는 동일 스펙 §3.5·§3.2). **Denylist**(비밀번호·토큰·원시 요청 본문 등)는 부모 요건 `20260330-activity-types-user-mgmt-permission-group` §2.1 Security 및 해당 스펙 §6을 따른다.
- **`changeReason` (MF-03, OP-PG-02, 잠정)**: 계약상 잠정안은 **`permissionGroupAuditV1.changeReason`**으로 **영속**하되 **최대 500자**로 절단한다. 제품이 **v1에서 생략**으로 바꾸면 계약·스펙을 먼저 수정한 뒤 구현한다.
- **DOC-CODE-SYNC (본 기능)**: Step 4 구현 시 백엔드·프론트는 저장/표시되는 `action_detail`이 **`activity-permission-group-audit.spec.yaml`**과 일치하도록 맞춘다. 의도적 차이가 있으면 같은 변경에서 계약·스펙을 갱신한다(`docs/workflow/DOC-CODE-SYNC.md`). **활동 이력 내보내기(export)** 는 미구현; 구현 시 MF-05(열·마스킹·검색과 동일 scope)를 추가한다.
- **메뉴·화면 표시 라벨·사이드바 트리 (요건 `docs/requirements/20260406-menu-display-names-admin.md` id **`20260406-menu-display-names-admin`**, `docs/requirements/20260407-screen-menu-parent-order.md` id **`20260407-screen-menu-parent-order`**)**: 관리자 구성 가능한 **사용자 표시명**(`labelUser`), 선택적 **관리자 전용 메모**(`labelAdmin`), 선택적 **상위 메뉴 그룹**(`parentGroupId`), 선택적 **동일 그룹 내 정렬**(`sortOrder`)를 `screen_id`(라우팅·권한·API용 기술 키)별로 저장·조회한다. **`screen_id` / `currentView` 값 자체는 변경하지 않는다.** 인증·세션은 기존과 동일(**세션 쿠키**, `POST /api/auth/login` 이후). 상세 요청/응답·오류 코드: **`specs/menu-display-labels.spec.yaml`**.
  - **`parentGroupId`** (string, **선택**, GET·PUT 항목에 동일): 사이드바 **최상위 그룹** 식별자. **닫힌 집합**만 허용 — `frontend/src/constants/menuTree.js`의 **`MENU_TREE`** 최상위 항목 **`id`** 와 동일: **`log-search`**, **`history`**, **`statistics`**, **`admin`**. 임의 문자열 불가. 알 수 없는 값·허용 집합 밖 값 → **400**, **`code`: `INVALID_INPUT`** (구현이 전용 코드를 쓰는 경우 스펙·`api-definition`에 명시). 생략 또는 **null** → 클라이언트는 해당 `screenId`에 대해 **`MENU_TREE` 기본 그룹**을 사용한다.
  - **`sortOrder`** (정수, **선택**, GET·PUT 항목에 동일): **동일 `parentGroupId` 안에서** 형제 leaf 간 순서. **0 이상의 정수**; **값이 작을수록** 사이드바에서 **위쪽**. 동일 `sortOrder`면 **`screenId` 오름차순(사전식)** 으로 동점 처리. 음수·정수가 아닌 표현 → **400** `INVALID_INPUT`. 생략 또는 **null** → 클라이언트는 **`MENU_TREE` 기본 순서**를 사용한다.
  - **GET** **`/api/screen-display-labels`** — **인증 필수**. 앱 셸(사이드바 등)을 쓰는 **모든 인증 사용자**가 호출할 수 있다(표시·트리 메타만 제공; 화면 접근 권한과 별개로, 클라이언트는 받은 값을 `allowedScreenIds` 등과 조합해 노출한다). 성공 시 공통 래퍼 `success: true`, **`data`** 에 설정 가능 화면에 대한 **`ScreenDisplayLabelItem[]`** (또는 `screenId` 키 맵; 둘 중 하나로 구현 통일, 스펙 예시 참고). 항목: **`screenId`**, **`labelUser`**, **`labelAdmin?`**, **`parentGroupId?`**, **`sortOrder?`**. 저장된 값이 없는 필드·화면은 목록에서 생략하거나 null — 구현은 스펙 §3과 일치시킨다. **401** — 비인증(세션 없음·만료).
  - **PUT** 또는 **PATCH** **`/api/screen-display-labels`** — **시스템 관리자만** (`app_user.is_system_admin = true`). 한 요청에 **하나 이상** 화면 항목 갱신. **`screenId` 는 서버 화이트리스트**만 허용: 본 계약 **화면 ID 목록** 및 프론트 `ALLOWED_SCREEN_IDS` / 주문 가능 화면 집합과 정합. 허용 목록에 없는 `screenId` → **400**, **`code`: `INVALID_SCREEN_ID`**. `labelUser` / `labelAdmin`은 **평문**, 길이 상한·금지 규칙 위반 → **400** `INVALID_INPUT`. **`parentGroupId` / `sortOrder` 검증 실패** → **400** `INVALID_INPUT`. 비관리자 → **403**, **`code`: `FORBIDDEN`**. HTML 저장·렌더 시 XSS 방지는 클라이언트 기본 이스케이프·서버 검증 병행(요건 §2.1).
  - **프론트 병합·폴백**: 기본 라벨·그룹·순서는 `menuTree.js`(`MENU_TREE`)·`LOG_TYPE_BY_VIEW` 등 **기존 하드코드**를 유지하고, GET 응답으로 **`screenId` 단위 병합(override)**. `parentGroupId`·`sortOrder`가 응답에 없거나 null이면 **해당 화면은 `MENU_TREE` 기본**을 쓴다. GET 실패·타임아웃 시 **기본값만** 사용하고 로그인·라우팅은 유지한다(요건 §2).

## HR Sync PoC (preview-only)

**요건**: `docs/requirements/20260408-external-hr-user-sync-security-db-design.md` (§2.6 PoC mode, 플래그 `HR_SYNC_POC_*`), 스냅샷·샘플 복제 데이터·인력 조회 확장 `docs/requirements/20260408-hr-sync-poc-snapshot-list-and-sample-data.md`, 미리보기 가시성·스냅샷 범위 `docs/requirements/20260408-hr-sync-poc-run-preview-visible-results.md`, 격리 PoC UM v2 클론 `docs/requirements/20260408-poc-user-management-v2-isolated-clone.md`.

**목적**: 운영 사용자·권한·Tree **변경 없이**(read-only / zero-impact) HR 스냅샷 대비 **미리보기**만 수행한다. `POST .../preview`는 **`app_user`·권한·프로덕션 권한 소스에 대한 쓰기를 하지 않는다.**

**베이스 경로**: `/api/hr-sync/poc`

**구성 플래그 (예시 이름; 실제 키는 백엔드 설정·환경 변수)**

| 플래그 | 기본(권장 PoC) | 설명 |
|--------|----------------|------|
| `HR_SYNC_POC_ENABLED` | off (`false`) | off면 PoC API·UI 진입 없음; 구현은 **403 `POC_DISABLED`** 또는 라우트 미노출로 일치시킨다. |
| `HR_SYNC_POC_DEFAULT_MODE` | `PREVIEW_ONLY` | 기본 실행 모드; apply/write 경로는 별도 게이트 없이 열리지 않는다. |
| `HR_SYNC_POC_APPLY_ENABLED` | off (`false`) | **Apply(변경 적용)** 허용 여부; PoC에서 기본은 비활성. |

**`GET /api/hr-sync/poc/config`**

- **Response `data`**: `{ "pocEnabled": boolean, "defaultMode": string, "applyEnabled": boolean }`
- **비밀·시그니처·업스트림 자격 증명 미포함** (노출 금지).
- `defaultMode`: 제품 기본은 **`PREVIEW_ONLY`** 문자열.

**`POST /api/hr-sync/poc/preview`**

- **Request body** (JSON): `{ "snapshotId"?: string, "ingestRunId"?: string }` — 둘 다 선택; 업스트림 연동 후 검증 규칙은 구현·스펙 §4와 DOC-CODE-SYNC로 정렬.
- **`classificationCounts` 범위 (스펙 §4.2·DOC-CODE-SYNC)**:
  - 요청에 **비어 있지 않은 `snapshotId`가 있으면**, 집계는 복제 **`ext_employee` 중 해당 `snapshot_id` 행만** 대상으로 한다.
  - **`snapshotId`가 없음**(생략·null·제품이 blank를 absent로 처리하는 경우 포함)이면 **PoC 전역 stub**: 복제 **`ext_employee`에 대한 스냅샷 미제한** 집계(예: 전체 테이블 기준 stub)이며, 스냅샷 단위 의미가 아니다.
- **유효한 스냅샷에 행이 0건**: **200** + `classificationCounts` **전 키 0** — 정상적인 “빈 스냅샷”(미리보기는 **404**가 아님; 스냅샷 인력 §4.4와 구분).
- **집계 중 DB/복제 오류**: **503** + **`HR_SYNC_POC_PREVIEW_FAILED`** — **200**으로 전부 0을 내려 실패를 위장하지 않는다(스펙 §4.2).
- **Response `data`**:  
  `{ "previewId": string, "snapshotId": string, "classificationCounts": { "TRANSFER": number, "NEW_HIRE": number, "RESIGNED": number, "UNCHANGED": number, "PROFILE_UPDATE_NON_SECURITY": number, "CONFLICT": number, "ORPHAN": number }, "riskTier": string, "upstreamGateStatus": string, "messageCode": string }`
- **읽기 전용**: `app_user` / 앱 권한 테이블 / 프로덕션 Tree 권위에 대한 **INSERT·UPDATE·DELETE 없음**.

**`GET /api/hr-sync/poc/snapshots`**

- **목적**: PoC에서 선택 가능한 **스냅샷 id 목록**과 최소 메타데이터(선택 `label`, `employeeCount`, 선택 `maxImportedAt`)를 반환한다. **`ext_employee` 등 복제 테이블** 기준; 런타임 샘플 주입 없음.
- **Response `data`**: `{ "snapshots": [ { "snapshotId": string, "label": string | null, "employeeCount": number, "maxImportedAt": string | null }, ... ] }`
- **읽기 전용**; 비밀·매니페스트·원시 적재 본문 미포함.

**`GET /api/hr-sync/poc/snapshots/{snapshotId}/employees`**

- **목적**: 지정 **`snapshotId`**에 속하는 **인력(복제 직원) 행**을 페이지로 조회한다. 소스는 **`ext_employee`**(필요 시 `ext_department` 조인으로 표시명만).
- **Query**: `page`(기본 1), `size`(기본 20, **최대 100**; 초과 시 **400** `VALIDATION_ERROR` — 스펙 §4.4).
- **Response `data`**: `{ "snapshotId": string, "employees": [ { "displayName": string | null, "jobTitle": string | null, "departmentKey": string | null, "departmentName": string | null, "active": boolean, "employeeNumber": string | null (선택·PoC에서는 **마스킹 없이** `ext_employee.employee_number` 평문) } ], "pagination": { "currentPage": number, "totalPages": number, "totalCount": number } }`
- **데이터 최소화**: **email 미포함**; `employeeNumber`는 PoC 인력 목록에서 **전체 사번** 노출(복제 샘플 전제). 알 수 없거나 해당 스냅샷에 행이 없으면 **404** `NOT_FOUND`(스펙 확정; 빈 200 대체 시 DOC-CODE-SYNC).
- **읽기 전용**: `app_user` / 권한 / 프로덕션 Tree에 대한 쓰기 없음.

**보안·세션 (PoC User Management — 화면 id `user-management-v2-poc`)**

- 프리픽스 **`/api/hr-sync/poc/user-mgmt/**`** 는 **다른 PoC API와 동일하게** **`HR_SYNC_POC_ENABLED`** 가 꺼져 있으면 **403 `POC_DISABLED`** (또는 라우트 미노출) 계열로 거부한다.
- **화면 권한**: 호출자는 **`allowedScreenIds`에 `user-management-v2-poc`** 가 있거나(또는 시스템 관리자 규칙) **해당 PoC UM API** 에 접근할 수 있다. **프로덕션 **`user-management-v2`** 권한만으로는 대체 불가** — 별도 화면 id (`docs/requirements/20260408-poc-user-management-v2-isolated-clone.md`).
- 프론트는 **`REACT_APP_HR_SYNC_POC_UI`** 등으로 메뉴를 추가로 숨길 수 있으나, **API 단일 진실은 서버 플래그 + 화면 권한**이다.

**`GET /api/hr-sync/poc/user-mgmt/replica-departments/tree`**

- **목적**: **`ext_department`** 만으로 PoC UM용 **부서 트리**(중첩 `children`) 반환. 쿼리 **`sourceSystem`** 기본값 **`HR_SAMPLE`** (스펙 §4.5).
- **읽기 전용**: 프로덕션 Tree / **`app_user`** / 권한 테이블에 쓰기 없음.

**`GET /api/hr-sync/poc/user-mgmt/replica-users`**

- **목적**: **`ext_employee`** (+ 표시명용 **`ext_department`** 조인) 기반 **페이지 목록**. 선택 쿼리 **`snapshotId`**, **`departmentKey`**, **`sourceSystem`**(기본 `HR_SAMPLE`), **`page`/`size`** (size 최대 100 — 스펙 §4.6).
- **읽기 전용**: **`app_user`** 에 INSERT/UPDATE/DELETE 없음.

**`POST /api/hr-sync/poc/user-mgmt/actions/migrate-preview`**

- **목적**: 마이그레이션 테스트용 **스텁**. **HTTP 200**, **`data`**: `{ "persisted": false, "messageCode": "POC_ACTION_NOT_PERSISTED" }`.
- **규범**: 핸들러는 **`app_user`** 및 애플리케이션 권한·프로덕션 Tree 권위에 대해 **어떠한 영속 변경도 하지 않는다** (스펙 §4.7). 실제 사용자 반영을 암시하는 활동 감사 기록도 남기지 않는다.

**Apply(PoC)**

- 요건상 PoC 기본은 **preview-only**; **`HR_SYNC_POC_APPLY_ENABLED`가 false이거나 preview-only 게이트 미충족**이면 **변경 적용 API는 호출 불가**여야 한다.
- 계약: **(a)** 전용 apply 경로가 있다면 **403** + 제품 코드(예: `POC_DISABLED`/`FUNCTION_NOT_ALLOWED`)로 거부하거나, **(b)** **라우트 자체를 노출하지 않음** — 둘 다 허용. 스케줄·서비스 계정 우회 없음.

**오류 코드** (`ApiResponse.code`, HTTP는 구현과 정렬 — DOC-CODE-SYNC)

| code | 의미 |
|------|------|
| `POC_DISABLED` | PoC 기능 플래그 off 또는 PoC API 비활성. |
| `SYNC_SOURCE_NOT_READY` | 업스트림 적재·완료 신호·매니페스트 등이 아직 없거나 미검증(초기에는 **placeholder** 응답·고정 메시지 허용). |
| `HR_SYNC_POC_PREVIEW_FAILED` | 미리보기 **`classificationCounts` 집계 실패**(복제/DB 오류 등); 통상 **503**. 빈 스냅샷(정상 0건)과 구분. |
| `VALIDATION_ERROR` | 요청 본문·경로 `snapshotId`·페이지 크기 등 검증 실패. |
| `NOT_FOUND` | § 스냅샷 인력: 복제 데이터에 해당 **`snapshotId`** 가 없음(또는 0건 — 스펙 §4.4, **404**). |
| `FORBIDDEN` / `FUNCTION_NOT_ALLOWED` | PoC 화면·역할과 동일 계열의 접근 거부 시(기존 패턴). |

**DOC-CODE-SYNC**: 권위 YAML — **`specs/hr-sync-poc.spec.yaml`**. 경로·필드·코드 변경 시 동일 작업에서 `docs/api-definition.md` 및 본 절을 갱신한다 (`docs/workflow/DOC-CODE-SYNC.md`).

## DB 스키마

- **정의 위치**: `backend/src/main/resources/db/schema.sql` 및 필요 시 `specs/` 내 스키마 기술.
- **변경**: 스키마 변경 시 schema.sql(또는 마이그레이션)을 먼저 반영하고, 백엔드 코드·API 스펙을 그에 맞춘다.
- **권한 그룹 관련 테이블** (요건 20250227, 20250303, 20260306): `permission_group` (id, code, name, description, sort_order), `app_user_permission_group` (user_id = app_user.username, permission_group_id → permission_group.id), `permission_group_screen` (permission_group_id, screen_id, scope, read, write, approve, decrypt — read/write/approve/decrypt BOOLEAN NULL; decrypt는 main 전용, 복호화 요청 권한; scope='self'|'team'|'all', activity-log·statistics·search-history·pending-approvals·**user-management-v2**에 적용, 생략/NULL 시 기본값 'team'; NULL=derived). 상세: `specs/permission-group-hierarchy.spec.yaml` §2.1, schema.sql.
  - **`app_user_permission_group.user_id`**: **권위(authoritative) FK**는 **`app_user.username`**(VARCHAR)이다. 숫자 `app_user.id`를 문자열로 저장한 **레거시(legacy)** 행은 마이그레이션 **`migrate-app-user-permission-group-user-id-to-username-20260407.sql`**에서 username으로 정규화한다.
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

- **요건**: `docs/requirements/20250303-permission-group-delete-system-admin-protection.md`, `docs/requirements/20260407-user-management-consistency-delete-reason-activity-audit.md`
- **규칙**: `is_system_admin = true`인 사용자는 역할 변경·삭제 불가. 최소 1명의 시스템 관리자 유지. `PUT /api/users/{userId}` 시 `SYSTEM_ADMIN_IMMUTABLE` 또는 `LAST_SYSTEM_ADMIN_BLOCKED` 반환. **`DELETE /api/users/{userId}`** (요건 20260407): 동일 보호 규칙 적용; 요청 JSON에 **`changeReason`** 필수(권한 그룹 감사 `changeReason`과 동일 필드명·활동 로그 영속 상한 **500자** — `docs/api-definition.md` §7.3). 참조 무결성으로 삭제 불가 시 **`USER_DELETE_REFERENCED`** 등(§7.3·§11). Path `{userId}`는 numeric `app_user.id`이다. 상세: `docs/api-definition.md` §7.2–§7.3, §11.

## 화면 기반 접근 제어 (Screen-based access)

- **요건**: `docs/requirements/20250227-permission-group-screen-menu-access.md`, `docs/requirements/20250303-screen-function-availability.md`
- **화면 ID 목록**: main(폐지 예정), pb-feplog, pb-fep-log-search, java-fw-imagelog, search-history, activity-log, **activity-log-detail**(활동 로그 상세 — 모달·드로어 등 구현 선택), **activity-log-access-audit**(민감 상세·전체 복사 본문 열람 접근 감사; 요건 `20260330-audit-evidence-activity-log-conservative` §1.1 Screen 3), **audit-policy-activity-log**(선택 — 보존·반출 승인; PO 확정 시), statistics, pending-approvals, user-management, **user-management-v2**, **hr-sync-poc**, **user-management-v2-poc**(HR PoC 전용 UM v2 클론; API `/api/hr-sync/poc/user-mgmt/*` — **`user-management-v2`** 권한과 별도 부여), department-approvers, user-permission-hierarchy, permission-group-management, **permission-group-screen-matrix**(권한 그룹 화면–권한 매트릭스 v2; **`permission-group-management`와 동일한 `/api/permission-groups.*` 접근 OR 집합** — 요건 `20260410-screen-access-menu-api-consistency`, `specs/permission-group-hierarchy.spec.yaml` §4.3.1), **screen-display-labels**(화면 표시 이름 — 권한 그룹 `allowedScreens`에 포함 시 **읽기 전용**; 라벨 저장은 `PUT /api/screen-display-labels`가 **시스템 관리자 전용**, 요건 `20260406-permission-group-invalid-screen-id-screen-display-labels`). 로그 검색: pb-feplog(PB FEP Log), pb-fep-log-search(PB FEP 로그 검색, 동일 API logType `pb_feplog`, 별도 권한), java-fw-imagelog(Java FW Image Log). 상세 및 화면↔API 매핑: `specs/permission-group-hierarchy.spec.yaml` §4. 새 화면 ID는 권한·메뉴 배치 시 스펙·`ScreenConstants`와 함께 갱신한다.
- **규칙**: `is_system_admin = true` 사용자는 모든 화면 접근. 비관리자는 자신의 권한 그룹 중 하나라도 해당 화면을 허용해야 접근 가능. 화면에 대응하는 API 호출 시 사용자가 해당 화면을 갖지 않으면 403 반환. (req 20250303: role 제거, is_system_admin 사용)
- **화면별 범위(scope)**: activity-log, statistics, search-history, pending-approvals, **`user-management-v2`** 는 권한 그룹별 scope('self'|'team'|'all') 적용, 기본값 'team' (생략/NULL 시). **`user-management-v2`** 는 활동 로그·통계 등과 동일 API 값(**`self`**/**`team`**/**`all`**)을 쓰며, **`GET /api/users`**, **`GET /api/departments/user-permission-hierarchy`**, **`GET /api/permission-groups`**, **`GET /api/user-management-v2/quick-entry/options`** 등 읽기에 서버 측으로 적용된다(요건 **`docs/requirements/20260409-user-management-v2-read-scope.md`**, **`specs/user-management-v2.spec.yaml`**; **`GET /api/permission-groups`** 화면 게이트 정합: **`20260409-user-management-v2-permission-groups-api-access-bugfix`**). **`GET /api/permission-groups` 및 `/api/permission-groups/**` 인터셉터·컨트롤러**는 **`allowedScreenIds`에 `user-management` \| `user-permission-hierarchy` \| `user-management-v2` \| `permission-group-management` \| `permission-group-screen-matrix` 중 하나**이면 통과해야 하며, 변경 API는 동일 family의 **`screenFunctions.write`**와 정렬한다(요건 **`docs/requirements/20260410-screen-access-menu-api-consistency.md`**, **`specs/permission-group-hierarchy.spec.yaml` §4.3.1**). **`screenScopes['user-management-v2']`**는 세션에 **`user-management-v2`**가 있을 때만 위 공유 GET에 적용; **`permission-group-*`만** 있는 경우에는 UM v2 scope를 적용하지 않는다. 백엔드 **`ScreenAccessInterceptor`**(`^/api/permission-groups.*` 경로 규칙)와 **`AuthService.canAccessUserManagementView`** / **`hasWriteForManagementScreens`**는 위 다섯 화면 ID(UM 세 개 + permission-group 두 개) OR 집합을 사용한다(요건 **`20260410-screen-access-menu-api-consistency`**). is_system_admin=false일 때 scope='self' → 본인 데이터만(승인 대기: 본인 요청만); scope='team' → 동일 부서(department_code)만; scope='all' → 전체. **`is_system_admin=true`** 인 경우 User Management v2 **유효 조회 범위는 항상 전체(all)** 로 두고, 권한 그룹 `scope` 로 관리자를 제한하지 않는다. applicable shared-pattern 화면에서 effective `scope=self`는 user/requester block을 숨기는 의미가 아니라 **visible locked self-context**를 의미한다. 즉 `department -> username -> userId` 순서의 세 필드는 화면에 계속 보이되 현재 인증 사용자 값으로 고정되고 수정할 수 없다. 이때 잠긴 self-context 표시값의 권위 소스는 auth/current-user payload이며, **`userId`**는 **numeric** **`app_user.id`**(예: 20269999, 20260001)이다. 특히 `activity-log`에서 effective `scope=self`이면 `department`(빈 값, `all`, `ALL`, `전체` 등 전체 표현 포함), `username`, `userId`, `departmentCode`, `ipAddress` 및 동등한 사용자 범위 확장 입력은 조회 범위를 넓히지 못하며, 백엔드는 이를 무시하거나 현재 사용자 기준 값으로 안전하게 override하여 결과를 항상 현재 사용자 본인 로그로 고정해야 한다. `search-history`의 requester filter(`department`, `username`, `userId`)는 조회 범위를 넓히지 않는 narrowing-only 조건이며, `scope=self`에서는 무시되고 `scope=team/all`에서만 허용 집합 내부 추가 필터로 적용된다. 상세: `specs/permission-group-hierarchy.spec.yaml` §4.3, `docs/requirements/20250303-activity-statistics-self-only-scope.md`, `docs/requirements/20250304-team-scope-default-and-approval.md`, `docs/requirements/20260305-pending-approvals-scope-same-as-search-history.md`.
- **승인 범위**: 승인(approve) 가능 범위는 부서로 고정되어 있으며 권한 설정에서 변경할 수 없음. scope 드롭다운은 조회(목록) 범위만 적용됨. (`specs/permission-group-hierarchy.spec.yaml` §1.1 Scope values, `docs/workflow/CONSISTENCY-STANDARDS.md` §7.)
- **복호화 승인 자격 (검색 이력·승인 대기)** (요건 `docs/requirements/20260323-approver-eligibility-from-permission-group-only.md`, `docs/requirements/20260323-approver-eligibility-from-permission-group-only-PRODUCT-QA.md`):
  - **권위(source of truth)**: `app_user_permission_group` → `permission_group_screen`에서 **`search-history`** 및/또는 **`pending-approvals`** 에 대해 **`approve`(승인)** 가 허용된 경우에만, 해당 화면이 관여하는 **복호화 흐름의 승인·반려**를 수행할 수 있다. 레거시 `decrypt_approver` 테이블은 승인 **자격** 판단에 사용하지 않으며, 마이그레이션 후 스키마에서 제거한다.
  - **“관리자(admin)” 계정의 정의(본 항·승인 정책에 한함)**: **`app_user.is_system_admin = true`인 사용자만** “시스템 관리자 계정”으로 본다. **`ADMIN_EXT` 등 권한 그룹을 통해 넓은 관리 기능을 가진 사용자**이더라도 **`is_system_admin = false`이면** 본 승인 정책에서 관리자로 **분류하지 않는다**. 그들의 승인 가능 여부는 **권한 그룹에 지정된 화면별 `approve`와 아래 동일 부서 규칙만**으로 결정하며, 그룹 배치·운영은 운영자 관리에 맡긴다.
  - **공통 판별(구현 모델)**: 승인 자격은 **먼저** 권한 그룹 기준으로 “해당 화면에 승인 권한을 가진 사용자인지”를 판정한다. **`is_system_admin = true`인 경우에 한해**, 위에서 그룹으로 승인이 부여되어 있더라도 **검색 이력·승인 대기 관련 승인·반려는 불가**로 한다(해당 화면의 유효 승인 권한을 시스템 관리자 플래그가 **상쇄**). “admin이면 무조건 false” 단일 분기로 그룹 검사를 생략하지 않고, **그룹 기반 승인 보유 여부를 계산한 뒤 `is_system_admin`이면 제외**하는 방식이 계약상 권장 모델이다.
  - **요청 단위(동일 부서)**: 특정 요청에 대한 승인·반려는 **승인자의 `department_code`와 요청자의 `department_code`가 동일할 때만** 허용한다(상위 부서 체인 기준 승인은 사용하지 않음).
- **auth/current-user self-context 계약**: 세션 수립 후 **technical/canonical 사용자 식별자**는 항상 **numeric `app_user.id`**다. **`auth.login.mode=local`** 에서는 로그인 요청으로 **`employeeNumber`(string, 권장)** 또는 deprecated **`userId`(number)** 중 하나를 받는다. **`auth.login.mode=ad`** 일 때는 로그인 요청에 **`principal`(string) + `password`** 를 사용하고, 성공 후 동일하게 `app_user`를 해석한다(요건 `20260407-external-dept-employee-ad-login`). `POST /api/auth/login`의 `user` payload와 `GET /api/auth/me` 응답은 self-scoped 화면 고정 표시용 `selfContext`를 포함해야 한다. 최소 필드는 `department: string | null`, `username: string`, `userId: number`, `employeeNumber: string | null`이다. **`selfContext.userId`**는 technical id인 **numeric `app_user.id`**이고, **`selfContext.employeeNumber`**는 human-facing user identifier(사번)다. **`selfContext.username`**은 현재 사용자의 **표시 이름(display name, 사용자명)**이다: `app_user.name`이 존재하고 비어 있지 않으면 그 값을 사용하고, 그렇지 않으면 `app_user.username`을 사용한다. `scope=self` 화면은 이 값을 화면 표시의 권위 소스로 사용하고, 공유 filter-options 응답이나 사용자가 조작한 필터 입력을 권위 값으로 승격하면 안 된다.
- **screenFunctions** (req 20250303-screen-function-availability): 로그인·GET /api/auth/me 응답에 `screenFunctions: Record<screenId, { read, write?, approve?, decrypt? }>` 포함. 화면별 read/write/approve/decrypt 가능 여부. pb-feplog, java-fw-imagelog는 read + optional decrypt(복호화 요청 권한); decrypt는 권한관리에서 부여/해제 가능(req 20260318). **screenFunctions explicit storage**: permission_group_screen.read/write/approve/decrypt에 명시 저장 시 해당 값 사용; NULL이면 기존 derivation 규칙 적용. 상세: `specs/permission-group-hierarchy.spec.yaml` §4.4. **`search-history`·`pending-approvals`의 `approve`**: 위 **「복호화 승인 자격」** 을 따른다(권한 그룹의 `approve`를 먼저 반영한 뒤 **`is_system_admin`이면 해당 화면 approve를 유효하지 않음**으로 산출). `ADMIN_EXT` 등 비시스템관리자는 그룹 `approve`만으로 판단한다.
- **`pending-approvals`의 `read` vs `approve` (요건 `20260407-pending-approvals-history-search-readonly-requester`)**: **`read`** — 해당 화면(복호화 승인 관리) 접근, 목록·필터·검색 이력 조회(승인 상태 포함) **읽기 전용** UI. **`approve`** — 복호화 승인·반려 **액션만**(POST `/api/search-history/{id}/approve|reject` 및 비즈니스 규칙 `canApproveForRequester`). 요청자(승인 권한 없음)는 `read=true`, `approve=false`이면 목록·상세 조회는 허용 범위 내에서 가능하고 승인/반려 API는 403 계약 코드로 거부된다.
- **복호화 승인 관리 목록 API (동일 PR 계약)**: 역사·필터 가능한 목록은 **`GET /api/search-history`** (`docs/api-definition.md` §6.1.2)를 **단일** 목록 엔드포인트로 사용한다. **`search-history` 화면이 없고 `pending-approvals` 읽기만 있는 사용자**도 이 GET을 호출할 수 있도록 화면 접근(인터셉터) 규칙이 **`search-history` 또는 (`pending-approvals` + read)** 를 허용한다(구현은 Step 4). 대안으로 별도 경로만 두고 본문 규칙이 다른 경우 계약·코드가 함께 갱신된다.
- **`listContext` 스코프 해석 (옵션 (a), 요건 §2)**: `GET /api/search-history` 및 `GET /api/search-history/{id}`에 선택 쿼리 **`listContext`** = `search-history` \| `pending-approvals` 가 있다. 목록·상세의 **행 가시성·scope**는 `screenScopes`에서 **이 값에 해당하는 화면 키**를 사용한다(예: `listContext=pending-approvals` → `screenScopes['pending-approvals']` 및 `pending-approvals`용 필터·부서 옵션 규칙). **생략 시**: `allowedScreenIds`에 **`search-history`가 있으면** `search-history`로 간주(검색 이력 화면 하위 호환). **`search-history`는 없고 `pending-approvals`만 있으면** `pending-approvals`로 간주. **두 화면 모두 있으면** 생략 시 **`search-history`**(기본은 검색 이력 화면과 동일 동작). **복호화 승인 관리** UI는 반드시 **`listContext=pending-approvals`**를 붙여 호출해, 승인자가 두 화면을 모두 가진 경우에도 **예전 대기 목록과 동일한 `pending-approvals` scope 기준**으로 행 집합이 바뀌지 않도록 한다. 서버는 호출자가 해당 `listContext`를 쓸 수 있는지 검증한다(예: `listContext=search-history`인데 `search-history` 화면 없음 → 403).
- **API function-level enforcement**: decrypt(복호화 요청), approve(승인/반려), write(생성·수정·삭제) API는 해당 function 권한 검증. 권한 없으면 403, `code: "FUNCTION_NOT_ALLOWED"`. 로그 검색 API **logType=pb_feplog** 접근: 사용자 `allowedScreenIds`에 **pb-feplog 또는 pb-fep-log-search** 중 하나 이상 필요. **java_fw_imglog**는 **java-fw-imagelog** 화면 필요. 해당 화면 없으면 403 `LOG_TYPE_NOT_ALLOWED`. 복호화 허용 목록(GET /api/decrypt/allowed)에서 `screen=pb-fep-log-search` 요청 시 저장·조회는 **pb-feplog**와 동일 키로 통합. 복호화 API(POST /api/logs/decrypt/*)는 해당 logType의 screen 접근 + decrypt 권한( pb-fep 계열 화면 중 하나에 decrypt 있으면 허용 ) 필요. **복호화 승인 소스 (req 20260318, 20260320)**: 복호화 허용 여부는 **decryption-allowed store**에서만 결정되며, java_fw_imglog는 **(guid, status)** 일치가 필요하다. POST /api/logs/decrypt/java_fw_imglog body에 **`status` 필수**(누락·공백 시 400 `MISSING_STATUS`). POST /api/logs/decrypt 는 **searchHistoryId를 승인 판단에 사용하지 않으며**, searchHistoryId는 감사(audit)용으로만 선택 전달 가능하다. **GET /api/decrypt/allowed**: 쿼리 `screen`, 응답 `{ screen, validUntil, guids, allowedRows: [{ guid, status }] }` — `allowedRows`가 UI 복합 허용 판단의 권위이며 `guids`는 하위 호환용 distinct guid 목록. 상세: `docs/api-definition.md` §10. write API는 function과 scope 모두 검증; scope=self일 때 타인 데이터 수정 시 403. **검색 화면 복호화 UI (req 20260317-search-decrypt-permission-ui)**: 사용자에게 해당 로그 타입 화면의 decrypt 권한이 없으면 복호화 액션 비활성/숨김 및 "복호화 권한이 없습니다." 표시.
- **POST /api/logs/decrypt 오류 HTTP**: 페이로드 복호화 실패(잘못된 암호문·키 불일치 등)는 **400** `DECRYPTION_FAILED`(응답 본문은 안전한 사용자 메시지; 스택·내부 예외 메시지 미포함); imagelog에 해당 행 없음은 **404** `LOG_ROW_NOT_FOUND`; DB 등 서버 오류는 **500** `INTERNAL_SERVER_ERROR`. 상세: `docs/api-definition.md` §10.2.
- **승인 전용 권한 그룹 (approval-only)**: `allowedScreenIds`에 로그 검색 화면(pb-feplog, java-fw-imagelog) 없이 `pending-approvals`만 가진 그룹은 **그룹 이름과 무관하게** 동일 UX/API 규칙(리다이렉트, 메뉴 필터링, 로그 API 403, 액션 숨김) 적용. 상세: `specs/permission-group-hierarchy.spec.yaml` §5.

이 문서는 dev 워크스페이스 전용이다. 변경 시 docs/README.md 등과 맞춘다.

## 부록 — 활동 로그 감사 증빙(보수적) 미확정 사항 (PO / Backend / DBA)

요건: `docs/requirements/20260330-audit-evidence-activity-log-conservative.md`. Step 4 구현 전 계약에 남기는 **TODO** (스펙 세부는 `specs/activity-log-audit-evidence.spec.yaml`).

| ID | Topic | Stub |
|----|--------|------|
| AAE-01 | 접근 감사 **물리 스키마** | `user_activity_access_audit` 별도 테이블 vs `user_activity_log`에 `ACCESS_AUDIT_VIEW` 행 적재 — **DBA/Security 결정**. |
| AAE-02 | 특권 공개 **권한 플래그** | `screenFunctions['activity-log']` 확장 vs 별도 permission 키 — **PO/Security**. |
| AAE-03 | 인앱 복사 **서버 최대 길이** | 정수 상한·제품 기본값 — **PO** (요건 TC-02). |
| AAE-04 | 활동 로그 **패키지 반출 승인** API | 엔드포인트·승인 레코드·검색 이력 승인 재사용 여부 — **PO 확정 후** §5 스펙 채움. |
| AAE-05 | **물리 삭제** 거부 시 HTTP/에러 코드 | 스냅샷 누락 시 409 vs 400 — **Backend/PO**. |
