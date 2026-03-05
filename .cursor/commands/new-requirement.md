# Start a new requirement

1. **Do not write** the requirement doc in this chat. **Invoke the Requirements subagent** via the Task tool (`subagent_type="Requirements"`). Pass the user request (or paste it below) and instruct Requirements to author the doc per `docs/workflow/AGENT-COLLABORATION-ON-REQUIREMENT.md` §1.1 (parallel input from experts and Backend/Frontend/DB/QA, then orchestrate into §1·§2, finalize §3). After the requirement doc exists, proceed to Step 2/3/4 as needed.
2. Read **docs/workflow/WORKFLOW_CHECKLIST.md** for order and gates. Do **steps 1–3** (requirement doc → §3 test plan) **first**, then step 4 (development). Detail: `docs/workflow/DEVELOPMENT_WORKFLOW.md`.
3. **Agent collaboration**: Follow **docs/workflow/AGENT-COLLABORATION-ON-REQUIREMENT.md**. Sequence: **Requirements** → **Security** (if PII/decrypt/access) → **Contract** / **DBA** / **Architecture** / **Consistency** / **UX** (as needed) → **Backend/Frontend/DB** → **Review** (optional) → **QA** → **Documentation** / **Release** (as needed). **Role boundaries**: **docs/workflow/CURSOR-SUBAGENTS-DESIGN.md** §2.6.

**Current requirement (fill below):**
<!-- Describe the requirement here; pass this to Requirements subagent -->
