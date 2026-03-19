# 20260320 - Multi-datasource and schema configuration (sys / PB log / ImageLog)

## 1. User requirement

### Requirement description

Operations will split persistence as follows:

| Store | Physical database | PostgreSQL schema | Data domain |
|-------|-------------------|---------------------|-------------|
| **A** | Database **A** (one server/instance; name configurable) | **`logmng_sys`** | Application system data (users, permission groups, search history, activity log metadata, decryption-approval stores, etc.—today in `schema.sql`, `schema_user_activity_log.sql`, migrations, and related app tables). |
| **A** (same DB as sys) | Same **A** | **`logmng`** | PB FEP log tables (`pb_send`, `pb_recv` and related PB log access). |
| **B** | Database **B** (separate from A; name configurable) | **`public`** (default; configurable if B uses another schema) | Java FW Image Log (`imagelog` table and ImageLog-only search/suggest/decrypt paths). |

The product requires this layout to be **driven by configuration** in:

1. **Database setup** — scripts and documentation must allow targeting the correct database(s) and schema(s) when creating objects and applying migrations (no reliance on a single hardcoded “everything in one DB + `public` only” workflow).
2. **Backend** — Spring Boot must support **multiple JDBC data sources** (or an equivalent supported pattern) so ImageLog traffic uses **B** while system + PB log traffic uses **A**, with **schema names and JDBC URLs** supplied via properties / environment (not baked into Java literals in a way that prevents the split).

**Backward compatibility:** Existing **single-database** developer setups (one PostgreSQL database, e.g. current `logmng`, with tables in default/`public` or a single search path) should remain **achievable** through **default configuration** or a documented “compat” profile, so local onboarding is not blocked.

**Non-goals (unless later requirement):** Changing HTTP API request/response shapes; adding an admin UI to edit datasource URLs in the browser; distributed transactions (2PC/XA) across A and B unless explicitly required later.

### User scenario

1. An operator provisions **database A** with schemas `logmng_sys` and `logmng`, and **database B** with ImageLog data in `public` (or as configured).
2. They run DB setup/migration tooling (or documented `psql` steps) that applies system DDL to `logmng_sys`, PB log DDL to `logmng`, and ImageLog DDL to B’s configured schema—without forking the repo to edit hardcoded database names inside SQL files (parameters or documented substitution must suffice).
3. They configure the backend via `application.yml`, profile, or environment variables: JDBC URL and credentials for **A**, JDBC URL and credentials for **B**, and schema-related settings (e.g. default schema, `search_path`, or table qualifiers) for `logmng_sys` / `logmng` / ImageLog.
4. The application starts; users use PB FEP log search (hits **A** / `logmng`), Java FW Image Log search and related features (hits **B** / configured schema), and all other features (auth, permissions, search history, etc.) (hit **A** / `logmng_sys` or configured path).
5. **Problem:** Today the backend uses a **single** `DataSource` (`DataSourceConfig` + `spring.datasource.*`) and SQL largely assumes **unqualified** table names (`pb_send`, `pb_recv`, `imagelog`) on one connection, and `setup.sh` targets a single `DB_NAME` with `public` grants. This blocks the A/B + multi-schema split without code and script changes.

### Expected outcome

- Two (or more) **independently configurable** JDBC configurations are supported: at minimum **primary (A)** for system + PB log, and **secondary (B)** for ImageLog.
- Schema placement for **`logmng_sys`**, **`logmng`**, and ImageLog on **B** is **configurable** (via URL parameters, Hikari `connection-init-sql` for `search_path`, and/or explicit configurable table/schema qualifiers in SQL builders—implementer chooses a consistent approach documented in §2).
- **DB setup** (`setup.sh`, `check-db.sh`, migration application notes, `DB_SETUP_GUIDE.md` or equivalent) documents how to apply DDL to the correct database/schema; scripts should prefer **environment variables** or parameters for DB name, host, user, and target schema where practical.
- **Secrets** (passwords, full JDBC URLs with credentials) remain **out of the repository**; example `application.yml` uses placeholders or non-secret defaults only, aligned with `docs/security-guide.md`.
- **Single-DB dev** remains possible: e.g. one database with a `search_path` or defaults matching current behavior, documented as the default profile.
- **Contract / ops docs** (`docs/contract.md` environment table) describe multi-datasource / multi-schema configuration at a high level so deployers know required env vars.
- **Cursor domain knowledge** (`db-domain`, `log-search-domain` skills) is updated after implementation so agents describe the real connection model.

**Note:** This requirement does **not** trigger search/filter UI pattern §2.4 (forms-and-filters alignment).

## 2. Design

### 2.1 Security review (optional; when PII / decryption / access control)

Formal Security subagent review is **recommended** before production rollout of dual databases.

- [ ] Security review performed (check when Security has signed off)
- **Risks (preliminary):** Additional credentials and JDBC endpoints increase secret-management and misconfiguration surface; misrouting could send ImageLog queries to the wrong DB; logs must not print full JDBC URLs or passwords.
- **Acceptance / recommendations (preliminary):** Use env vars or externalized config for all URLs and passwords; restrict health/diagnostic endpoints from exposing B DB internals; keep API-layer authorization unchanged (no new anonymous paths); document least-privilege DB users per datasource (A vs B).

### Technical design

#### Codebase summary

- **Backend config:** `DataSourceConfig` builds one `HikariDataSource` from `spring.datasource.*` (`application.yml`). There is no `@Primary` second bean today.
- **PB + ImageLog access:** `LogDbService` uses the injected `DataSource` for `pb_send` / `pb_recv` **and** `imagelog` (unqualified `FROM imagelog`, `FROM pb_send`, etc.).
- **ImageLog-adjacent:** `SearchSuggestService` runs `SELECT DISTINCT … FROM imagelog` for Java FW Image Log field suggestions. `GenerateSampleDataScript` seeds `imagelog` via `JdbcTemplate` (same primary datasource). `DbTestController` inspects `pb_send` / `pb_recv` on the primary datasource.
- **System data:** Numerous services inject the same `DataSource` for `app_user`, permission groups, `search_history`, activity log, decryption-allowed stores, etc. (all currently expected on the same database as PB tables in dev).
- **Hardcoded schema assumption:** `SearchHistoryUserIdMigrationCheck` queries `information_schema` with `table_schema = 'public'` for `search_history`—fragile if system tables move to `logmng_sys`.
- **DB scripts:** `setup.sh` uses fixed `DB_NAME=logmng`, applies `schema.sql`, `schema_user_activity_log.sql`, `schema_imagelog.sql`, and migrations against that single database; grants on `SCHEMA public`.
- **Contract:** `docs/contract.md` states a single DB row (localhost `logmng` + datasource).

#### Problem analysis

1. **Single connection** cannot simultaneously default to schema `logmng_sys` for app tables, `logmng` for PB tables, and database **B** for `imagelog` without `search_path` tricks that **cannot** span two physical databases.
2. **Unqualified SQL** ties all tables to one PostgreSQL `search_path` (typically `public`), which conflicts with placing sys vs PB objects in distinct schemas on A.
3. **Setup and check scripts** encode one database name and do not describe applying ImageLog DDL only to B or splitting sys vs PB schemas on A.
4. **Tests** widely construct or inject a single `DataSource`; multi-datasource wiring must be reflected in Spring tests and H2-based unit tests where applicable.

#### Solution approach

Structure by scope for handoff.

**Architecture (multi-datasource):**

- Introduce a **secondary `DataSource` bean** (e.g. `imagelogDataSource`) for database **B**, with its own Hikari pool and properties namespace (e.g. `app.datasource.imagelog.*` or `spring.datasource.imagelog.*`—implementer aligns with Spring Boot conventions and documents in contract).
- Keep **`@Primary` `DataSource`** for database **A** (system + PB log). Define a **`@Primary` `PlatformTransactionManager`** for the primary datasource; ImageLog read/write paths should **not** participate in the same local transaction as A unless explicitly designed (document that cross-DB operations are **non-atomic** unless a future requirement adds XA).
- Inject the secondary datasource only into components that touch **ImageLog** (`LogDbService` methods for `java_fw_imglog`, `SearchSuggestService` when log type is ImageLog, `GenerateSampleDataScript` / `JdbcTemplate` used for `imagelog`, and any other `imagelog` SQL). All other services remain on the primary bean.
- For **schema separation on A** (`logmng_sys` vs `logmng`), choose one consistent strategy:
  - **Preferred for clarity:** Configurable **schema qualifiers** (properties such as `app.db.schema.sys`, `app.db.schema.pb`) prepended in SQL for system vs PB tables, **or**
  - **Alternative:** Hikari `connection-init-sql` setting `SET search_path TO logmng_sys, logmng, public` on the primary pool, with DDL ensuring no table name collisions across listed schemas.
- For **B / ImageLog**, use JDBC URL `currentSchema=public` or explicit `schema.table` in SQL; schema name must be **configurable**.

**Backend:**

- Refactor `DataSourceConfig` (or add `ImagelogDataSourceConfig`) to register primary + secondary beans with qualifiers.
- Update `LogDbService` (and any helper) to use the correct `DataSource` per log type / code path; ensure decrypt and advanced-search paths for `java_fw_imglog` use **B**.
- Update `SearchSuggestService` for ImageLog suggestions to use **B**.
- Update startup seed utilities (`GenerateSampleDataScript`, encrypted sample generators if they touch `imagelog`) to use **B**’s `JdbcTemplate` or datasource.
- Replace or parameterize **`table_schema = 'public'`** in `SearchHistoryUserIdMigrationCheck` (and any similar checks) to use the **configured system schema** (e.g. `logmng_sys`).
- Extend `DbTestController` (or health checks) to optionally report connectivity to **B** without exposing secrets; align with `api-permission-map` / `AuthInterceptor` for anonymous vs authenticated test endpoints.
- Add **unit/integration tests** that verify routing: e.g. with mocks or testcontainers, assert ImageLog code paths obtain connections from the secondary configuration.
- **Default profile:** When only one database is configured, allow **optional** secondary properties to fall back to primary (document explicitly) **or** require both URLs to be set—product decision: **recommend** fallback for dev ergonomics (single URL duplicates to both beans) if feasible without surprising production.

**DB:**

- Document and, where feasible, parameterize `setup.sh` / `check-db.sh` with env vars: `DB_A_NAME`, `DB_B_NAME`, `SCHEMA_SYS`, `SCHEMA_PB`, optional `SCHEMA_IMAGELOG` / `DB_IMAGELOG` for B.
- Provide a **clear migration story:** either split DDL files by target (sys vs PB vs imagelog) or document `psql -v schema=…` / `\connect` sequences for applying `schema.sql` parts to `logmng_sys` vs `logmng`, and `schema_imagelog.sql` to database B.
- Ensure `GRANT`/`search_path` instructions match the chosen multi-schema model.

**Contract / spec:**

- Update `docs/contract.md` (Environment · Ports / DB section) with multi-datasource env vars and semantics.
- If `docs/api-definition.md` or `specs/*.spec.yaml` describe deployment assumptions, align them; **no** REST path or JSON shape change unless a new health field is added (then document).

**Frontend:**

- **Not required** for this requirement (no UI for datasource editing). No change to configuration screens unless a follow-up requirement adds ops UI.

### Affected scopes and change targets (verification)

Per `docs/workflow/REQUIREMENTS-CHANGE-TARGET-CHECKLIST.md` (§1 scope verification).

| Scope | Affected? | §2 subsection and change file list complete? |
|-------|------------|-----------------------------------------------|
| Backend | Yes | Yes |
| Frontend (config UI + view screen) | No | N/A |
| DB | Yes | Yes |
| Contract / Spec | Yes | Yes |
| Cursor tools (skills, specs) | Yes | Yes |

**Pattern checks:** Does not match scope-supporting screen, permission group API change, or search/filter UI consistency §2.4; **§2.4 verification table not applicable.**

### Planned change file list (expected change targets)

**(Planned at authoring. Implementing agent (Step 4) confirms or amends this list when implementation is complete.)**

#### Backend

- `backend/src/main/java/com/logmng/config/DataSourceConfig.java` (and/or new `*DataSource*.java`) — Register primary + secondary `DataSource` beans; `@Primary` transaction manager for A.
- `backend/src/main/java/com/logmng/service/LogDbService.java` — Route `imagelog` / `java_fw_imglog` SQL to datasource B; route PB SQL to A with correct schema resolution.
- `backend/src/main/java/com/logmng/service/SearchSuggestService.java` — Use B for ImageLog distinct queries.
- `backend/src/main/java/com/logmng/config/SearchHistoryUserIdMigrationCheck.java` — Configurable system schema instead of hardcoded `public`.
- `backend/src/main/java/com/logmng/util/GenerateSampleDataScript.java` (and related imagelog seed utilities if present) — Use B for `imagelog`.
- `backend/src/main/java/com/logmng/controller/DbTestController.java` — Optional: report B connectivity / imagelog table presence (document behavior).
- `backend/src/main/resources/application.yml` — Example structure for dual datasource + schema properties; **no real secrets** in repo.
- `backend/src/test/java/**` — Update Spring tests and any tests that assume a single `DataSource` bean; add routing/regression tests.

#### DB

- `backend/src/main/resources/db/setup.sh` — Parameterize DB/schema targets; document applying ImageLog DDL to B.
- `backend/src/main/resources/db/check-db.sh` — Optional checks for DB B / both pools.
- `backend/src/main/resources/db/schema.sql` / `schema_user_activity_log.sql` / `schema_imagelog.sql` — As needed: schema qualifiers (`CREATE SCHEMA`, `CREATE TABLE logmng_sys.app_user`, etc.) or companion “split apply” docs; **coordinate with DB subagent** for idempotent migrations.
- `backend/DB_SETUP_GUIDE.md` (if present) — Multi-DB / multi-schema procedure.

#### Contract / documentation

- `docs/contract.md` — Environment variables and datasource semantics.

#### Cursor tool update targets

- `.cursor/skills/db-domain/SKILL.md` — Describe A/B datasource and schema layout post-implementation.
- `.cursor/skills/log-search-domain/SKILL.md` — Note ImageLog uses datasource B.

## 3. Test approach

### Test case list (required)

| ID | Scope | Type | Scenario (input / condition) | Expected result | Verification (unit / integration / manual) |
|----|-------|------|------------------------------|-----------------|--------------------------------------------|
| TC-01 | Backend | Normal | Primary datasource configured for A with `search_path` or qualifiers including `logmng_sys` and `logmng`; typical app tables and PB tables exist. | Login, permission load, and PB FEP log search succeed using A. | Integration (Spring context + test DB or Testcontainers) or Manual (documented env). |
| TC-02 | Backend | Normal | Secondary datasource configured for B with `imagelog` populated. | Java FW Image Log search, advanced search, decrypt row, and suggest endpoints hit B and return correct rows. | Unit/Integration (mock or second container) + Manual smoke. |
| TC-03 | Backend | Edge | Secondary URL omitted; **fallback** mode enabled (if implemented per §2). | Application starts; ImageLog and PB both work against single DB when configured as today. | Integration / Manual. |
| TC-04 | Backend | Exception | Secondary URL invalid or B unreachable. | Application fails fast with clear error **or** ImageLog endpoints return controlled errors without crashing A-backed features (product choice—document in implementation). | Integration / Manual. |
| TC-05 | Backend | Normal | `SearchHistoryUserIdMigrationCheck` (or replacement) runs when `search_history` lives in `logmng_sys`. | Check detects column type correctly; no false negatives due to hardcoded `public`. | Unit (mvn test). |
| TC-06 | DB | Normal | Run documented setup applying sys DDL to `logmng_sys`, PB DDL to `logmng`, ImageLog DDL to B. | Tables exist in intended schemas/databases; grants allow app users to connect. | Manual (psql / setup script). |
| TC-07 | Integration | Normal | End-to-end: configured A+B; user searches PB then ImageLog. | Both searches return data from correct physical DB; no cross-contamination. | Manual or scripted HTTP (curl). |
| TC-08 | Contract | Normal | Deployer reads updated `docs/contract.md`. | All required env vars and JDBC properties for A and B are listed and match `application.yml` keys. | Manual review. |

### Test scenarios

#### Scenario 1: Split databases (target architecture)

1. Provision A with `logmng_sys` + `logmng`; provision B with `imagelog` in `public`.
2. Apply DDL per documentation; configure backend with two JDBC URLs.
3. Run TC-01, TC-02, TC-07.

#### Scenario 2: Single-database developer fallback

1. Use one PostgreSQL database and defaults (or duplicated URL config).
2. Run full regression: auth, PB search, ImageLog search, search history.
3. Confirm TC-03.

### Test data

- Minimal rows in `pb_send` / `pb_recv` on A (`logmng` schema).
- Minimal rows in `imagelog` on B (`public` or configured schema).
- At least one `app_user` and permission data on A (`logmng_sys`).

### Test environment

- Backend: `http://localhost:9200`
- Database: PostgreSQL 16 (or project standard); two logical databases or one for fallback scenario.

### 3.5 Browser automation verification

**Not applicable** (no frontend change).

## 4. Checklist

### Frontend verification

- [x] N/A for this requirement

### Backend verification

- [x] API test cases written and run (`mvn test`)
- [x] Logs checked (DataSourceConfig logs pool name/driver only at INFO; no JDBC URL/password at INFO)
- [x] Dual-datasource startup verified (single-DB fallback: ImageLog reuses primary when `app.datasource.imagelog.url` unset; health + `/api/db/test` 200)

### Integration

- [ ] End-to-end PB + ImageLog against **split** A+B databases (TC-01, TC-02, TC-07) — **not run in this session**; operator smoke when two JDBC URLs are configured
- [x] Single-DB fallback tested (if supported) — default `application.yml` + empty imagelog URL; app starts, `mvn test` + local restart pass

### Documentation

- [x] Requirement doc completed (§5 recorded)
- [x] Contract and DB setup guide updated

## 5. Test results

### Test run date

- 2026-03-20

### Test results

#### Frontend

- N/A

#### Backend

| TC | Result | Notes |
|----|--------|--------|
| TC-01 | Partial | Unit/integration cover routing; full A with `logmng_sys`/`logmng` not exercised against live split DB here |
| TC-02 | Partial | `LogDbServiceDataSourceRoutingTest` / mocks; live second DB B not exercised here |
| TC-03 | **Pass** | Empty `app.datasource.imagelog.url` → log line “using primary pool”; `mvn test` + Spring context tests pass |
| TC-04 | Partial | Covered by config (`fail-fast`); live invalid-B URL not exercised here |
| TC-05 | **Pass** | `SearchHistoryUserIdMigrationCheckSqlTest` / configurable schema |
| TC-06 | Deferred | Manual: run `setup.sh` with `DB_A_NAME`/`DB_B_NAME`/`SCHEMA_*` per `DB_SETUP_GUIDE.md` |
| TC-07 | Deferred | Manual HTTP smoke when A+B provisioned |
| TC-08 | **Pass** | `docs/contract.md` updated with property/env tables |

**Commands:**

- `cd backend && mvn test` — exit 0 (2026-03-20)
- `./scripts/dev-services.sh backend restart` — OK; after warm-up: `curl -s -o /dev/null -w "%{http_code}" http://localhost:9200/api/health` → 200
- `curl -s http://localhost:9200/api/db/test` → `connected: true` (single primary URL)

**Outcome:**

- **Pass** for automated scope (unit tests, single-DB dev startup, contract). **Deferred** for full split-DB operator verification (TC-01/02/06/07 live).

### Issues found and resolution

- None

### Next steps

1. Security formal review (§2.1) if production deployment planned.
2. Ops: run TC-06/TC-07 against real A+B when available.
3. Commit recorded for this requirement (see git log).

## 6. Error remedy result (cause and action) — for error/bug fix requirements only

N/A (feature / infrastructure requirement).

---

## 7. Final version (Korean)

### 최종 한국어 요약

- **요구사항 요약**: 시스템 DB는 **A DB**의 **`logmng_sys`** 스키마, PB 로그는 **A DB**의 **`logmng`** 스키마, Java FW ImageLog는 **B DB**의 **`public`**(또는 설정 가능한 스키마)에서 관리한다. DB 준비 스크립트와 백엔드 모두 **설정(환경 변수·프로파일·yml)** 으로 JDBC URL·스키마·데이터소스를 바꿀 수 있어야 하며, 단일 DB 개발 환경도 기본/호환 설정으로 유지 가능해야 한다.
- **기대 결과**: Spring Boot에서 **주 데이터소스(A: 시스템+PB)** 와 **ImageLog 전용 데이터소스(B)** 를 구분하고, `setup.sh` 등으로 스키마/DB별 DDL 적용 절차를 문서화한다. 비밀 정보는 저장소에 두지 않으며, `docs/contract.md`와 Cursor DB/로그검색 스킬을 구현 후 갱신한다.
- **검증**: 자동화 범위(mvn test, 단일 DB 기동, 계약 문서)는 §5에 기록됨. 실제 A/B 분리 DB에 대한 TC-06·TC-07은 운영 스모크로 남김.

---

**Author**: Requirements (subagent)  
**Date**: 2026-03-20  
**Status**: Implemented; §5 recorded; split A+B live smoke (TC-06/07) deferred to ops.

### Step 4 implementation log (main orchestration, 2026-03-20)

- **DB**: `setup.sh` / `check-db.sh` parameterized (`DB_A_NAME`, `DB_B_NAME`, `SCHEMA_*`); `schema_pb_fep.sql` / `schema_sys.sql` + `schema.sql` aggregator; `DB_SETUP_GUIDE.md` updated (DB subagent).
- **Backend**: dual `DataSource`, `imagelogJdbcTemplate`, schema `search_path` via `app.db.schema.*`, `app.datasource.imagelog.*`, fallback when imagelog URL empty; routing in `LogDbService` / `SearchSuggestService` / sample scripts / migration check; `mvn test` exit 0 (Backend subagent).
- **Contract**: `docs/contract.md` environment + property tables (Contract subagent).
- **Skills**: `.cursor/skills/db-domain/SKILL.md`, `log-search-domain/SKILL.md` updated.
