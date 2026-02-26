# .cursor terminology and naming (명칭·구분)

Single reference so **rules**, **commands**, **skills**, **agents**, and related docs use consistent names and roles. No confusion between "agent" vs "command" vs "skill", etc.

**Language**: This doc is in English (instruction layer). User-facing labels in Cursor (e.g. Subagent names) may be in any language.

---

## 1. Terms (what is what)

| Term | Meaning | Location | Who invokes / when applied |
|------|---------|-----------|----------------------------|
| **Rule** | Instruction the model follows in this workspace. Always-applied or conditional. **Not** user-invoked. | `.cursor/rules/*.mdc` | Cursor applies automatically when context matches |
| **Command** | Slash command the **user** types (e.g. `/verify`, `/new-requirement`). Triggers the attached instruction. | `.cursor/commands/*.md` | User types `/command-name` |
| **Skill** | Optional capability: agent **reads** the skill file when the task matches the skill description. Not always loaded. | `.cursor/skills/<name>/SKILL.md` | Agent fetches when task fits (e.g. "requirement doc", "test workflow") |
| **Agent (Subagent)** | A **role** (Frontend, Backend, QA, …). Implemented as: (1) **agent definition** (Cursor metadata); (2) **prompt** (text pasted in Cursor Settings). | Definition: `.cursor/agents/*.mdc`<br>Prompt text: `docs/cursor-subagents/*.md` | User switches to Subagent, or main agent invokes via mcp_task |
| **Main agent** | Default chat agent. Does not implement steps that have a dedicated Subagent; **delegates** (mcp_task or instructs user to switch). | — | User talks in default chat |
| **Delegation** | Main agent handing work to a Subagent (by mcp_task or by telling the user to switch and what to pass). | Rules: `agent-collaboration.mdc`<br>Docs: `SUBAGENT-DELEGATION.md`, `AGENT-COLLABORATION-ON-REQUIREMENT.md` | Main agent (or user manually) |
| **Delegation management** | Separate area for **improving** the delegation flow (analysis, backlog, DelegationManager). Not product agents. | `.cursor/delegation-mgmt/` | When improving workflow, not for feature work |
| **Prompt (subagent)** | The text that defines how a Subagent behaves. Stored in repo for versioning; pasted into Cursor Settings → Subagents. | `docs/cursor-subagents/*.md` | User pastes into Settings; Cursor uses when that Subagent is active |
| **MCP** | Model Context Protocol — external tools (e.g. browser automation). Config only. | `.cursor/mcp.json` | Cursor loads; agent uses MCP tools when available |

---

## 2. Naming conventions (avoid confusion)

### 2.1 Rules (`.cursor/rules/`)

- **Format**: `kebab-case.mdc`
- **General rules**: name by purpose — `contract-first.mdc`, `docs-reference.mdc`, `error-first-workflow.mdc`, `agent-collaboration.mdc`, `post-change-test-verify.mdc`, `workflow-todos.mdc`, `language-policy.mdc`, `security-permissions.mdc`, `core-principles.mdc`
- **Agent-scoped rules** (applied when a specific agent context is used): suffix `-agent` — `frontend-agent.mdc`, `backend-agent.mdc`, `db-agent.mdc`  
  → So: "rule" = behavior; "*-agent" = for that agent’s context only.
- **Do not**: use `command-*` or `skill-*` in rule names (reserved for commands/skills).

### 2.2 Commands (`.cursor/commands/`)

- **Format**: `kebab-case.md`; name = slash command name (e.g. `verify.md` → `/verify`).
- **Prefix by category** (so names are scannable):
  - **check-***: health/status — `check-backend.md`, `check-frontend.md`, `check-frontend-backend.md`, `check-db.md`
  - **start-*** / **stop-*** / **restart-***: service control — `start-all.md`, `start-frontend.md`, `restart-backend.md`, …
  - **verify**, **run-tests**: verification and test run
  - **new-requirement**, **plan**, **fix**, **review**, **follow-workflow**, **record-error-fix**: workflow
  - **agent-***: “use this agent” hint / legacy — `agent-frontend.md`, `agent-backend.md`, `agent-db.md`  
  → Prefer: “Switch to Frontend subagent” (no slash); keep `agent-*` only if you still want a shortcut.
- **Do not**: use `rule-*` or names that sound like rules (e.g. `contract-first.md` would blur with rule).

### 2.3 Skills (`.cursor/skills/<name>/`)

- **Format**: one folder per skill, `kebab-case` folder name; **exactly one** `SKILL.md` inside.
- **Examples**: `dev-workflow/`, `requirement-doc/`, `test-workflow/`, `db-domain/`
- **Naming**: folder = skill identity; describe scope in the first line of `SKILL.md` (so the agent knows when to use it).
- **Do not**: put commands or rules inside `skills/`; skills are “optional read when task matches”.

### 2.4 Agents / Subagents

- **Display name** (Cursor Settings → Subagents): e.g. **Frontend**, **Backend**, **QA**, **Requirements**. Can be PascalCase or short label.
- **Agent definition** (`.cursor/agents/`): `PascalCase.mdc` or `PascalCase-Module.mdc` — e.g. `Frontend.mdc`, `Backend-Log.mdc`, `RequirementsPastSearch.mdc`.
- **Prompt file** (`docs/cursor-subagents/`): `kebab-case.md` matching role — e.g. `frontend.md`, `backend.md`, `backend-log.md`, `requirements.md`, `past-requirements-search.md`.
- **Mapping**: CURSOR-SUBAGENTS-DESIGN.md and .cursor/agents/README.md list which prompt file goes to which Subagent name.
- **Delegation management agent**: lives under delegation-mgmt — `.cursor/delegation-mgmt/agents/DelegationManager.mdc` (clearly **not** a product Subagent).

### 2.5 Delegation and workflow docs

- **Delegation** = “who does which step”:
  - Rule: `agent-collaboration.mdc`
  - Docs: `SUBAGENT-DELEGATION.md`, `AGENT-COLLABORATION-ON-REQUIREMENT.md`
- **Workflow** = “order and gates”:
  - Docs: `WORKFLOW_CHECKLIST.md`, `DEVELOPMENT_WORKFLOW.md`
- Use the exact doc names above so rules and commands can reference them consistently.

### 2.6 Meta-criterion: similar names (e.g. DB vs DBA)

When two agents or tools belong to the same domain and their names look alike (e.g. **DB** vs **DBA**), use these standards so they are **scannable at a glance**:

**Role split (역할 구분)** — Two kinds of agents by responsibility:

| English | 한국어 | Meaning | Examples (Subagents) |
|---------|--------|---------|----------------------|
| **Implementation** | **수행자** | Directly performs changes (edits code/repo). | Frontend, Backend, DB (and module variants). |
| **Design / Review (ownership)** | **책임·설계(자)** | Owns design or review; proposes or approves; does **not** edit code. | DBA, Architecture, Security, Contract, UX, Consistency, Review. |

- **수행자** = the one who executes the work (schema, API, UI code, etc.).
- **책임·설계(자)** = the one responsible for design or review (설계·검토 책임); output is recommendations, §2.1, spec updates, or review report — implementation is done by a **수행자** (e.g. DB, Backend, Frontend).  
Using **(수행자)** and **(책임·설계)** (or **설계·검토**) as labels in tables/diagrams is consistent with this split.

1. **Primary split: Implementation vs Review-only**
   - **Implementation** (수행자) (edits code/repo): short, stack-oriented name — **Frontend**, **Backend**, **DB** (schema, migrations, scripts).
   - **Review-only** (책임·설계) (no code; design/review only): name should **not** be a one-letter variant of the implementer. Prefer a **role or suffix** that implies "review" or "design": **DBA** (schema design review), **Architecture**, **Security**, **Contract**, **UX**, **Consistency**, **Review**.

2. **Avoid ambiguous abbreviations**
   - If two names differ only by one letter or a short suffix (e.g. DB vs DBA), **visually separate** them:
     - **Option A**: Use a **full word** for the implementer and keep the abbreviation for the reviewer — e.g. **Database** (impl) vs **DBA** (review).
     - **Option B**: Add a **role suffix in parentheses** wherever both appear — e.g. **DB (Schema)** vs **DBA (Review)**. File names stay `DB.mdc` / `DBA.mdc`; the disambiguation is in the **label** shown to humans. Prefer Option B if you keep short names in Cursor Settings.

3. **Consistent listing**
   - In any table or diagram that shows both, **always** add the one-line role: e.g. `DB (schema, migrations)` and `DBA (schema design review, no code)`.

4. **Apply to other pairs**
   - Same idea for future pairs in one domain: **Contract** (spec edits) vs **Review** (change review); **Frontend** (code) vs **UX** (design review). Implementation = short stack name; Review = role/suffix that implies "no code" or "review".

**Role confirmation (DB vs DBA)**

- **DB**: Directly performs DB changes — schema, migrations, setup scripts, DB-related docs under `backend/.../db/`. **Implementation only.** Name **DB** is appropriate (short, stack-oriented; "DB layer" implementer).
- **DBA**: Review only — schema/design review, recommendations, no code edits. DB subagent applies changes. Name **DBA** is **acceptable but slightly overloaded**: in the industry "DBA" (Database Administrator) often implies both design review and operational/execution. In this project it is defined strictly as review-only, so:
  - **Keep "DBA"** if you rely on the (Review) label and docs to disambiguate; no rename needed.
  - **Optional rename** if you want the name itself to signal "review only": e.g. **Schema-Review** or **DB-Review** (Cursor Settings display name and docs). File names could stay `DBA.mdc` / `dba.md` for backward compatibility, or be renamed to match.

---

## 3. Quick decision table (“Is it a rule, command, skill, or agent?”)

| If it… | Then it’s… | Put it in… | Name like… |
|--------|------------|------------|------------|
| Tells the model how to behave in this project (always or when X) | **Rule** | `.cursor/rules/` | `purpose.mdc` or `scope-agent.mdc` |
| User invokes it with `/something` | **Command** | `.cursor/commands/` | `verb-or-noun.md` (check-*, start-*, verify, new-requirement, …) |
| Agent pulls it in only when the task matches (e.g. “write requirement”) | **Skill** | `.cursor/skills/<name>/SKILL.md` | folder: `kebab-case` |
| Is a **role** the user or main agent switches to / invokes | **Agent (Subagent)** | Def: `.cursor/agents/*.mdc`<br>Prompt: `docs/cursor-subagents/*.md` | Agent: `PascalCase.mdc`<br>Prompt: `kebab-case.md` |
| Describes how rules/commands/skills/agents connect | **Doc** | `docs/workflow/`, `.cursor/README.md` | — |
| Is only for improving delegation flow, not product features | **Delegation management** | `.cursor/delegation-mgmt/` | README, agents/DelegationManager.mdc, docs/ |

---

## 4. References

- **Layout and usage**: `.cursor/README.md`
- **How they connect (workflow phases)**: `docs/workflow/CURSOR-AND-TOOLS-INTEGRATION.md`
- **Subagent list and roles**: `docs/workflow/CURSOR-SUBAGENTS-DESIGN.md`, `.cursor/agents/README.md`
- **Delegation table**: `docs/workflow/SUBAGENT-DELEGATION.md`
