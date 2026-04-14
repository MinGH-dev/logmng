# 20260414 - PB FEP daily partitioning (pb_send / pb_recv)

## 1. User requirement

### Requirement description

PB FEP log storage in the project uses PostgreSQL **range partitioning** on `log_timestamp` for **`pb_send`** and **`pb_recv`** (PB database / schema context is often referred to as **pb_fep** in operator docs). The current provisioning migration creates **monthly** partition boundaries and names partitions with a **`YYYYMM`** suffix.

Operations and retention policies require **day-level** partition granularity: partition boundaries must be **daily** (calendar-day ranges), with naming and automation aligned to **per-day** management (including optional drop of old days for retention).

After the database work is implemented, the change must be covered by **automated tests** where applicable (DB verification scripts, backend regression tests) and must ship with a **versioned deployment/release artifact** per project norms (changelog entry, version bump, offline/airgap bundle alignment as used in recent releases).

### User scenario

1. An operator provisions or upgrades the PB FEP database using `setup.sh` (or equivalent documented path for split-PB / single-DB).
2. The resulting **`pb_send`** and **`pb_recv`** tables are **partitioned by range on `log_timestamp`** with **one partition per calendar day** (for active / planned ranges), not one partition per month.
3. Existing environments that already ran the **monthly** partitioning migration must be upgradable via a **documented migration** that preserves row counts and application-visible behavior.
4. Release engineering produces a **numbered release** (e.g. `CHANGELOG.md`, `pom.xml`, bundle scripts) consistent with prior release practices.

### Expected outcome

- **`pb_send` and `pb_recv`** use **daily** range partitions (partition key remains **`log_timestamp`**); partition naming must follow a **clear, documented convention** (for example `pb_send_YYYYMMDD` / `pb_recv_YYYYMMDD` — exact format to be fixed in implementation and mirrored in verification queries).
- **Greenfield installs** and **CI/test database setup** apply the daily strategy through **schema/migration/setup** scripts without relying on the obsolete monthly-only partition layout for new deployments.
- **Existing databases** that already have **monthly** child partitions can be migrated to **daily** partitions **without data loss**, with **rollback** guidance (see §2).
- **Application behavior** for log type **`pb_feplog`** remains correct: SQL continues to target the **parent** relations (`pb_send` / `pb_recv`); no API request/response shape change.
- **Tests** validate migration applicability and regression; **release artifacts** reflect the new version per project release checklist.

**Note**: This requirement does not change search/filter field layout; no design-doc alignment for forms is required.

## 2. Design

### 2.1 Security review (optional; when PII / decryption / access control)

- [ ] Security review performed (check if applicable)

PB FEP logs may contain PII in column payloads. Changing partition granularity does **not** expand decryption or API scope. Implementers must ensure **migration scripts** do not log row contents at INFO in production.

- Risks: Accidental exposure of row data in migration diagnostics; prolonged locks affecting availability during heavy DDL.
- Acceptance / recommendations: Use **DEBUG-only** or **suppressed** diagnostics for row-level migration; prefer **maintenance window** for production migration; follow `docs/security-guide.md` for logging.

### Technical design

#### Codebase summary

- **Baseline DDL**: `backend/src/main/resources/db/schema_pb_fep.sql` defines **`pb_send`** and **`pb_recv`** as **ordinary (non-partitioned)** tables for first-time creation.
- **Partitioning migration (current)**: `backend/src/main/resources/db/migrate-pb-send-recv-partitioning-20260408.sql` converts existing ordinary tables into **RANGE**-partitioned parents on **`log_timestamp`**, attaches **`pb_send_default`** / **`pb_recv_default`**, and creates **three monthly** partitions (previous / current / next calendar month) with names `pb_send_YYYYMM` / `pb_recv_YYYYMM`.
- **Setup**: `backend/src/main/resources/db/setup.sh` runs the above migration in PB FEP provisioning steps (`5-pb-fep-partition`, split-PB and non-split paths as documented).
- **Backend**: `LogDbService` and related code query **`pb_send`** / **`pb_recv`** as **parent** table names (no partition suffix in Java). `DbTestController` exposes table existence and counts for diagnostics — behavior must remain valid against partitioned parents.
- **Contract**: `docs/contract.md` documents PB tables and datasources; **no API shape change** is required if parent table names and column semantics are unchanged.

#### Problem analysis

1. **Monthly partitions** are too coarse for **day-based retention**, operational **detach/drop** of old data, and alignment with **calendar-day** operations.
2. **Two populations** must be addressed: **new installs** (must not cement monthly-only layout) and **existing installs** that already applied **`migrate-pb-send-recv-partitioning-20260408.sql`**.
3. **DDL complexity** and **locks** may require a **maintenance window**; rollback cannot rely on a trivial one-click reverse DDL for all cases.

#### Solution approach

Structure by scope for handoff.

**Frontend:**

- No UI or client change is required for partition granularity if APIs and parent table names stay the same.

**Backend:**

- **Must not** introduce SQL that references specific child partition table names for normal queries.
- **Must** run existing unit/integration tests (`mvn test`) after DB script changes in integrated environments; extend tests only if new diagnostics or behavior are added.
- Confirm **`DbTestController`** (or similar) still reports correct existence/counts when parents are partitioned by day.

**DB:**

- **Partition strategy**
  - Keep **RANGE (`log_timestamp`)** on both parents.
  - **Daily** bounds: for each day *D*, child partition covers **[D 00:00:00, D+1 00:00:00)** in the session timezone policy used for `log_timestamp` (document whether timestamps are **UTC** or **local** — must be **consistent** with existing data and ETL).
  - **Naming**: adopt **`pb_send_YYYYMMDD`** and **`pb_recv_YYYYMMDD`** (or another single documented pattern) for non-default partitions.
  - **DEFAULT partition**: retain **`pb_send_default`** / **`pb_recv_default`** for rows outside pre-created ranges unless product mandates strict rejection; document behavior.

- **Greenfield / new migrations**
  - Replace or supersede the **monthly** partition creation in **`migrate-pb-send-recv-partitioning-20260408.sql`** with **daily** partition creation for a **defined forward window** (e.g. current day and the next *N* days) plus **backward coverage** as needed for seed data — **exact window** must be chosen to match operator expectations and documented in `backend/DB_SETUP_GUIDE.md`.
  - Ensure **`setup.sh`** invokes the updated migration order **idempotently** (safe re-run where supported).

- **Upgrade path from monthly to daily (existing databases)**
  - Provide a **one-time migration script** (new file) that:
    - For each existing **monthly** child of `pb_send` / `pb_recv`, **splits** the key range into **daily** partitions and **moves** rows without loss (pattern: create daily children, move data, detach/drop monthly children when empty — **exact steps** are implementation details for the DB agent).
    - Preserves **indexes** and **update triggers** consistent with the parent / children model already used in the 20260408 script.
  - Require a **full backup** before running production upgrade.

- **Data retention**
  - **Product must confirm** retention horizon (*N* days). Document **operator procedure** to **`DROP`** detached partitions older than *N* (or attach automation outside the app — optional). The requirement **does not** mandate an in-app scheduler unless explicitly agreed.

- **Downtime**
  - Heavy **`ALTER TABLE`**, attach/detach, or bulk move may take **exclusive locks**. Plan a **maintenance window** or **read-only** period for production; document expected impact per environment size.

- **Rollback**
  - **Primary**: restore from **pre-migration backup** (recommended).
  - **Secondary**: document any **partial** rollback limitations (e.g. if only DDL partially applied). Do not promise trivial in-place downgrade without evidence.

- **Observability**
  - Update **`check-db.sh`** (or add documented SQL) to **assert daily partitioning** (e.g. sample partition name pattern, or `pg_inherits` / `pg_partition_tree` checks) where feasible without brittle hard-coding of every day.

### Affected scopes and change targets (verification)

Per `docs/workflow/REQUIREMENTS-CHANGE-TARGET-CHECKLIST.md`:

| Scope | Affected? | §2 subsection and change file list complete? |
|-------|------------|-----------------------------------------------|
| Backend | Yes (regression / diagnostics only) | Yes |
| Frontend | No | N/A |
| DB | Yes | Yes |
| Contract / Spec | Verify only (no API change) | Yes |
| Cursor tools (skills, specs) | Optional — `db-domain` skill if ops paths change | Yes |

Domain pattern **§2.4 (search/filter UI consistency)** does **not** apply.

### Planned change file list (expected change targets)

**(Planned at authoring. Implementing agent (Step 4) confirms or amends this list when implementation is complete.)**

#### Frontend

- None expected.

#### Backend

- `backend/src/main/java/com/logmng/controller/DbTestController.java` (and related tests under `backend/src/test/java/...`) — **only if** diagnostics must change to validate partitioned parents; otherwise **regression run only**.

#### DB

- `backend/src/main/resources/db/migrate-pb-send-recv-partitioning-20260408.sql` — **updated**: greenfield path creates **daily** partitions (`pb_*_YYYYMMDD`) for **`CURRENT_DATE` − 30 … + 7** days + DEFAULT; same rename/attach pattern as before.
- `backend/src/main/resources/db/migrate-pb-send-recv-monthly-to-daily-20260414.sql` — **new**: one-time **monthly → daily** upgrade (detach `*_YYYYMM` → create day partitions → insert → drop); idempotent skip when no `*_YYYYMM` children.
- `backend/src/main/resources/db/setup.sh` — runs **(1)** 20260408 then **(2)** 20260414 on split-PB and single-DB PB paths (`5-pb-fep-partition` / `5-pb-fep-partition-daily-upgrade`).
- `backend/src/main/resources/db/check-db.sh` — **6i**: optional `pb_send` partition sanity (no legacy `YYYYMM` children; `pg_partition_tree` hint).
- `backend/DB_SETUP_GUIDE.md` — backup, script order, maintenance window, rollback = restore backup, daily window **N=30 / M=7**.
- `.cursor/skills/db-domain/SKILL.md` — PB FEP migration filenames and `setup.sh` step names.

#### Contract / spec

- `docs/contract.md` — **only if** a short **non-normative** note is needed (e.g. "partitioned by day"); **must not** change API payloads.

#### Release / versioning (project norms)

- `CHANGELOG.md` — document partitioning change under a new version section.
- `backend/pom.xml` — version bump aligned with release.
- Scripts referenced by recent releases (e.g. `package-airgap-bin.sh`, `build-offline-bundle.sh`, `dev-services.sh`, `bin/` run scripts per prior changelog entries) — **must** align artifact version with `pom.xml` when releasing.

#### Cursor tool update targets

- `.cursor/skills/db-domain/SKILL.md` — update if migration filenames, setup modes, or operator flow change materially.

## 3. Test approach

### Test case list (required)

| ID | Scope | Type | Scenario (input / condition) | Expected result | Verification (unit / integration / manual) |
|----|-------|------|------------------------------|-----------------|--------------------------------------------|
| TC-01 | DB | Normal | Apply migrations on a **fresh** DB after `schema_pb_fep.sql` (no prior monthly migration) | Parents are **range-partitioned** on `log_timestamp`; **daily** child partitions exist per script window; **DEFAULT** partitions attached; indexes present on parents | Manual / SQL (`\d+`, `pg_partition_tree`, partition list) |
| TC-02 | DB | Normal | Apply **upgrade** migration on a DB that already has **monthly** partitions from `migrate-pb-send-recv-partitioning-20260408.sql` with sample rows spanning months | **Row counts** for `pb_send` and `pb_recv` **match** pre-migration; data visible across date boundaries; no orphan monthly partitions left per design | Manual / SQL (count compare before/after) |
| TC-03 | DB | Edge | Rows with `log_timestamp` falling in **DEFAULT** partition only | Search and counts via parent still correct; optional check that future daily partitions pick up new days per ops procedure | Manual / SQL |
| TC-04 | Integration | Regression | Backend health and PB search: `GET /api/health`, `GET /api/db/test` (if PB connected), `POST` log search for **`pb_feplog`** with date range covering seeded data | **200** responses; result rows consistent with seeded PB data | Integration (curl) or manual |
| TC-05 | Backend | Regression | `mvn test` with PB datasource configured or mocked per project norms | **Exit 0**; no regressions in log-type helpers | Unit (`mvn test`) |
| TC-06 | Release | Normal | Release checklist: `CHANGELOG` entry, `pom.xml` version, bundle/install scripts version alignment | Version strings **consistent** across listed artifacts; packaged deliverable build succeeds per `CHANGELOG` / Release workflow | Manual / script (project release procedure) |

### Test scenarios

#### Scenario 1: Greenfield provisioning

1. Run documented `setup.sh` path for PB FEP (or minimal SQL sequence).
2. Inspect partition hierarchy and naming.
3. Insert seed rows and run PB search API.

#### Scenario 2: Monthly → daily upgrade

1. Create DB at monthly migration state; load representative data.
2. Run daily upgrade migration.
3. Verify counts and partition layout.

### Test data

- Use existing seeds (`init-data*.sql`, local decrypt test seeds) or **executable INSERT** statements with explicit `log_timestamp` per day for multi-day coverage.

### Test environment

- PostgreSQL per `backend/DB_SETUP_GUIDE.md` and `docs/contract.md`.
- Backend: `http://localhost:9200` for integration checks.

## 4. Checklist

### Frontend verification

- [ ] N/A (no frontend change expected)

### Backend verification

- [x] Regression tests pass (`mvn test`)
- [ ] PB diagnostics endpoints behave as expected after DB change *(integration / live DB — optional when DB not attached to this QA run)*

### Integration

- [ ] PB FEP search end-to-end after migration *(live DB with PB FEP data — not executed in this QA run)*
- [x] Health endpoint: `GET /api/health` → 200 (2026-04-14)
- [ ] `GET /api/db/test` with PB connected *(optional — skipped when DB not verified this run)*

### Documentation

- [x] Requirement doc completed (§5 recorded)
- [x] `backend/DB_SETUP_GUIDE.md` updated for operators *(implemented in branch; operator runbook)*

## 5. Test results

### Test run date

- **2026-04-14** (QA Step 5: `mvn test`, release artifact path check, optional DB sanity)

### Test results

#### Frontend

- **N/A** — no frontend change for this requirement.

#### Backend

- **Pass** — `cd backend && mvn test` → **exit 0**, **BUILD SUCCESS**, **Tests run: 492**, Failures: 0, Errors: 0, Skipped: 0 (run on 2026-04-14).

#### DB (manual / optional)

- **Skipped (this run)** — No PostgreSQL instance was available for ad-hoc `psql` / `check-db.sh` partition assertions from this environment (Docker/DB not running or not connected). **TC-01–TC-03** remain operator-verified on a real PB FEP database per `backend/DB_SETUP_GUIDE.md`.

#### Release / offline bundle (1.0.2)

- **Pass** — Confirmed file exists: `dist/logmng-offline-1.0.2/bin/backend/logmng-backend-1.0.2.jar` (2026-04-14).

**Commands:**

| TC / check | Command / action | Result |
|------------|------------------|--------|
| TC-05 | `cd backend && mvn test` | Pass (exit 0; 492 tests) |
| Release JAR | `test -f dist/logmng-offline-1.0.2/bin/backend/logmng-backend-1.0.2.jar` | Pass |
| TC-01–03, check-db §6i | `check-db.sh` / `psql` partition inspection | Skipped (no DB this run) |
| Verify §3 health (optional) | `curl -s http://localhost:9200/api/health` → **200** | Pass (2026-04-14) |

### Issues found and resolution

- None for automated backend tests and 1.0.2 offline JAR path check.

## 6. Error remedy result (cause and action) — for error/bug fix requirements only

_Not applicable (feature/migration requirement)._

---

## 7. Final version (Korean)

> Stakeholder-facing summary. Technical source of truth remains English in §1–§3 above. Verification lines are updated when §5 is complete.

### 요약

- **요구사항**: PB FEP 로그 저장소(`pb_send`, `pb_recv`, 운영 문맥의 pb_fep DB)의 PostgreSQL 파티셔닝을 **월 단위**에서 **일 단위**로 전환한다. 파티션 키는 기존과 같이 **`log_timestamp`** 범위 분할을 사용한다.
- **기대효과**: 일 단위 보관·삭제(DROP PARTITION) 운영이 가능하고, 신규 설치는 일 단위 전략으로 일관되게 프로비저닝된다. 기존 **월 단위**로 이미 적용된 DB는 **데이터 손실 없이** 일 단위로 이행할 수 있어야 하며, **백업·롤백·점검 창구**가 문서화된다. 애플리케이션 API 형식은 부모 테이블명이 유지되는 한 **변경하지 않는다**.
- **구현 후**: 자동/통합 테스트와 배포 규범에 따른 **버전·CHANGELOG·번들** 반영이 수행된다.

### 검증 결과 (§5 연동)

- **2026-04-14**: 백엔드 `mvn test` 통과(492 tests), 오프라인 번들 **1.0.2** JAR 경로 확인. DB 파티션 실물 검증은 운영/스테이징 DB에서 수행.

---

**Author**: Requirements (subagent)  
**Date**: 2026-04-14  
**Status**: QA verification recorded (§5); release 1.0.2  
