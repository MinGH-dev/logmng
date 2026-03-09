# Design: AGENT-COLLABORATION-ON-REQUIREMENT document split and tool placement

**Status**: Design completed; implementation applied (new docs created, index shortened, all referencers updated).  
**Purpose**: Reduce token/context cost and role-specific loading by splitting the long single reference and assigning each part to the right tool (rule, skill, command, or doc).

---

## 1. Referencing tools (audit)

Tools that reference `docs/workflow/AGENT-COLLABORATION-ON-REQUIREMENT.md`:

| Category | File(s) | Reference pattern |
|----------|---------|--------------------|
| **Rules (alwaysApply)** | `.cursor/rules/agent-collaboration.mdc` | Full sequence; §1.1 (invoke Requirements); §1.4 (skills update) |
| **Rules** | `.cursor/rules/build-restart-handoff.mdc` | §1.2 (change file list) |
| **Commands** | `.cursor/commands/new-requirement.md` | §1.1 (authoring), full sequence |
| **Commands** | `.cursor/commands/dry-run-handoff.md` | §1.1 (hybrid consultation) |
| **Skills** | `.cursor/skills/requirement-doc/SKILL.md` | §1.1, §1.2 |
| **Agents** | `.cursor/agents/Requirements.mdc`, `Frontend.mdc`, `Backend` (implied), `DBA.mdc`, `Consistency.mdc`, `Release.mdc`, `Review.mdc`, `RequirementsPastSearch.mdc`, `Architecture.mdc` | Step N; §1.1, §1.2, §1.3 |
| **Cursor-subagents** | `docs/cursor-subagents/requirements.md`, `frontend.md`, `db.md`, `backend.md`, `qa-test.md`, `past-requirements-search.md`, `ux-design.md`, `release.md`, `documentation.md`, `consistency.md`, `architecture.md` | Step N or §1.1, §1.2 |
| **Workflow docs** | `CURSOR-SUBAGENTS-DESIGN.md`, `REQUIREMENTS-CHANGE-TARGET-CHECKLIST.md`, `HANDOFF-CONTEXT-QUALITY-SESSION.md`, `ANALYSIS-*`, `IMPROVEMENT-STRUCTURAL-ENFORCEMENT.md` | §1.1, §1.4, §2, §5.5 |
| **Other** | `HANDOFF-CHECKLIST.md` (content differs: what to include in handoff), `TERMINOLOGY.md`, `DOCUMENT-LANGUAGE-POLICY.md`, `RELEASE_CHECKLIST.md`, CHANGELOG, requirement docs | Sequence or § refs |
| **Treemap / i18n** | `scripts/treemap-i18n.json`, `docs/cursor-tools-treemap.html` | File name / description only |

**Section usage**:

- **§1.1** (Requirements authoring): Most cited — Requirements agent, requirement-doc skill, new-requirement command, dry-run-handoff, SUBAGENT-DELEGATION Step 1, ANALYSIS/IMPROVEMENT docs.
- **§1.2** (Change file list): Implementing agents (Backend/Frontend/DB), build-restart-handoff, requirement-doc skill.
- **§1.3** (Query experts): Backend/Frontend/DB agents, cursor-subagents (frontend, backend, db).
- **§1.4** (Cursor infrastructure): agent-collaboration.mdc, REQUIREMENTS-CHANGE-TARGET-CHECKLIST, Requirements authoring flow.
- **§2** (Handoff rules): Referenced as “handoff wording” in CURSOR-SUBAGENTS-DESIGN; useful for any agent that needs “what do I say when I hand off”.
- **§3–§6**: Sequence table, delegation pointer, “where referenced”, minimal flow, references — used by rules and index-style reads.

---

## 2. Risks of keeping one long document

| Risk | Description |
|------|-------------|
| **Token cost** | Whenever a rule/command/skill says “see AGENT-COLLABORATION-ON-REQUIREMENT.md”, the model may Read the full ~190 lines. Requirements subagent only needs §1.1+§1.2+§1.4; Step 4 agents only need §1.2+§1.3+sequence; loading the whole doc wastes context. |
| **Context dilution** | Long single doc mixes: (1) collaboration table, (2) detailed authoring procedure (§1.1), (3) change file list rule (§1.2), (4) query-experts rule (§1.3), (5) Cursor update (§1.4), (6) handoff wording (§2), (7) delegation/minimal flow/refs. The active role rarely needs all of these at once. |
| **Wrong compression** | When context is truncated, the model may drop the wrong part (e.g. drop §1.1 steps and keep §2), leading to incomplete authoring or handoff. |
| **Maintenance** | One large file is harder to change without touching unrelated sections; ownership (e.g. “Requirements authoring” vs “handoff rules”) is unclear. |
| **Discovery** | New agents or rules need “only handoff rules” or “only Requirements authoring” — a single long doc does not support role-based loading. |

---

## 3. Proposed split and tool placement

### 3.1 Keep one short index document

**File**: `docs/workflow/AGENT-COLLABORATION-ON-REQUIREMENT.md` (shortened)

**Contents** (target ~50–60 lines):

- Title and one-sentence purpose (single reference for who does what, in what order).
- **§1 Collaboration sequence**: **Table only** (Step | Agent(s) | Input | Output | Handoff to). Optional: one short bullet for Gate, Optional steps, Single source of truth, Response language, Cursor infrastructure **pointer**.
- **§2 Handoff rules**: **Keep** the bullet list (Requirements→Security, Security→…, etc.). It is ~15 lines and frequently needed by multiple agents; keeping it in the index avoids a separate read for “what do I say when I hand off”.
- **§3 Delegation**: Short paragraph + pointer to `SUBAGENT-DELEGATION.md` and `agent-collaboration.mdc`.
- **§4 Where this is referenced**: Update to list the new doc structure (see 3.2–3.5).
- **§5 Minimal flow**: Keep (short).
- **§6 References**: Keep.
- **New: “Detailed sections”** paragraph:
  - **§1.1 Requirements authoring (hybrid consultation, orchestration, change target verification)**: `docs/workflow/REQUIREMENTS-AUTHORING-WORKFLOW.md`
  - **§1.2 Change file list (tentative → confirmed)**: `REQUIREMENTS-AUTHORING-WORKFLOW.md` (§1.2 there)
  - **§1.3 Development subagents query experts**: `docs/workflow/DEVELOPMENT-QUERY-EXPERTS.md`
  - **§1.4 Cursor infrastructure update**: `REQUIREMENTS-AUTHORING-WORKFLOW.md` (§1.4 there)

**Referenced by**: Rules (agent-collaboration.mdc), commands (new-requirement, dry-run-handoff for sequence only), agents for “my Step” and “handoff wording” (§2), CURSOR-SUBAGENTS-DESIGN, TERMINOLOGY.

---

### 3.2 New document: Requirements authoring (§1.1, §1.2, §1.4)

**File**: `docs/workflow/REQUIREMENTS-AUTHORING-WORKFLOW.md` (new)

**Contents** (move from current §1.1, §1.2, §1.4):

- **§1.1** Full: Consultation strategy (skills-first, experts-selective), Steps 1–6 (past requests, domain baseline, codebase investigation, selective expert consultation, orchestrate, **5.5 change target verification**, finalize), “When to skip or simplify”.
- **§1.2** Change file list: tentative in §2, confirmed in Step 4; who updates the list.
- **§1.4** Cursor infrastructure update: procedure (Requirements list targets, Contract updates specs, Implementing agent updates skills).

**Referenced by**:

- `.cursor/rules/agent-collaboration.mdc`: Step 1 bullet → “Author per **REQUIREMENTS-AUTHORING-WORKFLOW.md** (parallel input, orchestrate, change target verification, finalize §3).”
- `.cursor/commands/new-requirement.md`: “instruct Requirements to author per **REQUIREMENTS-AUTHORING-WORKFLOW.md**”.
- `.cursor/commands/dry-run-handoff.md`: “Requirements follow **REQUIREMENTS-AUTHORING-WORKFLOW.md** (hybrid consultation).”
- `.cursor/skills/requirement-doc/SKILL.md`: “See **REQUIREMENTS-AUTHORING-WORKFLOW.md** for §1.1 and §1.2.”
- `.cursor/agents/Requirements.mdc`, `docs/cursor-subagents/requirements.md`: “Step 1; authoring details: **REQUIREMENTS-AUTHORING-WORKFLOW.md**.”
- `docs/workflow/SUBAGENT-DELEGATION.md` Step 1 cell: “per **REQUIREMENTS-AUTHORING-WORKFLOW.md**” (and keep REQUIREMENTS-CHANGE-TARGET-CHECKLIST ref).
- `REQUIREMENTS-CHANGE-TARGET-CHECKLIST.md`: “run before finalizing §2 per **REQUIREMENTS-AUTHORING-WORKFLOW.md** §1.1 step 5.5.”

**Benefit**: Requirements subagent and requirement-doc skill only need to Read this file (~90–100 lines) instead of the full 190-line doc.

---

### 3.3 New document: Development query experts (§1.3)

**File**: `docs/workflow/DEVELOPMENT-QUERY-EXPERTS.md` (new)

**Contents** (move from current §1.3):

- When Backend/Frontend/DB need detail the requirement doc does not specify and that falls in another agent’s domain, they **query that expert subagent** (UX, Contract, DBA, Security, Consistency).
- **How to query**: Task tool with requirement doc path and focused question; do not invent answers.

**Referenced by**:

- `.cursor/agents/Frontend.mdc`, `Backend.mdc`, `DB` (and cursor-subagents frontend.md, backend.md, db.md): “When detail is missing, query experts per **DEVELOPMENT-QUERY-EXPERTS.md**.”
- `SUBAGENT-DELEGATION.md` Step 4 cell: “See **DEVELOPMENT-QUERY-EXPERTS.md**” (optional; table is already long).

**Benefit**: Step 4 agents get a single short doc (~15 lines) instead of loading the full collaboration doc.

---

### 3.4 Handoff rules (§2)

**Decision**: **Keep §2 in the shortened AGENT-COLLABORATION-ON-REQUIREMENT.md** (index).

**Rationale**: §2 is a compact bullet list; many agents need it together with the sequence table. Splitting it into a separate HANDOFF-RULES.md would add one more file and one more Read for “what do I say when I hand off”. If the index stays ~50–60 lines including §2, one read of the index still gives sequence + handoff wording. If later the index grows, §2 can be moved to `docs/workflow/HANDOFF-RULES.md` and the index would point to it.

---

### 3.5 Rules and skills (no content duplication)

- **agent-collaboration.mdc**: Do **not** inline the full authoring steps. Keep: delegation gate, Step 1 (do not author; invoke Requirements with “Author per **REQUIREMENTS-AUTHORING-WORKFLOW.md** …”), collaboration sequence (follow **AGENT-COLLABORATION-ON-REQUIREMENT.md** for order), Cursor infrastructure → “per **REQUIREMENTS-AUTHORING-WORKFLOW.md** §1.4” (or “per AGENT-COLLABORATION index” which points there).
- **requirement-doc SKILL**: Short summary in SKILL + “Read **REQUIREMENTS-AUTHORING-WORKFLOW.md** for full procedure (§1.1, §1.2, §1.4).” Do not duplicate the long procedure in the skill.

---

## 4. Reference update matrix (design only)

| Referencer | Current | After split |
|------------|---------|-------------|
| `agent-collaboration.mdc` | AGENT-COLLABORATION §1.1, §1.4 | REQUIREMENTS-AUTHORING-WORKFLOW (Step 1); §1.4 → same doc |
| `new-requirement.md` | AGENT-COLLABORATION §1.1, full sequence | REQUIREMENTS-AUTHORING-WORKFLOW (authoring); AGENT-COLLABORATION (sequence) |
| `dry-run-handoff.md` | AGENT-COLLABORATION §1.1 | REQUIREMENTS-AUTHORING-WORKFLOW |
| `requirement-doc/SKILL.md` | AGENT-COLLABORATION §1.1, §1.2 | REQUIREMENTS-AUTHORING-WORKFLOW |
| `Requirements.mdc`, `requirements.md` | AGENT-COLLABORATION Step 1, §1.1, §1.2 | AGENT-COLLABORATION (Step 1); REQUIREMENTS-AUTHORING-WORKFLOW (detail) |
| `Frontend.mdc`, `frontend.md` (and Backend, db) | AGENT-COLLABORATION §1.2, §1.3 | REQUIREMENTS-AUTHORING-WORKFLOW (§1.2); DEVELOPMENT-QUERY-EXPERTS (§1.3) |
| `SUBAGENT-DELEGATION.md` | AGENT-COLLABORATION §1.1, §1.2, §1.3 | REQUIREMENTS-AUTHORING-WORKFLOW (§1.1, §1.2); DEVELOPMENT-QUERY-EXPERTS (§1.3) |
| `REQUIREMENTS-CHANGE-TARGET-CHECKLIST.md` | AGENT-COLLABORATION §1.4, §5.5 | REQUIREMENTS-AUTHORING-WORKFLOW (step 5.5, §1.4) |
| Other agents (DBA, Consistency, Release, Review, QA, etc.) | AGENT-COLLABORATION Step N | AGENT-COLLABORATION (index: table + §2) — no change to “Step N” ref |
| ANALYSIS / IMPROVEMENT docs | AGENT-COLLABORATION §1.1, §5.5 | REQUIREMENTS-AUTHORING-WORKFLOW |

---

## 5. Implementation order (when implementing)

1. Create `REQUIREMENTS-AUTHORING-WORKFLOW.md` with §1.1, §1.2, §1.4 content (cut from current doc).
2. Create `DEVELOPMENT-QUERY-EXPERTS.md` with §1.3 content.
3. Shorten `AGENT-COLLABORATION-ON-REQUIREMENT.md` to index (table, §2, §3–§6, “Detailed sections” pointers).
4. Update all referencers per §4 matrix (rules, commands, skills, agents, cursor-subagents, workflow docs).
5. Run dry-run handoff verification (`.cursor/commands/dry-run-handoff.md`) and fix any broken references.
6. Update treemap/i18n if they describe the doc (e.g. “single reference” → “index; see REQUIREMENTS-AUTHORING-WORKFLOW, DEVELOPMENT-QUERY-EXPERTS for details”).

---

## 6. Summary

| Document | Role | Approx. lines | Primary consumers |
|----------|------|----------------|-------------------|
| **AGENT-COLLABORATION-ON-REQUIREMENT.md** | Index: sequence table, §2 handoff rules, §3–§6, pointers to detail docs | ~50–60 | Rules, commands, all agents (Step + handoff wording) |
| **REQUIREMENTS-AUTHORING-WORKFLOW.md** (new) | §1.1, §1.2, §1.4 | ~90–100 | Requirements agent, requirement-doc skill, new-requirement, dry-run-handoff, checklist |
| **DEVELOPMENT-QUERY-EXPERTS.md** (new) | §1.3 | ~15 | Backend, Frontend, DB agents and cursor-subagents |

This design keeps a single entry point (AGENT-COLLABORATION-ON-REQUIREMENT.md) for “order of steps and handoff wording”, and moves role-heavy detail into two smaller docs so that Requirements and Step 4 agents load only what they need.
