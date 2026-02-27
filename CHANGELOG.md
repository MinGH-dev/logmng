# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### 2026-02-27

- **feat**: User permission hierarchy and permission group management (req `20250227-user-permission-hierarchy-group`): single-screen hierarchy by department (code/parent_code), users with role and permission groups per node; permission group CRUD and user assign/remove; new admin menu items; sample data (departments, permission groups, user–group assignments). APIs: `GET /api/departments/user-permission-hierarchy`, `GET/POST/PUT/DELETE /api/permission-groups*`, user–group assign/remove; admin-only (403 for non-admin).
- **fix**: DB schema and init-data not applied (req `20250227-user-permission-hierarchy-group-bugfix-1`): `setup.sh` updated to use `DB_SUPERUSER` (default `postgres`); schema.sql and init-data.sql applied to target DB; TC-01–TC-09 pass after backend restart. Delivered on `feat/cursor-commit-on-complete`.
- **docs**: New workflow docs — `DB-AGENT-REVIEW.md` (DB agent role review, apply steps, schema handoff); `QA-BROWSER-TEST-TROUBLESHOOTING.md` (browser automation failures, snapshot/refs, mitigations); `SUBAGENT-MODEL-SELECTION.md` (model per subagent for token optimization, fast vs default).
- **chore**: `.cursor` and docs updates — agent-collaboration, docs-reference, verify.md, QA.mdc, db-prompt; CURSOR-SUBAGENTS-DESIGN, SUBAGENT-DELEGATION, AGENT-COLLABORATION-ON-REQUIREMENT, CURSOR-AND-TOOLS-INTEGRATION; cursor-subagents db.md, qa-test.md.
- **chore**: `SUBAGENT-MODEL-SELECTION.md` — use concrete model names (`claude-haiku-4.5`, `sonnet4.6`) instead of presets (fast/default); agent-collaboration and SUBAGENT-DELEGATION model parameter wording aligned.

### 2026-02-26

- **UX (공통 사항)**: UX standards compliance audit and common verification — (1) UX standards compliance audit (req `20260225-ux-standards-compliance-audit`): alignment with `docs/design` (layout, grid-and-table, forms-and-filters, date-search, text-input, buttons). (2) Browser automation verification for frontend (TC-01~TC-08, §3.5): mandatory for frontend changes; policy in `docs/workflow/BROWSER-AUTOMATION-VERIFICATION-POLICY.md`. (3) Bugfix-1: date range validation `aria-invalid`/`aria-describedby` (req `20260225-ux-standards-compliance-audit-bugfix-1`). Delivered on `feat/cursor-commit-on-complete`.
- **docs**: Document language policy — tool-facing docs (docs/workflow, docs/template, docs/cursor-subagents) in English; requirements authored in English first, final Korean version after verification; commit message must reference requirement doc for traceability. New `docs/workflow/DOCUMENT-LANGUAGE-POLICY.md`; `.cursor/rules/language-policy.mdc`, `docs/template/REQUIREMENT_TEMPLATE.md`, `docs/template/BUGFIX_CHILD_TEMPLATE.md`, `.cursor/commands/commit-on-complete.md`, `.cursor/skills/requirement-doc/SKILL.md`, and workflow docs updated.
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
