# 20260326 - PB FEP log search screen (`pb-fep-log-search`) wireframe alignment

## 1. User requirement

### Requirement description

Deliver the **PB FEP log search** experience for screen ID **`pb-fep-log-search`** so that **visual structure and information architecture (IA)** match the approved wireframe SVG v10. Data continues to use log type **`pb_feplog`** (PB FEP union of `pb_send` / `pb_recv`). **Legacy** screen **`pb-feplog`** and its integration with **`POST /api/logs/db-refactored/search`** must remain **unchanged**; this requirement mandates a **dedicated** backend route and wireframe-oriented response so the new screen does not regress other log types or the legacy PB FEP path.

**Authoritative sources**

- **Wireframe (visual / IA):** `assets/svg/scenes/pg-fep-log-search/pb-fep-log-search-screen-date-time-guided-redraw-sort-centered-improved-v10.svg`
- **Behavior where the SVG is silent:** `assets/svg/scenes/pg-fep-log-search/pb-fep-log-search-screen-date-time-guided-redraw-sort-centered-improved-notes-v11-full.md` (notes v11)
- **DB columns:** `backend/src/main/resources/db/schema_pb_fep.sql` (`pb_send`, `pb_recv`)
- **Current legacy search implementation (reference only):** `LogDbService.searchPbFeplog` — used today by `POST /api/logs/db-refactored/search` when `logType=pb_feplog`

### SVG vs notes v11 (conflict resolution)

- For **visual parity** (labels, control types, layout, column order and header spellings, toolbar placement, stream presentation): **SVG v10 wins** wherever notes v11 disagree.
- **Documented product choice:** The SVG uses **조회일자** + **시작시간** + **종료시간** (three controls). Notes v11 describe **시작일시** / **종료일시** (two datetime fields). For **`pb-fep-log-search`**, product **chooses the SVG model**: one calendar **조회일자\*** and two time pickers **시작시간\*** / **종료시간\*** that combine with that date to form the full start/end bounds for the query (see §2). Semantic validation remains: combined start ≤ combined end; required fields as in §2.
- For **interaction rules** not drawn in the SVG (multi-expand, expand-all cancel on manual collapse, sort cycling, pagination defaults, scroll policy): **notes v11 apply** unless they contradict the SVG on the items in the previous bullet.

### User scenario

1. An operator opens **로그 검색 → PB FEP Log** on the **`pb-fep-log-search`** screen. The **search panel** is a **single compact row**: **조회일자\***, **시작시간\***, **종료시간\***, **Login ID\***, optional **TR Code**, **키워드 검색**, with a **clear horizontal gap** before **검색** and **초기화** aligned to the **right** (SVG proportions: short control heights ~28px wire, buttons ~32px wire).
2. The operator sets a valid combined datetime range, a non-blank Login ID, optional TR Code and comma-separated keywords, and runs **Search**. Results appear in a **dense** table; the **main viewport does not scroll**—only the **grid body** (data rows) scrolls; **pagination/footer stay fixed** (notes §5.3).
3. **Above the table header** (outside `<thead>`), the operator sees **복호화 승인 요청** and **전체 펼치기 ▾** on **one row** (SVG). No decrypt checkbox appears in the search panel.
4. The operator expands one or more rows via **전문보기 ▾** / **접기 ▴**, uses **전체 펼치기**, changes page size (25 / 50 / 100) and page number, and sorts columns. Filters persist after search; expand state persists across page changes per notes §7–§9 where absent from SVG; new search or page size change resets to page 1.
5. When decrypt policy allows, the operator may request decryption approval from the toolbar; permission and backend rules follow `docs/contract.md` and Security guidance (see §2.1).

### Expected outcome

- **Scope:** **`pb-fep-log-search` only** — wireframe layout, dedicated API, and column mapping in §2. **Not** legacy **`pb-feplog`** UI contract or **`POST /api/logs/db-refactored/search`** response shape for that screen.
- **Layout:** Viewport-level scrolling disabled for this view; **only the grid body scrolls**; search panel, grid toolbar row, column header row, and pagination remain visible (notes §5.3, SVG).
- **Search row (SVG):** **조회일자\***, **시작시간\***, **종료시간\***, **Login ID\***, **TR Code**, **키워드 검색**, **검색**, **초기화**; **spaced gap** between keyword field and right-aligned actions; **매체코드** not exposed (notes §3.2); **복호화** not in the search panel (notes §3.3).
- **Validation:** Required: inquiry date + start time + end time + Login ID (non-blank). Combined start ≤ combined end. Keywords: comma-separated tokens. TR Code optional.
- **Toolbar:** **복호화 승인 요청** + **전체 펼치기 ▾** on one row **above** the table header, **outside** the header row (SVG + notes §4).
- **Grid:** Column order and **header labels exactly as SVG**: narrow affordance column (▾/▸), then `log_timestamp`, `tr_code`, `login_id`, `msg_code`, `bmsg`, `log_ch_cd`, `send_recv`, `src_ip`, `dest_ip`, `app_id`, `data`. Dense styling per notes §5.1 where SVG does not contradict.
- **Data / stream row:** **STREAM DATA** chip + monospace stream lines in the expanded region (SVG); row actions **전문보기 ▾** / **접기 ▴**; multi-expand and expand-all behavior per notes §6–§9.
- **Sort:** Initial `log_timestamp` **desc**; per-column **no sort → desc → asc → no sort**; **cumulative** multi-column sort via **`sortSpecs`** on the client with server allowlist (notes §9). Frontend and backend must agree on allowed sort keys matching wireframe column semantics.
- **API:** New **`POST /api/logs/db-refactored/pb-fep-log-search`** (name finalized in §2) returns rows keyed for the wireframe **without** changing legacy `POST /api/logs/db-refactored/search` for **`pb-feplog`**.
- **a11y / empty / loading:** Per notes §10.
- **Stable row keys:** Per notes §12.

**Note:** This screen is **not** a user-context (`scope=self|team|all`) search; requirements pattern **user-block alignment** (activity-log / statistics) **does not apply**.

---

## 2. Design

### 2.1 Security review (optional; when PII / decryption / access control)

- **PII / logs:** PB FEP rows may contain sensitive fields; follow project logging and PII rules.
- **Decryption / approval:** PB FEP (`pb_feplog`) decryption and approval rules per `docs/contract.md`. Wireframe shows **복호화 승인 요청**; Security confirms visibility/disabled/hidden behavior and `createSearchHistory` applicability for this screen.
- [ ] Security review performed (check when Step 3 Security runs)

### Technical design

#### 2.A Search panel (`pb-fep-log-search`) — SVG v10

| Label (product copy) | Control | Required | Notes |
|----------------------|---------|----------|--------|
| 조회일자 * | Date (calendar) | Yes | Wire label **조회일자 \***; combines with start/end times. |
| 시작시간 * | Time | Yes | Dropdown affordance per SVG; combined with 조회일자 for range start. |
| 종료시간 * | Time | Yes | Same; combined with 조회일자 for range end (or end-of-day semantics per Contract — implementer aligns with API parsing). |
| Login ID * | Text | Yes | |
| TR Code | Text | No | |
| 키워드 검색 | Text | No | SVG copy **키워드 검색** (not abbreviated). |
| (gap) | — | — | Deliberate horizontal space before actions (SVG). |
| 검색 | Primary button | — | Right-aligned group. |
| 초기화 | Secondary button | — | |

- **Compact height:** Match SVG proportions (~28px inputs, ~32px buttons in wire) using project tokens; single-row layout priority.
- **Query encoding:** Client sends the resolved **start** / **end** `LocalDateTime` (or ISO strings per contract) to the new endpoint; server validates range and Login ID.

#### 2.B Grid toolbar — SVG v10

- **Single row** immediately **above** the column header row, **not inside** `<thead>`: left group **복호화 승인 요청** (out bordered control in SVG), then **전체 펼치기 ▾** (label includes ▾ per SVG).
- Same row sits **outside** scrollable tbody so it stays visible with the header (scroll policy §2.F).

#### 2.C Table — column order and headers (SVG exact)

First column: **narrow** column for row expand affordance only (**▾** expanded, **▸** collapsed) — no header text in SVG (empty over that column).

Then, left to right, **header text exactly**:

1. `log_timestamp`
2. `tr_code`
3. `login_id`
4. `msg_code`
5. `bmsg`
6. `log_ch_cd`
7. `send_recv`
8. `src_ip`
9. `dest_ip`
10. `app_id`
11. `data`

Sort UI (triangles) appears on headers per SVG for sortable columns; **expand affordance column** is not a sort key.

#### 2.D Field mapping from DB (`pb_send` / `pb_recv` per `schema_pb_fep.sql`)

Rows are the existing **UNION ALL** of `pb_send` and `pb_recv` (same as `searchPbFeplog`), with **wireframe-facing** keys in the **new** API response (DTO or `Map` with stable key names below). Legacy search may keep raw `user_id`, `status_code`, etc.

| Wireframe column / key | DB / SQL source | Rule |
|------------------------|-----------------|------|
| `log_timestamp` | `log_timestamp` | Formatted consistently (existing formatter). |
| `tr_code` | `tr_code` | |
| `login_id` | `user_id` | Display key `login_id`; filter still targets `user_id`. |
| `msg_code` | `status_code` | Expose as string suitable for display (e.g. zero-padded or raw string); aligns with SVG samples (`0000`, `W001`) — **Contract** documents format if normalized. |
| `bmsg` | `error_message` | |
| `log_ch_cd` | `device_type` | |
| `send_recv` | UNION branch | Literal **`SEND`** when row from `pb_send`, **`RECV`** when from `pb_recv` (SVG shows SEND/RECV). |
| `src_ip` | `ip_address` | Single IP column in schema; use for **src_ip** as client-facing source address. |
| `dest_ip` | *(no dedicated column)* | **Placeholder rule until product adds column:** return **empty string** or **`—`** in API JSON, or a documented constant; **do not invent** a second IP from the same `ip_address`. If contract later defines upstream IP derivation, update this row. |
| `app_id` | `session_id` (primary) | **Preferred mapping:** `app_id` ← `session_id` as application/session correlator when no separate column exists. If `session_id` null, return empty. Alternative agreed in contract: substring of `user_agent` — only if Security/Product approve. |
| `data` (cell / stream source) | `request_data`, `response_data` | **Stream payload** for expand/stream lines: follow **existing PB FEP decrypt rules** (e.g. when decrypt approved / flags match `LogDbService` behavior: use decrypted fields for display where applicable; redact or mask per contract). Collapsed row may show summary or truncated hint per Frontend UX; SVG emphasizes expanded **STREAM DATA** block. |

**Identifiers:** Each row must expose a **stable** business key for `expandedRowKeys` (e.g. composite `log_type` + `id` or DB `id` with branch discriminator) documented in API contract.

#### 2.E Expand / stream row — SVG + notes

- Expanded block: **STREAM DATA** chip (styled per SVG: small rounded chip, blue emphasis) + **monospace** stream lines (`.stream-line` class equivalents: ~10.5px wire, log-friendly).
- Row actions: **전문보기 ▾** (collapsed) / **접기 ▴** (expanded); not toggle pill (notes §6).
- **Multi-expand:** `expandedRowKeys`; **전체 펼치기**; manual collapse clears global expand-active state per notes §7.

#### 2.F Scroll / layout

- **Main content viewport:** **No** document-level scroll for this screen’s main column.
- **Scroll container:** Only the **table body** (data rows + expanded row panels) scrolls; internal scrollbar at grid edge as in SVG.
- **Fixed:** Search panel, grid toolbar, **thead**, pagination bar (notes §5.3).

#### 2.G New backend API (mandated)

**Goal:** Avoid changing **`POST /api/logs/db-refactored/search`** behavior for **`logType=pb_feplog`** used by **legacy `pb-feplog`** and any other callers.

**Proposed path (finalize in contract):** `POST /api/logs/db-refactored/pb-fep-log-search`

**Requirements:**

- **Auth / access:** Same enforcement as today for **PB FEP** / screen **`pb-fep-log-search`** (and `logType` **`pb_feplog`** where applicable): reuse existing log-type and screen permission checks; do not weaken checks.
- **Request body:** Align with wireframe filters (resolved start/end datetime, `loginId`, optional `trCode`, keywords list), **pagination** (`page`, `pageSize` with defaults matching notes: default **25**, allowed **25 / 50 / 100**), and **`sortSpecs`** — ordered list `{ field, direction }` with **server-side allowlist** matching wireframe sortable columns / DB aliases (reject unknown fields).
- **Response:** **Wireframe-keyed row objects** (§2.D keys) **or** a documented DTO; **must not** require legacy `pb-feplog` consumers to adopt this shape. Pagination metadata same family as existing log search responses unless contract specifies a slimmer DTO.
- **Implementation note:** May **delegate** to shared private logic with `searchPbFeplog` after request mapping, but the **public contract** for legacy search remains untouched.

#### Problem analysis (summary)

1. **Screen split:** Product introduces **`pb-fep-log-search`** as the wireframe-aligned route; **`pb-feplog`** stays on legacy endpoint and UI until explicitly retired.
2. **IA mismatch resolved:** Notes v11 vs SVG on datetime controls — **SVG wins** (§1).
3. **Layout:** Current `LogGrid` / `LogTable` / `DataTable` may not enforce grid-body-only scroll; CSS/component split needed for **this** screen path.
4. **Columns:** Legacy table uses different column names; new API + table must expose **SVG** headers and §2.D mapping.
5. **Missing DB columns:** `dest_ip` / richer `app_id` need explicit placeholder or mapping rules (§2.D).
6. **Sort:** Cumulative **`sortSpecs`** requires endpoint allowlist and ORDER BY builder (reuse patterns from `LogDbSortSpec` / `buildPbFeplogOrderBy` where possible).

#### Diagnostic phase (mandatory for error/bug fix only)

*Not applicable (feature requirement).*

#### Solution approach (by scope)

**Frontend (`pb-fep-log-search` route / view):**

- Implement search panel per §2.A; wire to **`POST .../pb-fep-log-search`** only for this screen.
- Toolbar §2.B; table structure §2.C; expanded stream §2.E; layout §2.F.
- Do **not** repoint legacy **`pb-feplog`** to the new endpoint without a separate requirement.

**Backend:**

- Add controller handler for **`POST /api/logs/db-refactored/pb-fep-log-search`** (final path per contract).
- Map request → query (reuse PB FEP union SQL / service layer); map result rows → §2.D wireframe keys; validate **`sortSpecs`** allowlist.
- Leave **`POST /api/logs/db-refactored/search`** and `searchPbFeplog` observable behavior for legacy callers **unchanged**.

**DB:**

- No migration required for v1 if placeholder rules apply; future columns for `dest_ip` / real `app_id` would be a separate DB requirement.

**Contract / spec:**

- Document new path, request/response for wireframe rows, **`sortSpecs`**, validation errors.
- Update `docs/api-definition.md` (and `docs/contract.md` if needed).

**Design docs:**

- Align `docs/design/search-fields-by-screen.md` **§1.1 PB FEP** (or `pb-fep-log-search` row) with **SVG labels** (조회일자 / 시작시간 / 종료시간) so standards match product choice.

### Pattern 3.4 (search/filter UI consistency) — applicability

- **§4 user-block field size:** **N/A**
- **Shared search control standards:** **Yes** — update design docs for **`pb-fep-log-search`** per §2.A and SVG.

### §2.4 verification (mandatory when pattern §3.4 applies to aligned user-block screens)

| Check | Status |
|-------|--------|
| User-block field size in §1 | **N/A** |
| Form/panel width | Follow **`docs/design/forms-and-filters.md`** |
| §3 TC for user-block cross-screen | **N/A**; use §3 SVG + API + regression TCs |

### Affected scopes and change targets (verification)

| Scope | Affected? | §2 subsection and change file list complete? |
|-------|------------|-----------------------------------------------|
| Backend | Yes — new endpoint, mapping, sort allowlist | Yes |
| Frontend | Yes — new screen path / components | Yes |
| DB | No (v1 placeholders) | Yes |
| Contract / Spec | Yes | Yes |
| Cursor tools | Optional `log-search-domain` SKILL | Yes |

### Planned change file list (tentative)

#### Frontend

- View / route for **`pb-fep-log-search`**; menu entry per product (`menuTree.js`, `App.js` as needed).
- Search panel component or variant: §2.A fields, gap, right-aligned buttons.
- `LogGrid` / `LogTable` **or** screen-specific grid: toolbar §2.B, columns §2.C, scroll §2.F, expand §2.E.
- **No** change required to legacy **`pb-feplog`** files **until** explicitly scoped (regression TC still validates legacy).

#### Backend

- `LogDbController` (or equivalent): **`POST .../pb-fep-log-search`**
- Service layer: mapping to §2.D, reuse query core from `searchPbFeplog` internally if appropriate.
- DTOs / request validation for wireframe search + **`sortSpecs`**
- Tests: API + ORDER BY allowlist

#### Contract / documentation

- `docs/api-definition.md`, `docs/contract.md`
- `docs/design/search-fields-by-screen.md` — PB FEP / `pb-fep-log-search` row

#### Cursor tools

- `.cursor/skills/log-search-domain/SKILL.md` — document new endpoint and screen id

---

## 3. Test approach

### Test case list (required)

| ID | Scope | Type | Scenario (input / condition) | Expected result | Verification |
|----|-------|------|------------------------------|-----------------|--------------|
| TC-SVG-01 | Frontend | Normal | Compare mounted **`pb-fep-log-search`** UI to SVG v10 checklist (§3.1). | **Search:** labels 조회일자\*, 시작시간\*, 종료시간\*, Login ID\*, TR Code, 키워드 검색; gap + right 검색/초기화; compact heights. **Toolbar:** 복호화 승인 요청 + 전체 펼치기 ▾ above header, outside thead. **Table:** column order and headers match §2.C; first column ▾/▸ only. **Stream:** STREAM DATA chip + monospace lines when expanded. | Manual / visual |
| TC-SVG-02 | Frontend | Normal | Scroll tall result set. | Only grid body scrolls; search, toolbar, thead, pagination fixed. | Manual |
| TC-API-01 | Backend | Normal | `POST /api/logs/db-refactored/pb-fep-log-search` with valid body and auth. | 200; rows include §2.D keys; pagination present; `send_recv` is SEND or RECV. | Integration / mvn test |
| TC-API-02 | Backend | Edge | `sortSpecs` with invalid field name. | 4xx; no SQL injection; allowlist enforced. | Unit / integration |
| TC-API-03 | Backend | Normal | `sortSpecs` multi-column order. | `ORDER BY` order matches request sequence. | Unit |
| TC-REG-01 | Regression | Normal | Open legacy **`pb-feplog`** (or equivalent legacy route); capture network. | Requests use **`POST /api/logs/db-refactored/search`** only; **no** calls to **`pb-fep-log-search`** endpoint. | Manual / browser devtools |
| TC-REG-02 | Regression | Normal | Other log types using **`db-refactored/search`**. | No behavior or response shape regression from new code path. | Smoke |
| TC-03 | Frontend | Edge | Combined start after combined end. | Validation error; no submit. | Unit |
| TC-04 | Frontend | Normal | Expand two rows; **전체 펼치기**; collapse one. | Global expand-active clears per notes §7. | Manual |
| TC-05 | Security | Normal | Decrypt toolbar when policy denies. | Aligned with contract / Security §2.1. | Manual |

### 3.1 SVG v10 visual checklist (for TC-SVG-01)

- [ ] Title area: **PB FEP Log** (or product-final title for this screen).
- [ ] Search row field order and **asterisks** on required labels match §2.A.
- [ ] Keyword label **키워드 검색** (not “키워드” only).
- [ ] **전체 펼치기** label includes **▾** in default state (SVG).
- [ ] Toolbar row **between** search panel and **thead**.
- [ ] Table headers: `log_timestamp`, `tr_code`, `login_id`, `msg_code`, `bmsg`, `log_ch_cd`, `send_recv`, `src_ip`, `dest_ip`, `app_id`, `data` (exact spelling, lowercase with underscores).
- [ ] Expanded rows: **STREAM DATA** chip + stream lines styling; actions **전문보기 ▾** / **접기 ▴**.
- [ ] Pagination: center page numbers, right page size (25 / 50 / 100), default 25.

### Test scenarios

#### Scenario 1: SVG parity

1. Open **`pb-fep-log-search`**; compare to SVG file side-by-side; run TC-SVG-01.

#### Scenario 2: API and regression

1. Exercise new endpoint with authenticated client; verify §2.D mapping.
2. Open **`pb-feplog`**; confirm TC-REG-01.

### Test data

- Seeded `pb_send` / `pb_recv` per project setup; include rows with null `error_message` / `session_id` to test placeholders.

### Test environment

- Frontend: `http://localhost:3001` (per contract)
- Backend: `http://localhost:9200`
- Database: PostgreSQL (per project)

### 3.5 Browser automation verification (optional)

- **Applicable:** TC-SVG-01, TC-SVG-02, TC-REG-01  
- **Reference:** `docs/workflow/BROWSER-AUTOMATION-VERIFICATION-POLICY.md`

---

## 4. Checklist

### Frontend verification

- [ ] API parameters validated
- [ ] UI behavior confirmed
- [ ] Error handling verified

### Backend verification

- [ ] New endpoint tests written and run
- [ ] Legacy search tests still pass
- [ ] Logs checked

### Integration

- [ ] End-to-end flow tested for **`pb-fep-log-search`**
- [ ] Legacy **`pb-feplog`** regression confirmed

### Documentation

- [ ] Requirement doc completed
- [ ] Contract/api-definition updated for new endpoint

---

## 5. Test results

*Note: The runs below predate the **dedicated `pb-fep-log-search` endpoint** and **SVG-authoritative §2** revision; re-run §3 after implementation.*

### Test run date

- 2026-03-26 (historical)

### Test results

- Backend unit tests: `cd backend && mvn test -q` — **pass** (as of historical run).
- Frontend unit tests: `cd frontend && npm test -- --watchAll=false` — **pass** (as of historical run).
- Manual/browser: TC-SVG-*, TC-API-*, TC-REG-* pending after delivery.

---

## 7. Final version (Korean) — add after all verification is complete

Per `docs/workflow/DOCUMENT-LANGUAGE-POLICY.md`, add a Korean summary after QA verification passes.

---

**Author**: Requirements (subagent)  
**Date**: 2026-03-27  
**Status**: In progress — revised for **SVG v10 authority**, screen **`pb-fep-log-search`**, and **mandated new API**  
