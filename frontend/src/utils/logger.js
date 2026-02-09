/**
 * 환경별 로그 레벨 관리 및 개인정보 마스킹 유틸리티
 */

// 환경 확인
const isDevelopment = process.env.NODE_ENV === 'development';
const isProduction = process.env.NODE_ENV === 'production';

/**
 * 개인정보 마스킹 함수
 * @param {string} data - 마스킹할 데이터
 * @param {number} visibleStart - 앞에 보여줄 문자 수 (기본값: 4)
 * @param {number} visibleEnd - 뒤에 보여줄 문자 수 (기본값: 4)
 * @returns {string} 마스킹된 데이터
 */
export const maskSensitiveData = (data, visibleStart = 4, visibleEnd = 4) => {
  if (!data || typeof data !== 'string') {
    return data;
  }

  const length = data.length;
  
  // 데이터가 너무 짧으면 전체 마스킹
  if (length <= visibleStart + visibleEnd) {
    return '*'.repeat(length);
  }

  const start = data.substring(0, visibleStart);
  const end = data.substring(length - visibleEnd);
  const masked = '*'.repeat(Math.max(0, length - visibleStart - visibleEnd));

  return `${start}${masked}${end}`;
};

/**
 * 객체 내 개인정보 마스킹 처리
 * @param {any} obj - 마스킹할 객체
 * @param {string[]} sensitiveKeys - 민감한 정보가 포함된 키 목록
 * @returns {any} 마스킹된 객체
 */
export const maskSensitiveObject = (obj, sensitiveKeys = []) => {
  if (!obj || typeof obj !== 'object') {
    return obj;
  }

  const defaultSensitiveKeys = [
    'decrypted_datastring',
    'decrypted_headerstring',
    'datastring',
    'headerstring',
    'data',
    'header',
    'password',
    'token',
    'accessToken',
    'refreshToken',
    'email',
    'phone',
    'accountNumber',
    'accountNumbers',
    'loginId',
    'username',
    'user',
    'keywords',
    'searchParams'
  ];

  const allSensitiveKeys = [...defaultSensitiveKeys, ...sensitiveKeys];
  const masked = Array.isArray(obj) ? [...obj] : { ...obj };

  for (const key in masked) {
    if (allSensitiveKeys.some(sensitiveKey => 
      key.toLowerCase().includes(sensitiveKey.toLowerCase())
    )) {
      if (typeof masked[key] === 'string' && masked[key].length > 0) {
        masked[key] = maskSensitiveData(masked[key]);
      } else if (typeof masked[key] === 'object' && masked[key] !== null) {
        masked[key] = maskSensitiveObject(masked[key], sensitiveKeys);
      }
    } else if (typeof masked[key] === 'object' && masked[key] !== null) {
      masked[key] = maskSensitiveObject(masked[key], sensitiveKeys);
    }
  }

  return masked;
};

/**
 * 로그 레벨
 */
export const LogLevel = {
  DEBUG: 'DEBUG',
  INFO: 'INFO',
  WARN: 'WARN',
  ERROR: 'ERROR'
};

/**
 * 현재 로그 레벨 (프로덕션에서는 ERROR, WARN만 허용)
 */
const getCurrentLogLevel = () => {
  if (isProduction) {
    return [LogLevel.ERROR, LogLevel.WARN];
  }
  return [LogLevel.DEBUG, LogLevel.INFO, LogLevel.WARN, LogLevel.ERROR];
};

/**
 * 로그 출력 여부 확인
 * @param {string} level - 로그 레벨
 * @returns {boolean} 출력 여부
 */
const shouldLog = (level) => {
  const allowedLevels = getCurrentLogLevel();
  return allowedLevels.includes(level);
};

/**
 * 안전한 로그 출력 (개인정보 마스킹)
 * @param {string} level - 로그 레벨
 * @param {string} message - 로그 메시지
 * @param {any} data - 로그 데이터 (선택)
 * @param {boolean} maskData - 데이터 마스킹 여부 (기본값: true)
 */
const safeLog = (level, message, data = null, maskData = true) => {
  if (!shouldLog(level)) {
    return;
  }

  const logMethod = {
    [LogLevel.DEBUG]: console.debug,
    [LogLevel.INFO]: console.info,
    [LogLevel.WARN]: console.warn,
    [LogLevel.ERROR]: console.error
  }[level] || console.log;

  if (data !== null) {
    const logData = maskData ? maskSensitiveObject(data) : data;
    logMethod(message, logData);
  } else {
    logMethod(message);
  }
};

/**
 * 로거 객체
 */
export const logger = {
  /**
   * DEBUG 레벨 로그 (개발 환경에서만)
   */
  debug: (message, data = null) => {
    safeLog(LogLevel.DEBUG, message, data, true);
  },

  /**
   * INFO 레벨 로그
   */
  info: (message, data = null) => {
    safeLog(LogLevel.INFO, message, data, true);
  },

  /**
   * WARN 레벨 로그
   */
  warn: (message, data = null) => {
    safeLog(LogLevel.WARN, message, data, true);
  },

  /**
   * ERROR 레벨 로그
   */
  error: (message, data = null) => {
    safeLog(LogLevel.ERROR, message, data, false); // 에러는 마스킹하지 않음 (디버깅 필요)
  },

  /**
   * 안전한 로그 (마스킹 없이, 개발 환경에서만)
   */
  safe: (message, data = null) => {
    if (isDevelopment) {
      safeLog(LogLevel.DEBUG, message, data, false);
    }
  }
};

export default logger;





