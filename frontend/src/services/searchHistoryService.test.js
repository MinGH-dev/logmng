import { getSearchHistoryList } from './searchHistoryService';

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
});
