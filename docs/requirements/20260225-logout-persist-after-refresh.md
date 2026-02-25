# 20260225 - 로그아웃 후 새로고침 시 로그인 상태 유지 버그 수정

## 1. 사용자 요건 내용

### 요건 설명
로그아웃을 수행한 뒤 브라우저에서 **새로고침(F5)** 을 하면, 다시 **로그인된 상태**로 화면이 표시된다. 사용자는 로그아웃 후에는 새로고침을 해도 로그인 화면이 유지되어야 한다고 기대한다.

### 사용자 시나리오
1. 사용자가 로그인한 상태로 앱을 사용 중이다.
2. 사용자가 로그아웃 버튼을 눌러 로그아웃한다.
3. 로그인 화면(또는 비인증 화면)으로 전환된다.
4. **문제**: 사용자가 새로고침(F5 또는 브라우저 새로고침)을 누르면, 다시 메인 화면이 로그인된 상태로 나타난다. 로그아웃이 유지되지 않는다.
5. 사용자는 로그아웃 후에는 새로고침을 해도 비인증 상태가 유지되기를 기대한다.

### 기대 결과
- 로그아웃 후 **새로고침**을 해도 **로그인 화면(비인증 화면)** 이 유지되어야 한다.
- 서버 세션(또는 인증 쿠키)이 로그아웃 시 **무효화**되어, 이후 `auth/check` 요청이 비인증을 반환해야 한다.
- 클라이언트는 로그아웃 시 로컬 상태·저장소를 정리하고, 새로고침 시 서버 응답에만 의존해 인증 상태를 결정해야 한다.

---

## 2. 설계

### 2.1 보안 검토 (선택, 개인정보·복호화·접근통제 관련 시)
- 인증·세션 무효화 관련이므로, 구현 후 필요 시 **Security** 서브에이전트로 §2.1 보안 검토 요청 가능.
- [ ] 보안 검토 수행 여부 (해당 시 체크)

### 기술 설계

#### 문제 분석
1. **백엔드 `AuthService.logout()`**: 현재 `logout()` 메서드는 로그만 남기고 **세션을 무효화하지 않음** (`session.invalidate()` 미호출). 따라서 로그아웃 요청 후에도 서버 세션이 살아 있고, 새로고침 시 `GET /api/auth/check`에 같은 세션 쿠키가 전달되면 `checkAuth()`가 `true`를 반환한다.
2. **백엔드 `AuthController.logout()`**: `HttpServletRequest`를 사용해 현재 요청의 세션을 무효화하는 로직이 없음. `authService.logout()`만 호출하고 있다.
3. **프론트엔드 `handleLogout`**: `POST /api/auth/logout` 호출 시 **`credentials: 'include'`** 가 없을 수 있음. 있으면 쿠키가 전달되어 서버가 해당 세션을 무효화할 수 있고, 없으면 서버가 “어느 세션을 무효화할지” 알 수 없는 경우가 있다(동일 출처이면 쿠키가 갈 수 있으나, CORS/설정에 따라 일관되게 보장하려면 `credentials: 'include'` 명시가 안전함).
4. **프론트엔드 `checkAuthStatus`**: 새로고침 시 `GET /api/auth/check`를 `credentials: 'include'`로 호출하고, `result.data?.authenticated`가 true이면 로그인 상태로 복원한다. 서버가 세션을 무효화하지 않으면 로그아웃 후에도 이 값이 true로 유지된다.
5. **정리**: 근본 원인은 **서버에서 로그아웃 시 세션을 무효화하지 않는 것**이다. 부가적으로 프론트엔드 로그아웃 요청에 `credentials: 'include'`가 있으면 동일 출정·CORS 환경에서 일관된 동작을 보장하기 좋다.

#### 해결 방안

**백엔드**
- `AuthController.logout()`에서 현재 요청의 `HttpServletRequest`로 `HttpSession`을 얻고, **`session.invalidate()`** 를 호출하여 로그아웃 시 해당 세션을 무효화한다.  
  - 또는 `AuthService.logout(HttpServletRequest request)` 등으로 요청을 넘기고, 서비스에서 `request.getSession(false)`로 세션을 얻은 뒤 `invalidate()` 호출.
- 로그아웃 후 동일 쿠키로 `auth/check`가 호출되면 세션이 없거나 무효화된 상태이므로 `checkAuth()`가 false를 반환하도록 유지한다(현재 `getSession(false)` 로직으로 충족 가능).

**프론트엔드**
- `App.js`의 `handleLogout`에서 `fetch(..., { method: 'POST', credentials: 'include', ... })` 로 **`credentials: 'include'`** 를 명시하여, 로그아웃 요청에 세션 쿠키가 포함되도록 한다. (이미 포함되어 있을 수 있으나, 계약상·CORS 환경에서 명시 권장.)
- 로그아웃 후 `clearUserData()`, `setUser(null)`, `setIsAuthenticated(false)` 등 기존 클라이언트 정리 로직은 유지한다. 새로고침 시에는 `checkAuthStatus()` 결과만으로 인증 여부를 결정하면 된다.

### 변경 파일 목록 (예상)

#### 백엔드
- `backend/src/main/java/com/logmng/controller/AuthController.java`
  - `logout()` 메서드에서 `HttpServletRequest`로 현재 세션을 얻고 `session.invalidate()` 호출. 또는
- `backend/src/main/java/com/logmng/service/AuthService.java`
  - `logout(HttpServletRequest request)` 등으로 요청을 받아, `request.getSession(false)` 후 `invalidate()` 수행.

#### 프론트엔드
- `frontend/src/App.js`
  - `handleLogout` 내 `fetch('/api/auth/logout', ...)` 호출에 **`credentials: 'include'`** 추가.

### 데이터베이스 변경사항
- 해당 없음 (세션은 서버 메모리 또는 세션 스토어 기준).

---

## 3. 테스트 수행 방안

### 테스트 케이스 목록 (요건 기준, 필수)

| ID | 구분 | 시나리오(입력·조건) | 기대 결과 | 검증 방법(단위/통합/수동) |
|----|------|----------------------|-----------|---------------------------|
| TC-01 | 정상 | 로그인 후 로그아웃 클릭 → 로그인 화면 표시 → 브라우저 새로고침(F5) | 새로고침 후에도 로그인 화면(비인증) 유지, 메인 화면으로 복귀하지 않음 | 수동(브라우저) |
| TC-02 | 정상 | 로그아웃 요청 시 서버에서 해당 세션 무효화 | 동일 브라우저에서 이후 `GET /api/auth/check` 호출 시 `authenticated: false` 반환 | 통합(curl 또는 수동) |
| TC-03 | 정상 | 로그아웃 API 호출 시 `credentials: 'include'`로 쿠키 전달 | 서버가 해당 클라이언트의 세션을 식별해 무효화 가능 | 코드 검토 또는 통합 |
| TC-04 | 자동 | `cd backend && mvn test` | 기존 단위 테스트 통과 | 단위 테스트 |
| TC-05 | 자동 | `cd frontend && npm test -- --watchAll=false` (해당 시) | 기존 단위 테스트 통과 | 단위 테스트 |

### 테스트 시나리오

#### 시나리오 1: 로그아웃 후 새로고침
1. 로그인한 상태에서 로그아웃 버튼 클릭.
2. 로그인 화면이 표시되는지 확인.
3. 브라우저 새로고침(F5) 수행.
4. **검증**: 로그인 화면이 유지되고, 메인(사이드바/앱) 화면이 보이지 않아야 함.

#### 시나리오 2: 세션 무효화 확인
1. 로그인 후 쿠키(세션 ID) 확인.
2. 로그아웃 API 호출 (`POST /api/auth/logout`, credentials 포함).
3. 같은 브라우저/쿠키로 `GET /api/auth/check` 호출.
4. **검증**: 응답에 `authenticated: false` (또는 동등한 비인증 응답)가 와야 함.

### 테스트 환경
- 프론트엔드: `http://localhost:3001`
- 백엔드: `http://localhost:9200/api`
- 데이터베이스: 프로젝트 contract 기준

---

## 4. 체크리스트

- [ ] 요건·설계 반영
- [ ] §3 테스트 케이스 수립
- [ ] 코드 변경 (Backend: 세션 무효화, Frontend: credentials: 'include')
- [ ] §5 테스트 결과 기록
- [ ] 검증(재시작·헬스체크) 통과
- [ ] §6 오류 조치 결과 기록 (버그 수정 요건)
- [ ] 커밋 메시지에 요건 ID 포함

---

## 5. 테스트 결과

(구현·검증 후 기록)

### 테스트 수행 일시
- [날짜 시간]

### 테스트 결과
- TC-01 ~ TC-03: (수동/통합 결과)
- TC-04, TC-05: (단위 테스트 결과)

---

## 6. 오류 조치 결과 (원인·조치) — 오류/버그 수정 요건

조치가 끝난 뒤 **동일 요구사항 ID(본 문서)** 에 맞춰 기록.

- **요구사항 ID**: 20260225-logout-persist-after-refresh
- **원인 (Root Cause)**: (구현 검증 후 작성)
- **조치 내용 (Actions Taken)**: (구현 검증 후 작성)
- **조치 결과 (Result)**: (구현 검증 후 작성)
- **완료 일시**: yyyy-MM-dd HH:mm

---

## 서브에이전트 위임 (Handoff)

- **Security** (선택): 인증·세션 무효화 설계에 대해 §2.1 보안 검토가 필요하면 이 문서 §1·§2를 입력으로 전달.
- **Backend**: 이 문서 + §3 테스트 케이스를 입력으로, `AuthController` 또는 `AuthService`에서 로그아웃 시 **세션 무효화** 구현 후 빌드·재시작·QA 위임.
- **Frontend**: 이 문서 + §3를 입력으로, `App.js`의 `handleLogout`에서 **`credentials: 'include'`** 추가 후 빌드·재시작·QA 위임.
- **QA**: Backend/Frontend 구현 및 빌드·재시작 완료 후, verify 체크리스트 및 §5·§6 갱신, 필요 시 커밋 수행.
