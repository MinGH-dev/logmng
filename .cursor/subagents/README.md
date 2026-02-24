# Sub-agent execution

This folder holds **prompts and instructions** for sub-agents that can be run as separate processes (e.g. via mcp_task).

## Roles vs sub-agents

| Type | Description | When to use |
|------|-------------|-------------|
| **Role rules** (existing) | Rules that apply when working under `frontend/` etc. Same chat, role only. | When opening and editing files in that folder |
| **Sub-agents** (here) | Launch a **separate agent** via mcp_task to perform that role only. | When the user delegates, e.g. "hand this to the frontend agent", "run as backend sub-agent" |

## Sub-agent flow

1. User asks to delegate (e.g. "give this to the frontend sub-agent" or `/run-frontend-agent`).
2. Main agent reads `.cursor/subagents/<role>-prompt.md`, appends the user's task, and calls **mcp_task**.
3. mcp_task starts a **separate agent** that performs only that role and returns the result.

## Prompt files

- `frontend-prompt.md` — instructions for frontend-only sub-agent
- `backend-prompt.md` — instructions for backend-only sub-agent
- `db-prompt.md` — instructions for DB-only sub-agent

The main agent concatenates the file content and the user's task and passes it as the `prompt` argument to mcp_task.
