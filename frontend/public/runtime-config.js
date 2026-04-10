/* Default: no override — app uses REACT_APP_API_BASE_URL from build or localhost default.
 * JDK static server serves /runtime-config.js from env LOGMNG_API_BASE_URL (overrides this file).
 * After deploy you may edit this file under www/ if not using the Java server override. */
window.__LOGMNG_RUNTIME_CONFIG__ = window.__LOGMNG_RUNTIME_CONFIG__ || {};
