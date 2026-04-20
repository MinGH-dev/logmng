# 20260416 - PB FEP log search keyword highlighting (ImageLog parity)

## 1. User requirement

### Requirement description

PB FEP log search results rendered with `frontend/src/components/LogTable.js` (including `layoutVariant="pb-fep-svg"` wireframe layout and the legacy PB FEP column set) must apply **keyword highlighting** that is **behaviorally aligned** with `frontend/src/components/ImageLogTable.js`: plain substring matches, encrypted-region styling where the payload uses the same quoted-bracket pattern, **OR** semantics across multiple keyword terms, and sensible behavior when a match exists only after server-side decrypt (plaintext keyword may not appear literally in the ciphertext shown in the UI).

### User scenario

1. An operator runs PB FEP log search (wireframe `pb-fep-log-search` or legacy PB FEP screen) with one or more keywords.
2. The result grid shows row fields (`log_time`, `tr_code`, identifiers, `bmsg`, `data` expand affordance, etc.) and, when expanded, the **STREAM DATA** / stream body built from `data` / `request_data` / `response_data` (see `streamPayload` / `renderStreamBody` in `LogTable.js`).
3. **Problem**: `LogTable` uses a **minimal** local `highlightKeywords` (regex split + `span.highlight-keyword`) and does **not** implement ImageLog’s `highlightKeywordsAsHtml` behavior (`<mark>`, `mark.encrypted-highlight`, quoted `"[...]"` encrypted pattern, `hasEncryptedMatch*` integration, or equivalent heuristics). Visual and semantic parity with ImageLog is missing.

### Expected outcome

- **Shared behavior**: Highlighting logic used for PB FEP table text (at minimum the expanded stream payload; **and** visible table cell strings where search-relevant values are shown) must follow the **same rules** as ImageLog for:
  - **Plain matches**: wrap matched substrings in `<mark>` (not ad-hoc `span` classes unless the project standardizes one path — **prefer `<mark>`** to match ImageLog and existing `ImageLogTable.css`).
  - **Encrypted / ciphertext regions**: where JSON (or similar) contains quoted bracket string values `"[...]"` per ImageLog’s `quotedBracketPattern`, apply `mark.encrypted-highlight` when the UX rules in ImageLog apply (including keyword-only search and decrypt-only match cases as approximated on the client when backend flags are absent — see §2).
  - **Multiple keywords**: **OR** across terms (each term participates in highlighting; combined behavior matches ImageLog’s sequential keyword processing).
  - **Decrypt-only match**: When the literal keyword does not appear in the displayed text but the row is still a search hit (e.g. match after decrypt on the server), the UI should still indicate relevance for encrypted-looking regions **without** exposing decrypted plaintext — using **frontend heuristics** consistent with ImageLog when `hasEncryptedMatch*` fields are **not** present on PB FEP rows (see §2).
- **Duplication**: Prefer **one shared implementation** (extract from `ImageLogTable.js` into a shared module) per Architecture commonization; consumers (`ImageLogTable`, `LogTable`) call the shared API.
- **Scope**: **Frontend-first**; PB FEP search API rows **do not** currently expose `hasEncryptedMatchDatastring` / `hasEncryptedMatchData` / etc. (unlike ImageLog). This requirement **does not** mandate a contract change; optional backend metadata may be listed as a **follow-up** for tighter accuracy.

**Note**: Pattern **3.4** (*search/filter form* alignment across screens) in `REQUIREMENTS-CHANGE-TARGET-CHECKLIST.md` does **not** apply to this requirement — the change targets **result grid / cell highlighting**, not search form layout. No new numeric layout standards are introduced here; highlight colors should **reuse** existing ImageLog highlight styles or a **single** shared stylesheet so PB FEP and ImageLog stay consistent.

## 2. Design

### 2.1 Security review (optional)

- **PII / decryption**: No new client-side decryption or plaintext exposure. Highlighting operates on **already returned** row text only. Optional future backend flags (if added under contract) would only refine **which** bracket regions get `encrypted-highlight`, not add decrypted fields to the response.
- [ ] Security review performed (recommend Frontend + Security sign-off if backend metadata is added later).

### Technical design

#### Codebase summary (investigation)

- **`ImageLogTable.js`**: Defines `highlightKeywordsAsHtml` and `highlightKeywords` with:
  - `quotedBracketPattern` for values that look like `"[...]"` inside JSON strings.
  - Parameters: `originalText`, `hasEncryptedMatch`, `fieldKeyword`, `keywordBackedFieldHighlight` for field-scoped and decrypt-only match UX.
  - Renders `<mark>` and `<mark class="encrypted-highlight">` via `dangerouslySetInnerHTML` (pretty mode) or wrapped spans.
  - Row flags: `hasEncryptedMatchDatastring`, `hasEncryptedMatchData`, `hasEncryptedMatchHeaderstring`, `hasEncryptedMatchHeader`, and legacy `_datastring_has_encrypted_match` style keys.
- **`LogTable.js`**: Inline `highlightKeywords(text, kwList)` splits on a **joined regex** (`|`) and wraps matches in `<span className="highlight-keyword">` — **no** encrypted-region pass, **no** `<mark>`, **no** `hasEncryptedMatch*`. Used in `renderStreamBody` only; **table body cells** (`<td>` for `tr_code`, `login_id`, `bmsg`, etc.) are plain text **without** keyword highlight.
- **`LogGrid.js`**: Passes `keywords={Array.isArray(searchParams.keywords) ? searchParams.keywords : []}` into `LogTable` (same source as ImageLog).
- **Styles**: `ImageLogTable.css` defines global `mark` and `mark.encrypted-highlight`. `LogTable.css` defines `.highlight-keyword` (yellow/brown) — **differs** from ImageLog’s `mark` styling.
- **Backend / contract**: `specs/log-db-pb-fep-log-search.spec.yaml` **`PbFepWireframeRow`** lists stable wire keys (`data`, `request_data`, `response_data`, …) but **no** `hasEncryptedMatch*` booleans. `docs/api-definition.md` states PB FEP responses do not include `decrypted_*` plaintext; optional non-plaintext hints are **contract-governed**. ImageLog-specific match flags are implemented in `LogDbService` for ImageLog rows, not for PB FEP wireframe rows in this investigation.

#### Problem analysis

1. **Behavior gap**: PB FEP `LogTable` highlighting is a **subset** and uses **different DOM/CSS** than ImageLog, so users see inconsistent keyword and ciphertext cues across log types.
2. **Metadata gap**: Without `hasEncryptedMatch*` on PB FEP rows, decrypt-only matches cannot be flagged precisely; ImageLog’s **heuristic** paths (quoted brackets + keyword-only search) must be applied to PB FEP **as the primary** mechanism unless/until contract extends optional flags.
3. **Maintenance**: Two divergent implementations increase drift risk; extraction to a **shared module** reduces duplication.

#### Diagnostic phase (mandatory for error/bug fix only)

*Not applicable — feature parity requirement.*

#### Solution approach

**Frontend:**

- **Extract** `highlightKeywordsAsHtml` (and the thin `highlightKeywords` wrapper if kept) into a **shared utility module** under `frontend/src/` (exact path to be chosen by Frontend; e.g. `utils/keywordHighlight.js`), preserving **documented** semantics (quoted-bracket detection, order of encrypted vs plain passes, OR across keywords).
- **Refactor** `ImageLogTable.js` to **import** the shared functions so existing ImageLog tests remain the **regression anchor** (update imports; behavior must remain compatible unless a deliberate fix is documented).
- **Update** `LogTable.js` to use the shared helper for:
  - **`renderStreamBody`** / stream lines (both `pb-fep-svg` and legacy expanded content).
  - **Visible row cells** that display string values from the row (all relevant `<td>` contents for both layout variants, excluding controls such as the expand button label). Pass **`hasEncryptedMatch`** as `false` unless the row object contains optional future flags; implementers **must** still run the same **heuristic** branches ImageLog uses when flags are false (quoted brackets, keyword-only search).
  - Use **`dangerouslySetInnerHTML`** or the same rendering strategy as ImageLog for cells that need HTML `mark` tags; ensure **one** consistent approach for stream lines (may require a small wrapper component to avoid invalid nesting inside `<pre>` if applicable — Frontend must verify DOM validity).
- **CSS**: Align PB FEP table highlight appearance with ImageLog — either **import** shared rules, duplicate **only** if tied to scoping (prefer **shared** `mark` / `mark.encrypted-highlight` under a table container class to avoid global conflicts), and **deprecate** or stop using `.highlight-keyword` for keyword hits if `<mark>` becomes the standard for PB FEP.
- **Tests**: Extend `LogTable.test.js` with cases mirroring **representative** `ImageLogTable.test.js` scenarios (plain keyword, multiple keywords OR, quoted `"[...]"` encrypted highlight, decrypt-only style behavior with **no** backend flags — heuristic path). Update or add tests if the shared module is covered directly.

**Backend:**

- **None** for the minimal scope. Optional follow-up (separate requirement + contract): add optional booleans to PB FEP row maps **only** if product and Security approve, mirroring ImageLog’s `hasEncryptedMatch*` for payload fields — then Frontend would pass them into the shared highlighter.

**DB:**

- **None**.

**Contract / spec:**

- **None** for minimal scope. If backend metadata is added later, update `docs/contract.md`, `docs/api-definition.md`, and `specs/log-db-pb-fep-log-search.spec.yaml` in that follow-up.

### Affected scopes and change targets (verification)

Per `docs/workflow/REQUIREMENTS-CHANGE-TARGET-CHECKLIST.md` §1:

| Scope | Affected? | §2 subsection and change file list complete? |
|-------|------------|-----------------------------------------------|
| Backend | No | N/A |
| Frontend (config UI + view screen) | Yes — **view screen** (LogTable / shared util / CSS / tests). No config UI. | Yes |
| DB | No | N/A |
| Contract / Spec | No (minimal scope); optional follow-up only | N/A |
| Cursor tools (skills, specs) | Optional: update `log-search-domain` or UI skill if highlighting behavior should be documented for operators — **not** blocking for Step 4 | Optional |

**Pattern 3.4** (search/filter form alignment): **Does not apply** — no form panel width or user-block field changes.

### Planned change file list (expected change targets)

#### Frontend

- New shared module (path TBD), e.g. `frontend/src/utils/keywordHighlight.js` (or `frontend/src/components/shared/keywordHighlight.js`)
  - Host `highlightKeywordsAsHtml` / `highlightKeywords` extracted from ImageLog with stable exports and JSDoc for parameters aligned with ImageLog.
- `frontend/src/components/ImageLogTable.js`
  - Remove duplicated logic; import shared functions; **no** intentional behavior regression.
- `frontend/src/components/LogTable.js`
  - Replace inline `highlightKeywords` with shared implementation; apply to `renderStreamBody` and **table body string cells** for PB FEP SVG and legacy layouts.
- `frontend/src/components/LogTable.css`
  - Scope or import `mark` / `mark.encrypted-highlight` styling consistent with ImageLog; remove or redirect `.highlight-keyword` usage for PB FEP keyword hits if superseded by `<mark>`.
- `frontend/src/components/ImageLogTable.css`
  - If common styles move to a shared CSS partial, adjust imports; otherwise ensure **single source of truth** for highlight colors.
- `frontend/src/components/ImageLogTable.test.js`
  - Update imports/mocks if needed; preserve existing TC behavior.
- `frontend/src/components/LogTable.test.js`
  - Add coverage for PB FEP highlighting parity scenarios.

#### Backend

- None (this requirement).

#### DB

- None.

## 3. Test approach

### Test case list (required)

| ID | Scope | Type | Scenario (input / condition) | Expected result | Verification (unit / integration / manual) |
|----|-------|------|------------------------------|-----------------|--------------------------------------------|
| TC-01 | Frontend | Normal | PB FEP row, expanded stream body, single keyword appears literally in `data` text | Substrings wrapped in `<mark>` (or equivalent DOM matching ImageLog); styling matches shared `mark` rules | Unit (npm test — LogTable or shared util) |
| TC-02 | Frontend | Normal | Multiple keywords (OR); stream text contains second term but not first in same line | Both terms highlighted per ImageLog OR behavior | Unit (npm test) |
| TC-03 | Frontend | Normal | Stream JSON-like text contains quoted bracket value `"[ENC]"` and keyword appears **inside** brackets; `hasEncryptedMatch*` absent | Region receives `mark.encrypted-highlight` per same heuristics as ImageLog | Unit (npm test) |
| TC-04 | Frontend | Edge | Keyword matches only on ciphertext heuristically (no literal plaintext keyword in UI); `hasEncryptedMatch*` absent; row still returned as search hit | Encrypted-looking quoted bracket regions still get appropriate `encrypted-highlight` when heuristic rules fire (same thresholds as ImageLog); no decrypted text invented | Unit (npm test) |
| TC-05 | Frontend | Regression | ImageLog row with `hasEncryptedMatchDatastring: true` and keywords-only search | Existing ImageLogTable tests still pass; bracket value uses `encrypted-highlight` | Unit (npm test — ImageLogTable.test.js) |
| TC-06 | Frontend | Normal | Collapsed PB FEP row (wireframe or legacy): keyword in `tr_code` or `bmsg` cell | Cell text shows same highlight semantics as stream (not only stream panel) | Unit (npm test) |
| TC-07 | Manual / browser | Normal | Local app: PB FEP search with keywords, expand row | Visual: highlights visible in grid cells and STREAM DATA; colors consistent with ImageLog screen | Manual (browser) |

### Test scenarios

#### Scenario 1: Wireframe PB FEP (`pb-fep-svg`)

1. Search with keywords that hit `bmsg` / `data` payload.
2. Verify collapsed columns and expanded STREAM DATA both show highlighting consistent with §1.

#### Scenario 2: Legacy PB FEP columns

1. Same as scenario 1 on legacy layout (`layoutVariant` default / non-svg).
2. Confirm `request_data` / `response_data` / `data` stream path still highlights correctly.

### Test data

- Use **synthetic** strings in unit tests (no real PII). Include samples with `"[...]"` quoted JSON fragments mirroring `ImageLogTable.test.js` patterns.

### Test environment

- Frontend: `http://localhost:3001` (manual TC-07)
- Backend: `http://localhost:9200` (when running full app for manual)

### 3.5 Browser automation verification (optional)

- **Applicable TCs**: TC-07.
- **Procedure**: Navigate to PB FEP log search → enter keywords → submit → expand row → snapshot to confirm `mark` / `encrypted-highlight` presence in DOM.
- **Reference**: `docs/workflow/BROWSER-AUTOMATION-VERIFICATION-POLICY.md`.

## 4. Checklist

### Frontend verification

- [ ] Shared highlight util extracted; ImageLog + LogTable use it
- [ ] PB FEP grid + stream highlighting matches §1 expected outcome
- [ ] CSS parity (or documented shared stylesheet) for `mark` / `encrypted-highlight`
- [ ] Automated tests per §3 pass

### Backend verification

- [ ] N/A for minimal scope

### Integration

- [ ] Manual spot-check on PB FEP screen (TC-07)

### Documentation

- [ ] Requirement doc completed (§1–§3)
- [ ] Optional follow-up noted if backend metadata is later required

## 5. Test results

*(To be filled after implementation and QA verification.)*

### Test run date

- TBD

### Test results

#### Frontend

- TBD

#### Backend

- N/A

---

## References

### Refinements (child requirements)

- `docs/requirements/20260416-pb-fep-data-field-full-line-highlight.md` — Full-line emphasis on each matching **logical line** in expanded STREAM DATA / 전문 (DATA area), in addition to inline `<mark>`; legacy `<pre>` split-by-line parity.

- `docs/requirements/20260416-pb-fep-keyword-match-flags-data-full-line-highlight.md` — Optional **server-side keyword match flags** per wire column (`request_data` / `response_data` / `bmsg`) so full-line emphasis works for **decrypt-only** keyword hits when the UI still shows ciphertext (extends the full-line doc’s “line match” definition).

- `docs/template/REQUIREMENT_TEMPLATE.md`
- `docs/workflow/REQUIREMENTS-AUTHORING-WORKFLOW.md`
- `docs/workflow/REQUIREMENTS-CHANGE-TARGET-CHECKLIST.md` §1 (scope table)
- `frontend/src/components/ImageLogTable.js`, `ImageLogTable.css`
- `frontend/src/components/LogTable.js`, `LogTable.css`
- `frontend/src/components/LogGrid.js` (keywords prop)
- `specs/log-db-pb-fep-log-search.spec.yaml` (`PbFepWireframeRow`)
