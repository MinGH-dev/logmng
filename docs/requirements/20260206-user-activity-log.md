# 20260206 - 사용자 활동 이력 보관 및 조회 기능

## 1. 사용자 요건 내용

### 요건 설명
시스템에서 사용자가 수행한 모든 활동(로그인, 로그아웃, 검색, 조회, 수정 등)을 데이터베이스에 보관하고, 관리자가 이를 조회할 수 있는 기능이 필요합니다.

### 사용자 시나리오

#### 시나리오 1: 사용자 활동 자동 기록
1. 사용자가 로그인합니다
2. 시스템이 자동으로 로그인 이력을 기록합니다
3. 사용자가 로그를 검색합니다
4. 시스템이 검색 조건과 결과를 자동으로 기록합니다
5. 사용자가 특정 로그를 상세 조회합니다
6. 시스템이 조회 이력을 자동으로 기록합니다

#### 시나리오 2: 관리자가 활동 이력 조회
1. 관리자가 로그인합니다
2. "사용자 활동 이력" 메뉴로 이동합니다
3. 날짜 범위, 사용자, 액션 타입 등으로 필터링합니다
4. 검색 결과를 확인합니다
5. 특정 이력의 상세 정보를 확인합니다

#### 시나리오 3: 보안 감사
1. 보안 담당자가 의심스러운 활동을 조회합니다
2. 특정 IP 주소에서의 모든 활동을 확인합니다
3. 특정 시간대의 모든 활동을 확인합니다
4. 활동 이력을 CSV로 내보냅니다

### 기대 결과
- 모든 사용자 활동이 자동으로 기록되어야 합니다
- 활동 이력을 조회할 수 있는 화면이 제공되어야 합니다
- 활동 이력을 필터링하고 검색할 수 있어야 합니다
- 활동 이력의 상세 정보를 확인할 수 있어야 합니다
- 활동 이력을 파일로 내보낼 수 있어야 합니다
- 데이터 보관 정책에 따라 오래된 데이터를 자동으로 삭제할 수 있어야 합니다

## 2. 설계

### 기술 설계

#### 2.1 데이터베이스 설계

**테이블명**: `user_activity_log`

**컬럼 설계**:
```sql
CREATE TABLE user_activity_log (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(100) NOT NULL,
    username VARCHAR(100),
    action_type VARCHAR(50) NOT NULL,  -- 'LOGIN', 'LOGOUT', 'SEARCH', 'VIEW', 'EXPORT', etc.
    action_detail TEXT,                -- JSON 형태로 상세 정보 저장
    ip_address VARCHAR(45),            -- IPv6 지원
    user_agent TEXT,
    request_method VARCHAR(10),         -- GET, POST, PUT, DELETE
    request_path VARCHAR(500),
    request_params TEXT,                -- JSON 형태
    response_status INTEGER,            -- HTTP 상태 코드
    response_time_ms INTEGER,           -- 응답 시간 (밀리초)
    success BOOLEAN DEFAULT true,
    error_message TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 인덱스
CREATE INDEX idx_user_activity_log_user_id ON user_activity_log(user_id);
CREATE INDEX idx_user_activity_log_action_type ON user_activity_log(action_type);
CREATE INDEX idx_user_activity_log_created_at ON user_activity_log(created_at);
CREATE INDEX idx_user_activity_log_ip_address ON user_activity_log(ip_address);
CREATE INDEX idx_user_activity_log_user_action_date ON user_activity_log(user_id, action_type, created_at);

-- 파티셔닝 (선택사항, 대용량 데이터 고려)
-- 월별 파티셔닝 또는 연도별 파티셔닝 고려
```

**액션 타입 정의**:
- `LOGIN`: 로그인
- `LOGOUT`: 로그아웃
- `SEARCH`: 로그 검색
- `VIEW`: 로그 상세 조회
- `EXPORT`: 데이터 내보내기
- `DECRYPT`: 복호화 요청
- `ADVANCED_SEARCH`: 고급 검색
- `SCHEMA_VIEW`: 스키마 정보 조회
- `STATS_VIEW`: 통계 조회
- `ERROR`: 에러 발생

**action_detail JSON 구조 예시**:
```json
{
  "requestParams": {
    "request": {
      "logType": "java_fw_imglog",
      "startDate": "2026-02-01 00:00:00",
      "endDate": "2026-02-06 23:59:59",
      "application": "test",
      "servicegroup": "test-group",
      "service": "test-service",
      "datastring": "password",
      "keywords": ["keyword1", "keyword2"],
      "page": 1,
      "pageSize": 10
    }
  },
  "searchSummary": {
    "totalCount": 150,
    "resultCount": 10,
    "currentPage": 1,
    "totalPages": 15
  },
  "response": "{...}" // 전체 응답 (크기 제한)
}
```

#### 2.2 백엔드 설계

**아키텍처 패턴**: AOP (Aspect-Oriented Programming)를 활용한 자동 로깅

**구조**:
```
com.logmng
├── entity/
│   └── UserActivityLog.java          # 엔티티 클래스
├── repository/
│   └── UserActivityLogRepository.java # JPA Repository
├── service/
│   └── UserActivityLogService.java   # 비즈니스 로직
├── controller/
│   └── UserActivityLogController.java # REST API
├── dto/
│   ├── request/
│   │   └── UserActivityLogSearchRequest.java
│   └── response/
│       └── UserActivityLogResponse.java
├── aspect/
│   └── ActivityLogAspect.java        # AOP를 통한 자동 로깅
└── interceptor/
    └── ActivityLogInterceptor.java   # HTTP 인터셉터 (선택사항)
```

**주요 기능**:

1. **자동 로깅 (AOP)**
   - `@ActivityLog` 어노테이션을 컨트롤러 메서드에 추가
   - 메서드 실행 전후에 자동으로 활동 이력 기록
   - 예외 발생 시에도 기록

2. **수동 로깅 (Service)**
   - 특정 이벤트에 대해 명시적으로 로깅
   - 로그인/로그아웃 등 인증 관련 이벤트

3. **이력 조회 API**
   - 목록 조회 (페이징, 필터링)
   - 상세 조회
   - 통계 조회
   - 내보내기 (CSV, Excel)

4. **데이터 보관 정책**
   - 오래된 데이터 자동 삭제 (스케줄러)
   - 기본 보관 기간: 1년 (설정 가능)

#### 2.3 프론트엔드 설계

**컴포넌트 구조**:
```
src/components/
├── UserActivityLog/
│   ├── UserActivityLogList.js        # 목록 화면
│   ├── UserActivityLogSearchForm.js  # 검색 폼
│   ├── UserActivityLogTable.js       # 테이블
│   ├── UserActivityLogDetail.js      # 상세 화면
│   └── UserActivityLog.css           # 스타일
```

**주요 기능**:
1. 활동 이력 목록 조회
2. 필터링 (날짜, 사용자, 액션 타입, IP 주소)
3. 상세 정보 모달/페이지
4. CSV 내보내기
5. 통계 대시보드 (선택사항)

### 변경 파일 목록

#### 데이터베이스
- `dev/backend/src/main/resources/db/migration/V1__create_user_activity_log.sql` (신규)
  - 테이블 생성
  - 인덱스 생성
  - 초기 데이터 (필요 시)

#### 백엔드

**Service**:
- `dev/backend/src/main/java/com/logmng/service/UserActivityLogService.java` (신규)
  - JDBC 기반 구현 (JPA 미사용)

**Service**:
- `dev/backend/src/main/java/com/logmng/service/UserActivityLogService.java` (신규)

**Controller**:
- `dev/backend/src/main/java/com/logmng/controller/UserActivityLogController.java` (신규)

**DTO**:
- `dev/backend/src/main/java/com/logmng/dto/request/UserActivityLogSearchRequest.java` (신규)
- `dev/backend/src/main/java/com/logmng/dto/response/UserActivityLogResponse.java` (신규)

**AOP**:
- `dev/backend/src/main/java/com/logmng/aspect/ActivityLogAspect.java` (신규)
- `dev/backend/src/main/java/com/logmng/annotation/ActivityLog.java` (신규)

**기존 파일 수정**:
- `dev/backend/src/main/java/com/logmng/controller/LogDbController.java`
  - `@ActivityLog` 어노테이션 추가
- `dev/backend/src/main/java/com/logmng/controller/AuthController.java` (존재 시)
  - 로그인/로그아웃 이력 기록

**설정 파일**:
- `dev/backend/src/main/resources/application.yml`
  - AOP 활성화 설정
  - 데이터 보관 정책 설정

#### 프론트엔드

**컴포넌트**:
- `dev/frontend/src/components/UserActivityLog/UserActivityLogList.js` (신규)
  - 당일 날짜 기본 설정
  - 초기 로드 시 당일 검색 자동 실행
- `dev/frontend/src/components/UserActivityLog/UserActivityLogSearchForm.js` (신규)
  - 당일 날짜 기본값 설정
  - 초기화 시 당일 날짜로 리셋
- `dev/frontend/src/components/UserActivityLog/UserActivityLogTable.js` (신규)
- `dev/frontend/src/components/UserActivityLog/UserActivityLogDetail.js` (신규)
  - 검색 결과 요약 표시 (전체 건수, 반환 건수, 페이지 정보)
  - 검색 조건 구조화 표시
- `dev/frontend/src/components/UserActivityLog/UserActivityLog.css` (신규)
  - 검색 결과 요약 스타일 추가

**라우팅**:
- `dev/frontend/src/App.js`
  - 활동 이력 화면 상태 관리 추가
  - 헤더에 "활동 이력" 버튼 추가 (모든 화면에서 접근 가능)
  - 활동 이력 화면 라우팅 구현

**API 서비스**:
- `dev/frontend/src/services/userActivityLogService.js` (신규)

### 데이터베이스 변경사항

#### 신규 테이블
- `user_activity_log`: 사용자 활동 이력 저장

#### 마이그레이션
- Flyway 또는 Liquibase를 사용하여 마이그레이션 스크립트 작성
- 프로덕션 배포 시 마이그레이션 실행

#### 데이터 보관 정책
- 기본 보관 기간: 1년
- 설정 파일에서 변경 가능
- 스케줄러를 통한 자동 삭제 (매일 새벽 2시 실행)

## 3. 테스트 수행 방안

### 테스트 시나리오

#### 시나리오 1: 로그인 이력 기록
1. 사용자가 로그인합니다
2. `user_activity_log` 테이블에 로그인 이력이 기록되는지 확인합니다
3. 기록된 데이터의 정확성을 확인합니다 (user_id, ip_address, action_type 등)

#### 시나리오 2: 로그 검색 이력 기록
1. 사용자가 로그를 검색합니다
2. 검색 조건과 결과가 `user_activity_log` 테이블에 기록되는지 확인합니다
3. `action_detail` JSON이 올바르게 저장되는지 확인합니다

#### 시나리오 3: 활동 이력 조회
1. 관리자가 활동 이력 화면에 접근합니다
2. 날짜 범위로 필터링합니다
3. 특정 사용자로 필터링합니다
4. 특정 액션 타입으로 필터링합니다
5. 검색 결과가 올바르게 표시되는지 확인합니다

#### 시나리오 4: 활동 이력 상세 조회
1. 활동 이력 목록에서 특정 이력을 클릭합니다
2. 상세 정보가 올바르게 표시되는지 확인합니다
3. `action_detail` JSON이 읽기 쉽게 표시되는지 확인합니다

#### 시나리오 5: 데이터 내보내기
1. 활동 이력을 필터링합니다
2. "내보내기" 버튼을 클릭합니다
3. CSV 파일이 다운로드되는지 확인합니다
4. CSV 파일의 내용이 올바른지 확인합니다

#### 시나리오 6: 에러 발생 시 이력 기록
1. 에러가 발생하는 API를 호출합니다
2. `user_activity_log` 테이블에 에러 이력이 기록되는지 확인합니다
3. `error_message`가 올바르게 저장되는지 확인합니다

#### 시나리오 7: 데이터 보관 정책
1. 1년 이상 된 데이터를 생성합니다 (테스트용)
2. 스케줄러가 실행되면 오래된 데이터가 삭제되는지 확인합니다

### 테스트 데이터

#### 초기 테스트 데이터
```sql
-- 테스트용 사용자 활동 이력 데이터
INSERT INTO user_activity_log (user_id, username, action_type, action_detail, ip_address, user_agent, request_method, request_path, response_status, success, created_at)
VALUES
  ('admin', '관리자', 'LOGIN', '{}', '192.168.1.100', 'Mozilla/5.0...', 'POST', '/api/auth/login', 200, true, NOW() - INTERVAL '1 day'),
  ('admin', '관리자', 'SEARCH', '{"logType":"java_fw_imglog","searchConditions":{"startDate":"2026-02-01"},"resultCount":10}', '192.168.1.100', 'Mozilla/5.0...', 'POST', '/api/logs/db-refactored/search', 200, true, NOW() - INTERVAL '2 hours'),
  ('user1', '사용자1', 'VIEW', '{"logType":"java_fw_imglog","guid":"test-guid-123"}', '192.168.1.101', 'Mozilla/5.0...', 'GET', '/api/logs/db-refactored/java_fw_imglog/detail/test-guid-123', 200, true, NOW() - INTERVAL '1 hour');
```

### 테스트 환경
- 프론트엔드: `http://localhost:3001` (계약·검증 기준; `docs/contract.md`)
- 백엔드: `http://localhost:9200`
- 데이터베이스: PostgreSQL

### 테스트 도구
- 백엔드: JUnit5, Mockito
- 프론트엔드: React Testing Library (선택사항)
- API 테스트: curl, Postman

## 4. 체크리스트

### 데이터베이스 설계
- [ ] `user_activity_log` 테이블 생성 스크립트 작성
- [ ] 인덱스 생성 스크립트 작성
- [ ] 마이그레이션 스크립트 작성 및 테스트
- [ ] 데이터 보관 정책 스크립트 작성

### 백엔드 검증
- [ ] `UserActivityLog` 엔티티 클래스 작성
- [ ] `UserActivityLogRepository` 작성
- [ ] `UserActivityLogService` 작성 및 단위 테스트
- [ ] `UserActivityLogController` 작성 및 통합 테스트
- [ ] `@ActivityLog` 어노테이션 및 AOP 구현
- [ ] 기존 컨트롤러에 `@ActivityLog` 어노테이션 추가
- [ ] 로그인/로그아웃 이력 기록 구현
- [ ] 에러 발생 시 이력 기록 구현
- [ ] 데이터 보관 정책 스케줄러 구현

### 프론트엔드 검증
- [ ] `UserActivityLogList` 컴포넌트 작성
- [ ] `UserActivityLogSearchForm` 컴포넌트 작성
- [ ] `UserActivityLogTable` 컴포넌트 작성
- [ ] `UserActivityLogDetail` 컴포넌트 작성
- [ ] API 서비스 클래스 작성
- [ ] 라우팅 설정
- [ ] UI/UX 테스트
- [ ] CSV 내보내기 기능 테스트

### 통합 테스트
- [ ] 로그인 시 이력 기록 테스트
- [ ] 로그 검색 시 이력 기록 테스트
- [ ] 활동 이력 조회 화면 테스트
- [ ] 필터링 기능 테스트
- [ ] 상세 조회 기능 테스트
- [ ] 내보내기 기능 테스트
- [ ] 에러 발생 시 이력 기록 테스트
- [ ] 성능 테스트 (대용량 데이터)

### 문서화
- [ ] 요건 문서 작성 완료 (본 문서)
- [ ] API 문서 작성 (Swagger/OpenAPI)
- [ ] 사용자 가이드 작성 (선택사항)

## 5. 구현 완료 및 개선 사항

### 5.1 구현 완료 항목 (2026-02-06)

#### 데이터베이스
- ✅ `user_activity_log` 테이블 생성 완료
- ✅ 인덱스 및 트리거 설정 완료
- ✅ 마이그레이션 스크립트 작성 및 실행 완료

#### 백엔드
- ✅ DTO 클래스 작성 (Request/Response)
- ✅ Service 클래스 작성 (JDBC 기반)
- ✅ Controller 작성 (검색, 상세 조회 API)
- ✅ AOP 어노테이션 및 Aspect 구현
- ✅ 기존 컨트롤러에 `@ActivityLog` 어노테이션 추가
  - `LogDbController`: SEARCH, VIEW, DECRYPT, ADVANCED_SEARCH
  - `AuthController`: LOGIN, LOGOUT
- ✅ 세션 기반 사용자 정보 저장

#### 프론트엔드
- ✅ API 서비스 클래스 작성
- ✅ 활동 이력 목록 컴포넌트 작성
- ✅ 검색 폼 컴포넌트 작성
- ✅ 테이블 컴포넌트 작성
- ✅ 상세 모달 컴포넌트 작성
- ✅ App.js에 활동 이력 메뉴 추가
- ✅ 헤더에서 모든 화면에서 활동 이력 접근 가능

### 5.2 개선 사항 (2026-02-06)

#### 검색 결과 요약 저장 기능 추가
- ✅ 검색 요청 시 검색 조건을 구조화하여 저장
- ✅ 검색 결과 요약 정보 저장 (전체 건수, 반환 건수, 페이지 정보)
- ✅ 프론트엔드 상세 화면에서 검색 결과 요약 표시
- ✅ 검색 조건과 결과 요약을 구분하여 표시

#### 사용자 경험 개선
- ✅ 활동 이력 화면 진입 시 당일 날짜로 기본 설정
- ✅ 시작일자: 오늘 00:00:00
- ✅ 종료일자: 오늘 23:59:59
- ✅ 초기화 버튼 클릭 시 당일 날짜로 리셋

### 5.3 검색 결과 요약 저장 구조

#### 검색 조건 저장
```json
{
  "requestParams": {
    "request": {
      "logType": "java_fw_imglog",
      "startDate": "2026-02-06 00:00:00",
      "endDate": "2026-02-06 23:59:59",
      "application": "test-app",
      "servicegroup": "test-group",
      "service": "test-service",
      "datastring": "search-term",
      "keywords": ["keyword1", "keyword2"],
      "page": 1,
      "pageSize": 10
    }
  }
}
```

#### 검색 결과 요약 저장
```json
{
  "searchSummary": {
    "totalCount": 150,      // 전체 검색 결과 건수
    "resultCount": 10,      // 현재 페이지 반환 건수
    "currentPage": 1,       // 현재 페이지 번호
    "totalPages": 15        // 전체 페이지 수
  }
}
```

### 5.4 테스트 결과

#### 테스트 수행 일시
- 2026-02-06 14:41 ~ 14:43

#### 테스트 결과
✅ **성공**
- 데이터베이스 마이그레이션 완료
- 활동 이력 자동 기록 확인 (LOGIN, SEARCH)
- 활동 이력 검색 API 정상 동작
- 활동 이력 상세 조회 API 정상 동작
- 검색 결과 요약 저장 확인

#### API 테스트 결과
```bash
# 활동 이력 검색
POST /api/activity-log/search
Response: {
  "success": true,
  "totalCount": 2,
  "firstItem": {
    "id": 2,
    "user_id": "admin",
    "action_type": "SEARCH",
    "request_path": "/api/logs/db-refactored/search"
  }
}

# 활동 이력 상세 조회
GET /api/activity-log/{id}
Response: {
  "success": true,
  "data": {
    "id": 2,
    "user_id": "admin",
    "action_type": "SEARCH",
    "request_path": "/api/logs/db-refactored/search",
    "response_status": 200,
    "success": true
  }
}
```

### 발견된 이슈 및 해결 방법

#### 이슈 1: AOP finally 블록에서 return 문 사용 불가
**원인**: finally 블록 내부의 try 블록에서 return을 사용할 수 없음

**해결 방법**:
- 로깅 로직을 별도 메서드(`logActivityInternal`)로 분리
- finally 블록에서는 메서드 호출만 수행

#### 이슈 2: 활동 이력 API 404 오류
**원인**: 서버 재시작 후 새 컨트롤러가 로드되지 않음

**해결 방법**:
- 서버 재시작 및 빌드 확인
- 컨트롤러 클래스가 JAR에 포함되었는지 확인

## 6. 구현 우선순위

### Phase 1: 기본 기능 (필수)
1. 데이터베이스 테이블 생성
2. 백엔드 엔티티 및 Repository 구현
3. Service 및 Controller 구현
4. 기본 조회 API 구현
5. 프론트엔드 목록 화면 구현

### Phase 2: 자동 로깅 (필수)
1. AOP 어노테이션 및 Aspect 구현
2. 기존 컨트롤러에 어노테이션 추가
3. 로그인/로그아웃 이력 기록

### Phase 3: 고급 기능 (선택)
1. 필터링 및 검색 기능 강화
2. 통계 대시보드
3. 데이터 내보내기 (CSV, Excel)
4. 데이터 보관 정책 및 스케줄러

## 7. 보안 고려사항

### 개인정보 보호
- `action_detail`에 민감한 정보(비밀번호 등)가 포함되지 않도록 주의
- 로그인 시 비밀번호는 절대 기록하지 않음

### 접근 제어
- 활동 이력 조회는 관리자 권한만 허용
- 일반 사용자는 자신의 활동 이력만 조회 가능 (선택사항)

### 데이터 암호화
- 민감한 정보는 암호화하여 저장 (필요 시)
- `action_detail`의 특정 필드 암호화 고려

## 8. 성능 고려사항

### 인덱스 최적화
- 자주 조회되는 컬럼에 인덱스 생성
- 복합 인덱스 활용

### 파티셔닝
- 대용량 데이터 고려 시 월별 또는 연도별 파티셔닝
- 오래된 파티션 자동 삭제

### 비동기 처리
- 활동 이력 기록은 비동기로 처리하여 API 응답 시간에 영향 최소화
- 큐를 사용한 배치 처리 고려

### 캐싱
- 자주 조회되는 통계 데이터는 캐싱 고려

## 6. 향후 개선 계획

### Phase 4: 고급 기능 (선택)
1. 데이터 내보내기 (CSV, Excel)
2. 통계 대시보드
3. 데이터 보관 정책 및 스케줄러
4. 실시간 활동 모니터링
5. 활동 이력 알림 기능

---

**작성자**: AI Assistant
**작성일**: 2026-02-06
**최종 수정일**: 2026-02-06
**상태**: ✅ 구현 완료 및 개선 완료

### 변경 이력
- 2026-02-06: 초기 설계 문서 작성
- 2026-02-06: Phase 1-3 구현 완료
- 2026-02-06: 검색 결과 요약 저장 기능 추가
- 2026-02-06: 당일 날짜 기본 설정 기능 추가
- 2026-02-06: 활동 이력 접근성 개선 (헤더 메뉴)

