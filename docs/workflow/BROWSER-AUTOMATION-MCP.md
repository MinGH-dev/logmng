# Browser Automation MCP (QA / Frontend / UX)

This project uses **browser automation** during verification and for UX/Frontend review. Subagents (QA, Frontend, UX) may use MCP browser tools when available.

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
| **QA**   | `.cursor/commands/verify.md` § 3.5 | After health check, optionally open http://localhost:3001, capture page (snapshot/screenshot), optionally run 1–2 §3 critical-path actions; record result in §5. |
| **Frontend** | `docs/cursor-subagents/frontend.md` | After build/restart, optional smoke: navigate to changed route, capture page, 1–2 key interactions before handoff to QA. |
| **UX**    | `docs/cursor-subagents/ux-design.md` | When reviewing a screen: open app, take snapshot/screenshot, compare with `docs/design/*` for concrete recommendations. |

## 4. References (for subagents)

- **Verification procedure**: `.cursor/commands/verify.md` — step 3.5 is the optional browser check; use the tool mapping above if your MCP is server-puppeteer.
- **Subagent prompts** (full text for Cursor Settings): `docs/cursor-subagents/qa-test.md`, `docs/cursor-subagents/frontend.md`, `docs/cursor-subagents/ux-design.md`.
- **Agent definitions** (short): `.cursor/agents/QA.mdc`, `.cursor/agents/Frontend.mdc`, `.cursor/agents/UX.mdc` — each references this doc and verify.md for browser steps.
