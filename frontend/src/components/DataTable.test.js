import React from 'react';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import DataTable from './DataTable';

describe('DataTable', () => {
  test('TC-01: one-page data keeps the shared footer visible without page navigation buttons', () => {
    const onPageSizeChange = jest.fn();
    const { container } = render(
      <DataTable
        columns={[
          { key: 'id', label: 'ID', sortable: true },
          { key: 'name', label: '이름', sortable: false },
        ]}
        sortConfig={{ key: 'id', direction: 'asc' }}
        onSort={jest.fn()}
        pagination={{
          currentPage: 1,
          totalPages: 1,
          totalCount: 7,
          onPageChange: jest.fn(),
          simple: true,
          infoText: '총 7건',
        }}
        pageSize={20}
        onPageSizeChange={onPageSizeChange}
        ariaLabel="공통 데이터 테이블"
      >
        <tr>
          <td>1</td>
          <td>테스트</td>
        </tr>
      </DataTable>,
    );

    const tableContainer = container.querySelector('.log-table-container');
    const pagination = tableContainer.querySelector(':scope > .pagination');

    expect(pagination).not.toBeNull();
    expect(screen.getByRole('navigation', { name: '테이블 푸터' })).toBeInTheDocument();
    expect(within(pagination).getByText('총 7건')).toBeInTheDocument();
    expect(within(pagination).getByText('표시 건수')).toBeInTheDocument();
    expect(within(pagination).queryByRole('button', { name: '다음 페이지' })).not.toBeInTheDocument();
  });

  test('TC-05: multi-page data renders pagination as a sibling of the shared table wrapper', async () => {
    const onPageChange = jest.fn();
    const onPageSizeChange = jest.fn();
    const { container } = render(
      <DataTable
        columns={[
          { key: 'id', label: 'ID', sortable: true },
          { key: 'name', label: '이름', sortable: false },
        ]}
        sortConfig={{ key: 'id', direction: 'asc' }}
        onSort={jest.fn()}
        pagination={{
          currentPage: 1,
          totalPages: 3,
          totalCount: 42,
          onPageChange,
          simple: true,
          infoText: '총 42건',
        }}
        pageSize={20}
        onPageSizeChange={onPageSizeChange}
        ariaLabel="공통 데이터 테이블"
      >
        <tr>
          <td>1</td>
          <td>테스트</td>
        </tr>
      </DataTable>,
    );

    const tableContainer = container.querySelector('.log-table-container');
    expect(tableContainer).not.toBeNull();
    expect(tableContainer.querySelector(':scope > .table-wrapper')).not.toBeNull();

    const pagination = tableContainer.querySelector(':scope > .pagination');
    expect(pagination).not.toBeNull();
    expect(screen.getByRole('navigation', { name: '테이블 푸터' })).toBeInTheDocument();
    expect(within(pagination).getByText('총 42건')).toBeInTheDocument();
    expect(within(pagination).getByRole('button', { name: '다음 페이지' })).toBeInTheDocument();
    expect(within(pagination).getByRole('spinbutton', { name: '페이지당 행 수' })).toHaveValue(20);

    await userEvent.click(within(pagination).getByRole('button', { name: '행 수 증가' }));
    expect(onPageSizeChange).toHaveBeenCalledWith(21);
  });
});
