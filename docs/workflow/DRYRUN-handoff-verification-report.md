# Dry-run handoff flow verification — report

**Date**: 2025-03-09  
**Procedure**: `.cursor/commands/dry-run-handoff.md`  
**Purpose**: Verify workflow rules produce correct handoff prompts (no code changes).

---

## 1. Virtual requirement

**Title**: Add error code `INVALID_DATE_RANGE` for log search when `startDate > endDate`, and show user message in frontend.

- **Scope**: Backend (validation + exception), Frontend (error message map), Contract (api-definition §11).
- **Why**: Exercises Backend handoff, §2.1 not required (no PII/decrypt), Contract/doc–code sync applicable (new error code).

---

## 2. Simulated handoff chain

### 2.a Requirements handoff (main → Requirements)

**Task invocation the main agent would send:**

```text
subagent_type: Requirements
prompt: |
  Author a requirement document per docs/workflow/REQUIREMENTS-AUTHORING-WORKFLOW.md:
  - User request: "When log search is called with startDate > endDate, return a clear error code
    (e.g. INVALID_DATE_RANGE) and show a user-friendly message in the frontend."
  - Follow hybrid consultation: (1) Read relevant skills (error-codes-domain, log-search-domain),
    (2) Run codebase investigation for LogDbSearchRequest, LogDbService, and frontend errorMessage.js
    and log search UI, (3) If needed, invoke Security only if you determine PII/access is involved;
    Contract or Consistency for error code naming.
  - Output: docs/requirements/yyyyMMdd-invalid-date-range-error-code.md with §1 (user scenario,
    expected outcome), §2 (codebase summary, solution, change file list for Backend and Frontend),
    §3 (test cases with Scope tag per TC). Do not author §1·§2 from judgment alone; use skills +
    codebase investigation + selective experts. Run change target verification before finalizing §2.
readonly: false
```

**Checks:**

- Main does **not** author §1·§2·§3; prompt instructs Requirements to author per REQUIREMENTS-AUTHORING-WORKFLOW. **Pass.**
- Prompt references REQUIREMENTS-AUTHORING-WORKFLOW (hybrid: skills-first → codebase → selective experts). **Pass.**

---

### 2.b Backend handoff (main → Backend)

**Full Task prompt the main agent would build (excerpt):**

```text
subagent_type: Backend
prompt: |
  Implement the Backend scope of requirement: docs/requirements/yyyyMMdd-invalid-date-range-error-code.md

  §1 Summary:
  When log search is called with startDate > endDate, backend must return 400 with code
  INVALID_DATE_RANGE so the frontend can show a consistent message.

  §2 Backend subsection:
  - In LogDbSearchRequest validation (or LogDbService), add date range check: if startDate > endDate,
    throw CustomException with code "INVALID_DATE_RANGE", HTTP 400.
  - Change file list (tentative): LogDbSearchRequest.java or LogDbService.java, exception handling;
    add unit test in LogDbServiceTest (or equivalent).

  §3 Test cases that involve Backend (with Scope tag):
  - [Backend] TC1: POST /api/logs/db-refactored/search with startDate > endDate returns 400 and
    response body code "INVALID_DATE_RANGE".
  - [Backend] TC2: Valid date range still returns 200 and search result.

  Contract/spec:
  - Error codes: docs/api-definition.md §11. Add INVALID_DATE_RANGE to the table; backend must
    use this exact string.
  - API: POST /api/logs/db-refactored/search — request body has startDate, endDate (docs/api-definition.md §5).

  Doc–code sync:
  - This change adds a new error code. Update docs/api-definition.md §11 (add INVALID_DATE_RANGE
    and user message) in this same work. See docs/workflow/DOC-CODE-SYNC.md.

  Cross-scope: Frontend will read response.code === "INVALID_DATE_RANGE" and show the message
  from errorMessage.js; ensure backend returns exactly that code.
readonly: false
```

**HANDOFF-CHECKLIST.md Backend — item check:**

| Item | Present in prompt | Pass? |
|------|--------------------|-------|
| §1 summary | Yes (one paragraph: startDate > endDate → 400, INVALID_DATE_RANGE) | Yes |
| §2 Backend subsection | Yes (validation location, change file list) | Yes |
| §2.1 Security | N/A (no PII/decrypt/access) | Yes |
| Contract/spec | Yes (api-definition §11, §5) | Yes |
| §3 Backend-scope TCs | Yes (TC1, TC2 with Scope tag) | Yes |
| Cross-scope | Yes (Frontend expects code, message) | Yes |
| Doc–code sync | Yes (add INVALID_DATE_RANGE to api-definition §11 in same work; DOC-CODE-SYNC ref) | Yes |

**Scope-specific excerpt:** Prompt uses Backend subsection and Backend TCs only, not full requirement doc. **Pass.**

---

### 2.c QA handoff (main → QA)

**Full Task prompt the main agent would build:**

```text
subagent_type: QA
prompt: |
  Run verification for requirement: docs/requirements/yyyyMMdd-invalid-date-range-error-code.md

  §1 Summary:
  When log search is called with startDate > endDate, backend returns 400 with code
  INVALID_DATE_RANGE; frontend shows a user-friendly message.

  §3 Full test case list (all TCs for this requirement):
  - [Backend] TC1: POST /api/logs/db-refactored/search with startDate > endDate returns 400,
    response body code "INVALID_DATE_RANGE".
  - [Backend] TC2: Valid date range still returns 200 and search result.
  - [Frontend] TC3: When API returns INVALID_DATE_RANGE, UI shows the message from errorMessage.js
    (no generic "Request failed").
  - [Integration] TC4: E2E: set start > end in log search form, submit, see error message.

  Build and restart: Confirm backend and frontend have been built and restarted (or QA runs
  ./scripts/dev-services.sh as needed).

  Requirement doc path for §5/§6 update: docs/requirements/yyyyMMdd-invalid-date-range-error-code.md

  Frontend TCs (TC3, TC4): If browser automation is used, see BROWSER-AUTOMATION-VERIFICATION-POLICY.md.
readonly: false
```

**HANDOFF-CHECKLIST.md QA — item check:**

| Item | Present in prompt | Pass? |
|------|--------------------|-------|
| §1 summary + §3 full TC list | Yes | Yes |
| Build/restart confirmation | Yes (confirm done or QA runs dev-services) | Yes |
| Requirement doc path for §5/§6 | Yes (path given) | Yes |
| Browser automation (if frontend) | Yes (reference to policy for TC3/TC4) | Yes |

---

## 3. Verification table

| Rule / Document | Check | Pass? |
|-----------------|-------|-------|
| `agent-collaboration.mdc` Step 1 gate | Main does not author §1·§2·§3; Requirements is instructed to author | Yes |
| `agent-collaboration.mdc` §3 gate | §3 exists before Step 4; prompt to Backend includes §3 TCs | Yes |
| `REQUIREMENTS-AUTHORING-WORKFLOW.md` | Requirements prompt asks for hybrid consultation (skills + codebase + selective experts) | Yes |
| `HANDOFF-CHECKLIST.md` Backend | All 7 items present (§1, §2, §2.1 if applicable, contract, §3 TCs, cross-scope, doc–code sync) | Yes |
| `HANDOFF-CHECKLIST.md` QA | All 4 items present (§1+§3, build/restart, doc path, browser automation if applicable) | Yes |
| `REQUIREMENT_TEMPLATE.md` §3 Scope tag | TCs in Backend handoff have [Backend] tag | Yes |
| `CONTEXT-QUALITY-AND-ORCHESTRATION-MITIGATION.md` §4.1 | Scope-specific excerpts (Backend gets Backend subsection + Backend TCs only) | Yes |
| `DOC-CODE-SYNC.md` | Backend handoff includes doc–code sync instruction (update api-definition §11 in same work) | Yes |

---

## 4. Report summary

- **Result**: All checks **passed**.
- **Findings**:
  - Requirements handoff: Correctly defers §1·§2·§3 to Requirements and references REQUIREMENTS-AUTHORING-WORKFLOW.
  - Backend handoff: All HANDOFF-CHECKLIST Backend items are present, including the new **Doc–code sync** item (update api-definition §11, reference DOC-CODE-SYNC.md).
  - QA handoff: §1+§3, build/restart, requirement doc path, and browser automation note are present.
- **No code changes** were made; only prompts were generated and checked against the checklist.

---

**Next run**: After any change to `HANDOFF-CHECKLIST.md`, `DOC-CODE-SYNC.md`, or handoff-related rules, re-run this dry-run to ensure new items remain included in the generated prompts.
