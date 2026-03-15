import React from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import UserManagement from './UserManagement';
import { getUsers } from '../../services/userService';
import { getUserPermissionHierarchy, listPermissionGroups } from '../../services/permissionGroupService';

jest.mock('../../services/userService', () => ({
  getUsers: jest.fn(),
}));
jest.mock('../../services/permissionGroupService', () => ({
  getUserPermissionHierarchy: jest.fn(),
  listPermissionGroups: jest.fn(),
}));
jest.mock('../../utils/logger', () => ({
  error: jest.fn(),
}));
jest.mock('../UserGroupAssignment/UserGroupAssignment', () => ({ userId, userGroups }) => (
  <div data-testid={`user-group-${userId}`} data-groups={JSON.stringify((userGroups || []).map((g) => g?.name || g?.id || g))} />
));

describe('UserManagement', () => {
  const adminUser = {
    isSystemAdmin: true,
    allowedScreenIds: ['user-permission-hierarchy'],
  };

  beforeEach(() => {
    jest.clearAllMocks();
    getUsers.mockResolvedValue({ data: [{ userId: 'user1', isApprover: false }] });
    listPermissionGroups.mockResolvedValue([]);
  });

  describe('TC-06: User management table has 사용자명 column', () => {
    test('table includes "사용자명" column and shows userName or userId per row', async () => {
      getUserPermissionHierarchy.mockResolvedValue({
        data: [
          {
            code: 'D1',
            name: '개발1팀',
            children: [],
            users: [
              { userId: 'user1', userName: '홍길동', rank: '대리', position: '개발', permissionGroups: [] },
              { userId: 'user2', userName: null, rank: '-', position: '-', permissionGroups: [] },
            ],
          },
        ],
      });

      render(<UserManagement user={adminUser} />);

      await waitFor(() => {
        expect(screen.getByText('[D1] 개발1팀')).toBeInTheDocument();
      });

      const expandButton = screen.getByRole('button', { name: /펼치기/ });
      await userEvent.click(expandButton);

      await waitFor(() => {
        expect(screen.getByRole('columnheader', { name: '사용자명' })).toBeInTheDocument();
        expect(screen.getByRole('columnheader', { name: '사용자 ID' })).toBeInTheDocument();
      });

      expect(screen.getByText('홍길동')).toBeInTheDocument();
      expect(screen.getByText('user1')).toBeInTheDocument();
      expect(screen.getAllByText('user2').length).toBeGreaterThanOrEqual(1);
    });
  });
});
