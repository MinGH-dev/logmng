# 에이전트 추가 검토 (합리적·일관된 결과물을 위한 에이전트 검토)

요청: "더 합리적이고 일관성 있는 결과물을 만들기 위해 더 추가해야 할 agent가 있을지 검토해줘."

**반영 완료**: Review, Documentation, Release, Consistency, UX 5개 에이전트를 추가했고, **역할 중복 방지**를 `docs/workflow/CURSOR-SUBAGENTS-DESIGN.md` §2.6에 정의해 두었다. 협업 순서는 `docs/workflow/AGENT-COLLABORATION-ON-REQUIREMENT.md`에 Step 3d, 4.5, 6로 반영되어 있다.

---

## 1. 현재 에이전트 구성 요약 (추가 반영 후)

| 구분 | 에이전트 | 역할 | 코드 수정 |
|------|----------|------|-----------|
| **구현** | Frontend, Backend, DB | 개발·담당 영역 요건 정리·단위/통합 테스트 | O |
| **문서·스펙** | Requirements | 요건 문서(§1·§2·§3), 스펙 초안 | X |
| **계약** | Contract | contract.md, specs 정의·갱신 | X |
| **검토** | Security, DBA, Architecture | 보안·스키마·성능 관점 설계 검토, 권고안 | X |
| **테스트·검증** | QA | 테스트 설계, §3·§5·§6, 검증 체크리스트 | 문서만 |
| **검토** | Review | 코드/변경 검토(계약·워크플로우·품질·표준 적용). 코드 수정 없음. | X |
| **문서** | Documentation | 사용자/운영 문서(README, QUICK_START, 런북). 요건·스펙·코드 수정 없음. | X |
| **릴리스** | Release | CHANGELOG, 버전, 릴리스 체크리스트. 사용자 가이드·코드 수정 없음. | X |
| **일관성** | Consistency | 표준 문서(CONSISTENCY-STANDARDS.md) 정의·갱신. 검토 실행·코드 수정 없음. | X |
| **UX** | UX | 디자인/UX 검토(a11y, UI 일관성). 코드 구현 없음. | X |
| **선택(모듈별)** | Backend-Auth, Backend-ActivityLog, Backend-Log, Frontend-Auth, Frontend-ActivityLog, Frontend-Log | 특정 모듈만 수정해 일관성 유지 | O |

협업 순서·역할 중복 방지: `docs/workflow/AGENT-COLLABORATION-ON-REQUIREMENT.md`, `docs/workflow/CURSOR-SUBAGENTS-DESIGN.md` §2.6.

---

## 2. 격차 분석 (합리성·일관성 관점)

| 격차 | 설명 | 현재 담당 | 보완 방향 |
|------|------|-----------|-----------|
| **코드/변경물 검토** | 구현이 계약·워크플로우·네이밍·에러 코드·로깅 규칙을 따르는지 **체계적으로 검토**하는 역할이 없음. `review.md`는 셀프 체크리스트일 뿐, 검토 수행 에이전트는 없음. | 없음 (개발자가 체크리스트 수행) | **Review** 에이전트: 변경분을 읽고 계약·규칙·체크리스트 기준으로 검토, 제안만 출력(코드 수정 없음). |
| **사용자/운영 문서 일원화** | README, QUICK_START, 배포·운영 가이드는 요건 문서·계약과 별도로 유지. 요구사항 반영 후 "사용자용/운영용 문서" 갱신을 전담하는 역할이 없음. | 요건 문서는 Requirements, API는 Contract; 나머지는 분산 | **Documentation** 에이전트(선택): 사용자/운영 문서만 담당, 코드·요건 문서 작성은 하지 않음. |
| **릴리스·변경 이력** | CHANGELOG, 버전, 릴리스 체크리스트는 `commit-on-complete.md`와 연계되나 전담 에이전트 없음. | 없음 | 규칙/커맨드로 충분할 수 있음; 필요 시 Documentation 또는 별도 **Release** 에이전트. |
| **일관성(네이밍·구조)** | API 오류 형식, 로깅 패턴, 파일 구조 등 프로젝트 전반 일관성은 규칙·Contract에 의존. | Contract, 규칙, 각 구현 에이전트 | Review 에이전트가 "계약·표준 준수" 검토 시 함께 점검 가능. |

---

## 3. 권고: 추가 권장 에이전트

### 3.1 Review (코드/변경물 검토) — **권장**

- **역할**: 구현·스펙 변경물을 **읽기만** 하고, 다음 기준으로 검토·제안.
  - **계약 준수**: API·DB가 `docs/contract.md`, `specs/`와 일치하는지.
  - **워크플로우**: 요건 문서 존재, §3 테스트 계획·§5 반영 여부.
  - **품질·일관성**: 입력 검증, 에러 코드·메시지 일관성, 로깅 수준·PII 미포함, NFR(지연/부하) 고려 여부.
- **산출물**: 검토 체크리스트 결과(통과/미통과), 수정 제안(어디를 어떻게 고칠지). **코드는 수정하지 않음** — Backend/Frontend/DB가 제안 반영.
- **호출 시점**: Step 4(구현) 완료 후, Step 5(QA) 전 또는 병행. 또는 PR 전 셀프 리뷰 대신 "Review 에이전트로 검토해줘" 호출.
- **효과**: 계약·규칙·체크리스트 준수와 결과물 일관성이 높아짐. QA(테스트 설계·결과)와 역할 분리: QA = 무엇을 테스트할지·결과 기록, Review = 코드/변경이 표준을 따르는지.

### 3.2 Documentation (사용자/운영 문서) — **선택**

- **역할**: **사용자·운영자용 문서**만 작성·갱신. 예: README, QUICK_START, 배포/운영 가이드, 트러블슈팅. `docs/requirements/`·스펙 작성은 하지 않음(Requirements·Contract 담당).
- **호출 시점**: 요건 완료·기능 반영 후 "사용자 문서/운영 문서 갱신" 필요 시. 협업 순서에서는 Step 5 이후 또는 별도 트리거.
- **효과**: 사용자·운영 문서가 한 곳 역할로 관리되어 최신 상태 유지가 쉬워짐. 팀이 작으면 기존 에이전트가 부가적으로 갱신해도 되므로 **선택** 권고.

---

## 4. 추가하지 않는 것이 합리적인 경우

| 후보 | 권고 | 이유 |
|------|------|------|
| **Release / Changelog** | 별도 에이전트 비권장 | `commit-on-complete.md`·체크리스트와 문서화로 충분. 필요 시 Documentation 에이전트 범위에 "CHANGELOG·릴리스 노트" 포함. |
| **Consistency / Standards** | 별도 에이전트 비권장 | Contract·규칙·Review(권장)로 커버 가능. 전용 에이전트는 중복. |
| **UX / Design** | 별도 에이전트 비권장 | Frontend에 a11y·품질 지침 있음. 디자인 시스템·전담 디자이너가 있으면 그때 검토. |
| **DevOps / CI** | 당장은 비권장 | CI/CD 파이프라인·공식 러너북이 필요해지면 그때 DevOps 또는 Infrastructure 에이전트 검토. |

---

## 5. 결론 및 다음 단계

- **추가 권장**: **Review** — 코드/변경물이 계약·워크플로우·품질 기준을 따르는지 검토만 수행하는 에이전트. 합리성·일관성 향상에 직접 기여.
- **선택 검토**: **Documentation** — 사용자/운영 문서 전담. 팀 규모·문서 양이 늘면 도입.
- **협업 순서 반영**: Review를 도입할 경우 `AGENT-COLLABORATION-ON-REQUIREMENT.md`에 "Step 4.5" 또는 "Step 5 직전"으로 삽입하고, `.cursor/agents/Review.mdc` 및 `docs/cursor-subagents/review.md`(또는 동일 이름) 추가. Documentation 도입 시에는 Step 5 이후 또는 별도 단계로 명시.

이 검토를 바탕으로 Review 에이전트 추가 여부를 결정하면 된다.
