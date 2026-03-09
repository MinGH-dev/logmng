# Development subagents: query experts when detail is needed (§1.3)

When **Backend**, **Frontend**, or **DB** (Step 4) implement from the requirement doc and need **detailed information** that the doc does not fully specify and that **falls in another agent's domain**, they must **query that expert subagent** instead of assuming.

**Who to query**:

- **UX** (layout, design, a11y, interaction): e.g. "Requirement doc X §2 — need exact layout/breakpoints for component Y."
- **Contract** (API shape, request/response, env): e.g. "Requirement doc X — need exact request body and response shape for endpoint Z."
- **DBA** (schema, indexes, JSON vs relational): e.g. "Requirement doc X — need final column list and index recommendation for table T."
- **Security** (access rules, PII handling): e.g. "Requirement doc X — need access rule for role R on resource S."
- **Consistency** (naming, error codes): e.g. "Requirement doc X — need error code and message for case C."

**How to query**: Invoke the expert subagent via the **Task tool** with a short description and the requirement doc path (e.g. `Task(subagent_type="UX", prompt="Requirement doc: docs/requirements/yyyyMMdd-name.md. Question: [focused question]. Please return [expected output].")`). If the Task tool is unavailable, ask the user to have the main agent invoke that subagent with the same question.

**Do not invent** answers in another agent's domain; get the answer from the owning agent, then continue implementation.

---

**Index**: `docs/workflow/AGENT-COLLABORATION-ON-REQUIREMENT.md`. Change file list (tentative → confirmed): `docs/workflow/REQUIREMENTS-AUTHORING-WORKFLOW.md` §1.2.
