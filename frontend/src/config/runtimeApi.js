/**
 * API base URL for browser → Spring Boot.
 *
 * **Alternate login entry (same SPA bundle):** bookmark `/#/login` or `/#/entry` — same
 * `index.html` and login flow as `/`. For path-based aliases (e.g. `/login`), configure the
 * reverse proxy or static host to serve `index.html` for those paths; set `homepage` /
 * `PUBLIC_URL` in build when the app is not deployed at domain root.
 *
 * Priority: window.__LOGMNG_RUNTIME_CONFIG__.apiBaseUrl (runtime) >
 * REACT_APP_API_BASE_URL (build) > localhost:3000 same-origin /api (CRA dev proxy; cookies match page origin) >
 * localhost on non-3000 ports defaults to backend :9200 to avoid frontend dev-server 404s >
 * default host:9200 (Node / non-loopback browser without env).
 * Runtime is set by /runtime-config.js (JDK static server reads LOGMNG_API_BASE_URL) or public/runtime-config.js.
 */
function getBuildDefault() {
  const v = process.env.REACT_APP_API_BASE_URL;
  const base =
    typeof v === 'string' && v.trim() !== '' ? v.trim() : 'http://localhost:9200/api';
  return base.replace(/\/$/, '');
}

function devLocalSameOriginApiBase() {
  // Port 3000 typically has CRA proxy (/api -> :9200); same-origin helps preserve cookie behavior.
  const { hostname, protocol, port } = window.location;
  const p = port || '3000';
  return `${protocol}//${hostname}:${p}/api`.replace(/\/$/, '');
}

function isAbsoluteHttpUrl(value) {
  // Absolute API URLs should always keep highest env precedence.
  return /^https?:\/\//i.test(value);
}

function isLoopbackHost(hostname) {
  return hostname === 'localhost' || hostname === '127.0.0.1';
}

export function getApiBaseUrl() {
  if (typeof window === 'undefined') {
    return getBuildDefault();
  }
  const cfg = window.__LOGMNG_RUNTIME_CONFIG__;
  if (cfg && typeof cfg.apiBaseUrl === 'string') {
    const t = cfg.apiBaseUrl.trim();
    if (t !== '') {
      return t.replace(/\/$/, '');
    }
  }
  const envApi = process.env.REACT_APP_API_BASE_URL;
  if (typeof envApi === 'string' && envApi.trim() !== '') {
    const normalizedEnvApi = envApi.trim().replace(/\/$/, '');
    if (isAbsoluteHttpUrl(normalizedEnvApi)) {
      return normalizedEnvApi;
    }
    const { hostname, port } = window.location;
    if (isLoopbackHost(hostname)) {
      if ((port || '3000') === '3000') {
        // Keep relative env on CRA default port so /api proxy remains usable.
        return normalizedEnvApi;
      }
      // Ignore relative env on non-3000 localhost to prevent hitting frontend dev-server /api.
      return 'http://localhost:9200/api';
    }
    return normalizedEnvApi;
  }
  const { hostname, port } = window.location;
  if (isLoopbackHost(hostname)) {
    if ((port || '3000') === '3000') {
      return devLocalSameOriginApiBase();
    }
    // Non-3000 localhost often runs without /api proxy (e.g. 3001), so call backend directly.
    return 'http://localhost:9200/api';
  }
  return getBuildDefault();
}
