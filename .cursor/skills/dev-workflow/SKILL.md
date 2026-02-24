---
name: dev-workflow
description: Apply dev workflow order (same for new features, errors, bugs). Requirement and test plan first; detail in docs. Optionally apply TDD or DDD cycle during development per WORKFLOW_MODES.md.
---

# Dev workflow

**Order and gates**: `docs/workflow/WORKFLOW_CHECKLIST.md`  
**Detail and examples**: `docs/workflow/DEVELOPMENT_WORKFLOW.md`  
**TDD vs DDD (methodology)**: `docs/workflow/WORKFLOW_MODES.md`

## How to use

1. At task start, read **WORKFLOW_CHECKLIST.md** to see if the current step is 1–3 (requirement doc, §3 test plan) or 4+ (development, test run).
2. **When creating a todo list (TodoWrite)** for a dev or error-fix task: use the **same order** as WORKFLOW_CHECKLIST — requirement + §3 → (spec/branch if needed) → develop → tests + §5 → verify → document. Rule: `workflow-todos.mdc`.
3. **Before development (code change)**: Confirm the requirement doc exists and §3 test case list is filled. If not, write them first, then develop.
4. **During development (step 3)**: Prefer **TDD** (RED–GREEN–REFACTOR) for new features or well-tested areas; prefer **DDD** (ANALYZE–PRESERVE–IMPROVE) for legacy or low-coverage code. See `docs/workflow/WORKFLOW_MODES.md` for when to use which and how to choose.
5. For concrete steps (restart command, test command, bugfix template, etc.), see the relevant section in **DEVELOPMENT_WORKFLOW.md**.

Keep rules and skills short; refer to the checklist and detail docs for consistency. How workflow connects to rules, commands, docs, scripts: `docs/workflow/CURSOR-AND-TOOLS-INTEGRATION.md`.
