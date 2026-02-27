# QA Agent Browser Test Troubleshooting

**Purpose**: Diagnose why browser automation often fails when the QA subagent runs verification (step 3.5). This doc summarizes root causes and mitigations.

---

## 1. Observed failures (from requirement docs)

From `docs/requirements/20250227-permission-management-in-hierarchy.md` §5:

- **cursor-ide-browser**: `browser_lock` and `browser_snapshot` were run. **Snapshot response contained only metadata (viewId, title, url, locked); no DOM/refs for click.** So QA could not run `browser_click` or other interactions that need element refs.
- **browser_take_screenshot** **timed out**.
- Result: TC-01, TC-06, TC-07, TC-08 (UI) could not be executed; manual verification was recommended.

---

## 2. Root causes

### 2.1 Snapshot returns only metadata (no DOM/refs)

- **What happens**: `browser_snapshot` (cursor-ide-browser) sometimes returns only `viewId`, `title`, `url`, `locked` and **no accessibility tree / element refs**. Without refs, `browser_click`, `browser_fill`, etc. cannot target elements.
- **Likely reasons**:
  - Page not fully loaded (SPA): snapshot was taken too early before React/UI painted.
  - cursor-ide-browser behavior: in some states (loading, error, or security context) it may return a minimal response.
- **Mitigation**:
  - After `browser_navigate`, wait 2–3 seconds (or use `browser_wait_for` if available), then call `browser_snapshot` again.
  - If using **server-puppeteer** (project `.cursor/mcp.json`): use `puppeteer_navigate` → short wait → `puppeteer_screenshot` or `puppeteer_evaluate` to get selectors; then `puppeteer_click(selector)`. No refs needed; CSS selectors are used directly.

### 2.2 Screenshot timeout

- **What happens**: `browser_take_screenshot` (or similar) times out.
- **Likely reasons**: Large viewport, slow render, or MCP/server timeout.
- **Mitigation**:
  - Resize viewport smaller before screenshot (e.g. 1280×720) for verification-only runs.
  - Prefer **puppeteer_screenshot** when server-puppeteer is available (often more stable in headless).
  - If timeout persists, record in §5 that screenshot was skipped (reason: timeout) and use health check + snapshot-only for that run.

### 2.3 MCP tool set mismatch

- **What happens**: Docs say “project default is server-puppeteer” (`puppeteer_*`). User environment may have only **cursor-ide-browser** (`browser_*`) enabled. QA may call `puppeteer_navigate` when only `browser_navigate` exists (or the opposite), leading to “tool not found” or wrong flow.
- **Mitigation**:
  - In **QA prompt / verify.md**: “Before step 3.5, if you have **puppeteer_*** tools (e.g. puppeteer_navigate), use those for browser verification; otherwise use **browser_*** (browser_navigate → browser_lock → browser_snapshot → …).”
  - Ensure project `.cursor/mcp.json` has `browser` → server-puppeteer and Cursor is restarted so that `puppeteer_*` tools are available in the same workspace.

### 2.4 cursor-ide-browser lock order

- **Rule** (from MCP server instructions): **browser_lock requires an existing browser tab**; you cannot lock before navigate. Correct order: `browser_navigate` → `browser_lock` → (interactions) → `browser_unlock`.
- **Mitigation**: QA (and verify.md) already state this order. If QA calls `browser_lock` before any navigate, the flow will fail; keep the order explicit in the QA subagent prompt and in verify.md step 3.5.

### 2.5 Subagent (mcp_task) MCP access

- **What happens**: When QA is **invoked via mcp_task**, the subagent may run in a context where **MCP tools are not available** or only a subset is available. Then browser tools are missing and step 3.5 cannot run.
- **Mitigation**:
  - Prefer **manual handoff** for QA when browser verification is required: user switches to the **QA** subagent chat (Cursor Settings) and passes the handoff. The QA chat runs in the same workspace and typically has the same MCP as the main agent.
  - If mcp_task is used: in the handoff, ask QA to “if no browser MCP is available, record in §5 that browser verification was skipped (reason: MCP unavailable) and recommend manual verification.”

### 2.6 Viewport and first-call options

- **Puppeteer**: First `puppeteer_navigate` should include `launchOptions: { defaultViewport: { width: 1920, height: 1080 } }` so layout is consistent; otherwise default 800×600 may break layout checks.
- **cursor-ide-browser**: After `browser_navigate`, call `browser_resize` (e.g. 1920×1080) so that snapshot/screenshot matches a full-HD layout.

---

## 3. Recommended actions (short)

| Priority | Action |
|----------|--------|
| 1 | **Use server-puppeteer when possible**: Project `.cursor/mcp.json` already points to `@modelcontextprotocol/server-puppeteer`. Ensure it is enabled and Cursor restarted. QA should prefer `puppeteer_*` (navigate with launchOptions viewport, then screenshot/click by selector) to avoid ref-dependent snapshot issues. |
| 2 | **Wait before snapshot**: When using cursor-ide-browser, after `browser_navigate` wait 2–3 s (or `browser_wait_for`) then `browser_snapshot`; retry once if response has no refs. |
| 3 | **Document “which MCP” in §5**: QA should record in §5 which tool was used (cursor-ide-browser vs server-puppeteer / project-0-dev-browser) and, on failure, whether snapshot had refs and whether screenshot timed out. |
| 4 | **Frontend verification via manual handoff**: For frontend-heavy verification, have the user switch to the QA subagent chat and pass the handoff so QA runs in the same workspace with MCP available. |
| 5 | **Explicit tool-order in QA prompt**: In `docs/cursor-subagents/qa-test.md` and `.cursor/agents/QA.mdc`, add one line: “cursor-ide-browser: always browser_navigate first, then browser_lock, then browser_snapshot; never lock before navigate.” |

---

## 4. References

- Verification procedure: `.cursor/commands/verify.md` (step 3.5).
- Browser policy and report format: `docs/workflow/BROWSER-AUTOMATION-VERIFICATION-POLICY.md`.
- Tool mapping and viewport: `docs/workflow/BROWSER-AUTOMATION-MCP.md`.
- Real failure example: `docs/requirements/20250227-permission-management-in-hierarchy.md` §5 (Browser automation).
