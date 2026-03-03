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

## ⚡ Cursor Skills & Commands

프로젝트에 등록된 Cursor 기능으로 빠르게 워크플로우를 적용할 수 있습니다.

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

### Subagents (Cursor 기본 기능만 사용)

- **Frontend / Backend / DB** 는 **Cursor Settings → Subagents**에서 생성하고, 프롬프트는 **docs/cursor-subagents/** 의 `frontend.md`, `backend.md`, `db.md` 를 복사해 넣습니다.
- 각 Subagent: **개발**, **요구사항 정리**, **테스트 자동화**를 담당 영역 안에서 수행.
- 설계·워크플로우: **docs/workflow/CURSOR-SUBAGENTS-DESIGN.md**
- 프로젝트 custom sub-agent(mcp_task, run-*-agent)는 **사용하지 않음**.

위 명령·스킬·규칙은 `.cursor/commands/`, `.cursor/skills/`, `.cursor/rules/`에 정의되어 있습니다.

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

**마지막 업데이트**: 2026-02-06





