import { getSearchHistoryList, createSearchHistory } from './searchHistoryService';

describe('searchHistoryService', () => {
  beforeEach(() => {
    global.fetch = jest.fn().mockResolvedValue({
      ok: true,
      json: async () => ({ success: true, data: {} }),
    });
  });

  afterEach(() => {
    jest.resetAllMocks();
  });

  test('includes requester query params when provided (userId numeric)', async () => {
    await getSearchHistoryList({
      page: 2,
      pageSize: 30,
      sortField: 'requested_at',
      sortDirection: 'asc',
      department: '개발부',
      username: '홍길동',
      userId: 12345678,
    });

    const requestUrl = new URL(global.fetch.mock.calls[0][0], 'http://localhost');

    expect(requestUrl.searchParams.get('page')).toBe('2');
    expect(requestUrl.searchParams.get('pageSize')).toBe('30');
    expect(requestUrl.searchParams.get('sortField')).toBe('requested_at');
    expect(requestUrl.searchParams.get('sortDirection')).toBe('asc');
    expect(requestUrl.searchParams.get('department')).toBe('개발부');
    expect(requestUrl.searchParams.get('username')).toBe('홍길동');
    expect(requestUrl.searchParams.get('userId')).toBe('12345678');
  });

  test('omits empty requester params', async () => {
    await getSearchHistoryList({
      page: 1,
      pageSize: 20,
      sortField: 'requested_at',
      sortDirection: 'desc',
      department: '',
      username: '   ',
      userId: '',
    });

    const requestUrl = new URL(global.fetch.mock.calls[0][0], 'http://localhost');

    expect(requestUrl.searchParams.get('page')).toBe('1');
    expect(requestUrl.searchParams.get('pageSize')).toBe('20');
    expect(requestUrl.searchParams.get('department')).toBeNull();
    expect(requestUrl.searchParams.get('username')).toBeNull();
    expect(requestUrl.searchParams.get('userId')).toBeNull();
  });

  describe('createSearchHistory', () => {
    test('TC-05 (req 20260318): when options with searchResultTotalCount and decryptionTargetCount provided, POST body includes both', async () => {
      global.fetch = jest.fn().mockResolvedValue({
        ok: true,
        json: async () => ({ success: true, data: { id: 1 } }),
      });

      await createSearchHistory('java_fw_imglog', { startDate: '2026-01-01' }, 'reason', {
        searchResultTotalCount: 50,
        decryptionTargetCount: 20,
      });

      expect(global.fetch).toHaveBeenCalledTimes(1);
      const [, init] = global.fetch.mock.calls[0];
      const body = JSON.parse(init.body);
      expect(body.searchResultTotalCount).toBe(50);
      expect(body.decryptionTargetCount).toBe(20);
      expect(body.logType).toBe('java_fw_imglog');
      expect(body.requestReason).toBe('reason');
    });

    test('when options omitted, POST body does not include count fields', async () => {
      global.fetch = jest.fn().mockResolvedValue({
        ok: true,
        json: async () => ({ success: true, data: { id: 1 } }),
      });

      await createSearchHistory('java_fw_imglog', { startDate: '2026-01-01' }, 'reason');

      const [, init] = global.fetch.mock.calls[0];
      const body = JSON.parse(init.body);
      expect(body).not.toHaveProperty('searchResultTotalCount');
      expect(body).not.toHaveProperty('decryptionTargetCount');
    });

    test('when only one count in options, POST body does not include count fields', async () => {
      global.fetch = jest.fn().mockResolvedValue({
        ok: true,
        json: async () => ({ success: true, data: { id: 1 } }),
      });

      await createSearchHistory('java_fw_imglog', {}, 'reason', { searchResultTotalCount: 50 });

      const [, init] = global.fetch.mock.calls[0];
      const body = JSON.parse(init.body);
      expect(body).not.toHaveProperty('searchResultTotalCount');
      expect(body).not.toHaveProperty('decryptionTargetCount');
    });
  });
});
