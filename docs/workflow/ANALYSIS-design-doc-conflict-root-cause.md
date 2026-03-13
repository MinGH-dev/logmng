# Analysis: Root cause of conflicting content across design docs (도구 상 중첩·상충 현상)

**Context**: The same fact (e.g. statistics screen IP field: label and controlType) was stated differently in `docs/design/search-fields-by-screen.md` (§3: IP, select) and `docs/design/search-field-definition-items.md` (§5: IP = text like activity log). Implementers followed one doc and the result diverged from the intended reference (activity log). This document analyzes **why** conflicting opinions end up in multiple "tools" (design docs) and suggests mitigations.

---

## 1. Root causes

### 1.1 Dual definition structure (이중 정의 구조)

- **Declared single source**: `search-field-definition-items.md` §5 states that the per-screen field **tables** live in `search-fields-by-screen.md` and that document is the **단일 소스** (single source).
- **But**: The **same** definition-items doc has a **narrative** section (§5) that defines behavior for statistics: "통계 필드 정의: ... IP는 활동 이력과 동일하게 **text** 직접 입력."
- So the **same fact** (statistics IP: controlType and label) exists in **two places**:
  - Table in search-fields-by-screen §3: `ip | IP | select`
  - Narrative in search-field-definition-items §5: IP = **text**, activity-log과 동일
- There is **no** rule that "§5 narrative must be derived from the table" or "the table must be derived from §5." So the two can be updated independently and **drift**.

**Cause**: The "schema" doc (definition-items) is not only a schema; it contains **authoritative narrative** about specific screens. That creates a second source of truth for the same field.

---

### 1.2 Unclear ownership and updater (갱신 주체 불명확)

- **docs/design/README.md**: "**Owner**: UX subagent (design system owner)." So **UX** is the owner of design standards.
- **Practice**:
  - Requirement docs list `search-fields-by-screen.md` in the **change file list** (e.g. "review or update §3 statistics"). So **Requirements** (when authoring) or **Frontend** (when implementing) **edit** search-fields-by-screen.
  - `search-field-definition-items.md` is only referenced as "schema to follow"; it is **not** in the change file list for the statistics design requirement. So **who updates** definition-items §5 when statistics fields change? UX? Requirements? No one is assigned.
- **REVIEW-agent-role-common-rules.md** already states: "갱신 주체가 불명확하면 중복·누락 가능."

**Cause**: Ownership is "UX" at folder level, but the **updater** for search-fields-by-screen is Requirements/Frontend per requirement; the updater for definition-items §5 is **undefined**. So the two docs can be updated in different cycles by different agents.

---

### 1.3 No sync rule between the two docs (동기화 규칙 없음)

- There is **no** documented rule such as:
  - "When you add or change a field in search-fields-by-screen, update search-field-definition-items §5 if it contains narrative about that screen."
  - Or: "definition-items §5 is the canonical rule; search-fields-by-screen table must conform to it."
- So the two documents can be edited **independently**. One task adds statistics §3 to search-fields-by-screen (e.g. from current code: IP = select). Another task (or same agent earlier) adds narrative in definition-items §5 (IP = text for statistics to match activity log). No step in the workflow forces a **consistency check** or **single edit** for the same fact.

**Cause**: Workflow and checklist require **referencing** both docs but do not require **syncing** them when one is updated.

---

### 1.4 Bidirectional reference without priority (양방향 참조, 우선순위 없음)

- search-fields-by-screen: "Definition items schema: search-field-definition-items."
- search-field-definition-items: "화면별 필드 정의표는 search-fields-by-screen. 해당 문서가 단일 소스."
- When the **table** (search-fields-by-screen) and the **narrative** (definition-items §5) conflict, there is **no** rule that says "table wins" or "narrative wins." Implementers may follow whichever they read first or whichever is in the handoff, so outcomes differ.

**Cause**: Two documents point to each other as authority, but conflict resolution is not defined.

---

### 1.5 Multiple agents, different cycles (여러 에이전트, 서로 다른 시점)

- **Requirements**: May add "search-fields-by-screen.md — add/update statistics §3" to the change file list; may cite both docs in §1/§2.
- **Frontend**: Implements and may "review or update" search-fields-by-screen §3 (e.g. from current code or UX note).
- **UX**: Owns design system; could update definition-items when defining cross-screen rules.
- These edits happen in **different** requirement cycles or by different subagents. No single "design doc sync" step ensures both files are updated together for the same fact.

**Cause**: The same piece of information (e.g. "statistics IP") can be written by different agents in different tasks without a gate that keeps the two docs in sync.

---

## 2. Summary table

| Cause | Description |
|-------|-------------|
| **Dual definition** | Same fact (e.g. statistics IP) defined in both the per-screen **table** (search-fields-by-screen) and a **narrative** (definition-items §5); no rule that one derives from the other. |
| **Unclear updater** | search-fields-by-screen is updated by Requirements/Frontend per requirement; definition-items §5 has no designated updater; UX is owner but updater role is not specified per doc. |
| **No sync rule** | Workflow requires **referencing** both docs but does not require **syncing** them when one is updated. |
| **No conflict priority** | When table and narrative conflict, no rule says which wins. |
| **Multiple agents, different cycles** | Different agents edit the two docs in different tasks; no single step forces consistency. |

---

## 3. Recommended mitigations

1. **Single place for per-screen field facts**  
   - Option A: All per-screen field definitions (including statistics) live **only** in search-fields-by-screen. Remove or narrow definition-items §5 so it does **not** repeat screen-specific field rules (e.g. "statistics IP = text"); keep §5 only to "when adding a screen, apply definition-items schema and cross-field rules; per-screen content is in search-fields-by-screen only."  
   - Option B: definition-items §5 is the **canonical** rule for cross-screen consistency (e.g. "statistics IP = activity log = text"). Then search-fields-by-screen §3 **must** conform to it, and any change to §3 must be validated against §5. Document this in both files and in the workflow.

2. **Designate updater and sync rule**  
   - Assign a single **owner/updater** for "search filter field definitions" across both docs (e.g. Requirements when the requirement changes field design; or UX when design standard changes).  
   - Add a **sync rule**: "When you add or change a field or screen in search-fields-by-screen, update search-field-definition-items §5 if it contains narrative about that screen so the two stay consistent; if you change definition-items §5, update the corresponding row/section in search-fields-by-screen."

3. **Conflict priority**  
   - Document explicitly: e.g. "For per-screen field table content, search-fields-by-screen is authoritative; definition-items §5 narrative must not contradict it" (or the reverse). Then implementers and requirement authors know which doc to align to when they conflict.

4. **Checklist at requirement authoring**  
   - When the requirement touches search/filter field design (pattern §2.4), add a step: "If you change search-fields-by-screen or definition-items, ensure **both** are updated and **consistent** for the affected screen/field; list both in change file list if either is edited."

5. **Review verification**  
   - When Review checks implementation against the requirement, add a check: "If the requirement involved search/filter field design, confirm that search-fields-by-screen and search-field-definition-items do not contradict each other for the affected screen."

---

## 4. Improvement applied (표준정의로만 통일)

- **search-field-definition-items.md §5**: 화면별 필드 내용을 제거하고 **표준정의 단일 소스** 원칙만 명시. "화면별 필드 정의는 search-fields-by-screen.md만 사용; 이 문서는 스키마(§1)와 공통 규칙(§4)만 담고, 화면별 구체 값은 중복 기술하지 않음. 갱신 시 search-fields-by-screen만 수정."
- **search-fields-by-screen.md §3**: 통계 기타 조건을 활동 이력과 동일하게 정리. `ipAddress | IP 주소 | text` (라벨·controlType·placeholder 활동 이력 §2.1과 동일).
- **docs/design/README.md**: "표준정의 단일 소스" 단락 추가. 화면별 검색/필터 필드는 search-fields-by-screen만 사용, definition-items는 스키마·§4만.
- **동기화 규칙**: "화면별 필드를 바꿀 때는 search-fields-by-screen.md만 수정. definition-items §5에는 화면별 필드 내용을 추가·수정하지 않음."
- **구현**: `StatisticsFilters.js` — 기타 조건 IP를 select → text 입력으로 변경, 라벨 "IP 주소", placeholder "IP 주소" (정답지·표준정의와 일치).

---

## 5. References

- `docs/design/search-fields-by-screen.md` §3 (statistics), §5 (도구 참조)
- `docs/design/search-field-definition-items.md` §5 (표준정의 단일 소스)
- `docs/design/README.md` (Owner: UX, 표준정의 단일 소스)
- `docs/workflow/REQUIREMENTS-CHANGE-TARGET-CHECKLIST.md` §2.4 (Design doc references)
- `docs/workflow/REVIEW-agent-role-common-rules.md` (갱신 주체 불명확, 단일 소유)
- `docs/workflow/ANALYSIS-search-field-design-doc-reference-gaps.md`
- `docs/workflow/ANALYSIS-implementation-phase-design-doc-usage.md`
