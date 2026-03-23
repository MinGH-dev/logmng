# SVG 와이어프레임 (logmng)

## Chromium 기준이란?

Chrome 브라우저는 SVG를 **글자(XML)로 먼저 읽습니다.**  
그래서 **UTF-8로 깨끗하게 저장**되어 있고, **XML 문법이 맞아야** 브라우저·미리보기에서 안 깨집니다.

## 확인 방법

터미널에서 프로젝트 루트로 이동한 뒤:

```bash
./scripts/validate-svg.sh
```

전부 `OK`면 Chromium에서 열어도 같은 종류의 오류는 나지 않도록 맞춘 상태입니다.

## 폴더

- `primitives/` — 자주 쓰는 조각 (`common-pagination-bar`, `common-list-shell`, `common-linear-loading-bar`, `common-tree-layout`, `common-tree-two-pane-layout`, `common-grid-cell-select`, `common-grid-cell-combobox`, `common-grid-cell-button` 등)
- `scenes/` — 화면별 와이어프레임
- 목록형 화면 **활동 이력 / 검색 이력 / 복호화 승인 관리** (`logmng-step-05`, `06`, `08`)은 하단 **공통 페이징 바** 레이아웃과 맞춰 두었습니다.

## 본문 화면 제목 (공통)

메인 영역의 화면 제목(메뉴별 타이틀)은 모두 CSS 클래스 **`.view-title`** 로 통일했습니다.

- 규격: `24px`, `font-weight: 600`, 색 `#212121`, 폰트 `"Noto Sans KR", "Roboto", system-ui` (앱 `theme.js`와 동일 계열)
- 참고 primitive: `primitives/common-view-title.svg`
- 로그인 카드 상단 브랜드만 **`.login-brand`** (22px, primary 색)로 별도 유지


## Tree UI/UX 공통

트리 기반 화면은 아래 primitive를 공통으로 사용합니다.

- `primitives/common-tree-layout.svg`: 단일 패널 트리 + 하위 테이블 구조 (사용자 관리형)
- `primitives/common-tree-two-pane-layout.svg`: 트리 + 우측 관리 패널 구조 (권한 그룹/설정형)

`scenes/logmng-step-09-user-management.svg`는 실제 `UserManagement` 구현(`HierarchyTree`, 부서 토글, 하위 사용자 테이블) 기준으로 맞췄으며, 권한 그룹은 **단건 선택 셀렉트**로 표현합니다.

## 그리드 셀 컨트롤 (공통)

테이블/그리드 **셀 안**에 넣는 컨트롤은 아래 primitive를 참고합니다.

- `primitives/common-grid-cell-select.svg` — 단건 선택 셀렉트 (표시값 + ▼)
- `primitives/common-grid-cell-combobox.svg` — 입력 + 드롭다운 토글 분리형 콤보
- `primitives/common-grid-cell-button.svg` — 셀 안 작은 버튼

씬에서는 동일 스타일 클래스(예: `.grid-select-field`, `.grid-select-chev`)로 맞추면 됩니다.
