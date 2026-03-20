import {
  FILTER_OPTION_SCREEN_IDS,
  getDepartmentFilterOptions,
} from './filterOptionsService';

describe('filterOptionsService', () => {
  beforeEach(() => {
    global.fetch = jest.fn().mockResolvedValue({
      ok: true,
      json: async () => ({ success: true, data: ['개발부'] }),
    });
  });

  afterEach(() => {
    jest.resetAllMocks();
  });

  test('requests department filter options with screen query parameter', async () => {
    await getDepartmentFilterOptions(FILTER_OPTION_SCREEN_IDS.STATISTICS);

    const [requestUrl, requestOptions] = global.fetch.mock.calls[0];
    const url = new URL(requestUrl, 'http://localhost');

    expect(url.pathname).toBe('/api/filter-options/departments');
    expect(url.searchParams.get('screen')).toBe('statistics');
    expect(requestOptions).toMatchObject({
      method: 'GET',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json' },
    });
  });

  test('rejects unsupported screen ids before requesting', async () => {
    await expect(getDepartmentFilterOptions('unknown-screen')).rejects.toThrow(
      '지원하지 않는 department filter screenId입니다: unknown-screen',
    );
    expect(global.fetch).not.toHaveBeenCalled();
  });
});
