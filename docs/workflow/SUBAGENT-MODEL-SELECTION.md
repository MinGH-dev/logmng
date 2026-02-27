# Subagent model selection (token optimization)

When the main agent invokes a subagent via **mcp_task**, it can pass an optional **model** parameter to control cost and latency. You can use:

- **Presets**: `fast` (lighter, lower cost) or omit for **default** (more capable).
- **Specific model name**: Any model identifier your environment supports (e.g. Cursor’s named models). When set in §2.1 below, the main agent passes that value as `model`.

Use a **faster, lighter model** for straightforward tasks and reserve the **default or a specific capable model** for tasks that need deep reasoning or high code quality.

Reference: mcp_task accepts `model` (optional). Prefer `fast` for quick, scoped tasks; use a specific model name when you want to pin an agent to that model; omit for default.

---

## 1. Recommended model by subagent

| Subagent | Recommended model | Reason |
|----------|-------------------|--------|
| **RequirementsPastSearch** | **fast** | Read-only search over past docs; narrow scope, summary output. |
| **Consistency** | **fast** | Update standards doc against conventions; checklist-style. |
| **Documentation** | **fast** | User/ops docs, README, runbooks; template-driven. |
| **Release** | **fast** | CHANGELOG, version, release checklist; formulaic. |
| **Requirements** | default | Orchestrates §1·§2·§3, merges expert feedback; multi-step reasoning. |
| **Security** | default | PII, access control, decryption scope; careful judgment. |
| **Contract** | default | API/DB contract and specs; single source of truth, accuracy critical. |
| **DBA** | default | Schema/index design, JSON vs relational; trade-off reasoning. |
| **Architecture** | default | Performance, scalability, commonization; design trade-offs. |
| **UX** | default | UI/design/a11y review; consistency and creativity. |
| **Backend** | default | Implementation, tests, build/restart; code quality and correctness. |
| **Frontend** | default | Implementation, components, tests; code quality and correctness. |
| **DB** | default | Schema, migrations, init-data; correctness and contract alignment. |
| **Backend-Auth, Backend-Log, Backend-ActivityLog** | **fast** | Module-scoped implementation; well-defined scope. |
| **Frontend-Auth, Frontend-Log, Frontend-ActivityLog** | **fast** | Screen/feature-scoped implementation; well-defined scope. |
| **Review** | default | Contract, workflow, CONSISTENCY-STANDARDS; quality judgment. |
| **QA** | default | Verification checklist, §5/§6, browser automation, commit; systematic execution. |

---

## 2. Specifying a particular model per subagent

You **can** assign a **specific model** to each subagent. If your Cursor / mcp_task setup exposes named models (e.g. `fast`, or concrete model IDs), do either of the following.

### 2.1 Project override (optional)

Maintain a mapping from **subagent_type** to the exact **model** value to pass. The main agent should read this and pass `model: "<value>"` when invoking mcp_task for that subagent.

| Subagent | Model (exact value to pass) | Note |
|----------|-----------------------------|------|
| Backend | `sonnet4.6` | Coding: implementation, tests, build/restart. |
| Frontend | `sonnet4.6` | Coding: implementation, components, tests. |
| DB | `sonnet4.6` | Coding: schema, migrations, init-data. |
| Backend-Auth | `sonnet4.6` | Coding: auth module. |
| Backend-Log | `sonnet4.6` | Coding: log module. |
| Backend-ActivityLog | `sonnet4.6` | Coding: activity log module. |
| Frontend-Auth | `sonnet4.6` | Coding: auth UI. |
| Frontend-Log | `sonnet4.6` | Coding: log UI. |
| Frontend-ActivityLog | `sonnet4.6` | Coding: activity log UI. |

- Subagents not listed here use the recommendation from §1 (fast or default).
- Add rows for other subagents when you want a specific model (e.g. Security → a named “capable” model, Documentation → `fast`).
- If a subagent is not listed here, the main agent uses the recommendation from §1 (fast or omit for default).

### 2.2 When to override (per-invocation)

- **User says "use the best model" or "highest quality"** → Omit `model` (or use default) for that invocation.
- **Task is clearly trivial** (e.g. "add one line to CHANGELOG") → Use **fast** even for a subagent that normally uses default.
- **Task is unusually complex** (e.g. cross-stack refactor, new security design) → Use default (or more capable model if available) even for subagents that can often use **fast**.

---

## 3. Main agent usage

When calling **mcp_task**:

1. If **§2.1** defines a model for that `subagent_type`, pass **model**: that value.
2. Else, if the recommendation in **§1** is **fast**, pass **model**: `"fast"`.
3. Otherwise omit **model** (use default).

Examples:

- Preset fast:  
  `mcp_task(subagent_type: "RequirementsPastSearch", description: "Summarize past requirements", prompt: "...", model: "fast")`
- Specific model (when §2.1 says e.g. Security → `my-secure-model`):  
  `mcp_task(subagent_type: "Security", description: "Security review", prompt: "...", model: "my-secure-model")`
- Default (no model):  
  `mcp_task(subagent_type: "Backend", description: "Implement auth API", prompt: "...")`

---

## 4. References

- Delegation table and mcp_task mapping: `docs/workflow/SUBAGENT-DELEGATION.md` §2.2.
- Rule that invokes subagents: `.cursor/rules/agent-collaboration.mdc`.

---

## 5. Model visibility (user-facing)

So the user can see which model each agent used:

- **Main agent**: When invoking a subagent via mcp_task, **include in the response** the subagent name and the model passed. Example: `Delegating to Backend (model: sonnet4.6)…` or `Invoking QA (model: default)…`. This gives the user visibility into which model ran each delegated step.
- **Subagent (manual handoff)**: When the user switches to a subagent chat manually, the model is typically shown in Cursor’s UI. For mcp_task invocations, the main agent’s report is the source of visibility.
