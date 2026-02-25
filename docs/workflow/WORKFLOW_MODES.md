# Workflow Modes (TDD vs DDD)

Development methodology selection for this project. Choose **TDD** or **DDD** per task (or as default) to keep behavior predictable and maintainable.

Reference: [moai-adk workflow-modes](https://github.com/modu-ai/moai-adk/blob/main/.claude/rules/moai/workflow/workflow-modes.md). This document adapts the same idea for Cursor: no automatic phase switch; the agent and user choose the mode and follow the corresponding cycle.

## Mode summary

| Mode | Cycle | Best for | Test timing |
|------|--------|----------|-------------|
| **TDD** | RED → GREEN → REFACTOR | New features, greenfield, or code with existing tests | Test **before** implementation |
| **DDD** | ANALYZE → PRESERVE → IMPROVE | Legacy code, low/no test coverage, refactors | Characterization tests **after** understanding |

## TDD (default)

**When to use**: New feature, bugfix in well-tested area, or when §3 test cases can be written before code.

**Cycle** (within "3. 개발" in WORKFLOW_CHECKLIST):

1. **RED**: Write a failing test (or add §3 test case and implement it as a failing test). Verify it fails.
2. **GREEN**: Write minimal code to pass. No extra abstraction yet.
3. **REFACTOR**: Improve code while keeping tests green. Record in §5 after run.

**Maintenance effect**: Test-first keeps regressions visible; refactors are safe.

## DDD (legacy / low coverage)

**When to use**: Existing code with few or no tests; refactor or behavior-preserving change; "유지보수" on legacy area.

**Cycle** (within "3. 개발"):

1. **ANALYZE**: Read existing code and dependencies; map behavior and side effects.
2. **PRESERVE**: Write characterization tests (or §3 cases) that capture **current** behavior. Run and confirm they pass.
3. **IMPROVE**: Make small changes; run characterization tests after each step. Add or adjust tests only when behavior intentionally changes.

**Maintenance effect**: Avoids breaking existing behavior; tests document current behavior before changes.

## How to choose in Cursor

- **No config file**: Decide per task. When starting a task, state "TDD" or "DDD" in the requirement doc (§2 or note) or in the chat. The agent follows the chosen cycle during development (step 3).
- **Optional default**: To set a project default, add a short note in this file under "Project default" (e.g. `Default: TDD`) or in `.cursor/README.md`. The agent reads it when applying dev-workflow.
- **Heuristic**: New feature or error fix in tested code → TDD. Refactor or change in legacy, low-coverage code → DDD.

## References

- Order and gates: `docs/workflow/WORKFLOW_CHECKLIST.md`
- Detail: `docs/workflow/DEVELOPMENT_WORKFLOW.md`
- Dev workflow skill: `.cursor/skills/dev-workflow/SKILL.md`
