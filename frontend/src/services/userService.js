/**
 * 사용자 관리 API (관리자 전용: 사용자 목록)
 * docs/api-definition.md §7
 * Note: updateUserRole removed — role is deprecated; admin access uses isSystemAdmin.
 */

import { getApiBaseUrl } from '../config/runtimeApi';

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
 * @returns {Promise<{ success: boolean, data: Array<{ userId, isSystemAdmin, departmentCode, isApprover }> }>}
 */
export const getUsers = async () => {
  const response = await fetchWithCreds(`${getApiBaseUrl()}/users`);
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

