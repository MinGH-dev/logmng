# Agent Collaboration on Requirement

When a **new requirement** or **error-fix request** occurs, agents collaborate in a defined order. This document is the **single reference** for who does what, in what sequence, and what each handoff produces. Use it so that all agents (including Review, Documentation, Release, Consistency, UX) work together **without role duplication**. Role boundaries: `docs/workflow/CURSOR-SUBAGENTS-DESIGN.md` §2.6.

---

## 1. Collaboration sequence

| Step | Agent(s) | Input | Output | Handoff to |
|------|-----------|--------|--------|------------|
| **1** | **Requirements** | User request, error message, or feature need | Requirement doc: `docs/requirements/yyyyMMdd-name.md` with §1, §2, §3. Optional: `specs/*.spec.yaml`. **During authoring**: solicit feedback from relevant expert subagents (see §1.1). | Step 2 if security-relevant; else Step 3 or 4 |
| **2** | **Security** (if PII / decryption / access control) | Requirement doc §1·§2 | §2.1 Security review or security appendix. | Step 3 or 4 |
| **3** | **Contract** (if API or DB change) | Requirement doc + security if any | Updated `docs/contract.md`, `specs/*.spec.yaml`. | Step 4 |
| **3b** | **DBA** (if schema / indexing / JSON design) | Requirement doc, schema or spec | Design review; no code. DB implements. | Step 4 |
| **3c** | **Architecture** (if performance / scale / caching) | Requirement doc, design or spec | Design review; no code. Backend/DB implement. | Step 4 |
| **3d** | **Consistency** (if new conventions / error codes) | Requirement doc or new convention need | Updated `docs/workflow/CONSISTENCY-STANDARDS.md`. Review applies it. | Step 4, Review |
| **3d** | **UX** (if UI / design / a11y) | Requirement doc §1·§2, UI description | § UX review or design recommendations. No code. Frontend implements. | Step 4 |
| **4** | **Backend / Frontend / DB** | Requirement doc, §3, contract/spec, reviews | Code and config; unit/integration tests; **build and restart** (required). When detail is missing, **query the owning expert subagent** (see §1.2). | Step 4.5 or 5 |
| **4.5** | **Review** (optional, before QA) | Implemented change (Step 4) | Review report vs contract, workflow, quality, `CONSISTENCY-STANDARDS.md`. No code. Implementing agent fixes. | Step 5 |
| **5** | **QA** | Requirement doc §3, implemented feature **after build and restart** | **Verification**: verify checklist, health/behavior check; test scenarios; §5 (and §6 for error fixes). | Step 6 or Done |
| **6** | **Documentation** (after QA) | Completed feature, requirement doc | Updated user/ops docs (README, QUICK_START, runbooks). No requirement docs, no code. | Done |
| **6** | **Release** (with or after Documentation) | Completed requirement(s), commit scope | CHANGELOG entry, release checklist update. No user guides, no code. | Done |

- **Gate**: Step 1 (requirement doc + §3) must be done **before** any code (Step 4). Same for error fixes.
- **Optional steps**: Security (PII/decrypt/access); Contract (API/DB change); DBA (schema); Architecture (performance); Consistency (new conventions); UX (UI/design); Review (before QA).
- **Single source of truth**: Requirement doc is the hub. Each agent updates only its designated sections or owned docs (e.g. Consistency → CONSISTENCY-STANDARDS.md; Review applies it but does not edit it).
- **Response language**: Agents respond to the user in the **user's requested language** (e.g. Korean when the user writes in Korean). See `.cursor/rules/language-policy.mdc`.

### 1.1 Requirements authoring: feedback from expert subagents

When the **Requirements** subagent writes the requirement doc, it **solicits feedback from relevant expert subagents** before finalizing the doc:

1. **Draft** §1 (user requirement) and §2 (design) from the user request or error message.
2. **Invoke each relevant expert** (via mcp_task or by instructing the user to switch and pass input):
   - **Security**: if the requirement involves PII, decryption scope, or access control → request §2.1 or security appendix.
   - **Contract**: if API or DB contract/spec change → request contract/spec updates or constraints for §2.
   - **DBA**: if schema, indexing, or data design → request design review or constraints for §2.
   - **Architecture**: if performance, scalability, or load → request design review or constraints for §2.
   - **Consistency**: if new conventions or error codes → request standards or constraints for §2.
   - **UX**: if UI, layout, or a11y → request § UX review or design recommendations for §2.
3. **Incorporate** the experts' feedback into §1·§2.
4. **Finalize** §3 (test plan) and the requirement doc.
5. **When the doc is complete**, the flow continues: Step 2 (Security if needed), Step 3 (Contract/DBA/Architecture/Consistency/UX if needed), then **Step 4** — the **responsible subagent** (Backend, Frontend, or DB) takes over and implements; after implementation, **Step 5** (QA) and so on.

This way the requirement doc reflects expert input **before** implementation starts, and each **responsible subagent** then performs its step in sequence.

### 1.2 Development subagents: query experts when detail is needed

When **Backend**, **Frontend**, or **DB** (Step 4) implement from the requirement doc and need **detailed information** that the doc does not fully specify and that **falls in another agent's domain**, they must **query that expert subagent** instead of assuming:

- **UX** (layout, design, a11y, interaction): e.g. "Requirement doc X §2 — need exact layout/breakpoints for component Y."
- **Contract** (API shape, request/response, env): e.g. "Requirement doc X — need exact request body and response shape for endpoint Z."
- **DBA** (schema, indexes, JSON vs relational): e.g. "Requirement doc X — need final column list and index recommendation for table T."
- **Security** (access rules, PII handling): e.g. "Requirement doc X — need access rule for role R on resource S."
- **Consistency** (naming, error codes): e.g. "Requirement doc X — need error code and message for case C."

**How to query**: Invoke the expert subagent via **mcp_task** with a short description and the requirement doc path (e.g. "Requirement doc: docs/requirements/yyyyMMdd-name.md. Question: [focused question]. Please return [expected output]."). If mcp_task is unavailable, ask the user to have the main agent invoke that subagent with the same question. **Do not invent** answers in another agent's domain; get the answer from the owning agent, then continue implementation.

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
- **Backend / Frontend / DB → QA**: "Implementation done; **build and restart completed**. Please perform **verification**: run verify checklist, health/behavior check, then add/update §5 (and §6 if error fix)."
- **QA → Documentation / Release / Done**: "§5 (and §6 if error fix) updated; verification done. Optionally update user docs and CHANGELOG."
- **Documentation / Release → Done**: "User docs / CHANGELOG updated."

---

## 3. Delegation (main agent → subagent)

When the user is in the **default (main) chat**, the main agent **does not perform** work that belongs to a dedicated subagent. Instead it **instructs the user** to switch to that subagent and pass the right input (requirement doc, context, etc.). This applies to **all steps** (1–6): Requirements, Security, Contract, DBA, Architecture, Consistency, UX, Frontend, Backend, DB, Review, QA, Documentation, Release.

- **Full delegation table** (Step → Subagent → what to pass): `docs/workflow/SUBAGENT-DELEGATION.md`
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
- Cursor rules and commands: `docs/workflow/CURSOR-AND-TOOLS-INTEGRATION.md`
- Requirement template: `docs/template/REQUIREMENT_TEMPLATE.md`
