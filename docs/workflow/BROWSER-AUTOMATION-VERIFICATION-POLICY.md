# Browser Automation Verification Policy

This document explains **why** browser automation was not used from the start in past verification, and defines the **current policy**: all frontend changes must be verified with Browser Automation, with a detailed report and handoff to fix failures.

---

## 1. Why subagents did not use Browser Automation initially (검토)

### 1.1 Root causes

| Cause | Where it was defined | Effect |
|-------|----------------------|--------|
| **Step 3.5 was "optional"** | `.cursor/commands/verify.md` — "Browser check **(optional**, when frontend was restarted and a browser MCP is available)" | QA followed verify.md and treated browser check as skip-pable; health check + optional 1–2 actions were enough to pass. |
| **No "frontend → mandatory browser" rule** | No policy doc stated that frontend scope implies mandatory browser verification. | QA did not infer that frontend changes require running Browser MCP. |
| **§3 did not require browser procedures** | Requirement docs (e.g. UX compliance) had TC-01~TC-07 as "수동 검증 대상" and no **§3.5 브라우저 자동화 검증** section. | Even if QA wanted to automate, there was no step-by-step procedure in the requirement doc to run. |
| **QA agent said "optionally"** | `.cursor/agents/QA.mdc` — "**optionally** run step 3.5 (browser check)". | QA subagent interpreted verification as health + optional browser. |
| **Handoff did not require report** | `SUBAGENT-DELEGATION.md` Step 5 → QA: "Output: verification (verify checklist, health/behavior), §5". No mention of "detailed browser report" or "handoff to Frontend on failure". | QA delivered §5 with one-line browser summary and did not create bugfix child or delegate back to Frontend for UI failures. |

### 1.2 Summary

Browser automation was **optional** in procedure and agent instructions, and requirement docs did not define **runnable browser procedures** (§3.5) or require a **detailed verification report**. The delegation chain did not define "on browser verification failure → create bugfix child and hand off to Frontend". As a result, subagents (especially QA) completed verification with health check + optional light browser check and marked UI test cases as "수동 검증 대상" instead of running them via Browser MCP and writing a detailed report.

---

## 2. Current policy (앞으로의 정책)

### 2.1 Mandatory browser verification for frontend changes

- **When**: The requirement or change involves **frontend** (`frontend/` source or config). Verification is performed by the **QA** subagent (Step 5).
- **Rule**: QA **must** run **Browser Automation** (step 3.5 in verify.md), not optionally. If a browser MCP is available (cursor-ide-browser or server-puppeteer per `.cursor/mcp.json`), QA executes the browser verification steps applicable to the requirement (see §2.2 and §2.3).
- **If no browser MCP**: If the environment has no browser MCP enabled, QA records in §5 that browser verification was skipped (reason: MCP unavailable) and recommends manual verification or running verification again when MCP is available.

### 2.2 Requirement doc: §3.5 when frontend-heavy

- For **frontend-heavy** requirements (UI, layout, forms, tables, a11y, UX standards), the requirement doc **should** include a **§3.5 브라우저 자동화 검증** (or equivalent) section that lists:
  - Which test cases (e.g. TC-01~TC-08) are runnable via Browser Automation.
  - Step-by-step procedures per TC (navigate, snapshot, click, fill, get_attribute, etc.) so QA can execute them without guessing.
- If the requirement doc has no §3.5, QA still runs browser verification to the extent possible: at least app load, login (if applicable), navigation to changed routes, and §3 test cases that are clearly UI-checkable (e.g. "sidebar visible", "no back link in content", "button has aria-label"). QA records what was run and what was not runnable.

### 2.3 Detailed verification report

- QA **must** produce a **detailed verification report** (not only pass/fail). The report is written in the requirement doc **§5** and must include:
  - **Scope**: Frontend only / backend only / both.
  - **Health check**: Frontend (3001), Backend (9200), DB if used — result (e.g. 200) and one-line outcome.
  - **Browser automation**:
    - **Tool used**: e.g. cursor-ide-browser, server-puppeteer, project-0-dev-browser.
    - **Base URL**: e.g. http://localhost:3001.
    - **Steps run**: For each test case or scenario run (e.g. TC-01, TC-02, …): **Pass** or **Fail**, and **brief note** (e.g. "메뉴별 '← 메인으로' 없음 확인", "날짜 역전 제출 후 aria-invalid 미노출").
    - **Failures**: For each **Fail**, include: TC ID, **what was checked** (e.g. selector or ref), **expected** (e.g. `aria-invalid="true"` on date input), **actual** (e.g. "0 elements with aria-invalid"). This allows the Frontend subagent to fix without re-guessing.
  - **Table**: A table in §5 listing each TC (or scenario) with columns: ID, Result (Pass/Fail), Note (short), and for failures optionally "Detail" (selector/ref, expected, actual).

### 2.4 Handoff on failure (오류 시 위임)

- **When** browser verification finds one or more **Fail** (e.g. TC-05 Fail: aria-invalid/aria-describedby not set on error):
  1. **QA** updates §5 with the detailed report (including failure detail: what was checked, expected, actual).
  2. **QA** does **not** commit the requirement as "done". QA creates a **bugfix child requirement**: `docs/requirements/{parentID}-bugfix-{N}.md` (e.g. `20260225-ux-standards-compliance-audit-bugfix-1.md`) using the template `docs/template/BUGFIX_CHILD_TEMPLATE.md`. In the bugfix doc, QA describes the failure (reference §5 of parent) and the expected fix (e.g. "Set aria-invalid and aria-describedby on date inputs when validation fails").
  3. **Hand off to the responsible subagent**: For UI/frontend failures → **Frontend** subagent. Main agent (or user) invokes **Frontend** via mcp_task with the bugfix requirement doc and the parent doc §5 failure detail. Frontend implements the fix, builds, restarts, and hands off to **QA** again.
  4. **QA** runs verification again (health + **browser automation** including the previously failed TCs). If all pass, QA updates §5 (and bugfix doc §5/§6), then **commit** per `.cursor/commands/commit-on-complete.md`. If any fail again, repeat: update §5, create or update bugfix child, hand off to Frontend again.

- **Flow summary**:  
  QA (browser verify) → **Fail** → QA writes detailed report in §5, creates bugfix child → **Frontend** (fix) → build/restart → **QA** (re-verify with browser) → Pass → commit.

---

## 3. References

- **Verification procedure**: `.cursor/commands/verify.md` — step 3.5 is **required** when scope is frontend; see this policy.
- **Tool mapping and activation**: `docs/workflow/BROWSER-AUTOMATION-MCP.md`.
- **Delegation**: `docs/workflow/SUBAGENT-DELEGATION.md` Step 5 (QA), §2.1 (build/restart), failure handling.
- **Bugfix template**: `docs/template/BUGFIX_CHILD_TEMPLATE.md`.
- **Example requirement with §3.5**: `docs/requirements/20260225-ux-standards-compliance-audit.md` (§3.5 브라우저 자동화 검증, §5 테스트 결과).
