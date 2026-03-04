# Subagent model selection (token optimization)

When the main agent invokes a subagent via the **Task tool** (referred to as "mcp_task" in project docs; see `SUBAGENT-DELEGATION.md` §2.2 for mapping), it can pass an optional **model** parameter.

Use a **faster, lighter model** for straightforward tasks and reserve the **default model** for tasks that need deep reasoning or high code quality.

---

## 1. Recommended model tier by subagent

| Subagent | Tier | Reason |
|----------|------|--------|
| **RequirementsPastSearch** | Light | Read-only search; narrow scope. |
| **Consistency** | Light | Standards doc; checklist-style. |
| **Documentation** | Light | User/ops docs; template-driven. |
| **Release** | Light | CHANGELOG, version, checklist. |
| **Requirements** | Default | Orchestrates §1·§2·§3; multi-step reasoning. |
| **Security** | Default | PII, access control; careful judgment. |
| **Contract** | Default | API/DB contract; accuracy critical. |
| **DBA** | Default | Schema design; trade-off reasoning. |
| **Architecture** | Default | Performance, commonization. |
| **UX** | Default | UI/design/a11y review. |
| **Backend** | Default | Coding: implementation, tests, build/restart. |
| **Frontend** | Default | Coding: implementation, components, tests. |
| **DB** | Default | Coding: schema, migrations, init-data. |
| **Backend-Auth, Backend-Log, Backend-ActivityLog** | Default | Coding: module-scoped. |
| **Frontend-Auth, Frontend-Log, Frontend-ActivityLog** | Default | Coding: screen/feature-scoped. |
| **Review** | Default | Contract, workflow, quality. |
| **QA** | Default | Verification, §5/§6, browser automation, commit. |

---

## 2. Task tool `model` parameter — actual constraints

The Task tool's `model` enum currently accepts only: **`fast`**.

- `fast` → fast, lightweight model for straightforward tasks (maps to **Light** tier above).
- Omit `model` → uses the default (inherits from parent), suitable for deeper reasoning (maps to **Default** tier above).

### 2.1 How to pass the model parameter

| Tier | Task tool invocation | When |
|------|---------------------|------|
| **Light** | `Task(subagent_type="Release", model="fast", ...)` | Subagent is Light tier in §1 |
| **Default** | `Task(subagent_type="Backend", ...)` (omit `model`) | Subagent is Default tier in §1 |

### 2.2 When to override (per-invocation)

- **User says "use the best model" or "highest quality"** → Omit `model` (use default) even for Light-tier subagents.
- **Task is clearly trivial** (e.g. "add one line to CHANGELOG") → Use `model="fast"` even for a subagent that normally uses Default tier.
- **Task is unusually complex** (e.g. cross-stack refactor, new security design) → Omit `model` (use default) even for Light-tier subagents.

---

## 3. User-facing reporting

When delegating, report the subagent name and tier clearly. Use natural language for the model:

- Light tier → report as "a faster model" (e.g. "Delegating to Release (faster model)…")
- Default tier → report as "default model" (e.g. "Delegating to Backend (default model)…")

Do **not** use internal model alias names (e.g. `fast`) in user-facing messages. Use natural language such as "a faster model", "a more capable model", or "the default model".

Examples:

- `Task(subagent_type="Release", model="fast", ...)` → report to user: "Delegating to Release (faster model)…"
- `Task(subagent_type="Backend", ...)` → report to user: "Delegating to Backend (default model)…"

---

## 4. References

- Tool mapping and delegation: `docs/workflow/SUBAGENT-DELEGATION.md` §2.2.
- Rule that invokes subagents: `.cursor/rules/agent-collaboration.mdc`.
