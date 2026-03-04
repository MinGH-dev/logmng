# Cursor commands·subagents가 활용되지 않은 원인

에이전트가 재시작·검증 시 `.cursor/commands/*.md`를 사용하지 않고, 개발 시 subagents를 위임하지 않은 **원인** 정리.

---

## 1. 원인 요약

| 구분 | 원인 |
|------|------|
| **Commands 미활용** | 규칙에 재시작·검증 절차가 **인라인으로 중복** 기재되어 있어, 규칙만 따라도 요구사항을 충족함. "먼저 commands를 읽고 실행하라"는 **명시적 지시**가 없음. |
| **Subagents 미활용** | 사용자가 "subagent로 맡겨줘" 등 **위임 발화**를 하지 않음. 규칙은 "위임 시 mcp_task 실행"으로만 되어 있어, 에이전트가 스스로 위임할 **트리거**가 없음. |

---

## 2. Commands 미활용 상세 원인

### 2.1 규칙에 절차가 중복 기재됨

- **`post-change-test-verify.mdc`** 에 다음이 **그대로** 적혀 있음:
  - 재시작: `./scripts/dev-services.sh {frontend|backend|all} restart`
  - 확인: `curl -s http://localhost:9200/api/health` 등
- 에이전트는 **규칙 본문만 따르면** 재시작·검증을 수행할 수 있음.
- 규칙 하단 "참고"에 `.cursor/commands/verify.md`가 있지만, **"해당 파일을 읽고 그 절차를 따르라"**는 문구가 없어, commands를 열 필요가 없음.

### 2.2 "참조"만 있고 "사용하라"는 지시 없음

- `docs-reference.mdc`: "상세: verify.md" 수준의 **참고 링크**만 있음.
- `DEVELOPMENT_WORKFLOW.md`: "상세: `/verify` 커맨드"라고만 하고, **에이전트가 verify.md를 읽고 실행하라**는 문장이 없음.
- 따라서 에이전트 입장에서는 **같은 내용을 규칙/워크플로우에서 이미 보고 실행**하면 되고, commands는 "사용자가 슬래시로 호출하는 용도"로만 인식될 수 있음.

### 2.3 단일 진실(SSOT)이 규칙·워크플로우에 있음

- 재시작·검증의 **실제 절차**가 규칙·DEVELOPMENT_WORKFLOW에 기술되어 있어, **commands가 SSOT가 아님**.
- commands는 "사용자용 요약/커맨드 정의"로만 쓰이고, 에이전트 동작 규칙에서는 **commands를 필수 경로**로 지정하지 않음.

---

## 3. Subagents 미활용 상세 원인

### 3.1 위임 트리거가 사용자 발화에만 있음

- `.cursor/subagents/README.md`: "사용자가 '프론트 에이전트한테 맡겨줘' 등으로 **요청**할 때" 사용.
- 사용자가 "활동로그 통계 검증해줘", "재기동 후 확인해줘"만 했을 뿐, **subagent 위임**을 하지 않음.
- 에이전트가 "이 작업은 프론트만 수정했으니 프론트 subagent를 쓰자"라고 **스스로 판단**하라는 규칙도 없음.

### 3.2 Subagent 규칙이 비사용·선택 적용

- `subagent-invoke.mdc`: **alwaysApply: false**, 상단에 "**이 규칙은 사용하지 않습니다**" 표기.
- Cursor Settings Subagents 사용 권장으로 되어 있어, **에이전트가 mcp_task를 호출할 규칙**이 사실상 꺼져 있음.

---

## 4. 개선 방향 (요약)

- **Commands 활용**: 재시작·검증 시 **반드시** `.cursor/commands/verify.md`(또는 `restart-all.md`, `check-backend.md` 등)를 **읽고** 그 절차를 따르도록 규칙에 **명시**한다. 규칙 본문의 인라인 절차는 "요약"으로 두고, "상세는 해당 command 파일 참조 후 수행"으로 바꾼다.
- **Subagents 활용**: (선택) "프론트/백엔드/DB만 수정한 경우 해당 subagent에 위임하라"는 규칙을 추가하거나, 사용자가 위임 시에만 mcp_task를 쓰도록 명시를 유지한다.

이 문서는 원인 분석용이며, 구체적 규칙 수정은 `post-change-test-verify.mdc` 및 `docs-reference.mdc` 반영 내용을 따른다.
