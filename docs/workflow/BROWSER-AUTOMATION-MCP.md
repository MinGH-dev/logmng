# Browser Automation MCP (QA / Frontend / UX)

This project uses **browser automation** during verification and for UX/Frontend review. **Policy**: For **all frontend changes**, QA **must** run browser automation (when MCP is available) and produce a **detailed verification report**. On **any** verification failure (frontend or not), QA creates a bugfix child, **identifies failure scope**, and **hands off to Requirements**; Requirements **delegates to the responsible expert by scope** (Frontend, Backend, DB, Security, Contract, UX, etc.); when issues are **closed**, **QA re-runs verification**; when all pass, QA commits. See `docs/workflow/BROWSER-AUTOMATION-VERIFICATION-POLICY.md`.

## 1. Configuration and activation

### Project-level (recommended)

- **File**: `.cursor/mcp.json` in the project root.
- **Server**: `@modelcontextprotocol/server-puppeteer` (runs via `npx`; opens a browser window).
- **Activation**: After editing `.cursor/mcp.json`, **restart Cursor completely** (MCP servers load at startup). Then confirm in Cursor **Settings → Tools & MCP** that the "browser" server is listed and enabled.

### Alternative: Cursor built-in "cursor-ide-browser"

If your Cursor version provides **cursor-ide-browser** in **Settings → Tools & MCP** and it works, you can enable that instead. Tool names will be `browser_*` (e.g. `browser_navigate`, `browser_snapshot`). If you use that, follow the same procedures below; the tool names in this doc map as identity (no change).

## 2. Tool mapping (Puppeteer MCP vs cursor-ide-browser)

When using **@modelcontextprotocol/server-puppeteer** (as in `.cursor/mcp.json`), the tools are named with a `puppeteer_` prefix. Use this mapping when following verify.md step 3.5 or subagent guidance:

| Procedure step | cursor-ide-browser | server-puppeteer (this project) |
|----------------|--------------------|----------------------------------|
| Open URL       | `browser_navigate`  | `puppeteer_navigate`             |
| Page structure / DOM | `browser_snapshot` | `puppeteer_screenshot` (full page or element) or run JS to inspect |
| Click          | `browser_click`     | `puppeteer_click` (CSS selector)  |
| Type / fill    | `browser_type`, `browser_fill` | `puppeteer_fill` (selector + value) |
| Screenshot     | `browser_take_screenshot` | `puppeteer_screenshot`       |

- **Lock/unlock**: Puppeteer MCP does not use lock/unlock; use a single flow (navigate → interact → done). For SPA loading, wait a few seconds or poll with another `puppeteer_screenshot` before clicking.
- **Wait strategy**: Prefer short waits (1–3 s) then take another screenshot or action instead of one long wait.

### 2.1 Browser viewport size (why it’s small and how to change it)

The automation browser often opens with a **small window** (e.g. 800×600) because:

- **Puppeteer MCP** (`server-puppeteer` / `project-0-dev-browser`): default viewport is **800×600**. Screenshot defaults are also 800×600 unless overridden.
- **cursor-ide-browser**: default tab size may be small depending on the embedded view.

You can get a **larger, desktop-like view** as follows:

| MCP | How to set larger viewport |
|-----|----------------------------|
| **cursor-ide-browser** | After `browser_navigate`, call **`browser_resize`** with `width` and `height` (e.g. `width: 1920`, `height: 1080`). |
| **server-puppeteer / project-0-dev-browser** | On **first** `puppeteer_navigate`, pass **`launchOptions`**: `{ "defaultViewport": { "width": 1920, "height": 1080 } }`. Changing `launchOptions` can restart the browser, so use the same options for the whole session. |

Recommended size for verification: **1920×1080** so layout and tables render like a full HD desktop screen. QA and other subagents running browser verification should use this viewport when opening the app (navigate with launchOptions, or navigate then resize) so §3 test cases run in a consistent, readable view.

## 3. Where subagents use it

| Subagent | Document | How they use browser automation |
|----------|----------|----------------------------------|
| **QA**   | `.cursor/commands/verify.md` § 3.5, `BROWSER-AUTOMATION-VERIFICATION-POLICY.md` | For **frontend** scope: **must** run browser verification. Write **detailed report** in §5. If any Fail: create bugfix child, **identify failure scope** (frontend, backend, db, security, contract, ux), **hand off to Requirements**. Requirements delegates to **responsible expert by scope**; when expert **closes** issue → **QA** re-verifies; when all pass → commit. |
| **Frontend** | `docs/cursor-subagents/frontend.md` | After build/restart, optional smoke before handoff to QA. When **Requirements** hands off a bugfix (scope frontend or ux→Frontend), implement fix, then hand off to **QA** for re-verification. Same for Backend/DB: when issue closed, hand off to QA. |
| **UX**    | `docs/cursor-subagents/ux-design.md` | When reviewing a screen: open app, take snapshot/screenshot, compare with `docs/design/*` for concrete recommendations. |

## 4. Verification report format (QA → §5)

When QA runs browser automation, §5 must include:

- **Tool and URL**: e.g. "cursor-ide-browser, base http://localhost:3001".
- **Per TC (or scenario)**: Table with columns: **ID**, **Result** (Pass/Fail), **Note** (short). For **Fail** add: **Detail** — selector/ref, **expected**, **actual** (so Frontend can fix without guessing).
- **On failure**: QA creates the bugfix doc, **identifies failure scope** (frontend | backend | db | security | contract | ux), and hands off to **Requirements**. Requirements formalizes the doc and **delegates to the responsible expert by scope** (Frontend, Backend, DB, Security, Contract, UX→Frontend). When that expert **closes** the issue → hand off to **QA** → QA re-runs verification; when all pass, QA commits.

See `BROWSER-AUTOMATION-VERIFICATION-POLICY.md` §2.3 and §2.4.

## 5. References (for subagents)

- **Verification procedure**: `.cursor/commands/verify.md` — step 3.5 is **required** for frontend scope when MCP is available.
- **Policy (mandatory browser, report, handoff on failure)**: `docs/workflow/BROWSER-AUTOMATION-VERIFICATION-POLICY.md`.
- **Subagent prompts** (full text for Cursor Settings): `docs/cursor-subagents/qa-test.md`, `docs/cursor-subagents/frontend.md`, `docs/cursor-subagents/ux-design.md`.
- **Agent definitions** (short): `.cursor/agents/QA.mdc`, `.cursor/agents/Frontend.mdc`, `.cursor/agents/UX.mdc` — each references this doc and verify.md for browser steps.
