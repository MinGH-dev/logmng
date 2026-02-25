# Cursor 지정 MD 파일 간 관계 재검증 보고서

**검증 일시**: 2026-02-20  
**범위**: `.cursor/` 및 `docs/` 내에서 참조되는 모든 `.md`·경로

---

## 1. 정상 연결 (파일·디렉터리 존재)

| 참조 경로 | 실제 위치 | 비고 |
|-----------|-----------|------|
| `docs/contract.md` | docs/contract.md | ✓ |
| `docs/api-definition.md` | docs/api-definition.md | contract.md 내 상대 링크 (api-definition.md) 정상 |
| `docs/security-guide.md` | docs/security-guide.md | ✓ |
| `docs/QUICK_START.md` | docs/QUICK_START.md | ✓ |
| `docs/README.md` | docs/README.md | ✓ |
| `docs/workflow/WORKFLOW_CHECKLIST.md` | docs/workflow/WORKFLOW_CHECKLIST.md | ✓ |
| `docs/workflow/DEVELOPMENT_WORKFLOW.md` | docs/workflow/DEVELOPMENT_WORKFLOW.md | ✓ |
| `docs/workflow/ERROR-FIX-WORKFLOW-FLOWCHART.md` | docs/workflow/ERROR-FIX-WORKFLOW-FLOWCHART.md | ✓ |
| `docs/workflow/CURSOR-SUBAGENTS-DESIGN.md` | docs/workflow/CURSOR-SUBAGENTS-DESIGN.md | ✓ |
| `docs/workflow/WHY-TESTS-VERIFY-NOT-AUTO.md` | docs/workflow/WHY-TESTS-VERIFY-NOT-AUTO.md | ✓ |
| `docs/template/REQUIREMENT_TEMPLATE.md` | docs/template/REQUIREMENT_TEMPLATE.md | ✓ |
| `docs/template/BUGFIX_CHILD_TEMPLATE.md` | docs/template/BUGFIX_CHILD_TEMPLATE.md | ✓ |
| `docs/template/ERROR_FIX_RESULT_TEMPLATE.md` | docs/template/ERROR_FIX_RESULT_TEMPLATE.md | ✓ |
| `docs/template/AGENT_PROMPT_TEMPLATE.md` | docs/template/AGENT_PROMPT_TEMPLATE.md | ✓ |
| `docs/requirements/` | docs/requirements/ (36개 요건·테스트 결과 md) | ✓ |
| `docs/cursor-subagents/frontend.md` | docs/cursor-subagents/frontend.md | ✓ |
| `docs/cursor-subagents/backend.md` | docs/cursor-subagents/backend.md | ✓ |
| `docs/cursor-subagents/db.md` | docs/cursor-subagents/db.md | ✓ |
| `docs/cursor-subagents/README.md` | docs/cursor-subagents/README.md | ✓ |
| `docs/cursor-subagents/qa-test.md` | docs/cursor-subagents/qa-test.md | ✓ |
| `docs/cursor-subagents/contract-api.md` | docs/cursor-subagents/contract-api.md | ✓ |
| `docs/cursor-subagents/requirements.md` | docs/cursor-subagents/requirements.md | ✓ |
| `backend/DB_SETUP_GUIDE.md` | backend/DB_SETUP_GUIDE.md | ✓ |
| `backend/src/main/resources/db/schema.sql` | backend/src/main/resources/db/schema.sql | ✓ |
| `specs/*.spec.yaml` | specs/user-management.spec.yaml 등 | ✓ (워크스페이스 루트의 specs/) |
| `.cursor/commands/verify.md` | .cursor/commands/verify.md | ✓ |
| `.cursor/commands/run-tests.md` | .cursor/commands/run-tests.md | ✓ |
| `.cursor/subagents/*.md` | frontend-prompt, backend-prompt, db-prompt, README | ✓ |

---

## 2. 끊긴/불일치 관계 (수정 권장)

### 2.1 `dev/requirements/` vs `docs/requirements/`

| 구분 | 내용 |
|------|------|
| **문제** | 일부 문서는 요건 문서 경로를 **`dev/requirements/`** 로 가리키지만, 실제 요건 문서는 **`docs/requirements/`** 에만 존재함. **`dev/requirements/` 디렉터리는 없음.** |
| **참조 위치** | `docs/workflow/DEVELOPMENT_WORKFLOW.md` (다수), `.cursor/agents/Requirements.mdc`, `.cursor/skills/requirement-doc/SKILL.md`, `docs/cursor-subagents/requirements.md` |
| **영향** | 에이전트나 사용자가 "dev/requirements/ 에 요건 문서 작성"으로 읽으면, 실제로는 docs/requirements/ 에 작성해야 하므로 경로 불일치·혼선 발생. |
| **권장 조치** | **옵션 A**: `DEVELOPMENT_WORKFLOW.md` 등에서 `dev/requirements/` → `docs/requirements/` 로 통일. (현재 대부분의 규칙·커맨드는 이미 `docs/requirements/` 사용.) **옵션 B**: `dev/requirements/` 디렉터리를 만들고 docs/requirements/ 와 동기화하거나 심볼릭 링크로 연결. (단일 진실을 위해 옵션 A 권장.) |

### 2.2 `dev/specs/` vs `specs/`

| 구분 | 내용 |
|------|------|
| **문제** | `DEVELOPMENT_WORKFLOW.md`·Contract.mdc 등은 **`dev/specs/`** 를 참조. 실제 스펙 파일은 워크스페이스 루트의 **`specs/`** 에 있음. (워크스페이스가 이미 `dev` 이므로 `specs/` = `dev/specs/` 경로상 동일할 수 있으나, 문서에서는 "dev/specs/" 라고만 씀.) |
| **참조 위치** | `docs/workflow/DEVELOPMENT_WORKFLOW.md`, `.cursor/agents/Contract.mdc`, `.cursor/agents/Requirements.mdc`, `docs/cursor-subagents/contract-api.md`, `docs/cursor-subagents/requirements.md` |
| **영향** | 경로 표기가 혼재되어 있어, "dev/specs/" 와 "specs/" 가 같은지 헷갈릴 수 있음. |
| **권장 조치** | 워크스페이스 루트가 `dev` 이면 **`specs/`** 로 통일하거나, "dev/specs/ (즉, 루트의 specs/)" 라고 한 줄 주석으로 명시. |

### 2.3 `dev/docs/`

| 구분 | 내용 |
|------|------|
| **문제** | `DEVELOPMENT_WORKFLOW.md` 에서 "설계 문서: dev/docs/" 로 표기. 실제 설계·문서는 **`docs/`** 에 있음. (워크스페이스 루트가 dev 이면 `docs/` = `dev/docs/` 이지만, 다른 문서들은 전부 `docs/` 만 사용.) |
| **영향** | 낮음. 동일 디렉터리를 가리키나 표기만 불일치. |
| **권장 조치** | 문서 내에서 **`docs/`** 로 통일 표기. |

---

## 3. 요약

- **대부분의 MD·경로 관계는 정상**이며, `docs/`·`.cursor/`·`backend/`·`specs/` 참조는 실제 파일/디렉터리와 일치함.
- **끊긴 관계**는 다음 한 곳입니다.
  - **`dev/requirements/`** 를 참조하는 문서들이 있으나, 해당 디렉터리는 없고 요건 문서는 **`docs/requirements/`** 에만 있음.
- **표기 불일치**는 다음 두 곳입니다.
  - **`dev/specs/`** vs **`specs/`**
  - **`dev/docs/`** vs **`docs/`**

---

## 4. 적용한 수정 (2026-02-20)

- **DEVELOPMENT_WORKFLOW.md**: `dev/requirements/` → `docs/requirements/`, `dev/specs/` → `specs/`, `dev/docs/` → `docs/` 로 통일. 개발 디렉터리 구조를 실제 프로젝트(워크스페이스 루트 = dev)에 맞게 수정.
- **Requirements.mdc, Contract.mdc, requirement-doc SKILL, cursor-subagents/requirements.md, contract-api.md, CURSOR-SUBAGENTS-DESIGN.md**: `dev/requirements/`, `dev/specs/` 참조 제거 또는 `docs/requirements/`, `specs/` 로 통일.
- **DEVELOPMENT_WORKFLOW.md "참고 자료"**: 존재하지 않는 `GIT_BRANCH_STRATEGY.md`, `CODING_CONVENTIONS.md`, `TESTING_GUIDE.md` 링크를 제거하고, 같은 폴더의 `WORKFLOW_CHECKLIST.md`, `ERROR-FIX-WORKFLOW-FLOWCHART.md` 로 교체.

위 적용 후 Cursor에서 참조하는 MD 파일·경로 관계가 일관되게 유지됩니다.
