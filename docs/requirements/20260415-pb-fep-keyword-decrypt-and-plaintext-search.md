# 20260415 - PB FEP keyword search: plaintext and decrypt-for-match filtering

## 1. User requirement

### Summary (user language — Korean intent)

- **PB FEP 로그 검색**(`pb_feplog`)은 **`POST /api/logs/db-refactored/search`**(기본 `logType=pb_feplog`)과 **`POST /api/logs/db-refactored/pb-fep-log-search`** 모두에서 **키워드 검색**이 동작해야 한다.
- 키워드는 (1) **와이어 컬럼의 평문**(`vlen`→`request_data`, `data`→`response_data`, `bmsg` 등 DB·JDBC 문자열 그대로)에 대해 **복호화 없이** 매칭할 수 있어야 하고, (2) **암호화된 페이로드**가 들어 있는 `vlen`/`data`/`bmsg`(API 계층 별칭 `request_data`/`response_data` 및 와이어프레임 `bmsg`)에 대해서는 **`CryptoUtil.decryptLogPayload(..., LogPayloadCryptoVariant.PB_FEP)`로 메모리 내 복호화 후 필터링만** 수행해야 한다(`java_fw_imglog`의 decrypt-for-match 패턴과 정렬; 목록 응답에 평문을 실어 보내지 않음은 `docs/contract.md` 및 `20260415-encrypted-field-search-no-client-plaintext.md`와 합치).
- **현재** `executePbFeplogUnionSearch`의 SQL 경로는 **키워드를 반영하지 않으며**, UI는 **하이라이트** 위주일 수 있으나 사용자는 **행 필터링**(키워드 적중 행만 결과에 포함)을 기대한다.

### Requirement description

For **`pb_feplog`** searches exposed through both **legacy** `POST /api/logs/db-refactored/search` (with `logType` defaulting to `pb_feplog`) and **wireframe** `POST /api/logs/db-refactored/pb-fep-log-search`, the product must implement **keyword-driven row filtering**:

1. **Plaintext / wire columns:** Match user keywords against **ciphertext-as-stored string** columns where the stored value is already plaintext (or non-encrypted wire text), using the same string extraction rules as today for JDBC values (e.g. `jdbcValueToString` on `request_data`, `response_data`, and **`bmsg` / `error_message`** wire paths).
2. **Encrypted payload columns:** When keywords could match only **after decryption**, the server may **decrypt in memory solely to evaluate the predicate**, following the same **search vs display** separation as **Java FW Image Log** (`decrypt-for-match`; no undocumented plaintext keys on standard search responses unless Contract explicitly allows them).
3. **Pagination correctness:** After filtering, **total count and page slices** must reflect **keyword-filtered** rows (not “page of SQL results then highlight only”).

**Cross-reference (security and contract):** This requirement **inherits** the constraints in `docs/requirements/20260415-encrypted-field-search-no-client-plaintext.md` for **browser-visible JSON**, **logging**, and **activity detail** minimization. Any optional non-plaintext match hints (booleans, etc.) must be **Contract-documented**.

### User scenario

1. An operator opens **PB FEP Log** or **PB FEP log search (wireframe)** and sets the usual structured filters (dates, `loginId`, etc.).
2. The operator enters **one or more keywords** that appear (a) in a **plaintext** portion of `request_data` / `response_data` / `bmsg`, or (b) **only inside** an encrypted PB FEP payload after decryption.
3. The operator runs search.
4. **Expected:** The grid lists **only rows** that satisfy the keyword predicate (combined with existing SQL filters as defined in §2).
5. **Expected:** The **HTTP response** for search does **not** expose decrypted sensitive payloads as plaintext fields unless the product’s **explicit decrypt** path and Contract say otherwise.
6. **Problem today:** Keywords are **not applied** in `executePbFeplogUnionSearch` SQL; combined with UI highlight-only behavior, **row sets do not reflect** keyword hits on decrypted or plaintext content.

### Expected outcome

- **Backend:** Keyword list participates in **row inclusion** for both PB FEP search endpoints, with **in-memory filtering** after a bounded prefetch when keywords are non-empty (see §2), aligned with the **`IMGLOG_FILTER_PREFETCH_CAP`** pattern.
- **Semantics:** Documented **OR across keyword tokens** and **case rules** (see §2 — default align with `java_fw_imglog` unless product chooses otherwise).
- **`decryptData` flag:** Clarified interaction: **keyword search must not depend on clients sending `decryptData: true`** for match behavior, and **search responses must not** carry `decrypted_*` plaintext fields by default (see §2 decision).
- **Activity log:** If keywords are recorded for `pb_feplog` search events, they must be **masked** like `java_fw_imglog` (`ActivityLogAspect` — extend the **`pb_feplog`** branch).
- **DB / tests:** Extend **`init-data-local-decrypt-test-pbfep.sql`** or add a **dedicated seed** with a **known plaintext substring** inside verifiable PB FEP ciphertext so automated tests can assert **keyword `X` returns the row**.
- **Frontend:** Either send **`decryptData: true`** when keywords are non-empty **only if** Contract still requires it for a legacy path, or **prefer** backend treating keywords as sufficient for match — **one approach is mandated in §2** so Frontend and Contract stay aligned.
- **Contract / `docs/api-definition.md`:** Updated if request defaults, response shape, `decryptData` semantics, or optional match-metadata fields change.

**Note:** Search form layout standards are **orthogonal** unless this work changes shared form components; if so, reference `docs/design/search-fields-by-screen.md` and `docs/design/search-field-definition-items.md`.

## 2. Design

### 2.1 Security review (PII / decryption / access control)

Because this requirement adds **decrypt-for-match** on PB FEP payloads and touches **activity logging**, the **Security** subagent should review §2.1 and the **Technical design** before implementation is considered complete.

- [ ] Security review performed
- **Risks:** Plaintext in search JSON via `decrypted_request_data` / `decrypted_response_data`; keyword values in **activity log** `action_detail`; excessive prefetch causing latency or memory pressure; logging decrypt failures with sensitive fragments.
- **Acceptance / recommendations (Security to confirm):** Treat **search** and **explicit decrypt** as separate trust boundaries; **mask** keywords in activity detail; **no production INFO logs** of decrypted content from match paths.

### Technical design

#### Codebase summary (authoring baseline)

- **`LogDbService.executePbFeplogUnionSearch`:** Builds `pb_send` ∪ `pb_recv` SQL with date/media/TR/login filters, **`ORDER BY`**, then **`LIMIT`/`OFFSET`**. **`request.getKeywords()` is not referenced** in the SQL builder. After each row is read, **`decrypted_request_data` / `decrypted_response_data`** are populated **only when** `Boolean.TRUE.equals(request.getDecryptData())` **and** keywords are non-empty — this path **decrypts for display fields**, not for **predicate filtering**, and conflicts with **“no plaintext on search wire”** for modern contract alignment.
- **`PB_FEPLOG_WIRE_SELECT_BODY`:** Projects `vlen AS request_data`, `data AS response_data`, `bmsg AS error_message` (plus other columns).
- **`java_fw_imglog` prefetch pattern:** When text filters apply, SQL uses **`LIMIT IMGLOG_FILTER_PREFETCH_CAP` (5000)**, then **`filterImageLogRowsByFieldAndKeywordTerms`**, then **in-memory pagination** and **`sanitizeJavaFwImglogSearchRow`**.
- **`ActivityLogAspect`:** For `java_fw_imglog`, **`datastring`**, **`headerstring`**, and **`keywords`** list entries are passed through **`maskSensitiveData`**. For **`pb_feplog`**, only `mediaCode`, `trCode`, `loginId` are copied — **keywords are not logged today**; if the backend starts accepting keywords for PB, the aspect **must** mask them the same way when added to `searchConditions`.

#### Relationship to existing requirements

| Existing doc | Relevance |
|--------------|-----------|
| `20260415-encrypted-field-search-no-client-plaintext` | **Mandatory** alignment: search/list JSON and DevTools must not become a plaintext channel; highlights must not force plaintext DOM. |
| `20260413-imagelog-search-decrypt-display-separation`, `20260414-imagelog-keyword-or-field-and-ui` | **Behavioral reference** for decrypt-for-match vs response sanitization and keyword OR semantics. |
| `20260326-pb-fep-log-search-screen-wireframe` | Wireframe endpoint and column naming for UI. |

#### Problem analysis

1. **Keywords ignored in SQL:** No predicate ties `keywords` to `request_data` / `response_data` / `bmsg`, so **row sets** are wrong for keyword-only intent.
2. **Cannot match encrypted-only substrings in SQL** without DB-side keys — **server-side in-memory decrypt-for-match** is required for parity with imagelog patterns.
3. **`decryptData` + keywords today:** Populates **`decrypted_*`** on the search response map — **contradicts** encrypted-field-search separation unless explicitly grandfathered in Contract (must be **resolved** in this requirement).
4. **Activity log gap:** If **`keywords`** are added to PB search logging, they must be **masked** like imagelog.
5. **Test gap:** Need **deterministic seed** ciphertext containing a **known substring** for automated assertions.

#### Diagnostic phase (mandatory for error/bug fix only)

*Not classified as a standalone error-fix requirement. If implementation uncovers a specific production defect, follow the project **error-first** diagnostic workflow and record under §6 if needed.*

#### Solution approach

**Decision — `decryptData` vs keyword match (single product rule):**

- **Match path:** When **`keywords` is non-empty**, the server **must** run the **keyword filter** (plaintext scan + decrypt-for-match on PB ciphertext columns per below) **regardless of `decryptData`**, mirroring the **`java_fw_imglog`** rule that **`decryptData` does not gate match decryption** (see `20260414-imagelog-keyword-or-field-and-ui`).
- **Response path:** For **`POST .../search`** and **`POST .../pb-fep-log-search`**, **do not** populate **`decrypted_request_data`**, **`decrypted_response_data`**, or other **decrypted plaintext keys** on row maps **unless** Contract and Security explicitly document an exception. Plaintext display remains on the **dedicated decrypt** APIs / approval flows where applicable.
- **Frontend (`LogGrid.js` and related):** **Prefer no client change:** do **not** require **`decryptData: true`** solely to enable keyword matching. If the UI currently sets `decryptData` to trigger the legacy decrypt columns on search, **remove or repurpose** that coupling during implementation so keyword search works with **`decryptData` false** for the search call. **If** a transitional period requires backward compatibility, document it in Contract with an **end date** (implement only if product confirms).

**Backend (`LogDbService` and helpers):**

- **Reference implementation pattern:** Introduce a **PB FEP prefetch cap** (constant analogous to **`IMGLOG_FILTER_PREFETCH_CAP`**, name e.g. **`PB_FEPLOG_KEYWORD_FILTER_PREFETCH_CAP`** — exact naming for implementer) used **when `keywords` is non-empty**: fetch up to **N** rows **ordered consistently** with the user’s `ORDER BY`, apply **in-memory keyword filter**, then **paginate** and recompute **`totalCount`** from the **filtered** list (same structural approach as imagelog text-filter path). **Document** cap choice and overflow behavior (e.g. “at most M matching rows visible within cap”) in Contract if user-visible.
- **Surfaces scanned per row for keyword OR (within one row, any keyword token may hit any surface — confirm final algebra):**  
  - **Plaintext / wire string:** `jdbcValueToString(row.get("request_data"))`, `jdbcValueToString(row.get("response_data"))`, `jdbcValueToString(row.get("error_message"))` (a.k.a. **`bmsg`** on wireframe mapping).  
  - **Decrypt-for-match:** On **`request_data`** and **`response_data`** strings, attempt **`CryptoUtil.decryptLogPayload(..., LogPayloadCryptoVariant.PB_FEP)`** when a **ciphertext heuristic** applies **or** use a **try/catch** strategy consistent with existing PB decrypt utilities — implementer must **avoid** logging decrypted material on failure at INFO/WARN.
  - **`bmsg`:** If product confirms **`bmsg` may contain encrypted payloads** in some environments, apply the **same decrypt-for-match** attempt as for `request_data`/`response_data`; if **`bmsg` is always plaintext error text**, plaintext match alone is sufficient (document the assumption in §5 notes if verified).
- **Keyword list combinatorics:** **Default:** **OR across keyword list** (any token matches ⇒ row matches keyword clause), **case-insensitive** per **`java_fw_imglog`** unified text behavior, unless product explicitly chooses different rules — if different, **update Contract** and §3 TCs.
- **Combination with structured SQL filters:** Structured filters remain **AND** with the keyword predicate (SQL narrows candidate set before or within prefetch — implementer designs for correctness within the cap).
- **Wireframe mapping:** Filtering applies to **logical row content** before **`mapPbFepRowToWireframe`** (or equivalent) so wireframe keys remain consistent.

**Frontend:**

- Ensure search requests include **`keywords`** array for PB screens as today.
- **Do not** rely on **`decryptData: true`** for keyword row filtering once backend behavior lands; align **highlight** behavior with Contract (non-plaintext hints only), consistent with `20260415-encrypted-field-search-no-client-plaintext`.

**DB:**

- **Extend** `backend/src/main/resources/db/init-data-local-decrypt-test-pbfep.sql` **or** add a **dedicated** seed file referenced from local setup docs, containing at least one row where a **stable plaintext substring** (e.g. token `PB-FEP-KW-TEST-20260415`) exists **only** inside ciphertext for **`request_data` or `response_data`**, plus a control row that does **not** contain that substring — for **automated** keyword assertions.
- No **production schema** change is required for keyword filtering itself unless implementer adds indexes for performance (then **DBA** review).

**Contract / API definition:**

- Document **`keywords`** behavior for **`pb_feplog`** on both endpoints.
- Document **`decryptData`** semantics after decoupling from match path; remove or deprecate any documented **`decrypted_*`** keys on search responses if they are eliminated.
- If **prefetch cap** implies **incomplete result guarantees**, document operator-visible behavior.

**Cursor tool update targets (if behavior changes domain knowledge):**

- `.cursor/skills/log-search-domain/SKILL.md` — PB FEP keyword + decrypt-for-match summary and `decryptData` semantics.

### Affected scopes and change targets (verification)

| Scope | Affected? | §2 subsection and change file list complete? |
|-------|------------|-----------------------------------------------|
| Backend | Yes | [ ] |
| Frontend | Yes (request payload coupling / highlight; verify) | [ ] |
| DB | Yes (seed for tests) | [ ] |
| Contract / Spec | Yes | [ ] |
| QA / Integration | Yes | [ ] |
| Cursor tools (skills) | Likely Yes | [ ] |

### Planned change file list (expected change targets)

**(Planned at authoring. Implementing agent (Step 4) confirms or amends this list when implementation is complete.)**

#### Backend

- `backend/src/main/java/com/logmng/service/LogDbService.java` — PB FEP keyword filter + prefetch cap; remove or isolate legacy **`decrypted_*`** on search responses per Contract.
- `backend/src/main/java/com/logmng/aspect/ActivityLogAspect.java` — **`pb_feplog`**: mask **`keywords`** (and any other sensitive search fields added) like **`java_fw_imglog`**.
- `backend/src/test/java/com/logmng/service/LogDbServiceTest.java` (and related test SQL under `backend/src/test/resources/sql/...`) — assertions for keyword filter + response shape.

#### Frontend

- `frontend/src/components/LogGrid.js` (and PB-specific search form components if they set `decryptData`) — align **`decryptData`** usage with §2 decision; ensure keywords are sent; avoid plaintext leakage in logs.

#### DB

- `backend/src/main/resources/db/init-data-local-decrypt-test-pbfep.sql` **or** new `seed-pb-fep-keyword-decrypt-*.sql` + reference from `setup.sh` / docs as appropriate for local runs.

#### Contract / docs

- `docs/contract.md`, `docs/api-definition.md` — PB FEP search semantics, `decryptData`, optional match metadata, prefetch/cap disclosure if needed.

#### Cursor tools

- `.cursor/skills/log-search-domain/SKILL.md` — PB FEP keyword + search vs decrypt summary.

## 3. Test approach

### Test case list (required)

| ID | Scope | Type | Scenario (input / condition) | Expected result | Verification |
|----|-------|------|------------------------------|-----------------|--------------|
| TC-01 | Backend | Normal | PB FEP search with **keywords** matching **plaintext** in `request_data` / `response_data` / `bmsg` only | Row **included** in filtered result set | Unit / integration (`mvn test`) |
| TC-02 | Backend | Normal | Seed row where substring exists **only after** `decryptLogPayload` on `request_data` or `response_data`; keyword = known plaintext token | Row **included** | Unit / integration (`mvn test`) |
| TC-03 | Backend | Normal | Row fails ciphertext heuristic / decrypt throws; keyword matches **plaintext portion only** of same column | Row still **included** if plaintext matches; **no** unhandled exception | Unit |
| TC-04 | Backend | Edge | **Multiple keywords** (OR semantics per §2) | Row included if **any** token matches eligible surface | Unit |
| TC-05 | Backend | Edge | **Case variation** on keyword vs stored / decrypted text | Behavior matches **documented** case rule (default: case-insensitive) | Unit |
| TC-06 | Backend | Normal | `keywords` non-empty, **`decryptData` false** | Rows still filtered by encrypted-only matches; **no** `decrypted_*` plaintext keys on serialized search response | Unit / assertion on response map |
| TC-07 | Contract | Normal | Review **`docs/contract.md` / `docs/api-definition.md`** after implementation | **`keywords`**, **`decryptData`**, response keys match implementation | Manual / doc review |
| TC-08 | DB | Normal | Apply seed SQL locally | Known token row present; control row absent for token | `psql` / setup script / CI fixture |
| TC-09 | Frontend | Normal | PB FEP or wireframe search UI: enter keyword, submit | Request payload contains **`keywords`**; **no** reliance on **`decryptData: true`** for results (post-backend change) | Unit (`npm test`) or integration |
| TC-10 | Backend | Security | Activity log entry for PB search includes keyword params | Values are **masked** (same family as `maskSensitiveData` for imagelog) | Unit / integration assertion on `action_detail` |
| TC-11 | QA / Integration | Normal | End-to-end: search with keyword hitting encrypted-only substring | Grid row count > 0; Network JSON **without** decrypted plaintext fields per Contract | Manual / browser + Network |
| TC-12 | QA / Integration | Edge | Dataset larger than prefetch cap (synthetic) | Behavior matches Contract (e.g. partial visibility / warning); **no** silent wrong totals — **document expected** | Integration / manual (as Contract dictates) |

### Test scenarios

#### Scenario 1: Encrypted-only keyword match

1. Load DB seed with known ciphertext plaintext token.
2. Call **`pb-fep-log-search`** (or legacy search) with date/login filters narrowing to seed rows and **keyword = token**.
3. Assert row returned; assert response JSON has **no** forbidden `decrypted_*` keys.

#### Scenario 2: Activity log masking

1. Execute PB search with a sensitive keyword from test harness.
2. Fetch activity log / inspect captured `action_detail`.
3. Assert keyword material is **masked**, not literal.

### Test data

- Executable seed: extend **`init-data-local-decrypt-test-pbfep.sql`** or companion file with **documented** plaintext token embedded in PB-encrypted payload for **`PB_FEP`** variant; include **negative** control rows.

### Test environment

- Frontend: `http://localhost:3001` (or per contract)
- Backend: `http://localhost:9200`
- Database: PostgreSQL (PB schema per project setup)

### 3.5 Browser automation verification (optional)

- **Applicable TCs:** TC-11 (partial).
- **Procedure:** Navigate to PB FEP log search → enter keyword → submit → snapshot grid; optional Network inspect per `docs/workflow/BROWSER-AUTOMATION-VERIFICATION-POLICY.md`.

## 4. Checklist

### Frontend verification

- [ ] Keywords sent for PB searches; **`decryptData`** behavior matches Contract
- [ ] No plaintext leakage via console/storage

### Backend verification

- [ ] Unit/integration tests cover TC-01–TC-06, TC-10
- [ ] Logs reviewed: no decrypted sensitive content at INFO in match paths

### Integration

- [ ] TC-11 passed on staging or local Docker stack

### Documentation

- [ ] Requirement doc completed
- [ ] Contract / API definition updated

## 5. Test results

### Test run date

- **2026-04-15** (KST, local agent run)

### Test results

| Command | Exit code | Summary |
|---------|-----------|---------|
| `cd /Volumes/T7/dev/logmng_frontend/dev/backend && mvn test` | **0** | **508** tests, **0** failures, **0** errors, **0** skipped — `BUILD SUCCESS` (Surefire aggregate). |
| `cd /Volumes/T7/dev/logmng_frontend/dev/frontend && npm test -- --watchAll=false` | **0** | **40** test suites passed; **292** tests passed. |

### TC coverage (automated vs deferred)

| §3 ID | Result | Notes |
|-------|--------|--------|
| TC-01 | **Pass** | `LogDbServiceTest.searchPbFeplog_keywords_plaintextSurfaces_tc01` — plaintext `request_data` / `response_data` / `error_message` (bmsg). |
| TC-02 | **Pass** | `LogDbServiceTest.searchPbFeplog_keywords_encryptedPayloadDecryptForMatch_tc02` — token only inside PB_FEP ciphertext. |
| TC-03 | **Pass** | `LogDbServiceTest.searchPbFeplog_keywords_decryptFails_plaintextPortionStillMatches_tc03`. |
| TC-04 | **Pass** | `LogDbServiceTest.searchPbFeplog_keywords_multipleTerms_orSemantics_tc04`. |
| TC-05 | **Pass** | `LogDbServiceTest.searchPbFeplog_keywords_caseInsensitive_tc05`. |
| TC-06 | **Pass** | `LogDbServiceTest.searchPbFeplog_keywords_decryptDataFalse_noDecryptedKeys_tc06` — legacy search + wireframe; no `decrypted_*` keys. |
| TC-07 | **Partial** | `docs/contract.md` / `docs/api-definition.md` updated in this change set; formal doc review checklist item remains for stakeholders. |
| TC-08 | **Deferred** | Seed `seed-pb-fep-keyword-decrypt-20260415.sql` + `setup.sh` hook added; local Docker / `psql` apply not executed in this QA run (unit tests are primary gate). |
| TC-09 | **Pass** | `LogGrid.test.js` — PB FEP / wireframe requests send `keywords`; `decryptData` false (no keyword/decrypt coupling). |
| TC-10 | **Pass** | `ActivityLogAspectTest.logActivity_pbFeplogSearch_masksKeywordsInActionDetail`. |
| TC-11 | **Deferred** | Browser / Network E2E not run in this step (optional per §3.5). |
| TC-12 | **Deferred** | Prefetch-cap edge / synthetic dataset — per Contract when exercised. |

**Commands:**

- Backend: `cd /Volumes/T7/dev/logmng_frontend/dev/backend && mvn test`
- Frontend: `cd /Volumes/T7/dev/logmng_frontend/dev/frontend && npm test -- --watchAll=false`

## 6. Error remedy result (cause and action)

*Not applicable unless tracked as a bugfix under this doc.*

---

## 7. Final version (Korean)

**Status:** Draft for stakeholders. Refresh after §5 per `docs/workflow/DOCUMENT-LANGUAGE-POLICY.md`.

### Draft Korean summary

- **요구사항:** PB FEP 로그 검색 두 엔드포인트에서 키워드로 **행이 걸러져야** 하며, 평문 컬럼은 그대로, 암호화 컬럼은 **매칭용 메모리 복호화**로 찾되 **목록 응답·개발자 도구로 평문이 새면 안 된다.**
- **기대 결과:** `executePbFeplogUnionSearch` 경로에 imagelog와 유사한 **프리페치 캡 + 메모리 필터**가 적용되고, `decryptData`는 **매칭 게이트가 아니다.** 활동 로그에는 키워드가 **마스킹**된다. 시드로 자동화 검증 가능한 **암호문 속 알려진 부분 문자열**이 준비된다.
- **검증:** §3 TC 및 §5 완료 후 기재.

---

**Author:** Requirements (subagent)  
**Date:** 2026-04-15  
**Status:** In progress
