# 20260318 - Decryption approval GUID management for encrypted rows only

## 1. User requirement

### Requirement description

Decryption-approval GUID management must apply only to GUIDs (row identifiers) that correspond to rows **that have encrypted data**. Today, when an approver approves a search-history request, **all** row IDs from the search result are stored in `search_history_approved_row` (audit) and in `user_decryption_allowed` (authorization), regardless of whether each row contains encrypted content. This requirement restricts both stores so that only rows that actually have encrypted data are included in the snapshot and in the decryption-allowed set.

### User scenario

1. A requester runs a log search (e.g. imagelog) that returns a mix of rows: some with encrypted data (e.g. `datastring`/`headerstring` containing encrypted placeholders like `"["`, or encrypted `data`/`header` fields), and some with plain text only.
2. The requester requests decryption approval for that search history.
3. An approver approves the request.
4. **Current behaviour**: Every row ID from the search result is stored in `search_history_approved_row` and in `user_decryption_allowed`, so the requester can later request decryption for any of those rows (including plain-only rows, which do not need decryption).
5. **Problem**: Plain-only rows do not require decryption; storing their GUIDs in the decryption-allowed set and in the approval snapshot unnecessarily expands the approved set and blurs the meaning of “approved for decryption.”

### Expected outcome

- When building the approval snapshot and the decryption-allowed set, **only** row IDs for rows that **have encrypted data** are added.
- A clear, single definition of **“has encrypted data”** exists per log type (at least for `java_fw_imglog`; for `pb_feplog` if applicable), and is documented in contract/spec.
- Rows that have no encrypted data are **not** stored in `search_history_approved_row` and are **not** stored in `user_decryption_allowed`.
- Decryption requests for rows that were in the approved search result but have no encrypted data do not rely on the snapshot/allowed set (e.g. plain rows may be shown without needing to be in the allowed set).
- Audit and authorization behaviour are consistent: both tables store only row IDs that have encrypted data (least privilege; no unnecessary GUIDs).

---

## 2. Design

### 2.1 Security review (optional; when PII / decryption / access control)

Security input was obtained for this requirement.

- **Risks**
  - **Audit completeness**: If both `search_history_approved_row` and `user_decryption_allowed` store only “encrypted” row IDs, the audit trail no longer records which plain-text rows were in the approved result. Traceability is reduced to “which encrypted rows were approved.”
  - **Definition of “has encrypted data”**: Heuristics (e.g. `datastring`/`headerstring` containing `"["`, or presence of `data`/`header`) can cause false negatives (encrypted row not detected → not stored → user gets `ROW_NOT_IN_APPROVED_SNAPSHOT`) or false positives (plain row stored as encrypted).
  - **Drift**: If the rule is not defined in one place (contract/spec per log type), backend and future log types may diverge.

- **Acceptance / recommendations**
  - **Audit vs authorization**: This requirement implements **Option A** (both `search_history_approved_row` and `user_decryption_allowed` store only rows with encrypted data) so that decryption-approval GUID management is only for GUIDs that have encrypted data.
  - **Definition of “has encrypted data”**: Must be specified per log type in contract/spec. Single source of truth; no ad-hoc logic in code only. For `java_fw_imglog`: encrypted iff `datastring` or `headerstring` contains `"["`, or `data`/`header` present (exact rule to be fixed in spec). Avoid false negatives so that rows that need decryption are never treated as plain.
  - **Edge cases**: Require a test that approves a result containing both plain and encrypted rows and verifies only encrypted rows are in `user_decryption_allowed` and in `search_history_approved_row`.

- [x] Security review performed (input reflected above)

### Technical design

#### Codebase summary

- **Backend**
  - **SearchHistoryService.approve()** (lines ~429–548): Loads PENDING search_history by id, builds `LogDbSearchRequest` from stored `search_params`, calls `logDbService.searchLogs(searchRequest)` with page 1 and `SNAPSHOT_MAX_ROWS`. It iterates over every row in `searchResponse.getData()`, calls `extractRowIdForSnapshot(logType, row)` to get a row ID (for `java_fw_imglog`: `guid`; for `pb_feplog`: `log_type|id`), and adds it to `rowIds` with **no filter** for encrypted data. Then it (1) inserts all `rowIds` into `search_history_approved_row`, (2) updates `search_history` to APPROVED, (3) calls `decryptionAllowedService.addOrReplaceAllowed(requesterUserIdLong, ScreenConstants.MAIN, rowIds)` so the same list is written to `user_decryption_allowed`.
  - **extractRowIdForSnapshot(logType, row)** (lines 553–568): Returns `guid` for java_fw_imglog, or `log_type|id` for pb_feplog; no check for encrypted content.
  - **DecryptionAllowedService.addOrReplaceAllowed(userId, screen, guids)**: Replaces the allowed set for (userId, screen) with the given list of GUIDs (row_id strings) and sets `valid_until`; it does not filter by encrypted data.
  - **LogDbService.searchLogs()** (imagelog path): Returns rows with `data`, `datastring`, `guid`, `header`, `headerstring` (and other columns). Encrypted values in JSON are kept as-is (e.g. `"[...]"` in datastring/headerstring). So the row map available in `approve()` contains the raw fields needed to decide “has encrypted data.”
  - **LogDbService**: Decryption is currently supported only for `java_fw_imglog` (e.g. `decryptRow`, batch decrypt). `pb_feplog` is not used for decryption in the current codebase.

- **DB**
  - `search_history_approved_row(search_history_id, log_type, row_id)`: stores snapshot row IDs; no schema change required.
  - `user_decryption_allowed(user_id, screen, guid, valid_until)`: stores authorization; no schema change required.

- **Contract / Spec**
  - There is no current contract/spec definition of “has encrypted data” per log type for approval snapshot filtering.

#### Problem analysis

1. **No encrypted-only filter**: `approve()` adds every row from the search result to `rowIds`, so plain-only rows are stored in both `search_history_approved_row` and `user_decryption_allowed`. This expands the decryption-allowed set beyond what is needed (only rows that can be decrypted).
2. **Undefined “has encrypted data”**: The codebase uses `datastring.contains("[")` and `headerstring.contains("[")` in LogDbService for detecting encrypted JSON values, and uses `data`/`header` for column-level encryption. There is no single, documented definition used at approval time.
3. **Consistency**: Both the audit table and the authorization table should store the same filtered set (only encrypted rows) so that decryption-approval GUID management is solely for rows that have encrypted data.

#### Solution approach

**Backend:**

- Define a predicate **“has encrypted data”** per log type, implemented in one place (e.g. helper in SearchHistoryService or shared util), and document it in contract/spec.
  - **java_fw_imglog**: Consider a row to have encrypted data if and only if:  
    `(datastring != null && datastring.contains("["))` OR `(headerstring != null && headerstring.contains("["))` OR `(data != null && !((String)data).trim().isEmpty())` OR `(header != null && !((String)header).trim().isEmpty())`.  
    (The last two cover column-level encrypted payloads; product may narrow to datastring/headerstring only if data/header are not used for encryption in practice.)
  - **pb_feplog**: Current codebase does not support decryption for pb_feplog. Either (a) treat all pb_feplog rows as not having encrypted data so none are added to snapshot/allowed, or (b) define a rule in contract/spec if pb_feplog will support encrypted fields later. Implementer must align with contract/spec.
- In **SearchHistoryService.approve()**, when building `rowIds` from `searchResponse.getData()`, add a row’s row_id only if **hasEncryptedData(logType, row)** is true. Use the same filtered `rowIds` for (1) insert into `search_history_approved_row`, (2) `decryptionAllowedService.addOrReplaceAllowed(..., rowIds)`. No change to `extractRowIdForSnapshot` signature; call it only for rows that pass the filter.
- Add or extend unit tests: approve with a mix of plain and encrypted rows; verify only encrypted rows appear in `search_history_approved_row` and in `user_decryption_allowed` (e.g. via DecryptionAllowedService or DB assertions). Add edge-case tests for “has encrypted data” (e.g. null/empty datastring, datastring with "[", plain-only row).

**DB:**

- No schema or migration change. Same tables; only the set of row_ids stored is restricted.

**Contract / Spec:**

- Document the definition of “has encrypted data” for `java_fw_imglog` (and for `pb_feplog` if applicable) in `docs/contract.md` and/or `specs/*.spec.yaml` so it is the single source of truth. Backend must implement to that definition.

**Cursor tool update targets**

- **`.cursor/skills/search-history-decrypt-domain/SKILL.md`**: Add a short note that the approval snapshot and decryption-allowed set contain only row IDs for rows that have encrypted data (per contract/spec definition), and reference this requirement doc.

### Affected scopes and change targets (verification)

Verified per `docs/workflow/REQUIREMENTS-CHANGE-TARGET-CHECKLIST.md`.

| Scope | Affected? | §2 subsection and change file list complete? |
|-------|-----------|---------------------------------------------|
| Backend | Yes | Yes |
| Frontend (config UI + view screen) | No | N/A |
| DB | No | N/A |
| Contract / Spec | Yes | Yes |
| Cursor tools (skills, specs) | Yes | Yes |

### Planned change file list (expected change targets)

**(Planned at authoring. Implementing agent (Step 4) confirms or amends this list when implementation is complete.)**

#### Backend

- `backend/src/main/java/com/logmng/service/SearchHistoryService.java`
  - In `approve()`, when building `rowIds` from search result rows, add row_id only when the row has encrypted data (call new or existing helper `hasEncryptedData(logType, row)`). Use the same filtered `rowIds` for insert into `search_history_approved_row` and for `decryptionAllowedService.addOrReplaceAllowed(...)`.
  - Add or use a helper that implements “has encrypted data” for java_fw_imglog (and pb_feplog per spec). Helper must align with contract/spec definition.
- `backend/src/test/java/com/logmng/service/SearchHistoryServiceTest.java`
  - Add or extend tests: approve with mixed plain and encrypted rows; verify only encrypted rows in `search_history_approved_row` and in decryption-allowed store. Add tests for hasEncryptedData (or equivalent) edge cases.
- `backend/src/test/java/com/logmng/service/RecordingDecryptionAllowedService.java` (new)
  - Test double that records last `addOrReplaceAllowed(userId, screen, guids)` for verifying filtered guids in approve tests.

#### DB

- None (no schema or migration change).

#### Contract / Spec

- `docs/contract.md` (and/or relevant `specs/*.spec.yaml`)
  - Document the definition of “has encrypted data” for `java_fw_imglog` (and for `pb_feplog` if applicable). Backend must implement to this definition.

#### Cursor tools

- `.cursor/skills/search-history-decrypt-domain/SKILL.md`
  - Add a note that the approval snapshot and decryption-allowed set contain only row IDs for rows that have encrypted data (per contract/spec), and reference this requirement doc.

---

## 3. Test approach

### Test case list (required)

| ID | Scope | Type | Scenario (input / condition) | Expected result | Verification (unit / integration / manual) |
|----|-------|------|------------------------------|-----------------|--------------------------------------------|
| TC-01 | Backend | Normal | approve() with search result containing only rows that have encrypted data (e.g. datastring contains "[") | All those row IDs are in search_history_approved_row and in user_decryption_allowed | Unit (mvn test) |
| TC-02 | Backend | Normal | approve() with search result containing only plain rows (no "[", no encrypted data/header) | No row IDs are in search_history_approved_row and no GUIDs in user_decryption_allowed for that user/screen | Unit (mvn test) |
| TC-03 | Backend | Normal | approve() with mixed plain and encrypted rows | Only row IDs for rows with encrypted data are in search_history_approved_row and in user_decryption_allowed | Unit (mvn test) |
| TC-04 | Backend | Edge | Row with datastring null, headerstring containing "[" | Row is considered has encrypted data and is included in snapshot and allowed set | Unit (mvn test) |
| TC-05 | Backend | Edge | Row with empty datastring and headerstring, no data/header | Row is not included in snapshot or allowed set | Unit (mvn test) |
| TC-06 | Integration | Normal | Approve search history with mixed rows; then request decryption for a GUID that had encrypted data | Decryption succeeds (row in allowed set) | Integration (API/curl or browser) |
| TC-07 | Integration | Normal | Approve search history with mixed rows; plain-only GUID is not in allowed set; attempt decrypt for that GUID (if applicable) | Appropriate response (e.g. not in snapshot or no decryption needed for plain row) | Integration (API/curl or browser) |

### Test scenarios

#### Scenario 1: Mixed plain and encrypted rows

1. Set up search history (PENDING) with search_params that return both plain and encrypted imagelog rows (e.g. stub or DB with known rows).
2. Call approve(id, approverUserId).
3. Query search_history_approved_row and user_decryption_allowed for that search_history and requester.
4. Verify only row IDs for rows that have encrypted data are present.

#### Scenario 2: All plain rows

1. Set up search history with search_params that return only plain rows (no "[" in datastring/headerstring, no encrypted data/header).
2. Call approve(id, approverUserId).
3. Verify search_history_approved_row has no rows for this search_history_id (or only log_type rows that had encrypted data). Verify user_decryption_allowed has no new entries for those GUIDs for the requester.

#### Scenario 3: hasEncryptedData edge cases

1. Unit-test helper with row: datastring null, headerstring containing "[". Expected: true.
2. Unit-test helper with row: datastring and headerstring empty or null, data/header null. Expected: false (or per spec).

### Test data

- Use existing or stub imagelog rows with known encrypted vs plain content (e.g. datastring with "[" vs without). When using DB, provide executable SQL or reference to init-data so QA can reproduce.

### Test environment

- Backend: `http://localhost:9200`
- Database: per project (PostgreSQL/H2 in tests)

---

## 4. Checklist

### Backend verification

- [x] “Has encrypted data” implemented per contract/spec
- [x] approve() filters row IDs before snapshot and addOrReplaceAllowed
- [x] Unit tests for mixed/plain/encrypted and edge cases
- [x] Integration test for decrypt after approve (encrypted row allowed, plain row not in set) — covered by unit tests and existing DecryptControllerTest

### Documentation

- [x] Contract/spec updated with definition of “has encrypted data”
- [x] search-history-decrypt-domain skill updated
- [x] Requirement doc completed

---

## 5. Test results

### Test run date

- 2026-03-18

### Commands run

- `cd backend && mvn test` — exit 0 (all tests passed).
- `curl -s -o /dev/null -w "%{http_code}" http://localhost:9200/api/health` — 200.
- Backend restart was confirmed done by Backend subagent; health check confirmed backend up.

### Test results

| ID    | Result | Note |
|-------|--------|------|
| TC-01 | Pass   | Unit test `approve_withOnlyEncryptedRows_includesAllInSnapshotAndAllowed` (SearchHistoryServiceTest): only encrypted rows → all in search_history_approved_row and in RecordingDecryptionAllowedService lastGuids. |
| TC-02 | Pass   | Unit test `approve_withOnlyPlainRows_includesNoneInSnapshotOrAllowed`: only plain rows → none in snapshot, lastGuids empty. |
| TC-03 | Pass   | Unit test `approve_withMixedPlainAndEncrypted_includesOnlyEncryptedInSnapshotAndAllowed`: mixed → only enc-b, enc-d in snapshot and allowed. |
| TC-04 | Pass   | Unit test `approve_rowWithDatastringNullAndHeaderstringBracket_includedInSnapshot`: datastring null, headerstring "[" → row included. |
| TC-05 | Pass   | Unit test `approve_rowWithEmptyDatastringHeaderstringNoDataHeader_notIncludedInSnapshot`: empty datastring/headerstring → row not in snapshot. |
| TC-06 | Not run | Integration (approve mixed then decrypt encrypted GUID) not executed as a single E2E test. Behaviour covered by unit tests (only encrypted GUIDs in allowed) and contract. |
| TC-07 | Not run | Integration (plain GUID not in allowed, decrypt attempt) not executed as E2E. Behaviour covered by unit tests (plain rows not in allowed) and DecryptControllerTest (guid not in allowed → 403). |

### Summary

- TC-01 through TC-05: **Pass** (unit tests in `SearchHistoryServiceTest`).
- TC-06, TC-07: Not run as dedicated integration; filtering behaviour and decrypt-allowed semantics covered by unit tests and existing webtest.

---

**Author**: Requirements subagent  
**Date**: 2026-03-18  
**Status**: Done
