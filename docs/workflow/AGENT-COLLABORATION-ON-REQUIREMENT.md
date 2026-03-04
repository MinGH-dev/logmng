# Agent Collaboration on Requirement

When a **new requirement** or **error-fix request** occurs, agents collaborate in a defined order. This document is the **single reference** for who does what, in what sequence, and what each handoff produces. Use it so that all agents (including Review, Documentation, Release, Consistency, UX) work together **without role duplication**. Role boundaries: `docs/workflow/CURSOR-SUBAGENTS-DESIGN.md` §2.6.

---

## 1. Collaboration sequence

| Step | Agent(s) | Input | Output | Handoff to |
|------|-----------|--------|--------|------------|
| **1** | **Requirements** | User request, error message, or feature need | Requirement doc with §1, §2, §3. **During authoring**: obtain **parallel** input from experts and Backend/Frontend/DB/QA (scenario, codebase, problem, solution); **orchestrate** into §1·§2; §2 "변경 파일 목록" is **tentative** (see §1.1, §1.2). | Step 2 if security-relevant; else Step 3 or 4 |
| **2** | **Security** (if PII / decryption / access control) | Requirement doc §1·§2 | §2.1 Security review or security appendix. | Step 3 or 4 |
| **3** | **Contract** (if API or DB change) | Requirement doc + security if any | Updated `docs/contract.md`, `specs/*.spec.yaml`. | Step 4 |
| **3b** | **DBA** (if schema / indexing / JSON design) | Requirement doc, schema or spec | Design review; no code. DB implements. | Step 4 |
| **3c** | **Architecture** (if performance / scale / caching **or when requirement involves frontend/backend** for commonization) | Requirement doc, design or spec | Design review (performance and **commonization**: frontend/backend shared or common functionality); no code. Backend/Frontend/DB implement. | Step 4 |
| **3d** | **Consistency** (if new conventions / error codes) | Requirement doc or new convention need | Updated `docs/workflow/CONSISTENCY-STANDARDS.md`. Review applies it. | Step 4, Review |
| **3d** | **UX** (if UI / design / a11y) | Requirement doc §1·§2, UI description | § UX review or design recommendations. No code. Frontend implements. | Step 4 |
| **4** | **Backend / Frontend / DB** | Requirement doc, §3, contract/spec, reviews | Code and config; unit/integration tests; **build and restart** (required). **Confirm or update** requirement doc §2 **변경 파일 목록** with actual files changed. When detail is missing, **query the owning expert subagent** (see §1.3). | Step 4.5 or 5 |
| **4.5** | **Review** (optional, before QA) | Implemented change (Step 4) | Review report vs contract, workflow, quality, `CONSISTENCY-STANDARDS.md`. No code. Implementing agent fixes. | Step 5 |
| **5** | **QA** | Requirement doc §3, implemented feature **after build and restart** | **Verification**: verify checklist, health/behavior check; test scenarios; §5 (and §6 for error fixes). | Step 6 or Done |
| **6** | **Documentation** (after QA) | Completed feature, requirement doc | Updated user/ops docs (README, QUICK_START, runbooks). No requirement docs, no code. | Done |
| **6** | **Release** (with or after Documentation) | Completed requirement(s), commit scope | CHANGELOG entry, release checklist update. No user guides, no code. | Done |

- **Gate**: Step 1 (requirement doc + §3) must be done **before** any code (Step 4). Same for error fixes.
- **Optional steps**: Security (PII/decrypt/access); Contract (API/DB change); DBA (schema); Architecture (performance **and commonization when frontend/backend**); Consistency (new conventions); UX (UI/design); Review (before QA).
- **Single source of truth**: Requirement doc is the hub. Each agent updates only its designated sections or owned docs (e.g. Consistency → CONSISTENCY-STANDARDS.md; Review applies it but does not edit it).
- **Response language**: Agents respond to the user in the **user's requested language** (e.g. Korean when the user writes in Korean). See `.cursor/rules/language-policy.mdc`.
- **Cursor infrastructure update**: When a requirement changes the domain model (e.g. permission model, data schema), the relevant `.cursor/skills/` files must be updated to reflect the new model. See §1.4.

### 1.1 Requirements authoring: parallel consensus and orchestration

When the **Requirements** subagent writes the requirement doc, it **must not write §1 (user scenario, expected outcome) and §2 (codebase summary, problem analysis, solution) from its own judgment alone**. Instead it **obtains input in parallel from experts and from development/DB/QA**, then **orchestrates** (merges) that input into §1·§2.

1. **Past user requests (when user has not explicitly requested a change)**  
   When the user has **not** explicitly requested a change to prior behavior or scope, **invoke RequirementsPastSearch** (via the Task tool with `subagent_type="RequirementsPastSearch"` when available, or instruct the user to switch and pass the topic/feature). Pass: "Topic: [topic]. What did the user recently request in past requirements? Summarize so we preserve it (max 300 words, bullets)." RequirementsPastSearch uses `docs/requirements/TOPIC-INDEX.md` and reads only §1 of relevant docs for token efficiency. Use the summary when drafting §1·§2 so continuity is maintained. If the user **has** explicitly requested a change (e.g. "이거 바꿔줘", "검색 필드를 A, B로만"), do not override that with past content.
2. **Parallel invocation (consensus input)**  
   Invoke **in parallel** (e.g. multiple Task tool calls in one turn) the following, as applicable to the user request or error:
   - **Experts** (when the requirement touches their domain):
     - **Security**: PII, decryption scope, or access control → request §2.1 or security appendix.
     - **Contract**: API or DB contract/spec change → request contract/spec constraints for §2.
     - **DBA**: schema, indexing, or data design → request design review or constraints for §2.
     - **Architecture**: performance, scalability, or load → request design review or constraints for §2; **and whenever the requirement involves frontend and/or backend implementation** → request **commonization review** (identify shared or common functionality that could be reflected in §2) so the requirement can reflect commonization.
     - **Consistency**: new conventions or error codes → request standards or constraints for §2.
     - **UX**: UI, layout, or a11y → request UX review or design recommendations for §2.
   - **Development / DB / QA** (for scenario, codebase, problem, solution):
     - **Backend** (or Backend-Log, Backend-Auth, etc. when scope is clear): codebase summary for backend area, problem analysis, solution ideas for backend.
     - **Frontend** (or Frontend-Log, etc. when scope is clear): codebase summary for frontend area, problem analysis, solution ideas for frontend.
     - **DB**: when schema/DB is involved — codebase summary for DB area, problem analysis, solution ideas for DB.
     - **QA**: user scenario testability, alignment with §3 test cases, edge/regression suggestions.
   - Pass to each: user request or error message, and ask for: (a) user scenario / expected outcome input for §1, (b) codebase summary for their area, (c) problem analysis, (d) solution approach. **State clearly that this is for requirement authoring only: do not implement; return only structured input for §1·§2.** Experts may return only (b)–(d) in their domain; QA focuses on (a) and testability.
3. **Orchestrate**  
   Merge the collected responses into:
   - **§1**: user requirement (description), **user scenario**, expected outcome (consensus from experts + Backend/Frontend/DB + QA where provided).
   - **§2**: **codebase summary** (per area), **problem analysis**, **solution approach**, and **변경 파일 목록 (예상)** — tentative change file list; see §1.2.
4. **Finalize**  
   Complete **§3** (test plan) and the requirement doc. When the doc is complete, the flow continues: Step 2 (Security if needed), Step 3 (Contract/DBA/… if needed), then **Step 4** — the responsible subagent implements; after implementation, **Step 5** (QA) and so on.

If the requirement is trivial or purely textual (no codebase/solution), Requirements may skip parallel invocation and write §1·§2 directly, then §3. **When the requirement involves frontend and/or backend implementation**, Requirements **should always** invoke **Architecture** (in addition to any performance/scale need) for **commonization review** so that shared or common functionality can be reflected in §2.

### 1.2 Change file list: tentative in §2, confirmed in Step 4

- The requirement doc §2 **"변경 파일 목록"** is **tentative (예상)** when authored. Requirements (and parallel inputs) may list **expected** files; the **implementing agent (Step 4)** — Backend, Frontend, DB, or module-specific (e.g. Backend-Log) — **must confirm or update** this list when implementation is complete.
- The implementing subagent **updates the requirement doc** §2 "변경 파일 목록" with the **actual** list of files it changed (add/remove/amend vs the tentative list). If **multiple** implementing agents work on the same requirement (e.g. Backend and Frontend), each updates the list with the files **it** changed (add or amend its own section). If the requirement doc has **no** "변경 파일 목록" section (e.g. older docs), the implementing agent **adds** it with the actual files changed. This keeps the requirement doc and implementation scope in sync.

### 1.3 Development subagents: query experts when detail is needed

When **Backend**, **Frontend**, or **DB** (Step 4) implement from the requirement doc and need **detailed information** that the doc does not fully specify and that **falls in another agent's domain**, they must **query that expert subagent** instead of assuming:

- **UX** (layout, design, a11y, interaction): e.g. "Requirement doc X §2 — need exact layout/breakpoints for component Y."
- **Contract** (API shape, request/response, env): e.g. "Requirement doc X — need exact request body and response shape for endpoint Z."
- **DBA** (schema, indexes, JSON vs relational): e.g. "Requirement doc X — need final column list and index recommendation for table T."
- **Security** (access rules, PII handling): e.g. "Requirement doc X — need access rule for role R on resource S."
- **Consistency** (naming, error codes): e.g. "Requirement doc X — need error code and message for case C."

**How to query**: Invoke the expert subagent via the **Task tool** with a short description and the requirement doc path (e.g. `Task(subagent_type="UX", prompt="Requirement doc: docs/requirements/yyyyMMdd-name.md. Question: [focused question]. Please return [expected output].")`). If the Task tool is unavailable, ask the user to have the main agent invoke that subagent with the same question. **Do not invent** answers in another agent's domain; get the answer from the owning agent, then continue implementation.

### 1.4 Cursor infrastructure update: when a domain model changes

When a requirement **changes the domain model** (e.g. permission model from multi-group to single-group, data schema restructuring, workflow change), the **Cursor infrastructure files** (`.cursor/skills/`, `specs/`, `.cursor/rules/`) may become stale and cause agents to produce incorrect implementations.

**Procedure**:

1. **Requirements (Step 1)**: When authoring §2, identify which `.cursor/skills/{domain}/` files and `specs/*.spec.yaml` files describe the changing domain. List them in §2 under **"Cursor 도구 업데이트 대상"** (Cursor tool update targets).
2. **Contract (Step 3)**: Updates `specs/*.spec.yaml` as usual — this covers spec files.
3. **Implementing agent (Step 4)**: Before or as part of implementation, update the `.cursor/skills/{domain}/` files listed in §2 to reflect the new domain model. This ensures other agents (and future invocations) read correct domain knowledge. Ownership: per `CURSOR-SUBAGENTS-DESIGN.md` §2.6 — each implementing agent owns skills for its domain.
4. **If no implementing agent is invoked** (e.g. the main agent performs "do it in this chat"): the main agent updates both skills and specs directly.

**Why**: Skills and specs serve as domain knowledge for all agents. If they describe an outdated model, agents will generate code based on stale assumptions.

---

## 2. Handoff rules

- **Requirements → Security / Contract / Consistency / UX**: "Requirement doc draft ready; please review or update your area."
- **Security → Contract / Backend / Frontend**: "Security review done; design shall follow §2.1."
- **Contract → Backend / Frontend**: "Contract and spec updated; implement to contract and specs."
- **DBA / Architecture → DB / Backend**: "Review done; apply recommendations in schema or code."
- **Consistency → Review / Backend / Frontend / DB**: "Standards doc updated; apply when implementing or when Review checks."
- **UX → Frontend**: "UX review done; implement per recommendations."
- **Backend / Frontend / DB → Review (optional)**: "Change ready; please review against contract, workflow, and CONSISTENCY-STANDARDS."
- **Review → Backend / Frontend / DB**: "Review report: [items]. Please fix and re-submit or proceed to QA."
- **Backend / Frontend / DB → QA**: "Implementation done; **build and restart completed**; requirement doc §2 **변경 파일 목록** confirmed/updated with actual files. Please perform **verification**: run verify checklist, health/behavior check, then add/update §5 (and §6 if error fix)."
- **QA → Requirements** (on verification failure): "Verification failed; bugfix child created at [path]; **failure scope**: [frontend | backend | db | security | contract | ux | …]. Please formalize the bugfix doc (§1·§2·§3) and **delegate to the responsible expert** by scope. Do not implement."
- **Requirements → Backend | Frontend | DB | Security | Contract | UX** (by failure scope): "Bugfix child [path]; please fix per doc. When **issue closed** (fix + build/restart done), hand off to **QA** for re-verification. QA will re-run verification; when all pass, QA commits."
- **Backend / Frontend / DB (after fixing bugfix) → QA**: "Issue closed; build/restart done. Please **re-run verification** (verify checklist, and browser automation if frontend). Update §5; if all pass, commit per commit-on-complete."
- **QA → Documentation / Release / Done**: "§5 (and §6 if error fix) updated; verification done. Optionally update user docs and CHANGELOG."
- **Documentation / Release → Done**: "User docs / CHANGELOG updated."

---

## 3. Delegation (main agent → subagent)

When the user is in the **default (main) chat**, the main agent **does not perform** work that belongs to a dedicated subagent. Instead it **instructs the user** to switch to that subagent and pass the right input (requirement doc, context, etc.). This applies to **all steps** (1–6): Requirements, Security, Contract, DBA, Architecture, Consistency, UX, Frontend, Backend, DB, Review, QA, Documentation, Release.

- **Full delegation table** (Step → Subagent → what to pass): `docs/workflow/SUBAGENT-DELEGATION.md`
- **Model per subagent** (token optimization, user visibility): `docs/workflow/SUBAGENT-MODEL-SELECTION.md` — main agent passes `model` when invoking the Task tool and reports it to the user.
- **Rule**: `.cursor/rules/agent-collaboration.mdc` §5

Exception: if the user says "code only here", "skip subagent", or "do it in this chat", the main agent may perform the relevant step(s) in the current chat.

---

## 4. Where this is referenced

- **Rules**: `.cursor/rules/agent-collaboration.mdc` — when a requirement or error-fix is requested, follow this sequence.
- **Commands**: `.cursor/commands/new-requirement.md` — points to this doc for full agent collaboration.
- **Docs**: `docs/workflow/CURSOR-SUBAGENTS-DESIGN.md` §3 (workflow) aligns with this sequence; this doc is the detailed collaboration map.
- **Agents**: Each agent's `.cursor/agents/*.mdc` or `docs/cursor-subagents/*.md` can reference this doc and state: "In requirement-driven work, my role is Step N; input from Step N-1; output for Step N+1."

---

## 5. Minimal flow

For small changes or when the user wants a single agent:

1. **Requirements**: Requirement doc + §3 (mandatory before code).
2. **Backend or Frontend or DB**: Implement and test per doc and contract.
3. **QA**: §5 test results and verification.

Security, Contract, DBA, and Architecture are added when the requirement scope demands it (PII, API change, schema design, performance).

---

## 6. References

- Checklist order: `docs/workflow/WORKFLOW_CHECKLIST.md`
- Subagent roles and when to use: `docs/workflow/CURSOR-SUBAGENTS-DESIGN.md`
- Subagent model selection (per-agent model, visibility): `docs/workflow/SUBAGENT-MODEL-SELECTION.md`
- Cursor rules and commands: `docs/workflow/CURSOR-AND-TOOLS-INTEGRATION.md`
- Requirement template: `docs/template/REQUIREMENT_TEMPLATE.md`
