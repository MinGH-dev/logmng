import { getApiBaseUrl } from './runtimeApi';

describe('getApiBaseUrl', () => {
  const original = window.__LOGMNG_RUNTIME_CONFIG__;
  const originalReactApp = process.env.REACT_APP_API_BASE_URL;
  let locationDescriptor;

  beforeEach(() => {
    locationDescriptor = Object.getOwnPropertyDescriptor(window, 'location');
    delete process.env.REACT_APP_API_BASE_URL;
  });

  afterEach(() => {
    window.__LOGMNG_RUNTIME_CONFIG__ = original;
    if (originalReactApp !== undefined) {
      process.env.REACT_APP_API_BASE_URL = originalReactApp;
    } else {
      delete process.env.REACT_APP_API_BASE_URL;
    }
    if (locationDescriptor) {
      Object.defineProperty(window, 'location', locationDescriptor);
    }
  });

  /** jsdom Location getters are not spyable; replace window.location for hostname/protocol/port. */
  function mockBrowserLocation(hostname, port, protocol = 'http:') {
    const hrefHost = port ? `${hostname}:${port}` : hostname;
    const href = `${protocol}//${hrefHost}/`;
    Object.defineProperty(window, 'location', {
      configurable: true,
      writable: true,
      value: {
        hostname,
        port,
        protocol,
        href,
      },
    });
  }

  test('uses runtime apiBaseUrl when set', () => {
    mockBrowserLocation('localhost', '3000');
    window.__LOGMNG_RUNTIME_CONFIG__ = { apiBaseUrl: 'http://example.com:9200/api/' };
    expect(getApiBaseUrl()).toBe('http://example.com:9200/api');
  });

  test('trims and strips trailing slash', () => {
    mockBrowserLocation('localhost', '3000');
    window.__LOGMNG_RUNTIME_CONFIG__ = { apiBaseUrl: '  http://x/api/  ' };
    expect(getApiBaseUrl()).toBe('http://x/api');
  });

  test('empty runtime on localhost:3000 uses same-origin /api (CRA proxy default)', () => {
    window.__LOGMNG_RUNTIME_CONFIG__ = { apiBaseUrl: '   ' };
    mockBrowserLocation('localhost', '3000');
    expect(getApiBaseUrl()).toBe('http://localhost:3000/api');
  });

  test('localhost:3001 falls back to backend direct url', () => {
    window.__LOGMNG_RUNTIME_CONFIG__ = undefined;
    mockBrowserLocation('localhost', '3001');
    expect(getApiBaseUrl()).toBe('http://localhost:9200/api');
  });

  test('127.0.0.1:3001 falls back to backend direct url', () => {
    window.__LOGMNG_RUNTIME_CONFIG__ = undefined;
    mockBrowserLocation('127.0.0.1', '3001');
    expect(getApiBaseUrl()).toBe('http://localhost:9200/api');
  });

  test('non-loopback host falls back to build default (not page origin)', () => {
    window.__LOGMNG_RUNTIME_CONFIG__ = undefined;
    mockBrowserLocation('devbox.example', '3000');
    const url = getApiBaseUrl();
    expect(url).not.toContain('devbox.example');
    expect(url).toBe('http://localhost:9200/api');
  });
});

describe('getApiBaseUrl REACT_APP_API_BASE_URL', () => {
  const originalEnv = process.env.REACT_APP_API_BASE_URL;
  let locationDescriptor;

  beforeEach(() => {
    locationDescriptor = Object.getOwnPropertyDescriptor(window, 'location');
  });

  afterEach(() => {
    process.env.REACT_APP_API_BASE_URL = originalEnv;
    jest.resetModules();
    if (locationDescriptor) {
      Object.defineProperty(window, 'location', locationDescriptor);
    }
  });

  test('prefers REACT_APP over localhost proxy when runtime unset', () => {
    process.env.REACT_APP_API_BASE_URL = 'http://custom-build:9999/api/';
    jest.resetModules();
    window.__LOGMNG_RUNTIME_CONFIG__ = undefined;
    Object.defineProperty(window, 'location', {
      configurable: true,
      writable: true,
      value: {
        hostname: 'localhost',
        port: '3000',
        protocol: 'http:',
        href: 'http://localhost:3000/',
      },
    });
    const { getApiBaseUrl: getUrl } = require('./runtimeApi');
    expect(getUrl()).toBe('http://custom-build:9999/api');
  });

  test('keeps relative REACT_APP on localhost:3000 for CRA proxy', () => {
    process.env.REACT_APP_API_BASE_URL = '/api';
    jest.resetModules();
    window.__LOGMNG_RUNTIME_CONFIG__ = undefined;
    Object.defineProperty(window, 'location', {
      configurable: true,
      writable: true,
      value: {
        hostname: 'localhost',
        port: '3000',
        protocol: 'http:',
        href: 'http://localhost:3000/',
      },
    });
    const { getApiBaseUrl: getUrl } = require('./runtimeApi');
    expect(getUrl()).toBe('/api');
  });

  test('ignores relative REACT_APP on localhost:3001 and uses backend direct url', () => {
    process.env.REACT_APP_API_BASE_URL = '/api';
    jest.resetModules();
    window.__LOGMNG_RUNTIME_CONFIG__ = undefined;
    Object.defineProperty(window, 'location', {
      configurable: true,
      writable: true,
      value: {
        hostname: 'localhost',
        port: '3001',
        protocol: 'http:',
        href: 'http://localhost:3001/',
      },
    });
    const { getApiBaseUrl: getUrl } = require('./runtimeApi');
    expect(getUrl()).toBe('http://localhost:9200/api');
  });
});
