# PB FEP wire schema, partitioning, and search verification

Operator-facing procedure for applying the **wire-aligned** `pb_send` / `pb_recv` DDL, optional partitioning migrations, daily partition maintenance, and validating application connectivity and search readiness. Traceability: requirement [`docs/requirements/20260414-pb-fep-wire-schema-alignment.md`](../requirements/20260414-pb-fep-wire-schema-alignment.md) (see **Appendix — Partition key decision**).

For Korean installation context, variable names, and `setup.sh` orchestration, see [`backend/DB_SETUP_GUIDE.md`](../../backend/DB_SETUP_GUIDE.md). This document focuses on **manual `psql` order** and **verification** against a real PB database.

---

## 1. Prerequisites

| Item | Notes |
|------|--------|
| **PostgreSQL** | **16** (matches repository DDL comments and tooling expectations). |
| **Privileges** | A role that can **CREATE** objects in the target database/schema (e.g. `CREATE TABLE`, `CREATE INDEX`, `CREATE TRIGGER`, `CREATE OR REPLACE FUNCTION` as required by the scripts). Use the table owner or a superuser consistent with your `setup.sh` / DBA policy. |
| **`update_updated_at_column()`** | Required by PB DDL triggers. Either: **(a)** apply [`schema_sys.sql`](../../backend/src/main/resources/db/schema_sys.sql) (or your environment’s system schema step) so the function exists in a schema on `search_path`, or **(b)** rely on the idempotent **`CREATE OR REPLACE FUNCTION update_updated_at_column()`** block at the top of [`create-pb-send-recv-daily-partitions-only.sql`](../../backend/src/main/resources/db/create-pb-send-recv-daily-partitions-only.sql) (same definition as `schema_sys` / `schema_pb_fep` alignment). |
| **`search_path`** | Unqualified `pb_send` / `pb_recv` names resolve via **`APP_DB_SCHEMA_PB`** (e.g. `logmng`) — set `search_path` for every manual session to **`SCHEMA_PB, public`** (or your operational equivalent). |

---

## 2. Apply order (manual)

Run from `backend/src/main/resources/db/` (or pass absolute paths to `psql -f`).

1. **Connect** to the **PB database** (split-PB: `DB_PB_NAME`; co-located PB on A: same DB as primary per deployment).
2. **`SET search_path`** (example — replace `logmng` with your `SCHEMA_PB`):

   ```sql
   SET search_path TO logmng, public;
   ```

3. **Base DDL —** [`schema_pb_fep.sql`](../../backend/src/main/resources/db/schema_pb_fep.sql)  
   Defines wire-aligned `pb_send` / `pb_recv`, indexes, and triggers.

4. **Optional partitioning migrations (environment-specific)**  
   - **Greenfield / first-time partitioning path:** [`migrate-pb-send-recv-partitioning-20260408.sql`](../../backend/src/main/resources/db/migrate-pb-send-recv-partitioning-20260408.sql) — only if your rollout uses this step (see `setup.sh` / `DB_SETUP_GUIDE.md`).  
   - **Legacy monthly partitions only:** [`migrate-pb-send-recv-monthly-to-daily-20260414.sql`](../../backend/src/main/resources/db/migrate-pb-send-recv-monthly-to-daily-20260414.sql) — run **after backup** if `pb_*_YYYYMM`-style children still exist; skip if already daily-only (script is idempotent with notices).

5. **Daily window / (re)partition maintenance —** [`create-pb-send-recv-daily-partitions-only.sql`](../../backend/src/main/resources/db/create-pb-send-recv-daily-partitions-only.sql)  
   Execute with the **same** `search_path` as above. Adjust `back_days` / `fwd_days` in the script if you need a wider calendar window before bulk loads.  
   **Policy:** This script does **not** create a **DEFAULT** partition; inserts with `log_timestamp` outside pre-created daily ranges **fail** until you extend the window.

---

## 3. Warning: existing “simplified” schema

If the database still has the **older application-centric** `pb_send` / `pb_recv` layout (e.g. columns like `media_code`, `user_id`, `request_data` instead of wire names such as `media_gb`, `brodid`, `vlen`, `data`), **do not** blindly re-run only the new DDL on production data.

- You need a **planned data migration** (column mapping, `log_timestamp` population rules, partition placement) and backups.  
- The requirement doc §3 (e.g. TC-03) and **Appendix** describe the **typed partition key** and **`brodid`** / **`loginId`** semantics — align ETL and cutover with those.

---

## 4. Verification SQL (database)

Use the same `search_path` as apply. Examples:

**Table shape (psql):**

```text
\d+ pb_send
\d+ pb_recv
```

**Confirm `log_timestamp` is the partition key** (see requirement Appendix): parent should show partition key definition on `log_timestamp` when partitioned.

**Count daily child partitions** (illustrative):

```sql
SELECT
  parent.relname AS parent_table,
  COUNT(*)       AS child_partitions
FROM pg_inherits
JOIN pg_class AS parent ON parent.oid = inhparent
WHERE parent.relname IN ('pb_send', 'pb_recv')
GROUP BY parent.relname
ORDER BY 1;
```

**Optional smoke read** (adjust predicates to match your seed or production samples):

```sql
SELECT id, log_timestamp, brodid, media_gb, tr_code
FROM pb_send
ORDER BY log_timestamp DESC
LIMIT 5;
```

**Automated layout check:** run [`check-db.sh`](../../backend/src/main/resources/db/check-db.sh) with the same environment variables as installation; section **6i** covers PB partition expectations.

---

## 5. Application connectivity

Runtime JDBC is defined in **`docs/contract.md`** (Environment · Ports / multi-datasource). In practice:

| Pool | Configuration |
|------|----------------|
| **Primary** | `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` (and driver options as deployed). If the PB URL is **empty** or **identical** to Primary, PB FEP tables are read from the **same** pool using `search_path` / schema settings. |
| **Dedicated PB** (split database) | `APP_DATASOURCE_PB_URL`, `APP_DATASOURCE_PB_USERNAME`, `APP_DATASOURCE_PB_PASSWORD` (optional keys per `application.yml` / contract). |

After the backend is running with the correct env:

- **Health:** `GET /api/health` (e.g. `http://localhost:9200/api/health` in dev — port per contract).  
- **DB connectivity:** `GET /api/db/test` — expect a successful response with **`data.connected === true`** and PB-related connectivity as implemented (validates pools against configured URLs).

Search smoke (authenticated) is covered by integration tests and manual TC-09 in the requirement; use **`POST /api/logs/db-refactored/search`** with `logType=pb_feplog` and the wireframe endpoint as needed for your UI version.

---

## 6. Partition key (normative summary)

From the requirement **Appendix** (full text in the requirement doc):

- **RANGE partition column:** `log_timestamp` as **`TIMESTAMP NOT NULL`** (or **`TIMESTAMPTZ`** if timezone rules are explicit and consistent in ingest).  
- **Rationale:** PostgreSQL RANGE partitioning requires **typed** bounds; raw varchar wire time fields are not used as partition keys without documented parse rules.  
- **Wire fields:** Keep `log_time` / `wire_ts` (legacy `"timestamp"`) as needed; **populate `log_timestamp`** for query and partitioning per a single documented rule at insert/migration time.
