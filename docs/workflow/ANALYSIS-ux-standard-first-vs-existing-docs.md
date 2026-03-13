# Analysis: UX/Frontend standard-first vs existing docs (중첩·범위 검토)

**Purpose**: Check whether the new standard-first tools (`ux-frontend-standard-principles.md`, `ux-frontend-standard-first.mdc`, and related updates) **overlap** with existing docs or are **overshadowed** by broader-scope documents when doing "one screen standardization" work.

**Date**: 2026-03-11

---

## 1. Summary

| Risk | Finding | Recommendation |
|------|---------|----------------|
| **중첩 (Overlap)** | Same rule (e.g. "do not re-define control size", "width by role") appears in 4–5 places. Intentional reinforcement; no conflict. | Optionally add "authoritative source" cross-references to reduce ambiguity. |
| **범위 가림 (Overshadowing)** | Requirement doc, handoff checklist, and Frontend subagent prompt do **not** mention "if standard undefined → inform user first". So implementers may follow the existing flow and skip the standard-first check. | Add one line to HANDOFF-CHECKLIST (Frontend), and to docs/design/README; optionally to Frontend subagent prompt and requirement template. |

---

## 2. Overlap (중첩) — same content in multiple places

### 2.1 "Do not re-define height/padding/border for form controls"

| Document | Location | Role |
|----------|----------|------|
| `ux-frontend-standard-principles.md` | §3 Search/filter UI standard | Principle + enforcement |
| `search-filter-form-design.mdc` | Single application point, Prohibited | Rule for search/filter |
| `css-standard-and-exceptions.md` | §3.1 Single application point, §4.4 Prohibited | CSS-layer enforcement |
| `forms-and-filters.md` | Prohibited (forms/filters) | Form/filter scope |

**Assessment**: Intentional reinforcement so that agents and humans see the rule wherever they look. **No conflict**; wording is consistent. Optional: in `css-standard-and-exceptions.md` §3.1 add "Authoritative single application point; principle in `ux-frontend-standard-principles.md` §3."

### 2.2 "Width by role (same role → same min/max)"

| Document | Location | Role |
|----------|----------|------|
| `ux-frontend-standard-principles.md` | §4 Role-based standard | Principle + role list |
| `forms-and-filters.md` | § Width by role | Form/filter width |
| `search-field-definition-items.md` | §4 Cross-field rules | Field-level schema |
| `search-filter-form-design.mdc` | Width by role | Rule for search/filter |
| `search-fields-by-screen.md` | §2, §3 tables | Authoritative **values** per screen/role |

**Assessment**: `search-fields-by-screen.md` is the single source for **values**; the others state the **rule** (same role = same size). No conflict. Optional: in `ux-frontend-standard-principles.md` §4 add "Per-screen role values: `search-fields-by-screen.md`; standard width vars: `search-filter-standard.css`."

### 2.3 "When standard is undefined, do not implement; inform user"

| Document | Location | Role |
|----------|----------|------|
| `ux-frontend-standard-principles.md` | §2 Required behavior, §10 Workflow, §11 Response | Full checklist + response format |
| `ux-frontend-standard-first.mdc` | Before implementing, Prohibited | Rule encoding |
| `search-filter-form-design.mdc` | When standard is undefined, Required workflow | Search/filter scope |
| `css-standard-and-exceptions.md` | Related + §4.4 Prohibited | CSS layer |
| `forms-and-filters.md` | Opening + Prohibited | Forms/filters scope |

**Assessment**: `ux-frontend-standard-principles.md` §2 and §10 are the **canonical** checklist and workflow; the others point to it or repeat the gate ("do not implement"). Slight duplication of the "don't implement" gate is acceptable so the rule fires in each context. **No conflict.**

---

## 3. Overshadowing (기존 범위가 새 영역을 가림)

When someone does **"one screen standardization"** (e.g. activity log + statistics alignment), they often start from: (1) a **requirement doc** (e.g. 20260311-activity-log-statistics-design-standards), (2) a **handoff** built from HANDOFF-CHECKLIST, (3) **Frontend** subagent (or frontend-agent rule). If none of these mention "standard-first: if standard undefined, stop and inform user," the new behavior can be **skipped**.

### 3.1 Requirement doc (e.g. 20260311-activity-log-statistics-design-standards.md)

- **Current**: §1 references design docs (forms-and-filters, search-fields-by-screen, css-standard-and-exceptions, UX-REDESIGN). §2 says "implementer must read and apply from design docs." No sentence like "if a needed standard is not defined in those docs, do not implement; inform the user and request standard definition first."
- **Risk**: Implementer follows the requirement and applies the design docs. For a **new** field or role not yet in search-fields-by-screen, they might add ad-hoc values (e.g. 180px) instead of asking for a standard first.
- **Recommendation**: In requirement **template** or in **§2.4 Implementation note** (in REQUIREMENTS-CHANGE-TARGET-CHECKLIST), add: "If the design docs do not define a needed standard (e.g. width by role, control size) for this change, do not implement; inform the user and request standard definition first per `docs/design/ux-frontend-standard-principles.md` §2 and §10." Optionally, when Requirements author adds §2.4 to a doc, they include this sentence in the implementation note.

### 3.2 HANDOFF-CHECKLIST (Frontend handoff)

- **Current**: "Design doc implementation (search/filter)" and "CSS standard and exceptions" tell the implementer to **read** design docs and use standard CSS. They do **not** say "if the standard is missing for this task, stop and inform user."
- **Risk**: Handoff is built from the checklist; Frontend receives "read search-fields-by-screen and search-filter-standard.css and apply." Frontend reads the docs, finds e.g. no width for "로그 타입" on statistics, and invents 180px. Standard-first gate is never applied.
- **Recommendation**: Add one bullet under **Frontend handoff**: "**Standard-first**: If the design docs do not define a needed standard (e.g. width by role, control size, icon) for this change, do not implement; inform the user and request standard definition first per `docs/design/ux-frontend-standard-principles.md` §2 and §10."

### 3.3 docs/design/README.md — "Approval when outside or conflicting"

- **Current**: Describes behavior when the **request** is "not in" or "conflicts with" standards → UX asks approval to define/update. Does **not** describe when the **standard is missing or ambiguous** (don't implement, inform user, define first).
- **Risk**: Readers might think "approval when outside" is the only "don't just do it" rule and miss the "standard undefined → inform and define first" flow.
- **Recommendation**: Add one short paragraph: "When a **standard is missing or ambiguous** for the task (e.g. no definition for a new field role or icon), do not implement; inform the user and ask for standard definition first. See `ux-frontend-standard-principles.md` §2 (checklist) and §10 (workflow)."

### 3.4 docs/cursor-subagents/frontend.md (Frontend subagent prompt)

- **Current**: "Search/filter form UI" and "CSS standard and exceptions" sections reference search-filter-form-design.mdc and design docs; say "read and apply" and "do not duplicate values." No "if standard undefined, inform user and do not implement first."
- **Risk**: When Frontend subagent is invoked with a handoff that doesn't mention standard-first, it may never run the "check standard exists" step.
- **Recommendation**: In the "Search/filter form UI" bullet, add: "If the design docs do not define a needed standard (e.g. width by role, control size) for this change, do not implement; inform the user and request standard definition first per `docs/design/ux-frontend-standard-principles.md` §2 and §10."

### 3.5 .cursor/rules/frontend-agent.mdc

- **Current**: Short; scope and API/spec only. No design docs or standard-first.
- **Risk**: When the **rule** "Frontend" is loaded (e.g. by glob), it might be the only rule read; ux-frontend-standard-first and search-filter-form-design apply only when their globs match. If the task is "fix activity log layout," both frontend-agent and search-filter-form-design (or ux-frontend-standard-first) might match; then both apply. If only frontend-agent matched (e.g. "add a new API call from frontend"), standard-first wouldn't apply anyway. So **low risk** for frontend-agent overshadowing; the main handoff and subagent prompt are the leverage points.

---

## 4. REQUIREMENTS-CHANGE-TARGET-CHECKLIST §2.4

- **Current**: §2.4 lists layout, form/panel width, spacing, a11y, design doc references, **Implementation note for Frontend** ("read and apply from search-field-definition-items and search-fields-by-screen"), and CSS standard. It does **not** say "if those docs don't define a needed standard, implementer must not implement and must inform user."
- **Risk**: Requirements author adds §2.4 and the implementation note as written; the note never mentions the standard-first gate. Implementer follows the note and may fill gaps ad-hoc.
- **Recommendation**: In §2.4 **Implementation note for Frontend**, append: "If the design docs do not define a needed standard (e.g. width by role, control size) for this change, do not implement; inform the user and request standard definition first per `docs/design/ux-frontend-standard-principles.md` §2 and §10."

---

## 5. Conclusion

- **Overlap**: No harmful duplication; multiple references reinforce the same rules. Optional: add one-line "authoritative source" cross-references in css-standard-and-exceptions and ux-frontend-standard-principles.
- **Overshadowing**: The new standard-first flow can be **skipped** when work is driven by (1) existing requirement doc, (2) handoff from HANDOFF-CHECKLIST, (3) Frontend subagent prompt — because none of them yet say "if standard undefined, inform user first." **Recommendation**: add the standard-first gate to HANDOFF-CHECKLIST (Frontend), design README, Frontend subagent prompt, and §2.4 Implementation note so that "one screen standardization" and any search/filter work consistently trigger the check.

---

*This analysis supports the decision to add minimal cross-references and one-sentence reminders in the handoff, README, subagent prompt, and §2.4 so the new standard-first behavior is not overshadowed by existing workflow.*
