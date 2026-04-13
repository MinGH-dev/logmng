# 20260413 - Image log search match vs plaintext display separation

## 1. User requirement

### Requirement description

Users must be able to **find** Java FW Image Log (`java_fw_imglog`) rows by searching against content that is stored encrypted in the database (including bracket-wrapped JSON string values and, where applicable, other encrypted columns). **Finding** a row may require **server-side decryption only for matching** (in-memory or equivalent), so ciphertext-only SQL predicates are not the only path.

At the same time, **viewing decrypted “everything”** must remain **gated by the existing decryption-approval workflow** (search history, approver decision, decryption-allowed store, composite `(guid, status)` rules per related requirements).

**Separation rule:** Search-time decryption used **solely to decide whether a row matches** the user’s criteria **must not** cause **plaintext** (or full decrypted payloads) to appear in the **default search list/grid** or in the **`POST .../search` (log DB search) response** as a side effect. The UI must continue to show **ciphertext or an agreed encrypted representation** for those fields in the grid until the user **explicitly** requests decryption through the **approved** decrypt path.

**Explicit decrypt only:** Full decryption for display must occur **only** when the user **explicitly** triggers the decrypt action (e.g. per-row decrypt) **and** the backend authorizes it (permission + decryption-allowed / approval rules). **Without** that authorization path, **decrypted values must never** be shown in the UI or returned in APIs that this requirement treats as “search / list” surfaces.

### User scenario

1. A user runs an Image Log search with **data**, **header**, and/or **keyword** conditions that match values present only **after** decrypting bracket-encrypted JSON fragments (or other supported encrypted representations).
2. The system **returns matching rows** in the search result set so the user knows which rows qualify.
3. In the **result grid**, columns that correspond to encrypted content still show **encrypted / masked form**, not plaintext produced only for search matching.
4. The user requests **decryption approval** when they need plaintext; after approval and explicit decrypt, **plaintext appears** only through the **dedicated decrypt API / UI path**, not through the search response alone.
5. **Problem:** Current backend logic may **decrypt for filtering** (`decryptJsonStringValues` on `datastring` / `headerstring`, keyword loops) while other paths (e.g. `readImageLogResultSet` when `decryptData` and `keywords` are set) may attach **`decrypted_data` / `decrypted_header`** from the **`data` / `header`** columns to the row map. That can **conflict** with the rule that **search must not expose plaintext** without explicit user action plus approval.

### Expected outcome

- Search **still matches** rows based on encrypted-stored content where the product supports it, using **server-side decrypt-for-match** when necessary.
- **`POST /api/logs/db-refactored/search`** (and any contract-equivalent log search endpoint used for the Image Log grid) **does not** include **plaintext** fields populated **only** from search-time decrypt-for-match. Internal flags or ephemeral values used for matching must **not** leak to clients as user-visible decrypted fields.
- The **Image Log list/grid** shows **ciphertext or agreed encrypted display** for protected columns **by default** after search; **no** automatic plaintext column from search alone.
- **Decrypt button / explicit decrypt** remains subject to **`screenFunctions.main.decrypt`** (or system admin) and **decryption-allowed / approval** rules documented in `docs/contract.md`, `docs/api-definition.md`, and requirements **`20260317-search-decrypt-permission-ui`**, **`20260318-decryption-approval-guids-encrypted-only`**, **`20260320-imagelog-guid-status-composite-key`**.
- **Contract and API definitions** are updated if response shapes or flags change so clients and agents have a single source of truth (`docs/workflow/DOC-CODE-SYNC.md`).

**Note:** This requirement does **not** redefine search-form **layout** or cross-screen **user-block** field alignment (see `docs/workflow/REQUIREMENTS-CHANGE-TARGET-CHECKLIST.md` §2.4) unless explicitly extended; scope is **search result content**, **API response fields**, and **decrypt gating**.

**Amendment (wire names for display-only match flags):** On **`POST /api/logs/db-refactored/search`** and **`advanced-search`** responses for **`java_fw_imglog`**, optional boolean **`hasEncryptedMatchDatastring`** / **`hasEncryptedMatchHeaderstring`** are the contract-documented, non-plaintext UI hints for encrypted-region highlight; they replace client-facing use of **`_datastring_has_encrypted_match`** / **`_headerstring_has_encrypted_match`** (see `docs/contract.md`, `docs/api-definition.md` §5.1, DOC–CODE–SYNC).

### Clarification: 「데이터」 (`datastring` request field) vs binary `data` / `header` columns — `LOCAL` example

**Verified in repo (2026-04-13):**

- Local seed `backend/src/main/resources/db/init-data-local-decrypt-test-imagelog.sql` (generated by `LocalDecryptSampleSeedGenerator`): row **`LOCAL-DECRYPT-TST-IM-0001`** has **`data`** / **`header`** as **E002 ciphertext**; **`datastring`** is JSON with a bracket-encrypted **`p`** value; **`headerstring`** is **plaintext JSON** including the string **`LOCAL-DECRYPT-TST-IM-0001`**. After decrypt, **`data`** plaintext is designed to contain **`LOCAL-DECRYPT-PLAIN-DATA-0001`**; decrypted **`p`** is **`한글복호화검증-필드p-0001`** (no substring **`LOCAL`**).
- **`LogDbService.filterImageLogRowsByDataHeaderKeywords`:** The **`datastring`** filter examines only the **`datastring`** column value (substring match and, when `[...]` segments exist, **`decryptJsonStringValues`** on that JSON). The **keywords** filter examines only **`datastring`** and **`headerstring`** the same way. It does **not** load, decrypt, or substring-scan the **`data`** or **`header`** binary columns for either path.
- **UI:** **`ImageLogSearchForm`** labels the **`datastring`** input **「데이터」** and submits it as API **`datastring`** only (`frontend/src/components/ImageLogSearchForm.js`).

**Spec consistency vs product gap**

- **Current behavior is consistent with the implemented scope of this requirement (`20260413-imagelog-search-decrypt-display-separation`):** §1 required **no plaintext in search responses** and **decrypt-for-match** for paths that already existed (e.g. **`datastring` / `headerstring`** bracket JSON). It did **not** require extending **decrypt-for-match** to the **`data`** / **`header`** DB columns when the user types a term in the **「데이터」** field (**`datastring`** API parameter only). Therefore, a user entering **`LOCAL`** in **「데이터」** does **not** match row IM-0001 via decrypted **`data`** plaintext — **not a defect relative to this doc’s original scope**.
- **Product / UX gap** if stakeholders assume **「데이터」** means “search everything data-related including the **`data`** column,” or if they expect one box to match **any** encrypted store: today they must use the field that actually covers the surface (e.g. **keywords** can match **`LOCAL`** in IM-0001’s **plaintext `headerstring`**, or a future **dedicated `headerstring` / keyword** strategy per product rules). Alternatively, a **follow-on requirement** may define **decrypt-for-match on `data` / `header`** for specific filters, **still** without returning **`decrypted_data` / `decrypted_header`** on **`POST .../search`** (per §1 separation rule).

---

## 2. Design

### 2.1 Security review (PII / decryption / approval)

- [x] Security review performed (Security subagent, 2026-04-13): Search vs explicit-decrypt trust boundaries confirmed; response serialization must not attach match-only decrypt results; `decryptData` must not populate plaintext on search responses; no decrypted material or raw keywords in logs (prod; DEBUG restricted); internal match flags stripped from outgoing JSON unless contract-documented; `app.decryption.auto-decrypt-on-keyword-search` applies to match logic only and must not contradict no-plaintext-in-search-response (align contract if needed).
- **Risks**
  - **Plaintext leakage via search API:** If decrypt-for-match populates response DTOs with decrypted strings, **PII** may leave the server without an audit trail equivalent to the **explicit decrypt** path.
  - **Logging:** Diagnostic or info logs must **not** print decrypted payloads or keyword fragments from decrypt-for-match.
  - **Authorization bypass:** Any new flag or internal field (e.g. match hints) must **not** allow the client to **force** plaintext without `FUNCTION_NOT_ALLOWED` / `DECRYPTION_NOT_APPROVED` / `ROW_NOT_IN_APPROVED_SNAPSHOT` behavior where applicable (see `search-history-decrypt-domain` skill).
  - **Configuration:** `app.decryption.auto-decrypt-on-keyword-search` (see `docs/contract.md`) must be interpreted so it **does not** override this requirement’s **no-plaintext-in-search-response** rule unless product explicitly documents an exception (if conflict, **this requirement’s explicit gating wins** until Contract is updated).
- **Acceptance / recommendations**
  - Treat **search** and **explicit decrypt** as **separate trust boundaries**: decrypt-for-match may run **only** in a scope that **cannot** assign client-visible plaintext fields for list/search responses.
  - **Strip or never populate** `decrypted_data`, `decrypted_header`, `decrypted_datastring`, `decrypted_headerstring` (and any equivalent keys) on **search** responses unless the operation is **explicitly** the decrypt/detail endpoint (or a documented exception approved in Contract).
  - Preserve **audit** on **POST `/api/logs/decrypt/{logType}`** as the primary path that exposes full decrypted payload to authorized users.
  - Re-verify **GET `/api/decrypt/allowed`** and **POST decrypt** behavior remain aligned with **`20260318`** / **`20260320`** composite keys.

### Technical design

#### Codebase summary (investigation)

- **`LogDbService` (Image Log search):** For `java_fw_imglog`, **datastring / headerstring / keyword** filters use **application-level filtering** after fetch (`filterImageLogRowsByDataHeaderKeywords`). Matching may call **`decryptJsonStringValues`** on JSON with `[...]` encrypted segments. Outward JSON should expose optional **`hasEncryptedMatchDatastring`** / **`hasEncryptedMatchHeaderstring`** (contract); legacy internal **`_datastring_has_encrypted_match`** / **`_headerstring_has_encrypted_match`** must not appear on the wire once implementation is aligned (DOC–CODE–SYNC).
- **Column scope (clarified):** **`filterImageLogRowsByDataHeaderKeywords`** never consults the **`data`** or **`header`** columns for **`hasDatastringSearch`**, **`hasHeaderstringSearch`**, or **`hasKeywordsSearch`** — only the **`datastring`** and **`headerstring`** text columns (plus bracket decrypt on those strings). Decrypt-for-match against binary **`data` / `header`** is **out of scope** for this requirement unless a future product decision extends it.
- **`readImageLogResultSet`:** When **`decryptData`** is true **and** **`keywords`** is non-empty, the method may **decrypt** the **`data` / `header`** column payloads and put **`decrypted_data` / `decrypted_header`** on the row map — which can surface **full binary payload plaintext** in the **search** response without a separate user **explicit decrypt** action. *(This requirement’s outcome is to stop that leakage on search; it does not by itself add **`data` / `header`** decrypt-for-match into **`filterImageLogRowsByDataHeaderKeywords`**.)*
- **Binary `data` / `header` columns:** Any future match logic that decrypts **`data` / `header`** for filtering must **still** comply with §1: match-only decrypt **must not** populate client-visible plaintext fields on **`POST .../search`**.
- **Related domain:** Decryption approval, decryption-allowed store, and **`DECRYPTION_NOT_APPROVED`** semantics per **`search-history-decrypt-domain`** skill and **`docs/contract.md`**.

#### Problem analysis

1. **Coupling of “match” and “display”:** Server-side decrypt used for **matching** is **merged** with **response assembly**, so the client may receive **decrypted** fields during **search**, violating **minimum disclosure** and explicit decrypt/approval expectations.
2. **Ambiguous API semantics:** `decryptData` on **`LogDbSearchRequest`** (see `docs/api-definition.md`) may be interpreted as “decrypt in search results,” which **collides** with the product rule: **decrypt-for-display** only after **approval + explicit action**.
3. **Match hint wire names:** Encrypted-region highlight hints are **contract-documented** as **`hasEncryptedMatchDatastring`** / **`hasEncryptedMatchHeaderstring`** (optional booleans, not plaintext); **`_*_has_encrypted_match`** is not a valid outward key once implementation follows Contract.

#### Diagnostic phase (mandatory for error/bug fix only)

*Not applicable — feature / security-behavior requirement (not an error-fix-only doc).*

#### Solution approach

Structure by scope for handoff.

**Frontend:**

- **Must** render **encrypted / masked** values in the **Image Log grid** for fields covered by this requirement **after search**, and **must** load **plaintext** only via **explicit decrypt** UX (button/modal) that calls **`POST /api/logs/decrypt/java_fw_imglog`** (or contract-successor) when **allowed**.
- **Must not** display plaintext taken **only** from **`POST .../search`** response fields that are **deprecated** for plaintext by this requirement.
- **Verify** parity with **`20260317-search-decrypt-permission-ui`**: decrypt controls remain gated by **`screenFunctions.main.decrypt`** (or **system admin**).

**Backend:**

- **Must** **separate** (a) **decrypt-for-match** from (b) **serialization of search results**.
- **Must** ensure **`POST /api/logs/db-refactored/search`** response rows for `java_fw_imglog` do not expose plaintext produced solely for matching or from **`readImageLogResultSet`**’s **`decryptData` + keywords** branch unless Contract explicitly allows (default: **must not**).
- **Must** serialize display-only match hints as documented **`hasEncryptedMatchDatastring`** / **`hasEncryptedMatchHeaderstring`** (optional booleans) and **must not** emit **undocumented** **`_*`** keys on search responses (DOC–CODE–SYNC with `docs/contract.md`).
- **Align** with **`app.decryption.auto-decrypt-on-keyword-search`**: behavior must satisfy **this requirement’s** non-leakage rule; if tension exists, **Contract** and **`docs/api-definition.md`** must be updated in the same change set.
- **Optional extension (not part of this requirement until product confirms):** If product requires **「데이터」**-style search to match **`LOCAL`** (or similar) from decrypted **`data`** / **`header`** payloads, implement **decrypt-for-match** on those columns in the filter pipeline **without** attaching **`decrypted_data` / `decrypted_header`** (or equivalents) to search responses; update **Contract**, **UI copy** (field scope), and **`log-search-domain`** skill so users know which box searches which column set.

**Contract / spec:**

- **Must** document the **final** allowed keys on **`LogDbSearchResponse`** rows for **`java_fw_imglog`** and the meaning of **`decryptData`** after this change (`docs/contract.md`, `docs/api-definition.md`, `specs/*` as applicable per **DOC-CODE-SYNC**).

**DB:**

- **None** expected unless audit or storage of match-only artifacts is required (default: **none**).

**Cursor tool update targets**

- **Skills:** `.cursor/skills/search-history-decrypt-domain/SKILL.md`, `.cursor/skills/log-search-domain/SKILL.md` — update **after** implementation so search vs decrypt boundaries stay accurate.

### Affected scopes and change targets (verification)

| Scope | Affected? | §2 subsection and change file list complete? |
|-------|------------|-----------------------------------------------|
| Backend | Yes | Yes |
| Frontend | Yes | Yes |
| DB | No | N/A |
| Contract / Spec | Yes | Yes |
| Cursor tools (skills, specs) | Yes | Yes |

### Planned change file list (expected change targets)

**(Planned at authoring. Implementing agent (Step 4) confirms or amends this list when implementation is complete.)**

#### Frontend

- Image Log search / grid components under `frontend/src/` that bind **`POST .../search`** row fields (e.g. datastring display, decrypt button visibility, Pretty/detail views).
  - **Must** stop treating **search response** plaintext fields as authoritative when this requirement removes or masks them; **must** use **explicit decrypt** API for plaintext.

#### Backend

- `backend/src/main/java/com/logmng/service/LogDbService.java` (Image Log search, **`readImageLogResultSet`**, **`filterImageLogRowsByDataHeaderKeywords`**, response assembly).
- Related controller/DTO mapping for **`LogDbSearchResponse`** row maps if keys are formalized or stripped.
- **Tests** under `backend/src/test/java/` covering §3 automated cases.

#### Contract / documentation

- `docs/contract.md` — decryption/search configuration text if behavior of **`app.decryption.auto-decrypt-on-keyword-search`** or search vs decrypt boundaries changes.
- `docs/api-definition.md` — **`LogDbSearchRequest`** / response row fields for **`java_fw_imglog`**.

#### DB

- None unless an audit requirement is added (TBD only if Security/Contract mandates).

---

## 3. Test approach

### Test case list (required)

**api-permission-map / decrypt completeness:** Include cases for **403** / **`FUNCTION_NOT_ALLOWED`** where relevant, and **`DECRYPTION_NOT_APPROVED`** / snapshot errors on decrypt when approval is missing (per **`search-history-decrypt-domain`**).

| ID | Scope | Type | Scenario (input / condition) | Expected result | Verification (unit / integration / manual) |
|----|-------|------|------------------------------|-----------------|--------------------------------------------|
| TC-01 | Backend | Normal | Image Log **search** with a **datastring** (or keyword) value that matches **only** inside bracket-decrypted JSON (ciphertext in DB), user has search permission | Row appears in **search result** (`totalCount` / row id + identifiers per contract); **`POST .../search` response body must not** contain **plaintext** decrypted fields forbidden by §1 (e.g. no `decrypted_data` / `decrypted_header` / `decrypted_datastring` populated **solely** for match unless Contract explicitly allows — default: **must not** include such plaintext). | Unit or integration (`mvn test` / documented HTTP + assertion on JSON keys) |
| TC-02 | Backend | Normal | Same as TC-01 but with **`decryptData: true`** and **keywords** non-empty (legacy trigger for **`readImageLogResultSet`** decrypt branch) | Response **still must not** leak **full** decrypted **`data`/`header`** payloads on **search**; align with updated Contract. If feature is removed, expect **no** plaintext keys — **verify** with JSON assertions. | Integration (`mvn test` or scripted API call) |
| TC-03 | Frontend | Normal | After TC-01 search, open **Image Log grid** | **Grid** shows **encrypted / masked** form for protected columns (not plaintext from search-time decrypt). | Manual / browser (or component test if stable) |
| TC-04 | Integration | Normal | User **with** decrypt permission and **approved** decryption-allowed row | **Per-row decrypt** (explicit action) returns **plaintext** via **`POST /api/logs/decrypt/java_fw_imglog`**; UI shows plaintext **only** after this path — **not** from search alone. | Manual / browser + API capture |
| TC-05 | Integration | Exception | User **without** approval (or row not in allowed set) attempts **explicit decrypt** | **`DECRYPTION_NOT_APPROVED`** or **`ROW_NOT_IN_APPROVED_SNAPSHOT`** (per contract); **no** decrypted payload in response. | Integration |
| TC-06 | Frontend | Exception | User **without** `screenFunctions.main.decrypt` (non-admin) | **Decrypt** UI **hidden or disabled** per **`20260317-search-decrypt-permission-ui`**; **no** plaintext from alternate UI path. | Manual / browser |
| TC-07 | Backend | Edge | Search match touches **`data`** column keyword behavior (if implemented) | **No** plaintext **`data`/`header`** in **search** response unless explicit decrypt path; document actual behavior in Contract if product adds partial match helpers. | Unit / integration per implementation |
| TC-08 | Backend | Edge | Response row may contain **internal** match flags during processing | **Outgoing JSON** must **not** expose undocumented internal keys (or only documented non-PII flags if product agrees). | Unit (serialization) |
| TC-09 | Backend | Normal / scope | Given seed **IM-0001** (`LOCAL-DECRYPT-TST-IM-0001`), search term **`LOCAL`** in API **`datastring`** only (UI **「데이터」**) | **Today (this requirement’s scope):** row **must not** be required to appear — match is **datastring-only**; decrypted **`data`** is **not** scanned. **If** product extends scope: row **may** match via **decrypt-for-match** on **`data`** (or agreed column set) **without** **`decrypted_data`** / **`decrypted_header`** in **`POST .../search`** JSON. | Unit / integration (`LogDbServiceTest` or HTTP + JSON key assertions); document branch in §5 |
| TC-10 | Backend | Regression | Given seed **IM-0001**, search term **`LOCAL`** in **`keywords`** (non-empty array) | Row **matches** via **plaintext substring** in **`headerstring`** (contains **`LOCAL-DECRYPT-TST-IM-0001`**) per current implementation; response **still** has **no** forbidden plaintext keys from §1. | Integration |

### Test scenarios

#### Scenario 1: Encrypted match without plaintext in search API

1. Seed or pick a row where plaintext match exists **only** after JSON bracket decrypt.
2. Call **`POST /api/logs/db-refactored/search`** with `logType` **`java_fw_imglog`** and matching criteria.
3. Assert row is returned and **forbidden** plaintext keys are absent or empty per §2.

#### Scenario 2: Approval and explicit decrypt

1. User obtains **APPROVED** search history and **decryption-allowed** entry for **`(guid, status)`**.
2. User triggers **explicit decrypt** from UI.
3. Assert **plaintext** appears **only** from decrypt API response, not from search.

### Test data

- Use or extend existing **`java_fw_imglog`** fixtures with **bracket-encrypted** `datastring` / `headerstring` (see **`20260318-image-log-search-data-header-keyword-fix`** and seed scripts if present).
- Document **GUID + status** pairs per **`20260320-imagelog-guid-status-composite-key`**.

### Test environment

- Frontend: `http://localhost:3001` (per contract / project defaults)
- Backend: `http://localhost:9200`
- Database: PostgreSQL per `docs/contract.md` (ImageLog datasource as configured)

### 3.5 Browser automation verification (optional)

- **Applicable TCs:** TC-03, TC-04, TC-05, TC-06
- **Procedure:** Login → Image Log search → assert grid ciphertext → exercise decrypt button vs approval states → snapshot/compare network responses for **`/search`** vs **`/decrypt`**.
- **Reference:** `docs/workflow/BROWSER-AUTOMATION-VERIFICATION-POLICY.md`

---

## 4. Checklist

### Frontend verification

- [ ] API parameters validated
- [ ] UI behavior confirmed (grid shows encrypted; plaintext only after explicit decrypt + approval)
- [ ] Error handling verified (`FUNCTION_NOT_ALLOWED`, decrypt approval errors)

### Backend verification

- [ ] API test cases written and run (§3)
- [ ] Logs checked (no decrypted payload at INFO in search path)
- [ ] Performance checked if prefetch cap (`IMGLOG_FILTER_PREFETCH_CAP`) is stressed

### Integration

- [ ] End-to-end flow tested (search → approval → decrypt)
- [ ] Edge cases tested (TC-07, TC-08)

### Documentation

- [ ] Requirement doc completed
- [x] Contract / api-definition updated in sync with behavior (`docs/contract.md`, `docs/api-definition.md`, 2026-04-13)

---

## 5. Test results

### Test run date

- 2026-04-13 (automated); manual §3.5 / TC-04–TC-06 browser verification optional for QA

### QA verdict

**Question:** User searches `LOCAL` expecting row `LOCAL-DECRYPT-TST-IM-0001`; the row does not appear. Is requirement `20260413` wrong, or is the backend wrong?

**Answer:** **Not a backend defect** for the keyword / `headerstring` path, and **not a contradiction** in `20260413` for the `datastring`-only path. §1 already clarifies: `LOCAL` in 「데이터」 (API `datastring` only) does **not** match IM-0001; **keywords** can match plaintext substring in **`headerstring`**. Automated checks below match that contract.

**Likely user cause:** **`LOCAL` was entered in 「데이터」** (`ImageLogSearchForm.js` maps that field to **`datastring` only** — not binary `data` decrypt-for-match). **Recommendation:** use **keyword search** with `LOCAL` (or a follow-on requirement if product needs 「데이터」 to imply `data`-column decrypt-for-match without plaintext in `POST .../search`).

### Test results

#### Frontend

- `cd frontend && npm test -- --watchAll=false` — **pass** (39 suites, 259 tests; per Frontend implementer run)

#### Backend

- `cd backend && mvn test -Dtest=LogDbServiceTest` — **pass** (2026-04-13; includes TC-01/TC-02 style assertions for search response keys).

**TC-09 / TC-10 evidence (`LogDbServiceTest`, same run):**

| Test method | Result | Notes |
|-------------|--------|--------|
| `searchJavaFwImglog_keywordLocal_matchesHeaderGuidLikeSeedIm0001` | **PASS** | `keywords` with `LOCAL` returns a row consistent with seed IM-0001 (match via `headerstring`-like surface). |
| `searchJavaFwImglog_datastringLocal_doesNotMatchSeedIm0001LikeRow` | **PASS** | `datastring` only `LOCAL` returns **0** rows for the same fixture (no match on `datastring`-only path). |

**Commands:**

```bash
cd backend && mvn test -Dtest=LogDbServiceTest
cd frontend && npm test -- --watchAll=false
```

**Outcome:**

- Backend: `sanitizeJavaFwImglogSearchRow` strips `decrypted_*` and `_`-prefixed keys; `readImageLogResultSet` no longer attaches `decrypted_data`/`decrypted_header` for search; `advancedSearch` aligned.
- Frontend: grid/modal do not use search-row `decrypted_*` for display; `decryptData` label/help updated; `LogGrid` preserves `decryptData` on re-search.

### Issues found and resolution

- Full `mvn test` may require local DB/env for integration tests — run in CI or with project `.env` when validating entire backend suite.

### QA verification notes

- **§3 TC-09 / TC-10** are covered by `LogDbServiceTest` with explicit method names recorded in the table above (run date **2026-04-13**).
- **UI wire:** the 「데이터」 control submits **`datastring` only** (`frontend/src/components/ImageLogSearchForm.js`); it does **not** map to binary `data` column search. Align user expectations with **keyword** vs **데이터** field scope, or track UX/product copy in a follow-on requirement.

### Next steps

1. ~~Security formal review of §2.1~~ — Done (see §2.1 checkbox)
2. ~~Contract update for `decryptData` and search response keys~~ — Done
3. ~~Backend / Frontend implementation~~ — Done (2026-04-13)
4. QA: optional Review subagent pass; manual decrypt approval flow (TC-04/TC-05); add §7 Korean final version when product signs off

---

## 6. Error remedy result (cause and action) — for error/bug fix requirements only

N/A

---

## 7. Final version (Korean) — add after all verification is complete

### Final Korean summary

- TBD
- **(Clarification 2026-04-13)** 사용자가 **「데이터」**에 `LOCAL`을 넣었을 때 IM-0001이 안 나오는 것은, 해당 필드가 API **`datastring`만** 검사하고 **`data` 컬럼 복호화 매칭**은 원 요구사항 범위에 없었기 때문이다. `LOCAL` 문자열은 **키워드** 등 **`headerstring`이 검사되는 경로**에서는 시드 기준으로 매칭될 수 있다. **`data`/`header`까지 검색**하려면 별도 제품 결정과 후속 요구사항이 필요하다.

---

**Author**: Requirements  
**Date**: 2026-04-13  
**Status**: In progress  
**Amendment**: 2026-04-13 — §1/§2/§3 clarification for **`datastring`** vs **`data`/`header`** column scope and **`LOCAL`** seed expectations (TC-09, TC-10).
