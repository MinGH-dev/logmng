# Search fields by screen (화면별 검색 필드 디자인 정의서)

Per-field design definition for **검색하기 (main)** and **활동 이력 (activity-log)**. Definition items schema: `docs/design/search-field-definition-items.md`. UX and Frontend agents must use this document when designing or implementing search/filter UI.

---

## ⚠️ 동일 이름·다른 성격 필드 — 피드백 요청

다음 필드는 **검색하기**와 **활동 이력** 양쪽에 같은 이름으로 존재하지만, **화면 성격과 의미가 다릅니다**. 진행 방향 결정 전까지는 **화면별로 별도 정의**해 두었으며, 시각적 크기(width/height/padding)는 동일하게 권장합니다.

| 필드명 | 검색하기 (main) | 활동 이력 (activity-log) | 결정 필요 사항 |
|--------|-----------------|--------------------------|----------------|
| **startDate** | 로그 검색 기간(시작) — DB 로그 발생 시점 | 활동 발생 기간(시작) — 사용자 활동 시점 | (1) 동일 필드로 통일(같은 라벨·크기만 유지, 의미는 화면별) vs (2) 화면별 필드로 유지(main.startDate / activity-log.startDate) |
| **endDate** | 로그 검색 기간(종료) | 활동 발생 기간(종료) | 위와 동일 |

**진행 시**: 위 표의 "결정 필요 사항"에 대해 사용자/제품 결정을 받은 뒤, (1) 통일 시에는 공통 정의 한 곳으로 합치고, (2) 유지 시에는 현재처럼 화면별 정의를 유지하세요. **결정 없이 한쪽만 바꾸지 마세요.**

---

## 1. 검색하기 (main) — 필드 정의

검색하기 화면은 **로그 타입 선택 후** 타입별로 다른 폼을 사용합니다.  
- **pb_feplog**: `SearchForm.js`  
- **java_fw_imglog**: `ImageLogSearchForm.js`  
- **고급 검색 (java_fw_imglog)**: `AdvancedSearchForm.js` — 토큰 기반 쿼리 빌더; 단일 필드 테이블이 아니므로 본 문서 §1.3으로만 참조.

### 1.1 검색하기 — SearchForm (pb_feplog)

| fieldId | label | controlType | block | width | height | padding | constraints | validation | defaultValue | placeholder | dataSource (select만) |
|---------|-------|-------------|-------|-------|--------|---------|--------------|------------|--------------|-------------|------------------------|
| startDate | 시작일시 | datetime-local | row1-date | min 140px, max 220px | 34px | 6px 8–10px | start ≤ end | startDate ≤ endDate; 필수 | 오늘 00:00:00 | — | — |
| endDate | 종료일시 | datetime-local | row1-date | min 140px, max 220px | 34px | 6px 8–10px | start ≤ end | startDate ≤ endDate; 필수 | 오늘 23:59:59 | — | — |
| media_gb | 매체코드 | text | log-type-specific | min 100px, max 200px | 34px | 6px 8–10px | — | — | '' | 매체코드 | — |
| tr_code | TR Code | text | log-type-specific | min 100px, max 200px | 34px | 6px 8–10px | — | 필수 | '' | TR Code | — |
| loginId | Login ID | text | log-type-specific | min 100px, max 200px | 34px | 6px 8–10px | — | — | '' | Login ID | — |
| keywords | 키워드 검색 | text | log-type-specific | min 160px, 1fr | 34px | 6px 8–10px | — | — | '' | 키워드1, 키워드2, 키워드3 (OR 조건으로 검색) | — |
| showDecryptOption | (키워드 입력 시 복호화 옵션 노출) | — | — | — | — | — | — | — | false | — | — |
| decryptData | 키워드 검색 시 복호화 여부 체크 | checkbox | log-type-specific | — | 44px min touch | — | — | — | false | — | — |

- **scopeWhenSelf**: 해당 없음 (main은 사용자 맥락 검색 아님).  
- **API 날짜 형식 (SearchForm)**: 전송 시 `HH24MISSMS3` 등 타입별 스펙 따름.

### 1.2 검색하기 — ImageLogSearchForm (java_fw_imglog)

| fieldId | label | controlType | block | width | height | padding | constraints | validation | defaultValue | placeholder | dataSource (select만) |
|---------|-------|-------------|-------|-------|--------|---------|--------------|------------|--------------|-------------|------------------------|
| startDate | 시작일시 | datetime-local | row1-date | min 140px, max 220px | 34px | 6px 8–10px | start ≤ end | startDate ≤ endDate; 필수 | 오늘 00:00:00 | — | — |
| endDate | 종료일시 | datetime-local | row1-date | min 140px, max 220px | 34px | 6px 8–10px | start ≤ end | startDate ≤ endDate; 필수 | 오늘 23:59:59 | — | — |
| application | 시스템 명 | text | log-type-specific | min 100px, max 200px | 34px | 6px 8–10px | — | — | '' | 시스템 명 | — |
| servicegroup | 서비스그룹 | text | log-type-specific | min 100px, max 200px | 34px | 6px 8–10px | — | — | '' | 서비스그룹 | — |
| service | 서비스명 | text | log-type-specific | min 100px, max 200px | 34px | 6px 8–10px | — | '' | 서비스명 | — |
| datastring | 데이터 | text | log-type-specific | min 100px, max 200px | 34px | 6px 8–10px | — | — | '' | 데이터 검색 | — |
| headerstring | 헤더 | text | log-type-specific | min 100px, max 200px | 34px | 6px 8–10px | — | — | '' | 헤더 검색 | — |
| keywords | 키워드 검색 | text | log-type-specific | min 160px, 1fr | 34px | 6px 8–10px | — | — | '' | 키워드1, 키워드2, 키워드3 (OR 조건으로 검색) | — |
| showDecryptOption | (키워드 입력 시 복호화 옵션 노출) | — | — | — | — | — | — | — | false | — | — |
| decryptData | 키워드 검색 시 복호화 여부 체크 | checkbox | log-type-specific | — | 44px min touch | — | — | — | false | — | — |

- **API 날짜 형식 (ImageLogSearchForm)**: `yyyy-MM-dd HH:mm:ss`.

### 1.3 검색하기 — AdvancedSearchForm (고급 검색)

- **controlType**: 토큰 기반 쿼리 빌더 (필드·연산자·값 추천). 단일 입력 필드 테이블이 아님.
- **데이터 소스**: `GET /api/log-types/java_fw_imglog/fields`, `GET /api/search/suggest?logType=java_fw_imglog&context=...`
- **정의**: 필드별 크기·제한값은 로그 타입/API 스펙에 따르며, 본 문서 §1.1·§1.2의 **공통 규칙**(height 34px, padding 6px 8–10px)을 적용. 상세 필드 목록은 API 및 AdvancedSearchForm 코드 참조.

---

## 2. 활동 이력 (activity-log) — 필드 정의

**참조**: `UserActivityLogSearchForm.js`, `UserContextFilterBlock.js`. 사용자 맥락 화면이므로 scope=self 시 사용자·기타 조건 블록 숨김.

### 2.1 활동 이력 — 전체 필드

| fieldId | label | controlType | block | width | height | padding | constraints | validation | defaultValue | placeholder | dataSource (select만) |
|---------|-------|-------------|-------|-------|--------|---------|--------------|------------|--------------|-------------|------------------------|
| startDate | 시작 일시 | datetime-local | row1-date | min 140px, max 220px | 34px | 6px 8–10px | start ≤ end | startDate ≤ endDate | 서버 오늘 00:00 | — | — |
| endDate | 종료 일시 | datetime-local | row1-date | min 140px, max 220px | 34px | 6px 8–10px | start ≤ end | startDate ≤ endDate | 서버 오늘 23:59 | — | — |
| department | 부서 | select | user | min 100px, 1fr in row | 34px | 6px 8–10px | — | — | '' | — | emptyOption: 전체; options: departmentList (prop from parent) |
| username | 사용자명 | text | user | min 100px, 1fr | 34px | 6px 8–10px | maxLength 5 | — | '' | (선택) | — |
| userId | 사용자 ID | text or select | user | min 100px, 1fr | 34px | 6px 8–10px | — | — | '' | (선택) | userList 있으면 select: value=userId, label=표시명; emptyOption: 전체 |
| actionType | 액션 타입 | select | extra | min 100px, max 200px | 34px | 6px 8–10px | — | — | '' | — | Static: 전체, 로그인, 로그아웃, 검색, 조회, 복호화, 고급 검색, 내보내기 |
| ipAddress | IP 주소 | text | extra | min 100px, max 200px | 34px | 6px 8–10px | — | — | '' | IP 주소 | — (직접 입력) |

- **scopeWhenSelf**: user 블록 + extra 블록(actionType, ipAddress) = **hidden**.  
- **API 날짜 형식**: `yyyy-MM-dd HH:mm:ss`.  
- **사용자 블록 순서**: 부서 → 사용자명 → 사용자 ID (고정).

---

## 3. 통계 (statistics) — 필드 정의

**참조**: `StatisticsFilters.js`, `StatisticsHeader.js`, `UserContextFilterBlock.js`. 사용자 맥락 화면이므로 scope=self 시 사용자·기타 조건 블록 숨김. 필터 패널은 항상 펼침(접기 없음). 컨테이너 너비·간격은 활동 이력과 동일(§4 화면 간 공통 규칙). 필드별 control 크기·제한값은 `docs/design/search-field-definition-items.md` 및 §4 화면 간 공통 규칙 참조.

### 3.1 통계 — 필드 목록 및 블록

| fieldId / block | label | controlType | 위치 | width | height | padding | 비고 |
|-----------------|-------|-------------|------|-------|--------|---------|------|
| logType | 로그 타입 | select | row1 (블록 제목 위) | min 140px, max 180px | 34px | 6px 8–10px | 전체, 로그인, API 목록; role="group" + aria-labelledby |
| startDate / endDate (일별) | 시작일, 종료일 | date | header | min 150px | 34px | 6px 10px | start ≤ end; aria-invalid, aria-describedby (오류 시) |
| year / month (월별) | 연도, 월 | select | header | — | 34px | 6px 8–10px | 연도/월 선택 |
| department, username, userId | 부서, 사용자명, 사용자 ID | user block | row1 | 활동 이력과 동일 | 34px | 6px 8–10px | UserContextFilterBlock; scope=self 시 숨김 |
| ip | IP | select | row2 기타 조건 | min 100px, max 200px | 34px | 6px 8–10px | scope=self 시 기타 조건 블록 전체 숨김 |

- **블록 순서**: row1 = 로그 타입 + 사용자 블록, row2 = 기타 조건(IP) + 검색/초기화 버튼.
- **패널 너비**: `.activity-statistics` max-width 1400px (`.activity-log-list-container`와 동일).
- **간격**: row/field gap 8–12px, block 12–16px, container padding 12–16px (compact variant). `docs/design/search-field-definition-items.md` §4 (cross-field rules) 및 `forms-and-filters.md` § Compact variant 참조.

---

## 4. 화면 간 공통 규칙

- **날짜 필드 (startDate / endDate)**: § "동일 이름·다른 성격" 결정 전까지는 검색하기·활동 이력 각각 위 표대로 정의. **시각적 크기**(width, height, padding)는 동일하게 적용(예: min 140px, max 220px, 34px, 6px 8–10px).
- **사용자 맥락 화면(활동 이력, 통계 등)**: 부서·사용자명·사용자 ID는 `docs/design/search-field-definition-items.md` §4 및 `docs/analysis-search-consistency-by-screen.md`에 따라 동일 축·동일 크기 유지.
- **Compact variant**: 모든 검색/필터 폼에서 row/field gap 8–12px, block gap 12–16px, container padding 12–16px. `docs/design/forms-and-filters.md` § Compact variant.

---

## 5. 도구 참조 (UX, Frontend 에이전트)

- **정의 항목 스키마**: `docs/design/search-field-definition-items.md`  
- **화면별 필드 정의**: 본 문서 `docs/design/search-fields-by-screen.md`  
- **폼/필터 공통 규칙**: `docs/design/forms-and-filters.md`  
- **동일 이름·다른 성격 필드**: 본 문서 § "동일 이름·다른 성격 필드 — 피드백 요청". 해당 필드를 변경·통일할 때는 **사용자에게 진행 방향을 묻고** 결정 후 반영할 것.

*Related: `forms-and-filters.md`, `search-field-definition-items.md`, `analysis-search-consistency-by-screen.md`.*
