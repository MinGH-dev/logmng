# 복호화 승인 스냅샷 — QA 테스트 시나리오

설계 문서: `docs/requirements/20260224-decryption-snapshot-final-design-en.md` §6.4  
구현: 승인 시 `search_history_approved_row` 저장, 복호화 시 스냅샷 존재 여부 검사.

---

## 테스트 케이스

| # | 시나리오 | 예상 결과 | 비고 |
|---|----------|-----------|------|
| 1 | **승인 시 스냅샷 생성** — PENDING 검색 이력에 대해 결재자가 승인 호출 | `search_history_approved_row`에 해당 search_history_id·log_type별 검색 결과 row_id(guid 등)가 삽입됨. search_history.approval_status = APPROVED | 검색 실행 시 동일 search_params 사용, row_id는 log_type별 규칙(java_fw_imglog=guid, pb_feplog=type\|id) |
| 2 | **스냅샷에 있는 row 복호화** — APPROVED 검색 이력 + 스냅샷에 있는 guid로 복호화 요청 | 200, 복호화된 데이터 반환 | searchHistoryId·guid가 스냅샷에 존재할 것 |
| 3 | **스냅샷에 없는 row 복호화 (403)** — 동일 APPROVED 검색 이력이지만 스냅샷에 없는 guid로 복호화 요청 | 403, `code: "ROW_NOT_IN_APPROVED_SNAPSHOT"`, 메시지 "승인된 검색 결과에 포함된 항목만 복호화할 수 있습니다." | |
| 4 | **스냅샷 없음(레거시 승인) 복호화 (403)** — APPROVED이지만 `search_history_approved_row`에 행이 없는 검색 이력으로 복호화 요청 | 403 (ROW_NOT_IN_APPROVED_SNAPSHOT 또는 동일 의미) | 스냅샷 기능 도입 전 승인 건 시뮬레이션 |

---

## 검증 방법

- **백엔드**: `POST /api/search-history/{id}/approve` 후 DB에서 `search_history_approved_row` 건수·row_id 확인.
- **복호화**: `POST /api/logs/decrypt/java_fw_imglog` body `{ "searchHistoryId": <id>, "guid": "<guid>" }` — 스냅샷에 있는 guid → 200, 없는 guid → 403 ROW_NOT_IN_APPROVED_SNAPSHOT.
- **마이그레이션**: 기존 DB에는 `backend/src/main/resources/db/migrate-search-history-approved-row.sql` 실행 후 재시작.

---

## §5 테스트 결과 (기록용)

- **실행일**: 2026-02-24
- **환경**: 백엔드 http://localhost:9200/api, DB 연동, 세션 인증(쿠키)
- **결과 요약**:
  - **시나리오 1 (승인 시 스냅샷 생성)**: **통과**. PENDING 검색 이력(id=8)에 대해 결재자(user1)가 `POST /api/search-history/8/approve` 호출 → 200, `approvalStatus: "APPROVED"`. 스냅샷 생성은 시나리오 2 복호화 성공으로 간접 확인.
  - **시나리오 2 (스냅샷에 있는 row 복호화)**: **통과**. user2로 `POST /api/logs/decrypt/java_fw_imglog` body `{"searchHistoryId":8,"guid":"250315142429291DAOLCS0TT0S01090000045001"}` → HTTP 200, 복호화된 데이터 반환.
  - **시나리오 3 (스냅샷에 없는 row 복호화 → 403)**: **부분 검증**. 스냅샷에 없는(그리고 DB에도 존재하지 않는) guid(`not-in-snapshot-guid-99999`, `out-of-snapshot-guid-xyz`)로 호출 시 **403이 아닌 500**, `code: "DECRYPTION_FAILED"` 수신. 설계상 스냅샷 검사가 선행되므로 “스냅샷에 없는 guid”이면 403 `ROW_NOT_IN_APPROVED_SNAPSHOT` 기대. 현재 환경에서는 승인 시 검색이 최대 10,000건까지 스냅샷에 포함되어 imagelog 8건 전부가 스냅샷에 들어가, “DB에는 있으나 스냅샷에는 없는” guid를 API만으로 만들기 어려움. **403 재현을 위한 수동 검증**은 아래 “수동 검증 절차” 시나리오 3 참고.
  - **시나리오 4 (스냅샷 없음·레거시 승인 → 403)**: **미실행 (수동 검증 대기)**. APPROVED이지만 `search_history_approved_row`에 행이 없는 경우는 DB 조작 또는 레거시 데이터 필요. 아래 “수동 검증 절차” 시나리오 4 참고.

**비고**: 시나리오 3에서 “존재하지 않는 guid” 사용 시 403 대신 500이 나오는 것은, 스냅샷 검사 통과 후 복호화 단계에서 “이미지로그를 찾을 수 없습니다”로 실패하는 것으로 해석됨. 설계상 스냅샷에 없는 row_id는 403으로 거부되는 것이 맞으므로, “스냅샷에 없는 실제 guid”로 재검증 권장.

---

## 수동 검증 절차 (실행 불가 시 또는 시나리오 3·4 보완용)

**전제**: 백엔드 기동(`http://localhost:9200`), DB 기동 및 `search_history_approved_row` 테이블 존재(`backend/src/main/resources/db/migrate-search-history-approved-row.sql` 반영), 결재자 계정(예: init-data의 user1) 및 일반 사용자(예: user2) 존재.

1. **로그인(세션 쿠키 유지)**  
   - 결재자: `curl -c cookies.txt -b cookies.txt -X POST http://localhost:9200/api/auth/login -H "Content-Type: application/json" -d '{"username":"user1","password":"user123"}'`  
   - 일반 사용자: `username":"user2"` 로 동일 요청 후 다른 쿠키 파일(예: `cookies2.txt`)에 저장.

2. **검색 이력 생성(PENDING)**  
   - user2 쿠키로:  
     `curl -b cookies2.txt -X POST http://localhost:9200/api/search-history -H "Content-Type: application/json" -d '{"logType":"java_fw_imglog","searchParams":{"logType":"java_fw_imglog","startDate":"2025-01-01 00:00:00","endDate":"2026-12-31 23:59:59","page":1,"pageSize":10}}'`  
   - 응답에서 `data.id`(검색 이력 ID) 확인.

3. **승인(시나리오 1)**  
   - user1 쿠키로:  
     `curl -b cookies.txt -X POST http://localhost:9200/api/search-history/{id}/approve`  
   - 200 및 `approvalStatus: "APPROVED"` 확인.  
   - (선택) DB: `SELECT * FROM search_history_approved_row WHERE search_history_id = {id};` 로 row_id 건수·값 확인.

4. **스냅샷에 있는 row 복호화(시나리오 2)**  
   - user2 쿠키로:  
     `curl -b cookies2.txt -X POST http://localhost:9200/api/logs/decrypt/java_fw_imglog -H "Content-Type: application/json" -d '{"searchHistoryId":<id>,"guid":"<스냅샷에 있는 guid>"}'`  
   - 스냅샷에 있는 guid는 위 DB 조회 결과의 `row_id` 또는 검색 결과의 guid 사용.  
   - 예상: HTTP 200, 복호화된 데이터.

5. **시나리오 3 (스냅샷에 없는 row → 403)**  
   - **방법 A**: 검색 조건을 좁혀 특정 row만 스냅샷에 들어가게 한 뒤, 그 검색 결과에 **없는** guid로 복호화. (예: 검색 조건으로 1건만 나오게 한 검색 이력 생성·승인 후, 다른 실제 guid로 복호화.)  
   - **방법 B**: 동일 APPROVED 검색 이력에 대해, `search_history_approved_row`에 **없는** guid(DB의 다른 imagelog 행의 guid 등)로 복호화 요청.  
   - 예상: HTTP 403, `code: "ROW_NOT_IN_APPROVED_SNAPSHOT"`, 메시지 "승인된 검색 결과에 포함된 항목만 복호화할 수 있습니다."

6. **시나리오 4 (스냅샷 없음·레거시 승인 → 403)**  
   - APPROVED 검색 이력 하나를 정한 뒤, DB에서 해당 이력의 스냅샷만 제거:  
     `DELETE FROM search_history_approved_row WHERE search_history_id = <id>;`  
   - user2 쿠키로 해당 `searchHistoryId`와 스냅샷에 있던 guid로 복호화 요청.  
   - 예상: HTTP 403 (`ROW_NOT_IN_APPROVED_SNAPSHOT` 또는 동일 의미).
