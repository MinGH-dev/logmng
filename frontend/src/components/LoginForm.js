import React, { useState, useEffect } from 'react';
import './LoginForm.css';
import logger from '../utils/logger';
import { getApiBaseUrl } from '../config/runtimeApi';
import { fetchAuthLoginMode } from '../services/authConfigService';

const LoginForm = ({ onLogin }) => {
  const [formData, setFormData] = useState({
    userId: '',
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
      const userIdTrimmed = (formData.userId || '').trim();
      if (!userIdTrimmed) {
        newErrors.userId = '사용자 ID를 입력해주세요.';
      } else {
        const userIdNum = Number(userIdTrimmed);
        if (Number.isNaN(userIdNum) || !Number.isInteger(userIdNum) || userIdNum < 0) {
          newErrors.userId = '사용자 ID는 숫자여야 합니다.';
        }
      }
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
            userId: Number(formData.userId),
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
        let errorMessage = result.error || '로그인에 실패했습니다.';

        if (response.status === 403) {
          errorMessage = '🔒 접근이 제한된 IP 주소입니다.\n시스템 관리자에게 접근 권한을 요청하세요.';
        } else if (response.status === 401) {
          errorMessage = '❌ 인증 정보가 올바르지 않습니다.\n사용자명과 비밀번호를 다시 확인해주세요.';
        } else if (response.status === 400) {
          errorMessage = '⚠️ 입력 정보가 부족합니다.\n모든 필드를 올바르게 입력해주세요.';
        } else if (response.status === 500) {
          errorMessage = '🚨 서버 오류가 발생했습니다.\n잠시 후 다시 시도하거나 관리자에게 문의하세요.';
        } else if (response.status === 0 || !response.ok) {
          errorMessage = '🌐 네트워크 연결을 확인해주세요.\n서버에 연결할 수 없습니다.';
        }

        setErrors({ general: errorMessage });
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
              <label htmlFor="userId">
                사용자 ID <span className="required">*</span>
              </label>
              <input
                type="number"
                id="userId"
                name="userId"
                value={formData.userId}
                onChange={handleInputChange}
                className={errors.userId ? 'error' : ''}
                placeholder="사용자 ID를 입력하세요 (예: 20260001)"
                disabled={loading}
                inputMode="numeric"
                step="1"
              />
              {errors.userId && <span className="error-message">{errors.userId}</span>}
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
