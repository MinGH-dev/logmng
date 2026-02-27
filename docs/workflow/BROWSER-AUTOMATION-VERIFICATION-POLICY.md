# Browser Automation Verification Policy

This document explains **why** browser automation was not used from the start in past verification, and defines the **current policy**: all frontend changes must be verified with Browser Automation, with a detailed report and handoff to fix failures.

---

## 1. Why subagents did not use Browser Automation initially

### 1.1 Root causes

| Cause | Where it was defined | Effect |
|-------|----------------------|--------|
| **Step 3.5 was "optional"** | `.cursor/commands/verify.md` — "Browser check **(optional**, when frontend was restarted and a browser MCP is available)" | QA followed verify.md and treated browser check as skip-pable; health check + optional 1–2 actions were enough to pass. |
| **No "frontend → mandatory browser" rule** | No policy doc stated that frontend scope implies mandatory browser verification. | QA did not infer that frontend changes require running Browser MCP. |
| **§3 did not require browser procedures** | Requirement docs (e.g. UX compliance) had TC-01~TC-07 as "manual verification" only and no **§3.5 Browser automation verification** section. | Even if QA wanted to automate, there was no step-by-step procedure in the requirement doc to run. |
| **QA agent said "optionally"** | `.cursor/agents/QA.mdc` — "**optionally** run step 3.5 (browser check)". | QA subagent interpreted verification as health + optional browser. |
| **Handoff did not require report** | `SUBAGENT-DELEGATION.md` Step 5 → QA: "Output: verification (verify checklist, health/behavior), §5". No mention of "detailed browser report" or "handoff to Frontend on failure". | QA delivered §5 with one-line browser summary and did not create bugfix child or delegate back to Frontend for UI failures. |

### 1.2 Summary

Browser automation was **optional** in procedure and agent instructions, and requirement docs did not define **runnable browser procedures** (§3.5) or require a **detailed verification report**. The delegation chain did not define "on browser verification failure → create bugfix child and hand off to Frontend". As a result, subagents (especially QA) completed verification with health check + optional light browser check and marked UI test cases as "manual verification" instead of running them via Browser MCP and writing a detailed report.

---

## 2. Current policy

### 2.1 Mandatory browser verification for frontend changes

- **When**: The requirement or change involves **frontend** (`frontend/` source or config). Verification is performed by the **QA** subagent (Step 5).
- **Rule**: QA **must** run **Browser Automation** (step 3.5 in verify.md), not optionally. If a browser MCP is available (**AgentDeskAI** preferred, or cursor-ide-browser, server-puppeteer per `.cursor/mcp.json`), QA executes the browser verification steps applicable to the requirement (see §2.2 and §2.3).
- **If no browser MCP**: If the environment has no browser MCP enabled, QA records in §5 that browser verification was skipped (reason: MCP unavailable) and recommends manual verification or running verification again when MCP is available.

### 2.2 Requirement doc: §3.5 when frontend-heavy

- For **frontend-heavy** requirements (UI, layout, forms, tables, a11y, UX standards), the requirement doc **should** include a **§3.5 Browser automation verification** (or equivalent) section that lists:
  - Which test cases (e.g. TC-01~TC-08) are runnable via Browser Automation.
  - Step-by-step procedures per TC (navigate, snapshot, click, fill, get_attribute, etc.) so QA can execute them without guessing.
- If the requirement doc has no §3.5, QA still runs browser verification to the extent possible: at least app load, login (if applicable), navigation to changed routes, and §3 test cases that are clearly UI-checkable (e.g. "sidebar visible", "no back link in content", "button has aria-label"). QA records what was run and what was not runnable.

### 2.3 Detailed verification report

- QA **must** produce a **detailed verification report** (not only pass/fail). The report is written in the requirement doc **§5** and must include:
  - **Scope**: Frontend only / backend only / both.
  - **Health check**: Frontend (3001), Backend (9200), DB if used — result (e.g. 200) and one-line outcome.
  - **Browser automation**:
    - **Tool used**: e.g. AgentDeskAI (user-browser-tools), cursor-ide-browser, server-puppeteer, project-0-dev-browser.
    - **Base URL**: e.g. http://localhost:3001.
    - **Steps run**: For each test case or scenario run (e.g. TC-01, TC-02, …): **Pass** or **Fail**, and **brief note** (e.g. "No 'back to main' link in content per menu", "aria-invalid not set after date reversal submit").
    - **Failures**: For each **Fail**, include: TC ID, **what was checked** (e.g. selector or ref), **expected** (e.g. `aria-invalid="true"` on date input), **actual** (e.g. "0 elements with aria-invalid"). This allows the Frontend subagent to fix without re-guessing.
  - **Table**: A table in §5 listing each TC (or scenario) with columns: ID, Result (Pass/Fail), Note (short), and for failures optionally "Detail" (selector/ref, expected, actual).

### 2.4 Handoff on failure

- **When** verification (browser or health/behavior) finds one or more **Fail**:
  1. **QA** updates §5 with the detailed report (including failure detail: what was checked, expected, actual). QA does **not** commit. QA creates a **bugfix child**: `docs/requirements/{parentID}-bugfix-{N}.md`. In the bugfix doc, QA describes the failure and **identifies the failure scope** (layer/domain): e.g. **frontend** (UI, a11y, layout), **backend** (API, server, logic), **db** (schema, migrations), **security** (PII, access, decrypt), **contract** (API/DB spec), **ux** (design review only — implementation is Frontend), or **other**. QA does **not** hand off directly to Frontend or Backend; QA **always hands off to Requirements**.
  2. **Hand off to Requirements**: QA **immediately hands off to the Requirements subagent** with the bugfix doc path, parent doc, §5 failure detail, and **failure scope** (frontend | backend | db | security | contract | ux | …). **Requirements** formalizes the bugfix doc (§1·§2·§3) and then **delegates to the responsible expert subagent** by scope:
     - **frontend** (UI, layout, a11y, frontend config) → **Frontend**
     - **backend** (API, server, Java, backend config) → **Backend**
     - **db** (schema, migrations, scripts) → **DB**
     - **security** (PII, decryption, access control) → **Security** (review/design); then Backend/Frontend implement if needed
     - **contract** (API/DB contract, specs) → **Contract**; then Backend/Frontend implement
     - **ux** (design/a11y recommendation only) → **UX** (review); then **Frontend** implements
     - **other** or unclear → Requirements may ask QA or the main agent to clarify scope, or assign to the most likely subagent (e.g. Backend if health/API failed).
  3. **Responsible subagent** (Frontend, Backend, DB, or Security/Contract/UX then implementer) **fixes** the issue, builds/restarts as applicable, and **reports issue closed** by handing off to **QA** (or by stating in the handoff that QA should re-verify). They do **not** commit; QA commits after re-verification passes.
  4. **QA** **re-runs verification** (health check, and browser automation for frontend-related failures). If **all pass**, QA updates §5 (and bugfix doc §5/§6), then **commit** per `.cursor/commands/commit-on-complete.md`. If **any fail again**, repeat: update §5, create or update bugfix child, hand off to **Requirements** again; Requirements again delegates to the responsible expert. Continue until all issues are closed and QA passes.

- **Flow summary**:  
  QA (verify) → **Fail** → QA writes §5, creates bugfix child, **identifies failure scope** → **Requirements** (formalize doc, **assign to responsible expert by scope**) → **Expert** (Frontend|Backend|DB|Security|Contract|UX→Frontend) (fix) → **issue closed** → **QA** (re-verify) → Pass → commit. If scope is not Frontend/QA, Requirements still receives the handoff and delegates to the correct expert; when that expert closes the issue, QA performs the QA procedure again.

---

## 3. References

- **Verification procedure**: `.cursor/commands/verify.md` — step 3.5 is **required** when scope is frontend; see this policy.
- **Tool mapping and activation**: `docs/workflow/BROWSER-AUTOMATION-MCP.md`.
- **Delegation**: `docs/workflow/SUBAGENT-DELEGATION.md` Step 5 (QA), §2.1 (build/restart), failure handling.
- **Bugfix template**: `docs/template/BUGFIX_CHILD_TEMPLATE.md`.
- **Example requirement with §3.5**: `docs/requirements/20260225-ux-standards-compliance-audit.md` (§3.5 Browser automation verification, §5 Test results).
