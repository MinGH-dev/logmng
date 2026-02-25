# 프로젝트 일관성 기준 (Consistency Standards)

**Owner**: Consistency 에이전트. 이 문서는 네이밍·구조·에러 형식·로깅 등 프로젝트 전반의 규칙을 정의한다. **Review** 에이전트는 변경 검토 시 이 문서를 기준으로 적용한다.

---

## 1. API·에러 응답

- **공통 형식**: `docs/api-definition.md` §1 및 `docs/contract.md` 준수. 성공 시 `success: true`, `data`. 실패 시 `success: false`, `error`, `code`.
- **에러 코드**: 새 코드 추가 시 `docs/api-definition.md`의 에러 코드 표에 등록. 대문자 스네이크 (예: `DECRYPTION_NOT_APPROVED`, `ROW_NOT_IN_APPROVED_SNAPSHOT`).
- **HTTP 상태**: 400 (클라이언트 오류), 403 (권한), 404 (없음), 500 (서버 오류) 등 의미에 맞게 사용.

---

## 2. 네이밍

- **API 경로**: 소문자, 케밥 또는 리소스 계층 (예: `/api/search-history/{id}/approve`).
- **DB**: 스네이크 (테이블·컬럼). 예: `search_history`, `approval_status`.
- **프론트**: 컴포넌트 PascalCase, 파일 컴포넌트명과 동일 또는 kebab. API 호출·상태는 camelCase.

---

## 3. 로깅

- **레벨**: ERROR(예외·실패), WARN(복구 가능), INFO(주요 흐름), DEBUG(상세).
- **PII**: 로그에 비밀번호·개인식별 정보 직접 출력 금지. `docs/security-guide.md` 참고.

---

## 4. 파일·디렉터리

- **요건 문서**: `docs/requirements/yyyyMMdd-name.md`. 8자리 날짜, 소문자, 하이픈.
- **백엔드**: `backend/src/main/java/com/logmng/` 하위 패키지 구조 유지. DB 리소스는 `backend/src/main/resources/db/`.

---

## 5. 갱신

- 규칙 추가·변경 시 **Consistency** 에이전트가 이 문서만 수정. **Review** 에이전트는 검토 시 이 문서를 참조한다.
