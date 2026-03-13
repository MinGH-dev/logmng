# Forms and filters — design standard

Reusable standard for **form layout** and **filter groups** (search forms, filter panels, settings). Complements layout, text-input, buttons, and date-search standards. **Standard-first**: when a standard is missing or unclear for layout, placement, sizing, or width by role, do not implement arbitrarily; inform the user and ask for standard definition first. See `docs/design/ux-frontend-standard-principles.md` (§2 checklist, §9 Prohibited, §10 Required workflow).

---

## Form layout

- **Structure**: Group related fields (e.g. keyword, date range, type). Use a single container for the whole form or filter block.
- **Grid or flex**: Use CSS Grid or flex for field groups. Example: `grid-template-columns: repeat(auto-fit, minmax(200px, 1fr))` so fields wrap on small screens and stay aligned.
- **Spacing**: Consistent gap between rows and columns (e.g. 16px or 24px). Align with the rest of the design system.

---

## Filter groups (search / list filters)

- **Grouping**: Put filters that belong together in one row or block (e.g. keyword + date range + type in one toolbar or panel).
- **Submit / reset**: Provide explicit "Search" / "Apply" and "Reset" (or "Clear") buttons. Do not rely only on "search on every keystroke" unless the spec requires it; explicit submit reduces accidental API calls and is clearer for accessibility.
- **Error display**: Show validation errors next to or below the relevant field; do not only use a single top-of-form message unless it is a general error. See `text-input.md` and `date-search.md` for field-level error and ARIA.

---

## Buttons in forms

- **Primary**: Submit / Search / Save. One primary per form or filter block.
- **Secondary**: Reset / Clear / Cancel. Place next to primary; follow `buttons.md` for type and placement.

---

## User-context filter order and grouping

On all screens that show user/requester filters (activity-log, statistics, and when added: search-history, pending-approvals, user-management, permission-group), the **UI order** is **부서 → 이름(사용자명) → 사용자 ID**. These three fields are grouped in **one block** (with group label "사용자" or "요청자" by screen). Screen-specific filters (IP, log type, date, action type, etc.) sit in a **separate block** with clear separation (heading or 16–24px gap). Reference: `docs/requirements/20260310-search-ui-unify.md`.

---

## Filter group title placement (공통 규칙)

**적용 범위**: 검색/필터 폼을 쓰는 모든 화면(활동 이력, 통계, 검색 이력, 승인 대기, 사용자 관리, 권한 그룹 등).

- **규칙**: 필터 그룹의 제목(예: "사용자", "요청자", "기타 조건", 화면별 블록 제목)은 **해당 그룹 필드 위에** 배치한다. 인라인으로 필드와 같은 행에 두지 않는다.
- **구조**: 제목 한 줄 → 그 아래 해당 필드(한 줄 또는 여러 줄). "사용자" 블록(legend + 부서·사용자명·사용자 ID)과 "기타 조건" 블록(제목 + 액션 타입·IP 등) 모두 동일한 패턴을 따른다.
- **검토**: UX/QA 검토 시 그룹 제목이 **필드 위(블록 제목)** 인지 확인한다. 인라인 제목은 표준 위반으로 간주한다.

---

## Search form panel width (user-context screens)

**적용 범위**: 활동 이력(activity-log), 통계(statistics) 등 사용자 맥락 검색/필터 화면.

- **규칙**: 검색/필터 **패널**은 두 화면에서 동일한 너비 기준을 사용한다. 페이지 컨테이너 전체 폭을 쓰거나, 제품에서 검색 폼에 명시적 max-width(예: 900px)를 정한 경우에는 동일 값을 적용한다.
- **목적**: 활동 이력과 통계를 오가는 사용자가 동일한 시각적 폭(검색 창 크기)을 경험하도록 한다. Reference: `docs/workflow/ANALYSIS-search-window-size-and-tools-improvement.md`.

---

## When this applies

- **Search forms**, filter panels above tables, settings forms: use this layout and grouping.
- **Data tables**: Table structure is defined in `grid-and-table.md`; filters that sit above the table follow this document and sit in the "optional toolbar" or "header" area of the page structure.

---

## Compact variant (search/filter forms)

For search and filter forms (main log search, activity log, statistics), a **compact variant** reduces vertical and horizontal footprint:

- **Row/field gap**: 8–12px (not 15–20px).
- **Block-to-block gap**: 12–16px.
- **Container padding**: 12–16px (not 20px).
- **Panel background**: `#f8f9fa` (single source: `--sf-panel-bg` in `search-filter-standard.css`). All search/filter panels (activity log, statistics, main search, advanced search) use this so the look is consistent.
- **Form controls**: Input/select height 32–36px; horizontal padding 8–10px, vertical 6–8px.
- **Buttons**: Minimum 44×44px touch target (see `buttons.md`).

Optional: collapsible filter block with default expanded; use `aria-expanded` and `aria-controls` on the toggle; when collapsed, search/apply buttons must remain reachable.

Reference: `docs/requirements/20260310-search-box-layout-improvement.md`.

---

## Single application point for control sizing

To keep field sizes consistent across screens (e.g. activity log and statistics), control sizing must be applied in **one place** only.

- **Wrapper class**: The form or filter root (including any header area that contains date or filter controls, e.g. StatisticsHeader) must use the standard wrapper class (e.g. `sf-compact-panel`) defined in `frontend/src/styles/search-filter-standard.css`. All `input.form-control`, `select.form-control`, and filter action buttons inside that root then get height, padding, border, and font-size from the standard file only.
- **No re-declaration**: Component CSS (and shared blocks like UserContextFilterBlock) must **not** set `height`, `min-height`, `padding`, `border`, `border-radius`, or `font-size` for those controls again. Components may only add layout (grid/flex, gaps) and width by role (see below). See `docs/design/css-standard-and-exceptions.md` §3.1.

---

## Filter block tiers (동일 계층 블록)

On user-context search/filter screens (activity-log, statistics, etc.), the following are treated as **the same tier** so they can sit in one row with consistent layout:

- **Date/period block** (날짜·기간 블록): Start/end date, or year/month when the screen has a period mode (e.g. 일별: start/end date; 월별: year, month). Same group-title-above-fields pattern and block-level width as other blocks.
- **Log-type block** (if present): e.g. 로그 타입 select.
- **User-context block**: 부서 → 사용자명 → 사용자 ID (single block with one group title).
- **Extra-condition block** (기타 조건): Screen-specific fields (IP, action type, etc.).

**Block-level width**: Each block has a **block-level** min/max width (in addition to per-field width) so that multiple blocks fit in one row and "기타 조건" can sit in the same row to the right of the user block. Definitions: `docs/design/search-fields-by-screen.md`; CSS variables (e.g. `var(--sf-field-user-block-max)`, `var(--sf-field-date-block-max)`) in `search-filter-standard.css`. See § Width by role below.

---

## Single row for non-date (날짜 제외 단일 행)

**적용 범위**: 사용자 맥락 검색/필터 화면(활동 이력, 통계, 검색 이력, 승인 대기, 사용자 관리, 권한 그룹 등).

- **규칙**: **날짜·기간 블록**을 제외한 나머지 블록(로그 타입, 사용자, 기타 조건)과 검색/초기화 버튼은 **단일 행**에 배치한다. 즉, 날짜(또는 일별/월별 기간) 블록은 별도 행(예: row1 또는 헤더)에 두고, 그 외 필터 블록과 액션 버튼은 한 행(row2 등)에 배치하여 동일 계층으로 취급한다.
- **목적**: 두 화면(활동 이력·통계) 이상에서 동일한 행 구성과 블록 배치를 유지하고, 요구사항 작성 시 "row1 = 날짜만, row2 = 나머지 단일 행"으로 명확히 정의할 수 있게 한다.
- **요구사항 반영**: 새 요구사항에서 검색/필터 레이아웃을 정의할 때는 본 규칙(날짜 제외 단일 행)과 § Form per mode를 참조한다.

---

## Form per mode (일별/월별 — 모드별 폼 로드)

When a screen has a **period mode selector** (e.g. 일별 / 월별) that changes which date/period fields are shown:

- **When date fields cannot be unified**: 일자/일시 등 날짜 관련 검색 필드를 일별과 월별에서 하나로 합치기 어려운 경우(필드 구성이 다름), **모드별로 별도 폼**을 두어 설계한다. 한 폼에 일별용·월별용 필드를 조건부로 섞지 않고, **일별용 폼**과 **월별용 폼**을 각각 두고 모드 전환 시 해당 폼만 로드한다.
- **Option — separate form per mode**: Load a **different form structure** when the user switches mode. For example: **일별** → render a form that contains only the "일별" date block (시작일, 종료일); **월별** → render a form that contains only the "월별" period block (연도, 월). Other blocks (로그 타입, 사용자, 기타 조건) stay the same; only the date/period block content changes. This keeps date/period as **one block at the same tier** and avoids mixing two different field sets in a single form.
- **Benefits**: Clear structure per mode; date/period block always has one clear role; layout (block order, width by role) stays consistent; easier to apply block-level width so all blocks sit in one row.

When authoring requirements or design for such screens, document whether the implementation uses one form with conditional fields or **separate form per mode**; if the latter, list the form components (e.g. `StatisticsFiltersDaily.js`, `StatisticsFiltersMonthly.js`) and when each is loaded.

---

## Width by role (화면 간 동일 폭)

Field **width** (min-width, max-width) is defined **by role**, not per screen, so the same role looks the same on every screen.

- **Roles**: (1) **Per-field**: date/single-select (row1 date or log-type), user-context block fields, extra-condition fields. (2) **Block-level** (for same-row layout): date/period block, user block, extra block — use `var(--sf-field-date-block-min/max)`, `var(--sf-field-user-block-min/max)`, `var(--sf-field-extra-min/max)` so blocks can sit in one row and 기타 조건 appears in one column to the right. Definitions: `docs/design/search-fields-by-screen.md`; numeric values or CSS variables in `search-filter-standard.css`.
- **Same role, same min/max**: Use the same min/max for the same role on activity log, statistics, and other user-context screens. Do not use different values per screen (e.g. 220px on one and 180px on another for the same role).
- **Shared blocks**: For blocks used on multiple screens (e.g. UserContextFilterBlock), use the same column rule or an optional **block-level max-width** so the block does not stretch to different widths and field sizes stay comparable; this allows the extra block to sit in the same row to the right.

---

## Prohibited (forms/filters)

- **Do not** apply different height, padding, or width to the same role/meaning on different screens.
- **Do not** hardcode size or gap values in component CSS when the standard already defines them; use `var(--sf-*)` or the wrapper.
- **Do not** implement when the standard for the task is undefined or ambiguous; inform the user and request standard definition first per `ux-frontend-standard-principles.md`.

---

*Related: `text-input.md`, `date-search.md`, `buttons.md`, `grid-and-table.md`, `css-standard-and-exceptions.md`, `ux-frontend-standard-principles.md`.*
