# 20260220 - 사용자 활동이력 오늘 날짜 조회 시 결과 없음 수정

## 1. 사용자 요건 내용

### 요건 설명
사용자 활동 이력 화면에서 "오늘" 날짜로 조회하면 조회된 활동 이력이 없다고 나온다. 사용자는 오늘 두세 번 정도 조회(검색 등)를 한 기억이 있어, 당일 데이터가 나와야 한다.

### 사용자 시나리오
1. 로그인한 사용자가 메인에서 로그 검색 등 조회를 2~3회 수행한다.
2. 사용자 활동 이력 메뉴로 이동해 시작/종료 날짜를 "오늘"로 두고 검색한다.
3. **문제**: "조회된 활동 이력이 없다"고 표시되거나 결과가 0건으로 나온다.
4. **기대**: 오늘 발생한 본인의 활동 이력(검색, 조회 등)이 목록에 나온다.

### 기대 결과
- 오늘 날짜(당일 00:00:00 ~ 23:59:59)로 검색 시 당일 생성된 활동 로그가 조회된다.
- 종료일이 "날짜만" 전달되더라도 당일 끝(23:59:59)까지 포함되도록 동작한다.

## 2. 설계

### 기술 설계

#### 문제 분석
1. **백엔드 날짜 파싱**: `UserActivityLogSearchRequest`의 `parseDateTime()`에서 `yyyy-MM-dd`(날짜만) 형식일 때 **start/end 모두** `atStartOfDay()`로 파싱하고 있음.
2. **영향**: 종료일(`endDate`)이 날짜만 오면 `2025-02-20` → `2025-02-20 00:00:00`이 되어, 조회 구간이 `[당일 00:00:00, 당일 00:00:00]`이 됨. 당일 00:00:00 이후에 발생한 모든 활동이 구간 밖으로 빠져서 결과 0건.
3. 프론트는 대부분 `yyyy-MM-dd HH:mm:ss`로 보내지만, 일부 상황(초기값/다른 클라이언트)에서 날짜만 넘어갈 수 있음. 방어적으로 endDate는 날짜만 오면 **당일 끝(23:59:59)** 로 해석하는 것이 안전함.

#### 해결 방안

**백엔드**
- `UserActivityLogSearchRequest`: `endDate`를 파싱할 때 **날짜만**(`yyyy-MM-dd`)이면 `atTime(23, 59, 59)`로 당일 끝으로 변환. `getEndDateAsDateTime()`에서만 이 규칙 적용.

**프론트엔드**
- 종료일을 당일로 둘 때 **23:59:59**까지 포함되도록 전송하는지 확인. `UserActivityLogSearchForm`의 `formatDateForAPI`는 `HH:mm` 뒤에 `:00`을 붙이므로 종료 시각이 23:59이면 `23:59:00`이 됨. 리스트 초기 검색은 이미 `23:59:59` 사용 중. 형식 일관성만 점검.

### 변경 파일 목록

#### 백엔드
- `backend/src/main/java/com/logmng/dto/request/UserActivityLogSearchRequest.java`
  - `getEndDateAsDateTime()`: 날짜만 파싱 시 `LocalDate.atTime(23, 59, 59)` 사용. 기존 `parseDateTime`은 start용으로 유지, end 전용 파싱 로직 추가.

#### 프론트엔드
- 필요 시 `UserActivityLogSearchForm.js`에서 종료일 포맷이 `23:59:59`로 나가도록 보완 (현재 23:59:00이면 당일 대부분 포함되므로 백엔드 수정만으로도 해결 가능).

### 데이터베이스 변경사항
없음.

## 3. 테스트 수행 방안

### 테스트 케이스 목록

| ID | 구분 | 시나리오(입력·조건) | 기대 결과 | 검증 방법 |
|----|------|----------------------|-----------|-----------|
| TC-01 | 정상 | POST /api/activity-log/search, startDate=오늘 00:00:00, endDate=오늘 23:59:59 | 당일 로그 포함 조회 | 단위/통합/curl |
| TC-02 | 엣지 | POST /api/activity-log/search, startDate=오늘, endDate=오늘 (날짜만) | endDate가 23:59:59로 해석되어 당일 로그 포함 | 단위/통합 |
| TC-03 | 정상 | 화면에서 오늘로 검색 | 당일 활동 이력 목록 표시 | 수동 |

### 테스트 시나리오

#### 시나리오 1: 날짜만 endDate 전달 시 당일 포함
1. `endDate`만 `yyyy-MM-dd` 형식으로 보내는 요청 구성.
2. 백엔드에서 해당 일자 23:59:59까지 조건에 포함되는지 로그/쿼리 확인.

#### 시나리오 2: E2E 오늘 조회
1. 로그인 후 로그 검색 1회 수행.
2. 사용자 활동 이력 이동 → 시작/종료 "오늘"로 검색.
3. 방금 수행한 검색 활동이 1건 이상 나오는지 확인.

## 4. 체크리스트

- [ ] 백엔드: endDate 날짜만 → 23:59:59 파싱 적용
- [ ] 단위 테스트(해당 시) 실행 및 통과
- [ ] 수동: 오늘 날짜 조회 시 활동 이력 표시 확인
- [ ] §5 테스트 결과 기록, §6 오류 조치 결과 기록

## 5. 테스트 결과

- **일시**: 2026-02-20
- **단위 테스트**: 백엔드 `mvn test` — 프로젝트 공통 이슈(JUnit 테스트 발견 실패)로 미실행. 프론트엔드 `npm test` — 테스트 파일 없음(0 matches).
- **검증**: `./scripts/dev-services.sh all restart` 후 백엔드 health 200·JSON 확인, 프론트 200 확인.
- **수동 확인 권장**: 로그인 후 로그 검색 1회 수행 → 사용자 활동 이력에서 오늘 날짜로 검색 시 해당 활동 1건 이상 표시되는지 확인.

## 6. 오류 조치 결과 (원인·조치)

**요구사항 ID**: 20260220-activity-log-today-empty-fix (본 문서와 동일)

### 원인 (Root Cause)
- **종료일을 날짜만 파싱하는 경우**: `UserActivityLogSearchRequest`에서 `endDate`가 `yyyy-MM-dd`(날짜만) 형식일 때도 `parseDateTime()`으로 **시작일과 동일하게** `atStartOfDay()`만 사용하고 있었음.
- 그 결과 조회 구간이 `[당일 00:00:00, 당일 00:00:00]`이 되어, 당일 00:00:00 이후에 발생한 모든 활동이 조건 밖으로 빠져 **0건**으로 조회됨.
- 프론트는 대부분 `yyyy-MM-dd HH:mm:ss`를 보내지만, 날짜만 넘어가는 경로나 다른 클라이언트가 있을 수 있어 방어적으로 종료일을 당일 끝으로 해석하는 것이 필요함.

### 조치 내용 (Actions Taken)
- **백엔드** `UserActivityLogSearchRequest.java`: `getEndDateAsDateTime()`에서만 사용하는 `parseEndDateTime()` 추가. `yyyy-MM-dd`일 때 `LocalDate.atTime(23, 59, 59)`로 당일 23:59:59 반환. 나머지 형식은 기존 `parseDateTime()` 재사용.
- **프론트엔드** `UserActivityLogSearchForm.js`: `formatDateForAPI()`에서 종료 시각이 `23:59`일 때 초를 `59`로 넣어 `23:59:59`로 전송하도록 수정.

### 조치 결과 (Result)
- 백엔드/프론트 재시작 후 health·프론트 200 확인 완료.
- 오늘 날짜로 조회 시 당일 활동 이력이 나오려면, 로그인 후 실제로 오늘 조회를 한 뒤 활동 이력 화면에서 오늘로 검색해 확인하면 됨.

### 완료 일시
- 2026-02-20

---

### 보완 (2026-02-20): 401 미인증 시 메시지 노출

**추가 원인**: DB에는 활동 로그가 존재함(55건, 오늘 데이터 포함). 그러나 활동 이력 검색 API(`/api/activity-log/search`)가 **인증 필수**라, 로그인하지 않은 상태에서 호출하면 401 `"로그인이 필요합니다."`가 반환됨. 프론트엔드는 401 시에도 "검색 결과 0건"처럼 빈 목록만 보여주어, 사용자가 "조회가 안 된다"고 인지함.

**추가 조치**:
- `userActivityLogService.js`: 401/403 시 throw 대신 응답 본문을 그대로 반환하도록 수정.
- `UserActivityLogList.js`: `result.code === 'UNAUTHORIZED'` 또는 로그인 관련 에러 메시지일 때 `authError` 상태로 저장하고, 화면에 **"로그인이 필요합니다. 로그인 후 다시 시도해 주세요."** 문구 표시.
- `UserActivityLog.css`: `.activity-log-auth-error` 스타일 추가.

**로그인 상태에서도 0건인 경우 (타임존/서버 날짜)**  
- **원인**: 브라우저 "오늘"이 서버/DB 날짜와 다르면(예: 한국 자정 넘어 서버는 UTC 전날) 요청 날짜에 해당하는 DB 행이 없어 0건으로 나옴. 또한 시각 경계(00:00:00 ~ 23:59:59) 비교 시 타임존에 따라 누락 가능.
- **조치**:  
  - 백엔드: 활동 이력 검색 시 `created_at >= ? AND created_at <= ?` 대신 **DATE(created_at) >= ? AND DATE(created_at) <= ?** 로 날짜만 비교하도록 변경.  
  - 프론트: 초기 "오늘" 검색 시 **서버 날짜** 사용. `GET /api/health`에서 `data.timestamp`의 날짜(yyyy-MM-dd)를 받아 그 날짜로 검색. Form은 마운트 시 검색 호출 제거하고, List만 서버 날짜로 1회 검색. 서버 날짜를 Form에 `initialServerDate`로 전달해 입력값 표시 일치.

---

## 본 요건과 무관한 이슈 (CORS / 로그 검색 API) — 분석·기록

### 사용자 제보 오류
- 브라우저 콘솔: `Access to fetch at 'http://localhost:9200/api/logs/db-refactored/search' from origin 'http://localhost:3001' has been blocked by CORS policy: Response to preflight request doesn't pass access control check: No 'Access-Control-Allow-Origin' header is present on the requested resource.`
- 실패 요청: **POST** `http://localhost:9200/api/logs/db-refactored/search` (이미지 로그 검색 등 **DB 로그 검색 API**).

### 본 요건(활동 이력 오늘 0건)과의 관계
- **위 CORS 오류는 본 요건(사용자 활동 이력 오늘 날짜 조회 0건) 수정과 무관합니다.**
- 본 요건에서 수정한 대상: **사용자 활동 이력** API (`/api/activity-log/search`) 및 해당 화면의 날짜 파싱·서버 날짜·401 메시지 표시.
- CORS 오류가 발생한 API: **DB 로그 검색** (`/api/logs/db-refactored/search`). 다른 API, 다른 경로임.

### CORS 오류의 실제 원인 (분석)
- **원인**: `AuthInterceptor`가 `/api/**`에 대해 **OPTIONS** 요청( CORS preflight )까지 인증 검사 대상으로 처리함. 로그인하지 않은 상태이거나 preflight에는 쿠키가 없어 `checkAuth`가 false → 인터셉터가 **401**을 직접 응답에 씀. 이때 **CORS 필터가 적용되기 전에** 401 응답이 나가거나, 에러 응답에 CORS 헤더가 붙지 않아 브라우저가 "No 'Access-Control-Allow-Origin' header"로 차단함.
- **결론**: CORS 설정(WebConfig) 자체가 잘못된 것이 아니라, **OPTIONS preflight가 인증 인터셉터에 막혀 401이 나가면서 CORS 헤더가 없는 응답**이 반환된 것이 원인.

### 조치 (본 요건 문서에 기록한 대로 적용)
- **AuthInterceptor**: `preHandle`에서 **HTTP method가 OPTIONS이면 인증 검사 없이 `return true`** 하여 preflight가 통과하도록 수정.
- **CorsFilterConfig** (추가): CORS를 **필터 체인 최상단**(`Ordered.HIGHEST_PRECEDENCE`)에 등록하여, OPTIONS preflight가 인터셉터에 닿기 전에 CORS 헤더와 200으로 응답하도록 함.
- **백엔드 재시작 필수**: 코드/설정 변경 후 반드시 백엔드를 재시작해야 적용됨. 재시작하지 않으면 이전 프로세스가 계속 동작해 CORS 오류가 그대로 날 수 있음.
- 이 조치는 “활동 이력 오늘 0건” 수정이 아니라 **CORS preflight 차단 해소**를 위한 별도 수정임.
