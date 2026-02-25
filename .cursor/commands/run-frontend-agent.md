# [Unused] Run frontend sub-agent

**This command is not used.** Create the Frontend sub-agent under Cursor **Settings → Subagents** and paste the contents of `docs/cursor-subagents/frontend.md`. Design: `docs/workflow/CURSOR-SUBAGENTS-DESIGN.md`.

---

# Run frontend sub-agent (reference only)

Launch a **sub-agent** to perform **frontend-only** work:

1. Read `.cursor/subagents/frontend-prompt.md` and build a string: its content + "## Task" + the task below.
2. Call **mcp_task** with `subagent_type`: `generalPurpose`, `description`: "Frontend sub-agent task", `prompt`: that string.
3. Summarize the sub-agent result for the user.

---
**[Task]** (user content goes below)
