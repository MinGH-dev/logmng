# Agent Collaboration on Requirement

When a **new requirement** or **error-fix request** occurs, agents collaborate in a defined order. This document is the **single reference** for who does what, in what sequence, and what each handoff produces. Use it so that all agents (including Review, Documentation, Release, Consistency, UX) work together **without role duplication**. Role boundaries: `docs/workflow/CURSOR-SUBAGENTS-DESIGN.md` §2.6.

**Detailed sections** (authoring steps, change file list, query experts, Cursor infrastructure): see **§ Detailed sections** at the end of this document.

---

## 1. Collaboration sequence

| Step | Agent(s) | Input | Output | Handoff to |
|------|-----------|--------|--------|------------|
| **1** | **Requirements** | User request, error message, or feature need | Requirement doc with §1, §2, §3. **During authoring**: obtain **parallel** input from experts and Backend/Frontend/DB/QA (scenario, codebase, problem, solution); **orchestrate** into §1·§2; the §2 planned change file list is **tentative** (see REQUIREMENTS-AUTHORING-WORKFLOW.md §1.1, §1.2). | Step 2 if security-relevant; else Step 3 or 4 |
| **2** | **Security** (if PII / decryption / access control) | Requirement doc §1·§2 | §2.1 Security review or security appendix. | Step 3 or 4 |
| **3** | **Contract** (if API or DB change) | Requirement doc + security if any | Updated `docs/contract.md`, `specs/*.spec.yaml`. | Step 4 |
| **3b** | **DBA** (if schema / indexing / JSON design) | Requirement doc, schema or spec | Design review; no code. DB implements. | Step 4 |
| **3c** | **Architecture** (if performance / scale / caching **or when requirement involves frontend/backend** for commonization) | Requirement doc, design or spec | Design review (performance and **commonization**: frontend/backend shared or common functionality); no code. Backend/Frontend/DB implement. | Step 4 |
| **3d** | **Consistency** (if new conventions / error codes) | Requirement doc or new convention need | Updated `docs/workflow/CONSISTENCY-STANDARDS.md`. Review applies it. | Step 4, Review |
| **3d** | **UX** (if UI / design / a11y) | Requirement doc §1·§2, UI description | § UX review or design recommendations. No code. Frontend implements. Role boundaries and responsibility matrix: **docs/workflow/UX-ROLE-SEPARATION-DESIGN.md**. | Step 4 |
| **4** | **Backend / Frontend / DB** | Requirement doc, §3, contract/spec, reviews | Code and config; **implement unit/integration tests** that cover §3 test cases; **build and restart** (required). **Confirm or update** requirement doc §2 planned change file list with actual files changed. When detail is missing, **query the owning expert subagent** (see DEVELOPMENT-QUERY-EXPERTS.md). | Step 4.5 or 5 |
| **4.5** | **Review** (optional, before QA) | Implemented change (Step 4) | Review report vs contract, workflow, quality, `CONSISTENCY-STANDARDS.md`. No code. Implementing agent fixes. | Step 5 |
| **5** | **QA** | Requirement doc §3, implemented feature **after build and restart** | **Verification**: verify checklist, health/behavior check; test scenarios; §5 (and §6 for error fixes). | Step 6 or Done |
| **6** | **Documentation** (after QA) | Completed feature, requirement doc | Updated user/ops docs (README, QUICK_START, runbooks). No requirement docs, no code. | Done |
| **6** | **Release** (with or after Documentation) | Completed requirement(s), commit scope | CHANGELOG entry, release checklist update. No user guides, no code. | Done |

- **Gate**: Step 1 (requirement doc + §3) must be done **before** any code (Step 4). Same for error fixes.
- **Optional steps**: Security (PII/decrypt/access); Contract (API/DB change); DBA (schema); Architecture (performance **and commonization when frontend/backend**); Consistency (new conventions); UX (UI/design); Review (before QA).
- **Single source of truth**: Requirement doc is the hub. Each agent updates only its designated sections or owned docs.
- **Response language**: Agents respond in the **user's requested language**. See `.cursor/rules/language-policy.mdc`.
- **Cursor infrastructure update**: When a requirement changes the domain model, update `.cursor/skills/` and specs per REQUIREMENTS-AUTHORING-WORKFLOW.md §1.4.

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
- **Backend / Frontend / DB → QA**: "Implementation done; **build and restart completed**; requirement doc §2 planned change file list confirmed/updated with actual files. Please perform **verification**: run verify checklist, health/behavior check, then add/update §5 (and §6 if error fix)."
- **QA → Requirements** (on verification failure): "Verification failed; bugfix child created at [path]; **failure scope**: [frontend | backend | db | security | contract | ux | …]. Please formalize the bugfix doc (§1·§2·§3) and **delegate to the responsible expert** by scope. Do not implement."
- **Requirements → Backend | Frontend | DB | Security | Contract | UX** (by failure scope): "Bugfix child [path]; please fix per doc. When **issue closed** (fix + build/restart done), hand off to **QA** for re-verification. QA will re-run verification; when all pass, QA commits."
- **Backend / Frontend / DB (after fixing bugfix) → QA**: "Issue closed; build/restart done. Please **re-run verification** (verify checklist, and browser automation if frontend). Update §5; if all pass, commit per commit-on-complete."
- **QA → Documentation / Release / Done**: "§5 (and §6 if error fix) updated; verification done. Optionally update user docs and CHANGELOG."
- **Documentation / Release → Done**: "User docs / CHANGELOG updated."

---

## 3. Delegation (main agent → subagent)

When the user is in the **default (main) chat**, the main agent **does not perform** work that belongs to a dedicated subagent. Instead it **instructs the user** to switch to that subagent and pass the right input (requirement doc, context, etc.). This applies to **all steps** (1–6).

- **Full delegation table** (Step → Subagent → what to pass): `docs/workflow/SUBAGENT-DELEGATION.md`
- **Model per subagent**: `docs/workflow/SUBAGENT-MODEL-SELECTION.md`
- **Rule**: `.cursor/rules/agent-collaboration.mdc`

Exception: if the user says "code only here", "skip subagent", or "do it in this chat", the main agent may perform the relevant step(s) in the current chat.

---

## 4. Where this is referenced

- **Rules**: `.cursor/rules/agent-collaboration.mdc` — when a requirement or error-fix is requested, follow this sequence. Step 1 authoring detail: `docs/workflow/REQUIREMENTS-AUTHORING-WORKFLOW.md`.
- **Commands**: `.cursor/commands/new-requirement.md` — points here for sequence; Requirements authoring per REQUIREMENTS-AUTHORING-WORKFLOW.md.
- **Docs**: `docs/workflow/CURSOR-SUBAGENTS-DESIGN.md` §3 aligns with this sequence. Detail: REQUIREMENTS-AUTHORING-WORKFLOW.md, DEVELOPMENT-QUERY-EXPERTS.md.
- **Agents**: Each agent's `.cursor/agents/*.mdc` or `docs/cursor-subagents/*.md` — "In requirement-driven work, my role is Step N; input from Step N-1; output for Step N+1."

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
- Subagent model selection: `docs/workflow/SUBAGENT-MODEL-SELECTION.md`
- Cursor rules and commands: `docs/workflow/CURSOR-AND-TOOLS-INTEGRATION.md`
- Requirement template: `docs/template/REQUIREMENT_TEMPLATE.md`

---

## Detailed sections (role-specific; load only when needed)

| Section | Document | Primary consumers |
|---------|----------|-------------------|
| **§1.1** Requirements authoring (hybrid consultation, orchestration, change target verification) | `docs/workflow/REQUIREMENTS-AUTHORING-WORKFLOW.md` | Requirements agent, requirement-doc skill, new-requirement command, dry-run-handoff |
| **§1.2** Change file list (tentative → confirmed) | `docs/workflow/REQUIREMENTS-AUTHORING-WORKFLOW.md` §1.2 | Backend, Frontend, DB, build-restart-handoff |
| **§1.3** Development subagents query experts | `docs/workflow/DEVELOPMENT-QUERY-EXPERTS.md` | Backend, Frontend, DB agents |
| **§1.4** Cursor infrastructure update | `docs/workflow/REQUIREMENTS-AUTHORING-WORKFLOW.md` §1.4 | Requirements (Step 1), implementing agents (Step 4), agent-collaboration.mdc |
