# Search field definition items (검색 필드 정의 항목)

Design doc reference for **per-field** search/filter specs. Use this when defining or reviewing fields on user-context screens (activity-log, statistics, and future search-history, pending-approvals, etc.). Single source of truth for field-level sizing, type, limits, and data source.

**Where this is used**: `docs/design/forms-and-filters.md` § Field-level control sizes; **화면별 필드 정의표**는 `docs/design/search-fields-by-screen.md`. Rules/skills reference this doc and search-fields-by-screen.md; they do not duplicate the tables.

---

## 1. Definition items (정의 항목) — all fields

For **every** search/filter field, define the following where applicable:

| Item | Korean | Description | Example |
|------|--------|--------------|---------|
| **fieldId** | 필드 ID | Unique name for the field (form state key, `name` or `id` suffix). | `startDate`, `department`, `ip` |
| **label** | 라벨 | Visible label text. | 시작 일시, 부서, IP 주소 |
| **controlType** | 종류 | Input type or control: `text`, `datetime-local`, `select`. | `datetime-local`, `select` |
| **screens** | 적용 화면 | Which screens show this field. | activity-log, statistics |
| **block** | 소속 블록 | Group: `date-period` \| `user` \| `extra` \| `log-type`. Legacy: `row1-date` = date-period (날짜·기간 블록). All are the same tier for layout. | extra |
| **scopeWhenSelf** | scope=self 시 | `hidden` = do not show when scope=self; `visible` = always show. | hidden |
| **width** | 너비 | CSS width: min-width, max-width (px or em). Same on all screens in scope. | min 140px, max 220px |
| **height** | 높이 | Control height (px). Prefer compact variant 32–36px. | 34px |
| **padding** | 패딩 | Horizontal / vertical padding of control. | 8–10px / 6–8px |
| **constraints** | 제한값 | maxLength (text), date min/max, or allowed set. | maxLength: 5 |
| **validation** | 검증 규칙 | Client-side rule (e.g. start ≤ end). Reference: date-search.md, text-input.md. | startDate ≤ endDate |
| **defaultValue** | 기본값 | Initial value when form loads or reset. | '', '전체', server today |
| **placeholder** | 플레이스홀더 | Placeholder for text input (optional). | IP 주소 |

---

## 2. Select-only items (선택창 전용)

For fields with **controlType: select**, also define:

| Item | Korean | Description | Example |
|------|--------|--------------|---------|
| **dataSource** | 데이터 소스 | The real authoritative API/domain source for options. Do **not** write only an intermediate prop or local variable name such as `departmentList`. If the UI receives a prop, the design doc must still name the upstream contract (API/domain source), response shape, and any scope-specific filtering rule. When a shared filter-options API is the contract, name that shared API explicitly and do not describe an old screen-specific endpoint as the source. | shared filter-options API (department options) → documented option shape |
| **optionValue** | 옵션 value | How option value is taken (e.g. `id`, `value`, or same as label). | value: lt.id |
| **optionLabel** | 옵션 label | How option label is shown (e.g. `displayName`, `name`). | label: lt.displayName \|\| lt.name |
| **emptyOption** | 빈 옵션 | First option for "all" / "전체": `true` or label string. | true (전체) |

---

## 3. Text / date-only items (텍스트·날짜 전용)

- **Text**: `maxLength`, `placeholder`, optional `pattern` or format hint.
- **datetime-local**: `min`, `max` (if any); API format (e.g. `yyyy-MM-dd HH:mm:ss`); default to server date or today.

---

## 4. Cross-field rules (화면 간 동일 적용)

- **Same field, multiple screens**: For a field that appears on both activity-log and statistics (e.g. 부서, 사용자명, 사용자 ID, IP), **width**, **height**, **padding**, **controlType**, and **constraints** must be identical so the two screens look and behave the same.
- **User-context block**: 부서, 사용자명, 사용자 ID share the same block ("사용자"); same sizing and order (부서 → 사용자명 → 사용자 ID) on all screens.
- **Shared select contract**: When the same select field is shared across aligned screens, document one shared option contract in the per-screen doc and reuse it consistently. For the `department` field on activity-log, statistics, and search-history, the screen docs must identify the same authoritative source: the new shared filter-options API for department options, not `/api/statistics/departments`. The docs must also identify the same response shape and the same scope rule. `scope=team` must show only the current user's own department unless a later requirement explicitly updates that contract.
- **Date/period block (날짜·기간 블록)**: Treated as the **same tier** as user block and extra block. When the screen has a period mode (e.g. 일별/월별), the date block may show different fields per mode (일별: start/end date; 월별: year, month); optionally use separate form structure per mode per `forms-and-filters.md` § Form per mode.
- **Width by role**: Use the same min/max width for the same role (e.g. date/single-select, extra-condition) on all screens. Prefer `var(--sf-field-date-min)`, `var(--sf-field-date-max)`, `var(--sf-field-extra-min)`, `var(--sf-field-extra-max)` from `frontend/src/styles/search-filter-standard.css` where the role matches; see `forms-and-filters.md` § Width by role.
- **Block-level width (블록 단위 너비)**: For same-row layout so that 기타 조건 sits in one column to the right, define **block-level** min/max for date-period block, user block, and extra block. Use `var(--sf-field-date-block-min/max)`, `var(--sf-field-user-block-min/max)` from `search-filter-standard.css`; see `forms-and-filters.md` § Filter block tiers and § Width by role.

---

## 4.5 Width by max character count (최대 글자 수에 따른 입력창 너비)

**기준 화면**: 사용자 활동 이력(activity-log). 해당 화면의 필드 너비·제한값을 **표준**으로 하고, **모든 화면**에서 동일 역할·동일 maxLength(또는 동일 제약)일 때 같은 너비를 적용한다.

- **규칙**: 텍스트 입력 필드는 **필드별 최대 글자 수(maxLength)**에 따라 입력창 너비를 정한다. 같은 maxLength(또는 같은 의미의 제약)를 가진 필드는 화면에 관계없이 동일한 min-width / max-width를 사용한다.
- **참조**: `docs/design/search-fields-by-screen.md` §2.1 활동 이력 — 전체 필드. 아래 표준값은 활동 이력 정의를 기준으로 함.

| 역할 / 제약 | maxLength(또는 비고) | width (표준) | 적용 화면 |
|-------------|---------------------|--------------|-----------|
| 사용자명 | 5 (한글 등) | min 100px, 1fr (행 내) | 활동 이력, 통계, 기타 사용자 맥락 화면 |
| 사용자 ID | 8 (숫자 등) | min 100px, 1fr (행 내) | 동일 |
| 날짜/일시 (datetime-local, date) | — | min 140px, max 220px | 검색하기, 활동 이력, 통계 등 |
| 기타 조건 텍스트 (IP 등) | 제한 없음 또는 긴 값 | min 100px, max 200px | 활동 이력, 통계 등 |
| 기타 조건 select (액션 타입 등) | — | min 100px, max 200px | 동일 |
| 로그 타입 select | — | min 140px, max 180px | 통계 등 |
| 부서 select | — | min 100px, 1fr in row | 사용자 블록 있는 모든 화면 |

- **화면 간 동일 적용**: 위 표준값은 **어느 화면에서나 동일**하게 적용한다. 활동 이력에서 사용하는 너비를 통계, 검색 이력, 승인 대기, 사용자 관리, 권한 그룹 등 다른 화면에서도 그대로 사용한다. CSS는 `frontend/src/styles/search-filter-standard.css`의 `var(--sf-field-*-min)`, `var(--sf-field-*-max)` 또는 동일 수치를 사용하고, 컴포넌트 CSS에서 역할별로 다른 값을 재정의하지 않는다.

---

## 5. 표준정의 단일 소스 (화면별 필드 정의)

**화면별 필드 정의는 `docs/design/search-fields-by-screen.md`만 사용한다.** 이 문서(search-field-definition-items.md)는 **스키마**(§1 정의 항목)와 **공통 규칙**(§4 cross-field rules)만 담고, 화면별 구체적 필드값(label, controlType, placeholder 등)은 **중복 기술하지 않는다**. 갱신 시 search-fields-by-screen만 수정하고, §4에 맞는지 검토할 것.

- **동일 이름·다른 성격 필드**(예: startDate/endDate): search-fields-by-screen.md § "동일 이름·다른 성격 필드 — 피드백 요청"에 따라 사용자에게 진행 방향을 묻고 결정 후 반영.
- **갱신 규칙**: 화면별 필드를 바꿀 때는 search-fields-by-screen.md만 수정. 이 문서 §5에는 화면별 필드 내용을 추가·수정하지 않음(중첩·상충 방지).

---

*Related: `forms-and-filters.md`, `search-fields-by-screen.md`, `text-input.md`, `date-search.md`. When changing search/filter UI, apply this definition and search-fields-by-screen.md; see Rules for "follow docs/design/forms-and-filters.md and search-field-definition-items.md".*
