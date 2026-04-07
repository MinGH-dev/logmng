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

/**
 * 사용자 삭제 (소프트/하드는 백엔드 정책). docs/api-definition.md §7
 * @param {number|string} userId — app_user.id
 * @param {{ changeReason: string }} body — trim 후 비어 있지 않음, 최대 500자
 */
export const deleteUser = async (userId, body) => {
  const changeReason = String(body?.changeReason ?? '').trim();
  const response = await fetchWithCreds(`${getApiBaseUrl()}/users/${encodeURIComponent(String(userId))}`, {
    method: 'DELETE',
    body: JSON.stringify({ changeReason }),
  });
  let result = {};
  try {
    result = await response.json();
  } catch {
    result = {};
  }
  if (!response.ok) {
    const msg = result.error || (response.status === 403 ? '권한이 없습니다.' : `HTTP ${response.status}`);
    const err = new Error(msg);
    err.status = response.status;
    err.code = result.code;
    throw err;
  }
  return result;
};

