/**
 * 보안 관련 유틸리티 함수
 */

/**
 * localStorage에 안전하게 데이터 저장
 * @param {string} key - 저장할 키
 * @param {any} value - 저장할 값
 * @param {boolean} encrypt - 암호화 여부 (기본값: false)
 */
export const setSecureStorage = (key, value, encrypt = false) => {
  try {
    const stringValue = typeof value === 'string' ? value : JSON.stringify(value);
    localStorage.setItem(key, stringValue);
  } catch (error) {
    console.error(`localStorage 저장 실패 (${key}):`, error);
  }
};

/**
 * localStorage에서 안전하게 데이터 가져오기
 * @param {string} key - 가져올 키
 * @param {any} defaultValue - 기본값
 * @returns {any} 저장된 값 또는 기본값
 */
export const getSecureStorage = (key, defaultValue = null) => {
  try {
    const value = localStorage.getItem(key);
    if (value === null) {
      return defaultValue;
    }
    try {
      return JSON.parse(value);
    } catch {
      return value;
    }
  } catch (error) {
    console.error(`localStorage 읽기 실패 (${key}):`, error);
    return defaultValue;
  }
};

/**
 * localStorage에서 데이터 삭제
 * @param {string} key - 삭제할 키
 */
export const removeSecureStorage = (key) => {
  try {
    localStorage.removeItem(key);
  } catch (error) {
    console.error(`localStorage 삭제 실패 (${key}):`, error);
  }
};

/**
 * 사용자 정보를 최소화하여 저장
 * @param {object} userData - 사용자 데이터
 */
export const saveMinimalUserData = (userData) => {
  if (!userData) {
    return;
  }

  // 필요한 최소한의 정보만 저장 (isSystemAdmin, allowedScreenIds, screenScopes — 메뉴·화면 접근·필터 표시용)
  const minimalData = {
    username: userData.username || null,
    isSystemAdmin: userData.isSystemAdmin === true,
    allowedScreenIds: Array.isArray(userData.allowedScreenIds) ? userData.allowedScreenIds : null,
    screenScopes: userData.screenScopes && typeof userData.screenScopes === 'object' ? userData.screenScopes : null,
  };

  setSecureStorage('user', minimalData);
};

/**
 * 사용자 정보 가져오기
 * @returns {object|null} 사용자 정보
 */
export const getMinimalUserData = () => {
  return getSecureStorage('user', null);
};

/**
 * 모든 사용자 관련 데이터 삭제
 */
export const clearUserData = () => {
  removeSecureStorage('user');
  removeSecureStorage('accessToken');
  removeSecureStorage('refreshToken');
  removeSecureStorage('selectedLogType');
};

/**
 * 에러 메시지 일반화
 * @param {Error|string} error - 에러 객체 또는 메시지
 * @param {string} defaultMessage - 기본 메시지
 * @returns {string} 일반화된 에러 메시지
 */
export const sanitizeErrorMessage = (error, defaultMessage = '오류가 발생했습니다.') => {
  // 개발 환경에서는 상세한 에러 정보 표시
  if (process.env.NODE_ENV === 'development') {
    if (error instanceof Error) {
      return `${defaultMessage} (${error.message})`;
    }
    return typeof error === 'string' ? error : defaultMessage;
  }

  // 프로덕션 환경에서는 일반적인 메시지만 표시
  return defaultMessage;
};

/**
 * 사용자에게 표시할 안전한 에러 메시지 생성
 * @param {string} context - 에러 컨텍스트 (예: '복호화', '검색')
 * @param {Error|string} error - 에러 객체 또는 메시지
 * @returns {string} 안전한 에러 메시지
 */
/** 복호화 승인 미완료 시 사용자 안내 문구 */
export const DECRYPTION_NOT_APPROVED_MESSAGE = "복호화 승인이 필요합니다. 먼저 '복호화 승인 요청'을 진행해 주세요.";

export const getUserFriendlyErrorMessage = (context, error) => {
  if (error && typeof error === 'object' && error.code === 'DECRYPTION_NOT_APPROVED') {
    return DECRYPTION_NOT_APPROVED_MESSAGE;
  }
  const defaultMessages = {
    '복호화': '복호화 중 오류가 발생했습니다. 관리자에게 문의하세요.',
    '검색': '검색 중 오류가 발생했습니다. 다시 시도해주세요.',
    '인증': '인증 중 오류가 발생했습니다. 다시 로그인해주세요.',
    '기본': '오류가 발생했습니다. 다시 시도해주세요.'
  };

  const defaultMessage = defaultMessages[context] || defaultMessages['기본'];
  return sanitizeErrorMessage(error, defaultMessage);
};

export default {
  setSecureStorage,
  getSecureStorage,
  removeSecureStorage,
  saveMinimalUserData,
  getMinimalUserData,
  clearUserData,
  sanitizeErrorMessage,
  getUserFriendlyErrorMessage
};





