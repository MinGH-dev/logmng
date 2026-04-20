---
name: db-domain
description: >
  Database specialist for this project: PostgreSQL schema, migrations, setup scripts.
  Use when the user asks about database schema design, migrations, setup.sh, check-db,
  schema.sql, init-data, or DB connection config. Do NOT use for API or backend Java logic
  (use Backend agent); scope is backend/src/main/resources/db/ only.
---

# DB Domain (project-scoped)

**Skill usage visibility**: When you use this skill to answer, state at the start of your response: `[Skill used: db-domain]`

Use for **schema, migrations, and DB config** in this repo. Scope: `backend/src/main/resources/db/` and DB docs only.

## Quick reference

- **Stack**: PostgreSQL (port 5432). Default dev: single DB `logmng`. **Split ops**: DB **A** (`DB_A_NAME`) for system + PB schemas (`SCHEMA_SYS`, `SCHEMA_PB`); DB **B** (`DB_B_NAME`) for ImageLog (`SCHEMA_IMAGELOG`). See `backend/DB_SETUP_GUIDE.md` and `docs/contract.md` (multi-datasource + env table). Req: `docs/requirements/20260320-multi-datasource-schema-configuration.md`.
- **DDL layout**: `schema.sql` includes `schema_pb_fep.sql` and `schema_sys.sql`; ImageLog remains `schema_imagelog.sql` (often applied to B when split).
- **PB FEP `log_time` + partitioning**: On `pb_send`/`pb_recv`, **`log_time`** is **`VARCHAR(20)`** with lexical format **`yyyyMMddHHmmssSSSSSS`** (microseconds, zero-padded). **Daily** RANGE partitions use **`log_time`** as the key; bounds are **lexicographic** 20-char literals—day **D**: **`FROM`** `YYYYMMDD || '000000000000'`, **`TO`** `YYYYMMDD(D+1) || '000000000000'`. Details and migration notes: `docs/requirements/20260415-pb-fep-log-time-20-char-microseconds.md` §2. Implementation lives in `schema_pb_fep.sql`, `create-pb-send-recv-daily-partitions-only.sql`, and `migrate-pb-send-recv-*.sql`; `setup.sh` runs PB FEP partition steps in order (`5-pb-fep-partition` then `5-pb-fep-partition-daily-upgrade` where applicable). **Operator-only**: `create-pb-send-recv-daily-partitions-only.sql` (e.g. pre-created window, strict no-DEFAULT setups). See `backend/DB_SETUP_GUIDE.md` § PB FEP partitioning.
- **Imagelog composite key (req 20260320)**: migrations `migrate-imagelog-guid-status-unique-20260320.sql` (DB B / ImageLog) and `migrate-sys-decryption-composite-pk-20260320.sql` (DB A / sys); wrapper `migrate-imagelog-composite-decrypt-20260320.sql` uses `\ir` for single-schema runs. `setup.sh` runs both in order.
- **Permission group screen columns (req 20260320)**: `setup.sh` step **4h** runs `migrate-permission-group-screen-scope.sql` → `migrate-permission-group-screen-functions.sql` → `migrate-permission-group-screen-decrypt.sql` → `migrate-permission-group-screen-scope-team.sql` on DB A before init-data/5a so legacy `permission_group_screen` rows get `scope`/`read`/`write`/`approve`/`decrypt`. `check-db.sh` section **6b** validates those columns on `SCHEMA_SYS.permission_group_screen`.
- **Scope**: `backend/src/main/resources/db/` (schema*.sql, setup.sh, check-db.sh, migrations, init-data), `backend/DB_*.md`. No Java, API, or frontend.
- **Workflow**: Requirement doc + §3 test plan before schema/script changes. After schema change, state "backend/spec update needed".

## When to use

- Designing or changing database schema
- Adding or editing migrations
- Editing setup.sh, check-db.sh, init-data.sql
- DB connection or config questions (within contract)
- Data policy or initial data

## Before editing

1. Read `docs/contract.md` (DB row) and `backend/src/main/resources/db/schema.sql` (use Grep + Read(offset, limit) if large).
2. If requirement or bug fix: ensure requirement doc exists and §3 test plan is filled (WORKFLOW_CHECKLIST).

## References

- Contract: `docs/contract.md`
- Workflow: `docs/workflow/WORKFLOW_CHECKLIST.md`, `docs/workflow/DEVELOPMENT_WORKFLOW.md`
- DB setup: `backend/DB_SETUP_GUIDE.md`
- Agent: `.cursor/agents/DB.mdc` (DB subagent)
