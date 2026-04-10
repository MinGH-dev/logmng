/**
 * Minimal { code, label } list when GET /api/activity-log/action-types is unavailable (404/network).
 * Authoritative labels come from the API when the call succeeds.
 * @see docs/api-definition.md §8.0.1, specs/activity-action-types.spec.yaml
 */
export const FALLBACK_ACTIVITY_ACTION_TYPE_OPTIONS = [
  { code: 'LOGIN', label: '로그인' },
  { code: 'LOGOUT', label: '로그아웃' },
  { code: 'SEARCH', label: '검색' },
  { code: 'VIEW', label: '조회' },
  { code: 'DECRYPT', label: '복호화' },
  { code: 'ADVANCED_SEARCH', label: '고급 검색' },
  { code: 'EXPORT', label: '내보내기' },
  { code: 'STATS_VIEW', label: '통계 조회' },
  { code: 'SCHEMA_VIEW', label: '스키마 조회' },
  { code: 'USER_CREATE', label: '사용자 등록' },
  { code: 'USER_DELETE', label: '사용자 삭제' },
  { code: 'DEPARTMENT_CREATE_ROOT', label: '부서 생성(최상위)' },
  { code: 'DEPARTMENT_CREATE_CHILD', label: '부서 생성(하위)' },
  { code: 'DEPARTMENT_UPDATE', label: '부서 수정' },
  { code: 'DEPARTMENT_DELETE', label: '부서 삭제' },
  { code: 'UNKNOWN', label: '기타' },
];
