import React from 'react';
import { render, screen, fireEvent } from '@testing-library/react';
import LogTable from './LogTable';

const pbFepSvgLog = (id) => ({
  id,
  log_type: 'PB',
  log_time: '2024-01-01T10:00:00',
  tr_code: 'TR',
  login_id: 'u1',
  msg_code: 'M',
  bmsg: 'b',
  log_ch_cd: 'c',
  send_recv: 'S',
  src_ip: '1.1.1.1',
  dest_ip: '2.2.2.2',
  app_id: 'a',
  data: 'd',
});

const baseProps = {
  logs: [pbFepSvgLog(1)],
  loading: false,
  sortConfig: { key: 'log_time', direction: 'desc' },
  sortCriteria: [{ key: 'log_time', direction: 'desc' }],
  onSort: jest.fn(),
  currentPage: 1,
  totalPages: 1,
  totalCount: 1,
  onPageChange: jest.fn(),
  pageSize: 25,
  onPageSizeChange: jest.fn(),
  layoutVariant: 'pb-fep-svg',
};

describe('LogTable PB FEP controlled expansion', () => {
  test('onRowExpandChange receives manualCollapse true when user collapses a row', () => {
    const onRowExpandChange = jest.fn();
    render(
      <LogTable
        {...baseProps}
        expandedRowKeys={new Set(['PB-1'])}
        onRowExpandChange={onRowExpandChange}
      />
    );
    fireEvent.click(screen.getByRole('button', { name: '전문 접기' }));
    expect(onRowExpandChange).toHaveBeenCalledTimes(1);
    expect(onRowExpandChange).toHaveBeenCalledWith(expect.any(Set), { manualCollapse: true });
    const arg = onRowExpandChange.mock.calls[0][0];
    expect(arg.has('PB-1')).toBe(false);
  });

  test('onRowExpandChange receives manualCollapse false when user expands a row', () => {
    const onRowExpandChange = jest.fn();
    render(
      <LogTable
        {...baseProps}
        expandedRowKeys={new Set()}
        onRowExpandChange={onRowExpandChange}
      />
    );
    fireEvent.click(screen.getByRole('button', { name: '전문 펼치기' }));
    expect(onRowExpandChange).toHaveBeenCalledTimes(1);
    expect(onRowExpandChange).toHaveBeenCalledWith(expect.any(Set), { manualCollapse: false });
    const arg = onRowExpandChange.mock.calls[0][0];
    expect(arg.has('PB-1')).toBe(true);
  });
});
