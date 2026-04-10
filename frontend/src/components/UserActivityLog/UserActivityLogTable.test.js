import React from 'react';
import { render, screen } from '@testing-library/react';
import UserActivityLogTable from './UserActivityLogTable';

jest.mock('../DataTable', () => {
  const React = require('react');
  const EmptyTableBody = ({ message, colSpan }) => (
    <tbody>
      <tr>
        <td colSpan={colSpan}>{message}</td>
      </tr>
    </tbody>
  );
  const DataTable = ({
    children,
    columns,
    loading,
    emptyMessage,
    emptyColSpan,
    pagination,
    pageSize,
    onPageSizeChange,
  }) => (
    <table data-testid="data-table">
      <thead>
        <tr>
          {columns.map((c) => (
            <th key={c.key}>{c.label}</th>
          ))}
        </tr>
      </thead>
      {loading ? (
        <tbody>
          <tr>
            <td colSpan={columns.length}>loading</td>
          </tr>
        </tbody>
      ) : (
        <tbody>
          {children || (
            <EmptyTableBody colSpan={emptyColSpan || columns.length} message={emptyMessage} />
          )}
        </tbody>
      )}
      {pagination && (
        <tfoot>
          <tr>
            <td colSpan={columns.length}>{pagination.infoText}</td>
          </tr>
        </tfoot>
      )}
    </table>
  );
  return { __esModule: true, default: DataTable, EmptyTableBody };
});

const baseLog = (overrides = {}) => ({
  id: 1,
  userId: 1,
  username: 'u1',
  action_type: 'LOGIN',
  ip_address: '127.0.0.1',
  request_path: '/api/x',
  response_status: 200,
  response_time_ms: 10,
  success: true,
  created_at: '2026-03-13T10:00:00Z',
  ...overrides,
});

describe('UserActivityLogTable (TC-13, TC-14)', () => {
  test('TC-13: renders label for known type from map; raw code for unknown', () => {
    const labelMap = { LOGIN: '로그인', SEARCH: '검색' };
    const { rerender } = render(
      <UserActivityLogTable
        logs={[baseLog({ action_type: 'LOGIN' }), baseLog({ id: 2, action_type: 'NEW_TYPE_X' })]}
        loading={false}
        actionTypeLabelMap={labelMap}
      />,
    );

    expect(screen.getByText('로그인')).toBeInTheDocument();
    expect(screen.getByText('NEW_TYPE_X')).toBeInTheDocument();

    rerender(
      <UserActivityLogTable
        logs={[baseLog({ action_type: 'UNKNOWN_CODE' })]}
        loading={false}
        actionTypeLabelMap={{}}
      />,
    );
    expect(screen.getByText('UNKNOWN_CODE')).toBeInTheDocument();
  });

  test('TC-14: LOGIN row displays Korean label when map provides it (regression)', () => {
    render(
      <UserActivityLogTable
        logs={[baseLog({ action_type: 'LOGIN' })]}
        loading={false}
        actionTypeLabelMap={{ LOGIN: '로그인' }}
      />,
    );
    expect(screen.getByText('로그인')).toBeInTheDocument();
  });
});
