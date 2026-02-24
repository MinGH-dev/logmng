/**
 * 검색 이력 API (복호화 승인 부가 기능)
 */

const API_BASE_URL = process.env.REACT_APP_API_BASE_URL || 'http://localhost:9200/api';

/**
 * 검색 이력 저장
 */
export const createSearchHistory = async (logType, searchParams) => {
  const response = await fetch(`${API_BASE_URL}/search-history`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    credentials: 'include',
    body: JSON.stringify({ logType, searchParams }),
  });
  const result = await response.json();
  if (!response.ok) {
    throw new Error(result.error || `HTTP ${response.status}`);
  }
  return result;
};

/**
 * 검색 이력 목록 조회
 */
export const getSearchHistoryList = async (page = 1, pageSize = 20, sortField = 'requested_at', sortDirection = 'desc') => {
  const params = new URLSearchParams({ page, pageSize, sortField, sortDirection });
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
