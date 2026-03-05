# 개발 문서 가이드

## 📌 문서 구분

| 구분 | 위치 | 용도 |
|------|------|------|
| **개발 관련 문서** | **`docs/`** (이 디렉터리) | 요건·워크플로우·계약·템플릿·요건 문서 등 **개발 시 참조**하는 문서만 모음. |
| **서브에이전트 개선·위임 관리 문서** | **`.cursor/delegation-mgmt/`** | 위임 흐름 개선, 부작용·대응 분석, 점진적 개선 백로그·로그 등 **서브에이전트 위임 정책** 관련 문서. 개발 문서와 분리되어 관리. |

- `docs/`에는 개발용 문서만 두고, 서브에이전트 **개선용**·**관리용** 문서는 `delegation-mgmt/`에서만 추가·수정합니다.
- 위임 **실행** 시 참조하는 표·순서(예: `docs/workflow/SUBAGENT-DELEGATION.md`)는 개발 워크플로우의 일부로 `docs/`에 둡니다.

## 📚 문서 구조 (개발 관련만)

```
docs/
├── README.md (이 파일)
├── CURSOR-PROMPTING-GUIDE.md    # Cursor 프롬프팅 가이드 — 요청 예시·서브에이전트 활용 (한글)
├── contract.md                  # 공통 계약(API·DB·포트) — 에이전트가 항상 참고
├── QUICK_START.md               # 빠른 시작 가이드
├── security-guide.md            # 프론트엔드 보안 가이드
├── cursor-subagents/            # Cursor Settings Subagents용 프롬프트 (복사해 넣기)
│   ├── README.md
│   ├── frontend.md
│   ├── backend.md
│   ├── db.md
│   ├── requirements.md         # 요건·스펙 문서
│   ├── qa-test.md              # 테스트·검증
│   └── contract-api.md         # API·계약
├── workflow/
│   ├── WORKFLOW_CHECKLIST.md    # 순서·게이트만 (짧음, 규칙·스킬에서 참조)
│   ├── DEVELOPMENT_WORKFLOW.md  # 개발 워크플로우 상세 가이드
│   └── CURSOR-SUBAGENTS-DESIGN.md # Cursor 기본 Subagents 설계·사용법
├── template/
│   ├── AGENT_PROMPT_TEMPLATE.md  # Agent 프롬프트 템플릿
│   └── REQUIREMENT_TEMPLATE.md   # 요건 문서 템플릿
└── requirements/                # 요건 문서들
    ├── 20260206-image-log-datastring-search.md
    └── ...
```

## 🎯 처음 읽을 때 (진입 경로)

새 요건·버그 수정을 할 때는 **먼저 워크플로우**를 이해한 뒤 요청하세요.

1. **[WORKFLOW_CHECKLIST.md](workflow/WORKFLOW_CHECKLIST.md)** — 순서·게이트만 확인 (가장 먼저)
2. **(필요 시)** [DEVELOPMENT_WORKFLOW.md](workflow/DEVELOPMENT_WORKFLOW.md) — 상세·예시
3. **요청 문장이 막힐 때** [CURSOR-PROMPTING-GUIDE.md](CURSOR-PROMPTING-GUIDE.md) — 예시·서브에이전트 활용

위 순서를 확인한 뒤, 아래 "새로운 요건 요청 시" 절차를 사용하세요.

## 🚀 새로운 요건 요청 시

위 워크플로우를 확인한 뒤, 아래처럼 요청하세요.

### 1단계: Agent에게 프롬프트 제공

새로운 Agent 세션을 열고, `AGENT_PROMPT_TEMPLATE.md`의 내용을 복사하여 요청하세요.

**예시:**
```
다음 개발 워크플로우를 반드시 따라 작업해주세요:

1. docs/workflow/WORKFLOW_CHECKLIST.md로 순서·게이트를 확인하고, 상세는 docs/workflow/DEVELOPMENT_WORKFLOW.md를 참고하세요.
2. 요건 문서를 생성하세요: docs/requirements/yyyyMMdd-요건명.md
3. 개발 원칙을 준수하세요 (기존 코드 보존, 검증 필수)
...

현재 요건: [실제 요건 내용]
```

### 2단계: Agent가 따라야 할 체크리스트

Agent에게 명시적으로 다음을 요청하세요:

```
다음 순서대로 작업해주세요:
1. [ ] docs/workflow/DEVELOPMENT_WORKFLOW.md 읽기
2. [ ] 요건 문서 작성
3. [ ] 기존 코드 분석
4. [ ] 신규 코드 작성
5. [ ] 검증 수행
6. [ ] 문서 업데이트
```

### 3단계: 검증

Agent가 작업을 완료한 후:
- [ ] 요건 문서가 생성되었는지 확인
- [ ] 기존 코드가 보존되었는지 확인
- [ ] 검증이 수행되었는지 확인
- [ ] Git 브랜치가 생성되었는지 확인

## 📝 문서 작성 가이드

### 요건 문서 작성

1. `REQUIREMENT_TEMPLATE.md`를 복사
2. `docs/requirements/yyyyMMdd-요건명.md`로 저장
3. 템플릿 내용을 채워넣기

### 파일명 규칙

- 형식: `yyyyMMdd-요건명.md`
- 예시: `20260206-image-log-datastring-search.md`
- 요건명은 영문 소문자, 하이픈 사용

## ⚠️ 주의사항

### Agent가 자주 하는 실수

1. **기존 코드 직접 수정**
   - 해결: 명시적으로 "기존 파일을 복사하거나 주석 처리하세요"라고 요청

2. **검증 단계 건너뛰기**
   - 해결: "반드시 검증 단계를 수행하세요"라고 명시

3. **요건 문서 미작성**
   - 해결: "요건 문서를 먼저 작성하세요"라고 명시

4. **Git 브랜치 미사용**
   - 해결: "Git 브랜치를 생성하세요"라고 명시

## 🔍 문서 활용 방법

### 개발 시작 전
1. `workflow/DEVELOPMENT_WORKFLOW.md` 읽기
2. `template/AGENT_PROMPT_TEMPLATE.md`를 Agent에게 제공
3. `template/REQUIREMENT_TEMPLATE.md`를 참고하여 요건 문서 작성

### 개발 중
1. 체크리스트 확인
2. 검증 수행
3. 문제 발생 시 워크플로우 가이드 참고

### 개발 완료 후
1. 요건 문서 업데이트
2. 테스트 결과 기록
3. 체크리스트 완료 표시

## ⚡ 전체 도구 레이어

프로젝트는 **6개 레이어**의 도구가 협력합니다. 전체 흐름도는 [README.md (루트)](../README.md)의 "전체 도구 워크플로우" 참조.

> **인터랙티브 트리맵**: 규칙·스킬·명령어·워크플로우·서브에이전트의 동작 흐름과 문서 참조 관계를 시각적으로 확인 → [**라이브 페이지**](https://htmlpreview.github.io/?https://github.com/MinGH-dev/logmng/blob/feat/cursor-commit-on-complete/docs/cursor-tools-treemap.html) | [소스](cursor-tools-treemap.html)

| 레이어 | 위치 | 수량 | 역할 |
|--------|------|------|------|
| **Rules** | `.cursor/rules/` | 14종 | 자동 적용 제약·원칙·게이트 (보안, 위임 순서, 계약 우선 등) |
| **Commands** | `.cursor/commands/` | 31종 | 슬래시 커맨드 — 요건 시작, 검증, 서비스 제어, 상태 확인 |
| **Skills** | `.cursor/skills/` | 13종 | 도메인 지식 자동 로드 — 컨텍스트 기반 |
| **Agents** | `.cursor/agents/` | 21종 | 단계별 서브에이전트 위임 (요건→리뷰→구현→QA→릴리스) |
| **Scripts** | `scripts/` | 3종 | 서비스 관리, 문서 인덱스 생성, 미추적 문서 검출 |
| **MCP** | `.cursor/mcp.json` | 1종 | Browser Puppeteer — 프론트 UI 자동화 검증 |

**적용 범위**: 아래 규칙·커맨드·스킬은 **dev 워크스페이스 전용**입니다. Cursor에서 `dev` 폴더를 루트로 열었을 때만 적용되며, `~/.cursor/` 등 글로벌 설정으로 복사하지 마세요. (자세한 내용: `.cursor/README.md`)

### Slash Commands (채팅에서 `/` 입력)

| 명령 | 설명 |
|------|------|
| `/new-requirement` | 새 요건 시작 — 워크플로우 따라 요건 문서 작성 후 개발 |
| `/verify` | 검증 수행 — 프론트/백엔드 검증 체크리스트 실행 |
| `/follow-workflow` | 워크플로우 따르기 — DEVELOPMENT_WORKFLOW 기준으로 현재 작업 점검 |
| `/check-frontend-backend` | 프론트엔드·백엔드 정상 실행 여부 확인 (포트 3001, 9200) |
| `/check-frontend` | 프론트엔드만 실행 여부 확인 (포트 3001) |
| `/check-backend` | 백엔드 기동 + DB 연결 확인 (9200, /api/health, /api/db/test) |
| `/check-db` | DB 연결만 확인 (9200, /api/db/test, 백엔드 필요) |
| **서비스 제어** | |
| `/start-backend` | 백엔드 시작 (9200, jar 없으면 빌드) |
| `/stop-backend` | 백엔드 중지 |
| `/restart-backend` | 백엔드 재시작 |
| `/start-db` | DB(PostgreSQL, 5432, Homebrew postgresql@16) 시작 |
| `/stop-db` | DB 중지 |
| `/restart-db` | DB 재시작 |
| `/start-frontend` | 프론트엔드 시작 (3001) |
| `/stop-frontend` | 프론트엔드 중지 |
| `/restart-frontend` | 프론트엔드 재시작 |
| `/start-all` | DB·백엔드·프론트 모두 시작 |
| `/stop-all` | DB·백엔드·프론트 모두 중지 |
| `/restart-all` | DB·백엔드·프론트 모두 재시작 |

### Skills (자동 적용)

- **dev-workflow**: 새 요건/기능 시작 시 개발 워크플로우 단계 자동 참고
- **requirement-doc**: 요건 문서 작성 시 템플릿·파일명 규칙 적용
- **auth-permission-domain**: 권한·접근 제어·is_system_admin·permission group·화면 접근 관련 질문 시 사용. 계약·스펙(docs/contract.md, specs/permission-group-hierarchy.spec.yaml §4.3)과 일치.
- **search-history-decrypt-domain**: 검색 이력·복호화·승인·반려·결재자, DECRYPTION_NOT_APPROVED, ROW_NOT_IN_APPROVED_SNAPSHOT 관련 질문 시 사용.
- **error-codes-domain**: API 에러 코드(FORBIDDEN, DECRYPTION_NOT_APPROVED 등) 관련 질문 시 사용. api-definition §11 단일 소스.
- **department-approver-domain**: 부서·결재자 지정·decrypt_approver·user-permission-hierarchy 관련 질문 시 사용.
- **log-search-domain**: 로그 검색·logType·pb_feplog·imagelog 관련 질문 시 사용.
- **activity-statistics-domain**: 활동 이력·통계·scope(self/all) 관련 질문 시 사용.
- **ui-ux-domain**: 메뉴·화면·view·adminOnly·canAccessView 관련 질문 시 사용.
- **api-permission-map**: API 엔드포인트별 권한 검증 맵 — 모든 API → 컨트롤러 → 권한 체크 메서드 → 에러 코드. 권한 검증 테스트 계획·403 에러 매핑 시 사용.
- **db-domain**: PostgreSQL 스키마·마이그레이션·설정 스크립트. DB 스키마 설계·마이그레이션·setup.sh 관련 질문 시 사용.
- **test-workflow**: 테스트·검증 워크플로우 — §3 테스트 계획, 단위/통합 실행, §5 기록, 검증(재시작·헬스 체크) 관련 작업 시 사용.
- **react-debugging**: React 프론트엔드 디버깅 가이드.

### Agents (21종 — `.cursor/agents/`)

단계별 서브에이전트 위임으로 동작합니다. 메인 에이전트가 단계를 식별하고 해당 Agent에 위임합니다.

| Step | Agents | 역할 |
|------|--------|------|
| 1 | Requirements · RequirementsPastSearch | 요건 문서 §1·§2·§3 |
| 2–3 | Security · Contract · DBA · Architecture · Consistency · UX | 전문가 리뷰 (코드 수정 없음) |
| 4 | Frontend (Log·Auth·ActivityLog) · Backend (Log·Auth·ActivityLog) · DB | 구현 + 테스트 코드 작성 |
| 4.5 | Review (선택) | 코드 리뷰 |
| 5 | QA | 검증·§5/§6·커밋 |
| 6 | Documentation · Release | 문서·릴리스 |

- 설계·역할·경계: [CURSOR-SUBAGENTS-DESIGN.md](workflow/CURSOR-SUBAGENTS-DESIGN.md)
- 위임 표·매핑: [SUBAGENT-DELEGATION.md](workflow/SUBAGENT-DELEGATION.md)
- 프로젝트 custom sub-agent(mcp_task, run-*-agent)는 **사용하지 않음**.

### Rules (14종 — `.cursor/rules/`)

모든 채팅·서브에이전트에 자동 적용되는 제약·원칙입니다.

| 규칙 | 역할 |
|------|------|
| `core-principles` | 병렬 호출, 도구 우선순위, 품질 게이트 |
| `agent-collaboration` | 위임 순서·게이트, 테스트 코드 = Step 4 |
| `error-first-workflow` | 에러→요건 문서 먼저 |
| `security-permissions` | 위험 명령 차단 |
| `contract-first` | API·DB 변경 시 계약 먼저 |
| `language-policy` | 응답 한국어, 문서 영어 우선 |
| `workflow-todos` | Todo가 CHECKLIST 순서를 따름 |
| `post-change-test-verify` | 코드 변경 후 테스트·검증 자동 |
| `build-restart-handoff` | 빌드·재시작 핸드오프 |
| `file-reading-optimization` | 파일 크기별 단계적 로딩 |
| `frontend-agent` / `backend-agent` / `db-agent` | 구현 에이전트 스코프 제한 |

위 명령·스킬·규칙·에이전트는 `.cursor/commands/`, `.cursor/skills/`, `.cursor/rules/`, `.cursor/agents/`에 정의되어 있습니다.

## 📞 도움말

- **Cursor로 뭘 어떻게 요청할지**: [CURSOR-PROMPTING-GUIDE.md](CURSOR-PROMPTING-GUIDE.md) — 자주 쓰는 요청 예시·서브에이전트 활용
- 개발 워크플로우: `workflow/DEVELOPMENT_WORKFLOW.md`
- 오류 수정 요건 → 진행 흐름·Skills/Sub-Agent/Commands 시점: `workflow/ERROR-FIX-WORKFLOW-FLOWCHART.md`
- 빠른 시작: `QUICK_START.md`
- 보안 가이드: `security-guide.md`
- Agent 프롬프트: `template/AGENT_PROMPT_TEMPLATE.md`
- 요건 템플릿: `template/REQUIREMENT_TEMPLATE.md`
- Agent Skill·문서 개선 설계: [SKILL-DOCUMENT-IMPROVEMENT-DESIGN.md](SKILL-DOCUMENT-IMPROVEMENT-DESIGN.md) — 도메인 skill 도입·문서 분할 점진적 로드맵

---

## 🔄 최근 도구·워크플로우 변경 (2025-03-04)

- **테스트 코드 구현 필수화**: Step 4(Backend/Frontend) 구현 시 §3 TC에 대한 자동화 테스트 코드 작성이 필수. `WORKFLOW_CHECKLIST.md` Step 5도 "구현 + 실행"으로 업데이트.
- **요건 템플릿 Definition of Done**: `REQUIREMENT_TEMPLATE.md` §3에 "Mandatory automated tests" 절 추가.
- **에이전트 위임 명확화**: 테스트 코드 작성은 Step 4(Backend/Frontend 서브에이전트) 소관. `AGENT-COLLABORATION-ON-REQUIREMENT.md` Step 4에 명시.
- **새 Skills 4종 추가**: `api-permission-map`, `db-domain`, `test-workflow`, `react-debugging` (위 Skills 목록 참조).

---

**마지막 업데이트**: 2025-03-04





