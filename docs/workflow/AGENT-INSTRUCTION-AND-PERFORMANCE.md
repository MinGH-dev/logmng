# 에이전트 지시문·성능 영향 정리

에이전트(LLM) 성능에 영향을 줄 수 있는 지시문·설정 요인을 정리한다. 참고: [moai-adk .claude](https://github.com/modu-ai/moai-adk/tree/main/.claude) 언어 정책 및 coding-standards.

---

## 1. 지시문 언어 vs 응답 언어 (영문 지시 + 사용자 언어 응답)

### moai-adk 방식

- **지시문(프롬프팅)**: **영문**. Agent 정의, Slash commands, Skill, Rules 등 모든 instruction 문서는 영문.
- **사용자 응답**: **사용자가 요청한 언어**. Conversation language에 맞춰 응답.

### 성능에 미치는 영향

- **지시문을 영문으로 두는 것이 유리한 이유**
  - 대부분의 LLM은 **지시 따르기(instruction following)** 학습 데이터가 영문 비중이 크다. 지시문을 영문으로 쓰면 의도 전달이 더 안정적일 수 있다.
  - **토크나이저**: 영문은 토큰 효율이 좋은 편이라, 같은 의미를 지시할 때 컨텍스트를 덜 잡아먹을 수 있다.
  - **혼합 언어**: 지시문(한국어) + 코드(영문) + 사용자 질문(한국어)이 섞이면 “어디까지가 지시, 어디부터가 대화” 경계가 흐려져, 지시 이탈 가능성이 늘어날 수 있다.
- **응답만 사용자 언어로 하는 경우**
  - “Respond in the user’s language” 한 줄이면 출력 언어만 제어된다. 모델 내부 추론/지시 이해는 영문 지시에 맞춰 두는 패턴이 일반적이다.
- **정리**: **지시문은 영문, 사용자에게 보이는 응답만 요청한 언어(예: 한국어)**로 두는 구성이 성능·일관성 측면에서 유리하다.  
  → 이 프로젝트에서는 `.cursor/rules/language-policy.mdc`로 이 원칙을 둔다.

---

## 2. 그 외 성능·동작에 영향을 줄 수 있는 유사 요인

아래는 같은 “지시/설정이 AI 동작에 미치는 영향” 관점에서의 요인들이다.

| 요인 | 설명 | 권장 |
|------|------|------|
| **지시문 언어** | 위 1과 동일. 지시는 영문, 응답만 사용자 언어. | rules/commands/skills/agents 본문은 영문 유지. |
| **alwaysApply 개수** | `alwaysApply: true`인 규칙이 많을수록 매 턴마다 컨텍스트에 포함됨. | 꼭 항상 필요한 것만 true, 나머지는 `paths`/조건 적용 또는 false. |
| **규칙·문서 길이** | 한 규칙/문서가 길수록 토큰 소비 증가, 핵심 지시가 묻힐 수 있음. | 요약 + 상세는 `docs/` 참조로 분리. (moai: CLAUDE.md 40k 제한 등) |
| **중복 지시** | 같은 내용을 여러 규칙/문서에 반복하면 토큰 낭비·충돌 가능. | 단일 출처 원칙. 반복 대신 “@file 또는 docs/ 경로” 참조. |
| **paths / 조건부 로딩** | 특정 경로·파일 타입일 때만 규칙 적용하면 해당 작업에만 집중. | backend/** 수정 시에만 backend 규칙 등. (Cursor가 paths 지원 시) |
| **지시문 내 금지/제한** | 예: 이모지 금지, “예상 소요 시간” 금지 → 환각·불필요 출력 감소. | moai coding-standards의 Content Restrictions 참고. |
| **파일 읽기 방식** | 큰 파일 전체 로딩 vs 구간/검색만 사용. | `file-reading-optimization.mdc` 참고. Grep → Read(offset, limit). |
| **도구 우선순위** | Read vs cat, Grep vs grep 등. 명시하면 일관된 도구 사용. | `core-principles.mdc`에 정의. |
| **출력 형식** | “XML 태그는 사용자에게 노출하지 말 것” 등. | 필요 시 output format 규칙으로 명시. |

---

## 3. 이 프로젝트에서의 반영

- **언어**: `.cursor/rules/language-policy.mdc` — 지시문은 영문, 사용자 응답은 요청한 언어.
- **항상 적용 규칙 수**: `alwaysApply: true`는 꼭 필요한 것만 유지. (docs-reference, contract-first, post-change-test-verify, error-first-workflow, core-principles, security-permissions 등)
- **길이·중복**: 규칙 본문은 짧게, 상세는 `docs/workflow/`, `docs/template/` 참조로 통일.
- **파일 읽기**: `file-reading-optimization.mdc`로 대용량 파일 시 토큰 절약 유도.

---

## 참고

- moai-adk: [.claude/rules/moai/development/coding-standards.md](https://github.com/modu-ai/moai-adk/blob/main/.claude/rules/moai/development/coding-standards.md) (Language Policy, File Size Limits, Content Restrictions, Duplicate Prevention)
- moai constitution: [.claude/rules/moai/core/moai-constitution.md](https://github.com/modu-ai/moai-adk/blob/main/.claude/rules/moai/core/moai-constitution.md) (Response Language)
