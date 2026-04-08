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

const parseJsonSafe = async (response) => {
  let result = {};
  try {
    result = await response.json();
  } catch {
    result = {};
  }
  if (!response.ok) {
    const msg =
      (typeof result.error === 'string' && result.error.trim() !== '' && result.error) ||
      (typeof result.message === 'string' && result.message.trim() !== '' && result.message) ||
      `HTTP ${response.status}`;
    const rawCode = result.code ?? result.errorCode;
    const code = rawCode == null || rawCode === '' ? undefined : String(rawCode);
    const err = new Error(msg);
    err.status = response.status;
    err.code = code;
    err.payload = result;
    throw err;
  }
  return result;
};

export const createRootDepartmentV2 = async (body) => {
  const response = await fetchWithCreds(`${getApiBaseUrl()}/user-management-v2/departments/root`, {
    method: 'POST',
    body: JSON.stringify(body),
  });
  return parseJsonSafe(response);
};

export const createChildDepartmentV2 = async (parentDepartmentId, body) => {
  const response = await fetchWithCreds(`${getApiBaseUrl()}/user-management-v2/departments/children`, {
    method: 'POST',
    body: JSON.stringify({ ...(body ?? {}), parentDepartmentId }),
  });
  return parseJsonSafe(response);
};

export const createDirectUserV2 = async (body) => {
  const response = await fetchWithCreds(`${getApiBaseUrl()}/user-management-v2/users/direct`, {
    method: 'POST',
    body: JSON.stringify(body),
  });
  return parseJsonSafe(response);
};

export const updateDepartmentV2 = async (departmentId, body) => {
  const response = await fetchWithCreds(
    `${getApiBaseUrl()}/user-management-v2/departments/${encodeURIComponent(String(departmentId))}`,
    {
      method: 'PUT',
      body: JSON.stringify(body),
    }
  );
  return parseJsonSafe(response);
};

export const deleteDepartmentV2 = async (departmentId, body) => {
  const changeReason = String(body?.changeReason ?? '').trim();
  const response = await fetchWithCreds(
    `${getApiBaseUrl()}/user-management-v2/departments/${encodeURIComponent(String(departmentId))}`,
    {
      method: 'DELETE',
      body: JSON.stringify({ changeReason }),
    }
  );
  return parseJsonSafe(response);
};

export const getQuickEntryOptionsV2 = async (params = {}) => {
  const q = new URLSearchParams();
  if (Array.isArray(params.fields) && params.fields.length > 0) {
    q.set('fields', params.fields.join(','));
  }
  if (params.limit != null) q.set('limit', String(params.limit));
  const suffix = q.toString() ? `?${q.toString()}` : '';
  const response = await fetchWithCreds(`${getApiBaseUrl()}/user-management-v2/quick-entry/options${suffix}`);
  return parseJsonSafe(response);
};

