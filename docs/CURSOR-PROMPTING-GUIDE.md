# Cursor 프롬프팅 가이드

Cursor에서 이 프로젝트를 작업할 때 **무엇을 어떻게 요청하면 좋은지** 예시 중심으로 정리한 가이드입니다.  
지금까지 자주 쓰인 요청 패턴을 바탕으로 친절하게 안내합니다.

**권장:** 아래 예시를 쓰기 **전에**, [워크플로우 체크리스트](workflow/WORKFLOW_CHECKLIST.md)로 **순서와 게이트**를 먼저 확인하세요.

---

## 1. 새 요건·기능을 추가하고 싶을 때

### 이렇게 요청하세요

- **예**: "UX 표준에 맞지 않는 화면을 파악해서 모두 표준에 맞게 개선해줘"
- **예**: "검색 이력에서 재조회 시 검색 조건이 동일하게 표시되게 해줘"
- **예**: "활동 이력에서 오늘 날짜로 조회했을 때 빈 결과가 나오는 버그 수정해줘"

### 어떻게 진행되나요?

1. **요건 문서 먼저**  
   메인 에이전트가 **Requirements** 서브에이전트에게 위임해서, `docs/requirements/yyyyMMdd-이름.md` 형태의 요건 문서(§1 요구사항, §2 설계, §3 테스트 계획)를 만들게 됩니다.

2. **그다음 구현·검증**  
   필요하면 UX, Contract 등 전문가 검토 후 **Frontend** 또는 **Backend**가 구현하고, **QA**가 검증(헬스 체크, 프론트 변경 시 브라우저 자동화 포함) 후 §5에 결과를 남깁니다.  
   검증이 통과하면 QA가 커밋까지 수행합니다.

3. **한 번에 하고 싶다면**  
   "코드만 여기서 해줘" / "서브에이전트 말고 이 채팅에서 해줘"라고 하면, 메인 채팅에서 요건 문서 작성부터 구현·검증까지 진행할 수 있습니다.

### 슬래시 커맨드 활용

- **`/new-requirement`** 뒤에 요구사항을 적으면, 위 흐름(요건 문서 → 구현 → 검증)이 자동으로 따라 적용됩니다.

---

## 2. 테스트·검증을 브라우저 자동화로 돌리고 싶을 때

### 이렇게 요청하세요

- **예**: "TC01~TC07 같은 걸 Browser Automation 기능으로 직접 검증할 수 있게 해줘"
- **예**: "/QA §3.5 브라우저 자동화로 TC-01~TC-07 실행 후 §5 반영해줘"

### 어떻게 진행되나요?

- 요건 문서에 **§3.5 브라우저 자동화 검증** 절차가 있으면, **QA** 서브에이전트가 Browser MCP로 TC를 실행하고 결과를 §5에 반영합니다.
- 프론트엔드 변경이 있는 요건은, 정책상 **브라우저 자동화 검증이 필수**이며, 실패 시 상세 리포트가 §5에 기록되고 Requirements를 거쳐 담당 전문가(Frontend 등)에게 수정이 위임됩니다.

### 참고

- 브라우저 자동화 정책: [docs/workflow/BROWSER-AUTOMATION-VERIFICATION-POLICY.md](workflow/BROWSER-AUTOMATION-VERIFICATION-POLICY.md)
- 검증 절차: `.cursor/commands/verify.md` (step 3.5)

---

## 3. 검증 실패 시 자동으로 담당자에게 넘기고 싶을 때

### 이렇게 요청하세요

- **예**: "테스트 중 프론트엔드나 QA 담당이 아닌 경우, Requirements에게 전달해서 해당 전문가에게 위임하고, issue close되면 다시 QA 절차 수행하는 흐름으로 개선해줘"
- **예**: "프론트엔드에게 바로 위임하지 말고, 해당 내용을 Requirements에게 먼저 위임하도록 해줘"

### 어떻게 진행되나요?

- 검증 실패 시 QA는 **항상 Requirements**에 먼저 넘깁니다 (Frontend/Backend로 바로 넘기지 않음).
- Requirements가 요건 문서를 정리한 뒤 **실패 범위(frontend, backend, db, security 등)**에 따라 **해당 전문가**에게만 위임합니다.
- 전문가가 수정 후 **issue closed**로 QA에 넘기면, QA가 **다시 검증**을 수행하고, 전부 통과하면 커밋합니다.

---

## 4. 도구·문서 정책을 바꾸고 싶을 때 (영어 통일, 요건 영문 우선 등)

### 이렇게 요청하세요

- **예**: "도구가 활용하는 모든 문서를 영어로 통일해줘"
- **예**: "요구사항은 영문으로 먼저 작성하고, 검증 다 끝난 뒤 한글 최종본 만들고, commit message에는 해당 문서 참조해서 버전별로 뭘 했는지 알 수 있게 해줘"

### 어떻게 진행되나요?

- **도구 문서**: `docs/workflow`, `docs/template`, `.cursor/` 등 도구가 참조하는 문서는 영어로 통일하는 방향으로 반영됩니다.
- **요건 문서**: 영문으로 먼저 작성(§1·§2·§3)하고, **검증이 모두 끝난 뒤** 같은 문서에 § Final version (Korean)을 추가하거나 `-ko.md` 한글 최종본을 만들 수 있습니다.
- **커밋 메시지**: 요건을 마무리하는 커밋에는 `req yyyyMMdd-name` 또는 `docs/requirements/yyyyMMdd-name.md`를 넣어서, 커밋 버전별로 어떤 요건 작업인지 추적할 수 있게 합니다.

### 참고

- [docs/workflow/DOCUMENT-LANGUAGE-POLICY.md](workflow/DOCUMENT-LANGUAGE-POLICY.md)
- `.cursor/commands/commit-on-complete.md`

---

## 5. CHANGELOG·커밋·푸시를 한 번에 하고 싶을 때

### 이렇게 요청하세요

- **예**: "/Release 방금 수정된 도구/문서 관련 내용 push까지 진행해줘"
- **예**: "/Release 별도 commit 및 push까지 진행해줘"
- **예**: "/Release UX 관련 공통 사항 반영에 대한 내용도 push해줘"
- **예**: "/Release 로컬 버전이 원격과 모두 동일한지 체크하고, push할 게 남았으면 모두 올려줘"

### 어떻게 진행되나요?

- **Release** 서브에이전트가 CHANGELOG 반영, 커밋, 푸시를 수행합니다.
- 도구/문서만 바꾼 경우 CHANGELOG + 해당 파일들만 커밋하고, 사용자가 "push까지"라고 하면 `git push`까지 실행합니다.
- "로컬과 원격 동일한지 체크하고 push할 게 있으면 올려줘"라고 하면, 로컬이 원격보다 앞선 커밋이 있을 때만 push합니다. 이미 동일하면 "push할 내용 없음"으로 안내합니다.

### 주의

- `.env`, `frontend/build/`, `node_modules/.cache/`는 보통 커밋 대상에서 제외됩니다.  
  Release는 이 경로들을 스테이징하지 않습니다.

---

## 6. QA 다음 단계·재검증을 이어가고 싶을 때

### 이렇게 요청하세요

- **예**: "/QA 검증한 내용에 대한 다음 단계를 진행해줘"
- **예**: "/QA §3.5 브라우저 자동화로 TC-01~TC-07 실행 후 §5 반영해줘"
- **예**: "/QA 다음 단계를 진행해줘" (이미 구현된 bugfix에 대해 TC 재검증 등)

### 어떻게 진행되나요?

- **QA** 서브에이전트가 요건 문서 §3·§3.5를 보고 검증(헬스 체크, 브라우저 자동화 등)을 실행하고, §5를 갱신합니다.
- bugfix처럼 "다음 단계"가 정해진 경우, TC 재검증 → 통과 시 §5·§6 반영 후 커밋까지 QA가 수행합니다.
- 백엔드/프론트가 떠 있지 않으면 QA가 재시작을 시도한 뒤 검증을 진행할 수 있습니다.

---

## 7. 요청 시 유의할 점

- **서브에이전트 위임**  
  요건 문서 작성, 구현, 검증, 커밋 등은 각각 전용 서브에이전트(Requirements, Frontend, Backend, QA, Release 등)가 담당합니다.  
  "코드만 여기서", "서브에이전트 건너뛰고 이 채팅에서 해줘"라고 하지 않는 한, 메인 에이전트는 **위임**만 하고 직접 그 단계를 수행하지 않습니다.

- **언어**  
  사용자가 한국어로 요청하면 응답도 한국어로 합니다. 코드·파일 경로·식별자는 그대로 두고, 설명만 한국어로 맞춥니다.

- **보안**  
  `.env`, `secrets/` 등은 읽지 않고, `git push --force` 같은 위험한 명령은 사용자 확인 없이 실행하지 않습니다.

---

## 8. 더 자세히 보고 싶을 때

| 목적 | 문서 |
|------|------|
| 개발 문서 전체 구조·요건 요청 방법 | [docs/README.md](README.md) |
| 워크플로우 순서·게이트 | [docs/workflow/WORKFLOW_CHECKLIST.md](workflow/WORKFLOW_CHECKLIST.md) |
| 서브에이전트 위임 표 | [docs/workflow/SUBAGENT-DELEGATION.md](workflow/SUBAGENT-DELEGATION.md) |
| 브라우저 자동화·검증 실패 시 위임 | [docs/workflow/BROWSER-AUTOMATION-VERIFICATION-POLICY.md](workflow/BROWSER-AUTOMATION-VERIFICATION-POLICY.md) |
| 문서 언어·요건 영문 우선·커밋 참조 | [docs/workflow/DOCUMENT-LANGUAGE-POLICY.md](workflow/DOCUMENT-LANGUAGE-POLICY.md) |
| 빠른 시작 | [docs/QUICK_START.md](QUICK_START.md) |
| API·DB·포트 계약 | [docs/contract.md](contract.md) |

---

**마지막 업데이트**: 2026-02-26
