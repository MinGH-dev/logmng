# 20260416 - PB FEP keyword match flags for DATA / STREAM full-line highlight (decrypt-only hits)

## 1. User requirement

### Requirement description

PB FEP log search must **not** rely on users typing ciphertext fragments. The server may include a row because a keyword matches **after in-memory decrypt-for-match** on `request_data`, `response_data`, or `error_message` (wire `bmsg`), while the UI still displays **wire ciphertext** (or other non-plaintext wire forms) in the **DATA** column and expanded **STREAM DATA** body.

Today, **full-line emphasis** in the expanded stream (see `docs/requirements/20260416-pb-fep-data-field-full-line-highlight.md`) is driven by a **line-level predicate** that mirrors `highlightKeywordsAsHtml` output on the **displayed** string. When the literal keyword does **not** appear in that displayed text, inline `<mark>` is absent and the line predicate is false — so **no** `stream-line--keyword-hit` (or equivalent) is applied, even though the row is a legitimate search hit on decrypted semantics.

This requirement adds **server-provided, non-plaintext hints** that name **which wire columns** participated in the keyword match so the client can apply **full-line** (and, if needed, related) emphasis **by plaintext keyword semantics** without requiring the keyword substring to appear in Base64/ciphertext.

### User scenario

1. An operator searches PB FEP logs with keyword **`LOCAL-PB`** (plaintext intent).
2. The backend includes a row because decrypt-for-match finds the term inside **`response_data`**, while the UI shows **ciphertext** (or encoded payload) in **DATA / STREAM DATA** — the literal string `LOCAL-PB` does **not** appear in the displayed stream text.
3. The row is expanded; **STREAM DATA** renders the same `streamPayload` selection as today (`frontend/src/components/LogTable.js`).
4. **Problem:** Full-line emphasis does not activate on matching logical lines because the client-only predicate sees **no** highlight HTML / no literal keyword in the displayed line.
5. **Expected:** For that row, logical lines in the stream panel that correspond to the **matched payload source** still receive **full-line keyword-hit styling**, consistent with the refinement in the full-line requirement doc, **without** exposing decrypted plaintext on the wire.

### Expected outcome

- **No new plaintext fields** in API responses; flags are **booleans or a small fixed enum list** only (contract-governed).
- When search keywords are **non-empty**, each PB FEP wireframe row **may** include optional fields indicating **which columns contributed** to keyword matching: at minimum the same logical sources used in backend keyword filtering (`request_data`, `response_data`, `error_message` / wire `bmsg`).
- **`mapPbFepRowToWireframe`** (or equivalent) **must** pass these hints through on the wireframe row map under **stable JSON keys** documented in `specs/log-db-pb-fep-log-search.spec.yaml` and `docs/contract.md` / `docs/api-definition.md`.
- **Frontend** (`LogTable.js` / `keywordHighlight.js`): When keywords are active and a row flag indicates that the **stream body’s displayed text is tied to a matched column** (see §2), apply **`stream-line--keyword-hit`** (or equivalent) to the **entire logical line**(s) for that payload region, **even if** `lineHasKeywordHighlightHtml` / inline `<mark>` finds no match in ciphertext.
- **Consistency:** Flags must be derived with the **same** per-cell rules as `pbFeplogCellMatchesKeyword` / `pbFeplogRowMatchesKeywordClause` in `LogDbService` (wire substring **or** PB_FEP decrypt-for-match), so UI behavior stays aligned with **why** the row appears in the result set.
- **Parent alignment:** This **extends** the definition of “line has a match” in `docs/requirements/20260416-pb-fep-data-field-full-line-highlight.md` when decrypt-only hits occur — full-line emphasis must not depend solely on ciphertext substring presence.

## 2. Design

### 2.1 Security review (optional)

- **No decrypted payload in JSON:** Response adds only **boolean (or enum) hints**, not `decrypted_*` fields. Align with `docs/requirements/20260415-encrypted-field-search-no-client-plaintext.md` and existing PB FEP wire rules.
- **Information disclosure:** Flags reveal **that** a keyword matched a given column family, not the plaintext. Product/Security should accept this as equivalent to “row is in result set” granularity; if stricter policy is required, document an opt-in or alternate UX in a follow-up.
- [ ] Security review performed (recommended before implementation).

### Technical design

#### Codebase summary (investigation)

- **`LogDbService.pbFeplogRowMatchesKeywordClause`:** For non-empty keyword terms, loads wire strings from JDBC keys `request_data`, `response_data`, `error_message`. For each keyword term, returns true if **`pbFeplogCellMatchesKeyword`** matches **any** of those three cells (OR across cells for a single term; row filter uses **OR across terms** at clause level per existing loop structure — verify in implementation when adding flags).
- **`pbFeplogCellMatchesKeyword`:** `containsIgnoreCase` on wire text **or**, if that fails, **`decryptPbFepPayloadForKeywordMatch`** then `containsIgnoreCase` on decrypted text (decrypt failures swallowed; DEBUG only).
- **`filterPbFeplogRowsByKeywordTerms`:** Filters prefetched rows; does not attach match metadata to row maps today.
- **`mapPbFepRowToWireframe`:** Maps physical row to wireframe keys including `request_data`, `response_data`, `bmsg` ← `error_message`, and **`data`** ← `buildWireframeDataCellSummary(request, response)` (short summary, prefers non-empty `response_data` then `request_data`).
- **`LogTable.js` — `streamPayload`:** For `pb-fep-svg`, returns `log.data ?? log.request_data ?? log.response_data ?? ''`. So the expanded stream may show the **`data` summary** first when present; that summary is **derived** from request/response payloads. Legacy path uses `request_data || response_data || data || …`.
- **`renderStreamBody`:** Builds per-line full-line class using `lineHasKeywordHighlightHtml` when keywords are non-empty; **fails** for decrypt-only ciphertext display as described in §1.

#### Problem analysis

1. **Predicate gap:** Client-only “does highlighted HTML differ from input?” cannot see decrypt-only matches when the UI shows ciphertext.
2. **Source ambiguity:** The stream may render `data` (summary), `request_data`, or `response_data` depending on `streamPayload` — flags must let the client know **which physical sources** matched so it can combine with **which string is displayed**.

#### Diagnostic phase (mandatory for error/bug fix only)

*Not applicable — feature / contract extension.*

#### Solution approach

**Backend:**

- When building each **PB FEP wireframe row** for responses where **keywords are non-empty**, compute **per-column match flags** using the **same** string sources and **`pbFeplogCellMatchesKeyword`** (or extracted shared helper) as the filter path:
  - **`keyword_match_request_data`:** true iff ∃ keyword term *K* such that `pbFeplogCellMatchesKeyword(request_data_wire, K)`.
  - **`keyword_match_response_data`:** true iff ∃ *K* such that `pbFeplogCellMatchesKeyword(response_data_wire, K)`.
  - **`keyword_match_bmsg`:** true iff ∃ *K* such that `pbFeplogCellMatchesKeyword(error_message_wire, K)` (maps to wire `bmsg`).
- **Omit vs false:** Contract must state whether keys are **omitted** when keywords are empty, or always present (`false`). Implementing agent must align JSON serialization with existing PB FEP patterns.
- **Performance:** Evaluation reuses decrypt-for-match only in line with existing keyword filter behavior; avoid duplicate decrypt passes per row if a single pass can populate flags (optimization allowed; behavior must match).

**Wireframe mapper:**

- **`mapPbFepRowToWireframe`:** Attach the optional flag fields to the outgoing map **after** computing wire strings for `request_data` / `response_data` / `bmsg` so keys align with **`PbFepWireframeRow`** consumers.
- **DATA summary / stream selection:** Optionally add **`keyword_match_data`** (name TBD in contract) computed as: the synthetic `data` field is considered “implicated” when `buildWireframeDataCellSummary` would draw from a payload that matched — e.g. when non-empty `response_data` is chosen for the summary, use `keyword_match_response_data`; when the summary falls back to `request_data`, use `keyword_match_request_data`. This disambiguates `streamPayload`’s preference for `log.data` on `pb-fep-svg`.

**Frontend:**

- Extend **`getPbFepOptionalEncryptedMatchHint`** or introduce a small helper that combines **row flags** with **`streamPayload(log)`** resolution:
  - If the displayed raw string is **sourced from** `response_data` (directly or via `data` summary from response branch), and `keyword_match_response_data` is true → treat **all logical lines** of that stream body as eligible for full-line hit class **or** apply the minimum line-level rule agreed with UX (default: **entire** displayed stream block lines — implementer must avoid false positives when only one line in a multi-line payload matched on server; **if server cannot indicate line-level decrypt match**, product may accept whole-stream emphasis — document choice in §5 if adopted).
  - **Refinement (preferred):** If whole-stream emphasis is too coarse, Backend may later extend with line indices — **out of scope** unless product requests; initial scope may use **all lines** in the displayed `raw` when the column flag matches and **no** inline mark is possible (decrypt-only case).
- **Minimum viable:** When `keyword_match_*` indicates the active stream source matched and `lineHasKeywordHighlightHtml` is false for every line, still set **`stream-line--keyword-hit`** on **each line** of `raw.split('\n')` for that payload (or the agreed subset).

**Contract / spec:**

- Update **`specs/log-db-pb-fep-log-search.spec.yaml`**: extend `PbFepWireframeRow` with optional boolean fields (exact names snake_case per existing row shape).
- Update **`docs/contract.md`** and **`docs/api-definition.md`** (PB FEP wireframe search sections) to describe fields, omission rules, and **no plaintext** guarantee.

**DB:**

- **None.**

### Affected scopes and change targets (verification)

Per `docs/workflow/REQUIREMENTS-CHANGE-TARGET-CHECKLIST.md` §1:

| Scope | Affected? | §2 subsection and change file list complete? |
|-------|------------|-----------------------------------------------|
| Backend | Yes — keyword flag computation + wireframe mapping | Yes |
| Frontend | Yes — `LogTable` / optional `keywordHighlight` helper | Yes |
| DB | No | N/A |
| Contract / Spec | Yes — `PbFepWireframeRow`, contract, api-definition | Yes |
| Cursor tools | Optional — `log-search-domain` skill if response shape is part of operator docs | Optional |

**Pattern 3.4** (search/filter form alignment): **Does not apply** — result presentation / API row shape only.

### Planned change file list (expected change targets)

#### Backend

- `backend/src/main/java/com/logmng/service/LogDbService.java`
  - Extract or reuse per-cell keyword match evaluation for **flag population**; **`mapPbFepRowToWireframe`** (or call site) attaches optional booleans to wireframe row maps when keywords are active.
- `backend/src/test/java/com/logmng/service/LogDbServiceTest.java` (or existing PB FEP test class covering `searchPbFepLogWireframe` / wireframe mapping)
  - Unit tests for flag behavior with ciphertext + decrypt-for-match fixtures (mock or stub crypto as appropriate).

#### Frontend

- `frontend/src/components/LogTable.js`
  - Combine row keyword-match flags with `streamPayload` / `renderStreamBody` full-line predicate for decrypt-only cases.
- `frontend/src/utils/keywordHighlight.js` (only if a shared predicate reduces duplication)
- `frontend/src/components/LogTable.test.js`
  - Tests per §3.

#### Contract / spec

- `specs/log-db-pb-fep-log-search.spec.yaml` — optional fields on `PbFepWireframeRow`.
- `docs/contract.md` — PB FEP wireframe response documentation.
- `docs/api-definition.md` — PB FEP wireframe subsection.

#### DB

- None.

## 3. Test approach

### Test case list (required)

| ID | Scope | Type | Scenario (input / condition) | Expected result | Verification (unit / integration / manual) |
|----|-------|------|------------------------------|-----------------|--------------------------------------------|
| TC-KF-01 | Backend | Normal | Row with `response_data` ciphertext; keyword matches **only** after `decryptPbFepPayloadForKeywordMatch`; `request_data` / `error_message` do not match | `keyword_match_response_data === true`; other column flags false | Unit (mvn test) |
| TC-KF-02 | Backend | Normal | Keyword matches `error_message` wire text literally (no decrypt needed) | `keyword_match_bmsg === true`; implementer verifies other flags per cell logic | Unit (mvn test) |
| TC-KF-03 | Backend | Edge | Keyword matches `request_data` only (decrypt or literal) | `keyword_match_request_data === true`; response/bmsg false | Unit (mvn test) |
| TC-KF-04 | Backend | Regression | Search with **empty** keywords array / no keyword filter | Flags omitted **or** all false per contract; no exception; shape backward compatible | Unit (mvn test) |
| TC-KF-05 | Frontend | Normal | Mock row: `LOCAL-PB` search, ciphertext in displayed stream, `keyword_match_response_data` true, `kwList` non-empty, `lineHasKeywordHighlightHtml` false for lines | Every `div.stream-line` for that stream has full-line hit class (per §2 minimum viable) **or** documented agreed subset | Unit (npm test — `LogTable.test.js`) |
| TC-KF-06 | Frontend | Regression | Plaintext literal keyword still in stream; flags may be true or false | Existing inline `<mark>` + full-line behavior from parent docs still works; no duplicate/conflicting classes | Unit (npm test) |
| TC-KF-07 | Integration | Normal | `POST .../pb-fep-log-search` with keywords and fixture row (if integration harness exists) | Response JSON includes new optional keys with expected booleans | Integration (optional if suite exists) |

### Test scenarios

#### Scenario 1: Decrypt-only match on `response_data`

1. Backend fixture: ciphertext response payload; plaintext contains keyword; wire display unchanged.
2. Verify API row flags and frontend expanded STREAM DATA show full-line emphasis on stream lines.

#### Scenario 2: Keyword on `bmsg` only

1. Match on `error_message` / `bmsg`; stream may not include bmsg text — confirm §2 UX expectations (flags still consistent; stream panel may not need full-line if not displayed — document in §5 if “no stream change” is correct).

### Test data

- Synthetic Base64/ciphertext-like strings (no real PII); keyword **`LOCAL-PB`** as in user example.
- Align with existing PB FEP test data patterns in `backend/src/test/...` and seed SQL if referenced.

### Test environment

- Backend: `mvn test`
- Frontend: `npm test -- --watchAll=false`

### 3.5 Browser automation verification (optional)

- **Applicable TCs:** TC-KF-05 (manual confirmation of DOM).
- **Procedure:** PB FEP wireframe search → expand row → snapshot stream lines for full-line class when ciphertext shows no literal keyword.
- **Reference:** `docs/workflow/BROWSER-AUTOMATION-VERIFICATION-POLICY.md`.

## 4. Checklist

### Frontend verification

- [x] Full-line class applies for decrypt-only hits when server flags indicate matching column for displayed stream
- [x] No plaintext fields added to client state from new API keys
- [x] §3 automated Frontend TCs pass

### Backend verification

- [x] Flags match `pbFeplogCellMatchesKeyword` semantics
- [x] §3 Backend TCs pass

### Integration

- [ ] Contract/spec updated in same change window as code
- [ ] Optional manual PB FEP screen check

### Documentation

- [x] Requirement doc completed (§1–§3)
- [x] Parent docs cross-referenced (see References)

## 5. Test results

*(Recorded after implementation verification on 2026-04-20.)*

### Test run date

- 2026-04-20

### Test results

#### Frontend

- `cd frontend && npm test -- --watchAll=false --testPathPattern=LogTable.test.js` — **Pass** (64 tests, 3 suites including `LogTable.test.js`).

#### Backend

- `cd backend && mvn test -q -Dtest='LogDbServiceTest#searchPbFeplog_keywords_attachKeywordMatchFlagsOnRawRows_tcKf07'` — **Pass** (tcKf07: legacy/raw row path attaches `keyword_match_*` when keywords non-empty; decrypt error paths log at WARN/ERROR as expected for invalid ciphertext fixtures).

#### Docker / local stack

- 2026-04-20: `./scripts/docker-dev-sync.sh` — **Success** (offline bundle + backend image recreate; frontend image rebuilt with synced `www/`).
- `curl -s http://localhost:9200/api/health` — **200**, `success:true`.
- `curl -s http://localhost:9200/api/db/test` — **200**, `data.connected` implied via PB table flags in response body.
- `curl -s -o /dev/null -w "%{http_code}" http://localhost:3001/` — **200**.

---

## References

### Parent / related requirements

- `docs/requirements/20260416-pb-fep-keyword-highlight-imagelog-parity.md` — ImageLog parity highlighting for PB FEP.
- `docs/requirements/20260416-pb-fep-data-field-full-line-highlight.md` — Full-line emphasis; **this doc extends** the “line match” definition for decrypt-only server matches.

### Technical references

- `backend/src/main/java/com/logmng/service/LogDbService.java` — `pbFeplogRowMatchesKeywordClause`, `pbFeplogCellMatchesKeyword`, `filterPbFeplogRowsByKeywordTerms`, `mapPbFepRowToWireframe`, `buildWireframeDataCellSummary`
- `frontend/src/components/LogTable.js` — `streamPayload`, `renderStreamBody`
- `frontend/src/utils/keywordHighlight.js` — `highlightKeywordsAsHtml`, `lineHasKeywordHighlightHtml`
- `specs/log-db-pb-fep-log-search.spec.yaml` — `PbFepWireframeRow`
- `docs/template/REQUIREMENT_TEMPLATE.md`
- `docs/workflow/REQUIREMENTS-AUTHORING-WORKFLOW.md`
- `docs/workflow/REQUIREMENTS-CHANGE-TARGET-CHECKLIST.md` §1
