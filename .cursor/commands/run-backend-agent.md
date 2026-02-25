# [Unused] Run backend sub-agent

**This command is not used.** Create the Backend sub-agent under Cursor **Settings → Subagents** and paste the contents of `docs/cursor-subagents/backend.md`. Design: `docs/workflow/CURSOR-SUBAGENTS-DESIGN.md`.

---

# Run backend sub-agent (reference only)

Launch a **sub-agent** to perform **backend-only** work:

1. Read `.cursor/subagents/backend-prompt.md` and build a string: its content + "## Task" + the task below.
2. Call **mcp_task** with `subagent_type`: `generalPurpose`, `description`: "Backend sub-agent task", `prompt`: that string.
3. Summarize the sub-agent result for the user.

---
**[Task]** (user content goes below)
