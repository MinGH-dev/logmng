import React from 'react';
import { render, screen, within, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import PermissionGroupScreenMatrix from './PermissionGroupScreenMatrix';

jest.mock('../../services/permissionGroupService', () => ({
  listPermissionGroups: jest.fn(() => Promise.resolve([])),
  getPermissionGroup: jest.fn(() =>
    Promise.resolve({
      data: { id: 1, code: 'A', name: 'Alpha', description: null, allowedScreens: [] },
    })
  ),
  updatePermissionGroup: jest.fn(),
  createPermissionGroup: jest.fn(() => Promise.resolve({ data: { id: 99, code: 'NEW', name: 'New' } })),
  deletePermissionGroup: jest.fn(() => Promise.resolve({})),
}));

jest.mock('@mui/material', () => {
  const React = require('react');
  return {
    Tooltip: ({ children }) => <span>{children}</span>,
    Snackbar: ({ children, open }) => (open ? <div data-testid="snackbar">{children}</div> : null),
    Alert: ({ children }) => <div>{children}</div>,
  };
});

describe('PermissionGroupScreenMatrix', () => {
  test('forbidden message when user has no admin permission-group access', () => {
    render(
      <PermissionGroupScreenMatrix
        user={{ allowedScreenIds: ['pb-feplog'], isSystemAdmin: false }}
      />
    );
    expect(screen.getByText(/관리자만 접근할 수 있습니다/)).toBeInTheDocument();
  });

  test('renders title when user may access (system admin)', () => {
    const { container } = render(<PermissionGroupScreenMatrix user={{ isSystemAdmin: true }} />);
    expect(
      screen.getByRole('heading', { name: /권한 그룹 관리 — 화면별 기능/ })
    ).toBeInTheDocument();
    const root = container.querySelector('.pgsm-root');
    expect(root).toHaveAttribute('data-layout', 'two-pane-group-list-matrix');
    expect(root).toHaveAttribute('data-matrix-pagination', 'none');
    expect(root).toHaveAttribute('data-group-list-actions', 'add-delete');
    expect(root).toHaveAttribute('data-scope-cell', 'compact-2char');
    expect(root).toHaveAttribute('data-grid-checkbox-size', '14');
  });

  test('group list header has 추가/삭제; 삭제 disabled until selection', async () => {
    const listPermissionGroups = require('../../services/permissionGroupService').listPermissionGroups;
    listPermissionGroups.mockResolvedValueOnce([{ id: 1, code: 'A', name: 'Alpha' }]);
    render(
      <PermissionGroupScreenMatrix
        user={{
          isSystemAdmin: true,
          screenFunctions: { 'permission-group-management': { write: true } },
          allowedScreenIds: ['permission-group-management'],
        }}
      />
    );
    const aside = screen.getByRole('complementary', { name: /권한 그룹 목록/ });
    await waitFor(() => {
      expect(within(aside).getByRole('button', { name: /Alpha/ })).toBeInTheDocument();
    });
    expect(within(aside).getByRole('button', { name: '추가' })).toBeEnabled();
    expect(within(aside).getByRole('button', { name: '삭제' })).toBeDisabled();
    await userEvent.click(within(aside).getByRole('button', { name: /Alpha/ }));
    await waitFor(() => {
      expect(within(aside).getByRole('button', { name: '삭제' })).toBeEnabled();
    });
  });
});
