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

3.5. **Browser check (required when frontend was in scope; optional when backend/DB only)**  
   - **Policy**: For **frontend** changes, browser check is **required** when a browser MCP is available. See `docs/workflow/BROWSER-AUTOMATION-VERIFICATION-POLICY.md`. For backend/DB-only scope, step 3.5 remains optional.  
   - **Purpose**: Confirm the app loads and key UI is visible beyond HTTP 2xx; run §3 (and §3.5 if present) test cases that are UI-checkable via Browser MCP.  
   - **Steps**: Navigate to `http://localhost:3001`; capture page (snapshot or screenshot); confirm app shell (sidebar, main area, or login). Run §3 critical-path actions and, if the requirement doc has **§3.5 브라우저 자동화 검증**, execute those procedures per TC. Use short waits (1–3 s) and capture again if the page is still loading.  
   - **Viewport size**: The default browser window is often small (e.g. 800×600). For full HD: **cursor-ide-browser** — after `browser_navigate` call **`browser_resize`** (width 1920, height 1080); **Puppeteer MCP** — on first **`puppeteer_navigate`** pass **`launchOptions`**: `{ "defaultViewport": { "width": 1920, "height": 1080 } }`. See `docs/workflow/BROWSER-AUTOMATION-MCP.md` §2.1.  
   - **Tool choice**: Prefer **puppeteer_*** (server-puppeteer, see `.cursor/mcp.json`) when available — use CSS selectors, no ref dependency. If only **cursor-ide-browser**: **always** `browser_navigate` first, then `browser_lock`, then `browser_snapshot` (never lock before navigate); wait 2–3 s after navigate before snapshot so the page has time to render; if snapshot returns only metadata (no refs), wait and retry once.
   - **Tool names**: If using **cursor-ide-browser**: `browser_navigate` → `browser_lock` → `browser_snapshot` → `browser_click` / `browser_fill` / `browser_get_attribute` / `browser_press_key` as needed → `browser_unlock`. If using **server-puppeteer** (project default): `puppeteer_navigate`, `puppeteer_screenshot`, `puppeteer_click`, etc. — see `docs/workflow/BROWSER-AUTOMATION-MCP.md` for mapping. Troubleshooting (snapshot no refs, screenshot timeout): `docs/workflow/QA-BROWSER-TEST-TROUBLESHOOTING.md`.  
   - **§5 — Detailed report**: Record in Test results: (1) tool used and base URL; (2) for each TC or scenario run: **Pass** or **Fail** and a short note; (3) for each **Fail**: what was checked (selector/ref), expected, actual (so the Frontend subagent can fix). See BROWSER-AUTOMATION-VERIFICATION-POLICY.md §2.3.  
   - **On failure**: If any check fails (browser or health/behavior), create a bugfix child and hand off to **Requirements**. Do **not** hand off directly to Frontend or Backend. In the bugfix doc, **identify failure scope** (frontend | backend | db | security | contract | ux | …). Requirements formalizes the doc and **delegates to the responsible expert** by scope; when that expert **closes** the issue, **QA re-runs verification**. When all issues are closed and verification passes, QA commits. See BROWSER-AUTOMATION-VERIFICATION-POLICY.md §2.4.

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
- **Browser check**: Step 3.5 is **required** for frontend scope when a browser MCP is available; optional for backend/DB-only. Policy, detailed report format, and handoff on failure: `docs/workflow/BROWSER-AUTOMATION-VERIFICATION-POLICY.md`. Tool mapping: `docs/workflow/BROWSER-AUTOMATION-MCP.md`.

Apply this procedure using the current requirement doc or changed files as the parent when relevant.

**Output (summary):** One-line result, e.g. `Verify: pass (backend 200, frontend 2xx, DB connected)` or `Verify: fail — scope and cause`.
