# 20260225 - 사이드바 라이브러리 도입(펼침·스크롤 안정화)

## 1. 사용자 요건 내용

### 요건 설명
기존 요건 `20260225-sidebar-submenu-expand-overflow.md`에서 정의한 **사이드바 하위 메뉴 펼침 시 다른 상위 메뉴가 가려지는** 문제에 대해, 여러 CSS 수정(flex, absolute, paper overflow 등)을 적용했음에도 사용자 환경에서 해결되지 않았다.  
사용자 요청: *"해결 안 되어, React에 이런 거 잘 해주는 라이브러리를 사용하는 게 더 확실한 해결방법 아닐까?"*

따라서 **현재 커스텀 MUI Drawer 기반 사이드바를 대체하거나 래핑**하여, **펼침(collapsible)·스크롤을 라이브러리에서 안정적으로 제공하는 React 사이드바/네비게이션 라이브러리**를 도입한다.

### 사용자 시나리오
1. 로그인 후 앱 메인 화면 진입
2. 사이드바가 펼쳐진 상태에서 한 상위 메뉴(예: 로그 검색)를 클릭해 하위 메뉴(검색하기, 검색 이력)를 연다
3. **기대**: 하위 메뉴가 펼쳐져도 "이력·승인", "통계", "관리" 등 모든 상위 메뉴가 **스크롤로 접근 가능**해야 함
4. 필요 시 사이드바 접힘(collapse) 상태에서도 정상 동작
5. 레이아웃·네비게이션 표준(왼쪽 고정 사이드바, 2단계 메뉴) 유지

### 기대 결과
- 하위 메뉴를 여러 개 펼쳐도 **모든 상위 메뉴가 항상 접근 가능** (라이브러리의 스크롤·레이아웃에 의해 보장)
- 사이드바 메뉴 영역에 **세로 스크롤**이 안정적으로 동작
- **접힌(collapsed) 상태**에서도 아이콘/토글 등으로 정상 동작
- 기존 2단계 메뉴 구조 및 라우팅 유지
- 빌드·기존 기능 회귀 없음

---

## 2. 설계

### 2.1 라이브러리 후보 비교

| 후보 | 설명 | 장점 | 단점 | 스크롤/펼침 지원 |
|------|------|------|------|-------------------|
| **shadcn/ui Sidebar** | Radix 기반 + Tailwind, `SidebarProvider` / `SidebarContent` 등 | 스크롤·collapse 내장, 접근성·문서 양호 | **Tailwind 도입 필요**, 현재 스택(MUI+Emotion)과 스타일 체계 상이 | ◎ 내장 |
| **Flowbite React Sidebar** | Tailwind 기반 React 컴포넌트, `Sidebar`, `SidebarCollapse`, 다단계 메뉴 | 다단계·collapse 지원, 사용 사례 많음 | **Tailwind 도입 필요** | ◎ 내장 |
| **React Bootstrap (Nav, Collapse)** | Bootstrap React, `Nav`, `NavDropdown`/Collapse | **Tailwind 불필요**, MUI와 병행 가능(사이드바만 Bootstrap) | 스크롤 영역은 직접 래핑 필요, MUI와 스타일 이중화 | △ 래퍼로 구현 |

**현재 스택**: React 18, MUI 5 (`@mui/material`, `@emotion/react`), Tailwind 미사용.

### 2.2 권장안 및 근거

- **권장: shadcn/ui Sidebar**  
  - **이유**: 펼침·스크롤·접힘을 컴포넌트 레벨에서 제공하며, `SidebarContent`가 스크롤 가능 영역으로 설계되어 있어 기존 flex/overflow 이슈를 라이브러리 구현에 맡길 수 있음. 번들 크기는 Radix 프리미티브 위주로 관리 가능.  
  - **전제**: 프로젝트가 **Tailwind CSS 도입**에 동의하는 경우. 사이드바만 shadcn으로 하고 나머지 화면은 MUI 유지 가능(공존).
- **대안( Tailwind 비도입 시)**: **React Bootstrap**의 `Nav` + `Collapse`로 사이드바만 구현하고, 메뉴 리스트를 감싼 영역에 `overflow-y: auto`, `max-height: 100vh` 등을 적용해 스크롤 영역을 명시. MUI와 병행 사용.

**정리**: Tailwind 도입 가능하면 **shadcn/ui Sidebar** 채택 권장; 불가 시 **React Bootstrap** + 스크롤 래퍼로 구현.

### 2.3 기술 설계 요약

- **도입 라이브러리**: 위 권장안에 따라 1종 선택 후 `package.json` 반영.
- **구조**: 기존 `AppSidebar.js`의 MUI `Drawer` + `List` + `Collapse` 조합을 **선택 라이브러리의 Sidebar/Nav 컴포넌트로 교체**하거나, Drawer 내부 콘텐츠만 라이브러리 컴포넌트로 교체.
- **라우팅**: 기존 React Router 경로·메뉴 항목 매핑 유지.
- **스타일**: shadcn 선택 시 Tailwind 설정 추가; Bootstrap 선택 시 Bootstrap CSS 추가 및 사이드바 영역만 Bootstrap 클래스 사용.

### 2.4 변경 파일 목록 (예상)

#### 프론트엔드
- `frontend/package.json`
  - 선택한 라이브러리 및 전제 조건(Tailwind 또는 Bootstrap) 의존성 추가.
- `frontend/src/components/AppSidebar.js`
  - MUI Drawer 기반 커스텀 메뉴를 **선택 라이브러리의 Sidebar/Nav 구조로 교체** (또는 Drawer paper 내부를 해당 컴포넌트로 교체).
- (shadcn 선택 시) `frontend/tailwind.config.js`, `frontend/postcss.config.js`, `frontend/src/index.css` 등 Tailwind 설정.
- (필요 시) `frontend/src/App.js`
  - 레이아웃 구조 변경 시(예: SidebarProvider로 앱 루트 감싸기) 수정.

위 목록은 최종 선택 라이브러리에 따라 Frontend 구현 단계에서 확정한다.

---

## 3. 테스트 수행 방안

기존 `20260225-sidebar-submenu-expand-overflow.md`의 TC-01~TC-03을 **재사용·확장**하고, 접힌 상태 및 빌드/회귀 검증을 추가한다.

| ID | 구분 | 시나리오(입력·조건) | 기대 결과 | 검증 방법 |
|----|------|----------------------|-----------|-----------|
| TC-01 | 정상 | 사이드바 펼친 상태에서 한 상위 메뉴의 하위 메뉴 펼침 | 해당 하위 메뉴만 펼쳐지고, 아래쪽 상위 메뉴도 스크롤로 보임 | 수동(브라우저) |
| TC-02 | 정상 | 하위 메뉴가 펼쳐진 상태에서 사이드바 세로 스크롤 | 맨 위·맨 아래 상위 메뉴까지 스크롤로 접근 가능 | 수동(브라우저) |
| TC-03 | 정상 | 여러 상위 메뉴를 연속으로 펼침 | 모든 상위 메뉴가 스크롤로 접근 가능, 가려짐 없음 | 수동(브라우저) |
| TC-04 | 정상 | 사이드바를 **접힌(collapsed)** 상태로 전환 후 메뉴 접근 | 접힌 상태에서 아이콘/토글로 메뉴 접근 가능, 레이아웃 깨짐 없음 | 수동(브라우저) |
| TC-05 | 자동 | `cd frontend && npm run build` (또는 `CI=false npm run build`) | 빌드 성공, 기존 라우트·페이지 회귀 없음 | CI/로컬 빌드 |
| TC-06 | 자동 | `cd frontend && npm test -- --watchAll=false` | 기존 단위 테스트 통과 (해당 테스트 있는 경우) | 단위 테스트 |

### 테스트 시나리오 요약
- **TC-01~TC-03**: 기존 요건과 동일 — 사이드바 열기 → 하위 메뉴 펼침 → 세로 스크롤로 모든 상위 메뉴 접근 가능 여부 확인.
- **TC-04**: 라이브러리에서 collapse를 제공하는 경우, 접힌 상태에서의 동작 및 시각적 정합성 확인.
- **TC-05~TC-06**: 라이브러리 도입 후 빌드·기존 테스트 통과로 회귀 여부 확인.

---

## 4. 체크리스트

- [ ] 요건·설계 반영
- [ ] §3 테스트 케이스 수립
- [ ] 라이브러리 선택 확정 (Tailwind 도입 여부에 따라 shadcn vs Bootstrap)
- [ ] 코드 변경 (AppSidebar, package.json, 필요 시 App.js·Tailwind 설정)
- [ ] §5 테스트 결과 기록
- [ ] 검증(재시작·헬스체크) 통과
- [ ] 커밋 메시지에 요건 ID 포함

---

## 5. 테스트 결과

(구현·검증 후 QA/Frontend에서 기록)

- **TC-01~TC-04**: 수동 검증(브라우저) — 수행 일시·결과 요약
- **TC-05**: 빌드 명령·결과(성공/실패)
- **TC-06**: 단위 테스트 명령·결과(성공/실패 또는 N/A)

---

## 6. 에러 해결 결과

(해당 시에만: 버그/오류 수정 시 원인·조치 기록)

---

## 참조

- **관련 요건**: `docs/requirements/20260225-sidebar-submenu-expand-overflow.md` (기존 CSS 수정 시도 및 TC-01~TC-03 정의)
- **Handoff**: **Frontend** — 이 문서 §2(라이브러리 선택·변경 파일), §3(테스트 케이스)를 입력으로 사이드바 라이브러리 도입 및 `AppSidebar.js` 교체(또는 래핑) 구현 후 빌드·TC-01~TC-06 검증. **QA** — 검증·§5 갱신·커밋 수행.
