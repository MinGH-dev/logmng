# Handoff dry run: "검색UI를 모두 같은 컨셉트로 통일해줘"

**목적**: 사용자 요청 **"검색UI를 모두 같은 컨셉트로 통일해줘"** 가 주어졌을 때, 현재 워크플로우·위임 규칙에 따라 **어떤 순서로 어떤 에이전트에게 어떻게 핸드오프**되는지 시뮬레이션. 코드 변경 없음, 프롬프트·흐름 검증만.

**참조**: `.cursor/commands/dry-run-handoff.md`, `docs/workflow/SUBAGENT-DELEGATION.md`, `docs/workflow/HANDOFF-CHECKLIST.md`, `docs/workflow/AGENT-COLLABORATION-ON-REQUIREMENT.md`, `docs/analysis-search-consistency-by-screen.md`.

---

## 1. 요청 해석 및 적용 스텝

| 사용자 요청 | 해석 | 적용 Step |
|-------------|------|-----------|
| "검색UI를 모두 같은 컨셉트로 통일해줘" | 사용자 맥락 화면(활동 이력·통계·사용자관리·권한그룹·검색이력·승인대기)에서 검색/필터 축을 **부서·이름·사용자ID**로 통일; scope=self 시 필터 블록 숨김; main(로그 검색)은 날짜·로그타입·타입별 필드만 유지. 기존 분석: `docs/analysis-search-consistency-by-screen.md`. | Step 1 → (2 없음) → Step 3d UX → Step 4 Frontend → Step 5 QA |

- **Step 1 (Requirements)**: 필수. 요청이 기능/요건이므로 요구사항 문서(§1·§2·§3) 선행.
- **Step 2 (Security)**: 해당 없음 (검색 UI 통일만, PII/복호화/접근제어 변경 없음).
- **Step 3 (Contract)**: 목록 API에 필터 파라미터 추가 시에만. 이 dry-run은 “UI·필터 통일” 전제로, API는 기존 분석대로 이미 있거나 최소 확장만 가정 → 선택.
- **Step 3d (UX)**: 검색/필터 UI 통일은 **폼·필터·컴포넌트 일관성**에 해당 → **UX** 호출. UX는 단일 도메인(폼/필터)이면 **UX-Components** 위임 가능.
- **Step 4 (Frontend)**: 구현은 **Frontend**만. 다중 화면(activity-log, statistics, user-management, permission-group, search-history, pending-approvals)이므로 Frontend 팀 리드가 직접 하거나 **Frontend-ActivityLog**, **Frontend-Log** 등에 일부 위임 가능.
- **Step 5 (QA)**: 빌드/재시작 후 검증.
- **Step 6 (Documentation/Release)**: 선택.

---

## 2. 핸드오프 체인 시뮬레이션

### 2.1 Step 1: Main → Requirements

**Main 판단**: 사용자가 기능 요청을 했고, "code only here" 등 예외 없음 → **요구사항 문서 선행** 필요. Main은 §1·§2·§3를 **작성하지 않고** Requirements를 Task로 호출.

**Task 호출 (Main이 보낼 것으로 기대되는 형태)**:

```text
subagent_type: Requirements
description: Author requirement doc for search UI consistency
prompt: |
  User request: "검색UI를 모두 같은 컨셉트로 통일해줘"

  Author the requirement document per docs/workflow/REQUIREMENTS-AUTHORING-WORKFLOW.md:

  1. Domain baseline: Read .cursor/skills/search-consistency-domain/SKILL.md and use
     docs/analysis-search-consistency-by-screen.md (§2 unified axes, §2.4 scope=self, §3 per-screen).
     For UI/design consistency also consider ui-ux-domain and docs/design/forms-and-filters.md.

  2. Optional — preserve prior scope: Invoke RequirementsPastSearch (readonly) with topic
     "search consistency" or "검색 통일" / paths under docs/requirements/ to summarize recent
     user-requested content from past docs (e.g. 20260310-search-consistency-*) so §1·§2
     preserve continuity unless the user explicitly asked to change it.

  3. Codebase investigation: Identify all screens that have search/filter UI — activity-log
     (UserActivityLogSearchForm, UserActivityLogList), statistics (StatisticsFilters),
     user-management, permission-group-management, search-history, pending-approvals.
     Check backend list APIs for existing filter params (userId, department, username, etc.)
     per docs/analysis-search-consistency-by-screen.md §3.

  4. Orchestrate §1 (user scenario: "모든 검색 UI를 같은 컨셉으로 통일"), §2 (design:
     user-context screens → 부서·이름·사용자ID 공통; scope=self → hide user/department filter
     block; main → date + log type + type-specific only; change file list by scope Frontend
     [and Backend if API params added]), §3 test cases with Scope tags (Frontend, Integration,
     scope=self hiding, per-screen axes).

  5. Run change target verification per REQUIREMENTS-CHANGE-TARGET-CHECKLIST.md so no
     touchpoint (e.g. activity-log + statistics + search-history + …) is missed.

  Output: docs/requirements/yyyyMMdd-search-ui-consistency-unify.md (or similar). Do not
  author §1·§2 from judgment alone; use skills + codebase + selective experts (e.g. UX if
  you need form/filter design constraints).
```

**체크**:
- [x] Main이 §1·§2·§3를 직접 작성하지 않음 (agent-collaboration.mdc Step 1 gate).
- [x] REQUIREMENTS-AUTHORING-WORKFLOW.md (skills-first → codebase → selective experts) 준수 지시.
- [x] 검색 통일 도메인에 **search-consistency-domain** + **docs/analysis-search-consistency-by-screen.md** 명시.
- [x] (선택) RequirementsPastSearch 호출로 기존 “검색 통일” 관련 요건 보존 가능하도록 안내.

---

### 2.2 Step 1 support (선택): Requirements → RequirementsPastSearch

Requirements가 기존 “검색 통일” 요건을 유지하려면 **RequirementsPastSearch**를 Task로 호출할 수 있음.

**Task 호출 (Requirements가 보낼 수 있는 형태)**:

```text
subagent_type: RequirementsPastSearch
description: Summarize past search-consistency requirement content
prompt: |
  Topic: search consistency, 검색 통일, activity-log statistics search filter.
  Paths or topics: docs/requirements/20260310-search-consistency-*.md, docs/requirements/TOPIC-INDEX.md
  (activity-log | statistics | sidebar | search).

  Summarize recent user-requested content from those requirement docs so the new requirement
  (search UI unify) can preserve that scope unless the user explicitly asked to change it.
  Output: short summary for §1·§2 continuity.
readonly: true
```

---

### 2.3 Step 3d: Main → UX (팀 리드)

요구사항 문서가 완성된 뒤, **UI/디자인** 관련이므로 Main은 Step 3d에서 **UX**만 호출. UX는 “폼·필터·컴포넌트 일관성”만 다루면 되므로 **UX-Components**에 위임할 수 있음.

**Task 호출 (Main → UX)**:

```text
subagent_type: UX
description: UX review for search UI consistency
prompt: |
  Requirement doc: docs/requirements/yyyyMMdd-search-ui-consistency-unify.md

  §1 summary: 사용자 요청 "검색UI를 모두 같은 컨셉트로 통일해줘". 사용자 맥락 화면(활동 이력,
  통계, 사용자관리, 권한그룹, 검색이력, 승인대기)에서 검색/필터를 부서·이름·사용자ID로 통일하고,
  scope=self일 때 해당 필터 블록 숨김. main(로그 검색)은 날짜·로그타입·타입별 필드만.

  §2 (relevant to UX): Frontend design — forms/filters consistency; reference
  docs/analysis-search-consistency-by-screen.md and docs/design/forms-and-filters.md.

  Please provide § UX review or design recommendations (form layout, filter group order,
  labels, scope=self hiding of filter block). No code. Frontend will implement.
  If this is forms/filters/component consistency only, you may delegate to UX-Components
  per docs/workflow/UX-ROLE-SEPARATION-DESIGN.md §4.5.
```

**체크**:
- [x] Main은 **UX**만 호출 (UX-A11y/UX-Layout/UX-Components는 UX가 내부 위임).
- [x] 요건 문서 §1·§2 요약과 UI 설명 포함.
- [x] UX-ROLE-SEPARATION-DESIGN.md §4.5 위임 가능성 언급.

---

### 2.4 Step 4: Main → Frontend

UX 검토가 나온 뒤, 구현은 **Frontend**만. Main은 **Frontend**만 호출. Frontend는 여러 화면을 건드리므로 직접 구현하거나 Frontend-ActivityLog, Frontend-Log 등에 일부 위임 가능.

**Task 호출 (Main → Frontend)** — HANDOFF-CHECKLIST Frontend 항목 반영:

```text
subagent_type: Frontend
description: Implement search UI consistency (all user-context screens)
prompt: |
  Implement the Frontend scope of: docs/requirements/yyyyMMdd-search-ui-consistency-unify.md

  §1 Summary:
  Unify all search/filter UI to the same concept: user-context screens (activity-log,
  statistics, user-management, permission-group-management, search-history, pending-approvals)
  use unified axes 부서·이름·사용자ID; scope=self일 때 부서·사용자·이름 필터 블록 전체 비표시.
  main(로그 검색) keeps date + log type + type-specific fields only.

  §2 Frontend subsection (excerpt):
  - Apply docs/analysis-search-consistency-by-screen.md (and search-consistency-domain skill).
  - Per-screen: add or align department/name/userId filters; scope=self → hideUserFilters
    true, do not send user/department params to API.
  - Change file list (tentative): UserActivityLogSearchForm, StatisticsFilters,
    UserManagement (search form if any), PermissionGroupManagement (user picker filter),
    SearchHistoryList (filter form), PendingApprovals (filter form); shared filter component
    or hooks if introduced.

  §2.1 Security: N/A for this requirement (no new access control; scope=self behavior already
  defined in permission group).

  Contract/spec: Use existing list APIs; if any new query params (e.g. departmentCode,
  username) are added, align with backend contract and docs/api-definition.md.

  §3 Test cases that involve Frontend (with Scope tag):
  - [Frontend] Activity-log: department/name/userId filters visible when scope≠self;
    hidden when scope=self; API not sent user params when scope=self.
  - [Frontend] Statistics: same. [Frontend] Search-history, pending-approvals: requester
    (userId)·부서·이름 filter if API supports. [Frontend] User-management, permission-group:
    search form with 부서·이름·사용자ID per analysis doc. [Integration] scope=self: backend
    returns only current user data when frontend does not send user/department params.

  Search/filter (user-context screens): MUST apply docs/analysis-search-consistency-by-screen.md
  (or search-consistency-domain skill): unified axes 부서·이름·사용자ID; scope=self일 때
  해당 필터 블록 전체 비표시, API에 해당 파라미터 미전송.

  UX role: Attach § UX review from Step 3d if provided (form/filter layout, labels).
  Implement per docs/workflow/UX-ROLE-SEPARATION-DESIGN.md (definition vs implementation).

  Cross-scope: Backend already enforces scope=self (ignores user/department when scope=self);
  frontend must not send those params when hideUserFilters is true.

  After implementation: run build and restart (frontend), then hand off to QA with this
  requirement doc path for §5 update.
```

**HANDOFF-CHECKLIST.md Frontend — 항목 점검**:

| 항목 | 포함 여부 | Pass? |
|------|-----------|-------|
| §1 summary | 예 (한 문단: 통일 축, scope=self 숨김, main 유지) | ✓ |
| §2 Frontend | 예 (분석 문서 적용, per-screen, 변경 파일 목록) | ✓ |
| §2.1 | 예 (N/A 명시) | ✓ |
| Contract/spec | 예 (기존 API, 신규 파라미터 시 계약 정합) | ✓ |
| §3 Frontend TC | 예 (Scope 태그, 활동이력·통계·검색이력·승인대기·사용자관리·권한그룹, scope=self) | ✓ |
| Cross-scope | 예 (Backend scope=self 동작, frontend 미전송) | ✓ |
| **Search/filter (user-context)** | 예 (분석 문서·스킬, 통일 축, scope=self 필터 블록 비표시·미전송) | ✓ |
| **UX role** | 예 (Step 3d § UX 검토 첨부, UX-ROLE-SEPARATION-DESIGN 참조) | ✓ |

---

### 2.5 Step 5: Main 또는 Frontend → QA

Frontend가 빌드·재시작을 완료한 뒤, **QA**를 Task로 호출.

**Task 호출 (Main 또는 Frontend → QA)**:

```text
subagent_type: QA
description: Verify search UI consistency implementation
prompt: |
  Requirement doc: docs/requirements/yyyyMMdd-search-ui-consistency-unify.md

  §1 Summary: 검색 UI 통일 — user-context 화면에서 부서·이름·사용자ID 통일, scope=self 시
  필터 블록 숨김; main은 날짜·로그타입·타입별만.

  §3 Full test case list: [paste or reference all §3 TCs from the requirement doc, including
  Frontend and Integration TCs for each screen and scope=self.]

  Build and restart: Frontend build and restart completed (or run them per SUBAGENT-DELEGATION
  §2.1 if not confirmed).

  Please perform verification per .cursor/commands/verify.md: run verify checklist, health
  check, and for frontend the browser automation step (3.5) when MCP available. Update
  requirement doc §5 with test results. On failure, create bugfix child and set failure scope
  (frontend | backend | ux | …) and hand off to Requirements per docs/workflow/UX-ROLE-SEPARATION-DESIGN.md §5.
```

**HANDOFF-CHECKLIST.md QA — 항목 점검**:

| 항목 | 포함 여부 | Pass? |
|------|-----------|-------|
| §1 summary + §3 | 예 (§1 요약, §3 전체 TC 목록) | ✓ |
| Build/restart 확인 | 예 (완료 또는 QA가 실행) | ✓ |
| 요건 문서 경로 | 예 (§5/§6 갱신용) | ✓ |
| Frontend 시 브라우저 자동화 | 예 (verify 3.5, MCP 시) | ✓ |
| Failure scope ux | 예 (실패 시 scope ux, Requirements → UX 역할 §5 참조) | ✓ |

---

## 3. 전체 흐름 요약 (순서도)

```text
User: "검색UI를 모두 같은 컨셉트로 통일해줘"
    │
    ▼
[Main] 요청 인식 → Step 1 필요 (요건 문서 선행)
    │
    ├─ Task(Requirements) ──────────────────────────────────────────► [Requirements]
    │       prompt: user request + REQUIREMENTS-AUTHORING-WORKFLOW     │
    │               + search-consistency-domain + analysis doc         │
    │               + (optional) RequirementsPastSearch                 │
    │                                                                   ├─ (optional) Task(RequirementsPastSearch)
    │                                                                   └─ Output: docs/requirements/...search-ui-consistency-unify.md
    │
    ▼ (요건 문서 완료)
[Main] Step 3d UI/design → Task(UX)
    │
    ├─ Task(UX) ───────────────────────────────────────────────────► [UX]
    │       prompt: requirement doc §1·§2 + UI description             │
    │                                                                   ├─ (optional) Task(UX-Components) for forms/filters only
    │                                                                   └─ Output: § UX review
    │
    ▼ (UX 검토 반영)
[Main] Step 4 구현 → Task(Frontend)
    │
    ├─ Task(Frontend) ───────────────────────────────────────────────► [Frontend]
    │       prompt: §1, §2 Frontend, §3 TCs, Contract, Search/filter   │
    │               (analysis doc + scope=self), UX review ref          │
    │                                                                   ├─ (optional) Task(Frontend-ActivityLog) / Task(Frontend-Log)
    │                                                                   ├─ build + restart
    │                                                                   └─ hand off to QA
    │
    ▼
[Main or Frontend] Step 5 → Task(QA)
    │
    ├─ Task(QA) ────────────────────────────────────────────────────► [QA]
    │       prompt: §1, §3, build/restart confirmation, doc path        │
    │                                                                   ├─ verify checklist + browser automation
    │                                                                   ├─ §5 update; on fail → bugfix child, failure scope, Requirements
    │                                                                   └─ on pass → commit (per commit-on-complete)
    │
    ▼
Done (optional Step 6 Documentation/Release)
```

---

## 4. 규칙·문서 검증 표

| Rule / Document | Check | Pass? |
|------------------|-------|-------|
| agent-collaboration.mdc Step 1 gate | Main이 §1·§2·§3 직접 작성하지 않음 | ✓ |
| agent-collaboration.mdc §3 gate | Step 4 전에 §3 존재 (요건 문서 완성 후 Frontend 호출) | ✓ |
| REQUIREMENTS-AUTHORING-WORKFLOW.md | Skills-first → codebase → selective experts (search-consistency, 분석 문서) | ✓ |
| HANDOFF-CHECKLIST.md Frontend | §1, §2, §2.1, Contract, §3, Cross-scope, **Search/filter**, **UX role** | ✓ |
| HANDOFF-CHECKLIST.md QA | §1+§3, build/restart, doc path, failure scope ux | ✓ |
| SUBAGENT-DELEGATION.md Step 3d | Main → UX only; UX may delegate to UX-Components | ✓ |
| SUBAGENT-DELEGATION.md Step 4 | Main → Frontend only; Frontend may delegate to Frontend-ActivityLog/Log | ✓ |
| UX-ROLE-SEPARATION-DESIGN.md §4.5 | UX 팀 리드, UX-Components 위임 조건 (forms/filters) | ✓ |
| docs/analysis-search-consistency-by-screen.md | 사용자 3축, scope=self 숨김, 화면별 권장 검색 항목 | ✓ |

---

## 5. Dry-run 결론

- **"검색UI를 모두 같은 컨셉트로 통일해줘"** 요청 시, 위와 같은 **Requirements → (RequirementsPastSearch) → UX (→ UX-Components) → Frontend (→ 모듈 위임) → QA** 순서로 핸드오프가 이루어지면, 현재 규칙·체크리스트·분석 문서와 일치함.
- **Requirements** 핸드오프 시 **search-consistency-domain** 스킬과 **docs/analysis-search-consistency-by-screen.md** 를 반드시 참조하도록 prompt에 넣어야 구현 범위와 통일 축이 누락되지 않음.
- **Frontend** 핸드오프 시 **Search/filter (user-context screens)** 항목과 **UX role** 항목을 포함해야 분석 문서·스킬·UX 검토가 구현에 반영됨.
- **UX**는 “폼·필터·컴포넌트 일관성”만 다루면 되므로 **UX-Components** 위임이 가능하며, 팀 리드인 UX만 Main이 호출함.
