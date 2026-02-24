# 20260206 - 활동 로그 통계 화면 개선

## 1. 사용자 요건 내용

### 요건 설명
기존 활동 로그 통계 화면을 개선하여 더욱 직관적이고 유용한 통계 정보를 제공합니다.

**주요 개선 사항:**
1. **일별/월별 통계 통합**: 일별과 월별 통계를 토글로 선택할 수 있도록 하고, 하나의 화면으로 통합
2. **검색 조건 확장**: 로그 타입, 사용자 ID, 부서, IP를 콤보박스로 선택하여 조회 가능
   - 로그 타입: 전체, 로그인, 로그 PB Fep Log, Java FW Image Log
   - 사용자 명 필터는 제거됨
3. **사용자별 통계 그래프**: 사용자별 통계를 일자/월별에 따라 그래프로 제공
4. **일별/월별 통계 그래프**: 일별/월별 통계도 그래프로 제공
5. **Excel 다운로드**: 통계 데이터를 Excel 파일로 다운로드 가능
6. **표/그래프 전환**: 표와 그래프를 선택하여 볼 수 있도록 제공 (기본값: 그래프)
7. **표 정렬 기능**: 표의 경우 필드별 정렬 기능 추가

### 사용자 시나리오

#### 시나리오 1: 일별/월별 통계 조회
1. 관리자가 통계 화면에 접근합니다.
2. 상단에서 "일별" 또는 "월별" 토글을 선택합니다.
3. 검색 조건을 설정합니다:
   - 로그 타입 (콤보박스 선택: 전체, 로그인, 로그 PB Fep Log, Java FW Image Log)
   - 사용자 ID (콤보박스 선택)
   - 부서 (콤보박스 선택)
   - IP (콤보박스 선택)
4. 날짜 범위를 선택합니다 (일별: 시작일~종료일, 월별: 연도/월)
5. "조회" 버튼을 클릭합니다.
6. 기본적으로 그래프가 표시됩니다.
7. "표" 버튼을 클릭하여 테이블 형태로 전환할 수 있습니다.
8. 테이블에서 컬럼 헤더를 클릭하여 정렬할 수 있습니다.
9. "Excel 다운로드" 버튼을 클릭하여 통계 데이터를 Excel 파일로 다운로드합니다.

#### 시나리오 2: 사용자별 통계 조회
1. 관리자가 통계 화면에 접근합니다.
2. "사용자별 통계" 섹션으로 이동합니다.
3. 사용자 ID를 선택합니다.
4. 일별/월별 토글을 선택합니다.
5. 날짜 범위를 선택합니다.
6. "조회" 버튼을 클릭합니다.
7. 선택한 기간에 따른 사용자별 통계가 그래프로 표시됩니다.
8. 표로 전환하여 상세 데이터를 확인할 수 있습니다.

### 기대 결과
- 일별/월별 통계를 하나의 화면에서 토글로 전환하여 조회 가능
- 다양한 검색 조건(로그 타입, 사용자 ID, 부서, IP)으로 필터링 가능
- 통계 데이터를 그래프와 표로 시각화하여 제공
- Excel 다운로드 기능으로 데이터 분석 용이
- 표에서 필드별 정렬 기능으로 데이터 탐색 용이

## 2. 설계

### 기술 설계

#### 백엔드 설계

**⚠️ 개발 환경 안내**
- 통계 API는 Node.js 서버에서 제공됩니다 (루트의 `backend/` 디렉토리)
- 이 서버는 통계 API 전용이며, 포트 9100에서 실행됩니다
- 개발 시 이 서버도 dev 환경으로 사용하거나, dev/backend에 별도로 구성할 수 있습니다
- **주의**: 루트의 `backend/`는 통계 API 전용이므로, 다른 백엔드 로직(Java Spring Boot)은 `dev/backend/`에 있습니다

**1. 통계 API 확장**
- 기존 API에 필터 조건 추가
- 일별 통계 API: `GET /api/statistics/activity/daily?startDate=YYYY-MM-DD&endDate=YYYY-MM-DD&logType=xxx&userId=xxx&department=xxx&ip=xxx`
- 월별 통계 API: `GET /api/statistics/activity/monthly?year=YYYY&month=MM&logType=xxx&userId=xxx&department=xxx&ip=xxx`
- 로그 타입 값: `LOGIN` (로그인), `pb_feplog` (로그 PB Fep Log), `java_fw_imglog` (Java FW Image Log)
- 사용자별 통계 API: `GET /api/statistics/activity/user?userId=USER_ID&startDate=YYYY-MM-DD&endDate=YYYY-MM-DD&periodType=daily|monthly`

**2. 필터 조건 처리**
- 활동로그 모델에 로그 타입 저장 필요
- 필터 조건에 따라 활동로그를 필터링하여 통계 집계
- 로그 타입 필터:
  - `LOGIN`: action이 'LOGIN'인 경우
  - `pb_feplog`: metadata.logType이 'pb_feplog'인 경우
  - `java_fw_imglog`: metadata.logType이 'java_fw_imglog'인 경우

**3. Excel 다운로드 API**
- `GET /api/statistics/activity/export?type=daily|monthly|user&...` (쿼리 파라미터는 통계 조회와 동일)
- Excel 파일 생성 및 다운로드 제공

**4. 사용자 목록 API (콤보박스용)**
- `GET /api/statistics/users` - 사용자 ID, 사용자 명 목록 조회
- `GET /api/statistics/departments` - 부서 목록 조회
- `GET /api/statistics/ips` - IP 목록 조회

#### 프론트엔드 설계

**⚠️ 개발 환경 안내**
- **모든 프론트엔드 작업은 `dev/frontend/` 디렉토리에서 수행합니다**
- 루트의 `src/` 디렉토리는 prod 환경일 수 있으므로 직접 수정하지 않습니다
- 통계 API는 별도의 Node.js 서버(포트 9100)를 사용합니다

**1. 통계 화면 컴포넌트 구조**
**위치**: `dev/frontend/src/components/`
```
ActivityStatistics.js (메인 컴포넌트 - 수정)
├── StatisticsHeader.js (신규 - 일별/월별 토글, 날짜 선택)
├── StatisticsFilters.js (신규 - 로그 타입, 사용자 ID, 부서, IP 콤보박스)
├── StatisticsView.js (신규 - 표/그래프 전환, Excel 다운로드 버튼)
├── StatisticsChart.js (신규 - 그래프 컴포넌트 - Chart.js 사용)
└── StatisticsTable.js (신규 - 표 컴포넌트 - 정렬 기능 포함)
```

**2. 그래프 라이브러리**
- Chart.js 또는 Recharts 사용
- 일별/월별 통계: 라인 차트 또는 바 차트
- 사용자별 통계: 라인 차트

**3. Excel 다운로드**
- `xlsx` 라이브러리 사용하여 클라이언트에서 Excel 생성
- 또는 백엔드 API를 통해 Excel 파일 다운로드

**4. 상태 관리**
- React Hooks (useState, useEffect) 사용
- 검색 조건, 통계 데이터, 표/그래프 전환 상태 관리

### 변경 파일 목록

**⚠️ 중요: 모든 개발 작업은 `dev/` 디렉토리 내에서만 수행해야 합니다.**

#### 백엔드 (Node.js 통계 API 서버)
**참고**: 통계 API는 Node.js 서버에서 제공됩니다. 루트의 `backend/`는 통계 API 전용 서버입니다.
- `backend/src/models/activityLogModel.js` (수정 - 로그 타입 필터 추가, 로그 타입 저장)
- `backend/src/services/activityLogService.js` (수정 - 로그 타입 필터 처리, 로그 타입 저장)
- `backend/src/controllers/statisticsController.js` (수정 - 로그 타입 필터 API, Excel 다운로드 API)
- `backend/src/routes/statisticsRoutes.js` (수정 - 새로운 엔드포인트 추가)
- `backend/src/controllers/logController.js` (수정 - 활동로그 기록 시 로그 타입 저장)

**주의**: 루트의 `backend/`는 통계 API 전용 Node.js 서버입니다. 이 서버도 dev 환경으로 사용하거나, dev/backend에 별도로 구성해야 합니다.

#### 프론트엔드 (dev 환경)
**⚠️ 모든 프론트엔드 작업은 `dev/frontend/` 디렉토리에서 수행합니다.**
- `dev/frontend/src/components/ActivityStatistics.js` (수정 - 일별/월별 토글, 필터, 그래프/표 전환, Excel 다운로드 추가)
- `dev/frontend/src/components/ActivityStatistics.css` (수정 - 스타일 개선)
- `dev/frontend/src/components/StatisticsHeader.js` (신규 생성 - 일별/월별 토글 컴포넌트)
- `dev/frontend/src/components/StatisticsFilters.js` (신규 생성 - 로그 타입, 사용자 ID, 부서, IP 콤보박스 컴포넌트)
- `dev/frontend/src/components/StatisticsView.js` (신규 생성 - 표/그래프 전환 컴포넌트)
- `dev/frontend/src/components/StatisticsChart.js` (신규 생성 - 그래프 컴포넌트)
- `dev/frontend/src/components/StatisticsTable.js` (신규 생성 - 표 컴포넌트, 정렬 기능 포함)
- `dev/frontend/src/services/api.js` (수정 - statisticsApi 확장: 필터 조건, Excel 다운로드 API 추가)
- `dev/frontend/src/App.js` (이미 통계 화면 라우팅이 있음 - 확인 필요)

### 데이터베이스 변경사항
- 활동로그에 로그 타입 필드 추가:
  - `logType` (로그 타입: 'LOGIN', 'pb_feplog', 'java_fw_imglog' 또는 null)
  - 활동로그 기록 시 metadata에 logType 저장

## 3. 테스트 수행 방안

### 테스트 시나리오

#### 백엔드 테스트
1. **필터 조건 테스트**
   - 사용자 ID로 필터링 테스트
   - 사용자 명으로 필터링 테스트
   - 부서로 필터링 테스트
   - IP로 필터링 테스트
   - 복합 필터 조건 테스트

2. **통계 API 테스트**
   - 일별 통계 API 필터 조건 테스트
   - 월별 통계 API 필터 조건 테스트
   - 사용자별 통계 API periodType 테스트

3. **Excel 다운로드 API 테스트**
   - 일별 통계 Excel 다운로드 테스트
   - 월별 통계 Excel 다운로드 테스트
   - 사용자별 통계 Excel 다운로드 테스트

4. **사용자 목록 API 테스트**
   - 사용자 목록 조회 테스트
   - 부서 목록 조회 테스트
   - IP 목록 조회 테스트

#### 프론트엔드 테스트
1. **UI 테스트**
   - 일별/월별 토글 동작 테스트
   - 검색 조건 콤보박스 동작 테스트
   - 표/그래프 전환 테스트
   - 표 정렬 기능 테스트
   - Excel 다운로드 버튼 동작 테스트

2. **통합 테스트**
   - 전체 플로우 테스트 (검색 조건 설정 → 조회 → 그래프/표 표시)
   - 필터 조건 변경 시 통계 갱신 테스트
   - Excel 다운로드 파일 검증

### 테스트 데이터
- 다양한 사용자 정보(사용자 ID, 사용자 명, 부서, IP)를 가진 활동로그 생성
- 여러 날짜에 걸친 활동 데이터 생성

### 테스트 환경
- 로컬 개발 환경
- 브라우저: Chrome, Firefox

## 4. 체크리스트

- [ ] 요건 문서 작성 완료
- [ ] 백엔드 설계 완료
- [ ] 프론트엔드 설계 완료
- [ ] 백엔드 구현 완료
- [ ] 프론트엔드 구현 완료
- [ ] 프론트엔드 검증 완료
- [ ] 백엔드 검증 완료
- [ ] 통합 테스트 완료
- [ ] 문서화 완료

## 5. 테스트 결과

### 테스트 수행 일시
- (구현 완료 후 작성)

### 구현 완료 사항
- (구현 완료 후 작성)

### 테스트 방법
- (구현 완료 후 작성)

### 발견된 이슈 및 해결 방법
- (테스트 중 발견 시 작성)

---

**작성일**: 2026-02-06
**버전**: 1.0.0

