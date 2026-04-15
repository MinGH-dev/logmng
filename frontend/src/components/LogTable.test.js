import React from 'react';
import { render, screen, fireEvent } from '@testing-library/react';
import LogTable, { formatLogTableTime } from './LogTable';

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

describe('formatLogTableTime (PB FEP log_time lexical)', () => {
  test('formats 20-digit yyyyMMddHHmmssSSSSSS with microseconds', () => {
    expect(formatLogTableTime('20260415143025123456')).toBe('2026-04-15 14:30:25.123456');
  });

  test('formats legacy 14-digit yyyyMMddHHmmss without fractional part', () => {
    expect(formatLogTableTime('20260415143025')).toBe('2026-04-15 14:30:25');
  });

  test('renders 20-digit log_time in pb-fep-svg table cell', () => {
    render(
      <LogTable
        {...baseProps}
        logs={[{ ...pbFepSvgLog(1), log_time: '20260415143025123456' }]}
      />
    );
    expect(screen.getByText('2026-04-15 14:30:25.123456')).toBeInTheDocument();
  });
});

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
