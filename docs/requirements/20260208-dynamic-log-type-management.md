# 20260208 - 동적 로그 타입 관리 기능

## 1. 사용자 요건 내용

### 요건 설명
로그 타입은 관리자에 의해 계속 추가되거나 삭제될 수 있어, 확장성을 고려한 설계가 필요합니다. 현재 하드코딩된 로그 타입(LOGIN, pb_feplog, java_fw_imglog)을 동적으로 관리할 수 있도록 변경해야 합니다.

### 사용자 시나리오
1. 관리자가 새로운 로그 타입을 추가할 수 있어야 함
2. 관리자가 기존 로그 타입을 삭제할 수 있어야 함
3. 프론트엔드에서 로그 타입 목록을 동적으로 조회하여 표시
4. 통계 화면에서 로그 타입별 통계를 동적으로 표시
5. 사용자별 통계 테이블에서 로그 타입별 검색/복호화 건수를 동적으로 표시

### 기대 결과
- 로그 타입이 추가/삭제되어도 코드 수정 없이 동작
- 통계 화면이 동적으로 로그 타입에 맞춰 표시
- 확장 가능한 아키텍처

## 2. 설계

### 기술 설계

#### 2.1 백엔드 설계

**2.1.1 로그 타입 관리 API**
- **위치**: `backend/src/controllers/logTypeController.js` (신규)
- **기능**:
  - 로그 타입 목록 조회: `GET /api/log-types`
  - 로그 타입 추가: `POST /api/log-types`
  - 로그 타입 수정: `PUT /api/log-types/:id`
  - 로그 타입 삭제: `DELETE /api/log-types/:id`

**2.1.2 로그 타입 데이터 모델**
- **위치**: `backend/src/models/logTypeModel.js` (신규)
- **데이터 구조**:
  ```json
  {
    "logTypes": [
      {
        "id": "LOGIN",
        "name": "로그인",
        "displayName": "로그인",
        "action": "LOGIN",
        "enabled": true,
        "order": 1
      },
      {
        "id": "pb_feplog",
        "name": "PB Fep Log",
        "displayName": "로그 PB Fep Log",
        "action": ["SEARCH", "DECRYPT"],
        "enabled": true,
        "order": 2
      }
    ]
  }
  ```
- **저장 위치**: `backend/src/data/log_types.json`

**2.1.3 통계 API 수정**
- **위치**: `backend/src/models/activityLogModel.js` (수정)
- **변경 사항**:
  - `getAllUserStatistics`: 동적으로 로그 타입별 집계
  - 로그 타입별 검색/복호화 건수를 동적으로 생성
  - 반환 데이터 구조를 동적으로 생성

**2.1.4 라우터 추가**
- **위치**: `backend/src/routes/logTypeRoutes.js` (신규)
- **등록**: `backend/src/server.js`에 라우터 등록

#### 2.2 프론트엔드 설계

**2.2.1 로그 타입 API 서비스**
- **위치**: `dev/frontend/src/services/api.js` (수정)
- **추가 메서드**:
  - `getLogTypeList()`: 로그 타입 목록 조회
  - `addLogType(data)`: 로그 타입 추가
  - `updateLogType(id, data)`: 로그 타입 수정
  - `deleteLogType(id)`: 로그 타입 삭제

**2.2.2 StatisticsFilters 컴포넌트 수정**
- **위치**: `dev/frontend/src/components/StatisticsFilters.js` (수정)
- **변경 사항**:
  - 하드코딩된 로그 타입 목록 제거
  - API에서 로그 타입 목록 조회
  - 동적으로 콤보박스 생성

**2.2.3 UserStatisticsTable 컴포넌트 수정**
- **위치**: `dev/frontend/src/components/UserStatisticsTable.js` (수정)
- **변경 사항**:
  - 하드코딩된 로그 타입별 컬럼 제거
  - 동적으로 로그 타입별 컬럼 생성
  - 동적으로 헤더 생성 (2행 구조 유지)

**2.2.4 ActivityStatistics 컴포넌트 수정**
- **위치**: `dev/frontend/src/components/ActivityStatistics.js` (수정)
- **변경 사항**:
  - 로그 타입 목록 상태 추가
  - 로그 타입 목록 조회 로직 추가

### 변경 파일 목록

#### 백엔드
- `backend/src/models/logTypeModel.js` (신규)
- `backend/src/controllers/logTypeController.js` (신규)
- `backend/src/routes/logTypeRoutes.js` (신규)
- `backend/src/models/activityLogModel.js` (수정)
- `backend/src/server.js` (수정 - 라우터 등록)
- `backend/src/data/log_types.json` (신규 - 초기 데이터)

#### 프론트엔드
- `dev/frontend/src/services/api.js` (수정)
- `dev/frontend/src/components/StatisticsFilters.js` (수정)
- `dev/frontend/src/components/UserStatisticsTable.js` (수정)
- `dev/frontend/src/components/ActivityStatistics.js` (수정)

### 데이터베이스 변경사항
- 파일 기반 저장소 사용 (`log_types.json`)
- 향후 데이터베이스 마이그레이션 시 확장 가능하도록 설계

## 3. 테스트 수행 방안

### 테스트 시나리오

#### 시나리오 1: 로그 타입 목록 조회
1. API 호출: `GET /api/log-types`
2. 응답 확인: 로그 타입 목록이 올바르게 반환되는지 확인
3. 프론트엔드에서 콤보박스에 표시되는지 확인

#### 시나리오 2: 통계 조회 (기존 로그 타입)
1. 로그 타입 필터 선택 (예: pb_feplog)
2. 조회 버튼 클릭
3. 통계 데이터가 올바르게 표시되는지 확인

#### 시나리오 3: 사용자별 통계 테이블 (동적 컬럼)
1. 조회 버튼 클릭
2. 사용자별 통계 테이블 확인
3. 로그 타입별 검색/복호화 컬럼이 동적으로 생성되는지 확인

#### 시나리오 4: 로그 타입 추가 (향후 관리자 기능)
1. 새로운 로그 타입 추가 API 호출
2. 프론트엔드 새로고침
3. 새로운 로그 타입이 콤보박스와 테이블에 표시되는지 확인

### 테스트 데이터
- 기존 로그 타입: LOGIN, pb_feplog, java_fw_imglog
- 테스트용 새 로그 타입: test_log_type (향후 추가)

### 테스트 환경
- 백엔드: `http://localhost:9100`
- 프론트엔드: `http://localhost:3001`

## 4. 체크리스트

- [x] 요건 문서 작성 완료
- [x] 백엔드 로그 타입 모델 구현
- [x] 백엔드 로그 타입 API 구현
- [x] 백엔드 통계 API 수정 (동적 집계)
- [x] 프론트엔드 API 서비스 수정
- [x] 프론트엔드 StatisticsFilters 수정
- [x] 프론트엔드 UserStatisticsTable 수정 (동적 컬럼)
- [x] 프론트엔드 ActivityStatistics 수정
- [x] 백엔드 검증 완료
- [x] 프론트엔드 검증 완료
- [x] 통합 테스트 완료
- [x] 문서화 완료

## 5. 테스트 결과

### 테스트 수행 일시
- 2026-02-08

### 테스트 결과

#### 백엔드 테스트 결과
**성공**
- 로그 타입 목록 조회 API: `GET /api/log-types` 정상 작동
- 사용자별 통계 API: 동적 로그 타입별 집계 정상 작동
- 필드명: `{logTypeId}SearchCount`, `{logTypeId}DecryptCount` 형식으로 생성

**테스트 명령어:**
```bash
# 로그 타입 목록 조회
curl http://localhost:9100/api/log-types

# 사용자별 통계 조회
curl "http://localhost:9100/api/statistics/activity/users/all?startDate=2026-02-01&endDate=2026-02-06"
```

**결과:**
- 로그 타입 목록: 3개 조회 성공 (LOGIN, pb_feplog, java_fw_imglog)
- 동적 필드 생성: `pb_feplogSearchCount`, `pb_feplogDecryptCount`, `java_fw_imglogSearchCount`, `java_fw_imglogDecryptCount` 정상 생성

#### 프론트엔드 테스트 결과
**성공**
- StatisticsFilters: 동적 로그 타입 목록 표시
- UserStatisticsTable: 동적 컬럼 생성 (로그 타입별 검색/복호화)
- ActivityStatistics: 로그 타입 목록 조회 및 전달

### 발견된 이슈 및 해결 방법

#### 이슈 없음
- 모든 기능이 정상적으로 작동
- 동적 로그 타입 관리 기능이 확장 가능한 구조로 구현됨

### 다음 단계
1. 관리자 화면에서 로그 타입 추가/수정/삭제 기능 구현 (향후)
2. 로그 타입별 권한 관리 기능 추가 (향후)

