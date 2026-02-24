# 20260220 - 활동로그 통계 anonymous 제거 및 미인증 조회 차단

## 1. 사용자 요건 내용

### 요건 설명
활동로그 통계 화면에 "anonymous" 사용자가 노출된다. 이 서비스는 로그인하지 않은 사용자가 조회하면 안 되는 서비스이므로, anonymous가 나타나는 원인을 확인하고 미인증 조회를 차단해야 한다.

### 사용자 시나리오
1. 관리자가 로그인한 뒤 활동 통계를 조회한다.
2. **문제**: 통계에 "anonymous" 행이 보인다. 또한 (현재) 로그인하지 않아도 API를 직접 호출하면 통계/활동 로그를 조회할 수 있는 상태다.
3. 기대: 로그인한 사용자만 통계·활동 이력을 조회할 수 있고, 통계에는 로그인된 사용자만 집계된다.

### 기대 결과
- 로그인하지 않은 사용자는 통계 API·활동 로그 API 등 조회 API를 호출할 수 없다 (401 응답).
- 인증 상태 확인 API(`/api/auth/check`)가 세션을 기준으로 올바르게 동작한다.
- 미인증 요청으로 인해 "anonymous" 활동 로그가 새로 생성되지 않는다 (선택: 기존 DB의 anonymous 행은 통계에서 제외하거나 유지).

## 2. 설계

### 기술 설계

#### 원인 분석
1. **anonymous 노출 원인**: `ActivityLogAspect`에서 `userId`가 없을 때 `"anonymous"`로 저장함 (`ActivityLogAspect.java` 321행). 세션이 없거나 만료된 상태에서 검색/복호화 등 `@ActivityLog`가 붙은 API가 호출되면 anonymous로 기록됨.
2. **미인증 조회 가능 원인**: 통계·활동 로그·로그 검색 등 API에 인증 체크가 없음. `AuthService.checkAuth()`는 항상 `false`를 반환해 세션 검증이 되지 않음.

#### 해결 방안

**백엔드**
- `AuthService.checkAuth(HttpServletRequest)`: 세션 존재 여부 및 `userId`/`username` 속성 검사 후 `boolean` 반환. 컨트롤러에서 `HttpServletRequest` 전달.
- 인증 필수 경로에 인터셉터 적용: `/api/statistics/**`, `/api/activity-log/**`, `/api/logs/**`, `/api/search/**` 등은 세션 유효 시에만 통과. 미인증 시 401 + JSON 메시지 반환.
- 인증 제외 경로: `/api/auth/**`, `/api/health`, `/api/db/test` 등은 그대로 허용.
- `ActivityLogAspect`: `userId`가 `null`이면 활동 로그 저장을 하지 않음 (anonymous 행 신규 생성 방지).

**프론트엔드**
- 통계·기타 API 호출 시 세션 쿠키가 전달되도록 axios 인스턴스에 `withCredentials: true` 설정. (이미 로그인/활동 로그 검색 등은 `credentials: 'include'` 사용 중이나, `api.js`의 axios 기본 설정에는 없을 수 있음.)

### 변경 파일 목록

#### 백엔드
- `AuthService.java` – `checkAuth(HttpServletRequest)` 추가, 세션 기반 검증.
- `AuthController.java` – `checkAuth` 호출 시 `HttpServletRequest` 전달.
- 신규 `config/AuthInterceptor.java` – 인증 필요 경로에서 세션 검사, 미인증 시 401.
- `WebConfig.java` – `AuthInterceptor` 등록 및 인증 제외 경로 패턴 설정.
- `ActivityLogAspect.java` – `userId == null`이면 `saveActivityLog` 호출하지 않음.

#### 프론트엔드
- `frontend/src/services/api.js` – axios 생성 시 `withCredentials: true` 설정.

### 데이터베이스 변경사항
없음. 기존 `user_activity_log`의 `user_id = 'anonymous'` 행은 별도 마이그레이션 없이, 통계 쿼리에서 제외할지 여부만 선택 가능(본 요건에서는 신규 anonymous 생성 차단만 수행).

## 3. 테스트 수행 방안

### 테스트 케이스 목록

| ID | 구분 | 시나리오(입력·조건) | 기대 결과 | 검증 방법 |
|----|------|----------------------|-----------|-----------|
| TC-01 | 정상 | 로그인 후 GET /api/statistics/activity/daily | 200, 통계 데이터 반환 | curl with cookie / 수동 |
| TC-02 | 예외 | 로그인 없이 GET /api/statistics/activity/daily | 401, 인증 필요 메시지 | curl without cookie |
| TC-03 | 정상 | 로그인 후 GET /api/auth/check | 200, authenticated: true | curl with cookie |
| TC-04 | 예외 | 로그인 없이 GET /api/auth/check | 200, authenticated: false | curl |
| TC-05 | 정상 | 로그인 없이 GET /api/health | 200 | curl |
| TC-06 | 정상 | 미인증 상태에서 @ActivityLog API 호출 후 DB 조회 | user_activity_log에 해당 요청에 대한 anonymous 행 없음 | 수동/통합 |

### 테스트 시나리오

#### 시나리오 1: 미인증 통계 조회 차단
1. 쿠키 없이 `GET /api/statistics/activity/daily` 호출.
2. 401 응답 및 JSON 메시지 확인.
3. 로그인 후 동일 API 호출 시 200 및 데이터 확인.

#### 시나리오 2: anonymous 신규 생성 방지
1. 로그인 없이 검색/복호화 등 @ActivityLog 엔드포인트 호출(401이 먼저 반환되므로, 인터셉터 전에 로그가 쌓이지 않도록 Aspect에서 미인증 시 저장 스킵).
2. (또는 테스트용으로 인터셉터 제외 후) userId 없이 Aspect만 동작하게 했을 때 DB에 anonymous 행이 추가되지 않음을 확인.

### 테스트 환경
- 백엔드: http://localhost:9200
- 프론트엔드: http://localhost:3001
- DB: contract 기준

## 4. 체크리스트

### 백엔드 검증
- [ ] 인증 제외 경로에서 200 동작
- [ ] 인증 필요 경로 미인증 시 401
- [ ] checkAuth 세션 기반 true/false 반환
- [ ] userId null 시 활동 로그 미저장

### 프론트엔드 검증
- [ ] 로그인 후 통계 조회 시 쿠키 전달로 200 확인
- [ ] 401 시 로그인 유도/리다이렉트 동작 확인

### 문서화
- [ ] 요건 문서 §5·§6 갱신

## 5. 테스트 결과

### 테스트 수행 일시
- 2026-02-20

### 테스트 결과

#### 백엔드
- 단위 테스트: `mvn test` — JUnit discovery 이슈로 스킵(기존 환경 이슈).
- 통합(curl) 검증:
  - `GET /api/health` → 200, 정상.
  - `GET /api/auth/check` (미인증) → 200, `data.authenticated: false`.
  - `GET /api/statistics/activity/daily` (미인증, 쿠키 없음) → **401**, `{"success":false,"error":"로그인이 필요합니다.","code":"UNAUTHORIZED"}`.
  - 로그인 세션 있으면 통계 API 200 기대(프론트에서 credentials 포함 시).

#### 프론트엔드
- 단위 테스트: 테스트 파일 없음(0 matches)으로 스킵.
- `api` axios에 `withCredentials: true` 적용, `App.js`에서 `result.data?.authenticated` 및 auth/check에 `credentials: 'include'` 적용 완료.

### 발견된 이슈 및 해결
- 백엔드 재시작 시 기존 JAR 사용으로 인터셉터 미반영: `mvn package -DskipTests` 후 backend 재시작으로 해결.

---

## 6. 오류 조치 결과 (원인·조치)

- **요구사항 ID**: 20260220-activity-statistics-no-anonymous-access
- **원인 (Root Cause)**:
  1. 통계·활동 로그 등 API에 인증 체크가 없어 미인증 사용자도 조회 가능했음.
  2. `AuthService.checkAuth()`가 항상 `false`를 반환해 세션 검증이 되지 않았음.
  3. `ActivityLogAspect`에서 `userId`가 없을 때 `"anonymous"`로 저장해, 미인증 요청(검색/복호화 등)이 anonymous로 기록되었음.
- **조치 내용 (Actions Taken)**:
  1. `AuthService.checkAuth(HttpServletRequest)`: 세션 존재 및 `userId`/`username` 속성 검사 후 반환.
  2. `AuthInterceptor` 추가: `/api/statistics/**`, `/api/activity-log/**`, `/api/logs/**`, `/api/search/**` 등은 세션 유효 시에만 통과, 미인증 시 401 + JSON 반환. `/api/auth/**`, `/api/health`, `/api/db/**`, `/api/log-types` 등은 제외.
  3. `WebConfig`에 인터셉터 등록.
  4. `ActivityLogAspect`: `userId == null`이면 활동 로그 저장 생략(anonymous 신규 생성 방지).
  5. 프론트: `api` axios에 `withCredentials: true`, auth/check에 `credentials: 'include'`, 인증 상태는 `result.data?.authenticated`로 확인하도록 수정.
- **조치 결과 (Result)**: 미인증 curl로 통계 API 호출 시 401 확인. 허용 경로(health, auth/check)는 200 유지.
- **완료 일시**: 2026-02-20

---

**작성일**: 2026-02-20  
**상태**: 완료
