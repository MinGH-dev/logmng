# Requirements Topic Index

For **RequirementsPastSearch** token optimization. Read this file first to find relevant doc IDs by topic, then read only §1 of those docs (offset=1, limit=90).

**Maintenance**: When adding a new requirement, add one line under the matching topic(s). Format: `- doc-id | one-line §1 summary`

---

## permission | access-control | 화면 접근 | 권한 그룹 | is_system_admin

- 20250227-permission-group-screen-menu-access | 권한 그룹별 화면 접근 설정; ADMIN or group allows screen; GENERAL_USER
- 20250227-permission-group-screen-menu-access-bugfix-1 | Admin 섹션: non-admin with user-management in allowedScreenIds → 사이드바 표시
- 20250303-user-management-permission-group-access | 사용자관리: 권한 그룹으로 user-management 허용 시 "관리자만 접근" 오류 수정
- 20250303-user-management-permission-group-access-bugfix-1 | Frontend: canAccessUserManagement
- 20250303-user-management-permission-group-access-bugfix-2 | Backend: hierarchy/permission-groups API accept user-management OR user-permission-hierarchy
- 20250227-permission-group-separate-menu | 권한 그룹 메뉴 분리
- 20250227-permission-management-in-hierarchy | 권한 관리를 사용자 권한 계층 화면에 통합
- 20250227-permission-user-management-close-button | 권한 그룹 사용자 할당 모달 닫기 버튼 가시성
- 20250303-permission-group-delete-system-admin-protection | 권한 그룹 삭제 제약; 시스템 관리자 보호 (is_system_admin)
- 20250303-permission-group-invalid-screen-id-bugfix | 권한 그룹 invalid screen_id 버그 수정
- 20250303-remove-role-single-admin | role 제거; is_system_admin만 admin 접근; 단일 시스템 관리자
- 20250303-remove-role-single-admin-bugfix-1 | login/me에 isSystemAdmin; PUT 410; role 응답 제거

## activity-log | statistics | 활동 로그 | 통계 | scope

- 20260206-activity-log-statistics | 활동로그 통계 화면 (월별/일별/사용자별)
- 20260206-activity-log-statistics-improvement | 활동 로그 통계 화면 개선
- 20260206-activity-log-statistics-improvement-test-results | 통계 개선 테스트 결과
- 20260206-activity-log-statistics-test-results | 활동로그 통계 테스트 결과
- 20260206-user-activity-log | 사용자 활동 이력 보관 및 조회
- 20260220-activity-log-today-empty-fix | 활동이력 오늘 날짜 조회 시 결과 없음 수정
- 20260220-activity-statistics-api-error-fix | 활동 로그 통계 조회 오류 수정
- 20260220-activity-statistics-no-anonymous-access | 통계 anonymous 제거, 미인증 조회 차단
- 20260220-activity-statistics-whole-equals-sum-of-logtypes | 통계 '전체' = 로그타입 합계 정합성
- 20250303-activity-statistics-self-only-scope | 비관리자 scope=self|all (activity-log, statistics, search-history)
- 20250303-activity-statistics-self-only-scope-bugfix-1 | scope 적용 미적용 (session/scope resolution)
- 20250303-activity-statistics-self-only-scope-bugfix-2 | TC-02, TC-06, TC-08 재검증

## sidebar | layout | 사이드바 | 레이아웃

- 20260225-sidebar-content-scroll-independent | 사이드바·컨텐츠 스크롤 독립화
- 20260225-sidebar-library-migration | 사이드바 라이브러리 도입 (펼침·스크롤 안정화)
- 20260225-sidebar-layout-no-overflow | 사이드바 펼침 시 콘텐츠 영역 가로 탈출 수정
- 20260225-sidebar-search-display-and-scroll | 사이드바 검색 메뉴 표시 및 스크롤 복구
- 20260225-sidebar-search-menu-hierarchy | 사이드바 검색 메뉴 계층 표시 개선
- 20260225-sidebar-submenu-expand-overflow | 사이드바 하위메뉴 펼침 시 다른 상위 메뉴 가림 수정
- 20260225-sidebar-topbar-layout-no-overlap | 사이드바·상단바 레이아웃 중첩 제거 및 스크롤 분리

## department | 부서 | 결재자 | hierarchy

- 20250227-department-approver-position | 부서 결재자: position 필드, 부서 범위 선택
- 20250227-dept-hierarchy-daol-structure | 부서 계층: 다올투자증권 구조 (4단계)
- 20250227-dept-hierarchy-sample-depth5 | 부서 계층 샘플 데이터 depth 5
- 20260225-department-approver-hierarchy | 부서별 결재자 지정 및 부서 계층 표시
- 20250227-remove-department-approver-screen-user-mgmt-improvements | 부서별 결재자 화면 제거; 사용자 관리 개선
- 20250227-remove-department-approver-screen-user-mgmt-improvements-bugfix-1 | POST /api/users/approvers 500 대신 404

## user-management | 사용자 관리 | hierarchy

- 20250227-user-management-hierarchy-permissions | 사용자 관리: 계층 표시, role·권한 그룹 편집
- 20250227-user-permission-hierarchy-group | 사용자 권한 계층 및 권한 그룹 관리
- 20250227-user-permission-hierarchy-group-bugfix-1 | DB schema·init-data 미적용
- 20250227-user2-approver-display-bugfix | user2 결재자 표시 오류
- 20250303-user-management-permission-group-access | 사용자관리: 권한 그룹으로 user-management 허용 시 "관리자만 접근" 오류 수정
- 20250303-user-management-permission-group-access-bugfix-1 | Frontend: canAccessUserManagement
- 20250303-user-management-permission-group-access-bugfix-2 | Backend: hierarchy/permission-groups API accept user-management OR user-permission-hierarchy

## decryption | 복호화 | search-history | 검색 이력 | approval

- 20260224-decryption-approver-designation | 복호화 결재자 지정 및 결재자 전용 승인
- 20260224-decryption-require-approval | 복호화 승인 없이 복호화 차단
- 20260224-search-history-decryption-approval | 검색 이력 및 복호화 승인 재요청
- 20260224-search-history-reload-and-detail-view | 검색 이력 재조회 시 조건 표시 및 자세히 보기
- 20260224-decryption-snapshot-final-design | 복호화 스냅샷 최종 설계
- 20260224-decryption-snapshot-final-design-en | (English)
- 20260224-decryption-snapshot-qa-test-scenarios | 복호화 스냅샷 QA 시나리오
- 20260224-decryption-approval-snapshot-guide | 복호화 승인 스냅샷 가이드

## image-log | imagelog | datastring

- 20260206-image-log-datastring-search | Image Log datastring 검색 기능 개선
- 20260206-image-log-decrypt-datastring-display | Image Log 복호화 시 datastring 필드 표시
- 20260224-image-log-encrypted-highlight-only | Image log 암호화 구간만 encrypted 하이라이트
- 20260225-image-log-search-no-results | 이미지 로그 검색 결과 없음

## grid | UX | ux-standards | 그리드

- 20260226-grid-design-unification | 그리드 디자인 통일
- 20260226-ux-grid-review-and-push | UX grid review and push
- 20260226-ux-grid-review-report | UX grid review 리포트
- 20260225-ux-standards-compliance-audit | UX 표준 준수 감사 및 개선
- 20260225-ux-standards-compliance-audit-bugfix-1 | 날짜 역전 제출 시 aria-invalid/aria-describedby 미노출

## privacy | security | 개인정보 | IP | 로깅

- 20260206-privacy-security-improvement | 프론트엔드 개인정보 보호 및 보안 개선
- 20260206-privacy-security-improvement-summary | 개인정보 보호 개선 요약
- 20260206-privacy-security-improvement-test-results | 개인정보 보호 테스트 결과
- 20260206-ip-collection-and-decryption-logging | IP 수집 정확도 개선 및 복호화 로깅 강화

## log-type | 로그 타입 | dynamic

- 20260208-dynamic-log-type-management | 동적 로그 타입 관리 기능

## auth | logout | 로그인 | 로그아웃

- 20260225-logout-persist-after-refresh | 로그아웃 후 새로고침 시 로그인 상태 유지 버그

## misc | bugfix | pretty | highlighting

- 20260206-pretty-mode-highlighting-fix | Pretty 모드 하이라이팅 표시 문제 수정
