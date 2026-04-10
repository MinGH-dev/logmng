# SVG wireframe — Activity log audit evidence (designer handoff)

**Purpose**: Single reference for **SVG wireframes** covering audit-evidence behavior on the user activity log flow: list, detail, access audit, and optional retention/export approval. Align wireframes with authoritative screen specs in [`docs/requirements/20260330-audit-evidence-activity-log-conservative.md` §1.1](../requirements/20260330-audit-evidence-activity-log-conservative.md#11-screen-specification-for-svg-wireframes-authoritative-for-layout).

**Language**: 본문은 **한국어**; 화면에 그대로 쓰이는 컨트롤·컬럼 라벨은 **English UI labels**를 유지한다.

**Related design standards**

- [Grid and table](grid-and-table.md) — 테이블 구조, 정렬, 페이지 크기, 푸터
- [Layout and navigation](layout-and-navigation.md) — 셸, 사이드바, **z-index** 계층
- [Search fields by screen](search-fields-by-screen.md) — 활동 이력(activity-log) 필드·블록 (§2)
- [Forms and filters](forms-and-filters.md) — 필터 그룹, compact variant, 사용자 블록 순서

---

## 1. SVG wireframe checklist — Screens 1–4

### Reading order (모든 전체 페이지: Screen 1, 3, 4)

위에서 아래로, 한국어/영문 혼용 시 **시각적 흐름**은 다음과 같다.

1. **Page header** — 제목 + 짧은 설명 한 줄  
2. **(선택) 전역 오류 배너** — 헤더 직후 또는 그리드 위  
3. **Filter panel** — 접기 가능 시 접기 컨트롤 포함; 기본은 펼침 권장(와이어프레임)  
4. **Toolbar** — **Search**, **Reset**; 조건부 **Export**  
5. **Results table** — 스크롤 영역  
6. **Grid footer** — 총 건수, **Rows per page**, 페이지 이동  
7. **Loading overlay** — 전역 또는 그리드 영역 (와이어프레임에 영역만 표시)

데이터 테이블 화면의 **블록 순서** 상세는 [grid-and-table.md](grid-and-table.md) § Page structure와 동일 계열로 맞춘다: `header → [toolbar/filters] → [actions] → table`.

**Screen 2 (modal / drawer)** 읽기 순서: **Header (title + close)** → **Metadata** → **Structured detail** → **Copy payload subsection** (해당 시) → **Footer (Close, optional link)**.

---

### Screen 1 — Activity log search & list

| Item | Designer notes |
|------|----------------|
| **Working name** | User Activity Log — List |
| **Route / view (placeholder)** | `activity-log` |
| **Mandatory regions (top → bottom)** | ① Page header — title e.g. **User activity log**, short description ② Filter panel (collapsible optional; default **expanded** for wireframe) ③ Toolbar: **Search**, **Reset**; optional **Export** ④ Results grid ⑤ Grid footer: pagination, **rows per page** ⑥ Auxiliary: loading region, inline error banner region |
| **Mandatory filter labels (English UI)** | **Start date**, **End date** (row 1 권장) · User block: **Department**, **User name**, **User ID** (순서 고정: 부서 → 이름 → ID — [forms-and-filters.md](forms-and-filters.md) § User-context) · **Action type** (multi-select or dropdown) · **Scope** — non-admin 에게는 hidden 또는 read-only (`screenScopes['activity-log']` 반영) |
| **Mandatory table columns (English UI)** | **Timestamp** · **User** · **Action type** · **Summary** · **IP** · **Actions** (row action: **View detail**) |
| **States (와이어프레임 variant)** | **Default** — 빈 테이블 또는 초기 필터 · **Loading** — 스켈레톤 또는 스피너; Search/Export 비활성 · **No results** — 그리드 본문 empty state · **Error** — 배너 + 메시지 |
| **Masked vs privileged (list)** | **Non-privileged**: 그리드에 **full `action_detail`** 없음; IP 등은 역할에 따라 마스킹 가능 · **Privileged**: **Summary** 열에서만 더 풍부한 요약 가능; **그리드에는 비밀 평문 금지** |
| **Export visibility** | **Export**는 **반출 승인 요건이 스코프에 포함되고 역할이 허용할 때만** 노출. 미확정 시 와이어프레임에 “gated” 주석 또는 별도 variant(숨김) 스크린샷 2장. 요건: [§1.1 Screen 1](../requirements/20260330-audit-evidence-activity-log-conservative.md#11-screen-specification-for-svg-wireframes-authoritative-for-layout) |

필터 필드 크기·블록 정의는 [search-fields-by-screen.md](search-fields-by-screen.md) §2 활동 이력과 정렬한다.

---

### Screen 2 — Activity log detail (row detail)

| Item | Designer notes |
|------|----------------|
| **Working name** | User Activity Log — Detail |
| **Surface** | Modal **또는** right **drawer** (구현 선택) |
| **Mandatory header (English UI)** | Title **Activity detail** · **Close** · optional **Copy log id** |
| **Metadata block (labels)** | `id`, **User ID**, **Username**, **Action type**, **Created at**, **IP address**, **Request method**, **Request path**, **Response status**, **Success**, **Error message** (해당 시) |
| **Structured detail** | `action_detail` — JSON tree 또는 key-value (expand/collapse 섹션) |
| **Copy payload subsection** (`action_type`이 인앱 복사일 때) | **Truncated** preview · **“truncated” badge** · character count · **View full content** — **privileged only** |
| **Footer** | **Close** · optional **Open access audit for this resource** (Screen 3이 있을 때) |
| **States** | **Loading** · **Error** · **Masked** — 민감 키 **MASKED** / redacted · **Privileged unmasked** — 허용 상세; 전체 본문은 명시적 액션 후; 접근 감사(Screen 3)와 연계 |

---

### Screen 3 — Access audit (sensitive detail / full copy views)

| Item | Designer notes |
|------|----------------|
| **Working name** | Activity log — Access audit |
| **Route (placeholder)** | `activity-log-access-audit` |
| **Page header** | Title + description — e.g. records access to sensitive activity detail and full copy content (영문 UI 문장은 제품 카피에 맞게 조정) |
| **Filters (English UI)** | Date range · **Accessor** user (department / name / ID) · target activity **log id** (optional) |
| **Table columns (English UI)** | Accessor · Timestamp · Target `user_activity_log.id` · Access type (e.g. `DETAIL_VIEW`, `COPY_BODY_FULL`) |
| **Footer** | Pagination (grid-and-table 기준) |
| **Export** | Screen 1과 **동일한 승인 게이트**가 적용되면 동일 규칙으로 표시 |
| **States** | Default · Loading · Empty · Error |
| **Role note** | 감사자 화면으로 **accessor identity** 마스킹 없음이 기본; 더 좁은 역할 범위는 PO 확인 ([요건 §1.1 Screen 3](../requirements/20260330-audit-evidence-activity-log-conservative.md)) |

---

### Screen 4 — (Optional) Retention summary & export approval

| Item | Designer notes |
|------|----------------|
| **Working name** | Audit policy — Retention & export |
| **Route (placeholder)** | `audit-policy-activity-log` |
| **Purpose** | **PO 확정 시에만** 구현: 보존 클래스 / legal hold **정렬 문구** read-only · 제3자 감사 패키지 **반출 승인 대기** 목록 |
| **Regions** | Header · **Retention summary** (정적 텍스트) · **Approval queue** table (request id, requester, scope, status, approver) · request **detail drawer** |
| **Controls (English UI)** | **Approve** / **Reject** — approver role only |
| **States** | Default · Loading · Empty queue · Error |
| **Wireframe** | 스코프 미확정이면 **별도 옵션 와이어프레임**으로 분리하거나 “optional” 레이어로 표기 |

---

## 2. A11y / layout notes

### Screen 2 — Modal / drawer focus

- 오버레이 열릴 때 **초기 포커스**는 패널 내부 첫 포커스 가능 요소(보통 **Close** 또는 제목)로 이동하는 패턴을 전제로 와이어프레임에 “focus trap 영역”을 명시한다.
- 닫기 후 포커스는 **View detail**을 연 행 액션으로 **복귀**하는 것이 일반적이다.
- 키보드: **Escape**로 닫기(제품 정책과 일치 시).

### z-index (모달·드로어)

커스텀 다이얼로그·드로어는 **AppBar보다 위**에 있어야 한다. 수치 기준은 [layout-and-navigation.md](layout-and-navigation.md) § **z-index hierarchy** (예: modal overlay **1300**, AppBar **1201**, sidebar **1200**)를 따른다. 와이어프레임 주석에 “see layout-and-navigation.md z-index table”을 적어 두면 구현·디자인 검수가 맞춰진다.

### Narrow viewport — column priority

좁은 뷰포트에서 테이블 가로 스크롤이 불가피할 때 **우선 노출 순위**(와이어프레임 메모):

1. **Timestamp**  
2. **Action type**  
3. **Summary** (또는 식별 가능한 최소 요약)  
4. **User**  
5. **IP**  
6. **Actions** (**View detail**은 가능하면 항상 접근 가능 — 행 확장 또는 고정 열)

세부 컬럼·정렬 요구는 [grid-and-table.md](grid-and-table.md) § Sorting, § Column rules 참고.

---

## 3. PO questions (제품 오너 확인 사항)

요건·보안 메모·스크린 사양에서 디자인/스코프를 가르는 **확인 질문**이다. 답이 나오기 전까지 와이어프레임에는 `TODO` 또는 variant 분기로 남긴다.

1. **Screen 4 포함 여부**: 보존 요약·제3자 반출 **승인 큐** UI를 초기 릴리스에 넣을지, 운영/백오피스만으로 갈지 ([요건 Screen 4](../requirements/20260330-audit-evidence-activity-log-conservative.md), §7 최종 요약).

2. **Export 노출 시점**: **Export** 버튼(Screen 1·3)을 **승인 백엔드·워크플로가 준비된 뒤에만** 보이게 할지, 자리만 잡아 두고 숨길지.

3. **물리 삭제 예외**: 도메인별로 **삭제 스냅샷**을 생략할 수 있는 예외가 있는지(없으면 보수적 기본: 스냅샷 필수).

4. **보존·Legal hold 문구**: 설정 기반 read-only 숫자/문구 vs **“Contact DBA”** 등 정적 문구 vs 초기에는 비노출 중 무엇을 쓸지(Screen 4와 연동).

5. **개인정보 고지 정합성 (S-5)**: 실제 수집 범위와 공지 문구 일치는 **법무/컴플라이언스** 확정이 필요; UI에 넣을 법적 면책/링크 범위.

6. **접근 감사(Screen 3) 역할 범위**: accessor 목록을 **열거된 감사 역할만** 보도록 할지, Security 메모의 “마스킹 없음”을 전제로 한 **최소 역할 집합**을 PO가 확정할지.

7. **검색하기(main) vs 활동 이력(activity-log)의 날짜 필드**: `startDate` / `endDate`를 **이름·의미만 통일**할지, **화면별 정의 유지**할지 — [search-fields-by-screen.md](search-fields-by-screen.md) 상단 “동일 이름·다른 성격” 피드백 항목과 연결.

---

## 4. Reference index

| Document | Use for |
|----------|---------|
| [`docs/requirements/20260330-audit-evidence-activity-log-conservative.md` §1.1](../requirements/20260330-audit-evidence-activity-log-conservative.md) | 스크린 1–4 권위 있는 영역·컨트롤·상태·masked/privileged |
| [grid-and-table.md](grid-and-table.md) | 테이블·푸터·정렬·페이지 사이즈 |
| [layout-and-navigation.md](layout-and-navigation.md) | z-index, 셸, 포커스/키보드 맥락 |
| [search-fields-by-screen.md](search-fields-by-screen.md) | activity-log 필터 필드·블록 |
| [forms-and-filters.md](forms-and-filters.md) | 필터 그룹 제목 위치, compact variant, 단일 행 규칙 |

---

*End of designer handoff.*
