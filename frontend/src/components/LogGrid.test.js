import React from 'react';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import LogGrid from './LogGrid';

jest.mock('../config/runtimeApi', () => ({
  getApiBaseUrl: () => 'http://test-api.example/api',
}));

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
        logType={{ id: 'pb_feplog', name: 'PB FEP', description: '' }}
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
    expect(body.sortSpecs).toEqual([{ field: 'log_timestamp', direction: 'desc' }]);
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
        logType={{ id: 'pb_feplog', name: 'PB FEP Log', description: '' }}
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
});
