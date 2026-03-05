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

---

## Frontend handoff

- [ ] **§1**: One-paragraph summary.
- [ ] **§2**: Full **Frontend** subsection.
- [ ] **§2.1**: Full §2.1 if security/access affects UI.
- [ ] **Contract/spec**: For any API the frontend calls (request/response shape, error codes).
- [ ] **§3**: All TCs that involve Frontend (including manual/browser if applicable).
- [ ] **Cross-scope** (if any): e.g. "Backend returns error code X; show message Y."

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

---

## QA handoff

- [ ] **§1** summary and **§3** (full test case list for this requirement).
- [ ] Confirmation: build and restart done (or QA runs them).
- [ ] Requirement doc path for §5/§6 update.
- [ ] For frontend: browser automation expectations if any (see `BROWSER-AUTOMATION-VERIFICATION-POLICY.md`).

---

**Reference**: `CONTEXT-QUALITY-AND-ORCHESTRATION-MITIGATION.md` §2.5 (wrong compression), §4.7 (mandatory checklist), §4.9 (Review vs full doc).
