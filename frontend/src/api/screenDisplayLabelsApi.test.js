jest.mock('../config/runtimeApi', () => ({
  getApiBaseUrl: () => 'http://localhost:9200/api',
}));

import {
  buildScreenDisplayLabelsUrl,
  fetchScreenDisplayLabels,
  putScreenDisplayLabels,
} from './screenDisplayLabelsApi';

describe('buildScreenDisplayLabelsUrl', () => {
  it('appends /api/screen-display-labels when base has no /api suffix', () => {
    expect(buildScreenDisplayLabelsUrl('http://localhost:9200')).toBe(
      'http://localhost:9200/api/screen-display-labels'
    );
  });

  it('appends /screen-display-labels when base already ends with /api', () => {
    expect(buildScreenDisplayLabelsUrl('http://localhost:9200/api')).toBe(
      'http://localhost:9200/api/screen-display-labels'
    );
  });

  it('strips trailing slashes before resolving', () => {
    expect(buildScreenDisplayLabelsUrl('http://localhost:9200/')).toBe(
      'http://localhost:9200/api/screen-display-labels'
    );
    expect(buildScreenDisplayLabelsUrl('http://localhost:9200/api/')).toBe(
      'http://localhost:9200/api/screen-display-labels'
    );
  });
});

describe('fetchScreenDisplayLabels / putScreenDisplayLabels error messages', () => {
  const origFetch = global.fetch;

  beforeEach(() => {
    global.fetch = jest.fn();
  });

  afterEach(() => {
    global.fetch = origFetch;
  });

  it('putScreenDisplayLabels uses json.error for Error.message when response is not OK', async () => {
    global.fetch.mockResolvedValue({
      ok: false,
      status: 400,
      json: async () => ({
        success: false,
        error: 'labelUser는 비어 있을 수 없습니다.',
        code: 'INVALID_INPUT',
      }),
    });

    await expect(putScreenDisplayLabels([])).rejects.toMatchObject({
      message: 'labelUser는 비어 있을 수 없습니다.',
      code: 'INVALID_INPUT',
      status: 400,
    });
  });

  it('fetchScreenDisplayLabels uses json.error for Error.message when response is not OK', async () => {
    global.fetch.mockResolvedValue({
      ok: false,
      status: 403,
      json: async () => ({
        success: false,
        error: '관리자만 조회할 수 있습니다.',
        code: 'FORBIDDEN',
      }),
    });

    await expect(fetchScreenDisplayLabels()).rejects.toMatchObject({
      message: '관리자만 조회할 수 있습니다.',
      code: 'FORBIDDEN',
      status: 403,
    });
  });
});
