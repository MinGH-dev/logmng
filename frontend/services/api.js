import axios from 'axios';

// API 기본 설정
const API_BASE_URL = process.env.REACT_APP_API_BASE_URL || 'http://localhost:9200/api';

const api = axios.create({
  baseURL: API_BASE_URL,
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json',
  },
});

// 요청 인터셉터
api.interceptors.request.use(
  (config) => {
    // 토큰이 있다면 헤더에 추가
    const token = localStorage.getItem('accessToken');
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
      localStorage.removeItem('accessToken');
      window.location.href = '/login';
    }
    return Promise.reject(error);
  }
);

// 로그 조회 API (파일 기반)
export const logApi = {
  // 로그 검색
  searchLogs: async (searchParams) => {
    try {
      const response = await api.post('/logs/search', searchParams);
      return response.data;
    } catch (error) {
      console.error('로그 검색 중 오류 발생:', error);
      throw error;
    }
  },

  // 로그 상세 조회
  getLogDetail: async (logId) => {
    try {
      const response = await api.get(`/logs/${logId}`);
      return response.data;
    } catch (error) {
      console.error('로그 상세 조회 중 오류 발생:', error);
      throw error;
    }
  },

  // 복호화된 데이터 조회
  getDecryptedData: async (logId) => {
    try {
      const response = await api.get(`/logs/${logId}/decrypt`);
      return response.data;
    } catch (error) {
      console.error('복호화된 데이터 조회 중 오류 발생:', error);
      throw error;
    }
  }
};

// DB 기반 로그 조회 API
export const logDbApi = {
  // DB 로그 검색 (리팩토링된 버전)
  searchLogsDB: async (searchParams) => {
    try {
      const response = await api.post('/logs/db-refactored/search', searchParams);
      return response.data;
    } catch (error) {
      console.error('DB 로그 검색 중 오류 발생:', error);
      throw error;
    }
  },

  // DB 로그 상세 조회
  getLogDetailDB: async (type, id) => {
    try {
      const response = await api.get(`/logs/db-refactored/${type}/${id}`);
      return response.data;
    } catch (error) {
      console.error('DB 로그 상세 조회 중 오류 발생:', error);
      throw error;
    }
  },

  // DB 로그 통계 조회
  getLogStatsDB: async (searchParams) => {
    try {
      const response = await api.post('/logs/db-refactored/stats', searchParams);
      return response.data;
    } catch (error) {
      console.error('DB 로그 통계 조회 중 오류 발생:', error);
      throw error;
    }
  },

  // 스키마 정보 조회
  getSchemaInfo: async () => {
    try {
      const response = await api.get('/logs/db-refactored/schema');
      return response.data;
    } catch (error) {
      console.error('스키마 정보 조회 중 오류 발생:', error);
      throw error;
    }
  },

  // DB 연결 상태 확인
  checkDBConnection: async () => {
    try {
      const response = await api.get('/logs/db-refactored/health');
      return response.data;
    } catch (error) {
      console.error('DB 연결 상태 확인 중 오류 발생:', error);
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
      console.error('매체코드 목록 조회 중 오류 발생:', error);
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
      console.error('TR Code 목록 조회 중 오류 발생:', error);
      throw error;
    }
  }
};

export default api; 