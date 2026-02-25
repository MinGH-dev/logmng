# 레이아웃 개선 — UX 검토 명세

Frontend 구현 시 준수할 레이아웃·네비게이션 명세. (UX Subagent 산출, 코드 없음.)

---

## (a) 메뉴 트리 (2 depth)

| 1차 메뉴 | 2차 메뉴 | 대응 뷰 | 비고 |
|----------|----------|---------|------|
| **로그 검색** | 검색하기 | LogTypeSelector → (타입 선택 시) LogGrid | 한 2차 항목으로 타입 선택 + 검색 화면 흐름 |
| **로그 검색** | 검색 이력 | SearchHistoryList | |
| **이력·승인** | 활동 이력 | UserActivityLogList | |
| **이력·승인** | 승인 대기 | PendingApprovals | |
| **통계** | 활동로그 통계 | ActivityStatistics | 1차만 있고 2차 하나 |
| **관리** | 사용자 관리 | UserManagement | ADMIN만 표시 |
| **관리** | 부서별 결재자 | DepartmentApproverManagement | ADMIN만 표시 |

- **현재 위치**: 활성 뷰에 해당하는 1차·2차 항목 강조(배경/좌측 띠), 해당 2차 항목에 `aria-current="page"`.
- **검색하기**: 클릭 시 `currentView = 'main'`, `selectedLogType = null` → 타입 선택 후 LogGrid. "로그 타입 선택" 전용 2차는 두지 않음.

---

## (b) 레이아웃 구조

1. **좌측 사이드바 (MUI Drawer, 고정)**
   - 메인 네비게이션만.
   - 열림 너비: **240px** (또는 256px).
   - 접힌 상태: **아이콘만**, 너비 약 56~64px.
   - 토글 버튼으로 열기/접기.
   - 스크롤 시에도 고정 (`variant="permanent"` 또는 동일 효과).

2. **상단 바 (MUI AppBar)**
   - **왼쪽**: 앱 타이틀 "로그 관리 시스템"(선택) 또는 비움. **사이드바 토글 버튼** 필수(접힌 상태에서도 메뉴 진입).
   - **오른쪽**: 사용자명("환영합니다, {username}님") + 로그아웃 버튼.
   - 상단 바에는 **메뉴 버튼/네비게이션 항목 배치하지 않음**.

3. **메인 콘텐츠 영역**
   - 사이드바·AppBar 제외한 나머지. `currentView`·`selectedLogType`에 따라 기존 컴포넌트만 전환.
   - **콘텐츠 내부 "← 메인으로", "← 로그 타입 선택" 등 공통 뒤로가기 제거.** (문맥상 필요한 경우 해당 화면 내부에만 예외적으로 유지 가능.)

4. **중복 제거**
   - 헤더에 있던 메뉴 버튼은 모두 **좌측 사이드바 2 depth 메뉴로 이전**.

---

## (c) 사용 MUI 컴포넌트

- **Drawer**: 좌측 네비게이션. `variant="permanent"`(또는 `persistent`), `open`/`closed`로 접기/펼치기.
- **List / ListItemButton / ListItemIcon / ListItemText**: 1차·2차 메뉴. 2차는 들여쓰기(inset 또는 pl).
- **AppBar + Toolbar**: 상단 바. 왼쪽 토글, 오른쪽 사용자 정보·로그아웃.
- **IconButton**: 사이드바 토글, 로그아웃 등.
- **Typography**: 타이틀, 사용자명, 메뉴 라벨.
- **Collapse**(선택): 1차 메뉴 클릭 시 2차 펼치기/접기.

테마: 기업 내부 시스템 톤. primary 회색·파랑 계열, 배경·테두리 절제.

---

## (d) 사이드바 접기/펼치기

- **펼침**: 240px, 1차·2차 라벨 모두 표시.
- **접힘**: 56~64px, 1차만 아이콘. 2차는 호버 시 툴팁/팝오버 또는 1차 클릭 시 일시적으로 2차 표시.
- **토글**: AppBar 왼쪽 IconButton. 키보드 포커스 후 Enter/Space. `aria-label="사이드바 열기"` / `"사이드바 닫기"` 상태에 따라 변경.

---

## (e) 뒤로가기 버튼

- **제거**: AppBar/헤더의 "← 메인으로", "← 로그 타입 선택". 이동은 사이드바 메뉴로만.
- **선택**: SearchHistoryList, UserManagement 등 콘텐츠 상단 "메인으로" 문맥 버튼은 팀 선택. 권장은 사이드바만 사용.

---

## (f) 접근성

- 키보드: 토글·모든 메뉴·로그아웃 포커스 가능. Tab 순서: 토글 → 메뉴 → 메인 → 사용자/로그아웃.
- ARIA: 현재 페이지 `aria-current="page"`, 토글 `aria-label`, 1차 확장 시 `aria-expanded`.
- 대비·포커스 링: WCAG 2.1 AA.

---

**구현 참조**: `frontend/src/App.js` 및 기존 뷰 컴포넌트. MUI 미도입 시 `@mui/material`, `@mui/icons-material`, `@emotion/react`, `@emotion/styled` 추가 후 진행.
