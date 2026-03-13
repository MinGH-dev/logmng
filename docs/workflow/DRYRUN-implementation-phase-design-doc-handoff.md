# Dry-run: Implementation-phase design doc handoff (search/filter)

**Date**: 2026-03-11  
**Purpose**: Verify that after the improvements in `docs/workflow/ANALYSIS-implementation-phase-design-doc-usage.md`, the handoff flow produces a **Frontend** handoff that includes the **design doc implementation instruction** when the requirement involves search/filter UI (§2.4). No code changes; prompts only.  
**Procedure**: Per `.cursor/commands/dry-run-handoff.md`, using a **search/filter** virtual requirement so the Frontend handoff and new checklist item are exercised.

---

## 1. Virtual requirement

**Chosen**: Align **statistics filter** spacing and field sizes with design standards. The requirement touches search/filter UI (StatisticsFilters, activity log comparison), so it matches **REQUIREMENTS-CHANGE-TARGET-CHECKLIST §2.4** (Search/filter UI consistency). This exercises:

- Requirements: §2 must reference both design docs and include **Implementation note for Frontend**.
- Frontend handoff: Must include **Design doc implementation (search/filter)** instruction per HANDOFF-CHECKLIST.

---

## 2. Simulated handoff chain (Requirements → Frontend → QA)

### 2.1 Requirements handoff (main → Requirements)

**Task invocation the main agent would send:**

- **subagent_type**: Requirements  
- **Prompt**:  
  *"Author per docs/workflow/REQUIREMENTS-AUTHORING-WORKFLOW.md: User request — Align statistics filter spacing and field sizes with design standards (activity log and statistics same width, compact spacing, field-level values from design docs). Parallel input from experts if needed, codebase investigation, change target verification per REQUIREMENTS-CHANGE-TARGET-CHECKLIST.md (including §2.4 search/filter UI consistency and **Implementation note for Frontend**). Finalize §3. Do not implement; output requirement doc only."*

**Checks:**

- [x] Main does **not** author §1·§2·§3 (per `agent-collaboration.mdc` Step 1). **Pass.**
- [x] Prompt instructs Requirements to follow REQUIREMENTS-AUTHORING-WORKFLOW.md and change target verification (including §2.4 and implementation note). **Pass.**

### 2.2 Frontend handoff (main → Frontend)

**Task prompt the main agent would build** (excerpt; must include the new design doc implementation instruction):

```
Implement the Frontend scope for: Align statistics filter spacing and field sizes with design standards.

§1 Summary:
- User request: Statistics filter matches design standards (same container width as activity log, compact spacing, field-level sizing from design docs). Expected: layout, group titles, panel width, spacing, and accessibility aligned with activity log and docs/design (forms-and-filters, search-fields-by-screen, search-field-definition-items).

§2 Frontend (excerpt):
- StatisticsFilters.css: Align spacing with activity log and compact variant (row/field gap 8–12px, block 12–16px, padding 12–16px); ensure no statistics-only width that differs from activity log.
- ActivityStatistics.css: Confirm .activity-statistics max-width 1400px matches .activity-log-list-container.
- StatisticsFilters.js / StatisticsHeader.js: Confirm semantics and a11y (no change if already compliant).
- docs/design/search-fields-by-screen.md: Review or update statistics §3 for consistency with definition-items and activity log.
[Change file list as in requirement §2.]

§2 Implementation note for Frontend (from requirement §2):
Implementer must read and apply field-level and layout values from search-field-definition-items.md and search-fields-by-screen.md when changing form/filter CSS or components; requirement §2 numeric values (e.g. 8–12px) are consistent with those docs but must be verified or sourced from the docs.

Design doc implementation (search/filter) — mandatory for this handoff:
Before changing form/filter CSS or component layout, read docs/design/search-field-definition-items.md (§1 definition items, §4 cross-field rules) and docs/design/search-fields-by-screen.md (per-screen tables for the affected screen). Apply width, height, padding, and gap values from those docs; verify requirement §2 numeric excerpts against the docs.

§3 Frontend TCs: [TC-01 through TC-07 from requirement doc, Scope=Frontend.]

Search/filter (user-context screens): Apply unified axes and the `scope=self` rule "visible, fixed to current user, not editable" for applicable user/requester blocks per docs/analysis-search-consistency-by-screen.md.

Contract/spec: No API change; UI only.
```

**HANDOFF-CHECKLIST.md (Frontend) verification:**

| Item | Present in prompt? |
|------|--------------------|
| §1 summary | Yes |
| §2 Full Frontend subsection | Yes (solution + change file list) |
| §2.1 Security | N/A (UI only) |
| Contract/spec | Yes (no API change stated) |
| §3 Frontend TCs | Yes |
| Cross-scope | N/A |
| Search/filter (user-context screens) | Yes (analysis-search-consistency-by-screen) |
| **Design doc implementation (search/filter)** | **Yes — explicit instruction to read definition-items and search-fields-by-screen and apply width/height/padding/gap** |
| UX role (optional) | Omitted (not required) |
| Doc–code sync | N/A for this requirement |

**Pass**: All Frontend checklist items are covered; the **new** "Design doc implementation (search/filter)" item is present as an explicit instruction in the handoff.

### 2.3 QA handoff (main → QA)

**Task prompt the main agent would build:**

```
Verify implementation for requirement: Align statistics filter spacing and field sizes with design standards.

§1 Summary: Statistics filter matches design standards (container width, compact spacing, field-level from design docs).

§3 Full test case list:
- TC-01 (Frontend): Filter panel shows 로그 타입, 사용자 block, 기타 조건, 검색/초기화.
- TC-02 (Frontend): 초기화 clears and re-runs search.
- TC-03 (Frontend): Same container width (max 1400px) as activity log.
- TC-04 (Frontend): Group titles above fields; semantic group.
- TC-05 (Frontend): 검색/초기화 focusable.
- TC-06 (Frontend): Date validation aria-invalid/aria-describedby when invalid.
- TC-07 (Frontend): scope=self hides user and 기타 blocks.

Build and restart: Confirm when done (or QA runs them). Requirement doc path: docs/requirements/yyyyMMdd-statistics-design-standards.md. Update §5 after verification.
```

**HANDOFF-CHECKLIST.md (QA) verification:**

| Item | Present? |
|------|----------|
| §1 summary + §3 full TC list | Yes |
| Build/restart confirmation | Yes |
| Requirement doc path for §5/§6 | Yes |

**Pass**: All QA checklist items covered.

---

## 3. Verification table

| Rule / Document | Check | Pass? |
|-----------------|-------|-------|
| `agent-collaboration.mdc` Step 1 gate | Main does not author §1·§2·§3 when delegating to Requirements | Yes |
| `agent-collaboration.mdc` §3 gate | §3 exists before Step 4 | Yes (simulated doc has §3) |
| `REQUIREMENTS-AUTHORING-WORKFLOW.md` | Hybrid consultation + change target verification; when §2.4 applies, §2 includes Implementation note for Frontend | Yes |
| `HANDOFF-CHECKLIST.md` Frontend | All applicable items present (including **Design doc implementation (search/filter)**) | Yes |
| `HANDOFF-CHECKLIST.md` QA | All 3+ items present in QA handoff | Yes |
| `REQUIREMENT_TEMPLATE.md` §3 Scope tag | TCs have Scope column | Yes |
| **New** DESIGN DOC IMPLEMENTATION | Frontend handoff includes explicit "read and apply from definition-items and search-fields-by-screen" instruction when requirement involves search/filter UI | Yes |

---

## 4. Report

- **Dry-run result**: **Pass.**  
  - Requirements prompt instructs author to complete §2.4 and **Implementation note for Frontend**.  
  - Frontend handoff **includes** the mandatory **Design doc implementation (search/filter)** instruction so the implementer is told to read `search-field-definition-items.md` and `search-fields-by-screen.md` and apply width, height, padding, and gap from those docs before changing CSS or components.  
  - QA handoff satisfies the checklist.

- **Improvements verified**:  
  - HANDOFF-CHECKLIST.md Frontend new bullet is reflected in the simulated Frontend prompt.  
  - REQUIREMENTS-CHANGE-TARGET-CHECKLIST §2.4 "Implementation note for Frontend" and REQUIREMENTS-AUTHORING-WORKFLOW step 5.5 ensure the requirement doc contains text that the handoff builder can pass as the implementation instruction.  
  - docs/cursor-subagents/frontend.md strengthened bullet ensures the Frontend agent, when it receives the handoff, also has "read first, then apply" in its standing instructions.

- **No code changes** were made in this dry-run; only workflow, checklist, template, and analysis docs were edited in the preceding improvement step.

---

**References**: `docs/workflow/ANALYSIS-implementation-phase-design-doc-usage.md`, `docs/workflow/HANDOFF-CHECKLIST.md`, `docs/workflow/REQUIREMENTS-CHANGE-TARGET-CHECKLIST.md` §2.4, `docs/workflow/REQUIREMENTS-AUTHORING-WORKFLOW.md` step 5.5, `.cursor/commands/dry-run-handoff.md`.
