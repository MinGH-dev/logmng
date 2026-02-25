# Cursor 기본 Subagents용 프롬프트

이 폴더는 **Cursor Settings → Subagents**에서 사용할 프롬프트를 담습니다. custom sub-agent(mcp_task, run-*-agent)는 사용하지 않습니다.

## Subagent 6개 (개발에 필요한 에이전트)

| 파일 | Subagent 이름 | 담당 |
|------|----------------|------|
| `frontend.md` | Frontend | 프론트엔드 개발·요구사항 정리·테스트 |
| `backend.md` | Backend | 백엔드 개발·요구사항 정리·테스트 |
| `db.md` | DB | DB 스키마·마이그레이션·요구사항 정리·테스트 |
| `requirements.md` | Requirements | 요건·스펙 문서 작성·갱신 (코드 수정 없음) |
| `qa-test.md` | QA | 테스트 시나리오·검증 체크리스트·테스트 결과 기록 |
| `contract-api.md` | Contract | API·계약(contract.md, specs) 정의·갱신, 정합성 유지 |

## 사용 방법

1. Cursor **Settings → Subagents**에서 위 6개 이름으로 Subagent 생성.
2. 각 Subagent의 프롬프트(설명)란에 해당 파일 **내용 전체**를 복사해 붙여넣기.
3. 상세 설계·워크플로우: `docs/workflow/CURSOR-SUBAGENTS-DESIGN.md` 참고.
