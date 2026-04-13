# 20260414 - Image log: keyword OR clause, field AND clause, and remove match-decrypt checkbox

## 1. User requirement

### Requirement description

Operators searching **Java FW Image Log** (`java_fw_imglog`) need predictable text matching:

1. **Keywords (`keywords`)** — When the user enters **comma-separated keywords** (or the equivalent list the UI sends), matching must be **OR across keyword tokens**: a row satisfies the **keyword clause** if **any** single keyword token matches.
2. **Keyword token match surfaces (union, includes encrypted payload)** — Product intent treats keyword search as the **union** of “data + header” text matching **including** content that exists **only** inside the encrypted **binary** `data` / `header` column payloads (decrypt for match using the same **`IMAGE_LOG`** policy as the explicit decrypt API path). For **each** keyword token, a row matches that token if **either** **(A)** or **(B)** holds:
   - **(A)** **Current keyword surfaces on string columns** — the same predicate as today on **`datastring`** / **`headerstring`** only: plaintext substring **or** bracket-JSON **decrypt-for-match** (`decryptJsonStringValues`) on those columns (unchanged).
   - **(B)** **Binary payload decrypt-for-match (in-memory only)** — for non-null **`data`** and/or **`header`** column values, after in-memory `cryptoUtil.decryptLogPayload(..., IMAGE_LOG)`, the decrypted plaintext **contains** the keyword (substring). Decrypt failure for a column → that column contributes **no** match for that token (search must **not** throw). **Never** attach `decrypted_data`, `decrypted_header`, or other match-only plaintext to search response rows (inherits **`20260413-imagelog-search-decrypt-display-separation`**). Optional response flags (e.g. `hasEncryptedMatchData` / `hasEncryptedMatchHeader`) are allowed for grid highlight parity if added; strip internal temporaries.
3. **Field form fields unchanged** — The dedicated **`datastring`** / **`headerstring`** form fields and their **field clause** semantics and **code paths** stay **exactly** as now: plaintext substring and bracket-JSON decrypt-for-match **on `datastring` / `headerstring` only** — **no** scan of binary **`data` / `header`** columns for field-derived terms. **Only** the **keyword clause** may be extended per §2.2.
4. **No regression on field-only search** — After the prior fix, **`datastring`**-only (and the unified **field** path using `datastring` / `headerstring`) behavior is **already correct**. This change **must not regress** when the user fills **only** `datastring`, **only** `headerstring`, or **both** `datastring` and `headerstring` with their terms (see §2 for the exact **field clause**).
5. **Combining field terms and keywords** — When the user provides **both**:
   - **Field-derived terms** from `datastring` and `headerstring` (non-empty trim, merged into one list with **case-insensitive dedup**), **and**
   - **Keyword tokens** from `keywords` only,

   then a row must satisfy **(field clause) AND (keyword clause)**:
   - **Field clause** (unchanged vs current intended behavior): **AND** across the effective **field** terms — i.e. **each** distinct field-derived term (after dedup) must match via **`javaFwImglogTermMatchesForFilter`** equivalent only (**`datastring` / `headerstring`** plaintext substring or bracket-JSON decrypt-for-match on those columns; **no** binary **`data` / `header`** scan).
   - **Keyword clause**: if keywords are **empty** after trim/split rules → **no keyword filter**; if **non-empty** → **OR** across keyword tokens (each token: **(A) OR (B)** per §1 item 2 / §2.2).
6. **UI** — Remove the Image Log form control labeled **「매칭용 복호화」** (match-only decrypt opt-in). Users must **not** have to opt in for bracket-JSON decrypt-for-match: match behavior remains **server-side** without that UX gate.

**Relationship to prior requirements:** This **refines** text semantics relative to **`20260413-imagelog-unified-text-search`**, which merged `datastring`, `headerstring`, and `keywords` into a single **`effectiveTerms`** list with **AND across all** terms. The product now requires **separate** **`keywordTerms`** (OR) vs **`fieldTerms`** (AND), combined with **AND** between the two clauses when both are non-empty. The **keyword** clause further **extends** match to **binary** **`data` / `header`** via in-memory **`IMAGE_LOG`** decrypt (§2.2) while **field** terms remain string-column-only. **`20260413-imagelog-search-decrypt-display-separation`** constraints (no search-response plaintext from match-only work) **remain in force**.

**Field definitions:** Search field labels and screen placement follow existing design references for this screen (e.g. `docs/design/search-fields-by-screen.md`, `docs/design/search-field-definition-items.md`) where applicable; this document defines **matching semantics**, not new field names.

### User scenario

1. User enters keywords **`A,B`** (two tokens). A row that contains **`B`** on any allowed keyword surface (per §1 item 2) but not **`A`** must **match** (keyword OR).
2. User enters **`datastring`** = text that requires substring **`X`**, and keywords **`Y,Z`**. A row that has **`X`** but neither **`Y`** nor **`Z`** must **not** match. A row that has **`X`** and **`Y`** (or **`Z`**) must **match**.
3. User fills **only** `datastring` (or only `headerstring`, or both) **without** keywords — behavior stays **as after the prior field unification fix** (no new false negatives vs that baseline).
4. User searches with a keyword that appears **only** in decrypted binary **`data`** or **`header`**, while **`datastring`** / **`headerstring`** do **not** contain that substring — the row **must** match when using **keywords**; the same substring via **`datastring` field search only** must **not** match if the field path does not use binary columns (regression guard).
5. User previously toggled **「매칭용 복호화」** — after this change, that control is **gone**; search still applies decrypt-for-match where the backend policy requires it for matching, **without** a user-facing checkbox.

### Expected outcome

- **`filterImageLogRowsByDataHeaderKeywords`** (and **`LogDbService.searchJavaFwImglog`** / any aligned prefetch path) implement **`fieldTerms`** vs **`keywordTerms`** as in §2; combined predicate **(field OK) AND (keyword OK)**.
- Keyword tokens: **OR** semantics; each token: **(A)** string-column predicate on **`datastring` / `headerstring`** **or** **(B)** in-memory **`IMAGE_LOG`** decrypt + substring on binary **`data` / `header`** per §2.2. Field-derived terms: **AND** semantics; each term: **`javaFwImglogTermMatchesForFilter`** equivalent only (string columns + bracket decrypt on those columns; **no** binary column scan).
- **No regression** for datastring/headerstring-only searches vs the current correct baseline.
- Image Log UI: **「매칭용 복호화」** removed; API may still accept legacy flags for compatibility but they **must not** gate match-only decrypt for this log type (see §2).
- **Contract / API definition** and **`log-search-domain`** skill **must** stay aligned with the new semantics (**DOC-CODE-SYNC**).

---

## 2. Design

### 2.1 Security review (PII / decryption / access control)

- [ ] Security review performed (check when PII / decryption scope or UX copy changes)
- **Inherited constraints:** Search-time **decrypt-for-match** remains **match-only**; **no** decrypted payload in **`POST .../search`** / **`advanced-search`** responses for **`java_fw_imglog`**. Align with **`20260413-imagelog-search-decrypt-display-separation`**, **`docs/contract.md`**, and project logging rules (no decrypted payloads or raw terms at unsafe levels in production).

### 2.2 Keyword clause: binary `data` / `header` payload match (design lock)

**Scope:** **Keyword tokens only** — extend the keyword OR clause; **do not** change **`datastring` / `headerstring` field** search semantics or code paths (plaintext + bracket-JSON `decryptJsonStringValues` on those string columns only).

**Keyword token `k` matches a row when** **either**:

- **(A)** **String columns (unchanged)** — the existing predicate on **`datastring`** and **`headerstring`** only: plaintext contains `k` **or** bracket-JSON decrypt-for-match on **`datastring`** **or** the same on **`headerstring`** (same behavior as today for that surface).
- **(B)** **Binary columns (in-memory only)** — for each non-null **`data`** and/or **`header`** cell (raw column string as stored), run **`cryptoUtil.decryptLogPayload(..., IMAGE_LOG)`** in memory only; if decrypted plaintext **contains** `k` (substring), the token matches via that column. If decrypt **fails** for a column, that column yields **no** match for `k`; the search **must not** throw. **Never** attach **`decrypted_data`**, **`decrypted_header`**, or other plaintext from this path to search response DTOs (**req `20260413-imagelog-search-decrypt-display-separation`**). Optional public flags (e.g. **`hasEncryptedMatchData`** / **`hasEncryptedMatchHeader`**) may be set for grid highlight parity if the product adds them; any internal temporaries must be stripped before response serialization.

**Field clause (unchanged):** **AND** across field-derived terms using **only** the **`javaFwImglogTermMatchesForFilter`** equivalent — **no** binary column scan.

**Combined predicate (unchanged structure):** **`(fieldOk) AND (keywordOk)`** — empty `fieldTerms` → field clause vacuously true; empty `keywordTerms` → keyword clause vacuously true.

### Technical design

#### Codebase summary (for implementers)

- **`LogDbService`**: **`searchJavaFwImglog`** and **`filterImageLogRowsByDataHeaderKeywords`** apply in-memory text filtering for imagelog rows. Prior work unified **`datastring` / `headerstring` / `keywords`** into one list; this requirement **splits** **`fieldTerms`** (from **`datastring` + `headerstring` only**, case-insensitive dedup of non-empty trimmed values, each whole trimmed string is one term — **same construction as the field side of the current unified model**) from **`keywordTerms`** (from **`keywords` list only**: preserve order, trim, drop empties; **do not** merge keyword tokens into **`fieldTerms`**).
- **Field-term predicate:** **`javaFwImglogTermMatchesForFilter`** equivalent — **`datastring` / `headerstring`** plaintext substring **or** bracket-JSON decrypt-for-match on those columns; **`hasEncryptedMatch*`** (or equivalent) only as driven by that path — **no** binary **`data` / `header`** scan.
- **Keyword-token predicate:** **(A)** same string-column union as field-term predicate on **`datastring` / `headerstring`**, **OR (B)** in-memory **`IMAGE_LOG`** payload decrypt + substring match on binary **`data` / `header`** per §2.2; decrypt failure → no match from that binary column; no response plaintext attachment.

#### Problem analysis

1. **Product intent:** Keywords are a **disjunctive** “any of these” filter; **`datastring` / `headerstring`** together form a **conjunctive** field filter. Merging all into one AND list (**`20260413-imagelog-unified-text-search`**) does not match that intent.
2. **Keyword “union” vs plaintext-only feel:** Operators expect keyword search to include matches inside encrypted **binary** payloads when decrypted with **`IMAGE_LOG`**, not only **`datastring` / `headerstring`** surfaces; today’s keyword path can feel **plaintext-only** if **(B)** is missing. Field form fields remain string-column-only by policy.
3. **UX:** An explicit **match-decrypt** checkbox forces users to understand server internals; match-only decrypt should follow **server policy** for this screen without opt-in.

#### Diagnostic phase (mandatory for error/bug fix only)

*Not applicable — behavior / UX requirement (not error-fix-only).*

#### Solution approach

**Backend (`java_fw_imglog` text filter):**

1. **Build `fieldTerms`:** From **`datastring`** and **`headerstring`** only: non-empty **trim**; merge into a list; **dedupe case-insensitively** (same rule as today for field unification). **Do not** include tokens from **`keywords`** in **`fieldTerms`**.
2. **Build `keywordTerms`:** From request **`keywords`** only: split/tokenize per **existing** API rules (e.g. comma-separated); **trim** each token; drop empty; **preserve order** for any deterministic test/debug narrative (outcome is OR so order does not affect membership).
3. **Row passes** in-memory text filter iff:
   - **`(fieldTerms` is empty **OR** every** term in **`fieldTerms`** matches** via the **field-term** predicate — **`javaFwImglogTermMatchesForFilter`** equivalent, string columns only), **AND**
   - **`(keywordTerms` is empty **OR** there exists** at least one keyword token **`k`** such that **`k`** matches** via the **keyword-token** predicate — **(A) OR (B)** per §2.2).
4. **Do not** unify field-term and keyword-token predicates into one helper that always scans binary columns — that would violate the **field path** lock; implement keyword **(B)** only on the keyword evaluation path.
5. **`decryptData` (or equivalent) request flag:** API may **still accept** the parameter for **compatibility**. For **`java_fw_imglog`**, document explicitly that it **does not** gate bracket decrypt-for-match for this filter (**always on** for match where implemented), **or** Backend sets policy so match path **ignores** the flag for gating. Implementer **must** align **`docs/contract.md`** / **`docs/api-definition.md`** and avoid requiring the removed UI to send a flag for normal search.
6. **Prefetch / SQL path:** Any prefetch must stay **consistent** with the final filter (no false negatives vs **`filterImageLogRowsByDataHeaderKeywords`**). If prefetch cannot apply keyword **(B)** in SQL, it must still **not** drop rows that the in-memory filter would include (align prefetch breadth with keyword binary match policy, or accept post-filter only per existing architecture — implementer documents chosen approach in contract/skill if relevant).

**Frontend:**

- Remove the **「매칭용 복호화」** checkbox (and any request wiring that implied user opt-in for match-only decrypt).
- Ensure request payload remains valid if backend still accepts optional **`decryptData`** (e.g. omit or send a harmless default per contract after update).

**Contract / spec:**

- Document **`java_fw_imglog`** text filter: **`fieldTerms` AND clause**, **`keywordTerms` OR clause**, combined with **AND**; and **`decryptData`** non-gating policy for this log type’s match path.

**DB:**

- **None** unless §3 needs seed adjustments.

**Cursor tool update targets**

- **Skills:** `.cursor/skills/log-search-domain/SKILL.md` — update after implementation.

### Affected scopes and change targets (verification)

| Scope | Affected? | §2 subsection and change file list complete? |
|-------|------------|-----------------------------------------------|
| Backend | Yes | Yes |
| Frontend | Yes | Yes |
| DB | Optional (test seed) | If tests require |
| Contract / Spec | Yes | Yes |
| Cursor tools (skills) | Yes | After impl |

### Planned change file list (expected change targets)

#### Backend

- `backend/src/main/java/com/logmng/service/LogDbService.java`
  - **Must** implement **`fieldTerms` vs `keywordTerms`** split and combined predicate in **`filterImageLogRowsByDataHeaderKeywords`** (and **`searchJavaFwImglog`** / aligned paths); **field** path: **`javaFwImglogTermMatchesForFilter`** equivalent only; **keyword** path: add §2.2 **(B)** on binary **`data` / `header`** without attaching plaintext to responses; **`decryptData`** must not gate match-only decrypt for **`java_fw_imglog`** (document policy in contract).
- `backend/src/test/java/com/logmng/service/LogDbServiceTest.java`
  - **Must** cover §3 TC-01–TC-06 and **TC-07–TC-09** (keyword binary payload / field regression / decrypt failure).

#### Frontend

- Image Log search form component(s) that render **「매칭용 복호화」** (e.g. under `frontend/src/components/` — implementer to confirm exact file).
  - **Must** remove the checkbox and related state/request fields that implied user opt-in for match decrypt.

#### Contract / API docs

- `docs/contract.md`, `docs/api-definition.md`
  - **Must** document keyword OR + field AND + **`decryptData`** semantics for **`java_fw_imglog`**, including keyword **(B)** binary payload match and **no** search-response plaintext for match-only decrypt.

#### DB

- `backend/src/main/resources/db/init-data-local-decrypt-test-imagelog.sql` and/or `backend/src/test/java/com/logmng/util/LocalDecryptSampleSeedGenerator.java`
  - **As needed** for §3 — including a row where a substring exists **only** in decrypted **`data` / `header`**, not in **`datastring` / `headerstring`**, and a decrypt-failure fixture for TC-09 (implementer-chosen id per LOCAL seed conventions).

---

## 3. Test approach

### Test case list (required)

| ID | Scope | Type | Scenario (input / condition) | Expected result | Verification (unit / integration / manual) |
|----|-------|------|------------------------------|-----------------|--------------------------------------------|
| TC-01 | Backend | Normal | Keywords **`A,B`** (OR): row contains match for **`B`** only on an allowed surface, not **`A`** | Row **matches** (keyword OR) | Unit (`mvn test`, `LogDbServiceTest`) |
| TC-02 | Backend | Normal | **Field** requires **`X`** (e.g. via `datastring`); keywords **`Y,Z`** (OR); row has **`X`** + **`Y`** | Row **matches** | Unit (`mvn test`) |
| TC-03 | Backend | Normal | Same as TC-02 but row has **`X`** only (no **`Y`** or **`Z`**) | Row **does not** match | Unit (`mvn test`) |
| TC-04 | Backend | Regression | **`datastring`**-only search against **LOCAL** seed row (e.g. **`LOCAL-DECRYPT-TST-IM-0001`** or documented baseline from prior imagelog reqs) — no keywords | Row **still** matches (field-only path unchanged vs prior correct behavior) | Unit (`mvn test`) |
| TC-05 | Backend | Edge | Keywords-only: two terms **`P,Q`** with OR — row matches **`Q`** only | **Matches** | Unit (`mvn test`) |
| TC-06 | Backend | Edge | Same row as TC-05 but test would **fail** if implementation incorrectly required **AND** across **`P`** and **`Q`** (e.g. assert OR behavior explicitly vs a negative control if needed) | OR semantics verified (implementer: minimal extra assertion or paired test row) | Unit (`mvn test`) |
| TC-07 | Backend | Normal | **Keyword only:** substring exists **only** inside decrypted binary **`data`** and/or **`header`** (via **`IMAGE_LOG`**), **not** in **`datastring` / `headerstring`** plaintext or bracket-JSON match | Row **included** in keyword search results | Unit (`LogDbServiceTest`; LOCAL seed or generator row) |
| TC-08 | Backend | Regression | **Field only:** same substring as TC-07 but search uses **`datastring`** (or field path) **only** — binary columns must **not** be scanned for field terms | Row **excluded** | Unit (`LogDbServiceTest`) |
| TC-09 | Backend | Edge | **Keyword** search: decrypt fails on payload column that would have matched if decrypted (malformed ciphertext or wrong key fixture) | Keyword does **not** match via that column; search completes **without** throw | Unit (`LogDbServiceTest`) |

### Test scenarios

#### Scenario 1: Keyword OR (TC-01, TC-05–TC-06)

1. Build request with keywords only (or keywords + empty field strings).
2. Assert rows matching **any** keyword token are included.

#### Scenario 2: Field AND + keyword OR (TC-02–TC-03)

1. Set `datastring` / `headerstring` so **field clause** requires **`X`**.
2. Set keywords **`Y,Z`**.
3. Assert **`X`+`Y`** matches; **`X`** alone does not.

#### Scenario 3: Field-only regression (TC-04)

1. Re-run **datastring**-only scenario against stable LOCAL seed documented in prior imagelog requirements.
2. Assert match outcome unchanged vs baseline.

#### Scenario 4: Keyword binary payload union (TC-07–TC-09)

1. Seed or construct a row where target plaintext appears **only** after **`decryptLogPayload(..., IMAGE_LOG)`** on **`data` / `header`**, with **`datastring` / `headerstring`** not containing the keyword.
2. Assert **keywords** include the row (**TC-07**); assert **`datastring`**-only (field path) does **not** (**TC-08**).
3. Use a decrypt-failure fixture on the binary column; assert keyword search does not throw and does not match via that column (**TC-09**).

### Test data

- Reuse **`init-data-local-decrypt-test-imagelog.sql`** / **`LocalDecryptSampleSeedGenerator`** as needed for TC-04 and decrypt-for-match branches in TC-01–TC-03.
- Add or extend fixtures for **TC-07–TC-09**: binary-only-in-payload plaintext, and a controlled decrypt-failure case for the payload column.

### Test environment

- Backend: `http://localhost:9200` (or per project)
- Database: PostgreSQL (per project setup)

### 3.5 Browser automation verification (optional)

- **Applicable TCs:** After Frontend change, optionally verify **「매칭용 복호화」** is absent and search still returns expected rows for a manual spot-check (link to TC-01–TC-04 narratives).
- **Reference:** `docs/workflow/BROWSER-AUTOMATION-VERIFICATION-POLICY.md`

---

## 4. Checklist

### Frontend verification

- [ ] Checkbox removed; no broken form submit
- [ ] Search behavior spot-checked for keyword OR + field AND

### Backend verification

- [ ] `LogDbServiceTest` §3 cases pass
- [ ] No plaintext decrypt payload in search responses (inherited rule)

### Integration

- [ ] End-to-end imagelog search spot-check (optional)

### Documentation

- [ ] Requirement doc completed
- [ ] Contract / API definition updated

---

## 5. Test results

### Test run date

- (Pending)

### Test results

#### Backend

- (Pending)

**Commands:**

```bash
# After implementation — run Backend tests covering this requirement
cd backend && mvn test -Dtest=LogDbServiceTest
```

---

**Author**: Requirements (subagent)  
**Date**: 2026-04-14  
**Status**: In progress
