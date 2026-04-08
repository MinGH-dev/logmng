import React from 'react';
import { render, screen, waitFor, within, fireEvent } from '@testing-library/react';
import PendingApprovals from './PendingApprovals';
import {
  getSearchHistoryList,
  getSearchHistoryDetail,
  approveSearchHistory,
  rejectSearchHistory,
} from '../../services/searchHistoryService';
import { getDepartmentFilterOptions } from '../../services/filterOptionsService';

jest.mock('../../services/searchHistoryService', () => ({
  getSearchHistoryList: jest.fn(),
  getSearchHistoryDetail: jest.fn(),
  approveSearchHistory: jest.fn(),
  rejectSearchHistory: jest.fn(),
}));

jest.mock('../../services/filterOptionsService', () => ({
  FILTER_OPTION_SCREEN_IDS: { PENDING_APPROVALS: 'pending-approvals' },
  getDepartmentFilterOptions: jest.fn(),
}));

const sampleRow = {
  id: 101,
  seq: 1,
  userId: 1,
  requesterUsername: 'u1',
  requesterDisplayName: '테스트',
  requesterDepartmentName: '개발부',
  searchParamsSummary: '요청자=10000001',
  requestedAt: '2026-03-13 10:00:00',
  approvalStatus: 'PENDING',
};

const baseUser = {
  isSystemAdmin: false,
  screenScopes: {
    'pending-approvals': 'team',
  },
  screenFunctions: {
    'pending-approvals': {
      read: true,
      approve: true,
    },
  },
};

describe('PendingApprovals', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    approveSearchHistory.mockResolvedValue({ success: true });
    rejectSearchHistory.mockResolvedValue({ success: true });
    getDepartmentFilterOptions.mockResolvedValue({ success: true, data: ['개발부'] });
    getSearchHistoryList.mockResolvedValue({
      success: true,
      data: {
        data: [sampleRow],
        pagination: {
          currentPage: 1,
          totalPages: 3,
          totalCount: 42,
        },
      },
    });
  });

  test('TC-03: one-page list keeps the shared footer visible', async () => {
    getSearchHistoryList.mockResolvedValueOnce({
      success: true,
      data: {
        data: [sampleRow],
        pagination: {
          currentPage: 1,
          totalPages: 1,
          totalCount: 5,
        },
      },
    });

    const { container } = render(<PendingApprovals user={baseUser} />);

    await waitFor(() => expect(getSearchHistoryList).toHaveBeenCalled());
    expect(getSearchHistoryList.mock.calls[0][0]).toMatchObject({
      listContext: 'pending-approvals',
    });
    await screen.findByText('요청자=10000001');

    const tableContainer = container.querySelector('.pending-approvals .log-table-container');
    const pagination = tableContainer.querySelector(':scope > .pagination');

    expect(pagination).not.toBeNull();
    expect(screen.getByRole('navigation', { name: '테이블 푸터' })).toBeInTheDocument();
    expect(within(pagination).getByText('총 5건')).toBeInTheDocument();
    expect(within(pagination).getByText('표시 건수')).toBeInTheDocument();
    expect(within(pagination).queryByRole('button', { name: '다음 페이지' })).not.toBeInTheDocument();
    expect(container.querySelector('.pending-approvals-pagination')).toBeNull();
  });

  test('TC-06: multi-page uses shared pagination region', async () => {
    const { container } = render(<PendingApprovals user={baseUser} />);

    await waitFor(() => expect(getSearchHistoryList).toHaveBeenCalled());
    await screen.findByText('요청자=10000001');

    const tableContainer = container.querySelector('.pending-approvals .log-table-container');
    expect(tableContainer).not.toBeNull();

    const pagination = tableContainer.querySelector(':scope > .pagination');
    expect(pagination).not.toBeNull();
    expect(screen.getByRole('navigation', { name: '테이블 푸터' })).toBeInTheDocument();
    expect(within(pagination).getByText('총 42건')).toBeInTheDocument();
    expect(within(pagination).getByRole('button', { name: '다음 페이지' })).toBeInTheDocument();
    expect(container.querySelector('.pending-approvals-pagination')).toBeNull();
  });

  test('TC-FE-01: requester (approve false) has no 승인/반려 buttons', async () => {
    const requesterUser = {
      ...baseUser,
      screenFunctions: {
        'pending-approvals': { read: true, approve: false },
      },
    };
    render(<PendingApprovals user={requesterUser} />);

    await waitFor(() => expect(getSearchHistoryList).toHaveBeenCalled());
    await screen.findByText('승인대기');

    expect(screen.queryByRole('button', { name: /승인, 요청 ID/ })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /반려, 요청 ID/ })).not.toBeInTheDocument();
  });

  test('TC-FE-02: approver sees 승인/반려 on PENDING row', async () => {
    render(<PendingApprovals user={baseUser} />);

    await waitFor(() => expect(getSearchHistoryList).toHaveBeenCalled());
    await screen.findByText('승인대기');

    expect(screen.getByRole('button', { name: '승인, 요청 ID 101' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '반려, 요청 ID 101' })).toBeInTheDocument();
  });

  test('TC-03: status mapping edge case (snake/lowercase) still shows 승인/반려 for approver', async () => {
    const originalFlag = process.env.REACT_APP_PA_DEBUG_VISIBILITY;
    process.env.REACT_APP_PA_DEBUG_VISIBILITY = 'true';
    getSearchHistoryList.mockResolvedValueOnce({
      success: true,
      data: {
        data: [{ ...sampleRow, approvalStatus: undefined, approval_status: 'pending' }],
        pagination: { currentPage: 1, totalPages: 1, totalCount: 1 },
      },
    });

    render(<PendingApprovals user={baseUser} />);

    await waitFor(() => expect(getSearchHistoryList).toHaveBeenCalled());
    await screen.findByText('승인대기');
    expect(screen.getByRole('button', { name: '승인, 요청 ID 101' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '반려, 요청 ID 101' })).toBeInTheDocument();

    process.env.REACT_APP_PA_DEBUG_VISIBILITY = originalFlag;
  });

  test('TC-03-2: status mapping trims whitespace and keeps action eligibility', async () => {
    getSearchHistoryList.mockResolvedValueOnce({
      success: true,
      data: {
        data: [{ ...sampleRow, approvalStatus: ' pending ' }],
        pagination: { currentPage: 1, totalPages: 1, totalCount: 1 },
      },
    });

    render(<PendingApprovals user={baseUser} />);

    await waitFor(() => expect(getSearchHistoryList).toHaveBeenCalled());
    await screen.findByText('승인대기');
    expect(screen.getByRole('button', { name: '승인, 요청 ID 101' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '반려, 요청 ID 101' })).toBeInTheDocument();
  });

  test('TC-FE-03: after approve, list is refreshed', async () => {
    render(<PendingApprovals user={baseUser} />);

    await waitFor(() => expect(getSearchHistoryList).toHaveBeenCalled());
    await screen.findByRole('button', { name: '승인, 요청 ID 101' });

    const initialCalls = getSearchHistoryList.mock.calls.length;
    fireEvent.click(screen.getByRole('button', { name: '승인, 요청 ID 101' }));

    await waitFor(() => expect(approveSearchHistory).toHaveBeenCalledWith(101));
    await waitFor(() => expect(getSearchHistoryList.mock.calls.length).toBeGreaterThan(initialCalls));
  });

  test('list + detail use listContext=pending-approvals', async () => {
    getSearchHistoryDetail.mockResolvedValue({
      success: true,
      data: { id: 101, searchParams: { logType: 'java_fw_imglog' }, logType: { id: 'java_fw_imglog' } },
    });

    render(<PendingApprovals user={baseUser} />);

    await waitFor(() => expect(getSearchHistoryList).toHaveBeenCalled());
    await screen.findByRole('button', { name: '상세, ID 101' });

    fireEvent.click(screen.getByRole('button', { name: '상세, ID 101' }));

    await waitFor(() => expect(getSearchHistoryDetail).toHaveBeenCalledWith(101, { listContext: 'pending-approvals' }));
  });

  test('검색 submits filters including approvalStatuses and date range', async () => {
    const { container } = render(<PendingApprovals user={baseUser} />);

    await waitFor(() => expect(getDepartmentFilterOptions).toHaveBeenCalledWith('pending-approvals'));

    await waitFor(() => expect(getSearchHistoryList.mock.calls.length).toBeGreaterThan(0));
    const callsAfterMount = getSearchHistoryList.mock.calls.length;

    const fromInput = container.querySelector('#pending-approvals-requested-at-from');
    const toInput = container.querySelector('#pending-approvals-requested-at-to');
    fireEvent.change(fromInput, { target: { value: '2026-01-01T00:00' } });
    fireEvent.change(toInput, { target: { value: '2026-01-31T23:59' } });

    fireEvent.click(screen.getByRole('button', { name: '검색' }));

    await waitFor(() => expect(getSearchHistoryList.mock.calls.length).toBeGreaterThan(callsAfterMount));
    const lastCall = getSearchHistoryList.mock.calls[getSearchHistoryList.mock.calls.length - 1][0];
    expect(lastCall.listContext).toBe('pending-approvals');
    expect(lastCall.requestedAtFrom).toMatch(/^2026-01-01/);
    expect(lastCall.requestedAtTo).toMatch(/^2026-01-31/);
  });
});
