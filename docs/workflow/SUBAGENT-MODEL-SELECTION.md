# Subagent model selection (token optimization)

When the main agent invokes a subagent via **mcp_task**, it can pass an optional **model** parameter to control cost and latency. You can use:

- Use **concrete model names** per §2.1 (no presets).
- **Specific model name**: Any model identifier your environment supports (e.g. Cursor’s named models). When set in §2.1 below, the main agent passes that value as `model`.

Use a **faster, lighter model** for straightforward tasks and reserve the **default or a specific capable model** for tasks that need deep reasoning or high code quality.

Reference: mcp_task accepts `model` (optional). Use concrete model names per §2.1 (e.g. `claude-haiku-4.5`, `sonnet4.6`). No presets like `fast`.

---

## 1. Recommended model by subagent

| Subagent | Recommended model | Reason |
|----------|-------------------|--------|
| **RequirementsPastSearch** | `claude-haiku-4.5` | Read-only search; narrow scope. |
| **Consistency** | `claude-haiku-4.5` | Standards doc; checklist-style. |
| **Documentation** | `claude-haiku-4.5` | User/ops docs; template-driven. |
| **Release** | `claude-haiku-4.5` | CHANGELOG, version, checklist. |
| **Requirements** | `sonnet4.6` | Orchestrates §1·§2·§3; multi-step reasoning. |
| **Security** | `sonnet4.6` | PII, access control; careful judgment. |
| **Contract** | `sonnet4.6` | API/DB contract; accuracy critical. |
| **DBA** | `sonnet4.6` | Schema design; trade-off reasoning. |
| **Architecture** | `sonnet4.6` | Performance, commonization. |
| **UX** | `sonnet4.6` | UI/design/a11y review. |
| **Backend** | `sonnet4.6` | Coding: implementation, tests, build/restart. |
| **Frontend** | `sonnet4.6` | Coding: implementation, components, tests. |
| **DB** | `sonnet4.6` | Coding: schema, migrations, init-data. |
| **Backend-Auth, Backend-Log, Backend-ActivityLog** | `sonnet4.6` | Coding: module-scoped. |
| **Frontend-Auth, Frontend-Log, Frontend-ActivityLog** | `sonnet4.6` | Coding: screen/feature-scoped. |
| **Review** | `sonnet4.6` | Contract, workflow, quality. |
| **QA** | `sonnet4.6` | Verification, §5/§6, browser automation, commit. |

---

## 2. Specifying a particular model per subagent

All subagents use a concrete model from §2.1.

### 2.1 Project override (required)

Maintain a mapping from **subagent_type** to the exact **model** value to pass. The main agent should read this and pass `model: "<value>"` when invoking mcp_task for that subagent.

| Subagent | Model (exact value to pass) | Note |
|----------|-----------------------------|------|
| RequirementsPastSearch | `claude-haiku-4.5` | Read-only search; narrow scope. |
| Consistency | `claude-haiku-4.5` | Standards doc; checklist-style. |
| Documentation | `claude-haiku-4.5` | User/ops docs; template-driven. |
| Release | `claude-haiku-4.5` | CHANGELOG, version, checklist. |
| Requirements | `sonnet4.6` | Orchestrates §1·§2·§3; multi-step reasoning. |
| Security | `sonnet4.6` | PII, access control; careful judgment. |
| Contract | `sonnet4.6` | API/DB contract; accuracy critical. |
| DBA | `sonnet4.6` | Schema design; trade-off reasoning. |
| Architecture | `sonnet4.6` | Performance, commonization. |
| UX | `sonnet4.6` | UI/design/a11y review. |
| Backend | `sonnet4.6` | Coding: implementation, tests, build/restart. |
| Frontend | `sonnet4.6` | Coding: implementation, components, tests. |
| DB | `sonnet4.6` | Coding: schema, migrations, init-data. |
| Backend-Auth | `sonnet4.6` | Coding: auth module. |
| Backend-Log | `sonnet4.6` | Coding: log module. |
| Backend-ActivityLog | `sonnet4.6` | Coding: activity log module. |
| Frontend-Auth | `sonnet4.6` | Coding: auth UI. |
| Frontend-Log | `sonnet4.6` | Coding: log UI. |
| Frontend-ActivityLog | `sonnet4.6` | Coding: activity log UI. |

- **Haiku 4.5가 두 개일 때**: Cursor Settings → Models에서 전체 식별자(예: `claude-haiku-4.5-20241022`, `anthropic/claude-haiku-4.5` 등)를 확인해 사용할 모델을 지정. provider·버전이 다른 경우 정확한 ID를 §2.1에 기입.
- 그 외: Cursor 식별자와 다르면 §2.1 값을 조정.

### 2.2 When to override (per-invocation)

- **User says "use the best model" or "highest quality"** → Use `sonnet4.6` for that invocation.
- **Task is clearly trivial** (e.g. "add one line to CHANGELOG") → Use `claude-haiku-4.5` even for a subagent that normally uses `sonnet4.6`.
- **Task is unusually complex** (e.g. cross-stack refactor, new security design) → Use `sonnet4.6` even for subagents that normally use `claude-haiku-4.5`.

---

## 3. Main agent usage

When calling **mcp_task**, always pass **model** from §2.1 for that `subagent_type`. No presets; use the exact value from the table.

Examples:

- Light (haiku):  
  `mcp_task(subagent_type: "Release", description: "CHANGELOG update", prompt: "...", model: "claude-haiku-4.5")`
- Capable (sonnet):  
  `mcp_task(subagent_type: "Backend", description: "Implement auth API", prompt: "...", model: "sonnet4.6")`

---

## 4. References

- Delegation table and mcp_task mapping: `docs/workflow/SUBAGENT-DELEGATION.md` §2.2.
- Rule that invokes subagents: `.cursor/rules/agent-collaboration.mdc`.

---

## 5. Model visibility (user-facing)

So the user can see which model each agent used:

- **Main agent**: When invoking a subagent via mcp_task, **include in the response** the subagent name and the model passed. Example: `Delegating to Backend (model: sonnet4.6)…` or `Invoking Release (model: claude-haiku-4.5)…`. This gives the user visibility into which model ran each delegated step.
- **Subagent (manual handoff)**: When the user switches to a subagent chat manually, the model is typically shown in Cursor’s UI. For mcp_task invocations, the main agent’s report is the source of visibility.
