# Dry-run: 검색 통일·scope=self 변경 사항 검증

**목적**: 분석 문서·스킬·HANDOFF-CHECKLIST 변경이 의도대로 동작하는지 dry-run으로 확인. 코드 변경 없음, 프롬프트·규칙 검증만 수행.

**가상 요건**: "활동 이력 화면에 부서 검색 필드 추가. scope=self(본인만)일 때는 부서·사용자 검색 필터를 비표시로 유지."

---

## 1. 시나리오: Requirements → Frontend → QA

(검색 통일·Frontend 핸드오프 신규 항목을 쓰는 경로)

### 1.1 Requirements 핸드오프 (main → Requirements)

**Task 호출 예시**:
```
subagent_type: Requirements
description: Author requirement doc for activity-log department filter
prompt: |
  User request: 활동 이력 화면에 부서 검색 필드 추가. scope=self(본인만)일 때는 부서·사용자 검색 필터를 비표시로 유지.

  Author the requirement doc per docs/workflow/REQUIREMENTS-AUTHORING-WORKFLOW.md:
  1. Domain baseline: read .cursor/skills/ relevant to this domain — for "search/filter on activity-log" you must read search-consistency-domain and use docs/analysis-search-consistency-by-screen.md (§2, §2.4, §3).
  2. Codebase investigation: UserActivityLogSearchForm, UserActivityLogList, hideUserFilters, backend UserActivityLogController/UserActivityLogSearchRequest.
  3. Orchestrate §1 (user scenario), §2 (design: add department field; keep hideUserFilters so scope=self hides entire user/department block; backend already ignores department when scope=self per §2.4), §2 change file list, §3 test cases (Frontend, Backend, Integration with scope=self check).
  4. Run change target verification per REQUIREMENTS-CHANGE-TARGET-CHECKLIST.md.
  Output: docs/requirements/yyyyMMdd-activity-log-department-filter.md
```

**검증**:
- [x] Main이 §1·§2·§3를 직접 작성하지 않음 (Step 1 gate).
- [x] Prompt에 REQUIREMENTS-AUTHORING-WORKFLOW.md (skills-first → codebase → experts) 준수 지시.
- [x] **search-consistency-domain** 스킬 및 **docs/analysis-search-consistency-by-screen.md** 참조가 요건 도메인(activity-log 검색)과 일치하므로 Requirements가 해당 스킬/문서를 읽도록 명시 가능.

### 1.2 Frontend 핸드오프 (main → Frontend)

**Task 프롬프트에 포함되어야 할 HANDOFF-CHECKLIST 항목** (Frontend 전부):

| 항목 | 포함 예시 | Pass? |
|------|-----------|-------|
| §1 요약 | "활동 이력에 부서 필터 추가. scope=self 시 기존처럼 사용자/부서 필터 비표시 유지." | ✓ |
| §2 Frontend | UserActivityLogSearchForm에 department 필드·departmentList prop; List에서 부서 목록 로드; hideUserFilters 시 department 제거 후 API 전송 | ✓ |
| §2.1 | (해당 시만) 이 요건은 접근 제어만 (scope=self 기존 동작 유지) | ✓ |
| Contract/spec | POST /api/activity-log/search request body에 department 선택 파라미터 (백엔드 스펙) | ✓ |
| §3 Frontend TC | 활동 이력 검색 폼에 부서 드롭다운 표시(scope≠self 시); scope=self 시 부서·사용자ID·사용자명 필터 미노출; 검색 시 파라미터 전달 검증 | ✓ |
| Cross-scope | Backend가 department 수신 시 app_user 조인으로 필터; scope=self 시 controller에서 department null 처리 | ✓ |
| **Search/filter (user-context)** | **"Apply docs/analysis-search-consistency-by-screen.md (or search-consistency-domain skill): unified axes 부서·이름·사용자ID; scope=self일 때 해당 필터 블록 전체 비표시, API에 해당 파라미터 미전송."** | ✓ |

**의도 동작 확인**:  
Frontend 핸드오프 체크리스트에 추가한 **Search/filter (user-context screens)** 항목이 “activity-log 검색/필터” 요건에서 **적용 대상**이 되며, 메인 에이전트가 프롬프트에 **분석 문서 또는 search-consistency-domain 스킬** 참조를 넣으면 구현 에이전트가 통일 축과 scope=self 규칙을 적용할 수 있음.

### 1.3 QA 핸드오프 (main → QA)

- [x] §1 요약 + §3 전체 TC 목록
- [x] Build/restart 완료 확인
- [x] 요건 문서 경로(§5/§6 갱신용)
- [x] (Frontend 포함 시) 브라우저 자동화 기대 있으면 BROWSER-AUTOMATION-VERIFICATION-POLICY.md 참조

---

## 2. 참조 무결성 검증

| 참조 | 위치 | 존재 여부 | 비고 |
|------|------|-----------|------|
| docs/analysis-search-consistency-by-screen.md | 스킬·체크리스트·분석 본문 | ✓ | §2, §2.4, §3, §4 존재 |
| §2.4 Scope=self | 분석 문서 | ✓ | "사용자·부서 검색 비적용" 규칙 명시 |
| .cursor/skills/search-consistency-domain/SKILL.md | 신규 스킬 | ✓ | description에 activity-log, statistics, search, filter 포함 → 검색 시 발견 가능 |
| ui-ux-domain → 분석 문서 | Document references | ✓ | 검색/필터 통일 행 추가됨 |
| activity-statistics-domain → 분석 문서 | Document references | ✓ | 검색/필터 통일 행 추가됨 |
| HANDOFF-CHECKLIST Frontend | Search/filter bullet | ✓ | activity-log, statistics, … 나열 및 분석 문서/스킬 참조 |

---

## 3. 규칙·문서 검증 표

| Rule / Document | Check | Pass? |
|------------------|-------|-------|
| agent-collaboration.mdc Step 1 | Main does not author §1·§2·§3 | ✓ |
| agent-collaboration.mdc §3 gate | §3 exists before Step 4 | ✓ (가상 요건에서 요건 문서 완성 후 Frontend 위임 가정) |
| REQUIREMENTS-AUTHORING-WORKFLOW.md | Skills-first → codebase → selective experts | ✓ (prompt에 search-consistency-domain + 분석 문서 명시) |
| HANDOFF-CHECKLIST.md Frontend | §1, §2 Frontend, §2.1, Contract, §3, Cross-scope **+ Search/filter (user-context)** | ✓ |
| HANDOFF-CHECKLIST.md QA | §1, §3, build/restart, doc path | ✓ |
| 검색 통일 의도 | Frontend가 “검색/필터” 요건 수신 시 분석 문서 또는 스킬 참조로 통일 축·scope=self 적용 | ✓ |

---

## 4. Dry-run 결론

- **변경 사항이 의도대로 동작하는 경로**가 dry-run으로 확인됨:
  1. **Requirements**: “activity-log 검색/필터” 요건 시 **search-consistency-domain** 스킬 및 **docs/analysis-search-consistency-by-screen.md**를 읽도록 프롬프트에 넣을 수 있음.
  2. **Frontend**: HANDOFF-CHECKLIST의 **Search/filter (user-context screens)** 항목에 따라, 활동 이력·통계 등 사용자 맥락 검색/필터 요건 시 **분석 문서 또는 스킬** 참조를 핸드오프에 포함할 수 있음.
  3. **분석 문서 §2.4**와 **스킬 Quick reference**에 scope=self 시 부서·사용자 검색 비표시 규칙이 명시되어 있어, 구현 시 적용 가능.

- **권장**: 실제 요건으로 Requirements → Frontend 핸드오프 시, 메인 에이전트가 Frontend 체크리스트를 **전부** 확인할 때 위 **Search/filter** 항목을 누락하지 않도록 규칙/프롬프트에 “검색·필터 UI 변경 시 해당 항목 포함”을 명시해 두면 더 안정적임.
