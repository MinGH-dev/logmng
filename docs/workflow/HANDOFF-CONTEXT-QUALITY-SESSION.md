# Handoff: Context quality and structural enforcement session

**Use this document** to resume work in a new main agent chat. Paste the "Prompt" section below as your first message.

---

## Current state

### What was done

Claude Code의 "팀 에이전트"(대등한 메인 에이전트 간 협업)와 Cursor의 "요구사항 기반 오케스트레이션"(메인 1개 + 서브에이전트 위임) 간 구조적 차이를 분석하고, **메인 에이전트의 규칙 우회(bypass) 방지** 및 **컨텍스트 품질 저하 완화**를 위한 규칙/명령/문서를 개선했다.

### Modified files (5)

| File | Change |
|------|--------|
| `.cursor/rules/agent-collaboration.mdc` | Step 1: 메인이 §1·§2·§3 작성 금지, Requirements만 작성. Gate: §3 확인 후 Step 4. Handoff: HANDOFF-CHECKLIST 준수. |
| `.cursor/rules/error-first-workflow.mdc` | Step 4 전 §1·§2·§3 완성 확인 추가. |
| `.cursor/rules/language-policy.mdc` | Requirements가 EN/KO 책임 명시. 중복 삭제. |
| `.cursor/commands/new-requirement.md` | 첫 지시를 "여기서 쓰지 말고 Requirements 호출"로 변경. |
| `docs/workflow/SUBAGENT-DELEGATION.md` | Review에 전체 요구사항 문서 전달 명시. prompt에 HANDOFF-CHECKLIST 참조 (분석 문서 참조 제거). |

### New files (5)

| File | Purpose |
|------|---------|
| `docs/workflow/CONTEXT-QUALITY-AND-ORCHESTRATION-MITIGATION.md` | 분석: 컨텍스트 bloat + wrong compression 원인, 완화 전략 §4.1–4.9 |
| `docs/workflow/HANDOFF-CHECKLIST.md` | 실행: Backend/Frontend/DB/Review/QA 핸드오프 시 필수 포함 항목 체크리스트 |
| `docs/workflow/IMPROVEMENT-STRUCTURAL-ENFORCEMENT.md` | 분석: 사용자 분석 기반 3전략(①권한제한 ②작성중협업 ③게이트키퍼) 구체화 + 체크리스트 8항목 Applied |
| `docs/workflow/ANALYSIS-requirement-authored-without-requirements-agent.md` | 분석: Requirements 에이전트 없이 문서 작성 시 병렬 검토 누락 원인 |
| `docs/workflow/PLAN-approval-only-group-tool-generalization.md` | (이전 세션) |

### Not yet done

- [ ] 변경사항 커밋 (워크플로 개선 관련 파일만)
- [ ] `REQUIREMENT_TEMPLATE.md`에 §2/§3 스코프 태그 가이드 추가 (§4.2)
- [ ] §4.4 핸드오프 포맷을 `.cursor/commands/`에 별도 명령으로 분리 (선택)
- [ ] §4.8 Requirements가 "Handoff per scope" 섹션을 자동 생성하도록 `requirements.md` 프롬프트 보강 (선택)
- [ ] 실제 요구사항으로 개선된 플로우 검증 (dry run)

---

## Prompt (새 채팅에 붙여넣기)

```
이전 세션에서 "컨텍스트 품질 저하 + 메인 에이전트 규칙 우회" 문제를 분석하고 규칙/명령/문서를 개선했어.

변경 내역은 `docs/workflow/HANDOFF-CONTEXT-QUALITY-SESSION.md`에 정리해놨으니 먼저 읽어줘.

이어서 해야 할 작업:
1. 변경사항을 커밋해줘 (워크플로 개선 관련 파일만: `.cursor/rules/`, `.cursor/commands/`, `docs/workflow/`의 수정·신규 파일).
2. `docs/template/REQUIREMENT_TEMPLATE.md`의 §2와 §3에 스코프 태그(Backend, Frontend, Integration 등) 가이드를 간단히 추가해줘. 이유는 `CONTEXT-QUALITY-AND-ORCHESTRATION-MITIGATION.md` §4.2에 있어.
3. 개선된 플로우가 제대로 동작하는지, 간단한 가상 요구사항으로 dry run 해봐줘. Requirements → Backend → QA 흐름만. 실제 코드 변경은 하지 말고 핸드오프 프롬프트만 만들어서 보여줘.
```

---

## Key documents (참조 우선순위)

| Priority | Document | Lines | Role |
|----------|----------|-------|------|
| **1 (규칙)** | `.cursor/rules/agent-collaboration.mdc` | 44 | alwaysApply — 위임 게이트, Step 1 금지, Gate, Handoff |
| **1 (규칙)** | `.cursor/rules/error-first-workflow.mdc` | 16 | alwaysApply — 에러 시 §1·§2·§3 확인 |
| **2 (실행)** | `docs/workflow/HANDOFF-CHECKLIST.md` | 56 | 핸드오프 시 필수 포함 항목 |
| **2 (실행)** | `.cursor/commands/new-requirement.md` | 9 | /new-requirement 진입점 |
| **3 (상세)** | `docs/workflow/SUBAGENT-DELEGATION.md` | 113 | Task 호출 매핑, 핸드오프 규칙 |
| **3 (상세)** | `docs/workflow/AGENT-COLLABORATION-ON-REQUIREMENT.md` | 150 | 협업 순서, §1.1 병렬 입력 |
| **4 (배경)** | `docs/workflow/CONTEXT-QUALITY-AND-ORCHESTRATION-MITIGATION.md` | 203 | 분석 전용 — 에이전트가 읽을 필요 없음 |
| **4 (배경)** | `docs/workflow/IMPROVEMENT-STRUCTURAL-ENFORCEMENT.md` | 144 | 분석 전용 — 에이전트가 읽을 필요 없음 |
