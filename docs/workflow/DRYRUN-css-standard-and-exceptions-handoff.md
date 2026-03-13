# Dry-run: Handoff verification — CSS standard and exception management

**Date**: 2026-03-11  
**Purpose**: Verify that after adding **CSS standard and exceptions** to the workflow (HANDOFF-CHECKLIST Frontend, REQUIREMENTS-CHANGE-TARGET-CHECKLIST §2.4, search-filter-form-design.mdc, frontend.md), the handoff flow runs correctly and the new touchpoints are exercised. No code changes; prompts only.  
**Procedure**: Per `.cursor/commands/dry-run-handoff.md`, using a virtual requirement that involves **search/filter CSS** and a **user-requested exception** so that Requirements §2.4 (CSS standard row), Frontend handoff (Design doc + **CSS standard and exceptions**), and QA are all verified.

---

## 1. Virtual requirement

**Chosen**: "통계 검색 폼에 사용자 요청으로 검색 버튼을 오른쪽 끝에만 배치하는 예외 적용."  
The requirement touches search/filter **CSS** (StatisticsFilters) and a **screen-specific override** (exception), so it matches:

- **REQUIREMENTS-CHANGE-TARGET-CHECKLIST §2.4** (Search/filter UI consistency) and the new **CSS standard and exceptions** row.
- **HANDOFF-CHECKLIST Frontend**: Design doc implementation (search/filter) + **CSS standard and exceptions** (new item).

This exercises: Requirements citing css-standard-and-exceptions and standard CSS file; Frontend handoff including the instruction to use standard CSS and to document the exception (comment + Exception index §5).

---

## 2. Simulated handoff chain (Requirements → Frontend → QA)

### 2.1 Requirements handoff (main → Requirements)

**Task invocation the main agent would send:**

- **subagent_type**: Requirements  
- **Prompt**:  
  *"Author per docs/workflow/REQUIREMENTS-AUTHORING-WORKFLOW.md: User request — 통계 검색 폼에 사용자 요청으로 검색 버튼을 오른쪽 끝에만 배치하는 예외 적용. This is a screen-specific CSS exception. Parallel input from experts if needed, codebase investigation, change target verification per REQUIREMENTS-CHANGE-TARGET-CHECKLIST.md (including §2.4 search/filter UI consistency, **CSS standard and exceptions** touchpoint, and Implementation note for Frontend). Finalize §3. Do not implement; output requirement doc only."*

**Checks:**

- [x] Main does **not** author §1·§2·§3 (per `agent-collaboration.mdc` Step 1). **Pass.**
- [x] Prompt instructs Requirements to run change target verification including §2.4 and **CSS standard** row. **Pass.**

### 2.2 Frontend handoff (main → Frontend)

**Task prompt the main agent would build** (excerpt; must include Design doc implementation **and** CSS standard and exceptions):

```
Implement the Frontend scope for: 통계 검색 폼에 사용자 요청으로 검색 버튼을 오른쪽 끝에만 배치하는 예외 적용.

§1 Summary:
- User request: Apply a screen-specific exception for the statistics search form — place the search button at the right end only (user-requested). Standard layout remains for other screens.

§2 Frontend (excerpt):
- StatisticsFilters.css: Add exception for .statistics-filters__actions (e.g. margin-left: auto or justify-content: flex-end) only for this screen. Do not change frontend/src/styles/search-filter-standard.css. Use standard values (var(--sf-*)) for spacing/control sizes elsewhere.
- docs/design/css-standard-and-exceptions.md: Add one row to §5 Exception index: StatisticsFilters | .statistics-filters__actions (검색 버튼 오른쪽 끝 배치) | req yyyyMMdd-name.
[Change file list as in requirement §2.]

Design doc implementation (search/filter):
Before changing form/filter CSS or component layout, read docs/design/search-field-definition-items.md (§1, §4) and docs/design/search-fields-by-screen.md (per-screen tables). Apply width, height, padding, gap from those docs.

CSS standard and exceptions (mandatory for this handoff):
Use standard values from frontend/src/styles/search-filter-standard.css (var(--sf-*) or .sf-* classes); do not duplicate those values in component CSS. For this screen-specific override, implement only in StatisticsFilters.css with a comment "/* Exception (req yyyyMMdd-name): 검색 버튼 오른쪽 끝 배치, 사용자 요청 */" and add a row to docs/design/css-standard-and-exceptions.md §5 Exception index.

§3 Frontend TCs: [from requirement doc, Scope=Frontend.]

Search/filter (user-context screens): Apply unified axes and scope=self per docs/analysis-search-consistency-by-screen.md.

Contract/spec: No API change; UI only.
```

**HANDOFF-CHECKLIST.md (Frontend) verification:**

| Item | Present in prompt? |
|------|--------------------|
| §1 summary | Yes |
| §2 Full Frontend subsection | Yes (solution + change file list) |
| §2.1 Security | N/A |
| Contract/spec | Yes (no API change stated) |
| §3 Frontend TCs | Yes |
| Cross-scope | N/A |
| Search/filter (user-context screens) | Yes |
| Design doc implementation (search/filter) | Yes |
| **CSS standard and exceptions** | **Yes — explicit instruction: use search-filter-standard.css, exception in component CSS only + comment + Exception index §5** |
| UX role (optional) | Omitted |
| Doc–code sync | N/A |

**Pass**: All Frontend checklist items are covered; the **new** "CSS standard and exceptions" item is present in the handoff.

### 2.3 QA handoff (main → QA)

**Task prompt the main agent would build:**

```
Verify implementation for requirement: 통계 검색 폼 검색 버튼 오른쪽 끝 배치 예외 적용.

§1 Summary: Screen-specific exception for statistics search form — search button at right end only.

§3 Full test case list:
- TC-01 (Frontend): Statistics filter shows 검색/초기화; 검색 button is at the right end of the actions row.
- TC-02 (Frontend): No regression on activity log search form layout.
[Other TCs from requirement doc.]

Build and restart: Confirm when done (or QA runs them). Requirement doc path: docs/requirements/yyyyMMdd-statistics-button-placement-exception.md. Update §5 after verification.
```

**HANDOFF-CHECKLIST.md (QA) verification:**

| Item | Present in prompt? |
|------|--------------------|
| §1 summary + §3 full TC list | Yes |
| Build/restart confirmation | Yes (or QA runs) |
| Requirement doc path for §5/§6 | Yes |

**Pass**: QA checklist items are covered.

---

## 3. Verification table (workflow 정상 수행 여부)

| Rule / Document | Check | Pass? |
|-----------------|-------|-------|
| `agent-collaboration.mdc` Step 1 gate | Main does not author §1·§2·§3 | Yes |
| `agent-collaboration.mdc` §3 gate | §3 exists before Step 4 | Yes (Requirements finalizes §3) |
| `REQUIREMENTS-AUTHORING-WORKFLOW.md` | Hybrid consultation, change target verification | Yes |
| `HANDOFF-CHECKLIST.md` Frontend — §1, §2, §3, Contract, Search/filter | All present | Yes |
| `HANDOFF-CHECKLIST.md` Frontend — **Design doc implementation (search/filter)** | Instruction to read definition-items + search-fields-by-screen | Yes |
| `HANDOFF-CHECKLIST.md` Frontend — **CSS standard and exceptions** | Instruction: standard CSS file, exception in component CSS + comment + Exception index §5 | Yes |
| `REQUIREMENTS-CHANGE-TARGET-CHECKLIST.md` §2.4 — Design doc references | search-fields-by-screen, definition-items | Yes |
| `REQUIREMENTS-CHANGE-TARGET-CHECKLIST.md` §2.4 — **CSS standard and exceptions** | §2 references css-standard-and-exceptions.md and search-filter-standard.css; impl note for Frontend | Yes (prompt builder would add when requirement touches CSS/exception) |
| `HANDOFF-CHECKLIST.md` QA | §1 + §3, build/restart, requirement doc path | Yes |
| Scope-specific excerpts (not full doc) | Handoff uses excerpts | Yes |

---

## 4. Report

- **Dry-run result**: **Pass.**  
  The handoff flow (Requirements → Frontend → QA) produces prompts that satisfy the checklist. The **CSS standard and exceptions** touchpoint is:
  - Reflected in **REQUIREMENTS-CHANGE-TARGET-CHECKLIST** §2.4 (new row).
  - Reflected in **HANDOFF-CHECKLIST** Frontend (new bullet).
  - Reflected in **search-filter-form-design.mdc** and **frontend.md** (implementation rule).
  - Correctly included in the **simulated Frontend handoff** when the virtual requirement involves search/filter CSS and an exception.

- **Workflow 정상 수행**: Requirements가 §2에 css-standard-and-exceptions 및 표준 CSS 파일을 참조하고, Implementation note에 표준 사용 + 예외 시 주석·Exception index를 넣으면, Main이 Frontend에 넘기는 핸드오프에 "CSS standard and exceptions" 지시가 포함됨. Frontend는 표준 CSS 사용 및 예외 시 컴포넌트 CSS + 주석 + Exception index §5 갱신으로 일관되게 수행 가능.

**References**: `.cursor/commands/dry-run-handoff.md`, `docs/workflow/HANDOFF-CHECKLIST.md`, `docs/workflow/REQUIREMENTS-CHANGE-TARGET-CHECKLIST.md` §2.4, `docs/design/css-standard-and-exceptions.md`, `docs/workflow/DRYRUN-implementation-phase-design-doc-handoff.md`.
