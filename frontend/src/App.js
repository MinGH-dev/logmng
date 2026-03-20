import React, { useState, useEffect } from 'react';
import { ThemeProvider, Box } from '@mui/material';
import { appTheme } from './theme';
import './App.css';
import LoginForm from './components/LoginForm';
import LogGrid from './components/LogGrid';
import UserActivityLogList from './components/UserActivityLog/UserActivityLogList';
import ActivityStatistics from './components/ActivityStatistics';
import SearchHistoryList from './components/SearchHistory/SearchHistoryList';
import UserManagement from './components/UserManagement/UserManagement';
import PermissionGroupManagement from './components/PermissionGroupManagement/PermissionGroupManagement';
import PendingApprovals from './components/PendingApprovals/PendingApprovals';
import AppSidebar from './components/AppSidebar';
import AppBar from './components/AppBar';
import { ORDERED_SCREEN_IDS } from './constants/menuTree';
import {
  saveMinimalUserData,
  getMinimalUserData,
  getAllowedScreenIds,
  getScreenFunctions,
  getSelfContext,
  deriveScreenFunctionsFromAllowed,
  clearUserData,
} from './utils/security';
import logger from './utils/logger';
import { getApiBaseUrl } from './config/runtimeApi';

/** logType objects for log-search screens (req 20260318). */
const LOG_TYPE_BY_VIEW = {
  'pb-feplog': { id: 'pb_feplog', name: 'PB FEP Log', description: '' },
  'java-fw-imagelog': { id: 'java_fw_imglog', name: 'Java FW Image Log', description: '' },
};

function App() {
  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);
  const [sidebarOpen, setSidebarOpen] = useState(true);
  const [currentView, setCurrentView] = useState('pb-feplog'); // 'pb-feplog' | 'java-fw-imagelog' | 'activity-log' | ...
  const [initialSearchParams, setInitialSearchParams] = useState(null);
  const [initialSearchApprovalId, setInitialSearchApprovalId] = useState(null);

  const canAccessView = (view) => {
    if (user?.isSystemAdmin === true) return true;
    const ids = getAllowedScreenIds(user);
    if (!ids || ids.length === 0) return false;
    if (view === 'user-management') {
      return ids.includes('user-management') || ids.includes('user-permission-hierarchy');
    }
    if (view === 'permission-group-management') {
      return ids.includes('permission-group-management') || ids.includes('user-permission-hierarchy');
    }
    return ids.includes(view);
  };

  const getFirstAllowedScreen = (u) => {
    if (u?.isSystemAdmin === true) return ORDERED_SCREEN_IDS[0];
    const ids = getAllowedScreenIds(u);
    if (!ids || ids.length === 0) return ORDERED_SCREEN_IDS[0];
    const first = ORDERED_SCREEN_IDS.find((sid) => ids.includes(sid));
    return first ?? ORDERED_SCREEN_IDS[0];
  };

  useEffect(() => {
    checkAuthStatus();
  }, []);

  useEffect(() => {
    if (!isAuthenticated || !user) return;
    const ids = getAllowedScreenIds(user);
    const allowed = user?.isSystemAdmin === true || (ids && ids.length > 0);
    if (!allowed) return;
    const canAccess =
      user?.isSystemAdmin === true ||
      (currentView === 'user-management' || currentView === 'user-permission-hierarchy'
        ? ids.includes('user-management') || ids.includes('user-permission-hierarchy')
        : currentView === 'permission-group-management'
          ? ids.includes('permission-group-management') || ids.includes('user-permission-hierarchy')
          : ids.includes(currentView));
    if (!canAccess) setCurrentView(getFirstAllowedScreen(user));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isAuthenticated, user]);

  const AUTH_CHECK_TIMEOUT_MS = 5000;

  const checkAuthStatus = async () => {
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), AUTH_CHECK_TIMEOUT_MS);
    try {
      const apiBaseUrl = getApiBaseUrl();
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
        const apiSelfContext = getSelfContext(fromApi);
        const savedSelfContext = getSelfContext(savedUser);
        const apiScreenFunctions = getScreenFunctions(fromApi);
        const mergedScreenFunctions = apiScreenFunctions && typeof apiScreenFunctions === 'object'
          ? apiScreenFunctions
          : (savedUser?.screenFunctions && typeof savedUser.screenFunctions === 'object' ? savedUser.screenFunctions : null);
        const fallbackScreenFunctions = !mergedScreenFunctions
          ? deriveScreenFunctionsFromAllowed(getAllowedScreenIds(fromApi) ?? savedUser?.allowedScreenIds, fromApi?.isSystemAdmin ?? savedUser?.isSystemAdmin ?? false)
          : null;
        const merged = savedUser
          ? {
              username: fromApi?.username ?? savedUser.username,
              isSystemAdmin: fromApi?.isSystemAdmin ?? savedUser.isSystemAdmin ?? false,
              allowedScreenIds: getAllowedScreenIds(fromApi) ?? savedUser?.allowedScreenIds ?? null,
              screenScopes: fromApi?.screenScopes && typeof fromApi.screenScopes === 'object'
                ? fromApi.screenScopes
                : savedUser?.screenScopes ?? null,
              screenFunctions: mergedScreenFunctions ?? fallbackScreenFunctions,
              selfContext: apiSelfContext ?? savedSelfContext,
            }
          : fromApi?.username
            ? {
                username: fromApi.username,
                isSystemAdmin: fromApi?.isSystemAdmin ?? false,
                allowedScreenIds: getAllowedScreenIds(fromApi),
                screenScopes: fromApi?.screenScopes && typeof fromApi.screenScopes === 'object' ? fromApi.screenScopes : null,
                screenFunctions: mergedScreenFunctions ?? fallbackScreenFunctions,
                selfContext: apiSelfContext,
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
    const sf = getScreenFunctions(userData);
    const selfContext = getSelfContext(userData);
    const minimalUserData = {
      username: userData.username || null,
      isSystemAdmin: userData.isSystemAdmin === true,
      allowedScreenIds: getAllowedScreenIds(userData),
      screenScopes: userData.screenScopes && typeof userData.screenScopes === 'object' ? userData.screenScopes : null,
      screenFunctions: sf && typeof sf === 'object' ? sf : deriveScreenFunctionsFromAllowed(getAllowedScreenIds(userData), userData.isSystemAdmin === true),
      selfContext,
    };
    setUser(minimalUserData);
    setIsAuthenticated(true);
    saveMinimalUserData(minimalUserData);
  };

  const handleLogout = async () => {
    try {
      const apiBaseUrl = getApiBaseUrl();
      await fetch(`${apiBaseUrl}/auth/logout`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
      });
    } catch (error) {
      logger.debug('로그아웃 요청 실패:', { error: error.message });
    } finally {
      setUser(null);
      setIsAuthenticated(false);
      clearUserData();
      setCurrentView('pb-feplog');
    }
  };

  useEffect(() => {
    if (!isAuthenticated || !user) return;
    const isAdmin = user?.isSystemAdmin === true;
    const ids = getAllowedScreenIds(user);
    const hasAccess =
      isAdmin ||
      (ids &&
        ids.length > 0 &&
        (currentView === 'user-management' || currentView === 'user-permission-hierarchy'
          ? ids.includes('user-management') || ids.includes('user-permission-hierarchy')
          : currentView === 'permission-group-management'
            ? ids.includes('permission-group-management') || ids.includes('user-permission-hierarchy')
            : ids.includes(currentView)));
    if (!hasAccess) setCurrentView(getFirstAllowedScreen(user));
  }, [isAuthenticated, user, currentView]);

  const handleNavigate = (view) => {
    if (!canAccessView(view)) {
      setCurrentView(getFirstAllowedScreen(user));
      return;
    }
    setCurrentView(view);
  };

  const handleReSearchFromHistory = (data) => {
    if (!data || !data.logType) return;
    const view = data.logType === 'pb_feplog' ? 'pb-feplog' : 'java-fw-imagelog';
    setInitialSearchParams(data.searchParams || null);
    setInitialSearchApprovalId(data.id != null ? data.id : null);
    setCurrentView(view);
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
            teamName={user?.selfContext?.department ?? ''}
            userName={user?.selfContext?.username ?? user?.username ?? ''}
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
            {currentView === 'pending-approvals' && <PendingApprovals user={user} />}
            {currentView === 'pb-feplog' && canAccessView('pb-feplog') && (
              <LogGrid
                logType={LOG_TYPE_BY_VIEW['pb-feplog']}
                initialSearchParams={initialSearchParams}
                initialSearchApprovalId={initialSearchApprovalId}
                onInitialSearchDone={handleInitialSearchDone}
                hasDecryptPermission={user?.isSystemAdmin === true || getScreenFunctions(user)?.['pb-feplog']?.decrypt === true}
              />
            )}
            {currentView === 'java-fw-imagelog' && canAccessView('java-fw-imagelog') && (
              <LogGrid
                logType={LOG_TYPE_BY_VIEW['java-fw-imagelog']}
                initialSearchParams={initialSearchParams}
                initialSearchApprovalId={initialSearchApprovalId}
                onInitialSearchDone={handleInitialSearchDone}
                hasDecryptPermission={user?.isSystemAdmin === true || getScreenFunctions(user)?.['java-fw-imagelog']?.decrypt === true}
              />
            )}
          </Box>
        </Box>
      </Box>
    </ThemeProvider>
  );
}

export default App;
