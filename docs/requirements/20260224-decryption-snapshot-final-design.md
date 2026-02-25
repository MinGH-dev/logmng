# 복호화 승인 스냅샷 최종 설계안 (방안 1·방안 2 통합)

Architecture·DBA·Security 에이전트 협의를 통해 정리한 **단일 기준 최종안**입니다.  
구현 시 이 문서와 `docs/requirements/20260224-decryption-approval-snapshot-guide.md`를 함께 참고합니다.

---

## 1. 문서 역할

- **가이드 문서** (`20260224-decryption-approval-snapshot-guide.md`)의 **§6.5(고 QPS 대응)·§6.6(Architecture 최종 방안)** 은 **방안 1**에 대한 검토·결정입니다.
  - **방안 1**: 스냅샷 테이블에 **단일 컬럼 row_id(VARCHAR)** 만 사용. 단일 키는 그대로, 복합 키는 **직렬화 문자열**로 저장.
- 본 문서는 **방안 2(복합 키)** 를 대비한 설계를 포함해, **방안 1과 방안 2를 하나의 최종안으로 통합**한 결과입니다.
  - **방안 2**: 복합 키를 **row_key_json(JSONB)** 또는 **row_id_part1/part2/part3** 등 별도 구조로 저장하는 방식. 에이전트 협의 결과, **단일 코드 경로·확장성** 관점에서 아래 §2·§3와 같이 정리합니다.

---

## 2. 에이전트 합의 요약

| 관점 | 방안 2 대비 핵심 합의 |
|------|------------------------|
| **Architecture** | 단일 테이블·단일 컬럼(row_id) 유지 권장. 복합 키는 **직렬화(canonical JSON 문자열 등)** 로 row_id에 저장. 별도 row_key_json 컬럼 없이 Phase 1/2(캐시·배치 API)까지 동일 문자열로 통일. |
| **DBA** | 복합 키가 많아지면 **row_key_json(JSONB) 단일 컬럼**으로 통일하는 전환 권고. PK는 (search_history_id, log_type, row_key_json). md5(…) PK 비권장. 단일 키만 있으면 당분간 row_id(VARCHAR) 직렬화만으로도 가능. |
| **Security** | **서버 단일 소스**: 클라이언트는 **파트만 전달**, 서버가 직렬화/row_key_json 구성 후 스냅샷 비교. 클라이언트가 직렬화 문자열/JSON을 직접 보내는 설계 비권장. row_key_json 수신 시 키 화이트리스트·타입·길이 제한. 감사 로그는 **직렬화 문자열**로 기록. |

---

## 3. 최종 설계 결정 (방안 1 + 방안 2 대비)

### 3.1 스키마·저장 형식 (통합 결정)

- **현재·단기(Phase 1)**  
  - **단일 테이블, 단일 컬럼 row_id(VARCHAR)** 사용.  
  - 단일 키(예: java_fw_imglog의 guid): `row_id`에 값 그대로 저장.  
  - 복합 키(예: pb_feplog의 type+id): **log_type별 canonical 직렬화 규칙**으로 문자열 생성 후 `row_id`에 저장(예: `"send|123"`, 또는 canonical JSON 문자열).  
  - PK: `(search_history_id, log_type, row_id)`.  
  - 이렇게 하면 **방안 1**과 **방안 2 형태의 복합 키**를 모두 **row_id 한 컬럼 + 직렬화**로 처리할 수 있어, 단일 코드 경로·캐시·배치 API가 동일한 “row 식별자 문자열”로 동작함.

- **확장 시(복합 키 log_type 다수·DB에서 키 구조 활용 필요 시)**  
  - **row_key_json(JSONB) 단일 컬럼**으로 이전 검토.  
  - 단일 키도 `{"guid":"xxx"}` 형태로 JSONB에 저장하면, PK `(search_history_id, log_type, row_key_json)` 하나로 방안 1·2를 모두 표현 가능.  
  - DBA 권고: PK에 row_key_json 사용, md5(row_key_json::text) PK는 사용하지 않음.  
  - 이전 시점까지는 기존 row_id(VARCHAR) 직렬화 규칙과 **canonical 문자열**을 매핑해 두면, 캐시·배치 API·감사 로그는 기존과 동일한 “직렬화 문자열” 기준 유지 가능.

### 3.2 복호화·스냅샷 검사 (보안 불변식)

- **순서**: (1) `isValidApprovalForUser(searchHistoryId, userId)` → (2) 스냅샷 존재 여부(DB 또는 캐시). 변경 없음.  
- **클라이언트 요청**:  
  - **복합 키라도 클라이언트는 파트만 전달**(예: type, id).  
  - 서버가 log_type별 규칙으로 **직렬화 또는 row_key_json**을 생성한 뒤 스냅샷과 비교.  
  - 클라이언트가 “직렬화된 row_id” 또는 “row_key_json 객체 전체”를 직접 보내는 API는 **권장하지 않음**(조작·우회 위험).  
- **row_key_json 수신이 불가피한 경우**: 키 화이트리스트, 값 타입·길이·전체 크기 제한 적용. 가능하면 파트만 받고 서버에서 JSON 구성.

### 3.3 성능·Phase 2 (방안 1 §6.5·§6.6과 동일)

- **Phase 1**: 스냅샷 검사 DB만, 캐시 없음. PK·search_history_id 인덱스 확보. 복호화 지연·QPS·DB 부하 모니터링.  
- **Phase 2**: P95 지연 또는 QPS 기준 초과 시 searchHistoryId 단위 인메모리 캐시(승인된 row 식별자 집합, TTL ≤ approval expiry, 크기 상한). 필요 시 배치 검사 API.  
- **캐시·배치 API**: row 식별자는 **방안 1과 동일한 “canonical 문자열”**로 통일. 복합 키는 직렬화 문자열 또는 (row_key_json 도입 시) 동일 규칙의 canonical 문자열로 Set/배치 요청에 사용.

### 3.4 감사 로그

- 복합 키 포함해 **row 식별자는 직렬화 문자열**로 기록(searchHistoryId, logType, rowId, userId, timestamp 등).  
- 스냅샷 저장·복호화 검사와 동일한 직렬화 규칙을 사용해 추적성·검색성을 유지.

---

## 4. 구현 시 체크리스트 (최종안 반영)

- [ ] 스냅샷 테이블: **현재는** (search_history_id, log_type, row_id) PK. 복합 키는 log_type별 직렬화로 row_id 저장.  
- [ ] **확장 시**: row_key_json(JSONB) 전환 시 PK (search_history_id, log_type, row_key_json), 단일 키는 `{"guid":"x"}` 형태.  
- [ ] 직렬화/역직렬화: 공통 유틸 한 곳, 승인 스냅샷 저장·복호화 검사 **동일 규칙**.  
- [ ] 복호화 API: **파트만 수신**, 서버에서 직렬화(또는 row_key_json) 생성 후 스냅샷 조회.  
- [ ] 보안 불변식: 승인 검사 → 스냅샷 존재 여부 순서, 캐시 TTL ≤ approval expiry.  
- [ ] 감사 로그: row 식별자 = 직렬화 문자열.  
- [ ] Phase 2 캐시·배치 API: row 식별자 = canonical 문자열로 통일.

---

## 5. 참고

- 상세 흐름·스키마 예시·엣지 케이스: `docs/requirements/20260224-decryption-approval-snapshot-guide.md`  
- 방안 1 전용 고 QPS·Phase 1/2·배치 API: 동일 가이드 **§6.5, §6.6**  
- 결재자 지정·보안 검토: `docs/requirements/20260224-decryption-approver-designation.md`  
- 에이전트 역할: `docs/workflow/CURSOR-SUBAGENTS-DESIGN.md`  
- **개발·테스트 에이전트용 영문 설계(진입점·API·테스트 케이스 포함)**: `docs/requirements/20260224-decryption-snapshot-final-design-en.md`
