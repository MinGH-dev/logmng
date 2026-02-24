/**
 * 사용자 관리 API (관리자 전용: 사용자 목록, 결재자 지정/해제)
 * docs/api-definition.md §7
 */

const API_BASE_URL = process.env.REACT_APP_API_BASE_URL || 'http://localhost:9200/api';

const fetchWithCreds = async (url, options = {}) => {
  const response = await fetch(url, {
    ...options,
    credentials: 'include',
    headers: { 'Content-Type': 'application/json', ...options.headers },
  });
  return response;
};

/**
 * 사용자 목록 조회 (관리자 전용)
 * @returns {Promise<{ success: boolean, data: Array<{ userId, role, departmentCode, isApprover }> }>}
 */
export const getUsers = async () => {
  const response = await fetchWithCreds(`${API_BASE_URL}/users`);
  const result = await response.json();
  if (!response.ok) {
    const msg = result.error || (response.status === 403 ? '권한이 없습니다.' : `HTTP ${response.status}`);
    const err = new Error(msg);
    err.status = response.status;
    err.code = result.code;
    throw err;
  }
  return result;
};

/**
 * 결재자 지정 (관리자 전용)
 * @param {string} userId - 결재자로 지정할 사용자 ID(username)
 */
export const addApprover = async (userId) => {
  const response = await fetchWithCreds(`${API_BASE_URL}/users/approvers`, {
    method: 'POST',
    body: JSON.stringify({ userId }),
  });
  const result = await response.json();
  if (!response.ok) {
    const msg = result.error || (response.status === 403 ? '권한이 없습니다.' : `HTTP ${response.status}`);
    const err = new Error(msg);
    err.status = response.status;
    err.code = result.code;
    throw err;
  }
  return result;
};

/**
 * 결재자 해제 (관리자 전용)
 * @param {string} userId - 결재자에서 해제할 사용자 ID(username)
 */
export const removeApprover = async (userId) => {
  const response = await fetchWithCreds(`${API_BASE_URL}/users/approvers/${encodeURIComponent(userId)}`, {
    method: 'DELETE',
  });
  const result = await response.json();
  if (!response.ok) {
    const msg = result.error || (response.status === 403 ? '권한이 없습니다.' : `HTTP ${response.status}`);
    const err = new Error(msg);
    err.status = response.status;
    err.code = result.code;
    throw err;
  }
  return result;
};
