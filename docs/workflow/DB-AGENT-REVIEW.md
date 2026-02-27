# DB Subagent Role Review

**Date**: 2026-02-27  
**Trigger**: Verification failed after schema/init-data were delivered; backend returned 500 (relation "department" does not exist). User asked whether DB agent was failing to perform its role and to review/improve.

## 1. Current DB agent role (no failure to "re-role")

- **Scope**: Modify only `backend/src/main/resources/db/` (schema.sql, setup.sh, init-data.sql, migrations) and DB docs. No Java/API/frontend.
- **What DB did**: Delivered schema and init-data changes and updated requirement doc §2 change file list. Mentioned setup.sh and that schema must be applied.
- **Conclusion**: DB agent **did** perform its assigned role (produce files and mention setup). The gap was **not** "DB didn't do its job" but that (1) apply steps were easy to miss or not run against the **correct** DB (backend's `localhost:5432/logmng`), and (2) there was no **mandatory** "Apply" block in the agent instructions, so sometimes the handoff to "user or QA" was unclear.

## 2. Gap analysis

| Item | Current | Problem |
|------|---------|--------|
| **Apply instructions** | DB may mention setup.sh in passing | Not always a clear, copy-pastable block; no "backend's DB" emphasis |
| **DB vs Backend/Frontend** | Delegation says Step 4 agents "build, restart; hand off to QA" | DB doesn't run build/restart; someone must **apply** schema then **restart backend** before QA |
| **Length of agent doc** | db.md ~45 lines | Adding long paragraphs could cause steps to be skipped at runtime |

## 3. Improvements applied

1. **DB agent doc** (`docs/cursor-subagents/db.md`): Added a short **"After delivering schema or init-data"** block (mandatory output): exact apply commands, backend's DB from contract, and "restart backend then QA can verify." Kept to a few lines so the doc stays scannable.
2. **Delegation** (`docs/workflow/SUBAGENT-DELEGATION.md`): Added one short note under §2.1 / §3: DB does not run build/restart; when DB delivers schema/init-data it must include apply steps and note that schema must be applied to backend's DB and backend restarted before QA verification.

## 4. Alternative if agent doc must stay minimal

If the DB prompt must not grow at all, use a **prompt template** for the main agent when invoking DB for schema/init-data:

- In the mcp_task (or handoff) prompt, add: *"Your response must end with an **Apply** block: exact command(s) to apply schema/init-data to the database the backend uses (see docs/contract.md: localhost:5432/logmng), and a note that the backend must be restarted before QA verification. If using setup.sh, mention DB_SUPERUSER when the postgres role may not exist."*

Then the DB agent document can stay unchanged; the **caller** enforces the required output.

## 5. Follow-up: DB agent as executor (2026-02-27)

- **Change**: DB subagent was updated to **perform** the apply, not only document it. After delivering schema/init-data, it must **run** the apply (setup.sh or psql -f ...), report outcome (exit code, success/failure), and still output the Apply block. Skip execution only when handoff says "document only" or "do not run apply".
- **Updated**: `docs/cursor-subagents/db.md`, `.cursor/subagents/db-prompt.md`, `docs/workflow/SUBAGENT-DELEGATION.md` §2.1.

## 6. References

- DB subagent prompt: `docs/cursor-subagents/db.md`
- Delegation: `docs/workflow/SUBAGENT-DELEGATION.md` §2.1, §3
- Contract (DB): `docs/contract.md`
- Bugfix that triggered review: `docs/requirements/20250227-user-permission-hierarchy-group-bugfix-1.md`
