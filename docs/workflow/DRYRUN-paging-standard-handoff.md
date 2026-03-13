# Dry-run: 페이징 표준 적용 시 도구·핸드오프 관계 검증

**목적**: "모든 화면에 페이징 처리 기능이 없음" 상태에서, 공통·표준에 정의된 페이징 내용이 있을 경우 **요구사항 → 구현 → QA** 도구 호출 관계가 HANDOFF-CHECKLIST대로 동작하는지 dry-run으로 검증. 코드 변경 없음.

**가상 요건**: "모든 데이터 테이블 화면에 그리드/테이블 설계 표준에 따른 페이징 적용: 기본 20건/페이지, 표시 건수 컨트롤(숫자 입력 + +/- 버튼, Enter 적용), 페이지네이션을 테이블 컨테이너 내부(.table-wrapper 직하)에 배치."

---

## 1. 공통·표준 내용 정리 (페이징)

### 1.1 설계 문서

| 문서 | 페이징 관련 내용 |
|------|------------------|
| **docs/design/grid-and-table.md** | • **Pagination**: `.table-wrapper` 직하, 테이블 컨테이너 **내부** 배치. `totalPages > 1`일 때만 표시. 공통 클래스 `.pagination`, first/prev/페이지번호/next/last.<br>• **Page size**: 기본 **20 rows/page**. "표시 건수" 컨트롤: 숫자 입력란 + **증감(+/−) 버튼** (1건 변경 시 **즉시 반영**), 직접 입력 후 **Enter** 시 반영. min/max 검증(예: 1~100).<br>• **Accessibility**: "Page 1 of 5" 등 레이블, 키보드 내비게이션. |
| **docs/workflow/CONSISTENCY-STANDARDS.md** §6 | • 단일 패턴: 리스트·표 형태 데이터는 동일한 **페이지네이션 동작** 사용.<br>• 클래스: `.pagination`, `.loading-container` 등.<br>• **페이지당 표시 건수**: 기본 **20건**. 페이지 크기 컨트롤 = 숫자 입력 + +/- 버튼; +/- 시 즉시 반영, 직접 입력 후 **엔터** 반영. `grid-and-table.md` § "Page size (rows per page)" 준수. |

### 1.2 API·계약

- **docs/api-definition.md**, **export/design/api-db-mapping.md**: 로그 검색·검색 이력·승인 대기·활동 로그 등 목록 API에 `page`, `pageSize` 및 응답 `pagination: { currentPage, totalPages, totalCount }` 정의됨.
- 즉, **백엔드 계약 상** 페이징 파라미터는 대부분 존재; **프론트 전역**에서 기본값 20·표시 건수 컨트롤·컨테이너 내 배치가 빠진 상태로 정리됨(20260226-ux-grid-review-report 기준).

### 1.3 결론

- **공통·표준 내용은 존재함**: `grid-and-table.md` + `CONSISTENCY-STANDARDS.md` §6에 페이징 배치, 기본 20건, 표시 건수 컨트롤(+/-, Enter)이 명시되어 있음.
- **도구 호출 관계 검증**: 아래는 이 표준을 "적용"하는 가상 요건으로 **Requirements → Backend → Frontend → QA** 핸드오프가 체크리스트대로 구성되는지 dry-run.

---

## 2. 시나리오: Requirements → Backend → Frontend → QA

### 2.1 Requirements 핸드오프 (main → Requirements)

**Task 호출 예시**:

```
subagent_type: Requirements
description: Author requirement doc for paging standard application
prompt: |
  User request: 모든 데이터 테이블 화면에 페이징 표준 적용. 기본 20건/페이지, 표시 건수 컨트롤(숫자+/-/Enter), 페이지네이션을 테이블 컨테이너 내부에 배치.

  Author the requirement doc per docs/workflow/REQUIREMENTS-AUTHORING-WORKFLOW.md:
  1. Domain baseline: read .cursor/skills/ relevant to grid/table/UX — ui-ux-domain, and use docs/design/grid-and-table.md (§ Pagination, § Page size), docs/workflow/CONSISTENCY-STANDARDS.md §6.
  2. Codebase investigation: DataTable.js/css, LogGrid, UserActivityLogList, SearchHistoryList, PendingApprovals; backend list APIs (page, pageSize, pagination response) in api-definition.md and controllers.
  3. Orchestrate §1 (user scenario), §2 (design: per-screen change list; Backend only if any API lacks page/pageSize/pagination; Frontend: default 20, rows-per-page control, pagination inside .log-table-container), §3 test cases (Frontend, Backend if changed, Integration).
  4. Run change target verification per REQUIREMENTS-CHANGE-TARGET-CHECKLIST.md.
  Output: docs/requirements/yyyyMMdd-paging-standard-all-screens.md
```

**검증**:

| Check | Pass? |
|-------|-------|
| Main이 §1·§2·§3를 직접 작성하지 않음 (Step 1 gate) | ✓ |
| Prompt에 REQUIREMENTS-AUTHORING-WORKFLOW.md 준수 지시 | ✓ |
| 설계 문서(grid-and-table.md, CONSISTENCY-STANDARDS §6) 참조로 요건 도메인(페이징)과 일치 | ✓ |

---

### 2.2 Backend 핸드오프 (main → Backend)

가상 요건에서 "API가 이미 page/pageSize/pagination을 지원하면 Backend 변경 없음"으로 가정. **변경이 있을 경우**에만 Backend 호출.

**Task 프롬프트에 포함되어야 할 HANDOFF-CHECKLIST 항목 (Backend)**:

| 항목 | 포함 예시 | Pass? |
|------|-----------|-------|
| §1 요약 | "데이터 테이블 화면 전반 페이징 표준 적용. API는 이미 page/pageSize 지원 시 변경 없음; 미지원 목록 API가 있으면 pagination 응답 추가." | ✓ |
| §2 Backend | 변경 대상 서비스/컨트롤러, page/pageSize 쿼리 및 응답 DTO에 currentPage/totalPages/totalCount 반환 | ✓ |
| §2.1 Security | (해당 시만) 목록 페이징은 일반적으로 PII/접근 제어 변경 없음 | ✓ |
| Contract/spec | docs/api-definition.md, docs/contract.md의 pagination 요청/응답 형식 | ✓ |
| §3 Backend TC | 목록 API 단위 테스트: page/pageSize 파라미터 및 pagination 필드 검증 | ✓ |
| Cross-scope | "Frontend는 page, pageSize 기본 20으로 요청; 응답 pagination으로 UI 갱신" | ✓ |
| CONSISTENCY-STANDARDS | 네이밍·에러 코드·파일 구조 접촉 시 참조 | ✓ |

**의도**: 페이징 요건이 API 확장을 수반할 때만 Backend 호출; 체크리스트 항목이 빠지지 않도록 §2에서 Backend subsection을 명시.

---

### 2.3 Frontend 핸드오프 (main → Frontend)

**Task 프롬프트에 포함되어야 할 HANDOFF-CHECKLIST 항목 (Frontend)**:

| 항목 | 포함 예시 | Pass? |
|------|-----------|-------|
| §1 요약 | "모든 데이터 테이블에 페이징 표준 적용: 기본 20건, 표시 건수 컨트롤(+/-, Enter), .pagination을 .table-wrapper 직하·컨테이너 내부에 배치." | ✓ |
| §2 Frontend | DataTable: pageSize 기본 20, rows-per-page UI(숫자+/-/Enter); 각 화면(LogGrid, UserActivityLogList, SearchHistoryList, PendingApprovals 등)에서 DataTable에 pagination prop 전달, 컨테이너 내부에만 배치 | ✓ |
| §2.1 | (해당 시) 접근 제어 변경 없음 | ✓ |
| Contract/spec | 목록 API 요청(page, pageSize), 응답(pagination.currentPage, totalPages, totalCount) | ✓ |
| §3 Frontend TC | 기본 20건 표시, +/-/Enter로 표시 건수 변경 시 즉시/반영, totalPages>1일 때만 페이지네이션 표시, 활동 로그 등에서 pagination이 테이블 컨테이너 내부에 있음 | ✓ |
| Cross-scope | Backend가 pagination 반환; Frontend는 해당 필드로 UI 갱신 | ✓ |
| **Design doc (grid/table)** | **"Apply docs/design/grid-and-table.md (§ Pagination, § Page size) and docs/workflow/CONSISTENCY-STANDARDS.md §6: default 20, rows-per-page control (+/-, Enter), .pagination inside .log-table-container under .table-wrapper."** | ✓ |

**의도**: 페이징은 **그리드/테이블 공통 UX**이므로, Frontend 핸드오프에 **grid-and-table.md + CONSISTENCY-STANDARDS §6** 참조가 명시되면 구현 에이전트가 표준대로 적용 가능. (현재 HANDOFF-CHECKLIST에는 "Search/filter" 전용 항목만 있고, "grid/table/pagination" 전용 bullet은 없으나, **§2 Frontend**에 위 설계 문서 참조를 넣으면 동일 효과.)

---

### 2.4 QA 핸드오프 (main → QA)

| 항목 | 포함 예시 | Pass? |
|------|-----------|-------|
| §1 요약 + §3 전체 TC 목록 | 페이징 표준 적용 요약 + §3의 Frontend/Backend/Integration TC 전부 | ✓ |
| Build/restart 완료 확인 | Backend/Frontend 빌드·재시작 완료 후 검증 | ✓ |
| 요건 문서 경로 | docs/requirements/yyyyMMdd-paging-standard-all-screens.md (§5/§6 갱신용) | ✓ |
| 브라우저 자동화 | 그리드 화면별 페이지 전환·표시 건수 변경 등 필요 시 BROWSER-AUTOMATION-VERIFICATION-POLICY.md 참조 | ✓ |

---

## 3. 규칙·문서 검증 표

| Rule / Document | Check | Pass? |
|-----------------|-------|-------|
| agent-collaboration.mdc Step 1 gate | Main does not author §1·§2·§3 | ✓ |
| agent-collaboration.mdc §3 gate | §3 존재 시에만 Step 4(Backend/Frontend) 호출 | ✓ |
| REQUIREMENTS-AUTHORING-WORKFLOW.md | Skills + codebase + selective experts, change target verification | ✓ |
| HANDOFF-CHECKLIST.md Backend | §1, §2 Backend, §2.1, Contract, §3, Cross-scope, CONSISTENCY-STANDARDS | ✓ |
| HANDOFF-CHECKLIST.md Frontend | §1, §2 Frontend, §2.1, Contract, §3, Cross-scope + **design doc(grid-and-table, CONSISTENCY §6)** | ✓ (§2에 설계 문서 참조로 포함 가능) |
| HANDOFF-CHECKLIST.md QA | §1, §3, build/restart, doc path | ✓ |
| REQUESTMENT_TEMPLATE.md §3 Scope tag | TCs have Scope column (Backend/Frontend/Integration) | ✓ |
| CONTEXT-QUALITY §4.1 | Scope-specific excerpts in handoff (not full doc) | ✓ |

---

## 4. Dry-run 결론

- **공통·표준 내용**: 페이징에 대한 공통·표준은 **존재함**. `docs/design/grid-and-table.md`와 `docs/workflow/CONSISTENCY-STANDARDS.md` §6에 배치, 기본 20건, 표시 건수 컨트롤(+/-, Enter)이 정의되어 있음.
- **도구 호출 관계**:
  1. **Requirements**: 페이징 표준 적용 요건 시 **ui-ux-domain** 스킬 및 **grid-and-table.md**, **CONSISTENCY-STANDARDS.md** §6 참조를 프롬프트에 넣으면, 요건 문서에 설계 기준이 반영됨.
  2. **Backend**: API에 이미 page/pageSize·pagination이 있으면 생략 가능; 없을 때만 호출하며, HANDOFF-CHECKLIST Backend 항목(§1, §2, Contract, §3, Cross-scope)을 채우면 됨.
  3. **Frontend**: §2 Frontend에 **grid-and-table.md** 및 **CONSISTENCY-STANDARDS.md** §6 참조를 명시하면, 구현 시 페이징 배치·기본값·컨트롤 동작을 표준대로 적용 가능. (선택: HANDOFF-CHECKLIST에 "When the requirement involves grid/table or pagination, include grid-and-table.md and CONSISTENCY-STANDARDS.md §6" 항목을 추가하면 검색/필터와 대칭적으로 명확해짐.)
  4. **QA**: §1·§3·빌드/재시작·문서 경로가 포함되면 검증·§5/§6 갱신이 가능함.

- **권장**: 실제 "모든 화면 페이징 표준 적용" 요건 진행 시, 메인 에이전트가 Frontend 핸드오프 작성 시 **§2에 grid-and-table.md + CONSISTENCY-STANDARDS §6** 참조를 반드시 포함하고, 필요 시 HANDOFF-CHECKLIST Frontend에 "grid/table/pagination" 설계 문서 참조 항목을 추가하면 도구 호출 관계가 더 안정적으로 유지됨.
