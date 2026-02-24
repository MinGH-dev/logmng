# Cursor 기본 Subagents 사용 설계

이 프로젝트는 **Cursor Settings의 Subagents**만 사용한다. 프로젝트 내 custom sub-agent(mcp_task, run-*-agent, .cursor/subagents/)는 **사용하지 않는다**.

---

## 1. Subagent 9개 정의 (개발에 필요한 에이전트)

Cursor **Settings → Subagents**에서 아래 9개 Subagent를 생성하고, 각각 `docs/cursor-subagents/` 폴더의 해당 프롬프트를 **복사해 붙여넣기**한다.

| Subagent 이름 (Settings에 입력) | 용도 | 프롬프트 파일 |
|--------------------------------|------|----------------|
| **Frontend** | 프론트엔드 개발·요구사항 정리·테스트 | `frontend.md` |
| **Backend** | 백엔드 개발·요구사항 정리·테스트 | `backend.md` |
| **DB** | DB 스키마·마이그레이션·요구사항 정리·테스트 | `db.md` |
| **Requirements** | 요건·스펙 문서 작성·갱신 (코드 수정 없음) | `requirements.md` |
| **QA** | 테스트 시나리오·검증 체크리스트·테스트 결과 기록 | `qa-test.md` |
| **Contract** | API·계약(contract.md, specs) 정의·갱신, 정합성 유지 | `contract-api.md` |
| **Security** | 보안 검토(보안 책임자). 요구사항·설계의 개인정보·접근통제·복호화 범위 검토 (코드 수정 없음) | `security.md` |
| **DBA** | 스키마·설계 검토(DBA 관점). JSON/인덱스/성능 검토 (코드 수정 없음) | `dba.md` |
| **Architecture** | 아키텍처 검토. 성능·확장성·조회 부하 검토 (코드 수정 없음) | `architecture.md` |

### 1.1 Backend 모듈별 Subagent (선택, moai-adk 스타일 세분화)

백엔드가 커지면 **모듈/기능 단위**로 Subagent를 나누면 범위가 명확해지고, 수정 시 다른 영역을 건드릴 위험이 줄어든다.  
Cursor Settings에 아래 **선택** Subagent를 추가로 만들고, 해당 프롬프트를 붙여넣어 사용할 수 있다.

| Subagent 이름 | 담당 모듈/기능 | 프롬프트 파일 | 사용 시점 |
|---------------|----------------|---------------|-----------|
| **Backend** | 전체(공통)·범위 불명확 시 | `backend.md` | 일반 백엔드 작업, health/config, 여러 모듈에 걸친 변경 |
| **Backend-Auth** | 로그인·인증·인터셉터 | `backend-auth.md` | AuthController, AuthService, AuthInterceptor, 로그인/세션 관련 |
| **Backend-ActivityLog** | 활동 로그·통계·사용자 활동 | `backend-activity-log.md` | ActivityStatistics*, UserActivityLog*, ActivityLogAspect |
| **Backend-Log** | 로그 DB·검색·복호화·로그 타입 | `backend-log.md` | LogDb*, SearchSuggest*, Decrypt*, LogType* |

- **Backend** 하나만 써도 된다. 작업이 특정 모듈에만 있을 때 **Backend-Auth / Backend-ActivityLog / Backend-Log**를 쓰면, 해당 패키지·클래스만 수정하라는 지시가 프롬프트에 명시되어 일관성이 높아진다.
- 참고: [moai-adk .claude/agents](https://github.com/modu-ai/moai-adk/tree/main/.claude) — expert-backend는 도메인/플랫폼별 스킬(domain-backend, platform-auth 등)로 세분화되어 있음.

### 1.2 Frontend 모듈별 Subagent (선택, moai-adk 스타일 세분화)

프론트엔드도 **화면/기능 단위**로 Subagent를 나누면 수정 범위가 명확해진다. Cursor Settings에 아래 **선택** Subagent를 추가로 만들고, 해당 프롬프트를 붙여넣어 사용할 수 있다.

| Subagent 이름 | 담당 화면/기능 | 프롬프트 파일 | 사용 시점 |
|---------------|----------------|---------------|-----------|
| **Frontend** | 전체(공통)·범위 불명확 시 | `frontend.md` | App, 라우팅, api, 공용 컴포넌트, 여러 화면에 걸친 변경 |
| **Frontend-Auth** | 로그인·인증 UI | `frontend-auth.md` | LoginForm, 로그인 플로우, 인증 상태 표시 |
| **Frontend-ActivityLog** | 활동 통계·사용자 활동 로그 UI | `frontend-activity-log.md` | ActivityStatistics, UserActivityLog/*, Statistics* |
| **Frontend-Log** | 로그 검색·테이블·이미지 로그·로그 타입 UI | `frontend-log.md` | LogGrid, LogTable, ImageLog*, SearchForm, AdvancedSearchForm, LogTypeSelector |

- **Frontend** 하나만 써도 된다. 특정 화면/기능만 다룰 때 **Frontend-Auth / Frontend-ActivityLog / Frontend-Log**를 쓰면 해당 컴포넌트만 수정하라는 지시가 명확해진다.
- 참고: [moai-adk expert-frontend](https://github.com/modu-ai/moai-adk/blob/main/.claude/agents/moai/expert-frontend.md) — 스코프 경계, a11y, 성능, 테스트, 위임 프로토콜. 개선 포인트: `docs/cursor-subagents/FRONTEND-IMPROVEMENT-POINTS.md`.

### 1.3 추가 Subagent (검토·문서·릴리스·일관성·UX)

아래 5개는 협업·결과물 일관성 강화용이다. **역할 중복 방지**는 §2.6 참고.

| Subagent 이름 | 용도 | 프롬프트 파일 |
|---------------|------|----------------|
| **Review** | 코드/변경 검토(계약·워크플로우·품질·표준 적용). 코드 수정 없음. | `review.md` |
| **Documentation** | 사용자/운영 문서(README, QUICK_START, 배포, 런북). 요건 문서·API 스펙·코드 수정 없음. | `documentation.md` |
| **Release** | CHANGELOG, 버전, 릴리스 체크리스트. 사용자 가이드·코드 수정 없음. | `release.md` |
| **Consistency** | 표준 문서 정의·갱신(CONSISTENCY-STANDARDS.md). 검토 실행·코드 수정 없음. Review가 표준 적용. | `consistency.md` |
| **UX** | 디자인/UX 검토(a11y, UI 일관성, 디자인 시스템). 코드 구현 없음. Frontend가 구현. | `ux-design.md` |

---

## 2. 각 Subagent의 담당 범위

### 공통 (Frontend / Backend / DB)

- **요구사항 정리**: 담당 영역에 대한 요구사항·요건을 `docs/requirements/yyyyMMdd-요건명.md` 형식으로 정리하거나, 기존 요건 문서의 해당 섹션을 갱신.
- **테스트 자동화**: 담당 영역의 단위/통합 테스트 작성·실행·자동화 제안. (프론트: Jest/React Testing Library, 백엔드: JUnit/Mockito, DB: 스키마 검증/데이터 검증 스크립트.)
- **계약 준수**: `docs/contract.md`, `specs/*.spec.yaml` 참고. API·스키마 변경 시 스펙 선 반영.

### Frontend

- **개발**: `frontend/` 내 코드·설정만 수정. API 호출은 contract·스펙 기준. 보안·로깅은 `docs/security-guide.md` 참고.
- **요구사항**: UI/UX·화면·프론트 이슈 관련 요건 문서 작성·갱신.
- **테스트**: 프론트 단위/컴포넌트 테스트, E2E 관련 시나리오 제안.

### Backend

- **개발**: `backend/` 내 코드·설정만 수정. API는 스펙·contract 기준. DB 접근은 schema.sql·contract와 정합성 유지.
- **요구사항**: API·비즈니스 로직·백엔드 이슈 관련 요건 문서 작성·갱신.
- **테스트**: API·서비스 단위 테스트, 통합 테스트, curl/스크립트 자동화 제안.

### DB

- **개발**: `backend/src/main/resources/db/`(schema.sql, setup.sh, 마이그레이션 등) 및 DB 설정 문서만 수정.
- **요구사항**: 스키마·마이그레이션·데이터 정책 관련 요건 문서 작성·갱신.
- **테스트**: 스키마 검증, 초기 데이터 검증, setup/check 스크립트 자동화.

### Requirements (요건·스펙)

- **문서만**: `docs/requirements/`, `specs/`, 요건·스펙 템플릿 관련 문서 작성·갱신. **코드는 수정하지 않음.**
- **요건 문서**: 사용자 요구사항(What/Why), 시나리오, 기대 결과, 체크리스트·테스트 결과 섹션 유지.
- **스펙 문서**: 복잡한 기능의 API·데이터·UI 설계를 요건과 정합되게 기술.

### QA (테스트·검증)

- **테스트 설계**: 테스트 케이스(정상·예외·엣지), E2E·회귀 시나리오 제안.
- **검증 체크리스트**: 워크플로우 기반 체크리스트 제안, 요건 문서의 체크리스트·테스트 결과 섹션 보완.
- **자동화 제안**: `/check-*`, `/verify` 활용 방법, CI·테스트 자동화 제안. (테스트 코드 작성은 Frontend/Backend/DB가 수행.)

### Contract (API·계약)

- **계약·스펙**: `docs/contract.md`, `specs/*.spec.yaml` 유지. API·환경·포트 단일 진실. API 추가/변경 시 **스펙 먼저** 작성.
- **정합성**: contract·스펙과 구현이 맞는지 점검 제안. **코드는 수정하지 않음** — Frontend/Backend가 스펙을 따라 구현.

### Security (보안 검토·보안 책임자)

- **보안 검토**: 요구사항 문서(§1·§2) 및 설계를 개인정보(PII)·접근 통제·복호화 범위·감사 로그 관점에서 검토. **§2.1 보안 검토** 또는 보안 검토 부록 제안·작성 (위험, 수용 기준, 설계 권고).
- **설계 권고**: 검토된 대로 설계·개발이 이루어지도록 권고안 제시 (예: 복호화 허용 범위를 승인 시점 결과 스냅샷으로 제한할지 여부). **코드는 수정하지 않음** — Requirements/Contract/Backend/Frontend가 반영.
- **가이드**: `docs/security-guide.md` 보완 제안. 사용 시점: 개인정보·복호화·접근통제 관련 요건 정의 시, 구현 전 설계 단계.

### DBA (스키마·설계 검토)

- **스키마 설계 검토**: 제안·기존 테이블에 대해 PK/인덱스·데이터 타입·제약·증가량 관점 검토.
- **JSON vs 관계형**: JSONB(예: row_key_json) 사용 시 조회 패턴·인덱스 가능성·유일성·저장량 검토 및 복합 컬럼 대비 권고.
- **성능·운영**: 백업/복구·쿼리 성능 관점 의견. **코드는 수정하지 않음** — 검토·권고만. 스키마 반영은 DB Subagent가 수행.

### Architecture (아키텍처 검토)

- **성능·확장성 검토**: 스냅샷 조회 등 빈번·무거운 데이터 접근에 대해 조회 패턴·부하·인덱스·캐시·배치 가능성 검토.
- **트레이드오프**: DB 전용 vs 캐시, 요청 단위 vs 배치 등 옵션 비교 및 조건별 권고.
- **운영 영향**: 지연·처리량·리소스 관점 의견. **코드는 수정하지 않음** — Backend/DB가 권고 반영.

### Review (코드/변경 검토)

- **검토만 수행**: 변경(패치/파일 목록)을 계약·워크플로우·품질·`docs/workflow/CONSISTENCY-STANDARDS.md` 기준으로 검토. 통과/미통과·제안 출력. **코드 수정·테스트 작성·§5 작성 안 함** (→ Backend/Frontend/DB, QA).
- **표준 적용**: 표준 문서는 Consistency가 소유; Review는 그 문서를 **참고해 검토만** 수행.

### Documentation (사용자/운영 문서)

- **범위**: README, QUICK_START, 배포·운영 가이드, 런북, 트러블슈팅. **요건 문서(`docs/requirements/`)·API 스펙(contract, specs)·코드 수정 안 함** (→ Requirements, Contract, 구현 에이전트).

### Release (릴리스·변경 이력)

- **범위**: CHANGELOG, 버전 부여 안내, 릴리스 체크리스트. **사용자 가이드(README·런북) 작성 안 함** (→ Documentation). **코드 수정 안 함**.

### Consistency (표준·일관성 문서)

- **표준 문서 소유**: `docs/workflow/CONSISTENCY-STANDARDS.md` 정의·갱신(네이밍, 에러 코드, 로깅, 파일 구조 등). **검토 실행·코드 수정 안 함** — Review가 이 문서를 **적용**해 검토.

### UX (디자인·UX 검토)

- **검토만 수행**: a11y, UI 일관성, 디자인 시스템, 인터랙션 권고. **코드 구현 안 함** (→ Frontend가 구현).

---

## 2.6 역할 중복 방지 (단일 담당 지정)

아래 영역은 **한 에이전트만** 담당하도록 지정. 협업·호출 시 이 표를 기준으로 하면 중복이 발생하지 않는다.

| 영역 | 담당 에이전트 | 비담당(참고) |
|------|----------------|--------------|
| 요건 문서(§1·§2·§3), 기능 스펙 | **Requirements** | Documentation은 사용자/운영 문서만 |
| API·환경·스펙(contract, specs) | **Contract** | Consistency는 코딩 컨벤션만 |
| 보안 검토(§2.1, 보안 권고) | **Security** | — |
| 스키마 설계 검토 | **DBA** | DB가 스키마 파일 수정 |
| 성능·확장성 설계 검토 | **Architecture** | — |
| **표준 문서** 정의·갱신(CONSISTENCY-STANDARDS) | **Consistency** | Review는 적용만 |
| **변경 검토**(계약·워크플로우·품질·표준 적용) | **Review** | QA는 테스트 설계·§5; Consistency는 표준 정의만 |
| 테스트 설계, §3·§5·§6, 검증 체크리스트 | **QA** | Review는 코드 검토만 |
| 사용자/운영 문서(README, QUICK_START, 런북) | **Documentation** | Requirements는 요건 문서; Release는 CHANGELOG만 |
| CHANGELOG, 버전, 릴리스 체크리스트 | **Release** | Documentation은 사용자/운영 문서만 |
| 디자인/UX 검토(a11y, UI 일관성) | **UX** | Frontend가 구현 |
| 구현(backend/, frontend/, db/) | **Backend / Frontend / DB** | Review·UX·Documentation·Release·Consistency는 코드 수정 안 함 |

---

## 2.5 요구사항 발생 시 에이전트 협업 (협업 순서·인계)

요구사항 또는 오류 수정 요청이 들어오면, **에이전트 협업 순서**에 따라 여러 Subagent가 순차·병렬로 참여한다. 상세한 단계별 역할·입력·출력·인계는 **단일 참조 문서**에서 관리한다.

- **문서**: `docs/workflow/AGENT-COLLABORATION-ON-REQUIREMENT.md`
- **규칙**: `.cursor/rules/agent-collaboration.mdc` (요구사항/오류 수정 시 위 순서 준수)
- **요약**: Requirements → Security/Contract/DBA/Architecture/Consistency/UX(해당 시) → Backend/Frontend/DB → Review(선택) → QA → Documentation/Release(해당 시). 역할 중복 방지: §2.6. 각 에이전트의 `.cursor/agents/*.mdc`에 "Collaboration" 절로 자신의 단계와 인계 대상을 명시해 두었다.

---

## 3. 작업 흐름 (어떤 에이전트를 언제 쓸지)

1. **요구사항·스펙 정리**  
   - **Requirements** Subagent: 새 요건·기능·오류 수정에 대해 요건 문서(`docs/requirements/yyyyMMdd-요건명.md`) 작성·갱신. 복잡한 기능이면 스펙 초안 작성.  
   - 또는 해당 레이어(Frontend/Backend/DB) Subagent에게 “이 요건 문서에 맞춰 구현해줘” 전에, Requirements가 요건 문서를 먼저 정리하게 할 수 있음.

2. **보안 검토 (개인정보·복호화·접근통제 관련 시)**  
   - **Security** Subagent: 요건 초안(§1·§2) 또는 설계가 있을 때, 개인정보·복호화 범위·접근 통제 관점으로 검토. §2.1 보안 검토 또는 권고안을 요건 문서에 반영. **검토된 대로 설계·개발**이 이루어지도록 권고(예: 복호화 허용 범위 정책).  
   - 개발 전 또는 Requirements/Contract와 병행하여 호출.

3. **API·계약 정의(크로스 레이어 변경 시)**  
   - **Contract** Subagent: API 추가/변경 시 `docs/contract.md`, `specs/*.spec.yaml` 먼저 갱신.  
   - 그 다음 Frontend/Backend Subagent가 스펙대로 구현.

4. **개발**  
   - **Frontend / Backend / DB** Subagent: 담당 디렉터리만 수정. contract·스펙·**보안 검토 결과** 참고. 요건 문서가 있으면 그에 맞춰 구현.

5. **코드/변경 검토(선택)**  
   - **Review** Subagent: 구현물을 계약·워크플로우·품질·`CONSISTENCY-STANDARDS.md` 기준으로 검토. 코드 수정 없음; 구현 에이전트가 제안 반영.

6. **테스트·검증**  
   - **QA** Subagent: 테스트 시나리오·체크리스트 제안, 요건 문서의 §5·§6 작성·갱신.  
   - **Frontend/Backend/DB**: 단위·통합 테스트 코드 작성.  
   - `/check-backend`, `/check-db`, `/check-frontend-backend`, `/verify` 로 상태·검증 수행.

7. **문서·릴리스(해당 시)**  
   - **Documentation**: 사용자/운영 문서(README, QUICK_START, 런북) 갱신. **Release**: CHANGELOG, 릴리스 체크리스트 갱신.

---

## 4. Subagent 등록 방법

### 4.1 로컬 에이전트 (프로젝트에 직접 추가)

이 프로젝트에는 **`.cursor/agents/`** 에 14개 Subagent 정의가 들어 있다.

- **Core 9**: `Frontend.mdc`, `Backend.mdc`, `DB.mdc`, `Requirements.mdc`, `QA.mdc`, `Contract.mdc`, `Security.mdc`, `DBA.mdc`, `Architecture.mdc`
- **추가 5**: `Review.mdc`, `Documentation.mdc`, `Release.mdc`, `Consistency.mdc`, `UX.mdc`
- Cursor가 **로컬 에이전트**(`.cursor/agents/*.mdc`)를 지원하면, 이 프로젝트를 열었을 때 위 Subagent가 자동으로 목록에 나타날 수 있다.
- 수정이 필요하면 `.cursor/agents/*.mdc`를 편집하면 된다. (동기화: `docs/cursor-subagents/*.md`와 역할·내용을 맞춰 두는 것을 권장.)

### 4.2 Cursor Settings에서 수동 등록 (한 번만)

로컬 에이전트가 적용되지 않는 Cursor 버전이면 아래대로 수동 등록한다.

1. **Settings → Subagents** 이동.
2. **Subagent 추가** 14번 해서 이름을 `Frontend`, `Backend`, `DB`, `Requirements`, `QA`, `Contract`, `Security`, `DBA`, `Architecture`, `Review`, `Documentation`, `Release`, `Consistency`, `UX`로 지정.
3. 각 Subagent의 **프롬프트(설명)** 란에 `docs/cursor-subagents/` 아래 해당 파일 **내용 전체를 복사해 붙여넣기**.
   - Frontend → `frontend.md`, Backend → `backend.md`, DB → `db.md`
   - Requirements → `requirements.md`, QA → `qa-test.md`, Contract → `contract-api.md`
   - Security → `security.md`, DBA → `dba.md`, Architecture → `architecture.md`
   - Review → `review.md`, Documentation → `documentation.md`, Release → `release.md`, Consistency → `consistency.md`, UX → `ux-design.md`
   - **(선택)** Backend 모듈별: Backend-Auth → `backend-auth.md`, Backend-ActivityLog → `backend-activity-log.md`, Backend-Log → `backend-log.md`
   - **(선택)** Frontend 모듈별: Frontend-Auth → `frontend-auth.md`, Frontend-ActivityLog → `frontend-activity-log.md`, Frontend-Log → `frontend-log.md`
4. (선택) 프로젝트/워크스페이스를 이 `dev` 폴더로 열어 두면, 규칙(docs-reference, contract-first, agent-collaboration)과 함께 동작.

---

## 5. 참고

- **공통 계약**: `docs/contract.md` (포트·API·DB 단일 진실).
- **개발 워크플로우**: `docs/workflow/DEVELOPMENT_WORKFLOW.md` (요건 문서 선 작성, 검증 필수).
- **프롬프트 본문**: `docs/cursor-subagents/*.md` — 여기만 수정하면 Subagent 동작을 통일해서 유지할 수 있다.

이 설계에 따라 **Core 9** (Frontend, Backend, DB, Requirements, QA, Contract, Security, DBA, Architecture) + **추가 5** (Review, Documentation, Release, Consistency, UX) = **14개** Subagent로 개발·요구사항 정리·스펙·검토·문서·릴리스·일관성·UX가 **역할 중복 없이** (§2.6) 동작하도록 구성한다.

---

## 6. 다른 도구와의 연동

- **규칙·커맨드·문서·스크립트**가 어떻게 연결되는지: **docs/workflow/CURSOR-AND-TOOLS-INTEGRATION.md**
- Subagent 선택(일반 vs 모듈별 vs 추가 5)은 위 §1·§1.1·§1.2·§1.3; **역할 중복 방지**는 §2.6. 실제 프롬프트는 `docs/cursor-subagents/*.md`. 서비스 재시작·헬스 확인은 `.cursor/commands/verify.md` 및 `scripts/dev-services.sh`와 동일 절차로 맞춰 둠.
