# 20260415 - PB FEP log_time 20-character microsecond lexical format

## 1. User requirement

### Requirement description

PB FEP physical tables **`pb_send`** and **`pb_recv`** must store the canonical product-facing time column **`log_time`** as a **fixed lexical numeric string** of **exactly 20 characters**, using format **`yyyyMMddHHmmssSSSSSS`** (calendar date 8 + time-of-day 6 + fractional second 6 = **microseconds**, zero-padded). This replaces the prior **14-character** second-resolution wire form (`yyyyMMddHHmmss`) stored in **`VARCHAR(15)`**, and aligns database **RANGE** partition bounds, application date-filter normalization, API responses, and UI display with the same semantics end-to-end.

Downstream systems ingesting into these tables must emit **`log_time`** in this 20-character form; operators upgrading existing databases must migrate column width, partition definitions, and **existing row values** so lexicographic ordering and daily partition routing remain correct.

### User scenario

1. An operator provisions or upgrades the PB FEP schema so **`log_time`** is **`VARCHAR(20)`**, daily partitions use **20-character** `FROM`/`TO` bounds, and legacy rows are normalized to 20 characters where needed.
2. A FEP ingest process writes rows with **`log_time`** = `20260415143025123456` (example: 2026-04-15 14:30:25.123456) — always 20 digits, microseconds padded.
3. A user searches PB FEP logs (`pb_feplog`) by date range; the backend compares **`log_time`** lexicographically using normalized bounds in the **same** 20-character space.
4. The user views search results on **pb-feplog** or **pb-fep-log-search**; **`log_time`** displays in a human-readable form without breaking sort or column layout.

### Expected outcome

- **`log_time`** in **`pb_send`** / **`pb_recv`** is **`VARCHAR(20)`** (or equivalent product-approved width that accepts exactly this 20-digit pattern), and all **RANGE (log_time)** partition **lower/upper bounds** use **20-character** strings: for calendar day **D**, inclusive lower bound **`YYYYMMDD || '000000000000'`** and exclusive upper bound **`YYYYMMDD(next day) || '000000000000'`** (confirm **total length 20** for both `FROM` and `TO` literals).
- Backend **`LogDbService`** uses a **microsecond-capable** formatter pattern aligned with **`yyyyMMddHHmmssSSSSSS`**, and maps **`LocalDateTime`** nanoseconds to **six** fractional digits (no reliance on **`withNano(0)`** alone where that strips sub-second precision required by the product).
- **Contract and API docs** describe the wire/API **`log_time`** lexical format (20 digits, microsecond padding) for PB FEP; tests and QA seeds use the same format.
- **Frontend** formats PB FEP **`log_time`** for display (and any client-side assumptions about string length or parsing) **without** mis-parsing 20-digit values or breaking tables.
- **Main agent does not implement** this requirement directly; **Backend**, **DB**, **Frontend**, and **Contract** agents implement per this document and project handoff rules.

### Expert input (orchestrated for §1 / §2)

Synthesized constraints (parallel expert roles; no substitute for Step 4 verification in code):

| Role | Input |
|------|--------|
| **Security** | No new PII field; time format change is operational. Ensure docs do not encourage logging full wire payloads; no change to decrypt scope by this requirement alone. |
| **Contract / API** | **`log_time`** remains the canonical PB FEP time key; update **`docs/contract.md`** and **`docs/api-definition.md`** to state **20-digit lexical** storage/echo semantics for PB FEP where today they imply second-only or unspecified length. |
| **DBA** | **Lexicographic** `RANGE` on **`text`/`varchar`** requires consistent width/padding for all rows and bounds; plan **data backfill** (e.g. pad legacy 14-digit values to 20 with **`000000`** microsecond suffix) and **partition recreation** or migration so bounds use **`…000000000000`** … **`…000000000000`** (next day). |
| **Backend** | Replace **`PB_FEPLOG_TIME_LEXICAL_FORMATTER`** pattern and **`toPbFeplogLogTimeLexical`** (and any duplicate normalization) to emit/consume 20-char microsecond form; align **WHERE** clauses for date filters with partition bounds. |
| **Frontend** | **`formatTime`** and PB FEP table cells must handle **20-digit** `log_time` (today may fall through to **`Date` parsing** incorrectly for compact strings). |
| **QA** | Extend **H2** fixtures, **`seed-pb-fep-qa-known-data-20260414.sql`** (or successor), and **`LogDbServiceTest`** scenarios for microsecond **`log_time`**. |

---

## 2. Design

### 2.1 Security review (optional)

- [ ] Security review performed (check if applicable)
- **Risks**: Low for this change; avoid documenting or logging raw sensitive **`data`** / **`bmsg`** in migration scripts beyond what existing processes already do.
- **Acceptance**: No broadening of decrypt or access scope; PB FEP **`log_time`** remains non-secret metadata.

### Technical design

#### Problem analysis

1. **Schema and partitions**: **`log_time`** is currently **`VARCHAR(15)`** with daily partition bounds built as **`YYYYMMDD || '0000000'`** (7 trailing zeros) and next-day upper bound in the same pattern — **15-character** strings — in scripts such as **`create-pb-send-recv-daily-partitions-only.sql`** and **`migrate-pb-send-recv-partitioning-20260408.sql`**. Sub-day ordering was not representable in the **15-char** bound pattern aligned with **14-digit** second times; moving to **20-char** microsecond **`log_time`** requires **12** trailing zeros for “start of day” in the **`YYYYMMDD` + `HHmmss` + `SSSSSS`** model: **`YYYYMMDD || '000000000000'`** (length **20**).
2. **Backend normalization**: **`LogDbService`** defines **`PB_FEPLOG_TIME_LEXICAL_FORMATTER`** as **`"yyyyMMddHHmmss"`** and **`toPbFeplogLogTimeLexical`** uses **`withNano(0)`**, producing **second** resolution only — inconsistent with **microsecond** wire format.
3. **Data migration**: Existing rows may store **`log_time`** as **14-digit** `yyyyMMddHHmmss` in a **VARCHAR(15)** column. **Lexicographic** comparison mixes unequal lengths unless values are **normalized** to **20** digits (e.g. append **`000000`** for missing fractional part) before relying on sort/partition predicates.
4. **Frontend display**: PB FEP **`log_time`** display paths (e.g. **`LogTable.js`** **`formatTime`**) may not parse **20-digit** compact timestamps and can show incorrect or opaque values.
5. **Documentation drift**: **`docs/contract.md`** PB FEP wire section describes canonical **`log_time`** but must explicitly record **20-character microsecond** lexical format after this change.
6. **Cursor domain skill**: **`.cursor/skills/db-domain/SKILL.md`** currently summarizes PB FEP partitioning with **`log_timestamp`**-era language in places; per **`docs/workflow/REQUIREMENTS-AUTHORING-WORKFLOW.md` §1.4**, update that skill when the DB domain model for **`log_time`** width/partition bounds changes so agents stay consistent.

#### Diagnostic phase (mandatory for error/bug fix only)

*Not applicable — feature/contractual schema alignment requirement.*

#### Solution approach

**DB:**

- Introduce a **forward migration** (and update **canonical DDL** sources) to set **`log_time`** to **`VARCHAR(20)`** on **`pb_send`** / **`pb_recv`** parent definitions and any **idempotent** recreate paths in **`schema_pb_fep.sql`**, **`create-pb-send-recv-daily-partitions-only.sql`**, **`migrate-pb-send-recv-partitioning-20260408.sql`**, **`migrate-pb-send-recv-remove-log-timestamp-20260414.sql`**, and **`migrate-pb-send-recv-monthly-to-daily-20260414.sql`** wherever **`log_time`** width or **`FOR VALUES FROM … TO …`** literals appear.
- **Partition bounds (daily)**: For each day **d**, use **`FROM (to_char(d,'YYYYMMDD') || '000000000000')`** **`TO (to_char(d+1,'YYYYMMDD') || '000000000000')`** — verify **string length 20** for both bounds. Replace prior **`'0000000'`** (7 zeros) / **15-char**-style bounds.
- **Operator migration strategy** (spell out for runbooks):
  1. **Quiesce or schedule** ingest during migration window as required by operations.
  2. **Normalize** existing **`log_time`** values to **20 digits** (define rule: e.g. right-pad **14-digit** values with **`000000`**; handle any anomalous lengths per DBA validation queries).
  3. **ALTER** column length on partitioned parents (**PostgreSQL** typically propagates to partitions; **confirm** on target version — if not, alter children or use detach/recreate per project DBA procedure).
  4. **Recreate or adjust** daily child partitions so **bounds** match the **20-char** pattern; **detach/drop/create/attach** as needed for existing deployments (scripted, idempotent where possible).
  5. **Re-run** **`check-db.sh`** PB FEP partition checks and add/adjust validation for **`log_time`** width if the project adds checks.
  6. **Update** seeds: **`seed-pb-fep-qa-known-data-20260414.sql`** — replace **14-digit** **`log_time`** and range predicates with **20-digit** values and bounds consistent with the new lexical space.

**Backend:**

- Update **`PB_FEPLOG_TIME_LEXICAL_FORMATTER`** to **`yyyyMMddHHmmssSSSSSS`** (or equivalent **`DateTimeFormatterBuilder`** / **`appendFraction`** usage) and implement **`toPbFeplogLogTimeLexical`** so **nanoseconds** map to **six** fractional digits (**truncate to microseconds** or **round** — **must** match product/FEP ingest; default recommendation: **truncate to microseconds** for determinism unless product specifies rounding).
- Audit **all** PB FEP SQL fragments that compare **`log_time`** to literals or build range endpoints; align with **20-char** bounds.
- Update **`backend/src/test/resources/sql/logdb-service/h2-schema.sql`** (**`VARCHAR(20)`**) and any **`LogDbServiceTest`** / JSON expectations.

**Frontend:**

- Update PB FEP display formatting so **20-digit** **`log_time`** renders as a stable human-readable timestamp (e.g. **`yyyy-MM-dd HH:mm:ss.SSSSSS`** or product format); avoid relying on **`new Date(string)`** for raw compact digits if parsing is unreliable.

**Contract:**

- Update **`docs/contract.md`** (PB FEP wire / **`log_time`** subsection) and **`docs/api-definition.md`** (PB FEP search sections) to specify **20-character** **`log_time`** lexical format and microsecond padding rules.

**Cursor skills:**

- Update **`.cursor/skills/db-domain/SKILL.md`** PB FEP bullet to reflect **`log_time VARCHAR(20)`**, **daily RANGE** bounds pattern **`…000000000000`**, and reference this requirement doc.

### Affected scopes and change targets (verification)

| Scope | Affected? | §2 subsection and change file list complete? |
|-------|------------|-----------------------------------------------|
| Backend | Yes | Yes |
| Frontend (config UI + view screen) | Yes (view / table display) | Yes |
| DB | Yes | Yes |
| Contract / Spec | Yes | Yes |
| Cursor tools (skills, specs) | Yes (db-domain skill) | Yes |

### Planned change file list (expected change targets)

*(Planned at authoring. Implementing agent (Step 4) confirms or amends when implementation is complete.)*

#### DB (`backend/src/main/resources/db/` only for schema/migrations)

- `schema_pb_fep.sql` — **`log_time VARCHAR(20)`** on **`pb_send`** / **`pb_recv`** definitions; any related comments.
- `create-pb-send-recv-daily-partitions-only.sql` — embedded parent DDL **`VARCHAR(20)`**; **`FOR VALUES FROM/TO`** use **`YYYYMMDD || '000000000000'`** (20-char); verify all branches (greenfield, ordinary→partitioned, already partitioned).
- `migrate-pb-send-recv-partitioning-20260408.sql` — column width and partition bound literals **→ 20-char** pattern.
- `migrate-pb-send-recv-remove-log-timestamp-20260414.sql` — **`log_time`** width in recreated parent DDL; bound strings if present.
- `migrate-pb-send-recv-monthly-to-daily-20260414.sql` — **`FOR VALUES FROM/TO`** **20-char** bounds for split daily partitions.
- `seed-pb-fep-qa-known-data-20260414.sql` — **20-digit** **`log_time`** values and **`DELETE`/range** predicates aligned with new lexical format.
- `init-data*.sql` — any **`pb_send`** / **`pb_recv`** inserts touching **`log_time`** (e.g. **`init-data-local-decrypt-test-pbfep.sql`** if still used; align with **`log_timestamp`** removal requirements already in place).
- `check-db.sh` — optional assertions or messages for **`log_time`** type/length or partition bound sanity if project adds them.
- **New migration script** (name TBD by DB agent) — **`ALTER`** / normalize data / **rebind** partitions per §2 operator steps.

#### Backend

- `backend/src/main/java/com/logmng/service/LogDbService.java` — **`PB_FEPLOG_TIME_LEXICAL_FORMATTER`**, **`toPbFeplogLogTimeLexical`**, Javadoc on PB FEP time semantics; any **`WHERE`**/**`ORDER BY`** text tied to **`log_time`** length.
- `backend/src/test/resources/sql/logdb-service/h2-schema.sql` — **`log_time VARCHAR(20)`** for **`pb_send`** / **`pb_recv`**.
- `backend/src/test/java/com/logmng/service/LogDbServiceTest.java` — expectations for **`log_time`** format and filter behavior.
- Additional test resources or DTO validation if **`log_time`** length is validated anywhere.

#### Frontend

- `frontend/src/components/LogTable.js` (and any PB FEP-only formatters) — **20-digit** **`log_time`** display path for **`formatTime`** or dedicated PB FEP formatter.
- Tests under **`frontend/src/components/*.test.js`** that snapshot PB FEP timestamps if present.

#### Contract / API docs

- `docs/contract.md` — PB FEP **`log_time`**: **20-digit** lexical format **`yyyyMMddHHmmssSSSSSS`** (subsection **PB FEP — wire schema** → **`log_time` wire value**; legacy table + wireframe **`Row object`** row updated).
- `docs/api-definition.md` — PB FEP **`POST .../search`** (§5.1) and **`POST .../pb-fep-log-search`** (§5.1.1): **`startDate`/`endDate`** vs lexical **`log_time`**; response **`log_time`** echo (**20-character** wire string).

*(Contract agent 2026-04-15: §2 file list unchanged — no additional files; sections above are the authoritative doc locations for TC-09.)*

#### Cursor / workflow

- `.cursor/skills/db-domain/SKILL.md` — PB FEP **`log_time`** and partition-bound summary update (**§1.4 / domain change** per workflow).

---

## 3. Test approach

### Test case list (required)

| ID | Scope | Type | Scenario (input / condition) | Expected result | Verification (unit / integration / manual) |
|----|-------|------|------------------------------|-----------------|--------------------------------------------|
| TC-01 | DB | Normal | New parent DDL defines **`log_time VARCHAR(20)`** for **`pb_send`** / **`pb_recv`** in **`schema_pb_fep.sql`** (and embedded DDL in partition scripts). | Column width **20** in applied schema. | Manual / psql `\d+` after setup; or scripted check |
| TC-02 | DB | Normal | Daily partition for day **D** uses **`FROM`** = **`YYYYMMDD` + `000000000000`**, **`TO`** = **`(D+1)`** + **`000000000000`**; both strings length **20**. | Bounds match lexical **20-char** microsecond space. | Manual / SQL inspect `pg_get_expr` partition bounds |
| TC-03 | DB | Edge | Legacy row with **14-digit** **`log_time`** after migration rule (e.g. pad with **`000000`**). | Row sorts and routes to correct **daily** partition; no **DEFAULT** partition required by policy. | Integration (PostgreSQL fixture or staging DB) |
| TC-04 | Backend | Normal | **`toPbFeplogLogTimeLexical`** for **`LocalDateTime`** with nanosecond component **123456789** ns → **six** fractional digits per truncation rule (e.g. **123456** µs). | Formatted string length **20**, digits match **`yyyyMMddHHmmssSSSSSS`**. | Unit (`mvn test`) |
| TC-05 | Backend | Normal | Date range filter for PB FEP search uses **20-char** lower/upper alignment consistent with partition bounds. | SQL predicates only match **`log_time`** in **[start, end]** in lexical **20-digit** space. | Unit / integration (`mvn test`) |
| TC-06 | Backend | Edge | **`LogDbServiceTest`** H2 **`pb_send`**/**`pb_recv`** fixtures use **20-char** **`log_time`**; search returns rows ordered by **`log_time desc`** as before. | Tests pass; no **VARCHAR** truncation. | Unit (`mvn test`) |
| TC-07 | Frontend | Normal | PB FEP grid renders **`log_time`** **20-digit** string as readable timestamp (not raw digits only unless product says otherwise). | Display matches expected pattern; no console errors. | Unit (`npm test`) and/or Manual / browser |
| TC-08 | Integration | Normal | End-to-end: insert **20-digit** **`log_time`** (fixture or API-backed DB), **`POST`** PB FEP search, response **`log_time`** echo **20** chars. | Full pipeline consistent. | Integration (curl + DB) or Manual |
| TC-09 | Contract | Normal | **`docs/contract.md`** and **`docs/api-definition.md`** state **20-character microsecond** lexical **`log_time`** for PB FEP. | Docs align with implemented behavior. | Manual review |

**Domain note**: Apply **`log-search-domain`** skill expectations: keep request/response shapes per contract; this requirement only tightens **`log_time`** lexical format.

### Test data

- Prefer **executable SQL** in **`seed-pb-fep-qa-known-data-*.sql`** with **20-digit** **`log_time`**, e.g. **`20260414091011000000`**, **`20260414091011123456`**, with **`DELETE`** ranges using **20-char** endpoints.
- H2 test schema: **`VARCHAR(20) NOT NULL`** for **`log_time`** on **`pb_send`** / **`pb_recv`**.

### Test environment

- Backend: `http://localhost:9200`
- Frontend: `http://localhost:3001`
- Database: PostgreSQL per **`docs/contract.md`** (PB schema); H2 for unit tests as today.

### 3.5 Browser automation verification (optional)

- **Applicable TCs**: TC-07, TC-08 (if UI verification desired).
- **Procedure**: Login → open **pb-feplog** or **pb-fep-log-search** → confirm timestamp column displays parsed **20-digit** **`log_time`** correctly.
- **Reference**: `docs/workflow/BROWSER-AUTOMATION-VERIFICATION-POLICY.md`

---

## 4. Checklist

### Frontend verification
- [ ] API parameters validated
- [ ] UI behavior confirmed (PB FEP **`log_time`** display)
- [ ] Error handling verified

### Backend verification
- [ ] API test cases written and run
- [ ] Logs checked (no new noisy INFO for normal paths)

### Integration
- [ ] End-to-end flow tested (search + DB **`log_time`** width)
- [ ] Edge cases (legacy padded values, midnight boundaries)

### Documentation
- [ ] Requirement doc completed
- [ ] Contract / api-definition updated in same change wave as code

---

## 5. Test results

### Test run date
- 2026-04-15 (Docker E2E subagent run; host agent environment)

### Test results

| Step | Command / artifact | Result | Notes |
|------|-------------------|--------|--------|
| Seed SQL | `backend/src/main/resources/db/seed-pb-fep-docker-verify-20260415.sql` (new) | **PASS** (deliverable) | Idempotent `DELETE … WHERE reserve = 'DVFY'` then `INSERT` into `pb_send` / `pb_recv` with **20-digit** `log_time` (`yyyyMMddHHmmssSSSSSS`), `SET search_path TO public;`, same minimal wire columns as `seed-pb-fep-qa-known-data-20260414.sql` + marker `reserve`. |
| `.env.docker` | `test -f .env.docker \|\| cp .env.docker.example .env.docker` | **PASS** | Ensures Compose interpolation; secrets not logged. |
| Compose / daemon | `docker-compose --env-file .env.docker -f docker/docker-compose.yml --project-directory . ps` | **FAIL (blocked)** | `Cannot connect to the Docker daemon at unix:///Users/ghmin/.colima/default/docker.sock` — Colima/Docker Engine not running in this environment; no container ports (9200/3001/5434) available to verify. |
| Stack bring-up (intended) | `SKIP_DB_INIT=1 SKIP_BUNDLE_BUILD=1 ./scripts/docker-local-manual-test.sh up` *or* full `./scripts/docker-local-manual-test.sh up` per `docker/README.md` | **NOT RUN** | Blocked by Docker daemon. |
| Load seed into `pbfep` (intended) | `docker-compose --env-file .env.docker -f docker/docker-compose.yml --project-directory . exec -T postgres-pb psql -U postgres -d pbfep -v ON_ERROR_STOP=1` with SQL on stdin from repo file | **NOT RUN** | Example: `psql … < backend/src/main/resources/db/seed-pb-fep-docker-verify-20260415.sql` (host path); if `-f` with host path fails inside container, pipe stdin as documented in requirement handoff. |
| Backend health (intended) | `curl -sf http://localhost:9200/api/health` | **NOT RUN** | Default published API port **9200** per `docker/README.md` / `docs/contract.md`. |
| DB connectivity (intended) | `curl -sf http://localhost:9200/api/db/test` | **NOT RUN** | Expect `data.connected === true` for multi-DB pools when stack is up. |
| PB FEP search (intended) | Session cookie after `POST /api/auth/login` (e.g. `employeeNumber` from closed-network seed + password from committed `init-data-closed-network-admin-only.sql`), then `POST /api/logs/db-refactored/search` with `logType=pb_feplog`, date range covering 2026-04-15, `loginId=dvfy_docker` | **NOT RUN** | Validates union search sees seeded `brodid`; row `log_time` length 20. Raw SQL alternative: `SELECT COUNT(*) FROM pb_send WHERE reserve='DVFY';` on `pbfep`. |
| `docker-dev-sync.sh` | N/A | **SKIPPED** | No backend/frontend **source** changes in this subagent pass (SQL + requirement doc only); run after Java/JS changes before image rebuild per `docker/README.md`. |

**Fix applied in-repo for this pass:** none (environment blocker only).

**Operator unblock:** start Docker (e.g. Colima `colima start` or Docker Desktop), then re-run smoke: `./scripts/docker-local-manual-test.sh smoke` — expect `/api/health` OK, `/api/db/test` OK. If host login returns IP-restriction errors, confirm `APP_SECURITY_AUTHORIZED_IPS` includes Docker bridge (see `docker/README.md`; `.env.docker.example` already lists `172.*`, etc.).

**Default ports (unchanged):** API **9200**, UI **3001**, Primary Postgres host **5433**, PB **`postgres-pb` / `pbfep` host 5434**, ImageLog host **5435** (overridable via `.env.docker`).

---

## 7. Final version (Korean) — add after all verification is complete

*(Per `docs/workflow/DOCUMENT-LANGUAGE-POLICY.md` — added after QA verification.)*

---

**Author**: Requirements (subagent)  
**Date**: 2026-04-15  
**Status**: In progress  

**TOPIC-INDEX**: Add line under PB FEP / log-search topic — `20260415-pb-fep-log-time-20-char-microseconds | PB FEP pb_send/pb_recv: log_time must be 20-char yyyyMMddHHmmssSSSSSS (microseconds); VARCHAR(20), partition bounds, backend formatter, frontend display, contract.`  
**Index script**: Run `./scripts/generate-requirements-index.sh` after adding the doc to **`docs/requirements/TOPIC-INDEX.md`**.
