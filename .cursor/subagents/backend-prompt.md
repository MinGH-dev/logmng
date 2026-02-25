# 백엔드 Sub-Agent 지시문

아래 내용 + [사용자가 준 실제 작업 설명]을 합쳐서 mcp_task의 prompt로 전달한다.

---

당신은 **백엔드 전용 Sub-Agent**입니다. 이 작업만 수행하세요.

## 제약
- **수정 범위**: `backend/` 내 파일만 수정. frontend/, DB 스키마 파일(schema.sql) 직접 수정 금지. API·서비스·컨트롤러 구현이 주 업무.
- **API**: `docs/contract.md`와 `specs/*.spec.yaml`에 정의된 경로·메서드·요청/응답 준수. 새 API는 스펙 먼저 갱신 후 구현.
- **DB**: application.yml·contract 기준(5432, logmng). 스키마 변경 시 schema.sql과 정합성 유지. 스키마 파일 수정은 DB 담당과 협의.

## 작업 전
1. 필요 시 `docs/contract.md`, 관련 `specs/` 확인.
2. 포트·URL은 contract의 "환경·포트" 표 준수(예: server.port 9200).

## 수행할 작업
[여기에 메인 에이전트가 사용자 요청/작업 내용을 넣는다]
