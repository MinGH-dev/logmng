# Requirement Document Template

**Language**: Author in **English first**. After **all verification is complete**, add **§ Final version (Korean)** (or create `yyyyMMdd-name-ko.md`) so the requirement is available in Korean. See `docs/workflow/DOCUMENT-LANGUAGE-POLICY.md`.

**Commit**: Every commit that closes this requirement **must reference this document** (e.g. `req yyyyMMdd-name` or `docs/requirements/yyyyMMdd-name.md`) so each commit version is traceable. See `.cursor/commands/commit-on-complete.md`.

---

## How to use

Copy this template to create `docs/requirements/yyyyMMdd-short-name.md`. Use lowercase English and hyphens for the file name.

**Date**: For `yyyyMMdd` and in-document dates (Date, Completed, §5 test run date, 작성일), use the **current year and date** from `.cursor/CURRENT-DATE-CONVENTION.md` so the correct year is used even when the conversation context is wrong.

**After verification**: Add the new doc to `docs/requirements/TOPIC-INDEX.md` under the matching topic (one line: `- doc-id | one-line §1 summary`). Run `./scripts/generate-requirements-index.sh` to check for docs not yet in the index.

---

```markdown
# yyyyMMdd - Short name

## 1. User requirement

### Requirement description
[Detailed description of the requirement]

### User scenario
1. [User action 1]
2. [User action 2]
3. [User action 3]
4. **Problem**: [Problem or improvement needed]

### Expected outcome
- [Expected outcome 1]
- [Expected outcome 2]
- [Expected outcome 3]

**Note**: Numeric and structural values (e.g. max-width, spacing, row layout) must be **sourced from** design docs; the requirement doc **references** them and may quote for traceability. If a value appears only here and not in a design doc, consider adding it to the design doc so the requirement stays reference-only. If aligning two search/filter UIs (e.g. activity log and statistics), consider: layout, group title placement, spacing, **form panel width/size**, and **user block field size** (부서, 사용자명, 사용자 ID same width/size on both screens per `docs/design/search-fields-by-screen.md` §3, §4). See `docs/design/forms-and-filters.md` § Search form panel width and `docs/workflow/REQUIREMENTS-CHANGE-TARGET-CHECKLIST.md` §2.4. When the requirement defines or aligns search/filter **fields**, §1 must explicitly reference both `docs/design/search-fields-by-screen.md` and `docs/design/search-field-definition-items.md` so implementers apply the same field schema. When **pattern §2.4** applies, §2 must include an **Implementation note for Frontend** with the full text from REQUIREMENTS-CHANGE-TARGET-CHECKLIST.md §2.4 row "Implementation note for Frontend" (read and apply from design docs including forms-and-filters.md; if any required standard is undefined or ambiguous, implementer must not infer or hardcode — must inform user, explain why needed, propose recommended standard draft, request feedback before implementation). The handoff builder will include this in the Frontend handoff.

## 2. Design

### 2.1 Security review (optional; when PII / decryption / access control)
When **Security** subagent has reviewed: summarize risks, acceptance criteria, and design recommendations here. Reference: `docs/workflow/CURSOR-SUBAGENTS-DESIGN.md`, `docs/workflow/WORKFLOW_CHECKLIST.md`.

- [ ] Security review performed (check if applicable)
- Risks: [e.g. decryption scope may expose new logs]
- Acceptance / recommendations: [e.g. limit to approved snapshot, audit log]

### Technical design

#### Problem analysis
1. [Problem 1]
2. [Problem 2]

#### Solution approach

Structure by scope so each implementing agent receives only its relevant section during handoff. Use scope headers (`Frontend:`, `Backend:`, `DB:`) consistently.

**Frontend:**
- [Change 1]
- [Change 2]
- When the feature is **configurable and also displayed**, list both **(a) configuration/setup UI** (e.g. permission group edit, settings) and **(b) user-facing screen(s)**. See `docs/workflow/REQUIREMENTS-CHANGE-TARGET-CHECKLIST.md`.

**Backend:**
- [Change 1]
- [Change 2]

**DB:**
- [None or change description]

### Affected scopes and change targets (verification)

**Before finalizing §2**, the Requirements author must verify that every affected scope is covered and that no touchpoint is missed. Use `docs/workflow/REQUIREMENTS-CHANGE-TARGET-CHECKLIST.md`.

| Scope | Affected? | §2 subsection and change file list complete? |
|-------|------------|-----------------------------------------------|
| Backend | [ ] Yes / [ ] No | [ ] |
| Frontend (config UI + view screen) | [ ] Yes / [ ] No | [ ] |
| DB | [ ] Yes / [ ] No | [ ] |
| Contract / Spec | [ ] Yes / [ ] No | [ ] |
| Cursor tools (skills, specs) | [ ] Yes / [ ] No | [ ] |

If the requirement matches a **domain pattern** (e.g. scope-supporting screen, permission group, API change), run the pattern checklist in REQUIREMENTS-CHANGE-TARGET-CHECKLIST.md §2 and confirm all touchpoints are in §2 and in the change file list below.

Where applicable, separate: **Design standard** (from design docs), **Product requirement** (explicitly agreed), **Not yet confirmed** (optional/TBD; implement only if product confirms). Use labels such as "Optional field constraints (implement only if product confirms)".

### Planned change file list (expected change targets)

**(Planned at authoring. Implementing agent (Step 4) confirms or amends this list when implementation is complete.)**

Use requirement tone in §2 and in this list: **must**, **verify**, **align**, **confirm**. Avoid implementation-complete phrasing (e.g. "No change", "already present", "confirmed by implementing agent") in the authored requirement; reserve those for §5 or post-implementation updates.

Structure by scope to enable scope-specific excerpt extraction for handoff (see `HANDOFF-CHECKLIST.md`).

#### Frontend
- `path/filename.js`
  - [Change description]

#### Backend
- `path/filename.java`
  - [Change description]

#### DB
- [None or change description]

## 3. Test approach

### Test case list (required)

Define test cases before unit/integration test execution. Update when the requirement or error fix changes.

**Domain-specific completeness**: If a relevant domain skill has a **§3 completeness checklist** (e.g. `api-permission-map` for permission/access-control requirements), apply that checklist before finalizing §3.

**Mandatory automated tests (Definition of Done)**: For every requirement, **all modified or added code** must be covered by automated tests. For each TC whose verification method is **Unit** or **Integration**, the implementing agent (Step 4 — Backend, Frontend, or DB) **must add or extend** test code (JUnit, Jest, or documented integration/curl) so that the TC is executed as part of `mvn test` / `npm test` or a documented integration procedure. TCs marked **Manual** or **Manual / browser** only are exempt. This ensures QA has runnable tests for every automated TC and avoids "no test code was written" gaps. See `docs/workflow/SUBAGENT-DELEGATION.md` §3 (Step 4).

**Scope tag**: Tag each TC with a **Scope** (`Backend`, `Frontend`, `DB`, `Integration`) so the main agent can extract scope-specific TCs for handoff. Include `Integration` TCs that imply backend behavior in Backend handoffs as well. See `HANDOFF-CHECKLIST.md`.

| ID | Scope | Type | Scenario (input / condition) | Expected result | Verification (unit / integration / manual) |
|----|-------|------|------------------------------|-----------------|--------------------------------------------|
| TC-01 | Backend | Normal | [Description] | [Expected] | Unit (mvn test) |
| TC-02 | Frontend | Normal | [Description] | [Expected] | Unit (npm test) |
| TC-03 | Integration | Exception | [Description] | [Expected] | Integration (curl / browser) |
| TC-04 | Backend | Edge | [Description] | [Expected] | [Method] |

### Test scenarios

#### Scenario 1: [Name]
1. [Step 1]
2. [Step 2]
3. [Verification]

#### Scenario 2: [Name]
1. [Step 1]
2. [Step 2]
3. [Verification]

### Test data
- [Test data description]
- When derivation rules or defaults apply, provide **executable SQL** (INSERT/UPDATE) so QA can set up test data without guessing.

### Test environment
- Frontend: `http://localhost:3001` (or per contract)
- Backend: `http://localhost:9200`
- Database: [DB type]

### 3.5 Browser automation verification (optional — for frontend-heavy requirements)

For requirements that change UI, layout, forms, tables, or a11y, add this section so QA can run TCs via Browser MCP.

- **Applicable TCs**: List TC IDs from the test case table that can be verified by browser automation (e.g. manual or manual-browser).
- **Procedure per TC**: Briefly describe steps (e.g. `browser_navigate` → login → menu click → `browser_snapshot` to confirm no "back to main" link).
- **Reference**: Example `docs/requirements/20260225-ux-standards-compliance-audit.md` §3.5. Policy: `docs/workflow/BROWSER-AUTOMATION-VERIFICATION-POLICY.md`.

## 4. Checklist

### Frontend verification
- [ ] API parameters validated
- [ ] UI behavior confirmed
- [ ] Error handling verified

### Backend verification
- [ ] API test cases written and run
- [ ] Logs checked
- [ ] Performance checked (if applicable)

### Integration
- [ ] End-to-end flow tested
- [ ] Edge cases tested

### Documentation
- [ ] Requirement doc completed
- [ ] Code comments added (if applicable)

## 5. Test results

### Test run date
- [Date and time]

### Test results

#### Frontend
[Pass / Fail]
- [Result description]

#### Backend
[Pass / Fail]
- [Result description]

**Commands:**

Provide **one executable command per TC** in §3 (login + request). Do not use "example pattern" for a subset; cover **every** TC so QA can copy-paste and run.

```bash
[Test commands — one per TC]
```

**Outcome:**
- [Item 1]
- [Item 2]

### Issues found and resolution

#### Issue 1: [Name]
**Cause**: [Cause description]

**Resolution**:
1. [Resolution 1]
2. [Resolution 2]

### Next steps
1. [Next step 1]
2. [Next step 2]

## 6. Error remedy result (cause and action) — for error/bug fix requirements only

Record root cause and actions under the **same requirement ID (this document)**. Do not create a separate file; keep traceability in this doc.  
Template: `docs/template/ERROR_FIX_RESULT_TEMPLATE.md`.  
Command: `/record-error-fix` can be used to record.

- **Requirement ID**: (this document filename, e.g. yyyyMMdd-short-name)
- **Root cause**: [Cause]
- **Actions taken**: [Summary of changes and configuration]
- **Result**: [Verification method and result, prevention]
- **Completed**: yyyy-MM-dd HH:mm

---

## 7. Final version (Korean) — add after all verification is complete

After QA has completed verification and before or with the final commit, add a **Korean summary** here (or create `docs/requirements/yyyyMMdd-short-name-ko.md`). See `docs/workflow/DOCUMENT-LANGUAGE-POLICY.md` §2.3.

### 요건 요약 (한글)
- **요건 설명**: [§1 요약]
- **기대 결과**: [§1 기대 결과 요약]
- **검증 결과**: [§5 요약, 통과/실패]

---

**Author**: [Name]
**Date**: yyyy-MM-dd
**Status**: [In progress / Done / On hold]
```
