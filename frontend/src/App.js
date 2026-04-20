import React, { useState, useEffect, useRef } from 'react';
import { ThemeProvider, Box } from '@mui/material';
import { appTheme } from './theme';
import './App.css';
import LoginForm from './components/LoginForm';
import LogGrid from './components/LogGrid';
import UserActivityLogList from './components/UserActivityLog/UserActivityLogList';
import ActivityLogAccessAuditList from './components/ActivityLogAccessAudit/ActivityLogAccessAuditList';
import ActivityStatistics from './components/ActivityStatistics';
import SearchHistoryList from './components/SearchHistory/SearchHistoryList';
import UserManagement from './components/UserManagement/UserManagement';
import UserManagementLegacy from './components/UserManagement/UserManagementLegacy';
import HrSyncPocPreview from './components/UserManagement/HrSyncPocPreview';
import UserManagementPoc from './components/UserManagement/UserManagementPoc';
import PermissionGroupManagement from './components/PermissionGroupManagement/PermissionGroupManagement';
import PermissionGroupScreenMatrix from './components/PermissionGroupScreenMatrix/PermissionGroupScreenMatrix';
import PendingApprovals from './components/PendingApprovals/PendingApprovals';
import AppSidebar, { DRAWER_WIDTH_OPEN, DRAWER_WIDTH_COLLAPSED } from './components/AppSidebar';
import AppBar from './components/AppBar';
import { ORDERED_SCREEN_IDS } from './constants/menuTree';
import {
  canAccessView as policyCanAccessView,
  canNonAdminAccessCurrentView,
  canAccessDeepLinkHrSyncPoc,
  canAccessDeepLinkUserManagementV2Poc,
} from './constants/screenAccessPolicy';
import { useScreenDisplayLabels } from './hooks/useScreenDisplayLabels';
import ScreenDisplayLabelsSettings from './components/ScreenDisplayLabelsSettings/ScreenDisplayLabelsSettings';
import {
  saveMinimalUserData,
  getMinimalUserData,
  getAllowedScreenIds,
  getScreenFunctions,
  getSelfContext,
  deriveScreenFunctionsFromAllowed,
  clearUserData,
  hasEffectiveAppAccess,
} from './utils/security';
import { getLastViewId, setLastViewId, clearLastViewStorage } from './utils/lastViewStorage';
import { getVisibleAdminSidebarChildViews } from './constants/screenAccessPolicy';
import { isScreenAccessDiagnosticEnabled } from './config/screenAccessDiagnostic';
import { isHrSyncPocMenuEnabled } from './config/hrSyncPocUi';
import NoPermissionDialog from './components/NoPermissionDialog';
import MyPageModal from './components/MyPageModal';
import logger from './utils/logger';
import { getApiBaseUrl } from './config/runtimeApi';

/** Prefer legacy PB FEP menu when both screens are allowed (검색 이력 재조회 등). */
const resolvePbFeplogViewForUser = (u) => {
  const ids = getAllowedScreenIds(u) ?? [];
  if (ids.includes('pb-feplog')) return 'pb-feplog';
  if (ids.includes('pb-fep-log-search')) return 'pb-fep-log-search';
  return 'pb-feplog';
};

function App() {
  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);
  const [sidebarOpen, setSidebarOpen] = useState(true);
  const [currentView, setCurrentView] = useState('pb-feplog'); // 'pb-feplog' | 'java-fw-imagelog' | 'activity-log' | ...
  const [initialSearchParams, setInitialSearchParams] = useState(null);
  const [initialSearchApprovalId, setInitialSearchApprovalId] = useState(null);
  const [accessAuditInitialTargetId, setAccessAuditInitialTargetId] = useState(null);
  const [myPageOpen, setMyPageOpen] = useState(false);
  const [lastViewRestoreDone, setLastViewRestoreDone] = useState(false);
  const didRestoreLastViewRef = useRef(false);

  const { labelItems, setLabelItems, mergedMenuTree, logTypesByView } = useScreenDisplayLabels(
    isAuthenticated,
    user
  );

  const canAccessView = (view) =>
    policyCanAccessView(view, {
      allowedScreenIds: getAllowedScreenIds(user),
      isSystemAdmin: user?.isSystemAdmin === true,
    });

  /** Same rules as sidebar: policy + non-admin gate (req 20260420 preserve view on refresh). */
  const isViewAllowedForLastView = (view, u) => {
    if (!u) return false;
    const ids = getAllowedScreenIds(u) ?? [];
    const isAdmin = u?.isSystemAdmin === true;
    return (
      policyCanAccessView(view, {
        allowedScreenIds: ids,
        isSystemAdmin: isAdmin,
      }) && (isAdmin || canNonAdminAccessCurrentView(view, ids))
    );
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
    if (!isAuthenticated) {
      didRestoreLastViewRef.current = false;
      setLastViewRestoreDone(false);
    }
  }, [isAuthenticated]);

  /** Dev-only: compare session allowedScreenIds vs effective 관리 submenu (req 20260414 diagnostic). */
  useEffect(() => {
    if (!user || !isScreenAccessDiagnosticEnabled()) return;
    const ids = getAllowedScreenIds(user) ?? [];
    const visibleAdminMenuViews = getVisibleAdminSidebarChildViews(ids, {
      isSystemAdmin: user.isSystemAdmin === true,
      isHrSyncPocMenuEnabled: () => isHrSyncPocMenuEnabled(),
    });
    logger.debug('Screen access diagnostic (req 20260414)', {
      isSystemAdmin: user.isSystemAdmin === true,
      allowedScreenIdsCount: ids.length,
      allowedScreenIdsSorted: [...ids].slice().sort(),
      visibleAdminMenuViews,
    });
  }, [user]);

  useEffect(() => {
    if (!isAuthenticated || !user) return;
    if (!hasEffectiveAppAccess(user)) return;
    const ids = getAllowedScreenIds(user);
    const allowed = user?.isSystemAdmin === true || (ids && ids.length > 0);
    if (!allowed) return;
    const canAccess =
      user?.isSystemAdmin === true || canNonAdminAccessCurrentView(currentView, ids);
    if (!canAccess) setCurrentView(getFirstAllowedScreen(user));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isAuthenticated, user]);

  /** Deep link: /user-management/hr-sync-poc (same permission as user-management). */
  useEffect(() => {
    if (!isAuthenticated || !user || !hasEffectiveAppAccess(user)) return;
    const path = (window.location.pathname || '').replace(/\/$/, '');
    if (!path.endsWith('/user-management/hr-sync-poc')) return;
    const ids = getAllowedScreenIds(user) ?? [];
    const ok = canAccessDeepLinkHrSyncPoc({
      allowedScreenIds: ids,
      isSystemAdmin: user?.isSystemAdmin === true,
    });
    if (ok) setCurrentView('hr-sync-poc');
  }, [isAuthenticated, user]);

  /** Deep link: /user-management/poc-v2 — PoC UM v2 clone (ScreenAccessInterceptor § user-mgmt PoC). */
  useEffect(() => {
    if (!isAuthenticated || !user || !hasEffectiveAppAccess(user)) return;
    const path = (window.location.pathname || '').replace(/\/$/, '');
    if (!path.endsWith('/user-management/poc-v2')) return;
    const ids = getAllowedScreenIds(user) ?? [];
    const ok = canAccessDeepLinkUserManagementV2Poc({
      allowedScreenIds: ids,
      isSystemAdmin: user?.isSystemAdmin === true,
    });
    if (ok) setCurrentView('user-management-v2-poc');
  }, [isAuthenticated, user]);

  /**
   * Restore last main-menu view from sessionStorage after deep-link effects (req 20260420).
   * Runs once per authenticated session (didRestoreLastViewRef); reset when isAuthenticated becomes false.
   */
  useEffect(() => {
    if (!isAuthenticated || !user || !hasEffectiveAppAccess(user)) return;
    if (didRestoreLastViewRef.current) return;

    const path = (window.location.pathname || '').replace(/\/$/, '');
    const isDeepLinkPath =
      path.endsWith('/user-management/hr-sync-poc') || path.endsWith('/user-management/poc-v2');

    if (isDeepLinkPath) {
      didRestoreLastViewRef.current = true;
      setLastViewRestoreDone(true);
      return;
    }

    const stored = getLastViewId();
    if (stored && isViewAllowedForLastView(stored, user)) {
      setCurrentView(stored);
    } else if (stored) {
      clearLastViewStorage();
    }

    didRestoreLastViewRef.current = true;
    setLastViewRestoreDone(true);
  }, [isAuthenticated, user]);

  useEffect(() => {
    if (!isAuthenticated || !user || !hasEffectiveAppAccess(user)) return;
    if (!lastViewRestoreDone) return;

    if (isViewAllowedForLastView(currentView, user)) {
      setLastViewId(currentView);
    } else {
      clearLastViewStorage();
    }
  }, [isAuthenticated, user, currentView, lastViewRestoreDone]);

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

  const replaceHistoryForLoginView = () => {
    try {
      const path = window.location.pathname || '/';
      const search = window.location.search || '';
      window.history.replaceState(null, '', path + search);
    } catch {
      /* ignore */
    }
  };

  const handleLogout = async () => {
    try {
      const apiBaseUrl = getApiBaseUrl();
      await fetch(`${apiBaseUrl}/auth/logout`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
      });
    } catch (error) {
      logger.debug('로그아웃 요청 실패:', { error: error.message });
    } finally {
      setUser(null);
      setIsAuthenticated(false);
      clearUserData();
      setCurrentView('pb-feplog');
      replaceHistoryForLoginView();
    }
  };

  const handleNoPermissionConfirm = async () => {
    try {
      const apiBaseUrl = getApiBaseUrl();
      await fetch(`${apiBaseUrl}/auth/logout`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
      });
    } catch (error) {
      logger.debug('권한 없음 모달 로그아웃 실패:', { error: error.message });
    } finally {
      setUser(null);
      setIsAuthenticated(false);
      clearUserData();
      setCurrentView('pb-feplog');
      replaceHistoryForLoginView();
    }
  };

  useEffect(() => {
    if (!isAuthenticated || !user) return;
    if (!hasEffectiveAppAccess(user)) return;
    const isAdmin = user?.isSystemAdmin === true;
    const ids = getAllowedScreenIds(user);
    const hasAccess =
      isAdmin || (ids && ids.length > 0 && canNonAdminAccessCurrentView(currentView, ids));
    if (!hasAccess) setCurrentView(getFirstAllowedScreen(user));
  }, [isAuthenticated, user, currentView]);

  const handleNavigate = (view) => {
    if (!canAccessView(view)) {
      setCurrentView(getFirstAllowedScreen(user));
      return;
    }
    setCurrentView(view);
    if (view === 'hr-sync-poc') {
      try {
        window.history.replaceState(null, '', '/user-management/hr-sync-poc');
      } catch {
        /* ignore */
      }
    } else if (view === 'user-management-v2-poc') {
      try {
        window.history.replaceState(null, '', '/user-management/poc-v2');
      } catch {
        /* ignore */
      }
    } else {
      try {
        const p = window.location.pathname || '';
        if (p.includes('hr-sync-poc') || p.includes('poc-v2')) window.history.replaceState(null, '', '/');
      } catch {
        /* ignore */
      }
    }
  };

  const handleReSearchFromHistory = (data) => {
    if (!data || !data.logType) return;
    const view = data.logType === 'pb_feplog' ? resolvePbFeplogViewForUser(user) : 'java-fw-imagelog';
    setInitialSearchParams(data.searchParams || null);
    setInitialSearchApprovalId(data.id != null ? data.id : null);
    setCurrentView(view);
  };

  const handleInitialSearchDone = () => {
    setInitialSearchParams(null);
    setInitialSearchApprovalId(null);
  };

  const horizontalScrollViews = new Set([
    'pending-approvals',
    'search-history',
    'statistics',
    'user-management',
    'user-management-v2',
    'user-permission-hierarchy',
    'hr-sync-poc',
    'user-management-v2-poc',
  ]);
  const isHorizontalScrollView = horizontalScrollViews.has(currentView);

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

  if (user && !hasEffectiveAppAccess(user)) {
    return (
      <ThemeProvider theme={appTheme}>
        <NoPermissionDialog open onConfirm={handleNoPermissionConfirm} />
      </ThemeProvider>
    );
  }

  return (
    <ThemeProvider theme={appTheme}>
      <Box
        className="App"
        sx={(theme) => ({
          display: 'flex',
          width: '100%',
          maxWidth: '100vw',
          height: '100vh',
          overflow: 'hidden',
          bgcolor: 'background.default',
          /* Main column insets for portals/modals (req 20260408-activity-log-detail-modal-viewport-centering) */
          '--app-main-inset-left': `${sidebarOpen ? DRAWER_WIDTH_OPEN : DRAWER_WIDTH_COLLAPSED}px`,
          '--app-main-inset-top': theme.spacing(7),
        })}
      >
        <AppSidebar
          open={sidebarOpen}
          isAdmin={user?.isSystemAdmin === true}
          allowedScreenIds={getAllowedScreenIds(user) ?? []}
          currentView={currentView}
          onNavigate={handleNavigate}
          menuTree={mergedMenuTree}
        />
        <Box
          component="main"
          sx={{
            flex: 1,
            minHeight: 0,
            display: 'flex',
            flexDirection: 'column',
            minWidth: 0,
            position: 'relative',
            zIndex: 0,
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
            onOpenMyPage={() => setMyPageOpen(true)}
          />
          <MyPageModal open={myPageOpen} onClose={() => setMyPageOpen(false)} />
          <Box
            sx={{
              flex: 1,
              p: 2,
              mt: 7,
              overflowY: currentView === 'pb-fep-log-search' ? 'hidden' : 'auto',
              overflowX: isHorizontalScrollView ? 'auto' : 'hidden',
              minHeight: 0,
              display: 'flex',
              flexDirection: 'column',
            }}
            data-horizontal-scroll-enabled={isHorizontalScrollView ? 'true' : 'false'}
          >
            {currentView === 'activity-log' && (
              <UserActivityLogList
                user={user}
                canOpenAccessAudit={canAccessView('activity-log-access-audit')}
                onNavigateToAccessAudit={(targetLogId) => {
                  setAccessAuditInitialTargetId(targetLogId);
                  if (canAccessView('activity-log-access-audit')) {
                    setCurrentView('activity-log-access-audit');
                  }
                }}
              />
            )}
            {currentView === 'activity-log-access-audit' && canAccessView('activity-log-access-audit') && (
              <ActivityLogAccessAuditList
                initialTargetActivityLogId={accessAuditInitialTargetId}
                onConsumedInitialTarget={() => setAccessAuditInitialTargetId(null)}
              />
            )}
            {currentView === 'statistics' && <ActivityStatistics user={user} />}
            {currentView === 'search-history' && (
              <SearchHistoryList user={user} onReSearch={handleReSearchFromHistory} />
            )}
            {(currentView === 'user-management' || currentView === 'user-permission-hierarchy') && (
              <UserManagementLegacy user={user} />
            )}
            {currentView === 'user-management-v2' && <UserManagement user={user} />}
            {currentView === 'user-management-v2-poc' &&
              (canAccessView('user-management-v2-poc') ? (
                <UserManagementPoc user={user} />
              ) : (
                <Box component="p" role="status" sx={{ m: 0 }}>
                  이 화면에 접근할 권한이 없습니다.
                </Box>
              ))}
            {currentView === 'hr-sync-poc' &&
              (canAccessView('hr-sync-poc') ? (
                <HrSyncPocPreview />
              ) : (
                <Box component="p" role="status" sx={{ m: 0 }}>
                  이 화면에 접근할 권한이 없습니다.
                </Box>
              ))}
            {currentView === 'permission-group-management' && (
              <PermissionGroupManagement user={user} menuTree={mergedMenuTree} />
            )}
            {currentView === 'permission-group-screen-matrix' && (
              <PermissionGroupScreenMatrix user={user} menuTree={mergedMenuTree} />
            )}
            {currentView === 'screen-display-labels' && (
              <ScreenDisplayLabelsSettings
                user={user}
                labelItems={labelItems}
                onLabelsUpdated={setLabelItems}
              />
            )}
            {currentView === 'pending-approvals' && <PendingApprovals user={user} />}
            {currentView === 'pb-feplog' && canAccessView('pb-feplog') && (
              <Box sx={{ flex: 1, minHeight: 0, display: 'flex', flexDirection: 'column', minWidth: 0 }}>
                <LogGrid
                  viewId="pb-feplog"
                  logType={logTypesByView['pb-feplog']}
                  initialSearchParams={initialSearchParams}
                  initialSearchApprovalId={initialSearchApprovalId}
                  onInitialSearchDone={handleInitialSearchDone}
                  hasDecryptPermission={user?.isSystemAdmin === true || getScreenFunctions(user)?.['pb-feplog']?.decrypt === true}
                />
              </Box>
            )}
            {currentView === 'pb-fep-log-search' && canAccessView('pb-fep-log-search') && (
              <Box sx={{ flex: 1, minHeight: 0, display: 'flex', flexDirection: 'column', minWidth: 0 }}>
                <LogGrid
                  viewId="pb-fep-log-search"
                  logType={logTypesByView['pb-fep-log-search']}
                  initialSearchParams={initialSearchParams}
                  initialSearchApprovalId={initialSearchApprovalId}
                  onInitialSearchDone={handleInitialSearchDone}
                  hasDecryptPermission={
                    user?.isSystemAdmin === true || getScreenFunctions(user)?.['pb-fep-log-search']?.decrypt === true
                  }
                />
              </Box>
            )}
            {currentView === 'java-fw-imagelog' && canAccessView('java-fw-imagelog') && (
              <LogGrid
                logType={logTypesByView['java-fw-imagelog']}
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
