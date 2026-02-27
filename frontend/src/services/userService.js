/**
 * 사용자 관리 API (관리자 전용: 사용자 목록, 역할 변경)
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
 * 사용자 역할 변경 (관리자 전용)
 * @param {string} userId - 역할을 변경할 사용자 ID(username)
 * @param {string} role - "ADMIN" | "USER"
 * @returns {Promise<{ success: boolean, data: { userId, role, departmentCode, isApprover } }>}
 */
export const updateUserRole = async (userId, role) => {
  const response = await fetchWithCreds(`${API_BASE_URL}/users/${encodeURIComponent(userId)}`, {
    method: 'PUT',
    body: JSON.stringify({ role }),
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
