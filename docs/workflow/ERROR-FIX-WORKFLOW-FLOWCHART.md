# 오류 수정 요건 → 작업 진행 흐름 (Flowchart)

사용자가 **오류 수정 요건**을 제기했을 때, Skills / Sub-Agent / Commands 가 어느 시점에 어떻게 쓰이는지 정리한 흐름도입니다.

---

## 전체 흐름도 (Mermaid)

```mermaid
flowchart TB
    subgraph START["시작"]
        A["사용자: 오류 수정 요건 제기"]
    end

    subgraph RULES_ALWAYS["항상 적용 (Rules, 사용자 행동 없음)"]
        R1["docs-reference.mdc\n(docs/ 참고)"]
        R2["contract-first.mdc\n(API·DB 계약 우선)"]
    end

    subgraph STEP1["1. 요건 수집·분석"]
        B["요건 파악 (What/Why)\n기존 코드·영향도 분석"]
        C["요건 문서 작성\ndocs/requirements/yyyyMMdd-요건명.md"]
    end

    subgraph STEP2["2. 설계·테스트 계획 (개발 전 필수)"]
        D["영향 레이어 결정\n(프론트/백엔드/DB)"]
        E["스펙·계약 확인\nspecs/, docs/contract.md"]
        T["테스트 계획 수립\n(요건 문서 §3, 개발 전)"]
    end

    subgraph STEP3["3. 개발"]
        F["환경 기동 (필요 시)"]
        G["해당 레이어 코드 수정"]
    end

    subgraph STEP4["4. 테스트 실행·검증"]
        H2["단위 테스트 실행\n(mvn test / npm test)"]
        H3["통합 테스트\n(또는 curl·수동)"]
        I["재시작·정상 실행 확인\n실패 시 bugfix 반복"]
    end

    subgraph STEP5["5. 문서화·완료"]
        J["요건 문서 업데이트\n테스트 결과 기록"]
        K["오류인 경우: 원인·조치 결과 기록\n(동일 요구사항 ID, /record-error-fix)"]
    end

    A --> B
    RULES_ALWAYS -.-> B
    B --> C
    C --> D
    D --> E
    E --> T
    T --> F
    F --> G
    G --> H2 --> H3 --> I
    I --> J
    J --> K

    style RULES_ALWAYS fill:#e8f5e9
    style STEP1 fill:#e3f2fd
    style STEP2 fill:#fff3e0
    style STEP3 fill:#fce4ec
    style STEP4 fill:#f3e5f5
    style STEP5 fill:#e0f2f1
```

---

## 시점별: Skills / Sub-Agent / Commands 사용

```mermaid
flowchart LR
    subgraph PHASE["단계"]
        P1["1. 요건·분석"]
        P2["2. 설계"]
        P3["3. 개발"]
        P4["4. 검증"]
        P5["5. 문서화"]
    end

    subgraph SKILLS["Skills (자동 적용)"]
        S1["dev-workflow\n(새 요건/기능 시)"]
        S2["requirement-doc\n(요건 문서 작성 시)"]
    end

    subgraph RULES["Rules"]
        Ra["docs-reference\ncontract-first\n(항상)"]
        Rb["frontend-agent\n(frontend/ 작업 시)"]
        Rc["backend-agent\n( backend/ 작업 시 )"]
        Rd["db-agent\n( db/ 작업 시 )"]
    end

    subgraph CMDS["Commands (사용자 실행)"]
        C1["/new-requirement"]
        C2["/follow-workflow"]
        C3["/agent-frontend\n/agent-backend\n/agent-db"]
        C4["/start-db\n/start-backend\n/start-frontend\n/start-all"]
        C5["/check-backend\n/check-db\n/check-frontend-backend\n/verify"]
        C5a["/run-tests\n(테스트 케이스·단위/통합 테스트)"]
        C6["/record-error-fix\n(조치 후 원인·조치 결과 기록)"]
    end

    P1 --> S1
    P1 --> S2
    P1 --> C1
    P2 --> Ra
    P2 --> C2
    P3 --> Rb
    P3 --> Rc
    P3 --> Rd
    P3 --> C3
    P3 --> C4
    P4 --> C5
    P4 --> C5a
    P5 --> S2
    P5 --> C6
```

---

## 단계별 상세: 무엇을 언제 쓸지

| 단계 | 진행 내용 | Rules (자동) | Skills (자동) | Commands (선택·수동) |
|------|-----------|--------------|----------------|----------------------|
| **1. 요건 수집·분석** | 요건 파악, 요건 문서 작성 | docs-reference, contract-first | dev-workflow, requirement-doc | `/new-requirement` — 새 요건 시작 시 한 번에 지시 |
| **2. 설계·테스트 계획** | 영향 레이어 결정, 스펙·계약 확인. **개발 전에** 요건 문서 §3 **테스트 계획(테스트 케이스 목록)** 수립. 개발 후에 요구사항·테스트 계획 기록 금지 | 동일 | — | `/follow-workflow` — 워크플로우 단계 점검 |
| **3. 개발** | 환경 기동, 해당 레이어 코드 수정. (1·2 완료 후에만 착수) | frontend-agent / backend-agent / db-agent (해당 경로 열면 자동) | — | `/agent-frontend`·`/agent-backend`·`/agent-db` — 역할 고정<br>`/start-db`·`/start-backend`·`/start-frontend`·`/start-all` — 서비스 기동 |
| **4. 테스트 실행·검증** | **단위 테스트**(mvn test / npm test) → **통합 테스트**(또는 curl·수동) → 재시작·정상 실행 확인·bugfix 반복. (테스트 계획은 2단계에서 이미 §3에 작성됨) | — | dev-workflow | `/run-tests` — 단위/통합 테스트 실행<br>`/verify` — 재시작·확인·bugfix 반복<br>`/check-*` — 개별 상태 확인 |
| **5. 문서화** | 요건 문서에 테스트 결과 반영; **오류인 경우** 원인·조치 결과를 **동일 요구사항 ID**로 같은 문서에 기록 | docs-reference | requirement-doc | `/record-error-fix` — 조치 완료 후 원인·조치 결과 기록 |

---

## 검증 단계: 재시작·정상 실행 확인·bugfix 반복

사용자에게 "재시작해 주세요"라고 하지 않는다. 에이전트가 아래를 **자동**으로 수행한다.

```mermaid
flowchart TB
    subgraph VERIFY["4. 검증 (자동)"]
        V1["변경 범위에 따라\n재시작 실행\n(scripts/dev-services.sh)"]
        V2["잠시 대기\n(백엔드 5~10초)"]
        V3["정상 실행 확인\n(health, 포트, DB)"]
        V4["모두 통과?"]
        V5["문서화로 진행"]
        V6["오류 범위 파악"]
        V7["부모 요건 하위\nbugfix 문서 생성\n{부모ID}-bugfix-{N}.md"]
        V8["bugfix 수정 반영"]
    end

    V1 --> V2 --> V3 --> V4
    V4 -->|Yes| V5
    V4 -->|No| V6 --> V7 --> V8 --> V1

    style V5 fill:#c8e6c9
    style V7 fill:#ffecb3
```

- **재시작**: `./scripts/dev-services.sh frontend|backend|db|all restart`
- **확인**: 백엔드 `GET /api/health`, 프론트 포트 3001, DB 사용 시 `GET /api/db/test`
- **실패 시**: `docs/requirements/{부모요건ID}-bugfix-{N}.md` 생성 (템플릿: `docs/template/BUGFIX_CHILD_TEMPLATE.md`) → 수정 후 다시 재시작·확인

---

## 요약 다이어그램 (단순 버전)

```mermaid
flowchart TB
    A["오류 수정 요건 제기"] --> B["요건 문서 작성"]
    B --> C["설계·스펙 확인"]
    C --> D["개발: 해당 레이어 수정"]
    D --> E["테스트 케이스·단위/통합 테스트\n→ 재시작·확인 자동, 실패 시 bugfix 반복"]
    E --> F["문서 업데이트"]

    B -.->|"Skills: dev-workflow\nrequirement-doc"| B
    B -.->|"Cmd: /new-requirement"| B
    C -.->|"Rules: contract-first\nCmd: /follow-workflow"| C
    D -.->|"Rules: *-agent (globs)\nCmd: /agent-*"| D
    D -.->|"Cmd: /start-*"| D
    E -.->|"Cmd: /verify\n(재시작·확인·bugfix 루프)"| E
```

---

- **Rules**: 대화/파일 경로에 따라 자동 적용. 사용자가 매번 명령할 필요 없음.
- **Skills**: “요건”, “요건 문서” 등 트리거 시 자동 적용.
- **Commands**: 사용자가 슬래시로 선택해 실행. 빠르게 단계·역할·검증을 고정할 때 사용.

이 파일은 `docs/workflow/` 에 있으며, 워크플로우 가이드와 함께 참고하면 됩니다.
