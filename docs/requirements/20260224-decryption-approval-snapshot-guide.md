# 승인 시점 스냅샷 구현 가이드

복호화 허용 범위를 **승인 시점의 검색 결과**로 제한하기 위한 설계·구현 가이드입니다.  
보안 검토 권고: `docs/requirements/20260224-decryption-approver-designation.md` §2.1 보안 검토 참고.

**§6.5·§6.6은 방안 1(단일 row_id/직렬화)에 대한 검토·최종 방안입니다.** 방안 2(복합 키) 대비 및 에이전트 협의 통합 최종안은 **`docs/requirements/20260224-decryption-snapshot-final-design.md`** 를 참고하세요.

---

## 1. 목표

- **현재**: `searchHistoryId`가 유효(본인 소유·APPROVED·미만료)이면 **동일 검색 조건에 걸리는 모든 행**에 대해 복호화 허용 → 승인 이후 추가된 로그까지 노출 가능.
- **목표**: **승인 시점에 조회된 행만** 복호화 가능하도록 제한.  
  → 승인 시점에 “그 검색 결과의 row 식별자 집합”을 저장하고, 복호화 시 해당 집합에 포함되는지 검사.

---

## 2. 전체 흐름

```
[요청자] 복호화 승인 요청 → search_history 생성 (PENDING, search_params 저장)
         ↓
[결재자] 승인 클릭 → ① search_params로 동일 검색 실행
                    ② 결과 집합의 row 식별자(guid 등) 수집
                    ③ 스냅샷 테이블에 저장
                    ④ search_history.APPROVED 갱신
         ↓
[요청자] 복호화 요청 (searchHistoryId + guid) → ④ 스냅샷에 (id, guid) 존재 여부 검사
                    ⑤ 있으면 복호화 수행, 없으면 403
```

---

## 3. DB 설계

### 3.1 스냅샷 테이블

승인 시점의 “검색 결과 row 식별자”만 저장. 로그 타입별로 row 식별 방식이 다름.  
**단일 컬럼 키**와 **복합 키** 모두 대비할 수 있도록 아래 두 가지 스키마 중 선택.

| log_type         | row 식별자 예시 | 단일/복합 | 비고 |
|------------------|------------------|-----------|------|
| java_fw_imglog   | `guid` (VARCHAR) | 단일 | 이미지로그 1건 = 1 guid |
| pb_feplog        | `send\|{id}` / `recv\|{id}` | 복합(직렬화) | 타입(send/recv) + id |
| (기타)           | (table, pk1, pk2, …) | 복합 가능 | 로그 타입별 직렬화 규칙 정의 |

**안 A: 단일 row_id 컬럼 (복합 키는 직렬화)**

복합 키인 경우 **한 문자열로 직렬화**해 `row_id` 한 컬럼에 저장. `log_type`별 직렬화 형식을 정해 두고, 승인 시 수집·복호화 시 검사에서 **동일 형식**으로 생성·비교한다.

```sql
-- 검색 이력 승인 시점 스냅샷 (복호화 허용 대상 row만)
CREATE TABLE search_history_approved_row (
    search_history_id BIGINT NOT NULL REFERENCES search_history(id) ON DELETE CASCADE,
    log_type         VARCHAR(50) NOT NULL,
    row_id           VARCHAR(512) NOT NULL,  -- 단일 키 또는 복합 키 직렬화 문자열 (형식은 log_type별 정의)
    PRIMARY KEY (search_history_id, log_type, row_id)
);
CREATE INDEX idx_search_history_approved_row_history ON search_history_approved_row(search_history_id);
```

- `row_id`: 단일 키면 그대로(예: guid), **복합 키면** 구분자로 결합한 문자열(예: `"send|123"`, `"k1:v1,k2:v2"`) 또는 URL-safe/base64 등. **같은 log_type 내에서 유일하고, 항상 같은 직렬화 규칙**을 쓸 것.

**안 B: 복합 키 전용 컬럼 (row_id_part1, row_id_part2, … 또는 row_key_json)**

복합 키를 컬럼으로 나누어 저장하고 싶을 때.

```sql
-- 예: 최대 3개 파트까지 지원하는 경우
CREATE TABLE search_history_approved_row (
    search_history_id BIGINT NOT NULL REFERENCES search_history(id) ON DELETE CASCADE,
    log_type         VARCHAR(50) NOT NULL,
    row_id_part1     VARCHAR(255) NOT NULL,   -- 필수 (예: type, guid)
    row_id_part2     VARCHAR(255) NULL,       -- 선택 (예: id)
    row_id_part3     VARCHAR(255) NULL,       -- 선택
    PRIMARY KEY (search_history_id, log_type, row_id_part1, row_id_part2, row_id_part3)
);
CREATE INDEX idx_search_history_approved_row_history ON search_history_approved_row(search_history_id);
```

- 복합 키가 3개를 넘거나 가변이면 **row_key_json JSONB** 한 컬럼로 저장하고, PK는 `(search_history_id, log_type, row_key_json)` 또는 `(search_history_id, log_type, md5(row_key_json::text))` 등으로 유일성 보장. 조회 시에는 `row_key_json @> incoming_json` 또는 동등 비교.

**선택 가이드**

- 키가 항상 1~2개 컬럼이고 형식이 단순하면 **안 A(직렬화)** 가 구현·인덱스가 단순함.
- 키 파트가 많거나, DB에서 파트별 조건으로 검색하고 싶으면 **안 B(복합 컬럼 또는 JSON)**.

### 3.2 DBA 검토 (row_key_json 방식)

DBA 관점 검토 결과: **row_key_json(JSONB) 방식은 본 용도(승인 스냅샷 row 존재 여부 exact match)에 적합**하다. PK는 `(search_history_id, log_type, row_key_json)`로 두고, 인덱스는 btree 기준. JSONB는 PostgreSQL에서 키 순서가 정규화되므로 키 이름·값만 일치하면 유일성 유지에 문제 없다. `md5(row_key_json::text)`를 PK에 쓸 경우에는 `row_key_json` 컬럼을 유지하고 조회 시 해시 비교 방식과 충돌 정책을 정해 두어야 한다. 스냅샷 행이 대량으로 늘어날 수 있으므로 `search_history_id` 인덱스 유지 및 장기적으로 파티셔닝 검토를 권장한다.

### 3.3 복합 키일 때 공통 규칙

- **직렬화 형식**: `log_type`별로 한 가지 형식을 정하고, “승인 스냅샷 저장”과 “복호화 시 검사”에서 **동일한 규칙**으로 생성·비교. (예: pb_feplog → `"{type}|{id}"`, 구분자 `|`는 값에 포함되지 않는다고 가정.)
- **유일성**: 서로 다른 row가 같은 `row_id`(또는 같은 part1/part2/part3 조합)로 저장되지 않도록 보장.
- **이스케이프**: 값 안에 구분자(`,`, `|`, `:`)가 들어갈 수 있으면 이스케이프 규칙을 정하고, 직렬화/역직렬화 시 일관 적용.
- **복호화 API**: 복합 키인 로그 타입은 API에서 여러 파라미터(예: `type`, `id`)를 받거나, 클라이언트가 직렬화한 한 문자열(예: `rowId=send|123`)을 보내도록 정한 뒤, 백엔드에서 스냅샷 조회 시 **같은 직렬화**로 변환해 비교.

---

**안 2: 대량 시 대체 (선택)**

- 건수가 매우 많을 때(예: 수만 건) 스냅샷 전체 저장이 부담되면, “결과 집합 해시 + 검색 조건”을 저장해 **동일 조건·동일 결과**일 때만 허용하는 방식 검토. (구현·성능·엣지 케이스는 별도 설계.)

---

## 4. 백엔드 구현 포인트

### 4.1 승인 시점에 스냅샷 수집·저장

**위치**: `SearchHistoryService.approve(Long id, String approverUserId)` 또는 별도 `ApprovalSnapshotService`.

**순서**:

1. `search_history`에서 해당 `id`의 `log_type`, `search_params` 조회 (이미 PENDING 검증 후).
2. `search_params`를 `LogDbSearchRequest` 또는 `AdvancedSearchRequest`로 변환 (JSON → DTO).  
   - 저장 시점에 프론트가 어떤 형태로 넣었는지에 맞춰 분기 (예: `filters`/`queryText` 있으면 Advanced).
3. **동일 검색 실행**
   - `LogDbService.searchLogs(request)` 또는 `advancedSearch(request)` 호출.
   - **전체 결과**를 받기 위해 `pageSize`를 충분히 크게 하거나, 페이지네이션으로 돌며 모든 row 수집.  
   - (선택) 상한 두기: 예) 최대 10,000건 초과 시 10,000건만 스냅샷에 넣거나, 초과 시 승인 실패 처리.
4. 결과 목록에서 **row 식별자**만 추출 (복합 키면 직렬화 규칙에 따라 한 문자열 또는 part1/part2/part3로 생성):
   - `java_fw_imglog`: 각 row의 `guid` (단일)
   - `pb_feplog`: `type`(send/recv) + `id` → `"send|"+id`, `"recv|"+id` (복합 → 직렬화)
   - 그 외 복합 키: `log_type`별 정의한 형식(예: `"k1:v1,k2:v2"`)으로 **항상 동일 규칙** 적용.
5. `search_history_approved_row`에 일괄 INSERT:  
   안 A면 `(search_history_id, log_type, row_id)`,  
   안 B면 `(search_history_id, log_type, row_id_part1, row_id_part2, row_id_part3)` 또는 `row_key_json`.
6. 기존처럼 `search_history`를 `APPROVED`로 UPDATE.

**트랜잭션**: 2~6을 한 트랜잭션으로 처리. 스냅샷 INSERT 실패 시 APPROVED로 바꾸지 않거나 롤백.

### 4.2 복호화 시 스냅샷 검사

**위치**: `SearchHistoryService` 또는 `DecryptController` 직전.

**기존**: `isValidApprovalForUser(searchHistoryId, userId)` 만으로 허용.

**변경**:

1. `isValidApprovalForUser(searchHistoryId, userId)` 로 “본인 소유·APPROVED·미만료” 확인 (유지).
2. **추가**: 요청한 row가 스냅샷에 존재하는지 조회.
   - **단일 row_id**: `(searchHistoryId, logType, rowId)` 로 조회.
   - **복합 키**: API에서 받은 파라미터(예: type, id)를 **스냅샷 저장 시와 동일한 직렬화 규칙**으로 `row_id` 문자열을 만들거나, 안 B면 `(part1, part2, part3)` 또는 `row_key_json`으로 조회.
3. 없으면 403 `DECRYPTION_NOT_APPROVED` (또는 `ROW_NOT_IN_APPROVED_SNAPSHOT` 등 별도 코드).

**인터페이스 예**:

- 단일 키: `boolean isRowInApprovedSnapshot(Long searchHistoryId, String logType, String rowId)`
- 복합 키(직렬화): 동일. 호출 전에 `logType`별 규칙으로 복합 키를 `rowId` 문자열로 직렬화해 전달.
- 복합 키(다중 컬럼): `boolean isRowInApprovedSnapshot(Long searchHistoryId, String logType, String part1, String part2, String part3)` 또는 Map/JSON 전달.

### 4.3 아키텍처 검토 (성능 고려)

복호화 시 스냅샷 조회는 PK `(search_history_id, log_type, row_id)`(또는 row_key_json) 기준 **단일 행 존재 여부** 검사이므로, 인덱스가 있으면 조회 비용이 작다. 단일 인스턴스·보통 수준의 복호화 QPS에서는 DB만으로 충분할 수 있으며, 복호화 지연·DB CPU를 모니터링하다가 부하가 커지면 (searchHistoryId, logType, rowId) 또는 searchHistoryId 단위 **인메모리 캐시와 TTL** 도입을 검토한다.

---

## 5. 로그 타입별 row_id 규칙 정리

| log_type         | 단일/복합 | 복호화 API에서 오는 식별자 | row_id 저장 값 (안 A) | 복합 시 part1/part2 (안 B) |
|------------------|-----------|----------------------------|------------------------|-----------------------------|
| java_fw_imglog   | 단일      | `guid`                     | `guid` 그대로          | part1=guid, part2/3 NULL   |
| pb_feplog        | 복합      | type + id                  | `"send\|123"`, `"recv\|456"` | part1=send/recv, part2=id  |
| (기타 복합 키)   | 복합      | (정의)                     | 구분자로 결합(예: `k1:v1,k2:v2`) | part1, part2, part3 또는 row_key_json |

**복합 키 직렬화 시 주의**
- 구분자(예: `|`, `,`, `:`)가 **값 안에 나오면** 이스케이프 규칙을 정해 충돌을 피할 것.
- 같은 `log_type` 내에서 서로 다른 row가 같은 문자열로 직렬화되지 않도록 보장.
- 직렬화/역직렬화는 공통 유틸(예: `RowIdCodec.forLogType(logType)`)로 두고, 승인 스냅샷 저장·복호화 검사 **둘 다** 그 유틸을 사용하면 일관성 유지에 유리함.

---

## 6. 엣지 케이스·정책

- **검색 결과 0건**: 스냅샷은 빈 집합. 승인은 가능하지만, 복호화 가능한 row가 없음 (정상).
- **검색 API 실패**: 승인 시점에 검색 실패 시 승인 자체를 실패 처리하고, 사용자에게 “승인 시 검색을 실행할 수 없어 승인에 실패했습니다” 등 안내.
- **대량 결과**: 상한(예: 10,000건) 초과 시 정책 결정 (전부 저장 vs 상한만 저장 vs 승인 거부).
- **재승인 없음**: 한 번 승인된 건의 스냅샷을 “나중에 다시 검색해서 갱신”하지 않음. 재승인하려면 재요청(새 search_history)으로 처리하는 것이 일관적.

---

## 6.5 고 QPS 대응 및 다학제 검토 (아키텍처·DBA·보안 합의)

키워드 검색·스냅샷 결과 내 재검색 시 복호화 클릭이 집중되면 **복호화 QPS가 크게 증가**할 수 있다. 아키텍처(성능)·DBA(스키마·쿼리)·보안(스냅샷 범위·감사) 관점을 종합한 합의 권고는 다음과 같다.

- **단기**: 스냅샷 검사는 DB 1 SELECT/복호화 유지. PK 및 `search_history_id` 인덱스를 확보하고, 복호화 지연·DB CPU·연결 수를 모니터링한다. 부하가 관찰되면 **searchHistoryId 단위 인메모리 캐시**(승인된 row_id 집합, TTL ≤ approval expiry, 크기 상한)를 도입한다.
- **중기(QPS 증가 시)**: **배치 검사 API**(searchHistoryId + row_id 목록 → 스냅샷 포함 여부 목록 반환)를 추가해 라운드트립을 줄이고, 필요 시 배치 결과 또는 searchHistoryId당 approved set을 짧은 TTL 캐시로 보조한다.
- **보안 불변식**: (1) 복호화 허용은 본인 소유·APPROVED·미만료 search_history 검사 **후** (2) 스냅샷 존재 여부(DB 또는 캐시)로만 판단. 캐시는 스냅샷 존재 검사만 반영하며 TTL은 approval expiry 이하로 두어 스냅샷 범위 우회가 불가능하도록 한다. 감사가 필요하면 복호화 수행 시 searchHistoryId·row_id·사용자·시각을 로깅한다.

---

## 6.6 최종 방안 (Architecture 결정)

아키텍처 에이전트가 단기/중기 옵션을 종합해 **구현의 단일 기준**으로 삼을 최종 방안을 아래와 같이 결정한다.

**우선 구현하는 것**: Phase 1에서는 **DB만 사용**한다. 복호화 요청마다 (1) `isValidApprovalForUser(searchHistoryId, userId)`로 본인·APPROVED·미만료 검사 후, (2) `search_history_approved_row`에 `(search_history_id, log_type, row_id)` 존재 여부를 PK로 1회 SELECT한다. 캐시는 Phase 1에 넣지 않는다.

**나중에 추가하는 것**: 복호화 P95 지연 또는 복호화 QPS가 정해진 기준을 넘으면 Phase 2로 전환해, (1) searchHistoryId 단위 인메모리 캐시(승인된 row_id 집합, TTL·크기 상한 적용)를 도입하고, (2) 필요 시 배치 검사 API를 제공한다.

**불변식**: 복호화 허용은 항상 "승인 검사(본인·APPROVED·미만료) → 스냅샷 존재 여부(DB 또는 캐시)" 순서로만 판단한다. 캐시는 스냅샷 존재 여부만 반영하며, TTL은 해당 search_history의 approval 만료 시각 이하로 둔다. 캐시 미스·만료 시에는 DB로 스냅샷 검사 후 필요 시 캐시를 보강한다.

### 단계별 구현 결정

- **Phase 1 (초기)**  
  - 스냅샷 검사: **DB만** 사용. 복호화 1건당 스냅샷 테이블 1 SELECT.  
  - 캐시: **도입하지 않음**.  
  - PK `(search_history_id, log_type, row_id)` 및 `search_history_id` 인덱스 확보.  
  - 복호화 지연(P50/P95), 복호화 QPS, DB CPU·연결 수를 모니터링할 수 있도록 준비.

- **Phase 2 (QPS 대응)**  
  - **도입 조건**: P95 복호화 지연이 **X ms 초과** 또는 복호화 QPS가 **Y 초과**가 일정 기간 유지될 때 (X, Y는 운영에서 정한 값, 예: P95 > 200ms 또는 QPS > 50).  
  - **캐시**: searchHistoryId 단위로 "승인된 row_id 집합"을 인메모리 캐시. TTL은 해당 search_history의 approval 만료 시각 이하.  
  - **배치 검사 API**: Phase 2에서 "한 번에 여러 row 스냅샷 포함 여부 조회"가 필요하다고 판단되면 배치 검사 API를 추가한다(형태는 아래 "배치 검사 API" 참고).

### 캐시 도입 조건 및 형태

- **도입 시점**: 위 Phase 2 조건(P95 > X ms 또는 QPS > Y) 충족 시.  
- **캐시 키**: `decrypt_snapshot:{searchHistoryId}` (또는 `decrypt_snapshot:{searchHistoryId}:{logType}` 로 logType별로 나눌 경우 동일 규칙으로 정의).  
- **캐시 값**: 해당 searchHistoryId(및 logType)에 대한 "승인된 row_id 집합"(Set 또는 동등 구조).  
- **TTL**: 해당 search_history의 approval 만료 시각까지 남은 시간, 단 **최대 24시간** 등 상한을 둔다. 만료 시각이 없거나 과거면 TTL 0(캐시하지 않음).  
- **크기 상한**: searchHistoryId당 캐시 entry 하나의 크기 상한(예: row_id 개수 최대 10,000 또는 바이트 상한)을 두고, 초과 시 해당 searchHistoryId는 캐시하지 않고 DB만 사용.  
- **캐시 무효화**: 스냅샷 테이블은 승인 시점 이후 갱신하지 않으므로, 별도 무효화는 하지 않고 **TTL 만료로만 제거**한다.

### 배치 검사 API

- **Phase 2에서 포함 여부**: 복호화 요청이 "동일 searchHistoryId에 대해 여러 row를 연속으로 검사"하는 패턴(예: 스냅샷 결과 내 재검색·키워드 검색 후 다건 복호화)이 많아지면 **Phase 2에서 배치 검사 API를 제공**한다.  
- **역할**: 클라이언트가 한 번에 (searchHistoryId + row_id 목록)을 보내면, 스냅샷 포함 여부만 목록으로 반환한다. 복호화 수행은 기존 복호화 API에서만 하며, 배치 API는 "포함 여부 조회 전용"이다.  
- **API 형태 (결정안)**  
  - **Method/Path**: `POST /api/search-history/{searchHistoryId}/approved-rows/check` (또는 contract에 맞게 `POST /api/decrypt/check-snapshot` 등으로 통일).  
  - **Request body**: `{ "logType": "java_fw_imglog", "rowIds": ["guid1", "guid2", ...] }`. 복합 키는 직렬화된 row_id 문자열 목록으로 전달.  
  - **Response**: `{ "results": [ { "rowId": "guid1", "approved": true }, { "rowId": "guid2", "approved": false }, ... ] }` 또는 `{ "approvedRowIds": ["guid1"], "rejectedRowIds": ["guid2"] }` 등 contract에서 정의한 단일 형식.  
  - **보안**: 배치 API 호출 전에 동일하게 `isValidApprovalForUser(searchHistoryId, userId)`를 수행하고, 통과한 경우에만 스냅샷 검사(DB/캐시)를 수행한다.  
- **프론트엔드 사용 시점**: 한 화면에서 **동일 searchHistoryId로 여러 row의 복호화 가능 여부를 미리 표시**하거나, "복호화 가능한 것만 일괄 복호화" 플로우를 만들 때 배치 검사 API를 사용한다. 단일 row 복호화 클릭은 기존대로 단건 복호화 API를 호출해도 된다.

---

## 7. 체크리스트 (구현 시)

- [ ] `search_history_approved_row` 테이블 및 인덱스 추가 (단일 row_id **또는** 복합 컬럼/row_key_json). PK는 (search_history_id, log_type, row_id) 또는 복합 키 조합.
- [ ] `log_type`별 **row 식별 규칙**(단일 vs 복합, 직렬화 형식) 문서화 및 공통 직렬화/역직렬화 유틸 적용.
- [ ] 승인 처리 시 `search_params`로 검색 실행 → row 식별자 수집(복합 키면 직렬화) → 스냅샷 INSERT (트랜잭션).
- [ ] 복호화 전 `isRowInApprovedSnapshot(…)` 검사 추가 (복합 키면 요청 파라미터를 동일 규칙으로 변환 후 비교).
- [ ] API/에러 코드: 스냅샷에 없을 때 403 및 메시지(예: “승인된 검색 결과에 포함된 항목만 복호화할 수 있습니다”).
- [ ] (선택) 감사 로그: 승인 시 스냅샷 건수, 복호화 시 searchHistoryId + row_id(또는 복합 키) 기록.

---

## 8. 참고

- 요구사항: `docs/requirements/20260224-decryption-approver-designation.md`
- 보안 검토: 동일 문서 §2.1 보안 검토
- 현재 복호화 검사: `SearchHistoryService.isValidApprovalForUser`, `DecryptController`
- 검색 서비스: `LogDbService.searchLogs`, `advancedSearch` (또는 해당 메서드명)

이 가이드를 기준으로 DB 스키마 추가 → 승인 시 스냅샷 저장 → 복호화 시 스냅샷 검사 순으로 구현하면, “승인 시점 스냅샷” 방식으로 복호화 범위를 제한할 수 있습니다.
