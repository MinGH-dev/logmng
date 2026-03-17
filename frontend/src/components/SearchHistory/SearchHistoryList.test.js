import React from 'react';
import { render, screen, waitFor, within, fireEvent } from '@testing-library/react';
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
        requesterDepartmentCode: 'TEAM_SALES_A1',
        requesterDisplayName: '홍길동',
        requesterUsername: '10000001',
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
  await screen.findByRole('button', { name: /검색 조건 보기/i });
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
    expect(getSearchHistoryList).toHaveBeenNthCalledWith(2, expect.objectContaining({
      page: 1,
      pageSize: 20,
      sortField: 'requested_at',
      sortDirection: 'desc',
      department: '개발부',
      username: '홍길',
      userId: 12345678,
    }));

    await userEvent.click(screen.getByRole('button', { name: '행 수 증가' }));

    await waitFor(() => expect(getSearchHistoryList).toHaveBeenCalledTimes(3));
    await waitFor(() => expect(screen.queryByText('데이터를 불러오는 중...')).not.toBeInTheDocument());
    expect(getSearchHistoryList).toHaveBeenNthCalledWith(3, expect.objectContaining({
      page: 1,
      pageSize: 21,
      sortField: 'requested_at',
      sortDirection: 'desc',
      department: '개발부',
      username: '홍길',
      userId: 12345678,
    }));
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

  test('TC-02: grid header for timestamp column shows 검색일시', async () => {
    const { container } = await renderAndWaitForInitialLoad(<SearchHistoryList user={baseUser} />);

    const headerCells = container.querySelectorAll('.log-table thead tr th');
    const headerLabels = Array.from(headerCells).map((th) => th.textContent?.trim().replace(/\s+/g, ' ') ?? '');

    expect(headerLabels).toEqual([
      expect.stringContaining('순번'),
      expect.stringContaining('검색일시'),
      '부서',
      '사용자ID',
      '사용자명',
      '검색 조건',
      '복호화',
      '요청사유',
      '만료일시',
      '동작',
    ]);
  });

  test('TC-02: requester data in three separate cells (부서, 사용자ID, 사용자명)', async () => {
    const { container } = await renderAndWaitForInitialLoad(<SearchHistoryList user={baseUser} />);

    const firstDataRow = container.querySelector('.log-table tbody tr');
    expect(firstDataRow).not.toBeNull();
    const cells = firstDataRow.querySelectorAll('td');
    expect(cells.length).toBe(10);

    const seqCell = cells[0].textContent?.trim();
    const dateCell = cells[1].textContent?.trim();
    const deptCell = cells[2].textContent?.trim();
    const usernameCell = cells[3].textContent?.trim();
    const displayNameCell = cells[4].textContent?.trim();

    expect(seqCell).toBe('1');
    expect(dateCell).toContain('2026-03-13');
    expect(deptCell).toBe('TEAM_SALES_A1');
    expect(usernameCell).toBe('10000001');
    expect(displayNameCell).toBe('홍길동');

    expect(deptCell).not.toMatch(/\s*\/\s*/);
    expect(usernameCell).not.toMatch(/\s*\/\s*/);
    expect(displayNameCell).not.toMatch(/\s*\/\s*/);
  });

  test('TC-03: empty list shows empty state with colSpan 10', async () => {
    getSearchHistoryList.mockResolvedValueOnce({
      success: true,
      data: { data: [], pagination: { currentPage: 1, totalPages: 1, totalCount: 0 } },
    });

    const { container } = render(<SearchHistoryList user={baseUser} />);
    await waitFor(() => expect(getSearchHistoryList).toHaveBeenCalledTimes(1));
    await waitFor(() => expect(screen.queryByText('데이터를 불러오는 중...')).not.toBeInTheDocument());
    await screen.findByText(/검색 이력이 없습니다/);

    const emptyRow = container.querySelector('.log-table tbody tr');
    expect(emptyRow).not.toBeNull();
    const emptyCell = emptyRow.querySelector('td[colspan="10"]');
    expect(emptyCell).not.toBeNull();
    expect(emptyCell?.textContent).toContain('검색 이력이 없습니다');
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
    expect(getSearchHistoryList).toHaveBeenLastCalledWith(expect.objectContaining({
      page: 1,
      pageSize: 20,
      sortField: 'requested_at',
      sortDirection: 'desc',
    }));
  });

  test('TC-08: set 검색일시 range and search sends requestedAtFrom, requestedAtTo', async () => {
    await renderAndWaitForInitialLoad(<SearchHistoryList user={baseUser} />);

    const fromInput = screen.getByLabelText(/검색일시 \(시작\)/);
    const toInput = screen.getByLabelText(/검색일시 \(종료\)/);
    fireEvent.change(fromInput, { target: { value: '2026-03-01T09:00' } });
    fireEvent.change(toInput, { target: { value: '2026-03-17T18:00' } });

    await userEvent.click(screen.getByRole('button', { name: '검색' }));

    await waitFor(() => expect(getSearchHistoryList).toHaveBeenCalledTimes(2));
    expect(getSearchHistoryList).toHaveBeenNthCalledWith(2, expect.objectContaining({
      requestedAtFrom: '2026-03-01 09:00:00',
      requestedAtTo: '2026-03-17 18:00:00',
    }));
  });

  test('TC-04: open approval dropdown, select 승인 and 반려, search sends approvalStatuses', async () => {
    await renderAndWaitForInitialLoad(<SearchHistoryList user={baseUser} />);

    const trigger = screen.getByRole('button', { name: /복호화/ });
    await userEvent.click(trigger);
    await userEvent.click(screen.getByRole('option', { name: '승인' }));
    await userEvent.click(screen.getByRole('option', { name: '반려' }));
    await userEvent.click(screen.getByRole('button', { name: '검색' }));

    await waitFor(() => expect(getSearchHistoryList).toHaveBeenCalledTimes(2));
    expect(getSearchHistoryList).toHaveBeenNthCalledWith(2, expect.objectContaining({
      approvalStatuses: expect.arrayContaining(['APPROVED', 'REJECTED']),
    }));
    const call = getSearchHistoryList.mock.calls[1][0];
    expect(call.approvalStatuses).toHaveLength(2);
  });

  test('TC-09: enter 요청 사유 and search sends requestReason', async () => {
    await renderAndWaitForInitialLoad(<SearchHistoryList user={baseUser} />);

    const reasonInput = screen.getByLabelText('요청 사유');
    await userEvent.type(reasonInput, '검색');
    await userEvent.click(screen.getByRole('button', { name: '검색' }));

    await waitFor(() => expect(getSearchHistoryList).toHaveBeenCalledTimes(2));
    expect(getSearchHistoryList).toHaveBeenNthCalledWith(2, expect.objectContaining({
      requestReason: '검색',
    }));
  });

  test('TC-05: 모두선택 in dropdown selects all approval statuses, search sends all four', async () => {
    await renderAndWaitForInitialLoad(<SearchHistoryList user={baseUser} />);

    const trigger = screen.getByRole('button', { name: /복호화/ });
    await userEvent.click(trigger);
    const selectAllOption = screen.getByRole('option', { name: '모두선택' });
    expect(selectAllOption).toHaveAttribute('aria-selected', 'false');

    await userEvent.click(selectAllOption);
    await waitFor(() => expect(selectAllOption).toHaveAttribute('aria-selected', 'true'));

    await userEvent.click(screen.getByRole('button', { name: '검색' }));
    await waitFor(() => expect(getSearchHistoryList).toHaveBeenCalledTimes(2));
    const call = getSearchHistoryList.mock.calls[1][0];
    expect(call.approvalStatuses).toEqual(expect.arrayContaining(['PENDING', 'APPROVED', 'REJECTED', 'EXPIRED']));
    expect(call.approvalStatuses).toHaveLength(4);
  });

  test('TC-06: 초기화 restores default date range (d−7, d+0), clears approval and 요청 사유', async () => {
    await renderAndWaitForInitialLoad(<SearchHistoryList user={baseUser} />);

    fireEvent.change(screen.getByLabelText(/검색일시 \(시작\)/), { target: { value: '2026-03-01T09:00' } });
    await userEvent.click(screen.getByRole('button', { name: /복호화/ }));
    await userEvent.click(screen.getByRole('option', { name: '승인' }));
    await userEvent.type(screen.getByLabelText('요청 사유'), 'test');
    await userEvent.click(screen.getByRole('button', { name: '검색' }));

    await waitFor(() => expect(getSearchHistoryList).toHaveBeenCalledTimes(2));

    await userEvent.click(screen.getByRole('button', { name: '초기화' }));

    await waitFor(() => expect(getSearchHistoryList).toHaveBeenCalledTimes(3));
    const call = getSearchHistoryList.mock.calls[2][0];
    expect(call.requestedAtFrom).toBeDefined();
    expect(call.requestedAtTo).toBeDefined();
    expect(typeof call.requestedAtFrom).toBe('string');
    expect(typeof call.requestedAtTo).toBe('string');
    expect(call.requestedAtFrom).toMatch(/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$/);
    expect(call.requestedAtTo).toMatch(/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$/);
    expect(call.approvalStatuses).toBeUndefined();
    expect(call.requestReason).toBeUndefined();
    const fromInput = screen.getByLabelText(/검색일시 \(시작\)/);
    const toInput = screen.getByLabelText(/검색일시 \(종료\)/);
    expect(fromInput.value).toMatch(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/);
    expect(toInput.value).toMatch(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/);
    expect(screen.getByLabelText('요청 사유').value).toBe('');
  });

  test('TC-01: form labels 검색일시 (시작), 검색일시 (종료) visible; default date range d−7 and d+0', async () => {
    await renderAndWaitForInitialLoad(<SearchHistoryList user={baseUser} />);

    expect(screen.getByLabelText(/검색일시 \(시작\)/)).toBeInTheDocument();
    expect(screen.getByLabelText(/검색일시 \(종료\)/)).toBeInTheDocument();
    expect(screen.queryByLabelText(/요청일시 \(시작\)/)).not.toBeInTheDocument();
    expect(screen.queryByLabelText(/요청일시 \(종료\)/)).not.toBeInTheDocument();

    const fromInput = screen.getByLabelText(/검색일시 \(시작\)/);
    const toInput = screen.getByLabelText(/검색일시 \(종료\)/);
    expect(fromInput.value).toMatch(/^\d{4}-\d{2}-\d{2}T00:00$/);
    expect(toInput.value).toMatch(/^\d{4}-\d{2}-\d{2}T23:59$/);
    expect(getSearchHistoryList).toHaveBeenCalledWith(expect.objectContaining({
      requestedAtFrom: expect.stringMatching(/^\d{4}-\d{2}-\d{2} 00:00:00$/),
      requestedAtTo: expect.stringMatching(/^\d{4}-\d{2}-\d{2} 23:59/),
    }));
  });

  test('TC-03: select 15d preset keeps other filters and refreshes list', async () => {
    await renderAndWaitForInitialLoad(<SearchHistoryList user={baseUser} />);
    await userEvent.selectOptions(screen.getByLabelText('부서'), '개발부');
    await userEvent.type(screen.getByLabelText(/사용자명/), '홍길');
    await userEvent.click(screen.getByRole('button', { name: '검색' }));
    await waitFor(() => expect(getSearchHistoryList).toHaveBeenCalledTimes(2));
    const callCountBeforePreset = getSearchHistoryList.mock.calls.length;
    await userEvent.selectOptions(screen.getByLabelText('기간'), '15');
    await waitFor(() => expect(getSearchHistoryList).toHaveBeenCalledTimes(callCountBeforePreset + 1));
    const call = getSearchHistoryList.mock.calls[callCountBeforePreset][0];
    expect(call.department).toBe('개발부');
    expect(call.username).toBe('홍길');
    expect(call.requestedAtFrom).toBeDefined();
    expect(call.requestedAtTo).toBeDefined();
  });

  test('TC-04: select 15d then 30d preset updates start date and refreshes each time', async () => {
    await renderAndWaitForInitialLoad(<SearchHistoryList user={baseUser} />);
    await userEvent.selectOptions(screen.getByLabelText('기간'), '15');
    await waitFor(() => expect(getSearchHistoryList).toHaveBeenCalledTimes(2));
    const from15 = screen.getByLabelText(/검색일시 \(시작\)/).value;
    expect(from15).toMatch(/^\d{4}-\d{2}-\d{2}T00:00$/);
    await userEvent.selectOptions(screen.getByLabelText('기간'), '30');
    await waitFor(() => expect(getSearchHistoryList).toHaveBeenCalledTimes(3));
    const from30 = screen.getByLabelText(/검색일시 \(시작\)/).value;
    expect(from30).toMatch(/^\d{4}-\d{2}-\d{2}T00:00$/);
    const date15 = new Date(from15.replace('T00:00', ''));
    const date30 = new Date(from30.replace('T00:00', ''));
    expect(date30.getTime()).toBeLessThan(date15.getTime());
  });

  test('TC-06: filter label and grid column show 복호화', async () => {
    const { container } = await renderAndWaitForInitialLoad(<SearchHistoryList user={baseUser} />);
    const 복호화Elements = screen.getAllByText('복호화');
    expect(복호화Elements.length).toBeGreaterThanOrEqual(1);
    expect(screen.getByRole('button', { name: /복호화/ })).toBeInTheDocument();
    const headerCells = container.querySelectorAll('.log-table thead tr th');
    const approvalHeader = Array.from(headerCells).find((th) => th.textContent?.trim() === '복호화');
    expect(approvalHeader).toBeInTheDocument();
  });

  test('TC-01 (req): 사용자ID column width fits 8-digit numbers', async () => {
    const { container } = await renderAndWaitForInitialLoad(<SearchHistoryList user={baseUser} />);

    const headerCells = container.querySelectorAll('.log-table thead tr th');
    expect(headerCells.length).toBeGreaterThanOrEqual(4);
    expect(headerCells[3].textContent?.trim()).toBe('사용자ID');

    const firstRow = container.querySelector('.log-table tbody tr');
    expect(firstRow).not.toBeNull();
    const cells = firstRow.querySelectorAll('td');
    expect(cells.length).toBeGreaterThanOrEqual(4);
    const userIdCell = cells[3];
    expect(userIdCell.textContent?.trim()).toBe('10000001');
    expect(container.querySelector('.search-history-list .log-table th:nth-child(4)')).toBeInTheDocument();
  });

  test('TC-02 (req): 검색 조건 column width fits the button', async () => {
    const { container } = await renderAndWaitForInitialLoad(<SearchHistoryList user={baseUser} />);

    const headerCells = container.querySelectorAll('.log-table thead tr th');
    expect(headerCells.length).toBeGreaterThanOrEqual(6);
    expect(headerCells[5].textContent?.trim()).toBe('검색 조건');

    const firstRow = container.querySelector('.log-table tbody tr');
    const cells = firstRow.querySelectorAll('td');
    const conditionsCell = cells[5];
    const btn = within(conditionsCell).getByRole('button', { name: /검색 조건 보기/i });
    expect(btn).toBeInTheDocument();
    expect(btn.textContent?.trim()).toBe('검색 조건 보기');
    expect(container.querySelector('.search-history-list .log-table th:nth-child(6)')).toBeInTheDocument();
  });

  test('TC-09 (integration): set date + approval + request reason, Search sends all filter params', async () => {
    await renderAndWaitForInitialLoad(<SearchHistoryList user={baseUser} />);

    fireEvent.change(screen.getByLabelText(/검색일시 \(시작\)/), { target: { value: '2026-03-01T09:00' } });
    fireEvent.change(screen.getByLabelText(/검색일시 \(종료\)/), { target: { value: '2026-03-17T18:00' } });
    await userEvent.click(screen.getByRole('button', { name: /복호화/ }));
    await userEvent.click(screen.getByRole('option', { name: '승인' }));
    await userEvent.type(screen.getByLabelText('요청 사유'), '테스트 사유');
    await userEvent.click(screen.getByRole('button', { name: '검색' }));

    await waitFor(() => expect(getSearchHistoryList).toHaveBeenCalledTimes(2));
    const call = getSearchHistoryList.mock.calls[1][0];
    expect(call.requestedAtFrom).toBe('2026-03-01 09:00:00');
    expect(call.requestedAtTo).toBe('2026-03-17 18:00:00');
    expect(call.approvalStatuses).toEqual(expect.arrayContaining(['APPROVED']));
    expect(call.requestReason).toBe('테스트 사유');
  });

  test('TC-14: two-row layout and filter fields use design doc sizing', async () => {
    const { container } = await renderAndWaitForInitialLoad(<SearchHistoryList user={baseUser} />);

    const toolbar = container.querySelector('.search-history-toolbar.sf-compact-panel');
    expect(toolbar).not.toBeNull();
    expect(container.querySelector('.search-history-toolbar__row-1')).not.toBeNull();
    expect(container.querySelector('.search-history-toolbar__row-2')).not.toBeNull();

    const dateFrom = document.getElementById('search-history-requested-at-from');
    const dateTo = document.getElementById('search-history-requested-at-to');
    const reasonInput = document.getElementById('search-history-request-reason');
    expect(dateFrom).toBeInTheDocument();
    expect(dateTo).toBeInTheDocument();
    expect(reasonInput).toBeInTheDocument();
    expect(dateFrom).toHaveClass('form-control');
    expect(reasonInput).toHaveClass('form-control');
  });
});
