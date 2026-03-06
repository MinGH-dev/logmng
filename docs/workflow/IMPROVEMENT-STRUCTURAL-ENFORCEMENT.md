# Structural enforcement: reducing main-agent bypass

**Purpose**: Implement the structural improvements identified in user analysis: (1) limit main agent's authority to author requirement content; (2) enforce Step 1 delegation and authoring-time collaboration; (3) strengthen the gate before Step 4. This doc ties the analysis to **concrete rule/command changes** and adds a few extra improvement points.

---

## 1. Validation of the analysis

The following judgments are **correct and should be implemented**:

| Judgment | Evidence in current docs |
|----------|---------------------------|
| Main agent has too much authority and can bypass delegation | `agent-collaboration.mdc` says "invoke that subagent" but does **not** say "main must not author §1·§2·§3". So the main agent can still write the requirement doc and skip Requirements + parallel review. |
| Parallel review is lost when main authors the doc | `docs/workflow/REQUIREMENTS-AUTHORING-WORKFLOW.md` assigns **parallel invocation** to the **Requirements** subagent. If Step 1 is done in the main chat, that logic never runs. See `ANALYSIS-requirement-authored-without-requirements-agent.md`. |
| Structural improvement is necessary, not optional | Context bloat + wrong compression + bypass lead to lower quality; rule-based enforcement (delegation + checklist) is the only lever without changing Cursor product. |

---

## 2. Core changes (from user analysis)

### 2.1 Limit main agent's "execution authority" (Strategy ①)

**Goal**: Main agent **does not have the role** of authoring requirement document content (§1·§2·§3). It only **invokes** the Requirements subagent and passes the user request.

**Concrete change** — in `agent-collaboration.mdc`:

- Add after "Delegation gate" a **role boundary**:
  - "**Step 1 (requirement doc)**: The main agent **does not author** the requirement document (§1·§2·§3). It **only** invokes the **Requirements** subagent via the Task tool with the user request (or error message). Authoring is the exclusive responsibility of the Requirements subagent, which performs parallel input and orchestration per `docs/workflow/REQUIREMENTS-AUTHORING-WORKFLOW.md`."
- Keep the existing **Exception**: "code only here" / "skip subagent" / "do it in this chat" → main may perform steps directly (so the user can still force single-chat flow when they want).

**Effect**: Reduces bypass by making "no authoring in main" explicit; exception remains user-driven.

---

### 2.2 "Authoring-time collaboration" (Strategy ②)

**Goal**: When Step 1 is delegated, the **invocation prompt** to Requirements must instruct it to perform **parallel invocation** (interview experts and Backend/Frontend/DB/QA) and orchestrate into §1·§2, then §3.

**Concrete change**:

1. **In `agent-collaboration.mdc`** (or in a "Delegation" subsection):  
   "When invoking the **Requirements** subagent (Step 1), the main agent **must** include in the prompt: (a) the user request or error message, (b) the instruction: **'Author the requirement doc per `docs/workflow/REQUIREMENTS-AUTHORING-WORKFLOW.md`: obtain parallel input from applicable experts, then orchestrate into §1·§2 and finalize §3. Do not write §1·§2 from your own judgment alone.'**"

2. **In `new-requirement.md`**:  
   Replace or supplement the current text with:  
   "**Do not write** the requirement doc in this chat. **Invoke the Requirements subagent** via the Task tool (`subagent_type=\"Requirements\"`). Pass the user request (or paste it) and instruct Requirements to author the doc per `docs/workflow/REQUIREMENTS-AUTHORING-WORKFLOW.md` (parallel input + orchestration). After the requirement doc exists, proceed to Step 2/3/4 as needed."

**Effect**: Ensures that whenever Step 1 is delegated, the "authoring-time collaboration" process is explicitly requested and not left to chance.

---

### 2.3 Gatekeeper: block Step 4 until §3 is confirmed (Strategy ③)

**Goal**: Before the main agent invokes Backend/Frontend/DB (Step 4), it must **confirm** that the requirement doc has **§1, §2, and §3 complete**. No implementation handoff without a complete doc.

**Concrete change**:

1. **In `agent-collaboration.mdc`** under "Key rules" (Gate):  
   "**Gate**: No development (Step 4) before requirement doc + §3 is complete. **Before invoking** any Step 4 subagent (Backend, Frontend, DB), the main agent must **confirm** that the requirement doc contains §1 (user requirement), §2 (design, including §2.1 if security-applicable), and §3 (test case list). If §3 is missing or empty, do not invoke Step 4; first invoke or re-invoke the Requirements subagent to complete §3."

2. **In `error-first-workflow.mdc`**:  
   Add one line: "Before delegating to Backend/Frontend/DB, ensure the requirement doc has §1, §2, and §3; if not, delegate to Requirements to complete it."

**Effect**: Closes the loophole where implementation starts on an incomplete or draft doc.

---

## 3. Language policy (user Strategy ③)

**Current**: `language-policy.mdc` already states that requirement docs are authored in English first and Korean is added after verification.

**Strengthen**: In `language-policy.mdc` under "Requirement documents", add:  
"**Responsibility**: The **Requirements** subagent (or whoever authors the requirement doc) is responsible for (1) authoring §1·§2·§3 in English, (2) after verification is complete, adding the Korean final version (§ Final version or `-ko.md`) per `DOCUMENT-LANGUAGE-POLICY.md`. The main agent does not author requirement content; it only delegates to Requirements."

**Effect**: Aligns language policy with the "main does not author" rule and assigns clear ownership.

---

## 4. Additional improvement points (beyond user analysis)

These complement the three strategies and reduce other bypass/quality risks.

### 4.1 Handoff completeness (wrong compression)

When the main agent **builds the handoff** for Backend/Frontend/DB (scope-specific excerpt), it can **omit** §2.1, contract, or cross-scope TCs. That is a form of "orchestration loss."

**Concrete change**: In `agent-collaboration.mdc` under "Key rules" or in a new bullet:  
"When invoking Step 4 (Backend, Frontend, DB), follow **`docs/workflow/HANDOFF-CHECKLIST.md`** for that scope so the handoff includes §1 summary, relevant §2, **§2.1 if present**, contract/spec ref if applicable, and **all** §3 test cases that apply to that scope. Do not omit cross-cutting constraints (security, contract) when building the prompt."

**Effect**: Reduces wrong compression; ties the rule to the existing checklist.

---

### 4.2 Review receives full doc

If Review (Step 4.5) only sees the same excerpt as the implementer, it cannot detect that the implementer missed something that was in the full doc but not in the excerpt.

**Concrete change**: In `SUBAGENT-DELEGATION.md` (or in the Review row), state:  
"When invoking **Review**, pass the **full requirement doc** (§1–§3 at least), not a scope-specific excerpt. Instruct Review to verify implementation against the **full** doc (including §2.1, contract, all §3 TCs)."  
This is already in `HANDOFF-CHECKLIST.md`; making it explicit in the delegation table reinforces it.

**Effect**: Review can catch omissions due to wrong compression; see `CONTEXT-QUALITY-AND-ORCHESTRATION-MITIGATION.md` §4.9.

---

### 4.3 new-requirement.md as single entry point

**Current**: `new-requirement.md` says "create … or use current requirement" and points to AGENT-COLLABORATION. It does **not** explicitly say "do not write the doc here; invoke Requirements."

**Concrete change**: Already covered in §2.2 — update `new-requirement.md` so that the **first** instruction is: do not write the requirement doc in this chat; invoke Requirements with the user request and the §1.1 instruction.

**Effect**: Anyone (user or agent) running `/new-requirement` sees the enforcement at the entry point.

---

## 5. Implementation checklist

| # | Change | File | Status |
|---|--------|------|--------|
| 1 | Main agent does not author §1·§2·§3; only invokes Requirements | `agent-collaboration.mdc` | Applied |
| 2 | When invoking Requirements, prompt must include §1.1 (parallel + orchestration) | `agent-collaboration.mdc` | Applied |
| 3 | new-requirement: do not write doc here; invoke Requirements with §1.1 | `new-requirement.md` | Applied |
| 4 | Gate: confirm §1·§2·§3 complete before Step 4; else re-invoke Requirements | `agent-collaboration.mdc` | Applied |
| 5 | error-first: before Step 4, ensure doc has §1·§2·§3 | `error-first-workflow.mdc` | Applied |
| 6 | Language: Requirements (author) responsible for EN first, KO after verify | `language-policy.mdc` | Applied |
| 7 | Step 4 handoff: follow HANDOFF-CHECKLIST per scope | `agent-collaboration.mdc` | Applied |
| 8 | Review: pass full requirement doc, verify against full doc | `SUBAGENT-DELEGATION.md` | Applied |

---

## 6. Summary

- **User analysis**: Correct; structural enforcement is necessary. The three strategies (limit main authority, authoring-time collaboration, gatekeeper) are the right levers.
- **Concrete changes**: Above table; primary edits in `agent-collaboration.mdc`, `new-requirement.md`, `error-first-workflow.mdc`, `language-policy.mdc`, and `SUBAGENT-DELEGATION.md`.
- **Extra points**: Handoff checklist in rule (§4.1); Review gets full doc (§4.2); new-requirement entry point (§4.3) — all reinforce the same goal: **orchestration and quality are preserved by rules, not by main-agent discretion**.

---

**References**

- `ANALYSIS-requirement-authored-without-requirements-agent.md`
- `CONTEXT-QUALITY-AND-ORCHESTRATION-MITIGATION.md` §2.5, §4.7–4.9
- `HANDOFF-CHECKLIST.md`
- `docs/workflow/REQUIREMENTS-AUTHORING-WORKFLOW.md`
