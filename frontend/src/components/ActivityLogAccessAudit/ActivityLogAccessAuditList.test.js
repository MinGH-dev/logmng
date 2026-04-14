import React from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import ActivityLogAccessAuditList from './ActivityLogAccessAuditList';
import { searchAccessAudit } from '../../services/userActivityLogService';

jest.mock('../../services/userActivityLogService', () => ({
  searchAccessAudit: jest.fn(),
}));

jest.mock('../../utils/logger', () => ({
  __esModule: true,
  default: { debug: jest.fn(), error: jest.fn(), info: jest.fn() },
}));

describe('ActivityLogAccessAuditList', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    global.fetch = jest.fn().mockResolvedValue({
      json: async () => ({
        success: true,
        data: { timestamp: '2026-04-14T10:00:00' },
      }),
    });
    searchAccessAudit.mockResolvedValue({
      success: true,
      data: {
        data: [],
        pagination: { currentPage: 1, totalPages: 1, totalCount: 0 },
      },
    });
  });

  test('renders title and loads audit list on mount', async () => {
    render(<ActivityLogAccessAuditList />);

    expect(screen.getByRole('heading', { name: '활동 로그 접근 감사' })).toBeInTheDocument();

    await waitFor(() => {
      expect(searchAccessAudit).toHaveBeenCalled();
    });
  });

  test('includes targetActivityLogId on first fetch and calls onConsumedInitialTarget once', async () => {
    const onConsumed = jest.fn();

    render(
      <ActivityLogAccessAuditList initialTargetActivityLogId={42} onConsumedInitialTarget={onConsumed} />,
    );

    await waitFor(() => {
      expect(searchAccessAudit).toHaveBeenCalledWith(
        expect.objectContaining({
          targetActivityLogId: 42,
          page: 1,
        }),
      );
    });

    await waitFor(() => {
      expect(onConsumed).toHaveBeenCalledTimes(1);
    });
  });
});
