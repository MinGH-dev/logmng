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

- **Stack**: PostgreSQL (port 5432, DB name `logmng`). See `docs/contract.md` for env table.
- **Scope**: schema.sql, setup.sh, init-data.sql, migrations, check-db.sh, backend/DB_*.md. No Java, API, or frontend.
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
