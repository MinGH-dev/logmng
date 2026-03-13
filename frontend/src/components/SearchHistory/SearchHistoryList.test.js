import React from 'react';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import SearchHistoryList from './SearchHistoryList';
import {
  getSearchHistoryList,
  getSearchHistoryDetail,
  reRequestSearchHistory,
} from '../../services/searchHistoryService';
import { statisticsApi } from '../../services/api';

jest.mock('../../services/searchHistoryService', () => ({
  getSearchHistoryList: jest.fn(),
  getSearchHistoryDetail: jest.fn(),
  reRequestSearchHistory: jest.fn(),
}));

jest.mock('../../services/api', () => ({
  statisticsApi: {
    getDepartmentList: jest.fn(),
  },
}));

const baseUser = {
  username: '10000001',
  isSystemAdmin: false,
  screenScopes: {
    'search-history': 'team',
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
        searchParamsSummary: '요청자=10000001',
        approvalStatus: 'PENDING',
        expiresAt: '2026-03-20 10:00:00',
        userId: '10000001',
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

const renderAndWaitForInitialLoad = async (ui) => {
  const view = render(ui);

  await waitFor(() => expect(getSearchHistoryList).toHaveBeenCalledTimes(1));
  await screen.findByText('요청자=10000001');
  await waitFor(() => expect(screen.getByRole('button', { name: '검색' })).toBeEnabled());
  await waitFor(() => expect(screen.queryByText('데이터를 불러오는 중...')).not.toBeInTheDocument());

  return view;
};

describe('SearchHistoryList', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    statisticsApi.getDepartmentList.mockResolvedValue({
      success: true,
      data: ['개발부', '운영부'],
    });
    getSearchHistoryList.mockResolvedValue(listResponse);
    getSearchHistoryDetail.mockResolvedValue({ success: true, data: {} });
    reRequestSearchHistory.mockResolvedValue({ success: true });
  });

  test('TC-07: non-self scope shows standard requester toolbar with compact shared classes', async () => {
    const { container } = await renderAndWaitForInitialLoad(<SearchHistoryList user={baseUser} />);

    expect(statisticsApi.getDepartmentList).toHaveBeenCalledTimes(1);

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
      userId: '12345678',
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
      userId: '12345678',
    });
  });

  test('TC-10: switching to self scope hides requester UI, clears local state, and omits requester params', async () => {
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

    expect(screen.queryByText('요청자')).not.toBeInTheDocument();
    expect(getSearchHistoryList).toHaveBeenLastCalledWith({
      page: 1,
      pageSize: 20,
      sortField: 'requested_at',
      sortDirection: 'desc',
    });
  });
});
