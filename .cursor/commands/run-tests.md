# Test plan and unit/integration tests

**Requirements and test plan are completed before development.** This command is for (1) defining §3 when the test plan is missing, and (2) running unit/integration tests after development and recording results.

**Skill**: `.cursor/skills/test-workflow/SKILL.md` (test plan → run → §5 → verify order).

## 1. Test plan (should be done before development)

- Check the current **requirement doc** (`docs/requirements/yyyyMMdd-name.md`) **§3 Test approach**.  
- If the **test case list** is empty or weak, **write or update it now** (normal/exception/edge, acceptance criteria).  
  **Do not fill the test plan after development** — §3 is completed before development.
- Template: `docs/template/REQUIREMENT_TEMPLATE.md` §3 "Test case list" (table: ID, 구분, 시나리오, 기대 결과, 검증 방법).

## 2. Unit tests

- **Backend** changed:
  ```bash
  cd backend && mvn test
  ```
  On failure: fix and re-run. Record result (pass/fail, summary) in requirement doc **§5 Test results**.
- **Frontend** changed:
  ```bash
  cd frontend && npm test -- --watchAll=false
  ```
  On failure: fix and re-run. Record in §5. If the project uses another test script, use that.
- **Both** changed: run both.

## 3. Integration tests

- If the project has integration test scripts/code, run them and record in §5.
- Otherwise verify key APIs/flows with curl or manual scenarios and record in §5.

## 4. Update requirement doc

- **§5 Test results**: test run time; unit test command and pass/fail (and failure summary); integration test method and result.

**Output (summary):** One-line result, e.g. `Tests: backend pass, frontend pass` or `Tests: backend fail — summary`. Then run `/verify` if not yet done.

See: `docs-reference.mdc`, `post-change-test-verify.mdc`, `docs/workflow/DEVELOPMENT_WORKFLOW.md`, `docs/workflow/ERROR-FIX-WORKFLOW-FLOWCHART.md`. When running `/verify`, ensure this test step was done first; if not, run it first. How tests fit with rules, verify, and scripts: `docs/workflow/CURSOR-AND-TOOLS-INTEGRATION.md`.
