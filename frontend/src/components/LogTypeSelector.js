import React, { useState, useEffect } from 'react';
import './LogTypeSelector.css';
import { getApiBaseUrl } from '../config/runtimeApi';

const LogTypeSelector = ({ onSelectLogType }) => {
  const [logTypes, setLogTypes] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    fetchLogTypes();
  }, []);

  const fetchLogTypes = async () => {
    try {
      setLoading(true);
      const apiBaseUrl = getApiBaseUrl();
      const response = await fetch(`${apiBaseUrl}/log-types`);
      const result = await response.json();
      
      if (result.success) {
        setLogTypes(result.data || []);
      } else {
        setError('로그 타입 목록을 불러오는데 실패했습니다.');
      }
    } catch (error) {
      console.error('로그 타입 목록 조회 중 오류:', error);
      setError('로그 타입 목록을 불러오는데 실패했습니다.');
    } finally {
      setLoading(false);
    }
  };

  const handleSelect = (logType) => {
    onSelectLogType(logType);
  };

  if (loading) {
    return (
      <div className="log-type-selector-container">
        <div className="loading-container">
          <div className="loading-spinner"></div>
          <p>로그 타입 목록을 불러오는 중...</p>
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="log-type-selector-container">
        <div className="error-container">
          <p className="error-message">{error}</p>
          <button onClick={fetchLogTypes} className="retry-button">
            다시 시도
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="log-type-selector-container">
      <div className="log-type-selector">
        <h2>로그 타입을 선택하세요</h2>
        <div className="log-type-grid">
          {logTypes.map((logType) => (
            <div
              key={logType.id}
              className="log-type-card"
              onClick={() => handleSelect(logType)}
            >
              <div className="log-type-icon">
                <span className="icon">📋</span>
              </div>
              <div className="log-type-info">
                <h3>{logType.name}</h3>
                <p className="log-type-description">{logType.description}</p>
                <div className="log-type-tables">
                  <span className="table-label">테이블:</span>
                  <span className="table-names">
                    {logType.tables?.join(', ') || 'N/A'}
                  </span>
                </div>
              </div>
              <div className="log-type-arrow">→</div>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
};

export default LogTypeSelector;

