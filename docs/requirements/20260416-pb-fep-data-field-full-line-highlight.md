# 20260416 - PB FEP DATA / STREAM DATA full-line keyword emphasis

**Parent requirement:** `docs/requirements/20260416-pb-fep-keyword-highlight-imagelog-parity.md`  
This document **refines** the parent: same keyword matching semantics (`frontend/src/utils/keywordHighlight.js` — plain `<mark>`, quoted-bracket `mark.encrypted-highlight`, OR across terms, heuristics when `hasEncryptedMatch*` is absent), but adds **visible full-line emphasis** in the expanded **DATA** area when a **logical line** contains a match.

## 1. User requirement

### Requirement description

For PB FEP log search, in the expanded payload area that shows **STREAM DATA** / **전문** content (the `data` / `request_data` / `response_data` stream built in `frontend/src/components/LogTable.js` via `streamPayload` and `renderStreamBody`), keyword hits must remain discoverable not only by inline `<mark>` (and encrypted-region marks) but also by **full-line visual emphasis**: whenever the highlighter would apply any match on a given **line**, that **entire line** must receive a clear, consistent emphasis (e.g. row/line background tint, left border, or a dedicated wrapper class scoped to the stream panel).

This applies to the **DATA** expansion region specifically (not a mandate to change unrelated grid cells unless product later extends the same pattern).

### User scenario

1. An operator expands a PB FEP row to view stream/payload text and has one or more search keywords active.
2. A **logical line** of that payload contains a keyword hit (including matches resolved via the same rules as `highlightKeywordsAsHtml`: plain substring, encrypted quoted-bracket path, OR semantics across keywords).
3. **Problem:** Inline `<mark>` alone can be easy to miss on dense monospace lines; users want the **whole line** to read as “this line matched,” similar to full-line highlight patterns in some IDEs or log viewers.
4. **Refinement:** In addition to existing inline marks, the **full line** must be visually emphasized.

### Expected outcome

- **Matching semantics:** Unchanged from the parent requirement — one **logical line** is a segment split by **newline (`\n`)** from the raw payload string returned for `streamPayload(log)` (same split as current wireframe path). A line is considered to “have a match” when, after applying the **same** `highlightKeywordsAsHtml` rules to that line’s text in isolation, the result would differ from the unhighlighted line in any match-indicating way (plain `<mark>` or `mark.encrypted-highlight`, per current implementation). Implementers may implement this by reusing the shared util and/or a small line-level predicate — behavior must stay aligned with the parent.
- **Full-line emphasis:** For every logical line that has a match, the **line container** (not only the matched substring) receives a **dedicated emphasis** (CSS class on the per-line wrapper, or equivalent). Inline `<mark>` / `encrypted-highlight` **may remain**; full-line styling **combines** with inline marks unless product specifies otherwise — default is **both** full-line background (or border) **and** existing marks.
- **Wireframe (`layoutVariant="pb-fep-svg"`):** Today each line is already rendered as `div.stream-line` with `dangerouslySetInnerHTML` from `highlightKeywordsAsHtml` per line. **“Whole line”** means: add the full-line emphasis class (and associated styles) on **`div.stream-line`** when that line matches.
- **Legacy single-block `<pre>`:** Today `renderStreamBody` uses one `<pre className="tr-data-stream">` and, when keywords are present, a **single** inner HTML pass over the **full raw string** (`streamHtml(raw)`), so there is **no** per-line DOM node. **“Whole line”** for legacy means: treat **logical lines** the same way as wireframe — **split `raw` by `\n`**, render **one row container per line** (e.g. `div` with class mirroring `stream-line`, or another valid structure inside/instead of monolithic `<pre>` — Frontend chooses DOM that preserves accessibility and monospace readability). Each row gets full-line emphasis **only** when **that** split segment matches. **Do not** apply one background to the entire `<pre>` when only one line matches.
- **Non-matching lines:** Lines with **no** keyword hit must **not** receive the full-line emphasis class; they may remain plain or show only normal cell/pre styling.
- **Scope:** **Frontend-only**; no new API fields required for this refinement unless the Frontend team discovers an unavoidable need (not expected).

**Note:** Collapsed column cells and other areas covered by the parent doc keep their existing highlighting rules; this refinement is scoped to the **expanded stream / DATA body** unless explicitly extended later.

## 2. Design

### 2.1 Security review (optional)

- **PII / decryption:** No change from parent — highlighting remains on **already returned** text; no new client decryption. Full-line CSS does not expose new data.
- [ ] Security review performed (optional; confirm if styling could confuse encrypted vs plain — low risk).

### Technical design

#### Codebase summary (investigation)

- **`LogTable.js` — `renderStreamBody`:** For `pb-fep-svg`, `raw.split('\n')` drives one `div.stream-line` per line; each line is passed through `highlightKeywordsAsHtml`. For **legacy**, the same `streamHtml` is applied to **entire** `raw` inside one `<pre>`, so line-level DOM and full-line emphasis are **not** yet expressible without structural change (split-by-line rendering aligned with wireframe).
- **`keywordHighlight.js`:** Produces HTML string with `<mark>` / `mark.encrypted-highlight`; no line-wrapper API today — line-level “has match” may be derived by comparing output to input, or by a small shared helper, consistent with parent semantics.

#### Problem analysis

1. **UX gap:** Inline marks alone do not give **scan-level** “which lines matched” for long payloads.
2. **Layout gap:** Legacy path renders the whole payload as one HTML blob; full-line emphasis per logical line **requires** per-line containers (or an equivalent approach) consistent with the wireframe split.

#### Diagnostic phase (mandatory for error/bug fix only)

*Not applicable — refinement / UX requirement.*

#### Solution approach

**Frontend:**

- **Wireframe:** Add a **conditional class** on each `div.stream-line` when that line’s text is a keyword hit (per §1 definition), e.g. `stream-line stream-line--keyword-hit` (final names per project conventions).
- **Legacy:** Refactor expanded stream rendering so **logical lines** use the **same per-line structure** as wireframe (split on `\n`, one wrapper per line, same highlight + full-line class rules). Preserve monospace and readability (`LogTable.css` / panel classes).
- **CSS:** Define full-line emphasis under the PB FEP stream panel scope (e.g. `.pb-fep-stream-panel .stream-line--keyword-hit` and legacy mirror) — **subtle** background or left border; must remain readable with `<mark>` and `encrypted-highlight`. Reuse or extend `LogTable.css`; align contrast with existing log table styles.
- **Naming suggestion (non-binding):** `stream-line--keyword-hit` or `pb-fep-stream-line--match` for the line wrapper; avoid generic global names that collide with other tables.
- **Tests:** Extend `LogTable.test.js` (and shared util tests if a predicate is extracted) per §3.

**Backend:** None.

**DB:** None.

**Contract / spec:** None for this refinement.

### Affected scopes and change targets (verification)

| Scope | Affected? |
|-------|------------|
| Backend | No |
| Frontend | Yes — `LogTable.js`, `LogTable.css`, tests |
| DB | No |
| Contract | No |

### Planned change file list (expected change targets)

#### Frontend

- `frontend/src/components/LogTable.js` — Per-line match detection and className on `stream-line`; legacy `renderStreamBody` aligned to per-line wrappers for full-line emphasis parity.
- `frontend/src/components/LogTable.css` — Styles for full-line emphasis class(es).
- `frontend/src/utils/keywordHighlight.js` — **Optional:** export a small `lineHasKeywordHighlightHtml` (or similar) helper if it reduces duplication; **only** if it preserves exact parent semantics.
- `frontend/src/components/LogTable.test.js` — New TCs in §3.

#### Backend / DB

- None.

## 3. Test approach

### Test case list (required)

| ID | Scope | Type | Scenario (input / condition) | Expected result | Verification |
|----|-------|------|------------------------------|-----------------|----------------|
| TC-FL-01 | Frontend | Normal | `pb-fep-svg`, expanded stream, payload line contains keyword | The corresponding `div.stream-line` has the full-line emphasis class (e.g. `stream-line--keyword-hit`); HTML may still contain `<mark>` | Unit (`LogTable.test.js`) |
| TC-FL-02 | Frontend | Regression | Same setup, line has **no** keyword | That `stream-line` **lacks** the full-line emphasis class | Unit |
| TC-FL-03 | Frontend | Normal | Multi-line payload: keyword only on second line | Only the second line’s wrapper has the full-line class; first and third do not | Unit |
| TC-FL-04 | Frontend | Normal | Legacy layout expanded stream (non-`pb-fep-svg`), multi-line payload with match on one line | Per-line wrappers exist; only the matching logical line has the full-line emphasis class; non-matching lines do not | Unit |
| TC-FL-05 | Frontend | Edge | Empty line between two lines; keyword only on non-empty line | Empty line wrapper without full-line class unless it is a match by semantics | Unit |
| TC-FL-06 | Frontend | Regression | Parent TC-01–TC-07 behaviors (ImageLog parity, OR keywords, encrypted bracket path) | Still pass after change; no regression in `ImageLogTable` / shared util tests | Unit (npm test) |

### Test scenarios

#### Scenario A: Wireframe STREAM DATA

1. Expand row; verify matching lines show both inline marks (if any) **and** full-line emphasis.
2. Collapse and re-expand; classes remain stable.

#### Scenario B: Legacy 전문

1. Same keyword behavior with legacy column layout; full-line emphasis only on matching **split** lines, not the whole `<pre>`.

### Test data

- Synthetic multi-line strings with `\n`; include a line with `"[...]"` encrypted pattern per parent TC-03.

### Test environment

- Frontend unit tests: `npm test -- --watchAll=false`.

### 3.5 Browser automation verification (optional)

- Spot-check expanded STREAM DATA / 전문: matching lines show full-line emphasis in the live DOM.

## 4. Checklist

### Frontend verification

- [ ] Full-line class applied only when a logical line matches per §1
- [ ] Legacy path uses per-line structure; no whole-block false positive
- [ ] CSS readable with existing `mark` styles
- [ ] §3 automated tests pass

### Backend verification

- [ ] N/A

### Integration

- [ ] Optional manual spot-check on PB FEP expanded payload

### Documentation

- [ ] Requirement doc completed (§1–§3)
- [ ] Parent requirement cross-referenced for matching semantics

## 5. Test results

*(To be filled after implementation and QA verification.)*

### Test run date

- TBD

### Test results

#### Frontend

- TBD

---

## References

- Parent: `docs/requirements/20260416-pb-fep-keyword-highlight-imagelog-parity.md`
- Related (decrypt-only / ciphertext display): `docs/requirements/20260416-pb-fep-keyword-match-flags-data-full-line-highlight.md` — when keyword match is known only after server decrypt-for-match, **line-level** client predicates may fail; optional API flags + Frontend combine with full-line emphasis per that doc.
- `docs/template/REQUIREMENT_TEMPLATE.md`
- `frontend/src/components/LogTable.js` (`renderStreamBody`, `streamPayload`)
- `frontend/src/utils/keywordHighlight.js`
