import { getApiBaseUrl } from './runtimeApi';

describe('getApiBaseUrl', () => {
  const original = window.__LOGMNG_RUNTIME_CONFIG__;

  afterEach(() => {
    window.__LOGMNG_RUNTIME_CONFIG__ = original;
  });

  test('uses runtime apiBaseUrl when set', () => {
    window.__LOGMNG_RUNTIME_CONFIG__ = { apiBaseUrl: 'http://example.com:9200/api/' };
    expect(getApiBaseUrl()).toBe('http://example.com:9200/api');
  });

  test('trims and strips trailing slash', () => {
    window.__LOGMNG_RUNTIME_CONFIG__ = { apiBaseUrl: '  http://x/api/  ' };
    expect(getApiBaseUrl()).toBe('http://x/api');
  });

  test('empty runtime string falls back to build default', () => {
    window.__LOGMNG_RUNTIME_CONFIG__ = { apiBaseUrl: '   ' };
    const u = getApiBaseUrl();
    expect(u).toBeTruthy();
    expect(u).not.toMatch(/^\s*$/);
  });
});
