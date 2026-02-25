# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### 2026-02-26

- **docs**: Browser Automation verification policy — frontend changes require browser verification; detailed report in §5; on failure create bugfix child and hand off to Frontend.
- **docs**: New policy doc `docs/workflow/BROWSER-AUTOMATION-VERIFICATION-POLICY.md` — analysis of why browser automation wasn’t used initially; policy: mandatory browser verification for frontend, §3.5 when frontend-heavy, detailed report, handoff on failure.
- **chore**: `.cursor/commands/verify.md` — step 3.5 required for frontend scope; detailed report in §5; on failure create bugfix child and hand off.
- **chore**: `.cursor/agents/QA.mdc` — browser verification required for frontend; detailed report; on failure bugfix child + hand off to Frontend.
- **chore**: `docs/workflow/SUBAGENT-DELEGATION.md` — Step 5 QA row: mandatory browser automation for frontend, detailed report, handoff on failure.
- **chore**: `docs/workflow/BROWSER-AUTOMATION-MCP.md` — policy link; QA row "must" + report format; §4 verification report format; §2.4 handoff on failure.
- **chore**: `docs/cursor-subagents/qa-test.md` — same policy; reference to BROWSER-AUTOMATION-VERIFICATION-POLICY.
- **chore**: `docs/template/REQUIREMENT_TEMPLATE.md` — added §3.5 브라우저 자동화 검증 (optional for frontend-heavy requirements).
- **chore**: `docs/workflow/CURSOR-AND-TOOLS-INTEGRATION.md` — added BROWSER-AUTOMATION-VERIFICATION-POLICY to browser row.

### 2026-02-25

- **docs**: design standards in `docs/design/` — grid-and-table, layout-and-navigation, buttons, text-input, date-search, forms-and-filters, README; approval flow for design system.
- **chore**: UX agent (`.cursor/agents/UX.mdc`) — design system owner; approval when changes are outside or conflict with standards.
- **chore**: `docs/cursor-subagents/ux-design.md` — index to design docs; no screen-specific reference.

### 2025-02-24

- **feat**: search-history — re-query form sync and detail view modal
- **feat**: search-history — 검색 이력 및 복호화 승인 재요청 부가 기능 (요건 20260224)
- **feat**: cursor — add commit-on-complete workflow step (verify 후 커밋)
- **chore**: release checklist with remote push step (`docs/workflow/RELEASE_CHECKLIST.md`)
