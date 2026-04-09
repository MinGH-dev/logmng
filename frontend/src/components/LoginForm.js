import React, { useState, useEffect } from 'react';
import './LoginForm.css';
import logger from '../utils/logger';
import { getApiBaseUrl } from '../config/runtimeApi';
import { fetchAuthLoginMode } from '../services/authConfigService';
import { getLoginFailureMessage } from '../utils/errorMessage';

const LoginForm = ({ onLogin }) => {
  const [formData, setFormData] = useState({
    employeeNumber: '',
    principal: '',
    password: '',
  });
  const [errors, setErrors] = useState({});
  const [loading, setLoading] = useState(false);
  const [authMode, setAuthMode] = useState(null);
  const [modeLoading, setModeLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const mode = await fetchAuthLoginMode();
        if (!cancelled) setAuthMode(mode);
      } catch (e) {
        if (!cancelled) setAuthMode('local');
      } finally {
        if (!cancelled) setModeLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  const handleInputChange = (e) => {
    const { name, value } = e.target;
    setFormData((prev) => ({
      ...prev,
      [name]: value,
    }));

    if (errors[name]) {
      setErrors((prev) => ({
        ...prev,
        [name]: '',
      }));
    }
  };

  const handleSubmit = async (e) => {
    e.preventDefault();

    const newErrors = {};
    const mode = authMode || 'local';

    if (mode === 'local') {
      const employeeNumber = (formData.employeeNumber || '').trim();
      if (!employeeNumber) newErrors.employeeNumber = '사용자 ID(사번)를 입력해주세요.';
    } else {
      const p = (formData.principal || '').trim();
      if (!p) {
        newErrors.principal = '로그인 ID(Principal)를 입력해주세요.';
      }
    }

    if (!formData.password) newErrors.password = '비밀번호를 입력해주세요.';

    if (Object.keys(newErrors).length > 0) {
      setErrors(newErrors);
      return;
    }

    setLoading(true);
    setErrors({});

    const LOGIN_TIMEOUT_MS = 10000;
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), LOGIN_TIMEOUT_MS);

    const apiBaseUrl = getApiBaseUrl();
    const requestBody =
      mode === 'local'
        ? {
            employeeNumber: (formData.employeeNumber || '').trim(),
            password: formData.password,
          }
        : {
            principal: (formData.principal || '').trim(),
            password: formData.password,
          };

    try {
      const response = await fetch(`${apiBaseUrl}/auth/login`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        credentials: 'include',
        body: JSON.stringify(requestBody),
        signal: controller.signal,
      });
      clearTimeout(timeoutId);

      const result = await response.json();

      if (result.success) {
        const userData =
          result.data?.user ?? (result.data?.username ? result.data : null) ?? result.user ?? null;

        if (userData) {
          logger.info('✅ 로그인 성공:', { username: userData.username || 'unknown' });
          onLogin(userData);
        } else {
          logger.error('❌ 로그인 응답에 사용자 정보가 없습니다:', { result });
          setErrors({ general: '🚨 로그인 응답 오류가 발생했습니다.\n관리자에게 문의하세요.' });
        }
      } else {
        setErrors({ general: getLoginFailureMessage(response.status, result) });
      }
    } catch (error) {
      clearTimeout(timeoutId);
      logger.error('❌ 로그인 요청 중 오류:', {
        error: error.message,
        apiBaseUrl,
        loginUrl: `${apiBaseUrl}/auth/login`,
      });

      if (error.name === 'TypeError' && error.message.includes('fetch')) {
        setErrors({
          general:
            '🌐 서버에 연결할 수 없습니다.\n' +
            '브라우저가 API에 도달하지 못했습니다(백엔드 로그에 요청이 안 남는 경우가 많음).\n\n' +
            `시도한 API: ${apiBaseUrl}\n` +
            '백엔드에 LOGMNG_API_BASE_URL(또는 정적 서버 환경)로 API 주소를 주거나, www/runtime-config.js 에 apiBaseUrl을 넣으세요. 재빌드 없이 가능합니다.\n' +
            'CORS_ALLOWED_ORIGINS에 UI 주소(예: http://서버IP:3001)가 정확히 포함돼 있는지 확인하세요.',
        });
      } else if (error.name === 'AbortError') {
        setErrors({
          general:
            '⏱️ 요청 시간이 초과되었습니다.\n' +
            `시도한 API: ${apiBaseUrl}\n` +
            '방화벽·포트·백엔드 기동 여부를 확인하세요.',
        });
      } else {
        setErrors({ general: '🚨 예상치 못한 오류가 발생했습니다.\n페이지를 새로고침하거나 관리자에게 문의하세요.' });
      }
    } finally {
      clearTimeout(timeoutId);
      setLoading(false);
    }
  };

  if (modeLoading || authMode == null) {
    return (
      <div className="login-container">
        <div className="login-form-wrapper">
          <div className="login-header">
            <h1>로그 관리 시스템</h1>
            <p>로그인 설정을 불러오는 중…</p>
          </div>
        </div>
      </div>
    );
  }

  const isAd = authMode === 'ad';

  return (
    <div className="login-container">
      <div className="login-form-wrapper">
        <div className="login-header">
          <h1>로그 관리 시스템</h1>
          <p>관리자 로그인이 필요합니다</p>
        </div>

        <form onSubmit={handleSubmit} className="login-form">
          {isAd ? (
            <div className="form-group">
              <label htmlFor="principal">
                로그인 ID (Principal) <span className="required">*</span>
              </label>
              <input
                type="text"
                id="principal"
                name="principal"
                value={formData.principal}
                onChange={handleInputChange}
                className={errors.principal ? 'error' : ''}
                placeholder="디렉터리 로그인 식별자 (예: UPN)"
                disabled={loading}
                autoComplete="username"
              />
              {errors.principal && <span className="error-message">{errors.principal}</span>}
            </div>
          ) : (
            <div className="form-group">
              <label htmlFor="employeeNumber">
                사용자 ID (사번) <span className="required">*</span>
              </label>
              <input
                type="text"
                id="employeeNumber"
                name="employeeNumber"
                value={formData.employeeNumber}
                onChange={handleInputChange}
                className={errors.employeeNumber ? 'error' : ''}
                placeholder="사번을 입력하세요 (예: EMP-2026-0001)"
                disabled={loading}
                autoComplete="username"
              />
              {errors.employeeNumber && <span className="error-message">{errors.employeeNumber}</span>}
            </div>
          )}

          <div className="form-group">
            <label htmlFor="password">
              비밀번호 <span className="required">*</span>
            </label>
            <input
              type="password"
              id="password"
              name="password"
              value={formData.password}
              onChange={handleInputChange}
              className={errors.password ? 'error' : ''}
              placeholder="비밀번호를 입력하세요"
              disabled={loading}
              autoComplete="current-password"
            />
            {errors.password && <span className="error-message">{errors.password}</span>}
          </div>

          {errors.general && (
            <div className="error-message general-error">
              {errors.general}
            </div>
          )}

          <button
            type="submit"
            className="login-button"
            disabled={loading}
          >
            {loading ? '로그인 중...' : '로그인'}
          </button>
        </form>

        <div className="login-footer">
          <p>관리자 계정으로 로그인하세요</p>
        </div>
      </div>
    </div>
  );
};

export default LoginForm;
