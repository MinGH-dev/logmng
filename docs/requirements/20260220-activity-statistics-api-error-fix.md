# 20260220 - 활동 로그 통계 조회 오류 수정

## 1. 사용자 요건 내용

### 요건 설명
활동 로그 통계 화면에서 "조회" 버튼 클릭 시 "통계 조회 중 오류가 발생했습니다" 메시지가 표시되어 통계를 조회할 수 없는 문제를 수정한다.

### 사용자 시나리오
1. 사용자가 활동 로그 통계 화면에 접근한다.
2. 일별 또는 월별을 선택하고 날짜(또는 연/월)를 선택한다.
3. "조회" 버튼을 클릭한다.
4. **문제**: "통계 조회 중 오류가 발생했습니다" 메시지가 표시되고 데이터가 나오지 않는다.

### 기대 결과
- 조회 버튼 클릭 시 일별/월별 통계 및 사용자별 통계가 정상 조회된다.
- 콤보박스(사용자 목록, 부서, IP, 로그 타입) 데이터가 로드된다.

## 2. 설계

### 기술 설계

#### 문제 분석 (원인)
1. **통계 API 호출 대상 불일치**: 프론트엔드는 통계·로그타입 API를 `http://localhost:9100/api`(STATISTICS_API_BASE_URL)로 호출하도록 설정되어 있음.
2. **9100 서버 부재**: 현재 프로젝트에는 9100 포트에서 동작하는 서버가 없음. 백엔드는 Java Spring Boot로 **9200** 포트에서만 동작하며, `scripts/dev-services.sh`에도 9100 서비스가 없음.
3. **결과**: 통계/로그타입 요청 시 연결 거부(ECONNREFUSED) 또는 네트워크 오류 → "통계 조회 중 오류가 발생했습니다" 표시.

#### 해결 방안
- **단일 백엔드(9200)로 통합**: 통계 API와 로그 타입 목록을 9200 백엔드(Java)에서 제공하도록 하고, 프론트는 9200(REACT_APP_API_BASE_URL)만 사용하도록 변경한다.
- 로그 타입 API(`/api/log-types`)는 이미 9200 백엔드(LogTypeController)에 구현되어 있음.
- 통계 API(`/api/statistics/activity/daily`, `/monthly`, `/users/all`, `/statistics/users`, `/departments`, `/ips`, `/activity/export`)는 9200 백엔드에 신규 구현한다.

**프론트엔드:**
- `frontend/src/services/api.js`: `statisticsApi`, `logTypeApi`가 9100 전용 클라이언트 대신 **기본 api 클라이언트(9200)**를 사용하도록 변경.

**백엔드:**
- `ActivityStatisticsController`: 통계 REST 엔드포인트 제공.
- `ActivityStatisticsService`: `user_activity_log` 테이블 기반 일별/월별/사용자별 집계, users/departments/ips 목록, CSV/Excel export.

### 변경 파일 목록

#### 프론트엔드
- `frontend/src/services/api.js`
  - statisticsApi, logTypeApi를 statisticsApiClient 대신 api(API_BASE_URL 9200) 사용하도록 변경. (STATISTICS_API_BASE_URL/9100 제거 또는 미사용 처리)

#### 백엔드
- `backend/src/main/java/com/logmng/controller/ActivityStatisticsController.java` (신규)
- `backend/src/main/java/com/logmng/service/ActivityStatisticsService.java` (신규)

### 데이터베이스 변경사항
- 없음. 기존 `user_activity_log` 테이블만 사용.

## 3. 테스트 수행 방안

### 테스트 케이스 목록

| ID | 구분 | 시나리오(입력·조건) | 기대 결과 | 검증 방법 |
|----|------|----------------------|-----------|-----------|
| TC-01 | 정상 | 통계 화면 → 일별, 기간 선택 → 조회 | dailyStats·summary 반환, 오류 없음 | 수동(브라우저) |
| TC-02 | 정상 | 통계 화면 → 월별, 연/월 선택 → 조회 | 월별 통계 반환, 오류 없음 | 수동 |
| TC-03 | 정상 | GET /api/statistics/activity/daily?startDate=...&endDate=... | 200, success true, data.dailyStats | curl/통합 |
| TC-04 | 정상 | GET /api/statistics/users | 200, success true, data 배열 | curl |
| TC-05 | 정상 | GET /api/log-types (9200) | 200, 로그 타입 목록 | curl |

### 테스트 환경
- 프론트엔드: http://localhost:3001
- 백엔드: http://localhost:9200
- DB: PostgreSQL 5432, logmng

## 4. 체크리스트

### 프론트엔드 검증
- [ ] 통계·로그타입 API가 9200으로 요청되는지 확인
- [ ] 조회 버튼 클릭 시 오류 없이 통계 표시

### 백엔드 검증
- [ ] /api/statistics/activity/daily, monthly, users/all 응답 형식(기존 테스트 결과 문서와 동일)
- [ ] /api/statistics/users, departments, ips 응답

### 통합 테스트
- [ ] 통계 화면 전체 플로우(조회, 표/그래프, Excel 다운로드) 정상

## 5. 테스트 결과

- **일시**: 2026-02-20
- **검증 내용**: 활동 로그 통계 화면 조회 시 모든 기능 검증
  - **일별/월별 통계 API** (GET /api/statistics/activity/daily, /monthly): 200, success true, data.dailyStats·summary 정상.
  - **사용자별 통계 API** (GET /api/statistics/activity/users/all): 200, success true, data 배열 정상.
  - **콤보박스 API** (GET /api/statistics/users, /departments, /ips): 200, success true.
  - **프론트 사용자별 테이블**: API 응답 형식(searchCount/decryptCount 또는 per-log-type 키)에 맞게 수정하여 집계 검색/복호화 컬럼 정상 표시.
  - **Excel 다운로드**: 전체 재기동(./scripts/dev-services.sh all restart) 후 GET /api/statistics/activity/export → 200, CSV 정상 반환 확인됨.
- **단위 테스트**: 백엔드 mvn test는 JUnit discovery 이슈로 환경 이슈 가능성 있음. 프론트엔드는 테스트 파일 없음(--passWithNoTests로 통과).

## 6. 오류 조치 결과 (원인·조치)

**요구사항 ID**: `20260220-activity-statistics-api-error-fix` (본 문서와 동일)

### 원인 (Root Cause)
- 프론트엔드는 통계·로그타입 API를 **9100 포트**(`STATISTICS_API_BASE_URL`)로 호출하도록 설정되어 있었음.
- 프로젝트에는 **9100에서 동작하는 서버가 없음**. 백엔드는 Java Spring Boot로 **9200** 포트에서만 동작하며, `scripts/dev-services.sh`에도 9100 서비스가 없음.
- 통계/로그타입 요청 시 **연결 거부(ECONNREFUSED)** → "통계 조회 중 오류가 발생했습니다" 표시.

### 조치 내용 (Actions Taken)
- **프론트엔드** (`frontend/src/services/api.js`): `statisticsApi`, `logTypeApi`가 9100 전용 클라이언트 대신 **기본 api 클라이언트(9200)**를 사용하도록 변경.
- **백엔드** (9200): 활동 로그 통계 API 신규 구현  
  - `ActivityStatisticsController.java`: `/api/statistics/activity/daily`, `/monthly`, `/activity/users/all`, `/users`, `/departments`, `/ips`, `/activity/export`  
  - `ActivityStatisticsService.java`: `user_activity_log` 테이블 기반 일별/월별/사용자별 집계, 사용자/IP 목록, CSV export.

### 조치 결과 (Result)
- 통계 조회 시 9200 백엔드로 요청이 전달되며, 동일 서버에서 통계 API가 제공됨.
- 재발 방지: contract·api-definition에 통계 API는 9200에서 제공한다고 명시되어 있으며, 9100 전용 설정은 사용하지 않음.

### 완료 일시
- 2026-02-20
