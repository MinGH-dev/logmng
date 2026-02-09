import React, { useState, useEffect } from 'react';
import './App.css';
import LoginForm from './components/LoginForm';
import LogTypeSelector from './components/LogTypeSelector';
import LogGrid from './components/LogGrid';
import UserActivityLogList from './components/UserActivityLog/UserActivityLogList';
import ActivityStatistics from './components/ActivityStatistics';
import { saveMinimalUserData, getMinimalUserData, clearUserData } from './utils/security';
import logger from './utils/logger';

function App() {
  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);
  const [selectedLogType, setSelectedLogType] = useState(null);
  const [currentView, setCurrentView] = useState('main'); // 'main' | 'activity-log' | 'statistics'

  // 컴포넌트 마운트 시 인증 상태 확인 및 선택된 로그 타입 복원
  useEffect(() => {
    checkAuthStatus();
    // 로컬 스토리지에서 선택된 로그 타입 복원
    const savedLogType = localStorage.getItem('selectedLogType');
    if (savedLogType) {
      try {
        setSelectedLogType(JSON.parse(savedLogType));
      } catch (e) {
        logger.error('로그 타입 복원 실패:', { error: e.message });
      }
    }
  }, []);

  // 인증 상태 확인
  const checkAuthStatus = async () => {
    try {
      const apiBaseUrl = process.env.REACT_APP_API_BASE_URL || 'http://localhost:9200/api';
      const response = await fetch(`${apiBaseUrl}/auth/check`);
      const result = await response.json();
      
      if (result.success && result.authenticated) {
        setIsAuthenticated(true);
        // 로컬 스토리지에서 사용자 정보 복원
        const savedUser = getMinimalUserData();
        if (savedUser) {
          setUser(savedUser);
        }
      }
    } catch (error) {
      logger.debug('인증 상태 확인 실패:', { error: error.message });
    } finally {
      setLoading(false);
    }
  };

  // 로그인 성공 처리
  const handleLogin = (userData) => {
    if (!userData) {
      logger.error('로그인 처리 실패: 사용자 데이터가 없습니다');
      return;
    }
    
    // 최소한의 사용자 정보만 저장
    const minimalUserData = {
      username: userData.username || null
    };
    setUser(minimalUserData);
    setIsAuthenticated(true);
    // 사용자 정보를 로컬 스토리지에 최소화하여 저장
    saveMinimalUserData(userData);
  };

  // 로그아웃 처리
  const handleLogout = async () => {
    try {
      const apiBaseUrl = process.env.REACT_APP_API_BASE_URL || 'http://localhost:9200/api';
      await fetch(`${apiBaseUrl}/auth/logout`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        }
      });
    } catch (error) {
      logger.debug('로그아웃 요청 실패:', { error: error.message });
    } finally {
      setUser(null);
      setIsAuthenticated(false);
      setSelectedLogType(null);
      // 모든 사용자 관련 데이터 삭제
      clearUserData();
    }
  };
  
  // 로그 타입 선택 처리
  const handleLogTypeSelect = (logType) => {
    setSelectedLogType(logType);
    localStorage.setItem('selectedLogType', JSON.stringify(logType));
  };
  
  // 뒤로가기 처리 (로그 타입 선택 화면으로)
  const handleBackToLogTypeSelect = () => {
    setSelectedLogType(null);
    localStorage.removeItem('selectedLogType');
  };

  // 활동 이력 화면으로 이동
  const handleShowActivityLog = () => {
    setCurrentView('activity-log');
  };

  // 메인 화면으로 돌아가기
  const handleBackToMain = () => {
    setCurrentView('main');
  };

  // 통계 화면으로 이동
  const handleShowStatistics = () => {
    setCurrentView('statistics');
  };

  // 로딩 중일 때
  if (loading) {
    return (
      <div className="App">
        <div className="loading-container">
          <div className="loading-spinner"></div>
          <p>시스템을 초기화하는 중...</p>
        </div>
      </div>
    );
  }

  // 인증되지 않은 경우 로그인 페이지 표시
  if (!isAuthenticated) {
    return <LoginForm onLogin={handleLogin} />;
  }

  // 인증된 경우 메인 페이지 표시
  return (
    <div className="App">
      <header className="App-header">
        <div className="header-content">
          <h1>로그 관리 시스템</h1>
          <div className="user-info">
            {currentView === 'activity-log' || currentView === 'statistics' ? (
              <button onClick={handleBackToMain} className="back-button">
                ← 메인으로
              </button>
            ) : selectedLogType ? (
              <button onClick={handleBackToLogTypeSelect} className="back-button">
                ← 로그 타입 선택
              </button>
            ) : null}
            {currentView !== 'activity-log' && currentView !== 'statistics' && (
              <>
                <button onClick={handleShowActivityLog} className="activity-log-button">
                  활동 이력
                </button>
                <button onClick={handleShowStatistics} className="activity-log-button">
                  활동로그 통계
                </button>
              </>
            )}
            <span className="welcome-text">환영합니다, {user?.username}님</span>
            <button onClick={handleLogout} className="logout-button">
              로그아웃
            </button>
          </div>
        </div>
      </header>
      <main>
        {currentView === 'activity-log' ? (
          <UserActivityLogList />
        ) : currentView === 'statistics' ? (
          <ActivityStatistics />
        ) : !selectedLogType ? (
          <LogTypeSelector onSelectLogType={handleLogTypeSelect} />
        ) : (
          <LogGrid logType={selectedLogType} />
        )}
      </main>
    </div>
  );
}

export default App; 