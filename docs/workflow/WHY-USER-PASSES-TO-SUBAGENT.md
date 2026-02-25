# Why the Main Agent Asks the User to Pass Input to Subagents (Not Direct Delivery)

## Question

Why does the main agent tell the **user** to "switch to the Backend/Frontend/QA subagent and pass this input" instead of **directly** passing the input to that subagent?

---

## 1. Short Answer

- **This project uses only Cursor Settings Subagents.** Each subagent is a **separate chat** the user opens by choosing that agent in Cursor (e.g. "Backend", "Frontend", "QA").
- The **main agent runs in the default chat**. It has **no API or mechanism** to open another chat or to post a message into another agent’s session. So it cannot "directly deliver" work to a subagent.
- The only way to get work to a subagent is for the **user** to switch to that agent’s chat and paste the input. Hence the rule: *"Instruct the user to switch to that subagent and provide the exact input to pass."*

---

## 2. Technical Reason: How Subagents Work in This Project

From `docs/workflow/CURSOR-SUBAGENTS-DESIGN.md`:

> This project uses **only Cursor Settings Subagents**. Custom sub-agents (mcp_task, run-*-agent, .cursor/subagents/) are **not used**.

So:

| Mechanism | Used in this project? | How handoff works |
|-----------|------------------------|--------------------|
| **Cursor Settings Subagents** | **Yes** | User selects "Backend" / "Frontend" / "QA" etc. in Cursor → a **new/separate chat** starts with that agent’s prompt. Main agent cannot see or post into that chat. |
| **mcp_task / .cursor/subagents/** | **No** | Would allow the main agent to launch another agent with a prompt and get a result in the same flow. Not used here. |

Because only Settings Subagents are used, "delegation" is **user-mediated**: main agent produces text → user copies → user switches chat → user pastes into subagent. There is no programmatic "send to Backend agent" call.

---

## 3. Design Reason: Main Agent as Delegation-Only

From `.cursor/rules/agent-collaboration.mdc` and `docs/workflow/SUBAGENT-DELEGATION.md`:

- The **main agent (default chat)** is defined as **delegation-only**: it must not perform steps that have a dedicated subagent (requirement doc, implementation, build, restart, verify, §5/§6, commit).
- Its only actions are: (1) receive the user request, (2) identify which step(s) and which subagent(s) are needed, (3) **instruct the user** to switch to that subagent and **provide the exact input** (and expected output).

So even if there were a way to "call" a subagent from the main agent, the current design deliberately makes the main agent **not execute** those steps and instead **output instructions and input** for the user to pass on. The "user passes" flow is the defined handoff.

---

## 4. Why Not Use "Direct" Delivery (e.g. mcp_task)?

The project **could** use something like `mcp_task` so that the main agent launches a Backend/Frontend/QA agent with a prompt and gets a result without the user copying and pasting. That would be "direct" from the user’s point of view. The project has chosen **not** to use that:

- **Cursor Settings Subagents** are the single supported model (`CURSOR-SUBAGENTS-DESIGN.md`: "only Cursor Settings Subagents").
- `.cursor/subagents/` exists (e.g. for mcp_task-style prompts) but the design doc states it is **not used** for this delegation.

So "direct delivery" is not implemented by design; the handoff is explicitly user-mediated.

---

## 5. Summary Table (updated: direct invocation is default)

| Aspect | Explanation |
|--------|-------------|
| **Default** | Main agent **invokes subagent via mcp_task** (subagent_type + prompt). |
| **Fallback** | When user says "manual handoff" or mcp_task unavailable, main agent instructs the user to switch and pass input. |
| **Rule** | See `docs/workflow/SUBAGENT-DELEGATION.md` §2 and §2.2. |
---


## 6. References

- `docs/workflow/CURSOR-SUBAGENTS-DESIGN.md` — Direct invocation via mcp_task; fallback to user switch.
- `docs/workflow/SUBAGENT-DELEGATION.md` — Step → subagent table; §2.2 mcp_task invocation and fallback.
- `.cursor/rules/agent-collaboration.mdc` — Delegation gate; main agent invokes via mcp_task (or instructs user as fallback).
