# PostgreSQL 16 데이터베이스 설정 가이드

## 📋 개요

이 가이드는 PostgreSQL 16을 사용하여 로그 관리 시스템의 데이터베이스를 설정하는 방법을 설명합니다.

## 기존 imagelog가 있는 DB에 암호화 샘플만 추가 (로컬/UI 복호화 테스트)

앱 기동 시 `imagelog`에 자동 시드를 넣지 않으며, **런타임 Java 코드는 `pb_send` / `pb_recv` / `imagelog`에 대한 INSERT·UPDATE·DELETE·TRUNCATE를 수행하지 않습니다.** 개발·테스트용 데이터는 `setup.sh`가 적용하는 `init-data-imagelog.sql`(또는 폐쇄망 최소 모드 생략 시 수동 적용), 필요 시 운영자가 직접 실행하는 **`psql -f …` 등 순수 SQL**로 적재합니다. 예: `backend/src/main/resources/db/init-data-imagelog.sql`(파일 상단 주석의 멱등 규칙 참고). 저장소의 `./scripts/append-imagelog-encrypted-samples.sh`는 더 이상 Java를 호출하지 않으며, 스크립트 안 주석만 참고용입니다. ImageLog 전용 DB를 쓰는 경우 `APP_DATASOURCE_IMAGELOG_URL`(및 USER/PASSWORD)을 `application.yml`과 맞춥니다.

### 폐쇄망 최소(`CLOSED_NETWORK_MINIMAL=1`) 이후 로컬 복호화 연습 시드

`INIT_DATA_FILE=init-data-closed-network-admin-only.sql` 등으로 imagelog 대량 샘플이 생략된 뒤에도, **로컬에서만** 앱 키(`ENCRYPTION_KEY` / dev 32바이트)로 **실제 복호화 가능한** ProObject 암호문 샘플을 넣으려면 `LOAD_LOCAL_DECRYPT_TEST_DATA=1`로 `setup.sh`를 실행합니다(기본값 unset/0). `init-data-local-decrypt-test-imagelog.sql`은 적용 전 `DELETE FROM imagelog WHERE guid LIKE 'LOCAL-DECRYPT-TST-IM-%'`로 기존 로컬 시드를 지우고 다시 넣습니다. 시드 내용 재생성: `backend/`에서 `LocalDecryptSampleSeedGenerator`(테스트 소스)로 두 SQL 파일을 덮어씁니다. 적용 파일: `init-data-local-decrypt-test-imagelog.sql`(DB B / `SCHEMA_IMAGELOG`), split PB 시 `init-data-local-decrypt-test-pbfep.sql`(PB DB 또는 A의 `SCHEMA_PB`). Docker 호스트에서 Postgres 포트가 매핑된 경우 `DB_HOST=127.0.0.1`, `DB_PORT=<POSTGRES_PUBLISH_PORT>`(예: 5433), 동일하게 `DB_PB_HOST`/`DB_PB_PORT`를 맞추고, 슈퍼유저 비밀번호는 `PGPASSWORD` 또는 `PGPASSWORD_SUPER`로만 전달합니다. 예:

`LOAD_LOCAL_DECRYPT_TEST_DATA=1 INSTALL_NONINTERACTIVE=1 ./setup.sh`

(`backend/src/main/resources/db`에서 실행; `.env` 또는 `.env.docker`를 미리 `set -a` / `source`한 뒤 위 변수만 추가해도 됩니다.) 호스트에 `psql`이 없으면 `setup.sh` 대신 Postgres 컨테이너에 SQL 파일을 복사한 뒤 `psql -U postgres -d imagelog`(및 split PB 시 `-d pbfep`)로 `-f` 실행해도 동일합니다.


## 운영: DB 인스턴스 분리 (System / PB FEP / ImageLog)

운영에서 PostgreSQL을 **물리 인스턴스(호스트·포트·DB 이름) 단위**로 나눌 수 있습니다. 런타임 JDBC 환경 변수의 **정식 이름·추가 옵션(드라이버, fail-fast 등)** 은 배포본의 **`docs/contract.md`** 및 `application.yml`을 따릅니다(백엔드에 PB 전용 데이터소스가 반영된 이후 contract가 최종 권위).

### 풀(연결)별 JDBC 설정 요약

| 역할 | 용도 | 대표 설정 (환경 변수 예) |
|------|------|---------------------------|
| **Primary** | 시스템 테이블(`app_user`, `search_history`, 권한 등). PB URL을 비운 구성에서는 **PB FEP(`pb_send`/`pb_recv`)도 동일 풀**에서 조회합니다. | `spring.datasource.*` — 운영에선 보통 `SPRING_DATASOURCE_URL` / `SPRING_DATASOURCE_USERNAME` / `SPRING_DATASOURCE_PASSWORD`(및 드라이버)로 덮어씀 |
| **PB FEP (선택)** | PB FEP 로그만 **별도 인스턴스**에 둘 때. URL을 **비우면** Primary 풀을 그대로 쓰고, 아래 스키마 변수로만 나눕니다(단일 DB·개발/폐쇄망 단순 구성). | `APP_DATASOURCE_PB_URL` 및 선택적 `APP_DATASOURCE_PB_USERNAME`, `APP_DATASOURCE_PB_PASSWORD`(이미지로그와 동일한 패턴의 부가 키가 있으면 contract 표 참고) |
| **ImageLog (선택)** | `java_fw_imglog` / `imagelog` 테이블이 있는 DB B. URL이 비면 **Primary 풀 재사용**. | `APP_DATASOURCE_IMAGELOG_URL`, `APP_DATASOURCE_IMAGELOG_USERNAME`, `APP_DATASOURCE_IMAGELOG_PASSWORD` 등(contract 표) |

PB·ImageLog 전용 URL을 **모두** 설정하면 백엔드는 Primary·PB·ImageLog에 대해 **서로 다른 HikariCP 풀**(별도 JDBC 연결)을 사용합니다. URL을 비우면 해당 역할은 Primary와 **같은 풀**로 합쳐집니다.

### 동일 호스트에서 스키마만 분리 (단일 DB)

DB 프로비저닝은 **`backend/src/main/resources/db/setup.sh`** 가 담당합니다. 애플리케이션 쪽 스키마 이름은 다음과 **`setup.sh` 변수**를 맞춥니다.

| 애플리케이션 (backend `app.db.schema.*`) | 환경 변수 | `setup.sh` / `check-db.sh` 변수 |
|------------------------------------------|-----------|----------------------------------|
| 시스템 | `APP_DB_SCHEMA_SYS` | `SCHEMA_SYS` |
| PB FEP | `APP_DB_SCHEMA_PB` | `SCHEMA_PB` |
| ImageLog | `APP_DB_SCHEMA_IMAGELOG` | `SCHEMA_IMAGELOG` (DB **B** 위) |

- **`DB_A_NAME`**: 시스템 + PB DDL·시드가 올라가는 DB 이름. Primary JDBC의 DB 이름과 일치해야 합니다.
- **`DB_B_NAME`**: ImageLog DDL·시드 대상 DB. ImageLog 전용 URL을 쓰지 않는 단일 DB 구성에서는 `DB_B_NAME`을 `DB_A_NAME`과 같게 두면 됩니다.

**PB FEP를 시스템 DB(A)와 다른 database로 둘 때**( `DB_PB_NAME` 및 선택적 `DB_PB_HOST`·`DB_PB_PORT`·`DB_PB_SUPERUSER`): `setup.sh`는 A에서 PB DDL을 적용하지 않고 PB database에서 `schema_pb_fep.sql` 등 PB 쪽 DDL·마이그레이션을 적용합니다. 전체 프로비저닝은 한 번의 `SETUP_MODE=full`(또는 설치 스크립트 기본)로 처리하고, **A만 이미 적용된 환경에서 PB DB만 채울 때**는 `SETUP_MODE=pb_only`로 동일 `DB_PB_*`를 넘겨 실행합니다. Spring은 `APP_DATASOURCE_PB_URL`을 해당 PB JDBC와 맞춥니다. split 모드에서 DB A 쪽 `search_path`는 시스템용(`SCHEMA_SYS`, `public`)만 쓰고, `schema_sys.sql`이 `update_updated_at_column()`을 SYS에 정의하므로 PB 스키마를 path에 넣지 않아도 됩니다. **`SETUP_MODE=sys_only`+split**이면 이 스크립트 실행만으로는 PB DB DDL이 돌아가지 않으므로 PB는 `SETUP_MODE=pb_only`로 별도 실행합니다.

스키마·DB 이름을 바꾼 뒤에는 `setup.sh`와 백엔드 env를 **같은 값**으로 유지하고, 점검은 동일 변수를 넘긴 `check-db.sh`로 검증합니다.

## 비대화형 설치·검증·멱등·독립 실행

### `scripts/install_linux.sh` (비대화형)

- **`INSTALL_NONINTERACTIVE=1`** (또는 `true` / `yes`): 대화형 메뉴 없이 `backend/src/main/resources/db/setup.sh`만 실행합니다.
- **Env 로드**: 기본은 저장소 루트 **`.env`**. 다른 파일이면 **`INSTALL_ENV_FILE`** 로 경로 지정.
- **install_linux 사전 검증**: `SETUP_MODE`, `DB_HOST`, `DB_PORT`, `DB_USER`, `DB_PASSWORD`, `DB_NAME` 또는 `DB_A_NAME`; `pb_only`일 때 **`DB_PB_NAME`**. 누락·무효 시 stderr에 **변수 이름만** 한 줄씩 출력하고 비0 종료(비밀번호 값은 출력하지 않음).
- **주의**: **`DB_ETL_USER` / `DB_ETL_PASSWORD`** 는 **`setup.sh` 비대화형**에서 `full`·`sys_only` 시 **필수**이나, `install_linux.sh` 단계에서는 검사하지 않습니다. `.env`에 반드시 넣으세요([`.env.example`](../.env.example), [`docs/contract.md`](../docs/contract.md)).

### `setup.sh` 직접 호출·비대화형 검증

- **`INSTALL_NONINTERACTIVE=1`** 또는 **`SETUP_NONINTERACTIVE=1`** (대소문자 `true`/`yes`/`y` 등 허용)이면, 스크립트가 기본값을 덮어쓰기 **전에** 필수 변수 존재·비어 있지 않음을 검사합니다.
- **`pb_only`**: `DB_PB_NAME`, `DB_USER`, `DB_PASSWORD` 필수; 연결 대상은 **`DB_PB_HOST`·`DB_PB_PORT` 둘 다 설정** 또는 **`DB_HOST`·`DB_PORT` 둘 다 설정** 중 하나.
- **`full` / `sys_only`**: `DB_HOST`, `DB_PORT`, `DB_USER`, `DB_PASSWORD`, **`DB_ETL_USER`**, **`DB_ETL_PASSWORD`** 필수.

### 멱등성(idempotency)

- DDL·대부분의 마이그레이션은 **재실행 안전**(`IF NOT EXISTS` 등)으로 설계되어 있습니다. 문제 해결 절(예: `42703`)에서도 `setup.sh` 재실행을 권장하는 경우가 있습니다.
- **`init-data*.sql`** 는 환경에 따라 **중복 키** 등이 날 수 있으므로, 재적용 전 **`SKIP_INIT_DATA=1`** 등을 검토하세요. `sys_only`에서 초기 데이터까지 넣을 때는 **`SYS_ONLY_LOAD_INIT_DATA=1`** (중복 주의).

### 운영 3분할에서의 **독립** 실행 순서 예

1. **시스템·ImageLog 쪽만** (`SETUP_MODE=full` 또는 `sys_only`) — Primary 호스트에서 A/B 및 split 시 정책에 맞게 실행.
2. **PB DB가 별도 database**이고 `sys_only`만 돌린 경우: PB DDL은 자동으로 포함되지 않으므로 **같은 `.env`로 `SETUP_MODE=pb_only`** 를 한 번 더 실행해 PB 인스턴스만 프로비저닝합니다.
3. 런타임 JDBC는 [`docs/contract.md`](../docs/contract.md)에 맞게 `SPRING_DATASOURCE_*`, `APP_DATASOURCE_PB_*`, `APP_DATASOURCE_IMAGELOG_*` 를 설정합니다.

슈퍼유저 클라이언트 비밀번호는 **`PGPASSWORD`** 또는 **`PGPASSWORD_SUPER`**(설정 시 내부에서 `PGPASSWORD`로 전달)를 사용합니다. 값은 로그에 출력되지 않습니다.

## 🔀 멀티 데이터베이스·멀티 스키마 (선택)

요구사항 `20260320-multi-datasource-schema-configuration`에 따라, 운영에서는 **DB A**에 시스템 데이터(`SCHEMA_SYS`, 예: `logmng_sys`)와 PB FEP 로그(`SCHEMA_PB`, 예: `logmng`)를 두고, **Java FW ImageLog**는 **DB B**의 스키마(`SCHEMA_IMAGELOG`, 기본 `public`)에 둘 수 있습니다. **새 환경 변수를 설정하지 않으면** 기존과 동일하게 단일 DB(`logmng`)·스키마 `public`으로 `setup.sh`가 동작합니다.

| 변수 | 설명 | 기본값 |
|------|------|--------|
| `DB_NAME` | 레거시 DB 이름 | `logmng` |
| `DB_A_NAME` | 시스템 + PB가 들어가는 DB | `DB_NAME` |
| `DB_B_NAME` | ImageLog 전용 DB | `DB_A_NAME` (같으면 단일 DB) |
| `SCHEMA_SYS` | 시스템 DDL 대상 스키마 | `public` |
| `SCHEMA_PB` | PB FEP DDL 대상 스키마 | `public` |
| `SCHEMA_IMAGELOG` | DB B에서 imagelog DDL 대상 스키마 | `public` |
| `DB_SUPERUSER` | DDL 실행 슈퍼유저 | `postgres` |
| `PGPASSWORD_SUPER` | 슈퍼유저 비밀번호(선택) | (미설정 시 로컬 인증에 따름) |

**DDL 파일 구성**  
`schema.sql`은 `schema_pb_fep.sql`(PB)과 `schema_sys.sql`(시스템)을 `\i`로 불러옵니다. 단일 DB·`public`만 쓰는 경우에도 `psql -f schema.sql` 한 번으로 이전과 동일한 객체 집합이 생성됩니다.  
PB FEP 로그 검색 API는 `pb_send`와 `pb_recv`를 UNION ALL로 조회하며, `init-data.sql` 시드도 동일 두 테이블에 적재합니다.

**마이그레이션 적용 순서 (TC-06)**  

1. DB A에 `SCHEMA_SYS`, `SCHEMA_PB`가 `public`이 아니면 `CREATE SCHEMA IF NOT EXISTS`로 생성.  
2. **PB**: `psql` 세션에서 `SET search_path TO SCHEMA_PB, public` 후 `schema_pb_fep.sql`.  
3. **시스템**: `SET search_path TO SCHEMA_SYS, SCHEMA_PB, public` 후 `schema_sys.sql` (**PB에 생성된 `update_updated_at_column()`을 쓰므로 PB가 path에 포함되어야 함**).  
4. **활동 로그**: 동일 `search_path`로 `schema_user_activity_log.sql`.  
5. **ImageLog**: DB `DB_B`에서 `SET search_path TO SCHEMA_IMAGELOG, public` 후 `schema_imagelog.sql`.  
6. 이후 마이그레이션·`init-data.sql`은 앱 테이블이 있는 스키마를 앞에 두는 `search_path`(예: `SCHEMA_SYS, SCHEMA_PB, public`)로 DB A에 적용; `init-data-imagelog.sql`은 DB B에 적용.  
7. **`setup.sh` 단계 4a–4g(레거시 정렬)**: ImageLog 쪽 `migrate-imagelog-guid-status-unique-20260320.sql`(DB B) 후, 시스템 DB A에 `migrate-sys-decryption-composite-pk-20260320.sql`(승인 스냅샷·`user_decryption_allowed`에 `row_status` 및 복합 PK). idempotent.  
8. **`setup.sh` 단계 4h (`permission_group_screen` 컬럼)**: init-data(5단계) 이전에 DB A에서 다음 네 파일을 **순서대로** 적용 — `migrate-permission-group-screen-scope.sql` → `migrate-permission-group-screen-functions.sql` → `migrate-permission-group-screen-decrypt.sql` → `migrate-permission-group-screen-scope-team.sql`. 신규 설치는 `schema_sys.sql`에 컬럼이 이미 있어 no-op; 레거시 테이블만 실제 DDL이 수행됨. **전체 `setup.sh`를 다시 돌릴 수 없는** 환경에서는 동일 순서로 수동 실행하되 `SET search_path TO SCHEMA_SYS, SCHEMA_PB, public`(또는 운영 환경 변수에 맞는 스키마)을 사용합니다. 점검: `check-db.sh` 6b. 요구사항: `docs/requirements/20260320-permission-group-screen-entry-error-migration-check.md`.  
실제 일괄 실행은 `backend/src/main/resources/db/setup.sh`가 위 순서와 변수를 사용합니다. 점검은 `check-db.sh`로 동일 변수를 넘겨 실행합니다.

**외부 조직 복제 `ext_department` / `ext_employee` (요건 20260407)**  
ETL·레플리카 작업은 전용 DB 역할 **`logmng_etl`**(기본; 환경 변수 `DB_ETL_USER` / `DB_ETL_PASSWORD`로 덮어쓰기)을 사용해 `ext_*`에 INSERT·UPDATE·DELETE합니다. 애플리케이션 JDBC 사용자(`DB_USER`, 기본 `logmng`)는 **`ext_*`에 SELECT만** 허용됩니다(`setup.sh` 4b-ext). 프로비저닝 시 외부 직원 키와 `app_user`의 연결은 **`app_user_external_identity`** 테이블에 저장합니다(복제 테이블에 대한 FK 없음). 이름 fuzzy 검색에 **`pg_trgm`** 을 쓰려면 별도 마이그레이션에서 `CREATE EXTENSION IF NOT EXISTS pg_trgm` 및 GIN 인덱스를 추가하면 되며, 기본 설치는 btree 인덱스만 포함합니다. 점검: `check-db.sh` 6e(TC-D01/D02).  
`init-data.sql`의 **HR_SAMPLE** `ext_employee` 시드는 `employee_number`를 **8자리 문자열**(예: `20261001`, `20261999`)로 두어 사용자 관리 화면의 숫자 **사용자 ID** 형식과 맞춥니다(기존 DB는 `migrate-hr-sample-employee-number-userid-format-20260407.sql`).

**애플리케이션 `search_path`**  
백엔드는 JDBC URL 옵션 또는 커넥션 풀 초기 SQL로 DB A에 `logmng_sys, logmng, public` 등 운영 스키마 순서를 맞춥니다. 상세 키는 `application.yml` 및 `docs/contract.md`(멀티 데이터소스 반영 시)를 따릅니다.

### 기존 `logmng` 스키마에 데이터만 있고 `logmng_sys`만 새로 쓰고 싶을 때

| 상황 | 가능 여부 / 권장 |
|------|------------------|
| **PB(`pb_send` 등)만 `logmng`에 있고**, 시스템 테이블(`app_user`, `search_history` 등)은 아직 없거나 `public` 등 다른 스키마에만 있음 | `SCHEMA_PB=logmng`, `SCHEMA_SYS=logmng_sys`로 `SETUP_MODE=sys_only` 실행 가능. `schema_sys.sql`이 `logmng_sys`에 객체를 만들고, `update_updated_at_column()`은 `search_path`에 `logmng`가 포함되어 PB 쪽 함수를 참조합니다. **기존 시스템 데이터가 다른 스키마에 있으면** 별도 `INSERT … SELECT`/DDL로 **이전**해야 앱이 같은 데이터를 봅니다. |
| **시스템 테이블까지 이미 `logmng` 스키마 안에 있음** | `logmng_sys`에만 DDL을 추가하면 `app_user` 등 **이름이 같은 빈 테이블**이 새 스키마에 생길 수 있습니다. `APP_DB_SCHEMA_SYS=logmng_sys`로 앱을 켜면 **빈 DB처럼 보일 수 있음**. 이 경우 **권장**: `APP_DB_SCHEMA_SYS`와 `APP_DB_SCHEMA_PB`를 **둘 다 `logmng`**로 두어 기존 데이터를 그대로 사용하거나, DBA가 테이블/데이터를 `logmng_sys`로 **이전**한 뒤 스키마 이름을 맞춥니다. |

**`SETUP_MODE=sys_only`** (`setup.sh`): `schema_pb_fep.sql`과 `init-data.sql`을 건너뜁니다. 초기 데이터까지 넣으려면(중복 주의) `SYS_ONLY_LOAD_INIT_DATA=1`을 함께 설정합니다.

**Linux 설치 도구**: 저장소 루트에서 `./scripts/install_linux.sh` — **대화형**(기본): 메뉴에서 전체 설치(1), `sys_only`(2), export 파일만 생성(3). **비대화형**: `INSTALL_NONINTERACTIVE=1` 과 채워진 `.env`([`.env.example`](../.env.example)). 생성 파일 기본 경로는 `backend/.env.logmng.generated`(`.gitignore` 대상, 커밋 금지). **`.env` 권한**: `chmod 600` 권장, 커밋 금지.

## 🔧 사전 요구사항

- PostgreSQL 16 설치 완료
- Homebrew (macOS)

## 🚀 설정 방법

### 1. PostgreSQL 서비스 시작

```bash
# PostgreSQL 16 서비스 시작
brew services start postgresql@16

# 서비스 상태 확인
brew services list | grep postgresql@16
```

### 2. 데이터베이스 및 사용자 생성

```bash
# PostgreSQL에 접속 (기본 postgres 사용자)
psql -U postgres

# 또는 비밀번호 없이 접속
psql postgres
```

PostgreSQL 프롬프트에서 다음 명령 실행:

```sql
-- 데이터베이스 생성
CREATE DATABASE logmng;

-- 사용자 생성
CREATE USER logmng WITH PASSWORD 'logmng123';

-- 권한 부여
GRANT ALL PRIVILEGES ON DATABASE logmng TO logmng;

-- 생성된 데이터베이스로 전환
\c logmng

-- 스키마 권한 부여
GRANT ALL PRIVILEGES ON SCHEMA public TO logmng;
```

### 3. 테이블 생성

```bash
# 스키마 파일 실행 (schema.sql → schema_pb_fep.sql + schema_sys.sql)
cd backend/src/main/resources/db
psql -U postgres -d logmng -f schema.sql
# 멀티 스키마는 setup.sh 사용 권장 (search_path·GRANT·마이그레이션 일괄)
```

또는 PostgreSQL 프롬프트에서:

```sql
\c logmng
\i /Volumes/T7/dev/logmng_frontend/dev/backend/src/main/resources/db/schema.sql
```

### 4. 초기 데이터 삽입

```bash
# 초기 데이터 파일 실행
psql -U postgres -d logmng -f init-data.sql
```

### 5. 연결 테스트

```bash
# logmng 사용자로 연결 테스트
psql -U logmng -d logmng -h localhost -p 5432

# 테이블 확인
\dt

# 데이터 확인
SELECT COUNT(*) FROM pb_send;
SELECT COUNT(*) FROM pb_recv;
```

## 📊 테이블 구조

### pb_send (송신 로그 테이블)
- `id`: BIGSERIAL (Primary Key)
- `log_timestamp`: TIMESTAMP (로그 시간)
- `media_code`: VARCHAR(10) (매체코드)
- `tr_code`: VARCHAR(20) (거래코드)
- `user_id`: VARCHAR(50) (사용자ID)
- `ip_address`: VARCHAR(45) (IP주소)
- `user_agent`: TEXT (사용자에이전트)
- `request_data`: TEXT (요청데이터 - 암호화)
- `response_data`: TEXT (응답데이터 - 암호화)
- `status_code`: INTEGER (상태코드)
- `response_time`: INTEGER (응답시간)
- `error_message`: TEXT (오류메시지)
- `session_id`: VARCHAR(100) (세션ID)
- `device_type`: VARCHAR(20) (디바이스타입)
- `created_at`: TIMESTAMP (생성일시)
- `updated_at`: TIMESTAMP (수정일시)

### pb_recv (수신 로그 테이블)
송신 테이블과 동일한 구조를 가집니다.

## 🔍 인덱스

다음 인덱스가 자동으로 생성됩니다:
- `idx_pb_send_timestamp`: 로그 시간 인덱스
- `idx_pb_send_media_code`: 매체코드 인덱스
- `idx_pb_send_tr_code`: 거래코드 인덱스
- `idx_pb_send_user_id`: 사용자ID 인덱스
- `idx_pb_send_session_id`: 세션ID 인덱스
- `idx_pb_send_search`: 복합 인덱스 (timestamp, media_code, tr_code)
- 수신 테이블에도 동일한 인덱스 생성

## ⚙️ 백엔드 설정

`application.yml`에 다음 설정이 포함되어 있습니다:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/logmng
    username: logmng
    password: logmng123
    driver-class-name: org.postgresql.Driver
```

## 🔧 문제 해결

### PostgreSQL 서비스가 시작되지 않는 경우

```bash
# 서비스 재시작
brew services restart postgresql@16

# 로그 확인
tail -f /opt/homebrew/var/log/postgresql@16.log
```

### PostgreSQL 42703: `column "row_status" does not exist` (`search_history_approved_row` 등)

- **원인**: 테이블이 예전 DDL로만 생성된 **레거시 DB**이고, `migrate-sys-decryption-composite-pk-20260320.sql`이 아직 적용되지 않았습니다. `schema.sql`만 다시 실행해도 `CREATE TABLE IF NOT EXISTS` 때문에 **기존 테이블에 컬럼이 자동 추가되지 않습니다**.
- **해결 (권장)**: `backend/src/main/resources/db`에서 `setup.sh`를 재실행하면 4g에서 동일 마이그레이션이 적용됩니다(다른 단계도 idempotent로 설계됨; `init-data` 중복은 환경에 따라 주의).
- **수동 한 줄 (단일 DB·`public`, `application.yml` 기본과 동일: `localhost:5432`, DB `logmng`)** — 프로젝트 루트에서:

```bash
psql -U postgres -h localhost -p 5432 -d logmng -v ON_ERROR_STOP=1 \
  -c "SET search_path TO public, public;" \
  -f backend/src/main/resources/db/migrate-sys-decryption-composite-pk-20260320.sql
```

- **폐쇄망 전용(동일 DDL, 단일 파일)**: `airgap-only-20260320-sys-decryption-composite-pk.sql` — 오프라인 번들 `db/`에 포함되며, 위 `psql` 예에서 `-f` 경로만 이 파일로 바꿔 실행하면 됩니다.

- **멀티 스키마**: `setup.sh`와 동일하게 `SET search_path TO SCHEMA_SYS, SCHEMA_PB, public` 후 같은 파일을 DB A에 실행하세요(변수 값은 해당 환경의 `DB_SETUP_GUIDE` § 멀티 DB 표 참고).
- **비밀번호**: 로컬 `trust`면 생략 가능; 그 외에는 `psql -W` 또는 `PGPASSWORD`/`PGPASSWORD_SUPER`를 사용합니다(저장소에 비밀번호를 넣지 마세요).

### 연결 실패 시

1. PostgreSQL 서비스가 실행 중인지 확인:
   ```bash
   brew services list | grep postgresql@16
   ```

2. 포트 확인:
   ```bash
   lsof -ti:5432
   ```

3. 수동으로 서비스 시작:
   ```bash
   /opt/homebrew/opt/postgresql@16/bin/postgres -D /opt/homebrew/var/postgresql@16
   ```

### 권한 문제

```sql
-- 모든 권한 부여
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO logmng;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO logmng;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO logmng;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO logmng;
```

## 📝 참고사항

- 기본 비밀번호는 `logmng123`입니다. 프로덕션 환경에서는 반드시 변경하세요.
- 데이터베이스 이름은 `logmng`입니다.
- 사용자 이름은 `logmng`입니다.
- 포트는 기본값 `5432`를 사용합니다.

### search_history.user_id 규칙 및 마이그레이션

- `search_history.user_id`는 요청자의 **사용자 ID (numeric `app_user.id`)** 입니다. 조인 조건은 **app_user.id = search_history.user_id** 입니다. username을 저장하지 않습니다. (요건: 20260316-search-history-user-id-query-and-naming)
- **신규 설치**: schema.sql 적용 후 init-data.sql에서 search_history 시드는 app_user.id(숫자)를 사용합니다.
- **기존 DB (VARCHAR user_id)** : 사용자 승인 후 아래 마이그레이션을 한 번 실행하세요. 혼합 의미 검출, username→id·id::text→id 백필, 고아 행 처리(정책: 삭제 후 문서화), cutover·FK·인덱스 적용. idempotent.
- **실행 예 (프로젝트 루트 기준):**  
  `psql -U postgres -h localhost -p 5432 -d logmng -f backend/src/main/resources/db/migrate-search-history-user-id-to-bigint.sql`
- **복호화 실행 경로 (req 20260317-decrypt-execution-user-id-fix)**: POST /api/logs/decrypt 의 소유·승인 검사는 `search_history.user_id`(BIGINT)와 현재 사용자 id만 사용합니다. 이 경로를 사용하기 전에 **반드시** `migrate-search-history-user-id-to-bigint` 를 적용해 `search_history.user_id` 가 BIGINT인 상태로 두세요.
  - **증상**: `search_history.user_id`가 아직 VARCHAR이고 username 값이 들어 있는 경우, 코드는 Long(user id)로 비교하므로 매칭되지 않아 "복호화 거부(승인 미충족)"가 발생할 수 있습니다. 이 경우 위 마이그레이션을 실행하면 해결됩니다.
- 이전 스크립트 `migrate-search-history-user-id-to-username.sql`은 **legacy** 로 두지 않고 새 스키마로 정렬한 환경에서는 실행하지 마세요.

### 복호화 허용 저장소 (user_decryption_allowed, req 20260318)

- "누가 어떤 GUID를 복호화할 수 있는지"는 **user_decryption_allowed** 테이블에서만 판단한다. `search_history_approved_row`는 감사/이력용으로만 유지된다.
- **마이그레이션 적용** (기존 DB에 테이블 추가 및 선택적 백필):
  - 프로젝트 루트에서: `psql -U postgres -h localhost -p 5432 -d logmng -f backend/src/main/resources/db/migrate-user-decryption-allowed.sql`
  - 또는: `psql -U logmng -h localhost -p 5432 -d logmng -f backend/src/main/resources/db/migrate-user-decryption-allowed.sql`
  - idempotent: 테이블/인덱스는 IF NOT EXISTS; 백필은 재실행 시 ON CONFLICT로 덮어쓰므로 안전.
  - `search_history_approved_row`는 변경·삭제하지 않음.

### ImageLog·승인 스냅샷 복합 키 (guid + status, req 20260320)

- **의미**: `java_fw_imglog` 행 식별은 `(guid, status)` 이다. ImageLog 테이블에는 유니크 인덱스, 시스템 DB에는 `search_history_approved_row.row_status`·`user_decryption_allowed.row_status`와 확장 PK가 필요하다.
- **스크립트 (분리 적용 — 다중 DB 필수)**:
  1. **DB B** (`SCHEMA_IMAGELOG`): `migrate-imagelog-guid-status-unique-20260320.sql` — `SET search_path` 후 실행 (`setup.sh`의 `run_sql_file_sp`와 동일).
  2. **DB A** (시스템 `SCHEMA_SYS` 등): `migrate-sys-decryption-composite-pk-20260320.sql`
- **단일 DB·한 스키마(public)**: `backend/src/main/resources/db/`에서 `psql -v ON_ERROR_STOP=1 -f migrate-imagelog-composite-decrypt-20260320.sql` — 내부에서 `\ir`로 위 두 파일을 순서대로 포함한다(동일 `search_path`이면 public에만 있을 때).
- **신규 설치**: `schema_sys.sql` / `schema_imagelog.sql`에 이미 반영되어 있으나, `setup.sh`는 레거시 DB 정렬을 위해 위 마이그레이션을 **idempotent**로 추가 실행한다.
- **사전 점검**: ImageLog에 동일 `(guid, 정규화 status)` 중복이 있으면 유니크 인덱스 생성이 실패한다. 주석 및 `migrate-imagelog-guid-status-unique-20260320.sql` 상단의 `SELECT … HAVING COUNT(*) > 1` 참고.
- **런타임 오류 42703** (`column "row_status" does not exist`): 위 시스템 마이그레이션 미적용 — 아래 **문제 해결 § PostgreSQL 42703** 또는 `check-db.sh` 6a 참고.

### 복호화 403 시 점검 (req 20260317-image-log-decrypt-error-root-cause-and-data-validation)

"복호화 거부(승인 미충족)"(403 DECRYPTION_NOT_APPROVED)이 **마이그레이션 적용 후에도** 발생하면, 다음 진단 SQL로 해당 검색 이력 행의 `user_id`와 요청자(requester)의 `app_user.id`가 일치하는지 확인하세요.

1. **실패한 요청의 searchHistoryId**를 로그 또는 프론트에서 확인합니다.
2. **요청자(복호화를 실행하려는 사용자)의 로그인 ID(사용자명)** 또는 `app_user.id`를 확인합니다.
3. 아래 SQL에서 `:search_history_id`를 해당 ID로 바꿔 실행합니다.

```sql
-- 아래에서 <search_history_id>, <requester_username> 을 실제 값으로 바꿔 실행
-- 예: search_history_id=100, 요청자 로그인ID=user2
SELECT sh.id AS search_history_id,
       sh.user_id AS row_user_id,
       sh.approval_status,
       sh.expires_at,
       (sh.expires_at > CURRENT_TIMESTAMP) AS not_expired,
       au.id AS app_user_id,
       (sh.user_id = au.id) AS user_id_matches
FROM search_history sh
LEFT JOIN app_user au ON au.username = '<requester_username>'
WHERE sh.id = <search_history_id>;
```

- **해석**: `row_user_id`는 해당 검색 이력을 **요청한 사용자**의 `app_user.id`여야 합니다. 복호화 실행 시 **현재 로그인 사용자 id**와 `row_user_id`가 같아야 403이 발생하지 않습니다.
- `user_id_matches`가 false이면, 해당 검색 이력은 다른 사용자(요청자) 소유이므로 **현재 로그인한 사용자가 요청자가 아닐 때** 의도된 403입니다(실행자는 요청자만 가능).
- `row_user_id`가 요청자의 `app_user.id`와 다른데 요청자가 실행했다면, **데이터 불일치**이거나 **세션/currentUserId** 문제일 수 있으므로 백엔드 로그의 `복호화 승인 검사 실패(진단): searchHistoryId=..., currentUserId=..., reason=..., rowUserId=...` 로그로 `currentUserId`와 `rowUserId`를 비교하세요.

## 검색 이력 승인 흐름 진단 로그 (선택)

운영에서 `APPROVAL_ERROR` 원인을 단계별로 구분할 때, 백엔드 환경 변수 **`APP_DIAGNOSTIC_APPROVAL_FLOW=true`** 를 설정한 뒤 재시작합니다. 로컬 dev에서는 **`BACKEND_DIAGNOSTIC_APPROVAL=1 ./scripts/dev-services.sh backend restart`** 로 동일하게 켤 수 있습니다. 로그에 **`[diag-approval]`** 접두와 `searchHistoryId=`, `phase=`(예: `LOAD_PENDING`, `SNAPSHOT`, `TXN_BEFORE_COMMIT`, `TXN_AFTER_COMMIT`, `DECRYPTION_ALLOWED_BEFORE`/`AFTER`, `CONTROLLER_THROWABLE`)가 출력됩니다. 상세 `DEBUG`는 로거 `com.logmng.diagnostic.approval`을 `DEBUG`로 올릴 때만(플래그가 켜진 상태에서) 추가로 나옵니다.

