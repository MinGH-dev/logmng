# 20260224 - 복호화 승인 없이 복호화 차단

## 1. 사용자 요건 내용

### 요건 설명
복호화는 **승인된 경우에만** 허용해야 한다. 검색 이력에서 "복호화 승인 요청"을 하고, (테스트 시에는 즉시 승인으로) 승인된 뒤에만 개별 로우에 대한 "복호화" 버튼으로 복호화가 가능해야 한다.

### 문제(현재 동작)
- **현상**: 승인 요청을 하지 않았거나, 승인 대기/반려/만료 상태에서도 **복호화** 버튼을 클릭하면 복호화가 수행됨.
- **원인**: 백엔드 `POST /api/logs/decrypt/{logType}` 가 **승인 여부를 검사하지 않고** 요청만 받으면 복호화를 수행함. 프론트엔드도 승인 상태를 확인하지 않고 복호화 API를 호출함.

### 사용자 시나리오
1. 사용자가 로그 검색 후 "복호화 승인 요청"을 하지 않은 상태에서 테이블의 "복호화" 버튼을 클릭한다.
2. **기대**: 복호화가 수행되지 않고, "복호화 승인이 필요합니다" 안내가 표시된다.
3. 사용자가 "복호화 승인 요청"을 하고 (테스트 시 즉시 승인) 이력에 승인된 건이 생긴 뒤 "복호화" 버튼을 클릭한다.
4. **기대**: 복호화가 수행된다.

### 기대 결과
- 백엔드: 복호화 API 호출 시 **요청 본문의 searchHistoryId**가 현재 사용자 소유이고, APPROVED·미만료인지 검사. 없거나 불일치 시 **403** + `DECRYPTION_NOT_APPROVED`. ("이번 검색에 대한 승인"만 허용)
- 프론트엔드: "복호화 승인 요청" 성공 시 반환된 검색 이력 ID를 저장하고, 복호화 API 호출 시 `searchHistoryId`로 전달. 재조회 시 해당 이력 ID 전달. 새 검색 시 승인 ID 초기화.

---

## 2. 설계

### 기술 설계

#### 해결 방안
- **백엔드**: 복호화 요청 본문에 **searchHistoryId**(검색 이력 ID) 필수. `SearchHistoryService.isValidApprovalForUser(searchHistoryId, userId)` 로 해당 건이 본인 소유·APPROVED·미만료인지 검사. 통과 시에만 복호화 수행.
- **API**: `POST /api/logs/decrypt/{logType}` 요청 본문에 `guid`, `status`, **searchHistoryId**(Long) 포함. searchHistoryId 없거나 유효하지 않으면 403.
- **프론트엔드**: "복호화 승인 요청" 성공 시 응답 `data.id`를 현재 검색의 승인 ID로 저장. 검색 실행(새 검색) 시 해당 ID 초기화. 복호화 버튼 클릭 시 요청 본문에 `searchHistoryId` 포함. 검색 이력 재조회 시 해당 이력 ID를 LogGrid에 전달해 복호화에 사용.

### 변경 파일 목록
- **백엔드**: `DecryptController.java` (또는 새 DecryptionApprovalService / SearchHistoryService 활용), `docs/api-definition.md` 에 에러 코드 추가.
- **프론트엔드**: `ImageLogTable.js` — 복호화 API 실패 시 403 + DECRYPTION_NOT_APPROVED 처리 및 사용자 안내.

---

## 3. 테스트 수행 방안

| ID | 구분 | 시나리오 | 기대 결과 | 검증 방법 |
|----|------|----------|-----------|-----------|
| TC-01 | 정상 | 유효한 승인 이력 있는 사용자가 복호화 요청 | 200, 복호화 데이터 반환 | 통합/수동 |
| TC-02 | 예외 | 승인 이력 없음(또는 모두 만료/반려) 상태에서 복호화 요청 | 403, code DECRYPTION_NOT_APPROVED | 통합/수동 |
| TC-03 | UI | TC-02 상황에서 프론트에서 복호화 클릭 | 안내 메시지 표시, 복호화 미수행 | 수동 |

---

## 4. 체크리스트
- [ ] 백엔드: 복호화 전 승인 여부 검사 및 403 반환
- [ ] api-definition.md 에 DECRYPTION_NOT_APPROVED 문서화
- [ ] 프론트: 403 + DECRYPTION_NOT_APPROVED 시 안내 메시지

## 5. 테스트 결과
- **구현 일시**: 2026-02-24
- **Backend**: SearchHistoryService.hasValidApproval() 추가, DecryptController에서 복호화 전 승인 검사 및 403 DECRYPTION_NOT_APPROVED 반환.
- **Frontend**: ImageLogTable에서 403 + DECRYPTION_NOT_APPROVED 시 안내 메시지 표시, security.js에 메시지 상수 추가.
- **빌드·재시작**: mvn package -DskipTests 후 ./scripts/dev-services.sh backend restart 수행.

## 6. 오류 조치 결과
(해당 없음)
