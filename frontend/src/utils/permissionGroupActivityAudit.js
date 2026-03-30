/**
 * Permission-group activity audit (action_detail.permissionGroupAuditV1) — UI helpers.
 * @see specs/activity-permission-group-audit.spec.yaml
 */

/** @type {readonly string[]} */
export const PERMISSION_GROUP_AUDIT_ACTION_TYPES = Object.freeze([
  'PERMISSION_GROUP_CREATE',
  'PERMISSION_GROUP_UPDATE',
  'PERMISSION_GROUP_DELETE',
  'ASSIGN_USER_TO_PERMISSION_GROUP',
  'UNASSIGN_USER_FROM_PERMISSION_GROUP',
]);

/**
 * @param {string|null|undefined} actionType
 * @returns {boolean}
 */
export function isPermissionGroupFamilyActionType(actionType) {
  if (actionType == null || actionType === '') return false;
  if (PERMISSION_GROUP_AUDIT_ACTION_TYPES.includes(actionType)) return true;
  return /^PERMISSION_GROUP_/.test(String(actionType));
}

/** @type {Record<string, string>} */
export const PERMISSION_GROUP_OPERATION_LABELS = {
  CREATE: '생성',
  UPDATE: '수정',
  DELETE: '삭제',
  ASSIGN_USER: '사용자 배정',
  UNASSIGN_USER: '사용자 해제',
};

/**
 * @param {string|null|undefined} operation
 * @returns {string}
 */
export function getPermissionGroupOperationLabel(operation) {
  if (operation == null || operation === '') return '-';
  return PERMISSION_GROUP_OPERATION_LABELS[operation] ?? operation;
}

/** Preset multi-select labels for activity-log search (O4, UX-only). */
export const PERMISSION_GROUP_AUDIT_PRESET_OPTIONS = Object.freeze([
  { value: 'PERMISSION_GROUP_CREATE', label: '권한 그룹 생성' },
  { value: 'PERMISSION_GROUP_UPDATE', label: '권한 그룹 수정' },
  { value: 'PERMISSION_GROUP_DELETE', label: '권한 그룹 삭제' },
  { value: 'ASSIGN_USER_TO_PERMISSION_GROUP', label: '권한 그룹 사용자 배정' },
  { value: 'UNASSIGN_USER_FROM_PERMISSION_GROUP', label: '권한 그룹 사용자 해제' },
]);
