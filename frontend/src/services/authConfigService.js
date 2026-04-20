/**
 * Public auth mode for login form shape (local vs AD). Server source of truth is auth.login.mode;
 * when GET /api/auth/config is not available, use REACT_APP_AUTH_LOGIN_MODE (default local).
 */
import { getApiBaseUrl } from '../config/runtimeApi';

const AUTH_CONFIG_TIMEOUT_MS = 4000;

/**
 * @returns {Promise<'local'|'ad'>}
 */
export async function fetchAuthLoginMode() {
  const apiBaseUrl = getApiBaseUrl();
  const controller = new AbortController();
  const timeoutId = setTimeout(() => {
    controller.abort();
  }, AUTH_CONFIG_TIMEOUT_MS);
  try {
    const response = await fetch(`${apiBaseUrl}/auth/config`, {
      credentials: 'include',
      signal: controller.signal,
    });
    if (!response.ok) {
      throw new Error(`auth config HTTP ${response.status}`);
    }
    const result = await response.json();
    const mode = result?.data?.loginMode ?? result?.data?.authLoginMode;
    if (mode === 'ad' || mode === 'local') {
      return mode;
    }
  } catch {
    // Contract gap: public config endpoint may be absent — fall back to build-time env.
  } finally {
    clearTimeout(timeoutId);
  }
  const env = process.env.REACT_APP_AUTH_LOGIN_MODE;
  if (env === 'ad' || env === 'local') {
    return env;
  }
  return 'local';
}
