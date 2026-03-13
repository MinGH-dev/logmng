import logger from '../utils/logger';

const API_BASE_URL = process.env.REACT_APP_API_BASE_URL || 'http://localhost:9200/api';

export const FILTER_OPTION_SCREEN_IDS = Object.freeze({
  ACTIVITY_LOG: 'activity-log',
  STATISTICS: 'statistics',
  SEARCH_HISTORY: 'search-history',
});

const SUPPORTED_DEPARTMENT_FILTER_SCREENS = new Set(Object.values(FILTER_OPTION_SCREEN_IDS));

export const getDepartmentFilterOptions = async (screenId) => {
  if (!SUPPORTED_DEPARTMENT_FILTER_SCREENS.has(screenId)) {
    throw new Error(`지원하지 않는 department filter screenId입니다: ${screenId}`);
  }

  const params = new URLSearchParams({ screen: screenId });
  const response = await fetch(`${API_BASE_URL}/filter-options/departments?${params.toString()}`, {
    method: 'GET',
    headers: { 'Content-Type': 'application/json' },
    credentials: 'include',
  });

  const result = await response.json();

  if (!response.ok) {
    const error = new Error(result.error || `HTTP ${response.status}`);
    error.status = response.status;
    error.code = result.code;
    logger.error('부서 필터 옵션 조회 실패:', { screenId, status: response.status, code: result.code });
    throw error;
  }

  return result;
};
