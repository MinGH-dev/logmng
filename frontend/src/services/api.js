import axios from 'axios';
import { getSecureStorage, removeSecureStorage } from '../utils/security';
import logger from '../utils/logger';

// API 기본 설정
const API_BASE_URL = process.env.REACT_APP_API_BASE_URL || 'http://localhost:9200/api';

const api = axios.create({
  baseURL: API_BASE_URL,
  timeout: 30000,
  withCredentials: true, // 세션 쿠키 전달 (통계·활동 로그 등 인증 필요 API용)
  headers: {
    'Content-Type': 'application/json',
  },
});

// 통계 API 전용 설정 (9100 포트 Node.js 서버 사용)
const STATISTICS_API_BASE_URL = process.env.REACT_APP_STATISTICS_API_BASE_URL || 'http://localhost:9100/api';

const statisticsApiClient = axios.create({
  baseURL: STATISTICS_API_BASE_URL,
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json',
  },
});

// 통계 API 요청 인터셉터 (인증 토큰이 필요한 경우)
statisticsApiClient.interceptors.request.use(
  (config) => {
    // 토큰이 있다면 헤더에 추가 (필요한 경우)
    const token = getSecureStorage('accessToken');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

// 통계 API 응답 인터셉터
statisticsApiClient.interceptors.response.use(
  (response) => {
    return response;
  },
  (error) => {
    // 통계 API는 인증 실패 시에도 에러를 그대로 전달
    return Promise.reject(error);
  }
);

// 요청 인터셉터
api.interceptors.request.use(
  (config) => {
    // 토큰이 있다면 헤더에 추가
    const token = getSecureStorage('accessToken');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

// 응답 인터셉터
api.interceptors.response.use(
  (response) => {
    return response;
  },
  (error) => {
    if (error.response?.status === 401) {
      // 인증 실패 시 로그인 페이지로 리다이렉트
      removeSecureStorage('accessToken');
      window.location.href = '/login';
    }
    return Promise.reject(error);
  }
);

// 로그 조회 API
export const logApi = {
  // 로그 검색
  searchLogs: async (searchParams) => {
    try {
      const response = await api.post('/logs/search', searchParams);
      return response.data;
    } catch (error) {
      logger.error('로그 검색 중 오류 발생:', { error: error.message });
      throw error;
    }
  },

  // 로그 상세 조회
  getLogDetail: async (logId) => {
    try {
      const response = await api.get(`/logs/${logId}`);
      return response.data;
    } catch (error) {
      logger.error('로그 상세 조회 중 오류 발생:', { error: error.message });
      throw error;
    }
  },

  // 복호화된 데이터 조회
  getDecryptedData: async (logId) => {
    try {
      const response = await api.get(`/logs/${logId}/decrypt`);
      return response.data;
    } catch (error) {
      logger.error('복호화된 데이터 조회 중 오류 발생:', { error: error.message });
      throw error;
    }
  }
};

// 매체코드 목록 조회 API
export const mediaApi = {
  getMediaCodes: async () => {
    try {
      const response = await api.get('/media-codes');
      return response.data;
    } catch (error) {
      logger.error('매체코드 목록 조회 중 오류 발생:', { error: error.message });
      throw error;
    }
  }
};

// TR Code 목록 조회 API
export const trCodeApi = {
  getTrCodes: async () => {
    try {
      const response = await api.get('/tr-codes');
      return response.data;
    } catch (error) {
      logger.error('TR Code 목록 조회 중 오류 발생:', { error: error.message });
      throw error;
    }
  }
};

// 통계 API (9200 백엔드 사용 - contract 기준)
export const statisticsApi = {
  // 일별 통계 조회 (필터 조건 추가)
  getDailyStatistics: async (startDate, endDate, filters = {}) => {
    try {
      const response = await api.get('/statistics/activity/daily', {
        params: {
          startDate,
          endDate,
          ...filters
        }
      });
      return response.data;
    } catch (error) {
      logger.error('일별 통계 조회 중 오류 발생:', { error: error.message });
      throw error;
    }
  },

  // 월별 통계 조회 (필터 조건 추가)
  getMonthlyStatistics: async (year, month, filters = {}) => {
    try {
      const response = await api.get('/statistics/activity/monthly', {
        params: {
          year,
          month,
          ...filters
        }
      });
      return response.data;
    } catch (error) {
      logger.error('월별 통계 조회 중 오류 발생:', { error: error.message });
      throw error;
    }
  },

  // 사용자별 통계 조회
  getUserStatistics: async (userId, startDate, endDate, periodType = 'daily') => {
    try {
      const response = await api.get('/statistics/activity/user', {
        params: {
          userId,
          startDate,
          endDate,
          periodType
        }
      });
      return response.data;
    } catch (error) {
      logger.error('사용자별 통계 조회 중 오류 발생:', { error: error.message });
      throw error;
    }
  },

  // 모든 사용자별 통계 조회
  getAllUserStatistics: async (startDate, endDate, filters = {}) => {
    try {
      const params = {
        startDate,
        endDate,
        ...filters
      };
      const response = await api.get('/statistics/activity/users/all', { params });
      return response.data;
    } catch (error) {
      logger.error('모든 사용자별 통계 조회 중 오류 발생:', { error: error.message });
      throw error;
    }
  },

  // 사용자 목록 조회
  getUserList: async () => {
    try {
      const response = await api.get('/statistics/users');
      return response.data;
    } catch (error) {
      logger.error('사용자 목록 조회 중 오류 발생:', { error: error.message });
      throw error;
    }
  },

  // 부서 목록 조회
  getDepartmentList: async () => {
    try {
      const response = await api.get('/statistics/departments');
      return response.data;
    } catch (error) {
      logger.error('부서 목록 조회 중 오류 발생:', { error: error.message });
      throw error;
    }
  },

  // IP 목록 조회
  getIpList: async () => {
    try {
      const response = await api.get('/statistics/ips');
      return response.data;
    } catch (error) {
      logger.error('IP 목록 조회 중 오류 발생:', { error: error.message });
      throw error;
    }
  },

  // Excel 다운로드
  exportStatistics: async (type, queryParams) => {
    try {
      const response = await api.get('/statistics/activity/export', {
        params: { type, ...queryParams },
        responseType: 'blob'
      });
      return response.data;
    } catch (error) {
      logger.error('Excel 다운로드 중 오류 발생:', { error: error.message });
      throw error;
    }
  }
};

// 로그 타입 API (9200 백엔드 사용 - contract 기준)
export const logTypeApi = {
  // 로그 타입 목록 조회
  getLogTypeList: async (enabledOnly = true) => {
    try {
      const response = await api.get('/log-types', {
        params: enabledOnly !== undefined ? { enabledOnly: enabledOnly.toString() } : {}
      });
      return response.data;
    } catch (error) {
      logger.error('로그 타입 목록 조회 중 오류 발생:', { error: error.message });
      throw error;
    }
  },

  // 로그 타입 조회 (ID로)
  getLogTypeById: async (id) => {
    try {
      const response = await api.get(`/log-types/${id}`);
      return response.data;
    } catch (error) {
      logger.error('로그 타입 조회 중 오류 발생:', { error: error.message });
      throw error;
    }
  },

  // 로그 타입 추가
  addLogType: async (logType) => {
    try {
      const response = await api.post('/log-types', logType);
      return response.data;
    } catch (error) {
      logger.error('로그 타입 추가 중 오류 발생:', { error: error.message });
      throw error;
    }
  },

  // 로그 타입 수정
  updateLogType: async (id, updates) => {
    try {
      const response = await api.put(`/log-types/${id}`, updates);
      return response.data;
    } catch (error) {
      logger.error('로그 타입 수정 중 오류 발생:', { error: error.message });
      throw error;
    }
  },

  // 로그 타입 삭제
  deleteLogType: async (id) => {
    try {
      const response = await api.delete(`/log-types/${id}`);
      return response.data;
    } catch (error) {
      logger.error('로그 타입 삭제 중 오류 발생:', { error: error.message });
      throw error;
    }
  }
};

export default api; 