# Follow the workflow

Apply the project workflow to the **current task**: align todos with the checklist, then run in that order.

## 1. Read the checklist

Read **docs/workflow/WORKFLOW_CHECKLIST.md** and keep its order in mind.

## 2. Create a todo list that matches the checklist

Create a **TodoWrite** list whose items follow the checklist **in this order**:

1. **Requirement and §3** — Gather/analyze → requirement doc (§1, §2) → §3 test plan. (All before any code.)
2. **(If complex)** Spec and branch.
3. **Develop** — Implement in frontend/backend only.
4. **Tests and §5** — Run unit/integration tests, record results in requirement doc §5.
5. **Verify** — Restart and health check per `.cursor/commands/verify.md`; if failed, bugfix child and repeat.
6. **Document** — Update §5 and, for error fixes, §6.

Do **not** put implementation or "develop" before requirement doc and §3. The checklist is a gate.

## 3. Execute in order

Work through the todo list in order. Do not skip to development before 1–2 are done.

## References

- **Checklist**: `docs/workflow/WORKFLOW_CHECKLIST.md`
- **Rule for todo order**: `.cursor/rules/workflow-todos.mdc`
- **Detail**: `docs/workflow/DEVELOPMENT_WORKFLOW.md`
- **Verification**: `.cursor/commands/verify.md`

Principle: Modify only under **dev/**; consider the task done only after verification.
