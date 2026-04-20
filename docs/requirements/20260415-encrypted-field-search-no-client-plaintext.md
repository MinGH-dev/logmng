# 20260415 - Encrypted field search without client-side plaintext exposure

## 1. User requirement

### Summary (user language — Korean intent)

- **데이터·헤더 등 민감 필드**는 DB·화면에 **암호문(또는 제품이 정한 마스킹/표현)**으로 남아야 하며, 사용자는 **평문이 보이지 않는 상태에서도** 검색어가 **암호화된 페이로드 안에 포함된 경우** 해당 행을 **찾을 수 있어야** 한다.
- 화면에는 민감 필드가 **오늘과 같이 암호화된 형태**로 표시되며, **제품 규칙·복호화 승인·명시적 복호화** 없이 **평문이 그리드/목록에 노출되면 안 된다.**
- **브라우저 개발자 도구**(Network 응답 본문, Console, Application/Storage 등)를 통해 **평문 PII가 유출되면 안 된다.** 구현자는 API 계약·직렬화·프론트 로깅·디버그 플래그까지 **엄격한 비노출 제약**을 따라야 한다.

### Requirement description

The product must support **search that can match user-entered terms against content that exists only inside **encrypted-at-rest** string payloads** (for example bracket-wrapped JSON ciphertext segments in `datastring` / `headerstring`, and analogous patterns on other log types where the product defines decrypt-for-match). Users expect **rows to be returned when the match exists only after server-side decryption of those segments for matching purposes**, without requiring that plaintext be visible in the UI during search.

**Display rule:** On list/grid and default search surfaces, **sensitive fields must remain shown as ciphertext or an agreed encrypted representation** (same as today or as specified by product/security), until the user follows the **explicit decrypt path** (permission, approval, and dedicated decrypt APIs) where applicable.

**Strict client exposure rule (security + UX for implementers):** **Developer tools must never show plaintext** for this class of data as a consequence of search or normal UI operation. In practice this implies: **HTTP API JSON bodies** consumed by the browser **must not carry decrypted PII** for “search / list” operations; **console logging**, **diagnostic dumps**, and **client-side storage** must not embed decrypted payloads. Any exception must be **explicitly** documented in contract/security review—not assumed.

### User scenario

1. An operator enters search criteria that should match a value that appears **only after decrypting an encrypted fragment** inside a stored string column (not necessarily visible as plaintext in the grid).
2. The system returns **the correct rows** in the search result set.
3. The **grid still shows ciphertext** (or masked/encrypted display) for those columns; the operator does not see full plaintext **solely because search ran**.
4. If the operator needs plaintext, they use the **existing decrypt / approval workflow** (per `docs/contract.md` and related requirements).
5. A developer opens **Chrome DevTools → Network** and inspects the search response; **no decrypted PII** appears in JSON for fields covered by this requirement (or values are **masked** per contract where masking is the approved transport).
6. **Problem addressed:** Ambiguity between “server may decrypt to decide a match” and “client may receive or log plaintext,” and gaps where **diagnostics or UI helpers** could leak sensitive content into DevTools surfaces.

### Expected outcome

- **Server-side `decrypt-for-match` (or equivalent)** may be used **only** to evaluate whether a row satisfies search predicates, **without** turning search/list API responses into a channel for **decrypted PII**.
- **UI:** Result cells show **ciphertext / encrypted representation** by default; **highlighting** of “where it matched” must **not** introduce **plaintext into the DOM** if avoidable—prefer **non-sensitive hints** (for example contract-documented boolean flags, ciphertext-span styling, or offsets that do not reveal decrypted content). If product absolutely requires a hint that could infer sensitive content, **Security** must sign off in §2.1.
- **DevTools / browser exposure:** Search and list flows **must not** place decrypted sensitive payloads in:
  - Network response JSON (except documented masked fields or dedicated decrypt endpoints),
  - `console.log` / error objects in production builds,
  - `localStorage` / `sessionStorage` / IndexedDB,
  - or other observable client surfaces under normal operation.
- **Diagnostics:** Any temporary logging of sensitive material during development **must** be behind **dev-only flags** or **DEBUG**-level logging that is **off in production**, per project error-fix and security rules.
- **Contract alignment:** `docs/contract.md`, `docs/api-definition.md`, and DOC–CODE–SYNC remain the single source of truth; any new flags or shapes are **documented**, not ad hoc.

**Note:** Numeric/layout standards for search forms are **referenced from design docs** (`docs/design/search-fields-by-screen.md`, `docs/design/search-field-definition-items.md`) when this requirement touches form fields; this doc does **not** redefine layout unless explicitly extended.

## 2. Design

### 2.1 Security review (PII / decryption / access control)

**Follow-up:** Because this requirement governs **decryption-for-matching**, **PII minimization**, and **browser-exposed API shapes**, the **Security** subagent should review §2.1 and the **Technical design** subsections on API responses and diagnostics **before implementation is considered complete**.

- [ ] Security review performed
- **Risks:** Plaintext in search responses; plaintext in logs; accidental `console.log` of row maps; feature flags that enable verbose client logging in production; inference attacks from overly rich match metadata.
- **Acceptance / recommendations (to be confirmed by Security):** Treat **search** and **explicit decrypt** as distinct trust boundaries; audit expectations for decrypt endpoints unchanged; minimize match metadata to non-sensitive booleans or ciphertext-safe presentation; prohibit production emission of decrypted match strings on the wire.

### Technical design

#### Relationship to existing requirements (delta summary)

| Existing doc | What it already established | How this doc extends it |
|--------------|----------------------------|---------------------------|
| `20260413-imagelog-search-decrypt-display-separation` | For `java_fw_imglog`, **decrypt-for-match** vs **no plaintext on `POST .../search`**; optional **`hasEncryptedMatchDatastring` / `hasEncryptedMatchHeaderstring`** as non-plaintext hints | Adds **explicit product-wide implementer rules** for **browser DevTools**, **console/storage**, and **diagnostic logging**; clarifies **DOM highlight** must not leak plaintext when avoidable |
| `20260413-imagelog-unified-text-search`, `20260414-imagelog-keyword-or-field-and-ui` | Unified text search semantics, keyword vs field combinators, binary column decrypt-for-match where defined | **No change** to boolean/search algebra here; this doc **does not** redefine AND/OR merge—only **client exposure and display safety** |
| `20260414-image-log-datastring-headerstring-independent-matching-fix` | **Field-independent** routing: `datastring` vs `headerstring` inputs must not cross-leak | **Orthogonal:** field routing fixes remain governed by that doc; this doc still applies **no client plaintext** regardless of which field path matched |
| `20260206-privacy-security-improvement` (summary) | Frontend must avoid logging decrypted fields | **Reinforces** for **search + encrypted match** flows and **Network tab**; adds **testable criteria** |

If future log types (for example PB FEP) add **decrypt-for-match**, they **must** inherit the same **separation**: match logic may use decryption internally; **list/search JSON to the browser** stays free of decrypted PII unless Contract explicitly documents an exception.

#### Problem analysis

1. **Semantic gap:** “Server decrypts to match” is sometimes implemented in a way that **reuses DTOs** or **row maps** that also populate **decrypted_* keys** for the client.
2. **Client leakage:** Even when APIs are correct, **frontend debugging** or **React dev-only** paths may log **full row objects**.
3. **Highlight vs privacy:** UI may attempt to show “what matched” by **injecting decrypted substrings** into the DOM—violating **minimum disclosure** for search surfaces.
4. **DevTools are part of acceptance:** Operators and auditors may inspect Network; **security acceptance** must include **no plaintext in search JSON**.

#### Diagnostic phase (mandatory for error/bug fix only)

*Not applicable as a standalone error-fix doc. If work is triggered by a specific leakage bug, follow the **error-first** diagnostic phase in the project workflow and record under §6 when used.*

#### Solution approach

**Backend:**

- **Decrypt-for-match only:** Decryption used for **filtering/matching** must occur in a code path that **does not** serialize decrypted sensitive values onto **`LogDbSearchResponse` / row maps** for standard search endpoints.
- **Response shape:** Return only **ciphertext columns** (and **contract-approved** optional booleans or **masked** fields). **Do not** add decrypted payload fields to search responses “for convenience.”
- **Logging:** No decrypted content from match paths in INFO/WARN; DEBUG gated per Security and project rules.
- **Contract:** Update `docs/contract.md` / `docs/api-definition.md` if new non-sensitive flags are introduced.

**Frontend:**

- **Render ciphertext** in grids from API fields; do **not** derive or fetch plaintext for display via search API.
- **Highlight:** Use **flags** or **styling on ciphertext** (or non-revealing indicators). Avoid placing **decrypted match excerpts** in the DOM for search results unless Security-approved and Contract-documented.
- **DevTools:** No `console.log` of full API responses containing sensitive keys in production bundles; avoid persisting row payloads to storage.
- **Dev-only diagnostics:** Any verbose logging behind `NODE_ENV === 'development'` or explicit **local** flags—**never** default-on in production.

**DB:**

- No mandatory schema change for this requirement; **verify** no new columns are required for “match only.”

### Affected scopes and change targets (verification)

| Scope | Affected? | §2 subsection and change file list complete? |
|-------|------------|-----------------------------------------------|
| Backend | Yes (serialization, match path boundaries, logging) | [ ] |
| Frontend (search grid, logging, highlight) | Yes | [ ] |
| DB | Likely No | [ ] |
| Contract / Spec | Yes (wire shape, masked fields) | [ ] |
| Cursor tools (skills, specs) | Yes if API or domain behavior changes | [ ] |

### Planned change file list (expected change targets)

**(Planned at authoring. Implementing agent (Step 4) confirms or amends this list when implementation is complete.)**

#### Frontend

- `frontend/src/components/**` (Image Log / log search grid and helpers as applicable)
  - Ensure list rendering and highlight paths do not inject plaintext from search; remove unsafe logging.

#### Backend

- `backend/src/main/java/com/logmng/service/LogDbService.java` (and related DTO/serialization for search responses)
  - Enforce **no decrypted sensitive fields** on search responses; align match-only decrypt scope with serialization layer.

#### Contract / docs

- `docs/contract.md`, `docs/api-definition.md`
  - Document allowed response fields for search; explicitly forbid decrypted PII keys on search endpoints unless Security-approved exception.

#### DB

- None unless a separate requirement introduces schema for match metadata (not expected here).

## 3. Test approach

### Test case list (required)

**Domain note:** Apply **`log-search-domain`** and **`search-history-decrypt-domain`** skills for cross-checks on search vs decrypt endpoints.

| ID | Scope | Type | Scenario (input / condition) | Expected result | Verification |
|----|-------|------|-------------------------------|-----------------|--------------|
| TC-01 | Backend | Normal | Search where match exists **only** inside bracket-encrypted JSON in `datastring` and/or `headerstring` (per seed data such as `LOCAL-DECRYPT-TST-IM-0001` family) | Row appears in result set | Unit / integration (`mvn test`) |
| TC-02 | Backend | Normal | Inspect serialized **search** response DTO / JSON for the row | **No** keys containing **decrypted plaintext** for protected columns (e.g. no `decrypted_datastring` / `decrypted_headerstring` / `decrypted_data` / `decrypted_header` populated from match-only paths on search) | Unit or integration assertion on response map |
| TC-03 | Integration | Normal | `POST` search (contract path for `java_fw_imglog`) | Response body matches contract: ciphertext fields + optional **boolean** hints only | Integration (curl or REST test) |
| TC-04 | Frontend | Normal | Grid after search | User sees **encrypted/masked** column text as today; **no** new plaintext column from search alone | Unit (`npm test`) + manual/browser |
| TC-05 | Frontend | Security | Production build / staging | **No** `console.log` of full row objects with sensitive keys in bundled code path used for search (static review or lint rule where available) | Manual / CI grep (if configured) |
| TC-06 | Manual | Security | Browser DevTools → **Network** → search XHR | Response JSON **does not** contain decrypted PII for search-listed fields | Manual |
| TC-07 | Manual | Edge | **Application** tab / **localStorage** after search session | No full decrypted payloads stored from search flow | Manual |
| TC-08 | Backend | Edge | DEBUG logs in match path | No decrypted PII at INFO; DEBUG only per policy | Code review / log capture in dev |

### Test scenarios

#### Scenario 1: Encrypted-only match returns row, API stays ciphertext-safe

1. Prepare data where plaintext match exists only after decrypt-for-match.
2. Execute search.
3. Assert row is returned; assert response JSON has **no** decrypted sensitive fields.

#### Scenario 2: DevTools inspection

1. Log in, run search.
2. Open Network, select search request, view Response.
3. Confirm **no plaintext PII** for fields covered by this requirement.

### Test data

- Use existing **LOCAL** imagelog seed rows and generators referenced in `20260413-imagelog-search-decrypt-display-separation` § “LOCAL example” where applicable.
- When adding new fixtures, provide **executable SQL** or generator references in implementation notes for QA.

### Test environment

- Frontend: `http://localhost:3001` (or per project default)
- Backend: `http://localhost:9200`
- Database: PostgreSQL (per project setup)

### 3.5 Browser automation verification (optional)

- **Applicable TCs:** TC-04, TC-06 (partial—Network body may require manual confirm depending on automation limits).
- **Procedure:** Navigate to Image Log search → submit → snapshot grid → optional Network capture per `docs/workflow/BROWSER-AUTOMATION-VERIFICATION-POLICY.md`.

## 4. Checklist

### Frontend verification

- [ ] API parameters validated
- [ ] UI shows ciphertext for sensitive columns after search
- [ ] No plaintext leakage via console/storage

### Backend verification

- [ ] Search response serialization tests cover TC-02
- [ ] Logs reviewed for production safety

### Integration

- [ ] End-to-end: search hit + Network JSON review (TC-06)

### Documentation

- [ ] Requirement doc completed
- [ ] Contract updated if response rules change

## 5. Test results

### Test run date

- Pending

### Test results

Pending QA.

**Commands:**

To be filled with **one executable command per TC** after implementation (per template).

## 6. Error remedy result (cause and action)

*Not applicable unless this doc is used to track a specific leakage bug; use `docs/template/ERROR_FIX_RESULT_TEMPLATE.md` if needed.*

---

## 7. Final version (Korean)

**Status:** Draft summary for stakeholders. Per `docs/workflow/DOCUMENT-LANGUAGE-POLICY.md`, the **final** Korean block may be refreshed after §5 verification completes.

### Draft Korean summary

- **요구사항 요약:** 암호화된 문자열 안에만 존재하는 검색어도 **서버에서 매칭용 복호화**로 찾을 수 있어야 하며, **목록·검색 응답과 화면·개발자 도구**에서는 **평문 PII가 노출되면 안 된다.** 하이라이트 등 UX는 **평문 DOM 삽입 없이** 가능한 범위에서 처리하고, 예외는 보안 검토·계약 문서로만 허용한다.
- **기대 결과:** 검색 API JSON에 복호화된 민감 필드가 실리지 않고, 프론트는 암호문 표시를 유지하며, Network/Console/Storage에서 평문이 보이지 않는다. 개발용 진단 로그는 플래그/DEBUG로만 제한한다.
- **검증 결과:** §5 완료 후 기재.

---

**Author:** Requirements (subagent)  
**Date:** 2026-04-15  
**Status:** In progress  
