# 검토: 공통 규칙/공통 레이어 역할 — 에이전트 담당 방안

**검토 목적**: (1) 아키텍처 서브에이전트가 공통 규칙(검색 통일 등) 담당, (2) 프론트엔드/백엔드 하위 서브에이전트를 두고 프론트엔드가 공통 규칙 관리 — 두 방안을 **에이전트 동작 흐름** 관점에서만 검토. 코드/설정 변경 제안 없음.

---

## 1. 방안 요약

| 방안 | 내용 |
|------|------|
| **A** | **Architecture**가 “공통 규칙” 담당 — 공통 레이어/검색 통일 규칙의 설계·문서 소유 |
| **B** | **Frontend/Backend 하위 서브에이전트** (예: Frontend-Common) 추가 + **Frontend**가 “공통 규칙 관리” 역할 부여 |

---

## 2. 방안 A: Architecture가 공통 규칙 담당

### 2.1 현재 Architecture 역할 (참고)

- **SUBAGENT-DELEGATION**: Step 3c. Performance, scalability + **commonization review** (frontend/backend 공통 기능 검토). **No code** — Backend/Frontend가 구현.
- **CURSOR-SUBAGENTS-DESIGN §2.6**: "Performance/scalability and **commonization (frontend/backend)** design review" → Architecture 소유.

### 2.2 “공통 규칙 담당”을 넓히는 경우

- **의미**: 공통 규칙(예: 검색 통일 규칙, 공통 레이어 설계)을 정의·유지하는 **문서/스펙**의 소유자를 Architecture로 두는 것.
- **가능한 소유 문서 예**: `docs/analysis-search-consistency-by-screen.md`, 공통 컴포넌트 설계 스펙, `.cursor/skills/search-consistency-domain` 등.

### 2.3 흐름에서 문제될 수 있는 점

| 이슈 | 설명 |
|------|------|
| **문서 vs 코드 주인 분리** | Architecture는 코드를 수정하지 않음. 규칙 문서는 Architecture, 구현은 Backend/Frontend. **문서와 실제 코드가 어긋날 수 있음.** 완화: “구현은 Architecture가 정한 설계/문서를 따름”을 규칙으로 명시하고, Review가 문서 준수 여부를 보는 식으로 정리 필요. |
| **요건 문서와의 경계** | 요건 문서(§1·§2·§3)는 **Requirements** 소유. “검색 통일” 요건은 Requirements가 쓰고, **도메인 설계 문서**(어떤 화면에 어떤 축을 쓸지 등)는 Architecture가 쓴다면, **누가 어떤 문서를 갱신할지**가 명확해야 함. 예: 요건이 “활동 이력에 부서 추가”일 때, 분석 문서(화면 목록·규칙) 갱신은 Requirements가 §2에서 참조만 할지, Architecture가 직접 갱신할지. 갱신 주체가 불명확하면 중복·누락 가능. |
| **호출 순서/의존성** | 공통 규칙이 바뀌는 요건 시 “Requirements → Architecture(규칙/설계 갱신) → Backend/Frontend(구현)” 순서를 쓰려면, Main이 **두 번** 위임해야 함. 또는 Requirements 작성 시 Architecture를 **병렬 자문**으로만 쓰고, “규칙 문서 갱신”은 Requirements가 §2에 반영·Architecture는 검토만 할 수도 있음. 전자면 단계 증가, 후자면 “규칙 문서 소유”가 Architecture만의 역할이 아니게 됨. |
| **스킬/규칙 파일 소유** | `.cursor/skills/`는 현재 §2.6에서 “Implementing agent (Backend/Frontend/DB for their domain)”. 공통 규칙 스킬(search-consistency-domain)을 Architecture 소유로 바꾸면, **도메인 스킬**과 **설계/공통 규칙 스킬**의 소유가 나뉨. 업데이트 책임이 “요건 반영 시 Requirements? 설계 변경 시 Architecture?”로 나뉘어 일관된 갱신이 어려울 수 있음. |

### 2.4 정리 (방안 A)

- Architecture가 **공통 규칙/공통 레이어 설계 문서**를 소유하는 것은 역할과 맞음.
- 다만 **문서–코드 일치**, **Requirements와의 문서 갱신 책임 분리**, **호출 순서(단계 수)** 를 워크플로에 명시하지 않으면 흐름에서 혼선·중복이 날 수 있음.

---

## 3. 방안 B: Frontend/Backend 하위 서브에이전트 + Frontend가 공통 규칙 관리

### 3.1 현재 설계와의 관계

- **CURSOR-SUBAGENTS-DESIGN §5.1**에 이미 **Frontend-Common / Backend-Common** 옵션이 있음.
- 현재: Architecture가 commonization **검토** → Frontend/Backend가 **공통 코드·기능 코드 모두 구현**.
- 대안: **Frontend-Common**, **Backend-Common**를 두어 **공통 코드만** 구현하고, Frontend/Backend(기능)는 화면/모듈만 구현.

### 3.2 “Frontend가 공통 규칙 관리” 해석

- **해석 1**: Frontend(에이전트)가 **공통 규칙 문서/스펙을 유지**한다.  
  → §2.6상 docs/ 요구사항·설계는 Requirements/Contract 등이 소유. Frontend가 `docs/analysis-*`를 소유하면 **역할 테이블과 충돌** 가능.
- **해석 2**: Frontend가 **구현 시 공통 규칙을 항상 적용**한다(문서는 그대로 두고, 구현 책임만 Frontend).  
  → 이건 지금도 가능. HANDOFF-CHECKLIST의 “Search/filter (user-context)” 참조로 충분. **별도 “공통 규칙 관리” 역할을 부여하지 않아도 됨.**
- **해석 3**: **Frontend-Common** 같은 하위(형제) 에이전트를 두고, **Frontend**가 “공통 레이어 쓸지 여부·어떤 규칙 적용할지”를 **조율**한다.  
  → 아래 “하위 서브에이전트” 흐름에서 검토.

### 3.3 하위(형제) 서브에이전트일 때의 흐름

- Cursor에서 **서브에이전트가 다른 서브에이전트를 Task로 호출**하는지는 프로젝트 기본 가정과 다를 수 있음. 일반적으로 **Main만** Task로 Backend/Frontend 등을 호출.
- 따라서 **Frontend-Common**를 둔다면 **Main이 호출하는 형제 에이전트**로 두는 구성이 자연스러움:  
  Main → Frontend-Common(공통 구현), Main → Frontend(화면 구현).

### 3.4 흐름에서 문제될 수 있는 점

| 이슈 | 설명 |
|------|------|
| **한 요건을 두 번 나눠 위임** | “활동 이력에 부서 필터 추가 + 공통 UserFilterFields 사용” 같은 요건이면, Main이 (1) Frontend-Common에 “공통 컴포넌트 생성”, (2) Frontend에 “활동 이력에 통합”을 **순차** 위임해야 함. **순서 강제**(Common 먼저)와 **인터페이스/스펙 전달**이 Main 또는 요건 문서에 명시되어 있지 않으면, Frontend가 “아직 없는 컴포넌트”를 전제하고 받을 수 있음. |
| **공통 규칙 문서 소유** | “공통 규칙 관리”를 Frontend에 둬도, **규칙 문서**(예: 분석 문서, 스킬)는 §2.6상 docs/skills 소유와 맞춰야 함. Frontend는 구현 에이전트이므로 **문서 소유**까지 넣으면 Requirements/Architecture/Consistency와 겹침. **규칙은 문서로, 구현만 Frontend**로 두는 편이 경계가 분명함. |
| **단일 소유 규칙** | “Each file has exactly one owning agent.” 공통 레이어(`frontend/src/common/`)를 Frontend-Common가 소유하면, Frontend는 **수정하지 않고 사용만** 해야 함. 실수로 Frontend가 common을 수정하면 소유 충돌. 프롬프트/체크리스트에 “common/ 수정 금지”를 명시해야 함. |
| **실패 시 재위임** | Frontend-Common가 실패하거나 스펙이 바뀌면, Frontend 작업이 막힘. Main이 재위임(Common 수정 → Frontend 재실행) 순서를 알아야 하고, 사용자 대기 시간이 길어질 수 있음. |

### 3.5 정리 (방안 B)

- **Frontend-Common / Backend-Common**를 두는 것 자체는 §5.1에 정리된 대로 **핸드오프 순서**(Architecture → Common → Frontend/Backend)와 **소유 경계**를 문서에 넣으면 동작 가능.
- **“Frontend가 공통 규칙 관리”**는 **문서/스펙 소유**까지 포함하면 역할 충돌이 나므로, **구현 시 규칙 적용 책임**만 두고, **규칙 정의/문서**는 Requirements·Architecture·스킬 등 기존 소유 구조를 유지하는 편이 흐름상 안전함.

---

## 4. 공통으로 고려할 점

| 항목 | 권장 |
|------|------|
| **규칙 문서 소유** | “공통 규칙” 문서(분석 문서, 공통 레이어 설계)는 **한 주체**만 명확히. Architecture 또는 Requirements 중 하나로 하고, 다른 쪽은 “참조·자문”으로 두면 중복·누락이 줄어듦. |
| **호출 순서** | Common 에이전트를 쓰면 **Requirements → (Security/Contract/) Architecture → Common → Frontend/Backend → QA** 순서를 SUBAGENT-DELEGATION 등에 명시하고, Main이 “공통 레이어 건”일 때 이 순서로 호출하도록 규칙화. |
| **문서–코드 일치** | 설계/규칙 문서를 누가 가지든, “구현은 해당 문서/스펙 준수”를 Review·체크리스트에 넣어 drift를 줄이는 것이 좋음. |

---

## 5. 결론 (검토만)

- **방안 A (Architecture가 공통 규칙 담당)**: 역할은 맞지만, **문서 갱신 책임**(Requirements vs Architecture), **문서–코드 일치**, **호출/단계 수**를 워크플로에 명시하지 않으면 에이전트 흐름에서 역할 겹침·누락이 생길 수 있음.
- **방안 B (하위 서브에이전트 + Frontend 공통 규칙 관리)**: **Frontend-Common** 등 형제 에이전트 추가는 §5.1 대로 순서·소유만 정하면 가능. 다만 **“공통 규칙 관리”를 문서/스펙 소유까지** Frontend에 주면 기존 역할 테이블과 충돌하므로, **구현 시 규칙 적용**만 Frontend(와 HANDOFF-CHECKLIST)에 두고, **규칙 정의/문서**는 기존처럼 Requirements·Architecture·스킬 구조를 유지하는 편이 안전함.
- 두 방안 모두, **공통 규칙 문서의 단일 소유**와 **Common 사용 시 호출 순서·인터페이스 명시**를 워크플로에 넣지 않으면 에이전트 동작 흐름에서 문제가 될 여지가 있음.

---

## 6. 해결 방안 제안 (단일 소유 + 검증 강화, 신규 에이전트 없음)

검토에서 나온 문제(문서 소유 불명확, 문서–코드 drift, 호출 단계 증가)를 **새 에이전트 없이** 줄이는 방안이다.

### 6.1 원칙

- **규칙/도메인 설계 문서**는 **한 주체**만 명확히 소유한다.
- **호출 단계**는 늘리지 않는다 (Requirements → … → Frontend/Backend → QA 유지).
- **문서–코드 일치**는 **Review**에서 한 번 검증하도록 한다.

### 6.2 규칙 문서·스킬 소유 (단일 주체)

| 아티팩트 | 소유 | 갱신 시점 |
|----------|------|-----------|
| `docs/analysis-search-consistency-by-screen.md` (및 동일 성격의 도메인 설계 문서) | **Requirements** | 요건 작성 시, 해당 요건이 **규칙 자체를 바꿀 때** (예: 적용 화면 추가·축 변경) 같은 사이클에서 갱신. 규칙을 **적용만** 하는 요건이면 §2에서 참조만 하고 문서는 수정하지 않음. |
| `.cursor/skills/search-consistency-domain/` | **Requirements** | 위 분석 문서를 갱신할 때 같은 요건 사이클에서 함께 갱신. (분석 문서와 스킬이 한 세트로 유지됨.) |

- **Architecture**는 기존처럼 commonization **검토**만 하고, 규칙 문서/스킬 **소유·갱신**은 하지 않는다.
- **결과**: “누가 규칙 문서를 고치나?” → Requirements. “규칙이 바뀌는 요건”일 때만 Requirements가 분석 문서(+ 스킬)를 수정하므로, 호출 순서는 그대로이고 단일 소유만 명확해진다.

### 6.3 문서–코드 일치 (Review 검증)

- **Review** handoff 시, 요건이 **사용자 맥락 검색/필터**(activity-log, statistics, user-management, permission-group-management, search-history, pending-approvals)를 건드리면, Review가 다음을 확인한다.
  - 구현이 `docs/analysis-search-consistency-by-screen.md` §2(통일 축), §2.4(scope=self)를 따르는지.
- **HANDOFF-CHECKLIST**의 Review 섹션에 아래 한 줄을 추가하면 된다.
  - "If the requirement touches **search/filter on user-context screens**, verify implementation conforms to **docs/analysis-search-consistency-by-screen.md** (§2 unified axes, §2.4 scope=self)."

이렇게 하면 “규칙 문서는 있는데 구현이 다르다”는 drift를 Review 단계에서 한 번 걸러낼 수 있다.

### 6.4 Requirements 작성 워크플로 보강

- **REQUIREMENTS-AUTHORING-WORKFLOW** (또는 요건 작성 관련 규칙)에 다음을 넣는다.
  - 요건이 **도메인 규칙**(예: 사용자 맥락 검색 통일)을 **적용**하는 경우: §2에서 해당 도메인 설계 문서(예: analysis-search-consistency-by-screen.md)를 **참조**하고, 어떻게 적용하는지 적는다.
  - 요건이 **규칙 자체를 변경**하는 경우(적용 화면 추가, 축 추가/변경 등): 같은 요건 작성 사이클에서 해당 **도메인 설계 문서**와 **관련 스킬**(예: search-consistency-domain)을 **직접 갱신**한다.
- 이렇게 하면 “규칙이 바뀌었는데 문서는 안 바뀜”을 요건 단계에서 줄일 수 있다.

### 6.5 Common 에이전트(Frontend-Common 등)는 언제 도입할지

- **지금은 도입하지 않는다.** Architecture 검토 + Frontend/Backend 구현으로 충분하다.
- 공통 레이어가 커지거나, “공통 코드만 전담하는 에이전트”가 필요해지면, **CURSOR-SUBAGENTS-DESIGN §5.1**에 따라 Frontend-Common/Backend-Common와 **호출 순서(Architecture → Common → Frontend/Backend)** 를 워크플로에 명시한 뒤 도입하면 된다.
- 그때도 **규칙 문서/스킬 소유**는 이 제안대로 **Requirements**에 두면, Common 에이전트는 “구현만” 담당하고 “규칙 정의”는 건드리지 않게 할 수 있다.

### 6.7 공통화가 늘어날 때 Common 에이전트 도입 (트리거)

공통화를 시작하면 공통 레이어가 점점 늘어나므로, **Common 에이전트 도입이 필요해지는 시점**을 트리거로 두는 것이 좋다.

**도입 트리거 (다음 중 하나라도 해당 시 도입 검토)**

| 트리거 | 설명 |
|--------|------|
| **공통 디렉터리·파일 수** | `frontend/src/common/`(또는 합의된 공통 경로) 아래 컴포넌트·훅·유틸이 **일정 개수 이상** (예: 5개 이상 파일, 또는 3개 이상 재사용 컴포넌트). |
| **여러 요건에서 공통 수정** | 서로 다른 요건에서 **같은 공통 모듈**을 계속 건드리게 되고, Frontend/Backend가 “공통 + 기능”을 동시에 수정하면서 **소유 경계가 흐려짐**. |
| **공통 레이어 전담 리뷰 부담** | Review 단계에서 “이번에 공통도 바꿨는데, 공통 규칙·기존 사용처와 맞는지” 검토 부담이 반복적으로 큼. |

**도입 시 유지할 것 (§6 해결 방안과의 조합)**

- **규칙/도메인 설계 문서**는 계속 **Requirements** 소유. Common 에이전트는 **구현만** 담당하고, `docs/analysis-*`, 스킬 갱신은 하지 않음.
- **호출 순서**: Requirements(요건·규칙 문서) → Architecture(commonization 검토) → **Common**(공통 코드 구현, §2 변경 파일 목록에 common 경로 반영) → **Frontend/Backend**(화면·기능 구현, 공통 사용) → Review(전체·규칙 문서 준수 검증) → QA.
- **단일 소유**: `frontend/src/common/`(및 합의된 공통 경로)는 **Frontend-Common**만 수정; Frontend(기능)는 **사용만** 하고 common 디렉터리 직접 수정 금지. (Backend 공통도 동일.)

**정리**: 공통화가 늘어나면 Common 에이전트를 두는 편이 맞고, 위 트리거를 워크플로(또는 CURSOR-SUBAGENTS-DESIGN §5.1 인근)에 “Common 에이전트 도입 시점”으로 명시해 두면, “언제 도입할지”를 일관되게 결정할 수 있다. 도입해도 §6의 “규칙 문서는 Requirements, 문서–코드 일치는 Review” 방안은 그대로 유지된다.

### 6.6 요약 (이 방안이 해결하는 것)

| 검토에서 나온 문제 | 이 방안으로의 대응 |
|--------------------|---------------------|
| 규칙 문서 소유 불명확 (Requirements vs Architecture) | **Requirements** 단일 소유. Architecture는 검토만. |
| 문서–코드 drift | **Review**에서 “검색/필터(사용자 맥락) 시 분석 문서 §2·§2.4 준수” 검증 추가. |
| 호출 단계 증가 (Architecture 규칙 갱신용 추가 호출) | **증가 없음.** 규칙 갱신은 Requirements가 요건 작성 시 함께 수행. |
| Frontend가 “공통 규칙 관리”할 때 문서 소유까지 주면 역할 충돌 | **문서/스펙 소유는 Requirements.** Frontend는 구현 + HANDOFF-CHECKLIST에 따른 규칙 적용만. |
| Common 에이전트 도입 시 순서·인터페이스 불명확 | **당장 Common 미도입.** 도입 시에는 §5.1대로 순서·소유를 문서에 명시. 규칙 문서는 계속 Requirements 소유. |

이 방안을 적용하려면 (1) CURSOR-SUBAGENTS-DESIGN §2.6 등 **역할 테이블**에 “도메인 설계 문서(예: analysis-* by screen) → Requirements”를 한 줄로 반영하고, (2) **HANDOFF-CHECKLIST** Review에 위 검증 한 줄을 추가하며, (3) **REQUIREMENTS-AUTHORING-WORKFLOW**에 “도메인 규칙 적용 시 참조, 규칙 변경 시 문서·스킬 갱신”을 넣으면 된다.
