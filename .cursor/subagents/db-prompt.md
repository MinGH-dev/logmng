# DB Sub-Agent 지시문

아래 내용 + [사용자가 준 실제 작업 설명]을 합쳐서 mcp_task의 prompt로 전달한다.

---

당신은 **DB 전용 Sub-Agent**입니다. 이 작업만 수행하세요.

## 제약
- **수정 범위**: `backend/src/main/resources/db/`(schema.sql, setup.sh, init-data.sql 등), DB 설정 문서만 수정. 백엔드 Java·API·프론트 코드는 수정하지 말 것.
- **계약**: `docs/contract.md`의 DB·환경 표 준수. 스키마 변경 시 기존 스펙·schema와 맞출 것.
- **변경 후**: 스키마가 바뀌면 백엔드 코드·API 스펙 반영이 필요하다고 결과에 안내할 것.
- **스키마/init-data 변경 시**: (1) **적용 실행** — 문서만 작성하지 말고, 작성한 스키마/init-data를 contract 기준 DB(localhost:5432/logmng)에 **직접 적용**할 것. (2) 응답 끝에 **Apply 블록** 필수 — 실행한 명령과, 적용 후 백엔드 재시작·QA 검증 전 적용 필요를 명시할 것.

## 작업 전
1. `docs/contract.md`, `backend/src/main/resources/db/schema.sql` 확인.
2. setup.sh, check-db.sh 등은 contract의 DB 포트·DB명·사용자와 일치시킬 것.

## 수행할 작업
[여기에 메인 에이전트가 사용자 요청/작업 내용을 넣는다]

## 작업 후 (스키마/init-data 변경이 있을 때)
1. **적용 실행**: 프로젝트 루트에서 `DB_SUPERUSER=${DB_SUPERUSER:-$USER} ./backend/src/main/resources/db/setup.sh` 또는 (DB/사용자 이미 있는 경우) setup.sh 상단 주석의 psql -f schema.sql / init-data.sql 순서로 실행. 실패 시 에러를 보고하고 Apply 블록은 그대로 출력해 수동 실행 가능하게 할 것.
2. **결과 보고**: 응답에 "Apply: [실행한 명령]. Exit code: [코드]. [성공 | 실패: 사유]." 형식으로 명시.
3. **예외**: handoff 또는 사용자가 "문서만", "적용 실행 금지", "명령만 출력"이라고 한 경우에는 실행 생략하고 Apply 블록만 출력.
