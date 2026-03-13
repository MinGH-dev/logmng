import React from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import ActivityStatistics from './ActivityStatistics';
import { statisticsApi, logTypeApi } from '../services/api';
import {
  FILTER_OPTION_SCREEN_IDS,
  getDepartmentFilterOptions,
} from '../services/filterOptionsService';

jest.mock('./StatisticsHeader', () => () => <div data-testid="statistics-header" />);
jest.mock('./StatisticsView', () => () => <div data-testid="statistics-view" />);
jest.mock('./UserStatisticsTable', () => () => <div data-testid="user-statistics-table" />);
jest.mock('./StatisticsFilters', () => (props) => (
  <div
    data-testid="statistics-filters"
    data-departments={JSON.stringify(props.departmentList || [])}
    data-users={JSON.stringify(props.userList || [])}
    data-log-types={JSON.stringify(props.logTypeList || [])}
    data-self-scope={props.isSelfScope ? 'true' : 'false'}
    data-filters={JSON.stringify(props.filters || {})}
    data-self-context={JSON.stringify(props.selfContext || {})}
  />
));

jest.mock('../services/api', () => ({
  statisticsApi: {
    getUserList: jest.fn(),
    getIpList: jest.fn(),
  },
  logTypeApi: {
    getLogTypeList: jest.fn(),
  },
}));

jest.mock('../services/filterOptionsService', () => ({
  FILTER_OPTION_SCREEN_IDS: {
    ACTIVITY_LOG: 'activity-log',
    STATISTICS: 'statistics',
    SEARCH_HISTORY: 'search-history',
  },
  getDepartmentFilterOptions: jest.fn(),
}));

describe('ActivityStatistics', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    statisticsApi.getUserList.mockResolvedValue({ success: true, data: [] });
    statisticsApi.getIpList.mockResolvedValue({ success: true, data: [] });
    logTypeApi.getLogTypeList.mockResolvedValue({ success: true, data: [] });
    getDepartmentFilterOptions.mockResolvedValue({
      success: true,
      data: ['개발부', '운영부'],
    });
  });

  test('loads statistics department options from shared filter options API', async () => {
    render(
      <ActivityStatistics
        user={{
          isSystemAdmin: false,
          screenScopes: { statistics: 'team' },
        }}
      />,
    );

    await waitFor(() =>
      expect(getDepartmentFilterOptions).toHaveBeenCalledWith(
        FILTER_OPTION_SCREEN_IDS.STATISTICS,
      ),
    );

    const filters = await screen.findByTestId('statistics-filters');
    await waitFor(() => {
      expect(JSON.parse(filters.getAttribute('data-departments'))).toEqual(['개발부', '운영부']);
      expect(JSON.parse(filters.getAttribute('data-users'))).toEqual([]);
      expect(JSON.parse(filters.getAttribute('data-log-types'))).toEqual([]);
    });
  });

  test('self scope keeps locked self-context from auth payload and does not load editable options', async () => {
    render(
      <ActivityStatistics
        user={{
          isSystemAdmin: false,
          screenScopes: { statistics: 'self' },
          selfContext: {
            department: '개발부',
            username: '홍길동',
            userId: '10000001',
          },
        }}
      />,
    );

    await waitFor(() => expect(logTypeApi.getLogTypeList).toHaveBeenCalled());

    expect(getDepartmentFilterOptions).not.toHaveBeenCalled();
    expect(statisticsApi.getUserList).not.toHaveBeenCalled();
    expect(statisticsApi.getIpList).not.toHaveBeenCalled();

    const filters = await screen.findByTestId('statistics-filters');
    await waitFor(() => {
      expect(filters.getAttribute('data-self-scope')).toBe('true');
      expect(JSON.parse(filters.getAttribute('data-users'))).toEqual([]);
      expect(JSON.parse(filters.getAttribute('data-log-types'))).toEqual([]);
      expect(JSON.parse(filters.getAttribute('data-self-context'))).toEqual({
        department: '개발부',
        username: '홍길동',
        userId: '10000001',
      });
      expect(JSON.parse(filters.getAttribute('data-filters'))).toEqual({
        logType: '',
        department: '개발부',
        username: '홍길동',
        userId: '10000001',
        ip: '',
      });
    });
  });
});
