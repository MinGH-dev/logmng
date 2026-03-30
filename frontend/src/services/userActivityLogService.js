/**
 * 사용자 활동 이력 API 서비스
 */

import { getApiBaseUrl } from '../config/runtimeApi';

/**
 * 사용자 활동 이력 검색
 * 401/403 시에도 본문을 파싱해 반환하여 화면에서 "로그인 필요" 메시지를 보여줄 수 있게 함.
 */
export const searchActivityLogs = async (searchParams) => {
  try {
    const response = await fetch(`${getApiBaseUrl()}/activity-log/search`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      credentials: 'include', // 세션 쿠키 전달
      body: JSON.stringify(searchParams),
    });

    const result = await response.json();

    if (!response.ok) {
      // 인증 실패 등으로 401/403이면 본문 그대로 반환(화면에서 메시지 표시용)
      if (response.status === 401 || response.status === 403) {
        return result;
      }
      throw new Error(result.error || `HTTP error! status: ${response.status}`);
    }

    return result;
  } catch (error) {
    console.error('활동 이력 검색 실패:', error);
    throw error;
  }
};

/**
 * 활동 유형 필터 옵션 (GET). 실패 시 클라이언트는 폴백 상수 사용.
 * @returns {Promise<{ success: boolean, data: { code: string, label: string }[]|null, status?: number }>}
 */
export const getActivityLogActionTypes = async () => {
  try {
    const response = await fetch(`${getApiBaseUrl()}/activity-log/action-types`, {
      method: 'GET',
      headers: {
        'Content-Type': 'application/json',
      },
      credentials: 'include',
    });

    const result = await response.json().catch(() => ({}));

    if (!response.ok) {
      return { success: false, data: null, status: response.status };
    }

    const raw = result.data !== undefined ? result.data : result;
    const list = Array.isArray(raw) ? raw : [];
    return { success: true, data: list };
  } catch (error) {
    console.error('활동 유형 목록 조회 실패:', error);
    return { success: false, data: null };
  }
};

/**
 * 사용자 활동 이력 상세 조회
 */
export const getActivityLogDetail = async (id) => {
  try {
    const response = await fetch(`${getApiBaseUrl()}/activity-log/${id}`, {
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

