# 20260414 - PB FEP wire-format schema alignment (pb_send / pb_recv)

## 1. User requirement

### Requirement description

Align PostgreSQL definitions for **`pb_send`** and **`pb_recv`** with the **legacy PB FEP wire-format column layout** (reference export style `pb_recv_20250818`), instead of the current simplified application-centric columns in `backend/src/main/resources/db/schema_pb_fep.sql` (e.g. `log_timestamp`, `media_code`, `tr_code`, `user_id`, …).

**User / broker identity:** Wire field **`brodid`** must correspond to **`user_id`** semantics for application use: the existing search filter **`loginId`** (UI) must continue to resolve to the same logical user key as today (equality filter on the column that stores wire **`brodid`**).

**Partitioning:** Remake the **daily partition creation** path so it matches the **new** table layout. The **RANGE partition key** must be a **real date/time type** (`timestamp` / `timestamptz`), not a raw wire `varchar`. If legacy stores **`log_time`** (or similar) as **string**, the design must document how rows get a **typed** partition key (populate a canonical column on ingest, migration rules, or documented parse expression)—and scripts (`create-pb-send-recv-daily-partitions-only.sql`, migration chain referenced from `setup.sh` / `backend/DB_SETUP_GUIDE.md`) must stay consistent with that choice.

**Application compatibility:** The **existing PB FEP search UIs** (legacy **`pb-feplog`** → `POST /api/logs/db-refactored/search`, wireframe **`pb-fep-log-search`** → `POST /api/logs/db-refactored/pb-fep-log-search`) must **remain compatible**: labels and row keys used by the screens must not break; **automated search-related tests** (`LogDbServiceTest`, `LogGrid.test.js`, `SearchForm.test.js`, `LogTable.test.js`, routing tests, etc.) must pass after implementation without ad-hoc “fix the test only” drift.

**Reference wire columns (pb_recv example — product-provided list):**  
`log_time`, `log_ch_cd`, `log_io_cd`, `log_len`, `len`, `tr_gb`, `comp_gb`, `enct_gb`, `data_off`, `tr_code`, `comp_no`, `brodid`, `media_gb`, `channel_no`, `tr_seq`, `trid_sign`, `trid_media_gb`, `trid_term_no`, `trid_svr_no`, `trid_svr_seq`, `pub_ip`, `prt_ip`, `prc_brno`, `brno`, `term_no`, `lan_gb`, `prc_time`, `msg_code`, `msg_gb`, `compress_re`, `fnc_key`, `rec_cnt`, `exp_prc`, `reserve`, `con_gb`, `con_key`, `vlen_len`, `vhd_len`, `bmsg_len`, `vlen`, `vhd`, `bmsg`, `data`, **`timestamp`** (quoted identifier in legacy DDL).

### User scenario

1. A DBA applies updated **PB FEP DDL** and **partition** scripts to the PB database (or schema), then loads / migrates data that matches the wire layout.
2. The log ingestion path (FEP / batch / app) inserts rows using **wire column names** (or an agreed mapping) such that **`brodid`** is searchable with the same **loginId** filter behavior as today.
3. An operator runs **daily partition maintenance** (rolling window, no DEFAULT partition per existing policy) without partition key type errors.
4. End users open **PB FEP v1.0.0** or **v2.0.0** search screens, run searches with date range, **loginId**, optional media/TR filters, sort, and pagination; results display **unchanged column labels and wireframe keys** (for v2).

### Expected outcome

- **`pb_send` / `pb_recv`** schemas **reflect wire layout** (types and names as agreed; see §2 for PostgreSQL reserved / quoting rules and **`timestamp`** naming).
- **`brodid`** is the stored wire user key; **`loginId`** filter semantics match **`brodid`** (same as current `user_id` filter behavior against that column).
- **RANGE daily partitioning** uses a **single, typed, NOT NULL** partition column; **varchar wire time fields alone** are not used as partition bounds without a documented parse / companion typed column.
- **API JSON shapes** for existing endpoints remain **backward compatible** for clients: either unchanged keys via **SQL aliases** and/or **server-side mapping** from new physical columns to existing response field names (`log_timestamp`, `tr_code`, `login_id` in wireframe, etc.).
- **Contract and API docs** are updated if any **documented** row field names, mappings, or table column lists change (`docs/contract.md`, `docs/api-definition.md`, `specs/log-db-pb-fep-log-search.spec.yaml` as applicable).
- **Search tests** (Backend unit, Frontend unit where applicable, DB apply smoke) pass per §3.

**Design references for search field definitions (traceability):** Where search/filter behavior ties to screen field catalogs, implementers should cross-check `docs/design/search-fields-by-screen.md` and `docs/design/search-field-definition-items.md`. This requirement is **data-layer alignment** first; **§2.4 cross-screen filter layout pattern** does not apply unless product explicitly extends scope to other log search screens.

---

## 2. Design

### 2.1 Security review (optional)

- **PII / logs:** PB FEP payloads may contain sensitive data; schema expansion must not weaken access control. **Screen access** and **`logType=pb_feplog`** enforcement stay per existing contract.
- **Decrypt:** Existing decrypt behavior for PB payloads remains per `CryptoUtil` / contract; new binary/text columns must not bypass redaction rules.
- [ ] Security review performed (recommended before Step 4 if new columns store PII categories not previously held)

### Technical design

#### Codebase summary (current baseline)

- **DB:** `schema_pb_fep.sql` defines simplified `pb_send` / `pb_recv` with `log_timestamp TIMESTAMP NOT NULL`, `media_code`, `tr_code`, `user_id`, `ip_address`, `user_agent`, `request_data`, `response_data`, `status_code`, `response_time`, `error_message`, `session_id`, `device_type`, audit columns.
- **Migrations:** `migrate-pb-send-recv-partitioning-20260408.sql` and follow-ups build **daily RANGE** children on **`log_timestamp`**; `create-pb-send-recv-daily-partitions-only.sql` assumes the same column set for (re)partitioning.
- **Backend — `LogDbService.executePbFeplogUnionSearch`:** Builds `SELECT` lists of **simplified column names**, `WHERE` on `log_timestamp`, `media_code`, `tr_code`, `user_id` (from **`request.getLoginId()`**), `ORDER BY` via **`buildPbFeplogOrderBy`** with allowlist **`PB_FEPLOG_SORTABLE_COLUMNS`**. **`normalizePbFeplogSortField`** maps legacy wire **names** (e.g. `brodid` → `user_id`, `prc_time` → `log_timestamp`, `bmsg` → `error_message`, …) to **physical** columns for ordering.
- **Wireframe:** **`mapPbFepRowToWireframe`** maps DB rows to **stable UI keys** (`login_id` ← `user_id`, `msg_code` ← `status_code`, `bmsg` ← `error_message`, `log_ch_cd` ← `device_type`, `src_ip` ← `ip_address`, `app_id` ← `session_id`, …). Contract text in `docs/contract.md` describes this mapping.
- **Frontend:** `LogTable.js` defines legacy vs wireframe column **keys**; `LogGrid.js` chooses `/search` vs `/pb-fep-log-search` by screen id.

#### Problem analysis

1. **Semantic gap:** Simplified columns do not match **operational / wire** exports; DBAs and FEP tools expect **wire names** (`media_gb`, `brodid`, …).
2. **Partitioning vs wire types:** Legacy may use **`log_time` string** and a quoted **`"timestamp"`** column; PostgreSQL **RANGE partitioning** requires **typed** bounds—design must choose one canonical **`timestamp`/`timestamptz`** column for partitions and document population.
3. **API stability:** Changing physical names breaks raw **`/search`** row maps unless the service layer selects **aliases** or maps in Java to **preserve** existing JSON keys.
4. **Send vs recv:** Product must confirm whether **`pb_send`** has **identical** wire columns to **`pb_recv`** or an **asymmetric** subset; asymmetry affects UNION column lists and **NOT NULL** rules.

#### Solution approach

**DB:**

- Define **`pb_send`** / **`pb_recv`** DDL aligned to wire layout: column names, types (VARCHAR lengths, numeric scales, BYTEA/TEXT for payloads per wire spec). Reserve handling: legacy quoted `"timestamp"` must become a **non-reserved** PostgreSQL name (e.g. `wire_ts`, `legacy_timestamp`, or fold into **`log_timestamp`** if duplicate).
- **`brodid`:** Store wire broker id in column **`brodid`**; document that **application filter** `loginId` maps to **`brodid`** (replacing **`user_id`** as the physical column for this filter). If product requires keeping a **`user_id`** synonym, use a **generated column** or **view**—prefer a single source of truth to avoid drift.
- **Partition key:** Use **`log_timestamp TIMESTAMP NOT NULL`** (or **`timestamptz`** if timezone rules are explicit) as **RANGE** partition key, **provided** it is **filled for every row** from **`prc_time`**, parsed **`log_time`**, and/or legacy **`timestamp`** per documented rules. Document **one** authoritative rule for ingest and migration.
- **Remake** `create-pb-send-recv-daily-partitions-only.sql` (and any dependent migration snippets) so **`CREATE TABLE … PARTITION OF`** and index definitions reference the **new** parent column list. Keep operational policy: **no DEFAULT partition** unless product changes policy (out of scope unless stated).
- **Data migration:** Script(s) from old simplified tables → new layout (column mapping table in §2 appendix style in implementation notes). **H2 test schema** (`backend/src/test/resources/sql/logdb-service/h2-schema.sql`) must align so CI reflects production shapes.
- **`check-db.sh`:** Adjust column-count / index expectations if validation thresholds were tied to simplified schemas.

**Backend:**

- Update **`executePbFeplogUnionSearch`** `SELECT` / `WHERE` / `ORDER BY` to use **new physical names** while **preserving**:
  - **Legacy `/search`:** Response keys expected by UI/tests (`log_timestamp`, `media_code`, …) via **`AS` aliases** or **post-map**—contract must list which keys are **stable aliases**.
  - **Wireframe:** Output of **`mapPbFepRowToWireframe`** must still expose the same **wireframe keys**; adjust mapping to read **`brodid`**, wire **`bmsg`**, **`msg_code`**, **`pub_ip`**, etc., as per final column plan.
- Refresh **`PB_FEPLOG_SORTABLE_COLUMNS`** and **`normalizePbFeplogSortField`** so allowlisted sort fields map to **new** physical columns (and legacy sort **aliases** still work).
- **Decrypt path:** If encrypted payloads move from `request_data`/`response_data` to wire **`data`** / **`bmsg`** / chunks, update **`decryptData`** / keyword behavior consistently and document.

**Frontend:**

- **No label/key breakage:** If API preserves **exact** response keys, Frontend may need **no** change. If contract introduces **new** optional fields for display, update **`LogTable`** / formatters **only** as needed while keeping existing tests green.
- Verify **`getPbFeplogRowKey`** uniqueness with new data (still uses `id`, `log_timestamp`, `tr_code`, `user_id`/`login_id`).

**Contract / spec:**

- Update **`docs/contract.md`** PB FEP sections: table column inventory, wireframe row mapping, and any **alias** rules.
- Update **`docs/api-definition.md`** and **`specs/log-db-pb-fep-log-search.spec.yaml`** if response field sourcing changes.

**Cursor tool update targets**

- `.cursor/skills/db-domain/SKILL.md` — PB FEP partitioning / script names / partition key column.
- `.cursor/skills/log-search-domain/SKILL.md` — PB FEP column / pool / mapping notes.
- No workflow rule change unless `setup.sh` ordering or delegation changes (then run treemap policy if applicable).

### Affected scopes and change targets (verification)

Per `docs/workflow/REQUIREMENTS-CHANGE-TARGET-CHECKLIST.md`:

| Scope | Affected? | §2 subsection and change file list complete? |
|-------|------------|-----------------------------------------------|
| Backend | Yes | Yes |
| Frontend | Maybe (minimal if API keys stable) | Yes |
| DB | Yes | Yes |
| Contract / Spec | Yes | Yes |
| Cursor tools (skills) | Yes | Yes |

**§2.4 Search/filter UI pattern:** **Not applicable** as a cross-screen alignment project. This requirement does **not** require the §2.4 user-block width / multi-screen form parity table **unless** product extends scope to align PB FEP search forms with other log search screens.

### Planned change file list (expected change targets)

**(Planned at authoring. Implementing agent (Step 4) confirms or amends this list when implementation is complete.)**

#### DB

- `backend/src/main/resources/db/schema_pb_fep.sql` — Wire-aligned `pb_send` / `pb_recv` definitions; indexes; triggers.
- `backend/src/main/resources/db/migrate-pb-send-recv-partitioning-20260408.sql` — Revisit embedded DDL if still used for greenfield; or superseding migration for wire layout + partition attach.
- `backend/src/main/resources/db/migrate-pb-send-recv-monthly-to-daily-20260414.sql` — Align with new parent definition if monthly remnants exist.
- `backend/src/main/resources/db/create-pb-send-recv-daily-partitions-only.sql` — Remake for new columns; **partition key = typed `log_timestamp` (or agreed `timestamptz`)**; window logic unchanged unless product dictates.
- `backend/src/main/resources/db/setup.sh` / `backend/src/main/resources/db/check-db.sh` — Ordering, validation thresholds.
- `backend/src/main/resources/db/init-data*.sql` / seeds — Insert column lists for PB FEP test data.
- `backend/DB_SETUP_GUIDE.md` — Partition key and script description.

**DB implementation confirmed (Step 4):** Wire-aligned DDL in `schema_pb_fep.sql`; `migrate-pb-send-recv-partitioning-20260408.sql`, `create-pb-send-recv-daily-partitions-only.sql`, and `migrate-pb-send-recv-monthly-to-daily-20260414.sql` (structure-agnostic; no column-list change) aligned; `init-data.sql`, `init-data-local-decrypt-test-pbfep.sql`, `migrate-pb-fep-pagination-bmsg-sample-20260330.sql`, `check-db.sh` (§6i column threshold) updated. Skills: `.cursor/skills/db-domain/SKILL.md`. **H2 / Java tests:** Backend owns `h2-schema.sql` — must mirror this layout + SQL aliases in `LogDbService`.

#### Backend

- `backend/src/main/java/com/logmng/service/LogDbService.java` — Union SQL, ORDER BY allowlist, wireframe mapping, decrypt field sources.
- `backend/src/test/java/com/logmng/service/LogDbServiceTest.java` / `LogDbServiceDataSourceRoutingTest.java` / stubs — Align with new columns.
- `backend/src/test/resources/sql/logdb-service/h2-schema.sql` — H2 DDL for tests.

**Backend implementation confirmed (Step 4, wire SQL + tests):** `LogDbService.java` — `PB_FEPLOG_WIRE_SELECT_BODY` projects wire columns to stable legacy JSON keys (`brodid AS user_id`, `media_gb AS media_code`, `pub_ip AS ip_address`, `vlen AS request_data`, `data AS response_data`, `msg_code AS status_code`, `bmsg AS error_message`, `prt_ip AS session_id`, `log_ch_cd AS device_type`, …); `WHERE` uses `brodid`, `log_timestamp`, `media_gb`, `tr_code`; `normalizePbFeplogSortField` extended for `media_gb`, `vlen`, `data`; decrypt paths use JDBC string coercion for `vlen`/`data` payloads. Tests: `LogDbServiceTest`, `LogDbServiceDataSourceRoutingTest`; fixtures `h2-schema.sql`, `insert-pb-*.sql`, `sql/logdb-routing/seed-pb-send-one.sql`.

#### Frontend (conditional)

- `frontend/src/components/LogTable.js` / `LogGrid.js` — Only if response keys or sort keys change.
- Associated `*.test.js` files.

#### Contract / spec

- `docs/contract.md` — PB FEP table + API mapping.
- `docs/api-definition.md` — If row shape text changes.
- `specs/log-db-pb-fep-log-search.spec.yaml` — Wireframe row field sourcing.

---

## 3. Test approach

### Test case list (required)

**Domain notes:** Apply log-search expectations from `.cursor/skills/log-search-domain/SKILL.md` (contract alignment, PB pool / `search_path`). **Mandatory automated coverage:** Each TC below marked **Unit** must get a **JUnit/Jest** (or documented SQL smoke) implementation in Step 4.

| ID | Scope | Type | Scenario (input / condition) | Expected result | Verification |
|----|-------|------|------------------------------|-----------------|--------------|
| TC-01 | DB | Normal | Apply updated `schema_pb_fep.sql` on clean PB schema | `pb_send` / `pb_recv` exist with wire-aligned columns; **`brodid`** present; typed **`log_timestamp`** NOT NULL | Manual / setup script |
| TC-02 | DB | Normal | Run remade `create-pb-send-recv-daily-partitions-only.sql` on partitioned parents | Daily children exist for window; **no DEFAULT** partition; inserts outside window fail as per policy | Manual |
| TC-03 | DB | Edge | Migrate sample rows from old simplified backup to new layout | All rows land in correct **daily** partition; **`log_timestamp`** populated per migration rule | Manual / SQL |
| TC-04 | Backend | Normal | `POST .../search` with `logType=pb_feplog`, date range, `loginId` = known **`brodid`** | Rows filtered; JSON keys **unchanged** from contract baseline (`log_timestamp`, `media_code`, `tr_code`, `user_id`, …) | Unit (mvn test) |
| TC-05 | Backend | Normal | Same as TC-04 with optional `mediaCode`, `trCode` | Filters apply against wire **`media_gb`/`tr_code`** (or aliased columns) | Unit |
| TC-06 | Backend | Normal | `sortSpecs` / `sortField` using legacy aliases (`brodid`, `prc_time`, `bmsg`, `pub_ip`) | Order by resolves to correct physical columns; **no SQL injection** | Unit |
| TC-07 | Backend | Normal | `searchPbFepLogWireframe` / `mapPbFepRowToWireframe` | Wireframe keys (`login_id`, `msg_code`, `bmsg`, `log_ch_cd`, `src_ip`, `app_id`, `data`, …) match **contract**; `login_id` reflects **`brodid`** | Unit |
| TC-08 | Backend | Edge | `decryptData=true` with keywords | Decrypt targets correct ciphertext columns per new layout; no ciphertext leaked as plaintext on failure | Unit |
| TC-09 | Integration | Normal | Health + `POST /api/logs/db-refactored/search` and `/pb-fep-log-search` with auth | **200**; pagination totals consistent | Integration (curl / manual) |
| TC-10 | Frontend | Normal | `LogGrid.test.js` — wireframe URL and row expand keys | Still passes; **`getPbFeplogRowKey`** stable | Unit (npm test) |
| TC-11 | Frontend | Normal | `SearchForm.test.js` (pb-fep-log-search) | Still passes | Unit (npm test) |
| TC-12 | Frontend | Normal | `LogTable.test.js` layout variants | Still passes | Unit (npm test) |
| TC-13 | Backend | Regression | `LogDbServiceDataSourceRoutingTest` | PB pool routing still valid | Unit (mvn test) |
| TC-14 | DB | Regression | `check-db.sh` section **6i** (PB partition layout) | Exits clean for new column counts / partition naming | Manual |
| TC-15 | Contract | Normal | Grep / review: `docs/contract.md` matches implemented mappings | Docs and code synchronized | Manual review |

**§3 test case count:** **15** (TC-01 … TC-15).

### Test scenarios

#### Scenario A: DBA rollout

1. Apply DDL + migrations + partition script.  
2. Load wire-format sample data.  
3. Confirm partition pruning / child attachment with `EXPLAIN` on bounded date query (document in §5 if needed).

#### Scenario B: Developer regression

1. `cd backend && mvn test`  
2. `cd frontend && npm test -- --watchAll=false`  
3. Fix failures only via intended mapping/SQL changes.

### Test data

- Provide **executable INSERT** samples with **non-null `log_timestamp`**, **`brodid`**, and representative **`tr_code` / `media_gb`** for both `pb_send` and `pb_recv`.  
- Include at least one row for **send** and one for **recv** branch for UNION tests.

### Test environment

- Frontend: `http://localhost:3001` (per contract)  
- Backend: `http://localhost:9200`  
- Database: PostgreSQL 16, PB schema per `docs/contract.md`

---

## 4. Checklist

### Frontend verification

- [x] PB FEP v1 (legacy **pb-feplog**): browser MCP — login, form visible, search submitted with date range 2026-04-13 + Login ID `local_decrypt` (TR Code filled `%` for required field); **rows not verified** — backend returned error (see §5 TC-09 / browser console).
- [ ] PB FEP v2 wireframe screen (**pb-fep-log-search**) navigation + grid (not exercised in this run; blocked by same backend failure on authenticated curl).
- [ ] No undefined column keys in table bindings (not re-validated beyond unit tests this run)

### Backend verification

- [x] Unit tests updated and passing
- [x] ORDER BY allowlist enforced (covered by unit tests; no separate manual sign-off)

### Integration

- [x] Health + DB connectivity after restart (`/api/health`, `/api/db/test`)
- [ ] End-to-end search with **wire-aligned** PB schema (TC-09): **blocked on verifier DB** — PostgreSQL still has legacy 16-column `pb_send`/`pb_recv` (see §5); apply `schema_pb_fep.sql` + runbook before re-test.

### Documentation

- [x] Requirement doc completed (§5 recorded)
- [x] Contract / api-definition / spec updated in same change set as behavior
- [x] PB FEP wire DB apply runbook: [`docs/operations/PB-FEP-WIRE-DB-APPLY.md`](../../docs/operations/PB-FEP-WIRE-DB-APPLY.md) (linked from [`backend/DB_SETUP_GUIDE.md`](../../backend/DB_SETUP_GUIDE.md) — PB FEP partitioning section)

---

## 5. Test results

### Test run date

- 2026-04-14 — **full QA verification pass** (automated tests, restart, curls, `check-db.sh`, browser MCP). **E2E PB search blocked** by local PostgreSQL still on **legacy** `pb_send`/`pb_recv` layout (see blocker).

### Pass / fail summary

| Area | Result |
|------|--------|
| Backend `mvn test` | **Pass** (exit 0) |
| Frontend `npm test -- --watchAll=false` | **Pass** (exit 0) |
| Restart + health + frontend HTTP | **Pass** |
| `GET /api/db/test` | **Pass** (`connected: true`; PB counts reported) |
| TC-09 curl + UI PB search | **Blocked / Fail** on verifier DB (missing wire columns → HTTP 500) |
| `check-db.sh` | **Pass** exit 0 with **warning** (column count vs wire expectation) |
| Browser MCP (§3.5) | **Pass** for shell + login + PB FEP(old) search **attempt**; **no data grid** due to same server error |

### Blocker (environment) — action for operators

- **Symptom:** `POST /api/logs/db-refactored/search` with `logType=pb_feplog` returns **500** `INTERNAL_SERVER_ERROR`. Backend log: `PSQLException: ERROR: column "media_gb" does not exist` (`LogDbService.executePbFeplogUnionSearch`).
- **Cause:** Connected PostgreSQL `pb_send` / `pb_recv` are still **simplified** (16 columns: `media_code`, `user_id`, …). `backend/src/main/resources/db/check-db.sh` reports: `pb_send 테이블 컬럼 수: 16 (예상: 45개 이상; 와이어 정렬 schema_pb_fep)`.
- **Fix:** Apply wire-aligned DDL + data per **`docs/operations/PB-FEP-WIRE-DB-APPLY.md`** / `schema_pb_fep.sql`, then re-run TC-09 and browser PB search.

### Auth note (dev seed — not a secret)

- `init-data.sql` comment “user1 = **20260001**” refers to **`app_user.id`**, not local-login **`employeeNumber`**.
- **`POST /api/auth/login`** (local mode) with `{"employeeNumber":"20260001","password":"user123"}` → **`success: false`**, `code: INVALID_CREDENTIALS` (no session cookie; **20260001** is `app_user.id`, not 사번).
- Working session: **`{"employeeNumber":"20261001","password":"user123"}`** — matches `migrate-app-user-employee-number-display-backfill-20260409.sql` backfill for `user1`. Capture **`Set-Cookie: JSESSIONID`** for curl.

### API integration (curl) — snippets

1. **Login (success):**  
   `curl -sS -c cookies.txt -H "Content-Type: application/json" -d '{"employeeNumber":"20261001","password":"user123"}' http://localhost:9200/api/auth/login`  
   → `{"success":true,...}` and `JSESSIONID` in cookie jar.

2. **`POST /api/logs/db-refactored/search`** (cookie `-b cookies.txt`):  
   Body example: `{"logType":"pb_feplog","startDate":"2026-04-13 00:00:00","endDate":"2026-04-13 23:59:59","loginId":"local_decrypt","page":1,"pageSize":25}`  
   → **`success": false`, `code": "INTERNAL_SERVER_ERROR`** (verifier DB legacy schema).

3. **`POST /api/logs/db-refactored/pb-fep-log-search`:** same cookie, body `{"startDate":"2026-04-13 00:00:00","endDate":"2026-04-13 23:59:59","loginId":"local_decrypt","page":1,"pageSize":25}`  
   → **401** without cookie; with cookie → same **500** class failure as search when DB mismatched.

### Manual / DB

- **`backend/src/main/resources/db/check-db.sh`:** exit **0**; **§6i** PB partition check **skipped** (`pb_send` not partitioned parent `relkind=SETp` on this instance); **§7** column-count **warning** (16 vs wire ≥45).
- **`psql` smoke** `SELECT COUNT(*) FROM pb_send WHERE reserve='LDPT';` → **not run / N/A** — column `reserve` does not exist on legacy table (expected after wire migration only).

### Browser MCP (verify.md §3.5)

- **Tool:** `cursor-ide-browser` (project policy). **URL:** `http://localhost:3001`. **`browser_resize`:** 1920×1080. **Flow:** `browser_navigate` → `browser_lock` → wait → snapshot → fill → click.
- **Login:** 사번 `20261001`, 비밀번호 dev seed `user123` → success (console: 로그인 성공).
- **Screen:** sidebar **PB FEP(old)**; heading **PB FEP(old)**; set **시작/종료** `2026-04-13T00:00` / `2026-04-13T23:59:59`, **Login ID** `local_decrypt`, **TR Code** `%` (required indicator), **검색**.
- **Result:** brief “데이터를 불러오는 중…” then no grid data in snapshot; **console** `검색 중 오류 발생` (debug) — consistent with backend 500 above. No captcha / field-name blocker.

### §3 test case matrix (TC-01–TC-15)

| ID | Result | Evidence (one line) |
|----|--------|---------------------|
| TC-01 | **Skip** | QA host DB not clean-applied wire DDL; `check-db` lists legacy columns only |
| TC-02 | **Skip** | Partition parent not present (`check-db` 6i skip); not re-run |
| TC-03 | **Skip** | No wire migration sample on this DB |
| TC-04 | **Pass** | `mvn test` (LogDbService / H2 wire schema) |
| TC-05 | **Pass** | `mvn test` |
| TC-06 | **Pass** | `mvn test` |
| TC-07 | **Pass** | `mvn test` |
| TC-08 | **Pass** | `mvn test` |
| TC-09 | **Blocked** | Authenticated curl + UI: **500** / `media_gb` missing on PostgreSQL |
| TC-10 | **Pass** | `npm test` |
| TC-11 | **Pass** | `npm test` |
| TC-12 | **Pass** | `npm test` |
| TC-13 | **Pass** | `mvn test` |
| TC-14 | **Pass (warn)** | `check-db.sh` exit 0; PB column-count warning documents drift |
| TC-15 | **Skip** | Contract grep not repeated in this QA run |

### Command log (automated + verify)

| Check | Command / step | Result | Notes |
|-------|----------------|--------|--------|
| Backend unit tests | `cd backend && mvn test` | **Pass** (exit 0) | Full suite |
| Frontend unit tests | `cd frontend && npm test -- --watchAll=false` | **Pass** (exit 0) | exit 0 on verifier host |
| Verify: restart | `./scripts/dev-services.sh all restart` | **Pass** (exit 0) | First health curl after restart needed retry (~8 s insufficient once; subsequent `curl` → 200) |
| Verify: backend health | `curl -s http://localhost:9200/api/health` | **Pass** | 200, `success: true` |
| Verify: frontend HTTP | `curl -s -o /dev/null -w "%{http_code}" http://localhost:3001` | **Pass** | 200 |
| Verify: DB connectivity | `curl -s http://localhost:9200/api/db/test` | **Pass** | `connected: true`; e.g. `pb_send_count` / `pb_recv_count` 189 each, `pbUsesPrimaryFallback: true` |
| Browser §3.5 | cursor-ide-browser | **Pass** (UI path) / **Fail** (data) | Login + PB FEP(old) search; error state matches TC-09 |

**Follow-up (generator, PB FEP seed, runbook — 2026-04-14):**

- **`LocalDecryptSampleSeedGenerator`:** Aligned with wire-layout PB FEP sample columns so regenerated `init-data-local-decrypt-test-pbfep.sql` stays consistent with `schema_pb_fep.sql` / decrypt tests.
- **Seed:** `backend/src/main/resources/db/init-data-local-decrypt-test-pbfep.sql` — refreshed from the generator for PB FEP local decrypt fixtures (ImageLog seed file left unchanged when diff was ciphertext-only churn).
- **Runbook path:** Operator manual apply order and verification: **`docs/operations/PB-FEP-WIRE-DB-APPLY.md`** (cross-linked from **`backend/DB_SETUP_GUIDE.md`** in the PB FEP daily partitioning section).
- **Regression:** `cd backend && mvn test` — **Pass** (exit 0) on 2026-04-14 after this follow-up (ImageLog seed file not included in commit; ciphertext-only churn reverted).

### Checklist (this requirement)

- [x] Backend: unit tests passing (`mvn test`)
- [x] Frontend: unit tests passing where applicable (`npm test`)
- [x] Integration smoke: health + DB test API after restart
- [ ] Full E2E PB FEP search on **wire-aligned** PostgreSQL (blocked until DB apply; optional re-run after migration)

---

## 6. Final version (Korean) — 요약

**목적:** `pb_send`·`pb_recv` 테이블을 레거시 PB FEP **와이어 포맷 컬럼**에 맞추고, **`brodid` = 로그인/검색용 사용자 키(`loginId` 필터)** 로 동작하도록 한다. **일 단위 RANGE 파티션**은 **실제 날짜/시간 타입** 컬럼(권장: 채워진 **`log_timestamp`**)을 기준으로 하며, 문자열 **`log_time`** 만으로는 파티션 경계를 정하지 않고, **파싱/적재 규칙**을 설계에 명시한다. **기존 PB FEP 검색 화면(v1/v2)** 및 **검색 관련 자동 테스트**가 깨지지 않도록 API JSON 키(또는 동일 의미의 별칭)를 유지한다. **`docs/contract.md`** 등 계약 문서 갱신이 필요할 수 있다.

---

## Appendix — Partition key decision (normative for implementers)

- **RANGE partition column:** **`log_timestamp`** as **`TIMESTAMP NOT NULL`** (alternatively **`TIMESTAMPTZ`** if timezone semantics are specified and applied consistently in ingest).  
- **Rationale:** PostgreSQL **RANGE** partitioning requires **typed** bounds; varchar wire fields are **not** suitable as partition keys without explicit cast rules that are hard to validate and index.  
- **Wire fields `log_time` / quoted `timestamp`:** Store as legacy fidelity columns if needed; **populate `log_timestamp`** (same instant) for **query + partition** using a **single documented** rule set at insert/migration time.
