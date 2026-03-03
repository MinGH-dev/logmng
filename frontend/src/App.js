import React, { useState, useEffect } from 'react';
import { ThemeProvider, Box } from '@mui/material';
import { appTheme } from './theme';
import './App.css';
import LoginForm from './components/LoginForm';
import LogTypeSelector from './components/LogTypeSelector';
import LogGrid from './components/LogGrid';
import UserActivityLogList from './components/UserActivityLog/UserActivityLogList';
import ActivityStatistics from './components/ActivityStatistics';
import SearchHistoryList from './components/SearchHistory/SearchHistoryList';
import UserManagement from './components/UserManagement/UserManagement';
import PermissionGroupManagement from './components/PermissionGroupManagement/PermissionGroupManagement';
import PendingApprovals from './components/PendingApprovals/PendingApprovals';
import AppSidebar from './components/AppSidebar';
import AppBar from './components/AppBar';
import { saveMinimalUserData, getMinimalUserData, getAllowedScreenIds, clearUserData } from './utils/security';
import logger from './utils/logger';

function App() {
  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);
  const [sidebarOpen, setSidebarOpen] = useState(true);
  const [selectedLogType, setSelectedLogType] = useState(null);
  const [currentView, setCurrentView] = useState('main'); // 'main' | 'activity-log' | 'statistics' | 'search-history' | 'user-management' | 'user-permission-hierarchy' | 'permission-group-management' | 'pending-approvals'
  const [initialSearchParams, setInitialSearchParams] = useState(null);
  const [initialSearchApprovalId, setInitialSearchApprovalId] = useState(null);

  const canAccessView = (view) => {
    if (user?.isSystemAdmin === true) return true;
    const ids = getAllowedScreenIds(user);
    if (!ids || ids.length === 0) return false;
    if (view === 'user-management') {
      return ids.includes('user-management') || ids.includes('user-permission-hierarchy');
    }
    return ids.includes(view);
  };

  useEffect(() => {
    checkAuthStatus();
    const savedLogType = localStorage.getItem('selectedLogType');
    if (savedLogType) {
      try {
        setSelectedLogType(JSON.parse(savedLogType));
      } catch (e) {
        logger.error('로그 타입 복원 실패:', { error: e.message });
      }
    }
  }, []);

  const AUTH_CHECK_TIMEOUT_MS = 5000;

  const checkAuthStatus = async () => {
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), AUTH_CHECK_TIMEOUT_MS);
    try {
      const apiBaseUrl = process.env.REACT_APP_API_BASE_URL || 'http://localhost:9200/api';
      const response = await fetch(`${apiBaseUrl}/auth/check`, {
        credentials: 'include',
        signal: controller.signal,
      });
      clearTimeout(timeoutId);
      const result = await response.json();

      if (result.success && result.data?.authenticated) {
        setIsAuthenticated(true);
        const savedUser = getMinimalUserData();
        const fromApi = result.data;
        const merged = savedUser
          ? {
              username: fromApi?.username ?? savedUser.username,
              isSystemAdmin: fromApi?.isSystemAdmin ?? savedUser.isSystemAdmin ?? false,
              allowedScreenIds: getAllowedScreenIds(fromApi) ?? savedUser?.allowedScreenIds ?? null,
              screenScopes: fromApi?.screenScopes && typeof fromApi.screenScopes === 'object'
                ? fromApi.screenScopes
                : savedUser?.screenScopes ?? null,
            }
          : fromApi?.username
            ? {
                username: fromApi.username,
                isSystemAdmin: fromApi?.isSystemAdmin ?? false,
                allowedScreenIds: getAllowedScreenIds(fromApi),
                screenScopes: fromApi?.screenScopes && typeof fromApi.screenScopes === 'object' ? fromApi.screenScopes : null,
              }
            : null;
        if (merged) {
          setUser(merged);
          saveMinimalUserData(merged);
        }
      }
    } catch (error) {
      clearTimeout(timeoutId);
      if (error.name === 'AbortError') {
        logger.debug('인증 상태 확인 타임아웃 — 로그인 화면으로 전환');
      } else {
        logger.debug('인증 상태 확인 실패:', { error: error.message });
      }
    } finally {
      setLoading(false);
    }
  };

  const handleLogin = (userData) => {
    if (!userData) {
      logger.error('로그인 처리 실패: 사용자 데이터가 없습니다');
      return;
    }
    const minimalUserData = {
      username: userData.username || null,
      isSystemAdmin: userData.isSystemAdmin === true,
      allowedScreenIds: getAllowedScreenIds(userData),
      screenScopes: userData.screenScopes && typeof userData.screenScopes === 'object' ? userData.screenScopes : null,
    };
    setUser(minimalUserData);
    setIsAuthenticated(true);
    saveMinimalUserData(minimalUserData);
  };

  const handleLogout = async () => {
    try {
      const apiBaseUrl = process.env.REACT_APP_API_BASE_URL || 'http://localhost:9200/api';
      await fetch(`${apiBaseUrl}/auth/logout`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
      });
    } catch (error) {
      logger.debug('로그아웃 요청 실패:', { error: error.message });
    } finally {
      setUser(null);
      setIsAuthenticated(false);
      setSelectedLogType(null);
      clearUserData();
    }
  };

  const handleLogTypeSelect = (logType) => {
    setSelectedLogType(logType);
    localStorage.setItem('selectedLogType', JSON.stringify(logType));
  };

  const handleSearchMain = () => {
    setCurrentView('main');
    setSelectedLogType(null);
    localStorage.removeItem('selectedLogType');
  };

  useEffect(() => {
    if (!isAuthenticated || !user) return;
    if (currentView === 'main') return;
    const isAdmin = user?.isSystemAdmin === true;
    const ids = getAllowedScreenIds(user);
    const hasAccess =
      isAdmin ||
      (ids &&
        ids.length > 0 &&
        (currentView === 'user-management' || currentView === 'user-permission-hierarchy'
          ? ids.includes('user-management') || ids.includes('user-permission-hierarchy')
          : ids.includes(currentView)));
    if (!hasAccess) setCurrentView('main');
  }, [isAuthenticated, user, currentView]);

  const handleNavigate = (view) => {
    if (!canAccessView(view)) {
      setCurrentView('main');
      return;
    }
    setCurrentView(view);
  };

  const handleReSearchFromHistory = (data) => {
    if (!data || !data.logType) return;
    setSelectedLogType({ id: data.logType, name: data.logType });
    setInitialSearchParams(data.searchParams || null);
    setInitialSearchApprovalId(data.id != null ? data.id : null);
    setCurrentView('main');
  };

  const handleInitialSearchDone = () => {
    setInitialSearchParams(null);
    setInitialSearchApprovalId(null);
  };

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

  if (!isAuthenticated) {
    return <LoginForm onLogin={handleLogin} />;
  }

  return (
    <ThemeProvider theme={appTheme}>
      <Box
        className="App"
        sx={{
          display: 'flex',
          width: '100%',
          maxWidth: '100vw',
          height: '100vh',
          overflow: 'hidden',
          bgcolor: 'background.default',
        }}
      >
        <AppSidebar
          open={sidebarOpen}
          isAdmin={user?.isSystemAdmin === true}
          allowedScreenIds={getAllowedScreenIds(user) ?? []}
          currentView={currentView}
          onNavigate={handleNavigate}
          onSearchMain={handleSearchMain}
        />
        <Box
          component="main"
          sx={{
            flex: 1,
            minHeight: 0,
            display: 'flex',
            flexDirection: 'column',
            minWidth: 0,
            overflowX: 'hidden',
            boxSizing: 'border-box',
          }}
        >
          <AppBar
            sidebarOpen={sidebarOpen}
            onToggleSidebar={() => setSidebarOpen((o) => !o)}
            username={user?.username}
            onLogout={handleLogout}
          />
          <Box sx={{ flex: 1, p: 2, mt: 7, overflowY: 'auto', minHeight: 0 }}>
            {currentView === 'activity-log' && <UserActivityLogList user={user} />}
            {currentView === 'statistics' && <ActivityStatistics user={user} />}
            {currentView === 'search-history' && (
              <SearchHistoryList user={user} onReSearch={handleReSearchFromHistory} />
            )}
            {(currentView === 'user-management' || currentView === 'user-permission-hierarchy') && (
              <UserManagement user={user} />
            )}
            {currentView === 'permission-group-management' && <PermissionGroupManagement user={user} />}
            {currentView === 'pending-approvals' && <PendingApprovals />}
            {currentView === 'main' && !selectedLogType && (
              <LogTypeSelector onSelectLogType={handleLogTypeSelect} />
            )}
            {currentView === 'main' && selectedLogType && (
              <LogGrid
                logType={selectedLogType}
                initialSearchParams={initialSearchParams}
                initialSearchApprovalId={initialSearchApprovalId}
                onInitialSearchDone={handleInitialSearchDone}
              />
            )}
          </Box>
        </Box>
      </Box>
    </ThemeProvider>
  );
}

export default App;
