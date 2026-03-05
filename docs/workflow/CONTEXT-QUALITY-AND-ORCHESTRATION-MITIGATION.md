# Context quality and orchestration: Cursor vs team-agent, and mitigations

**Purpose**: Compare Claude Code's "team agent" (peer main agents) with Cursor's requirement-based orchestration; explain why context growth can degrade quality; and propose mitigations that fit the current workflow.

---

## 1. Comparison: team-agent vs requirement-orchestration

| Aspect | Claude Code team agent | Cursor (this project) |
|--------|------------------------|------------------------|
| **Agent relationship** | Multiple agents as **peer main agents**; each has full agency. | One **main agent** + **subagents** invoked via Task; subagents are delegated, not peers. |
| **Orchestration** | Agents **interact directly** with each other in one workflow; no single "hub" document. | **Requirement doc** is the hub; main agent delegates steps; handoff is via prompt (requirement path + task). |
| **Context per agent** | Each peer gets **focused context** for its turn; handoff is agent-to-agent. | Main agent accumulates **full thread** (req doc + all prior handoffs + subagent returns); subagent gets **prompt only** (no prior subagent context). |
| **Quality claim** | Single workflow with peer interaction → **high completion** in one pass. | Quality can **degrade** as requirement doc + history grow; main agent context bloat; subagent sees only current prompt. |

**Why Cursor does not support team-agent style**: Cursor's Task tool invokes a subagent with a **single prompt**. The subagent does not share a live conversation with other agents; there is no built-in "peer main agents in one room" mode. So orchestration is **document-centric**: the requirement doc + handoff text carry the shared state.

---

## 2. Why context growth hurts quality here

1. **Main agent context**
   - The main chat holds: user messages, requirement doc (full §1–§5), all Task invocations, and all subagent **return messages**. As the thread grows, the model may attend less to the requirement doc and more to recent noise, or drop details from §2/§3.

2. **Subagent context**
   - Each subagent receives **only** the prompt passed in the Task (e.g. requirement doc path + "implement §2 Backend changes; confirm §2 change file list"). It does **not** see other subagents' outputs or the main thread. So:
     - If the prompt is too long (e.g. full requirement doc pasted), the useful part (e.g. §2 Backend, §3 TCs) can be diluted.
     - If the prompt is too short, the subagent may miss constraints from §2.1 (Security), contract, or §3.

3. **Requirement doc as single blob**
   - Passing the **entire** requirement doc to every subagent inflates prompt size and mixes concerns (Security, Backend, Frontend, §5). The part relevant to **this** step (e.g. Backend) is a fraction of the doc.

4. **No "reset" of context**
   - In a team-agent setup, each peer can start with a fresh view of the task. Here, the main agent never "forgets" the long thread, so summarization or focus can degrade over turns.

---

## 2.5 Wrong compression: the main orchestration risk

When we **reduce context by passing scope-specific excerpts** (§4.1), we introduce a **compression step**: someone (main agent or script) decides what to include in each handoff. **Wrong or lossy compression** is the risk that **essential information is omitted**, so the subagent implements against an incomplete picture and quality degrades.

### 2.5.1 Why handoff omission happens

| Cause | Description |
|-------|-------------|
| **Scope boundary blur** | A constraint is **cross-cutting** (e.g. §2.1 Security, contract, error codes) but the handoff is built per "Backend" or "Frontend". The builder includes only "Backend §2" and **drops §2.1** or contract ref, so the subagent never sees "must not expose PII" or "response shape per contract". |
| **Orchestrator not Requirements** | The **main agent** often builds the handoff (it invokes Task). It did not author §1·§2·§3 and may not know which sentence in §2 is critical for Backend (e.g. "decrypt_approver check") or which §3 TCs are mandatory. So it **over-compresses** (too short) or **mis-scopes** (includes wrong section). |
| **Implicit dependencies** | Backend and Frontend depend on each other (API shape, error codes). If Backend handoff omits "Frontend will call this endpoint with body X", Backend may implement a different contract. If the handoff is built from a **single-scope view**, cross-scope constraints are the first to be dropped. |
| **Edge cases and TCs** | §3 may list 10 TCs; only 4 are "Backend". The builder might paste only those 4. If one of the other 6 (e.g. "Integration: approve then decrypt") implies a Backend behavior, that implication is **lost** in the excerpt. |
| **No checklist** | There is no **mandatory list** of "what must be in a Backend handoff" (e.g. §2.1 if present, contract/spec ref if API change, all §3 TCs that touch Backend). So each handoff is ad hoc and omission is likely over time. |

### 2.5.2 Consequence

- Subagent delivers **locally consistent** work (matches the excerpt it saw) but **violates** full requirement (Security, contract, or cross-scope behavior).
- **Review** or **QA** may catch it, but only after implementation; rework and re-handoff. If Review also receives a **compressed** view, it may not have the full §1·§2 to compare against.
- **Requirements agent’s orchestration intent** (what it put in §2 for each area and how §2.1/contract tie in) is **not** guaranteed to reach the implementing agent when handoff is built by another actor with a different mental model.

So: **optimising context size by excerpting can directly cause quality loss if compression is wrong.** The mitigation is not "don’t excerpt" but "make compression **explicit, checkable, and owned**" (§4.7–4.9).

---

## 3. Quality degradation: scenarios and causes

This section summarises **when and how** quality drops, so we can target mitigations.

| Scenario | Cause | Who is affected |
|----------|--------|------------------|
| **Wrong compression** | Handoff excerpt omits §2.1, contract, cross-scope constraint, or relevant §3 TCs. | Implementing subagent (Backend/Frontend/DB); later Review/QA if they only see excerpt. |
| **Orchestration handoff gap** | Main agent (not Requirements) builds handoff and doesn’t know what’s essential; Requirements’ intent in §2 is not fully reflected in the prompt. | All steps that receive a handoff built by main agent. |
| **Context bloat (main)** | Main agent thread too long; model pays less attention to requirement doc and more to recent messages; drops details when invoking next step. | Main agent’s next delegation (wrong or incomplete prompt). |
| **Context bloat (subagent)** | Full requirement doc pasted; subagent’s effective context is diluted; misses key sentence in §2 or §3. | Implementing subagent. |
| **No cross-check** | No one checks that "what Backend was given" matches "what §2 and §2.1 say for Backend". Omission is discovered only at QA or in production. | Entire pipeline. |
| **Step 1 skipped** | Requirement doc authored by main agent; no parallel expert/Backend/Frontend input during authoring; §2 is thin or wrong; later handoff is built on a weak base. | Downstream steps; see `ANALYSIS-requirement-authored-without-requirements-agent.md`. |

**Takeaway**: Quality degradation is not only "context too big". It is also **"context too small or wrong"** when we compress for handoff. Mitigations must address **both** bloat and **wrong compression**.

---

## 4. Mitigations (within current Cursor + requirement workflow)

These keep the existing rule: **requirement doc + §3 before code**; **delegation via Task**; **no change to Cursor product**. They reduce context bloat and sharpen what each agent sees, and **reduce wrong-compression risk** by making handoff content explicit and checkable.

### 4.1 Handoff: pass scope-specific excerpts, not the full doc

- **Main agent** (when invoking Backend/Frontend/DB/Review/QA): instead of "see requirement doc `docs/requirements/yyyyMMdd-name.md`", pass a **short handoff artifact** that contains:
  - **Required**: §1 one-paragraph summary + **§2 section for this scope** (e.g. §2 Backend + §2.1 if Security applied) + **§3 test cases** that apply to this step (e.g. TCs for Backend).
  - **Optional**: link to full doc path for reference; "변경 파일 목록" (tentative or confirmed) for this scope.
- **Rule/command update**: In `SUBAGENT-DELEGATION.md` or a handoff checklist, add: "Prefer passing **scope-specific excerpts** (§1 summary + relevant §2 + relevant §3) in the Task prompt; attach or paste the full doc only when the subagent must see the whole (e.g. Review, QA)."
- **Effect**: Subagent prompt size drops; the model sees only what matters for its step. Less dilution, better adherence to §2/§3. **Risk**: Wrong compression (§2.5); mitigate with §4.7–4.9.

### 4.2 Requirement doc structure: keep §2/§3 scoped and linkable

- **§2**: Keep **per-area subsections** (Frontend / Backend / DB / Security §2.1) so the main agent (or a small script) can extract "Backend only" or "Frontend only" for handoff.
- **§3**: Tag test cases by **scope** (e.g. `Backend`, `Frontend`, `Integration`) so the main agent can list only the TCs relevant to the implementing subagent.
- **Template**: In `REQUIREMENT_TEMPLATE.md`, add a short note: "§2 and §3 should be structured so that scope-specific excerpts can be passed to Backend/Frontend/DB (see CONTEXT-QUALITY-AND-ORCHESTRATION-MITIGATION.md §4.1, §4.7)."
- **Effect**: Easier to build minimal handoff artifacts; less copy-paste of irrelevant sections.

### 4.3 Main agent: optional "phase handoff" summary

- After **Step 1 (Requirements)** or after **Step 4 (implementation)** is done, the main agent can write a **one-time short summary** (e.g. in a `docs/workflow/handoff/yyyyMMdd-name-phase2.md` or in the requirement doc as "§ Handoff summary for Step 5"):
  - 2–3 sentences: what was decided, what was implemented, what to verify.
- Next steps (e.g. QA) receive this summary **plus** §3 and §5, rather than the full thread.
- **Effect**: Reduces reliance on the full conversation history; gives QA (or Review) a clear, fixed context.

### 4.4 Subagent prompt template: strict input format

- Define a **handoff format** that every Task prompt should follow when invoking Backend/Frontend/DB (and optionally Review, QA), for example:
  - `REQ: path/to/req.md`
  - `SCOPE: Backend`
  - `SUMMARY: [1–2 sentences from §1]`
  - `TASK: [Concrete task, e.g. implement §2 Backend; add/run §3 Backend TCs; confirm §2 change file list]`
  - `EXCERPT §2: [pasted Backend part of §2]`
  - `EXCERPT §3: [pasted §3 TCs for Backend]`
- Put this in `SUBAGENT-DELEGATION.md` or in `.cursor/commands/` so the main agent (and humans) use it consistently.
- **Effect**: Subagents get a predictable, minimal context; less ambiguity and smaller prompts.

### 4.5 Limit what the main agent pastes from subagent returns

- When the main agent prepares the next Task call, it should **not** paste the **entire** previous subagent output into the next prompt. It should pass:
  - **Status**: e.g. "Backend implementation done; build/restart OK; §2 change file list updated."
  - **Artifacts**: requirement doc path (or scope-specific excerpt); link to any new file or diff if needed.
- **Effect**: Main agent's own context stays smaller when chaining steps; less accumulation of long subagent logs.

### 4.6 Optional: DelegationManager or script for excerpt extraction

- **DelegationManager** (`.cursor/delegation-mgmt/`) or a small script could:
  - Take `docs/requirements/yyyyMMdd-name.md` + scope (e.g. `Backend`),
  - Output a minimal handoff text: §1 summary + §2 Backend + §2.1 if present + §3 TCs for Backend.
- Main agent (or user) uses this output as the Task prompt.
- **Effect**: Consistent, minimal handoffs; less main-agent token use for building excerpts. **Risk**: Script must implement the **mandatory checklist** (§4.7) so it never drops §2.1, contract ref, or cross-scope TCs.

### 4.7 Mandatory handoff checklist (reduce wrong compression)

Define a **per-scope checklist** of what **must** be included in every handoff when building an excerpt. The agent or script that builds the prompt **must** include each item when applicable; Review (or a later step) can verify that the handoff was complete.

**Example — Backend handoff:**

- [ ] §1: one-paragraph summary (user scenario + expected outcome).
- [ ] §2: full **Backend** subsection (solution, change file list for backend).
- [ ] §2.1: **full §2.1 Security** if the requirement involves PII, decryption, or access control (even if "Backend" is the scope).
- [ ] Contract/spec: **reference or excerpt** of API/DB contract and `specs/*.spec.yaml` if the change touches API or schema.
- [ ] §3: **all** test cases that involve Backend (unit, integration, or integration TCs where Backend is under test); do not drop "Integration" TCs that imply Backend behavior.
- [ ] Cross-scope note (if any): e.g. "Frontend will call POST /api/… with body X; implement to contract."

**Example — Frontend handoff:**

- [ ] §1 summary.
- [ ] §2 full **Frontend** subsection.
- [ ] §2.1 if security/access affects UI.
- [ ] Contract/spec for any API the frontend calls (request/response shape, error codes).
- [ ] §3 all TCs that involve Frontend (including manual/browser if applicable).

- **Rule**: Document this checklist in `SUBAGENT-DELEGATION.md` or a dedicated `docs/workflow/HANDOFF-CHECKLIST.md`. Main agent (and any excerpt script) must follow it. **Effect**: Reduces omission of §2.1, contract, and cross-scope constraints; wrong compression becomes detectable (Review checks "was checklist satisfied?").

### 4.8 Requirements agent produces handoff excerpts (or handoff spec)

To align handoff content with **orchestration intent**, the **Requirements** subagent can produce the **handoff excerpts** (or a short "handoff spec") as part of Step 1 output:

- When the requirement doc is finalised, Requirements adds a section **"Handoff per scope"** (or separate file): for each of Backend, Frontend, DB (and optionally Review, QA), a short list or pointer: "Backend handoff must include: §2 lines X–Y, §2.1 in full, §3 TCs #1,#2,#5, contract ref to `specs/foo.spec.yaml`."
- The **main agent** (or script) then builds the Task prompt by **following this spec** instead of inferring from the full doc. So the **author** of §1·§2·§3 also defines what each step receives; orchestration handoff is less likely to drop critical content.

- **Effect**: Single point of truth for "what must be in each handoff"; reduces wrong compression caused by main agent not knowing what’s essential. Downside: extra work for Requirements; optional for small requirements, recommended for multi-scope or security-relevant ones.

### 4.9 Review checks against full requirement doc

When **Review** (Step 4.5) runs, it should receive **full requirement doc** (§1–§3 at least) and **compare** the implemented change against it, not only against the excerpt the implementing agent received. So:

- If the Backend handoff **omitted** §2.1 and Backend implemented without the security constraint, Review (which has the full doc) can flag "§2.1 requires X; implementation does not reflect it."
- Review’s prompt should explicitly say: "You have the **full** requirement doc; verify that implementation satisfies **all** of §2 and §3 that apply to this scope, not only what may have been in the handoff."

- **Effect**: Catches wrong compression **after** implementation but **before** QA; reduces risk that omission goes to production.

---

## 5. What we do not change

- **Gate**: Requirement doc + §3 before code (Step 1 before Step 4).
- **Delegation**: Steps still performed by dedicated subagents via Task; main agent does not implement when a subagent exists (unless user says "code only here").
- **Single source of truth**: Requirement doc remains the hub; §2 "변경 파일 목록" is confirmed by implementing agent.
- **No Cursor product dependency**: All mitigations are **process and prompt design**; they do not require Cursor to support peer main agents.

---

## 6. Summary

| Problem | Mitigation |
|--------|------------|
| **Wrong compression / handoff omission** | Mandatory handoff checklist (§4.7); Requirements produces handoff excerpts or spec (§4.8); Review checks against full doc (§4.9). |
| Main agent context bloat | Phase handoff summary (§4.3); avoid pasting full subagent output into next step (§4.5). |
| Subagent sees too much irrelevant text | Scope-specific excerpts (§4.1); structured §2/§3 (§4.2); strict handoff format (§4.4). |
| Orchestration intent not in handoff | Requirements-authored handoff spec per scope (§4.8); checklist forces §2.1, contract, cross-scope TCs (§4.7). |
| No shared "peer" context | Accept document-centric handoff; keep handoff artifact small and **complete** (checklist) so each subagent has a clear, minimal but **non-lossy** view. |
| Reproducibility of handoff | Standard prompt template (§4.4); mandatory checklist (§4.7); optional excerpt script that follows checklist (§4.6). |

Implementing **§4.7 (mandatory checklist)** together with §4.1 and §4.4 addresses both context size and wrong compression. §4.8 (Requirements produces handoff spec) best preserves orchestration intent; §4.9 (Review vs full doc) catches omission even when checklist was not followed.

---

**References**

- `docs/workflow/AGENT-COLLABORATION-ON-REQUIREMENT.md` — collaboration sequence and handoff rules
- `docs/workflow/SUBAGENT-DELEGATION.md` — Task tool and step → subagent mapping
- `docs/workflow/ANALYSIS-requirement-authored-without-requirements-agent.md` — importance of Step 1 delegation
