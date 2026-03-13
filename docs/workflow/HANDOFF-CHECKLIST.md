# Handoff checklist (scope-specific excerpts)

When building a **scope-specific handoff** for Backend, Frontend, DB, Review, or QA (see `CONTEXT-QUALITY-AND-ORCHESTRATION-MITIGATION.md` §4.1, §4.7), the builder (main agent or script) **must** include each applicable item below. This reduces **wrong compression**: omitting §2.1, contract, or cross-scope constraints.

---

## Backend handoff

- [ ] **§1**: One-paragraph summary (user scenario + expected outcome).
- [ ] **§2**: Full **Backend** subsection (solution, change file list for backend).
- [ ] **§2.1**: Full **§2.1 Security** if the requirement involves PII, decryption, or access control (include even when scope is "Backend").
- [ ] **Contract/spec**: Reference or excerpt of API/DB contract and `specs/*.spec.yaml` if the change touches API or schema.
- [ ] **§3**: **All** test cases that involve Backend (unit, integration, and any Integration TCs that imply Backend behavior).
- [ ] **Cross-scope** (if any): e.g. "Frontend will call POST /api/… with body X; implement to contract."
- [ ] **CONSISTENCY-STANDARDS**: When the change touches naming, error codes, logging, or file structure — reference **docs/workflow/CONSISTENCY-STANDARDS.md** so the implementer (Backend or delegated Backend-Auth/ActivityLog/Log) applies it.
- [ ] **Doc–code sync**: If the change adds or changes API paths, error codes, or shared constants, update `docs/api-definition.md` and/or `docs/contract.md` (and specs) in the same work. See **docs/workflow/DOC-CODE-SYNC.md**.

---

## Frontend handoff

- [ ] **Default standard lookup still applies**: Even when the handoff omits design-doc references, the Frontend implementer must read `docs/design/README.md` first and open the relevant `docs/design/*` standards based on the task type (layout, grid/table, forms/filters, buttons/inputs, CSS exceptions).
- [ ] **§1**: One-paragraph summary.
- [ ] **§2**: Full **Frontend** subsection.
- [ ] **§2.1**: Full §2.1 if security/access affects UI.
- [ ] **Contract/spec**: For any API the frontend calls (request/response shape, error codes).
- [ ] **§3**: All TCs that involve Frontend (including manual/browser if applicable).
- [ ] **Cross-scope** (if any): e.g. "Backend returns error code X; show message Y."
- [ ] **Shared UI primitive ownership**: If the issue was reported on one screen but may belong to a shared table/grid/layout/CSS primitive, include the shared fix target first (shared component, shared stylesheet, relevant design doc such as `docs/design/grid-and-table.md`) and state that screen-only workarounds are not allowed unless shared ownership is ruled out.
- [ ] **Shared footer contract**: For shared table/grid footer work, state explicitly that the footer region remains visible whenever footer metadata exists, including one-page datasets. Total count and rows-per-page are part of this always-visible shared footer contract.
- [ ] **Consumer verification set**: For shared UI primitive work, include the consumer screens that must be checked after the shared fix, not only the first screen where the defect was observed.
- [ ] **One-page navigation expectation**: For one-page datasets, state in the handoff that page navigation buttons may be absent or disabled, but the shared footer region itself must not be suppressed.
- [ ] **Search/filter (user-context screens)**: If the requirement touches search or filter UI on activity-log, statistics, user-management, permission-group-management, search-history, or pending-approvals — reference **docs/analysis-search-consistency-by-screen.md** (or the search-consistency-domain skill) so the implementer applies unified axes (department, user name, user ID) and the scope=self hiding rule.
- [ ] **Design doc implementation (search/filter)**: When the requirement involves search/filter UI (REQUIREMENTS-CHANGE-TARGET-CHECKLIST §2.4 or change file list touches StatisticsFilters, UserActivityLogSearchForm, etc.), include an **implementation instruction** in the handoff: "Before changing form/filter CSS or component layout, read `docs/design/search-field-definition-items.md` (§1 definition items, §4 cross-field rules), `docs/design/search-fields-by-screen.md` (per-screen tables for the affected screen), and `docs/design/forms-and-filters.md` (§ Single row for non-date, § Form per mode, § Width by role, § Compact variant). Apply width, height, padding, gap, and layout/structural rules from those docs; verify requirement §2 numeric excerpts against the docs."
- [ ] **User block / field width (when §2.4)**: When the requirement **aligns search/filter UI across two or more screens** (e.g. activity-log and statistics, or any user-context screens), include in the handoff: "Apply **same user block field width** on all aligned screens (e.g. `var(--sf-field-user-block-min)`, `var(--sf-field-user-block-max)` or same grid/field sizing from search-field-definition-items.md §4.5). Ensure layout does **not** squeeze the user block — e.g. do not put the user block and another control (log type, etc.) in a single `1fr` cell; give the user block its own column or sufficient min-width. Verify the TC that compares user block fields (department, user name, user ID) across screens (same min/max width and visual size)." Ref: `docs/workflow/ANALYSIS-user-field-size-activity-log-vs-statistics.md`.
- [ ] **CSS standard and exceptions**: When the requirement involves search/filter **CSS** (styling, spacing, layout), include in the handoff: "Use standard values from `frontend/src/styles/search-filter-standard.css` (variables `var(--sf-*)` or `.sf-*` classes); do not duplicate those values in component CSS. For **screen-specific overrides** (user-requested exceptions), implement only in the component's CSS file with a comment `/* Exception (req yyyyMMdd-name): reason */` and add a row to `docs/design/css-standard-and-exceptions.md` §5 Exception index." See `docs/design/css-standard-and-exceptions.md`.
- [ ] **Standard-first**: When the requirement touches search/filter, detail, icon, or field layout, include in the handoff the full response pattern: "If any required standard for layout, field sizing, spacing, icon usage, label placement, or control semantics is not defined or is ambiguous in the design docs, the implementer must not infer or hardcode a solution. The implementer must first (1) inform the user of the undefined standard items, (2) explain why each is needed, (3) propose a recommended standard draft, and (4) request feedback so the standard can be explicitly defined before implementation proceeds." See `docs/design/ux-frontend-standard-principles.md` §2 and §10.
- [ ] **UX role (optional)**: If Step 3d UX review was done, attach § UX review or design note. For permission/screen-access/button/scope UI, **docs/workflow/UX-ROLE-SEPARATION-DESIGN.md** defines definition vs implementation; Frontend implements per that matrix.
- [ ] **Doc–code sync**: If the change adds or changes API usage, error code handling, or shared constants, update `docs/api-definition.md` and/or `docs/contract.md` (and specs) in the same work. See **docs/workflow/DOC-CODE-SYNC.md**.

---

## DB handoff

- [ ] **§1**: One-paragraph summary.
- [ ] **§2**: Full **DB** subsection (schema, migrations, init-data).
- [ ] **§2.1**: Full §2.1 if schema or data touches PII/access.
- [ ] **Contract/spec**: Schema contract, `specs/*.spec.yaml` if DB shape is specified.
- [ ] **§3**: All TCs that involve DB or schema (e.g. migration, data checks).

---

## Review handoff

- [ ] **Full requirement doc** (§1–§3 at least) so Review can compare implementation against **full** doc, not only the excerpt the implementer received.
- [ ] Implemented change (files changed, or path to diff/branch).
- [ ] Instruction: "Verify implementation satisfies **all** of §2 and §3 that apply to this scope, including §2.1 and contract; not only what may have been in the handoff."
- [ ] **Doc–code sync**: If API paths, error codes, or shared constants were changed, confirm that `docs/api-definition.md` and/or `docs/contract.md` (and specs) were updated in the same PR. See **docs/workflow/DOC-CODE-SYNC.md**.

---

## QA handoff

- [ ] **§1** summary and **§3** (full test case list for this requirement).
- [ ] Confirmation: build and restart done (or QA runs them).
- [ ] Requirement doc path for §5/§6 update.
- [ ] For frontend: browser automation expectations if any (see `BROWSER-AUTOMATION-VERIFICATION-POLICY.md`).
- [ ] **Failure scope ux**: When verification fails due to UI/design/a11y, set failure scope **ux** and hand off to Requirements; UX role and handoff (UX → Frontend) are described in **docs/workflow/UX-ROLE-SEPARATION-DESIGN.md** §5.

---

**Reference**: `CONTEXT-QUALITY-AND-ORCHESTRATION-MITIGATION.md` §2.5 (wrong compression), §4.7 (mandatory checklist), §4.9 (Review vs full doc). Doc–code sync: **DOC-CODE-SYNC.md**.
