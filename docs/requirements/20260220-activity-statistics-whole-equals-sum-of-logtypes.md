# 20260220 - 활동 로그 통계 '전체' = 로그타입 합계 정합성

## 1. 사용자 요건 내용

### 요건 설명
활동 로그 통계 화면에서 로그타입이 **'전체'**일 때의 수치와, **나머지 로그 타입(LOGIN, pb_feplog, java_fw_imglog 등)별 수치의 합계**가 일치해야 한다. 현재는 불일치하는 것으로 보임.

### 사용자 시나리오
1. 사용자가 활동 로그 통계 화면에서 로그타입 **'전체'**로 조회한다.
2. 요약(검색/복호화/로그인 횟수, 사용자 수) 및 일별/월별 수치를 확인한다.
3. 동일 기간·동일 조건으로 각 로그타입(LOGIN, pb_feplog, java_fw_imglog)을 선택해 조회한 뒤 수치를 합산한다.
4. **문제**: '전체' 수치 ≠ (각 로그타입 수치의 합).

### 기대 결과
- **전체** 선택 시 표시되는 검색/복호화/로그인 횟수 및 사용자 수 = **각 로그타입별로 조회한 수치의 합**과 동일하다.

## 2. 설계

### 기술 설계

#### 문제 분석
1. **현재 '전체' 집계 방식**: `logType`이 비어 있을 때 **필터 없이** `user_activity_log` 전체를 집계한다.
2. **개별 로그타입 집계**: `LOGIN`은 `action_type = 'LOGIN'`, 그 외는 `action_detail::text LIKE '%"logType":"xxx"%'`로 필터한다.
3. **불일치 원인**: `action_detail`에 `logType`이 없거나, 다른 형태로 저장된 행은 '전체'에는 포함되지만 개별 로그타입 합계에는 포함되지 않아 **전체 > 합계**가 될 수 있다.

#### 해결 방안
- **'전체'를 "각 로그타입별 집계의 합"으로 정의**한다.
- 백엔드에서 `logType`이 null/빈값일 때: 통계에 사용하는 로그타입 목록(LOGIN, pb_feplog, java_fw_imglog)에 대해 **각각 동일 집계를 수행한 뒤 일별·요약 수치를 합산**하여 반환한다.
- 이렇게 하면 **전체 = sum(LOGIN, pb_feplog, java_fw_imglog)** 가 항상 성립한다.

**백엔드:**
- `ActivityStatisticsService`: (1) 통계용 로그타입 목록 상수 또는 조회, (2) `logType`이 비어 있을 때 각 로그타입별로 기존 집계 로직 호출 후 일별·요약 병합(합산), (3) 사용자별 통계(`getAllUserStatistics`)도 동일하게 '전체'일 때 로그타입별 합산 적용.

### 변경 파일 목록

#### 백엔드
- `backend/src/main/java/com/logmng/service/ActivityStatisticsService.java`
  - 통계용 로그타입 ID 목록 정의(LOGIN, pb_feplog, java_fw_imglog).
  - 일별/월별 통계: logType 비어 있으면 각 로그타입별 집계 결과를 합산해 반환.
  - 사용자별 통계: logType 비어 있으면 각 로그타입별 결과를 사용자별로 합산해 반환.

#### 프론트엔드 (추가 조치)
- **통계 로그타입 콤보에 '로그인' 추가**: 백엔드 '전체' = LOGIN + pb_feplog + java_fw_imglog 합산인데, 기존 통계 화면에는 **로그인(LOGIN)** 옵션이 없어 사용자가 합산할 때 pb_feplog + java_fw_imglog만 더해 **전체 ≠ 합계**로 보였음. `StatisticsFilters.js`에서 로그타입 옵션에 `{ value: 'LOGIN', label: '로그인' }`을 추가하여, 화면에서 '전체' = (로그인 + pb_feplog + java_fw_imglog) 합과 동일하게 맞춤.

### 데이터베이스 변경사항
- 없음.

## 3. 테스트 수행 방안

### 테스트 케이스 목록

| ID | 구분 | 시나리오(입력·조건) | 기대 결과 | 검증 방법 |
|----|------|----------------------|-----------|-----------|
| TC-01 | 정상 | 로그타입 '전체'로 일별 통계 조회 | 200, summary/dailyStats 반환 | curl 또는 단위 테스트 |
| TC-02 | 정합성 | 동일 기간으로 '전체' 조회 후, LOGIN/pb_feplog/java_fw_imglog 각각 조회해 합산 | 전체 totalSearches/totalDecrypts/totalLogins = 합계 | 수동 또는 자동 검증 |
| TC-03 | 정상 | 로그타입 특정 값으로 조회 | 기존과 동일 동작 | mvn test |

### 테스트 시나리오
1. 백엔드 단위 테스트: `mvn test` 통과.
2. 통합: 동일 startDate/endDate로 GET `/api/statistics/activity/daily?startDate=...&endDate=...`(전체) 호출 후, 동일 조건에 `logType=LOGIN`, `logType=pb_feplog`, `logType=java_fw_imglog` 각각 호출해 summary 수치 합산 = 전체 summary 수치 확인.

## 4. 체크리스트

- [ ] 백엔드: '전체' 시 로그타입별 합산 로직 구현
- [ ] 백엔드: 사용자별 통계 '전체' 동일 방식 적용
- [ ] 단위 테스트 통과
- [ ] 검증(재시작·헬스·통계 API) 통과

## 5. 테스트 결과

### 테스트 수행 일시
- 2026-02-20

### 결과
- **백엔드 컴파일**: 성공 (`mvn compile`, `mvn package -DskipTests`)
- **백엔드 단위 테스트**: 프로젝트 공통 이슈(JUnit TestEngine 발견 실패)로 미실행. 코드 변경 범위는 통계 서비스 로직만 해당.
- **검증**: 백엔드 재시작 후 `GET /api/statistics/activity/daily?startDate=2026-02-01&endDate=2026-02-20`(전체)와 동일 조건으로 `logType=LOGIN`, `pb_feplog`, `java_fw_imglog` 각각 호출해 summary 합산 비교 → **전체 summary = 로그타입별 합계** 일치 확인.

## 6. 오류 조치 결과 (원인·조치)

- **요구사항 ID**: 20260220-activity-statistics-whole-equals-sum-of-logtypes
- **원인 (Root Cause)**: '전체' 선택 시 로그타입 필터 없이 `user_activity_log` 전체를 집계하고, 개별 로그타입은 `action_detail`의 `logType` 또는 `action_type=LOGIN`으로만 필터해 집계하고 있어, `action_detail`에 logType이 없거나 다른 형태인 행은 '전체'에만 포함되어 **전체 수치 ≠ 개별 로그타입 합계**가 됨.
- **조치 내용 (Actions Taken)**: `ActivityStatisticsService`에서 로그타입이 비어 있을 때 통계용 로그타입 목록(LOGIN, pb_feplog, java_fw_imglog)별로 동일 집계를 수행한 뒤 일별·요약·사용자별 통계를 **합산**하여 반환하도록 변경. '전체' = sum(각 로그타입)로 정의.
- **조치 결과 (Result)**: 동일 기간으로 '전체' 조회 시 summary(totalSearches, totalDecrypts, totalLogins, uniqueUsers)가 LOGIN + pb_feplog + java_fw_imglog 조회 결과의 합(및 uniqueUsers 합집합)과 일치함을 curl로 확인.
- **추가 조치 (시작일 2/1~오늘 여전히 불일치)**  
  - **원인**: 통계 화면 로그타입 드롭다운에 **로그인(LOGIN)**이 없어, 사용자가 "나머지 로그 타입"을 합산할 때 pb_feplog + java_fw_imglog만 더함. 백엔드는 '전체' = LOGIN + pb_feplog + java_fw_imglog 이므로 **전체 > (화면에서 보이는 합)** 으로 보임.  
  - **조치**: `frontend/src/components/StatisticsFilters.js`에 로그타입 옵션 **'로그인'(value: LOGIN)** 추가. 이제 화면에서 전체 = 로그인 + PB FEP Log + Java FW Image Log 합으로 검증 가능.
- **완료 일시**: 2026-02-20
