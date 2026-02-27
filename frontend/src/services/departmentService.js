/**
 * 부서 계층 및 부서별 결재자 API (관리자 전용)
 * docs/api-definition.md §12
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
 * 부서 트리 또는 평면 목록
 * @param {string} format - 'tree' | 'flat'
 */
export const getDepartments = async (format = 'tree') => {
  const response = await fetchWithCreds(`${API_BASE_URL}/departments?format=${format}`);
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
 * 해당 부서 멤버 목록 (department_code = code)
 * @param {string} code - 부서코드
 * @returns {Promise<Array<{userId, username, role, departmentCode, position, isApprover}>>}
 */
export const getDepartmentMembers = async (code) => {
  const response = await fetchWithCreds(`${API_BASE_URL}/departments/${encodeURIComponent(code)}/members`);
  const result = await response.json();
  if (!response.ok) {
    const msg = result.error || (response.status === 404 ? '부서를 찾을 수 없습니다.' : `HTTP ${response.status}`);
    const err = new Error(msg);
    err.status = response.status;
    err.code = result.code;
    throw err;
  }
  const data = result.data;
  return Array.isArray(data) ? data : (data?.data || []);
};

/**
 * 해당 부서 결재자 목록
 * @param {string} code - 부서코드
 */
export const getDepartmentApprovers = async (code) => {
  const response = await fetchWithCreds(`${API_BASE_URL}/departments/${encodeURIComponent(code)}/approvers`);
  const result = await response.json();
  if (!response.ok) {
    const msg = result.error || (response.status === 404 ? '부서를 찾을 수 없습니다.' : `HTTP ${response.status}`);
    const err = new Error(msg);
    err.status = response.status;
    err.code = result.code;
    throw err;
  }
  const data = result.data;
  return Array.isArray(data) ? data : (data?.data || []);
};

/**
 * 부서별 결재자 추가
 * @param {string} code - 부서코드
 * @param {string} userId - 사용자 ID(username)
 */
export const addDepartmentApprover = async (code, userId) => {
  const response = await fetchWithCreds(`${API_BASE_URL}/departments/${encodeURIComponent(code)}/approvers`, {
    method: 'POST',
    body: JSON.stringify({ userId }),
  });
  const result = await response.json();
  if (!response.ok) {
    const msg = result.error || (response.status === 403 ? '권한이 없습니다.' : response.status === 404 ? '부서 또는 사용자를 찾을 수 없습니다.' : response.status === 400 ? '잘못된 요청입니다.' : `HTTP ${response.status}`);
    const err = new Error(msg);
    err.status = response.status;
    err.code = result.code;
    throw err;
  }
  return result;
};

/**
 * 팀장 지정: 해당 부서 멤버 중 position에 "팀장" 포함된 사용자를 결재자로 일괄 추가
 * @param {string} code - 부서코드
 */
export const addDefaultApprovers = async (code) => {
  const response = await fetchWithCreds(`${API_BASE_URL}/departments/${encodeURIComponent(code)}/approvers/default`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
  });
  const result = await response.json();
  if (!response.ok) {
    const msg = result.error || (response.status === 404 ? '부서를 찾을 수 없습니다.' : `HTTP ${response.status}`);
    const err = new Error(msg);
    err.status = response.status;
    err.code = result.code;
    throw err;
  }
  return result;
};

/**
 * 부서별 결재자 제거
 * @param {string} code - 부서코드
 * @param {string} userId - 사용자 ID(username)
 */
export const removeDepartmentApprover = async (code, userId) => {
  const response = await fetchWithCreds(`${API_BASE_URL}/departments/${encodeURIComponent(code)}/approvers/${encodeURIComponent(userId)}`, {
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
