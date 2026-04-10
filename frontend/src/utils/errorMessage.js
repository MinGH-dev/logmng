/**
 * API error code / status → user-facing message (docs/api-definition.md §11, §14)
 * Used by admin views: UserPermissionHierarchy, PermissionGroupManagement.
 */
export const getErrorMessage = (e, fallback) => {
  const code = e?.code;
  const status = e?.status;
  if (code === 'FORBIDDEN' || status === 403) return '권한이 없습니다.';
  if (code === 'PERMISSION_GROUP_NOT_FOUND') return '권한 그룹을 찾을 수 없습니다.';
  if (code === 'PERMISSION_GROUP_HAS_USERS') return '해당 그룹에 사용자가 배정되어 있어 삭제할 수 없습니다.';
  if (code === 'USER_ALREADY_IN_GROUP') return '해당 사용자가 이미 이 그룹에 배정되어 있습니다.';
  if (code === 'USER_NOT_FOUND') return '해당 사용자를 찾을 수 없습니다.';
  if (code === 'SYSTEM_ADMIN_IMMUTABLE') return '시스템 관리자는 수정할 수 없습니다.';
  if (code === 'LAST_SYSTEM_ADMIN_BLOCKED') return '최소 1명의 시스템 관리자가 필요합니다.';
  if (code === 'LAST_ADMIN_BLOCKED') return '최소 1명의 관리자가 필요합니다.';
  if (code === 'SELF_DEMOTION_BLOCKED') return '본인 역할을 변경할 수 없습니다.';
  if (code === 'PASSWORD_CHANGE_NOT_ALLOWED')
    return e?.message || '이 환경에서는 비밀번호를 변경할 수 없습니다. 디렉터리에서 관리되는 계정일 수 있습니다.';
  if (code === 'INVALID_CREDENTIALS')
    return e?.message || '현재 비밀번호가 올바르지 않습니다.';
  if (code === 'INVALID_INPUT') return e?.message || '입력값을 확인해 주세요.';
  if (code === 'SELF_DEMOTION') return e?.message || '역할 변경이 허용되지 않습니다.';
  if (code === 'USER_DELETE_REFERENCED') return '연결된 데이터가 있어 사용자를 삭제할 수 없습니다.';
  if (code === 'DEPARTMENT_NOT_FOUND') return '부서를 찾을 수 없습니다.';
  if (code === 'ALREADY_APPROVER') return '이미 해당 부서 결재자로 등록되어 있습니다.';
  if (status === 404) {
    const m = e?.message != null ? String(e.message).trim() : '';
    if (m && !/^HTTP\s+\d{3}$/i.test(m)) return m;
    return '찾을 수 없습니다.';
  }
  if (status === 400) return code ? (e?.message || fallback) : (e?.message || '잘못된 요청입니다.');
  return e?.message || fallback;
};

const LOGIN_MSG_CREDENTIALS =
  '❌ 인증 정보가 올바르지 않습니다.\n사용자명과 비밀번호를 다시 확인해주세요.';
const LOGIN_MSG_IP_FORBIDDEN =
  '🔒 접근이 제한된 IP 주소입니다.\n시스템 관리자에게 접근 권한을 요청하세요.';
const LOGIN_MSG_INPUT_SHORT = '⚠️ 입력 정보가 부족합니다.\n모든 필드를 올바르게 입력해주세요.';
const LOGIN_MSG_SERVER = '🚨 서버 오류가 발생했습니다.\n잠시 후 다시 시도하거나 관리자에게 문의하세요.';
const LOGIN_MSG_NETWORK = '🌐 네트워크 연결을 확인해주세요.\n서버에 연결할 수 없습니다.';

/**
 * Map POST /api/auth/login failure to a user-facing message.
 * Prefer `result.code` (docs/api-definition.md §2.1, §11), then HTTP status.
 *
 * @param {number} status - response.status
 * @param {{ success?: boolean, code?: string, error?: string }} [result]
 */
export const getLoginFailureMessage = (status, result) => {
  const resultError = result?.error != null ? String(result.error).trim() : '';
  const code = result?.code;

  if (code === 'USER_ACCOUNT_DISABLED') {
    return '⚠️ 이 계정은 비활성 처리되었거나 삭제된 계정입니다.\n관리자에게 문의해 주세요.';
  }
  if (code === 'INVALID_INPUT') {
    return (
      resultError ||
      '⚠️ 입력 정보를 확인해 주세요.\n필수 항목과 형식을 올바르게 입력했는지 다시 확인해 주세요.'
    );
  }
  if (code === 'UNAUTHORIZED') {
    return '🔐 로그인하지 않았거나 세션이 만료되었습니다.\n다시 로그인해 주세요.';
  }
  if (code === 'APP_USER_NOT_PROVISIONED') {
    return (
      'ℹ️ 디렉터리 로그인은 확인되었으나, 이 시스템에 등록된 사용자 계정이 없습니다.\n' +
      '관리자에게 계정 등록(프로비저닝)을 요청해 주세요.'
    );
  }
  if (code === 'INVALID_AUTH_MODE' || code === 'AUTH_CONFIGURATION_ERROR') {
    return '⚙️ 로그인 서버 설정 오류로 로그인할 수 없습니다.\n시스템 관리자에게 문의해 주세요.';
  }
  if (code === 'INVALID_CREDENTIALS' || code === 'DIRECTORY_AUTH_FAILED') {
    return LOGIN_MSG_CREDENTIALS;
  }

  if (status === 403) {
    return LOGIN_MSG_IP_FORBIDDEN;
  }
  if (status === 401) {
    return LOGIN_MSG_CREDENTIALS;
  }
  if (status === 400) {
    return resultError || LOGIN_MSG_INPUT_SHORT;
  }
  if (status === 500) {
    return LOGIN_MSG_SERVER;
  }
  if (status === 0 || (typeof status === 'number' && status >= 400)) {
    return LOGIN_MSG_NETWORK;
  }
  return resultError || '로그인에 실패했습니다.';
};
