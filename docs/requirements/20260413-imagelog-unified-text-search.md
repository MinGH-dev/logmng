# 20260413 - Image log unified text search (datastring / headerstring / keywords)

## 1. User requirement

### Requirement description

Operators searching **Java FW Image Log** (`java_fw_imglog`) must not need a manual to understand the difference between **「데이터」** (`datastring`), **「헤더」** (`headerstring`), and **「키워드」** (`keywords`) as **independent AND gates** across fields. They want to **find rows where some text appears somewhere** in the supported text surfaces (e.g. typing **`LOCAL`** and getting the row that contains it in **either** plaintext **`headerstring`** **or** inside bracket-encrypted JSON on **`datastring`** after decrypt-for-match), **without** having to guess which input box “owns” that substring.

Today, when **both** `datastring` and `keywords` (and/or `headerstring`) are filled, the backend can apply **AND semantics across separate paths** (e.g. datastring path must match **and** keyword path must match). That **rejects** rows where the term appears only on one path (e.g. **`LOCAL`** visible in **`headerstring`** via the keyword pipeline but **not** in the **`datastring`** substring path), which feels broken to users who treat the three fields as “ways to type what I’m looking for.”

This requirement defines a **quick fix**: **unify** the three inputs into a single **effective term list** and a single **per-row match rule** (see §2), while **preserving** the **no-plaintext-in-search-response** rule from **`20260413-imagelog-search-decrypt-display-separation`**.

### User scenario

1. A user enters **`LOCAL`** in **「데이터」** (`datastring`) **or** in **「키워드」** (`keywords`) (or splits keywords per existing rules) expecting the same row to be found if **`LOCAL`** appears in **`headerstring`** plaintext or after **decrypt-for-match** on **`datastring`** / **`headerstring`** bracket JSON — **without** caring which API field carried the token.
2. The user enters the **same** token in **both** `datastring` and `keywords` (duplicate intent). The row that matches that token **once** must **still** match (no false negative from redundant AND).
3. The user enters **two different** tokens across fields (e.g. `datastring` = `LOCAL`, `keywords` = `OTHER` where **`OTHER`** does not occur anywhere in the row). The row must **not** match (**both** tokens are required in the unified model).
4. **Problem:** Current **independent AND** between datastring-only, headerstring-only, and keywords-only matching causes **false negatives** and forces users to learn internal field semantics.

### Expected outcome

- For **`java_fw_imglog`** text filtering in **`LogDbService`** (including any **prefetch** path that must stay consistent with **`filterImageLogRowsByDataHeaderKeywords`**), **one unified model** applies: **deduplicated `effectiveTerms`** and **AND across terms**, **OR across fields / decrypt-for-match surfaces** for **each** term (§2).
- **Search / list API responses** remain **without** plaintext decrypted payloads from match-only work (**unchanged** vs **`20260413-imagelog-search-decrypt-display-separation`**).
- **Documented breaking change:** Workflows that relied on **AND between different strings** in **`datastring`** vs **`keywords`** / **`headerstring`** may see **more rows** returned (OR-across-fields **per term**). Mitigations in §2.
- **Contract / API definition** and **`log-search-domain`** skill **must** be updated if externally visible behavior or documented semantics change (**DOC-CODE-SYNC**).

**Note:** This requirement does **not** extend decrypt-for-match to binary **`data`** / **`header`** columns unless a **separate** product decision does so; it **unifies** matching for **`datastring`**, **`headerstring`**, and **`keywords`** only, reusing the **same** decrypt-for-match logic as the keyword path today.

---

## 2. Design

### 2.1 Security review (PII / decryption / access control)

- [ ] Security review performed (check when PII / decryption scope changes)
- **Inherited constraints (must remain true):** Search-time **decrypt-for-match** is **match-only**; **no** decrypted **payload** in **`POST .../search`** / **`advanced-search`** responses for **`java_fw_imglog`**. Align with **`20260413-imagelog-search-decrypt-display-separation`** §1–§2.1, **`docs/contract.md`**, **`app.decryption.auto-decrypt-on-keyword-search`** interpretation (match-only; no plaintext leakage).
- **Logging:** Must **not** log decrypted payloads or raw search terms at levels that could expose PII in production (DEBUG / dev-only for diagnostics).

### Technical design

#### Codebase summary (for implementers)

- **`LogDbService`**: **`filterImageLogRowsByDataHeaderKeywords`** (and any **related prefetch** path for imagelog that applies the same filters) currently treats **`datastring`**, **`headerstring`**, and **`keywords`** as **separate** predicates combined with **AND** when multiple are active.
- **Decrypt-for-match:** Existing **bracket-JSON** **`decryptJsonStringValues`** (or equivalent) on **`datastring`** / **`headerstring`** as used on the **keyword** path must be **reused** for **each term** when testing row match (no new plaintext attachment to responses).

#### Problem analysis

1. **UX / mental model:** Three visible inputs map to **one user intent** (“text I’m looking for”) but implementation **ANDs** independent paths → **false negatives** when the same token is valid on a different surface than the user’s chosen box.
2. **Power-user regression risk:** Some users may have relied on **AND** between **different** strings in **`datastring`** and **`keywords`** to narrow results; unified semantics **widen** that case (breaking change).

#### Diagnostic phase (mandatory for error/bug fix only)

*Not applicable — behavior / UX requirement (not error-fix-only).*

#### Solution approach (locked semantics)

**Backend (`java_fw_imglog` text filter only):**

1. **Build `effectiveTerms`** (ordered list; order **does not** affect outcome after dedup):
   - **(a)** All **non-empty trimmed** tokens from the request **`keywords`** list using **existing split / tokenization rules** (if any — **must** stay consistent with current keyword behavior except as unified below).
   - **(b)** If **`datastring`** is non-empty after trim, append **one** term = **full trimmed `datastring`** (**substring** semantics for that field remain as today: the **whole** `datastring` value is one term for the unified check, not re-split unless product already splits `datastring` elsewhere — **default: single token = full trimmed string**).
   - **(c)** If **`headerstring`** is non-empty after trim, append **one** term = **full trimmed `headerstring`** (same **whole-string-as-one-term** rule as **(b)**).
2. **Deduplicate terms case-insensitively** (locked choice): after normalization for dedup (e.g. **Unicode case fold** or **locale-independent lower** — implementer **must** pick one approach, document in code comment and **Contract** if user-visible; default recommendation: **`String.equalsIgnoreCase`** / consistent **lower** for ASCII-heavy logs if already used in codebase).
3. **Row matches text filter** iff **for every** term `t` in **`effectiveTerms`**, **at least one** of the following is true:
   - **`datastring`** plaintext contains `t` (substring, **same** rules as today for that field), **or**
   - **`headerstring`** plaintext contains `t`, **or**
   - **Existing** bracket-JSON **decrypt-for-match** on **`datastring`** contains `t`, **or**
   - **Same** decrypt-for-match on **`headerstring`** contains `t`.
4. If **`effectiveTerms`** is **empty**, apply **no** text filter from these three fields (**other** filters unchanged: date, log type, etc.).
5. **Prefetch / SQL path:** Any **prefetch** or **pre-filter** that must mirror row visibility **must** use the **same** unified semantics (or a **conservative superset** that does not drop rows the final filter would keep — implementer **must** verify **no false negatives** vs final `filterImageLogRowsByDataHeaderKeywords`).

**Frontend:**

- **No** required UI change for this “quick fix” **unless** product wants copy/tooltips reflecting unified semantics. Optional: clarify that multiple boxes **contribute terms** that are **all required** (AND) but each term may hit **any** of the text surfaces (OR).

**Contract / spec:**

- **Must** document **unified text search semantics** for **`java_fw_imglog`** (`datastring`, `headerstring`, `keywords`): **effectiveTerms** construction, **case-insensitive dedup**, **AND across terms**, **OR across surfaces per term**.
- **Breaking change notice:** Users who relied on **AND between different strings** in **`datastring`** and **`keywords`** / **`headerstring`** may see **additional** matches. **Mitigations (product / ops):** release note; optional short **admin** or **user** note in search help; if needed later, a **compatibility flag** (out of scope unless product requests).

**DB:**

- **None** required unless tests need seed updates (see §3).

**Cursor tool update targets**

- **Skills:** `.cursor/skills/log-search-domain/SKILL.md` — update **after** implementation so **`java_fw_imglog`** text search semantics stay accurate.

### Affected scopes and change targets (verification)

| Scope | Affected? | §2 subsection and change file list complete? |
|-------|------------|-----------------------------------------------|
| Backend | Yes | Yes |
| Frontend | Optional (copy only) | If product confirms |
| DB | Optional (test seed) | If tests require |
| Contract / Spec | Yes | Yes |
| Cursor tools (skills) | Yes | After impl |

### Planned change file list (expected change targets)

#### Backend

- `backend/src/main/java/com/logmng/service/LogDbService.java`
  - **Must** implement **unified `effectiveTerms`** and **per-term OR-across-fields** matching in **`filterImageLogRowsByDataHeaderKeywords`** (and **align** any **prefetch** / related imagelog filter path).
- `backend/src/test/java/com/logmng/service/LogDbServiceTest.java` (or **existing** test class covering this filter)
  - **Must** add §3 test cases.
- `backend/src/main/resources/db/init-data-local-decrypt-test-imagelog.sql` and/or `backend/src/test/java/com/logmng/util/LocalDecryptSampleSeedGenerator.java`
  - **As needed** so §3 scenarios are **stable** (row **`LOCAL-DECRYPT-TST-IM-0001`** per **`20260413-imagelog-search-decrypt-display-separation`** §1 clarification).

#### Contract / API docs

- `docs/contract.md`, `docs/api-definition.md`
  - **Must** document **`java_fw_imglog`** unified text search semantics and breaking-change note.

#### Frontend

- **None** unless product requests **copy** / **help** updates (then `frontend/src/components/ImageLogSearchForm.js` or help copy per product).

#### DB

- **None** for production schema; **test data only** if required.

---

## 3. Test approach

### Test case list (required)

| ID | Scope | Type | Scenario (input / condition) | Expected result | Verification |
|----|-------|------|------------------------------|-----------------|--------------|
| TC-01 | Backend | Normal | Seed row **`LOCAL-DECRYPT-TST-IM-0001`**. Request: **`datastring`** only = **`LOCAL`** (trimmed); **`headerstring`** / **`keywords`** empty. | Row **matches** (term appears in **`headerstring`** plaintext and/or decrypt-for-match surfaces per §2 — **same** row as today’s keyword path for **`LOCAL`**). | Unit (`mvn test`) |
| TC-02 | Backend | Normal | Same seed row. Request: **`keywords`** only containing **`LOCAL`** (per existing keyword list rules); **`datastring`** / **`headerstring`** empty. | Row **matches**. | Unit (`mvn test`) |
| TC-03 | Backend | Regression | Same seed row. **`datastring`** = **`LOCAL`** **and** **`keywords`** include **`LOCAL`** (same term duplicated). | Row **matches** (no false negative from duplicate AND). | Unit (`mvn test`) |
| TC-04 | Backend | Edge | Same seed row. **`datastring`** = **`LOCAL`**, **`keywords`** include **`OTHER`** where **`OTHER`** does **not** occur in the row on any unified surface. | Row **does not** match (**both** terms required). | Unit (`mvn test`) |
| TC-05 | Backend | Edge | **Two distinct terms** both required: e.g. one term from **`headerstring`** / keyword path (substring in plaintext **`headerstring`**) **and** a second term that appears **only** after **decrypt-for-match** on **`datastring`** bracket JSON — **construct** inputs so **`effectiveTerms`** has **two** tokens; **positive** case: row contains **both**; **negative** case: one term missing. | **Positive** → match; **negative** → no match. | Unit (`mvn test`) |

### Test scenarios

#### Scenario 1: Single box finds the row

1. Load seed **`LOCAL-DECRYPT-TST-IM-0001`**.
2. Search with **`LOCAL`** in **`datastring`** only (TC-01).
3. **Verify** row present in filtered result set.

#### Scenario 2: Duplicate term in datastring + keywords

1. Same seed.
2. Set **`datastring`** and **`keywords`** both to **`LOCAL`** (TC-03).
3. **Verify** row still present.

#### Scenario 3: Two terms — AND semantics

1. Same seed.
2. **`datastring`** = **`LOCAL`**, **`keywords`** = **`OTHER`** (TC-04).
3. **Verify** row absent.

### Test data

- **Primary seed row:** **`LOCAL-DECRYPT-TST-IM-0001`** as described in **`20260413-imagelog-search-decrypt-display-separation`** §1 (plaintext **`headerstring`** contains identifier substring; **`datastring`** bracket JSON; binary **`data`** / **`header`** **out of scope** for this requirement).
- Implementer **must** ensure SQL seed or generator **matches** §3 needs for TC-05 (two-term positive/negative) **without** relying on production data.

### Test environment

- Backend: unit tests (`mvn test`).
- Database: H2 / test fixtures per existing **`LogDbServiceTest`** conventions.

### 3.5 Browser automation verification

- **Not required** unless Frontend copy changes are in scope.

---

## 4. Checklist

### Frontend verification

- [ ] If copy/help updated: UI text reviewed
- [ ] No reliance on old AND-across-fields mental model in user-facing strings (if updated)

### Backend verification

- [ ] §3 unit tests written and run
- [ ] Prefetch path aligned with final filter (no false negatives)
- [ ] No plaintext decrypt fields on search response (regression vs **`20260413`**)

### Integration

- [ ] Optional: manual search on Image Log screen for **`LOCAL`** scenarios (if timeboxed)

### Documentation

- [ ] Requirement doc completed
- [ ] Contract / API definition updated (**DOC-CODE-SYNC**)
- [ ] `log-search-domain` skill updated after implementation

---

## 5. Test results

### Test run date

- *Pending*

### Test results

#### Backend

- *Pending*

**Commands:**

```bash
cd backend && mvn test -Dtest=LogDbServiceTest
```

*(Adjust `-Dtest=` if tests live in another class name.)*

**Outcome:**

- *Pending*

### Issues found and resolution

- *None yet*

### Next steps

1. Security review if decrypt scope or logging touches new surfaces.
2. Implement Backend + Contract + tests; run §5 commands; record results.

---

## 6. Error remedy result (cause and action) — for error/bug fix requirements only

*Not applicable.*

---

## 7. Final version (Korean) — add after all verification is complete

*Per `docs/workflow/DOCUMENT-LANGUAGE-POLICY.md`, add a Korean summary here (or `20260413-imagelog-unified-text-search-ko.md`) after QA verification completes.*

---

**Author**: Requirements subagent  
**Date**: 2026-04-13  
**Status**: In progress
