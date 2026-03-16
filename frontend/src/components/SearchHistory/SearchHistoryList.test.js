import React from 'react';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import SearchHistoryList from './SearchHistoryList';
import {
  getSearchHistoryList,
  getSearchHistoryDetail,
  reRequestSearchHistory,
} from '../../services/searchHistoryService';
import {
  FILTER_OPTION_SCREEN_IDS,
  getDepartmentFilterOptions,
} from '../../services/filterOptionsService';

jest.mock('../../services/searchHistoryService', () => ({
  getSearchHistoryList: jest.fn(),
  getSearchHistoryDetail: jest.fn(),
  reRequestSearchHistory: jest.fn(),
}));

jest.mock('../../services/filterOptionsService', () => ({
  FILTER_OPTION_SCREEN_IDS: {
    ACTIVITY_LOG: 'activity-log',
    STATISTICS: 'statistics',
    SEARCH_HISTORY: 'search-history',
  },
  getDepartmentFilterOptions: jest.fn(),
}));

const baseUser = {
  username: '10000001',
  isSystemAdmin: false,
  screenScopes: {
    'search-history': 'team',
  },
  selfContext: {
    department: '개발부',
    username: '홍길동',
    userId: 20260001,
  },
};

const listResponse = {
  success: true,
  data: {
    data: [
      {
        id: 1,
        seq: 1,
        requestedAt: '2026-03-13 10:00:00',
        requested_at: '2026-03-13 10:00:00',
        searchParamsSummary: '요청자=20260001',
        approvalStatus: 'PENDING',
        expiresAt: '2026-03-20 10:00:00',
        userId: 20260001,
        isExpired: false,
      },
    ],
    pagination: {
      currentPage: 1,
      totalPages: 3,
      totalCount: 42,
    },
  },
};

const onePageListResponse = {
  success: true,
  data: {
    data: listResponse.data.data,
    pagination: {
      currentPage: 1,
      totalPages: 1,
      totalCount: 7,
    },
  },
};

const renderAndWaitForInitialLoad = async (ui) => {
  const view = render(ui);

  await waitFor(() => expect(getSearchHistoryList).toHaveBeenCalledTimes(1));
  await screen.findByText('요청자=20260001');
  await waitFor(() => expect(screen.getByRole('button', { name: '검색' })).toBeEnabled());
  await waitFor(() => expect(screen.queryByText('데이터를 불러오는 중...')).not.toBeInTheDocument());

  return view;
};

describe('SearchHistoryList', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    getDepartmentFilterOptions.mockResolvedValue({
      success: true,
      data: ['개발부', '운영부'],
    });
    getSearchHistoryList.mockResolvedValue(listResponse);
    getSearchHistoryDetail.mockResolvedValue({ success: true, data: {} });
    reRequestSearchHistory.mockResolvedValue({ success: true });
  });

  test('TC-07: non-self scope shows standard requester toolbar with compact shared classes', async () => {
    const { container } = await renderAndWaitForInitialLoad(<SearchHistoryList user={baseUser} />);

    expect(getDepartmentFilterOptions).toHaveBeenCalledTimes(1);
    expect(getDepartmentFilterOptions).toHaveBeenCalledWith(
      FILTER_OPTION_SCREEN_IDS.SEARCH_HISTORY,
    );

    const toolbar = container.querySelector('.search-history-toolbar.sf-compact-panel');
    expect(toolbar).not.toBeNull();

    const requesterBlock = within(toolbar).getByText('요청자');
    expect(requesterBlock).toBeInTheDocument();

    const requesterFieldset = container.querySelector('.user-context-filter-block--compact');
    expect(requesterFieldset).not.toBeNull();

    const labelTexts = Array.from(
      container.querySelectorAll('.user-context-filter-block .form-group label'),
    ).map((node) => node.textContent);
    expect(labelTexts).toEqual(['부서', '사용자명 (최대 5자)', '사용자 ID (8자리)']);

    expect(screen.getByRole('option', { name: '전체' })).toBeInTheDocument();
    expect(screen.getByRole('option', { name: '개발부' })).toBeInTheDocument();
    expect(screen.getByRole('option', { name: '운영부' })).toBeInTheDocument();

    expect(within(toolbar).getByRole('button', { name: '검색' })).toBeInTheDocument();
    expect(within(toolbar).getByRole('button', { name: '초기화' })).toBeInTheDocument();
  });

  test('TC-09: explicit search and page-size change keep applied filters and reset page to 1', async () => {
    await renderAndWaitForInitialLoad(<SearchHistoryList user={baseUser} />);

    await userEvent.selectOptions(screen.getByLabelText('부서'), '개발부');
    await userEvent.type(screen.getByLabelText(/사용자명/), '홍길');
    await userEvent.type(screen.getByLabelText(/사용자 ID/), '12345678');

    expect(getSearchHistoryList).toHaveBeenCalledTimes(1);

    await userEvent.click(screen.getByRole('button', { name: '검색' }));

    await waitFor(() => expect(getSearchHistoryList).toHaveBeenCalledTimes(2));
    await waitFor(() => expect(screen.queryByText('데이터를 불러오는 중...')).not.toBeInTheDocument());
    expect(getSearchHistoryList).toHaveBeenNthCalledWith(2, {
      page: 1,
      pageSize: 20,
      sortField: 'requested_at',
      sortDirection: 'desc',
      department: '개발부',
      username: '홍길',
      userId: 12345678,
    });

    await userEvent.click(screen.getByRole('button', { name: '행 수 증가' }));

    await waitFor(() => expect(getSearchHistoryList).toHaveBeenCalledTimes(3));
    await waitFor(() => expect(screen.queryByText('데이터를 불러오는 중...')).not.toBeInTheDocument());
    expect(getSearchHistoryList).toHaveBeenNthCalledWith(3, {
      page: 1,
      pageSize: 21,
      sortField: 'requested_at',
      sortDirection: 'desc',
      department: '개발부',
      username: '홍길',
      userId: 12345678,
    });
  });

  test('TC-02: one-page search history keeps the shared footer visible', async () => {
    getSearchHistoryList.mockResolvedValueOnce(onePageListResponse);

    const { container } = await renderAndWaitForInitialLoad(<SearchHistoryList user={baseUser} />);

    const tableContainer = container.querySelector('.search-history-list .log-table-container');
    expect(tableContainer).not.toBeNull();

    const pagination = tableContainer.querySelector(':scope > .pagination');
    expect(pagination).not.toBeNull();
    expect(screen.getByRole('navigation', { name: '테이블 푸터' })).toBeInTheDocument();
    expect(within(pagination).getByText('총 7건')).toBeInTheDocument();
    expect(within(pagination).getByText('표시 건수')).toBeInTheDocument();
    expect(within(pagination).queryByRole('button', { name: '다음 페이지' })).not.toBeInTheDocument();
    expect(container.querySelector('.search-history-pagination')).toBeNull();
  });

  test('TC-06: multi-page search history renders shared pagination region inside the shared table container', async () => {
    const { container } = await renderAndWaitForInitialLoad(<SearchHistoryList user={baseUser} />);

    const tableContainer = container.querySelector('.search-history-list .log-table-container');
    expect(tableContainer).not.toBeNull();

    const pagination = tableContainer.querySelector(':scope > .pagination');
    expect(pagination).not.toBeNull();
    expect(tableContainer.querySelector(':scope > .table-wrapper')).not.toBeNull();
    expect(screen.getByRole('navigation', { name: '테이블 푸터' })).toBeInTheDocument();
    expect(within(pagination).getByText('총 42건')).toBeInTheDocument();
    expect(within(pagination).getByRole('button', { name: '다음 페이지' })).toBeInTheDocument();
    expect(container.querySelector('.search-history-pagination')).toBeNull();
  });

  test('TC-10: switching to self scope shows locked requester UI and omits requester params', async () => {
    const { rerender } = await renderAndWaitForInitialLoad(<SearchHistoryList user={baseUser} />);

    await userEvent.selectOptions(screen.getByLabelText('부서'), '개발부');
    await userEvent.type(screen.getByLabelText(/사용자명/), '홍길');
    await userEvent.type(screen.getByLabelText(/사용자 ID/), '12345678');
    await userEvent.click(screen.getByRole('button', { name: '검색' }));

    await waitFor(() => expect(getSearchHistoryList).toHaveBeenCalledTimes(2));
    await waitFor(() => expect(screen.queryByText('데이터를 불러오는 중...')).not.toBeInTheDocument());

    rerender(
      <SearchHistoryList
        user={{
          ...baseUser,
          screenScopes: {
            'search-history': 'self',
          },
        }}
      />,
    );

    await waitFor(() => expect(getSearchHistoryList).toHaveBeenCalledTimes(3));
    await waitFor(() => expect(screen.queryByText('데이터를 불러오는 중...')).not.toBeInTheDocument());

    expect(screen.getByText('요청자')).toBeInTheDocument();
    expect(screen.getByDisplayValue('개발부')).toHaveAttribute('readonly');
    expect(screen.getByDisplayValue('홍길동')).toHaveAttribute('readonly');
    expect(screen.getByDisplayValue('20260001')).toHaveAttribute('readonly');
    expect(getSearchHistoryList).toHaveBeenLastCalledWith({
      page: 1,
      pageSize: 20,
      sortField: 'requested_at',
      sortDirection: 'desc',
    });
  });
});
