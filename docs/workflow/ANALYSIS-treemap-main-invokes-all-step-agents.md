# Treemap: Why it shows "Main invokes all Backend/Frontend agents"

**Symptom**: The treemap shows Main → Backend, Frontend, DB, **Backend-Auth**, **Backend-Log**, **Backend-ActivityLog**, **Frontend-Auth**, **Frontend-Log**, **Frontend-ActivityLog** (i.e. Main directly invokes every agent in Step 4). The actual design is: Main → Backend / Frontend / DB only; Backend/Frontend then delegate to the module agents.

**Scope**: Cause analysis only (no fix in this doc).

---

## Root cause

The misrepresentation comes from **how flow-step invocations are built** in `scripts/generate-treemap.js`, not from the workflow docs.

### 1. Data that is correct

- **MAIN_INVOKES** (lines 268–272) correctly lists only the agents the main agent invokes: `Backend`, `Frontend`, `DB`, `Requirements`, `QA`, etc. It does **not** include `Backend-Auth`, `Backend-Log`, `Backend-ActivityLog`, `Frontend-Auth`, etc.
- **AGENT_INVOCATION_MAP** (lines 275–280) correctly encodes delegation: e.g. `Backend → [Backend-Auth, Backend-ActivityLog, Backend-Log]`, `Frontend → [Frontend-Auth, ...]`.
- **buildAgentData()** uses both: e.g. `Backend-Auth.invokedBy` is set from `AGENT_INVOCATION_MAP` (Backend), not from Main, so the **per-agent** “Invoked by” data is correct.

So the **source of truth** in the script (MAIN_INVOKES + AGENT_INVOCATION_MAP) matches SUBAGENT-DELEGATION.md. The bug is only in how the **flow steps** are rendered.

### 2. Where it goes wrong: buildFlowSteps()

In `buildFlowSteps(agents)` (lines 334–362):

1. For each step number, **all agents** in that step are collected into `stepAgents` (e.g. Step 4: Backend, Frontend, DB, Backend-Auth, Backend-Log, Backend-ActivityLog, Frontend-Auth, Frontend-Log, Frontend-ActivityLog).
2. The first invocation added is:
   ```js
   invocations.push({ from: 'Main', to: stepAgents.map(a => a.name) });
   ```
   So **Main’s targets are set to every agent in the step**, not to the subset that Main actually invokes.

3. After that, the script correctly adds Backend → [Backend-Auth, ...], Frontend → [Frontend-Auth, ...] from `AGENT_INVOCATION_MAP`.

**Result**: The flow-step diagram gets two kinds of edges:

- **Main → [Backend, Frontend, DB, Backend-Auth, Backend-Log, Backend-ActivityLog, Frontend-Auth, Frontend-Log, Frontend-ActivityLog]** (wrong: Main should not point to the module agents).
- **Backend → [Backend-Auth, Backend-ActivityLog, Backend-Log]**, **Frontend → [...]** (correct).

So the treemap **over-reports** Main’s invocations by not filtering Step 4 (and any other step) to only **MAIN_INVOKES** when building the "Main → to" edge.

### 3. Why it happens

- `buildFlowSteps` was written so that “for this step, Main invokes **all agents that belong to this step**” (stepAgents = all agents with that step number).
- The design later introduced “Main invokes only team leads; team leads delegate to module agents.” That was reflected in **MAIN_INVOKES** and **AGENT_INVOCATION_MAP**, but **buildFlowSteps** was never updated to use **MAIN_INVOKES** when building the Main → targets edge. So the flow-step view still uses the old rule “Main → everyone in the step,” which matches the wrong mental model (Main calls every Backend/Frontend agent directly).

---

## Summary

| What | Status |
|------|--------|
| MAIN_INVOKES / AGENT_INVOCATION_MAP | Correct (Main → Backend/Frontend/DB only; Backend/Frontend delegate) |
| buildAgentData() (per-agent invokedBy) | Correct |
| buildFlowSteps() (Main → who for each step) | **Wrong**: uses `stepAgents` (all agents in step) instead of `stepAgents.filter(a => MAIN_INVOKES.has(a.name))` for Main’s targets |

So the treemap shows “Main invokes all Backend/Frontend agents” **because** the flow-step builder treats “Main” as invoking every agent in the step, instead of restricting to the agents listed in **MAIN_INVOKES** for that step.
