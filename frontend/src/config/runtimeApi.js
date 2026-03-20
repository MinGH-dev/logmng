/**
 * API base URL for browser → Spring Boot.
 * Priority: window.__LOGMNG_RUNTIME_CONFIG__.apiBaseUrl (runtime) > REACT_APP_API_BASE_URL (build).
 * Runtime is set by /runtime-config.js (JDK static server reads LOGMNG_API_BASE_URL) or public/runtime-config.js.
 */
const BUILD_DEFAULT = (process.env.REACT_APP_API_BASE_URL || 'http://localhost:9200/api').replace(
  /\/$/,
  ''
);

export function getApiBaseUrl() {
  if (typeof window === 'undefined') {
    return BUILD_DEFAULT;
  }
  const cfg = window.__LOGMNG_RUNTIME_CONFIG__;
  if (cfg && typeof cfg.apiBaseUrl === 'string') {
    const t = cfg.apiBaseUrl.trim();
    if (t !== '') {
      return t.replace(/\/$/, '');
    }
  }
  return BUILD_DEFAULT;
}
