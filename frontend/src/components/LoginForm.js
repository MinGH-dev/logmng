import React, { useState } from 'react';
import './LoginForm.css';
import logger from '../utils/logger';

const LoginForm = ({ onLogin }) => {
  const [formData, setFormData] = useState({
    username: '',
    password: ''
  });
  const [errors, setErrors] = useState({});
  const [loading, setLoading] = useState(false);

  // 폼 데이터 변경 처리
  const handleInputChange = (e) => {
    const { name, value } = e.target;
    setFormData(prev => ({
      ...prev,
      [name]: value
    }));

    // 에러 제거
    if (errors[name]) {
      setErrors(prev => ({
        ...prev,
        [name]: ''
      }));
    }
  };

  // 로그인 처리
  const handleSubmit = async (e) => {
    e.preventDefault();
    
    // 필수 필드 검증
    const newErrors = {};
    if (!formData.username) newErrors.username = '사용자명을 입력해주세요.';
    if (!formData.password) newErrors.password = '비밀번호를 입력해주세요.';

    if (Object.keys(newErrors).length > 0) {
      setErrors(newErrors);
      return;
    }

    setLoading(true);
    setErrors({});

    const LOGIN_TIMEOUT_MS = 10000; // 10초
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), LOGIN_TIMEOUT_MS);

    try {
      const apiBaseUrl = process.env.REACT_APP_API_BASE_URL || 'http://localhost:9200/api';
      const response = await fetch(`${apiBaseUrl}/auth/login`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        credentials: 'include',
        body: JSON.stringify(formData),
        signal: controller.signal,
      });
      clearTimeout(timeoutId);

      const result = await response.json();

      if (result.success) {
        // API 응답 구조에 따라 user 데이터 추출
        const userData = result.user || result.data?.user || result.data || null;
        
        if (userData) {
          logger.info('✅ 로그인 성공:', { username: userData.username || 'unknown' });
          onLogin(userData);
        } else {
          logger.error('❌ 로그인 응답에 사용자 정보가 없습니다:', { result });
          setErrors({ general: '🚨 로그인 응답 오류가 발생했습니다.\n관리자에게 문의하세요.' });
        }
      } else {
        // HTTP 상태 코드에 따른 에러 메시지 처리
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
      logger.error('❌ 로그인 요청 중 오류:', { error: error.message });

      if (error.name === 'TypeError' && error.message.includes('fetch')) {
        setErrors({ general: '🌐 서버에 연결할 수 없습니다.\n네트워크 연결을 확인해주세요.' });
      } else if (error.name === 'AbortError') {
        setErrors({ general: '⏱️ 요청 시간이 초과되었습니다.\n서버(백엔드)가 동작 중인지 확인해주세요.' });
      } else {
        setErrors({ general: '🚨 예상치 못한 오류가 발생했습니다.\n페이지를 새로고침하거나 관리자에게 문의하세요.' });
      }
    } finally {
      clearTimeout(timeoutId);
      setLoading(false);
    }
  };

  return (
    <div className="login-container">
      <div className="login-form-wrapper">
        <div className="login-header">
          <h1>로그 관리 시스템</h1>
          <p>관리자 로그인이 필요합니다</p>
        </div>
        
        <form onSubmit={handleSubmit} className="login-form">
          <div className="form-group">
            <label htmlFor="username">
              사용자명 <span className="required">*</span>
            </label>
            <input
              type="text"
              id="username"
              name="username"
              value={formData.username}
              onChange={handleInputChange}
              className={errors.username ? 'error' : ''}
              placeholder="사용자명을 입력하세요"
              disabled={loading}
            />
            {errors.username && <span className="error-message">{errors.username}</span>}
          </div>

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
