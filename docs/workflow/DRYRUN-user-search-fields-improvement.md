# Dry-run: "사용자 관련 검색필드를 규칙에 맞춰서 개선해줘" 검증

**사용자 요청**: "사용자 관련 검색필드를 규칙에 맞춰서 개선해줘"

**목적**: 이 요청이 들어왔을 때 **관련 화면이 어떻게 도출되는지**, **설계 단계에서 어떻게 진행할지**를 dry-run으로 보여줌. 코드 변경 없음.

---

## 1. Main agent 판단 (진입)

- 사용자 요청은 **새 기능/개선 요청**이며, "규칙"은 **docs/analysis-search-consistency-by-screen.md** 및 **search-consistency-domain** 스킬에 정의된 "사용자 관련 검색 통일 규칙"으로 해석 가능.
- **Step 1**이 필요: 요건 문서(§1·§2·§3) 작성.  
- **agent-collaboration.mdc**에 따라 Main은 §1·§2·§3를 직접 작성하지 않고 **Requirements** 서브에이전트를 **Task**로 호출.

---

## 2. Requirements 핸드오프 (Main → Requirements)

Main이 Task로 보낼 **prompt** 예시:

```
User request: "사용자 관련 검색필드를 규칙에 맞춰서 개선해줘"

Task: Author a requirement document for this improvement. Follow docs/workflow/REQUIREMENTS-AUTHORING-WORKFLOW.md (hybrid consultation).

1) Domain baseline (mandatory)
   - Read .cursor/skills/search-consistency-domain/SKILL.md and docs/analysis-search-consistency-by-screen.md.
   - "규칙" = 분석 문서의 통일 기준: 사용자·요청자 맥락 화면에서 **부서 + 이름(사용자명) + 사용자 ID** 공통 제공; scope=self일 때 해당 필터 비표시.
   - From the analysis doc §3, identify **related screens**: activity-log, statistics, user-management, permission-group-management, search-history, pending-approvals. (main은 로그 검색만 하므로 제외.)

2) Codebase investigation
   - For each related screen, gather current search/filter implementation (components, API params, hideUserFilters/scope handling).
   - Build §2: codebase summary per screen, gap vs. rule, tentative change file list (Frontend / Backend per screen).

3) Optional: RequirementsPastSearch with topic "search consistency" or "user search" to preserve any prior user intent (unless user explicitly changed scope).

4) Selective experts (if needed)
   - Contract: if search-history or pending-approvals list API needs new filter params (userId, department, username).
   - UX: if you want layout/accessibility review for shared filter component.
   - Architecture: commonization of "user search 3 axes" component across screens.

5) Orchestrate
   - §1: User scenario (사용자 관련 화면에서 부서·이름·사용자ID로 일관되게 검색/필터할 수 있게 개선; scope=self일 때는 기존처럼 본인만 조회).
   - §2: Design by scope (Backend / Frontend) and by screen; include §2.1 only if access control/PII touched.
   - §3: Test cases with Scope tags (Frontend, Backend, Integration).

6) Change target verification per REQUIREMENTS-CHANGE-TARGET-CHECKLIST.md (all affected screens and APIs).

Output: docs/requirements/yyyyMMdd-user-search-fields-consistency.md
```

---

## 3. 관련 화면 도출 (Requirements가 스킬·분석 문서에서 얻는 결과)

**규칙 출처**: `docs/analysis-search-consistency-by-screen.md` §2.1, §3.

| # | 화면 ID | 메뉴 라벨 | 현재 검색/필터 | 규칙 대비 개선 방향 |
|---|---------|------------|----------------|---------------------|
| 1 | activity-log | 활동 이력 | 사용자ID, 사용자명, IP, 액션타입 (부서 없음) | **부서** 추가 → 부서·이름·사용자ID + (IP, 액션타입). scope=self 시 필터 블록 비표시 유지. |
| 2 | statistics | 활동로그 통계 | 로그타입, 사용자ID, 부서, IP (이름 없음) | **이름(사용자명)** 추가 → 부서·이름·사용자ID + (로그타입, IP). scope=self 시 필터 비표시 유지. |
| 3 | user-management | 사용자 관리 | 부서 트리만 (검색 폼 없음) | **부서·이름·사용자ID** 검색 폼 추가 → 트리/목록 필터. |
| 4 | permission-group-management | 권한 그룹 관리 | 그룹별 사용자 추가 시 userId 드롭다운만 | **부서·이름**으로 후보 목록 필터 후 사용자ID 선택. |
| 5 | search-history | 검색 이력 | 페이지/정렬만 (요청자 필터 없음) | **요청자(사용자ID)·부서·이름** 필터 추가. (API 지원 시) |
| 6 | pending-approvals | 승인 대기 | 요청자 컬럼만 (필터 없음) | **요청자(사용자ID)·부서·이름** 필터 추가. (API 지원 시) |

**제외**: main(로그 검색) — 사용자 3축 규칙 대상 아님.

---

## 4. 설계 단계에서의 진행 (§2 구조 예시)

Requirements가 **orchestrate** 단계에서 만드는 **§2 설계 초안**이 아래와 같이 진행되는 것으로 가정.

### 4.1 §2.1 공통 규칙 (규칙 요약)

- **통일 축**: 사용자 관련 화면에서 **부서**, **이름(사용자명)**, **사용자 ID**를 공통 기본으로 제공.
- **scope=self**: activity-log, statistics에서 `screenScopes[화면] === 'self'`이면 부서·사용자ID·사용자명·IP 필터 **전체 비표시**, API에도 해당 파라미터 미전송. 백엔드는 기존처럼 현재 사용자로 고정 조회.
- **참조**: docs/analysis-search-consistency-by-screen.md §2.1, §2.4.

### 4.2 §2.2 화면별 설계 (관련 화면 + 담당 스코프)

| 화면 | Frontend 변경 요약 | Backend 변경 요약 |
|------|--------------------|-------------------|
| **activity-log** | UserActivityLogSearchForm에 부서 필드·departmentList; 기존 hideUserFilters 블록에 부서 포함. List에서 부서 목록 로드. | UserActivityLogSearchRequest에 department; Controller scope=self 시 setDepartment(null); Service에서 department 시 app_user 조인 필터. |
| **statistics** | StatisticsFilters에 사용자명(이름) 필드 추가; 기존 userId·부서·IP와 동일 블록, hideUserFilters 시 비표시. | 통계 API에 username 파라미터 추가(선택); 서비스에서 이름 필터 적용. |
| **user-management** | 상단에 검색 폼(부서·이름·사용자ID) 추가; 트리/목록을 해당 조건으로 클라이언트 필터. | (선택) 계층 API에 필터 파라미터 추가 가능. 없으면 프론트만 필터. |
| **permission-group-management** | 그룹별 사용자 추가 다이얼로그에 부서·이름 필터 추가; addableUsers 목록을 부서·이름으로 필터 후 userId 선택. | 없음 (기존 getUsers 등 활용). |
| **search-history** | SearchHistoryList 상단에 요청자·부서·이름 필터 폼; loadList 시 해당 파라미터 전달. | GET /api/search-history에 optional requesterUserId, department, username 쿼리 추가; Service.list에서 필터 적용. |
| **pending-approvals** | PendingApprovals 상단에 요청자·부서·이름 필터 폼; loadList 시 해당 파라미터 전달. | GET /api/search-history/pending에 optional requesterUserId, department, username 쿼리 추가; Service.listPending에서 필터 적용. |

### 4.3 §2.3 적용 순서 제안 (분석 문서 §4.2 반영)

1. **Backend**: search-history / pending list API에 필터 파라미터 추가 (필요 시 Contract 스텝 선행).
2. **공통**: 부서·이름·사용자ID를 재사용하는 공통 컴포넌트/훅 검토 (Architecture 공통화 검토 시).
3. **화면별 Frontend**: 활동 이력(부서) → 통계(이름) → 사용자 관리(검색 폼) → 권한 그룹 관리(필터) → 검색 이력·승인 대기(필터 폼 + API 연동).

### 4.4 §2.4 변경 파일 목록 (예상, tentative)

- **Backend**: UserActivityLogSearchRequest, UserActivityLogController, UserActivityLogService; ActivityStatisticsController, ActivityStatisticsService; SearchHistoryController, SearchHistoryService (등).
- **Frontend**: UserActivityLogSearchForm, UserActivityLogList; StatisticsFilters, ActivityStatistics; UserManagement; PermissionGroupPanel; SearchHistoryList; PendingApprovals (등).
- **Contract/spec**: API 정의서·스펙에 검색 이력/승인 대기 목록 API 필터 파라미터 반영.

---

## 5. 설계 단계 이후 흐름 (참고)

- **Step 2**: (필요 시) Security — 접근 제어·PII만 해당 시 §2.1.
- **Step 3**: Contract — search-history/pending API 변경 시 스펙 갱신. 필요 시 UX(공통 필터 UI), Architecture(공통 컴포넌트) 병렬 자문.
- **Step 4**: Backend / Frontend 위임 시 **HANDOFF-CHECKLIST** 적용.  
  - **Frontend** handoff 시 체크리스트의 **"Search/filter (user-context screens)"** 항목 적용 → `docs/analysis-search-consistency-by-screen.md` 또는 search-consistency-domain 스킬 참조를 프롬프트에 포함하여, 위 6개 화면 구현 시 통일 축과 scope=self 규칙이 적용되도록 함.
- **Step 5**: QA — §3 테스트 케이스로 검증.

---

## 6. Dry-run 검증 표

| 검증 항목 | 결과 |
|-----------|------|
| 사용자 요청 → "규칙"이 분석 문서·스킬로 해석 가능 | ✓ |
| 관련 화면 6개 도출 (activity-log, statistics, user-management, permission-group-management, search-history, pending-approvals) | ✓ |
| main 제외 (로그 검색은 사용자 3축 대상 아님) | ✓ |
| Requirements 핸드오프에 search-consistency-domain + 분석 문서 읽기 명시 | ✓ |
| §2 설계에 화면별 개선 방향·담당 스코프(Backend/Frontend)·적용 순서·변경 파일 목록 포함 | ✓ |
| scope=self 규칙이 activity-log, statistics에 명시됨 | ✓ |
| Frontend handoff 시 Search/filter 체크리스트 항목으로 분석 문서/스킬 참조 가능 | ✓ |

---

**정리**: "사용자 관련 검색필드를 규칙에 맞춰서 개선해줘" 요청 시, Main이 Requirements를 호출하고, Requirements는 **search-consistency-domain** 스킬과 **docs/analysis-search-consistency-by-screen.md**를 읽어 **관련 화면 6개**를 도출한 뒤, 위와 같은 **§2 설계 구조**(공통 규칙 → 화면별 설계 → 적용 순서 → 변경 파일 목록)로 진행하는 단계가 dry-run으로 확인됨.
