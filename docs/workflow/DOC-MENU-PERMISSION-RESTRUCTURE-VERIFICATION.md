# Documentation verification: menu/permission restructure (20260318)

**Requirement**: `docs/requirements/20260318-menu-and-permission-restructure.md`  
**Task**: Verify all documentation that references menu structure, screen IDs, or menu labels is either (1) already in the requirement's planned change list, or (2) should be added for sync.  
**Scope**: docs/workflow, docs/design, docs/template, docs/README.md, HANDOFF-CHECKLIST, REQUIREMENTS-CHANGE-TARGET-CHECKLIST, export/design, docs/cursor-subagents, README.md, CHANGELOG.md, and other non-contract docs mentioning menu/screen.  
**Date**: 2026-03-18

---

## (a) Docs that need no update or are already in the change list

| Document | Notes |
|----------|--------|
| **docs/contract.md** | In requirement's planned change list (§2 Contract/Spec). |
| **docs/api-definition.md** | In requirement's planned change list (§2 Contract/Spec). |
| **specs/permission-group-hierarchy.spec.yaml** | In requirement's planned change list (§2 Contract/Spec). |
| **.cursor/skills/auth-permission-domain/SKILL.md** | In requirement's planned change list (Cursor skills). |
| **.cursor/skills/ui-ux-domain/SKILL.md** | In requirement's planned change list (Cursor skills). |
| **.cursor/skills/api-permission-map/SKILL.md** | In requirement's planned change list (Cursor skills). |
| **docs/template/REQUIREMENT_TEMPLATE.md** | Only references domain patterns (e.g. scope-supporting screen); no concrete screen IDs or menu labels. No update required for this restructure. |
| **docs/README.md** | Only skill names (검색 이력, 로그 검색, 활동 이력) in descriptions; no menu structure or screen ID list. Optional: add a note that menu/screen set is defined in contract + ui-ux-domain after restructure. |
| **docs/cursor-subagents/*.md** | References are generic ("menu", "main search", "log search", design doc paths). No hardcoded screen ID or menu-label table. No change required for menu restructure. |

---

## (b) Docs that reference menu/screen but are NOT in the requirement's change list (gaps — recommend adding)

These documents contain **screen IDs** (main, search-main, search-history, pending-approvals) or **menu labels** (검색하기, 승인 대기, 검색 이력, 로그 검색, 이력·승인) or **menu structure** (이력·승인 order, 로그 검색 children). They should be added to the requirement's change list or explicitly updated so they stay in sync with the restructure.

### High impact (structure/labels directly defined)

| Document | What to align |
|----------|----------------|
| **docs/design/layout-improvement-ux-spec.md** | §(a) 메뉴 트리 table: currently "로그 검색 → 검색하기, 검색 이력" and "이력·승인 → 활동 이력, 승인 대기". Must change to: 로그 검색 → PB FEP Log, Java FW Image Log only; 이력·승인 → 활동 이력, 검색 이력, 복호화 승인 관리; remove "검색하기"; rename "승인 대기" → "복호화 승인 관리". |
| **docs/design/search-fields-by-screen.md** | §1 "검색하기 (main)" and §4 "검색 이력 (search-history)"; references to main, activity-log, statistics, search-history, 승인 대기. Update: main → pb-feplog / java-fw-imagelog per-screen; label "승인 대기" → "복호화 승인 관리"; 이력·승인 order (검색 이력 after 활동 이력). |
| **docs/analysis-search-consistency-by-screen.md** | §1 table: main (검색하기), search-history (검색 이력), pending-approvals (승인 대기); §2.2 "로그 검색 맥락 (main)", §2.3 "이력·승인 맥락". Update: main → pb-feplog / java-fw-imagelog or "로그 검색 (pb-feplog, java-fw-imagelog)"; label "승인 대기" → "복호화 승인 관리"; ensure "검색 이력" under 이력·승인 and order. |
| **export/design/permission-by-screen.md** | Full table of screen_id (main, search-history, pending-approvals) and menu labels (검색하기, 검색 이력, 승인 대기). Add pb-feplog, java-fw-imagelog; remove or deprecate main; rename "승인 대기" → "복호화 승인 관리"; update §2.1 main, §2.2 search-history, §2.5 pending-approvals and API examples. |
| **export/design/screen-api-mapping.md** | § screen_id table and sections "main (검색하기)", "search-history (검색 이력)", "pending-approvals (승인 대기)". Add pb-feplog, java-fw-imagelog; remove/deprecate main; rename pending-approvals label to "복호화 승인 관리"; update path→screen by log type. |
| **export/design/screen-user-actions.md** | Sections "main (검색하기)", "search-history (검색 이력)", "pending-approvals (승인 대기)". Same as screen-api-mapping: new screen IDs, label rename, and scope/decrypt per log type. |
| **export/design/db-definition.md** | screen_id fixed set and "검색하기 / main". Update allowed screen_id list (pb-feplog, java-fw-imagelog; main removed or deprecated). |
| **export/design/api-db-mapping.md** | "DB logs (검색하기 main)", "Search history (검색 이력)", search-history/pending APIs. Update wording to log-type screens where relevant; keep search-history/pending API paths (unchanged). |

### Medium impact (references in checklists, handoffs, analysis)

| Document | What to align |
|----------|----------------|
| **docs/workflow/HANDOFF-CHECKLIST.md** | References "activity-log, statistics, user-management, permission-group-management, search-history, pending-approvals". Add pb-feplog, java-fw-imagelog to Frontend search/filter bullet if those screens have search/filter; ensure "pending-approvals" label in handoff text is "복호화 승인 관리" where user-facing. |
| **docs/workflow/REQUIREMENTS-CHANGE-TARGET-CHECKLIST.md** | §3.2 "Permission or screen-access change" mentions "Frontend menu/sidebar and permission configuration UI". No concrete IDs; ensure pattern still applies to pb-feplog, java-fw-imagelog and 이력·승인 order. Optional: add one-line example "(e.g. pb-feplog, java-fw-imagelog, search-history under 이력·승인)". |
| **docs/workflow/DOC-CODE-SYNC.md** | References ScreenConstants, menuTree.js, screenFunctionDescriptions.js, contract, spec §4.1. Already about syncing; ensure runbook or checklist reminds to update after screen ID/menu restructure (could add "e.g. menu restructure 20260318"). |
| **docs/workflow/DRYRUN-user-search-fields-improvement.md** | Table with screen IDs (search-history, pending-approvals) and labels (검색 이력, 승인 대기); "main 제외". Update: main → pb-feplog/java-fw-imagelog or "로그 검색 (타입별)"; label "승인 대기" → "복호화 승인 관리". |
| **docs/workflow/PLAN-approval-only-group-tool-generalization.md** | Uses main, pending-approvals, "검색 이력"; approval-only condition "main 없음 + pending-approvals 있음". Update: approval-only to "pb-feplog/java-fw-imagelog 없음 + pending-approvals 있음" (or keep "main" if deprecated but document). |
| **docs/workflow/ANALYSIS-pending-approvals-scope-frontend-incomplete.md** | pending-approvals, "승인 대기", ScreenSelectionTree. Label "승인 대기" → "복호화 승인 관리" for consistency. |
| **docs/workflow/DRYRUN-search-ui-unify-handoff.md** | Lists activity-log, statistics, search-history, pending-approvals; "main(로그 검색)". Update to pb-feplog/java-fw-imagelog and label "복호화 승인 관리" where relevant. |
| **docs/workflow/REVIEW-agent-role-common-rules.md** | Lists activity-log, statistics, user-management, permission-group-management, search-history, pending-approvals. Add pb-feplog, java-fw-imagelog to the "user-context/search" set if Review checks log-search screens. |

### Lower impact (design standards, no ID table)

| Document | What to align |
|----------|----------------|
| **docs/design/search-field-definition-items.md** | Mentions "search-history, pending-approvals" and "검색하기, 활동 이력". Update to include pb-feplog, java-fw-imagelog and "복호화 승인 관리" where screen lists are given. |
| **docs/design/forms-and-filters.md** | Lists "검색 이력, 승인 대기" and "search-history, pending-approvals". Rename "승인 대기" → "복호화 승인 관리"; add pb-feplog, java-fw-imagelog if form rules apply to log search screens. |
| **docs/design/css-standard-and-exceptions.md** | SearchHistory component only; no menu/screen ID table. No change required for menu restructure. |
| **docs/design/README.md** | Refers to "검색하기, 활동 이력" in search-fields-by-screen description. Update to "로그 검색(pb-feplog, java-fw-imagelog), 활동 이력" (or "검색하기" removed per requirement). |
| **export/design/README.md** | References menuTree.js, App.js. No screen ID list; no change strictly required; optional note that screen set changed (see contract). |

### Optional / historical

| Document | Notes |
|----------|--------|
| **README.md** (root) | Feature list "로그 검색, 활동 이력, 통계, 승인·사용자 관리". Optional: after restructure, keep as-is (high-level) or add "복호화 승인 관리" if we rename in UI. |
| **CHANGELOG.md** | Historical entries (main, pending-approvals, 검색 이력). Do not rewrite history; optional: add new entry for 20260318 restructure (menu/screen ID and label changes). |

---

## (c) Pass/fail summary

**Question**: "All docs that need menu sync are identified."

**Result: PASS (with recommendation).**

- **Planned change list** in the requirement already includes: contract, api-definition, permission-group-hierarchy.spec.yaml, and the three Cursor skills. That is sufficient for **code and contract**.
- **Documentation** that describes **menu structure**, **screen IDs**, or **menu labels** for users/operators/implementers lives in:
  - **docs/design/** (layout-improvement-ux-spec, search-fields-by-screen, search-field-definition-items, forms-and-filters, README)
  - **docs/analysis-search-consistency-by-screen.md**
  - **export/design/** (permission-by-screen, screen-api-mapping, screen-user-actions, db-definition, api-db-mapping, README)
  - **docs/workflow/** (HANDOFF-CHECKLIST, REQUIREMENTS-CHANGE-TARGET-CHECKLIST, DOC-CODE-SYNC, and several ANALYSIS/DRYRUN/PLAN/REVIEW docs)

All of the above that reference the old menu/screen set or labels are listed in **(b)**. So:

- **(a)** Correctly identifies docs that need no update or are already in the change list.
- **(b)** Lists every doc that references menu/screen and is **not** in the requirement's change list — these are the **gaps** to add or update.
- **(c)** All docs that need menu sync are **identified**; the only remaining step is to **add** the items in (b) to the requirement's planned change list (or a "Documentation" subsection) and then update them when implementing the restructure.

**Recommendation for the requirement owner**

1. Add a **§2 subsection "Documentation (user/ops and design)"** to `20260318-menu-and-permission-restructure.md` with a **planned change list** that includes at least:
   - **docs/design**: layout-improvement-ux-spec.md, search-fields-by-screen.md, search-field-definition-items.md, forms-and-filters.md, README.md
   - **docs/analysis-search-consistency-by-screen.md**
   - **export/design**: permission-by-screen.md, screen-api-mapping.md, screen-user-actions.md, db-definition.md, api-db-mapping.md, README.md
   - **docs/workflow**: HANDOFF-CHECKLIST.md, REQUIREMENTS-CHANGE-TARGET-CHECKLIST.md, DOC-CODE-SYNC.md, and the ANALYSIS/DRYRUN/PLAN/REVIEW docs that contain screen IDs or menu labels (see table in (b)).
2. Assign **Step 6 (Documentation)** to update these docs when the menu/permission restructure is implemented (e.g. after QA verification), so menu structure, screen IDs, and labels stay in sync across workflow, design, and export/design.

---

**Reference**: Requirement `docs/requirements/20260318-menu-and-permission-restructure.md` §2 Planned change file list (Contract/Spec and Cursor skills only); `docs/workflow/AGENT-COLLABORATION-ON-REQUIREMENT.md` Step 6 Documentation.
