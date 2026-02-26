# TERMINOLOGY 규칙 적용 시 실제 변경 리스트

[.cursor/TERMINOLOGY.md](TERMINOLOGY.md) **전체**에 따라 변경이 필요한 항목을 정리했습니다.  
§2 (네이밍 규칙) + §2.6 (역할: **수행자** vs **책임·설계(자)**) 기준.

---

## 변경될 리스트 (실행용 체크리스트)

아래 순서대로 적용하면 TERMINOLOGY 기준과 일치합니다. `[선택]` = 선택 적용.

### Commands · Legacy

- [ ] **삭제** `.cursor/commands/run-frontend-agent.md` (또는 내용을 Deprecated 문구만 유지)
- [ ] **삭제** `.cursor/commands/run-backend-agent.md` (동일)
- [ ] **삭제** `.cursor/commands/run-db-agent.md` (동일)
- [ ] **선택**: 위 3개 삭제 시 `.cursor/README.md`·`TERMINOLOGY.md` 에 "run-*-agent 제거됨; use Settings → Subagents" 문구 추가
- [ ] **옵션 A**: **삭제** `.cursor/subagents/` 폴더 전체 (frontend-prompt.md, backend-prompt.md, db-prompt.md, README.md)
- [ ] **옵션 B**: `.cursor/subagents/README.md` 상단에 `DEPRECATED: Use docs/cursor-subagents/*.md and Cursor Settings → Subagents.` 추가

### kebab-case

- [ ] **리네임** `docs/cursor-subagents/FRONTEND-IMPROVEMENT-POINTS.md` → `docs/cursor-subagents/frontend-improvement-points.md` [선택]
- [ ] **수정** `docs/workflow/CURSOR-SUBAGENTS-DESIGN.md` 내 `FRONTEND-IMPROVEMENT-POINTS.md` 링크 → `frontend-improvement-points.md` (위 리네임 시)

### 역할 라벨 (수행자 / 책임·설계) — 표·다이어그램

- [ ] **확인·유지** `README.md`: 다이어그램 노드 `DB · Schema`, `DBA · Review` 및 "DB vs DBA" 설명; 필요 시 **(수행자)** / **(책임·설계)** 라벨 추가
- [ ] **수정** `docs/workflow/CURSOR-SUBAGENTS-DESIGN.md`: 표에서 DB/DBA 행에 한 줄 역할 추가 (DB: schema, migrations (수행자); DBA: schema design review, no code (책임·설계))
- [ ] **수정** `docs/workflow/SUBAGENT-DELEGATION.md`: Step 3b DBA 행에 "(schema design review, no code)" 등 명시 (없을 경우)
- [ ] **확인·유지** `.cursor/agents/README.md`: DB.mdc / DBA.mdc 표에 **DB (Schema)** / **DBA (Review)** 표기
- [ ] **확인** `docs/workflow/AGENT-COLLABORATION-ON-REQUIREMENT.md`: 3b DBA 셀 "Design review; no code. DB implements." 유지
- [ ] **공통**: 표에 Contract/Review, Frontend/UX 등 쌍이 나올 때 역할 한 줄씩 붙이기 (수행자 vs 책임·설계)

### 에이전트 표시 이름 [선택]

- [ ] **DBA** → Cursor Settings·문서에서 표시 이름을 **Schema-Review** 또는 **DB-Review** 로 변경 (파일명 `DBA.mdc`·`dba.md` 유지 가능)
- [ ] **DBA 파일 통일 시**: `DBA.mdc` → `Schema-Review.mdc`, `dba.md` → `schema-review.md` 리네임 후 SUBAGENT-DELEGATION·CURSOR-SUBAGENTS-DESIGN 등 참조 수정

### 문서 반영

- [ ] `.cursor/README.md`: run-*-agent 제거·subagents legacy·역할(수행자/책임·설계) 기준 §2.6 참조 반영 (필요 시)
- [ ] `TERMINOLOGY.md`: run-*-agent 제거 시 "run-*-agent commands removed" 문구 추가 (필요 시)

---

## 1. 삭제 또는 Deprecated 처리 (Commands)

**규칙**: §2.2 — Command는 **agent-*** 만 "use this agent" 용도. **run-*-agent** 는 미사용(README 기준).

| 현재 경로 | 조치 | 사유 |
|-----------|------|------|
| `.cursor/commands/run-frontend-agent.md` | **삭제** 또는 Deprecated 문구만 유지 | run-*-agent 미사용; agent-frontend.md 와 역할 중복 |
| `.cursor/commands/run-backend-agent.md` | **삭제** 또는 Deprecated 문구만 유지 | 동일 |
| `.cursor/commands/run-db-agent.md` | **삭제** 또는 Deprecated 문구만 유지 | 동일 |

- **선택**: 세 파일 삭제 후 `.cursor/README.md`·`TERMINOLOGY.md` 에 "run-*-agent 제거됨; use Settings → Subagents" 명시.

---

## 2. 이름 변경 — kebab-case (규칙 §2.3, §2.4)

**규칙**: `docs/cursor-subagents/` 의 참고 문서는 **kebab-case.md**.

| 현재 경로 | 변경 후 | 비고 |
|-----------|---------|------|
| `docs/cursor-subagents/FRONTEND-IMPROVEMENT-POINTS.md` | `docs/cursor-subagents/frontend-improvement-points.md` | 참고 문서 선택 적용 |

- **참조 수정**: `docs/workflow/CURSOR-SUBAGENTS-DESIGN.md` 내 해당 링크를 `frontend-improvement-points.md` 로 변경.

---

## 3. Legacy 폴더 정리 (.cursor/subagents/)

**규칙**: §2.4 — Subagent prompt 는 `docs/cursor-subagents/*.md` 단일 위치. `.cursor/subagents/` 는 사용 중단.

| 항목 | 조치 | 사유 |
|------|------|------|
| `.cursor/subagents/` 전체 | **옵션 A**: 폴더 삭제 | 정규 프롬프트는 docs/cursor-subagents; 중복 제거 |
| | **옵션 B**: 유지 시 `subagents/README.md` 상단에 **"DEPRECATED: Use docs/cursor-subagents/*.md and Cursor Settings → Subagents."** 추가 | 삭제하지 않을 때 혼선 방지 |

- 1번에서 run-*-agent 삭제 시 옵션 A 선택하면 참조 끊김 없음.

---

## 4. 역할에 맞는 이름 제안 (규칙 §2.6 · Role confirmation)

**기준**: 구현(Implementation) vs 검토(Review-only) 구분 = **수행자** vs **책임·설계(자)** (TERMINOLOGY §2.6 역할 구분 표). 비슷한 이름은 표/라벨에서 역할 접미사로 구분(Option B). TERMINOLOGY "Role confirmation" 참고.

### 4.1 표·다이어그램에서 역할 라벨 일관 적용

다음 문서에서 **동일 도메인 쌍**이 나올 때마다 한 줄 역할을 붙입니다(§2.6 "Consistent listing").

| 문서/위치 | 적용할 라벨 (예시) | 비고 |
|-----------|--------------------|------|
| `README.md` (다이어그램·설명) | DB → **DB (Schema)** 또는 **DB · Schema**; DBA → **DBA (Review)** 또는 **DBA · Review**. 한국어 표기 시 **수행자** / **책임·설계** 사용 가능 (TERMINOLOGY §2.6) | 이미 반영된 경우 유지 |
| `docs/workflow/CURSOR-SUBAGENTS-DESIGN.md` (표) | DB: "schema, migrations (impl)"; DBA: "schema design review (no code)" | 표 설명에 한 줄 역할 추가 |
| `docs/workflow/SUBAGENT-DELEGATION.md` (표) | Step 3b DBA 행에 "(schema design review, no code)" 등 명시 | 필요 시 |
| `.cursor/agents/README.md` (표) | DB.mdc / DBA.mdc 행에 **DB (Schema)** / **DBA (Review)** 역할 표기 | 이미 반영된 경우 유지 |
| `docs/workflow/AGENT-COLLABORATION-ON-REQUIREMENT.md` (표) | 3b DBA: "Design review; no code. DB implements." 유지 | 확인만 |

- **추가 쌍** (§2.6 "Apply to other pairs"): Contract (spec 편집) vs Review (변경 검토), Frontend (코드) vs UX (디자인 검토) — 표에 나올 때 역할 한 줄씩 붙이기.

### 4.2 에이전트 표시 이름 — 역할이 이름에 드러나게 (선택)

**규칙**: TERMINOLOGY "Role confirmation" — DBA는 검토 전용이므로, 이름만 봐도 구분되게 하려면 표시 이름 변경 가능.

| 현재 (Cursor Settings·문서) | 제안 (역할에 맞는 이름) | 조치 | 비고 |
|-----------------------------|--------------------------|------|------|
| **DB** | 변경 없음 | — | 구현 전용으로 **DB** 적절 (§2.6 Role confirmation). |
| **DBA** | **Schema-Review** 또는 **DB-Review** | **선택**: Cursor Settings 표시 이름 + 문서/다이어그램에서 동일하게 사용 | "검토만"이 이름에 드러남. 파일명 `DBA.mdc`·`dba.md` 는 호환용 유지 가능. |
| **Architecture** | 변경 없음 또는 **Architecture (성능·공통화)** | 표/다이어그램에서만 라벨로 사용 가능 | 이미 README 등에서 "성능·공통화 검토"로 표기된 경우 유지. |
| **UX** | 변경 없음 | 표에서 **UX (design review)** 로만 통일 | 구현은 Frontend. |
| **Review** | 변경 없음 | 표에서 **Review (change review)** 로만 통일 | Contract/코드 검토. |

- **파일/프롬프트 경로**: DBA 표시 이름을 Schema-Review 등으로 바꿔도 `DBA.mdc`, `dba.md` 는 그대로 둘 수 있음(참조·mcp_task 등 호환). 통일하고 싶으면 `Schema-Review.mdc`, `schema-review.md` 로 리네임 후 문서·SUBAGENT-DELEGATION 등 참조 경로 수정.

### 4.3 요약

- **필수**: 표·다이어그램에서 **DB (Schema)** / **DBA (Review)** 등 역할 라벨 일관 적용(§4.1).
- **선택**: DBA 의 Cursor/문서 표시 이름을 **Schema-Review** 또는 **DB-Review** 로 변경(§4.2). 파일명은 유지 또는 schema-review 로 통일.

---

## 5. 규칙 준수 확인 — 변경 불필요

TERMINOLOGY 규칙을 이미 만족해 **변경하지 않아도 되는** 항목입니다.

| 유형 | 현재 상태 | 규칙 |
|------|-----------|------|
| **Rules** | `*.mdc` kebab-case; agent 전용 `*-agent.mdc` | §2.1 ✓ |
| **Commands** (run-*-agent 제외) | check-*, start-*, stop-*, restart-*, verify, run-tests, workflow, agent-* | §2.2 ✓ |
| **Skills** | 폴더 kebab-case, SKILL.md 1개 | §2.3 ✓ |
| **Agents** (정의·파일명) | PascalCase.mdc, prompt kebab-case.md | §2.4 ✓ |
| **Prompts** (FRONTEND-IMPROVEMENT-POINTS 제외) | kebab-case.md | §2.4 ✓ |
| **delegation-mgmt** | 제품 에이전트와 분리 | §2.4 ✓ |

---

## 6. 적용 순서 제안

1. **1번**: run-*-agent 커맨드 3개 삭제 또는 Deprecated 통일.
2. **3번**: `.cursor/subagents/` 삭제(옵션 A) 또는 README에 DEPRECATED 추가(옵션 B).
3. **2번**: FRONTEND-IMPROVEMENT-POINTS.md → frontend-improvement-points.md 리네임 및 참조 수정(선택).
4. **4.1번**: 표·다이어그램에 DB (Schema) / DBA (Review) 등 역할 라벨 일관 적용.
5. **4.2번 (선택)**: DBA 표시 이름을 Schema-Review 또는 DB-Review 로 변경 시, Cursor Settings·문서·필요 시 파일명 반영.
6. `.cursor/README.md`·`TERMINOLOGY.md` 에 run-*-agent 제거·subagents legacy·역할 라벨 기준(§2.6) 반영.

---

## 7. 참고

- 규칙 정의: [.cursor/TERMINOLOGY.md](TERMINOLOGY.md) (§2 네이밍, §2.6 메타기준·역할에 맞는 이름)
- 레이아웃·사용법: [.cursor/README.md](README.md)
- 위임·워크플로 연결: [docs/workflow/CURSOR-AND-TOOLS-INTEGRATION.md](../docs/workflow/CURSOR-AND-TOOLS-INTEGRATION.md)
- 서브에이전트 역할 경계: [docs/workflow/CURSOR-SUBAGENTS-DESIGN.md](../docs/workflow/CURSOR-SUBAGENTS-DESIGN.md) §2.6
