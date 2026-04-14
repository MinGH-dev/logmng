import React from 'react';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import LogGrid from './LogGrid';
import { getPbFeplogRowKey } from './LogTable';

jest.mock('../config/runtimeApi', () => ({
  getApiBaseUrl: () => 'http://test-api.example/api',
}));

/** Distinct timestamps so getPbFeplogRowKey fallback stays unique if `id` is absent in API rows. */
const makePbFepRow = (id) => ({
  id,
  log_type: 'PB',
  log_time: `2024-01-01T10:00:00.${String(id).padStart(6, '0')}`,
  tr_code: 'TR',
  login_id: 'user01',
  msg_code: 'M',
  bmsg: 'msg',
  log_ch_cd: 'ch',
  send_recv: 'S',
  src_ip: '10.0.0.1',
  dest_ip: '10.0.0.2',
  app_id: 'app',
  data: 'stream-line',
});

function mockPbFepTwoPageFetch(page1Rows, page2Rows) {
  global.fetch.mockImplementation((_url, opts) => {
    const body = opts?.body ? JSON.parse(opts.body) : {};
    const page = body.page ?? 1;
    const rows = page === 1 ? page1Rows : page2Rows;
    const total = page1Rows.length + page2Rows.length;
    const payload = {
      success: true,
      data: {
        data: rows,
        pagination: { totalPages: 2, currentPage: page, totalCount: total },
      },
    };
    return Promise.resolve({
      ok: true,
      json: async () => JSON.parse(JSON.stringify(payload)),
    });
  });
}

async function searchPbFepWireframe() {
  fireEvent.change(screen.getByPlaceholderText('Login ID'), { target: { value: 'user01' } });
  fireEvent.click(screen.getByRole('button', { name: '검색' }));
  await waitFor(() => {
    expect(screen.getByRole('table', { name: '로그 검색 결과' })).toBeInTheDocument();
  });
}

describe('LogGrid Java FW Image Log layout', () => {
  beforeEach(() => {
    global.fetch = jest.fn().mockResolvedValue({
      ok: true,
      json: async () => ({
        success: true,
        data: { validUntil: null, guids: [], allowedRows: [] },
      }),
    });
  });

  test('java_fw_imglog uses flex grid modifier, table region, and DataTable fill + pagination footer', () => {
    const { container } = render(
      <LogGrid
        viewId="java-fw-imagelog"
        logType={{ id: 'java_fw_imglog', name: 'Java FW Image Log', description: '' }}
        hasDecryptPermission
      />
    );
    expect(container.querySelector('.log-grid.log-grid--java-fw-imagelog')).toBeInTheDocument();
    const region = container.querySelector('.log-grid-table-region');
    expect(region).toBeInTheDocument();
    expect(region.querySelector('.log-table-container.log-table-container--fill')).toBeInTheDocument();
    expect(region.querySelector('.pagination.pagination--info-buttons-size')).toBeInTheDocument();
  });
});

describe('LogGrid PB FEP endpoints', () => {
  beforeEach(() => {
    global.fetch = jest.fn();
  });

  test('pb-fep-log-search view posts to pb-fep-log-search URL', async () => {
    global.fetch.mockResolvedValue({
      ok: true,
      json: async () => ({
        success: true,
        data: {
          data: [],
          pagination: { totalPages: 1, currentPage: 1, totalCount: 0 },
        },
      }),
    });

    render(
      <LogGrid
        viewId="pb-fep-log-search"
        logType={{ id: 'pb_feplog', name: 'PB FEP v2.0.0', description: '' }}
        hasDecryptPermission={false}
      />
    );

    fireEvent.change(screen.getByPlaceholderText('Login ID'), { target: { value: 'user01' } });
    fireEvent.click(screen.getByRole('button', { name: '검색' }));

    await waitFor(() => {
      expect(global.fetch).toHaveBeenCalled();
    });

    const calls = global.fetch.mock.calls;
    const searchCall = calls.find(
      (c) => typeof c[0] === 'string' && c[0].includes('/pb-fep-log-search')
    );
    expect(searchCall).toBeDefined();
    expect(searchCall[0]).toBe('http://test-api.example/api/logs/db-refactored/pb-fep-log-search');
    const body = JSON.parse(searchCall[1].body);
    expect(body.sortSpecs).toEqual([{ field: 'log_time', direction: 'desc' }]);
    expect(body.sortSpecs.some((s) => s.field === 'log_timestamp')).toBe(false);
  });

  test('pb-feplog legacy view posts to db-refactored/search', async () => {
    global.fetch.mockResolvedValue({
      ok: true,
      json: async () => ({
        success: true,
        data: {
          data: [],
          pagination: { totalPages: 1, currentPage: 1, totalCount: 0 },
        },
      }),
    });

    render(
      <LogGrid
        viewId="pb-feplog"
        logType={{ id: 'pb_feplog', name: 'PB FEP v1.0.0', description: '' }}
        hasDecryptPermission={false}
      />
    );

    fireEvent.change(screen.getByPlaceholderText('TR Code'), { target: { value: 'TR01' } });
    fireEvent.change(screen.getByPlaceholderText('Login ID'), { target: { value: 'user01' } });
    fireEvent.click(screen.getByRole('button', { name: '검색' }));

    await waitFor(() => {
      expect(global.fetch).toHaveBeenCalled();
    });

    const searchCall = global.fetch.mock.calls.find(
      (c) => typeof c[0] === 'string' && c[0].includes('/db-refactored/search')
    );
    expect(searchCall).toBeDefined();
    expect(searchCall[0]).not.toContain('pb-fep-log-search');
  });

  test('pb-feplog legacy initialSearchParams datetime-local gets normalized to space format', async () => {
    global.fetch.mockResolvedValue({
      ok: true,
      json: async () => ({
        success: true,
        data: {
          data: [],
          pagination: { totalPages: 1, currentPage: 1, totalCount: 0 },
        },
      }),
    });

    render(
      <LogGrid
        viewId="pb-feplog"
        logType={{ id: 'pb_feplog', name: 'PB FEP v1.0.0', description: '' }}
        hasDecryptPermission={false}
        initialSearchParams={{
          tr_code: 'TR01',
          loginId: 'user01',
          startDate: '2026-04-14T00:00',
          endDate: '2026-04-14T23:59',
        }}
      />
    );

    await waitFor(() => {
      expect(global.fetch).toHaveBeenCalled();
    });

    const searchCall = global.fetch.mock.calls.find(
      (c) => typeof c[0] === 'string' && c[0].includes('/db-refactored/search')
    );
    expect(searchCall).toBeDefined();
    const body = JSON.parse(searchCall[1].body);
    expect(body.startDate).toBe('2026-04-14 00:00:00');
    expect(body.endDate).toBe('2026-04-14 23:59:00');
    expect(body.sortSpecs).toEqual([{ field: 'log_time', direction: 'desc' }]);
    expect(body.sortSpecs.some((s) => s.field === 'log_timestamp')).toBe(false);
  });

  /** TC-01 / TC-04: expand-all then page 2 — all rows expanded without clicking expand-all again */
  test('TC-01 TC-04: 전체 펼치기 후 다음 페이지에서도 모든 행이 펼쳐짐', async () => {
    const r1 = [makePbFepRow(101), makePbFepRow(102)];
    const r2 = [makePbFepRow(201), makePbFepRow(202)];
    mockPbFepTwoPageFetch(r1, r2);

    render(
      <LogGrid
        viewId="pb-fep-log-search"
        logType={{ id: 'pb_feplog', name: 'PB FEP v2.0.0', description: '' }}
        hasDecryptPermission={false}
      />
    );

    await searchPbFepWireframe();

    fireEvent.click(screen.getByRole('button', { name: '전체 펼치기' }));
    await waitFor(() => {
      expect(screen.getAllByRole('button', { name: '전문 접기' })).toHaveLength(2);
    });

    fireEvent.click(screen.getByRole('button', { name: '2' }));

    await waitFor(() => {
      expect(screen.getAllByRole('button', { name: '전문 접기' })).toHaveLength(2);
    });
  });

  /** TC-02: collapse-all then page 2 — all rows collapsed */
  test('TC-02: 전체 접기 후 다음 페이지에서도 모든 행이 접힘', async () => {
    const r1 = [makePbFepRow(101), makePbFepRow(102)];
    const r2 = [makePbFepRow(201), makePbFepRow(202)];
    mockPbFepTwoPageFetch(r1, r2);

    render(
      <LogGrid
        viewId="pb-fep-log-search"
        logType={{ id: 'pb_feplog', name: 'PB FEP v2.0.0', description: '' }}
        hasDecryptPermission={false}
      />
    );

    await searchPbFepWireframe();

    fireEvent.click(screen.getByRole('button', { name: '전체 펼치기' }));
    await waitFor(() => {
      expect(screen.getByRole('button', { name: '전체 접기' })).toBeInTheDocument();
    });

    fireEvent.click(screen.getByRole('button', { name: '전체 접기' }));

    await waitFor(() => {
      expect(screen.getAllByRole('button', { name: '전문 펼치기' })).toHaveLength(2);
    });

    fireEvent.click(screen.getByRole('button', { name: '2' }));

    await waitFor(() => {
      expect(screen.getAllByRole('button', { name: '전문 펼치기' })).toHaveLength(2);
    });
  });

  /** TC-03: manual collapse one row clears full expand-all pressed state */
  test('TC-03: 전체 펼치기 후 한 행만 접으면 전체 펼치기 활성(aria-pressed) 해제', async () => {
    const r1 = [makePbFepRow(101), makePbFepRow(102)];
    mockPbFepTwoPageFetch(r1, r1);

    render(
      <LogGrid
        viewId="pb-fep-log-search"
        logType={{ id: 'pb_feplog', name: 'PB FEP v2.0.0', description: '' }}
        hasDecryptPermission={false}
      />
    );

    await searchPbFepWireframe();

    const expandAll = screen.getByRole('button', { name: '전체 펼치기' });
    fireEvent.click(expandAll);

    await waitFor(() => {
      expect(expandAll).toHaveAttribute('aria-pressed', 'true');
    });

    const firstRowExpand = screen.getAllByRole('button', { name: '전문 접기' })[0];
    fireEvent.click(firstRowExpand);

    await waitFor(() => {
      expect(expandAll).toHaveAttribute('aria-pressed', 'false');
    });
    expect(screen.getByRole('button', { name: '전체 펼치기' })).toBeInTheDocument();
  });

  /** TC-05: new search clears expansion */
  test('TC-05: 새 검색 시 펼침 상태 초기화', async () => {
    const r1 = [makePbFepRow(101), makePbFepRow(102)];
    mockPbFepTwoPageFetch(r1, r1);

    render(
      <LogGrid
        viewId="pb-fep-log-search"
        logType={{ id: 'pb_feplog', name: 'PB FEP v2.0.0', description: '' }}
        hasDecryptPermission={false}
      />
    );

    await searchPbFepWireframe();

    fireEvent.click(screen.getByRole('button', { name: '전체 펼치기' }));
    await waitFor(() => {
      expect(screen.getAllByRole('button', { name: '전문 접기' })).toHaveLength(2);
    });

    mockPbFepTwoPageFetch(r1, r1);
    fireEvent.change(screen.getByPlaceholderText('Login ID'), { target: { value: 'user02' } });
    fireEvent.click(screen.getByRole('button', { name: '검색' }));

    await waitFor(() => {
      expect(screen.getAllByRole('button', { name: '전문 펼치기' })).toHaveLength(2);
    });
    expect(screen.getByRole('button', { name: '전체 펼치기' })).toHaveAttribute('aria-pressed', 'false');
  });

  test('makePbFepRow keys are distinct for expand/collapse tests', () => {
    expect(getPbFeplogRowKey(makePbFepRow(101))).not.toBe(getPbFeplogRowKey(makePbFepRow(102)));
    expect(getPbFeplogRowKey(makePbFepRow(201))).not.toBe(getPbFeplogRowKey(makePbFepRow(202)));
  });

  /** TC-08: page 2 — manual collapse then re-expand all rows → toolbar shows 전체 접기 again */
  test('TC-08: 페이지2에서 한 행 접었다가 다시 펼치면 전체 접기(aria-pressed true) 복구', async () => {
    const r1 = [makePbFepRow(101), makePbFepRow(102)];
    const r2 = [makePbFepRow(201), makePbFepRow(202)];
    mockPbFepTwoPageFetch(r1, r2);

    render(
      <LogGrid
        viewId="pb-fep-log-search"
        logType={{ id: 'pb_feplog', name: 'PB FEP v2.0.0', description: '' }}
        hasDecryptPermission={false}
      />
    );

    await searchPbFepWireframe();

    const expandAllBtn = screen.getByRole('button', { name: '전체 펼치기' });
    await userEvent.click(expandAllBtn);
    await waitFor(() => {
      expect(screen.getByRole('button', { name: '전체 접기' })).toBeInTheDocument();
    });
    await waitFor(() => {
      expect(screen.getAllByRole('button', { name: '전문 접기' })).toHaveLength(2);
    });

    await userEvent.click(screen.getByRole('button', { name: '2' }));
    await waitFor(() => {
      expect(screen.getAllByRole('button', { name: '전문 접기' })).toHaveLength(2);
    });

    expect(screen.getByRole('button', { name: '전체 접기' })).toHaveAttribute('aria-pressed', 'true');

    const rowCollapseButtons = screen.getAllByRole('button', { name: '전문 접기' });
    expect(rowCollapseButtons).toHaveLength(2);
    // Collapse first data row on page 2; other row stays expanded (mixed state)
    await userEvent.click(rowCollapseButtons[0]);
    await waitFor(() => {
      expect(screen.getByRole('button', { name: '전체 펼치기' })).toHaveAttribute('aria-pressed', 'false');
    });

    const rowExpandButtons = screen.getAllByRole('button', { name: '전문 펼치기' });
    expect(rowExpandButtons).toHaveLength(1);
    await userEvent.click(rowExpandButtons[0]);
    await waitFor(() => {
      expect(screen.getAllByRole('button', { name: '전문 접기' })).toHaveLength(2);
    });
    expect(screen.getByRole('button', { name: '전체 접기' })).toHaveAttribute('aria-pressed', 'true');
  });
});
