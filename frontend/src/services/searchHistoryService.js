/**
 * 검색 이력 API (복호화 승인 부가 기능)
 */

const API_BASE_URL = process.env.REACT_APP_API_BASE_URL || 'http://localhost:9200/api';

/**
 * 검색 이력 저장
 * @param {string} logType - 로그 타입 ID
 * @param {object} searchParams - 검색 조건
 * @param {string} [requestReason] - 요청 사유 (optional or required per product; max 500)
 * @param {{ searchResultTotalCount?: number, decryptionTargetCount?: number }} [options] - 선택. 둘 다 제공되고 0 이상일 때만 body에 포함(서버가 권위 값으로 저장). 한쪽만 보내면 400.
 */
export const createSearchHistory = async (logType, searchParams, requestReason, options) => {
  const body = { logType, searchParams };
  if (requestReason != null && String(requestReason).trim() !== '') {
    body.requestReason = String(requestReason).trim();
  }
  const sr = options?.searchResultTotalCount;
  const dt = options?.decryptionTargetCount;
  if (
    typeof sr === 'number' && Number.isFinite(sr) && sr >= 0 &&
    typeof dt === 'number' && Number.isFinite(dt) && dt >= 0
  ) {
    body.searchResultTotalCount = sr;
    body.decryptionTargetCount = dt;
  }
  const response = await fetch(`${API_BASE_URL}/search-history`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    credentials: 'include',
    body: JSON.stringify(body),
  });
  let result;
  try {
    result = await response.json();
  } catch (_) {
    if (response.status === 404) {
      throw new Error('검색 이력 API를 찾을 수 없습니다. 백엔드를 재빌드·재시작한 뒤 다시 시도해 주세요.');
    }
    throw new Error(`서버 응답 오류 (${response.status})`);
  }
  if (!response.ok) {
    if (response.status === 404) {
      throw new Error('검색 이력 API를 찾을 수 없습니다. 백엔드를 재빌드·재시작한 뒤 다시 시도해 주세요.');
    }
    throw new Error(result.error || `HTTP ${response.status}`);
  }
  return result;
};

/**
 * 검색 이력 목록 조회
 * @param {object} opts
 * @param {number|string} [opts.userId] - Requester user ID (numeric app_user.id)
 * @param {string} [opts.requestedAtFrom] - 요청일시 범위 시작 (yyyy-MM-dd HH:mm:ss)
 * @param {string} [opts.requestedAtTo] - 요청일시 범위 종료 (yyyy-MM-dd HH:mm:ss)
 * @param {string[]} [opts.approvalStatuses] - 복호화 승인 여부 (PENDING, APPROVED, REJECTED, EXPIRED); repeated param
 * @param {string} [opts.requestReason] - 요청사유 부분 검색
 */
export const getSearchHistoryList = async ({
  page = 1,
  pageSize = 20,
  sortField = 'requested_at',
  sortDirection = 'desc',
  department = '',
  username = '',
  userId = '',
  requestedAtFrom = '',
  requestedAtTo = '',
  approvalStatuses = [],
  requestReason = '',
} = {}) => {
  const params = new URLSearchParams({
    page: String(page),
    pageSize: String(pageSize),
    sortField,
    sortDirection,
  });

  if (department) {
    params.set('department', department);
  }
  if (username && String(username).trim()) {
    params.set('username', String(username).trim());
  }
  if (userId !== '' && userId != null && userId !== undefined) {
    params.set('userId', String(userId));
  }
  if (requestedAtFrom && String(requestedAtFrom).trim()) {
    params.set('requestedAtFrom', String(requestedAtFrom).trim());
  }
  if (requestedAtTo && String(requestedAtTo).trim()) {
    params.set('requestedAtTo', String(requestedAtTo).trim());
  }
  if (Array.isArray(approvalStatuses) && approvalStatuses.length > 0) {
    approvalStatuses.forEach((s) => {
      if (s && String(s).trim()) params.append('approvalStatus', String(s).trim());
    });
  }
  if (requestReason != null && String(requestReason).trim() !== '') {
    params.set('requestReason', String(requestReason).trim());
  }

  const response = await fetch(`${API_BASE_URL}/search-history?${params}`, {
    method: 'GET',
    headers: { 'Content-Type': 'application/json' },
    credentials: 'include',
  });
  const result = await response.json();
  if (!response.ok) {
    throw new Error(result.error || `HTTP ${response.status}`);
  }
  return result;
};

/**
 * 검색 이력 재요청 (만료 건)
 */
export const reRequestSearchHistory = async (id) => {
  const response = await fetch(`${API_BASE_URL}/search-history/${id}/re-request`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    credentials: 'include',
  });
  const result = await response.json();
  if (!response.ok) {
    throw new Error(result.error || `HTTP ${response.status}`);
  }
  return result;
};

/**
 * 검색 이력 상세 (재조회용)
 */
export const getSearchHistoryDetail = async (id) => {
  const response = await fetch(`${API_BASE_URL}/search-history/${id}`, {
    method: 'GET',
    headers: { 'Content-Type': 'application/json' },
    credentials: 'include',
  });
  const result = await response.json();
  if (!response.ok) {
    throw new Error(result.error || `HTTP ${response.status}`);
  }
  return result;
};

/**
 * 승인 대기 목록 조회 (결재자·관리자 전용). 403 시 code FORBIDDEN_NOT_APPROVER / NOT_APPROVER.
 */
export const getPendingList = async (page = 1, pageSize = 20) => {
  const params = new URLSearchParams({ page: String(page), pageSize: String(pageSize) });
  const response = await fetch(`${API_BASE_URL}/search-history/pending?${params}`, {
    method: 'GET',
    headers: { 'Content-Type': 'application/json' },
    credentials: 'include',
  });
  const result = await response.json();
  if (!response.ok) {
    const err = new Error(result.error || `HTTP ${response.status}`);
    err.status = response.status;
    err.code = result.code;
    throw err;
  }
  return result;
};

/**
 * 검색 이력 승인 (결재자·관리자 전용)
 */
export const approveSearchHistory = async (id) => {
  const response = await fetch(`${API_BASE_URL}/search-history/${id}/approve`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    credentials: 'include',
  });
  const result = await response.json();
  if (!response.ok) {
    const err = new Error(result.error || `HTTP ${response.status}`);
    err.status = response.status;
    err.code = result.code;
    throw err;
  }
  return result;
};

/**
 * 검색 이력 반려 (결재자·관리자 전용). rejectionReason 선택.
 */
export const rejectSearchHistory = async (id, rejectionReason) => {
  const body = rejectionReason != null && rejectionReason !== '' ? { rejectionReason } : {};
  const response = await fetch(`${API_BASE_URL}/search-history/${id}/reject`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    credentials: 'include',
    body: JSON.stringify(body),
  });
  const result = await response.json();
  if (!response.ok) {
    const err = new Error(result.error || `HTTP ${response.status}`);
    err.status = response.status;
    err.code = result.code;
    throw err;
  }
  return result;
};
