import React from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import { ThemeProvider } from '@mui/material';
import { appTheme } from '../../theme';
import UserManagementLegacy from './UserManagementLegacy';
import { getUsers } from '../../services/userService';
import { getUserPermissionHierarchy, listPermissionGroups } from '../../services/permissionGroupService';

jest.mock('../../services/userService', () => ({
  getUsers: jest.fn(),
  deleteUser: jest.fn(),
}));

jest.mock('../../services/permissionGroupService', () => ({
  getUserPermissionHierarchy: jest.fn(),
  listPermissionGroups: jest.fn(),
}));

jest.mock('../../utils/logger', () => ({
  error: jest.fn(),
}));

jest.mock('../UserGroupAssignment/UserGroupAssignment', () => () => null);
jest.mock('./ExternalProvisioning', () => () => null);

describe('UserManagementLegacy', () => {
  test('기존 사용자 관리 헤더/액션을 유지한다', async () => {
    getUserPermissionHierarchy.mockResolvedValue({ data: [] });
    getUsers.mockResolvedValue({ data: [] });
    listPermissionGroups.mockResolvedValue([]);

    render(
      <ThemeProvider theme={appTheme}>
        <UserManagementLegacy
          user={{
            isSystemAdmin: true,
            allowedScreenIds: ['user-management'],
            screenFunctions: { 'user-management': { write: true } },
          }}
        />
      </ThemeProvider>
    );

    await waitFor(() => {
      expect(screen.getByRole('heading', { name: '사용자 관리' })).toBeInTheDocument();
    });
    expect(screen.getByRole('button', { name: '사용자 추가' })).toBeInTheDocument();
    expect(screen.queryByText('사용자 관리 v2')).not.toBeInTheDocument();
  });
});
