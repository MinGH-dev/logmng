# Requirements authoring workflow (§1.1, §1.2, §1.4)

Detail for **Step 1** of the collaboration sequence. Index and sequence table: `docs/workflow/AGENT-COLLABORATION-ON-REQUIREMENT.md`.

---

## 1.1 Requirements authoring: hybrid consultation and orchestration

When the **Requirements** subagent writes the requirement doc, it **must not write §1 (user scenario, expected outcome) and §2 (codebase summary, problem analysis, solution) from its own judgment alone**. Instead it gathers domain knowledge and expert input through a **hybrid approach** (skills + tools + selective expert invocation), then **orchestrates** (merges) that input into §1·§2.

### Consultation strategy: skills-first, experts-selective

| Source | Cost | When to use |
|--------|------|-------------|
| **Skills files** (`.cursor/skills/`) | Free (Read tool) | **Always** — baseline domain knowledge |
| **Codebase investigation** (SemanticSearch, Grep, Read) | Low (tool calls) | **Always** — gather actual code structure, current implementation |
| **Expert subagent** (Task, `readonly=true`, `model="fast"`) | Medium (subagent invocation) | **Selective** — only when the requirement touches that expert's domain |

### Steps

1. **Past user requests (when user has not explicitly requested a change)**  
   When the user has **not** explicitly requested a change to prior behavior or scope, **invoke RequirementsPastSearch** (via the Task tool with `subagent_type="RequirementsPastSearch"` when available, or instruct the user to switch and pass the topic/feature). Pass: "Topic: [topic]. What did the user recently request in past requirements? Summarize so we preserve it (max 300 words, bullets)." RequirementsPastSearch uses `docs/requirements/TOPIC-INDEX.md` and reads only §1 of relevant docs for token efficiency. Use the summary when drafting §1·§2 so continuity is maintained. If the user **has** explicitly requested a change (e.g. "이거 바꿔줘", "검색 필드를 A, B로만"), do not override that with past content.

2. **Domain baseline (skills files)**  
   Read the `.cursor/skills/` files relevant to the requirement's domain. These provide curated knowledge (permission model, error codes, DB schema, UI structure, search/decrypt flow, etc.) at zero subagent cost.
   - Identify which skills are relevant from the user request (e.g. auth-related → `auth-permission-domain`, log search → `log-search-domain`).
   - Use the domain knowledge to inform §1 (user scenario context) and §2 (problem analysis, existing behavior).

3. **Codebase investigation (tools)**  
   Use **SemanticSearch**, **Grep**, **Read**, and **Glob** to gather current implementation details for each affected area (backend, frontend, DB). This replaces the previous approach of invoking Backend/Frontend/DB subagents solely for codebase context.
   - Backend: search for relevant services, controllers, entities, and existing tests.
   - Frontend: search for relevant components, API calls, and state management.
   - DB: read schema files, migrations, and init-data when schema is involved.
   - Use findings to build the **codebase summary**, **problem analysis**, and **tentative change file list** for §2.

4. **Selective expert consultation (Task tool)**  
   Invoke expert subagents **only** when the requirement touches their domain. Use `readonly=true` (experts advise only; no file changes) and `model="fast"` (advisory, not implementation — sufficient quality at lower cost). Invoke applicable experts **in parallel** (multiple Task calls in one turn).

   | Expert | Trigger (when to invoke) | What to request |
   |--------|--------------------------|-----------------|
   | **Security** | PII, decryption scope, or access control | §2.1 security review or security constraints for §2 |
   | **Contract** | API or DB contract/spec change | Contract/spec constraints for §2 |
   | **DBA** | Schema, indexing, or data design change | Design review or constraints for §2 |
   | **Architecture** | Performance/scalability concern; **or** Frontend+Backend simultaneous change (commonization review) | Design review for §2; commonization of shared functionality |
   | **Consistency** | New conventions or error codes | Standards or naming constraints for §2 |
   | **UX** | UI, layout, or a11y change | UX review or design recommendations for §2 |

   Pass to each expert: user request summary, relevant §2 draft excerpt, and the specific question. **State clearly**: "This is for requirement authoring only: do not implement; return only structured input for §2." Keep the prompt **focused** — include only the expert's domain context, not the entire draft.

   **Not invoked as experts** (replaced by steps 2–3 above):
   - ~~Backend / Frontend / DB for codebase summary~~ → use skills files + codebase investigation tools.
   - ~~QA for testability~~ → Requirements authors §3 using skills knowledge + codebase investigation; QA verifies during Step 5.

5. **Orchestrate**  
   Merge the collected inputs (skills knowledge, codebase investigation, expert feedback) into:
   - **§1**: user requirement (description), **user scenario**, expected outcome.
   - **§2**: **codebase summary** (per area, from step 3), **problem analysis**, **solution approach** (informed by expert feedback from step 4), and **변경 파일 목록 (예상)** — tentative change file list; see §1.2 below. Structure §2 by scope (`Frontend:`, `Backend:`, `DB:`) for handoff extraction (see `HANDOFF-CHECKLIST.md`).

5.5. **Change target verification (mandatory)**  
   **Before** finalizing §2, run the **change target checklist** so that no affected scope or touchpoint is missed. See `docs/workflow/REQUIREMENTS-CHANGE-TARGET-CHECKLIST.md`.
   - For **each** scope (Backend, Frontend, DB, Contract, Cursor tools): if the requirement affects it, ensure §2 has a subsection and the change file list includes **every** relevant file. For **Frontend**, ensure both **(a) configuration/setup UI** (e.g. permission group edit, settings) and **(b) user-facing screen(s)** are considered when the feature is both configurable and displayed.
   - If the requirement matches a **domain-specific pattern** (e.g. adding a scope-supporting screen, permission group change, API/error change), ensure every touchpoint listed in that pattern in the checklist is covered in §2 and in the change file list.
   - Missing a change target (e.g. Frontend configuration UI) causes incomplete implementation; the implementing agent (Step 4) only changes what is listed in the doc. So the Requirements author **must** complete this verification before handing off.

6. **Finalize**  
   Complete **§3** (test plan, with **Scope tag** per TC — see `REQUIREMENT_TEMPLATE.md`) and the requirement doc. When the doc is complete, the flow continues: Step 2 (Security if needed and not yet consulted in step 4), Step 3 (Contract/DBA/… if needed and not yet consulted), then **Step 4** — the responsible subagent implements; after implementation, **Step 5** (QA) and so on.

   Note: If Security, Contract, DBA, or Architecture was already consulted in step 4 above and their input is reflected in §2, Step 2/3 may still be invoked for **formal review** of the complete doc if the scope warrants it (e.g. Security writes §2.1 formally; Contract updates `specs/*.spec.yaml`). The step-4 consultation provides **early input**; Steps 2/3 provide **formal output**.

### When to skip or simplify

- **Trivial or purely textual** requirement (no codebase/solution): Requirements may skip steps 3–4 and write §1·§2 directly from skills knowledge, then §3.
- **Single-scope** requirement (e.g. backend-only bug fix): Skip experts not relevant; use skills + codebase investigation only. Invoke Security only if access control or PII is involved.
- **Multi-scope** requirement (frontend + backend): **Always** invoke **Architecture** for commonization review (step 4) so shared functionality is reflected in §2.

---

## 1.2 Change file list: tentative in §2, confirmed in Step 4

- The requirement doc §2 **"변경 파일 목록"** is **tentative (예상)** when authored. Requirements (and parallel inputs) may list **expected** files; the **implementing agent (Step 4)** — Backend, Frontend, DB, or module-specific (e.g. Backend-Log) — **must confirm or update** this list when implementation is complete.
- The implementing subagent **updates the requirement doc** §2 "변경 파일 목록" with the **actual** list of files it changed (add/remove/amend vs the tentative list). If **multiple** implementing agents work on the same requirement (e.g. Backend and Frontend), each updates the list with the files **it** changed (add or amend its own section). If the requirement doc has **no** "변경 파일 목록" section (e.g. older docs), the implementing agent **adds** it with the actual files changed. This keeps the requirement doc and implementation scope in sync.

---

## 1.4 Cursor infrastructure update: when a domain model changes

When a requirement **changes the domain model** (e.g. permission model from multi-group to single-group, data schema restructuring, workflow change), the **Cursor infrastructure files** (`.cursor/skills/`, `specs/`, `.cursor/rules/`) may become stale and cause agents to produce incorrect implementations.

**Procedure**:

1. **Requirements (Step 1)**: When authoring §2, identify which `.cursor/skills/{domain}/` files and `specs/*.spec.yaml` files describe the changing domain. List them in §2 under **"Cursor 도구 업데이트 대상"** (Cursor tool update targets).
2. **Contract (Step 3)**: Updates `specs/*.spec.yaml` as usual — this covers spec files.
3. **Implementing agent (Step 4)**: Before or as part of implementation, update the `.cursor/skills/{domain}/` files listed in §2 to reflect the new domain model. This ensures other agents (and future invocations) read correct domain knowledge. Ownership: per `CURSOR-SUBAGENTS-DESIGN.md` §2.6 — each implementing agent owns skills for its domain.
4. **If no implementing agent is invoked** (e.g. the main agent performs "do it in this chat"): the main agent updates both skills and specs directly.

**Why**: Skills and specs serve as domain knowledge for all agents. If they describe an outdated model, agents will generate code based on stale assumptions.
