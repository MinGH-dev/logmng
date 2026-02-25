# Browser Automation MCP (QA / Frontend / UX)

This project uses **browser automation** during verification and for UX/Frontend review. **Policy**: For **all frontend changes**, QA **must** run browser automation (when MCP is available), produce a **detailed verification report**, and on failure **hand off to Frontend** via a bugfix child requirement. See `docs/workflow/BROWSER-AUTOMATION-VERIFICATION-POLICY.md`.

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

## 3. Where subagents use it

| Subagent | Document | How they use browser automation |
|----------|----------|----------------------------------|
| **QA**   | `.cursor/commands/verify.md` § 3.5, `BROWSER-AUTOMATION-VERIFICATION-POLICY.md` | For **frontend** scope: **must** run browser verification (navigate, snapshot, §3 and §3.5 procedures). Write **detailed report** in §5: tool, base URL, per-TC Pass/Fail and note; for each Fail include what was checked, expected, actual. If any Fail → create bugfix child and **hand off to Frontend** to fix, then re-verify. |
| **Frontend** | `docs/cursor-subagents/frontend.md` | After build/restart, optional smoke: navigate to changed route, capture page, 1–2 key interactions before handoff to QA. When QA hands off a bugfix (browser verification failure), implement fix and hand back to QA for re-verification. |
| **UX**    | `docs/cursor-subagents/ux-design.md` | When reviewing a screen: open app, take snapshot/screenshot, compare with `docs/design/*` for concrete recommendations. |

## 4. Verification report format (QA → §5)

When QA runs browser automation, §5 must include:

- **Tool and URL**: e.g. "cursor-ide-browser, base http://localhost:3001".
- **Per TC (or scenario)**: Table with columns: **ID**, **Result** (Pass/Fail), **Note** (short). For **Fail** add: **Detail** — selector/ref, **expected**, **actual** (so Frontend can fix without guessing).
- **On failure**: QA creates `docs/requirements/{parentID}-bugfix-{N}.md`, describes the failure and expected fix, and hands off to **Frontend** (via main agent or user). Frontend fixes → build/restart → QA re-runs browser verification.

See `BROWSER-AUTOMATION-VERIFICATION-POLICY.md` §2.3 and §2.4.

## 5. References (for subagents)

- **Verification procedure**: `.cursor/commands/verify.md` — step 3.5 is **required** for frontend scope when MCP is available.
- **Policy (mandatory browser, report, handoff on failure)**: `docs/workflow/BROWSER-AUTOMATION-VERIFICATION-POLICY.md`.
- **Subagent prompts** (full text for Cursor Settings): `docs/cursor-subagents/qa-test.md`, `docs/cursor-subagents/frontend.md`, `docs/cursor-subagents/ux-design.md`.
- **Agent definitions** (short): `.cursor/agents/QA.mdc`, `.cursor/agents/Frontend.mdc`, `.cursor/agents/UX.mdc` — each references this doc and verify.md for browser steps.
