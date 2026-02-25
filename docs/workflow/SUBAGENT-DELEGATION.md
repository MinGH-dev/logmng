# Subagent delegation (all steps)

When the user requests a **requirement**, **feature**, or **error fix**, work that belongs to a dedicated subagent should be **delegated** by instructing the user to switch to that subagent and pass the right input. The main agent does not perform that step in the main chat unless the user explicitly says "code only here", "skip subagent", or "do it in this chat".

This document covers **all collaboration steps** (1–6). Role boundaries: `docs/workflow/CURSOR-SUBAGENTS-DESIGN.md` §2.6.

---

## 1. Delegation table (Step → Subagent)

| Step | Subagent | When to delegate | Main agent instructs user to |
|------|----------|------------------|-------------------------------|
| **1** | **Requirements** | New requirement or error fix needs a formal requirement doc (§1, §2, §3). | Switch to **Requirements** subagent; provide the user request or error message. Output: requirement doc `docs/requirements/yyyyMMdd-name.md`. Then proceed to Step 2/3/4 as needed. |
| **2** | **Security** | Requirement involves PII, decryption scope, or access control. | Switch to **Security** subagent with the requirement doc (§1·§2). Output: §2.1 Security review. Then Step 3 or 4. |
| **3** | **Contract** | API or DB contract/spec change. | Switch to **Contract** subagent with requirement doc (and security if any). Output: updated `docs/contract.md`, specs. Then Step 4. |
| **3b** | **DBA** | Schema design, indexing, JSON vs relational. | Switch to **DBA** subagent with requirement doc and schema/spec. Output: design review; no code. DB implements. |
| **3c** | **Architecture** | Performance, scalability, caching, load. | Switch to **Architecture** subagent with requirement doc and design. Output: design review; no code. Backend/DB implement. |
| **3d** | **Consistency** | New conventions, error codes, or standards. | Switch to **Consistency** subagent. Output: updated `CONSISTENCY-STANDARDS.md`. Review applies it. |
| **3d** | **UX** | UI, layout, design, or a11y. | Switch to **UX** subagent with requirement doc §1·§2 and UI description. Output: UX review or design recommendations. Then **Frontend** implements. |
| **4** | **Backend / Frontend / DB** | Implementation in `backend/`, `frontend/`, or DB only. | Switch to **Backend**, **Frontend**, or **DB** subagent with requirement doc, §3, contract/spec, and any review output. They implement, build, restart, and hand off to QA. |
| **4.5** | **Review** | Optional: review change before QA. | Switch to **Review** subagent with the implemented change. Output: review report vs contract, workflow, CONSISTENCY-STANDARDS. Implementing agent fixes, then Step 5. |
| **5** | **QA** | After implementation and build/restart. | Switch to **QA** subagent with requirement doc §3 and confirmation that build/restart is done. Output: verification (verify checklist, health/behavior), §5 (and §6 for error fixes). |
| **6** | **Documentation** | User/ops docs (README, QUICK_START, runbooks). | Switch to **Documentation** subagent with completed feature and requirement doc. Output: updated user/ops docs. No requirement docs, no code. |
| **6** | **Release** | CHANGELOG, version, release checklist. | Switch to **Release** subagent with completed requirement(s) and commit scope. Output: CHANGELOG entry, release checklist. No user guides, no code. |

---

## 2. Main agent behavior (default chat)

1. **Identify** which step(s) the user request needs (requirement doc → security? → contract? → implementation → review? → QA → docs/release?).
2. **For each step** that has a dedicated subagent in the table above:
   - **Do not perform** that step in the main chat (do not write requirement doc if delegating to Requirements; do not implement if delegating to Frontend/Backend/DB; do not run verification if delegating to QA; etc.).
   - **Instruct the user** to switch to the matching subagent and pass the described input. Optionally give a short handoff sentence (e.g. "Requirement doc draft ready; please review or update your area.").
3. **Exception**: If the user explicitly says "code only here", "skip subagent", or "do it in this chat", the main agent may perform the relevant step(s) in the current chat.

---

## 3. Step 4 (implementation) in detail

- **frontend/** (UI, layout, components, styles, API calls from frontend) → **Frontend** subagent.
- **backend/** (Java, controllers, services, config; excluding DB-only) → **Backend** subagent.
- **backend/.../db/** only (schema, migrations, setup scripts) → **DB** subagent.

For **UI/layout/design** changes: optionally recommend **UX** subagent first (Step 3d), then **Frontend** (Step 4).

---

## 4. Minimal flow and delegation

Even in the minimal flow (requirement doc → implement → QA):

- **Step 1** can be delegated to **Requirements** (requirement doc + §3).
- **Step 4** must be delegated to **Frontend** / **Backend** / **DB** (no implementation in main chat).
- **Step 5** can be delegated to **QA** (verification, §5/§6).

So the main agent typically: (1) **instructs the user to switch to the Requirements subagent** (or invokes the Requirements subagent) with the user request or error message — the main agent **does not write** the requirement doc in the main chat; (2) after the requirement doc (§1, §2, §3) exists, instructs the user to switch to the implementing subagent (Frontend/Backend/DB) with the doc; (3) after implementation, instructs the user to switch to QA for verification.

---

## 5. References

- Collaboration sequence: `docs/workflow/AGENT-COLLABORATION-ON-REQUIREMENT.md`
- Subagent roles and scope: `docs/workflow/CURSOR-SUBAGENTS-DESIGN.md` §1, §2, §2.6
- Rule that enforces delegation: `.cursor/rules/agent-collaboration.mdc` §5
