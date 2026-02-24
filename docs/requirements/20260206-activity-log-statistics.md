# 20260206 - 활동로그 통계 화면

## 1. 사용자 요건 내용

### 요건 설명
- 활동로그에 대한 통계 데이터를 볼 수 있는 화면이 필요합니다.
- **전체 사용자 대상**으로 다음 통계를 제공해야 합니다:
  1. **월별 통계**: 월 단위로 검색/복호화 활동 통계
  2. **일별 통계**: 일 단위로 검색/복호화 활동 통계
  3. **사용자별 통계**: 사용자별 검색을 통한 통계 조회 기능

### 사용자 시나리오
1. 관리자가 통계 화면에 접근합니다.
2. 통계 유형을 선택합니다:
   - **월별 통계**: 연도와 월을 선택하여 월별 통계 조회
   - **일별 통계**: 날짜 범위를 선택하여 일별 통계 조회
   - **사용자별 통계**: 사용자 ID를 검색하여 특정 사용자의 통계 조회
3. 각 통계 유형별로 다음 정보를 확인합니다:
   - 검색 횟수 (누가 검색을 많이 했는지)
   - 복호화 횟수 (누가 복호화를 많이 했는지)
   - 전체 통계 요약
4. 통계 데이터를 테이블 형태로 확인합니다.

### 기대 결과
- 월별 통계 데이터를 시각적으로 확인 가능
- 일별 통계 데이터를 시각적으로 확인 가능
- 사용자별 검색을 통한 통계 조회 가능
- 전체 사용자 대상 통계 제공
- 날짜 범위별 통계 조회 가능

## 2. 설계

### 기술 설계

#### 백엔드 설계
1. **활동로그 저장**
   - 로그 검색 시 활동로그 기록 (action: 'SEARCH', userId, timestamp)
   - 복호화 시 활동로그 기록 (action: 'DECRYPT', userId, timestamp)
   - 메모리 기반 저장소 사용 (JSON 파일 또는 인메모리 배열)
   - 향후 DB 연동 가능하도록 구조 설계

2. **통계 API**
   - **일별 통계**: `GET /api/statistics/activity/daily?startDate=YYYY-MM-DD&endDate=YYYY-MM-DD`
   - **월별 통계**: `GET /api/statistics/activity/monthly?year=YYYY&month=MM`
   - **사용자별 통계**: `GET /api/statistics/activity/user?userId=USER_ID&startDate=YYYY-MM-DD&endDate=YYYY-MM-DD`
   
   응답 형식 (일별):
     ```json
     {
       "success": true,
       "data": {
         "dailyStats": [
           {
             "date": "2026-02-01",
             "totalSearches": 150,
             "totalDecrypts": 45,
             "userStats": [
               {
                 "userId": "user001",
                 "searchCount": 50,
                 "decryptCount": 15
               }
             ]
           }
         ],
         "summary": {
           "totalSearches": 1500,
           "totalDecrypts": 450,
           "uniqueUsers": 25
         }
       }
     }
     ```
   
   응답 형식 (월별):
     ```json
     {
       "success": true,
       "data": {
         "month": "2026-02",
         "totalSearches": 4500,
         "totalDecrypts": 1350,
         "dailyStats": [
           {
             "date": "2026-02-01",
             "totalSearches": 150,
             "totalDecrypts": 45
           }
         ],
         "userStats": [
           {
             "userId": "user001",
             "searchCount": 1500,
             "decryptCount": 450
           }
         ]
       }
     }
     ```
   
   응답 형식 (사용자별):
     ```json
     {
       "success": true,
       "data": {
         "userId": "user001",
         "totalSearches": 1500,
         "totalDecrypts": 450,
         "dailyStats": [
           {
             "date": "2026-02-01",
             "searchCount": 50,
             "decryptCount": 15
           }
         ],
         "period": {
           "startDate": "2026-02-01",
           "endDate": "2026-02-28"
         }
       }
     }
     ```

#### 프론트엔드 설계
1. **통계 화면 컴포넌트**
   - `ActivityStatistics.js` - 메인 통계 화면
   - 통계 유형 선택 탭 (월별/일별/사용자별)
   - **월별 통계**: 연도/월 선택 컴포넌트
   - **일별 통계**: 날짜 범위 선택 컴포넌트
   - **사용자별 통계**: 사용자 ID 검색 컴포넌트 + 날짜 범위 선택
   - 통계 테이블 컴포넌트 (각 유형별)

2. **라우팅**
   - App.js에 통계 화면 라우팅 설정 (이미 구현됨)

3. **API 서비스**
   - `api.js`에 `statisticsApi` 확장
     - `getDailyStatistics(startDate, endDate)`
     - `getMonthlyStatistics(year, month)`
     - `getUserStatistics(userId, startDate, endDate)`

### 변경 파일 목록

#### 백엔드
- `backend/src/models/activityLogModel.js` (수정 - 월별/사용자별 통계 함수 추가)
- `backend/src/services/activityLogService.js` (수정 - 월별/사용자별 통계 메서드 추가)
- `backend/src/controllers/statisticsController.js` (수정 - 월별/사용자별 통계 API 추가)
- `backend/src/routes/statisticsRoutes.js` (수정 - 새로운 엔드포인트 추가)
- `backend/src/server.js` (이미 수정됨)
- `backend/src/controllers/logController.js` (이미 수정됨)
- `backend/src/data/activity_logs.json` (이미 생성됨)

#### 프론트엔드
- `dev/frontend/src/components/ActivityStatistics.js` (수정 - 월별/사용자별 통계 추가)
- `dev/frontend/src/components/ActivityStatistics.css` (수정 - 탭 스타일 추가)
- `dev/frontend/src/services/api.js` (수정 - statisticsApi 확장)
- `dev/frontend/src/App.js` (이미 수정됨)

### 데이터베이스 변경사항
- 현재는 파일 기반 저장소 사용
- 향후 DB 연동 시 테이블 설계:
  ```sql
  CREATE TABLE activity_logs (
    id SERIAL PRIMARY KEY,
    user_id VARCHAR(100) NOT NULL,
    action VARCHAR(20) NOT NULL, -- 'SEARCH' or 'DECRYPT'
    timestamp TIMESTAMP NOT NULL,
    metadata JSONB
  );
  ```

## 3. 테스트 수행 방안

### 테스트 시나리오

#### 백엔드 테스트
1. **활동로그 기록 테스트**
   - 로그 검색 시 활동로그가 기록되는지 확인
   - 복호화 시 활동로그가 기록되는지 확인
   - 동일 사용자의 여러 활동이 모두 기록되는지 확인

2. **통계 API 테스트**
   - 날짜 범위별 통계 조회 테스트
   - 사용자별 통계 집계 정확성 테스트
   - 일별 통계 집계 정확성 테스트
   - 빈 날짜 범위 조회 테스트

#### 프론트엔드 테스트
1. **UI 테스트**
   - 날짜 범위 선택 기능 테스트
   - 통계 데이터 표시 테스트
   - 로딩 상태 표시 테스트
   - 에러 처리 테스트

2. **통합 테스트**
   - 전체 플로우 테스트 (검색 → 활동로그 기록 → 통계 조회)
   - 날짜 범위 변경 시 통계 갱신 테스트

### 테스트 데이터
- 다양한 사용자 ID로 검색/복호화 활동 생성
- 여러 날짜에 걸친 활동 데이터 생성

### 테스트 환경
- 로컬 개발 환경
- 브라우저: Chrome, Firefox

## 4. 체크리스트

- [x] 요건 문서 작성 완료 (월별/일별/사용자별 통계 요건 추가)
- [x] 백엔드 설계 완료 (월별/사용자별 통계 API 설계)
- [x] 프론트엔드 설계 완료 (탭 방식 통계 화면 설계)
- [x] 백엔드 구현 완료 (월별/사용자별 통계 API 구현)
- [x] 프론트엔드 구현 완료 (탭 방식 통계 화면 구현)
- [x] 활동로그 기록 기능 통합 완료
- [ ] 프론트엔드 검증 완료
- [ ] 백엔드 검증 완료
- [ ] 통합 테스트 완료
- [x] 문서화 완료

## 5. 테스트 결과

### 테스트 수행 일시
- 2026-02-06 (구현 완료)
- 2026-02-06 16:45 (통합 테스트 완료)

### 구현 완료 사항

#### 백엔드
- ✅ 활동로그 모델 구현 (`backend/src/models/activityLogModel.js`)
  - JSON 파일 기반 활동로그 저장
  - 날짜 범위별 조회 기능
  - 일별/사용자별 통계 집계 기능
  
- ✅ 활동로그 서비스 구현 (`backend/src/services/activityLogService.js`)
  - 검색 활동 기록 (`logSearch`)
  - 복호화 활동 기록 (`logDecrypt`)
  - 통계 조회 (`getStatistics`)
  
- ✅ 통계 컨트롤러 구현 (`backend/src/controllers/statisticsController.js`)
  - 날짜 범위 검증
  - 통계 데이터 조회 API
  
- ✅ 통계 라우터 구현 (`backend/src/routes/statisticsRoutes.js`)
  - `GET /api/statistics/activity` 엔드포인트
  
- ✅ 기존 로그 컨트롤러에 활동로그 기록 통합
  - `searchLogs`: 검색 시 활동로그 기록
  - `getDecryptedData`: 복호화 시 활동로그 기록
  
- ✅ 서버에 통계 라우터 등록 (`backend/src/server.js`)

#### 프론트엔드
- ✅ 통계 화면 컴포넌트 구현 (`src/components/ActivityStatistics.js`)
  - 날짜 범위 선택 기능
  - 요약 통계 표시 (전체 검색/복호화 횟수, 활동 사용자 수)
  - 일별 통계 테이블
  - 사용자별 통계 테이블
  
- ✅ 통계 화면 스타일 구현 (`src/components/ActivityStatistics.css`)
  - 반응형 디자인
  - 모던한 UI/UX
  
- ✅ API 서비스 연동 (`src/services/api.js`)
  - `statisticsApi.getActivityStatistics` 추가
  
- ✅ 앱 네비게이션 추가 (`src/App.js`)
  - 탭 방식 네비게이션 (로그 검색 / 활동로그 통계)
  - 헤더에 네비게이션 버튼 추가

### 테스트 방법

#### 1. 백엔드 서버 실행
```bash
cd backend
npm start
```

#### 2. 프론트엔드 서버 실행
```bash
npm start
```

#### 3. 테스트 시나리오
1. **활동로그 생성**
   - 로그 검색 화면에서 검색 수행 (여러 사용자 ID로)
   - 로그 상세에서 복호화 수행
   
2. **통계 조회**
   - 상단 네비게이션에서 "활동로그 통계" 탭 클릭
   - 날짜 범위 선택 (기본값: 최근 7일)
   - "조회" 버튼 클릭
   - 요약 통계, 일별 통계, 사용자별 통계 확인

#### 4. API 직접 테스트
```bash
# 통계 조회 API 테스트
curl "http://localhost:9100/api/statistics/activity?startDate=2026-02-01&endDate=2026-02-06"
```

### 발견된 이슈 및 해결 방법
- **이슈**: 날짜 범위 필터링 시 하루 전체를 포함하지 않는 문제
  - **해결**: `startOfDay`, `endOfDay` 함수를 사용하여 날짜 범위를 하루 전체로 확장

### 통합 테스트 결과
- ✅ 백엔드 서버 정상 동작 확인
- ✅ 활동로그 기록 기능 정상 동작 (검색/복호화)
- ✅ 통계 API 정상 동작 (일별/사용자별/요약 통계)
- ✅ 데이터 정확성 검증 완료
- ✅ 에러 케이스 처리 정상 동작
- ⚠️ 시작일이 종료일보다 늦은 경우 검증 추가 권장

**상세 테스트 결과**: `20260206-activity-log-statistics-test-results.md` 참조

### 다음 단계
- 프론트엔드 통합 테스트 (브라우저 UI 확인)
- 실제 사용 환경에서 테스트 수행
- 활동로그 데이터가 많아질 경우 성능 최적화 검토 (인덱싱, 페이징 등)
- 향후 DB 연동 시 테이블 설계 및 마이그레이션 계획

