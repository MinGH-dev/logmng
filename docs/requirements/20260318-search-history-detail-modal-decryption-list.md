# 20260318 - Search history detail modal: decryption-requested list and count

## 1. User requirement

### Requirement description

On the Search History (검색 이력) screen, in the action column of search results, the "view details" (자세히 보기) modal currently shows search conditions (로그 타입, 요청 사유, 검색 조건). The user requests that this modal also display: (1) the **list** of applications, service groups, and GUIDs that were requested for decryption (i.e. the rows in the approval snapshot), and (2) the **total count** of those items.

### User scenario

1. User opens the Search History screen and runs a search.
2. In the result grid, user clicks the "자세히 보기" (view details) action for a row.
3. **Problem**: The detail modal shows only search conditions (log type, request reason, search params). The user cannot see which specific items (application, service group, guid) were requested for decryption or how many there are.
4. **Issue (2026-03-18 feedback)**: Users expect **"복호화 요청 대상"** in the detail modal for **PENDING (대기)** and **APPROVED (승인)** alike. A design that only populated the list from `search_history_approved_row` when APPROVED caused PENDING (and REJECTED/EXPIRED) to always show "해당 없음" even when the search had encrypted rows—misaligned with user expectation.
5. User expects to see in the same modal: a list of decryption-requested items (application, service group, guid per row) and the total count, **for both waiting and approved requests** when the underlying search targets encrypted image-log rows.

### Expected outcome

- In the "검색 조건 상세" (view details) modal:
  - A new section **"복호화 요청 대상"** is shown when there is a non-empty set of rows to display under the rules below; otherwise show clearly that there is nothing to list (e.g. "해당 없음" or "총 0건" per product choice).
  - The section displays the **list**: for each item, **application**, **service group**, and **GUID** (e.g. table: Application | Service group | GUID).
  - The **total count** is displayed (e.g. "복호화 요청 대상 (총 n건)").
- **APPROVED**: If `search_history_approved_row` is **non-empty**, the list and count are **authoritative** from that snapshot. If the snapshot is **empty** (edge case), **fallback**: re-run the **stored search** (`search_params` + `log_type`) with the **same encrypted-row selection rules as at approve time**, cap results at **SNAPSHOT_MAX_ROWS**, include only **java_fw_imglog** rows with **hasEncryptedData** (or equivalent project rule), resolve application and service group from imagelog where possible.
- **PENDING, REJECTED, EXPIRED**: Populate **decryptionRequestedRows** / **decryptionRequestedCount** by **re-running the stored search** (`search_params` + `log_type`), **java_fw_imglog + hasEncryptedData only**, cap **SNAPSHOT_MAX_ROWS**, resolve application and service group from imagelog—same logical row set the user would have sent for approval (before snapshot persistence).
- **Security**: Unchanged; only the **requester** may open detail for their own search history; no broadening of who can see which rows.

**Note**: API field naming remains camelCase (`decryptionRequestedRows`, `decryptionRequestedCount`, `serviceGroup`). Contract updates must describe presence rules for all approval statuses covered above.

## 2. Design

### 2.1 Security review (optional)

- **Access**: Detail remains **requester-only** (same as today); no new actors or cross-user visibility.
- **Content**: For PENDING/REJECTED/EXPIRED, the list is derived from **re-executing the requester’s stored search** with encrypted-row filters—information the requester already had access to when submitting; still no decryption of payload in this flow.
- Security posture is **unchanged**; amend only expands **when** the same class of metadata (app/sg/guid) is shown in the modal.

### Technical design

#### Problem analysis

1. **Current modal**: The detail modal calls `GET /api/search-history/{id}`. Users expect **"복호화 요청 대상"** for **PENDING** and **APPROVED**; showing "해당 없음" for PENDING whenever the search included encrypted rows is incorrect.
2. **Authoritative snapshot**: For **APPROVED**, `search_history_approved_row` is the source of truth when populated. Empty snapshot on APPROVED is an edge case requiring a **fallback** identical to approve-time row selection.
3. **Non-APPROVED**: No persisted snapshot; the only consistent way to show the same conceptual set is to **re-run** the stored query (`search_params`, `log_type`) with **java_fw_imglog** rows that **hasEncryptedData** (same rules as approval snapshot build), capped at **SNAPSHOT_MAX_ROWS**, then resolve app/sg from imagelog.

#### Solution approach

**Backend:**

- Extend **GET /api/search-history/{id}** (requester-only, unchanged authorization):
  - **APPROVED**:
    - If `search_history_approved_row` has **one or more** rows: **decryptionRequestedRows** / **decryptionRequestedCount** from that table (authoritative). Resolve application/serviceGroup from imagelog by guid per row; nulls if resolution fails; do not fail the request.
    - If snapshot is **empty**: **fallback**—re-run stored search with **same encrypted-row rules as approve** (java_fw_imglog + hasEncryptedData, cap **SNAPSHOT_MAX_ROWS**), build list and count, resolve app/sg from imagelog.
  - **PENDING, REJECTED, EXPIRED**: Always derive list/count by **re-running stored search** (`search_params` + `log_type`), **java_fw_imglog + hasEncryptedData only**, **SNAPSHOT_MAX_ROWS** cap, resolve app/sg from imagelog. If the re-run yields **zero** rows, return empty array and count 0 (frontend: "총 0건" or "해당 없음" per UX).
  - **decryptionRequestedRows**: `{ application: string | null, serviceGroup: string | null, guid: string }[]`. **decryptionRequestedCount**: number (length of list after cap).
  - Log DB partial/unavailable: 200; guid present where applicable; application/serviceGroup null where resolution fails.

**Frontend:**

- When `decryptionRequestedRows` is present and **length > 0**, show **"복호화 요청 대상 (총 n건)"** and the table (Application | Service group | GUID), semantic table/a11y per design docs.
- When **empty array** or **zero count** after amendment, show empty state consistently (e.g. "해당 없음" or "총 0건")—align with product copy.
- Layout unchanged: max-height, overflow auto, z-index ≥ 1300.

**DB:**

- No schema change. Read-only use of `search_history_approved_row` and existing log DB (imagelog) for resolution.

**Contract / Spec:**

- Update `docs/api-definition.md` §6.1 (GET /api/search-history/{id}): document the new response fields `decryptionRequestedRows` and `decryptionRequestedCount`, when they are present (APPROVED only), and that application/serviceGroup may be null when log DB resolution fails. Update `docs/contract.md` briefly if the search-history detail response is summarized there.

### Affected scopes and change targets (verification)

**Before finalizing §2**, the Requirements author verified every affected scope per `docs/workflow/REQUIREMENTS-CHANGE-TARGET-CHECKLIST.md`.

| Scope | Affected? | §2 subsection and change file list complete? |
|-------|-----------|-----------------------------------------------|
| Backend | Yes | Yes |
| Frontend (view screen) | Yes | Yes |
| DB | No (read-only) | N/A |
| Contract / Spec | Yes | Yes |
| Cursor tools (skills, specs) | Optional | Yes (skill update listed) |

This requirement extends GET search-history detail behavior for **all** approval statuses that need a visible target list; **security remains requester-only**. Pattern 3.3 (API change) applies; contract and api-definition must state APPROVED snapshot vs fallback and PENDING/REJECTED/EXPIRED re-run rules.

### Planned change file list (expected change targets)

**(Planned at authoring. Implementing agent (Step 4) confirms or amends this list when implementation is complete.)**

#### Backend

- `SearchHistoryService.getDetail(userId, id)`:
  - **APPROVED + non-empty** `search_history_approved_row`: build list from snapshot; resolve app/sg from imagelog.
  - **APPROVED + empty** snapshot: fallback re-run stored search (encrypted-row rules, SNAPSHOT_MAX_ROWS).
  - **PENDING / REJECTED / EXPIRED**: re-run stored search (java_fw_imglog + hasEncryptedData, SNAPSHOT_MAX_ROWS); build list and count; resolve app/sg.
- Reuse or add LogDb/search helpers shared with approve-time snapshot logic where possible (single source of row-selection rules).
- **Tests** (see §3): PENDING with encrypted hits → non-empty list; APPROVED with snapshot vs empty snapshot fallback; log DB partial failure.

**Prior Step 4 note (pre-amendment):** Implementation matched APPROVED-only snapshot behavior; **after 2026-03-18 amendment**, backend must implement PENDING/REJECTED/EXPIRED re-run and APPROVED empty-snapshot fallback; tests TC-02/TC-05 etc. superseded by §3.

#### Frontend

- `SearchHistoryList.js`: Show section when API returns rows (non-empty) or per empty-state rule; handle **PENDING** with populated `decryptionRequestedRows` same as APPROVED.
- `frontend/src/components/SearchHistory/SearchHistory.css`
  - If needed: styles for the decryption-requested table inside the modal (max-height, overflow, spacing). Align with `docs/design/css-standard-and-exceptions.md`; document any exception.

#### Contract / Spec

- `docs/api-definition.md` §6.1 and `docs/contract.md` (if summarized): **APPROVED** (snapshot authoritative; empty → fallback re-run), **PENDING/REJECTED/EXPIRED** (re-run rules, SNAPSHOT_MAX_ROWS, java_fw_imglog + hasEncryptedData), requester-only; null app/sg on resolution failure.

#### Cursor tool update targets

- `.cursor/skills/search-history-decrypt-domain/SKILL.md`: Detail modal lists decryption targets for **PENDING and APPROVED** (and same computation for REJECTED/EXPIRED); APPROVED uses snapshot when non-empty.

#### DB

- None.

## 3. Test approach

### Test case list (required)

| ID | Scope | Type | Scenario (input / condition) | Expected result | Verification (unit / integration / manual) |
|----|-------|------|------------------------------|-----------------|--------------------------------------------|
| TC-01 | Backend | Normal | APPROVED; **non-empty** `search_history_approved_row`; java_fw_imglog; imagelog resolves app/sg. | `decryptionRequestedRows` / `decryptionRequestedCount` match **snapshot** (authoritative); not re-run row order if it differs from live search. | Unit (mvn test) |
| TC-01b | Backend | Normal | APPROVED; **empty** `search_history_approved_row`; stored search would return encrypted java_fw_imglog rows. | **Fallback**: list/count from **re-run** (same encrypted-row rules as approve, SNAPSHOT_MAX_ROWS cap); app/sg from imagelog where possible. | Unit (mvn test) |
| TC-02 | Backend | Normal | **PENDING** (same for REJECTED/EXPIRED if tested); stored search has **at least one** java_fw_imglog row with hasEncryptedData within cap. | Response includes **non-empty** `decryptionRequestedRows` and matching `decryptionRequestedCount`; derived from **re-run**, not snapshot. | Unit (mvn test) |
| TC-02b | Backend | Edge | PENDING; stored search yields **zero** encrypted rows (or non-imagelog log_type). | Empty array and count 0 (or documented omit rule); UI empty state. | Unit |
| TC-03 | Backend | Edge | APPROVED with snapshot (or re-run path); log DB missing rows or partial failure. | 200; guid present; application/serviceGroup null where resolution failed; count consistent with spec. | Unit or Integration |
| TC-04 | Frontend | Normal | "자세히 보기" for **PENDING** with API returning non-empty decryption list. | Modal shows **"복호화 요청 대상 (총 n건)"** and table; same UX as APPROVED with data. | Unit or Manual |
| TC-05 | Frontend | Normal | "자세히 보기" for **APPROVED** with snapshot vs **APPROVED** with empty snapshot (fallback). | Snapshot case: table matches persisted rows. Fallback case: table matches re-run capped set (verify against known fixture). | Manual / browser or unit with mocked API |
| TC-06 | Integration | Normal | E2E: pending request with encrypted hits → detail modal lists targets; after approve, detail still correct (snapshot). | PENDING shows re-run list; APPROVED shows snapshot when populated. | Manual / browser |

**§3 bullets (summary for handoff)**

- **PENDING non-empty**: GET detail for a PENDING search history whose stored params hit encrypted java_fw_imglog rows → `decryptionRequestedRows` is non-empty, count matches capped re-run; requester-only enforced.
- **APPROVED snapshot vs fallback**: When `search_history_approved_row` has rows, response must match snapshot; when empty, response must match **fallback re-run** (same rules as approve, SNAPSHOT_MAX_ROWS), not "해당 없음" if re-run has rows.

### Test scenarios

#### Scenario 1: APPROVED with non-empty snapshot

1. APPROVED + rows in `search_history_approved_row`.
2. GET detail / open modal.
3. List and count = snapshot; imagelog enriches app/sg.

#### Scenario 2: APPROVED with empty snapshot (fallback)

1. APPROVED but no approved_row rows; search still has encrypted imagelog matches.
2. GET detail.
3. List from **re-run**; count ≤ SNAPSHOT_MAX_ROWS.

#### Scenario 3: PENDING with encrypted rows

1. PENDING; search_history has search_params that return encrypted java_fw_imglog rows.
2. GET detail / open modal.
3. Non-empty list and count; no "해당 없음" solely because status is PENDING.

#### Scenario 4: Log DB partial failure

1. Any path above with missing imagelog rows for some guids.
2. Verify 200, null app/sg where needed, guid present.

### Test data

- Fixtures: APPROVED with populated and empty `search_history_approved_row`; PENDING with same `search_params` as a search known to return encrypted imagelog rows; imagelog rows for guid resolution. No new schema required.

### Test environment

- Frontend: http://localhost:3001 (or per contract)
- Backend: http://localhost:9200
- Database: PostgreSQL per project setup

## 4. Checklist

### Frontend verification

- [ ] After amendment: PENDING + non-empty API → modal shows list (TC-04)
- [ ] APPROVED snapshot vs fallback UX (TC-05)
- [ ] Empty list / 0 count empty state consistent; table a11y unchanged

### Backend verification

- [ ] After amendment: PENDING/REJECTED/EXPIRED re-run path; APPROVED empty-snapshot fallback; TC-01, TC-01b, TC-02, TC-02b
- [ ] Log DB partial failure still 200 (TC-03)

### Integration

- [ ] E2E PENDING + APPROVED snapshot (TC-06)
- [ ] Empty snapshot fallback (§3 Scenario 2)

### Documentation

- [x] Requirement doc amended 2026-03-18 (PENDING/APPROVED parity, snapshot vs fallback)
- [ ] api-definition.md / contract.md aligned with amended §2 (post-implementation)

## 5. Test results

### Test run date

- **Pre-amendment**: 2026-03-18 (APPROVED-only snapshot behavior).
- **Post-amendment**: Re-run tests after Backend/Frontend implement §2 (PENDING re-run, APPROVED fallback).

### Test results

Prior runs validated **APPROVED + snapshot** and **non-APPROVED omitted fields**; those cases are **superseded** by the 2026-03-18 amendment. Re-execute:

- Backend: `mvn test` — must include TC-01b, TC-02, TC-02b (or equivalent).
- Frontend: `npm test` + manual TC-04, TC-05, TC-06 per §3.

### Issues found and resolution

- **User feedback (2026-03-18)**: PENDING must show "복호화 요청 대상" when search has encrypted rows; doc updated; implementation and tests to follow.

### Next steps

- Backend: implement re-run for PENDING/REJECTED/EXPIRED; APPROVED empty-snapshot fallback; extend unit tests.
- Contract: document field presence for all statuses.
- QA: re-verify checklist §4 after implementation.

## 6. Error remedy result (cause and action) — for error/bug fix requirements only

Not applicable (feature requirement).

---

**Author**: Requirements subagent  
**Date**: 2026-03-18  
**Amendment**: 2026-03-18 — PENDING/APPROVED parity; APPROVED snapshot authoritative with empty-snapshot fallback; re-run rules for non-APPROVED.  
**Status**: Design amended; implementation and §5 verification pending to match §2/§3.
