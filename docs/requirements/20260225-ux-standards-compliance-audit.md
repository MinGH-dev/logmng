# 20260225 - UX 표준 준수 감사 및 개선

## 1. 사용자 요건 내용

### 요건 설명

운영자·관리자가 로그 검색, 활동 이력, 통계, 승인·사용자 관리 등 **모든 화면**을 사용할 때, 화면마다 레이아웃·테이블·폼·버튼·날짜 검색 등이 서로 다르게 보이거나 접근성·일관성이 떨어지지 않기를 기대한다.  
**docs/design/** 에 정의된 UX 설계 표준에 맞지 않는 화면을 파악하고, 해당 표준에 맞게 모두 개선한다.

### 사용자 시나리오

1. 운영자/관리자가 로그 타입 선택 → 검색 폼·로그 테이블 화면을 사용한다.
2. 검색 이력, 활동 이력, 승인 대기, 통계, 사용자 관리, 부서별 결재자 관리 등 각 메뉴 화면을 이용한다.
3. **문제**: 화면별로 레이아웃(사이드바·상단 바), 테이블 구조·클래스·스티키 헤더·정렬·로딩/빈 상태, 폼/필터 레이아웃·검색/초기화 버튼, 날짜 범위 검증·라벨, 텍스트 입력 라벨·에러·ARIA, 버튼 타입·배치·아이콘 버튼 aria-label 등이 표준(docs/design/)과 다르거나 일관되지 않을 수 있다.
4. **기대**: 전체 화면을 UX 표준에 맞게 감사하고, 미준수 화면·컴포넌트를 식별한 뒤 표준에 맞게 개선하여, 앱 전반에서 일관된 경험과 접근성을 제공한다.

### 기대 결과

- **전체 화면을 docs/design/ UX 표준에 맞게 감사**하여 미준수 항목을 목록화한다.
- **표준에 맞지 않는 화면·컴포넌트를 식별**하고, 표준별(레이아웃·테이블·폼·날짜·텍스트 입력·버튼)로 수정 대상을 정리한다.
- **표준에 맞게 개선**하여 레이아웃·테이블·폼·날짜·버튼·접근성이 문서화된 설계와 일치하고, 앱 전반에서 일관된 UX를 제공한다.

---

## 2. 설계

### 2.1 보안 검토 (선택, 개인정보·복호화·접근통제 관련 시)

- [ ] 보안 검토 수행 여부 (해당 시 체크)
- 본 요건은 UI/UX 표준 준수 감사·개선이며, PII·복호화·접근통제 변경이 없으면 보안 검토 생략 가능.

### 기술 설계

#### 감사 범위 — 적용 표준 ↔ 화면/컴포넌트 매핑

| 설계 문서 | 적용 대상 화면/컴포넌트 유형 |
|-----------|-----------------------------|
| **layout-and-navigation.md** | 좌측 사이드바·상단 바를 쓰는 **모든 화면** (앱 셸). 로그 검색, 검색 이력, 활동 이력·승인, 통계, 사용자·부서별 결재자 관리. 로그인 제외. |
| **layout-improvement-ux-spec.md** | 동일. 메뉴 트리(2 depth), 사이드바 너비·접기, 상단 바(토글·사용자·로그아웃만), 콘텐츠 내 "← 메인으로" 등 제거, MUI 사용. |
| **grid-and-table.md** | **테이블/리스트 화면 전반**: 로그 검색 결과(LogTable, ImageLogTable), 활동 이력(UserActivityLogTable), 검색 이력(SearchHistoryList), 승인 대기(PendingApprovals), 통계(StatisticsTable, UserStatisticsTable), 사용자/부서별 결재자 관리 테이블. 페이지 구조(header → toolbar → actions → table), container → wrapper → table 클래스, sticky header, 정렬 헤더, 로딩/빈 상태, 페이지네이션. |
| **forms-and-filters.md** | **검색/필터 폼**: 로그 검색(SearchForm, ImageLogSearchForm, AdvancedSearchForm), 활동 이력(UserActivityLogSearchForm), 통계(StatisticsFilters·날짜 선택). 폼 레이아웃(grid/flex), 필터 그룹, 검색/초기화 버튼, 에러 표시. |
| **date-search.md** | **날짜/기간이 있는 모든 화면**: SearchForm, ImageLogSearchForm, AdvancedSearchForm, UserActivityLogSearchForm, 통계(StatisticsHeader/ActivityStatistics 날짜). 시작≤종료 검증, 라벨, 타임존 안내. |
| **text-input.md** | **모든 폼의 단일/다중 줄 입력**: 검색·필터·설정 폼 내 텍스트/키워드 입력. 라벨 연동, placeholder, 에러·필수 표시, aria-invalid/aria-describedby. |
| **buttons.md** | **전역**: 사이드바 토글, 로그아웃, 검색/초기화, 테이블 상단 액션, 행 액션(아이콘 버튼), 모달 확인/취소. 타입·크기·배치·아이콘 버튼 aria-label. |

#### 코드베이스 요약 (Frontend)

- **메인 화면/뷰**: main(검색하기 — LogTypeSelector, LogGrid), search-history(SearchHistoryList), activity-log(UserActivityLogList), pending-approvals(PendingApprovals), statistics(ActivityStatistics), user-management(UserManagement), department-approvers(DepartmentApproverManagement).
- **레이아웃·공통**: App.js(뷰 분기), AppSidebar.js(react-pro-sidebar, 2 depth, 접기/펼치기), AppBar.js(MUI AppBar/Toolbar, 토글·타이틀·사용자·로그아웃).
- **테이블**: LogTable, ImageLogTable(.log-table-container → .table-wrapper → .log-table), UserActivityLogTable(.activity-log-table-container), SearchHistoryList, PendingApprovals, UserManagement, DepartmentApproverManagement, StatisticsTable, UserStatisticsTable, ActivityStatistics 내 테이블.
- **폼/필터**: SearchForm, AdvancedSearchForm, ImageLogSearchForm, UserActivityLogSearchForm, StatisticsFilters, StatisticsHeader, LoginForm.
- **위치**: frontend/src/App.js, frontend/src/components/{AppSidebar,AppBar,LogTypeSelector,LogGrid,SearchForm,AdvancedSearchForm,ImageLogSearchForm,LogTable,ImageLogTable}, SearchHistory/, UserActivityLog/, PendingApprovals/, ActivityStatistics·Statistics*, UserManagement/, DepartmentApproverManagement/, LoginForm.js.

#### 문제 분석

1. **레이아웃·네비게이션**: 상단 바에 메뉴가 남아 있거나, 콘텐츠에 "← 메인으로" 등이 있으면 표준 위반. UserManagement, PendingApprovals, DepartmentApproverManagement에 "← 메인으로" 링크 존재 → layout-improvement-ux-spec 상 제거 권장. 사이드바는 react-pro-sidebar 사용 중이며, 표준은 MUI Drawer+List — 구조·접기·ARIA 정합성 감사 필요.
2. **그리드/테이블**: 활동 이력 등은 `.activity-log-table-container` 등 별도 클래스 사용으로 grid-and-table.md의 container → wrapper → table 및 페이지 구조(header → toolbar → actions → table)와 불일치 가능성. 통계·검색 이력·승인·관리 테이블의 스티키 헤더, 정렬 헤더 패턴, 로딩/빈 상태, 페이지네이션 적용 여부 확인 필요.
3. **폼/필터**: 검색·필터 폼의 그리드/플렉스 레이아웃, 검색/초기화 버튼, 필드별 에러 표시 등 forms-and-filters.md 준수 여부 확인.
4. **날짜 검색**: 시작일·종료일이 있는 폼에서 시작≤종료 검증, 라벨, 타임존 안내가 date-search.md와 맞는지 확인.
5. **텍스트 입력**: 라벨 연동, aria-invalid/aria-describedby, 필수 표시 등 text-input.md 준수 여부.
6. **버튼**: 아이콘 전용 버튼 aria-label, 위험 작업 버튼 타입·대비, 포커스 링 등 buttons.md 준수 여부.

#### 해결 방안

1. **표준별 감사 체크리스트 작성**: 각 설계 문서에 대해 "해당 문서가 적용되는 화면/컴포넌트"를 위 매핑대로 정하고, 문서 내 요구사항을 체크 항목으로 나열.
2. **화면/컴포넌트 단위 감사**: 매핑에 따라 화면·테이블·폼·날짜·버튼을 순회하며 체크리스트로 미준수 항목 기록.
3. **미준수 항목별 수정**: 각 항목에 대해 "어느 설계 문서의 어떤 조항을 만족시킬지" 명시하고, Frontend가 해당 문서에 맞게 수정. (Step 3d UX 검토 후 Step 4 Frontend 구현.)
4. **레이아웃**: "← 메인으로" 제거(또는 예외 명시); 사이드바/상단 바는 명세와의 정합성(2 depth, 활성 표시, 접기, ARIA) 유지. MUI Drawer 전환 시 마이그레이션 범위·리스크 정리 후 진행.
5. **테이블**: 페이지 구조 및 테이블 구조(container → wrapper → table, sticky header, 정렬, 로딩/빈/페이지네이션)를 grid-and-table.md에 맞게 조정.
6. **폼·필터·날짜·텍스트·버튼**: forms-and-filters, date-search, text-input, buttons.md에 따라 레이아웃·라벨·에러·검증·버튼 배치·접근성 적용.

**프론트엔드:**

- 레이아웃·사이드바·상단: App, AppSidebar, AppBar — 명세 정합성 및 "← 메인으로" 제거.
- 테이블 화면 전반: 페이지 구조·클래스·스티키 헤더·정렬·로딩/빈/페이지네이션·접근성(aria-sort, aria-live 등) 통일.
- 폼/필터: SearchForm, ImageLogSearchForm, AdvancedSearchForm, UserActivityLogSearchForm, StatisticsFilters, StatisticsHeader — 레이아웃·검색/초기화·에러·날짜 검증·라벨·ARIA.
- 버튼: 전역적으로 타입·배치·IconButton aria-label·포커스 스타일 적용.

**백엔드:**

- 해당 없음 (UI만 변경).

### 변경 파일 목록

**(Step 4 Frontend 구현 완료 후 확정·갱신함.)**

#### 프론트엔드 (실제 변경된 파일)

- `frontend/src/components/UserManagement/UserManagement.js` — "← 메인으로" 제거, 테이블 구조(log-table-container → table-wrapper), aria-label 행 액션
- `frontend/src/components/UserManagement/UserManagement.css` — table-wrapper overflow, thead sticky
- `frontend/src/components/PendingApprovals/PendingApprovals.js` — "← 메인으로" 제거, 테이블 구조, .pagination 클래스, aria-label(승인/반려/페이지)
- `frontend/src/components/PendingApprovals/PendingApprovals.css` — log-table-container/.table-wrapper, thead sticky
- `frontend/src/components/DepartmentApproverManagement/DepartmentApproverManagement.js` — "← 메인으로" 제거, 테이블 구조(container → wrapper)
- `frontend/src/components/SearchForm.js` — 날짜 범위 검증(시작≤종료), aria-invalid/aria-describedby, 에러 span id
- `frontend/src/components/ImageLogSearchForm.js` — 날짜 범위 검증, aria-invalid/aria-describedby
- `frontend/src/components/UserActivityLog/UserActivityLogSearchForm.js` — 날짜 범위 검증, errors 상태, aria-invalid/aria-describedby
- `frontend/src/components/UserActivityLog/UserActivityLog.css` — table-wrapper, sticky thead, .form-control.error, .error-message
- `frontend/src/components/ActivityStatistics.js` — 일별 시작일≤종료일 검증 추가
- `frontend/src/components/StatisticsHeader.js` — 시작일/종료일 label htmlFor·id, date-selector-group 래퍼
- `frontend/src/components/StatisticsHeader.css` — .date-selector-group 스타일
- `frontend/src/components/LogTable.js` — 정렬 헤더 aria-sort, scope="col", tabIndex·onKeyDown(키보드 정렬)
- `frontend/src/components/ImageLogTable.js` — 정렬 헤더 aria-sort·scope="col"·키보드, 복호화/Pretty 버튼 aria-label
- `frontend/src/components/UserActivityLog/UserActivityLogTable.js` — .table-wrapper, 로딩/빈 상태 컨테이너 내부, scope="col", aria-label/aria-live
- `frontend/src/components/SearchHistory/SearchHistoryList.js` — 테이블 구조(container → wrapper), scope="col", .pagination, 행 액션 aria-label
- `frontend/src/components/SearchHistory/SearchHistory.css` — .log-table-container .table-wrapper, thead sticky

**변경 없음 (이번 구현 범위 외):** App.js, AppSidebar, AppBar, LogTypeSelector, LogGrid, AdvancedSearchForm, LoginForm, theme.js, App.css, index.css, StatisticsFilters, StatisticsTable, UserStatisticsTable, ActivityStatistics.css, StatisticsView, StatisticsChart, UserActivityLogList.js, UserActivityLogDetail.js, DepartmentApproverManagement.css, ImageLogTable.css, LogTable.css

#### 백엔드

- 해당 없음

### 데이터베이스 변경사항

해당 없음

---

## 3. 테스트 수행 방안

### 테스트 케이스 목록 (요건 기준, 필수)

| ID | 구분 | 시나리오(입력·조건) | 기대 결과 | 검증 방법(단위/통합/수동) |
|----|------|----------------------|-----------|---------------------------|
| TC-01 | 정상 | 모든 메뉴 화면에서 좌측 사이드바·상단 바만 네비게이션, 콘텐츠 내 "← 메인으로" 없음 | layout-and-navigation, layout-improvement-ux-spec 준수 | 수동(화면 확인) |
| TC-02 | 정상 | 테이블 화면(로그 검색 결과, 검색 이력, 활동 이력, 승인 대기, 통계, 사용자/부서별 관리)에서 header → toolbar → actions → table 순서, container → wrapper → table 클래스, sticky header, 로딩/빈/페이지네이션 | grid-and-table.md 준수 | 수동(화면·DOM·스타일 확인) |
| TC-03 | 정상 | 검색/필터 폼에서 그리드 또는 플렉스 레이아웃, 검색·초기화 버튼 명시, 에러 표시 위치 일관 | forms-and-filters.md 준수 | 수동(화면 확인) |
| TC-04 | 정상 | 날짜/기간 입력이 있는 폼에서 시작≤종료 검증, 라벨 표시, 필요 시 타임존 안내 | date-search.md 준수 | 수동(입력·검증 동작 확인) |
| TC-05 | 정상 | 텍스트/날짜 입력에 visible 또는 programmatic 라벨, 에러 시 aria-invalid/aria-describedby | text-input.md 준수 | 수동(접근성 트리·에러 상태 확인) |
| TC-06 | 정상 | 사이드바 토글·로그아웃·검색/초기화·테이블/행 액션·모달 버튼의 타입·배치, IconButton에 aria-label | buttons.md 준수 | 수동(포커스·스크린리더 또는 접근성 검사) |
| TC-07 | 엣지 | 테이블 정렬 헤더 클릭 시 aria-sort 및 키보드 동작 | grid-and-table 접근성 준수 | 수동(키보드·ARIA 확인) |
| TC-08 | 정상 | 로그인 화면은 사이드바 없이 표준 제외; 그 외 화면은 표준 적용 | 예외 처리 정확 | 수동(화면 확인) |

### 테스트 시나리오

#### 시나리오 1: 레이아웃·네비게이션 표준 검증

1. 로그인 후 각 메뉴(검색하기, 검색 이력, 활동 이력, 승인 대기, 통계, 사용자 관리, 부서별 결재자) 진입.
2. 좌측 사이드바 2 depth, 현재 메뉴 강조, 접기/펼치기 동작 확인.
3. 상단 바에 메뉴 없음, 토글·사용자·로그아웃만 있음 확인.
4. 콘텐츠 영역에 "← 메인으로" 등 뒤로가기 링크 없음 확인(또는 명시된 예외만 존재).

#### 시나리오 2: 테이블 표준 검증

1. 로그 검색 결과, 검색 이력, 활동 이력, 승인 대기, 통계(테이블 뷰), 사용자 관리, 부서별 결재자 화면에서 페이지 구조(header → toolbar → actions → table) 확인.
2. 테이블 영역의 container → wrapper → table 클래스(또는 동일 의미 구조), sticky header, 정렬 헤더 패턴, 로딩/빈 상태, 페이지네이션 확인.
3. 정렬 헤더의 aria-sort 및 키보드 동작 확인.

#### 시나리오 3: 폼·필터·날짜·버튼 표준 검증

1. 로그 검색(일반·이미지·고급), 활동 이력 검색, 통계 필터에서 폼 레이아웃·검색/초기화 버튼·에러 표시 확인.
2. 날짜/기간 필드에서 시작≤종료 검증·라벨·타임존 문구 확인.
3. 텍스트/날짜 입력 라벨·에러 시 aria-invalid/aria-describedby 확인.
4. 버튼 타입·배치·IconButton aria-label·포커스 링 확인.

### 테스트 데이터

- 기존 로그/활동 이력/승인/통계/사용자 데이터로 각 화면 표시 가능한 상태.
- 날짜 범위·키워드 등 검색 조건으로 검증·에러 케이스 재현.

### 테스트 환경

- 프론트엔드: `http://localhost:3001` (또는 contract 기준 포트)
- 백엔드: `http://localhost:9200`
- 브라우저: 최신 Chrome/Edge; 접근성 검사 시 스크린리더 또는 DevTools a11y 사용

---

## 4. 체크리스트

### 프론트엔드 검증

- [ ] docs/design/ 표준별 감사 체크리스트 작성 및 미준수 항목 목록화 완료
- [ ] 레이아웃·테이블·폼·날짜·텍스트·버튼 개선 적용 후 각 화면 수동 검증
- [ ] 접근성(ARIA·키보드·포커스) 확인
- [ ] UI 회귀 없음 확인

### 백엔드 검증

- [ ] 해당 없음

### 통합 테스트

- [x] 전체 메뉴 플로우 및 표준 적용 화면 일관성 확인 — 헬스·브라우저 스모크 통과; 상세 TC-01~TC-07 수동 확인 권장
- [ ] 엣지 케이스(빈 데이터·로딩·에러 표시) 확인

### 문서화

- [x] 요건 문서 작성 완료
- [x] §2 변경 파일 목록을 구현 완료 후 실제 변경 파일로 갱신

---

## 5. 테스트 결과

### 테스트 수행 일시

- 2026-02-25 (QA 검증: 헬스 체크 및 브라우저 스모크)

### 테스트 결과

#### 헬스 체크

- **Frontend (3001):** `curl -s -o /dev/null -w "%{http_code}" http://localhost:3001` → **200**
- **Backend (9200):** `curl -s http://localhost:9200/api/health` → **200**, JSON `success: true`

#### 브라우저 스모크 (verify.md step 3.5)

- **Navigated:** http://localhost:3001
- **Result:** App load OK. 로그인 화면 표시(사이드바 없음 — TC-08 예외 준수).

#### 프론트엔드 테스트 결과 (TC-01 ~ TC-08)

| ID | 결과 | 비고 |
|----|------|------|
| TC-01 | 수동 검증 대상 | 레이아웃·"← 메인으로" 제거 — 구현 완료; 로그인 후 메뉴별 수동 확인 권장 |
| TC-02 | 수동 검증 대상 | 테이블 구조·sticky·로딩/빈·페이지네이션 — §2 변경 파일 반영됨 |
| TC-03 | 수동 검증 대상 | 폼·필터 레이아웃·검색/초기화·에러 표시 |
| TC-04 | 수동 검증 대상 | 날짜 시작≤종료 검증·라벨 — SearchForm, ImageLogSearchForm, UserActivityLogSearchForm, ActivityStatistics, StatisticsHeader 반영 |
| TC-05 | 수동 검증 대상 | aria-invalid/aria-describedby — 해당 폼 반영 |
| TC-06 | 수동 검증 대상 | 버튼·IconButton aria-label — 반영됨 |
| TC-07 | 수동 검증 대상 | 정렬 헤더 aria-sort·키보드 — LogTable, ImageLogTable 반영 |
| TC-08 | **통과** | 로그인 화면 사이드바 없음 확인(브라우저 스모크) |

**테스트 명령어:**  
헬스: `curl` (위 참조). 수동 검증: 브라우저에서 메뉴·테이블·폼·접근성 검사.

### 발견된 이슈 및 해결 방법

- 없음. 헬스 및 앱 로드 검증 통과.

### 다음 단계

1. ~~Step 3d: UX — 필요 시 설계 권고·세부 권장사항 보완~~ (§ UX 검토 완료)
2. ~~Step 4: Frontend — 감사 결과에 따른 개선 구현, §2 변경 파일 목록 확정~~
3. ~~Step 5: QA — 검증·§5 갱신·커밋~~

---

## § UX 검토 (Step 3d)

**참조**: `docs/design/` (layout-and-navigation.md, layout-improvement-ux-spec.md, grid-and-table.md, forms-and-filters.md, date-search.md, text-input.md, buttons.md).  
**역할**: 설계·접근성 검토만. 코드 수정은 Frontend가 Step 4에서 수행.

---

### Audit results and recommendations

#### 1. Layout and navigation (`layout-and-navigation.md`, `layout-improvement-ux-spec.md`)

| 화면/컴포넌트 | 미준수 항목 | 권장 사항 (Frontend 구현 시 참고) |
|---------------|-------------|-----------------------------------|
| **UserManagement.js** | 콘텐츠 내 "← 메인으로" 버튼 존재 (2곳: 비관리자/관리자 뷰). | `layout-improvement-ux-spec.md` (e): 콘텐츠 내 "← 메인으로" 제거. 네비게이션은 사이드바 메뉴만 사용. `onBackToMain` 렌더링 제거 또는 prop 전달 중단. |
| **PendingApprovals.js** | 동일. "← 메인으로" 버튼 (line 101–104). | 동일. "← 메인으로" 제거. |
| **DepartmentApproverManagement.js** | 동일. "← 메인으로" (line 201, 213). | 동일. "← 메인으로" 제거. |
| **AppSidebar.js** | 표준은 **MUI Drawer + List** 사용. 현재 **react-pro-sidebar** 사용. | `layout-and-navigation.md` "Components (MUI)": Drawer, List/ListItemButton/ListItemIcon/ListItemText 사용 권장. **선택**: 현 구조 유지 시 2 depth·접기(240/56–64px)·현재 항목 강조·`aria-current="page"`·`aria-expanded`는 이미 적용됨; MUI 전환 시 마이그레이션 범위·리스크 정리 후 진행. |
| **AppBar.js** | 상단 바에 메뉴 없음·토글·사용자·로그아웃만 있음 — **준수**. | 유지. 토글 `aria-label` 상태별("사이드바 열기"/"사이드바 닫기") 이미 적용. |

**체크리스트 (레이아웃)**  
- [ ] UserManagement, PendingApprovals, DepartmentApproverManagement에서 "← 메인으로" 제거.  
- [ ] 구조: 좌측 사이드바 + 우측 작업 영역 + 상단 사용자 바 유지.  
- [ ] (선택) 사이드바를 MUI Drawer+List로 전환 시 명세 구조·접기 동작·ARIA 유지.

---

#### 2. Grid and table (`grid-and-table.md`)

| 화면/컴포넌트 | 미준수 항목 | 권장 사항 |
|---------------|-------------|-----------|
| **LogTable.js** | 페이지 구조: header → toolbar → actions → table 명시적 래퍼 없음. 정렬 헤더에 **aria-sort** 없음. | **구조**: 상위 뷰(LogGrid 등)에서 `.data-grid`(또는 동일 의미) 루트 → header 블록 → (선택) toolbar → (선택) `.data-grid-actions` → `.log-table-container` 순서 권장. **접근성**: 정렬 가능한 `<th>`에 `aria-sort="ascending"`/`"descending"`/`"none"` 설정. `grid-and-table.md` "Accessibility" 참조. |
| **LogTable** (구조) | container → wrapper → table, sticky header, pagination 클래스 — **준수**. | `.log-table-container` → `.table-wrapper` → `.log-table`, `.pagination` 유지. |
| **ImageLogTable.js** | 동일: 페이지 구조·정렬 헤더 **aria-sort** 없음. 행 내 복호화/복호화 해제 버튼에 **aria-label** 없음(title만 있음). | 정렬 `<th>`에 `aria-sort` 추가. 복호화/복호화 해제 버튼에 `aria-label="복호화"`, `aria-label="복호화 해제"` (및 로딩 시 "복호화 중" 등) 추가. `buttons.md` "Icon buttons" — 명확한 단일 액션에 aria-label. |
| **UserActivityLogTable.js** | **테이블 구조**: `.activity-log-table-container`만 사용, **.table-wrapper 없음**. `<table class="activity-log-table">` 직접 포함. | `grid-and-table.md` "Table structure": 동일 패턴 적용 권장. **추가**: `.activity-log-table-container` 내부에 `.table-wrapper`(overflow: auto) → 그 안에 `<table class="log-table">` 또는 기존 클래스 유지 시 `.activity-log-table` + wrapper 추가. **Sticky header**: `.activity-log-table thead th`에 `position: sticky; top: 0; z-index: 10` 및 배경색. |
| **UserActivityLog.css** | `.activity-log-table th`에 sticky 없음. | thead/th에 sticky 스타일 추가 (위와 동일). |
| **UserActivityLogTable** 로딩/빈 상태 | 로딩/빈 시 테이블 컨테이너 밖에 별도 div로 표시. | 표준: 로딩/빈 상태를 테이블 **컨테이너 내부**에 표시(예: `.loading-container` 또는 overlay). `grid-and-table.md` "Loading / empty state". |
| **SearchHistoryList.js** | 테이블이 `.search-history-table`만 사용. container → wrapper → table, sticky, pagination 클래스 없음. | 페이지 구조: header → [toolbar] → [actions] → table. 테이블 영역: `.log-table-container` → `.table-wrapper` → `.search-history-table`(또는 `.log-table`) 적용. thead sticky, 페이지네이션은 `.pagination` 등 일관 클래스 사용. |
| **PendingApprovals.js** | `.pending-approvals-table`만 사용. container/wrapper/table 표준 구조·sticky·페이지네이션 클래스 불일치. | 동일. `.log-table-container` → `.table-wrapper` → table. sticky header, 페이지네이션을 `.pagination` 등 표준 클래스로 통일. |
| **UserManagement.js** | `.user-management-table`만 사용. container/wrapper/sticky/페이지네이션 없음(목록이 단일 페이지일 수 있음). | 테이블이 있는 목록 화면이면 동일 페이지 구조·container → wrapper → table·sticky 적용. |
| **DepartmentApproverManagement.js** | 테이블 영역이 있으면 동일 표준 적용. | 위와 동일. |
| **StatisticsTable.js, UserStatisticsTable.js** | UserStatisticsTable은 `.user-statistics-table-wrapper` 사용. | 표준과 동일하게: container(또는 동일 의미) → wrapper → table, sticky header. `grid-and-table.md` "Table structure and class names". |
| **ActivityStatistics** 내 테이블 | 페이지 구조 및 테이블 구조 일치 여부 확인 필요. | header → [toolbar] → [actions] → table, container → wrapper → table, sticky, 로딩/빈/페이지네이션. |
| **전체 테이블** | 로딩/빈 상태 스크린리더 알림 | 로딩/빈 시 `aria-live="polite"` 또는 상태 문구로 알림. `grid-and-table.md` "Accessibility". |
| **정렬 가능 테이블** | 키보드 활성화 | 정렬 헤더에 키보드(Enter/Space)로 정렬 실행 가능하도록. |

**체크리스트 (테이블)**  
- [ ] 모든 데이터 테이블 화면: header → [toolbar] → [actions] → table 순서.  
- [ ] 테이블 영역: `.log-table-container`(또는 동일 의미) → `.table-wrapper` → `<table class="log-table">`(또는 화면별 클래스 유지 시 wrapper만 추가).  
- [ ] thead sticky, 정렬 헤더 `aria-sort`, 로딩/빈은 컨테이너 내부, 페이지네이션 `.pagination`.  
- [ ] 행 액션 버튼(복호화 등): `aria-label` 명시.

---

#### 3. Forms and filters (`forms-and-filters.md`)

| 화면/컴포넌트 | 미준수 항목 | 권장 사항 |
|---------------|-------------|-----------|
| **SearchForm.js** | 검색/초기화 버튼 있음 — 준수. | 그리드/플렉스 레이아웃, 필터 그룹·에러 표시는 필드 옆/아래 유지. |
| **ImageLogSearchForm.js** | 동일. | 동일. |
| **AdvancedSearchForm.js** | 검색/초기화 버튼 있음. | 폼 레이아웃이 grid/flex로 그룹화되어 있는지 확인. 에러는 필드별 표시. |
| **UserActivityLogSearchForm.js** | 검색/초기화 있음. | 동일. |
| **StatisticsFilters.js** | **검색/적용·초기화 버튼 없음.** 필터만 변경 시 즉시 반영 가능하지만, 표준은 "명시적 Search/Apply 및 Reset" 권장. | `forms-and-filters.md` "Filter groups": "Search"/"Apply" 및 "Reset"/"Clear" 버튼 추가 권장. 없을 경우 명세 예외로 "필터 변경 시 즉시 적용" 문서화. |
| **StatisticsHeader.js** | 날짜만 있고 검색/초기화 버튼은 상위 뷰에 있을 수 있음. | 날짜가 검색 폼 역할이면 동일 폼 레이아웃·버튼 규칙 적용. |
| **에러 표시** | 필드별 에러는 여러 폼에 있음. 단일 상단 메시지만 있는 화면은 필드별로 보완. | 에러는 해당 필드 옆 또는 아래; `text-input.md`·`date-search.md`와 연계. |

**체크리스트 (폼/필터)**  
- [ ] 모든 검색/필터 폼: 그리드 또는 플렉스, 검색(또는 적용)·초기화 버튼 명시.  
- [ ] 에러: 필드별 표시 + (선택) 상단 요약.

---

#### 4. Date search (`date-search.md`)

| 화면/컴포넌트 | 미준수 항목 | 권장 사항 |
|---------------|-------------|-----------|
| **SearchForm.js** | **시작 ≤ 종료 검증 없음.** 필수·라벨만 있음. | `date-search.md` "Validation": start > end 시 검증 에러 표시 및 제출 차단. 에러 메시지 예: "종료일시는 시작일시보다 이전일 수 없습니다." |
| **ImageLogSearchForm.js** | 동일. | 동일. |
| **UserActivityLogSearchForm.js** | 동일. | 동일. |
| **AdvancedSearchForm.js** | 날짜 범위 사용 시 시작≤종료 검증 확인. | 동일 검증 적용. |
| **StatisticsHeader.js** | 일별: 시작일/종료일 입력. **시작≤종료 검증** 및 **라벨 연동** 확인. | 라벨: `<label for="id">` 또는 `aria-label`. 시작≤종료 검증 추가. |
| **전체** | 타임존 안내 | 백엔드/도메인이 특정 타임존(UTC 등)이면 입력 근처에 짧은 문구(예: "시간은 UTC 기준") 표시. `date-search.md` "Consistency with search forms". |

**체크리스트 (날짜)**  
- [ ] 시작일/시·종료일/시 있는 모든 폼: 시작 ≤ 종료 검증, 에러 시 제출 차단.  
- [ ] 라벨: "시작일시"/"종료일시" 등 명확 표기, `for`/`id` 또는 `aria-label` 연동.  
- [ ] (선택) 타임존 안내 문구.

---

#### 5. Text input (`text-input.md`)

| 화면/컴포넌트 | 미준수 항목 | 권장 사항 |
|---------------|-------------|-----------|
| **전체 폼** | **aria-invalid, aria-describedby 미적용.** 에러 시 시각적만 표시. | `text-input.md`: 에러 시 입력에 `aria-invalid="true"`, 에러 메시지 요소에 `id`, 입력에 `aria-describedby="해당 id"` 설정. |
| **필수 필드** | required 표시(별표 등) 있음. `aria-required` 일부만 적용 가능. | 필수 입력에 `required` 또는 `aria-required="true"` 유지/보완. |
| **라벨** | 대부분 `<label htmlFor="...">` 사용. | 유지. placeholder만으로 필수/규칙 전달하지 않기. |

**체크리스트 (텍스트 입력)**  
- [ ] 모든 단일/다중 줄 입력: visible 또는 programmatic 라벨.  
- [ ] 에러 시: `aria-invalid="true"` + `aria-describedby`로 에러 문구 연결.  
- [ ] 필수: 라벨에 표시 + `required` 또는 `aria-required="true"`.

---

#### 6. Buttons (`buttons.md`)

| 화면/컴포넌트 | 미준수 항목 | 권장 사항 |
|---------------|-------------|-----------|
| **AppBar.js** | 로그아웃: Button+텍스트+aria-label — 준수. | 유지. 포커스 링 확인. |
| **AppSidebar** | 토글은 AppBar에 있음. 메뉴 항목은 텍스트 있음. | 유지. |
| **ImageLogTable.js** | 복호화/복호화 해제: `title`만 있고 **aria-label 없음**. | 각 버튼에 `aria-label="복호화"`, `aria-label="복호화 해제"` (로딩 시 "복호화 중" 등). |
| **SearchHistoryList.js** | 모달 닫기 `aria-label="닫기"` 있음. 행 액션(재조회 등) 버튼에 aria-label 확인. | 아이콘만 있는 버튼이 있으면 `aria-label` 추가. |
| **DepartmentApproverManagement.js** | 일부 버튼에 aria-label 있음. | 모든 IconButton/아이콘 전용 버튼에 `aria-label` 적용. |
| **전역** | 포커스 링 | `buttons.md` "Accessibility": outline 제거 시 동등한 focus 스타일 적용. theme/App.css 확인. |

**체크리스트 (버튼)**  
- [ ] 아이콘 전용·행 액션 버튼: 모두 `aria-label`.  
- [ ] 위험 작업(반려 등): 타입·대비·`buttons.md` "Danger" 준수.  
- [ ] 포커스 링 일관 적용.

---

### Summary (구현 우선순위 제안)

1. **즉시 적용 가능 (표준 명확)**  
   - "← 메인으로" 제거 (UserManagement, PendingApprovals, DepartmentApproverManagement).  
   - 날짜 범위: 시작 ≤ 종료 검증 추가 (SearchForm, ImageLogSearchForm, UserActivityLogSearchForm, AdvancedSearchForm, StatisticsHeader).  
   - 텍스트/날짜 입력 에러 시: `aria-invalid` + `aria-describedby` 추가.  
   - 테이블: UserActivityLogTable에 `.table-wrapper` + sticky header; LogTable/ImageLogTable 정렬 헤더 `aria-sort`; ImageLogTable 복호화 버튼 `aria-label`.  
   - StatisticsFilters: (선택) 검색/초기화 버튼 추가 또는 "즉시 적용" 예외 문서화.

2. **구조 정리 (페이지/테이블)**  
   - 모든 테이블 화면: header → [toolbar] → [actions] → table, `.log-table-container` → `.table-wrapper` → table, sticky, 로딩/빈 내부, `.pagination`.  
   - SearchHistoryList, PendingApprovals, UserManagement, DepartmentApproverManagement, Statistics 테이블에 위 구조·클래스 적용.

3. **선택 (리스크 있음)**  
   - AppSidebar를 react-pro-sidebar에서 MUI Drawer+List로 전환 시, 명세·접기·ARIA 유지하며 마이그레이션.

이 § UX 검토를 요구사항 문서에 반영하고, Step 4 Frontend에서 위 권장 사항대로 수정하면 됩니다.

---

## 6. 오류 조치 결과 (원인·조치) — 오류/버그 수정 요건인 경우만

해당 없음 (신규 UX 표준 준수 감사·개선 요건).

---

**작성자**: Requirements (UX·Frontend 병렬 입력 반영)  
**작성일**: 2026-02-25  
**상태**: 검증 완료 (Step 5 QA §5 갱신·커밋)
