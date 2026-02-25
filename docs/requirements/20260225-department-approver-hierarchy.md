# 20260225 - 부서별 결재자 지정 및 부서 계층(하이라키) 표시

## 1. 사용자 요건 내용

### 요건 설명
관리자 화면에서 **부서 단위로** 복호화 결재자를 지정할 수 있어야 한다. 부서는 **부서코드와 상위부서코드**로 **계층(하이라키)** 구조로 관리되며, 관리자 UI에서도 트리 형태로 표시된다. 기존 전역 결재자 지정 방식을 **부서별 결재자 지정**으로 확장한다.

### 사용자 시나리오
1. 관리자가 **부서 목록**을 **계층(트리)** 형태로 조회한다. (예: 본부 > 팀A > 팀A-1)
2. 관리자가 특정 **부서**를 선택하고, 해당 부서의 **결재자(들)**를 지정·변경·해제한다.
3. 지정된 결재자는 해당 부서(및 필요 시 하위 부서) 소속 사용자들의 복호화 승인 요청에 대해 승인/반려할 수 있다.
4. 부서 데이터는 **부서코드(code)** 와 **상위부서코드(parent_code)** 로 계층을 구성한다. 루트 부서는 parent_code가 NULL이다.
5. **문제**: 현재는 결재자가 사용자 단위로만 지정되어 있고, 부서 개념은 app_user.department_code로만 존재하며 부서 테이블·계층이 없다.

### 기대 결과
- **부서** 테이블이 도입되어 부서코드·상위부서코드·부서명 등으로 계층 구조가 유지된다.
- 관리자 화면에서 부서가 **트리(하이라키)** 로 표시되고, 부서별로 결재자를 지정/해제할 수 있다.
- 복호화 승인 권한 판단 시: 사용자 소속 부서(또는 상위 부서)에 지정된 결재자이면 승인/반려 가능 (기존 전역 결재자와의 호환 정책은 §2에서 정의).
- API: 부서 트리 조회, 부서별 결재자 목록/지정/해제가 계약·스펙에 반영된다.

---

## 2. 설계

### 2.1 보안 검토 (선택, 개인정보·복호화·접근통제 관련 시)
- [x] 보안 검토 수행 여부 (Security Subagent 검토 후 체크)

#### 검토 범위
- **PII**: 부서 코드·부서명은 단독으로는 개인정보에 해당하지 않으나, 조직 구조와 결재자·요청자 정보가 결합되면 소속 인원 추론 가능성이 있음.
- **접근 통제**: 부서 계층 조회/수정, 부서별 결재자 지정·조회 권한 주체 명확화.
- **복호화 범위**: 승인 권한이 부서(및 상위 부서) 단위로 제한되는지, 기존 "검색 이력 단위 승인" 정책과의 정합성.

#### 리스크
| 구분 | 리스크 | 심각도 |
|------|--------|--------|
| 접근통제 | 부서 트리·결재자 지정 API를 비관리자에게 열면 조직 구조·결재권 한계가 노출되고, 부서별 결재자 위·변조 가능 | 높음 |
| 정보 노출 | 결재자에게 **전체** 부서 트리를 허용하면, 승인 대기 목록(요청자 등)과 결합해 타 부서 인원·구조 추론 가능 | 중간 |
| 권한 판단 | 부서별 결재자 여부를 상위 부서까지 올바르게 조회하지 않으면, 권한 과다 부여 또는 정당한 결재자 차단 발생 | 중간 |
| 감사 | 부서 단위 결재자 지정·변경 이력이 없으면 책임 추적 불가 | 낮음 |

#### 보안 수용 기준 (Acceptance Criteria)
- **부서 계층 조회 (`GET /api/departments`)**
  - **관리자(ADMIN)**: 전체 부서 트리 조회 가능.
  - **결재자(USER, decrypt_approver 소속)**: 본인이 결재자로 지정된 부서(및 해당 부서의 하위 부서만)로 제한된 트리만 조회 가능하거나, 결재 업무에 불필요하면 부서 트리 API 자체는 관리자 전용으로 제한.
- **부서별 결재자 지정·해제**
  - `GET /api/departments/{code}/approvers`, `POST`, `DELETE` — **관리자 전용**. 비관리자 호출 시 403.
- **승인/반려 권한**
  - 요청자 소속 부서(또는 그 상위 부서)에 결재자로 등록된 사용자만 해당 검색 이력에 대해 승인/반려 가능. 전역 결재자(department_code NULL)는 기존처럼 허용.
- **감사**
  - 부서별 결재자 추가/삭제 이력은 감사 목적으로 로그 또는 이력 테이블에 남길 것을 권장(구현 범위는 별도 요건).

#### 설계 권고
1. **API 권한**
   - `GET /api/departments`: 관리자만 전체 트리 허용. 결재자에게는 "본인 결재 권한 부서 서브트리"만 반환하거나, UI에서 부서 트리 없이 "내 승인 대기 목록"만 제공하는 방식으로 최소 노출.
2. **결재자 지정**
   - 부서별 결재자 목록 조회·지정·해제는 모두 **관리자 전용**으로 구현하고, Contract/스펙에 403 조건 명시.
3. **복호화 범위**
   - 기존 정책 유지: 승인은 "검색 이력 단위(스냅샷)"이며, 부서별 결재자는 "누가 그 이력에 대해 승인/반려할 수 있는지"만 제한. 복호화 수행 범위는 기존과 동일하게 승인된 검색 이력에 한함.
4. **문서**
   - `docs/security-guide.md`에 "부서별 결재자: 관리자만 지정·조회, 부서 트리 노출은 관리자 전체·결재자 최소" 원칙을 추가할 것을 권장.

### 기술 설계

#### 문제 분석
1. 현재 `decrypt_approver`는 user_id만 저장하는 전역 결재자 목록이다. 부서 단위 지정이 불가능하다.
2. 부서 계층을 위한 테이블이 없고, `app_user.department_code`만 있어 상위부서 관계를 표현할 수 없다.
3. 관리자 UI에 부서 트리와 부서별 결재자 지정 화면이 없다.

#### 해결 방안

**데이터 모델**
- **department**: 부서 마스터. `code` (PK), `parent_code` (FK to department.code, NULL이면 루트), `name`, `sort_order`(선택). 계층 표현.
- **decrypt_approver**: 기존 테이블 확장 또는 유지. **부서별 결재자**를 위해 `department_code` 컬럼 추가. (user_id, department_code) 조합으로 부서별 지정. 기존 전역 결재자 호환: department_code NULL이면 기존처럼 전역 결재자로 간주할지, 또는 마이그레이션으로 부서별로 이전할지 §3 전에 확정.
- **app_user**: 기존 `department_code` 유지. FK to department(code) 권장.

**API (초안, Contract Subagent에서 확정)**
- `GET /api/departments` — 부서 트리(계층) 조회. 관리자·결재자 필요 시. 응답: 계층 구조(children 포함).
- `GET /api/departments/{code}/approvers` — 해당 부서 결재자 목록.
- `POST /api/departments/{code}/approvers` — 해당 부서에 결재자 추가. Body: `{ "userId": "user1" }`.
- `DELETE /api/departments/{code}/approvers/{userId}` — 해당 부서 결재자 해제.
- 기존 `GET /api/users`, `POST /api/users/approvers`, `DELETE /api/users/approvers/{userId}` 와의 관계: 부서별 API로 통합하거나 기존 API는 전역 결재자용으로 유지할지 Contract에서 정의.

**승인 권한 판단**
- 사용자 A가 승인/반려 API 호출 시: A가 ADMIN이면 허용; 그 외에는 A의 department_code에 해당하는 부서(또는 상위 부서)에 A가 결재자로 등록되어 있으면 허용. 전역 결재자(user_id만 등록)가 있으면 기존처럼 허용.

**프론트엔드**
- 관리자: 부서 트리 UI(접기/펼치기), 부서 노드 선택 시 해당 부서 결재자 목록·추가/삭제 UI.
- **UX 권고**(UX Subagent): 트리 role="tree"/treeitem, 접기/펼치기·들여쓰기·code+name 라벨, 선택 상태 명확화; 결재자 영역은 기존 사용자 관리 테이블·버튼 스타일과 일관; 접근성(aria-expanded, aria-selected, 키보드 탐색). 상세는 협업 시 UX 검토 노트 참고.

### 변경 파일 목록

#### 백엔드
- `backend/src/main/resources/db/schema.sql` — department 테이블 추가, decrypt_approver에 department_code 추가(또는 별도 department_approver 테이블).
- `backend/src/main/resources/db/init-data.sql` — department 초기 데이터, 기존 decrypt_approver 마이그레이션.
- `backend/.../controller/DepartmentController.java` (신규) — 부서 트리, 부서별 결재자 API.
- `backend/.../service/DepartmentService.java`, `DecryptApproverService.java` 수정 — 부서별 결재 로직.
- `backend/.../service/AuthService.java` 또는 결재 권한 판단 로직 — 부서별 결재자 여부 확인.

#### 프론트엔드
- 관리자 화면: 부서 트리 컴포넌트, 부서별 결재자 지정 패널(기존 사용자 목록·결재자 추가/해제 연동).

#### 문서
- `docs/contract.md`, `docs/api-definition.md` 또는 `specs/*.spec.yaml` — 부서·부서별 결재자 API 반영.

### 데이터베이스 변경사항

**테이블: department (신규)**
| 컬럼명 | 타입 | 설명 |
|--------|------|------|
| code | VARCHAR(50) PRIMARY KEY | 부서코드 |
| parent_code | VARCHAR(50) NULL | 상위부서코드, NULL이면 루트 |
| name | VARCHAR(200) | 부서명 |
| sort_order | INT DEFAULT 0 | 정렬 순서 |

**테이블: decrypt_approver 변경**
- 옵션 A: `department_code VARCHAR(50) NULL` 추가. (user_id, department_code) UNIQUE. department_code NULL = 전역 결재자(기존 호환).
- 옵션 B: 별도 `department_approver (department_code, user_id)` 테이블 신규, 기존 decrypt_approver는 전역만 유지.

**DBA 권고 요약** (DBA Subagent 검토 반영): **Option A** 채택 — `decrypt_approver`에 `department_code VARCHAR(50) NULL` 추가. UNIQUE는 partial unique index로 전역 1인 1행 + 부서별 (user_id, department_code) 유일. PK는 (user_id, department_code) 또는 대리키. department 테이블: parent_code FK, (parent_code, sort_order) 인덱스. app_user.department_code → FK to department(code). 상세: 요건 협업 시 DBA 설계 노트 참고.

---

## 3. 테스트 수행 방안

### 테스트 케이스 목록 (요건 기준, 필수)

| ID | 구분 | 시나리오(입력·조건) | 기대 결과 | 검증 방법 |
|----|------|----------------------|-----------|-----------|
| TC-01 | 정상 | GET /api/departments 호출(관리자) | 200, 계층 구조 JSON(children 포함, parent_code 관계) | 통합(curl) |
| TC-02 | 정상 | 특정 부서에 결재자 추가(관리자) — POST /api/departments/{code}/approvers | 201/200, 해당 부서 결재자 목록에 반영 | 통합 |
| TC-02b | 정상 | 특정 부서에서 결재자 해제(관리자) — DELETE /api/departments/{code}/approvers/{userId} | 204/200, 목록에서 제거 | 통합 |
| TC-03 | 정상 | 요청자 소속 부서(또는 상위 부서)에 결재자로 등록된 사용자가 해당 검색 이력에 대해 승인/반려 API 호출(canApproveForRequester) | 200, 승인/반려 처리됨 | 통합 |
| TC-04 | 예외 | 비관리자가 부서 결재자 지정·해제 API(GET/POST/DELETE .../approvers) 호출 | 403 | 통합 |
| TC-05 | 엣지 | parent_code로 순환 참조 없음 | 스키마/제약 또는 애플리케이션 검증 | 단위/스키마 |
| TC-06 | 예외 | 비관리자가 GET /api/departments 호출 | 403 | 통합 |

### 테스트 시나리오

#### 시나리오 1: 부서 계층 조회 및 표시
1. 관리자 로그인 후 부서 관리(또는 결재자 지정) 메뉴 진입.
2. 부서가 트리(접기/펼치기)로 표시되는지 확인.
3. 루트 부서와 하위 부서가 parent_code 관계로 올바르게 표시되는지 확인.

#### 시나리오 2: 부서별 결재자 지정
1. 관리자가 트리에서 특정 부서 선택.
2. 해당 부서 결재자 목록 조회, 사용자 추가/삭제.
3. 저장 후 해당 부서 결재자로 지정된 사용자로 로그인하여 승인 대기 목록에서 승인/반려 동작 확인.

### 테스트 환경
- 프론트엔드: http://localhost:3001
- 백엔드: http://localhost:9200
- DB: PostgreSQL (localhost:5432, logmng)

---

## 4. 체크리스트

**검증 안내**: 아래 항목 중 "부서 트리 API 연동 및 계층 표시", "부서별 결재자 목록·추가/해제 UI", "관리자 로그인 → 부서별 결재자 → 트리 + 결재자 지정" 등은 수동 또는 E2E로 확인 후 체크 가능.

### 프론트엔드 검증
- [ ] 부서 트리 API 연동 및 계층 표시 (수동/E2E: 관리자 → 부서별 결재자 메뉴 → 트리 표시 확인)
- [ ] 부서별 결재자 목록·추가/해제 UI 동작
- [ ] 에러 처리 및 권한(403) 처리

### 백엔드 검증
- [ ] 부서 트리·부서별 결재자 API 단위/통합 테스트
- [ ] 승인 권한 판단 로직(부서별 결재자) 테스트

### 통합 테스트
- [ ] 관리자 로그인 → 부서 트리 조회 → 부서별 결재자 지정 → 결재자 승인 플로우 E2E

### 문서화
- [ ] 요건 문서 §5 테스트 결과 기록
- [ ] API 정의·계약 반영

---

## 5. 테스트 결과

### 테스트 수행 일시
- 2026-02-25

### 테스트 결과

| 구분 | 일시 | 명령/방법 | 결과 | 비고 |
|------|------|-----------|------|------|
| 백엔드 단위 테스트 | 2026-02-25 | `cd backend && mvn test` | 성공 | SearchHistoryServiceTest, DecryptControllerTest 등; StubDecryptApproverService로 결재자 의존성 처리 |
| 검증(재시작·헬스) | 2026-02-25 | `./scripts/dev-services.sh backend restart` 후 `curl -s http://localhost:9200/api/health` | 200, success true, status OK | 재시작 성공, 헬스 체크 통과 |
| 검증(전체 재기동) | 2026-02-25 | `./scripts/dev-services.sh all restart` → 10s 대기 → 백엔드/프론트/DB 체크 | 통과 | Backend 9200 200 JSON, Frontend 3001 200, DB test connected |
| 수동 E2E(선택) | — | 관리자 로그인 → 부서별 결재자 메뉴 → 트리 표시 → 부서 선택 → 결재자 추가/해제 | 권장 | UI 연동·계층 표시·결재자 지정 플로우 확인 |

#### 백엔드 테스트 결과
- **성공**. `cd backend && mvn test` 통과.
- SearchHistoryServiceTest, DecryptControllerTest 등: StubDecryptApproverService 도입으로 결재자 의존성 처리.

#### 프론트엔드 테스트 결과
- 프로젝트에 `*.test.js`/`*.spec.js` 없음. 수동 확인.

#### 검증 (verify)
- Backend 재시작: `./scripts/dev-services.sh backend restart` 성공.
- Health check: `curl -s http://localhost:9200/api/health` → 200, `success: true`, `status: "OK"`.
- **전체 재기동 검증 (2026-02-25)**: `./scripts/dev-services.sh all restart` 후 Backend 9200 → 200 JSON, Frontend 3001 → 200, `GET /api/db/test` → connected. 모두 통과.

### 다음 단계
- 관리자 로그인 후 "부서별 결재자" 메뉴에서 부서 트리·부서별 결재자 지정 동작 수동 확인 권장.
- DB 신규 설치 시 `schema.sql`(department 테이블, decrypt_approver 확장) 및 `init-data.sql`(부서·전역 결재자 데이터) 적용 필요. 기존 DB는 decrypt_approver DROP 후 재생성으로 마이그레이션됨.

---

**작성일**: 2026-02-25  
**상태**: 진행 중
