# DRYRUN: Would Frontend consider Single row for non-date and Form per mode without explicit §1 bullets?

**Purpose**: Test whether the Frontend handoff would lead an implementer to apply **§ Single row for non-date** and **§ Form per mode** from `docs/design/forms-and-filters.md` when those two rules are **not** explicitly stated in the requirement doc §1 Expected outcome.

**Date**: 2026-03-13

---

## 1. Scenario (가정)

- **Requirement doc (가상)**: Same as `20260313-activity-log-statistics-design-standards.md` **except**:
  - §1 Expected outcome has **no** bullet "Single row for non-date (날짜 제외 단일 행)".
  - §1 Expected outcome has **no** bullet "Form per mode (모드별 폼 — when date fields cannot be unified)".
  - §2 StatisticsFilters.js has **no** item (5) "When 일별/월별 date fields cannot be unified … use separate form per mode".
- **Question**: If the handoff is built from this reduced requirement per `HANDOFF-CHECKLIST.md`, would the Frontend implementer be directed to consider those two design rules?

---

## 2. Handoff build (checklist 기준)

Handoff builder includes (Frontend):

| Item | Source | Included in handoff? |
|------|--------|----------------------|
| §1 one-paragraph summary | Requirement §1 | Yes. Summary would still mention "row1 = dates, row2 = rest" (from Activity log layout) but **not** the rule names "single row for non-date" or "form per mode". |
| §2 Full Frontend subsection | Requirement §2 | Yes. Contains **Implementation note**: "Implementer must read and apply field-level and **layout values** from `docs/design/search-field-definition-items.md`, `docs/design/search-fields-by-screen.md`, **`docs/design/forms-and-filters.md`**, and `docs/design/UX-REDESIGN-activity-log-statistics-search.md` …" |
| Design doc implementation (search/filter) | HANDOFF-CHECKLIST verbatim | Yes. Text: "Before changing form/filter CSS or component layout, read **`docs/design/search-field-definition-items.md`** … and **`docs/design/search-fields-by-screen.md`** …" — **does not mention `forms-and-filters.md`**. |
| CSS standard and exceptions | HANDOFF-CHECKLIST | Yes. |
| Standard-first | HANDOFF-CHECKLIST | Yes. |

**Observation**: The **only** place that tells Frontend to read `forms-and-filters.md` is the **requirement §2 Implementation note** (because "§2 Full Frontend subsection" is included). The HANDOFF-CHECKLIST’s own "Design doc implementation" bullet names only `search-field-definition-items.md` and `search-fields-by-screen.md`, not `forms-and-filters.md`.

---

## 3. Would Frontend consider the two rules?

### 3.1 Path to the rules

1. Implementer receives full §2, including Implementation note: "read and apply … from … **forms-and-filters.md**".
2. If they follow that, they open `docs/design/forms-and-filters.md`.
3. In that file they find:
   - **§ Single row for non-date (날짜 제외 단일 행)** — narrative rule: date block in separate row, rest in single row.
   - **§ Form per mode (일별/월별 — 모드별 폼 로드)** — narrative rule: when date fields cannot be unified, use separate form per mode.

So **if** the implementer reads `forms-and-filters.md` in full (or at least layout-related sections), they **can** find and apply both rules. So **theoretically yes**.

### 3.2 Risks (실제로 적용될 가능성)

| Risk | Explanation |
|------|-------------|
| **"Values" vs structural rules** | Implementation note says "read and apply **field-level and layout values**". An implementer may interpret "values" as numeric (8–12px, 1400px, min/max width) and treat § Single row for non-date and § Form per mode as **descriptive context** rather than mandatory rules to apply. |
| **Checklist vs requirement §2** | HANDOFF-CHECKLIST "Design doc implementation" does **not** list `forms-and-filters.md`. If the handoff builder ever builds the "Design doc implementation" instruction **only** from the checklist (and omits or shortens §2 Implementation note), Frontend would **not** be told to read `forms-and-filters.md` and would miss both rules. |
| **No §1 checklist for layout rules** | §1 would still have "Activity log layout: Row 1 = … Row 2 = …" (outcome), but no explicit "Single row for non-date" or "Form per mode". QA and implementers often use §1 as the **contract**; if a rule is not in §1, it may not be verified or prioritized. |

### 3.3 search-fields-by-screen.md 참조

`docs/design/search-fields-by-screen.md` (which **is** in the checklist’s Design doc implementation) contains:

- **§ 통계 (statistics)** 쪽: "**폼/필터 공통 규칙**: `docs/design/forms-and-filters.md` — 행 구성은 § Single row for non-date(날짜 제외 단일 행), 일별/월별 등 날짜 필드 분리 시 § Form per mode(모드별 폼 로드) 참조."

So if the implementer reads `search-fields-by-screen.md` for statistics (and activity-log) and follows that **cross-reference**, they would be directed to `forms-and-filters.md` § Single row for non-date and § Form per mode. That is a **second path** to the two rules, but it depends on them reading the **공통 규칙** paragraph in search-fields-by-screen.md, not only the field tables.

---

## 4. Dry-run verdict

| Question | Result |
|----------|--------|
| Would Frontend **receive** a pointer to `forms-and-filters.md`? | **Yes**, via requirement §2 Implementation note (included in "§2 Full Frontend subsection"). |
| Would Frontend **receive** a pointer from HANDOFF-CHECKLIST Design doc implementation? | **No**. The checklist only names search-field-definition-items.md and search-fields-by-screen.md. |
| Could Frontend **find** Single row for non-date and Form per mode? | **Yes**, if they (1) open forms-and-filters.md per §2 Implementation note, or (2) follow the "폼/필터 공통 규칙" cross-reference in search-fields-by-screen.md. |
| Would Frontend **reliably apply** them without §1 bullets? | **Uncertain**. Depends on (1) reading full forms-and-filters.md (or layout sections), (2) treating narrative rules as mandatory, (3) not limiting "layout values" to numeric values only. Without explicit §1 Expected outcome bullets, application is **not guaranteed**; it is **possible but not assured**. |

**Conclusion**: 요구사항 문서에 해당 문구가 없어도, **§2 Implementation note** 때문에 프론트엔드는 `forms-and-filters.md`를 읽으라는 지시는 받는다. 그래서 **이론상** 해당 부분(단일 행, 모드별 폼)을 고려할 **경로**는 있다. 다만 (1) Implementation note가 "layout **values**"를 강조해 구조 규칙이 묻힐 수 있고, (2) HANDOFF-CHECKLIST의 Design doc implementation에는 `forms-and-filters.md`가 없어, 체크리스트만 따르면 해당 문서를 읽으라는 문구가 빠질 수 있다. 따라서 **요구사항 §1에 두 규칙을 명시해 두는 것이** 적용 가능성을 보장하는 데 유리하다.

---

## 5. Recommendations

1. **Requirement doc**: §1에 "Single row for non-date"와 "Form per mode"를 Expected outcome으로 유지 (현재 반영된 상태 유지).
2. **HANDOFF-CHECKLIST**: "Design doc implementation (search/filter)" 항목에 `docs/design/forms-and-filters.md`를 추가하여, 검색/필터 UI 시 **항상** forms-and-filters.md를 읽고 § Single row for non-date, § Form per mode 등을 적용하도록 명시.
3. **Optional**: REQUIREMENTS-CHANGE-TARGET-CHECKLIST §2.4에서 "Implementation note for Frontend"에 "apply layout and structural rules from forms-and-filters.md (e.g. § Single row for non-date, § Form per mode)"를 한 줄 추가.

---

**Author**: Main agent (dry-run)  
**Date**: 2026-03-13
