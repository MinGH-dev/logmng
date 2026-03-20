/**
 * 권한 그룹 CRUD, 그룹별 사용자 배정/해제, 사용자 권한 계층 API (관리자 전용)
 * docs/api-definition.md §14, specs/permission-group-hierarchy.spec.yaml
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

const parseResponse = async (response) => {
  const result = await response.json();
  if (!response.ok) {
    const err = new Error(result.error || `HTTP ${response.status}`);
    err.status = response.status;
    err.code = result.code;
    err.message = result.error || err.message;
    throw err;
  }
  return result;
};

/**
 * 사용자 권한 계층 (부서 트리 + 부서별 사용자 및 권한 그룹)
 * @param {string} format - 'tree' | 'flat'
 */
export const getUserPermissionHierarchy = async (format = 'tree') => {
  const response = await fetchWithCreds(
    `${getApiBaseUrl()}/departments/user-permission-hierarchy?format=${format}`
  );
  return parseResponse(response);
};

/**
 * 권한 그룹 목록
 */
export const listPermissionGroups = async () => {
  const response = await fetchWithCreds(`${getApiBaseUrl()}/permission-groups`);
  const result = await parseResponse(response);
  const data = result.data;
  return Array.isArray(data) ? data : (data?.data || []);
};

/**
 * 권한 그룹 생성
 * @param {{ code: string, name: string, description?: string, sortOrder?: number }} body
 */
export const createPermissionGroup = async (body) => {
  const response = await fetchWithCreds(`${getApiBaseUrl()}/permission-groups`, {
    method: 'POST',
    body: JSON.stringify(body),
  });
  return parseResponse(response);
};

/**
 * 권한 그룹 상세
 * @param {number} id
 */
export const getPermissionGroup = async (id) => {
  const response = await fetchWithCreds(`${getApiBaseUrl()}/permission-groups/${id}`);
  return parseResponse(response);
};

/**
 * 권한 그룹 수정
 * @param {number} id
 * @param {{ code?: string, name?: string, description?: string, sortOrder?: number }} body
 */
export const updatePermissionGroup = async (id, body) => {
  const response = await fetchWithCreds(`${getApiBaseUrl()}/permission-groups/${id}`, {
    method: 'PUT',
    body: JSON.stringify(body),
  });
  return parseResponse(response);
};

/**
 * 권한 그룹 삭제
 * @param {number} id
 */
export const deletePermissionGroup = async (id) => {
  const response = await fetchWithCreds(`${getApiBaseUrl()}/permission-groups/${id}`, {
    method: 'DELETE',
  });
  return parseResponse(response);
};

/**
 * 그룹에 속한 사용자 목록
 * @param {number} groupId
 */
export const listUsersInGroup = async (groupId) => {
  const response = await fetchWithCreds(`${getApiBaseUrl()}/permission-groups/${groupId}/users`);
  const result = await parseResponse(response);
  const data = result.data;
  return Array.isArray(data) ? data : (data?.data || []);
};

/**
 * 그룹에 사용자 배정
 * @param {number} groupId
 * @param {number} userId - app_user.id (numeric)
 */
export const addUserToGroup = async (groupId, userId) => {
  const response = await fetchWithCreds(`${getApiBaseUrl()}/permission-groups/${groupId}/users`, {
    method: 'POST',
    body: JSON.stringify({ userId: Number(userId) }),
  });
  return parseResponse(response);
};

/**
 * 그룹에서 사용자 제거
 * @param {number} groupId
 * @param {number} userId - app_user.id (numeric)
 */
export const removeUserFromGroup = async (groupId, userId) => {
  const response = await fetchWithCreds(
    `${getApiBaseUrl()}/permission-groups/${groupId}/users/${encodeURIComponent(String(userId))}`,
    { method: 'DELETE' }
  );
  return parseResponse(response);
};
