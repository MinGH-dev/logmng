# 20260409 - Advanced search field name picker and value caret

## 1. User requirement

### Requirement description

On the **Java FW Image Log** advanced (conditional) search UI, users building filters need to know which **field names** are valid for search and how to enter them. The product shall show a **deduplicated list of searchable field names** when the user focuses or selects the **field-name entry area**, restrict the list to **fields that participate in search** (same authority as existing metadata/validation), and when the user **picks a field**, insert the **canonical text fragment** used for that field in this UI. After insertion, the **text caret must land in the value position** so the user can **type the search value immediately**, without the caret remaining in the field-name segment or requiring an extra click.

**Scope note**: This applies to the **advanced search** experience used with **`java_fw_imglog`** (see `frontend/src/components/AdvancedSearchForm.js`, embedded from `LogGrid`). It does **not** redefine main search / statistics / activity-log shared filter blocks; those follow `docs/design/search-fields-by-screen.md` where applicable. Advanced imagelog filter fields are governed by **field metadata and backend filter validation** as referenced in §2.

### User scenario

1. The user opens **Java FW Image Log** search and uses the **advanced / conditional** search input (token-style area with field / operator / value flow).
2. The user **focuses** or **clicks into** the control where they enter the **next field name** (the main text input used to compose conditions).
3. **Today**: Suggestions for fields appear mainly after the user types (debounced `fetchSuggestions`); an empty focus does not reliably present a full **searchable field list**.
4. **Expected**: A **list of searchable field names** (labels and/or canonical names per product rules) appears on focus/select, filtered to **only fields usable in search** for this log type.
5. The user **selects** one field from the list.
6. The UI inserts the **text format** the application expects for that field in this composer (aligned with token / inline parsing and the eventual `filters[]` payload — see §2).
7. The **caret is placed in the value position** (immediately after the delimiter that separates field name from value, or after a **default operator** when the format requires an operator before the value — see §2), so typing continues in the **value** segment, not inside the field name.

### Expected outcome

- **Discoverability**: Users see **all searchable fields** for `java_fw_imglog` advanced search when entering a field name, without having to guess names or type arbitrary prefixes first.
- **Correctness**: Listed fields and inserted fragments **align** with **`GET /api/log-types/java_fw_imglog/fields`** (`FieldMetadataService` / `FieldMetadataResponse`) and with backend filter **allowlist** behavior (`LogDbService.validateFieldName` — see §2). Any discrepancy between metadata and SQL allowlist is **resolved in implementation** (document in §5 / contract if API or code changes).
- **Efficiency**: After choosing a field, the user can **type the value in one continuous action**; caret is **not** left in the field-name portion, and the user is **not** forced to complete an extra step that leaves focus in the wrong segment (unless a mandatory operator must appear before the value — then caret is after that operator per §2).
- **Consistency**: Inserted “text format” matches the **same semantic model** already used by `AdvancedSearchForm` (`parseContext`, token types, `buildFiltersFromTokens`, `POST .../search` with `filters`).

## 2. Design

### 2.1 Security review (optional; when PII / decryption / access control)

- [ ] Security review performed (check if applicable)

This feature surfaces **field names and labels** already exposed by **`GET /api/log-types/{typeId}/fields`** and used in search UI. It does **not** by itself broaden decryption or log row access. If **encrypted** fields (`datastring`, `headerstring` per metadata) remain searchable only under existing product rules, **do not** change server enforcement; UI copy may reference encryption warnings if already present elsewhere.

- Risks: Low — same data surfaces as today; avoid suggesting fields that are not actually accepted by the backend filter pipeline.
- Acceptance / recommendations: Keep the picker list **consistent** with **metadata + allowlist**; no new endpoints required unless product chooses a dedicated “searchable-only” projection.

### Technical design

#### Codebase summary (verified)

**Frontend — `AdvancedSearchForm` (`frontend/src/components/AdvancedSearchForm.js`)**

- Loads **`fieldMetadata`** via **`GET /api/log-types/java_fw_imglog/fields`** when `logType.id === 'java_fw_imglog'`.
- Suggestions: **`GET /api/search/suggest`** with query `logType`, `context` (`field` | `operator` | `value`), optional `prefix`, optional `fieldName` (see `docs/api-definition.md` §6).
- **Inline parsing**: `parseContext` treats the segment after the last space; if it contains `:`, it splits **`field` : `value`** and sets context to **`value`** for suggestions.
- **Token model**: Selecting a field suggestion calls `handleSuggestionSelect` in **`field`** context: `addToken({ type: 'field', value, label })`, clears input, sets context to **`operator`**, then fetches operator suggestions — so the user **does not** immediately land in “value typing” after a field pick.
- **Committed filters**: `buildFiltersFromTokens` emits `filters: [{ field, operator, value }]` for `filter`-type tokens and incomplete `field` tokens with operator/value from state.
- **Display**: Confirmed filter chips show `fieldLabel`, `operatorLabel`, `valueLabel`; field tokens render as `` `${label || value}:` ``.

**Backend — searchable field names**

- **Metadata (user-facing catalog)** — `FieldMetadataService.getJavaFwImglogFieldMetadata()` returns **nine** entries with canonical `name` (lowercase snake):

  | `name` (canonical) | Notes |
  |--------------------|--------|
  | `insert_time` | `operatorsAllowed`: `>=`, `<=`, `>`, `<`, `=` |
  | `application` | `:`, `=`, `IN`, `NOT IN` |
  | `servicegroup` | same |
  | `service` | same |
  | `status` | same; `enumValues`: input, output, error |
  | `guid` | `:`, `=` |
  | `datastring` | `:`, `~` (encrypted flag in metadata) |
  | `headerstring` | `:`, `~` (encrypted flag in metadata) |

- **SQL filter allowlist** — `LogDbService.validateFieldName` allows: `application`, `servicegroup`, `service`, `status`, `guid`, `datastring`, `headerstring`, `insert_time`, **`data`**, **`header`**.

**Gap to track**: **`data`** and **`header`** appear in **`validateFieldName`** but **not** in **`FieldMetadataService`** list. The requirement is that the **picker shows only fields that are actually searchable** in this flow; implementers **must** either (a) expose `data` / `header` in metadata if product confirms they are user-facing searchable fields, or (b) **omit** them from the picker if they are internal-only — **verify** against product and `appendFilterCondition` usage before release.

**Contract / API references**

- `docs/api-definition.md` §4.3 — `GET /api/log-types/{typeId}/fields`
- `docs/api-definition.md` §6 — `GET /api/search/suggest`
- `docs/contract.md` — environment and log-search family (see DB log search for `java_fw_imglog`)

#### Problem analysis

1. **Empty focus**: `onFocus` only re-opens suggestions when `inputValue.trim()` and `suggestions.length > 0`, so users do not get a **full field list** on focus alone.
2. **Caret / flow**: Current **field → operator → value** stepwise flow (chips + suggest API) does not meet the expectation to **type the value immediately** after picking a field; users may want **inline `field:value`** or a **default operator** with caret in the value segment.
3. **Single source of truth**: The picker must not show arbitrary strings; it must reflect **metadata + backend acceptance** to avoid 400/ignored filters.

#### Diagnostic phase (mandatory for error/bug fix only)

*Not applicable — feature requirement.*

#### Solution approach

Structure by scope.

**Frontend:**

- On **focus** and when the user is in **field-name entry mode** (equivalent to current `currentContext === 'field'` and/or the input is composing a new field segment — align with existing state machine), **load and show** the list of **searchable fields** for `java_fw_imglog`.
  - **Source of truth**: Prefer **`fieldMetadata`** from **`GET /api/log-types/java_fw_imglog/fields`** already loaded in the component; **filter** to fields that are valid for **filter/search** (exclude or gray out non-filterable fields only if metadata introduces such flags — today all listed fields are filter-capable; **verify** `FieldMetadataResponse` shape in code).
- When the user **selects** a field from the list:
  - Insert the **canonical textual fragment** for this composer. Minimum alignment: the fragment must allow **`parseContext`** and token building to treat the **next typing** as **value** where applicable. Product-default options (pick one during implementation and **verify** in §3):
    - **Option A (inline)**: Insert **`{name}:`** (and if multiple operators are mandatory before a value, insert **`{name} {defaultOperator} `** with caret before value — **default operator** should be the **first** entry in `operatorsAllowed` or explicit product rule per field type).
    - **Option B (token + value)**: Keep chip tokens but after selection, transition to **value** context with **input focused** and **empty value text**, with the same effect as “caret in value position” (no editing inside the field label).
- **Caret placement**: After insertion, **focus** the input and set **selection/caret** so the next character typed applies to the **value** (not the field name). Use **`inputRef`**; for controlled input, set `inputValue` and **`selectionStart` / `selectionEnd`** in the same tick (e.g. `requestAnimationFrame` or React effect) per browser constraints.
- **Keyboard / a11y**: Preserve **Arrow / Enter / Escape** behavior for the dropdown; ensure the new list does not trap focus (see WCAG patterns for combobox if implemented as such).
- **Tests**: Add or extend **Jest** tests for: focus opens list, selection inserts fragment, caret index after insertion, regression for `buildFiltersFromTokens` / search request shape.

**Backend:**

- **No API change required** for the minimal feature if the UI reuses **`GET /api/log-types/java_fw_imglog/fields`**. If product resolves the **`data` / `header`** gap, Backend may **extend** `FieldMetadataService` (and contract) — only with explicit product confirmation.

**DB:**

- None.

### Affected scopes and change targets (verification)

| Scope | Affected? | §2 subsection and change file list complete? |
|-------|------------|-----------------------------------------------|
| Backend | Optional (metadata alignment only) | [ ] |
| Frontend (config UI + view screen) | Yes — view (`AdvancedSearchForm` in `LogGrid`) | [ ] |
| DB | No | [ ] |
| Contract / Spec | Optional if Backend or API docs change | [ ] |
| Cursor tools (skills, specs) | Optional — update `log-search-domain` or UI skill if behavior is canonical | [ ] |

### Planned change file list (expected change targets)

**(Planned at authoring. Implementing agent (Step 4) confirms or amends this list when implementation is complete.)**

#### Frontend

- `frontend/src/components/AdvancedSearchForm.js`
  - Focus/field-context: show searchable field list; selection inserts canonical fragment; caret in value position; reconcile with `parseContext` / tokens / `handleSuggestionSelect`.
- `frontend/src/components/AdvancedSearchForm.css`
  - Styles for list visibility, focus ring, dropdown overlap — **only** if layout requires (respect `search-filter` / compact rules where shared wrappers apply; this component may use existing compact variant — verify `20260310-search-box-layout-improvement` notes in CSS header).
- `frontend/src/components/LogGrid.js`
  - Only if props/handlers must change for the advanced search block (prefer no change).

#### Backend

- `backend/src/main/java/com/logmng/service/FieldMetadataService.java` — **only if** product adds `data` / `header` (or other) to public metadata for the picker.
- `docs/api-definition.md` — **only if** response shape or field list changes.

#### DB

- None.

## 3. Test approach

### Test case list (required)

| ID | Scope | Type | Scenario (input / condition) | Expected result | Verification (unit / integration / manual) |
|----|-------|------|------------------------------|-----------------|--------------------------------------------|
| TC-01 | Frontend | Normal | `java_fw_imglog` selected; user focuses advanced search field input with empty value | List shows searchable field names (from metadata; at least the nine names in §2) | Unit (Jest): mock `fetch` for `/api/log-types/java_fw_imglog/fields`, assert rendered options or handler |
| TC-02 | Frontend | Normal | User selects field **`status`** from list | Inserted text/token state matches canonical format; subsequent typed characters build **value** | Unit: assert `inputValue` / caret position after select |
| TC-03 | Frontend | Normal | User selects **`insert_time`** | Inserted fragment respects operators (not only `:`); caret in **value** position per product rule for date/timestamp fields | Unit |
| TC-04 | Frontend | Edge | User focuses field input while `fieldMetadata` still loading | No crash; list appears after load; optional loading indicator per UX standard | Unit or manual |
| TC-05 | Frontend | Regression | Complete a filter and run search | `onSearch` receives `filters` with correct `field`, `operator`, `value`; request matches existing `LogGrid` advanced handler | Unit (mock `onSearch`) |
| TC-06 | Integration | Normal | End-to-end: pick field, type value, search | Backend accepts request (200) for valid filter; no 400 from invalid field name | Manual / browser MCP |
| TC-07 | Frontend | Edge | Escape / blur closes list without corrupting tokens | Prior tokens unchanged; input state consistent | Unit |

### Test scenarios

#### Scenario 1: Focus shows full searchable list

1. Open Java FW Image Log with advanced search visible.
2. Focus the field-name input with no prior typing.
3. Verify the dropdown lists all searchable fields from metadata (and labels if shown).

#### Scenario 2: Select field → type value immediately

1. Open the field list; choose **`application`**.
2. Type a value without clicking elsewhere.
3. Verify characters appear in the **value** segment and a search can be executed with expected `filters`.

### Test data

- Log type **`java_fw_imglog`** available; user with **`java-fw-imagelog`** screen access (per contract).
- No special DB rows required beyond existing imagelog data for integration smoke.

### Test environment

- Frontend: `http://localhost:3001` (or per contract)
- Backend: `http://localhost:9200`
- Database: per project dev setup

### 3.5 Browser automation verification (optional — for frontend-heavy requirements)

- **Applicable TCs**: TC-06 (and manual parts of TC-01–TC-05 if not fully covered by Jest).
- **Procedure**: Login → navigate to Java FW Image Log → open advanced search → `browser_snapshot` → focus input → confirm list → select field → type value → trigger search → verify network payload or UI result.
- **Reference**: `docs/workflow/BROWSER-AUTOMATION-VERIFICATION-POLICY.md`

## 4. Checklist

### Frontend verification

- [ ] API parameters validated
- [ ] UI behavior confirmed
- [ ] Error handling verified

### Backend verification

- [ ] API test cases written and run (if Backend changed)
- [ ] Logs checked
- [ ] Performance checked (if applicable)

### Integration

- [ ] End-to-end flow tested
- [ ] Edge cases tested

### Documentation

- [ ] Requirement doc completed
- [ ] Code comments added (if applicable)

## 5. Test results

### Test run date

- (Pending — fill after QA run)

### Test results

#### Frontend

- (Pending)

#### Backend

- (Pending)

**Commands:**

- (QA: one command per TC from §3 after implementation)

**Outcome:**

- (Pending)

### Issues found and resolution

- (Pending)

### Next steps

1. Implement per §2 Frontend scope.
2. Run §3 tests; update §5.
3. If `data`/`header` decision affects Backend, update contract and `FieldMetadataService`.

## 6. Error remedy result (cause and action) — for error/bug fix requirements only

*Not applicable.*

---

## 7. Final version (Korean) — add after all verification is complete

Per `docs/workflow/DOCUMENT-LANGUAGE-POLICY.md`, add **§ Final version (Korean)** after QA verification and before/with the closing commit.

### Final Korean summary (placeholder)

- **Requirement description**: (번역 예정)
- **Expected outcome**: (번역 예정)
- **Verification result**: (번역 예정)

---

**Author**: Requirements (subagent)  
**Date**: 2026-04-09  
**Status**: In progress
