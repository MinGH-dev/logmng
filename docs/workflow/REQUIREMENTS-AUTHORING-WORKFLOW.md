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
   When the user has **not** explicitly requested a change to prior behavior or scope, **invoke RequirementsPastSearch** (via the Task tool with `subagent_type="RequirementsPastSearch"` when available, or instruct the user to switch and pass the topic/feature). Pass: "Topic: [topic]. What did the user recently request in past requirements? Summarize so we preserve it (max 300 words, bullets)." RequirementsPastSearch uses `docs/requirements/TOPIC-INDEX.md` and reads only §1 of relevant docs for token efficiency. Use the summary when drafting §1·§2 so continuity is maintained. If the user **has** explicitly requested a change, do not override that with past content.

2. **Domain baseline (skills files)**  
   Read the `.cursor/skills/` files relevant to the requirement's domain. These provide curated knowledge (permission model, error codes, DB schema, UI structure, search/decrypt flow, etc.) at zero subagent cost.
   - Identify which skills are relevant from the user request (e.g. auth-related → `auth-permission-domain`, log search → `log-search-domain`).
   - Use the domain knowledge to inform §1 (user scenario context) and §2 (problem analysis, existing behavior).

3. **Codebase investigation (tools)**  
   Use **SemanticSearch**, **Grep**, **Read**, and **Glob** to gather current implementation details for each affected area (backend, frontend, DB). This replaces the previous approach of invoking Backend/Frontend/DB subagents solely for codebase context.
   - Backend: search for relevant services, controllers, entities, and existing tests.
   - Frontend: search for relevant components, API calls, and state management.
  - For UI defects first reported on a single screen, verify whether the behavior belongs to a **shared UI primitive** (shared component, shared stylesheet, shared layout contract) before scoping the requirement to one consumer screen.
  - For shared table/grid footer issues, explicitly check whether one-page datasets are suppressing the **shared footer region** or footer metadata (total count, rows-per-page). Treat that as a shared footer-contract issue first, not as a multi-page-only pagination issue.
   - DB: read schema files, migrations, and init-data when schema is involved.
   - **When aligning screen A with screen B** (e.g. statistics search with activity log search): list not only layout and spacing but also **form/panel dimensions** (width, and height if relevant) so the solution can include size alignment if the user expects it. See `docs/workflow/REQUIREMENTS-CHANGE-TARGET-CHECKLIST.md` §2.4 (search/filter UI consistency).
   - **When the requirement touches search/filter form or field design**: ensure §1 or §2 explicitly list the design docs that define the standard: at least `docs/design/forms-and-filters.md`, and for **field definitions** both `docs/design/search-fields-by-screen.md` and `docs/design/search-field-definition-items.md`. See `docs/workflow/REQUIREMENTS-CHANGE-TARGET-CHECKLIST.md` §2.4 (Design doc references).
   - **When the requirement touches search/filter CSS** (styling, spacing, layout, or screen-specific override): ensure §2 references `docs/design/css-standard-and-exceptions.md` and `frontend/src/styles/search-filter-standard.css`; Implementation note for Frontend should mention using standard CSS and, for exceptions, component CSS only + comment + Exception index (§5). See REQUIREMENTS-CHANGE-TARGET-CHECKLIST §2.4 row "CSS standard and exceptions".
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
   - **§2**: **codebase summary** (per area, from step 3), **problem analysis**, **solution approach** (informed by expert feedback from step 4), and **planned change file list (expected change targets)**; see §1.2 below. Structure §2 by scope (`Frontend:`, `Backend:`, `DB:`) for handoff extraction (see `HANDOFF-CHECKLIST.md`). For optional or unconfirmed items, mark clearly (e.g. "implement only if product confirms") so implementers do not treat them as mandatory.

5.5. **Change target verification (mandatory)**  
   **Before** finalizing §2, run the **change target checklist** so that no affected scope or touchpoint is missed. See `docs/workflow/REQUIREMENTS-CHANGE-TARGET-CHECKLIST.md`.
   - For **each** scope (Backend, Frontend, DB, Contract, Cursor tools): if the requirement affects it, ensure §2 has a subsection and the change file list includes **every** relevant file. For **Frontend**, ensure both **(a) configuration/setup UI** (e.g. permission group edit, settings) and **(b) user-facing screen(s)** are considered when the feature is both configurable and displayed.
  - For **shared UI primitive defects** (shared table/grid/layout/CSS behavior reported via one screen), list the **shared fix target first** and then list the **consumer-screen verification targets** separately. Do not reduce the requirement to one screen unless shared ownership has been ruled out.
  - For **shared table footer defects**, verify whether the footer region should remain visible on one-page datasets whenever footer metadata exists. If yes, describe total count and rows-per-page as part of the always-visible shared footer contract, and describe one-page navigation buttons as optional or disabled rather than allowing the footer region to disappear.
   - If the requirement matches a **domain-specific pattern** (e.g. adding a scope-supporting screen, permission group change, API/error change), ensure every touchpoint listed in that pattern in the checklist is covered in §2 and in the change file list.
  - When the requirement matches **pattern §2.4 (search/filter UI consistency)**, §2 must include an **Implementation note for Frontend** (full text per REQUIREMENTS-CHANGE-TARGET-CHECKLIST §2.4, including read/apply from design docs and undefined-standard response). **Before finalizing §2**, run the **§2.4 verification** in REQUIREMENTS-CHANGE-TARGET-CHECKLIST.md (table "§2.4 verification (mandatory when pattern applies)"): (1) §1 Expected outcome has an **explicit** bullet that user block fields (department, user name, user ID) have the **same width/size** on all aligned screens; (2) §2 and change file list include applying the same user-block width and ensuring layout does not squeeze the user block (e.g. avoid sharing a single `1fr` cell); (3) §3 includes at least one TC that compares user-block field size across the aligned screens. See checklist §2.4 row "Field width by role (user block)" and `docs/workflow/ANALYSIS-user-field-size-activity-log-vs-statistics.md`. See `docs/workflow/HANDOFF-CHECKLIST.md` Frontend § Design doc implementation and `docs/workflow/ANALYSIS-implementation-phase-design-doc-usage.md`.
   - Missing a change target (e.g. Frontend configuration UI) causes incomplete implementation; the implementing agent (Step 4) only changes what is listed in the doc. So the Requirements author **must** complete this verification before handing off.

6. **Finalize**  
   Complete **§3** (test plan, with **Scope tag** per TC — see `REQUIREMENT_TEMPLATE.md`) and the requirement doc. The checklist line `- [ ] Requirement doc completed` is the canonical machine-readable completion marker for automation, so it must remain unchecked during draft authoring and be changed to `- [x] Requirement doc completed` only when that single requirement document has truly reached its final completed state in the workflow. That completion transition is also the trigger for automatic `docs/requirements/TOPIC-INDEX.md` maintenance, so ordinary draft saves must not be used as an index-update mechanism. When the doc is complete, the flow continues: Step 2 (Security if needed and not yet consulted in step 4), Step 3 (Contract/DBA/… if needed and not yet consulted), then **Step 4** — the responsible subagent implements; after implementation, **Step 5** (QA) and so on.

   Note: If Security, Contract, DBA, or Architecture was already consulted in step 4 above and their input is reflected in §2, Step 2/3 may still be invoked for **formal review** of the complete doc if the scope warrants it (e.g. Security writes §2.1 formally; Contract updates `specs/*.spec.yaml`). The step-4 consultation provides **early input**; Steps 2/3 provide **formal output**.

### When to skip or simplify

- **Trivial or purely textual** requirement (no codebase/solution): Requirements may skip steps 3–4 and write §1·§2 directly from skills knowledge, then §3.
- **Single-scope** requirement (e.g. backend-only bug fix): Skip experts not relevant; use skills + codebase investigation only. Invoke Security only if access control or PII is involved.
- **Multi-scope** requirement (frontend + backend): **Always** invoke **Architecture** for commonization review (step 4) so shared functionality is reflected in §2.

---

## 1.2 Change file list: planned in §2, confirmed or amended in Step 4

- The requirement doc §2 lists **planned change file list (expected change targets)** when authored. Requirements (and parallel inputs) list **expected** files; do not use "Actual files changed" as the **section name** at authoring time. The **implementing agent (Step 4)** — Backend, Frontend, DB, or module-specific (e.g. Backend-Log) — **confirms or amends** this list when implementation is complete.
- The implementing subagent **updates the requirement doc** §2 change file list with the files it actually changed (add/remove/amend vs the planned list). If **multiple** implementing agents work on the same requirement (e.g. Backend and Frontend), each updates the list with the files **it** changed (add or amend its own section). If the requirement doc has **no** change file list section (e.g. older docs), the implementing agent **adds** it. This keeps the requirement doc and implementation scope in sync.
- Use **requirement tone** in §2 and in the change file list when authoring: **must**, **verify**, **align**, **confirm**. Avoid implementation-complete phrasing ("No change", "already present", "confirmed by implementing agent") in the initial draft; reserve those for §5 or the implementing agent's update. For **optional or unconfirmed** items, mark clearly (e.g. "implement only if product confirms") so implementers do not treat them as mandatory.

---

## 1.3 When the requirement is an error/bug fix

When the requirement is an **error fix or bug fix** (user input is mainly an error message, stack trace, or "fix this error"):

- **Do not fix based on hypothesis.** The solution approach in §2 must **not** instruct implementers to change logic immediately based on a suspected cause.
- **Diagnostic phase (mandatory):** §2 must require:
  1. Add **diagnostic (debug) logs** in the suspected areas (e.g. key variables, branch outcomes, per-item results) so the root cause can be verified from logs.
  2. Reproduce the error once and **capture logs**.
  3. **Analyze logs** to confirm the actual root cause (not assumption).
  4. Only **after** the cause is confirmed from logs, proceed to the logic/code fix.
- **Diagnostic logs must not run in production:** §2 (or a short implementation note) must state that diagnostic logs must be either: **(a)** at DEBUG level (or equivalent) so they are off in production, or **(b)** behind a feature flag / dev-only path, or **(c)** removed or downgraded after the fix is verified. This ensures the "add logs to verify" step does not leave production logging sensitive or verbose data.

Apply this subsection whenever the requirement is classified as an error/bug fix (e.g. from the error-first workflow or user-reported failure). The implementing agent (Step 4) must follow the diagnostic phase before applying the fix.

---

## 1.4 Cursor infrastructure update: when a domain model changes

When a requirement **changes the domain model** (e.g. permission model from multi-group to single-group, data schema restructuring, workflow change), the **Cursor infrastructure files** (`.cursor/skills/`, `specs/`, `.cursor/rules/`) may become stale and cause agents to produce incorrect implementations.

**Procedure**:

1. **Requirements (Step 1)**: When authoring §2, identify which `.cursor/skills/{domain}/` files and `specs/*.spec.yaml` files describe the changing domain. List them in §2 under **"Cursor tool update targets"**.
2. **Contract (Step 3)**: Updates `specs/*.spec.yaml` as usual — this covers spec files.
3. **Implementing agent (Step 4)**: Before or as part of implementation, update the `.cursor/skills/{domain}/` files listed in §2 to reflect the new domain model. This ensures other agents (and future invocations) read correct domain knowledge. Ownership: per `CURSOR-SUBAGENTS-DESIGN.md` §2.6 — each implementing agent owns skills for its domain.
4. **If no implementing agent is invoked** (e.g. the main agent performs "do it in this chat"): the main agent updates both skills and specs directly.

**Why**: Skills and specs serve as domain knowledge for all agents. If they describe an outdated model, agents will generate code based on stale assumptions.
