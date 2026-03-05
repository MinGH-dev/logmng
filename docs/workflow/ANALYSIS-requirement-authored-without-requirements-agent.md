# Analysis: Requirement authored without Requirements agent — other agents’ review missing

**목적**: 요구사항 문서를 Requirements 에이전트가 아닌 메인 에이전트가 작성했을 때, 다른 에이전트(검토)가 누락될 수 있는 **원인**을 정리한다.

---

## 1. 설계상 기대 흐름 (Step 1)

- **Step 1(요구사항 작성)** 은 **Requirements 전용 서브에이전트**가 수행한다.  
  `SUBAGENT-DELEGATION.md` §1:  
  *"Step 1 → **Requirements** … **During authoring**, Requirements **obtains parallel input** from experts (Security, Contract, UX, DBA, Architecture, Consistency) and from **Backend/Frontend/DB/QA** (scenario, codebase summary, problem analysis, solution) … **orchestrates** (merges) into §1·§2."*

- **AGENT-COLLABORATION-ON-REQUIREMENT.md** §1.1:
  - Requirements 서브에이전트는 §1·§2를 **자기 판단만으로 쓰지 않고**,
  - **작성 단계에서** Security, Contract, Backend, Frontend, DB, QA, Architecture 등을 **병렬로 호출**해
  - (a) 사용자 시나리오/기대 결과, (b) 코드베이스 요약, (c) 문제 분석, (d) 해결 방안 입력을 받은 뒤
  - 그걸 **조율(merge)** 해서 §1·§2를 완성하고, 이어서 §3(테스트 계획)을 확정한다.

즉, **다른 에이전트에 의한 “검토”** 는  
- **문서가 완성된 뒤** Step 2( Security ), Step 4( Backend/Frontend ) 등에서만 이루어지는 것이 아니라,  
- **문서 작성 중(Step 1)** 에 Requirements 에이전트가 병렬 호출을 통해 **§1·§2 초안에 대한 입력**을 받는 형태로 설계되어 있다.

---

## 2. 실제로 발생한 것 (예: 20260304-search-history-action-requester-only)

- 사용자가 `/new-requirement` 로 “검색 이력 동작 필드 요청자 전용” 요건을 요청했다.
- **요구사항 문서(§1·§2·§3)** 는 **메인 에이전트(현재 채팅)** 가 직접 작성했다.  
  → **Requirements 서브에이전트는 호출되지 않았다.**
- 그 **이후** 에만:
  - Step 2: Security 서브에이전트 호출 → §2.1 보안 검토
  - Step 4: Backend / Frontend 서브에이전트 호출 → 구현
  - Step 5: QA 서브에이전트 호출 → 검증·§5·커밋

이때 **Step 1 동안에 기대되던 동작**은 다음과 같다.

- Requirements 에이전트가 **작성 중** 에 아래를 **병렬 호출**하는 것:
  - Security: 접근 통제 요건에 대한 §2.1/보안 관점 입력
  - Backend: 검색 이력 서비스/API 관점 코드베이스·문제·해결안
  - Frontend: 검색 이력 화면·동작 버튼 관점 코드베이스·문제·해결안
  - Architecture: frontend/backend 공통화 등 설계 관점 검토 (요건이 frontend+backend를 포함하므로)
  - QA: 시나리오·테스트 가능성·§3 정합성
- 이 입력을 **조율**해 §1(시나리오·기대 결과)·§2(문제 분석·해결 방안·변경 파일 목록 예상)를 채우고, 그 다음 §3을 정리하는 것.

**실제로는**  
- 요구사항 작성 주체가 **Requirements 에이전트가 아니라 메인 에이전트**였기 때문에,  
- 위 **“작성 단계 병렬 호출”** 이 일어나지 않았다.  
- 따라서 **다른 에이전트의 “요구사항 초안에 대한 검토/입력”** 이 누락되었다.

---

## 3. 원인 정리

| 구분 | 내용 |
|------|------|
| **직접 원인** | Step 1(요구사항 문서 작성)을 **Requirements 서브에이전트에 위임하지 않고**, **메인 에이전트가 직접** §1·§2·§3을 작성함. |
| **설계상 책임** | “작성 중 병렬 검토”는 **Requirements 서브에이전트의 역할**로 정의되어 있음 (AGENT-COLLABORATION §1.1, SUBAGENT-DELEGATION Step 1). |
| **결과** | Requirements 에이전트가 호출되지 않았기 때문에, **작성 단계에서** Security/Backend/Frontend/Architecture/QA를 호출하는 로직이 실행되지 않음 → **다른 에이전트의 검토(입력)** 가 §1·§2에 반영되지 않음. |
| **그래도 수행된 것** | 문서가 존재한 **이후** Step 2(Security), Step 4(Backend/Frontend), Step 5(QA)는 수행됨. 즉 “문서 완성 후 검토/구현/검증”은 이루어졌고, **작성 단계의 병렬 검토**만 빠진 상태. |

요약하면:  
**“요구사항이 Requirements 에이전트가 작성한 것이 아니라서, 작성 단계에서 다른 에이전트를 호출하는 흐름이 돌지 않았고, 그 결과 다른 에이전트의 검토가 (작성 시점에서는) 되지 않았다.”** 가 원인이다.

---

## 4. 규칙/문서와의 대응

- **agent-collaboration.mdc**  
  - “Step을 수행하기 전에, 그 Step에 전용 서브에이전트가 있으면 **그 서브에이전트를 Task로 호출**하고, 본 채팅에서 수행하지 말라.”  
  - Step 1의 전용 서브에이전트는 **Requirements** 이므로, 요구사항 문서 작성은 **Requirements 호출**로 이어져야 함.
- **SUBAGENT-DELEGATION.md** §1  
  - Step 1은 Requirements가 담당하고, **작성 중** parallel input을 받아 §1·§2를 조율한다고 명시.
- **new-requirement.md**  
  - “Agent collaboration: … Sequence: **Requirements** → Security → …” 로 되어 있어, **첫 단계가 Requirements** 인 것이 맞음.

즉, **문서/규칙 상으로는**  
- 새 요구사항 시작 시 **Step 1을 메인 에이전트가 직접 하지 말고**,  
- **Requirements 서브에이전트를 Task로 호출**하고,  
- 그 에이전트가 “작성 중 병렬 호출 → §1·§2 조율 → §3 확정”을 하도록 되어 있는데,  
- 실제로는 메인 에이전트가 Step 1을 대신 수행하면서 **Requirements를 거치지 않았고**, 그래서 **다른 에이전트 검토가 (작성 시점에) 누락된 것**으로 볼 수 있다.

---

## 5. 개선 방향 제안

1. **/new-requirement 실행 시 Step 1 위임 강제**  
   - 메인 에이전트는 **요구사항 문서 초안(§1·§2·§3)을 직접 쓰지 않고**,  
   - **Requirements 서브에이전트만** Task로 호출한다.  
   - 호출 시 사용자 요청(및 필요 시 현재 요건 설명)을 prompt에 넣고, “요구사항 문서 작성 및 §1.1에 따른 병렬 검토(Backend/Frontend/Security/Architecture/QA 등) 수행”을 명시한다.
2. **new-requirement.md / agent-collaboration 보강**  
   - “요구사항 문서는 **반드시 Requirements 서브에이전트가 작성**한다. 메인 에이전트는 요구사항 문서 본문을 작성하지 않고, Step 1을 Task(Requirements) 호출로 대체한다.” 를 한 줄로라도 명시하면, 같은 원인으로 인한 검토 누락을 줄일 수 있다.
3. **예외**  
   - 사용자가 “code only here” / “skip subagent” / “do it in this chat” 등으로 **명시적으로** 메인 채팅에서 하라고 한 경우에만, 메인 에이전트가 요구사항 문서를 작성할 수 있도록 예외를 둔다.  
   - 이 경우에도 “다른 에이전트 검토가 작성 단계에서는 수행되지 않을 수 있음”을 규칙/주석에 적어 두면 좋다.

---

**작성일**: 2026-03-04  
**관련**: `AGENT-COLLABORATION-ON-REQUIREMENT.md` §1.1, `SUBAGENT-DELEGATION.md` §1 Step 1, `.cursor/rules/agent-collaboration.mdc`, `.cursor/commands/new-requirement.md`
