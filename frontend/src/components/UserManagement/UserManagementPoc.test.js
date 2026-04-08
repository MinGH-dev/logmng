import React from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import UserManagementPoc from './UserManagementPoc';
import * as pocUserManagementService from '../../services/pocUserManagementService';
import * as hrSyncPocService from '../../services/hrSyncPocService';

jest.mock('../../services/pocUserManagementService', () => ({
  fetchReplicaDepartmentTree: jest.fn(),
  fetchReplicaUsers: jest.fn(),
  postMigratePreview: jest.fn(),
  isPocUserMgmtDisabled: jest.fn(() => false),
  isPocUserMgmtUnauthorized: jest.fn(() => false),
}));

jest.mock('../../services/hrSyncPocService', () => ({
  fetchSnapshots: jest.fn(),
}));

describe('UserManagementPoc', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    window.alert = jest.fn();
    pocUserManagementService.fetchReplicaDepartmentTree.mockResolvedValue({
      success: true,
      data: {
        roots: [
          {
            departmentKey: 'D1',
            name: '본부',
            children: [{ departmentKey: 'D2', name: '팀', children: [] }],
          },
        ],
      },
    });
    hrSyncPocService.fetchSnapshots.mockResolvedValue({
      success: true,
      data: { snapshots: [{ snapshotId: 'snap-1', label: '테스트 스냅샷' }] },
    });
    pocUserManagementService.fetchReplicaUsers.mockResolvedValue({
      success: true,
      data: {
        employees: [
          {
            displayName: '홍길동',
            jobTitle: '대리',
            departmentKey: 'D2',
            departmentName: '팀',
            isActive: true,
            employeeNumber: 'E001',
          },
        ],
        pagination: { currentPage: 1, totalPages: 1, totalCount: 1 },
      },
    });
    pocUserManagementService.postMigratePreview.mockResolvedValue({
      success: true,
      data: { persisted: false, messageCode: 'POC_ACTION_NOT_PERSISTED' },
    });
  });

  test('renders PoC title and loads replica tree (smoke)', async () => {
    render(<UserManagementPoc />);
    expect(screen.getByTestId('user-management-poc-root')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: /사용자 관리 v2 \(PoC\)/i })).toBeInTheDocument();
    await waitFor(() => {
      expect(pocUserManagementService.fetchReplicaDepartmentTree).toHaveBeenCalled();
    });
    await screen.findByRole('tree', { name: /복제 부서 트리/i });
  });

  test('selecting a department loads replica users', async () => {
    pocUserManagementService.fetchReplicaDepartmentTree.mockResolvedValue({
      success: true,
      data: { roots: [{ departmentKey: 'D2', name: '팀', children: [] }] },
    });
    render(<UserManagementPoc />);
    const teamBtn = await screen.findByRole('button', { name: '팀' });
    await userEvent.click(teamBtn);
    await waitFor(() => {
      expect(pocUserManagementService.fetchReplicaUsers).toHaveBeenCalledWith(
        expect.objectContaining({ departmentKey: 'D2', sourceSystem: 'HR_SAMPLE' })
      );
    });
    expect(await screen.findByText('홍길동')).toBeInTheDocument();
  });

  test('migrate preview stub shows alert with messageCode', async () => {
    render(<UserManagementPoc />);
    await screen.findByRole('tree', { name: /복제 부서 트리/i });
    const btn = screen.getByRole('button', { name: /마이그레이션 미리보기 \(PoC\)/i });
    await userEvent.click(btn);
    await waitFor(() => {
      expect(pocUserManagementService.postMigratePreview).toHaveBeenCalled();
    });
    expect(window.alert).toHaveBeenCalledWith(
      expect.stringContaining('POC_ACTION_NOT_PERSISTED')
    );
  });
});
