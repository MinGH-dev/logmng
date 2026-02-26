# 20260225-ux-standards-compliance-audit-bugfix-1 — 날짜 역전 제출 시 aria-invalid/aria-describedby 미노출

**부모 요구사항 ID**: `20260225-ux-standards-compliance-audit`  
**Bugfix 순번**: 1

---

## 1. 사용자 요건 내용 (실패 기술·영향)

### 발견 경로

- **언제**: 검증 단계 — 부모 요건 §3.5 브라우저 자동화 검증 중 실패
- **어떤 확인에서 실패**: **TC-05** (텍스트/날짜 입력 에러 시 aria-invalid/aria-describedby). 날짜 역전(시작일 > 종료일) 제출 후 에러 메시지는 노출되나, 해당 날짜 입력 요소에 `aria-invalid="true"` 및 `aria-describedby`가 설정되지 않음. 브라우저에서 `aria-invalid` 보유 요소 0개 확인.

### 증상 및 영향

- **레이어**: frontend
- **증상**: 시작일 > 종료일로 제출 시 에러 문구는 보이지만, 날짜 입력 필드에 `aria-invalid="true"` 및 `aria-describedby="{id-of-error-element}"`가 설정되지 않음 (text-input.md / date-search.md 미준수).
- **사용자 영향**: 접근성 — 스크린리더가 에러 상태·에러 문구를 인식하지 못함. text-input.md·date-search.md 표준 준수 실패.

### 기대 결과

- 날짜 범위 검증 실패(시작 > 종료) 시, 해당 **시작일/종료일 입력**에 `aria-invalid="true"`가 설정되고, 에러 메시지 요소와 `aria-describedby`로 연결되어 스크린리더 및 자동화 검증(TC-05)에서 인식 가능해야 한다.

---

## 2. 설계

### 원인 (추정)

- 부모 §2에는 SearchForm, ImageLogSearchForm, UserActivityLogSearchForm에 날짜 범위 검증 및 aria-invalid/aria-describedby 반영으로 기재되어 있으나, 브라우저 검증 시 해당 속성이 노출되지 않음.
- UserActivityLogSearchForm(및 SearchForm, ImageLogSearchForm, StatisticsHeader, ActivityStatistics)에서 검증 실패 시 `errors` 상태는 갱신되나, 해당 **입력 DOM에 `aria-invalid`·`aria-describedby`를 바인딩하지 않았거나**, 에러 메시지 요소에 **안정적인 `id`가 없어** 연결되지 않은 것으로 추정.

### 수정 설계 (Frontend 구현 범위)

- **대상 컴포넌트**: SearchForm, ImageLogSearchForm, UserActivityLogSearchForm, StatisticsHeader, ActivityStatistics (날짜 범위가 있는 모든 폼).
- **수정 요약**:
  1. 날짜 범위 검증 실패(시작 > 종료) 시, 해당 **시작일/종료일 입력**에 `aria-invalid="true"` 설정.
  2. 에러 메시지를 담는 요소에 **안정적인 `id`** 부여(예: `date-range-error`, `start-end-error-{formId}` 등 폼별 고유 id).
  3. 해당 입력에 `aria-describedby="{위 id}"` 설정하여 에러 문구와 연결.
  4. 검증 통과 시 `aria-invalid` 제거(또는 `"false"`) 및 `aria-describedby` 제거 또는 빈 문자열.
- **참조**: `docs/design/text-input.md`, `docs/design/date-search.md`.

### 변경 파일 목록 (예상)

**(Step 4 Frontend 구현 완료 후 확정·갱신함.)**

- `frontend/src/components/SearchForm.js` — 날짜 범위 에러 시 dateRange 상태, 시작/종료 입력에 aria-invalid·aria-describedby, 에러 id `search-form-date-range-error`
- `frontend/src/components/ImageLogSearchForm.js` — 동일, id `image-log-search-form-date-range-error`
- `frontend/src/components/UserActivityLog/UserActivityLogSearchForm.js` — 동일, id `user-activity-log-search-form-date-range-error`
- `frontend/src/components/StatisticsHeader.js` — dateRangeInvalid·dateRangeErrorId props, 일별 날짜 입력에 aria-invalid·aria-describedby
- `frontend/src/components/ActivityStatistics.js` — dateRangeInvalid 상태, 날짜 변경 시 에러 초기화, 에러 메시지 id `activity-statistics-date-range-error`, StatisticsHeader에 props 전달

### 데이터베이스 변경사항

해당 없음

---

## 3. 테스트 수행 방안

### 테스트 케이스 (재검증)

| ID | 구분 | 시나리오 | 기대 결과 | 검증 방법 |
|----|------|----------|-----------|-----------|
| **TC-05** | 정상 | 날짜 역전(시작일 > 종료일) 입력 후 폼 제출 | 해당 날짜 입력에 `aria-invalid="true"` 및 `aria-describedby` 존재 | 부모 §3.5 **브라우저 자동화** 재실행 |

### §3.5 재실행 절차 (TC-05)

- 부모 요건 §3.5 브라우저 자동화 **TC-05** 재실행.
- 절차: 날짜가 있는 폼에서 시작일 > 종료일 입력 → 제출 → `browser_snapshot` 또는 `browser_get_attribute`로 해당 날짜 입력 요소의 `aria-invalid="true"`, `aria-describedby="{id}"` 존재 여부 확인.
- 통과 시 §5에 결과 기록 후 부모 요건 재검증 루프 종료.

---

## 4. 체크리스트

- [x] Frontend: 날짜 범위 에러 시 aria-invalid/aria-describedby 및 에러 요소 id 적용
- [x] 빌드·재시작 후 QA에 재검증 위임
- [x] TC-05 재실행 통과 시 §5 갱신

---

## 5. 테스트 결과

**(QA 재검증 후 기록)**

- **TC-05 재실행**: **통과** (2026-02-26 재검증 완료)
- **비고**: 헬스 체크 후 백엔드(9200)·프론트엔드(3001) 재시작 수행. 도구: project-0-dev-browser (puppeteer_navigate, puppeteer_fill, puppeteer_click, puppeteer_evaluate). Base URL: http://localhost:3001. 로그인(admin/admin123) → 활동 이력 메뉴 이동 → 활동 이력 검색 폼에서 시작일 2025-02-02, 종료일 2025-01-01 입력 후 제출. **결과**: `#startDate`·`#endDate`에 `aria-invalid="true"`, `aria-describedby="user-activity-log-search-form-date-range-error"` 확인. 에러 요소 존재, 문구 "종료일시는 시작일시보다 이전일 수 없습니다." 표시. **TC-05 재검증: 통과.**

---

## 6. Error remedy result

- **조치**: Frontend에서 날짜 범위 검증 실패 시 시작일/종료일 입력에 `aria-invalid="true"` 및 `aria-describedby="{에러 요소 id}"` 적용 완료.
- **검증**: §3.5 TC-05 재실행 통과. 부모 요건 재검증 루프 종료.

---

**작성**: Requirements (bugfix child 정식화)  
**상태**: QA 재검증 완료 — TC-05 통과. 커밋 완료.
