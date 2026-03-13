# Analysis: Implementation phase not applying design doc (search-field-definition-items)

**Context**: For the statistics screen design standards requirement (`20260311-statistics-screen-design-standards.md`), the **design phase** referenced both `docs/design/search-fields-by-screen.md` and `docs/design/search-field-definition-items.md`, but the **implementation phase** (Frontend) did not use the field-level schema from definition-items when changing CSS. Spacing and container width were aligned from requirement §2 excerpts and forms-and-filters.md only.

**Goal**: Identify root causes and improve workflow so implementation applies the same design intent (field-level and layout values from design docs) as the design phase.

---

## 1. Observed gap

| Phase | Design docs referenced | How they were used |
|-------|------------------------|---------------------|
| **Requirements / §2** | search-fields-by-screen, search-field-definition-items (per checklist §2.4) | §2 and change file list named the docs; solution said "align spacing 8–12px", "max-width 1400px", "review search-fields-by-screen §3". |
| **Handoff (main → Frontend)** | Design doc **paths** listed under "Design doc references (checklist §2.4)" | No explicit instruction to **read** those docs and **apply** their numeric values when implementing. |
| **Frontend implementation** | — | Changed CSS using requirement §2 excerpts (8–12px gap, 1400px) and alignment with activity log. Did **not** read definition-items §1/§4 or search-fields-by-screen §3 to apply field-level width/height/padding. |

Result: Design and implementation are **documentally** aligned (both cite the same docs) but **operationally** divergent: implementer did not use definition-items as the source of truth for field-level values.

---

## 2. Root cause analysis

### 2.1 Handoff does not require an "implementation instruction"

- **HANDOFF-CHECKLIST.md** Frontend section requires:
  - §1 summary, §2 Frontend subsection, §3 TCs, contract, cross-scope, **Search/filter (user-context screens)** → reference `docs/analysis-search-consistency-by-screen.md` for unified axes and scope=self.
- It does **not** require: "When search/filter UI is in scope, tell the implementer to **read** search-field-definition-items.md and search-fields-by-screen.md and **apply** width, height, padding, gap from those docs when changing CSS or components."
- So the handoff builder (main agent) lists design docs as **references** but does not add an actionable **instruction** that the implementer must use those docs as the **source of numeric values**.

**Cause**: Checklist and workflow focus on "reference the doc" (traceability) and not "instruct to apply from the doc" (implementation behavior).

### 2.2 Requirement §2 gives concrete numbers without mandating sourcing

- §2 said: "row/field gap 8–12px, block 12–16px, container padding 12–16px", "max-width 1400px", "align spacing with activity log".
- The implementer had **actionable numbers** directly in the handoff. There was no requirement that "these numbers must be verified or taken from search-field-definition-items.md and search-fields-by-screen.md".
- So the implementer reasonably optimized for the explicit task ("align spacing", "confirm 1400px") and did not open the design docs for field-level schema (height 34px, padding 6px 8–10px, etc.).

**Cause**: §2 duplicates numeric values as excerpts without tying them to "source: definition-items / search-fields-by-screen; implementer must apply from there."

### 2.3 Frontend rule is not guaranteed in Task context

- `.cursor/rules/search-filter-form-design.mdc` says: "When implementing or modifying search/filter components, apply the field definitions (size, type, constraints, data source) from search-fields-by-screen.md and search-field-definition-items.md."
- The rule has **globs** (e.g. `**/StatisticsFilters*`); when the main agent invokes Frontend via **Task**, the subagent may not receive the same rule context. Even when the Frontend **agent** doc (frontend.md) says "follow search-fields-by-screen and search-field-definition-items", the handoff prompt did not repeat the **operational** step ("read and apply from those docs"), so the implementer followed the handoff text (change file list + §2 excerpts) first.

**Cause**: Relying on a rule or agent doc alone is insufficient when the handoff itself does not state "read and apply from design docs"; the handoff wins as the immediate task specification.

### 2.4 No "implementation note" in requirement §2 for §2.4 pattern

- **REQUIREMENTS-CHANGE-TARGET-CHECKLIST.md** §2.4 requires that §1 or §2 **reference** both design docs (Design doc references row).
- It does **not** require that §2 include an **implementation note for Frontend** such as: "Implementer must read and apply field-level and layout values from search-field-definition-items.md and search-fields-by-screen.md when changing form/filter CSS or components."
- So the Requirements author adds the doc references but does not add text that the handoff builder can pass as an **instruction** to the implementer.

**Cause**: Checklist covers "reference" but not "instruction to apply"; the handoff builder has nothing to excerpt for "how to use the design docs during implementation."

---

## 3. Summary of causes

| # | Cause | Where |
|---|--------|--------|
| 1 | Handoff does not require an explicit "read and apply from design docs" instruction for search/filter | HANDOFF-CHECKLIST.md Frontend |
| 2 | §2 gives numeric excerpts without "source: design docs; implementer must verify/apply from there" | Requirement doc §2; no template/workflow requirement for implementation note |
| 3 | Implementer follows handoff text first; rule/agent doc may not be enough in Task context | Handoff content + rule scope |
| 4 | §2.4 pattern requires "reference" but not "implementation note" for Frontend | REQUIREMENTS-CHANGE-TARGET-CHECKLIST.md §2.4 |

---

## 4. Improvements (applied)

### 4.1 HANDOFF-CHECKLIST.md — Frontend: design doc implementation instruction

**Add** to Frontend handoff section:

- **Design doc implementation (search/filter)**: When the requirement involves search/filter UI (REQUIREMENTS-CHANGE-TARGET-CHECKLIST §2.4 or change file list touches StatisticsFilters, UserActivityLogSearchForm, etc.), include an **implementation instruction** in the handoff: "Before changing form/filter CSS or component layout, read `docs/design/search-field-definition-items.md` (§1 definition items, §4 cross-field rules) and `docs/design/search-fields-by-screen.md` (per-screen tables for the affected screen). Apply width, height, padding, and gap values from those docs; verify requirement §2 numeric excerpts against the docs."

**Rationale**: The handoff becomes the single place that forces "read and apply" behavior; the implementer receives it in the same prompt as §2 and change file list.

### 4.2 REQUIREMENTS-CHANGE-TARGET-CHECKLIST.md §2.4 — implementation note for Frontend

**Add** a row to the §2.4 table:

- **Implementation note for Frontend**: When §2.4 applies, §2 must include a short **implementation note** that the handoff builder will pass to Frontend: "Implementer must read and apply field-level and layout values from search-field-definition-items.md and search-fields-by-screen.md when changing form/filter CSS or components; requirement §2 numeric values (e.g. 8–12px) are consistent with those docs but must be verified or sourced from the docs."

**Rationale**: Requirements author is reminded to add text that the main agent can excerpt into the Frontend handoff.

### 4.3 REQUIREMENTS-AUTHORING-WORKFLOW.md — implementation note when §2.4 applies

**Add** (e.g. in step 5.5 or after change target verification): When the requirement matches pattern §2.4 (search/filter UI consistency), §2 must include an **Implementation note for Frontend** (one sentence or bullet) so the handoff includes the "read and apply from design docs" instruction. See HANDOFF-CHECKLIST.md Frontend § Design doc implementation.

**Rationale**: Workflow and checklist stay in sync; author runs both "change target verification" and "implementation note for §2.4".

### 4.4 docs/cursor-subagents/frontend.md — strengthen search/filter bullet

**Replace** the "Search/filter form UI" bullet with:

- **Search/filter form UI**: When implementing or changing search/filter forms (검색하기, 활동 이력, 통계 등), **before** changing layout or styling read **docs/design/search-field-definition-items.md** (§1 definition items, §4 cross-field rules) and **docs/design/search-fields-by-screen.md** (per-screen tables for the affected screen) and **apply** width, height, padding, and gap from those docs. If the handoff includes numeric excerpts (e.g. 8–12px), treat them as consistent with the docs and **verify or source from the docs** when in doubt. For same-name fields across screens, do not unify or change definition without **user direction** — see search-fields-by-screen.md § "동일 이름·다른 성격 필드 — 피드백 요청". Rule: `.cursor/rules/search-filter-form-design.mdc`.

**Rationale**: Frontend agent always sees "read first, then apply"; handoff can reinforce the same.

### 4.5 Requirement template — implementation note when §2.4 applies

**Add** in §2 (e.g. near "Affected scopes and change targets" or in the note about §2.4): When the requirement matches **pattern §2.4** (search/filter UI consistency), §2 must include an **Implementation note for Frontend**: "Implementer must read and apply field/layout values from search-field-definition-items.md and search-fields-by-screen.md when changing form/filter CSS or components." The handoff builder will include this in the Frontend handoff.

**Rationale**: Template drives consistent authoring; every new search/filter alignment requirement gets the note.

---

## 5. Verification: handoff dry-run

A dry-run simulated a **search/filter** virtual requirement ("Align statistics filter spacing and field sizes with design standards") and verified:

1. **Requirements output**: §2 references both design docs and includes an **Implementation note for Frontend** (prompt instructs author to add it when §2.4 applies).
2. **Frontend handoff**: The prompt built by the main agent includes the **design doc implementation instruction** (read and apply from definition-items and search-fields-by-screen).
3. **Checklist**: HANDOFF-CHECKLIST.md Frontend items, including the new design doc implementation item, are satisfied.

**Result**: Pass. See **docs/workflow/DRYRUN-implementation-phase-design-doc-handoff.md**.

---

## 6. References

- `docs/requirements/20260311-statistics-screen-design-standards.md`
- `docs/workflow/ANALYSIS-search-field-design-doc-reference-gaps.md` (authoring-phase gaps)
- `docs/workflow/HANDOFF-CHECKLIST.md`
- `docs/workflow/REQUIREMENTS-CHANGE-TARGET-CHECKLIST.md` §2.4
- `docs/workflow/REQUIREMENTS-AUTHORING-WORKFLOW.md`
- `docs/cursor-subagents/frontend.md`
- `docs/template/REQUIREMENT_TEMPLATE.md`
- `.cursor/rules/search-filter-form-design.mdc`
