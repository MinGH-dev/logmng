# 20260414 - Image log datastring/headerstring independent matching fix

## 1. User requirement

### Requirement description

In Java FW Image Log search, entering `LOCAL` in `datastring` currently returns rows where `datastring` does not contain `LOCAL`. Root cause is confirmed: in backend `LogDbService.searchJavaFwImglog`, `datastring` input is handled as a unified field term and also matches `headerstring` (and decryption match path), causing cross-field leakage and user confusion.

This requirement defines strict field-independent matching rules for `datastring` and `headerstring`, clarifies `keywords` behavior, and adds response match-flag key consistency checks so users can trust which field actually matched.

### User scenario

1. User opens Java FW Image Log search and enters a term in `datastring` only (for example `LOCAL`).
2. User executes search expecting only rows where data area matches.
3. User sees rows where only header area matches and assumes search is broken.
4. **Problem**: `datastring` and `headerstring` filtering are not independent in backend matching logic.

### Expected outcome

- `datastring` input must match only `datastring` and `decryptedDatastring` scope.
- `headerstring` input must match only `headerstring` and `decryptedHeaderstring` scope.
- `keywords` behavior must be explicitly defined and remain stable across releases.
- API response must expose consistent match-flag keys so UI can explain match reason without ambiguity.

## 2. Design

### 2.1 Security review (optional; when PII / decryption / access control)

- [ ] Security review performed (recommended because decrypted field matching is involved)
- Decryption-based matching must not broaden visibility beyond existing authorization boundaries.
- Diagnostic and operational logs must not print raw decrypted values.
- TODO: Confirm whether any response-level match flags can expose sensitive decrypted content inference and whether masking rules are needed.

### Technical design

#### Codebase summary

- Backend image-log filtering path uses `buildJavaFwImglogTextFilterTerms` and `javaFwImglogTermMatchesForFilter`.
- Existing behavior treats `datastring` input as unified text term, so matching can happen via header/decrypted-header routes.
- Existing test `searchJavaFwImglog_datastringLocal_matchesSeedIm0001ViaHeaderPlaintext` currently guarantees this cross-field behavior.

#### Problem analysis

1. Field intent is violated: `datastring` input can match header-related fields.
2. Existing automated test encodes the unintended behavior and blocks desired fix.
3. Match-flag semantics in API response are not formally constrained for field-independent interpretation.

#### Diagnostic phase (mandatory for error/bug fix only)

Root cause is already confirmed from analysis and existing test behavior:

- `buildJavaFwImglogTextFilterTerms` + `javaFwImglogTermMatchesForFilter` unify term matching across text domains.
- Regression anchor test verifies `datastring` search can succeed via header plaintext path.

Implementation must still keep diagnostic logging at debug-safe level if additional verification logs are added.

#### Solution approach

**Backend:**
- Redefine matching contract:
  - `datastring` condition = (`datastring` OR `decryptedDatastring`) only.
  - `headerstring` condition = (`headerstring` OR `decryptedHeaderstring`) only.
- Remove cross-field fallback from `datastring` to header path and from `headerstring` to data path.
- Reclassify or replace tests that currently enforce cross-field leakage.
- Define `keywords` semantics explicitly:
  - Preserve existing cross-field OR behavior only if product requires broad keyword search.
  - If preserved, document it as independent from strict field-specific filters and combined by explicit boolean rules.
  - TODO: Final product decision required: keep cross-field OR for `keywords` as-is or split by field-specific keyword modes.
- Add/align response match flags with deterministic key naming.
  - TODO: Confirm canonical response keys (for example `matchedDatastring`, `matchedHeaderstring`, `matchedKeyword`) against current API contract and frontend usage.

**Frontend:**
- No functional scope change requested in this requirement, except compatibility verification that UI interpretation of backend match flags remains correct.
- If backend response flag keys change, UI mapping must be updated consistently.

**DB:**
- No schema change required.

#### Existing behavior vs. target behavior

| Input field | Existing behavior | Target behavior |
|-------------|-------------------|-----------------|
| `datastring` | Can match data/header/decrypted paths via unified term | Must match only data + decrypted-data paths |
| `headerstring` | Can be evaluated through unified term routes | Must match only header + decrypted-header paths |
| `keywords` | Broad cross-field OR behavior (current implicit behavior) | Behavior must be explicitly documented and tested |

#### Compatibility impact

- Search result sets may shrink for users who unknowingly relied on cross-field leakage from `datastring` or `headerstring`.
- Existing tests asserting cross-field matching must be updated to new contract.
- API consumers using match flags may require mapping alignment if key names are normalized.

#### Risks

1. Hidden dependency on old cross-field behavior in downstream analytics or UI messaging.
2. Regression in decryption-aware matching if field routing is tightened incorrectly.
3. Inconsistent interpretation of `keywords` until final rule is fixed and documented.

#### Rollback plan

1. Keep fix isolated to image-log text filter composition and matcher logic.
2. If severe regression occurs, revert to previous matcher strategy commit for `searchJavaFwImglog` path only.
3. Restore previous tests temporarily and reopen requirement with narrowed rollout guard.

### Affected scopes and change targets (verification)

| Scope | Affected? | §2 subsection and change file list complete? |
|-------|------------|-----------------------------------------------|
| Backend | [x] Yes | [x] |
| Frontend (config UI + view screen) | [ ] Yes / [x] No (compatibility check only) | [x] |
| DB | [ ] Yes / [x] No | [x] |
| Contract / Spec | [x] Yes (response flag key consistency check) | [x] |
| Cursor tools (skills, specs) | [ ] Yes / [x] No | [x] |

### Planned change file list (expected change targets)

#### Frontend
- `frontend/src/components/LogGrid.js`
  - Verify/align response match-flag key interpretation if backend keys are normalized.
- TODO: Confirm exact frontend file(s) consuming image-log match flags.

#### Backend
- `backend/src/main/java/com/logmng/service/LogDbService.java`
  - Enforce field-independent matching logic for `datastring` and `headerstring`.
- `backend/src/test/java/com/logmng/service/LogDbServiceTest.java`
  - Replace cross-field leakage expectation tests with field-independent expectations.
- `backend/src/test/java/com/logmng/service/LogDbServiceSearchJavaFwImglogTest.java`
  - TODO: Confirm actual test file name in current branch and include equivalent coverage updates.

#### DB
- None.

#### Contract / Spec / Docs
- `docs/contract.md`
  - Clarify field-specific matching contract and `keywords` boolean semantics.
- `docs/api-definition.md`
  - Verify and document response match-flag key set and meanings.
- TODO: If no contract update is required by current governance, record rationale in §5 during implementation phase.

## 3. Test approach

### Test case list (required)

| ID | Scope | Type | Scenario (input / condition) | Expected result | Verification (unit / integration / manual) |
|----|-------|------|------------------------------|-----------------|--------------------------------------------|
| TC-01 | Backend | Normal | `datastring=LOCAL`, `headerstring` empty, seed rows where only header has `LOCAL` | Rows matched only by header must be excluded | Unit (mvn test) |
| TC-02 | Backend | Normal | `headerstring=LOCAL`, `datastring` empty, seed rows where only data has `LOCAL` | Rows matched only by data must be excluded | Unit (mvn test) |
| TC-03 | Backend | Normal | `datastring=LOCAL`, row has `LOCAL` only in decrypted data field | Row included (data-scope decryption match allowed) | Unit (mvn test) |
| TC-04 | Backend | Normal | `headerstring=LOCAL`, row has `LOCAL` only in decrypted header field | Row included (header-scope decryption match allowed) | Unit (mvn test) |
| TC-05 | Backend | Regression | Existing test `searchJavaFwImglog_datastringLocal_matchesSeedIm0001ViaHeaderPlaintext` | Test removed/rewritten to reject header-only match for datastring search | Unit (mvn test) |
| TC-06 | Backend | Edge | `datastring` and `headerstring` both provided with different terms | Boolean combination follows defined rule without cross-field leakage | Unit (mvn test) |
| TC-07 | Integration | Regression | API search request with only `datastring` term that exists in header-only row | API response excludes header-only row; count reflects filtered set | Integration (curl) |
| TC-08 | Integration | Regression | API search request with only `headerstring` term that exists in data-only row | API response excludes data-only row; count reflects filtered set | Integration (curl) |
| TC-09 | Backend | Boundary | Empty string / null for `datastring` and `headerstring` | No false positives; no exception; baseline behavior retained | Unit (mvn test) |
| TC-10 | Integration | Regression | `keywords` with mixed fields (one keyword in data, one in header) | Behavior matches explicitly documented `keywords` rule | Integration (curl) |
| TC-11 | Frontend | Regression | UI reads API response match flags for image-log rows | Match reason labels/indicators use correct keys consistently | Manual / browser |

### Test scenarios

#### Scenario 1: Datastring isolation
1. Send search with `datastring` term present only in header for one candidate row.
2. Execute backend search path.
3. Verify row is excluded and no header-route fallback occurs.

#### Scenario 2: Headerstring isolation
1. Send search with `headerstring` term present only in data for one candidate row.
2. Execute backend search path.
3. Verify row is excluded and no data-route fallback occurs.

#### Scenario 3: Keywords rule confirmation
1. Prepare rows where keywords appear across data/header/decrypted variants.
2. Execute keywords search with multiple terms.
3. Verify result matches documented keywords boolean semantics.
4. TODO: Finalize expected boolean truth table after product decision.

#### Scenario 4: Match-flag consistency
1. Execute representative searches for data-only match, header-only match, keyword-only match.
2. Inspect response match flags.
3. Verify keys and meanings are stable and documented.
4. TODO: Confirm canonical key names with frontend and contract owners.

### Test data

- Seed row A: term in `datastring` only.
- Seed row B: term in `headerstring` only.
- Seed row C: term in decrypted data only.
- Seed row D: term in decrypted header only.
- Seed row E: mixed keyword distribution for cross-field keyword scenario.

### Test environment

- Frontend: `http://localhost:3001`
- Backend: `http://localhost:9200`
- Database: PostgreSQL

## 4. Checklist

### Frontend verification
- [ ] Response match-flag key mapping reviewed
- [ ] UI interpretation verified for no ambiguous match explanation

### Backend verification
- [ ] Field-independent matcher implemented for `datastring` and `headerstring`
- [ ] Legacy cross-field expectation tests updated
- [ ] Decryption-path matching remains field-scoped

### Integration
- [ ] API regression tests for data/header isolation passed
- [ ] Keywords behavior test passed against documented rule

### Documentation
- [x] Requirement doc completed
- [ ] Contract/API docs updated or TODO resolved with rationale

## 5. Test results

### Test run date
- Not run yet (requirement-authoring phase)

### Test results
#### Frontend
- Pending

#### Backend
- Pending

### Issues found and resolution
- None (not executed yet)

### Next steps
1. Finalize TODO decisions for `keywords` semantics and response match-flag key naming.
2. Implement backend matcher change and test updates.
3. Run TC-01 to TC-11 and record results.

## 6. Error remedy result (cause and action) — for error/bug fix requirements only

- **Requirement ID**: 20260414-image-log-datastring-headerstring-independent-matching-fix
- **Root cause**: `datastring` was processed as unified term and matched header/decryption routes, violating field independence.
- **Actions taken**: To be completed after implementation.
- **Result**: To be completed after verification.
- **Completed**: TODO:

---

**Author**: Requirements subagent
**Date**: 2026-04-14
**Status**: In progress
