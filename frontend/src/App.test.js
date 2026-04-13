import React from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import App from './App';
import { NO_PERMISSION_MESSAGE_KO } from './components/NoPermissionDialog';

jest.mock('./utils/logger', () => ({
  __esModule: true,
  default: { debug: jest.fn(), error: jest.fn(), info: jest.fn() },
}));

jest.mock('./components/LogGrid', () => {
  function Mock() {
    return <div data-testid="mock-log-grid" />;
  }
  return Mock;
});
jest.mock('./components/UserActivityLog/UserActivityLogList', () => () => null);
jest.mock('./components/ActivityStatistics', () => () => null);
jest.mock('./components/SearchHistory/SearchHistoryList', () => () => null);
jest.mock('./components/UserManagement/UserManagement', () => () => null);
jest.mock('./components/UserManagement/HrSyncPocPreview', () => () => null);
jest.mock('./components/UserManagement/UserManagementPoc', () => () => null);
jest.mock('./components/PermissionGroupManagement/PermissionGroupManagement', () => () => null);
jest.mock('./components/PermissionGroupScreenMatrix/PermissionGroupScreenMatrix', () => () => null);
jest.mock('./components/PendingApprovals/PendingApprovals', () => () => null);
jest.mock('./components/ScreenDisplayLabelsSettings/ScreenDisplayLabelsSettings', () => () => null);

jest.mock('./components/AppSidebar', () => {
  function AppSidebar({ onNavigate }) {
    return (
      <aside data-testid="app-sidebar">
        <button type="button" onClick={() => onNavigate?.('pending-approvals')}>to-pending-approvals</button>
        <button type="button" onClick={() => onNavigate?.('search-history')}>to-search-history</button>
        <button type="button" onClick={() => onNavigate?.('statistics')}>to-statistics</button>
        <button type="button" onClick={() => onNavigate?.('user-management')}>to-user-management</button>
        <button type="button" onClick={() => onNavigate?.('pb-feplog')}>to-pb-feplog</button>
      </aside>
    );
  }
  return AppSidebar;
});
jest.mock('./components/AppBar', () => () => <header data-testid="app-bar">bar</header>);

jest.mock('./hooks/useScreenDisplayLabels', () => ({
  useScreenDisplayLabels: () => ({
    labelItems: [],
    setLabelItems: () => {},
    mergedMenuTree: [],
    logTypesByView: { 'pb-feplog': { id: 'pb_feplog' }, 'pb-fep-log-search': { id: 'pb_feplog' }, 'java-fw-imagelog': { id: 'java_fw_imglog' } },
  }),
}));

describe('App zero-permission gate (TC-F02, TC-F03)', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    localStorage.clear();
  });

  test('no sidebar when authenticated with empty allowedScreenIds (TC-F03)', async () => {
    global.fetch = jest.fn((url) => {
      const u = String(url);
      if (u.includes('/auth/check')) {
        return Promise.resolve({
          ok: true,
          json: () =>
            Promise.resolve({
              success: true,
              data: {
                authenticated: true,
                username: 'noperm',
                isSystemAdmin: false,
                allowedScreenIds: [],
              },
            }),
        });
      }
      if (u.includes('/auth/config')) {
        return Promise.resolve({ ok: false, status: 404 });
      }
      return Promise.reject(new Error(`unexpected fetch ${url}`));
    });

    render(<App />);

    await waitFor(() => {
      expect(screen.queryByTestId('app-sidebar')).not.toBeInTheDocument();
    });

    expect(await screen.findByText(NO_PERMISSION_MESSAGE_KO)).toBeInTheDocument();
  });

  test('confirm no-permission modal triggers logout fetch (TC-F02)', async () => {
    global.fetch = jest.fn((url, opts) => {
      const u = String(url);
      if (u.includes('/auth/check')) {
        return Promise.resolve({
          ok: true,
          json: () =>
            Promise.resolve({
              success: true,
              data: {
                authenticated: true,
                username: 'noperm',
                isSystemAdmin: false,
                allowedScreenIds: [],
              },
            }),
        });
      }
      if (u.includes('/auth/logout') && opts?.method === 'POST') {
        return Promise.resolve({
          ok: true,
          json: () => Promise.resolve({ success: true, data: null }),
        });
      }
      if (u.includes('/auth/config')) {
        return Promise.resolve({ ok: false, status: 404 });
      }
      return Promise.reject(new Error(`unexpected fetch ${url}`));
    });

    render(<App />);

    await waitFor(() => {
      expect(screen.getByRole('button', { name: '확인' })).toBeInTheDocument();
    });

    await userEvent.click(screen.getByRole('button', { name: '확인' }));

    await waitFor(() => {
      expect(global.fetch).toHaveBeenCalledWith(
        expect.stringContaining('/auth/logout'),
        expect.objectContaining({ method: 'POST', credentials: 'include' })
      );
    });
    await waitFor(() => {
      expect(screen.queryByText(NO_PERMISSION_MESSAGE_KO)).not.toBeInTheDocument();
    });
  });
});

describe('App HR Sync PoC view without user-management permission', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    localStorage.clear();
  });

  test('shows Korean no-access message when current view is hr-sync-poc but canAccessView is false', async () => {
    global.fetch = jest.fn((url) => {
      const u = String(url);
      if (u.includes('/auth/check')) {
        return Promise.resolve({
          ok: true,
          json: () =>
            Promise.resolve({
              success: true,
              data: {
                authenticated: true,
                username: 'hrpoconly',
                isSystemAdmin: false,
                allowedScreenIds: ['hr-sync-poc'],
              },
            }),
        });
      }
      if (u.includes('/auth/config')) {
        return Promise.resolve({ ok: false, status: 404 });
      }
      return Promise.reject(new Error(`unexpected fetch ${url}`));
    });

    render(<App />);

    await waitFor(() => {
      expect(screen.getByText('이 화면에 접근할 권한이 없습니다.')).toBeInTheDocument();
    });
  });
});

describe('App horizontal scroll enablement for narrow viewport targets', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    localStorage.clear();
  });

  test('enables horizontal scroll only on target views', async () => {
    global.fetch = jest.fn((url) => {
      const u = String(url);
      if (u.includes('/auth/check')) {
        return Promise.resolve({
          ok: true,
          json: () =>
            Promise.resolve({
              success: true,
              data: {
                authenticated: true,
                username: 'tester',
                isSystemAdmin: false,
                allowedScreenIds: [
                  'pb-feplog',
                  'pending-approvals',
                  'search-history',
                  'statistics',
                  'user-management',
                ],
              },
            }),
        });
      }
      if (u.includes('/auth/config')) {
        return Promise.resolve({ ok: false, status: 404 });
      }
      return Promise.reject(new Error(`unexpected fetch ${url}`));
    });

    const { container } = render(<App />);

    await waitFor(() => {
      expect(screen.getByTestId('app-sidebar')).toBeInTheDocument();
    });

    const scrollContainer = container.querySelector('[data-horizontal-scroll-enabled]');
    expect(scrollContainer).not.toBeNull();
    expect(scrollContainer).toHaveAttribute('data-horizontal-scroll-enabled', 'false');

    await userEvent.click(screen.getByRole('button', { name: 'to-pending-approvals' }));
    await waitFor(() => {
      expect(scrollContainer).toHaveAttribute('data-horizontal-scroll-enabled', 'true');
    });

    await userEvent.click(screen.getByRole('button', { name: 'to-pb-feplog' }));
    await waitFor(() => {
      expect(scrollContainer).toHaveAttribute('data-horizontal-scroll-enabled', 'false');
    });
  });
});
