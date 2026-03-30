/**
 * Centralized copy for read/write/approve function descriptions (Korean).
 * Used in permission group config UI (ScreenSelectionTree) and action button tooltips.
 * Requirement: docs/requirements/20250303-screen-function-availability.md §2
 */

/** Full descriptions for permission group config UI (tooltip/inline help) */
export const FUNCTION_DESCRIPTIONS = {
  read: '이 화면을 선택하면 사용자는 해당 화면의 데이터를 조회할 수 있습니다. 검색, 목록 보기, 상세 보기가 가능합니다.',
  write: '수정 권한이 있으면 사용자는 생성·수정·삭제 등 변경 작업을 수행할 수 있습니다. 사용자 추가/수정, 권한 그룹 할당, 결재자 지정 등이 포함됩니다.',
  approve: '승인 권한은 \'부서별 결재자\'에서 별도 지정이 필요합니다. 이 화면만 선택하면 승인/반려 버튼이 비활성화됩니다. 결재자로 지정된 사용자만 복호화 승인·반려를 처리할 수 있습니다.',
  decrypt: '복호화 권한이 있으면 해당 로그 검색 화면(PB FEP v1.0.0, PB FEP v2.0.0, Java FW Image Log)에서 암호화된 로그의 복호화 요청이 가능합니다. 권한이 없으면 복호화 API 호출 시 403이 반환됩니다.',
};

/** Short labels for "부여되는 권한: 조회, 수정" summary */
export const FUNCTION_LABELS = {
  read: '조회',
  write: '수정',
  approve: '승인',
  decrypt: '복호화',
};

/** Example copy (short) — req doc §2 */
export const FUNCTION_LABELS_SHORT = {
  read: '조회 권한 – 화면 접근 및 데이터 열람 가능',
  write: '수정 권한 – 생성·수정·삭제 등 변경 작업 가능',
  approve: '승인 권한 – 복호화 승인/반려 처리 가능 (결재자 지정 필요)',
};

/** Tooltip for approve checkbox in permission group config (req 20250303-screen-function-checkbox-selection) */
export const APPROVE_CHECKBOX_TOOLTIP = '결재자 지정 필요';

/** Tooltip when action button is disabled due to missing function */
export const ACTION_DISABLED_TOOLTIPS = {
  approve: '승인 권한이 없습니다',
  reject: '반려 권한이 없습니다',
  write: '수정 권한이 없습니다',
  create: '생성 권한이 없습니다',
  edit: '수정 권한이 없습니다',
  delete: '삭제 권한이 없습니다',
  decrypt: '복호화 권한이 없습니다',
};

/** Screens that support write (user-management, department-approvers, user-permission-hierarchy, permission-group-management) */
export const SCREENS_WITH_WRITE = ['user-management', 'department-approvers', 'user-permission-hierarchy', 'permission-group-management'];

/** Screens that support approve (search-history, pending-approvals) */
export const SCREENS_WITH_APPROVE = ['search-history', 'pending-approvals'];

/** Screens that support decrypt (request decryption on search screen). req 20260318: pb-feplog, java-fw-imagelog. */
export const SCREENS_WITH_DECRYPT = ['pb-feplog', 'pb-fep-log-search', 'java-fw-imagelog'];

/**
 * Get functions available for a screen (for config UI display).
 * @param {string} screenId
 * @returns {{ read: boolean, write?: boolean, approve?: boolean, decrypt?: boolean }}
 */
export const getScreenFunctionCapabilities = (screenId) => {
  const hasWrite = SCREENS_WITH_WRITE.includes(screenId);
  const hasApprove = SCREENS_WITH_APPROVE.includes(screenId);
  const hasDecrypt = SCREENS_WITH_DECRYPT.includes(screenId);
  return {
    read: true,
    ...(hasWrite && { write: true }),
    ...(hasApprove && { approve: true }),
    ...(hasDecrypt && { decrypt: true }),
  };
};

/**
 * Build "부여되는 권한: 조회" or "부여되는 권한: 조회, 수정" summary.
 * @param {string} screenId
 * @returns {string}
 */
export const getGrantedFunctionsSummary = (screenId) => {
  const cap = getScreenFunctionCapabilities(screenId);
  const parts = [];
  if (cap.read) parts.push(FUNCTION_LABELS.read);
  if (cap.write) parts.push(FUNCTION_LABELS.write);
  if (cap.approve) parts.push(FUNCTION_LABELS.approve);
  if (cap.decrypt) parts.push(FUNCTION_LABELS.decrypt);
  return parts.length > 0 ? `부여되는 권한: ${parts.join(', ')}` : '';
};
