# 사용자 관련 검색 규칙 적용을 위한 공통 기능 — 현재 구성

규칙(부서·이름·사용자ID 통일, scope=self 숨김)을 적용하려면 공통 기능이 필요한데, **현재는 어떻게 구성되어 있는지**만 정리한 문서입니다.

---

## 1. 공통 레이어 유무

| 구분 | 현재 상태 |
|------|-----------|
| **공통 컴포넌트** | 없음. `frontend/src/common/`, `frontend/src/shared/` 디렉터리 없음. |
| **사용자 3축(부서·이름·사용자ID) 공통 UI** | 없음. 화면마다 별도 폼/필터 구현. |
| **공통 훅** (예: useUserFilterOptions) | 없음. |
| **공통 API 래퍼** | 부분적. 사용자/부서 목록을 **두 경로**로 가져옴(아래 §2). |

---

## 2. 데이터 소스 (사용자 목록·부서 목록)

### 2.1 사용자 목록

| 사용처 | API | 서비스 | 비고 |
|--------|-----|--------|------|
| **통계 (ActivityStatistics)** | GET /api/statistics/users | `api.js` → `statisticsApi.getUserList()` | scope 적용된 목록 (statistics 전용) |
| **사용자 관리 (UserManagement)** | GET /api/users (또는 hierarchy 내 포함) | `userService.getUsers()` | 관리자용 전체 사용자 |
| **권한 그룹 관리 (PermissionGroupPanel)** | GET /api/users | `userService.getUsers()` | 동일 userService |

→ **두 종류**: (1) `statisticsApi.getUserList()` = `/api/statistics/users` (통계 scope 반영), (2) `userService.getUsers()` = `/api/users` (관리용). **공통 “사용자 목록 for 필터”** 추상화 없음.

### 2.2 부서 목록

| 사용처 | API | 서비스 | 비고 |
|--------|-----|--------|------|
| **통계 (ActivityStatistics)** | GET /api/statistics/departments | `api.js` → `statisticsApi.getDepartmentList()` | 평면 문자열 목록 (필터용) |
| **활동 이력 (UserActivityLog)** | (현재 미사용) | — | Form에 `departmentList` prop 있으나 List에서 로드·전달 없음 |
| **부서 계층 (사용자 관리 등)** | GET /api/departments?format=tree \| flat | `departmentService.getDepartments(format)` | 트리/평면, 계층 구조 |

→ **두 종류**: (1) `/api/statistics/departments` = 통계용 평면 부서 목록, (2) `/api/departments` = 계층용. **필터용 부서 목록**을 공통으로 쓰는 레이어 없음.

---

## 3. 화면별 현재 구현 (검색/필터 UI)

| 화면 | 사용자/부서 데이터 로드 | 필터 UI 컴포넌트 | 공통 여부 |
|------|-------------------------|------------------|-----------|
| **활동 이력** | userList/departmentList **로드 안 함** (부서 필드는 Form에 prop만 있음) | `UserActivityLogSearchForm` (userId·username 텍스트, 액션타입, IP) | 전용 컴포넌트 |
| **통계** | ActivityStatistics에서 `statisticsApi.getUserList()`, `getDepartmentList()`, `getIpList()` 호출 후 state 보관 | `StatisticsFilters` (userId·부서·IP **select**) | 전용 컴포넌트 |
| **사용자 관리** | `getUserPermissionHierarchy`, `getUsers`, `listPermissionGroups` — 검색 폼 없음 | 없음 (트리만) | — |
| **권한 그룹 관리** | `getUsers()` → userList state, addableUsers 필터만 | 다이얼로그 내 **select** (userId만, 부서는 옵션 라벨에 표시) | 전용 인라인 UI |
| **검색 이력** | 사용자/부서 목록 없음 | 검색 폼 없음 | — |
| **승인 대기** | 사용자/부서 목록 없음 | 검색 폼 없음 | — |

- **활동 이력**: 사용자ID·사용자명은 **input**, 통계는 **select**. 부서는 통계에만 select로 있음. **같은 “사용자 3축”을 쓰는 공통 컴포넌트 없음.**
- **scope=self**: 활동 이력·통계 각각 `hideUserFilters`로 필터 블록 숨김. 로직은 비슷하나 **공통 훅/컴포넌트 없음.**

---

## 4. UI 패턴 차이

| 항목 | 활동 이력 | 통계 | 권한 그룹 (사용자 추가) |
|------|-----------|------|--------------------------|
| 사용자 ID | 텍스트 input | select (userList) | select (userList) |
| 사용자명 | 텍스트 input | **없음** | **없음** (드롭다운만) |
| 부서 | **없음** (prop만 준비) | select (departmentList) | **없음** |
| 데이터 로드 | 없음 | 부모(ActivityStatistics)에서 API 호출 | PermissionGroupPanel에서 getUsers() |

→ 규칙(부서·이름·사용자ID)을 맞추려면 **공통 필드 세트 + 공통 데이터 로드**가 필요하고, 현재는 **화면마다 다른 패턴**으로 되어 있음.

---

## 5. 백엔드 측

- **사용자 목록**:  
  - `/api/users` (UserController 등): 관리/권한용.  
  - `/api/statistics/users`: 통계 scope 적용된 사용자 목록.  
  → “필터용 사용자 목록” 하나로 통일된 스펙/엔드포인트는 없음.
- **부서 목록**:  
  - `/api/statistics/departments`: 통계용 평면.  
  - `/api/departments`: 계층(tree/flat).  
  → “필터용 부서 목록” 공통 사용은 없음.
- **검색 이력/승인 대기 목록**:  
  - 현재 목록 API에 요청자(userId)·부서·이름 **필터 파라미터 없음**.  
  → 공통 규칙 적용하려면 API 확장 필요.

---

## 6. 정리 (규칙 적용 시 필요한 공통 기능)

| 필요한 것 | 현재 상태 |
|-----------|-----------|
| **사용자 3축(부서·이름·사용자ID) 공통 UI** | 없음. 화면별 전용 폼/필터. |
| **scope=self일 때 필터 블록 숨김** | 활동 이력·통계 각각 구현. 공통 훅/컴포넌트 없음. |
| **필터용 사용자·부서 목록 조회** | statistics 전용 API + userService/departmentService 혼재. “필터용” 공통 API/래퍼 없음. |
| **검색 이력/승인 대기 필터** | 목록 API에 필터 없음; 프론트에도 폼 없음. |
| **공통 디렉터리** | `common/`, `shared/` 없음. |

규칙을 적용하려면 예를 들어:

1. **공통 컴포넌트**: 부서·이름·사용자ID 필드 세트(select/input 조합) + `hideUserFilters` 시 비표시.
2. **공통 훅 또는 서비스**: 필터용 사용자 목록·부서 목록 한 곳에서 가져오기 (API 정책 정한 뒤).
3. **API 정책**: “필터용 사용자/부서 목록” 단일 경로 여부, 검색 이력/승인 대기 목록에 requesterUserId·department·username 쿼리 추가 여부.

위와 같은 **공통 기능**을 새로 두는 단계가 필요하고, 현재는 그런 구성이 없음.
