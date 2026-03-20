import React from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import UserActivityLogList from './UserActivityLogList';
import { searchActivityLogs } from '../../services/userActivityLogService';
import {
  FILTER_OPTION_SCREEN_IDS,
  getDepartmentFilterOptions,
} from '../../services/filterOptionsService';

jest.mock('./UserActivityLogTable', () => () => <div data-testid="activity-log-table" />);
jest.mock('./UserActivityLogDetail', () => () => null);

jest.mock('../../services/userActivityLogService', () => ({
  searchActivityLogs: jest.fn(),
}));

jest.mock('../../services/filterOptionsService', () => ({
  FILTER_OPTION_SCREEN_IDS: {
    ACTIVITY_LOG: 'activity-log',
    STATISTICS: 'statistics',
    SEARCH_HISTORY: 'search-history',
  },
  getDepartmentFilterOptions: jest.fn(),
}));

jest.mock('../../utils/logger', () => ({
  debug: jest.fn(),
  info: jest.fn(),
  error: jest.fn(),
}));

describe('UserActivityLogList', () => {
  const createUser = (scope) => ({
    isSystemAdmin: false,
    screenScopes: { 'activity-log': scope },
    selfContext: {
      department: '개발부',
      username: '홍길동',
      userId: 20260001,
    },
  });

  const expectSanitizedSelfRequest = (request) => {
    expect(request).toEqual(expect.objectContaining({
      startDate: '2026-03-13 00:00:00',
      endDate: '2026-03-13 23:59:59',
      page: 1,
      pageSize: 20,
    }));
    expect(request).not.toHaveProperty('department');
    expect(request).not.toHaveProperty('username');
    expect(request).not.toHaveProperty('userId');
    expect(request).not.toHaveProperty('ipAddress');
  };

  beforeEach(() => {
    jest.clearAllMocks();
    global.fetch = jest.fn().mockResolvedValue({
      json: async () => ({
        success: true,
        data: { timestamp: '2026-03-13T10:00:00' },
      }),
    });
    getDepartmentFilterOptions.mockResolvedValue({
      success: true,
      data: ['개발부', '운영부'],
    });
    searchActivityLogs.mockResolvedValue({
      success: true,
      data: {
        data: [],
        pagination: { totalPages: 1, totalCount: 0 },
      },
    });
  });

  afterEach(() => {
    jest.resetAllMocks();
  });

  test('self scope shows locked self-context and still sanitizes the initial request', async () => {
    render(
      <UserActivityLogList
        user={createUser('self')}
      />,
    );

    await waitFor(() => expect(searchActivityLogs).toHaveBeenCalled());

    expect(getDepartmentFilterOptions).not.toHaveBeenCalled();
    expect(screen.getByDisplayValue('개발부')).toHaveAttribute('readonly');
    expect(screen.getByDisplayValue('홍길동')).toHaveAttribute('readonly');
    expect(screen.getByDisplayValue('20260001')).toHaveAttribute('readonly');
    // req 20260316: 기타 조건(액션 타입, IP 주소) visible when scope=self
    expect(screen.getByLabelText('액션 타입')).toBeInTheDocument();
    expect(screen.getByLabelText('IP 주소')).toBeInTheDocument();

    expectSanitizedSelfRequest(searchActivityLogs.mock.calls[0][0]);
  });

  test.each(['team', 'all'])('%s scope shows user filters and loads department options', async (scope) => {
    render(
      <UserActivityLogList
        user={createUser(scope)}
      />,
    );

    await waitFor(() =>
      expect(getDepartmentFilterOptions).toHaveBeenCalledWith(
        FILTER_OPTION_SCREEN_IDS.ACTIVITY_LOG,
      ),
    );

    expect(await screen.findByLabelText('부서')).toBeInTheDocument();
    expect(screen.getByLabelText(/사용자명/)).toBeInTheDocument();
    expect(screen.getByLabelText(/사용자 ID/)).toBeInTheDocument();
    expect(screen.getByLabelText('액션 타입')).toBeInTheDocument();
    expect(screen.getByLabelText('IP 주소')).toBeInTheDocument();
  });

  test('team scope keeps submitted filters in the outgoing request', async () => {
    render(
      <UserActivityLogList
        user={createUser('team')}
      />,
    );

    await waitFor(() =>
      expect(getDepartmentFilterOptions).toHaveBeenCalledWith(
        FILTER_OPTION_SCREEN_IDS.ACTIVITY_LOG,
      ),
    );

    expect(await screen.findByRole('option', { name: '개발부' })).toBeInTheDocument();
    await userEvent.selectOptions(screen.getByLabelText('부서'), '개발부');
    await userEvent.type(screen.getByLabelText(/사용자명/), 'user2');
    await userEvent.type(screen.getByLabelText(/사용자 ID/), '12345678');
    await userEvent.selectOptions(screen.getByLabelText('액션 타입'), 'LOGIN');
    await userEvent.type(screen.getByLabelText('IP 주소'), '10.0.0.7');
    await userEvent.click(screen.getByRole('button', { name: '검색' }));

    await waitFor(() =>
      expect(searchActivityLogs).toHaveBeenLastCalledWith(expect.objectContaining({
        startDate: '2026-03-13 00:00:00',
        endDate: '2026-03-13 23:59:59',
        department: '개발부',
        username: 'user2',
        userId: 12345678,
        actionType: 'LOGIN',
        ipAddress: '10.0.0.7',
        page: 1,
        pageSize: 20,
      })),
    );
  });

  test('switching from all scope to self shows locked values and re-sanitizes search requests', async () => {
    const { rerender } = render(
      <UserActivityLogList
        user={createUser('all')}
      />,
    );

    await waitFor(() =>
      expect(getDepartmentFilterOptions).toHaveBeenCalledWith(
        FILTER_OPTION_SCREEN_IDS.ACTIVITY_LOG,
      ),
    );

    expect(await screen.findByRole('option', { name: '운영부' })).toBeInTheDocument();
    await userEvent.selectOptions(screen.getByLabelText('부서'), '운영부');
    await userEvent.type(screen.getByLabelText(/사용자명/), 'other');
    await userEvent.type(screen.getByLabelText(/사용자 ID/), '87654321');
    await userEvent.type(screen.getByLabelText('IP 주소'), '192.168.0.10');
    await userEvent.click(screen.getByRole('button', { name: '검색' }));

    await waitFor(() =>
      expect(searchActivityLogs).toHaveBeenLastCalledWith(expect.objectContaining({
        department: '운영부',
        username: 'other',
        userId: 87654321,
        ipAddress: '192.168.0.10',
      })),
    );

    rerender(
      <UserActivityLogList
        user={createUser('self')}
      />,
    );

    await waitFor(() => {
      expect(screen.getByDisplayValue('개발부')).toHaveAttribute('readonly');
      expect(screen.getByDisplayValue('홍길동')).toHaveAttribute('readonly');
      expect(screen.getByDisplayValue('20260001')).toHaveAttribute('readonly');
      // req 20260316: 기타 조건 visible when scope=self
      expect(screen.getByLabelText('IP 주소')).toBeInTheDocument();
    });

    await userEvent.click(screen.getByRole('button', { name: '검색' }));

    await waitFor(() => expect(searchActivityLogs).toHaveBeenCalledTimes(3));
    expectSanitizedSelfRequest(searchActivityLogs.mock.calls[2][0]);
  });
});
