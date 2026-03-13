import React from 'react';
import { render, screen, waitFor, within } from '@testing-library/react';
import PendingApprovals from './PendingApprovals';
import {
  getPendingList,
  approveSearchHistory,
  rejectSearchHistory,
} from '../../services/searchHistoryService';

jest.mock('../../services/searchHistoryService', () => ({
  getPendingList: jest.fn(),
  approveSearchHistory: jest.fn(),
  rejectSearchHistory: jest.fn(),
}));

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
    getPendingList.mockResolvedValue({
      data: {
        data: [
          {
            id: 101,
            requester: '10000001',
            searchParamsSummary: '요청자=10000001',
            requestedAt: '2026-03-13 10:00:00',
          },
        ],
        pagination: {
          currentPage: 1,
          totalPages: 3,
          totalCount: 42,
        },
      },
    });
  });

  test('TC-03: one-page pending approvals keeps the shared footer visible', async () => {
    getPendingList.mockResolvedValueOnce({
      data: {
        data: [
          {
            id: 101,
            requester: '10000001',
            searchParamsSummary: '요청자=10000001',
            requestedAt: '2026-03-13 10:00:00',
          },
        ],
        pagination: {
          currentPage: 1,
          totalPages: 1,
          totalCount: 5,
        },
      },
    });

    const { container } = render(<PendingApprovals user={baseUser} />);

    await waitFor(() => expect(getPendingList).toHaveBeenCalledWith(1, 20));
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

  test('TC-06: multi-page pending approvals uses the shared pagination region without a screen-local pagination block', async () => {
    const { container } = render(<PendingApprovals user={baseUser} />);

    await waitFor(() => expect(getPendingList).toHaveBeenCalledWith(1, 20));
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
});
