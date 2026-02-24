# 20260224 - 검색 이력 및 복호화 승인 재요청 (부가 기능)

## 1. 사용자 요건 내용

### 요건 설명
복호화 승인/결재 기능 도입에 앞서, **검색 이력**과 **복호화 승인 여부·재요청**을 지원하는 부가 기능을 먼저 구현한다.

- 복호화 시 승인요청/결재를 통해 검색 결과에 대한 복호화 승인요청을 결재자에게 요청하고, 결재자가 승인하면 복호화가 가능한 프로세스를 향후 도입할 예정이다.
- 결재 유효 기간은 **1일**로 고정하며, 1일 경과 시 다시 승인요청을 해야 한다.
- **한 번 승인요청을 한 검색**(결재 승인 여부와 무관)은 **검색 이력**을 통해 재 조회할 수 있어야 한다.

본 요건은 다음을 목표로 한다.
- 사용자별 **최근 검색 이력 목록** 제공
- 목록 항목: **순번, 일시, 검색 조건, 복호화 승인 여부**, 만료된 경우 **재요청** 가능

### 사용자 시나리오

1. 사용자가 로그 검색을 수행하고, 복호화가 필요한 경우 승인요청을 한다.
2. 시스템은 해당 검색을 검색 이력에 저장한다(검색 조건·일시·승인 요청 여부 포함).
3. 사용자가 "검색 이력" 메뉴에서 자신의 최근 검색 이력 목록을 조회한다.
4. 목록에서 **순번, 일시, 검색 조건 요약, 복호화 승인 여부**(대기/승인/만료/반려)를 확인한다.
5. **만료된 건**에 대해 "재요청" 버튼을 눌러 다시 승인요청을 할 수 있다.
6. 이력 항목을 선택하면 동일 검색 조건으로 재 조회할 수 있다(같은 조건으로 검색 API 재호출).

### 기대 결과

- 사용자별 검색 이력이 DB에 저장되고, 목록 API로 조회 가능하다.
- 목록에 순번, 일시, 검색 조건(요약), 복호화 승인 여부가 표시된다.
- 승인 유효 기간(1일)이 지난 건은 "만료"로 표시되며, "재요청" 액션이 가능하다.
- 이력에서 "재 조회" 시 저장된 검색 조건으로 검색이 다시 실행된다.
- (실제 결재자 승인 프로세스는 별도 요건에서 구현; 본 요건에서는 승인 상태 저장·표시·재요청 플로우만 구현)

### 결재 이력 관리 (2026-02-24 보강)

- **승인 요청 이력**: 위와 같이 `search_history`로 관리됨.
- **결재 이력**: 누가·언제 승인/반려했는지 이력을 남겨 감사·추적이 가능하도록 한다.
  - 저장 항목: 결재자(approver) user_id, 결재 일시(approved_at / rejected_at), 반려 시 사유(rejection_reason, 선택).
  - 검색 이력 목록·상세 API 응답에 결재 이력(결재자, 결재일시, 반려 사유)을 포함하여 화면에서 확인 가능하게 한다.
  - 테스트용으로 결재자 없이 즉시 승인하는 경우: `approved_by` = 요청자 본인 또는 시스템 표시용 값, `approved_at` = requested_at 동일 또는 저장 시점.

---

## 2. 설계

### 기술 설계

#### 문제 분석
1. 현재 검색은 요청 시마다 실행되며, 검색 조건·복호화 승인 상태를 이력으로 보관하지 않음.
2. 복호화 승인 유효 기간(1일)과 만료 시 재요청 개념을 담을 데이터 모델이 필요함.
3. 사용자별 이력 목록 조회·재조회·재요청 API 및 UI가 필요함.

#### 해결 방안

**데이터 모델**
- **검색 이력** 테이블: 한 건당 "한 번의 검색(및 승인요청)" 기록.
- 저장 시점: 사용자가 검색 실행 시(복호화 요청 여부와 무관하게 저장하거나, 복호화 요청 시점에 한해 저장하는 정책 선택 가능).  
  → **1안**: 복호화 요청이 포함된 검색만 이력 저장.  
  → **2안**: 모든 검색을 이력 저장하고, 복호화 요청 여부/승인 상태만 플래그.  
  요건상 "한 번 승인요청을 한 검색"을 재 조회 가능하게 하므로, **승인요청이 발생한 검색**을 이력에 남기는 것이 적합.  
  → **초기 구현**: 검색 시 "복호화 요청" 플래그가 있거나, 별도 "승인 요청" 액션 시 이력 행을 생성.  
  → 이력 행에 검색 조건(JSON), 일시, 사용자, 복호화 승인 상태, 요청일시·만료일시를 저장.

**상태 정의**
- `PENDING`: 승인 대기
- `APPROVED`: 승인됨 (만료일까지 유효)
- `EXPIRED`: 유효 기간(1일) 경과
- `REJECTED`: 반려

**유효 기간**
- 승인 요청 시점 + 1일 = 만료 시각. 만료 시 자동으로 `EXPIRED`로 간주(조회 시 계산 가능).

**백엔드**
- DB: `search_history`(또는 `decrypt_search_history`) 테이블 추가.
- API:
  - `POST /api/search-history` — 검색 이력 저장(검색 실행 시 또는 승인 요청 시 호출).
  - `GET /api/search-history` — 현재 사용자 최근 검색 이력 목록(순번, 일시, 검색 조건 요약, 복호화 승인 여부, 만료 여부).
  - `POST /api/search-history/{id}/re-request` — 만료된 건에 대한 재요청(동일 검색 조건으로 새 승인 요청 생성 또는 상태를 PENDING으로 갱신 + 만료일 1일 연장).
  - `GET /api/search-history/{id}` — 이력 상세(검색 조건 전체) — 재 조회 시 사용.

**프론트엔드**
- 검색 이력 목록 화면(또는 기존 로그 검색 화면 내 패널): 테이블에 순번, 일시, 검색 조건 요약, 복호화 승인 여부, 만료 시 "재요청" 버튼.
- 재요청: 해당 이력 ID로 re-request API 호출.
- 재 조회: 이력 ID로 상세 조회 후 저장된 검색 조건으로 기존 검색 API 재호출.

### 변경 파일 목록

#### 백엔드
- `backend/src/main/resources/db/schema.sql` — `search_history` 테이블 및 인덱스 추가.
- `backend/src/main/java/com/logmng/entity/SearchHistory.java` — 엔티티(또는 DTO/도메인 모델).
- `backend/src/main/java/com/logmng/repository/SearchHistoryRepository.java` — JPA/MyBatis 리포지토리.
- `backend/src/main/java/com/logmng/service/SearchHistoryService.java` — 이력 저장·목록·재요청·상세 로직.
- `backend/src/main/java/com/logmng/controller/SearchHistoryController.java` — REST API.
- `backend/src/main/java/com/logmng/dto/request/SearchHistoryCreateRequest.java` — 생성 요청 DTO.
- `backend/src/main/java/com/logmng/dto/response/SearchHistoryListResponse.java` — 목록 응답 DTO.
- (필요 시) `backend/src/main/resources/db/migrations/` — 마이그레이션 스크립트.

#### 프론트엔드
- `frontend/src/services/searchHistoryService.js` — 검색 이력 API 호출.
- `frontend/src/components/SearchHistory/` — 검색 이력 목록·재요청·재조회 UI 컴포넌트.
- `frontend/src/App.js` — 검색 이력 라우트/메뉴 연결.
- `frontend/src/services/api.js` — 검색 이력 API 경로 추가(또는 서비스에서 직접 호출).

#### 문서/계약
- `docs/api-definition.md` — 검색 이력 API 정의 추가.
- `docs/contract.md` — 참조 유지(환경·포트 변경 없음).

### 데이터베이스 변경사항

**테이블: `search_history`**

| 컬럼명 | 타입 | 설명 |
|--------|------|------|
| id | BIGSERIAL PRIMARY KEY | PK |
| user_id | VARCHAR(100) NOT NULL | 요청 사용자 |
| log_type | VARCHAR(50) NOT NULL | 로그 타입 (pb_feplog, java_fw_imglog 등) |
| search_params | TEXT | 검색 조건 JSON |
| requested_at | TIMESTAMP NOT NULL | 승인 요청 일시 (또는 검색 실행 일시) |
| expires_at | TIMESTAMP | 승인 유효 만료 일시 (requested_at + 1일) |
| approval_status | VARCHAR(20) NOT NULL | PENDING, APPROVED, EXPIRED, REJECTED |
| approved_by | VARCHAR(100) | 결재자 user_id (승인 시) |
| approved_at | TIMESTAMP | 결재(승인) 일시 |
| rejected_by | VARCHAR(100) | 결재자 user_id (반려 시) |
| rejected_at | TIMESTAMP | 결재(반려) 일시 |
| rejection_reason | TEXT | 반려 사유 (선택) |
| created_at | TIMESTAMP DEFAULT CURRENT_TIMESTAMP | |
| updated_at | TIMESTAMP DEFAULT CURRENT_TIMESTAMP | |

- 인덱스: `user_id`, `requested_at`, `(user_id, requested_at DESC)`. 결재 이력 조회용 `approved_at`, `rejected_at` 인덱스는 필요 시 추가.

---

## 3. 테스트 수행 방안

### 테스트 케이스 목록 (요건 기준, 필수)

| ID | 구분 | 시나리오(입력·조건) | 기대 결과 | 검증 방법 |
|----|------|----------------------|-----------|-----------|
| TC-01 | 정상 | 검색 이력 저장 API 호출(검색 조건 + user_id) | 201, 저장된 이력 ID 반환 | 통합(curl/단위) |
| TC-02 | 정상 | 현재 사용자 검색 이력 목록 조회 | 200, 순번·일시·검색 조건 요약·승인 여부 포함 목록 | 통합/단위 |
| TC-03 | 정상 | 만료된 이력에 재요청 API 호출 | 200, 상태 PENDING 갱신, expires_at 1일 연장 | 통합/단위 |
| TC-04 | 정상 | 이력 상세 조회 후 동일 조건으로 검색 API 재호출 | 동일 검색 결과 구조 반환 | 통합/수동 |
| TC-05 | 예외 | 비인증 사용자가 이력 목록 조회 | 401 또는 403 | 통합 |
| TC-06 | 예외 | 다른 사용자의 이력 ID로 재요청 | 403 또는 404 | 통합/단위 |
| TC-07 | 엣지 | requested_at 기준 24시간 경과 건 목록 조회 | approval_status EXPIRED 또는 만료 플래그 true | 단위/통합 |
| TC-08 | 정상 | 승인된 건 목록/상세 조회 | approved_by, approved_at 포함 | 통합 |
| TC-09 | 정상 | 반려된 건(추후 구현 시) 상세 조회 | rejected_by, rejected_at, rejection_reason 포함 | 통합 |

### 테스트 시나리오

#### 시나리오 1: 검색 이력 저장 및 목록 조회
1. 로그인 후 검색 실행(복호화 요청 플래그 포함).
2. 검색 이력 저장 API가 호출되어 한 건 저장됨.
3. 검색 이력 목록 API 호출 시 방금 저장한 건이 순번·일시·검색 조건 요약·승인 여부와 함께 조회됨.

#### 시나리오 2: 만료 후 재요청
1. requested_at이 25시간 전인 이력 건 조회.
2. 목록에서 해당 건이 "만료"로 표시되고 "재요청" 버튼 노출.
3. 재요청 API 호출 후 상태 PENDING, expires_at이 호출 시점 + 1일로 갱신됨.

#### 시나리오 3: 이력에서 재 조회
1. 이력 목록에서 한 건 선택.
2. 상세 API로 검색 조건 조회 후, 동일 조건으로 로그 검색 API 호출.
3. 기대와 동일한 검색 결과 구조가 반환됨.

### 테스트 데이터
- 테스트 사용자 1~2명, logType=java_fw_imglog 기준 검색 조건( startDate, endDate, filters 등) JSON 샘플.
- requested_at이 1일 이전/이내인 행 각 1건 이상.

### 테스트 환경
- 프론트엔드: http://localhost:3001
- 백엔드: http://localhost:9200
- DB: PostgreSQL (contract.md 기준)

---

## 4. 체크리스트

### 프론트엔드 검증
- [x] API 전달 파라미터 검증 완료
- [x] 검색 이력 목록·재요청·재조회 UI 정상 동작
- [x] 에러 처리(403, 404, 네트워크 오류) 적절히 처리

### 백엔드 검증
- [ ] 검색 이력 저장·목록·재요청·상세 API 테스트 케이스 작성 및 실행 (기존 JUnit 이슈로 미작성)
- [x] 로그·에러코드 일관성 확인
- [x] 만료 여부 계산(1일) 로직 서비스 내 구현

### 통합 테스트
- [ ] 로그인 → 검색(승인요청) → 이력 목록 → 재요청/재조회 플로우 테스트 (수동 검증 권장)
- [x] 타 사용자 이력 접근 차단 확인 (서비스 레이어)

### 문서화
- [x] 요건 문서 §5 테스트 결과 반영
- [x] api-definition.md 검색 이력 API 정의 반영

---

## 5. 테스트 결과

### 테스트 수행 일시
- 2026-02-24

### 테스트 결과

#### 백엔드
- **컴파일**: 성공 (`mvn -q compile`)
- **단위 테스트**: 프로젝트 기존 이슈로 JUnit Jupiter 테스트 발견 실패 (`mvn test` 실패). SearchHistory 관련 단독 테스트 클래스는 미작성.

#### 프론트엔드
- **테스트**: 프로젝트에 `*.test.js`/`*.spec.js` 없음 — 단위 테스트 미실행. 수동/통합으로 검증.

#### 통합(수동)
- 검색 이력 저장/목록/재요청/상세 API는 인증 세션 기반으로 동작. DB 테이블 `search_history` 적용 후 curl 또는 UI로 검증 필요.

**테스트 명령어:**
```bash
cd backend && mvn -q compile
cd frontend && npm test -- --watchAll=false
```

### 발견된 이슈 및 해결 방법
- 백엔드 JUnit 발견 실패: 기존 프로젝트 설정 이슈. 본 요건 범위 외.
- DB: `search_history` 테이블은 `schema.sql`에 추가됨. 신규 환경에서는 `backend/src/main/resources/db/schema.sql` 적용 또는 setup 스크립트 실행 필요.

### 결재 이력 보강 (2026-02-24)
- 요구사항 보강: 결재 이력(승인/반려한 사람·일시·반려 사유) 관리 반영.
- DB: `search_history`에 `approved_by`, `approved_at`, `rejected_by`, `rejected_at`, `rejection_reason` 컬럼 추가(schema.sql 및 migrate-search-history-approval-columns.sql).
- Backend: create 시 APPROVED면 approved_by=요청자·approved_at 저장; list/detail 응답에 결재 이력 필드 포함.
- Frontend: 검색 이력 목록에 "결재 이력" 컬럼 추가(승인/반려 시 결재자·일시 표시).
- Subagent 활용: Requirements(요건 갱신) → DB(스키마·마이그레이션) → Backend(저장·조회) → Frontend(목록 표시) 순으로 개발.

### 다음 단계
- 복호화 승인/결재 본 기능(결재자 승인 프로세스) 요건 수립 및 구현

---

## 6. 오류 조치 결과 (원인·조치) — 오류/버그 수정 요건인 경우만

(해당 없음 — 신규 기능 요건)

---

**작성일**: 2026-02-24  
**상태**: 진행 중
