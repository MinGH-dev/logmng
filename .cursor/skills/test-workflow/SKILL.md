---
name: test-workflow
description: >
  Testing and verification workflow for this project: §3 test plan before dev,
  unit/integration runs, §5 recording, then verify (restart + health check).
  Use when defining test cases, running tests, recording results, or running verification.
  Do NOT use for writing test code (Backend/Frontend implement); use for test design and workflow.
---

# Test Workflow (project-scoped)

Use for **test plan (§3), test execution, result recording (§5), and verification** in this repo.

## Quick reference

| Step | When | Action |
|------|------|--------|
| **§3 Test plan** | Before any development | Fill test case list in requirement doc (normal/exception/edge). Table format per requirement template. |
| **Unit tests** | After backend/frontend change | Backend: `cd backend && mvn test`. Frontend: `cd frontend && npm test -- --watchAll=false`. Fix failures, re-run. |
| **§5 Test results** | After each test run | Record in requirement doc: date, command, pass/fail, summary (and integration/curl if used). |
| **Verify** | After tests pass | Restart scope (frontend/backend/db/all), health check per `verify.md`. On failure → bugfix child, repeat. |
| **Commit** | When requirement fully resolved | After verify pass + §5/§6 updated, commit per `commit-on-complete.md` (no push unless user asks). |

## When to use

- Defining or updating §3 test cases (before development)
- Deciding what to run (mvn test vs npm test vs both)
- Recording §5 test results
- Running or explaining verify steps (restart, health check, bugfix child)
- Test strategy or checklist design (QA agent scope)

## Order (WORKFLOW_CHECKLIST)

1. Requirement doc + §2 design + **§3 test plan** (mandatory before code).
2. Development (frontend/backend).
3. **Unit/integration tests** → record in **§5**.
4. **Verification** (restart + health check); on failure create bugfix child, retry.
5. Document (§5, §6 for error fixes).
6. **Commit** when requirement is fully resolved (per `commit-on-complete.md`).

## Commands

- **/run-tests** — Ensure §3 filled; run mvn test / npm test; record §5.
- **/verify** — After tests: restart, health check, bugfix loop if needed; on success, commit per commit-on-complete.
- **/check-backend**, **/check-db**, **/check-frontend-backend** — Health checks.

## References

- Requirement template (§3, §5): `docs/template/REQUIREMENT_TEMPLATE.md`
- Workflow: `docs/workflow/WORKFLOW_CHECKLIST.md`, `docs/workflow/DEVELOPMENT_WORKFLOW.md`
- Post-change rule: `post-change-test-verify.mdc`
- Commands: `run-tests.md`, `verify.md`, `commit-on-complete.md`
