# Analysis: Search field design doc reference gaps and improvements

**Context**: The requirement doc for statistics screen design standards (`20260311-statistics-screen-design-standards.md`) references `docs/design/search-fields-by-screen.md` (per-screen field definitions) but **does not** reference `docs/design/search-field-definition-items.md` (field-level definition schema). The project’s search/filter design standard is defined by **both** documents; omitting the schema doc in requirement authoring leads to incomplete design alignment.

**Conclusion**: The gap is due to vague §1 wording, solution focus on “where to write” only, rule scope (code-only globs), and missing checklist items. Recommended improvements are listed in §4.

---

## 1. The two design docs (검색 필드별 디자인 표준)

| Document | Role | Referenced in 20260311-statistics-screen-design-standards? |
|----------|------|------------------------------------------------------------|
| **search-fields-by-screen.md** | Per-screen field definitions (검색하기, 활동 이력, 통계). Where to list fields and blocks. | Yes — multiple times (§1, §2, change file list). |
| **search-field-definition-items.md** | Field-level schema: fieldId, controlType, width, height, constraints, dataSource, etc. How to define each field. | No — not mentioned. |

Both are mandatory in `.cursor/rules/search-filter-form-design.mdc` and in Frontend/UX subagent docs. When a requirement touches search/filter field design, **both** should be cited in the requirement doc so that implementers and design-doc authors follow the same schema.

---

## 2. Root causes (why the schema doc was not referenced)

### 2.1 Vague §1 wording

- §1 says: “Design standards are defined in `docs/design/` (e.g. forms-and-filters, **search-field definitions**, UX redesign).”
- “Search-field definitions” can be read as only the per-screen document (search-fields-by-screen). The **definition items** document (search-field-definition-items) is not explicitly named, so the Requirements author had no prompt to add it.

### 2.2 Solution focused only on “where to write”

- §2 states: “Add a statistics section to **search-fields-by-screen.md**” and “Reuse the same width/height/padding/controlType as activity log.”
- The **target file** (search-fields-by-screen) is clear; the **schema to follow** (search-field-definition-items) when writing that section is not stated. So the requirement specifies where to document fields but not which definition schema to apply.

### 2.3 Rule does not apply to requirement docs

- `search-filter-form-design.mdc` has globs like `frontend/**/SearchForm*`, `**/StatisticsFilters*` — i.e. **code files** only.
- Requirement docs (`docs/requirements/*.md`) are not in scope, so the rule “follow search-field-definition-items + search-fields-by-screen” is not triggered when **authoring** the requirement. The gap appears at authoring time, not at implementation time.

### 2.4 Checklist has no “design doc reference” check

- `REQUIREMENTS-CHANGE-TARGET-CHECKLIST.md` §2.4 (Search/filter UI consistency) lists layout, form/panel width, spacing, a11y and references `forms-and-filters.md`, `analysis-search-consistency-by-screen.md`.
- It does **not** require that requirement docs explicitly reference **both** search field design docs (`search-fields-by-screen.md` and `search-field-definition-items.md`) when the requirement involves search/filter field design or documentation. So the Requirements author is not reminded to add the schema doc.

### 2.5 Different roles of the two docs

- **search-fields-by-screen** appears naturally as a **change target** (e.g. “add statistics section”).
- **search-field-definition-items** is a **design standard to follow**, not a file to edit in that requirement. Unless the requirement explicitly lists “design standards to apply,” the schema doc stays out of §1/§2.

---

## 3. Impact

- Implementers and design-doc updaters may only open search-fields-by-screen and omit the definition-items schema when adding or changing fields (e.g. 로그 타입, period UI), leading to inconsistent field specs (size, controlType, constraints).
- Future requirement authors may copy the same pattern (citing only search-fields-by-screen) and perpetuate the gap.

---

## 4. Recommended improvements

### 4.1 Requirement doc §1: explicit design standard list

When a requirement aligns or defines **search/filter UI or fields** (e.g. activity log, statistics, search-history), §1 should name **both**:

- `docs/design/search-fields-by-screen.md` (per-screen field definitions)
- `docs/design/search-field-definition-items.md` (field-level definition schema)

**Action**: Update the requirement template or authoring workflow so that for search/filter-related requirements, §1 “design standards” or “references” explicitly list these two docs (and any other design docs that must be followed). Avoid generic phrases like “search-field definitions” without naming the schema doc.

### 4.2 REQUIREMENTS-CHANGE-TARGET-CHECKLIST §2.4: add design doc references

In **§2.4 Search/filter UI consistency**, add a row or bullet:

- **Design doc references**: When the requirement involves search/filter field design or documentation (e.g. adding a statistics section to search-fields-by-screen), §1 or §2 must explicitly reference **both** `docs/design/search-fields-by-screen.md` and `docs/design/search-field-definition-items.md`. Implementers and doc updaters use the schema in definition-items when writing or updating the per-screen table.

**Action**: Edit `REQUIREMENTS-CHANGE-TARGET-CHECKLIST.md` §2.4 to include this touchpoint and, if useful, add the two doc paths to the “Reference” line in §2.4.

### 4.3 Requirements authoring workflow: design-doc list for search/filter

In **REQUIREMENTS-AUTHORING-WORKFLOW.md**, in the step that deals with “aligning screen A with screen B” or “search/filter UI,” add a short note:

- When the requirement touches **search/filter form or field design**, ensure §1 or §2 list the design docs that define the standard: at least `forms-and-filters.md`, and for **field definitions** both `search-fields-by-screen.md` and `search-field-definition-items.md`. See `REQUIREMENTS-CHANGE-TARGET-CHECKLIST.md` §2.4.

**Action**: Add one sentence or bullet in the appropriate subsection (e.g. near the reference to §2.4 and form/panel dimensions).

### 4.4 (Optional) Broaden rule trigger for “design doc” context

If the project wants requirement **authoring** to always consider the search-field design pair when the requirement is about search/filter UI:

- Either add a note in the Requirements subagent instructions (or in the workflow) that when drafting §1·§2 for search/filter alignment or field documentation, the author must cite both search-fields-by-screen and search-field-definition-items; or
- Introduce a lightweight checklist or prompt that runs when the requirement topic is “search,” “filter,” “검색 조건,” or “통계/활동 이력 필드” to “confirm both search field design docs are referenced.”

**Action**: Product decision. Minimal fix is §4.1–4.3; this item is for stricter process.

### 4.5 Retrofit the statistics requirement doc (optional)

For `20260311-statistics-screen-design-standards.md`, add an explicit reference to `docs/design/search-field-definition-items.md` in §1 (e.g. in the “Design standards are defined in …” sentence) and in §2 where it says “Reuse the same width/height/padding/controlType” (e.g. “per search-field-definition-items.md and search-fields-by-screen.md”). This does not change implementation scope but improves traceability and sets the pattern for future docs.

**Action**: One-time edit to the existing requirement doc; optional if §4.1–4.3 are applied to future authoring.

---

## 5. Summary

| Cause | Improvement |
|-------|-------------|
| §1 “search-field definitions” vague | §1 explicitly list search-fields-by-screen **and** search-field-definition-items for search/filter requirements (§4.1). |
| §2 only says “where to write” | §2 (or §4.1) state that field definitions must follow the schema in search-field-definition-items (§4.1, §4.5). |
| Rule globs exclude requirement docs | Workflow/checklist remind the author to cite both docs when topic is search/filter (§4.3, §4.4). |
| Checklist §2.4 omits design doc refs | Add “design doc references” touchpoint to §2.4 (§4.2). |

**References**: `docs/requirements/20260311-statistics-screen-design-standards.md`, `docs/design/search-field-definition-items.md`, `docs/design/search-fields-by-screen.md`, `.cursor/rules/search-filter-form-design.mdc`, `REQUIREMENTS-CHANGE-TARGET-CHECKLIST.md` §2.4, `REQUIREMENTS-AUTHORING-WORKFLOW.md`.

---

## 6. Implementation status (2026-03-11)

- **§4.1**: Applied — `docs/template/REQUIREMENT_TEMPLATE.md` §1 Note now requires explicit reference to both `search-fields-by-screen.md` and `search-field-definition-items.md` when the requirement defines or aligns search/filter fields.
- **§4.2**: Applied — `REQUIREMENTS-CHANGE-TARGET-CHECKLIST.md` §2.4 has a new row **Design doc references** and updated Reference line.
- **§4.3**: Applied — `REQUIREMENTS-AUTHORING-WORKFLOW.md` step 3 (Codebase investigation) has a new bullet for search/filter form or field design (list both design docs in §1 or §2).
- **§4.5**: Applied — `docs/requirements/20260311-statistics-screen-design-standards.md` §1 and §2 now explicitly reference `search-field-definition-items.md` alongside `search-fields-by-screen.md`.
- **Handoff dry-run**: Completed — `docs/workflow/DRYRUN-handoff-after-search-field-doc-improvements.md`; verification table and report **Pass**.
