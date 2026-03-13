# UX 역할 분리 세분화 설계

화면 기능, 스킬, 문서, 에이전트 구성을 종합하여 **UX 관련 역할**을 세분화한 설계 문서입니다.  
역할 분리 축: **문서/스킬(정의)** · **구현(프론트엔드)** · **검토(UX 에이전트)** · **검증(QA)**.

---

## 1. 현재 UX 관련 기능 종합

### 1.1 화면·메뉴·접근 제어 (화면에서 사용 중인 기능)

| 구분 | 내용 | 코드/문서 위치 |
|------|------|----------------|
| **화면 ID** | main, search-history, activity-log, statistics, pending-approvals, user-management, user-permission-hierarchy, permission-group-management, department-approvers | `specs/permission-group-hierarchy.spec.yaml` §4.1, `frontend/src/constants/menuTree.js` (ALLOWED_SCREEN_IDS) |
| **메뉴 트리** | 2-depth (로그 검색, 이력·승인, 통계, 관리), adminOnly 그룹(관리) | `menuTree.js` MENU_TREE, adminOnly |
| **화면 접근** | canAccessView(view): is_system_admin → 전체; else allowedScreenIds. user-management / permission-group-management는 user-permission-hierarchy로도 접근 허용 | `App.js` canAccessView, `AppSidebar.js` canShowChild |
| **초기/리다이렉트** | main 없으면 첫 허용 화면으로 리다이렉트 (approval-only 대응) | `App.js` getFirstAllowedScreen, useEffect redirect |
| **라우팅 가드** | currentView 변경 시 접근 없으면 getFirstAllowedScreen으로 전환 | `App.js` useEffect hasAccess, handleNavigate |

### 1.2 화면별 기능(버튼·액션) 제어

| 구분 | 내용 | 코드/문서 위치 |
|------|------|----------------|
| **screenFunctions** | read, write, approve, decrypt (화면별) | `AuthService.resolveScreenFunctions`, `security.js` getScreenFunctions |
| **쓰기 화면** | user-management, department-approvers, user-permission-hierarchy, permission-group-management → 생성/수정/삭제 버튼 enable/disable | `screenFunctionDescriptions.js` SCREENS_WITH_WRITE |
| **승인 화면** | search-history, pending-approvals → 승인/반려 버튼 enable/disable | SCREENS_WITH_APPROVE |
| **복호화** | main → 복호화 요청 가능 여부 (screenFunctions.main.decrypt) | SCREENS_WITH_DECRYPT, api-permission-map |
| **버튼 비활성 툴팁** | 수정/생성/삭제/승인/반려/복호화 권한 없을 때 툴팁 | ACTION_DISABLED_TOOLTIPS |
| **액션 숨김** | approval-only: 재조회·재요청 등 main 의존 액션 숨김 | auth-permission-domain §Approval-only, search-history UI |

### 1.3 검색·필터·스코프 UI

| 구분 | 내용 | 코드/문서 위치 |
|------|------|----------------|
| **사용자 맥락 축** | 부서, 이름(사용자명), 사용자 ID — 활동 이력·통계·사용자관리·권한그룹·검색이력·승인대기 통일 | `docs/analysis-search-consistency-by-screen.md`, search-consistency-domain SKILL |
| **scope=self** | 해당 화면에서 부서/사용자/이름 필터 블록 비표시, API에 사용자 파라미터 미전송 | activity-log, statistics 등; hideUserFilters |
| **로그 검색(main)** | 사용자 3축 미적용; 날짜·로그타입·타입별 필드만 | analysis-search-consistency §2.2 |

### 1.4 디자인 표준·접근성

| 구분 | 내용 | 코드/문서 위치 |
|------|------|----------------|
| **그리드/테이블** | 페이지 구조, sticky header, 정렬, 페이징 | `docs/design/grid-and-table.md` |
| **레이아웃/네비게이션** | 사이드바, 2-depth 메뉴, AppBar, z-index | `docs/design/layout-and-navigation.md` |
| **버튼/폼/필터** | 버튼 타입, 폼 레이아웃, 필터 그룹 | `docs/design/buttons.md`, forms-and-filters.md, text-input.md, date-search.md |
| **접근성** | WCAG 2.1 AA, 시맨틱 HTML, ARIA, 키보드, 대비 | UX.mdc, ui-ux-domain SKILL §Requirement doc checklist |

---

## 2. 역할 분리 축 정의

역할은 다음 **세 축**으로 구분한다.

- **정의(문서/스킬)**: 무엇을 해야 하는지, 어떤 규칙/표준인지 기록.
- **구현(프론트엔드)**: 실제 UI/코드 변경. 권한·메뉴·버튼·필터 등 적용.
- **검토/검증**: 설계 일관성(UX), 계약 준수(Review), 동작 확인(QA).

### 2.1 정의 담당 (문서·스킬·스펙)

| 담당 | 산출물 | UX 관련 내용 |
|------|--------|--------------|
| **Contract / Spec** | contract.md, specs §4 (Screen IDs, screen-based access, screenFunctions) | 화면 ID 목록, API↔화면 매핑, scope 값, 에러 코드 |
| **Requirements** | 요구사항 §1·§2·§3 | 사용자 시나리오, 화면/메뉴/접근 제어 요구, §3 테스트 관점 |
| **스킬 (도메인)** | ui-ux-domain, auth-permission-domain, api-permission-map, search-consistency-domain, activity-statistics-domain | 메뉴/화면/접근, 권한/스코프, API 권한 검증, 검색 통일/scope=self |
| **디자인 표준** | docs/design/*.md | 그리드, 레이아웃, 버튼, 폼, 날짜 검색, a11y 기준 |

**역할 분리 원칙**: UX “동작 규칙”(어떤 화면에 무엇을 보여줄지, 언제 숨길지)은 **스펙·계약·스킬**에 정의되고, **프론트엔드는 그 정의를 구현**한다. 새 규칙 추가·변경 시 스펙/요구사항/스킬 먼저 갱신.

### 2.2 구현 담당 (프론트엔드)

| 담당 | 범위 | UX 관련 구현 |
|------|------|--------------|
| **Frontend (팀 리드)** | App, 라우팅, 공통, 크로스 화면 | canAccessView, getFirstAllowedScreen, 메뉴/라우팅 연동 |
| **Frontend-Auth** | 로그인, 인증 상태 | 로그인 후 allowedScreenIds/screenFunctions 반영 |
| **Frontend-ActivityLog** | 활동 이력, 통계 | scope=self 필터 숨김, 부서·이름·사용자ID 필터, 통계 필터 |
| **Frontend-Log** | 로그 검색, 검색 이력 | main 검색 폼, 검색 이력 목록, 재조회/재요청 액션 숨김(approval-only) |
| **공통 (Frontend)** | AppSidebar, menuTree, screenFunctionDescriptions, security.js | 메뉴 필터(adminOnly, canShowChild), 버튼 disable/툴팁, getScreenFunctions |
| **화면별** | UserManagement, PermissionGroupManagement, PendingApprovals, SearchHistory 등 | write/approve 기반 버튼 enable/disable, 권한 그룹 설정 UI(scope, read/write/approve/decrypt) |

**역할 분리 원칙**: “디자인 시스템 정의”는 UX 에이전트 + docs/design; **코드 작성·수정은 Frontend만** 수행. UX는 코드를 수정하지 않음.

### 2.3 검토·검증 담당

| 담당 | 시점 | UX 관련 검토/검증 |
|------|------|-------------------|
| **UX (서브에이전트)** | Step 3d, 신규/변경 화면 설계 시 | a11y, 레이아웃·네비게이션, docs/design 준수, z-index/오버레이, § UX 검토 산출 |
| **Review** | Step 4.5, 구현 후 | 계약·워크플로·표준 준수, 체크리스트 (UX 항목 포함 가능) |
| **QA** | Step 5, 빌드/재시작 후 | 권한별 메뉴/화면 접근, 버튼 상태, scope=self 필터 숨김, 브라우저 검증; 실패 시 failure scope `ux` 가능 |

---

## 3. 세분화된 UX 책임 매트릭스

아래는 **기능 영역별**로 “정의 / 구현 / 검토·검증”을 누가 담당하는지 정리한 것이다.

### 3.1 메뉴·라우팅·화면 접근

| 기능 | 정의(문서/스킬) | 구현 | 검토·검증 |
|------|-----------------|------|-----------|
| 화면 ID 목록 | Spec §4.1, contract, ui-ux-domain | menuTree ALLOWED_SCREEN_IDS, App canAccessView | Review(계약), QA(접근 TC) |
| 메뉴 트리 구조(2-depth, 그룹) | layout-and-navigation.md, ui-ux-domain | menuTree MENU_TREE, AppSidebar | UX(레이아웃), QA |
| adminOnly 그룹(관리) | Spec §4, ui-ux-domain | menuTree adminOnly, AppSidebar filteredTree | QA(비관리자 메뉴) |
| canAccessView / user-management ↔ user-permission-hierarchy | contract, auth-permission-domain | App.js, AppSidebar canShowChild | Review, QA |
| 첫 화면/리다이렉트(approval-only) | auth-permission-domain §Approval-only, Spec §5 | App getFirstAllowedScreen, useEffect | QA(approval-only 시나리오) |

### 3.2 버튼·액션·기능 제어

| 기능 | 정의(문서/스킬) | 구현 | 검토·검증 |
|------|-----------------|------|-----------|
| screenFunctions 구조(read/write/approve/decrypt) | Spec §4.4, auth-permission-domain | security.js, screenFunctionDescriptions.js | Review, QA |
| SCREENS_WITH_WRITE / SCREENS_WITH_APPROVE / SCREENS_WITH_DECRYPT | Spec §4.4, screenFunctionDescriptions, api-permission-map | screenFunctionDescriptions.js, ScreenSelectionTree, 각 화면 | QA(write/approve 미부여 시 비활성) |
| 버튼 비활성 + 툴팁 | ui-ux-domain §screenFunctions → UI, screenFunctionDescriptions ACTION_DISABLED_TOOLTIPS | 각 화면(UserManagement, PendingApprovals, PermissionGroupPanel 등) | UX(버튼 표준), QA |
| approval-only 액션 숨김(재조회·재요청 등) | auth-permission-domain §Action hiding | SearchHistory 등, allowedScreenIds.includes('main') | QA(approval-only) |

### 3.3 검색·필터·스코프 UI

| 기능 | 정의(문서/스킬) | 구현 | 검토·검증 |
|------|-----------------|------|-----------|
| 사용자 맥락 3축(부서·이름·사용자ID) | analysis-search-consistency-by-screen.md, search-consistency-domain | 활동 이력, 통계, 사용자관리, 권한그룹, 검색이력, 승인대기 | UX(폼/필터 표준), QA |
| scope=self 시 필터 블록 숨김 | Spec §4.3, search-consistency-domain, activity-statistics-domain | ActivityStatistics, UserActivityLog 등 hideUserFilters | QA(scope=self 시 필터 없음) |
| main(로그 검색) 전용 축 | analysis-search-consistency §2.2 | LogGrid, SearchForm 등 | UX(forms-and-filters), QA |

### 3.4 디자인 시스템·접근성

| 기능 | 정의(문서/스킬) | 구현 | 검토·검증 |
|------|-----------------|------|-----------|
| 그리드/테이블/레이아웃/버튼/폼 표준 | docs/design/*.md | Frontend 전체 | UX(필수 참조), Review(표준 준수) |
| a11y(키보드, ARIA, 대비) | ui-ux-domain §Requirement doc checklist, UX.mdc | Frontend | UX 검토, QA(필요 시 브라우저 a11y) |
| z-index/모달·오버레이 | layout-and-navigation.md, UX.mdc §z-index | Frontend | UX(필수 검토) |

---

## 4. 에이전트·스킬·문서 매핑 (UX 관점)

### 4.1 UX 서브에이전트(UX.mdc) — 팀 리드

- **설계 책임**: 디자인 시스템 소유(docs/design 참조·정렬).
- **검토 범위**: a11y, UI 일관성, 레이아웃·네비게이션, 인터랙션, z-index/오버레이. **단일 도메인**이면 UX-A11y / UX-Layout / UX-Components에 위임 가능(§4.5).
- **산출물**: § UX 검토 또는 디자인 노트. **코드 수정 없음** → Frontend가 반영.
- **호출 시점**: Step 3d(선택), 신규/변경 화면·권한 연동 UI 설계 시; 버그 수정 시 failure scope `ux` → Requirements → UX 검토 후 Frontend.

### 4.2 Frontend(팀 리드) vs 모듈 서브에이전트

- **Frontend**: App, AppSidebar, menuTree, screenFunctionDescriptions, security, canAccessView, getFirstAllowedScreen, 공통 컴포넌트, 크로스 화면.
- **Frontend-Auth**: 로그인·인증 UI만.
- **Frontend-ActivityLog**: ActivityStatistics, UserActivityLog, 통계/활동이력 필터·scope UI.
- **Frontend-Log**: LogGrid, SearchForm, LogTypeSelector, 검색 이력 목록·액션(재조회/재요청 숨김 포함).

권한/메뉴/접근은 **공통**이므로 보통 Frontend(팀 리드)가 담당; 특정 화면만 건드리면 해당 모듈에 위임.

### 4.3 스킬·문서 참조 (UX 역할 분리 시)

| UX 관련 판단 | 참조 스킬/문서 |
|--------------|----------------|
| 화면 ID, 메뉴, canAccessView, adminOnly | ui-ux-domain, specs §4.1, contract §화면 기반 접근 제어 |
| read/write/approve/decrypt, 버튼 비활성/툴팁 | auth-permission-domain, api-permission-map, screenFunctionDescriptions |
| approval-only, 액션 숨김 | auth-permission-domain §Approval-only, Spec §5 |
| 검색/필터 통일, scope=self | search-consistency-domain, analysis-search-consistency-by-screen.md |
| 통계·활동 이력 scope/필터 | activity-statistics-domain |
| 그리드·레이아웃·버튼·폼·a11y | docs/design/*, ui-ux-domain §Requirement doc completeness checklist |

### 4.5 UX 팀 세분화 및 위임 (Backend/Frontend와 동일 패턴)

**UX는 팀 리드**이며, 요청 범위가 **한 도메인에만** 해당하면 해당 UX 모듈 서브에이전트에 **Task 도구로 위임**한다. 메인 에이전트는 Step 3d에서 **UX만** 호출하며, UX가 내부적으로 UX-A11y/UX-Layout/UX-Components에 위임할 수 있다.

| 위임 대상 | 범위 | Prompt 파일 |
|-----------|------|-------------|
| **UX-A11y** | 접근성 전담 — WCAG 2.1 AA, 시맨틱 HTML, ARIA, 키보드/포커스, 대비, 스크린리더, 비활성 툴팁 | `docs/cursor-subagents/ux-a11y.md` |
| **UX-Layout** | 레이아웃·네비게이션 전담 — 앱 셸, 사이드바, 2-depth 메뉴, 상단바, z-index, 모달/오버레이/드로어 | `docs/cursor-subagents/ux-layout.md` |
| **UX-Components** | 디자인 시스템·컴포넌트 전담 — 버튼, 폼, 필터, 그리드/테이블, 텍스트 입력, 날짜 검색, 시각적 일관성 | `docs/cursor-subagents/ux-components.md` |

- **위임 조건**: 요청이 a11y만 / 레이아웃·메뉴·z-index만 / 컴포넌트·폼·테이블만 다룰 때 해당 서브에이전트에 위임. **복수 도메인**이거나 범위가 불명확하면 UX(팀 리드)가 직접 전체 검토.
- **산출물**: 위임 시 해당 서브에이전트가 § UX 검토 (A11y|Layout|Components) 작성; UX 팀 리드가 병합 후 Frontend에 전달.
- **에이전트 정의**: `.cursor/agents/UX.mdc`(팀 리드), `UX-A11y.mdc`, `UX-Layout.mdc`, `UX-Components.mdc`. CURSOR-SUBAGENTS-DESIGN.md §1.3 UX 세분화 표 참조.

---

## 5. 요구사항·버그 수정 시 UX 역할 흐름

1. **신규/변경 화면·권한 연동**
   - Requirements: §1·§2·§3 작성, 필요 시 **UX** 호출(§3d) → § UX 검토.
   - Contract/Spec: 화면 ID·screenFunctions·API 매핑 반영.
   - Frontend: 구현 시 docs/design + 스킬 규칙 준수.
   - QA: 권한별 메뉴/접근/버튼/필터 검증.

2. **버그 수정(검증 실패)**
   - QA: failure scope 지정(예: `ux`).
   - Requirements: 버그픽스 자식 요구사항 정리 후 **ux** → **UX** 검토 후 **Frontend** 위임.
   - UX: 설계/표준 관점 검토만; Frontend가 수정.

3. **검색/필터 통일·scope=self**
   - 요구사항에 search-consistency-domain, analysis-search-consistency 반영.
   - 구현: Frontend / Frontend-ActivityLog 등 해당 화면 담당.
   - UX: 폼·필터 레이아웃·docs/design 준수 검토.

---

## 6. 정리

- **정의**: 화면 ID, 접근 규칙, screenFunctions, 검색 축, scope=self 규칙, 디자인 표준은 **스펙·계약·스킬·docs/design**에 두고, 요구사항은 이를 반영해 작성.
- **구현**: **Frontend(및 모듈)** 만 코드 수정; 권한/메뉴/버튼/필터는 위 문서·스킬에 맞춰 구현.
- **검토**: **UX(팀 리드)**는 디자인·a11y·레이아웃·z-index 검토를 담당하며, 단일 도메인일 때 **UX-A11y / UX-Layout / UX-Components**에 위임(§4.5). **Review**는 계약·표준 준수, **QA**는 동작·권한 시나리오 검증.
- **역할 충돌 방지**: UX(및 UX-*)는 코드 미수정; Frontend는 디자인 시스템을 “정의”하지 않고 docs/design + UX 지침을 따름.  
이렇게 세분화하면 화면 기능·스킬·문서·에이전트가 일관되게 UX 역할을 나누며, Backend/Frontend와 동일하게 팀 리드 + 도메인별 위임 구조를 가진다.
