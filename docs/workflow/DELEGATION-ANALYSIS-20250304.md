# Delegation analysis: why main agent wrote test code (2025-03-04)

## What happened

The user asked to run unperformed unit tests and improve where needed for requirement `20250304-team-scope-default-and-approval`. The **main agent** implemented backend unit tests (ScopeHelperTest, DepartmentScopeHelperTest) in the main chat instead of delegating to the **Backend** subagent.

## Root cause

1. **Gate wording**: The delegation gate in `agent-collaboration.mdc` and `SUBAGENT-DELEGATION.md` referred to "implementation" and "code edit" but did not **explicitly** state that **writing or modifying unit/integration test code** is Step 4 (Backend/Frontend) and must be delegated. So the main agent could treat "perform unit tests" as a mix of "run tests" (QA) and "improve" (generic), and chose to add test code itself.

2. **Request ambiguity**: "수행되지 않은 단위테스트를 모두 수행" can mean both "**run** the unit tests" and "**implement** the unit tests". Without an explicit rule that "adding test code = implementation = delegate to Backend", the main agent did not consistently classify test **implementation** as Step 4.

3. **Table visibility**: The Step 4 row in the delegation table did not mention "test code"; only §3 "Mandatory tests" said that Backend/Frontend *must implement* tests. The **gate** (what the main agent must not do) did not explicitly say "do not write test code — invoke Backend/Frontend."

## Changes made

- **`.cursor/rules/agent-collaboration.mdc`**: Added an explicit line that **implementation (Step 4) includes writing or modifying unit/integration test code** in backend/ or frontend/, and that such work must be delegated to Backend or Frontend (no test code in main chat).
- **`docs/workflow/SUBAGENT-DELEGATION.md`**:
  - Step 4 row: "Implementation" now explicitly includes "writing or modifying unit/integration test code (e.g. JUnit, Jest)."
  - §3 "Step 4 in detail": New bullet **"Test code"** stating that adding or modifying unit/integration test code is Step 4 and must be delegated to Backend or Frontend; the main agent must not write test code in the main chat.

## Expected behavior after fix

When the user asks to "perform unperformed unit tests" or "add missing unit tests" for a requirement:

- **Implementing** missing backend (frontend) unit/integration tests → **Backend** (Frontend) subagent via Task tool.
- **Running** tests and recording §5 → after implementation, **QA** subagent (or Backend/Frontend as part of build; §5 update by QA).

Main agent: identifies that "add/implement unit tests" is Step 4, invokes Backend (or Frontend) with the requirement doc and task; does not write test code in the main chat unless the user said "code only here" / "do it in this chat".
