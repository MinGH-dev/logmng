# Agent Collaboration on Requirement

When a **new requirement** or **error-fix request** occurs, agents collaborate in a defined order. This document is the **single reference** for who does what, in what sequence, and what each handoff produces. Use it so that all agents (including Review, Documentation, Release, Consistency, UX) work together **without role duplication**. Role boundaries: `docs/workflow/CURSOR-SUBAGENTS-DESIGN.md` §2.6.

---

## 1. Collaboration sequence

| Step | Agent(s) | Input | Output | Handoff to |
|------|-----------|--------|--------|------------|
| **1** | **Requirements** | User request, error message, or feature need | Requirement doc: `docs/requirements/yyyyMMdd-name.md` with §1, §2, §3. Optional: `specs/*.spec.yaml`. | Step 2 if security-relevant; else Step 3 or 4 |
| **2** | **Security** (if PII / decryption / access control) | Requirement doc §1·§2 | §2.1 Security review or security appendix. | Step 3 or 4 |
| **3** | **Contract** (if API or DB change) | Requirement doc + security if any | Updated `docs/contract.md`, `specs/*.spec.yaml`. | Step 4 |
| **3b** | **DBA** (if schema / indexing / JSON design) | Requirement doc, schema or spec | Design review; no code. DB implements. | Step 4 |
| **3c** | **Architecture** (if performance / scale / caching) | Requirement doc, design or spec | Design review; no code. Backend/DB implement. | Step 4 |
| **3d** | **Consistency** (if new conventions / error codes) | Requirement doc or new convention need | Updated `docs/workflow/CONSISTENCY-STANDARDS.md`. Review applies it. | Step 4, Review |
| **3d** | **UX** (if UI / design / a11y) | Requirement doc §1·§2, UI description | § UX review or design recommendations. No code. Frontend implements. | Step 4 |
| **4** | **Backend / Frontend / DB** | Requirement doc, §3, contract/spec, reviews | Code and config; unit/integration tests; **build and restart** (required). | Step 4.5 or 5 |
| **4.5** | **Review** (optional, before QA) | Implemented change (Step 4) | Review report vs contract, workflow, quality, `CONSISTENCY-STANDARDS.md`. No code. Implementing agent fixes. | Step 5 |
| **5** | **QA** | Requirement doc §3, implemented feature **after build and restart** | **Verification**: verify checklist, health/behavior check; test scenarios; §5 (and §6 for error fixes). | Step 6 or Done |
| **6** | **Documentation** (after QA) | Completed feature, requirement doc | Updated user/ops docs (README, QUICK_START, runbooks). No requirement docs, no code. | Done |
| **6** | **Release** (with or after Documentation) | Completed requirement(s), commit scope | CHANGELOG entry, release checklist update. No user guides, no code. | Done |

- **Gate**: Step 1 (requirement doc + §3) must be done **before** any code (Step 4). Same for error fixes.
- **Optional steps**: Security (PII/decrypt/access); Contract (API/DB change); DBA (schema); Architecture (performance); Consistency (new conventions); UX (UI/design); Review (before QA).
- **Single source of truth**: Requirement doc is the hub. Each agent updates only its designated sections or owned docs (e.g. Consistency → CONSISTENCY-STANDARDS.md; Review applies it but does not edit it).
- **Response language**: Agents respond to the user in the **user's requested language** (e.g. Korean when the user writes in Korean). See `.cursor/rules/language-policy.mdc`.

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

## 3. Where this is referenced

- **Rules**: `.cursor/rules/agent-collaboration.mdc` — when a requirement or error-fix is requested, follow this sequence.
- **Commands**: `.cursor/commands/new-requirement.md` — points to this doc for full agent collaboration.
- **Docs**: `docs/workflow/CURSOR-SUBAGENTS-DESIGN.md` §3 (workflow) aligns with this sequence; this doc is the detailed collaboration map.
- **Agents**: Each agent's `.cursor/agents/*.mdc` or `docs/cursor-subagents/*.md` can reference this doc and state: "In requirement-driven work, my role is Step N; input from Step N-1; output for Step N+1."

---

## 4. Minimal flow

For small changes or when the user wants a single agent:

1. **Requirements**: Requirement doc + §3 (mandatory before code).
2. **Backend or Frontend or DB**: Implement and test per doc and contract.
3. **QA**: §5 test results and verification.

Security, Contract, DBA, and Architecture are added when the requirement scope demands it (PII, API change, schema design, performance).

---

## 5. References

- Checklist order: `docs/workflow/WORKFLOW_CHECKLIST.md`
- Subagent roles and when to use: `docs/workflow/CURSOR-SUBAGENTS-DESIGN.md`
- Cursor rules and commands: `docs/workflow/CURSOR-AND-TOOLS-INTEGRATION.md`
- Requirement template: `docs/template/REQUIREMENT_TEMPLATE.md`
