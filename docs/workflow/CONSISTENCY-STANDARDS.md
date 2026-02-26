# 프로젝트 일관성 기준 (Consistency Standards)

**Owner**: Consistency 에이전트. 이 문서는 네이밍·구조·에러 형식·로깅 등 프로젝트 전반의 규칙을 정의한다. **Review** 에이전트는 변경 검토 시 이 문서를 기준으로 적용한다.

---

## 1. API·에러 응답

- **공통 형식**: `docs/api-definition.md` §1 및 `docs/contract.md` 준수. 성공 시 `success: true`, `data`. 실패 시 `success: false`, `error`, `code`.
- **에러 코드**: 새 코드 추가 시 `docs/api-definition.md`의 에러 코드 표에 등록. 대문자 스네이크 (예: `DECRYPTION_NOT_APPROVED`, `ROW_NOT_IN_APPROVED_SNAPSHOT`).
- **HTTP 상태**: 400 (클라이언트 오류), 403 (권한), 404 (없음), 500 (서버 오류) 등 의미에 맞게 사용.

---

## 2. 네이밍

- **API 경로**: 소문자, 케밥 또는 리소스 계층 (예: `/api/search-history/{id}/approve`).
- **DB**: 스네이크 (테이블·컬럼). 예: `search_history`, `approval_status`.
- **프론트**: 컴포넌트 PascalCase, 파일 컴포넌트명과 동일 또는 kebab. API 호출·상태는 camelCase.

---

## 3. 로깅

- **레벨**: ERROR(예외·실패), WARN(복구 가능), INFO(주요 흐름), DEBUG(상세).
- **PII**: 로그에 비밀번호·개인식별 정보 직접 출력 금지. `docs/security-guide.md` 참고.

---

## 4. 파일·디렉터리

- **요건 문서**: `docs/requirements/yyyyMMdd-name.md`. 8자리 날짜, 소문자, 하이픈.
- **백엔드**: `backend/src/main/java/com/logmng/` 하위 패키지 구조 유지. DB 리소스는 `backend/src/main/resources/db/`.
- **프론트 데이터 테이블**: 리스트/표 형태 데이터 화면은 하나의 통일된 그리드 패턴 사용. 공통 컴포넌트 및 클래스 이름·정렬·페이지네이션 동작은 `docs/design/grid-and-table.md` 및 아래 §6 준수.

---

## 5. 갱신

- 규칙 추가·변경 시 **Consistency** 에이전트가 이 문서만 수정. **Review** 에이전트는 검토 시 이 문서를 참조한다.

---

## 6. 데이터 테이블(통일 그리드)

- **단일 패턴**: 리스트·표 형태 데이터(로그, 활동 로그, 검색 이력 등)는 동일한 구조·클래스·정렬·페이지네이션 동작을 사용한다.
- **공통 컴포넌트**: 통일 그리드는 하나의 공유 컴포넌트(또는 동일 규격을 따르는 래퍼)를 사용한다. 컴포넌트명은 PascalCase, 파일은 컴포넌트명과 동일 또는 kebab(§2).
- **클래스 접두사**: 페이지 루트 `.data-grid`, 테이블 영역 `.log-table-container` → `.table-wrapper` → `<table class="log-table">`. 정렬 헤더 `.sortable-header`, 페이지네이션 `.pagination`, 로딩/빈 상태 `.loading-container`. 상세는 `docs/design/grid-and-table.md` 준수.
- **정렬(필수)**: 모든 그리드/테이블에는 **정렬 기능을 필수**로 제공. 동일한 정렬 UX(클릭 시 오름/내림 토글, 아이콘, `aria-sort` 노출). 상세는 `docs/design/grid-and-table.md` § "Sorting — required for all grids/tables" 준수.
- **페이지당 표시 건수(공통 UX 규칙)**: 모든 그리드는 기본 **20건**/페이지. 페이지 크기 컨트롤은 숫자 입력란 옆에 증감(+/−) 버튼 제공; 증감 버튼으로 1건씩 변경 시 **즉시 반영**, 직접 입력 후 **엔터키** 입력 시 반영. 상세는 `docs/design/grid-and-table.md` § "Page size (rows per page)" 준수.
- **검색 필드 지정**: 사용자가 별도로 검색 대상을 지정·수정 요청한 경우가 아니면, DB 스키마(`backend/src/main/resources/db/schema.sql` 등)를 참고해 **속성(컬럼 타입·의미)에 따라 검색 필드를 자동 부여**. 상세는 `docs/design/grid-and-table.md` § "Search field assignment" 준수.
- **파일 위치**: 공유 그리드 컴포넌트는 `frontend/src/components/` 직하위 또는 `frontend/src/components/shared/`(프로젝트에서 공통 컴포넌트를 모을 경우).
