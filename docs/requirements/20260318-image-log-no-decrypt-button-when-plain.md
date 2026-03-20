# 20260318 - Image log: no Decrypt button when row has no encrypted data

## 1. User requirement

### Requirement description

On the image log search result table (ImageLogTable), when a row has **no encrypted data** (e.g. datastring and headerstring are plain JSON such as `{"id":"plain", "name":"", "age":0}` with no bracket-wrapped ciphertext), the **복호화 (Decrypt)** button must **not** be shown; the cell must show **"-"** instead.

Currently the Decrypt button is shown for such rows when the API returns non-empty `data` or `header` fields (e.g. plain content). The UI should treat a row as "has encrypted data" only when the displayed content (datastring / headerstring) actually contains encrypted-style payload (e.g. quoted bracket-wrapped cipher like `"[base64...]"`), not merely when `data` or `header` are present and non-empty.

### User scenario

1. User opens the image log search screen and runs a search.
2. Results include at least one row where datastring is plain JSON (e.g. `{"id":"plain", "name":"", "age":0}`) and headerstring is plain JSON, with no encrypted placeholder like `"[...]"` in either field.
3. **Problem**: The Decrypt column for that row shows the green "복호화" button instead of "-".
4. User expects: for rows with no encrypted content, the cell shows "-" and no button, so that decrypt actions are only offered when the row actually contains decryptible encrypted data.

### Expected outcome

- For any row where **datastring** and **headerstring** do **not** contain encrypted-style content (e.g. no quoted bracket-wrapped cipher pattern such as `"[...]"` inside the JSON string values), the Decrypt cell shows **"-"** and **no** Decrypt (or "복호화 해제") button.
- The Decrypt button (or "복호화 해제") is shown only when the row has encrypted-style content in datastring and/or headerstring (e.g. at least one occurrence of a quoted bracket-wrapped cipher pattern), subject to permission and decryption-allowed list as already implemented.
- Presence of non-empty `log.data` or `log.header` alone must **not** cause the Decrypt button to appear; encrypted state must be derived from the **content shape** of datastring/headerstring (or from an explicit backend flag if the API later provides one).

---

## 2. Design

### 2.1 Security review (optional; when PII / decryption / access control)

Not applicable for this change. Decryption scope and permission checks are unchanged; only the **visibility** of the Decrypt button for plain rows is corrected so that decrypt is not offered when there is nothing to decrypt.

### Technical design

#### Problem analysis

1. **Current logic**: In `frontend/src/components/ImageLogTable.js`, `hasEncryptedData` is computed as: true if (datastring contains `[` and `]`) OR (headerstring contains `[` and `]`) OR (`log.data` is non-empty string) OR (`log.header` is non-empty string).
2. **Bug**: For plain (non-encrypted) rows, the API may still return `data` and/or `header` as non-empty strings (e.g. plain text or JSON). The third and fourth conditions then make `hasEncryptedData` true, so the Decrypt button is shown incorrectly.
3. **Additional nuance**: Plain JSON can contain `[` and `]` as part of array syntax (e.g. `{"arr":[1,2]}`). So "contains `[` and `]`" alone is too broad for "encrypted content". The backend and frontend already use a **quoted bracket-wrapped** pattern for encrypted values (e.g. a string value like `"[base64...]"` inside JSON). Encrypted-style content should be detected as the presence of that pattern (e.g. `"(\[[^\]]*\])"` or equivalent) in datastring and/or headerstring, not merely any `[`/`]` or the presence of `data`/`header`.
4. **Backend**: The image log search API returns `data`, `datastring`, `header`, `headerstring` per row. There is no per-row `encrypted` or `hasEncryptedData` flag in the current contract. The fix is therefore **frontend-only**: define a content-based rule so that the Decrypt button is shown only when datastring/headerstring contain actual encrypted-style content.

#### Solution approach

**Frontend:**

- **Change `hasEncryptedData` in `ImageLogTable.js`** so that:
  - **Do not** use `(log.data && typeof log.data === 'string' && log.data.length > 0)` or `(log.header && typeof log.header === 'string' && log.header.length > 0)` as a signal for encrypted data. Presence of `data`/`header` alone must not imply encrypted.
  - Consider a row as having encrypted data **only** when **datastring** and/or **headerstring** contain **encrypted-style content**. Use the same semantic as the existing highlight logic: encrypted values appear as **quoted bracket-wrapped** strings in JSON (e.g. `"key":"[ciphertext]"`). A suitable detection rule is: at least one occurrence in datastring or headerstring of the pattern of a quoted string whose value is bracket-wrapped (e.g. match `"(\[[^\]]*\])"` or equivalent so that `"[...]"`-style cipher is detected, while plain JSON arrays like `[1,2,3]` are not treated as encrypted).
  - If the backend later adds an explicit per-row flag (e.g. `encrypted` or `hasEncryptedData`) in the search response, the frontend may use that flag when present and fall back to the content-based rule when absent; document this in code or in this requirement for future reference.
- **Document the rule**: In code or comment, state that "encrypted" for decrypt-button visibility means: datastring or headerstring contains at least one quoted bracket-wrapped value (encrypted payload pattern), not merely non-empty `data`/`header` or any `[`/`]`.

**Backend:**

- No change required for this bugfix. If a per-row `encrypted` (or `hasEncryptedData`) flag is added in a future change, the frontend can be updated to use it; that is out of scope here.

**DB:**

- No change.

### Affected scopes and change targets (verification)

Per `docs/workflow/REQUIREMENTS-CHANGE-TARGET-CHECKLIST.md`:

| Scope | Affected? | §2 subsection and change file list complete? |
|-------|-----------|-----------------------------------------------|
| Backend | No | N/A |
| Frontend (config UI + view screen) | Yes (view screen only) | Yes |
| DB | No | N/A |
| Contract / Spec | No | N/A |
| Cursor tools (skills, specs) | No | N/A |

### Planned change file list (expected change targets)

**(Planned at authoring. Implementing agent (Step 4) confirms or amends this list when implementation is complete.)**  
*Confirmed by Frontend (Step 4): no amendments; only the two files below were changed.*

#### Frontend

- `frontend/src/components/ImageLogTable.js`
  - Update `hasEncryptedData` logic so that (log.data / log.header presence) is not used; rely only on datastring/headerstring containing encrypted-style content (e.g. quoted bracket-wrapped cipher pattern). Add a short comment documenting the rule.
- `frontend/src/components/ImageLogTable.test.js`
  - Ensure TC-01 (no encrypted data → "-", no button) still passes with the new logic. Add or align at least one test so that a row with plain datastring/headerstring **and** non-empty `data`/`header` (if such payload is passed in tests) still shows "-" and no Decrypt button. If TC-01 already covers a plain row without `data`/`header`, add an edge-case TC: row with plain datastring/headerstring but with `data` and/or `header` set to non-empty strings → cell shows "-", no button.

---

## 3. Test approach

### Test case list (required)

| ID | Scope | Type | Scenario (input / condition) | Expected result | Verification (unit / integration / manual) |
|----|-------|------|------------------------------|-----------------|--------------------------------------------|
| TC-01 | Frontend | Normal | Row with plain datastring and headerstring (e.g. `{"key":"value"}`, `{"h":"v"}`), no `[`/`]` cipher pattern, no or empty `data`/`header`. | Decrypt cell shows "-"; no Decrypt button. | Unit (npm test — ImageLogTable.test.js) |
| TC-02 | Frontend | Edge | Row with plain datastring/headerstring (no bracket-wrapped cipher) but `data` and/or `header` non-empty (e.g. plain string). | Decrypt cell shows "-"; no Decrypt button. | Unit (npm test) |
| TC-03 | Frontend | Normal | Row with datastring containing encrypted-style pattern (e.g. `{"p":"[base64...]"}`). | Decrypt button shown (subject to permission and decryption-allowed as existing). | Unit (npm test) |
| TC-04 | Frontend | Regression | Existing TC-01 in ImageLogTable.test.js (plain row → "-", no button). | Still passes after change. | Unit (npm test) |

### Test scenarios

#### Scenario 1: Plain row without data/header

1. Render ImageLogTable with one log: datastring `'{"key":"value"}'`, headerstring `'{"h":"v"}'`, no `data`/`header` or empty.
2. Find the Decrypt cell for that row.
3. **Verification**: Cell text is "-"; no button with name "복호화" or "복호화 해제".

#### Scenario 2: Plain row with non-empty data/header (edge case)

1. Render ImageLogTable with one log: datastring and headerstring plain JSON (no `"[...]"` pattern), and `data: "plain"`, `header: "plain"` (or any non-empty string).
2. Find the Decrypt cell.
3. **Verification**: Cell shows "-"; no Decrypt button (hasEncryptedData must be false).

#### Scenario 3: Row with encrypted-style content

1. Render ImageLogTable with one log: datastring containing quoted bracket-wrapped value (e.g. `'{"key":"[encrypted-value]"}'`).
2. Find the Decrypt cell (with decryptionAllowed including the row’s guid if required by existing tests).
3. **Verification**: Decrypt button is present (existing TC-02/TC-03 behavior unchanged).

### Test data

- Use inline props in tests: plain JSON strings for datastring/headerstring; for TC-02 add `data: 'plain'`, `header: 'plain'` (or similar) to the log object.
- No DB or API test data change required; unit tests mock props.

### Test environment

- Frontend: `http://localhost:3001` (or per contract)
- Unit tests: `cd frontend && npm test -- --watchAll=false`

### 3.5 Browser automation verification (optional)

Applicable for manual confirmation: open image log search, run search that returns a plain row (e.g. sample data with `{"id":"plain",...}`), confirm Decrypt cell shows "-" and no button. Procedure: navigate → login → image log search → run search → snapshot → assert decrypt cell content for plain row.

---

## 4. Checklist

### Frontend verification

- [ ] hasEncryptedData logic updated; (data/header presence) not used for encrypted.
- [ ] Encrypted detection uses datastring/headerstring content shape (quoted bracket-wrapped pattern).
- [ ] UI: plain row shows "-", no Decrypt button; encrypted row still shows button (with permission/allowed).

### Backend verification

- [ ] No backend change; N/A.

### Integration

- [ ] Image log search result table behavior confirmed (plain vs encrypted row).

### Documentation

- [ ] Requirement doc completed
- [ ] Code comment added for encrypted-detection rule (if applicable)

---

## 5. Test results

### Test run date

- [Date and time]

### Test results

#### Frontend

[Pass / Fail]

- TC-01, TC-02, TC-03, TC-04: [result]

**Commands:**

```bash
cd frontend && npm test -- --watchAll=false --testPathPattern=ImageLogTable
```

**Outcome:**

- [To be filled by QA or implementer]

### Issues found and resolution

- [If any]

### Next steps

1. Implement hasEncryptedData fix and tests.
2. Run unit tests and verification; update §5.
3. Commit per commit-on-complete (reference this doc).

---

## 6. Error remedy result (cause and action) — for error/bug fix requirements only

- **Requirement ID**: 20260318-image-log-no-decrypt-button-when-plain
- **Root cause**: [To be filled after fix — hasEncryptedData used log.data/log.header presence and generic `[`/`]` or non-empty data/header.]
- **Actions taken**: [Summary of change to hasEncryptedData and tests.]
- **Result**: [Verification method and result.]
- **Completed**: yyyy-MM-dd HH:mm

---

**Author**: Requirements subagent  
**Date**: 2026-03-18  
**Status**: In progress
