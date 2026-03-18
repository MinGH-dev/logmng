# Development Workflow Guide

For the short version, read `WORKFLOW_CHECKLIST.md` first. This document provides the detailed guide that workflow rules, commands, and skills refer to.

## 1. Purpose

Use this guide for requirement-driven work, bug fixes, and workflow-aligned implementation in this repository.

The required order is:

1. requirement analysis
2. requirement doc
3. §3 test plan
4. implementation
5. test execution
6. verification
7. documentation update
8. commit

## 2. Working assumptions

- Work in the repository workspace only.
- Keep requirement docs under `docs/requirements/`.
- Keep specs under `specs/`.
- Keep workflow and template documents under `docs/workflow/` and `docs/template/`.
- Use a feature branch such as `feat/<feature-key>`.

## 3. Requirement vs spec

- **Requirement document**: what the user needs and why
- **Specification**: how the system should implement it

Use a requirement doc first. Add or update specs when the change needs technical contract detail.

## 4. Detailed flow

### Step 1. Collect and analyze the requirement

- Understand the user goal, scenario, and expected outcome.
- Inspect the current implementation and the affected area.
- Identify whether Security, Contract, DBA, Architecture, Consistency, or UX review is needed.

### Step 2. Write the requirement document

- Create or update `docs/requirements/yyyyMMdd-name.md`.
- Fill §1 and §2 in English.
- Add the planned change file list in §2.
- If the change affects tool-facing domain knowledge, include a **Cursor tool update targets** subsection.

### Step 3. Define the §3 test plan before implementation

- List normal, exception, and edge cases.
- Tag each TC with a scope such as Backend, Frontend, DB, or Integration.
- If browser automation is relevant, add §3.5.

### Step 4. Implement

- Follow the requirement doc, contract, and relevant standards.
- Use scope ownership correctly: frontend work in `frontend/`, backend work in `backend/`, DB work in DB-owned files.
- If the change is subagent-owned, use the appropriate subagent and pass an English handoff prompt.

**For error fixes:** Do not implement based on a suspected cause. Before changing logic, complete a **diagnostic phase**: add diagnostic (debug) logs in suspected areas (e.g. key variables, branch outcomes, per-item results), reproduce the error, capture logs, and analyze them to confirm the root cause. Only after the cause is confirmed from logs, implement the fix. Diagnostic logs used for this verification must **not** run in production (use DEBUG level, a feature flag / dev-only path, or remove/reduce them after the fix is verified).

### Step 5. Run tests

- Backend: run `mvn test` or the required backend test command.
- Frontend: run `npm test -- --watchAll=false` or the required frontend test command.
- Record results in requirement-doc §5.

### Step 6. Verify on a restarted application

- Restart the affected service(s).
- Run health checks and behavior checks.
- If verification fails, create or update the bugfix path and repeat the loop until verification passes.

### Step 7. Update documentation

- Update requirement-doc §5, and §6 for error fixes.
- Change the checklist item `- [ ] Requirement doc completed` to `- [x] Requirement doc completed` only when the requirement doc has reached its final completed state after tests/verification. This checklist transition is the canonical completion trigger used by automation, and it is also when `TOPIC-INDEX.md` auto-maintenance runs for that document.
- Update related docs if the change affects release, contract, or user/ops documentation.

### Step 8. Commit

- Commit only after tests and verification pass.
- Reference the requirement doc in the commit message.
- Push only when the user explicitly asks for it or when the workflow hands the final release-and-push step to Release.

## 5. Verification checklist

### Frontend

- API parameters are correct
- UI behavior matches the requirement
- Error handling is correct

### Backend

- Input validation is correct
- Business logic matches the requirement
- Error handling is correct
- Logs are appropriate

### Integration

- End-to-end flow passes
- Edge cases are covered

## 6. Language rule for workflow execution

- User-facing assistant replies follow the user's requested language.
- Tool-facing workflow docs, copied handoff text, and Task prompt payloads must be in English.

## 7. References

- `docs/workflow/WORKFLOW_CHECKLIST.md`
- `docs/workflow/ERROR-FIX-WORKFLOW-FLOWCHART.md`
- `docs/workflow/CURSOR-AND-TOOLS-INTEGRATION.md`
- `docs/template/REQUIREMENT_TEMPLATE.md`
- `.cursor/commands/run-tests.md`
- `.cursor/commands/verify.md`
- `.cursor/commands/commit-on-complete.md`
