# Subagent delegation (all steps)

## Gate: before performing any step

**Before** you perform any of these — requirement doc, implementation (code edit in frontend/backend), build, restart, verification, §5/§6 update, commit — **check**: does this step have a dedicated subagent in the table below? Did the user **not** say "code only here", "skip subagent", or "do it in this chat"? If both are true → **do not perform the step**. **Invoke that subagent via mcp_task** (see §2.2) with the handoff input as the prompt. Performing in the current chat is allowed only when the user explicitly requested it.

When the user requests a **requirement**, **feature**, or **error fix**, work that belongs to a dedicated subagent is **delegated by direct invocation**: the main agent calls **mcp_task** with the matching `subagent_type` and the handoff prompt. The main agent does not perform that step in the main chat unless the user explicitly says "code only here", "skip subagent", or "do it in this chat".

This document covers **all collaboration steps** (1–6). Role boundaries: `docs/workflow/CURSOR-SUBAGENTS-DESIGN.md` §2.6.

---

## 1. Delegation table (Step → Subagent)

| Step | Subagent | When to delegate | Main agent instructs user to |
|------|----------|------------------|-------------------------------|
| **1** | **Requirements** | New requirement or error fix needs a formal requirement doc (§1, §2, §3). | Switch to **Requirements** subagent; provide the user request or error message. **During authoring**, Requirements obtains **parallel** input from experts (Security, Contract, UX, DBA, Architecture, Consistency) and from **Backend/Frontend/DB/QA** (scenario, codebase summary, problem analysis, solution) per `docs/workflow/AGENT-COLLABORATION-ON-REQUIREMENT.md` §1.1; **orchestrates** (merges) into §1·§2; §2 "변경 파일 목록" is **tentative**; then finalizes §3. Output: requirement doc. **After doc is complete**, proceed to Step 2/3/4; each **responsible subagent** then performs its step in sequence. |
| **2** | **Security** | Requirement involves PII, decryption scope, or access control. | Switch to **Security** subagent with the requirement doc (§1·§2). Output: §2.1 Security review. Then Step 3 or 4. |
| **3** | **Contract** | API or DB contract/spec change. | Switch to **Contract** subagent with requirement doc (and security if any). Output: updated `docs/contract.md`, specs. Then Step 4. |
| **3b** | **DBA** | Schema design, indexing, JSON vs relational. | Switch to **DBA** subagent with requirement doc and schema/spec. Output: design review; no code. DB implements. |
| **3c** | **Architecture** | Performance, scalability, caching, load. | Switch to **Architecture** subagent with requirement doc and design. Output: design review; no code. Backend/DB implement. |
| **3d** | **Consistency** | New conventions, error codes, or standards. | Switch to **Consistency** subagent. Output: updated `CONSISTENCY-STANDARDS.md`. Review applies it. |
| **3d** | **UX** | UI, layout, design, or a11y. | Switch to **UX** subagent with requirement doc §1·§2 and UI description. Output: UX review or design recommendations. Then **Frontend** implements. |
| **4** | **Backend / Frontend / DB** | Implementation in `backend/`, `frontend/`, or DB only. | Switch to **Backend**, **Frontend**, or **DB** subagent with requirement doc, §3, contract/spec, and any review output. They implement, build, restart; **confirm or update** the requirement doc §2 **변경 파일 목록** (change file list) with the **actual** files changed; then hand off to QA. When they need detail the requirement doc does not specify and that belongs to an expert (UX, Contract, DBA, Security, Consistency), they **invoke that expert subagent** via mcp_task with a focused question (or ask the main agent to invoke); they do not assume. See `AGENT-COLLABORATION-ON-REQUIREMENT.md` §1.2 (change file list), §1.3 (query experts). |
| **4.5** | **Review** | Optional: review change before QA. | Switch to **Review** subagent with the implemented change. Output: review report vs contract, workflow, CONSISTENCY-STANDARDS. Implementing agent fixes, then Step 5. |
| **5** | **QA** | After implementation and build/restart. | Switch to **QA** subagent with requirement doc §3 and confirmation that build/restart is done. Output: verification (verify checklist, health/behavior). For **frontend** changes, QA **must** run **browser automation** (step 3.5) when MCP is available and write a **detailed report** in §5 (per-TC Pass/Fail; for each Fail: what was checked, expected, actual). If any browser check **fails**, QA creates a **bugfix child** requirement and **hands off to Frontend** to fix; QA re-verifies after fix. Then §5 (and §6 for error fixes) and **commit** when all pass. See `docs/workflow/BROWSER-AUTOMATION-VERIFICATION-POLICY.md`. |
| **6** | **Documentation** | User/ops docs (README, QUICK_START, runbooks). | Switch to **Documentation** subagent with completed feature and requirement doc. Output: updated user/ops docs. No requirement docs, no code. |
| **6** | **Release** | CHANGELOG, version, release checklist. | Switch to **Release** subagent with completed requirement(s) and commit scope. Output: CHANGELOG entry, release checklist. No user guides, no code. |

---

## 2. Main agent behavior (default chat) — direct invocation

The main agent **delegates by directly invoking** subagents. It does **not** execute steps that have a dedicated subagent.

1. **Identify** which step(s) the user request needs (requirement doc → security? → contract? → implementation → review? → QA → docs/release?).
2. **For each step** that has a dedicated subagent in the table above:
   - **Do not perform** that step in the main chat: do not write the requirement doc, do not implement, do not run build/restart, do not run verification, do not update §5/§6, do not commit.
   - **Invoke the subagent via mcp_task**: use the `subagent_type` from §2.2, and set `prompt` to the **exact handoff input** (requirement doc path, task description, expected output). Set `description` to a short 3–5 word summary. The subagent runs with that prompt and returns the result.
3. **Commit**: When delegation is in effect, the main agent does **not** commit. After QA completes verification and §5/§6, the **QA subagent** performs commit per `.cursor/commands/commit-on-complete.md`. If the user requested push (e.g. "push해줘", "push 해주세요"), include that in the handoff to QA so QA runs `git push` after commit. See §5 below.
4. **Exception**: If the user explicitly says "code only here", "skip subagent", or "do it in this chat", the main agent may perform the relevant step(s) in the current chat (including build, verify, commit).

### 2.2 Direct invocation via mcp_task (and fallback)

- **Preferred**: Main agent calls **mcp_task** with:
  - **subagent_type**: one of Requirements, Security, Contract, DBA, Architecture, Consistency, UX, Backend, Frontend, DB, Review, QA, Documentation, Release (must match the step; see table below).
  - **prompt**: the full handoff text (e.g. requirement doc path, user request or error message, task description, expected output).
  - **description**: short task summary (3–5 words).
- **Step → subagent_type mapping** (for mcp_task):  
  Step 1 → Requirements | Step 2 → Security | Step 3 → Contract, DBA, Architecture, Consistency, UX (as needed) | Step 4 → Backend, Frontend, DB | Step 4.5 → Review | Step 5 → QA | Step 6 → Documentation, Release.  
  For module-specific work use Backend-Auth, Backend-Log, Frontend-Auth, etc. when applicable.
- **Fallback**: If the user says "manual handoff" or "I'll switch myself", or if mcp_task is unavailable, **instruct the user** to switch to that subagent (Cursor Settings) and provide the same handoff input to pass.

---

## 2.1 Build and restart (mandatory for Step 4)

- **Frontend / Backend** subagents: When they modify code or config, they **must run build and restart themselves** (from project root) and **include in their handoff** a one-line confirmation, e.g. `Build: [command] exit [code]. Restart: [command] done. QA verification requested.` They do **not** ask the user to run restart; the subagent performs it.
- **QA** subagent: Performs verification **only after** build and restart are confirmed (from the handoff or by running them). If the handoff does not confirm build/restart, QA **runs the appropriate build and restart itself**, then runs verification. Do not ask the user to run restart; the subagent handles it. This avoids verification on a stale run.

---

## 3. Step 4 (implementation) in detail

- **frontend/** (UI, layout, components, styles, API calls from frontend) → **Frontend** subagent.
- **backend/** (Java, controllers, services, config; excluding DB-only) → **Backend** subagent.
- **backend/.../db/** only (schema, migrations, setup scripts) → **DB** subagent.
- **Change file list**: After implementation, the implementing subagent (Backend, Frontend, DB, or module-specific e.g. Backend-Log) **must confirm or update** the requirement doc §2 **"변경 파일 목록"** with the **actual** list of files changed (so the doc is not left with only the tentative list from Step 1). If the section is missing, add it. See `AGENT-COLLABORATION-ON-REQUIREMENT.md` §1.2.

For **UI/layout/design** changes: optionally recommend **UX** subagent first (Step 3d), then **Frontend** (Step 4).

---

## 4. Minimal flow and delegation

Even in the minimal flow (requirement doc → implement → QA):

- **Step 1** can be delegated to **Requirements** (requirement doc + §3).
- **Step 4** must be delegated to **Frontend** / **Backend** / **DB** (no implementation in main chat).
- **Step 5** can be delegated to **QA** (verification, §5/§6).

So the main agent typically: (1) **invokes the Requirements subagent via mcp_task** with the user request or error message as the prompt — the main agent **does not write** the requirement doc in the main chat; (2) after the requirement doc (§1, §2, §3) exists, invokes the implementing subagent (Frontend/Backend/DB) via mcp_task with the doc and task; (3) after implementation (and build/restart by Frontend/Backend), invokes QA via mcp_task for verification. The main agent does not run build, restart, verification, or commit.

---

## 5. After QA: commit and push

- **QA subagent** performs verification per `.cursor/commands/verify.md`, updates §5 (and §6 for error fixes), then **performs commit** per `.cursor/commands/commit-on-complete.md`. **When the user requested push** (e.g. "push해줘", "push 해주세요"), QA runs `git push` after the commit; otherwise QA does not push.
- The **main agent** does not commit when delegation is in effect. When the user asks to push, the main agent should either (1) include "user requested push" in the handoff to QA so QA commits and pushes, or (2) delegate to **Release** with "commit and push all current changes" if the context is release/chore (e.g. "지금까지 변경된 내용 push해줘").

---

## 6. References

- Collaboration sequence: `docs/workflow/AGENT-COLLABORATION-ON-REQUIREMENT.md`
- Subagent roles and scope: `docs/workflow/CURSOR-SUBAGENTS-DESIGN.md` §1, §2, §2.6
- Rule that enforces delegation: `.cursor/rules/agent-collaboration.mdc` §5
