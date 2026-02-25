# Run Verification (tests, restart, health check, bugfix loop)

Run the verification steps: after **test plan + unit/integration tests**, run restart and checks without asking the user; on failure create a bugfix child requirement and repeat until resolved.

**Step 5 (subagent delegation)**: When the project uses subagent delegation for a requirement, **the QA subagent** performs this procedure (restart + health check + §5/§6 update). The main agent does **not** run verification in the main chat; it delegates to QA. See `docs/workflow/SUBAGENT-DELEGATION.md` (Step 5 → QA).

**Skill**: `.cursor/skills/test-workflow/SKILL.md`. **Order**: run-tests (§3 + run + §5) → then this (restart + health check).

## 1. Order

0. **Test plan + unit/integration tests (required first)**  
   - If the requirement doc **§3 test cases** are empty or stale, **define them first**.  
   - **Unit tests**: backend changed → `cd backend && mvn test`; frontend changed → `cd frontend && npm test -- --watchAll=false`. Fix failures and re-run.  
   - Run integration tests (or curl/manual) and record results in the requirement doc **§5 Test results**.  
   - If this step was skipped, do the same as `/run-tests` then continue with step 1.

1. **Determine scope**  
   From the current requirement doc and changed files, decide **restart target**: frontend only → `frontend`; backend only → `backend`; db/schema only → `db`; both or unclear → `all`.

2. **Restart**  
   From project root:
   ```bash
   ./scripts/dev-services.sh {frontend|backend|db|all} restart
   ```
   Wait **5–10 seconds** after backend restart before the next step.

3. **Health check**  
   - **Backend** (9200): `curl -s http://localhost:9200/api/health` → 200 and JSON.  
   - **Frontend** (3001): `curl -s -o /dev/null -w "%{http_code}" http://localhost:3001` → 2xx.  
   - **DB** (if used): `curl -s http://localhost:9200/api/db/test` → `data.connected === true`.  
   Check only what was restarted, or all if needed.

4. **Result**  
   - **All pass** → Update requirement doc with test results and checklist. For error fixes add "6. Error remedy result". Then **commit** per `.cursor/commands/commit-on-complete.md`. **Done.**  
   - **Any fail** → Go to 5.

5. **Failure handling and bugfix child**  
   - Identify **scope** of failure (layer, API, screen) from health/port/DB and logs.  
   - Get **parent requirement ID** (current requirement doc name, e.g. `20260220-activity-statistics-api-fix`).  
   - Create `docs/requirements/{parentID}-bugfix-{N}.md` (N = next number for that parent). Template: `docs/template/BUGFIX_CHILD_TEMPLATE.md`.  
   - Fix per that doc, then **repeat from step 2** until all checks pass.

## 2. References

- **Tests first**: `/run-tests`. If verification is run without tests, run tests first.
- **Workflow**: `docs/workflow/DEVELOPMENT_WORKFLOW.md`
- **Bugfix template**: `docs/template/BUGFIX_CHILD_TEMPLATE.md`
- **Script**: `scripts/dev-services.sh` (frontend | backend | db | all) (start | stop | restart)
- **How this fits with rules, commands, docs, scripts**: `docs/workflow/CURSOR-AND-TOOLS-INTEGRATION.md`

Apply this procedure using the current requirement doc or changed files as the parent when relevant.

**Output (summary):** One-line result, e.g. `Verify: pass (backend 200, frontend 2xx, DB connected)` or `Verify: fail — scope and cause`.
