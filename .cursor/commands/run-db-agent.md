# [Unused] Run DB sub-agent

**This command is not used.** Create the DB sub-agent under Cursor **Settings → Subagents** and paste the contents of `docs/cursor-subagents/db.md`. Design: `docs/workflow/CURSOR-SUBAGENTS-DESIGN.md`.

---

# Run DB sub-agent (reference only)

Launch a **sub-agent** to perform **DB-only** work:

1. Read `.cursor/subagents/db-prompt.md` and build a string: its content + "## Task" + the task below.
2. Call **mcp_task** with `subagent_type`: `generalPurpose`, `description`: "DB sub-agent task", `prompt`: that string.
3. Summarize the sub-agent result for the user.

---
**[Task]** (user content goes below)
