# Same-case analysis: yyyyMMdd path pattern vs RequirementsPastSearch/TOPIC-INDEX

**Reference**: Backend 상세보기 "other docs"에 `docs/requirements/yyyyMMdd-name.md`가 경로 **패턴**(작성 규칙)인데, 요건 문서 **검색**은 RequirementsPastSearch + TOPIC-INDEX로 하는 것이 맞다는 분석.  
**Question**: Backend 외에 **동일 케이스**가 있는지 분석.

**Scope**: `.cursor/agents/*.mdc` (treemap에 사용되는 에이전트) 및 동일한 “경로 패턴이 other docs에 노출되지만, 검색은 RequirementsPastSearch/TOPIC-INDEX” 구조가 적용되는지 여부.

---

## 1. “동일 케이스” 정의

- **상황**: 에이전트 프롬프트에 `docs/requirements/yyyyMMdd-name.md`(또는 동일한 의미의 경로 패턴)가 **문자열/백틱**으로 있어, 트리맵이 해당 경로를 추출해 해당 에이전트 상세의 **other docs**에 노출함.
- **문제점**: 그 경로는 “**작성 규칙**”(새 요건 문서를 쓸 위치)일 뿐이고, “**기존 요건 문서 검색**”은 RequirementsPastSearch + TOPIC-INDEX로 하는 설계와 다름. 따라서 other docs에만 패턴이 보이면 “검색도 이 경로로 한다”로 오해할 수 있음.
- **동일 케이스**: 위와 같은 **경로 패턴 노출 + 검색은 실제로는 RequirementsPastSearch/TOPIC-INDEX** 구조가 있는 에이전트.

---

## 2. 에이전트별 정리 (`.cursor/agents/*.mdc`)

| Agent | 문구 (요건 문서 관련) | 추출 가능 ref | 동일 케이스 여부 |
|-------|------------------------|----------------|------------------|
| **Backend** | Write or update requirement docs in `docs/requirements/yyyyMMdd-name.md` | `docs/requirements/yyyyMMdd-name.md` → other | **예** |
| **DB** | Write or update requirement docs in `docs/requirements/yyyyMMdd-name.md` for schema, migrations, and data policy. | `docs/requirements/yyyyMMdd-name.md` → other | **예** |
| **Requirements** | Create or update docs in `docs/requirements/yyyyMMdd-name.md`; Output \| `docs/requirements/yyyyMMdd-name.md`; Filenames: `yyyyMMdd-name.md` | 동일 패턴 → other | **예** (작성 주체이자 검색 시 RequirementsPastSearch 호출) |
| **RequirementsPastSearch** | Requirement docs: `docs/requirements/` (naming `yyyyMMdd-name.md`); Topic index: `docs/requirements/TOPIC-INDEX.md` | `TOPIC-INDEX.md`, `yyyyMMdd-name.md` 등 → other | **예** (검색은 TOPIC-INDEX 명시, 패턴은 naming만) |
| **Security** | Review requirement docs (`docs/requirements/*.md`); Scope: `docs/requirements/` | `docs/requirements/*.md` 또는 디렉터리 | **아니오** (검토 범위 표시, “검색” 역할 아님) |
| **QA** | update ... in `docs/requirements/*.md`; Create `docs/requirements/{parentID}-bugfix-{N}.md` | glob/패턴 | **아니오** (갱신·버그픽스 자식 경로, 검색 아님) |
| **Documentation** | Does not write `docs/requirements/*` | 범위 제외 표현 | **아니오** |
| **Architecture**, **DBA** | 특정 문서만 참조 (예: `docs/requirements/20260224-decryption-approval-snapshot-guide.md`) | 구체 파일 경로 → other | **아니오** (특정 문서 참조, 패턴 아님) |
| **Release** | `docs/RELEASE_NOTES-yyyyMMdd-*.md` | 요건 문서 경로 아님 | **아니오** |
| **Frontend** | (agents/Frontend.mdc에 요건 경로/yyyyMMdd 없음) | - | **아니오** |
| **Backend-Auth**, **Backend-ActivityLog**, **Backend-Log** | “Write or update requirement docs only for X” (경로 없음) | 추출되는 경로 없음 | **아니오** |

---

## 3. 동일 케이스로 정리된 에이전트 (4개)

1. **Backend** — 작성 규칙으로 `docs/requirements/yyyyMMdd-name.md` 사용; 검색은 하지 않음. other docs에 패턴만 노출되면 동일 케이스.
2. **DB** — Backend와 동일. 작성 규칙만 있고 검색은 RequirementsPastSearch/TOPIC-INDEX.
3. **Requirements** — 요건 문서 **작성** 시 동일 경로 규칙 사용; **검색**은 REQUIREMENTS-AUTHORING-WORKFLOW에 따라 RequirementsPastSearch(TOPIC-INDEX) 호출. other docs에 패턴만 있으면 동일 케이스.
4. **RequirementsPastSearch** — 검색은 TOPIC-INDEX 명시; `yyyyMMdd-name.md`는 “naming” 설명. other docs에 naming 패턴만 강조되면 “검색도 이 패턴으로?”로 오해 가능하므로 동일 케이스로 봄.

---

## 4. 동일 케이스가 아닌 에이전트

- **Security, QA, Documentation**: 요건 문서 **경로/범위**만 언급(검토·갱신·제외), “검색” 역할 없음.
- **Architecture, DBA**: **특정** 요건 문서 경로만 참조, `yyyyMMdd-name` **패턴**이 아님.
- **Release**: 요건 문서가 아니라 릴리스 노트 경로.
- **Frontend, Backend-Auth, Backend-ActivityLog, Backend-Log**: `.cursor/agents` 쪽에는 `docs/requirements/yyyyMMdd-name.md` 같은 리터럴이 없어, 트리맵 other docs에 해당 패턴이 안 뜸 (동일 케이스 아님).

---

## 5. docs/cursor-subagents/*.md 참고

- **backend.md**, **frontend.md**, **db.md**, **backend-auth.md**, **backend-activity-log.md**, **backend-log.md**, **requirements.md** 등에는 “Write or update requirement docs in `docs/requirements/yyyyMMdd-name.md`”와 같은 문구가 있음.
- 트리맵의 **에이전트 ref는 `.cursor/agents/*.mdc`에서만** 추출하므로, cursor-subagents에만 있는 문구는 **현재 트리맵 other docs에 반영되지 않음**.  
- 다만, Cursor 설정에 붙여 넣는 **프롬프트**는 이 파일들이므로, “동일 케이스”의 **의미**(경로 패턴 = 작성 규칙, 검색 = RequirementsPastSearch/TOPIC-INDEX)는 subagents 문서에도 동일하게 적용됨. **표시** 문제는 에이전트 4개(Backend, DB, Requirements, RequirementsPastSearch)에 집중하면 됨.

---

## 6. 결론 및 트리맵 개선 방향

- **동일 케이스**: **Backend, DB, Requirements, RequirementsPastSearch** 4개.
- 이들에 대해서는 Backend와 같은 방식으로 보면 됨:  
  - **작성** = `docs/requirements/yyyyMMdd-name.md` 경로 규칙  
  - **검색** = RequirementsPastSearch + `docs/requirements/TOPIC-INDEX.md`  
- 트리맵 개선 시: 위 4개 에이전트 상세의 “other docs”에서  
  - 경로 **패턴**(`docs/requirements/yyyyMMdd-name.md`)만 두지 말고,  
  - **검색**을 나타내는 ref로 **TOPIC-INDEX.md** 및 (해당 시) **RequirementsPastSearch**를 함께 노출하거나, 패턴을 “작성 규칙”으로 라벨하는 방식으로 통일하면, “동일 케이스”가 모두 같은 원칙으로 보이게 됨.

**References**: ANALYSIS-requirement-doc-search-vs-path-pattern.md, REQUIREMENTS-AUTHORING-WORKFLOW.md, past-requirements-search.md, generate-treemap.js (scanAgents, extractRef, classifyRef).
