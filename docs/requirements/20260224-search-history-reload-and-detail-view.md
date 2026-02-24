# 20260224 - 검색 이력 재조회 시 조건 표시 및 자세히 보기

## 1. 사용자 요건 내용

### 요건 설명
검색 이력 화면에서 (1) 재조회 시 메인 검색 화면의 **검색 조건**이 저장된 조건과 동일하게 표시되도록 하고, (2) 검색 이력 목록에서 **자세히 보기**로 전체 검색 조건을 보기 좋게 볼 수 있도록 한다.

### 사용자 시나리오
1. 사용자가 검색 이력 목록에서 **재조회**를 클릭한다.
2. **기대**: 메인으로 이동 후 검색이 실행되며, **검색 조건 입력란(시작일시, 종료일시, 시스템명, 키워드 등)**에 저장된 조건이 그대로 채워져 있어, “지금 보이는 결과가 어떤 조건으로 나온 것인지” 한눈에 알 수 있다.
3. 사용자가 검색 이력 목록에서 **자세히 보기**를 클릭한다.
4. **기대**: 해당 이력의 **전체 검색 조건**이 읽기 쉬운 형태(키·값 정리 또는 pretty JSON)로 표시된다.

### 기대 결과
- 재조회 후 검색 조건창(필드별 검색 폼)에 저장된 조건이 동일하게 표시된다.
- 검색 이력 조회 창에서 “자세히 보기”로 전체 검색 조건을 pretty하게 확인할 수 있다.

---

## 2. 설계

### 기술 설계

#### 문제 분석
1. **재조회 시**: `getSearchHistoryDetail`로 받은 `searchParams`로 API 검색은 수행되나, `ImageLogSearchForm`은 자체 `formData` 상태만 사용하여 **초기값을 받지 않음**. 따라서 재조회 후에도 폼에는 기본값(예: 오늘 날짜)만 보이고, 실제 사용된 조건과 불일치한다.
2. **자세히 보기**: 목록에는 `searchParamsSummary` 요약만 있고, 전체 조건을 보는 UI가 없다.

#### 해결 방안

**프론트엔드**
- **재조회 시 검색 조건 동일 표시**
  - `LogGrid`: 재조회로 들어온 경우(`initialSearchParams` 존재) **검색 조건을 폼용 형태로 변환**해 `ImageLogSearchForm`에 `initialFormValues`(또는 동일 의미 prop)로 전달.
  - `ImageLogSearchForm`: `initialFormValues` prop을 받아, 마운트/갱신 시 폼 상태를 해당 값으로 동기화. API의 `startDate`/`endDate`가 `"yyyy-MM-dd HH:mm:ss"`이면 `datetime-local`용 `"yyyy-MM-ddTHH:mm:ss"`로 변환. `keywords`가 배열이면 쉼표 구분 문자열로.
- **자세히 보기**
  - `SearchHistoryList`: 각 행에 “자세히 보기” 버튼 추가. 클릭 시 `getSearchHistoryDetail(id)` 호출 후, **모달 또는 드로어**에서 `searchParams`를 키·값 목록 또는 pretty JSON으로 표시. 레이블은 한글/영문 매핑 가능(예: startDate → 시작일시).

**백엔드**
- 변경 없음. 기존 `GET /api/search-history/{id}` 응답(`logType`, `searchParams` 등) 그대로 사용.

### 변경 파일 목록

#### 프론트엔드
- `frontend/src/components/LogGrid.js` — `initialSearchParams`를 폼 초기값 형태로 변환해 `ImageLogSearchForm`에 전달.
- `frontend/src/components/ImageLogSearchForm.js` — `initialFormValues`(또는 동일 명칭) prop 수신, `useEffect`로 폼 상태 동기화(날짜/키워드 형식 변환).
- `frontend/src/components/SearchHistory/SearchHistoryList.js` — “자세히 보기” 버튼 추가, 상세 조회 후 모달/패널로 전체 검색 조건 pretty 표시.

#### 백엔드
- 없음.

---

## 3. 테스트 수행 방안

### 테스트 케이스 목록

| ID | 구분 | 시나리오(입력·조건) | 기대 결과 | 검증 방법 |
|----|------|----------------------|-----------|------------|
| TC-01 | 정상 | 검색 이력에서 “재조회” 클릭 → 메인 이동 | 검색 결과가 해당 조건으로 조회됨. **검색 조건 입력란**에 시작일시·종료일시·시스템명·키워드 등이 저장된 값과 동일하게 표시됨. | 수동: 재조회 후 폼 필드 값 확인 |
| TC-02 | 정상 | 검색 이력에서 “자세히 보기” 클릭 | 해당 이력의 전체 검색 조건이 읽기 쉬운 형태로 표시됨(모달/패널). | 수동: 자세히 보기 → 조건 내용 확인 |
| TC-03 | 엣지 | 재조회한 뒤 로그 타입이 이미지로그가 아닌 경우 | pb_feplog 등이면 SearchForm 사용; 해당 폼에 초기값 전달 시 동일 원칙 적용하거나, 이미지로그만 적용 시 스킵. | 수동(필요 시) |

### 테스트 시나리오
- 시나리오 1: 이미지로그로 검색 → 복호화 승인 요청 → 검색 이력에서 해당 건 “재조회” → 메인에서 폼 값과 결과 일치 여부 확인.
- 시나리오 2: 검색 이력 목록에서 아무 건 “자세히 보기” → 전체 조건 표시 확인 후 닫기.

---

## 4. 체크리스트
- [x] 재조회 시 `ImageLogSearchForm`에 초기값 전달 및 폼 동기화
- [x] 자세히 보기 버튼 및 전체 검색 조건 pretty 표시
- [x] §5 테스트 결과 기록

---

## 5. 테스트 결과
- **단위 테스트**: 프론트엔드 해당 컴포넌트용 단위 테스트 파일 없음. `npm test -- --watchAll=false` → No tests found (--passWithNoTests 시 0으로 종료).
- **수동 검증**: TC-01(재조회 후 폼 조건 동일 표시), TC-02(자세히 보기 모달)는 구현 완료 후 수동 확인 권장.
- **변경 요약**: LogGrid — 재조회 시 `initialSearchParams` 기반으로 첫 렌더부터 `initialFormValues` 전달. SearchHistoryList — 자세히 보기 버튼·모달(한글 라벨, Escape/오버레이 닫기) 추가.
- **검증**: Frontend 재시작 후 health check — frontend 3001 → 200, backend 9200 → success.

---

## 6. 참고
- 검색 이력 API: `docs/api-definition.md` 검색 이력 섹션, `getSearchHistoryDetail`.
- 기존 재조회 플로우: `SearchHistoryList` → `onReSearch` → `App` `handleReSearchFromHistory` → `LogGrid` `initialSearchParams`.
