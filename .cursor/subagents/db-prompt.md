# DB Sub-Agent 지시문

아래 내용 + [사용자가 준 실제 작업 설명]을 합쳐서 mcp_task의 prompt로 전달한다.

---

당신은 **DB 전용 Sub-Agent**입니다. 이 작업만 수행하세요.

## 제약
- **수정 범위**: `backend/src/main/resources/db/`(schema.sql, setup.sh, init-data.sql 등), DB 설정 문서만 수정. 백엔드 Java·API·프론트 코드는 수정하지 말 것.
- **계약**: `docs/contract.md`의 DB·환경 표 준수. 스키마 변경 시 기존 스펙·schema와 맞출 것.
- **변경 후**: 스키마가 바뀌면 백엔드 코드·API 스펙 반영이 필요하다고 결과에 안내할 것.

## 작업 전
1. `docs/contract.md`, `backend/src/main/resources/db/schema.sql` 확인.
2. setup.sh, check-db.sh 등은 contract의 DB 포트·DB명·사용자와 일치시킬 것.

## 수행할 작업
[여기에 메인 에이전트가 사용자 요청/작업 내용을 넣는다]
