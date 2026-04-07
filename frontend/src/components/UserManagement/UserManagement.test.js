import React from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ThemeProvider } from '@mui/material';
import UserManagement from './UserManagement';
import { getUsers, deleteUser } from '../../services/userService';
import { getUserPermissionHierarchy, listPermissionGroups } from '../../services/permissionGroupService';
import { appTheme } from '../../theme';

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
jest.mock('../UserGroupAssignment/UserGroupAssignment', () => ({ userId, userGroups }) => (
  <div data-testid={`user-group-${userId}`} data-groups={JSON.stringify((userGroups || []).map((g) => g?.name || g?.id || g))} />
));

function renderUserManagement(user) {
  return render(
    <ThemeProvider theme={appTheme}>
      <UserManagement user={user} />
    </ThemeProvider>
  );
}

describe('UserManagement', () => {
  const adminUser = {
    isSystemAdmin: true,
    allowedScreenIds: ['user-permission-hierarchy'],
    screenFunctions: {
      'user-permission-hierarchy': { write: true },
    },
  };

  beforeEach(() => {
    jest.clearAllMocks();
    getUsers.mockResolvedValue({ data: [{ userId: 20260001, isApprover: false }] });
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
              { userId: 20260001, userName: '홍길동', rank: '대리', position: '개발', permissionGroups: [] },
              { userId: 20260002, userName: null, rank: '-', position: '-', permissionGroups: [] },
            ],
          },
        ],
      });

      renderUserManagement(adminUser);

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
      expect(screen.getByText('20260001')).toBeInTheDocument();
      expect(screen.getAllByText('20260002').length).toBeGreaterThanOrEqual(1);
    });
  });

  describe('사용자 ID column', () => {
    test('prefers employeeNumber over app userId when present', async () => {
      getUserPermissionHierarchy.mockResolvedValue({
        data: [
          {
            code: 'D1',
            name: '인사연동팀',
            children: [],
            users: [
              {
                userId: 20270001,
                employeeNumber: '20261001',
                userName: '김직원',
                rank: '-',
                position: '-',
                permissionGroups: [],
              },
            ],
          },
        ],
      });

      renderUserManagement(adminUser);

      await waitFor(() => {
        expect(screen.getByText('[D1] 인사연동팀')).toBeInTheDocument();
      });

      await userEvent.click(screen.getByRole('button', { name: /펼치기/ }));

      await waitFor(() => {
        expect(screen.getByText('김직원')).toBeInTheDocument();
        expect(screen.getByText('20261001')).toBeInTheDocument();
      });

      expect(screen.getByTestId('user-group-20270001')).toBeInTheDocument();
    });

    test('falls back to employee_number (snake_case) when employeeNumber absent', async () => {
      getUserPermissionHierarchy.mockResolvedValue({
        data: [
          {
            code: 'D2',
            name: '스네이크팀',
            children: [],
            users: [
              {
                userId: 99,
                employee_number: 'EMP-SN-1',
                userName: '박직원',
                rank: '-',
                position: '-',
                permissionGroups: [],
              },
            ],
          },
        ],
      });

      renderUserManagement(adminUser);

      await waitFor(() => {
        expect(screen.getByText('[D2] 스네이크팀')).toBeInTheDocument();
      });

      await userEvent.click(screen.getByRole('button', { name: /펼치기/ }));

      await waitFor(() => {
        expect(screen.getByText('EMP-SN-1')).toBeInTheDocument();
      });
    });
  });

  describe('TC-09: 사용자 삭제 다이얼로그', () => {
    test('사유 없이 삭제할 수 없고, 사유 입력 후 성공 시 API 호출 및 목록 갱신', async () => {
      deleteUser.mockResolvedValue({ success: true, data: null });
      getUserPermissionHierarchy.mockResolvedValue({
        data: [
          {
            code: 'D1',
            name: '팀',
            children: [],
            users: [
              {
                userId: 20260001,
                userName: '삭제대상',
                employeeNumber: 'E99',
                rank: '-',
                position: '-',
                permissionGroups: [],
              },
            ],
          },
        ],
      });

      renderUserManagement(adminUser);

      await waitFor(() => {
        expect(screen.getByText('[D1] 팀')).toBeInTheDocument();
      });

      await userEvent.click(screen.getByRole('button', { name: /펼치기/ }));

      await waitFor(() => {
        expect(screen.getByRole('button', { name: /삭제대상 사용자 삭제/ })).toBeInTheDocument();
      });

      await userEvent.click(screen.getByRole('button', { name: /삭제대상 사용자 삭제/ }));

      await waitFor(() => {
        expect(screen.getByRole('dialog', { name: '사용자 삭제' })).toBeInTheDocument();
      });

      await userEvent.click(screen.getByRole('button', { name: '삭제' }));

      await waitFor(() => {
        expect(screen.getByText('삭제 사유를 입력하세요.')).toBeInTheDocument();
      });

      expect(deleteUser).not.toHaveBeenCalled();

      const reasonInput = screen.getByLabelText(/삭제 사유/);
      await userEvent.type(reasonInput, '  퇴사 처리  ');

      await userEvent.click(screen.getByRole('button', { name: '삭제' }));

      await waitFor(() => {
        expect(deleteUser).toHaveBeenCalledWith(20260001, { changeReason: '퇴사 처리' });
      });

      await waitFor(() => {
        expect(screen.queryByRole('dialog', { name: '사용자 삭제' })).not.toBeInTheDocument();
      });

      expect(getUserPermissionHierarchy.mock.calls.length).toBeGreaterThanOrEqual(2);
    });
  });

  describe('사용자 추가 modal', () => {
    test('opens dialog with org provisioning flow when primary action is clicked', async () => {
      getUserPermissionHierarchy.mockResolvedValue({
        data: [{ code: 'D1', name: '팀', children: [], users: [] }],
      });

      renderUserManagement(adminUser);

      await waitFor(() => {
        expect(screen.getByText('[D1] 팀')).toBeInTheDocument();
      });

      expect(screen.queryByRole('dialog')).not.toBeInTheDocument();

      await userEvent.click(screen.getByRole('button', { name: '사용자 추가' }));

      await waitFor(() => {
        expect(screen.getByRole('dialog')).toBeInTheDocument();
      });
      expect(screen.getByText('인사정보에서 사용자 등록')).toBeInTheDocument();
      expect(screen.getByRole('button', { name: '취소' })).toBeInTheDocument();
      expect(screen.getByLabelText('직원명')).toBeInTheDocument();
    });
  });
});
