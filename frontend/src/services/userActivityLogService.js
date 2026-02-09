/**
 * 사용자 활동 이력 API 서비스
 */

const API_BASE_URL = process.env.REACT_APP_API_BASE_URL || 'http://localhost:9200/api';

/**
 * 사용자 활동 이력 검색
 */
export const searchActivityLogs = async (searchParams) => {
  try {
    const response = await fetch(`${API_BASE_URL}/activity-log/search`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      credentials: 'include', // 세션 쿠키 전달
      body: JSON.stringify(searchParams),
    });

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    const result = await response.json();
    return result;
  } catch (error) {
    console.error('활동 이력 검색 실패:', error);
    throw error;
  }
};

/**
 * 사용자 활동 이력 상세 조회
 */
export const getActivityLogDetail = async (id) => {
  try {
    const response = await fetch(`${API_BASE_URL}/activity-log/${id}`, {
      method: 'GET',
      headers: {
        'Content-Type': 'application/json',
      },
      credentials: 'include', // 세션 쿠키 전달
    });

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    const result = await response.json();
    return result;
  } catch (error) {
    console.error('활동 이력 상세 조회 실패:', error);
    throw error;
  }
};

/**
 * CSV 내보내기 (선택사항 - 나중에 구현)
 */
export const exportActivityLogs = async (searchParams) => {
  // TODO: 구현 예정
  throw new Error('CSV 내보내기 기능은 아직 구현되지 않았습니다.');
};

