/**
 * API error code / status → user-facing message (docs/api-definition.md §11, §14)
 * Used by admin views: UserPermissionHierarchy, PermissionGroupManagement, DepartmentApproverManagement.
 */
export const getErrorMessage = (e, fallback) => {
  const code = e?.code;
  const status = e?.status;
  if (code === 'FORBIDDEN' || status === 403) return '권한이 없습니다.';
  if (code === 'PERMISSION_GROUP_NOT_FOUND' || (status === 404 && code)) return '권한 그룹을 찾을 수 없습니다.';
  if (code === 'PERMISSION_GROUP_HAS_USERS') return '해당 그룹에 사용자가 배정되어 있어 삭제할 수 없습니다.';
  if (code === 'USER_ALREADY_IN_GROUP') return '해당 사용자가 이미 이 그룹에 배정되어 있습니다.';
  if (code === 'USER_NOT_FOUND') return '해당 사용자를 찾을 수 없습니다.';
  if (code === 'INVALID_INPUT' || code === 'SELF_DEMOTION') return e?.message || '역할 변경이 허용되지 않습니다.';
  if (code === 'DEPARTMENT_NOT_FOUND') return '부서를 찾을 수 없습니다.';
  if (code === 'ALREADY_APPROVER') return '이미 해당 부서 결재자로 등록되어 있습니다.';
  if (status === 404) return '찾을 수 없습니다.';
  if (status === 400) return code ? (e?.message || fallback) : (e?.message || '잘못된 요청입니다.');
  return e?.message || fallback;
};
