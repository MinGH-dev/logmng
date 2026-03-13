# Permission by screen (화면별 권한 부여 — 권한관리 화면 참고용)

권한관리 화면(권한 그룹 관리 / 사용자 권한 계층)에서 **화면별로 설정 가능한 항목이 다르다**. 이 문서는 그 차이를 한눈에 보기 위해 정리한 것이다. 다른 팀/도구에 전달할 때 이 문서를 사용하면, “어떤 화면에 어떤 UI가 나오고, 어떤 값이 API로 전송되는지”를 혼란 없이 전달할 수 있다.

**용어**
- **조회 범위(scope)**: 목록/조회 시 데이터 범위 — **본인**(self) | **부서**(team) | **전체**(all). API 값: `self` | `team` | `all`.
- **승인 범위**: 승인/반려 가능 범위. **항상 부서 단위로 고정**이며, 권한 설정에서 변경할 수 없다. “조회 범위” 드롭다운은 목록만 적용된다.
- **기능(read/write/approve/decrypt)**: 화면별로 줄 수 있는 권한. read는 화면 선택 시 항상 포함; write/approve/decrypt는 화면마다 지원 여부가 다르다.

---

## 1. 화면별 설정 가능 항목 요약표

| 화면 ID (screen_id) | 메뉴 라벨 | 조회 범위 (scope) | 조회 ✓ | 수정 (write) | 승인 (approve) | 복호화 (decrypt) | 비고 |
|--------------------|-----------|-------------------|--------|--------------|----------------|------------------|------|
| main | 검색하기 | ✗ 없음 | ✓ (고정) | ✗ 불가 | ✗ 불가 | ✓ 선택 | decrypt=true 시 복호화 요청 가능 |
| search-history | 검색 이력 | ✓ 본인/부서/전체 | ✓ (고정) | ✗ 불가 | ✓ 선택 | ✗ 불가 | approve 선택 시 “승인” 역할; **승인 선택 시 scope=부서 고정** |
| activity-log | 활동 이력 | ✓ 본인/부서/전체 | ✓ (고정) | ✗ 불가 | ✗ 불가 | ✗ 불가 | scope만 설정 |
| statistics | 활동로그 통계 | ✓ 본인/부서/전체 | ✓ (고정) | ✗ 불가 | ✗ 불가 | ✗ 불가 | scope만 설정 |
| pending-approvals | 승인 대기 | ✓ 본인/부서/전체 | ✓ (고정) | ✗ 불가 | ✓ 선택 | ✗ 불가 | approve 선택 시 승인/반려 가능; **승인 선택 시 scope=부서 고정** |
| user-management | 사용자 관리 | ✗ 없음 | ✓ (고정) | ✓ 선택 | ✗ 불가 | ✗ 불가 | write로 수정 권한 부여 |
| department-approvers | 부서별 결재자 | ✗ 없음 | ✓ (고정) | ✓ 선택 | ✗ 불가 | ✗ 불가 | (메뉴에 없을 수 있음) |
| user-permission-hierarchy | 사용자 권한 계층 | ✗ 없음 | ✓ (고정) | ✓ 선택 | ✗ 불가 | ✗ 불가 | write로 그룹/사용자 할당 수정 |
| permission-group-management | 권한 그룹 관리 | ✗ 없음 | ✓ (고정) | ✓ 선택 | ✗ 불가 | ✗ 불가 | user-permission-hierarchy와 동일 |

- **조회 ✓**: 해당 화면을 허용하면 항상 “조회” 가능. UI에서는 “조회 ✓”로만 표시하고 별도 체크박스 없음.
- **수정/승인/복호화**: “✓ 선택”인 화면만 권한관리 UI에 **토글 또는 라디오**가 노출되고, on/off 값을 API로 보냄.

---

## 2. 권한관리 화면에서의 UI 동작 (화면별)

권한 그룹 편집 시, “허용 화면” 트리에서 **화면을 선택(체크)** 하면 그 아래에 아래 규칙대로 컨트롤이 나온다.

### 2.1 main (검색하기)

- **나타나는 것**: “조회 ✓” 라벨, **복호화** 토글만.
- **나타나지 않는 것**: 조회 범위(scope) 드롭다운, 수정, 승인.
- **복호화**: 켜면 해당 그룹 사용자는 검색하기에서 복호화 요청 가능; 끄면 복호화 API 403.
- **API**: `allowedScreens` 항목에 `{ screenId: "main", decrypt: true|false }`. scope/write/approve 보내지 않음(또는 무시).

### 2.2 search-history (검색 이력)

- **나타나는 것**: “조회 ✓”, **조회 범위** 드롭다운(본인/부서/전체), **조회 vs 승인** 라디오(둘 중 하나만 선택).
- **나타나지 않는 것**: 수정, 복호화.
- **조회 범위**:
  - 라디오가 **“조회”** 일 때: 드롭다운 **활성**. 사용자가 본인/부서/전체 중 선택 가능. 기본값 **부서**.
  - 라디오가 **“승인”** 일 때: 드롭다운 **비활성(고정)**. 표시는 “부서”로만 하고, API에는 항상 `scope: "team"` 전송. (승인 범위는 부서 고정이므로.)
- **API**: `{ screenId: "search-history", scope: "self"|"team"|"all", approve: true|false }`.

### 2.3 activity-log (활동 이력)

- **나타나는 것**: “조회 ✓”, **조회 범위** 드롭다운(본인/부서/전체)만.
- **나타나지 않는 것**: 수정, 승인, 복호화.
- **기본값**: scope 생략 시 **부서**(team).
- **API**: `{ screenId: "activity-log", scope: "self"|"team"|"all" }`.

### 2.4 statistics (활동로그 통계)

- **나타나는 것**: “조회 ✓”, **조회 범위** 드롭다운(본인/부서/전체)만.
- **나타나지 않는 것**: 수정, 승인, 복호화.
- **기본값**: scope 생략 시 **부서**(team).
- **API**: `{ screenId: "statistics", scope: "self"|"team"|"all" }`.

### 2.5 pending-approvals (승인 대기)

- **나타나는 것**: “조회 ✓”, **조회 범위** 드롭다운, **조회 vs 승인** 라디오.
- **나타나지 않는 것**: 수정, 복호화.
- **조회 범위**: search-history와 동일. **“승인” 선택 시** 드롭다운 비활성, 표시 “부서”, API에는 `scope: "team"`.
- **API**: `{ screenId: "pending-approvals", scope: "self"|"team"|"all", approve: true|false }`.

### 2.6 user-management (사용자 관리)

- **나타나는 것**: “조회 ✓”, **수정** 토글만.
- **나타나지 않는 것**: 조회 범위, 승인, 복호화.
- **수정**: 켜면 그룹 사용자가 사용자 목록 조회 및 (구현된 경우) 사용자 관련 수정 가능; 끄면 조회만.
- **API**: `{ screenId: "user-management", write: true|false }`.

### 2.7 user-permission-hierarchy / permission-group-management (사용자 권한 계층 / 권한 그룹 관리)

- **나타나는 것**: “조회 ✓”, **수정** 토글만.
- **나타나지 않는 것**: 조회 범위, 승인, 복호화.
- **수정**: 켜면 권한 그룹 CRUD, 그룹에 사용자 할당/해제 가능.
- **API**: `{ screenId: "user-permission-hierarchy", write: true|false }` 또는 `{ screenId: "permission-group-management", write: true|false }`.

### 2.8 department-approvers (부서별 결재자)

- **나타나는 것**: “조회 ✓”, **수정** 토글만.
- **나타나지 않는 것**: 조회 범위, 승인, 복호화.
- **API**: `{ screenId: "department-approvers", write: true|false }`.

---

## 3. 혼동하기 쉬운 점 정리

| 혼동 포인트 | 설명 |
|-------------|------|
| **“조회 범위” vs “승인 범위”** | 조회 범위(scope)는 **목록/조회 시** 본인/부서/전체만 구분한다. **승인(approve) 가능 범위**는 별도이며, 항상 **부서 단위**로 고정되어 있어서 권한 설정에서 바꿀 수 없다. 따라서 search-history·pending-approvals에서 “승인”을 선택하면 scope 드롭다운은 “부서”로 고정되고 비활성화된다. |
| **화면마다 나오는 항목이 다름** | main은 scope 없고 decrypt만; activity-log/statistics는 scope만; search-history/pending-approvals는 scope + approve 라디오; 관리 화면들은 write만. 동일한 “권한 설정” UI라도 **화면별로 노출되는 필드는 다르다.** |
| **approve와 decrypt_approver** | approve=true로 주어도, 실제로 승인/반려를 수행하려면 **부서별 결재자(decrypt_approver)** 로 지정되어 있거나 시스템 관리자여야 한다. 권한 그룹의 “승인”은 “이 화면에서 승인 기능을 쓸 수 있게 할지”만 정한다. |
| **scope 기본값** | scope를 지원하는 화면(activity-log, statistics, search-history, pending-approvals)에서 값을 안 넣거나 null이면 **부서(team)** 로 간주한다. |

---

## 4. API 요청 형식 (allowedScreens)

권한 그룹 생성/수정 시 `allowedScreens`는 아래 형태의 배열이다.

```ts
type AllowedScreenItem = {
  screenId: string;   // 필수. 위 표의 화면 ID 중 하나
  scope?: 'self' | 'team' | 'all';  // scope 지원 화면만. 생략/null → 'team'
  read?: boolean;     // 보통 생략. 화면 있으면 true
  write?: boolean;   // 수정 지원 화면만 (user-management, department-approvers, user-permission-hierarchy, permission-group-management)
  approve?: boolean; // search-history, pending-approvals만
  decrypt?: boolean; // main만
};
```

- **검증**: main에 write/approve를 true로 보내면 400 INVALID_SCREEN_FUNCTION. search-history/pending-approvals에 write/decrypt true도 마찬가지. approve=true인데 scope를 self/all로 보내면, 서버에서 승인 범위가 부서 고정이므로 scope는 team으로 처리하는 구현이 일반적이다(프론트는 “승인” 선택 시 scope를 비활성하고 team만 보냄).

---

## 5. 화면별 권한 부여 — 한 줄 요약 (전달용)

- **main**: 조회 고정 + **복호화** on/off만 설정.
- **search-history**: 조회 고정 + **조회 범위**(본인/부서/전체) + **조회 vs 승인** 라디오. 승인 선택 시 범위는 부서 고정.
- **activity-log**: 조회 고정 + **조회 범위**(본인/부서/전체)만.
- **statistics**: 조회 고정 + **조회 범위**(본인/부서/전체)만.
- **pending-approvals**: 조회 고정 + **조회 범위** + **조회 vs 승인** 라디오. 승인 선택 시 범위는 부서 고정.
- **user-management, department-approvers, user-permission-hierarchy, permission-group-management**: 조회 고정 + **수정** on/off만.

이 문서를 기준으로 하면, 권한관리 화면의 “화면별로 다른 설정 항목”을 다른 쪽에 일관되게 전달할 수 있다.
