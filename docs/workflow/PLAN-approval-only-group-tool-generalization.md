# Plan: Approval-only permission group — 도구 일반화 (이름 무관 규칙 적용)

**목적**: 현재 "APPROVE_USER"라는 **이름**에 묶여 있는 승인 전용 규칙을, **그룹 이름과 무관하게** "승인 전용 권한 그룹" **조건**으로 정의하고, 다른 승인 그룹(예: TEAM_APPROVER, REGIONAL_APPROVER)을 만들어도 동일 규칙이 적용되도록 도구(skills, specs, docs)를 개선한다.

**범위**: 코드 동작 변경 없음. 프론트/백엔드는 이미 `allowedScreenIds`·`screenFunctions` 기준으로 동작하므로, **문서·스킬·스펙**만 정리하면 된다.

---

## 1. 정의: 승인 전용 권한 그룹 (Approval-only permission group)

### 1.1 `allowedScreenIds`에 화면이 들어가는 규칙 (근거)

**`allowedScreenIds`는 권한 그룹 설정에서 오며, 그룹 이름과 무관하다.**

- 사용자는 **단일 권한 그룹** 하나만 가짐 (`app_user_permission_group`).
- 백엔드 **AuthService**는 로그인/세션 시 `PermissionGroupService.getAllowedScreenIdsForUser(username)`를 호출한다.
- **getAllowedScreenIdsForUser** 규칙:
  - `permission_group_screen` 테이블에서 **해당 사용자의 권한 그룹**에 연결된 행만 조회 (`app_user_permission_group` JOIN).
  - 그 중 **`read IS NULL OR read = true`** 인 행의 `screen_id`만 수집해 리스트로 반환.
- 이 리스트가 로그인/GET `/api/auth/me` 응답의 **`allowedScreenIds`** 가 된다.

즉, **어떤 화면(예: `pending-approvals`)이 `allowedScreenIds`에 들어가려면**,  
그 사용자의 권한 그룹에 대해 **`permission_group_screen`에 (permission_group_id, screen_id='pending-approvals') 행이 있고, `read`가 false가 아니어야** 한다.  
→ **권한 그룹 CRUD(생성/수정)** 시 해당 그룹에 `pending-approvals` 화면을 부여하면 들어간다.** (규칙: DB + 백엔드 조회 로직.)

### 1.2 승인 전용 그룹의 조건 (이름 무관)

다음을 모두 만족하는 권한 그룹을 **승인 전용**으로 본다.

- `allowedScreenIds`에 **`main`이 없음** (로그 검색 화면 접근 불가)
- `allowedScreenIds`에 **`pending-approvals`가 있음** (위 규칙에 따라, 해당 그룹에 `permission_group_screen`으로 pending-approvals가 부여되어 있고 read가 false가 아님)
- (선택) 해당 화면에 **approve = true** (승인/반려 기능 사용 가능).  
  실제 승인 수행은 `decrypt_approver` 테이블 등록 여부로 백엔드가 판단.

**적용 규칙 (그룹 코드/이름과 무관)**  
위 조건을 만족하는 **어떤** 권한 그룹(APPROVE_USER, TEAM_APPROVER, CUSTOM_APPROVER 등)에도 동일하게 적용:

- 로그인 후 초기 화면: `main`이 없으면 첫 허용 화면(예: pending-approvals)으로 리다이렉트.
- 메뉴: `allowedScreenIds`에 있는 화면만 노출 (main 없으면 로그 검색·검색 이력 메뉴 없음).
- 검색 이력 화면: 재조회/재요청/자세히 보기는 **요청자만** (이미 구현됨); main 없으면 재조회·재요청은 애초에 노출 안 함(요청자여도 main 없으면 숨김 등 기존 정책 유지).
- API: `main` 없으면 로그 검색 API 403; pending-approvals + decrypt_approver면 승인/반려 API 허용.

---

## 2. 개선 대상 파일 및 수정 방향

### 2.1 스킬 (Skills) — 최우선

| 파일 | 현재 | 개선 방향 |
|------|------|------------|
| `.cursor/skills/auth-permission-domain/SKILL.md` | § "APPROVE_USER pattern" — 예시로 `APPROVE_USER` 그룹 이름 사용 | **§ "Approval-only permission group"** 으로 제목 변경. 본문에서 "조건( main 없음 + pending-approvals 있음 )"으로 정의하고, "예: APPROVE_USER, TEAM_APPROVER 등 **이름 무관**" 문구 추가. "APPROVE_USER pattern" → "approval-only group" 용어로 통일. |
| `.cursor/skills/search-history-decrypt-domain/SKILL.md` | APPROVE_USER pattern (승인 전용 권한 그룹) … 권한 그룹(APPROVE_USER) | "승인 전용 권한 그룹" 정의를 **조건 기반**으로 바꿈. "이름이 APPROVE_USER가 아닌 그룹도 동일 규칙 적용" 명시. auth-permission-domain §로 링크 유지. |
| `.cursor/skills/department-approver-domain/SKILL.md` | APPROVE_USER pattern … 권한 그룹(APPROVE_USER) | "승인 전용 권한 그룹(조건: main 없음, pending-approvals 있음). 예: APPROVE_USER, 팀 내 승인권자 전용 그룹 등." 로 수정. |

**공통 규칙**  
- "APPROVE_USER"는 **예시 그룹 코드**로만 언급 (예: "예: code=APPROVE_USER").  
- 규칙의 적용 대상은 **"승인 전용 권한 그룹"** = 위 조건을 만족하는 **모든** 권한 그룹.

---

### 2.2 스펙 (Specs)

| 파일 | 수정 내용 |
|------|-----------|
| `specs/permission-group-hierarchy.spec.yaml` | §4 (Screen IDs) 뒤 또는 별도 절에 **"Approval-only permission groups"** 추가: (1) 정의: main 없음 + pending-approvals 있음 (+ approve). (2) 동작: 초기 뷰/메뉴/API 규칙은 그룹 **이름/코드와 무관**하게 동일 적용. (3) 예시: APPROVE_USER, TEAM_APPROVER 등. |

---

### 2.3 계약/문서 (Contract, requirements)

| 파일 | 수정 내용 |
|------|-----------|
| `docs/contract.md` | "화면 기반 접근 제어" 또는 관련 절에 한 줄: **승인 전용 권한 그룹**은 main 없이 pending-approvals(및 approve)만 가진 그룹이며, 그룹 이름과 무관하게 동일 UX/API 규칙 적용. 상세는 `specs/permission-group-hierarchy.spec.yaml` §Approval-only. |
| `docs/requirements/20260304-approve-only-permission-group.md` | 제목/본문에서 "APPROVE_USER"를 "승인 전용 권한 그룹(approval-only)"으로 유지하되, "이 요건은 **APPROVE_USER 뿐 아니라** 동일 조건을 만족하는 모든 그룹에 적용된다"는 문단 추가. TC/시나리오에서 "APPROVE_USER"는 **테스트용 예시 그룹**으로만 표기. |
| `docs/requirements/20260304-permission-group-modal-error-visibility.md` | "APPROVE_USER" → "승인 전용 권한 그룹(예: APPROVE_USER)" 등으로 완화. (모달 오류 가시성 요건은 유지.) |
| `docs/requirements/TOPIC-INDEX.md` | 기존 "APPROVE_USER" 언급을 "approval-only permission group" 또는 "승인 전용 권한 그룹"으로 정리해도 됨 (선택). |

---

### 2.4 코드·테스트

| 파일 | 수정 필요 여부 |
|------|----------------|
| `backend/.../PermissionGroupServiceTest.java` | 주석 "APPROVE_USER-like" 유지 가능. 필요 시 "approval-only (e.g. APPROVE_USER)" 로만 정리. |
| `frontend/src/App.js` 등 | **변경 없음.** 이미 `allowedScreenIds`·`getFirstAllowedScreen` 기준. |

---

## 3. (추가) 그룹 권한 관리 UI 개선: 승인 지원 화면은 "승인 or 조회만" 단일 선택

**배경**: 승인이 들어간 화면(search-history, pending-approvals)은 스펙상 **read 필수 + approve 선택**. 현재 UI는 "조회 ✓" 라벨 + "승인" 토글로 되어 있어, 의미상 **"조회만" vs "조회+승인"** 두 가지인데 한눈에 안 들어올 수 있음.

**개선 방향**: 승인 지원 화면에서는 **"조회만" | "승인"** 을 **단일 선택**(라디오/세그먼트)으로 보여 주어, "이 화면은 조회만 할지, 승인까지 할지"만 고르게 한다.

| 항목 | 현재 | 개선 후 |
|------|------|---------|
| approve 지원 화면 (search-history, pending-approvals) | 화면 체크 시 "조회 ✓" + "승인" 토글 버튼 | 화면 체크 시 **"조회만" / "승인"** 중 하나 선택 (라디오 또는 세그먼트 컨트롤) |
| 의미 | read=true 고정, approve=true/false | **조회만** → read=true, approve=false / **승인** → read=true, approve=true |
| API/백엔드 | 변경 없음 (동일 payload: read, approve) | 변경 없음 |

**적용 대상**: `frontend/src/components/PermissionGroupManagement/ScreenSelectionTree.js` — `SCREENS_WITH_APPROVE` 인 화면일 때만, "조회 ✓" + approve 토글 대신 **"조회만" | "승인"** 단일 선택 UI로 교체.  
**스펙/계약**: `specs/permission-group-hierarchy.spec.yaml` §1.1.1 검증 규칙은 그대로 두고, 필요 시 "UI에서 승인 지원 화면은 조회만/승인 중 하나로 선택 권장" 문구만 추가.

**완료 기준**: (1) 승인 지원 화면에서 "조회만" 선택 시 approve=false, "승인" 선택 시 approve=true로 저장. (2) 기존 그룹 편집 시 값이 올바르게 "조회만"/"승인"으로 표시. (3) API 요청/응답 형식 변경 없음.

---

## 4. 작업 순서 제안

1. **스펙에 정의 추가**  
   `specs/permission-group-hierarchy.spec.yaml`에 **Approval-only permission groups** 절 추가 (정의 + 적용 규칙).
2. **스킬 3개 수정**  
   `auth-permission-domain` → `search-history-decrypt-domain` → `department-approver-domain` 순으로, "APPROVE_USER pattern"을 "approval-only (조건 기반, 이름 무관)"으로 교체.
3. **contract.md**  
   한 줄 요약 + 스펙 § 참조.
4. **요구사항 문서**  
   20260304-approve-only-permission-group, 20260304-permission-group-modal-error-visibility에 "다른 승인 그룹에도 동일 규칙" 문구 추가.
5. **(선택)** TOPIC-INDEX, 테스트 주석 등에서 용어 정리.

---

## 5. 완료 기준 (Definition of Done)

**§1–2 도구 일반화**
- [ ] 스펙에 "approval-only permission group"이 **조건(main 없음, pending-approvals 있음)** 으로 정의되어 있음.
- [ ] 세 스킬에서 규칙이 **그룹 이름/코드가 아닌 조건**으로 서술되어 있고, "다른 승인 그룹을 만들어도 동일 규칙 적용"이 명시됨.
- [ ] contract.md에 승인 전용 그룹이 이름 무관 규칙으로 한 줄 이상 반영됨.
- [ ] 관련 요구사항 문서에서 APPROVE_USER는 예시로만 쓰이고, "동일 조건의 그룹 모두 적용"이 드러남.
- [ ] 기존 동작(리다이렉트, 메뉴, API 403)은 변경 없음 — 문서/도구만 일반화.

**§3 그룹 권한 관리 UI (승인 화면: 조회만 | 승인 단일 선택)**
- [ ] 승인 지원 화면(search-history, pending-approvals)에서 "조회만" / "승인" 단일 선택 UI 적용.
- [ ] 저장/편집 시 read·approve 값이 스펙과 일치하고, API 형식 변경 없음.

---

**작성일**: 2026-03-04  
**상태**: 계획 확정 후 스펙·스킬·문서 수정 진행
