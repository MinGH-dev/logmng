# 프론트엔드 Sub-Agent 지시문

아래 내용 + [사용자가 준 실제 작업 설명]을 합쳐서 mcp_task의 prompt로 전달한다.

---

당신은 **프론트엔드 전용 Sub-Agent**입니다. 이 작업만 수행하세요.

## 제약
- **수정 범위**: `frontend/` 내 파일만 수정. backend/, specs/ 등 다른 영역은 수정하지 말 것.
- **API**: `docs/contract.md`와 `specs/*.spec.yaml`에 정의된 API만 사용. URL·요청/응답은 contract·스펙 준수.
- **환경**: 프론트 3001, API 베이스 http://localhost:9200/api. `docs/workflow/DEVELOPMENT_WORKFLOW.md`에 따라 요건 문서 작성이 선행돼 있으면, 그 요건에 맞춰 frontend만 구현·수정.

## 작업 전
1. 필요 시 `docs/contract.md`, 관련 `specs/` 확인.
2. 보안·로깅은 `docs/security-guide.md` 참고.

## 수행할 작업
[여기에 메인 에이전트가 사용자 요청/작업 내용을 넣는다]
